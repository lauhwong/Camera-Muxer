package com.miracles.codec.camera

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Created by lxw
 */
class CodeThreadPool(fixedSize: Int) {
    private val mPool: ThreadPoolExecutor
    private val mQueue = LinkedBlockingQueue<Runnable>()
    private val mWaitLock = Object()
    private val mExecutedTimestamps = HashMap<Long, Boolean>()

    init {
        mPool = object : ThreadPoolExecutor(fixedSize, fixedSize, 0L, TimeUnit.MILLISECONDS,
                mQueue) {
            override fun afterExecute(r: Runnable, t: Throwable?) {
                super.afterExecute(r, t)
                synchronized(mWaitLock){
                    mExecutedTimestamps[(r as TimestampRunnable).timeStamp] = true
                    mWaitLock.notifyAll()
                }
            }
        }
    }

    fun hasSmallerTimestampRunning(timeStamp: Long): Boolean {
        synchronized(mWaitLock) {
            for ((key, value) in mExecutedTimestamps) {
                if (timeStamp <= key) break
                if (!value) return true
            }
        }
        return false
    }

    fun runCondition(condition: () -> Boolean) {
        synchronized(mWaitLock) {
            while (condition.invoke()) {
                try {
                    mWaitLock.wait()
                } catch (ignored: Exception) {
                }
            }
        }
    }

    fun execute(runnable: TimestampRunnable) {
        synchronized(mWaitLock){
            mExecutedTimestamps[runnable.timeStamp] = false
        }
        mPool.execute(runnable)
    }

    fun awaitTerminate() {
        mPool.shutdown()
        mPool.awaitTermination(1, TimeUnit.DAYS)
        mExecutedTimestamps.clear()
    }


    open class TimestampRunnable(val timeStamp: Long) : Runnable {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TimestampRunnable) return false

            if (timeStamp != other.timeStamp) return false

            return true
        }

        override fun hashCode(): Int {
            return timeStamp.hashCode()
        }

        override fun run() {

        }

    }

}