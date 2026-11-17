package io.github.avalonskyafar.sancai.widget

import android.animation.ValueAnimator
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
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.interaction.SancaiHorizontalDragOwner
import io.github.avalonskyafar.sancai.internal.HorizontalDragGate
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The signature Sancai control: a recessed glazed track with two or more options and a neutral
 * thumb that follows the finger.
 *
 * The colour of the bed is the state. As the thumb travels, the glaze of the option it leaves
 * blends into the glaze of the option it approaches, so the control answers "which option" by
 * position and "how strong" by colour at the same time.
 *
 * By default the options are read as an ordinal scale and glazed terracotta, ochre, cobalt and
 * celadon in that order (see [io.github.avalonskyafar.sancai.theme.SancaiGlazes.scale]). Use
 * [setGlazes] for a plain choice or a custom scale.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiOptions="@array/strength_levels"
 *     app:sancaiSelectedIndex="2" />
 * ```
 *
 * The view is 60 dp tall by default: a 52 dp track plus the gutter its shadow needs.
 */
public class SancaiSegmentedTrack @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiControl(context, attrs, defStyleAttr), SancaiHorizontalDragOwner {

    /** Called when the user moves the selection to another option. */
    public fun interface OnSelectionChangeListener {
        public fun onSelectionChanged(track: SancaiSegmentedTrack, index: Int)
    }

    private var labels: List<String> = emptyList()
    private var customGlazes: List<SancaiGlaze>? = null
    private var glazes: List<SancaiGlaze> = emptyList()
    private var selected = 0
    private var candidate = 0
    private var thumbPosition = 0f
    private var trackPosition = 0f
    private var down = false
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
            glazes = resolveGlazes()
            jumpTo(selected.coerceIn(0, max(0, labels.size - 1)))
            accessibility.invalidateRoot()
            requestLayout()
        }

    /** Index of the selected option. */
    public val selectedIndex: Int get() = selected

    init {
        var initial = 0
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiSegmentedTrack, defStyleAttr, 0)
            try {
                a.getTextArray(R.styleable.SancaiSegmentedTrack_sancaiOptions)?.let { entries ->
                    labels = entries.map { it.toString() }
                }
                initial = a.getInt(R.styleable.SancaiSegmentedTrack_sancaiSelectedIndex, 0)
                if (a.getInt(R.styleable.SancaiSegmentedTrack_sancaiGlazeMode, MODE_SCALE) == MODE_UNIFORM) {
                    val glaze = theme.glazes.fromAttr(a.getInt(R.styleable.SancaiSegmentedTrack_sancaiGlaze, 2))
                    customGlazes = List(MAX_GLAZES) { glaze }
                }
                isEnabled = a.getBoolean(R.styleable.SancaiSegmentedTrack_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        glazes = resolveGlazes()
        jumpTo(initial.coerceIn(0, max(0, labels.size - 1)))
        ViewCompat.setAccessibilityDelegate(this, accessibility)
    }

    /** Replaces the option labels. */
    public fun setOptions(vararg labels: CharSequence) {
        options = labels.toList()
    }

    /**
     * Sets the glaze poured under each option, or `null` to return to the default ordinal
     * scale. When the list is shorter than the options its last glaze is repeated.
     */
    public fun setGlazes(glazes: List<SancaiGlaze>?) {
        customGlazes = glazes?.takeIf { it.isNotEmpty() }
        this.glazes = resolveGlazes()
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
        val active = glazeOf(candidate)
        val room = segment - dp(6f)
        for (i in 0 until count) {
            val label = labels[i]
            textPaint.textSize = baseSize
            val measured = textPaint.measureText(label)
            if (measured > room && measured > 0f) textPaint.textSize = baseSize * room / measured
            textPaint.color = if (i == candidate) {
                theme.colors.ink
            } else {
                theme.labelOnBed(active, glazeOf(i), count)
            }
            canvas.drawText(label, contentLeft + segment * (toVisual(i.toFloat()) + 0.5f), baseline, textPaint)
        }
        textPaint.textSize = baseSize
    }

    private fun configureLabelPaint(count: Int) {
        textPaint.typeface = SancaiWeight.BOLD.typeface()
        textPaint.textSize = sp(if (count >= 4) LABEL_SMALL_SP else LABEL_SP)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.style = Paint.Style.FILL
    }

    private fun resolveGlazes(): List<SancaiGlaze> {
        val count = max(1, labels.size)
        val custom = customGlazes ?: return theme.glazes.scale(count)
        return List(count) { custom[it.coerceAtMost(custom.size - 1)] }
    }

    private fun glazeOf(index: Int): SancaiGlaze = glazes[index.coerceIn(0, glazes.size - 1)]

    /** Bed or rim colour at a fractional position: the two neighbouring glazes, blended. */
    private fun bedAt(position: Float, edge: Boolean): Int {
        val last = max(0, labels.size - 1)
        val low = floor(position).toInt().coerceIn(0, last)
        val high = (low + 1).coerceAtMost(last)
        val share = (position - low).coerceIn(0f, 1f)
        val first = if (edge) theme.bedEdgeColor(glazeOf(low)) else theme.bedColor(glazeOf(low))
        val second = if (edge) theme.bedEdgeColor(glazeOf(high)) else theme.bedColor(glazeOf(high))
        return SancaiColors.blend(first, second, share)
    }

    /** Converts between logical option position and left-to-right slot; it is its own inverse. */
    private fun toVisual(position: Float): Float = if (isRtl) labels.size - 1 - position else position

    // ---------------------------------------------------------------------------------------
    // Touch and keys
    // ---------------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || labels.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                gate.down(event)
                down = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> when (gate.move(this, event)) {
                HorizontalDragGate.State.DRAGGING -> follow(event.x)
                HorizontalDragGate.State.ABORTED -> if (down) {
                    down = false
                    candidate = selected
                    invalidate()
                }
                HorizontalDragGate.State.UNDECIDED -> Unit
            }
            MotionEvent.ACTION_UP -> {
                gate.finish(this)
                down = false
                if (gate.isAborted) {
                    candidate = selected
                    animateTo(selected.toFloat())
                } else {
                    commit(indexAt(event.x))
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                gate.finish(this)
                down = false
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
            SancaiHaptics.detent(this)
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
                    commit((selected + 1) % labels.size)
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
            SancaiHaptics.tick(this)
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
        private const val MAX_GLAZES = 1
        private const val STATE_SUPER = "sancai:super"
        private const val STATE_SELECTED = "sancai:selected"
    }
}
