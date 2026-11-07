package com.skb8.vivotool.hooks.installer

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.view.View
import com.skb8.vivotool.core.XLog
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Only installer hooks use DexKit. Keep reflection objects, never a live native bridge. */
internal object InstallerDexTargets {
    private const val MODEL = "com.android.packageinstaller.vivo.model."
    private var cachedLoader: ClassLoader? = null
    private var cached: Targets? = null

    data class Channel(val start: Method, val result: Method, val setValue: Method)

    data class Security(
        val intercept: Channel,
        val virus: Channel,
        val preScan: Channel,
        val animationProgress: Method,
        val animationFinish: Method,
        val invalidVpn: Method,
        val localRisk: Method,
        val banner: Method,
        val response: Class<*>,
        val virusInfo: Class<*>
    )

    data class LocalChecks(
        val compatibility: Method,
        val restricted: Method,
        val wx32: Method,
        val installedVersion: Method
    )

    data class Targets(val security: Result<Security>, val localChecks: Result<LocalChecks>)

    @Synchronized
    fun get(loader: ClassLoader): Targets {
        if (cachedLoader === loader) return requireNotNull(cached)
        System.loadLibrary("dexkit")
        val started = System.nanoTime()
        val targets = DexKitBridge.create(loader, false).use { bridge ->
            val resolver = Resolver(bridge, loader)
            // A changed security implementation must not disable the independent local checks.
            Targets(runCatching { resolver.security() }, runCatching { resolver.localChecks() })
        }
        cachedLoader = loader
        cached = targets
        XLog.i("[installer_dexkit] resolved in ${(System.nanoTime() - started) / 1_000_000} ms")
        targets.security.exceptionOrNull()?.let { XLog.e("[installer_dexkit] security signatures", it) }
        targets.localChecks.exceptionOrNull()?.let { XLog.e("[installer_dexkit] local-check signatures", it) }
        return targets
    }

    private class Resolver(val bridge: DexKitBridge, val loader: ClassLoader) {
        private fun model(name: String) = Class.forName(MODEL + name, false, loader)

        private fun classUsing(label: String, vararg strings: String): Class<*> {
            val matches = bridge.findClass { matcher { usingStrings(*strings) } }
            check(matches.size == 1) {
                "$label: expected one class, found ${matches.map { it.name }}"
            }
            return matches.single().getInstance(loader)
        }

        private fun method(label: String, configure: MethodMatcher.() -> Unit): Method {
            val matches = bridge.findMethod { matcher(configure) }
            check(matches.size == 1) {
                "$label: expected one method, found ${matches.map { it.descriptor }}"
            }
            return matches.single().getMethodInstance(loader).also {
                it.isAccessible = true
                XLog.i("[installer_dexkit] $label: ${it.toGenericString()}")
            }
        }

        private fun named(type: Class<*>, name: String, result: Class<*>, vararg params: Class<*>): Method =
            type.getDeclaredMethod(name, *params).also {
                check(it.returnType == result && !Modifier.isStatic(it.modifiers))
                it.isAccessible = true
            }

        private fun channel(type: Class<*>, vararg params: Class<*>): Channel {
            val start = method("${type.name} scan entry") {
                declaredClass(type.name)
                paramTypes(*params.map { it.name }.toTypedArray())
                returnType = "void"
            }
            // The MutableLiveData subclass is itself obfuscated; derive it from the getter.
            val getters = type.declaredMethods.filter {
                !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 &&
                    it.returnType.superclass?.name == "androidx.lifecycle.LiveData"
            }
            check(getters.size == 1) { "${type.name}: ambiguous result channel: $getters" }
            val getter = getters.single().also { it.isAccessible = true }
            val liveData = requireNotNull(getter.returnType.superclass)
            val setter = method("${type.name} result setter") {
                declaredClass(liveData.name)
                paramTypes("java.lang.Object")
                returnType = "void"
                usingStrings("setValue")
            }
            return Channel(start, getter, setter)
        }

