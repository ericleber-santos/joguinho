package com.ericleber.joguinho.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.ericleber.joguinho.core.GameState

/**
 * Renderiza o HUD (Heads-Up Display) via Canvas.
 *
 * Suporta dois modos:
 * - Compacto: para telas < 600dp de largura (smartphones)
 * - Expandido: para telas >= 600dp de largura (tablets)
 *
 * Todos os elementos são desenhados via Canvas — sem arquivos de imagem.
 *
 * Requisitos: 5.4, 5.7, 8.6, 13.2, 13.3, 17.5
 */
class HudRenderer {

    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true  // texto precisa de antialiasing para legibilidade
        isFilterBitmap = false
        color = Color.WHITE
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private val bgPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }

    /**
     * Método principal de renderização — seleciona automaticamente compacto ou expandido.
     * @param canvas canvas de destino
     * @param gameState estado atual do jogo
     * @param screenWidthDp largura da tela em dp
     */
    fun render(canvas: Canvas, gameState: GameState, screenWidthDp: Float) {
        val w = canvas.width
        val h = canvas.height
        if (screenWidthDp < 600f) {
            renderCompact(canvas, gameState, w, h)
        } else {
            renderExpanded(canvas, gameState, w, h)
        }
    }

    /**
     * HUD compacto para smartphones.
     * Score no topo-esquerda, slowdown no topo-direita, combo no centro-topo.
     */
    fun renderCompact(canvas: Canvas, gameState: GameState, w: Int, h: Int) {
        val padding = 8f
        val barH = 32f

        // Fundo semi-transparente no topo
        bgPaint.color = Color.argb(140, 0, 0, 0)
        canvas.drawRect(0f, 0f, w.toFloat(), barH + padding * 2, bgPaint)

        // Score
        renderScore(canvas, gameState.accumulatedScore, padding, padding + 20f)

        // Combo streak
        if (gameState.comboStreak > 0) {
            renderComboStreak(canvas, gameState.comboStreak, w / 2f, padding + 20f)
        }

        // Slowdown indicator
        if (gameState.heroIsSlowedDown) {
            renderSlowdownIndicator(
                canvas,
                gameState.heroSlowdownRemainingMs,
                w - 120f,
                padding,
                compact = true
            )
        }

        // Aviso de temperatura
        if (gameState.isThermalThrottling) {
            renderTemperatureWarning(canvas, w - 40f, barH + padding * 2 + 8f)
        }
    }

    /**
     * HUD expandido para tablets.
     * Mais espaço para informações adicionais.
     */
    fun renderExpanded(canvas: Canvas, gameState: GameState, w: Int, h: Int) {
        val padding = 12f
        val barH = 48f

        // Fundo semi-transparente no topo
        bgPaint.color = Color.argb(140, 0, 0, 0)
        canvas.drawRect(0f, 0f, w.toFloat(), barH + padding * 2, bgPaint)

        // Score (maior)
        textPaint.textSize = 22f
        renderScore(canvas, gameState.accumulatedScore, padding, padding + 28f)

        // Combo streak (centro)
        if (gameState.comboStreak > 0) {
            renderComboStreak(canvas, gameState.comboStreak, w / 2f, padding + 28f)
        }

        // Slowdown indicator (direita)
        if (gameState.heroIsSlowedDown) {
            renderSlowdownIndicator(
                canvas,
                gameState.heroSlowdownRemainingMs,
                w - 180f,
                padding,
                compact = false
            )
        }

        // Aviso de temperatura
        if (gameState.isThermalThrottling) {
            renderTemperatureWarning(canvas, w - 60f, barH + padding * 2 + 12f)
        }

        // Andar atual
        textPaint.textSize = 14f
        textPaint.color = Color.rgb(200, 200, 200)
        canvas.drawText("Andar ${gameState.floorNumber}", padding, barH + padding * 2 + 20f, textPaint)
        textPaint.color = Color.WHITE
    }

    /**
     * Barra de contagem regressiva do Slowdown com segundos restantes.
     * @param remainingMs milissegundos restantes do Slowdown
     * @param x posição X
     * @param y posição Y
     * @param compact modo compacto (menor)
     */
    fun renderSlowdownIndicator(
        canvas: Canvas,
        remainingMs: Long,
        x: Float,
        y: Float,
        compact: Boolean
    ) {
        val barW = if (compact) 100f else 160f
        val barH = if (compact) 12f else 18f
        val maxMs = 5000L  // duração máxima esperada do Slowdown

        val progress = (remainingMs.toFloat() / maxMs).coerceIn(0f, 1f)
        val seconds = remainingMs / 1000f

        // Fundo da barra
        bgPaint.color = Color.argb(180, 30, 30, 80)
        canvas.drawRoundRect(RectF(x, y, x + barW, y + barH), 4f, 4f, bgPaint)

        // Preenchimento da barra (azul → roxo conforme diminui)
        val barColor = lerpColor(Color.rgb(100, 100, 255), Color.rgb(200, 50, 200), 1f - progress)
        bgPaint.color = barColor
        canvas.drawRoundRect(RectF(x, y, x + barW * progress, y + barH), 4f, 4f, bgPaint)

        // Borda
        bgPaint.color = Color.rgb(150, 150, 255)
        bgPaint.style = Paint.Style.STROKE
        bgPaint.strokeWidth = 1f
        canvas.drawRoundRect(RectF(x, y, x + barW, y + barH), 4f, 4f, bgPaint)
        bgPaint.style = Paint.Style.FILL

        // Texto de segundos
        textPaint.textSize = if (compact) 10f else 14f
        textPaint.color = Color.WHITE
        val label = "LENTO %.1fs".format(seconds)
        canvas.drawText(label, x + 4f, y + barH - 2f, textPaint)
    }

    /**
     * Contador de combo streak.
     * @param combo número atual de combo
     * @param x posição X (centro)
     * @param y posição Y (baseline do texto)
     */
    fun renderComboStreak(canvas: Canvas, combo: Int, x: Float, y: Float) {
        // Cor do combo: amarelo para combos baixos, laranja para médios, vermelho para altos
        val comboColor = when {
            combo >= 20 -> Color.rgb(255, 50, 50)
            combo >= 10 -> Color.rgb(255, 150, 50)
            else -> Color.rgb(255, 220, 50)
        }

        textPaint.textSize = 18f
        textPaint.color = comboColor
        textPaint.textAlign = Paint.Align.CENTER

        val label = "x$combo COMBO"
        // Sombra do texto
        textPaint.color = Color.argb(180, 0, 0, 0)
        canvas.drawText(label, x + 1f, y + 1f, textPaint)
        // Texto principal
        textPaint.color = comboColor
        canvas.drawText(label, x, y, textPaint)

        textPaint.textAlign = Paint.Align.LEFT
    }

    /**
     * Exibe o score atual.
     * @param score pontuação atual
     * @param x posição X
     * @param y posição Y (baseline)
     */
    fun renderScore(canvas: Canvas, score: Float, x: Float, y: Float) {
        textPaint.textSize = 18f
        textPaint.color = Color.WHITE

        val label = "SCORE: ${score.toInt()}"
        // Sombra
        textPaint.color = Color.argb(180, 0, 0, 0)
        canvas.drawText(label, x + 1f, y + 1f, textPaint)
        // Texto
        textPaint.color = Color.WHITE
        canvas.drawText(label, x, y, textPaint)
    }

    /**
     * Ícone de aviso de temperatura (throttling térmico).
     * Desenha um ícone de termômetro simples.
     * @param x posição X (centro do ícone)
     * @param y posição Y (topo do ícone)
     */
    fun renderTemperatureWarning(canvas: Canvas, x: Float, y: Float) {
        val iconSize = 20f

        // Fundo vermelho pulsante
        bgPaint.color = Color.argb(180, 200, 50, 50)
        canvas.drawRoundRect(
            RectF(x - iconSize / 2, y, x + iconSize / 2, y + iconSize),
            4f, 4f, bgPaint
        )

        // Símbolo de temperatura (triângulo de alerta)
        paint.color = Color.rgb(255, 220, 50)
        paint.style = Paint.Style.FILL
        val path = android.graphics.Path()
        path.moveTo(x, y + 2f)
        path.lineTo(x + iconSize / 2 - 2f, y + iconSize - 2f)
        path.lineTo(x - iconSize / 2 + 2f, y + iconSize - 2f)
        path.close()
        canvas.drawPath(path, paint)

        // "!" no centro
        textPaint.textSize = 12f
        textPaint.color = Color.rgb(200, 50, 50)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("!", x, y + iconSize - 4f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    /**
     * Notificação animada de conquista.
     * @param achievementName nome da conquista
     * @param progress progresso da animação (0.0 = entrando, 1.0 = saindo)
     * @param x posição X
     * @param y posição Y
     */
    fun renderAchievementNotification(
        canvas: Canvas,
        achievementName: String,
        progress: Float,
        x: Float,
        y: Float
    ) {
        // Animação de slide: entra da direita, fica visível, sai pela direita
        val slideOffset = when {
            progress < 0.2f -> (1f - progress / 0.2f) * 300f  // entrando
            progress > 0.8f -> ((progress - 0.8f) / 0.2f) * 300f  // saindo
            else -> 0f  // visível
        }

        val panelW = 240f
        val panelH = 48f
        val panelX = x + slideOffset
        val panelY = y

        // Fundo da notificação
        bgPaint.color = Color.argb(220, 20, 60, 20)
        canvas.drawRoundRect(
            RectF(panelX, panelY, panelX + panelW, panelY + panelH),
            8f, 8f, bgPaint
        )

        // Borda dourada
        bgPaint.color = Color.rgb(200, 170, 50)
        bgPaint.style = Paint.Style.STROKE
        bgPaint.strokeWidth = 2f
        canvas.drawRoundRect(
            RectF(panelX, panelY, panelX + panelW, panelY + panelH),
            8f, 8f, bgPaint
        )
        bgPaint.style = Paint.Style.FILL

        // Ícone de troféu (estrela simples)
        paint.color = Color.rgb(255, 220, 50)
        canvas.drawRect(panelX + 8f, panelY + 14f, panelX + 20f, panelY + 34f, paint)

        // Texto da conquista
        textPaint.textSize = 11f
        textPaint.color = Color.rgb(200, 200, 200)
        canvas.drawText("CONQUISTA!", panelX + 28f, panelY + 18f, textPaint)

        textPaint.textSize = 13f
        textPaint.color = Color.rgb(255, 220, 100)
        canvas.drawText(achievementName, panelX + 28f, panelY + 36f, textPaint)
    }

    // -------------------------------------------------------------------------
    // Utilitários
    // -------------------------------------------------------------------------

    private fun lerpColor(start: Int, end: Int, t: Float): Int {
        val r = (Color.red(start) + (Color.red(end) - Color.red(start)) * t).toInt()
        val g = (Color.green(start) + (Color.green(end) - Color.green(start)) * t).toInt()
        val b = (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * t).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}

/**
 * Extensão para verificar throttling térmico no GameState.
 * Usa o evento de temperatura emitido pelo GameLoop.
 */
val GameState.isThermalThrottling: Boolean
    get() = pendingEvents.any { it is com.ericleber.joguinho.core.GameEvent.HeroReceivedSlowdown }
        && heroIsSlowedDown.not()
