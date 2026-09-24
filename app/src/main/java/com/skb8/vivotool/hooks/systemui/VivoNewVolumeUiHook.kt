package com.skb8.vivotool.hooks.systemui

import android.content.Context
import android.os.Build
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.XLog
import com.skb8.vivotool.core.callMethod
import com.skb8.vivotool.core.getField
import com.skb8.vivotool.core.setField
import java.util.Collections
import java.util.WeakHashMap

/**
 * Включение нового стиля регулировки громкости OriginOS 5.
 *
 * В плагине `com.vivo.systemuiplugin` содержатся две реализации диалога громкости:
 *  - старый `VivoVolumeOldImpl` (классический вертикальный слайдер OriginOS 3/4);
 *  - новый `VivoVolumeNewImpl` (адаптивный слайдер OriginOS 5: пружинные физические
 *    анимации, двухэтапное сжатие в тонкую 6dp-полоску у края экрана с быстрым
 *    разворачиванием по касанию, упругая реакция на нажатия клавиш громкости).
 *
 * Выбор между ними происходит в `VivoVolumeDialogImpl.onCreate`:
 * ```java
 * boolean zIsSupportNewVolumeUI = SystemPropertiesFacade.isSupportNewVolumeUI();
 * if (zIsSupportNewVolumeUI) {
 *     this.mImpl = new VivoVolumeNewImpl();
 * } else {
 *     this.mImpl = new VivoVolumeOldImpl();
 * }
 * ```
 *
 * Метод `SystemPropertiesFacade.isSupportNewVolumeUI()` в коде прошивки возвращает `false`.
 * Хук подменяет его на `true`, активируя `VivoVolumeNewImpl`.
 *
 * Также предусмотрен fallback: если в конкретной версии метод `isSupportNewVolumeUI`
 * был заинлайнен компилятором, хук перехватывает `VivoVolumeDialogImpl.onCreate`
 * и принудительно заменяет `mImpl` на экземпляр `VivoVolumeNewImpl`.
 */
object VivoNewVolumeUiHook : BaseHook() {

    const val ID = "systemui_new_volume_ui"

    private const val FACADE_CLASS = "com.vivo.systemuiplugin.common.utils.SystemPropertiesFacade"
    private const val DIALOG_IMPL_CLASS = "com.vivo.systemuiplugin.systemui.volume.VivoVolumeDialogImpl"
    private const val NEW_IMPL_CLASS = "com.vivo.systemuiplugin.systemui.volume.VivoVolumeNewImpl"

    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val SYSTEM_UI_PLUGIN_PACKAGE = "com.vivo.systemuiplugin"

    override val id: String = ID
    override val titleRes: Int = R.string.hook_systemui_new_volume_ui_title
    override val descriptionRes: Int = R.string.hook_systemui_new_volume_ui_description

    override val targetPackages: Set<String> = setOf(
        SYSTEM_UI_PACKAGE,
        SYSTEM_UI_PLUGIN_PACKAGE
    )

    override val enabledByDefault: Boolean = false

