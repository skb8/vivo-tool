package com.skb8.vivotool.core

/**
 * Определение того, активирован ли модуль.
 *
 * Методы этого объекта подменяются самим модулем ([HookEntry.hookSelf]) в процессе
 * приложения. Если LSPosed не активировал модуль, подмены не происходит и методы
 * возвращают значения по умолчанию.
 */
object ModuleStatus {

    @JvmStatic
    fun isActive(): Boolean = false

    @JvmStatic
    fun xposedApiVersion(): Int = -1

    @JvmStatic
    fun frameworkName(): String = "unknown"
}
