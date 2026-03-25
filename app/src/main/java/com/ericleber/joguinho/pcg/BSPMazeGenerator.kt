package com.ericleber.joguinho.pcg

import com.ericleber.joguinho.core.MazeData
import kotlin.random.Random

/**
 * Gerador de labirintos usando Binary Space Partitioning (BSP).
 *
 * O BSP divide recursivamente o espaço em regiões menores, cria salas em cada
 * folha da árvore e conecta salas adjacentes com corredores. A conectividade
 * é garantida por construção — cada divisão cria exatamente um corredor de ligação.
 *
 * Requisitos: 2.1, 2.2, 2.3, 2.4, 2.5
 */
class BSPMazeGenerator(private val random: Random) {

    companion object {
        const val TILE_FLOOR = 0
        const val TILE_WALL = 1

        // Tamanho mínimo de uma folha BSP — menor = mais salas geradas
        // Com mapa 40x30 e MIN_LEAF_SIZE=8, o BSP gera 4-6 salas por Map
        private const val MIN_LEAF_SIZE = 8
    }

    /**
     * Nó da árvore BSP representando uma região retangular do mapa.
     */
    private data class BSPNode(
        val x: Int, val y: Int,
        val width: Int, val height: Int
    ) {
        var left: BSPNode? = null
        var right: BSPNode? = null

        // Sala gerada dentro desta folha
        var roomX: Int = 0
        var roomY: Int = 0
        var roomW: Int = 0
        var roomH: Int = 0

        val isLeaf: Boolean get() = left == null && right == null

        // Centro da sala para conexão de corredores
        val roomCenterX: Int get() = roomX + roomW / 2
        val roomCenterY: Int get() = roomY + roomH / 2
    }

