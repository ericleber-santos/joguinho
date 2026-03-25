package com.ericleber.joguinho.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.ericleber.joguinho.biome.BIOME_PALETTES
import com.ericleber.joguinho.core.GameState
import com.ericleber.joguinho.core.MazeData
import kotlin.math.sin

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

    private val lightingSystem = LightingSystem()

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

    // Paint para a placa de saída animada
    private val placaPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }

    // Tamanho base do tile em dp — calculado dinamicamente em onSurfaceChanged
    // para que o mapa sempre preencha a tela inteira sem fundo preto
    private var tileWDinamico: Float = 28f
    private var tileHDinamico: Float = 14f

    /**
     * Recalcula o tamanho do tile para que o mapa preencha a tela.
     * Chamado quando o mapa muda ou a tela é redimensionada.
     * Fórmula: tileW = min(screenW, screenH*2) / (mapW + mapH) * 2
     */
    // Altura da área de jogo (retângulo A) — 80% da tela
    // O retângulo B (20% inferior) é reservado para o HUD
    private val fracaoAreaJogo = 0.80f

    /**
     * Recalcula o tile e a câmera para que o mapa preencha exatamente o retângulo A.
     *
     * Em projeção isométrica 2:1, os 4 extremos do mapa W×H são:
     *   Topo    = tile(0,0)         → screenX=0,          screenY=0
     *   Direita = tile(W-1,0)       → screenX=(W-1)*tileW/2, screenY=(W-1)*tileH/2
     *   Esquerda= tile(0,H-1)       → screenX=-(H-1)*tileW/2, screenY=(H-1)*tileH/2
     *   Fundo   = tile(W-1,H-1)     → screenX=0,          screenY=(W+H-2)*tileH/2
     *
     * Largura total do mapa na tela = (W+H-2)*tileW/2  ≈ (W+H)*tileW/2
     * Altura total do mapa na tela  = (W+H-2)*tileH/2  ≈ (W+H)*tileW/4
     *
     * Para preencher screenWidth:  tileW = screenWidth*2/(W+H)
     * Para preencher alturaA:      tileW = alturaA*4/(W+H)
     * Usa o MENOR para que o mapa caiba nos dois eixos sem cortar.
     */
    // Tamanho mínimo do tile em px para o personagem ser visível
    private val tileSizeMinimo = 78f

    // Posição do hero para scroll da câmera
    private var heroWorldX: Int = 0
    private var heroWorldY: Int = 0

    fun recalcularTile(mapWidth: Int, mapHeight: Int) {
        if (screenWidth <= 0 || screenHeight <= 0) return
        val alturaA = screenHeight * fracaoAreaJogo

        // Calcula tile para caber o mapa inteiro
        val tileWPorLargura = screenWidth.toFloat() / mapWidth
        val tileHPorAltura  = alturaA / mapHeight
        val tileFit = minOf(tileWPorLargura, tileHPorAltura)

        // Garante zoom mínimo para o personagem ser visível
        val tileSize = tileFit.coerceAtLeast(tileSizeMinimo)
        tileWDinamico = tileSize
        tileHDinamico = tileSize

        val larguraMapa = mapWidth * tileSize
        val alturaMapa  = mapHeight * tileSize

        if (larguraMapa <= screenWidth && alturaMapa <= alturaA) {
            // Mapa cabe inteiro — centraliza
            cameraX = (screenWidth - larguraMapa) / 2f
            cameraY = (alturaA - alturaMapa) / 2f
        } else {
            // Mapa maior que a tela — câmera segue o hero
            val heroSx = heroWorldX * tileSize
            val heroSy = heroWorldY * tileSize
            cameraX = (screenWidth / 2f - heroSx).coerceIn(screenWidth - larguraMapa, 0f)
            cameraY = (alturaA / 2f - heroSy).coerceIn(alturaA - alturaMapa, 0f)
        }
    }

    // Frames de animação atuais
    private var heroAnimFrame: Int = 0
    private var spikeAnimFrame: Int = 0
    private var monsterAnimFrame: Int = 0
    private var frameCounter: Int = 0

    // Contador global de frames para animações independentes (placa, etc.)
    private var frameTotal: Long = 0L

    /**
     * Renderiza um frame completo do jogo.
     * @param canvas canvas de destino (obtido do SurfaceHolder)
     * @param gameState estado atual do jogo
     */
    fun render(canvas: Canvas, gameState: GameState) {
        // Atualiza posição do hero para scroll da câmera
        heroWorldX = gameState.heroPosition.x
        heroWorldY = gameState.heroPosition.y

        // Recalcula tile e câmera
        val mazeAtual = gameState.mazeData
        if (mazeAtual != null) recalcularTile(mazeAtual.width, mazeAtual.height)

        val tileW = tileWDinamico
        val tileH = tileHDinamico

        val palette = BIOME_PALETTES[gameState.currentBiome]
            ?: BIOME_PALETTES.values.first()

        spriteCache.currentBiome = gameState.currentBiome.name

        frameCounter++
        frameTotal++
        if (frameCounter % 8 == 0) { // Reduzido frequência de atualização de animação (mais leve)
            heroAnimFrame = (heroAnimFrame + 1) % 8
            spikeAnimFrame = (spikeAnimFrame + 1) % 12
            monsterAnimFrame = (monsterAnimFrame + 1) % 8
        }

        // Fundo do retângulo A
        bgPaint.color = palette.backgroundColor
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight * fracaoAreaJogo, bgPaint)

        val mazeData = gameState.mazeData ?: return

        val entradaTx = mazeData.startIndex % mazeData.width
        val entradaTy = mazeData.startIndex / mazeData.width
        val saidaTx = mazeData.exitIndex % mazeData.width
        val saidaTy = mazeData.exitIndex / mazeData.width

        // =====================================================================
        // PIPELINE TOP-DOWN — linha por linha, coluna por coluna
        // Ordem: chão → decorativos → personagens → paredes
        // =====================================================================

        val minX = ((-cameraX) / tileW).toInt().coerceAtLeast(0)
        val maxX = ((screenWidth - cameraX) / tileW).toInt().coerceAtMost(mazeData.width - 1)
        val minY = ((-cameraY) / tileH).toInt().coerceAtLeast(0)
        val maxY = ((screenHeight * fracaoAreaJogo - cameraY) / tileH).toInt().coerceAtMost(mazeData.height - 1)

        // Passo 1: Chão
        for (ty in minY..maxY) {
            for (tx in minX..maxX) {
                val idx = ty * mazeData.width + tx
                if (idx < 0 || idx >= mazeData.tiles.size) continue
                if (mazeData.tiles[idx] == 1) continue
                val sx = tx * tileW + cameraX
                val sy = ty * tileH + cameraY
                tileRenderer.renderFloorTile(canvas, sx, sy, tileW, tileH, palette, tx, ty)
            }
        }

        // Passo 2: Decorativos (Otimizado: renderiza apenas 1 a cada 12 tiles)
        for (ty in minY..maxY) {
            for (tx in minX..maxX) {
                val idx = ty * mazeData.width + tx
                if (idx < 0 || idx >= mazeData.tiles.size) continue
                if (mazeData.tiles[idx] != 0) continue
                if ((tx == entradaTx && ty == entradaTy) || (tx == saidaTx && ty == saidaTy)) continue
                val decorSeed = (tx * 31 + ty * 17 + mazeData.seed.toInt()) % 12
                if (decorSeed != 0) continue
                val variant = (tx + ty) % 4
                val sx = tx * tileW + cameraX
                val sy = ty * tileH + cameraY
                val decorKey = "biome_${gameState.currentBiome.name}_decor_$variant"
                val decorBitmap = spriteCache.getOrCreate(decorKey) {
                    tileRenderer.createTileBitmap(
                        when (variant) {
                            0 -> TileType.DECORATIVE_0; 1 -> TileType.DECORATIVE_1
                            2 -> TileType.DECORATIVE_2; else -> TileType.DECORATIVE_3
                        }, tileW.toInt(), tileH.toInt(), palette, biome = gameState.currentBiome
                    )
                }
                canvas.drawBitmap(decorBitmap, sx, sy, null)
            }
        }

        // Passo 3: Personagens (centro do tile)
        val heroSx = gameState.heroPosition.x * tileW + cameraX + tileW / 2f
        val heroSy = gameState.heroPosition.y * tileH + cameraY + tileH / 2f
        val spikeSx = gameState.spikePosition.x * tileW + cameraX + tileW / 2f
        val spikeSy = gameState.spikePosition.y * tileH + cameraY + tileH / 2f

        for (monster in gameState.monsters) {
            if (!monster.isActive) continue
            val mx = monster.position.x * tileW + cameraX + tileW / 2f
            val my = monster.position.y * tileH + cameraY + tileH / 2f
            val seed = monster.id.hashCode()
            val appearance = MonsterAppearance(
                bodyColor = Color.rgb(150 + (seed and 0xFF) % 100, 50 + (seed shr 8 and 0xFF) % 80, 50 + (seed shr 16 and 0xFF) % 80),
                eyeColor = Color.rgb(255, 200 + (seed and 0x3F), 0),
                size = 0.7f + ((seed and 0xF) / 15f) * 0.6f,
                shapeVariant = seed and 0x3, animVariant = seed shr 4 and 0x3
            )
            characterRenderer.renderMonster(canvas, mx, my, appearance, monsterAnimFrame, tileW, tileH)
        }

        characterRenderer.renderSpike(canvas, spikeSx, spikeSy, gameState.spikeCompanionState, spikeAnimFrame, tileW, tileH)

        // Se estiver em animação de saída, sobe o herói
        var drawHeroSy = heroSy
        if (gameState.isExiting) {
            val progress = (gameState.exitAnimationTimerMs.toFloat() / 800f).coerceIn(0f, 1f)
            drawHeroSy -= progress * tileH * 1.2f // Sobe até o teto
        }

        val heroDirection = mapDirectionToHeroDirection(gameState.heroDirection)
        val heroAnimState = when {
            gameState.isExiting -> HeroAnimState.WALK // Usa animação de andar para simular escalada
            gameState.heroIsSlowedDown -> HeroAnimState.SLOWDOWN
            gameState.heroStoppedDurationSec > 0.05f -> HeroAnimState.IDLE
            else -> HeroAnimState.WALK
        }
        val currentFrame = if (heroAnimState == HeroAnimState.IDLE) 0 else heroAnimFrame
        characterRenderer.renderHero(canvas, heroSx, drawHeroSy, heroDirection, heroAnimState, currentFrame, tileW, tileH)

        // Passo 4: Paredes (por cima de tudo)
        for (ty in minY..maxY) {
            for (tx in minX..maxX) {
                val idx = ty * mazeData.width + tx
                if (idx < 0 || idx >= mazeData.tiles.size) continue
                if (mazeData.tiles[idx] != 1) continue
                val sx = tx * tileW + cameraX
                val sy = ty * tileH + cameraY
                tileRenderer.renderWallTile(canvas, sx, sy, tileW, tileH, palette, tx, ty)
            }
        }

        // Passo 5: Placa de Saída e Escada
        if (saidaTx in minX..maxX && saidaTy in minY..maxY) {
            renderizarPlacaSaida(canvas, mazeData, saidaTx, saidaTy, tileW, tileH)
        }

        // Partículas
        particleSystem.render(canvas)

        // HUD no retângulo B
        val alturaA = screenHeight * fracaoAreaJogo
        val alturaB = screenHeight - alturaA
        hudRenderer.renderRetanguloB(canvas, gameState, screenWidth.toFloat(), alturaA, alturaB)
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
                if (result.size >= 1200) return result  // culling: máximo 1200 tiles para mapa grande
            }
        }

        return result
    }

    /**
     * Atualiza a posição da câmera para seguir o Hero.
     * Usa clamp para não mostrar área fora do mapa quando o hero está perto das bordas.
     */
    fun updateCamera(heroScreenX: Float, heroScreenY: Float) {
        cameraX = screenWidth / 2f - heroScreenX
        cameraY = screenHeight * 0.45f - heroScreenY
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
        lightingSystem.release()
    }

    // -------------------------------------------------------------------------
    // Placa de saída animada
    // -------------------------------------------------------------------------

    /**
     * Renderiza uma placa estática acima do tile de saída.
     * Poste + tabuleta de madeira com texto "SAÍDA".
     */
    private fun renderizarPlacaSaida(
        canvas: Canvas,
        maze: MazeData,
        saidaTx: Int,
        saidaTy: Int,
        tileW: Float,
        tileH: Float
    ) {
        val screenPos = IsometricProjection.worldToScreen(saidaTx, saidaTy, tileW, tileH)
        val cx = screenPos.x + cameraX + tileW / 2f
        val baseSy = screenPos.y + cameraY + tileH / 2f

        // Placa centralizada no tile
        val placaCx = cx
        val placaBaseY = baseSy - tileH * 0.8f
        val placaLargura = tileW * 0.8f
        val placaAltura = tileH * 0.6f
        val alturaPosto = tileH * 0.4f

        // Renderizar Escada CENTRALIZADA ACIMA da placa
        // Ela desce do teto e para logo atrás do topo da placa
        renderizarEscadaSaida(canvas, placaCx, placaBaseY - placaAltura, tileW, tileH, maze.exitWallDirection ?: com.ericleber.joguinho.core.Direction.NORTH)

        // Poste da placa
        placaPaint.style = Paint.Style.FILL
        placaPaint.color = Color.rgb(80, 50, 20)
        canvas.drawRect(placaCx - tileW * 0.03f, placaBaseY, placaCx + tileW * 0.03f, placaBaseY + alturaPosto, placaPaint)

        // Tabuleta
        val placaLeft = placaCx - placaLargura / 2f
        val placaTop = placaBaseY - placaAltura
        val placaRight = placaCx + placaLargura / 2f
        val placaBottom = placaBaseY
        
        placaPaint.color = Color.rgb(50, 30, 10)
        canvas.drawRect(placaLeft - 2f, placaTop - 2f, placaRight + 2f, placaBottom + 2f, placaPaint)
        placaPaint.color = Color.rgb(160, 110, 50)
        canvas.drawRect(placaLeft, placaTop, placaRight, placaBottom, placaPaint)

        // Texto "SAÍDA" centralizado
        placaPaint.color = Color.WHITE
        placaPaint.textSize = (tileH * 0.24f).coerceAtLeast(10f)
        placaPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("SAÍDA", placaCx, placaTop + placaAltura * 0.65f, placaPaint)
        
        // Marcador no chão (Círculo de luz/checkpoint)
        // Indica exatamente onde o herói deve ficar para subir
        placaPaint.style = Paint.Style.FILL
        placaPaint.color = Color.argb(100, 255, 255, 255) // Branco translúcido
        canvas.drawOval(
            placaCx - tileW * 0.3f, 
            baseSy - tileH * 0.15f, 
            placaCx + tileW * 0.3f, 
            baseSy + tileH * 0.15f, 
            placaPaint
        )
        
        // Borda do marcador
        placaPaint.style = Paint.Style.STROKE
        placaPaint.strokeWidth = 3f
        placaPaint.color = Color.WHITE
        canvas.drawOval(
            placaCx - tileW * 0.3f, 
            baseSy - tileH * 0.15f, 
            placaCx + tileW * 0.3f, 
            baseSy + tileH * 0.15f, 
            placaPaint
        )
        
        placaPaint.strokeWidth = 0f
        placaPaint.style = Paint.Style.FILL
        placaPaint.textAlign = Paint.Align.LEFT
    }

    /**
     * Desenha uma escada ou túnel na parede adjacente ao tile de saída.
     */
    private fun renderizarEscadaSaida(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        tileW: Float,
        tileH: Float,
        direcao: com.ericleber.joguinho.core.Direction
    ) {
        placaPaint.style = Paint.Style.STROKE
        placaPaint.strokeWidth = 6f
        placaPaint.color = Color.WHITE // Escada branca
        
        val ladderW = tileW * 0.35f
        val ladderTop = cy - tileH * 3.0f // Vem do teto (bem alto)
        val ladderBottom = cy + tileH * 0.1f // Termina logo atrás da placa
        
        // Desenha as duas hastes verticais da escada
        canvas.drawLine(cx - ladderW/2, ladderTop, cx - ladderW/2, ladderBottom, placaPaint)
        canvas.drawLine(cx + ladderW/2, ladderTop, cx + ladderW/2, ladderBottom, placaPaint)
        
        // Desenha os degraus
        val numDegraus = 10
        for (i in 0 until numDegraus) {
            val stepY = ladderTop + (ladderBottom - ladderTop) * (i.toFloat() / (numDegraus - 1))
            canvas.drawLine(cx - ladderW/2, stepY, cx + ladderW/2, stepY, placaPaint)
        }
        
        placaPaint.strokeWidth = 0f
        placaPaint.style = Paint.Style.FILL
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
