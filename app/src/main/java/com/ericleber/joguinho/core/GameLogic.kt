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
    }

    // Acumuladores de movimento sub-tile para Spike e Monsters
    private var spikeAccumX = 0f
    private var spikeAccumY = 0f
    private val monsterAccumX = mutableMapOf<String, Float>()
    private val monsterAccumY = mutableMapOf<String, Float>()

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
                var baseVel = MONSTER_SPEED_TILES_PER_SEC * (1.2f + bossFloorBonus) * phase3SpeedMult * mudSlowMult
                val velocidade = if (gameState.heroIsSlowedDown) baseVel * 0.7f else baseVel
                
                val timer = (monsterTimers[monster.id] ?: 0f) + deltaTimeSec
                monsterTimers[monster.id] = timer

                val (dx, dy) = calcularDirecaoMonster(monster, heroPos, timer)

                // Lógica de Boss: Provocações
                val currentTime = System.currentTimeMillis()
                val lastTauntTime = gameState.monsterCollisionCooldowns["taunt_${monster.id}"] ?: 0L
                if (currentTime - lastTauntTime > 12000L) { // 12 segundos de cooldown
                    atualizarProvocacaoBoss(monster)
                    gameState.monsterCollisionCooldowns["taunt_${monster.id}"] = currentTime
                }

                val accumX = (monsterAccumX[monster.id] ?: 0f) + dx * velocidade * deltaTimeSec
                val accumY = (monsterAccumY[monster.id] ?: 0f) + dy * velocidade * deltaTimeSec

                val tileDx = accumX.toInt()
                val tileDy = accumY.toInt()
                monsterAccumX[monster.id] = accumX - tileDx
                monsterAccumY[monster.id] = accumY - tileDy

                if (tileDx == 0 && tileDy == 0) return@map monster

                val novaPos = Position(
                    (monster.position.x + tileDx).coerceIn(0, maze.width - 1),
                    (monster.position.y + tileDy).coerceIn(0, maze.height - 1)
                )

                // Verifica colisão com Pilar de Pedra ou Caixa para destruir
                val hitElement = gameState.survivalElements.find { 
                    (it.type == com.ericleber.joguinho.core.SurvivalElementType.STONE_PILLAR || it.type == com.ericleber.joguinho.core.SurvivalElementType.PUSHABLE_BOX) 
                    && it.position == novaPos && it.active
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
                    return@map monster // Não avança o tile, gasta o movimento atacando
                }

                // Não atravessa paredes nem ocupa a posição do Hero
                val indice = novaPos.y * maze.width + novaPos.x
                if (maze.tiles[indice] == BSPMazeGenerator.TILE_WALL || novaPos == heroPos) {
                    return@map monster
                } else {
                    return@map monster.copy(position = novaPos)
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
            


            val accumX = (monsterAccumX[monster.id] ?: 0f) + dx * velocidade * deltaTimeSec
            val accumY = (monsterAccumY[monster.id] ?: 0f) + dy * velocidade * deltaTimeSec

            val tileDx = accumX.toInt()
            val tileDy = accumY.toInt()
            monsterAccumX[monster.id] = accumX - tileDx
            monsterAccumY[monster.id] = accumY - tileDy

            if (tileDx == 0 && tileDy == 0) return@map monster

            val novaPos = Position(
                (monster.position.x + tileDx).coerceIn(0, maze.width - 1),
                (monster.position.y + tileDy).coerceIn(0, maze.height - 1)
            )

            // Não atravessa paredes nem ocupa a posição do Hero
            val indice = novaPos.y * maze.width + novaPos.x
            if (maze.tiles[indice] == BSPMazeGenerator.TILE_WALL || novaPos == heroPos) {
                monster
            } else {
                monster.copy(position = novaPos)
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
            if (item.isActive && item.position == heroPos) {
                when (item.type) {
                    com.ericleber.joguinho.core.ItemType.SPEED_BOOTS -> {
                        gameState.heroHasSpeedBuff = true
                        gameState.heroSpeedBuffRemainingMs = 7000L
                        // Evento de áudio: Power-up coletado
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
            
            // Tocha de Gelo (Overlap)
            if (elem.type == com.ericleber.joguinho.core.SurvivalElementType.ICE_TORCH && elem.position == heroPos && elem.cooldownRemainingMs <= 0) {
                gameState.bossFightState = gameState.bossFightState.copy(
                    bossStunRemainingMs = gameState.bossFightState.bossStunRemainingMs + 4000L // 4s Stun
                )
                onSoundEffectRequested?.invoke(TipoEfeito.POWER_UP_COLETADO) // Reutilizando som
                return@map elem.copy(cooldownRemainingMs = 20000L) // 20s cooldown
            }
            
            // Sino de Distração (Overlap)
            if (elem.type == com.ericleber.joguinho.core.SurvivalElementType.DISTRACTION_BELL && elem.position == heroPos && !gameState.bossFightState.bellUsed) {
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
                
                // Se o herói estiver adjacente (distância 1) e estiver tentando ir na direção da caixa
                if (Math.abs(dx) + Math.abs(dy) == 1) {
                    val directionMatch = when (gameState.heroDirection) {
                        com.ericleber.joguinho.core.Direction.NORTH -> dy == -1 && dx == 0
                        com.ericleber.joguinho.core.Direction.SOUTH -> dy == 1 && dx == 0
                        com.ericleber.joguinho.core.Direction.EAST -> dx == 1 && dy == 0
                        com.ericleber.joguinho.core.Direction.WEST -> dx == -1 && dy == 0
                        else -> false
                    }
                    
                    if (directionMatch) {
                        val pushPos = Position(elem.position.x + dx, elem.position.y + dy)
                        // Verifica se o tile atrás da caixa está livre
                        val pushIdx = pushPos.y * maze.width + pushPos.x
                        val isWall = pushPos.x < 0 || pushPos.y < 0 || pushPos.x >= maze.width || pushPos.y >= maze.height || maze.tiles[pushIdx] == 1
                        val hasElement = gameState.survivalElements.any { it.active && it.position == pushPos && it.type != com.ericleber.joguinho.core.SurvivalElementType.MUD_SWAMP }
                        
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
            var targetX = heroPos.x.toFloat()
            var targetY = heroPos.y.toFloat()
            
            if (gameState.bossFightState.bossDistractedMs > 0) {
                val bell = gameState.survivalElements.find { it.type == com.ericleber.joguinho.core.SurvivalElementType.DISTRACTION_BELL && it.active }
                if (bell != null) {
                    targetX = bell.position.x.toFloat()
                    targetY = bell.position.y.toFloat()
                }
            }

            // Persegue continuamente (sem susto na Fase 5)
            val dx = (targetX - monster.position.x).toFloat()
            val dy = (targetY - monster.position.y).toFloat()
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist > 0) Pair(dx / dist, dy / dist) else Pair(0f, 0f)
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

        MovementPattern.CHASE, MovementPattern.TANK_SLOW -> {
            val dx = (heroPos.x - monster.position.x).toFloat()
            val dy = (heroPos.y - monster.position.y).toFloat()
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < 10f && dist > 0f) Pair(dx / dist, dy / dist) else Pair(0f, 0f)
        }

        MovementPattern.AMBUSH -> {
            val dx = (heroPos.x - monster.position.x).toFloat()
            val dy = (heroPos.y - monster.position.y).toFloat()
            val dist = sqrt(dx * dx + dy * dy)
            // Só se move se o jogador chegar perto (aggro radius de 4 tiles)
            if (dist < 4f && dist > 0f) Pair(dx / dist, dy / dist) else Pair(0f, 0f)
        }

        MovementPattern.ZONING_DEFENDER -> {
            // Defende em torno do ponto âncora
            val anchor = monster.anchorPosition ?: monster.position
            val dxHero = (heroPos.x - anchor.x).toFloat()
            val dyHero = (heroPos.y - anchor.y).toFloat()
            val distHeroToAnchor = sqrt(dxHero * dxHero + dyHero * dyHero)
            
            if (distHeroToAnchor < 5f) {
                // Hero invadiu a zona, persegue o hero
                val dx = (heroPos.x - monster.position.x).toFloat()
                val dy = (heroPos.y - monster.position.y).toFloat()
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > 0f) Pair(dx / dist, dy / dist) else Pair(0f, 0f)
            } else {
                // Retorna ao ponto âncora de forma circular
                val distToAnchor = sqrt(
                    Math.pow((monster.position.x - anchor.x).toDouble(), 2.0) +
                    Math.pow((monster.position.y - anchor.y).toDouble(), 2.0)
                ).toFloat()
                if (distToAnchor > 2f) {
                    val dx = (anchor.x - monster.position.x).toFloat()
                    val dy = (anchor.y - monster.position.y).toFloat()
                    Pair(dx / distToAnchor, dy / distToAnchor)
                } else {
                    val angulo = timer * 0.8f
                    Pair(kotlin.math.cos(angulo.toDouble()).toFloat(), kotlin.math.sin(angulo.toDouble()).toFloat())
                }
            }
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
            val isColliding = if (monster.isBoss) {
                dx <= 1 && dy <= 1 // Raio de 1 tile apenas para o Boss
            } else {
                monster.position == heroPos // Mesmo tile estrito para todos os outros monstros
            }

            if (!isColliding) return@map monster

            // Proteção: Impedir dano de monstros que estejam longe demais (invisíveis/fora da tela)
            val monsterDistToHero = sqrt(
                ((monster.position.x - heroPos.x).toFloat().let { it * it }) +
                ((monster.position.y - heroPos.y).toFloat().let { it * it })
            )
            if (monsterDistToHero > 6f) return@map monster // Ignora monstros fora de alcance visível

            // Verifica cooldown de 2 segundos para o mesmo monstro
            val lastCollision = gameState.monsterCollisionCooldowns[monster.id] ?: 0L
            if (currentTime - lastCollision < 2000L) return@map monster

            // Registra colisão para cooldown
            gameState.monsterCollisionCooldowns[monster.id] = currentTime

            // Aplica/Acumula Slowdown ao Hero (com cap máximo)
            gameState.heroIsSlowedDown = true
            gameState.heroSlowdownRemainingMs = (gameState.heroSlowdownRemainingMs + SLOWDOWN_MONSTER_MS)
                .coerceAtMost(SLOWDOWN_MAX_ACUMULADO_MS)
            gameState.currentMapClean = false
            
            // Evento de áudio: Lentidão iniciada
            onSoundEffectRequested?.invoke(TipoEfeito.LENTIDAO_INICIO)
            
            // Incrementa contador de lentidões no mapa
            gameState.mapSlowdownCount++
            
            // Se ficou lento 3x, reinicia no início do mapa
            if (gameState.mapSlowdownCount >= 3) {
                gameState.mapSlowdownCount = 0
                gameState.heroPosition = Position(
                    maze.startIndex % maze.width,
                    maze.startIndex / maze.width
                )
                gameState.heroIsSlowedDown = false
                gameState.heroSlowdownRemainingMs = 0
            }

            // Spike é imune a danos e lentidão (Requisito: Spike não sofre dano ou lentidão)
            // gameState.spikeIsSlowedDown = true
            // gameState.spikeSlowdownRemainingMs += SLOWDOWN_SPIKE_MS

            // Emite evento para SpikeAI e HUD
            gameState.emitEvent(GameEvent.HeroReceivedSlowdown)
            gameState.resetComboStreak()

            // Recua Monster 2 tiles na direção oposta ao Hero
            val recuoX = (monster.position.x - heroPos.x).coerceIn(-1, 1) * 2
            val recuoY = (monster.position.y - heroPos.y).coerceIn(-1, 1) * 2
            val novaPosX = (monster.position.x + recuoX).coerceIn(0, maze.width - 1)
            val novaPosY = (monster.position.y + recuoY).coerceIn(0, maze.height - 1)
            val novoIndice = novaPosY * maze.width + novaPosX
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

            val distancia = abs(trap.position.x - heroPos.x) + abs(trap.position.y - heroPos.y)
            if (distancia > TRAP_ACTIVATION_RADIUS) return@map trap

            // Ativa a Trap
            gameState.heroIsSlowedDown = true
            gameState.heroSlowdownRemainingMs = SLOWDOWN_TRAP_MS
            gameState.currentMapClean = false
            
            // Incrementa contador de lentidões no mapa
            gameState.mapSlowdownCount++
            
            // Se ficou lento 3x, reinicia no início do mapa
            if (gameState.mapSlowdownCount >= 3) {
                gameState.mapSlowdownCount = 0
                gameState.heroPosition = Position(
                    maze.startIndex % maze.width,
                    maze.startIndex / maze.width
                )
                gameState.heroIsSlowedDown = false
                gameState.heroSlowdownRemainingMs = 0
            }
            
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

        val dx = (heroPos.x - spikePos.x).toFloat()
        val dy = (heroPos.y - spikePos.y).toFloat()
        val distancia = sqrt(dx * dx + dy * dy)

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
            spikeAccumX = 0f
            spikeAccumY = 0f
            return
        }

        // Velocidade reduzida em Slowdown
        val velocidade = if (gameState.spikeIsSlowedDown) SPIKE_SPEED_TILES_PER_SEC * 0.4f
                         else SPIKE_SPEED_TILES_PER_SEC

        // Normaliza direção
        val normX = dx / distancia
        val normY = dy / distancia

        spikeAccumX += normX * velocidade * deltaTimeSec
        spikeAccumY += normY * velocidade * deltaTimeSec

        val tileDx = spikeAccumX.toInt()
        val tileDy = spikeAccumY.toInt()

        if (tileDx == 0 && tileDy == 0) return

        spikeAccumX -= tileDx
        spikeAccumY -= tileDy

        // Tenta mover na direção combinada primeiro, depois nos eixos separados,
        // e agora também tenta diagonais alternativas para contornar paredes
        val candidatos = listOf(
            Position(spikePos.x + tileDx, spikePos.y + tileDy),
            Position(spikePos.x + tileDx, spikePos.y),
            Position(spikePos.x, spikePos.y + tileDy),
            // Diagonais alternativas para contornar obstáculos
            Position(spikePos.x + tileDx, spikePos.y + 1),
            Position(spikePos.x + tileDx, spikePos.y - 1),
            Position(spikePos.x + 1, spikePos.y + tileDy),
            Position(spikePos.x - 1, spikePos.y + tileDy)
        )

        for (candidato in candidatos) {
            if (candidato.x < 0 || candidato.y < 0 ||
                candidato.x >= maze.width || candidato.y >= maze.height) continue
            val indice = candidato.y * maze.width + candidato.x
            if (maze.tiles[indice] == BSPMazeGenerator.TILE_FLOOR) {
                gameState.spikePosition = candidato
                gameState.spikeCompanionState = when {
                    gameState.spikeIsSlowedDown -> "SLOWDOWN_PROPRIO"
                    distancia > 5f -> "CHAMANDO"
                    else -> "SEGUINDO"
                }
                return
            }
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
            val nx = alvo.x + ox
            val ny = alvo.y + oy
            if (nx < 0 || ny < 0 || nx >= maze.width || ny >= maze.height) continue
            val idx = ny * maze.width + nx
            if (maze.tiles[idx] == BSPMazeGenerator.TILE_FLOOR) {
                return Position(nx, ny)
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
