package com.miracles.mediacodec

import android.app.Application
import com.miracles.camera.logMEE

/**
 * Created by lxw
 */
class MeApp: Application() {
    override fun onCreate() {
        super.onCreate()
        val defExHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            logMEE("UncaughtException--->",e)
            defExHandler.uncaughtException(t,e)
        }
    }
}