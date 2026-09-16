package com.skb8.vivotool.core

import com.skb8.vivotool.BuildConfig
import de.robv.android.xposed.XSharedPreferences

/**
 * Чтение настроек изнутри процессов с хуками.
 *
 * Настройки пишет UI приложения (см. `settings/AppSettings`) в режиме
 * MODE_WORLD_READABLE, LSPosed отдаёт их модулю через [XSharedPreferences].
 */
internal object HookPrefs {

    private val prefs: XSharedPreferences by lazy {
        XSharedPreferences(BuildConfig.APPLICATION_ID, Constants.PREFS_NAME).apply {
            makeWorldReadable()
        }
    }

    private fun snapshot(): XSharedPreferences? = try {
        prefs.apply {
            if (hasFileChanged()) reload()
        }
    } catch (t: Throwable) {
        XLog.e("Не удалось прочитать настройки модуля", t)
        null
    }

    fun isEnabled(hook: BaseHook): Boolean =
        snapshot()?.getBoolean(Constants.enabledKey(hook.id), hook.enabledByDefault)
            ?: hook.enabledByDefault

    fun getBoolean(key: String, default: Boolean): Boolean =
        snapshot()?.getBoolean(key, default) ?: default

    fun getInt(key: String, default: Int): Int =
        snapshot()?.getInt(key, default) ?: default

    fun getFreeformLimit(): Int =
        getInt(Constants.FREEFORM_LIMIT_KEY, Constants.DEFAULT_FREEFORM_LIMIT).coerceIn(2, 10)

    fun getString(key: String, default: String): String =
        snapshot()?.getString(key, default) ?: default

    /** Все настройки с указанным префиксом — для хуков со списком значений. */
    fun entriesWithPrefix(prefix: String): Map<String, Any?> {
        val store = snapshot() ?: return emptyMap()
        return try {
            store.all.filterKeys { it.startsWith(prefix) }
        } catch (t: Throwable) {
            XLog.e("Не удалось перечислить настройки с префиксом '$prefix'", t)
            emptyMap()
        }
    }
}
