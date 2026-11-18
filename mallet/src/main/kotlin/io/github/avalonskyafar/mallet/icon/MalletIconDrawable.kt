package io.github.avalonskyafar.mallet.icon

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat
import io.github.avalonskyafar.mallet.theme.MalletColors
import io.github.avalonskyafar.mallet.theme.MalletTheme
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A [MalletIcon] as a drawable, for image views, compound drawables and anywhere else a
 * drawable is expected. It honours tint and alpha, and mirrors directional icons in
 * right-to-left layouts.
 *
 * @param color stroke colour; defaults to the muted colour of the theme
 * @param sizeDp intrinsic size; must be positive (anything smaller counts as one pixel)
 * @param strokeDp stroke width at that size
 */
public class MalletIconDrawable @JvmOverloads constructor(
    context: Context,
    public val icon: MalletIcon,
    @ColorInt color: Int = MalletTheme.of(context).colors.muted,
    sizeDp: Float = 24f,
    strokeDp: Float = MalletIconPainter.STROKE_DP,
) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val sizePx = maxOf(1, (sizeDp * density).roundToInt())
    private val strokePx = strokeDp * density
    private var baseColor = color
    private var tint: ColorStateList? = null
    private var alphaValue = 255

    /** Stroke colour when no tint is set. */
    @get:ColorInt
    @setparam:ColorInt
    public var color: Int
        get() = baseColor
        set(value) {
            if (baseColor != value) {
                baseColor = value
                invalidateSelf()
            }
        }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val side = min(bounds.width(), bounds.height()).toFloat()
        if (side <= 0f) return
        val resolved = tint?.getColorForState(state, baseColor) ?: baseColor
        val drawn = if (alphaValue == 255) {
            resolved
        } else {
            MalletColors.withAlpha(resolved, android.graphics.Color.alpha(resolved) * alphaValue / 255)
        }
        val mirrored = DrawableCompat.getLayoutDirection(this) == View.LAYOUT_DIRECTION_RTL
        MalletIconPainter.draw(
            canvas, icon,
            bounds.left + (bounds.width() - side) / 2f,
            bounds.top + (bounds.height() - side) / 2f,
            side, drawn, strokePx * side / sizePx, mirrored,
        )
    }

    override fun getIntrinsicWidth(): Int = sizePx

    override fun getIntrinsicHeight(): Int = sizePx

    override fun setAlpha(alpha: Int) {
        val bounded = alpha.coerceIn(0, 255)
        if (alphaValue != bounded) {
            alphaValue = bounded
            invalidateSelf()
        }
    }

    override fun getAlpha(): Int = alphaValue

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        invalidateSelf()
    }

    override fun isStateful(): Boolean = tint?.isStateful == true

    override fun onStateChange(state: IntArray): Boolean {
        if (tint?.isStateful == true) {
            invalidateSelf()
            return true
        }
        return false
    }

    override fun onLayoutDirectionChanged(layoutDirection: Int): Boolean {
        if (icon.isDirectional) {
            invalidateSelf()
            return true
        }
        return false
    }

    override fun isAutoMirrored(): Boolean = icon.isDirectional

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Icons are single-colour strokes: use the colour or the tint instead.
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
