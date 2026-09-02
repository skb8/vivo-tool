package com.skb8.vivotool.core

import com.skb8.vivotool.BuildConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Точка входа модуля. Указана в `META-INF/xposed/java_init.list`
 * и в `assets/xposed_init` (легаси-формат).
 */
class HookEntry : IXposedHookZygoteInit, IXposedHookLoadPackage {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        HookRegistry.hooks.forEach { hook ->
            if (!hook.isSupported()) return@forEach
            try {
                hook.onZygote(startupParam)
            } catch (t: Throwable) {
                XLog.e("Хук '${hook.id}' упал в initZygote", t)
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
            hookSelf(lpparam)
            return
        }

        val hooks = HookRegistry.hooksFor(lpparam.packageName)
        if (hooks.isEmpty()) return

        for (hook in hooks) {
            when {
                !hook.isSupported() ->
                    XLog.d("Хук '${hook.id}' пропущен: несовместимая версия Android")

                !HookPrefs.isEnabled(hook) ->
                    XLog.d("Хук '${hook.id}' пропущен: отключён в настройках")

                else -> hook.applyTo(lpparam)
            }
        }
    }

    /** Подмена [ModuleStatus], чтобы UI приложения знал, что модуль активен. */
    private fun hookSelf(lpparam: XC_LoadPackage.LoadPackageParam) {
        val statusClass = XposedHelpers.findClassIfExists(
            ModuleStatus::class.java.name,
            lpparam.classLoader
        ) ?: return

        XposedBridge.hookAllMethods(
            statusClass,
            "isActive",
            XC_MethodReplacement.returnConstant(true)
        )
        XposedBridge.hookAllMethods(
            statusClass,
            "xposedApiVersion",
            XC_MethodReplacement.returnConstant(XposedBridge.getXposedVersion())
        )
        XposedBridge.hookAllMethods(
            statusClass,
            "frameworkName",
            XC_MethodReplacement.returnConstant(detectFramework())
        )
    }

    private fun detectFramework(): String {
        val known = mapOf(
            "org.lsposed.lspd.core.Main" to "LSPosed",
            "org.lsposed.lspd.service.BridgeService" to "LSPosed",
            "com.elderdrivers.riru.edxp.core.Main" to "EdXposed",
            "de.robv.android.xposed.XposedInit" to "Xposed"
        )
        for ((className, name) in known) {
            if (XposedHelpers.findClassIfExists(className, null) != null) return name
        }
        return "Xposed-совместимый"
    }

    companion object {
        /** Путь к APK модуля — нужен для доступа к своим ресурсам из чужих процессов. */
        @Volatile
        var modulePath: String? = null
            private set
    }
}