    /**
     * Gera um MazeData com as dimensões fornecidas.
     *
     * @param width  Largura do mapa em tiles
     * @param height Altura do mapa em tiles
     * @param floorNumber Número do andar (para metadados)
     * @param seed    Seed usada na geração (para metadados)
     * @param wallDensityTarget Densidade alvo de paredes (0.0–1.0), usada para
     *                          ajustar o tamanho mínimo das salas e aumentar
     *                          a densidade de paredes conforme a faixa do Floor.
     */
    fun generate(
        width: Int,
        height: Int,
        floorNumber: Int,
        seed: Long,
        wallDensityTarget: Float = 0.5f
    ): MazeData {
        val tiles = IntArray(width * height) { TILE_WALL }

        val root = BSPNode(1, 1, width - 2, height - 2)
        splitNode(root, wallDensityTarget)
        createRooms(root, tiles, width, wallDensityTarget)
        connectRooms(root, tiles, width)

        val leaves = mutableListOf<BSPNode>()
        collectLeaves(root, leaves)
        
        // Embaralha as folhas para que o início não seja sempre na "primeira" sala
        val shuffledLeaves = leaves.shuffled(random)

        // Entrada: posição aleatória dentro de uma das salas iniciais
        val startLeaf = shuffledLeaves.first()
        val startX = startLeaf.roomX + 1 + (random.nextInt((startLeaf.roomW - 2).coerceAtLeast(1)))
        val startY = startLeaf.roomY + 1 + (random.nextInt((startLeaf.roomH - 2).coerceAtLeast(1)))
        val startIndex = startY * width + startX

        // Saída: Procuramos a sala mais distante possível do ponto de início
        var exitX = 0
        var exitY = 0
        var exitIndex = 0
        
        // Distância mínima alvo: 60% da diagonal do mapa para garantir que o jogador explore
        val targetMinDistance = (Math.sqrt((width * width + height * height).toDouble()) * 0.6).toInt()

        var found = false
        var exitWallDir: com.ericleber.joguinho.core.Direction? = null
        
        // Ordena as folhas pela distância do centro delas até o ponto de início (descendente)
        val sortedExitLeaves = shuffledLeaves.filter { it != startLeaf }.sortedByDescending { leaf ->
            Math.abs(leaf.roomCenterX - startX) + Math.abs(leaf.roomCenterY - startY)
        }

        for (leaf in sortedExitLeaves) {
            // Tenta as 4 paredes da sala da folha em ordem aleatória
            val wallOptions = listOf(
                com.ericleber.joguinho.core.Direction.NORTH,
                com.ericleber.joguinho.core.Direction.SOUTH,
                com.ericleber.joguinho.core.Direction.WEST,
                com.ericleber.joguinho.core.Direction.EAST
            ).shuffled(random)

            for (dir in wallOptions) {
                val tx: Int
                val ty: Int
                when (dir) {
                    com.ericleber.joguinho.core.Direction.NORTH -> { tx = leaf.roomX + leaf.roomW / 2; ty = leaf.roomY }
                    com.ericleber.joguinho.core.Direction.SOUTH -> { tx = leaf.roomX + leaf.roomW / 2; ty = leaf.roomY + leaf.roomH - 1 }
                    com.ericleber.joguinho.core.Direction.WEST -> { tx = leaf.roomX; ty = leaf.roomY + leaf.roomH / 2 }
                    com.ericleber.joguinho.core.Direction.EAST -> { tx = leaf.roomX + leaf.roomW - 1; ty = leaf.roomY + leaf.roomH / 2 }
                    else -> continue
                }

                val dist = Math.abs(tx - startX) + Math.abs(ty - startY)
                // Se a distância for maior ou igual ao alvo, aceitamos
                if (dist >= targetMinDistance) {
                    exitX = tx
                    exitY = ty
                    exitWallDir = dir
                    found = true
                    break
                }
            }
            if (found) break
        }

        // Fallback: Se não achou com a distância alvo, pega a parede mais distante possível entre todas as salas
        if (!found) {
            var maxDist = -1
            for (leaf in sortedExitLeaves) {
                val wallOptions = listOf(
                    com.ericleber.joguinho.core.Direction.NORTH,
                    com.ericleber.joguinho.core.Direction.SOUTH,
                    com.ericleber.joguinho.core.Direction.WEST,
                    com.ericleber.joguinho.core.Direction.EAST
                )
                for (dir in wallOptions) {
                    val tx: Int
                    val ty: Int
                    when (dir) {
                        com.ericleber.joguinho.core.Direction.NORTH -> { tx = leaf.roomX + leaf.roomW / 2; ty = leaf.roomY }
                        com.ericleber.joguinho.core.Direction.SOUTH -> { tx = leaf.roomX + leaf.roomW / 2; ty = leaf.roomY + leaf.roomH - 1 }
                        com.ericleber.joguinho.core.Direction.WEST -> { tx = leaf.roomX; ty = leaf.roomY + leaf.roomH / 2 }
                        com.ericleber.joguinho.core.Direction.EAST -> { tx = leaf.roomX + leaf.roomW - 1; ty = leaf.roomY + leaf.roomH / 2 }
                        else -> continue
                    }
                    val dist = Math.abs(tx - startX) + Math.abs(ty - startY)
                    if (dist > maxDist) {
                        maxDist = dist
                        exitX = tx
                        exitY = ty
                        exitWallDir = dir
                        found = true
                    }
                }
            }
        }
        exitIndex = exitY * width + exitX

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
            exitWallDirection = exitWallDir
        )
    }

    /**
     * Divide recursivamente um nó BSP até atingir o tamanho mínimo.
     * A densidade alvo influencia o tamanho mínimo das folhas:
     * densidades maiores → folhas menores → mais paredes relativas.
     */
    private fun splitNode(node: BSPNode, densityTarget: Float) {
        // Ajusta tamanho mínimo da folha com base na densidade alvo
        val minSize = when {
            densityTarget < 0.5f -> MIN_LEAF_SIZE + 2   // andares 1–20: salas maiores
            densityTarget < 0.7f -> MIN_LEAF_SIZE + 1   // andares 21–60
            else -> MIN_LEAF_SIZE                        // andares 61–120: salas menores
        }

        if (node.width < minSize * 2 && node.height < minSize * 2) return

        val splitHorizontal = when {
            node.width > node.height * 1.25f -> false   // muito largo → divide vertical
            node.height > node.width * 1.25f -> true    // muito alto → divide horizontal
            else -> random.nextBoolean()
        }

        if (splitHorizontal) {
            if (node.height < minSize * 2 + 1) return
            val splitY = random.nextInt(minSize, node.height - minSize)
            node.left = BSPNode(node.x, node.y, node.width, splitY)
            node.right = BSPNode(node.x, node.y + splitY, node.width, node.height - splitY)
        } else {
            if (node.width < minSize * 2 + 1) return
            val splitX = random.nextInt(minSize, node.width - minSize)
            node.left = BSPNode(node.x, node.y, splitX, node.height)
            node.right = BSPNode(node.x + splitX, node.y, node.width - splitX, node.height)
        }

        node.left?.let { splitNode(it, densityTarget) }
        node.right?.let { splitNode(it, densityTarget) }
    }

    /**
     * Cria salas nas folhas da árvore BSP e as esculpe no array de tiles.
     */
    private fun createRooms(node: BSPNode, tiles: IntArray, mapWidth: Int, densityTarget: Float) {
        if (node.isLeaf) {
            // Margem mínima de 1 tile entre a sala e a borda da folha
            val margin = 1
            val maxRoomW = node.width - margin * 2
            val maxRoomH = node.height - margin * 2

            // Tamanho mínimo da sala: 3x3
            if (maxRoomW < 3 || maxRoomH < 3) return

            // Salas maiores para preencher mais a tela
            val minRoomFraction = when {
                densityTarget < 0.5f -> 0.75f
                densityTarget < 0.7f -> 0.65f
                else -> 0.55f
            }

            val roomW = random.nextInt((maxRoomW * minRoomFraction).toInt().coerceAtLeast(3), maxRoomW + 1)
            val roomH = random.nextInt((maxRoomH * minRoomFraction).toInt().coerceAtLeast(3), maxRoomH + 1)
            val offsetW = maxRoomW - roomW
            val offsetH = maxRoomH - roomH
            val roomX = node.x + margin + if (offsetW > 0) random.nextInt(offsetW) else 0
            val roomY = node.y + margin + if (offsetH > 0) random.nextInt(offsetH) else 0

            node.roomX = roomX
            node.roomY = roomY
            node.roomW = roomW
            node.roomH = roomH

            // Esculpe a sala no mapa
            for (y in roomY until roomY + roomH) {
                for (x in roomX until roomX + roomW) {
                    tiles[y * mapWidth + x] = TILE_FLOOR
                }
            }
        } else {
            node.left?.let { createRooms(it, tiles, mapWidth, densityTarget) }
            node.right?.let { createRooms(it, tiles, mapWidth, densityTarget) }

            // Propaga centro da sala para nós internos (usado na conexão)
            val leftLeaf = getAnyLeaf(node.left)
            val rightLeaf = getAnyLeaf(node.right)
            if (leftLeaf != null) {
                node.roomX = leftLeaf.roomCenterX
                node.roomY = leftLeaf.roomCenterY
                node.roomW = 1
                node.roomH = 1
            }
            if (rightLeaf != null) {
                // Não sobrescreve — apenas usado para conectar
            }
        }
    }

    /**
     * Conecta recursivamente as salas irmãs com corredores em L.
     */
    private fun connectRooms(node: BSPNode, tiles: IntArray, mapWidth: Int) {
        if (node.isLeaf) return

        node.left?.let { connectRooms(it, tiles, mapWidth) }
        node.right?.let { connectRooms(it, tiles, mapWidth) }

        val leftLeaf = getAnyLeaf(node.left) ?: return
        val rightLeaf = getAnyLeaf(node.right) ?: return

        val x1 = leftLeaf.roomCenterX
        val y1 = leftLeaf.roomCenterY
        val x2 = rightLeaf.roomCenterX
        val y2 = rightLeaf.roomCenterY

        // Corredor em L: horizontal primeiro, depois vertical
        if (random.nextBoolean()) {
            carveHorizontalCorridor(tiles, mapWidth, x1, x2, y1)
            carveVerticalCorridor(tiles, mapWidth, y1, y2, x2)
        } else {
            carveVerticalCorridor(tiles, mapWidth, y1, y2, x1)
            carveHorizontalCorridor(tiles, mapWidth, x1, x2, y2)
        }
    }

    private fun carveHorizontalCorridor(tiles: IntArray, mapWidth: Int, x1: Int, x2: Int, y: Int) {
        val from = minOf(x1, x2)
        val to = maxOf(x1, x2)
        val comprimento = to - from

        // Corredor com 5 tiles de largura
        for (x in from..to) {
            for (dy in -2..2) {
                val ty = y + dy
                if (ty >= 0 && ty < tiles.size / mapWidth) {
                    tiles[ty * mapWidth + x] = TILE_FLOOR
                }
            }
        }

        // Obstáculo no meio do corredor — força desvio, evita linha reta A→B
        // Só insere se o corredor for longo o suficiente (>10 tiles)
        if (comprimento > 10) {
            val midX = from + comprimento / 2 + random.nextInt(comprimento / 4) - comprimento / 8
            val ladoObstaculo = if (random.nextBoolean()) 1 else -1
            // Bloco de parede de 3×3 deslocado para um lado do corredor
            for (bx in (midX - 1)..(midX + 1)) {
                for (by in (y + ladoObstaculo)..(y + ladoObstaculo * 2)) {
                    if (bx >= 0 && bx < mapWidth && by >= 0 && by < tiles.size / mapWidth) {
                        tiles[by * mapWidth + bx] = TILE_WALL
                    }
                }
            }
        }
    }

    private fun carveVerticalCorridor(tiles: IntArray, mapWidth: Int, y1: Int, y2: Int, x: Int) {
        val from = minOf(y1, y2)
        val to = maxOf(y1, y2)
        val comprimento = to - from

        // Corredor com 5 tiles de largura
        for (y in from..to) {
            for (dx in -2..2) {
                val tx = x + dx
                if (tx >= 0 && tx < mapWidth) {
                    tiles[y * mapWidth + tx] = TILE_FLOOR
                }
            }
        }

        // Obstáculo no meio do corredor vertical
        if (comprimento > 10) {
            val midY = from + comprimento / 2 + random.nextInt(comprimento / 4) - comprimento / 8
            val ladoObstaculo = if (random.nextBoolean()) 1 else -1
            for (by in (midY - 1)..(midY + 1)) {
                for (bx in (x + ladoObstaculo)..(x + ladoObstaculo * 2)) {
                    if (bx >= 0 && bx < mapWidth && by >= 0 && by < tiles.size / mapWidth) {
                        tiles[by * mapWidth + bx] = TILE_WALL
                    }
                }
            }
        }
    }

    private fun getAnyLeaf(node: BSPNode?): BSPNode? {
        if (node == null) return null
        if (node.isLeaf) return node
        return getAnyLeaf(node.left) ?: getAnyLeaf(node.right)
    }

    private fun collectLeaves(node: BSPNode, result: MutableList<BSPNode>) {
        if (node.isLeaf) {
            result.add(node)
        } else {
            node.left?.let { collectLeaves(it, result) }
            node.right?.let { collectLeaves(it, result) }
        }
    }
}
