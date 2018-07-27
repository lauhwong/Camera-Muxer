package com.miracles.codec.camera

import android.media.MediaCodec
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Created by lxw
 */
class MediaCodecLife(private val codec: MediaCodec, private val audio: Boolean) : LifeCycle {
    private val mBuf = MediaCodec.BufferInfo()
    private val mCodecCallbacks = CopyOnWriteArrayList<CodecCallback>()

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
        val index = codec.dequeueInputBuffer(-1)
        if (index < 0) return -1
        val inputBuffer = codec.inputBuffers[index]
        inputBuffer.clear()
        if (inputBuffer.capacity() < len) {
            codec.queueInputBuffer(index, 0, len, timeStamp, 0)
            return -1
        }
        inputBuffer.put(data, 0, len)
        codec.queueInputBuffer(index, 0, len, timeStamp, 0)
        callback { it.onInput(codec, data, len, timeStamp) }
        drainOutput(false)
        return 0
    }

    override fun start() {
        codec.start()
    }

    override fun stop() {
        drainOutput(true)
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