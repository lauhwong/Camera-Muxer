package com.miracles.codec.camera

/**
 * Created by lxw
 */
interface MeCodec : LifeCycle {

    fun audioInput(bytes: ByteArray, len: Int, timeStampInNs: Long): Int

    fun videoInput(bytes: ByteArray, len: Int, timeStampInNs: Long): Int

}