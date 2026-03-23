package com.ericleber.joguinho.character

import android.graphics.Bitmap
import com.ericleber.joguinho.core.MazeData
import com.ericleber.joguinho.core.Position

/**
 * Estado de animação de um personagem companheiro.
 */
enum class CompanionState {
    SEGUINDO,
    FAREJANDO,
    ALERTANDO,
    INCENTIVANDO,
    SLOWDOWN_PROPRIO,
    ENTUSIASMADO,
    CHAMANDO,
    CELEBRANDO
}

/**
 * Evento de jogo relevante para a IA do companheiro.
 */
sealed class GameEvent {
    object HeroReceivedSlowdown : GameEvent()
    object HeroCompletedMap : GameEvent()
    object HeroCompletedFloor : GameEvent()
    data class MonsterNearby(val distanceInTiles: Float) : GameEvent()
    object HeroNearExit : GameEvent()
    object HeroSurpassedObstacle : GameEvent()
}

/**
 * Abstração de personagem companheiro (não controlado pelo Player).
 * Requisitos: 24.3
 */
abstract class CompanionCharacter {

    abstract val id: String
    abstract val skinId: String

    var currentState: CompanionState = CompanionState.SEGUINDO
    var position: Position = Position(0, 0)
    var isSlowedDown: Boolean = false
    var slowdownRemainingMs: Long = 0L

    val effectiveSpeed: Float
        get() = if (isSlowedDown) baseSpeed * 0.4f else baseSpeed

    protected abstract val baseSpeed: Float

    /**
     * Atualiza a IA do companheiro com base no contexto atual do jogo.
     */
    abstract fun updateAI(
        heroPosition: Position,
        mazeData: MazeData,
        gameEvents: List<GameEvent>
    )

    /**
     * Aplica Slowdown por [durationMs] milissegundos.
     */
    fun onSlowdown(durationMs: Long) {
        isSlowedDown = true
        slowdownRemainingMs = durationMs
        currentState = CompanionState.SLOWDOWN_PROPRIO
    }

    /**
     * Atualiza o timer de Slowdown. Deve ser chamado a cada frame pelo GameLoop.
     */
    fun updateSlowdown(deltaMs: Long) {
        if (isSlowedDown) {
            slowdownRemainingMs -= deltaMs
            if (slowdownRemainingMs <= 0L) {
                isSlowedDown = false
                slowdownRemainingMs = 0L
                if (currentState == CompanionState.SLOWDOWN_PROPRIO) {
                    currentState = CompanionState.SEGUINDO
                }
            }
        }
    }

    /**
     * Retorna os frames de animação para o [state] fornecido.
     * Mínimo 12 frames por estado para o Spike (Requisito 11.5, 17.2).
     */
    abstract fun getAnimationFrames(state: CompanionState): List<Bitmap>
}
