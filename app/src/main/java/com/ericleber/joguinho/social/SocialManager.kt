package com.ericleber.joguinho.social

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import com.ericleber.joguinho.biome.Biome
import com.ericleber.joguinho.biome.BIOME_PALETTES
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference

/**
 * Gerencia o compartilhamento de resultados do jogo em redes sociais.
 *
 * Gera uma imagem 1080x1080 via Canvas com:
 * - Fundo temático do Biome atual
 * - Nome do jogo "Spike na Caverna"
 * - Andar alcançado, tempo total acumulado e total de Maps percorridos
 * - Sprite do Spike em pose de celebração desenhado programaticamente
 *
 * Compartilha via Intent.ACTION_SEND com FileProvider, compatível com
 * WhatsApp, Instagram e demais apps (Requisitos 6.7, 6.8).
 *
 * Usa WeakReference para Context para evitar vazamento de memória (Req. 20.2).
 */
class SocialManager(context: Context) {

    private val contextRef = WeakReference(context.applicationContext)

    // =========================================================================
    // API pública
    // =========================================================================

    /**
     * Gera a imagem de compartilhamento e abre o seletor nativo do Android.
     *
     * @param floorNumber   Andar alcançado pelo Player
     * @param totalTimeMs   Tempo total acumulado em milissegundos
     * @param totalMaps     Total de Maps percorridos
     * @param biome         Biome atual para tema visual (padrão: Mina Abandonada)
     */
    fun share(
        floorNumber: Int,
        totalTimeMs: Long,
        totalMaps: Int,
        biome: Biome = Biome.MINA_ABANDONADA
    ) {
        val ctx = contextRef.get() ?: return
        val bitmap = generateShareImage(floorNumber, totalTimeMs, totalMaps, biome)
        val uri = saveBitmapToCache(ctx, bitmap)
        openShareChooser(ctx, uri)
    }

    /**
     * Gera o Bitmap 1080x1080 com estatísticas e sprite do Spike.
     * Pode ser chamado separadamente para pré-visualização.
     */
    fun generateShareImage(
        floorNumber: Int,
        totalTimeMs: Long,
        totalMaps: Int,
        biome: Biome = Biome.MINA_ABANDONADA
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = BIOME_PALETTES[biome]
        val bgColor = palette?.backgroundColor ?: 0xFF0D0D0D.toInt()
        val accentColor = palette?.accentColor ?: 0xFFD4A017.toInt()

        drawBackground(canvas, bgColor, accentColor)
        drawTitle(canvas, accentColor)
        drawStats(canvas, floorNumber, totalTimeMs, totalMaps, accentColor)
        drawSpikeSprite(canvas, accentColor)
        drawFooter(canvas)

        return bitmap
    }

    // =========================================================================
    // Desenho da imagem
    // =========================================================================

