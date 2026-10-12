package com.skb8.vivotool.hooks.settings

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.skb8.vivotool.R
import com.skb8.vivotool.core.AboutPhoneConfig
import com.skb8.vivotool.core.AboutPhoneField
import com.skb8.vivotool.core.AboutPhoneRow
import com.skb8.vivotool.core.AboutPhoneVisibility
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.HookPrefs
import com.skb8.vivotool.core.SystemProperties
import com.skb8.vivotool.core.XLog

/** Меняет только представление экрана: системные свойства и имена Bluetooth не затрагиваются. */
object AboutPhoneCustomizationHook : BaseHook() {
    const val ID = "settings_about_phone_customization"
    private const val ADDED_ROW_TAG = "vivo_tool_about_row:"
    override val id = ID
    override val titleRes = R.string.hook_about_customization_title
    override val descriptionRes = R.string.hook_about_customization_description
    override val targetPackages = setOf(AboutPhoneViews.PACKAGE)
    override val enabledByDefault = false
    override fun isSupported(sdkInt: Int) = super.isSupported(sdkInt) && SystemProperties.releaseVersionMajor >= 16

    private var cachedText: String? = null
    private var cachedConfig = AboutPhoneConfig()

    @Synchronized
    private fun config(): AboutPhoneConfig {
        val text = HookPrefs.getString(AboutPhoneConfig.PREF_KEY, "")
        if (text != cachedText) {
            cachedConfig = AboutPhoneConfig.decode(text)
            cachedText = text
        }
        return cachedConfig
    }

    override fun onHook() {
        val base = findClassOrNull(AboutPhoneViews.BASE_CONTROLLER)
        if (base == null) {
            XLog.w("[$id] контроллеры «О телефоне» не найдены")
            return
        }
        // Базовый initRootView вызывается после создания и заполнения родного виджета.
        base.hookAfter("initRootView", View::class.java) { param ->
            val controller = param.thisObject ?: return@hookAfter
            applyController(controller)
        }
        // onResume и обработчики смены конфигурации могут повторно заполнять текст.
        val controllers = AboutPhoneConfig.fields.flatMap { it.controllerNames } +
            "VivoDeviceInfoProductModelController"
        val summaryTargets = (listOf(base) + controllers.mapNotNull {
            findClassOrNull(AboutPhoneViews.CONTROLLERS + it)
        }).distinct()
        // По одному хуку на реализацию, даже если её наследуют несколько контроллеров.
        summaryTargets.mapNotNull { target ->
            target.methodsInHierarchy("getSummary", String::class.java)
                .firstOrNull { it.parameterCount == 0 }?.declaringClass
        }.distinct().forEach { target ->
            target.hookAfter("getSummary") { param ->
                val controller = param.thisObject ?: return@hookAfter
                val settings = config()
                if (controller.javaClass.simpleName == "VivoDeviceInfoProductModelController") {
                    settings.modelName?.let { param.result = it }
                } else {
                    fieldFor(controller)?.let { field ->
                        settings.row(field.key)?.value?.let { param.result = it }
                    }
                }
            }
        }
        summaryTargets.mapNotNull { target ->
            target.methodsInHierarchy("bindView")
                .firstOrNull { it.parameterTypes.contentEquals(arrayOf(View::class.java)) }?.declaringClass
        }.distinct().forEach { target ->
            target.hookAfter("bindView", View::class.java) { param ->
                param.thisObject?.let(::applyController)
            }
        }
        findClassOrNull(AboutPhoneViews.FRAGMENT)?.apply {
            hookAllAfter("onCreateView") { param -> (param.result as? View)?.let(::applyPage) }
            hookAllAfter("onConfigurationChanged") { param ->
                (param.thisObject?.fieldOrNull("mRootView") as? View)?.let(::applyPage)
            }
        }
    }

    private fun fieldFor(controller: Any): AboutPhoneField? = AboutPhoneConfig.fields.firstOrNull { field ->
        field.controllerNames.any { controller.javaClass.name == AboutPhoneViews.CONTROLLERS + it }
    }

    private fun applyController(controller: Any) {
        val root = controller.callOrNull("getRootView") as? View ?: return
        val settings = config()
        if (controller.javaClass.simpleName == "VivoDeviceInfoProductModelController") {
            applyModel(root, settings)
        } else {
            val field = fieldFor(controller) ?: return
            settings.row(field.key)?.let { applyRow(root, it) }
        }
    }

    private fun applyModel(root: View, settings: AboutPhoneConfig) {
        if (settings.hideModel) root.visibility = View.GONE
        settings.modelName?.let { name ->
            (AboutPhoneViews.find(root, "summary") as? TextView)?.apply {
                text = name
                visibility = if (name.isEmpty()) View.GONE else View.VISIBLE
            }
            // IQOO в штатном имени рисуется ещё и отдельной картинкой.
            AboutPhoneViews.find(root, "product_model_img")?.visibility = View.GONE
            root.contentDescription = name
        }
    }

