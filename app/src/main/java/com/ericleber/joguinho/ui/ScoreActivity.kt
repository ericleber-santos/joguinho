package com.ericleber.joguinho.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Tela de Score exibida ao completar um Floor.
 *
 * Recebe dados via Intent extras e exibe:
 * - Número do andar, tempo, Maps, Slowdowns, ComboStreak, Score
 * - Comparação com recorde pessoal e animação "Novo Recorde!" (Req. 6.9, 10.6)
 * - Leaderboard local top 10 acessível via botão (Req. 10.5)
 * - Botões: "Próximo Andar", "Reiniciar Andar", "Salvar e Sair" (Req. 6.3)
 * - Botão de compartilhamento (Req. 6.7, 6.8)
 *
 * Toda a UI é desenhada via Canvas — sem XML.
 * Orientação forçada landscape (Req. 1.3).
 */
class ScoreActivity : Activity() {

    companion object {
        const val EXTRA_ANDAR = "floorNumber"
        const val EXTRA_TEMPO_MS = "totalTimeMs"
        const val EXTRA_MAPS = "mapsCount"
        const val EXTRA_SLOWDOWNS = "slowdownsCount"
        const val EXTRA_COMBO = "maxComboStreak"
        const val EXTRA_SCORE = "accumulatedScore"
        const val EXTRA_RECORDE_MS = "personalBestMs"
        const val EXTRA_LEADERBOARD = "leaderboard"
    }

    // Dados recebidos via Intent
    private var andar = 1
    private var tempoMs = 0L
    private var maps = 0
    private var slowdowns = 0
    private var combo = 0
    private var score = 0f
    private var recordeMs = 0L
    private var leaderboard = LongArray(0)

    // Novo recorde: tempo atual < recorde pessoal (ou sem recorde anterior)
    private var novoRecorde = false

    // Controle de exibição do leaderboard
    private var exibirLeaderboard = false

    // Animação "Novo Recorde!" — dura 2 segundos (Req. 10.6)
    private var inicioAnimacaoRecordeMs = 0L
    private val DURACAO_ANIMACAO_MS = 2000L

    private lateinit var scoreView: ScoreView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // Ler dados do Intent
        andar = intent.getIntExtra(EXTRA_ANDAR, 1)
        tempoMs = intent.getLongExtra(EXTRA_TEMPO_MS, 0L)
        maps = intent.getIntExtra(EXTRA_MAPS, 0)
        slowdowns = intent.getIntExtra(EXTRA_SLOWDOWNS, 0)
        combo = intent.getIntExtra(EXTRA_COMBO, 0)
        score = intent.getFloatExtra(EXTRA_SCORE, 0f)
        recordeMs = intent.getLongExtra(EXTRA_RECORDE_MS, 0L)
        leaderboard = intent.getLongArrayExtra(EXTRA_LEADERBOARD) ?: LongArray(0)

        // Verificar novo recorde (Req. 6.9, 10.6)
        novoRecorde = recordeMs == 0L || tempoMs < recordeMs
        if (novoRecorde) inicioAnimacaoRecordeMs = System.currentTimeMillis()

