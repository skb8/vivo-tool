package com.skb8.vivotool.hooks.player

import android.content.Context
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.HookPrefs
import com.skb8.vivotool.core.XLog
import com.skb8.vivotool.core.getField
import com.skb8.vivotool.core.getStaticField

/**
 * Хук для плеера Origin (com.vivo.musicwidgetmix):
 * расширяет белый список приложений, для которых разрешён показ
 * в динамическом острове OriginOS (Dynamic Island / SuperX-уведомление),
 * а также перехват аудиопотоков.
 */
object VivoIslandAppsHook : BaseHook() {

    const val ID = "origin_player_island_apps"

    override val id = ID
    override val targetPackages = setOf(Constants.ORIGIN_PLAYER_PACKAGE)
    override val titleRes = R.string.hook_player_island_apps_title
    override val descriptionRes = R.string.hook_player_island_apps_description
    override val enabledByDefault = false

    /**
     * 33 пакета плееров и аудиосервисов, захардкоженные по умолчанию
     * в прошивке Vivo (t3.v.g).
     */
    val DEFAULT_ISLAND_PACKAGES = setOf(
        "com.android.bbkmusic.local",
        "com.android.bbkmusic",
        "com.tencent.qqmusic",
        "com.kugou.android",
        "com.netease.cloudmusic",
        "cn.kuwo.player",
        "cmccwm.mobilemusic",
        "com.tencent.qqmusiclite",
        "com.kugou.android.elder",
        "com.kugou.android.lite",
        "com.tencent.blackkey",
        "com.luna.music",
        "cn.wenyu.bodian",
        "com.kugou.viper",
        "com.ting.mp3.android",
        "com.hiby.music",
        "com.spotify.music",
        "com.apple.android.music",
        "com.xs.fm.lite",
        "com.xs.fm",
        "com.ximalaya.ting.android",
        "com.dragon.read",
        "bubei.tingshu",
        "com.kmxs.reader",
        "fm.qingting.qtradio",
        "app.podcast.cosmos",
        "com.baidu.netdisk",
        "com.ximalaya.ting.lite",
        "cn.missevan",
        "com.yibasan.lizhifm",
        "com.shinyv.cnr",
        "com.audio.tingting",
        "com.tencent.qqmusicpad"
    )

