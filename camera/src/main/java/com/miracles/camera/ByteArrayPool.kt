package com.miracles.camera

import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * Created by lxw
 */
class ByteArrayPool(val maxSize: Int, val perSize: Int) {
    companion object {
        val EMPTY = ByteArrayPool(1, 1)
    }

    private val mPool = LinkedBlockingDeque<ByteArray>(maxSize)
    private var mExistInstanceCount = 0
    private val mInstanceCountLock = ByteArray(0)

    private fun newBytesArray(): ByteArray? {
        synchronized(mInstanceCountLock) {
            if (mExistInstanceCount > maxSize) return null
            mExistInstanceCount++
            return ByteArray(perSize)
        }
    }

    fun getBytes(): ByteArray {
        var result = getBytes(0, TimeUnit.MILLISECONDS)
        if (result == null) {
            result = getBytes(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
        }
        return result ?: throw NullPointerException("force get bytes is null.")
    }

    fun getBytes(timeout: Long, unit: TimeUnit): ByteArray? {
        var result = hasAvailableBytes(timeout, unit)
        if (result == null) {
            result = newBytesArray()
        }
        return result
    }

    private fun hasAvailableBytes(timeout: Long, unit: TimeUnit): ByteArray? {
        var result: ByteArray? = null
        synchronized(mPool) {
            try {
                result = mPool.poll(timeout, unit)
            } catch (ignored: InterruptedException) {
            }
        }
        return result
    }

    fun releaseBytes(bytes: ByteArray): Boolean {
        if (bytes.size != perSize) return false
        synchronized(mPool) {
            if (mPool.size < maxSize) {
                mPool.offer(bytes)
                return true
            }
        }
        return false
    }

    fun clear() {
        synchronized(mPool) {
            mPool.clear()
        }
        synchronized(mInstanceCountLock) {
            mExistInstanceCount = 0
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteArrayPool) return false

        if (perSize != other.perSize) return false
        if (maxSize != other.maxSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = perSize
        result = 31 * result + maxSize
        return result
    }

}