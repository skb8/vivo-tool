package com.skb8.vivotool.hooks.camera

import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.XLog

/**
 * Стабилизация с выравниванием горизонта в видео.
 *
 * Режим в приложении есть: стабилизация хранится в `pref_video_super_stable`
 * значениями «выкл» (0), «ультра» (1), «стандартная» (2) и «горизонт» (3).
 * При значении «3» `EisAddition.getEisSessionParam()` отдаёт режим 10, он
 * уходит в сессию как `SessionParam.eis`, а в интерфейсе появляется кнопка
 * переключения горизонтальной и вертикальной съёмки
 * (`SuperEisHorizonUpdateSideButton`).
 *
 * Список значений в настройках собирается под модель, и «3» в него не
 * попадает — выбрать режим руками нельзя. Хук подменяет прочитанное значение:
 * выбранная «ультра» отдаётся коду как «горизонт». Так соглашаются все, кто
 * спрашивает настройку: и EIS, и кнопка направления съёмки.
 *
 * Экспериментально: сам алгоритм живёт в HAL, и если для этой модели режима
 * 10 там нет, съёмка может не запуститься — тогда твик надо выключить.
 */
object CameraHorizonEisHook : BaseHook() {

    const val ID = "camera_horizon_eis"

    private const val SETTING_MANAGER_CLASS = "com.android.camera.setting.SettingManager"

    /** `ISettingKeys.KEY_VIDEO_STABLE`. */
    private const val STABLE_KEY = "pref_video_super_stable"

    private const val ULTRA_VALUE = "1"
    private const val HORIZON_VALUE = "3"

    override val id: String = ID

    override val titleRes: Int = R.string.hook_camera_horizon_eis_title

    override val descriptionRes: Int = R.string.hook_camera_horizon_eis_description

    override val targetPackages: Set<String> = setOf(CameraFeatureConfigHook.CAMERA_PACKAGE)

    /** Твик меняет режим съёмки, поэтому включается только вручную. */
    override val enabledByDefault: Boolean = false

    @Volatile
    private var reported = false

    override fun onHook() {
        val clazz = findClassOrNull(SETTING_MANAGER_CLASS)
        if (clazz == null) {
            XLog.w("[$id] класс $SETTING_MANAGER_CLASS не найден")
            return
        }

        val hooks = clazz.hookAllAfter("getSettingValueFromKey") { param ->
            if (param.args?.getOrNull(0) != STABLE_KEY) return@hookAllAfter
            if (param.result != ULTRA_VALUE) return@hookAllAfter

            param.result = HORIZON_VALUE
            // Метод горячий, поэтому в журнал пишем только первую подмену.
            if (!reported) {
                reported = true
                XLog.i("[$id] ультра-стабилизация отдаётся как режим горизонта")
            }
        }
        if (hooks.isEmpty()) {
            XLog.w("[$id] метод getSettingValueFromKey не найден")
        } else {
            XLog.i("[$id] getSettingValueFromKey перехвачен")
        }
    }
}
