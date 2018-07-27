package com.miracles.camera;

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup

/**
 * Created by lxw
 */
class TextureViewPreview(val parent: ViewGroup) : CameraPreview() {
    private val mContext = parent.context
    private val mTextureView = TextureView(mContext)
    private var mDisplayOrientation = 0

    init {
        parent.addView(mTextureView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
                setSize(width, height)
                configureTransform()
                dispatchSurfaceChanged()
            }

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                setSize(width, height)
                configureTransform()
                dispatchSurfaceChanged()
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                setSize(0, 0)
                return true
            }
        }
    }

    override fun isReady() = mTextureView.surfaceTexture != null

    override fun setDisplayOrientation(displayOrientation: Int) {
        mDisplayOrientation = displayOrientation
        configureTransform()
    }

    override fun setBufferSize(width: Int, height: Int) {
        mTextureView.surfaceTexture.setDefaultBufferSize(width, height)
    }

    override fun getView() = mTextureView

    override fun getSurfaceTexture(): SurfaceTexture? = mTextureView.surfaceTexture

    override fun getSurface() = Surface(mTextureView.surfaceTexture)

    override fun getOutputClass() = SurfaceTexture::class.java

    private fun configureTransform() {
        val matrix = Matrix()
        val centerX = getWidth() / 2.toFloat()
        val centerY = getHeight() / 2.toFloat()
        if (mDisplayOrientation % 180 == 90) {
            matrix.postRotate(mDisplayOrientation % 360 - 180.toFloat(), centerX, centerY)
        } else if (mDisplayOrientation == 180) {
            matrix.postRotate(180.toFloat(), centerX, centerY)
        }
        mTextureView.setTransform(matrix)
    }
}
