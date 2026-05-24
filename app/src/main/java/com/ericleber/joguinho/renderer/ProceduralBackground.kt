package com.ericleber.joguinho.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

class ProceduralBackground(private val floor: Int) {
    // Texturas PNG para parallax (carregadas de assets)
    private var texLayer0: Bitmap? = null // camada frontal (camada1.png, parallax 0.50x)
    private var texLayer1: Bitmap? = null // camada média (camada2.png, parallax 0.25x)
    private var texLayer2: Bitmap? = null // camada mais distante (camada3.png, parallax 0.05x)
    private var texturesLoaded = false

    fun hasTextures(): Boolean = texturesLoaded

    fun loadParallaxTextures(context: Context) {
        try {
            texLayer0 = BitmapFactory.decodeStream(
                context.assets.open("textures/entranhas-camada1.png")
            )
            texLayer1 = BitmapFactory.decodeStream(
                context.assets.open("textures/entranhas-camada2.png")
            )
            texLayer2 = BitmapFactory.decodeStream(
                context.assets.open("textures/entranhas-camada3.png")
            )
            texturesLoaded = texLayer0 != null && texLayer1 != null && texLayer2 != null
        } catch (e: Exception) {
            texturesLoaded = false
        }
    }

    private fun renderTextureLayer(
        canvas: Canvas, bitmap: Bitmap,
        cameraX: Float, cameraY: Float,
        screenW: Int, screenH: Int,
        parallaxFactor: Float,
        alpha: Int = 255
    ) {
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        paint.shader = shader
        paint.alpha = alpha.coerceIn(0, 255)
        paint.style = Paint.Style.FILL
        val m = Matrix()
        m.postTranslate(cameraX * parallaxFactor, cameraY * parallaxFactor)
        shader.setLocalMatrix(m)
        canvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), paint)
        paint.shader = null
        paint.alpha = 255
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private val FACTOR_FAR  = 0.05f
    private val FACTOR_MID  = 0.25f
    private val FACTOR_NEAR = 0.50f

    private val WRAP_FAR  = 4f
    private val WRAP_MID  = 3f
    private val WRAP_NEAR = 2.5f

    private fun rng(seed: Long) = java.util.Random(seed xor (floor.toLong() * 2654435761L))

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
        when (lightingMode) {
            com.ericleber.joguinho.biome.LightingMode.BIOLUMINESCENT ->
                renderBio(canvas, cameraX, cameraY, screenW, screenH, time)
            com.ericleber.joguinho.biome.LightingMode.SUBTERRANEAN ->
                renderCave(canvas, cameraX, cameraY, screenW, screenH, time, baseColor)
            com.ericleber.joguinho.biome.LightingMode.MOONLIGHT ->
                renderMoon(canvas, cameraX, cameraY, screenW, screenH, time)
            com.ericleber.joguinho.biome.LightingMode.VOID_DARK ->
                renderVoid(canvas, cameraX, cameraY, screenW, screenH, time, accentColor)
            com.ericleber.joguinho.biome.LightingMode.LAVA_GLOW ->
                renderLava(canvas, cameraX, cameraY, screenW, screenH, time)
            com.ericleber.joguinho.biome.LightingMode.DAYLIGHT ->
                renderDay(canvas, cameraX, cameraY, screenW, screenH, time)
        }
    }

    // =========================================================================
    // BIOLUMINESCENT — Jardim Profundo
    // =========================================================================
    private fun renderBio(canvas: Canvas, camX: Float, camY: Float, w: Int, h: Int, time: Long) {
        val wf = w.toFloat(); val hf = h.toFloat()

        // CAMADA 3 — fundo + névoa distante (fator 0.05)
        paint.shader = LinearGradient(0f, 0f, 0f, hf,
            intArrayOf(Color.parseColor("#060f06"), Color.parseColor("#0a1a0a"), Color.parseColor("#0d200d")),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, wf, hf, paint)
        paint.shader = null

        val areaFar = wf * WRAP_FAR
        for (i in 0 until 6) {
            val r = rng(i * 100L)
            val baseX = r.nextFloat() * areaFar
            val posX = ((baseX - camX * FACTOR_FAR) % areaFar + areaFar) % areaFar - wf * 0.5f
            val posY = r.nextFloat() * hf + sin(time / 5000.0 + i).toFloat() * 15f
            val radius = 80f + r.nextFloat() * 60f
            val alpha = (sin(time / 3000.0 + i * 1.3) * 12 + 18).toInt()
            paint.shader = RadialGradient(posX, posY, radius,
                intArrayOf(Color.argb(alpha, 40, 180, 60), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(posX, posY, radius, paint)
            paint.shader = null
        }

        // CAMADA 2 — cogumelos + algas (fator 0.25)
        val areaMid = wf * WRAP_MID
        for (i in 0 until 10) {
            val r = rng(i * 200L + 1L)
            val baseX = r.nextFloat() * areaMid
            val posX = ((baseX - camX * FACTOR_MID) % areaMid + areaMid) % areaMid - wf * 0.5f
            if (posX < -120f || posX > wf + 120f) continue
            val hasteH = hf * (0.20f + r.nextFloat() * 0.20f)
            val hasteW = 8f + r.nextFloat() * 8f
            val capW = 40f + r.nextFloat() * 40f
            val capH = 20f + r.nextFloat() * 15f
            val pulse = (sin(time / 2000.0 + i * 0.7) * 0.4 + 0.6).toFloat()
            paint.shader = RadialGradient(posX, hf - hasteH, capW * 0.8f,
                intArrayOf(Color.argb((30 * pulse).toInt(), 100, 255, 80), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(posX, hf - hasteH, capW * 0.8f, paint)
            paint.shader = null
            paint.color = Color.parseColor("#0f1a0f"); paint.alpha = 180
            canvas.drawRect(posX - hasteW/2f, hf - hasteH, posX + hasteW/2f, hf, paint)
            canvas.drawOval(posX - capW/2f, hf - hasteH - capH/2f, posX + capW/2f, hf - hasteH + capH/2f, paint)
            paint.alpha = 255
        }
        for (i in 0 until 8) {
            val r = rng(i * 300L + 2L)
            val baseX = r.nextFloat() * areaMid
            val posX = ((baseX - camX * FACTOR_MID) % areaMid + areaMid) % areaMid - wf * 0.5f
            if (posX < -60f || posX > wf + 60f) continue
            val algaH = hf * (0.12f + r.nextFloat() * 0.12f)
            val sway = sin(time / 1800.0 + i * 0.9).toFloat() * 14f
            paint.color = Color.argb(160, 20, 130, 60)
            paint.strokeWidth = 5f; paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
            path.reset()
            path.moveTo(posX, hf)
            path.cubicTo(posX + sway, hf - algaH * 0.33f, posX - sway, hf - algaH * 0.66f, posX + sway * 0.5f, hf - algaH)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL; paint.strokeWidth = 0f
        }

        // CAMADA 1 — esporos flutuantes (fator 0.50)
        val areaNear = wf * WRAP_NEAR
        for (i in 0 until 25) {
            val r = rng(i * 400L + 3L)
            val baseX = r.nextFloat() * areaNear
            val baseY = r.nextFloat() * hf
            val posX = ((baseX - camX * FACTOR_NEAR) % areaNear + areaNear) % areaNear
            val px = (posX + sin(time / 4000.0 + i * 0.5).toFloat() * 18f + wf) % wf
            val py = (baseY + cos(time / 3200.0 + i * 0.8).toFloat() * 10f + hf) % hf
            val glow = (sin(time / 1200.0 + i * 2.1) * 0.35 + 0.65).toFloat()
            paint.shader = RadialGradient(px, py, 5f,
                intArrayOf(Color.argb((90 * glow).toInt(), 120, 255, 80), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(px, py, 3f, paint)
            paint.shader = null
        }
    }

    // =========================================================================
    // SUBTERRANEAN — Entranhas, Abismos Aquáticos, Minas, Ruínas
    // =========================================================================
    private fun renderCave(canvas: Canvas, camX: Float, camY: Float, w: Int, h: Int, time: Long, baseColor: Int) {
        if (texturesLoaded) {
            // Camada 3 (fundo azul distante): mais distante, fator 0.05x, alpha 255
            texLayer2?.let { renderTextureLayer(canvas, it, camX, camY, w, h, FACTOR_FAR, 255) }
            // Camada 2 (estalactites): camada média, fator 0.25x, alpha 255
            texLayer1?.let { renderTextureLayer(canvas, it, camX, camY, w, h, FACTOR_MID, 255) }
            // Camada 1 (caverna frontal): camada mais próxima, fator 0.50x, alpha 255
            texLayer0?.let { renderTextureLayer(canvas, it, camX, camY, w, h, FACTOR_NEAR, 255) }
            return
        }

        val wf = w.toFloat(); val hf = h.toFloat()
        val br = (Color.red(baseColor) * 0.35f).toInt().coerceIn(0, 60)
        val bg = (Color.green(baseColor) * 0.35f).toInt().coerceIn(0, 60)
        val bb = (Color.blue(baseColor) * 0.35f).toInt().coerceIn(0, 80)
        val stalR = (Color.red(baseColor) * 0.55f).toInt().coerceIn(0, 255)
        val stalG = (Color.green(baseColor) * 0.55f).toInt().coerceIn(0, 255)
        val stalB = (Color.blue(baseColor) * 0.55f).toInt().coerceIn(0, 255)

        // CAMADA 3 — fundo escuro + estalactites distantes (fator 0.05)
        paint.color = Color.rgb(br, bg, bb)
        canvas.drawRect(0f, 0f, wf, hf, paint)

        val areaFar = wf * WRAP_FAR
        paint.color = Color.argb(80, stalR, stalG, stalB)
        for (i in 0 until 20) {
            val r = rng(i * 50L + 10L)
            val baseX = r.nextFloat() * areaFar
            val posX = ((baseX - camX * FACTOR_FAR) % areaFar + areaFar) % areaFar - wf * 0.5f
            if (posX < -30f || posX > wf + 30f) continue
            val bw = 10f + r.nextFloat() * 14f; val bh = 25f + r.nextFloat() * 50f
            path.reset(); path.moveTo(posX - bw/2f, 0f); path.lineTo(posX + bw/2f, 0f); path.lineTo(posX, bh); path.close()
            canvas.drawPath(path, paint)
        }

        // CAMADA 2 — estalactites médias + estalagmites (fator 0.25)
        val areaMid = wf * WRAP_MID
        paint.color = Color.argb(150, stalR, stalG, stalB)
        for (i in 0 until 15) {
            val r = rng(i * 150L + 20L)
            val baseX = r.nextFloat() * areaMid
            val posX = ((baseX - camX * FACTOR_MID) % areaMid + areaMid) % areaMid - wf * 0.5f
            if (posX < -40f || posX > wf + 40f) continue
            val bw = 8f + r.nextFloat() * 10f; val bh = 20f + r.nextFloat() * 35f
            path.reset(); path.moveTo(posX - bw/2f, 0f); path.lineTo(posX + bw/2f, 0f); path.lineTo(posX, bh); path.close()
            canvas.drawPath(path, paint)
            val gh = 15f + r.nextFloat() * 25f
            path.reset(); path.moveTo(posX - bw/2f, hf); path.lineTo(posX + bw/2f, hf); path.lineTo(posX, hf - gh); path.close()
            canvas.drawPath(path, paint)
        }

        // CAMADA 1 — partículas de poeira (fator 0.50)
        val areaNear = wf * WRAP_NEAR
        for (i in 0 until 20) {
            val r = rng(i * 200L + 30L)
            val baseX = r.nextFloat() * areaNear; val baseY = r.nextFloat() * hf
            val posX = ((baseX - camX * FACTOR_NEAR) % areaNear + areaNear) % areaNear
            val px = (posX + sin(time / 5000.0 + i * 0.6).toFloat() * 12f + wf) % wf
            val py = (baseY + cos(time / 4000.0 + i * 0.9).toFloat() * 8f + hf) % hf
            val alpha = (sin(time / 2000.0 + i * 1.5) * 20 + 35).toInt()
            paint.color = Color.argb(alpha, stalR + 20, stalG + 20, stalB + 20)
            canvas.drawCircle(px, py, 2f + r.nextFloat() * 2f, paint)
        }
    }

    // =========================================================================
    // MOONLIGHT — Reino da Magia, Base Lunar
    // =========================================================================
    private fun renderMoon(canvas: Canvas, camX: Float, camY: Float, w: Int, h: Int, time: Long) {
        val wf = w.toFloat(); val hf = h.toFloat()

        // CAMADA 3 — céu noturno + estrelas fixas + ruínas distantes (fator 0.05)
        paint.shader = LinearGradient(0f, 0f, 0f, hf,
            intArrayOf(Color.parseColor("#05050f"), Color.parseColor("#0a0a1a"), Color.parseColor("#0e0e22")),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, wf, hf, paint); paint.shader = null

        for (i in 0 until 50) {
            val r = rng(i * 77L)
            val sx = r.nextFloat() * wf; val sy = r.nextFloat() * hf * 0.8f
            val twinkle = (sin(time / 1100.0 + i * 2.3) * 0.4 + 0.6).toFloat()
            paint.color = Color.argb((80 * twinkle).toInt(), 200, 210, 255)
            val sz = 1f + r.nextFloat() * 1.5f
            canvas.drawRect(sx, sy, sx + sz, sy + sz, paint)
        }

        val areaFar = wf * WRAP_FAR
        for (i in 0 until 8) {
            val r = rng(i * 120L + 5L)
            val baseX = r.nextFloat() * areaFar
            val posX = ((baseX - camX * FACTOR_FAR) % areaFar + areaFar) % areaFar - wf * 0.5f
            if (posX < -80f || posX > wf + 80f) continue
            val colH = hf * (0.15f + r.nextFloat() * 0.20f); val colW = 12f + r.nextFloat() * 10f
            paint.color = Color.argb(50, 20, 20, 40)
            canvas.drawRect(posX - colW/2f, hf - colH, posX + colW/2f, hf, paint)
            canvas.drawRect(posX - colW, hf - colH, posX + colW, hf - colH + colW * 0.4f, paint)
        }

        // CAMADA 2 — pilares e arcos médios (fator 0.25)
        val areaMid = wf * WRAP_MID
        for (i in 0 until 10) {
            val r = rng(i * 180L + 6L)
            val baseX = r.nextFloat() * areaMid
            val posX = ((baseX - camX * FACTOR_MID) % areaMid + areaMid) % areaMid - wf * 0.5f
            if (posX < -80f || posX > wf + 80f) continue
            val colH = hf * (0.25f + r.nextFloat() * 0.25f); val colW = 10f + r.nextFloat() * 8f
            paint.color = Color.argb(80, 18, 18, 35)
            canvas.drawRect(posX - colW/2f, hf - colH, posX + colW/2f, hf, paint)
        }

        // CAMADA 1 — partículas mágicas (fator 0.50)
        val areaNear = wf * WRAP_NEAR
        for (i in 0 until 20) {
            val r = rng(i * 250L + 7L)
            val baseX = r.nextFloat() * areaNear; val baseY = r.nextFloat() * hf
            val posX = ((baseX - camX * FACTOR_NEAR) % areaNear + areaNear) % areaNear
            val px = (posX + sin(time / 6000.0 + i * 0.5).toFloat() * 20f + wf) % wf
            val py = (baseY + cos(time / 4500.0 + i * 0.8).toFloat() * 12f + hf) % hf
            val glow = (sin(time / 1800.0 + i * 1.3) * 0.35 + 0.65).toFloat()
            paint.shader = RadialGradient(px, py, 5f,
                intArrayOf(Color.argb((80 * glow).toInt(), 140, 100, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(px, py, 3f, paint); paint.shader = null
        }
    }

    // =========================================================================
    // VOID_DARK — Abismo do Vazio
    // =========================================================================
    private fun renderVoid(canvas: Canvas, camX: Float, camY: Float, w: Int, h: Int, time: Long, accentColor: Int) {
        val wf = w.toFloat(); val hf = h.toFloat()

        // CAMADA 3 — preto absoluto + formas distantes (fator 0.05)
        paint.color = Color.parseColor("#050508")
        canvas.drawRect(0f, 0f, wf, hf, paint)

        val areaFar = wf * WRAP_FAR
        for (i in 0 until 6) {
            val r = rng(i * 130L)
            val baseX = r.nextFloat() * areaFar
            val posX = ((baseX - camX * FACTOR_FAR) % areaFar + areaFar) % areaFar - wf * 0.5f
            if (posX < -60f || posX > wf + 60f) continue
            val baseY = r.nextFloat() * hf; val sz = 20f + r.nextFloat() * 30f
            paint.color = Color.argb(20, 10, 10, 20)
            path.reset()
            for (v in 0 until 5) {
                val angle = v * (Math.PI * 2 / 5)
                val rad = sz * (0.7f + r.nextFloat() * 0.3f)
                val vx = posX + cos(angle).toFloat() * rad; val vy = baseY + sin(angle).toFloat() * rad
                if (v == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
            }
            path.close(); canvas.drawPath(path, paint)
        }

        // CAMADA 2 — silhuetas de abismo (fator 0.25)
        val areaMid = wf * WRAP_MID
        for (i in 0 until 5) {
            val r = rng(i * 190L + 1L)
            val baseX = r.nextFloat() * areaMid
            val posX = ((baseX - camX * FACTOR_MID) % areaMid + areaMid) % areaMid - wf * 0.5f
            if (posX < -100f || posX > wf + 100f) continue
            paint.color = Color.argb(35, 5, 5, 12)
            canvas.drawCircle(posX, r.nextFloat() * hf, 40f + r.nextFloat() * 40f, paint)
        }

        // CAMADA 1 — olhos pulsando (fator 0.50)
        val areaNear = wf * WRAP_NEAR
        for (i in 0 until 8) {
            val r = rng(i * 270L + 2L)
            val baseX = r.nextFloat() * areaNear
            val posX = ((baseX - camX * FACTOR_NEAR) % areaNear + areaNear) % areaNear
            val ey = r.nextFloat() * hf; val gap = 5f + r.nextFloat() * 5f; val eyeR = 1.5f + r.nextFloat() * 1f
            val pulse = sin(time / 4000.0 + i * 2.1).toFloat()
            val alpha = ((pulse + 1f) * 0.5f * 200f).toInt().coerceIn(0, 200)
            if (alpha < 10) continue
            paint.color = Color.argb(alpha, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            canvas.drawCircle(posX - gap/2f, ey, eyeR, paint)
            canvas.drawCircle(posX + gap/2f, ey, eyeR, paint)
        }
    }

    // =========================================================================
    // LAVA_GLOW — Núcleo de Fogo
    // =========================================================================
    private fun renderLava(canvas: Canvas, camX: Float, camY: Float, w: Int, h: Int, time: Long) {
        val wf = w.toFloat(); val hf = h.toFloat()

        // CAMADA 3 — basalto + brilho de lava no chão (fator 0.05)
        paint.shader = LinearGradient(0f, 0f, 0f, hf,
            intArrayOf(Color.parseColor("#0f0804"), Color.parseColor("#1a1008"), Color.parseColor("#2a1505")),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, wf, hf, paint); paint.shader = null
        paint.shader = LinearGradient(0f, hf * 0.7f, 0f, hf,
            intArrayOf(Color.TRANSPARENT, Color.argb(60, 255, 80, 0)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, hf * 0.7f, wf, hf, paint); paint.shader = null

        val areaFar = wf * WRAP_FAR
        for (i in 0 until 10) {
            val r = rng(i * 110L)
            val baseX = r.nextFloat() * areaFar
            val posX = ((baseX - camX * FACTOR_FAR) % areaFar + areaFar) % areaFar - wf * 0.5f
            if (posX < -50f || posX > wf + 50f) continue
            val colW = 15f + r.nextFloat() * 20f; val colH = hf * (0.20f + r.nextFloat() * 0.25f)
            paint.color = Color.argb(130, 10, 6, 4)
            canvas.drawRect(posX - colW/2f, hf - colH, posX + colW/2f, hf, paint)
        }

        // CAMADA 2 — colunas médias com brilho na base (fator 0.25)
        val areaMid = wf * WRAP_MID
        for (i in 0 until 8) {
            val r = rng(i * 160L + 1L)
            val baseX = r.nextFloat() * areaMid
            val posX = ((baseX - camX * FACTOR_MID) % areaMid + areaMid) % areaMid - wf * 0.5f
            if (posX < -50f || posX > wf + 50f) continue
            val colW = 20f + r.nextFloat() * 20f; val colH = hf * (0.30f + r.nextFloat() * 0.20f)
            paint.color = Color.argb(170, 12, 7, 4)
            canvas.drawRect(posX - colW/2f, hf - colH, posX + colW/2f, hf, paint)
            val gradH = colH * 0.35f
            paint.shader = LinearGradient(0f, hf - gradH, 0f, hf,
                intArrayOf(Color.TRANSPARENT, Color.argb(80, 255, 60, 0)), null, Shader.TileMode.CLAMP)
            canvas.drawRect(posX - colW/2f, hf - gradH, posX + colW/2f, hf, paint); paint.shader = null
        }

        // CAMADA 1 — faíscas ascendentes (fator 0.50)
        val areaNear = wf * WRAP_NEAR
        for (i in 0 until 20) {
            val r = rng(i * 220L + 2L)
            val baseX = r.nextFloat() * areaNear
            val posX = ((baseX - camX * FACTOR_NEAR) % areaNear + areaNear) % areaNear
            val speed = 80f + r.nextFloat() * 120f
            val progress = (time / 1000f * speed / hf + r.nextFloat()) % 1f
            val py = hf - (progress * hf); if (py < 0f || py > hf) continue
            val alpha = (progress * 255f).toInt().coerceIn(0, 255)
            paint.color = Color.argb(alpha, 255, 160 + (r.nextFloat() * 60f).toInt(), 0)
            val sz = 1.5f + r.nextFloat() * 1.5f
            canvas.drawRect(posX, py, posX + sz, py + sz, paint)
        }
    }

    // =========================================================================
    // DAYLIGHT — Floresta de Árvores, Superfície Aberta
    // =========================================================================
    private fun renderDay(canvas: Canvas, camX: Float, camY: Float, w: Int, h: Int, time: Long) {
        val wf = w.toFloat(); val hf = h.toFloat()

        // CAMADA 3 — gradiente de céu + nuvens + colinas distantes (fator 0.05)
        paint.shader = LinearGradient(0f, 0f, 0f, hf,
            intArrayOf(Color.parseColor("#3377bb"), Color.parseColor("#66aadd"), Color.parseColor("#aaccee")),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, wf, hf, paint); paint.shader = null

        val areaFar = wf * WRAP_FAR
        for (i in 0 until 6) {
            val r = rng(i * 90L)
            val baseX = r.nextFloat() * areaFar
            val posX = ((baseX - camX * FACTOR_FAR) % areaFar + areaFar) % areaFar - wf * 0.5f
            if (posX < -150f || posX > wf + 150f) continue
            val cy = hf * (0.05f + r.nextFloat() * 0.25f)
            val cw = 80f + r.nextFloat() * 80f; val ch = 20f + r.nextFloat() * 15f
            paint.color = Color.argb(100, 255, 255, 255)
            canvas.drawOval(posX - cw/2f, cy - ch/2f, posX + cw/2f, cy + ch/2f, paint)
            canvas.drawOval(posX - cw/3f, cy - ch, posX + cw/3f, cy, paint)
        }
        for (i in 0 until 5) {
            val r = rng(i * 140L + 1L)
            val baseX = r.nextFloat() * areaFar
            val posX = ((baseX - camX * FACTOR_FAR) % areaFar + areaFar) % areaFar - wf * 0.5f
            if (posX < -150f || posX > wf + 150f) continue
            paint.color = Color.argb(70, 20, 60, 20)
            canvas.drawOval(posX - 120f, hf * 0.5f, posX + 120f, hf * 0.75f, paint)
        }

        // CAMADA 2 — árvores médias em silhueta (fator 0.25)
        val areaMid = wf * WRAP_MID
        for (i in 0 until 12) {
            val r = rng(i * 170L + 2L)
            val baseX = r.nextFloat() * areaMid
            val posX = ((baseX - camX * FACTOR_MID) % areaMid + areaMid) % areaMid - wf * 0.5f
            if (posX < -80f || posX > wf + 80f) continue
            val trunkH = hf * (0.12f + r.nextFloat() * 0.10f); val trunkW = 5f + r.nextFloat() * 5f
            val copaW = 30f + r.nextFloat() * 30f; val copaH = 35f + r.nextFloat() * 25f
            paint.color = Color.argb(130, 10, 30, 10)
            canvas.drawRect(posX - trunkW/2f, hf - trunkH, posX + trunkW/2f, hf, paint)
            canvas.drawOval(posX - copaW/2f, hf - trunkH - copaH/2f, posX + copaW/2f, hf - trunkH + copaH/4f, paint)
        }

        // CAMADA 1 — folhas e partículas de luz (fator 0.50)
        val areaNear = wf * WRAP_NEAR
        for (i in 0 until 18) {
            val r = rng(i * 230L + 3L)
            val baseX = r.nextFloat() * areaNear; val baseY = hf * (0.1f + r.nextFloat() * 0.8f)
            val posX = ((baseX - camX * FACTOR_NEAR) % areaNear + areaNear) % areaNear
            val px = (posX + sin(time / 5000.0 + i * 0.6).toFloat() * 20f + wf) % wf
            val py = (baseY + sin(time / 3500.0 + i * 1.1).toFloat() * 8f + hf) % hf
            val alpha = (sin(time / 2500.0 + i * 1.7) * 30 + 50).toInt()
            paint.color = Color.argb(alpha, 200, 255, 150)
            canvas.drawCircle(px, py, 2f + r.nextFloat() * 2f, paint)
        }
    }
}
