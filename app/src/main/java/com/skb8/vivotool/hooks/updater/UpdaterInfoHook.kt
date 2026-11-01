package com.skb8.vivotool.hooks.updater

import android.app.Activity
import android.widget.AdapterView
import android.widget.ListPopupWindow
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.XLog
import com.skb8.vivotool.core.callMethod
import com.skb8.vivotool.core.getField
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Меню проверено в CustomUpdateActivity.initVToolBar: i2.b / i2.a. */
object UpdaterInfoHook : BaseHook() {
    const val ID = "updater_update_info"
    const val PACKAGE = "com.bbk.updater"
    private const val ACTIVITY = "$PACKAGE.ui.UpdateActivity"
    private const val CUSTOM_ACTIVITY = "$PACKAGE.ui.customactivity.CustomUpdateActivity"
    private const val ITEM_TAG = "vivo_tool_update_info"

    override val id = ID
    override val titleRes = R.string.hook_updater_info_title
    override val descriptionRes = R.string.hook_updater_info_description
    override val targetPackages = setOf(PACKAGE)
    override val enabledByDefault = false

    private val menus = WeakHashMap<Any, WeakReference<Activity>>()

    override fun onHook() {
        val activity = findClassOrNull(CUSTOM_ACTIVITY)
        val popup = findClassOrNull("i2.b")
        val item = findClassOrNull("i2.a")
        if (activity == null || popup == null || item == null) {
            XLog.w("[$id] версия меню обновлений не поддерживается")
            return
        }
        UpdaterInfoReader.installParserHooks { name, callback ->
            findClassOrNull("$PACKAGE.utils.CheckResultUtils")?.hookAllAfter(name) { param ->
                val bean = param.result ?: return@hookAllAfter
                val json = param.args.getOrNull(1) as? String ?: return@hookAllAfter
                callback(bean, json)
            }
        }
        popup.hookAfter("show") { param ->
            val window = param.thisObject as? ListPopupWindow ?: return@hookAfter
            val owner = menus[window]?.get() ?: return@hookAfter
            val list = window.listView ?: return@hookAfter
            val original = list.onItemClickListener ?: return@hookAfter
            if (original is DetailsClickListener) return@hookAfter
            list.onItemClickListener = DetailsClickListener(owner, window, original)
        }
        activity.hookAllAfter("initVToolBar") { param ->
            val owner = param.thisObject as? Activity ?: return@hookAllAfter
            if (owner.javaClass.name != ACTIVITY) return@hookAllAfter
            val window = owner.fieldOrNull("mPopupWindow") as? ListPopupWindow ?: return@hookAllAfter
            val existing = window.callMethod("X") as? List<*> ?: return@hookAllAfter
            if (existing.any { it?.callOrNull("b") == ITEM_TAG }) return@hookAllAfter
            val added = item.getConstructor(String::class.java).newInstance(UpdaterInfoText(owner).details)
            added.callMethod("q", ITEM_TAG)
            window.callMethod("J0", ArrayList<Any?>().apply { addAll(existing); add(added) })
            menus[window] = WeakReference(owner)
            XLog.i("[$id] пункт информации добавлен в меню обновлений")
        }
    }

    private class DetailsClickListener(
        activity: Activity,
        popup: ListPopupWindow,
        private val original: AdapterView.OnItemClickListener
    ) : AdapterView.OnItemClickListener {
        private val owner = WeakReference(activity)
        private val window = WeakReference(popup)

        override fun onItemClick(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
            val selected = parent?.getItemAtPosition(position)
            val tag = runCatching { selected?.callMethod("b") }.getOrNull()
            if (tag != ITEM_TAG) {
                original.onItemClick(parent, view, position, id)
                return
            }
            window.get()?.dismiss()
            val activity = owner.get() ?: return
            if (activity.isFinishing || activity.isDestroyed) return
            // Читаем именно presenter открытой Activity, без глобального кэша обновлений.
            val presenter = runCatching { activity.getField("mUpdatePresenter") }.getOrNull()
            runCatching { UpdaterInfoDialog.show(activity, presenter) }.onFailure {
                XLog.e("[$ID] не удалось открыть информацию об обновлении", it)
                android.widget.Toast.makeText(activity, UpdaterInfoText(activity).failed,
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

