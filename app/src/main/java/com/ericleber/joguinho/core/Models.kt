package com.ericleber.joguinho.core

import kotlinx.serialization.Serializable

@Serializable
enum class Direction {
    NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST
}

@Serializable
enum class MovementPattern {
    LINEAR, CIRCULAR, RANDOM, CHASE, PATROL_HORIZONTAL, PATROL_VERTICAL, BOSS_STALKER
}

@Serializable
data class Position(val x: Int, val y: Int)

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
    val bossType: Int = 0 // 0=Normal, 1=Slime King, 2=Skeleton Lord, etc.
)

@Serializable
data class ItemState(
    val id: String,
    val position: Position,
    val type: ItemType,
    val isActive: Boolean = true
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
