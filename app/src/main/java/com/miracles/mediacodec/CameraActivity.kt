package com.miracles.mediacodec

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Parcel
import android.os.Parcelable
import com.miracles.camera.*
import com.miracles.codec.camera.AudioDevice
import com.miracles.codec.camera.CapturePictureHandler
import com.miracles.codec.camera.Mp4Muxer
import com.miracles.codec.camera.Mp4MuxerHandler
import kotlinx.android.synthetic.main.activity_camera.*
import java.io.File

/**
 * Created by lxw
 */
class CameraActivity : BaseActivity() {
    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CameraActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        initCameraView()
    }

    private fun getSavedDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "camera&muxer")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun initCameraView() {

        //bind cameraControls
        cameraControls.bindCameraView(cameraView)
        val picturePath = File(getSavedDir(), "me.jpeg").absolutePath
        //record preview size
        cameraView.setCameraSizeStrategy(CameraFunctions.STRATEGY_RECORD_PREVIEW_SIZE, getRecordStrategy())
        //mp4Callback
        cameraView.addCallback(object : MMP4MuxerHandler(this@CameraActivity, getSavedDir()) {
            override fun onStopRecordingFrame(cameraView: CameraView, timeStampInNs: Long) {
                super.onStopRecordingFrame(cameraView, timeStampInNs)
                PreviewActivity.start(this@CameraActivity, mMp4Path, false)
            }
        })
        //picture callback
        cameraView.addCallback(object : CapturePictureHandler(picturePath) {
            override fun onPictureTaken(cameraView: CameraView, data: ByteArray) {
                super.onPictureTaken(cameraView, data)
                logMED("onPictureTaken")
                PreviewActivity.start(this@CameraActivity, path, true)
            }
        })
    }

    private fun getRecordStrategy(): ChooseSizeStrategy {
        val display = resources.displayMetrics
        logMED("display width=${display.widthPixels} ,height=${display.heightPixels}")
        val aspectRatio = display.heightPixels.toFloat() / display.widthPixels
        return MChooseStrategy(16/9f)
    }

    private class MChooseStrategy(val aspectRatio: Float) : ChooseSizeStrategy {
        private val mFacingBackChooseStrategy: ChooseSizeStrategy
        private val mFacingFrontChooseStrategy: ChooseSizeStrategy

        init {
            mFacingFrontChooseStrategy = ChooseSizeStrategy.AspectRatioStrategy(aspectRatio, (480 * aspectRatio).toInt(), 480)
            mFacingBackChooseStrategy = ChooseSizeStrategy.AspectRatioStrategy(aspectRatio, (1080 * aspectRatio).toInt(), 1080)
        }

        constructor(parcel: Parcel) : this(parcel.readFloat()) {

        }

        override fun writeToParcel(dest: Parcel?, flags: Int) {
            dest?.writeFloat(aspectRatio)
        }

        override fun describeContents() = 0

        override fun chooseSize(preview: CameraPreview, displayOrientation: Int, cameraSensorOrientation: Int, facing: Int, sizes: List<Size>): Size {
            return if (facing == CameraFunctions.FACING_FRONT) {
                mFacingFrontChooseStrategy.chooseSize(preview, displayOrientation, cameraSensorOrientation, facing, sizes)
            } else {
                mFacingBackChooseStrategy.chooseSize(preview, displayOrientation, cameraSensorOrientation, facing, sizes)
            }
        }


        companion object CREATOR : Parcelable.Creator<MChooseStrategy> {
            override fun createFromParcel(parcel: Parcel): MChooseStrategy {
                return MChooseStrategy(parcel)
            }

            override fun newArray(size: Int): Array<MChooseStrategy?> {
                return arrayOfNulls(size)
            }
        }
    }

    /**
     * Mp4 record handler .path
     */
    private open class MMP4MuxerHandler(val ctx: Context, val dir: File) : Mp4MuxerHandler() {
        override fun createMp4Muxer(frameWidth: Int, frameHeight: Int): Mp4Muxer {
            val path = File(dir, "me.mp4").absolutePath
            val mp4Param = Mp4Muxer.Params().apply {
                this.path = path
                this.width = frameHeight / 2
                this.height = frameWidth / 2
            }
            val audioParam = AudioDevice.Params()
            val audioDevice = AudioDevice.create(audioParam)
            return Mp4Muxer(ctx, mp4Param, audioDevice)
        }

        override fun onStartRecordingFrame(cameraView: CameraView, timeStampInNs: Long) {
            super.onStartRecordingFrame(cameraView, timeStampInNs)
            logMED("---onStartRecordingFrame----")
        }

        override fun onStopRecordingFrame(cameraView: CameraView, timeStampInNs: Long) {
            super.onStopRecordingFrame(cameraView, timeStampInNs)
            logMED("---onStopRecordingFrame----")
        }

    }

    override fun onResume() {
        super.onResume()
        cameraView.start()
    }

    override fun onPause() {
        super.onPause()
        cameraView.stop()
    }

}