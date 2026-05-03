package com.ericleber.joguinho.input

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import com.ericleber.joguinho.core.Direction
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Joystick virtual flutuante que se reposiciona para o ponto de toque.
 *
 * - Raio mínimo de 80dp (Requisito 4.1, 13.4)
 * - Reposicionamento dinâmico ao tocar (Requisito 4.7)
 * - Mapeia para 8 direções cardinais/diagonais (Requisito 4.3)
 *
 * Estilos:
 * - MOVE: Âmbar/Tocha
 * - SHOOT: Azul/Água
 *
 * Requisitos: 4.1, 4.7, 12.3, 13.4
 */
class FloatingJoystick {

    companion object {
        private const val MIN_RADIUS_DP = 80f
        private const val KNOB_RATIO = 0.4f          // raio do knob = 40% do raio externo
        private const val DEAD_ZONE_RATIO = 0.15f    // zona morta central = 15% do raio
    }

    // Raio em pixels (calculado a partir de dp na inicialização)
    private val radiusPx: Float = MIN_RADIUS_DP * Resources.getSystem().displayMetrics.density

    // Centro atual do joystick (reposicionado a cada toque)
    var centerX: Float = 0f
        private set
    var centerY: Float = 0f
        private set

    // Posição atual do knob (dentro do raio)
    private var knobX: Float = 0f
    private var knobY: Float = 0f

    // Vetor de direção normalizado [-1, 1]
    var directionX: Float = 0f
        private set
    var directionY: Float = 0f
        private set

    // Magnitude do input [0, 1]
    var magnitude: Float = 0f
        private set

    // Indica se o joystick está ativo (dedo pressionado)
    var isActive: Boolean = false
        private set

    // ID do ponteiro de toque associado a este joystick
    private var pointerId: Int = -1

    // Paints para renderização — estética cave: anel translúcido âmbar/branco
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 220, 180, 100)   // anel externo: âmbar translúcido (tocha)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val outerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(25, 200, 160, 80)    // preenchimento muito sutil
        style = Paint.Style.FILL
    }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 240, 200, 120)  // knob: âmbar mais opaco
        style = Paint.Style.FILL
    }
    private val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 230, 150)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // -------------------------------------------------------------------------
    // Eventos de toque
    // -------------------------------------------------------------------------

    /**
     * Chamado quando um novo toque é detectado na metade esquerda da tela.
     * Reposiciona o centro do joystick para o ponto de toque.
     */
    fun onTouchDown(x: Float, y: Float, id: Int) {
        centerX = x
        centerY = y
        knobX = x
        knobY = y
        pointerId = id
        isActive = true
        directionX = 0f
        directionY = 0f
        magnitude = 0f
    }

    /**
     * Chamado quando o dedo se move. Atualiza a posição do knob e o vetor de direção.
     * Garante que o knob não ultrapasse o raio externo.
     */
    fun onTouchMove(x: Float, y: Float, id: Int) {
        if (id != pointerId) return

        val dx = x - centerX
        val dy = y - centerY
        val dist = hypot(dx, dy)

        // Limita o knob ao raio externo
        val clampedDist = min(dist, radiusPx)
        val angle = atan2(dy, dx)

        knobX = centerX + clampedDist * kotlin.math.cos(angle)
        knobY = centerY + clampedDist * kotlin.math.sin(angle)

        // Calcula magnitude normalizada [0, 1]
        magnitude = clampedDist / radiusPx

        // Aplica zona morta
        if (magnitude < DEAD_ZONE_RATIO) {
            directionX = 0f
            directionY = 0f
            magnitude = 0f
        } else {
            directionX = dx / dist
            directionY = dy / dist
        }
    }

    /**
     * Chamado quando o dedo é levantado. Reseta o joystick.
     */
    fun onTouchUp(id: Int) {
        if (id != pointerId) return
        isActive = false
        pointerId = -1
        directionX = 0f
        directionY = 0f
        magnitude = 0f
        knobX = centerX
        knobY = centerY
    }

    // -------------------------------------------------------------------------
    // Direção mapeada para 8 direções
    // -------------------------------------------------------------------------

    /**
     * Converte o vetor contínuo para uma das 8 direções cardinais/diagonais.
     * Retorna null se o joystick estiver na zona morta.
     * Requisito 4.3
     */
    fun getMappedDirection(): Direction? {
        if (magnitude < DEAD_ZONE_RATIO) return null

        // Ângulo em graus, 0° = leste, sentido horário
        val angleDeg = Math.toDegrees(atan2(directionY.toDouble(), directionX.toDouble()))
        val normalized = ((angleDeg + 360.0) % 360.0)

        return when {
            normalized < 22.5  -> Direction.EAST
            normalized < 67.5  -> Direction.SOUTH_EAST
            normalized < 112.5 -> Direction.SOUTH
            normalized < 157.5 -> Direction.SOUTH_WEST
            normalized < 202.5 -> Direction.WEST
            normalized < 247.5 -> Direction.NORTH_WEST
            normalized < 292.5 -> Direction.NORTH
            normalized < 337.5 -> Direction.NORTH_EAST
            else               -> Direction.EAST
        }
    }

    /**
     * Retorna o vetor de movimento contínuo (para movimento suave).
     * Já normalizado e com magnitude aplicada.
     */
    fun getMovementVector(): PointF = PointF(directionX * magnitude, directionY * magnitude)

    /**
     * Desenha o joystick no canvas com estilo opcional.
     * @param accentColor Cor principal (âmbar para movimento, azul para tiro)
     * @param drawWaterIcon Se true, desenha uma gota d'água no knob
     */
    fun draw(canvas: Canvas, accentColor: Int = Color.rgb(220, 180, 100), drawWaterIcon: Boolean = false) {
        if (!isActive) return

        // Ajusta as cores dos paints com base no acento
        outerPaint.color = Color.argb(70, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        outerFillPaint.color = Color.argb(25, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        innerPaint.color = Color.argb(160, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        innerBorderPaint.color = Color.argb(100, 255, 230, 150)

        // Preenchimento sutil do anel externo
        canvas.drawCircle(centerX, centerY, radiusPx, outerFillPaint)
        // Borda do anel externo (âmbar translúcido — estética tocha)
        canvas.drawCircle(centerX, centerY, radiusPx, outerPaint)

        // Knob: preenchimento + borda
        canvas.drawCircle(knobX, knobY, radiusPx * KNOB_RATIO, innerPaint)
        canvas.drawCircle(knobX, knobY, radiusPx * KNOB_RATIO, innerBorderPaint)

        // Desenha ícone de água se solicitado
        if (drawWaterIcon) {
            val iconSize = radiusPx * KNOB_RATIO * 0.6f
            val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            // Gota d'água simples (triângulo + círculo)
            val path = android.graphics.Path()
            path.moveTo(knobX, knobY - iconSize)
            path.lineTo(knobX - iconSize * 0.6f, knobY + iconSize * 0.3f)
            path.lineTo(knobX + iconSize * 0.6f, knobY + iconSize * 0.3f)
            path.close()
            canvas.drawPath(path, dropPaint)
            canvas.drawCircle(knobX, knobY + iconSize * 0.3f, iconSize * 0.6f, dropPaint)
        }
    }
}
