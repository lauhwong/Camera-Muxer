package com.miracles.codec.camera;

import com.miracles.camera.CameraView
import java.io.File

/**
 * Created by lxw
 */
open class CapturePictureHandler(val initializedPath: String) : CameraView.Callback {
    companion object {
        const val EXT = "jpeg"
    }

    var capturedPath: String? = null
        private set

    override fun onPictureTaken(cameraView: CameraView, data: ByteArray) {
        val nameOfPic = "M_${System.currentTimeMillis()}.$EXT"
        var path = initializedPath
        if (initializedPath.endsWith(File.separator)) {
            path += nameOfPic
        }
        this.capturedPath = path
        val file = File(path)
        if (file.parentFile != null) {
            file.parentFile.mkdirs()
        }
        file.writeBytes(data)
    }
}
