package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.sin

class ProceduralBackground(private val floor: Int) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()
    private val matrix = Matrix()

    private var bgTextureBitmap: Bitmap? = null
    private var lastW = 0
    private var lastH = 0
    private var lastColor = 0

    // Cache de gradientes da névoa
    private val cachedFogGradients = arrayOfNulls<RadialGradient>(8)
    private val cachedFogRadii = FloatArray(8)
    private val fogBaseX = FloatArray(8)
    private val fogBaseY = FloatArray(8)

    // Cache de estalactites
    private var estalactiteBaseX = FloatArray(0)
    private var estalactiteBaseW = FloatArray(0)
    private var estalactiteHeight = FloatArray(0)
    private var numStalactites = 0

    fun render(
        canvas: Canvas,
        cameraX: Float,
        cameraY: Float,
        screenW: Int,
        screenH: Int,
        time: Long,
        baseColor: Int
    ) {
        // Inicializa ou recria os caches se o tamanho da tela ou a cor mudarem
        checkInitCaches(screenW, screenH, baseColor)

        // Suporte inicial apenas para SUBTERRANEAN (Fallback por enquanto)
        renderCaveLayer(canvas, cameraX, cameraY, screenW, screenH, time, baseColor)
    }

    private fun checkInitCaches(screenW: Int, screenH: Int, baseColor: Int) {
        if (bgTextureBitmap == null || lastW != screenW || lastH != screenH || lastColor != baseColor) {
            lastW = screenW
            lastH = screenH
            lastColor = baseColor

            // 1. Limpa bitmap antigo se houver
            bgTextureBitmap?.recycle()

            // 2. Cria bitmap offscreen
            val bitmap = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.RGB_565)
            val tempCanvas = Canvas(bitmap)

            val r = Color.red(baseColor)
            val g = Color.green(baseColor)
            val b = Color.blue(baseColor)

            val bgR = (r * 0.55f).toInt().coerceIn(0, 255)
            val bgG = (g * 0.55f).toInt().coerceIn(0, 255)
            val bgB = (b * 0.55f).toInt().coerceIn(0, 255)
            val bgColor = Color.rgb(bgR, bgG, bgB)

            val tempPaint = Paint().apply {
                isAntiAlias = false
                style = Paint.Style.FILL
            }

            // Preenche fundo
            tempPaint.color = bgColor
            tempCanvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), tempPaint)

            // Noise de pedra
            val pixelSize = 4f
            val cols = (screenW / pixelSize).toInt() + 1
            val rows = (screenH / pixelSize).toInt() + 1

            for (row in 0..rows) {
                for (col in 0..cols) {
                    val seed = (col * 73856093L xor row * 19349663L xor floor.toLong())
                    val rng = java.util.Random(seed)
                    val factor = -0.25f + rng.nextFloat() * 0.50f
                    if (factor > 0.05f || factor < -0.05f) {
                        val vr = (bgR * (1f + factor)).toInt().coerceIn(0, 255)
                        val vg = (bgG * (1f + factor)).toInt().coerceIn(0, 255)
                        val vb = (bgB * (1f + factor)).toInt().coerceIn(0, 255)
                        tempPaint.color = Color.rgb(vr, vg, vb)
                        val px = col * pixelSize
                        val py = row * pixelSize
                        tempCanvas.drawRect(px, py, px + pixelSize, py + pixelSize, tempPaint)
                    }
                }
            }
            bgTextureBitmap = bitmap

            // 3. Inicializa Gradients e Posições da Névoa (Estáticos por andar/bioma)
            val fogR = (r * 0.80f).toInt().coerceIn(0, 255)
            val fogG = (g * 0.80f).toInt().coerceIn(0, 255)
            val fogB = (b * 0.80f).toInt().coerceIn(0, 255)
            val fogWrap = screenW * 2f

            for (i in 0 until 8) {
                val seed = i * 19349663L xor floor.toLong()
                val rng = java.util.Random(seed)
                val radius = 40f + rng.nextFloat() * 40f
                val alpha = 25 + rng.nextInt(16)

                cachedFogRadii[i] = radius
                fogBaseX[i] = rng.nextFloat() * fogWrap
                fogBaseY[i] = rng.nextFloat() * screenH

                cachedFogGradients[i] = RadialGradient(
                    0f, 0f, radius,
                    Color.argb(alpha, fogR, fogG, fogB),
                    Color.argb(0, fogR, fogG, fogB),
                    Shader.TileMode.CLAMP
                )
            }

            // 4. Inicializa Estalactites (Estáticos por andar)
            val interval = 40f
            numStalactites = ((screenW * 3) / interval).toInt()
            estalactiteBaseX = FloatArray(numStalactites)
            estalactiteBaseW = FloatArray(numStalactites)
            estalactiteHeight = FloatArray(numStalactites)

            for (i in 0 until numStalactites) {
                val seed = i * 73856093L xor floor.toLong()
                val rng = java.util.Random(seed)
                estalactiteBaseX[i] = i * interval + (rng.nextFloat() * 20f - 10f)
                estalactiteBaseW[i] = 8f + rng.nextFloat() * 12f
                estalactiteHeight[i] = 20f + rng.nextFloat() * 40f
            }
        }
    }

    private fun renderCaveLayer(
        canvas: Canvas,
        cameraX: Float,
        cameraY: Float,
        screenW: Int,
        screenH: Int,
        time: Long,
        baseColor: Int
    ) {
        val bitmap = bgTextureBitmap ?: return

        // 1. Desenha textura de fundo estática offscreen cacheada (Camada 0)
        paint.shader = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // 2. Camada 1 (parallax 0.25x) — Estalactites no teto
        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)
        val stalactiteR = (r * 0.70f).toInt().coerceIn(0, 255)
        val stalactiteG = (g * 0.70f).toInt().coerceIn(0, 255)
        val stalactiteB = (b * 0.70f).toInt().coerceIn(0, 255)

        paint.color = Color.rgb(stalactiteR, stalactiteG, stalactiteB)
        paint.alpha = 180
        paint.isAntiAlias = true

        val p25X = cameraX * 0.25f
        val wrapW = screenW * 3f

        for (i in 0 until numStalactites) {
            val baseX = estalactiteBaseX[i]

            // Wrap horizontal infinito
            var posX = (baseX - p25X) % wrapW
            if (posX < 0) posX += wrapW
            posX -= screenW.toFloat() // Centraliza a janela de wrap

            if (posX > -50f && posX < screenW + 50f) {
                val baseW = estalactiteBaseW[i]
                val height = estalactiteHeight[i]

                path.reset()
                path.moveTo(posX - baseW / 2f, 0f)
                path.lineTo(posX + baseW / 2f, 0f)
                path.lineTo(posX, height)
                path.close()
                canvas.drawPath(path, paint)
            }
        }

        // 3. Camada 2 (parallax 0.45x) — Névoa de caverna
        val p45X = cameraX * 0.45f
        val fogWrap = screenW * 2f

        paint.isAntiAlias = false
        paint.style = Paint.Style.FILL

        for (i in 0 until 8) {
            val baseX = fogBaseX[i]
            val baseY = fogBaseY[i]

            var posX = (baseX - p45X) % fogWrap
            if (posX < 0) posX += fogWrap
            posX -= screenW * 0.5f

            val radius = cachedFogRadii[i]
            val floatAnim = sin(time / 4000.0 + i * 0.8).toFloat() * 10f
            val py = baseY + floatAnim

            if (posX > -100f && posX < screenW + 100f) {
                val gradient = cachedFogGradients[i]
                if (gradient != null) {
                    matrix.reset()
                    matrix.postTranslate(posX, py)
                    gradient.setLocalMatrix(matrix)

                    paint.shader = gradient
                    canvas.drawCircle(posX, py, radius, paint)
                }
            }
        }

        // Cleanup paint state
        paint.shader = null
        paint.alpha = 255
    }
}
