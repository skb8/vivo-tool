package com.skb8.vivotool.settings

import android.content.Context
import android.content.SharedPreferences
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.XLog

import com.skb8.vivotool.core.ServiceBridge

/**
 * Настройки со стороны приложения.
 *
 * Настройки сохраняются локально в SharedPreferences и синхронизируются с RemotePreferences
 * через [ServiceBridge], если сервис Vector/LibXposed подключен.
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
        get() = ServiceBridge.isConnected || WorldReadable.isReadable(context, Constants.PREFS_NAME)

    fun syncToRemote(remote: SharedPreferences) {
        try {
            val localAll = prefs.all
            val remoteAll = remote.all
            if (localAll.isNotEmpty()) {
                val editor = remote.edit()
                for ((k, v) in localAll) {
                    when (v) {
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Float -> editor.putFloat(k, v)
                        is String -> editor.putString(k, v)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(k, v as? Set<String>)
                        }
                    }
                }
                editor.apply()
                XLog.i("Настройки успешно синхронизированы в RemotePreferences (${localAll.size} записей)")
            } else if (remoteAll.isNotEmpty()) {
                val editor = prefs.edit()
                for ((k, v) in remoteAll) {
                    when (v) {
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Float -> editor.putFloat(k, v)
                        is String -> editor.putString(k, v)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(k, v as? Set<String>)
                        }
                    }
                }
                editor.apply()
                XLog.i("Настройки успешно восстановлены из RemotePreferences (${remoteAll.size} записей)")
            }
        } catch (t: Throwable) {
            XLog.e("Не удалось синхронизировать настройки с RemotePreferences", t)
        }
    }

    fun isEnabled(hook: BaseHook): Boolean =
        prefs.getBoolean(Constants.enabledKey(hook.id), hook.enabledByDefault)

    fun setEnabled(hook: BaseHook, enabled: Boolean) {
        val key = Constants.enabledKey(hook.id)
        prefs.edit().putBoolean(key, enabled).commit()
        ServiceBridge.remotePrefs?.edit()?.putBoolean(key, enabled)?.apply()
        WorldReadable.fix(context, Constants.PREFS_NAME)
    }

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).commit()
        ServiceBridge.remotePrefs?.edit()?.putBoolean(key, value)?.apply()
        WorldReadable.fix(context, Constants.PREFS_NAME)
    }

    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).commit()
        ServiceBridge.remotePrefs?.edit()?.putInt(key, value)?.apply()
        WorldReadable.fix(context, Constants.PREFS_NAME)
    }

    fun getFreeformLimit(): Int =
        getInt(Constants.FREEFORM_LIMIT_KEY, Constants.DEFAULT_FREEFORM_LIMIT).coerceIn(2, 10)

    fun setFreeformLimit(limit: Int) {
        setInt(Constants.FREEFORM_LIMIT_KEY, limit.coerceIn(2, 10))
    }

    fun getStringSet(key: String, default: Set<String> = emptySet()): Set<String> =
        prefs.getStringSet(key, default) ?: default

    fun setStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).commit()
        ServiceBridge.remotePrefs?.edit()?.putStringSet(key, value)?.apply()
        WorldReadable.fix(context, Constants.PREFS_NAME)
    }

    fun getIslandApps(): Set<String> = getStringSet(Constants.ISLAND_APPS_KEY)

    fun setIslandApps(apps: Set<String>) {
        setStringSet(Constants.ISLAND_APPS_KEY, apps)
    }

    fun remove(key: String) {
        prefs.edit().remove(key).commit()
        ServiceBridge.remotePrefs?.edit()?.remove(key)?.apply()
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
        val remoteEditor = ServiceBridge.remotePrefs?.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach {
            editor.remove(it)
            remoteEditor?.remove(it)
        }
        editor.commit()
        remoteEditor?.apply()
        WorldReadable.fix(context, Constants.PREFS_NAME)
    }
}
