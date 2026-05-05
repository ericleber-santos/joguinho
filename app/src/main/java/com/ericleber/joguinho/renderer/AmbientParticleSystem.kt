package com.ericleber.joguinho.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.ericleber.joguinho.biome.Biome
import kotlin.random.Random

/**
 * Configuração de partículas ambiente para um bioma.
 *
 * @param maxCount   número máximo de partículas simultâneas (0 = desativado)
 * @param sizePx     tamanho em pixels de cada partícula
 * @param color      cor ARGB (alpha ignorado — controlado por life)
 * @param vyMin      velocidade vertical mínima (negativo = sobe, positivo = desce)
 * @param vyMax      velocidade vertical máxima
 * @param vxRange    variação horizontal (±vxRange px/s)
 */
data class AmbientParticleConfig(
    val maxCount: Int,
    val sizePx: Float,
    val color: Int,
    val vyMin: Float,
    val vyMax: Float,
    val vxRange: Float = 5f
)

/** Partícula ambiente — struct mutável para pool sem alocação. */
private class AmbientParticle {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var life = 0f        // ms restantes
    var maxLife = 0f     // ms total
    var active = false
}

/**
 * Sistema de partículas ambientais por bioma (poeira, esporos, terra caindo, etc.).
 *
 * Restrições:
 *  - Pool pré-alocado de MAX_POOL partículas — zero new() em update/render.
 *  - Caps: MINA=15, FUNGOS=25, CALCARIO=8, TERRA=12, UMIDA=0
 */
class AmbientParticleSystem {

    companion object {
        private const val MAX_POOL = 50          // pool global
        private const val LIFE_MIN_MS = 2000f
        private const val LIFE_MAX_MS = 5000f

        /** Configurações padrão por bioma (5 biomas prioritários). */
        val CONFIGS: Map<Biome, AmbientParticleConfig> = mapOf(
            Biome.MINA_ABANDONADA to AmbientParticleConfig(
                maxCount = 40, sizePx = 5f,
                color = 0xFFD4A96A.toInt(),
                vyMin = -50f, vyMax = -15f, vxRange = 10f
            ),
            Biome.CAVERNA_UMIDA to AmbientParticleConfig(
                maxCount = 30, sizePx = 4f,
                color = 0xFF88CCFF.toInt(),
                vyMin = -30f, vyMax = -10f, vxRange = 7f
            ),
            Biome.JARDIM_DE_FUNGOS to AmbientParticleConfig(
                maxCount = 50, sizePx = 6f,
                color = 0xFF7FFF00.toInt(),
                vyMin = -60f, vyMax = -20f, vxRange = 15f
            ),
            Biome.CAVERNA_DE_CALCARIO to AmbientParticleConfig(
                maxCount = 25, sizePx = 4f,
                color = 0xFFE8E8E8.toInt(),
                vyMin = -20f, vyMax = 20f, vxRange = 8f
            ),
            Biome.TUNEIS_DE_TERRA to AmbientParticleConfig(
                maxCount = 35, sizePx = 5f,
                color = 0xFF8B4513.toInt(),
                vyMin = 25f, vyMax = 60f, vxRange = 6f
            )
        )

        private val DEFAULT_CONFIG = AmbientParticleConfig(
            maxCount = 15, sizePx = 3f,
            color = Color.WHITE,
            vyMin = -20f, vyMax = -5f, vxRange = 5f
        )
    }

    private val pool = Array(MAX_POOL) { AmbientParticle() }
    private var config: AmbientParticleConfig? = null
    private var screenBounds = RectF()
    private val rng = Random(System.currentTimeMillis())

    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }

    // -----------------------------------------------------------------------
    // Inicialização / troca de bioma
    // -----------------------------------------------------------------------

    fun init(cfg: AmbientParticleConfig?, bounds: RectF) {
        config = cfg ?: DEFAULT_CONFIG
        screenBounds.set(bounds)
        // Resetar todas as partículas
        pool.forEach { it.active = false }
        // Pré-popular até o máximo imediatamente (evitar "burst" de spawn)
        val targetCfg = config!!
        if (targetCfg.maxCount > 0) {
            repeat(targetCfg.maxCount.coerceAtMost(MAX_POOL)) { spawnOne(targetCfg) }
        }
    }

    fun updateBounds(bounds: RectF) { screenBounds.set(bounds) }

    // -----------------------------------------------------------------------
    // Update
    // -----------------------------------------------------------------------

    fun update(deltaMs: Long) {
        val cfg = config ?: return
        if (cfg.maxCount == 0) return

        val dtSec = deltaMs / 1000f
        var activeCount = 0

        for (p in pool) {
            if (!p.active) continue
            p.x += p.vx * dtSec
            p.y += p.vy * dtSec
            p.life -= deltaMs
            if (p.life <= 0f || !screenBounds.contains(p.x, p.y)) {
                p.active = false
            } else {
                activeCount++
            }
        }

        // Reabastecer pool se abaixo do máximo
        while (activeCount < cfg.maxCount) {
            if (!spawnOne(cfg)) break
            activeCount++
        }
    }

    // -----------------------------------------------------------------------
    // Render
    // -----------------------------------------------------------------------

    fun render(canvas: Canvas) {
        val cfg = config ?: return
        if (cfg.maxCount == 0) return

        val r = Color.red(cfg.color)
        val g = Color.green(cfg.color)
        val b = Color.blue(cfg.color)
        val half = cfg.sizePx / 2f

        for (p in pool) {
            if (!p.active) continue
            val lifeRatio = (p.life / p.maxLife).coerceIn(0f, 1f)
            // Efeito de "twinkle" (pulsação aleatória baseada na posição/tempo)
            val wave = kotlin.math.sin((p.x + p.y + p.life * 0.01f).toDouble()).toFloat()
            val twinkle = 0.8f + 0.2f * wave
            
            // Pulsação de tamanho (cresce e diminui levemente)
            val currentHalf = half * (0.8f + 0.4f * wave)
            
            paint.color = Color.argb((lifeRatio * 180 * twinkle).toInt(), r, g, b)
            canvas.drawRect(p.x - currentHalf, p.y - currentHalf, p.x + currentHalf, p.y + currentHalf, paint)
        }
    }

    // -----------------------------------------------------------------------
    // Utilitários
    // -----------------------------------------------------------------------

    fun clear() { pool.forEach { it.active = false }; config = null }

    fun activeCount(): Int = pool.count { it.active }

    private fun spawnOne(cfg: AmbientParticleConfig): Boolean {
        val slot = pool.firstOrNull { !it.active } ?: return false
        slot.x = screenBounds.left + rng.nextFloat() * screenBounds.width()
        // Esporos/poeira: spawn na base se sobe, no topo se desce
        slot.y = if (cfg.vyMin < 0) {
            screenBounds.bottom - rng.nextFloat() * screenBounds.height() * 0.3f
        } else {
            screenBounds.top + rng.nextFloat() * screenBounds.height() * 0.2f
        }
        slot.vx = (rng.nextFloat() * 2f - 1f) * cfg.vxRange
        slot.vy = cfg.vyMin + rng.nextFloat() * (cfg.vyMax - cfg.vyMin)
        slot.life = LIFE_MIN_MS + rng.nextFloat() * (LIFE_MAX_MS - LIFE_MIN_MS)
        slot.maxLife = slot.life
        slot.active = true
        return true
    }
}
