package com.skb8.vivotool.core

object Constants {
    /** Имя файла настроек, общего для UI приложения и процессов с хуками. */
    const val PREFS_NAME = "vivo_tool_prefs"

    /** Тег для logcat и журнала Xposed. */
    const val TAG = "VivoTool"

    /** Ключ настройки «хук включён». */
    fun enabledKey(hookId: String): String = "hook_${hookId}_enabled"

    /** Специальное значение в [BaseHook.targetPackages] — применять хук во всех процессах. */
    const val ALL_PACKAGES = "*"

    /** Пакет системного фреймворка (system_server). */
    const val SYSTEM_FRAMEWORK = "android"
}
