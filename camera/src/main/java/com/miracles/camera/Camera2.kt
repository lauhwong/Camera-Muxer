package com.miracles.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.support.annotation.RequiresApi
import android.util.SparseIntArray
import java.nio.ByteBuffer


/**
 * Created by lxw
 */
@RequiresApi(21)
open class Camera2(preview: CameraPreview, ctx: Context, callback: CameraFunctions.Callback) : CameraDevice(preview, callback) {
    companion object {
        //Capture state.
        private const val STATE_PREVIEW = 0
        private const val STATE_LOCKING = 1
        private const val STATE_LOCKED = 2
        private const val STATE_PRECAPTURE = 3
        private const val STATE_WAITING = 4
        private const val STATE_CAPTURING = 5
        private val INTERNAL_FACINGS = SparseIntArray()

        init {
            INTERNAL_FACINGS.put(CameraFunctions.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT)
            INTERNAL_FACINGS.put(CameraFunctions.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK)
        }
    }

    private val mCameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var mCameraId: String? = null
    private var mCameraCharacteristics: CameraCharacteristics? = null
    private val mPreviewSizes = arrayListOf<Size>()
    private val mPictureSizes = arrayListOf<Size>()
    private val mRecordSizes = arrayListOf<Size>()
    private var mPictureImageReader: ImageReader? = null
    private var mRecordImageReader: ImageReader? = null
    private var mRecordFormat: Int = -1
    private var mImageFormat: Int = -1
    private var mCamera: android.hardware.camera2.CameraDevice? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    private var mCaptureSession: CameraCaptureSession? = null
    /**
     * camera2 open callback
     */
    private val mCameraDeviceCallback = object : android.hardware.camera2.CameraDevice.StateCallback() {
        override fun onOpened(camera: android.hardware.camera2.CameraDevice) {
            mCamera = camera
            callback.onCameraOpened()
            startCaptureSession()
        }

        override fun onDisconnected(camera: android.hardware.camera2.CameraDevice) {
            mCamera = null
        }

        override fun onError(camera: android.hardware.camera2.CameraDevice, error: Int) {
            logMED("Camera2 State onError: id=${camera.id} ,error=$error")
            mCamera = null
        }
    }
    /**
     * Image capture listener.
     */
    private val mPictureOnImageAvailableListener = ImageReader.OnImageAvailableListener {
        it.acquireNextImage().use { image ->
            val planes = image.planes
            if (planes.isNotEmpty()) {
                val buffer = planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                callback.onPictureTaken(data)
            }
        }
    }
    /**
     * record uncompressed yuv data listener...
     */
    private val mRecordOnImageAvailableListener = ImageReader.OnImageAvailableListener {
        val imageReader = it.acquireNextImage() ?: return@OnImageAvailableListener
        imageReader.use { image ->
            val start = System.currentTimeMillis()
            val planes = image.planes
            if (planes.isNotEmpty() && mRecordingFrameInProgress.get()) {
                val y = planes[0].buffer
                val u = planes[1].buffer
                val v = planes[2].buffer
                val ySize = y.remaining()
                if (mPreviewBytesPool == ByteArrayPool.EMPTY) {
                    mPreviewBytesPool = ByteArrayPool(5, ySize * 3 / 2)
                }
                //copy uncompressed yuv data ...
                val bytes = mPreviewBytesPool.getBytes()
                val stride = planes[1].pixelStride
                if (stride == 1) {
                    ByteBuffer.wrap(bytes).put(y).put(u).put(v)
                } else {
                    y.get(bytes, 0, ySize)
                    val vStart = ySize * 5 / 4
                    var index = 0
                    val end = u.remaining()
                    for (i in 0 until end step stride) {
                        bytes[ySize + index] = u.get(i)
                        bytes[vStart + index] = v.get(i)
                        ++index
                    }
                }
                callback.onFrameRecording(bytes, bytes.size, mPreviewBytesPool, image.width, image.height, image.format,
                        orientationOfImage(), facing, SystemClock.elapsedRealtimeNanos())
                logMED("camera2 cost time is ${System.currentTimeMillis() - start}")
            }
        }
    }

