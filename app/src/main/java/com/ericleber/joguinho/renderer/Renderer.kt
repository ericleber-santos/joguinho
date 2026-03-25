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

    // Tamanho base do tile em dp — calibrado para ASUS X00TDB (density~2.0)
    // 40dp * 2.0 = 80px por tile → ~9 tiles visíveis na horizontal (720px) — zoom +25%
    private val baseTileSize = 40

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
        val tileW = IsometricProjection.getTileWidth(baseTileSize, density)
        val tileH = IsometricProjection.getTileHeight(baseTileSize, density)

        val palette = BIOME_PALETTES[gameState.currentBiome]
            ?: BIOME_PALETTES.values.first()

        // Atualiza bioma no cache
        spriteCache.currentBiome = gameState.currentBiome.name

        // Avança frames de animação a cada 6 frames de render (~10fps de animação a 60fps)
        frameCounter++
        frameTotal++
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

        // Calcula posições de entrada e saída a partir dos índices do MazeData
        val entradaTx = mazeData.startIndex % mazeData.width
        val entradaTy = mazeData.startIndex / mazeData.width
        val saidaTx = mazeData.exitIndex % mazeData.width
        val saidaTy = mazeData.exitIndex / mazeData.width

        // Posição isométrica dos personagens (em tiles inteiros para ordenação)
        val heroTx = gameState.heroPosition.x.toInt()
        val heroTy = gameState.heroPosition.y.toInt()
        val spikeTx = gameState.spikePosition.x.toInt()
        val spikeTy = gameState.spikePosition.y.toInt()

        // Pré-calcula dados do Hero para renderização
        val heroScreenPos = IsometricProjection.worldToScreen(
            gameState.heroPosition.x, gameState.heroPosition.y, tileW, tileH
        )
        val heroDirection = mapDirectionToHeroDirection(gameState.heroDirection)
        val heroAnimState = when {
            gameState.heroIsSlowedDown -> HeroAnimState.SLOWDOWN
            else -> HeroAnimState.WALK
        }

        // Pré-calcula dados do Spike
        val spikeScreenPos = IsometricProjection.worldToScreen(
            gameState.spikePosition.x, gameState.spikePosition.y, tileW, tileH
        )
        val spikeState = gameState.spikeCompanionState

        // Pré-calcula dados dos monsters
        data class MonsterRenderData(
            val tx: Int, val ty: Int,
            val sx: Float, val sy: Float,
            val appearance: MonsterAppearance
        )
        val monstersData = gameState.monsters.filter { it.isActive }.map { monster ->
            val sp = IsometricProjection.worldToScreen(
                monster.position.x, monster.position.y, tileW, tileH
            )
            val seed = monster.id.hashCode()
            MonsterRenderData(
                tx = monster.position.x.toInt(),
                ty = monster.position.y.toInt(),
                sx = sp.x + cameraX + tileW / 2f,
                sy = sp.y + cameraY,
                appearance = MonsterAppearance(
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
            )
        }

        // =====================================================================
        // PIPELINE ISOMÉTRICO UNIFICADO
        //
        // Para cada diagonal sum = tx + ty (ordem crescente = frente para trás):
        //   1. Chão de todos os tiles nessa diagonal
        //   2. Paredes de todos os tiles nessa diagonal
        //   3. Decorativos de todos os tiles nessa diagonal
        //   4. Personagens cujo sum == heroTx+heroTy (entre paredes da mesma linha)
        //
        // Isso garante que paredes com ty > heroTy sejam desenhadas DEPOIS do
        // personagem, cobrindo-o corretamente na perspectiva isométrica.
        // =====================================================================

        // Agrupa tiles visíveis por diagonal sum
        val tilesPorDiagonal = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()
        for ((tx, ty) in visibleTiles) {
            val sum = tx + ty
            tilesPorDiagonal.getOrPut(sum) { mutableListOf() }.add(Pair(tx, ty))
        }

        val heroSum = heroTx + heroTy
        val spikeSum = spikeTx + spikeTy

        // Conjunto de sums de monsters para renderização intercalada
        val monsterSumMap = mutableMapOf<Int, MutableList<MonsterRenderData>>()
        for (m in monstersData) {
            val s = m.tx + m.ty
            monsterSumMap.getOrPut(s) { mutableListOf() }.add(m)
        }

        val minSum = tilesPorDiagonal.keys.minOrNull() ?: 0
        val maxSum = tilesPorDiagonal.keys.maxOrNull() ?: 0

        for (sum in minSum..maxSum) {
            val tilesNaLinha = tilesPorDiagonal[sum] ?: emptyList()

            // --- Passo 1: Chão de todos os tiles nessa diagonal ---
            for ((tx, ty) in tilesNaLinha) {
                val tileIndex = ty * mazeData.width + tx
                if (tileIndex < 0 || tileIndex >= mazeData.tiles.size) continue
                if (mazeData.tiles[tileIndex] == 1) continue

                val screenPos = IsometricProjection.worldToScreen(tx, ty, tileW, tileH)
                val sx = screenPos.x + cameraX
                val sy = screenPos.y + cameraY

                if (tx == entradaTx && ty == entradaTy) {
                    val entradaBitmap = spriteCache.getOrCreate("entrada") {
                        tileRenderer.createTileBitmap(TileType.ENTRADA, tileW.toInt(), tileH.toInt(), palette)
                    }
                    canvas.drawBitmap(entradaBitmap, sx, sy, null)
                } else {
                    tileRenderer.renderFloorTile(canvas, sx, sy, tileW, tileH, palette, tx, ty)
                }
            }

            // --- Passo 2: Decorativos nessa diagonal ---
            for ((tx, ty) in tilesNaLinha) {
                val tileIndex = ty * mazeData.width + tx
                if (tileIndex < 0 || tileIndex >= mazeData.tiles.size) continue
                if (mazeData.tiles[tileIndex] != 0) continue
                if ((tx == entradaTx && ty == entradaTy) || (tx == saidaTx && ty == saidaTy)) continue
                val decorSeed = (tx * 31 + ty * 17 + mazeData.seed.toInt()) % 7
                if (decorSeed != 0) continue

                val temParedeAdjacente = listOf(
                    Pair(tx - 1, ty), Pair(tx + 1, ty),
                    Pair(tx, ty - 1), Pair(tx, ty + 1)
                ).any { (nx, ny) ->
                    val ni = ny * mazeData.width + nx
                    ni >= 0 && ni < mazeData.tiles.size && mazeData.tiles[ni] == 1
                }
                if (temParedeAdjacente) continue

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
                        tileW.toInt(), tileH.toInt(), palette,
                        biome = gameState.currentBiome
                    )
                }
                canvas.drawBitmap(decorBitmap, sx, sy, null)
            }

            // --- Passo 3: Personagens nessa diagonal (antes das paredes da mesma linha) ---
            // O Y passado é o topo do tile (sy). O personagem deve ter seus pés
            // no centro vertical do tile (sy + tileH/2), então o centro do sprite
            // fica em sy + tileH/2 - alturaSprite/2. Para o Hero (s=tileW/48),
            // a altura total é ~36s ≈ 36*(tileW/48) = 0.75*tileW = 1.5*tileH.
            // Queremos que os pés (cy + 24s) fiquem em sy + tileH/2:
            //   cy = sy + tileH/2 - 24s = sy + tileH/2 - 24*(tileW/48) = sy + tileH/2 - tileW/2
            // Como tileH = tileW/2: cy = sy + tileH/2 - tileH = sy - tileH/2
            val charOffsetY = -tileH * 0.5f

            // Monsters
            monsterSumMap[sum]?.forEach { m ->
                characterRenderer.renderMonster(canvas, m.sx, m.sy + charOffsetY, m.appearance, monsterAnimFrame, tileW, tileH)
            }
            // Spike
            if (sum == spikeSum) {
                characterRenderer.renderSpike(
                    canvas,
                    spikeScreenPos.x + cameraX + tileW / 2f,
                    spikeScreenPos.y + cameraY + charOffsetY,
                    spikeState, spikeAnimFrame, tileW, tileH
                )
            }
            // Hero
            if (sum == heroSum) {
                characterRenderer.renderHero(
                    canvas,
                    heroScreenPos.x + cameraX + tileW / 2f,
                    heroScreenPos.y + cameraY + charOffsetY,
                    heroDirection, heroAnimState, heroAnimFrame, tileW, tileH
                )
            }
            // --- Passo 4: Paredes nessa diagonal (por cima dos personagens da mesma linha) ---
            for ((tx, ty) in tilesNaLinha) {
                val tileIndex = ty * mazeData.width + tx
                if (tileIndex < 0 || tileIndex >= mazeData.tiles.size) continue
                if (mazeData.tiles[tileIndex] != 1) continue

                val screenPos = IsometricProjection.worldToScreen(tx, ty, tileW, tileH)
                val sx = screenPos.x + cameraX
                val sy = screenPos.y + cameraY

                val wallOffsetY = sy - tileH * 1.4f
                tileRenderer.renderWallTile(canvas, sx, wallOffsetY, tileW, tileH, palette, tx, ty)
            }
        }

        // Placa de saída animada (sempre por cima de tudo)
        renderizarPlacaSaida(canvas, mazeData, saidaTx, saidaTy, tileW, tileH)

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
                if (result.size >= 400) return result  // culling: máximo 400 tiles
            }
        }

        return result
    }

    /**
     * Atualiza a posição da câmera para seguir o Hero.
     * Centraliza o Hero levemente abaixo do centro da tela para mostrar
     * mais do labirinto à frente (direção de exploração).
     *
     * @param heroScreenX posição X do Hero na tela (antes do offset de câmera)
     * @param heroScreenY posição Y do Hero na tela (antes do offset de câmera)
     */
    fun updateCamera(heroScreenX: Float, heroScreenY: Float) {
        cameraX = screenWidth / 2f - heroScreenX
        // Offset vertical: hero fica 35% do topo — mostra mais labirinto à frente
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
     * Renderiza uma placa pixel art animada flutuando acima do tile de saída.
     * A placa tem um poste, uma tabuleta de madeira com texto "SAÍDA" e uma seta
     * apontando para o corredor de saída. A placa sobe e desce suavemente (bob).
     *
     * Requisito: imersão — substituição do losango dourado por sinalização diegética.
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

        // Animação de bob: sobe e desce suavemente
        val pulsacao = sin(frameTotal * 0.08f).toFloat()
        val bobY = pulsacao * tileH * 0.3f

        // Posição vertical da base da placa (1.5 tileH acima do centro do tile)
        val placaBaseY = baseSy - tileH * 1.5f + bobY

        // Dimensões da placa (em pixels, relativas ao tileW)
        val placaLargura = tileW * 0.9f
        val placaAltura = tileH * 0.7f
        val alturaPosto = tileH * 0.8f

        // --- Poste ---
        placaPaint.style = Paint.Style.FILL
        placaPaint.color = Color.rgb(80, 50, 20)  // marrom escuro
        val postoLeft = cx - tileW * 0.04f
        val postoRight = cx + tileW * 0.04f
        canvas.drawRect(postoLeft, placaBaseY, postoRight, placaBaseY + alturaPosto, placaPaint)

        // --- Tabuleta de madeira (fundo) ---
        val placaLeft = cx - placaLargura / 2f
        val placaTop = placaBaseY - placaAltura
        val placaRight = cx + placaLargura / 2f
        val placaBottom = placaBaseY

        // Sombra/borda escura
        placaPaint.color = Color.rgb(50, 30, 10)
        canvas.drawRect(placaLeft - 2f, placaTop - 2f, placaRight + 2f, placaBottom + 2f, placaPaint)

        // Madeira
        placaPaint.color = Color.rgb(160, 110, 50)
        canvas.drawRect(placaLeft, placaTop, placaRight, placaBottom, placaPaint)

        // Detalhe de madeira (veio horizontal)
        placaPaint.color = Color.rgb(140, 95, 40)
        val veioY = placaTop + placaAltura * 0.45f
        canvas.drawRect(placaLeft + 2f, veioY, placaRight - 2f, veioY + 2f, placaPaint)

        // --- Texto "SAÍDA" ---
        placaPaint.style = Paint.Style.FILL
        placaPaint.color = Color.WHITE
        placaPaint.textSize = (tileH * 0.28f).coerceAtLeast(8f)
        placaPaint.textAlign = Paint.Align.CENTER
        val textoY = placaTop + placaAltura * 0.42f
        canvas.drawText("SAÍDA", cx, textoY, placaPaint)
        placaPaint.textAlign = Paint.Align.LEFT  // reset

        // --- Seta apontando para o corredor de saída ---
        val (setaDx, setaDy) = detectarDirecaoSaida(maze, saidaTx, saidaTy)
        desenharSetaIsometrica(canvas, cx, placaTop + placaAltura * 0.78f, setaDx, setaDy, tileW, tileH, placaAltura)
    }

    /**
     * Detecta qual vizinho ortogonal do tile de saída é chão livre e está
     * mais próximo da borda do mapa — esse é o corredor de saída.
     * Retorna (dx, dy) onde dx/dy ∈ {-1, 0, 1}.
     */
    private fun detectarDirecaoSaida(maze: MazeData, saidaTx: Int, saidaTy: Int): Pair<Int, Int> {
        val vizinhos = listOf(
            Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1)
        )

        var melhorDir = Pair(1, 0)
        var menorDistBorda = Int.MAX_VALUE

        for ((dx, dy) in vizinhos) {
            val nx = saidaTx + dx
            val ny = saidaTy + dy
            if (nx < 0 || ny < 0 || nx >= maze.width || ny >= maze.height) continue
            val idx = ny * maze.width + nx
            if (maze.tiles[idx] != 0) continue  // só chão livre

            // Distância até a borda mais próxima do mapa
            val distBorda = minOf(nx, ny, maze.width - 1 - nx, maze.height - 1 - ny)
            if (distBorda < menorDistBorda) {
                menorDistBorda = distBorda
                melhorDir = Pair(dx, dy)
            }
        }

        return melhorDir
    }

    /**
     * Desenha uma seta triangular na placa apontando na direção isométrica correta.
     * No espaço isométrico:
     *   dx=1, dy=0  → direita-baixo na tela
     *   dx=-1, dy=0 → esquerda-cima
     *   dx=0, dy=1  → direita-cima (sul no grid = baixo-direita isométrico)
     *   dx=0, dy=-1 → esquerda-baixo
     */
    private fun desenharSetaIsometrica(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        dx: Int,
        dy: Int,
        tileW: Float,
        tileH: Float,
        placaAltura: Float
    ) {
        val tamanho = placaAltura * 0.28f

        // Converte direção do grid para vetor de tela isométrico (normalizado)
        // Isométrico: screenX = (worldX - worldY) * tileW/2, screenY = (worldX + worldY) * tileH/2
        val telaX = (dx - dy).toFloat()
        val telaY = (dx + dy).toFloat() * 0.5f
        val comprimento = kotlin.math.sqrt(telaX * telaX + telaY * telaY).coerceAtLeast(0.01f)
        val normX = telaX / comprimento
        val normY = telaY / comprimento

        // Perpendicular à direção da seta
        val perpX = -normY
        val perpY = normX

        // Triângulo: ponta na frente, base atrás
        val path = Path()
        path.moveTo(cx + normX * tamanho, cy + normY * tamanho)           // ponta
        path.lineTo(cx - normX * tamanho + perpX * tamanho * 0.6f,
                    cy - normY * tamanho + perpY * tamanho * 0.6f)         // base esquerda
        path.lineTo(cx - normX * tamanho - perpX * tamanho * 0.6f,
                    cy - normY * tamanho - perpY * tamanho * 0.6f)         // base direita
        path.close()

        // Borda escura
        placaPaint.style = Paint.Style.FILL
        placaPaint.color = Color.rgb(80, 60, 0)
        canvas.drawPath(path, placaPaint)

        // Seta amarela/dourada
        val pathInner = Path()
        val s = 0.75f
        pathInner.moveTo(cx + normX * tamanho * s, cy + normY * tamanho * s)
        pathInner.lineTo(cx - normX * tamanho * s + perpX * tamanho * 0.45f,
                         cy - normY * tamanho * s + perpY * tamanho * 0.45f)
        pathInner.lineTo(cx - normX * tamanho * s - perpX * tamanho * 0.45f,
                         cy - normY * tamanho * s - perpY * tamanho * 0.45f)
        pathInner.close()

        placaPaint.color = Color.rgb(255, 210, 50)
        canvas.drawPath(pathInner, placaPaint)
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
