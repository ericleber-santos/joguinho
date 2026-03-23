package com.ericleber.joguinho.character

import com.ericleber.joguinho.core.Position

/**
 * Contexto passado ao SpikeAI a cada frame para avaliação de transições.
 */
data class SpikeAIContext(
    val heroPosition: Position,
    val heroMoved: Boolean,
    val heroStoppedDurationSec: Float,
    val heroReceivedSlowdown: Boolean,
    val heroDistanceToExitTiles: Float,
    val nearestMonsterDistanceTiles: Float,
    val spikeIsSlowed: Boolean,
    val heroSurpassedObstacle: Boolean,
    val heroReachedExit: Boolean
)

/**
 * Máquina de estados da IA do Spike.
 *
 * 8 estados comportamentais conforme design:
 * SEGUINDO, FAREJANDO, ALERTANDO, INCENTIVANDO,
 * SLOWDOWN_PROPRIO, ENTUSIASMADO, CHAMANDO, CELEBRANDO
 *
 * Requisitos: 11.1, 11.2, 11.3, 11.6, 11.7, 11.8, 11.9
 */
class SpikeAI(private val spike: Spike) {

    private var currentState = CompanionState.SEGUINDO
    private var stateTimerSec = 0f

    /**
     * Atualiza a máquina de estados do Spike.
     * Deve ser chamado a cada frame pelo GameLoop.
     * @param deltaTimeSec tempo decorrido em segundos
     * @param context contexto atual do jogo
     */
    fun update(deltaTimeSec: Float, context: SpikeAIContext) {
        stateTimerSec += deltaTimeSec

        val nextState = when (currentState) {
            CompanionState.SEGUINDO -> evaluateFromSeguindo(context)

            // Farejando: Hero parado > 3s → farejar; se mover → voltar (Requisito 11.1)
            CompanionState.FAREJANDO -> when {
                context.nearestMonsterDistanceTiles < 5f -> transition(CompanionState.ALERTANDO)
                context.heroMoved -> transition(CompanionState.SEGUINDO)
                else -> CompanionState.FAREJANDO
            }

            // Alertando: latido ao detectar Monster < 5 tiles (Requisito 11.3)
            CompanionState.ALERTANDO -> when {
                context.heroReceivedSlowdown -> transition(CompanionState.INCENTIVANDO)
                context.nearestMonsterDistanceTiles >= 5f -> transition(CompanionState.SEGUINDO)
                else -> CompanionState.ALERTANDO
            }

            // Incentivando: preocupação por 1s, depois entra em Slowdown próprio (Requisito 11.2)
            CompanionState.INCENTIVANDO -> when {
                stateTimerSec >= 1f -> {
                    spike.onSlowdown(2000L) // Spike recebe Slowdown de 2s (Requisito 5.1)
                    transition(CompanionState.SLOWDOWN_PROPRIO)
                }
                else -> CompanionState.INCENTIVANDO
            }

            // Slowdown próprio: aguarda fim do Slowdown
            CompanionState.SLOWDOWN_PROPRIO -> when {
                !context.spikeIsSlowed -> transition(CompanionState.SEGUINDO)
                else -> CompanionState.SLOWDOWN_PROPRIO
            }

            // Entusiasmado: Hero < 3 tiles do Exit (Requisito 11.7)
            CompanionState.ENTUSIASMADO -> when {
                context.heroReachedExit -> transition(CompanionState.CELEBRANDO)
                context.heroDistanceToExitTiles >= 3f -> transition(CompanionState.SEGUINDO)
                else -> CompanionState.ENTUSIASMADO
            }

            // Chamando: Hero parado > 5s — corre em direção ao Exit e volta (Requisito 11.6)
            CompanionState.CHAMANDO -> when {
                context.heroMoved -> transition(CompanionState.SEGUINDO)
                context.nearestMonsterDistanceTiles < 5f -> transition(CompanionState.ALERTANDO)
                else -> CompanionState.CHAMANDO
            }

            // Celebrando: 3s após Hero alcançar Exit (Requisito 11.4)
            CompanionState.CELEBRANDO -> when {
                stateTimerSec >= 3f -> transition(CompanionState.SEGUINDO)
                else -> CompanionState.CELEBRANDO
            }
        }

        currentState = nextState
        spike.currentState = currentState
    }

    /**
     * Avalia transições a partir do estado SEGUINDO.
     * Prioridade: Slowdown > Entusiasmado > Alertando > Chamando > Farejando
     */
    private fun evaluateFromSeguindo(ctx: SpikeAIContext): CompanionState = when {
        ctx.heroReceivedSlowdown -> transition(CompanionState.INCENTIVANDO)
        ctx.heroReachedExit -> transition(CompanionState.CELEBRANDO)
        ctx.heroDistanceToExitTiles < 3f -> CompanionState.ENTUSIASMADO
        ctx.nearestMonsterDistanceTiles < 5f -> CompanionState.ALERTANDO
        ctx.heroStoppedDurationSec > 5f -> transition(CompanionState.CHAMANDO)
        ctx.heroStoppedDurationSec > 3f -> CompanionState.FAREJANDO
        else -> CompanionState.SEGUINDO
    }

    /**
     * Realiza a transição para um novo estado, resetando o timer.
     */
    private fun transition(newState: CompanionState): CompanionState {
        stateTimerSec = 0f
        return newState
    }

    fun getCurrentState(): CompanionState = currentState
}
