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

        // Tamanho mínimo de uma folha BSP (sala + margem)
        private const val MIN_LEAF_SIZE = 7
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

        // Raiz da árvore BSP cobre todo o mapa (com borda de 1 tile)
        val root = BSPNode(1, 1, width - 2, height - 2)
        splitNode(root, wallDensityTarget)
        createRooms(root, tiles, width, wallDensityTarget)
        connectRooms(root, tiles, width)

        // Coleta todas as salas folha para posicionar entrada e saída
        val leaves = mutableListOf<BSPNode>()
        collectLeaves(root, leaves)

        // Entrada no canto superior-esquerdo da primeira sala,
        // saída no canto inferior-direito da última sala
        val startLeaf = leaves.first()
        val exitLeaf = leaves.last()

        val startIndex = (startLeaf.roomY + 1) * width + (startLeaf.roomX + 1)
        val exitIndex = (exitLeaf.roomY + exitLeaf.roomH - 2) * width + (exitLeaf.roomX + exitLeaf.roomW - 2)

        // Garante que entrada e saída são tiles de chão
        tiles[startIndex] = TILE_FLOOR
        tiles[exitIndex] = TILE_FLOOR

        return MazeData(
            width = width,
            height = height,
            tiles = tiles,
            startIndex = startIndex,
            exitIndex = exitIndex,
            floorNumber = floorNumber,
            seed = seed
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

            // Salas menores em densidades maiores (mais paredes ao redor)
            val minRoomFraction = when {
                densityTarget < 0.5f -> 0.6f
                densityTarget < 0.7f -> 0.5f
                else -> 0.4f
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
        for (x in from..to) {
            tiles[y * mapWidth + x] = TILE_FLOOR
        }
    }

    private fun carveVerticalCorridor(tiles: IntArray, mapWidth: Int, y1: Int, y2: Int, x: Int) {
        val from = minOf(y1, y2)
        val to = maxOf(y1, y2)
        for (y in from..to) {
            tiles[y * mapWidth + x] = TILE_FLOOR
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
