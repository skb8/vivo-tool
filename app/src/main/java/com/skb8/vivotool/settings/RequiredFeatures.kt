package com.skb8.vivotool.settings

import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.hooks.camera.CameraFeatureConfigHook
import com.skb8.vivotool.hooks.camera.CameraZeissHook

/**
 * Фичи камеры, которые твик включает за пользователя.
 *
 * Некоторым твикам мало своего кода: нужны ещё флаги из `FeatureConfig`,
 * те же, что вручную переключаются в «Изменении доступных фич». Чтобы не
 * заставлять искать их по списку, приложение выставляет их вместе с твиком
 * и убирает, когда твик выключают.
 */
object RequiredFeatures {

    private val byHook: Map<String, Map<String, Boolean>> = mapOf(
        CameraZeissHook.ID to CameraZeissHook.REQUIRED_FEATURES
    )

    fun apply(settings: AppSettings, hook: BaseHook, enabled: Boolean) {
        val features = byHook[hook.id] ?: return
        features.forEach { (feature, value) ->
            val key = CameraFeatureConfigHook.settingsKey(
                CameraFeatureConfigHook.Source.CONFIG,
                feature
            )
            if (enabled) settings.setBoolean(key, value) else settings.remove(key)
        }
    }
}
