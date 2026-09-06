package com.skb8.vivotool.settings

import android.content.Context
import android.content.SharedPreferences
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.XLog

/**
 * Настройки со стороны приложения.
 *
 * Файл открывается в режиме MODE_WORLD_READABLE: только так процессы с хуками
 * смогут прочитать его через `XSharedPreferences`. Если LSPosed не активировал
 * модуль, система запретит такой режим — тогда используется приватный файл,
 * а UI показывает предупреждение ([isSharedWithHooks] == false).
 */
class AppSettings(context: Context) {

    private var shared = true

    @Suppress("DEPRECATION", "WorldReadableFiles")
    private val prefs: SharedPreferences = try {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_WORLD_READABLE)
    } catch (t: Throwable) {
        XLog.w("MODE_WORLD_READABLE недоступен, настройки не увидят хуки: ${t.message}")
        shared = false
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        if (shared) WorldReadable.fix(context, Constants.PREFS_NAME)
    }

    /** Видны ли настройки процессам с хуками. */
    val isSharedWithHooks: Boolean get() = shared

    fun isEnabled(hook: BaseHook): Boolean =
        prefs.getBoolean(Constants.enabledKey(hook.id), hook.enabledByDefault)

    fun setEnabled(hook: BaseHook, enabled: Boolean) {
        prefs.edit().putBoolean(Constants.enabledKey(hook.id), enabled).apply()
    }

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    /** Все boolean-настройки с указанным префиксом. */
    fun booleanEntriesWithPrefix(prefix: String): Map<String, Boolean> =
        prefs.all
            .filterKeys { it.startsWith(prefix) }
            .mapNotNull { (key, value) -> (value as? Boolean)?.let { key to it } }
            .toMap()

    /** Удаляет все настройки с указанным префиксом. */
    fun removeWithPrefix(prefix: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }
}
