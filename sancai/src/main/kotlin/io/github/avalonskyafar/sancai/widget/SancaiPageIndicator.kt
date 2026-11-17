package io.github.avalonskyafar.sancai.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.os.BundleCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.interaction.SancaiPager
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A row of dots, one per page: muted, with the current one in the accent colour.
 *
 * Attached to a [SancaiPager] it follows the pager continuously: while a page moves, the two
 * dots involved cross-blend with the position. Tapping a dot goes to that page.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiPageIndicator
 *     android:id="@+id/dots"
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     android:layout_gravity="center_horizontal" />
 * ```
 *
 * ```kotlin
 * dots.attachTo(pager)
 * ```
 *
 * Non-obvious behaviour:
 *
 * - Standalone use: set [count] and [selectedIndex] (or `app:sancaiPageIndicatorCount` and
 *   `app:sancaiSelectedIndex`) and listen with [setOnSelectionChangeListener]. While a
 *   pager is attached, both properties mirror the pager and setting them moves the pager.
 * - The listener fires for user input only (tap, keyboard, accessibility action); the pager's
 *   own `onPageSelected` fires as well, because the tap is the user's.
 * - The indicator is the keyboard handle of its pager: it takes focus, and Left / Right move
 *   one page. A key that has no page to go to is not consumed.
 * - Each dot is a 7 dp circle in a 28 dp cell; a pressed dot sinks instead of changing colour.
 * - In dark themes the resting dots recede halfway towards the canvas, so that the accent dot
 *   remains the strongest mark of the row.
 * - Taps, keys and accessibility actions do nothing while the attached pager is disabled.
 * - To screen readers the row is a single element, "Page 2 of 8", with scroll actions.
 * - [selectedIndex] is saved and restored for standalone use; an attached indicator takes its
 *   position from the pager, which saves its own.
 */
public class SancaiPageIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiControl(context, attrs, defStyleAttr) {

    /** Receives the dot the user chose on a [SancaiPageIndicator]. */
    public fun interface OnSelectionChangeListener {
        /** The user moved [indicator] to dot [index]. */
        public fun onSelectionChanged(indicator: SancaiPageIndicator, index: Int)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val focusBounds = RectF()
    private var pager: SancaiPager? = null
    private var ownCount = 0
    private var ownSelected = 0
    private var position = 0f
    private var pressedDot = NO_DOT
    private var described = NO_DOT
    private var downX = 0f
    private var downY = 0f
    private var animator: ValueAnimator? = null

    private val pagerListener = object : SancaiPager.OnPageChangeListener {
        override fun onPageScrolled(index: Int, offset: Float) {
            position = index + offset
            if (described != selectedIndex) describe()
            invalidate()
        }
    }
    private val pagesObserver: () -> Unit = { syncWithPager() }

    private var selectionChangeListener: OnSelectionChangeListener? = null

    /** Called when the user chooses a dot. Not called for changes made from code. */
    public fun setOnSelectionChangeListener(listener: OnSelectionChangeListener?) {
        selectionChangeListener = listener
    }

    /** Number of dots. Mirrors the page count while a pager is attached (setting is ignored). */
    public var count: Int
        get() = pager?.pageCount ?: ownCount
        set(value) {
            if (pager != null || value.coerceAtLeast(0) == ownCount) return
            animator?.cancel()
            ownCount = value.coerceAtLeast(0)
            ownSelected = ownSelected.coerceIn(0, (ownCount - 1).coerceAtLeast(0))
            position = ownSelected.toFloat()
            describe()
            requestLayout()
            invalidate()
        }

    /**
     * Index of the accent dot. With a pager attached this is the pager's current page and
     * setting it jumps the pager there. Never calls [setOnSelectionChangeListener].
     */
    public var selectedIndex: Int
        get() = pager?.currentPage ?: ownSelected
        set(value) {
            val attached = pager
            if (attached != null) {
                attached.setCurrentPage(value, animate = false)
                return
            }
            if (value == ownSelected || value !in 0 until ownCount) return
            animator?.cancel()
            ownSelected = value
            position = value.toFloat()
            describe()
            invalidate()
        }

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiPageIndicator, defStyleAttr, 0)
        count = a.getInt(R.styleable.SancaiPageIndicator_sancaiPageIndicatorCount, 0)
        selectedIndex = a.getInt(R.styleable.SancaiPageIndicator_sancaiSelectedIndex, 0)
        a.recycle()
        // Dots are chosen by position; there is no click, so Enter and Space pass through.
        isClickable = false
        describe()
    }

    /**
     * Follows [pager] and moves it when a dot is tapped; `null` detaches and leaves the dots as
     * they are, for standalone use. The indicator holds the pager until then.
     */
    public fun attachTo(pager: SancaiPager?) {
        val old = this.pager
        if (old === pager) return
        if (old != null) {
            old.removeOnPageChangeListener(pagerListener)
            old.removePagesObserver(pagesObserver)
            ownCount = old.pageCount
            ownSelected = old.currentPage
        }
        animator?.cancel()
        this.pager = pager
        if (pager != null) {
            pager.addOnPageChangeListener(pagerListener)
            pager.addPagesObserver(pagesObserver)
        }
        syncWithPager()
    }

    private fun syncWithPager() {
        val attached = pager
        // Without a pager the dots rest on the selection, also when the pager was let go mid-way.
        position = attached?.scrollPosition ?: ownSelected.toFloat()
        describe()
        requestLayout()
        invalidate()
    }

    private fun describe() {
        val total = count
        described = selectedIndex
        contentDescription =
            if (total > 0) resources.getString(R.string.sancai_pager_page_of, selectedIndex + 1, total) else null
    }

    // ---- Measure and draw --------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cell = dp(CELL_DP)
        val wantedWidth = (count * cell).roundToInt() + paddingLeft + paddingRight
        val wantedHeight = cell.roundToInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(wantedWidth, widthMeasureSpec),
            resolveSize(wantedHeight, heightMeasureSpec),
        )
    }

    /** Left edge of the row of cells: centred in the space inside the padding. */
    private fun rowLeft(): Float {
        val inner = width - paddingLeft - paddingRight
        return paddingLeft + (inner - count * dp(CELL_DP)) / 2f
    }

    private fun dotCenterX(index: Int): Float {
        val slot = if (isRtl) count - 1 - index else index
        return rowLeft() + (slot + 0.5f) * dp(CELL_DP)
    }

    private fun dotAt(x: Float): Int {
        val total = count
        if (total == 0) return NO_DOT
        val slot = floor((x - rowLeft()) / dp(CELL_DP)).toInt()
        if (slot < 0 || slot >= total) return NO_DOT
        return if (isRtl) total - 1 - slot else slot
    }

    override fun onDraw(canvas: Canvas) {
        val total = count
        if (total == 0) return
        val centerY = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val radius = dp(DOT_DP) / 2f
        val rest = restColor()
        val accent = theme.colors.accent
        for (i in 0 until total) {
            val share = (1f - abs(i - position)).coerceIn(0f, 1f)
            dotPaint.color = SancaiColors.blend(rest, accent, share)
            val sink = if (i == pressedDot) dp(SancaiMotion.PRESS_SINK_DP) else 0f
            canvas.drawCircle(dotCenterX(i), centerY + sink, radius, dotPaint)
        }
        if (showsFocus) {
            val cell = dp(CELL_DP)
            val left = rowLeft()
            focusBounds.set(left, centerY - cell / 2f, left + total * cell, centerY + cell / 2f)
            focusBounds.intersect(0f, 0f, width.toFloat(), height.toFloat())
            material.drawFocusRim(canvas, focusBounds, focusBounds.height() / 2f)
        }
    }

    /**
     * Colour of the dots that are not current: the muted ink. On a dark canvas the muted ink is
     * lighter than the accent and would outshine the current dot, so there it recedes towards
     * the canvas and the accent dot stays the brightest mark of the row.
     */
    private fun restColor(): Int {
        val colors = theme.colors
        return if (theme.isDark) SancaiColors.blend(colors.muted, colors.canvas, DARK_REST_RECEDE) else colors.muted
    }

    // ---- Input -------------------------------------------------------------------------------

    /** A user choice: moves, then tells the listener, then ticks. False if nothing moved. */
    private fun choose(index: Int): Boolean {
        if (index == selectedIndex || index !in 0 until count) return false
        val attached = pager
        if (attached != null) {
            // A disabled pager keeps its page, whoever asks on the user's behalf.
            if (!attached.isEnabled) return false
            if (!attached.moveToPage(index, animate = true, fromUser = true)) return false
        } else {
            animator?.cancel()
            val from = position
            ownSelected = index
            animator = settle(from, index.toFloat()) { position = it }
        }
        describe()
        selectionChangeListener?.onSelectionChanged(this, index)
        SancaiHaptics.tick(this)
        return true
    }

    private fun press(dot: Int) {
        if (pressedDot == dot) return
        pressedDot = dot
        invalidate()
    }

    @Suppress("ClickableViewAccessibility") // dots are reached through the scroll actions
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                press(dotAt(event.x))
            }
            MotionEvent.ACTION_MOVE -> {
                // A finger that wanders off is no tap; the dot comes back up.
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) press(NO_DOT)
            }
            MotionEvent.ACTION_UP -> {
                val dot = pressedDot
                press(NO_DOT)
                if (dot != NO_DOT && dot == dotAt(event.x)) choose(dot)
            }
            MotionEvent.ACTION_CANCEL -> press(NO_DOT)
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled) {
            val towardsEnd = if (isRtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            val towardsStart = if (isRtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            when (keyCode) {
                towardsEnd, KeyEvent.KEYCODE_PAGE_DOWN -> if (choose(selectedIndex + 1)) return true
                towardsStart, KeyEvent.KEYCODE_PAGE_UP -> if (choose(selectedIndex - 1)) return true
                KeyEvent.KEYCODE_MOVE_HOME -> if (choose(0)) return true
                KeyEvent.KEYCODE_MOVE_END -> if (choose(count - 1)) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---- Accessibility -----------------------------------------------------------------------

    override fun getAccessibilityClassName(): CharSequence = "android.widget.SeekBar"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        // One element that scrolls; it has no click of its own.
        info.isClickable = false
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        val total = count
        if (total == 0) return
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, (total - 1).toFloat(), selectedIndex.toFloat(),
        )
        if (!isEnabled) return
        if (selectedIndex < total - 1) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        if (selectedIndex > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (isEnabled) {
            when (action) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> return choose(selectedIndex + 1)
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> return choose(selectedIndex - 1)
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    // ---- State -------------------------------------------------------------------------------

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        putInt(STATE_SELECTED, selectedIndex)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        if (pager == null) selectedIndex = state.getInt(STATE_SELECTED, ownSelected)
    }

    /** Disabling under the finger lets the pressed dot come back up. */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) press(NO_DOT)
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        // The dots are mirrored when drawn.
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        // Without a pager to follow, a cancelled glide lands on the selection.
        if (pager == null) position = ownSelected.toFloat()
        press(NO_DOT)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DOT_DP = 7f
        const val CELL_DP = 28f
        const val DARK_REST_RECEDE = 0.5f
        const val NO_DOT = -1
        const val STATE_SUPER = "sancai:super"
        const val STATE_SELECTED = "sancai:selectedIndex"
    }
}