    override fun onHook() {
        XLog.i("Применяем хук добавления сторонних приложений в Origin Island")

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
                val customApps = HookPrefs.getIslandApps()
                if (customApps.isNotEmpty()) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val list = param.thisObject?.getField("f15026d") as? MutableList<String>
                        list?.let {
                            for (pkg in customApps) {
                                if (!it.contains(pkg)) it.add(pkg)
                            }
                        }
                    } catch (t: Throwable) {
                        XLog.e("Ошибка добавления в f15026d", t)
                    }
                }
            }

            // h() заполняет f15023a (общий список перехвата аудиопотоков)
            clazz.hookAfter("h") { param ->
                val customApps = HookPrefs.getIslandApps()
                if (customApps.isNotEmpty()) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val list = param.thisObject?.getField("f15023a") as? MutableList<String>
                        list?.let {
                            for (pkg in customApps) {
                                if (!it.contains(pkg)) it.add(pkg)
                            }
                        }
                    } catch (t: Throwable) {
                        XLog.e("Ошибка добавления в f15023a", t)
                    }
                }
            }

            // f() заполняет f15024b (виджеты / шторка)
            clazz.hookAfter("f") { param ->
                val customApps = HookPrefs.getIslandApps()
                if (customApps.isNotEmpty()) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val list = param.thisObject?.getField("f15024b") as? MutableList<String>
                        list?.let {
                            for (pkg in customApps) {
                                if (!it.contains(pkg)) it.add(pkg)
                            }
                        }
                    } catch (t: Throwable) {
                        XLog.e("Ошибка добавления в f15024b", t)
                    }
                }
            }

            // Геттер c(): возвращает белый список острова
            clazz.hookAfter("c") { param ->
                val customApps = HookPrefs.getIslandApps()
                if (customApps.isNotEmpty()) {
                    val list = (param.result as? List<*>)?.filterIsInstance<String>()?.toMutableList()
                    if (list != null) {
                        for (pkg in customApps) {
                            if (!list.contains(pkg)) list.add(pkg)
                        }
                        param.result = list
                    }
                }
            }

            // Геттер d(): возвращает общий список перехвата аудиопотоков
            clazz.hookAfter("d") { param ->
                val customApps = HookPrefs.getIslandApps()
                if (customApps.isNotEmpty()) {
                    val list = (param.result as? List<*>)?.filterIsInstance<String>()?.toMutableList()
                    if (list != null) {
                        for (pkg in customApps) {
                            if (!list.contains(pkg)) list.add(pkg)
                        }
                        param.result = list
                    }
                }
            }

            // Геттер b(): возвращает список виджетов
            clazz.hookAfter("b") { param ->
                val customApps = HookPrefs.getIslandApps()
                if (customApps.isNotEmpty()) {
                    val list = (param.result as? List<*>)?.filterIsInstance<String>()?.toMutableList()
                    if (list != null) {
                        for (pkg in customApps) {
                            if (!list.contains(pkg)) list.add(pkg)
                        }
                        param.result = list
                    }
                }
            }
        } catch (t: Throwable) {
            XLog.e("Не удалось захукать t3.v (WhitelistManager)", t)
        }
    }

    private fun hookAppUtils() {
        val clazz = findClassOrNull("com.vivo.musicwidgetmix.utils.d") ?: run {
            XLog.w("[$id] Класс com.vivo.musicwidgetmix.utils.d не найден в $hookedPackage")
            return
        }
        try {
            // d.P(Context, String): проверка перехвата аудиопотока в MainApplication
            clazz.hookAfter("P", Context::class.java, String::class.java) { param ->
                val pkg = param.args[1] as? String ?: return@hookAfter
                val customApps = HookPrefs.getIslandApps()
                if (pkg in customApps) {
                    param.result = true
                }
            }

            // d.Q(Context, String): resident_music_app_white_list
            clazz.hookAfter("Q", Context::class.java, String::class.java) { param ->
                val pkg = param.args[1] as? String ?: return@hookAfter
                val customApps = HookPrefs.getIslandApps()
                if (pkg in customApps) {
                    param.result = true
                }
            }

            // d.M(Context, String): cooperation music check
            clazz.hookAfter("M", Context::class.java, String::class.java) { param ->
                val pkg = param.args[1] as? String ?: return@hookAfter
                val customApps = HookPrefs.getIslandApps()
                if (pkg in customApps) {
                    param.result = true
                }
            }
        } catch (t: Throwable) {
            XLog.e("Не удалось захукать AppUtils", t)
        }
    }

    private fun hookMainApplication() {
        val clazz = findClassOrNull("com.vivo.musicwidgetmix.MainApplication") ?: run {
            XLog.w("[$id] Класс com.vivo.musicwidgetmix.MainApplication не найден в $hookedPackage")
            return
        }
        try {
            clazz.hookAfter("onCreate") { param ->
                val customApps = HookPrefs.getIslandApps()
                if (customApps.isNotEmpty()) {
                    val appClass = param.thisObject?.javaClass ?: return@hookAfter
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val f8509g0 = appClass.getStaticField("f8509g0") as? MutableList<String>
                        f8509g0?.let {
                            for (pkg in customApps) {
                                if (!it.contains(pkg)) it.add(pkg)
                            }
                        }
                    } catch (_: Throwable) {}

                    try {
                        @Suppress("UNCHECKED_CAST")
                        val f8510h0 = appClass.getStaticField("f8510h0") as? MutableList<String>
                        f8510h0?.let {
                            for (pkg in customApps) {
                                if (!it.contains(pkg)) it.add(pkg)
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (t: Throwable) {
            XLog.e("Не удалось захукать MainApplication", t)
        }
    }
}
