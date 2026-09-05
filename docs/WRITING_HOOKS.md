# Как писать хуки

## 1. Создать класс хука

Хук — это `object`, унаследованный от `BaseHook`. Файл кладём в
`app/src/main/java/com/skb8/vivotool/hooks/<приложение>/`.

```kotlin
package com.skb8.vivotool.hooks.systemui

import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook

object StatusBarClockHook : BaseHook() {

    override val id = "systemui_clock_seconds"          // ключ настроек, менять нельзя
    override val titleRes = R.string.hook_clock_seconds_title
    override val descriptionRes = R.string.hook_clock_seconds_description
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

Название и описание — строковые ресурсы: язык по умолчанию английский
(`res/values/strings.xml`), русский перевод — в `res/values-ru/strings.xml`.
Строку нужно завести в обоих файлах, иначе на русском покажется английский текст.

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

// Поиск членов класса рефлексией
clazz.findFieldOrNull("mMaxNumber")                // поле класса или родителя
clazz.methodsInHierarchy("isVip")                  // все реализации в иерархии
clazz.methodsInHierarchy("isVip", Boolean::class.javaPrimitiveType)

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

Свой экран настроек для твика (открывается тапом по названию в списке):

1. добавить id твика в `ui/HookDetails.kt`;
2. добавить ветку в `ui/AppRoot.kt` → `Route.HookSettings`;
3. положить composable в `ui/detail/`.

Пример — `ui/detail/AboutPhoneImageScreen.kt`. Если хук читает настройку
через `HookPrefs` на каждый вызов, изменения применяются без перезагрузки.

## Хуки системного фреймворка

Пакет `Constants.SYSTEM_FRAMEWORK` (`android`) — это `system_server`, куда
попадают классы из `vivo-services.jar` вроде `com.android.server.wm.*`.
В LSPosed для таких хуков нужно отметить «Системный фреймворк» в области
действия модуля и перезагрузить устройство.

- Имена классов и методов различаются между прошивками — ищите через
  `findFirstClass(...)` и логируйте, если ничего не нашлось.
- Чтобы отключить метод целиком, есть `skipAll("methodName")`: он подставляет
  нейтральное значение под фактический тип возврата, поэтому не упадёт
  на неизвестной сигнатуре.
- Ошибка в system_server = бутлуп, поэтому никаких исключений наружу.

Настройки хранятся в world-readable `SharedPreferences`, поэтому применяются
без перезагрузки — при следующем чтении внутри хука.

## Подмена ресурсов и картинок

Хуки ресурсов через `initPackageResources` в LSPosed ненадёжны, поэтому drawable
подменяются перехватом загрузки — см. `hooks/settings/AboutPhoneRomImageHook.kt`:

- `Resources.loadDrawable` — единая точка и для `getDrawable(id)`, и для
  `android:src` из XML-разметки;
- `Resources.openRawResource` — для `BitmapFactory.decodeResource`;
- идентификатор ресурса берётся по имени: `res.getIdentifier(name, "drawable", pkg)`;
- размер подменённой картинки подгоняется под `intrinsicWidth/Height` оригинала,
  чтобы не поехала вёрстка.

Картинку от пользователя передаём через хранилище (base64 в world-readable
`SharedPreferences`) — каталог данных модуля чужому процессу недоступен:

```kotlin
// приложение
ImageStore.save(context, ImageKeys.MY_IMAGE, bitmap)

// хук
HookImages.bitmap(ImageKeys.MY_IMAGE)   // с кэшем до следующего изменения
HookImages.bytes(ImageKeys.MY_IMAGE)    // сырые байты для openRawResource
```

Экран обрезки (`ui/CropScreen.kt`) переиспользуемый: принимает точный размер
ресурса и возвращает bitmap ровно этого размера.

## Список значений в настройках

Когда у твика не одно значение, а набор (как фичи камеры), удобно хранить их
по префиксу ключа:

```kotlin
// приложение
settings.setBoolean("camera_feature_isXxx", true)
settings.booleanEntriesWithPrefix("camera_feature_")
settings.removeWithPrefix("camera_feature_")

// хук
HookPrefs.entriesWithPrefix("camera_feature_")
```

## Чтение классов целевого приложения из UI

APK системных приложений доступен на чтение, поэтому приложение может открыть
его своим class loader'ом и посмотреть, что внутри — так собирается список фич
камеры (`settings/CameraFeatureLoader.kt`):

```kotlin
val info = packageManager.getApplicationInfo(packageName, 0)
val loader = PathClassLoader(info.sourceDir, info.nativeLibraryDir, javaClass.classLoader)
val clazz = loader.loadClass("com.example.Config")
```

Дальше обычная рефлексия: обход иерархии через `declaredMethods` и `superclass`,
вызов методов без аргументов, чтобы показать значения из прошивки. Каждый шаг
оборачивайте в `try/catch`: часть классов может не разрешиться.

Найденный через рефлексию метод подменяется в хуке напрямую:

```kotlin
method.replaceWithConstant(true)
method.hookAfter { param -> param.result = 120f }
```

Так же работает и внутри хука: `methodsInHierarchy("configZoomRange")` находит
все реализации метода, включая объявленные в родителях, — `hookAll*` видит
только объявленные в самом классе, поэтому переопределения он пропускает.

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
