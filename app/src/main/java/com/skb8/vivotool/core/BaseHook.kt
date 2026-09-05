package com.skb8.vivotool.core

import android.os.Build
import androidx.annotation.StringRes
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Базовый класс для всех хуков.
 *
 * Чтобы добавить хук, создайте наследника в пакете `com.skb8.vivotool.hooks`,
 * зарегистрируйте его в [HookRegistry] и добавьте целевой пакет в `app/module-scope.txt`.
 *
 * Пример:
 * ```
 * object ExampleHook : BaseHook() {
 *     override val id = "example"
 *     override val titleRes = R.string.hook_example_title
 *     override val targetPackages = setOf("com.example.app")
 *
 *     override fun onHook() {
 *         findClass("com.example.app.Foo").hookAfter("bar", Int::class.java) { param ->
 *             param.result = 42
 *         }
 *     }
 * }
 * ```
 */
abstract class BaseHook {

    /** Стабильный идентификатор: используется как ключ настроек, менять нельзя. */
    abstract val id: String

    /** Название хука для списка в приложении. */
    @get:StringRes
    abstract val titleRes: Int

    /** Короткое описание того, что делает хук, или 0, если его нет. */
    @get:StringRes
    open val descriptionRes: Int = 0

    /**
     * Пакеты приложений, в процессах которых нужно применить хук.
     *
     * Используйте [Constants.SYSTEM_FRAMEWORK] для system_server и
     * [Constants.ALL_PACKAGES] — чтобы применять хук во всех процессах.
     */
    abstract val targetPackages: Set<String>

    /** Включён ли хук, пока пользователь не изменил настройку. */
    open val enabledByDefault: Boolean = true

    /** Минимальная поддерживаемая версия Android (API level). */
    open val minSdk: Int = Build.VERSION_CODES.O_MR1

    /** Максимальная поддерживаемая версия Android (API level). */
    open val maxSdk: Int = Int.MAX_VALUE

    private var loadPackageParam: XC_LoadPackage.LoadPackageParam? = null

    /** Параметры загрузки текущего пакета. Доступны только внутри [onHook]. */
    protected val param: XC_LoadPackage.LoadPackageParam
        get() = requireNotNull(loadPackageParam) { "param доступен только внутри onHook()" }

    /** ClassLoader приложения, в которое внедряется хук. */
    protected val classLoader: ClassLoader
        get() = param.classLoader

    /** Пакет приложения, в которое внедряется хук. */
    protected val hookedPackage: String
        get() = param.packageName

    /** Собственно логика хука. Исключения перехватываются и логируются. */
    protected abstract fun onHook()

    /**
     * Хуки на стадии Zygote (для ресурсов и системных классов).
     * Вызывается один раз при старте, до [onHook], и не зависит от настроек.
     */
    open fun onZygote(startupParam: IXposedHookZygoteInit.StartupParam) = Unit

    /** Подходит ли хук текущей версии Android. */
    fun isSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt in minSdk..maxSdk

    /** Применим ли хук к указанному пакету. */
    fun matches(packageName: String): Boolean =
        Constants.ALL_PACKAGES in targetPackages || packageName in targetPackages

    internal fun applyTo(lpparam: XC_LoadPackage.LoadPackageParam) {
        loadPackageParam = lpparam
        try {
            onHook()
            XLog.i("Хук '$id' применён к ${lpparam.packageName}")
        } catch (t: Throwable) {
            XLog.e("Хук '$id' упал на ${lpparam.packageName}", t)
        }
    }

    // ---------------------------------------------------------------------
    // Поиск классов
    // ---------------------------------------------------------------------

    /** Находит класс или бросает исключение (будет поймано в [applyTo]). */
    protected fun findClass(className: String, loader: ClassLoader = classLoader): Class<*> =
        XposedHelpers.findClass(className, loader)

    /** Находит класс или возвращает null, если его нет. */
    protected fun findClassOrNull(className: String, loader: ClassLoader = classLoader): Class<*>? =
        XposedHelpers.findClassIfExists(className, loader)

    /** Первый существующий класс из списка — удобно для разных версий прошивки. */
    protected fun findFirstClass(vararg classNames: String, loader: ClassLoader = classLoader): Class<*>? =
        classNames.firstNotNullOfOrNull { XposedHelpers.findClassIfExists(it, loader) }

