package com.skb8.vivotool.hooks

import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.hooks.camera.CameraFeatureConfigHook
import com.skb8.vivotool.hooks.camera.CameraHorizonEisHook
import com.skb8.vivotool.hooks.camera.CameraZeissHook
import com.skb8.vivotool.hooks.framework.VivoMultiFreeformHook
import com.skb8.vivotool.hooks.player.VivoIslandAppsHook
import com.skb8.vivotool.hooks.settings.AboutPhoneRomImageHook
import com.skb8.vivotool.hooks.settings.AboutPhoneCustomizationHook
import com.skb8.vivotool.hooks.settings.AboutPhoneDividersHook
import com.skb8.vivotool.hooks.settings.AboutPhoneGradientHook
import com.skb8.vivotool.hooks.share.VivoShareNoTimeoutHook
import com.skb8.vivotool.hooks.systemui.VivoNewVolumeUiHook

/**
 * Список подключённых хуков — единственное место, куда нужно добавить свой хук.
 *
 * Порядок в списке = порядок применения внутри одного процесса.
 *
 * Чек-лист добавления хука:
 *  1. Создать файл в `app/src/main/java/com/skb8/vivotool/hooks/<приложение>/`.
 *  2. Унаследовать [BaseHook] (проще всего — `object`).
 *  3. Добавить объект в [all].
 *  4. Добавить целевой пакет в `app/module-scope.txt`,
 *     чтобы LSPosed предложил это приложение при выборе области действия.
 *
 * Подробности — в `docs/WRITING_HOOKS.md`.
 */
object HookModules {

    val all: List<BaseHook>
        get() {
            val list = mutableListOf(
                AboutPhoneRomImageHook,
                CameraFeatureConfigHook,
                CameraZeissHook,
                CameraHorizonEisHook,
            )
            if (com.skb8.vivotool.core.SystemProperties.releaseVersionMajor >= 16) {
                list.addAll(listOf(AboutPhoneGradientHook, AboutPhoneCustomizationHook, AboutPhoneDividersHook))
            }
            // В Android 16+ (OriginOS 6+) новый стиль громкости уже штатный по умолчанию
            if (com.skb8.vivotool.core.SystemProperties.releaseVersionMajor <= 15) {
                list.add(VivoNewVolumeUiHook)
            }
            list.addAll(
                listOf(
                    VivoMultiFreeformHook,
                    VivoIslandAppsHook,
                    VivoShareNoTimeoutHook,
                )
            )
            return list
        }
}

