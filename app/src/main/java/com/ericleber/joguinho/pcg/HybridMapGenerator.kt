package com.ericleber.joguinho.pcg

import com.ericleber.joguinho.core.Direction
import com.ericleber.joguinho.core.MazeData
import kotlin.random.Random

/**
 * Gerador de Terrenos Horizontal Side-Scrolling Procedural.
 * 
 * Substitui o antigo layout top-down por fatias horizontais lineares contínuas
 * (esquerda para direita), projetando plataformas sólidas de apoio, abismos transitáveis,
 * tetos protetores e conectividade início-fim 100% garantida por construção.
 *
 * Requisito: T-022
 */
class HybridMapGenerator(private val random: Random) {

    companion object {
        const val TILE_FLOOR = 0
        const val TILE_WALL = 1
    }

    fun generate(
        width: Int,
        height: Int,
        floorNumber: Int,
        seed: Long,
        wallDensityTarget: Float = 0.5f
    ): MazeData {
        // Inicializa o mapa como espaço vazio (ar)
        val tiles = IntArray(width * height) { TILE_FLOOR }

        // 1. Criar Tetos Sólidos na parte superior (linhas Y de 0 a 2)
        val tetoHeight = 3
        for (y in 0 until tetoHeight) {
            for (x in 0 until width) {
                tiles[y * width + x] = TILE_WALL
            }
        }

        // 2. Criar Piso Sólido Base e Relevo Dinâmico
        // A baseline representa a linha Y a partir da qual tudo para baixo (até Y = height - 1) é sólido.
        val baseFloorY = height - 5
        val minFloorY = height - 12
        val maxFloorY = height - 3

        val floorY = IntArray(width) { baseFloorY }
        var currentY = baseFloorY

        // Variação suave da linha de chão coluna por coluna
        for (x in 0 until width) {
            if (x < 5) {
                // Plataforma estável no início para spawn seguro
                floorY[x] = baseFloorY
            } else if (x >= width - 5) {
                // Plataforma estável no fim para saída segura
                floorY[x] = baseFloorY
            } else {
                // A cada 4 colunas, decide se altera suavemente a altura da plataforma
                if (x % 4 == 0) {
                    val change = random.nextInt(-2, 3) // Variação máxima de 2 blocos para ser escalável
                    currentY = (currentY + change).coerceIn(minFloorY, maxFloorY)
                }
                floorY[x] = currentY
            }
        }

        // Preenche com blocos de parede sólidos do floorY correspondente até a base inferior (height - 1)
        for (x in 0 until width) {
            val startY = floorY[x]
            for (y in startY until height) {
                tiles[y * width + x] = TILE_WALL
            }
        }

        // 3. Fechar laterais extremas por segurança
        for (y in 0 until height) {
            tiles[y * width + 0] = TILE_WALL
            tiles[y * width + (width - 1)] = TILE_WALL
        }

        // 4. Esculpir Abismos (Pits) na base do terreno
        // Abismos são vãos na baseline do chão.
        // Espaçamento mínimo e largura controlados por floorNumber.
        var nextPitX = 9 + random.nextInt(4)
        while (nextPitX < width - 9) {
            val pitWidth = when {
                floorNumber < 15 -> 2
                floorNumber < 40 -> random.nextInt(2, 4)
                else -> random.nextInt(3, 5) // Máximo 4 tiles para ser transitável com inércia
            }

            // Limpa o chão nas colunas do abismo
            for (px in nextPitX until nextPitX + pitWidth) {
                if (px < width - 6) {
                    for (y in tetoHeight until height) {
                        tiles[y * width + px] = TILE_FLOOR
                    }
                    floorY[px] = -1 // Sinaliza abismo profundo
                }
            }

            // Avança para o próximo abismo
            nextPitX += pitWidth + 9 + random.nextInt(6)
        }

        // 5. Inserir Plataformas Suspensas (Floating Platforms)
        // Adiciona plataformas no ar sobre abismos ou relevos baixos.
        var platX = 6
        while (platX < width - 7) {
            val platLen = random.nextInt(3, 7)
            
            // Calcula a altura média do chão correspondente abaixo
            var avgBaseY = 0
            var validBaseCount = 0
            for (px in platX until (platX + platLen).coerceAtMost(width - 6)) {
                if (floorY[px] != -1) {
                    avgBaseY += floorY[px]
                    validBaseCount++
                }
            }
            val refY = if (validBaseCount > 0) avgBaseY / validBaseCount else baseFloorY
            
            // Altura da plataforma: 4 a 5 blocos acima do chão, distante o suficiente do teto
            val platY = (refY - random.nextInt(4, 6)).coerceIn(tetoHeight + 3, height - 4)

            // Escreve a plataforma
            for (px in platX until (platX + platLen).coerceAtMost(width - 6)) {
                tiles[platY * width + px] = TILE_WALL
            }

            platX += platLen + random.nextInt(5, 9)
        }

        // 6. Inserir pequenos pilares verticais (obstáculos/degraus)
        for (x in 7 until width - 7 step 8) {
            if (floorY[x] != -1 && random.nextFloat() < 0.3f) {
                val pY = floorY[x]
                val pHeight = random.nextInt(1, 3)
                for (dy in 1..pHeight) {
                    tiles[(pY - dy) * width + x] = TILE_WALL
                }
            }
        }

        // 7. DefinirstartIndex e exitIndex de forma 100% estável e segura
        val startX = 3
        val startY = floorY[startX] - 1
        val startIndex = startY * width + startX

        val exitX = width - 4
        val exitY = floorY[exitX] - 1
        val exitIndex = exitY * width + exitX

        // Assegurar ar livre no início e na saída para o player spawnar livremente
        for (dy in 0..2) {
            tiles[startIndex - dy * width] = TILE_FLOOR
            tiles[exitIndex - dy * width] = TILE_FLOOR
        }

        // Limpeza final de bolsões inacessíveis a partir do startIndex
        removeIsolatedPockets(tiles, width, height, startIndex)

        return MazeData(
            width = width,
            height = height,
            tiles = tiles,
            startIndex = startIndex,
            exitIndex = exitIndex,
            floorNumber = floorNumber,
            seed = seed,
            exitWallDirection = Direction.EAST // Direção do portal apontada para a direita (Leste)
        )
    }

    private fun removeIsolatedPockets(tiles: IntArray, width: Int, height: Int, startIndex: Int) {
        val reachable = BooleanArray(width * height)
        val queue = java.util.LinkedList<Int>()
        
        if (tiles[startIndex] == TILE_FLOOR) {
            reachable[startIndex] = true
            queue.add(startIndex)
        }

        while (queue.isNotEmpty()) {
            val curr = queue.poll() ?: continue
            val x = curr % width
            val y = curr / width

            val neighbors = listOf(
                Pair(x - 1, y), Pair(x + 1, y),
                Pair(x, y - 1), Pair(x, y + 1)
            )
            for ((nx, ny) in neighbors) {
                if (nx in 0 until width && ny in 0 until height) {
                    val idx = ny * width + nx
                    if (!reachable[idx] && tiles[idx] == TILE_FLOOR) {
                        reachable[idx] = true
                        queue.add(idx)
                    }
                }
            }
        }

        for (i in tiles.indices) {
            if (tiles[i] == TILE_FLOOR && !reachable[i]) {
                tiles[i] = TILE_WALL
            }
        }
    }
}
