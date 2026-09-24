package com.skb8.vivotool.core

object Constants {
    /** Имя файла настроек, общего для UI приложения и процессов с хуками. */
    const val PREFS_NAME = "vivo_tool_prefs"

    /**
     * Отдельный файл для картинок (base64), чтобы основной файл настроек
     * оставался маленьким и быстро перечитывался внутри хуков.
     */
    const val IMAGE_PREFS_NAME = "vivo_tool_images"

    /** Тег для logcat и журнала Xposed. */
    const val TAG = "VivoTool"

    /** Ключ настройки «хук включён». */
    fun enabledKey(hookId: String): String = "hook_${hookId}_enabled"

    /** Специальное значение в [BaseHook.targetPackages] — применять хук во всех процессах. */
    const val ALL_PACKAGES = "*"

    /** Пакет системного фреймворка (system_server). */
    const val SYSTEM_FRAMEWORK = "android"

    /** Ключ настройки количества одновременных плавающих окон (2..10). */
    const val FREEFORM_LIMIT_KEY = "hook_framework_multi_freeform_limit"
    const val DEFAULT_FREEFORM_LIMIT = 2

    /** Пакет плеера Origin (виджет плеера / музыкальный остров). */
    const val ORIGIN_PLAYER_PACKAGE = "com.vivo.musicwidgetmix"

    /** Ключ настройки списка кастомных приложений для острова (Set<String>). */
    const val ISLAND_APPS_KEY = "hook_origin_player_island_apps"

    /** Пакет Vivo Share. */
    const val VIVO_SHARE_PACKAGE = "com.vivo.share"
}
