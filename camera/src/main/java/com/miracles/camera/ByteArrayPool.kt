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

    constructor(maxSize: Int, perSize: Int, factor: Int) : this(maxSize, perSize) {
        initialize(factor)
    }

    fun initialize(factor: Int) {
        if (factor <= 0) return
        for (x in 0..factor) {
            if (!releaseBytes(ByteArray(perSize))) {
                break
            }
        }
    }

    fun getBytes(): ByteArray {
        return getBytes(0, TimeUnit.MILLISECONDS)
    }

    fun getBytes(timeout: Long, unit: TimeUnit): ByteArray {
        var result: ByteArray? = null
        synchronized(mPool) {
            try {
                result = mPool.poll(timeout, unit)
            } catch (ignored: InterruptedException) {
            }
        }
        return result ?: ByteArray(perSize)
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