    private val hookedLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )

    override fun onHook() {
        hookClassLoader(classLoader)

        // 1. Перехватываем создание любых загрузчиков классов в процессе (PathClassLoader, DexClassLoader)
        findClassOrNull("dalvik.system.BaseDexClassLoader")?.hookAllConstructorsAfter { param ->
            val loader = param.thisObject as? ClassLoader ?: return@hookAllConstructorsAfter
            hookClassLoader(loader)
        }

        // 2. Перехватываем динамическую загрузку целевых классов через ClassLoader.loadClass
        findClassOrNull("java.lang.ClassLoader")?.hookAfter(
            "loadClass",
            String::class.java,
            Boolean::class.javaPrimitiveType
        ) { param ->
            val name = param.args[0] as? String ?: return@hookAfter
            if (name == FACADE_CLASS || name == DIALOG_IMPL_CLASS) {
                val loader = param.thisObject as? ClassLoader ?: return@hookAfter
                hookClassLoader(loader)
            }
        }

        // 3. Плагин SystemUI может загружаться через LoadedApk или ContextImpl
        findClassOrNull("android.app.LoadedApk")?.hookAfter("getClassLoader") { hookParam ->
            val loadedApk = hookParam.thisObject ?: return@hookAfter
            val pkg = loadedApk.getField("mPackageName") as? String
            if (pkg == SYSTEM_UI_PLUGIN_PACKAGE || pkg == SYSTEM_UI_PACKAGE) {
                val cl = hookParam.result as? ClassLoader ?: return@hookAfter
                hookClassLoader(cl)
            }
        }

        findClassOrNull("android.app.ContextImpl")?.let { contextImpl ->
            contextImpl.hookAllAfter("createPackageContext") { param ->
                checkPackageContext(param.args, param.result)
            }
            contextImpl.hookAllAfter("createPackageContextAsUser") { param ->
                checkPackageContext(param.args, param.result)
            }
        }
    }

    private fun checkPackageContext(args: Array<Any?>?, result: Any?) {
        val pkgName = args?.firstNotNullOfOrNull { it as? String } ?: return
        if (pkgName == SYSTEM_UI_PLUGIN_PACKAGE) {
            val context = result as? Context ?: return
            hookClassLoader(context.classLoader)
        }
    }

    private fun hookClassLoader(loader: ClassLoader) {
        if (!hookedLoaders.add(loader)) return

        var hookedAny = false

        // 1. Основной путь: подмена флага SystemPropertiesFacade.isSupportNewVolumeUI() -> true
        val facadeClass = findClassOrNull(FACADE_CLASS, loader)
        if (facadeClass != null) {
            val unhook = facadeClass.returnConstant("isSupportNewVolumeUI", true)
            if (unhook != null) {
                hookedAny = true
                XLog.i("[$id] SystemPropertiesFacade.isSupportNewVolumeUI() подменён на true")
            }
        }

        // 2. Fallback и логирование через VivoVolumeDialogImpl.onCreate и init
        val dialogClass = findClassOrNull(DIALOG_IMPL_CLASS, loader)
        val newImplClass = findClassOrNull(NEW_IMPL_CLASS, loader)

        if (dialogClass != null) {
            dialogClass.hookAfter("onCreate", Context::class.java, Context::class.java) { param ->
                val dialog = param.thisObject ?: return@hookAfter
                val currentImpl = dialog.getField("mImpl")

                if (currentImpl != null && currentImpl.javaClass.name.contains("VivoVolumeOldImpl")) {
                    XLog.w("[$id] mImpl остался VivoVolumeOldImpl, заменяем на VivoVolumeNewImpl")
                    if (newImplClass != null) {
                        try {
                            val newImpl = newImplClass.getDeclaredConstructor().newInstance()
                            val hostContext = param.args[0] as Context
                            val pluginContext = param.args[1] as Context
                            newImpl.callMethod("onCreate", hostContext, pluginContext)
                            dialog.setField("mImpl", newImpl)
                            XLog.i("[$id] mImpl успешно заменён на VivoVolumeNewImpl в onCreate")
                        } catch (t: Throwable) {
                            XLog.e("[$id] Не удалось создать VivoVolumeNewImpl", t)
                        }
                    }
                } else {
                    XLog.i("[$id] Активна реализация: ${currentImpl?.javaClass?.name}")
                }
            }

            // Fallback: перехват init() если onCreate отработал до применения хука
            dialogClass.hookAllBefore("init") { param ->
                val dialog = param.thisObject ?: return@hookAllBefore
                val currentImpl = dialog.getField("mImpl")
                if (currentImpl != null && currentImpl.javaClass.name.contains("VivoVolumeOldImpl") && newImplClass != null) {
                    try {
                        val contextUtilsClass = findClassOrNull("com.vivo.systemuiplugin.common.utils.ContextUtils", loader)
                        val instance = contextUtilsClass?.callOrNull("getInstance")
                        val hostContext = instance?.callOrNull("getSystemUIContext") as? Context
                        val pluginContext = instance?.callOrNull("getSysuiPluginContext") as? Context
                        if (hostContext != null && pluginContext != null) {
                            val newImpl = newImplClass.getDeclaredConstructor().newInstance()
                            newImpl.callMethod("onCreate", hostContext, pluginContext)
                            dialog.setField("mImpl", newImpl)
                            XLog.i("[$id] mImpl успешно заменён на VivoVolumeNewImpl перед init")
                        }
                    } catch (t: Throwable) {
                        XLog.e("[$id] Не удалось заменить mImpl перед init", t)
                    }
                }
            }
            hookedAny = true
        }

        if (!hookedAny) {
            XLog.d("[$id] Классы громкости не найдены в ClassLoader $loader")
        }
    }
}
