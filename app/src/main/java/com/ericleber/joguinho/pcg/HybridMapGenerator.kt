package com.ericleber.joguinho.pcg

import com.ericleber.joguinho.core.Direction
import com.ericleber.joguinho.core.MazeData
import kotlin.random.Random

/**
 * Gerador de Arenas de Sobrevivência (Horda).
 *
 * Produz arenas completamente abertas:
 * - Apenas bordas de parede (teto 1 tile, chão 1 tile, laterais)
 * - Sem plataformas, sem obstáculos internos
 * - Start à esquerda, Exit (portal) à direita, ambos no chão
 */
class HybridMapGenerator(private val random: Random) {

    companion object {
        const val TILE_FLOOR = 0
        const val TILE_WALL = 1
        const val TILE_TRAP_SPIKES = 2
        const val TILE_TRAP_LAVA = 3
        const val TILE_TRAP_PIRANHA_WATER = 4
    }

    fun generate(
        width: Int,
        height: Int,
        floorNumber: Int,
        seed: Long,
        wallDensityTarget: Float = 0.5f
    ): MazeData {
        val tiles = IntArray(width * height) { TILE_FLOOR }

        // Start à esquerda, Exit (portal) à direita, ambos no chão
        val groundY = height - 2
        val startX = 2
        val exitX = width - 3

        val startIndex = groundY * width + startX
        val exitIndex = groundY * width + exitX

        return MazeData(
            width = width,
            height = height,
            tiles = tiles,
            startIndex = startIndex,
            exitIndex = exitIndex,
            floorNumber = floorNumber,
            seed = seed,
            exitWallDirection = Direction.EAST
        )
    }
}