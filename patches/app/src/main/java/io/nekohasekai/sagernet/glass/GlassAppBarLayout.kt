package io.nekohasekai.sagernet.glass

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import com.google.android.material.appbar.AppBarLayout

/**
 * Drop-in replacement for `AppBarLayout` in `layout_appbar.xml`.
 *
 * The toolbar keeps its Material behaviour (scroll flags, insets, menu); only the opaque
 * `colorPrimary` fill is replaced by glass. The bottom corners are rounded so the bar reads as a
 * floating sheet when a list scrolls under it.
 */
class GlassAppBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppBarLayout(context, attrs, defStyleAttr) {

    val tokens: GlassTokens = GlassTokens.resolve(context)

    init {
        setBackgroundColor(Color.TRANSPARENT)
        background = GlassDrawable(
            context, tokens,
            cornerRadiusDp = 0f,
            corners = GlassDrawable.Corners.None,
            tintTop = tokens.chromeTop,
            tintBottom = tokens.chromeBottom,
            borderTop = Color.TRANSPARENT,
            borderBottom = tokens.chromeBorderBottom,
        ).bindTo(this)
    }
}
