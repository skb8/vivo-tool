# Vivo Tool

LSPosed-модуль в стиле LuckyTool: приложение-контейнер с UI, в которое удобно
добавлять хуки под конкретные приложения. Хуков пока нет — это готовый каркас.

- Каждый хук — отдельный класс-наследник `BaseHook` с собственным `id`, названием и списком целевых пакетов.
- Хуки включаются и выключаются по отдельности прямо в приложении (без пересборки).
- Целевые приложения перечисляются в одном файле и автоматически попадают в область
  действия (scope), которую LSPosed предлагает при выборе приложений.
- Сборка APK — через GitHub Actions.

## Хуки

| Хук | Приложение | Что делает |
| --- | --- | --- |
| Картинка в «О телефоне» | `com.android.settings` | Подменяет ресурс `about_phone_origin_os_rom15` (936×618) своим изображением |

Картинка выбирается из галереи прямо в приложении и обрезается под точный
размер ресурса: пропорции кадра фиксированы, масштаб и положение задаются
жестами. Результат хранится в world-readable `SharedPreferences`, поэтому
меняется без переустановки модуля — достаточно закрыть «Настройки» (force stop).

## Требования

- Android 8.1+ (API 27), LSPosed (Xposed API 93+)
- Для локальной сборки: JDK 17, Android SDK 35

## Сборка

Через GitHub Actions: workflow [`Build APK`](.github/workflows/build.yml) запускается на
каждый push в `main`, на pull request и вручную (`Actions` → `Build APK` → `Run workflow`).
Готовые APK лежат в артефактах сборки: `vivo-tool-debug` и `vivo-tool-release`.

Тег вида `v1.0.0` дополнительно создаёт GitHub Release с APK.

Локально:

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew assembleRelease      # app/build/outputs/apk/release/
```

По умолчанию release подписывается debug-ключом, чтобы APK можно было сразу
установить. Для своего ключа задайте переменные окружения `VIVO_KEYSTORE_FILE`,
`VIVO_KEYSTORE_PASSWORD`, `VIVO_KEY_ALIAS`, `VIVO_KEY_PASSWORD`; в Actions — секреты
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Установка

1. Установить APK.
2. В LSPosed включить модуль «Vivo Tool».
3. Выбрать приложения в области действия (LSPosed предложит те, что указаны в `app/module-scope.txt`).
4. Перезагрузить устройство или сделать force stop целевых приложений.
5. Открыть приложение — карточка сверху покажет, активен ли модуль.

## Как добавить хук

Коротко:

1. Скопировать `app/src/main/java/com/skb8/vivotool/hooks/template/TemplateHook.kt`
   в `hooks/<приложение>/<Имя>Hook.kt`.
2. Задать `id`, `title`, `targetPackages`.
3. Зарегистрировать объект в `hooks/HookModules.kt`.
4. Добавить пакет приложения в `app/module-scope.txt`.

Подробно, с примерами и приёмами реверса — [`docs/WRITING_HOOKS.md`](docs/WRITING_HOOKS.md).

## Структура проекта

```
app/module-scope.txt                    список приложений для scope (единственный источник правды)
app/src/main/assets/xposed_init         точка входа (легаси-формат Xposed)
app/src/main/java/com/skb8/vivotool/
├── core/
│   ├── HookEntry.kt                    точка входа модуля, применяет хуки
│   ├── BaseHook.kt                     базовый класс хука + хелперы hookBefore/hookAfter/replace
│   ├── HookRegistry.kt                 доступ к хукам, проверка scope
│   ├── HookPrefs.kt                    чтение настроек внутри процессов с хуками
│   ├── XposedExt.kt                    короткие обёртки над рефлексией XposedHelpers
│   ├── ModuleStatus.kt                 определение активации модуля
│   ├── Constants.kt                    имена настроек и специальные значения
│   └── XLog.kt                         логи в logcat и журнал LSPosed
├── hooks/
│   ├── HookModules.kt                  список подключённых хуков
│   └── template/TemplateHook.kt        шаблон для копирования (не подключён)
├── settings/AppSettings.kt             запись настроек (world-readable)
├── ui/                                 экран на Jetpack Compose
└── MainActivity.kt
```

Метаданные LSPosed (`META-INF/xposed/module.prop`, `java_init.list`, `scope.list`) и
`@array/module_scope` генерируются при сборке задачей `generateXposedMetadata`
из `app/module-scope.txt`, поэтому список приложений правится только в одном месте.

## Лицензия

MIT
