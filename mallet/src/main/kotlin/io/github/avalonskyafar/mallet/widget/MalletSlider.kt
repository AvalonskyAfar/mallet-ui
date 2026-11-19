package io.github.avalonskyafar.mallet.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.interaction.MalletHaptics
import io.github.avalonskyafar.mallet.interaction.MalletHorizontalDragOwner
import io.github.avalonskyafar.mallet.internal.HorizontalDragGate
import io.github.avalonskyafar.mallet.theme.MalletColors
import io.github.avalonskyafar.mallet.theme.MalletHue
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A slim recessed track with a coloured stretch that runs from the start to a neutral bead.
 *
 * The bead follows the finger anywhere on the track; a tap makes it glide to the touched
 * place. The hue behind the bead is the value: either one hue ([hue], cobalt by default)
 * or a scale set with [setHueScale], for example bronze, ochre, cobalt, jade: the
 * stretch then wears the hue nearest to the bead; at the half-way point to the next hue it
 * switches to that one, over a short fade. The rest of the track stays a neutral groove.
 *
 * With a [stepSize] above zero the bead snaps to the step grid and every crossed step gives a
 * detent haptic; with `0` the slider is continuous.
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.widget.MalletSlider
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:malletValueFrom="0"
 *     app:malletValueTo="100"
 *     app:malletStepSize="5"
 *     app:malletValue="40"
 *     app:malletHue="cobalt" />
 * ```
 *
 * The view is 44 dp tall by default: a 14 dp track, centred, under a 28 dp bead. The track is
 * inset by half a bead on both sides, so the bead and its shadow never leave the view.
 */
public class MalletSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MalletControl(context, attrs, defStyleAttr), MalletHorizontalDragOwner {

    /** Called when the user changes the value by touch, keyboard or an accessibility action. */
    public fun interface OnValueChangeListener {
        /**
         * The user moved the bead of [slider]; [value] is the new value, already clamped and
         * snapped. Changes made from code through [MalletSlider.value], [MalletSlider.setValue]
         * or the range setters do not come here.
         */
        public fun onValueChanged(slider: MalletSlider, value: Float)
    }

    /** Called when a touch that moved the bead ends: after a drag or a tap, once per gesture. */
    public fun interface OnStopTrackingTouchListener {
        /** The finger has lifted (or the drag was taken away); [MalletSlider.value] is final. */
        public fun onStopTrackingTouch(slider: MalletSlider)
    }

    private var from = 0f
    private var to = 1f
    private var step = 0f
    private var current = 0f

    /** Where the bead is drawn, 0 at the start and 1 at the end; glides behind [current]. */
    private var position = 0f
    private var singleHue: MalletHue = theme.hues.cobalt
    private var scale: List<MalletHue>? = null
    private var down = false
    private var moved = false
    private val gate = HorizontalDragGate(touchSlop)
    private var animator: ValueAnimator? = null

    // With a scale: the hue that is shown right now, and the blend that is replacing the
    // colour before it (1 = finished).
    private var shownIndex = 0
    private var blend = 1f
    private var blendFromBed = 0
    private var blendFromEdge = 0
    private var blendAnimator: ValueAnimator? = null
    private val trackBounds = RectF()
    private val beadBounds = RectF()
    private val trackClip = Path()

    private var valueChangeListener: OnValueChangeListener? = null

    /** Listener for value changes made by the user. */
    public fun setOnValueChangeListener(listener: OnValueChangeListener?) {
        valueChangeListener = listener
    }

    private var stopTrackingTouchListener: OnStopTrackingTouchListener? = null

    /** Listener for the end of a touch gesture, the moment to commit an expensive change. */
    public fun setOnStopTrackingTouchListener(listener: OnStopTrackingTouchListener?) {
        stopTrackingTouchListener = listener
    }

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

    /** Lower bound, at the start of the track. Changing it re-clamps [value]. NaN and infinity are ignored. */
    public var valueFrom: Float
        get() = from
        set(value) {
            if (!value.isFinite()) return
            from = value
            rangeChanged()
        }

    /** Upper bound, at the end of the track. Changing it re-clamps [value]. NaN and infinity are ignored. */
    public var valueTo: Float
        get() = to
        set(value) {
            if (!value.isFinite()) return
            to = value
            rangeChanged()
        }

    /**
     * Distance between two stops, counted from [valueFrom]; `0` makes the slider continuous, and
     * so does a negative, NaN or infinite step.
     */
    public var stepSize: Float
        get() = step
        set(value) {
            step = cleanStep(value)
            rangeChanged()
        }

    /** The hue of the stretch behind the bead. Setting it ends a scale set by [setHueScale]. */
    public var hue: MalletHue
        get() = singleHue
        set(value) {
            singleHue = value
            scale = null
            syncHue(animate = false)
            invalidate()
        }

    init {
        // The slider has no click: Space and Enter are not consumed, taps move the bead instead.
        isClickable = false
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletSlider, defStyleAttr, 0)
            try {
                from = a.getFloat(R.styleable.MalletSlider_malletValueFrom, from).takeIf { it.isFinite() } ?: from
                to = a.getFloat(R.styleable.MalletSlider_malletValueTo, to).takeIf { it.isFinite() } ?: to
                step = cleanStep(a.getFloat(R.styleable.MalletSlider_malletStepSize, step))
                current = a.getFloat(R.styleable.MalletSlider_malletValue, from)
                singleHue = theme.hues.fromAttr(a.getInt(R.styleable.MalletSlider_malletHue, 2))
                if (a.getInt(R.styleable.MalletSlider_malletSliderHueMode, MODE_SINGLE) == MODE_SCALE) {
                    scale = theme.hues.scale(SCALE_HUES)
                }
                isEnabled = a.getBoolean(R.styleable.MalletSlider_android_enabled, true)
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
        if (animate) glideTo(fractionOf(current)) else jumpTo(fractionOf(current), blend = false)
        announceState()
    }

    /**
     * Colours the stretch with a scale of hues instead of one hue. The hues of [hues] stand at
     * even distances along the track, the first at [valueFrom] and the last at [valueTo], and
     * the stretch wears the one nearest to the bead. When the bead passes the half-way point
     * between two of them, the next hue blends in and replaces the old one; the stretch never
     * rests on a mixture of two. `null` or an empty list returns to the single [hue].
     */
    public fun setHueScale(hues: List<MalletHue>?) {
        scale = hues?.takeIf { it.isNotEmpty() }?.toList()
        syncHue(animate = false)
        invalidate()
    }

    /** A usable step: anything that is not a positive finite number means "continuous". */
    private fun cleanStep(raw: Float): Float = if (raw.isFinite()) max(0f, raw) else 0f

    private fun rangeChanged() {
        animator?.cancel()
        current = snap(current)
        position = fractionOf(current)
        syncHue(animate = false)
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
        // The coloured stretch is the same capsule drawn again, cut off under the bead's centre.
        val save = canvas.save()
        if (isRtl) {
            canvas.clipRect(centreX, trackBounds.top, trackBounds.right, trackBounds.bottom)
        } else {
            canvas.clipRect(trackBounds.left, trackBounds.top, centreX, trackBounds.bottom)
        }
        material.drawTrack(
            canvas, trackBounds, stretchColor(edge = false), stretchColor(edge = true), lifted = false,
        )
        canvas.restoreToCount(save)

        val radius = dp(BEAD_DP) / 2f
        beadBounds.set(centreX - radius, centreY - radius, centreX + radius, centreY + radius)
        material.drawThumb(canvas, beadBounds, down)
        if (showsFocus) material.drawFocusRim(canvas, beadBounds, radius)
    }

    /** The plain recess: mist in light themes, the plain groove in dark ones. */
    private fun grooveColor(edge: Boolean): Int = if (edge) theme.neutralBedEdge else theme.neutralBed

    /** Bed or rim colour of the stretch right now: the shown hue, or a blend on its way to it. */
    private fun stretchColor(edge: Boolean): Int {
        val hues = scale
        val hue = if (hues == null) singleHue else hues[shownIndex.coerceIn(0, hues.lastIndex)]
        val target = if (edge) theme.bedEdgeColor(hue) else theme.bedColor(hue)
        if (blend >= 1f) return target
        return MalletColors.blend(if (edge) blendFromEdge else blendFromBed, target, blend)
    }

    /**
     * Makes the stretch wear the hue of the scale that is nearest to the bead. With
     * [animate] a new hue blends in over the colour that is showing; without, it
     * is there at once.
     */
    private fun syncHue(animate: Boolean) {
        val hues = scale
        val nearest = if (hues == null) 0 else (position.coerceIn(0f, 1f) * hues.lastIndex).roundToInt()
        if (!animate) {
            blendAnimator?.cancel()
            blendAnimator = null
            shownIndex = nearest
            blend = 1f
            return
        }
        if (nearest == shownIndex) return
        blendFromBed = stretchColor(edge = false)
        blendFromEdge = stretchColor(edge = true)
        shownIndex = nearest
        blendAnimator?.cancel()
        blend = 0f
        blendAnimator = settle(0f, 1f) { blend = it }
    }

    // ---------------------------------------------------------------------------------------
    // Touch and keys
    // ---------------------------------------------------------------------------------------

    // Lint: a slider has no click; accessibility moves it with scroll actions.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val action = event.actionMasked
        when {
            action == MotionEvent.ACTION_DOWN -> {
                gate.down(event)
                down = true
                moved = false
                invalidate()
            }
            action == MotionEvent.ACTION_MOVE -> when (gate.move(this, event)) {
                HorizontalDragGate.State.DRAGGING -> {
                    moved = true
                    change(valueAt(gate.x(event)), glide = false, tap = false)
                }
                HorizontalDragGate.State.ABORTED -> if (down) {
                    down = false
                    invalidate()
                }
                HorizontalDragGate.State.UNDECIDED -> Unit
            }
            // The followed finger lifts, alone or with another one still down. Once the gesture
            // has ended (here, by cancel or by disabling) the gate ignores what is left of it.
            gate.isRelease(event) -> {
                val x = gate.x(event)
                gate.finish(this)
                down = false
                invalidate()
                if (!gate.isAborted) {
                    if (!gate.isDragging) change(valueAt(x), glide = true, tap = true)
                    stopTrackingTouchListener?.onStopTrackingTouch(this)
                }
            }
            action == MotionEvent.ACTION_CANCEL -> {
                // A gesture that has already ended (see above) was reported then, not again.
                val live = gate.isActive
                gate.finish(this)
                down = false
                invalidate()
                // A drag that was taken away has still moved the value: the gesture ends here.
                if (live && moved) stopTrackingTouchListener?.onStopTrackingTouch(this)
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
    private fun stepBy(direction: Int): Boolean = change(stepTarget(direction), glide = true, tap = true)

    /** Where one step in [direction] leads; the current value when an end is in the way. */
    private fun stepTarget(direction: Int): Float {
        val amount = if (step > 0f) step else (to - from) * CONTINUOUS_KEY_SHARE
        return snap(current + direction * amount)
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
        if (glide) glideTo(fractionOf(next)) else jumpTo(fractionOf(next), blend = true)
        announceState()
        valueChangeListener?.onValueChanged(this, next)
        if (tap) MalletHaptics.tick(this) else repeat(crossed.coerceAtMost(MAX_DETENTS)) { MalletHaptics.detent(this) }
        return true
    }

    private fun glideTo(target: Float) {
        animator?.cancel()
        val start = position
        animator = settle(0f, 1f) { progress ->
            position = start + (target - start) * progress
            syncHue(animate = true)
        }
    }

    /** Puts the bead at [target] at once; [blend] says whether a new hue blends in or is just there. */
    private fun jumpTo(target: Float, blend: Boolean) {
        animator?.cancel()
        animator = null
        position = target
        syncHue(animate = blend)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        position = fractionOf(current)
        syncHue(animate = false)
        super.onDetachedFromWindow()
    }

    override fun setEnabled(enabled: Boolean) {
        // Disabling under the finger ends the gesture; the rest of its events are ignored.
        if (!enabled && down) {
            gate.finish(this)
            down = false
            if (moved) stopTrackingTouchListener?.onStopTrackingTouch(this)
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
        // Offered only where a step leads: the last stop may lie below valueTo.
        if (stepTarget(1) != current) node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
        if (stepTarget(-1) != current) node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD)
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

        const val CONTINUOUS_KEY_SHARE = 0.05f
        const val SCALE_HUES = 4
        const val MODE_SINGLE = 0
        const val MODE_SCALE = 1
        const val MAX_DETENTS = 3
        const val MAX_DECIMALS = 3
        const val STEP_EPSILON = 1e-4f
        val POWERS_OF_TEN = floatArrayOf(1f, 10f, 100f, 1000f)
        const val STATE_SUPER = "mallet:super"
        const val STATE_VALUE = "mallet:slider:value"
    }
}