    private fun applyRow(view: View, row: AboutPhoneRow) {
        when (row.visibility) {
            AboutPhoneVisibility.HIDE -> view.visibility = View.GONE
            AboutPhoneVisibility.SHOW -> view.visibility = View.VISIBLE
            AboutPhoneVisibility.DEFAULT -> Unit
        }
        val titleView = view.callOrNull("getTitleView") as? TextView
        val valueView = view.callOrNull("getSummaryView") as? TextView
        row.title?.let { title -> titleView?.apply { text = title; visibility = if (title.isEmpty()) View.GONE else View.VISIBLE } }
        row.value?.let { value -> valueView?.apply { text = value; visibility = if (value.isEmpty()) View.GONE else View.VISIBLE } }
        if (row.title != null || row.value != null) {
            view.contentDescription = listOfNotNull(titleView?.text, valueView?.text).joinToString(", ")
        }
    }

    private fun applyPage(root: View) {
        val settings = config()
        AboutPhoneViews.find(root, "product_model")?.let { applyModel(it, settings) }
        val body = AboutPhoneViews.find(root, "about_phone_device_info_layout") as? LinearLayout ?: return
        // Удаляем только наши строки перед повторным заполнением страницы.
        for (i in body.childCount - 1 downTo 0) {
            val child = body.getChildAt(i)
            if ((child.tag as? String)?.startsWith(ADDED_ROW_TAG) == true) body.removeViewAt(i)
        }
        val added = mutableListOf<Pair<AboutPhoneRow, String>>()
        AboutPhoneConfig.fields.forEach { field ->
            val row = settings.row(field.key) ?: return@forEach
            val views = field.viewNames.mapNotNull { AboutPhoneViews.find(root, it) }
            views.forEach { view ->
                if (row.visibility == AboutPhoneVisibility.SHOW && row.title == null) {
                    val titleView = view.callOrNull("getTitleView") as? TextView
                    if (titleView?.text.isNullOrEmpty()) titleView?.text = nativeTitle(root, field)
                }
                applyRow(view, row)
            }
            if (row.visibility == AboutPhoneVisibility.SHOW && views.isEmpty()) {
                // Условный пункт (например, чип) мог не создаться для этой модели.
                // Добавляем визуальный пункт без вызова аппаратного контроллера.
                added += row to nativeTitle(root, field)
            }
        }
        settings.rows.filter { it.key.startsWith(AboutPhoneConfig.CUSTOM_PREFIX) &&
            it.visibility != AboutPhoneVisibility.HIDE }.forEach { row ->
            added += row to row.title.orEmpty()
        }
        // Собственные строки располагаются после характеристик, перед нижними ссылками.
        val battery = AboutPhoneViews.find(root, "device_info_battery")
        var position = if (battery?.parent === body) body.indexOfChild(battery) + 1 else body.childCount
        val rowClass = findClassOrNull(AboutPhoneViews.ROW_CLASS)
        if (rowClass != null) {
            val constructor = rowClass.getConstructor(android.content.Context::class.java)
            added.forEach { (row, title) ->
                val view = constructor.newInstance(root.context) as View
                view.tag = ADDED_ROW_TAG + row.key
                view.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (30 * root.resources.displayMetrics.density).toInt()
                }
                applyRow(view, row.copy(title = row.title ?: title,
                    value = row.value ?: "", visibility = AboutPhoneVisibility.SHOW))
                body.addView(view, position++)
            }
        }
        // Не оставляем пустые блоки после скрытия всех их пунктов.
        for (name in listOf("rom_storage", "other_info")) {
            val group = AboutPhoneViews.find(root, name) as? ViewGroup ?: continue
            val visible = (0 until group.childCount).any { group.getChildAt(it).visibility != View.GONE }
            group.visibility = if (visible) View.VISIBLE else View.GONE
        }
        val advisor = AboutPhoneViews.find(root, "user_advisor_plan")
        if (advisor?.visibility == View.GONE) AboutPhoneViews.find(root, "user_advisor_plan_divider")?.visibility = View.GONE
    }

    /** Берём локализованную подпись из APK Settings, а не из ресурсов модуля. */
    private fun nativeTitle(root: View, field: AboutPhoneField): String {
        val resourceName = when (field.key) {
            "cpu" -> "cpu_info"
            "chip", "image_chip" -> "vivo_about_phone_chip"
            "ram" -> "running_ram"
            "storage" -> "phone_mount"
            "device_name" -> "vivo_origin_device_name_title"
            "battery" -> "battery_capacity"
            "storage_compress" -> "vivo_storage_compress"
            "version" -> "version_info_title"
            "status" -> "status_info_title"
            "legal" -> "legal_info_title"
            "advisor" -> "user_advisor_plan_title"
            else -> return ""
        }
        val id = root.resources.getIdentifier(resourceName, "string", AboutPhoneViews.PACKAGE)
        return if (id != 0) root.resources.getString(id) else ""
    }
}
