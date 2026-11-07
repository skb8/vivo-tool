package com.skb8.vivotool.hooks.installer

import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook

/** Client-side gates only. PackageInstaller sessions still receive the real system result. */
object InstallerLocalChecksHook : BaseHook() {
    override val id = "installer_skip_local_checks"
    override val titleRes = R.string.hook_installer_local_checks_title
    override val descriptionRes = R.string.hook_installer_local_checks_description
    override val targetPackages = setOf("com.android.packageinstaller")
    override val enabledByDefault = false

    override fun onHook() {
        val targets = InstallerDexTargets.get(classLoader).localChecks.getOrThrow()
        val hooks = mutableListOf<Unhook>()
        try {
            hooks += requireNotNull(targets.compatibility.replaceWithConstant(true))
            hooks += requireNotNull(targets.restricted.replaceWithConstant(false))
            hooks += requireNotNull(targets.wx32.replaceWithConstant(false))
            hooks += requireNotNull(targets.installedVersion.replaceWithConstant(false))
        } catch (t: Throwable) {
            hooks.asReversed().forEach { it.unhook() }
            throw t
        }
    }
}
