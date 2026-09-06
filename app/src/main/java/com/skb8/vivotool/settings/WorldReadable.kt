package com.skb8.vivotool.settings

import android.content.Context
import com.skb8.vivotool.core.XLog
import java.io.File

/**
 * Доступность файлов настроек чужим процессам.
 *
 * Хуки читают наши настройки из процесса целевого приложения через
 * `XSharedPreferences`, поэтому мало открыть файл в MODE_WORLD_READABLE —
 * нужно ещё, чтобы в каталоги над ним можно было войти. После очистки данных
 * приложения система создаёт каталог данных заново с приватными правами, и
 * хуки перестают видеть настройки, хотя сам файл читаемый.
 *
 * Каталогам ставим только право на вход, файлу — на чтение: списка файлов
 * чужим процессам знать не нужно.
 */
internal object WorldReadable {

    fun fix(context: Context, prefsName: String) {
        val dataDir = File(context.applicationInfo.dataDir)
        val prefsDir = File(dataDir, "shared_prefs")

        traversable(dataDir)
        traversable(prefsDir)
        readable(File(prefsDir, "$prefsName.xml"))
    }

    private fun traversable(dir: File) {
        if (!dir.isDirectory) return
        if (!dir.setExecutable(true, false)) {
            XLog.d("Каталог ${dir.name} не удалось открыть для входа")
        }
    }

    private fun readable(file: File) {
        if (!file.isFile) return
        if (!file.setReadable(true, false)) {
            XLog.d("Файл ${file.name} не удалось открыть на чтение")
        }
    }
}
