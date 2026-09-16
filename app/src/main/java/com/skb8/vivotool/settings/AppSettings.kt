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
class AppSettings(private val context: Context) {

    private val prefs: SharedPreferences = run {
        val p = try {
            @Suppress("DEPRECATION", "WorldReadableFiles")
            context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: Throwable) {
            context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        }
        WorldReadable.fix(context, Constants.PREFS_NAME)
        p
    }

    /** Видны ли настройки процессам с хуками. */
    val isSharedWithHooks: Boolean
        get() = WorldReadable.isReadable(context, Constants.PREFS_NAME)

    fun isEnabled(hook: BaseHook): Boolean =
        prefs.getBoolean(Constants.enabledKey(hook.id), hook.enabledByDefault)

    fun setEnabled(hook: BaseHook, enabled: Boolean) {
        prefs.edit().putBoolean(Constants.enabledKey(hook.id), enabled).commit()
        WorldReadable.fix(context, Constants.PREFS_NAME)
    }

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).commit()
        WorldReadable.fix(context, Constants.PREFS_NAME)
    }

    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).commit()
        WorldReadable.fix(context, Constants.PREFS_NAME)
    }

    fun getFreeformLimit(): Int =
        getInt(Constants.FREEFORM_LIMIT_KEY, Constants.DEFAULT_FREEFORM_LIMIT).coerceIn(2, 10)

    fun setFreeformLimit(limit: Int) {
        setInt(Constants.FREEFORM_LIMIT_KEY, limit.coerceIn(2, 10))
    }

    fun remove(key: String) {
        prefs.edit().remove(key).commit()
        WorldReadable.fix(context, Constants.PREFS_NAME)
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
        editor.commit()
        WorldReadable.fix(context, Constants.PREFS_NAME)
    }
}
