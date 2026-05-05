package com.ericleber.joguinho.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.random.Random

/**
 * Sistema de goteiras animadas para biomas úmidos (hasDrips = true).
 *
 * Ciclo de vida: FORMING → FALLING → SPLASHING → DEAD (reciclado no pool)
 *
 * Restrições técnicas (Android mobile):
 *  - Pool pré-alocado de MAX_DRIPS gotas — zero new() em update/render.
 *  - Máximo de MAX_DRIPS gotas ativas simultâneas.
 *  - Máximo de MAX_SOURCES fontes registradas por mapa.
 */
class DripSystem {

    companion object {
        private const val MAX_DRIPS = 30           // Aumentado pool
        private const val MAX_SOURCES = 80          // Aumentado fontes
        private const val GRAVITY = 1200f          // Aumentado drasticamente px/s²
        private const val FORMING_DURATION = 1000f // ms (mais rápido)
        private const val SPLASH_DURATION = 500f   // ms (mais visível)
    }

    // -----------------------------------------------------------------------
    // Pool de partículas (pré-alocado)
    // -----------------------------------------------------------------------

    private val pool = Array(MAX_DRIPS) { DripParticle() }
    private val sources = ArrayList<DripSource>(MAX_SOURCES)

    // Paint compartilhado — atualizado a cada gota (não criado em loop)
    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        color = Color.argb(200, 100, 180, 255)
    }

    // Callback de som — injetado externamente para evitar dependência circular
    var onDripSound: ((volume: Float) -> Unit)? = null
    var onSplashSound: (() -> Unit)? = null

    /**
     * Inicializa o sistema para um novo mapa, escaneando por fontes de goteira.
     * Uma fonte é criada onde há teto (parede) acima de um chão (piso).
     */
    fun init(maze: com.ericleber.joguinho.core.MazeData, palette: com.ericleber.joguinho.biome.BiomePalette) {
        sources.clear()
        pool.forEach { it.state = DripState.DEAD }

        if (!palette.hasDrips) return

        val width = maze.width
        val height = maze.height
        val tiles = maze.tiles

        for (ty in 0 until height - 1) {
            for (tx in 0 until width) {
                val idxSelf = ty * width + tx
                val idxBelow = (ty + 1) * width + tx
                
                // Regra: parede no topo, chão abaixo = goteira no "bico" do teto
                if (tiles[idxSelf] == 1 && tiles[idxBelow] == 0) {
                    // Encontra o chão mais baixo na mesma coluna para o splash
                    var gridFloorY = ty + 1
                    for (fy in (ty + 1) until height) {
                        if (tiles[fy * width + tx] == 1) {
                            gridFloorY = fy - 1
                            break
                        }
                        if (fy == height - 1) gridFloorY = fy
                    }
                    
                    // 25% de chance de ser uma goteira ativa nesse tile
                    val seed = tx * 13 + ty * 37
                    if (java.util.Random(seed.toLong()).nextFloat() < 0.25f) {
                        registerDripSource(tx, ty, gridFloorY, seed.toLong())
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Geração de mapa — chamado uma vez por mapa
    // -----------------------------------------------------------------------

    /**
     * Registra um tile de teto como fonte potencial de goteiras usando coordenadas de grid.
     *
     * @param gridX       X no grid do maze
     * @param gridY       Y no grid do maze (teto)
     * @param gridFloorY  Y no grid do maze (chão onde espirra)
     * @param mapSeed     seed do mapa para determinismo
     */
    fun registerDripSource(
        gridX: Int, gridY: Int,
        gridFloorY: Int,
        mapSeed: Long
    ) {
        if (sources.size >= MAX_SOURCES) return
        val rng = Random(mapSeed + gridX * 100L + gridY)
        if (rng.nextFloat() >= 0.25f) return   // Aumentado para 25%

        val intervalMs = (1000L + (rng.nextLong().and(0x7FFFFFFF) % 4000L))

        sources.add(
            DripSource(
                gridX = gridX,
                gridY = gridY,
                gridFloorY = gridFloorY,
                intervalMs = intervalMs
            )
        )
    }

    // -----------------------------------------------------------------------
    // Update — chamado a cada frame com deltaMs
    // -----------------------------------------------------------------------

    fun update(deltaMs: Long, tileSize: Float) {
        if (sources.isEmpty()) return

        // Tentar emitir novas gotas de cada fonte
        val activeCount = pool.count { it.state != DripState.DEAD }

        for (source in sources) {
            source.timerMs += deltaMs
            if (source.timerMs < source.intervalMs) continue
            source.timerMs = 0L

            if (activeCount >= MAX_DRIPS) continue  // cap atingido
            val slot = pool.firstOrNull { it.state == DripState.DEAD } ?: continue

            // Inicializar a gota convertendo Grid -> World
            val worldX = source.gridX * tileSize + tileSize / 2f
            val ceilingY = source.gridY * tileSize + tileSize // topo do tile abaixo do teto
            val floorY = source.gridFloorY * tileSize

            slot.x = worldX + (Random.nextFloat() * 4f - 2f)
            slot.y = ceilingY
            slot.ceilingY = ceilingY
            slot.floorY = floorY
            slot.vy = 0f
            slot.state = DripState.FORMING
            slot.scale = 0f
            slot.alpha = 1f
            slot.splashTimer = 0f

            onDripSound?.invoke(0.3f + Random.nextFloat() * 0.4f)
        }

        // Atualizar todas as gotas ativas
        val dtSec = deltaMs / 1000f
        for (drip in pool) {
            when (drip.state) {
                DripState.FORMING -> {
                    drip.scale += deltaMs / FORMING_DURATION
                    if (drip.scale >= 1f) {
                        drip.scale = 1f
                        drip.state = DripState.FALLING
                    }
                }
                DripState.FALLING -> {
                    drip.vy += GRAVITY * dtSec
                    drip.y += drip.vy * dtSec
                    if (drip.y >= drip.floorY) {
                        drip.y = drip.floorY
                        drip.state = DripState.SPLASHING
                        drip.splashTimer = 0f
                        onSplashSound?.invoke()
                    }
                }
                DripState.SPLASHING -> {
                    drip.splashTimer += deltaMs / SPLASH_DURATION
                    drip.alpha = (1f - drip.splashTimer).coerceIn(0f, 1f)
                    if (drip.splashTimer >= 1f) drip.state = DripState.DEAD
                }
                DripState.DEAD -> { /* no-op */ }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Render — chamado no render loop, dentro do clipRect do jogo
    // -----------------------------------------------------------------------

    /**
     * Renderiza as gotas ativas com offset de câmera.
     *
     * @param canvas  canvas atual do SurfaceHolder
     * @param cameraX offset horizontal da câmera (pixels)
     * @param cameraY offset vertical da câmera (pixels)
     */
    fun render(canvas: Canvas, cameraX: Float, cameraY: Float) {
        for (drip in pool) {
            if (drip.state == DripState.DEAD) continue

            val sx = drip.x + cameraX
            val sy = drip.y + cameraY

            paint.alpha = (drip.alpha * 220).toInt().coerceIn(0, 255)

            when (drip.state) {
                DripState.FORMING -> {
                    // Gota crescendo no teto: oval pequena
                    val w = 3f * drip.scale
                    val h = 5f * drip.scale
                    paint.style = Paint.Style.FILL
                    canvas.drawOval(
                        RectF(sx - w / 2f, sy - h, sx + w / 2f, sy),
                        paint
                    )
                }
                DripState.FALLING -> {
                    // Glow (Aura)
                    val baseAlpha = (drip.alpha * 220).toInt().coerceIn(0, 255)
                    paint.style = Paint.Style.FILL
                    paint.alpha = baseAlpha / 3
                    canvas.drawCircle(sx, sy, 8f, paint)
                    
                    // Gota caindo
                    paint.alpha = baseAlpha
                    canvas.drawOval(RectF(sx - 2f, sy - 5f, sx + 2f, sy + 2f), paint)
                }
                DripState.SPLASHING -> {
                    // Efeito de impacto: círculos concêntricos expandindo
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    val radius = drip.splashTimer * 16f
                    val baseAlpha = (drip.alpha * 220).toInt().coerceIn(0, 255)
                    
                    paint.alpha = baseAlpha
                    canvas.drawCircle(sx, sy, radius, paint)
                    
                    paint.alpha = baseAlpha / 2
                    canvas.drawCircle(sx, sy, radius * 0.6f, paint)
                }
                DripState.DEAD -> { /* ignorar */ }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Utilitários
    // -----------------------------------------------------------------------

    /** Número de gotas ativas (para debug/telemetria). */
    fun activeCount(): Int = pool.count { it.state != DripState.DEAD }

    /** Limpa tudo ao trocar de mapa. */
    fun reset() {
        sources.clear()
        pool.forEach { it.state = DripState.DEAD }
    }
}
