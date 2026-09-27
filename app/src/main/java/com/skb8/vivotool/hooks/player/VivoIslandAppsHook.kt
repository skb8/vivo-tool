package com.skb8.vivotool.hooks.player

import android.content.Context
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.XLog
import com.skb8.vivotool.core.getField
import com.skb8.vivotool.core.getStaticField
import com.skb8.vivotool.core.setField
import com.skb8.vivotool.core.setStaticField

/**
 * Хук для плеера Origin (com.vivo.musicwidgetmix):
 * снимает ограничение белого списка приложений для динамического острова OriginOS.
 * Любое установленное приложение, воспроизводящее звук через MediaSession/AudioTrack,
 * автоматически отображается в Острове с обложкой, названием трека и кнопками управления.
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
        XLog.i("[$id] Включаем универсальную поддержку всех плееров в Origin Island")

        hookWhitelistManager()
        hookAppUtils()
        hookMainApplication()
    }

    private fun hookWhitelistManager() {
        val clazz = findClassOrNull("t3.v") ?: run {
            XLog.w("[$id] Класс t3.v не найден в $hookedPackage")
            return
        }
        try {
            // g() заполняет f15026d (белый список для Острова)
            clazz.hookAfter("g") { param ->
                val thisObj = param.thisObject ?: return@hookAfter
                try {
                    @Suppress("UNCHECKED_CAST")
                    val list = thisObj.getField("f15026d") as? List<String> ?: emptyList()
                    thisObj.setField("f15026d", PermissiveStringList(list))
                } catch (t: Throwable) {
                    XLog.e("[$id] Ошибка подмены f15026d", t)
                }
            }

            // h() заполняет f15023a (общий список перехвата аудиопотоков)
            clazz.hookAfter("h") { param ->
                val thisObj = param.thisObject ?: return@hookAfter
                try {
                    @Suppress("UNCHECKED_CAST")
                    val list = thisObj.getField("f15023a") as? List<String> ?: emptyList()
                    thisObj.setField("f15023a", PermissiveStringList(list))
                } catch (t: Throwable) {
                    XLog.e("[$id] Ошибка подмены f15023a", t)
                }
            }

            // f() заполняет f15024b (виджеты / шторка)
            clazz.hookAfter("f") { param ->
                val thisObj = param.thisObject ?: return@hookAfter
                try {
                    @Suppress("UNCHECKED_CAST")
                    val list = thisObj.getField("f15024b") as? List<String> ?: emptyList()
                    thisObj.setField("f15024b", PermissiveStringList(list))
                } catch (t: Throwable) {
                    XLog.e("[$id] Ошибка подмены f15024b", t)
                }
            }

            // Геттер c(): возвращает белый список острова
            clazz.hookAfter("c") { param ->
                @Suppress("UNCHECKED_CAST")
                val list = param.result as? List<String> ?: emptyList()
                param.result = PermissiveStringList(list)
            }

            // Геттер d(): возвращает общий список перехвата аудиопотоков
            clazz.hookAfter("d") { param ->
                @Suppress("UNCHECKED_CAST")
                val list = param.result as? List<String> ?: emptyList()
                param.result = PermissiveStringList(list)
            }

            // Геттер b(): возвращает список виджетов
            clazz.hookAfter("b") { param ->
                @Suppress("UNCHECKED_CAST")
                val list = param.result as? List<String> ?: emptyList()
                param.result = PermissiveStringList(list)
            }
        } catch (t: Throwable) {
            XLog.e("[$id] Не удалось захукать t3.v (WhitelistManager)", t)
        }
    }

    private fun hookAppUtils() {
        val clazz = findClassOrNull("com.vivo.musicwidgetmix.utils.d") ?: run {
            XLog.w("[$id] Класс com.vivo.musicwidgetmix.utils.d не найден в $hookedPackage")
            return
        }
        try {
            // d.P(Context, String): проверка перехвата аудиопотока в MainApplication и ResidentManager
            clazz.hookAfter("P", Context::class.java, String::class.java) { param ->
                val pkg = param.args[1] as? String ?: return@hookAfter
                if (pkg.isNotEmpty() && !isIgnored(pkg)) {
                    param.result = true
                }
            }

            // d.Q(Context, String): resident_music_app_white_list
            clazz.hookAfter("Q", Context::class.java, String::class.java) { param ->
                val pkg = param.args[1] as? String ?: return@hookAfter
                if (pkg.isNotEmpty() && !isIgnored(pkg)) {
                    param.result = true
                }
            }

            // d.A(Context): белый список плееров на экране блокировки
            clazz.hookAfter("A", Context::class.java) { param ->
                @Suppress("UNCHECKED_CAST")
                val list = param.result as? List<String> ?: emptyList()
                param.result = PermissiveStringList(list)
            }
        } catch (t: Throwable) {
            XLog.e("[$id] Не удалось захукать AppUtils", t)
        }
    }

    private fun hookMainApplication() {
        val clazz = findClassOrNull("com.vivo.musicwidgetmix.MainApplication") ?: run {
            XLog.w("[$id] Класс com.vivo.musicwidgetmix.MainApplication не найден в $hookedPackage")
            return
        }
        try {
            clazz.hookAfter("onCreate") { param ->
                val appClass = param.thisObject?.javaClass ?: return@hookAfter
                try {
                    @Suppress("UNCHECKED_CAST")
                    val f8509g0 = appClass.getStaticField("f8509g0") as? List<String> ?: emptyList()
                    appClass.setStaticField("f8509g0", PermissiveStringList(f8509g0))
                } catch (_: Throwable) {}

                try {
                    @Suppress("UNCHECKED_CAST")
                    val f8510h0 = appClass.getStaticField("f8510h0") as? List<String> ?: emptyList()
                    appClass.setStaticField("f8510h0", PermissiveStringList(f8510h0))
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            XLog.e("[$id] Не удалось захукать MainApplication", t)
        }
    }
}
