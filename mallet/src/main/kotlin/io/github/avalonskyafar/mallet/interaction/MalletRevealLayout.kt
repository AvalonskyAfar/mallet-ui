package io.github.avalonskyafar.mallet.interaction

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconPainter
import io.github.avalonskyafar.mallet.internal.HorizontalDragGate
import io.github.avalonskyafar.mallet.internal.useOwnFocusRim
import io.github.avalonskyafar.mallet.material.MalletMaterial
import io.github.avalonskyafar.mallet.theme.MalletMotion
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.theme.MalletWeight
import io.github.avalonskyafar.mallet.widget.MalletControl

/**
 * A row inside a card that slides aside to uncover one action, typically "Delete".
 *
 * The layout takes exactly one child, the foreground row. Behind it lies a recessed coloured
 * bed with the action in a strip [STRIP_WIDTH_DP] wide at the [revealEdge]. Dragging the row
 * away from that edge follows the finger for at most the strip width; on release the row
 * opens when at least [OPEN_FRACTION] of the strip is uncovered and closes otherwise. A tap on
 * the open row closes it, and opening one row closes the other open rows of the same parent
 * (see [closeSiblings]).
 *
 * The foreground gets a flat row fill when it has no background of its own, so the bed stays
 * hidden while the row is closed. The row adds no rim or shadow: it lives in its card.
 *
 * Gestures that start on a [MalletHorizontalDragOwner] inside the row (a switch, a track) are
 * left to that view, and mostly-vertical movement is handed back to the scrolling parent.
 * The action is also offered as a custom accessibility action, and from the keyboard: with
 * the row focused, the arrow pointing away from the reveal edge opens it, Enter or Space runs
 * the uncovered action, and the opposite arrow or Escape closes it. Rows come back closed
 * after a configuration change: [isOpen] is deliberately not saved.
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.interaction.MalletRevealLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="50dp"
 *     app:malletRevealActionLabel="@string/mallet_action_delete"
 *     app:malletRevealActionTone="danger"
 *     app:malletRevealEdge="start">
 *     <LinearLayout ... />
 * </io.github.avalonskyafar.mallet.interaction.MalletRevealLayout>
 * ```
 *
 * In code: `row.setAction(getString(R.string.mallet_action_delete)) { delete(item) }`.
 */
