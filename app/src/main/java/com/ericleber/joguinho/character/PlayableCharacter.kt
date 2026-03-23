package com.ericleber.joguinho.character

import android.graphics.Bitmap
import com.ericleber.joguinho.core.Direction
import com.ericleber.joguinho.core.Position

/**
 * Estado de animação de um personagem jogável.
 */
enum class CharacterState {
    IDLE, WALKING, RUNNING, SLOWDOWN, CELEBRATING
}

/**
 * Abstração de personagem controlável pelo Player.
 * Requisitos: 4.8, 24.1, 24.2, 24.3
 */
abstract class PlayableCharacter {

    abstract val id: String
    abstract val baseSpeed: Float
    abstract val skinId: String

    var currentState: CharacterState = CharacterState.IDLE
    var currentDirection: Direction = Direction.SOUTH
    var position: Position = Position(0, 0)
    var isSlowedDown: Boolean = false
    var slowdownRemainingMs: Long = 0L

    /**
     * Velocidade efetiva: 40% da base durante Slowdown (Requisito 4.8).
     */
    val effectiveSpeed: Float
        get() = if (isSlowedDown) baseSpeed * 0.4f else baseSpeed

    /**
     * Aplica Slowdown por [durationMs] milissegundos.
     */
    fun onSlowdown(durationMs: Long) {
        isSlowedDown = true
        slowdownRemainingMs = durationMs
        currentState = CharacterState.SLOWDOWN
    }

    /**
     * Atualiza o timer de Slowdown. Deve ser chamado a cada frame pelo GameLoop.
     * @param deltaMs tempo decorrido em milissegundos
     */
    fun updateSlowdown(deltaMs: Long) {
        if (isSlowedDown) {
            slowdownRemainingMs -= deltaMs
            if (slowdownRemainingMs <= 0L) {
                isSlowedDown = false
                slowdownRemainingMs = 0L
                if (currentState == CharacterState.SLOWDOWN) {
                    currentState = CharacterState.IDLE
                }
            }
        }
    }

    /**
     * Retorna os frames de animação para o [state] fornecido.
     * Mínimo 8 frames para movimento, 4 frames para idle (Requisito 8.5, 17.2).
     */
    abstract fun getAnimationFrames(state: CharacterState): List<Bitmap>
}
