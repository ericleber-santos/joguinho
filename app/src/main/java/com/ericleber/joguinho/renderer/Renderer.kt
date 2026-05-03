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
        val heroSx = heroWorldX * tileSize + tileSize / 2f
        val heroSy = heroWorldY * tileSize + tileSize / 2f
        
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
                renderList.add(object : Renderable {
                    override val ySort: Float = ty + 1.0f
                    override fun render(c: Canvas) {
                        tileRenderer.renderWallTile(c, sx, sy, tileW, tileH, palette, tx, ty)
                    }
                })
            }
        }

        // 2. Itens (Power-ups)
        for (item in gameState.items) {
            if (!item.isActive) continue
            val sx = item.position.x * tileW + cameraX + tileW / 2f
            val sy = item.position.y * tileH + cameraY + tileH / 2f
            renderList.add(object : Renderable {
                override val ySort: Float = item.position.y + 0.4f // Levemente atrás do pé
                override fun render(c: Canvas) {
                    characterRenderer.renderBanana(c, sx, sy, heroAnimFrame, tileW)
                }
            })
        }

        // 3. Armadilhas
        val trapPaint = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
        val trapPath = Path()
        val biomeName = gameState.currentBiome.name
        for (trap in gameState.traps) {
            val ttx = trap.position.x
            val tty = trap.position.y
            if (ttx.toInt() !in minX..maxX || tty.toInt() !in minY..maxY) continue
            val sx = ttx * tileW + cameraX
            val sy = tty * tileH + cameraY
            val cx = sx + tileW / 2f
            val cy = sy + tileH / 2f
            val phase = (System.currentTimeMillis() % 2000L) / 2000f
            val anim = if (!trap.isActivated) (sin(phase * Math.PI * 2).toFloat() * 0.5f + 0.5f) else 1f

            renderList.add(object : Renderable {
                override val ySort: Float = tty + 0.8f // Quase na base do tile
                override fun render(c: Canvas) {
                    when {
                        // JARDIM/FLORESTA: Cobra que dá bote
                        biomeName.contains("JARDIM") || biomeName.contains("FLORESTA") || biomeName.contains("PLANTACAO") || biomeName.contains("RAIZES") || biomeName.contains("POMAR") -> {
                            trapPaint.color = if (trap.isActivated) Color.rgb(200, 50, 30) else Color.rgb(50, 150, 50)
                            val bodyR = tileW * 0.30f
                            c.drawCircle(cx, cy + tileH * 0.1f, bodyR, trapPaint)
                            trapPaint.color = if (trap.isActivated) Color.rgb(160, 30, 20) else Color.rgb(30, 110, 30)
                            c.drawCircle(cx, cy + tileH * 0.1f, bodyR * 0.7f, trapPaint)
                            val headY = cy - tileH * 0.15f - (anim * tileH * 0.25f)
                            trapPaint.color = if (trap.isActivated) Color.rgb(220, 60, 30) else Color.rgb(60, 170, 60)
                            c.drawOval(RectF(cx - tileW * 0.15f, headY - tileH * 0.1f, cx + tileW * 0.15f, headY + tileH * 0.1f), trapPaint)
                            trapPaint.color = Color.YELLOW
                            c.drawRect(cx - tileW * 0.08f, headY - tileH * 0.04f, cx - tileW * 0.03f, headY + tileH * 0.02f, trapPaint)
                            c.drawRect(cx + tileW * 0.03f, headY - tileH * 0.04f, cx + tileW * 0.08f, headY + tileH * 0.02f, trapPaint)
                            if (anim > 0.5f) {
                                trapPaint.color = Color.RED
                                trapPaint.style = Paint.Style.STROKE; trapPaint.strokeWidth = 1.5f
                                c.drawLine(cx, headY + tileH * 0.08f, cx - tileW * 0.06f, headY + tileH * 0.18f, trapPaint)
                                c.drawLine(cx, headY + tileH * 0.08f, cx + tileW * 0.06f, headY + tileH * 0.18f, trapPaint)
                                trapPaint.style = Paint.Style.FILL
                            }
                        }
                        // VULCÂNICA: Poça de lava
                        biomeName.contains("VULCANICO") || biomeName.contains("LAVA") || biomeName.contains("FOGO") -> {
                            trapPaint.color = Color.rgb(200, 60, 10)
                            c.drawOval(RectF(sx + tileW * 0.1f, sy + tileH * 0.2f, sx + tileW * 0.9f, sy + tileH * 0.85f), trapPaint)
                            trapPaint.color = Color.rgb(255, 150, 30)
                            c.drawOval(RectF(sx + tileW * 0.25f, sy + tileH * 0.35f, sx + tileW * 0.75f, sy + tileH * 0.7f), trapPaint)
                            trapPaint.color = Color.rgb(255, 200, 50)
                            val bubbleY = cy - tileH * 0.1f * anim
                            c.drawCircle(cx - tileW * 0.1f, bubbleY, tileW * 0.06f, trapPaint)
                            c.drawCircle(cx + tileW * 0.15f, bubbleY - tileH * 0.05f, tileW * 0.04f, trapPaint)
                        }
                        // DARDO: Ruínas
                        biomeName.contains("RUINA") || biomeName.contains("TEMPLO") -> {
                            trapPaint.color = Color.rgb(40, 30, 20)
                            c.drawRect(sx + tileW * 0.05f, cy - tileH * 0.12f, sx + tileW * 0.2f, cy + tileH * 0.12f, trapPaint)
                            val dartLen = tileW * 0.6f * anim
                            trapPaint.color = if (trap.isActivated) Color.rgb(180, 40, 40) else Color.rgb(120, 100, 60)
                            c.drawRect(sx + tileW * 0.2f, cy - tileH * 0.03f, sx + tileW * 0.2f + dartLen, cy + tileH * 0.03f, trapPaint)
                        }
                        // MINA (default): Espinhos
                        else -> {
                            trapPaint.color = Color.rgb(100, 90, 80)
                            c.drawRect(sx + tileW * 0.05f, sy + tileH * 0.7f, sx + tileW * 0.95f, sy + tileH * 0.95f, trapPaint)
                            trapPaint.color = if (trap.isActivated) Color.rgb(200, 50, 30) else Color.rgb(200, 170, 50)
                            val spikeH = tileH * 0.6f * anim
                            val numSpikes = 3
                            val sw = tileW * 0.85f / numSpikes
                            for (i in 0 until numSpikes) {
                                val bx = sx + tileW * 0.075f + sw * i
                                val by = sy + tileH * 0.7f
                                trapPath.reset()
                                trapPath.moveTo(bx, by); trapPath.lineTo(bx + sw, by); trapPath.lineTo(bx + sw / 2f, by - spikeH); trapPath.close()
                                c.drawPath(trapPath, trapPaint)
                            }
                        }
                    }
                }
            })
        }

        // 4. Elementos de Sobrevivência (Pilares, Caixas)
        for (elem in gameState.survivalElements) {
            if (!elem.active && elem.type != com.ericleber.joguinho.core.SurvivalElementType.STONE_PILLAR) continue
            val sx = elem.position.x * tileW + cameraX
            val sy = elem.position.y * tileH + cameraY
            val cx = sx + tileW / 2f
            val cy = sy + tileH / 2f

            renderList.add(object : Renderable {
                override val ySort: Float = elem.position.y + 0.9f
                override fun render(c: Canvas) {
                    when (elem.type) {
                        com.ericleber.joguinho.core.SurvivalElementType.ICE_TORCH -> {
                            trapPaint.color = Color.rgb(100, 150, 255)
                            c.drawRect(cx - tileW * 0.1f, cy - tileH * 0.4f, cx + tileW * 0.1f, cy, trapPaint)
                        }
                        com.ericleber.joguinho.core.SurvivalElementType.STONE_PILLAR -> {
                            trapPaint.color = if (elem.active) Color.rgb(80, 80, 80) else Color.rgb(100, 100, 100)
                            val pH = if (elem.active) tileH * 1.4f else tileH * 0.4f
                            c.drawRect(sx + tileW * 0.1f, sy + tileH * 0.9f - pH, sx + tileW * 0.9f, sy + tileH * 0.9f, trapPaint)
                        }
                        com.ericleber.joguinho.core.SurvivalElementType.PUSHABLE_BOX -> {
                            trapPaint.color = Color.rgb(139, 69, 19)
                            c.drawRect(sx + tileW * 0.2f, sy + tileH * 0.2f, sx + tileW * 0.8f, sy + tileH * 0.8f, trapPaint)
                        }
                        else -> {}
                    }
                }
            })
        }

        // 5. Monstros (+0.5f para ySort nos pés)
        for (monster in gameState.monsters) {
            if (!monster.isActive) continue
            val mx = monster.position.x * tileW + cameraX + tileW / 2f
            val my = monster.position.y * tileH + cameraY + tileH / 2f
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
        val heroSx = gameState.heroPosition.x * tileW + cameraX + tileW / 2f
        val heroSy = gameState.heroPosition.y * tileH + cameraY + tileH / 2f
        val spikeSx = gameState.spikePosition.x * tileW + cameraX + tileW / 2f
        val spikeSy = gameState.spikePosition.y * tileH + cameraY + tileH / 2f

        renderList.add(object : Renderable {
            override val ySort: Float = gameState.spikePosition.y + 0.5f
            override fun render(c: Canvas) {
                val facingLeft = when (gameState.heroDirection) {
                    com.ericleber.joguinho.core.Direction.WEST, com.ericleber.joguinho.core.Direction.NORTH_WEST, com.ericleber.joguinho.core.Direction.SOUTH_WEST -> true
                    else -> false
                }
                characterRenderer.drawDog(c, spikeSx, spikeSy, tileW, AnimState.WALK, facingLeft)
            }
        })

        renderList.add(object : Renderable {
            override val ySort: Float = gameState.heroPosition.y + 0.5f
            override fun render(c: Canvas) {
                var drawHeroSy = heroSy
                if (gameState.isExiting) {
                    val progress = (gameState.exitAnimationTimerMs.toFloat() / 800f).coerceIn(0f, 1f)
                    drawHeroSy -= progress * tileH * 1.2f
                }
                val heroAnimState = when {
                    gameState.heroIsSlowedDown -> AnimState.WALK
                    gameState.heroHasSpeedBuff -> AnimState.RUN
                    gameState.heroStoppedDurationSec > 0.05f -> AnimState.IDLE
                    else -> AnimState.WALK
                }
                characterRenderer.drawHero(c, heroSx, drawHeroSy, tileW, heroAnimState, gameState.heroDirection, gameState.heroIsSlowedDown, gameState.heroHasSpeedBuff)
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
                heroSx, heroSy, tileW, heroAnimState, gameState.heroDirection
            )
            
            val tx = impact.x * tileW + cameraX + tileW / 2f
            val ty = impact.y * tileH + cameraY + tileH / 2f

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
            val vx = vfx.position.x * tileW + cameraX + tileW / 2f
            val vy = vfx.position.y * tileH + cameraY + tileH / 2f
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
            val px = popup.position.x * tileW + cameraX + tileW / 2f
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

        // Passo FINAL: Elementos de HUD fixos (Z-Index Máximo)
        if (saidaTx in minX..maxX && saidaTy in minY..maxY) {
            renderizarPlacaSaida(canvas, mazeData, saidaTx, saidaTy, tileW, tileH)
        }

        // Partículas
        particleSystem.render(canvas)

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
}

/**
 * Extensões do GameState para o Renderer.
 * (Mantidas para compatibilidade — os campos reais estão em GameState)
 */
