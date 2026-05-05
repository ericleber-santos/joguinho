package com.ericleber.joguinho.renderer

import kotlin.math.sin

/**
 * Utilitários de animação baseados em tempo para props decorativos.
 *
 * Todas as funções são puras (sem estado) e podem ser chamadas no render thread.
 * Usam sin() para ondulações suaves e determinísticas.
 */
object PropAnimator {

    /**
     * Fator de escala vertical para musgo animado — ondulação de 2%.
     * Frequência: ~0.5 ciclos por segundo (período de 2s).
     *
     * @param timeMs tempo absoluto em ms (ex: frameTotal do Renderer)
     * @param phaseOffset offset de fase para evitar que todos os tiles pulsem em sincronia
     */
    fun mossScaleY(timeMs: Long, phaseOffset: Float = 0f): Float {
        val t = timeMs / 1000f
        return 1f + sin(t * 0.5f + phaseOffset) * 0.02f
    }

    /**
     * Fator de escala para cogumelo pulsante — pulso de 5%.
     * Frequência: ~1.5 ciclos por segundo (período de ~0.67s).
     */
    fun mushroomScale(timeMs: Long, phaseOffset: Float = 0f): Float {
        val t = timeMs / 1000f
        return 0.95f + sin(t * 1.5f + phaseOffset) * 0.05f
    }

    /**
     * Alpha pulsante para glow de cristal — variação de 20%.
     * Frequência: ~0.8 ciclos por segundo.
     */
    fun crystalGlowAlpha(timeMs: Long, phaseOffset: Float = 0f): Float {
        val t = timeMs / 1000f
        return 0.7f + sin(t * 0.8f + phaseOffset) * 0.2f
    }

    /**
     * Offset X suave para runa mágica — oscilação horizontal de 1px.
     */
    fun runeGlowOffset(timeMs: Long, phaseOffset: Float = 0f): Float {
        val t = timeMs / 1000f
        return sin(t * 1.2f + phaseOffset) * 1f
    }
}
