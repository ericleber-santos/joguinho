package com.ericleber.joguinho.persistence

import android.content.Context
import com.ericleber.joguinho.core.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.lang.ref.WeakReference

/**
 * Gerenciador de persistência do estado do jogo.
 *
 * Mantém um buffer circular de 3 snapshots de segurança (Requisito 7.6).
 * Em caso de corrupção do snapshot mais recente, restaura o anterior (Requisito 7.7).
 * Utiliza WeakReference para o contexto Android, evitando vazamentos de memória.
 */
class PersistenceManager(
    contexto: Context,
    private val banco: AppDatabase
) {

    private val referenciaContexto = WeakReference(contexto.applicationContext)
    private val dao = banco.saveStateDao()
    private val leaderboardDao = banco.leaderboardDao()

    /** Instância Json configurada para ignorar chaves desconhecidas (compatibilidade futura). */
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "PersistenceManager"
        private const val TOTAL_TENTATIVAS = 3
        private const val INTERVALO_TENTATIVA_MS = 100L
        private const val TAMANHO_BUFFER = 3
    }

    /**
     * Resultado selado das operações de restauração.
     */
    sealed class RestoreResult {
        /** Restauração bem-sucedida com o estado salvo e o índice do snapshot utilizado. */
        data class Sucesso(val estado: SaveState, val indiceSnapshot: Int) : RestoreResult()

        /** Falha por corrupção: todos os snapshots estão corrompidos. */
        data class FalhaCorrupcao(val mensagem: String) : RestoreResult()

        /** Nenhum snapshot encontrado no banco de dados. */
        object Vazio : RestoreResult()
    }

    /**
     * Salva o estado do jogo de forma síncrona com até 3 tentativas.
     * Em caso de falha, aguarda 100ms antes de tentar novamente (Requisito 7.2).
     *
     * @param estado Estado do jogo a ser persistido.
     * @return `true` se o salvamento foi bem-sucedido, `false` após todas as tentativas falharem.
     */
    suspend fun salvar(estado: SaveState): Boolean = withContext(Dispatchers.IO) {
        repeat(TOTAL_TENTATIVAS) { tentativa ->
            try {
                val indiceAtual = obterIndiceUltimoSnapshot()
                val proximoIndice = (indiceAtual + 1) % TAMANHO_BUFFER
                val entidade = SaveStateEntity(
                    timestamp = System.currentTimeMillis(),
                    snapshotIndex = proximoIndice,
                    saveStateJson = json.encodeToString(SaveState.serializer(), estado)
                )
                dao.inserirOuSubstituir(entidade)
                return@withContext true
            } catch (e: Exception) {
                Logger.error(
                    TAG,
                    "Falha ao salvar estado na tentativa ${tentativa + 1} de $TOTAL_TENTATIVAS",
                    e
                )
                if (tentativa < TOTAL_TENTATIVAS - 1) {
                    delay(INTERVALO_TENTATIVA_MS)
                }
            }
        }
        Logger.error(TAG, "Todas as $TOTAL_TENTATIVAS tentativas de salvamento falharam")
        false
    }

    /**
     * Salva o estado do jogo de forma assíncrona, sem bloquear a thread chamadora.
     * Utilizado no ciclo de vida onPause para garantir conclusão em até 500ms (Requisito 7.3).
     *
     * @param estado Estado do jogo a ser persistido.
     */
    suspend fun salvarAsync(estado: SaveState) {
        CoroutineScope(Dispatchers.IO).launch {
            salvar(estado)
        }
    }

    /**
     * Restaura o estado do jogo a partir dos snapshots armazenados.
     *
     * Tenta desserializar os snapshots do mais recente ao mais antigo.
     * Em caso de corrupção do mais recente, utiliza o anterior (Requisito 7.7).
     *
     * @return [RestoreResult.Sucesso] com o estado restaurado,
     *         [RestoreResult.FalhaCorrupcao] se todos os snapshots estiverem corrompidos,
     *         ou [RestoreResult.Vazio] se não houver snapshots.
     */
    suspend fun restaurar(): RestoreResult = withContext(Dispatchers.IO) {
        val snapshots = dao.obterTodosSnapshots()

        if (snapshots.isEmpty()) {
            return@withContext RestoreResult.Vazio
        }

        for (snapshot in snapshots) {
            try {
                val estado = json.decodeFromString(SaveState.serializer(), snapshot.saveStateJson)
                return@withContext RestoreResult.Sucesso(estado, snapshot.snapshotIndex)
            } catch (e: Exception) {
                Logger.error(
                    TAG,
                    "Snapshot ${snapshot.snapshotIndex} corrompido, tentando o anterior",
                    e
                )
            }
        }

        RestoreResult.FalhaCorrupcao(
            "Todos os ${snapshots.size} snapshots estão corrompidos e não podem ser restaurados"
        )
    }

    /**
     * Verifica se existe pelo menos um snapshot salvo no banco de dados.
     *
     * @return `true` se houver ao menos um snapshot, `false` caso contrário.
     */
    suspend fun temSaveState(): Boolean = withContext(Dispatchers.IO) {
        dao.obterQuantidade() > 0
    }

    /**
     * Remove todos os snapshots do banco de dados.
     */
    suspend fun limpar() = withContext(Dispatchers.IO) {
        dao.deletarTodos()
    }

    // =========================================================================
    // Placar local (Requisitos 10.5, 14.4)
    // =========================================================================

    /**
     * Salva uma entrada no placar local.
     * @param entrada entrada a ser persistida
     */
    suspend fun salvarEntradaLeaderboard(entrada: LeaderboardEntry) = withContext(Dispatchers.IO) {
        leaderboardDao.inserir(
            LeaderboardEntryEntity(
                floorNumber = entrada.floorNumber,
                timeMs = entrada.timeMs,
                timestamp = entrada.timestamp
            )
        )
    }

    /**
     * Retorna as melhores entradas do placar para um andar específico.
     * @param floorNumber número do andar
     * @param limit quantidade máxima de entradas (padrão: 10)
     * @return lista ordenada pelo menor tempo
     */
    suspend fun getTopEntriesForFloor(
        floorNumber: Int,
        limit: Int = 10
    ): List<LeaderboardEntry> = withContext(Dispatchers.IO) {
        leaderboardDao.obterMelhoresDoAndar(floorNumber, limit).map { it.toLeaderboardEntry() }
    }

    /**
     * Retorna o índice do snapshot mais recente no buffer circular.
     * Retorna -1 se não houver nenhum snapshot, de modo que o próximo índice calculado seja 0.
     */
    private suspend fun obterIndiceUltimoSnapshot(): Int {
        return dao.obterSnapshotMaisRecente()?.snapshotIndex ?: -1
    }
}
