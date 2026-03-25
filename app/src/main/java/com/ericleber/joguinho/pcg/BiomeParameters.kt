package com.ericleber.joguinho.pcg

/**
 * Parâmetros de geração procedural por faixa de Floor.
 *
 * Define as densidades de paredes e dimensões de mapa para cada faixa,
 * conforme Requisitos 2.3, 2.4 e 2.5.
 */
data class BiomeParameters(
    /** Densidade mínima de paredes (0.0–1.0) */
    val wallDensityMin: Float,
    /** Densidade máxima de paredes (0.0–1.0) */
    val wallDensityMax: Float,
    /** Largura do mapa em tiles */
    val mapWidth: Int,
    /** Altura do mapa em tiles */
    val mapHeight: Int
) {
    /**
     * Retorna a densidade alvo interpolada linearmente dentro da faixa,
     * com base na posição do Floor dentro da faixa (0.0 = início, 1.0 = fim).
     */
    fun targetDensity(progress: Float): Float =
        wallDensityMin + (wallDensityMax - wallDensityMin) * progress.coerceIn(0f, 1f)
}

object BiomeParametersProvider {

    /**
     * Retorna os parâmetros de geração para o Floor informado.
     *
     * Faixas conforme especificação:
     * - Floors  1–20:  densidade 40%–55%  (Requisito 2.3)
     * - Floors 21–60:  densidade 55%–70%  (Requisito 2.4)
     * - Floors 61–120: densidade 70%–85%  (Requisito 2.5)
     */
    fun forFloor(floorNumber: Int): BiomeParameters = when (floorNumber) {
        in 1..20   -> BiomeParameters(
            wallDensityMin = 0.40f,
            wallDensityMax = 0.55f,
            mapWidth  = 40,   // maior para acomodar 3+ salas com corredores largos
            mapHeight = 30
        )
        in 21..60  -> BiomeParameters(
            wallDensityMin = 0.55f,
            wallDensityMax = 0.70f,
            mapWidth  = 45,
            mapHeight = 35
        )
        else       -> BiomeParameters(  // 61–120
            wallDensityMin = 0.70f,
            wallDensityMax = 0.85f,
            mapWidth  = 50,
            mapHeight = 40
        )
    }

    /**
     * Calcula o progresso do Floor dentro de sua faixa (0.0–1.0).
     * Usado para interpolar a densidade alvo ao longo da faixa.
     */
    fun floorProgress(floorNumber: Int): Float = when (floorNumber) {
        in 1..20   -> (floorNumber - 1) / 19f
        in 21..60  -> (floorNumber - 21) / 39f
        else       -> (floorNumber - 61) / 59f
    }
}
