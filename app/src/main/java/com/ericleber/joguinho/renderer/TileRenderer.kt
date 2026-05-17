package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.ericleber.joguinho.biome.Biome
import com.ericleber.joguinho.biome.BiomePalette
import com.ericleber.joguinho.biome.WallDetailType
import com.ericleber.joguinho.core.MazeData
import java.util.Random
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
        val hash = (tileX * 13 + tileY * 7 + tileX * tileY * 3) and 0xFF
        val microVar = ((hash shr 2) % 6) - 3
        val corFinal = variarCor(palette.floorColor, microVar)

        paint.color = corFinal
        canvas.drawRect(x, y, x + tileW, y + tileH, paint)

        val seed = tileX * 11 + tileY * 17
        val rng = Random(seed.toLong())

        // Detalhes de Textura (Style Stardew)
        if (tileW > 8f) {
            // 1. Ruído de cor (manchas de terra/pedra)
            if (rng.nextFloat() > 0.6f) {
                paint.color = clarear(corFinal, 0.08f)
                val sw = tileW * (0.2f + rng.nextFloat() * 0.3f)
                val sh = tileH * (0.2f + rng.nextFloat() * 0.3f)
                canvas.drawRect(x + rng.nextFloat() * (tileW - sw), y + rng.nextFloat() * (tileH - sh), x + sw, y + sh, paint)
            }

            // 2. Grãos e Cracks
            paint.color = escurecer(corFinal, 0.20f)
            val n = 3 + rng.nextInt(3)
            for (i in 0 until n) {
                val gx = x + rng.nextFloat() * (tileW - 2f)
                val gy = y + rng.nextFloat() * (tileH - 2f)
                canvas.drawRect(gx, gy, gx + 2f, gy + 2f, paint)
                
                // Pequena rachadura ocasional
                if (rng.nextFloat() > 0.85f) {
                    canvas.drawLine(gx, gy, gx + 4f, gy + 4f, paint)
                }
            }

            // 3. Poças de Água (Biomas Úmidos/Pântano)
            if (palette.hasDrips && rng.nextFloat() > 0.92f) {
                paint.color = Color.argb(120, 100, 150, 255)
                val pw = tileW * 0.4f
                val ph = tileH * 0.2f
                val px = x + rng.nextFloat() * (tileW - pw)
                val py = y + rng.nextFloat() * (tileH - ph)
                canvas.drawOval(RectF(px, py, px + pw, py + ph), paint)
            }
        }
    }

    // Bioma atual para diferenciação visual das paredes
    private var biomeAtual: Biome = Biome.MINA_ABANDONADA
    
    fun setBiome(biome: Biome) { biomeAtual = biome }

    fun renderWallTile(
        canvas: Canvas,
        x: Float, y: Float,
        tileW: Float, tileH: Float,
        palette: BiomePalette,
        tileX: Int, tileY: Int,
        mazeData: MazeData? = null
    ) {
        val seed = tileX * 7 + tileY * 13
        val varBase = ((seed * 3) % 14) - 7
        val corBase = variarCor(palette.wallColor, varBase)
        val corTopo = palette.wallTopColor
        val corSombra = palette.wallShadowColor

        // Bitmask de vizinhos (Blob 8-neighbor)
        val mask = getWallBitmask(tileX, tileY, mazeData)
        
        // --- Fase 11: Renderização Orgânica (Blob Tileset) ---
        renderOrganicWall(canvas, x, y, tileW, tileH, palette, mask, seed)
    }

    /**
     * Renderiza a parede com quinas arredondadas e conexões orgânicas (47 variantes simuladas).
     */
    private fun renderOrganicWall(
        canvas: Canvas, x: Float, y: Float, tw: Float, th: Float,
        p: BiomePalette, mask: Int, seed: Int
    ) {
        val corBase = variarCor(p.wallColor, (seed % 10) - 5)
        val corTopo = p.wallTopColor
        val corSombra = p.wallShadowColor
        
        // 1. Base sólida com textura procedural
        val texture = when {
            biomeAtual.name.contains("FLORESTA") || biomeAtual.name.contains("JARDIM") -> 
                HighFidelitySpriteEngine.getWoodTexture(tw.toInt(), corBase, seed.toLong())
            else -> 
                HighFidelitySpriteEngine.getStoneTexture(tw.toInt(), corBase, seed.toLong())
        }
        canvas.drawBitmap(texture, x, y, paint)

        // 2. Extração de vizinhos da bitmask (N=1, NE=2, E=4, SE=8, S=16, SW=32, W=64, NW=128)
        val n = (mask and 1) != 0
        val e = (mask and 4) != 0
        val s = (mask and 16) != 0
        val w = (mask and 64) != 0
        
        // 3. Renderização de Quinas (Aumenta o "Organic Feel")
        val cornerSize = tw * 0.25f
        
        // TOP-LEFT
        renderCorner(canvas, x, y, cornerSize, corTopo, 
            isOuter = !n && !w, 
            isInner = n && w && (mask and 128) == 0)
            
        // TOP-RIGHT
        renderCorner(canvas, x + tw - cornerSize, y, cornerSize, corTopo,
            isOuter = !n && !e,
            isInner = n && e && (mask and 2) == 0)
            
        // BOTTOM-LEFT
        renderCorner(canvas, x, y + th - cornerSize, cornerSize, corSombra,
            isOuter = !s && !w,
            isInner = s && w && (mask and 32) == 0)
            
        // BOTTOM-RIGHT
        renderCorner(canvas, x + tw - cornerSize, y + th - cornerSize, cornerSize, corSombra,
            isOuter = !s && !e,
            isInner = s && e && (mask and 8) == 0)

        // 4. Detalhes de profundidade (Face frontal e Topo)
        if (!n) { // Topo visível
            paint.color = corTopo
            canvas.drawRect(x + 2f, y, x + tw - 2f, y + 4f, paint)
        }
        if (!s) { // Face frontal visível
            paint.color = corSombra
            canvas.drawRect(x, y + th * 0.7f, x + tw, y + th, paint)
        }

        // 5. Props Decorativos (Cipós/Raízes) - 15% de chance em biomas orgânicos
        if (!s && (biomeAtual.name.contains("FLORESTA") || biomeAtual.name.contains("JARDIM") || biomeAtual.name.contains("CAVERNA"))) {
            val rng = Random(seed.toLong())
            if (rng.nextFloat() < 0.15f) {
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
        val bitmap = Bitmap.createBitmap(tileW.coerceAtLeast(1), tileH.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        renderWallTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, tileX, tileY, mazeData)
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
}
