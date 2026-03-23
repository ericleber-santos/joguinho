package com.ericleber.joguinho.character

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.ericleber.joguinho.core.MazeData
import com.ericleber.joguinho.core.Position

/**
 * Implementação concreta do Spike — viralata brasileiro branco com manchas pretas.
 * Companheiro do Hero, controlado pela SpikeAI.
 * Sprites gerados programaticamente via Canvas (Requisito 17.1).
 * Requisitos: 11.5, 24.3
 */
class Spike(
    override val id: String = "spike",
    override val skinId: String = "default",
    override val baseSpeed: Float = 4f
) : CompanionCharacter() {

    // Cache de frames por estado — mínimo 12 frames por estado (Requisito 11.5)
    private val frameCache = HashMap<CompanionState, List<Bitmap>>()

    override fun updateAI(
        heroPosition: Position,
        mazeData: MazeData,
        gameEvents: List<GameEvent>
    ) {
        // A lógica de IA é delegada para SpikeAI.
        // Este método pode ser usado para atualizações de posição simples (pathfinding).
        // Mantém distância máxima de 2 tiles do Hero (Requisito 4.5).
        val dx = heroPosition.x - position.x
        val dy = heroPosition.y - position.y
        val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
        if (distance > 2.0) {
            // Move em direção ao Hero (pathfinding simples)
            val stepX = if (dx != 0) dx / Math.abs(dx) else 0
            val stepY = if (dy != 0) dy / Math.abs(dy) else 0
            position = Position(position.x + stepX, position.y + stepY)
        }
    }

    override fun getAnimationFrames(state: CompanionState): List<Bitmap> {
        return frameCache.getOrPut(state) { generateFrames(state) }
    }

    /**
     * Gera 12 frames por estado comportamental (Requisito 11.5, 17.2).
     */
    private fun generateFrames(state: CompanionState): List<Bitmap> {
        return List(12) { frameIndex -> generateSpikeFrame(state, frameIndex) }
    }

    private fun generateSpikeFrame(state: CompanionState, frameIndex: Int): Bitmap {
        val size = 24
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
        }

        val t = frameIndex.toFloat() / 12f
        val bobOffset = (Math.sin(t * Math.PI * 2) * 1.0).toInt()

        // Corpo — branco predominante (viralata brasileiro)
        paint.color = Color.WHITE
        canvas.drawRect(6f, (10 + bobOffset).toFloat(), 18f, (20 + bobOffset).toFloat(), paint)

        // Manchas pretas no dorso (Requisito 11.5)
        paint.color = Color.BLACK
        canvas.drawRect(8f, (10 + bobOffset).toFloat(), 11f, (14 + bobOffset).toFloat(), paint)
        canvas.drawRect(14f, (12 + bobOffset).toFloat(), 17f, (16 + bobOffset).toFloat(), paint)

        // Cabeça
        paint.color = Color.WHITE
        canvas.drawRect(7f, (5 + bobOffset).toFloat(), 17f, (11 + bobOffset).toFloat(), paint)

        // Mancha preta na cabeça
        paint.color = Color.BLACK
        canvas.drawRect(9f, (5 + bobOffset).toFloat(), 12f, (8 + bobOffset).toFloat(), paint)

        // Olhos — expressivos conforme estado
        val eyeColor = when (state) {
            CompanionState.ALERTANDO -> Color.rgb(255, 80, 80)   // vermelho alerta
            CompanionState.INCENTIVANDO -> Color.rgb(255, 200, 50) // dourado incentivo
            CompanionState.ENTUSIASMADO -> Color.rgb(100, 220, 100) // verde entusiasmo
            CompanionState.SLOWDOWN_PROPRIO -> Color.rgb(150, 150, 255) // azulado lento
            else -> Color.rgb(50, 50, 50) // escuro normal
        }
        paint.color = eyeColor
        canvas.drawRect(9f, (6 + bobOffset).toFloat(), 11f, (8 + bobOffset).toFloat(), paint)
        canvas.drawRect(13f, (6 + bobOffset).toFloat(), 15f, (8 + bobOffset).toFloat(), paint)

        // Orelhas — posição varia por estado
        val earOffset = when (state) {
            CompanionState.ALERTANDO, CompanionState.ENTUSIASMADO -> -1 // orelhas erguidas
            CompanionState.INCENTIVANDO, CompanionState.SLOWDOWN_PROPRIO -> 2 // orelhas abaixadas
            else -> 0
        }
        paint.color = Color.WHITE
        canvas.drawRect(6f, (3 + bobOffset + earOffset).toFloat(), 9f, (7 + bobOffset).toFloat(), paint)
        canvas.drawRect(15f, (3 + bobOffset + earOffset).toFloat(), 18f, (7 + bobOffset).toFloat(), paint)

        // Rabo — animação varia por estado
        val tailWag = when (state) {
            CompanionState.CELEBRANDO, CompanionState.ENTUSIASMADO ->
                (Math.sin(t * Math.PI * 4) * 3).toInt() // agitado
            CompanionState.SEGUINDO ->
                (Math.sin(t * Math.PI * 2) * 1.5).toInt() // suave
            else -> 0
        }
        paint.color = Color.WHITE
        canvas.drawRect(17f, (14 + bobOffset + tailWag).toFloat(), 22f, (17 + bobOffset).toFloat(), paint)

        // Patas com mancha preta (Requisito 11.5)
        paint.color = Color.BLACK
        canvas.drawRect(7f, (19 + bobOffset).toFloat(), 10f, (23 + bobOffset).toFloat(), paint)
        canvas.drawRect(14f, (19 + bobOffset).toFloat(), 17f, (23 + bobOffset).toFloat(), paint)

        return bitmap
    }

    /**
     * Libera o cache de frames ao encerrar o Map (Requisito 20.1).
     */
    fun clearFrameCache() {
        frameCache.values.forEach { frames -> frames.forEach { it.recycle() } }
        frameCache.clear()
    }
}
