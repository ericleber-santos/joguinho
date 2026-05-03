package com.ericleber.joguinho.ui

import com.ericleber.joguinho.core.ScorePopup
import com.ericleber.joguinho.core.Position
import java.util.*

/**
 * Sistema de Object Pooling para números de pontuação (Score).
 * Evita alocação excessiva de objetos ao derrotar muitos inimigos.
 */
object ScorePopupPool {
    private val pool: Queue<ScorePopup> = LinkedList()
    private const val MAX_POOL_SIZE = 50

    /**
     * Obtém um Popup do pool ou cria um novo se necessário.
     */
    fun obtain(id: String, position: Position, score: Int, currentTimeMs: Long): ScorePopup {
        val popup = pool.poll()
        return if (popup != null) {
            popup.id = id
            popup.position = position
            popup.score = score
            popup.createdAtMs = currentTimeMs
            popup.alpha = 255
            popup.offsetY = 0f
            popup
        } else {
            ScorePopup(id, position, score, currentTimeMs)
        }
    }

    /**
     * Devolve um popup ao pool para reuso futuro.
     */
    fun recycle(popup: ScorePopup) {
        if (pool.size < MAX_POOL_SIZE) {
            pool.offer(popup)
        }
    }
}
