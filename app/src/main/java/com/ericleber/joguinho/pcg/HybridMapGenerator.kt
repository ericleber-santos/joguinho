package com.ericleber.joguinho.pcg

import com.ericleber.joguinho.core.Direction
import com.ericleber.joguinho.core.MazeData
import kotlin.random.Random

/**
 * Gerador Híbrido de Mapas (Perlin Noise + Pseudo-WFC).
 * 
 * Passo 1: Usa Perlin Noise (fBm) para gerar a topologia base (cavernas orgânicas).
 * Passo 2: Insere "ruínas" lógicas (padrões WFC-like) em áreas abertas.
 * Passo 3: Limpeza celular para remover paredes isoladas.
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
        val tiles = IntArray(width * height) { TILE_WALL }
        val perlin = PerlinNoise(seed)

        // Passo 1: Geração Base com Perlin Noise (fBm)
        // O noise scale determina a "frequência" das cavernas.
        // Quanto menor, mais longas e conectadas.
        val noiseScale = 0.08
        val threshold = 0.0 + (wallDensityTarget - 0.5) * 0.2 // Ajusta o threshold baseado na densidade
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                // fBm com 3 octaves para suavidade
                val noiseVal = perlin.fbm(x * noiseScale, y * noiseScale, octaves = 3)
                if (noiseVal > threshold) {
                    tiles[y * width + x] = TILE_FLOOR
                } else {
                    tiles[y * width + x] = TILE_WALL
                }
            }
        }

        // Passo 2: Limpeza Celular (Remove paredes ou chãos isolados de 1 bloco)
        cleanUpCellular(tiles, width, height)

        // Passo 3: Pseudo-WFC (Inserir Ruínas)
        insertRuins(tiles, width, height, seed)

        // Definir Ponto de Início e Saída baseados em distâncias
        val (startIndex, exitIndex, exitDir) = findStartAndExit(tiles, width, height)

        // Passo 4: Limpeza final (Flood Fill para bolsões isolados e preenchimento de becos sem saída)
        removeIsolatedPockets(tiles, width, height, startIndex)
        fillDeadEnds(tiles, width, height, startIndex, exitIndex)

        return MazeData(
            width = width,
            height = height,
            tiles = tiles,
            startIndex = startIndex,
            exitIndex = exitIndex,
            floorNumber = floorNumber,
            seed = seed,
            exitWallDirection = exitDir
        )
    }

    private fun cleanUpCellular(tiles: IntArray, width: Int, height: Int) {
        val newTiles = tiles.clone()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var wallNeighbors = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        if (tiles[(y + dy) * width + (x + dx)] == TILE_WALL) {
                            wallNeighbors++
                        }
                    }
                }
                
                // Se for chão e quase cercado, vira parede
                if (tiles[y * width + x] == TILE_FLOOR && wallNeighbors >= 6) {
                    newTiles[y * width + x] = TILE_WALL
                }
                // Se for parede solitária, vira chão
                else if (tiles[y * width + x] == TILE_WALL && wallNeighbors <= 1) {
                    newTiles[y * width + x] = TILE_FLOOR
                }
            }
        }
        System.arraycopy(newTiles, 0, tiles, 0, tiles.size)
    }

    private fun insertRuins(tiles: IntArray, width: Int, height: Int, seed: Long) {
        val numRuins = 2 + random.nextInt(3)
        for (i in 0 until numRuins) {
            // Tenta encontrar um local adequado (clareira)
            for (attempts in 0..10) {
                val rw = 5 + random.nextInt(4)
                val rh = 5 + random.nextInt(4)
                val rx = random.nextInt(2, width - rw - 2)
                val ry = random.nextInt(2, height - rh - 2)

                // Verifica se a área é majoritariamente chão
                var floorCount = 0
                for (y in ry until ry + rh) {
                    for (x in rx until rx + rw) {
                        if (tiles[y * width + x] == TILE_FLOOR) floorCount++
                    }
                }

                if (floorCount > (rw * rh) * 0.7) {
                    // Padrão Ruína: Paredes nas bordas com buracos
                    for (y in ry until ry + rh) {
                        for (x in rx until rx + rw) {
                            val isBorder = (x == rx || x == rx + rw - 1 || y == ry || y == ry + rh - 1)
                            if (isBorder) {
                                // 70% de chance de parede na borda
                                if (random.nextDouble() > 0.3) {
                                    tiles[y * width + x] = TILE_WALL
                                }
                            } else {
                                // Limpa o interior
                                tiles[y * width + x] = TILE_FLOOR
                            }
                        }
                    }
                    // Adiciona um pilar central ocasional
                    if (random.nextBoolean()) {
                        tiles[(ry + rh / 2) * width + (rx + rw / 2)] = TILE_WALL
                    }
                    break // Sucesso, vai para a próxima ruína
                }
            }
        }
    }

    private fun findStartAndExit(tiles: IntArray, width: Int, height: Int): Triple<Int, Int, Direction> {
        val floorIndices = mutableListOf<Int>()
        for (i in tiles.indices) {
            if (tiles[i] == TILE_FLOOR) floorIndices.add(i)
        }

        if (floorIndices.isEmpty()) {
            // Fallback extremo
            tiles[width + 1] = TILE_FLOOR
            tiles[tiles.size - width - 2] = TILE_FLOOR
            return Triple(width + 1, tiles.size - width - 2, Direction.NORTH)
        }

        // Ponto de início aleatório
        val startIndex = floorIndices[random.nextInt(floorIndices.size)]
        val startX = startIndex % width
        val startY = startIndex / width

        // Encontra o ponto mais distante alcançável (na prática, apenas o mais distante linearmente já que o PathValidator checará conectividade depois)
        var maxDist = -1
        var exitIndex = startIndex

        for (idx in floorIndices) {
            val tx = idx % width
            val ty = idx / width
            val dist = Math.abs(tx - startX) + Math.abs(ty - startY)
            if (dist > maxDist) {
                // Checa se tem alguma parede vizinha para ser uma saída válida
                var hasWallNeighbor = false
                for (dir in listOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
                    val nx = when(dir) { Direction.WEST -> tx - 1; Direction.EAST -> tx + 1; else -> tx }
                    val ny = when(dir) { Direction.NORTH -> ty - 1; Direction.SOUTH -> ty + 1; else -> ty }
                    if (nx in 0 until width && ny in 0 until height && tiles[ny * width + nx] == TILE_WALL) {
                        hasWallNeighbor = true
                        break
                    }
                }
                if (hasWallNeighbor) {
                    maxDist = dist
                    exitIndex = idx
                }
            }
        }
        
        // Define a direção da parede de saída
        val ex = exitIndex % width
        val ey = exitIndex / width
        var exitDir = Direction.NORTH
        val dirs = listOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST).shuffled(random)
        for (dir in dirs) {
            val nx = when(dir) { Direction.WEST -> ex - 1; Direction.EAST -> ex + 1; else -> ex }
            val ny = when(dir) { Direction.NORTH -> ey - 1; Direction.SOUTH -> ey + 1; else -> ey }
            if (nx in 0 until width && ny in 0 until height && tiles[ny * width + nx] == TILE_WALL) {
                exitDir = dir
                break
            }
        }

        return Triple(startIndex, exitIndex, exitDir)
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

    private fun fillDeadEnds(tiles: IntArray, width: Int, height: Int, startIndex: Int, exitIndex: Int) {
        var changed = true
        while (changed) {
            changed = false
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val idx = y * width + x
                    if (tiles[idx] == TILE_FLOOR && idx != startIndex && idx != exitIndex) {
                        var wallCount = 0
                        if (tiles[(y - 1) * width + x] == TILE_WALL) wallCount++
                        if (tiles[(y + 1) * width + x] == TILE_WALL) wallCount++
                        if (tiles[y * width + (x - 1)] == TILE_WALL) wallCount++
                        if (tiles[y * width + (x + 1)] == TILE_WALL) wallCount++

                        if (wallCount >= 3) {
                            tiles[idx] = TILE_WALL
                            changed = true
                        }
                    }
                }
            }
        }
    }
}
