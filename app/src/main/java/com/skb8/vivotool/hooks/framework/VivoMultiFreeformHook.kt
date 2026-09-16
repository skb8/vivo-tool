package com.skb8.vivotool.hooks.framework

import android.os.IBinder
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.HookPrefs
import com.skb8.vivotool.core.XLog
import de.robv.android.xposed.XposedHelpers
import java.util.Collections
import java.util.WeakHashMap

/**
 * Включение одновременного открытия плавающих окон (Freeform) от 2 до 10.
 *
 * В OriginOS на обычных смартфонах установлено ограничение в максимум 1 активное
 * плавающее окно: при открытии второго окна предыдущее принудительно сворачивается в док
 * в методе `com.android.server.wm.VivoFreeformTaskController.minimizeCurrentVisibleFreeformTaskIfNeed`.
 *
 * При лимите 2 хук активирует нативную логику Fold (`VivoFreeformUtils.notSupportMultiVisibleFreeform -> false`).
 * При лимите от 3 до 10 хук перехватывает `minimizeCurrentVisibleFreeformTaskIfNeed`
 * и позволяет держать открытыми до выбранного пользователем количества окон.
 */
object VivoMultiFreeformHook : BaseHook() {

    const val ID = "framework_multi_freeform"

    private const val UTILS_CLASS = "com.android.server.wm.VivoFreeformUtils"
    private const val CONTROLLER_CLASS = "com.android.server.wm.VivoFreeformTaskController"

    override val id: String = ID
    override val titleRes: Int = R.string.hook_framework_multi_freeform_title
    override val descriptionRes: Int = R.string.hook_framework_multi_freeform_description

    override val targetPackages: Set<String> = setOf(Constants.SYSTEM_FRAMEWORK, "system_server")

    override val enabledByDefault: Boolean = true

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
        if (hookedLoaders.contains(loader)) return

        var hookedAny = false

        // 1. Нативная поддержка Fold (база для 2 окон, жестов и позиционирования)
        val utilsClass = findClassOrNull(UTILS_CLASS, loader)
        if (utilsClass != null) {
            val unhooks = utilsClass.replaceAll("notSupportMultiVisibleFreeform") {
                false
            }
            if (unhooks.isNotEmpty()) {
                hookedAny = true
                XLog.i("[$id] VivoFreeformUtils.notSupportMultiVisibleFreeform подменён на false")
            }
        }

        // 2. Расширение лимита от 2 до 10 окон через VivoFreeformTaskController
        val controllerClass = findClassOrNull(CONTROLLER_CLASS, loader)
        if (controllerClass != null) {
            val unhooks = controllerClass.hookAllBefore("minimizeCurrentVisibleFreeformTaskIfNeed") { param ->
                val limit = HookPrefs.getFreeformLimit()
                if (limit <= 2) {
                    // При лимите 2 оставляем нативную логику Fold
                    return@hookAllBefore
                }

                val tasks = param.thisObject.fieldOrNull("mVivoFreeformTasks") as? List<*>
                    ?: return@hookAllBefore

                // Подсчитываем текущие видимые задачи
                val visibleList = mutableListOf<Pair<Any, Any>>() // (Task, TopActivity)
                for (i in tasks.indices.reversed()) {
                    val vTask = tasks[i] ?: continue
                    val task = vTask.callOrNull("getTask") ?: continue
                    val isVisible = param.thisObject.callOrNull("isVisibleFreeformTask", task) as? Boolean ?: false
                    if (isVisible) {
                        val topActivity = task.callOrNull("getTopMostActivity")
                        if (topActivity != null) {
                            visibleList.add(Pair(task, topActivity))
                        }
                    }
                }

                val startingFreeform = param.args.firstOrNull() as? Boolean ?: false
                val threshold = if (startingFreeform) limit - 1 else limit

                if (visibleList.size <= threshold) {
                    // Окон меньше лимита — отменяем принудительное сворачивание
                    param.result = null
                    return@hookAllBefore
                }

                // Лимит превышен: сворачиваем самое старое окно (находящееся в конце списка)
                val excess = visibleList.size - threshold
                val wmService = param.thisObject.fieldOrNull("mWmService")
                val atmService = wmService?.fieldOrNull("mAtmService")

                for (idx in 0 until excess) {
                    val targetIndex = visibleList.size - 1 - idx
                    if (targetIndex in visibleList.indices) {
                        val (task, topActivity) = visibleList[targetIndex]
                        try {
                            XposedHelpers.setBooleanField(task, "mFreeFormLayerBoost", false)
                        } catch (_: Throwable) {}
                        val appToken = topActivity.fieldOrNull("appToken") as? IBinder
                        if (appToken != null) {
                            atmService?.callOrNull("miniMizeWindowVivoFreeformMode", appToken, true)
                        }
                    }
                }

                param.result = null
            }
            if (unhooks.isNotEmpty()) {
                hookedAny = true
                XLog.i("[$id] VivoFreeformTaskController.minimizeCurrentVisibleFreeformTaskIfNeed хукнут (лимит 2..10)")
            }
        }

        if (hookedAny) {
            hookedLoaders.add(loader)
        }
    }
}
