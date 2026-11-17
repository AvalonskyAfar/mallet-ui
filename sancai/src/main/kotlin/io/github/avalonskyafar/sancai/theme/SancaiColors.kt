package io.github.avalonskyafar.sancai.theme

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import kotlin.math.roundToInt

/**
 * Neutral and semantic colour roles of a Sancai theme.
 *
 * The light values are the white porcelain canvas of the original app: canvas, surface and
 * raised are all pure white, and depth comes from hairlines and short shadows instead of grey
 * tints. The dark values are a blue-black slate with three real elevation steps.
 *
 * @property canvas page background (origin: L1)
 * @property surface card body (origin: S1)
 * @property raised thumbs and other elements that sit above a surface (origin: K3)
 * @property ink primary text and icon colour; also the tint of every shadow in light themes
 * @property muted secondary text, chevrons and navigation icons
 * @property hairline the single edge stroke of cards and capsules
 * @property divider full-bleed separator inside a surface (origin: D1)
 * @property accent text-level accent used for links and confirming dialog actions
 * @property danger destructive fills and text
 * @property shadow reference shadow colour kept for custom drawing
 */
public data class SancaiColors(
    val isDark: Boolean,
    @ColorInt val canvas: Int,
    @ColorInt val surface: Int,
    @ColorInt val raised: Int,
    @ColorInt val ink: Int,
    @ColorInt val muted: Int,
    @ColorInt val hairline: Int,
    @ColorInt val divider: Int,
    @ColorInt val accent: Int,
    @ColorInt val danger: Int,
    @ColorInt val warning: Int,
    @ColorInt val success: Int,
    @ColorInt val shadow: Int,
) {
    public companion object {
        /** Share of [ink] composited onto [canvas] to produce the default [divider]. */
        public const val DIVIDER_INK_SHARE: Float = 0.14f

        @JvmStatic
        @JvmOverloads
        public fun light(highContrast: Boolean = false): SancaiColors {
            val canvas = Color.rgb(255, 255, 255)
            val ink = Color.rgb(26, 28, 31)
            val hairline = if (highContrast) Color.rgb(209, 210, 210) else Color.rgb(237, 237, 237)
            val danger = Color.rgb(200, 88, 88)
            val success = Color.rgb(46, 145, 104)
            return SancaiColors(
                isDark = false,
                canvas = canvas,
                surface = Color.rgb(255, 255, 255),
                raised = Color.rgb(255, 255, 255),
                ink = ink,
                muted = if (highContrast) Color.rgb(76, 78, 80) else Color.rgb(142, 143, 144),
                hairline = hairline,
                divider = if (highContrast) hairline else blend(canvas, ink, DIVIDER_INK_SHARE),
                accent = Color.rgb(49, 90, 155),
                // High contrast deepens the two roles that fall short of 4.5 : 1 on white.
                danger = if (highContrast) blend(danger, ink, 0.08f) else danger,
                warning = Color.rgb(154, 106, 20),
                success = if (highContrast) blend(success, ink, 0.12f) else success,
                shadow = Color.argb(23, 26, 28, 31),
            )
        }

        @JvmStatic
        @JvmOverloads
        public fun dark(highContrast: Boolean = false): SancaiColors {
            val canvas = Color.rgb(24, 29, 36)
            val ink = Color.rgb(235, 239, 244)
            val hairline = if (highContrast) Color.rgb(88, 101, 118) else Color.rgb(54, 64, 77)
            val accent = Color.rgb(91, 126, 164)
            val danger = Color.rgb(151, 89, 96)
            val warning = Color.rgb(157, 128, 75)
            val success = Color.rgb(89, 133, 105)
            return SancaiColors(
                isDark = true,
                canvas = canvas,
                surface = Color.rgb(31, 37, 46),
                raised = Color.rgb(38, 45, 56),
                ink = ink,
                muted = if (highContrast) Color.rgb(201, 208, 217) else Color.rgb(161, 172, 187),
                hairline = hairline,
                divider = if (highContrast) hairline else blend(canvas, ink, DIVIDER_INK_SHARE),
                // High contrast lifts the muted semantic colours until they read as text.
                accent = if (highContrast) blend(accent, ink, 0.20f) else accent,
                danger = if (highContrast) blend(danger, ink, 0.30f) else danger,
                warning = if (highContrast) blend(warning, ink, 0.10f) else warning,
                success = if (highContrast) blend(success, ink, 0.15f) else success,
                shadow = Color.argb(100, 0, 0, 0),
            )
        }

        /** Opaque linear mix of two colours; [secondShare] is the weight of [second]. */
        @JvmStatic
        @ColorInt
        public fun blend(
            @ColorInt first: Int,
            @ColorInt second: Int,
            @FloatRange(from = 0.0, to = 1.0) secondShare: Float,
        ): Int {
            val a = secondShare.coerceIn(0f, 1f)
            return Color.rgb(
                (Color.red(first) * (1 - a) + Color.red(second) * a).roundToInt(),
                (Color.green(first) * (1 - a) + Color.green(second) * a).roundToInt(),
                (Color.blue(first) * (1 - a) + Color.blue(second) * a).roundToInt(),
            )
        }

        /**
         * WCAG 2 contrast ratio of two opaque colours, from 1 (identical) to 21 (black on
         * white). Text needs 4.5 to pass level AA; large text and graphics need 3.
         */
        @JvmStatic
        public fun contrast(@ColorInt first: Int, @ColorInt second: Int): Float {
            val a = luminance(first)
            val b = luminance(second)
            return ((maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)).toFloat()
        }

        private fun luminance(color: Int): Double =
            0.2126 * linear(Color.red(color)) + 0.7152 * linear(Color.green(color)) + 0.0722 * linear(Color.blue(color))

        private fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }

        /** [color] with its alpha channel replaced by [alpha] (clamped to 0..255). */
        @JvmStatic
        @ColorInt
        public fun withAlpha(@ColorInt color: Int, alpha: Int): Int =
            Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}
