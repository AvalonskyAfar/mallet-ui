package io.github.avalonskyafar.mallet.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.interaction.MalletHaptics
import io.github.avalonskyafar.mallet.interaction.MalletHorizontalDragOwner
import io.github.avalonskyafar.mallet.internal.HorizontalDragGate
import io.github.avalonskyafar.mallet.theme.MalletColors
import io.github.avalonskyafar.mallet.theme.MalletHue
import io.github.avalonskyafar.mallet.theme.MalletWeight
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The signature Mallet control: a recessed glazed track with two or more options and a neutral
 * thumb that follows the finger.
 *
 * The colour of the bed is the state. As the thumb travels, the hue of the option it leaves
 * blends into the hue of the option it approaches, so the control answers "which option" by
 * position and "how strong" by colour at the same time.
 *
 * By default the options are read as an ordinal scale and glazed bronze, ochre, cobalt and
 * jade in that order (see [io.github.avalonskyafar.mallet.theme.MalletHues.scale]). Use
 * [setHues] for a plain choice or a custom scale.
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.widget.MalletSegmentedTrack
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:malletOptions="@array/strength_levels"
 *     app:malletSelectedIndex="2" />
 * ```
 *
 * The view is 60 dp tall by default: a 52 dp track plus the gutter its shadow needs.
 */
public class MalletSegmentedTrack @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MalletControl(context, attrs, defStyleAttr), MalletHorizontalDragOwner {

    /** Called when the user moves the selection to another option. */
    public fun interface OnSelectionChangeListener {
        public fun onSelectionChanged(track: MalletSegmentedTrack, index: Int)
    }

    private var labels: List<String> = emptyList()
    private var customHues: List<MalletHue>? = null
    private var hues: List<MalletHue> = emptyList()
    private var selected = 0
    private var candidate = 0
    private var thumbPosition = 0f
    private var trackPosition = 0f
    private var down = false
    private var tracking = false
    private val gate = HorizontalDragGate(touchSlop)
    private var animator: ValueAnimator? = null
    private val trackBounds = RectF()
    private val thumbBounds = RectF()
    private val trackClip = Path()
    private val accessibility = Accessibility()

    private var selectionChangeListener: OnSelectionChangeListener? = null

    /** Listener for user-driven selection changes. Not called for [select]. */
    public fun setOnSelectionChangeListener(listener: OnSelectionChangeListener?) {
        selectionChangeListener = listener
    }

    /** The option labels. Replacing them keeps the selected index when it is still valid. */
    public var options: List<CharSequence>
        get() = labels
        set(value) {
            labels = value.map { it.toString() }
            hues = resolveHues()
            // Replaced under the finger: the gesture was about options that are gone.
            letGo()
            jumpTo(selected.coerceIn(0, max(0, labels.size - 1)))
            announceState()
            requestLayout()
        }

    /** Index of the selected option. */
    public val selectedIndex: Int get() = selected

    init {
        var initial = 0
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletSegmentedTrack, defStyleAttr, 0)
            try {
                a.getTextArray(R.styleable.MalletSegmentedTrack_malletOptions)?.let { entries ->
                    labels = entries.map { it.toString() }
                }
                initial = a.getInt(R.styleable.MalletSegmentedTrack_malletSelectedIndex, 0)
                if (a.getInt(R.styleable.MalletSegmentedTrack_malletHueMode, MODE_SCALE) == MODE_UNIFORM) {
                    val hue = theme.hues.fromAttr(a.getInt(R.styleable.MalletSegmentedTrack_malletHue, 2))
                    customHues = List(MAX_HUES) { hue }
                }
                isEnabled = a.getBoolean(R.styleable.MalletSegmentedTrack_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        hues = resolveHues()
        jumpTo(initial.coerceIn(0, max(0, labels.size - 1)))
        ViewCompat.setAccessibilityDelegate(this, accessibility)
        announceState()
    }

    /** Replaces the option labels. */
    public fun setOptions(vararg labels: CharSequence) {
        options = labels.toList()
    }

    /**
     * Sets the hue poured under each option, or `null` to return to the default ordinal
     * scale. When the list is shorter than the options its last hue is repeated.
     */
    public fun setHues(hues: List<MalletHue>?) {
        customHues = hues?.takeIf { it.isNotEmpty() }
        this.hues = resolveHues()
        invalidate()
    }

    /**
     * Moves the selection to [index] from code. The listener is not called, which makes this
     * safe to use for rolling back a change the listener refused.
     */
    @JvmOverloads
    public fun select(index: Int, animate: Boolean = true) {
        if (labels.isEmpty()) return
        val next = index.coerceIn(0, labels.size - 1)
        selected = next
        candidate = next
        if (animate) animateTo(next.toFloat()) else jumpTo(next)
        announceState()
    }

    // ---------------------------------------------------------------------------------------
    // Measuring and drawing
    // ---------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = max(1, labels.size)
        configureLabelPaint(count)
        var widest = 0f
        for (label in labels) widest = max(widest, textPaint.measureText(label))
        val segment = max(dp(MIN_SEGMENT_DP), widest + dp(28f))
        val desiredWidth = (dp(GUTTER_SIDE_DP * 2 + INSET_DP * 2) + segment * count).roundToInt()
        val desiredHeight = dp(HEIGHT_DP).roundToInt()
        setMeasuredDimension(
            resolveSize(desiredWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(desiredHeight + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = labels.size
        trackBounds.set(
            paddingLeft + dp(GUTTER_SIDE_DP),
            paddingTop + dp(GUTTER_TOP_DP),
            width - paddingRight - dp(GUTTER_SIDE_DP),
            height - paddingBottom - dp(GUTTER_BOTTOM_DP),
        )
        if (count == 0 || trackBounds.width() <= 0f || trackBounds.height() <= 0f) return

        material.drawTrack(canvas, trackBounds, bedAt(trackPosition, edge = false), bedAt(trackPosition, edge = true), down)

        val inset = dp(INSET_DP)
        val contentLeft = trackBounds.left + inset
        val segment = (trackBounds.width() - inset * 2f) / count
        val left = contentLeft + toVisual(thumbPosition) * segment
        thumbBounds.set(left, trackBounds.top + inset, left + segment, trackBounds.bottom - inset)
        // The thumb's lift stays inside the rounded track, so an end option never grows a
        // clipped shadow corner.
        trackClip.rewind()
        trackClip.addRoundRect(trackBounds, trackBounds.height() / 2f, trackBounds.height() / 2f, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(trackClip)
        material.drawThumb(canvas, thumbBounds, down)
        canvas.restoreToCount(save)
        if (showsFocus) material.drawFocusRim(canvas, trackBounds, trackBounds.height() / 2f)

        configureLabelPaint(count)
        val baseSize = textPaint.textSize
        val metrics = textPaint.fontMetrics
        val baseline = trackBounds.centerY() - (metrics.ascent + metrics.descent) / 2f + if (down) dp(1f) else 0f
        val active = hueOf(candidate)
        val room = segment - dp(6f)
        for (i in 0 until count) {
            val label = labels[i]
            textPaint.textSize = baseSize
            val measured = textPaint.measureText(label)
            if (measured > room && measured > 0f) textPaint.textSize = baseSize * room / measured
            textPaint.color = if (i == candidate) {
                theme.colors.ink
            } else {
                theme.labelOnBed(active, hueOf(i), count)
            }
            canvas.drawText(label, contentLeft + segment * (toVisual(i.toFloat()) + 0.5f), baseline, textPaint)
        }
        textPaint.textSize = baseSize
    }

    private fun configureLabelPaint(count: Int) {
        textPaint.typeface = MalletWeight.BOLD.typeface()
        textPaint.textSize = sp(if (count >= 4) LABEL_SMALL_SP else LABEL_SP)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.style = Paint.Style.FILL
    }

    private fun resolveHues(): List<MalletHue> {
        val count = max(1, labels.size)
        val custom = customHues ?: return theme.hues.scale(count)
        return List(count) { custom[it.coerceAtMost(custom.size - 1)] }
    }

    private fun hueOf(index: Int): MalletHue = hues[index.coerceIn(0, hues.size - 1)]

    /** Bed or rim colour at a fractional position: the two neighbouring hues, blended. */
    private fun bedAt(position: Float, edge: Boolean): Int {
        val last = max(0, labels.size - 1)
        val low = floor(position).toInt().coerceIn(0, last)
        val high = (low + 1).coerceAtMost(last)
        val share = (position - low).coerceIn(0f, 1f)
        val first = if (edge) theme.bedEdgeColor(hueOf(low)) else theme.bedColor(hueOf(low))
        val second = if (edge) theme.bedEdgeColor(hueOf(high)) else theme.bedColor(hueOf(high))
        return MalletColors.blend(first, second, share)
    }

    /** Converts between logical option position and left-to-right slot; it is its own inverse. */
    private fun toVisual(position: Float): Float = if (isRtl) labels.size - 1 - position else position

    // ---------------------------------------------------------------------------------------
    // Touch and keys
    // ---------------------------------------------------------------------------------------

    // Lint: each option is its own accessibility element and takes the click there (ExploreByTouchHelper).
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || labels.isEmpty()) return false
        val action = event.actionMasked
        when {
            action == MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                gate.down(event)
                down = true
                tracking = true
                invalidate()
            }
            action == MotionEvent.ACTION_MOVE -> when (gate.move(this, event)) {
                HorizontalDragGate.State.DRAGGING -> follow(gate.x(event))
                HorizontalDragGate.State.ABORTED -> if (down) {
                    down = false
                    candidate = selected
                    invalidate()
                }
                HorizontalDragGate.State.UNDECIDED -> Unit
            }
            // The followed finger lifts; fingers that stay down do nothing afterwards.
            gate.isRelease(event) -> {
                val x = gate.x(event)
                gate.finish(this)
                down = false
                tracking = false
                if (gate.isAborted) {
                    candidate = selected
                    animateTo(selected.toFloat())
                } else {
                    commit(indexAt(x))
                    performClick()
                }
            }
            action == MotionEvent.ACTION_CANCEL -> {
                gate.finish(this)
                down = false
                tracking = false
                candidate = selected
                animateTo(selected.toFloat())
            }
        }
        return true
    }

    private fun slotAt(x: Float): Float {
        val left = paddingLeft + dp(GUTTER_SIDE_DP + INSET_DP)
        val right = width - paddingRight - dp(GUTTER_SIDE_DP + INSET_DP)
        val segment = max(1f, (right - left) / labels.size)
        return (x - left) / segment
    }

    private fun indexAt(x: Float): Int {
        val slot = floor(slotAt(x)).toInt().coerceIn(0, labels.size - 1)
        return toVisual(slot.toFloat()).roundToInt()
    }

    private fun follow(x: Float) {
        val over = indexAt(x)
        if (over != candidate) {
            candidate = over
            MalletHaptics.detent(this)
        }
        val slot = (slotAt(x) - 0.5f).coerceIn(0f, (labels.size - 1).toFloat())
        thumbPosition = toVisual(slot)
        trackPosition = thumbPosition
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled && labels.isNotEmpty()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val forward = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != isRtl
                    val next = selected + if (forward) 1 else -1
                    // At either end the key falls through, so focus can leave the track.
                    if (next in labels.indices) {
                        commit(next)
                        return true
                    }
                }
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    // With one option there is nothing to step to: the key is not consumed
                    // (the platform would take it as a press of a clickable view).
                    if (labels.size < 2) return false
                    // A held key steps once.
                    if (event.repeatCount == 0) commit((selected + 1) % labels.size)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** A user-driven change: animate, then tell the listener, then tick. */
    private fun commit(index: Int) {
        val old = selected
        selected = index
        candidate = index
        animateTo(index.toFloat())
        if (old != index) {
            announceState()
            selectionChangeListener?.onSelectionChanged(this, index)
            MalletHaptics.tick(this)
        }
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        val startThumb = thumbPosition
        val startTrack = trackPosition
        if (startThumb == target && startTrack == target) {
            invalidate()
            return
        }
        animator = settle(0f, 1f) { progress ->
            thumbPosition = startThumb + (target - startThumb) * progress
            trackPosition = startTrack + (target - startTrack) * progress
        }
    }

    private fun jumpTo(index: Int) {
        animator?.cancel()
        selected = index
        candidate = index
        thumbPosition = index.toFloat()
        trackPosition = thumbPosition
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        // Disabled under the finger: let go and rest on the committed option.
        if (!enabled && tracking) {
            letGo()
            jumpTo(selected)
        }
        super.setEnabled(enabled)
    }

    /**
     * Ends a gesture in flight without committing. Does nothing when no finger is down, so a
     * drag on another view keeps its hold on the scrolling parent.
     */
    private fun letGo() {
        if (!tracking) return
        down = false
        tracking = false
        gate.finish(this)
    }

    override fun onDetachedFromWindow() {
        letGo()
        jumpTo(selected)
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------------------------------
    // State and accessibility
    // ---------------------------------------------------------------------------------------

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        putInt(STATE_SELECTED, selected)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        if (labels.isNotEmpty()) select(state.getInt(STATE_SELECTED, selected), animate = false)
    }

    private fun announceState() {
        ViewCompat.setStateDescription(this, labels.getOrNull(selected))
        accessibility.invalidateRoot()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun getAccessibilityClassName(): CharSequence = "android.widget.RadioGroup"

    private fun optionRect(index: Int, out: Rect) {
        val left = paddingLeft + dp(GUTTER_SIDE_DP + INSET_DP)
        val right = width - paddingRight - dp(GUTTER_SIDE_DP + INSET_DP)
        val segment = (right - left) / max(1, labels.size)
        val slot = toVisual(index.toFloat())
        out.set(
            (left + slot * segment).roundToInt(),
            (paddingTop + dp(GUTTER_TOP_DP)).roundToInt(),
            (left + (slot + 1f) * segment).roundToInt(),
            (height - paddingBottom - dp(GUTTER_BOTTOM_DP)).roundToInt(),
        )
        if (out.isEmpty) out.set(0, 0, 1, 1)
    }

    /** Exposes every option to accessibility services as a radio button of its own. */
    private inner class Accessibility : ExploreByTouchHelper(this) {
        private val rect = Rect()

        override fun getVirtualViewAt(x: Float, y: Float): Int =
            if (labels.isEmpty() || width == 0) INVALID_ID else indexAt(x)

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            for (i in labels.indices) virtualViewIds.add(i)
        }

        override fun onPopulateNodeForHost(node: AccessibilityNodeInfoCompat) {
            // The options take the click; the track as a whole has nothing to activate.
            node.isClickable = false
            node.removeAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            node.className = "android.widget.RadioButton"
            node.text = labels.getOrNull(virtualViewId).orEmpty().ifEmpty { " " }
            node.isCheckable = true
            node.isChecked = virtualViewId == selected
            node.isEnabled = isEnabled
            optionRect(virtualViewId, rect)
            @Suppress("DEPRECATION")
            node.setBoundsInParent(rect)
            if (isEnabled && virtualViewId != selected) {
                node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
            }
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || !isEnabled) return false
            if (virtualViewId !in labels.indices) return false
            commit(virtualViewId)
            return true
        }
    }

    public companion object {
        /** Default height of the view in dp: a 52 dp track plus its shadow gutter. */
        public const val HEIGHT_DP: Float = 60f

        /** Room at each side of the capsule, inside the view, in dp. */
        public const val GUTTER_SIDE_DP: Float = 1.5f

        /** Room above the capsule, inside the view, in dp. */
        public const val GUTTER_TOP_DP: Float = 1.5f

        /**
         * Room below the capsule, inside the view, in dp. It holds the shadow, so the capsule
         * sits above the middle of the view: text beside a track is centred on the capsule by
         * lifting it half the difference between this and [GUTTER_TOP_DP].
         */
        public const val GUTTER_BOTTOM_DP: Float = 6.5f

        private const val INSET_DP = 4f
        private const val MIN_SEGMENT_DP = 52f
        private const val LABEL_SP = 12.2f
        private const val LABEL_SMALL_SP = 11.2f
        private const val MODE_SCALE = 0
        private const val MODE_UNIFORM = 1
        private const val MAX_HUES = 1
        private const val STATE_SUPER = "mallet:super"
        private const val STATE_SELECTED = "mallet:selected"
    }
}
