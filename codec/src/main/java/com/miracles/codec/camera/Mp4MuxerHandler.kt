package com.miracles.codec.camera

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import com.miracles.camera.ByteArrayPool
import com.miracles.camera.CameraFunctions
import com.miracles.camera.CameraView
import com.miracles.camera.logMED
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by lxw
 */
abstract class Mp4MuxerHandler : AudioDevice.Callback, CameraView.Callback {
    private var mCompressBytesPool: ByteArrayPool = ByteArrayPool.EMPTY
    private lateinit var mMuxer: MeMuxer
    private lateinit var mMp4Muxer: Mp4Muxer
    protected lateinit var mMp4Path: String
    private var mStartTimeStamp = 0L
    private var mRecordingTimeStamp = 0L
    protected var mMp4Width = 0
    protected var mMp4Height = 0
    private var mRecordFrame = 0
    @SuppressLint("UseSparseArrays")
    private val mAudioBytesCache = HashMap<Int, MutableList<AudioCache>>()
    private val mAudioInputLock = ReentrantLock(true)
    private var mReleased = AtomicBoolean(true)
    private var mDiscardFrame = false
    private val mDiscardFrameThreshold: Int
    private var mLastCheckFrameDiscardTime = 0L

    companion object {
        private const val DISCARD_FRAME_THRESHOLD = 10
    }

    constructor() : this(DISCARD_FRAME_THRESHOLD)
    /**
     * discard frame if [compressed time> (1e9/fps)] per  discardFrameThreshold
     */
    constructor(discardFrameThreshold: Int) {
        mDiscardFrameThreshold = discardFrameThreshold
    }

