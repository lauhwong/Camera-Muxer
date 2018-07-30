package com.miracles.codec.camera

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.miracles.camera.ByteArrayPool
import com.miracles.camera.CallbackBridge
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by lxw
 */
class AudioDevice private constructor(private val params: Params) : CallbackBridge<AudioDevice.Callback>(), LifeCycle {
    private var mStarted = AtomicBoolean(false)
    private val mStopped = AtomicBoolean(false)
    private var mAudioRecord: AudioRecord
    private var mRecordThread: Thread? = null
    private val mPoolFactor = 100
    //Note:do not initialized for bufSize...cause bug below 21....
    private val mByteArrayPool: ByteArrayPool = ByteArrayPool(mPoolFactor, 2048)

    companion object {
        fun create(params: Params): AudioDevice {
            return AudioDevice(params)
        }
    }

    /**
     * params for audioRecord initialize
     */
    class Params {
        var audioSource = MediaRecorder.AudioSource.MIC
        var audioSampleRate = 44100
        var audioChannel = AudioFormat.CHANNEL_IN_MONO
        var audioFormat = AudioFormat.ENCODING_PCM_16BIT
    }

    init {
        val bufSize = AudioRecord.getMinBufferSize(params.audioSampleRate, params.audioChannel, params.audioFormat)
        try {
            mAudioRecord = AudioRecord(params.audioSource, params.audioSampleRate, params.audioChannel, params.audioFormat, bufSize)
        } catch (ex: Throwable) {
            throw RuntimeException("AudioDevice init exception!", ex)
        }
    }

    override fun start() {
        if (mStarted.getAndSet(true)) return
        mByteArrayPool.initialize(mPoolFactor)
        mStopped.set(false)
        mAudioRecord.startRecording()
        mRecordThread = Thread({
            while (!mStopped.get()) {
                val data = mByteArrayPool.getBytes()
                val read = mAudioRecord.read(data, 0, data.size)
                if (read > 0) {
                    callback { audioRecording(data, read, SystemClock.elapsedRealtimeNanos()) }
                }
            }
        }, "MediaAudioRecord")
        mRecordThread?.start()
    }

    override fun stop() {
        if (!mStarted.getAndSet(false)) return
        if (mStopped.getAndSet(true)) return
        mAudioRecord.stop()
        mAudioRecord.release()
        mRecordThread?.join()
        mByteArrayPool.clear()
        mRecordThread = null
    }

    fun release(bytes: ByteArray) {
        mByteArrayPool.releaseBytes(bytes)
    }


    interface Callback {

        fun audioRecording(data: ByteArray, len: Int, timeStampInNs: Long)

    }
}
