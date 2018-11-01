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
    private var mStartFlag = AtomicBoolean(false)
    private var mStopFlag = AtomicBoolean(true)
    @Volatile
    private var mAudioRecordInitializedFlag = false
    private var mAudioThread: Thread? = null
    private val mAudioThreadLock = Object()
    private val mPoolFactor = 2
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
        mAudioThread = Thread({
            val audioRecord: AudioRecord
            val bufSize = AudioRecord.getMinBufferSize(params.audioSampleRate, params.audioChannel, params.audioFormat)
            try {
                audioRecord = AudioRecord(params.audioSource, params.audioSampleRate, params.audioChannel, params.audioFormat, bufSize)
            } catch (ex: Throwable) {
                return@Thread
            }
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                return@Thread
            }
            mAudioRecordInitializedFlag = true
            while (!mStartFlag.get()) {
                try {
                    synchronized(mAudioThreadLock) {
                        mAudioThreadLock.wait()
                    }
                } catch (ex: InterruptedException) {
                    return@Thread
                }
            }
            audioRecord.startRecording()
            while (!mStopFlag.get()) {
                val data = mByteArrayPool.getBytes()
                val read = audioRecord.read(data, 0, data.size)
                if (read > 0) {
                    callback { audioRecording(data, read, SystemClock.elapsedRealtimeNanos()) }
                }
            }
            audioRecord.stop()
            audioRecord.release()
        }, "MediaAudioRecord")
        mAudioThread?.start()
    }

    override fun start() {
        if (mStartFlag.getAndSet(true)) return
        mStopFlag.set(false)
        synchronized(mAudioThreadLock) {
            mAudioThreadLock.notifyAll()
        }
    }

    override fun stop() {
        if (mStopFlag.getAndSet(true)) return
        synchronized(mAudioThreadLock) {
            mAudioThreadLock.notifyAll()
        }
        if (mAudioRecordInitializedFlag) {
            mAudioThread?.join()
        }
        mByteArrayPool.clear()
        mAudioThread = null
    }

    fun release(bytes: ByteArray) {
        mByteArrayPool.releaseBytes(bytes)
    }


    interface Callback {

        fun audioRecording(data: ByteArray, len: Int, timeStampInNs: Long)

    }
}