    private data class AudioCache(val bytes: ByteArray, val len: Int, val timeStampInNs: Long) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AudioCache) return false

            if (!Arrays.equals(bytes, other.bytes)) return false
            if (len != other.len) return false
            if (timeStampInNs != other.timeStampInNs) return false

            return true
        }

        override fun hashCode(): Int {
            var result = Arrays.hashCode(bytes)
            result = 31 * result + len
            result = 31 * result + timeStampInNs.hashCode()
            return result
        }
    }

    override fun audioRecording(data: ByteArray, len: Int, timeStampInNs: Long) {
        val timeStamp = timeStampInNs - mStartTimeStamp
        val timeInSN = (timeStamp / 1e9).toInt()
        //reduce blocking
        if (timeInSN <= 1) {
            mMuxer.audioInput(data, len, timeStamp)
            mMp4Muxer.mAudioDevice.release(data)
        } else {
            try {
                mAudioInputLock.lock()
                var list = mAudioBytesCache[timeInSN]
                if (list == null) {
                    list = arrayListOf()
                    mAudioBytesCache[timeInSN] = list
                }
                list.add(AudioCache(data, len, timeStamp))
            } finally {
                mAudioInputLock.unlock()
            }
        }

    }

    override fun onStartRecordingFrame(cameraView: CameraView, timeStampInNs: Long) {
        //create mp4 muxer
        val size = cameraView.getSize(CameraFunctions.SIZE_RECORD)
        mMp4Muxer = createMp4Muxer(size.width, size.height)
        mMuxer = mMp4Muxer.mMuxer
        mMp4Path = mMp4Muxer.mMp4Path
        mMp4Width = mMp4Muxer.mMp4Width
        mMp4Height = mMp4Muxer.mMp4Height
        //reset base time.
        mStartTimeStamp = timeStampInNs
        mRecordFrame = 0
        mRecordingTimeStamp = 0
        mDiscardFrame = false
        mLastCheckFrameDiscardTime = 0
        //start muxer
        mMuxer.start()
        //start audio device
        mMp4Muxer.mAudioDevice.addCallback(this)
        mMp4Muxer.mAudioDevice.start()
        //can be released now...
        mReleased.set(false)
    }

    override fun onFrameRecording(cameraView: CameraView, frameBytes: CameraView.FrameBytes, width: Int, height: Int, format: Int,
                                  orientation: Int, facing: Int, timeStampInNs: Long) {
        val start = System.nanoTime()
        val fps = mMp4Muxer.params.fps
        val fpsGapInNs = (1e9 / fps).toLong()
        if (mDiscardFrame) {
            mDiscardFrame = false
        } else {
            if (mLastCheckFrameDiscardTime == 0L) {
                mLastCheckFrameDiscardTime = start
            } else {
                val checkDiscardGap = fps / mDiscardFrameThreshold
                if (mRecordFrame % checkDiscardGap == 0) {
                    val elapsed = System.nanoTime() - mLastCheckFrameDiscardTime
                    mDiscardFrame = elapsed > checkDiscardGap * 1e9 / (fps - frameBytes.bytesPool.maxSize)
                    mLastCheckFrameDiscardTime = System.nanoTime()
                }
            }
        }
        try {
            val data = frameBytes.datas
            if (mDiscardFrame) {
                logMED("onFrameRecording discard this frame...")
                return
            }
            //1.compress data to code
            var codeFormat = LibYuvUtils.FOURCC_NV21
            if (format == ImageFormat.YUV_420_888) {
                codeFormat = LibYuvUtils.FOURCC_I420
            } else if (format == ImageFormat.YV12) {
                codeFormat = LibYuvUtils.FOURCC_YV12
            }
            val compressed = compress(data, data.size, orientation, facing, width, height, codeFormat, mMp4Muxer.mSupportDeprecated420)
            //2.release bytes.
            frameBytes.bytesPool.releaseBytes(data)
            frameBytes.released = true
            //3.code compressed data to Mp4.
            //timeStampInNs - mStartTimeStamp
            ++mRecordFrame
            mRecordingTimeStamp += fpsGapInNs
            mMuxer.videoInput(compressed, compressed.size, mRecordingTimeStamp)
            mCompressBytesPool.releaseBytes(compressed)
            if (mRecordFrame % fps == 0) {
                consumeAudioInput(mRecordFrame / fps, Long.MAX_VALUE, false)
            }
        } finally {
            val consumedInNs = System.nanoTime() - start
            logMED("onFrameRecording cost ${TimeUnit.NANOSECONDS.toMillis(consumedInNs)} ms ,DiscardFrame=$mDiscardFrame! ,mRecordFrame=$mRecordFrame")
        }
    }

    private fun consumeAudioInput(timeInSN: Int, maxTimeInNs: Long, endOfInput: Boolean) {
        try {
            mAudioInputLock.lock()
            for (t in 0..timeInSN) {
                val cachedAudios = mAudioBytesCache.remove(t) ?: continue
                for ((bytes, len, timeInNs) in cachedAudios) {
                    if (timeInNs <= maxTimeInNs) {
                        mMuxer.audioInput(bytes, len, timeInNs)
                    }
                    mMp4Muxer.mAudioDevice.release(bytes)
                }
            }
        } finally {
            mAudioInputLock.unlock()
        }
        if (endOfInput) mAudioBytesCache.clear()
    }

    private fun compress(data: ByteArray, len: Int, orientation: Int, facing: Int,
                         width: Int, height: Int, format: Int, supportI420: Boolean): ByteArray {
        if (mCompressBytesPool == ByteArrayPool.EMPTY) {
            val size = mMp4Width * mMp4Height * 3 / 2
            mCompressBytesPool = ByteArrayPool(2, size)
        }
        val bf = mCompressBytesPool.getBytes()
        val rotation = LibYuvUtils.ROTATION_90 * orientation / 90
        val res = LibYuvUtils.scaleRotationAndMirrorToI420(data, len, bf, width, height,
                if (rotation % 90 == 0) mMp4Height else mMp4Width, if (rotation % 90 == 0) mMp4Width else mMp4Height,
                LibYuvUtils.SCALE_FILTER_NONE, rotation, facing == CameraFunctions.FACING_FRONT, format)
        if (res > 0 && !supportI420) {
            val result = mCompressBytesPool.getBytes()
            LibYuvUtils.i420ToNV12(bf, res, result, mMp4Width, mMp4Height)
            mCompressBytesPool.releaseBytes(bf)
            return result
        }
        return bf
    }

    override fun onStopRecordingFrame(cameraView: CameraView, timeStampInNs: Long) {
        if (mReleased.getAndSet(true)) return
        mMp4Muxer.mAudioDevice.removeCallback(this)
        mMp4Muxer.mAudioDevice.stop()
        consumeAudioInput((mRecordingTimeStamp / 1e9).toInt() + 1, mRecordingTimeStamp, true)
        mMuxer.stop()
        mCompressBytesPool.clear()
    }

    /**
     * Invoke multiple times.
     */
    abstract fun createMp4Muxer(frameWidth: Int, frameHeight: Int): Mp4Muxer
}