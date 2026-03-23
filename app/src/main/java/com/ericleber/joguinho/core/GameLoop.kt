package com.ericleber.joguinho.core

import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.view.SurfaceHolder
import com.ericleber.joguinho.character.SpikeAI
import com.ericleber.joguinho.character.SpikeAIContext

/**
 * Loop principal do jogo em thread dedicada com fixed timestep a 60fps.
 *
 * Responsabilidades:
 * - Processar atualizações de estado em passos fixos de ~16.6ms
 * - Acionar o Renderer a cada frame com interpolação
 * - Reduzir para 5fps em pausa (Requisito 18.1)
 * - Reduzir para 30fps em temperatura crítica via ThermalStatusCallback (Requisito 8.6, 18.5)
 * - Suspender em no máximo 100ms ao receber onPause (Requisito 18.1)
 * - Retomar em no máximo 200ms ao receber onResume (Requisito 18.2)
 *
 * Requisitos: 8.1, 8.6, 18.1, 18.2, 18.5, 21.6
 */
class GameLoop(
    private val surfaceHolder: SurfaceHolder,
    private val gameState: GameState,
    private val spikeAI: SpikeAI,
    private val powerManager: PowerManager?
) : Thread("GameLoop") {

    companion object {
        private const val TAG = "GameLoop"
        private const val TARGET_FPS_NORMAL = 60
        private const val TARGET_FPS_THERMAL = 30
        private const val TARGET_FPS_PAUSED = 5
        private const val NS_PER_SECOND = 1_000_000_000L
    }

    // --- Estado de controle ---
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var thermalThrottled = false

    // --- Callback de temperatura (API 29+) ---
    private var thermalListener: Any? = null  // PowerManager.OnThermalStatusChangedListener

    // --- Renderer callback (injetado externamente) ---
    var onRender: ((alpha: Float) -> Unit)? = null

    // --- Update callback (lógica de jogo) ---
    var onUpdate: ((deltaTimeSec: Float) -> Unit)? = null

    // --- Métricas de FPS (para HUD de debug) ---
    @Volatile var currentFps: Int = 0
        private set

    // --- Contador para monitoramento de heap ---
    private var heapCheckCounter = 0

    // -------------------------------------------------------------------------
    // Ciclo de vida
    // -------------------------------------------------------------------------

    override fun start() {
        running = true
        registerThermalCallback()
        super.start()
    }

    /**
     * Para o loop de forma limpa.
     * Aguarda no máximo 500ms para a thread encerrar.
     */
    fun stopLoop() {
        running = false
        unregisterThermalCallback()
        try {
            join(500)
        } catch (e: InterruptedException) {
            Logger.error(TAG, "Interrompido ao aguardar encerramento do GameLoop", e)
        }
    }

    /**
     * Pausa o loop — reduz para 5fps e para de processar atualizações de estado.
     * Deve completar em no máximo 100ms (Requisito 18.1).
     */
    fun pause() {
        paused = true
    }

    /**
     * Retoma o loop a partir do estado salvo.
     * Deve completar em no máximo 200ms (Requisito 18.2).
     */
    fun resume() {
        paused = false
    }

    // -------------------------------------------------------------------------
    // Loop principal
    // -------------------------------------------------------------------------

    override fun run() {
        var lastTimeNs = System.nanoTime()
        var accumulator = 0L
        var fpsFrameCount = 0
        var fpsTimer = 0L

        while (running) {
            val targetFps = when {
                paused -> TARGET_FPS_PAUSED
                thermalThrottled -> TARGET_FPS_THERMAL
                else -> TARGET_FPS_NORMAL
            }
            val targetFrameNs = NS_PER_SECOND / targetFps

            val nowNs = System.nanoTime()
            val elapsedNs = (nowNs - lastTimeNs).coerceAtMost(targetFrameNs * 4) // cap lag
            lastTimeNs = nowNs
            accumulator += elapsedNs
            fpsTimer += elapsedNs

            // Atualiza estado em passos fixos (fixed timestep)
            if (!paused) {
                while (accumulator >= targetFrameNs) {
                    val deltaTimeSec = targetFrameNs / NS_PER_SECOND.toFloat()
                    update(deltaTimeSec)
                    accumulator -= targetFrameNs
                }
            } else {
                // Em pausa, descarta o acúmulo para não processar burst ao retomar
                accumulator = 0L
            }

            // Renderiza com interpolação (alpha = fração do frame atual)
            val alpha = if (paused) 0f else accumulator.toFloat() / targetFrameNs
            render(alpha)

            // Atualiza contador de FPS a cada segundo
            fpsFrameCount++
            if (fpsTimer >= NS_PER_SECOND) {
                currentFps = fpsFrameCount
                fpsFrameCount = 0
                fpsTimer = 0L
            }

            // Dorme o tempo restante para não queimar CPU
            val frameElapsedNs = System.nanoTime() - nowNs
            val sleepNs = targetFrameNs - frameElapsedNs
            if (sleepNs > 1_000_000L) { // só dorme se > 1ms
                sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
            }
        }
    }

    // -------------------------------------------------------------------------
    // Update e Render
    // -------------------------------------------------------------------------

    private fun update(deltaTimeSec: Float) {
        if (gameState.phase != GamePhase.PLAYING) return

        val deltaMs = (deltaTimeSec * 1000f).toLong()

        // Atualiza timer do Floor
        gameState.floorTimerMs += deltaMs

        // Atualiza Slowdown do Hero
        if (gameState.heroIsSlowedDown) {
            gameState.heroSlowdownRemainingMs -= deltaMs
            if (gameState.heroSlowdownRemainingMs <= 0L) {
                gameState.heroIsSlowedDown = false
                gameState.heroSlowdownRemainingMs = 0L
            }
        }

        // Atualiza Slowdown do Spike
        if (gameState.spikeIsSlowedDown) {
            gameState.spikeSlowdownRemainingMs -= deltaMs
            if (gameState.spikeSlowdownRemainingMs <= 0L) {
                gameState.spikeIsSlowedDown = false
                gameState.spikeSlowdownRemainingMs = 0L
            }
        }

        // Atualiza SpikeAI
        val spikeContext = buildSpikeAIContext()
        spikeAI.update(deltaTimeSec, spikeContext)

        // Delega lógica adicional (input, colisões, etc.) ao callback externo
        onUpdate?.invoke(deltaTimeSec)

        // Monitora uso de heap nativo (Requisitos 8.4, 20.5, 20.6)
        checkHeapUsage()

        // Limpa eventos processados no frame
        gameState.clearEvents()
    }

    /**
     * Verifica o uso de heap nativo a cada ~300 frames (~5s a 60fps).
     * Loga um aviso quando o uso ultrapassar 80%.
     * Requisitos: 8.4, 20.5, 20.6
     */
    private fun checkHeapUsage() {
        heapCheckCounter++
        if (heapCheckCounter % 300 != 0) return

        val allocated = Debug.getNativeHeapAllocatedSize()
        val total = Debug.getNativeHeapSize()
        if (total > 0) {
            val usagePercent = (allocated * 100L / total).toInt()
            if (usagePercent > 80) {
                Logger.error(TAG, "Heap nativo acima de 80%: $usagePercent% ($allocated / $total bytes)", null)
            }
        }
    }

    private fun render(alpha: Float) {
        val holder = surfaceHolder
        val canvas = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.lockHardwareCanvas()
            } else {
                holder.lockCanvas()
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Falha ao obter canvas para renderização", e)
            return
        } ?: return

        try {
            onRender?.invoke(alpha)
        } finally {
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                Logger.error(TAG, "Falha ao postar canvas", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // SpikeAI context builder
    // -------------------------------------------------------------------------

    private fun buildSpikeAIContext(): SpikeAIContext {
        // Calcula distância do Hero ao Exit (simplificado — será refinado com MazeData)
        val heroPos = gameState.heroPosition
        val spikePos = gameState.spikePosition

        // Distância euclidiana em tiles (placeholder até integração com MazeData)
        val heroDistanceToExit = 999f  // será calculado pelo subsistema de colisão

        // Distância ao Monster mais próximo
        val nearestMonsterDist = gameState.monsters
            .filter { it.isActive }
            .minOfOrNull { m ->
                val dx = (m.position.x - heroPos.x).toFloat()
                val dy = (m.position.y - heroPos.y).toFloat()
                Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            } ?: 999f

        val heroReceivedSlowdown = gameState.pendingEvents
            .any { it is GameEvent.HeroReceivedSlowdown }
        val heroReachedExit = gameState.pendingEvents
            .any { it is GameEvent.HeroReachedExit }
        val heroSurpassedObstacle = gameState.pendingEvents
            .any { it is GameEvent.HeroSurpassedObstacle }

        return SpikeAIContext(
            heroPosition = heroPos,
            heroMoved = false,  // será atualizado pelo InputController
            heroStoppedDurationSec = 0f,  // será rastreado pelo InputController
            heroReceivedSlowdown = heroReceivedSlowdown,
            heroDistanceToExitTiles = heroDistanceToExit,
            nearestMonsterDistanceTiles = nearestMonsterDist,
            spikeIsSlowed = gameState.spikeIsSlowedDown,
            heroSurpassedObstacle = heroSurpassedObstacle,
            heroReachedExit = heroReachedExit
        )
    }

    // -------------------------------------------------------------------------
    // Thermal throttling (API 29+)
    // -------------------------------------------------------------------------

    /**
     * Registra listener de temperatura para reduzir FPS em temperatura crítica.
     * Requisitos: 8.6, 18.5
     */
    private fun registerThermalCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = powerManager ?: return
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                // THERMAL_STATUS_CRITICAL = 5, THERMAL_STATUS_SEVERE = 4
                thermalThrottled = status >= PowerManager.THERMAL_STATUS_SEVERE
                if (thermalThrottled) {
                    Logger.error(TAG, "Temperatura crítica detectada (status=$status) — reduzindo para ${TARGET_FPS_THERMAL}fps")
                    gameState.emitEvent(GameEvent.HeroReceivedSlowdown) // sinaliza HUD
                }
            }
            pm.addThermalStatusListener(listener)
            thermalListener = listener
        }
    }

    private fun unregisterThermalCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = powerManager ?: return
            val listener = thermalListener as? PowerManager.OnThermalStatusChangedListener ?: return
            pm.removeThermalStatusListener(listener)
            thermalListener = null
        }
    }
}
