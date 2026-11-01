package com.skb8.vivotool.hooks.updater

import android.content.Context
import android.text.Html
import com.skb8.vivotool.core.XLog
import com.skb8.vivotool.core.callMethod
import com.skb8.vivotool.core.callStaticMethod
import com.skb8.vivotool.core.getField
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.WeakHashMap

internal data class UpdatePackageDetails(
    val kind: String,
    val version: String,
    val metadata: String,
    val firmwareLinks: List<String>,
    val logLinks: List<String>,
    val changelog: String,
    val decodedJson: String,
    val serverJson: String?
)

internal object UpdaterInfoReader {
    // Только вход JSON-парсера конкретного пакета после штатной расшифровки.
    // Не перехватываем ответы авторизации и не сохраняем данные на диск/в журнал.
    private val serverData = Collections.synchronizedMap(WeakHashMap<Any, String>())

    fun installParserHooks(hook: (String, (Any, String) -> Unit) -> Unit) {
        for (name in listOf("getFotaUpdateInfoJsonStr", "getVgcUpdateInfoFromJsonStr")) {
            hook(name) { bean, json ->
                if (json.length <= 2_000_000 && runCatching { JSONObject(json) }.isSuccess) serverData[bean] = json
            }
        }
    }

    /** Вызывается в UI-потоке, чтобы взять согласованный снимок текущего presenter. */
    fun currentPackages(presenter: Any?): List<Pair<String, Any>> {
        if (presenter == null) return emptyList()
        return listOfNotNull(
            runCatching { presenter.getField("mFotaUpdateInfo") }.getOrNull()?.let { "FOTA" to it },
            runCatching { presenter.getField("mVgcUpdateInfo") }.getOrNull()?.let { "VGC" to it }
        )
    }

    fun read(context: Context, kind: String, bean: Any): UpdatePackageDetails {
        val words = UpdaterInfoText(context)
        val loader = bean.javaClass.classLoader!!
        fun value(method: String): String = runCatching { bean.callMethod(method)?.toString().orEmpty() }.getOrDefault("")
        val version = value("getVersion")
        val metadata = buildList {
            fun addValue(label: String, method: String) {
                value(method).takeIf { it.isNotBlank() }?.let { add("$label: $it") }
            }
            addValue(words.text("Версия", "Version"), "getVersion")
            addValue("Android", "getArdVer")
            addValue("OriginOS", "getOsVersion")
            addValue(words.text("Имя файла", "File name"), "getFileName")
            val bytes = value("getFileLength").toLongOrNull()
            if (bytes != null && bytes > 0) {
                add("${words.text("Размер", "Size")}: ${android.text.format.Formatter.formatFileSize(context, bytes)} ($bytes ${words.text("байт", "bytes")})")
            }
            addValue(words.text("Тип OTA", "OTA type"), "getOtaType")
            addValue(words.text("Тип пакета", "Package type"), "getPackageType")
            addValue(words.text("Тип обновления", "Update type"), "getType")
            addValue(words.text("Пробная версия", "Trial version"), "isTrialVersion")
            addValue("MD5", "getMd5")
            addValue("SHA-256", "getSha256")
        }.joinToString("\n")
        val json = runCatching {
            val gsonClass = Class.forName("com.google.gson.Gson", true, loader)
            val gson = gsonClass.getConstructor().newInstance()
            val encoded = gsonClass.getMethod("toJson", Any::class.java).invoke(gson, bean) as String
            JSONObject(encoded).toString(2)
        }.getOrElse {
            XLog.w("[${UpdaterInfoHook.ID}] сериализация пакета недоступна: ${it.javaClass.simpleName}")
            JSONObject().apply {
                put("version", version)
                put("downloadURL", value("getDownloadURL"))
                put("fileName", value("getFileName"))
                put("fileLength", value("getFileLength"))
                put("md5", value("getMd5"))
                put("sha256", value("getSha256"))
                put("otaType", value("getOtaType"))
                put("updateLog", value("getUpdateLog"))
            }.toString(2)
        }
        val source = serverData[bean]?.let { runCatching { JSONObject(it).toString(2) }.getOrNull() }
        // Поля pk / downloadURL могут содержать несколько зеркал в JSON-массиве.
        val firmware = linkedSetOf<String>()
        addUrls(value("getDownloadURL"), firmware)
        collectPackageUrls(JSONObject(json), firmware)
        source?.let { collectPackageUrls(JSONObject(it), firmware) }
        val logs = linkedSetOf<String>()
        for (name in listOf("getLogUrl", "getLogZip", "getH5Link")) addUrls(value(name), logs)

        // WebHelper читает локальный data.js / prefs для данной версии, без HTTP-запроса.
        val cachedLog = if (kind == "FOTA" && version.isNotBlank()) runCatching {
            val helper = Class.forName("com.bbk.updater.utils.WebHelper", true, loader)
                .callStaticMethod("getInstance", context) ?: return@runCatching ""
            val log = helper.callMethod("getLogInfo", version) ?: return@runCatching ""
            buildList {
                for (method in listOf("getLogHead", "getHeadContent")) {
                    (log.callMethod(method) as? String)?.takeIf { it.isNotBlank() }?.let(::add)
                }
                val sections = listOfNotNull(log.callMethod("getRecommend")) +
                    ((log.callMethod("getBody") as? List<*>)?.filterNotNull() ?: emptyList())
                for (section in sections) {
                    (section.callMethod("getTitle") as? String)?.takeIf { it.isNotBlank() }?.let(::add)
                    (section.callMethod("getContents") as? List<*>)?.filterIsInstance<String>()?.let(::addAll)
                }
            }.joinToString("\n\n")
        }.getOrDefault("") else ""
        val changelog = listOf(cachedLog, value("getUpdateLog"),
            if (kind == "FOTA") value("getUpdateLogInHtml") else "")
            .firstOrNull { it.isNotBlank() }.orEmpty()
        return UpdatePackageDetails(kind, version, metadata, firmware.toList(), logs.toList(),
            plainText(changelog), json, source)
    }

    private fun plainText(text: String): String {
        if (!Regex("</?[a-zA-Z][^>]*>").containsMatchIn(text)) return text.trim()
        return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    private fun addUrls(text: String, urls: MutableSet<String>) {
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) {
            runCatching { JSONArray(trimmed) }.getOrNull()?.let { array ->
                for (i in 0 until array.length()) addUrls(array.optString(i), urls)
                return
            }
        }
        // Не декодируем query-параметры: это может повредить подписанную ссылку.
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) urls.add(trimmed)
    }

    private fun collectPackageUrls(value: Any?, urls: MutableSet<String>, depth: Int = 0) {
        if (depth > 12) return
        when (value) {
            is JSONObject -> for (key in value.keys()) {
                val child = value.opt(key)
                if (key.equals("pk", true) || key.equals("downloadURL", true)) addUrls(child.toString(), urls)
                if (child is JSONObject || child is JSONArray) collectPackageUrls(child, urls, depth + 1)
            }
            is JSONArray -> for (i in 0 until value.length()) collectPackageUrls(value.opt(i), urls, depth + 1)
        }
    }
}
