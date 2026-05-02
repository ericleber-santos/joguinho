package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Direções de movimento do Hero (8 direções cardinais/diagonais).
 */
enum class HeroDirection { N, NE, E, SE, S, SW, W, NW }

/**
 * Animações disponíveis para o Herói e Cachorro.
 */
enum class AnimState { IDLE, WALK, RUN }

/**
 * Aparência procedural de um Monster.
 */
data class MonsterAppearance(
    val bodyColor: Int,
    val eyeColor: Int,
    val size: Float,
    val shapeVariant: Int,
    val animVariant: Int,
    val isBoss: Boolean = false,
    val isHit: Boolean = false
)

class CharacterRenderer {

    // ── Paleta do Herói ───────────────────────────────────────────────────────
    private val heroSkin        = 0xFFE8B87A.toInt()
    private val heroSkinDark    = 0xFFC9944A.toInt()
    private val heroHat         = 0xFF7A5230.toInt()
    private val heroHatBrim     = 0xFF5C3A1A.toInt()
    private val heroShirt       = 0xFF4A7EC7.toInt()
    private val heroShirtDark   = 0xFF2E5FA8.toInt()
    private val heroPants       = 0xFF3A2E22.toInt()
    private val heroShoes       = 0xFF2A1A0A.toInt()
    private val heroOutline     = 0xFF1A0A00.toInt()
    private val heroEye         = 0xFF1A0A00.toInt()

    // ── Paleta do Cachorro ────────────────────────────────────────────────────
    private val dogWhite        = 0xFFF0EEE8.toInt()
    private val dogBlack        = 0xFF2A2520.toInt()
    private val dogNose         = 0xFF2A1A1A.toInt()
    private val dogEye          = 0xFF1A0A00.toInt()
    private val dogTongue       = 0xFFE06060.toInt()
    private val dogOutline      = 0xFF1A0A00.toInt()

    // ── Estado de animação ────────────────────────────────────────────────────
    private var animTick        = 0L      // tick global acumulado
    private val paint           = Paint(Paint.ANTI_ALIAS_FLAG)

