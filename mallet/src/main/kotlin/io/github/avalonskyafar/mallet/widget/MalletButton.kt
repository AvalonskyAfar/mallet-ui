package io.github.avalonskyafar.mallet.widget

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
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconDrawable
import io.github.avalonskyafar.mallet.interaction.MalletHaptics
import io.github.avalonskyafar.mallet.internal.useOwnFocusRim
import io.github.avalonskyafar.mallet.material.MalletGutters
import io.github.avalonskyafar.mallet.material.MalletMaterial
import io.github.avalonskyafar.mallet.material.MalletSurfaceDrawable
import io.github.avalonskyafar.mallet.theme.MalletMotion
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import kotlin.math.roundToInt

/**
 * The Mallet capsule button.
 *
 * | Variant | Body | Use |
 * |---|---|---|
 * | [Variant.NEUTRAL] | neutral capsule | ordinary actions |
 * | [Variant.PRIMARY] | capsule in the accent colour | the one main action of a screen |
 * | [Variant.DANGER] | capsule in the danger colour | destructive actions |
 * | [Variant.TEXT] | none, coloured label only | dialog actions and inline links |
 *
 * Pressing sinks the capsule by 1.5 dp and tightens its shadow; nothing changes colour.
 * Keyboard focus thickens the rim into ink. A disabled button pales. While [isLoading] the
 * label gives way to a spinner and clicks are ignored.
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.widget.MalletButton
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:text="Restore defaults"
 *     app:malletButtonVariant="neutral" />
 * ```
 */
@SuppressLint("AppCompatCustomView")
public class MalletButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : TextView(context, attrs, defStyleAttr) {

    public enum class Variant { NEUTRAL, PRIMARY, DANGER, TEXT }

    private val theme: MalletTheme = MalletTheme.of(context)
    private val material = MalletMaterial(context, theme)
    private val density = resources.displayMetrics.density
    private val sinkPx = MalletMotion.PRESS_SINK_DP * density
    private val bodyBounds = RectF()
    private val viewBounds = android.graphics.Rect()
    private val spinnerBounds = RectF()
    private val spinnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var body: MalletSurfaceDrawable? = null
    private var down = false
    private var spinner: ValueAnimator? = null
    private var spinnerAngle = 0f
    private var iconDrawable: MalletIconDrawable? = null

    /** Visual variant. */
    public var variant: Variant = Variant.NEUTRAL
        set(value) {
            field = value
            applyVariant()
        }

    /** Label colour of the [Variant.TEXT] variant. */
    public var tone: MalletTextTone = MalletTextTone.ACCENT
        set(value) {
            field = value
            applyVariant()
        }

    /** Icon drawn before the label, or `null`. */
    public var icon: MalletIcon? = null
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
        MalletTextRole.LABEL.applyTo(this, theme)
        gravity = Gravity.CENTER
        textAlignment = TEXT_ALIGNMENT_CENTER
        // Clickable and focusable, unless the layout says otherwise.
        val defaults = context.obtainStyledAttributes(attrs, R.styleable.MalletButton, defStyleAttr, 0)
        try {
            if (!defaults.hasValue(R.styleable.MalletButton_android_focusable)) isFocusable = true
            if (!defaults.hasValue(R.styleable.MalletButton_android_clickable)) isClickable = true
        } finally {
            defaults.recycle()
        }
        useOwnFocusRim()
        var initialVariant = Variant.NEUTRAL
        var initialTone = MalletTextTone.ACCENT
        var initialIcon: MalletIcon? = null
        var loading = false
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletButton, defStyleAttr, 0)
            try {
                initialVariant = VARIANTS.getOrElse(a.getInt(R.styleable.MalletButton_malletButtonVariant, 0)) { Variant.NEUTRAL }
                initialTone = TONES.getOrElse(a.getInt(R.styleable.MalletButton_malletTone, MalletTextTone.ACCENT.ordinal)) { MalletTextTone.ACCENT }
                initialIcon = MalletIcon.fromAttr(a.getInt(R.styleable.MalletButton_malletIcon, -1))
                loading = a.getBoolean(R.styleable.MalletButton_malletLoading, false)
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
            MalletSurfaceDrawable(material, pill = true, tint = tint).also { it.sinkPx = sinkPx }
        }
        background = body
        val current = icon
        iconDrawable = if (current == null) null else MalletIconDrawable(context, current, label, ICON_DP)
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
        MalletHaptics.tick(this)
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
        val save = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), MalletControl.DISABLED_ALPHA)
        super.draw(canvas)
        canvas.restoreToCount(save)
    }

    /** Width the icon adds to the label, in pixels: the icon, and the gap when there is a label. */
    private val iconExtra: Int
        get() = when {
            iconDrawable == null -> 0
            text.isNullOrEmpty() -> (ICON_DP * density).roundToInt()
            else -> ((ICON_DP + ICON_GAP_DP) * density).roundToInt()
        }

    // The icon's room is taken out of the label's box at the start side, so the label is
    // measured, wrapped and centred in what is left. (Also called by the TextView constructor,
    // before this class is initialised: there is no icon yet then.)
    override fun getCompoundPaddingLeft(): Int =
        super.getCompoundPaddingLeft() + if (layoutDirection == LAYOUT_DIRECTION_RTL) 0 else iconExtra

    override fun getCompoundPaddingRight(): Int =
        super.getCompoundPaddingRight() + if (layoutDirection == LAYOUT_DIRECTION_RTL) iconExtra else 0

    /** How far label, icon and spinner are lifted so they centre on the capsule, not on the view. */
    private val bodyLift: Float
        get() = if (body == null) 0f else (MalletGutters.SURFACE_BOTTOM_DP - MalletGutters.SURFACE_TOP_DP) / 2f * density

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        // The gap beside the icon comes and goes with the label, and with it the label's box.
        if (iconDrawable != null) requestLayout()
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
            viewBounds.set(0, 0, width, height)
            surface.bodyBounds(viewBounds, bodyBounds)
            bodyBounds.offset(0f, sink)
            material.drawFocusRim(canvas, bodyBounds, bodyBounds.height() / 2f)
        }
        val save = canvas.save()
        // The capsule leaves more room below (shadow) than above: lift the content to its centre.
        canvas.translate(0f, sink - bodyLift)
        if (isLoading) {
            drawSpinner(canvas)
        } else {
            drawIcon(canvas)
            super.onDraw(canvas)
        }
        canvas.restoreToCount(save)
    }

    private fun drawSpinner(canvas: Canvas) {
        val radius = SPINNER_RADIUS_DP * density
        // Centre on the label box; onDraw lifts it to the centre of the capsule.
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
        private val TONES = MalletTextTone.values()
    }
}
