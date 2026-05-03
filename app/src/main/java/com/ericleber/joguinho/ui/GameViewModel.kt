package com.ericleber.joguinho.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ericleber.joguinho.audio.AudioManager
import com.ericleber.joguinho.audio.TipoEfeito
import com.ericleber.joguinho.core.GameLogic
import com.ericleber.joguinho.core.GameLoop
import com.ericleber.joguinho.core.GamePhase
import com.ericleber.joguinho.core.GameState
import com.ericleber.joguinho.core.Logger
import com.ericleber.joguinho.core.Position
import com.ericleber.joguinho.pcg.PCGEngine
import com.ericleber.joguinho.persistence.PersistenceManager
import com.ericleber.joguinho.persistence.SaveState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * ViewModel que serve como ponte entre a UI thread e o GameLoop.
 *
 * Responsabilidades:
 * - Expor o estado do jogo à UI via StateFlow (Requisito 21.1)
 * - Gerenciar o ciclo de vida do GameLoop de forma lifecycle-aware (Requisito 21.2)
 * - Fornecer métodos para iniciar, pausar, retomar e parar o jogo (Requisito 21.4)
 * - Coordenar salvamento e restauração de estado (Requisito 21.5)
 *
 * Usa WeakReference para Context, evitando vazamento de memória (Requisito 20.2).
 * Injeção de dependência manual — sem Dagger/Hilt.
 */
class GameViewModel : ViewModel() {

    companion object {
        private const val TAG = "GameViewModel"
    }

    // --- Referência fraca ao contexto para evitar vazamento de memória ---
    private var contextoRef: WeakReference<Context> = WeakReference(null)

    // --- Estado do jogo (Model) — deve ser declarado antes de gameLogic ---
    val gameState = GameState()

    // --- Dependências injetadas manualmente ---
    private var gameLoop: GameLoop? = null
    private var persistenceManager: PersistenceManager? = null
    private var audioManager: AudioManager? = null
    private val pcgEngine = PCGEngine()
    val gameLogic = GameLogic(gameState)

    // Callback para lançar ScoreActivity — injetado pela GameActivity
    var onHeroReachedExit: (() -> Unit)? = null

    // --- Estado do jogo exposto à UI via StateFlow (Requisito 21.1) ---
    private val _faseJogo = MutableStateFlow(GamePhase.MENU)
    val faseJogo: StateFlow<GamePhase> = _faseJogo.asStateFlow()

    private val _estadoSalvamento = MutableStateFlow<EstadoSalvamento>(EstadoSalvamento.Ocioso)
    val estadoSalvamento: StateFlow<EstadoSalvamento> = _estadoSalvamento.asStateFlow()

    private val _saveStateRestaurado = MutableStateFlow<SaveState?>(null)
    val saveStateRestaurado: StateFlow<SaveState?> = _saveStateRestaurado.asStateFlow()

    // =========================================================================
    // Inicialização de dependências
    // =========================================================================

    /**
     * Inicializa as dependências do ViewModel.
     * Deve ser chamado pela Activity antes de qualquer outra operação.
     *
     * @param contexto Contexto Android (armazenado via WeakReference)
     * @param loop GameLoop a ser gerenciado
     * @param gerenciadorPersistencia PersistenceManager para save/restore
     * @param gerenciadorAudio AudioManager para controle de áudio
     */
    fun inicializar(
        contexto: Context,
        loop: GameLoop,
        gerenciadorPersistencia: PersistenceManager,
        gerenciadorAudio: AudioManager
    ) {
        contextoRef = WeakReference(contexto.applicationContext)
        gameLoop = loop
        persistenceManager = gerenciadorPersistencia
        audioManager = gerenciadorAudio

        // Conecta callbacks do GameLogic
        gameLogic.onMapCompleted = { salvarEstadoAsync() }
        gameLogic.onHeroReachedExit = {
            // Se o floor foi completado, lança ScoreActivity; senão regenera mapa
            if (gameState.phase == GamePhase.SCORE_SCREEN) {
                onHeroReachedExit?.invoke()
            } else {
                gerarMapa()
            }
        }
        gameLogic.onSoundEffectRequested = { tipo: TipoEfeito ->
            audioManager?.reproduzirEfeito(tipo)
        }
    }

    // =========================================================================
    // Controle do jogo (Requisito 21.4)
    // =========================================================================

    /**
     * Gera o labirinto atual via PCGEngine e posiciona Hero e Spike.
     * Chamado em iniciarJogo() e ao avançar para o próximo Map.
     */
    fun gerarMapa() {
        val mapaGerado = pcgEngine.generateMap(
            floorNumber = gameState.floorNumber,
            mapIndex = gameState.mapIndex,
            playerSeed = gameState.floorSeed
        )
        gameState.mazeData = mapaGerado.maze
        gameState.monsters = mapaGerado.monsters
        gameState.traps = mapaGerado.traps
        gameState.items = mapaGerado.items
        gameState.survivalElements = mapaGerado.survivalElements
        gameState.currentMapClean = true

        // Reset BossFightState se aplicável
        if (gameState.mapIndex == 2) {
            gameState.bossFightState = com.ericleber.joguinho.core.BossFightState(isActive = true)
            gameState.bossAoeZones = emptyList()
        } else {
            gameState.bossFightState = com.ericleber.joguinho.core.BossFightState(isActive = false)
            gameState.bossAoeZones = emptyList()
        }

        val maze = mapaGerado.maze
        val startX = maze.startIndex % maze.width
        val startY = maze.startIndex / maze.width
        gameState.heroPosition = Position(startX + 0.5f, startY + 0.5f)
        gameState.spikePosition = Position((startX + 1).coerceAtMost(maze.width - 1) + 0.5f, startY + 0.5f)
        
        // Garante que a fase volte para PLAYING após a geração
        gameState.phase = GamePhase.PLAYING
    }

