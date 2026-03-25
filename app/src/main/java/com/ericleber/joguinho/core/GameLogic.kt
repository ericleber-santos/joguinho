package com.ericleber.joguinho.core

import com.ericleber.joguinho.pcg.BSPMazeGenerator
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
        private const val SLOWDOWN_MONSTER_MS = 3000L

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
    }

    // Acumuladores de movimento sub-tile para Spike e Monsters
    private var spikeAccumX = 0f
    private var spikeAccumY = 0f
    private val monsterAccumX = mutableMapOf<String, Float>()
    private val monsterAccumY = mutableMapOf<String, Float>()

    // Timers de padrão de movimento dos Monsters (para patrulha circular/aleatória)
    private val monsterTimers = mutableMapOf<String, Float>()

    // Callback chamado quando o Hero chega ao Exit (para lançar ScoreActivity)
    var onHeroReachedExit: (() -> Unit)? = null

    // Callback chamado ao final de cada Map para salvar estado (Requisito 7.1)
    var onMapCompleted: (() -> Unit)? = null

    /**
     * Atualiza toda a lógica de jogo para o frame atual.
     * Deve ser chamado pelo GameLoop após o InputController processar o input.
     *
     * @param deltaTimeSec tempo do frame em segundos
     */
    fun update(deltaTimeSec: Float) {
        if (gameState.phase != GamePhase.PLAYING) return
        val maze = gameState.mazeData ?: return

        atualizarMovimentoMonsters(deltaTimeSec, maze)
        verificarColisaoHeroMonster()
        verificarAtivacaoTraps()
        atualizarMovimentoSpike(deltaTimeSec, maze)
        verificarHeroNoExit(maze)
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
        val velocidade = if (gameState.heroIsSlowedDown) MONSTER_SPEED_TILES_PER_SEC * 0.7f
                         else MONSTER_SPEED_TILES_PER_SEC

        gameState.monsters = gameState.monsters.map { monster ->
            if (!monster.isActive) return@map monster

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
            // Patrulha horizontal: vai e volta
            val fase = (timer * 0.5f) % 2f
            if (fase < 1f) Pair(1f, 0f) else Pair(-1f, 0f)
        }

        MovementPattern.CIRCULAR -> {
            // Movimento circular ao redor da posição inicial
            val angulo = timer * 1.2f
            Pair(
                kotlin.math.cos(angulo.toDouble()).toFloat(),
                kotlin.math.sin(angulo.toDouble()).toFloat()
            )
        }

        MovementPattern.RANDOM -> {
            // Muda direção a cada ~2 segundos
            val intervalo = (timer / 2f).toInt()
            val seed = monster.id.hashCode() xor intervalo
            val direcoes = listOf(
                Pair(1f, 0f), Pair(-1f, 0f), Pair(0f, 1f), Pair(0f, -1f),
                Pair(0f, 0f) // pausa ocasional
            )
            direcoes[((seed and 0x7FFFFFFF) % direcoes.size)]
        }

        MovementPattern.CHASE -> {
            // Persegue o Hero por linha de visão simples
            val dx = (heroPos.x - monster.position.x).toFloat()
            val dy = (heroPos.y - monster.position.y).toFloat()
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < 8f && dist > 0f) Pair(dx / dist, dy / dist)
            else Pair(0f, 0f) // fora do alcance de perseguição
        }
    }

    // -------------------------------------------------------------------------
    // Colisão Hero↔Monster (Requisito 5.1)
    // -------------------------------------------------------------------------

    /**
     * Verifica se o Hero está na mesma posição de algum Monster ativo.
     * Aplica Slowdown ao Hero (3s) e ao Spike (2s), e recua o Monster 2 tiles.
     */
    private fun verificarColisaoHeroMonster() {
        if (gameState.heroIsSlowedDown) return // já em Slowdown, ignora nova colisão

        val heroPos = gameState.heroPosition
        val maze = gameState.mazeData ?: return

        gameState.monsters = gameState.monsters.map { monster ->
            if (!monster.isActive) return@map monster
            if (monster.position != heroPos) return@map monster

            // Aplica Slowdown ao Hero
            gameState.heroIsSlowedDown = true
            gameState.heroSlowdownRemainingMs = SLOWDOWN_MONSTER_MS
            gameState.currentMapClean = false

            // Aplica Slowdown ao Spike
            gameState.spikeIsSlowedDown = true
            gameState.spikeSlowdownRemainingMs = SLOWDOWN_SPIKE_MS

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
    private fun verificarAtivacaoTraps() {
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

        if (distancia <= SPIKE_MAX_DISTANCE) {
            // Já está perto o suficiente
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

        // Tenta mover na direção combinada primeiro, depois nos eixos separados
        val candidatos = listOf(
            Position(spikePos.x + tileDx, spikePos.y + tileDy),
            Position(spikePos.x + tileDx, spikePos.y),
            Position(spikePos.x, spikePos.y + tileDy)
        )

        for (candidato in candidatos) {
            if (candidato.x < 0 || candidato.y < 0 ||
                candidato.x >= maze.width || candidato.y >= maze.height) continue
            val indice = candidato.y * maze.width + candidato.x
            if (maze.tiles[indice] == BSPMazeGenerator.TILE_FLOOR) {
                gameState.spikePosition = candidato
                // Atualiza estado comportamental do Spike no GameState para o Renderer
                gameState.spikeCompanionState = when {
                    gameState.spikeIsSlowedDown -> "SLOWDOWN_PROPRIO"
                    distancia > 5f -> "CHAMANDO"
                    else -> "SEGUINDO"
                }
                return
            }
        }
    }

    // -------------------------------------------------------------------------
    // Detecção do Exit (Requisito 6.1)
    // -------------------------------------------------------------------------

    /**
     * Verifica se o Hero chegou ao tile de saída do labirinto.
     * Usa raio de 1 tile para facilitar a detecção — o Hero não precisa
     * estar exatamente no centro do tile de saída.
     */
    private fun verificarHeroNoExit(maze: MazeData) {
        val heroX = gameState.heroPosition.x
        val heroY = gameState.heroPosition.y
        val exitX = maze.exitIndex % maze.width
        val exitY = maze.exitIndex / maze.width

        // Raio de 1 tile: qualquer tile adjacente ao Exit conta como chegada
        val distancia = Math.abs(heroX - exitX) + Math.abs(heroY - exitY)
        if (distancia > 1) return

        // Incrementa ComboStreak se completou sem Slowdown (Requisito 5.7)
        if (gameState.currentMapClean) {
            gameState.incrementComboStreak()
            gameState.emitEvent(GameEvent.HeroSurpassedObstacle)
        }

        gameState.emitEvent(GameEvent.MapCompleted)
        gameState.emitEvent(GameEvent.HeroReachedExit)

        // Verifica se é o último Map do Floor (3 Maps por Floor)
        val totalMapsNoFloor = 3
        if (gameState.mapIndex >= totalMapsNoFloor - 1) {
            // Completou o Floor — vai para ScoreActivity
            gameState.completarAndar(gameState.floorTimerMs)
            gameState.emitEvent(GameEvent.FloorCompleted)
            gameState.phase = GamePhase.SCORE_SCREEN  // sinaliza ao ViewModel
            onMapCompleted?.invoke()
            onHeroReachedExit?.invoke()
        } else {
            // Avança para o próximo Map do mesmo Floor
            gameState.mapIndex++
            gameState.currentMapClean = true
            onMapCompleted?.invoke()
            onHeroReachedExit?.invoke()
        }
    }
}
