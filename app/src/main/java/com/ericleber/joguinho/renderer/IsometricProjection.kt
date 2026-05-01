package com.ericleber.joguinho.renderer

import android.graphics.PointF

/**
 * Projeção top-down: converte coordenadas de mundo (grid 2D) para coordenadas de tela.
 *
 * Projeção simples 1:1 — tile (tx, ty) ocupa o retângulo
 * [tx*tileW, ty*tileH, (tx+1)*tileW, (ty+1)*tileH] na tela.
 *
 * Mantém o nome IsometricProjection para compatibilidade com o resto do código.
 */
object IsometricProjection {

    /**
     * Converte coordenadas de mundo para coordenadas de tela (canto superior esquerdo do tile).
     */
    fun worldToScreen(worldX: Float, worldY: Float, tileW: Float, tileH: Float): PointF {
        return PointF(worldX * tileW, worldY * tileH)
    }

    /**
     * Projeção inversa: converte coordenadas de tela para coordenadas de mundo.
     */
    fun screenToWorld(
        screenX: Float, screenY: Float,
        tileW: Float, tileH: Float,
        offsetX: Float, offsetY: Float
    ): Pair<Int, Int> {
        val worldX = ((screenX - offsetX) / tileW).toInt()
        val worldY = ((screenY - offsetY) / tileH).toInt()
        return Pair(worldX, worldY)
    }

    fun getTileWidth(baseTileSize: Int, density: Float): Float = baseTileSize * density
    fun getTileHeight(baseTileSize: Int, density: Float): Float = baseTileSize * density
}
