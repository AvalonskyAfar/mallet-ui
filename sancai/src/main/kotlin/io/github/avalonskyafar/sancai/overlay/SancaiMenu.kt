package io.github.avalonskyafar.sancai.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconPainter
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.material.SancaiGutters
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import io.github.avalonskyafar.sancai.widget.SancaiControl
import io.github.avalonskyafar.sancai.widget.SancaiDivider
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A small porcelain panel of actions that grows out of the control that opened it.
 *
 * The menu is built in code and shown in its own popup window:
 *
 * ```kotlin
 * SancaiMenu(context)
 *     .add(getString(R.string.sort_by_name), SancaiIcon.EDIT) { sortByName() }
 *     .add(getString(R.string.sort_by_date)) { sortByDate() }
 *     .addDivider()
 *     .addDanger(getString(R.string.delete_all), SancaiIcon.TRASH) { deleteAll() }
 *     .setChecked(0)
 *     .show(anchor)
 * ```
 *
 * - The panel sits below the anchor, aligned with its end edge. It flips above the anchor when
 *   there is no room below and always stays 8 dp inside the window. A menu taller than the
 *   window scrolls.
 * - Items are indexed in the order they were added; dividers do not count.
 * - Pressing an item sinks its content; nothing is tinted. A click ticks, runs the
 *   item's callback, then dismisses the menu. Touching outside and Back (or Escape) dismiss it as well.
 * - With a keyboard, focus moves into the menu (to the first checked item, else the first
 *   enabled one), Up and Down walk the enabled items, Enter or Space activates, and focus
 *   returns to the anchor when the menu closes.
 * - It grows from the anchor corner; with reduced motion it only fades.
 *
 * A menu can be shown again after it was dismissed, and changed between showings.
 */
public class SancaiMenu(context: Context) {

    private val context: Context = context
    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val entries = ArrayList<Entry>()
    private var popup: MenuPopup? = null
    private var panel: MenuPanel? = null
    private var root: View? = null
    private var anchor: View? = null
    private var closing = false
    private var onDismiss: Runnable? = null

    /** Number of items; dividers do not count. */
    public val itemCount: Int get() = entries.count { !it.divider }

    /** Whether the menu is on screen (it still is while it glides away). */
    public val isShowing: Boolean get() = popup != null

    /**
     * Adds an action with an optional leading [icon]. [onClick] runs when the user picks the
     * item, just before the menu closes.
     */
    @JvmOverloads
    public fun add(label: CharSequence, icon: SancaiIcon? = null, onClick: Runnable? = null): SancaiMenu {
        entries.add(Entry(label, icon, danger = false, action = onClick))
        return this
    }

    /** Adds an action without an icon. For callers in Java. */
    public fun add(label: CharSequence, onClick: Runnable): SancaiMenu = add(label, null, onClick)

    /** Adds a destructive action: label and icon are drawn in the danger colour. */
    @JvmOverloads
    public fun addDanger(label: CharSequence, icon: SancaiIcon? = null, onClick: Runnable? = null): SancaiMenu {
        entries.add(Entry(label, icon, danger = true, action = onClick))
        return this
    }

    /** Adds a destructive action without an icon. For callers in Java. */
    public fun addDanger(label: CharSequence, onClick: Runnable): SancaiMenu = addDanger(label, null, onClick)

    /**
     * Starts a new group: a hairline from rim to rim after the items added so far. A divider
     * with nothing before it, or directly after another one, is ignored.
     */
    public fun addDivider(): SancaiMenu {
        if (entries.isNotEmpty() && !entries.last().divider) entries.add(Entry("", null, false, null, divider = true))
        return this
    }

    /**
     * Marks the item at [index] as the selected one (a trailing check mark) or, with
     * [checked] false, clears its mark. Several items may be checked. Unknown indices are
     * ignored.
     */
    @JvmOverloads
    public fun setChecked(index: Int, checked: Boolean = true): SancaiMenu {
        item(index)?.checked = checked
        panel?.sync()
        return this
    }

    /** Whether the item at [index] carries the check mark. */
    public fun isChecked(index: Int): Boolean = item(index)?.checked == true

    /** Enables or disables the item at [index]. A disabled item pales and cannot be picked. */
    public fun setEnabled(index: Int, enabled: Boolean): SancaiMenu {
        item(index)?.enabled = enabled
        panel?.sync()
        return this
    }

