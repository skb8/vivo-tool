package com.skb8.vivotool.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.skb8.vivotool.core.XLog
import com.skb8.vivotool.hooks.camera.CameraFeatureConfigHook
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Одна фича из FeatureConfig камеры. */
data class CameraFeature(
    /** Имя метода — оно же ключ настройки. */
    val name: String,
    /** Значение, которое метод возвращает в прошивке, если его удалось узнать. */
    val defaultValue: Boolean?,
    /** У метода есть аргументы, поэтому результат зависит от них. */
    val parameterized: Boolean,
    /** Сколько перегрузок у метода. */
    val overloads: Int,
    /** Класс, в котором метод объявлен — модель или общий конфиг. */
    val declaredIn: String
)

/** Результат разбора конфигурации камеры. */
data class CameraFeatureCatalog(
    val product: String,
    val configClassName: String?,
    val features: List<CameraFeature>,
    val error: String?
)

/**
 * Читает список фич прямо из APK камеры.
 *
 * APK системных приложений доступен на чтение, поэтому приложение открывает
 * его своим [PathClassLoader], находит класс конфигурации для этой модели
 * и через рефлексию собирает все методы с типом возврата `boolean`.
 * Методы без аргументов вызываются, чтобы показать значение из прошивки.
 */
object CameraFeatureLoader {

    fun load(context: Context): CameraFeatureCatalog {
        val product = Build.PRODUCT.orEmpty()
        val candidates = CameraFeatureConfigHook.configClassCandidates(product)

        val appInfo = try {
            context.packageManager.getApplicationInfo(CameraFeatureConfigHook.CAMERA_PACKAGE, 0)
        } catch (t: Throwable) {
            return CameraFeatureCatalog(
                product = product,
                configClassName = null,
                features = emptyList(),
                error = "Приложение камеры (${CameraFeatureConfigHook.CAMERA_PACKAGE}) не найдено"
            )
        }

        val loader = try {
            PathClassLoader(dexPath(appInfo), appInfo.nativeLibraryDir, javaClass.classLoader)
        } catch (t: Throwable) {
            XLog.e("Не удалось открыть APK камеры", t)
            return CameraFeatureCatalog(
                product = product,
                configClassName = null,
                features = emptyList(),
                error = "Не удалось прочитать APK камеры: ${t.message}"
            )
        }

        val configClass = candidates.firstNotNullOfOrNull { className ->
            try {
                loader.loadClass(className)
            } catch (_: Throwable) {
                null
            }
        } ?: return CameraFeatureCatalog(
            product = product,
            configClassName = null,
            features = emptyList(),
            error = "Класс конфигурации не найден. Искали: ${candidates.joinToString()}"
        )

        val instance = try {
            configClass.getDeclaredConstructor().newInstance()
        } catch (t: Throwable) {
            XLog.w("Не удалось создать ${configClass.name}: ${t.message}")
            null
        }

        return CameraFeatureCatalog(
            product = product,
            configClassName = configClass.name,
            features = collectFeatures(configClass, instance),
            error = null
        )
    }

    private fun dexPath(appInfo: ApplicationInfo): String {
        val parts = buildList {
            add(appInfo.sourceDir)
            appInfo.splitSourceDirs?.let { addAll(it) }
        }
        return parts.filter { it.isNotBlank() }.joinToString(File.pathSeparator)
    }

    private fun collectFeatures(configClass: Class<*>, instance: Any?): List<CameraFeature> {
        val byName = linkedMapOf<String, MutableList<Method>>()

        var next: Class<*>? = configClass
        while (true) {
            val clazz = next ?: break
            if (clazz == Any::class.java) break

            val declared = try {
                clazz.declaredMethods
            } catch (t: Throwable) {
                XLog.w("Не удалось прочитать методы ${clazz.name}: ${t.message}")
                emptyArray()
            }

            declared.forEach { method ->
                if (!isFeatureMethod(method)) return@forEach
                byName.getOrPut(method.name) { mutableListOf() }.add(method)
            }

            next = try {
                clazz.superclass
            } catch (t: Throwable) {
                null
            }
        }

        return byName.map { (name, methods) ->
            val noArgs = methods.firstOrNull { it.parameterCount == 0 }
            CameraFeature(
                name = name,
                defaultValue = noArgs?.let { readValue(it, instance) },
                parameterized = noArgs == null,
                overloads = methods.size,
                declaredIn = methods.first().declaringClass.simpleName
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun isFeatureMethod(method: Method): Boolean {
        if (method.returnType != Boolean::class.javaPrimitiveType) return false
        if (Modifier.isStatic(method.modifiers)) return false
        if (Modifier.isAbstract(method.modifiers)) return false
        if (Modifier.isPrivate(method.modifiers)) return false
        if (method.isSynthetic || method.isBridge) return false
        // equals(Object) и подобное к фичам не относится
        return method.name != "equals"
    }

    /** Вызывает метод, чтобы узнать значение из прошивки. */
    private fun readValue(method: Method, instance: Any?): Boolean? {
        if (instance == null) return null
        return try {
            method.isAccessible = true
            method.invoke(instance) as? Boolean
        } catch (t: Throwable) {
            null
        }
    }
}
