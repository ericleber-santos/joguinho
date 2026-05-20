package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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

    private var worldAtual: BiomeWorld = BiomeWorld.ENTRANHAS
    
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
            renderEntranhasWall(canvas, x, y, tw, th, seed, tileX, tileY, n, s, e, w)
            return
        }

        if (worldAtual == BiomeWorld.ABISMOS_AQUATICOS) {
            renderAbismoWall(canvas, x, y, tw, th, seed, tileX, tileY, n, s, e, w)
            return
        }

        if (worldAtual == BiomeWorld.JARDIM_PROFUNDO) {
            renderJardimWall(canvas, x, y, tw, th, seed, tileX, tileY, n, s, e, w)
            return
        }

        if (worldAtual == BiomeWorld.NUCLEO_DE_FOGO) {
            renderNucleoWall(canvas, x, y, tw, th, seed, tileX, tileY, n, s, e, w)
            return
        }

        if (worldAtual == BiomeWorld.REINO_DA_MAGIA) {
            renderReinoMagiaWall(canvas, x, y, tw, th, seed, tileX, tileY, n, s, e, w)
            return
        }

        if (worldAtual == BiomeWorld.MINAS_RIQUEZAS) {
            renderMinasWall(canvas, x, y, tw, th, seed, tileX, tileY, n, s, e, w)
            return
        }

        // 1. Base sólida (mesma cor entre vizinhos = sem linhas de grid)
        paint.color = corBase
        canvas.drawRect(x, y, x + tw, y + th, paint)

        // 2. Textura de pedra com ruído procedural
        val pixelSize = 2f
        val cols = (tw / pixelSize).toInt().coerceAtLeast(1)
        val rows = (th / pixelSize).toInt().coerceAtLeast(1)
        val tileRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor seed.toLong()))

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if ((n && r == 0) || (s && r == rows - 1) || (w && c == 0) || (e && c == cols - 1)) {
                    continue
                }
                val factor = -0.20f + tileRng.nextFloat() * 0.40f
                val vr = (Color.red(corBase) * (1f + factor)).toInt().coerceIn(0, 255)
                val vg = (Color.green(corBase) * (1f + factor)).toInt().coerceIn(0, 255)
                val vb = (Color.blue(corBase) * (1f + factor)).toInt().coerceIn(0, 255)
                paint.color = Color.rgb(vr, vg, vb)
                canvas.drawRect(
                    x + c * pixelSize,
                    y + r * pixelSize,
                    (x + (c+1) * pixelSize).coerceAtMost(x + tw),
                    (y + (r+1) * pixelSize).coerceAtMost(y + th),
                    paint
                )
            }
        }

        // 3. Borda inferior dentada orgânica (dentro do tile)
        if (!s) {
            var curX = x
            val toothRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor 9999L))
            while (curX < x + tw) {
                val toothW = 2f + toothRng.nextFloat() * 4f
                val toothH = 2f + toothRng.nextFloat() * 4f
                val toothColor = Color.rgb(
                    (Color.red(corBase) * 0.55f).toInt().coerceIn(0,255),
                    (Color.green(corBase) * 0.55f).toInt().coerceIn(0,255),
                    (Color.blue(corBase) * 0.55f).toInt().coerceIn(0,255)
                )
                paint.color = toothColor
                canvas.drawRect(
                    curX,
                    y + th - toothH,
                    (curX + toothW).coerceAtMost(x + tw),
                    y + th,
                    paint
                )
                curX += toothW + toothRng.nextFloat() * 2f
            }

            paint.color = Color.rgb(
                (Color.red(corBase) * 0.35f).toInt().coerceIn(0,255),
                (Color.green(corBase) * 0.35f).toInt().coerceIn(0,255),
                (Color.blue(corBase) * 0.35f).toInt().coerceIn(0,255)
            )
            canvas.drawRect(x, y + th - 1f, x + tw, y + th, paint)
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
                val mossColors = intArrayOf(
                    Color.parseColor("#2d6a2d"),
                    Color.parseColor("#3d8a3d"),
                    Color.parseColor("#1e4a1e")
                )
                val tileRng2 = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor 1000L))
                val pixelSize = 2f
                val cols = (tw / pixelSize).toInt()
                val rows = (th / pixelSize).toInt()

                // Musgo cresce da borda inferior para cima (3-5 linhas)
                val mossHeight = 3 + tileRng2.nextInt(3)
                for (r in 0 until mossHeight) {
                    for (c in 0 until cols) {
                        val prob = 0.85f - r * 0.18f
                        if (tileRng2.nextFloat() < prob) {
                            paint.color = mossColors[tileRng2.nextInt(mossColors.size)]
                            paint.alpha = 255
                            val py = rows - 1 - r
                            if (py >= 0) canvas.drawRect(
                                x + c * pixelSize, y + py * pixelSize,
                                (x + (c+1) * pixelSize).coerceAtMost(x + tw),
                                (y + (py+1) * pixelSize).coerceAtMost(y + th), paint
                            )
                        }
                    }
                }

                // Gotas caindo da borda superior (só se exposto ao norte)
                if (!n) {
                    paint.color = Color.parseColor("#2d6a2d")
                    paint.strokeWidth = 1f
                    paint.style = Paint.Style.STROKE
                    val numDrips = 2 + tileRng2.nextInt(4)
                    for (i in 0 until numDrips) {
                        val dx = x + 2f + tileRng2.nextFloat() * (tw - 4f)
                        val dripLen = 3f + tileRng2.nextFloat() * 9f
                        canvas.drawLine(dx, y, dx, y + dripLen, paint)
                    }
                    paint.style = Paint.Style.FILL
                    paint.strokeWidth = 0f
                }
                paint.alpha = 255
            }
            WallDetailType.ICE_DRIP -> {
                // 1. Reflexo integrado procedural
                val tileRngIce = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor 2000L))
                val specCount = 3 + tileRngIce.nextInt(3)
                for (i in 0 until specCount) {
                    val sx = x + tileRngIce.nextFloat() * tw
                    val sy = y + tileRngIce.nextFloat() * th
                    paint.color = Color.argb(70, 255, 255, 255)
                    canvas.drawRect(sx, sy, sx + 2f, sy + 2f, paint)
                    canvas.drawRect(sx + 2f, sy + 2f, sx + 4f, sy + 4f, paint)
                }
                paint.style = Paint.Style.FILL
                paint.alpha = 255

                // 2. Estalactites triangulares
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
        if (!s && (worldAtual.name.contains("FLORESTA") || worldAtual.name.contains("JARDIM") || worldAtual.name.contains("ENTRANHAS"))) {
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
        world: BiomeWorld = BiomeWorld.ENTRANHAS, tileX: Int = 0, tileY: Int = 0
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
        tileX: Int = 0, tileY: Int = 0, world: BiomeWorld = BiomeWorld.ENTRANHAS
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
        if (worldAtual == BiomeWorld.ENTRANHAS || worldAtual == BiomeWorld.ABISMOS_AQUATICOS || worldAtual == BiomeWorld.JARDIM_PROFUNDO || worldAtual == BiomeWorld.NUCLEO_DE_FOGO || worldAtual == BiomeWorld.REINO_DA_MAGIA || worldAtual == BiomeWorld.MINAS_RIQUEZAS) return

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
                val mossColors = intArrayOf(
                    Color.parseColor("#2d6a2d"),
                    Color.parseColor("#3d8a3d"),
                    Color.parseColor("#1e4a1e")
                )
                val tileRng2 = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor 1000L))
                val pixelSize = 2f
                val cols = (tw / pixelSize).toInt()
                val rows = (th / pixelSize).toInt()

                // Musgo cresce da borda inferior para cima (3-5 linhas)
                val mossHeight = 3 + tileRng2.nextInt(3)
                for (r in 0 until mossHeight) {
                    for (c in 0 until cols) {
                        val prob = 0.85f - r * 0.18f
                        if (tileRng2.nextFloat() < prob) {
                            paint.color = mossColors[tileRng2.nextInt(mossColors.size)]
                            paint.alpha = 255
                            val py = rows - 1 - r
                            if (py >= 0) canvas.drawRect(
                                x + c * pixelSize, y + py * pixelSize,
                                (x + (c+1) * pixelSize).coerceAtMost(x + tw),
                                (y + (py+1) * pixelSize).coerceAtMost(y + th), paint
                            )
                        }
                    }
                }
                paint.alpha = 255
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
                // 1. Reflexo integrado procedural
                val tileRngIce = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor 2000L))
                val specCount = 3 + tileRngIce.nextInt(3)
                for (i in 0 until specCount) {
                    val sx = x + tileRngIce.nextFloat() * tw
                    val sy = y + tileRngIce.nextFloat() * th
                    paint.color = Color.argb(70, 255, 255, 255)
                    canvas.drawRect(sx, sy, sx + 2f, sy + 2f, paint)
                    canvas.drawRect(sx + 2f, sy + 2f, sx + 4f, sy + 4f, paint)
                }
                paint.alpha = 255

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
                paint.alpha = 255
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
        seed: Int, tileX: Int, tileY: Int,
        n: Boolean, s: Boolean, e: Boolean, w: Boolean
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
                if ((n && r == 0) || (s && r == rows - 1) || (w && c == 0) || (e && c == cols - 1)) {
                    continue
                }
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

        // 4. Borda inferior dentada orgânica
        if (!s) {
            val baseColor = Color.rgb(baseR, baseG, baseB)
            var curX = x
            val toothRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor 9999L))
            while (curX < x + tw) {
                val toothW = 2f + toothRng.nextFloat() * 4f
                val toothH = 2f + toothRng.nextFloat() * 4f
                val toothColor = Color.rgb(
                    (Color.red(baseColor) * 0.55f).toInt().coerceIn(0,255),
                    (Color.green(baseColor) * 0.55f).toInt().coerceIn(0,255),
                    (Color.blue(baseColor) * 0.55f).toInt().coerceIn(0,255)
                )
                paint.color = toothColor
                canvas.drawRect(
                    curX,
                    y + th - toothH,
                    (curX + toothW).coerceAtMost(x + tw),
                    y + th,
                    paint
                )
                curX += toothW + toothRng.nextFloat() * 2f
            }

            paint.color = Color.rgb(
                (Color.red(baseColor) * 0.35f).toInt().coerceIn(0,255),
                (Color.green(baseColor) * 0.35f).toInt().coerceIn(0,255),
                (Color.blue(baseColor) * 0.35f).toInt().coerceIn(0,255)
            )
            canvas.drawRect(x, y + th - 1f, x + tw, y + th, paint)
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
                if (w && c == 0) continue
                if (e && c == cols - 1) continue
                
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
        // 5. Borda inferior dentada orgânica
        if (!s) {
            var curX = x
            val toothRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor 9999L))
            while (curX < x + tw) {
                val toothW = 2f + toothRng.nextFloat() * 4f
                val toothH = 2f + toothRng.nextFloat() * 4f
                val toothColor = Color.rgb(
                    (Color.red(baseColor) * 0.55f).toInt().coerceIn(0,255),
                    (Color.green(baseColor) * 0.55f).toInt().coerceIn(0,255),
                    (Color.blue(baseColor) * 0.55f).toInt().coerceIn(0,255)
                )
                paint.color = toothColor
                canvas.drawRect(
                    curX,
                    y + th - toothH,
                    (curX + toothW).coerceAtMost(x + tw),
                    y + th,
                    paint
                )
                curX += toothW + toothRng.nextFloat() * 2f
            }

            paint.color = Color.rgb(
                (Color.red(baseColor) * 0.35f).toInt().coerceIn(0,255),
                (Color.green(baseColor) * 0.35f).toInt().coerceIn(0,255),
                (Color.blue(baseColor) * 0.35f).toInt().coerceIn(0,255)
            )
            canvas.drawRect(x, y + th - 1f, x + tw, y + th, paint)
        }

        // Restaurar estado padrão do paint
        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL
    }

    private fun renderJardimWall(
        canvas: Canvas, x: Float, y: Float, tw: Float, th: Float,
        seed: Int, tileX: Int, tileY: Int,
        n: Boolean, s: Boolean, e: Boolean, w: Boolean
    ) {
        // Paleta base: pedra escura esverdeada
        val baseR = 26  // 0x1a
        val baseG = 42  // 0x2a
        val baseB = 26  // 0x1a
        val baseColor = Color.rgb(baseR, baseG, baseB)

        val tileRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor seed.toLong()))

        val pixelSize = 2f
        val cols = (tw / pixelSize).toInt().coerceAtLeast(1)
        val rows = (th / pixelSize).toInt().coerceAtLeast(1)

        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL

        // 1. Textura base: pedra escura esverdeada com variação sutil por pixel
        paint.color = baseColor
        canvas.drawRect(x, y, x + tw, y + th, paint)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if ((n && r == 0) || (s && r == rows - 1) || (w && c == 0) || (e && c == cols - 1)) {
                    continue
                }
                val factor = -0.12f + tileRng.nextFloat() * 0.24f
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

        // 2. Musgo INTEGRADO: clusters de pixels verdes na borda inferior e laterais
        val mossColors = intArrayOf(Color.parseColor("#2d6a2d"), Color.parseColor("#3d8a3d"))

        // Borda inferior: musgo cresce de baixo para cima (3-6 linhas de pixels)
        if (!s) {
            val mossHeight = 3 + tileRng.nextInt(4) // 3-6 pixels de altura
            for (r in 0 until mossHeight) {
                for (c in 0 until cols) {
                    // Probabilidade diminui com a altura (mais musgo embaixo)
                    val prob = 0.85f - r * 0.15f
                    if (tileRng.nextFloat() < prob) {
                        paint.color = mossColors[tileRng.nextInt(2)]
                        val py = rows - 1 - r
                        if (py >= 0) {
                            canvas.drawRect(
                                x + c * pixelSize,
                                y + py * pixelSize,
                                (x + (c + 1) * pixelSize).coerceAtMost(x + tw),
                                (y + (py + 1) * pixelSize).coerceAtMost(y + th),
                                paint
                            )
                        }
                    }
                }
            }
        }

        // Borda lateral esquerda: musgo esparso (2-3 pixels de largura)
        if (!w) {
            val mossW = 2 + tileRng.nextInt(2)
            for (c in 0 until mossW) {
                for (r in 0 until rows) {
                    if (tileRng.nextFloat() < 0.5f) {
                        paint.color = mossColors[tileRng.nextInt(2)]
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
        }

        // Borda lateral direita: musgo esparso
        if (!e) {
            val mossW = 2 + tileRng.nextInt(2)
            for (c in 0 until mossW) {
                for (r in 0 until rows) {
                    if (tileRng.nextFloat() < 0.5f) {
                        paint.color = mossColors[tileRng.nextInt(2)]
                        val px = cols - 1 - c
                        if (px >= 0) {
                            canvas.drawRect(
                                x + px * pixelSize,
                                y + r * pixelSize,
                                (x + (px + 1) * pixelSize).coerceAtMost(x + tw),
                                (y + (r + 1) * pixelSize).coerceAtMost(y + th),
                                paint
                            )
                        }
                    }
                }
            }
        }

        // 3. Gotas de musgo: linhas de 1px caindo da borda superior, comprimento 4-12px
        if (!n) {
            paint.color = Color.parseColor("#2d6a2d")
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE

            val numDrips = 3 + tileRng.nextInt(4) // 3-6 gotas
            for (i in 0 until numDrips) {
                val dx = x + 3f + tileRng.nextFloat() * (tw - 6f)
                val dripLen = 4f + tileRng.nextFloat() * 8f // 4-12px
                canvas.drawLine(dx, y, dx, y + dripLen, paint)
            }
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0f
        }

        // 4. Raízes: linhas curvas de 1px em marrom escuro (#3a2010) cruzando o tile
        val numRoots = 1 + tileRng.nextInt(2) // 1-2 raízes
        paint.color = Color.parseColor("#3a2010")
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        paint.isAntiAlias = true

        for (i in 0 until numRoots) {
            path.reset()
            val startX = x + tileRng.nextFloat() * tw
            val startY = y + tileRng.nextFloat() * th * 0.3f
            val endX = x + tileRng.nextFloat() * tw
            val endY = y + th * 0.7f + tileRng.nextFloat() * th * 0.3f
            val ctrlX = x + tileRng.nextFloat() * tw
            val ctrlY = y + th * 0.3f + tileRng.nextFloat() * th * 0.4f

            path.moveTo(startX, startY)
            path.quadTo(ctrlX, ctrlY, endX, endY)
            canvas.drawPath(path, paint)
        }

        paint.style = Paint.Style.FILL
        paint.isAntiAlias = false
        paint.strokeWidth = 0f

        // 5. Pontinhos luminosos: 3-5 pixels de verde-limão (#aaff44) bioluminescentes
        val sporeColor = Color.parseColor("#aaff44")
        val numSpores = 3 + tileRng.nextInt(3) // 3-5 esporos

        for (i in 0 until numSpores) {
            val sx = x + 4f + tileRng.nextFloat() * (tw - 8f)
            val sy = y + 4f + tileRng.nextFloat() * (th - 8f)

            // RadialGradient sutil em torno do esporo (raio 4px, alpha 40)
            paint.isAntiAlias = true
            val glowGradient = RadialGradient(
                sx, sy, 4f,
                Color.argb(40, 170, 255, 68),
                Color.argb(0, 170, 255, 68),
                Shader.TileMode.CLAMP
            )
            paint.shader = glowGradient
            canvas.drawCircle(sx, sy, 4f, paint)
            paint.shader = null
            paint.isAntiAlias = false

            // Ponto central brilhante de 1px
            paint.color = sporeColor
            canvas.drawRect(sx - 0.5f, sy - 0.5f, sx + 0.5f, sy + 0.5f, paint)
        }
        // 6. Borda inferior dentada orgânica
        if (!s) {
            var curX = x
            val toothRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor 9999L))
            while (curX < x + tw) {
                val toothW = 2f + toothRng.nextFloat() * 4f
                val toothH = 2f + toothRng.nextFloat() * 4f
                val toothColor = Color.rgb(
                    (Color.red(baseColor) * 0.55f).toInt().coerceIn(0,255),
                    (Color.green(baseColor) * 0.55f).toInt().coerceIn(0,255),
                    (Color.blue(baseColor) * 0.55f).toInt().coerceIn(0,255)
                )
                paint.color = toothColor
                canvas.drawRect(
                    curX,
                    y + th - toothH,
                    (curX + toothW).coerceAtMost(x + tw),
                    y + th,
                    paint
                )
                curX += toothW + toothRng.nextFloat() * 2f
            }

            paint.color = Color.rgb(
                (Color.red(baseColor) * 0.35f).toInt().coerceIn(0,255),
                (Color.green(baseColor) * 0.35f).toInt().coerceIn(0,255),
                (Color.blue(baseColor) * 0.35f).toInt().coerceIn(0,255)
            )
            canvas.drawRect(x, y + th - 1f, x + tw, y + th, paint)
        }

        // Restaurar estado padrão do paint
        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL
    }

    private fun renderNucleoWall(
        canvas: Canvas, x: Float, y: Float, tw: Float, th: Float,
        seed: Int, tileX: Int, tileY: Int,
        n: Boolean, s: Boolean, e: Boolean, w: Boolean
    ) {
        // Paleta: basalto preto
        val baseR = 26   // 0x1a
        val baseG = 18   // 0x12
        val baseB = 16   // 0x10
        val baseColor = Color.rgb(baseR, baseG, baseB)

        val tileRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor seed.toLong()))

        val pixelSize = 2f
        val cols = (tw / pixelSize).toInt().coerceAtLeast(1)
        val rows = (th / pixelSize).toInt().coerceAtLeast(1)

        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL

        // 1. Textura base: basalto preto com variação sutil
        paint.color = baseColor
        canvas.drawRect(x, y, x + tw, y + th, paint)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if ((n && r == 0) || (s && r == rows - 1) || (w && c == 0) || (e && c == cols - 1)) {
                    continue
                }
                val factor = -0.10f + tileRng.nextFloat() * 0.20f
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

        // 2. Rachaduras de lava: linhas de 1px em laranja/vermelho em 60% dos tiles
        if (tileRng.nextFloat() < 0.60f) {
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE
            paint.isAntiAlias = true

            val numCracks = 1 + tileRng.nextInt(3) // 1-3 rachaduras
            for (i in 0 until numCracks) {
                // Cor no espectro #ff3300 → #ff8800 (NUNCA vermelho puro)
                val t = tileRng.nextFloat()
                val crackR = 255
                val crackG = (51 + t * 85).toInt().coerceIn(51, 136) // 0x33 → 0x88
                val crackB = 0
                paint.color = Color.rgb(crackR, crackG, crackB)

                path.reset()
                val startX = x + tileRng.nextFloat() * tw
                val startY = y + tileRng.nextFloat() * th * 0.3f
                path.moveTo(startX, startY)

                val segments = 2 + tileRng.nextInt(3)
                var curCX = startX
                var curCY = startY
                for (seg in 0 until segments) {
                    val dx = -8f + tileRng.nextFloat() * 16f
                    val dy = 4f + tileRng.nextFloat() * 8f
                    curCX = (curCX + dx).coerceIn(x, x + tw)
                    curCY = (curCY + dy).coerceIn(y, y + th)
                    path.lineTo(curCX, curCY)
                }
                canvas.drawPath(path, paint)
            }
            paint.style = Paint.Style.FILL
            paint.isAntiAlias = false
            paint.strokeWidth = 0f
        }

        // 3. Veio de magma: linha diagonal de 2-3px com RadialGradient de brilho
        if (tileRng.nextFloat() < 0.40f) {
            paint.isAntiAlias = true
            paint.strokeWidth = 2f + tileRng.nextFloat()
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#ff6600")

            val vx1 = x + tileRng.nextFloat() * tw * 0.3f
            val vy1 = y + tileRng.nextFloat() * th * 0.3f
            val vx2 = x + tw * 0.7f + tileRng.nextFloat() * tw * 0.3f
            val vy2 = y + th * 0.7f + tileRng.nextFloat() * th * 0.3f

            canvas.drawLine(vx1, vy1, vx2, vy2, paint)

            // Brilho RadialGradient no centro do veio
            val cx = (vx1 + vx2) / 2f
            val cy = (vy1 + vy2) / 2f
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0f
            val glowGradient = RadialGradient(
                cx, cy, 8f,
                Color.argb(60, 255, 102, 0),
                Color.argb(0, 255, 102, 0),
                Shader.TileMode.CLAMP
            )
            paint.shader = glowGradient
            canvas.drawCircle(cx, cy, 8f, paint)
            paint.shader = null
            paint.isAntiAlias = false
        }

        // 4. Borda inferior dentada orgânica
        if (!s) {
            var curX = x
            val toothRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor 9999L))
            while (curX < x + tw) {
                val toothW = 2f + toothRng.nextFloat() * 4f
                val toothH = 2f + toothRng.nextFloat() * 4f
                val toothColor = Color.rgb(
                    (Color.red(baseColor) * 0.55f).toInt().coerceIn(0,255),
                    (Color.green(baseColor) * 0.55f).toInt().coerceIn(0,255),
                    (Color.blue(baseColor) * 0.55f).toInt().coerceIn(0,255)
                )
                paint.color = toothColor
                canvas.drawRect(
                    curX,
                    y + th - toothH,
                    (curX + toothW).coerceAtMost(x + tw),
                    y + th,
                    paint
                )
                curX += toothW + toothRng.nextFloat() * 2f
            }

            paint.color = Color.rgb(
                (Color.red(baseColor) * 0.35f).toInt().coerceIn(0,255),
                (Color.green(baseColor) * 0.35f).toInt().coerceIn(0,255),
                (Color.blue(baseColor) * 0.35f).toInt().coerceIn(0,255)
            )
            canvas.drawRect(x, y + th - 1f, x + tw, y + th, paint)
        }

        // 5. Partículas de brasa animadas: 2-4 circles de 1px em amarelo (#ffcc00)
        val time = System.currentTimeMillis()
        val numEmbers = 2 + (seed % 3) // 2-4 partículas
        paint.color = Color.parseColor("#ffcc00")

        for (i in 0 until numEmbers) {
            val baseEX = x + 4f + tileRandom(tileX, tileY, i * 311 + 700) * (tw - 8f)
            // Animação: as partículas sobem lentamente via sin(time)
            val phase = tileX * 0.7f + tileY * 0.3f + i * 1.5f
            val animOffset = sin((time / 800.0 + phase).toFloat()) * 3f
            val baseEY = y + 4f + tileRandom(tileX, tileY, i * 311 + 800) * (th - 8f)
            val ey = baseEY + animOffset

            if (ey in y..(y + th)) {
                canvas.drawCircle(baseEX, ey, 1f, paint)
            }
        }

        // Restaurar estado padrão do paint
        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL
    }

    private fun renderReinoMagiaWall(
        canvas: Canvas, x: Float, y: Float, tw: Float, th: Float,
        seed: Int, tileX: Int, tileY: Int,
        n: Boolean, s: Boolean, e: Boolean, w: Boolean
    ) {
        // Paleta base: pedra índigo (#1a1030)
        val baseR = 26   // 0x1a
        val baseG = 16   // 0x10
        val baseB = 48   // 0x30
        val baseColor = Color.rgb(baseR, baseG, baseB)

        val tileRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor seed.toLong()))

        val pixelSize = 2f
        val cols = (tw / pixelSize).toInt().coerceAtLeast(1)
        val rows = (th / pixelSize).toInt().coerceAtLeast(1)

        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL

        // 1. Textura base: pedra índigo com variação tonal (10-15%)
        paint.color = baseColor
        canvas.drawRect(x, y, x + tw, y + th, paint)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if ((n && r == 0) || (s && r == rows - 1) || (w && c == 0) || (e && c == cols - 1)) {
                    continue
                }
                val factor = -0.15f + tileRng.nextFloat() * 0.30f
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

        // 2. Veios mágicos: conectam regiões do tile (linhas roxas finas)
        if (tileRng.nextFloat() < 0.50f) {
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#442288")
            paint.isAntiAlias = true

            val numVeins = 1 + tileRng.nextInt(3)
            for (i in 0 until numVeins) {
                path.reset()
                val sx = x + tileRng.nextFloat() * tw
                val sy = y + tileRng.nextFloat() * th
                path.moveTo(sx, sy)

                val segments = 1 + tileRng.nextInt(2)
                var cx = sx
                var cy = sy
                for (seg in 0 until segments) {
                    cx += -10f + tileRng.nextFloat() * 20f
                    cy += -10f + tileRng.nextFloat() * 20f
                    cx = cx.coerceIn(x, x + tw)
                    cy = cy.coerceIn(y, y + th)
                    path.lineTo(cx, cy)
                }
                canvas.drawPath(path, paint)
            }
            paint.style = Paint.Style.FILL
            paint.isAntiAlias = false
            paint.strokeWidth = 0f
        }

        // 3. Cristais incrustados: em 30% dos tiles
        if (tileRng.nextFloat() < 0.30f) {
            val crystalColors = intArrayOf(
                Color.parseColor("#aa66ff"),
                Color.parseColor("#cc88ff"),
                Color.parseColor("#8844cc")
            )
            val numCrystals = 1 + tileRng.nextInt(3)
            paint.isAntiAlias = true

            for (i in 0 until numCrystals) {
                val cx = x + 6f + tileRng.nextFloat() * (tw - 12f)
                val cy = y + 6f + tileRng.nextFloat() * (th - 12f)
                val sizeW = 2f + tileRng.nextFloat() * 3f
                val sizeH = 4f + tileRng.nextFloat() * 4f

                path.reset()
                path.moveTo(cx, cy - sizeH) // Topo
                path.lineTo(cx + sizeW, cy) // Direita
                path.lineTo(cx, cy + sizeH) // Base
                path.lineTo(cx - sizeW, cy) // Esquerda
                path.close()

                paint.color = crystalColors[tileRng.nextInt(crystalColors.size)]
                canvas.drawPath(path, paint)

                // Ponto especular branco no topo
                paint.color = Color.argb(200, 255, 255, 255)
                canvas.drawCircle(cx, cy - sizeH * 0.5f, 1f, paint)
            }
            paint.isAntiAlias = false
        }

        // 4. Runa gravada: em 15% dos tiles
        if (tileRng.nextFloat() < 0.15f) {
            val cx = x + tw / 2f + (-4f + tileRng.nextFloat() * 8f)
            val cy = y + th / 2f + (-4f + tileRng.nextFloat() * 8f)
            val time = System.currentTimeMillis()
            val phase = tileX * 0.5f + tileY * 0.8f
            // Pulsação alpha
            val pulseAlpha = (180 + sin((time / 600.0 + phase).toFloat()) * 75).toInt().coerceIn(0, 255)

            // Brilho RadialGradient
            paint.isAntiAlias = true
            val glowGradient = RadialGradient(
                cx, cy, 12f,
                Color.argb((pulseAlpha * 0.8f).toInt().coerceIn(0, 255), 136, 68, 204), // #8844cc
                Color.argb(0, 136, 68, 204),
                Shader.TileMode.CLAMP
            )
            paint.shader = glowGradient
            canvas.drawCircle(cx, cy, 12f, paint)
            paint.shader = null

            // Desenho da runa (linhas 1px)
            paint.color = Color.argb(pulseAlpha, 200, 150, 255) // Roxo claro
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE

            val runaVar = tileRng.nextInt(4)
            val size = 4f
            when (runaVar) {
                0 -> { // Símbolo Y
                    canvas.drawLine(cx - size, cy - size, cx, cy, paint)
                    canvas.drawLine(cx + size, cy - size, cx, cy, paint)
                    canvas.drawLine(cx, cy, cx, cy + size, paint)
                }
                1 -> { // Símbolo Diamante Aberto
                    canvas.drawLine(cx, cy - size, cx + size, cy, paint)
                    canvas.drawLine(cx + size, cy, cx, cy + size, paint)
                    canvas.drawLine(cx, cy + size, cx - size, cy, paint)
                }
                2 -> { // Símbolo Zeta/Raio
                    canvas.drawLine(cx - size, cy - size, cx + size, cy - size, paint)
                    canvas.drawLine(cx + size, cy - size, cx - size, cy + size, paint)
                    canvas.drawLine(cx - size, cy + size, cx + size, cy + size, paint)
                }
                3 -> { // Símbolo Teta (círculo com traço)
                    canvas.drawCircle(cx, cy, size, paint)
                    canvas.drawLine(cx - size, cy, cx + size, cy, paint)
                }
            }

            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0f
            paint.isAntiAlias = false
        }

        // 5. Borda inferior dentada orgânica
        if (!s) {
            var curX = x
            val toothRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor 9999L))
            while (curX < x + tw) {
                val toothW = 2f + toothRng.nextFloat() * 4f
                val toothH = 2f + toothRng.nextFloat() * 4f
                val toothColor = Color.rgb(
                    (Color.red(baseColor) * 0.55f).toInt().coerceIn(0,255),
                    (Color.green(baseColor) * 0.55f).toInt().coerceIn(0,255),
                    (Color.blue(baseColor) * 0.55f).toInt().coerceIn(0,255)
                )
                paint.color = toothColor
                canvas.drawRect(
                    curX,
                    y + th - toothH,
                    (curX + toothW).coerceAtMost(x + tw),
                    y + th,
                    paint
                )
                curX += toothW + toothRng.nextFloat() * 2f
            }

            paint.color = Color.rgb(
                (Color.red(baseColor) * 0.35f).toInt().coerceIn(0,255),
                (Color.green(baseColor) * 0.35f).toInt().coerceIn(0,255),
                (Color.blue(baseColor) * 0.35f).toInt().coerceIn(0,255)
            )
            canvas.drawRect(x, y + th - 1f, x + tw, y + th, paint)
        }

        // Restaurar estado padrão do paint
        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL
    }

    private fun renderMinasWall(
        canvas: Canvas, x: Float, y: Float, tw: Float, th: Float,
        seed: Int, tileX: Int, tileY: Int,
        n: Boolean, s: Boolean, e: Boolean, w: Boolean
    ) {
        // Paleta base: pedra marrom-âmbar (#2a1e0a)
        val baseR = 42   // 0x2a
        val baseG = 30   // 0x1e
        val baseB = 10   // 0x0a
        val baseColor = Color.rgb(baseR, baseG, baseB)

        val tileRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor seed.toLong()))

        val pixelSize = 2f
        val cols = (tw / pixelSize).toInt().coerceAtLeast(1)
        val rows = (th / pixelSize).toInt().coerceAtLeast(1)

        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL

        // 1. Textura base: marrom-âmbar com variação tonal (10-15%)
        paint.color = baseColor
        canvas.drawRect(x, y, x + tw, y + th, paint)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if ((n && r == 0) || (s && r == rows - 1) || (w && c == 0) || (e && c == cols - 1)) {
                    continue
                }
                val factor = -0.15f + tileRng.nextFloat() * 0.30f
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

        // 2. Rachaduras de mineração: linhas de 1px em diagonal (#1a0e04)
        if (tileRng.nextFloat() < 0.60f) {
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#1a0e04") // Muito escuro
            paint.isAntiAlias = true

            val numCracks = 1 + tileRng.nextInt(3)
            for (i in 0 until numCracks) {
                val sx = x + tileRng.nextFloat() * tw
                val sy = y + tileRng.nextFloat() * th
                val len = 5f + tileRng.nextFloat() * 15f
                val angle = tileRng.nextFloat() * Math.PI.toFloat() * 2f

                val ex = sx + cos(angle) * len
                val ey = sy + sin(angle) * len

                canvas.drawLine(sx, sy, ex, ey, paint)
            }
            paint.style = Paint.Style.FILL
            paint.isAntiAlias = false
            paint.strokeWidth = 0f
        }

        // 3. Veios de ouro: linhas diagonais amarelo-âmbar (#d4a020) em 50% dos tiles
        if (tileRng.nextFloat() < 0.50f) {
            paint.strokeWidth = 1f + tileRng.nextFloat() // 1-2px
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#d4a020")
            paint.isAntiAlias = true

            val numVeins = 1 + tileRng.nextInt(2)
            for (i in 0 until numVeins) {
                path.reset()
                val sx = x + tileRng.nextFloat() * tw
                val sy = y + tileRng.nextFloat() * th
                path.moveTo(sx, sy)

                val segments = 2 + tileRng.nextInt(3)
                var cx = sx
                var cy = sy
                for (seg in 0 until segments) {
                    cx += -8f + tileRng.nextFloat() * 16f
                    cy += -8f + tileRng.nextFloat() * 16f
                    cx = cx.coerceIn(x, x + tw)
                    cy = cy.coerceIn(y, y + th)
                    path.lineTo(cx, cy)
                }
                canvas.drawPath(path, paint)
            }
            paint.style = Paint.Style.FILL
            paint.isAntiAlias = false
            paint.strokeWidth = 0f
        }

        // 4. Pepitas de ouro: em 25% dos tiles, ouro brilhante (#ffcc44)
        if (tileRng.nextFloat() < 0.25f) {
            val numNuggets = 1 + tileRng.nextInt(3)
            paint.isAntiAlias = true

            for (i in 0 until numNuggets) {
                val cx = x + 4f + tileRng.nextFloat() * (tw - 8f)
                val cy = y + 4f + tileRng.nextFloat() * (th - 8f)
                val size = 1.5f + tileRng.nextFloat() * 1f // Raio de ~1.5 a 2.5 (3-5px total)

                // Brilho especular (RadialGradient)
                val glowGradient = RadialGradient(
                    cx, cy, 6f,
                    Color.argb(70, 255, 204, 68), // #ffcc44
                    Color.argb(0, 255, 204, 68),
                    Shader.TileMode.CLAMP
                )
                paint.shader = glowGradient
                canvas.drawCircle(cx, cy, 6f, paint)
                paint.shader = null

                // Forma da pepita (losango/círculo distorcido)
                paint.color = Color.parseColor("#ffcc44")
                if (tileRng.nextBoolean()) {
                    canvas.drawCircle(cx, cy, size, paint)
                } else {
                    path.reset()
                    path.moveTo(cx, cy - size)
                    path.lineTo(cx + size, cy)
                    path.lineTo(cx, cy + size)
                    path.lineTo(cx - size, cy)
                    path.close()
                    canvas.drawPath(path, paint)
                }

                // Ponto especular branco no topo
                paint.color = Color.argb(200, 255, 255, 255)
                canvas.drawCircle(cx - size * 0.3f, cy - size * 0.3f, 0.5f + tileRng.nextFloat() * 0.5f, paint)
            }
            paint.isAntiAlias = false
        }

        // 5. Borda inferior dentada orgânica
        if (!s) {
            var curX = x
            val toothRng = java.util.Random((tileX * 73856093L xor tileY * 19349663L xor 9999L))
            while (curX < x + tw) {
                val toothW = 2f + toothRng.nextFloat() * 4f
                val toothH = 2f + toothRng.nextFloat() * 4f
                val toothColor = Color.rgb(
                    (Color.red(baseColor) * 0.55f).toInt().coerceIn(0,255),
                    (Color.green(baseColor) * 0.55f).toInt().coerceIn(0,255),
                    (Color.blue(baseColor) * 0.55f).toInt().coerceIn(0,255)
                )
                paint.color = toothColor
                canvas.drawRect(
                    curX,
                    y + th - toothH,
                    (curX + toothW).coerceAtMost(x + tw),
                    y + th,
                    paint
                )
                curX += toothW + toothRng.nextFloat() * 2f
            }

            paint.color = Color.rgb(
                (Color.red(baseColor) * 0.35f).toInt().coerceIn(0,255),
                (Color.green(baseColor) * 0.35f).toInt().coerceIn(0,255),
                (Color.blue(baseColor) * 0.35f).toInt().coerceIn(0,255)
            )
            canvas.drawRect(x, y + th - 1f, x + tw, y + th, paint)
        }

        // Restaurar estado padrão do paint
        paint.reset()
        paint.isAntiAlias = false
        paint.isFilterBitmap = false
        paint.style = Paint.Style.FILL
    }

    // =========================================================================
    // ARMADILHAS PROCEDURAIS DE VALA (Ponto 4)
    // =========================================================================

    fun renderTrapSpikesTile(
        canvas: Canvas,
        x: Float, y: Float,
        tileW: Float, tileH: Float,
        tileX: Int, tileY: Int
    ) {
        // Base metálica escura na base inferior do tile
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        
        paint.color = Color.rgb(45, 45, 55)
        canvas.drawRect(x, y + tileH * 0.75f, x + tileW, y + tileH, paint)
        
        // Borda superior da base metálica
        paint.color = Color.rgb(90, 90, 105)
        canvas.drawRect(x, y + tileH * 0.72f, x + tileW, y + tileH * 0.75f, paint)

        // 4 estacas triangulares cinza metálico com brilho especular branco
        val numSpikes = 4
        val spikeW = tileW / numSpikes
        for (i in 0 until numSpikes) {
            val spikeX = x + i * spikeW + spikeW / 2f
            
            // Desenha o triângulo da estaca
            path.reset()
            path.moveTo(x + i * spikeW, y + tileH * 0.72f)
            path.lineTo(spikeX, y + tileH * 0.1f)
            path.lineTo(x + (i + 1) * spikeW, y + tileH * 0.72f)
            path.close()
            
            // Gradiente cinza metálico
            paint.color = Color.rgb(160 - i * 10, 160 - i * 10, 175 - i * 10)
            canvas.drawPath(path, paint)
            
            // Desenha brilho especular do lado esquerdo da ponta até a base
            path.reset()
            path.moveTo(spikeX, y + tileH * 0.1f)
            path.lineTo(spikeX - spikeW * 0.2f, y + tileH * 0.72f)
            path.lineTo(spikeX, y + tileH * 0.72f)
            path.close()
            paint.color = Color.rgb(230, 230, 245)
            canvas.drawPath(path, paint)
        }
    }

    fun renderTrapLavaTile(
        canvas: Canvas,
        x: Float, y: Float,
        tileW: Float, tileH: Float,
        tileX: Int, tileY: Int
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL

        val time = System.currentTimeMillis() / 1000f
        // Pulsação suave via sin
        val pulse = (sin(time * 2f + tileX) * 0.5f + 0.5f) // 0.0 a 1.0
        
        // Gradiente vertical de vermelho escuro para laranja/amarelo brilhante
        val red = (180 + pulse * 75).toInt().coerceIn(0, 255)
        val green = (40 + pulse * 60).toInt().coerceIn(0, 255)
        val blue = 10
        val colorLava = Color.rgb(red, green, blue)
        
        val grad = LinearGradient(
            x, y, x, y + tileH,
            Color.rgb(255, 120, 20),
            colorLava,
            Shader.TileMode.CLAMP
        )
        paint.shader = grad
        canvas.drawRect(x, y, x + tileW, y + tileH, paint)
        paint.shader = null

        // Pequenas bolhas amarelas procedurais subindo
        paint.color = Color.rgb(255, 220, 30)
        val numBubbles = 3
        for (i in 0 until numBubbles) {
            val randomVal = tileRandom(tileX, tileY, i * 7)
            val bx = x + (randomVal * 0.8f + 0.1f) * tileW
            val speed = 0.5f + randomVal * 0.5f
            val by = y + tileH - ((time * speed + randomVal) % 1.0f) * tileH
            
            val bubbleSize = 2f + 3f * sin(time * 4f + i)
            if (bubbleSize > 0.5f) {
                canvas.drawCircle(bx, by, bubbleSize, paint)
            }
        }
    }

    fun renderTrapPiranhaWaterTile(
        canvas: Canvas,
        x: Float, y: Float,
        tileW: Float, tileH: Float,
        tileX: Int, tileY: Int
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL

        val time = System.currentTimeMillis() / 1000f

        // Preenchimento com água azul-escura
        paint.color = Color.rgb(10, 25, 75)
        canvas.drawRect(x, y + tileH * 0.15f, x + tileW, y + tileH, paint)

        // Ondas no topo simuladas por senóides
        paint.color = Color.rgb(25, 70, 150)
        path.reset()
        path.moveTo(x, y + tileH * 0.15f)
        for (i in 0..10) {
            val px = x + (i / 10f) * tileW
            val py = y + tileH * 0.15f + sin(time * 5f + (tileX * 2) + (i / 10f) * Math.PI.toFloat() * 2) * 3f
            path.lineTo(px, py)
        }
        path.lineTo(x + tileW, y + tileH)
        path.lineTo(x, y + tileH)
        path.close()
        canvas.drawPath(path, paint)

        // Bolhas subindo
        paint.color = Color.argb(160, 200, 220, 255)
        val numBubbles = 2
        for (i in 0 until numBubbles) {
            val randomVal = tileRandom(tileX, tileY, i * 11)
            val bx = x + (randomVal * 0.8f + 0.1f) * tileW
            val speed = 0.4f + randomVal * 0.4f
            val by = y + tileH - ((time * speed + randomVal) % 1.0f) * tileH
            canvas.drawCircle(bx, by, 1.5f + randomVal * 1.5f, paint)
        }

        // Piranha vermelha saltitante
        val jumpCycle = (time * 1.5f + tileX * 0.7f) % 2.0f // ciclo de 2 segundos
        if (jumpCycle < 0.8f) { // pula de vez em quando
            val progress = jumpCycle / 0.8f // 0.0 a 1.0
            // Arco de pulo (parábola)
            val px = x + tileW * 0.5f
            val py = y + tileH * 0.7f - 16f * progress * (1f - progress) - tileH * 0.2f
            
            // Corpo da piranha (oval vermelha inclinada)
            paint.color = Color.rgb(190, 20, 20)
            canvas.drawOval(RectF(px - 6f, py - 4f, px + 6f, py + 4f), paint)
            
            // Rabo (triângulo)
            path.reset()
            path.moveTo(px - 5f, py)
            path.lineTo(px - 9f, py - 3f)
            path.lineTo(px - 9f, py + 3f)
            path.close()
            canvas.drawPath(path, paint)

            // Olho amarelo brilhante
            paint.color = Color.rgb(255, 230, 0)
            canvas.drawCircle(px + 3f, py - 1f, 1f, paint)
        }
    }
}
