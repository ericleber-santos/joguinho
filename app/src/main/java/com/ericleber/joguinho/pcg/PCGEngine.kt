package com.ericleber.joguinho.pcg

import com.ericleber.joguinho.biome.Biome
import com.ericleber.joguinho.core.ItemState
import com.ericleber.joguinho.core.MazeData
import com.ericleber.joguinho.core.MonsterState
import com.ericleber.joguinho.core.SurvivalElementState
import com.ericleber.joguinho.core.TrapState
import java.util.LinkedList
import kotlin.random.Random

/**
 * Resultado completo da geração de um Map.
 */
data class GeneratedMap(
    val maze: MazeData,
    val monsters: List<MonsterState>,
    val traps: List<TrapState>,
    val items: List<ItemState> = emptyList(),
    val survivalElements: List<SurvivalElementState> = emptyList()
)

/**
 * Orquestrador do sistema de geração procedural (PCG).
 *
 * Responsabilidades:
 * - Combinar FloorSeed + mapIndex para seed determinístico (Requisito 2.1, 2.7)
 * - Delegar geração ao BSPMazeGenerator
 * - Validar caminho via MazeValidator (Requisito 2.2)
 * - Posicionar entidades via EntityPlacer (Requisito 2.6)
 * - Usar Maze de fallback após 3 falhas consecutivas (Requisito 19.6)
 *
 * Requisitos: 2.1, 2.7, 19.6
 */
class PCGEngine {

    private val entityPlacer = EntityPlacer(Random.Default)

    companion object {
        private const val MAX_GENERATION_ATTEMPTS = 15
    }

    /**
     * Gera um Map completo (labirinto + entidades) de forma determinística.
     *
     * O seed final combina o FloorSeed do Player com o índice do Map,
     * garantindo que dois Players no mesmo Floor recebam Mazes diferentes
     * (seeds de Player distintos) mas que o mesmo Player sempre veja o
     * mesmo Map ao reiniciar (seed determinístico).
     *
     * @param floorNumber  Número do andar (1–120)
     * @param mapIndex     Índice do Map dentro do Floor (0-based)
     * @param playerSeed   FloorSeed único do Player
     */
    fun generateMap(floorNumber: Int, mapIndex: Int, playerSeed: Long): GeneratedMap {
        val params = BiomeParametersProvider.forFloor(floorNumber)
        val progress = BiomeParametersProvider.floorProgress(floorNumber)
        val densityTarget = params.targetDensity(progress)

        // Seed determinístico: combina playerSeed, floorNumber e mapIndex
        val combinedSeed = playerSeed xor (floorNumber.toLong() shl 32) xor mapIndex.toLong()

        repeat(MAX_GENERATION_ATTEMPTS) { attempt ->
            // Cada tentativa usa seed ligeiramente diferente para variar o resultado
            val attemptSeed = combinedSeed + attempt
            val random = Random(attemptSeed)
            val generator = HybridMapGenerator(random)

            val maze = generator.generate(
                width = params.mapWidth,
                height = params.mapHeight,
                floorNumber = floorNumber,
                seed = attemptSeed,
                wallDensityTarget = densityTarget
            )

            if (MazeValidator.hasValidPath(maze)) {
                val criticalPath = computeCriticalPath(maze)
                val entityRandom = Random(attemptSeed xor 0xDEADBEEF)
                val placer = EntityPlacer(entityRandom)

                val currentBiome = Biome.entries.firstOrNull { floorNumber in it.floorRange } ?: Biome.MINA_ABANDONADA
                val monsters = placer.placeMonsters(maze, floorNumber, mapIndex, criticalPath, currentBiome)
                val monsterIndices = monsters.map { it.position.iy * maze.width + it.position.ix }.toSet()
                val traps = placer.placeTraps(maze, floorNumber, criticalPath, monsterIndices)
                val trapIndices = traps.map { it.position.iy * maze.width + it.position.ix }.toSet()
                val items = placer.placeItems(maze, mapIndex, criticalPath, monsterIndices + trapIndices)
                val itemIndices = items.map { it.position.iy * maze.width + it.position.ix }.toSet()
                val survivalElements = placer.placeSurvivalElements(maze, mapIndex, criticalPath, monsterIndices + trapIndices + itemIndices)

                return GeneratedMap(maze, monsters, traps, items, survivalElements)
            }
        }

        // Fallback: mapa simples garantidamente válido após 3 falhas
        return generateFallbackMap(floorNumber, mapIndex, combinedSeed)
    }

