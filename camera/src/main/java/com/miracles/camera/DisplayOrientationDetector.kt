package com.miracles.camera

import android.content.Context
import android.util.SparseIntArray
import android.view.Display
import android.view.OrientationEventListener
import android.view.Surface

/**
 * Created by lxw
 */
abstract class DisplayOrientationDetector(ctx: Context) {
    private val mOrientationEventListener: OrientationEventListener
    var mDisplay: Display? = null
    private var mLastKnownDisplayOrientation = 0

    companion object {
        val DISPLAY_ORIENTATIONS = SparseIntArray()

        init {
            DISPLAY_ORIENTATIONS.put(Surface.ROTATION_0, 0)
            DISPLAY_ORIENTATIONS.put(Surface.ROTATION_90, 90)
            DISPLAY_ORIENTATIONS.put(Surface.ROTATION_180, 180)
            DISPLAY_ORIENTATIONS.put(Surface.ROTATION_270, 270)
        }
    }

    init {
        mOrientationEventListener = object : OrientationEventListener(ctx) {
            private var mLastKnownRotation = ORIENTATION_UNKNOWN
            override fun onOrientationChanged(orientation: Int) {
                val display = mDisplay ?: return
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN || mDisplay == null) {
                    return
                }
                val rotation = display.orientation
                if (mLastKnownRotation != rotation) {
                    mLastKnownRotation = rotation
                    dispatchOnDisplayOrientationChanged(DISPLAY_ORIENTATIONS.get(rotation))
                }
            }
        }
    }

    fun enable(display: Display) {
        mDisplay = display
        mOrientationEventListener.enable()
        dispatchOnDisplayOrientationChanged(DISPLAY_ORIENTATIONS.get(display.rotation))
    }

    fun disable() {
        mOrientationEventListener.disable()
        mDisplay = null
    }

    fun dispatchOnDisplayOrientationChanged(displayOrientation: Int) {
        mLastKnownDisplayOrientation = displayOrientation
        onDisplayOrientationChanged(displayOrientation)
    }

    /**
     * Called when display orientation is changed.
     *
     * @param displayOrientation(counterclockwise) One of 0, 90, 180, and 270.
     */
    abstract fun onDisplayOrientationChanged(displayOrientation: Int)
}