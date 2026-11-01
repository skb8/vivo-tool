package com.skb8.vivotool.hooks.updater

import android.content.Context

/** Ресурсы модуля недоступны по R.id в процессе Updater. Язык берём с его экрана. */
internal class UpdaterInfoText(context: Context) {
    private val ru = context.resources.configuration.locales[0].language == "ru"
    fun text(russian: String, english: String) = if (ru) russian else english
    val details get() = text("Об этом обновлении", "About this update")
    val close get() = text("Закрыть", "Close")
    val copy get() = text("Копировать", "Copy")
    val copyAll get() = text("Копировать всё", "Copy all")
    val copied get() = text("Скопировано", "Copied")
    val copyFailed get() = text("Не удалось скопировать. Выделите нужный текст и скопируйте его.",
        "Could not copy. Select the text you need and copy it.")
    val loading get() = text("Читаем данные обновления…", "Reading update details…")
    val empty get() = text("На этой странице нет доступного обновления. Проверьте обновления и откройте это окно снова.",
        "No update is available on this page. Check for updates and open this window again.")
    val failed get() = text("Не удалось прочитать данные. Закройте окно и попробуйте снова.",
        "Could not read update details. Close this window and try again.")
    val metadata get() = text("Данные пакета", "Package details")
    val firmware get() = text("Ссылки на прошивку", "Firmware links")
    val changelog get() = text("Список изменений", "Changelog")
    val logMissing get() = text("Текст списка изменений ещё не загружен. Откройте список изменений на главной странице и повторно откройте это окно.",
        "The changelog text has not loaded yet. View the changelog on the main page and reopen this window.")
    val noLink get() = text("Приложение не предоставило ссылку на пакет.", "The app did not provide a package link.")
    val json get() = text("Расшифрованные данные пакета (JSON)", "Decoded package data (JSON)")
    val response get() = text("Данные сервера (JSON)", "Server data (JSON)")
    val logLinks get() = text("Ссылки на список изменений", "Changelog links")
}
