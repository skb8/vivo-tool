package com.skb8.vivotool.hooks.settings

import android.view.View
import android.view.ViewGroup

internal object AboutPhoneViews {
    const val PACKAGE = "com.android.settings"
    const val CONTROLLERS = "com.vivo.settings.aboutphone.deviceinfo."
    const val FRAGMENT = "${CONTROLLERS}VivoAboutPhoneFragment"
    const val BASE_CONTROLLER = "${CONTROLLERS}VivoDeviceInfoBaseController"
    const val ROW_CLASS = "${CONTROLLERS}VivoDeviceInfoLayout"

    fun find(root: View, name: String): View? {
        val id = root.resources.getIdentifier(name, "id", PACKAGE)
        return if (id == 0) null else root.findViewById(id)
    }

    fun children(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) yieldAll(children(root.getChildAt(i)))
        }
    }
}
