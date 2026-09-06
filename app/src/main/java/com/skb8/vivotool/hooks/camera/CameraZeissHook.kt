package com.skb8.vivotool.hooks.camera

import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.XLog

/**
 * Возможности Zeiss, отключённые для этой модели.
 *
 * Три вещи включаются флагами `FeatureConfig` — их твик выставляет сам, через
 * те же настройки, что и «Изменение доступных фич» (см. [REQUIRED_FEATURES]):
 *  - `isSupportZeissColor` — кнопка цвета T*. Гейт в `PhotoSettingSupport`:
 *    `isSupportZeissColor(isFront) && !isSupportColorfulButton(isFront, module)`,
 *    поэтому вторым флагом кнопку «живого» цвета приходится выключать;
 *  - `isSupportPortraitFormulaConfig` — формулы портрета Biotar, Distagon,
 *    Planar и Sonnar (`KEY_PORTRAIT_FORMULA`).
 *
 * Логотип в водяном знаке-рамке флагами не включается. Рамка поддерживается
 * (`isSupportWatermarkBorder()` возвращает true), но
 * `WatermarkFrameLogo.provideLogoOrSpecialIcon` начинается с:
 *
 * ```java
 * if (DeviceUtil.isYSeries() || (isPadDevice() && !isIQOO()) || DeviceUtil.isTSeries()) {
 *     return null; // никакого логотипа
 * }
 * ```
 *
 * Там же по этим проверкам скрывается разделительная линия рядом с логотипом.
 * Серия читается из системного свойства `vivo.product.series` и из
 * `FeatureConfig.productSeries()`, то есть значением-строкой.
 *
 * Хук отвечает «не Y и не T» только тогда, когда спрашивает код водяного
 * знака: [DEVICE_UTIL_CLASS] используется по всему приложению для вёрстки и
 * набора функций, и врать всем подряд — это чужие поломки.
 */
object CameraZeissHook : BaseHook() {

    const val ID = "camera_zeiss"

    /**
     * Фичи `FeatureConfig`, без которых кнопки Zeiss не появятся.
     * Приложение выставляет их при включении твика и убирает при выключении.
     */
    val REQUIRED_FEATURES: Map<String, Boolean> = mapOf(
        "isSupportZeissColor" to true,
        "isSupportColorfulButton" to false,
        "isSupportPortraitFormulaConfig" to true
    )

    private const val DEVICE_UTIL_CLASS = "com.android.camera.utils.DeviceUtil"

    /** Проверки серии, из-за которых логотип не рисуется. */
    private val SERIES_CHECKS = listOf("isYSeries", "isTSeries")

    /** Пакет кода водяного знака — по нему узнаём вызывающего. */
    private const val WATERMARK_MARKER = "watermark"

    override val id: String = ID

    override val titleRes: Int = R.string.hook_camera_zeiss_title

    override val descriptionRes: Int = R.string.hook_camera_zeiss_description

    override val targetPackages: Set<String> = setOf(CameraFeatureConfigHook.CAMERA_PACKAGE)

    /** Твик меняет вид снимков, поэтому включается только вручную. */
    override val enabledByDefault: Boolean = false

    override fun onHook() {
        val clazz = findClassOrNull(DEVICE_UTIL_CLASS)
        if (clazz == null) {
            XLog.w("[$id] класс $DEVICE_UTIL_CLASS не найден")
            return
        }

        SERIES_CHECKS.forEach { name ->
            val hooks = clazz.hookAllBefore(name) { param ->
                if (calledFromWatermark()) param.result = false
            }
            if (hooks.isEmpty()) {
                XLog.w("[$id] метод $name не найден")
            } else {
                XLog.i("[$id] $name перехвачен")
            }
        }
    }

    private fun calledFromWatermark(): Boolean =
        Throwable().stackTrace.any { frame ->
            frame.className.contains(WATERMARK_MARKER, ignoreCase = true)
        }
}