    /** Whether the item at [index] can be picked. */
    public fun isEnabled(index: Int): Boolean = item(index)?.enabled == true

    /** Removes every item and divider. Has no effect on a menu that is showing. */
    public fun clear(): SancaiMenu {
        if (popup == null) entries.clear()
        return this
    }

    /** Called whenever the menu has closed, whatever closed it. */
    public fun setOnDismissListener(listener: Runnable?): SancaiMenu {
        onDismiss = listener
        return this
    }

    private fun item(index: Int): Entry? = entries.filter { !it.divider }.getOrNull(index)

    /**
     * Opens the menu next to [anchor], which must be attached to a window. Does nothing when
     * the menu is empty or already showing.
     */
    public fun show(anchor: View) {
        if (popup != null || itemCount == 0 || anchor.windowToken == null) return
        val content = buildPanel(anchor.layoutDirection)
        val frame = windowFrame(anchor)
        val margin = content.px(WINDOW_MARGIN_DP)
        content.measure(
            View.MeasureSpec.makeMeasureSpec(max(0, frame.width() - 2 * margin) + 2 * content.gutterSide, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val origin = IntArray(2)
        anchor.getLocationInWindow(origin)
        val anchorRect = Rect(origin[0], origin[1], origin[0] + anchor.width, origin[1] + anchor.height)
        val rtl = anchor.layoutDirection == View.LAYOUT_DIRECTION_RTL
        // A menu taller than the window keeps its margins and scrolls instead.
        val roomy = max(0, frame.height() - 2 * margin) + content.gutterTop + content.gutterBottom
        val height = min(content.measuredHeight, roomy)
        val host: View = if (height == content.measuredHeight) content else ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val spot = place(
            anchorRect, frame, content.measuredWidth, height,
            Rect(content.gutterSide, content.gutterTop, content.gutterSide, content.gutterBottom),
            rtl, margin, content.px(ANCHOR_GAP_DP),
        )
        placement = spot
        val window = MenuPopup(this, host, content.measuredWidth, height)
        window.setBackgroundDrawable(null)
        window.elevation = 0f
        window.animationStyle = 0
        window.isOutsideTouchable = true
        window.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        popup = window
        panel = content
        root = host
        this.anchor = anchor
        closing = false
        anchor.addOnAttachStateChangeListener(detachWatcher)
        window.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT, spot.x, spot.y)

        host.pivotX = (if (rtl) content.gutterSide else content.measuredWidth - content.gutterSide).toFloat()
        host.pivotY = (if (spot.above) height - content.gutterBottom else content.gutterTop).toFloat()
        val startScale = if (theme.reduceMotion) 1f else START_SCALE
        host.alpha = 0f
        host.scaleX = startScale
        host.scaleY = startScale
        host.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(SancaiMotion.feedbackDuration(theme.reduceMotion))
            .setInterpolator(SancaiMotion.glide())
            .start()
        content.focusInitial()
    }

    /** Closes the menu; it shrinks back into the anchor corner first. Safe to call at any time. */
    public fun dismiss() {
        val content = root
        if (popup == null || closing || content == null) return
        closing = true
        val endScale = if (theme.reduceMotion) 1f else START_SCALE
        content.animate().cancel()
        content.animate().alpha(0f).scaleX(endScale).scaleY(endScale)
            .setDuration(SancaiMotion.feedbackDuration(theme.reduceMotion))
            .setInterpolator(SancaiMotion.glide())
            .withEndAction { finish() }
            .start()
    }

    private fun finish() {
        val window = popup ?: return
        val opener = anchor
        popup = null
        panel = null
        root = null
        anchor = null
        closing = false
        opener?.removeOnAttachStateChangeListener(detachWatcher)
        window.close()
        if (opener != null && opener.isAttachedToWindow && opener.isFocusable) opener.requestFocus()
        onDismiss?.run()
    }

    /** Closes at once, without the exit glide, when the anchor leaves its window. */
    private val detachWatcher = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            root?.animate()?.cancel()
            finish()
        }
    }

    /** Where the last [show] put the popup; window coordinates of the panel view. */
    internal var placement: Placement? = null
        private set

    /** The panel on screen, or null. */
    internal val showingPanel: MenuPanel? get() = panel

    /** Builds the panel view for the current items. Tests render this directly. */
    internal fun buildPanel(layoutDirection: Int = View.LAYOUT_DIRECTION_LTR): MenuPanel {
        val content = MenuPanel(context, theme)
        content.layoutDirection = layoutDirection
        var index = 0
        for (entry in entries) {
            if (entry.divider) {
                content.addView(SancaiDivider(context), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            } else {
                val view = MenuItemView(context, entry, index++)
                view.setOnClickListener { picked(entry, it) }
                view.onEscape = { dismiss() }
                content.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        content.sync()
        return content
    }

    private fun picked(entry: Entry, view: View) {
        if (closing) return
        // An action: tick first, then run it.
        SancaiHaptics.tick(view)
        entry.action?.run()
        dismiss()
    }

    private fun windowFrame(anchor: View): Rect {
        val frame = Rect()
        anchor.getWindowVisibleDisplayFrame(frame)
        val inWindow = IntArray(2)
        val onScreen = IntArray(2)
        anchor.getLocationInWindow(inWindow)
        anchor.getLocationOnScreen(onScreen)
        frame.offset(inWindow[0] - onScreen[0], inWindow[1] - onScreen[1])
        if (frame.isEmpty) frame.set(0, 0, anchor.rootView.width, anchor.rootView.height)
        return frame
    }

    /** One row of the menu, or a group divider. */
    internal class Entry(
        val label: CharSequence,
        val icon: SancaiIcon?,
        val danger: Boolean,
        val action: Runnable?,
        val divider: Boolean = false,
    ) {
        var checked: Boolean = false
        var enabled: Boolean = true
    }

    /** Top-left corner of the panel view in window coordinates, and whether it opens upwards. */
    internal class Placement(val x: Int, val y: Int, val above: Boolean)

    public companion object {
        /** Narrowest panel body, in dp. */
        public const val MIN_WIDTH_DP: Float = 168f

        /** Widest panel body, in dp; longer labels are ellipsised. */
        public const val MAX_WIDTH_DP: Float = 280f

        /** Height of an item at the default text size, in dp. */
        public const val ITEM_HEIGHT_DP: Float = 48f

        /** Distance the panel keeps from the window edges, in dp. */
        public const val WINDOW_MARGIN_DP: Float = 8f

        internal const val ANCHOR_GAP_DP: Float = 4f
        internal const val START_SCALE: Float = 0.92f

        /**
         * Places a panel view of [width] by [height] whose visible body is inset by [gutters].
         * The body hangs below [anchor] with its end edge on the anchor's end edge, flips above
         * when it does not fit below (and fits above, or there is more room there), and is
         * kept [margin] inside [frame]. All rectangles are in window coordinates.
         */
        internal fun place(
            anchor: Rect,
            frame: Rect,
            width: Int,
            height: Int,
            gutters: Rect,
            rtl: Boolean,
            margin: Int,
            gap: Int,
        ): Placement {
            val bodyWidth = width - gutters.left - gutters.right
            val bodyHeight = height - gutters.top - gutters.bottom
            val minLeft = frame.left + margin
            val maxLeft = max(minLeft, frame.right - margin - bodyWidth)
            val left = (if (rtl) anchor.left else anchor.right - bodyWidth).coerceIn(minLeft, maxLeft)

            val minTop = frame.top + margin
            val maxTop = max(minTop, frame.bottom - margin - bodyHeight)
            val below = anchor.bottom + gap
            val aboveTop = anchor.top - gap - bodyHeight
            val roomBelow = frame.bottom - margin - below
            val roomAbove = anchor.top - gap - minTop
            val above = below > maxTop && (aboveTop >= minTop || roomAbove > roomBelow)
            val top = (if (above) aboveTop else below).coerceIn(minTop, maxTop)
            return Placement(left - gutters.left, top - gutters.top, above)
        }
    }
}

/** The popup window; every platform dismissal (outside touch, Back) goes through the menu's exit. */
private class MenuPopup(
    private val owner: SancaiMenu,
    content: View,
    width: Int,
    height: Int,
) : PopupWindow(content, width, height, true) {

    override fun dismiss() {
        owner.dismiss()
    }

    fun close() {
        super.dismiss()
    }
}

/**
 * The content view of the popup: the porcelain body with its shadow gutter, the items and the
 * group dividers.
 */
@SuppressLint("ViewConstructor")
internal class MenuPanel(context: Context, theme: SancaiTheme) : LinearLayout(context) {

    private val material = SancaiMaterial(context, theme)
    private val body = RectF()

    val gutterSide: Int = ceil(material.dp(SancaiGutters.SURFACE_SIDE_DP)).toInt()
    val gutterTop: Int = ceil(material.dp(SancaiGutters.SURFACE_TOP_DP)).toInt()
    val gutterBottom: Int = ceil(material.dp(SancaiGutters.SURFACE_BOTTOM_DP)).toInt()

    /** The item views, top to bottom. */
    val items: List<MenuItemView>
        get() = (0 until childCount).mapNotNull { getChildAt(it) as? MenuItemView }

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        val pad = px(VERTICAL_PADDING_DP)
        setPadding(gutterSide, gutterTop + pad, gutterSide, gutterBottom + pad)
        ViewCompat.setAccessibilityPaneTitle(this, context.getString(R.string.sancai_menu_pane_title))
    }

    fun px(dp: Float): Int = material.dp(dp).roundToInt()

    /** Re-reads the entries: shared icon and check columns, enabled state. */
    fun sync() {
        val views = items
        val icons = views.any { it.entry.icon != null }
        val checks = views.any { it.entry.checked }
        for (view in views) view.sync(icons, checks)
        requestLayout()
    }

    /** Puts keyboard focus on the first checked item, else on the first enabled one. */
    fun focusInitial() {
        val views = items.filter { it.isEnabled }
        (views.firstOrNull { it.entry.checked } ?: views.firstOrNull())?.requestFocus()
    }

    /** Moves focus from [from] to the next enabled item in [direction] (+1 down, -1 up). */
    fun moveFocus(from: MenuItemView, direction: Int): Boolean {
        val views = items
        var index = views.indexOf(from) + direction
        while (index in views.indices) {
            if (views[index].isEnabled) return views[index].requestFocus()
            index += direction
        }
        return false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var wanted = 0
        for (view in items) wanted = max(wanted, view.wantedWidth())
        var bodyWidth = wanted.coerceIn(px(SancaiMenu.MIN_WIDTH_DP), px(SancaiMenu.MAX_WIDTH_DP))
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            bodyWidth = min(bodyWidth, MeasureSpec.getSize(widthMeasureSpec) - 2 * gutterSide)
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(max(0, bodyWidth) + 2 * gutterSide, MeasureSpec.EXACTLY),
            heightMeasureSpec,
        )
    }

    override fun onDraw(canvas: Canvas) {
        body.set(
            gutterSide.toFloat(), gutterTop.toFloat(),
            (width - gutterSide).toFloat(), (height - gutterBottom).toFloat(),
        )
        material.drawSurface(canvas, body, material.dp(SancaiMaterial.RADIUS_PANEL_DP))
    }

    override fun getAccessibilityClassName(): CharSequence = "android.view.Menu"

    private companion object {
        const val VERTICAL_PADDING_DP = 4f
    }
}

