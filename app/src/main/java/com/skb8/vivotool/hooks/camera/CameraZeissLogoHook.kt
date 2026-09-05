package com.skb8.vivotool.hooks.camera

import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.XLog

/**
 * Логотип Zeiss в водяном знаке-рамке.
 *
 * Сама рамка на этой прошивке поддерживается (`isSupportWatermarkBorder()`
 * возвращает true), но логотип из неё вырезан по серии устройства —
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
 * `FeatureConfig.productSeries()`, то есть значением-строкой — фичами из
 * FeatureConfig это не переключить.
 *
 * Хук отвечает «не Y и не T» только тогда, когда спрашивает код водяного
 * знака: [DEVICE_UTIL_CLASS] используется по всему приложению для вёрстки и
 * набора функций, и врать всем подряд — это чужие поломки.
 */
object CameraZeissLogoHook : BaseHook() {

    const val ID = "camera_zeiss_logo"

    private const val DEVICE_UTIL_CLASS = "com.android.camera.utils.DeviceUtil"

    /** Проверки серии, из-за которых логотип не рисуется. */
    private val SERIES_CHECKS = listOf("isYSeries", "isTSeries")

    /** Пакет кода водяного знака — по нему узнаём вызывающего. */
    private const val WATERMARK_MARKER = "watermark"

    override val id: String = ID

    override val titleRes: Int = R.string.hook_camera_zeiss_logo_title

    override val descriptionRes: Int = R.string.hook_camera_zeiss_logo_description

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
