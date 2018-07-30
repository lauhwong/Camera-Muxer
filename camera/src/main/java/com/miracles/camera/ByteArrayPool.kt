package com.miracles.camera

import java.util.*

/**
 * Created by lxw
 */
class ByteArrayPool(val maxSize: Int, val perSize: Int) {
    companion object {
        val EMPTY = ByteArrayPool(0, 1)
    }

    private val mPool = ArrayDeque<ByteArray>(maxSize)

    constructor(maxSize: Int, perSize: Int, factor: Int) : this(maxSize, perSize) {
        initialize(factor)
    }

    fun initialize(factor: Int) {
        if (factor <= 0) return
        for (x in 0..factor) {
            releaseBytes(ByteArray(perSize))
        }
    }

    fun getBytes(): ByteArray {
        var result: ByteArray? = null
        synchronized(mPool) {
            result = mPool.poll()
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