public class MalletRevealLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** The edge of the row at which the action strip is uncovered. */
    public enum class Edge { START, END }

    /**
     * Told when the user opens or closes the row, and when the user's opening of a sibling
     * closes this one (see [closeSiblings]). Never called for [open] and [close].
     */
    public fun interface OnOpenChangeListener {
        /** [open] is the state the row is settling to. */
        public fun onOpenChanged(layout: MalletRevealLayout, open: Boolean)
    }

    private val theme = MalletTheme.of(context)
    private val material = MalletMaterial(context, theme)
    private val density = resources.displayMetrics.density
    private val stripWidth = STRIP_WIDTH_DP * density
    private val gate = HorizontalDragGate(ViewConfiguration.get(context).scaledTouchSlop)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()

    private var actionClick: OnClickListener? = null
    private var ownActionId = View.NO_ID
    private var rowActionId = View.NO_ID
    private var rowActionTarget: View? = null

    /** Uncovered width in pixels, 0 (closed) to [stripWidth] (open). */
    private var offset = 0f
    private var targetOpen = false
    private var animator: ValueAnimator? = null

    // One gesture.
    private var downOffset = 0f
    private var dragStartX = 0f
    private var dragging = false
    private var downOnOwner = false
    private var downOnStrip = false
    private var stripPressed = false
    private var refused = false

    /**
     * The label of the action, or null while the row has no action. A row without an action
     * does not move.
     */
    public var actionLabel: CharSequence? = null
        private set

    /** The tone that colours the bed behind the row. */
    public var actionTone: MalletTextTone = MalletTextTone.DANGER
        private set

    /** The icon drawn above the label, or null for a label alone. */
    public var actionIcon: MalletIcon? = null
        private set

    /** The edge at which the action appears. START as in a mail list; mirrored in RTL. */
    public var revealEdge: Edge = Edge.START
        set(value) {
            if (field == value) return
            field = value
            applyOffset(offset)
            invalidate()
        }

    /** Whether opening this row closes the other open reveal rows of the same parent. */
    public var closeSiblings: Boolean = true

    private var openChangeListener: OnOpenChangeListener? = null

    /** Called when the user opens or closes the row by drag, tap, key or accessibility action. */
    public fun setOnOpenChangeListener(listener: OnOpenChangeListener?) {
        openChangeListener = listener
    }

    /** True while the row is open or settling open. */
    public val isOpen: Boolean get() = targetOpen

    /** The single child that slides, or null before it is added. */
    public val row: View? get() = if (childCount > 0) getChildAt(0) else null

    init {
        setWillNotDraw(false)
        useOwnFocusRim()
        clipChildren = true
        labelPaint.typeface = MalletWeight.MEDIUM.typeface()
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.color = Color.WHITE
        labelPaint.textSize = MalletTextRole.FOOTNOTE.sizeSp * resources.displayMetrics.scaledDensity * theme.textScale
        val a = context.obtainStyledAttributes(attrs, R.styleable.MalletRevealLayout, defStyleAttr, 0)
        revealEdge = if (a.getInt(R.styleable.MalletRevealLayout_malletRevealEdge, 0) == 1) Edge.END else Edge.START
        closeSiblings = a.getBoolean(R.styleable.MalletRevealLayout_malletRevealCloseSiblings, true)
        val tone = MalletTextTone.entries.getOrElse(
            a.getInt(R.styleable.MalletRevealLayout_malletRevealActionTone, MalletTextTone.DANGER.ordinal),
        ) { MalletTextTone.DANGER }
        val icon = MalletIcon.fromAttr(a.getInt(R.styleable.MalletRevealLayout_malletRevealActionIcon, -1))
        val label = a.getText(R.styleable.MalletRevealLayout_malletRevealActionLabel)
        a.recycle()
        if (label != null) setAction(label, tone, icon, null)
    }

    /**
     * Sets the one action behind the row.
     *
     * [label] is drawn white, centred in the strip, and names the accessibility action;
     * [tone] colours the bed; [icon] is drawn above the label when given. [onClick] runs when
     * the user taps the uncovered strip, presses Enter on the open row or picks the
     * accessibility action; it receives this layout. A held key runs it once, not once per
     * repeat. The row stays open afterwards: close it
     * or remove it in the callback.
     */
    @JvmOverloads
    public fun setAction(
        label: CharSequence,
        tone: MalletTextTone = MalletTextTone.DANGER,
        icon: MalletIcon? = null,
        onClick: OnClickListener?,
    ) {
        actionLabel = label
        actionTone = tone
        actionIcon = icon
        actionClick = onClick
        isFocusable = true
        syncAccessibility()
        invalidate()
    }

    /** Replaces the callback of the action; for rows whose action is declared in XML. */
    public fun setOnActionClickListener(onClick: OnClickListener?) {
        actionClick = onClick
    }

    /** Removes the action and closes the row at once. The row no longer moves. */
    public fun clearAction() {
        actionLabel = null
        actionIcon = null
        actionClick = null
        isFocusable = false
        moveTo(false, animate = false)
        syncAccessibility()
        invalidate()
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        check(childCount <= 1) { "MalletRevealLayout takes exactly one child, the foreground row" }
        if (child.background == null) child.background = RowFillDrawable(material)
        applyOffset(offset)
        syncAccessibility()
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        // The row leaves as it came: not shifted, and without the action of this layout.
        child.translationX = 0f
        if (child.background is RowFillDrawable) child.background = null
        if (child === rowActionTarget) {
            if (rowActionId != View.NO_ID) ViewCompat.removeAccessibilityAction(child, rowActionId)
            rowActionId = View.NO_ID
            rowActionTarget = null
        }
    }

    /** Opens the row, uncovering the action. Does nothing without an action. No callback. */
    @JvmOverloads
    public fun open(animate: Boolean = true) {
        if (actionLabel == null || !isEnabled) return
        moveTo(true, animate)
    }

    /** Closes the row. No callback. */
    @JvmOverloads
    public fun close(animate: Boolean = true) {
        moveTo(false, animate)
    }

    private val hasAction: Boolean get() = actionLabel != null && isEnabled

    /** True when the strip sits at the left edge, so that the row slides to the right. */
    private val stripOnLeft: Boolean
        get() = (revealEdge == Edge.START) != (layoutDirection == LAYOUT_DIRECTION_RTL)

    private fun moveTo(open: Boolean, animate: Boolean, byUser: Boolean = false) {
        targetOpen = open
        if (open && closeSiblings) closeOtherRows(byUser)
        val target = if (open) stripWidth else 0f
        animator?.cancel()
        animator = null
        syncState()
        if (!animate || offset == target || !isAttachedToWindow || !isLaidOut) {
            applyOffset(target)
            return
        }
        animator = ValueAnimator.ofFloat(offset, target).apply {
            duration = MalletMotion.feedbackDuration(theme.reduceMotion)
            interpolator = MalletMotion.glide()
            addUpdateListener { applyOffset(it.animatedValue as Float) }
            start()
        }
    }

    /** A change made by the user: listener first, haptic second. */
    private fun userMoveTo(open: Boolean) {
        val changed = targetOpen != open
        moveTo(open, animate = true, byUser = true)
        if (changed) {
            openChangeListener?.onOpenChanged(this, open)
            MalletHaptics.tick(this)
        }
    }

    /** A sibling that the user's gesture closes tells its own listener; one closed by code does not. */
    private fun closeOtherRows(byUser: Boolean) {
        val group = parent as? ViewGroup ?: return
        for (i in 0 until group.childCount) {
            val other = group.getChildAt(i)
            if (other === this || other !is MalletRevealLayout || !other.isOpen) continue
            other.close()
            if (byUser) other.openChangeListener?.onOpenChanged(other, false)
        }
    }

    private fun applyOffset(value: Float) {
        offset = value.coerceIn(0f, stripWidth)
        row?.translationX = if (stripOnLeft) offset else -offset
        invalidate()
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        applyOffset(offset)
    }

    // ---- drawing ----

    override fun draw(canvas: Canvas) {
        if (isEnabled) {
            super.draw(canvas)
            return
        }
        val save = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), MalletControl.DISABLED_ALPHA)
        super.draw(canvas)
        canvas.restoreToCount(save)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val label = actionLabel ?: return
        if (offset <= 0f) return
        // The bed lies where the row was laid out, which padding or a margin moves off the edges.
        val child = row
        val l = child?.left?.toFloat() ?: 0f
        val r = child?.right?.toFloat() ?: width.toFloat()
        val t = child?.top?.toFloat() ?: 0f
        val b = child?.bottom?.toFloat() ?: height.toFloat()
        val left = if (stripOnLeft) l else r - stripWidth
        val save = canvas.save()
        // Only the uncovered part: a row with a translucent background must not show the bed.
        if (stripOnLeft) canvas.clipRect(l, t, l + offset, b) else canvas.clipRect(r - offset, t, r, b)
        rect.set(left, t, left + stripWidth, b)
        material.drawRevealBed(canvas, rect, actionTone.resolve(theme.colors))

        val sink = if (stripPressed) MalletMotion.PRESS_SINK_DP * density else 0f
        val cx = left + stripWidth / 2f
        val metrics = labelPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val icon = actionIcon
        val iconSize = if (icon != null) ICON_DP * density else 0f
        val gap = if (icon != null) ICON_GAP_DP * density else 0f
        var top = t + (b - t - iconSize - gap - textHeight) / 2f + sink
        if (icon != null) {
            MalletIconPainter.draw(
                canvas, icon, cx - iconSize / 2f, top, iconSize, Color.WHITE,
                ICON_STROKE_DP * density, icon.isDirectional && layoutDirection == LAYOUT_DIRECTION_RTL,
            )
            top += iconSize + gap
        }
        val text = TextUtils.ellipsize(
            label, labelPaint, stripWidth - 2 * LABEL_INSET_DP * density, TextUtils.TruncateAt.END,
        )
        canvas.drawText(text, 0, text.length, cx, top - metrics.ascent, labelPaint)
        canvas.restoreToCount(save)
    }

    override fun dispatchDraw(canvas: Canvas) {
        // The row slides out under the edge of the layout, never over the card's padding or rim.
        val save = canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
        if (isFocused && hasWindowFocus() && !isInTouchMode) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            material.drawFocusRim(canvas, rect, 0f)
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        if (enabled == isEnabled) return
        super.setEnabled(enabled)
        if (!enabled) moveTo(false, animate = false)
        invalidate()
    }

    // ---- touch ----

    /**
     * True while the row can still move in [direction]: towards open while closed, towards
     * closed while open. Page hosts ask this to let the row move before they do.
     */
    override fun canScrollHorizontally(direction: Int): Boolean {
        if (!hasAction || direction == 0) return super.canScrollHorizontally(direction)
        return canMove(fingerMovesRight = direction < 0)
    }

    private fun canMove(fingerMovesRight: Boolean): Boolean =
        if (fingerMovesRight == stripOnLeft) offset < stripWidth else offset > 0f

    private fun inStrip(x: Float, y: Float): Boolean {
        if (offset <= 0f) return false
        val child = row
        val l = child?.left?.toFloat() ?: 0f
        val r = child?.right?.toFloat() ?: width.toFloat()
        val t = child?.top?.toFloat() ?: 0f
        val b = child?.bottom?.toFloat() ?: height.toFloat()
        if (y < t || y >= b) return false
        return if (stripOnLeft) x >= l && x < l + offset else x >= r - offset && x < r
    }

    private fun beginGesture(event: MotionEvent) {
        gate.down(event)
        dragging = false
        refused = false
        stripPressed = false
        downOnStrip = hasAction && inStrip(event.x, event.y)
        val child = row
        downOnOwner = !downOnStrip && child != null &&
            ownerUnder(child, event.x - child.left - child.translationX, event.y - child.top)
    }

    /** Feeds a move; returns true once the row follows the finger. */
    private fun decide(event: MotionEvent): Boolean {
        if (dragging) return true
        if (refused || downOnOwner) return false
        when (gate.move(this, event)) {
            HorizontalDragGate.State.UNDECIDED -> return false
            HorizontalDragGate.State.ABORTED -> refused = true
            HorizontalDragGate.State.DRAGGING -> {
                if (canMove(fingerMovesRight = gate.x(event) > gate.downX)) {
                    animator?.cancel()
                    animator = null
                    dragging = true
                    dragStartX = gate.x(event)
                    downOffset = offset
                } else {
                    // Nothing to uncover in this direction: the page behind may have it.
                    refused = true
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
        if (stripPressed) {
            stripPressed = false
            invalidate()
        }
        return dragging
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!hasAction) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginGesture(event)
                // A touch on the open row belongs to the layout: a tap closes it.
                targetOpen && !downOnStrip && !downOnOwner
            }
            MotionEvent.ACTION_MOVE -> decide(event)
            else -> {
                // The followed finger left while a view in the row has the gesture: nothing
                // that another finger does afterwards moves the row.
                if (gate.isRelease(event)) refused = true
                false
            }
        }
    }

    // Lint: the row's own click is forwarded; the action is offered as an accessibility action.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!hasAction) return super.onTouchEvent(event)
        val action = event.actionMasked
        when {
            action == MotionEvent.ACTION_DOWN -> {
                // The gesture was set up in onInterceptTouchEvent, which always sees the down.
                if (downOnStrip) {
                    stripPressed = true
                    invalidate()
                }
            }
            action == MotionEvent.ACTION_MOVE -> if (decide(event)) {
                val dx = gate.x(event) - dragStartX
                applyOffset(downOffset + if (stripOnLeft) dx else -dx)
            }
            // The followed finger lifts: the gesture ends here, whatever other fingers still do.
            gate.isRelease(event) -> {
                val x = gate.x(event)
                val y = gate.y(event)
                gate.finish(this)
                val wasPressed = stripPressed
                stripPressed = false
                when {
                    dragging -> userMoveTo(offset >= stripWidth * OPEN_FRACTION)
                    refused -> settleBack()
                    wasPressed && inStrip(x, y) -> performAction()
                    targetOpen -> userMoveTo(false)
                    else -> performClick()
                }
                dragging = false
                invalidate()
            }
            action == MotionEvent.ACTION_CANCEL -> {
                gate.finish(this)
                stripPressed = false
                if (dragging) userMoveTo(false) else settleBack()
                dragging = false
                invalidate()
            }
            else -> return false
        }
        return true
    }

    /** Returns a row that was left between its ends to the state it had. */
    private fun settleBack() {
        if (animator == null && offset != (if (targetOpen) stripWidth else 0f)) moveTo(targetOpen, animate = true)
    }

    private fun performAction() {
        if (!hasAction) return
        // An action: tick first, then run it.
        MalletHaptics.tick(this)
        actionClick?.onClick(this)
    }

    private fun ownerUnder(view: View, x: Float, y: Float): Boolean {
        if (view is MalletHorizontalDragOwner) return view.isEnabled
        if (view !is ViewGroup) return false
        val px = x + view.scrollX
        val py = y + view.scrollY
        for (i in view.childCount - 1 downTo 0) {
            val child = view.getChildAt(i)
            if (child.visibility != VISIBLE) continue
            if (px >= child.left && px < child.right && py >= child.top && py < child.bottom &&
                ownerUnder(child, px - child.left, py - child.top)
            ) {
                return true
            }
        }
        return false
    }

    // ---- keyboard and accessibility ----

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleKey(keyCode, event) || super.onKeyDown(keyCode, event)

    /** Keys that a focused view inside the row leaves alone reach the row as well. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (super.dispatchKeyEvent(event)) return true
        return event.action == KeyEvent.ACTION_DOWN && !isFocused && hasFocus() && handleKey(event.keyCode, event)
    }

    private fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        if (!hasAction) return false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val towardsOpen = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) == stripOnLeft
                if (towardsOpen != targetOpen) {
                    userMoveTo(towardsOpen)
                    return true
                }
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_DPAD_CENTER -> if (targetOpen) {
                // A held key repeats: the action runs once, the repeats are swallowed.
                if (event.repeatCount == 0) performAction()
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> if (targetOpen) {
                userMoveTo(false)
                return true
            }
        }
        return false
    }

    private fun syncAccessibility() {
        if (ownActionId != View.NO_ID) ViewCompat.removeAccessibilityAction(this, ownActionId)
        rowActionTarget?.let { if (rowActionId != View.NO_ID) ViewCompat.removeAccessibilityAction(it, rowActionId) }
        ownActionId = View.NO_ID
        rowActionId = View.NO_ID
        rowActionTarget = null
        val label = actionLabel
        if (label != null) {
            // On the layout and on the row, whichever of the two a screen reader lands on.
            ownActionId = ViewCompat.addAccessibilityAction(this, label) { _, _ -> runAccessibilityAction() }
            row?.let { child ->
                rowActionId = ViewCompat.addAccessibilityAction(child, label) { _, _ -> runAccessibilityAction() }
                rowActionTarget = child
            }
        }
        syncState()
    }

    private fun runAccessibilityAction(): Boolean {
        if (!hasAction) return false
        performAction()
        return true
    }

    private fun syncState() {
        val state = when {
            actionLabel == null -> null
            targetOpen -> resources.getString(R.string.mallet_reveal_state_open, actionLabel)
            else -> null
        }
        ViewCompat.setStateDescription(this, state)
    }

    override fun getAccessibilityClassName(): CharSequence = MalletRevealLayout::class.java.name

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        applyOffset(if (targetOpen) stripWidth else 0f)
        super.onDetachedFromWindow()
    }

    public companion object {
        /** Width of the action strip, and the farthest the row travels. */
        public const val STRIP_WIDTH_DP: Float = 78f

        /** Share of the strip that must be uncovered on release for the row to open. */
        public const val OPEN_FRACTION: Float = 0.42f

        private const val LABEL_INSET_DP = 6f
        private const val ICON_DP = 20f
        private const val ICON_GAP_DP = 2f
        private const val ICON_STROKE_DP = 1.8f
    }
}

/** The flat fill given to a row that brings no background, so that it hides the bed. */
private class RowFillDrawable(private val material: MalletMaterial) : Drawable() {
    private val body = RectF()

    override fun draw(canvas: Canvas) {
        body.set(bounds)
        material.drawRowFill(canvas, body)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
