package com.ericleber.joguinho.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entrada do placar local — registra o tempo de conclusão de um andar.
 * Requisitos: 10.5, 14.4
 */
data class LeaderboardEntry(
    val floorNumber: Int,
    val timeMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Entidade Room para persistência das entradas do placar local.
 * Indexada por floorNumber para consultas eficientes por andar.
 */
@Entity(
    tableName = "leaderboard",
    indices = [Index(value = ["floorNumber"])]
)
data class LeaderboardEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val floorNumber: Int,
    val timeMs: Long,
    val timestamp: Long
) {
    fun toLeaderboardEntry() = LeaderboardEntry(
        floorNumber = floorNumber,
        timeMs = timeMs,
        timestamp = timestamp
    )
}
