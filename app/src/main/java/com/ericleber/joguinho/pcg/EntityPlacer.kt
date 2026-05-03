package com.ericleber.joguinho.pcg

import com.ericleber.joguinho.biome.Biome
import com.ericleber.joguinho.core.ItemState
import com.ericleber.joguinho.core.ItemType
import com.ericleber.joguinho.core.MazeData
import com.ericleber.joguinho.core.MonsterState
import com.ericleber.joguinho.core.MovementPattern
import com.ericleber.joguinho.core.Position
import com.ericleber.joguinho.core.SurvivalElementState
import com.ericleber.joguinho.core.SurvivalElementType
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
        // Aumento drástico de densidade:
        // - Base: 4 monstros no andar 1 (era 2).
        // - Aumenta 1 monstro a cada 3 andares (era 5).
        // - Bônus por mapa: +1 no mapa 1, +2 no mapa 2 (era +0, +1, +2).
        val baseByFloor = 4 + (floorNumber / 3)
        val bonusByMap = mapIndex + 1
        return (baseByFloor + bonusByMap).coerceAtMost(15) // Limite aumentado para 15
    }

    /**
     * Calcula a quantidade de Traps para o Floor informado.
     * Fórmula: min(1 + floor(floorNumber / 15), 8)  — Requisito 5.6
     */
    fun trapCount(floorNumber: Int): Int {
        // Aumento drástico de armadilhas:
        // - Base: 3 armadilhas no andar 1 (era 1).
        // - Aumenta 1 armadilha a cada 5 andares (era 15).
        // - Mínimo de 3, máximo de 12.
        return (3 + floorNumber / 5).coerceAtMost(12)
    }

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
        criticalPath: Set<Int>,
        biome: Biome
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
                val bossHp = 50 + (floorNumber * 5)
                monsters.add(MonsterState(
                    id = "boss_${floorNumber}",
                    position = Position(it % maze.width, it / maze.width),
                    movementPattern = MovementPattern.BOSS_STALKER,
                    isActive = true,
                    isBoss = true,
                    bossType = (floorNumber / 10) % 3, // Varia o tipo por bioma
                    hp = bossHp,
                    maxHp = bossHp
                ))
                candidates.remove(it)
            }
        }

        val selected = candidates.shuffled(random).take(count)
        
        // Ecologia de Monstros baseada no Bioma (Fase 4)
        val patterns = when {
            biome.name.contains("VULCANICO") || biome.name.contains("LAVA") || biome.name.contains("FOGO") ->
                arrayOf(MovementPattern.TANK_SLOW, MovementPattern.TANK_SLOW, MovementPattern.RANDOM, MovementPattern.PATROL_HORIZONTAL)
            biome.name.contains("FLORESTA") || biome.name.contains("JARDIM") || biome.name.contains("RAIZES") ->
                arrayOf(MovementPattern.AMBUSH, MovementPattern.AMBUSH, MovementPattern.CHASE, MovementPattern.ZONING_DEFENDER)
            biome.name.contains("CRISTAIS") || biome.name.contains("TEMPLO") || biome.name.contains("MISTICO") ->
                arrayOf(MovementPattern.ZONING_DEFENDER, MovementPattern.ZONING_DEFENDER, MovementPattern.CHASE, MovementPattern.LINEAR)
            else -> // MINA e outros
                arrayOf(MovementPattern.PATROL_HORIZONTAL, MovementPattern.PATROL_VERTICAL, MovementPattern.RANDOM, MovementPattern.TANK_SLOW)
        }

        monsters.addAll(selected.mapIndexed { i, index ->
            val pos = Position(index % maze.width, index / maze.width)
            val pattern = patterns[random.nextInt(patterns.size)]
            val monsterHp = 1 + (floorNumber / 15)
            MonsterState(
                id = "monster_${floorNumber}_${mapIndex}_$i",
                position = pos,
                movementPattern = pattern,
                isActive = true,
                anchorPosition = if (pattern == MovementPattern.ZONING_DEFENDER) pos else null,
                hp = monsterHp,
                maxHp = monsterHp
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
        // Obstáculos removidos conforme pedido do usuário: "não ficou bom nem bonito"
        return emptyList()
    }

    /**
     * Posiciona os Elementos de Sobrevivência (Fase 5 - Encontros de Chefes)
     *
     * @param maze MazeData já validado
     * @param mapIndex Índice do mapa (apenas 2 possui Boss)
     * @param criticalPath Caminho crítico (evitar bloqueios)
     * @param occupiedIndices Índices já ocupados por outras entidades
     */
    fun placeSurvivalElements(
        maze: MazeData,
        mapIndex: Int,
        criticalPath: Set<Int>,
        occupiedIndices: Set<Int> = emptySet()
    ): List<SurvivalElementState> {
        // Obstáculos removidos conforme pedido do usuário: "não ficou bom nem bonito"
        return emptyList()
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
