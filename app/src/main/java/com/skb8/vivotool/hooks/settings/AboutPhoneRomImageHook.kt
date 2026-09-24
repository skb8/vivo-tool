package com.skb8.vivotool.hooks.settings

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.HookImages
import com.skb8.vivotool.core.HookParam
import com.skb8.vivotool.core.ImageKeys
import com.skb8.vivotool.core.XLog
import java.io.ByteArrayInputStream

/**
 * Подменяет картинку карточки «О телефоне» в настройках.
 *
 * Целевой ресурс — `about_phone_origin_os_rom15` (в APK настроек это
 * `res/ZsR.webp`, размер [TARGET_WIDTH]×[TARGET_HEIGHT]).
 *
 * Ресурс подменяется не через `initPackageResources` (в LSPosed резервные
 * хуки ресурсов ненадёжны), а перехватом загрузки drawable: `Resources.loadDrawable`
 * покрывает и `getDrawable(id)`, и `android:src` из XML-разметки, а
 * `openRawResource` — декодирование через `BitmapFactory.decodeResource`.
 */
object AboutPhoneRomImageHook : BaseHook() {

    const val ID = "settings_about_phone_rom_image"

    /** Имя ресурса в APK настроек. */
    const val RESOURCE_NAME = "about_phone_origin_os_rom15"

    /** Размер оригинального ресурса — под него обрезается выбранная картинка. */
    const val TARGET_WIDTH = 936
    const val TARGET_HEIGHT = 618

    private const val SETTINGS_PACKAGE = "com.android.settings"

    private val resourceTypes = listOf("drawable", "mipmap", "raw")

    @Volatile
    private var resourceId = 0

    private var scaledCache: Bitmap? = null
    private var scaledSource: Bitmap? = null

    override val id: String = ID

    override val titleRes: Int = R.string.hook_about_phone_title

    override val descriptionRes: Int = R.string.hook_about_phone_description

    override val targetPackages: Set<String> = setOf(SETTINGS_PACKAGE)

    override fun onHook() {
        val resourcesClass = Resources::class.java

        // 1. Основной путь: и getDrawable(), и android:src из XML-разметки приходят сюда.
        val loadDrawableHooks = resourcesClass.hookAllAfter("loadDrawable", ::onDrawableLoaded)
        if (loadDrawableHooks.isEmpty()) {
            // Скрытого loadDrawable нет — работаем по публичному API.
            XLog.w("[$id] Resources.loadDrawable не найден, используем публичные методы")
            resourcesClass.hookAllAfter("getDrawable", ::onDrawableLoaded)
            resourcesClass.hookAllAfter("getDrawableForDensity", ::onDrawableLoaded)
        }

        // 2. Внутренняя реализация Android 7+: ResourcesImpl.loadDrawable
        findClassOrNull("android.content.res.ResourcesImpl")?.hookAllAfter("loadDrawable", ::onDrawableLoaded)

        // 3. BitmapFactory.decodeResource() и прочее чтение ресурса как потока байт.
        resourcesClass.hookAllBefore("openRawResource") { hookParam ->
            val res = (hookParam.thisObject as? Resources)
                ?: (hookParam.args.firstNotNullOfOrNull { it as? Resources })
                ?: return@hookAllBefore
            val requestedId = requestedId(hookParam.args) ?: return@hookAllBefore
            if (!isTarget(res, requestedId)) return@hookAllBefore

            HookImages.bytes(ImageKeys.ABOUT_PHONE_ROM)?.let {
                hookParam.result = ByteArrayInputStream(it)
            }
        }

        afterApplicationCreated { app -> resolveId(app.resources) }
    }

    private fun onDrawableLoaded(hookParam: HookParam) {
        val res = (hookParam.thisObject as? Resources)
            ?: (hookParam.args.firstNotNullOfOrNull { it as? Resources })
            ?: return
        val requestedId = requestedId(hookParam.args) ?: return
        if (!isTarget(res, requestedId)) return

        replacementDrawable(res, hookParam.result as? Drawable)?.let { hookParam.result = it }
    }

    /** Идентификатор ресурса — первый int или TypedValue.resourceId среди аргументов метода. */
    private fun requestedId(args: Array<Any?>?): Int? {
        if (args == null) return null
        for (arg in args) {
            when (arg) {
                is Int -> if (arg != 0) return arg
                is android.util.TypedValue -> if (arg.resourceId != 0) return arg.resourceId
            }
        }
        return null
    }

    private fun isTarget(res: Resources, requestedId: Int): Boolean {
        if (requestedId == 0) return false
        if (resourceId == 0) resolveId(res)
        if (resourceId != 0 && requestedId == resourceId) return true

        try {
            val entryName = res.getResourceEntryName(requestedId)
            if (entryName == RESOURCE_NAME) {
                resourceId = requestedId
                return true
            }
        } catch (_: Resources.NotFoundException) {
        }
        return false
    }

    private fun resolveId(res: Resources) {
        val resolved = resourceTypes.firstNotNullOfOrNull { type ->
            res.getIdentifier(RESOURCE_NAME, type, SETTINGS_PACKAGE).takeIf { it != 0 }
        } ?: return
        resourceId = resolved
        XLog.d("[$id] $RESOURCE_NAME -> 0x${Integer.toHexString(resolved)}")
    }

    /**
     * Собирает drawable из выбранной картинки. Размер подгоняется под оригинал,
     * чтобы вёрстка карточки не поехала.
     */
    private fun replacementDrawable(res: Resources, original: Drawable?): Drawable? {
        val source = HookImages.bitmap(ImageKeys.ABOUT_PHONE_ROM) ?: return null
        val width = original?.intrinsicWidth?.takeIf { it > 0 } ?: TARGET_WIDTH
        val height = original?.intrinsicHeight?.takeIf { it > 0 } ?: TARGET_HEIGHT

        val bitmap = synchronized(this) {
            val cached = scaledCache
            if (cached != null && scaledSource === source &&
                cached.width == width && cached.height == height
            ) {
                cached
            } else {
                Bitmap.createScaledBitmap(source, width, height, true).also {
                    it.density = res.displayMetrics.densityDpi
                    scaledCache = it
                    scaledSource = source
                }
            }
        }

        return BitmapDrawable(res, bitmap)
    }
}
