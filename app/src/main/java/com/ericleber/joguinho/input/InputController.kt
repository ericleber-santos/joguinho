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
 * Orquestrador de input adaptado para Arena Survivor Twin-Stick.
 *
 * Responsabilidades:
 * - Capturar MotionEvents e roteá-los de forma ergonômica com suporte real a multi-touch
 * - Metade Esquerda: Joystick flutuante para movimento
 * - Metade Direita: Joystick flutuante para mira/tiro (twin-stick 360°) + Botão A para Pulo
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

    // Joystick de mira na metade direita (twin-stick)
    val aimJoystick = FloatingJoystick()

    // Limite da metade esquerda da tela para o joystick (em pixels)
    private var screenHalfWidth = 0f
    private var screenWidth = 0f
    private var screenHeight = 0f

    // Botão de pulo no canto inferior direito
    private var buttonA_X = 0f
    private var buttonA_Y = 0f
    private var buttonA_Radius = 0f
    private var isButtonAPressed = false
    private var buttonAPointerId = -1

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

        // Botão A (PULO) — posicionado onde o herói fica na tela (centro-direita, não na borda)
        buttonA_X = width - 200f * density
        buttonA_Y = height - 80f * density

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
            // Metade direita: verifica se é o botão de pulo ou o joystick de mira
            val distA = hypot(x - buttonA_X, y - buttonA_Y)

            if (distA <= buttonA_Radius) {
                isButtonAPressed = true
                buttonAPointerId = id
            } else {
                aimJoystick.onTouchDown(x, y, id)
            }
        }
    }

    private fun handleTouchMove(x: Float, y: Float, id: Int) {
        if (x <= screenHalfWidth) {
            // Movimento na esquerda: repassa para o joystick correspondente
            moveJoystick.onTouchMove(x, y, id)
        } else {
            // Se o dedo que moveu era o do pulo, re-valida colisão
            if (id == buttonAPointerId) {
                val dist = hypot(x - buttonA_X, y - buttonA_Y)
                isButtonAPressed = dist <= buttonA_Radius * 1.5f
            } else {
                aimJoystick.onTouchMove(x, y, id)
            }
        }
    }

    private fun handleTouchUp(x: Float, y: Float, id: Int) {
        // Libera joysticks se o dedo levantado for o dono deles
        moveJoystick.onTouchUp(id)
        aimJoystick.onTouchUp(id)

        // Libera o botão de pulo
        if (id == buttonAPointerId) {
            isButtonAPressed = false
            buttonAPointerId = -1
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

        // Joystick de mira (twin-stick): direção do tiro em 360°
        if (aimJoystick.isActive) {
            gameState.isShooting = true
            gameState.shootingAngle = kotlin.math.atan2(
                aimJoystick.directionY.toDouble(),
                aimJoystick.directionX.toDouble()
            ).toFloat()
            // Atualiza direção visual do herói para acompanhar a mira ao atirar
            gameState.heroDirection = when {
                aimJoystick.directionX < -0.1f -> Direction.WEST
                aimJoystick.directionX > 0.1f -> Direction.EAST
                else -> gameState.heroDirection
            }
        } else {
            gameState.isShooting = false
        }

        if (mazeData != null) {
            // Sincroniza a direção visual (apenas quando não está atirando — twin-stick)
            if (!gameState.isShooting) {
                if (inputVx < -0.1f) {
                    gameState.heroDirection = Direction.WEST
                } else if (inputVx > 0.1f) {
                    gameState.heroDirection = Direction.EAST
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

        // 3. Joystick de Mira (Azul Água, com ícone de gota)
        aimJoystick.draw(canvas, accentColor = Color.rgb(80, 180, 255), drawWaterIcon = true)
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
