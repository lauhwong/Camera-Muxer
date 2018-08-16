package com.miracles.camera

import android.util.Log

/**
 * Log extensions.
 */
const val LOG_TAG = "ME-MEDIA"//javaClass.simpleName

//val logFile = File(File(Environment.getExternalStorageDirectory(), "camera&muxer"), "me.log")
fun Any.logMED(msg: String) {
    Log.d(LOG_TAG, msg)
//    logFile.appendBytes("$msg \r\n".toByteArray())
}

fun Any.logMEE(msg: String) {
    Log.e(LOG_TAG, msg)
//    logFile.appendBytes("$msg \r\n".toByteArray())
}

fun Any.logMEE(msg: String, ex: Throwable) {
    Log.e(LOG_TAG, msg, ex)
//    logFile.appendBytes("$msg \r\n".toByteArray())
}