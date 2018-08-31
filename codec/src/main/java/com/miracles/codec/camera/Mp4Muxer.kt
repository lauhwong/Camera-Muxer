package com.miracles.codec.camera

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import com.miracles.camera.logMED
import com.miracles.camera.logMEE
import java.io.File

/**
 * Created by lxw
 */
class Mp4Muxer(internal val ctx: Context, internal val params: Params, internal val mAudioDevice: AudioDevice) {
    companion object {
        private const val VIDEO_MIME_TYPE = "video/avc"
        private const val AUDIO_MIME_TYPE = "audio/mp4a-latm"
    }

    /**
     * Params to construct Mp4MuxerHandler
     */
    class Params {
        //video code info
        var width = 544
            set(value) {
                var c = value % 16
                if (c != 0) {
                    c = 16 - c
                }
                field = value + c
            }
        var height = 960
            set(value) {
                var c = value % 16
                if (c != 0) {
                    c = 16 - c
                }
                field = value + c
            }
        var videoBitrate = 1300000
        var fps = 30
        // force 2 set mp4's fps=$fps
        var fpsEnsure = false
        //balance video'timeStamp per gap
        var balanceTimestamp = true
        var balanceTimestampGapInSeconds = 3
        //audio code info
        var audioSampleRate = 44100
        var audioBitRate = 64000
        var audioChannelCount = 1
        //path of mp4
        var path: String? = null
    }

    internal val mMuxer: MeMuxer
    internal val mMp4Path: String
    internal val mMp4Width: Int
    internal val mMp4Height: Int
    internal var mSupportI420 = false
    internal var mSupportNV12 = false

    init {
        mMp4Width = params.width
        mMp4Height = params.height
        //video.
        val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, mMp4Width, mMp4Height)
        val videoCodec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
        val capabilitiesForType = videoCodec.codecInfo.getCapabilitiesForType(VIDEO_MIME_TYPE)
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, params.videoBitrate)
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, params.fps)
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
        val i420s = arrayOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar)
        val nv12s = arrayOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar)
        val supportedColorFormats = arrayListOf<Int>()
        for (key in capabilitiesForType.colorFormats) {
            if (i420s.contains(key)) {
                supportedColorFormats.add(key)
                mSupportI420 = true
            } else if (nv12s.contains(key)) {
                supportedColorFormats.add(key)
                mSupportNV12 = true
            }
        }
        var handled = false
        for (key in supportedColorFormats) {
            try {
                videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, key)
                videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                mSupportI420 = i420s.contains(key)
                mSupportNV12 = nv12s.contains(key)
                handled = true
                break
            } catch (ex: Throwable) {
                logMEE("KEY_COLOR_FORMAT=$key", ex)
            }
        }
        if (!handled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } else {
                throw IllegalArgumentException("Can't Find VideoCodec Encoder...")
            }
        }
        logMED("mMp4Width=$mMp4Width ,mMp4Height=$mMp4Height mSupportI420=$mSupportI420 mSupportNV12=$mSupportNV12")
        val videoCodecLife = MediaCodecLife(videoCodec, false)
        //audio
        val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, params.audioSampleRate, params.audioChannelCount)
        val audioCodec = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE)
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, params.audioBitRate)
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)//AACObjectLC
        audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val audioCodecLife = MediaCodecLife(audioCodec, true)
        //construct memuxer.
        val nameOfMp4 = "M-${params.width}*${params.height}_${System.currentTimeMillis()}.mp4"
        var f = params.path
        if (f == null) {
            f = File(ctx.cacheDir, nameOfMp4).absolutePath
        } else if (f.endsWith(File.separator)) {
            f += nameOfMp4
        }
        mMp4Path = f!!
        val file = File(mMp4Path)
        if (file.parentFile != null) {
            file.parentFile.mkdirs()
        }
        val muxer = MediaMuxer(mMp4Path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mMuxer = MeMuxer(params.balanceTimestamp, params.balanceTimestampGapInSeconds, muxer, videoCodecLife, audioCodecLife)
    }
}