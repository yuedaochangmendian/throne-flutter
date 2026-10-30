package io.nekohasekai.sagernet.glass

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.drawerlayout.widget.DrawerLayout
import io.nekohasekai.sagernet.R

/**
 * Wiring for the app's chrome. Everything here is additive: no view class changes, no layout
 * rewrites, so a bad build can be reverted file by file.
 */
object GlassDecor {

    /** Applies glass to the main window's floating chrome: stats bar, FAB and drawer panels. */
    fun installMain(activity: Activity) {
        if (!GlassHost.enabled) return
        val tokens = GlassTokens.resolve(activity)

        // Stats bar: smoked glass with rounded top corners. Replacing the BottomAppBar background
        // also drops its FAB cradle, which is exactly what the floating-button look wants.
        activity.findViewById<View>(R.id.stats)?.let { bar ->
            bar.backgroundTintList = null
            bar.background = GlassDrawable(
                activity, tokens,
                cornerRadiusDp = tokens.cardCornerDp,
                corners = GlassDrawable.Corners.Top,
                tintTop = tokens.chromeTop,
                tintBottom = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(tokens.chromeBottom, Color.BLACK, 0.15f), 0xC4
                ),
            ).bindTo(bar)
        }

        // FAB: a thicker slab of the same glass, so it still reads as the primary action.
        activity.findViewById<View>(R.id.fab)?.let { fab ->
            fab.backgroundTintList = null
            fab.background = GlassDrawable(
                activity, tokens,
                cornerRadiusDp = 28f,
                corners = GlassDrawable.Corners.All,
                tintTop = ColorUtils.setAlphaComponent(tokens.chromeTop, 0xD9),
                tintBottom = ColorUtils.setAlphaComponent(tokens.chromeBottom, 0xE6),
            ).bindTo(fab)
        }

        // Drawer: one panel of glass over the frozen content behind it. The backdrop is refreshed
        // while the drawer slides so it does not look like a screenshot.
        val drawer = activity.findViewById<DrawerLayout>(R.id.drawer_layout)
        val panels = listOfNotNull(
            activity.findViewById<View>(R.id.nav_view),
            activity.findViewById<View>(R.id.nav_view_black),
        )
        panels.forEach { panel ->
            panel.background = GlassDrawable(
                activity, tokens,
                cornerRadiusDp = 0f,
                corners = GlassDrawable.Corners.None,
                tintTop = tokens.cardTop,
                tintBottom = ColorUtils.setAlphaComponent(tokens.cardBottom, 0xFF),
                borderTop = ColorUtils.setAlphaComponent(Color.WHITE, 0x1F),
                borderBottom = ColorUtils.setAlphaComponent(Color.WHITE, 0x1F),
            ).bindTo(panel)
        }
        drawer?.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                GlassBackdrop.invalidate()
            }
        })
    }

    /**
     * Turns any container into a glass card. Call it on the root of a list item, a card, a
     * preference header: anything that should read as a slab of glass rather than flat colour.
     */
    @JvmOverloads
    fun card(
        view: View,
        radiusDp: Float = GlassTokens.resolve(view.context).cardCornerDp,
        clickable: Boolean = false,
        corners: GlassDrawable.Corners = GlassDrawable.Corners.All,
    ): GlassDrawable {
        val tokens = GlassTokens.resolve(view.context)
        val glass = GlassDrawable(view.context, tokens, radiusDp, corners).bindTo(view)
        if (clickable) {
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusDp * view.resources.displayMetrics.density
            }
            view.background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(Color.WHITE, 0x26)),
                glass, mask
            )
            view.isClickable = true
        } else {
            view.background = glass
        }
        // A glass card needs to be padded by the caller, but it always clears its own outline.
        view.clipToOutline = false
        return glass
    }

    /** Removes padding that Material's card insets add, so the glass edge is the visible edge. */
    fun flush(view: View) {
        view.setPadding(0, 0, 0, 0)
    }

    fun tokens(context: Context): GlassTokens = GlassTokens.resolve(context)
}
