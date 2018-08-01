package com.miracles.camera

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.Camera
import android.os.Build
import android.support.v4.util.SparseArrayCompat
import android.util.SparseIntArray
import android.view.SurfaceHolder
import java.io.IOException


/**
 * Created by lxw
 */
@Suppress("DEPRECATION")
class Camera1(preview: CameraPreview, callback: CameraFunctions.Callback) : CameraDevice(preview, callback) {
    companion object {
        private const val INVALID_CAMERA_ID = -1
        private val FLASH_MODES = SparseArrayCompat<String>()
        private val INTERNAL_FACINGS = SparseIntArray()

        init {
            FLASH_MODES.put(CameraFunctions.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF)
            FLASH_MODES.put(CameraFunctions.FLASH_ON, Camera.Parameters.FLASH_MODE_ON)
            FLASH_MODES.put(CameraFunctions.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH)
            FLASH_MODES.put(CameraFunctions.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO)
            FLASH_MODES.put(CameraFunctions.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE)

            INTERNAL_FACINGS.put(CameraFunctions.FACING_FRONT, Camera.CameraInfo.CAMERA_FACING_FRONT)
            INTERNAL_FACINGS.put(CameraFunctions.FACING_BACK, Camera.CameraInfo.CAMERA_FACING_BACK)
        }
    }

    private var mCamera: Camera? = null
    private val mCameraInfo = Camera.CameraInfo()
    private var mCameraId = INVALID_CAMERA_ID
    private var mCameraParameters: Camera.Parameters? = null
    private val mPreviewSizes = arrayListOf<Size>()
    private val mPictureSizes = arrayListOf<Size>()
    private var mShowingPreview = false
    private var mLastPreviewTimeStamp = 0L

    init {
        preview.callback = object : CameraPreview.PreviewCallback {
            override fun onSurfaceChanged() {
                setUpPreview()
                adjustCameraParameters()
            }
        }
    }

    override fun open(): Boolean {
        chooseCamera()
        openCamera()
        if (preview.isReady()) {
            setUpPreview()
        }
        mCamera?.startPreview()
        mShowingPreview = true
        if (isRecordingFrame()) {
            resumeFrameRecording()
        }
        return true
    }

    override fun close() {
        stopPreview()
        releaseCamera()
    }

    override fun setAutoFocus(autoFocus: Boolean) {
        if (setAutoFocusInternal(autoFocus)) {
            mCamera?.parameters = mCameraParameters
        }
    }

    private fun setAutoFocusInternal(autoFocus: Boolean): Boolean {
        mAutoFocus = autoFocus
        val parameter = mCameraParameters ?: return false
        return if (isCameraOpened()) {
            val modes = parameter.supportedFocusModes
            if (autoFocus && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameter.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                parameter.focusMode = Camera.Parameters.FOCUS_MODE_FIXED
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
                parameter.focusMode = Camera.Parameters.FOCUS_MODE_INFINITY
            } else {
                parameter.focusMode = modes[0]
            }
            true
        } else {
            false
        }
    }

    override fun setFlash(flash: Int) {
        if (setFlashInternal(flash)) {
            mCamera?.parameters = mCameraParameters
        }
    }

