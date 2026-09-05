package com.skb8.vivotool.hooks.camera

import android.util.Range
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.XLog
import de.robv.android.xposed.XC_MethodHook

/**
 * Один и тот же предел зума во всех режимах камеры.
 *
 * Предел приходит из `getSensorZoomRange(moduleId, isFront, cameraType)`:
 * по moduleId прошивка выбирает «модель» камеры (`photo` → NORMAL,
 * `video` → Video), по модели и типу камеры — конкретный camera id, и уже у
 * него спрашивает `SCALER_AVAILABLE_MAX_DIGITAL_ZOOM`. Поэтому у видео предел
 * свой и он ниже, чем у фото, даже для той же камеры. Сверху режимы срезают
 * его ещё раз: видео берёт значение из `FeatureConfig.limitedMaxZoom` (на
 * PD2425 это 10x для Master), замедленная съёмка опускает до 5x и так далее.
 *
 * Хук поднимает верхнюю границу до предела режима фото — того, который камера
 * уже использует в стоке, когда снимаешь фото. Выше не поднимаем: зум уходит
 * в кроп сенсора, и запрос за пределами того, что отдаёт прошивка, роняет
 * камеру.
 *
 * Точка одна: [HOOK_CLASS] складывает посчитанный режимом диапазон в
 * `ZoomConfig.cameraTypeZoomRange`, откуда его берут и съёмка, и UI зума.
 * Фронтальную камеру не трогаем — там предел свой и оптики нет.
 */
object CameraZoomHook : BaseHook() {

    const val ID = "camera_zoom"

    private const val HOOK_CLASS = "com.android.camera.setting.SettingFunctionHook"

    /** `ModuleId.PHOTO_ID` — режим, предел которого берём за образец. */
    private const val PHOTO_MODULE_ID = "photo"

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
            as? MutableMap<String, Range<Float>>
        val current = ranges?.get(cameraType)
        val lower = current?.lower as? Float
        val upper = current?.upper as? Float
        if (ranges == null || lower == null || upper == null) {
            XLog.w("[$id] $moduleId/$cameraType: диапазон не прочитан")
            return
        }

        val characteristics = characteristics(hook, moduleId)
        if (characteristics == null) {
            XLog.w("[$id] $moduleId/$cameraType: характеристики камеры недоступны")
            return
        }

        val own = sensorUpper(characteristics, moduleId, isFront, cameraType)
        val photo = sensorUpper(characteristics, PHOTO_MODULE_ID, isFront, cameraType)
        val ceiling = maxOf(own ?: 0f, photo ?: 0f)
        val seen = "$moduleId/$cameraType $lower..$upper (свой $own, фото $photo)"

        if (ceiling <= upper || lower >= ceiling) {
            XLog.i("[$id] $seen: без изменений")
            return
        }

        try {
            ranges[cameraType] = Range(lower, ceiling)
        } catch (t: Throwable) {
            XLog.e("[$id] $seen: не удалось записать диапазон", t)
            return
        }
        XLog.i("[$id] $seen -> $ceiling")
    }

    /** Характеристики камеры того же экземпляра настроек, что и у режима. */
    private fun characteristics(hook: Any, moduleId: String): Any? {
        val manager = hook.callOrNull("getFunctionZoomManager", moduleId) ?: return null
        val settingManager = manager.fieldOrNull("mSettingManager") ?: return null
        return settingManager.callOrNull("getSettingCharacteristics")
    }

    /** Предел зума, который прошивка отдаёт для этой камеры в указанном режиме. */
    private fun sensorUpper(
        characteristics: Any,
        moduleId: String,
        isFront: Boolean,
        cameraType: String
    ): Float? {
        val range = characteristics.callOrNull(
            "getSensorZoomRange",
            moduleId,
            isFront,
            cameraType
        ) as? Range<*> ?: return null
        return range.upper as? Float
    }
}
