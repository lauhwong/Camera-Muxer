package com.miracles.camera

import android.util.Log

/**
 * Log extensions.
 */
const val LOG_TAG = "ME-MEDIA"

fun Any.logMED(msg: String) {
    //javaClass.simpleName
    Log.d(LOG_TAG, msg)
}

fun Any.logMEE(msg: String) {
    Log.e(LOG_TAG, msg)
}

fun Any.logMEE(msg: String, ex: Throwable) {
    Log.e(LOG_TAG, msg, ex)
}