    private fun setFlashInternal(flash: Int): Boolean {
        val parameters = mCameraParameters ?: return false
        parameters.focusAreas
        if (isCameraOpened()) {
            val modes = parameters.supportedFlashModes
            val mode = FLASH_MODES.get(flash)
            if (modes != null && modes.contains(mode)) {
                parameters.flashMode = mode
                mFlash = flash
                return true
            }
            if (modes == null || !modes.contains(mode)) {
                parameters.flashMode = FLASH_MODES.get(CameraFunctions.FLASH_OFF)
                mFlash = CameraFunctions.FLASH_OFF
                return true
            }
            return false
        } else {
            mFlash = flash
            return false
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setUpPreview() {
        try {
            val camera = mCamera ?: return
            if (preview.getOutputClass() === SurfaceHolder::class.java) {
                val needsToStopPreview = mShowingPreview && Build.VERSION.SDK_INT < 14
                if (needsToStopPreview) {
                    camera.stopPreview()
                }
                camera.setPreviewDisplay(preview.getSurfaceHolder())
                if (needsToStopPreview) {
                    camera.startPreview()
                }
            } else {
                camera.setPreviewTexture(preview.getSurfaceTexture())
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun stopPreview() {
        if (!mShowingPreview) return
        mCamera?.run {
            setPreviewCallbackWithBuffer(null)
            stopPreview()
        }
        mShowingPreview = false
    }

    private fun chooseCamera() {
        val internal = INTERNAL_FACINGS.get(facing)
        for (index in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(index, mCameraInfo)
            if (mCameraInfo.facing == internal) {
                mCameraId = index
                return
            }
        }
    }

    private fun openCamera() {
        releaseCamera()
        mCamera = Camera.open(mCameraId)
        val camera = mCamera ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            camera.enableShutterSound(false)
        }
        mCameraParameters = camera.parameters
        mPreviewSizes.clear()
        for (size in mCameraParameters?.supportedPreviewSizes ?: arrayListOf()) {
            mPreviewSizes.add(Size(size.width, size.height))
        }
        mPictureSizes.clear()
        for (size in mCameraParameters?.supportedPictureSizes ?: arrayListOf()) {
            mPictureSizes.add(Size(size.width, size.height))
        }
        adjustCameraParameters()
        camera.setDisplayOrientation(calcDisplayOrientation(displayOrientation))
        callback.onCameraOpened()
    }

    private fun adjustCameraParameters() {
        val parameters = mCameraParameters ?: return
        val previewSize = getCameraSizeStrategy(STRATEGY_PREVIEW_SIZE).chooseSize(preview, displayOrientation, mCameraInfo.orientation, facing, mPreviewSizes)
        cacheCameraSize(SIZE_PREVIEW, previewSize)
        cacheCameraSize(SIZE_RECORD, previewSize)
        val pictureSize = getCameraSizeStrategy(STRATEGY_PICTURE_SIZE).chooseSize(preview, displayOrientation, mCameraInfo.orientation, facing, mPictureSizes)
        cacheCameraSize(SIZE_PICTURE, pictureSize)
        if (mShowingPreview) {
            mCamera?.stopPreview()
        }
        parameters.setPreviewSize(previewSize.width, previewSize.height)
        logMED("previewSize is $previewSize")
        parameters.setPictureSize(pictureSize.width, pictureSize.height)
        logMED("pictureSize is $pictureSize")
        parameters.setRotation(calcCameraRotation(displayOrientation))
        parameters.setRecordingHint(true)
        setAutoFocusInternal(mAutoFocus)
        setFlashInternal(mFlash)
        mCamera?.parameters = parameters
        if (mShowingPreview) {
            mCamera?.startPreview()
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun updateDisplayOrientation(displayOrientation: Int) {
        val camera = mCamera ?: return
        mCameraParameters?.setRotation(calcCameraRotation(displayOrientation))
        camera.parameters = mCameraParameters
        val needsToStopPreview = mShowingPreview && Build.VERSION.SDK_INT < 14
        if (needsToStopPreview) {
            camera.stopPreview()
        }
        camera.setDisplayOrientation(calcDisplayOrientation(displayOrientation))
        if (needsToStopPreview) {
            camera.startPreview()
        }
    }

    override fun takePicture() {
        if (!isCameraOpened()) {
            throw IllegalStateException("Camera is not ready. Call start() before takePicture().")
        }
        val camera = mCamera ?: return
        if (mAutoFocus) {
            camera.cancelAutoFocus()
            camera.autoFocus { _, _ ->
                takePictureInternal()
            }
        } else {
            takePictureInternal()
        }
    }

    private fun takePictureInternal() {
        if (!mPictureCaptureInProgress.getAndSet(true)) {
            mCamera?.takePicture({
                callback.onStartCapturePicture()
            }, null, { data, camera ->
                mPictureCaptureInProgress.set(false)
                callback.onPictureTaken(data)
                camera.cancelAutoFocus()
                camera.startPreview()
            })
        }

    }

    override fun startRecord() {
        if (mRecordingFrameInProgress.getAndSet(true)) return
        callback.onStartRecordingFrame(timeStampInNs())
        resumeFrameRecording()
    }

    private fun resumeFrameRecording() {
        val cam = mCamera ?: return
        val previewSize = cam.parameters.previewSize
        val sizeInByte = previewSize.width * previewSize.height * 3 / 2
        val maxFactor = getCameraSizeStrategy(STRATEGY_PREVIEW_SIZE).bytesPoolSize(com.miracles.camera.Size(previewSize.width, previewSize.height))
        if (mPreviewBytesPool.perSize != sizeInByte) {
            mPreviewBytesPool.clear()
            mPreviewBytesPool = ByteArrayPool(maxFactor, sizeInByte, maxFactor)
        } else {
            mPreviewBytesPool.initialize(maxFactor)
        }
        for (x in 0..2) {
            cam.addCallbackBuffer(mPreviewBytesPool.getBytes())
        }
        val format = cam.parameters.previewFormat
        cam.setPreviewCallbackWithBuffer { data, _ ->
            if (!mRecordingFrameInProgress.get() || data?.size ?: 0 == 0) {
                cam.addCallbackBuffer(data)
                return@setPreviewCallbackWithBuffer
            }
            if (mLastPreviewTimeStamp == 0L) {
                mLastPreviewTimeStamp = timeStampInNs()
            }
            logMED("---->recoring frame gap=${(timeStampInNs() - mLastPreviewTimeStamp) / 1e6.toInt()}")
            mLastPreviewTimeStamp = timeStampInNs()
            callback.onFrameRecording(data, data.size, mPreviewBytesPool, previewSize.width, previewSize.height, format,
                    calcCameraRotation(displayOrientation), facing, timeStampInNs())
            cam.addCallbackBuffer(mPreviewBytesPool.getBytes())
        }
    }

    override fun stopRecord() {
        if (!mRecordingFrameInProgress.get()) return
        mCamera?.setPreviewCallbackWithBuffer(null)
        callback.onStopRecordingFrame(timeStampInNs())
        mPreviewBytesPool.clear()
        mRecordingFrameInProgress.set(false)
    }

    override fun isCameraOpened() = mCamera != null

    private fun releaseCamera() {
        val cam = mCamera ?: return
        cam.release()
        mCamera = null
        callback.onCameraClosed()
    }

    //Note:setZoom must be after setPreviewSize.
    override fun setZoom(zoom: Int) {
        val params = mCameraParameters ?: return
        val supported = params.isZoomSupported
        if (!supported) return
        var z = zoom
        if (z <= ZOOM_MIN) {
            z = ZOOM_MIN
        } else if (z >= ZOOM_MAX) {
            z = ZOOM_MAX
        }
        params.zoom = constraintZoom(z, ZOOM_MAX, params.maxZoom)
        mCamera?.parameters = mCameraParameters
    }

    override fun getZoom(): Int {
        val params = mCamera?.parameters ?: return ZOOM_MIN
        val supported = params.isZoomSupported
        if (!supported) return ZOOM_MIN
        return if (!supported) {
            ZOOM_MIN
        } else {
            constraintZoom(params.zoom, params.maxZoom, ZOOM_MAX)
        }
    }

    override fun focus(rect: Rect?, cb: ((Boolean) -> Unit)?) {
        val cam = mCamera
        val params = mCameraParameters
        if (cam == null || params == null) {
            cb?.invoke(false)
            return
        }
        if (rect != null && rect.width() > 0 && rect.height() > 0) {
            val maxAreas = params.maxNumFocusAreas
            if (maxAreas > 0) {
                val focusArea = getFocusArea(rect)
                if (focusArea.width() > 0 && focusArea.height() > 0) {
                    params.focusAreas = arrayListOf(Camera.Area(focusArea, 1000))
                }
            }
        }
        cam.cancelAutoFocus()
        cam.autoFocus { success, _ ->
            cb?.invoke(success)
            cam.autoFocus(null)
        }
    }

    private fun getFocusArea(rect: Rect): Rect {
        val previewSize = getSize(SIZE_PREVIEW)
        if (previewSize.height < 0 || previewSize.width < 0 || !preview.isReady()) {
            return Rect()
        }
        val matrix = Matrix()
        val prf = RectF(0f, 0f, preview.getWidth().toFloat(), preview.getHeight().toFloat())
        val pzrf = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        matrix.postRotate(-displayOrientation.toFloat())
        matrix.setRectToRect(prf, pzrf, Matrix.ScaleToFit.FILL)
        matrix.postRotate(-calcCameraRotation(0).toFloat())
        matrix.setRectToRect(pzrf, RectF(-1000f, -1000f, 1000f, 1000f), Matrix.ScaleToFit.FILL)
        val dst = RectF(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
        matrix.mapRect(dst)
        return Rect(dst.left.toInt(), dst.top.toInt(), dst.right.toInt(), dst.bottom.toInt())
    }

    /**
     * Calculate camera display orientation
     */
    private fun calcDisplayOrientation(screenOrientationDegrees: Int): Int {
        return if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (360 - (mCameraInfo.orientation + screenOrientationDegrees) % 360) % 360
        } else {  // back-facing
            (mCameraInfo.orientation - screenOrientationDegrees + 360) % 360
        }
    }

    /**
     * Calculate camera rotation( pictures's orientation clockwise )
     */
    private fun calcCameraRotation(screenOrientationDegrees: Int): Int {
        return if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (mCameraInfo.orientation + screenOrientationDegrees) % 360
        } else {  // back-facing
            val landscapeFlip = if (screenOrientationDegrees == 90 || screenOrientationDegrees == 270) 180 else 0
            (mCameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360
        }
    }
}