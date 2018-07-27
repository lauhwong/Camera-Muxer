package com.miracles.camera;

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by lxw
 *
 * @see android.os.HandlerThread
 */
class ThreadLoop(private val name: String) {
    private val mQueue = LinkedBlockingQueue<Runnable>()
    private val mQuitFlag = AtomicBoolean(false)
    private val mStartFlag = AtomicBoolean(false)
    private var mThread: Thread? = null

    fun start() {
        if (!mStartFlag.getAndSet(true)) {
            mQuitFlag.set(false)
            val thread = Thread(this::loop, name)
            thread.start()
            mThread = thread
        }
    }

    private fun loop() {
        while (true) {
            try {
                val unhandled = mQueue.take()
                if (unhandled is QuitRunnable) {
                    break
                }
                unhandled.run()
            } catch (ex: Throwable) {
                logMEE("ThreadLoop run err!", ex)
            }
        }
    }

    fun enqueue(runnable: Runnable) {
        if (!mStartFlag.get()) {
            logMED("ThreadLoop enqueue hasn't started!")
            return
        }
        try {
            mQueue.offer(runnable)
        } catch (ex: Exception) {
            logMEE("ThreadLoop enqueue!", ex)
        }
    }

    /**
     * QuitSafe like handlerThread's quitSafe.
     */
    fun quit() {
        if (!mQuitFlag.getAndSet(true)) {
            enqueue(QuitRunnable())
        }
        logMED("ThreadLoop quit mQueue=${mQueue.size}")
        mThread?.join()
        mQueue.clear()
        mStartFlag.set(false)
        logMED("ThreadLoop quit success!")
    }

    class QuitRunnable : Runnable {
        override fun run() {

        }
    }
}
