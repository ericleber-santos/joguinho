package com.ericleber.joguinho.renderer

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.ericleber.joguinho.core.GameState

/**
 * SurfaceView principal do jogo.
 *
 * Integra o SurfaceHolder com o Renderer, gerenciando o ciclo de vida da superfície
 * de renderização. O GameLoop chama drawFrame() a cada frame.
 *
 * Requisitos: 8.1, 13.6
 */
class GameSurfaceView(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    lateinit var renderer: Renderer
    lateinit var gameState: GameState

    init {
        // Registra este objeto como callback do SurfaceHolder
        holder.addCallback(this)
        // Mantém a tela ligada durante o jogo
        keepScreenOn = true
    }

    /**
     * Chamado quando a superfície é criada.
     * Inicializa as dimensões do renderer com os valores atuais da superfície.
     */
    override fun surfaceCreated(holder: SurfaceHolder) {
        if (::renderer.isInitialized) {
            val dm = resources.displayMetrics
            renderer.onSurfaceChanged(width, height, dm.density)
        }
    }

    /**
     * Chamado quando a superfície muda de tamanho ou formato.
     * Atualiza o renderer com as novas dimensões e densidade.
     */
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (::renderer.isInitialized) {
            renderer.onSurfaceChanged(width, height, resources.displayMetrics.density)
        }
    }

    /**
     * Chamado quando a superfície é destruída.
     * Libera todos os recursos do renderer (bitmaps, cache).
     */
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (::renderer.isInitialized) {
            renderer.release()
        }
    }

    /**
     * Desenha um frame do jogo.
     * Deve ser chamado pelo GameLoop a cada frame (~60fps).
     *
     * Usa lockHardwareCanvas quando disponível (API 26+) para aceleração por hardware.
     */
    fun drawFrame() {
        if (!::renderer.isInitialized || !::gameState.isInitialized) return

        val surfaceHolder = holder
        val canvas = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                surfaceHolder.lockHardwareCanvas()
            } else {
                surfaceHolder.lockCanvas()
            }
        } catch (e: Exception) {
            null
        } ?: return

        try {
            renderer.render(canvas, gameState)
        } finally {
            try {
                surfaceHolder.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                // Ignora erros ao postar o canvas (ex: superfície destruída)
            }
        }
    }
}
