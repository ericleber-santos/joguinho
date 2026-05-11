package com.ericleber.joguinho.core

import java.util.PriorityQueue

/**
 * Utilitário de busca de caminho A* (Lite) otimizado para o grid do labirinto.
 */
object Pathfinder {

    data class Node(
        val x: Int,
        val y: Int,
        var g: Int = 0, // Custo do início até aqui
        var h: Int = 0, // Heurística até o destino
        var parent: Node? = null
    ) : Comparable<Node> {
        val f: Int get() = g + h
        override fun compareTo(other: Node): Int = f.compareTo(other.f)
        
        override fun equals(other: Any?): Boolean {
            if (other !is Node) return false
            return x == other.x && y == other.y
        }
        override fun hashCode(): Int = x * 31 + y
    }

    /**
     * Calcula o caminho mais curto entre duas posições no grid.
     * @return Lista de posições do caminho ou null se não houver caminho.
     */
    fun findPath(
        start: Position,
        target: Position,
        maze: MazeData,
        maxSteps: Int = 200 // Limite para evitar travamentos em mapas gigantes
    ): List<Position>? {
        val startX = start.ix
        val startY = start.iy
        val targetX = target.ix
        val targetY = target.iy

        if (startX == targetX && startY == targetY) return emptyList()

        val openList = PriorityQueue<Node>()
        val closedSet = mutableSetOf<Int>() // Usamos Int hash para performance (y * width + x)

        val startNode = Node(startX, startY, 0, manhattan(startX, startY, targetX, targetY))
        openList.add(startNode)

        var steps = 0
        while (openList.isNotEmpty() && steps < maxSteps) {
            steps++
            val current = openList.poll() ?: break
            
            if (current.x == targetX && current.y == targetY) {
                return reconstructPath(current)
            }

            closedSet.add(current.y * maze.width + current.x)

            // 4 Vizinhos (N, S, E, W)
            val neighbors = listOf(
                Pair(current.x, current.y - 1),
                Pair(current.x, current.y + 1),
                Pair(current.x - 1, current.y),
                Pair(current.x + 1, current.y)
            )

            for (neighbor in neighbors) {
                val nx = neighbor.first
                val ny = neighbor.second

                // Verifica limites e se é parede
                if (nx < 0 || ny < 0 || nx >= maze.width || ny >= maze.height) continue
                if (maze.tiles[ny * maze.width + nx] == 1) continue // Parede
                if (closedSet.contains(ny * maze.width + nx)) continue

                val gScore = current.g + 1
                val hScore = manhattan(nx, ny, targetX, targetY)
                val neighborNode = Node(nx, ny, gScore, hScore, current)

                // Se já estiver na openList com custo menor, ignora
                val existing = openList.find { it.x == nx && it.y == ny }
                if (existing != null && existing.g <= gScore) continue

                openList.add(neighborNode)
            }
        }

        return null // Caminho não encontrado ou estourou passos
    }

    private fun manhattan(x1: Int, y1: Int, x2: Int, y2: Int): Int {
        return kotlin.math.abs(x1 - x2) + kotlin.math.abs(y1 - y2)
    }

    private fun reconstructPath(node: Node): List<Position> {
        val path = mutableListOf<Position>()
        var curr: Node? = node
        while (curr != null) {
            path.add(Position(curr.x, curr.y))
            curr = curr.parent
        }
        return path.reversed()
    }
}
