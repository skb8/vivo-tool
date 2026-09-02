# Как писать хуки

## 1. Создать класс хука

Хук — это `object`, унаследованный от `BaseHook`. Файл кладём в
`app/src/main/java/com/skb8/vivotool/hooks/<приложение>/`.

```kotlin
package com.skb8.vivotool.hooks.systemui

import com.skb8.vivotool.core.BaseHook

object StatusBarClockHook : BaseHook() {

    override val id = "systemui_clock_seconds"          // ключ настроек, менять нельзя
    override val title = "Секунды в часах статусбара"
    override val description = "Показывает секунды рядом со временем"
    override val category = "SystemUI"                   // группа в списке приложения
    override val targetPackages = setOf("com.android.systemui")
    override val enabledByDefault = false

    override fun onHook() {
        findClass("com.android.systemui.statusbar.policy.Clock")
            .hookAfter("updateClock") { param ->
                // ...
            }
    }
}
```

Дополнительно можно переопределить:

| Свойство | Зачем |
| --- | --- |
| `minSdk` / `maxSdk` | хук применяется только на нужных версиях Android |
| `enabledByDefault` | включён ли хук до того, как пользователь что-то менял |
| `onZygote(startupParam)` | хуки на стадии Zygote (ресурсы, системные классы) |

Спецзначения для `targetPackages`:

- `Constants.SYSTEM_FRAMEWORK` (`"android"`) — процесс `system_server`;
- `Constants.ALL_PACKAGES` (`"*"`) — все процессы (использовать осторожно).

## 2. Зарегистрировать хук

`app/src/main/java/com/skb8/vivotool/hooks/HookModules.kt`:

```kotlin
val all: List<BaseHook> = listOf(
    StatusBarClockHook,
)
```

Реестр, UI со списком и переключателями подхватят хук автоматически.

## 3. Добавить пакет в область действия

`app/module-scope.txt` — одна строка на пакет:

```
com.android.systemui
```

Из этого файла при сборке генерируются `@array/module_scope` и
`META-INF/xposed/scope.list`. Если забыть про этот шаг,
LSPosed не предложит приложение в списке выбора, а на главном экране появится
предупреждение «Пакеты вне области действия».

## Хелперы для хуков

Внутри `onHook()` доступны:

```kotlin
// Поиск классов
findClass("com.example.Foo")                       // бросит исключение, если нет
findClassOrNull("com.example.Foo")                 // null, если нет
findFirstClass("com.example.FooV2", "com.example.Foo")  // первый существующий

// Хуки методов (все ошибки логируются, хук не падает целиком)
clazz.hookBefore("method", Int::class.java) { param -> param.args[0] = 0 }
clazz.hookAfter("method") { param -> param.result = true }
clazz.replace("method") { param -> "новое значение" }
clazz.returnConstant("isVip", true)
clazz.doNothing("showAd")
clazz.hookAllBefore("method") { }                  // все перегрузки
clazz.hookAllAfter("method") { }
clazz.replaceAll("method") { null }
clazz.hookConstructorBefore(Context::class.java) { }
clazz.hookConstructorAfter { }
clazz.hookAllConstructorsAfter { }

// Контекст приложения
afterApplicationCreated { app -> /* app: Application */ }

// Свойства процесса
classLoader        // ClassLoader целевого приложения
hookedPackage      // имя пакета
param              // XC_LoadPackage.LoadPackageParam
```

Рефлексия (`core/XposedExt.kt`):

```kotlin
obj.getField("mField")
obj.getFieldAs<String>("mName")
obj.setField("mField", value)
obj.callMethod("doWork", arg1, arg2)
clazz.getStaticField("sInstance")
clazz.callStaticMethod("getInstance")
clazz.newInstance(arg)
param.arg<String>(0)
param.setArg(0, "x")
param.resultAs<Int>()
```

Типы параметров при поиске метода указываются как `Int::class.java`,
`String::class.java`, `"com.example.Foo"` (строкой — если класс приватный) или
`Context::class.java`.

## Настройки хука

Каждый хук уже имеет переключатель вкл/выкл. Для дополнительных настроек:

- в UI писать через `AppSettings.setBoolean/setInt`;
- в хуке читать через `HookPrefs.getBoolean/getInt/getString`.

Настройки хранятся в world-readable `SharedPreferences`, поэтому применяются
без перезагрузки — при следующем чтении внутри хука.

## Логи

```kotlin
XLog.d("отладка")             // только logcat
XLog.i("важное")              // logcat + журнал LSPosed
XLog.e("упало", throwable)
```

Смотреть: `adb logcat -s VivoTool` или журнал в LSPosed.

## Практика

- Всегда проверяйте существование классов и методов (`findClassOrNull`,
  `findFirstClass`): в разных версиях Funtouch/OriginOS сигнатуры отличаются.
- Не бросайте исключения из хуков системных процессов — можно получить
  бутлуп. Хелперы `BaseHook` уже ловят ошибки, но логику лучше писать защищённо.
- Один хук = одна функция. Так пользователь сможет отключить именно то,
  что ему мешает.
- `id` — это ключ настроек. Переименование `id` сбросит настройку пользователя.
- Для поиска нужных классов удобно распаковать APK приложения
  (`jadx-gui`, `apktool`) и искать по строкам из UI.
