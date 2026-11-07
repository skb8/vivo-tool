package com.skb8.vivotool.hooks.framework

import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.HookPrefs
import com.skb8.vivotool.core.SystemProperties
import com.skb8.vivotool.core.XLog
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

/**
 * Android 16 keeps freeform tasks per display in mVivoFreeformTasksAllDisplays.
 * Its native minimization code reads getVivoFreeformTasksMaxShowNum(displayId, area)
 * and handles the pending task, task order, transitions and ActivityRecord.token itself.
 * Raise that quota instead of replacing minimization with the Android 15 list algorithm.
 */
object VivoMultiFreeformAndroid16Hook : BaseHook() {
    // Same preference and slider as Android 15; only one implementation is registered.
    override val id: String = VivoMultiFreeformHook.ID
    override val titleRes: Int = R.string.hook_framework_multi_freeform_title
    override val descriptionRes: Int = R.string.hook_framework_multi_freeform_description
    override val targetPackages = setOf(Constants.SYSTEM_FRAMEWORK, "system_server")
    override val enabledByDefault = true

    override fun isSupported(sdkInt: Int): Boolean =
        super.isSupported(sdkInt) && SystemProperties.releaseVersionMajor >= 16

    private const val UTILS = "com.android.server.wm.VivoFreeformUtils"
    private const val CONTROLLER = "com.android.server.wm.VivoFreeformTaskController"
    private const val DISPLAY_AREA = "com.android.server.wm.TaskDisplayArea"

    // Deduplicate by the defining Class, including when different loaders delegate to it.
    private val visited = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    private val discovering = ThreadLocal<Boolean>()
    @Volatile private var utilsInstalled = false
    @Volatile private var controllerInstalled = false

    override fun onHook() {
        discover(classLoader)
        if (utilsInstalled && controllerInstalled) return

        // Vendor WindowManager classes can arrive in a separate services class loader.
        findClassOrNull("java.lang.ClassLoader")?.hookAfter(
            "loadClass", String::class.java, Boolean::class.javaPrimitiveType
        ) { param ->
            val name = param.args[0] as? String ?: return@hookAfter
            if (name == UTILS || name == CONTROLLER) {
                (param.result as? Class<*>)?.let(::install)
            }
        }
        findClassOrNull("dalvik.system.BaseDexClassLoader")?.hookAllConstructorsAfter { param ->
            (param.thisObject as? ClassLoader)?.let(::discover)
        }
        discover(classLoader)
    }

    private fun discover(loader: ClassLoader) {
        if ((utilsInstalled && controllerInstalled) || discovering.get() == true) return
        discovering.set(true)
        try {
            findClassOrNull(UTILS, loader)?.let(::install)
            findClassOrNull(CONTROLLER, loader)?.let(::install)
        } finally {
            discovering.remove()
        }
    }

    private fun install(clazz: Class<*>) {
        // Mark before inspecting methods: resolving their types can call loadClass again.
        if (!synchronized(visited) { visited.add(clazz) }) return
        try {
            when (clazz.name) {
                UTILS -> {
                    val method = clazz.declaredMethods.single {
                        it.name == "notSupportMultiVisibleFreeform" &&
                            Modifier.isStatic(it.modifiers) && it.returnType == Boolean::class.javaPrimitiveType &&
                            it.parameterTypes.map { type -> type.name } == listOf(DISPLAY_AREA)
                    }
                    requireNotNull(method.replaceWithConstant(false))
                    utilsInstalled = true
                    XLog.i("[$id] Android 16: native support for two visible freeform windows enabled")
                }
                CONTROLLER -> {
                    val method = clazz.declaredMethods.single {
                        it.name == "getVivoFreeformTasksMaxShowNum" &&
                            !Modifier.isStatic(it.modifiers) && it.returnType == Int::class.javaPrimitiveType &&
                            it.parameterTypes.map { type -> type.name } == listOf("int", DISPLAY_AREA)
                    }
                    requireNotNull(clazz.hookBefore(method.name, *method.parameterTypes) { param ->
                        val limit = HookPrefs.getFreeformLimit()
                        if (limit > 2 && param.args[0] == 0 && param.args[1] != null) {
                            param.result = limit
                        }
                    })
                    controllerInstalled = true
                    // ART may have inlined the small quota getter into precompiled callers.
                    // Deoptimize only those callers so they reach the hooked getter again.
                    clazz.declaredMethods.filter {
                        it.name == "minimizeCurrentVisibleFreeformTaskIfNeed" ||
                            it.name == "minimizeGameFreeformIfNeed"
                    }.forEach { caller ->
                        runCatching {
                            if (xposed?.deoptimize(caller) != true) {
                                XLog.w("[$id] Android 16: could not deoptimize ${caller.name}")
                            }
                        }.onFailure { XLog.e("[$id] Android 16: deoptimize ${caller.name}", it) }
                    }
                    XLog.i("[$id] Android 16: native visible-window quota follows the 2..10 slider")
                }
            }
        } catch (t: Throwable) {
            synchronized(visited) { visited.remove(clazz) }
            XLog.e("[$id] Android 16: could not hook ${clazz.name}", t)
        }
    }
}
