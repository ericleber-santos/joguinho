package com.ericleber.joguinho

import android.graphics.Canvas
import android.graphics.Rect
import android.view.Surface
import android.view.SurfaceHolder

/**
 * Implementação falsa de SurfaceHolder para uso em testes de integração.
 * Evita a necessidade de uma Surface real do Android.
 */
class FakeSurfaceHolder : SurfaceHolder {

    override fun addCallback(callback: SurfaceHolder.Callback?) {}
    override fun removeCallback(callback: SurfaceHolder.Callback?) {}
    override fun isCreating(): Boolean = false
    override fun setType(type: Int) {}
    override fun setFixedSize(width: Int, height: Int) {}
    override fun setSizeFromLayout() {}
    override fun setFormat(format: Int) {}
    override fun setKeepScreenOn(screenOn: Boolean) {}
    override fun lockCanvas(): Canvas? = null
    override fun lockCanvas(dirty: Rect?): Canvas? = null
    override fun unlockCanvasAndPost(canvas: Canvas?) {}
    override fun getSurface(): Surface? = null
    override fun getSurfaceFrame(): Rect = Rect(0, 0, 1280, 720)
}