    // Paints antigos (para compatibilidade com monstros e bananas)
    private val paintFill = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }
    private val paintContorno = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val path = Path()

    /**
     * Chame a cada frame do seu game loop.
     * @param deltaMs tempo em milissegundos desde o último frame
     */
    fun update(deltaMs: Long) {
        animTick += deltaMs
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HERÓI
    // ─────────────────────────────────────────────────────────────────────────

    fun drawHero(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        tileSize: Float,
        state: AnimState,
        facingLeft: Boolean = false,
        isFlashingRed: Boolean = false,
        isFlashingBlue: Boolean = false
    ) {
        val u = tileSize / 20f

        val t = animTick / 1000f

        val bodyBob: Float
        val legSwing: Float
        val armSwing: Float

        when (state) {
            AnimState.IDLE -> {
                bodyBob  = if (sin(t * 2.0) > 0.3) u * 0.5f else 0f
                legSwing = 0f
                armSwing = 0f
            }
            AnimState.WALK -> {
                val phase = sin(t * 6.0).toFloat()
                bodyBob  = if (abs(phase) > 0.7f) u * 0.5f else 0f
                legSwing = phase
                armSwing = -phase
            }
            AnimState.RUN -> {
                val phase = sin(t * 12.0).toFloat()
                bodyBob  = if ((animTick / 83) % 2 == 0L) u else 0f
                legSwing = phase * 1.5f
                armSwing = -phase * 1.2f
            }
        }

        // Tintura de status
        val currentShirt = when {
            isFlashingRed -> Color.rgb(255, 50, 50)
            isFlashingBlue -> Color.rgb(50, 150, 255)
            else -> heroShirt
        }
        val currentPants = when {
            isFlashingRed -> Color.rgb(180, 30, 30)
            isFlashingBlue -> Color.rgb(30, 80, 180)
            else -> heroPants
        }

        canvas.save()
        if (facingLeft) canvas.scale(-1f, 1f, cx, cy)

        val hatTop = cy - u * 9f + bodyBob
        fillRect(canvas, cx - u * 4f, hatTop,          u * 8f, u * 3.5f, heroHat)
        fillRect(canvas, cx - u * 2f, hatTop + u*3.5f, u * 8f,  u * 1f,   heroHatBrim) // Aba deslocada para frente
        strokeRect(canvas, cx - u*4f, hatTop,           u*8f,   u*4.5f,  heroOutline, u*0.5f)

        val headTop = cy - u * 7f + bodyBob
        fillRect(canvas, cx - u * 4f, headTop, u * 8f, u * 7f, heroSkin)
        strokeRect(canvas, cx - u*4f, headTop, u*8f, u*7f, heroOutline, u*0.5f)

        // Olhos deslocados para a direita (perfil)
        fillRect(canvas, cx + u*0.5f, headTop + u*2.5f, u*1.2f, u*1.2f, heroEye)
        fillRect(canvas, cx + u*2.5f,  headTop + u*2.5f, u*1.2f, u*1.2f, heroEye)

        fillRect(canvas, cx + u*1.0f, headTop + u*5f, u*2.5f, u*0.7f, heroSkinDark)

        val bodyTop = cy - u * 0f + bodyBob
        val armTopY = bodyTop + u * 0.5f
        val armLen  = u * 5f

        // Braço Esquerdo (atrás no perfil - sombra)
        val lArmX = cx - u * 3.8f
        val lArmSwingPx = armSwing * u * 1.5f
        fillRect(canvas, lArmX, armTopY + lArmSwingPx, u*2.2f, armLen, currentShirt)
        strokeRect(canvas, lArmX, armTopY + lArmSwingPx, u*2.2f, armLen, heroOutline, u*0.5f)
        fillCircle(canvas, lArmX + u*1.1f, armTopY + lArmSwingPx + armLen + u, u*1.3f, heroSkin)

        // CORPO (Perfil 3/4: mais estreito para dar profundidade)
        fillRect(canvas, cx - u * 3.8f, bodyTop, u * 6.5f, u * 7f, currentShirt)
        strokeRect(canvas, cx - u * 3.8f, bodyTop, u * 6.5f, u * 7f, heroOutline, u*0.5f)

        // Detalhes da camisa (botões/gola)
        fillRect(canvas, cx + u*1.0f, bodyTop + u*1f, u*1.2f, u*1.2f, heroShirtDark)
        fillRect(canvas, cx + u*1.0f, bodyTop + u*3f, u*1.2f, u*1.2f, heroShirtDark)

        // Braço Direito (à frente no perfil)
        val rArmX = cx - u * 1.5f // Recuado para alinhar com a nuca, não com o nariz
        val rArmSwingPx = -armSwing * u * 1.5f
        fillRect(canvas, rArmX, armTopY + rArmSwingPx, u*2.2f, armLen, currentShirt)
        strokeRect(canvas, rArmX, armTopY + rArmSwingPx, u*2.2f, armLen, heroOutline, u*0.5f)
        
        val handX = rArmX + u*1.1f
        val handY = armTopY + rArmSwingPx + armLen + u
        fillCircle(canvas, handX, handY, u*1.3f, heroSkin)

        // PISTOLINHA D'ÁGUA (Wap Style / Super Soaker)
        // Só aparece se não estiver em Idle ou se estiver atirando (aqui mostramos sempre para dar o "Wow")
        canvas.save()
        val gunAngle = if (state == AnimState.IDLE) 10f else -10f
        canvas.rotate(gunAngle, handX, handY)
        
        // Corpo da arma (Translúcido / Plástico)
        val gunW = u * 8f
        val gunH = u * 3f
        paint.color = Color.argb(180, 200, 230, 255) // Azul claro translúcido
        canvas.drawRoundRect(RectF(handX - u, handY - u*2, handX + gunW, handY + u), u, u, paint)
        
        // Tanque de água (Verde limão néon)
        paint.color = Color.rgb(173, 255, 47) 
        canvas.drawRoundRect(RectF(handX, handY - u*3.5f, handX + u*5f, handY - u*1.5f), u, u, paint)
        
        // Cano da arma (Cinza escuro / Wap nozzle)
        paint.color = Color.DKGRAY
        canvas.drawRect(handX + gunW - u, handY - u*1.2f, handX + gunW + u*2f, handY - u*0.3f, paint)
        
        // Detalhes (Gatilho e listras)
        paint.color = Color.RED
        canvas.drawRect(handX + u*2, handY, handX + u*3, handY + u*1.5f, paint)
        
        canvas.restore()

        val legTopY = bodyTop + u * 7f
        val legLen  = u * 5f
        val legW    = u * 2.5f
        val legSwingPx = legSwing * u * 2.5f // Balanço mais amplo para movimento dinâmico

        // Pernas (Centralizadas sob o tronco no perfil)
        // Perna 1 (Trás)
        fillRect(canvas, cx - u * 2.5f, legTopY + legSwingPx, legW, legLen, currentPants)
        strokeRect(canvas, cx - u * 2.5f, legTopY + legSwingPx, legW, legLen, heroOutline, u*0.5f)
        fillRect(canvas, cx - u * 2.8f, legTopY + legSwingPx + legLen, legW + u, u*1.8f, heroShoes)

        // Perna 2 (Frente)
        fillRect(canvas, cx + u * 0.5f, legTopY - legSwingPx, legW, legLen, currentPants)
        strokeRect(canvas, cx + u * 0.5f, legTopY - legSwingPx, legW, legLen, heroOutline, u*0.5f)
        fillRect(canvas, cx + u * 0.2f, legTopY - legSwingPx + legLen, legW + u, u*1.8f, heroShoes)

        val shadowY = cy + u * 12f
        drawShadow(canvas, cx, shadowY, u * 5f, u)

        canvas.restore()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CACHORRO
    // ─────────────────────────────────────────────────────────────────────────

    fun drawDog(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        tileSize: Float,
        state: AnimState,
        facingLeft: Boolean = false
    ) {
        val u = tileSize / 20f
        val t = animTick / 1000f

        val bodyBob: Float
        val legAnim: Float
        val tailWag: Float
        val tongueOut: Boolean

        when (state) {
            AnimState.IDLE -> {
                val cycle = (animTick / 500) % 3
                bodyBob   = 0f
                legAnim   = 0f
                tailWag   = if (cycle < 1) u * 1.5f else if (cycle < 2) -u * 1.5f else 0f
                tongueOut = cycle == 2L
            }
            AnimState.WALK -> {
                val phase = sin(t * 6.0).toFloat()
                bodyBob   = if (abs(phase) > 0.7f) u * 0.5f else 0f
                legAnim   = phase
                tailWag   = phase * u * 2f
                tongueOut = phase > 0.5f
            }
            AnimState.RUN -> {
                val phase = sin(t * 12.0).toFloat()
                bodyBob   = if ((animTick / 83) % 2 == 0L) u * 0.8f else 0f
                legAnim   = phase * 1.5f
                tailWag   = phase * u * 3f
                tongueOut = true
            }
        }

        val yOffset = u * 4f
        val renderCy = cy + yOffset

        canvas.save()
        if (facingLeft) canvas.scale(-1f, 1f, cx, renderCy)

        val tailBaseX = cx - u * 7f
        val tailBaseY = renderCy - u * 2f + bodyBob
        paint.color = dogWhite
        paint.strokeWidth = u * 1.5f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(
            tailBaseX, tailBaseY,
            tailBaseX - u * 3f, tailBaseY - u * 3f + tailWag,
            paint
        )
        paint.style = Paint.Style.FILL

        val bodyLeft = cx - u * 6f
        val bodyTop  = renderCy - u * 3f + bodyBob
        val bodyW    = u * 12f
        val bodyH    = u * 6f
        fillRoundRect(canvas, bodyLeft, bodyTop, bodyW, bodyH, u * 1.5f, dogWhite)
        strokeRoundRect(canvas, bodyLeft, bodyTop, bodyW, bodyH, u*1.5f, dogOutline, u*0.5f)

        fillCircle(canvas, cx - u*2f, bodyTop + u*1.5f, u*1.2f, dogBlack)
        fillCircle(canvas, cx + u*3f, bodyTop + u*3f,   u*1f,   dogBlack)

        val headCX = cx + u * 7f
        val headCY = renderCy - u * 2f + bodyBob
        fillCircle(canvas, headCX, headCY, u * 4f, dogWhite)
        strokeCircle(canvas, headCX, headCY, u * 4f, dogOutline, u*0.5f)

        fillRoundRect(canvas, headCX + u*1f, headCY - u*5f, u*3f, u*3.5f, u, dogBlack)
        fillRoundRect(canvas, headCX - u*2f, headCY - u*5f, u*2f, u*3f,   u, dogWhite)

        fillCircle(canvas, headCX + u*1.5f, headCY - u*0.5f, u*0.8f, dogEye)
        fillCircle(canvas, headCX + u*2f, headCY - u*1f, u*0.3f, 0xFFFFFFFF.toInt())

        fillRoundRect(canvas, headCX - u*2f, headCY + u*0.5f, u*3f, u*2.5f, u, 0xFFF5EDD5.toInt())
        fillCircle(canvas, headCX - u*0.5f, headCY + u*1f, u*0.8f, dogNose)

        if (tongueOut) {
            fillRoundRect(canvas, headCX - u*1.5f, headCY + u*2.5f, u*2f, u*2f, u, dogTongue)
        }

        fillCircle(canvas, headCX - u*1.5f, headCY - u*1.5f, u*1.5f, dogBlack)

        val legTopY = bodyTop + bodyH - u
        val legH    = u * 4f
        val legW    = u * 2f
        val animPx  = legAnim * u * 1.5f

        fillRoundRect(canvas, cx - u*5f,  legTopY - animPx, legW, legH, u*0.5f, dogWhite)
        fillRoundRect(canvas, cx - u*2.5f, legTopY + animPx, legW, legH, u*0.5f, dogWhite)
        fillRoundRect(canvas, cx + u*1f,  legTopY + animPx, legW, legH, u*0.5f, dogWhite)
        fillRoundRect(canvas, cx + u*3.5f, legTopY - animPx, legW, legH, u*0.5f, dogWhite)

        listOf(
            cx - u*5f, cx - u*2.5f, cx + u*1f, cx + u*3.5f
        ).forEachIndexed { _, lx ->
            fillRoundRect(canvas, lx - u*0.3f, legTopY + legH, legW + u*0.6f, u*1.5f, u*0.7f, dogBlack)
        }

        val shadowY = renderCy + u * 9f
        drawShadow(canvas, cx, shadowY, u * 4f, u)

        canvas.restore()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS INTERNOS
    // ─────────────────────────────────────────────────────────────────────────

    private fun fillRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRect(x, y, x + w, y + h, paint)
    }

    private fun strokeRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, stroke: Float) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        canvas.drawRect(x, y, x + w, y + h, paint)
    }

    private fun fillRoundRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), r, r, paint)
    }

    private fun strokeRoundRect(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, color: Int, stroke: Float) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), r, r, paint)
    }

    private fun fillCircle(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private fun strokeCircle(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, stroke: Float) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private fun drawShadow(canvas: Canvas, cx: Float, cy: Float, rx: Float, u: Float) {
        paint.color = 0x33000000
        paint.style = Paint.Style.FILL
        canvas.drawOval(RectF(cx - rx, cy - u, cx + rx, cy + u), paint)
    }

    // -------------------------------------------------------------------------
    // Monsters — silhuetas distintas, paletas quentes, outline escuro
    // -------------------------------------------------------------------------

    fun renderMonster(
        canvas: Canvas,
        x: Float, y: Float,
        appearance: MonsterAppearance,
        frame: Int,
        tileW: Float,
        tileH: Float
    ) {
        val s = tileW / 48f * appearance.size
        val t = frame.toFloat() / 8f
        val bob = (Math.sin(t * Math.PI * 2) * 2 * s).toFloat()
        val cx = x
        
        // Feedback de dano: flash vermelho (MECH-04 / UI-04)
        val actualBodyColor = if (appearance.isHit) Color.RED else appearance.bodyColor
        val actualAppearance = appearance.copy(bodyColor = actualBodyColor)
        
        val yOffset = when (appearance.shapeVariant % 4) {
            3 -> -4 * s
            else -> -2 * s
        }
        val cy = y + bob + yOffset

        paintFill.color = Color.argb(60, 0, 0, 0)
        canvas.drawOval(RectF(cx - 8 * s, y + 10 * s, cx + 8 * s, y + 15 * s), paintFill)

        when (appearance.shapeVariant % 4) {
            0 -> monsterRedondo(canvas, cx, cy, s, t, actualAppearance)
            1 -> monsterEspinhoso(canvas, cx, cy, s, t, actualAppearance)
            2 -> monsterQuadrado(canvas, cx, cy, s, t, actualAppearance)
            3 -> monsterAlto(canvas, cx, cy, s, t, actualAppearance)
        }

        if (appearance.isBoss) {
            paintFill.color = Color.argb(60, 0, 0, 0)
            val auraBase = (14 + Math.sin(t * Math.PI * 3) * 3) * s
            canvas.drawCircle(cx, cy, auraBase.toFloat(), paintFill)
            
            paintContorno.color = Color.argb(150, 200, 0, 0)
            paintContorno.strokeWidth = 3f * s
            val auraRaio = (16 + Math.sin(t * Math.PI * 5) * 2) * s
            canvas.drawCircle(cx, cy, auraRaio.toFloat(), paintContorno)
            paintContorno.strokeWidth = 1.5f
        } else if (appearance.size < 1.0f) {
            paintContorno.color = Color.argb(180, 255, 255, 0)
            paintContorno.strokeWidth = 1.5f * s
            val auraRaio = (10 + Math.sin(t * Math.PI * 6) * 1.5) * s
            canvas.drawCircle(cx, cy, auraRaio.toFloat(), paintContorno)
            
            val numRaios = 4
            for (i in 0 until numRaios) {
                val ang = (i * (360 / numRaios) + t * 360) * Math.PI / 180.0
                val r1 = auraRaio + 1 * s
                val r2 = auraRaio + 4 * s
                canvas.drawLine(
                    (cx + r1 * Math.cos(ang)).toFloat(), (cy + r1 * Math.sin(ang)).toFloat(),
                    (cx + r2 * Math.cos(ang)).toFloat(), (cy + r2 * Math.sin(ang)).toFloat(),
                    paintContorno
                )
            }
            paintContorno.strokeWidth = 1.5f
        }
    }

    private fun monsterRedondo(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, app: MonsterAppearance) {
        paintFill.color = app.bodyColor
        canvas.drawCircle(cx, cy, 9 * s, paintFill)
        paintFill.color = escurecer(app.bodyColor, 0.20f)
        canvas.drawOval(RectF(cx - 9 * s, cy - 9 * s, cx + 9 * s, cy - 2 * s), paintFill)
        paintFill.color = clarear(app.bodyColor, 0.20f)
        canvas.drawOval(RectF(cx - 8 * s, cy - 8 * s, cx - 2 * s, cy - 3 * s), paintFill)
        paintFill.color = app.eyeColor
        canvas.drawCircle(cx - 3.5f * s, cy - 2 * s, 3f * s, paintFill)
        canvas.drawCircle(cx + 3.5f * s, cy - 2 * s, 3f * s, paintFill)
        paintFill.color = Color.WHITE
        canvas.drawCircle(cx - 2.5f * s, cy - 3 * s, 1.2f * s, paintFill)
        canvas.drawCircle(cx + 4.5f * s, cy - 3 * s, 1.2f * s, paintFill)
        paintFill.color = Color.rgb(10, 0, 0)
        canvas.drawCircle(cx - 3 * s, cy - 2 * s, 1.5f * s, paintFill)
        canvas.drawCircle(cx + 4 * s, cy - 2 * s, 1.5f * s, paintFill)
        paintContorno.color = Color.rgb(20, 0, 0)
        paintContorno.strokeWidth = 1.5f * s
        canvas.drawArc(RectF(cx - 4 * s, cy + 1 * s, cx + 4 * s, cy + 6 * s), 0f, 180f, false, paintContorno)
        paintContorno.strokeWidth = 1.5f
        paintContorno.color = escurecer(app.bodyColor, 0.50f)
        paintContorno.strokeWidth = 1.2f
        canvas.drawCircle(cx, cy, 9 * s, paintContorno)
    }

    private fun monsterEspinhoso(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, app: MonsterAppearance) {
        paintFill.color = app.bodyColor
        path.reset()
        for (i in 0 until 8) {
            val ang = (i * 45.0 - 22.5) * Math.PI / 180.0
            val r = if (i % 2 == 0) 11 * s else 6 * s
            val px = cx + (r * Math.cos(ang)).toFloat()
            val py = cy + (r * Math.sin(ang)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, paintFill)
        paintFill.color = escurecer(app.bodyColor, 0.15f)
        canvas.drawCircle(cx, cy, 5 * s, paintFill)
        paintFill.color = clarear(app.bodyColor, 0.20f)
        canvas.drawCircle(cx - 3 * s, cy - 3 * s, 2 * s, paintFill)
        paintFill.color = app.eyeColor
        canvas.drawRect(cx - 4 * s, cy - 3.5f * s, cx - 1 * s, cy + 0.5f * s, paintFill)
        canvas.drawRect(cx + 1 * s, cy - 3.5f * s, cx + 4 * s, cy + 0.5f * s, paintFill)
        paintFill.color = Color.WHITE
        canvas.drawRect(cx - 3.5f * s, cy - 3.5f * s, cx - 2.5f * s, cy - 2.5f * s, paintFill)
        canvas.drawRect(cx + 1.5f * s, cy - 3.5f * s, cx + 2.5f * s, cy - 2.5f * s, paintFill)
        paintContorno.color = escurecer(app.bodyColor, 0.50f)
        paintContorno.strokeWidth = 1.2f
        canvas.drawPath(path, paintContorno)
    }

    private fun monsterQuadrado(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, app: MonsterAppearance) {
        paintFill.color = app.bodyColor
        canvas.drawRect(cx - 9 * s, cy - 9 * s, cx + 9 * s, cy + 9 * s, paintFill)
        paintFill.color = escurecer(app.bodyColor, 0.22f)
        canvas.drawRect(cx - 9 * s, cy - 9 * s, cx + 9 * s, cy - 5 * s, paintFill)
        paintFill.color = clarear(app.bodyColor, 0.18f)
        canvas.drawRect(cx - 8 * s, cy - 8 * s, cx - 3 * s, cy - 5 * s, paintFill)
        paintFill.color = app.eyeColor
        canvas.drawRect(cx - 6.5f * s, cy - 4.5f * s, cx - 1 * s, cy + 1.5f * s, paintFill)
        canvas.drawRect(cx + 1 * s, cy - 4.5f * s, cx + 6.5f * s, cy + 1.5f * s, paintFill)
        paintFill.color = Color.WHITE
        canvas.drawRect(cx - 6 * s, cy - 4.5f * s, cx - 5 * s, cy - 3.5f * s, paintFill)
        canvas.drawRect(cx + 1.5f * s, cy - 4.5f * s, cx + 2.5f * s, cy - 3.5f * s, paintFill)
        paintFill.color = Color.rgb(10, 0, 0)
        canvas.drawRect(cx - 4.5f * s, cy - 3.5f * s, cx - 2.5f * s, cy + 1 * s, paintFill)
        canvas.drawRect(cx + 2.5f * s, cy - 3.5f * s, cx + 4.5f * s, cy + 1 * s, paintFill)
        paintContorno.color = escurecer(app.bodyColor, 0.50f)
        paintContorno.strokeWidth = 1.2f
        canvas.drawRect(cx - 9 * s, cy - 9 * s, cx + 9 * s, cy + 9 * s, paintContorno)
    }

    private fun monsterAlto(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, app: MonsterAppearance) {
        paintFill.color = app.bodyColor
        canvas.drawRect(cx - 5 * s, cy - 13 * s, cx + 5 * s, cy + 8 * s, paintFill)
        paintFill.color = clarear(app.bodyColor, 0.08f)
        canvas.drawRect(cx - 7 * s, cy - 17 * s, cx + 7 * s, cy - 9 * s, paintFill)
        paintFill.color = clarear(app.bodyColor, 0.22f)
        canvas.drawRect(cx - 6 * s, cy - 16 * s, cx - 2 * s, cy - 13 * s, paintFill)
        paintFill.color = app.eyeColor
        canvas.drawCircle(cx - 3 * s, cy - 13.5f * s, 3f * s, paintFill)
        canvas.drawCircle(cx + 3 * s, cy - 13.5f * s, 3f * s, paintFill)
        paintFill.color = Color.WHITE
        canvas.drawCircle(cx - 2 * s, cy - 14.5f * s, 1.2f * s, paintFill)
        canvas.drawCircle(cx + 4 * s, cy - 14.5f * s, 1.2f * s, paintFill)
        paintFill.color = Color.rgb(10, 0, 0)
        canvas.drawCircle(cx - 3 * s, cy - 13.5f * s, 1.5f * s, paintFill)
        canvas.drawCircle(cx + 3 * s, cy - 13.5f * s, 1.5f * s, paintFill)
        paintContorno.color = escurecer(app.bodyColor, 0.50f)
        paintContorno.strokeWidth = 1.2f
        canvas.drawRect(cx - 7 * s, cy - 17 * s, cx + 7 * s, cy + 8 * s, paintContorno)
    }

    private fun escurecer(color: Int, factor: Float): Int {
        val f = (factor * 255).toInt()
        return Color.rgb(
            (Color.red(color) - f).coerceAtLeast(0),
            (Color.green(color) - f).coerceAtLeast(0),
            (Color.blue(color) - f).coerceAtLeast(0)
        )
    }

    private fun clarear(color: Int, factor: Float): Int {
        val f = (factor * 255).toInt()
        return Color.rgb(
            (Color.red(color) + f).coerceAtMost(255),
            (Color.green(color) + f).coerceAtMost(255),
            (Color.blue(color) + f).coerceAtMost(255)
        )
    }

    fun renderBanana(canvas: Canvas, x: Float, y: Float, frame: Int, tileW: Float) {
        val s = (tileW / 48f) * 1.8f
        val t = frame.toFloat() / 15f
        val bob = (Math.sin(t * Math.PI * 2) * 5 * s).toFloat()
        val cx = x
        val cy = y + bob - 5 * s

        paintFill.color = Color.argb(40, 0, 0, 0)
        canvas.drawOval(RectF(cx - 10 * s, y + 10 * s, cx + 10 * s, y + 16 * s), paintFill)

        paintFill.color = Color.argb(50, 255, 255, 100)
        canvas.drawCircle(cx, cy, 14 * s + (Math.sin(t * Math.PI * 4) * 2 * s).toFloat(), paintFill)
        paintFill.color = Color.argb(80, 255, 255, 0)
        canvas.drawCircle(cx, cy, 10 * s, paintFill)

        paintFill.color = Color.rgb(255, 240, 0)
        path.reset()
        path.moveTo(cx - 10 * s, cy - 6 * s)
        path.quadTo(cx, cy + 12 * s, cx + 10 * s, cy - 6 * s)
        path.quadTo(cx, cy + 4 * s, cx - 10 * s, cy - 6 * s)
        canvas.drawPath(path, paintFill)

        paintFill.color = Color.rgb(80, 50, 10)
        canvas.drawRect(cx - 12 * s, cy - 8 * s, cx - 9 * s, cy - 4 * s, paintFill)
        
        paintContorno.color = Color.rgb(120, 80, 0)
        paintContorno.strokeWidth = 2f * s
        canvas.drawPath(path, paintContorno)
        paintContorno.strokeWidth = 1.5f
    }

    fun renderWaterProjectile(canvas: Canvas, x: Float, y: Float, tileW: Float, direction: com.ericleber.joguinho.core.Direction) {
        val s = tileW / 40f
        
        canvas.save()
        val angle = when (direction) {
            com.ericleber.joguinho.core.Direction.NORTH -> 270f
            com.ericleber.joguinho.core.Direction.SOUTH -> 90f
            com.ericleber.joguinho.core.Direction.EAST -> 0f
            com.ericleber.joguinho.core.Direction.WEST -> 180f
            com.ericleber.joguinho.core.Direction.NORTH_EAST -> 315f
            com.ericleber.joguinho.core.Direction.NORTH_WEST -> 225f
            com.ericleber.joguinho.core.Direction.SOUTH_EAST -> 45f
            com.ericleber.joguinho.core.Direction.SOUTH_WEST -> 135f
        }
        canvas.rotate(angle, x, y)
        
        // Jato de alta pressão (Wap style): Longo e fino com núcleo branco
        paintFill.color = Color.argb(150, 100, 200, 255) // Azul água
        canvas.drawRoundRect(RectF(x - 8 * s, y - 1.5f * s, x + 8 * s, y + 1.5f * s), 1f * s, 1f * s, paintFill)
        
        paintFill.color = Color.WHITE // Núcleo de pressão
        canvas.drawRoundRect(RectF(x - 6 * s, y - 0.5f * s, x + 6 * s, y + 0.5f * s), 0.5f * s, 0.5f * s, paintFill)
        
        canvas.restore()
    }

    fun renderWaterSplash(canvas: Canvas, x: Float, y: Float, tileW: Float, progress: Float) {
        val s = tileW / 40f
        val alpha = (255 * (1f - progress)).toInt()
        val radius = 5 * s + progress * 15 * s
        
        paintFill.color = Color.argb(alpha, 150, 220, 255)
        
        // Desenha várias gotas saindo do centro
        val numDrops = 6
        for (i in 0 until numDrops) {
            val ang = (i * (360 / numDrops)) * Math.PI / 180.0
            val dx = (Math.cos(ang) * radius).toFloat()
            val dy = (Math.sin(ang) * radius).toFloat()
            val dropSize = 3 * s * (1f - progress)
            canvas.drawCircle(x + dx, y + dy, dropSize, paintFill)
        }
        
        // Centro do splash
        paintFill.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawCircle(x, y, 4 * s * (1f - progress), paintFill)
    }

    fun renderWaterMuzzle(canvas: Canvas, x: Float, y: Float, tileW: Float, progress: Float, angle: Float) {
        val s = tileW / 40f
        val alpha = (200 * (1f - progress)).toInt()
        
        canvas.save()
        canvas.rotate(angle, x, y)
        
        paintFill.color = Color.argb(alpha, 200, 240, 255)
        val w = 15 * s * progress
        val h = 8 * s * progress
        canvas.drawOval(RectF(x, y - h/2, x + w, y + h/2), paintFill)
        
        canvas.restore()
    }
}
