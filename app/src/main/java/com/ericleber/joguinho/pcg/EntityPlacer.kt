package com.ericleber.joguinho.pcg

import com.ericleber.joguinho.core.ItemState
import com.ericleber.joguinho.core.ItemType
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
     * Calcula a quantidade de Monsters para o mapa atual com base no andar e índice do mapa.
     * Lógica de Progressão:
     * - Base: 2 monstros no andar 1.
     * - Aumenta 1 monstro a cada 5 andares.
     * - Adiciona +1 ou +2 monstros dependendo do mapIndex (mapas posteriores no mesmo andar são mais difíceis).
     * - Limite máximo de 10 monstros para evitar tédio/impossibilidade.
     */
    fun monsterCount(floorNumber: Int, mapIndex: Int): Int {
        val baseByFloor = 2 + (floorNumber / 5)
        val bonusByMap = mapIndex // +0, +1 ou +2
        return (baseByFloor + bonusByMap).coerceAtMost(10)
    }

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
        mapIndex: Int,
        criticalPath: Set<Int>
    ): List<MonsterState> {
        val count = monsterCount(floorNumber, mapIndex)
        val candidates = getFloorCandidates(maze, criticalPath).toMutableList()
        
        val monsters = mutableListOf<MonsterState>()
        
        // Se for o último mapa do bioma (mapIndex == 2), adiciona um Boss próximo à escada
        if (mapIndex == 2) {
            val exitX = maze.exitIndex % maze.width
            val exitY = maze.exitIndex / maze.width
            
            // Busca um tile de chão na sala adjacente à saída (distância 2-4)
            // Requisito: Boss spawnado única e exclusivamente na sala ao lado da escada
            val bossTile = candidates.filter { 
                val dist = Math.abs(it % maze.width - exitX) + Math.abs(it / maze.width - exitY)
                dist in 2..4
            }.shuffled(random).firstOrNull()
            
            bossTile?.let {
                monsters.add(MonsterState(
                    id = "boss_${floorNumber}",
                    position = Position(it % maze.width, it / maze.width),
                    movementPattern = MovementPattern.BOSS_STALKER,
                    isActive = true,
                    isBoss = true,
                    bossType = (floorNumber / 10) % 3 // Varia o tipo por bioma
                ))
                candidates.remove(it)
            }
        }

        val selected = candidates.shuffled(random).take(count)
        val patterns = arrayOf(MovementPattern.PATROL_HORIZONTAL, MovementPattern.PATROL_VERTICAL, MovementPattern.RANDOM)

        monsters.addAll(selected.mapIndexed { i, index ->
            MonsterState(
                id = "monster_${floorNumber}_${mapIndex}_$i",
                position = Position(index % maze.width, index / maze.width),
                movementPattern = patterns[random.nextInt(patterns.size)],
                isActive = true
            )
        })
        
        return monsters
    }

    /**
     * Posiciona itens benéficos (Botas de Velocidade) no mapa.
     */
    fun placeItems(
        maze: MazeData,
        mapIndex: Int,
        criticalPath: Set<Int>,
        occupiedIndices: Set<Int>
    ): List<ItemState> {
        // Requisito: O power up poderá estar em locais aleatórios SOMENTE no mapa onde tem chefão (mapIndex == 2)
        if (mapIndex != 2) return emptyList()

        // 1 item por mapa de boss, em algum lugar fora do caminho crítico
        val candidates = getFloorCandidates(maze, criticalPath + occupiedIndices)
        val selected = candidates.shuffled(random).take(1)
        
        return selected.mapIndexed { i, index ->
            ItemState(
                id = "item_${maze.seed}_$i",
                position = Position(index % maze.width, index / maze.width),
                type = ItemType.SPEED_BOOTS // Representará a Banana visualmente
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
