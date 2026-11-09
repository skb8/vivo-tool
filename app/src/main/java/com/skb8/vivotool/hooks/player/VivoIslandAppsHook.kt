package com.skb8.vivotool.hooks.player

import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.XLog

/**
 * Хук для плеера Origin (com.vivo.musicwidgetmix):
 * снимает ограничение белого списка приложений для динамического острова OriginOS.
 * Точки перехвата находятся через DexKit по назначению списков и проверок.
 * Карточку и управление воспроизведением создаёт штатный код плеера.
 */
object VivoIslandAppsHook : BaseHook() {

    const val ID = "origin_player_island_apps"

    override val id = ID
    override val targetPackages = setOf(Constants.ORIGIN_PLAYER_PACKAGE)
    override val titleRes = R.string.hook_player_island_apps_title
    override val descriptionRes = R.string.hook_player_island_apps_description
    override val enabledByDefault = false

    private val IGNORED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.vivo.musicwidgetmix",
        "com.vivo.upslide",
        "com.android.server.telecom",
        "com.android.phone",
        "com.vivo.incallui",
        "com.android.incallui",
        "com.google.android.dialer"
    )

    private fun isIgnored(pkg: String): Boolean {
        return pkg in IGNORED_PACKAGES || pkg.startsWith("com.android.internal")
    }

    /**
     * Список-обёртка, чей метод contains() возвращает true для любых медиа-пакетов,
     * снимая проверку белого списка в IslandNotificationManager и WhitelistManager.
     */
    class PermissiveStringList(initial: Collection<String> = emptyList()) : ArrayList<String>(initial) {
        override fun contains(element: String): Boolean {
            if (element.isEmpty() || isIgnored(element)) return false
            return true
        }
    }

    override fun onHook() {
        // Resolve all roles before installing anything. Ambiguous or changed signatures
        // leave the original application behavior intact and are reported by BaseHook.
        val targets = PlayerIslandDexTargets.get(classLoader)
        val hooks = mutableListOf<Unhook>()
        try {
            targets.predicates.forEach { method ->
                hooks += requireNotNull(method.declaringClass.hookAfter(
                    method.name, *method.parameterTypes
                ) { param ->
                    val pkg = param.args[1] as? String ?: return@hookAfter
                    if (pkg.isNotEmpty() && !isIgnored(pkg)) param.result = true
                })
            }
            targets.lists.forEach { method ->
                hooks += requireNotNull(method.declaringClass.hookAfter(
                    method.name, *method.parameterTypes
                ) { param ->
                    @Suppress("UNCHECKED_CAST")
                    val list = param.result as? List<String> ?: return@hookAfter
                    if (list !is PermissiveStringList) param.result = PermissiveStringList(list)
                })
            }
        } catch (t: Throwable) {
            hooks.asReversed().forEach { it.unhook() }
            throw t
        }
        targets.callers.forEach { caller ->
            runCatching {
                if (xposed?.deoptimize(caller) != true) {
                    XLog.w("[$id] Could not deoptimize $caller")
                }
            }.onFailure { XLog.e("[$id] Deoptimize $caller", it) }
        }
        XLog.i("[$id] DexKit: audio admission and island/player whitelists hooked")
    }
}
