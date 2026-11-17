package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconPainter
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * A precise number control: a recessed glazed track with a minus zone, a plus zone and a
 * neutral thumb in the centre that carries the value and never moves.
 *
 * Tapping minus or plus changes the value by one [stepSize]; holding either repeats, slowly at
 * first and faster after ten steps, until the finger lifts or the range ends. Tapping the value
 * asks the app to open its own editor through [onEditRequestListener]. The value always sits on
 * the step grid that starts at [valueFrom] and never leaves the range; at a bound the mark that
 * can no longer do anything pales.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiStepper
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiValueFrom="0"
 *     app:sancaiValueTo="500"
 *     app:sancaiStepSize="10"
 *     app:sancaiValue="120"
 *     app:sancaiStepperUnit="ms" />
 * ```
 *
 * The view is 61 dp tall by default: a 53 dp track plus the gutter its shadow needs. Minus is
 * always on the left and plus on the right, also in right-to-left layouts, like a number line.
 */
public class SancaiStepper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiControl(context, attrs, defStyleAttr) {

    /** Called when the user changes the value by touch, keyboard or an accessibility action. */
    public fun interface OnValueChangeListener {
        public fun onValueChange(stepper: SancaiStepper, value: Float)
    }

    /** Called when the user taps the value (or presses Enter) to type a number directly. */
    public fun interface OnEditRequestListener {
        public fun onEditRequest(stepper: SancaiStepper)
    }

    /** Turns a value into the text written on the thumb. */
    public fun interface Formatter {
        public fun format(value: Float): CharSequence
    }

    private var from = 0f
    private var to = 100f
    private var step = 1f
    private var current = 0f
    private var pressedZone = ZONE_NONE
    private var repeats = 0
    private val trackBounds = RectF()
    private val thumbBounds = RectF()
    private val trackClip = Path()
    private val repeater = Runnable { repeat() }

    /** Listener for user-driven value changes. Not called when [value] is set from code. */
    public var onValueChangeListener: OnValueChangeListener? = null

    /** Listener for a tap on the value. Without one the centre zone only shows its press. */
    public var onEditRequestListener: OnEditRequestListener? = null

    /**
     * The current value. Setting it snaps to the step grid, clamps to the range and does not
     * call the listener.
     */
    public var value: Float
        get() = current
        set(value) {
            val next = snap(value)
            if (next == current) return
            current = next
            valueChanged()
        }

    /** Lower bound of the range and origin of the step grid. */
    public var valueFrom: Float
        get() = from
        set(value) = setRange(value, max(value, to))

    /** Upper bound of the range. A value is never above it, even when it is off the grid. */
    public var valueTo: Float
        get() = to
        set(value) = setRange(minOf(from, value), value)

    /** Distance between two neighbouring values; must be greater than zero. */
    public var stepSize: Float
        get() = step
        set(value) {
            require(value > 0f && !value.isNaN() && !value.isInfinite()) { "stepSize must be > 0, was $value" }
            step = value
            current = snap(current)
            valueChanged()
        }

    /** Number of digits written after the decimal separator (0 to 6). Ignored by a [formatter]. */
    public var decimals: Int = 0
        set(value) {
            field = value.coerceIn(0, MAX_DECIMALS)
            valueChanged()
        }

    /** Suffix written directly after the number, for example `" ms"` or `"%"`. Ignored by a [formatter]. */
    public var unit: CharSequence? = null
        set(value) {
            field = value
            valueChanged()
        }

    /** Replaces the built-in number formatting ([decimals] and [unit]) when it is not `null`. */
    public var formatter: Formatter? = null
        set(value) {
            field = value
            valueChanged()
        }

    /** The glaze poured into the track. Cobalt by default. */
    public var glaze: SancaiGlaze = theme.glazes.cobalt
        set(value) {
            field = value
            invalidate()
        }

    /** The value as it is written on the thumb and announced by accessibility services. */
    public val formattedValue: CharSequence
        get() = formatter?.format(current) ?: (number() + (unit ?: ""))

    /** The number with [decimals] digits; a value that rounds to zero is written without a sign. */
    private fun number(): String {
        val text = String.format(Locale.getDefault(), "%.${decimals}f", current)
        return if (current < 0f && text.none { it in '1'..'9' }) text.removePrefix("-") else text
    }

    init {
        var initial = 0f
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiStepper, defStyleAttr, 0)
            try {
                glaze = theme.glazes.fromAttr(a.getInt(R.styleable.SancaiStepper_sancaiGlaze, 2))
                from = a.getFloat(R.styleable.SancaiStepper_sancaiValueFrom, from)
                to = max(from, a.getFloat(R.styleable.SancaiStepper_sancaiValueTo, max(from, to)))
                step = a.getFloat(R.styleable.SancaiStepper_sancaiStepSize, step).takeIf { it > 0f } ?: 1f
                initial = a.getFloat(R.styleable.SancaiStepper_sancaiValue, from)
                decimals = a.getInt(R.styleable.SancaiStepper_sancaiStepperDecimals, 0)
                unit = a.getText(R.styleable.SancaiStepper_sancaiStepperUnit)
                isEnabled = a.getBoolean(R.styleable.SancaiStepper_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        current = snap(initial)
        valueChanged()
    }

    /** Sets both bounds at once; [valueTo] must not be below [valueFrom]. The value is re-clamped. */
    public fun setRange(valueFrom: Float, valueTo: Float) {
        require(valueTo >= valueFrom) { "valueTo ($valueTo) must not be below valueFrom ($valueFrom)" }
        from = valueFrom
        to = valueTo
        current = snap(current)
        valueChanged()
    }

    /** Index of the highest grid position that still lies inside the range. */
    private val lastIndex: Long
        get() = floor((to.toDouble() - from) / step + GRID_EPSILON).toLong().coerceAtLeast(0L)

    private fun indexOf(value: Float): Long =
        round((value.toDouble() - from) / step).toLong().coerceIn(0L, lastIndex)

    private fun valueAt(index: Long): Float = (from + index * step.toDouble()).toFloat()

    private fun snap(value: Float): Float = if (value.isNaN()) current else valueAt(indexOf(value))

    private fun canStep(direction: Int): Boolean {
        val next = indexOf(current) + direction
        return next in 0L..lastIndex
    }

    /** Repaints and re-announces after the value or its formatting changed. */
    private fun valueChanged() {
        ViewCompat.setStateDescription(this, formattedValue)
        invalidate()
    }

    // ---------------------------------------------------------------------------------------
    // Measuring and drawing
    // ---------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(MIN_WIDTH_DP).roundToInt() + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(dp(HEIGHT_DP).roundToInt() + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    private fun updateTrackBounds() {
        trackBounds.set(
            paddingLeft + dp(GUTTER_SIDE_DP),
            paddingTop + dp(GUTTER_TOP_DP),
            width - paddingRight - dp(GUTTER_SIDE_DP),
            height - paddingBottom - dp(GUTTER_BOTTOM_DP),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateTrackBounds()
        val track = trackBounds
        if (track.width() <= 0f || track.height() <= 0f) return
        val pressed = pressedZone != ZONE_NONE
        material.drawTrack(canvas, track, theme.bedColor(glaze), theme.bedEdgeColor(glaze), pressed)

        val thumbWidth = track.width() * THUMB_SHARE
        val inset = dp(THUMB_INSET_DP)
        thumbBounds.set(
            track.centerX() - thumbWidth / 2f,
            track.top + inset,
            track.centerX() + thumbWidth / 2f,
            track.bottom - inset,
        )
        // The thumb's lift stays inside the track, so its shadow never spills below the rim.
        trackClip.rewind()
        trackClip.addRoundRect(track, track.height() / 2f, track.height() / 2f, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(trackClip)
        material.drawThumb(canvas, thumbBounds, pressedZone == ZONE_VALUE)
        canvas.restoreToCount(save)
        if (showsFocus) material.drawFocusRim(canvas, track, track.height() / 2f)

        val sink = if (pressed) dp(PRESS_SINK_DP) else 0f
        val markColor = theme.labelOnBed(glaze)
        val paleColor = SancaiColors.withAlpha(markColor, BOUND_ALPHA)
        val size = dp(MARK_DP)
        val sideCentre = track.width() * (SIDE_WEIGHT / 2f) / TOTAL_WEIGHT
        val top = track.centerY() - size / 2f + sink
        val stroke = dp(MARK_STROKE_DP)
        SancaiIconPainter.draw(
            canvas, SancaiIcon.MINUS, track.left + sideCentre - size / 2f, top, size,
            if (canStep(-1)) markColor else paleColor, stroke,
        )
        SancaiIconPainter.draw(
            canvas, SancaiIcon.PLUS, track.right - sideCentre - size / 2f, top, size,
            if (canStep(1)) markColor else paleColor, stroke,
        )

        val text = formattedValue.toString()
        textPaint.typeface = SancaiWeight.MEDIUM.typeface()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.style = Paint.Style.FILL
        textPaint.color = theme.colors.ink
        textPaint.textSize = sp(VALUE_SP)
        // A long value shrinks to stay on the thumb instead of spilling onto the bed.
        val room = thumbWidth - dp(VALUE_MARGIN_DP) * 2f
        val measured = textPaint.measureText(text)
        if (measured > room && room > 0f) textPaint.textSize *= room / measured
        val metrics = textPaint.fontMetrics
        canvas.drawText(text, track.centerX(), track.centerY() - (metrics.ascent + metrics.descent) / 2f + sink, textPaint)
    }

    // ---------------------------------------------------------------------------------------
    // Touch
    // ---------------------------------------------------------------------------------------

    /** The zone under a finger: minus, value or plus by weight, or none once it leaves the view. */
    private fun zoneAt(x: Float, y: Float): Int {
        val slop = touchSlop.toFloat()
        if (x < -slop || x > width + slop || y < -slop || y > height + slop) return ZONE_NONE
        val left = paddingLeft.toFloat()
        val span = max(1f, (width - paddingLeft - paddingRight).toFloat())
        val first = left + span * SIDE_WEIGHT / TOTAL_WEIGHT
        val second = left + span * (SIDE_WEIGHT + VALUE_WEIGHT) / TOTAL_WEIGHT
        return if (x < first) ZONE_MINUS else if (x < second) ZONE_VALUE else ZONE_PLUS
    }

    private fun directionOf(zone: Int): Int = when (zone) {
        ZONE_MINUS -> -1
        ZONE_PLUS -> 1
        else -> 0
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> press(zoneAt(event.x, event.y))
            MotionEvent.ACTION_MOVE -> {
                val zone = zoneAt(event.x, event.y)
                if (zone != pressedZone) press(zone)
            }
            MotionEvent.ACTION_UP -> {
                val zone = pressedZone
                val held = repeats > 0
                press(ZONE_NONE)
                // A hold has already stepped; only a plain tap acts on release.
                if (!held) {
                    when (zone) {
                        ZONE_MINUS, ZONE_PLUS -> if (stepBy(directionOf(zone))) SancaiHaptics.tick(this)
                        ZONE_VALUE -> requestEdit()
                    }
                }
                if (zone != ZONE_NONE) performClick()
            }
            MotionEvent.ACTION_CANCEL -> press(ZONE_NONE)
        }
        return true
    }

    /** Moves the press to [zone] and arms the auto-repeat when that zone can still step. */
    private fun press(zone: Int) {
        removeCallbacks(repeater)
        if (repeats > 0) parent?.requestDisallowInterceptTouchEvent(false)
        repeats = 0
        pressedZone = zone
        if (canStep(directionOf(zone)) && directionOf(zone) != 0) postDelayed(repeater, REPEAT_START_MS)
        invalidate()
    }

    private fun repeat() {
        val direction = directionOf(pressedZone)
        if (direction == 0 || !isEnabled || !stepBy(direction)) return
        // The listener may have disabled or removed the control; then the hold is over.
        if (directionOf(pressedZone) != direction) return
        // From the first repeat on the gesture is a hold: a scrolling parent may not steal it.
        if (repeats == 0) parent?.requestDisallowInterceptTouchEvent(true)
        repeats++
        SancaiHaptics.detent(this)
        if (canStep(direction)) postDelayed(repeater, if (repeats >= REPEAT_FAST_AFTER) REPEAT_FAST_MS else REPEAT_MS)
    }

    /** A user-driven step. Returns `false`, and tells nobody, when the bound is in the way. */
    private fun stepBy(direction: Int): Boolean {
        if (direction == 0 || !canStep(direction)) return false
        return commit(valueAt(indexOf(current) + direction))
    }

    /** A user-driven change to [target]: store, announce, then tell the listener. */
    private fun commit(target: Float): Boolean {
        val next = snap(target)
        if (next == current) return false
        current = next
        valueChanged()
        onValueChangeListener?.onValueChange(this, next)
        return true
    }

    private fun requestEdit(): Boolean {
        val listener = onEditRequestListener ?: return false
        listener.onEditRequest(this)
        SancaiHaptics.tick(this)
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled) {
            // A key that cannot do anything falls through, so focus can leave at a bound.
            val handled = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> stepBy(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS,
                KeyEvent.KEYCODE_NUMPAD_ADD -> stepBy(1)
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER ->
                    return requestEdit() || super.onKeyDown(keyCode, event)
                else -> false
            }
            if (handled) {
                SancaiHaptics.tick(this)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) press(ZONE_NONE)
        super.setEnabled(enabled)
    }

    override fun onDetachedFromWindow() {
        press(ZONE_NONE)
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        // A hold must not keep counting behind a dialog that the listener opened.
        if (!hasWindowFocus) press(ZONE_NONE)
        super.onWindowFocusChanged(hasWindowFocus)
    }

    // ---------------------------------------------------------------------------------------
    // State and accessibility
    // ---------------------------------------------------------------------------------------

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        putFloat(STATE_VALUE, current)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        value = state.getFloat(STATE_VALUE, current)
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.SeekBar"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, from, to, current,
        )
        if (!isEnabled) return
        if (canStep(-1)) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (canStep(1)) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        }
        if (onEditRequestListener != null) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    R.id.sancai_stepper_action_edit,
                    context.getString(R.string.sancai_stepper_action_edit),
                ),
            )
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (isEnabled) {
            when (action) {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> return stepBy(-1)
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> return stepBy(1)
                android.R.id.accessibilityActionSetProgress -> {
                    val target = arguments?.takeIf { it.containsKey(ARGUMENT_PROGRESS) } ?: return false
                    commit(target.getFloat(ARGUMENT_PROGRESS))
                    return true
                }
                R.id.sancai_stepper_action_edit -> return requestEdit()
                // Activating the control as a whole means "edit": the steps have their own actions.
                AccessibilityNodeInfo.ACTION_CLICK -> if (requestEdit()) return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private companion object {
        const val HEIGHT_DP = 61f
        const val MIN_WIDTH_DP = 168f
        const val GUTTER_SIDE_DP = 1.5f
        const val GUTTER_TOP_DP = 1.5f
        const val GUTTER_BOTTOM_DP = 6.5f
        const val SIDE_WEIGHT = 1f
        const val VALUE_WEIGHT = 1.35f
        const val TOTAL_WEIGHT = SIDE_WEIGHT * 2f + VALUE_WEIGHT
        const val THUMB_SHARE = 0.42f
        const val THUMB_INSET_DP = 4f
        const val PRESS_SINK_DP = 1f
        const val MARK_DP = 16f
        const val MARK_STROKE_DP = 2f
        const val VALUE_SP = 12.5f
        const val VALUE_MARGIN_DP = 8f

        /** Alpha (0..255) of a mark whose bound is reached: 40 %. */
        const val BOUND_ALPHA = 102
        const val MAX_DECIMALS = 6

        /** Tolerance that keeps a bound that is on the grid reachable despite float rounding. */
        const val GRID_EPSILON = 1e-4

        const val REPEAT_START_MS = 400L
        const val REPEAT_MS = 80L
        const val REPEAT_FAST_MS = 40L
        const val REPEAT_FAST_AFTER = 10

        const val ZONE_NONE = -1
        const val ZONE_MINUS = 0
        const val ZONE_VALUE = 1
        const val ZONE_PLUS = 2

        const val STATE_SUPER = "sancai:super"
        const val STATE_VALUE = "sancai:stepper:value"
        const val ARGUMENT_PROGRESS = "android.view.accessibility.action.ARGUMENT_PROGRESS_VALUE"
    }
}
