package io.github.avalonskyafar.sancai.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.widget.TextView
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconDrawable
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.material.SancaiSurfaceDrawable
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.roundToInt

/**
 * The Sancai capsule button.
 *
 * | Variant | Body | Use |
 * |---|---|---|
 * | [Variant.NEUTRAL] | white porcelain capsule | ordinary actions |
 * | [Variant.PRIMARY] | capsule glazed in the accent colour | the one main action of a screen |
 * | [Variant.DANGER] | capsule glazed in the danger colour | destructive actions |
 * | [Variant.TEXT] | none, coloured label only | dialog actions and inline links |
 *
 * Pressing sinks the capsule by 1.5 dp and tightens its shadow; nothing changes colour.
 * Keyboard focus thickens the rim into ink. A disabled button pales. While [isLoading] the
 * label gives way to a spinner and clicks are ignored.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiButton
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:text="Restore defaults"
 *     app:sancaiButtonVariant="neutral" />
 * ```
 */
@SuppressLint("AppCompatCustomView")
public class SancaiButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : TextView(context, attrs, defStyleAttr) {

    public enum class Variant { NEUTRAL, PRIMARY, DANGER, TEXT }

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val material = SancaiMaterial(context, theme)
    private val density = resources.displayMetrics.density
    private val sinkPx = SancaiMotion.PRESS_SINK_DP * density
    private val bodyBounds = RectF()
    private val spinnerBounds = RectF()
    private val spinnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var body: SancaiSurfaceDrawable? = null
    private var down = false
    private var spinner: ValueAnimator? = null
    private var spinnerAngle = 0f
    private var iconDrawable: SancaiIconDrawable? = null

    /** Visual variant. */
    public var variant: Variant = Variant.NEUTRAL
        set(value) {
            field = value
            applyVariant()
        }

    /** Label colour of the [Variant.TEXT] variant. */
    public var tone: SancaiTextTone = SancaiTextTone.ACCENT
        set(value) {
            field = value
            applyVariant()
        }

    /** Icon drawn before the label, or `null`. */
    public var icon: SancaiIcon? = null
        set(value) {
            field = value
            applyVariant()
        }

