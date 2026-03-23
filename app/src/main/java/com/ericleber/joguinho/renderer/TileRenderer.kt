package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.ericleber.joguinho.biome.BiomePalette

/**
 * Tipos de tile disponíveis para renderização.
 */
enum class TileType {
    WALL,
    FLOOR,
    DECORATIVE_0,
    DECORATIVE_1,
    DECORATIVE_2,
    DECORATIVE_3
}

/**
 * Renderiza tiles isométricos 32x32px por Bioma via Canvas.
 *
 * Usa Paint.filterBitmap = false e isAntiAlias = false para estética pixel art.
 * Paredes isométricas têm 3 faces: topo (mais claro), esquerda (cor base), direita (mais escura).
 *
 * Requisitos: 8.2, 17.1, 17.3
 */
class TileRenderer {

    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }

    private val path = Path()

    /**
     * Desenha um tile de parede isométrico 3D com 3 faces.
     * @param canvas canvas de destino
     * @param x posição X do tile na tela (canto superior esquerdo do losango)
     * @param y posição Y do tile na tela
     * @param tileW largura do tile
     * @param tileH altura do tile
     * @param palette paleta de cores do bioma
     */
    fun renderWallTile(
        canvas: Canvas,
        x: Float,
        y: Float,
        tileW: Float,
        tileH: Float,
        palette: BiomePalette
    ) {
        val halfW = tileW / 2f
        val halfH = tileH / 2f
        val wallHeight = tileH * 0.8f

        // Face superior (topo) — cor mais clara
        paint.color = lighten(palette.wallColor, 0.3f)
        path.reset()
        path.moveTo(x + halfW, y)                    // topo
        path.lineTo(x + tileW, y + halfH)            // direita
        path.lineTo(x + halfW, y + tileH)            // baixo
        path.lineTo(x, y + halfH)                    // esquerda
        path.close()
        canvas.drawPath(path, paint)

        // Face esquerda — cor base da parede
        paint.color = palette.wallColor
        path.reset()
        path.moveTo(x, y + halfH)                    // topo-esquerda
        path.lineTo(x + halfW, y + tileH)            // topo-centro
        path.lineTo(x + halfW, y + tileH + wallHeight) // baixo-centro
        path.lineTo(x, y + halfH + wallHeight)       // baixo-esquerda
        path.close()
        canvas.drawPath(path, paint)

        // Face direita — cor mais escura
        paint.color = darken(palette.wallColor, 0.3f)
        path.reset()
        path.moveTo(x + halfW, y + tileH)            // topo-centro
        path.lineTo(x + tileW, y + halfH)            // topo-direita
        path.lineTo(x + tileW, y + halfH + wallHeight) // baixo-direita
        path.lineTo(x + halfW, y + tileH + wallHeight) // baixo-centro
        path.close()
        canvas.drawPath(path, paint)
    }

    /**
     * Desenha um tile de chão (losango plano).
     * @param canvas canvas de destino
     * @param x posição X do tile na tela
     * @param y posição Y do tile na tela
     * @param tileW largura do tile
     * @param tileH altura do tile
     * @param palette paleta de cores do bioma
     */
    fun renderFloorTile(
        canvas: Canvas,
        x: Float,
        y: Float,
        tileW: Float,
        tileH: Float,
        palette: BiomePalette
    ) {
        val halfW = tileW / 2f
        val halfH = tileH / 2f

        paint.color = palette.floorColor
        path.reset()
        path.moveTo(x + halfW, y)           // topo
        path.lineTo(x + tileW, y + halfH)   // direita
        path.lineTo(x + halfW, y + tileH)   // baixo
        path.lineTo(x, y + halfH)           // esquerda
        path.close()
        canvas.drawPath(path, paint)

        // Borda sutil para definição do tile
        paint.color = darken(palette.floorColor, 0.15f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    /**
     * Desenha elementos decorativos (pedras, plantas, etc.) baseados no variant.
     * @param variant 0-3 define o tipo de decoração
     */
    fun renderDecorativeTile(
        canvas: Canvas,
        x: Float,
        y: Float,
        tileW: Float,
        tileH: Float,
        palette: BiomePalette,
        variant: Int
    ) {
        // Primeiro renderiza o chão base
        renderFloorTile(canvas, x, y, tileW, tileH, palette)

        val halfW = tileW / 2f
        val halfH = tileH / 2f
        val cx = x + halfW
        val cy = y + halfH

        when (variant % 4) {
            0 -> {
                // Pedra pequena
                paint.color = darken(palette.wallColor, 0.1f)
                canvas.drawRect(cx - 3f, cy - 2f, cx + 3f, cy + 2f, paint)
                paint.color = lighten(palette.wallColor, 0.2f)
                canvas.drawRect(cx - 3f, cy - 2f, cx, cy, paint)
            }
            1 -> {
                // Planta/musgo
                paint.color = palette.accentColor
                canvas.drawCircle(cx, cy - 2f, 3f, paint)
                canvas.drawCircle(cx - 3f, cy, 2f, paint)
                canvas.drawCircle(cx + 3f, cy, 2f, paint)
            }
            2 -> {
                // Cristal/veio mineral
                paint.color = palette.accentColor
                path.reset()
                path.moveTo(cx, cy - 5f)
                path.lineTo(cx + 2f, cy)
                path.lineTo(cx, cy + 2f)
                path.lineTo(cx - 2f, cy)
                path.close()
                canvas.drawPath(path, paint)
            }
            3 -> {
                // Rachadura no chão
                paint.color = darken(palette.floorColor, 0.3f)
                paint.strokeWidth = 1f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx - 4f, cy - 2f, cx + 2f, cy + 3f, paint)
                canvas.drawLine(cx + 2f, cy + 3f, cx + 4f, cy + 1f, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    /**
     * Pré-renderiza um tile para um Bitmap para uso no cache.
     * @param tileType tipo do tile
     * @param tileW largura do tile em pixels
     * @param tileH altura do tile em pixels
     * @param palette paleta de cores do bioma
     * @return Bitmap com o tile renderizado
     */
    fun createTileBitmap(
        tileType: TileType,
        tileW: Int,
        tileH: Int,
        palette: BiomePalette
    ): Bitmap {
        val bitmapHeight = when (tileType) {
            TileType.WALL -> (tileH * 1.8f).toInt()  // inclui altura da parede
            else -> tileH
        }
        val bitmap = Bitmap.createBitmap(tileW, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (tileType) {
            TileType.WALL -> renderWallTile(canvas, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette)
            TileType.FLOOR -> renderFloorTile(canvas, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette)
            TileType.DECORATIVE_0 -> renderDecorativeTile(canvas, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, 0)
            TileType.DECORATIVE_1 -> renderDecorativeTile(canvas, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, 1)
            TileType.DECORATIVE_2 -> renderDecorativeTile(canvas, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, 2)
            TileType.DECORATIVE_3 -> renderDecorativeTile(canvas, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, 3)
        }

        return bitmap
    }

    // -------------------------------------------------------------------------
    // Utilitários de cor
    // -------------------------------------------------------------------------

    /** Clareia uma cor pelo fator dado (0.0 a 1.0). */
    private fun lighten(color: Int, factor: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val f = (factor * 255).toInt()
        return Color.rgb(
            (r + f).coerceAtMost(255),
            (g + f).coerceAtMost(255),
            (b + f).coerceAtMost(255)
        )
    }

    /** Escurece uma cor pelo fator dado (0.0 a 1.0). */
    private fun darken(color: Int, factor: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val f = (factor * 255).toInt()
        return Color.rgb(
            (r - f).coerceAtLeast(0),
            (g - f).coerceAtLeast(0),
            (b - f).coerceAtLeast(0)
        )
    }
}
