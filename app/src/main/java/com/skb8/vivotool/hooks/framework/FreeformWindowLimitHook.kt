package com.skb8.vivotool.hooks.framework

import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.HookPrefs
import com.skb8.vivotool.core.XLog
import de.robv.android.xposed.XposedHelpers

/**
 * Снимает ограничение на количество одновременно видимых плавающих окон.
 *
 * В прошивке лимит задаётся в `VivoFreeformWindowManager`:
 * ```java
 * int maxVisibleCount = VivoFreeformUtils.notSupportMultiVisibleFreeform(...) != 0 ? 1 : 2;
 * ```
 * а всё, что сверх лимита, принудительно сворачивается вызовом
 * `mAtmService.miniMizeWindowVivoFreeformMode(token, true)`.
 *
 * Хук работает на трёх уровнях, чтобы не зависеть от точных имён методов
 * в конкретной прошивке:
 *  1. `notSupportMultiVisibleFreeform` всегда сообщает о поддержке нескольких окон;
 *  2. методы автосворачивания (`...IfNeed`) не выполняются в режиме [MODE_UNLIMITED];
 *  3. сам `miniMizeWindowVivoFreeformMode` блокируется, если его вызвал
 *     автоматический лимит, а не пользователь.
 *
 * Всё, что хук нашёл и сделал, пишется в журнал LSPosed с тегом `VivoTool`.
 */
object FreeformWindowLimitHook : BaseHook() {

    const val ID = "framework_freeform_visible_limit"

    /** Ключ настройки режима. Читается на каждый вызов, перезагрузка не нужна. */
    const val KEY_MODE = "freeform_visible_limit_mode"

    /** Разрешить два окна (лимит самой прошивки). */
    const val MODE_TWO = 2

    /** Не сворачивать окна принудительно. */
    const val MODE_UNLIMITED = 0

    const val DEFAULT_MODE = MODE_UNLIMITED

    private const val MANAGER_CLASS = "com.android.server.wm.VivoFreeformWindowManager"

    private val utilsCandidates = arrayOf(
        "com.android.server.wm.VivoFreeformUtils",
        "com.android.server.wm.vivo.VivoFreeformUtils",
        "com.vivo.services.freeform.VivoFreeformUtils",
        "com.android.server.am.VivoFreeformUtils"
    )

    private val atmsCandidates = arrayOf(
        "com.android.server.wm.ActivityTaskManagerService",
        "com.android.server.am.ActivityTaskManagerService"
    )

    /** Методы, из которых прошивка сворачивает лишние окна. */
    private val autoMinimizeMethods = arrayOf(
        "minimizeCurrentVisibleFreeformTaskIfNeed",
        "minimizeGameFreeformIfNeed"
    )

    /** Сколько вызовов логировать, чтобы не засорять журнал. */
    private const val MAX_LOGGED_CALLS = 12

    private var loggedCalls = 0

    override val id: String = ID

    override val title: String = "Больше плавающих окон"

    override val description: String =
        "Снимает лимит на одновременно видимые плавающие окна"

    override val category: String = "Многооконность"

    override val targetPackages: Set<String> = setOf(Constants.SYSTEM_FRAMEWORK)

    override fun onHook() {
        XLog.i("[$id] запуск в процессе '$hookedPackage', режим=${modeName()}")

        val manager = locateClass(MANAGER_CLASS)
        val utils = locateClass(*utilsCandidates)
        val atms = locateClass(*atmsCandidates)

        manager?.let { dumpMethods(it, "minimize", "visible", "freeform") }
        utils?.let { dumpMethods(it, "multi", "visible", "support") }

        hookSupportFlag(utils)
        hookAutoMinimize(manager)
        hookForcedMinimize(atms)
    }

    /** Сообщаем прошивке, что несколько видимых плавающих окон поддерживаются. */
    private fun hookSupportFlag(utils: Class<*>?) {
        if (utils == null) return

        val hooks = utils.hookAllAfter("notSupportMultiVisibleFreeform") { param ->
            val original = param.result
            param.result = when (original) {
                is Boolean -> false
                is Int -> 0
                is Long -> 0L
                else -> original
            }
            log("notSupportMultiVisibleFreeform: $original -> ${param.result}")
        }
        XLog.i("[$id] notSupportMultiVisibleFreeform: перехвачено методов ${hooks.size}")
    }

