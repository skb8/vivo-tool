package com.skb8.vivotool.core

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/*
 * Короткие обёртки над XposedHelpers, чтобы код хуков читался как обычный Kotlin.
 */

/** Значение поля объекта по имени. */
fun Any.getField(name: String): Any? = XposedHelpers.getObjectField(this, name)

/** Значение поля объекта с приведением типа; null, если тип не совпал. */
@Suppress("UNCHECKED_CAST")
fun <T> Any.getFieldAs(name: String): T? = XposedHelpers.getObjectField(this, name) as? T

/** Записать значение в поле объекта. */
fun Any.setField(name: String, value: Any?) = XposedHelpers.setObjectField(this, name, value)

/** Вызвать метод объекта. */
fun Any.callMethod(name: String, vararg args: Any?): Any? =
    XposedHelpers.callMethod(this, name, *args)

/** Вызвать метод объекта с явным указанием типов параметров. */
fun Any.callMethodExact(name: String, parameterTypes: Array<Class<*>>, vararg args: Any?): Any? =
    XposedHelpers.callMethod(this, name, parameterTypes, *args)

/** Значение статического поля класса. */
fun Class<*>.getStaticField(name: String): Any? = XposedHelpers.getStaticObjectField(this, name)

/** Записать значение в статическое поле класса. */
fun Class<*>.setStaticField(name: String, value: Any?) =
    XposedHelpers.setStaticObjectField(this, name, value)

/** Вызвать статический метод класса. */
fun Class<*>.callStaticMethod(name: String, vararg args: Any?): Any? =
    XposedHelpers.callStaticMethod(this, name, *args)

/** Создать экземпляр класса. */
fun Class<*>.newInstance(vararg args: Any?): Any = XposedHelpers.newInstance(this, *args)

/** Аргумент хукнутого метода по индексу с приведением типа. */
@Suppress("UNCHECKED_CAST")
fun <T> XC_MethodHook.MethodHookParam.arg(index: Int): T? = args.getOrNull(index) as? T

/** Заменить аргумент хукнутого метода. */
fun XC_MethodHook.MethodHookParam.setArg(index: Int, value: Any?) {
    args[index] = value
}

/** Результат хукнутого метода с приведением типа. */
@Suppress("UNCHECKED_CAST")
fun <T> XC_MethodHook.MethodHookParam.resultAs(): T? = result as? T
