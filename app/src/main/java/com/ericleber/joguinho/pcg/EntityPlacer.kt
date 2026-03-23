package com.ericleber.joguinho.pcg

import com.ericleber.joguinho.core.MazeData
import com.ericleber.joguinho.core.MonsterState
import com.ericleber.joguinho.core.MovementPattern
import com.ericleber.joguinho.core.Position
import com.ericleber.joguinho.core.TrapState
import kotlin.random.Random

/**
 * Posiciona Monsters e Traps no mapa garantindo que nenhuma entidade
 * bloqueie permanentemente o caminho válido até o Exit.
 *
 * Estratégia: entidades são colocadas apenas em tiles de chão que NÃO
 * fazem parte do caminho mínimo (BFS) entre entrada e saída. Isso garante
 * que o caminho válido permanece livre mesmo com todas as entidades presentes.
 *
 * Requisitos: 2.6, 5.5, 5.6
 */
class EntityPlacer(private val random: Random) {

    /**
     * Calcula a quantidade de Monsters para o Floor informado.
     * Fórmula: min(2 + floor(floorNumber / 10), 12)  — Requisito 5.5
     */
    fun monsterCount(floorNumber: Int): Int =
        minOf(2 + floorNumber / 10, 12)

    /**
     * Calcula a quantidade de Traps para o Floor informado.
     * Fórmula: min(1 + floor(floorNumber / 15), 8)  — Requisito 5.6
     */
    fun trapCount(floorNumber: Int): Int =
        minOf(1 + floorNumber / 15, 8)

    /**
     * Posiciona Monsters no mapa.
     *
     * @param maze         MazeData já validado com caminho válido
     * @param floorNumber  Número do andar (determina quantidade)
     * @param criticalPath Conjunto de índices que formam o caminho mínimo (não podem ser bloqueados)
     * @return Lista de [MonsterState] com posições e padrões de movimento
     */
    fun placeMonsters(
        maze: MazeData,
        floorNumber: Int,
        criticalPath: Set<Int>
    ): List<MonsterState> {
        val count = monsterCount(floorNumber)
        val candidates = getFloorCandidates(maze, criticalPath)
        val selected = candidates.shuffled(random).take(count)
        val patterns = MovementPattern.entries.toTypedArray()

        return selected.mapIndexed { i, index ->
            MonsterState(
                id = "monster_${floorNumber}_$i",
                position = Position(index % maze.width, index / maze.width),
                movementPattern = patterns[random.nextInt(patterns.size)],
                isActive = true
            )
        }
    }

    /**
     * Posiciona Traps no mapa.
     *
     * @param maze         MazeData já validado com caminho válido
     * @param floorNumber  Número do andar (determina quantidade)
     * @param criticalPath Conjunto de índices que formam o caminho mínimo
     * @param occupiedIndices Índices já ocupados por Monsters
     * @return Lista de [TrapState] com posições
     */
    fun placeTraps(
        maze: MazeData,
        floorNumber: Int,
        criticalPath: Set<Int>,
        occupiedIndices: Set<Int> = emptySet()
    ): List<TrapState> {
        val count = trapCount(floorNumber)
        val candidates = getFloorCandidates(maze, criticalPath + occupiedIndices)
        val selected = candidates.shuffled(random).take(count)

        return selected.mapIndexed { i, index ->
            TrapState(
                id = "trap_${floorNumber}_$i",
                position = Position(index % maze.width, index / maze.width),
                isActivated = false
            )
        }
    }

    /**
     * Retorna tiles de chão que não fazem parte do caminho crítico,
     * não são a entrada nem a saída, e têm distância mínima de 2 tiles
     * da entrada para evitar spawn imediato sobre o Hero.
     */
    private fun getFloorCandidates(maze: MazeData, excluded: Set<Int>): List<Int> {
        val startX = maze.startIndex % maze.width
        val startY = maze.startIndex / maze.width

        return maze.tiles.indices.filter { index ->
            maze.tiles[index] == BSPMazeGenerator.TILE_FLOOR &&
                index !in excluded &&
                index != maze.startIndex &&
                index != maze.exitIndex &&
                manhattanDistance(index, maze.startIndex, maze.width) >= 2
        }
    }

    private fun manhattanDistance(indexA: Int, indexB: Int, width: Int): Int {
        val ax = indexA % width; val ay = indexA / width
        val bx = indexB % width; val by = indexB / width
        return Math.abs(ax - bx) + Math.abs(ay - by)
    }
}
