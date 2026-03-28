package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

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
 */
data class MonsterAppearance(
    val bodyColor: Int,
    val eyeColor: Int,
    val size: Float,
    val shapeVariant: Int,
    val animVariant: Int,
    val isBoss: Boolean = false
)

/**
 * Renderiza sprites do Hero, Spike e Monsters via Canvas.
 *
 * Estética pixel art Stardew Valley cave:
 * - Outline de 1px escuro em todos os personagens
 * - Olhos grandes e expressivos
 * - Sombra elíptica no chão (oval semi-transparente, alpha 60)
 * - Sprites proporcionais ao tile (escala relativa a tileW)
 *
 * Regras: Paint.isAntiAlias = false, Paint.isFilterBitmap = false.
 * Requisitos: 8.5, 17.1, 17.2, 2.8, 25.6
 */
class CharacterRenderer {

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

    // -------------------------------------------------------------------------
    // Hero — aventureiro com chapéu, estilo Stardew Valley
    // -------------------------------------------------------------------------

    /**
     * Desenha o Hero com outline 1px, olhos grandes expressivos e sombra no chão.
     * Bob vertical animado durante caminhada.
     * Escala: s = tileW / 48f → personagem ocupa ~2/3 do tile.
     */
    fun renderHero(
        canvas: Canvas,
        x: Float, y: Float,
        direction: HeroDirection,
        animState: HeroAnimState,
        frame: Int,
        tileW: Float,
        tileH: Float,
        isSlowedDown: Boolean = false,
        hasSpeedBuff: Boolean = false
    ) {
        val s = tileW / 48f
        val totalFrames = if (animState == HeroAnimState.IDLE) 4 else 8
        val t = frame.toFloat() / totalFrames

        val bob = when (animState) {
            HeroAnimState.WALK     -> (Math.sin(t * Math.PI * 2) * 2.5 * s).toFloat()
            HeroAnimState.SLOWDOWN -> (Math.sin(t * Math.PI) * 1.5 * s).toFloat()
            else -> 0f
        }

        // Efeito de piscar vermelho durante a lentidão ou azul durante o buff
        val isFlashingRed = isSlowedDown && (frame % 2 == 0)
        val isFlashingBlue = hasSpeedBuff && (frame % 2 == 0)
        
        val corCorpo = when {
            isFlashingRed -> Color.rgb(255, 50, 50) // Vermelho vibrante
            isFlashingBlue -> Color.rgb(50, 150, 255) // Azul vibrante
            isSlowedDown -> Color.rgb(80, 80, 180)
            else -> Color.rgb(70, 110, 200)
        }
        val corCalca = when {
            isFlashingRed -> Color.rgb(180, 30, 30) // Vermelho escuro
            isFlashingBlue -> Color.rgb(30, 80, 180) // Azul escuro
            isSlowedDown -> Color.rgb(50, 50, 130)
            else -> Color.rgb(45, 70, 140)
        }

        val cx = x
        val cy = y + bob

        // Sombra elíptica no chão (alpha 60 — discreta)
        paintFill.color = Color.argb(60, 0, 0, 0)
        canvas.drawOval(RectF(cx - 8 * s, cy + 20 * s, cx + 8 * s, cy + 25 * s), paintFill)

        // --- Pernas animadas ---
        val legSwing = when (animState) {
            HeroAnimState.WALK -> (Math.sin(t * Math.PI * 2) * 3.5 * s).toFloat()
            else -> 0f
        }
        // Outline das pernas
        paintContorno.color = escurecer(corCalca, 0.45f)
        paintContorno.strokeWidth = 1f
        canvas.drawRect(cx - 5 * s, cy + 13 * s, cx - 1 * s, cy + 22 * s + legSwing, paintContorno)
        canvas.drawRect(cx + 1 * s, cy + 13 * s, cx + 5 * s, cy + 22 * s - legSwing, paintContorno)
        // Pernas
        paintFill.color = corCalca
        canvas.drawRect(cx - 5 * s, cy + 13 * s, cx - 1 * s, cy + 22 * s + legSwing, paintFill)
        canvas.drawRect(cx + 1 * s, cy + 13 * s, cx + 5 * s, cy + 22 * s - legSwing, paintFill)
        // Botas
        val corBota = Color.rgb(80, 50, 20)
        paintFill.color = corBota
        canvas.drawRect(cx - 5.5f * s, cy + 19 * s + legSwing, cx - 0.5f * s, cy + 24 * s + legSwing, paintFill)
        canvas.drawRect(cx + 0.5f * s, cy + 19 * s - legSwing, cx + 5.5f * s, cy + 24 * s - legSwing, paintFill)
        // Outline botas
        paintContorno.color = escurecer(corBota, 0.40f)
        canvas.drawRect(cx - 5.5f * s, cy + 19 * s + legSwing, cx - 0.5f * s, cy + 24 * s + legSwing, paintContorno)
        canvas.drawRect(cx + 0.5f * s, cy + 19 * s - legSwing, cx + 5.5f * s, cy + 24 * s - legSwing, paintContorno)

        // --- Corpo (casaco de aventureiro) ---
        paintFill.color = corCorpo
        canvas.drawRect(cx - 6 * s, cy + 4 * s, cx + 6 * s, cy + 14 * s, paintFill)
        // Detalhe central do casaco
        paintFill.color = escurecer(corCorpo, 0.15f)
        canvas.drawRect(cx - 1 * s, cy + 4 * s, cx + 1 * s, cy + 14 * s, paintFill)
        // Botões
        paintFill.color = clarear(corCorpo, 0.30f)
        canvas.drawRect(cx - 0.5f * s, cy + 6 * s, cx + 0.5f * s, cy + 7 * s, paintFill)
        canvas.drawRect(cx - 0.5f * s, cy + 9 * s, cx + 0.5f * s, cy + 10 * s, paintFill)

        // --- Braços ---
        val armSwing = when (direction) {
            HeroDirection.N, HeroDirection.NE, HeroDirection.NW -> -2 * s
            HeroDirection.S, HeroDirection.SE, HeroDirection.SW -> 2 * s
            else -> 0f
        }
        paintFill.color = corCorpo
        canvas.drawRect(cx - 9 * s, cy + 5 * s + armSwing, cx - 6 * s, cy + 14 * s, paintFill)
        canvas.drawRect(cx + 6 * s, cy + 5 * s - armSwing, cx + 9 * s, cy + 14 * s, paintFill)
        // Outline do corpo + braços
        paintContorno.color = escurecer(corCorpo, 0.45f)
        paintContorno.strokeWidth = 1.2f
        canvas.drawRect(cx - 9 * s, cy + 4 * s, cx + 9 * s, cy + 14 * s, paintContorno)

        // --- Cabeça ---
        val corPele = Color.rgb(225, 185, 145)
        paintFill.color = corPele
        canvas.drawRect(cx - 5 * s, cy - 4 * s, cx + 5 * s, cy + 5 * s, paintFill)
        // Outline da cabeça
        paintContorno.color = escurecer(corPele, 0.40f)
        paintContorno.strokeWidth = 1f
        canvas.drawRect(cx - 5 * s, cy - 4 * s, cx + 5 * s, cy + 5 * s, paintContorno)

        // --- Chapéu ---
        val corAba = Color.rgb(110, 75, 30)
        val corCopa = Color.rgb(90, 60, 20)
        // Aba larga
        paintFill.color = corAba
        canvas.drawRect(cx - 8 * s, cy - 6 * s, cx + 8 * s, cy - 3 * s, paintFill)
        // Copa
        paintFill.color = corCopa
        canvas.drawRect(cx - 4 * s, cy - 12 * s, cx + 4 * s, cy - 6 * s, paintFill)
        // Fita do chapéu
        paintFill.color = Color.rgb(180, 140, 60)
        canvas.drawRect(cx - 4 * s, cy - 7 * s, cx + 4 * s, cy - 6 * s, paintFill)
        // Highlight no chapéu (canto superior esquerdo — estilo Stardew)
        paintFill.color = clarear(corCopa, 0.25f)
        canvas.drawRect(cx - 3.5f * s, cy - 11.5f * s, cx - 1 * s, cy - 9 * s, paintFill)
        // Outline do chapéu
        paintContorno.color = escurecer(corCopa, 0.50f)
        paintContorno.strokeWidth = 1f
        canvas.drawRect(cx - 8 * s, cy - 12 * s, cx + 8 * s, cy - 3 * s, paintContorno)

        // --- Olhos grandes expressivos (estilo Stardew) ---
        val corOlho = when (animState) {
            HeroAnimState.SLOWDOWN -> Color.rgb(140, 140, 255)
            else -> Color.rgb(20, 20, 20)
        }
        // Olho esquerdo — maior que antes
        paintFill.color = corOlho
        canvas.drawRect(cx - 4 * s, cy - 3 * s, cx - 1 * s, cy + 1 * s, paintFill)
        // Olho direito
        canvas.drawRect(cx + 1 * s, cy - 3 * s, cx + 4 * s, cy + 1 * s, paintFill)
        // Brilho nos olhos (pixel branco no canto superior esquerdo)
        paintFill.color = Color.WHITE
        canvas.drawRect(cx - 3.5f * s, cy - 3 * s, cx - 2.5f * s, cy - 2 * s, paintFill)
        canvas.drawRect(cx + 1.5f * s, cy - 3 * s, cx + 2.5f * s, cy - 2 * s, paintFill)
        // Pupila (ponto escuro menor)
        paintFill.color = Color.rgb(5, 5, 5)
        canvas.drawRect(cx - 2.5f * s, cy - 2 * s, cx - 1.5f * s, cy + 0.5f * s, paintFill)
        canvas.drawRect(cx + 1.5f * s, cy - 2 * s, cx + 2.5f * s, cy + 0.5f * s, paintFill)
    }

