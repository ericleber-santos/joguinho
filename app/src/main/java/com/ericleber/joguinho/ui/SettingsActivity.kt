package com.ericleber.joguinho.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Tela de configurações do jogo.
 *
 * Acessível a partir do menu principal e do menu de pausa (Requisito 12.1).
 * Toda a UI é desenhada via Canvas em uma View customizada — sem XML.
 * Orientação forçada landscape.
 *
 * Configurações disponíveis:
 * - volumeMusica: Int (0–100, padrão 80)
 * - volumeEfeitos: Int (0–100, padrão 80)
 * - tamanhoJoystick: Int (80–160dp, padrão 100)
 * - posicaoJoystick: String ("esquerda"/"direita", padrão "esquerda")
 * - modoAltoContraste: Boolean (padrão false)
 * - usarDPad: Boolean (padrão false)
 * - feedbackHaptico: Boolean (padrão true)
 * - intensidadeHaptico: Int (1–3, padrão 2)
 * - idioma: String ("pt"/"en", padrão "pt")
 *
 * Requisitos: 12.1, 12.2, 12.4, 12.5
 */
class SettingsActivity : Activity() {

    companion object {
        const val PREFS_NOME = "configuracoes_jogo"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var configView: ConfigView

    // Valores atuais das configurações
    private var volumeMusica = 80
    private var volumeEfeitos = 80
    private var tamanhoJoystick = 100
    private var posicaoJoystick = "esquerda"
    private var modoAltoContraste = false
    private var usarDPad = false
    private var feedbackHaptico = true
    private var intensidadeHaptico = 2
    private var idioma = "pt"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        prefs = getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
        carregarConfiguracoes()

        configView = ConfigView(this)
        setContentView(configView)
    }

    /** Carrega configurações salvas ou aplica valores padrão. */
    private fun carregarConfiguracoes() {
        volumeMusica = prefs.getInt("volumeMusica", 80)
        volumeEfeitos = prefs.getInt("volumeEfeitos", 80)
        tamanhoJoystick = prefs.getInt("tamanhoJoystick", 100)
        posicaoJoystick = prefs.getString("posicaoJoystick", "esquerda") ?: "esquerda"
        modoAltoContraste = prefs.getBoolean("modoAltoContraste", false)
        usarDPad = prefs.getBoolean("usarDPad", false)
        feedbackHaptico = prefs.getBoolean("feedbackHaptico", true)
        intensidadeHaptico = prefs.getInt("intensidadeHaptico", 2)
        idioma = prefs.getString("idioma", "pt") ?: "pt"
    }

    /** Persiste todas as configurações e encerra a Activity. */
    private fun salvarESair() {
        prefs.edit().apply {
            putInt("volumeMusica", volumeMusica)
            putInt("volumeEfeitos", volumeEfeitos)
            putInt("tamanhoJoystick", tamanhoJoystick)
            putString("posicaoJoystick", posicaoJoystick)
            putBoolean("modoAltoContraste", modoAltoContraste)
            putBoolean("usarDPad", usarDPad)
            putBoolean("feedbackHaptico", feedbackHaptico)
            putInt("intensidadeHaptico", intensidadeHaptico)
            putString("idioma", idioma)
            apply()
        }
        finish()
    }

