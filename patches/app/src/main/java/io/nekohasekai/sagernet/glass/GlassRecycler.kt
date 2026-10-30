package io.nekohasekai.sagernet.glass

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R

/**
 * Turns list items into glass cards without touching adapters.
 *
 * Items are glassed when they scroll into view, and marked with [R.id.glass_card] so a rebind does
 * not wrap them twice. Margins are left to the item layout: only the radius is applied here, which
 * keeps dividers, decorations and item bounds exactly as they were.
 */
object GlassRecycler {

    @JvmOverloads
    fun glassItems(
        recyclerView: RecyclerView,
        radiusDp: Float = 16f,
        gapDp: Float = 6f,
    ) {
        val density = recyclerView.resources.displayMetrics.density
        recyclerView.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    if (view.getTag(R.id.glass_card) == true) return
                    view.setTag(R.id.glass_card, true)
                    val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
                    if (lp != null && gapDp > 0f) {
                        // Only spacing that was already zero gets touched: layouts that space their
                        // own items keep full control.
                        if (lp.bottomMargin == 0) lp.bottomMargin = (gapDp * density).toInt()
                        view.layoutParams = lp
                    }
                    GlassDecor.card(view, radiusDp)
                }

                override fun onChildViewDetachedFromWindow(view: View) = Unit
            }
        )
        // Items already on screen when the list is glassed on first layout.
        for (i in 0 until recyclerView.childCount) {
            recyclerView.getChildAt(i)?.let { view ->
                if (view.getTag(R.id.glass_card) != true) {
                    view.setTag(R.id.glass_card, true)
                    GlassDecor.card(view, radiusDp)
                }
            }
        }
    }
}
