package com.skb8.vivotool.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import com.skb8.vivotool.R
import com.skb8.vivotool.core.XLog
import com.skb8.vivotool.hooks.camera.CameraFeatureConfigHook
import com.skb8.vivotool.hooks.camera.CameraFeatureConfigHook.Source
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Одна фича камеры. */
data class CameraFeature(
    /** Где объявлена фича — от этого зависит ключ настройки. */
    val source: Source,
    /** Имя метода. */
    val name: String,
    /** Значение, которое метод возвращает в прошивке, если его удалось узнать. */
    val defaultValue: Boolean?,
    /** У метода есть аргументы, поэтому результат зависит от них. */
    val parameterized: Boolean,
    /** Сколько перегрузок у метода. */
    val overloads: Int,
    /** Класс, в котором метод объявлен — модель, общий конфиг или менеджер. */
    val declaredIn: String
) {
    /** Уникальный ключ для списка: имена в разных классах могут совпадать. */
    val key: String get() = "${source.name}:$name"
}

/** Результат разбора конфигурации камеры. */
data class CameraFeatureCatalog(
    val product: String,
    /** Найденные классы с фичами. */
    val classNames: List<String>,
    /** Классы, которых в этой прошивке нет. */
    val notFound: List<String>,
    val features: List<CameraFeature>,
    val error: String?
)

/**
 * Читает список фич прямо из APK камеры.
 *
 * APK системных приложений доступен на чтение, поэтому приложение открывает
 * его своим [PathClassLoader], находит классы с фичами и через рефлексию
 * собирает все методы с типом возврата `boolean`. Методы без аргументов
 * вызываются, чтобы показать значение из прошивки.
 *
 * Путь к нативным библиотекам загрузчику намеренно не передаётся: часть кода
 * камеры дёргает JNI, а нам нужны только значения конфигурации — без библиотек
 * такой вызов упадёт с `UnsatisfiedLinkError`, который мы перехватим, вместо
 * того чтобы выполнять нативный код камеры в процессе приложения.
 */
object CameraFeatureLoader {

    /** Общий бюджет на вызовы методов камеры: дальше значения просто неизвестны. */
    private const val PROBE_BUDGET_MS = 8_000L

    fun load(context: Context): CameraFeatureCatalog {
        val product = Build.PRODUCT.orEmpty()

        val appInfo = try {
            context.packageManager.getApplicationInfo(CameraFeatureConfigHook.CAMERA_PACKAGE, 0)
        } catch (t: Throwable) {
            return failure(
                product,
                context.getString(
                    R.string.camera_error_not_installed,
                    CameraFeatureConfigHook.CAMERA_PACKAGE
                )
            )
        }

        val loader = try {
            PathClassLoader(dexPath(appInfo), javaClass.classLoader)
        } catch (t: Throwable) {
            XLog.e("Не удалось открыть APK камеры", t)
            return failure(
                product,
                context.getString(R.string.camera_error_apk, t.message.orEmpty())
            )
        }

        val classNames = mutableListOf<String>()
        val notFound = mutableListOf<String>()
        val features = mutableListOf<CameraFeature>()
        val deadline = SystemClock.uptimeMillis() + PROBE_BUDGET_MS

        Source.entries.forEach { source ->
            val candidates = CameraFeatureConfigHook.classCandidates(source, product)
            val target = candidates.firstNotNullOfOrNull { name ->
                try {
                    loader.loadClass(name)
                } catch (_: Throwable) {
                    null
                }
            }
            if (target == null) {
                notFound += candidates.first().substringAfterLast('.')
                return@forEach
            }
            classNames += target.name
            features += collectFeatures(source, target, instanceOf(target), deadline)
        }

        if (classNames.isEmpty()) {
            val candidates = Source.entries
                .flatMap { CameraFeatureConfigHook.classCandidates(it, product) }
                .joinToString()
            return failure(product, context.getString(R.string.camera_error_class, candidates))
        }

        return CameraFeatureCatalog(
            product = product,
            classNames = classNames,
            notFound = notFound,
            features = features.sortedWith(
                compareBy({ it.name.lowercase() }, { it.source.ordinal })
            ),
            error = null
        )
    }

    private fun failure(product: String, error: String) = CameraFeatureCatalog(
        product = product,
        classNames = emptyList(),
        notFound = emptyList(),
        features = emptyList(),
        error = error
    )

    private fun dexPath(appInfo: ApplicationInfo): String {
        val parts = buildList {
            add(appInfo.sourceDir)
            appInfo.splitSourceDirs?.let { addAll(it) }
        }
        return parts.filter { it.isNotBlank() }.joinToString(File.pathSeparator)
    }

    /**
     * Объект, на котором можно вызывать методы: у менеджера это готовый
     * синглтон в поле `instance`, у конфигурации — обычный конструктор.
     */
    private fun instanceOf(target: Class<*>): Any? {
        val singleton = try {
            target.getDeclaredField("instance")
                .takeIf { Modifier.isStatic(it.modifiers) }
                ?.apply { isAccessible = true }
                ?.get(null)
        } catch (t: Throwable) {
            null
        }
        if (singleton != null) return singleton

        return try {
            target.getDeclaredConstructor().newInstance()
        } catch (t: Throwable) {
            XLog.w("Не удалось создать ${target.name}: ${t.message}")
            null
        }
    }

    private fun collectFeatures(
        source: Source,
        target: Class<*>,
        instance: Any?,
        deadline: Long
    ): List<CameraFeature> {
        val byName = linkedMapOf<String, MutableList<Method>>()

        var next: Class<*>? = target
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
                source = source,
                name = name,
                defaultValue = noArgs?.let { readValue(it, instance, deadline) },
                parameterized = noArgs == null,
                overloads = methods.size,
                declaredIn = methods.first().declaringClass.simpleName
            )
        }
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
    private fun readValue(method: Method, instance: Any?, deadline: Long): Boolean? {
        if (instance == null) return null
        if (SystemClock.uptimeMillis() > deadline) return null
        return try {
            method.isAccessible = true
            method.invoke(instance) as? Boolean
        } catch (t: Throwable) {
            null
        }
    }
}
