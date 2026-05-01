package com.ericleber.joguinho.core

import kotlinx.serialization.Serializable

@Serializable
enum class Direction {
    NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST
}

@Serializable
enum class MovementPattern {
    LINEAR, CIRCULAR, RANDOM, CHASE, PATROL_HORIZONTAL, PATROL_VERTICAL, BOSS_STALKER,
    AMBUSH, ZONING_DEFENDER, TANK_SLOW
}

@Serializable
data class Position(val x: Float, val y: Float) {
    /** Retorna a coordenada X como Int para indexação de arrays de tiles. */
    val ix: Int get() = x.toInt()
    /** Retorna a coordenada Y como Int para indexação de arrays de tiles. */
    val iy: Int get() = y.toInt()

    /** Calcula a distância euclidiana até outra posição. */
    fun dist(other: Position): Float {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Construtor secundário para compatibilidade com inteiros. */
    constructor(ix: Int, iy: Int) : this(ix.toFloat(), iy.toFloat())
}

data class MazeData(
    val width: Int,
    val height: Int,
    val tiles: IntArray,       // 0 = caminho, 1 = parede
    val startIndex: Int,
    val exitIndex: Int,
    val floorNumber: Int,
    val seed: Long,
    val exitWallDirection: Direction? = null // Direção da parede onde a escada será desenhada
)

@Serializable
data class HeroState(
    val position: Position,
    val direction: Direction,
    val isSlowedDown: Boolean,
    val slowdownRemainingMs: Long
)

@Serializable
data class SpikeState(
    val position: Position,
    val isSlowedDown: Boolean,
    val slowdownRemainingMs: Long
)

@Serializable
data class MonsterState(
    val id: String,
    val position: Position,
    val movementPattern: MovementPattern,
    val isActive: Boolean,
    val isBoss: Boolean = false,
    val bossType: Int = 0, // 0=Normal, 1=Slime King, 2=Skeleton Lord, etc.
    val anchorPosition: Position? = null // Ponto central para Zoning Defenders
)

@Serializable
data class ItemState(
    val id: String,
    val position: Position,
    val type: ItemType,
    val isActive: Boolean = true
)

@Serializable
enum class SurvivalElementType {
    ICE_TORCH, STONE_PILLAR, MUD_SWAMP, PUSHABLE_BOX, DISTRACTION_BELL
}

@Serializable
data class SurvivalElementState(
    val id: String,
    var position: Position,
    val type: SurvivalElementType,
    val active: Boolean = true,
    val cooldownRemainingMs: Long = 0L,
    val durability: Int = 2
)

@Serializable
data class AoeZone(
    val position: Position,
    val createdAtMs: Long,
    val explodesAtMs: Long,
    val radiusTiles: Float = 1.5f
)

@Serializable
data class BossFightState(
    val isActive: Boolean = false,
    val elapsedMs: Long = 0L,
    val totalDurationMs: Long = 120000L,
    val bossStunRemainingMs: Long = 0L,
    val bossDistractedMs: Long = 0L,
    val bellUsed: Boolean = false,
    val nextAoeMs: Long = 45000L
)

@Serializable
enum class ItemType {
    SPEED_BOOTS
}

@Serializable
data class TrapState(
    val id: String,
    val position: Position,
    val isActivated: Boolean
)

@Serializable
data class PlayerStatistics(
    val totalMapsCompleted: Int = 0,
    val totalPlayTimeMs: Long = 0L,
    val totalSlowdownsReceived: Int = 0,
    val totalMaxComboStreaks: Int = 0
)
