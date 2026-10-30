package io.nekohasekai.sagernet.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Captures the window once, downscales it and blurs it: the single backdrop every glass surface
 * samples from.
 *
 * One shared backdrop instead of one per surface keeps the cost flat (the app bar, the stats bar,
 * the FAB and every glass card read the same bitmap) and makes the blur continuous across
 * adjacent surfaces, which is what sells the "one sheet of glass" illusion.
 *
 * Capture runs in `OnPreDraw`, never inside a surface's own `draw()`: drawing the hierarchy from
 * within its own draw pass is re-entrant and shows up as flicker or an ANR on slow devices.
 * Surfaces registered with [registerSurface] are hidden (alpha 0) for the duration of the capture
 * so a bar does not blur itself.
 */
object GlassBackdrop {

    private const val TAG = "GlassBackdrop"

    /** Minimum delay between two captures. Lower is smoother, higher saves battery. */
    var captureIntervalMs = 90L

    private var rootRef: WeakReference<View>? = null
    private var bitmap: Bitmap? = null
    private var scale = 1f
    private var dirty = true
    private var capturing = false
    private var lastCaptureAt = 0L
    private var pixelBuffer = IntArray(0)
    private var radiusDp = 26f
    private var downscale = 0.22f

    private val surfaces = WeakHashMap<View, Boolean>()

    class Frame(val bitmap: Bitmap, val scale: Float)

    fun attach(root: View, tokens: GlassTokens) {
        radiusDp = tokens.blurRadiusDp
        downscale = tokens.downscale
        if (rootRef?.get() === root) return
        rootRef = WeakReference(root)
        dirty = true
        bitmap = null
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                maybeCapture()
                return true
            }
        })
        root.viewTreeObserver.addOnScrollChangedListener { dirty = true }
    }

    /** Surfaces hide themselves while the backdrop is captured. Weakly held: nothing to unregister. */
    fun registerSurface(view: View) {
        surfaces[view] = true
        dirty = true
    }

    fun invalidate() {
        dirty = true
    }

    fun frame(): Frame? {
        val bmp = bitmap ?: return null
        return Frame(bmp, scale)
    }

    private fun maybeCapture() {
        if (capturing) return
        val root = rootRef?.get() ?: return
        if (!root.isAttachedToWindow || root.width <= 0 || root.height <= 0) return
        val now = SystemClock.uptimeMillis()
        if (!dirty) return
        if (now - lastCaptureAt < captureIntervalMs) return
        lastCaptureAt = now
        dirty = false
        capture(root)
    }

    private fun capture(root: View) {
        val w = (root.width * downscale).toInt().coerceAtLeast(8)
        val h = (root.height * downscale).toInt().coerceAtLeast(8)
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "backdrop allocation failed", e)
            return
        }
        val s = w.toFloat() / root.width
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        canvas.scale(s, s)

        val hidden = ArrayList<View>(surfaces.size)
        for (view in surfaces.keys) {
            if (view.isAttachedToWindow && view.alpha != 0f) hidden.add(view)
        }
        val alphas = FloatArray(hidden.size)
        for (i in hidden.indices) {
            alphas[i] = hidden[i].alpha
            hidden[i].alpha = 0f
        }

        capturing = true
        try {
            root.draw(canvas)
        } catch (e: RuntimeException) {
            // A view in the tree can refuse a software canvas (a SurfaceView, a detached WebView).
            // The wallpaper-only fallback in GlassDrawable still looks right; never crash for it.
            Log.w(TAG, "backdrop capture failed", e)
        } finally {
            capturing = false
            for (i in hidden.indices) hidden[i].alpha = alphas[i]
        }

        val count = w * h
        val pixels = if (pixelBuffer.size == count) pixelBuffer else IntArray(count).also { pixelBuffer = it }
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val radius = (radiusDp * root.resources.displayMetrics.density * s).toInt()
            .coerceIn(1, (w / 4).coerceAtLeast(1))
        GlassBlur.blur(pixels, w, h, radius, passes = 3)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)

        bitmap = bmp
        scale = s
    }
}
