package com.skb8.vivotool.hooks.settings

import android.view.View
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.SystemProperties

/** Только разделители экрана «О устройстве», включая строки, добавленные редактором. */
object AboutPhoneDividersHook : BaseHook() {
    const val ID = "settings_about_phone_hide_dividers"
    override val id = ID
    override val titleRes = R.string.hook_about_dividers_title
    override val descriptionRes = R.string.hook_about_dividers_description
    override val targetPackages = setOf(AboutPhoneViews.PACKAGE)
    override val enabledByDefault = false
    override fun isSupported(sdkInt: Int) = super.isSupported(sdkInt) && SystemProperties.releaseVersionMajor >= 16

    override fun onHook() {
        findClassOrNull(AboutPhoneViews.ROW_CLASS)?.apply {
            hookAllConstructorsAfter { param -> (param.thisObject as? View)?.let(::hideDividers) }
            hookBefore("setDividerVisibility", Boolean::class.javaPrimitiveType) { it.setArg(0, false) }
        }
        findClassOrNull(AboutPhoneViews.FRAGMENT)?.apply {
            hookAllAfter("onCreateView") { param -> (param.result as? View)?.let(::hideDividers) }
            hookAllAfter("onConfigurationChanged") { param ->
                (param.thisObject?.fieldOrNull("mRootView") as? View)?.let(::hideDividers)
            }
        }
    }

    private fun hideDividers(root: View) {
        AboutPhoneViews.children(root).forEach { view ->
            val name = if (view.id == View.NO_ID) "" else runCatching {
                view.resources.getResourceEntryName(view.id)
            }.getOrDefault("")
            if (view.javaClass.name == "com.originui.widget.components.divider.VDivider" ||
                name == "divider" || name == "user_advisor_plan_divider") {
                view.visibility = View.GONE
            }
        }
    }
}
