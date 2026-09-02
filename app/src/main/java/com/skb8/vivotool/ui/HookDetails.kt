package com.skb8.vivotool.ui

import com.skb8.vivotool.hooks.framework.FreeformWindowLimitHook
import com.skb8.vivotool.hooks.settings.AboutPhoneRomImageHook

/**
 * Твики, у которых есть свой экран настроек.
 *
 * Добавляя такой твик, допишите его id сюда и добавьте ветку в
 * `AppRoot` → `HookDetailScreen`.
 */
object HookDetails {

    private val withDetails = setOf(
        AboutPhoneRomImageHook.ID,
        FreeformWindowLimitHook.ID
    )

    fun hasDetails(hookId: String): Boolean = hookId in withDetails
}
