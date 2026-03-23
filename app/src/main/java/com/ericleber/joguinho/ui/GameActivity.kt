package com.ericleber.joguinho.ui

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ericleber.joguinho.audio.AudioManager
import com.ericleber.joguinho.character.Spike
import com.ericleber.joguinho.character.SpikeAI
import com.ericleber.joguinho.core.GameLoop
import com.ericleber.joguinho.core.Logger
import com.ericleber.joguinho.persistence.AppDatabase
import com.ericleber.joguinho.persistence.PersistenceManager
import com.ericleber.joguinho.renderer.CharacterRenderer
import com.ericleber.joguinho.renderer.GameSurfaceView
import com.ericleber.joguinho.renderer.HudRenderer
import com.ericleber.joguinho.renderer.ParticleSystem
import com.ericleber.joguinho.renderer.Renderer
import com.ericleber.joguinho.renderer.SpriteCache
import com.ericleber.joguinho.renderer.TileRenderer
import com.ericleber.joguinho.update.ReengagementWorker
import java.util.concurrent.TimeUnit

/**
 * Activity principal do jogo — ponto de entrada da sessão de jogo.
 *
 * Responsabilidades:
 * - Forçar modo landscape (Requisito 1.3, 13.6)
 * - Instanciar e conectar GameSurfaceView, GameLoop, AudioManager e PersistenceManager
 * - Delegar controle de ciclo de vida ao GameViewModel (Requisitos 21.1–21.5)
 * - Respeitar limites de tempo: ≤100ms para pausar GameLoop/Áudio, ≤500ms para salvar (Requisitos 18.1, 18.2)
 * - Tratar resultado da ScoreActivity (Requisito 6.3)
 *
 * Todo texto, comentários e nomes de métodos em Português do Brasil (Requisito 22.7).
 */
class GameActivity : AppCompatActivity() {

    companion object {
        private const val CODIGO_SCORE = 1001
        private const val CHAVE_NUMERO_ANDAR = "numeroAndar"
        private const val CHAVE_INDICE_MAPA = "indiceMapa"
    }

    private lateinit var viewModel: GameViewModel
    private lateinit var superficieJogo: GameSurfaceView
    private lateinit var gerenciadorAudio: AudioManager

    // =========================================================================
    // Ciclo de vida
    // =========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Forçar modo landscape e tela cheia (Requisitos 1.3, 13.6)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Instanciar dependências
        gerenciadorAudio = AudioManager(this)
        val banco = AppDatabase.getInstance(applicationContext)
        val gerenciadorPersistencia = PersistenceManager(applicationContext, banco)

        // Instanciar GameSurfaceView e Renderer
        superficieJogo = GameSurfaceView(this)
        val renderer = Renderer(
            spriteCache = SpriteCache(),
            tileRenderer = TileRenderer(),
            characterRenderer = CharacterRenderer(),
            particleSystem = ParticleSystem(),
            hudRenderer = HudRenderer()
        )
        superficieJogo.renderer = renderer

        // Inicializar ViewModel antes de criar o GameLoop (precisa do gameState)
        viewModel = ViewModelProvider(this)[GameViewModel::class.java]

        // Instanciar GameLoop
        val spike = Spike()
        val spikeAI = SpikeAI(spike)
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        val gameLoop = GameLoop(
            gameState = viewModel.gameState,
            spikeAI = spikeAI,
            powerManager = powerManager
        )
        gameLoop.onRender = { superficieJogo.drawFrame() }

        viewModel.inicializar(this, gameLoop, gerenciadorPersistencia, gerenciadorAudio)
        superficieJogo.gameState = viewModel.gameState

        setContentView(superficieJogo)

        // Restaurar estado do Bundle se disponível (rotação de tela, etc.)
        if (savedInstanceState != null) {
            viewModel.gameState.floorNumber = savedInstanceState.getInt(CHAVE_NUMERO_ANDAR, 1)
            viewModel.gameState.mapIndex = savedInstanceState.getInt(CHAVE_INDICE_MAPA, 0)
        }

