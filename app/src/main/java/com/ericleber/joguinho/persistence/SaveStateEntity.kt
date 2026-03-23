package com.ericleber.joguinho.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidade Room que representa um snapshot de segurança do estado do jogo.
 * O buffer circular mantém até 3 snapshots (índices 0, 1 e 2).
 */
@Entity(tableName = "save_states")
data class SaveStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val snapshotIndex: Int,       // 0, 1 ou 2 (3 snapshots de segurança)
    val saveStateJson: String     // SaveState serializado como JSON
)
