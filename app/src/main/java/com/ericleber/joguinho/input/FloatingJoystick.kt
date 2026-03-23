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
 * - Reposicionamento dinâmico ao tocar na metade esquerda da tela (Requisito 4.7)
 * - Mapeia para 8 direções cardinais/diagonais (Requisito 4.3)
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

    // Paints para renderização
    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.FILL
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

    // -------------------------------------------------------------------------
    // Renderização
    // -------------------------------------------------------------------------

    /**
     * Desenha o joystick no canvas. Deve ser chamado pelo Renderer na UI thread do SurfaceView.
     */
    fun draw(canvas: Canvas) {
        if (!isActive) return

        // Anel externo
        canvas.drawCircle(centerX, centerY, radiusPx, outerPaint)

        // Knob interno
        canvas.drawCircle(knobX, knobY, radiusPx * KNOB_RATIO, innerPaint)
    }
}