    private fun drawBackground(canvas: Canvas, bgColor: Int, accentColor: Int) {
        val paint = Paint().apply { isAntiAlias = true }

        // Fundo sólido
        paint.color = bgColor
        canvas.drawRect(0f, 0f, SIZE, SIZE, paint)

        // Gradiente simulado: faixa superior mais clara
        paint.color = Color.argb(60,
            Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRect(0f, 0f, SIZE, SIZE * 0.35f, paint)

        // Borda decorativa dupla
        paint.style = Paint.Style.STROKE
        paint.color = accentColor
        paint.strokeWidth = 14f
        canvas.drawRect(18f, 18f, SIZE - 18f, SIZE - 18f, paint)
        paint.strokeWidth = 4f
        paint.color = Color.argb(120,
            Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRect(32f, 32f, SIZE - 32f, SIZE - 32f, paint)
        paint.style = Paint.Style.FILL

        // Estrelas decorativas nos cantos
        paint.color = Color.argb(80,
            Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        drawStar(canvas, 80f, 80f, 28f, paint)
        drawStar(canvas, SIZE - 80f, 80f, 28f, paint)
        drawStar(canvas, 80f, SIZE - 80f, 28f, paint)
        drawStar(canvas, SIZE - 80f, SIZE - 80f, 28f, paint)
    }

    private fun drawTitle(canvas: Canvas, accentColor: Int) {
        val paint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Título principal
        paint.color = accentColor
        paint.textSize = 96f
        canvas.drawText("Spike na Caverna", SIZE / 2f, 160f, paint)

        // Linha separadora
        paint.color = Color.argb(100,
            Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawLine(100f, 190f, SIZE - 100f, 190f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawStats(
        canvas: Canvas,
        floorNumber: Int,
        totalTimeMs: Long,
        totalMaps: Int,
        accentColor: Int
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Andar — destaque principal
        paint.color = Color.WHITE
        paint.textSize = 110f
        canvas.drawText("Andar $floorNumber", SIZE / 2f, 330f, paint)

        // Subtítulo do andar
        paint.color = Color.argb(180, 255, 255, 255)
        paint.textSize = 52f
        canvas.drawText("alcançado!", SIZE / 2f, 400f, paint)

        // Linha divisória
        paint.color = Color.argb(60, 255, 255, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawLine(150f, 430f, SIZE - 150f, 430f, paint)
        paint.style = Paint.Style.FILL

        // Tempo total
        paint.color = Color.argb(200, 255, 255, 255)
        paint.textSize = 58f
        canvas.drawText("⏱  ${formatTime(totalTimeMs)}", SIZE / 2f, 510f, paint)

        // Total de Maps
        paint.textSize = 54f
        canvas.drawText("🗺  $totalMaps mapas explorados", SIZE / 2f, 590f, paint)
    }

    private fun drawSpikeSprite(canvas: Canvas, accentColor: Int) {
        // Spike em pose de celebração — desenhado programaticamente via Canvas
        // Posicionado na parte inferior central da imagem
        val cx = SIZE / 2f
        val cy = 790f
        val scale = 3.2f  // Escala do sprite para 1080px

        val paint = Paint().apply {
            isAntiAlias = false  // Estética pixel art
            isFilterBitmap = false
        }

        // --- Corpo (branco com manchas pretas — viralata brasileiro) ---
        paint.color = 0xFFF5F5F5.toInt()
        canvas.drawRoundRect(RectF(cx - 55f * scale, cy - 30f * scale,
            cx + 55f * scale, cy + 20f * scale), 18f * scale, 18f * scale, paint)

        // Mancha preta no dorso
        paint.color = 0xFF1A1A1A.toInt()
        canvas.drawRoundRect(RectF(cx - 30f * scale, cy - 28f * scale,
            cx + 20f * scale, cy - 8f * scale), 10f * scale, 10f * scale, paint)

        // --- Cabeça ---
        paint.color = 0xFFF5F5F5.toInt()
        canvas.drawCircle(cx + 45f * scale, cy - 35f * scale, 32f * scale, paint)

        // Focinho
        paint.color = 0xFFE8C8A0.toInt()
        canvas.drawRoundRect(RectF(cx + 30f * scale, cy - 22f * scale,
            cx + 68f * scale, cy - 8f * scale), 8f * scale, 8f * scale, paint)

        // Nariz
        paint.color = 0xFF2A1A1A.toInt()
        canvas.drawCircle(cx + 62f * scale, cy - 17f * scale, 5f * scale, paint)

        // Olho (animado — aberto e brilhante para celebração)
        paint.color = 0xFF1A1A1A.toInt()
        canvas.drawCircle(cx + 52f * scale, cy - 38f * scale, 7f * scale, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cx + 54f * scale, cy - 40f * scale, 3f * scale, paint)

        // Mancha preta no olho
        paint.color = 0xFF1A1A1A.toInt()
        canvas.drawCircle(cx + 42f * scale, cy - 42f * scale, 10f * scale, paint)
        paint.color = 0xFFF5F5F5.toInt()
        canvas.drawCircle(cx + 42f * scale, cy - 42f * scale, 6f * scale, paint)

        // --- Orelhas erguidas (estado entusiasmado) ---
        paint.color = 0xFFF5F5F5.toInt()
        // Orelha esquerda
        val orelhaEsq = Path().apply {
            moveTo(cx + 22f * scale, cy - 55f * scale)
            lineTo(cx + 10f * scale, cy - 85f * scale)
            lineTo(cx + 35f * scale, cy - 65f * scale)
            close()
        }
        canvas.drawPath(orelhaEsq, paint)
        // Orelha direita
        val orelhaDireita = Path().apply {
            moveTo(cx + 55f * scale, cy - 60f * scale)
            lineTo(cx + 70f * scale, cy - 88f * scale)
            lineTo(cx + 72f * scale, cy - 60f * scale)
            close()
        }
        canvas.drawPath(orelhaDireita, paint)
        // Interior das orelhas (rosa)
        paint.color = 0xFFFFB3BA.toInt()
        val orelhaEsqInt = Path().apply {
            moveTo(cx + 22f * scale, cy - 58f * scale)
            lineTo(cx + 14f * scale, cy - 78f * scale)
            lineTo(cx + 32f * scale, cy - 66f * scale)
            close()
        }
        canvas.drawPath(orelhaEsqInt, paint)

        // --- Patas dianteiras levantadas (celebração) ---
        paint.color = 0xFFF5F5F5.toInt()
        // Pata esquerda levantada
        canvas.drawRoundRect(RectF(cx - 60f * scale, cy - 45f * scale,
            cx - 40f * scale, cy - 10f * scale), 8f * scale, 8f * scale, paint)
        // Pata direita levantada
        canvas.drawRoundRect(RectF(cx + 10f * scale, cy - 10f * scale,
            cx + 30f * scale, cy + 20f * scale), 8f * scale, 8f * scale, paint)

        // Manchas nas patas
        paint.color = 0xFF1A1A1A.toInt()
        canvas.drawRoundRect(RectF(cx - 58f * scale, cy - 44f * scale,
            cx - 42f * scale, cy - 28f * scale), 6f * scale, 6f * scale, paint)

        // --- Rabo em alta velocidade (arco animado) ---
        paint.color = 0xFFF5F5F5.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f * scale
        paint.strokeCap = Paint.Cap.ROUND
        val rabo = Path().apply {
            moveTo(cx - 52f * scale, cy - 10f * scale)
            quadTo(cx - 90f * scale, cy - 60f * scale,
                cx - 70f * scale, cy - 80f * scale)
        }
        canvas.drawPath(rabo, paint)
        paint.style = Paint.Style.FILL

        // --- Balão de fala com coração (Req. 11.9) ---
        paint.color = Color.WHITE
        val balaoRect = RectF(cx - 10f * scale, cy - 100f * scale,
            cx + 60f * scale, cy - 65f * scale)
        canvas.drawRoundRect(balaoRect, 10f * scale, 10f * scale, paint)
        // Triângulo do balão
        val triBalao = Path().apply {
            moveTo(cx + 5f * scale, cy - 65f * scale)
            lineTo(cx + 15f * scale, cy - 55f * scale)
            lineTo(cx + 25f * scale, cy - 65f * scale)
            close()
        }
        canvas.drawPath(triBalao, paint)
        // Coração no balão
        paint.color = 0xFFE53935.toInt()
        paint.textSize = 28f * scale
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("♥", cx + 25f * scale, cy - 72f * scale, paint)
        paint.textAlign = Paint.Align.CENTER

        // --- Partículas de celebração ao redor do Spike ---
        val coresParticula = intArrayOf(
            0xFFFFD700.toInt(), 0xFFFF6B35.toInt(),
            0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), accentColor
        )
        val tintaParticula = Paint().apply { isAntiAlias = true }
        for (i in 0..11) {
            val angulo = (i * 30.0) * Math.PI / 180.0
            val raio = 110f * scale
            val px = cx + (Math.cos(angulo) * raio).toFloat()
            val py = cy - 20f * scale + (Math.sin(angulo) * raio * 0.5f).toFloat()
            tintaParticula.color = coresParticula[i % coresParticula.size]
            canvas.drawCircle(px, py, 6f * scale, tintaParticula)
        }
    }

    private fun drawFooter(canvas: Canvas) {
        val paint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = Color.argb(140, 200, 200, 200)
            textSize = 42f
        }
        canvas.drawText("Jogue você também! #SpikeNaCaverna", SIZE / 2f, SIZE - 55f, paint)
    }

    // =========================================================================
    // Utilitários
    // =========================================================================

    /** Salva o bitmap em cache interno e retorna URI via FileProvider. */
    private fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri {
        val file = File(context.cacheDir, SHARE_FILE_NAME)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    /** Abre o seletor nativo de compartilhamento do Android (Req. 6.8). */
    private fun openShareChooser(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar resultado"))
    }

    /** Formata milissegundos para hh:mm:ss (exibição de tempo total acumulado). */
    private fun formatTime(ms: Long): String {
        val horas = ms / 3_600_000
        val minutos = (ms % 3_600_000) / 60_000
        val segundos = (ms % 60_000) / 1_000
        return if (horas > 0) "%dh %02dm %02ds".format(horas, minutos, segundos)
        else "%02dm %02ds".format(minutos, segundos)
    }

    /** Desenha uma estrela de 5 pontas centrada em (cx, cy) com raio externo dado. */
    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = Path()
        val innerRadius = radius * 0.4f
        for (i in 0..9) {
            val angulo = (i * 36.0 - 90.0) * Math.PI / 180.0
            val r = if (i % 2 == 0) radius else innerRadius
            val x = cx + (Math.cos(angulo) * r).toFloat()
            val y = cy + (Math.sin(angulo) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    companion object {
        private const val IMAGE_SIZE = 1080
        private const val SIZE = IMAGE_SIZE.toFloat()
        private const val SHARE_FILE_NAME = "spike_resultado.png"
    }
}