    /**
     * established camera2 operation session's callback.
     */
    private val mSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
            if (mCaptureSession == session) {
                mCaptureSession = null
            }
        }

        override fun onConfigured(session: CameraCaptureSession) {
            if (mCamera == null) return
            mCaptureSession = session
            setAutoFocus(mAutoFocus)
            setFlash(mFlash)
            try {
                session.setRepeatingRequest(mPreviewRequestBuilder?.build(), mCaptureCallback, mBackgroundHandler)
            } catch (ex: Exception) {
                logMEE("Capture Session Repeating Request Failed!", ex)
            }
        }
    }
    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        private var mState: Int = 0

        internal fun setState(state: Int) {
            mState = state
        }

        override fun onCaptureProgressed(session: CameraCaptureSession?, request: CaptureRequest?, partialResult: CaptureResult) {
            super.onCaptureProgressed(session, request, partialResult)
            process(partialResult)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession?, request: CaptureRequest?, result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)
            process(result)
        }

        private fun process(result: CaptureResult) {
            when (mState) {
                STATE_LOCKING -> {
                    val af = result.get(CaptureResult.CONTROL_AF_STATE) ?: return
                    if (af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            setState(STATE_CAPTURING)
                            onReady()
                        } else {
                            setState(STATE_LOCKED)
                            onPrecaptureRequired()
                        }
                    }
                }
                STATE_PRECAPTURE -> {
                    val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            ae == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED ||
                            ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        setState(STATE_WAITING)
                    }
                }
                STATE_WAITING -> {
                    val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        setState(STATE_CAPTURING)
                        onReady()
                    }
                }
            }
        }

        internal fun onPrecaptureRequired() {
            mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            setState(STATE_PRECAPTURE)
            try {
                mCaptureSession?.capture(mPreviewRequestBuilder?.build(), this, mBackgroundHandler)
                mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)
            } catch (e: Exception) {
                logMEE("Failed to run precapture sequence.", e)
            }
        }

        internal fun onReady() {
            logMED("ready 2 capture picture,")
            captureStillPicture()
        }
    }
    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null

    init {
        preview.callback = object : CameraPreview.PreviewCallback {
            override fun onSurfaceChanged() {
                startCaptureSession()
            }
        }
    }

    override fun open(): Boolean {
        if (!chooseCameraIdByFacing()) {
            return false
        }
        if (!collectCameraInfo()) {
            return false
        }
        prepareImageReader()
        startOpeningCamera()
        startBackgroundThread()
        return true
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        val thread = HandlerThread("Camera2")
        thread.start()
        mBackgroundThread = thread
        mBackgroundHandler = Handler(thread.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun chooseCameraIdByFacing(): Boolean {
        try {
            val internalFacing = INTERNAL_FACINGS.get(facing)
            val ids = mCameraManager.cameraIdList
            if (ids.isEmpty()) {
                throw RuntimeException("no camera available!")
            }
            for (id in ids) {
                val characteristics = mCameraManager.getCameraCharacteristics(id)
                val level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    continue
                }
                val internal = characteristics.get(CameraCharacteristics.LENS_FACING)
                        ?: throw NullPointerException("Unexpected Camera State!")
                if (internal == internalFacing) {
                    mCameraId = id
                    mCameraCharacteristics = characteristics
                    return true
                }
            }
            mCameraId = ids[0]
            val characteristics = mCameraManager.getCameraCharacteristics(mCameraId)
            val level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            if (level == null || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                return false
            }
            val internal = mCameraCharacteristics?.get(CameraCharacteristics.LENS_FACING)
                    ?: throw NullPointerException("Unexpected Camera State!")
            var i = 0
            val count = INTERNAL_FACINGS.size()
            while (i < count) {
                if (INTERNAL_FACINGS.valueAt(i) == internal) {
                    facing = INTERNAL_FACINGS.keyAt(i)
                    return true
                }
                i++
            }
            facing = CameraFunctions.FACING_BACK
            return true
        } catch (e: Exception) {
            throw RuntimeException("Camera2 Not Available!", e)
        }
    }

    private fun collectCameraInfo(): Boolean {
        val map = mCameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return false
        mPreviewSizes.clear()
        for (size in map.getOutputSizes(preview.getOutputClass())) {
            mPreviewSizes.add(Size(size.width, size.height))
        }
        mRecordSizes.clear()
        val outputFormats = map.outputFormats
        for (format in arrayOf(ImageFormat.YUV_420_888, ImageFormat.YV12, ImageFormat.NV21)) {
            if (format in outputFormats) {
                mRecordFormat = format
                break
            }
        }
        for (format in arrayOf(ImageFormat.JPEG)) {
            if (format in outputFormats) {
                mImageFormat = format
            }
        }
        if (mImageFormat < 0 || mRecordFormat < 0) {
            logMED("camera2 collect camerainfo not support image or record format!")
            return false
        }
        for (size in map.getOutputSizes(mRecordFormat)) {
            mRecordSizes.add(Size(size.width, size.height))
        }
        mPictureSizes.clear()
        collectPictureSizes(mPictureSizes, map, mImageFormat)
        return true
    }

    protected open fun collectPictureSizes(sizes: MutableList<Size>, map: StreamConfigurationMap, format: Int) {
        for (size in map.getOutputSizes(format)) {
            sizes.add(Size(size.width, size.height))
        }
    }

    private fun prepareImageReader() {
        mPictureImageReader?.close()
        mRecordImageReader?.close()
        val sensorOrientation = mCameraCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val pictureSize = getCameraSizeStrategy(STRATEGY_PICTURE_SIZE).chooseSize(preview, displayOrientation, sensorOrientation, facing, mPictureSizes)
        cacheCameraSize(SIZE_PICTURE, pictureSize)
        mPictureImageReader = ImageReader.newInstance(pictureSize.width, pictureSize.height, mImageFormat, 2)
        mPictureImageReader?.setOnImageAvailableListener(mPictureOnImageAvailableListener, mBackgroundHandler)
        val recordSize = getCameraSizeStrategy(STRATEGY_RECORD_PREVIEW_SIZE).chooseSize(preview, displayOrientation, sensorOrientation, facing, mRecordSizes)
        cacheCameraSize(SIZE_RECORD, pictureSize)
        mRecordImageReader = ImageReader.newInstance(recordSize.width, recordSize.height, mRecordFormat, 5)
        mRecordImageReader?.setOnImageAvailableListener(mRecordOnImageAvailableListener, mBackgroundHandler)
    }

    @SuppressLint("MissingPermission")
    private fun startOpeningCamera() {
        try {
            mCameraManager.openCamera(mCameraId, mCameraDeviceCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            throw RuntimeException("Failed to open camera: $mCameraId", e)
        }
    }

    private fun startCaptureSession() {
        val pir = mPictureImageReader
        val rir = mRecordImageReader
        if (!isCameraOpened() || !preview.isReady() || pir == null || rir == null) return
        val sensorOrientation = mCameraCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val previewSize = getCameraSizeStrategy(STRATEGY_PREVIEW_SIZE).chooseSize(preview, displayOrientation, sensorOrientation, facing, mPreviewSizes)
        cacheCameraSize(SIZE_PREVIEW, previewSize)
        preview.setBufferSize(previewSize.width, previewSize.height)
        val surface = preview.getSurface()
        try {
            mPreviewRequestBuilder = mCamera?.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder?.addTarget(surface)
            mCamera?.createCaptureSession(listOf(surface, pir.surface, rir.surface), mSessionCallback, mBackgroundHandler)
        } catch (e: Exception) {
            throw RuntimeException("Failed to Start Camera Session !", e)
        }
    }

    override fun close() {
        mCaptureSession?.run {
            close()
            mCaptureSession = null
        }
        mCamera?.run {
            close()
            mCamera = null
        }
        mPictureImageReader?.run {
            close()
            mPictureImageReader = null
        }
        mRecordImageReader?.run {
            close()
            mRecordImageReader = null
        }
        stopBackgroundThread()
    }

    override fun setAutoFocus(autoFocus: Boolean) {
        mAutoFocus = autoFocus
        mPreviewRequestBuilder?.run {
            mAutoFocus = setAutoFocusInternal(this, autoFocus)
        }
    }

    private fun setAutoFocusInternal(requestBuilder: CaptureRequest.Builder, autoFocus: Boolean): Boolean {
        if (autoFocus) {
            val modes = mCameraCharacteristics?.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                    ?: intArrayOf()
            // Auto focus is not supported
            if (modes.isEmpty() || modes.size == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF) {
                requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            } else {
                requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                return true
            }
        } else {
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        }
        return false
    }

    override fun setFlash(flash: Int) {
        mFlash = flash
        mPreviewRequestBuilder?.run {
            setFlashInternal(this, flash)
        }
    }

    private fun setFlashInternal(requestBuilder: CaptureRequest.Builder, flash: Int) {
        when (flash) {
            CameraFunctions.FLASH_OFF -> {
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            CameraFunctions.FLASH_ON -> {
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            CameraFunctions.FLASH_TORCH -> {
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }
            CameraFunctions.FLASH_AUTO -> {
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
            CameraFunctions.FLASH_RED_EYE -> {
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE)
                requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
            }
        }
    }

    /**
     * Start 2 capture pictures build with the capture request builder...
     */
    private fun captureStillPicture() {
        try {
            val camera = mCamera ?: return
            val captureSession = mCaptureSession ?: return
            val ipr = mPictureImageReader ?: return
            val captureRequestBuilder = camera.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequestBuilder.addTarget(ipr.surface)
            setAutoFocusInternal(captureRequestBuilder, getAutoFocus())
            setFlashInternal(captureRequestBuilder, getFlash())
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientationOfImage())
            // Stop preview and capture a still picture.
            captureSession.stopRepeating()
            captureSession.abortCaptures()
            captureSession.capture(captureRequestBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession?, request: CaptureRequest?, result: TotalCaptureResult?) {
                    unlockFocus()
                    mPictureCaptureInProgress.set(false)
                    logMED("capture picture success!")
                }
            }, mBackgroundHandler)
        } catch (e: Exception) {
            mPictureCaptureInProgress.set(false)
            logMEE("Cannot Capture Picture!", e)
        }
    }

    private fun orientationOfImage(): Int {
        val sensorOrientation = mCameraCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION)
                ?: 0
        return (sensorOrientation + displayOrientation * (if (facing == CameraFunctions.FACING_FRONT) 1 else -1) + 360) % 360
    }

    private fun lockFocus() {
        mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
        try {
            mCaptureCallback.setState(STATE_LOCKING)
            mCaptureSession?.capture(mPreviewRequestBuilder?.build(), mCaptureCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            logMEE("Failed to lock focus.", e)
        }
    }

    private fun unlockFocus() {
        val previewRequestBuilder = mPreviewRequestBuilder ?: return
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
        try {
            mCaptureSession?.capture(previewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler)
            mCaptureCallback.setState(STATE_PREVIEW)
            setAutoFocus(mAutoFocus)
            setFlash(mFlash)
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            mCaptureSession?.setRepeatingRequest(previewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler)
        } catch (e: Exception) {
            logMEE("Failed to restart camera preview.", e)
        }
    }

    override fun takePicture() {
        if (mPictureCaptureInProgress.getAndSet(true)) return
        if (getAutoFocus()) {
            lockFocus()
        }
        captureStillPicture()
    }

    override fun startRecord() {
        if (mRecordingFrameInProgress.getAndSet(true)) return
        callback.onStartRecordingFrame(timeStampInNs())
        try {
            val camera = mCamera ?: return
            val captureSession = mCaptureSession ?: return
            val ipr = mRecordImageReader ?: return
            mCaptureCallback.setState(STATE_PREVIEW)
            val recordRequestBuilder = camera.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_RECORD)
            recordRequestBuilder.addTarget(preview.getSurface())
            recordRequestBuilder.addTarget(ipr.surface)
            setAutoFocusInternal(recordRequestBuilder, getAutoFocus())
            setFlashInternal(recordRequestBuilder, getFlash())
            captureSession.stopRepeating()
            captureSession.abortCaptures()
            captureSession.setRepeatingRequest(recordRequestBuilder.build(), mCaptureCallback, mBackgroundHandler)
        } catch (e: Exception) {
            logMEE("Cannot Capture Picture!", e)
            mRecordingFrameInProgress.set(false)
        }
    }

    override fun stopRecord() {
        unlockFocus()
        mRecordingFrameInProgress.set(false)
        callback.onStopRecordingFrame(timeStampInNs())
    }

    override fun isCameraOpened(): Boolean {
        return mCamera != null
    }

    override fun updateDisplayOrientation(displayOrientation: Int) {
        preview.setDisplayOrientation(displayOrientation)
    }

    override fun getZoom(): Int {
        //todo
        return ZOOM_MIN
    }

    override fun setZoom(zoom: Int) {
        //todo
    }

    override fun focus(rect: Rect?, cb: ((Boolean) -> Unit)?) {
        //todo
    }
}