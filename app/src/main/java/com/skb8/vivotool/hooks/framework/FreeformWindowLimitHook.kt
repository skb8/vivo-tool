package com.skb8.vivotool.hooks.framework

import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.HookPrefs
import com.skb8.vivotool.core.XLog
import de.robv.android.xposed.XC_MethodHook

/**
 * Снимает ограничение на количество одновременно видимых плавающих окон.
 *
 * В прошивке лимит задаётся в `VivoFreeformWindowManager`:
 * ```java
 * int maxVisibleCount = VivoFreeformUtils.notSupportMultiVisibleFreeform(...) != 0 ? 1 : 2;
 * ```
 * и всё, что сверх лимита, принудительно сворачивается в мини-окно
 * в `minimizeCurrentVisibleFreeformTaskIfNeed`.
 *
 * Хук работает на двух уровнях:
 *  - [MODE_TWO] — `notSupportMultiVisibleFreeform` всегда сообщает о поддержке
 *    нескольких окон, поэтому лимит прошивки становится 2 вместо 1;
 *  - [MODE_UNLIMITED] — дополнительно отключается принудительное сворачивание,
 *    так что на экране остаётся столько окон, сколько открыл пользователь
 *    (общий лимит списка задач в прошивке — 12 — остаётся в силе).
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

    private val utilsClassNames = arrayOf(
        "com.android.server.wm.VivoFreeformUtils",
        "com.android.server.wm.vivo.VivoFreeformUtils",
        "com.vivo.services.freeform.VivoFreeformUtils",
        "com.android.server.am.VivoFreeformUtils"
    )

    override val id: String = ID

    override val title: String = "Больше плавающих окон"

    override val description: String =
        "Снимает лимит на одновременно видимые плавающие окна"

    override val category: String = "Многооконность"

    override val targetPackages: Set<String> = setOf(Constants.SYSTEM_FRAMEWORK)

    override fun onHook() {
        hookMultiVisibleSupport()
        hookForcedMinimize()
    }

    /** Сообщаем прошивке, что несколько видимых плавающих окон поддерживаются. */
    private fun hookMultiVisibleSupport() {
        val utils = findFirstClass(*utilsClassNames)
        if (utils == null) {
            XLog.w("[$id] VivoFreeformUtils не найден — пробуем только отключить сворачивание")
            return
        }

        val hooks = utils.hookAllAfter("notSupportMultiVisibleFreeform") { param ->
            param.result = when (param.result) {
                is Boolean -> false
                is Int -> 0
                is Long -> 0L
                else -> param.result
            }
        }
        if (hooks.isEmpty()) {
            XLog.w("[$id] метод notSupportMultiVisibleFreeform не найден в ${utils.name}")
        }
    }

    /**
     * Отключаем принудительное сворачивание лишних окон, когда выбран режим
     * [MODE_UNLIMITED]. В режиме [MODE_TWO] оригинальная логика работает как есть.
     */
    private fun hookForcedMinimize() {
        val manager = findClassOrNull(MANAGER_CLASS) ?: run {
            XLog.w("[$id] $MANAGER_CLASS не найден, хук неприменим к этой прошивке")
            return
        }

        val skipIfUnlimited: (XC_MethodHook.MethodHookParam) -> Unit = { param ->
            if (isUnlimited()) {
                param.result = neutralResult(param.method)
            }
        }

        val minimizeHooks = manager.hookAllBefore(
            "minimizeCurrentVisibleFreeformTaskIfNeed",
            skipIfUnlimited
        )
        if (minimizeHooks.isEmpty()) {
            XLog.w("[$id] minimizeCurrentVisibleFreeformTaskIfNeed не найден в ${manager.name}")
        }

        // Такое же ограничение для игровых плавающих окон.
        manager.hookAllBefore("minimizeGameFreeformIfNeed", skipIfUnlimited)
    }

    private fun isUnlimited(): Boolean =
        HookPrefs.getInt(KEY_MODE, DEFAULT_MODE) == MODE_UNLIMITED
}
