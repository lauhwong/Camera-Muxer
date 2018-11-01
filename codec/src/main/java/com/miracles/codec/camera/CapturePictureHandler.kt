package com.miracles.codec.camera;

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import com.miracles.camera.CameraFunctions
import com.miracles.camera.CameraView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Created by lxw
 */
open class CapturePictureHandler(private val savedDirOrPath: String) : CameraView.Callback {
    companion object {
        const val EXT = "jpeg"
    }

    final override fun onPictureCaptured(cameraView: CameraView, data: ByteArray, len: Int, width: Int, height: Int, format: Int,
                                         orientation: Int, facing: Int, timeStampInNs: Long) {
        super.onPictureCaptured(cameraView, data, len, width, height, format, orientation, facing, timeStampInNs)
        var path = savedDirOrPath
        if (savedDirOrPath.endsWith(File.separator)) {
            path += "M_${System.currentTimeMillis()}.$EXT"
        }
        val file = File(path)
        if (file.parentFile != null) {
            file.parentFile.mkdirs()
        }
        var codeFormat = LibYuvUtils.FOURCC_NV21
        if (format == ImageFormat.YUV_420_888) {
            codeFormat = LibYuvUtils.FOURCC_I420
        } else if (format == ImageFormat.YV12) {
            codeFormat = LibYuvUtils.FOURCC_YV12
        }
        val picWidth = if (orientation % 90 == 0) height else width
        val picHeight = if (orientation % 90 == 0) width else height
        val i420Bytes = ByteArray(picWidth * picHeight * 3 / 2)
        val rotation = LibYuvUtils.ROTATION_90 * orientation / 90
        val success = LibYuvUtils.scaleRotationAndMirrorToI420(data, len, i420Bytes, width, height, width, height,
                LibYuvUtils.SCALE_FILTER_NONE, rotation, facing == CameraFunctions.FACING_FRONT, codeFormat)
        if (success > 0) {
            val rawData = if (data.size == i420Bytes.size) data else ByteArray(i420Bytes.size)
            if (LibYuvUtils.i420ToNV21(i420Bytes, i420Bytes.size, rawData, picWidth, picHeight) > 0) {
                if (YuvImage(rawData, ImageFormat.NV21, picWidth, picHeight, null)
                                .compressToJpeg(Rect(0, 0, picWidth, picHeight), 100,
                                        FileOutputStream(file))) {
                    onPictureCapturedResult(cameraView, path, null)
                    return
                }
            }
        }
        onPictureCapturedResult(cameraView, path, IOException("Camera Data Process Error !"))
    }

    open fun onPictureCapturedResult(cameraView: CameraView, path: String, ex: Throwable?) {}

}
