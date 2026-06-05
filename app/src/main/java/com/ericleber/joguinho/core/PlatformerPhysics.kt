package com.ericleber.joguinho.core

import com.ericleber.joguinho.biome.BiomeWorld

/**
2. * Motor físico clássico de plataforma 2D com resolução AABB (Axis-Aligned Bounding Box).
3. * Gerencia gravidade, pulo modulado, Coyote Time, Jump Buffering, inércia horizontal e atrito.
4. *
5. * Todo texto, lógica e documentação em Português do Brasil.
6. *
7. * Requisito: T-021
8. */
object PlatformerPhysics {

    // --- Constantes Físicas Calibradas ---
    const val GRAVIDADE = 26f                  // Aceleração da gravidade (tiles/s²)
    const val VELOCIDADE_TERMINAL = 15f         // Velocidade de queda limite (tiles/s)
    const val FORCA_PULO = 10.8f                // Impulso inicial do pulo (tiles/s)
    
    const val ACEL_CHAO = 35f                   // Aceleração ao correr no chão (tiles/s²)
    const val ACEL_AR = 20f                     // Aceleração ao correr no ar (tiles/s²)
    const val ATRITO_CHAO = 24f                 // Frenagem suave ao soltar controles no chão
    const val AR_DRAG = 4f                      // Frenagem sutil ao soltar controles no ar
    const val VEL_MAX_X = 5.2f                  // Velocidade horizontal máxima do Hero (tiles/s)
    const val VEL_MAX_SPIKE = 4.8f              // Velocidade horizontal máxima do Spike (tiles/s)

    // --- Dimensões das Hitboxes AABB (em frações de tile) ---
    const val HERO_LARGURA = 0.5f
    const val HERO_ALTURA = 0.78f
    const val SPIKE_LARGURA = 0.48f
    const val SPIKE_ALTURA = 0.58f

