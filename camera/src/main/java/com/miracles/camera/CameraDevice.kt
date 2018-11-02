package com.miracles.camera

import android.os.Build
import android.os.SystemClock
import android.util.SparseArray
import java.lang.IllegalArgumentException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by lxw
 */
abstract class CameraDevice(val preview: CameraPreview, val callback: CameraFunctions.Callback) : CameraFunctions() {
    protected var mPreviewBytesPool: ByteArrayPool = ByteArrayPool.EMPTY
    protected val mPictureCaptureInProgress = AtomicBoolean(false)
    protected val mRecordingFrameInProgress = AtomicBoolean(false)
    protected val mSizeStrategyMaps = SparseArray<ChooseSizeStrategy>()
    protected val mSizeMaps = SparseArray<Size>()
    protected val mDefaultChooseSizeStrategy = ChooseSizeStrategy.screenAspectRatioStrategy(preview.getView().context)

    override fun isCapturingPicture() = mPictureCaptureInProgress.get()

    override fun isRecordingFrame() = mRecordingFrameInProgress.get()

    override fun setCameraSizeStrategy(kind: Int, strategy: ChooseSizeStrategy) {
        if (isCapturingPicture() || isRecordingFrame()) return
        val oldStrategy = mSizeStrategyMaps[kind]
        if (oldStrategy != strategy) {
            mSizeStrategyMaps.put(kind, strategy)
            if (isCameraOpened()) {
                close()
                open()
            }
        }
    }

    override fun getCameraSizeStrategy(kind: Int): ChooseSizeStrategy {
        val oldStrategy = mSizeStrategyMaps[kind]
        if (oldStrategy != null) return oldStrategy
        return when (kind) {
            STRATEGY_PREVIEW_SIZE -> mDefaultChooseSizeStrategy
            STRATEGY_PICTURE_SIZE -> mDefaultChooseSizeStrategy
            STRATEGY_RECORD_PREVIEW_SIZE -> mDefaultChooseSizeStrategy
            else -> throw IllegalArgumentException("非法参数 kind=$kind !")
        }
    }

    protected fun timeStampInNs() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        SystemClock.elapsedRealtimeNanos()
    } else {
        SystemClock.elapsedRealtime() * 1000
    }

    fun getSize(kind: Int): Size {
        val size = mSizeMaps.get(kind)
        return size ?: Size(-1, -1)
    }

    protected fun cacheCameraSize(kind: Int, size: Size) {
        mSizeMaps.put(kind, size)
    }

    protected fun constraintZoom(zoom: Int, maxZoom: Int, maxConstraintZoom: Int) = (zoom.toFloat() / maxZoom * maxConstraintZoom).toInt()
}