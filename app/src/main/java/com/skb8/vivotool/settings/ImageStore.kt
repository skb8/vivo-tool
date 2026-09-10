package com.skb8.vivotool.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import com.skb8.vivotool.core.Constants
import com.skb8.vivotool.core.ImageKeys
import com.skb8.vivotool.core.XLog
import java.io.ByteArrayOutputStream

/**
 * Хранилище картинок со стороны приложения.
 *
 * Пишем в world-readable `SharedPreferences`, откуда картинку читает
 * `HookImages` внутри процесса целевого приложения.
 */
object ImageStore {

    private const val WEBP_QUALITY = 95

    private fun prefs(context: Context): SharedPreferences = try {
        @Suppress("DEPRECATION", "WorldReadableFiles")
        context.getSharedPreferences(Constants.IMAGE_PREFS_NAME, Context.MODE_WORLD_READABLE)
    } catch (_: Throwable) {
        context.getSharedPreferences(Constants.IMAGE_PREFS_NAME, Context.MODE_PRIVATE)
    }.also {
        WorldReadable.fix(context, Constants.IMAGE_PREFS_NAME)
    }

    /** Сохраняет картинку. Возвращает false, если не удалось сжать или записать. */
    fun save(context: Context, key: String, bitmap: Bitmap): Boolean {
        val bytes = compress(bitmap) ?: return false
        return try {
            val saved = prefs(context).edit()
                .putString(key, Base64.encodeToString(bytes, Base64.NO_WRAP))
                .putLong(ImageKeys.updatedKey(key), System.currentTimeMillis())
                .commit()
            if (saved) {
                WorldReadable.fix(context, Constants.IMAGE_PREFS_NAME)
            }
            saved
        } catch (t: Throwable) {
            XLog.e("Не удалось сохранить картинку $key", t)
            false
        }
    }

    /** Удаляет картинку — целевое приложение вернётся к оригинальному ресурсу. */
    fun clear(context: Context, key: String) {
        prefs(context).edit()
            .remove(key)
            .remove(ImageKeys.updatedKey(key))
            .commit()
        WorldReadable.fix(context, Constants.IMAGE_PREFS_NAME)
    }

    fun load(context: Context, key: String): Bitmap? {
        val encoded = prefs(context).getString(key, null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (t: Throwable) {
            XLog.e("Не удалось прочитать картинку $key", t)
            null
        }
    }

    fun has(context: Context, key: String): Boolean =
        prefs(context).getLong(ImageKeys.updatedKey(key), 0L) != 0L

    /** Размер сохранённой картинки в байтах — показываем в UI. */
    fun sizeBytes(context: Context, key: String): Int =
        prefs(context).getString(key, null)?.length?.let { it / 4 * 3 } ?: 0

    private fun compress(bitmap: Bitmap): ByteArray? {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        return try {
            ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(format, WEBP_QUALITY, out)) {
                    ByteArrayOutputStream().use { fallback ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fallback)
                        fallback.toByteArray()
                    }
                } else {
                    out.toByteArray()
                }
            }
        } catch (t: Throwable) {
            XLog.e("Не удалось сжать картинку", t)
            null
        }
    }
}
