package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Direções de movimento do Hero (8 direções cardinais/diagonais).
 */
enum class HeroDirection { N, NE, E, SE, S, SW, W, NW }

/**
 * Estado de animação do Hero.
 */
enum class HeroAnimState { WALK, IDLE, SLOWDOWN }

/**
 * Aparência procedural de um Monster.
 * @param bodyColor cor principal do corpo
 * @param eyeColor cor dos olhos
 * @param size escala do sprite (0.7f a 1.3f)
 * @param shapeVariant variante de forma do corpo (0-3)
 * @param animVariant variante de animação (0-2)
 */
data class MonsterAppearance(
    val bodyColor: Int,
    val eyeColor: Int,
    val size: Float,
    val shapeVariant: Int,
    val animVariant: Int
)

/**
 * Renderiza sprites do Hero, Spike e Monsters via Canvas.
 *
 * Todos os sprites são gerados programaticamente — sem arquivos de imagem externos.
 * Usa Paint.filterBitmap = false e isAntiAlias = false para estética pixel art.
 *
 * Requisitos: 8.5, 17.1, 17.2, 2.8
 */
class CharacterRenderer {

    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }

    private val path = Path()

    // -------------------------------------------------------------------------
    // Hero
    // -------------------------------------------------------------------------

    /**
     * Desenha o Hero no canvas.
     * 8 direções, 8 frames de caminhada, 4 frames idle.
     *
     * @param canvas canvas de destino
     * @param x posição X na tela
     * @param y posição Y na tela
     * @param direction direção do Hero
     * @param animState estado de animação
     * @param frame frame atual da animação
     * @param tileW largura do tile (para escala)
     * @param tileH altura do tile (para escala)
     */
    fun renderHero(
        canvas: Canvas,
        x: Float,
        y: Float,
        direction: HeroDirection,
        animState: HeroAnimState,
        frame: Int,
        tileW: Float,
        tileH: Float
    ) {
        val scale = tileW / 64f
        val totalFrames = if (animState == HeroAnimState.IDLE) 4 else 8
        val t = frame.toFloat() / totalFrames
        val bobOffset = when (animState) {
            HeroAnimState.WALK -> (Math.sin(t * Math.PI * 2) * 2 * scale).toFloat()
            HeroAnimState.SLOWDOWN -> (Math.sin(t * Math.PI) * 1 * scale).toFloat()
            else -> 0f
        }

        val bodyColor = when (animState) {
            HeroAnimState.SLOWDOWN -> Color.rgb(100, 100, 200)
            else -> Color.rgb(60, 100, 180)
        }

        val s = scale
        val cx = x
        val cy = y + bobOffset

        // Sombra
        paint.color = Color.argb(60, 0, 0, 0)
        canvas.drawOval(
            android.graphics.RectF(cx - 8 * s, cy + 22 * s, cx + 8 * s, cy + 26 * s),
            paint
        )

        // Pernas com animação de caminhada
        paint.color = Color.rgb(40, 60, 120)
        val legAnim = when (animState) {
            HeroAnimState.WALK -> (Math.sin(t * Math.PI * 2) * 3 * s).toFloat()
            else -> 0f
        }
        canvas.drawRect(cx - 5 * s, cy + 14 * s, cx - 1 * s, cy + 22 * s + legAnim, paint)
        canvas.drawRect(cx + 1 * s, cy + 14 * s, cx + 5 * s, cy + 22 * s - legAnim, paint)

        // Corpo
        paint.color = bodyColor
        canvas.drawRect(cx - 6 * s, cy + 4 * s, cx + 6 * s, cy + 15 * s, paint)

        // Braços — direção influencia posição
        val armAngle = when (direction) {
            HeroDirection.N, HeroDirection.NE, HeroDirection.NW -> -2 * s
            HeroDirection.S, HeroDirection.SE, HeroDirection.SW -> 2 * s
            else -> 0f
        }
        paint.color = bodyColor
        canvas.drawRect(cx - 9 * s, cy + 5 * s + armAngle, cx - 6 * s, cy + 13 * s, paint)
        canvas.drawRect(cx + 6 * s, cy + 5 * s - armAngle, cx + 9 * s, cy + 13 * s, paint)

        // Cabeça
        paint.color = Color.rgb(220, 180, 140)
        canvas.drawRect(cx - 5 * s, cy - 4 * s, cx + 5 * s, cy + 4 * s, paint)

        // Chapéu de aventureiro
        paint.color = Color.rgb(100, 70, 30)
        canvas.drawRect(cx - 7 * s, cy - 6 * s, cx + 7 * s, cy - 3 * s, paint)
        canvas.drawRect(cx - 4 * s, cy - 10 * s, cx + 4 * s, cy - 6 * s, paint)

        // Olhos — expressivos por estado
        val eyeColor = when (animState) {
            HeroAnimState.SLOWDOWN -> Color.rgb(150, 150, 255)
            else -> Color.rgb(30, 30, 30)
        }
        paint.color = eyeColor
        canvas.drawRect(cx - 3 * s, cy - 2 * s, cx - 1 * s, cy, paint)
        canvas.drawRect(cx + 1 * s, cy - 2 * s, cx + 3 * s, cy, paint)
    }

    /**
     * Pré-renderiza frames do Hero para cache.
     * @return lista de Bitmaps com os frames da animação
     */
    fun createHeroFrames(
        direction: HeroDirection,
        animState: HeroAnimState,
        tileW: Int,
        tileH: Int
    ): List<Bitmap> {
        val frameCount = if (animState == HeroAnimState.IDLE) 4 else 8
        return List(frameCount) { frameIndex ->
            val bitmap = Bitmap.createBitmap(tileW, tileH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            renderHero(
                canvas,
                tileW / 2f,
                tileH / 2f,
                direction,
                animState,
                frameIndex,
                tileW.toFloat(),
                tileH.toFloat()
            )
            bitmap
        }
    }

    // -------------------------------------------------------------------------
    // Spike
    // -------------------------------------------------------------------------

    /**
     * Desenha o Spike (viralata branco com manchas pretas) no canvas.
     * 12+ frames por estado comportamental.
     *
     * Estados: SEGUINDO, FAREJANDO, ALERTANDO, INCENTIVANDO,
     *          SLOWDOWN_PROPRIO, ENTUSIASMADO, CHAMANDO, CELEBRANDO
     *
     * @param spikeState nome do estado comportamental
     * @param frame frame atual (0-11+)
     */
    fun renderSpike(
        canvas: Canvas,
        x: Float,
        y: Float,
        spikeState: String,
        frame: Int,
        tileW: Float,
        tileH: Float
    ) {
        val scale = tileW / 64f
        val totalFrames = 12
        val t = frame.toFloat() / totalFrames
        val s = scale

        val bobOffset = (Math.sin(t * Math.PI * 2) * 1.5 * s).toFloat()
        val cx = x
        val cy = y + bobOffset

        // Sombra
        paint.color = Color.argb(50, 0, 0, 0)
        canvas.drawOval(
            android.graphics.RectF(cx - 7 * s, cy + 10 * s, cx + 7 * s, cy + 13 * s),
            paint
        )

        // Rabo — animação varia por estado
        val tailWag = when (spikeState) {
            "CELEBRANDO", "ENTUSIASMADO" -> (Math.sin(t * Math.PI * 4) * 4 * s).toFloat()
            "SEGUINDO" -> (Math.sin(t * Math.PI * 2) * 2 * s).toFloat()
            else -> 0f
        }
        paint.color = Color.WHITE
        canvas.drawRect(cx + 6 * s, cy + 2 * s + tailWag, cx + 11 * s, cy + 5 * s, paint)
        // Mancha preta no rabo
        paint.color = Color.BLACK
        canvas.drawRect(cx + 8 * s, cy + 2 * s + tailWag, cx + 10 * s, cy + 4 * s, paint)

        // Corpo — branco predominante
        paint.color = Color.WHITE
        canvas.drawRect(cx - 6 * s, cy + 2 * s, cx + 7 * s, cy + 10 * s, paint)

        // Manchas pretas no dorso
        paint.color = Color.BLACK
        canvas.drawRect(cx - 4 * s, cy + 2 * s, cx - 1 * s, cy + 5 * s, paint)
        canvas.drawRect(cx + 2 * s, cy + 4 * s, cx + 5 * s, cy + 7 * s, paint)

        // Patas com animação
        val pawAnim = (Math.sin(t * Math.PI * 2) * 1.5 * s).toFloat()
        paint.color = Color.BLACK
        canvas.drawRect(cx - 5 * s, cy + 9 * s, cx - 2 * s, cy + 13 * s + pawAnim, paint)
        canvas.drawRect(cx + 2 * s, cy + 9 * s, cx + 5 * s, cy + 13 * s - pawAnim, paint)

        // Cabeça
        paint.color = Color.WHITE
        canvas.drawRect(cx - 5 * s, cy - 5 * s, cx + 5 * s, cy + 3 * s, paint)

        // Mancha preta na cabeça
        paint.color = Color.BLACK
        canvas.drawRect(cx - 3 * s, cy - 5 * s, cx, cy - 2 * s, paint)

        // Orelhas — posição varia por estado
        val earOffset = when (spikeState) {
            "ALERTANDO", "ENTUSIASMADO" -> -2 * s  // orelhas erguidas
            "INCENTIVANDO", "SLOWDOWN_PROPRIO" -> 2 * s  // orelhas abaixadas
            else -> 0f
        }
        paint.color = Color.WHITE
        canvas.drawRect(cx - 6 * s, cy - 8 * s + earOffset, cx - 3 * s, cy - 4 * s, paint)
        canvas.drawRect(cx + 3 * s, cy - 8 * s + earOffset, cx + 6 * s, cy - 4 * s, paint)

        // Olhos — expressivos por estado
        val eyeColor = when (spikeState) {
            "ALERTANDO" -> Color.rgb(255, 80, 80)
            "INCENTIVANDO" -> Color.rgb(255, 200, 50)
            "ENTUSIASMADO" -> Color.rgb(100, 220, 100)
            "SLOWDOWN_PROPRIO" -> Color.rgb(150, 150, 255)
            "CELEBRANDO" -> Color.rgb(255, 220, 100)
            else -> Color.rgb(50, 50, 50)
        }
        paint.color = eyeColor
        canvas.drawRect(cx - 3 * s, cy - 3 * s, cx - 1 * s, cy - 1 * s, paint)
        canvas.drawRect(cx + 1 * s, cy - 3 * s, cx + 3 * s, cy - 1 * s, paint)

        // Nariz
        paint.color = Color.rgb(255, 150, 150)
        canvas.drawRect(cx - 1 * s, cy, cx + 1 * s, cy + 1 * s, paint)

        // Efeito especial por estado
        when (spikeState) {
            "FAREJANDO" -> {
                // Partículas de farejar
                paint.color = Color.argb(120, 200, 200, 200)
                val sniffOffset = (Math.sin(t * Math.PI * 4) * 3 * s).toFloat()
                canvas.drawCircle(cx + 5 * s, cy - 2 * s + sniffOffset, 1.5f * s, paint)
                canvas.drawCircle(cx + 7 * s, cy - 4 * s + sniffOffset, 1f * s, paint)
            }
            "CHAMANDO" -> {
                // Indicador de chamada (ondas)
                paint.color = Color.argb(150, 255, 255, 100)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f * s
                val waveRadius = (3 + t * 4) * s
                canvas.drawCircle(cx, cy - 8 * s, waveRadius, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    /**
     * Pré-renderiza frames do Spike para cache.
     */
    fun createSpikeFrames(state: String, tileW: Int, tileH: Int): List<Bitmap> {
        return List(12) { frameIndex ->
            val bitmap = Bitmap.createBitmap(tileW, tileH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            renderSpike(
                canvas,
                tileW / 2f,
                tileH / 2f,
                state,
                frameIndex,
                tileW.toFloat(),
                tileH.toFloat()
            )
            bitmap
        }
    }

    // -------------------------------------------------------------------------
    // Monster
    // -------------------------------------------------------------------------

    /**
     * Desenha um Monster com aparência procedural.
     * @param appearance parâmetros visuais do monster
     * @param frame frame atual da animação
     */
    fun renderMonster(
        canvas: Canvas,
        x: Float,
        y: Float,
        appearance: MonsterAppearance,
        frame: Int,
        tileW: Float,
        tileH: Float
    ) {
        val scale = tileW / 64f * appearance.size
        val totalFrames = 8
        val t = frame.toFloat() / totalFrames
        val s = scale

        val bobOffset = (Math.sin(t * Math.PI * 2) * 2 * s).toFloat()
        val cx = x
        val cy = y + bobOffset

        // Sombra
        paint.color = Color.argb(60, 0, 0, 0)
        canvas.drawOval(
            android.graphics.RectF(cx - 8 * s, cy + 12 * s, cx + 8 * s, cy + 16 * s),
            paint
        )

        when (appearance.shapeVariant % 4) {
            0 -> renderMonsterRound(canvas, cx, cy, s, t, appearance)
            1 -> renderMonsterSpiky(canvas, cx, cy, s, t, appearance)
            2 -> renderMonsterSquare(canvas, cx, cy, s, t, appearance)
            3 -> renderMonsterTall(canvas, cx, cy, s, t, appearance)
        }
    }

    private fun renderMonsterRound(
        canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, app: MonsterAppearance
    ) {
        // Corpo redondo
        paint.color = app.bodyColor
        canvas.drawCircle(cx, cy, 8 * s, paint)

        // Olhos
        paint.color = app.eyeColor
        canvas.drawCircle(cx - 3 * s, cy - 2 * s, 2 * s, paint)
        canvas.drawCircle(cx + 3 * s, cy - 2 * s, 2 * s, paint)

        // Boca ameaçadora
        paint.color = Color.rgb(30, 0, 0)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * s
        canvas.drawArc(
            android.graphics.RectF(cx - 4 * s, cy + 1 * s, cx + 4 * s, cy + 6 * s),
            0f, 180f, false, paint
        )
        paint.style = Paint.Style.FILL
    }

    private fun renderMonsterSpiky(
        canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, app: MonsterAppearance
    ) {
        // Corpo com espinhos
        paint.color = app.bodyColor
        path.reset()
        for (i in 0 until 8) {
            val angle = (i * 45.0 - 22.5) * Math.PI / 180.0
            val outerR = if (i % 2 == 0) 10 * s else 6 * s
            val px = cx + (outerR * Math.cos(angle)).toFloat()
            val py = cy + (outerR * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, paint)

        // Olhos
        paint.color = app.eyeColor
        canvas.drawRect(cx - 4 * s, cy - 3 * s, cx - 1 * s, cy, paint)
        canvas.drawRect(cx + 1 * s, cy - 3 * s, cx + 4 * s, cy, paint)
    }

    private fun renderMonsterSquare(
        canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, app: MonsterAppearance
    ) {
        // Corpo quadrado
        paint.color = app.bodyColor
        canvas.drawRect(cx - 8 * s, cy - 8 * s, cx + 8 * s, cy + 8 * s, paint)

        // Detalhes
        paint.color = darken(app.bodyColor, 0.2f)
        canvas.drawRect(cx - 8 * s, cy - 8 * s, cx + 8 * s, cy - 6 * s, paint)

        // Olhos quadrados
        paint.color = app.eyeColor
        canvas.drawRect(cx - 5 * s, cy - 4 * s, cx - 1 * s, cy, paint)
        canvas.drawRect(cx + 1 * s, cy - 4 * s, cx + 5 * s, cy, paint)
    }

    private fun renderMonsterTall(
        canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, app: MonsterAppearance
    ) {
        // Corpo alto e fino
        paint.color = app.bodyColor
        canvas.drawRect(cx - 5 * s, cy - 12 * s, cx + 5 * s, cy + 8 * s, paint)

        // Cabeça maior
        canvas.drawRect(cx - 7 * s, cy - 14 * s, cx + 7 * s, cy - 8 * s, paint)

        // Olhos
        paint.color = app.eyeColor
        canvas.drawCircle(cx - 3 * s, cy - 12 * s, 2 * s, paint)
        canvas.drawCircle(cx + 3 * s, cy - 12 * s, 2 * s, paint)
    }

    // -------------------------------------------------------------------------
    // Utilitários de cor
    // -------------------------------------------------------------------------

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
