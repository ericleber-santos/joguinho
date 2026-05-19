package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
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
    private var lastLightingMode: com.ericleber.joguinho.biome.LightingMode? = null

    // Cache de gradientes da névoa (SUBTERRANEAN)
    private val cachedFogGradients = arrayOfNulls<RadialGradient>(8)
    private val cachedFogRadii = FloatArray(8)
    private val fogBaseX = FloatArray(8)
    private val fogBaseY = FloatArray(8)

    // Cache de estalactites (SUBTERRANEAN)
    private var estalactiteBaseX = FloatArray(0)
    private var estalactiteBaseW = FloatArray(0)
    private var estalactiteHeight = FloatArray(0)
    private var numStalactites = 0

    // Cache de pilares (MOONLIGHT)
    private var pillarBaseX = FloatArray(0)
    private var pillarBaseW = FloatArray(0)
    private var pillarHeight = FloatArray(0)
    private var numPillars = 0

    // Cache de estrelas (MOONLIGHT)
    private val starX = FloatArray(30)
    private val starY = FloatArray(30)
    private val starSize = FloatArray(30)

    // Cache de formas geométricas (VOID_DARK)
    private var shapeBaseX = FloatArray(6)
    private var shapeBaseY = FloatArray(6)
    private val shapesVertices = Array(6) { FloatArray(10) }

    // Cache de olhos (VOID_DARK)
    private val eyeX = FloatArray(6)
    private val eyeY = FloatArray(6)
    private val eyeSize = FloatArray(6)
    private val eyeGap = FloatArray(6)

    // Cache de árvores (DAYLIGHT)
    private var treeBaseX = FloatArray(0)
    private var treeWidth = FloatArray(0)
    private var treeHeight = FloatArray(0)
    private var treeCopaW = FloatArray(0)
    private var treeCopaH = FloatArray(0)
    private var numTrees = 0

    // Cache de partículas (DAYLIGHT)
    private val particleX = FloatArray(12)
    private val particleY = FloatArray(12)

    // Cache de colunas (LAVA_GLOW)
    private var columnBaseX = FloatArray(0)
    private var columnBaseW = FloatArray(0)
    private var columnHeight = FloatArray(0)
    private var numColumns = 0
    private val cachedColumnGradients = arrayOfNulls<LinearGradient>(7)

    // Cache de faíscas (LAVA_GLOW)
    private val sparkX = FloatArray(15)
    private val sparkSpeed = FloatArray(15)
    private val sparkPhase = FloatArray(15)
    private val sparkSize = FloatArray(15)

    // Cache de cogumelos (BIOLUMINESCENT)
    private var shroomBaseX = FloatArray(0)
    private var shroomBaseY = FloatArray(0)
    private var shroomHasteW = FloatArray(0)
    private var shroomHasteH = FloatArray(0)
    private var shroomCapW = FloatArray(0)
    private var shroomCapH = FloatArray(0)
    private var shroomHaloRadius = FloatArray(0)
    private var numShrooms = 0
    private val cachedShroomHaloGradients = arrayOfNulls<RadialGradient>(6)

    // Cache de esporos (BIOLUMINESCENT)
    private val esporoX = FloatArray(20)
    private val esporoY = FloatArray(20)
    private val esporoSize = FloatArray(20)
    private val esporoAlpha = IntArray(20)

    fun render(
        canvas: Canvas,
        cameraX: Float,
        cameraY: Float,
        screenW: Int,
        screenH: Int,
        time: Long,
        baseColor: Int,
        accentColor: Int,
        lightingMode: com.ericleber.joguinho.biome.LightingMode
    ) {
        // Inicializa ou recria os caches se o tamanho da tela, a cor ou o modo de luz mudarem
        checkInitCaches(screenW, screenH, baseColor, lightingMode)

        when (lightingMode) {
            com.ericleber.joguinho.biome.LightingMode.MOONLIGHT -> {
                renderMoonlightLayer(canvas, cameraX, cameraY, screenW, screenH, time)
            }
            com.ericleber.joguinho.biome.LightingMode.VOID_DARK -> {
                renderVoidDarkLayer(canvas, cameraX, cameraY, screenW, screenH, time, accentColor)
            }
            com.ericleber.joguinho.biome.LightingMode.DAYLIGHT -> {
                renderDaylightLayer(canvas, cameraX, cameraY, screenW, screenH, time)
            }
            com.ericleber.joguinho.biome.LightingMode.LAVA_GLOW -> {
                renderLavaGlowLayer(canvas, cameraX, cameraY, screenW, screenH, time)
            }
            com.ericleber.joguinho.biome.LightingMode.BIOLUMINESCENT -> {
                renderBioluminescentLayer(canvas, cameraX, cameraY, screenW, screenH, time)
            }
            else -> {
                renderCaveLayer(canvas, cameraX, cameraY, screenW, screenH, time, baseColor)
            }
        }
    }

    private fun checkInitCaches(screenW: Int, screenH: Int, baseColor: Int, lightingMode: com.ericleber.joguinho.biome.LightingMode) {
        if (bgTextureBitmap == null || lastW != screenW || lastH != screenH || lastColor != baseColor || lastLightingMode != lightingMode) {
            lastW = screenW
            lastH = screenH
            lastColor = baseColor
            lastLightingMode = lightingMode

            // 1. Limpa bitmap antigo se houver
            bgTextureBitmap?.recycle()

            val baseColorToUse = when (lightingMode) {
                com.ericleber.joguinho.biome.LightingMode.VOID_DARK -> Color.parseColor("#050508")
                com.ericleber.joguinho.biome.LightingMode.MOONLIGHT -> Color.parseColor("#0e0e18")
                com.ericleber.joguinho.biome.LightingMode.LAVA_GLOW -> Color.parseColor("#1a1210")
                com.ericleber.joguinho.biome.LightingMode.BIOLUMINESCENT -> Color.parseColor("#0a120a")
                else -> baseColor
            }

            val r = Color.red(baseColorToUse)
            val g = Color.green(baseColorToUse)
            val b = Color.blue(baseColorToUse)

            // 2. Cria bitmap offscreen
            val bitmap = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
            val tempCanvas = Canvas(bitmap)

            if (lightingMode == com.ericleber.joguinho.biome.LightingMode.DAYLIGHT) {
                // DAYLIGHT: Gradiente vertical de céu
                val skyShader = LinearGradient(
                    0f, 0f, 0f, screenH.toFloat(),
                    intArrayOf(Color.parseColor("#4488cc"), Color.parseColor("#88bbee"), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                val tempPaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                    shader = skyShader
                    alpha = 90
                }
                tempCanvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), tempPaint)
            } else {
                val bgR = if (lightingMode == com.ericleber.joguinho.biome.LightingMode.MOONLIGHT) 14 
                          else if (lightingMode == com.ericleber.joguinho.biome.LightingMode.VOID_DARK) 5
                          else if (lightingMode == com.ericleber.joguinho.biome.LightingMode.BIOLUMINESCENT) 10
                          else (r * 0.55f).toInt().coerceIn(0, 255)
                val bgG = if (lightingMode == com.ericleber.joguinho.biome.LightingMode.MOONLIGHT) 14 
                          else if (lightingMode == com.ericleber.joguinho.biome.LightingMode.VOID_DARK) 5
                          else if (lightingMode == com.ericleber.joguinho.biome.LightingMode.BIOLUMINESCENT) 18
                          else (g * 0.55f).toInt().coerceIn(0, 255)
                val bgB = if (lightingMode == com.ericleber.joguinho.biome.LightingMode.MOONLIGHT) 24 
                          else if (lightingMode == com.ericleber.joguinho.biome.LightingMode.VOID_DARK) 8
                          else if (lightingMode == com.ericleber.joguinho.biome.LightingMode.BIOLUMINESCENT) 10
                          else (b * 0.55f).toInt().coerceIn(0, 255)
                val bgColor = Color.rgb(bgR, bgG, bgB)

                val tempPaint = Paint().apply {
                    isAntiAlias = false
                    style = Paint.Style.FILL
                }

                // Preenche fundo
                tempPaint.color = bgColor
                tempCanvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), tempPaint)

                // Noise de pedra
                if (lightingMode != com.ericleber.joguinho.biome.LightingMode.VOID_DARK) {
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
                }
            }
            bgTextureBitmap = bitmap

            // 3. Inicializa Gradients e Geometria baseado no modo
            if (lightingMode == com.ericleber.joguinho.biome.LightingMode.MOONLIGHT) {
                // MOONLIGHT: Pilares
                val interval = 80f
                numPillars = ((screenW * 3) / interval).toInt()
                pillarBaseX = FloatArray(numPillars)
                pillarBaseW = FloatArray(numPillars)
                pillarHeight = FloatArray(numPillars)

                for (i in 0 until numPillars) {
                    val seed = i * 73856093L xor floor.toLong()
                    val rng = java.util.Random(seed)
                    pillarBaseX[i] = i * interval + (rng.nextFloat() * 40f - 20f)
                    pillarBaseW[i] = 8f + rng.nextFloat() * 8f // 8-16px
                    pillarHeight[i] = screenH * (0.3f + rng.nextFloat() * 0.3f) // 30-60% da tela
                }

                // MOONLIGHT: Estrelas
                for (i in 0 until 30) {
                    val seed = i * 19349663L xor floor.toLong()
                    val rng = java.util.Random(seed)
                    starX[i] = rng.nextFloat() * screenW
                    starY[i] = rng.nextFloat() * screenH
                    starSize[i] = 1f + rng.nextFloat() * 1f
                }
            } else if (lightingMode == com.ericleber.joguinho.biome.LightingMode.VOID_DARK) {
                // VOID_DARK: Formas Geométricas Irregulares
                val wrapW = screenW * 2f
                for (i in 0 until 6) {
                    val seed = i * 73856093L xor floor.toLong()
                    val rng = java.util.Random(seed)

                    shapeBaseX[i] = rng.nextFloat() * wrapW
                    shapeBaseY[i] = rng.nextFloat() * screenH

                    for (v in 0 until 5) {
                        val angle = (v * 2 * Math.PI / 5)
                        val radius = 10f + rng.nextFloat() * 15f // diâmetro 20-50px
                        val vx = kotlin.math.cos(angle) * radius
                        val vy = kotlin.math.sin(angle) * radius
                        shapesVertices[i][v * 2] = vx.toFloat()
                        shapesVertices[i][v * 2 + 1] = vy.toFloat()
                    }
                }

                // VOID_DARK: Olhos
                for (i in 0 until 6) {
                    val seed = i * 19349663L xor floor.toLong()
                    val rng = java.util.Random(seed)

                    eyeX[i] = rng.nextFloat() * screenW
                    eyeY[i] = rng.nextFloat() * screenH
                    eyeSize[i] = 1f + rng.nextFloat() * 1f // raio 1-2px (diâmetro 2-4px)
                    eyeGap[i] = 4f + rng.nextFloat() * 4f // distância 4-8px
                }
            } else if (lightingMode == com.ericleber.joguinho.biome.LightingMode.DAYLIGHT) {
                // DAYLIGHT: Árvores
                val interval = 120f
                numTrees = ((screenW * 2.5f) / interval).toInt().coerceAtLeast(6).coerceAtMost(8)
                treeBaseX = FloatArray(numTrees)
                treeWidth = FloatArray(numTrees)
                treeHeight = FloatArray(numTrees)
                treeCopaW = FloatArray(numTrees)
                treeCopaH = FloatArray(numTrees)

                val wrapW = screenW * 2.5f
                for (i in 0 until numTrees) {
                    val seed = i * 73856093L xor floor.toLong()
                    val rng = java.util.Random(seed)
                    treeBaseX[i] = rng.nextFloat() * wrapW
                    treeWidth[i] = 4f + rng.nextFloat() * 4f // 4-8px
                    treeHeight[i] = screenH * (0.15f + rng.nextFloat() * 0.10f) // 15-25%
                    treeCopaW[i] = 24f + rng.nextFloat() * 16f // 24-40px
                    treeCopaH[i] = 30f + rng.nextFloat() * 20f // 30-50px
                }

                // DAYLIGHT: Partículas
                for (i in 0 until 12) {
                    val seed = i * 19349663L xor floor.toLong()
                    val rng = java.util.Random(seed)
                    particleX[i] = rng.nextFloat() * screenW
                    particleY[i] = rng.nextFloat() * screenH
                }
            } else if (lightingMode == com.ericleber.joguinho.biome.LightingMode.LAVA_GLOW) {
                // LAVA_GLOW: Colunas
                val interval = 120f
                val wrapW = screenW * 2.5f
                val rngCol = java.util.Random(73856093L xor floor.toLong())
                numColumns = 5 + rngCol.nextInt(3) // 5 a 7
                columnBaseX = FloatArray(numColumns)
                columnBaseW = FloatArray(numColumns)
                columnHeight = FloatArray(numColumns)

                for (i in 0 until numColumns) {
                    val seed = i * 73856093L xor floor.toLong()
                    val rng = java.util.Random(seed)
                    columnBaseX[i] = rng.nextFloat() * wrapW
                    columnBaseW[i] = 20f + rng.nextFloat() * 20f // 20-40px
                    val h = screenH * (0.3f + rng.nextFloat() * 0.2f) // 30-50%
                    columnHeight[i] = h

                    // LinearGradient da base da coluna (últimos 30%)
                    val gradH = h * 0.3f
                    val gradTop = screenH - gradH
                    val gradBottom = screenH.toFloat()

                    cachedColumnGradients[i] = LinearGradient(
                        0f, gradTop, 0f, gradBottom,
                        Color.TRANSPARENT,
                        Color.parseColor("#ff4400"),
                        Shader.TileMode.CLAMP
                    )
                }

                // LAVA_GLOW: Faíscas
                for (i in 0 until 15) {
                    val seed = i * 19349663L xor floor.toLong()
                    val rng = java.util.Random(seed)
                    sparkX[i] = rng.nextFloat() * screenW
                    sparkSpeed[i] = 150f + rng.nextFloat() * 150f
                    sparkPhase[i] = rng.nextFloat() * screenH
                    sparkSize[i] = 1f + rng.nextFloat() * 1f // 1-2px
                }
            } else if (lightingMode == com.ericleber.joguinho.biome.LightingMode.BIOLUMINESCENT) {
                // BIOLUMINESCENT: Cogumelos
                val interval = 150f
                val wrapW = screenW * 2.5f
                val rngShroom = java.util.Random(73856093L xor floor.toLong())
                numShrooms = 4 + rngShroom.nextInt(3) // 4 a 6
                shroomBaseX = FloatArray(numShrooms)
                shroomBaseY = FloatArray(numShrooms)
                shroomHasteW = FloatArray(numShrooms)
                shroomHasteH = FloatArray(numShrooms)
                shroomCapW = FloatArray(numShrooms)
                shroomCapH = FloatArray(numShrooms)
                shroomHaloRadius = FloatArray(numShrooms)

                for (i in 0 until numShrooms) {
                    val seed = i * 73856093L xor floor.toLong()
                    val rng = java.util.Random(seed)
                    shroomBaseX[i] = rng.nextFloat() * wrapW
                    shroomBaseY[i] = screenH.toFloat()
                    shroomHasteW[i] = 6f + rng.nextFloat() * 6f // 6-12px
                    shroomHasteH[i] = screenH * (0.25f + rng.nextFloat() * 0.15f) // 25-40% da tela
                    shroomCapW[i] = 30f + rng.nextFloat() * 20f // 30-50px
                    shroomCapH[i] = 16f + rng.nextFloat() * 10f // 16-26px

                    val radius = 30f + rng.nextFloat() * 20f // 30-50px
                    shroomHaloRadius[i] = radius

                    cachedShroomHaloGradients[i] = RadialGradient(
                        0f, 0f, radius,
                        Color.argb(25, 170, 255, 68), // #aaff44 com alpha 25
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                }

                // BIOLUMINESCENT: Espores
                for (i in 0 until 20) {
                    val seed = i * 19349663L xor floor.toLong()
                    val rng = java.util.Random(seed)
                    esporoX[i] = rng.nextFloat() * screenW
                    esporoY[i] = rng.nextFloat() * screenH
                    esporoSize[i] = 1f + rng.nextFloat() * 0.5f // raio 1-1.5px (diâmetro 2-3px)
                    esporoAlpha[i] = 60 + rng.nextInt(31) // 60 a 90
                }
            } else {
                // SUBTERRANEAN: Gradients da Névoa
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

                // SUBTERRANEAN: Estalactites
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

    private fun renderMoonlightLayer(
        canvas: Canvas,
        cameraX: Float,
        cameraY: Float,
        screenW: Int,
        screenH: Int,
        time: Long
    ) {
        val bitmap = bgTextureBitmap ?: return

        // 1. Camada 0: Pedra cinza-índigo escura offscreen cacheada
        paint.shader = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // 2. Camada 1: Pilares de ruína
        paint.color = Color.parseColor("#1a1828")
        paint.alpha = 70
        paint.isAntiAlias = true

        val p25X = cameraX * 0.25f
        val wrapW = screenW * 3f

        for (i in 0 until numPillars) {
            val baseX = pillarBaseX[i]

            // Wrap horizontal
            var posX = (baseX - p25X) % wrapW
            if (posX < 0) posX += wrapW
            posX -= screenW.toFloat()

            if (posX > -50f && posX < screenW + 50f) {
                val w = pillarBaseW[i]
                val h = pillarHeight[i]

                // Desenha retângulo subindo do chão (screenH)
                canvas.drawRect(
                    posX - w / 2f,
                    screenH - h,
                    posX + w / 2f,
                    screenH.toFloat(),
                    paint
                )
            }
        }

        // 3. Camada 2: Estrelas fixas (não se movem com a câmera)
        paint.color = Color.WHITE
        paint.isAntiAlias = false

        for (i in 0 until 30) {
            val alpha = (sin(time / 1100.0 + i * 2.3).toFloat() * 40f + 60f).toInt().coerceIn(0, 255)
            paint.alpha = alpha

            val sx = starX[i]
            val sy = starY[i]
            val size = starSize[i]

            canvas.drawRect(sx, sy, sx + size, sy + size, paint)
        }

        // Cleanup
        paint.alpha = 255
    }

    private fun renderVoidDarkLayer(
        canvas: Canvas,
        cameraX: Float,
        cameraY: Float,
        screenW: Int,
        screenH: Int,
        time: Long,
        accentColor: Int
    ) {
        val bitmap = bgTextureBitmap ?: return

        // 1. Camada 0: Preto quase puro (#050508) sólido
        paint.shader = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // 2. Camada 1: 4-6 formas geométricas irregulares silhueta muito escura (#0a0a12, alpha 40)
        paint.color = Color.parseColor("#0a0a12")
        paint.alpha = 40
        paint.isAntiAlias = true

        val p25X = cameraX * 0.25f
        val wrapW = screenW * 2f

        for (i in 0 until 6) {
            val baseX = shapeBaseX[i]
            val baseY = shapeBaseY[i]

            // Wrap horizontal
            var posX = (baseX - p25X) % wrapW
            if (posX < 0) posX += wrapW
            posX -= screenW * 0.5f

            if (posX > -100f && posX < screenW + 100f) {
                path.reset()
                val x0 = posX + shapesVertices[i][0]
                val y0 = baseY + shapesVertices[i][1]
                path.moveTo(x0, y0)
                for (v in 1 until 5) {
                    val xv = posX + shapesVertices[i][v * 2]
                    val yv = baseY + shapesVertices[i][v * 2 + 1]
                    path.lineTo(xv, yv)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
        }

        // 3. Camada 2: 6 pares de olhos (cor accent, alpha pulsando, piscam lentamente)
        paint.color = accentColor
        paint.isAntiAlias = true

        for (i in 0 until 6) {
            val pulse = sin(time / 4000.0 + i * 2.1).toFloat()
            val alpha = ((pulse + 1f) * 0.5f * 255f).toInt().coerceIn(0, 255)

            if (alpha >= 10) {
                paint.alpha = alpha
                val sx = eyeX[i]
                val sy = eyeY[i]
                val r = eyeSize[i]
                val gap = eyeGap[i]

                // Olho esquerdo
                canvas.drawCircle(sx - gap / 2f, sy, r, paint)
                // Olho direito
                canvas.drawCircle(sx + gap / 2f, sy, r, paint)
            }
        }

        // Cleanup
        paint.alpha = 255
    }

    private fun renderDaylightLayer(
        canvas: Canvas,
        cameraX: Float,
        cameraY: Float,
        screenW: Int,
        screenH: Int,
        time: Long
    ) {
        val bitmap = bgTextureBitmap ?: return

        // 1. Camada 0: Gradiente vertical de céu cacheado (desenhado com alpha 90)
        paint.shader = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // 2. Camada 1: 6-8 árvores em silhueta (#051804, alpha 80)
        paint.color = Color.parseColor("#051804")
        paint.alpha = 80
        paint.isAntiAlias = true

        val p25X = cameraX * 0.25f
        val wrapW = screenW * 2.5f

        for (i in 0 until numTrees) {
            val baseX = treeBaseX[i]

            // Wrap horizontal
            var posX = (baseX - p25X) % wrapW
            if (posX < 0) posX += wrapW
            posX -= screenW * 0.5f

            if (posX > -100f && posX < screenW + 100f) {
                val trunkW = treeWidth[i]
                val treeH = treeHeight[i]
                val copaW = treeCopaW[i]
                val copaH = treeCopaH[i]

                // Tronco retangular
                canvas.drawRect(
                    posX - trunkW / 2f,
                    screenH - treeH,
                    posX + trunkW / 2f,
                    screenH.toFloat(),
                    paint
                )

                // Copa oval
                val copaLeft = posX - copaW / 2f
                val copaTop = screenH - treeH - copaH / 2f
                val copaRight = posX + copaW / 2f
                val copaBottom = screenH - treeH + copaH / 2f
                canvas.drawOval(copaLeft, copaTop, copaRight, copaBottom, paint)
            }
        }

        // 3. Camada 2: 12 partículas de luz flutuando (#ffffcc, alpha 50)
        paint.color = Color.parseColor("#ffffcc")
        paint.alpha = 50
        paint.isAntiAlias = true

        for (i in 0 until 12) {
            val drift = sin(time / 5000.0 + i * 0.6).toFloat() * 18f
            val px = particleX[i] + drift
            val py = particleY[i]

            canvas.drawCircle(px, py, 1f, paint) // círculo de 2px
        }

        // Cleanup
        paint.alpha = 255
    }

    private fun renderLavaGlowLayer(
        canvas: Canvas,
        cameraX: Float,
        cameraY: Float,
        screenW: Int,
        screenH: Int,
        time: Long
    ) {
        val bitmap = bgTextureBitmap ?: return

        // 1. Camada 0: Basalto escuro com noise cacheado offscreen
        paint.shader = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // 2. Camada 1: 5-7 colunas de basalto subindo do chão
        val p25X = cameraX * 0.25f
        val wrapW = screenW * 2.5f

        for (i in 0 until numColumns) {
            val baseX = columnBaseX[i]

            // Wrap horizontal
            var posX = (baseX - p25X) % wrapW
            if (posX < 0) posX += wrapW
            posX -= screenW * 0.5f

            if (posX > -100f && posX < screenW + 100f) {
                val w = columnBaseW[i]
                val h = columnHeight[i]

                // Corpo da coluna (#120a08, alpha 80)
                paint.shader = null
                paint.color = Color.parseColor("#120a08")
                paint.alpha = 80
                paint.style = Paint.Style.FILL
                canvas.drawRect(
                    posX - w / 2f,
                    screenH - h,
                    posX + w / 2f,
                    screenH.toFloat(),
                    paint
                )

                // Brilho na base (LinearGradient transparente -> #ff4400, alpha 60 nos últimos 30% da altura da coluna)
                val gradient = cachedColumnGradients[i]
                if (gradient != null) {
                    matrix.reset()
                    matrix.postTranslate(posX, 0f) // Apenas translada horizontalmente
                    gradient.setLocalMatrix(matrix)

                    paint.shader = gradient
                    paint.alpha = 60
                    canvas.drawRect(
                        posX - w / 2f,
                        screenH - h * 0.3f,
                        posX + w / 2f,
                        screenH.toFloat(),
                        paint
                    )
                }
            }
        }

        // 3. Camada 2: 15 faíscas ascendentes animadas
        paint.shader = null
        paint.color = Color.parseColor("#ffcc00")

        for (i in 0 until 15) {
            val speed = sparkSpeed[i]
            val phase = sparkPhase[i]
            val progress = (time / 2000f * speed + phase) % screenH
            val posY = screenH - progress
            val posX = sparkX[i]
            val size = sparkSize[i]

            // Alpha diminui conforme sobe
            val alphaRatio = 1f - (progress / screenH)
            val alpha = (alphaRatio * 255f).toInt().coerceIn(0, 255)

            paint.alpha = alpha
            canvas.drawRect(posX, posY, posX + size, posY + size, paint)
        }

        // Cleanup
        paint.alpha = 255
    }

    private fun renderBioluminescentLayer(
        canvas: Canvas,
        cameraX: Float,
        cameraY: Float,
        screenW: Int,
        screenH: Int,
        time: Long
    ) {
        val bitmap = bgTextureBitmap ?: return

        // 1. Camada 0: Pedra escura esverdeada offscreen cacheada com noise
        paint.shader = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // 2. Camada 1: 4-6 cogumelos gigantes em silhueta (#0f1a0f, alpha 70) + Halo
        val p25X = cameraX * 0.25f
        val wrapW = screenW * 2.5f

        for (i in 0 until numShrooms) {
            val baseX = shroomBaseX[i]
            val baseY = shroomBaseY[i]

            // Wrap horizontal
            var posX = (baseX - p25X) % wrapW
            if (posX < 0) posX += wrapW
            posX -= screenW * 0.5f

            if (posX > -100f && posX < screenW + 100f) {
                val hasteW = shroomHasteW[i]
                val hasteH = shroomHasteH[i]
                val capW = shroomCapW[i]
                val capH = shroomCapH[i]
                val haloRadius = shroomHaloRadius[i]

                val capCenterX = posX
                val capCenterY = baseY - hasteH

                // Desenha Halo bioluminescente (#aaff44 alpha 25, raio 30-50px)
                val haloGradient = cachedShroomHaloGradients[i]
                if (haloGradient != null) {
                    matrix.reset()
                    matrix.postTranslate(capCenterX, capCenterY)
                    haloGradient.setLocalMatrix(matrix)

                    paint.shader = haloGradient
                    paint.alpha = 25
                    canvas.drawCircle(capCenterX, capCenterY, haloRadius, paint)
                }

                // Desenha Cogumelo em silhueta (#0f1a0f, alpha 70)
                paint.shader = null
                paint.color = Color.parseColor("#0f1a0f")
                paint.alpha = 70

                // Haste = retângulo fino
                canvas.drawRect(
                    posX - hasteW / 2f,
                    baseY - hasteH,
                    posX + hasteW / 2f,
                    baseY,
                    paint
                )

                // Corpo/Copa = oval largo
                val capLeft = capCenterX - capW / 2f
                val capTop = capCenterY - capH / 2f
                val capRight = capCenterX + capW / 2f
                val capBottom = capCenterY + capH / 2f
                canvas.drawOval(capLeft, capTop, capRight, capBottom, paint)
            }
        }

        // 3. Camada 2: 20 esporos flutuantes (#aaff44, alpha 60-90, drift senoidal horizontal e wrap total)
        paint.shader = null
        paint.color = Color.parseColor("#aaff44")
        paint.isAntiAlias = true

        val p45X = cameraX * 0.45f
        val p45Y = cameraY * 0.45f

        for (i in 0 until 20) {
            val drift = sin(time / 5000.0 + i * 0.4).toFloat() * 15f
            var px = (esporoX[i] + drift - p45X) % screenW
            if (px < 0) px += screenW
            var py = (esporoY[i] - p45Y) % screenH
            if (py < 0) py += screenH

            paint.alpha = esporoAlpha[i]
            canvas.drawCircle(px, py, esporoSize[i], paint)
        }

        // Cleanup
        paint.alpha = 255
    }
}
