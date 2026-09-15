package com.skb8.vivotool.hooks.framework

import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.XLog
import java.util.Collections
import java.util.WeakHashMap

/**
 * Включение одновременного открытия до двух плавающих окон (Freeform).
 *
 * В OriginOS на обычных смартфонах установлено ограничение в максимум 1 активное
 * плавающее окно: при открытии второго окна предыдущее принудительно сворачивается в док
 * в методе `com.android.server.wm.VivoFreeformTaskController.minimizeCurrentVisibleFreeformTaskIfNeed`.
 *
 * Ограничение определяется проверкой:
 * `VivoFreeformUtils.notSupportMultiVisibleFreeform(TaskDisplayArea)`:
 * ```java
 * public static boolean notSupportMultiVisibleFreeform(TaskDisplayArea taskDisplayArea) {
 *     boolean isFoldSecondary = isFoldDev() && "local:secondary".equals(taskDisplayArea.mDisplayContent.getDisplayInfo().uniqueId);
 *     boolean isPhone = (isFoldDev() || sIsPadDevice) ? false : true;
 *     return isFoldSecondary || isPhone;
 * }
 * ```
 * На планшетах и складных устройствах Fold метод возвращает `false`, что задаёт
 * `maxVisibleCount = 2`.
 *
 * Данный хук переопределяет метод `notSupportMultiVisibleFreeform`, чтобы он всегда
 * возвращал `false`. Это включает нативную поддержку до 2-х плавающих окон на обычных
 * телефонах с сохранением корректного позиционирования, жестов и анимаций.
 */
object VivoMultiFreeformHook : BaseHook() {

    const val ID = "framework_multi_freeform"

    private const val UTILS_CLASS = "com.android.server.wm.VivoFreeformUtils"
    private const val CONTROLLER_CLASS = "com.android.server.wm.VivoFreeformTaskController"

    override val id: String = ID
    override val titleRes: Int = R.string.hook_framework_multi_freeform_title
    override val descriptionRes: Int = R.string.hook_framework_multi_freeform_description

    override val targetPackages: Set<String> = setOf(Constants.SYSTEM_FRAMEWORK)

    override val enabledByDefault: Boolean = false

    private val hookedLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )

    override fun onHook() {
        hookClassLoader(classLoader)

        // Перехватываем создание загрузчиков классов в system_server
        findClassOrNull("dalvik.system.BaseDexClassLoader")?.hookAllConstructorsAfter { param ->
            val loader = param.thisObject as? ClassLoader ?: return@hookAllConstructorsAfter
            hookClassLoader(loader)
        }

        // Перехватываем динамическую загрузку классов WindowManager
        findClassOrNull("java.lang.ClassLoader")?.hookAfter(
            "loadClass",
            String::class.java,
            Boolean::class.javaPrimitiveType
        ) { param ->
            val name = param.args[0] as? String ?: return@hookAfter
            if (name == UTILS_CLASS || name == CONTROLLER_CLASS) {
                val loader = param.thisObject as? ClassLoader ?: return@hookAfter
                hookClassLoader(loader)
            }
        }
    }

    private fun hookClassLoader(loader: ClassLoader) {
        if (!hookedLoaders.add(loader)) return

        val utilsClass = findClassOrNull(UTILS_CLASS, loader)
        if (utilsClass != null) {
            val unhooks = utilsClass.replaceAll("notSupportMultiVisibleFreeform") {
                XLog.d("[$id] notSupportMultiVisibleFreeform() -> false")
                false
            }
            if (unhooks.isNotEmpty()) {
                XLog.i("[$id] VivoFreeformUtils.notSupportMultiVisibleFreeform подменён на false (активно до 2 окон)")
            }
        }
    }
}