    fun createHeroFrames(direction: HeroDirection, animState: HeroAnimState, tileW: Int, tileH: Int): List<Bitmap> {
        val frameCount = if (animState == HeroAnimState.IDLE) 4 else 8
        return List(frameCount) { i ->
            val bmp = Bitmap.createBitmap(tileW, tileH * 2, Bitmap.Config.ARGB_8888)
            renderHero(Canvas(bmp), tileW / 2f, tileH / 2f, direction, animState, i, tileW.toFloat(), tileH.toFloat())
            bmp
        }
    }

    // -------------------------------------------------------------------------
    // Spike — viralata branco com manchas pretas, fofo e expressivo
    // -------------------------------------------------------------------------

    /**
     * Desenha o Spike com outline 1px, olhos grandes expressivos e sombra no chão.
     * Rabo animado por estado comportamental.
     * Orelhas erguidas/abaixadas por estado.
     */
    fun renderSpike(
        canvas: Canvas,
        x: Float, y: Float,
        spikeState: String,
        frame: Int,
        tileW: Float,
        tileH: Float
    ) {
        val s = tileW / 52f
        val t = frame.toFloat() / 12f
        val bob = (Math.sin(t * Math.PI * 2) * 1.8 * s).toFloat()
        val cx = x
        val cy = y + bob

        // Sombra elíptica no chão
        paintFill.color = Color.argb(60, 0, 0, 0)
        canvas.drawOval(RectF(cx - 8 * s, cy + 12 * s, cx + 8 * s, cy + 16 * s), paintFill)

        // --- Rabo animado ---
        val rabAnim = when (spikeState) {
            "CELEBRANDO", "ENTUSIASMADO" -> (Math.sin(t * Math.PI * 5) * 5 * s).toFloat()
            "SEGUINDO"                   -> (Math.sin(t * Math.PI * 2) * 2.5 * s).toFloat()
            else -> 0f
        }
        paintFill.color = Color.rgb(245, 245, 245)
        canvas.drawRect(cx + 6 * s, cy + 1 * s + rabAnim, cx + 12 * s, cy + 4 * s + rabAnim, paintFill)
        paintFill.color = Color.rgb(30, 30, 30)
        canvas.drawRect(cx + 8 * s, cy + 1 * s + rabAnim, cx + 11 * s, cy + 3 * s + rabAnim, paintFill)
        // Outline do rabo
        paintContorno.color = Color.rgb(20, 20, 20)
        paintContorno.strokeWidth = 1f
        canvas.drawRect(cx + 6 * s, cy + 1 * s + rabAnim, cx + 12 * s, cy + 4 * s + rabAnim, paintContorno)

        // --- Corpo branco ---
        val corCorpo = Color.rgb(245, 245, 245)
        paintFill.color = corCorpo
        canvas.drawRect(cx - 7 * s, cy + 1 * s, cx + 7 * s, cy + 11 * s, paintFill)
        // Manchas pretas no dorso
        paintFill.color = Color.rgb(30, 30, 30)
        canvas.drawRect(cx - 5 * s, cy + 1 * s, cx - 1 * s, cy + 5 * s, paintFill)
        canvas.drawRect(cx + 2 * s, cy + 4 * s, cx + 5 * s, cy + 7 * s, paintFill)
        paintFill.color = Color.rgb(50, 50, 50)
        canvas.drawRect(cx - 2 * s, cy + 7 * s, cx + 1 * s, cy + 9 * s, paintFill)
        // Outline do corpo
        paintContorno.color = Color.rgb(20, 20, 20)
        paintContorno.strokeWidth = 1.2f
        canvas.drawRect(cx - 7 * s, cy + 1 * s, cx + 7 * s, cy + 11 * s, paintContorno)

        // --- Patas animadas ---
        val pataAnim = (Math.sin(t * Math.PI * 2) * 2 * s).toFloat()
        // Patas traseiras (pretas)
        paintFill.color = Color.rgb(30, 30, 30)
        canvas.drawRect(cx - 6 * s, cy + 10 * s, cx - 3 * s, cy + 15 * s + pataAnim, paintFill)
        canvas.drawRect(cx + 3 * s, cy + 10 * s, cx + 6 * s, cy + 15 * s - pataAnim, paintFill)
        // Patas dianteiras (brancas)
        paintFill.color = Color.rgb(235, 235, 235)
        canvas.drawRect(cx - 5 * s, cy + 9 * s, cx - 2 * s, cy + 14 * s - pataAnim, paintFill)
        canvas.drawRect(cx + 2 * s, cy + 9 * s, cx + 5 * s, cy + 14 * s + pataAnim, paintFill)
        // Outline patas
        paintContorno.color = Color.rgb(20, 20, 20)
        paintContorno.strokeWidth = 1f
        canvas.drawRect(cx - 6 * s, cy + 9 * s, cx - 2 * s, cy + 15 * s, paintContorno)
        canvas.drawRect(cx + 2 * s, cy + 9 * s, cx + 6 * s, cy + 15 * s, paintContorno)

        // --- Cabeça ---
        paintFill.color = corCorpo
        canvas.drawRect(cx - 6 * s, cy - 6 * s, cx + 6 * s, cy + 3 * s, paintFill)
        // Mancha preta na cabeça (lado esquerdo)
        paintFill.color = Color.rgb(30, 30, 30)
        canvas.drawRect(cx - 5 * s, cy - 6 * s, cx - 1 * s, cy - 2 * s, paintFill)
        // Outline da cabeça
        paintContorno.color = Color.rgb(20, 20, 20)
        paintContorno.strokeWidth = 1.2f
        canvas.drawRect(cx - 6 * s, cy - 6 * s, cx + 6 * s, cy + 3 * s, paintContorno)

        // --- Orelhas ---
        val orelhaOffset = when (spikeState) {
            "ALERTANDO", "ENTUSIASMADO" -> -2.5f * s
            "INCENTIVANDO", "SLOWDOWN_PROPRIO" -> 2f * s
            else -> 0f
        }
        val corOrelha = Color.rgb(220, 210, 200)
        paintFill.color = corOrelha
        canvas.drawRect(cx - 7 * s, cy - 10 * s + orelhaOffset, cx - 3 * s, cy - 5 * s, paintFill)
        canvas.drawRect(cx + 3 * s, cy - 10 * s + orelhaOffset, cx + 7 * s, cy - 5 * s, paintFill)
        // Interior rosa
        paintFill.color = Color.rgb(255, 180, 180)
        canvas.drawRect(cx - 6 * s, cy - 9 * s + orelhaOffset, cx - 4 * s, cy - 6 * s, paintFill)
        canvas.drawRect(cx + 4 * s, cy - 9 * s + orelhaOffset, cx + 6 * s, cy - 6 * s, paintFill)
        // Outline orelhas
        paintContorno.color = escurecer(corOrelha, 0.35f)
        paintContorno.strokeWidth = 1f
        canvas.drawRect(cx - 7 * s, cy - 10 * s + orelhaOffset, cx - 3 * s, cy - 5 * s, paintContorno)
        canvas.drawRect(cx + 3 * s, cy - 10 * s + orelhaOffset, cx + 7 * s, cy - 5 * s, paintContorno)

        // --- Olhos grandes expressivos ---
        val corOlho = when (spikeState) {
            "ALERTANDO"        -> Color.rgb(255, 70, 70)
            "INCENTIVANDO"     -> Color.rgb(255, 200, 50)
            "ENTUSIASMADO"     -> Color.rgb(80, 220, 80)
            "SLOWDOWN_PROPRIO" -> Color.rgb(140, 140, 255)
            "CELEBRANDO"       -> Color.rgb(255, 220, 80)
            else               -> Color.rgb(30, 30, 30)
        }
        // Olho esquerdo
        paintFill.color = corOlho
        canvas.drawRect(cx - 4.5f * s, cy - 5 * s, cx - 1 * s, cy - 1 * s, paintFill)
        // Olho direito
        canvas.drawRect(cx + 1 * s, cy - 5 * s, cx + 4.5f * s, cy - 1 * s, paintFill)
        // Brilho (pixel branco no canto superior esquerdo de cada olho)
        paintFill.color = Color.WHITE
        canvas.drawRect(cx - 4 * s, cy - 5 * s, cx - 3 * s, cy - 4 * s, paintFill)
        canvas.drawRect(cx + 1.5f * s, cy - 5 * s, cx + 2.5f * s, cy - 4 * s, paintFill)
        // Pupila
        paintFill.color = Color.rgb(5, 5, 5)
        canvas.drawRect(cx - 3 * s, cy - 4 * s, cx - 2 * s, cy - 1.5f * s, paintFill)
        canvas.drawRect(cx + 2 * s, cy - 4 * s, cx + 3 * s, cy - 1.5f * s, paintFill)

        // --- Nariz ---
        paintFill.color = Color.rgb(30, 30, 30)
        canvas.drawRect(cx - 1.5f * s, cy - 1 * s, cx + 1.5f * s, cy + 1 * s, paintFill)
        paintFill.color = Color.rgb(255, 160, 160)
        canvas.drawRect(cx - 0.5f * s, cy - 0.5f * s, cx + 0.5f * s, cy + 0.5f * s, paintFill)

        // --- Efeitos especiais por estado ---
        when (spikeState) {
            "FAREJANDO" -> {
                paintFill.color = Color.argb(110, 220, 220, 220)
                val sniff = (Math.sin(t * Math.PI * 4) * 3 * s).toFloat()
                canvas.drawCircle(cx + 6 * s, cy - 2 * s + sniff, 1.5f * s, paintFill)
                canvas.drawCircle(cx + 8 * s, cy - 4 * s + sniff, 1f * s, paintFill)
            }
            "CHAMANDO" -> {
                paintContorno.color = Color.argb(140, 255, 255, 100)
                paintContorno.strokeWidth = 1.2f * s
                val raio = (3 + t * 5) * s
                canvas.drawCircle(cx, cy - 10 * s, raio, paintContorno)
                paintContorno.strokeWidth = 1.5f
            }
        }
    }

