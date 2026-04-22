package com.ericleber.joguinho.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.ericleber.joguinho.biome.Biome
import com.ericleber.joguinho.biome.BIOME_PALETTES
import com.ericleber.joguinho.persistence.AppDatabase
import com.ericleber.joguinho.persistence.PersistenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela principal do jogo — Menu inicial.
 *
 * Exibe animação de introdução (Hero e Spike entrando na caverna) por até 4 segundos,
 * com skip por toque. Após a animação, exibe o menu principal com as opções:
 * Continuar, Novo Jogo, Configurações, Galeria de Biomas, Recorde Pessoal e Modo DEV.
 *
 * Toda a UI é desenhada via Canvas em uma View customizada — sem XML.
 * Design State of the Art (Pro Max) com Cubic Ease-Out e partículas decorativas.
 *
 * Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5, 10.1, 10.7
 */
class MainMenuActivity : AppCompatActivity() {

    private lateinit var viewModel: GameViewModel
    private lateinit var menuView: MenuView

    // Estado da tela
    private var temSaveExistente = false
    private var recordePessoal = 0
    private var biomasDesbloqueados: List<Biome> = emptyList()

    // Controle de tela
    private var exibindoGaleria = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Forçar orientação landscape (Requisito 1.3)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Tela cheia sem barra de status
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // Inicializar ViewModel e PersistenceManager
        viewModel = ViewModelProvider(this)[GameViewModel::class.java]
        val banco = AppDatabase.getInstance(applicationContext)
        val gerenciadorPersistencia = PersistenceManager(applicationContext, banco)

        // Criar e exibir a View customizada
        menuView = MenuView(this)
        setContentView(menuView)

