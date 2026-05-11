package com.ericleber.joguinho.core

import com.ericleber.joguinho.pcg.BSPMazeGenerator
import com.ericleber.joguinho.audio.TipoEfeito
import com.ericleber.joguinho.biome.BiomeWorld
import com.ericleber.joguinho.renderer.PortalState
import com.ericleber.joguinho.core.MonsterAIState
import com.ericleber.joguinho.core.Pathfinder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lógica central do jogo: colisões, movimento de entidades, detecção de Exit e ComboStreak.
 *
 * Chamado pelo GameLoop a cada frame via onUpdate, após o InputController processar o input.
 * Centraliza toda a lógica de gameplay que não é responsabilidade do Renderer ou do InputController.
 *
 * Responsabilidades:
 * - Mover Monsters conforme seus padrões (Requisito 5.9)
 * - Detectar colisão Hero↔Monster e aplicar Slowdown (Requisito 5.1)
 * - Detectar ativação de Traps por proximidade e aplicar Slowdown (Requisito 5.2)
 * - Mover Spike seguindo o Hero com pathfinding simples (Requisito 4.5)
 * - Detectar chegada do Hero ao Exit e emitir evento (Requisito 6.1)
 * - Incrementar ComboStreak ao completar Map sem Slowdown (Requisito 5.7)
 * - Salvar automaticamente ao final de cada Map (Requisito 7.1)
 *
 * Requisitos: 4.5, 5.1, 5.2, 5.7, 5.9, 6.1, 7.1
 */
class GameLogic(private val gameState: GameState) {

    companion object {
        /** Duração do Slowdown causado por Monster em ms (Requisito 5.1). */
        private const val SLOWDOWN_MONSTER_MS = 2000L

        /** Tempo máximo acumulado de Slowdown para não frustrar o jogador. */
        private const val SLOWDOWN_MAX_ACUMULADO_MS = 4000L

        /** Duração do Slowdown do Spike ao contato com Monster em ms (Requisito 5.1). */
        private const val SLOWDOWN_SPIKE_MS = 2000L

        /** Duração do Slowdown causado por Trap em ms (Requisito 5.2). */
        private const val SLOWDOWN_TRAP_MS = 2000L

        /** Distância máxima (em tiles) para ativar uma Trap (Requisito 5.2). */
        private const val TRAP_ACTIVATION_RADIUS = 1

        /** Distância máxima do Spike ao Hero antes de se mover (Requisito 4.5). */
        private const val SPIKE_MAX_DISTANCE = 2f

        /** Velocidade do Spike em tiles/segundo. */
        private const val SPIKE_SPEED_TILES_PER_SEC = 3.5f

        /** Velocidade base dos Monsters em tiles/segundo. */
        private const val MONSTER_SPEED_TILES_PER_SEC = 1.5f

        /** Tempo máximo (em segundos) que o Spike pode ficar travado antes de teleportar. */
        private const val SPIKE_STUCK_THRESHOLD_SEC = 3.0f

        /** Distância do Spike ao Hero que dispara teleporte de emergência (em tiles). */
        private const val SPIKE_TELEPORT_DISTANCE = 8f

        /** Fator de aumento de velocidade do Boss por andar (Floor). */
        private const val BOSS_SPEED_SCALING_PER_FLOOR = 0.015f

        /** Duração da lentidão severa do Boss. */
        private const val SLOWDOWN_BOSS_MS = 3500L

        /** Tempo inicial do mapa (5 minutos). */
        private const val MAP_TIMER_INITIAL_MS = 300000L
    }

    // REMOVIDO: Acumuladores de movimento sub-tile (Agora usamos movimento fluído direto)

    // Timers de padrão de movimento dos Monsters (para patrulha circular/aleatória)
    private val monsterTimers = mutableMapOf<String, Float>()

    // Rastreamento de travamento do Spike (pathfinding melhorado)
    private var spikeLastPosition: Position? = null
    private var spikeStuckTimerSec = 0f

    // Callback chamado quando o Hero chega ao Exit (para lançar ScoreActivity)
    var onHeroReachedExit: (() -> Unit)? = null

    // Callback chamado ao final de cada Map para salvar estado (Requisito 7.1)
    var onMapCompleted: (() -> Unit)? = null

    // Callback para solicitar reprodução de efeito sonoro
    var onSoundEffectRequested: ((TipoEfeito) -> Unit)? = null
    
    // Callbacks para o esguicho contínuo
    var onWaterStreamStarted: (() -> Unit)? = null
    var onWaterStreamStopped: (() -> Unit)? = null

    private var wasShootingLastFrame = false
    private var damageAccumulatorMs = 0L

    // Timers para atualização de IA (Fase 12)
    private val monsterAiTimers: MutableMap<String, Long> = mutableMapOf()
    private val monsterPathCooldowns: MutableMap<String, Long> = mutableMapOf()

