package com.miracles.camera

import java.util.*

/**
 * Created by lxw
 */
class ByteArrayPool(val maxSize: Int, val perSize: Int) {
    companion object {
        val EMPTY = ByteArrayPool(0, 1)
    }

    private val mMaxPoolSize = maxSize / perSize
    private val mPool = ArrayDeque<ByteArray>(mMaxPoolSize)

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
            if (mPool.size < mMaxPoolSize) {
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
        if (mMaxPoolSize != other.mMaxPoolSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = perSize
        result = 31 * result + mMaxPoolSize
        return result
    }

}