    /** Поле класса или его родителей, готовое к чтению и записи, или null. */
    protected fun Class<*>.findFieldOrNull(name: String): Field? =
        XposedHelpers.findFieldIfExists(this, name)

    /**
     * Все неабстрактные реализации метода во всей иерархии класса.
     *
     * Нужно, когда неизвестно, в каком классе прошивки метод объявлен, или когда
     * он переопределён и вызывается через `super`: тогда подменять нужно каждую
     * реализацию. `hookAll*` так не умеет — он видит только объявленные в классе.
     */
    protected fun Class<*>.methodsInHierarchy(
        name: String,
        returnType: Class<*>? = null
    ): List<Method> {
        val found = mutableListOf<Method>()
        var next: Class<*>? = this
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
                    (returnType == null || method.returnType == returnType) &&
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

    // ---------------------------------------------------------------------
    // Хуки методов
    // ---------------------------------------------------------------------

    /** Хук перед выполнением метода. */
    protected fun Class<*>.hookBefore(
        methodName: String,
        vararg parameterTypes: Any?,
        action: (XC_MethodHook.MethodHookParam) -> Unit
    ): XC_MethodHook.Unhook? = hookMethod(this, methodName, parameterTypes, beforeCallback(action))

    /** Хук после выполнения метода. */
    protected fun Class<*>.hookAfter(
        methodName: String,
        vararg parameterTypes: Any?,
        action: (XC_MethodHook.MethodHookParam) -> Unit
    ): XC_MethodHook.Unhook? = hookMethod(this, methodName, parameterTypes, afterCallback(action))

    /** Полная замена тела метода; результат лямбды становится результатом метода. */
    protected fun Class<*>.replace(
        methodName: String,
        vararg parameterTypes: Any?,
        action: (XC_MethodHook.MethodHookParam) -> Any?
    ): XC_MethodHook.Unhook? = hookMethod(this, methodName, parameterTypes, replaceCallback(action))

    /** Метод всегда возвращает указанное значение. */
    protected fun Class<*>.returnConstant(
        methodName: String,
        value: Any?,
        vararg parameterTypes: Any?
    ): XC_MethodHook.Unhook? =
        hookMethod(this, methodName, parameterTypes, XC_MethodReplacement.returnConstant(value))

    /** Метод становится пустым (ничего не делает). */
    protected fun Class<*>.doNothing(
        methodName: String,
        vararg parameterTypes: Any?
    ): XC_MethodHook.Unhook? =
        hookMethod(this, methodName, parameterTypes, XC_MethodReplacement.DO_NOTHING)

    /**
     * Метод не выполняется вообще: возвращается нейтральное значение под его
     * тип (`null`, `false`, `0`). Безопаснее, чем `doNothing`, когда сигнатура
     * метода в прошивке неизвестна.
     */
    protected fun Class<*>.skipAll(methodName: String): Set<XC_MethodHook.Unhook> =
        hookAllBefore(methodName) { param -> param.result = neutralResult(param.method) }

    /** Хук всех перегрузок метода — до выполнения. */
    protected fun Class<*>.hookAllBefore(
        methodName: String,
        action: (XC_MethodHook.MethodHookParam) -> Unit
    ): Set<XC_MethodHook.Unhook> = hookAll(this, methodName, beforeCallback(action))

    /** Хук всех перегрузок метода — после выполнения. */
    protected fun Class<*>.hookAllAfter(
        methodName: String,
        action: (XC_MethodHook.MethodHookParam) -> Unit
    ): Set<XC_MethodHook.Unhook> = hookAll(this, methodName, afterCallback(action))

    /** Замена всех перегрузок метода. */
    protected fun Class<*>.replaceAll(
        methodName: String,
        action: (XC_MethodHook.MethodHookParam) -> Any?
    ): Set<XC_MethodHook.Unhook> = hookAll(this, methodName, replaceCallback(action))

    /** Хук конструктора после выполнения. */
    protected fun Class<*>.hookConstructorAfter(
        vararg parameterTypes: Any?,
        action: (XC_MethodHook.MethodHookParam) -> Unit
    ): XC_MethodHook.Unhook? = hookConstructor(this, parameterTypes, afterCallback(action))

    /** Хук конструктора до выполнения. */
    protected fun Class<*>.hookConstructorBefore(
        vararg parameterTypes: Any?,
        action: (XC_MethodHook.MethodHookParam) -> Unit
    ): XC_MethodHook.Unhook? = hookConstructor(this, parameterTypes, beforeCallback(action))

    /** Хук всех конструкторов после выполнения. */
    protected fun Class<*>.hookAllConstructorsAfter(
        action: (XC_MethodHook.MethodHookParam) -> Unit
    ): Set<XC_MethodHook.Unhook> = safeHookSet("конструкторы ${this.name}") {
        XposedBridge.hookAllConstructors(this, afterCallback(action))
    }

    /**
     * Выполняет действие после создания Application целевого приложения —
     * удобно, когда нужен готовый Context.
     */
    protected fun afterApplicationCreated(action: (android.app.Application) -> Unit) {
        findClass("android.app.Application").hookAfter("onCreate") { hookParam ->
            (hookParam.thisObject as? android.app.Application)?.let(action)
        }
    }

    /**
     * Подменяет уже найденный через рефлексию метод: он не выполняется,
     * а сразу возвращает [value]. Удобно, когда метод нашли обходом иерархии.
     */
    protected fun Method.replaceWithConstant(value: Any?): XC_MethodHook.Unhook? =
        safeHook("$declaringClass.$name") {
            XposedBridge.hookMethod(this, XC_MethodReplacement.returnConstant(value))
        }

    /** Хук после выполнения уже найденного через рефлексию метода. */
    protected fun Method.hookAfter(
        action: (XC_MethodHook.MethodHookParam) -> Unit
    ): XC_MethodHook.Unhook? = safeHook("$declaringClass.$name") {
        XposedBridge.hookMethod(this, afterCallback(action))
    }

    /** Значение, которое можно вернуть вместо вызова метода, не сломав вызывающий код. */
    protected fun neutralResult(member: Member?): Any? =
        when ((member as? Method)?.returnType) {
            null, Void.TYPE -> null
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            Short::class.javaPrimitiveType -> 0.toShort()
            Byte::class.javaPrimitiveType -> 0.toByte()
            Char::class.javaPrimitiveType -> '\u0000'
            else -> null
        }

    // ---------------------------------------------------------------------
    // Внутренняя реализация
    // ---------------------------------------------------------------------

    private fun hookMethod(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: Array<out Any?>,
        callback: XC_MethodHook
    ): XC_MethodHook.Unhook? = safeHook("${clazz.name}.$methodName") {
        XposedHelpers.findAndHookMethod(clazz, methodName, *parameterTypes, callback)
    }

    private fun hookConstructor(
        clazz: Class<*>,
        parameterTypes: Array<out Any?>,
        callback: XC_MethodHook
    ): XC_MethodHook.Unhook? = safeHook("${clazz.name}.<init>") {
        XposedHelpers.findAndHookConstructor(clazz, *parameterTypes, callback)
    }

    private fun hookAll(
        clazz: Class<*>,
        methodName: String,
        callback: XC_MethodHook
    ): Set<XC_MethodHook.Unhook> = safeHookSet("${clazz.name}.$methodName") {
        XposedBridge.hookAllMethods(clazz, methodName, callback)
    }

    private inline fun safeHook(
        target: String,
        block: () -> XC_MethodHook.Unhook?
    ): XC_MethodHook.Unhook? = try {
        block()
    } catch (t: Throwable) {
        XLog.e("[$id] не удалось хукнуть $target", t)
        null
    }

    private inline fun safeHookSet(
        target: String,
        block: () -> Set<XC_MethodHook.Unhook>
    ): Set<XC_MethodHook.Unhook> = try {
        block()
    } catch (t: Throwable) {
        XLog.e("[$id] не удалось хукнуть $target", t)
        emptySet()
    }

    private fun beforeCallback(action: (XC_MethodHook.MethodHookParam) -> Unit): XC_MethodHook =
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    action(param)
                } catch (t: Throwable) {
                    XLog.e("[$id] ошибка в beforeHookedMethod", t)
                }
            }
        }

    private fun afterCallback(action: (XC_MethodHook.MethodHookParam) -> Unit): XC_MethodHook =
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    action(param)
                } catch (t: Throwable) {
                    XLog.e("[$id] ошибка в afterHookedMethod", t)
                }
            }
        }

    private fun replaceCallback(action: (XC_MethodHook.MethodHookParam) -> Any?): XC_MethodHook =
        object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? = try {
                action(param)
            } catch (t: Throwable) {
                XLog.e("[$id] ошибка в replaceHookedMethod", t)
                null
            }
        }
}
