package com.ericleber.joguinho.pcg

import com.ericleber.joguinho.core.GameState
import com.ericleber.joguinho.core.Position
import com.ericleber.joguinho.core.MonsterState
import com.ericleber.joguinho.core.MovementPattern
import com.ericleber.joguinho.core.MonsterArchetype
import com.ericleber.joguinho.core.MazeData
import java.util.Random

/**
 * Gerenciador de ondas e spawn contínuo de monstros na Arena.
 * Spawna monstros fora do campo de visão imediato do jogador (baseado na distância do Hero)
 * e escala a dificuldade com o tempo.
 */
class ArenaSpawnManager(private val gameState: GameState) {
    private val random = Random()
    private var lastSpawnTimeMs: Long = 0
    private var spawnIntervalMs: Long = 2000 // Inicia spawnando a cada 2.0 segundos

    companion object {
        private const val MAX_ACTIVE_MONSTERS = 12
    }

    /**
     * Atualiza o spawn de monstros com base no tempo decorrido.
     */
    fun update(currentTimeMs: Long, maze: MazeData) {
        // Só spawna monstros se estivermos na fase ativa de sobrevivência
        if (gameState.phase != com.ericleber.joguinho.core.GamePhase.PLAYING) return

        // Verifica o limite máximo de monstros ativos na arena
        val activeCount = gameState.monsters.count { it.isActive }
        if (activeCount >= MAX_ACTIVE_MONSTERS) return

        if (currentTimeMs - lastSpawnTimeMs > spawnIntervalMs) {
            spawnEnemy(maze)
            lastSpawnTimeMs = currentTimeMs

            // Dificuldade progressiva: diminui o intervalo de spawn conforme o tempo da fase passa
            // floorTimerMs vai acumulando, quanto maior o tempo, menor o intervalo de spawn (mínimo de 700ms)
            val elapsedSec = gameState.floorTimerMs / 1000f
            spawnIntervalMs = (2000L - (elapsedSec * 15L).toLong()).coerceAtLeast(700L)
        }
    }

    private fun spawnEnemy(maze: MazeData) {
        val heroX = gameState.heroPosition.x

        // Determina uma distância horizontal segura (ex: entre 8 e 12 tiles do herói) para spawnar fora da tela
        val offset = 8 + random.nextInt(5)
        val dir = if (random.nextBoolean()) 1 else -1
        var targetX = (heroX + offset * dir).toInt().coerceIn(1, maze.width - 2)

        // Se o ponto calculado cair muito perto do herói (devido ao clamp nas bordas do mapa), tenta o lado oposto
        if (Math.abs(targetX - heroX) < 6) {
            targetX = (heroX - offset * dir).toInt().coerceIn(1, maze.width - 2)
        }

        // Procura um tile de chão (FLOOR = 0) na coluna targetX de baixo para cima
        var targetY = -1
        for (y in (maze.height - 2) downTo 3) {
            val idx = y * maze.width + targetX
            val belowIdx = (y + 1) * maze.width + targetX
            if (maze.tiles[idx] == 0 && maze.tiles[belowIdx] == 1) {
                targetY = y
                break
            }
        }

        // Se não achou chão estável naquela coluna, procura a primeira coluna vizinha disponível
        if (targetY == -1) {
            val offsets = listOf(-2, -1, 1, 2)
            for (off in offsets) {
                val altX = (targetX + off).coerceIn(1, maze.width - 2)
                for (y in (maze.height - 2) downTo 3) {
                    val idx = y * maze.width + altX
                    val belowIdx = (y + 1) * maze.width + altX
                    if (maze.tiles[idx] == 0 && maze.tiles[belowIdx] == 1) {
                        targetX = altX
                        targetY = y
                        break
                    }
                }
                if (targetY != -1) break
            }
        }

        // Fallback se não encontrar nenhum lugar válido
        if (targetY == -1) return

        val pos = Position(targetX + 0.5f, targetY + 0.5f)

        // Escolhe o padrão de movimento
        val pattern = when (random.nextInt(4)) {
            0 -> MovementPattern.CHASE
            1 -> MovementPattern.PATROL_HORIZONTAL
            2 -> MovementPattern.RANDOM
            else -> MovementPattern.ZONING_DEFENDER
        }

        // Define o arquétipo
        val archetype = when (pattern) {
            MovementPattern.ZONING_DEFENDER -> MonsterArchetype.SHOOTER
            MovementPattern.CHASE -> if (random.nextFloat() < 0.35f) MonsterArchetype.DASHER else MonsterArchetype.MELEE
            else -> MonsterArchetype.MELEE
        }

        val monsterHp = 1 + (gameState.floorNumber / 15)
        val newMonster = MonsterState(
            id = "spawned_${System.currentTimeMillis()}_${random.nextInt(1000)}",
            position = pos,
            movementPattern = pattern,
            isActive = true,
            anchorPosition = if (pattern == MovementPattern.ZONING_DEFENDER) pos else null,
            hp = monsterHp,
            maxHp = monsterHp,
            archetype = archetype
        )

        gameState.monsters = gameState.monsters + newMonster
    }
}
