package io.nekohasekai.sagernet.glass

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView

/**
 * 把 [LiquidGlassView] 接到 Throne 的 View 树上。
 *
 * 库的模型是「玻璃是一个容器，内容放在它里面」：玻璃自己负责采样身后的像素、做 SDF 折射和
 * 边缘高光，内容画在玻璃之上。所以这里不做 `background = ...`，而是把目标 View **换成一个
 * 玻璃容器**，原 View 挪进去当唯一子级。好处有三：
 *
 *  - 原 View 的 `LayoutParams` 整个让给玻璃，`CoordinatorLayout` 的 anchor、`DrawerLayout`
 *    的 `layout_gravity`、外边距、weight 全都原样保留，容器位置尺寸一个像素都不变；
 *  - 玻璃采样时会把自己（连同子级）临时设为 INVISIBLE，所以栏里的标题、FAB 的图标不会
 *    在背景底片里留下重影——只有把内容放进玻璃里才有这个效果；
 *  - `findViewById` 照样能找到原来的 View，业务代码零改动。
 *
 * 采样源固定指向 [GlassHostLayout]：库默认只看直接父容器，而我们的壁纸画在窗口根部，
 * 只采父容器会得到一片纯色，折射什么都看不见。
 */
object GlassSurface {

    /** 玻璃外观。默认值对齐 iOS 26 的 Regular 材质：24dp 倒角、12dp 折射高度、0.12 色散。 */
    data class Style(
        val radiusDp: Float = 20f,
        val corners: Corners = Corners.All,
        val tint: Int = 0x33FFFFFF,
        val tintStrength: Float = 0.32f,
        val bevelDp: Float = 24f,
        val refractionDp: Float = 12f,
        val dispersion: Float = 0.12f,
        val clear: Boolean = false,
        val sensorHighlight: Boolean = true,
    ) {
        companion object {
            fun chrome(tokens: GlassTokens, radiusDp: Float = tokens.cardCornerDp) = Style(
                radiusDp = radiusDp,
                tint = tokens.chromeTop,
                tintStrength = 0.34f,
            )

            fun panel(tokens: GlassTokens, radiusDp: Float = 0f) = Style(
                radiusDp = radiusDp,
                tint = tokens.cardTop,
                tintStrength = 0.42f,
                bevelDp = 18f,
                clear = true,
            )
        }
    }

    enum class Corners { All, Top, Bottom, None }

    /**
     * 用玻璃容器替换 [view]，原 View 成为它的内容层。
     *
     * @return 玻璃容器；[view] 没有父容器（还没挂上树）时返回 null。
     */
    fun wrap(view: View, style: Style): LiquidGlassView? {
        if (view is LiquidGlassView) {
            configure(view, style)
            bindBackdrop(view)
            return view
        }
        val parent = view.parent as? ViewGroup ?: return null
        val index = parent.indexOfChild(view)
        val params = view.layoutParams

        parent.removeViewAt(index)

        val glass = LiquidGlassView(view.context)
        configure(glass, style)
        // 原 View 的尺寸约束原样带进玻璃，玻璃再套用原 View 的 LayoutParams 顶替它的位置。
        glass.addView(view, FrameLayout.LayoutParams(params.width, params.height))
        parent.addView(glass, index, params)

        bindBackdrop(glass)
        return glass
    }

    /** 只改外观，不动层级。 */
    fun configure(glass: LiquidGlassView, style: Style) {
        glass.material = if (style.clear) GlassMaterial.CLEAR else GlassMaterial.REGULAR
        glass.bevelWidth = style.bevelDp
        glass.refractionHeight = style.refractionDp
        glass.dispersionStrength = style.dispersion
        glass.setGlassTint(style.tint, style.tintStrength)
        val r = style.radiusDp
        glass.cornerRadiusTopLeft = if (style.corners == Corners.All || style.corners == Corners.Top) r else 0f
        glass.cornerRadiusTopRight = if (style.corners == Corners.All || style.corners == Corners.Top) r else 0f
        glass.cornerRadiusBottomLeft = if (style.corners == Corners.All || style.corners == Corners.Bottom) r else 0f
        glass.cornerRadiusBottomRight = if (style.corners == Corners.All || style.corners == Corners.Bottom) r else 0f
        // 玻璃本身不参与交互，点击照常落到里面的内容上。
        glass.isClickable = false
        glass.isFocusable = false
        glass.clipChildren = false
    }

    /**
     * 采样源指向窗口根部的 [GlassHostLayout]。挂树之前拿不到，所以退化成一次性监听。
     */
    fun bindBackdrop(glass: LiquidGlassView) {
        val host = GlassHost.hostOf(glass)
        if (host != null) {
            glass.backdropSource = host
            return
        }
        glass.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                (v as? LiquidGlassView)?.backdropSource = GlassHost.hostOf(v)
                v.removeOnAttachStateChangeListener(this)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }

    /** 玻璃下面的控件要把自己的不透明底色让出来，否则挡住折射。 */
    fun clearBackground(view: View) {
        view.backgroundTintList = null
        view.background = null
        view.setBackgroundColor(Color.TRANSPARENT)
    }
}