    /** Shows a spinner instead of the label and ignores clicks. */
    public var isLoading: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateSpinner()
            invalidate()
        }

    init {
        SancaiTextRole.LABEL.applyTo(this, theme)
        gravity = Gravity.CENTER
        textAlignment = TEXT_ALIGNMENT_CENTER
        isClickable = true
        isFocusable = true
        var initialVariant = Variant.NEUTRAL
        var initialTone = SancaiTextTone.ACCENT
        var initialIcon: SancaiIcon? = null
        var loading = false
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiButton, defStyleAttr, 0)
            try {
                initialVariant = VARIANTS.getOrElse(a.getInt(R.styleable.SancaiButton_sancaiButtonVariant, 0)) { Variant.NEUTRAL }
                initialTone = TONES.getOrElse(a.getInt(R.styleable.SancaiButton_sancaiTone, SancaiTextTone.ACCENT.ordinal)) { SancaiTextTone.ACCENT }
                initialIcon = SancaiIcon.fromAttr(a.getInt(R.styleable.SancaiButton_sancaiIcon, -1))
                loading = a.getBoolean(R.styleable.SancaiButton_sancaiLoading, false)
            } finally {
                a.recycle()
            }
        }
        if (paddingLeft == 0 && paddingTop == 0 && paddingRight == 0 && paddingBottom == 0) {
            val horizontal = (PADDING_HORIZONTAL_DP * density).roundToInt()
            val vertical = (PADDING_VERTICAL_DP * density).roundToInt()
            setPadding(horizontal, vertical, horizontal, vertical)
        }
        tone = initialTone
        icon = initialIcon
        variant = initialVariant
        isLoading = loading
    }

    private fun applyVariant() {
        val colors = theme.colors
        val tint = when (variant) {
            Variant.PRIMARY -> colors.accent
            Variant.DANGER -> colors.danger
            else -> null
        }
        val label = when (variant) {
            Variant.NEUTRAL -> colors.ink
            Variant.PRIMARY, Variant.DANGER -> theme.labelOnTint(tint ?: colors.accent)
            Variant.TEXT -> tone.resolve(colors)
        }
        setTextColor(label)
        body = if (variant == Variant.TEXT) {
            null
        } else {
            SancaiSurfaceDrawable(material, pill = true, tint = tint).also { it.sinkPx = sinkPx }
        }
        background = body
        val current = icon
        iconDrawable = if (current == null) null else SancaiIconDrawable(context, current, label, ICON_DP)
        requestLayout()
        invalidate()
    }

    // ---------------------------------------------------------------------------------------
    // Press
    // ---------------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isEnabled && !isLoading) {
            when (event.actionMasked) {
                // The sink starts on touch-down, without the tap delay of scrolling parents.
                MotionEvent.ACTION_DOWN -> setDown(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setDown(false)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun dispatchSetPressed(pressed: Boolean) {
        super.dispatchSetPressed(pressed)
        // Keyboard and accessibility presses arrive here rather than through touch events.
        if (!pressed) setDown(false) else if (!isInTouchMode) setDown(true)
    }

    private fun setDown(value: Boolean) {
        if (down == value) return
        down = value
        body?.pressed = value
        invalidate()
    }

    override fun performClick(): Boolean {
        if (isLoading) return false
        SancaiHaptics.tick(this)
        return super.performClick()
    }

    // ---------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------

    override fun draw(canvas: Canvas) {
        if (isEnabled) {
            super.draw(canvas)
            return
        }
        val save = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), SancaiControl.DISABLED_ALPHA)
        super.draw(canvas)
        canvas.restoreToCount(save)
    }

    /** Width the icon and its gap add to the label, in pixels. */
    private val iconExtra: Int
        get() = if (iconDrawable == null) 0 else ((ICON_DP + ICON_GAP_DP) * density).roundToInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val extra = iconExtra
        if (extra == 0 || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) return
        var width = measuredWidth + extra
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
            width = minOf(width, MeasureSpec.getSize(widthMeasureSpec))
        }
        setMeasuredDimension(width, measuredHeight)
    }

    /** The icon sits right before the label, and the pair is centred as one group. */
    private fun drawIcon(canvas: Canvas) {
        val drawable = iconDrawable ?: return
        val textLayout = layout ?: return
        var textWidth = 0f
        for (line in 0 until textLayout.lineCount) textWidth = maxOf(textWidth, textLayout.getLineWidth(line))
        val size = (ICON_DP * density).roundToInt()
        val group = textWidth + iconExtra
        val centreX = (paddingLeft + width - paddingRight) / 2f
        val centreY = (paddingTop + height - paddingBottom) / 2f
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val left = if (rtl) centreX + group / 2f - size else centreX - group / 2f
        drawable.setBounds(
            left.roundToInt(), (centreY - size / 2f).roundToInt(),
            left.roundToInt() + size, (centreY - size / 2f).roundToInt() + size,
        )
        androidx.core.graphics.drawable.DrawableCompat.setLayoutDirection(drawable, layoutDirection)
        drawable.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        val sink = if (down) sinkPx else 0f
        val surface = body
        if (surface != null && isFocused && hasWindowFocus() && !isInTouchMode) {
            surface.bodyBounds(android.graphics.Rect(0, 0, width, height), bodyBounds)
            bodyBounds.offset(0f, sink)
            material.drawFocusRim(canvas, bodyBounds, bodyBounds.height() / 2f)
        }
        val save = canvas.save()
        canvas.translate(0f, sink)
        if (isLoading) {
            drawSpinner(canvas)
        } else {
            drawIcon(canvas)
            val shift = iconExtra / 2f
            canvas.translate(if (layoutDirection == LAYOUT_DIRECTION_RTL) -shift else shift, 0f)
            super.onDraw(canvas)
        }
        canvas.restoreToCount(save)
    }

    private fun drawSpinner(canvas: Canvas) {
        val radius = SPINNER_RADIUS_DP * density
        // Centre on the label box, which sits above the shadow gutter of the body.
        val centreX = (paddingLeft + width - paddingRight) / 2f + scrollX
        val centreY = (paddingTop + height - paddingBottom) / 2f + scrollY
        spinnerBounds.set(centreX - radius, centreY - radius, centreX + radius, centreY + radius)
        spinnerPaint.color = currentTextColor
        spinnerPaint.strokeWidth = 2f * density
        canvas.drawArc(spinnerBounds, spinnerAngle, 276f, false, spinnerPaint)
    }

    private fun updateSpinner() {
        spinner?.cancel()
        spinner = null
        if (!isLoading || !isAttachedToWindow) return
        spinner = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = SPINNER_TURN_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                spinnerAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateSpinner()
    }

    override fun onDetachedFromWindow() {
        spinner?.cancel()
        spinner = null
        super.onDetachedFromWindow()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"

    public companion object {
        private const val PADDING_HORIZONTAL_DP = 22f
        private const val PADDING_VERTICAL_DP = 13f
        private const val ICON_DP = 18f
        private const val ICON_GAP_DP = 7f
        private const val SPINNER_RADIUS_DP = 8f
        private const val SPINNER_TURN_MS = 900L
        private val VARIANTS = Variant.values()
        private val TONES = SancaiTextTone.values()
    }
}
