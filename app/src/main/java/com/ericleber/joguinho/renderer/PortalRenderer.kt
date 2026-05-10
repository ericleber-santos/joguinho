package com.ericleber.joguinho.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.ericleber.joguinho.biome.BiomeWorld
import kotlin.math.sin
import kotlin.math.cos

/**
 * Renderiza o portal interdimensional no tile de saída.
 *
 * Substitui a placa/escada por um portal animado único por BiomeWorld destino.
 * Estados visuais: DORMANT (fraco) → AWAKENING (crescendo) → OPEN (pulsante).
 *
 * Performance: RadialGradient recriado apenas quando raio muda (a cada ~8 frames).
 * Sem alocação em hot path — Paint e objetos reutilizados.
 */
class PortalRenderer {

    // Paint reutilizável — sem alocação por frame
    private val paintGlow = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val paintRing = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val paintText = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val paintParticle = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // Raio anterior e status de bloqueio para evitar recriar RadialGradient todo frame
    private var lastRadius = -1f
    private var lastLockedState = false
    private var cachedGradient: RadialGradient? = null

    /**
     * Renderiza o portal no tile de saída.
     *
     * @param canvas      canvas de destino
     * @param cx          centro X na tela (pixels)
     * @param cy          centro Y na tela (pixels)
     * @param tileW       largura do tile em pixels
     * @param tileH       altura do tile em pixels
     * @param frameTotal  frame global (para animação)
     * @param state       estado do portal
     * @param destWorld   BiomeWorld de destino (define a cor)
     * @param isLocked    se true, portal está bloqueado (boss fight)
     */
    fun render(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        tileW: Float,
        tileH: Float,
        frameTotal: Long,
        state: PortalState,
        destWorld: BiomeWorld,
        isLocked: Boolean
    ) {
        val time = frameTotal * 0.05f
        
        // Se bloqueado, usa cores de alerta/mortas (cinza escuro e vermelho hostil)
        val colors = if (isLocked) PortalColors(Color.rgb(60, 60, 60), Color.rgb(180, 40, 40)) else destWorld.portalColors

        // Raio do portal: cresce com estado
        val baseRadius = when (state) {
            PortalState.DORMANT    -> tileW * 0.25f
            PortalState.AWAKENING  -> tileW * 0.40f
            PortalState.OPEN       -> tileW * 0.55f
        }
        val pulseAmp = if (state == PortalState.OPEN) 0.08f else 0.03f
        val radius = baseRadius * (1f + sin(time.toDouble()).toFloat() * pulseAmp)

        // Marcador no chão (elipse — perspectiva)
        renderGroundMark(canvas, cx, cy + tileH * 0.1f, tileW, tileH, colors.primary, state)

        // Halo de luz externo (RadialGradient reutilizável)
        val glowRadius = radius * 2.0f
        if (kotlin.math.abs(radius - lastRadius) > 2f || isLocked != lastLockedState) {
            val glowAlpha = if (isLocked) 30 else 120
            cachedGradient = RadialGradient(
                cx, cy, glowRadius,
                intArrayOf(
                    Color.argb(glowAlpha, Color.red(colors.primary), Color.green(colors.primary), Color.blue(colors.primary)),
                    Color.argb(0,   Color.red(colors.primary), Color.green(colors.primary), Color.blue(colors.primary))
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            lastRadius = radius
            lastLockedState = isLocked
        }
        paintGlow.shader = cachedGradient
        canvas.drawCircle(cx, cy, glowRadius, paintGlow)
        paintGlow.shader = null

        // Núcleo do portal: disco sólido com cor destino
        paintGlow.color = Color.argb(
            if (state == PortalState.DORMANT) 80 else 180,
            Color.red(colors.primary), Color.green(colors.primary), Color.blue(colors.primary)
        )
        canvas.drawCircle(cx, cy, radius, paintGlow)

        // Anel externo pulsante
        val ringAlpha = if (isLocked) 100 else 220
        paintRing.color = Color.argb(ringAlpha, Color.red(colors.accent), Color.green(colors.accent), Color.blue(colors.accent))
        paintRing.strokeWidth = (tileW * 0.06f).coerceAtLeast(4f)
        canvas.drawCircle(cx, cy, radius + tileW * 0.08f, paintRing)

        // Segundo anel (rotação oposta)
        paintRing.alpha = ringAlpha / 2
        canvas.drawCircle(cx, cy, radius + tileW * 0.14f, paintRing)

        // Partículas orbitais (apenas no estado OPEN)
        if (state == PortalState.OPEN && !isLocked) {
            renderOrbitalParticles(canvas, cx, cy, radius, time, colors, tileW)
        }

        // Ícone/texto do mundo destino (apenas OPEN)
        if (state != PortalState.DORMANT) {
            renderWorldLabel(canvas, cx, cy - radius - tileH * 0.6f, destWorld, tileW, isLocked)
        }
    }

    private fun renderGroundMark(
        canvas: Canvas, cx: Float, cy: Float,
        tileW: Float, tileH: Float,
        color: Int, state: PortalState
    ) {
        val alpha = when (state) {
            PortalState.DORMANT   -> 60
            PortalState.AWAKENING -> 120
            PortalState.OPEN      -> 180
        }
        paintGlow.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawOval(
            cx - tileW * 0.45f, cy - tileH * 0.20f,
            cx + tileW * 0.45f, cy + tileH * 0.20f,
            paintGlow
        )
    }

    private fun renderOrbitalParticles(
        canvas: Canvas, cx: Float, cy: Float,
        radius: Float, time: Float,
        colors: PortalColors, tileW: Float
    ) {
        val particleCount = 5
        val particleSize = (tileW * 0.07f).coerceAtLeast(5f)

        for (i in 0 until particleCount) {
            val angle = time * 1.5f + (i * (2f * Math.PI.toFloat() / particleCount))
            val orbitRadius = radius + tileW * 0.18f
            val px = cx + cos(angle.toDouble()).toFloat() * orbitRadius
            val py = cy + sin(angle.toDouble()).toFloat() * orbitRadius * 0.4f // achatado (perspectiva)

            val pAlpha = 180 + (sin((angle * 2).toDouble()) * 60).toInt()
            paintParticle.color = Color.argb(pAlpha.coerceIn(100, 240),
                Color.red(colors.accent), Color.green(colors.accent), Color.blue(colors.accent))
            canvas.drawCircle(px, py, particleSize, paintParticle)
        }
    }

    private fun renderWorldLabel(
        canvas: Canvas, cx: Float, cy: Float,
        world: BiomeWorld, tileW: Float, isLocked: Boolean
    ) {
        if (isLocked) {
            val label = "🔒 BLOQUEADO"
            paintText.textSize = (tileW * 0.30f).coerceAtLeast(14f)
            paintText.setShadowLayer(6f, 0f, 2f, Color.BLACK)
            paintText.color = world.portalColors.accent
            canvas.drawText(label, cx, cy, paintText)
            paintText.clearShadowLayer()
        } else {
            // Desenha a plaquinha "SAÍDA" (Plaquinha de madeira elegante)
            val text = "SAÍDA"
            paintText.textSize = (tileW * 0.28f).coerceAtLeast(12f)
            val textWidth = paintText.measureText(text)
            val padH = 18f
            val padV = 10f
            
            val rect = android.graphics.RectF(
                cx - textWidth/2 - padH, 
                cy - paintText.textSize - padV, 
                cx + textWidth/2 + padH, 
                cy + padV
            )
                             
            // Fundo da plaquinha (Madeira escura)
            paintParticle.color = Color.rgb(80, 50, 20)
            canvas.drawRoundRect(rect, 8f, 8f, paintParticle)
            
            // Borda da plaquinha (Bege madeira clara)
            paintParticle.style = Paint.Style.STROKE
            paintParticle.color = Color.rgb(200, 180, 150)
            paintParticle.strokeWidth = 3f
            canvas.drawRoundRect(rect, 8f, 8f, paintParticle)
            paintParticle.style = Paint.Style.FILL
            
            // Texto
            paintText.color = Color.WHITE
            paintText.setShadowLayer(4f, 0f, 2f, Color.BLACK)
            canvas.drawText(text, cx, cy - padV * 0.2f, paintText)
            paintText.clearShadowLayer()
        }
    }

    fun release() {
        cachedGradient = null
        lastRadius = -1f
    }
}

/** Estado de animação do portal. */
enum class PortalState {
    DORMANT,    // Player longe — portal fraco, quase invisível
    AWAKENING,  // Player a ≤ 5 tiles — portal crescendo
    OPEN        // Player a ≤ 2.5 tiles — portal totalmente aberto
}

/** Cores do portal por BiomeWorld destino. */
data class PortalColors(val primary: Int, val accent: Int)
