package com.ericleber.joguinho.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.ericleber.joguinho.biome.BIOME_PALETTES
import com.ericleber.joguinho.biome.BiomePalette
import com.ericleber.joguinho.biome.BiomeWorld
import com.ericleber.joguinho.core.GameState
import com.ericleber.joguinho.core.MazeData
import com.ericleber.joguinho.core.Position
import kotlin.math.sin

/**
 * Interface para objetos que podem ser ordenados por profundidade.
 */
interface Renderable {
    val ySort: Float
    fun render(canvas: android.graphics.Canvas)
}

/**
 * Orquestrador de renderização isométrica.
 */
class Renderer(
    private val spriteCache: SpriteCache,
    private val tileRenderer: TileRenderer,
    private val characterRenderer: CharacterRenderer,
    private val particleSystem: ParticleSystem,
    private val hudRenderer: HudRenderer
) {

    private val lightingSystem = LightingSystem()
    private val portalRenderer = PortalRenderer()

    // -----------------------------------------------------------------------
    // Fase 10 — Sistemas de imersão visual
    // -----------------------------------------------------------------------

    /** Sistema de goteiras animadas para biomas com hasDrips = true. */
    val dripSystem = DripSystem()

    /** Sistema de partículas ambiente (poeira, esporos, terra). */
    private val ambientParticles = AmbientParticleSystem()

    /** Paint para overlay de luz ambiente (PorterDuff MULTIPLY). */
    private val ambientLightPaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.FILL
    }

    /** Configuração para partículas de musgo (Soul Tiles). */
    private val mossConfig = ParticleConfig(
        vxRange = -30f..30f,
        vyRange = -50f..-20f,
        lifeRange = 0.4f..0.8f,
        startColor = Color.rgb(60, 140, 40),
        endColor = Color.argb(0, 40, 80, 20),
        startSize = 6f,
        endSize = 2f,
        type = ParticleType.RECT
    )

    /** Bioma do último frame — usado para detectar troca e reinicializar sistemas. */
    private var lastWorld: BiomeWorld? = null
    /** Mapa do último frame — usado para re-init do DripSystem. */
    private var lastMaze: MazeData? = null

    /** Novo fundo procedural. */
    private var proceduralBackground: ProceduralBackground? = null
    private var proceduralBackgroundFloor: Int = -1

    var cameraX: Float = 0f
    var cameraY: Float = 0f
    private var cameraResetPending = true
    var screenWidth: Int = 0
    var screenHeight: Int = 0
    var density: Float = 1f
    private var lastFrameTimeMs: Long = 0L

    private val bgPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }

    private val bgLayerPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val bgPath = Path()

    enum class BgCategory {
        CAVERN, FOREST, VOLCANIC, CRYSTAL, VOID
    }


    // Paint para os popups de score (Estilo Clean/Android)
    private val popupPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    // Paint para a placa de saída animada
    private val placaPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }

    // --- Fase 12: Paints de Ecologia ---
    private val fireVfxPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val blinkVfxPaint = Paint().apply {
        isAntiAlias = true
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
    // O retângulo B (antigo HUD inferior) não existe mais. Tela cheia (Fullscreen)
    private val fracaoAreaJogo = 1.00f

    /**
     * Recalcula o tile e a câmera para que o mapa preencha a tela de forma responsiva.
     * Ajusta o zoom dependendo se é celular ou tablet.
     */
    // Tamanho mínimo do tile em px para o personagem ser visível (ajustado pela densidade)
    private var tileSizeMinimo = 31f // Reduzido mais 20% para afastar a câmera

    // Posição do hero para scroll da câmera
    private var heroWorldX: Float = 0f
    private var heroWorldY: Float = 0f

    fun recalcularTile(mapWidth: Int, mapHeight: Int, gameState: GameState) {
        if (screenWidth <= 0 || screenHeight <= 0) return
        
        // Ajusta o tamanho mínimo baseado na densidade da tela
        tileSizeMinimo = 40f * density 
        
        val alturaA = screenHeight * fracaoAreaJogo

        // Em tablets (largura > 600dp), mostramos um pouco mais do mapa
        val isTablet = (screenWidth / density) >= 600f
        
        // Zoom agressivo: poucos tiles visíveis = tudo maior e mais claro
        val tilesVisiveisDesejados = if (isTablet) {
            24f // Tablet: aprox. 24 tiles visíveis
        } else {
            20f // Celular: aprox. 20 tiles visíveis (câmera mais afastada)
        }

        // Calcula tile para que caibam X tiles na menor dimensão da tela
        val tileBase = minOf(screenWidth.toFloat(), alturaA) / tilesVisiveisDesejados
        
        // Garante que o tile não seja menor que o mínimo para visibilidade
        val tileSize = tileBase.coerceAtLeast(tileSizeMinimo)
        
        tileWDinamico = tileSize
        tileHDinamico = tileSize

        val larguraMapa = mapWidth * tileSize
        val alturaMapa  = mapHeight * tileSize

        val heroSx = heroWorldX * tileSize
        val heroSy = heroWorldY * tileSize
        
        // Look-ahead horizontal: 3.5 tiles na direção da velocidade física do herói
        val targetOffset = if (Math.abs(gameState.heroVelocityX) > 0.1f) {
            Math.signum(gameState.heroVelocityX) * 3.5f * tileSize
        } else {
            0f
        }

        val targetHeroSx = heroSx + targetOffset

        // Calcula a posição ideal (alvo) da câmera, prendendo nos limites físicos reais do mapa em pixels
        val targetCameraX = (screenWidth / 2f - targetHeroSx).coerceIn(screenWidth - larguraMapa, 0f)
        val targetCameraY = (alturaA / 2f - heroSy).coerceIn(alturaA - alturaMapa, 0f)

        // Se for o primeiro frame ou troca de mapa, teleporta a câmera instantaneamente
        if (cameraResetPending) {
            cameraX = targetCameraX
            cameraY = targetCameraY
            cameraResetPending = false
        } else {
            // Caso contrário, interpola suavemente com Lerp cinematográfico (0.08f)
            cameraX += (targetCameraX - cameraX) * 0.08f
            cameraY += (targetCameraY - cameraY) * 0.08f
        }
        
        // Se o mapa for menor que a tela, centraliza o mapa
        if (larguraMapa < screenWidth) {
            cameraX = (screenWidth - larguraMapa) / 2f
        }
        if (alturaMapa < alturaA) {
            cameraY = (alturaA - alturaMapa) / 2f
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
        val currentTime = System.currentTimeMillis()
        if (lastFrameTimeMs == 0L) lastFrameTimeMs = currentTime
        val deltaMs = currentTime - lastFrameTimeMs
        lastFrameTimeMs = currentTime
        characterRenderer.update(deltaMs)

        // Atualiza posição do hero para scroll da câmera
        heroWorldX = gameState.heroPosition.x
        heroWorldY = gameState.heroPosition.y

        // Recalcula tile e câmera
        val mazeAtual = gameState.mazeData
        if (mazeAtual != null) {
            if (mazeAtual != lastMaze) {
                cameraResetPending = true
            }
            recalcularTile(mazeAtual.width, mazeAtual.height, gameState)
        }

        val tileW = tileWDinamico
        val tileH = tileHDinamico

        val basePalette = BIOME_PALETTES[gameState.currentBiomeWorld]
            ?: BIOME_PALETTES.values.first()
        val palette = com.ericleber.joguinho.biome.applyDepthHueShiftToPalette(basePalette, gameState.floorNumber)

        spriteCache.currentBiome = gameState.currentBiomeWorld.name
        tileRenderer.setBiomeWorld(gameState.currentBiomeWorld)

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

        // --- Fase 10: Céu Estelar para mundos abertos ---
        if (gameState.currentBiomeWorld.isOpenAir) {
            renderSky(canvas, gameState)
        }

        // --- FASE 11: Background Parallax em Camadas de Profundidade ---
        renderLayeredBackground(canvas, palette, gameState)

        // Limita a área de desenho do jogo para não invadir o HUD (Culling)
        canvas.save()
        canvas.clipRect(0f, 0f, screenWidth.toFloat(), screenHeight * fracaoAreaJogo)

        val mazeData = gameState.mazeData ?: run {
            canvas.restore()
            return
        }

        val entradaTx = mazeData.startIndex % mazeData.width
        val entradaTy = mazeData.startIndex / mazeData.width
        val saidaTx = mazeData.exitIndex % mazeData.width
        val saidaTy = mazeData.exitIndex / mazeData.width

        // =====================================================================
        // CÁLCULO DA VIEWPORT (CULLING)
        // =====================================================================
        val minX = ((-cameraX) / tileW).toInt().coerceAtLeast(0)
        val maxX = ((screenWidth - cameraX) / tileW).toInt().coerceAtMost(mazeData.width - 1)
        val minY = ((-cameraY) / tileH).toInt().coerceAtLeast(0)
        val maxY = ((screenHeight * fracaoAreaJogo - cameraY) / tileH).toInt().coerceAtMost(mazeData.height - 1)

        // =====================================================================
        // NOVO BACKGROUND PROCEDURAL
        // =====================================================================
        if (proceduralBackground == null || proceduralBackgroundFloor != gameState.floorNumber) {
            proceduralBackgroundFloor = gameState.floorNumber
            proceduralBackground = ProceduralBackground(gameState.floorNumber)
        }
        
        val currentLightingMode = gameState.currentBiomeWorld.lightingMode
        if (currentLightingMode == com.ericleber.joguinho.biome.LightingMode.SUBTERRANEAN ||
            currentLightingMode == com.ericleber.joguinho.biome.LightingMode.MOONLIGHT ||
            currentLightingMode == com.ericleber.joguinho.biome.LightingMode.VOID_DARK ||
            currentLightingMode == com.ericleber.joguinho.biome.LightingMode.DAYLIGHT ||
            currentLightingMode == com.ericleber.joguinho.biome.LightingMode.LAVA_GLOW ||
            currentLightingMode == com.ericleber.joguinho.biome.LightingMode.BIOLUMINESCENT) {
            proceduralBackground?.render(
                canvas,
                cameraX,
                cameraY,
                screenWidth,
                (screenHeight * fracaoAreaJogo).toInt(),
                System.currentTimeMillis(),
                palette.wallColor,
                palette.accentColor,
                currentLightingMode
            )
        }

        // Passo 1: Chão (Base Estática)
        for (ty in minY..maxY) {
            for (tx in minX..maxX) {
                val idx = ty * mazeData.width + tx
                if (idx < 0 || idx >= mazeData.tiles.size) continue
                if (mazeData.tiles[idx] == 1) continue
                val sx = tx * tileW + cameraX
                val sy = ty * tileH + cameraY
                
                // Desenha chão básico de pedra por padrão
                tileRenderer.renderFloorTile(canvas, sx, sy, tileW, tileH, palette, tx, ty)
                
                // Se for armadilha de vala, desenha o visual dinâmico correspondente (Ponto 4)
                when (mazeData.tiles[idx]) {
                    2 -> tileRenderer.renderTrapSpikesTile(canvas, sx, sy, tileW, tileH, tx, ty)
                    3 -> tileRenderer.renderTrapLavaTile(canvas, sx, sy, tileW, tileH, tx, ty)
                    4 -> tileRenderer.renderTrapPiranhaWaterTile(canvas, sx, sy, tileW, tileH, tx, ty)
                }
            }
        }

        // Passo 2: Decorativos (Base Estática)
        for (ty in minY..maxY) {
            for (tx in minX..maxX) {
                val idx = ty * mazeData.width + tx
                if (idx < 0 || idx >= mazeData.tiles.size) continue
                if (mazeData.tiles[idx] != 0) continue
                if ((tx == entradaTx && ty == entradaTy) || (tx == saidaTx && ty == saidaTy)) continue
                
                // Desativar decorativos para o Bioma Úmido
                if (gameState.currentBiomeWorld.name.contains("UMIDO") || gameState.currentBiomeWorld.name.contains("PANTANO")) continue

                val decorSeed = (tx * 31 + ty * 17 + mazeData.seed.toInt()) % 15
                if (decorSeed != 0) continue
                val variant = (tx + ty) % 4
                val sx = tx * tileW + cameraX
                val sy = ty * tileH + cameraY
                val decorKey = "biome_${gameState.currentBiomeWorld.name}_floor_${gameState.floorNumber}_decor_$variant"
                val decorBitmap = spriteCache.getOrCreate(decorKey) {
                    tileRenderer.createTileBitmap(
                        when (variant) {
                            0 -> TileType.DECORATIVE_0; 1 -> TileType.DECORATIVE_1
                            2 -> TileType.DECORATIVE_2; else -> TileType.DECORATIVE_3
                        }, tileW.toInt(), tileH.toInt(), palette, world = gameState.currentBiomeWorld
                    )
                }
                canvas.drawBitmap(decorBitmap, sx, sy, null)
            }
        }

        // =====================================================================
        // PIPELINE DE RENDERIZAÇÃO ORDENADA (Y-SORTING)
        // Resolve problemas de profundidade e oclusão (2.5D Real)
        // =====================================================================
        val renderList = mutableListOf<Renderable>()

        // 1. Paredes (Base do tile + 1.0f para garantir que cubram quem está atrás)
        for (ty in minY..maxY) {
            for (tx in minX..maxX) {
                val idx = ty * mazeData.width + tx
                if (idx < 0 || idx >= mazeData.tiles.size) continue
                if (mazeData.tiles[idx] != 1) continue
                
                val sx = tx * tileW + cameraX
                val sy = ty * tileH + cameraY
                
                // Calcula bitmask para AutoTiling
                val mask = tileRenderer.getWallBitmask(tx, ty, mazeData)
                
                // --- Fase 10: Paredes como Árvores ou Cristais ---
                val isForest = gameState.currentBiomeWorld == com.ericleber.joguinho.biome.BiomeWorld.FLORESTA_DE_ARVORES
                val wallKey = if (isForest) {
                    "biome_${gameState.currentBiomeWorld.name}_tree_variant_${(tx + ty) % 3}"
                } else {
                    "biome_${gameState.currentBiomeWorld.name}_wall_mask_$mask"
                }
                
                renderList.add(object : Renderable {
                    override val ySort: Float = ty + 1.0f
                    override fun render(c: Canvas) {
                        val wallBitmap = spriteCache.getOrCreate(wallKey) {
                            if (isForest) {
                                tileRenderer.createTreeBitmap(tileW.toInt(), tileH.toInt(), palette, tx, ty)
                            } else {
                                tileRenderer.createWallBitmap(tileW.toInt(), tileH.toInt(), palette, tx, ty, mazeData)
                            }
                        }
                        
                        // Desenha o bitmap (árvores são desenhadas um pouco acima para parecerem altas)
                        val drawY = if (isForest) sy - tileH * 0.8f else sy
                        if (isForest) {
                            c.drawBitmap(wallBitmap, sx, drawY, null)
                        } else {
                            c.drawBitmap(wallBitmap, sx - 16f, drawY - 16f, null)
                        }
                        
                        // --- Detalhes de Paredes Procedurais por WallDetailType ---
                        if (!isForest) {
                            tileRenderer.renderWallDetail(c, sx, drawY, tileW, tileH, palette, tx, ty)
                        }

                        // --- Fase 10: Soul Tiles (Micro-interações) ---
                        renderSoulTileInteraction(c, sx, drawY, tileW, tileH, tx, ty, gameState)
                    }
                })
            }
        }

        // 2. Itens (Power-ups)
        for (item in gameState.items) {
            if (!item.isActive) continue
            val sx = item.position.x * tileW + cameraX
            val sy = item.position.y * tileH + cameraY
            renderList.add(object : Renderable {
                override val ySort: Float = item.position.y + 0.4f // Levemente atrás do pé
                override fun render(c: Canvas) {
                    if (item.type == com.ericleber.joguinho.core.ItemType.HEART) {
                        characterRenderer.renderHeart(c, sx, sy, heroAnimFrame, tileW)
                    } else {
                        characterRenderer.renderBanana(c, sx, sy, heroAnimFrame, tileW)
                    }
                }
            })
        }

        // 3. Armadilhas (Removidas conforme pedido do usuário)

        // 4. Elementos de Sobrevivência (Removidos conforme pedido do usuário)

        // 5. Monstros (+0.5f para ySort nos pés)
        for (monster in gameState.monsters) {
            if (!monster.isActive) continue
            val mx = monster.position.x * tileW + cameraX
            val my = monster.position.y * tileH + cameraY
            val seed = monster.id.hashCode()
            val finalScale = if (monster.isBoss) 3.00f else 1.2f
            
            val bodyColor = if (monster.isBoss) Color.rgb(200, 40, 40) else Color.rgb(150 + (seed % 100), 50, 50)
            val eyeColor = if (monster.isBoss) Color.YELLOW else Color.RED
            val isHit = (System.currentTimeMillis() - monster.lastHitTimeMs) < 150L
            val isMoving = monster.aiState in listOf(
                com.ericleber.joguinho.core.MonsterAIState.CHASE,
                com.ericleber.joguinho.core.MonsterAIState.PATROL,
                com.ericleber.joguinho.core.MonsterAIState.DASHING,
                com.ericleber.joguinho.core.MonsterAIState.RETREAT
            )
            val appearance = MonsterAppearance(bodyColor, eyeColor, finalScale, seed and 0x3, seed shr 4 and 0x3, monster.isBoss, isHit, monster.archetype, isMoving)

            renderList.add(object : Renderable {
                override val ySort: Float = monster.position.y + 0.5f
                override fun render(c: Canvas) {
                    // Flash de dano (Overlay Branco)
                    if (monster.damageFlashRemainingMs > 0) {
                        characterRenderer.renderMonster(c, mx, my, appearance.copy(isHit = true), monsterAnimFrame, tileW, tileH)
                    } else {
                        characterRenderer.renderMonster(c, mx, my, appearance, monsterAnimFrame, tileW, tileH)
                    }
                }
            })
        }

        // 6. Spike & Hero (+0.5f para ySort nos pés)
        val heroSx = gameState.heroPosition.x * tileW + cameraX
        val heroSy = gameState.heroPosition.y * tileH + cameraY
        val spikeSx = gameState.spikePosition.x * tileW + cameraX
        val spikeSy = gameState.spikePosition.y * tileH + cameraY

        renderList.add(object : Renderable {
            override val ySort: Float = gameState.spikePosition.y + 0.5f
            override fun render(c: Canvas) {
                var renderSx = spikeSx
                var renderSy = spikeSy
                var scale = 1f
                var rotation = 0f
                var alpha = 255
                
                // 1. Lógica de Saída do Portal
                if (gameState.isExiting) {
                    val progressBase = (gameState.exitAnimationTimerMs.toFloat() / 800f)
                    val progress = ((progressBase - 0.2f) * 1.25f).coerceIn(0f, 1f)
                    if (progress > 0f) {
                        val saidaTx = mazeData.exitIndex % mazeData.width
                        val saidaTy = mazeData.exitIndex / mazeData.width
                        val portalSx = saidaTx * tileW + cameraX + tileW / 2f
                        val portalSy = saidaTy * tileH + cameraY + tileH / 2f
                        renderSx = spikeSx + (portalSx - spikeSx) * progress
                        renderSy = spikeSy + (portalSy - tileH * 0.2f - spikeSy) * progress - (Math.sin(progress * Math.PI).toFloat() * tileH)
                        scale = 1f - (progress * progress)
                        rotation = progress * 1080f
                        alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
                    }
                }
                
                // 2. Lógica de Combate (Lunge/Bote)
                val jumpProgress = if (gameState.spikeAttackTimerMs > 0) {
                    (600f - gameState.spikeAttackTimerMs) / 600f
                } else 0f
                val lungeProgress = if (jumpProgress <= 0.5f) jumpProgress * 2f else (1f - jumpProgress) * 2f
                
                val finalSx = renderSx + (gameState.spikeJumpOffsetX * lungeProgress * tileW)
                val finalSy = renderSy + (gameState.spikeJumpOffsetY * lungeProgress * tileH)
                val zOffset = gameState.spikeZ * tileH
                
                val facingLeft = when (gameState.heroDirection) {
                    com.ericleber.joguinho.core.Direction.WEST, com.ericleber.joguinho.core.Direction.NORTH_WEST, com.ericleber.joguinho.core.Direction.SOUTH_WEST -> true
                    else -> false
                }
                
                // 3. Renderização Final
                c.save()
                if (gameState.isExiting && alpha < 255) {
                    c.saveLayerAlpha(finalSx - tileW * 2f, finalSy - tileH * 4f, finalSx + tileW * 2f, finalSy + tileH * 2f, alpha)
                }
                if (gameState.isExiting) {
                    c.translate(finalSx, finalSy - tileH * 0.5f)
                    c.rotate(rotation)
                    c.scale(scale, scale)
                    c.translate(-finalSx, -(finalSy - tileH * 0.5f))
                }

                val spikeAnim = when(gameState.spikeCompanionState) {
                    "CORRENDO", "ENTUSIASMADO" -> com.ericleber.joguinho.renderer.AnimState.RUN
                    "ANDANDO" -> com.ericleber.joguinho.renderer.AnimState.WALK
                    else -> com.ericleber.joguinho.renderer.AnimState.IDLE
                }

                characterRenderer.drawDog(c, finalSx, finalSy, tileW, spikeAnim, facingLeft, zOffset)
                
                if (gameState.isExiting && alpha < 255) {
                    c.restore()
                }
                c.restore()
            }
        })

        renderList.add(object : Renderable {
            override val ySort: Float = gameState.heroPosition.y + 0.5f
            override fun render(c: Canvas) {
                var drawHeroSx = heroSx
                var drawHeroSy = heroSy
                var scale = 1f
                var rotation = 0f
                var alpha = 255

                if (gameState.isExiting) {
                    val progress = (gameState.exitAnimationTimerMs.toFloat() / 800f).coerceIn(0f, 1f)
                    
                    val saidaTx = mazeData.exitIndex % mazeData.width
                    val saidaTy = mazeData.exitIndex / mazeData.width
                    val portalSx = saidaTx * tileW + cameraX + tileW / 2f
                    val portalSy = saidaTy * tileH + cameraY + tileH / 2f
                    
                    drawHeroSx = heroSx + (portalSx - heroSx) * progress
                    drawHeroSy = heroSy + (portalSy - tileH * 0.2f - heroSy) * progress - (Math.sin(progress * Math.PI).toFloat() * tileH)
                    
                    scale = 1f - (progress * progress)
                    rotation = progress * 1080f
                    alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
                }

                if (gameState.isRespawning) {
                    val progress = (gameState.respawnTimerMs.toFloat() / 1500f).coerceIn(0f, 1f)
                    scale = 1f - progress * 0.7f
                    alpha = (255 * (1f - progress * progress)).toInt().coerceIn(0, 255)
                    rotation = progress * 360f
                }

                c.save()
                if ((gameState.isExiting || gameState.isRespawning) && alpha < 255) {
                    c.saveLayerAlpha(
                        drawHeroSx - tileW * 2f, 
                        drawHeroSy - tileH * 4f, 
                        drawHeroSx + tileW * 2f, 
                        drawHeroSy + tileH * 2f, 
                        alpha
                    )
                }
                if (gameState.isExiting) {
                    c.translate(drawHeroSx, drawHeroSy - tileH)
                    c.rotate(rotation)
                    c.scale(scale, scale)
                    c.translate(-drawHeroSx, -(drawHeroSy - tileH))
                }
                if (gameState.isRespawning) {
                    c.translate(drawHeroSx, drawHeroSy - tileH)
                    c.rotate(rotation)
                    c.scale(scale, scale)
                    c.translate(-drawHeroSx, -(drawHeroSy - tileH))
                }

                val heroAnimState = when {
                    gameState.heroIsSlowedDown -> AnimState.WALK
                    gameState.heroHasSpeedBuff -> AnimState.RUN
                    gameState.heroStoppedDurationSec > 0.05f -> AnimState.IDLE
                    else -> AnimState.WALK
                }
                characterRenderer.drawHero(
                    c, drawHeroSx, drawHeroSy, tileW, heroAnimState, gameState.heroDirection,
                    gameState.heroIsSlowedDown, gameState.heroHasSpeedBuff,
                    equippedWeapon = gameState.equippedWeapon,
                    isShooting = gameState.isShooting,
                    shootingAngle = gameState.shootingAngle
                )

                if ((gameState.isExiting || gameState.isRespawning) && alpha < 255) {
                    c.restore()
                }
                c.restore()
            }
        })

        // 7. Projéteis (Legado removido)

        // 8. Water Stream (Esguicho Contínuo)
        if (gameState.isShooting && gameState.waterStreamImpactPos != null) {
            val impact = gameState.waterStreamImpactPos!!
            
            val heroAnimState = when {
                gameState.heroIsSlowedDown -> AnimState.WALK
                gameState.heroHasSpeedBuff -> AnimState.RUN
                gameState.heroStoppedDurationSec > 0.05f -> AnimState.IDLE
                else -> AnimState.WALK
            }
            
            val (ox, oy) = characterRenderer.getGunTipPosition(
                heroSx, heroSy, tileW, heroAnimState, gameState.heroDirection,
                equippedWeapon = gameState.equippedWeapon,
                isShooting = gameState.isShooting,
                shootingAngle = gameState.shootingAngle
            )
            
            val tx = impact.x * tileW + cameraX
            val ty = impact.y * tileH + cameraY

            renderList.add(object : Renderable {
                // Bias de +0.6f para garantir que o jato fique sempre à frente do corpo do herói (que é +0.5f)
                override val ySort: Float = gameState.heroPosition.y + 0.6f
                override fun render(c: Canvas) {
                    characterRenderer.drawWaterStream(c, ox, oy, tx, ty, tileW)
                }
            })
        }

        // 9. VFX (Muzzle, Splash)
        val currentTimeVfx = System.currentTimeMillis()
        for (vfx in gameState.vfxList) {
            val vx = vfx.position.x * tileW + cameraX
            val vy = vfx.position.y * tileH + cameraY
            val elapsed = currentTimeVfx - vfx.createdAtMs
            val progress = (elapsed.toFloat() / vfx.durationMs).coerceIn(0f, 1f)
            if (progress >= 1.0f) continue

            renderList.add(object : Renderable {
                override val ySort: Float = vfx.position.y + 0.65f
                override fun render(c: Canvas) {
                    when (vfx.type) {
                        com.ericleber.joguinho.core.VfxType.WATER_SPLASH -> characterRenderer.renderWaterSplash(c, vx, vy, tileW, progress)
                        com.ericleber.joguinho.core.VfxType.WATER_JET_MUZZLE -> characterRenderer.renderWaterMuzzle(c, vx, vy, tileW, progress, vfx.angle)
                        com.ericleber.joguinho.core.VfxType.FIRE_TRAIL -> {
                            fireVfxPaint.color = Color.rgb(255, (100 + 155 * (1f - progress)).toInt(), 0)
                            fireVfxPaint.alpha = (200 * (1f - progress)).toInt()
                            val radius = (tileW * 0.3f) * (0.8f + 0.4f * sin(progress * 10).toFloat())
                            c.drawCircle(vx, vy, radius, fireVfxPaint)
                        }
                        com.ericleber.joguinho.core.VfxType.BLINK_SHADOW -> {
                            blinkVfxPaint.color = Color.MAGENTA
                            blinkVfxPaint.alpha = (150 * (1f - progress)).toInt()
                            c.drawCircle(vx, vy, tileW * 0.5f * (1f + progress), blinkVfxPaint)
                        }
                    }
                }
            })
        }
        
        // 10. Popups de Score
        for (popup in gameState.scorePopups) {
            val px = popup.position.x * tileW + cameraX
            val py = popup.position.y * tileH + cameraY - popup.offsetY
            
            renderList.add(object : Renderable {
                override val ySort: Float = popup.position.y + 1.5f // Sempre no topo das entidades
                override fun render(c: Canvas) {
                    popupPaint.textSize = (tileW * 0.45f).coerceAtLeast(18f)
                    popupPaint.color = Color.rgb(255, 235, 59) // Amarelo vibrante para score
                    popupPaint.alpha = popup.alpha
                    popupPaint.setShadowLayer(4f, 0f, 2f, Color.BLACK)
                    c.drawText("+${popup.score}", px, py, popupPaint)
                    popupPaint.clearShadowLayer()
                }
            })
        }

        // EXECUÇÃO DA RENDERIZAÇÃO ORDENADA
        renderList.sortBy { it.ySort }
        for (item in renderList) {
            item.render(canvas)
        }

        // Passo FINAL: Portal Interdimensional (substitui placa/escada)
        if (saidaTx in minX..maxX && saidaTy in minY..maxY) {
            val portalSx = saidaTx * tileW + cameraX + tileW / 2f
            val portalSy = saidaTy * tileH + cameraY + tileH / 2f
            val isLocked = gameState.monsters.any { it.isBoss && it.isActive }
            portalRenderer.render(
                canvas     = canvas,
                cx         = portalSx,
                cy         = portalSy,
                tileW      = tileW,
                tileH      = tileH,
                frameTotal = frameTotal,
                state      = gameState.portalState,
                destWorld  = gameState.portalDestWorld,
                isLocked   = isLocked
            )
        }

        // Partículas legadas (water splash, etc.)
        particleSystem.render(canvas)

        // -----------------------------------------------------------------------
        // Fase 10 - DripSystem + Particulas Ambiente + Luz Ambiente
        // -----------------------------------------------------------------------

        // Reinicializar sistemas ao trocar de bioma ou mapa
        val worldAtualFrame = gameState.currentBiomeWorld
        val mazeAtualFrame = gameState.mazeData
        
        if (worldAtualFrame != lastWorld || mazeAtualFrame != lastMaze) {
            lastWorld = worldAtualFrame
            lastMaze = mazeAtualFrame
            
            // Re-init partículas ambiente
            val cfg = AmbientParticleSystem.CONFIGS[worldAtualFrame]
            val bounds = RectF(0f, 0f, screenWidth.toFloat(), screenHeight * fracaoAreaJogo)
            ambientParticles.init(cfg, bounds)
            
            // Re-init DripSystem (fontes de goteira dependem do mapa)
            if (mazeAtualFrame != null) {
                val pal = BIOME_PALETTES[worldAtualFrame] ?: BIOME_PALETTES.values.first()
                dripSystem.init(mazeAtualFrame, pal)
            }
        }

        // Atualizar e renderizar goteiras (apenas biomas com hasDrips)
        val paletteForDrip = BIOME_PALETTES[gameState.currentBiomeWorld]
        if (paletteForDrip?.hasDrips == true) {
            dripSystem.update(deltaMs, tileWDinamico)
            dripSystem.render(canvas, cameraX, cameraY)
        }

        // Atualizar e renderizar partículas ambiente
        val gameBounds = RectF(0f, 0f, screenWidth.toFloat(), screenHeight * fracaoAreaJogo)
        ambientParticles.updateBounds(gameBounds)
        ambientParticles.update(deltaMs)
        ambientParticles.render(canvas)

        // Overlay de cor de luz ambiente (MULTIPLY) — aplicado por último,
        // antes do restore, para não afetar o HUD
        renderAmbientLight(canvas, gameState)

        // Overlay de flash vermelho durante animação de morte/respawn
        if (gameState.isRespawning) {
            val progress = (gameState.respawnTimerMs.toFloat() / 1500f).coerceIn(0f, 1f)
            val flashAlpha = ((1f - progress) * 120).toInt().coerceIn(0, 120)
            bgPaint.color = Color.argb(flashAlpha, 180, 0, 0)
            canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight * fracaoAreaJogo, bgPaint)
            bgPaint.alpha = 255
        }

        // Restaura a área total de desenho para renderizar o HUD sobreposto
        canvas.restore()

        // HUD responsivo e Pro Max
        hudRenderer.render(canvas, gameState, screenWidth / density)
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
        dripSystem.reset()
        ambientParticles.clear()
        lastWorld = null
        cameraResetPending = true
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
        portalRenderer.release()
        dripSystem.reset()
        ambientParticles.clear()
    }

    // -----------------------------------------------------------------------
    // Fase 10 — Overlay de luz ambiente (PorterDuff MULTIPLY)
    // -----------------------------------------------------------------------

    /**
     * Aplica uma cor de luz ambiente sobre toda a área do jogo via MULTIPLY.
     * Chamado APÓS todos os sprites/tiles, ANTES de canvas.restore() (preserva HUD limpo).
     *
     * A cor deve ter alpha ~0x33–0x55 para efeito sutil.
     * Biomas mais claros (Calcário) usam alpha menor (0x33).
     */
    /**
     * FASE 11: Iluminacao Pro Max.
     * Alem do overlay MULTIPLY, adiciona luzes dinamicas via RadialGradient.
     */
    private fun renderAmbientLight(canvas: Canvas, gameState: GameState) {
        val world = gameState.currentBiomeWorld
        val ambientColor = when (world) {
            BiomeWorld.ENTRANHAS -> 0x660A0A0A.toInt()
            BiomeWorld.NUCLEO_DE_FOGO -> 0x44220000.toInt()
            BiomeWorld.ABISMO_DO_VAZIO -> 0xAA000005.toInt()
            else -> 0x55050510.toInt()
        }

        // 1. Camada de Escuridão (Multiply)
        val sc = canvas.saveLayer(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), null)
        canvas.drawColor(ambientColor)
        
        // 2. Luzes Dinâmicas (Clear / Screen)
        ambientLightPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        
        // Luz do Herói
        val heroX = gameState.heroPosition.x * tileWDinamico + cameraX
        val heroY = gameState.heroPosition.y * tileHDinamico + cameraY
        renderLightSource(canvas, heroX, heroY, tileWDinamico * 4f, 0.8f)

        // Luz do Portal
        val maze = gameState.mazeData
        if (maze != null) {
            val sTx = maze.exitIndex % maze.width
            val sTy = maze.exitIndex / maze.width
            val portalX = sTx * tileWDinamico + cameraX + tileWDinamico / 2f
            val portalY = sTy * tileHDinamico + cameraY + tileHDinamico / 2f
            renderLightSource(canvas, portalX, portalY, tileWDinamico * 6f, 0.6f)
        }

        // Luz dos Monstros (Bioluminescência)
        for (m in gameState.monsters) {
            if (m.isActive) {
                val mx = m.position.x * tileWDinamico + cameraX
                val my = m.position.y * tileHDinamico + cameraY
                renderLightSource(canvas, mx, my, tileWDinamico * 2f, 0.4f)
            }
        }

        ambientLightPaint.xfermode = null
        canvas.restoreToCount(sc)
    }

    private fun renderLightSource(canvas: Canvas, x: Float, y: Float, radius: Float, intensity: Float) {
        val gradient = android.graphics.RadialGradient(
            x, y, radius,
            intArrayOf(Color.argb((255 * intensity).toInt(), 255, 255, 255), Color.TRANSPARENT),
            null, android.graphics.Shader.TileMode.CLAMP
        )
        ambientLightPaint.shader = gradient
        canvas.drawCircle(x, y, radius, ambientLightPaint)
        ambientLightPaint.shader = null
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
        val screenPos = IsometricProjection.worldToScreen(saidaTx.toFloat(), saidaTy.toFloat(), tileW, tileH)
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

    // =========================================================================
    // FASE 10: SISTEMAS DE IMERSÃO AVANÇADA
    // =========================================================================

    /**
     * Renderiza o fundo de céu (Lua/Estrelas ou Sol) para biomas abertos.
     */
    private fun renderSky(canvas: Canvas, gameState: GameState) {
        val isMoon = gameState.currentBiomeWorld == com.ericleber.joguinho.biome.BiomeWorld.BASE_LUNAR
        val isForest = gameState.currentBiomeWorld == com.ericleber.joguinho.biome.BiomeWorld.FLORESTA_DE_ARVORES
        
        // Gradiente de fundo
        val topColor = if (isMoon) Color.rgb(5, 5, 20) else Color.rgb(100, 180, 255)
        val botColor = if (isMoon) Color.rgb(20, 20, 40) else Color.rgb(180, 220, 255)
        
        // Desenhar estrelas se for Lua ou Noite
        if (isMoon) {
            val starRng = java.util.Random(gameState.mazeData?.seed ?: 0L)
            bgPaint.color = Color.WHITE
            for (i in 0..100) {
                val sx = starRng.nextFloat() * screenWidth
                val sy = starRng.nextFloat() * screenHeight
                val size = 1f + starRng.nextFloat() * 2f
                val alpha = (150 + sin(frameTotal * 0.05f + i) * 105).toInt()
                bgPaint.alpha = alpha
                canvas.drawRect(sx, sy, sx + size, sy + size, bgPaint)
            }
            bgPaint.alpha = 255
        }
    }

    /**
     * Implementa as micro-interações de "Soul Tiles" (Requisito 10.5).
     * Reage a projéteis e proximidade do jogador.
     */
    private fun renderSoulTileInteraction(
        c: Canvas, sx: Float, sy: Float, tw: Float, th: Float,
        tx: Int, ty: Int, gameState: GameState
    ) {
        val world = gameState.currentBiomeWorld
        val isCrystal = world.name.contains("MINA") || world.name.contains("MAGIA") || world.name.contains("ENTRANHAS")
        val isMoss = world.name.contains("JARDIM") || world.name.contains("FLORESTA")
        
        // 1. Proximidade do Herói (Solta partículas de musgo)
        if (isMoss) {
            val dist = gameState.heroPosition.dist(com.ericleber.joguinho.core.Position(tx.toFloat(), ty.toFloat()))
            if (dist < 1.2f) {
                if (frameTotal % 15 == 0L) {
                    particleSystem.emit(sx + tw/2f, sy + th/2f, 2, mossConfig)
                }
            }
        }
        
        // 2. Impacto de Jato D'água (Faz o cristal brilhar)
        if (isCrystal && gameState.isShooting) {
            val impactPos = gameState.waterStreamImpactPos
            if (impactPos != null && impactPos.ix == tx && impactPos.iy == ty) {
                // Efeito de brilho branco intenso
                bgPaint.color = Color.WHITE
                bgPaint.alpha = (100 + sin(frameTotal * 0.2f) * 100).toInt()
                c.drawRect(sx, sy, sx + tw, sy + th, bgPaint)
                bgPaint.alpha = 255
            }
        }
    }

    private fun renderLayeredBackground(
        canvas: Canvas,
        palette: BiomePalette,
        gameState: GameState
    ) {
        val gameHeight = screenHeight * fracaoAreaJogo
        val bgCategory = getBgCategory(gameState.currentBiomeWorld)
        val time = System.currentTimeMillis()

        // ---------------------------------------------------------------------
        // CAMADA -3: Distante, quase estática (Fator de movimento: 0.1f)
        // ---------------------------------------------------------------------
        if (bgCategory != BgCategory.VOID) {
            val ox3 = cameraX * 0.1f
            val oy3 = cameraY * 0.1f
            val spacingX = 350f
            val spacingY = 400f

            val startCellX = ((-ox3) / spacingX).toInt() - 1
            val endCellX = ((screenWidth - ox3) / spacingX).toInt() + 1
            val startCellY = ((-oy3) / spacingY).toInt() - 1
            val endCellY = ((gameHeight - oy3) / spacingY).toInt() + 1

            for (cx in startCellX..endCellX) {
                for (cy in startCellY..endCellY) {
                    val elemSeed = cx * 101 + cy * 73
                    val rng = java.util.Random(elemSeed.toLong())

                    val bx = cx * spacingX + ox3 + rng.nextFloat() * 120f
                    val by = cy * spacingY + oy3 + rng.nextFloat() * 120f

                    bgLayerPaint.reset()
                    bgLayerPaint.isAntiAlias = true
                    bgLayerPaint.style = Paint.Style.FILL

                    when (bgCategory) {
                        BgCategory.CAVERN -> {
                            bgLayerPaint.color = escurecer(palette.wallColor, 0.7f)
                            bgLayerPaint.alpha = 140

                            val w = 80f + rng.nextFloat() * 70f
                            val h = 160f + rng.nextFloat() * 120f

                            bgPath.reset()
                            bgPath.moveTo(bx - w / 2f, by)
                            bgPath.lineTo(bx, by + h)
                            bgPath.lineTo(bx + w / 2f, by)
                            bgPath.close()
                            canvas.drawPath(bgPath, bgLayerPaint)
                        }
                        BgCategory.FOREST -> {
                            bgLayerPaint.color = escurecer(palette.wallColor, 0.6f)
                            bgLayerPaint.alpha = 120
                            bgLayerPaint.style = Paint.Style.STROKE
                            bgLayerPaint.strokeWidth = 25f + rng.nextFloat() * 20f
                            bgLayerPaint.strokeCap = Paint.Cap.ROUND

                            bgPath.reset()
                            bgPath.moveTo(bx, by - 150f)
                            bgPath.quadTo(bx + 120f, by + 100f, bx - 50f, by + 300f)
                            canvas.drawPath(bgPath, bgLayerPaint)
                        }
                        BgCategory.VOLCANIC -> {
                            val colW = 70f + rng.nextFloat() * 40f
                            bgLayerPaint.color = Color.rgb(40, 20, 20)
                            bgLayerPaint.alpha = 200
                            canvas.drawRect(bx - colW/2f, 0f, bx + colW/2f, gameHeight, bgLayerPaint)

                            bgLayerPaint.color = Color.rgb(255, 60, 0)
                            bgLayerPaint.alpha = 100 + (sin(time * 0.002 + elemSeed) * 50).toInt()
                            bgLayerPaint.strokeWidth = 6f
                            bgLayerPaint.style = Paint.Style.STROKE
                            bgPath.reset()
                            bgPath.moveTo(bx, 0f)
                            bgPath.lineTo(bx - 10f, gameHeight * 0.3f)
                            bgPath.lineTo(bx + 10f, gameHeight * 0.6f)
                            bgPath.lineTo(bx, gameHeight)
                            canvas.drawPath(bgPath, bgLayerPaint)
                        }
                        BgCategory.CRYSTAL -> {
                            bgLayerPaint.color = palette.crystalColor
                            bgLayerPaint.alpha = 70

                            val cw = 60f + rng.nextFloat() * 60f
                            val ch = 120f + rng.nextFloat() * 100f

                            bgPath.reset()
                            bgPath.moveTo(bx, by + ch)
                            bgPath.lineTo(bx - cw/2f, by + ch * 0.3f)
                            bgPath.lineTo(bx, by)
                            bgPath.lineTo(bx + cw/2f, by + ch * 0.3f)
                            bgPath.close()
                            canvas.drawPath(bgPath, bgLayerPaint)
                        }
                        else -> {}
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // CAMADA -2: Intermediária, movimento lento (Fator de movimento: 0.25f)
        // ---------------------------------------------------------------------
        val ox2 = cameraX * 0.25f
        val oy2 = cameraY * 0.25f
        val spacingX2 = 250f
        val spacingY2 = 300f

        val startCellX2 = ((-ox2) / spacingX2).toInt() - 1
        val endCellX2 = ((screenWidth - ox2) / spacingX2).toInt() + 1
        val startCellY2 = ((-oy2) / spacingY2).toInt() - 1
        val endCellY2 = ((gameHeight - oy2) / spacingY2).toInt() + 1

        for (cx in startCellX2..endCellX2) {
            for (cy in startCellY2..endCellY2) {
                val elemSeed = cx * 79 + cy * 53
                val rng = java.util.Random(elemSeed.toLong())

                val bx = cx * spacingX2 + ox2 + rng.nextFloat() * 80f
                val by = cy * spacingY2 + oy2 + rng.nextFloat() * 80f

                bgLayerPaint.reset()
                bgLayerPaint.isAntiAlias = true
                bgLayerPaint.style = Paint.Style.FILL

                when (bgCategory) {
                    BgCategory.CAVERN -> {
                        bgLayerPaint.color = escurecer(palette.wallColor, 0.5f)
                        bgLayerPaint.alpha = 180

                        val size = 40f + rng.nextFloat() * 30f
                        bgPath.reset()
                        for (i in 0 until 8) {
                            val angle = i * (Math.PI * 2 / 8)
                            val r = size * (0.8f + rng.nextFloat() * 0.4f)
                            val px = bx + kotlin.math.cos(angle).toFloat() * r
                            val py = by + sin(angle).toFloat() * r
                            if (i == 0) bgPath.moveTo(px, py) else bgPath.lineTo(px, py)
                        }
                        bgPath.close()
                        canvas.drawPath(bgPath, bgLayerPaint)
                    }
                    BgCategory.FOREST -> {
                        bgLayerPaint.color = escurecer(palette.wallColor, 0.4f)
                        bgLayerPaint.alpha = 160

                        val stemW = 10f + rng.nextFloat() * 8f
                        val stemH = 40f + rng.nextFloat() * 30f
                        val capR = 25f + rng.nextFloat() * 20f

                        canvas.drawRect(bx - stemW/2f, by, bx + stemW/2f, by + stemH, bgLayerPaint)
                        bgPath.reset()
                        bgPath.arcTo(RectF(bx - capR, by - capR, bx + capR, by + capR), 180f, 180f)
                        bgPath.close()
                        canvas.drawPath(bgPath, bgLayerPaint)
                    }
                    BgCategory.VOLCANIC -> {
                        bgLayerPaint.color = Color.rgb(90, 30, 20)
                        bgLayerPaint.alpha = 220
                        val size = 20f + rng.nextFloat() * 15f
                        canvas.drawCircle(bx, by, size, bgLayerPaint)

                        bgLayerPaint.color = Color.rgb(255, 120, 0)
                        bgLayerPaint.alpha = 150 + (sin(time * 0.003 + elemSeed) * 80).toInt()
                        canvas.drawCircle(bx, by, size * 0.4f, bgLayerPaint)
                    }
                    BgCategory.CRYSTAL -> {
                        bgLayerPaint.color = clarear(palette.crystalColor, 0.2f)
                        bgLayerPaint.alpha = 140

                        val size = 25f + rng.nextFloat() * 20f
                        bgPath.reset()
                        bgPath.moveTo(bx, by - size)
                        bgPath.lineTo(bx + size * 0.6f, by)
                        bgPath.lineTo(bx, by + size)
                        bgPath.lineTo(bx - size * 0.6f, by)
                        bgPath.close()
                        canvas.drawPath(bgPath, bgLayerPaint)
                    }
                    BgCategory.VOID -> {
                        bgLayerPaint.color = Color.rgb(30, 20, 50)
                        bgLayerPaint.alpha = 100
                        val size = 30f + rng.nextFloat() * 30f
                        if (elemSeed % 2 == 0) {
                            canvas.drawCircle(bx, by, size, bgLayerPaint)
                        } else {
                            canvas.drawRect(bx - size, by - size, bx + size, by + size, bgLayerPaint)
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // CAMADA -1: Próxima, movimento rápido (Fator de movimento: 0.45f)
        // ---------------------------------------------------------------------
        val ox1 = cameraX * 0.45f
        val oy1 = cameraY * 0.45f
        val spacingX1 = 150f
        val spacingY1 = 150f

        val startCellX1 = ((-ox1) / spacingX1).toInt() - 1
        val endCellX1 = ((screenWidth - ox1) / spacingX1).toInt() + 1
        val startCellY1 = ((-oy1) / spacingY1).toInt() - 1
        val endCellY1 = ((gameHeight - oy1) / spacingY1).toInt() + 1

        for (cx in startCellX1..endCellX1) {
            for (cy in startCellY1..endCellY1) {
                val elemSeed = cx * 67 + cy * 43
                val rng = java.util.Random(elemSeed.toLong())

                val speedFactorX = 0.01f + rng.nextFloat() * 0.02f
                val speedFactorY = -0.015f - rng.nextFloat() * 0.02f
                
                val driftX = (time * speedFactorX) % spacingX1
                val driftY = (time * speedFactorY) % spacingY1

                val bx = cx * spacingX1 + ox1 + rng.nextFloat() * 50f + driftX
                val by = cy * spacingY1 + oy1 + rng.nextFloat() * 50f + driftY

                val finalX = (bx % screenWidth + screenWidth) % screenWidth
                val finalY = (by % gameHeight + gameHeight) % gameHeight

                bgLayerPaint.reset()
                bgLayerPaint.isAntiAlias = true
                bgLayerPaint.style = Paint.Style.FILL

                when (bgCategory) {
                    BgCategory.CAVERN -> {
                        bgLayerPaint.color = palette.backgroundColor
                        bgLayerPaint.alpha = 25 + (sin(time * 0.001 + elemSeed) * 10).toInt()
                        canvas.drawCircle(finalX, finalY, 60f + rng.nextFloat() * 40f, bgLayerPaint)
                    }
                    BgCategory.FOREST -> {
                        bgLayerPaint.color = Color.rgb(120, 240, 100)
                        bgLayerPaint.alpha = 100 + (sin(time * 0.004 + elemSeed) * 80).toInt()
                        canvas.drawCircle(finalX, finalY, 3f + rng.nextFloat() * 3f, bgLayerPaint)
                    }
                    BgCategory.VOLCANIC -> {
                        val tColorVal = (sin(time * 0.005 + elemSeed) * 50f + 200f).toInt()
                        bgLayerPaint.color = Color.rgb(255, tColorVal.coerceIn(100, 255), 0)
                        bgLayerPaint.alpha = 150 + (sin(time * 0.006 + elemSeed) * 100).toInt()
                        canvas.drawCircle(finalX, finalY, 2f + rng.nextFloat() * 2.5f, bgLayerPaint)
                    }
                    BgCategory.CRYSTAL -> {
                        bgLayerPaint.color = Color.WHITE
                        bgLayerPaint.alpha = 120 + (sin(time * 0.005 + elemSeed) * 80).toInt()
                        
                        val sz = 4f + rng.nextFloat() * 4f
                        bgPath.reset()
                        bgPath.moveTo(finalX, finalY - sz)
                        bgPath.lineTo(finalX + sz * 0.7f, finalY)
                        bgPath.lineTo(finalX, finalY + sz)
                        bgPath.lineTo(finalX - sz * 0.7f, finalY)
                        bgPath.close()
                        canvas.drawPath(bgPath, bgLayerPaint)
                    }
                    BgCategory.VOID -> {
                        bgLayerPaint.color = Color.rgb(10, 5, 20)
                        bgLayerPaint.alpha = 40 + (sin(time * 0.001 + elemSeed) * 20).toInt()
                        canvas.drawCircle(finalX, finalY, 80f + rng.nextFloat() * 60f, bgLayerPaint)
                    }
                }
            }
        }
    }

    private fun getBgCategory(world: com.ericleber.joguinho.biome.BiomeWorld): BgCategory {
        return when (world) {
            com.ericleber.joguinho.biome.BiomeWorld.ENTRANHAS,
            com.ericleber.joguinho.biome.BiomeWorld.MINAS_RIQUEZAS,
            com.ericleber.joguinho.biome.BiomeWorld.RUINAS_ANCESTRAIS -> BgCategory.CAVERN
            
            com.ericleber.joguinho.biome.BiomeWorld.FLORESTA_DE_ARVORES,
            com.ericleber.joguinho.biome.BiomeWorld.JARDIM_PROFUNDO,
            com.ericleber.joguinho.biome.BiomeWorld.SUPERFICIE_ABERTA -> BgCategory.FOREST
            
            com.ericleber.joguinho.biome.BiomeWorld.NUCLEO_DE_FOGO -> BgCategory.VOLCANIC
            
            com.ericleber.joguinho.biome.BiomeWorld.REINO_DA_MAGIA,
            com.ericleber.joguinho.biome.BiomeWorld.ABISMOS_AQUATICOS -> BgCategory.CRYSTAL
            
            com.ericleber.joguinho.biome.BiomeWorld.ABISMO_DO_VAZIO,
            com.ericleber.joguinho.biome.BiomeWorld.BASE_LUNAR -> BgCategory.VOID
        }
    }

    private fun clarear(color: Int, factor: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt()
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt()
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun escurecer(color: Int, factor: Float): Int {
        val r = (Color.red(color) * (1f - factor)).toInt()
        val g = (Color.green(color) * (1f - factor)).toInt()
        val b = (Color.blue(color) * (1f - factor)).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}


/**
 * Extensões do GameState para o Renderer.
 * (Mantidas para compatibilidade — os campos reais estão em GameState)
 */
