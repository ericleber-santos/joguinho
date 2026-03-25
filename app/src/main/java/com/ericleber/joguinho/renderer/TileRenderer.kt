package com.ericleber.joguinho.renderer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.ericleber.joguinho.biome.Biome
import com.ericleber.joguinho.biome.BiomePalette

enum class TileType {
    WALL, FLOOR,
    DECORATIVE_0, DECORATIVE_1, DECORATIVE_2, DECORATIVE_3,
    ENTRADA, SAIDA
}

/**
 * Renderiza tiles isométricos estilo Stardew Valley cave.
 *
 * Chão: 4 variantes de textura por tile (base, rachadura, musgo, umidade).
 *       Variação determinística por posição — sem padrão repetitivo.
 *       Borda inferior levemente mais escura para definir profundidade.
 *
 * Parede: bloco sólido com 3 faces, altura real (1.4x tileH).
 *         Topo claro, face frontal com textura de pedra, lateral escura.
 *         Detalhes embutidos: cristais, veios de minério, rachaduras.
 *
 * Flora: cogumelos luminescentes, cristais, raízes, liquens.
 *
 * Regras: Paint.isAntiAlias = false, Paint.isFilterBitmap = false (pixel art).
 */
class TileRenderer {

    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }
    private val path = Path()

    // Altura da parede — 1.4x tileH para parecer bloco sólido real
    private val fatorAlturaParede = 1.4f

    // =========================================================================
    // CHÃO — terra de caverna com 4 variantes de textura
    // =========================================================================

    /**
     * Renderiza tile de chão como superfície plana de terra escura.
     *
     * Regra fundamental: chão deve ser ESCURO e PLANO.
     * A parede é o elemento com volume e cor clara — o chão é o "vazio" entre elas.
     * Sem subdivisão interna visível, sem losango interno, sem borda entre tiles.
     * Apenas micro-variação de cor (±3 níveis) para textura orgânica sutil.
     */
    fun renderFloorTile(
        canvas: Canvas,
        x: Float, y: Float,
        tileW: Float, tileH: Float,
        palette: BiomePalette,
        tileX: Int = 0, tileY: Int = 0
    ) {
        val halfW = tileW / 2f
        val halfH = tileH / 2f
        val cx = x + halfW
        val cy = y + halfH

        val hash = (tileX * 13 + tileY * 7 + tileX * tileY * 3) and 0xFF

        // Micro-variação mínima: ±3 níveis — tiles quase idênticos, superfície contínua
        val microVar = ((hash shr 2) % 6) - 3
        val corFinal = variarCor(palette.floorColor, microVar)

        // Losango plano — cor única, sem subdivisão
        paint.color = corFinal
        path.reset()
        path.moveTo(cx, y)
        path.lineTo(x + tileW, cy)
        path.lineTo(cx, y + tileH)
        path.lineTo(x, cy)
        path.close()
        canvas.drawPath(path, paint)

        // Detalhe mínimo: 1-2 pixels escuros por tile (grão de terra)
        // Apenas em 40% dos tiles para não criar padrão repetitivo
        val seed = tileX * 11 + tileY * 17
        if (hash % 5 != 0) {
            renderDetalheChaoBase(canvas, cx, cy, halfW, halfH, corFinal, seed)
        }
    }

    private fun renderDetalheChaoBase(
        canvas: Canvas, cx: Float, cy: Float,
        halfW: Float, halfH: Float, cor: Int, seed: Int
    ) {
        // 2-3 grãos de terra/pedra pequenos
        paint.color = escurecer(cor, 0.20f)
        val n = 2 + (seed % 2)
        for (i in 0 until n) {
            val gx = cx + ((seed * (i + 1) * 3) % 13 - 6).toFloat() * (halfW * 0.11f)
            val gy = cy + ((seed * (i + 1) * 5) % 11 - 5).toFloat() * (halfH * 0.11f)
            canvas.drawRect(gx, gy, gx + 1.5f, gy + 1.5f, paint)
        }
        // 1 pixel claro (reflexo)
        if (seed % 3 == 0) {
            paint.color = clarear(cor, 0.15f)
            val rx = cx + ((seed * 7) % 9 - 4).toFloat() * (halfW * 0.08f)
            val ry = cy - halfH * 0.3f + ((seed * 3) % 5).toFloat() * (halfH * 0.06f)
            canvas.drawRect(rx, ry, rx + 1f, ry + 1f, paint)
        }
    }

    private fun renderDetalheRachadura(
        canvas: Canvas, cx: Float, cy: Float,
        halfW: Float, halfH: Float, cor: Int, seed: Int
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = escurecer(cor, 0.35f)
        // Linha de rachadura diagonal
        val x1 = cx + ((seed % 7) - 3).toFloat() * (halfW * 0.12f)
        val y1 = cy - halfH * 0.2f
        canvas.drawLine(x1, y1, x1 + halfW * 0.25f, y1 + halfH * 0.4f, paint)
        if (seed % 2 == 0) {
            canvas.drawLine(x1 + halfW * 0.25f, y1 + halfH * 0.4f,
                x1 + halfW * 0.15f, y1 + halfH * 0.6f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun renderDetalheMusgo(
        canvas: Canvas, cx: Float, cy: Float,
        halfW: Float, halfH: Float, palette: BiomePalette, seed: Int
    ) {
        // Cluster de musgo: 3-5 pixels verdes/coloridos
        paint.color = Color.argb(180,
            Color.red(palette.mossColor),
            Color.green(palette.mossColor),
            Color.blue(palette.mossColor))
        val n = 3 + (seed % 3)
        for (i in 0 until n) {
            val mx = cx + ((seed * (i + 2) * 4) % 15 - 7).toFloat() * (halfW * 0.10f)
            val my = cy + ((seed * (i + 1) * 6) % 11 - 5).toFloat() * (halfH * 0.10f)
            canvas.drawRect(mx, my, mx + 2f, my + 2f, paint)
        }
    }

    private fun renderDetalheUmidade(
        canvas: Canvas, cx: Float, cy: Float,
        halfW: Float, halfH: Float, palette: BiomePalette, seed: Int
    ) {
        // Mancha de umidade: oval escura semi-transparente
        paint.color = Color.argb(100,
            Color.red(palette.floorVariant2),
            Color.green(palette.floorVariant2),
            Color.blue(palette.floorVariant2))
        val ow = halfW * 0.4f
        val oh = halfH * 0.35f
        val ox = cx + ((seed % 5) - 2).toFloat() * (halfW * 0.08f)
        val oy = cy + ((seed % 3) - 1).toFloat() * (halfH * 0.08f)
        canvas.drawOval(RectF(ox - ow, oy - oh, ox + ow, oy + oh), paint)
    }

    // =========================================================================
    // PAREDE — bloco sólido com 3 faces, textura de pedra, detalhes orgânicos
    // =========================================================================

    /**
     * Parede isométrica estilo Stardew Valley cave — pedra com blocos visíveis.
     *
     * Estrutura:
     * - Face frontal esquerda: blocos de pedra com juntas escuras, variação de cor por bloco
     * - Face lateral direita: sombra profunda com blocos menores
     * - Topo: losango plano com highlight nas bordas
     * - Detalhes: musgo nas juntas, veios de minério, cristais ocasionais
     * - Outline preto nas arestas externas
     */
    fun renderWallTile(
        canvas: Canvas,
        x: Float, y: Float,
        tileW: Float, tileH: Float,
        palette: BiomePalette,
        tileX: Int = 0, tileY: Int = 0
    ) {
        val halfW = tileW / 2f
        val halfH = tileH / 2f
        val alturaParede = tileH * fatorAlturaParede
        val seed = tileX * 7 + tileY * 13

        // ---- Face frontal esquerda (losango esquerdo + face vertical) ----
        // Cor base com micro-variação por tile
        val varBase = ((seed * 3) % 14) - 7
        val corFrontal = variarCor(palette.wallColor, varBase)

        paint.color = corFrontal
        path.reset()
        path.moveTo(x, y + halfH)
        path.lineTo(x + halfW, y + tileH)
        path.lineTo(x + halfW, y + tileH + alturaParede)
        path.lineTo(x, y + halfH + alturaParede)
        path.close()
        canvas.drawPath(path, paint)

        // ---- Face lateral direita — sombra profunda ----
        paint.color = palette.wallShadowColor
        path.reset()
        path.moveTo(x + halfW, y + tileH)
        path.lineTo(x + tileW, y + halfH)
        path.lineTo(x + tileW, y + halfH + alturaParede)
        path.lineTo(x + halfW, y + tileH + alturaParede)
        path.close()
        canvas.drawPath(path, paint)

        // ---- Blocos de pedra na face frontal ----
        // Divide a face frontal em fileiras de blocos com juntas escuras
        renderBlocosPedraFrontal(canvas, x, y, tileW, tileH, alturaParede, corFrontal, seed)

        // ---- Blocos menores na face lateral ----
        renderBlocosPedraLateral(canvas, x, y, tileW, tileH, alturaParede, palette.wallShadowColor, seed)

        // ---- Topo (losango) — cor plana com highlight nas bordas ----
        paint.color = palette.wallTopColor
        path.reset()
        path.moveTo(x + halfW, y)
        path.lineTo(x + tileW, y + halfH)
        path.lineTo(x + halfW, y + tileH)
        path.lineTo(x, y + halfH)
        path.close()
        canvas.drawPath(path, paint)

        // Highlight de 1px nas bordas superiores do topo
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = clarear(palette.wallTopColor, 0.25f)
        canvas.drawLine(x + halfW, y, x + tileW, y + halfH, paint)
        canvas.drawLine(x + halfW, y, x, y + halfH, paint)
        paint.style = Paint.Style.FILL

        // ---- Outline preto nas arestas externas ----
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(200, 0, 0, 0)
        // Arestas verticais
        canvas.drawLine(x, y + halfH, x, y + halfH + alturaParede, paint)
        canvas.drawLine(x + tileW, y + halfH, x + tileW, y + halfH + alturaParede, paint)
        canvas.drawLine(x + halfW, y + tileH, x + halfW, y + tileH + alturaParede, paint)
        // Base
        canvas.drawLine(x, y + halfH + alturaParede, x + halfW, y + tileH + alturaParede, paint)
        canvas.drawLine(x + halfW, y + tileH + alturaParede, x + tileW, y + halfH + alturaParede, paint)
        // Outline do topo
        path.reset()
        path.moveTo(x + halfW, y)
        path.lineTo(x + tileW, y + halfH)
        path.lineTo(x + halfW, y + tileH)
        path.lineTo(x, y + halfH)
        path.close()
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL

        // ---- Detalhes orgânicos ----
        renderTexturaPedra(canvas, x, y, tileW, tileH, palette, tileX, tileY)
    }

    /**
     * Desenha fileiras de blocos de pedra na face frontal esquerda da parede.
     * Cada bloco tem cor ligeiramente diferente e junta escura entre eles.
     * Isso cria a aparência de alvenaria irregular estilo Stardew Valley.
     */
    private fun renderBlocosPedraFrontal(
        canvas: Canvas,
        x: Float, y: Float,
        tileW: Float, tileH: Float,
        alturaParede: Float,
        corBase: Int, seed: Int
    ) {
        val halfW = tileW / 2f
        // Altura total da face frontal = alturaParede
        // Dividimos em 2-3 fileiras de blocos
        val numFileiras = 3
        val alturaFileira = alturaParede / numFileiras

        // Cor da junta (escura)
        val corJunta = escurecer(corBase, 0.35f)

        for (fileira in 0 until numFileiras) {
            val yTopo = y + tileH + fileira * alturaFileira
            val yBase = yTopo + alturaFileira

            // Cada fileira tem 2 blocos na face frontal esquerda
            // A face frontal é um paralelogramo — simplificamos como retângulo projetado
            val numBlocos = 2
            for (bloco in 0 until numBlocos) {
                val bSeed = seed * 31 + fileira * 7 + bloco * 13
                val varBloco = ((bSeed * 5) % 18) - 9
                val corBloco = variarCor(corBase, varBloco)

                // Posição X do bloco na face frontal (projeção isométrica simplificada)
                val xEsq = x + bloco * (halfW / numBlocos)
                val xDir = xEsq + halfW / numBlocos - 1f

                // Clip ao losango da face frontal — desenhamos retângulo simples
                // A face frontal vai de x até x+halfW horizontalmente
                paint.color = corBloco
                canvas.drawRect(xEsq + 1f, yTopo + 1f, xDir, yBase - 1f, paint)

                // Highlight no topo do bloco (borda superior mais clara)
                if (fileira == 0) {
                    paint.color = clarear(corBloco, 0.12f)
                    canvas.drawRect(xEsq + 1f, yTopo + 1f, xDir, yTopo + 2.5f, paint)
                }
            }

            // Junta horizontal entre fileiras
            paint.color = corJunta
            canvas.drawRect(x, yBase - 1f, x + halfW, yBase, paint)
        }

        // Junta vertical central na face frontal
        paint.color = corJunta
        canvas.drawRect(x + halfW / 2f - 0.5f, y + tileH, x + halfW / 2f + 0.5f, y + tileH + alturaParede, paint)
    }

    /**
     * Desenha blocos menores na face lateral direita da parede.
     */
    private fun renderBlocosPedraLateral(
        canvas: Canvas,
        x: Float, y: Float,
        tileW: Float, tileH: Float,
        alturaParede: Float,
        corSombra: Int, seed: Int
    ) {
        val halfW = tileW / 2f
        val halfH = tileH / 2f
        val numFileiras = 3
        val alturaFileira = alturaParede / numFileiras
        val corJunta = escurecer(corSombra, 0.20f)

        for (fileira in 0 until numFileiras) {
            val yTopo = y + halfH + fileira * alturaFileira
            val yBase = yTopo + alturaFileira

            // Variação sutil por bloco na face lateral
            val bSeed = seed * 17 + fileira * 11
            val varBloco = ((bSeed * 3) % 10) - 5
            val corBloco = variarCor(corSombra, varBloco)

            paint.color = corBloco
            canvas.drawRect(x + halfW + 1f, yTopo + 1f, x + tileW - 1f, yBase - 1f, paint)

            // Junta horizontal
            paint.color = corJunta
            canvas.drawRect(x + halfW, yBase - 1f, x + tileW, yBase, paint)
        }
    }

    /**
     * Textura de pedra: rachaduras, veios de minério, cristais embutidos.
     * Chamado tanto no renderWallTile quanto diretamente pelo Renderer.
     */
    fun renderTexturaPedra(
        canvas: Canvas,
        x: Float, y: Float,
        tileW: Float, tileH: Float,
        palette: BiomePalette,
        tileX: Int, tileY: Int
    ) {
        val halfW = tileW / 2f
        val alturaParede = tileH * fatorAlturaParede
        val seed = tileX * 7 + tileY * 13

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f

        // Rachadura na face frontal
        paint.color = Color.argb(80, 0, 0, 0)
        val rx1 = x + (seed % 5).toFloat() * (halfW / 5f)
        val ry1 = y + tileH + (seed % 4).toFloat() * (alturaParede / 5f)
        canvas.drawLine(rx1, ry1, rx1 + halfW * 0.20f, ry1 + alturaParede * 0.22f, paint)
        if (seed % 3 == 0) {
            val rx2 = x + ((seed * 3) % 6).toFloat() * (halfW / 6f)
            val ry2 = y + tileH + alturaParede * 0.5f
            canvas.drawLine(rx2, ry2, rx2 + halfW * 0.12f, ry2 + alturaParede * 0.14f, paint)
        }

        paint.style = Paint.Style.FILL

        // Veio de minério (acento do bioma) — aparece em ~20% das paredes
        if (seed % 5 == 0) {
            paint.color = Color.argb(160,
                Color.red(palette.wallDetailColor),
                Color.green(palette.wallDetailColor),
                Color.blue(palette.wallDetailColor))
            val vx = x + halfW * 0.3f + ((seed * 2) % 5).toFloat() * (halfW / 5f)
            val vy = y + tileH + alturaParede * 0.25f
            canvas.drawRect(vx, vy, vx + 3f, vy + alturaParede * 0.35f, paint)
            // Pixel brilhante no veio
            paint.color = Color.argb(220,
                Color.red(palette.wallDetailColor),
                Color.green(palette.wallDetailColor),
                Color.blue(palette.wallDetailColor))
            canvas.drawRect(vx, vy, vx + 1.5f, vy + 2f, paint)
        }

        // Cristal embutido — aparece em ~10% das paredes
        if (seed % 10 == 0) {
            renderCristalEmbutido(canvas, x + halfW * 0.6f,
                y + tileH + alturaParede * 0.3f, tileW * 0.08f, palette)
        }
    }

    private fun renderCristalEmbutido(
        canvas: Canvas, cx: Float, cy: Float, tamanho: Float, palette: BiomePalette
    ) {
        // Cristal pequeno: losango de 3-4px com brilho
        paint.color = Color.argb(200,
            Color.red(palette.crystalColor),
            Color.green(palette.crystalColor),
            Color.blue(palette.crystalColor))
        path.reset()
        path.moveTo(cx, cy - tamanho)
        path.lineTo(cx + tamanho * 0.6f, cy)
        path.lineTo(cx, cy + tamanho)
        path.lineTo(cx - tamanho * 0.6f, cy)
        path.close()
        canvas.drawPath(path, paint)
        // Pixel de brilho no topo
        paint.color = Color.argb(255,
            (Color.red(palette.crystalColor) + 60).coerceAtMost(255),
            (Color.green(palette.crystalColor) + 60).coerceAtMost(255),
            (Color.blue(palette.crystalColor) + 60).coerceAtMost(255))
        canvas.drawRect(cx - 0.5f, cy - tamanho + 0.5f, cx + 0.5f, cy - tamanho + 1.5f, paint)
    }

    // =========================================================================
    // ENTRADA e SAÍDA
    // =========================================================================

    fun renderEntradaTile(canvas: Canvas, x: Float, y: Float, tileW: Float, tileH: Float) {
        val halfW = tileW / 2f; val halfH = tileH / 2f
        val cx = x + halfW; val cy = y + halfH
        // Base verde escura
        paint.color = Color.rgb(30, 100, 45)
        path.reset(); path.moveTo(cx, y); path.lineTo(x + tileW, cy)
        path.lineTo(cx, y + tileH); path.lineTo(x, cy); path.close()
        canvas.drawPath(path, paint)
        // Losango interno mais claro
        paint.color = Color.rgb(45, 150, 65)
        path.reset(); path.moveTo(cx, cy - halfH * 0.5f); path.lineTo(cx + halfW * 0.5f, cy)
        path.lineTo(cx, cy + halfH * 0.5f); path.lineTo(cx - halfW * 0.5f, cy); path.close()
        canvas.drawPath(path, paint)
        // Outline
        paint.color = Color.rgb(15, 60, 25); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
        path.reset(); path.moveTo(cx, y); path.lineTo(x + tileW, cy)
        path.lineTo(cx, y + tileH); path.lineTo(x, cy); path.close()
        canvas.drawPath(path, paint); paint.style = Paint.Style.FILL
    }

    fun renderSaidaTile(canvas: Canvas, x: Float, y: Float, tileW: Float, tileH: Float) {
        val halfW = tileW / 2f; val halfH = tileH / 2f
        val cx = x + halfW; val cy = y + halfH
        // Base dourada escura
        paint.color = Color.rgb(140, 100, 10)
        path.reset(); path.moveTo(cx, y); path.lineTo(x + tileW, cy)
        path.lineTo(cx, y + tileH); path.lineTo(x, cy); path.close()
        canvas.drawPath(path, paint)
        // Losango interno âmbar
        paint.color = Color.rgb(220, 170, 30)
        path.reset(); path.moveTo(cx, cy - halfH * 0.5f); path.lineTo(cx + halfW * 0.5f, cy)
        path.lineTo(cx, cy + halfH * 0.5f); path.lineTo(cx - halfW * 0.5f, cy); path.close()
        canvas.drawPath(path, paint)
        // Outline
        paint.color = Color.rgb(80, 55, 5); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
        path.reset(); path.moveTo(cx, y); path.lineTo(x + tileW, cy)
        path.lineTo(cx, y + tileH); path.lineTo(x, cy); path.close()
        canvas.drawPath(path, paint); paint.style = Paint.Style.FILL
    }

    // =========================================================================
    // DECORATIVOS — flora de caverna: cogumelos, cristais, raízes, liquens
    // =========================================================================

    fun renderDecorativeTile(
        canvas: Canvas, x: Float, y: Float, tileW: Float, tileH: Float,
        palette: BiomePalette, variant: Int,
        biome: Biome = Biome.MINA_ABANDONADA, tileX: Int = 0, tileY: Int = 0
    ) {
        renderFloorTile(canvas, x, y, tileW, tileH, palette, tileX, tileY)
        val cx = x + tileW / 2f
        val cy = y + tileH / 2f
        when (biome) {
            Biome.MINA_ABANDONADA      -> decorativoMina(canvas, cx, cy, tileW, tileH, palette, variant)
            Biome.RIACHOS_SUBTERRANEOS -> decorativoRiacho(canvas, cx, cy, tileW, tileH, palette, variant)
            Biome.PLANTACOES_ABRIGOS   -> decorativoPlantacao(canvas, cx, cy, tileW, tileH, palette, variant)
            Biome.CONSTRUCOES_ROCHOSAS -> decorativoRocha(canvas, cx, cy, tileW, tileH, palette, variant)
            Biome.POMARES_ABERTURAS    -> decorativoPomar(canvas, cx, cy, tileW, tileH, palette, variant)
            Biome.ERA_DINOSSAUROS      -> decorativoDinossauro(canvas, cx, cy, tileW, tileH, palette, variant)
        }
    }

    // Cogumelo pixel art: haste + chapéu com brilho
    private fun renderCogumelo(
        canvas: Canvas, cx: Float, cy: Float,
        hasteColor: Int, chapeuColor: Int, tamanho: Float
    ) {
        val h = tamanho
        // Haste
        paint.color = hasteColor
        canvas.drawRect(cx - h * 0.15f, cy - h * 0.5f, cx + h * 0.15f, cy + h * 0.1f, paint)
        // Chapéu (semicírculo pixel art)
        paint.color = chapeuColor
        canvas.drawRect(cx - h * 0.4f, cy - h * 0.9f, cx + h * 0.4f, cy - h * 0.4f, paint)
        canvas.drawRect(cx - h * 0.3f, cy - h * 1.1f, cx + h * 0.3f, cy - h * 0.85f, paint)
        // Brilho no chapéu
        paint.color = clarear(chapeuColor, 0.30f)
        canvas.drawRect(cx - h * 0.15f, cy - h * 1.05f, cx, cy - h * 0.85f, paint)
        // Outline escuro
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = escurecer(chapeuColor, 0.40f)
        canvas.drawRect(cx - h * 0.4f, cy - h * 0.9f, cx + h * 0.4f, cy - h * 0.4f, paint)
        paint.style = Paint.Style.FILL
    }

    // Cristal decorativo: losango com brilho
    private fun renderCristalDecorativo(
        canvas: Canvas, cx: Float, cy: Float,
        cor: Int, tamanho: Float
    ) {
        val t = tamanho
        paint.color = escurecer(cor, 0.20f)
        path.reset()
        path.moveTo(cx, cy - t)
        path.lineTo(cx + t * 0.5f, cy - t * 0.3f)
        path.lineTo(cx + t * 0.4f, cy + t * 0.5f)
        path.lineTo(cx - t * 0.4f, cy + t * 0.5f)
        path.lineTo(cx - t * 0.5f, cy - t * 0.3f)
        path.close()
        canvas.drawPath(path, paint)
        paint.color = cor
        path.reset()
        path.moveTo(cx, cy - t * 0.9f)
        path.lineTo(cx + t * 0.4f, cy - t * 0.2f)
        path.lineTo(cx + t * 0.3f, cy + t * 0.4f)
        path.lineTo(cx - t * 0.3f, cy + t * 0.4f)
        path.lineTo(cx - t * 0.4f, cy - t * 0.2f)
        path.close()
        canvas.drawPath(path, paint)
        // Brilho
        paint.color = clarear(cor, 0.50f)
        canvas.drawRect(cx - t * 0.1f, cy - t * 0.85f, cx + t * 0.1f, cy - t * 0.5f, paint)
    }

    private fun decorativoMina(
        canvas: Canvas, cx: Float, cy: Float,
        tileW: Float, tileH: Float, p: BiomePalette, v: Int
    ) {
        val s = tileW * 0.18f
        when (v % 4) {
            // Cogumelo marrom pequeno
            0 -> renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
            // Cristal laranja/âmbar
            1 -> renderCristalDecorativo(canvas, cx, cy - tileH * 0.1f, p.crystalColor, s * 0.9f)
            // Pedra solta com veio dourado
            2 -> {
                paint.color = p.wallColor
                canvas.drawRect(cx - s * 0.7f, cy - s * 0.4f, cx + s * 0.7f, cy + s * 0.4f, paint)
                paint.color = clarear(p.wallColor, 0.15f)
                canvas.drawRect(cx - s * 0.7f, cy - s * 0.4f, cx + s * 0.7f, cy - s * 0.1f, paint)
                paint.color = p.accentColor
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
                canvas.drawLine(cx - s * 0.3f, cy - s * 0.3f, cx + s * 0.2f, cy + s * 0.2f, paint)
                paint.style = Paint.Style.FILL
            }
            // Musgo + líquen na pedra
            else -> {
                paint.color = Color.argb(200, Color.red(p.mossColor), Color.green(p.mossColor), Color.blue(p.mossColor))
                for (i in 0..4) {
                    val mx = cx + (i * 37 % 13 - 6).toFloat() * s * 0.12f
                    val my = cy + (i * 23 % 9 - 4).toFloat() * s * 0.12f
                    canvas.drawRect(mx, my, mx + 2.5f, my + 2.5f, paint)
                }
            }
        }
    }

    private fun decorativoRiacho(
        canvas: Canvas, cx: Float, cy: Float,
        tileW: Float, tileH: Float, p: BiomePalette, v: Int
    ) {
        val s = tileW * 0.18f
        when (v % 4) {
            // Cogumelo azul luminescente
            0 -> renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
            // Cristal ciano
            1 -> renderCristalDecorativo(canvas, cx, cy - tileH * 0.1f, p.crystalColor, s * 0.9f)
            // Poça d'água com reflexo
            2 -> {
                paint.color = Color.argb(160, Color.red(p.accentColor), Color.green(p.accentColor), Color.blue(p.accentColor))
                canvas.drawOval(RectF(cx - s * 0.8f, cy - s * 0.35f, cx + s * 0.8f, cy + s * 0.35f), paint)
                paint.color = Color.argb(100, 255, 255, 255)
                canvas.drawOval(RectF(cx - s * 0.4f, cy - s * 0.2f, cx, cy), paint)
            }
            // Musgo úmido
            else -> {
                paint.color = Color.argb(180, Color.red(p.mossColor), Color.green(p.mossColor), Color.blue(p.mossColor))
                for (i in 0..5) {
                    val mx = cx + (i * 41 % 15 - 7).toFloat() * s * 0.11f
                    val my = cy + (i * 19 % 9 - 4).toFloat() * s * 0.11f
                    canvas.drawRect(mx, my, mx + 2f, my + 2f, paint)
                }
            }
        }
    }

    private fun decorativoPlantacao(
        canvas: Canvas, cx: Float, cy: Float,
        tileW: Float, tileH: Float, p: BiomePalette, v: Int
    ) {
        val s = tileW * 0.18f
        when (v % 4) {
            // Cogumelo roxo
            0 -> renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
            // Cristal roxo
            1 -> renderCristalDecorativo(canvas, cx, cy - tileH * 0.1f, p.crystalColor, s * 0.9f)
            // Raiz exposta
            2 -> {
                paint.color = Color.rgb(92, 60, 30)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                canvas.drawLine(cx - s * 0.6f, cy + s * 0.2f, cx, cy - s * 0.3f, paint)
                canvas.drawLine(cx, cy - s * 0.3f, cx + s * 0.5f, cy + s * 0.1f, paint)
                canvas.drawLine(cx - s * 0.2f, cy - s * 0.1f, cx - s * 0.5f, cy - s * 0.5f, paint)
                paint.style = Paint.Style.FILL
            }
            // Samambaia pixel art
            else -> {
                paint.color = p.mossColor
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
                canvas.drawLine(cx, cy + s * 0.2f, cx - s * 0.5f, cy - s * 0.6f, paint)
                canvas.drawLine(cx, cy + s * 0.2f, cx + s * 0.5f, cy - s * 0.5f, paint)
                canvas.drawLine(cx, cy + s * 0.2f, cx, cy - s * 0.7f, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    private fun decorativoRocha(
        canvas: Canvas, cx: Float, cy: Float,
        tileW: Float, tileH: Float, p: BiomePalette, v: Int
    ) {
        val s = tileW * 0.18f
        when (v % 4) {
            // Cogumelo cinza
            0 -> renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
            // Cristal quartzo
            1 -> renderCristalDecorativo(canvas, cx, cy - tileH * 0.1f, p.crystalColor, s * 0.9f)
            // Pedra polida com reflexo
            2 -> {
                paint.color = p.wallColor
                canvas.drawOval(RectF(cx - s * 0.7f, cy - s * 0.4f, cx + s * 0.7f, cy + s * 0.4f), paint)
                paint.color = clarear(p.wallColor, 0.25f)
                canvas.drawOval(RectF(cx - s * 0.35f, cy - s * 0.35f, cx, cy - s * 0.05f), paint)
            }
            // Poeira + fragmentos
            else -> {
                paint.color = Color.argb(160, Color.red(p.particleColor), Color.green(p.particleColor), Color.blue(p.particleColor))
                for (i in 0..3) {
                    val px = cx + (i * 37 % 11 - 5).toFloat() * s * 0.14f
                    val py = cy + (i * 23 % 7 - 3).toFloat() * s * 0.14f
                    canvas.drawRect(px, py, px + 1.5f, py + 1.5f, paint)
                }
            }
        }
    }

    private fun decorativoPomar(
        canvas: Canvas, cx: Float, cy: Float,
        tileW: Float, tileH: Float, p: BiomePalette, v: Int
    ) {
        val s = tileW * 0.18f
        when (v % 4) {
            // Cogumelo ciano
            0 -> renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
            // Cristal esmeralda
            1 -> renderCristalDecorativo(canvas, cx, cy - tileH * 0.1f, p.crystalColor, s * 0.9f)
            // Flor subterrânea
            2 -> {
                paint.color = p.accentColor
                for (i in 0..4) {
                    val ang = i * 72f * (Math.PI / 180f).toFloat()
                    val px = cx + kotlin.math.cos(ang) * s * 0.4f
                    val py = cy + kotlin.math.sin(ang) * s * 0.25f
                    canvas.drawRect(px - 1.5f, py - 1.5f, px + 1.5f, py + 1.5f, paint)
                }
                paint.color = clarear(p.accentColor, 0.40f)
                canvas.drawRect(cx - 2f, cy - 2f, cx + 2f, cy + 2f, paint)
            }
            // Musgo dourado
            else -> {
                paint.color = Color.argb(200, Color.red(p.mossColor), Color.green(p.mossColor), Color.blue(p.mossColor))
                for (i in 0..4) {
                    val mx = cx + (i * 43 % 13 - 6).toFloat() * s * 0.12f
                    val my = cy + (i * 17 % 9 - 4).toFloat() * s * 0.12f
                    canvas.drawRect(mx, my, mx + 2.5f, my + 2.5f, paint)
                }
            }
        }
    }

    private fun decorativoDinossauro(
        canvas: Canvas, cx: Float, cy: Float,
        tileW: Float, tileH: Float, p: BiomePalette, v: Int
    ) {
        val s = tileW * 0.18f
        when (v % 4) {
            // Cogumelo vermelho
            0 -> renderCogumelo(canvas, cx, cy, p.mushroomColor, p.mushroomCapColor, s)
            // Cristal âmbar
            1 -> renderCristalDecorativo(canvas, cx, cy - tileH * 0.1f, p.crystalColor, s * 0.9f)
            // Osso fóssil
            2 -> {
                paint.color = Color.rgb(200, 185, 155)
                canvas.drawRect(cx - s * 0.6f, cy - s * 0.1f, cx + s * 0.6f, cy + s * 0.1f, paint)
                canvas.drawOval(RectF(cx - s * 0.7f, cy - s * 0.2f, cx - s * 0.45f, cy + s * 0.2f), paint)
                canvas.drawOval(RectF(cx + s * 0.45f, cy - s * 0.2f, cx + s * 0.7f, cy + s * 0.2f), paint)
            }
            // Cinza vulcânica
            else -> {
                paint.color = Color.argb(140, Color.red(p.particleColor), Color.green(p.particleColor), Color.blue(p.particleColor))
                for (i in 0..4) {
                    val px = cx + (i * 31 % 13 - 6).toFloat() * s * 0.13f
                    val py = cy + (i * 19 % 9 - 4).toFloat() * s * 0.13f
                    canvas.drawRect(px, py, px + 2f, py + 2f, paint)
                }
            }
        }
    }

    // =========================================================================
    // createTileBitmap — para SpriteCache (paredes e decorativos)
    // Chão NÃO usa cache — renderizado direto com variação por tile
    // =========================================================================

    fun createTileBitmap(
        tileType: TileType, tileW: Int, tileH: Int, palette: BiomePalette,
        tileX: Int = 0, tileY: Int = 0, biome: Biome = Biome.MINA_ABANDONADA
    ): Bitmap {
        val bitmapH = when (tileType) {
            TileType.WALL -> (tileH * (1f + fatorAlturaParede) + 4f).toInt()
            else -> tileH
        }
        val bitmap = Bitmap.createBitmap(tileW, bitmapH.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        when (tileType) {
            TileType.WALL         -> renderWallTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, tileX, tileY)
            TileType.FLOOR        -> renderFloorTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, tileX, tileY)
            TileType.ENTRADA      -> renderEntradaTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat())
            TileType.SAIDA        -> renderSaidaTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat())
            TileType.DECORATIVE_0 -> renderDecorativeTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, 0, biome, tileX, tileY)
            TileType.DECORATIVE_1 -> renderDecorativeTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, 1, biome, tileX, tileY)
            TileType.DECORATIVE_2 -> renderDecorativeTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, 2, biome, tileX, tileY)
            TileType.DECORATIVE_3 -> renderDecorativeTile(c, 0f, 0f, tileW.toFloat(), tileH.toFloat(), palette, 3, biome, tileX, tileY)
        }
        return bitmap
    }

    // =========================================================================
    // Utilitários de cor
    // =========================================================================

    /** Mistura duas cores com fator t (0=cor1, 1=cor2). */
    private fun misturarCores(cor1: Int, cor2: Int, t: Float): Int {
        val r = (Color.red(cor1) * (1f - t) + Color.red(cor2) * t).toInt()
        val g = (Color.green(cor1) * (1f - t) + Color.green(cor2) * t).toInt()
        val b = (Color.blue(cor1) * (1f - t) + Color.blue(cor2) * t).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    /** Varia a cor por um delta (positivo = clarear, negativo = escurecer). */
    private fun variarCor(color: Int, delta: Int): Int = Color.rgb(
        (Color.red(color) + delta).coerceIn(0, 255),
        (Color.green(color) + delta).coerceIn(0, 255),
        (Color.blue(color) + delta).coerceIn(0, 255)
    )

    private fun clarear(color: Int, factor: Float): Int {
        val f = (factor * 255).toInt()
        return Color.rgb(
            (Color.red(color) + f).coerceAtMost(255),
            (Color.green(color) + f).coerceAtMost(255),
            (Color.blue(color) + f).coerceAtMost(255)
        )
    }

    private fun escurecer(color: Int, factor: Float): Int {
        val f = (factor * 255).toInt()
        return Color.rgb(
            (Color.red(color) - f).coerceAtLeast(0),
            (Color.green(color) - f).coerceAtLeast(0),
            (Color.blue(color) - f).coerceAtLeast(0)
        )
    }
}
