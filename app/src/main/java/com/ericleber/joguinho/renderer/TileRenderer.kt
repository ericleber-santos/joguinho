package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.ericleber.joguinho.biome.Biome
import com.ericleber.joguinho.biome.BiomePalette

enum class TileType {
    WALL, FLOOR,
    DECORATIVE_0, DECORATIVE_1, DECORATIVE_2, DECORATIVE_3,
    ENTRADA, SAIDA
}

/**
 * Renderiza tiles top-down estilo Stardew Valley cave.
 *
 * Chão: quadrado com variação de cor e grãos de textura.
 * Parede: quadrado escuro com highlight/sombra e textura de pedra.
 * Flora: cogumelos, cristais, raízes, liquens.
 *
 * Regras: Paint.isAntiAlias = false, Paint.isFilterBitmap = false (pixel art).
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
        if (hash % 4 != 0 && tileW > 4f) {
            paint.color = escurecer(corFinal, 0.15f)
            val n = 2 + (seed % 2)
            for (i in 0 until n) {
                val gx = x + ((seed * (i + 1) * 3).and(0x7FFFFFFF) % (tileW.toInt().coerceAtLeast(2))).toFloat()
                val gy = y + ((seed * (i + 1) * 5).and(0x7FFFFFFF) % (tileH.toInt().coerceAtLeast(2))).toFloat()
                canvas.drawRect(gx, gy, gx + 1.5f, gy + 1.5f, paint)
            }
        }
    }

    // =========================================================================
    // PAREDE
    // =========================================================================

    fun renderWallTile(
        canvas: Canvas,
        x: Float, y: Float,
        tileW: Float, tileH: Float,
        palette: BiomePalette,
        tileX: Int = 0, tileY: Int = 0
    ) {
        val seed = tileX * 7 + tileY * 13
        val varBase = ((seed * 3) % 14) - 7
        val corBase = variarCor(palette.wallColor, varBase)

        paint.color = corBase
        canvas.drawRect(x, y, x + tileW, y + tileH, paint)

        if (tileW > 4f) {
            // Highlight topo e esquerda
            paint.color = clarear(corBase, 0.20f)
            canvas.drawRect(x, y, x + tileW, y + 2f, paint)
            canvas.drawRect(x, y, x + 2f, y + tileH, paint)
            // Sombra base e direita
            paint.color = escurecer(corBase, 0.25f)
            canvas.drawRect(x, y + tileH - 2f, x + tileW, y + tileH, paint)
            canvas.drawRect(x + tileW - 2f, y, x + tileW, y + tileH, paint)
            // Textura
            renderTexturaPedraTopDown(canvas, x, y, tileW, tileH, palette, seed)
        }
    }

    private fun renderTexturaPedraTopDown(
        canvas: Canvas, x: Float, y: Float,
        tileW: Float, tileH: Float, palette: BiomePalette, seed: Int
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(60, 0, 0, 0)
        val rx1 = x + (seed % 5).toFloat() * (tileW / 6f) + 3f
        val ry1 = y + (seed % 4).toFloat() * (tileH / 5f) + 3f
        canvas.drawLine(rx1, ry1, (rx1 + tileW * 0.25f).coerceAtMost(x + tileW - 1f),
            (ry1 + tileH * 0.30f).coerceAtMost(y + tileH - 1f), paint)
        paint.style = Paint.Style.FILL

        if (seed % 3 == 0 && tileW > 6f) {
            paint.color = Color.argb(120,
                Color.red(palette.mossColor), Color.green(palette.mossColor), Color.blue(palette.mossColor))
            val mx = x + ((seed * 3).and(0x7FFFFFFF) % (tileW.toInt().coerceAtLeast(5) - 4)).toFloat()
            val my = y + ((seed * 5).and(0x7FFFFFFF) % (tileH.toInt().coerceAtLeast(5) - 4)).toFloat()
            canvas.drawRect(mx, my, mx + 3f, my + 3f, paint)
        }

        if (seed % 5 == 0 && tileW > 8f) {
            paint.color = Color.argb(140,
                Color.red(palette.wallDetailColor), Color.green(palette.wallDetailColor), Color.blue(palette.wallDetailColor))
            val vx = x + tileW * 0.3f + ((seed * 2) % 4).toFloat() * (tileW / 5f)
            val vy = y + tileH * 0.3f
            canvas.drawRect(vx.coerceAtMost(x + tileW - 3f), vy,
                (vx + 2f).coerceAtMost(x + tileW - 1f), (vy + tileH * 0.35f).coerceAtMost(y + tileH - 1f), paint)
        }
    }

    // Mantido para compatibilidade
    fun renderTexturaPedra(
        canvas: Canvas, x: Float, y: Float,
        tileW: Float, tileH: Float, palette: BiomePalette, tileX: Int, tileY: Int
    ) {
        renderTexturaPedraTopDown(canvas, x, y, tileW, tileH, palette, tileX * 7 + tileY * 13)
    }

    // =========================================================================
    // ENTRADA e SAÍDA — top-down simples
    // =========================================================================

    fun renderEntradaTile(canvas: Canvas, x: Float, y: Float, tileW: Float, tileH: Float) {
        paint.color = Color.rgb(30, 120, 50)
        canvas.drawRect(x, y, x + tileW, y + tileH, paint)
        paint.color = Color.rgb(50, 180, 80)
        val m = tileW * 0.2f
        canvas.drawRect(x + m, y + m, x + tileW - m, y + tileH - m, paint)
    }

    fun renderSaidaTile(canvas: Canvas, x: Float, y: Float, tileW: Float, tileH: Float) {
        paint.color = Color.rgb(140, 100, 10)
        canvas.drawRect(x, y, x + tileW, y + tileH, paint)
        paint.color = Color.rgb(220, 170, 30)
        val m = tileW * 0.2f
        canvas.drawRect(x + m, y + m, x + tileW - m, y + tileH - m, paint)
    }

    // =========================================================================
    // DECORATIVOS
    // =========================================================================

    fun renderDecorativeTile(
        canvas: Canvas, x: Float, y: Float, tileW: Float, tileH: Float,
        palette: BiomePalette, variant: Int,
        biome: Biome = Biome.MINA_ABANDONADA, tileX: Int = 0, tileY: Int = 0
    ) {
        renderFloorTile(canvas, x, y, tileW, tileH, palette, tileX, tileY)
        val cx = x + tileW / 2f
        val cy = y + tileH / 2f
        // Seleciona o estilo decorativo baseado no nome ou categoria do bioma
        val nome = biome.name
        when {
            nome.contains("MINA") || nome.contains("CAVERNA") || nome.contains("TUNEIS") -> 
                decorativoMina(canvas, cx, cy, tileW, tileH, palette, variant)
            nome.contains("RIACHO") || nome.contains("LAGO") || nome.contains("AQUATICO") || nome.contains("ABISMO") -> 
                decorativoRiacho(canvas, cx, cy, tileW, tileH, palette, variant)
            nome.contains("JARDIM") || nome.contains("FLORESTA") || nome.contains("PLANTACAO") || nome.contains("RAIZES") || nome.contains("POMAR") -> 
                decorativoPlantacao(canvas, cx, cy, tileW, tileH, palette, variant)
            nome.contains("CONSTRUCAO") || nome.contains("RUINA") || nome.contains("TEMPLO") || nome.contains("SALOES") || nome.contains("TUMULO") -> 
                decorativoRocha(canvas, cx, cy, tileW, tileH, palette, variant)
            nome.contains("VULCANICO") || nome.contains("LAVA") || nome.contains("FOGO") || nome.contains("DINOSSAURO") || nome.contains("FORJA") -> 
                decorativoDinossauro(canvas, cx, cy, tileW, tileH, palette, variant)
            else -> decorativoMina(canvas, cx, cy, tileW, tileH, palette, variant)
        }
    }

    private fun renderCogumelo(
        canvas: Canvas, cx: Float, cy: Float,
        hasteColor: Int, chapeuColor: Int, tamanho: Float
    ) {
        val h = tamanho
        paint.color = hasteColor
        canvas.drawRect(cx - h * 0.15f, cy - h * 0.5f, cx + h * 0.15f, cy + h * 0.1f, paint)
        paint.color = chapeuColor
        canvas.drawRect(cx - h * 0.4f, cy - h * 0.9f, cx + h * 0.4f, cy - h * 0.4f, paint)
        canvas.drawRect(cx - h * 0.3f, cy - h * 1.1f, cx + h * 0.3f, cy - h * 0.85f, paint)
        paint.color = clarear(chapeuColor, 0.30f)
        canvas.drawRect(cx - h * 0.15f, cy - h * 1.05f, cx, cy - h * 0.85f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = escurecer(chapeuColor, 0.40f)
        canvas.drawRect(cx - h * 0.4f, cy - h * 0.9f, cx + h * 0.4f, cy - h * 0.4f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun renderCristalDecorativo(canvas: Canvas, cx: Float, cy: Float, cor: Int, tamanho: Float) {
        val t = tamanho
        paint.color = escurecer(cor, 0.20f)
        path.reset()
        path.moveTo(cx, cy - t); path.lineTo(cx + t * 0.5f, cy - t * 0.3f)
        path.lineTo(cx + t * 0.4f, cy + t * 0.5f); path.lineTo(cx - t * 0.4f, cy + t * 0.5f)
        path.lineTo(cx - t * 0.5f, cy - t * 0.3f); path.close()
        canvas.drawPath(path, paint)
        paint.color = cor
        path.reset()
        path.moveTo(cx, cy - t * 0.9f); path.lineTo(cx + t * 0.4f, cy - t * 0.2f)
        path.lineTo(cx + t * 0.3f, cy + t * 0.4f); path.lineTo(cx - t * 0.3f, cy + t * 0.4f)
        path.lineTo(cx - t * 0.4f, cy - t * 0.2f); path.close()
        canvas.drawPath(path, paint)
        paint.color = clarear(cor, 0.50f)
        canvas.drawRect(cx - t * 0.1f, cy - t * 0.85f, cx + t * 0.1f, cy - t * 0.5f, paint)
    }

    private fun decorativoMina(canvas: Canvas, cx: Float, cy: Float, tileW: Float, tileH: Float, p: BiomePalette, v: Int) {
        val s = tileW * 0.18f
        when (v % 4) {
            0 -> renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
            1 -> renderCristalDecorativo(canvas, cx, cy - tileH * 0.1f, p.crystalColor, s * 0.9f)
            2 -> {
                paint.color = p.wallColor
                canvas.drawRect(cx - s * 0.7f, cy - s * 0.4f, cx + s * 0.7f, cy + s * 0.4f, paint)
                paint.color = p.accentColor
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
                canvas.drawLine(cx - s * 0.3f, cy - s * 0.3f, cx + s * 0.2f, cy + s * 0.2f, paint)
                paint.style = Paint.Style.FILL
            }
            else -> {
                paint.color = Color.argb(200, Color.red(p.mossColor), Color.green(p.mossColor), Color.blue(p.mossColor))
                for (i in 0..4) {
                    val mx = cx + (i * 37 % 13 - 6).toFloat() * s * 0.12f
                    val my = cy + (i * 23 % 9 - 4).toFloat() * s * 0.12f
                    canvas.drawRect(mx, my, mx + 2.5f, my + 2.5f, paint)
                }
            }
        }
    }

    private fun decorativoRiacho(canvas: Canvas, cx: Float, cy: Float, tileW: Float, tileH: Float, p: BiomePalette, v: Int) {
        val s = tileW * 0.18f
        when (v % 4) {
            0 -> renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
            1 -> renderCristalDecorativo(canvas, cx, cy - tileH * 0.1f, p.crystalColor, s * 0.9f)
            2 -> {
                paint.color = Color.argb(160, Color.red(p.accentColor), Color.green(p.accentColor), Color.blue(p.accentColor))
                canvas.drawOval(RectF(cx - s * 0.8f, cy - s * 0.35f, cx + s * 0.8f, cy + s * 0.35f), paint)
            }
            else -> {
                paint.color = Color.argb(180, Color.red(p.mossColor), Color.green(p.mossColor), Color.blue(p.mossColor))
                for (i in 0..5) {
                    val mx = cx + (i * 41 % 15 - 7).toFloat() * s * 0.11f
                    val my = cy + (i * 19 % 9 - 4).toFloat() * s * 0.11f
                    canvas.drawRect(mx, my, mx + 2f, my + 2f, paint)
                }
            }
        }
    }

    private fun decorativoPlantacao(canvas: Canvas, cx: Float, cy: Float, tileW: Float, tileH: Float, p: BiomePalette, v: Int) {
        val s = tileW * 0.18f
        when (v % 4) {
            0 -> renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
            1 -> renderCristalDecorativo(canvas, cx, cy - tileH * 0.1f, p.crystalColor, s * 0.9f)
            2 -> {
                paint.color = Color.rgb(92, 60, 30)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                canvas.drawLine(cx - s * 0.6f, cy + s * 0.2f, cx, cy - s * 0.3f, paint)
                canvas.drawLine(cx, cy - s * 0.3f, cx + s * 0.5f, cy + s * 0.1f, paint)
                paint.style = Paint.Style.FILL
            }
            else -> {
                paint.color = p.mossColor
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
                canvas.drawLine(cx, cy + s * 0.2f, cx - s * 0.5f, cy - s * 0.6f, paint)
                canvas.drawLine(cx, cy + s * 0.2f, cx + s * 0.5f, cy - s * 0.5f, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    private fun decorativoRocha(canvas: Canvas, cx: Float, cy: Float, tileW: Float, tileH: Float, p: BiomePalette, v: Int) {
        val s = tileW * 0.18f
        when (v % 4) {
            0 -> renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
            1 -> renderCristalDecorativo(canvas, cx, cy - tileH * 0.1f, p.crystalColor, s * 0.9f)
            2 -> {
                paint.color = p.wallColor
                canvas.drawOval(RectF(cx - s * 0.7f, cy - s * 0.4f, cx + s * 0.7f, cy + s * 0.4f), paint)
            }
            else -> {
                paint.color = Color.argb(160, Color.red(p.particleColor), Color.green(p.particleColor), Color.blue(p.particleColor))
                for (i in 0..3) {
                    val px = cx + (i * 37 % 11 - 5).toFloat() * s * 0.14f
                    val py = cy + (i * 23 % 7 - 3).toFloat() * s * 0.14f
                    canvas.drawRect(px, py, px + 1.5f, py + 1.5f, paint)
                }
            }
        }
    }

    private fun decorativoPomar(canvas: Canvas, cx: Float, cy: Float, tileW: Float, tileH: Float, p: BiomePalette, v: Int) {
        val s = tileW * 0.18f
        when (v % 4) {
            0 -> renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
            1 -> renderCristalDecorativo(canvas, cx, cy - tileH * 0.1f, p.crystalColor, s * 0.9f)
            2 -> {
                paint.color = p.accentColor
                for (i in 0..4) {
                    val ang = i * 72f * (Math.PI / 180f).toFloat()
                    val px = cx + kotlin.math.cos(ang) * s * 0.4f
                    val py = cy + kotlin.math.sin(ang) * s * 0.25f
                    canvas.drawRect(px - 1.5f, py - 1.5f, px + 1.5f, py + 1.5f, paint)
                }
            }
            else -> {
                paint.color = Color.argb(200, Color.red(p.mossColor), Color.green(p.mossColor), Color.blue(p.mossColor))
                for (i in 0..4) {
                    val mx = cx + (i * 43 % 13 - 6).toFloat() * s * 0.12f
                    val my = cy + (i * 17 % 9 - 4).toFloat() * s * 0.12f
                    canvas.drawRect(mx, my, mx + 2.5f, my + 2.5f, paint)
                }
            }
        }
    }

    private fun decorativoDinossauro(canvas: Canvas, cx: Float, cy: Float, tileW: Float, tileH: Float, p: BiomePalette, v: Int) {
        val s = tileW * 0.18f
        when (v % 4) {
            0 -> renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
            1 -> renderCristalDecorativo(canvas, cx, cy - tileH * 0.1f, p.crystalColor, s * 0.9f)
            2 -> {
                paint.color = Color.rgb(200, 185, 155)
                canvas.drawRect(cx - s * 0.6f, cy - s * 0.1f, cx + s * 0.6f, cy + s * 0.1f, paint)
                canvas.drawOval(RectF(cx - s * 0.7f, cy - s * 0.2f, cx - s * 0.45f, cy + s * 0.2f), paint)
                canvas.drawOval(RectF(cx + s * 0.45f, cy - s * 0.2f, cx + s * 0.7f, cy + s * 0.2f), paint)
            }
            else -> {
                paint.color = Color.argb(140, Color.red(p.particleColor), Color.green(p.particleColor), Color.blue(p.particleColor))
                for (i in 0..4) {
                    val px = cx + (i * 31 % 13 - 6).toFloat() * s * 0.13f
                    val py = cy + (i * 19 % 9 - 4).toFloat() * s * 0.13f
                    canvas.drawRect(px, py, px + 2f, py + 2f, paint)
                }
            }
        }
    }

    // =========================================================================
    // createTileBitmap — para SpriteCache
    // =========================================================================

    fun createTileBitmap(
        tileType: TileType, tileW: Int, tileH: Int, palette: BiomePalette,
        tileX: Int = 0, tileY: Int = 0, biome: Biome = Biome.MINA_ABANDONADA
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(tileW.coerceAtLeast(1), tileH.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        when (tileType) {
            TileType.WALL         -> renderWallTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, tileX, tileY)
            TileType.FLOOR        -> renderFloorTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, tileX, tileY)
            TileType.ENTRADA      -> renderEntradaTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat())
            TileType.SAIDA        -> renderSaidaTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat())
            TileType.DECORATIVE_0 -> renderDecorativeTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, 0, biome, tileX, tileY)
            TileType.DECORATIVE_1 -> renderDecorativeTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, 1, biome, tileX, tileY)
            TileType.DECORATIVE_2 -> renderDecorativeTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, 2, biome, tileX, tileY)
            TileType.DECORATIVE_3 -> renderDecorativeTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, 3, biome, tileX, tileY)
        }
        return bitmap
    }

    // =========================================================================
    // Utilitários de cor
    // =========================================================================

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
}
