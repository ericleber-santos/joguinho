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
     * HUD compacto para smartphones — barra discreta na parte inferior.
     * Score à esquerda, combo no centro, andar/mapa à direita.
     * Slowdown aparece como texto pequeno acima da barra quando ativo.
     */
    fun renderCompact(canvas: Canvas, gameState: GameState, w: Int, h: Int) {
        val barH = 36f
        val padding = 8f
        val barY = h - barH - padding

        // Fundo semi-transparente na parte inferior
        bgPaint.color = Color.argb(160, 0, 0, 0)
        canvas.drawRect(0f, barY - padding, w.toFloat(), h.toFloat(), bgPaint)

        val baselineY = barY + barH * 0.72f

        // Score — esquerda
        textPaint.textSize = 18f
        textPaint.color = Color.rgb(220, 220, 200)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${gameState.accumulatedScore.toInt()}", padding * 2, baselineY, textPaint)

        // Combo — centro (só se ativo)
        if (gameState.comboStreak > 0) {
            val comboColor = when {
                gameState.comboStreak >= 20 -> Color.rgb(255, 80, 80)
                gameState.comboStreak >= 10 -> Color.rgb(255, 160, 50)
                else -> Color.rgb(255, 220, 80)
            }
            textPaint.textSize = 18f
            textPaint.color = comboColor
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("x${gameState.comboStreak}", w / 2f, baselineY, textPaint)
        }

        // Andar e mapa — direita
        textPaint.textSize = 16f
        textPaint.color = Color.rgb(180, 180, 160)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("A${gameState.floorNumber}  M${gameState.mapIndex + 1}/3", w - padding * 2, baselineY, textPaint)

        // Slowdown — texto pequeno acima da barra, centralizado
        if (gameState.heroIsSlowedDown) {
            val seg = gameState.heroSlowdownRemainingMs / 1000f
            textPaint.textSize = 15f
            textPaint.color = Color.argb(220, 120, 120, 255)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("LENTO %.1fs".format(seg), w / 2f, barY - padding * 0.5f, textPaint)
        }

        textPaint.textAlign = Paint.Align.LEFT

        // Banner de andar/mapa (texto pequeno no canto superior direito)
        renderBannerMapaAtual(canvas, gameState, w, h)

        // Mini-HUD permanente no canto superior esquerdo
        renderInfoMapaEsquerda(canvas, gameState)
    }

    /**
     * HUD expandido para tablets — mesma estrutura, tamanhos maiores.
     */
    fun renderExpanded(canvas: Canvas, gameState: GameState, w: Int, h: Int) {
        val barH = 32f
        val padding = 8f
        val barY = h - barH - padding

        bgPaint.color = Color.argb(160, 0, 0, 0)
        canvas.drawRect(0f, barY - padding, w.toFloat(), h.toFloat(), bgPaint)

        val baselineY = barY + barH * 0.72f

        textPaint.textSize = 16f
        textPaint.color = Color.rgb(220, 220, 200)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${gameState.accumulatedScore.toInt()}", padding * 2, baselineY, textPaint)

        if (gameState.comboStreak > 0) {
            val comboColor = when {
                gameState.comboStreak >= 20 -> Color.rgb(255, 80, 80)
                gameState.comboStreak >= 10 -> Color.rgb(255, 160, 50)
                else -> Color.rgb(255, 220, 80)
            }
            textPaint.textSize = 16f
            textPaint.color = comboColor
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("x${gameState.comboStreak}", w / 2f, baselineY, textPaint)
        }

        textPaint.textSize = 14f
        textPaint.color = Color.rgb(180, 180, 160)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Andar ${gameState.floorNumber}  Mapa ${gameState.mapIndex + 1}/3", w - padding * 2, baselineY, textPaint)

        if (gameState.heroIsSlowedDown) {
            val seg = gameState.heroSlowdownRemainingMs / 1000f
            textPaint.textSize = 13f
            textPaint.color = Color.argb(220, 120, 120, 255)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("LENTO %.1fs".format(seg), w / 2f, barY - padding * 0.5f, textPaint)
        }

        textPaint.textAlign = Paint.Align.LEFT
        renderBannerMapaAtual(canvas, gameState, w, h)

        // Mini-HUD permanente no canto superior esquerdo
        renderInfoMapaEsquerda(canvas, gameState)
    }

    /**
     * Mini-HUD no canto superior esquerdo com segundos restantes.
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
     * Mini-HUD no canto superior esquerdo com informações permanentes do mapa atual.
     * Exibe: andar, mapa, bioma e timer do floor.
     * Fundo semi-transparente arredondado, texto monoespaçado pequeno.
     */
    fun renderInfoMapaEsquerda(canvas: Canvas, gameState: GameState) {
        val margem = 10f
        val padH = 14f
        val padV = 10f
        val espacoLinha = 30f
        val tamanhoTexto = 22f

        val andar = gameState.floorNumber
        val mapa = gameState.mapIndex + 1
        val nomeBiomaTexto = nomeBioma(gameState.currentBiome)
        val timerSeg = gameState.floorTimerMs / 1000
        val timerTexto = "%d:%02d".format(timerSeg / 60, timerSeg % 60)

        val linhas = listOf(
            "Andar $andar",
            "Mapa  $mapa/3",
            nomeBiomaTexto,
            timerTexto
        )

        // Mede largura máxima para dimensionar o painel
        textPaint.textSize = tamanhoTexto
        val larguraMax = linhas.maxOf { textPaint.measureText(it) }
        val painelW = larguraMax + padH * 2
        val painelH = linhas.size * espacoLinha + padV * 2

        // Fundo semi-transparente
        bgPaint.color = Color.argb(170, 0, 0, 0)
        canvas.drawRoundRect(
            android.graphics.RectF(margem, margem, margem + painelW, margem + painelH),
            8f, 8f, bgPaint
        )

        // Borda sutil
        bgPaint.color = Color.argb(90, 255, 255, 255)
        bgPaint.style = Paint.Style.STROKE
        bgPaint.strokeWidth = 1f
        canvas.drawRoundRect(
            android.graphics.RectF(margem, margem, margem + painelW, margem + painelH),
            8f, 8f, bgPaint
        )
        bgPaint.style = Paint.Style.FILL

        // Textos
        textPaint.textSize = tamanhoTexto
        textPaint.textAlign = Paint.Align.LEFT
        val corBioma = com.ericleber.joguinho.biome.BIOME_PALETTES[gameState.currentBiome]?.accentColor
            ?: 0xFFFFCC00.toInt()

        val cores = listOf(
            Color.rgb(230, 230, 210),   // andar — branco suave
            Color.rgb(190, 190, 170),   // mapa — cinza
            Color.argb(255, Color.red(corBioma), Color.green(corBioma), Color.blue(corBioma)), // bioma — cor do acento
            Color.rgb(160, 210, 160)    // timer — verde suave
        )

        linhas.forEachIndexed { i, linha ->
            val tx = margem + padH
            val ty = margem + padV + espacoLinha * (i + 1) - 3f
            // Sombra
            textPaint.color = Color.argb(140, 0, 0, 0)
            canvas.drawText(linha, tx + 1f, ty + 1f, textPaint)
            // Texto
            textPaint.color = cores[i]
            canvas.drawText(linha, tx, ty, textPaint)
        }

        textPaint.textAlign = Paint.Align.LEFT
    }

    /**
     * Texto discreto no canto superior direito exibido ao entrar em novo mapa.
     * Aparece por ~2s com fade-in e fade-out. Sem painel, sem fundo — só texto com sombra.
     */
    fun renderBannerMapaAtual(canvas: Canvas, gameState: GameState, w: Int, h: Int) {
        val tempoMs = gameState.floorTimerMs
        if (tempoMs > 2000L) return

        val progresso = tempoMs / 2000f
        val alpha = when {
            progresso < 0.12f -> (progresso / 0.12f * 255).toInt()
            progresso > 0.75f -> ((1f - (progresso - 0.75f) / 0.25f) * 255).toInt()
            else -> 255
        }.coerceIn(0, 255)

        if (alpha <= 0) return

        val nomeBioma = nomeBioma(gameState.currentBiome)
        val andar = gameState.floorNumber
        val mapa = gameState.mapIndex + 1
        val corBioma = com.ericleber.joguinho.biome.BIOME_PALETTES[gameState.currentBiome]?.accentColor
            ?: 0xFFFFCC00.toInt()

        val margem = 12f
        val tx = w - margem

        // Linha 1: "A12 · M2/3" — pequena, cinza
        textPaint.textSize = 11f
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.color = Color.argb((alpha * 0.6f).toInt(), 200, 200, 180)
        canvas.drawText("A$andar · M$mapa/3", tx + 1f, 21f, textPaint)
        textPaint.color = Color.argb(alpha, 200, 200, 180)
        canvas.drawText("A$andar · M$mapa/3", tx, 20f, textPaint)

        // Linha 2: nome do bioma — cor do acento
        textPaint.textSize = 13f
        textPaint.color = Color.argb((alpha * 0.5f).toInt(), 0, 0, 0)
        canvas.drawText(nomeBioma, tx + 1f, 37f, textPaint)
        textPaint.color = Color.argb(alpha, Color.red(corBioma), Color.green(corBioma), Color.blue(corBioma))
        canvas.drawText(nomeBioma, tx, 36f, textPaint)

        textPaint.textAlign = Paint.Align.LEFT
    }

    /**
     * Retorna o nome legível do bioma em português.
     */
    private fun nomeBioma(biome: com.ericleber.joguinho.biome.Biome): String = when (biome) {
        com.ericleber.joguinho.biome.Biome.MINA_ABANDONADA      -> "Mina Abandonada"
        com.ericleber.joguinho.biome.Biome.RIACHOS_SUBTERRANEOS -> "Riachos Subterrâneos"
        com.ericleber.joguinho.biome.Biome.PLANTACOES_ABRIGOS   -> "Plantações e Abrigos"
        com.ericleber.joguinho.biome.Biome.CONSTRUCOES_ROCHOSAS -> "Construções Rochosas"
        com.ericleber.joguinho.biome.Biome.POMARES_ABERTURAS    -> "Pomares e Aberturas"
        com.ericleber.joguinho.biome.Biome.ERA_DINOSSAUROS      -> "Era dos Dinossauros"
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
