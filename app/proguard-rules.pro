# Шринкинг и обфускация отключены (см. app/build.gradle.kts),
# но правила оставлены на случай включения minify.

# Точка входа модуля и классы, к которым обращаются по имени.
-keep class com.skb8.vivotool.core.HookEntry { *; }
-keep class com.skb8.vivotool.core.ModuleStatus { *; }
-keep class * extends com.skb8.vivotool.core.BaseHook { *; }

# Xposed API предоставляется фреймворком.
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
