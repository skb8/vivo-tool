package com.skb8.vivotool.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.skb8.vivotool.core.XLog
import java.util.concurrent.TimeUnit

/**
 * Закрытие целевого приложения, чтобы изменения твика подхватились
 * при его следующем запуске.
 */
object AppControl {

    private const val SU_TIMEOUT_SECONDS = 10L

    /**
     * Пробует остановить приложение через root. Возвращает `false`, если
     * root недоступен — тогда остаётся открыть экран «О приложении».
     */
    fun forceStop(packageName: String): Boolean = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $packageName"))
        val finished = process.waitFor(SU_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            false
        } else {
            process.exitValue() == 0
        }
    } catch (t: Throwable) {
        XLog.w("Не удалось выполнить force-stop $packageName: ${t.message}")
        false
    }

    /** Системный экран «О приложении», где есть кнопка принудительной остановки. */
    fun openAppInfo(context: Context, packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            XLog.w("Не удалось открыть настройки приложения $packageName: ${t.message}")
        }
    }
}
