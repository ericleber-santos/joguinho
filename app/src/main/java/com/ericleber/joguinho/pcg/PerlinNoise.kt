package com.ericleber.joguinho.pcg

import kotlin.math.floor

/**
 * Utilitário matemático para geração de Simplex Noise 2D.
 * Muito mais rápido e orgânico que o Perlin Noise clássico, ideal para topologia de cavernas.
 */
class PerlinNoise(private val seed: Long = 0) {
    
    // Tabela de permutação baseada na seed
    private val p = IntArray(512)
    
    init {
        val random = java.util.Random(seed)
        val pBase = IntArray(256)
        for (i in 0..255) pBase[i] = i
        
        // Fisher-Yates shuffle
        for (i in 255 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = pBase[i]
            pBase[i] = pBase[j]
            pBase[j] = temp
        }
        
        for (i in 0..511) {
            p[i] = pBase[i and 255]
        }
    }
    
    /**
     * Retorna um valor de noise 2D entre -1.0 e 1.0
     */
    fun noise(xin: Double, yin: Double): Double {
        var n0: Double
        var n1: Double
        var n2: Double
        
        val F2 = 0.5 * (Math.sqrt(3.0) - 1.0)
        val s = (xin + yin) * F2
        val i = floor(xin + s).toInt()
        val j = floor(yin + s).toInt()
        
        val G2 = (3.0 - Math.sqrt(3.0)) / 6.0
        val t = (i + j) * G2
        val X0 = i - t
        val Y0 = j - t
        val x0 = xin - X0
        val y0 = yin - Y0
        
        val i1: Int
        val j1: Int
        if (x0 > y0) {
            i1 = 1; j1 = 0
        } else {
            i1 = 0; j1 = 1
        }
        
        val x1 = x0 - i1 + G2
        val y1 = y0 - j1 + G2
        val x2 = x0 - 1.0 + 2.0 * G2
        val y2 = y0 - 1.0 + 2.0 * G2
        
        val ii = i and 255
        val jj = j and 255
        val gi0 = p[ii + p[jj]] % 12
        val gi1 = p[ii + i1 + p[jj + j1]] % 12
        val gi2 = p[ii + 1 + p[jj + 1]] % 12
        
        var t0 = 0.5 - x0 * x0 - y0 * y0
        if (t0 < 0) n0 = 0.0 else {
            t0 *= t0
            n0 = t0 * t0 * dot(gi0, x0, y0)
        }
        
        var t1 = 0.5 - x1 * x1 - y1 * y1
        if (t1 < 0) n1 = 0.0 else {
            t1 *= t1
            n1 = t1 * t1 * dot(gi1, x1, y1)
        }
        
        var t2 = 0.5 - x2 * x2 - y2 * y2
        if (t2 < 0) n2 = 0.0 else {
            t2 *= t2
            n2 = t2 * t2 * dot(gi2, x2, y2)
        }
        
        return 70.0 * (n0 + n1 + n2)
    }
    
    private fun dot(g: Int, x: Double, y: Double): Double {
        val grad3 = arrayOf(
            doubleArrayOf(1.0, 1.0), doubleArrayOf(-1.0, 1.0), doubleArrayOf(1.0, -1.0), doubleArrayOf(-1.0, -1.0),
            doubleArrayOf(1.0, 0.0), doubleArrayOf(-1.0, 0.0), doubleArrayOf(1.0, 0.0), doubleArrayOf(-1.0, 0.0),
            doubleArrayOf(0.0, 1.0), doubleArrayOf(0.0, -1.0), doubleArrayOf(0.0, 1.0), doubleArrayOf(0.0, -1.0)
        )
        return grad3[g][0] * x + grad3[g][1] * y
    }
    
    /**
     * Fractal Brownian Motion (fBm)
     * Combina múltiplas oitavas de noise para criar terrenos mais orgânicos.
     */
    fun fbm(x: Double, y: Double, octaves: Int = 4, persistence: Double = 0.5, lacunarity: Double = 2.0): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        var maxValue = 0.0
        for (i in 0 until octaves) {
            total += noise(x * frequency, y * frequency) * amplitude
            maxValue += amplitude
            amplitude *= persistence
            frequency *= lacunarity
        }
        return total / maxValue
    }
}
