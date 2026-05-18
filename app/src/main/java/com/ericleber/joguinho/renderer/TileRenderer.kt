package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.ericleber.joguinho.biome.Biome
import com.ericleber.joguinho.biome.BiomePalette
import com.ericleber.joguinho.biome.BiomeWorld
import com.ericleber.joguinho.biome.WallDetailType
import com.ericleber.joguinho.core.MazeData
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

enum class TileType {
    WALL, FLOOR,
    DECORATIVE_0, DECORATIVE_1, DECORATIVE_2, DECORATIVE_3,
    ENTRADA, SAIDA
}

/**
 * Renderiza tiles top-down estilo Stardew Valley cave.
 */
class TileRenderer {

    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }
    private val path = Path()

    private fun tileRandom(tileX: Int, tileY: Int, index: Int = 0): Float {
        val seed = tileX * 73856093 xor tileY * 19349663 xor index * 83492791
        return (seed and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat()
    }

    // =========================================================================
    // CHÃO
    // =========================================================================

    fun renderFloorTile(
        canvas: Canvas,
        x: Float, y: Float,
        tileW: Float, tileH: Float,
        palette: BiomePalette,
        tileX: Int = 0, tileY: Int = 0
    ) {
        val corBase = palette.floorColor

        paint.color = corBase
        canvas.drawRect(x, y, x + tileW, y + tileH, paint)

        if (tileW <= 8f) return

        val lightTone = clarear(corBase, 0.15f)
        val darkTone = escurecer(corBase, 0.20f)

        // Manchas de pedra procedural (3 tons: base, claro, escuro)
        val numPatches = 10 + (tileRandom(tileX, tileY, 100) * 5).toInt()
        for (i in 0 until numPatches) {
            val r = tileRandom(tileX, tileY, i * 137 + 200)
            paint.color = if (r > 0.5f) lightTone else darkTone

            val patchW = 4 + (tileRandom(tileX, tileY, i * 137 + 300) * 6).toInt()
            val patchH = 4 + (tileRandom(tileX, tileY, i * 137 + 400) * 6).toInt()
            val px = x + tileRandom(tileX, tileY, i * 137 + 500) * (tileW - patchW)
            val py = y + tileRandom(tileX, tileY, i * 137 + 600) * (tileH - patchH)
            canvas.drawRect(px, py, px + patchW, py + patchH, paint)
        }

        // Rachaduras finas (1px, 8-15px comprimento) em 30% dos tiles
        if (tileRandom(tileX, tileY, 9999) < 0.3f) {
            paint.color = escurecer(corBase, 0.40f)
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE
            val startX = x + tileRandom(tileX, tileY, 7777) * (tileW - 2f)
            val startY = y + tileRandom(tileX, tileY, 6666) * (tileH - 2f)
            val crackLen = 8 + (tileRandom(tileX, tileY, 8888) * 7).toInt()
            val angle = tileRandom(tileX, tileY, 5555) * Math.PI.toFloat() * 2
            val endX = startX + cos(angle) * crackLen
            val endY = startY + sin(angle) * crackLen
            canvas.drawLine(startX, startY, endX, endY, paint)
            paint.style = Paint.Style.FILL
        }
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f

        // Poças de água (biomas úmidos)
        if (palette.hasDrips && tileRandom(tileX, tileY, 4444) > 0.92f) {
            paint.color = Color.argb(120, 100, 150, 255)
            val pw = tileW * 0.4f
            val ph = tileH * 0.2f
            val px = x + tileRandom(tileX, tileY, 3333) * (tileW - pw)
            val py = y + tileRandom(tileX, tileY, 2222) * (tileH - ph)
            canvas.drawOval(RectF(px, py, px + pw, py + ph), paint)
        }
    }

    // Bioma atual para diferenciação visual das paredes
    private var biomeAtual: Biome = Biome.MINA_ABANDONADA
    private var worldAtual: BiomeWorld = BiomeWorld.ENTRANHAS
    
    fun setBiome(biome: Biome) { biomeAtual = biome }
    fun setBiomeWorld(world: BiomeWorld) { worldAtual = world }

    fun renderWallTile(
        canvas: Canvas,
        x: Float, y: Float,
        tileW: Float, tileH: Float,
        palette: BiomePalette,
        tileX: Int, tileY: Int,
        mazeData: MazeData? = null
    ) {
        val seed = tileX * 7 + tileY * 13

        val mask = getWallBitmask(tileX, tileY, mazeData)

        renderOrganicWall(canvas, x, y, tileW, tileH, palette, mask, seed, tileX, tileY)
    }

    /**
     * Renderiza a parede com bordas dentadas orgânicas, textura de pedra procedural
     * e detalhes de bioma integrados (MOSS, ICE_DRIP, CRYSTAL_VEIN).
     */
    private fun renderOrganicWall(
        canvas: Canvas, x: Float, y: Float, tw: Float, th: Float,
        p: BiomePalette, mask: Int, seed: Int,
        tileX: Int, tileY: Int
    ) {
        val corBase = variarCor(p.wallColor, seed % 10 - 5)
        val borderColor = escurecer(corBase, 0.3f)
        val lightTone = clarear(corBase, 0.15f)
        val darkTone = escurecer(corBase, 0.20f)
        val corTopo = p.wallTopColor

        val n = (mask and 1) != 0
        val e = (mask and 4) != 0
        val s = (mask and 16) != 0
        val w = (mask and 64) != 0

        if (worldAtual == BiomeWorld.ENTRANHAS) {
            renderEntranhasWall(canvas, x, y, tw, th, seed, tileX, tileY, s)
            return
        }

        if (worldAtual == BiomeWorld.ABISMOS_AQUATICOS) {
            renderAbismoWall(canvas, x, y, tw, th, seed, tileX, tileY, n, s, e, w)
            return
        }

        // 1. Base sólida (mesma cor entre vizinhos = sem linhas de grid)
        paint.color = corBase
        canvas.drawRect(x, y, x + tw, y + th, paint)

        // 2. Textura de pedra com ruído procedural (3 tons)
        val numPatches = 12 + (seed % 6)
        for (i in 0 until numPatches) {
            val r = tileRandom(tileX, tileY, i * 137 + 200)
            paint.color = if (r > 0.5f) lightTone else darkTone

            val patchW = 3 + (tileRandom(tileX, tileY, i * 137 + 300) * 5).toInt()
            val patchH = 3 + (tileRandom(tileX, tileY, i * 137 + 400) * 5).toInt()
            val px = x + tileRandom(tileX, tileY, i * 137 + 500) * (tw - patchW)
            val py = y + tileRandom(tileX, tileY, i * 137 + 600) * (th - patchH)
            canvas.drawRect(px, py, px + patchW, py + patchH, paint)
        }

        // 3. Borda inferior dentada (se exposta)
        if (!s) {
            paint.color = borderColor
            val segments = (tw / 3).toInt().coerceAtLeast(6)
            val segW = tw / segments
            path.reset()
            path.moveTo(x, y + th)
            path.lineTo(x, y + th + 5f)
            for (i in 1 until segments) {
                val lx = x + i * segW
                val out = tileRandom(tileX, tileY, i * 53 + 77) * 5f - 1f
                path.lineTo(lx, y + th + out)
            }
            path.lineTo(x + tw, y + th + 5f)
            path.lineTo(x + tw, y + th)
            path.close()
            canvas.drawPath(path, paint)

            // Projeções alternadas de 1-3px
            val projCount = (tw / 6).toInt().coerceAtLeast(3)
            for (i in 0 until projCount) {
                val px2 = x + tileRandom(tileX, tileY, i * 100 + 50) * tw
                val proj = if (tileRandom(tileX, tileY, i * 100 + 51) > 0.5f) 3f else -2f
                canvas.drawRect(px2, y + th, px2 + 2f, y + th + proj, paint)
            }
        }

        // 4. Borda direita dentada (se exposta)
        if (!e) {
            paint.color = borderColor
            val segments = (th / 3).toInt().coerceAtLeast(6)
            val segH = th / segments
            path.reset()
            path.moveTo(x + tw, y)
            path.lineTo(x + tw + 5f, y)
            for (i in 1 until segments) {
                val ly = y + i * segH
                val out = tileRandom(tileX, tileY, i * 61 + 88) * 5f - 1f
                path.lineTo(x + tw + out, ly)
            }
            path.lineTo(x + tw + 5f, y + th)
            path.lineTo(x + tw, y + th)
            path.close()
            canvas.drawPath(path, paint)
        }

        // 5. Borda esquerda dentada (se exposta)
        if (!w) {
            paint.color = borderColor
            val segments = (th / 3).toInt().coerceAtLeast(6)
            val segH = th / segments
            path.reset()
            path.moveTo(x, y)
            path.lineTo(x - 5f, y)
            for (i in 1 until segments) {
                val ly = y + i * segH
                val out = tileRandom(tileX + 1, tileY, i * 71 + 99) * 5f - 1f
                path.lineTo(x - out - 1f, ly)
            }
            path.lineTo(x - 5f, y + th)
            path.lineTo(x, y + th)
            path.close()
            canvas.drawPath(path, paint)
        }

        // 6. Topo visível (quando sem vizinho norte)
        if (!n) {
            paint.color = corTopo
            canvas.drawRect(x + 2f, y, x + tw - 2f, y + 3f, paint)
        }

        // 7. Rachaduras finas (1px, 8-15px) em 30% dos tiles
        if (tileRandom(tileX, tileY, 9999) < 0.3f) {
            paint.color = escurecer(corBase, 0.40f)
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE
            val startX = x + tileRandom(tileX, tileY, 7777) * (tw - 2f)
            val startY = y + tileRandom(tileX, tileY, 6666) * (th - 2f)
            val crackLen = 8 + (tileRandom(tileX, tileY, 8888) * 7).toInt()
            val angle = tileRandom(tileX, tileY, 5555) * Math.PI.toFloat() * 2
            val endX = startX + cos(angle) * crackLen
            val endY = startY + sin(angle) * crackLen
            canvas.drawLine(startX, startY, endX, endY, paint)
            paint.style = Paint.Style.FILL
        }
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f

        // 8. Detalhes de bioma integrados na textura
        when (p.wallDetailType) {
            WallDetailType.MOSS -> {
                val mossColor = Color.rgb(45, 106, 45)
                val mossPatches = 6 + (seed % 4)
                for (i in 0 until mossPatches) {
                    if (tileRandom(tileX, tileY, i * 73 + 1000) > 0.5f) continue
                    val mx = x + tileRandom(tileX, tileY, i * 73 + 1100) * tw
                    val my = y + th * 0.5f + tileRandom(tileX, tileY, i * 73 + 1200) * th * 0.5f
                    val mw = 2 + (tileRandom(tileX, tileY, i * 73 + 1300) * 3).toInt()
                    val mh = 2 + (tileRandom(tileX, tileY, i * 73 + 1400) * 3).toInt()
                    paint.color = mossColor
                    paint.alpha = 180
                    canvas.drawRect(mx, my, mx + mw, my + mh, paint)
                }
                paint.alpha = 255

                if (!n && tileRandom(tileX, tileY, 444) > 0.6f) {
                    val numDrops = 2 + (seed % 3)
                    for (i in 0 until numDrops) {
                        val dx = x + tileRandom(tileX, tileY, i * 50 + 2000) * tw
                        val dropH = 3 + (tileRandom(tileX, tileY, i * 50 + 2100) * 4).toInt()
                        paint.color = Color.rgb(35, 90, 35)
                        canvas.drawRect(dx, y, dx + 2f, y + dropH, paint)
                    }
                }
            }
            WallDetailType.ICE_DRIP -> {
                paint.color = Color.WHITE
                paint.alpha = 60
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                canvas.drawLine(x + tw * 0.2f, y + th * 0.2f, x + tw * 0.8f, y + th * 0.8f, paint)
                paint.style = Paint.Style.FILL
                paint.alpha = 255

                if (!n) {
                    val numStalactites = 2 + (seed % 3)
                    for (i in 0 until numStalactites) {
                        val cx2 = x + tw * (0.25f + i * 0.25f)
                        val h = 3 + tileRandom(tileX, tileY, i * 31 + 500) * 3f
                        val w = tw * 0.1f
                        paint.color = Color.rgb(180, 210, 240)
                        paint.alpha = 240
                        path.reset()
                        path.moveTo(cx2 - w/2f, y)
                        path.lineTo(cx2, y - h)
                        path.lineTo(cx2 + w/2f, y)
                        path.close()
                        canvas.drawPath(path, paint)
                    }
                    paint.alpha = 255
                }
            }
            WallDetailType.CRYSTAL_VEIN -> {
                val veinColor = p.crystalColor
                paint.color = veinColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = tw * 0.04f
                val numVeins = 1 + (seed % 2)
                for (i in 0 until numVeins) {
                    val sx2 = x + tw * (0.2f + i * 0.4f)
                    val sy2 = y + th * (0.1f + i * 0.3f)
                    canvas.drawLine(sx2, sy2, sx2 + tw * 0.4f, sy2 + th * 0.5f, paint)
                }

                paint.style = Paint.Style.FILL
                val numDiamonds = 1 + (seed % 2)
                for (i in 0 until numDiamonds) {
                    val dcx = x + tw * (0.3f + tileRandom(tileX, tileY, i * 41 + 600) * 0.4f)
                    val dcy = y + th * (0.3f + tileRandom(tileX, tileY, i * 41 + 700) * 0.4f)
                    val dsize = tw * (0.08f + tileRandom(tileX, tileY, i * 41 + 800) * 0.06f)
                    path.reset()
                    path.moveTo(dcx, dcy - dsize)
                    path.lineTo(dcx + dsize * 0.7f, dcy)
                    path.lineTo(dcx, dcy + dsize)
                    path.lineTo(dcx - dsize * 0.7f, dcy)
                    path.close()
                    paint.color = veinColor
                    canvas.drawPath(path, paint)

                    if (tileRandom(tileX, tileY, i * 41 + 900) > 0.3f) {
                        paint.color = Color.WHITE
                        canvas.drawCircle(dcx, dcy - dsize, tw * 0.03f, paint)
                    }
                }
                paint.style = Paint.Style.FILL
            }
            else -> {}
        }

        paint.alpha = 255

        // Cipós decorativos (15% em biomas orgânicos)
        if (!s && (biomeAtual.name.contains("FLORESTA") || biomeAtual.name.contains("JARDIM") || biomeAtual.name.contains("CAVERNA"))) {
            if (tileRandom(tileX, tileY, 777) < 0.15f) {
                renderVines(canvas, x + tw * 0.3f, y + th * 0.8f, tw * 0.4f, p.accentColor, seed)
            }
        }
    }

    private fun renderVines(canvas: Canvas, vx: Float, vy: Float, width: Float, color: Int, seed: Int) {
        paint.color = color
        paint.alpha = 180
        val rng = Random(seed.toLong())
        val length = 20f + rng.nextFloat() * 40f
        
        // Animação procedural baseada no tempo (Balanço suave)
        val time = (System.currentTimeMillis() % 2000) / 2000f // 0 a 1 em 2 segundos
        val sway = kotlin.math.sin(time * 2 * Math.PI.toFloat()) * 8f // Amplitude de 8px
        
        // Desenha o cipó como uma série de segmentos dinâmicos
        var curY = vy
        var curX = vx
        repeat(5) { i ->
            val nextY = curY + length / 5f
            // Cada segmento balança um pouco mais que o anterior (efeito chicote)
            val segmentSway = sway * (i + 1) / 5f
            val nextX = curX + (rng.nextFloat() * 4f - 2f) + segmentSway
            canvas.drawLine(curX, curY, nextX, nextY, paint)
            curX = nextX
            curY = nextY
        }
        // Folhas no final
        paint.style = Paint.Style.FILL
        canvas.drawCircle(curX, curY, 4f, paint)
    }

    private fun renderCorner(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int, isOuter: Boolean, isInner: Boolean) {
        if (isOuter) {
            // Arredonda a quina externa (estilo Spelunky)
            paint.color = color
            canvas.drawRect(cx, cy, cx + size, cy + size, paint)
            // Pequeno chanfro
            paint.color = Color.TRANSPARENT // Simula transparência no bitmap do tile
            // (Nota: Em bitmap real, usaríamos PorterDuff para recortar)
        } else if (isInner) {
            // Detalhe de quina interna (sombra/destaque na conexão)
            paint.color = clarear(color, 0.1f)
            canvas.drawRect(cx, cy, cx + size * 0.5f, cy + size * 0.5f, paint)
        }
    }

    /** 
     * Calcula bitmask de 8 vizinhos (Blob Tileset).
     * N=1, NE=2, E=4, SE=8, S=16, SW=32, W=64, NW=128
     */
    fun getWallBitmask(tx: Int, ty: Int, mazeData: MazeData?): Int {
        if (mazeData == null) return 255
        var mask = 0
        val w = mazeData.width
        val h = mazeData.height
        val t = mazeData.tiles

        // Cardeais
        val n = ty > 0 && t[(ty - 1) * w + tx] == 1
        val e = tx < w - 1 && t[ty * w + (tx + 1)] == 1
        val s = ty < h - 1 && t[(ty + 1) * w + tx] == 1
        val w_ = tx > 0 && t[ty * w + (tx - 1)] == 1

        if (n) mask = mask or 1
        if (e) mask = mask or 4
        if (s) mask = mask or 16
        if (w_) mask = mask or 64

        // Diagonais (Só contam se os cardeais adjacentes também forem paredes para evitar 'leaks')
        if (n && e && ty > 0 && tx < w - 1 && t[(ty - 1) * w + (tx + 1)] == 1) mask = mask or 2
        if (s && e && ty < h - 1 && tx < w - 1 && t[(ty + 1) * w + (tx + 1)] == 1) mask = mask or 8
        if (s && w_ && ty < h - 1 && tx > 0 && t[(ty + 1) * w + (tx - 1)] == 1) mask = mask or 32
        if (n && w_ && ty > 0 && tx > 0 && t[(ty - 1) * w + (tx - 1)] == 1) mask = mask or 128

        return mask
    }

    // --- MINA ---
    private fun texturaMina(canvas: Canvas, x: Float, y: Float, tw: Float, th: Float, p: BiomePalette, seed: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(100, 0, 0, 0)
        val rx = x + (seed % 5 + 2) * (tw / 8f)
        val ry = y + (seed % 4 + 1) * (th / 6f)
        canvas.drawLine(rx, ry, rx + tw * 0.3f, ry + th * 0.35f, paint)
        paint.style = Paint.Style.FILL
        if (seed % 4 == 0) {
            paint.color = Color.argb(200, 220, 190, 80)
            val mx = x + (seed * 3 % (tw * 0.7f).toInt().coerceAtLeast(1)) + tw * 0.1f
            val my = y + (seed * 7 % (th * 0.6f).toInt().coerceAtLeast(1)) + th * 0.15f
            canvas.drawRect(mx, my, mx + tw * 0.08f, my + th * 0.06f, paint)
        }
    }

    private fun texturaRiacho(canvas: Canvas, x: Float, y: Float, tw: Float, th: Float, p: BiomePalette, seed: Int) {
        paint.color = Color.argb(100, 100, 180, 255)
        paint.style = Paint.Style.STROKE
        val numGotas = 1 + seed % 3
        for (i in 0 until numGotas) {
            val gx = x + tw * (0.2f + i * 0.3f)
            canvas.drawLine(gx, y + th * 0.1f, gx, y + th * 0.7f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun texturaJardim(canvas: Canvas, x: Float, y: Float, tw: Float, th: Float, p: BiomePalette, seed: Int) {
        paint.color = Color.rgb(80, 55, 25)
        paint.style = Paint.Style.STROKE
        val ry = y + th * (0.3f + (seed % 3) * 0.15f)
        canvas.drawLine(x, ry, x + tw, ry + th * 0.15f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun texturaConstrucao(canvas: Canvas, x: Float, y: Float, tw: Float, th: Float, p: BiomePalette, seed: Int) {
        paint.color = Color.argb(120, 0, 0, 0)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(x, y + th * 0.33f, x + tw, y + th * 0.33f, paint)
        canvas.drawLine(x, y + th * 0.66f, x + tw, y + th * 0.66f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun texturaVulcanica(canvas: Canvas, x: Float, y: Float, tw: Float, th: Float, p: BiomePalette, seed: Int) {
        paint.color = Color.rgb(255, 100, 20)
        paint.style = Paint.Style.STROKE
        val fx = x + (seed % 4 + 1) * (tw / 6f)
        canvas.drawLine(fx, y + th * 0.15f, fx + tw * 0.15f, y + th * 0.85f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun texturaFloresta(canvas: Canvas, x: Float, y: Float, tw: Float, th: Float, p: BiomePalette, seed: Int) {
        paint.color = Color.argb(100, 60, 40, 20)
        paint.style = Paint.Style.STROKE
        val rx = x + tw * 0.4f
        canvas.drawLine(rx, y, rx, y + th, paint)
        paint.style = Paint.Style.FILL
    }

    fun renderEntradaTile(canvas: Canvas, x: Float, y: Float, tileW: Float, tileH: Float) {
        paint.color = Color.rgb(30, 120, 50)
        canvas.drawRect(x, y, x + tileW, y + tileH, paint)
    }

    fun renderSaidaTile(canvas: Canvas, x: Float, y: Float, tileW: Float, tileH: Float) {
        paint.color = Color.rgb(140, 100, 10)
        canvas.drawRect(x, y, x + tileW, y + tileH, paint)
    }

    fun renderDecorativeTile(
        canvas: Canvas, x: Float, y: Float, tileW: Float, tileH: Float,
        palette: BiomePalette, variant: Int,
        biome: Biome = Biome.MINA_ABANDONADA, tileX: Int = 0, tileY: Int = 0
    ) {
        renderFloorTile(canvas, x, y, tileW, tileH, palette, tileX, tileY)
        val cx = x + tileW / 2f
        val cy = y + tileH / 2f
        decorativoMina(canvas, cx, cy, tileW, tileH, palette, variant)
    }

    private fun renderCogumelo(canvas: Canvas, cx: Float, cy: Float, haste: Int, chapeu: Int, s: Float) {
        paint.color = haste
        canvas.drawRect(cx - s * 0.2f, cy, cx + s * 0.2f, cy + s * 0.8f, paint)
        paint.color = chapeu
        canvas.drawOval(RectF(cx - s, cy - s * 0.4f, cx + s, cy + s * 0.4f), paint)
    }

    private fun renderCristalDecorativo(canvas: Canvas, cx: Float, cy: Float, cor: Int, s: Float) {
        paint.color = cor
        path.reset()
        path.moveTo(cx, cy - s)
        path.lineTo(cx + s * 0.6f, cy)
        path.lineTo(cx, cy + s)
        path.lineTo(cx - s * 0.6f, cy)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun decorativoMina(canvas: Canvas, cx: Float, cy: Float, tw: Float, th: Float, p: BiomePalette, v: Int) {
        val s = tw * 0.2f
        renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
    }

    fun createTileBitmap(
        tileType: TileType, tileW: Int, tileH: Int, palette: BiomePalette,
        tileX: Int = 0, tileY: Int = 0, biome: Biome = Biome.MINA_ABANDONADA
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(tileW.coerceAtLeast(1), tileH.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        when (tileType) {
            TileType.WALL -> renderWallTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, tileX, tileY, null)
            TileType.FLOOR -> renderFloorTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, tileX, tileY)
            else -> renderFloorTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, tileX, tileY)
        }
        return bitmap
    }

    fun createWallBitmap(tileW: Int, tileH: Int, palette: BiomePalette, tileX: Int, tileY: Int, mazeData: MazeData): Bitmap {
        val margin = 16
        val bmpW = tileW + margin * 2
        val bmpH = tileH + margin * 2
        val bitmap = Bitmap.createBitmap(bmpW.coerceAtLeast(1), bmpH.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        renderWallTile(c, margin.toFloat(), margin.toFloat(), tileW.toFloat(), tileH.toFloat(), palette, tileX, tileY, mazeData)
        return bitmap
    }
    fun createTreeBitmap(tileW: Int, tileH: Int, palette: BiomePalette, tx: Int, ty: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(tileW.coerceAtLeast(1), (tileH * 2.2f).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        paint.color = Color.rgb(80, 55, 30)
        c.drawRect(tileW * 0.35f, 0f, tileW * 0.65f, tileH * 2f, paint)
        return bitmap
    }

    private fun variarCor(color: Int, delta: Int): Int = Color.rgb(
        (Color.red(color) + delta).coerceIn(0, 255),
        (Color.green(color) + delta).coerceIn(0, 255),
        (Color.blue(color) + delta).coerceIn(0, 255)
    )

    private fun clarear(color: Int, factor: Float): Int {
        val f = (factor * 255).toInt()
        return Color.rgb(
            (Color.red(color) + f).coerceAtMost(255),
            (Color.green(color) + f).coerceAtMost(255),
            (Color.blue(color) + f).coerceAtMost(255)
        )
    }

    private fun escurecer(color: Int, factor: Float): Int {
        val f = (factor * 255).toInt()
        return Color.rgb(
            (Color.red(color) - f).coerceAtLeast(0),
            (Color.green(color) - f).coerceAtLeast(0),
            (Color.blue(color) - f).coerceAtLeast(0)
        )
    }

    fun renderWallDetail(
        canvas: Canvas,
        x: Float, y: Float,
        tw: Float, th: Float,
        p: BiomePalette,
        tileX: Int, tileY: Int
    ) {
        if (worldAtual == BiomeWorld.ENTRANHAS || worldAtual == BiomeWorld.ABISMOS_AQUATICOS) return

        val detailType = p.wallDetailType
        if (detailType == WallDetailType.NONE) return

        val seed = tileX * 31 + tileY * 17
        val rng = Random(seed.toLong())

        // Configurar paint para detalhes
        paint.reset()
        paint.isAntiAlias = true

        when (detailType) {
            WallDetailType.CRYSTAL_VEIN -> {
                val crystalColor = p.crystalColor
                paint.color = crystalColor
                
                // 1. Linhas diagonais (veios do cristal)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = tw * 0.05f
                val numVeins = 1 + (seed % 2)
                for (i in 0 until numVeins) {
                    val startX = x + tw * (0.2f + i * 0.4f)
                    val startY = y + th * (0.1f + i * 0.3f)
                    val endX = startX + tw * 0.4f
                    val endY = startY + th * 0.5f
                    canvas.drawLine(startX, startY, endX, endY, paint)
                }

                // 2. Pequenos losangos incrustados
                paint.style = Paint.Style.FILL
                val numDiamonds = 1 + (seed % 2)
                for (i in 0 until numDiamonds) {
                    val cx = x + tw * (0.3f + rng.nextFloat() * 0.4f)
                    val cy = y + th * (0.3f + rng.nextFloat() * 0.4f)
                    val size = tw * (0.08f + rng.nextFloat() * 0.06f)
                    
                    path.reset()
                    path.moveTo(cx, cy - size)
                    path.lineTo(cx + size * 0.7f, cy)
                    path.lineTo(cx, cy + size)
                    path.lineTo(cx - size * 0.7f, cy)
                    path.close()
                    canvas.drawPath(path, paint)

                    // 3. Brilho especular nos vértices
                    if (rng.nextFloat() > 0.3f) {
                        paint.color = Color.WHITE
                        val specSize = tw * 0.03f
                        canvas.drawCircle(cx, cy - size, specSize, paint)
                        
                        val pulse = (sin(System.currentTimeMillis() * 0.01 + seed) * 1.5f + 1.5f).toFloat()
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1.5f
                        canvas.drawLine(cx - specSize * pulse, cy - size, cx + specSize * pulse, cy - size, paint)
                        canvas.drawLine(cx, cy - size - specSize * pulse, cx, cy - size + specSize * pulse, paint)
                        
                        paint.style = Paint.Style.FILL
                        paint.color = crystalColor
                    }
                }
            }
            WallDetailType.MOSS -> {
                val mossColor = p.accentColor
                
                // 1. Manchas circulares com alpha variável
                val numPatches = 3 + (seed % 3)
                for (i in 0 until numPatches) {
                    val cx = x + tw * (0.2f + rng.nextFloat() * 0.6f)
                    val cy = y + th * (0.2f + rng.nextFloat() * 0.6f)
                    val radius = tw * (0.1f + rng.nextFloat() * 0.15f)
                    val alphaVar = 100 + (rng.nextFloat() * 80).toInt()
                    paint.style = Paint.Style.FILL
                    paint.color = mossColor
                    paint.alpha = alphaVar
                    canvas.drawCircle(cx, cy, radius, paint)
                }

                // 2. Bordas orgânicas na borda inferior da parede
                paint.color = mossColor
                paint.alpha = 230
                paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(x, y + th)
                
                val segments = 4
                val segW = tw / segments
                for (i in 0 until segments) {
                    val startX = x + i * segW
                    val endX = startX + segW
                    val ctrlX = startX + segW / 2f
                    val curveHeight = th * (0.08f + rng.nextFloat() * 0.12f)
                    val ctrlY = y + th - curveHeight
                    
                    path.quadTo(ctrlX, ctrlY, endX, y + th)
                }
                path.lineTo(x + tw, y + th + 4f)
                path.lineTo(x, y + th + 4f)
                path.close()
                canvas.drawPath(path, paint)

                // 3. Gotículas
                paint.style = Paint.Style.FILL
                val numDroplets = 2 + (seed % 3)
                for (i in 0 until numDroplets) {
                    val dx = x + tw * rng.nextFloat()
                    val dy = y + th + (rng.nextFloat() * 8f)
                    val r = 2f + rng.nextFloat() * 2f
                    paint.alpha = 150 + (rng.nextFloat() * 100).toInt()
                    canvas.drawCircle(dx, dy, r, paint)
                }
            }
            WallDetailType.EMBER -> {
                val time = System.currentTimeMillis()
                
                // 1. Calor ondulante
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                val numHeatWaves = 2
                for (i in 0 until numHeatWaves) {
                    paint.color = Color.rgb(255, 120 + i * 50, 0)
                    paint.alpha = 50 - i * 20
                    
                    path.reset()
                    val freq = 0.05f
                    val amp = 3f
                    val speed = 0.005f
                    
                    var first = true
                    for (step in 0..10) {
                        val px = x + tw * (step / 10f)
                        val angle = (step * freq * tw) + (time * speed) + seed
                        val py = y + (th * 0.15f) + (sin(angle) * amp)
                        
                        if (first) {
                            path.moveTo(px, py)
                            first = false
                        } else {
                            path.lineTo(px, py)
                        }
                    }
                    canvas.drawPath(path, paint)
                }

                // 2. Partículas ascendentes
                paint.style = Paint.Style.FILL
                val numEmbers = 3 + (seed % 3)
                for (i in 0 until numEmbers) {
                    val partSeed = seed + i * 23
                    val pRng = Random(partSeed.toLong())
                    val startX = x + tw * (0.2f + pRng.nextFloat() * 0.6f)
                    
                    val duration = 1200L + (partSeed % 600)
                    val t = ((time + partSeed) % duration) / duration.toFloat()
                    
                    val py = y + th * 0.9f - (t * th * 0.8f)
                    val px = startX + sin(t * 10f + partSeed) * 5f
                    
                    val r = 255
                    val g = (100 + t * 155).toInt().coerceIn(0, 255)
                    val b = (t * 100).toInt().coerceIn(0, 255)
                    paint.color = Color.rgb(r, g, b)
                    paint.alpha = (255 * (1f - t)).toInt().coerceIn(0, 255)
                    
                    val radius = 1.5f + (1f - t) * 2f
                    canvas.drawCircle(px, py, radius, paint)
                }
            }
            WallDetailType.ICE_DRIP -> {
                // 1. Reflexo diagonal
                paint.color = Color.WHITE
                paint.alpha = 80
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                canvas.drawLine(x + tw * 0.2f, y + th * 0.2f, x + tw * 0.8f, y + th * 0.8f, paint)

                // 2. Estalactites triangulares
                paint.style = Paint.Style.FILL
                val numStalactites = 2 + (seed % 3)
                for (i in 0 until numStalactites) {
                    val cx = x + tw * (0.25f + i * 0.25f)
                    val cy = y
                    val w = tw * 0.12f
                    val h = th * (0.2f + rng.nextFloat() * 0.25f)
                    
                    paint.color = Color.rgb(180, 210, 240)
                    paint.alpha = 240
                    
                    path.reset()
                    path.moveTo(cx - w/2f, cy)
                    path.lineTo(cx, cy + h)
                    path.lineTo(cx + w/2f, cy)
                    path.close()
                    canvas.drawPath(path, paint)
                    
                    paint.color = Color.WHITE
                    paint.alpha = 180
                    path.reset()
                    path.moveTo(cx - w/2f, cy)
                    path.lineTo(cx, cy + h)
                    path.lineTo(cx - w/4f, cy)
                    path.close()
                    canvas.drawPath(path, paint)
                }
            }
            WallDetailType.RUNE_GLOW -> {
                val time = System.currentTimeMillis()
                val pulse = (120 + sin(time * 0.003f + seed) * 80f).toInt().coerceIn(0, 255)
                
                val cx = x + tw * 0.5f
                val cy = y + th * 0.45f
                val runeSize = tw * 0.15f
                
                // 1. Halo difuso
                val glowColor = p.accentColor
                paint.color = glowColor
                paint.style = Paint.Style.FILL
                paint.alpha = (pulse * 0.2f).toInt()
                canvas.drawCircle(cx, cy, runeSize * 2.2f, paint)

                // 2. Runa
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = tw * 0.05f
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = glowColor
                paint.alpha = pulse
                
                path.reset()
                when (seed % 4) {
                    0 -> {
                        path.moveTo(cx, cy + runeSize)
                        path.lineTo(cx, cy - runeSize)
                        path.moveTo(cx - runeSize * 0.7f, cy - runeSize * 0.3f)
                        path.lineTo(cx, cy - runeSize)
                        path.lineTo(cx + runeSize * 0.7f, cy - runeSize * 0.3f)
                    }
                    1 -> {
                        path.moveTo(cx - runeSize, cy - runeSize)
                        path.lineTo(cx + runeSize, cy + runeSize)
                        path.moveTo(cx + runeSize, cy - runeSize)
                        path.lineTo(cx - runeSize, cy + runeSize)
                        path.moveTo(cx - runeSize * 0.8f, cy)
                        path.lineTo(cx + runeSize * 0.8f, cy)
                    }
                    2 -> {
                        path.moveTo(cx - runeSize, cy - runeSize)
                        path.lineTo(cx + runeSize, cy - runeSize)
                        path.moveTo(cx - runeSize, cy + runeSize)
                        path.lineTo(cx + runeSize, cy + runeSize)
                        path.lineTo(cx - runeSize, cy - runeSize)
                    }
                    3 -> {
                        path.moveTo(cx, cy + runeSize)
                        path.lineTo(cx, cy - runeSize)
                        path.moveTo(cx - runeSize * 0.8f, cy - runeSize * 0.8f)
                        path.lineTo(cx, cy - runeSize * 0.2f)
                        path.moveTo(cx + runeSize * 0.8f, cy - runeSize * 0.8f)
                        path.lineTo(cx, cy - runeSize * 0.2f)
                    }
                }
                canvas.drawPath(path, paint)
                paint.strokeCap = Paint.Cap.BUTT
            }
            WallDetailType.NONE -> {}
        }

        // Restaurar paint
        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL
    }

    private fun renderEntranhasWall(
        canvas: Canvas, x: Float, y: Float, tw: Float, th: Float,
        seed: Int, tileX: Int, tileY: Int, s: Boolean
    ) {
        val baseR = 58  // 0x3a
        val baseG = 46  // 0x2e
        val baseB = 36  // 0x24
        
        val tileRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor seed.toLong()))

        // 1. Textura base: marrom-cinza escuro com variação de +-20% por pixel
        val pixelSize = 2f
        val cols = (tw / pixelSize).toInt().coerceAtLeast(1)
        val rows = (th / pixelSize).toInt().coerceAtLeast(1)
        
        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL
        
        // Pinta a base sólida
        paint.color = Color.rgb(baseR, baseG, baseB)
        canvas.drawRect(x, y, x + tw, y + th, paint)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val factor = -0.20f + tileRng.nextFloat() * 0.40f
                val vr = (baseR * (1f + factor)).toInt().coerceIn(0, 255)
                val vg = (baseG * (1f + factor)).toInt().coerceIn(0, 255)
                val vb = (baseB * (1f + factor)).toInt().coerceIn(0, 255)
                paint.color = Color.rgb(vr, vg, vb)
                canvas.drawRect(
                    x + c * pixelSize, 
                    y + r * pixelSize, 
                    (x + (c + 1) * pixelSize).coerceAtMost(x + tw), 
                    (y + (r + 1) * pixelSize).coerceAtMost(y + th), 
                    paint
                )
            }
        }

        // 2. Rachaduras diagonais em 40% dos tiles (linhas de 10-20px)
        if (tileRng.nextFloat() < 0.40f) {
            paint.color = Color.rgb(18, 14, 11)
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            
            val rx = x + 6f + tileRng.nextFloat() * (tw - 26f)
            val ry = y + 6f + tileRng.nextFloat() * (th - 26f)
            val length = 10f + tileRng.nextFloat() * 10f
            val angle = if (tileRng.nextBoolean()) (Math.PI / 4.0) else (3.0 * Math.PI / 4.0)
            val endX = (rx + cos(angle) * length).toFloat()
            val endY = (ry + sin(angle) * length).toFloat()
            
            canvas.drawLine(rx, ry, endX, endY, paint)
            
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0f
        }

        // 3. Pequenas pedras salientes: drawCircle de 3-5px em 15% dos tiles, cor+25%
        if (tileRng.nextFloat() < 0.15f) {
            val salR = (baseR * 1.25f).toInt().coerceIn(0, 255)
            val salG = (baseG * 1.25f).toInt().coerceIn(0, 255)
            val salB = (baseB * 1.25f).toInt().coerceIn(0, 255)
            
            val radius = 3f + tileRng.nextFloat() * 2f
            val px = x + radius + 4f + tileRng.nextFloat() * (tw - radius * 2f - 8f)
            val py = y + radius + 4f + tileRng.nextFloat() * (th - radius * 2f - 8f)
            
            paint.color = Color.rgb(15, 12, 10)
            canvas.drawCircle(px, py + 1.5f, radius, paint)
            
            paint.color = Color.rgb(salR, salG, salB)
            canvas.drawCircle(px, py, radius, paint)
            
            paint.color = clarear(Color.rgb(salR, salG, salB), 0.3f)
            canvas.drawCircle(px - radius * 0.3f, py - radius * 0.3f, radius * 0.3f, paint)
        }

        // 4. Borda inferior: dentes de 2-4px de altura, espaçados irregularmente a cada 3-6px
        if (!s) {
            paint.color = Color.rgb(20, 16, 12)
            paint.style = Paint.Style.FILL
            
            var curX = x
            while (curX < x + tw) {
                val toothW = 3f + tileRng.nextFloat() * 3f
                val toothH = 2f + tileRng.nextFloat() * 2f
                
                canvas.drawRect(
                    curX, 
                    y + th, 
                    (curX + toothW).coerceAtMost(x + tw), 
                    y + th + toothH, 
                    paint
                )
                curX += toothW
            }
        }

        // 5. Gotas d'água: 1-2 gotas por tile em 20% dos tiles (circle 2px, azul #4a6a8a)
        if (tileRng.nextFloat() < 0.20f) {
            paint.color = Color.parseColor("#4a6a8a")
            val numDrops = 1 + tileRng.nextInt(2)
            for (i in 0 until numDrops) {
                val dx = x + 6f + tileRng.nextFloat() * (tw - 12f)
                val dy = y + 6f + tileRng.nextFloat() * (th - 12f)
                
                canvas.drawCircle(dx, dy, 2f, paint)
                
                paint.color = Color.parseColor("#8faac4")
                canvas.drawCircle(dx - 0.5f, dy - 0.5f, 0.7f, paint)
                paint.color = Color.parseColor("#4a6a8a")
            }
        }
        
        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL
    }

    private fun renderAbismoWall(
        canvas: Canvas, x: Float, y: Float, tw: Float, th: Float,
        seed: Int, tileX: Int, tileY: Int,
        n: Boolean, s: Boolean, e: Boolean, w: Boolean
    ) {
        val baseColorHex = "#1a2a3a"
        val baseColor = Color.parseColor(baseColorHex)
        val baseR = 26
        val baseG = 42
        val baseB = 58

        val tileRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor seed.toLong()))

        val pixelSize = 2f
        val cols = (tw / pixelSize).toInt().coerceAtLeast(1)
        val rows = (th / pixelSize).toInt().coerceAtLeast(1)

        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL

        // 1. Desenhar a base com cantos expostos arredondados suavemente (raio 6px) e sem dentes laterais
        val needsRounding = (!n && !w) || (!n && !e) || (!s && !e) || (!s && !w)
        if (needsRounding) {
            val radii = floatArrayOf(
                if (!n && !w) 6f else 0f, if (!n && !w) 6f else 0f, // Top-left
                if (!n && !e) 6f else 0f, if (!n && !e) 6f else 0f, // Top-right
                if (!s && !e) 6f else 0f, if (!s && !e) 6f else 0f, // Bottom-right
                if (!s && !w) 6f else 0f, if (!s && !w) 6f else 0f  // Bottom-left
            )
            path.reset()
            path.addRoundRect(RectF(x, y, x + tw, y + th), radii, Path.Direction.CW)
            
            // Desenhar base sólida sob o path
            paint.color = baseColor
            canvas.drawPath(path, paint)
            
            // Salvar clipe para manter os pixels internos perfeitamente alinhados ao path arredondado!
            canvas.save()
            canvas.clipPath(path)
        } else {
            // Desenhar base sólida retangular
            paint.color = baseColor
            canvas.drawRect(x, y, x + tw, y + th, paint)
        }

        // 2. Textura de gelo: pixels em diagonal (45°) com tons alternados claro/escuro alinhados globalmente
        val colsGlobalOffset = tileX * cols
        val rowsGlobalOffset = tileY * rows

        for (r in 0 until rows) {
            // Se houver vizinho ao norte, a primeira linha de pixels é cor base sólida
            // Se houver vizinho ao sul, a última linha de pixels é cor base sólida
            // Isso garante 100% de fusão nas junções horizontais
            if (n && r == 0) continue
            if (s && r == rows - 1) continue

            for (c in 0 until cols) {
                val globalCol = colsGlobalOffset + c
                val globalRow = rowsGlobalOffset + r
                
                // 45° Slanted Stripe Formula: globalCol + globalRow
                val diag = globalCol + globalRow
                val stripe = diag % 8
                
                val factor = when (stripe) {
                    0, 1 -> 0.12f  // Claro
                    4, 5 -> -0.12f // Escuro
                    else -> 0f      // Base
                }
                
                if (factor != 0f) {
                    val vr = (baseR * (1f + factor)).toInt().coerceIn(0, 255)
                    val vg = (baseG * (1f + factor)).toInt().coerceIn(0, 255)
                    val vb = (baseB * (1f + factor)).toInt().coerceIn(0, 255)
                    paint.color = Color.rgb(vr, vg, vb)
                    canvas.drawRect(
                        x + c * pixelSize,
                        y + r * pixelSize,
                        (x + (c + 1) * pixelSize).coerceAtMost(x + tw),
                        (y + (r + 1) * pixelSize).coerceAtMost(y + th),
                        paint
                    )
                }
            }
        }

        // 3. Reflexo especular: linha diagonal de pixels brancos (alpha 60-80) em 50% dos tiles
        if (tileRng.nextFloat() < 0.50f) {
            val specAlpha = 60 + tileRng.nextInt(21) // alpha 60-80
            paint.color = Color.argb(specAlpha, 255, 255, 255)
            
            // Escolhe uma diagonal aleatória que corte no meio do tile
            val targetDiag = 6 + tileRng.nextInt((cols - 6).coerceAtLeast(1))
            val specLength = 8 + tileRng.nextInt(7) // comprimento 8-14px
            val startC = tileRng.nextInt((cols - specLength).coerceAtLeast(1))
            
            for (i in 0 until specLength) {
                val c = startC + i
                val r = targetDiag - c
                
                // Não desenha sobre a linha de fusão se houver vizinhos
                if (n && r == 0) continue
                if (s && r == rows - 1) continue
                
                if (r in 0 until rows && c in 0 until cols) {
                    canvas.drawRect(
                        x + c * pixelSize,
                        y + r * pixelSize,
                        (x + (c + 1) * pixelSize).coerceAtMost(x + tw),
                        (y + (r + 1) * pixelSize).coerceAtMost(y + th),
                        paint
                    )
                }
            }
        }

        // Restaurar clipe se aplicamos arredondamento
        if (needsRounding) {
            canvas.restore()
        }

        // 4. Estalactites na borda SUPERIOR: triângulos de 4-8px de altura, 3-5px de base
        if (!n) {
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.FILL

            val spacing = 6f + tileRng.nextFloat() * 4f // Espaçamento irregular 6-10px
            var curX = x + 2f
            while (curX < x + tw - 2f) {
                val baseW = 3f + tileRng.nextFloat() * 2f // 3-5px de base
                val height = 4f + tileRng.nextFloat() * 4f // 4-8px de altura
                
                val cx = curX + baseW / 2f
                if (cx + baseW / 2f < x + tw) {
                    path.reset()
                    path.moveTo(cx - baseW / 2f, y)
                    path.lineTo(cx + baseW / 2f, y)
                    path.lineTo(cx, y + height)
                    path.close()
                    
                    val gradient = LinearGradient(
                        cx, y, cx, y + height,
                        Color.parseColor("#8ab8d8"),
                        Color.parseColor("#c8e8f8"),
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = gradient
                    canvas.drawPath(path, paint)
                    paint.shader = null
                }
                curX += baseW + spacing
            }
        }

        // Restaurar estado padrão do paint
        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL
    }
}
