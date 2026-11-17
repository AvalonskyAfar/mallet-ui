package io.github.avalonskyafar.sancai.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Checkable
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconPainter
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.interaction.SancaiHorizontalDragOwner
import io.github.avalonskyafar.sancai.internal.HorizontalDragGate
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The hero switch of a screen: a large glazed capsule whose wide neutral thumb carries a power
 * glyph and the current state, while the free half of the bed names the action a tap performs.
 *
 * The colour of the bed is the state: terracotta while off, cobalt while on, blended by the
 * position of the thumb while it travels. Both labels change the moment the thumb crosses the
 * middle of its travel, not on release, so the control always reads true under the finger.
 *
 * A tap toggles. A horizontal drag follows the finger and settles on the nearer side when it is
 * released; a drag that turns vertical is handed to the scrolling parent and changes nothing.
 * Use one power switch per screen, at full width.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiPowerSwitch
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiChecked="true"
 *     app:sancaiPowerSwitchTurnOffLabel="@string/stop_protection" />
 * ```
 *
 * The view is 92 dp tall by default: an 80 dp capsule plus the gutter its deep shadow needs.
 */
public class SancaiPowerSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiControl(context, attrs, defStyleAttr), Checkable, SancaiHorizontalDragOwner {

    /** Called when the user turns the switch on or off. */
    public fun interface OnCheckedChangeListener {
        /**
         * The user turned [switch] on or off; [isChecked] is the new state. Calling
         * [setChecked] from here rolls the change back without a second callback.
         */
        public fun onCheckedChanged(switch: SancaiPowerSwitch, isChecked: Boolean)
    }

    private var checked = false
    private var thumbPosition = 0f
    private var trackPosition = 0f
    private var startThumbPosition = 0f
    private var visualOn = false
    private var down = false
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

    /** State label shown on the thumb while the switch is on. Defaults to "On". */
    public var onLabel: CharSequence = context.getString(R.string.sancai_power_switch_on)
        set(value) {
            field = value
            labelsChanged()
        }

    /** State label shown on the thumb while the switch is off. Defaults to "Off". */
    public var offLabel: CharSequence = context.getString(R.string.sancai_power_switch_off)
        set(value) {
            field = value
            labelsChanged()
        }

    /** Action label shown on the bed while the switch is off. Defaults to "Turn on". */
    public var turnOnLabel: CharSequence = context.getString(R.string.sancai_power_switch_turn_on)
        set(value) {
            field = value
            labelsChanged()
        }

    /** Action label shown on the bed while the switch is on. Defaults to "Turn off". */
    public var turnOffLabel: CharSequence = context.getString(R.string.sancai_power_switch_turn_off)
        set(value) {
            field = value
            labelsChanged()
        }

    /** Glaze of the bed while the switch is off. Terracotta by default. */
    public var offGlaze: SancaiGlaze = theme.glazes.terracotta
        set(value) {
            field = value
            invalidate()
        }

    /** Glaze of the bed while the switch is on. Cobalt by default. */
    public var onGlaze: SancaiGlaze = theme.glazes.cobalt
        set(value) {
            field = value
            invalidate()
        }

    init {
        var initial = false
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiPowerSwitch, defStyleAttr, 0)
            try {
                initial = a.getBoolean(R.styleable.SancaiPowerSwitch_sancaiChecked, false)
                a.getText(R.styleable.SancaiPowerSwitch_sancaiPowerSwitchOnLabel)?.let { onLabel = it }
                a.getText(R.styleable.SancaiPowerSwitch_sancaiPowerSwitchOffLabel)?.let { offLabel = it }
                a.getText(R.styleable.SancaiPowerSwitch_sancaiPowerSwitchTurnOnLabel)?.let { turnOnLabel = it }
                a.getText(R.styleable.SancaiPowerSwitch_sancaiPowerSwitchTurnOffLabel)?.let { turnOffLabel = it }
                if (a.hasValue(R.styleable.SancaiPowerSwitch_sancaiPowerSwitchOnGlaze)) {
                    onGlaze = theme.glazes.fromAttr(a.getInt(R.styleable.SancaiPowerSwitch_sancaiPowerSwitchOnGlaze, 2))
                }
                if (a.hasValue(R.styleable.SancaiPowerSwitch_sancaiPowerSwitchOffGlaze)) {
                    offGlaze = theme.glazes.fromAttr(a.getInt(R.styleable.SancaiPowerSwitch_sancaiPowerSwitchOffGlaze, 0))
                }
                isEnabled = a.getBoolean(R.styleable.SancaiPowerSwitch_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        jumpTo(initial)
        announceState()
    }

    // ---------------------------------------------------------------------------------------
    // Checkable
    // ---------------------------------------------------------------------------------------

    override fun isChecked(): Boolean = checked

    /** Turns the switch on or off from code, gliding when the view is on screen. Never calls the listener. */
    override fun setChecked(checked: Boolean) {
        setChecked(checked, animate = true)
    }

    /**
     * Turns the switch on or off from code. The listener is not called, which makes this safe
     * for rolling back a change the listener refused. With [animate] false the thumb jumps.
     */
    public fun setChecked(checked: Boolean, animate: Boolean) {
        val changed = this.checked != checked
        this.checked = checked
        if (animate) animateTo(checked) else jumpTo(checked)
        if (changed) announceState()
    }

    /** Flips the state from code. Never calls the listener. */
    override fun toggle() {
        setChecked(!checked)
    }

    // ---------------------------------------------------------------------------------------
    // Measuring and drawing
    // ---------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        configureLabelPaint()
        val state = max(textPaint.measureText(onLabel.toString()), textPaint.measureText(offLabel.toString()))
        val action = max(textPaint.measureText(turnOnLabel.toString()), textPaint.measureText(turnOffLabel.toString()))
        // Wide enough for the thumb to hold its group and for the free half to hold the action.
        val thumb = max(dp(MIN_THUMB_DP), dp(GLYPH_DP + GLYPH_GAP_DP + THUMB_PADDING_DP * 2f) + state)
        val free = action + dp(BED_PADDING_DP * 2f)
        val track = max((thumb + dp(INSET_DP)) / THUMB_SHARE, thumb + free + dp(INSET_DP * 2f))
        val desiredWidth = (track + dp(GUTTER_SIDE_DP * 2f)).roundToInt()
        val desiredHeight = dp(HEIGHT_DP).roundToInt()
        setMeasuredDimension(
            resolveSize(desiredWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(desiredHeight + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    private fun layoutTrack() {
        trackBounds.set(
            paddingLeft + dp(GUTTER_SIDE_DP),
            paddingTop + dp(GUTTER_TOP_DP),
            width - paddingRight - dp(GUTTER_SIDE_DP),
            height - paddingBottom - dp(GUTTER_BOTTOM_DP),
        )
    }

    private fun thumbWidth(): Float = max(dp(MIN_THUMB_DP), trackBounds.width() * THUMB_SHARE - dp(INSET_DP))

    /** Distance the thumb can travel, in pixels; never zero so drags can divide by it. */
    private fun travel(): Float = max(1f, trackBounds.width() - dp(INSET_DP) * 2f - thumbWidth())

    /** Converts the logical position (0 off, 1 on) to a left-to-right fraction. */
    private fun toVisual(position: Float): Float = if (isRtl) 1f - position else position

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        layoutTrack()
        if (trackBounds.width() <= 0f || trackBounds.height() <= 0f) return

        val bed = SancaiColors.blend(theme.bedColor(offGlaze), theme.bedColor(onGlaze), trackPosition)
        val edge = SancaiColors.blend(theme.bedEdgeColor(offGlaze), theme.bedEdgeColor(onGlaze), trackPosition)
        material.drawTrack(canvas, trackBounds, bed, edge, down, hero = true)

        val inset = dp(INSET_DP)
        val thumbWidth = thumbWidth()
        val travel = max(0f, trackBounds.width() - inset * 2f - thumbWidth)
        val left = trackBounds.left + inset + toVisual(thumbPosition) * travel
        thumbBounds.set(left, trackBounds.top + inset, left + thumbWidth, trackBounds.bottom - inset)
        configureLabelPaint()
        val baseSize = textPaint.textSize
        val centerY = trackBounds.centerY() + if (down) dp(PRESS_SINK_DP) else 0f
        // The action label lies on the bed, so the thumb slides over it instead of under it.
        drawAction(canvas, bed, centerY, baseSize)

        // The thumb's lift stays inside the capsule, so its shadow never spills over the rim.
        trackClip.rewind()
        trackClip.addRoundRect(trackBounds, trackBounds.height() / 2f, trackBounds.height() / 2f, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(trackClip)
        material.drawThumb(canvas, thumbBounds, down)
        canvas.restoreToCount(save)
        if (showsFocus) material.drawFocusRim(canvas, trackBounds, trackBounds.height() / 2f)

        drawThumbGroup(canvas, thumbBounds.centerX(), centerY, thumbWidth, baseSize)
        textPaint.textSize = baseSize
        textPaint.textAlign = Paint.Align.CENTER
    }

    /** The power glyph and the state label, centred on the thumb as one group. */
    private fun drawThumbGroup(canvas: Canvas, centerX: Float, centerY: Float, thumbWidth: Float, baseSize: Float) {
        val label = (if (visualOn) onLabel else offLabel).toString()
        val glyph = dp(GLYPH_DP)
        val gap = if (label.isEmpty()) 0f else dp(GLYPH_GAP_DP)
        val shown = fit(label, thumbWidth - glyph - gap - dp(THUMB_PADDING_DP * 2f), baseSize)
        val text = textPaint.measureText(shown, 0, shown.length)
        val groupLeft = centerX - (glyph + gap + text) / 2f
        val glyphLeft = if (isRtl) groupLeft + text + gap else groupLeft
        val textLeft = if (isRtl) groupLeft else groupLeft + glyph + gap
        // The painter draws on a 24 unit grid in which the ring is 17 units wide and centred
        // at (12, 12.5): scale the box so the ring is exactly the glyph size.
        val box = glyph * ICON_GRID / ICON_RING
        SancaiIconPainter.draw(
            canvas,
            SancaiIcon.POWER,
            glyphLeft - box * ICON_RING_LEFT / ICON_GRID,
            centerY - box * ICON_RING_CENTER_Y / ICON_GRID,
            box,
            theme.colors.ink,
            dp(GLYPH_STROKE_DP),
        )
        textPaint.color = theme.colors.ink
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            shown, 0, shown.length, textLeft, centerY - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint,
        )
    }

    /** The action label, centred in the half of the bed the thumb leaves free. */
    private fun drawAction(canvas: Canvas, bed: Int, centerY: Float, baseSize: Float) {
        val label = (if (visualOn) turnOffLabel else turnOnLabel).toString()
        if (label.isEmpty()) return
        // With the proportional thumb the centre of the free half is 22 % or 78 % of the track.
        val thumbAtEnd = visualOn != isRtl
        val freeLeft = if (thumbAtEnd) trackBounds.left else trackBounds.right - freeWidth()
        val shown = fit(label, freeWidth() - dp(BED_PADDING_DP * 2f), baseSize)
        val inkShare = when {
            theme.highContrast -> INK_SHARE_CONTRAST
            theme.isDark -> INK_SHARE_DARK
            else -> INK_SHARE_LIGHT
        }
        textPaint.color = SancaiColors.blend(bed, theme.colors.ink, inkShare)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            shown,
            0,
            shown.length,
            freeLeft + freeWidth() / 2f,
            centerY - (textPaint.ascent() + textPaint.descent()) / 2f,
            textPaint,
        )
    }

    /** Width of the bed beside a resting thumb, from the track rim to the thumb. */
    private fun freeWidth(): Float = max(0f, trackBounds.width() - dp(INSET_DP) - thumbWidth())

    /**
     * Makes [label] fit [room] and returns the text to draw. A label that is too long first
     * shrinks, down to [MIN_LABEL_SCALE] of its size; what still does not fit ends in an
     * ellipsis. Long translations therefore neither spill nor become unreadably small.
     */
    private fun fit(label: String, room: Float, baseSize: Float): CharSequence {
        textPaint.textSize = baseSize
        val measured = textPaint.measureText(label)
        if (room <= 0f || measured <= room) return label
        textPaint.textSize = baseSize * max(MIN_LABEL_SCALE, room / measured)
        if (textPaint.measureText(label) <= room) return label
        ellipsisPaint.set(textPaint)
        return TextUtils.ellipsize(label, ellipsisPaint, room, TextUtils.TruncateAt.END)
    }

    private val ellipsisPaint = TextPaint()

    private fun configureLabelPaint() {
        textPaint.typeface = SancaiWeight.MEDIUM.typeface()
        textPaint.textSize = sp(LABEL_SP)
        textPaint.style = Paint.Style.FILL
    }

    private fun labelsChanged() {
        announceState()
        requestLayout()
        invalidate()
    }

    // ---------------------------------------------------------------------------------------
    // Touch and keys
    // ---------------------------------------------------------------------------------------

    // Lint: the accessibility click toggles through performAccessibilityAction.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                gate.down(event)
                startThumbPosition = thumbPosition
                down = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> when (gate.move(this, event)) {
                HorizontalDragGate.State.DRAGGING -> follow(event.x)
                HorizontalDragGate.State.ABORTED -> if (down) {
                    down = false
                    invalidate()
                }
                HorizontalDragGate.State.UNDECIDED -> Unit
            }
            MotionEvent.ACTION_UP -> {
                gate.finish(this)
                down = false
                if (gate.isAborted) {
                    animateTo(checked)
                } else {
                    // A drag settles on the nearer side; a tap toggles.
                    commit(if (gate.isDragging) thumbPosition >= 0.5f else !checked)
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                gate.finish(this)
                down = false
                animateTo(checked)
            }
        }
        return true
    }

    private fun follow(x: Float) {
        layoutTrack()
        val dx = (x - gate.downX) * if (isRtl) -1f else 1f
        thumbPosition = (startThumbPosition + dx / travel()).coerceIn(0f, 1f)
        trackPosition = thumbPosition
        val on = thumbPosition >= 0.5f
        if (on != visualOn) {
            visualOn = on
            SancaiHaptics.detent(this)
        }
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val target = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != isRtl
                    // Already on that side: the key falls through, so focus can leave the switch.
                    if (target != checked) {
                        commit(target)
                        return true
                    }
                }
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    // A held key toggles once, not on every repeat.
                    if (event.repeatCount == 0) commit(!checked)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** A user-driven change: animate, then tell the listener, then tick. */
    private fun commit(next: Boolean) {
        val changed = next != checked
        checked = next
        animateTo(next)
        if (changed) {
            announceState()
            checkedChangeListener?.onCheckedChanged(this, next)
            SancaiHaptics.tick(this)
        }
    }

    private fun animateTo(on: Boolean) {
        animator?.cancel()
        val target = if (on) 1f else 0f
        val startThumb = thumbPosition
        val startTrack = trackPosition
        if (startThumb == target && startTrack == target) {
            visualOn = on
            invalidate()
            return
        }
        animator = settle(0f, 1f) { progress ->
            thumbPosition = startThumb + (target - startThumb) * progress
            trackPosition = startTrack + (target - startTrack) * progress
            visualOn = thumbPosition >= 0.5f
        }
    }

    private fun jumpTo(on: Boolean) {
        animator?.cancel()
        checked = on
        visualOn = on
        thumbPosition = if (on) 1f else 0f
        trackPosition = thumbPosition
        invalidate()
    }

    override fun onDetachedFromWindow() {
        // Nothing keeps gliding off screen: the thumb rests where the state says it is.
        down = false
        jumpTo(checked)
        super.onDetachedFromWindow()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        // Disabled under the finger: the rest of the gesture is ignored, so let go now.
        if (!enabled && down) {
            down = false
            gate.finish(this)
            animateTo(checked)
        }
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
        ViewCompat.setStateDescription(this, if (checked) onLabel else offLabel)
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

    /** A click from an accessibility service is user input: it toggles and notifies. */
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_CLICK && isEnabled) {
            commit(!checked)
            performClick()
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private companion object {
        const val HEIGHT_DP = 92f
        const val GUTTER_SIDE_DP = 2.5f
        const val GUTTER_TOP_DP = 2f
        const val GUTTER_BOTTOM_DP = 10f
        const val INSET_DP = 5.6f
        const val MIN_THUMB_DP = 112f
        const val THUMB_SHARE = 0.56f
        const val LABEL_SP = 14f
        const val MIN_LABEL_SCALE = 0.8f
        const val GLYPH_DP = 17f
        const val GLYPH_STROKE_DP = 1.8f
        const val GLYPH_GAP_DP = 6.5f
        const val THUMB_PADDING_DP = 14f
        const val BED_PADDING_DP = 12f
        const val PRESS_SINK_DP = 1f
        const val INK_SHARE_LIGHT = 0.76f
        const val INK_SHARE_DARK = 0.82f
        const val INK_SHARE_CONTRAST = 0.92f

        // Geometry of the POWER icon on the painter's grid.
        const val ICON_GRID = 24f
        const val ICON_RING = 17f
        const val ICON_RING_LEFT = 3.5f
        const val ICON_RING_CENTER_Y = 12.5f

        const val STATE_SUPER = "sancai:super"
        const val STATE_CHECKED = "sancai:checked"
    }
}
