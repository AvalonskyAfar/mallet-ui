package io.github.avalonskyafar.sancai.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewConfiguration
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.internal.useOwnFocusRim
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTheme

/**
 * Base class of the custom-drawn Sancai controls, and a starting point for your own.
 *
 * It resolves the [theme] once, owns a [material] painter, fades the whole control when it is
 * disabled, repaints on focus changes and offers [settle] for the one kind of animation a
 * control ever runs.
 *
 * Conventions every control built on this class follows:
 *
 * - Change listeners fire for user input only (touch, keyboard, accessibility). Setting a value
 *   from code never calls back, so rolling a control back from inside its own listener is safe.
 * - The listener is called first and the haptic tick second, so a control that switches haptics
 *   off stays silent when it does so.
 * - A drag that turns vertical is handed back to the scrolling parent and leaves the value
 *   untouched.
 */
public abstract class SancaiControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** The theme this control was created with. */
    protected val theme: SancaiTheme = SancaiTheme.of(context)

    /** Painter for tracks, thumbs and porcelain in [theme]. */
    protected val material: SancaiMaterial = SancaiMaterial(context, theme)

    /** Distance a finger must travel before a drag is recognised. */
    protected val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    /** Shared paint for labels and glyphs; reset what you change. */
    protected val textPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    init {
        // Clickable and focusable, unless the layout says otherwise.
        val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiControl, defStyleAttr, 0)
        try {
            if (!a.hasValue(R.styleable.SancaiControl_android_focusable)) isFocusable = true
            if (!a.hasValue(R.styleable.SancaiControl_android_clickable)) isClickable = true
        } finally {
            a.recycle()
        }
        useOwnFocusRim()
    }

    /** Whether the layout runs right-to-left. */
    protected val isRtl: Boolean get() = layoutDirection == LAYOUT_DIRECTION_RTL

    /** Whether the keyboard-focus rim should be drawn right now. */
    protected val showsFocus: Boolean get() = isFocused && hasWindowFocus() && !isInTouchMode

    protected fun dp(value: Float): Float = value * resources.displayMetrics.density

    /** Text size in pixels for [value] sp, including the system font scale and the theme scale. */
    protected fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics) * theme.textScale

    /**
     * Animates from [from] to [to] along the Sancai glide and reports every value to
     * [onUpdate]. With reduced motion the end value is applied at once and `null` is returned.
     */
    protected fun settle(
        from: Float,
        to: Float,
        durationMs: Long = SancaiMotion.CONTROL_MS,
        onUpdate: (Float) -> Unit,
    ): ValueAnimator? {
        if (theme.reduceMotion || from == to || !isLaidOut) {
            onUpdate(to)
            invalidate()
            return null
        }
        return ValueAnimator.ofFloat(from, to).apply {
            duration = durationMs
            interpolator = SancaiMotion.glide()
            addUpdateListener {
                onUpdate(it.animatedValue as Float)
                invalidate()
            }
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        if (isEnabled) {
            super.draw(canvas)
            return
        }
        val save = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), DISABLED_ALPHA)
        super.draw(canvas)
        canvas.restoreToCount(save)
    }

    override fun setEnabled(enabled: Boolean) {
        val changed = enabled != isEnabled
        super.setEnabled(enabled)
        if (changed) invalidate()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        invalidate()
    }

    public companion object {
        /** Alpha (0..255) of a disabled control: the glaze pales, the shape stays. */
        public const val DISABLED_ALPHA: Int = 107
    }
}
