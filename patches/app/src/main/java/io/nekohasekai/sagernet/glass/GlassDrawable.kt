package io.nekohasekai.sagernet.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

/**
 * The glass surface itself: a Drawable, so it can be dropped on any View's background without
 * changing its class, which is what makes "glass everywhere" cheap to wire into a 60-layout app.
 *
 * Three layers, in the order iOS 26 composites them:
 *  1. the backdrop, sampled from [GlassBackdrop] and slightly magnified (the lens),
 *  2. a vertical tint: lighter at the top, denser at the bottom (glass thickness),
 *  3. a specular hairline: bright on the top-left, fading to nothing (the lit edge).
 *
 * API 24 has no RenderEffect, so the blur is baked into the backdrop bitmap instead of being
 * applied per view; the result is identical and costs nothing extra per surface.
 */
class GlassDrawable @JvmOverloads constructor(
    context: Context,
    private val tokens: GlassTokens,
    cornerRadiusDp: Float,
    private val corners: Corners = Corners.All,
    private val tintTop: Int = tokens.chromeTop,
    private val tintBottom: Int = tokens.chromeBottom,
    private val borderTop: Int = tokens.chromeBorderTop,
    private val borderBottom: Int = tokens.chromeBorderBottom,
) : Drawable() {

    enum class Corners { All, Top, Bottom, None }

    private val density = context.resources.displayMetrics.density
    private val radiusPx = cornerRadiusDp * density

    private val path = Path()
    private val edgePath = Path()
    private val rect = RectF()
    private val edge = RectF()

    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val matrix = Matrix()
    private val location = IntArray(2)

    private var shader: BitmapShader? = null
    private var shaderSource: Bitmap? = null
    private var viewRef: WeakReference<View>? = null

    private var tintShader: Shader? = null
    private var borderShader: Shader? = null
    private var gradientKey = 0

    private var alphaValue = 255
    private var colorFilterValue: ColorFilter? = null

    /** Attach to the view whose background this is: the backdrop is sampled at the view's position. */
    fun bindTo(view: View): GlassDrawable {
        viewRef = WeakReference(view)
        GlassBackdrop.registerSurface(view)
        return this
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        val r = min(radiusPx, min(b.width(), b.height()) / 2f)
        rect.set(b)
        buildPath(r)

        // 1. Backdrop.
        val frame = GlassBackdrop.frame()
        if (frame != null) {
            val bitmap = frame.bitmap
            if (shaderSource !== bitmap) {
                shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                shaderSource = bitmap
                shaderPaint.shader = shader
            }
            val view = viewRef?.get()
            if (view != null && view.isAttachedToWindow) {
                view.getLocationInWindow(location)
                val s = frame.scale
                // Slight magnification: the lens effect of a thick glass slab. Sampling beyond the
                // view's own edges also hides the clamp smear at the borders.
                val zoom = 1.04f
                matrix.reset()
                matrix.setScale(s, s)
                matrix.postTranslate(b.left - location[0] * s, b.top - location[1] * s)
                matrix.postScale(zoom, zoom, b.exactCenterX(), b.exactCenterY())
                shader?.setLocalMatrix(matrix)
                shaderPaint.alpha = alphaValue
                shaderPaint.colorFilter = colorFilterValue
                canvas.drawPath(path, shaderPaint)
            }
        }

        // 2. Tint. `bounds` is an integer Rect, so the gradient endpoints have to be widened to Float.
        val left = b.left.toFloat()
        val top = b.top.toFloat()
        val right = b.right.toFloat()
        val bottom = b.bottom.toFloat()
        val key = (b.width() shl 16) xor b.height() xor (b.top * 31)
        if (tintShader == null || gradientKey != key) {
            tintShader = LinearGradient(
                left, top, left, bottom, tintTop, tintBottom, Shader.TileMode.CLAMP
            )
            gradientKey = key
        }
        tintPaint.shader = tintShader
        tintPaint.alpha = alphaValue
        tintPaint.colorFilter = colorFilterValue
        canvas.drawPath(path, tintPaint)

        // 3. Specular hairline: the edge catches the light at the top-left and dies out.
        val stroke = max(1f, density * 1.05f)
        borderPaint.strokeWidth = stroke
        edge.set(b)
        edge.inset(stroke / 2f, stroke / 2f)
        if (borderShader == null || gradientKey != key) {
            borderShader = LinearGradient(
                left, top, right, bottom,
                intArrayOf(borderTop, borderTop and 0x00FFFFFF, borderBottom),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        borderPaint.shader = borderShader
        borderPaint.alpha = alphaValue
        borderPaint.colorFilter = colorFilterValue
        drawEdge(canvas, edge, (r - stroke / 2f).coerceAtLeast(0f))
    }

    private fun drawEdge(canvas: Canvas, area: RectF, r: Float) {
        when (corners) {
            Corners.None -> canvas.drawRect(area, borderPaint)
            Corners.All -> canvas.drawRoundRect(area, r, r, borderPaint)
            // Canvas has no per-corner-radii overload; that only exists on Path.
            Corners.Top -> drawPathEdge(canvas, area, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
            Corners.Bottom -> drawPathEdge(canvas, area, floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r))
        }
    }

    private fun drawPathEdge(canvas: Canvas, area: RectF, radii: FloatArray) {
        edgePath.reset()
        edgePath.addRoundRect(area, radii, Path.Direction.CW)
        canvas.drawPath(edgePath, borderPaint)
    }

    private fun buildPath(r: Float) {
        path.reset()
        when (corners) {
            Corners.None -> path.addRect(rect, Path.Direction.CW)
            Corners.All -> path.addRoundRect(rect, r, r, Path.Direction.CW)
            Corners.Top -> path.addRoundRect(
                rect, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW
            )
            Corners.Bottom -> path.addRoundRect(
                rect, floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r), Path.Direction.CW
            )
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        gradientKey = 0
        tintShader = null
        borderShader = null
    }

    override fun getOutline(outline: Outline) {
        val r = min(radiusPx, min(bounds.width(), bounds.height()) / 2f)
        outline.setRoundRect(bounds, r)
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        colorFilterValue = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
