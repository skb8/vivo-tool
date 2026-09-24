# Модуль обращается к части своих классов по имени: из LSPosed (точка входа)
# и из собственных хуков через рефлексию. Такие классы переименовывать нельзя.

# Точка входа: имя записано в META-INF/xposed/java_init.list и assets/xposed_init.
-keep class com.skb8.vivotool.core.HookEntry { *; }

# Методы подменяются по имени в HookEntry.hookSelf.
-keep class com.skb8.vivotool.core.ModuleStatus { *; }

# Хуки создаются как object и вызываются из HookModules, но onHook()
# дёргается через рефлексию Xposed — оставляем целиком.
-keep class * extends com.skb8.vivotool.core.BaseHook { *; }

# LibXposed API есть только в рантайме (compileOnly), поэтому R8 не должен
# ругаться на отсутствующие классы интерфейсов и хелперов.
-dontwarn io.github.libxposed.annotation.**
-keep class io.github.libxposed.api.** { *; }
-dontwarn io.github.libxposed.api.**

# Читаемые стектрейсы в журнале LSPosed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
