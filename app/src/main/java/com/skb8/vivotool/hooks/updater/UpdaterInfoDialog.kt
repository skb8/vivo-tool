package com.skb8.vivotool.hooks.updater

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.skb8.vivotool.core.XLog
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

internal object UpdaterInfoDialog {
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "VivoTool-UpdateInfo").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    fun show(activity: Activity, presenter: Any?) {
        val words = UpdaterInfoText(activity)
        val packages = UpdaterInfoReader.currentPackages(presenter)
        if (packages.isEmpty()) {
            AlertDialog.Builder(activity).setTitle(words.details).setMessage(words.empty)
                .setPositiveButton(words.close, null).show()
            return
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(activity, 20)
            setPadding(padding, padding / 2, padding, padding)
        }
        val status = TextView(activity).apply { text = words.loading; setPadding(0, dp(activity, 12), 0, dp(activity, 12)) }
        root.addView(status)
        val scroll = ScrollView(activity).apply { addView(root) }
        val dialog = AlertDialog.Builder(activity).setTitle(words.details).setView(scroll)
            .setPositiveButton(words.close, null).create()
        dialog.show()
        val owner = WeakReference(activity)
        val context = activity.applicationContext
        worker.execute {
            val result = runCatching { packages.map { (kind, bean) -> UpdaterInfoReader.read(context, kind, bean) } }
            main.post {
                val current = owner.get() ?: return@post
                if (current.isFinishing || current.isDestroyed || !dialog.isShowing) return@post
                result.onSuccess { details ->
                    runCatching {
                        root.removeAllViews()
                        render(current, root, details, words)
                    }.onFailure {
                        XLog.e("[${UpdaterInfoHook.ID}] не удалось показать данные пакета", it)
                        root.removeAllViews()
                        root.addView(TextView(current).apply { text = words.failed })
                    }
                }.onFailure {
                    XLog.e("[${UpdaterInfoHook.ID}] не удалось прочитать пакет", it)
                    status.text = words.failed
                }
            }
        }
    }

    private fun render(activity: Activity, root: LinearLayout, packages: List<UpdatePackageDetails>, words: UpdaterInfoText) {
        val fullText = packages.joinToString("\n\n") { details ->
            buildString {
                appendLine("${details.kind} · ${details.version}")
                appendLine(details.metadata)
                appendLine("\n${words.firmware}\n${details.firmwareLinks.joinToString("\n")}")
                appendLine("\n${words.changelog}\n${details.changelog}")
                if (details.logLinks.isNotEmpty()) appendLine("\n${words.logLinks}\n${details.logLinks.joinToString("\n")}")
                appendLine("\n${words.json}\n${details.decodedJson}")
                details.serverJson?.let { appendLine("\n${words.response}\n$it") }
            }
        }
        root.addView(copyButton(activity, words.copyAll, fullText, words))
        for (details in packages) {
            root.addView(TextView(activity).apply {
                text = "${details.kind} · ${details.version}"
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextIsSelectable(true)
                setPadding(0, dp(activity, 20), 0, dp(activity, 8))
            })
            section(activity, root, words.metadata, details.metadata, words)
            if (details.firmwareLinks.isEmpty()) {
                section(activity, root, words.firmware, words.noLink, words, copy = false)
            } else {
                details.firmwareLinks.forEachIndexed { index, link ->
                    val title = if (details.firmwareLinks.size == 1) words.firmware else "${words.firmware} · ${index + 1}"
                    section(activity, root, title, link, words)
                }
            }
            section(activity, root, words.changelog, details.changelog.ifBlank { words.logMissing }, words,
                copy = details.changelog.isNotBlank())
            details.logLinks.forEachIndexed { index, link ->
                section(activity, root, "${words.logLinks} · ${index + 1}", link, words)
            }
            section(activity, root, words.json, details.decodedJson, words, collapsed = true)
            details.serverJson?.let { section(activity, root, words.response, it, words, collapsed = true) }
        }
    }

    private fun section(activity: Activity, root: LinearLayout, title: String, value: String,
        words: UpdaterInfoText, copy: Boolean = true, collapsed: Boolean = false) {
        root.addView(TextView(activity).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(activity, 18), 0, dp(activity, 4))
        })
        val content = TextView(activity).apply {
            // Полные данные остаются в буфере копирования; длинный JSON не блокирует layout.
            text = if (value.length <= 120_000) value else value.take(120_000) +
                words.text("\n…Полный текст доступен через «Копировать».", "\n…Use Copy to get the full text.")
            textSize = 14f
            setTextIsSelectable(true)
            if (collapsed) typeface = Typeface.MONOSPACE
            visibility = if (collapsed) View.GONE else View.VISIBLE
        }
        if (collapsed) {
            root.addView(Button(activity).apply {
                isAllCaps = false
                fun updateTitle() {
                    text = if (content.visibility == View.VISIBLE) words.text("Скрыть JSON", "Hide JSON")
                        else words.text("Показать JSON", "Show JSON")
                }
                updateTitle()
                setOnClickListener {
                    content.visibility = if (content.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    updateTitle()
                }
            })
        }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (copy) root.addView(copyButton(activity, words.copy, value, words).apply { contentDescription = "${words.copy}: $title" })
    }

    private fun copyButton(context: Context, label: String, value: String, words: UpdaterInfoText): Button = Button(context).apply {
        text = label
        isAllCaps = false
        setOnClickListener {
            val copied = runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(words.details, value))
            }.isSuccess
            Toast.makeText(context, if (copied) words.copied else words.copyFailed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
