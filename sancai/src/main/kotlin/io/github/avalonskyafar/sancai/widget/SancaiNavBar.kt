package io.github.avalonskyafar.sancai.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconPainter
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The bottom navigation row: quiet outline icons with labels, standing on the bare canvas.
 *
 * There is no bar, no background and no indicator. In [Mode.LAUNCHER] every item is a shortcut
 * and nothing is ever selected. In [Mode.TABS] one item is selected, and the only thing that
 * says so is ink: the selected icon and label turn from muted to ink and the icon stroke grows
 * a little heavier.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiNavBar
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiNavBarMode="tabs"
 *     app:sancaiOptions="@array/nav_labels"
 *     app:sancaiNavBarIcons="@array/nav_icons"
 *     app:sancaiSelectedIndex="0" />
 * ```
 *
 * `sancaiNavBarIcons` is an integer array of `sancaiIcon` values, one per label; a label without
 * an icon gets the "more" circle. From code use [addItem] or [setItems].
 *
 * With a keyboard, the arrow keys move a cursor from item to item and Space or Enter activates
 * the item under it; the selection does not follow the cursor on its own.
 *
 * The row is 72 dp tall and grows by the navigation-bar inset, so it can sit at the very bottom
 * of an edge-to-edge window. Set [elevated] when content scrolls underneath it.
 */
public class SancaiNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiControl(context, attrs, defStyleAttr) {

    /** How the items behave. */
    public enum class Mode {
        /** Every item is a shortcut; nothing is selected; the listener fires on every tap. */
        LAUNCHER,

        /** One item is selected and drawn in ink. */
        TABS,
    }

    /** One entry of the row: an outline [icon] above a short [label]. */
    public class Item(public val icon: SancaiIcon, public val label: CharSequence)

    /** Called when the user activates an item. */
    public fun interface OnItemSelectedListener {
        /**
         * In [Mode.LAUNCHER] this is called for every tap. In [Mode.TABS] it is called when the
         * selection moved to [index]; the bar already shows the new selection, and calling
         * [select] from here rolls it back without calling the listener again.
         */
        public fun onItemSelected(bar: SancaiNavBar, index: Int)
    }

    /** Called in [Mode.TABS] when the user taps the item that is already selected. */
    public fun interface OnItemReselectedListener {
        public fun onItemReselected(bar: SancaiNavBar, index: Int)
    }

    private val entries = ArrayList<Item>()
    private val badges = ArrayList<SancaiBadge?>()
    private var inkShares = FloatArray(0)
    private var selected = 0
    private var cursor = 0
    private var pressed = NONE
    private var sinkPx = 0f
    private var downX = 0f
    private var downY = 0f
    private var insetBottom = 0
    private var sinkAnimator: ValueAnimator? = null
    private var inkAnimator: ValueAnimator? = null
    private val linePaint = Paint()
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scratch = RectF()
    private val accessibility = Accessibility()

    private var itemSelectedListener: OnItemSelectedListener? = null

    /** Listener for user-driven activation. Never called for [select] or [selectedIndex]. */
    public fun setOnItemSelectedListener(listener: OnItemSelectedListener?) {
        itemSelectedListener = listener
    }

    private var itemReselectedListener: OnItemReselectedListener? = null

    /** Listener for a tap on the selected item in [Mode.TABS], typically "scroll to top". */
    public fun setOnItemReselectedListener(listener: OnItemReselectedListener?) {
        itemReselectedListener = listener
    }

    /** Shortcut row or tab row. Switching to [Mode.TABS] selects the first item. */
    public var mode: Mode = Mode.LAUNCHER
        set(value) {
            if (field == value) return
            field = value
            selected = 0
            snapInk()
            announceState()
        }

    /**
     * Index of the selected item in [Mode.TABS]; `-1` in [Mode.LAUNCHER] or without items.
     * Setting it moves the selection without animation and without calling the listener.
     */
    public var selectedIndex: Int
        get() = if (mode == Mode.TABS && entries.isNotEmpty()) selected else NONE
        set(value) = select(value, animate = false)

    /**
     * Draws a full-bleed hairline along the top edge, for content that scrolls underneath the
     * row. Off by default: the row normally stands on the bare canvas.
     */
    public var elevated: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** The items in logical order. In right-to-left layouts the first one is drawn at the right. */
    public val items: List<Item> get() = java.util.Collections.unmodifiableList(entries)

    /**
     * Whether the row grows by the height of the system navigation bar, keeping the items above
     * it in an edge-to-edge window. On by default. The insets are not consumed.
     */
    public var fitNavigationBar: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (!value) insetBottom = 0
            requestLayout()
            if (value) ViewCompat.requestApplyInsets(this)
        }

    init {
        var initial = 0
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiNavBar, defStyleAttr, 0)
            try {
                if (a.getInt(R.styleable.SancaiNavBar_sancaiNavBarMode, 0) == 1) mode = Mode.TABS
                val labels = a.getTextArray(R.styleable.SancaiNavBar_sancaiOptions)
                val iconsId = a.getResourceId(R.styleable.SancaiNavBar_sancaiNavBarIcons, 0)
                val icons = if (iconsId != 0) resources.getIntArray(iconsId) else IntArray(0)
                labels?.forEachIndexed { i, label ->
                    val icon = icons.getOrNull(i)?.let { SancaiIcon.fromAttr(it) } ?: SancaiIcon.MORE_CIRCLE
                    entries.add(Item(icon, label))
                    badges.add(null)
                }
                initial = a.getInt(R.styleable.SancaiNavBar_sancaiSelectedIndex, 0)
                elevated = a.getBoolean(R.styleable.SancaiNavBar_sancaiElevated, false)
                fitNavigationBar = a.getBoolean(R.styleable.SancaiNavBar_sancaiNavBarFitNavigationBar, true)
                isEnabled = a.getBoolean(R.styleable.SancaiNavBar_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        linePaint.color = theme.colors.divider
        haloPaint.color = theme.colors.canvas
        selected = initial.coerceIn(0, max(0, entries.size - 1))
        snapInk()
        ViewCompat.setAccessibilityDelegate(this, accessibility)
        announceState()
    }

    /** Appends an item and returns its index. */
    public fun addItem(icon: SancaiIcon, label: CharSequence): Int {
        entries.add(Item(icon, label))
        badges.add(null)
        itemsChanged()
        return entries.size - 1
    }

    /** Replaces all items. Badges are dropped; the selection is kept when it is still valid. */
    public fun setItems(items: List<Item>) {
        entries.clear()
        entries.addAll(items)
        badges.clear()
        repeat(entries.size) { badges.add(null) }
        itemsChanged()
    }

    private fun itemsChanged() {
        release()
        selected = selected.coerceIn(0, max(0, entries.size - 1))
        cursor = cursor.coerceIn(0, max(0, entries.size - 1))
        snapInk()
        announceState()
        requestLayout()
    }

    /**
     * Moves the selection to [index] from code; the listeners are not called. Does nothing in
     * [Mode.LAUNCHER], where nothing is ever selected.
     */
    @JvmOverloads
    public fun select(index: Int, animate: Boolean = true) {
        if (mode != Mode.TABS || entries.isEmpty()) return
        selected = index.coerceIn(0, entries.size - 1)
        if (animate) glideInk() else snapInk()
        announceState()
    }

    /**
     * Shows a count badge at the top end of the icon of item [index]; a [count] of zero or less
     * removes it. Counts beyond 99 read "99+".
     */
    public fun setBadge(index: Int, count: Int) {
        if (index !in entries.indices) return
        if (count <= 0) {
            badges[index] = null
        } else {
            val badge = badges[index] ?: SancaiBadge(context).also { badges[index] = it }
            badge.showDot = false
            badge.count = count
        }
        badgesChanged()
    }

    /** Shows or removes a small dot without a number at the top end of the icon of item [index]. */
    public fun setBadgeDot(index: Int, show: Boolean) {
        if (index !in entries.indices) return
        if (!show) {
            badges[index] = null
        } else {
            val badge = badges[index] ?: SancaiBadge(context).also { badges[index] = it }
            badge.count = 0
            badge.showDot = true
        }
        badgesChanged()
    }

    /** The count shown on item [index]; zero when it has no badge or only a dot. */
    public fun badgeCount(index: Int): Int = badges.getOrNull(index)?.count ?: 0

    /** Whether item [index] shows a dot without a number. */
    public fun hasBadgeDot(index: Int): Boolean = badges.getOrNull(index)?.let { it.count == 0 && it.showDot } ?: false

    private fun badgesChanged() {
        accessibility.invalidateRoot()
        invalidate()
    }

    // ---------------------------------------------------------------------------------------
    // Selection ink
    // ---------------------------------------------------------------------------------------

    private fun inkTarget(index: Int): Float = if (mode == Mode.TABS && index == selected) 1f else 0f

    private fun snapInk() {
        inkAnimator?.cancel()
        inkShares = FloatArray(entries.size) { inkTarget(it) }
        invalidate()
    }

    /** Lets the ink flow from the old item to the new one. */
    private fun glideInk() {
        inkAnimator?.cancel()
        if (inkShares.size != entries.size) {
            snapInk()
            return
        }
        val from = inkShares.copyOf()
        inkAnimator = settle(0f, 1f) { progress ->
            for (i in from.indices) {
                if (i < inkShares.size) inkShares[i] = from[i] + (inkTarget(i) - from[i]) * progress
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Measuring and drawing
    // ---------------------------------------------------------------------------------------

    private fun configureLabelPaint() {
        textPaint.typeface = SancaiWeight.BOLD.typeface()
        textPaint.textSize = sp(SancaiTextRole.CAPTION.sizeSp)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.style = Paint.Style.FILL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        configureLabelPaint()
        var widest = 0f
        for (item in entries) widest = max(widest, textPaint.measureText(item.label.toString()))
        val cell = max(dp(MIN_ITEM_DP), widest + dp(LABEL_ROOM_DP))
        val desiredWidth = (dp(SIDE_DP * 2f) + cell * max(1, entries.size)).roundToInt()
        val desiredHeight = dp(HEIGHT_DP).roundToInt() + insetBottom
        setMeasuredDimension(
            resolveSize(desiredWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(desiredHeight + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    private fun rowLeft(): Float = paddingLeft + dp(SIDE_DP)

    private fun cellWidth(): Float =
        max(1f, (width - paddingLeft - paddingRight - dp(SIDE_DP * 2f)) / max(1, entries.size))

    /** Converts between logical item index and left-to-right slot; it is its own inverse. */
    private fun toVisual(index: Int): Int = if (isRtl) entries.size - 1 - index else index

    private fun centerX(index: Int): Float = rowLeft() + cellWidth() * (toVisual(index) + 0.5f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (elevated) {
            // Full-bleed: the hairline ignores the side margins and the padding.
            canvas.drawRect(0f, 0f, width.toFloat(), max(1f, dp(HAIRLINE_DP)), linePaint)
        }
        if (entries.isEmpty() || width == 0) return
        val top = paddingTop.toFloat()
        val cell = cellWidth()
        configureLabelPaint()
        val baseSize = textPaint.textSize
        val room = cell - dp(LABEL_ROOM_DP / 2f)
        // One size for the whole row: when a label is too wide, all labels shrink together (down
        // to a floor), and whatever still does not fit is cut with an ellipsis.
        var widest = 0f
        for (item in entries) widest = max(widest, textPaint.measureText(item.label.toString()))
        val labelSize = if (widest > room) baseSize * max(MIN_LABEL_SCALE, room / widest) else baseSize
        for (i in entries.indices) {
            val item = entries[i]
            val ink = inkShares.getOrElse(i) { 0f }
            val color = SancaiColors.blend(theme.colors.muted, theme.colors.ink, ink)
            val cx = centerX(i)
            val sink = if (i == pressed) sinkPx else 0f
            val iconSize = dp(ICON_DP)
            SancaiIconPainter.draw(
                canvas, item.icon, cx - iconSize / 2f, top + sink + dp(ICON_CENTER_DP) - iconSize / 2f, iconSize, color,
                dp(STROKE_DP + (STROKE_SELECTED_DP - STROKE_DP) * ink), isRtl && item.icon.isDirectional,
            )
            textPaint.textSize = labelSize
            val label = fit(item.label.toString(), room)
            textPaint.color = color
            canvas.drawText(label, cx, top + sink + dp(BASELINE_DP), textPaint)
            badges[i]?.let { drawBadge(canvas, it, cx, top + sink) }
            if (showsFocus && i == cursor) {
                scratch.set(cx - cell / 2f + dp(2f), top + dp(2f), cx + cell / 2f - dp(2f), top + dp(HEIGHT_DP - 2f))
                material.drawFocusRim(canvas, scratch, dp(SancaiMaterial.RADIUS_CARD_DP))
            }
        }
        textPaint.textSize = baseSize
    }

    /** [label] as it fits into [room] at the current text size: whole, or cut with an ellipsis. */
    private fun fit(label: String, room: Float): String {
        if (textPaint.measureText(label) <= room) return label
        var kept = textPaint.breakText(label, true, max(0f, room - textPaint.measureText(ELLIPSIS)), null)
        // Never cut a surrogate pair in half.
        if (kept > 0 && Character.isHighSurrogate(label[kept - 1])) kept--
        return label.substring(0, kept).trimEnd() + ELLIPSIS
    }

    /** The badge hangs on the top end corner of the icon, on a thin halo of canvas colour. */
    private fun drawBadge(canvas: Canvas, badge: SancaiBadge, cx: Float, top: Float) {
        if (!badge.isShowing) return
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        badge.measure(unspecified, unspecified)
        val w = badge.measuredWidth
        val h = badge.measuredHeight
        badge.layout(0, 0, w, h)
        val dot = badge.count == 0
        val reach = dp(if (dot) DOT_REACH_DP else BADGE_REACH_DP)
        var left = if (isRtl) cx - reach + h / 2f - w else cx + reach - h / 2f
        left = left.coerceIn(0f, max(0f, (width - w).toFloat()))
        val y = top + dp(if (dot) DOT_CENTER_DP else BADGE_CENTER_DP) - h / 2f
        val halo = dp(HALO_DP)
        scratch.set(left - halo, y - halo, left + w + halo, y + h + halo)
        canvas.drawRoundRect(scratch, scratch.height() / 2f, scratch.height() / 2f, haloPaint)
        val save = canvas.save()
        canvas.translate(left, y)
        badge.draw(canvas)
        canvas.restoreToCount(save)
    }

    // ---------------------------------------------------------------------------------------
    // Touch and keys
    // ---------------------------------------------------------------------------------------

    private fun indexAt(x: Float): Int {
        val slot = floor((x - rowLeft()) / cellWidth()).toInt().coerceIn(0, entries.size - 1)
        return toVisual(slot)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || entries.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                press(indexAt(event.x))
            }
            MotionEvent.ACTION_MOVE -> if (pressed != NONE) {
                // A finger that wanders off the item or starts scrolling takes the press back.
                val outside = event.y < -touchSlop || event.y > height + touchSlop ||
                    event.x < 0f || event.x > width || indexAt(event.x) != pressed
                val scrolling = abs(event.y - downY) > touchSlop && abs(event.y - downY) > abs(event.x - downX)
                if (outside || scrolling) release()
            }
            MotionEvent.ACTION_UP -> {
                val index = pressed
                release()
                if (index != NONE) {
                    activate(index)
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    /** The whole item content sinks; nothing is tinted. */
    private fun press(index: Int) {
        sinkAnimator?.cancel()
        pressed = index
        val depth = dp(SancaiMotion.PRESS_SINK_DP)
        sinkAnimator = settle(0f, depth, SancaiMotion.PRESS_MS) { sinkPx = it }
    }

    private fun release() {
        sinkAnimator?.cancel()
        sinkAnimator = null
        if (pressed == NONE && sinkPx == 0f) return
        pressed = NONE
        sinkPx = 0f
        invalidate()
    }

    /** A user-driven activation: show it, then tell the listener, then tick. */
    private fun activate(index: Int) {
        if (index !in entries.indices) return
        cursor = index
        if (mode == Mode.TABS) {
            if (index == selected) {
                itemReselectedListener?.onItemReselected(this, index)
                SancaiHaptics.tick(this)
                return
            }
            selected = index
            glideInk()
            announceState()
        }
        itemSelectedListener?.onItemSelected(this, index)
        SancaiHaptics.tick(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled && entries.isNotEmpty()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val forward = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) != isRtl
                    val next = cursor + if (forward) 1 else -1
                    // At either end the key falls through, so focus can leave the row.
                    if (next in entries.indices) {
                        cursor = next
                        invalidate()
                        return true
                    }
                }
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    activate(cursor)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        // Focus enters a tab row on the selected tab, and a shortcut row where it was left.
        if (gainFocus && mode == Mode.TABS && entries.isNotEmpty()) cursor = selected
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    // ---------------------------------------------------------------------------------------
    // Insets, state and accessibility
    // ---------------------------------------------------------------------------------------

    override fun onApplyWindowInsets(insets: android.view.WindowInsets): android.view.WindowInsets {
        val bottom = if (fitNavigationBar) {
            // With the view, the compat layer can tell the navigation bar from the keyboard before API 30.
            WindowInsetsCompat.toWindowInsetsCompat(insets, this)
                .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        } else {
            0
        }
        if (bottom != insetBottom) {
            insetBottom = bottom
            requestLayout()
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onDetachedFromWindow() {
        release()
        inkAnimator?.end()
        inkAnimator = null
        super.onDetachedFromWindow()
    }

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
        select(state.getInt(STATE_SELECTED, selected), animate = false)
    }

    private fun announceState() {
        val label = if (mode == Mode.TABS) entries.getOrNull(selected)?.label else null
        ViewCompat.setStateDescription(this, label)
        accessibility.invalidateRoot()
    }

    /** What a screen reader says for item [index]: the label, plus what its badge reports. */
    private fun describe(index: Int): CharSequence {
        val label = entries[index].label
        val badge = badges[index] ?: return label
        return when {
            badge.count > 0 -> context.getString(R.string.sancai_nav_bar_item_with_count, label, badge.text)
            badge.showDot -> context.getString(R.string.sancai_nav_bar_item_with_dot, label)
            else -> label
        }
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun getAccessibilityClassName(): CharSequence =
        if (mode == Mode.TABS) "android.widget.TabWidget" else "android.view.ViewGroup"

    private fun itemRect(index: Int, out: Rect) {
        val cell = cellWidth()
        val left = rowLeft() + toVisual(index) * cell
        out.set(left.roundToInt(), paddingTop, (left + cell).roundToInt(), paddingTop + dp(HEIGHT_DP).roundToInt())
        if (out.isEmpty) out.set(0, 0, 1, 1)
    }

    /** Exposes every item to accessibility services: a tab in a tab row, a button otherwise. */
    private inner class Accessibility : ExploreByTouchHelper(this) {
        private val rect = Rect()

        override fun getVirtualViewAt(x: Float, y: Float): Int =
            if (entries.isEmpty() || width == 0) INVALID_ID else indexAt(x)

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            for (i in entries.indices) virtualViewIds.add(i)
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            val tabs = mode == Mode.TABS
            val known = virtualViewId in entries.indices
            node.className = if (tabs) TAB_CLASS else "android.widget.Button"
            node.contentDescription = if (known) describe(virtualViewId).ifEmpty { " " } else " "
            node.isEnabled = isEnabled
            node.isClickable = true
            if (tabs) {
                node.isSelected = virtualViewId == selected
                node.setCollectionItemInfo(
                    AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                        0, 1, virtualViewId, 1, false, virtualViewId == selected,
                    ),
                )
            }
            if (known) itemRect(virtualViewId, rect) else rect.set(0, 0, 1, 1)
            @Suppress("DEPRECATION")
            node.setBoundsInParent(rect)
            if (isEnabled) node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
        }

        override fun onPopulateNodeForHost(node: AccessibilityNodeInfoCompat) {
            if (mode != Mode.TABS) return
            node.setCollectionInfo(
                AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(
                    1, entries.size, false, AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_SINGLE,
                ),
            )
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || !isEnabled) return false
            if (virtualViewId !in entries.indices) return false
            activate(virtualViewId)
            return true
        }
    }

    private companion object {
        const val NONE = -1
        const val HEIGHT_DP = 72f
        const val SIDE_DP = 18f
        /** Side of the box an icon is drawn in: a glyph of the 24 unit grid is 24 dp across in it. */
        const val ICON_DP = 32f
        const val ICON_CENTER_DP = 21f
        const val BASELINE_DP = 57f
        const val STROKE_DP = 1.7f
        const val STROKE_SELECTED_DP = 2f
        const val MIN_ITEM_DP = 64f
        const val LABEL_ROOM_DP = 16f
        const val HAIRLINE_DP = 1f

        // The first circle of the badge sits on the top end corner of the icon, pulled in a little.
        const val BADGE_REACH_DP = 13f
        const val BADGE_CENTER_DP = 10.5f
        const val MIN_LABEL_SCALE = 0.84f
        const val ELLIPSIS = "…"
        const val DOT_REACH_DP = 11f
        const val DOT_CENTER_DP = 11f
        const val HALO_DP = 1.5f
        const val TAB_CLASS = "android.app.ActionBar\$Tab"
        const val STATE_SUPER = "sancai:super"
        const val STATE_SELECTED = "sancai:selected"
    }
}
