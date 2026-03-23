package com.ericleber.joguinho.character

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Implementação concreta do Hero — personagem controlado pelo Player.
 * Sprites gerados programaticamente via Canvas (Requisito 17.1).
 * Requisitos: 24.1, 24.2
 */
class Hero(
    override val id: String = "hero",
    override val baseSpeed: Float = 4f,
    override val skinId: String = "default"
) : PlayableCharacter() {

    // Cache de frames por estado para evitar geração repetida (Requisito 17.7)
    private val frameCache = HashMap<CharacterState, List<Bitmap>>()

    override fun getAnimationFrames(state: CharacterState): List<Bitmap> {
        return frameCache.getOrPut(state) { generateFrames(state) }
    }

    /**
     * Gera frames de animação via Canvas.
     * WALKING/RUNNING: 8 frames (Requisito 4.3, 17.2)
     * IDLE: 4 frames
     * SLOWDOWN: 8 frames com visual de lentidão (Requisito 4.8)
     */
    private fun generateFrames(state: CharacterState): List<Bitmap> {
        val frameCount = when (state) {
            CharacterState.IDLE -> 4
            CharacterState.CELEBRATING -> 8
            else -> 8
        }
        return List(frameCount) { frameIndex ->
            generateHeroFrame(state, frameIndex, frameCount)
        }
    }

    private fun generateHeroFrame(state: CharacterState, frameIndex: Int, totalFrames: Int): Bitmap {
        val size = 32
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
        }

        val t = frameIndex.toFloat() / totalFrames
        val bobOffset = if (state == CharacterState.WALKING || state == CharacterState.RUNNING) {
            (Math.sin(t * Math.PI * 2) * 1.5).toInt()
        } else 0

        // Cor base do Hero — azul escuro (aventureiro)
        val bodyColor = when (state) {
            CharacterState.SLOWDOWN -> Color.rgb(100, 100, 200) // azulado/lento
            CharacterState.CELEBRATING -> Color.rgb(255, 200, 50) // dourado
            else -> Color.rgb(60, 100, 180)
        }

        // Corpo isométrico simplificado (pixel art)
        paint.color = bodyColor
        canvas.drawRect(10f, (12 + bobOffset).toFloat(), 22f, (26 + bobOffset).toFloat(), paint)

        // Cabeça
        paint.color = Color.rgb(220, 180, 140) // tom de pele
        canvas.drawRect(11f, (6 + bobOffset).toFloat(), 21f, (13 + bobOffset).toFloat(), paint)

        // Chapéu de aventureiro
        paint.color = Color.rgb(100, 70, 30)
        canvas.drawRect(9f, (4 + bobOffset).toFloat(), 23f, (8 + bobOffset).toFloat(), paint)

        // Pernas com animação de caminhada
        paint.color = Color.rgb(40, 60, 120)
        val legOffset = if (state == CharacterState.WALKING || state == CharacterState.RUNNING) {
            (Math.sin(t * Math.PI * 2) * 2).toInt()
        } else 0
        canvas.drawRect(11f, (26 + bobOffset).toFloat(), 15f, (32 + bobOffset + legOffset).toFloat(), paint)
        canvas.drawRect(17f, (26 + bobOffset).toFloat(), 21f, (32 + bobOffset - legOffset).toFloat(), paint)

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
