package com.skb8.vivotool.hooks.camera

import android.os.Build
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.HookPrefs
import com.skb8.vivotool.core.XLog
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Подмена доступных фич камеры.
 *
 * Прошивка держит набор фич в классе, имя которого зависит от модели:
 * `com.android.camera.featureconfig.FeatureConfig_meat_<ro.product.name>`
 * (например `FeatureConfig_meat_PD2425`), который наследуется от
 * `FeatureConfig_MEAT` с общими значениями.
 *
 * Хук подменяет методы с типом возврата `boolean`: каждая фича может быть
 * оставлена как есть, принудительно включена или выключена. Значения берутся
 * из настроек, список фич собирает само приложение — см.
 * `settings/CameraFeatureLoader.kt`.
 *
 * Переопределения читаются один раз при старте процесса камеры, поэтому
 * после изменения нужно закрыть камеру (force stop).
 */
object CameraFeatureConfigHook : BaseHook() {

    const val ID = "camera_feature_config"

    /** Пакет приложения камеры. */
    const val CAMERA_PACKAGE = "com.android.camera"

    /** Префикс ключей настроек: `camera_feature_<имя метода>` = true/false. */
    const val KEY_PREFIX = "camera_feature_"

    private const val CONFIG_PACKAGE = "com.android.camera.featureconfig"

    private const val BASE_CONFIG_CLASS =
        "com.android.camera.featureconfig.configuration.loader.FeatureConfig_MEAT"

    override val id: String = ID

    override val titleRes: Int = R.string.hook_camera_features_title

    override val descriptionRes: Int = R.string.hook_camera_features_description

    override val targetPackages: Set<String> = setOf(CAMERA_PACKAGE)

    /** Ключ настройки для фичи. */
    fun settingsKey(feature: String): String = KEY_PREFIX + feature

    /** Имя фичи из ключа настройки. */
    fun featureName(settingsKey: String): String = settingsKey.removePrefix(KEY_PREFIX)

    /**
     * Возможные имена класса конфигурации в порядке приоритета:
     * сначала класс под конкретную модель, затем общий базовый.
     */
    fun configClassCandidates(product: String = Build.PRODUCT): List<String> = buildList {
        if (product.isNotBlank()) {
            add("$CONFIG_PACKAGE.FeatureConfig_meat_$product")
            add("$CONFIG_PACKAGE.FeatureConfig_meat_${product.uppercase()}")
        }
        add(BASE_CONFIG_CLASS)
    }.distinct()

    override fun onHook() {
        val overrides = readOverrides()
        if (overrides.isEmpty()) {
            XLog.i("[$id] переопределений нет, конфигурация камеры не тронута")
            return
        }

        val configClass = configClassCandidates().firstNotNullOfOrNull { findClassOrNull(it) }
        if (configClass == null) {
            XLog.w(
                "[$id] класс конфигурации не найден, искали: " +
                    configClassCandidates().joinToString()
            )
            return
        }
        XLog.i("[$id] конфигурация: ${configClass.name}, переопределений: ${overrides.size}")

        var applied = 0
        overrides.forEach { (feature, value) ->
            val methods = booleanMethods(configClass, feature)
            if (methods.isEmpty()) {
                XLog.w("[$id] фича '$feature' не найдена в ${configClass.name}")
                return@forEach
            }
            methods.forEach { method ->
                if (method.replaceWithConstant(value) != null) {
                    applied++
                    XLog.i("[$id] ${method.declaringClass.simpleName}.$feature -> $value")
                }
            }
        }
        XLog.i("[$id] применено методов: $applied")
    }

    private fun readOverrides(): Map<String, Boolean> =
        HookPrefs.entriesWithPrefix(KEY_PREFIX)
            .mapNotNull { (key, value) ->
                (value as? Boolean)?.let { featureName(key) to it }
            }
            .toMap()

    /**
     * Все реализации метода с типом возврата `boolean` во всей иерархии:
     * фича может быть объявлена и в классе модели, и в базовом конфиге,
     * а вызвана через `super`, поэтому подменяем каждую.
     */
    private fun booleanMethods(configClass: Class<*>, name: String): List<Method> {
        val found = mutableListOf<Method>()
        var next: Class<*>? = configClass
        while (true) {
            val clazz = next ?: break
            if (clazz == Any::class.java) break

            val declared = try {
                clazz.declaredMethods
            } catch (t: Throwable) {
                XLog.e("[$id] не удалось прочитать методы ${clazz.name}", t)
                emptyArray()
            }
            declared.filterTo(found) { method ->
                method.name == name &&
                    method.returnType == Boolean::class.javaPrimitiveType &&
                    !Modifier.isAbstract(method.modifiers)
            }

            next = try {
                clazz.superclass
            } catch (t: Throwable) {
                null
            }
        }
        return found
    }
}
