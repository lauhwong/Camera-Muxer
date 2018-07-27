package com.miracles.camera

import android.content.Context
import android.hardware.camera2.params.StreamConfigurationMap
import android.support.annotation.RequiresApi

/**
 * Created by lxw
 */
@RequiresApi(23)
class Camera2Api23(preview: CameraPreview, ctx: Context, callback: CameraFunctions.Callback) : Camera2(preview, ctx, callback) {

    override fun collectPictureSizes(sizes: MutableList<Size>, map: StreamConfigurationMap, format: Int) {
        val outputSizes = map.getHighResolutionOutputSizes(format)
        if (outputSizes != null) {
            for (size in map.getHighResolutionOutputSizes(format)) {
                sizes.add(Size(size.width, size.height))
            }
        }
        if (sizes.isEmpty()) {
            super.collectPictureSizes(sizes, map, format)
        }
    }
}