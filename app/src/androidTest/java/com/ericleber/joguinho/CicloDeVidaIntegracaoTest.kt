package com.ericleber.joguinho

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ericleber.joguinho.audio.AudioManager
import com.ericleber.joguinho.core.GameLoop
import com.ericleber.joguinho.core.GamePhase
import com.ericleber.joguinho.core.Position
import com.ericleber.joguinho.character.Spike
import com.ericleber.joguinho.character.SpikeAI
import com.ericleber.joguinho.persistence.AppDatabase
import com.ericleber.joguinho.persistence.PersistenceManager
import com.ericleber.joguinho.ui.GameViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes de integração para o ciclo de vida Android.
 *
 * Valida o comportamento correto de salvamento em onPause,
 * restauração em onResume e liberação de recursos em onDestroy.
 *
 * Requisitos: 22.5
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CicloDeVidaIntegracaoTest {

    private lateinit var banco: AppDatabase
    private lateinit var gerenciadorPersistencia: PersistenceManager
    private lateinit var gerenciadorAudio: AudioManager
    private lateinit var viewModel: GameViewModel

    @Before
    fun configurar() {
        val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Banco em memória para testes isolados
        banco = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        gerenciadorPersistencia = PersistenceManager(contexto, banco)
        gerenciadorAudio = AudioManager(contexto)
        viewModel = GameViewModel()

        // Cria um SurfaceHolder simulado mínimo para o GameLoop
        val spike = Spike()
        val spikeAI = SpikeAI(spike)
        val surfaceHolder = FakeSurfaceHolder()
        val gameLoop = GameLoop(
            surfaceHolder = surfaceHolder,
            gameState = viewModel.gameState,
            spikeAI = spikeAI,
            powerManager = null
        )

        viewModel.inicializar(contexto, gameLoop, gerenciadorPersistencia, gerenciadorAudio)
    }

    @After
    fun limpar() {
        banco.close()
        gerenciadorAudio.liberarRecursos()
    }

    // =========================================================================
    // Testes de onPause — salvamento correto
    // =========================================================================

    /**
     * Verifica que pausarJogo() persiste o estado atual no banco de dados.
     * Simula o comportamento de onPause da GameActivity.
     * Requisito: 22.5
     */
    @Test
    fun pausarJogo_deveSalvarEstadoNoBanco() = runBlocking {
        // Configura estado inicial com dados reconhecíveis
        viewModel.gameState.floorNumber = 5
        viewModel.gameState.mapIndex = 2
        viewModel.gameState.heroPosition = Position(10, 15)
        viewModel.gameState.accumulatedScore = 1234.5f

        // Simula onPause
        viewModel.pausarJogo()

        // Aguarda o salvamento assíncrono concluir
        delay(500)

        // Verifica que o estado foi persistido
        val temSave = gerenciadorPersistencia.temSaveState()
        assertTrue("Estado deve ser salvo ao pausar o jogo", temSave)
    }

    /**
     * Verifica que o estado salvo em onPause contém os dados corretos do jogo.
     * Requisito: 22.5
     */
    @Test
    fun pausarJogo_estadoSalvoDeveConterDadosCorretos() = runBlocking {
        // Configura estado com valores específicos
        viewModel.gameState.floorNumber = 7
        viewModel.gameState.mapIndex = 3
        viewModel.gameState.comboStreak = 10
        viewModel.gameState.accumulatedScore = 9999f

        // Simula onPause
        viewModel.pausarJogo()
        delay(500)

        // Restaura e verifica os dados
        val resultado = gerenciadorPersistencia.restaurar()
        assertTrue(
            "Restauração deve ser bem-sucedida após pausar",
            resultado is PersistenceManager.RestoreResult.Sucesso
        )

        val estado = (resultado as PersistenceManager.RestoreResult.Sucesso).estado
        assertEquals("Número do andar deve ser preservado", 7, estado.floorNumber)
        assertEquals("Índice do mapa deve ser preservado", 3, estado.mapIndex)
        assertEquals("ComboStreak deve ser preservado", 10, estado.comboStreak)
        assertEquals("Score acumulado deve ser preservado", 9999f, estado.accumulatedScore, 0.01f)
    }

    /**
     * Verifica que pausarJogo() altera a fase do jogo para PAUSED.
     * Requisito: 22.5
     */
    @Test
    fun pausarJogo_deveMudarFaseParaPausado() = runBlocking {
        viewModel.gameState.phase = GamePhase.PLAYING

        viewModel.pausarJogo()

        assertEquals(
            "Fase deve ser PAUSED após pausar",
            GamePhase.PAUSED,
            viewModel.gameState.phase
        )
    }

    // =========================================================================
    // Testes de onResume — restauração correta
    // =========================================================================

    /**
     * Verifica que retomarJogo() altera a fase do jogo para PLAYING.
     * Simula o comportamento de onResume da GameActivity.
     * Requisito: 22.5
     */
    @Test
    fun retomarJogo_deveMudarFaseParaJogando() = runBlocking {
        viewModel.gameState.phase = GamePhase.PAUSED

        viewModel.retomarJogo()

        assertEquals(
            "Fase deve ser PLAYING após retomar",
            GamePhase.PLAYING,
            viewModel.gameState.phase
        )
    }

    /**
     * Verifica o ciclo completo: pausar → retomar preserva o estado do jogo.
     * Simula onPause seguido de onResume.
     * Requisito: 22.5
     */
    @Test
    fun cicloCompleto_pausarERetomar_devePreservarEstado() = runBlocking {
        // Estado inicial
        viewModel.gameState.floorNumber = 12
        viewModel.gameState.accumulatedScore = 5000f
        viewModel.gameState.comboStreak = 8

        // Simula onPause
        viewModel.pausarJogo()
        delay(500)

        // Simula onResume
        viewModel.retomarJogo()

        // Estado deve ser preservado em memória
        assertEquals("Número do andar deve ser preservado após ciclo", 12, viewModel.gameState.floorNumber)
        assertEquals("Score deve ser preservado após ciclo", 5000f, viewModel.gameState.accumulatedScore, 0.01f)
        assertEquals("ComboStreak deve ser preservado após ciclo", 8, viewModel.gameState.comboStreak)
    }

    /**
     * Verifica que restaurarEstado() carrega o SaveState persistido corretamente.
     * Simula restauração após reinício do processo.
     * Requisito: 22.5
     */
    @Test
    fun restaurarEstado_deveCarregarSaveStatePersistido() = runBlocking {
        // Salva um estado diretamente no banco
        val estadoOriginal = viewModel.gameState.apply {
            floorNumber = 20
            mapIndex = 1
            accumulatedScore = 7500f
        }.toSaveState()

        gerenciadorPersistencia.salvar(estadoOriginal)

        // Cria novo ViewModel simulando reinício do processo
        val novoViewModel = GameViewModel()
        val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()
        val spike = Spike()
        val spikeAI = SpikeAI(spike)
        val gameLoop = GameLoop(
            surfaceHolder = FakeSurfaceHolder(),
            gameState = novoViewModel.gameState,
            spikeAI = spikeAI,
            powerManager = null
        )
        novoViewModel.inicializar(contexto, gameLoop, gerenciadorPersistencia, gerenciadorAudio)

        // Restaura o estado
        novoViewModel.restaurarEstado()
        delay(300)

        // Verifica que o estado foi restaurado
        assertEquals("Número do andar deve ser restaurado", 20, novoViewModel.gameState.floorNumber)
        assertEquals("Índice do mapa deve ser restaurado", 1, novoViewModel.gameState.mapIndex)
        assertEquals("Score deve ser restaurado", 7500f, novoViewModel.gameState.accumulatedScore, 0.01f)
    }

    // =========================================================================
    // Testes de onDestroy — liberação de recursos
    // =========================================================================

    /**
     * Verifica que pararJogo() altera a fase do jogo para MENU.
     * Simula o comportamento de onDestroy da GameActivity.
     * Requisito: 22.5
     */
    @Test
    fun pararJogo_deveMudarFaseParaMenu() = runBlocking {
        viewModel.gameState.phase = GamePhase.PLAYING

        viewModel.pararJogo()

        assertEquals(
            "Fase deve ser MENU após parar o jogo",
            GamePhase.MENU,
            viewModel.faseJogo.value
        )
    }

    /**
     * Verifica que o AudioManager é liberado corretamente em onDestroy.
     * Após liberarRecursos(), o AudioManager não deve estar em reprodução.
     * Requisito: 22.5
     */
    @Test
    fun liberarRecursosAudio_deveEncerrarReproducao() {
        // Libera recursos do AudioManager
        gerenciadorAudio.liberarRecursos()

        // Verifica que não há exceção ao chamar pausarTudo após liberação
        // (comportamento defensivo esperado)
        try {
            gerenciadorAudio.pausarTudo()
        } catch (e: Exception) {
            // Não deve lançar exceção após liberação
            assertTrue("Não deve lançar exceção após liberação: ${e.message}", false)
        }
    }

    /**
     * Verifica que múltiplos ciclos de pausa/retomada não causam inconsistência de estado.
     * Requisito: 22.5
     */
    @Test
    fun multiplosCiclosPausaRetomada_naoDevemCausarInconsistencia() = runBlocking {
        viewModel.gameState.floorNumber = 3
        viewModel.gameState.accumulatedScore = 1000f

        // Executa 3 ciclos de pausa/retomada
        repeat(3) {
            viewModel.pausarJogo()
            delay(200)
            viewModel.retomarJogo()
        }

        // Estado deve permanecer consistente
        assertEquals("Número do andar deve ser consistente após múltiplos ciclos", 3, viewModel.gameState.floorNumber)
        assertEquals("Score deve ser consistente após múltiplos ciclos", 1000f, viewModel.gameState.accumulatedScore, 0.01f)
        assertEquals("Fase deve ser PLAYING após retomada", GamePhase.PLAYING, viewModel.gameState.phase)
    }
}
