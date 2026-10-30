package io.nekohasekai.sagernet.glass

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import io.nekohasekai.sagernet.R

/**
 * Quality tiers, mirroring the tiers liquid_glass_widgets exposes on Flutter.
 *
 * The Flutter library can lean on Impeller shaders; a native Android build has to work on
 * GPUs from API 24 upwards, so the tiers here are about how much of the pipeline is drawn.
 */
enum class GlassQuality {
    /** Tint + hairline only. No backdrop blur: for very large surfaces or when the device is struggling. */
    Minimal,

    /** Backdrop blur + tint + hairline. The default. */
    Standard,

    /** Reserved for the AGSL lens shader (API 33+, opt in). Falls back to Standard when unavailable. */
    Premium,
}

/**
 * The design tokens of the glass system, derived from the active theme instead of hard coded.
 *
 * Throne ships 24 colour themes plus Monet, AMOLED and a White theme that turns readables black.
 * Sampling `colorPrimary` / `colorBackground` keeps every one of them on-brand without touching
 * the theme files: only the surface recipe lives here.
 *
 * Chrome (app bar, stats bar, FAB) is always *smoked* glass: a darkened, translucently tinted
 * primary. That is what lets the existing white-on-primary text stay untouched and readable in
 * all 24 themes, including White (whose primary is near white and darkens to a mid grey here).
 */
data class GlassTokens(
    val dark: Boolean,
    val amoled: Boolean,
    val chromeLight: Boolean,
    /** App bar / stats bar / FAB tint, top and bottom of the vertical gradient. */
    val chromeTop: Int,
    val chromeBottom: Int,
    val chromeBorderTop: Int,
    val chromeBorderBottom: Int,
    /** Cards, drawer rows, dialogs: follow the theme's surface instead of the primary. */
    val cardTop: Int,
    val cardBottom: Int,
    val cardBorderTop: Int,
    val cardBorderBottom: Int,
    /** Readability scrim painted over the wallpaper, below the content. */
    val scrim: Int,
    val wallpaperTop: Int,
    val wallpaperBottom: Int,
    val blobWarm: Int,
    val blobPrimary: Int,
    val blobCool: Int,
    /** Text and icons drawn on chrome glass. */
    val onGlass: Int,
    val blurRadiusDp: Float,
    val downscale: Float,
    val chromeCornerDp: Float,
    val cardCornerDp: Float,
) {

    companion object {

        private val WHITE = 0xFFFFFFFF.toInt()
        private val BLACK = 0xFF000000.toInt()

        fun resolve(context: Context): GlassTokens {
            val night = (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val primary = MaterialColors.getColor(
                context, R.attr.colorPrimary, if (night) BLACK else WHITE
            )
            val background = MaterialColors.getColor(
                context, android.R.attr.colorBackground, if (night) BLACK else WHITE
            )
            val amoled = night && ColorUtils.calculateLuminance(background) < 0.01f
            // White theme: a light primary needs more darkening to keep white text readable.
            val chromeLight = ColorUtils.calculateLuminance(primary) > 0.6f

            val chromeBase = ColorUtils.blendARGB(
                primary, BLACK, if (chromeLight) 0.52f else 0.62f
            )
            val chromeTop = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(chromeBase, WHITE, 0.14f), 0x99
            )
            val chromeBottom = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(chromeBase, BLACK, 0.34f), 0xB8
            )

            val cardTop: Int
            val cardBottom: Int
            if (night) {
                cardTop = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(primary, WHITE, 0.10f), 0x3D
                )
                cardBottom = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(primary, BLACK, 0.55f), 0x59
                )
            } else {
                cardTop = ColorUtils.setAlphaComponent(WHITE, 0x9E)
                cardBottom = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(primary, WHITE, 0.82f), 0x85
                )
            }

            val cardBorderTop = ColorUtils.setAlphaComponent(WHITE, if (night) 0x3D else 0xB3)
            val cardBorderBottom = ColorUtils.setAlphaComponent(
                if (night) WHITE else ColorUtils.blendARGB(primary, BLACK, 0.35f),
                if (night) 0x14 else 0x26
            )

            val wallTop = ColorUtils.blendARGB(
                primary, if (night) 0xFF07070C.toInt() else WHITE, if (night) 0.74f else 0.84f
            )
            val wallBottom = if (night) {
                if (amoled) BLACK else ColorUtils.blendARGB(primary, 0xFF0A0A12.toInt(), 0.91f)
            } else {
                ColorUtils.blendARGB(primary, WHITE, 0.95f)
            }

            return GlassTokens(
                dark = night,
                amoled = amoled,
                chromeLight = chromeLight,
                chromeTop = chromeTop,
                chromeBottom = chromeBottom,
                chromeBorderTop = ColorUtils.setAlphaComponent(WHITE, if (night) 0x59 else 0x66),
                chromeBorderBottom = ColorUtils.setAlphaComponent(WHITE, if (night) 0x1A else 0x26),
                cardTop = cardTop,
                cardBottom = cardBottom,
                cardBorderTop = cardBorderTop,
                cardBorderBottom = cardBorderBottom,
                scrim = ColorUtils.setAlphaComponent(
                    if (night) BLACK else WHITE, if (night) 0x4D else 0x33
                ),
                wallpaperTop = wallTop,
                wallpaperBottom = wallBottom,
                blobWarm = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(primary, WHITE, 0.28f), if (night) 0x5C else 0x4D
                ),
                blobPrimary = ColorUtils.setAlphaComponent(primary, if (night) 0x4D else 0x3D),
                blobCool = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(primary, 0xFF3B82F6.toInt(), 0.62f),
                    if (night) 0x47 else 0x33
                ),
                onGlass = WHITE,
                blurRadiusDp = 26f,
                downscale = 0.22f,
                chromeCornerDp = 0f,
                cardCornerDp = 20f,
            )
        }
    }
}