/**
 * One action row: optional leading icon, start-aligned label, optional trailing check mark.
 * The whole content sinks while the row is pressed.
 */
@SuppressLint("ViewConstructor")
internal class MenuItemView(
    context: Context,
    val entry: SancaiMenu.Entry,
    private val index: Int,
) : SancaiControl(context) {

    /** Called for the Escape key. */
    var onEscape: (() -> Unit)? = null

    private val labelPaint = TextPaint(textPaint)
    private val focusBounds = RectF()
    private var iconColumn = false
    private var checkColumn = false
    private var sink = 0f
    private var sinkAnimator: android.animation.ValueAnimator? = null
    private var shown: CharSequence = entry.label
    private var shownFor = -1

    init {
        val scaledDensity = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics)
        SancaiTextRole.SUBHEAD.applyTo(labelPaint, theme, scaledDensity)
        labelPaint.typeface = SancaiWeight.REGULAR.typeface()
        contentDescription = entry.label
    }

    /** Whether any row of the menu has an icon or a check mark: rows share those columns. */
    fun sync(icons: Boolean, checks: Boolean) {
        iconColumn = icons
        checkColumn = checks
        isEnabled = entry.enabled
        isFocusable = entry.enabled
        shownFor = -1
        invalidate()
    }

    private fun labelStartInset(): Float = dp(SIDE_PADDING_DP) + if (iconColumn) dp(ICON_DP + ICON_GAP_DP) else 0f

    private fun labelEndInset(): Float = dp(SIDE_PADDING_DP) + if (checkColumn) dp(ICON_DP + ICON_GAP_DP) else 0f

    /** Width this row needs to show its label in full. */
    fun wantedWidth(): Int =
        ceil(labelStartInset() + labelPaint.measureText(entry.label, 0, entry.label.length) + labelEndInset()).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val metrics = labelPaint.fontMetrics
        val height = max(dp(SancaiMenu.ITEM_HEIGHT_DP), metrics.descent - metrics.ascent + dp(2 * MIN_TEXT_MARGIN_DP))
        setMeasuredDimension(
            getDefaultSize(wantedWidth(), widthMeasureSpec),
            resolveSize(height.roundToInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val colors = theme.colors
        val rtl = isRtl
        val icon = dp(ICON_DP)
        val iconTop = (height - icon) / 2f + sink
        val stroke = dp(ICON_STROKE_DP)
        entry.icon?.let {
            val left = if (rtl) width - dp(SIDE_PADDING_DP) - icon else dp(SIDE_PADDING_DP)
            SancaiIconPainter.draw(
                canvas, it, left, iconTop, icon,
                if (entry.danger) colors.danger else colors.muted, stroke, rtl && it.isDirectional,
            )
        }
        if (entry.checked) {
            val left = if (rtl) dp(SIDE_PADDING_DP) else width - dp(SIDE_PADDING_DP) - icon
            SancaiIconPainter.draw(canvas, SancaiIcon.CHECK, left, iconTop, icon, colors.ink, stroke)
        }

        val room = (width - labelStartInset() - labelEndInset()).toInt()
        if (shownFor != room) {
            shown = TextUtils.ellipsize(entry.label, labelPaint, max(0, room).toFloat(), TextUtils.TruncateAt.END)
            shownFor = room
        }
        labelPaint.color = if (entry.danger) colors.danger else colors.ink
        val textWidth = labelPaint.measureText(shown, 0, shown.length)
        val x = if (rtl) width - labelStartInset() - textWidth else labelStartInset()
        val metrics = labelPaint.fontMetrics
        val baseline = (height - metrics.descent - metrics.ascent) / 2f + sink
        canvas.drawText(shown, 0, shown.length, x, baseline, labelPaint)

        if (showsFocus) {
            focusBounds.set(dp(FOCUS_INSET_X_DP), dp(FOCUS_INSET_Y_DP), width - dp(FOCUS_INSET_X_DP), height - dp(FOCUS_INSET_Y_DP))
            material.drawFocusRim(canvas, focusBounds, dp(FOCUS_RADIUS_DP))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (isEnabled) sinkTo(dp(SancaiMotion.PRESS_SINK_DP), SancaiMotion.PRESS_MS)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> sinkTo(0f, 0L)
        }
        return super.onTouchEvent(event)
    }

    private fun sinkTo(target: Float, durationMs: Long) {
        sinkAnimator?.cancel()
        sinkAnimator = if (durationMs == 0L) {
            sink = target
            invalidate()
            null
        } else {
            settle(sink, target, durationMs) { sink = it }
        }
    }

    /** How far the content is sunk right now, in pixels. */
    val sinkPx: Float get() = sink

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_DOWN -> (parent as? MenuPanel)?.moveFocus(this, 1) == true
        KeyEvent.KEYCODE_DPAD_UP -> (parent as? MenuPanel)?.moveFocus(this, -1) == true
        KeyEvent.KEYCODE_ESCAPE -> onEscape?.let { it(); true } ?: false
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        sinkAnimator?.cancel()
        sinkAnimator = null
        sink = 0f
        super.onDetachedFromWindow()
    }

    override fun getAccessibilityClassName(): CharSequence = "android.view.MenuItem"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = accessibilityClassName
        info.text = entry.label
        info.isCheckable = checkColumn
        info.isChecked = entry.checked
        info.collectionItemInfo =
            AccessibilityNodeInfo.CollectionItemInfo.obtain(index, 1, 0, 1, false, entry.checked)
    }

    private companion object {
        const val SIDE_PADDING_DP = 16f
        const val ICON_DP = 20f
        const val ICON_GAP_DP = 12f
        const val ICON_STROKE_DP = 1.75f
        const val MIN_TEXT_MARGIN_DP = 12f
        const val FOCUS_INSET_X_DP = 4f
        const val FOCUS_INSET_Y_DP = 2f
        const val FOCUS_RADIUS_DP = 6f
    }
}
