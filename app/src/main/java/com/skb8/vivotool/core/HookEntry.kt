package com.skb8.vivotool.core

import com.skb8.vivotool.BuildConfig
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Точка входа модуля LibXposed. Указана в `META-INF/xposed/java_init.list`.
 */
class HookEntry : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        XLog.setXposedInterface(this)
        modulePath = moduleApplicationInfo.sourceDir

        runCatching {
            HookPrefs.init(getRemotePreferences(Constants.PREFS_NAME))
            HookImages.init(getRemotePreferences(Constants.IMAGE_PREFS_NAME))
        }.onFailure { t ->
            XLog.e("Не удалось инициализировать RemotePreferences", t)
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val packageName = param.packageName
        val classLoader = param.classLoader

        if (packageName == BuildConfig.APPLICATION_ID) {
            hookSelf(classLoader)
            return
        }

        dispatchHooks(packageName, classLoader)
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        dispatchHooks(Constants.SYSTEM_FRAMEWORK, param.classLoader)
    }

    private fun dispatchHooks(packageName: String, classLoader: ClassLoader) {
        val hooks = HookRegistry.hooksFor(packageName)
        if (hooks.isEmpty()) return

        XLog.i("Загружены в $packageName, подходящих хуков: ${hooks.size}")

        for (hook in hooks) {
            when {
                !hook.isSupported() ->
                    XLog.d("Хук '${hook.id}' пропущен: несовместимая версия Android")

                !HookPrefs.isEnabled(hook) ->
                    XLog.d("Хук '${hook.id}' пропущен: отключён в настройках")

                else -> hook.applyTo(this, packageName, classLoader)
            }
        }
    }

    /** Подмена [ModuleStatus], чтобы UI приложения знал, что модуль активен. */
    private fun hookSelf(classLoader: ClassLoader) {
        val statusClass = runCatching {
            classLoader.loadClass(ModuleStatus::class.java.name)
        }.getOrNull() ?: return

        statusClass.declaredMethods.forEach { method ->
            when (method.name) {
                "isActive" -> hook(method).intercept { true }
                "xposedApiVersion" -> hook(method).intercept { apiVersion }
                "frameworkName" -> hook(method).intercept { frameworkName }
            }
        }
    }

    companion object {
        /** Путь к APK модуля. */
        @Volatile
        var modulePath: String? = null
            private set
    }
}
