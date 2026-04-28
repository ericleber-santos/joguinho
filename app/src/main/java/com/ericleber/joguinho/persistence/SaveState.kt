package com.ericleber.joguinho.persistence

import com.ericleber.joguinho.core.*
import kotlinx.serialization.Serializable

@Serializable
data class SaveState(
    val version: Int = 1,
    val floorNumber: Int,
    val mapIndex: Int,
    val floorSeed: Long,
    val heroPosition: Position,
    val heroState: HeroState,
    val spikePosition: Position,
    val spikeState: SpikeState,
    val monsters: List<MonsterState>,
    val traps: List<TrapState>,
    val bossFightState: BossFightState = BossFightState(),
    val survivalElements: List<SurvivalElementState> = emptyList(),
    val bossAoeZones: List<AoeZone> = emptyList(),
    val floorTimerMs: Long,
    val accumulatedScore: Float,
    val comboStreak: Int,
    val comboBonus: Float,
    val statistics: PlayerStatistics,
    val achievements: Set<String>,
    val personalBests: Map<Int, Long>,
    // Extensibility fields (Requisitos 24.6, 24.8)
    val activeCharacterId: String = "hero",
    val activeSkinId: String = "default"
)
