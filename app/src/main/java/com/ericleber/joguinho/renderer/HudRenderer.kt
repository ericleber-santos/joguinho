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
     * Tinta para o ícone do Boss.
     */
    private val bossIconPaint = Paint().apply {
        isAntiAlias = true
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
     * HUD compacto para smartphones — HUD translúcido "State of the Art".
     * Respeita Safe Areas (evita entalhes).
     */
    fun renderCompact(canvas: Canvas, gameState: GameState, w: Int, h: Int) {
        // Altura da barra baseada na altura da tela (aprox 12% para toque fácil)
        val barH = h * 0.12f
        val padding = w * 0.04f
        val barY = h - barH

        // HUD Inferior Premium (Sombra projetada)
        bgPaint.color = Color.argb(120, 0, 0, 0)
        canvas.drawRect(0f, barY - 10f, w.toFloat(), h.toFloat(), bgPaint) // sombra suave
        bgPaint.color = Color.argb(220, 10, 12, 18) // Fundo profundo
        canvas.drawRect(0f, barY, w.toFloat(), h.toFloat(), bgPaint)

        val baselineY = barY + barH * 0.65f

        // Score — esquerda
        textPaint.textSize = barH * 0.5f
        textPaint.color = Color.rgb(220, 220, 200)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${gameState.accumulatedScore.toInt()}", padding, baselineY, textPaint)

        // Combo — centro (só se ativo)
        if (gameState.comboStreak > 0) {
            val comboColor = when {
                gameState.comboStreak >= 20 -> Color.rgb(255, 80, 80)
                gameState.comboStreak >= 10 -> Color.rgb(255, 160, 50)
                else -> Color.rgb(255, 220, 80)
            }
            textPaint.textSize = barH * 0.5f
            textPaint.color = comboColor
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("x${gameState.comboStreak}", w / 2f, baselineY, textPaint)
        }

        // Andar e mapa — direita
        textPaint.textSize = barH * 0.6f
        textPaint.color = Color.rgb(180, 180, 160)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("A${gameState.floorNumber} M${gameState.mapIndex + 1}/3", w - padding, baselineY, textPaint)

        // Slowdown — centralizado, com sombra forte
        if (gameState.heroIsSlowedDown) {
            val seg = gameState.heroSlowdownRemainingMs / 1000f
            textPaint.textSize = barH * 0.8f
            textPaint.color = Color.argb(255, 255, 80, 80)
            textPaint.setShadowLayer(8f, 0f, 4f, Color.BLACK)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("LENTO %.1fs".format(seg), w / 2f, barY - barH * 0.5f, textPaint)
            textPaint.clearShadowLayer()
        }
        
        // Mensagem do Boss — Painel estilo Zelda/Hades (Ícone + Texto Elegante)
        gameState.bossMessage?.let { msg ->
            val bannerH = barH * 1.5f
            val bannerY = barY - bannerH - 20f
            val bannerW = w * 0.85f
            val bannerX = (w - bannerW) / 2f
            
            // Fundo do Painel do Boss com cantos arredondados (Pro Max)
            val rect = RectF(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH)
            
            // Sombra do painel
            bgPaint.color = Color.argb(150, 0, 0, 0)
            canvas.drawRoundRect(RectF(rect.left + 5f, rect.top + 8f, rect.right + 5f, rect.bottom + 8f), 12f, 12f, bgPaint)
            
            // Fundo escuro com leve tom vermelho (OKLCH dark-red vibe)
            bgPaint.color = Color.argb(240, 20, 5, 5)
            canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
            
            // Borda dourada/vermelha
            bgPaint.color = Color.rgb(200, 40, 40)
            bgPaint.style = Paint.Style.STROKE
            bgPaint.strokeWidth = 3f
            canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
            bgPaint.style = Paint.Style.FILL
            
            // Ícone do Boss (quadrado à esquerda)
            val iconSize = bannerH * 0.7f
            val iconX = bannerX + 20f
            val iconY = bannerY + (bannerH - iconSize) / 2f
            bossIconPaint.color = Color.rgb(180, 30, 30) // placeholder para o rosto do Boss
            canvas.drawRoundRect(RectF(iconX, iconY, iconX + iconSize, iconY + iconSize), 8f, 8f, bossIconPaint)
            
            // Texto da Mensagem Elegante
            textPaint.textSize = bannerH * 0.4f
            textPaint.color = Color.rgb(240, 220, 220)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.setShadowLayer(4f, 0f, 2f, Color.BLACK)
            canvas.drawText("Chefe do Bioma:", iconX + iconSize + 20f, bannerY + bannerH * 0.4f, textPaint)
            
            textPaint.textSize = bannerH * 0.5f
            textPaint.color = Color.WHITE
            canvas.drawText(msg, iconX + iconSize + 20f, bannerY + bannerH * 0.85f, textPaint)
            textPaint.clearShadowLayer()
        }

        textPaint.textAlign = Paint.Align.LEFT
        renderBannerMapaAtual(canvas, gameState, w, h)
        renderInfoMapaEsquerda(canvas, gameState)
    }

    /**
     * HUD expandido para tablets — mesma estrutura, tamanhos maiores.
     */
    fun renderExpanded(canvas: Canvas, gameState: GameState, w: Int, h: Int) {
        // Em tablets, a barra pode ser um pouco menor proporcionalmente (6%)
        val barH = h * 0.06f
        val padding = w * 0.03f
        val barY = h - barH

        bgPaint.color = Color.argb(180, 0, 0, 0)
        canvas.drawRect(0f, barY, w.toFloat(), h.toFloat(), bgPaint)

        val baselineY = barY + barH * 0.65f

        // Score
        textPaint.textSize = barH * 0.5f
        textPaint.color = Color.rgb(220, 220, 200)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("${gameState.accumulatedScore.toInt()}", padding, baselineY, textPaint)

        // Combo
        if (gameState.comboStreak > 0) {
            val comboColor = when {
                gameState.comboStreak >= 20 -> Color.rgb(255, 80, 80)
                gameState.comboStreak >= 10 -> Color.rgb(255, 160, 50)
                else -> Color.rgb(255, 220, 80)
            }
            textPaint.textSize = barH * 0.5f
            textPaint.color = comboColor
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("x${gameState.comboStreak}", w / 2f, baselineY, textPaint)
        }

        // Andar e mapa
        textPaint.textSize = barH * 0.6f
        textPaint.color = Color.rgb(180, 180, 160)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Andar ${gameState.floorNumber}  Mapa ${gameState.mapIndex + 1}/3", w - padding, baselineY, textPaint)

        // Slowdown
        if (gameState.heroIsSlowedDown) {
            val seg = gameState.heroSlowdownRemainingMs / 1000f
            textPaint.textSize = barH * 0.9f
            textPaint.color = Color.argb(255, 255, 80, 80)
            textPaint.setShadowLayer(8f, 0f, 4f, Color.BLACK)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("LENTO %.1fs".format(seg), w / 2f, barY - barH * 0.6f, textPaint)
            textPaint.clearShadowLayer()
        }
        
        // Mensagem do Boss — Estilo Zelda/Hades (Tablet Escalonado)
        gameState.bossMessage?.let { msg ->
            val bannerH = barH * 2f
            val bannerY = barY - bannerH - 30f
            val bannerW = w * 0.6f // Painel mais centralizado em tablets
            val bannerX = (w - bannerW) / 2f
            
            val rect = RectF(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH)
            
            // Sombra do painel
            bgPaint.color = Color.argb(150, 0, 0, 0)
            canvas.drawRoundRect(RectF(rect.left + 8f, rect.top + 10f, rect.right + 8f, rect.bottom + 10f), 16f, 16f, bgPaint)
            
            // Fundo escuro
            bgPaint.color = Color.argb(240, 20, 5, 5)
            canvas.drawRoundRect(rect, 16f, 16f, bgPaint)
            
            // Borda elegante
            bgPaint.color = Color.rgb(200, 40, 40)
            bgPaint.style = Paint.Style.STROKE
            bgPaint.strokeWidth = 4f
            canvas.drawRoundRect(rect, 16f, 16f, bgPaint)
            bgPaint.style = Paint.Style.FILL
            
            // Ícone do Boss (Rosto furioso simplificado)
            val iconSize = bannerH * 0.7f
            val iconX = bannerX + 30f
            val iconY = bannerY + (bannerH - iconSize) / 2f
            
            // Fundo da cabeça do Boss
            bossIconPaint.color = Color.rgb(160, 20, 20)
            canvas.drawRoundRect(RectF(iconX, iconY, iconX + iconSize, iconY + iconSize), 12f, 12f, bossIconPaint)
            
            // Chifres
            bossIconPaint.color = Color.rgb(80, 10, 10)
            canvas.drawRect(iconX + iconSize * 0.15f, iconY - iconSize * 0.15f, iconX + iconSize * 0.35f, iconY + iconSize * 0.2f, bossIconPaint)
            canvas.drawRect(iconX + iconSize * 0.65f, iconY - iconSize * 0.15f, iconX + iconSize * 0.85f, iconY + iconSize * 0.2f, bossIconPaint)
            
            // Olhos brilhantes e furiosos
            bossIconPaint.color = Color.YELLOW
            val eyeW = iconSize * 0.25f
            val eyeH = iconSize * 0.15f
            val eyeY = iconY + iconSize * 0.35f
            // Olho esquerdo
            val pathLeftEye = android.graphics.Path()
            pathLeftEye.moveTo(iconX + iconSize * 0.15f, eyeY + eyeH)
            pathLeftEye.lineTo(iconX + iconSize * 0.40f, eyeY + eyeH * 0.5f)
            pathLeftEye.lineTo(iconX + iconSize * 0.40f, eyeY + eyeH)
            canvas.drawPath(pathLeftEye, bossIconPaint)
            
            // Olho direito
            val pathRightEye = android.graphics.Path()
            pathRightEye.moveTo(iconX + iconSize * 0.85f, eyeY + eyeH)
            pathRightEye.lineTo(iconX + iconSize * 0.60f, eyeY + eyeH * 0.5f)
            pathRightEye.lineTo(iconX + iconSize * 0.60f, eyeY + eyeH)
            canvas.drawPath(pathRightEye, bossIconPaint)
            
            // Boca "denteada"
            bossIconPaint.color = Color.BLACK
            val mouthY = iconY + iconSize * 0.7f
            canvas.drawRect(iconX + iconSize * 0.2f, mouthY, iconX + iconSize * 0.8f, mouthY + iconSize * 0.15f, bossIconPaint)
            bossIconPaint.color = Color.WHITE // Dentes
            canvas.drawRect(iconX + iconSize * 0.3f, mouthY, iconX + iconSize * 0.4f, mouthY + iconSize * 0.1f, bossIconPaint)
            canvas.drawRect(iconX + iconSize * 0.6f, mouthY, iconX + iconSize * 0.7f, mouthY + iconSize * 0.1f, bossIconPaint)
            
            // Textos
            textPaint.textSize = bannerH * 0.35f
            textPaint.color = Color.rgb(240, 220, 220)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.setShadowLayer(4f, 0f, 2f, Color.BLACK)
            canvas.drawText("Chefe do Bioma:", iconX + iconSize + 30f, bannerY + bannerH * 0.35f, textPaint)
            
            textPaint.textSize = bannerH * 0.45f
            textPaint.color = Color.WHITE
            canvas.drawText(msg, iconX + iconSize + 30f, bannerY + bannerH * 0.8f, textPaint)
            textPaint.clearShadowLayer()
        }

        textPaint.textAlign = Paint.Align.LEFT
        renderBannerMapaAtual(canvas, gameState, w, h)
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
    private fun nomeBioma(biome: com.ericleber.joguinho.biome.Biome): String = biome.displayName

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
