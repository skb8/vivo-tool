package com.skb8.vivotool.core

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * Логгер, работающий и внутри процессов с хуками, и в самом приложении.
 *
 * В процессе с хуками сообщения дублируются в журнал Xposed (виден в Vector/LSPosed),
 * в обычном процессе приложения — только в logcat.
 */
object XLog {

    @Volatile
    private var xposed: XposedInterface? = null

    fun setXposedInterface(interfaceInstance: XposedInterface?) {
        xposed = interfaceInstance
    }

    private fun toXposed(priority: Int, message: String, throwable: Throwable? = null) {
        try {
            xposed?.log(priority, Constants.TAG, message, throwable)
        } catch (_: Throwable) {
            // Модуль не активирован — XposedInterface недоступен.
        }
    }

    fun d(message: String) {
        Log.d(Constants.TAG, message)
    }

    fun i(message: String) {
        Log.i(Constants.TAG, message)
        toXposed(Log.INFO, message)
    }

    fun w(message: String) {
        Log.w(Constants.TAG, message)
        toXposed(Log.WARN, "W: $message")
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(Constants.TAG, message, throwable)
        toXposed(Log.ERROR, "E: $message", throwable)
    }
}
