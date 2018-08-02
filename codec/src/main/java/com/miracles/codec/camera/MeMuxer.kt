package com.miracles.codec.camera

import android.media.MediaCodec
import android.media.MediaMuxer
import com.miracles.camera.logMED
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Created by lxw
 */
class MeMuxer(val mediaMuxer: MediaMuxer, val videoCodecLife: MediaCodecLife, val audioCodecLife: MediaCodecLife) : LifeCycle, MeCodec {
    private class CodecCallbackImpl(private val meMuxer: MeMuxer, private val audio: Boolean) : MediaCodecLife.CodecCallback {
        override fun onOutputStatus(codec: MediaCodec, status: Int) {
            super.onOutputStatus(codec, status)
            meMuxer.onOutputStatus(codec, audio, status)
        }

        override fun onOutputData(codec: MediaCodec, data: ByteArray, len: Int, timeStamp: Long, flags: Int) {
            super.onOutputData(codec, data, len, timeStamp, flags)
            meMuxer.onOutputData(audio, data, len, timeStamp, flags)
        }

        override fun onEndOfOutputStream() {
            super.onEndOfOutputStream()
            meMuxer.onEndOfOutputStream(audio)
        }
    }

    private data class MediaBuf(val audio: Boolean, val byteBuffer: ByteBuffer, val bufferInfo: MediaCodec.BufferInfo)

    //flag?
    private var mAudioTrackIndex = -1
    private var mVideoTrackIndex = -1
    private var mMuxerStarted = false
    private var mAudioStopped = false
    private var mVideoStopped = false
    private var mMuxerStopped = false
    private val mBuffers = arrayListOf<MediaBuf>()

    init {
        videoCodecLife.addCallback(CodecCallbackImpl(this, false))
        audioCodecLife.addCallback(CodecCallbackImpl(this, true))
    }

    @Synchronized
    private fun onOutputStatus(codec: MediaCodec, audio: Boolean, status: Int) {
        if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (audio) {
                mAudioTrackIndex = mediaMuxer.addTrack(codec.outputFormat)
            } else {
                mVideoTrackIndex = mediaMuxer.addTrack(codec.outputFormat)
            }
            if (mAudioTrackIndex != -1 && mVideoTrackIndex != -1 && !mMuxerStarted) {
                mMuxerStarted = true
                mediaMuxer.start()
            }
        }
    }

    @Synchronized
    private fun onOutputData(audio: Boolean, data: ByteArray, len: Int, timeStamp: Long, flags: Int) {
        val buf = MediaCodec.BufferInfo()
        buf.set(0, len, timeStamp, flags)
        mBuffers.add(MediaBuf(audio, ByteBuffer.wrap(data), buf))
        drainMediaBuf()
    }

    private fun drainMediaBuf() {
        if (!mMuxerStarted) return
        val it = mBuffers.iterator()
        while (it.hasNext()) {
            val mediaBuf = it.next()
            val trackId = if (mediaBuf.audio) mAudioTrackIndex else mVideoTrackIndex
            mediaMuxer.writeSampleData(trackId, mediaBuf.byteBuffer, mediaBuf.bufferInfo)
            it.remove()
        }
    }

    @Synchronized
    private fun onEndOfOutputStream(audio: Boolean) {
        if (audio) mAudioStopped = true else mVideoStopped = true
        if (!mMuxerStarted) return
        if (!mMuxerStopped && mAudioStopped && mVideoStopped) {
            logMED("Release MeMuxer !")
            drainMediaBuf()
            mediaMuxer.stop()
            mediaMuxer.release()
            mBuffers.clear()
        }
    }


    override fun start() {
        videoCodecLife.start()
        audioCodecLife.start()
    }


    override fun stop() {
        videoCodecLife.stop()
        audioCodecLife.stop()
    }


    override fun audioInput(bytes: ByteArray, len: Int, timeStampInNs: Long): Int {
        synchronized(audioCodecLife) {
            return audioCodecLife.input(bytes, len, TimeUnit.NANOSECONDS.toMicros(timeStampInNs))
        }
    }


    override fun videoInput(bytes: ByteArray, len: Int, timeStampInNs: Long): Int {
        synchronized(videoCodecLife) {
            return videoCodecLife.input(bytes, len, TimeUnit.NANOSECONDS.toMicros(timeStampInNs))
        }
    }

}