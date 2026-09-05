package com.skb8.vivotool.hooks.camera

import android.util.Range
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.XLog
import de.robv.android.xposed.XC_MethodHook

/**
 * Один и тот же предел зума во всех режимах камеры.
 *
 * Максимум, который камера действительно умеет, приложение спрашивает у
 * прошивки — `getSensorZoomRange(moduleId, isFront, cameraType)`. Режим фото
 * отдаёт этот максимум как есть, а остальные режимы его срезают: видео берёт
 * предел из `FeatureConfig.limitedMaxZoom` (на PD2425 это 10x для Master),
 * замедленная съёмка опускает до 5x, про-режим — до 2.5x и так далее.
 *
 * Хук правит уже посчитанный режимом диапазон: верхняя граница поднимается до
 * сенсорной, нижняя остаётся своей. Выше сенсорной поднимать нельзя — зум
 * переводится в область кропа сенсора, и запрос за её пределами роняет камеру
 * или не даёт режиму открыться.
 *
 * Точка одна: [HOOK_CLASS] складывает результат режима в
 * `ZoomConfig.cameraTypeZoomRange`, откуда его берут и съёмка, и UI зума.
 * Фронтальную камеру не трогаем — там предел свой и оптики нет.
 */
object CameraZoomHook : BaseHook() {

    const val ID = "camera_zoom"

    private const val HOOK_CLASS = "com.android.camera.setting.SettingFunctionHook"

    override val id: String = ID

    override val titleRes: Int = R.string.hook_camera_zoom_title

    override val descriptionRes: Int = R.string.hook_camera_zoom_description

    override val targetPackages: Set<String> = setOf(CameraFeatureConfigHook.CAMERA_PACKAGE)

    /** Твик меняет поведение камеры, поэтому включается только вручную. */
    override val enabledByDefault: Boolean = false

    override fun onHook() {
        val clazz = findClassOrNull(HOOK_CLASS)
        if (clazz == null) {
            XLog.w("[$id] класс $HOOK_CLASS не найден")
            return
        }

        val hooks = clazz.hookAllAfter("configZoomRange", ::raiseUpperBound)
        if (hooks.isEmpty()) {
            XLog.w("[$id] метод configZoomRange не найден")
        } else {
            XLog.i("[$id] configZoomRange перехвачен")
        }
    }

    private fun raiseUpperBound(param: XC_MethodHook.MethodHookParam) {
        val hook = param.thisObject ?: return
        val zoomConfig = param.args?.getOrNull(0) ?: return
        val moduleId = param.args?.getOrNull(1) as? String ?: return
        val isFront = param.args?.getOrNull(2) as? Boolean ?: return
        val cameraType = param.args?.getOrNull(3) as? String ?: return
        if (isFront) return

        @Suppress("UNCHECKED_CAST")
        val ranges = zoomConfig.fieldOrNull("cameraTypeZoomRange")
            as? MutableMap<String, Range<Float>> ?: return
        val current = ranges[cameraType] ?: return
        val lower = current.lower as? Float ?: return
        val upper = current.upper as? Float ?: return

        val sensorUpper = sensorUpper(hook, moduleId, isFront, cameraType) ?: return
        if (upper >= sensorUpper || lower >= sensorUpper) return

        try {
            ranges[cameraType] = Range(lower, sensorUpper)
        } catch (t: Throwable) {
            XLog.e("[$id] не удалось записать диапазон для $cameraType", t)
            return
        }
        XLog.i("[$id] $moduleId/$cameraType: предел $upper -> $sensorUpper")
    }

    /** Предел зума, который прошивка считает достижимым для этой камеры. */
    private fun sensorUpper(
        hook: Any,
        moduleId: String,
        isFront: Boolean,
        cameraType: String
    ): Float? {
        val manager = hook.callOrNull("getFunctionZoomManager", moduleId) ?: return null
        val settingManager = manager.fieldOrNull("mSettingManager") ?: return null
        val characteristics = settingManager.callOrNull("getSettingCharacteristics") ?: return null
        val range = characteristics.callOrNull(
            "getSensorZoomRange",
            moduleId,
            isFront,
            cameraType
        ) as? Range<*> ?: return null
        return range.upper as? Float
    }
}
