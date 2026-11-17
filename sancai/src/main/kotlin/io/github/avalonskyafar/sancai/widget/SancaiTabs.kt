package io.github.avalonskyafar.sancai.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller
import androidx.annotation.VisibleForTesting
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.interaction.SancaiHorizontalDragOwner
import io.github.avalonskyafar.sancai.interaction.SancaiPager
import io.github.avalonskyafar.sancai.internal.HorizontalDragGate
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Text tabs for switching between sibling views.
 *
 * Tabs are a different idea from [SancaiSegmentedTrack] on purpose. The track selects a *value*
 * and shows it with glaze; tabs select a *view* and stay quiet: muted labels on the bare canvas,
 * the selected one in ink, and under it a small accent capsule exactly as wide as the label. The
 * capsule glides to the new label when the selection changes and can follow a continuous
 * position ([setScrollPosition]), so it tracks the finger while a [SancaiPager] is dragged. A
 * hairline runs along the bottom edge from one side of the view to the other.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiTabs
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiOptions="@array/library_sections"
 *     app:sancaiSelectedIndex="0"
 *     app:sancaiTabsLayout="fill" />
 * ```
 *
 * With [Layout.FILL] every tab gets the same width. With [Layout.SCROLL] every tab is as wide as
 * its label plus 16 dp on either side; when the tabs do not fit, the row scrolls with the finger
 * and always brings the selected tab into view.
 *
 * Left and right arrows move the selection (mirrored in right-to-left layouts), Home and End
 * jump to the first and last tab. Accessibility services see a tab list with one tab per label.
 * The view is 44 dp tall.
 */
public class SancaiTabs @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiControl(context, attrs, defStyleAttr), SancaiHorizontalDragOwner {

    /** How the tabs share the width of the view. */
    public enum class Layout {
        /** Every tab gets the same share of the width. */
        FILL,

        /** Every tab is as wide as its label plus padding; the row scrolls when it has to. */
        SCROLL,
    }

    /** Called when the user chooses another tab. */
    public fun interface OnTabSelectedListener {
        public fun onTabSelected(tabs: SancaiTabs, index: Int)
    }

    private var labels: List<String> = emptyList()
    private var selected = 0

    /** Fractional logical index the indicator stands at; equals [selected] at rest. */
    private var position = 0f
    private var pressed = NONE
    private var animator: ValueAnimator? = null

    // Geometry in content coordinates, left to right as drawn (already mirrored for RTL).
    private var tabLeft = FloatArray(0)
    private var tabWidth = FloatArray(0)
    private var labelWidth = FloatArray(0)

    /** Text size factor of a label condensed to fit its equal share; 1 for all others. */
    private var labelScale = FloatArray(0)

    /** The labels as drawn: [labels], with those that do not fit even condensed shortened. */
    private var shown: List<String> = emptyList()
    private val ellipsisPaint = TextPaint()
    private var downY = 0f
    private var contentWidth = 0f
    private var scrollPx = 0f

    private val gate = HorizontalDragGate(touchSlop)
    private val scroller = OverScroller(context)
    private var velocity: VelocityTracker? = null
    private var downScroll = 0f
    private var stripDragged = false

    private var pager: SancaiPager? = null
    private val pagerListener = object : SancaiPager.OnPageChangeListener {
        override fun onPageSelected(index: Int) {
            // A swipe of the pager is user input too. A tab the user chose is already selected
            // when the pager reports it, so it is not reported twice.
            if (index == selected || index !in labels.indices) return
            selected = index
            announceState()
            tabSelectedListener?.onTabSelected(this@SancaiTabs, index)
        }

        override fun onPageScrolled(index: Int, offset: Float) {
            followPager(index + offset)
        }
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorBounds = RectF()
    private val focusBounds = RectF()
    private val accessibility = Accessibility()

    private var tabSelectedListener: OnTabSelectedListener? = null

    /**
     * Listener for user-driven tab changes: a tap, a key, an accessibility action, or a swipe
     * of the pager given to [attachTo]. Not called for [select].
     */
    public fun setOnTabSelectedListener(listener: OnTabSelectedListener?) {
        tabSelectedListener = listener
    }

    /** Index of the selected tab. */
    public val selectedIndex: Int get() = selected

    /** The tab labels, in order. Replace them with [setTabs]. */
    public val tabs: List<CharSequence> get() = labels

    /** How the tabs share the width: equal parts or natural widths in a scrolling row. */
    public var layout: Layout = Layout.FILL
        set(value) {
            if (field == value) return
            field = value
            computeTabs()
            requestLayout()
        }

    init {
        var initial = 0
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiTabs, defStyleAttr, 0)
            try {
                a.getTextArray(R.styleable.SancaiTabs_sancaiOptions)?.let { entries ->
                    labels = entries.map { it.toString() }
                }
                initial = a.getInt(R.styleable.SancaiTabs_sancaiSelectedIndex, 0)
                if (a.getInt(R.styleable.SancaiTabs_sancaiTabsLayout, 0) == 1) layout = Layout.SCROLL
                isEnabled = a.getBoolean(R.styleable.SancaiTabs_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        linePaint.style = Paint.Style.FILL
        jumpTo(initial.coerceIn(0, max(0, labels.size - 1)))
        ViewCompat.setAccessibilityDelegate(this, accessibility)
        announceState()
    }

    /** Replaces the tab labels. The selected index is kept when it is still valid. */
    public fun setTabs(labels: List<CharSequence>) {
        this.labels = labels.map { it.toString() }
        // Tabs that get their labels after the pager was attached take the pager's page.
        val wanted = pager?.currentPage ?: selected
        jumpTo(wanted.coerceIn(0, max(0, this.labels.size - 1)))
        computeTabs()
        announceState()
        requestLayout()
    }

    /** Replaces the tab labels. The selected index is kept when it is still valid. */
    public fun setTabs(vararg labels: CharSequence) {
        setTabs(labels.toList())
    }

    /**
     * Selects tab [index] from code; the indicator glides there when [animate] is true. The
     * listener is not called, and a pager attached with [attachTo] is not moved: move the pager
     * instead and the tabs follow it.
     */
    @JvmOverloads
    public fun select(index: Int, animate: Boolean = true) {
        if (labels.isEmpty()) return
        val next = index.coerceIn(0, labels.size - 1)
        selected = next
        if (animate) animateTo(next.toFloat()) else jumpTo(next)
        announceState()
    }

    /**
     * Places the indicator [offset] (0 to 1) of the way from tab [index] to the tab after it,
     * without changing the selection: the indicator's position and width and the ink of the two
     * labels are blended between both tabs. Call it from a scroll callback to make the tabs
     * follow a finger; finish with [select] once the movement has landed.
     */
    public fun setScrollPosition(index: Int, offset: Float) {
        if (labels.isEmpty()) return
        animator?.cancel()
        position = (index + offset).coerceIn(0f, (labels.size - 1).toFloat())
        revealPosition()
        invalidate()
    }

    /**
     * Couples the tabs with [pager], or ends the coupling with `null`. The indicator then
     * follows every movement of the pager, the selection follows its current page, and choosing
     * a tab glides the pager to that page. The tabs take the pager's page at once. The pager is
     * not asked for labels: set as many tabs as it has pages.
     *
     * [setOnTabSelectedListener] is called when the user chooses a tab and when the user swipes
     * the pager to another page.
     */
    public fun attachTo(pager: SancaiPager?) {
        this.pager?.removeOnPageChangeListener(pagerListener)
        this.pager = pager
        if (pager == null) return
        pager.addOnPageChangeListener(pagerListener)
        if (labels.isNotEmpty()) {
            selected = pager.currentPage.coerceIn(0, labels.size - 1)
            setScrollPosition(0, pager.scrollPosition)
            announceState()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Geometry
    // ---------------------------------------------------------------------------------------

    private val available: Float get() = max(0, width - paddingLeft - paddingRight).toFloat()

    private val scrollRange: Float get() = max(0f, contentWidth - available)

    private fun configureLabelPaint() {
        textPaint.typeface = SancaiWeight.MEDIUM.typeface()
        textPaint.textSize = sp(SancaiTextRole.LABEL.sizeSp)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.style = Paint.Style.FILL
    }

    /** Lays the tabs out in content coordinates, left to right as drawn. */
    private fun computeTabs() {
        val count = labels.size
        if (tabLeft.size != count) {
            tabLeft = FloatArray(count)
            tabWidth = FloatArray(count)
            labelWidth = FloatArray(count)
            labelScale = FloatArray(count)
        }
        shown = labels
        configureLabelPaint()
        val baseSize = textPaint.textSize
        val room = available
        var total = 0f
        for (i in 0 until count) {
            val natural = textPaint.measureText(labels[i])
            labelScale[i] = 1f
            if (layout == Layout.FILL) {
                tabWidth[i] = room / count
                // A label too long for its share is condensed a little, then shortened with an
                // ellipsis; it never runs into its neighbours.
                val limit = max(0f, tabWidth[i] - dp(LABEL_GAP_DP))
                labelWidth[i] = min(natural, limit)
                if (natural > limit && natural > 0f && room > 0f) {
                    labelScale[i] = max(MIN_CONDENSE, limit / natural)
                    if (natural * labelScale[i] > limit + 0.5f) {
                        ellipsisPaint.set(textPaint)
                        ellipsisPaint.textSize = baseSize * labelScale[i]
                        val cut = TextUtils.ellipsize(labels[i], ellipsisPaint, limit, TextUtils.TruncateAt.END).toString()
                        if (shown === labels) shown = labels.toMutableList()
                        (shown as MutableList<String>)[i] = cut
                        labelWidth[i] = min(limit, ellipsisPaint.measureText(cut))
                    }
                }
            } else {
                tabWidth[i] = max(dp(MIN_TAB_DP), natural + dp(SCROLL_PADDING_DP * 2f))
                labelWidth[i] = natural
            }
            total += tabWidth[i]
        }
        contentWidth = total
        // A row shorter than the view starts at the start edge: on the right in RTL.
        var x = if (isRtl) max(0f, room - total) else 0f
        for (slot in 0 until count) {
            val index = if (isRtl) count - 1 - slot else slot
            tabLeft[index] = x
            x += tabWidth[index]
        }
        revealPosition()
        accessibility.invalidateRoot()
        invalidate()
    }

    private fun lerp(from: Float, to: Float, share: Float): Float = from + (to - from) * share

    /** Value of [of] at the fractional tab [at], blended between the two neighbouring tabs. */
    private inline fun between(at: Float, of: (Int) -> Float): Float {
        val last = labels.size - 1
        val first = floor(at).toInt().coerceIn(0, last)
        return lerp(of(first), of(min(first + 1, last)), (at - first).coerceIn(0f, 1f))
    }

    /** Scrolls just far enough that the tab under the indicator and a peek of its neighbours show. */
    private fun revealPosition() {
        val range = scrollRange
        if (range <= 0f || labels.isEmpty() || tabLeft.size != labels.size) {
            scrollPx = 0f
            return
        }
        scroller.abortAnimation()
        val left = between(position) { tabLeft[it] } - dp(PEEK_DP)
        val right = between(position) { tabLeft[it] + tabWidth[it] } + dp(PEEK_DP)
        if (left < scrollPx) scrollPx = left else if (right > scrollPx + available) scrollPx = right - available
        scrollPx = scrollPx.coerceIn(0f, range)
    }

    /** Logical index of the tab at view coordinate [x], or [NONE] beside the row. */
    private fun indexAt(x: Float): Int {
        val contentX = x - paddingLeft + scrollPx
        for (i in labels.indices) {
            if (i < tabLeft.size && contentX >= tabLeft[i] && contentX < tabLeft[i] + tabWidth[i]) return i
        }
        return NONE
    }

    // ---------------------------------------------------------------------------------------
    // Measuring and drawing
    // ---------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        configureLabelPaint()
        var widest = 0f
        var natural = 0f
        for (label in labels) {
            val measured = textPaint.measureText(label)
            widest = max(widest, measured)
            natural += max(dp(MIN_TAB_DP), measured + dp(SCROLL_PADDING_DP * 2f))
        }
        val desired = if (layout == Layout.FILL) {
            max(dp(MIN_TAB_DP), widest + dp(SCROLL_PADDING_DP * 2f)) * labels.size
        } else {
            natural
        }
        setMeasuredDimension(
            resolveSize(desired.roundToInt() + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(dp(HEIGHT_DP).roundToInt() + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Also after a change of padding, text scale or layout direction, which leave the size
        // alone. (Not from onRtlPropertiesChanged: reading the padding there re-enters it.)
        computeTabs()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val hairline = max(1f, dp(HAIRLINE_DP))
        val bottom = (height - paddingBottom).toFloat()
        // Full-bleed: the hairline ignores the padding and does not scroll.
        linePaint.color = theme.colors.divider
        canvas.drawRect(0f, bottom - hairline, width.toFloat(), bottom, linePaint)
        val count = labels.size
        if (count == 0 || tabLeft.size != count || available <= 0f) return

        val save = canvas.save()
        canvas.clipRect(paddingLeft.toFloat(), 0f, (width - paddingRight).toFloat(), height.toFloat())
        canvas.translate(paddingLeft - scrollPx, 0f)

        indicatorRect(indicatorBounds)
        linePaint.color = theme.colors.accent
        val radius = indicatorBounds.height() / 2f
        canvas.drawRoundRect(indicatorBounds, radius, radius, linePaint)

        configureLabelPaint()
        val baseSize = textPaint.textSize
        val metrics = textPaint.fontMetrics
        val centre = paddingTop + (bottom - hairline - dp(INDICATOR_DP) - paddingTop) / 2f + dp(LABEL_DROP_DP)
        val baseline = centre - (metrics.ascent + metrics.descent) / 2f
        for (i in 0 until count) {
            val label = shown.getOrNull(i) ?: labels[i]
            textPaint.textSize = baseSize * labelScale[i]
            // Ink arrives as the indicator does: half way between two tabs both are half inked.
            val nearness = (1f - abs(position - i)).coerceIn(0f, 1f)
            textPaint.color = SancaiColors.blend(theme.colors.muted, theme.colors.ink, nearness)
            val sink = if (i == pressed) dp(SancaiMotion.PRESS_SINK_DP) else 0f
            canvas.drawText(label, tabLeft[i] + tabWidth[i] / 2f, baseline + sink, textPaint)
        }
        textPaint.textSize = baseSize

        if (showsFocus && selected in 0 until count) {
            focusBounds.set(
                tabLeft[selected] + dp(FOCUS_INSET_SIDE_DP),
                paddingTop + dp(FOCUS_INSET_TOP_DP),
                tabLeft[selected] + tabWidth[selected] - dp(FOCUS_INSET_SIDE_DP),
                bottom - dp(FOCUS_INSET_BOTTOM_DP),
            )
            material.drawFocusRim(canvas, focusBounds, dp(FOCUS_RADIUS_DP))
        }
        canvas.restoreToCount(save)
    }

    /** The indicator capsule in content coordinates: as wide as its label, resting on the hairline. */
    private fun indicatorRect(out: RectF) {
        val centre = between(position) { tabLeft[it] + tabWidth[it] / 2f }
        val half = between(position) { labelWidth[it] } / 2f
        val bottom = height - paddingBottom - max(1f, dp(HAIRLINE_DP))
        out.set(centre - half, bottom - dp(INDICATOR_DP), centre + half, bottom)
    }

    // ---------------------------------------------------------------------------------------
    // Touch and keys
    // ---------------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || labels.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.abortAnimation()
                gate.down(event)
                downScroll = scrollPx
                downY = event.y
                stripDragged = false
                pressed = indexAt(event.x)
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                if (scrollRange > 0f) {
                    // A row that scrolls follows the finger; a vertical gesture is handed on.
                    when (gate.move(this, event)) {
                        HorizontalDragGate.State.DRAGGING -> {
                            stripDragged = true
                            scrollPx = (downScroll - (event.x - gate.downX)).coerceIn(0f, scrollRange)
                            releasePress()
                            accessibility.invalidateRoot()
                        }
                        HorizontalDragGate.State.ABORTED -> releasePress()
                        HorizontalDragGate.State.UNDECIDED -> Unit
                    }
                } else if (abs(event.x - gate.downX) > touchSlop || indexAt(event.x) != pressed) {
                    // Nothing to drag here: a finger that wanders off is no longer a tap.
                    releasePress()
                }
                // Neither is one that moves up or down: that gesture belongs to a scrolling parent.
                if (abs(event.y - downY) > touchSlop && !stripDragged) releasePress()
            }
            MotionEvent.ACTION_UP -> {
                gate.finish(this)
                val chosen = pressed
                releasePress()
                if (stripDragged) {
                    fling()
                } else if (chosen != NONE && chosen == indexAt(event.x) && abs(event.y - downY) <= touchSlop) {
                    choose(chosen)
                    performClick()
                }
                endGesture()
            }
            MotionEvent.ACTION_CANCEL -> {
                gate.finish(this)
                releasePress()
                endGesture()
            }
        }
        return true
    }

    private fun releasePress() {
        pressed = NONE
        invalidate()
    }

    private fun endGesture() {
        velocity?.recycle()
        velocity = null
        stripDragged = false
    }

    /** Lets a scrolling row coast after a quick drag. */
    private fun fling() {
        val tracker = velocity ?: return
        if (theme.reduceMotion) return
        val configuration = ViewConfiguration.get(context)
        tracker.computeCurrentVelocity(1000, configuration.scaledMaximumFlingVelocity.toFloat())
        val speed = tracker.xVelocity
        if (abs(speed) < configuration.scaledMinimumFlingVelocity) return
        scroller.fling(scrollPx.roundToInt(), 0, -speed.roundToInt(), 0, 0, scrollRange.roundToInt(), 0, 0)
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollPx = scroller.currX.toFloat().coerceIn(0f, scrollRange)
            postInvalidateOnAnimation()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled && labels.isNotEmpty()) {
            val next = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val forward = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != isRtl
                    selected + if (forward) 1 else -1
                }
                KeyEvent.KEYCODE_MOVE_HOME -> 0
                KeyEvent.KEYCODE_MOVE_END -> labels.size - 1
                else -> NONE
            }
            // At either end an arrow falls through, so focus can leave the tabs.
            if (next in labels.indices && next != selected) {
                choose(next)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** A user-driven change: move, then tell the listener, then tick. */
    private fun choose(index: Int) {
        val old = selected
        if (index == old) {
            animateTo(index.toFloat())
            return
        }
        selected = index
        val attached = pager
        if (attached != null && index < attached.pageCount) {
            // The pager glides and the indicator follows its scroll callbacks. A pager that is
            // busy with another move refuses, and then the tabs stay where the pager is.
            if (!attached.moveToPage(index, animate = true, fromUser = true) && attached.currentPage != index) {
                selected = old
                return
            }
        } else {
            animateTo(index.toFloat())
        }
        announceState()
        tabSelectedListener?.onTabSelected(this, index)
        SancaiHaptics.tick(this)
    }

    private fun followPager(at: Float) {
        if (labels.isEmpty()) return
        animator?.cancel()
        position = at.coerceIn(0f, (labels.size - 1).toFloat())
        // A programmatic page change reports no selection; the tabs still have to agree.
        val page = pager?.currentPage ?: selected
        if (page != selected && page in labels.indices) {
            selected = page
            announceState()
        }
        revealPosition()
        invalidate()
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        val start = position
        if (start == target) {
            revealPosition()
            invalidate()
            return
        }
        animator = settle(0f, 1f) { progress ->
            position = start + (target - start) * progress
            revealPosition()
        }
    }

    private fun jumpTo(index: Int) {
        animator?.cancel()
        selected = index
        position = index.toFloat()
        revealPosition()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        scroller.abortAnimation()
        // Without a pager to follow, a cancelled glide lands on the selection.
        if (pager == null || pager?.isMoving == false) position = selected.toFloat()
        endGesture()
        pressed = NONE
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
        // With a pager attached the pager restores its page and the tabs follow it.
        if (pager != null) return
        val saved = max(0, state.getInt(STATE_SELECTED, selected))
        // Labels that arrive after the restore (loaded data) still find the saved tab selected.
        if (labels.isEmpty()) selected = saved else select(saved, animate = false)
    }

    private fun announceState() {
        val label = labels.getOrNull(selected)
        ViewCompat.setStateDescription(
            this,
            if (label == null) null else resources.getString(R.string.sancai_tabs_position, label, selected + 1, labels.size),
        )
        accessibility.invalidateRoot()
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun getAccessibilityClassName(): CharSequence = "android.widget.TabWidget"

    /** Bounds of tab [index] in view coordinates, as far as they are scrolled into view. */
    private fun tabRect(index: Int, out: Rect) {
        if (index !in labels.indices || index >= tabLeft.size) {
            out.set(0, 0, 1, 1)
            return
        }
        val left = paddingLeft + tabLeft[index] - scrollPx
        out.set(
            max(paddingLeft.toFloat(), left).roundToInt(),
            paddingTop,
            min((width - paddingRight).toFloat(), left + tabWidth[index]).roundToInt(),
            height - paddingBottom,
        )
        if (out.isEmpty) out.set(0, 0, 1, 1)
    }

    /** The indicator capsule in view coordinates, for tests of its geometry. */
    @VisibleForTesting
    internal fun indicatorBounds(out: RectF) {
        if (labels.isEmpty() || tabLeft.size != labels.size) {
            out.setEmpty()
            return
        }
        indicatorRect(out)
        out.offset(paddingLeft - scrollPx, 0f)
    }

    /** How far a scrolling row is scrolled, in pixels from its left end; for tests. */
    @get:VisibleForTesting
    internal val scrollOffset: Float get() = scrollPx

    /** Exposes every tab to accessibility services as a tab of a tab list. */
    private inner class Accessibility : ExploreByTouchHelper(this) {
        private val rect = Rect()

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            if (labels.isEmpty() || width == 0) return INVALID_ID
            val index = indexAt(x)
            return if (index == NONE) INVALID_ID else index
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            for (i in labels.indices) virtualViewIds.add(i)
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            node.className = TAB_CLASS
            node.text = labels.getOrNull(virtualViewId).orEmpty().ifEmpty { " " }
            node.isEnabled = isEnabled
            node.isClickable = true
            node.isSelected = virtualViewId == selected
            node.setCollectionItemInfo(
                AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                    0, 1, virtualViewId, 1, false, virtualViewId == selected,
                ),
            )
            tabRect(virtualViewId, rect)
            @Suppress("DEPRECATION")
            node.setBoundsInParent(rect)
            if (isEnabled && virtualViewId != selected) {
                node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
            }
        }

        override fun onPopulateNodeForHost(node: AccessibilityNodeInfoCompat) {
            node.setCollectionInfo(
                AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(
                    1, labels.size, false, AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_SINGLE,
                ),
            )
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || !isEnabled) return false
            if (virtualViewId !in labels.indices) return false
            choose(virtualViewId)
            return true
        }
    }

    public companion object {
        /** Height of the view in dp. */
        public const val HEIGHT_DP: Float = 44f

        private const val INDICATOR_DP = 3f
        private const val HAIRLINE_DP = 1f
        private const val SCROLL_PADDING_DP = 16f

        /** Narrowest tab: the smallest comfortable touch target. */
        private const val MIN_TAB_DP = 44f

        /** Least room between a condensed label and the edges of an equal-width tab, in total. */
        private const val LABEL_GAP_DP = 8f

        /** Smallest text size factor of a condensed label; beyond it the label gets an ellipsis. */
        private const val MIN_CONDENSE = 0.86f

        /** How much of the neighbouring tab stays visible beside the selected one. */
        private const val PEEK_DP = 24f

        /** Optical correction: the label sits a touch below the centre of the room above the indicator. */
        private const val LABEL_DROP_DP = 0.5f
        private const val FOCUS_INSET_SIDE_DP = 4f
        private const val FOCUS_INSET_TOP_DP = 5f
        private const val FOCUS_INSET_BOTTOM_DP = 8f
        private const val FOCUS_RADIUS_DP = 8f
        private const val NONE = -1
        private const val TAB_CLASS = "android.app.ActionBar\$Tab"
        private const val STATE_SUPER = "sancai:super"
        private const val STATE_SELECTED = "sancai:selected"
    }
}
