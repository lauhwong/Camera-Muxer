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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by lxw
 */
abstract class Mp4MuxerHandler : AudioDevice.Callback, CameraView.Callback {
    private var mCompressedFrameBytesPool: ByteArrayPool = ByteArrayPool.EMPTY
    private lateinit var mMuxer: MeMuxer
    private lateinit var mMp4Muxer: Mp4Muxer
    protected lateinit var mMp4Path: String
    private var mStartTimestamp = AtomicLong(0)
    private var mRecordingTimeStamp = 0L
    protected var mMp4Width = 0
    protected var mMp4Height = 0
    private var mRecordFrame = 0
    @SuppressLint("UseSparseArrays")
    private val mAudioBytesCache = HashMap<Int, MutableList<AudioCache>>()
    private val mAudioInputLock = ReentrantLock(true)
    private var mReleased = AtomicBoolean(true)
    private var mCodeThreadPool: FrameCodeThreadPool? = null
    val codePoolSize: Int
    private val mVideoLock = Object()

    companion object {
        private val CODE_POOL_SIZE = Math.min(Math.max(2, Runtime.getRuntime().availableProcessors()), 3)
    }

    constructor() : this(CODE_POOL_SIZE)

    constructor(codePoolSize: Int) {
        this.codePoolSize = codePoolSize
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
        try {
            val startUpTimestamp = mStartTimestamp.get()
            if (startUpTimestamp <= 0) return
            val timeStamp = timeStampInNs - startUpTimestamp
            mMuxer.audioInput(data, len, timeStamp)
        } finally {
            mMp4Muxer.mAudioDevice.release(data)
        }

    }

    override fun onStartRecordingFrame(cameraView: CameraView, timeStampInNs: Long) {
        //create threadPool
        mCodeThreadPool = FrameCodeThreadPool(codePoolSize)
        //create mp4 muxer
        val size = cameraView.getSize(CameraFunctions.SIZE_RECORD)
        mMp4Muxer = createMp4Muxer(size.width, size.height)
        mMuxer = mMp4Muxer.mMuxer
        mMp4Path = mMp4Muxer.mMp4Path
        mMp4Width = mMp4Muxer.mMp4Width
        mMp4Height = mMp4Muxer.mMp4Height
        //reset base time.
        mStartTimestamp.set(0)
        mRecordFrame = 0
        mRecordingTimeStamp = 0
        //start muxer
        mMuxer.start()
        //start audio device
        mMp4Muxer.mAudioDevice.addCallback(this)
        mMp4Muxer.mAudioDevice.start()
        //bytes pool of  compressed
        if (mCompressedFrameBytesPool == ByteArrayPool.EMPTY) {
            val f = if (mMp4Muxer.mSupportDeprecated420) 1 else 2
            val mp4Size = mMp4Width * mMp4Height * 3 / 2
            mCompressedFrameBytesPool = ByteArrayPool(f * codePoolSize, mp4Size)
        }
        //can be released now...
        mReleased.set(false)
    }

    override fun onFrameRecording(cameraView: CameraView, frameBytes: CameraView.FrameBytes, width: Int, height: Int, format: Int,
                                  orientation: Int, facing: Int, timeStampInNs: Long) {
        if (mStartTimestamp.get() <= 0) {
            mStartTimestamp.compareAndSet(0, timeStampInNs)
        }
        val pool = mCodeThreadPool
                ?: throw IllegalArgumentException("RecordThreadPool is not initialized! ")
        frameBytes.consumed = true
        pool.execute(object : FrameCodeThreadPool.TimestampRunnable(timeStampInNs) {
            override fun run() {
                val start = System.nanoTime()
                val fps = mMp4Muxer.params.fps
                val fpsGapInNs = (1e9 / fps).toLong()
                val data = frameBytes.datas
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
                //3.wait for last frame to complete.
                pool.runCondition { pool.hasSmallerTimestampRunning(timeStamp) }
                //4.code compressed data to Mp4.
                ++mRecordFrame
                val fpsEnsure = mMp4Muxer.params.fpsEnsure
                if (fpsEnsure) {
                    mRecordingTimeStamp = (mRecordFrame - 1) * fpsGapInNs
                } else {
                    mRecordingTimeStamp = timeStampInNs - mStartTimestamp.get()
                }
                mMuxer.videoInput(compressed, compressed.size, mRecordingTimeStamp)
                mCompressedFrameBytesPool.releaseBytes(compressed)
                val consumedInNs = System.nanoTime() - start
                logMED("onFrameRecording cost ${TimeUnit.NANOSECONDS.toMillis(consumedInNs)} ms!")
            }
        })
    }

    private fun compress(data: ByteArray, len: Int, orientation: Int, facing: Int,
                         width: Int, height: Int, format: Int, supportI420: Boolean): ByteArray {
        val bf = mCompressedFrameBytesPool.getBytes()
        val rotation = LibYuvUtils.ROTATION_90 * orientation / 90
        val res = LibYuvUtils.scaleRotationAndMirrorToI420(data, len, bf, width, height,
                if (rotation % 90 == 0) mMp4Height else mMp4Width, if (rotation % 90 == 0) mMp4Width else mMp4Height,
                LibYuvUtils.SCALE_FILTER_NONE, rotation, facing == CameraFunctions.FACING_FRONT, format)
        if (res > 0 && !supportI420) {
            val result = mCompressedFrameBytesPool.getBytes()
            LibYuvUtils.i420ToNV12(bf, res, result, mMp4Width, mMp4Height)
            mCompressedFrameBytesPool.releaseBytes(bf)
            return result
        }
        return bf
    }

    override fun onStopRecordingFrame(cameraView: CameraView, timeStampInNs: Long) {
        if (mReleased.getAndSet(true)) return
        //stop audio
        mMp4Muxer.mAudioDevice.removeCallback(this)
        mMp4Muxer.mAudioDevice.stop()
        //stop threadPool
        mCodeThreadPool?.awaitTerminate()
        mCodeThreadPool = null
        mMuxer.stop()
        //clear cache.
        mCompressedFrameBytesPool.clear()
        logMED("Stop Record TotalTimeInSN=${(mRecordingTimeStamp / 1e9).toInt() + 1} ,Frames=$mRecordFrame")
    }

    /**
     * Invoke multiple times.
     */
    abstract fun createMp4Muxer(frameWidth: Int, frameHeight: Int): Mp4Muxer
}