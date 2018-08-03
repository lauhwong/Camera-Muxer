package com.miracles.camera

import android.graphics.Rect

/**
 * Created by lxw
 */
abstract class CameraFunctions {

    companion object {
        const val FACING_FRONT = 0
        const val FACING_BACK = 1

        const val ORIENTATION_0 = 0
        const val ORIENTATION_90 = 90
        const val ORIENTATION_180 = 180
        const val ORIENTATION_270 = 270

        const val FLASH_OFF = 0
        const val FLASH_ON = 1
        const val FLASH_TORCH = 2
        const val FLASH_AUTO = 3
        const val FLASH_RED_EYE = 4

        const val STRATEGY_PREVIEW_SIZE = 0
        const val STRATEGY_PICTURE_SIZE = 1
        const val STRATEGY_RECORD_PREVIEW_SIZE = 2

        const val SIZE_PREVIEW = 0
        const val SIZE_PICTURE = 1
        const val SIZE_RECORD = 2

        const val ZOOM_MIN = 1
        const val ZOOM_MAX = 10
    }

    private val facings = arrayOf(FACING_FRONT, FACING_BACK)
    var facing: Int = facings[1]
        get() {
            return if (field !in facings) facings[1] else field
        }
        set(value) {
            if (value != field && value in facings) {
                field = value
                if (isCameraOpened()) {
                    close()
                    open()
                }
            }
        }

    private val orientations = arrayOf(ORIENTATION_0, ORIENTATION_90, ORIENTATION_180, ORIENTATION_270)
    var displayOrientation = 0
        get() {
            return if (field !in orientations) orientations[0] else field
        }
        set(value) {
            if (value != field && value in orientations) {
                field = value
                updateDisplayOrientation(value)
            }
        }

    protected var mAutoFocus: Boolean = true

    protected val flashes = arrayOf(FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO, FLASH_RED_EYE)
    protected var mFlash = FLASH_OFF

    abstract fun open(): Boolean

    abstract fun close()

    abstract fun setAutoFocus(autoFocus: Boolean)

    fun getAutoFocus() = mAutoFocus

    abstract fun setFlash(flash: Int)

    fun getFlash() = mFlash

    abstract fun takePicture()

    abstract fun startRecord()

    abstract fun stopRecord()

    abstract fun isCameraOpened(): Boolean

    abstract fun updateDisplayOrientation(displayOrientation: Int)

    abstract fun isCapturingPicture(): Boolean

    abstract fun isRecordingFrame(): Boolean

    abstract fun setCameraSizeStrategy(kind: Int, strategy: ChooseSizeStrategy)

    abstract fun getCameraSizeStrategy(kind: Int): ChooseSizeStrategy

    abstract fun setZoom(zoom: Int)

    abstract fun getZoom(): Int

    abstract fun focus(focusRect: Rect?, meteringRect: Rect?, cb: ((Boolean) -> Unit)?)

    interface Callback {

        fun onCameraOpened() {}

        fun onCameraClosed() {}

        fun onStartCapturePicture() {}

        fun onPictureTaken(data: ByteArray) {}

        fun onStartRecordingFrame(timeStampInNs: Long) {}
        /**
         * result uncompressed yuv data.
         */
        fun onFrameRecording(data: ByteArray, len: Int, bytesPool: ByteArrayPool, width: Int, height: Int, format: Int,
                             orientation: Int, facing: Int, timeStampInNs: Long) {
        }

        fun onStopRecordingFrame(timeStampInNs: Long) {}

    }
}