    fun createSpikeFrames(state: String, tileW: Int, tileH: Int): List<Bitmap> {
        return List(12) { i ->
            val bmp = Bitmap.createBitmap(tileW, tileH * 2, Bitmap.Config.ARGB_8888)
            renderSpike(Canvas(bmp), tileW / 2f, tileH / 2f, state, i, tileW.toFloat(), tileH.toFloat())
            bmp
        }
    }

    // -------------------------------------------------------------------------
    // Monsters — silhuetas distintas, paletas quentes, outline escuro
    // -------------------------------------------------------------------------

    /**
     * Desenha um Monster com aparência procedural, outline 1px e sombra no chão.
     * 4 variantes de silhueta: redondo, espinhoso, quadrado, alto.
     */
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
        val cy = y + bob

        // Sombra elíptica no chão
        paintFill.color = Color.argb(60, 0, 0, 0)
        canvas.drawOval(RectF(cx - 8 * s, cy + 10 * s, cx + 8 * s, cy + 15 * s), paintFill)

        when (appearance.shapeVariant % 4) {
            0 -> monsterRedondo(canvas, cx, cy, s, t, appearance)
            1 -> monsterEspinhoso(canvas, cx, cy, s, t, appearance)
            2 -> monsterQuadrado(canvas, cx, cy, s, t, appearance)
            3 -> monsterAlto(canvas, cx, cy, s, t, appearance)
        }

