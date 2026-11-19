package io.github.avalonskyafar.mallet.widget

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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Checkable
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.interaction.MalletHaptics
import io.github.avalonskyafar.mallet.interaction.MalletHorizontalDragOwner
import io.github.avalonskyafar.mallet.internal.HorizontalDragGate
import io.github.avalonskyafar.mallet.theme.MalletColors
import io.github.avalonskyafar.mallet.theme.MalletHue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The compact two-state switch: a small recessed coloured track with a neutral round thumb.
 *
 * The colour of the bed is the state: bronze while off, cobalt while on, and a blend of
 * the two while the thumb travels. The thumb itself never changes colour. A tap toggles; a
 * horizontal drag makes the thumb follow the finger and commits to the nearer side on
 * release; a mostly vertical movement gives the gesture back to a scrolling parent and
 * leaves the state alone.
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.widget.MalletSwitch
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     app:malletChecked="true" />
 * ```
 *
 * The view is 61 x 44 dp by default: the 61 x 34 dp track of the original (a 58 x 30 dp bed
 * plus the gutter its shadow needs), centred in a 44 dp touch target. A view forced to 34 dp
 * is all track. It carries no label of its own: give it a `contentDescription`.
 *
 * The listener is called for user input only (touch, keyboard, accessibility action), never
 * for [setChecked] or [toggle], so a change can be rolled back from inside the listener.
 */
public class MalletSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MalletControl(context, attrs, defStyleAttr), Checkable, MalletHorizontalDragOwner {

    /** Called when the user turns the switch on or off. */
    public fun interface OnCheckedChangeListener {
        /** The user turned [switch] on or off; [isChecked] is its new state. */
        public fun onCheckedChanged(switch: MalletSwitch, isChecked: Boolean)
    }

    private var checked = false
    private var position = 0f
    private var startPosition = 0f
    private var downX = 0f
    private var down = false
    private var tracking = false
    private var inTouchCommit = false
    private val gate = HorizontalDragGate(touchSlop)
    private var animator: ValueAnimator? = null
    private val trackBounds = RectF()
    private val thumbBounds = RectF()
    private val trackClip = Path()

    private var checkedChangeListener: OnCheckedChangeListener? = null

    /** Listener for user-driven changes. Not called for [setChecked] or [toggle]. */
    public fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        checkedChangeListener = listener
    }

    /** Hue of the bed while the switch is off. Bronze by default. */
    public var offHue: MalletHue = theme.hues.bronze
        set(value) {
            field = value
            invalidate()
        }

    /** Hue of the bed while the switch is on. Cobalt by default. */
    public var onHue: MalletHue = theme.hues.cobalt
        set(value) {
            field = value
            invalidate()
        }

    /** What accessibility services say for the on state. Defaults to the localised "On". */
    public var onStateDescription: CharSequence = context.getString(R.string.mallet_state_on)
        set(value) {
            field = value
            announceState()
        }

    /** What accessibility services say for the off state. Defaults to the localised "Off". */
    public var offStateDescription: CharSequence = context.getString(R.string.mallet_state_off)
        set(value) {
            field = value
            announceState()
        }

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletSwitch, defStyleAttr, 0)
            try {
                checked = a.getBoolean(R.styleable.MalletSwitch_malletChecked, false)
                if (a.hasValue(R.styleable.MalletSwitch_malletSwitchOffHue)) {
                    offHue = theme.hues.fromAttr(a.getInt(R.styleable.MalletSwitch_malletSwitchOffHue, 0))
                }
                if (a.hasValue(R.styleable.MalletSwitch_malletSwitchOnHue)) {
                    onHue = theme.hues.fromAttr(a.getInt(R.styleable.MalletSwitch_malletSwitchOnHue, 2))
                }
                isEnabled = a.getBoolean(R.styleable.MalletSwitch_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        position = if (checked) 1f else 0f
        announceState()
    }

    override fun isChecked(): Boolean = checked

    /** Sets the state from code, gliding the thumb when the view is on screen. No callback. */
    override fun setChecked(checked: Boolean) {
        setChecked(checked, animate = true)
    }

    /**
     * Sets the state from code. The listener is not called, which makes this safe to use for
     * rolling back a change the listener refused. With [animate] `false` the thumb jumps.
     */
    public fun setChecked(checked: Boolean, animate: Boolean) {
        val changed = this.checked != checked
        this.checked = checked
        if (animate) animateTo(target()) else jumpTo(target())
        if (changed) announceState()
    }

    /** Flips the state from code. The listener is not called. */
    override fun toggle() {
        setChecked(!checked, animate = true)
    }

    // ---------------------------------------------------------------------------------------
    // Measuring and drawing
    // ---------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(WIDTH_DP).roundToInt() + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(dp(TARGET_HEIGHT_DP).roundToInt() + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    /**
     * Height of the box the track is drawn in. A view taller than the track keeps up to
     * 5 dp of touch margin above and below it, so the default 44 dp view shows the 34 dp
     * track; taller views grow the track, and a view of 34 dp or less is all track.
     */
    private fun boxHeight(): Float {
        val content = (height - paddingTop - paddingBottom).toFloat()
        return min(content, max(dp(HEIGHT_DP), content - dp(TARGET_HEIGHT_DP - HEIGHT_DP)))
    }

    private fun boxTop(): Float = paddingTop + ((height - paddingTop - paddingBottom) - boxHeight()) / 2f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        trackBounds.set(
            paddingLeft + dp(GUTTER_SIDE_DP),
            boxTop() + dp(GUTTER_TOP_DP),
            width - paddingRight - dp(GUTTER_SIDE_DP),
            boxTop() + boxHeight() - dp(GUTTER_BOTTOM_DP),
        )
        if (trackBounds.width() <= 0f || trackBounds.height() <= 0f) return

        val bed = MalletColors.blend(theme.bedColor(offHue), theme.bedColor(onHue), position)
        val edge = MalletColors.blend(theme.bedEdgeColor(offHue), theme.bedEdgeColor(onHue), position)
        material.drawTrack(canvas, trackBounds, bed, edge, down)

        val inset = dp(INSET_DP)
        val size = thumbSize()
        val visual = if (isRtl) 1f - position else position
        val left = trackBounds.left + inset + visual * travel()
        // Centred in the track, which is the original top-aligned place at the default size.
        val top = trackBounds.centerY() - size / 2f + if (down) dp(PRESS_SINK_DP) else 0f
        thumbBounds.set(left, top, left + size, top + size)
        // The thumb's lift stays inside the rounded track, as in every Mallet track.
        trackClip.rewind()
        trackClip.addRoundRect(trackBounds, trackBounds.height() / 2f, trackBounds.height() / 2f, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(trackClip)
        material.drawThumb(canvas, thumbBounds, down)
        canvas.restoreToCount(save)
        if (showsFocus) material.drawFocusRim(canvas, trackBounds, trackBounds.height() / 2f)
    }

    private fun trackWidth(): Float = width - paddingLeft - paddingRight - dp(GUTTER_SIDE_DP * 2f)

    private fun trackHeight(): Float = boxHeight() - dp(GUTTER_TOP_DP + GUTTER_BOTTOM_DP)

    /** A circle of 26.4 dp, or as much as the padded track height allows. */
    private fun thumbSize(): Float = max(0f, min(dp(THUMB_DP), trackHeight() - dp(INSET_DP) * 2f))

    /** Distance in pixels between the off and the on place of the thumb; never below 1. */
    private fun travel(): Float = max(1f, trackWidth() - dp(INSET_DP) * 2f - thumbSize())

    private fun target(): Float = if (checked) 1f else 0f

    // ---------------------------------------------------------------------------------------
    // Touch and keys
    // ---------------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        // A gesture whose start this view never saw (it was disabled then) changes nothing.
        if (event.actionMasked != MotionEvent.ACTION_DOWN && !tracking) return true
        val action = event.actionMasked
        when {
            action == MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                gate.down(event)
                downX = event.x
                startPosition = position
                down = true
                tracking = true
                invalidate()
            }
            action == MotionEvent.ACTION_MOVE -> when (gate.move(this, event)) {
                HorizontalDragGate.State.DRAGGING -> {
                    val x = gate.x(event)
                    val dx = if (isRtl) downX - x else x - downX
                    position = (startPosition + dx / travel()).coerceIn(0f, 1f)
                    invalidate()
                }
                HorizontalDragGate.State.ABORTED -> if (down) {
                    down = false
                    invalidate()
                }
                HorizontalDragGate.State.UNDECIDED -> Unit
            }
            // The followed finger lifts; fingers that stay down do nothing afterwards.
            gate.isRelease(event) -> {
                val dragged = gate.isDragging
                gate.finish(this)
                down = false
                tracking = false
                if (gate.isAborted) {
                    animateTo(target())
                } else {
                    // A drag commits to the nearer side; a tap toggles.
                    commit(if (dragged) position >= 0.5f else !checked)
                    inTouchCommit = true
                    performClick()
                    inTouchCommit = false
                }
            }
            action == MotionEvent.ACTION_CANCEL -> {
                gate.finish(this)
                down = false
                tracking = false
                animateTo(target())
            }
        }
        return true
    }

    /**
     * Toggles the switch as the user would (the listener is called), then runs the click
     * listener. Rows that host a switch forward their own clicks here.
     */
    override fun performClick(): Boolean {
        if (!inTouchCommit && isEnabled) commit(!checked)
        return super.performClick()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val next = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != isRtl
                    // Towards the side the thumb already rests on the key falls through, so
                    // focus can leave the switch.
                    if (next != checked) {
                        commit(next)
                        return true
                    }
                }
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    // A held key toggles once.
                    if (event.repeatCount == 0) performClick()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** A user-driven change: glide, then tell the listener, then tick. */
    private fun commit(next: Boolean) {
        val changed = next != checked
        checked = next
        animateTo(target())
        if (changed) {
            announceState()
            checkedChangeListener?.onCheckedChanged(this, next)
            MalletHaptics.tick(this)
        }
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        if (position == target) {
            invalidate()
            return
        }
        animator = settle(position, target) { position = it }
    }

    private fun jumpTo(target: Float) {
        animator?.cancel()
        animator = null
        position = target
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        if (!enabled && tracking) {
            // Disabled under the finger: let go and return to the committed side.
            down = false
            tracking = false
            gate.finish(this)
            jumpTo(target())
        }
        super.setEnabled(enabled)
    }

    override fun onDetachedFromWindow() {
        // A glide that lost its window ends where it was heading.
        jumpTo(target())
        down = false
        tracking = false
        super.onDetachedFromWindow()
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
        putBoolean(STATE_CHECKED, checked)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        setChecked(state.getBoolean(STATE_CHECKED, checked), animate = false)
    }

    private fun announceState() {
        ViewCompat.setStateDescription(this, if (checked) onStateDescription else offStateDescription)
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.Switch"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isCheckable = true
        info.isChecked = checked
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        event.isChecked = checked
    }

    public companion object {
        /** Default width of the view in dp: a 58 dp track plus its side gutters. */
        public const val WIDTH_DP: Float = 61f

        /** Height of the box the track is drawn in, in dp: a 30 dp bed plus its shadow gutter. */
        public const val HEIGHT_DP: Float = 34f

        /** Height of the view on `wrap_content`, in dp: the track centred in a 44 dp touch target. */
        public const val TARGET_HEIGHT_DP: Float = 44f

        private const val GUTTER_SIDE_DP = 1.5f
        private const val GUTTER_TOP_DP = 1f
        private const val GUTTER_BOTTOM_DP = 3f
        private const val INSET_DP = 2.8f
        private const val THUMB_DP = 26.4f
        private const val PRESS_SINK_DP = 1f
        private const val STATE_SUPER = "mallet:super"
        private const val STATE_CHECKED = "mallet:checked"
    }
}
