package io.nekohasekai.sagernet.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * The wallpaper the whole app floats on.
 *
 * Liquid Glass needs something to refract: on iOS that is your home screen, here it is a mesh
 * gradient derived from the theme's primary colour. It is rendered once per size/theme into a
 * half-resolution bitmap (blobs are low frequency, upscaling costs nothing) and stretched, so the
 * per-frame cost is a single textured quad.
 */
object GlassWallpaper {

    private var cached: Bitmap? = null
    private var cacheKey = 0
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dest = RectF()

    fun draw(canvas: Canvas, width: Int, height: Int, tokens: GlassTokens) {
        if (width <= 0 || height <= 0) return
        val key = (width shl 12) xor height xor tokens.hashCode()
        var bitmap = cached
        if (bitmap == null || cacheKey != key || bitmap.isRecycled) {
            val w = (width * 0.34f).toInt().coerceAtLeast(2)
            val h = (height * 0.34f).toInt().coerceAtLeast(2)
            bitmap = render(w, h, tokens)
            cached = bitmap
            cacheKey = key
        }
        dest.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bitmap, null, dest, blit)
    }

    private fun render(w: Int, h: Int, tokens: GlassTokens): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val wf = w.toFloat()
        val hf = h.toFloat()

        val base = Paint(Paint.ANTI_ALIAS_FLAG)
        base.shader = LinearGradient(
            0f, 0f, wf * 0.25f, hf,
            tokens.wallpaperTop, tokens.wallpaperBottom, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, wf, hf, base)

        // Three overlapping lights: a warm one behind the app bar, the theme colour on the trailing
        // edge, and a cool one at the bottom. Together they give the blur something to bleed.
        blob(canvas, wf, hf, tokens.blobWarm, 0.02f, 0.04f, 0.95f)
        blob(canvas, wf, hf, tokens.blobPrimary, 0.96f, 0.28f, 0.80f)
        blob(canvas, wf, hf, tokens.blobCool, 0.34f, 1.02f, 1.00f)

        val shade = Paint(Paint.ANTI_ALIAS_FLAG)
        shade.shader = LinearGradient(
            0f, hf * 0.55f, 0f, hf,
            Color.TRANSPARENT,
            if (tokens.dark) 0x66000000 else 0x14000000,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, hf * 0.55f, wf, hf, shade)

        return bitmap
    }

    private fun blob(
        canvas: Canvas,
        w: Float,
        h: Float,
        color: Int,
        cx: Float,
        cy: Float,
        size: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = maxOf(w, h) * 0.5f * size
        paint.shader = RadialGradient(
            w * cx, h * cy, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}
