package com.ericleber.joguinho.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.ericleber.joguinho.biome.Biome
import com.ericleber.joguinho.biome.BIOME_PALETTES
import com.ericleber.joguinho.biome.BiomePalette
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
    private var lastBiome: Biome? = null
    /** Mapa do último frame — usado para re-init do DripSystem. */
    private var lastMaze: MazeData? = null

    var cameraX: Float = 0f
    var cameraY: Float = 0f
    var screenWidth: Int = 0
    var screenHeight: Int = 0
    var density: Float = 1f
    private var lastFrameTimeMs: Long = 0L

    private val bgPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
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

    fun recalcularTile(mapWidth: Int, mapHeight: Int) {
        if (screenWidth <= 0 || screenHeight <= 0) return
        
        // Ajusta o tamaço mínimo baseado na densidade da tela
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

        // Câmera segue o herói com interpolação suave (opcional, aqui mantemos direto)
        val heroSx = heroWorldX * tileSize
        val heroSy = heroWorldY * tileSize
        
        // Centraliza o herói na tela, mas respeita os limites do mapa
        cameraX = (screenWidth / 2f - heroSx).coerceIn(screenWidth - larguraMapa, 0f)
        cameraY = (alturaA / 2f - heroSy).coerceIn(alturaA - alturaMapa, 0f)
        
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
        if (mazeAtual != null) recalcularTile(mazeAtual.width, mazeAtual.height)

        val tileW = tileWDinamico
        val tileH = tileHDinamico

        val basePalette = BIOME_PALETTES[gameState.currentBiome]
            ?: BIOME_PALETTES.values.first()
        val palette = com.ericleber.joguinho.biome.applyDepthHueShiftToPalette(basePalette, gameState.floorNumber)

        spriteCache.currentBiome = gameState.currentBiome.name
        tileRenderer.setBiome(gameState.currentBiome)

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

        // --- FASE 11: Background Walls (Camada de Profundidade 0) ---
        // Renderiza antes do clipRect para garantir cobertura total ou parallax
        val bgMaze = gameState.mazeData
        if (bgMaze != null) {
            val visibleTilesForBg = getVisibleTiles(bgMaze, tileWDinamico, tileHDinamico)
            renderBackgroundWalls(canvas, visibleTilesForBg, tileWDinamico, tileHDinamico, palette, gameState)
        }

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

        // Passo 1: Chão (Base Estática)
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

        // Passo 2: Decorativos (Base Estática)
        for (ty in minY..maxY) {
            for (tx in minX..maxX) {
                val idx = ty * mazeData.width + tx
                if (idx < 0 || idx >= mazeData.tiles.size) continue
                if (mazeData.tiles[idx] != 0) continue
                if ((tx == entradaTx && ty == entradaTy) || (tx == saidaTx && ty == saidaTy)) continue
                
                // Desativar decorativos para o Bioma Úmido
                if (gameState.currentBiome.name.contains("UMIDO") || gameState.currentBiome.name.contains("PANTANO")) continue

                val decorSeed = (tx * 31 + ty * 17 + mazeData.seed.toInt()) % 15
                if (decorSeed != 0) continue
                val variant = (tx + ty) % 4
                val sx = tx * tileW + cameraX
                val sy = ty * tileH + cameraY
                val decorKey = "biome_${gameState.currentBiome.name}_floor_${gameState.floorNumber}_decor_$variant"
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
                    "biome_${gameState.currentBiome.name}_tree_variant_${(tx + ty) % 3}"
                } else {
                    "biome_${gameState.currentBiome.name}_wall_mask_$mask"
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
                        c.drawBitmap(wallBitmap, sx, drawY, null)
                        
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
            val appearance = MonsterAppearance(bodyColor, eyeColor, finalScale, seed and 0x3, seed shr 4 and 0x3, monster.isBoss, isHit)

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
                var drawSpikeSx = spikeSx
                var drawSpikeSy = spikeSy
                var scale = 1f
                var rotation = 0f
                var alpha = 255
                
                if (gameState.isExiting) {
                    val progressBase = (gameState.exitAnimationTimerMs.toFloat() / 800f)
                    val progress = ((progressBase - 0.2f) * 1.25f).coerceIn(0f, 1f)
                    
                    if (progress > 0f) {
                        val saidaTx = mazeData.exitIndex % mazeData.width
                        val saidaTy = mazeData.exitIndex / mazeData.width
                        val portalSx = saidaTx * tileW + cameraX + tileW / 2f
                        val portalSy = saidaTy * tileH + cameraY + tileH / 2f
                        
                        drawSpikeSx = spikeSx + (portalSx - spikeSx) * progress
                        drawSpikeSy = spikeSy + (portalSy - tileH * 0.2f - spikeSy) * progress - (Math.sin(progress * Math.PI).toFloat() * tileH)
                        
                        scale = 1f - (progress * progress)
                        rotation = progress * 1080f
                        alpha = (255 * (1f - progress)).toInt().coerceIn(0, 255)
                    }
                }
                
                val facingLeft = when (gameState.heroDirection) {
                    com.ericleber.joguinho.core.Direction.WEST, com.ericleber.joguinho.core.Direction.NORTH_WEST, com.ericleber.joguinho.core.Direction.SOUTH_WEST -> true
                    else -> false
                }
                
                c.save()
                if (gameState.isExiting && alpha < 255) {
                    c.saveLayerAlpha(
                        drawSpikeSx - tileW * 2f, 
                        drawSpikeSy - tileH * 4f, 
                        drawSpikeSx + tileW * 2f, 
                        drawSpikeSy + tileH * 2f, 
                        alpha
                    )
                }
                if (gameState.isExiting) {
                    c.translate(drawSpikeSx, drawSpikeSy - tileH * 0.5f)
                    c.rotate(rotation)
                    c.scale(scale, scale)
                    c.translate(-drawSpikeSx, -(drawSpikeSy - tileH * 0.5f))
                }
                
                characterRenderer.drawDog(c, drawSpikeSx, drawSpikeSy, tileW, AnimState.WALK, facingLeft)
                
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

                c.save()
                if (gameState.isExiting && alpha < 255) {
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

                if (gameState.isExiting && alpha < 255) {
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
        val biomeAtualFrame = gameState.currentBiome
        val mazeAtualFrame = gameState.mazeData
        
        if (biomeAtualFrame != lastBiome || mazeAtualFrame != lastMaze) {
            lastBiome = biomeAtualFrame
            lastMaze = mazeAtualFrame
            
            // Re-init partículas ambiente
            val cfg = AmbientParticleSystem.CONFIGS[biomeAtualFrame]
            val bounds = RectF(0f, 0f, screenWidth.toFloat(), screenHeight * fracaoAreaJogo)
            ambientParticles.init(cfg, bounds)
            
            // Re-init DripSystem (fontes de goteira dependem do mapa)
            if (mazeAtualFrame != null) {
                val pal = BIOME_PALETTES[biomeAtualFrame] ?: BIOME_PALETTES.values.first()
                dripSystem.init(mazeAtualFrame, pal)
            }
        }

        // Atualizar e renderizar goteiras (apenas biomas com hasDrips)
        val paletteForDrip = BIOME_PALETTES[gameState.currentBiome]
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
        lastBiome = null
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
        val biome = gameState.currentBiome
        val ambientColor = when (biome) {
            Biome.MINA_ABANDONADA -> 0x660A0A0A.toInt()
            Biome.CAVERNA_DE_LAVA -> 0x44220000.toInt()
            Biome.ABISMO_PROFUNDO -> 0xAA000005.toInt()
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
        val biome = gameState.currentBiome.name
        val isCrystal = biome.contains("MINA") || biome.contains("MAGIA")
        val isMoss = biome.contains("JARDIM") || biome.contains("FLORESTA")
        
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

    /**
     * FASE 11: Renderiza as paredes de fundo com parallax leve.
     */
    private fun renderBackgroundWalls(
        canvas: Canvas,
        visibleTiles: List<Pair<Int, Int>>,
        tileW: Float, tileH: Float,
        palette: BiomePalette,
        gameState: GameState
    ) {
        val bgCameraX = cameraX * 0.4f // Parallax: move-se menos que a câmera principal
        val bgCameraY = cameraY * 0.4f
        
        bgPaint.color = escurecer(palette.wallColor, 0.5f) // Mais escura para profundidade
        
        val maze = gameState.mazeData ?: return
        for (tile in visibleTiles) {
            val tx = tile.first
            val ty = tile.second
            
            if (ty * maze.width + tx < maze.tiles.size && maze.tiles[ty * maze.width + tx] == 1) {
                val screenPos = IsometricProjection.worldToScreen(tx.toFloat(), ty.toFloat(), tileW, tileH)
                canvas.drawRect(
                    screenPos.x + bgCameraX, 
                    screenPos.y + bgCameraY, 
                    screenPos.x + bgCameraX + tileW, 
                    screenPos.y + bgCameraY + tileH, 
                    bgPaint
                )
            }
        }
    }

    private fun escurecer(color: Int, factor: Float): Int {
        val r = (Color.red(color) * (1f - factor)).toInt()
        val g = (Color.green(color) * (1f - factor)).toInt()
        val b = (Color.blue(color) * (1f - factor)).toInt()
        return Color.rgb(r, g, b)
    }
}

/**
 * Extensões do GameState para o Renderer.
 * (Mantidas para compatibilidade — os campos reais estão em GameState)
 */