    /**
     * Computa o caminho mínimo (BFS) entre entrada e saída.
     * Retorna o conjunto de índices que formam esse caminho crítico.
     * Entidades não podem ser posicionadas nesses tiles.
     */
    private fun computeCriticalPath(maze: MazeData): Set<Int> {
        val prev = IntArray(maze.width * maze.height) { -1 }
        val visited = BooleanArray(maze.width * maze.height)
        val queue: LinkedList<Int> = LinkedList()

        visited[maze.startIndex] = true
        queue.add(maze.startIndex)

        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: continue
            if (current == maze.exitIndex) break

            for (neighbor in getNeighbors(current, maze)) {
                if (!visited[neighbor] && maze.tiles[neighbor] == HybridMapGenerator.TILE_FLOOR) {
                    visited[neighbor] = true
                    prev[neighbor] = current
                    queue.add(neighbor)
                }
            }
        }

        // Reconstrói o caminho do Exit até o Start
        val path = mutableSetOf<Int>()
        var cur = maze.exitIndex
        while (cur != -1) {
            path.add(cur)
            cur = prev[cur]
        }
        return path
    }

    private fun getNeighbors(index: Int, maze: MazeData): List<Int> {
        val x = index % maze.width
        val y = index / maze.width
        val neighbors = mutableListOf<Int>()
        if (y > 0)                neighbors.add((y - 1) * maze.width + x)
        if (y < maze.height - 1)  neighbors.add((y + 1) * maze.width + x)
        if (x > 0)                neighbors.add(y * maze.width + (x - 1))
        if (x < maze.width - 1)   neighbors.add(y * maze.width + (x + 1))
        return neighbors
    }

    /**
     * Gera um mapa de fallback simples: corredor horizontal do canto
     * superior-esquerdo ao canto inferior-direito, garantidamente válido.
     * Usado apenas quando o BSP falha 3 vezes consecutivas (Requisito 19.6).
     */
    private fun generateFallbackMap(floorNumber: Int, mapIndex: Int, seed: Long): GeneratedMap {
        val params = BiomeParametersProvider.forFloor(floorNumber)
        val w = params.mapWidth
        val h = params.mapHeight
        val tiles = IntArray(w * h) { HybridMapGenerator.TILE_WALL }

        // Corredor horizontal na linha do meio
        val midY = h / 2
        for (x in 1 until w - 1) tiles[midY * w + x] = HybridMapGenerator.TILE_FLOOR

        for (y in 1 until h - 1) tiles[y * w + 1] = HybridMapGenerator.TILE_FLOOR
        for (y in 1 until h - 1) tiles[y * w + (w - 2)] = HybridMapGenerator.TILE_FLOOR

        val startIndex = midY * w + 1
        val exitIndex = midY * w + (w - 2)

        val maze = MazeData(
            width = w, height = h,
            tiles = tiles,
            startIndex = startIndex,
            exitIndex = exitIndex,
            floorNumber = floorNumber,
            seed = seed,
            exitWallDirection = com.ericleber.joguinho.core.Direction.EAST // No fallback, a saída é à direita
        )

        val criticalPath = computeCriticalPath(maze)
        val placer = EntityPlacer(Random(seed))
        val currentBiome = Biome.entries.firstOrNull { floorNumber in it.floorRange } ?: Biome.MINA_ABANDONADA
        val monsters = placer.placeMonsters(maze, floorNumber, mapIndex, criticalPath, currentBiome)
        val monsterIndices = monsters.map { it.position.iy * w + it.position.ix }.toSet()
        val traps = placer.placeTraps(maze, floorNumber, criticalPath, monsterIndices)
        val trapIndices = traps.map { it.position.iy * w + it.position.ix }.toSet()
        val items = placer.placeItems(maze, mapIndex, criticalPath, monsterIndices + trapIndices)
        val itemIndices = items.map { it.position.iy * w + it.position.ix }.toSet()
        val survivalElements = placer.placeSurvivalElements(maze, mapIndex, criticalPath, monsterIndices + trapIndices + itemIndices)

        return GeneratedMap(maze, monsters, traps, items, survivalElements)
    }
}
