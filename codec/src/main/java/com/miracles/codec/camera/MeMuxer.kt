package com.miracles.codec.camera

import android.media.MediaCodec
import android.media.MediaMuxer
import com.miracles.camera.logMED
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Created by lxw
 */
class MeMuxer(val balanceTimestamp: Boolean, val balanceTimestampGapInSeconds: Int, val mediaMuxer: MediaMuxer, val videoCodecLife: MediaCodecLife, val audioCodecLife: MediaCodecLife) : LifeCycle, MeCodec {
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

    private data class MediaBuf(val byteBuffer: ByteBuffer, val bufferInfo: MediaCodec.BufferInfo)

    //flag to identify?
    private var mAudioTrackIndex = -1
    private var mVideoTrackIndex = -1
    private var mMuxerStarted = false
    private var mAudioStopped = false
    private var mVideoStopped = false
    private var mMuxerStopped = false
    private val mAudioBuffers = ArrayList<MediaBuf>()
    private val mVideoBuffers = ArrayList<MediaBuf>()
    private var mLastBalanceSecond = 0

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
        if (audio) {
            mAudioBuffers.add(MediaBuf(ByteBuffer.wrap(data), buf))
            drainMediaBuf(true, false)
        } else {
            if (balanceTimestamp) {
                if (mVideoBuffers.isNotEmpty()) {
                    fun timeInSeconds(buf: MediaBuf) = (buf.bufferInfo.presentationTimeUs / 1e6).toInt()
                    val balancedSecond = mLastBalanceSecond
                    val tsGap = (timeStamp / 1e6).toInt() - balancedSecond
                    if (tsGap >= balanceTimestampGapInSeconds) {
                        var balanceFrameIndex = mVideoBuffers.size - 1
                        for (index in mVideoBuffers.size - 1 downTo 0) {
                            val bufTimeInSecond = timeInSeconds(mVideoBuffers[index])
                            if (bufTimeInSecond < balancedSecond) {
                                break
                            }
                            balanceFrameIndex = index
                        }
                        val cacheBuffers = mVideoBuffers.subList(balanceFrameIndex, mVideoBuffers.size)
                        val newBalanceFrameTimestamp = balanceTimestamp(cacheBuffers)
                        drainMediaBuf(false, true)
                        mLastBalanceSecond += balanceTimestampGapInSeconds
                        if (newBalanceFrameTimestamp > 0) {
                            buf.presentationTimeUs = newBalanceFrameTimestamp
                        }
                    }
                }
                mVideoBuffers.add(MediaBuf(ByteBuffer.wrap(data), buf))
            } else {
                mVideoBuffers.add(MediaBuf(ByteBuffer.wrap(data), buf))
                drainMediaBuf(false, true)
            }
        }
    }

    private fun balanceTimestamp(buffers: List<MediaBuf>): Long {
        val frames = buffers.size
        if (frames > 2) {
            val startTimeStamp = buffers[0].bufferInfo.presentationTimeUs
            val endTimeStamp = buffers[frames - 1].bufferInfo.presentationTimeUs
            val frameDeltaGap = (endTimeStamp - startTimeStamp) / (frames - 1)
            for (index in 0 until frames) {
                buffers[index].bufferInfo.presentationTimeUs = startTimeStamp + index * frameDeltaGap
            }
            return endTimeStamp + frameDeltaGap
        }
        return 0
    }

    private fun drainMediaBuf(audio: Boolean, video: Boolean) {
        if (!mMuxerStarted) return
        if (audio) {
            val it = mAudioBuffers.iterator()
            while (it.hasNext()) {
                val mediaBuf = it.next()
                mediaMuxer.writeSampleData(mAudioTrackIndex, mediaBuf.byteBuffer, mediaBuf.bufferInfo)
                it.remove()
            }
        }
        if (video) {
            val it = mVideoBuffers.iterator()
            while (it.hasNext()) {
                val mediaBuf = it.next()
                mediaMuxer.writeSampleData(mVideoTrackIndex, mediaBuf.byteBuffer, mediaBuf.bufferInfo)
                it.remove()
            }
        }
    }

    @Synchronized
    private fun onEndOfOutputStream(audio: Boolean) {
        if (audio) mAudioStopped = true else mVideoStopped = true
        if (!mMuxerStarted) return
        if (!mMuxerStopped && mAudioStopped && mVideoStopped) {
            logMED("EndOfOutputStream Release MeMuxer !")
            balanceTimestamp(mVideoBuffers)
            drainMediaBuf(true, true)
            mLastBalanceSecond = 0
            mediaMuxer.stop()
            mediaMuxer.release()
            mAudioBuffers.clear()
            mVideoBuffers.clear()
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