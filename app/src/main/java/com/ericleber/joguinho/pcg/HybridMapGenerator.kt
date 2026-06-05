package com.ericleber.joguinho.pcg

import com.ericleber.joguinho.core.Direction
import com.ericleber.joguinho.core.MazeData
import kotlin.random.Random

/**
 * Gerador de Arenas de Sobrevivência (Horda).
 *
 * Produz mapas abertos no estilo "survivor arena":
 * - Apenas bordas de parede (teto 1 tile, chão 1 tile, laterais)
 * - Sem relevo variável de chão (piso reto)
 * - Plataformas finas e raras (1 tile de espessura, comprimento 2-4)
 * - Sem espinhos/pilares na geração base (armadilhas vêm do EntityPlacer)
 * - ExitIndex posicionado no centro do mapa
 *
 * Regra de ouro: o jogador precisa de ~70%+ de área aberta para correr/dodjar.
 */
class HybridMapGenerator(private val random: Random) {

    companion object {
        const val TILE_FLOOR = 0
        const val TILE_WALL = 1
        const val TILE_TRAP_SPIKES = 2
        const val TILE_TRAP_LAVA = 3
        const val TILE_TRAP_PIRANHA_WATER = 4

        /** Número de plataformas flutuantes por arena */
        private const val MAX_PLATFORMS = 4
        /** Comprimento mínimo de uma plataforma (tiles) */
        private const val PLAT_MIN_LEN = 2
        /** Comprimento máximo de uma plataforma (tiles) */
        private const val PLAT_MAX_LEN = 4
    }

    fun generate(
        width: Int,
        height: Int,
        floorNumber: Int,
        seed: Long,
        wallDensityTarget: Float = 0.5f
    ): MazeData {
        // Inicializa o mapa inteiro como chão vazio
        val tiles = IntArray(width * height) { TILE_FLOOR }

        // 1. Teto (borda superior) — só 1 tile de espessura
        for (x in 0 until width) {
            tiles[0 * width + x] = TILE_WALL
        }

        // 2. Chão (borda inferior) — só 1 tile de espessura
        for (x in 0 until width) {
            tiles[(height - 1) * width + x] = TILE_WALL
        }

        // 3. Paredes laterais
        for (y in 0 until height) {
            tiles[y * width + 0] = TILE_WALL
            tiles[y * width + (width - 1)] = TILE_WALL
        }

        // 4. Plataformas flutuantes finas (1 tile de espessura)
        // Posicionadas em alturas variadas para servir de pontos táticos (pulo/perspectiva)
        val numPlatforms = random.nextInt(2, MAX_PLATFORMS + 1)
        val usedXPositions = mutableSetOf<Int>()

        for (i in 0 until numPlatforms) {
            val platLen = random.nextInt(PLAT_MIN_LEN, PLAT_MAX_LEN + 1)
            val platX = generateUniqueX(width, platLen, usedXPositions)
            if (platX < 0) continue

            // Altura: entre 30% e 70% da arena (evita teto e chão)
            val minY = (height * 0.3f).toInt().coerceAtLeast(2)
            val maxY = (height * 0.7f).toInt().coerceAtMost(height - 3)
            val platY = random.nextInt(minY, maxY + 1)

            for (px in platX until (platX + platLen).coerceAtMost(width - 1)) {
                tiles[platY * width + px] = TILE_WALL
            }

            // Marca as posições X usadas para evitar sobreposição
            for (px in (platX - 2) until (platX + platLen + 2)) {
                usedXPositions.add(px)
            }

            // 4a. Pequena chance de uma segunda fileira (plataforma de 2 tiles de altura, tipo "degrau")
            if (random.nextFloat() < 0.25f && platY - 1 >= 2) {
                val extraY = platY - 1
                for (px in platX until (platX + platLen).coerceAtMost(width - 1)) {
                    tiles[extraY * width + px] = TILE_WALL
                }
            }
        }

        // 5. Start e Exit no centro da arena
        val centerX = width / 2
        val centerY = height / 2

        val startIndex = centerY * width + centerX
        val exitIndex = startIndex // mesmo lugar — o portal aparece após sobreviver

        // Garante que o centro está livre
        tiles[startIndex] = TILE_FLOOR

        return MazeData(
            width = width,
            height = height,
            tiles = tiles,
            startIndex = startIndex,
            exitIndex = exitIndex,
            floorNumber = floorNumber,
            seed = seed,
            exitWallDirection = Direction.EAST
        )
    }

    /**
     * Encontra uma posição X que não conflite com plataformas existentes.
     * Retorna -1 se não encontrar espaço.
     */
    private fun generateUniqueX(width: Int, platLen: Int, used: MutableSet<Int>): Int {
        for (attempt in 0..20) {
            val x = random.nextInt(3, width - 3 - platLen)
            var conflict = false
            for (px in x until (x + platLen)) {
                if (px in used) {
                    conflict = true
                    break
                }
            }
            if (!conflict) return x
        }
        return -1
    }
}
