package com.skb8.vivotool.hooks.installer

import android.os.Handler
import android.os.Looper
import android.view.View
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.HookParam
import com.skb8.vivotool.core.callMethod
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/** Keep native result rendering and installation; discover the scan pipeline in the current DEX. */
object InstallerSkipSecurityHook : BaseHook() {
    override val id = "installer_skip_security"
    override val titleRes = R.string.hook_installer_skip_security_title
    override val descriptionRes = R.string.hook_installer_skip_security_description
    override val targetPackages = setOf("com.android.packageinstaller")
    override val enabledByDefault = false

    override fun onHook() {
        // Resolve and validate every target before changing any part of the scan pipeline.
        val targets = InstallerDexTargets.get(classLoader).security.getOrThrow()
        val handler = Handler(Looper.getMainLooper())
        val completed = Collections.synchronizedSet(
            Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
        )
        val hooks = mutableListOf<Unhook>()
        try {
            // The coordinator passes this Runnable only after BOTH scan results arrive.
            // No private animation fields are touched.
            hooks += targets.animationProgress.before { it.result = null }
            hooks += targets.animationFinish.before { param ->
                val calculator = requireNotNull(param.thisObject)
                val completion = param.args[0] as Runnable
                if (completed.add(calculator)) {
                    if (!handler.post(completion)) {
                        completed.remove(calculator)
                        error("Cannot enqueue installer completion")
                    }
                }
                param.result = null
            }
            hooks += targets.preScan.start.before { param ->
                // Not a Tencent whitelist entry: keep the ordinary installation button.
                targets.preScan.publish(handler, requireNotNull(param.thisObject), 0)
                param.result = null
            }
            hooks += targets.intercept.start.before { param ->
                val response = targets.response.getDeclaredConstructor().newInstance().apply {
                    callMethod("setSafeLevel", 0)
                    callMethod("setServerType", 1)
                    callMethod("setServerResult", true)
                    callMethod("setNoRisk", true)
                    callMethod("setInterceptModel", 2)
                    callMethod("setAuthenticationRequired", false)
                    callMethod("setShowAuthPrompt", false)
                    callMethod("setIsShowTip", -1)
                }
                targets.intercept.publish(handler, requireNotNull(param.thisObject), response)
                param.result = null
            }
            hooks += targets.virus.start.before { param ->
                val now = System.currentTimeMillis()
                val virus = targets.virusInfo.getDeclaredConstructor().newInstance().apply {
                    callMethod("setVirusScanStatus", 1)
                    callMethod("setVirusScanStartTime", now)
                    callMethod("setVirusScanEndTime", now)
                    callMethod("setVirusDes", "")
                    callMethod("setInstallationForbidden", false)
                }
                targets.virus.publish(handler, requireNotNull(param.thisObject), virus)
                param.result = null
            }
            hooks += requireNotNull(targets.invalidVpn.replaceWithConstant(false))
            // Only the risk card: the other hook controls the 32-bit installation warning.
            hooks += requireNotNull(targets.localRisk.replaceWithConstant(false))
            hooks += targets.banner.before { param ->
                (param.thisObject as View).visibility = View.GONE
                param.result = null
            }
            hooks += targets.banner.declaringClass.hookAllConstructorsAfter { param ->
                (param.thisObject as View).visibility = View.GONE
            }.also { require(it.isNotEmpty()) }
        } catch (t: Throwable) {
            hooks.asReversed().forEach { it.unhook() }
            throw t
        }
    }

    private fun Method.before(action: (HookParam) -> Unit): Unhook =
        requireNotNull(declaringClass.hookBefore(name, *parameterTypes, action = action))

    private fun InstallerDexTargets.Channel.publish(handler: Handler, owner: Any, value: Any) {
        val liveData = requireNotNull(result.invoke(owner))
        // Like postValue, dispatch on main AFTER the current setup call has returned.
        check(handler.post { setValue.invoke(liveData, value) }) { "Cannot enqueue installer result" }
    }
}
