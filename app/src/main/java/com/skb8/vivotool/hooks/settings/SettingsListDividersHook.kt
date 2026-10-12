package com.skb8.vivotool.hooks.settings

import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.SystemProperties
import com.skb8.vivotool.core.XLog

/** Общие разделители пунктов списков во всём процессе com.android.settings. */
object SettingsListDividersHook : BaseHook() {
    const val ID = "settings_hide_list_dividers"
    private const val LIST_BASE = "com.originui.widget.listitem.VListBase"
    private const val HOLDER = "androidx.preference.PreferenceViewHolder"

    override val id = ID
    override val titleRes = R.string.hook_settings_dividers_title
    override val descriptionRes = R.string.hook_settings_dividers_description
    override val targetPackages = setOf("com.android.settings")
    override val enabledByDefault = false
    override fun isSupported(sdkInt: Int) = super.isSupported(sdkInt) && SystemProperties.releaseVersionMajor >= 16

    override fun onHook() {
        val listBase = findClassOrNull(LIST_BASE)
        if (listBase != null) {
            // Родной VListContent рисует линию сам, без отдельного View-divider.
            // Сохраняем выполнение метода: он также делегирует настройку для других тем.
            listBase.hookBefore("showDividerLine",
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType) { param ->
                param.setArg(0, false)
            }
            // Конструктор выставляет mShowDividerLine напрямую; отключаем исходное
            // состояние до первого показа. Наследники также проходят этот путь.
            listBase.hookAllConstructorsAfter { param ->
                param.thisObject?.callOrNull("showDividerLine", false, true)
            }
            XLog.i("[$id] линии пунктов OriginUI отключены")
        } else {
            XLog.w("[$id] $LIST_BASE не найден")
        }

        val holder = findClassOrNull(HOLDER)
        if (holder != null) {
            // DividerDecoration читает эти флаги и для рисования, и для отступов.
            // Возвращаем false при каждом чтении, включая переиспользованные строки.
            for (method in listOf("isDividerAllowedAbove", "isDividerAllowedBelow", "isCardDividerShow")) {
                holder.returnConstant(method, false)
            }
            XLog.i("[$id] разделители списков Preference отключены")
        } else {
            XLog.w("[$id] $HOLDER не найден")
        }
    }
}
