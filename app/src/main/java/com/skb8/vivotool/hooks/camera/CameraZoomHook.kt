package com.skb8.vivotool.hooks.camera

import android.util.Range
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.HookPrefs
import com.skb8.vivotool.core.XLog
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Снятие ограничения максимального цифрового зума.
 *
 * Прошивка режет зум сразу на четырёх уровнях, поэтому и хуков четыре:
 *  1. менеджеры зума режимов (`configZoomRange`) — там видео обрезается
 *     до 10x/15x, а с HDR или стабилизацией и вовсе до 2x/5x;
 *  2. лимиты прошивки в `FeatureConfig` (`getRecordingZoomRangeUpper`,
 *     `limitedMaxZoom`) — их спрашивают при старте записи;
 *  3. диапазон, который запрашивает UI зума (`getZoomRange`, `getZoomUIRange`),
 *     иначе предел вернётся при нажатии «Запись»;
 *  4. зашитый предел шкалы 100x в виджетах линейки зума — без него ползунок
 *     не докручивается дальше 100x, даже если пинчем зум работает.
 *
 * Значение читается один раз при старте процесса камеры, поэтому после
 * изменения нужно закрыть камеру (force stop).
 */
object CameraZoomHook : BaseHook() {

    const val ID = "camera_zoom"

    /** Ключ настройки с максимальным зумом. */
    const val MAX_ZOOM_KEY = "camera_max_zoom"

    /** По умолчанию — 120x, максимум цифрового зума в режиме фото. */
    const val DEFAULT_MAX_ZOOM = 120f

    /** Разумные границы значения: меньше 2x бессмысленно, больше 1000x прошивка не переварит. */
    const val MIN_MAX_ZOOM = 2f
    const val MAX_MAX_ZOOM = 1000f

    /** Глубина обхода полей объекта в поисках диапазонов зума. */
    private const val MAX_DEPTH = 2

    private const val CAMERA_CLASS_PREFIX = "com.android.camera."

    /**
     * Методы, у которых нужно поднять верхнюю границу зума — и в результате,
     * и в аргументах: `configZoomRange` не возвращает диапазон, а складывает
     * его в переданный `ZoomConfig`.
     */
    private val RANGE_METHODS = listOf(
        "com.android.camera.setting.SettingFunctionHook" to "configZoomRange",
        "com.android.camera.setting.BaseFunctionZoomManager" to "configZoomRange",
        "com.android.camera.normalvideo.setting.NormalVideoZoomManager" to "configZoomRange",
        "com.android.camera.ui.commonui.zoomui.base.ReadContext" to "getZoomRange",
        "com.android.camera.ui.commonui.zoomui.base.BaseZoomModelImpl" to "getZoomUIRange",
        "com.android.camera.ui.commonui.zoomui.BaseZoomModelImpl" to "getZoomUIRange"
    )

    /** Методы прошивки, отдающие предел зума при записи видео. */
    private val LIMIT_METHODS = listOf("getRecordingZoomRangeUpper", "limitedMaxZoom")

    /** Виджеты линейки зума с зашитым пределом шкалы. */
    private val RULERS = listOf(
        // mMaxNumber хранит зум, умноженный на 100.
        Ruler(
            className = "com.android.camera.ui.commonui.zoomui.widget.scrollruler.ZoomScrollRuler",
            fieldName = "mMaxNumber",
            scale = 100f
        ),
        Ruler(
            className = "com.android.camera.ui.commonui.zoomui.widget.ZoomCircleRuler",
            fieldName = "maxZoom",
            scale = 1f
        )
    )

    private class Ruler(val className: String, val fieldName: String, val scale: Float)

    override val id: String = ID

    override val titleRes: Int = R.string.hook_camera_zoom_title

    override val descriptionRes: Int = R.string.hook_camera_zoom_description

    override val targetPackages: Set<String> = setOf(CameraFeatureConfigHook.CAMERA_PACKAGE)

    /** Твик меняет поведение камеры, поэтому включается только вручную. */
    override val enabledByDefault: Boolean = false

    /** Приводит значение к разумным границам. */
    fun clamp(value: Float): Float = value.coerceIn(MIN_MAX_ZOOM, MAX_MAX_ZOOM)

    override fun onHook() {
        val max = clamp(HookPrefs.getFloat(MAX_ZOOM_KEY, DEFAULT_MAX_ZOOM))
        XLog.i("[$id] максимальный зум: $max")

        widenRangeMethods(max)
        liftFirmwareLimits(max)
        liftRulerLimits(max)
    }

