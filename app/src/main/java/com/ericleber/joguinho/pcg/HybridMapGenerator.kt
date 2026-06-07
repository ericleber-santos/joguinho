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

        // 4. Plataformas flutuantes — regras de geração:
        //    - Baixa: 1 tile acima do chão (alcançável com pulo simples)
        //    - Alta: 3 tiles acima do chão (só existe se houver uma baixa embaixo)
        //    - Máx 2 baixas, máx 1 alta
        val groundY = height - 2
        val usedXPositions = mutableSetOf<Int>()

        // 4a. Plataformas baixas (0 a 2)
        val numLowPlats = random.nextInt(0, 3) // 0, 1 ou 2
        val lowPlats = mutableListOf<Pair<Int, Int>>() // (platX, platLen)
        for (i in 0 until numLowPlats) {
            val platLen = random.nextInt(PLAT_MIN_LEN, PLAT_MAX_LEN + 1)
            val platX = generateUniqueX(width, platLen, usedXPositions)
            if (platX < 0) continue
            val platY = groundY - 1 // 1 tile acima do chão
            for (px in platX until (platX + platLen).coerceAtMost(width - 1)) {
                tiles[platY * width + px] = TILE_WALL
            }
            lowPlats.add(platX to platLen)
            for (px in (platX - 2) until (platX + platLen + 2)) {
                usedXPositions.add(px)
            }
        }

        // 4b. Plataforma alta (0 ou 1) — só se existir pelo menos 1 baixa
        if (lowPlats.isNotEmpty() && random.nextFloat() < 0.5f) {
            val (platX, platLen) = lowPlats.random(random)
            val platY = groundY - 3 // 3 tiles acima do chão
            // Posiciona acima da plataforma baixa escolhida
            val highX = (platX + platLen / 2).coerceIn(1, width - 2 - 1)
            for (px in highX until (highX + 1).coerceAtMost(width - 1)) {
                tiles[platY * width + px] = TILE_WALL
            }
            // Degraus para alcançar a plataforma alta (alturaPlat = 3)
            val centroX = highX
            for (d in 1..2) {
                val dy = groundY - d
                if (dy > platY) {
                    tiles[dy * width + centroX] = TILE_WALL
                }
            }
        }
        val startX = 2
        val exitX = width - 3

        val startIndex = groundY * width + startX
        val exitIndex = groundY * width + exitX

        // Garante que estão livres
        tiles[startIndex] = TILE_FLOOR
        tiles[exitIndex] = TILE_FLOOR

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
