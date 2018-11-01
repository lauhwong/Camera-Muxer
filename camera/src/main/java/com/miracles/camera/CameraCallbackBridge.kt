package com.miracles.camera

/**
 * Created by lxw
 */
internal class CameraCallbackBridge(val cameraView: CameraView) : CallbackBridge<CameraView.Callback>(), CameraFunctions.Callback {
    private val mBackgroundThread = ThreadLoop("CameraCallbackBridge")

    internal fun startBackgroundThread() {
        mBackgroundThread.start()
    }

    internal fun stopBackgroundThread() {
        mBackgroundThread.quit()
    }

    override fun onCameraOpened() {
        callback { onCameraOpened(cameraView) }
    }

    override fun onCameraClosed() {
        callback { onCameraClosed(cameraView) }
    }

    override fun onStartCapturePicture() {
        callback { onStartCapturePicture(cameraView) }
    }

    override fun onPictureCaptured(data: ByteArray, len: Int, width: Int, height: Int, format: Int, orientation: Int, facing: Int, timeStampInNs: Long) {
        callback { onPictureCaptured(cameraView, data, len, width, height, format, orientation, facing, timeStampInNs) }
    }

    override fun onStartRecordingFrame(timeStampInNs: Long) {
        callback { onStartRecordingFrame(cameraView, timeStampInNs) }
    }

    override fun onFrameRecording(data: ByteArray, len: Int, bytesPool: ByteArrayPool, width: Int, height: Int, format: Int,
                                  orientation: Int, facing: Int, timeStampInNs: Long) {
        val frameBytes = CameraView.FrameBytes(data, len, bytesPool, false)
        callback { onFrameRecording(cameraView, frameBytes, width, height, format, orientation, facing, timeStampInNs) }
        //recycle bytes to reuse...
        mBackgroundThread.enqueue(Runnable { if (!frameBytes.consumed) bytesPool.releaseBytes(data) })
    }

    override fun onStopRecordingFrame(timeStampInNs: Long) {
        callback { onStopRecordingFrame(cameraView, timeStampInNs) }
    }

    override fun callback(methods: CameraView.Callback.() -> Unit) {
        mBackgroundThread.enqueue(Runnable {
            super.callback(methods)
        })
    }

}