        // Iniciar novo jogo ou restaurar save conforme Intent extra "novoJogo"
        val novoJogo = intent.getBooleanExtra("novoJogo", true)
        if (novoJogo) {
            viewModel.iniciarJogo()
        } else {
            viewModel.restaurarEstado()
        }
    }

    /**
     * Pausa o jogo ao ir para background.
     * GameLoop e Áudio devem pausar em ≤100ms; salvamento em ≤500ms (Requisitos 18.1, 7.3).
     * Agenda notificação de reengajamento para 48h (Requisitos 10.8, 18.3).
     */
    override fun onPause() {
        super.onPause()
        viewModel.pausarJogo()
        agendarNotificacaoReengajamento()
    }

    /**
     * Retoma o jogo ao voltar para foreground.
     * Deve completar em ≤200ms (Requisito 18.2).
     * Cancela a notificação de reengajamento agendada (Requisito 10.8).
     */
    override fun onResume() {
        super.onResume()
        viewModel.retomarJogo()
        cancelarNotificacaoReengajamento()
    }

    /**
     * Libera todos os recursos ao destruir a Activity (Requisitos 20.3, 21.3).
     *
     * Ordem de limpeza:
     * 1. Para o GameLoop (remove ThermalStatusCallback internamente via stopLoop)
     * 2. Libera AudioManager (MediaPlayer, SoundPool, coroutines)
     * 3. Libera bitmaps do Renderer
     */
    override fun onDestroy() {
        super.onDestroy()
        viewModel.pararJogo()
        try {
            superficieJogo.renderer.release()
        } catch (e: UninitializedPropertyAccessException) {
            // renderer ainda não foi inicializado, nada a liberar
        }
    }

    /**
     * Responde a pressão de memória do sistema evictando sprites não essenciais.
     * Quando o nível for TRIM_MEMORY_RUNNING_LOW ou superior, libera sprites de biomas inativos.
     * Requisitos: 20.5, 20.6
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            if (::superficieJogo.isInitialized && superficieJogo.isRendererInitialized()) {
                superficieJogo.renderer.evictNonEssentialSprites()
            }
            Logger.error("GameActivity", "onTrimMemory level=$level: evictNonEssential chamado", null)
        }
    }

    /**
     * Salva estado mínimo no Bundle para sobreviver a mudanças de configuração.
     * Persiste número do andar e índice do mapa (Requisito 21.5).
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(CHAVE_NUMERO_ANDAR, viewModel.gameState.floorNumber)
        outState.putInt(CHAVE_INDICE_MAPA, viewModel.gameState.mapIndex)
    }

    // =========================================================================
    // Navegação para ScoreActivity
    // =========================================================================

    /**
     * Lança a ScoreActivity ao completar um andar, passando todas as estatísticas necessárias.
     * Requisitos: 6.1, 6.3
     */
    fun lancarTelaScore() {
        val estado = viewModel.gameState
        val recordePessoal = estado.personalBests[estado.floorNumber] ?: 0L
        val leaderboard = estado.personalBests.values.sorted().toLongArray()

        val intent = Intent(this, ScoreActivity::class.java).apply {
            putExtra(ScoreActivity.EXTRA_ANDAR, estado.floorNumber)
            putExtra(ScoreActivity.EXTRA_TEMPO_MS, estado.floorTimerMs)
            putExtra(ScoreActivity.EXTRA_MAPS, estado.statistics.totalMapsCompleted)
            putExtra(ScoreActivity.EXTRA_SLOWDOWNS, estado.statistics.totalSlowdownsReceived)
            putExtra(ScoreActivity.EXTRA_COMBO, estado.comboStreak)
            putExtra(ScoreActivity.EXTRA_SCORE, estado.accumulatedScore)
            putExtra(ScoreActivity.EXTRA_RECORDE_MS, recordePessoal)
            putExtra(ScoreActivity.EXTRA_LEADERBOARD, leaderboard)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, CODIGO_SCORE)
    }

    /**
     * Trata o resultado retornado pela ScoreActivity.
     * Ações possíveis: "proximoAndar", "reiniciarAndar", "salvarESair" (Requisito 6.3).
     */
    @Deprecated("Usando startActivityForResult conforme design existente")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CODIGO_SCORE || resultCode != Activity.RESULT_OK) return

        when (data?.getStringExtra("acao")) {
            "proximoAndar" -> avancarParaProximoAndar()
            "reiniciarAndar" -> reiniciarAndarAtual()
            "salvarESair" -> salvarESairParaMenu()
        }
    }

    // =========================================================================
    // Ações de navegação pós-Score
    // =========================================================================

    /** Avança para o próximo andar incrementando floorNumber e reiniciando o loop. */
    private fun avancarParaProximoAndar() {
        viewModel.gameState.floorNumber++
        viewModel.gameState.mapIndex = 0
        viewModel.iniciarJogo()
    }

    /** Reinicia o andar atual mantendo o mesmo seed. */
    private fun reiniciarAndarAtual() {
        viewModel.gameState.mapIndex = 0
        viewModel.iniciarJogo()
    }

    /** Salva o estado e retorna ao menu principal. */
    private fun salvarESairParaMenu() {
        viewModel.salvarESair()
        finish()
    }

    // =========================================================================
    // Notificação de reengajamento (Requisitos 10.8, 18.3)
    // =========================================================================

    /**
     * Agenda notificação de reengajamento para 48h via WorkManager.
     * Cancela qualquer trabalho anterior antes de agendar o novo.
     */
    private fun agendarNotificacaoReengajamento() {
        val workManager = WorkManager.getInstance(applicationContext)
        workManager.cancelUniqueWork(ReengagementWorker.NOME_TRABALHO)
        val requisicao = OneTimeWorkRequestBuilder<ReengagementWorker>()
            .setInitialDelay(48, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniqueWork(
            ReengagementWorker.NOME_TRABALHO,
            ExistingWorkPolicy.REPLACE,
            requisicao
        )
    }

    /**
     * Cancela a notificação de reengajamento agendada quando o jogador retorna.
     */
    private fun cancelarNotificacaoReengajamento() {
        WorkManager.getInstance(applicationContext)
            .cancelUniqueWork(ReengagementWorker.NOME_TRABALHO)
    }

}
