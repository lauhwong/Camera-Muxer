package com.miracles.codec.camera

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
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
    internal var mSupportDeprecated420 = false

    init {
        mMp4Width = params.width
        mMp4Height = params.height
        //video.
        val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, mMp4Width, mMp4Height)
        val videoCodec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
        val capabilitiesForType = videoCodec.codecInfo.getCapabilitiesForType(VIDEO_MIME_TYPE)
        for (key in capabilitiesForType.colorFormats) {
            if (key == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                mSupportDeprecated420 = true
                break
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        } else {
            if (mSupportDeprecated420) {
                videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)
            } else {
                videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            }
        }
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, params.videoBitrate)
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, params.fps)
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
        videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val videoCodecLife = MediaCodecLife(videoCodec, false)
        //audio
        val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, params.audioSampleRate, params.audioChannelCount)
        val audioCodec = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE)
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, params.audioBitRate)
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)//AACObjectLC
        audioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val audioCodecLife = MediaCodecLife(audioCodec, true)
        //construct memuxer.
        var f = params.path
        if (f == null) {
            f = File(ctx.cacheDir, "M_${params.width}_${params.height}.mp4").absolutePath
        }
        mMp4Path = f!!
        val muxer = MediaMuxer(mMp4Path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mMuxer = MeMuxer(muxer, videoCodecLife, audioCodecLife)
    }
}