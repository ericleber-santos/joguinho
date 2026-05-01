package com.ericleber.joguinho.core

import com.ericleber.joguinho.pcg.BSPMazeGenerator
import com.ericleber.joguinho.audio.TipoEfeito
import kotlin.math.abs
import kotlin.math.sqrt

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
        verificarInteracaoHeroiElementos(maze)

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
        verificarColisaoHeroMonster(maze)
        verificarAtivacaoTraps(maze)
        atualizarMovimentoSpike(deltaTimeSec, maze)
        verificarHeroNoExit(maze)
        
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
        if (!gameState.bossFightState.isActive) return

        val state = gameState.bossFightState
        
        // Verifica se Boss morreu por tempo (Vitória)
        if (state.elapsedMs >= state.totalDurationMs) {
            gameState.monsters = gameState.monsters.filterNot { it.isBoss }
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
        if (newElapsed >= 40000L && newElapsed >= newNextAoe) {
            // Cria um AoE no jogador
            val newAoe = com.ericleber.joguinho.core.AoeZone(
                position = gameState.heroPosition,
                createdAtMs = newElapsed,
                explodesAtMs = newElapsed + 2000L // 2 segundos para explodir
            )
            gameState.bossAoeZones = gameState.bossAoeZones + newAoe
            newNextAoe = newElapsed + 5000L // cooldown de 5 segundos
            onSoundEffectRequested?.invoke(TipoEfeito.BOSS_PROVOCACAO) // Toca um som de aviso
        }

        // Processa explosões de AoE
        val currentZones = gameState.bossAoeZones.toMutableList()
        val zonesToRemove = mutableListOf<com.ericleber.joguinho.core.AoeZone>()
        for (zone in currentZones) {
            if (newElapsed >= zone.explodesAtMs) {
                // Explode!
                val dx = Math.abs(gameState.heroPosition.x - zone.position.x)
                val dy = Math.abs(gameState.heroPosition.y - zone.position.y)
                if (dx <= 1 && dy <= 1) { // 3x3 área
                    gameState.heroIsSlowedDown = true
                    gameState.heroSlowdownRemainingMs = 4000L // Slowdown Crítico
                    gameState.resetComboStreak()
                    onSoundEffectRequested?.invoke(TipoEfeito.LENTIDAO_INICIO)
                }
                zonesToRemove.add(zone)
            }
        }
        if (zonesToRemove.isNotEmpty()) {
            gameState.bossAoeZones = currentZones - zonesToRemove
        }

        // Atualiza cooldowns de elementos de sobrevivência
        gameState.survivalElements = gameState.survivalElements.map { elem ->
            if (elem.cooldownRemainingMs > 0) {
                elem.copy(cooldownRemainingMs = Math.max(0L, elem.cooldownRemainingMs - deltaMs))
            } else {
                elem
            }
        }

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
     * Move cada Monster ativo conforme seu padrão de movimento.
     * Padrões: LINEAR, CIRCULAR, RANDOM, CHASE.
     * Monsters não atravessam paredes.
     */
    private fun atualizarMovimentoMonsters(deltaTimeSec: Float, maze: MazeData) {
        val heroPos = gameState.heroPosition
        
        gameState.monsters = gameState.monsters.map { monster ->
            if (!monster.isActive) return@map monster

            // --- Lógica Específica do Boss ---
            if (monster.isBoss) {
                val state = gameState.bossFightState
                // Boss atordoado (gelo) não se move
                if (state.bossStunRemainingMs > 0) return@map monster
                
                // Verifica se está na Lama (reduz velocidade) e não está na Fase 3 (ignora lama)
                val inMud = gameState.survivalElements.any { 
                    it.type == com.ericleber.joguinho.core.SurvivalElementType.MUD_SWAMP && it.position == monster.position 
                }
                
                // Calcula velocidade base do Boss com bônus de andar e Fase 3
                val phase3SpeedMult = if (state.elapsedMs >= 80000L) 1.5f else 1.0f
                val mudSlowMult = if (inMud && state.elapsedMs < 80000L) 0.6f else 1.0f // 40% slow
                
                val bossFloorBonus = gameState.floorNumber * BOSS_SPEED_SCALING_PER_FLOOR
                val baseVel = MONSTER_SPEED_TILES_PER_SEC * (1.2f + bossFloorBonus) * phase3SpeedMult * mudSlowMult
                val velocidade = if (gameState.heroIsSlowedDown) baseVel * 0.7f else baseVel
                
                val timer = (monsterTimers[monster.id] ?: 0f) + deltaTimeSec
                monsterTimers[monster.id] = timer

                val (dx, dy) = calcularDirecaoMonster(monster, heroPos, timer)

                // Lógica de Rage: Se o herói estiver longe (> 6 tiles), a raiva aumenta gradualmente
                val distToHeroReal = monster.position.dist(heroPos)
                var newRage = monster.rageMultiplier
                if (distToHeroReal > 6f) {
                    newRage = (newRage + 0.1f * deltaTimeSec).coerceAtMost(2.0f) // Máximo 2x velocidade
                } else if (distToHeroReal < 3f) {
                    newRage = (newRage - 0.2f * deltaTimeSec).coerceAtLeast(1.0f) // Acalma se perto
                }

                val velocidadeFinal = velocidade * newRage
                val nextX = (monster.position.x + dx * velocidadeFinal * deltaTimeSec).coerceIn(0f, maze.width - 1f)
                val nextY = (monster.position.y + dy * velocidadeFinal * deltaTimeSec).coerceIn(0f, maze.height - 1f)

                // Verifica colisão com Pilar de Pedra ou Caixa para destruir (usando ix/iy para o tile)
                val hitElement = gameState.survivalElements.find { 
                    (it.type == com.ericleber.joguinho.core.SurvivalElementType.STONE_PILLAR || it.type == com.ericleber.joguinho.core.SurvivalElementType.PUSHABLE_BOX) 
                    && it.position.ix == nextX.toInt() && it.position.iy == nextY.toInt() && it.active
                }
                
                if (hitElement != null) {
                    // Boss colidiu com elemento destrutível!
                    gameState.bossFightState = gameState.bossFightState.copy(bossStunRemainingMs = gameState.bossFightState.bossStunRemainingMs + 1000L) // Delay de 1s
                    onSoundEffectRequested?.invoke(TipoEfeito.BOSS_PROVOCACAO) // Som de destruição

                    if (hitElement.type == com.ericleber.joguinho.core.SurvivalElementType.PUSHABLE_BOX) {
                        // Caixa é destruída na hora e dá delay de 2s
                        gameState.bossFightState = gameState.bossFightState.copy(bossStunRemainingMs = gameState.bossFightState.bossStunRemainingMs + 2000L)
                        gameState.survivalElements = gameState.survivalElements.map { if (it.id == hitElement.id) it.copy(active = false) else it }
                    } else if (hitElement.type == com.ericleber.joguinho.core.SurvivalElementType.STONE_PILLAR) {
                        val newDurability = hitElement.durability - 1
                        gameState.survivalElements = gameState.survivalElements.map { 
                            if (it.id == hitElement.id) it.copy(durability = newDurability, active = newDurability > 0) else it 
                        }
                    }
                    return@map monster // Não avança, gasta o movimento atacando
                }

                // Não atravessa paredes (checa com raio de 0.3 para evitar atravessamento lateral)
                val isWall = checkMonsterCollision(nextX, nextY, maze, 0.3f)
                // Evita ficar EXATAMENTE em cima do herói (mantém pequena distância)
                val distToHero = monster.position.dist(heroPos)

                if (isWall || distToHero < 0.5f) {
                    return@map monster.copy(rageMultiplier = newRage)
                } else {
                    return@map monster.copy(position = Position(nextX, nextY), rageMultiplier = newRage)
                }
            }

            // --- Lógica de Monstros Normais ---
            val baseVel = if (monster.movementPattern == MovementPattern.TANK_SLOW) {
                MONSTER_SPEED_TILES_PER_SEC * 0.5f // Metade da velocidade base
            } else if (monster.movementPattern == MovementPattern.AMBUSH) {
                MONSTER_SPEED_TILES_PER_SEC * 1.5f // 50% mais rápido quando se move
            } else {
                MONSTER_SPEED_TILES_PER_SEC
            }
            val velocidade = if (gameState.heroIsSlowedDown) baseVel * 0.7f else baseVel

            val timer = (monsterTimers[monster.id] ?: 0f) + deltaTimeSec
            monsterTimers[monster.id] = timer

            val (dx, dy) = calcularDirecaoMonster(monster, heroPos, timer)

            val nextX = (monster.position.x + dx * velocidade * deltaTimeSec).coerceIn(0f, maze.width - 1f)
            val nextY = (monster.position.y + dy * velocidade * deltaTimeSec).coerceIn(0f, maze.height - 1f)

            // Não atravessa paredes (checa o tile destino)
            val indice = nextY.toInt() * maze.width + nextX.toInt()
            if (maze.tiles[indice] == BSPMazeGenerator.TILE_WALL) {
                monster
            } else {
                monster.copy(position = Position(nextX, nextY))
            }
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
                }
                item.copy(isActive = false)
            } else {
                item
            }
        }
    }

    private fun verificarInteracaoHeroiElementos(maze: MazeData) {
        if (!gameState.bossFightState.isActive) return
        
        val heroPos = gameState.heroPosition
        
        gameState.survivalElements = gameState.survivalElements.map { elem ->
            if (!elem.active) return@map elem
            
            // Tocha de Gelo (Proximidade)
            if (elem.type == com.ericleber.joguinho.core.SurvivalElementType.ICE_TORCH && elem.position.dist(heroPos) < 0.8f && elem.cooldownRemainingMs <= 0) {
                gameState.bossFightState = gameState.bossFightState.copy(
                    bossStunRemainingMs = gameState.bossFightState.bossStunRemainingMs + 4000L // 4s Stun
                )
                onSoundEffectRequested?.invoke(TipoEfeito.POWER_UP_COLETADO)
                return@map elem.copy(cooldownRemainingMs = 20000L) // 20s cooldown
            }
            
            // Sino de Distração (Proximidade)
            if (elem.type == com.ericleber.joguinho.core.SurvivalElementType.DISTRACTION_BELL && elem.position.dist(heroPos) < 0.8f && !gameState.bossFightState.bellUsed) {
                gameState.bossFightState = gameState.bossFightState.copy(
                    bossDistractedMs = 6000L,
                    bellUsed = true
                )
                onSoundEffectRequested?.invoke(TipoEfeito.POWER_UP_COLETADO)
                return@map elem.copy(active = false)
            }
            
            // Empurrar Caixas
            if (elem.type == com.ericleber.joguinho.core.SurvivalElementType.PUSHABLE_BOX) {
                val dx = elem.position.x - heroPos.x
                val dy = elem.position.y - heroPos.y
                
                // Se o herói estiver adjacente (distância < 1.2f) e estiver tentando ir na direção da caixa
                if (Math.abs(dx) + Math.abs(dy) < 1.2f) {
                    val directionMatch = when (gameState.heroDirection) {
                        com.ericleber.joguinho.core.Direction.NORTH -> dy < -0.4f && Math.abs(dx) < 0.4f
                        com.ericleber.joguinho.core.Direction.SOUTH -> dy > 0.4f && Math.abs(dx) < 0.4f
                        com.ericleber.joguinho.core.Direction.EAST -> dx > 0.4f && Math.abs(dy) < 0.4f
                        com.ericleber.joguinho.core.Direction.WEST -> dx < -0.4f && Math.abs(dy) < 0.4f
                        else -> false
                    }
                    
                    if (directionMatch) {
                        val pushX = if (Math.abs(dx) > 0.5f) elem.position.x + (if (dx > 0) 1f else -1f) else elem.position.x
                        val pushY = if (Math.abs(dy) > 0.5f) elem.position.y + (if (dy > 0) 1f else -1f) else elem.position.y
                        val pushPos = Position(pushX, pushY)
                        
                        // Verifica se o tile atrás da caixa está livre
                        val pushIdx = pushPos.iy * maze.width + pushPos.ix
                        val isWall = pushPos.ix < 0 || pushPos.iy < 0 || pushPos.ix >= maze.width || pushPos.iy >= maze.height || maze.tiles[pushIdx] == 1
                        val hasElement = gameState.survivalElements.any { it.active && it.position.ix == pushPos.ix && it.position.iy == pushPos.iy && it.type != com.ericleber.joguinho.core.SurvivalElementType.MUD_SWAMP }
                        
                        if (!isWall && !hasElement) {
                            return@map elem.copy(position = pushPos)
                        }
                    }
                }
            }

            elem
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
            
            if (gameState.bossFightState.bossDistractedMs > 0) {
                val bell = gameState.survivalElements.find { it.type == com.ericleber.joguinho.core.SurvivalElementType.DISTRACTION_BELL && it.active }
                if (bell != null) {
                    targetX = bell.position.x
                    targetY = bell.position.y
                }
            }

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

            // Lógica de Dano/Slowdown baseada no tipo de monstro
            if (monster.isBoss) {
                if (gameState.heroIsSlowedDown && (currentTime - gameState.heroLastSlowdownTimeMs) < SLOWDOWN_BOSS_MS) {
                    // Já está sob efeito do Boss e foi pego de novo! Perde vida.
                    gameState.heroLives--
                    onSoundEffectRequested?.invoke(TipoEfeito.BOSS_RISADA)
                    
                    // Se as vidas acabarem, Game Over
                    if (gameState.heroLives <= 0) {
                        gameState.heroLives = 0
                        gameState.phase = GamePhase.GAME_OVER
                    } else {
                        // Respawn no início do mapa para dar chance
                        gameState.heroPosition = Position(maze.startIndex % maze.width, maze.startIndex / maze.width)
                        gameState.heroIsSlowedDown = false
                        gameState.heroSlowdownRemainingMs = 0
                    }
                } else {
                    // Primeiro golpe do Boss: Lentidão Severa
                    gameState.heroIsSlowedDown = true
                    gameState.heroSlowdownRemainingMs = SLOWDOWN_BOSS_MS
                    gameState.heroLastSlowdownTimeMs = currentTime
                    onSoundEffectRequested?.invoke(TipoEfeito.LENTIDAO_INICIO)
                }
            } else {
                // Monstro Normal: Aplica slowdown padrão
                gameState.heroIsSlowedDown = true
                gameState.heroSlowdownRemainingMs = (gameState.heroSlowdownRemainingMs + SLOWDOWN_MONSTER_MS).coerceAtMost(SLOWDOWN_MAX_ACUMULADO_MS)
                onSoundEffectRequested?.invoke(TipoEfeito.LENTIDAO_INICIO)
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

    /**
     * Verifica se o Hero está dentro do raio de ativação de alguma Trap inativa.
     * Ativa a Trap e aplica Slowdown ao Hero por 2 segundos.
     */
    private fun verificarAtivacaoTraps(maze: MazeData) {
        if (gameState.heroIsSlowedDown) return

        val heroPos = gameState.heroPosition

        gameState.traps = gameState.traps.map { trap ->
            if (trap.isActivated) return@map trap

            val distancia = trap.position.dist(heroPos)
            if (distancia > 0.7f) return@map trap // Raio de ativação fluído

            // Ativa a Trap
            gameState.heroIsSlowedDown = true
            gameState.heroSlowdownRemainingMs = SLOWDOWN_TRAP_MS
            gameState.currentMapClean = false
            
            // Incrementa contador de lentidões no mapa (estatística apenas agora)
            gameState.mapSlowdownCount++
            
            gameState.emitEvent(GameEvent.HeroReceivedSlowdown)
            gameState.resetComboStreak()

            trap.copy(isActivated = true)
        }
    }

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

        // Rastreamento de travamento
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

        // Movimento fluído em direção ao Herói
        val vx = (dx / distancia) * velocidade * deltaTimeSec
        val vy = (dy / distancia) * velocidade * deltaTimeSec

        val nextX = spikePos.x + vx
        val nextY = spikePos.y + vy

        // Spike desliza em paredes (simples)
        val indiceX = spikePos.y.toInt() * maze.width + nextX.toInt()
        val indiceY = nextY.toInt() * maze.width + spikePos.x.toInt()
        
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
        if (gameState.bossFightState.isActive && gameState.bossFightState.elapsedMs < gameState.bossFightState.totalDurationMs) {
            return // Porta travada!
        }

        // O herói deve estar próximo ao tile da saída (placa + escada).
        // Usamos distância Euclidiana (raio de 0.8 tiles) para que qualquer toque
        // na placa ou na escada (frente, trás, lados) ative a animação.
        val dx = (heroX - exitX).toFloat()
        val dy = (heroY - exitY).toFloat()
        val distSq = dx * dx + dy * dy
        if (distSq > 0.64f) return // Raio de 0.8 tiles (0.8 * 0.8 = 0.64)

        // Inicia animação de saída em vez de transição imediata
        gameState.isExiting = true
        gameState.exitAnimationTimerMs = 0L
        gameState.emitEvent(GameEvent.HeroReachedExit)
    }

    /**
     * Processa a transição real de nível após a animação da escada.
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

        // Emite evento de conclusão de mapa
        gameState.emitEvent(GameEvent.MapCompleted)

        // Muda a fase para evitar processamento repetido da saída no mesmo frame
        gameState.phase = GamePhase.LOADING

        // Verifica se é o último Map do Floor (3 Maps por Floor)
        val totalMapsNoFloor = 3
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
}
