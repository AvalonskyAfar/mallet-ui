package io.github.avalonskyafar.sancai.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.interaction.SancaiHorizontalDragOwner
import io.github.avalonskyafar.sancai.internal.HorizontalDragGate
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A slim recessed track with a glazed stretch that runs from the start to a neutral bead.
 *
 * The bead follows the finger anywhere on the track; a tap makes it glide to the touched
 * place. The glaze behind the bead is the value: either one glaze ([glaze], cobalt by default)
 * or a scale set with [setGlazeScale], through which the stretch blends as the value rises,
 * for example terracotta, ochre, cobalt, celadon. The rest of the track stays a neutral groove.
 *
 * With a [stepSize] above zero the bead snaps to the step grid and every crossed step gives a
 * detent haptic; with `0` the slider is continuous.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiSlider
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiValueFrom="0"
 *     app:sancaiValueTo="100"
 *     app:sancaiStepSize="5"
 *     app:sancaiValue="40"
 *     app:sancaiGlaze="cobalt" />
 * ```
 *
 * The view is 44 dp tall by default: a 14 dp track, centred, under a 28 dp bead. The track is
 * inset by half a bead on both sides, so the bead and its shadow never leave the view.
 */
public class SancaiSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiControl(context, attrs, defStyleAttr), SancaiHorizontalDragOwner {

    /** Called when the user changes the value by touch, keyboard or an accessibility action. */
    public fun interface OnValueChangeListener {
        /**
         * @param value the new value, already clamped and snapped.
         * @param fromUser always `true`: changes made from code through [SancaiSlider.value],
         * [SancaiSlider.setValue] or the range setters do not call the listener at all.
         */
        public fun onValueChange(slider: SancaiSlider, value: Float, fromUser: Boolean)
    }

    /** Called when a touch that moved the bead ends: after a drag or a tap, once per gesture. */
    public fun interface OnStopTrackingTouchListener {
        /** The finger has lifted (or the drag was taken away); [SancaiSlider.value] is final. */
        public fun onStopTrackingTouch(slider: SancaiSlider)
    }

    private var from = 0f
    private var to = 1f
    private var step = 0f
    private var current = 0f

    /** Where the bead is drawn, 0 at the start and 1 at the end; glides behind [current]. */
    private var position = 0f
    private var singleGlaze: SancaiGlaze = theme.glazes.cobalt
    private var scale: List<SancaiGlaze>? = null
    private var down = false
    private var moved = false
    private val gate = HorizontalDragGate(touchSlop)
    private var animator: ValueAnimator? = null
    private val trackBounds = RectF()
    private val beadBounds = RectF()
    private val trackClip = Path()

    /** Listener for value changes made by the user. */
    public var onValueChangeListener: OnValueChangeListener? = null

    /** Listener for the end of a touch gesture, the moment to commit an expensive change. */
    public var onStopTrackingTouchListener: OnStopTrackingTouchListener? = null

    /**
     * Turns a value into the text accessibility services read, for example `"40 %"`. With
     * `null` the value is read as a plain number with as many decimals as [stepSize] needs.
     */
    public var valueFormatter: ((Float) -> CharSequence)? = null
        set(value) {
            field = value
            announceState()
        }

    /** The current value, always inside [valueFrom]..[valueTo] and on the step grid. */
    public var value: Float
        get() = current
        set(value) = setValue(value, animate = false)

    /** Lower bound, at the start of the track. Changing it re-clamps [value]. */
    public var valueFrom: Float
        get() = from
        set(value) {
            from = value
            rangeChanged()
        }

    /** Upper bound, at the end of the track. Changing it re-clamps [value]. */
    public var valueTo: Float
        get() = to
        set(value) {
            to = value
            rangeChanged()
        }

    /** Distance between two stops, counted from [valueFrom]; `0` makes the slider continuous. */
    public var stepSize: Float
        get() = step
        set(value) {
            step = max(0f, value)
            rangeChanged()
        }

    /** The glaze of the stretch behind the bead. Setting it ends a scale set by [setGlazeScale]. */
    public var glaze: SancaiGlaze
        get() = singleGlaze
        set(value) {
            singleGlaze = value
            scale = null
            invalidate()
        }

    init {
        // The slider has no click: Space and Enter are not consumed, taps move the bead instead.
        isClickable = false
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiSlider, defStyleAttr, 0)
            try {
                from = a.getFloat(R.styleable.SancaiSlider_sancaiValueFrom, from)
                to = a.getFloat(R.styleable.SancaiSlider_sancaiValueTo, to)
                step = max(0f, a.getFloat(R.styleable.SancaiSlider_sancaiStepSize, step))
                current = a.getFloat(R.styleable.SancaiSlider_sancaiValue, from)
                singleGlaze = theme.glazes.fromAttr(a.getInt(R.styleable.SancaiSlider_sancaiGlaze, 2))
                if (a.getInt(R.styleable.SancaiSlider_sancaiSliderGlazeMode, MODE_SINGLE) == MODE_SCALE) {
                    scale = theme.glazes.scale(SCALE_GLAZES)
                }
                isEnabled = a.getBoolean(R.styleable.SancaiSlider_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        rangeChanged()
    }

    /**
     * Sets the value from code, optionally gliding the bead there. The value is clamped to the
     * range and snapped to the step grid. The listener is not called, which makes this safe to
     * use for rolling back a change the listener refused.
     */
    public fun setValue(value: Float, animate: Boolean) {
        current = snap(value)
        if (animate) glideTo(fractionOf(current)) else jumpTo(fractionOf(current))
        announceState()
    }

    /**
     * Glazes the stretch with a scale instead of one glaze: at [valueFrom] it shows the first
     * glaze of [glazes], at [valueTo] the last, and in between it blends through the list.
     * `null` or an empty list returns to the single [glaze].
     */
    public fun setGlazeScale(glazes: List<SancaiGlaze>?) {
        scale = glazes?.takeIf { it.isNotEmpty() }?.toList()
        invalidate()
    }

    private fun rangeChanged() {
        animator?.cancel()
        current = snap(current)
        position = fractionOf(current)
        announceState()
        invalidate()
    }

    /** Clamps [raw] to the range and, with a step, moves it to the nearest stop. */
    private fun snap(raw: Float): Float {
        if (!(to > from) || raw.isNaN()) return from
        val clamped = raw.coerceIn(from, to)
        if (step <= 0f) return clamped
        val stops = floor((to - from) / step + STEP_EPSILON)
        val index = Math.round((clamped - from) / step).toFloat().coerceIn(0f, stops)
        return (from.toDouble() + index.toDouble() * step.toDouble()).toFloat().coerceIn(from, to)
    }

    private fun fractionOf(value: Float): Float =
        if (to <= from) 0f else ((value - from) / (to - from)).coerceIn(0f, 1f)

    // ---------------------------------------------------------------------------------------
    // Measuring and drawing
    // ---------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(WIDTH_DP).roundToInt() + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(dp(HEIGHT_DP).roundToInt() + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    /** Left end of the bead centre's travel. The travel ends one track radius inside the track. */
    private val travelLeft: Float get() = paddingLeft + dp(BEAD_DP / 2f + TRACK_DP / 2f)
    private val travelRight: Float get() = width - paddingRight - dp(BEAD_DP / 2f + TRACK_DP / 2f)

    /** Converts between a fraction of the value and a fraction of the way from left to right. */
    private fun toVisual(fraction: Float): Float = if (isRtl) 1f - fraction else fraction

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centreY = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val half = dp(TRACK_DP) / 2f
        trackBounds.set(
            paddingLeft + dp(BEAD_DP / 2f),
            centreY - half,
            width - paddingRight - dp(BEAD_DP / 2f),
            centreY + half,
        )
        if (trackBounds.width() <= trackBounds.height()) return
        val centreX = travelLeft + (travelRight - travelLeft) * toVisual(position)

        material.drawTrack(canvas, trackBounds, grooveColor(edge = false), grooveColor(edge = true))
        // The glazed stretch is the same capsule poured again, cut off under the bead's centre.
        val save = canvas.save()
        if (isRtl) {
            canvas.clipRect(centreX, trackBounds.top, trackBounds.right, trackBounds.bottom)
        } else {
            canvas.clipRect(trackBounds.left, trackBounds.top, centreX, trackBounds.bottom)
        }
        material.drawTrack(
            canvas, trackBounds, glazeAt(position, edge = false), glazeAt(position, edge = true), lifted = false,
        )
        canvas.restoreToCount(save)

        val radius = dp(BEAD_DP) / 2f
        beadBounds.set(centreX - radius, centreY - radius, centreX + radius, centreY + radius)
        material.drawThumb(canvas, beadBounds, down)
        if (showsFocus) material.drawFocusRim(canvas, beadBounds, radius)
    }

    /** The unglazed recess: mist in light themes, the plain groove in dark ones. */
    private fun grooveColor(edge: Boolean): Int = when {
        !theme.isDark -> if (edge) theme.bedEdgeColor(theme.glazes.mist) else theme.bedColor(theme.glazes.mist)
        edge -> SancaiColors.blend(theme.colors.ink, theme.colors.canvas, GROOVE_EDGE_CANVAS_SHARE)
        else -> theme.groove
    }

    /** Bed or rim colour of the stretch when the bead stands at [fraction] of the range. */
    private fun glazeAt(fraction: Float, edge: Boolean): Int {
        val glazes = scale
        if (glazes == null || glazes.size == 1) {
            val only = glazes?.first() ?: singleGlaze
            return if (edge) theme.bedEdgeColor(only) else theme.bedColor(only)
        }
        val at = fraction.coerceIn(0f, 1f) * (glazes.size - 1)
        val low = floor(at).toInt().coerceIn(0, glazes.size - 1)
        val high = (low + 1).coerceAtMost(glazes.size - 1)
        val first = if (edge) theme.bedEdgeColor(glazes[low]) else theme.bedColor(glazes[low])
        val second = if (edge) theme.bedEdgeColor(glazes[high]) else theme.bedColor(glazes[high])
        return SancaiColors.blend(first, second, at - low)
    }

    // ---------------------------------------------------------------------------------------
    // Touch and keys
    // ---------------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gate.down(event)
                down = true
                moved = false
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> when (gate.move(this, event)) {
                HorizontalDragGate.State.DRAGGING -> {
                    moved = true
                    change(valueAt(event.x), glide = false, tap = false)
                }
                HorizontalDragGate.State.ABORTED -> if (down) {
                    down = false
                    invalidate()
                }
                HorizontalDragGate.State.UNDECIDED -> Unit
            }
            MotionEvent.ACTION_UP -> {
                gate.finish(this)
                down = false
                invalidate()
                if (!gate.isAborted) {
                    if (!gate.isDragging) change(valueAt(event.x), glide = true, tap = true)
                    onStopTrackingTouchListener?.onStopTrackingTouch(this)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                gate.finish(this)
                down = false
                invalidate()
                // A drag that was taken away has still moved the value: the gesture ends here.
                if (moved) onStopTrackingTouchListener?.onStopTrackingTouch(this)
            }
        }
        return true
    }

    /** The value under the horizontal touch position [x], snapped. */
    private fun valueAt(x: Float): Float {
        val span = max(1f, travelRight - travelLeft)
        val fraction = toVisual(((x - travelLeft) / span).coerceIn(0f, 1f))
        return snap(from + fraction * (to - from))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            val forward = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != isRtl
            // At either end the key falls through, so focus can leave the slider.
            if (stepBy(if (forward) 1 else -1)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** One keyboard or accessibility step: a stop, or 5 % of the range when continuous. */
    private fun stepBy(direction: Int): Boolean {
        val amount = if (step > 0f) step else (to - from) * CONTINUOUS_KEY_SHARE
        return change(snap(current + direction * amount), glide = true, tap = true)
    }

    /**
     * A user-driven change: move the bead, then tell the listener, then give the haptic. A
     * drag gives a detent for every stop it crosses and none when continuous; a tap or key
     * gives one tick.
     */
    private fun change(next: Float, glide: Boolean, tap: Boolean): Boolean {
        if (next == current) return false
        val crossed = if (step > 0f) abs(Math.round((next - current) / step)) else 0
        current = next
        if (glide) glideTo(fractionOf(next)) else jumpTo(fractionOf(next))
        announceState()
        onValueChangeListener?.onValueChange(this, next, true)
        if (tap) SancaiHaptics.tick(this) else repeat(crossed.coerceAtMost(MAX_DETENTS)) { SancaiHaptics.detent(this) }
        return true
    }

    private fun glideTo(target: Float) {
        animator?.cancel()
        val start = position
        animator = settle(0f, 1f) { progress -> position = start + (target - start) * progress }
    }

    private fun jumpTo(target: Float) {
        animator?.cancel()
        animator = null
        position = target
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        position = fractionOf(current)
        super.onDetachedFromWindow()
    }

    override fun setEnabled(enabled: Boolean) {
        // Disabling under the finger ends the gesture; the rest of its events are ignored.
        if (!enabled && down) {
            gate.finish(this)
            down = false
            if (moved) onStopTrackingTouchListener?.onStopTrackingTouch(this)
        }
        super.setEnabled(enabled)
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        invalidate()
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
        setValue(state.getFloat(STATE_VALUE, current), animate = false)
    }

    private fun announceState() {
        ViewCompat.setStateDescription(this, formatValue(current))
    }

    private fun formatValue(value: Float): CharSequence {
        valueFormatter?.let { return it(value) }
        val range = to - from
        val decimals = when {
            step > 0f -> (0..MAX_DECIMALS).firstOrNull { digits ->
                val scaled = step * POWERS_OF_TEN[digits]
                abs(scaled - Math.round(scaled)) < STEP_EPSILON
            } ?: MAX_DECIMALS
            range >= 100f -> 0
            range >= 10f -> 1
            else -> 2
        }
        return NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(value.toDouble())
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.SeekBar"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        val node = AccessibilityNodeInfoCompat.wrap(info)
        node.setRangeInfo(
            AccessibilityNodeInfoCompat.RangeInfoCompat.obtain(
                AccessibilityNodeInfoCompat.RangeInfoCompat.RANGE_TYPE_FLOAT, from, to, current,
            ),
        )
        // The slider has no click of its own; a double tap would do nothing.
        node.removeAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
        node.isClickable = false
        if (!isEnabled || to <= from) return
        node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SET_PROGRESS)
        if (current < to) node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
        if (current > from) node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (isEnabled) {
            when (action) {
                AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> return stepBy(1)
                AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> return stepBy(-1)
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SET_PROGRESS.id -> {
                    val key = AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE
                    if (arguments == null || !arguments.containsKey(key)) return false
                    return change(snap(arguments.getFloat(key)), glide = true, tap = true)
                }
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private companion object {
        const val HEIGHT_DP = 44f
        const val WIDTH_DP = 160f
        const val TRACK_DP = 14f
        const val BEAD_DP = 28f

        /** Canvas share in the rim of the unglazed dark groove, as in the theme's neutral rim. */
        const val GROOVE_EDGE_CANVAS_SHARE = 0.93f
        const val CONTINUOUS_KEY_SHARE = 0.05f
        const val SCALE_GLAZES = 4
        const val MODE_SINGLE = 0
        const val MODE_SCALE = 1
        const val MAX_DETENTS = 3
        const val MAX_DECIMALS = 3
        const val STEP_EPSILON = 1e-4f
        val POWERS_OF_TEN = floatArrayOf(1f, 10f, 100f, 1000f)
        const val STATE_SUPER = "sancai:super"
        const val STATE_VALUE = "sancai:slider:value"
    }
}
