package com.skb8.vivotool.hooks.settings

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import com.skb8.vivotool.R
import com.skb8.vivotool.core.BaseHook
import com.skb8.vivotool.core.SystemProperties
import com.skb8.vivotool.core.XLog

/** Цвета берутся из нижнего края реально показанного фото, включая подмену картинки. */
object AboutPhoneGradientHook : BaseHook() {
    const val ID = "settings_about_phone_dynamic_gradient"
    override val id = ID
    override val titleRes = R.string.hook_about_gradient_title
    override val descriptionRes = R.string.hook_about_gradient_description
    override val targetPackages = setOf(AboutPhoneViews.PACKAGE)
    override val enabledByDefault = false
    override fun isSupported(sdkInt: Int) = super.isSupported(sdkInt) && SystemProperties.releaseVersionMajor >= 16

    override fun onHook() {
        val controller = findClassOrNull("${AboutPhoneViews.CONTROLLERS}theme.VivoDeviceInfoOriginImageController")
        if (controller == null) {
            XLog.w("[$id] контроллер изображения не найден")
            return
        }
        controller.hookAfter("bindView", View::class.java) { param ->
            val root = param.args.firstOrNull() as? View ?: return@hookAfter
            updateGradient(root)
        }
    }

    private fun updateGradient(root: View) {
        val image = AboutPhoneViews.find(root, "origin_logo_image") as? ImageView ?: return
        val background = AboutPhoneViews.find(root, "origin_logo_image_bg") as? ImageView ?: return
        val photo = image.drawable ?: return
        val original = background.drawable ?: return
        // Маленький образец не создаёт полноразмерную копию фото в UI-потоке.
        val sample = photo.toBitmap(96, 48)
        val colors = IntArray(3) { band ->
            var red = 0L
            var green = 0L
            var blue = 0L
            var weight = 0L
            for (y in 28 until sample.height) {
                for (x in band * 32 until (band + 1) * 32) {
                    val pixel = sample.getPixel(x, y)
                    val alpha = Color.alpha(pixel).toLong()
                    red += Color.red(pixel) * alpha
                    green += Color.green(pixel) * alpha
                    blue += Color.blue(pixel) * alpha
                    weight += alpha
                }
            }
            if (weight == 0L) Color.TRANSPARENT else
                Color.rgb((red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt())
        }
        // toBitmap может вернуть исходный bitmap при совпадении размеров: не recycle.
        background.setImageDrawable(PhotoGradientDrawable(
            colors,
            original.intrinsicWidth.coerceAtLeast(1),
            original.intrinsicHeight.coerceAtLeast(1)
        ))
        XLog.d("[$id] градиент рассчитан по текущему изображению")
    }

    /** Сохраняет размер штатного фона; его компоновкой продолжает управлять Settings. */
    private class PhotoGradientDrawable(
        private val colors: IntArray,
        private val imageWidth: Int,
        private val imageHeight: Int
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun getIntrinsicWidth() = imageWidth
        override fun getIntrinsicHeight() = imageHeight

        override fun onBoundsChange(bounds: Rect) {
            val color = LinearGradient(bounds.left.toFloat(), 0f, bounds.right.toFloat(), 0f,
                colors, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            val fade = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(),
                intArrayOf(Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, 160), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            paint.shader = ComposeShader(color, fade, PorterDuff.Mode.DST_IN)
        }

        override fun draw(canvas: Canvas) { canvas.drawRect(bounds, paint) }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Suppress("DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
