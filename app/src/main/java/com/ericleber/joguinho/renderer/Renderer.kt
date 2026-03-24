package com.ericleber.joguinho.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.ericleber.joguinho.biome.BIOME_PALETTES
import com.ericleber.joguinho.core.GameState
import com.ericleber.joguinho.core.MazeData

/**
 * Orquestrador de renderização isométrica.
 *
 * Coordena todos os sub-renderers e aplica culling de tiles (máximo 200 draw calls/frame).
 * Ordem de renderização: chão → paredes → decorativos → monsters → spike → hero → partículas → HUD.
 *
 * Suporta modo de alto contraste quando gameState.highContrastMode = true.
 *
 * Requisitos: 8.3, 8.7
 */
class Renderer(
    private val spriteCache: SpriteCache,
    private val tileRenderer: TileRenderer,
    private val characterRenderer: CharacterRenderer,
    private val particleSystem: ParticleSystem,
    private val hudRenderer: HudRenderer
) {

    var cameraX: Float = 0f
    var cameraY: Float = 0f
    var screenWidth: Int = 0
    var screenHeight: Int = 0
    var density: Float = 1f

    private val bgPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }

    // Tamanho base do tile em dp
    private val baseTileSize = 32

    // Frames de animação atuais
    private var heroAnimFrame: Int = 0
    private var spikeAnimFrame: Int = 0
    private var monsterAnimFrame: Int = 0
    private var frameCounter: Int = 0

    /**
     * Renderiza um frame completo do jogo.
     * @param canvas canvas de destino (obtido do SurfaceHolder)
     * @param gameState estado atual do jogo
     */
    fun render(canvas: Canvas, gameState: GameState) {
        val tileW = IsometricProjection.getTileWidth(baseTileSize, density)
        val tileH = IsometricProjection.getTileHeight(baseTileSize, density)

        val palette = BIOME_PALETTES[gameState.currentBiome]
            ?: BIOME_PALETTES.values.first()

        // Atualiza bioma no cache
        spriteCache.currentBiome = gameState.currentBiome.name

        // Avança frames de animação a cada 6 frames de render (~10fps de animação a 60fps)
        frameCounter++
        if (frameCounter % 6 == 0) {
            heroAnimFrame = (heroAnimFrame + 1) % 8
            spikeAnimFrame = (spikeAnimFrame + 1) % 12
            monsterAnimFrame = (monsterAnimFrame + 1) % 8
        }

        // Fundo
        val bgColor = if (gameState.highContrastMode) Color.BLACK else palette.backgroundColor
        bgPaint.color = bgColor
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), bgPaint)

        // Atualiza câmera para seguir o Hero
        val heroRawPos = IsometricProjection.worldToScreen(
            gameState.heroPosition.x, gameState.heroPosition.y, tileW, tileH
        )
        updateCamera(heroRawPos.x, heroRawPos.y)

        val mazeData = gameState.mazeData ?: return

        val visibleTiles = getVisibleTiles(mazeData, tileW, tileH)

        // 1. Renderiza tiles de chão
        for ((tx, ty) in visibleTiles) {
            val tileIndex = ty * mazeData.width + tx
            if (tileIndex < 0 || tileIndex >= mazeData.tiles.size) continue
            if (mazeData.tiles[tileIndex] == 1) continue  // pula paredes nesta passagem

            val screenPos = IsometricProjection.worldToScreen(tx, ty, tileW, tileH)
            val sx = screenPos.x + cameraX
            val sy = screenPos.y + cameraY

            val floorKey = "biome_${gameState.currentBiome.name}_floor"
            val floorBitmap = spriteCache.getOrCreate(floorKey) {
                tileRenderer.createTileBitmap(TileType.FLOOR, tileW.toInt(), tileH.toInt(), palette)
            }
            canvas.drawBitmap(floorBitmap, sx, sy, null)
        }

        // 2. Renderiza tiles de parede
        for ((tx, ty) in visibleTiles) {
            val tileIndex = ty * mazeData.width + tx
            if (tileIndex < 0 || tileIndex >= mazeData.tiles.size) continue
            if (mazeData.tiles[tileIndex] != 1) continue  // pula chão nesta passagem

            val screenPos = IsometricProjection.worldToScreen(tx, ty, tileW, tileH)
            val sx = screenPos.x + cameraX
            val sy = screenPos.y + cameraY

            val wallKey = "biome_${gameState.currentBiome.name}_wall"
            val wallBitmap = spriteCache.getOrCreate(wallKey) {
                tileRenderer.createTileBitmap(TileType.WALL, tileW.toInt(), (tileH * 1.8f).toInt(), palette)
            }
            canvas.drawBitmap(wallBitmap, sx, sy, null)
        }

        // 3. Renderiza tiles decorativos (a cada 7 tiles de chão, baseado em seed)
        for ((tx, ty) in visibleTiles) {
            val tileIndex = ty * mazeData.width + tx
            if (tileIndex < 0 || tileIndex >= mazeData.tiles.size) continue
            if (mazeData.tiles[tileIndex] != 0) continue
            // Decoração determinística baseada na posição
            val decorSeed = (tx * 31 + ty * 17 + mazeData.seed.toInt()) % 7
            if (decorSeed != 0) continue

            val variant = (tx + ty) % 4
            val screenPos = IsometricProjection.worldToScreen(tx, ty, tileW, tileH)
            val sx = screenPos.x + cameraX
            val sy = screenPos.y + cameraY

            val decorKey = "biome_${gameState.currentBiome.name}_decor_$variant"
            val decorBitmap = spriteCache.getOrCreate(decorKey) {
                tileRenderer.createTileBitmap(
                    when (variant) {
                        0 -> TileType.DECORATIVE_0
                        1 -> TileType.DECORATIVE_1
                        2 -> TileType.DECORATIVE_2
                        else -> TileType.DECORATIVE_3
                    },
                    tileW.toInt(), tileH.toInt(), palette
                )
            }
            canvas.drawBitmap(decorBitmap, sx, sy, null)
        }

        // 4. Renderiza monsters
        for (monster in gameState.monsters) {
            if (!monster.isActive) continue
            val screenPos = IsometricProjection.worldToScreen(
                monster.position.x, monster.position.y, tileW, tileH
            )
            val sx = screenPos.x + cameraX
            val sy = screenPos.y + cameraY

            // Aparência procedural baseada no ID do monster
            val seed = monster.id.hashCode()
            val appearance = MonsterAppearance(
                bodyColor = if (gameState.highContrastMode) Color.rgb(255, 50, 50)
                            else Color.rgb(
                                150 + (seed and 0xFF) % 100,
                                50 + (seed shr 8 and 0xFF) % 80,
                                50 + (seed shr 16 and 0xFF) % 80
                            ),
                eyeColor = if (gameState.highContrastMode) Color.YELLOW
                           else Color.rgb(255, 200 + (seed and 0x3F), 0),
                size = 0.7f + ((seed and 0xF) / 15f) * 0.6f,
                shapeVariant = (seed and 0x3),
                animVariant = (seed shr 4 and 0x3)
            )
            characterRenderer.renderMonster(canvas, sx, sy, appearance, monsterAnimFrame, tileW, tileH)
        }

        // 5. Renderiza Spike
        val spikeScreenPos = IsometricProjection.worldToScreen(
            gameState.spikePosition.x, gameState.spikePosition.y, tileW, tileH
        )
        val spikeState = gameState.spikeCompanionState
        characterRenderer.renderSpike(
            canvas,
            spikeScreenPos.x + cameraX,
            spikeScreenPos.y + cameraY,
            spikeState,
            spikeAnimFrame,
            tileW,
            tileH
        )

        // 6. Renderiza Hero
        val heroScreenPos = IsometricProjection.worldToScreen(
            gameState.heroPosition.x, gameState.heroPosition.y, tileW, tileH
        )
        val heroDirection = mapDirectionToHeroDirection(gameState.heroDirection)
        val heroAnimState = when {
            gameState.heroIsSlowedDown -> HeroAnimState.SLOWDOWN
            else -> HeroAnimState.WALK
        }
        characterRenderer.renderHero(
            canvas,
            heroScreenPos.x + cameraX,
            heroScreenPos.y + cameraY,
            heroDirection,
            heroAnimState,
            heroAnimFrame,
            tileW,
            tileH
        )

        // 7. Renderiza partículas
        particleSystem.render(canvas)

        // 8. Renderiza HUD
        val screenWidthDp = screenWidth / density
        hudRenderer.render(canvas, gameState, screenWidthDp)
    }

    /**
     * Retorna a lista de tiles visíveis na viewport atual.
     * Limita a no máximo 200 tiles (culling).
     *
     * @param mazeData dados do mapa
     * @param tileW largura do tile em pixels
     * @param tileH altura do tile em pixels
     * @return lista de pares (x, y) de tiles visíveis
     */
    fun getVisibleTiles(mazeData: MazeData, tileW: Float, tileH: Float): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()

        // Margem extra para tiles parcialmente visíveis
        val margin = 2

        // Converte os cantos da tela para coordenadas de mundo
        val topLeft = IsometricProjection.screenToWorld(
            0f, 0f, tileW, tileH, cameraX, cameraY
        )
        val bottomRight = IsometricProjection.screenToWorld(
            screenWidth.toFloat(), screenHeight.toFloat(), tileW, tileH, cameraX, cameraY
        )

        val minX = (topLeft.first - margin).coerceAtLeast(0)
        val maxX = (bottomRight.first + margin).coerceAtMost(mazeData.width - 1)
        val minY = (topLeft.second - margin).coerceAtLeast(0)
        val maxY = (bottomRight.second + margin).coerceAtMost(mazeData.height - 1)

        // Itera em ordem isométrica (diagonal) para renderização correta de profundidade
        for (sum in (minX + minY)..(maxX + maxY)) {
            for (tx in minX..maxX) {
                val ty = sum - tx
                if (ty < minY || ty > maxY) continue
                result.add(Pair(tx, ty))
                if (result.size >= 200) return result  // culling: máximo 200 tiles
            }
        }

        return result
    }

    /**
     * Atualiza a posição da câmera para seguir o Hero.
     * Centraliza o Hero na tela.
     *
     * @param heroScreenX posição X do Hero na tela (antes do offset de câmera)
     * @param heroScreenY posição Y do Hero na tela (antes do offset de câmera)
     */
    fun updateCamera(heroScreenX: Float, heroScreenY: Float) {
        cameraX = screenWidth / 2f - heroScreenX
        cameraY = screenHeight / 2f - heroScreenY
    }

    /**
     * Atualiza dimensões da tela e densidade.
     * Deve ser chamado em surfaceChanged.
     */
    fun onSurfaceChanged(width: Int, height: Int, density: Float) {
        screenWidth = width
        screenHeight = height
        this.density = density
    }

    /**
     * Libera memória de Bitmaps ao encerrar um Map.
     * Deve ser chamado pelo GameLoop/GameState ao transicionar entre Maps.
     * Requisito 20.1
     */
    fun onMapEnd() {
        spriteCache.recycleAll()
    }

    /**
     * Evicta sprites não essenciais (de biomas inativos) para liberar memória.
     * Deve ser chamado em resposta a onTrimMemory com nível RUNNING_LOW ou superior.
     * Requisito 20.6
     */
    fun evictNonEssentialSprites() {
        spriteCache.evictNonEssential()
    }

    /**
     * Libera todos os recursos: recicla bitmaps e limpa o cache.
     * Deve ser chamado em onDestroy.
     */
    fun release() {
        spriteCache.clear()
        particleSystem.clear()
    }

    // -------------------------------------------------------------------------
    // Utilitários
    // -------------------------------------------------------------------------

    private fun mapDirectionToHeroDirection(direction: com.ericleber.joguinho.core.Direction): HeroDirection {
        return when (direction) {
            com.ericleber.joguinho.core.Direction.NORTH -> HeroDirection.N
            com.ericleber.joguinho.core.Direction.NORTH_EAST -> HeroDirection.NE
            com.ericleber.joguinho.core.Direction.EAST -> HeroDirection.E
            com.ericleber.joguinho.core.Direction.SOUTH_EAST -> HeroDirection.SE
            com.ericleber.joguinho.core.Direction.SOUTH -> HeroDirection.S
            com.ericleber.joguinho.core.Direction.SOUTH_WEST -> HeroDirection.SW
            com.ericleber.joguinho.core.Direction.WEST -> HeroDirection.W
            com.ericleber.joguinho.core.Direction.NORTH_WEST -> HeroDirection.NW
        }
    }
}

/**
 * Extensões do GameState para o Renderer.
 * (Mantidas para compatibilidade — os campos reais estão em GameState)
 */
