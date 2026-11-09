package com.skb8.vivotool.hooks.player

import android.app.Application
import android.content.Context
import com.skb8.vivotool.core.XLog
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Discover whitelist roles through settings keys and call/field relationships. */
internal object PlayerIslandDexTargets {
    data class Targets(
        val predicates: List<Method>,
        val lists: List<Method>,
        val callers: List<Method>
    )

    private var cachedLoader: ClassLoader? = null
    private var cached: Targets? = null

    @Synchronized
    fun get(loader: ClassLoader): Targets {
        if (cachedLoader === loader) return requireNotNull(cached)
        System.loadLibrary("dexkit")
        val started = System.nanoTime()
        val targets = DexKitBridge.create(loader, false).use { Resolver(it, loader).resolve() }
        cachedLoader = loader
        cached = targets
        XLog.i("[origin_player_dexkit] predicates: ${targets.predicates.joinToString()}")
        XLog.i("[origin_player_dexkit] list getters: ${targets.lists.joinToString()}")
        XLog.i("[origin_player_dexkit] resolved in ${(System.nanoTime() - started) / 1_000_000} ms")
        return targets
    }

    private class Resolver(val bridge: DexKitBridge, val loader: ClassLoader) {
        private fun one(label: String, configure: MethodMatcher.() -> Unit): MethodData =
            unique(label, bridge.findMethod { matcher(configure) })

        private fun unique(label: String, methods: List<MethodData>): MethodData {
            check(methods.size == 1) {
                "$label: expected one method, found ${methods.map { it.descriptor }}"
            }
            return methods.single()
        }

        private fun MethodData.reflect(): Method =
            getMethodInstance(loader).also { it.isAccessible = true }

        private fun defaultList(label: String, method: MethodData, owner: String? = null): MethodData =
            unique(label, method.invokes.filter {
                it.isMethod && !Modifier.isStatic(it.modifiers) && it.paramCount == 0 &&
                    it.returnTypeName == "java.util.List" && (owner == null || it.className == owner)
            })

        private fun applicationList(label: String, method: MethodData): Field {
            val fields = method.usingFields.map { it.field }.filter {
                it.typeName == "java.util.List" && Modifier.isStatic(it.modifiers)
            }.distinctBy { it.descriptor }
            check(fields.size == 1) { "$label: ambiguous static list: ${fields.map { it.descriptor }}" }
            return fields.single().getFieldInstance(loader).also { it.isAccessible = true }
        }

        fun resolve(): Targets {
            val resident = one("resident audio whitelist") {
                paramTypes(Context::class.java.name, "java.lang.String")
                returnType = "boolean"
                usingEqStrings(listOf("resident_music_app_white_list"))
            }
            check(Modifier.isStatic(resident.modifiers))
            val audioDefault = defaultList("default audio list", resident)
            val manager = audioDefault.className

            // The other predicate iterates an Application list and falls back to the same
            // default audio list. Wrapping contains() alone cannot bypass these loops.
            val audio = unique("audio stream admission", bridge.findMethod {
                matcher {
                    declaredClass(resident.className)
                    paramTypes(Context::class.java.name, "java.lang.String")
                    returnType = "boolean"
                    invokeMethods { add { descriptor = audioDefault.descriptor } }
                }
            }.filter { it.descriptor != resident.descriptor })
            check(Modifier.isStatic(audio.modifiers))

            val islandUpdate = one("island package update") {
                paramTypes("java.lang.String")
                returnType = "void"
                usingStrings("IslandNotificationManager", "updatePackageName not in whitelist")
            }
            val islandDefault = defaultList("default island list", islandUpdate, manager)
            val widgets = one("cooperating players list") {
                declaredClass(resident.className)
                paramTypes(Context::class.java.name)
                returnType = "java.util.List"
                invokeMethods {
                    add { declaredClass(manager); paramCount = 0; returnType = "java.util.List" }
                }
            }
            val widgetDefault = defaultList("default widget list", widgets, manager)
            check(setOf(audioDefault.descriptor, islandDefault.descriptor, widgetDefault.descriptor).size == 3) {
                "Audio, island and widget defaults must be three distinct lists"
            }
            val lock = one("lock-screen players list") {
                declaredClass(resident.className)
                paramTypes(Context::class.java.name)
                returnType = "java.util.List"
                usingEqStrings(listOf("lock_music_app_white_list"))
            }

            val fields = listOf(applicationList("audio", audio), applicationList("widgets", widgets))
            check(fields.distinct().size == 2 && fields.map { it.declaringClass }.distinct().size == 1)
            val application = fields.first().declaringClass
            check(Application::class.java.isAssignableFrom(application))
            XLog.i("[origin_player_dexkit] source fields (read only): ${fields.joinToString()}")

            val predicates = listOf(audio, resident)
            val lists = listOf(audioDefault, islandDefault, widgetDefault, widgets, lock)
            val patched = (predicates + lists).map { it.descriptor }.toSet()
            val callers = (predicates + lists).flatMap { it.callers }
                .filter { it.isMethod && !Modifier.isAbstract(it.modifiers) &&
                    !Modifier.isNative(it.modifiers) && it.descriptor !in patched }
                .distinctBy { it.descriptor }
            return Targets(predicates.map { it.reflect() }, lists.map { it.reflect() },
                callers.map { it.reflect() })
        }
    }
}
