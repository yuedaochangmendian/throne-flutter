package io.nekohasekai.sagernet.glass

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.graphics.ColorUtils

/**
 * Window-level glass for dialogs.
 *
 * Unlike an in-app surface, a dialog lives in its own window, so there is nothing to snapshot:
 * the platform can blur whatever is behind it (`setBackgroundBlurRadius`, API 31+). That is both
 * cheaper and more correct than capturing the activity behind the dialog.
 *
 * Below API 31 the panel simply becomes more opaque; the rounded silhouette stays.
 */
object GlassWindows {

    @JvmStatic
    @JvmOverloads
    fun apply(dialog: Dialog, cornerDp: Float = 28f, marginDp: Float = 16f) {
        if (!GlassHost.enabled) return
        val window = dialog.window ?: return
        val context = dialog.context
        val tokens = GlassTokens.resolve(context)
        val density = context.resources.displayMetrics.density
        val blurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        val top = if (blurAvailable) tokens.cardTop else opaque(tokens.cardTop, 0xF2)
        val bottom = if (blurAvailable) tokens.cardBottom else opaque(tokens.cardBottom, 0xF5)

        val panel = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerDp * density
            colors = intArrayOf(top, bottom)
            setStroke(
                (density * 1.05f).toInt().coerceAtLeast(1),
                tokens.cardBorderTop
            )
        }
        window.setBackgroundDrawable(InsetDrawable(panel, (marginDp * density).toInt()))
        window.setDimAmount(if (blurAvailable) 0.28f else 0.42f)

        if (blurAvailable) {
            blurBehind(window)
        }
    }

    /** Blurs everything behind a window. API 31+; a no-op elsewhere. */
    @JvmStatic
    @JvmOverloads
    fun blurBehind(window: Window, radiusDp: Float = 30f) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val density = window.context.resources.displayMetrics.density
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.setBackgroundBlurRadius((radiusDp * density).toInt())
    }

    /** Removes the opaque window layer so the wallpaper behind the activity can show through. */
    @JvmStatic
    fun makeWindowTransparent(window: Window) {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun opaque(color: Int, minAlpha: Int): Int =
        ColorUtils.setAlphaComponent(color, maxOf(Color.alpha(color), minAlpha))
}