    /** Dispara vibração de teste ao alterar intensidade háptica. */
    private fun vibrarTeste(intensidade: Int) {
        val vibrador: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrador?.let { v ->
            if (!v.hasVibrator()) return
            val duracaoMs = when (intensidade) { 1 -> 30L; 2 -> 60L; else -> 100L }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = when (intensidade) { 1 -> 80; 2 -> 160; else -> 255 }
                v.vibrate(VibrationEffect.createOneShot(duracaoMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(duracaoMs)
            }
        }
    }

    // =========================================================================
    // View customizada — toda UI desenhada via Canvas (Requisito 12.2)
    // =========================================================================

    inner class ConfigView(contexto: Activity) : View(contexto) {

        // Tintas reutilizáveis
        private val tintaFundo = Paint().apply { isAntiAlias = true }
        private val tintaTexto = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
        }
        private val tintaControle = Paint().apply { isAntiAlias = true }

        // Retângulos interativos para detecção de toque
        private val zonasToque = mutableMapOf<String, RectF>()

        // Slider arrastado no momento (null = nenhum)
        private var sliderAtivo: String? = null

        override fun onTouchEvent(evento: MotionEvent): Boolean {
            val x = evento.x
            val y = evento.y
            when (evento.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Verificar início de arrasto em slider
                    sliderAtivo = null
                    for ((chave, rect) in zonasToque) {
                        if (rect.contains(x, y)) {
                            when (chave) {
                                "sliderMusica", "sliderEfeitos", "sliderJoystick" -> {
                                    sliderAtivo = chave
                                    atualizarSlider(chave, x, rect)
                                }
                                "toggleContraste" -> { modoAltoContraste = !modoAltoContraste; invalidate() }
                                "toggleDPad" -> { usarDPad = !usarDPad; invalidate() }
                                "toggleHaptico" -> { feedbackHaptico = !feedbackHaptico; invalidate() }
                                "haptico1" -> { intensidadeHaptico = 1; if (feedbackHaptico) vibrarTeste(1); invalidate() }
                                "haptico2" -> { intensidadeHaptico = 2; if (feedbackHaptico) vibrarTeste(2); invalidate() }
                                "haptico3" -> { intensidadeHaptico = 3; if (feedbackHaptico) vibrarTeste(3); invalidate() }
                                "posEsquerda" -> { posicaoJoystick = "esquerda"; invalidate() }
                                "posDireita" -> { posicaoJoystick = "direita"; invalidate() }
                                "idiomapt" -> { idioma = "pt"; invalidate() }
                                "idiomaan" -> { idioma = "en"; invalidate() }
                                "salvar" -> salvarESair()
                                "voltar" -> finish()
                            }
                            return true
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val ativo = sliderAtivo ?: return true
                    zonasToque[ativo]?.let { rect -> atualizarSlider(ativo, x, rect) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> sliderAtivo = null
            }
            return true
        }

        /** Atualiza o valor do slider com base na posição X do toque. */
        private fun atualizarSlider(chave: String, x: Float, rect: RectF) {
            val proporcao = ((x - rect.left) / rect.width()).coerceIn(0f, 1f)
            when (chave) {
                "sliderMusica" -> volumeMusica = (proporcao * 100).toInt()
                "sliderEfeitos" -> volumeEfeitos = (proporcao * 100).toInt()
                "sliderJoystick" -> tamanhoJoystick = 80 + (proporcao * 80).toInt() // 80–160
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            zonasToque.clear()
            desenharTela(canvas)
        }

        private fun desenharTela(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()

            // Fundo
            val corFundo = if (modoAltoContraste) Color.BLACK else 0xFF0D0D0D.toInt()
            tintaFundo.color = corFundo
            canvas.drawRect(0f, 0f, w, h, tintaFundo)

            // Faixa superior
            tintaFundo.color = if (modoAltoContraste) 0xFF222200.toInt() else 0xFF1A1200.toInt()
            canvas.drawRect(0f, 0f, w, h * 0.14f, tintaFundo)

            // Título — usa sp via fontScale do sistema (Requisito 12.2)
            val escalaFonte = resources.configuration.fontScale
            val tamanhoTitulo = (h * 0.08f * escalaFonte).coerceAtMost(h * 0.12f)
            tintaTexto.color = if (modoAltoContraste) Color.YELLOW else 0xFFD4A017.toInt()
            tintaTexto.textSize = tamanhoTitulo
            tintaTexto.textAlign = Paint.Align.CENTER
            val rotuloTitulo = if (idioma == "pt") "Configurações" else "Settings"
            canvas.drawText(rotuloTitulo, w / 2f, h * 0.10f, tintaTexto)

            // Layout em duas colunas
            val col1X = w * 0.26f  // centro da coluna esquerda
            val col2X = w * 0.74f  // centro da coluna direita
            val inicioY = h * 0.18f
            val passo = h * 0.115f
            val tamanhoRotulo = (h * 0.042f * escalaFonte).coerceAtMost(h * 0.065f)
            val tamanhoValor = (h * 0.038f * escalaFonte).coerceAtMost(h * 0.055f)

            // --- Coluna esquerda ---
            // 1. Volume Música
            val lbMusica = if (idioma == "pt") "Vol. Música" else "Music Vol."
            desenharRotulo(canvas, lbMusica, w * 0.04f, inicioY, tamanhoRotulo)
            val rectSliderMusica = RectF(w * 0.04f, inicioY + passo * 0.12f, w * 0.48f, inicioY + passo * 0.55f)
            desenharSlider(canvas, rectSliderMusica, volumeMusica / 100f, "$volumeMusica", tamanhoValor)
            zonasToque["sliderMusica"] = rectSliderMusica

            // 2. Volume Efeitos
            val lbEfeitos = if (idioma == "pt") "Vol. Efeitos" else "SFX Vol."
            desenharRotulo(canvas, lbEfeitos, w * 0.04f, inicioY + passo, tamanhoRotulo)
            val rectSliderEfeitos = RectF(w * 0.04f, inicioY + passo * 1.12f, w * 0.48f, inicioY + passo * 1.55f)
            desenharSlider(canvas, rectSliderEfeitos, volumeEfeitos / 100f, "$volumeEfeitos", tamanhoValor)
            zonasToque["sliderEfeitos"] = rectSliderEfeitos

            // 3. Tamanho Joystick
            val lbJoystick = if (idioma == "pt") "Tamanho Joystick" else "Joystick Size"
            desenharRotulo(canvas, lbJoystick, w * 0.04f, inicioY + passo * 2f, tamanhoRotulo)
            val rectSliderJoystick = RectF(w * 0.04f, inicioY + passo * 2.12f, w * 0.48f, inicioY + passo * 2.55f)
            val propJoystick = (tamanhoJoystick - 80f) / 80f
            desenharSlider(canvas, rectSliderJoystick, propJoystick, "${tamanhoJoystick}dp", tamanhoValor)
            zonasToque["sliderJoystick"] = rectSliderJoystick

            // 4. Posição Joystick
            val lbPosicao = if (idioma == "pt") "Posição Joystick" else "Joystick Side"
            desenharRotulo(canvas, lbPosicao, w * 0.04f, inicioY + passo * 3f, tamanhoRotulo)
            val lbEsq = if (idioma == "pt") "Esquerda" else "Left"
            val lbDir = if (idioma == "pt") "Direita" else "Right"
            val rectPosEsq = RectF(w * 0.04f, inicioY + passo * 3.12f, w * 0.24f, inicioY + passo * 3.55f)
            val rectPosDir = RectF(w * 0.26f, inicioY + passo * 3.12f, w * 0.48f, inicioY + passo * 3.55f)
            desenharBotaoOpcao(canvas, rectPosEsq, lbEsq, posicaoJoystick == "esquerda", tamanhoValor)
            desenharBotaoOpcao(canvas, rectPosDir, lbDir, posicaoJoystick == "direita", tamanhoValor)
            zonasToque["posEsquerda"] = rectPosEsq
            zonasToque["posDireita"] = rectPosDir

            // --- Coluna direita ---
            // 5. Alto Contraste
            val lbContraste = if (idioma == "pt") "Alto Contraste" else "High Contrast"
            desenharRotulo(canvas, lbContraste, w * 0.52f, inicioY, tamanhoRotulo)
            val rectToggleContraste = RectF(w * 0.52f, inicioY + passo * 0.12f, w * 0.72f, inicioY + passo * 0.55f)
            desenharToggle(canvas, rectToggleContraste, modoAltoContraste, tamanhoValor)
            zonasToque["toggleContraste"] = rectToggleContraste

            // 6. D-Pad
            val lbDPad = if (idioma == "pt") "Usar D-Pad" else "Use D-Pad"
            desenharRotulo(canvas, lbDPad, w * 0.52f, inicioY + passo, tamanhoRotulo)
            val rectToggleDPad = RectF(w * 0.52f, inicioY + passo * 1.12f, w * 0.72f, inicioY + passo * 1.55f)
            desenharToggle(canvas, rectToggleDPad, usarDPad, tamanhoValor)
            zonasToque["toggleDPad"] = rectToggleDPad

            // 7. Feedback Háptico (Requisito 12.5)
            val lbHaptico = if (idioma == "pt") "Feedback Háptico" else "Haptic Feedback"
            desenharRotulo(canvas, lbHaptico, w * 0.52f, inicioY + passo * 2f, tamanhoRotulo)
            val rectToggleHaptico = RectF(w * 0.52f, inicioY + passo * 2.12f, w * 0.72f, inicioY + passo * 2.55f)
            desenharToggle(canvas, rectToggleHaptico, feedbackHaptico, tamanhoValor)
            zonasToque["toggleHaptico"] = rectToggleHaptico

            // 8. Intensidade Háptica (Requisito 12.5)
            val lbIntensidade = if (idioma == "pt") "Intensidade Háptica" else "Haptic Intensity"
            desenharRotulo(canvas, lbIntensidade, w * 0.52f, inicioY + passo * 3f, tamanhoRotulo)
            val alphaIntensidade = if (feedbackHaptico) 255 else 100
            val niveis = listOf("1" to "haptico1", "2" to "haptico2", "3" to "haptico3")
            niveis.forEachIndexed { i, (rotulo, chave) ->
                val rx = w * 0.52f + i * (w * 0.075f)
                val rect = RectF(rx, inicioY + passo * 3.12f, rx + w * 0.065f, inicioY + passo * 3.55f)
                val selecionado = intensidadeHaptico == (i + 1)
                desenharBotaoOpcao(canvas, rect, rotulo, selecionado, tamanhoValor, alphaIntensidade)
                zonasToque[chave] = rect
            }

            // 9. Idioma (Requisito 12.4)
            val lbIdioma = if (idioma == "pt") "Idioma" else "Language"
            desenharRotulo(canvas, lbIdioma, w * 0.74f, inicioY + passo * 2f, tamanhoRotulo)
            val rectPt = RectF(w * 0.74f, inicioY + passo * 2.12f, w * 0.86f, inicioY + passo * 2.55f)
            val rectEn = RectF(w * 0.88f, inicioY + passo * 2.12f, w * 0.98f, inicioY + passo * 2.55f)
            desenharBotaoOpcao(canvas, rectPt, "PT", idioma == "pt", tamanhoValor)
            desenharBotaoOpcao(canvas, rectEn, "EN", idioma == "en", tamanhoValor)
            zonasToque["idiomapt"] = rectPt
            zonasToque["idiomaan"] = rectEn

            // --- Botões inferiores ---
            val alturaBotao = h * 0.10f
            val yBotoes = h - alturaBotao - h * 0.03f
            val lbSalvar = if (idioma == "pt") "Salvar" else "Save"
            val lbVoltar = if (idioma == "pt") "Cancelar" else "Cancel"
            val rectSalvar = RectF(w * 0.35f, yBotoes, w * 0.65f, yBotoes + alturaBotao)
            val rectVoltar = RectF(w * 0.02f, yBotoes, w * 0.22f, yBotoes + alturaBotao)
            desenharBotaoAcao(canvas, rectSalvar, lbSalvar, 0xFF1A3A00.toInt(), 0xFF4CAF50.toInt(), tamanhoValor)
            desenharBotaoAcao(canvas, rectVoltar, lbVoltar, 0xFF1E1E1E.toInt(), 0xFF555555.toInt(), tamanhoValor)
            zonasToque["salvar"] = rectSalvar
            zonasToque["voltar"] = rectVoltar
        }

        /** Desenha rótulo de seção. */
        private fun desenharRotulo(canvas: Canvas, texto: String, x: Float, y: Float, tamanho: Float) {
            tintaTexto.color = if (modoAltoContraste) Color.WHITE else 0xFF888888.toInt()
            tintaTexto.textSize = tamanho
            tintaTexto.textAlign = Paint.Align.LEFT
            canvas.drawText(texto, x, y, tintaTexto)
        }

        /** Desenha slider arrastável com trilha e polegar. */
        private fun desenharSlider(canvas: Canvas, rect: RectF, proporcao: Float, valor: String, tamanhoTexto: Float) {
            val cy = rect.centerY()
            val alturaTriha = rect.height() * 0.3f

            // Trilha de fundo
            tintaControle.color = if (modoAltoContraste) 0xFF444444.toInt() else 0xFF333333.toInt()
            canvas.drawRoundRect(
                RectF(rect.left, cy - alturaTriha / 2f, rect.right, cy + alturaTriha / 2f),
                alturaTriha / 2f, alturaTriha / 2f, tintaControle
            )

            // Trilha preenchida
            val xPreenchido = rect.left + proporcao * rect.width()
            tintaControle.color = if (modoAltoContraste) Color.YELLOW else 0xFFD4A017.toInt()
            canvas.drawRoundRect(
                RectF(rect.left, cy - alturaTriha / 2f, xPreenchido, cy + alturaTriha / 2f),
                alturaTriha / 2f, alturaTriha / 2f, tintaControle
            )

            // Polegar
            val raioPolegar = rect.height() * 0.5f
            canvas.drawCircle(xPreenchido, cy, raioPolegar, tintaControle)

            // Valor
            tintaTexto.color = if (modoAltoContraste) Color.WHITE else 0xFFCCCCCC.toInt()
            tintaTexto.textSize = tamanhoTexto
            tintaTexto.textAlign = Paint.Align.LEFT
            canvas.drawText(valor, rect.right + rect.width() * 0.04f, cy + tamanhoTexto * 0.35f, tintaTexto)
        }

        /** Desenha toggle on/off. */
        private fun desenharToggle(canvas: Canvas, rect: RectF, ligado: Boolean, tamanhoTexto: Float) {
            val corFundo = when {
                modoAltoContraste && ligado -> Color.YELLOW
                ligado -> 0xFF4CAF50.toInt()
                modoAltoContraste -> 0xFF444444.toInt()
                else -> 0xFF333333.toInt()
            }
            tintaControle.color = corFundo
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, tintaControle)

            // Círculo deslizante
            val cx = if (ligado) rect.right - rect.height() / 2f else rect.left + rect.height() / 2f
            tintaControle.color = Color.WHITE
            canvas.drawCircle(cx, rect.centerY(), rect.height() * 0.38f, tintaControle)

            // Rótulo
            val rotulo = if (ligado) (if (idioma == "pt") "Sim" else "On") else (if (idioma == "pt") "Não" else "Off")
            tintaTexto.color = if (modoAltoContraste) Color.WHITE else 0xFFCCCCCC.toInt()
            tintaTexto.textSize = tamanhoTexto
            tintaTexto.textAlign = Paint.Align.LEFT
            canvas.drawText(rotulo, rect.right + rect.width() * 0.08f, rect.centerY() + tamanhoTexto * 0.35f, tintaTexto)
        }

        /** Desenha botão de opção selecionável. */
        private fun desenharBotaoOpcao(
            canvas: Canvas, rect: RectF, rotulo: String,
            selecionado: Boolean, tamanhoTexto: Float, alpha: Int = 255
        ) {
            val corFundo = when {
                modoAltoContraste && selecionado -> 0xFF444400.toInt()
                selecionado -> 0xFF4A3000.toInt()
                else -> 0xFF1E1E1E.toInt()
            }
            val corBorda = when {
                modoAltoContraste && selecionado -> Color.YELLOW
                selecionado -> 0xFFD4A017.toInt()
                else -> 0xFF555555.toInt()
            }
            tintaControle.color = corFundo
            tintaControle.alpha = alpha
            canvas.drawRoundRect(rect, 8f, 8f, tintaControle)
            tintaControle.color = corBorda
            tintaControle.alpha = alpha
            tintaControle.style = Paint.Style.STROKE
            tintaControle.strokeWidth = if (selecionado) 2.5f else 1.5f
            canvas.drawRoundRect(rect, 8f, 8f, tintaControle)
            tintaControle.style = Paint.Style.FILL
            tintaControle.alpha = 255

            tintaTexto.color = if (modoAltoContraste) Color.WHITE else Color.WHITE
            tintaTexto.alpha = alpha
            tintaTexto.textSize = tamanhoTexto
            tintaTexto.textAlign = Paint.Align.CENTER
            canvas.drawText(rotulo, rect.centerX(), rect.centerY() + tamanhoTexto * 0.35f, tintaTexto)
            tintaTexto.alpha = 255
        }

        /** Desenha botão de ação (Salvar / Cancelar). */
        private fun desenharBotaoAcao(
            canvas: Canvas, rect: RectF, rotulo: String,
            corFundo: Int, corBorda: Int, tamanhoTexto: Float
        ) {
            tintaControle.color = corFundo
            canvas.drawRoundRect(rect, 12f, 12f, tintaControle)
            tintaControle.color = corBorda
            tintaControle.style = Paint.Style.STROKE
            tintaControle.strokeWidth = 2.5f
            canvas.drawRoundRect(rect, 12f, 12f, tintaControle)
            tintaControle.style = Paint.Style.FILL

            tintaTexto.color = Color.WHITE
            tintaTexto.textSize = tamanhoTexto * 1.1f
            tintaTexto.textAlign = Paint.Align.CENTER
            canvas.drawText(rotulo, rect.centerX(), rect.centerY() + tamanhoTexto * 0.38f, tintaTexto)
        }
    }
}
