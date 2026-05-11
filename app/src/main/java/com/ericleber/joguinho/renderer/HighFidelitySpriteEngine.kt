package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.util.Random

/**
 * Motor de geração de texturas procedurais para o "Spelunky Mode".
 */
object HighFidelitySpriteEngine {

    private val paint = Paint().apply { isAntiAlias = false }
    private val cache = mutableMapOf<String, Bitmap>()

    /**
     * Gera uma textura de pedra detalhada.
     */
    fun getStoneTexture(size: Int, baseColor: Int, seed: Long): Bitmap {
        val key = "stone_${baseColor}_$size"
        return cache.getOrPut(key) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val rng = Random(seed)

            // 1. Base
            canvas.drawColor(baseColor)

            // 2. Ruído de Grão (Grain)
            for (i in 0 until (size * size / 10)) {
                val alpha = rng.nextInt(30) + 10
                paint.color = if (rng.nextBoolean()) Color.BLACK else Color.WHITE
                paint.alpha = alpha
                val px = rng.nextInt(size).toFloat()
                val py = rng.nextInt(size).toFloat()
                canvas.drawPoint(px, py, paint)
            }

            // 3. Rachaduras (Cracks)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            repeat(3) {
                paint.color = Color.BLACK
                paint.alpha = 40
                var cx = rng.nextInt(size).toFloat()
                var cy = rng.nextInt(size).toFloat()
                repeat(5) {
                    val nx = cx + rng.nextInt(11) - 5
                    val ny = cy + rng.nextInt(11) - 5
                    canvas.drawLine(cx, cy, nx, ny, paint)
                    cx = nx
                    cy = ny
                }
            }

            bitmap
        }
    }

    /**
     * Gera uma textura de madeira com veios.
     */
    fun getWoodTexture(size: Int, baseColor: Int, seed: Long): Bitmap {
        val key = "wood_${baseColor}_$size"
        return cache.getOrPut(key) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val rng = Random(seed)

            canvas.drawColor(baseColor)

            // Veios da madeira
            paint.color = Color.BLACK
            paint.alpha = 30
            for (i in 0 until size step 4) {
                val offset = rng.nextInt(3) - 1
                canvas.drawLine(i.toFloat() + offset, 0f, i.toFloat() + offset, size.toFloat(), paint)
            }

            bitmap
        }
    }

    fun clearCache() {
        cache.values.forEach { it.recycle() }
        cache.clear()
    }
}
