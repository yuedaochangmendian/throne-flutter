package io.nekohasekai.sagernet.glass

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * The window root the app draws on: wallpaper, readability scrim, then the activity's layout.
 *
 * Wrapping happens in `Activity.onContentChanged`, so it covers all 60 layouts of the app without
 * touching a single one of them, and every `findViewById` keeps working because the original view
 * is simply re-parented.
 */
class GlassHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    val tokens: GlassTokens = GlassTokens.resolve(context)

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        GlassWallpaper.draw(canvas, width, height, tokens)
        // A scrim between wallpaper and content: the glass still glows through, but body text keeps
        // its contrast, which is the difference between "iOS 26" and "unreadable soup".
        canvas.drawColor(tokens.scrim)
    }
}

object GlassHost {

    /** Set to false to keep the plain Material surfaces (a build flag, handy for A/B testing). */
    var enabled = true

    /** 从任意 View 往上找窗口根部的宿主；玻璃用它当采样源。 */
    fun hostOf(view: View): GlassHostLayout? {
        var parent = view.parent
        while (parent is View) {
            if (parent is GlassHostLayout) return parent
            parent = parent.parent
        }
        return null
    }

    /**
     * Wraps the activity's content in a [GlassHostLayout]. Safe to call on every content change:
     * it is a no-op once wrapped.
     */
    fun wrap(activity: Activity): GlassHostLayout? {
        if (!enabled) return null
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        if (content.childCount == 0) return null
        val current = content.getChildAt(0)
        if (current is GlassHostLayout) return current

        content.removeViewAt(0)
        val host = GlassHostLayout(activity)
        host.addView(
            current,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        content.addView(
            host,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        // The window must not paint an opaque layer over the wallpaper.
        activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return host
    }
}
