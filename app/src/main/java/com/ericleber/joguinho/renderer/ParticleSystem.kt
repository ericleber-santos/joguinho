package com.ericleber.joguinho.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Tipos de partícula disponíveis.
 */
enum class ParticleType { CIRCLE, RECT, SPARK }

/**
 * Representa uma partícula individual no sistema.
 *
 * @param x posição X atual
 * @param y posição Y atual
 * @param vx velocidade horizontal (pixels/segundo)
 * @param vy velocidade vertical (pixels/segundo)
 * @param life vida restante (0.0 a maxLife)
 * @param maxLife vida máxima inicial
 * @param startColor cor inicial
 * @param endColor cor final (quando life = 0)
 * @param startSize tamanho inicial em pixels
 * @param endSize tamanho final em pixels
 * @param type tipo de forma da partícula
 */
data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var maxLife: Float,
    val startColor: Int,
    val endColor: Int,
    val startSize: Float,
    val endSize: Float,
    val type: ParticleType
)

/**
 * Configuração para emissão de partículas.
 *
 * @param vxRange intervalo de velocidade horizontal
 * @param vyRange intervalo de velocidade vertical
 * @param lifeRange intervalo de vida das partículas
 * @param startColor cor inicial
 * @param endColor cor final
 * @param startSize tamanho inicial
 * @param endSize tamanho final
 * @param type tipo de partícula
 */
data class ParticleConfig(
    val vxRange: ClosedFloatingPointRange<Float>,
    val vyRange: ClosedFloatingPointRange<Float>,
    val lifeRange: ClosedFloatingPointRange<Float>,
    val startColor: Int,
    val endColor: Int,
    val startSize: Float,
    val endSize: Float,
    val type: ParticleType
)

/**
 * Sistema de partículas usando drawCircle, drawRect e drawPath com interpolação de cor/opacidade.
 *
 * Requisitos: 3.4, 17.4
 */
class ParticleSystem {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val sparkPath = Path()
    private val random = java.util.Random()

    /**
     * Emite [count] partículas na posição (x, y) com a configuração fornecida.
     */
    fun emit(x: Float, y: Float, count: Int, config: ParticleConfig) {
        repeat(count) {
            val vx = lerp(config.vxRange.start, config.vxRange.endInclusive, random.nextFloat())
            val vy = lerp(config.vyRange.start, config.vyRange.endInclusive, random.nextFloat())
            val life = lerp(config.lifeRange.start, config.lifeRange.endInclusive, random.nextFloat())
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy,
                    life = life,
                    maxLife = life,
                    startColor = config.startColor,
                    endColor = config.endColor,
                    startSize = config.startSize,
                    endSize = config.endSize,
                    type = config.type
                )
            )
        }
    }

    /**
     * Atualiza todas as partículas ativas.
     * @param deltaTime tempo decorrido em segundos
     */
    fun update(deltaTime: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * deltaTime
            p.y += p.vy * deltaTime
            p.life -= deltaTime
            if (p.life <= 0f) {
                iterator.remove()
            }
        }
    }

    /**
     * Desenha todas as partículas ativas com interpolação de cor, opacidade e tamanho.
     */
    fun render(canvas: Canvas) {
        for (p in particles) {
            val progress = 1f - (p.life / p.maxLife)  // 0.0 = início, 1.0 = fim
            val color = lerpColor(p.startColor, p.endColor, progress)
            val alpha = ((p.life / p.maxLife) * 255).toInt().coerceIn(0, 255)
            val size = lerp(p.startSize, p.endSize, progress)

            paint.color = Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
            )

            when (p.type) {
                ParticleType.CIRCLE -> {
                    canvas.drawCircle(p.x, p.y, size / 2f, paint)
                }
                ParticleType.RECT -> {
                    val half = size / 2f
                    canvas.drawRect(p.x - half, p.y - half, p.x + half, p.y + half, paint)
                }
                ParticleType.SPARK -> {
                    sparkPath.reset()
                    sparkPath.moveTo(p.x, p.y - size)
                    sparkPath.lineTo(p.x + size * 0.3f, p.y)
                    sparkPath.lineTo(p.x, p.y + size * 0.5f)
                    sparkPath.lineTo(p.x - size * 0.3f, p.y)
                    sparkPath.close()
                    canvas.drawPath(sparkPath, paint)
                }
            }
        }
    }

    /**
     * Remove todas as partículas ativas.
     */
    fun clear() {
        particles.clear()
    }

    /** Número de partículas ativas. */
    fun activeCount(): Int = particles.size

    // -------------------------------------------------------------------------
    // Utilitários de interpolação
    // -------------------------------------------------------------------------

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /**
     * Interpola linearmente entre duas cores ARGB.
     */
    private fun lerpColor(start: Int, end: Int, t: Float): Int {
        val r = lerp(Color.red(start).toFloat(), Color.red(end).toFloat(), t).toInt()
        val g = lerp(Color.green(start).toFloat(), Color.green(end).toFloat(), t).toInt()
        val b = lerp(Color.blue(start).toFloat(), Color.blue(end).toFloat(), t).toInt()
        return Color.rgb(
            r.coerceIn(0, 255),
            g.coerceIn(0, 255),
            b.coerceIn(0, 255)
        )
    }
}
