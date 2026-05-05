package com.ericleber.joguinho.renderer

/** Estado de ciclo de vida de uma gota de caverna. */
enum class DripState { FORMING, FALLING, SPLASHING, DEAD }

/**
 * Representa uma única gota de água — usada em pool fixo pelo DripSystem.
 * Todos os campos são mutáveis para reutilização sem alocação.
 */
data class DripParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vy: Float = 0f,
    var ceilingY: Float = 0f,
    var floorY: Float = 0f,
    var state: DripState = DripState.DEAD,
    var scale: Float = 0f,
    var alpha: Float = 1f,
    var splashTimer: Float = 0f   // 0..1 (SPLASHING progress)
)

/**
 * Fonte fixa de goteira registrada na geração do mapa.
 * Uma fonte emite gotas periodicamente com intervalo aleatório.
 */
data class DripSource(
    val gridX: Int,
    val gridY: Int,
    val gridFloorY: Int,
    val intervalMs: Long,
    var timerMs: Long = 0L
)
