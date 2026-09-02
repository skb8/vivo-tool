package com.skb8.vivotool.hooks

import com.skb8.vivotool.core.BaseHook

/**
 * Список подключённых хуков — единственное место, куда нужно добавить свой хук.
 *
 * Порядок в списке = порядок применения внутри одного процесса.
 *
 * Чек-лист добавления хука:
 *  1. Создать файл в `app/src/main/java/com/skb8/vivotool/hooks/<приложение>/`.
 *  2. Унаследовать [BaseHook] (проще всего — `object`).
 *  3. Добавить объект в [all].
 *  4. Добавить целевой пакет в `app/module-scope.txt`,
 *     чтобы LSPosed предложил это приложение при выборе области действия.
 *
 * Подробности — в `docs/WRITING_HOOKS.md`.
 */
object HookModules {

    val all: List<BaseHook> = listOf(
        // Пока хуков нет. Пример регистрации:
        // com.skb8.vivotool.hooks.template.TemplateHook,
    )
}
