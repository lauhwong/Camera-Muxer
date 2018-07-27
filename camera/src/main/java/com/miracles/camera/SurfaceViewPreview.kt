package com.miracles.camera

import android.support.v4.view.ViewCompat
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup

/**
 * Created by lxw
 */
class SurfaceViewPreview(val parent: ViewGroup) : CameraPreview() {
    private val mCtx = parent.context
    private val mSurfaceView = SurfaceView(mCtx)

    init {
        parent.addView(mSurfaceView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mSurfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                setSize(width, height)
                if (!ViewCompat.isInLayout(mSurfaceView)) {
                    dispatchSurfaceChanged()
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                setSize(0, 0)
            }

            override fun surfaceCreated(holder: SurfaceHolder) {
            }
        })
    }

    override fun isReady() = getWidth() != 0 && getHeight() != 0

    override fun getSurfaceHolder(): SurfaceHolder? = mSurfaceView.holder

    override fun setDisplayOrientation(displayOrientation: Int) {
        //no-op
    }

    override fun getView() = mSurfaceView

    override fun getSurface() = mSurfaceView.holder.surface

    override fun getOutputClass() = SurfaceView::class.java
}