        fun security(): Security {
            val apk = model("ApkBaseInfo")
            val caller = model("CallerInfo")
            val settings = model("SettingsInfo")
            val response = model("InterceptServiceResponse")
            val virusInfo = model("VirusInfo")
            val interceptClass = classUsing("interception strategy", "InterceptServiceStrategy", "unRegisterConfigListener")
            val virusClass = classUsing("virus strategy", "VirusScanStrategy", "unbind servier error")
            val preClass = classUsing("pre-scan repository", "PreScanRepository", "https://az.appstore.vivo.com.cn/third-service/ready")
            val animation = classUsing("scan animation", "RiskScanAnimatorCalculator init", "scheduleDelayedExecution return")
            val progress = method("animation progress") {
                declaredClass(animation.name)
                paramCount = 0
                returnType = "void"
                usingStrings("mTencentWhitelistStatus not init, please wait")
            }
            val finish = method("animation completion") {
                declaredClass(animation.name)
                paramTypes("java.lang.Runnable")
                returnType = "void"
            }
            val localRisk = method("local risk predicate") {
                paramTypes(Context::class.java.name, caller.name, apk.name, response.name, virusInfo.name)
                returnType = "boolean"
                invokeMethods {
                    add { declaredClass(apk.name); name = "isForbidInvalidVpnApk"; paramCount = 0 }
                    add { declaredClass(apk.name); name = "isWxOnlySupport32"; paramCount = 0 }
                }
            }
            val banner = method("extra protection banner") {
                paramTypes("boolean")
                returnType = "void"
                usingStrings("003|041|02|202")
            }
            check(View::class.java.isAssignableFrom(banner.declaringClass))
            return Security(
                channel(interceptClass, Application::class.java, caller, apk, settings),
                channel(virusClass, Application::class.java, caller, apk, settings),
                channel(preClass, caller, apk),
                progress, finish,
                named(apk, "isForbidInvalidVpnApk", Boolean::class.javaPrimitiveType!!),
                localRisk, banner, response, virusInfo
            ).also {
                // These descriptive model APIs are not obfuscated. Validate them before hooking.
                response.getDeclaredConstructor()
                virusInfo.getDeclaredConstructor()
                listOf("setSafeLevel", "setServerType", "setInterceptModel", "setIsShowTip").forEach {
                    named(response, it, Void.TYPE, Int::class.javaPrimitiveType!!)
                }
                listOf("setServerResult", "setNoRisk", "setAuthenticationRequired", "setShowAuthPrompt").forEach {
                    named(response, it, Void.TYPE, Boolean::class.javaPrimitiveType!!)
                }
                named(virusInfo, "setVirusScanStatus", Void.TYPE, Int::class.javaPrimitiveType!!)
                named(virusInfo, "setVirusScanStartTime", Void.TYPE, Long::class.javaPrimitiveType!!)
                named(virusInfo, "setVirusScanEndTime", Void.TYPE, Long::class.javaPrimitiveType!!)
                named(virusInfo, "setVirusDes", Void.TYPE, String::class.java)
                named(virusInfo, "setInstallationForbidden", Void.TYPE, Boolean::class.javaPrimitiveType!!)
            }
        }

        fun localChecks(): LocalChecks {
            val apk = model("ApkBaseInfo")
            val compatibility = method("platform compatibility") {
                paramTypes(PackageInfo::class.java.name)
                returnType = "boolean"
                usingStrings("isSupportCurrentPlatform")
            }
            val installedVersion = method("installed version comparison") {
                paramTypes(PackageManager::class.java.name, model("OpenInstalledInfo").name)
                returnType = "boolean"
                usingStrings("getInstalledPkgInfo, installedVerCode = ")
            }
            check(Modifier.isStatic(compatibility.modifiers) && Modifier.isStatic(installedVersion.modifiers))
            return LocalChecks(
                compatibility,
                named(apk, "isRestrictInstall", Boolean::class.javaPrimitiveType!!),
                named(apk, "isWxOnlySupport32", Boolean::class.javaPrimitiveType!!),
                installedVersion
            )
        }
    }
}
