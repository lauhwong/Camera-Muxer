package com.miracles.camera

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View

/**
 * Created by lxw
 */
abstract class CameraPreview {
    internal var callback: PreviewCallback? = null
    private var mWidth = 0
    private var mHeight = 0

    interface PreviewCallback {
        fun onSurfaceChanged()
    }

    protected fun dispatchSurfaceChanged() {
        callback?.onSurfaceChanged()
    }

    /**
     * info for user choose specify size strategy for preview.
     */
    abstract fun isReady(): Boolean

    internal fun setSize(width: Int, height: Int) {
        mWidth = width
        mHeight = height
    }

    fun getWidth() = mWidth

    fun getHeight() = mHeight

    abstract fun setDisplayOrientation(displayOrientation: Int)
    /**
     * retrieve view's details for camera preview...
     */
    open fun setBufferSize(width: Int, height: Int) {

    }

    abstract fun getView(): View

    abstract fun getSurface(): Surface

    abstract fun getOutputClass(): Class<*>

    open fun getSurfaceHolder(): SurfaceHolder? = null

    open fun getSurfaceTexture(): SurfaceTexture? = null

}