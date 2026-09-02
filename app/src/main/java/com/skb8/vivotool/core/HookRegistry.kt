package com.skb8.vivotool.core

import com.skb8.vivotool.hooks.HookModules

/**
 * Реестр всех хуков модуля.
 *
 * Чтобы подключить новый хук, достаточно добавить его в [HookModules.all]
 * (см. `hooks/HookModules.kt`) — реестр и UI подхватят его автоматически.
 */
object HookRegistry {

    /** Все известные хуки. */
    val hooks: List<BaseHook> get() = HookModules.all

    /** Хуки, которые нужно применить к указанному пакету. */
    fun hooksFor(packageName: String): List<BaseHook> =
        hooks.filter { it.matches(packageName) }

    /** Хуки, сгруппированные по категории — для списка в приложении. */
    fun byCategory(): Map<String, List<BaseHook>> =
        hooks.groupBy { it.category }.toSortedMap()

    /** Все пакеты, упомянутые в хуках (кроме универсального `*`). */
    fun declaredPackages(): Set<String> =
        hooks.flatMap { it.targetPackages }
            .filter { it != Constants.ALL_PACKAGES }
            .toSet()

    /**
     * Пакеты, для которых есть хуки, но которые забыли добавить
     * в `app/module-scope.txt` — LSPosed не предложит их в списке выбора.
     */
    fun packagesMissingFromScope(scopePackages: Collection<String>): Set<String> =
        declaredPackages() - scopePackages.toSet()

    init {
        val duplicates = hooks.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Дублирующиеся id хуков: $duplicates" }
    }
}
