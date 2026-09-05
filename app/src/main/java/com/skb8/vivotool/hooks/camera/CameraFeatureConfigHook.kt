package com.skb8.vivotool.hooks.camera

import android.os.Build
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.HookPrefs
import com.skb8.vivotool.core.XLog
import java.lang.reflect.Method

/**
 * Подмена доступных фич камеры.
 *
 * Фичи лежат в двух местах (см. [Source]):
 *  - класс под конкретную модель `FeatureConfig_meat_<ro.product.name>`
 *    (например `FeatureConfig_meat_PD2425`) с наследованием от `FeatureConfig_MEAT` —
 *    это значения «из прошивки»;
 *  - `FeatureManager`, который поверх них учитывает возможности платформы,
 *    поэтому одна и та же фича может быть выключена именно здесь.
 *
 * Хук подменяет методы с типом возврата `boolean`: каждая фича может быть
 * оставлена как есть, принудительно включена или выключена. Список фич
 * собирает само приложение — см. `settings/CameraFeatureLoader.kt`.
 *
 * Переопределения читаются один раз при старте процесса камеры, поэтому
 * после изменения нужно закрыть камеру (force stop).
 */
object CameraFeatureConfigHook : BaseHook() {

    const val ID = "camera_feature_config"

    /** Пакет приложения камеры. */
    const val CAMERA_PACKAGE = "com.android.camera"

    /** Пакет с классами конфигурации камеры. */
    const val CONFIG_PACKAGE = "com.android.camera.featureconfig"

    private const val BASE_CONFIG_CLASS =
        "com.android.camera.featureconfig.configuration.loader.FeatureConfig_MEAT"

    /**
     * Источник фич. Префикс ключа настроек у каждого свой, поэтому фичи
     * с одинаковыми именами в разных классах не конфликтуют.
     */
    enum class Source(val keyPrefix: String) {
        CONFIG("camera_feature_"),
        MANAGER("camera_manager_")
    }

    override val id: String = ID

    override val titleRes: Int = R.string.hook_camera_features_title

    override val descriptionRes: Int = R.string.hook_camera_features_description

    override val targetPackages: Set<String> = setOf(CAMERA_PACKAGE)

    /** Ключ настройки для фичи. */
    fun settingsKey(source: Source, feature: String): String = source.keyPrefix + feature

    /** Имя фичи из ключа настройки. */
    fun featureName(source: Source, settingsKey: String): String =
        settingsKey.removePrefix(source.keyPrefix)

    /**
     * Возможные имена класса в порядке приоритета: для конфигурации это
     * сначала класс под конкретную модель, затем общий базовый.
     */
    fun classCandidates(source: Source, product: String = Build.PRODUCT): List<String> =
        when (source) {
            Source.CONFIG -> buildList {
                if (product.isNotBlank()) {
                    add("$CONFIG_PACKAGE.FeatureConfig_meat_$product")
                    add("$CONFIG_PACKAGE.FeatureConfig_meat_${product.uppercase()}")
                }
                add(BASE_CONFIG_CLASS)
            }.distinct()

            Source.MANAGER -> listOf("$CONFIG_PACKAGE.FeatureManager")
        }

    override fun onHook() {
        Source.entries.forEach(::applyOverrides)
    }

    private fun applyOverrides(source: Source) {
        val overrides = readOverrides(source)
        if (overrides.isEmpty()) {
            XLog.d("[$id] ${source.name}: переопределений нет")
            return
        }

        val candidates = classCandidates(source)
        val target = candidates.firstNotNullOfOrNull { findClassOrNull(it) }
        if (target == null) {
            XLog.w("[$id] ${source.name}: класс не найден, искали: ${candidates.joinToString()}")
            return
        }
        XLog.i("[$id] ${target.name}: переопределений ${overrides.size}")

        var applied = 0
        overrides.forEach { (feature, value) ->
            val methods = booleanMethods(target, feature)
            if (methods.isEmpty()) {
                XLog.w("[$id] фича '$feature' не найдена в ${target.name}")
                return@forEach
            }
            methods.forEach { method ->
                if (method.replaceWithConstant(value) != null) {
                    applied++
                    XLog.i("[$id] ${method.declaringClass.simpleName}.$feature -> $value")
                }
            }
        }
        XLog.i("[$id] ${source.name}: применено методов $applied")
    }

    private fun readOverrides(source: Source): Map<String, Boolean> =
        HookPrefs.entriesWithPrefix(source.keyPrefix)
            .mapNotNull { (key, value) ->
                (value as? Boolean)?.let { featureName(source, key) to it }
            }
            .toMap()

    /**
     * Все реализации метода с типом возврата `boolean` во всей иерархии:
     * фича может быть объявлена и в классе модели, и в базовом конфиге,
     * а вызвана через `super`, поэтому подменяем каждую.
     */
    private fun booleanMethods(target: Class<*>, name: String): List<Method> =
        target.methodsInHierarchy(name, Boolean::class.javaPrimitiveType)
}
