package com.skb8.vivotool.hooks

import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.hooks.camera.CameraFeatureConfigHook
import com.skb8.vivotool.hooks.camera.CameraHorizonEisHook
import com.skb8.vivotool.hooks.camera.CameraZeissHook
import com.skb8.vivotool.hooks.framework.VivoMultiFreeformHook
import com.skb8.vivotool.hooks.player.VivoIslandAppsHook
import com.skb8.vivotool.hooks.settings.AboutPhoneRomImageHook
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

    val all: List<BaseHook> = listOf(
        AboutPhoneRomImageHook,
        CameraFeatureConfigHook,
        CameraZeissHook,
        CameraHorizonEisHook,
        VivoNewVolumeUiHook,
        VivoMultiFreeformHook,
        VivoIslandAppsHook,
        VivoShareNoTimeoutHook,
    )
}

