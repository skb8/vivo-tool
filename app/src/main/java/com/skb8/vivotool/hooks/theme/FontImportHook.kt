package com.skb8.vivotool.hooks.theme

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.XLog
import com.skb8.vivotool.core.callMethod
import com.skb8.vivotool.core.getField
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object FontImportHook : BaseHook() {
    override val id = "theme_font_import"
    override val targetPackages = setOf("com.bbk.theme")
    override val titleRes = R.string.hook_theme_font_import_title
    override val descriptionRes = R.string.hook_theme_font_import_description
    override val enabledByDefault = false

    private const val FONT = 4
    private const val PICK_FONT = 0x6219
    private val intType = Int::class.javaPrimitiveType!!
    private val boolType = Boolean::class.javaPrimitiveType!!
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean()
    private val headers = WeakHashMap<Activity, WeakReference<LinearLayout>>()

    override fun onHook() {
        val fragment = findClass("com.bbk.theme.fragment.CuteSettingsResListFragment")
        val activity = findClass("com.bbk.theme.ResListActivity")
        val database = findClass("com.bbk.theme.base.ResDbUtils")
        val helper = findClass("com.bbk.theme.apply.ApplyThemeHelper")
        val ids = findClass("com.bbk.theme.apply.ApplyThemeUtils")
        val themeUtils = findClass("com.bbk.theme.utils.ThemeUtils")
        val constants = findClass("com.bbk.theme.common.ThemeConstants")
        val item = findClass("com.bbk.theme.common.ThemeItem")
        val newItem = item.getDeclaredConstructor()
        val setters = listOf(
            item.getMethod("setCategory", intType),
            item.getMethod("setPackageId", String::class.java),
            item.getMethod("setResId", String::class.java),
            item.getMethod("setName", String::class.java),
            item.getMethod("setPath", String::class.java),
            item.getMethod("setRight", String::class.java),
            item.getMethod("setLocalItem", boolType)
        )
        val fontArgs = arrayOf(
            Context::class.java, String::class.java, intType,
            String::class.java, boolType, String::class.java
        )
        val apply = helper.getDeclaredMethod("applyFont", *fontArgs)
        val reload = helper.getMethod("fontConfigChanged", boolType, boolType, Context::class.java)
        val instance = helper.getMethod("getInstance")
        val success = helper.getField("APPLY_SUCCESS")
        val linkPath = constants.getField("DATA_LINK_FONT_PATH")
        val currentId = themeUtils.getMethod("getCurrentUseId", intType)
        val application = findClass("com.bbk.theme.ThemeApp").getMethod("getInstance")

        // Only look up files belonging to our private catalog, including on FontService reload.
        val hooks = mutableListOf<Unhook>()
        try {
            hooks += requireNotNull(database.hookBefore(
                "queryResPath", Context::class.java, intType, String::class.java
            ) { param ->
                if (param.args[1] == FONT) {
                    val context = param.arg<Context>(0) ?: return@hookBefore
                    val font = ImportedFonts.find(context, param.arg<String>(2).orEmpty())
                    if (font != null) param.result = font.archive.absolutePath
                }
            })
            hooks += requireNotNull(database.hookBefore(
                "queryThemeItemByPkgId", Context::class.java, intType, String::class.java
            ) { param ->
                if (param.args[1] == FONT) {
                    val context = param.arg<Context>(0) ?: return@hookBefore
                    val font = ImportedFonts.find(context, param.arg<String>(2).orEmpty())
                        ?: return@hookBefore
                    val entry = newItem.newInstance()
                    val values = listOf(FONT, font.id, font.id, font.name, font.archive.absolutePath, "free", true)
                    setters.zip(values).forEach { (setter, value) -> setter.invoke(entry, value) }
                    param.result = entry
                }
            })
            hooks += requireNotNull(ids.hookBefore(
                "getFontApplyNewPackageId", String::class.java, intType, boolType
            ) { param ->
                if (param.args[1] == FONT) {
                    val context = application.invoke(null) as? Context ?: return@hookBefore
                    ImportedFonts.find(context, param.arg<String>(0).orEmpty())?.let { param.result = it.id }
                }
            })
            // The small database wrappers can be inlined into these native callers.
            listOf(apply, themeUtils.getMethod("getThemeItem", Context::class.java, String::class.java, intType))
                .forEach { caller ->
                    runCatching { xposed?.deoptimize(caller) }
                        .onFailure { XLog.e("[$id] Could not deoptimize ${caller.name}", it) }
                }
            hooks += requireNotNull(helper.hookBefore("applyFont", *fontArgs) { param ->
                if (param.args[2] == FONT) {
                    val context = param.arg<Context>(0) ?: return@hookBefore
                    val font = ImportedFonts.find(context, param.arg<String>(1).orEmpty())
                        ?: return@hookBefore
                    param.setArg(3, font.id)
                    param.setArg(4, false)
                    param.setArg(5, font.name)
                }
            })

            val applyImported: (Context, ImportedFonts.Font) -> Unit = { context, font ->
                synchronized(helper) {
                    val link = linkPath.get(null) as String
                    val previousTarget = runCatching { Os.readlink(link) }.getOrNull()
                    try {
                        check(apply.invoke(null, context, font.id, FONT, font.id, false, font.name) ==
                            success.getInt(null)) { "iTheme rejected the imported font" }
                    } catch (error: Exception) {
                        // iTheme removes its old link before creating the new one.
                        if (previousTarget != null) runCatching {
                            try {
                                Os.lstat(link)
                            } catch (missing: ErrnoException) {
                                if (missing.errno == OsConstants.ENOENT) Os.symlink(previousTarget, link)
                            }
                        }.onFailure { XLog.e("[$id] Failed to restore the previous font link", it) }
                        throw error
                    }
                }
                reload.invoke(instance.invoke(null), false, true, context)
            }
            val render: (Activity, LinearLayout) -> Unit = { host, header ->
                renderCards(host, header, { currentId.invoke(null, FONT) as? String }, applyImported)
            }
            hooks += requireNotNull(activity.hookBefore(
                "onActivityResult", intType, intType, Intent::class.java
            ) { param ->
                if (param.args[0] != PICK_FONT) return@hookBefore
                val host = param.thisObject as? Activity ?: return@hookBefore
                param.result = null
                if (param.args[1] != Activity.RESULT_OK) return@hookBefore
                val uri = param.arg<Intent>(2)?.data ?: return@hookBefore
                importFont(host, uri, render)
            })
            hooks += requireNotNull(fragment.hookAfter("setupViews") { param ->
                val screen = param.thisObject ?: return@hookAfter
                if (screen.getField("mResType") != FONT) return@hookAfter
                val info = screen.getField("mResListInfo") ?: return@hookAfter
                if (info.getField("fromSetting") != true) return@hookAfter
                val host = screen.callMethod("getActivity") as? Activity ?: return@hookAfter
                if (!activity.isInstance(host)) return@hookAfter
                val adapter = screen.getField("mAdapter") ?: return@hookAfter
                val previous = headers[host]?.get()
                if (previous != null && adapter.callMethod("containView", previous) == true) return@hookAfter
                val header = LinearLayout(host).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = ViewGroup.LayoutParams(-1, -2)
                    setPadding(dp(16), dp(8), dp(16), dp(4))
                }
                render(host, header)
                adapter.callMethod("addHeaderView", header)
                headers[host] = WeakReference(header)
                adapter.callMethod("notifyDataSetChanged")
            })
            hooks += requireNotNull(fragment.hookAfter("updateList", ArrayList::class.java) { param ->
                val screen = param.thisObject ?: return@hookAfter
                if (screen.getField("mResType") != FONT) return@hookAfter
                val host = screen.callMethod("getActivity") as? Activity ?: return@hookAfter
                val header = headers[host]?.get() ?: return@hookAfter
                val adapter = screen.getField("mAdapter") ?: return@hookAfter
                if (adapter.callMethod("containView", header) != true) return@hookAfter
                render(host, header)
                if ((param.args[0] as? List<*>).isNullOrEmpty()) {
                    (screen.getField("mEmptyLayout") as? View)?.visibility = View.GONE
                    (screen.getField("mRecyclerView") as? View)?.visibility = View.VISIBLE
                }
            })
        } catch (error: Throwable) {
            hooks.asReversed().forEach { it.unhook() }
            throw error
        }
    }

    private fun importFont(host: Activity, uri: Uri, render: (Activity, LinearLayout) -> Unit) {
        val unnamed = host.localized("Шрифт", "Font")
        runOperation(host, host.localized("Шрифт добавлен", "Font imported"),
            host.localized("Не удалось импортировать шрифт TTF или OTF до 32 МБ",
                "Could not import a TTF or OTF font up to 32 MB"),
            { ImportedFonts.importFont(it, uri, unnamed) }, render)
    }

    private fun renderCards(
        host: Activity,
        header: LinearLayout,
        currentId: () -> String?,
        apply: (Context, ImportedFonts.Font) -> Unit
    ) {
        header.removeAllViews()
        header.addView(card(host, host.localized("Импорт", "Import"), "TTF / OTF") {
            runCatching {
                host.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    // Some providers report fonts as application/octet-stream.
                    type = "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, PICK_FONT)
            }.onFailure {
                toast(host, host.localized("Не удалось открыть выбор файла", "Could not open the file picker"))
            }
        })
        val current = currentId()
        for (font in ImportedFonts.list(host)) {
            val selected = font.id == current
            val view = card(host, font.name, if (selected) host.localized("Применён", "Applied")
                else host.localized("Нажмите, чтобы применить", "Tap to apply")) {
                AlertDialog.Builder(host)
                    .setTitle(font.name)
                    .setMessage(host.localized("Применить этот шрифт?", "Apply this font?"))
                    .setNegativeButton(host.localized("Отмена", "Cancel"), null)
                    .setPositiveButton(host.localized("Применить", "Apply")) { _, _ ->
                        runOperation(host, host.localized("Шрифт применён", "Font applied"),
                            host.localized("Не удалось применить шрифт", "Could not apply the font"),
                            { apply(it, font) },
                            { screen, content -> renderCards(screen, content, currentId, apply) })
                    }.show()
            }
            view.isEnabled = !selected
            header.addView(view)
        }
    }

    private fun runOperation(
        host: Activity,
        success: String,
        failure: String,
        operation: (Context) -> Unit,
        refresh: (Activity, LinearLayout) -> Unit
    ) {
        if (!busy.compareAndSet(false, true)) {
            toast(host, host.localized("Дождитесь завершения операции", "Wait for the current operation"))
            return
        }
        val context = host.applicationContext
        worker.execute {
            val result = runCatching { operation(context) }
            result.onFailure { XLog.e("[$id] $failure", it) }
            host.runOnUiThread {
                busy.set(false)
                toast(context, if (result.isSuccess) success else failure)
                headers.entries.toList().forEach { (screen, reference) ->
                    val header = reference.get() ?: return@forEach
                    if (!screen.isDestroyed && !screen.isFinishing) {
                        runCatching { refresh(screen, header) }
                            .onFailure { XLog.e("[$id] Could not refresh imported fonts", it) }
                    }
                }
            }
        }
    }

    private fun card(context: Context, title: String, subtitle: String, action: () -> Unit): View {
        val colors = context.obtainStyledAttributes(intArrayOf(
            android.R.attr.colorBackgroundFloating, android.R.attr.colorControlHighlight,
            android.R.attr.textColorPrimary, android.R.attr.textColorSecondary
        ))
        val background = colors.getColor(0, Color.WHITE)
        val ripple = colors.getColor(1, 0x20000000)
        val primary = colors.getColor(2, Color.BLACK)
        val secondary = colors.getColor(3, Color.DKGRAY)
        colors.recycle()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            minimumHeight = dp(64)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            this.background = RippleDrawable(ColorStateList.valueOf(ripple), GradientDrawable().apply {
                setColor(background)
                cornerRadius = dp(16).toFloat()
            }, null)
            addView(TextView(context).apply { text = title; textSize = 17f; setTextColor(primary) })
            addView(TextView(context).apply { text = subtitle; textSize = 13f; setTextColor(secondary) })
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun Context.localized(ru: String, en: String): String =
        if (resources.configuration.locales[0]?.language == "ru") ru else en

    private fun View.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(context: Context, message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