        // Verificar save existente e carregar dados (Requisito 1.2, 10.1)
        carregarDadosIniciais(gerenciadorPersistencia)
    }

    /**
     * Carrega dados iniciais: verifica save existente, recorde pessoal e biomas desbloqueados.
     * Requisitos: 1.2, 10.1, 10.7
     */
    private fun carregarDadosIniciais(gerenciadorPersistencia: PersistenceManager) {
        viewModel.verificarSaveExistente { temSave ->
            temSaveExistente = temSave
            menuView.atualizarEstado(temSave)
        }

        // Carregar recorde pessoal e biomas desbloqueados do save mais recente
        lifecycleScope.launch(Dispatchers.IO) {
            val resultado = gerenciadorPersistencia.restaurar()
            if (resultado is PersistenceManager.RestoreResult.Sucesso) {
                val estado = resultado.estado
                // Recorde pessoal = maior floor alcançado (Requisito 10.1)
                val maiorFloor = estado.personalBests.keys.maxOrNull() ?: estado.floorNumber
                val novoRecorde = maiorFloor
                val novosBiomas = Biome.entries.filter { it.floorRange.first <= maiorFloor }
                withContext(Dispatchers.Main) {
                    recordePessoal = novoRecorde
                    biomasDesbloqueados = novosBiomas
                    menuView.atualizarRecordeEBiomas(recordePessoal, biomasDesbloqueados)
                }
            }
        }
    }

    /** Navega para GameActivity iniciando novo jogo. */
    private fun iniciarNovoJogo() {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("novoJogo", true)
        startActivity(intent)
    }

    /** Navega para GameActivity continuando partida salva. */
    private fun continuarJogo() {
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("novoJogo", false)
        startActivity(intent)
    }

    /** Navega para SettingsActivity (Requisito 12.1). */
    private fun abrirConfiguracoes() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    /** Alterna exibição da galeria de biomas. */
    private fun alternarGaleria() {
        exibindoGaleria = !exibindoGaleria
        menuView.exibirGaleria(exibindoGaleria)
    }

    /** Abre um Modal Nativo para iniciar o jogo em um local específico (Modo DEV). */
    private fun abrirModoDev() {
        val biomasNomes = Biome.entries.map { it.displayName }.toTypedArray()
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }
        val floorInput = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Andar Inicial (Ex: 15)"
            textSize = 18f
        }
        val textBioma = android.widget.TextView(this).apply {
            text = "Selecione o Bioma:"
            textSize = 14f
            setPadding(0, 30, 0, 10)
        }
        val biomeSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainMenuActivity, android.R.layout.simple_spinner_dropdown_item, biomasNomes)
        }
        layout.addView(floorInput)
        layout.addView(textBioma)
        layout.addView(biomeSpinner)

        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("⚙️ Modo DEV (Teste)")
            .setView(layout)
            .setPositiveButton("Iniciar Jogo") { _, _ ->
                val floorStr = floorInput.text.toString()
                val floor = if (floorStr.isNotEmpty()) floorStr.toInt() else 1
                val biome = Biome.entries[biomeSpinner.selectedItemPosition]
                
                val intent = Intent(this, GameActivity::class.java).apply {
                    putExtra("novoJogo", true)
                    putExtra("devMode", true)
                    putExtra("devFloor", floor)
                    putExtra("devBiome", biome.name)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =========================================================================
    // View customizada — toda UI desenhada via Canvas
    // =========================================================================

    /**
     * View que gerencia a animação de introdução e o menu principal.
     * Toda a renderização é feita via Canvas sem XML.
     */
    inner class MenuView(contexto: AppCompatActivity) : View(contexto) {

        // --- Animação de introdução ---
        private val DURACAO_INTRO_MS = 4000L
        private var emIntro = true
        private var inicioIntroMs = System.currentTimeMillis()
        private var progressoIntro = 0f  // 0.0 a 1.0

        // --- Estado do menu ---
        private var temSave = false
        private var recorde = 0
        private var biomas: List<Biome> = emptyList()
        private var exibirGaleriaAtiva = false

        // --- Animação de partículas da galeria ---
        private var frameGaleria = 0
        private var ultimoFrameMs = System.currentTimeMillis()
        
        // --- Interatividade de botões ---
        private val retanguloBotoes = mutableMapOf<String, RectF>()

        // --- Tintas reutilizáveis ---
        private val tintaFundo = Paint().apply { isAntiAlias = true }
        private val tintaTexto = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        private val tintaBotao = Paint().apply { isAntiAlias = true }
        private val tintaDestaque = Paint().apply {
            isAntiAlias = true
            color = 0xFFD4A017.toInt()
        }
        private val tintaPersonagem = Paint().apply { isAntiAlias = true }

        // --- Tinta de sombra para os botões ---
        private val tintaSombra = Paint().apply {
            isAntiAlias = true
            color = Color.argb(120, 0, 0, 0)
        }

        // --- Partículas Decorativas de Fundo ---
        inner class MenuParticle(var x: Float, var y: Float, var speedY: Float, var size: Float, var alpha: Int)
        private val particles = List(40) { 
            MenuParticle(
                x = (Math.random() * 3000).toFloat(),
                y = (Math.random() * 2000).toFloat(),
                speedY = (Math.random() * 2f + 0.5f).toFloat(),
                size = (Math.random() * 4f + 2f).toFloat(),
                alpha = (Math.random() * 150 + 50).toInt()
            ) 
        }

        init {
            // Iniciar loop de animação
            agendarProximoFrame()
        }

        fun atualizarEstado(saveExiste: Boolean) {
            temSave = saveExiste
            invalidate()
        }

        fun atualizarRecordeEBiomas(novoRecorde: Int, novosBiomas: List<Biome>) {
            recorde = novoRecorde
            biomas = novosBiomas
            invalidate()
        }

        fun exibirGaleria(ativo: Boolean) {
            exibirGaleriaAtiva = ativo
            invalidate()
        }

        private fun agendarProximoFrame() {
            postDelayed({
                val agora = System.currentTimeMillis()
                if (emIntro) {
                    progressoIntro = ((agora - inicioIntroMs) / DURACAO_INTRO_MS.toFloat()).coerceIn(0f, 1f)
                    if (progressoIntro >= 1f) emIntro = false
                }
                
                // Animar partículas de fundo flutuando pra cima
                particles.forEach { p ->
                    p.y -= p.speedY
                    if (p.y < -10) {
                        p.y = height.toFloat() + 10f
                        p.x = (Math.random() * width).toFloat()
                    }
                }

                // Avançar frame da galeria
                val deltaTempo = agora - ultimoFrameMs
                if (deltaTempo >= 100) {
                    frameGaleria++
                    ultimoFrameMs = agora
                }
                invalidate()
                agendarProximoFrame()
            }, 16L) // ~60fps
        }

        override fun onTouchEvent(evento: MotionEvent): Boolean {
            if (evento.action != MotionEvent.ACTION_DOWN) return true

            // Skip da animação de introdução (Requisito 1.4)
            if (emIntro) {
                emIntro = false
                progressoIntro = 1f
                invalidate()
                return true
            }

            val x = evento.x
            val y = evento.y

            // Verificar toque nos botões do menu
            if (exibirGaleriaAtiva) {
                // Toque fora da galeria fecha ela
                retanguloBotoes["fecharGaleria"]?.let { rect ->
                    if (rect.contains(x, y)) {
                        alternarGaleria()
                        return true
                    }
                }
                // Qualquer toque fora fecha a galeria
                alternarGaleria()
                return true
            }

            retanguloBotoes.forEach { (acao, rect) ->
                if (rect.contains(x, y)) {
                    when (acao) {
                        "continuar" -> if (temSave) continuarJogo()
                        "novoJogo" -> iniciarNovoJogo()
                        "configuracoes" -> abrirConfiguracoes()
                        "galeria" -> alternarGaleria()
                        "recorde" -> { /* apenas exibe informação */ }
                        "modoDev" -> abrirModoDev()
                    }
                    return true
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (emIntro) {
                desenharIntro(canvas)
            } else if (exibirGaleriaAtiva) {
                desenharGaleria(canvas)
            } else {
                desenharMenu(canvas)
            }
        }

        // =====================================================================
        // Animação de introdução (Requisito 1.1, 1.4)
        // =====================================================================

        /**
         * Desenha a animação de introdução: Hero e Spike entrando na caverna.
         * Duração máxima de 4 segundos, pulável por toque.
         */
        private fun desenharIntro(canvas: Canvas) {
            val largura = width.toFloat()
            val altura = height.toFloat()

            // Fundo escuro da caverna
            tintaFundo.color = 0xFF0A0A0A.toInt()
            canvas.drawRect(0f, 0f, largura, altura, tintaFundo)

            // Silhueta da entrada da caverna (arco)
            tintaFundo.color = 0xFF1A1008.toInt()
            val centroX = largura / 2f
            val centroY = altura / 2f
            val raioArco = altura * 0.45f
            canvas.drawCircle(centroX, centroY + raioArco * 0.3f, raioArco, tintaFundo)

            // Paredes da caverna (laterais)
            tintaFundo.color = 0xFF2C1F14.toInt()
            canvas.drawRect(0f, 0f, centroX - raioArco * 0.7f, altura, tintaFundo)
            canvas.drawRect(centroX + raioArco * 0.7f, 0f, largura, altura, tintaFundo)

            // Efeito de luz da caverna
            // Usando Cubic Ease-Out para suavidade na entrada e luz
            val t = progressoIntro
            val easeOut = 1f - Math.pow(1.0 - t.toDouble(), 3.0).toFloat()
            
            val alphaLuz = (easeOut * 180).toInt().coerceIn(0, 180)
            tintaFundo.color = Color.argb(alphaLuz, 255, 140, 0)
            canvas.drawCircle(centroX, centroY, raioArco * 0.6f, tintaFundo)

            // Partículas de poeira da caverna (brilho suave)
            tintaFundo.color = Color.argb(100, 255, 200, 100)
            particles.forEach { p ->
                if (p.x in (centroX - raioArco)..(centroX + raioArco)) {
                    canvas.drawCircle(p.x, p.y, p.size, tintaFundo)
                }
            }

            // Hero entrando da esquerda (Requisito 1.1)
            val posHeroX = -largura * 0.15f + easeOut * (centroX - largura * 0.08f)
            val posHeroY = centroY + altura * 0.05f
            desenharHeroSimples(canvas, posHeroX, posHeroY, altura * 0.12f)

            // Spike entrando da direita (Requisito 1.1)
            val posSpikeX = largura * 1.15f - easeOut * (largura * 0.15f + centroX - largura * 0.08f)
            val posSpikeY = centroY + altura * 0.05f
            desenharSpikeSimples(canvas, posSpikeX, posSpikeY, altura * 0.10f)

            // Título do jogo com fade-in
            val alphaTexto = (easeOut * 255).toInt().coerceIn(0, 255)
            tintaTexto.color = Color.argb(alphaTexto, 212, 160, 23)
            tintaTexto.textSize = altura * 0.10f
            tintaTexto.setShadowLayer(8f, 0f, 4f, Color.BLACK)
            canvas.drawText("Spike na Caverna", centroX, altura * 0.18f, tintaTexto)
            tintaTexto.clearShadowLayer()

            // Dica de skip
            tintaTexto.color = Color.argb((alphaTexto * 0.6f).toInt(), 255, 255, 255)
            tintaTexto.textSize = altura * 0.04f
            canvas.drawText("Toque para pular", centroX, altura * 0.92f, tintaTexto)
        }

        /** Desenha representação simples do Hero via Canvas. */
        private fun desenharHeroSimples(canvas: Canvas, x: Float, y: Float, tamanho: Float) {
            // Corpo (azul)
            tintaPersonagem.color = 0xFF1565C0.toInt()
            canvas.drawRoundRect(
                RectF(x - tamanho * 0.3f, y - tamanho * 0.5f, x + tamanho * 0.3f, y + tamanho * 0.5f),
                tamanho * 0.1f, tamanho * 0.1f, tintaPersonagem
            )
            // Cabeça
            tintaPersonagem.color = 0xFFFFCC80.toInt()
            canvas.drawCircle(x, y - tamanho * 0.65f, tamanho * 0.22f, tintaPersonagem)
            // Capacete
            tintaPersonagem.color = 0xFF0D47A1.toInt()
            canvas.drawArc(
                RectF(x - tamanho * 0.25f, y - tamanho * 0.9f, x + tamanho * 0.25f, y - tamanho * 0.43f),
                180f, 180f, true, tintaPersonagem
            )
        }

        /** Desenha representação simples do Spike via Canvas. */
        private fun desenharSpikeSimples(canvas: Canvas, x: Float, y: Float, tamanho: Float) {
            // Corpo (laranja)
            tintaPersonagem.color = 0xFFE65100.toInt()
            canvas.drawRoundRect(
                RectF(x - tamanho * 0.28f, y - tamanho * 0.45f, x + tamanho * 0.28f, y + tamanho * 0.45f),
                tamanho * 0.1f, tamanho * 0.1f, tintaPersonagem
            )
            // Cabeça
            tintaPersonagem.color = 0xFFFFCC80.toInt()
            canvas.drawCircle(x, y - tamanho * 0.6f, tamanho * 0.20f, tintaPersonagem)
            // Espinhos no topo
            tintaPersonagem.color = 0xFFBF360C.toInt()
            for (i in -2..2) {
                canvas.drawLine(
                    x + i * tamanho * 0.08f, y - tamanho * 0.78f,
                    x + i * tamanho * 0.04f, y - tamanho * 0.95f,
                    tintaPersonagem.apply { strokeWidth = tamanho * 0.04f }
                )
            }
        }

        // =====================================================================
        // Menu principal (Requisito 1.2, 1.3)
        // =====================================================================

        /**
         * Desenha o menu principal com todos os botões.
         * "Continuar" é destacado se houver save existente (Requisito 1.2).
         */
        private fun desenharMenu(canvas: Canvas) {
            val largura = width.toFloat()
            val altura = height.toFloat()
            retanguloBotoes.clear()

            // Fundo gradiente da caverna
            tintaFundo.color = 0xFF0D0D0D.toInt()
            canvas.drawRect(0f, 0f, largura, altura, tintaFundo)

            // Decoração de fundo — silhueta de caverna
            tintaFundo.color = 0xFF1A1008.toInt()
            canvas.drawRect(0f, altura * 0.6f, largura, altura, tintaFundo)

            // Partículas cintilantes no fundo
            particles.forEach { p ->
                tintaFundo.color = Color.argb(p.alpha, 255, 220, 150)
                canvas.drawCircle(p.x, p.y, p.size, tintaFundo)
            }

            // Título com sombra intensa (Premium)
            tintaTexto.color = 0xFFD4A017.toInt()
            tintaTexto.textSize = altura * 0.12f
            tintaTexto.setShadowLayer(12f, 0f, 6f, Color.argb(200, 0, 0, 0))
            canvas.drawText("Spike na Caverna", largura / 2f, altura * 0.16f, tintaTexto)
            tintaTexto.clearShadowLayer()

            // Subtítulo decorativo
            tintaTexto.color = 0xFF8B6914.toInt()
            tintaTexto.textSize = altura * 0.04f
            canvas.drawText("~ Explorando a Caverna ~", largura / 2f, altura * 0.26f, tintaTexto)

            // Recorde pessoal no canto superior direito (Requisito 10.1)
            tintaTexto.color = 0xFFFFD700.toInt()
            tintaTexto.textSize = altura * 0.038f
            tintaTexto.textAlign = Paint.Align.RIGHT
            val textoRecorde = if (recorde > 0) "⭐ Recorde: Andar $recorde" else "⭐ Recorde: —"
            canvas.drawText(textoRecorde, largura - largura * 0.02f, altura * 0.07f, tintaTexto)
            tintaTexto.textAlign = Paint.Align.CENTER

            // Botões do menu
            val centroX = largura / 2f
            val larguraBotao = largura * 0.32f
            val alturaBotao = altura * 0.11f
            val espacamento = altura * 0.135f
            val inicioY = altura * 0.35f

            val botoes = listOf(
                Triple("continuar", "Continuar", temSave),
                Triple("novoJogo", "Novo Jogo", false),
                Triple("galeria", "Galeria de Biomas", false),
                Triple("configuracoes", "Configurações", false),
                Triple("modoDev", "Modo DEV (Teste)", false),
                Triple("recorde", "Recorde Pessoal", false)
            )

            botoes.forEachIndexed { indice, (acao, rotulo, destacado) ->
                val topoY = inicioY + indice * espacamento
                val rect = RectF(
                    centroX - larguraBotao / 2f,
                    topoY,
                    centroX + larguraBotao / 2f,
                    topoY + alturaBotao
                )
                retanguloBotoes[acao] = rect
                desenharBotao(canvas, rect, rotulo, acao, destacado, acao == "continuar" && !temSave)
            }
        }

        /**
         * Desenha um botão do menu com estilo de caverna.
         *
         * @param desabilitado true se o botão deve aparecer acinzentado (ex: Continuar sem save)
         */
        private fun desenharBotao(
            canvas: Canvas,
            rect: RectF,
            rotulo: String,
            acao: String,
            destacado: Boolean,
            desabilitado: Boolean
        ) {
            val isAcaoEspecial = acao == "modoDev" || acao == "recorde"
            
            // Sombra do botão (Pro Max)
            val cornerRadius = 16f
            canvas.drawRoundRect(
                rect.left + 4f, rect.top + 6f, 
                rect.right + 4f, rect.bottom + 6f, 
                cornerRadius, cornerRadius, tintaSombra
            )

            // Fundo do botão com visual Glass/Moderno
            tintaBotao.color = when {
                desabilitado -> 0xFF2A2A2A.toInt()
                destacado -> 0xFF4A3000.toInt()
                isAcaoEspecial -> 0xFF151A22.toInt() // cor diferente para funções não-core
                else -> 0xFF1C1C1C.toInt()
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, tintaBotao)

            // Borda do botão
            tintaBotao.color = when {
                desabilitado -> 0xFF444444.toInt()
                destacado -> 0xFFD4A017.toInt()
                isAcaoEspecial -> 0xFF405060.toInt()
                else -> 0xFF555555.toInt()
            }
            tintaBotao.style = Paint.Style.STROKE
            tintaBotao.strokeWidth = if (destacado) 4f else 2f
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, tintaBotao)
            tintaBotao.style = Paint.Style.FILL

            // Texto do botão
            tintaTexto.color = when {
                desabilitado -> 0xFF666666.toInt()
                destacado -> 0xFFFFD700.toInt()
                isAcaoEspecial -> 0xFFA0C0D0.toInt()
                else -> 0xFFEEEEEE.toInt()
            }
            tintaTexto.textSize = rect.height() * 0.38f
            tintaTexto.setShadowLayer(4f, 0f, 2f, Color.BLACK)
            canvas.drawText(rotulo, rect.centerX(), rect.centerY() + tintaTexto.textSize * 0.35f, tintaTexto)
            tintaTexto.clearShadowLayer()

            // Indicador de destaque (save existente)
            if (destacado) {
                tintaDestaque.color = 0xFFD4A017.toInt()
                tintaDestaque.textSize = rect.height() * 0.30f
                tintaTexto.textAlign = Paint.Align.LEFT
                canvas.drawText("▶", rect.left + 10f, rect.centerY() + tintaDestaque.textSize * 0.35f, tintaDestaque)
                tintaTexto.textAlign = Paint.Align.CENTER
            }
        }

        // =====================================================================
        // Galeria de Biomas (Requisito 10.7)
        // =====================================================================

        /**
         * Desenha a galeria de biomas desbloqueados com cenas estáticas animadas.
         * Requisito 10.7
         */
        private fun desenharGaleria(canvas: Canvas) {
            val largura = width.toFloat()
            val altura = height.toFloat()

            // Fundo escuro
            tintaFundo.color = Color.argb(230, 5, 5, 10)
            canvas.drawRect(0f, 0f, largura, altura, tintaFundo)

            // Título da galeria
            tintaTexto.color = 0xFFD4A017.toInt()
            tintaTexto.textSize = altura * 0.08f
            canvas.drawText("Galeria de Biomas", largura / 2f, altura * 0.12f, tintaTexto)

            if (biomas.isEmpty()) {
                tintaTexto.color = 0xFF888888.toInt()
                tintaTexto.textSize = altura * 0.05f
                canvas.drawText("Nenhum bioma desbloqueado ainda.", largura / 2f, altura / 2f, tintaTexto)
            } else {
                // Grade de biomas desbloqueados
                val colunas = 3
                val margemH = largura * 0.04f
                val margemV = altura * 0.18f
                val espacoH = (largura - margemH * 2) / colunas
                val espacoV = (altura - margemV) / 2f

                biomas.forEachIndexed { indice, bioma ->
                    val coluna = indice % colunas
                    val linha = indice / colunas
                    val centroX = margemH + coluna * espacoH + espacoH / 2f
                    val centroY = margemV + linha * espacoV + espacoV * 0.4f
                    val raio = minOf(espacoH, espacoV) * 0.35f

                    desenharCenaBioma(canvas, bioma, centroX, centroY, raio)

                    // Nome do bioma
                    tintaTexto.color = 0xFFEEEEEE.toInt()
                    tintaTexto.textSize = altura * 0.035f
                    canvas.drawText(bioma.displayName, centroX, centroY + raio + altura * 0.05f, tintaTexto)
                }
            }

            // Botão fechar
            val rectFechar = RectF(largura * 0.4f, altura * 0.88f, largura * 0.6f, altura * 0.97f)
            retanguloBotoes["fecharGaleria"] = rectFechar
            tintaBotao.color = 0xFF333333.toInt()
            canvas.drawRoundRect(rectFechar, 10f, 10f, tintaBotao)
            tintaTexto.color = 0xFFEEEEEE.toInt()
            tintaTexto.textSize = rectFechar.height() * 0.4f
            canvas.drawText("Fechar", rectFechar.centerX(), rectFechar.centerY() + tintaTexto.textSize * 0.35f, tintaTexto)

            // Dica de toque
            tintaTexto.color = 0xFF666666.toInt()
            tintaTexto.textSize = altura * 0.033f
            canvas.drawText("Toque em qualquer lugar para fechar", largura / 2f, altura * 0.85f, tintaTexto)
        }

        /**
         * Desenha uma cena estática animada de um bioma usando as cores da paleta.
         * A animação é feita variando elementos com base em [frameGaleria].
         */
        private fun desenharCenaBioma(canvas: Canvas, bioma: Biome, cx: Float, cy: Float, raio: Float) {
            val paleta = BIOME_PALETTES[bioma] ?: return

            // Fundo circular do bioma
            tintaFundo.color = paleta.backgroundColor
            canvas.drawCircle(cx, cy, raio, tintaFundo)

            // Chão
            tintaFundo.color = paleta.floorColor
            canvas.drawRect(cx - raio, cy + raio * 0.2f, cx + raio, cy + raio, tintaFundo)

            // Paredes laterais
            tintaFundo.color = paleta.wallColor
            canvas.drawRect(cx - raio, cy - raio, cx - raio * 0.6f, cy + raio, tintaFundo)
            canvas.drawRect(cx + raio * 0.6f, cy - raio, cx + raio, cy + raio, tintaFundo)

            // Luz ambiente animada (pulsa com frameGaleria)
            val pulsacao = (Math.sin(frameGaleria * 0.15) * 0.3 + 0.7).toFloat()
            val alphaLuz = (pulsacao * 80).toInt().coerceIn(20, 100)
            tintaFundo.color = Color.argb(alphaLuz,
                Color.red(paleta.ambientLight),
                Color.green(paleta.ambientLight),
                Color.blue(paleta.ambientLight)
            )
            canvas.drawCircle(cx, cy - raio * 0.2f, raio * 0.5f, tintaFundo)

            // Partículas animadas
            val alphaParticula = (pulsacao * 200).toInt().coerceIn(100, 220)
            tintaFundo.color = Color.argb(alphaParticula,
                Color.red(paleta.particleColor),
                Color.green(paleta.particleColor),
                Color.blue(paleta.particleColor)
            )
            for (i in 0..2) {
                val angulo = (frameGaleria * 0.08 + i * 2.1).toFloat()
                val px = cx + (Math.cos(angulo.toDouble()) * raio * 0.35).toFloat()
                val py = cy + (Math.sin(angulo.toDouble()) * raio * 0.25).toFloat()
                canvas.drawCircle(px, py, raio * 0.06f, tintaFundo)
            }

            // Borda do bioma com cor de destaque
            tintaBotao.color = paleta.accentColor
            tintaBotao.style = Paint.Style.STROKE
            tintaBotao.strokeWidth = 3f
            canvas.drawCircle(cx, cy, raio, tintaBotao)
            tintaBotao.style = Paint.Style.FILL

            // Clip circular para conter o conteúdo
            // (desenhamos por cima com fundo transparente para simular clip)
        }
    }
}
