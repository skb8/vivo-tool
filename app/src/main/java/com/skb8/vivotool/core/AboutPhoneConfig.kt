package com.skb8.vivotool.core

import androidx.annotation.StringRes
import com.skb8.vivotool.R
import org.json.JSONArray
import org.json.JSONObject

enum class AboutPhoneVisibility { DEFAULT, SHOW, HIDE }

/** null сохраняет штатный текст; пустая строка намеренно убирает его. */
data class AboutPhoneRow(
    val key: String,
    val title: String? = null,
    val value: String? = null,
    val visibility: AboutPhoneVisibility = AboutPhoneVisibility.DEFAULT
)

data class AboutPhoneField(
    val key: String,
    @StringRes val labelRes: Int,
    val viewNames: List<String>,
    val controllerNames: List<String>
)

/** Общий формат для меню модуля и процесса настроек. Сохраняется одной записью. */
data class AboutPhoneConfig(
    val modelName: String? = null,
    val hideModel: Boolean = false,
    val rows: List<AboutPhoneRow> = emptyList()
) {
    fun row(key: String): AboutPhoneRow? = rows.firstOrNull { it.key == key }

    fun withRow(row: AboutPhoneRow): AboutPhoneConfig {
        val existing = rows.indexOfFirst { it.key == row.key }
        return copy(rows = rows.toMutableList().apply {
            if (existing >= 0) set(existing, row) else add(row)
        })
    }

    fun withoutRow(key: String): AboutPhoneConfig = copy(rows = rows.filterNot { it.key == key })

    fun encode(): String = JSONObject().apply {
        put("version", 1)
        modelName?.let { put("modelName", it) }
        put("hideModel", hideModel)
        put("rows", JSONArray().apply {
            rows.forEach { row ->
                put(JSONObject().apply {
                    put("key", row.key)
                    row.title?.let { put("title", it) }
                    row.value?.let { put("value", it) }
                    put("visibility", row.visibility.name)
                })
            }
        })
    }.toString()

    companion object {
        const val PREF_KEY = "settings_about_phone_customization"
        const val CUSTOM_PREFIX = "custom:"
        const val MAX_CUSTOM_ROWS = 50

        val fields = listOf(
            field("cpu", R.string.about_field_cpu, "device_info_cpu", "VivoDeviceInfoCpuController"),
            AboutPhoneField("chip", R.string.about_field_chip,
                listOf("device_info_chip", "device_info_chip_display", "device_info_chip_image_blue", "device_info_chip_v1"),
                listOf("VivoDeviceInfoSelfInnovateChipController", "VivoDeviceInfoIrisDisplayChipController",
                    "VivoDeviceInfoBlueImageChipController", "VivoDeviceInfoV1ChipController").map { "chip.$it" }),
            field("ram", R.string.about_field_ram, "device_info_ram", "VivoDeviceInfoRamController"),
            field("storage", R.string.about_field_storage, "device_info_storage", "VivoDeviceInfoStorageController"),
            field("device_name", R.string.about_field_device_name, "ll_device_name", "VivoDeviceInfoModifiedDeviceNameController"),
            field("battery", R.string.about_field_battery, "device_info_battery", "VivoDeviceInfoBatteryController"),
            field("storage_compress", R.string.about_field_compress, "device_info_storage_compress", "VivoDeviceInfoStorageCompressController"),
            field("image_chip", R.string.about_field_image_chip, "device_info_chip_image_v2", "chip.VivoDeviceInfoImageCapabilitiesController"),
            field("version", R.string.about_field_version, "version_info", "VivoDeviceInfoVersionInfoController"),
            field("status", R.string.about_field_status, "status_info", "VivoDeviceInfoStatusInfoController"),
            field("legal", R.string.about_field_legal, "legal_info", "VivoDeviceInfoLegalInfoController"),
            field("advisor", R.string.about_field_advisor, "user_advisor_plan", "VivoAboutPhoneUserAdvisorPlanController")
        )

        private fun field(key: String, label: Int, view: String, controller: String) =
            AboutPhoneField(key, label, listOf(view), listOf(controller))

        fun decode(text: String): AboutPhoneConfig {
            if (text.isBlank()) return AboutPhoneConfig()
            return runCatching {
                val json = JSONObject(text)
                require(json.optInt("version", 1) == 1)
                val items = json.optJSONArray("rows") ?: JSONArray()
                val rows = buildList {
                    for (i in 0 until items.length().coerceAtMost(fields.size + MAX_CUSTOM_ROWS)) {
                        val item = items.optJSONObject(i) ?: continue
                        val key = item.optString("key")
                        if (fields.none { it.key == key } && !key.startsWith(CUSTOM_PREFIX)) continue
                        if (key == CUSTOM_PREFIX || key.length > 100) continue
                        val visibility = runCatching {
                            AboutPhoneVisibility.valueOf(item.optString("visibility", "DEFAULT"))
                        }.getOrDefault(AboutPhoneVisibility.DEFAULT)
                        add(AboutPhoneRow(key, item.optionalText("title"), item.optionalText("value"), visibility))
                    }
                }.distinctBy { it.key }
                AboutPhoneConfig(json.optionalText("modelName"), json.optBoolean("hideModel"), rows)
            }.getOrElse {
                XLog.e("Не удалось прочитать настройки «О телефоне»", it)
                AboutPhoneConfig()
            }
        }

        private fun JSONObject.optionalText(name: String): String? =
            if (has(name) && !isNull(name)) getString(name).take(2000) else null
    }
}
