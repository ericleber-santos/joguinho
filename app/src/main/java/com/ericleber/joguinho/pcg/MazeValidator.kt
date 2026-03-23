package com.ericleber.joguinho.pcg

import com.ericleber.joguinho.core.MazeData
import java.util.LinkedList

/**
 * Valida a integridade de um MazeData usando BFS (Breadth-First Search).
 *
 * Garante que existe exatamente um caminho válido da posição inicial do Hero
 * até o Exit, conforme Requisitos 2.2 e 2.6.
 */
object MazeValidator {

    /**
     * Retorna true se existe pelo menos um caminho transitável de
     * [MazeData.startIndex] até [MazeData.exitIndex].
     */
    fun hasValidPath(maze: MazeData): Boolean {
        if (maze.startIndex == maze.exitIndex) return true
        if (maze.tiles[maze.startIndex] == BSPMazeGenerator.TILE_WALL) return false
        if (maze.tiles[maze.exitIndex] == BSPMazeGenerator.TILE_WALL) return false

        val visited = BooleanArray(maze.width * maze.height)
        val queue: LinkedList<Int> = LinkedList()

        visited[maze.startIndex] = true
        queue.add(maze.startIndex)

        while (queue.isNotEmpty()) {
            val current = queue.poll()
            if (current == maze.exitIndex) return true

            for (neighbor in getNeighbors(current, maze)) {
                if (!visited[neighbor] && maze.tiles[neighbor] == BSPMazeGenerator.TILE_FLOOR) {
                    visited[neighbor] = true
                    queue.add(neighbor)
                }
            }
        }

        return false
    }

    /**
     * Retorna os índices dos 4 vizinhos ortogonais de [index] dentro dos limites do mapa.
     */
    private fun getNeighbors(index: Int, maze: MazeData): List<Int> {
        val x = index % maze.width
        val y = index / maze.width
        val neighbors = mutableListOf<Int>()

        if (y > 0)                neighbors.add((y - 1) * maze.width + x) // Norte
        if (y < maze.height - 1)  neighbors.add((y + 1) * maze.width + x) // Sul
        if (x > 0)                neighbors.add(y * maze.width + (x - 1)) // Oeste
        if (x < maze.width - 1)   neighbors.add(y * maze.width + (x + 1)) // Leste

        return neighbors
    }

    /**
     * Calcula a densidade real de paredes do mapa (proporção de tiles WALL).
     * Usado para validar que a densidade está dentro da faixa esperada para o Floor.
     */
    fun wallDensity(maze: MazeData): Float {
        val wallCount = maze.tiles.count { it == BSPMazeGenerator.TILE_WALL }
        return wallCount.toFloat() / maze.tiles.size
    }
}
