package com.ericleber.joguinho.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
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
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Orquestrador de input adaptado para Platformer Arcade Side-Scrolling 2D (T-023).
 *
 * Responsabilidades:
 * - Capturar MotionEvents e roteá-los de forma ergonômica com suporte real a multi-touch
 * - Metade Esquerda: Joystick/D-pad invisível suave flutuante que controla a velocidade em X
 * - Metade Direita: Dois botões virtuais fixos dedicados (Botão A para Pulo, Botão B para Tiro)
 * - Garantir latência de toque máxima de 16ms
 * - Estética visual Premium de Neon Glassmorphism translúcido desenhado diretamente no Canvas
 */
class InputController(
    context: Context,
    private val gameState: GameState
) {

    companion object {
        private const val BASE_SPEED_TILES_PER_SEC = 3.5f
    }

    private val contextRef = WeakReference(context.applicationContext)

    // Joystick flutuante da metade esquerda (D-pad suave)
    val moveJoystick = FloatingJoystick()

    // Limite da metade esquerda da tela para o joystick (em pixels)
    private var screenHalfWidth = 0f
    private var screenWidth = 0f
    private var screenHeight = 0f

    // Botões físicos virtuais no canto inferior direito
    private var buttonA_X = 0f
    private var buttonA_Y = 0f
    private var buttonA_Radius = 0f
    private var isButtonAPressed = false
    private var buttonAPointerId = -1

    private var buttonB_X = 0f
    private var buttonB_Y = 0f
    private var buttonB_Radius = 0f
    private var isButtonBPressed = false
    private var buttonBPointerId = -1

    // Rastreamento de movimento para SpikeAI
    var heroMoved: Boolean = false
        private set
    var heroStoppedDurationSec: Float = 0f
        private set

    // Paints para renderização premium dos botões
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    // -------------------------------------------------------------------------
    // Configuração de Layout e Ergonomia
    // -------------------------------------------------------------------------

    /**
     * Chamado quando o tamanho da view é conhecido.
     * Configura o layout dos botões A/B e o limite esquerdo/direito.
     */
    fun onSizeChanged(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        screenHalfWidth = width / 2f
        
        val ctx = contextRef.get()
        val density = ctx?.resources?.displayMetrics?.density ?: 2f

        buttonA_Radius = 44f * density
        buttonB_Radius = 38f * density

        // Botão A (PULO) fica bem no canto inferior direito (confortável para o polegar)
        buttonA_X = width - 80f * density
        buttonA_Y = height - 80f * density

        // Botão B (TIRO) fica ligeiramente acima e à esquerda de A
        buttonB_X = width - 175f * density
        buttonB_Y = height - 125f * density

        buttonBorderPaint.strokeWidth = 3f * density
    }

    // -------------------------------------------------------------------------
    // Processamento de MotionEvent com Suporte Robusto a Multi-Touch
    // -------------------------------------------------------------------------

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameState.phase == com.ericleber.joguinho.core.GamePhase.UPGRADE_SELECTION) {
            return handleUpgradeSelectionTouch(event)
        }
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
                // ACTION_MOVE pode conter múltiplos ponteiros de toques simultâneos
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
        if (x <= screenHalfWidth) {
            // Metade esquerda: ativa o D-pad/Joystick flutuante suave
            moveJoystick.onTouchDown(x, y, id)
        } else {
            // Metade direita: verifica colisão circular com as hitboxes físicas dos botões
            val distA = hypot(x - buttonA_X, y - buttonA_Y)
            val distB = hypot(x - buttonB_X, y - buttonB_Y)

            if (distA <= buttonA_Radius) {
                isButtonAPressed = true
                buttonAPointerId = id
            } else if (distB <= buttonB_Radius) {
                isButtonBPressed = true
                buttonBPointerId = id
            }
        }
    }

    private fun handleTouchMove(x: Float, y: Float, id: Int) {
        if (x <= screenHalfWidth) {
            // Movimento na esquerda: repassa para o joystick correspondente
            moveJoystick.onTouchMove(x, y, id)
        } else {
            // Se o dedo que moveu era o ativado em um dos botões, re-valida colisão
            if (id == buttonAPointerId) {
                val dist = hypot(x - buttonA_X, y - buttonA_Y)
                // Se saiu do raio de toque por muito, cancela
                isButtonAPressed = dist <= buttonA_Radius * 1.5f
            } else if (id == buttonBPointerId) {
                val dist = hypot(x - buttonB_X, y - buttonB_Y)
                isButtonBPressed = dist <= buttonB_Radius * 1.5f
            }
        }
    }

    private fun handleTouchUp(x: Float, y: Float, id: Int) {
        // Libera joystick se o dedo levantado for o dono dele
        moveJoystick.onTouchUp(id)

        // Libera os botões físicos correspondentes
        if (id == buttonAPointerId) {
            isButtonAPressed = false
            buttonAPointerId = -1
        }
        if (id == buttonBPointerId) {
            isButtonBPressed = false
            buttonBPointerId = -1
        }
    }

    // -------------------------------------------------------------------------
    // Atualização de movimento de frame (Chamada pelo GameLoop a 60 FPS)
    // -------------------------------------------------------------------------

    fun update(deltaTimeSec: Float, mazeData: MazeData?, hapticEnabled: Boolean = true) {
        val movementVector = moveJoystick.getMovementVector()

        // 1. Extrair Entrada Horizontal e de Salto para a Física do Platformer (T-021)
        var inputVx = 0f
        var inputVy = 0f
        if (movementVector != null) {
            inputVx = movementVector.x
            inputVy = movementVector.y
        }

        // Pulo ativado unicamente pelo Botão A verde da direita (Ponto 2 e 3)
        val jumpPressed = isButtonAPressed

        gameState.inputDirecaoX = inputVx
        gameState.inputDirecaoY = inputVy
        gameState.inputPuloPressionado = jumpPressed
        gameState.isShooting = isButtonBPressed

        if (mazeData != null) {
            // Sincroniza a direção visual para as animações de sprites (WEST/EAST)
            if (inputVx < -0.1f) {
                gameState.heroDirection = Direction.WEST
            } else if (inputVx > 0.1f) {
                gameState.heroDirection = Direction.EAST
            }

            // Define o ângulo de tiro: auto-aim respeitando a direção do olhar
            if (isButtonBPressed) {
                val facingLeft = gameState.heroDirection == Direction.WEST
                val targets = gameState.monsters.filter { m ->
                    m.isActive && !m.isBoss &&
                        (if (facingLeft) m.position.x < gameState.heroPosition.x
                         else m.position.x > gameState.heroPosition.x)
                }
                val nearestMonster = targets.minByOrNull { it.position.dist(gameState.heroPosition) }
                if (nearestMonster != null) {
                    val dx = nearestMonster.position.x - gameState.heroPosition.x
                    val dy = nearestMonster.position.y - gameState.heroPosition.y
                    gameState.shootingAngle = kotlin.math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
                } else {
                    // Fallback: mantém direção visual do herói
                    gameState.shootingAngle = if (gameState.heroDirection == Direction.WEST) {
                        Math.PI.toFloat()
                    } else {
                        0f
                    }
                }
            }

            // Atualiza status de animação de caminhada para o Renderer e SpikeAI
            val heroMovendoHorizontalmente = Math.abs(gameState.heroVelocityX) > 0.1f
            heroMoved = heroMovendoHorizontalmente
            if (!heroMoved) {
                heroStoppedDurationSec += deltaTimeSec
            } else {
                heroStoppedDurationSec = 0f
            }
            gameState.heroStoppedDurationSec = heroStoppedDurationSec
            return
        }

        // Fallback de segurança para modo sem mapa
        val currentPos = gameState.heroPosition
        val dx = inputVx * BASE_SPEED_TILES_PER_SEC * deltaTimeSec
        gameState.heroPosition = Position(currentPos.x + dx, currentPos.y)
    }

    // -------------------------------------------------------------------------
    // Renderização dos Controles (Neon Glassmorphism Visual)
    // -------------------------------------------------------------------------

    fun draw(canvas: Canvas) {
        // 1. Joystick de Movimento (D-pad suave translúcido na esquerda)
        moveJoystick.draw(canvas, accentColor = Color.rgb(200, 200, 200), drawWaterIcon = false)

        // 2. Botão A (PULO - Esmeralda Translúcido)
        drawVirtualButton(
            canvas,
            buttonA_X,
            buttonA_Y,
            buttonA_Radius,
            "A",
            Color.rgb(46, 204, 113),
            isButtonAPressed
        )

        // 3. Botão B (TIRO - Rubi Translúcido)
        drawVirtualButton(
            canvas,
            buttonB_X,
            buttonB_Y,
            buttonB_Radius,
            "B",
            Color.rgb(231, 76, 60),
            isButtonBPressed
        )
    }

    private fun drawVirtualButton(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        label: String,
        baseColor: Int,
        isPressed: Boolean
    ) {
        if (radius <= 0f) return

        val alphaBase = if (isPressed) 100 else 35
        val alphaBorder = if (isPressed) 150 else 65

        // Preenchimento circular translúcido
        buttonPaint.color = Color.argb(
            alphaBase,
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )
        buttonPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, buttonPaint)

        // Borda circular neon
        buttonBorderPaint.color = Color.argb(
            alphaBorder,
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )
        canvas.drawCircle(cx, cy, radius, buttonBorderPaint)

        // Texto centralizado
        buttonTextPaint.textSize = radius * 0.9f
        buttonTextPaint.setShadowLayer(6f, 0f, 3f, Color.BLACK)
        
        val fm = buttonTextPaint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, cx, textY, buttonTextPaint)
        buttonTextPaint.clearShadowLayer()
    }

    private fun handleUpgradeSelectionTouch(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        
        val ctx = contextRef.get()
        val density = ctx?.resources?.displayMetrics?.density ?: 2f
        
        val w = screenWidth
        val h = screenHeight
        
        val cardWidth = 175f * density
        val cardHeight = 250f * density
        val gap = 24f * density
        val startX = w / 2f - (cardWidth * 1.5f + gap)
        val cardY = h * 0.25f
        
        val buttonWidth = 220f * density
        val buttonHeight = 38f * density
        val buttonX = w / 2f - buttonWidth / 2f
        val buttonY = h * 0.81f
        
        val options = gameState.upgradeCardsOptions
        
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                var found = -1
                for (i in 0 until minOf(3, options.size)) {
                    val cx = startX + i * (cardWidth + gap)
                    if (x >= cx && x <= cx + cardWidth && y >= cardY && y <= cardY + cardHeight) {
                        found = i
                        break
                    }
                }
                gameState.upgradeSelectionIndex = found
            }
            MotionEvent.ACTION_UP -> {
                var selectedSlot = -1
                for (i in 0 until minOf(3, options.size)) {
                    val cx = startX + i * (cardWidth + gap)
                    if (x >= cx && x <= cx + cardWidth && y >= cardY && y <= cardY + cardHeight) {
                        selectedSlot = i
                        break
                    }
                }
                
                if (selectedSlot != -1 && selectedSlot < options.size) {
                    val card = options[selectedSlot]
                    gameState.aplicarUpgrade(card)
                    gameState.upgradeSelectionIndex = -1
                    gameState.upgradeCardsOptions = emptyList()
                    gameState.phase = com.ericleber.joguinho.core.GamePhase.PLAYING
                } else if (x >= buttonX && x <= buttonX + buttonWidth && y >= buttonY && y <= buttonY + buttonHeight) {
                    if (gameState.coinsCollected >= 5) {
                        gameState.coinsCollected -= 5
                        gameState.upgradeCardsOptions = com.ericleber.joguinho.core.UpgradeCard.generateRandomOptions(
                            java.util.Random(),
                            gameState.heroDoubleJumpUnlocked
                        )
                        gameState.upgradeSelectionIndex = -1
                    }
                } else {
                    gameState.upgradeSelectionIndex = -1
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                gameState.upgradeSelectionIndex = -1
            }
        }
        return true
    }
}
