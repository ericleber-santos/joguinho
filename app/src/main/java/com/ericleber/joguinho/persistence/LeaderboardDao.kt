package com.ericleber.joguinho.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO para operações de persistência do placar local.
 * Requisitos: 10.5, 14.4
 */
@Dao
interface LeaderboardDao {

    /** Insere uma nova entrada no placar. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(entrada: LeaderboardEntryEntity)

    /**
     * Retorna as melhores entradas para um andar específico, ordenadas pelo menor tempo.
     * @param andar número do andar
     * @param limite quantidade máxima de entradas (padrão: 10)
     */
    @Query("SELECT * FROM leaderboard WHERE floorNumber = :andar ORDER BY timeMs ASC LIMIT :limite")
    suspend fun obterMelhoresDoAndar(andar: Int, limite: Int): List<LeaderboardEntryEntity>

    /** Remove todas as entradas do placar. */
    @Query("DELETE FROM leaderboard")
    suspend fun deletarTodos()
}
