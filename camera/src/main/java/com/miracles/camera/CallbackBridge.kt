package com.miracles.camera

/**
 * Created by lxw
 */
open class CallbackBridge<T> {
    private val lock = Object()
    private val mCallbacks = arrayListOf<T>()


    protected open fun callback(methods: T.() -> Unit) {
        synchronized(lock) {
            for (cb in mCallbacks) {
                methods.invoke(cb)
            }
        }
    }

    open fun addCallback(cb: T) {
        synchronized(lock) {
            mCallbacks.add(cb)
        }
    }

    open fun removeCallback(cb: T) {
        synchronized(lock) {
            mCallbacks.remove(cb)
        }
    }

    open fun addCallbackIfNotExist(cb: T): Boolean {
        synchronized(lock) {
            val exist = mCallbacks.contains(cb)
            if (!exist) {
                addCallback(cb)
            }
            return exist
        }
    }
}