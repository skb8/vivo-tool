package com.skb8.vivotool.hooks.installer

import android.app.Application
import android.content.Context
import android.os.Handler
import android.view.View
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.callMethod
import com.skb8.vivotool.core.getField
import com.skb8.vivotool.core.setField
import java.util.concurrent.atomic.AtomicBoolean

/** Vivo Package Installer 12.4.1.9.5: retain the native result pipeline and install action. */
object InstallerSkipSecurityHook : BaseHook() {
    override val id = "installer_skip_security"
    override val titleRes = R.string.hook_installer_skip_security_title
    override val descriptionRes = R.string.hook_installer_skip_security_description
    override val targetPackages = setOf("com.android.packageinstaller")
    override val enabledByDefault = false

    private const val MODEL = "com.android.packageinstaller.vivo.model."

    override fun onHook() {
        val responseClass = findClass(MODEL + "InterceptServiceResponse")
        val virusClass = findClass(MODEL + "VirusInfo")
        val interceptStrategy = findClass("L0.k")
        val virusStrategy = findClass("L0.t")
        val preScan = findClass("J0.f")
        val animator = findClass("com.android.packageinstaller.vivo.view.P")
        val apk = findClass(MODEL + "ApkBaseInfo")
        val banner = findClass("com.android.packageinstaller.vivo.view.OpenSuperGuardGuideView")
        val caller = findClass(MODEL + "CallerInfo")
        val settings = findClass(MODEL + "SettingsInfo")
        val localRisk = findClass("N0.e")

        // Validate the obfuscated animation fields before suppressing either scan.
        require(animator.getDeclaredField("b").type == Handler::class.java)
        require(animator.getDeclaredField("g").type == AtomicBoolean::class.java)
        require(animator.getDeclaredField("k").type == Long::class.javaPrimitiveType)
        require(animator.getDeclaredField("n").type == Runnable::class.java)

        val hooks = mutableListOf<Unhook>()
        try {
            hooks += requireNotNull(animator.hookBefore("q") { param ->
                val calculator = requireNotNull(param.thisObject)
                (calculator.getField("b") as Handler)
                    .removeCallbacks(calculator.getField("n") as Runnable)
                (calculator.getField("g") as AtomicBoolean).set(true)
                calculator.setField("k", 0L)
                // Native scheduler keeps its one-shot guard and waits for BOTH results.
                calculator.callMethod("o")
                param.result = null
            })
            hooks += requireNotNull(preScan.hookBefore("w", caller, apk) { param ->
                val result = requireNotNull(requireNotNull(param.thisObject).callMethod("k"))
                result.callMethod("l", 0) // Not a Tencent whitelist / automatic installation.
                param.result = null
            })
            hooks += requireNotNull(interceptStrategy.hookBefore(
                "c", Application::class.java, caller, apk, settings
            ) { param ->
                val response = responseClass.getDeclaredConstructor().newInstance().apply {
                    callMethod("setSafeLevel", 0)
                    callMethod("setServerType", 1)
                    callMethod("setServerResult", true)
                    callMethod("setNoRisk", true)
                    callMethod("setInterceptModel", 2)
                    callMethod("setAuthenticationRequired", false)
                    callMethod("setShowAuthPrompt", false)
                    callMethod("setIsShowTip", -1)
                }
                val result = requireNotNull(requireNotNull(param.thisObject).callMethod("a"))
                result.callMethod("l", response)
                param.result = null
            })
            hooks += requireNotNull(virusStrategy.hookBefore(
                "c", Application::class.java, caller, apk, settings
            ) { param ->
                val now = System.currentTimeMillis()
                val virus = virusClass.getDeclaredConstructor().newInstance().apply {
                    callMethod("setVirusScanStatus", 1)
                    callMethod("setVirusScanStartTime", now)
                    callMethod("setVirusScanEndTime", now)
                    callMethod("setVirusDes", "")
                    callMethod("setInstallationForbidden", false)
                }
                val result = requireNotNull(requireNotNull(param.thisObject).callMethod("a"))
                result.callMethod("l", virus)
                param.result = null
            })
            hooks += requireNotNull(apk.returnConstant("isForbidInvalidVpnApk", false))
            // The risk renderer also labels some 32-bit APKs as unsafe, independently of scans.
            // Leave their installation compatibility dialog to the separate local-checks hook.
            hooks += requireNotNull(localRisk.returnConstant(
                "b", false, Context::class.java, caller, apk, responseClass, virusClass
            ))
            hooks += requireNotNull(banner.hookBefore("l", Boolean::class.javaPrimitiveType) { param ->
                (param.thisObject as View).visibility = View.GONE
                param.result = null
            })
            hooks += banner.hookAllConstructorsAfter { param ->
                (param.thisObject as View).visibility = View.GONE
            }.also { require(it.isNotEmpty()) }
        } catch (t: Throwable) {
            // An incompatible app must keep the original scan pipeline intact.
            hooks.asReversed().forEach { it.unhook() }
            throw t
        }
    }
}