    /**
     * Atualiza toda a lógica de jogo para o frame atual.
     * Deve ser chamado pelo GameLoop após o InputController processar o input.
     *
     * @param deltaTimeSec tempo do frame em segundos
     */
    fun update(deltaTimeSec: Float) {
        if (gameState.phase != GamePhase.PLAYING) return
        val maze = gameState.mazeData ?: return

        val deltaMs = (deltaTimeSec * 1000).toLong()

        // Atualiza timers de Slowdown
        if (gameState.heroIsSlowedDown) {
            gameState.heroSlowdownRemainingMs -= deltaMs
            if (gameState.heroSlowdownRemainingMs <= 0) {
                gameState.heroIsSlowedDown = false
                gameState.heroSlowdownRemainingMs = 0
            }
        }

        // Verifica colisão com itens
        verificarColisaoItens()

        // Atualiza timer de Buff de Velocidade
        if (gameState.heroHasSpeedBuff) {
            gameState.heroSpeedBuffRemainingMs -= deltaMs
            if (gameState.heroSpeedBuffRemainingMs <= 0) {
                gameState.heroHasSpeedBuff = false
                gameState.heroSpeedBuffRemainingMs = 0
            }
        }

        // Atualiza timer de mensagem do Boss
        if (gameState.bossMessage != null) {
            gameState.bossMessageTimerMs -= deltaMs
            if (gameState.bossMessageTimerMs <= 0) {
                gameState.bossMessage = null
            }
        }
        if (gameState.spikeIsSlowedDown) {
            gameState.spikeSlowdownRemainingMs -= (deltaTimeSec * 1000).toLong()
            if (gameState.spikeSlowdownRemainingMs <= 0) {
                gameState.spikeIsSlowedDown = false
                gameState.spikeSlowdownRemainingMs = 0
            }
        }

        // Se estiver em animação de saída, processa o timer e a transição
        if (gameState.isExiting) {
            gameState.exitAnimationTimerMs += (deltaTimeSec * 1000).toLong()
            if (gameState.exitAnimationTimerMs >= 800) { // 800ms de animação
                processarTransicaoNivel(maze)
            }
            return
        }

        atualizarBossFight(deltaMs, maze)
        atualizarMovimentoMonsters(deltaTimeSec, maze)
        atualizarSistemaAmbush(deltaTimeSec, maze) // Fase 10
        verificarColisaoHeroMonster(maze)
        atualizarMovimentoSpike(deltaTimeSec, maze)
        verificarHeroNoExit(maze)
        atualizarPortal(maze)          // Portal interdimensional
        atualizarEcologia(deltaTimeSec, maze) // Fase 12: Ecologia por Bioma
        atualizarWaterStream(deltaTimeSec, maze)
        atualizarVfx(deltaMs)
        atualizarFeedbackCombate(deltaMs)
        
        // Atualiza timer do mapa (5 minutos)
        gameState.mapTimerMs -= deltaMs
        if (gameState.mapTimerMs <= 0) {
            gameState.mapTimerMs = 0
            // Penalidade por tempo esgotado: Perde uma vida e reseta o timer
            if (gameState.heroLives > 0) {
                gameState.heroLives--
                gameState.mapTimerMs = 300000L // Reseta para 5 min
                if (gameState.heroLives <= 0) {
                    gameState.phase = GamePhase.GAME_OVER
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lógica do Boss (Fase 5)
    // -------------------------------------------------------------------------
    private fun atualizarBossFight(deltaMs: Long, maze: MazeData) {
        val bossAlive = gameState.monsters.any { it.isBoss && it.isActive }
        
        if (!gameState.bossFightState.isActive) {
            // Se não está ativa mas tem um boss vivo, algo ativou o boss (spawn)
            if (bossAlive) {
                gameState.bossFightState = gameState.bossFightState.copy(isActive = true, elapsedMs = 0L)
            }
            return
        }

        val state = gameState.bossFightState
        
        // Se o Boss morreu (não está mais na lista ou inativo), encerra a luta
        if (!bossAlive) {
            gameState.bossFightState = state.copy(isActive = false)
            return
        }

        // Verifica se Boss morreu por tempo (Vitória por sobrevivência - opcional, mantemos por segurança)
        if (state.elapsedMs >= state.totalDurationMs) {
            gameState.monsters = gameState.monsters.filterNot { it.isBoss }
            gameState.bossFightState = state.copy(isActive = false)
            return
        }

        // Incrementa tempo
        var newElapsed = state.elapsedMs + deltaMs
        var newNextAoe = state.nextAoeMs
        var newStun = state.bossStunRemainingMs
        var newDistracted = state.bossDistractedMs

        if (newStun > 0) newStun -= deltaMs
        if (newDistracted > 0) newDistracted -= deltaMs

        // Lógica de AoE na Fase 2 e 3 (Após 40s)

        gameState.bossFightState = state.copy(
            elapsedMs = newElapsed,
            nextAoeMs = newNextAoe,
            bossStunRemainingMs = Math.max(0L, newStun),
            bossDistractedMs = Math.max(0L, newDistracted)
        )
    }

    // -------------------------------------------------------------------------
    // Movimento dos Monsters (Requisito 5.9)
    // -------------------------------------------------------------------------

    /**
     * Move cada Monster ativo conforme seu padrão de movimento e estado de IA (FSM).
     */
    private fun atualizarMovimentoMonsters(deltaTimeSec: Float, maze: MazeData) {
        val heroPos = gameState.heroPosition
        val currentTime = System.currentTimeMillis()
        
        gameState.monsters = gameState.monsters.map { monster ->
            if (!monster.isActive) return@map monster

            // 1. Lógica de Transição de Estados (FSM)
            val distToHero = monster.position.dist(heroPos)
            
            // Boss sempre persegue ou ataca
            if (monster.isBoss) {
                if (gameState.bossFightState.bossStunRemainingMs > 0) return@map monster
                monster.aiState = MonsterAIState.CHASE
            } else {
                val newState = when (monster.aiState) {
                    MonsterAIState.AMBUSH -> {
                        if (distToHero < monster.ambushTriggerRadius) MonsterAIState.CHASE else MonsterAIState.AMBUSH
                    }
                    MonsterAIState.PATROL -> {
                        if (distToHero < 5f) MonsterAIState.CHASE else MonsterAIState.PATROL
                    }
                    MonsterAIState.CHASE -> {
                        if (distToHero > 8f) MonsterAIState.PATROL else MonsterAIState.CHASE
                    }
                    else -> monster.aiState
                }
                monster.aiState = newState
            }

            // 2. Lógica de Pathfinding (CHASE)
            if (monster.aiState == MonsterAIState.CHASE) {
                val lastCalc = monsterPathCooldowns[monster.id] ?: 0L
                val interval = if (monster.isBoss) 500L else 1200L // Boss recalcula mais rápido
                if (currentTime - lastCalc > interval) {
                    monster.targetPath = Pathfinder.findPath(monster.position, heroPos, maze)
                    monsterPathCooldowns[monster.id] = currentTime
                }
            }

            // 3. Execução do Movimento baseado no Estado
            val (dx, dy) = when {
                monster.isBoss -> {
                    // Boss usa sempre seu padrão de perseguição agressivo
                    val timer = (monsterTimers[monster.id] ?: 0f) + deltaTimeSec
                    monsterTimers[monster.id] = timer
                    calcularDirecaoMonster(monster, heroPos, timer)
                }
                monster.aiState == MonsterAIState.CHASE -> {
                    val path = monster.targetPath
                    if (path != null && path.size > 1) {
                        val nextPoint = path[1]
                        val pdx = nextPoint.x - monster.position.x
                        val pdy = nextPoint.y - monster.position.y
                        val pdist = sqrt(pdx * pdx + pdy * pdy)
                        if (pdist > 0.05f) Pair(pdx / pdist, pdy / pdist) else Pair(0f, 0f)
                    } else {
                        // Perseguição direta (linear) se o path falhar ou for nulo
                        val pdx = heroPos.x - monster.position.x
                        val pdy = heroPos.y - monster.position.y
                        val pdist = sqrt(pdx * pdx + pdy * pdy)
                        if (pdist > 0.05f) Pair(pdx / pdist, pdy / pdist) else Pair(0f, 0f)
                    }
                }
                monster.aiState == MonsterAIState.PATROL -> {
                    val timer = (monsterTimers[monster.id] ?: 0f) + deltaTimeSec
                    monsterTimers[monster.id] = timer
                    calcularDirecaoMonster(monster, heroPos, timer)
                }
                else -> Pair(0f, 0f)
            }

            // 4. Velocidade e Aplicação de Movimento
            val baseVel = when {
                monster.isBoss -> {
                    val phase3SpeedMult = if (gameState.bossFightState.elapsedMs >= 80000L) 1.5f else 1.0f
                    val bossFloorBonus = gameState.floorNumber * BOSS_SPEED_SCALING_PER_FLOOR
                    // Velocidade base aumentada de 1.2f para 2.0f (Proativo)
                    MONSTER_SPEED_TILES_PER_SEC * (2.0f + bossFloorBonus) * phase3SpeedMult
                }
                monster.aiState == MonsterAIState.CHASE -> MONSTER_SPEED_TILES_PER_SEC * 1.15f
                monster.movementPattern == MovementPattern.TANK_SLOW -> MONSTER_SPEED_TILES_PER_SEC * 0.6f
                else -> MONSTER_SPEED_TILES_PER_SEC
            }
            
            val rage = if (monster.isBoss) monster.rageMultiplier else 1.0f
            val velocidade = if (gameState.heroIsSlowedDown) baseVel * 0.7f * rage else baseVel * rage

            var nextX = (monster.position.x + dx * velocidade * deltaTimeSec).coerceIn(0f, maze.width - 1f)
            var nextY = (monster.position.y + dy * velocidade * deltaTimeSec).coerceIn(0f, maze.height - 1f)

            // Repulsão do Portal (apenas Boss)
            if (monster.isBoss) {
                val pX = maze.exitIndex % maze.width + 0.5f
                val pY = maze.exitIndex / maze.width + 0.5f
                if (monster.position.dist(Position(pX, pY)) < 1.5f) {
                    val angle = atan2((nextY - pY).toDouble(), (nextX - pX).toDouble())
                    nextX = pX + (cos(angle) * 1.5f).toFloat()
                    nextY = pY + (sin(angle) * 1.5f).toFloat()
                }
            }

            if (!checkMonsterCollision(nextX, nextY, maze, 0.3f)) {
                monster.position = Position(nextX, nextY)
            }
            
            monster
        }
    }

    private fun atualizarProvocacaoBoss(boss: MonsterState) {
        val frases = listOf(
            "Você não vai passar!",
            "O Spike parece delicioso...",
            "Fuja enquanto pode!",
            "Este bioma é meu!",
            "Onde pensa que vai?"
        )
        gameState.bossMessage = frases.random()
        gameState.bossMessageTimerMs = 3000L
        // Evento de áudio: Provocação do Boss
        onSoundEffectRequested?.invoke(TipoEfeito.BOSS_PROVOCACAO)
    }

    private fun verificarColisaoItens() {
        val heroPos = gameState.heroPosition
        gameState.items = gameState.items.map { item ->
            if (item.isActive && item.position.dist(heroPos) < 0.6f) {
                when (item.type) {
                    com.ericleber.joguinho.core.ItemType.SPEED_BOOTS -> {
                        gameState.heroHasSpeedBuff = true
                        gameState.heroSpeedBuffRemainingMs = 7000L
                        onSoundEffectRequested?.invoke(TipoEfeito.POWER_UP_COLETADO)
                    }
                    com.ericleber.joguinho.core.ItemType.HEART -> {
                        gameState.heroLives = (gameState.heroLives + 1).coerceAtMost(3)
                        onSoundEffectRequested?.invoke(TipoEfeito.POWER_UP_COLETADO)
                    }
                }
                item.copy(isActive = false)
            } else {
                item
            }
        }
    }


    /**
     * Verifica colisão de um monstro contra as paredes.
     */
    private fun checkMonsterCollision(x: Float, y: Float, maze: MazeData, radius: Float): Boolean {
        val left = (x - radius).toInt()
        val right = (x + radius).toInt()
        val top = (y - radius).toInt()
        val bottom = (y + radius).toInt()

        for (ty in top..bottom) {
            for (tx in left..right) {
                if (tx < 0 || ty < 0 || tx >= maze.width || ty >= maze.height) return true
                if (maze.tiles[ty * maze.width + tx] == 1) return true
            }
        }
        return false
    }

    /**
     * Calcula o vetor de direção (dx, dy) para um Monster conforme seu padrão.
     * Retorna valores entre -1.0 e 1.0.
     */
    private fun calcularDirecaoMonster(
        monster: com.ericleber.joguinho.core.MonsterState,
        heroPos: Position,
        timer: Float
    ): Pair<Float, Float> = when (monster.movementPattern) {

        MovementPattern.LINEAR -> {
            val fase = (timer * 0.5f) % 2f
            if (fase < 1f) Pair(1f, 0f) else Pair(-1f, 0f)
        }

        MovementPattern.PATROL_HORIZONTAL -> {
            // Patrulha curta (estilo Mario)
            val fase = (timer * 0.8f) % 4f
            if (fase < 2f) Pair(1f, 0f) else Pair(-1f, 0f)
        }

        MovementPattern.PATROL_VERTICAL -> {
            val fase = (timer * 0.8f) % 4f
            if (fase < 2f) Pair(0f, 1f) else Pair(0f, -1f)
        }

        MovementPattern.BOSS_STALKER -> {
            // Delay de atualização do Boss: recalcula alvo a cada 1.5s para dar chance ao player
            val updateIntervalMs = 1500L
            val tick = (gameState.bossFightState.elapsedMs / updateIntervalMs)
            
            // Usamos o ID do monstro e o tick para estabilizar a direção por um tempo
            val seed = monster.id.hashCode() + tick.toInt()
            val randomOffset = ((seed % 100) / 100f) * 0.5f // Leve variação aleatória
            
            var targetX = heroPos.x.toFloat()
            var targetY = heroPos.y.toFloat()

            val dx = targetX - monster.position.x
            val dy = targetY - monster.position.y
            val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            
            // O Boss agora tem um "lag" de movimento para não ser impossível
            if (dist > 0.1f) Pair(dx / dist, dy / dist) else Pair(0f, 0f)
        }

        MovementPattern.CHASE, MovementPattern.AMBUSH, MovementPattern.TANK_SLOW -> {
            // NUNCA persegue o player. Movimento padrão inteligente nos eixos X ou Y.
            // Escolhe eixo X ou Y baseado no ID do monstro e tempo
            val interval = 3000L // muda direção a cada 3s
            val moveTick = (timer * 1000 / interval).toLong()
            val seed = monster.id.hashCode() + moveTick.toInt()
            
            val isHorizontal = (seed % 2 == 0)
            val direction = if ((seed / 2) % 2 == 0) 1f else -1f
            
            if (isHorizontal) Pair(direction, 0f) else Pair(0f, direction)
        }

        MovementPattern.ZONING_DEFENDER -> {
            // Defende em torno do ponto âncora, sem seguir o player diretamente
            val anchor = monster.anchorPosition ?: monster.position
            val dxAnchor = anchor.x - monster.position.x
            val dyAnchor = anchor.y - monster.position.y
            val distToAnchor = sqrt(dxAnchor * dxAnchor + dyAnchor * dyAnchor)
            
            if (distToAnchor > 3f) {
                // Volta para a âncora se estiver longe
                Pair(dxAnchor / distToAnchor.toFloat(), dyAnchor / distToAnchor.toFloat())
            } else {
                // Patrulha aleatória em cruz perto da âncora
                val seed = monster.id.hashCode() + (timer.toInt() / 2)
                val dirs = listOf(Pair(1f, 0f), Pair(-1f, 0f), Pair(0f, 1f), Pair(0f, -1f))
                dirs[seed.coerceAtLeast(0) % 4]
            }
        }
        MovementPattern.CIRCULAR -> {
            val angulo = timer * 1.2f
            Pair(kotlin.math.cos(angulo.toDouble()).toFloat(), kotlin.math.sin(angulo.toDouble()).toFloat())
        }

        MovementPattern.RANDOM -> {
            val intervalo = (timer / 2f).toInt()
            val seed = monster.id.hashCode() xor intervalo
            val direcoes = listOf(Pair(1f, 0f), Pair(-1f, 0f), Pair(0f, 1f), Pair(0f, -1f), Pair(0f, 0f))
            direcoes[((seed and 0x7FFFFFFF) % direcoes.size)]
        }
    }

    // -------------------------------------------------------------------------
    // Colisão Hero↔Monster (Requisito 5.1)
    // -------------------------------------------------------------------------

    /**
     * Verifica se o Hero está na mesma posição de algum Monster ativo.
     * Aplica Slowdown ao Hero (3s) e ao Spike (2s), e recua o Monster 2 tiles.
     * Agora com acúmulo de tempo e cooldown de 2s por monstro.
     */
    private fun verificarColisaoHeroMonster(maze: MazeData) {
        val heroPos = gameState.heroPosition
        val currentTime = System.currentTimeMillis()

        gameState.monsters = gameState.monsters.map { monster ->
            if (!monster.isActive) return@map monster
            
            // Hitbox Dinâmica: Monstros maiores ocupam uma área de colisão maior.
            // - Pequeno (0.5x): Colisão apenas no mesmo tile.
            // - Médio (1.0x): Colisão no mesmo tile.
            // - Grande (1.5x): Colisão no mesmo tile e tiles adjacentes (raio 1).
            // - Boss (2.0x): Colisão em raio de 1.5 tiles.
            val seed = monster.id.hashCode()
            val scale = if (monster.isBoss) {
                2.0f
            } else if (monster.movementPattern == MovementPattern.TANK_SLOW) {
                1.8f
            } else if (monster.movementPattern == MovementPattern.AMBUSH) {
                0.8f
            } else {
                when (seed % 3) {
                    0 -> 0.9f
                    1 -> 1.2f
                    else -> 1.5f
                }
            }
            
            val dx = Math.abs(monster.position.x - heroPos.x)
            val dy = Math.abs(monster.position.y - heroPos.y)
            
            // Colisão baseada em distância (raio)
            val collisionRadius = if (monster.isBoss) 1.2f else 0.6f
            val isColliding = monster.position.dist(heroPos) < collisionRadius

            if (!isColliding) return@map monster

            // Proteção: Impedir dano de monstros que estejam longe demais (invisíveis/fora da tela)
            val monsterDistToHero = monster.position.dist(heroPos)
            if (monsterDistToHero > 6f) return@map monster // Ignora monstros fora de alcance visível

            // Verifica cooldown de 2 segundos para o mesmo monstro
            val lastCollision = gameState.monsterCollisionCooldowns[monster.id] ?: 0L
            if (currentTime - lastCollision < 2000L) return@map monster

            // Registra colisão para cooldown
            gameState.monsterCollisionCooldowns[monster.id] = currentTime

            // Lógica de Dano Direto (Corações)
            // Se tiver vidas, perde uma. Se chegar a 0 (menos de 1 completo), reseta para o início.
            if (gameState.heroLives > 0) {
                gameState.heroLives--
                onSoundEffectRequested?.invoke(TipoEfeito.LENTIDAO_INICIO)
            }
            
            // MECÂNICA DE RESET: Se após o dano estiver com 0, volta ao início do mapa
            if (gameState.heroLives <= 0) {
                gameState.heroLives = 0 // Garante 0
                // Som de derrota sutil (risada do boss ou lentidão)
                onSoundEffectRequested?.invoke(TipoEfeito.BOSS_RISADA)
                
                // REQUISITO: Se morrer no boss (andar par, mapa index 1), volta para o mapa 0 do mesmo andar
                if (gameState.floorNumber % 2 == 0 && gameState.mapIndex == 1) {
                    gameState.mapIndex = 0
                    onMapCompleted?.invoke()
                } else {
                    // Respawn no início do mapa atual
                    gameState.heroPosition = Position((maze.startIndex % maze.width) + 0.5f, (maze.startIndex / maze.width) + 0.5f)
                }
                
                gameState.heroIsSlowedDown = false
                gameState.heroSlowdownRemainingMs = 0
                
                // Penalidade: Herói ganha 1 vida para poder continuar tentando
                gameState.heroLives = 1 
            } else {
                // Se ainda tem vida, aplica o recuo e um pequeno slowdown visual
                gameState.heroIsSlowedDown = true
                gameState.heroSlowdownRemainingMs = SLOWDOWN_MONSTER_MS
            }
            
            gameState.currentMapClean = false
            gameState.mapSlowdownCount++

            // Spike é imune a danos e lentidão (Requisito: Spike não sofre dano ou lentidão)
            // gameState.spikeIsSlowedDown = true
            // gameState.spikeSlowdownRemainingMs += SLOWDOWN_SPIKE_MS

            // Emite evento para SpikeAI e HUD
            gameState.emitEvent(GameEvent.HeroReceivedSlowdown)
            gameState.resetComboStreak()

            // Recua Monster 2 tiles na direção oposta ao Hero
            val recuoX = (if (monster.position.x > heroPos.x) 1f else -1f) * 2f
            val recuoY = (if (monster.position.y > heroPos.y) 1f else -1f) * 2f
            val novaPosX = (monster.position.x + recuoX).coerceIn(0f, maze.width - 1f)
            val novaPosY = (monster.position.y + recuoY).coerceIn(0f, maze.height - 1f)
            val novoIndice = novaPosY.toInt() * maze.width + novaPosX.toInt()
            val novaPos = if (maze.tiles[novoIndice] == BSPMazeGenerator.TILE_FLOOR) {
                Position(novaPosX, novaPosY)
            } else {
                monster.position // não recua se a posição de recuo for parede
            }

            monster.copy(position = novaPos)
        }
    }

    // -------------------------------------------------------------------------
    // Ativação de Traps (Requisito 5.2)
    // -------------------------------------------------------------------------


    // -------------------------------------------------------------------------
    // Movimento do Spike (Requisito 4.5)
    // -------------------------------------------------------------------------

    /**
     * Move o Spike em direção ao Hero quando a distância excede SPIKE_MAX_DISTANCE.
     * Pathfinding simples: move um tile por vez na direção do Hero, evitando paredes.
     */
    private fun atualizarMovimentoSpike(deltaTimeSec: Float, maze: MazeData) {
        val heroPos = gameState.heroPosition
        val spikePos = gameState.spikePosition

        val dx = heroPos.x - spikePos.x
        val dy = heroPos.y - spikePos.y
        val distancia = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        // --- REQUISITO: Spike ataca o Boss (Prioridade) ---
        val boss = gameState.monsters.find { it.isBoss && it.isActive }
        if (boss != null) {
            val distHeroBoss = heroPos.dist(boss.position)
            val distSpikeBoss = spikePos.dist(boss.position)
            
            // Spike ataca se o Boss estiver no alcance
            if (distHeroBoss < 6.0f && distSpikeBoss < 5.0f) {
                if (gameState.spikeAttackTimerMs == 0L) {
                    val cooldownKey = "spike_attack_cooldown"
                    val lastAttack = monsterTimers[cooldownKey] ?: 0f
                    if (gameState.floorTimerMs.toFloat() - lastAttack > 1200f) {
                        gameState.spikeAttackTimerMs = 600L 
                        monsterTimers[cooldownKey] = gameState.floorTimerMs.toFloat()
                        
                        // Calcula vetor do bote (Lunge) em direção ao Boss
                        val dxB = boss.position.x - spikePos.x
                        val dyB = boss.position.y - spikePos.y
                        val distB = sqrt(dxB * dxB + dyB * dyB)
                        if (distB > 0.1f) {
                            // Ele se projeta 1.5 tiles na direção do Boss
                            gameState.spikeJumpOffsetX = (dxB / distB) * 1.5f
                            gameState.spikeJumpOffsetY = (dyB / distB) * 1.5f
                        }
                    }
                }
            }
        }

        // Processa animação de pulo (Z e Lunge)
        if (gameState.spikeAttackTimerMs > 0) {
            val totalDur = 600f
            val progress = (totalDur - gameState.spikeAttackTimerMs) / totalDur // 0.0 a 1.0
            
            // Parábola Z (Altura)
            gameState.spikeZ = 4.8f * progress * (1f - progress)
            
            // Arco de Lunge (Vai e Volta)
            // LungeProgress: 0.0 -> 1.0 (no ápice) -> 0.0 (no chão)
            val lungeProgress = if (progress <= 0.5f) progress * 2f else (1f - progress) * 2f
            // Aplicamos o offset baseado no vetor capturado no início
            // (Note: as variáveis offsetX/Y originais guardam o vetor total do bote)
            
            // No ápice (progress approx 0.5), aplica o dano e o VFX
            if (progress >= 0.5f && progress < 0.5f + (deltaTimeSec * 1000 / totalDur)) {
                boss?.let {
                    if (it.isActive) {
                        gameState.vfxList = gameState.vfxList + VfxState(
                            id = "spike_bite_${System.currentTimeMillis()}",
                            position = it.position,
                            type = VfxType.WATER_SPLASH,
                            createdAtMs = System.currentTimeMillis(),
                            durationMs = 400L
                        )
                        it.hp = (it.hp - 5).coerceAtLeast(0)
                        if (it.hp == 0) {
                            it.isActive = false
                            gameState.accumulatedScore += 100 
                        }
                        onSoundEffectRequested?.invoke(TipoEfeito.SPIKE_BITE)
                    }
                }
            }

            gameState.spikeAttackTimerMs = (gameState.spikeAttackTimerMs - (deltaTimeSec * 1000).toLong()).coerceAtLeast(0)
            if (gameState.spikeAttackTimerMs == 0L) {
                gameState.spikeZ = 0f
            }
            gameState.spikeCompanionState = "ENTUSIASMADO"
        } else {
            gameState.spikeZ = 0f
            gameState.spikeJumpOffsetX = 0f
            gameState.spikeJumpOffsetY = 0f
        }

        // Rastreamento de travamento e Estado de Animação (Patas)
        val movDist = if (spikeLastPosition != null) gameState.spikePosition.dist(spikeLastPosition!!) else 0f
        if (movDist > 0.01f) {
            gameState.spikeCompanionState = if (movDist > 0.1f) "CORRENDO" else "ANDANDO"
        } else if (gameState.spikeAttackTimerMs == 0L) {
            gameState.spikeCompanionState = "SENTADO"
        }
        
        spikeLastPosition = gameState.spikePosition.copy()
        if (spikeLastPosition == spikePos) {
            spikeStuckTimerSec += deltaTimeSec
        } else {
            spikeStuckTimerSec = 0f
            spikeLastPosition = spikePos
        }

        // Teleporte de emergência: se ficou travado por muito tempo E está longe do Hero
        if (spikeStuckTimerSec >= SPIKE_STUCK_THRESHOLD_SEC && distancia > SPIKE_TELEPORT_DISTANCE) {
            val destino = encontrarTileAdjacenteVazio(heroPos, maze)
            if (destino != null) {
                gameState.spikePosition = destino
                spikeStuckTimerSec = 0f
                spikeLastPosition = destino
                gameState.spikeCompanionState = "ENTUSIASMADO"
                return
            }
        }

        if (distancia <= SPIKE_MAX_DISTANCE) {
            return
        }

        // Velocidade reduzida em Slowdown
        val velocidade = if (gameState.spikeIsSlowedDown) SPIKE_SPEED_TILES_PER_SEC * 0.4f
                         else SPIKE_SPEED_TILES_PER_SEC

        // Movimento fluído em direção ao Herói com colisão simples
        val vx = (dx / distancia) * velocidade * deltaTimeSec
        val vy = (dy / distancia) * velocidade * deltaTimeSec

        val nextX = spikePos.x + vx
        val nextY = spikePos.y + vy
        
        var finalX = spikePos.x
        var finalY = spikePos.y

        if (nextX >= 0 && nextX < maze.width && maze.tiles[spikePos.iy * maze.width + nextX.toInt()] == BSPMazeGenerator.TILE_FLOOR) {
            finalX = nextX
        }
        if (nextY >= 0 && nextY < maze.height && maze.tiles[nextY.toInt() * maze.width + spikePos.ix] == BSPMazeGenerator.TILE_FLOOR) {
            finalY = nextY
        }

        gameState.spikePosition = Position(finalX, finalY)


        gameState.spikeCompanionState = when {
            gameState.spikeIsSlowedDown -> "SLOWDOWN_PROPRIO"
            distancia > 5f -> "CHAMANDO"
            else -> "SEGUINDO"
        }
    }

    /**
     * Encontra um tile vazio (FLOOR) adjacente à posição alvo.
     * Usado para o teleporte de emergência do Spike.
     */
    private fun encontrarTileAdjacenteVazio(alvo: Position, maze: MazeData): Position? {
        val offsets = listOf(
            Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1),
            Pair(-1, -1), Pair(1, -1), Pair(-1, 1), Pair(1, 1)
        )
        for ((ox, oy) in offsets) {
            val nx = (alvo.x + ox).toInt()
            val ny = (alvo.y + oy).toInt()
            if (nx < 0 || ny < 0 || nx >= maze.width || ny >= maze.height) continue
            val idx = ny * maze.width + nx
            if (maze.tiles[idx] == BSPMazeGenerator.TILE_FLOOR) {
                return Position(nx.toFloat(), ny.toFloat())
            }
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Detecção do Exit (Requisito 6.1)
    // -------------------------------------------------------------------------

    /**
     * Verifica se o Hero chegou ao tile de saída do labirinto.
     * Usa raio de 0 tiles (exato) para evitar término imediato ao nascer perto da saída.
     */
    private fun verificarHeroNoExit(maze: MazeData) {
        val heroX = gameState.heroPosition.x
        val heroY = gameState.heroPosition.y
        val exitX = maze.exitIndex % maze.width
        val exitY = maze.exitIndex / maze.width

        // Verifica se é uma Boss Fight em andamento
        if (gameState.monsters.any { it.isBoss && it.isActive }) {
            return // Porta travada enquanto o Boss estiver vivo!
        }

        // O herói deve estar próximo ao tile da saída (placa + escada).
        // Usamos distância Euclidiana (raio de 0.8 tiles) para que qualquer toque
        // na placa ou na escada (frente, trás, lados) ative a animação.
        val dx = (heroX - exitX).toFloat()
        val dy = (heroY - exitY).toFloat()
        val distSq = dx * dx + dy * dy
        if (distSq > 1.44f) return // Raio de 1.2 tiles (1.2 * 1.2 = 1.44) para cobrir a esfera azul do portal

        // Inicia animação de saída em vez de transição imediata
        gameState.isExiting = true
        gameState.exitAnimationTimerMs = 0L
        gameState.emitEvent(GameEvent.HeroReachedExit)
    }

    /**
     * Atualiza o estado visual do portal de saída baseado na distância do Hero.
     *
     * DORMANT   : dist > 5 tiles
     * AWAKENING : dist ≤ 5 tiles
     * OPEN      : dist ≤ 2.5 tiles
     *
     * Também calcula o mundo destino do portal ao entrar em AWAKENING pela primeira vez.
     */
    private fun atualizarPortal(maze: MazeData) {
        val heroX = gameState.heroPosition.x
        val heroY = gameState.heroPosition.y
        val exitX = (maze.exitIndex % maze.width).toFloat()
        val exitY = (maze.exitIndex / maze.width).toFloat()

        val dx = heroX - exitX
        val dy = heroY - exitY
        val dist = sqrt(dx * dx + dy * dy)

        val newState = when {
            dist <= 2.5f -> PortalState.OPEN
            dist <= 5.0f -> PortalState.AWAKENING
            else         -> PortalState.DORMANT
        }

        // Calcula mundo destino quando o portal acorda pela primeira vez
        if (newState == PortalState.AWAKENING && gameState.portalState == PortalState.DORMANT) {
            val nextFloor = gameState.floorNumber + 1
            gameState.portalDestWorld = BiomeWorld.fromFloor(nextFloor.coerceAtMost(120))
        }

        gameState.portalState = newState
    }

    /**
     * Processa a transição real de nível após a animação do portal.
     */
    private fun processarTransicaoNivel(maze: MazeData) {
        gameState.isExiting = false
        gameState.exitAnimationTimerMs = 0L

        // Incrementa ComboStreak se completou sem Slowdown (Requisito 5.7)
        if (gameState.currentMapClean) {
            gameState.incrementComboStreak()
            gameState.emitEvent(GameEvent.HeroSurpassedObstacle)
        }
        
        // Reseta contador de lentidões para o próximo mapa
        gameState.mapSlowdownCount = 0
        
        // Reseta o timer de sobrevivência do mapa para 5 minutos
        gameState.mapTimerMs = MAP_TIMER_INITIAL_MS

        // Reseta o portal para o próximo mapa (começa DORMANT)
        gameState.portalState = PortalState.DORMANT

        // Emite evento de conclusão de mapa
        gameState.emitEvent(GameEvent.MapCompleted)

        // Muda a fase para evitar processamento repetido da saída no mesmo frame
        gameState.phase = GamePhase.LOADING

        // Verifica se é o último Map do Floor (2 Maps por Floor)
        val totalMapsNoFloor = 2
        if (gameState.mapIndex >= totalMapsNoFloor - 1) {
            // Completou o Floor — Avança para o próximo Floor automaticamente (até o 120)
            if (gameState.floorNumber < 120) {
                gameState.floorNumber++
                gameState.mapIndex = 0
                gameState.currentMapClean = true
                // Emite eventos de conclusão de andar
                gameState.completarAndar(gameState.floorTimerMs)
                gameState.floorTimerMs = 0 // Reseta o timer para o novo andar
                
                // Notifica o ViewModel para regenerar o mapa para o novo Floor
                onMapCompleted?.invoke()
                onHeroReachedExit?.invoke()
            } else {
                // Chegou ao fim do jogo (Piso 120)
                gameState.completarAndar(gameState.floorTimerMs)
                gameState.phase = GamePhase.SCORE_SCREEN
                onMapCompleted?.invoke()
                onHeroReachedExit?.invoke()
            }
        } else {
            // Avança para o próximo Map do mesmo Floor
            gameState.mapIndex++
            gameState.currentMapClean = true
            onMapCompleted?.invoke()
            onHeroReachedExit?.invoke()
        }
    }
    private fun atualizarVfx(deltaMs: Long) {
        val currentTime = System.currentTimeMillis()
        gameState.vfxList = gameState.vfxList.filter { 
            (currentTime - it.createdAtMs) < it.durationMs
        }
    }

    private fun atualizarFeedbackCombate(deltaMs: Long) {
        val currentTime = System.currentTimeMillis()
        
        // 1. Atualiza Flash de Dano nos Monstros
        for (monster in gameState.monsters) {
            if (monster.damageFlashRemainingMs > 0) {
                monster.damageFlashRemainingMs = Math.max(0L, monster.damageFlashRemainingMs - deltaMs)
            }
        }

        // 2. Atualiza Popups de Score
        val newPopups = gameState.scorePopups.toMutableList()
        val removeList = mutableListOf<ScorePopup>()
        
        for (popup in newPopups) {
            val elapsed = currentTime - popup.createdAtMs
            val progress = elapsed.toFloat() / popup.durationMs
            
            if (progress >= 1.0f) {
                removeList.add(popup)
                com.ericleber.joguinho.ui.ScorePopupPool.recycle(popup)
            } else {
                // Sobe suavemente
                popup.offsetY = progress * 60f 
                // Fade out no final
                if (progress > 0.5f) {
                    popup.alpha = ((1.0f - (progress - 0.5f) * 2f) * 255).toInt().coerceIn(0, 255)
                }
            }
        }
        
        if (removeList.isNotEmpty()) {
            gameState.scorePopups = newPopups.filter { it !in removeList }
        }
    }

    private fun atualizarWaterStream(deltaTimeSec: Float, maze: MazeData) {
        val currentTime = System.currentTimeMillis()
        val isShooting = gameState.isShooting
        
        // --- Gerenciamento de Som ---
        if (isShooting && !wasShootingLastFrame) {
            onWaterStreamStarted?.invoke()
        } else if (!isShooting && wasShootingLastFrame) {
            onWaterStreamStopped?.invoke()
        }
        wasShootingLastFrame = isShooting

        if (!isShooting) {
            gameState.waterStreamDistance = 0f
            gameState.waterStreamVisualDistance = 0f
            gameState.waterStreamImpactPos = null
            return
        }

        // --- Raycasting para o Esguicho (Twin-Stick) ---
        val maxDistance = 7.0f // Distância máxima do esguicho
        val step = 0.2f // Precisão do raio
        
        // --- Cálculo Preciso da Origem (Ponta da Arma) ---
        val u = 0.05f
        val facingLeft = kotlin.math.abs(gameState.shootingAngle) > Math.PI / 2
        
        // Ombro (Pivot do braço) relativo ao centro do herói
        val pivotX = -0.02f
        val pivotY = -0.425f
        
        // Mão relativa ao ombro (Braço estendido com holdAngle de -35º)
        val holdAngleDeg = -35f
        val holdAngleRad = Math.toRadians(holdAngleDeg.toDouble()).toFloat()
        val armLen = 0.25f // 5u
        val hX_hand = pivotX - armLen * kotlin.math.sin(holdAngleRad)
        val hY_hand = pivotY + armLen * kotlin.math.cos(holdAngleRad)
        
        // Rotação da arma (Compensa facingLeft para bater com o Renderer)
        var weaponRotDeg = Math.toDegrees(gameState.shootingAngle.toDouble()).toFloat()
        if (facingLeft) {
            weaponRotDeg = if (weaponRotDeg >= 0) 180f - weaponRotDeg else -180f - weaponRotDeg
        }
        val finalWeaponRotRad = Math.toRadians(weaponRotDeg.toDouble()).toFloat()
        
        // Ponta da arma (Muzzle) relativa à mão (10u de comprimento, -0.75u de altura)
        val tipRelX = 0.5f 
        val tipRelY = -0.0375f
        
        val tipX_rot = tipRelX * kotlin.math.cos(finalWeaponRotRad) - tipRelY * kotlin.math.sin(finalWeaponRotRad)
        val tipY_rot = tipRelX * kotlin.math.sin(finalWeaponRotRad) + tipRelY * kotlin.math.cos(finalWeaponRotRad)
        
        var finalOffX = hX_hand + tipX_rot
        var finalOffY = hY_hand + tipY_rot
        
        // Aplica o espelhamento se estiver olhando para a esquerda
        if (facingLeft) {
            finalOffX = -finalOffX
        }
        
        val dx = kotlin.math.cos(gameState.shootingAngle.toDouble()).toFloat()
        val dy = kotlin.math.sin(gameState.shootingAngle.toDouble()).toFloat()
        
        val origin = Position(
            gameState.heroPosition.x + 0.5f + finalOffX,
            gameState.heroPosition.y + 0.5f + finalOffY
        )
        
        var currentDist = 0f
        var impactPos = origin
        var hitMonsterId: String? = null

        while (currentDist < maxDistance) {
            currentDist += step
            val checkX = origin.x + dx * currentDist
            val checkY = origin.y + dy * currentDist
            val checkPos = Position(checkX, checkY)

            // 1. Colisão com Parede
            val ix = checkX.toInt()
            val iy = checkY.toInt()
            if (ix < 0 || iy < 0 || ix >= maze.width || iy >= maze.height || maze.tiles[iy * maze.width + ix] == 1) {
                impactPos = checkPos
                break
            }

            // 2. Colisão com Monstros
            val monster = gameState.monsters.find { m ->
                if (!m.isActive) return@find false
                val dist = m.position.dist(checkPos)
                val radius = if (m.isBoss) 1.2f else 0.6f
                dist < radius
            }
            if (monster != null) {
                impactPos = checkPos
                hitMonsterId = monster.id
                break
            }
            
            impactPos = checkPos
        }

        gameState.waterStreamDistance = currentDist
        
        // Extensão progressiva (cresce 30 tiles por segundo)
        if (gameState.waterStreamVisualDistance < currentDist) {
            gameState.waterStreamVisualDistance = (gameState.waterStreamVisualDistance + deltaTimeSec * 30f).coerceAtMost(currentDist)
        } else {
            gameState.waterStreamVisualDistance = currentDist
        }
        
        // Calcula a posição de impacto visual (onde o jato termina no frame atual)
        val visualImpactX = origin.x + dx * gameState.waterStreamVisualDistance
        val visualImpactY = origin.y + dy * gameState.waterStreamVisualDistance
        gameState.waterStreamImpactPos = Position(visualImpactX, visualImpactY)

        // --- Dano Contínuo (apenas se o jato visual atingiu o alvo) ---
        if (hitMonsterId != null && gameState.waterStreamVisualDistance >= currentDist - 0.1f) {
            damageAccumulatorMs += (deltaTimeSec * 1000).toLong()
            if (damageAccumulatorMs >= 150) {
                damageAccumulatorMs = 0
                gameState.monsters.forEach { m ->
                    if (m.id == hitMonsterId && m.isActive) {
                        val damage = 1
                        val wasAlive = m.hp > 0
                        m.hp = (m.hp - damage).coerceAtLeast(0)
                        val isNowDead = wasAlive && m.hp == 0
                        
                        m.isActive = m.hp > 0
                        m.lastHitTimeMs = currentTime
                        m.damageFlashRemainingMs = 150L // Flash de 150ms
                        
                        // Score e Popup apenas na morte
                        if (isNowDead) {
                            val points = if (m.isBoss) 500 else 10
                            gameState.accumulatedScore += points
                            
                            // Requisito: Recupera um coração após derrotar o Chefe
                            if (m.isBoss) {
                                gameState.bossesDefeatedCount++
                                gameState.heroLives = (gameState.heroLives + 1).coerceAtMost(3)
                                onSoundEffectRequested?.invoke(TipoEfeito.POWER_UP_COLETADO)
                            }
                            
                            val popup = com.ericleber.joguinho.ui.ScorePopupPool.obtain(
                                id = "score_${m.id}_${currentTime}",
                                position = m.position,
                                score = points,
                                currentTimeMs = currentTime
                            )
                            gameState.scorePopups = gameState.scorePopups + popup
                        }
                    }
                }
            }
        } else {
            damageAccumulatorMs = 0
        }

        // --- VFX de Respingo no Impacto ---
        if (System.currentTimeMillis() % 100 < 50) { // Gera partículas intermitentes para economia
            val splashVfx = VfxState(
                id = "stream_splash_${System.currentTimeMillis()}",
                position = impactPos,
                type = VfxType.WATER_SPLASH,
                createdAtMs = System.currentTimeMillis(),
                durationMs = 200L
            )
            gameState.vfxList = gameState.vfxList + splashVfx
        }
    }

    /**
     * Sistema de Emboscada (Requisito 10.6).
     * Desperta inimigos camuflados ou gera ataques de surpresa.
     */
    private var ambushCooldownMs = 0L

    private fun atualizarSistemaAmbush(deltaTimeSec: Float, maze: MazeData) {
        if (gameState.phase != GamePhase.PLAYING) return
        val currentTime = System.currentTimeMillis()
        val world = gameState.currentBiomeWorld
        
        // Só ocorre em mundos perigosos (ex: Floresta, Abismo, Base Lunar)
        if (world != BiomeWorld.FLORESTA_DE_ARVORES && world != BiomeWorld.ABISMO_DO_VAZIO && world != BiomeWorld.BASE_LUNAR) return

        if (ambushCooldownMs > 0) {
            ambushCooldownMs -= (deltaTimeSec * 1000).toLong()
            return
        }

        val heroPos = gameState.heroPosition
        
        // 1. Emboscada por Proximidade (Mímica)
        // Checa tiles ao redor do herói
        val radius = 2
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val tx = (heroPos.x + dx).toInt()
                val ty = (heroPos.y + dy).toInt()
                if (tx < 0 || ty < 0 || tx >= maze.width || ty >= maze.height) continue
                
                val idx = ty * maze.width + tx
                // Se for uma "Parede" em mundo aberto, pode ser um mímico
                if (maze.tiles[idx] == 1) {
                    val rng = java.util.Random((tx * 31 + ty * 17 + gameState.floorSeed).toLong())
                    if (rng.nextFloat() > 0.995f) { // Chance rara por frame
                        despertarMimico(tx, ty, maze)
                        ambushCooldownMs = 5000L // Cooldown global de emboscada
                        return
                    }
                }
            }
        }
    }

    private fun despertarMimico(tx: Int, ty: Int, maze: MazeData) {
        // Transforma o tile em chão (o monstro "saiu" da parede/árvore)
        maze.tiles[ty * maze.width + tx] = 0
        
        // Spawn de monstro agressivo
        val ambushMonster = MonsterState(
            id = "ambush_${System.currentTimeMillis()}",
            position = Position(tx.toFloat() + 0.5f, ty.toFloat() + 0.5f),
            movementPattern = MovementPattern.AMBUSH,
            isActive = true,
            hp = 2,
            maxHp = 2,
            rageMultiplier = 1.4f // Rápido ao despertar
        )
        gameState.monsters = gameState.monsters + ambushMonster
        
        // Feedback visual/sonoro
        onSoundEffectRequested?.invoke(TipoEfeito.BOSS_RISADA)
        // Adiciona VFX de impacto
        gameState.vfxList = gameState.vfxList + VfxState(
            id = "ambush_vfx_${System.currentTimeMillis()}",
            position = Position(tx.toFloat(), ty.toFloat()),
            type = VfxType.WATER_SPLASH,
            createdAtMs = System.currentTimeMillis(),
            durationMs = 500L
        )
    }

    /**
     * Fase 12: Implementa comportamentos únicos baseados no ecossistema do bioma.
     */
    private fun atualizarEcologia(deltaTimeSec: Float, maze: MazeData) {
        val world = gameState.currentBiomeWorld
        val currentTime = System.currentTimeMillis()

        // 1. Efeitos nos Monstros e Geração de VFX de rastro
        val newVfx = mutableListOf<VfxState>()
        
        gameState.monsters.forEach { monster ->
            if (!monster.isActive) return@forEach
            
            // LAVA/VULCÂNICO: Rastro de fogo constante
            if (world == BiomeWorld.NUCLEO_DE_FOGO) {
                // A cada ~250ms gera um ponto de fogo
                if ((currentTime / 250) % 100 != ((currentTime - (deltaTimeSec*1000).toLong()) / 250) % 100) {
                    newVfx.add(VfxState(
                        id = "fire_${monster.id}_${currentTime}",
                        position = Position(monster.position.x, monster.position.y),
                        type = VfxType.FIRE_TRAIL,
                        createdAtMs = currentTime,
                        durationMs = 2500L
                    ))
                }
            }
            
            // ABISMO: Blink (Teletransporte aleatório sutil)
            if (world == BiomeWorld.ABISMO_DO_VAZIO) {
                val blinkSeed = monster.id.hashCode() + (currentTime / 3000).toInt()
                if (monster.aiState == MonsterAIState.CHASE && (currentTime % 3000 < (deltaTimeSec * 1000))) {
                    val angle = (blinkSeed % 360) * Math.PI / 180.0
                    val bX = monster.position.x + (Math.cos(angle) * 2.2f).toFloat()
                    val bY = monster.position.y + (Math.sin(angle) * 2.2f).toFloat()
                    
                    if (!checkMonsterCollision(bX, bY, maze, 0.4f)) {
                         newVfx.add(VfxState(
                            id = "blink_${monster.id}_$currentTime",
                            position = monster.position,
                            type = VfxType.BLINK_SHADOW,
                            createdAtMs = currentTime,
                            durationMs = 600L
                         ))
                         monster.position = Position(bX, bY)
                         onSoundEffectRequested?.invoke(TipoEfeito.BOSS_RISADA) // Som de blink
                    }
                }
            }
        }
        
        if (newVfx.isNotEmpty()) {
            gameState.vfxList = gameState.vfxList + newVfx
        }

        // 2. Interação do Hero com a Ecologia (Dano de área/Lentidão)
        gameState.vfxList.forEach { vfx ->
            if (vfx.type == VfxType.FIRE_TRAIL) {
                if (gameState.heroPosition.dist(vfx.position) < 0.7f) {
                    gameState.heroIsSlowedDown = true
                    gameState.heroSlowdownRemainingMs = Math.max(gameState.heroSlowdownRemainingMs, 800L)
                }
            }
        }
    }
}
