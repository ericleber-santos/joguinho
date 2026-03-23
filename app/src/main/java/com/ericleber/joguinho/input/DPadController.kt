package com.ericleber.joguinho.input

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.ericleber.joguinho.core.Direction

/**
 * Controle direcional fixo (D-pad) como alternativa ao joystick flutuante.
 *
 * Exibe 4 botões direcionais (cima, baixo, esquerda, direita) fixos na região
 * esquerda da tela. Área de toque mínima de 80x80dp por botão (Requisito 13.4).
 *
 * Requisito 12.3
 */
class DPadController {

    companion object {
        private const val BUTTON_SIZE_DP = 80f
        private const val BUTTON_GAP_DP = 4f
        private const val MARGIN_DP = 24f
    }

    private val density = Resources.getSystem().displayMetrics.density
    private val buttonSizePx = BUTTON_SIZE_DP * density
    private val gapPx = BUTTON_GAP_DP * density
    private val marginPx = MARGIN_DP * density

    // Retângulos de toque para cada direção
    private var upRect    = RectF()
    private var downRect  = RectF()
    private var leftRect  = RectF()
    private var rightRect = RectF()

    // Estado dos botões pressionados
    private val pressedButtons = mutableSetOf<Direction>()

    // Mapeamento pointer → botão (para multi-touch)
    private val pointerToButton = mutableMapOf<Int, Direction>()

    // Paints
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val buttonPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.FILL
        textSize = 28f * density
        textAlign = Paint.Align.CENTER
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------

    /**
     * Calcula as posições dos botões com base na altura da tela.
     * Deve ser chamado quando o tamanho da view é conhecido.
     */
    fun layout(screenWidth: Float, screenHeight: Float) {
        // Centro do D-pad: canto inferior esquerdo
        val centerX = marginPx + buttonSizePx + gapPx
        val centerY = screenHeight - marginPx - buttonSizePx - gapPx

        upRect = RectF(
            centerX - buttonSizePx / 2f,
            centerY - buttonSizePx - gapPx - buttonSizePx,
            centerX + buttonSizePx / 2f,
            centerY - buttonSizePx - gapPx
        )
        downRect = RectF(
            centerX - buttonSizePx / 2f,
            centerY + gapPx,
            centerX + buttonSizePx / 2f,
            centerY + gapPx + buttonSizePx
        )
        leftRect = RectF(
            centerX - buttonSizePx - gapPx - buttonSizePx,
            centerY - buttonSizePx / 2f,
            centerX - buttonSizePx - gapPx,
            centerY + buttonSizePx / 2f
        )
        rightRect = RectF(
            centerX + gapPx,
            centerY - buttonSizePx / 2f,
            centerX + gapPx + buttonSizePx,
            centerY + buttonSizePx / 2f
        )
    }

    // -------------------------------------------------------------------------
    // Eventos de toque
    // -------------------------------------------------------------------------

    fun onTouchDown(x: Float, y: Float, id: Int) {
        val dir = hitTest(x, y) ?: return
        pressedButtons.add(dir)
        pointerToButton[id] = dir
    }

    fun onTouchMove(x: Float, y: Float, id: Int) {
        val prevDir = pointerToButton[id]
        val newDir = hitTest(x, y)

        if (prevDir != null && prevDir != newDir) {
            pressedButtons.remove(prevDir)
        }
        if (newDir != null) {
            pressedButtons.add(newDir)
            pointerToButton[id] = newDir
        } else {
            pointerToButton.remove(id)
        }
    }

    fun onTouchUp(id: Int) {
        val dir = pointerToButton.remove(id) ?: return
        pressedButtons.remove(dir)
    }

    // -------------------------------------------------------------------------
    // Estado
    // -------------------------------------------------------------------------

    /**
     * Retorna a direção ativa com base nos botões pressionados.
     * Suporta diagonais (dois botões simultâneos).
     */
    fun getActiveDirection(): Direction? {
        val up    = Direction.NORTH in pressedButtons
        val down  = Direction.SOUTH in pressedButtons
        val left  = Direction.WEST  in pressedButtons
        val right = Direction.EAST  in pressedButtons

        return when {
            up && right -> Direction.NORTH_EAST
            up && left  -> Direction.NORTH_WEST
            down && right -> Direction.SOUTH_EAST
            down && left  -> Direction.SOUTH_WEST
            up    -> Direction.NORTH
            down  -> Direction.SOUTH
            left  -> Direction.WEST
            right -> Direction.EAST
            else  -> null
        }
    }

    val isActive: Boolean get() = pressedButtons.isNotEmpty()

    // -------------------------------------------------------------------------
    // Renderização
    // -------------------------------------------------------------------------

    fun draw(canvas: Canvas) {
        drawButton(canvas, upRect,    Direction.NORTH, "▲")
        drawButton(canvas, downRect,  Direction.SOUTH, "▼")
        drawButton(canvas, leftRect,  Direction.WEST,  "◀")
        drawButton(canvas, rightRect, Direction.EAST,  "▶")
    }

    private fun drawButton(canvas: Canvas, rect: RectF, dir: Direction, arrow: String) {
        val paint = if (dir in pressedButtons) buttonPressedPaint else buttonPaint
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        canvas.drawText(arrow, rect.centerX(), rect.centerY() + arrowPaint.textSize / 3f, arrowPaint)
    }

    // -------------------------------------------------------------------------
    // Hit test
    // -------------------------------------------------------------------------

    private fun hitTest(x: Float, y: Float): Direction? = when {
        upRect.contains(x, y)    -> Direction.NORTH
        downRect.contains(x, y)  -> Direction.SOUTH
        leftRect.contains(x, y)  -> Direction.WEST
        rightRect.contains(x, y) -> Direction.EAST
        else -> null
    }
}