    /**
     * Отключаем автосворачивание лишних окон в режиме [MODE_UNLIMITED].
     * В режиме [MODE_TWO] оригинальная логика работает как есть.
     */
    private fun hookAutoMinimize(manager: Class<*>?) {
        if (manager == null) return

        autoMinimizeMethods.forEach { methodName ->
            val hooks = manager.hookAllBefore(methodName) { param ->
                if (isUnlimited()) {
                    param.result = neutralResult(param.method)
                    log("$methodName пропущен (режим «без ограничения»)")
                }
            }
            XLog.i("[$id] $methodName: перехвачено методов ${hooks.size}")
        }
    }

    /**
     * Последний рубеж: блокируем принудительное сворачивание, если его вызвал
     * автоматический лимит прошивки. Сворачивание руками пользователя приходит
     * другим путём и остаётся рабочим.
     */
    private fun hookForcedMinimize(atms: Class<*>?) {
        if (atms == null) return

        val hooks = atms.hookAllBefore("miniMizeWindowVivoFreeformMode") { param ->
            val caller = autoMinimizeCaller()
            if (caller != null) {
                log("miniMizeWindowVivoFreeformMode вызван из $caller")
                if (isUnlimited()) {
                    param.result = neutralResult(param.method)
                    log("miniMizeWindowVivoFreeformMode заблокирован")
                }
            } else {
                log("miniMizeWindowVivoFreeformMode: ручной вызов, не трогаем\n" + shortStack())
            }
        }
        XLog.i("[$id] miniMizeWindowVivoFreeformMode: перехвачено методов ${hooks.size}")
    }

    /**
     * Имя метода прошивки, из которого пришёл вызов сворачивания,
     * если это автоматический лимит, а не действие пользователя.
     */
    private fun autoMinimizeCaller(): String? =
        Throwable().stackTrace.firstOrNull { frame ->
            frame.className.contains("VivoFreeform") && frame.methodName.contains("IfNeed")
        }?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" }

    private fun shortStack(): String =
        Throwable().stackTrace
            .drop(2)
            .take(12)
            .joinToString("\n") { "    at ${it.className}.${it.methodName}" }

    /** Ищем класс и в загрузчике процесса, и в загрузчике загрузки системы. */
    private fun locateClass(vararg classNames: String): Class<*>? {
        classNames.forEach { className ->
            findClassOrNull(className)?.let {
                XLog.i("[$id] найден $className")
                return it
            }
            XposedHelpers.findClassIfExists(className, null)?.let {
                XLog.i("[$id] найден $className (boot classloader)")
                return it
            }
        }
        XLog.w("[$id] не найдено ни одного из классов: ${classNames.joinToString()}")
        return null
    }

    /** Печатаем реальные сигнатуры — так видно, как метод называется в прошивке. */
    private fun dumpMethods(clazz: Class<*>, vararg keywords: String) {
        val methods = try {
            clazz.declaredMethods
        } catch (t: Throwable) {
            XLog.e("[$id] не удалось прочитать методы ${clazz.name}", t)
            return
        }

        val matched = methods
            .filter { method -> keywords.any { method.name.contains(it, ignoreCase = true) } }
            .sortedBy { it.name }
            .joinToString("\n") { method ->
                "    ${method.returnType.simpleName} ${method.name}(" +
                    method.parameterTypes.joinToString { it.simpleName } + ")"
            }

        XLog.i(
            "[$id] методы ${clazz.name} по ключевым словам ${keywords.joinToString()}:\n" +
                matched.ifBlank { "    (ничего не найдено)" }
        )
    }

    private fun log(message: String) {
        if (loggedCalls >= MAX_LOGGED_CALLS) return
        loggedCalls++
        XLog.i("[$id] $message")
    }

    private fun isUnlimited(): Boolean = readMode() == MODE_UNLIMITED

    private fun readMode(): Int = HookPrefs.getInt(KEY_MODE, DEFAULT_MODE)

    private fun modeName(): String =
        if (readMode() == MODE_UNLIMITED) "без ограничения" else "два окна"
}