        scoreView = ScoreView(this)
        setContentView(scoreView)
    }

    // =========================================================================
    // Ações dos botões
    // =========================================================================

    /** Próximo Andar: retorna resultado para GameActivity gerar novo Floor (Req. 6.4) */
    private fun proximoAndar() {
        val resultado = Intent()
        resultado.putExtra("acao", "proximoAndar")
        setResult(RESULT_OK, resultado)
        finish()
    }

    /** Reiniciar Andar: retorna resultado para GameActivity regenerar com mesmo seed (Req. 6.5) */
    private fun reiniciarAndar() {
        val resultado = Intent()
        resultado.putExtra("acao", "reiniciarAndar")
        setResult(RESULT_OK, resultado)
        finish()
    }

    /** Salvar e Sair: retorna resultado para GameActivity persistir e voltar ao menu (Req. 6.6) */
    private fun salvarESair() {
        val resultado = Intent()
        resultado.putExtra("acao", "salvarESair")
        setResult(RESULT_OK, resultado)
        finish()
    }

    /** Compartilhar: gera imagem e abre seletor nativo do Android (Req. 6.7, 6.8) */
    private fun compartilhar() {
        val bitmap = gerarImagemCompartilhamento()
        val uri = salvarBitmapNoCache(bitmap)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartilhar resultado"))
    }

    /**
     * Gera imagem 1080x1080 com estatísticas para compartilhamento (Req. 6.7).
     * Inclui: nome do jogo, andar alcançado, tempo total acumulado, total de Maps.
     */
    private fun gerarImagemCompartilhamento(): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tinta = Paint().apply { isAntiAlias = true }

        // Fundo escuro
        tinta.color = 0xFF0D0D0D.toInt()
        canvas.drawRect(0f, 0f, 1080f, 1080f, tinta)

        // Borda dourada
        tinta.color = 0xFFD4A017.toInt()
        tinta.style = Paint.Style.STROKE
        tinta.strokeWidth = 12f
        canvas.drawRect(20f, 20f, 1060f, 1060f, tinta)
        tinta.style = Paint.Style.FILL

        // Título
        tinta.color = 0xFFD4A017.toInt()
        tinta.textSize = 90f
        tinta.textAlign = Paint.Align.CENTER
        canvas.drawText("Spike na Caverna", 540f, 160f, tinta)

        // Subtítulo
        tinta.color = 0xFF8B6914.toInt()
        tinta.textSize = 50f
        canvas.drawText("~ Resultado do Andar ~", 540f, 230f, tinta)

        // Andar
        tinta.color = Color.WHITE
        tinta.textSize = 80f
        canvas.drawText("Andar $andar", 540f, 380f, tinta)

        // Tempo
        tinta.color = 0xFFAAAAAA.toInt()
        tinta.textSize = 55f
        canvas.drawText("Tempo: ${formatarTempo(tempoMs)}", 540f, 480f, tinta)

        // Maps
        canvas.drawText("Maps percorridos: $maps", 540f, 560f, tinta)

        // Score
        tinta.color = 0xFFFFD700.toInt()
        tinta.textSize = 65f
        canvas.drawText("Score: ${score.toInt()}", 540f, 680f, tinta)

        // Novo recorde
        if (novoRecorde) {
            tinta.color = 0xFFFF6B35.toInt()
            tinta.textSize = 70f
            canvas.drawText("★ NOVO RECORDE! ★", 540f, 800f, tinta)
        }

        // Rodapé
        tinta.color = 0xFF555555.toInt()
        tinta.textSize = 38f
        canvas.drawText("Jogue você também!", 540f, 980f, tinta)

        return bitmap
    }

    /** Salva o bitmap em cache e retorna URI via FileProvider. */
    private fun salvarBitmapNoCache(bitmap: Bitmap): Uri {
        val arquivo = File(cacheDir, "compartilhamento_score.png")
        FileOutputStream(arquivo).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(this, "$packageName.provider", arquivo)
    }

    // =========================================================================
    // Utilitário de formatação de tempo
    // =========================================================================

    /** Formata tempo em milissegundos para mm:ss:ms */
    private fun formatarTempo(ms: Long): String {
        val minutos = ms / 60000
        val segundos = (ms % 60000) / 1000
        val centesimos = (ms % 1000) / 10
        return "%02d:%02d:%02d".format(minutos, segundos, centesimos)
    }

    // =========================================================================
    // View customizada — toda UI desenhada via Canvas
    // =========================================================================

    inner class ScoreView(contexto: Activity) : View(contexto) {

        private val tintaFundo = Paint().apply { isAntiAlias = true }
        private val tintaTexto = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        private val tintaBotao = Paint().apply { isAntiAlias = true }

        // Retângulos dos botões para detecção de toque
        private val retanguloBotoes = mutableMapOf<String, RectF>()

        init { agendarProximoFrame() }

        private fun agendarProximoFrame() {
            postDelayed({
                invalidate()
                agendarProximoFrame()
            }, 32L) // ~30fps suficiente para tela estática com animação
        }

        override fun onTouchEvent(evento: MotionEvent): Boolean {
            if (evento.action != MotionEvent.ACTION_DOWN) return true
            val x = evento.x
            val y = evento.y
            retanguloBotoes.forEach { (acao, rect) ->
                if (rect.contains(x, y)) {
                    when (acao) {
                        "proximoAndar" -> proximoAndar()
                        "reiniciarAndar" -> reiniciarAndar()
                        "salvarESair" -> salvarESair()
                        "compartilhar" -> compartilhar()
                        "leaderboard" -> {
                            exibirLeaderboard = !exibirLeaderboard
                            invalidate()
                        }
                        "fecharLeaderboard" -> {
                            exibirLeaderboard = false
                            invalidate()
                        }
                    }
                    return true
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            retanguloBotoes.clear()
            if (exibirLeaderboard) {
                desenharLeaderboard(canvas)
            } else {
                desenharScore(canvas)
            }
        }

        // =====================================================================
        // Tela principal de Score
        // =====================================================================

        private fun desenharScore(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()

            // Fundo
            tintaFundo.color = 0xFF0D0D0D.toInt()
            canvas.drawRect(0f, 0f, w, h, tintaFundo)

            // Faixa superior dourada
            tintaFundo.color = 0xFF1A1200.toInt()
            canvas.drawRect(0f, 0f, w, h * 0.18f, tintaFundo)

            // Título
            tintaTexto.color = 0xFFD4A017.toInt()
            tintaTexto.textSize = h * 0.10f
            canvas.drawText("Andar $andar Completo!", w / 2f, h * 0.13f, tintaTexto)

            // Layout em duas colunas: estatísticas à esquerda, botões à direita
            val colunaEsq = w * 0.28f
            val colunaDireita = w * 0.72f
            val inicioY = h * 0.24f
            val espacamento = h * 0.10f

            // --- Estatísticas ---
            val rotulos = listOf("Tempo", "Maps", "Slowdowns", "Combo Máx.", "Score")
            val valores = listOf(
                formatarTempo(tempoMs),
                maps.toString(),
                slowdowns.toString(),
                combo.toString(),
                score.toInt().toString()
            )

            rotulos.forEachIndexed { i, rotulo ->
                val y = inicioY + i * espacamento

                // Rótulo
                tintaTexto.color = 0xFF888888.toInt()
                tintaTexto.textSize = h * 0.045f
                tintaTexto.textAlign = Paint.Align.RIGHT
                canvas.drawText("$rotulo:", colunaEsq - w * 0.01f, y, tintaTexto)

                // Valor
                tintaTexto.color = if (rotulo == "Score") 0xFFFFD700.toInt() else Color.WHITE
                tintaTexto.textSize = h * 0.055f
                tintaTexto.textAlign = Paint.Align.LEFT
                canvas.drawText(valores[i], colunaEsq + w * 0.01f, y, tintaTexto)
            }
            tintaTexto.textAlign = Paint.Align.CENTER

            // --- Recorde pessoal (Req. 6.9) ---
            val yRecorde = inicioY + 5 * espacamento + h * 0.02f
            if (recordeMs > 0L) {
                tintaTexto.color = 0xFF888888.toInt()
                tintaTexto.textSize = h * 0.038f
                canvas.drawText("Recorde pessoal: ${formatarTempo(recordeMs)}", w * 0.28f, yRecorde, tintaTexto)
            }

            // --- Animação "Novo Recorde!" (Req. 10.6) ---
            val decorrido = System.currentTimeMillis() - inicioAnimacaoRecordeMs
            if (novoRecorde) {
                val emAnimacao = decorrido < DURACAO_ANIMACAO_MS
                val alpha = if (emAnimacao) {
                    // Pulsa durante a animação
                    val fase = (decorrido.toFloat() / DURACAO_ANIMACAO_MS * Math.PI * 4).toFloat()
                    ((Math.sin(fase.toDouble()) * 0.4 + 0.8) * 255).toInt().coerceIn(100, 255)
                } else 255

                tintaTexto.color = Color.argb(alpha, 255, 107, 53)
                tintaTexto.textSize = h * 0.065f
                canvas.drawText("★ NOVO RECORDE! ★", w * 0.28f, yRecorde + h * 0.08f, tintaTexto)

                // Partículas de celebração durante animação (Req. 10.6)
                if (emAnimacao) {
                    desenharParticulasCelebracao(canvas, w, h, decorrido)
                }
            }

            // --- Botões de ação (Req. 6.3) ---
            val larguraBotao = w * 0.22f
            val alturaBotao = h * 0.11f
            val xBotoes = colunaDireita
            val botoesAcao = listOf(
                "proximoAndar" to "Próximo Andar",
                "reiniciarAndar" to "Reiniciar Andar",
                "salvarESair" to "Salvar e Sair"
            )
            botoesAcao.forEachIndexed { i, (acao, rotulo) ->
                val yBotao = h * 0.25f + i * (alturaBotao + h * 0.04f)
                val rect = RectF(
                    xBotoes - larguraBotao / 2f, yBotao,
                    xBotoes + larguraBotao / 2f, yBotao + alturaBotao
                )
                retanguloBotoes[acao] = rect
                val corFundo = if (acao == "proximoAndar") 0xFF1A3A00.toInt() else 0xFF1E1E1E.toInt()
                val corBorda = if (acao == "proximoAndar") 0xFF4CAF50.toInt() else 0xFF555555.toInt()
                desenharBotao(canvas, rect, rotulo, corFundo, corBorda)
            }

            // --- Botão Leaderboard (Req. 10.5) ---
            val yLb = h * 0.25f + 3 * (alturaBotao + h * 0.04f)
            val rectLb = RectF(
                xBotoes - larguraBotao / 2f, yLb,
                xBotoes + larguraBotao / 2f, yLb + alturaBotao
            )
            retanguloBotoes["leaderboard"] = rectLb
            desenharBotao(canvas, rectLb, "Leaderboard", 0xFF1A1A3A.toInt(), 0xFF5555FF.toInt())

            // --- Botão Compartilhar (Req. 6.7) ---
            val yShare = h * 0.25f + 4 * (alturaBotao + h * 0.04f)
            val rectShare = RectF(
                xBotoes - larguraBotao / 2f, yShare,
                xBotoes + larguraBotao / 2f, yShare + alturaBotao
            )
            retanguloBotoes["compartilhar"] = rectShare
            desenharBotao(canvas, rectShare, "Compartilhar", 0xFF1A1A1A.toInt(), 0xFFD4A017.toInt())
        }

        /** Desenha partículas de celebração para animação de novo recorde (Req. 10.6). */
        private fun desenharParticulasCelebracao(canvas: Canvas, w: Float, h: Float, decorrido: Long) {
            val progresso = decorrido.toFloat() / DURACAO_ANIMACAO_MS
            val tintaParticula = Paint().apply { isAntiAlias = true }
            val cores = intArrayOf(0xFFFFD700.toInt(), 0xFFFF6B35.toInt(), 0xFF4CAF50.toInt(), 0xFF2196F3.toInt())
            for (i in 0..19) {
                val angulo = (i * 18.0 + progresso * 360.0) * Math.PI / 180.0
                val raio = progresso * w * 0.35f
                val px = w * 0.28f + (Math.cos(angulo) * raio).toFloat()
                val py = h * 0.55f + (Math.sin(angulo) * raio * 0.5f).toFloat()
                val alpha = ((1f - progresso) * 220).toInt().coerceIn(0, 255)
                tintaParticula.color = Color.argb(alpha,
                    Color.red(cores[i % cores.size]),
                    Color.green(cores[i % cores.size]),
                    Color.blue(cores[i % cores.size])
                )
                canvas.drawCircle(px, py, h * 0.012f, tintaParticula)
            }
        }

        // =====================================================================
        // Leaderboard local top 10 (Req. 10.5)
        // =====================================================================

        private fun desenharLeaderboard(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()

            // Fundo
            tintaFundo.color = 0xFF050510.toInt()
            canvas.drawRect(0f, 0f, w, h, tintaFundo)

            // Título
            tintaTexto.color = 0xFF5555FF.toInt()
            tintaTexto.textSize = h * 0.09f
            canvas.drawText("Leaderboard — Andar $andar", w / 2f, h * 0.12f, tintaTexto)

            if (leaderboard.isEmpty()) {
                tintaTexto.color = 0xFF666666.toInt()
                tintaTexto.textSize = h * 0.055f
                canvas.drawText("Nenhum tempo registrado ainda.", w / 2f, h / 2f, tintaTexto)
            } else {
                val inicioY = h * 0.22f
                val espacamento = h * 0.075f
                leaderboard.take(10).forEachIndexed { i, tempo ->
                    val y = inicioY + i * espacamento
                    val ehAtual = tempo == tempoMs

                    // Destaque para o tempo atual
                    if (ehAtual) {
                        tintaFundo.color = 0xFF1A1A00.toInt()
                        canvas.drawRoundRect(
                            RectF(w * 0.15f, y - espacamento * 0.7f, w * 0.85f, y + espacamento * 0.15f),
                            8f, 8f, tintaFundo
                        )
                    }

                    // Posição
                    val corPosicao = when (i) {
                        0 -> 0xFFFFD700.toInt()  // Ouro
                        1 -> 0xFFCCCCCC.toInt()  // Prata
                        2 -> 0xFFCD7F32.toInt()  // Bronze
                        else -> 0xFF888888.toInt()
                    }
                    tintaTexto.color = corPosicao
                    tintaTexto.textSize = h * 0.055f
                    tintaTexto.textAlign = Paint.Align.RIGHT
                    canvas.drawText("${i + 1}.", w * 0.28f, y, tintaTexto)

                    // Tempo
                    tintaTexto.color = if (ehAtual) 0xFFFFD700.toInt() else Color.WHITE
                    tintaTexto.textAlign = Paint.Align.LEFT
                    val sufixo = if (ehAtual) "  ◀ você" else ""
                    canvas.drawText(formatarTempo(tempo) + sufixo, w * 0.30f, y, tintaTexto)
                }
                tintaTexto.textAlign = Paint.Align.CENTER
            }

            // Botão fechar
            val rectFechar = RectF(w * 0.38f, h * 0.86f, w * 0.62f, h * 0.96f)
            retanguloBotoes["fecharLeaderboard"] = rectFechar
            desenharBotao(canvas, rectFechar, "Fechar", 0xFF1E1E1E.toInt(), 0xFF555555.toInt())
        }

        // =====================================================================
        // Utilitário de botão
        // =====================================================================

        private fun desenharBotao(canvas: Canvas, rect: RectF, rotulo: String, corFundo: Int, corBorda: Int) {
            tintaBotao.color = corFundo
            tintaBotao.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, 12f, 12f, tintaBotao)

            tintaBotao.color = corBorda
            tintaBotao.style = Paint.Style.STROKE
            tintaBotao.strokeWidth = 2.5f
            canvas.drawRoundRect(rect, 12f, 12f, tintaBotao)
            tintaBotao.style = Paint.Style.FILL

            tintaTexto.color = Color.WHITE
            tintaTexto.textSize = rect.height() * 0.36f
            tintaTexto.textAlign = Paint.Align.CENTER
            canvas.drawText(rotulo, rect.centerX(), rect.centerY() + tintaTexto.textSize * 0.35f, tintaTexto)
        }
    }
}
