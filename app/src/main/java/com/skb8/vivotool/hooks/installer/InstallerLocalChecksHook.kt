package com.skb8.vivotool.hooks.installer

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
        val apk = findClass("com.android.packageinstaller.vivo.model.ApkBaseInfo")
        // Runtime names from smali; JADX renames these to AbstractC0272e / AbstractC0277f1.
        val compatibility = findClass("Q0.e")
        val installedVersion = findClass("Q0.f1")
        val openInstalledInfo = findClass("com.android.packageinstaller.vivo.model.OpenInstalledInfo")
        val hooks = mutableListOf<Unhook>()
        try {
            // vivo.install.compatibility metadata: Android, ROM, region, device and not_install.
            hooks += requireNotNull(compatibility.returnConstant("e", true, PackageInfo::class.java))
            hooks += requireNotNull(apk.returnConstant("isRestrictInstall", false))
            hooks += requireNotNull(apk.returnConstant("isWxOnlySupport32", false))
            // Do not replace installation with the "open installed version" dialog on a downgrade.
            hooks += requireNotNull(installedVersion.returnConstant(
                "b", false, PackageManager::class.java, openInstalledInfo
            ))
        } catch (t: Throwable) {
            hooks.asReversed().forEach { it.unhook() }
            throw t
        }
    }
}
