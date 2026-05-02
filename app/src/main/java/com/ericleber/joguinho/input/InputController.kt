package com.ericleber.joguinho.input

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import com.ericleber.joguinho.core.Direction
import com.ericleber.joguinho.core.GameState
import com.ericleber.joguinho.core.MazeData
import com.ericleber.joguinho.core.Position
import java.lang.ref.WeakReference

/**
 * Orquestrador de input: gerencia FloatingJoystick e DPadController,
 * aplica movimento ao Hero e dispara feedback háptico em colisão com parede.
 *
 * Responsabilidades:
 * - Capturar MotionEvents e roteá-los ao controle ativo (joystick ou D-pad)
 * - Calcular nova posição do Hero com base na direção e velocidade
 * - Detectar colisão com paredes e aplicar vibração de 20ms (Requisito 4.6)
 * - Garantir latência máxima de 16ms do input ao movimento (Requisito 4.2)
 * - Rastrear tempo parado do Hero para o SpikeAI
 * - Suportar troca entre joystick flutuante e D-pad (Requisito 12.3)
 *
 * Requisitos: 4.2, 4.3, 4.4, 4.6, 4.7, 12.5
 */
class InputController(
    context: Context,
    private val gameState: GameState
) {

    companion object {
        /** Velocidade base do Hero em tiles/segundo — 3.5 tiles/s para movimento contemplativo. */
        private const val BASE_SPEED_TILES_PER_SEC = 3.5f

        /** Multiplicador de corrida (Requisito 4.4 — 80% mais rápido). */
        private const val RUN_MULTIPLIER = 1.8f

        /** Multiplicador de Slowdown (Requisito 4.8 — 40% da velocidade normal). */
        private const val SLOWDOWN_MULTIPLIER = 0.4f

        /** Duração da vibração em colisão com parede em ms (Requisito 4.6). */
        private const val WALL_HAPTIC_MS = 20L

        /** Limite da metade esquerda da tela para o joystick (em pixels). */
        private var screenHalfWidth = 0f
    }

    // WeakReference para evitar vazamento de memória (Requisito 20.2)
    private val contextRef = WeakReference(context.applicationContext)

    // Controles
    val joystick = FloatingJoystick()
    val dpad = DPadController()

    // Modo de controle ativo
    var useDPad: Boolean = false

    // Botão de corrida
    private var runButtonPressed: Boolean = false
    private var runButtonPointerId: Int = -1

    // Botão de tiro
    private var shootButtonPointerId: Int = -1

    // Rastreamento de movimento para SpikeAI
    var heroMoved: Boolean = false
        private set
    var heroStoppedDurationSec: Float = 0f
        private set

    // Vibrador (lazy para não crashar em dispositivos sem vibração)
    private val vibrator: Vibrator? by lazy {
        val ctx = contextRef.get() ?: return@lazy null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // -------------------------------------------------------------------------
    // Configuração
    // -------------------------------------------------------------------------

    /**
     * Deve ser chamado quando o tamanho da view é conhecido.
     * Configura o layout do D-pad e o limite do joystick.
     */
    fun onSizeChanged(width: Float, height: Float) {
        screenHalfWidth = width / 2f
        dpad.layout(width, height)
    }

    // -------------------------------------------------------------------------
    // Processamento de MotionEvent
    //
    // Chamado pela GameSurfaceView.onTouchEvent() na UI thread.
    // O resultado é lido pelo GameLoop na game thread — a leitura é segura
    // porque os campos de direção são escritos atomicamente (primitivos).
    // Latência máxima: 1 frame = 16ms (Requisito 4.2).
    // -------------------------------------------------------------------------

    fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                handleTouchDown(x, y, pointerId)
            }
            MotionEvent.ACTION_MOVE -> {
                // ACTION_MOVE pode conter múltiplos ponteiros
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    handleTouchMove(event.getX(i), event.getY(i), id)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                handleTouchUp(x, y, pointerId)
            }
        }
        return true
    }

    private fun handleTouchDown(x: Float, y: Float, id: Int) {
        if (useDPad) {
            dpad.onTouchDown(x, y, id)
        } else {
            if (x <= screenHalfWidth) {
                // Metade esquerda: joystick flutuante (Requisito 4.7)
                joystick.onTouchDown(x, y, id)
            } else {
                // Metade direita dividida:
                val screenHeight = (contextRef.get()?.resources?.displayMetrics?.heightPixels ?: 2000).toFloat()
                if (y > screenHeight * 0.6f) {
                    // Canto inferior direito: Botão de Tiro (MECH-03)
                    gameState.isShooting = true
                    shootButtonPointerId = id
                } else {
                    // Canto superior direito: Botão de Corrida (Requisito 4.4)
                    runButtonPressed = true
                    runButtonPointerId = id
                }
            }
        }
    }

    private fun handleTouchMove(x: Float, y: Float, id: Int) {
        if (useDPad) {
            dpad.onTouchMove(x, y, id)
        } else {
            joystick.onTouchMove(x, y, id)
        }
    }

    private fun handleTouchUp(x: Float, y: Float, id: Int) {
        if (useDPad) {
            dpad.onTouchUp(id)
        } else {
            joystick.onTouchUp(id)
            if (id == runButtonPointerId) {
                runButtonPressed = false
                runButtonPointerId = -1
            }
            if (id == shootButtonPointerId) {
                gameState.isShooting = false
                shootButtonPointerId = -1
            }
        }
    }

    // -------------------------------------------------------------------------
    // Atualização de movimento (chamada pelo GameLoop a cada frame)
    // -------------------------------------------------------------------------

    fun update(deltaTimeSec: Float, mazeData: MazeData?, hapticEnabled: Boolean = true) {
        val direction = getActiveDirection()
        val movementVector = if (useDPad) {
            direction?.let { dir ->
                val (vx, vy) = directionToVector(dir)
                // Normaliza diagonais para D-Pad
                if (vx != 0f && vy != 0f) {
                    val invSqrt2 = 0.7071f
                    android.graphics.PointF(vx * invSqrt2, vy * invSqrt2)
                } else {
                    android.graphics.PointF(vx, vy)
                }
            }
        } else {
            joystick.getMovementVector()
        }

        if (movementVector == null || (movementVector.x == 0f && movementVector.y == 0f)) {
            // Hero parado
            heroMoved = false
            heroStoppedDurationSec += deltaTimeSec
            gameState.heroStoppedDurationSec = heroStoppedDurationSec
            return
        }

        heroMoved = true
        heroStoppedDurationSec = 0f
        gameState.heroStoppedDurationSec = 0f

        // Calcula velocidade efetiva
        val baseSpeed = BASE_SPEED_TILES_PER_SEC
        var speedMultiplier = when {
            gameState.heroIsSlowedDown -> SLOWDOWN_MULTIPLIER
            runButtonPressed -> RUN_MULTIPLIER
            else -> 1f
        }
        
        // Aplica buff de velocidade (+50%) se ativo
        if (gameState.heroHasSpeedBuff) {
            speedMultiplier *= 1.5f
        }
        
        val effectiveSpeed = baseSpeed * speedMultiplier
        val currentPos = gameState.heroPosition

        // Vetor de movimento por frame
        val dx = movementVector.x * effectiveSpeed * deltaTimeSec
        val dy = movementVector.y * effectiveSpeed * deltaTimeSec

        if (mazeData == null) {
            gameState.heroPosition = Position(currentPos.x + dx, currentPos.y + dy)
            if (direction != null) gameState.heroDirection = direction
            return
        }

        // Tenta mover nos dois eixos (Diagonal)
        val nextX = currentPos.x + dx
        val nextY = currentPos.y + dy

        if (!checkCollision(nextX, nextY, mazeData)) {
            gameState.heroPosition = Position(nextX, nextY)
        } else {
            // Se houver colisão diagonal, tenta mover em cada eixo separadamente (Sliding)
            val canMoveX = !checkCollision(nextX, currentPos.y, mazeData)
            val canMoveY = !checkCollision(currentPos.x, nextY, mazeData)

            when {
                canMoveX -> gameState.heroPosition = Position(nextX, currentPos.y)
                canMoveY -> gameState.heroPosition = Position(currentPos.x, nextY)
                else -> {
                    // Totalmente bloqueado
                    if (hapticEnabled) vibrate(WALL_HAPTIC_MS)
                }
            }
        }

        if (direction != null) gameState.heroDirection = direction
    }

    /**
     * Verifica colisão de um círculo (hitbox do herói) contra os tiles do labirinto.
     * @param radius Raio da hitbox (em frações de tile). 0.35f permite passar em corredores apertados.
     */
    private fun checkCollision(x: Float, y: Float, maze: MazeData, radius: Float = 0.32f): Boolean {
        val left = (x - radius).toInt()
        val right = (x + radius).toInt()
        val top = (y - radius).toInt()
        val bottom = (y + radius).toInt()

        for (ty in top..bottom) {
            for (tx in left..right) {
                if (isWallAt(tx, ty, maze)) return true
            }
        }
        return false
    }

    private fun isWallAt(tx: Int, ty: Int, maze: MazeData): Boolean {
        if (tx < 0 || ty < 0 || tx >= maze.width || ty >= maze.height) return true
        if (maze.tiles[ty * maze.width + tx] == 1) return true
        
        // Colisão com elementos sólidos (Pilares e Caixas)
        return gameState.survivalElements.any {
            it.active && it.position.ix == tx && it.position.iy == ty &&
            (it.type == com.ericleber.joguinho.core.SurvivalElementType.STONE_PILLAR ||
             it.type == com.ericleber.joguinho.core.SurvivalElementType.PUSHABLE_BOX)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun getActiveDirection(): Direction? =
        if (useDPad) dpad.getActiveDirection() else joystick.getMappedDirection()

    /**
     * Converte uma Direction para vetor (dx, dy) no espaço do grid.
     * Y positivo = sul (para baixo no grid).
     */
    private fun directionToVector(dir: Direction): Pair<Float, Float> = when (dir) {
        Direction.NORTH       ->  Pair( 0f, -1f)
        Direction.NORTH_EAST  ->  Pair( 1f, -1f)
        Direction.EAST        ->  Pair( 1f,  0f)
        Direction.SOUTH_EAST  ->  Pair( 1f,  1f)
        Direction.SOUTH       ->  Pair( 0f,  1f)
        Direction.SOUTH_WEST  ->  Pair(-1f,  1f)
        Direction.WEST        ->  Pair(-1f,  0f)
        Direction.NORTH_WEST  ->  Pair(-1f, -1f)
    }


    /**
     * Dispara vibração com duração especificada.
     * Respeita a API disponível no dispositivo.
     * Requisito 12.5
     */
    private fun vibrate(durationMs: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }

    // -------------------------------------------------------------------------
    // Renderização (delegada aos controles)
    // -------------------------------------------------------------------------

    /**
     * Desenha o controle ativo no canvas.
     * Deve ser chamado pelo Renderer a cada frame.
     */
    fun draw(canvas: Canvas) {
        if (useDPad) {
            dpad.draw(canvas)
        } else {
            joystick.draw(canvas)
        }
    }
}
