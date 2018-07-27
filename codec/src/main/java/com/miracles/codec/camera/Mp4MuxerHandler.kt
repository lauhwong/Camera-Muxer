package com.miracles.codec.camera

import android.graphics.ImageFormat
import com.miracles.camera.ByteArrayPool
import com.miracles.camera.CameraFunctions
import com.miracles.camera.CameraView
import com.miracles.camera.logMED
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
    private val mAudioBytesCache = HashMap<Int, MutableList<AudioCache>>()
    private val mAudioInputLock = ReentrantLock(true)
    private var mReleased = AtomicBoolean(false)

    private data class AudioCache(val bytes: ByteArray, val len: Int, val timeStampInNs: Long)

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
        mReleased.set(false)
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
        //start muxer
        mMuxer.start()
        //start audio device
        mMp4Muxer.mAudioDevice.addCallback(this)
        mMp4Muxer.mAudioDevice.start()
    }

    override fun onFrameRecording(cameraView: CameraView, data: ByteArray, len: Int, bytesPool: ByteArrayPool, width: Int, height: Int, format: Int,
                                  orientation: Int, facing: Int, timeStampInNs: Long) {
        val start = System.currentTimeMillis()
        var codeFormat = LibYuvUtils.FOURCC_NV21
        if (format == ImageFormat.YUV_420_888) {
            codeFormat = LibYuvUtils.FOURCC_I420
        } else if (format == ImageFormat.YV12) {
            codeFormat = LibYuvUtils.FOURCC_YV12
        }
        val compressed = compress(data, len, orientation, facing, width, height, codeFormat, mMp4Muxer.mSupportDeprecated420)
        val fps = mMp4Muxer.params.fps
        //timeStampInNs - mStartTimeStamp
        ++mRecordFrame
        mRecordingTimeStamp += (1e9 / fps).toLong()
        mMuxer.videoInput(compressed, compressed.size, mRecordingTimeStamp)
        mCompressBytesPool.releaseBytes(compressed)
        if (mRecordFrame % fps == 0) {
            consumeAudioInput(mRecordFrame / fps, Long.MAX_VALUE, false)
        }
        logMED("onFrameRecording cost ${System.currentTimeMillis() - start}ms !")
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
            mCompressBytesPool = ByteArrayPool(size * 2, size)
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