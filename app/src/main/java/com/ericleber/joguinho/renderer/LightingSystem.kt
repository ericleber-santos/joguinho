package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.ericleber.joguinho.biome.BiomePalette
import com.ericleber.joguinho.core.GameState
import kotlin.math.sin

/**
 * Sistema de iluminação em overlay — escuridão apenas nas bordas do mapa.
 *
 * Comportamento:
 * - Centro da tela (onde o Hero está) fica totalmente visível
 * - Escuridão cresce suavemente em direção às bordas da tela
 * - Alpha máximo do overlay: 160 (não 210 — menos agressivo)
 * - Halo do Hero: raio grande (5 tiles) para iluminar bem a área de jogo
 * - Cogumelos e cristais emitem halos coloridos pulsantes discretos
 *
 * Requisito: meta-pixel-art.md — Sistema de Iluminação
 */
class LightingSystem {

    private var overlayBitmap: Bitmap? = null
    private var overlayCanvas: Canvas? = null

    private val paintEscuro = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }

    // DST_OUT apaga o overlay onde o gradiente é opaco → cria "buraco" de luz
    private val paintLuz = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = false
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private val paintOverlay = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }

    /**
     * Renderiza o overlay de iluminação.
     * Deve ser chamado APÓS tiles e personagens, ANTES do HUD.
     */
    fun renderOverlay(
        canvas: Canvas,
        gameState: GameState,
        cameraX: Float,
        cameraY: Float,
        tileW: Float,
        tileH: Float,
        palette: BiomePalette,
        frameTotal: Long,
        screenWidth: Int,
        screenHeight: Int
    ) {
        if (overlayBitmap == null ||
            overlayBitmap!!.width != screenWidth ||
            overlayBitmap!!.height != screenHeight) {
            overlayBitmap?.recycle()
            overlayBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            overlayCanvas = Canvas(overlayBitmap!!)
        }

        val oc = overlayCanvas ?: return

        // Overlay com alpha 150 — escuridão moderada, não sufocante
        oc.drawColor(Color.argb(150, 0, 0, 0))

        // --- Halo principal do Hero — raio grande, ilumina toda a área de jogo ---
        val heroScreenPos = IsometricProjection.worldToScreen(
            gameState.heroPosition.x, gameState.heroPosition.y, tileW, tileH
        )
        val heroSx = heroScreenPos.x + cameraX + tileW / 2f
        val heroSy = heroScreenPos.y + cameraY + tileH / 2f

        // Halo externo grande: ilumina ~5 tiles ao redor — cobre a maior parte da tela
        desenharHaloDeLuz(oc, heroSx, heroSy, tileW * 5.5f, palette.ambientLight, 255)
        // Halo médio: zona mais brilhante (~3 tiles)
        desenharHaloDeLuz(oc, heroSx, heroSy, tileW * 3.0f, palette.glowColor, 220)
        // Núcleo quente: imediatamente ao redor do Hero
        desenharHaloDeLuz(oc, heroSx, heroSy, tileW * 1.5f, Color.WHITE, 180)

        // --- Halo do Spike ---
        val spikeScreenPos = IsometricProjection.worldToScreen(
            gameState.spikePosition.x, gameState.spikePosition.y, tileW, tileH
        )
        val spikeSx = spikeScreenPos.x + cameraX + tileW / 2f
        val spikeSy = spikeScreenPos.y + cameraY + tileH / 2f
        desenharHaloDeLuz(oc, spikeSx, spikeSy, tileW * 2.5f, palette.ambientLight, 160)

        // --- Halos dos decorativos (cogumelos e cristais) ---
        val mazeData = gameState.mazeData
        if (mazeData != null) {
            val pulsacao = sin(frameTotal * 0.06f).toFloat()
            val alphaCogumelo = (100 + pulsacao * 30).toInt().coerceIn(70, 130)

            for (ty in 0 until mazeData.height) {
                for (tx in 0 until mazeData.width) {
                    val tileIndex = ty * mazeData.width + tx
                    if (tileIndex < 0 || tileIndex >= mazeData.tiles.size) continue
                    if (mazeData.tiles[tileIndex] != 0) continue

                    val decorSeed = (tx * 31 + ty * 17 + mazeData.seed.toInt()) % 7
                    if (decorSeed != 0) continue

                    val temParedeAdj = listOf(tx-1 to ty, tx+1 to ty, tx to ty-1, tx to ty+1)
                        .any { (nx, ny) ->
                            val ni = ny * mazeData.width + nx
                            ni >= 0 && ni < mazeData.tiles.size && mazeData.tiles[ni] == 1
                        }
                    if (temParedeAdj) continue

                    val screenPos = IsometricProjection.worldToScreen(tx, ty, tileW, tileH)
                    val decorSx = screenPos.x + cameraX + tileW / 2f
                    val decorSy = screenPos.y + cameraY + tileH / 2f

                    // Culling — só processa decorativos visíveis
                    if (decorSx < -tileW * 5 || decorSx > screenWidth + tileW * 5) continue
                    if (decorSy < -tileH * 5 || decorSy > screenHeight + tileH * 5) continue

                    val variant = (tx + ty) % 4
                    val corHalo = when (variant) {
                        0 -> palette.mushroomCapColor
                        1 -> palette.crystalColor
                        else -> continue
                    }
                    val raioDecor = tileW * (1.2f + pulsacao * 0.12f)
                    desenharHaloDeLuz(oc, decorSx, decorSy, raioDecor, corHalo, alphaCogumelo)
                }
            }
        }

        canvas.drawBitmap(overlayBitmap!!, 0f, 0f, paintOverlay)
    }

    /**
     * Desenha um "buraco" de luz no overlay via gradiente radial.
     * Centro totalmente transparente → borda opaca = área iluminada.
     */
    private fun desenharHaloDeLuz(
        canvas: Canvas,
        cx: Float, cy: Float,
        raio: Float,
        corLuz: Int,
        alphaMax: Int
    ) {
        if (raio <= 0f) return

        val r = Color.red(corLuz)
        val g = Color.green(corLuz)
        val b = Color.blue(corLuz)

        val gradiente = RadialGradient(
            cx, cy, raio,
            intArrayOf(
                Color.argb(alphaMax, r, g, b),       // centro: máxima remoção do overlay
                Color.argb(alphaMax * 2 / 3, r, g, b), // 40%: ainda bem iluminado
                Color.argb(alphaMax / 4, r, g, b),   // 70%: começa a escurecer
                Color.argb(0, r, g, b)                // borda: sem efeito
            ),
            floatArrayOf(0f, 0.4f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        paintLuz.shader = gradiente
        canvas.drawCircle(cx, cy, raio, paintLuz)
        paintLuz.shader = null
    }

    fun release() {
        overlayBitmap?.recycle()
        overlayBitmap = null
        overlayCanvas = null
    }
}
