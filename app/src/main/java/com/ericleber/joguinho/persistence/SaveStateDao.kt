package com.ericleber.joguinho.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO para operações de persistência dos snapshots de segurança.
 */
@Dao
interface SaveStateDao {

    /** Insere ou substitui um snapshot no banco de dados. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirOuSubstituir(entidade: SaveStateEntity)

    /** Retorna o snapshot mais recente com base no timestamp. */
    @Query("SELECT * FROM save_states ORDER BY timestamp DESC LIMIT 1")
    suspend fun obterSnapshotMaisRecente(): SaveStateEntity?

    /** Retorna todos os snapshots ordenados do mais recente para o mais antigo. */
    @Query("SELECT * FROM save_states ORDER BY timestamp DESC")
    suspend fun obterTodosSnapshots(): List<SaveStateEntity>

    /** Retorna o snapshot correspondente ao índice do buffer circular (0, 1 ou 2). */
    @Query("SELECT * FROM save_states WHERE snapshotIndex = :indice LIMIT 1")
    suspend fun obterSnapshotPorIndice(indice: Int): SaveStateEntity?

    /** Remove todos os snapshots do banco de dados. */
    @Query("DELETE FROM save_states")
    suspend fun deletarTodos()

    /** Retorna a quantidade de snapshots armazenados. */
    @Query("SELECT COUNT(*) FROM save_states")
    suspend fun obterQuantidade(): Int
}
