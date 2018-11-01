package com.miracles.codec.camera

import android.media.MediaCodec
import android.media.MediaMuxer
import com.miracles.camera.logMED
import com.miracles.camera.logMEE
import java.nio.ByteBuffer
import java.util.TreeSet
import java.util.concurrent.TimeUnit

/**
 * Created by lxw
 */
class MeMuxer(val balanceTimestamp: Boolean, val balanceTimestampGapInSeconds: Int, val mediaMuxer: MediaMuxer,
              val videoCodecLife: MediaCodecLife?, val audioCodecLife: MediaCodecLife?) : LifeCycle, MeCodec {
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
    companion object {
        private const val UNINITIALIZED_TRACK_INDEX = Int.MIN_VALUE + 1
        private const val UNREACHABLE_TRACK_INDEX = Int.MIN_VALUE
    }

    private var mAudioTrackIndex = UNINITIALIZED_TRACK_INDEX
    private var mVideoTrackIndex = UNINITIALIZED_TRACK_INDEX
    @Volatile
    private var mMuxerStarted = false
    @Volatile
    private var mAudioStopped = false
    @Volatile
    private var mVideoStopped = false
    @Volatile
    private var mMuxerStopped = false
    private val mAudioBuffers = ArrayList<MediaBuf>()
    private val mVideoBuffers = ArrayList<MediaBuf>()
    private var mLastBalanceSecond = 0

    init {
        videoCodecLife?.addCallback(CodecCallbackImpl(this, false))
        if (videoCodecLife == null) {
            mVideoTrackIndex = UNREACHABLE_TRACK_INDEX
            mVideoStopped = true
        }
        audioCodecLife?.addCallback(CodecCallbackImpl(this, true))
        if (audioCodecLife == null) {
            mAudioTrackIndex = UNREACHABLE_TRACK_INDEX
            mAudioStopped = true
        }
    }

    @Synchronized
    private fun onOutputStatus(codec: MediaCodec, audio: Boolean, status: Int) {
        if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (audio) {
                mAudioTrackIndex = mediaMuxer.addTrack(codec.outputFormat)
            } else {
                mVideoTrackIndex = mediaMuxer.addTrack(codec.outputFormat)
            }
            startMuxer(false)
        }
    }

    private fun startMuxer(force: Boolean) {
        val hasTrack = if (force) {
            mAudioTrackIndex != UNINITIALIZED_TRACK_INDEX || mVideoTrackIndex != UNINITIALIZED_TRACK_INDEX
        } else {
            mAudioTrackIndex != UNINITIALIZED_TRACK_INDEX && mVideoTrackIndex != UNINITIALIZED_TRACK_INDEX
        }
        if (hasTrack && !mMuxerStarted) {
            mMuxerStarted = true
            mediaMuxer.start()
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
        if (mAudioStopped && mVideoStopped) {
            drainCachedBuffers()
        }
    }

    private fun drainCachedBuffers() {
        if (!mMuxerStarted || mMuxerStopped) return
        balanceTimestamp(mVideoBuffers)
        drainMediaBuf(true, true)
        mediaMuxer.stop()
        mediaMuxer.release()
        logMED("DrainCachedBuffers Of MeMuxer ! ")
    }

    override fun start() {
        videoCodecLife?.start()
        audioCodecLife?.start()
    }


    override fun stop() {
        videoCodecLife?.stop()
        audioCodecLife?.stop()
        if (mAudioBuffers.isNotEmpty() || mVideoBuffers.isNotEmpty()) {
            logMED("Stop MeMuxer, CacheBuffers Not Empty !")
            startMuxer(true)
            drainCachedBuffers()
        }
        mAudioBuffers.clear()
        mVideoBuffers.clear()
        mMuxerStopped = false
        mLastBalanceSecond = 0
        logMED("MeMuxer Stopped !!! ")
    }


    @Throws(IllegalArgumentException::class)
    override fun audioInput(bytes: ByteArray, len: Int, timeStampInNs: Long): Int {
        val life = audioCodecLife
                ?: throw IllegalArgumentException("AudioInput AudioCodecLife is Null !")
        synchronized(life) {
            return life.input(bytes, len, TimeUnit.NANOSECONDS.toMicros(timeStampInNs))
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun videoInput(bytes: ByteArray, len: Int, timeStampInNs: Long): Int {
        val life = videoCodecLife
                ?: throw IllegalArgumentException("VideoInput VideoCodecLife is Null !")
        synchronized(life) {
            return life.input(bytes, len, TimeUnit.NANOSECONDS.toMicros(timeStampInNs))
        }
    }

}