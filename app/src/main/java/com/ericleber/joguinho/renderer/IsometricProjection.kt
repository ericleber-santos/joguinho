package com.ericleber.joguinho.renderer

import android.graphics.PointF

/**
 * Projeção isométrica: converte coordenadas de mundo (grid 2D) para coordenadas de tela.
 *
 * Fórmula padrão de projeção isométrica 2:1:
 *   screenX = (worldX - worldY) * (tileW / 2)
 *   screenY = (worldX + worldY) * (tileH / 2)
 *
 * Requisitos: 8.2, 13.5
 */
object IsometricProjection {

    /**
     * Converte coordenadas de mundo para coordenadas de tela.
     * @param worldX coluna no grid do mapa
     * @param worldY linha no grid do mapa
     * @param tileW largura do tile em pixels
     * @param tileH altura do tile em pixels
     * @return ponto na tela (sem offset de câmera)
     */
    fun worldToScreen(worldX: Int, worldY: Int, tileW: Float, tileH: Float): PointF {
        val screenX = (worldX - worldY) * (tileW / 2f)
        val screenY = (worldX + worldY) * (tileH / 2f)
        return PointF(screenX, screenY)
    }

    /**
     * Projeção inversa: converte coordenadas de tela para coordenadas de mundo.
     * Usado para mapear toque do usuário para célula do grid.
     *
     * @param screenX posição X na tela
     * @param screenY posição Y na tela
     * @param tileW largura do tile em pixels
     * @param tileH altura do tile em pixels
     * @param offsetX deslocamento horizontal da câmera
     * @param offsetY deslocamento vertical da câmera
     * @return par (worldX, worldY) no grid do mapa
     */
    fun screenToWorld(
        screenX: Float,
        screenY: Float,
        tileW: Float,
        tileH: Float,
        offsetX: Float,
        offsetY: Float
    ): Pair<Int, Int> {
        val relX = screenX - offsetX
        val relY = screenY - offsetY
        // Inversa da projeção isométrica
        val worldX = (relX / (tileW / 2f) + relY / (tileH / 2f)) / 2f
        val worldY = (relY / (tileH / 2f) - relX / (tileW / 2f)) / 2f
        return Pair(worldX.toInt(), worldY.toInt())
    }

    /**
     * Calcula a largura do tile escalada pela densidade da tela.
     *
     * Fator 1.0f (sem multiplicador extra) para que o tile base em dp
     * corresponda diretamente a pixels — evita tiles gigantes em telas
     * de alta densidade (ex: ASUS X00TDB density=2.0 → 32dp = 64px, não 128px).
     *
     * @param baseTileSize tamanho base do tile em dp (ex: 32)
     * @param density densidade da tela (DisplayMetrics.density)
     * @return largura do tile em pixels
     */
    fun getTileWidth(baseTileSize: Int, density: Float): Float {
        return baseTileSize * density  // 1:1 dp→px, sem multiplicador extra
    }

    /**
     * Calcula a altura do tile escalada pela densidade da tela.
     * A altura isométrica é metade da largura para proporção 2:1.
     * @param baseTileSize tamanho base do tile em dp
     * @param density densidade da tela
     * @return altura do tile em pixels
     */
    fun getTileHeight(baseTileSize: Int, density: Float): Float {
        return getTileWidth(baseTileSize, density) / 2f
    }
}