    // ---------------------------------------------------------------------
    // 1 и 3: диапазоны зума в логике режимов и в UI
    // ---------------------------------------------------------------------

    private fun widenRangeMethods(max: Float) {
        val patched = mutableSetOf<Method>()
        RANGE_METHODS.forEach { (className, methodName) ->
            val clazz = findClassOrNull(className)
            if (clazz == null) {
                XLog.d("[$id] класса $className в этой прошивке нет")
                return@forEach
            }

            val found = clazz.methodsInHierarchy(methodName)
            if (found.isEmpty()) {
                XLog.w("[$id] ${clazz.simpleName}: метод $methodName не найден")
                return@forEach
            }

            found.filter { patched.add(it) }.forEach { method ->
                val hooked = method.hookAfter { param ->
                    if (param.hasThrowable() || isFrontCamera(param)) return@hookAfter
                    widen(param.result, max)?.let { param.result = it }
                    param.args?.forEach { widen(it, max) }
                }
                if (hooked != null) {
                    XLog.i("[$id] перехвачен ${method.declaringClass.simpleName}.$methodName")
                }
            }
        }
    }

    /**
     * Относится ли вызов к фронтальной камере: у неё оптики нет и 120x
     * превращаются в кашу, поэтому её предел не трогаем.
     *
     * Имён параметров в рантайме нет, поэтому `isFront` опознаём только по
     * известной сигнатуре `configZoomRange(ZoomConfig, String, boolean, String)`.
     * Если в прошивке сигнатура другая — снимаем предел для всех камер.
     */
    private fun isFrontCamera(param: XC_MethodHook.MethodHookParam): Boolean {
        val types = (param.method as? Method)?.parameterTypes ?: return false
        val known = types.size == 4 &&
            types[1] == String::class.java &&
            types[2] == Boolean::class.javaPrimitiveType &&
            types[3] == String::class.java
        return known && param.args?.getOrNull(2) == true
    }

    /**
     * Поднимает верхнюю границу диапазонов зума внутри значения.
     *
     * Диапазон неизменяемый, поэтому для него возвращается замена. Словари,
     * списки и поля объектов правятся на месте — тогда возвращается null.
     */
    private fun widen(value: Any?, max: Float, depth: Int = 0): Range<Float>? {
        when (value) {
            null -> return null

            is Range<*> -> return widenRange(value, max)

            is MutableMap<*, *> -> {
                if (depth >= MAX_DEPTH) return null
                @Suppress("UNCHECKED_CAST")
                val map = value as MutableMap<Any?, Any?>
                map.keys.toList().forEach { key ->
                    val replacement = widen(map[key], max, depth + 1) ?: return@forEach
                    write("диапазон для '$key'") { map[key] = replacement }
                }
                return null
            }

            is MutableList<*> -> {
                if (depth >= MAX_DEPTH) return null
                @Suppress("UNCHECKED_CAST")
                val list = value as MutableList<Any?>
                list.indices.forEach { index ->
                    val replacement = widen(list[index], max, depth + 1) ?: return@forEach
                    write("диапазон [$index]") { list[index] = replacement }
                }
                return null
            }

            else -> {
                // Обходим только классы камеры: в чужих объектах Range<Float>
                // может означать что угодно, например выдержку.
                if (depth >= MAX_DEPTH) return null
                if (!value.javaClass.name.startsWith(CAMERA_CLASS_PREFIX)) return null
                widenFields(value, max, depth + 1)
                return null
            }
        }
    }

    private fun widenRange(range: Range<*>, max: Float): Range<Float>? {
        val lower = range.lower as? Float ?: return null
        val upper = range.upper as? Float ?: return null
        if (lower >= max || upper >= max) return null
        return Range(lower, max)
    }

