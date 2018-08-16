package com.miracles.codec.camera

import android.media.MediaCodec
import com.miracles.camera.logMEE
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Created by lxw
 */
class MediaCodecLife(private val codec: MediaCodec, private val audio: Boolean) : LifeCycle {
    private val mBuf = MediaCodec.BufferInfo()
    private val mCodecCallbacks = CopyOnWriteArrayList<CodecCallback>()
    @Volatile
    private var mHasDataInput = false
    @Volatile
    private var mLowBufferCapacity = false

    companion object {
        const val INPUT_SUCCESS = 0
        const val INVALIDATE_INDEX = -1
        const val LOW_BUFFER_CAPACITY = -2
    }

    interface CodecCallback {
        fun onInput(codec: MediaCodec, data: ByteArray, len: Int, timeStamp: Long) {}
        fun onOutputStatus(codec: MediaCodec, status: Int) {}
        fun onOutputData(codec: MediaCodec, data: ByteArray, len: Int, timeStamp: Long, flags: Int) {}
        fun onEndOfOutputStream() {}
    }

    /**
     * input data to code or decode
     */
    fun input(data: ByteArray, len: Int, timeStamp: Long): Int {
        if (mLowBufferCapacity) return LOW_BUFFER_CAPACITY
        val index = codec.dequeueInputBuffer(-1)
        if (index < 0) return INVALIDATE_INDEX
        val inputBuffer = codec.inputBuffers[index]
        inputBuffer.clear()
        if (inputBuffer.capacity() < len || len <= 0) {
            logMEE("ERROR: MediaCodeLife's capacity=${inputBuffer.capacity()} < InputLen=$len")
            mLowBufferCapacity = true
            codec.queueInputBuffer(index, 0, 0, timeStamp, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return -1
        }
        if (!mHasDataInput) mHasDataInput = true
        inputBuffer.put(data, 0, len)
        codec.queueInputBuffer(index, 0, len, timeStamp, 0)
        callback { it.onInput(codec, data, len, timeStamp) }
        drainOutput(false)
        return INPUT_SUCCESS
    }

    override fun start() {
        codec.start()
    }

    override fun stop() {
        if (mHasDataInput) drainOutput(true)
        codec.stop()
        codec.release()
        mCodecCallbacks.clear()
    }

    private fun drainOutput(endOfStream: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(mBuf, 0)
            if (index < 0) {
                callback { it.onOutputStatus(codec, index) }
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                    if (!endOfStream) {
                    break
//                    }
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                } else if (index < 0) {

                }
            } else {
                //other flags to be ignored ?
                if ((mBuf.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    mBuf.size = 0
                }
                if (mBuf.size != 0) {
                    val outBuf = codec.outputBuffers[index]
                    outBuf.position(mBuf.offset)
                    outBuf.limit(mBuf.offset + mBuf.size)
                    val bytes = ByteArray(mBuf.size)
                    outBuf.get(bytes, 0, mBuf.size)
                    callback { it.onOutputData(codec, bytes, mBuf.size, mBuf.presentationTimeUs, mBuf.flags) }
                }
                codec.releaseOutputBuffer(index, false)
                if (mBuf.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }
        }
        if (endOfStream) callback { it.onEndOfOutputStream() }
    }

    private fun callback(cb: (CodecCallback) -> Unit) {
        for (callback in mCodecCallbacks) {
            cb.invoke(callback)
        }
    }

    fun addCallback(cb: CodecCallback) {
        mCodecCallbacks.add(cb)
    }

    fun removeCallback(cb: CodecCallback) {
        mCodecCallbacks.remove(cb)
    }
}