    /**
     * Verifica se uma Bounding Box colide com paredes ou elementos sólidos do mapa.
     */
    fun checkColisaoMapa(
        x: Float,
        y: Float,
        largura: Float,
        altura: Float,
        gameState: GameState
    ): Boolean {
        val maze = gameState.mazeData ?: return false

        // Limites da caixa
        val xMin = x - largura / 2f
        val xMax = x + largura / 2f
        val yMin = y - altura / 2f
        val yMax = y + altura / 2f

        // Converte limites para coordenadas de tiles
        val tileLeft = Math.floor(xMin.toDouble()).toInt()
        val tileRight = Math.floor(xMax.toDouble()).toInt()
        val tileTop = Math.floor(yMin.toDouble()).toInt()
        val tileBottom = Math.floor(yMax.toDouble()).toInt()

        for (ty in tileTop..tileBottom) {
            for (tx in tileLeft..tileRight) {
                // Fora dos limites do labirinto é considerado sólido
                if (tx < 0 || ty < 0 || tx >= maze.width || ty >= maze.height) {
                    return true
                }
                
                // Verifica parede ou armadilha sólida no mapa (Ponto 4)
                if (maze.tiles[ty * maze.width + tx] != 0) {
                    return true
                }

                // Verifica colisão com elementos dinâmicos do modo de sobrevivência (Pilares e Caixas)
                val temElementoSolido = gameState.survivalElements.any {
                    it.active && it.position.ix == tx && it.position.iy == ty &&
                    (it.type == SurvivalElementType.STONE_PILLAR ||
                     it.type == SurvivalElementType.PUSHABLE_BOX)
                }
                if (temElementoSolido) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Atualiza a simulação física do Hero usando integração de Euler e resolução AABB em dois passos.
     */
    fun atualizarHero(deltaTimeSec: Float, gameState: GameState, direcaoX: Float, direcaoY: Float, puloPressionado: Boolean) {
        val maze = gameState.mazeData ?: return
        val deltaMs = (deltaTimeSec * 1000).toLong()

        // 1. Atualizar Timers de Usabilidade de Salto
        if (gameState.heroIsGrounded) {
            gameState.heroCoyoteTimerMs = 100L // coyote time de 100ms
            gameState.heroJumpCount = 0 // Reseta pulo duplo ao tocar o chão
        } else {
            gameState.heroCoyoteTimerMs = (gameState.heroCoyoteTimerMs - deltaMs).coerceAtLeast(0L)
        }

        if (puloPressionado && !gameState.heroJumpPressed) {
            gameState.heroJumpBufferTimerMs = 120L // jump buffer de 120ms
        } else {
            gameState.heroJumpBufferTimerMs = (gameState.heroJumpBufferTimerMs - deltaMs).coerceAtLeast(0L)
        }
        gameState.heroJumpPressed = puloPressionado

        // 2. Aplicar Física Horizontal (Aceleração, Atrito e Inércia)
        val acel = if (gameState.heroIsGrounded) ACEL_CHAO else ACEL_AR
        val atrito = if (gameState.heroIsGrounded) ATRITO_CHAO else AR_DRAG

        var vx = gameState.heroVelocityX
        var vy = gameState.heroVelocityY

        // Modificadores de biomas/efeitos
        val world = gameState.currentBiomeWorld
        val isIce = world == BiomeWorld.ABISMOS_AQUATICOS
        val isForest = world == BiomeWorld.FLORESTA_DE_ARVORES

        var velMaxX = VEL_MAX_X * gameState.heroSpeedMultiplier
        if (gameState.heroIsSlowedDown) velMaxX *= 0.4f
        if (gameState.heroHasSpeedBuff) velMaxX *= 1.5f
        if (isForest) velMaxX *= 0.65f // Penalidade de mata fechada

        // Aceleração/Inércia horizontal
        if (direcaoX != 0f) {
            val fatorAcel = if (isIce) 0.4f else 1.0f // Gelo reduz aceleração/aderência
            vx += direcaoX * acel * deltaTimeSec * fatorAcel
            vx = vx.coerceIn(-velMaxX, velMaxX)
        } else {
            // Aplica atrito/desaceleração horizontal gradual
            val fatorAtrito = if (isIce) 0.15f else 1.0f // Gelo escorrega mais
            val sinal = Math.signum(vx)
            vx -= sinal * atrito * deltaTimeSec * fatorAtrito
            if (Math.signum(vx) != sinal) {
                vx = 0f
            }
        }

        // --- Detecção de Wall Slide & Wall Jump (Ponto 4) ---
        val heroPos = gameState.heroPosition
        val encostaEsquerda = !gameState.heroIsGrounded && checkColisaoMapa(heroPos.x - 0.06f, heroPos.y, HERO_LARGURA, HERO_ALTURA, gameState)
        val encostaDireita = !gameState.heroIsGrounded && checkColisaoMapa(heroPos.x + 0.06f, heroPos.y, HERO_LARGURA, HERO_ALTURA, gameState)
        val isClimbing = encostaEsquerda || encostaDireita
        gameState.heroIsClimbing = isClimbing
        val wallDir = if (encostaEsquerda) -1 else if (encostaDireita) 1 else 0

        if (isClimbing) {
            // Wall Climb: sobe a parede se segurar pra cima
            if (direcaoY < -0.3f) {
                vy = -4.5f
                gameState.heroIsGrounded = false
            } else if (direcaoY > 0.3f) {
                // Desce rápido
                if (vy > 0f) vy = vy.coerceAtMost(VELOCIDADE_TERMINAL)
            } else {
                // Wall Slide: desliza lentamente ao cair
                if (vy > 0f) {
                    vy = vy.coerceAtMost(1.2f)
                }
            }
            // Wall Jump: pula na diagonal oposta
            if (gameState.heroJumpBufferTimerMs > 0L) {
                vy = -FORCA_PULO * 0.92f * gameState.heroJumpMultiplier
                vx = -wallDir * VEL_MAX_X * 0.9f
                gameState.heroIsGrounded = false
                gameState.heroCoyoteTimerMs = 0L
                gameState.heroJumpBufferTimerMs = 0L
                gameState.heroJumpCount = 1
            }
        }

        // 3. Aplicar Física Vertical (Gravidade e Modulação de Salto)
        // Modulação do pulo: se soltar o botão de pulo e estiver subindo, diminui a velocidade vertical
        if (!puloPressionado && vy < 0f) {
            vy *= 0.52f // Desaceleração suave de corte de salto
        }

        // Aplica gravidade
        vy += GRAVIDADE * deltaTimeSec
        if (vy > VELOCIDADE_TERMINAL) {
            vy = VELOCIDADE_TERMINAL
        }

        // 4. Lógica de Pulo (Coyote Time + Jump Buffering + Double Jump)
        if (gameState.heroJumpBufferTimerMs > 0L) {
            if (gameState.heroCoyoteTimerMs > 0L) {
                vy = -FORCA_PULO * gameState.heroJumpMultiplier
                gameState.heroIsGrounded = false
                gameState.heroCoyoteTimerMs = 0L
                gameState.heroJumpBufferTimerMs = 0L
                gameState.heroJumpCount = 1
            } else if (gameState.heroDoubleJumpUnlocked && gameState.heroJumpCount < 2) {
                // Pulo duplo no ar
                vy = -FORCA_PULO * 0.95f * gameState.heroJumpMultiplier
                gameState.heroIsGrounded = false
                gameState.heroJumpBufferTimerMs = 0L
                gameState.heroJumpCount = 2
                
                // VFX sob os pés do herói para feedback visual
                val currentTime = System.currentTimeMillis()
                val doubleJumpVfx = VfxState(
                    id = "double_jump_vfx_${currentTime}",
                    position = Position(gameState.heroPosition.x, gameState.heroPosition.y + HERO_ALTURA / 2f),
                    type = VfxType.WATER_SPLASH,
                    createdAtMs = currentTime,
                    durationMs = 250L
                )
                gameState.vfxList = gameState.vfxList + doubleJumpVfx
            }
        }

        // 5. Integração e Resolução AABB Separada em 2 Passos (Eixo X depois Eixo Y)
        var pos = gameState.heroPosition

        // --- Passo X ---
        val proxX = pos.x + vx * deltaTimeSec
        if (!checkColisaoMapa(proxX, pos.y, HERO_LARGURA, HERO_ALTURA, gameState)) {
            pos = Position(proxX, pos.y)
        } else {
            // Ajustar o Hero rente à parede lateral
            val tx = if (vx > 0f) {
                Math.floor((proxX + HERO_LARGURA / 2f).toDouble()).toFloat() - HERO_LARGURA / 2f - 0.001f
            } else {
                Math.floor((proxX - HERO_LARGURA / 2f).toDouble()).toFloat() + 1f + HERO_LARGURA / 2f + 0.001f
            }
            // Apenas ajusta se o ajuste for válido e livre de colisão
            if (!checkColisaoMapa(tx, pos.y, HERO_LARGURA, HERO_ALTURA, gameState)) {
                pos = Position(tx, pos.y)
            }
            vx = 0f
        }

        // --- Passo Y ---
        val proxY = pos.y + vy * deltaTimeSec
        if (!checkColisaoMapa(pos.x, proxY, HERO_LARGURA, HERO_ALTURA, gameState)) {
            pos = Position(pos.x, proxY)
            gameState.heroIsGrounded = false
        } else {
            if (vy > 0f) {
                // Colisão caindo: Aterrissou no chão
                val ty = Math.floor((proxY + HERO_ALTURA / 2f).toDouble()).toFloat() - HERO_ALTURA / 2f - 0.001f
                if (!checkColisaoMapa(pos.x, ty, HERO_LARGURA, HERO_ALTURA, gameState)) {
                    pos = Position(pos.x, ty)
                }
                vy = 0f
                gameState.heroIsGrounded = true
            } else if (vy < 0f) {
                // Colisão subindo: Bateu a cabeça no teto
                val ty = Math.floor((proxY - HERO_ALTURA / 2f).toDouble()).toFloat() + 1f + HERO_ALTURA / 2f + 0.001f
                if (!checkColisaoMapa(pos.x, ty, HERO_LARGURA, HERO_ALTURA, gameState)) {
                    pos = Position(pos.x, ty)
                }
                vy = 0f
            }
        }

        // Atualizar estado global
        gameState.heroPosition = pos
        gameState.heroVelocityX = vx
        gameState.heroVelocityY = vy
    }

    /**
     * Atualiza a simulação física do Spike de forma paralela ao Hero, integrando a física de Tether (cabo elástico).
     * O cabo funciona como uma mola com atração proporcional à distância (Lei de Hooke).
     */
    fun atualizarSpike(deltaTimeSec: Float, gameState: GameState, direcaoX: Float, puloPressionado: Boolean) {
        val maze = gameState.mazeData ?: return

        // Se Spike estiver ancorado como canhão, ele fica imóvel e não sofre gravidade
        if (gameState.isSpikeAnchored) {
            gameState.spikeVelocityX = 0f
            gameState.spikeVelocityY = 0f
            return
        }

        val heroPos = gameState.heroPosition
        val spikePos = gameState.spikePosition
        val dx = heroPos.x - spikePos.x
        val dy = heroPos.y - spikePos.y
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        var vx = gameState.spikeVelocityX
        var vy = gameState.spikeVelocityY

        // Constantes elásticas do cabo
        val comprimentoRepouso = 2.0f
        val k = if (gameState.spikeSlingActive) 38f else 12f // Constante de mola muito maior no sling
        val amortecimento = if (gameState.spikeSlingActive) 1.5f else 5.0f // Amortece menos durante o sling

        // 1. Aplica força elástica se a distância for maior que o repouso
        if (dist > comprimentoRepouso) {
            val forcaMola = k * (dist - comprimentoRepouso)
            val ax = (dx / dist) * forcaMola
            val ay = (dy / dist) * forcaMola
            vx += ax * deltaTimeSec
            vy += ay * deltaTimeSec

            // Aplica atrito/amortecimento do cabo elástico
            vx -= vx * amortecimento * deltaTimeSec
            vy -= vy * amortecimento * deltaTimeSec
        } else {
            // Se chegou perto do Hero no meio do Sling, encerra o Sling de alta velocidade
            if (gameState.spikeSlingActive && dist < 1.2f) {
                gameState.spikeSlingActive = false
            }

            // Aceleração horizontal normal no chão se não estiver no sling
            if (!gameState.spikeSlingActive) {
                val velMaxSpike = if (gameState.spikeIsSlowedDown) VEL_MAX_SPIKE * 0.4f else VEL_MAX_SPIKE * gameState.spikeSpeedMultiplier
                if (direcaoX != 0f) {
                    vx += direcaoX * ACEL_CHAO * deltaTimeSec
                    vx = vx.coerceIn(-velMaxSpike, velMaxSpike)
                } else {
                    // Desaceleração gradual
                    val sinal = Math.signum(vx)
                    vx -= sinal * ATRITO_CHAO * deltaTimeSec
                    if (Math.signum(vx) != sinal) {
                        vx = 0f
                    }
                }
            }
        }

        // 2. Gravidade normal se não estiver no sling de alta velocidade
        if (!gameState.spikeSlingActive) {
            vy += GRAVIDADE * deltaTimeSec
            if (vy > VELOCIDADE_TERMINAL) {
                vy = VELOCIDADE_TERMINAL
            }

            // Pulo físico do Spike
            if (puloPressionado && gameState.spikeIsGrounded) {
                vy = -FORCA_PULO * 0.95f
                gameState.spikeIsGrounded = false
            }
        }

        // 3. Integração e Resolução AABB Separada em 2 Passos
        var pos = gameState.spikePosition

        // --- Passo X ---
        val proxX = pos.x + vx * deltaTimeSec
        if (!checkColisaoMapa(proxX, pos.y, SPIKE_LARGURA, SPIKE_ALTURA, gameState)) {
            pos = Position(proxX, pos.y)
        } else {
            // Se bater numa parede lateral caminhando, Spike tenta pular se estiver no chão
            if (gameState.spikeIsGrounded && vx != 0f && !gameState.spikeSlingActive) {
                vy = -FORCA_PULO * 0.85f
                gameState.spikeIsGrounded = false
            }
            val tx = if (vx > 0f) {
                Math.floor((proxX + SPIKE_LARGURA / 2f).toDouble()).toFloat() - SPIKE_LARGURA / 2f - 0.001f
            } else {
                Math.floor((proxX - SPIKE_LARGURA / 2f).toDouble()).toFloat() + 1f + SPIKE_LARGURA / 2f + 0.001f
            }
            if (!checkColisaoMapa(tx, pos.y, SPIKE_LARGURA, SPIKE_ALTURA, gameState)) {
                pos = Position(tx, pos.y)
            }
            vx = 0f
        }

        // --- Passo Y ---
        val proxY = pos.y + vy * deltaTimeSec
        if (!checkColisaoMapa(pos.x, proxY, SPIKE_LARGURA, SPIKE_ALTURA, gameState)) {
            pos = Position(pos.x, proxY)
            gameState.spikeIsGrounded = false
        } else {
            if (vy > 0f) {
                // Aterrissou
                val ty = Math.floor((proxY + SPIKE_ALTURA / 2f).toDouble()).toFloat() - SPIKE_ALTURA / 2f - 0.001f
                // Fallback de segurança para ajuste
                val finalY = if (!checkColisaoMapa(pos.x, ty, SPIKE_LARGURA, SPIKE_ALTURA, gameState)) ty else proxY.toInt().toFloat() + 0.5f
                pos = Position(pos.x, finalY)
                vy = 0f
                gameState.spikeIsGrounded = true
            } else if (vy < 0f) {
                // Teto
                val ty = Math.floor((proxY - SPIKE_ALTURA / 2f).toDouble()).toFloat() + 1f + SPIKE_ALTURA / 2f + 0.001f
                if (!checkColisaoMapa(pos.x, ty, SPIKE_LARGURA, SPIKE_ALTURA, gameState)) {
                    pos = Position(pos.x, ty)
                }
                vy = 0f
            }
        }

        gameState.spikePosition = pos
        gameState.spikeVelocityX = vx
        gameState.spikeVelocityY = vy
    }
}
