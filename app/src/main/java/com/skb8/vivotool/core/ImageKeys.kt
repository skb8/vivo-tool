package com.skb8.vivotool.core

/** Ключи картинок в хранилище, общие для UI приложения и хуков. */
object ImageKeys {

    /** Картинка карточки «О телефоне» в настройках. */
    const val ABOUT_PHONE_ROM = "about_phone_rom15"

    /** Метка времени последнего изменения — по ней инвалидируется кэш в хуке. */
    fun updatedKey(key: String): String = "${key}_updated"
}
