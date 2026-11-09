package com.skb8.vivotool.hooks.camera

import com.skb8.vivotool.core.XLog
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Android 16 camera targets. No obfuscated UI class or method names are stored. */
internal object CameraHorizonDexTargets {
    private const val FEATURE_PACKAGE = "com.android.camera.featureconfig"
    private const val HORIZON_TIPS = "FUNCTION_TYPE_VIDEO_SUPER_EIS_HORIZON"
    private const val STABLE_VERSION = "recordStableVersion"

    data class Targets(
        val tips: Method,
        val versions: List<Method>,
        val callers: List<Method>
    )

    private var cachedLoader: ClassLoader? = null
    private var cached: Targets? = null

    @Synchronized
    fun get(loader: ClassLoader): Targets {
        if (cachedLoader === loader) return requireNotNull(cached)
        System.loadLibrary("dexkit")
        val started = System.nanoTime()
        val targets = DexKitBridge.create(loader, false).use { bridge ->
            // Match the horizon function and its dedicated boolean show/hide signature,
            // regardless of the UI package/method names chosen by obfuscation.
            val tipsCandidates = bridge.findMethod {
                matcher {
                    paramTypes("boolean")
                    returnType = "void"
                    usingEqStrings(listOf(HORIZON_TIPS))
                    invokeMethods {
                        add {
                            declaredClass("com.android.camera.res.standardization.helptipsview.HelpTipsParam")
                            name = "withFunctionType"
                            paramTypes("java.lang.String")
                        }
                    }
                }
            }.filter { !Modifier.isStatic(it.modifiers) && !Modifier.isAbstract(it.modifiers) }
            check(tipsCandidates.size == 1) {
                "Horizon tips: expected one method, found ${tipsCandidates.map { it.descriptor }}"
            }
            val tips = tipsCandidates.single()

            // FeatureConfig chooses the real model/platform using the camera's own factory.
            // Do not assume Build.PRODUCT matches the selected FeatureConfig_meat_* name.
            val facade = Class.forName("$FEATURE_PACKAGE.FeatureConfig", false, loader)
            val active = requireNotNull(facade.getField("instance").get(null))
            val hierarchy = generateSequence<Class<*>>(active.javaClass) { it.superclass }
                .map { it.name }.toSet()
            val declarations = bridge.findMethod {
                searchPackages(FEATURE_PACKAGE)
                matcher {
                    name = STABLE_VERSION
                    paramCount = 0
                    returnType = "int"
                }
            }.filter { !Modifier.isStatic(it.modifiers) }
            val implementations = declarations.filter {
                it.className in hierarchy && !Modifier.isAbstract(it.modifiers)
            }
            check(implementations.isNotEmpty()) { "No $STABLE_VERSION implementation in $hierarchy" }

            // Calls usually target the IMeatFeatureConfig interface, rather than the concrete
            // model implementation. Include interface call sites to defeat ART inlining.
            val relevantDeclarations = declarations.filter {
                it.className in hierarchy || Modifier.isAbstract(it.modifiers)
            }
            val patchedDescriptors = implementations.map { it.descriptor }.toSet() + tips.descriptor
            val callers = (relevantDeclarations.flatMap { it.callers } + tips.callers)
                .filter { it.isMethod && !Modifier.isAbstract(it.modifiers) &&
                    !Modifier.isNative(it.modifiers) && it.descriptor !in patchedDescriptors }
                .distinctBy { it.descriptor }
            Targets(
                tips.getMethodInstance(loader).also { it.isAccessible = true },
                implementations.map { it.getMethodInstance(loader).also { method -> method.isAccessible = true } }
                    .distinct(),
                callers.map { it.getMethodInstance(loader).also { method -> method.isAccessible = true } }
            )
        }
        // Reflection results are valid only for this loader/process. A camera update starts
        // a new process and scans the new DEX; close the native bridge after each resolution.
        cachedLoader = loader
        cached = targets
        XLog.i("[camera_horizon_dexkit] tips: ${targets.tips.toGenericString()}")
        XLog.i("[camera_horizon_dexkit] versions: ${targets.versions.joinToString { it.toGenericString() }}")
        XLog.i("[camera_horizon_dexkit] resolved in ${(System.nanoTime() - started) / 1_000_000} ms")
        return targets
    }
}