    private fun widenFields(target: Any, max: Float, depth: Int) {
        val fields = try {
            target.javaClass.declaredFields
        } catch (t: Throwable) {
            XLog.d("[$id] не удалось прочитать поля ${target.javaClass.name}: ${t.message}")
            return
        }

        fields.forEach { field ->
            if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) return@forEach
            val current = try {
                field.isAccessible = true
                field.get(target)
            } catch (t: Throwable) {
                return@forEach
            }
            val replacement = widen(current, max, depth) ?: return@forEach
            write("${target.javaClass.simpleName}.${field.name}") {
                field.set(target, replacement)
            }
        }
    }

    private inline fun write(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            XLog.d("[$id] $what не поддаётся записи: ${t.message}")
        }
    }

    // ---------------------------------------------------------------------
    // 2: лимиты прошивки при записи видео
    // ---------------------------------------------------------------------

    private fun liftFirmwareLimits(max: Float) {
        val classes = limitClasses()
        if (classes.isEmpty()) {
            XLog.w("[$id] классы FeatureConfig не найдены, лимиты записи останутся")
            return
        }

        val patched = mutableSetOf<Method>()
        classes.forEach { clazz ->
            LIMIT_METHODS.forEach { name ->
                clazz.methodsInHierarchy(name).filter { patched.add(it) }.forEach { method ->
                    // Возвращаем сам предел, а не -1: если прошивка не проверяет
                    // «нет лимита», а просто берёт минимум, -1 обнулит зум.
                    val value = numeric(method, max)
                    if (value == null) {
                        XLog.w("[$id] $name: тип результата ${method.returnType} не число")
                        return@forEach
                    }
                    if (method.replaceWithConstant(value) != null) {
                        XLog.i("[$id] ${method.declaringClass.simpleName}.$name -> $value")
                    }
                }
            }
        }
        if (patched.isEmpty()) XLog.w("[$id] методы с лимитами зума не найдены")
    }

    private fun limitClasses(): List<Class<*>> {
        val names = buildList {
            add("${CameraFeatureConfigHook.CONFIG_PACKAGE}.FeatureConfig_common")
            CameraFeatureConfigHook.Source.entries.forEach {
                addAll(CameraFeatureConfigHook.classCandidates(it))
            }
        }
        return names.distinct().mapNotNull { findClassOrNull(it) }.distinct()
    }

    /** Значение [value] в типе результата метода. */
    private fun numeric(method: Method, value: Float): Any? = when (method.returnType) {
        Float::class.javaPrimitiveType -> value
        Double::class.javaPrimitiveType -> value.toDouble()
        Int::class.javaPrimitiveType -> value.toInt()
        Long::class.javaPrimitiveType -> value.toLong()
        else -> null
    }

    // ---------------------------------------------------------------------
    // 4: шкала линейки зума
    // ---------------------------------------------------------------------

    private fun liftRulerLimits(max: Float) {
        RULERS.forEach { ruler ->
            val clazz = findClassOrNull(ruler.className)
            if (clazz == null) {
                XLog.d("[$id] линейки ${ruler.className} в этой прошивке нет")
                return@forEach
            }

            val field = clazz.findFieldOrNull(ruler.fieldName)
            if (field == null) {
                XLog.w("[$id] ${clazz.simpleName}: поле ${ruler.fieldName} не найдено")
                return@forEach
            }

            val value = fieldValue(field, max * ruler.scale)
            if (value == null) {
                XLog.w("[$id] ${clazz.simpleName}.${field.name}: тип ${field.type} не число")
                return@forEach
            }

            val patch: (XC_MethodHook.MethodHookParam) -> Unit = { param ->
                param.thisObject?.let { target ->
                    write("${clazz.simpleName}.${field.name}") { field.set(target, value) }
                }
            }

            // Шкала пересчитывается там, где виджету отдают список значений зума,
            // поэтому поле переписываем после каждого такого метода и после
            // конструктора — на случай, если список пришёл сразу в него.
            clazz.hookAllConstructorsAfter(patch)
            val setters = clazz.declaredMethods.filter(::takesList)
            setters.forEach { method -> method.hookAfter(patch) }

            if (setters.isEmpty()) {
                XLog.w("[$id] ${clazz.simpleName}: методы со списком зумов не найдены")
            } else {
                XLog.i("[$id] ${clazz.simpleName}.${field.name} = $value, методов: ${setters.size}")
            }
        }
    }

    private fun takesList(method: Method): Boolean =
        !Modifier.isAbstract(method.modifiers) &&
            method.parameterTypes.any { List::class.java.isAssignableFrom(it) }

    private fun fieldValue(field: Field, value: Float): Any? = when (field.type) {
        Float::class.javaPrimitiveType -> value
        Double::class.javaPrimitiveType -> value.toDouble()
        Int::class.javaPrimitiveType -> value.toInt()
        Long::class.javaPrimitiveType -> value.toLong()
        else -> null
    }
}
