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
import com.example.liquidglass.LiquidGlassView
import io.nekohasekai.sagernet.R

/**
 * 把 App 的「外框」交给 [LiquidGlassView]：顶栏、底部统计栏、FAB、抽屉、弹窗。
 *
 * 列表内容不铺实时玻璃——每块玻璃每帧都要把身后的内容重画一遍进离屏位图，一屏十几张卡片
 * 就是十几倍开销。列表改用半透明底 + 1dp 高光描边（[card]），观感统一但成本恒定，
 * 这也是 iOS 自己的做法：玻璃只出现在控制和浮层上。
 */
object GlassDecor {

    /** 主界面：顶栏、统计栏、FAB、抽屉。 */
    fun installMain(activity: Activity) {
        if (!GlassHost.enabled) return
        val tokens = GlassTokens.resolve(activity)

        activity.findViewById<LiquidGlassView>(R.id.appbar_glass)?.let { installAppBar(activity, it) }

        // 统计栏：底部一整条，只圆上面两个角。
        activity.findViewById<View>(R.id.stats)?.let { bar ->
            GlassSurface.clearBackground(bar)
            GlassSurface.wrap(
                bar,
                GlassSurface.Style(
                    radiusDp = tokens.cardCornerDp,
                    corners = GlassSurface.Corners.Top,
                    tint = tokens.chromeTop,
                    tintStrength = 0.38f,
                )
            )
        }

        // FAB：更厚的一块同一材质，才压得住下面的统计栏。
        activity.findViewById<View>(R.id.fab)?.let { fab ->
            GlassSurface.clearBackground(fab)
            GlassSurface.wrap(
                fab,
                GlassSurface.Style(
                    radiusDp = 28f,
                    tint = tokens.chromeTop,
                    tintStrength = 0.46f,
                    dispersion = 0.16f,
                )
            )
        }

        // 抽屉：整块玻璃。滑出期间库自己跟着重绘，不需要我们再手动刷新底片。
        listOf(R.id.nav_view, R.id.nav_view_black).forEach { id ->
            activity.findViewById<View>(id)?.let { panel ->
                GlassSurface.clearBackground(panel)
                GlassSurface.wrap(panel, GlassSurface.Style.panel(tokens))
            }
        }
    }

    /** 顶栏玻璃：XML 里已经是容器，这里只补采样源和圆角。 */
    fun installAppBar(activity: Activity, glass: LiquidGlassView) {
        if (!GlassHost.enabled) return
        val tokens = GlassTokens.resolve(activity)
        GlassSurface.configure(
            glass,
            GlassSurface.Style(
                radiusDp = 0f,
                corners = GlassSurface.Corners.None,
                tint = tokens.chromeTop,
                tintStrength = 0.30f,
                bevelDp = 20f,
                refractionDp = 10f,
            )
        )
        GlassSurface.bindBackdrop(glass)
    }

    /**
     * 列表卡片、设置分组的「静态玻璃」：半透明渐变 + 顶部高光描边，不做实时折射。
     *
     * 尺寸、圆角、点击水波纹都保持原来 Material 卡片的行为，只是把不透明填充换成玻璃感底。
     */
    @JvmOverloads
    fun card(
        view: View,
        radiusDp: Float = GlassTokens.resolve(view.context).cardCornerDp,
        clickable: Boolean = false,
        corners: GlassSurface.Corners = GlassSurface.Corners.All,
    ): GradientDrawable {
        val tokens = GlassTokens.resolve(view.context)
        val density = view.resources.displayMetrics.density
        val radiusPx = radiusDp * density
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            colors = intArrayOf(tokens.cardTop, tokens.cardBottom)
            if (corners == GlassSurface.Corners.All) {
                cornerRadius = radiusPx
            } else {
                cornerRadii = floatArrayOf(
                    if (corners == GlassSurface.Corners.Top) radiusPx else 0f,
                    if (corners == GlassSurface.Corners.Top) radiusPx else 0f,
                    if (corners == GlassSurface.Corners.Top) radiusPx else 0f,
                    if (corners == GlassSurface.Corners.Top) radiusPx else 0f,
                    if (corners == GlassSurface.Corners.Bottom) radiusPx else 0f,
                    if (corners == GlassSurface.Corners.Bottom) radiusPx else 0f,
                    if (corners == GlassSurface.Corners.Bottom) radiusPx else 0f,
                    if (corners == GlassSurface.Corners.Bottom) radiusPx else 0f,
                )
            }
            setStroke((density * 1.05f).toInt().coerceAtLeast(1), tokens.cardBorderTop)
        }

        if (clickable) {
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx
            }
            view.background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(Color.WHITE, 0x26)),
                fill, mask
            )
            view.isClickable = true
        } else {
            view.background = fill
        }
        view.clipToOutline = false
        return fill
    }

    /** Removes padding that Material's card insets add, so the glass edge is the visible edge. */
    fun flush(view: View) {
        view.setPadding(0, 0, 0, 0)
    }

    fun tokens(context: Context): GlassTokens = GlassTokens.resolve(context)
}