    /**
     * Inicia o jogo a partir do estado atual do GameState.
     * Gera o labirinto via PCGEngine, posiciona Hero e Spike, e inicia o GameLoop.
     */
    fun iniciarJogo() {
        gerarMapa()
        gameState.phase = GamePhase.PLAYING
        _faseJogo.value = GamePhase.PLAYING
        gameLoop?.start()
        Logger.error(TAG, "Jogo iniciado — Floor ${gameState.floorNumber}, Map ${gameState.mapIndex}, labirinto ${gameState.mazeData?.width}x${gameState.mazeData?.height}")
    }

    /**
     * Pausa o jogo: para o GameLoop, pausa o áudio e inicia salvamento assíncrono.
     * Deve completar em no máximo 500ms (Requisito 21.1 / 18.1).
     */
    fun pausarJogo() {
        gameState.phase = GamePhase.PAUSED
        _faseJogo.value = GamePhase.PAUSED
        gameLoop?.pausar()
        audioManager?.pausarTudo()
        salvarEstadoAsync()
    }

    /**
     * Retoma o jogo a partir do estado preservado.
     * Deve completar em no máximo 200ms (Requisito 21.2 / 18.2).
     */
    fun retomarJogo() {
        gameState.phase = GamePhase.PLAYING
        _faseJogo.value = GamePhase.PLAYING
        gameLoop?.retomar()
        audioManager?.retomar()
    }

    /**
     * Para o jogo completamente, encerrando o GameLoop.
     * Chamado em onDestroy para liberar recursos (Requisito 21.3).
     */
    fun pararJogo() {
        gameLoop?.stopLoop()
        audioManager?.liberarRecursos()
        _faseJogo.value = GamePhase.MENU
    }

    /**
     * Salva o estado atual e retorna ao menu principal.
     * Aguarda a conclusão do salvamento antes de sinalizar o retorno.
     */
    fun salvarESair() {
        viewModelScope.launch {
            _estadoSalvamento.value = EstadoSalvamento.Salvando
            val pm = persistenceManager
            val sucesso = if (pm != null) {
                pm.salvar(gameState.toSaveState())
            } else {
                false
            }
            _estadoSalvamento.value = if (sucesso) {
                EstadoSalvamento.Sucesso
            } else {
                EstadoSalvamento.Erro("Falha ao salvar o estado do jogo")
            }
            pararJogo()
        }
    }

    // =========================================================================
    // Coordenação de save/restore (Requisito 21.5)
    // =========================================================================

    /**
     * Salva o estado do jogo de forma assíncrona sem bloquear a UI thread.
     * Usado em onPause para garantir conclusão em até 500ms (Requisito 7.3).
     */
    fun salvarEstadoAsync() {
        viewModelScope.launch {
            val pm = persistenceManager ?: return@launch
            _estadoSalvamento.value = EstadoSalvamento.Salvando
            val sucesso = pm.salvar(gameState.toSaveState())
            _estadoSalvamento.value = if (sucesso) {
                EstadoSalvamento.Sucesso
            } else {
                EstadoSalvamento.Erro("Falha ao salvar estado em background")
            }
        }
    }

    /**
     * Restaura o estado do jogo a partir do SaveState persistido.
     * Atualiza o GameState e emite o SaveState restaurado via StateFlow.
     */
    fun restaurarEstado() {
        viewModelScope.launch {
            val pm = persistenceManager ?: return@launch
            val resultado = pm.restaurar()
            when (resultado) {
                is PersistenceManager.RestoreResult.Sucesso -> {
                    gameState.restoreFrom(resultado.estado)
                    _saveStateRestaurado.value = resultado.estado
                    _faseJogo.value = GamePhase.PLAYING
                    Logger.error(TAG, "Estado restaurado do snapshot ${resultado.indiceSnapshot}")
                }
                is PersistenceManager.RestoreResult.FalhaCorrupcao -> {
                    Logger.error(TAG, "Falha ao restaurar estado: ${resultado.mensagem}")
                    _saveStateRestaurado.value = null
                }
                PersistenceManager.RestoreResult.Vazio -> {
                    _saveStateRestaurado.value = null
                }
            }
        }
    }

    /**
     * Verifica se existe um SaveState salvo para exibir a opção "Continuar" no menu.
     *
     * @param callback Chamado com `true` se houver save, `false` caso contrário
     */
    fun verificarSaveExistente(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val temSave = persistenceManager?.temSaveState() ?: false
            callback(temSave)
        }
    }

    // =========================================================================
    // Limpeza de recursos (Requisito 21.3)
    // =========================================================================

    /**
     * Chamado automaticamente pelo framework quando o ViewModel é destruído.
     * Libera todos os recursos: GameLoop, AudioManager e referências.
     */
    override fun onCleared() {
        super.onCleared()
        pararJogo()
        contextoRef.clear()
        gameLoop = null
        persistenceManager = null
        audioManager = null
        Logger.error(TAG, "GameViewModel destruído — recursos liberados")
    }

    // =========================================================================
    // Estado de salvamento — sealed class para UI
    // =========================================================================

    /**
     * Representa o estado atual da operação de salvamento para a UI.
     */
    sealed class EstadoSalvamento {
        /** Nenhuma operação de salvamento em andamento. */
        object Ocioso : EstadoSalvamento()

        /** Salvamento em progresso. */
        object Salvando : EstadoSalvamento()

        /** Salvamento concluído com sucesso. */
        object Sucesso : EstadoSalvamento()

        /** Falha no salvamento com mensagem descritiva. */
        data class Erro(val mensagem: String) : EstadoSalvamento()
    }
}
