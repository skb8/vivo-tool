package com.skb8.vivotool.core

import android.util.Log
import de.robv.android.xposed.XposedBridge

/**
 * Логгер, работающий и внутри процессов с хуками, и в самом приложении.
 *
 * В процессе с хуками сообщения дублируются в журнал Xposed (виден в LSPosed),
 * в обычном процессе приложения — только в logcat.
 */
object XLog {

    private fun toXposed(message: String) {
        try {
            XposedBridge.log("[${Constants.TAG}] $message")
        } catch (_: Throwable) {
            // Модуль не активирован — XposedBridge недоступен.
        }
    }

    fun d(message: String) {
        Log.d(Constants.TAG, message)
    }

    fun i(message: String) {
        Log.i(Constants.TAG, message)
        toXposed(message)
    }

    fun w(message: String) {
        Log.w(Constants.TAG, message)
        toXposed("W: $message")
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(Constants.TAG, message, throwable)
        toXposed("E: $message" + (throwable?.let { "\n" + Log.getStackTraceString(it) } ?: ""))
    }
}
