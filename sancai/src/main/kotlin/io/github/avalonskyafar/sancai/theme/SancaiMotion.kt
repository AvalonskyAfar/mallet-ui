package io.github.avalonskyafar.sancai.theme

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import kotlin.math.roundToLong

/**
 * Timing and distance tokens shared by every Sancai interaction.
 *
 * There is exactly one easing curve. Everything that moves either follows the finger or
 * settles along [glide], which starts fast and lands softly; nothing bounces.
 */
public object SancaiMotion {
    /** Thumb and glaze settle after a tap or release. */
    public const val CONTROL_MS: Long = 260L

    /** Any animation while motion is reduced. */
    public const val REDUCED_MS: Long = 120L

    /** Programmatic full-distance page transition. */
    public const val PAGE_MS: Long = 420L

    /** Shortest finger-release settle of a page. */
    public const val SETTLE_MIN_MS: Long = 220L

    /** Longest finger-release settle of a page. */
    public const val SETTLE_MAX_MS: Long = 420L

    /** Dialog and sheet entrance and exit. */
    public const val DIALOG_MS: Long = 320L

    /** Toasts, reveal rows, expanders and other small feedback. */
    public const val FEEDBACK_MS: Long = 220L

    /** Small feedback while motion is reduced. */
    public const val FEEDBACK_REDUCED_MS: Long = 90L

    /** Time for pressed content to sink; release is immediate. */
    public const val PRESS_MS: Long = 55L

    /** Distance pressed content sinks. */
    public const val PRESS_SINK_DP: Float = 1.5f

    /** Old-palette snapshot fading over the new theme. */
    public const val THEME_FADE_MS: Long = 300L

    /** A drag past this share of the travel commits on release. */
    public const val COMMIT_FRACTION: Float = 0.28f

    /** A fling faster than this (dp per second, in the travel direction) commits on release. */
    public const val COMMIT_VELOCITY_DP: Float = 320f

    /** The single Sancai easing curve: cubic-bezier(0.22, 0.78, 0.18, 1). */
    @JvmStatic
    public fun glide(): Interpolator = PathInterpolator(0.22f, 0.78f, 0.18f, 1f)

    /** Curve of the theme cross-fade: cubic-bezier(0.2, 0, 0, 1). */
    @JvmStatic
    public fun fade(): Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    @JvmStatic
    public fun controlDuration(reduceMotion: Boolean): Long = if (reduceMotion) REDUCED_MS else CONTROL_MS

    @JvmStatic
    public fun feedbackDuration(reduceMotion: Boolean): Long =
        if (reduceMotion) FEEDBACK_REDUCED_MS else FEEDBACK_MS

    /**
     * Duration for settling the remaining share of a travel (0 = already there, 1 = full
     * distance). A floor keeps the landing readable even when the finger lets go late.
     */
    @JvmStatic
    public fun settleDuration(remainingFraction: Float, reduceMotion: Boolean): Long {
        if (reduceMotion) return REDUCED_MS
        val bounded = remainingFraction.coerceIn(0f, 1f)
        return (210f + bounded * 210f).roundToLong().coerceIn(SETTLE_MIN_MS, SETTLE_MAX_MS)
    }
}