        // Efeito de aura para o Boss
        if (appearance.isBoss) {
            paintContorno.color = Color.argb(100, 255, 50, 50)
            paintContorno.strokeWidth = 2f * s
            val auraRaio = (12 + Math.sin(t * Math.PI * 4) * 2) * s
            canvas.drawCircle(cx, cy, auraRaio.toFloat(), paintContorno)
            paintContorno.strokeWidth = 1.5f
        }
    }

    private fun monsterRedondo(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, app: MonsterAppearance) {
        // Corpo redondo
        paintFill.color = app.bodyColor
        canvas.drawCircle(cx, cy, 9 * s, paintFill)
        // Detalhe escuro no topo (profundidade)
        paintFill.color = escurecer(app.bodyColor, 0.20f)
        canvas.drawOval(RectF(cx - 9 * s, cy - 9 * s, cx + 9 * s, cy - 2 * s), paintFill)
        // Highlight no canto superior esquerdo
        paintFill.color = clarear(app.bodyColor, 0.20f)
        canvas.drawOval(RectF(cx - 8 * s, cy - 8 * s, cx - 2 * s, cy - 3 * s), paintFill)
        // Olhos grandes
        paintFill.color = app.eyeColor
        canvas.drawCircle(cx - 3.5f * s, cy - 2 * s, 3f * s, paintFill)
        canvas.drawCircle(cx + 3.5f * s, cy - 2 * s, 3f * s, paintFill)
        // Brilho nos olhos
        paintFill.color = Color.WHITE
        canvas.drawCircle(cx - 2.5f * s, cy - 3 * s, 1.2f * s, paintFill)
        canvas.drawCircle(cx + 4.5f * s, cy - 3 * s, 1.2f * s, paintFill)
        // Pupila
        paintFill.color = Color.rgb(10, 0, 0)
        canvas.drawCircle(cx - 3 * s, cy - 2 * s, 1.5f * s, paintFill)
        canvas.drawCircle(cx + 4 * s, cy - 2 * s, 1.5f * s, paintFill)
        // Boca
        paintContorno.color = Color.rgb(20, 0, 0)
        paintContorno.strokeWidth = 1.5f * s
        canvas.drawArc(RectF(cx - 4 * s, cy + 1 * s, cx + 4 * s, cy + 6 * s), 0f, 180f, false, paintContorno)
        paintContorno.strokeWidth = 1.5f
        // Outline
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
        // Centro mais escuro
        paintFill.color = escurecer(app.bodyColor, 0.15f)
        canvas.drawCircle(cx, cy, 5 * s, paintFill)
        // Highlight
        paintFill.color = clarear(app.bodyColor, 0.20f)
        canvas.drawCircle(cx - 3 * s, cy - 3 * s, 2 * s, paintFill)
        // Olhos
        paintFill.color = app.eyeColor
        canvas.drawRect(cx - 4 * s, cy - 3.5f * s, cx - 1 * s, cy + 0.5f * s, paintFill)
        canvas.drawRect(cx + 1 * s, cy - 3.5f * s, cx + 4 * s, cy + 0.5f * s, paintFill)
        paintFill.color = Color.WHITE
        canvas.drawRect(cx - 3.5f * s, cy - 3.5f * s, cx - 2.5f * s, cy - 2.5f * s, paintFill)
        canvas.drawRect(cx + 1.5f * s, cy - 3.5f * s, cx + 2.5f * s, cy - 2.5f * s, paintFill)
        // Outline
        paintContorno.color = escurecer(app.bodyColor, 0.50f)
        paintContorno.strokeWidth = 1.2f
        canvas.drawPath(path, paintContorno)
    }

    private fun monsterQuadrado(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, app: MonsterAppearance) {
        paintFill.color = app.bodyColor
        canvas.drawRect(cx - 9 * s, cy - 9 * s, cx + 9 * s, cy + 9 * s, paintFill)
        // Faixa escura no topo
        paintFill.color = escurecer(app.bodyColor, 0.22f)
        canvas.drawRect(cx - 9 * s, cy - 9 * s, cx + 9 * s, cy - 5 * s, paintFill)
        // Highlight
        paintFill.color = clarear(app.bodyColor, 0.18f)
        canvas.drawRect(cx - 8 * s, cy - 8 * s, cx - 3 * s, cy - 5 * s, paintFill)
        // Olhos quadrados grandes
        paintFill.color = app.eyeColor
        canvas.drawRect(cx - 6.5f * s, cy - 4.5f * s, cx - 1 * s, cy + 1.5f * s, paintFill)
        canvas.drawRect(cx + 1 * s, cy - 4.5f * s, cx + 6.5f * s, cy + 1.5f * s, paintFill)
        paintFill.color = Color.WHITE
        canvas.drawRect(cx - 6 * s, cy - 4.5f * s, cx - 5 * s, cy - 3.5f * s, paintFill)
        canvas.drawRect(cx + 1.5f * s, cy - 4.5f * s, cx + 2.5f * s, cy - 3.5f * s, paintFill)
        paintFill.color = Color.rgb(10, 0, 0)
        canvas.drawRect(cx - 4.5f * s, cy - 3.5f * s, cx - 2.5f * s, cy + 1 * s, paintFill)
        canvas.drawRect(cx + 2.5f * s, cy - 3.5f * s, cx + 4.5f * s, cy + 1 * s, paintFill)
        // Outline
        paintContorno.color = escurecer(app.bodyColor, 0.50f)
        paintContorno.strokeWidth = 1.2f
        canvas.drawRect(cx - 9 * s, cy - 9 * s, cx + 9 * s, cy + 9 * s, paintContorno)
    }

    private fun monsterAlto(canvas: Canvas, cx: Float, cy: Float, s: Float, t: Float, app: MonsterAppearance) {
        // Corpo alto e fino
        paintFill.color = app.bodyColor
        canvas.drawRect(cx - 5 * s, cy - 13 * s, cx + 5 * s, cy + 8 * s, paintFill)
        // Cabeça maior
        paintFill.color = clarear(app.bodyColor, 0.08f)
        canvas.drawRect(cx - 7 * s, cy - 17 * s, cx + 7 * s, cy - 9 * s, paintFill)
        // Highlight
        paintFill.color = clarear(app.bodyColor, 0.22f)
        canvas.drawRect(cx - 6 * s, cy - 16 * s, cx - 2 * s, cy - 13 * s, paintFill)
        // Olhos grandes
        paintFill.color = app.eyeColor
        canvas.drawCircle(cx - 3 * s, cy - 13.5f * s, 3f * s, paintFill)
        canvas.drawCircle(cx + 3 * s, cy - 13.5f * s, 3f * s, paintFill)
        paintFill.color = Color.WHITE
        canvas.drawCircle(cx - 2 * s, cy - 14.5f * s, 1.2f * s, paintFill)
        canvas.drawCircle(cx + 4 * s, cy - 14.5f * s, 1.2f * s, paintFill)
        paintFill.color = Color.rgb(10, 0, 0)
        canvas.drawCircle(cx - 3 * s, cy - 13.5f * s, 1.5f * s, paintFill)
        canvas.drawCircle(cx + 3 * s, cy - 13.5f * s, 1.5f * s, paintFill)
        // Outline
        paintContorno.color = escurecer(app.bodyColor, 0.50f)
        paintContorno.strokeWidth = 1.2f
        canvas.drawRect(cx - 7 * s, cy - 17 * s, cx + 7 * s, cy + 8 * s, paintContorno)
    }

    // -------------------------------------------------------------------------
    // Utilitários de cor
    // -------------------------------------------------------------------------

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
}
