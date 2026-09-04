# Модуль обращается к части своих классов по имени: из LSPosed (точка входа)
# и из собственных хуков через рефлексию. Такие классы переименовывать нельзя.

# Точка входа: имя записано в META-INF/xposed/java_init.list и assets/xposed_init.
-keep class com.skb8.vivotool.core.HookEntry { *; }

# Методы подменяются по имени в HookEntry.hookSelf.
-keep class com.skb8.vivotool.core.ModuleStatus { *; }

# Хуки создаются как object и вызываются из HookModules, но onHook()
# дёргается через рефлексию Xposed — оставляем целиком.
-keep class * extends com.skb8.vivotool.core.BaseHook { *; }

# Xposed API есть только в рантайме (compileOnly), поэтому R8 не должен
# ругаться на отсутствующие классы интерфейсов и хелперов.
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Читаемые стектрейсы в журнале LSPosed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
