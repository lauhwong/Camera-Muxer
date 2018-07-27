package com.miracles.codec.camera;

import com.miracles.camera.CameraView
import java.io.File

/**
 * Created by lxw
 */
open class CapturePictureHandler(val path: String) : CameraView.Callback {
    override fun onPictureTaken(cameraView: CameraView, data: ByteArray) {
        val file = File(path)
        file.writeBytes(data)
    }
}
