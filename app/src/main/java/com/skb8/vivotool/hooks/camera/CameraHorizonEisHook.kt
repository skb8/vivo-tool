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
 * выбранная «стандартная» отдаётся коду как «горизонт». Так соглашаются все,
 * кто спрашивает настройку: и EIS, и кнопка направления съёмки.
 *
 * Носителем выбрана именно «стандартная», а не «ультра»: ультра-стабилизация
 * на этой модели снимает с широкоугольного объектива, а стандартная — с
 * основного. Правило mutex читает `ListPreference` напрямую, минуя подмену,
 * поэтому выбор камеры и диапазон зума остаются от стандартного режима, то
 * есть с основной камеры. «Ультра» при этом работает как раньше.
 *
 * Вместе с режимом приложение показывает обучающую подсказку с видео
 * `R.raw.horizon_tip`, а в ресурсах этой прошивки такого файла нет — сборка
 * его выкинула вместе с неподдерживаемым режимом. Поэтому создание подсказки
 * приходится отключать, иначе камера падает с `NoSuchFieldError` на первом
 * кадре превью (см. [MODULE_UI_CLASS]).
 *
 * Экспериментально: сам алгоритм живёт в HAL, и если для этой модели режима
 * 10 там нет, съёмка может не запуститься — тогда твик надо выключить.
 */
object CameraHorizonEisHook : BaseHook() {

    const val ID = "camera_horizon_eis"

    private const val SETTING_MANAGER_CLASS = "com.android.camera.setting.SettingManager"

    private const val MODULE_UI_CLASS =
        "com.android.camera.normalvideo.ui.moduleui.NormalVideoModuleUI"

    /** Показ обучающей подсказки о съёмке с горизонтом. */
    private const val TIPS_METHOD = "showOrHideHelpTipsView"

    /** `ISettingKeys.KEY_VIDEO_STABLE`. */
    private const val STABLE_KEY = "pref_video_super_stable"

    /** «Стандартная» стабилизация — она снимает с основной камеры. */
    private const val NORMAL_VALUE = "2"
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
        silenceHelpTips()

        val clazz = findClassOrNull(SETTING_MANAGER_CLASS)
        if (clazz == null) {
            XLog.w("[$id] класс $SETTING_MANAGER_CLASS не найден")
            return
        }

        val hooks = clazz.hookAllAfter("getSettingValueFromKey") { param ->
            if (param.args?.getOrNull(0) != STABLE_KEY) return@hookAllAfter
            if (param.result != NORMAL_VALUE) return@hookAllAfter

            param.result = HORIZON_VALUE
            // Метод горячий, поэтому в журнал пишем только первую подмену.
            if (!reported) {
                reported = true
                XLog.i("[$id] стандартная стабилизация отдаётся как режим горизонта")
            }
        }
        if (hooks.isEmpty()) {
            XLog.w("[$id] метод getSettingValueFromKey не найден")
        } else {
            XLog.i("[$id] getSettingValueFromKey перехвачен")
        }
    }

    /**
     * Отключает обучающую подсказку о съёмке с горизонтом: она тянет видео
     * `R.raw.horizon_tip`, которого в этой прошивке нет. Метод занимается
     * только этой подсказкой, так что кроме неё ничего не теряется.
     */
    private fun silenceHelpTips() {
        val clazz = findClassOrNull(MODULE_UI_CLASS)
        if (clazz == null) {
            XLog.w("[$id] класс $MODULE_UI_CLASS не найден")
            return
        }

        val hooks = clazz.skipAll(TIPS_METHOD)
        if (hooks.isEmpty()) {
            XLog.w("[$id] метод $TIPS_METHOD не найден, камера может упасть на подсказке")
        } else {
            XLog.i("[$id] подсказка о горизонте отключена")
        }
    }
}
