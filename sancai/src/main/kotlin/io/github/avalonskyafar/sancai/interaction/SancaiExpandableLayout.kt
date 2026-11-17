package io.github.avalonskyafar.sancai.interaction

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiControl
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A section that expands and collapses. The first child is the header and is always
 * visible; all further children form the body, which opens below it.
 *
 * Expanding grows the height of the body from zero while the body stays anchored at its
 * top edge and is clipped at the bottom, as if a cover slid away. Nothing fades or scales.
 * Collapsing is the reverse, and a change of mind in mid-flight turns around from the
 * current height. While collapsed, the body children are `GONE`, so they take no part in
 * layout, focus or accessibility; their own visibility comes back when the section opens.
 * Change the visibility of a body child only while the section is expanded.
 *
 * With [toggleOnHeaderClick] the header is pressable (it sinks under the finger) and
 * toggles the section; Enter and Space do the same when it has focus. The header also
 * carries the expanded / collapsed state description and the expand / collapse
 * accessibility actions. A view passed to [setIndicator], typically a chevron at the end
 * of the header, turns half a circle with the progress.
 *
 * A disabled section keeps its current state: the header pales and no longer toggles,
 * the body stays as it is.
 *
 * The layout is always vertical and top-aligned. Give it an id to have its state saved.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.interaction.SancaiExpandableLayout
 *     android:id="@+id/details"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiExpandableExpanded="false"
 *     app:sancaiExpandableIndicator="@+id/chevron">
 *
 *     <FrameLayout android:layout_width="match_parent" android:layout_height="56dp">
 *         <!-- centred title, and @id/chevron at the end -->
 *     </FrameLayout>
 *
 *     <TextView android:layout_width="match_parent" android:layout_height="wrap_content" />
 * </io.github.avalonskyafar.sancai.interaction.SancaiExpandableLayout>
 * ```
 */
public class SancaiExpandableLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Told when the user expands or collapses the section. */
    public fun interface OnExpansionChangeListener {
        /** [expanded] is the state the user just chose; the body may still be moving. */
        public fun onExpansionChanged(layout: SancaiExpandableLayout, expanded: Boolean)
    }

    /** Told about every step of the opening, so that other views can move with it. */
    public fun interface OnExpansionProgressListener {
        /** [fraction] runs from 0 (collapsed) to 1 (fully expanded). */
        public fun onExpansionProgress(layout: SancaiExpandableLayout, fraction: Float)
    }

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val material: SancaiMaterial = SancaiMaterial(context, theme)
    private val focusBounds = RectF()
    private var ready = false
    private var bodyExtent = 0

    private var expanded = true
    private var fraction = 1f
    private var animator: ValueAnimator? = null

    /** Visibility of the body children that were hidden by collapsing. */
    private val hiddenBody = HashMap<View, Int>()
    private var boundHeader: View? = null
    private var pressInstalled = false
    private var keyHeld = false
    private var indicatorView: View? = null
    private var indicatorId = View.NO_ID

    private val focusWatcher = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ -> invalidate() }

    private var expansionChangeListener: OnExpansionChangeListener? = null

    /**
     * Called after the user toggled the section through the header, the keyboard or an
     * accessibility action. [setExpanded], [toggle] and state restoration never call it.
     */
    public fun setOnExpansionChangeListener(listener: OnExpansionChangeListener?) {
        expansionChangeListener = listener
    }

    private var expansionProgressListener: OnExpansionProgressListener? = null

    /**
     * Called whenever the open fraction changes, whoever caused it, including each frame
     * of the animation.
     */
    public fun setOnExpansionProgressListener(listener: OnExpansionProgressListener?) {
        expansionProgressListener = listener
    }

    /** Whether the section is expanded, or on its way there. Setting it jumps without animation. */
    public var isExpanded: Boolean
        get() = expanded
        set(value) = setExpanded(value, false)

    /** How far the body is open right now: 0 collapsed, 1 fully expanded. */
    public val expansionFraction: Float get() = fraction

    /** Whether the body is on its way to the state given by [isExpanded]. */
    public val isSettling: Boolean get() = animator != null

    /** The first child, or null while the layout is empty. */
    public val header: View? get() = getChildAt(0)

    /**
     * Whether a click on the header toggles the section. Turning this on makes the header
     * clickable and focusable and replaces its touch and click listeners; turning it off
     * removes them again. Default: true.
     */
    public var toggleOnHeaderClick: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            syncHeader()
        }

    /** State description of the header while expanded. Null uses the library text. */
    public var expandedStateDescription: CharSequence? = null
        set(value) {
            field = value
            syncAccessibility()
        }

    /** State description of the header while collapsed. Null uses the library text. */
    public var collapsedStateDescription: CharSequence? = null
        set(value) {
            field = value
            syncAccessibility()
        }

    /**
     * Corner radius, in dp, of the rim drawn inside the header while it has keyboard
     * focus. Default: the card radius, so the rim also fits a header at the top of a card.
     */
    public var headerFocusRadiusDp: Float = SancaiMaterial.RADIUS_CARD_DP
        set(value) {
            field = value
            invalidate()
        }

    init {
        super.setOrientation(VERTICAL)
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiExpandableLayout, defStyleAttr, 0)
            try {
                expanded = a.getBoolean(R.styleable.SancaiExpandableLayout_sancaiExpandableExpanded, true)
                fraction = if (expanded) 1f else 0f
                toggleOnHeaderClick =
                    a.getBoolean(R.styleable.SancaiExpandableLayout_sancaiExpandableToggleOnHeaderClick, true)
                indicatorId = a.getResourceId(R.styleable.SancaiExpandableLayout_sancaiExpandableIndicator, NO_ID)
            } finally {
                a.recycle()
            }
        }
        ready = true
    }

    /** The layout is always vertical; other orientations are ignored. */
    override fun setOrientation(orientation: Int) {
        super.setOrientation(VERTICAL)
    }

    /** The body is anchored under the header, so the vertical part of [gravity] is always top. */
    override fun setGravity(gravity: Int) {
        super.setGravity((gravity and Gravity.VERTICAL_GRAVITY_MASK.inv()) or Gravity.TOP)
    }

    /** The layout is always top-aligned; other vertical gravities are ignored. */
    override fun setVerticalGravity(verticalGravity: Int) {
        super.setVerticalGravity(Gravity.TOP)
    }

    /** A touch on the part of the body that is still covered goes to nobody. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val first = getChildAt(0)
        if (event.actionMasked == MotionEvent.ACTION_DOWN && fraction < 1f && first != null) {
            val cover = first.bottom + (first.layoutParams as LayoutParams).bottomMargin + visibleBodyExtent()
            if (event.y >= cover) return false
        }
        return super.dispatchTouchEvent(event)
    }

    /**
     * Expands or collapses the section without calling [setOnExpansionChangeListener].
     *
     * With [animate] the body glides from its current height, also when it is already
     * moving the other way. Without it, or while the layout is not attached and laid out,
     * the body jumps.
     */
    public fun setExpanded(expanded: Boolean, animate: Boolean) {
        val target = if (expanded) 1f else 0f
        if (this.expanded == expanded) {
            if (!animate && animator != null) {
                cancelAnimator()
                applyFraction(target)
            }
            return
        }
        this.expanded = expanded
        cancelAnimator()
        syncAccessibility()
        if (animate && isLaidOut && isAttachedToWindow && fraction != target) {
            glideTo(target)
        } else {
            applyFraction(target)
        }
    }

    /** Switches between expanded and collapsed with the animation, like [setExpanded]. */
    public fun toggle() {
        setExpanded(!expanded, true)
    }

    /**
     * Names the view that shows the direction of the section, usually a chevron inside
     * the header. It is rotated from 0 degrees (collapsed) to 180 degrees (expanded) with
     * the progress; in a right-to-left layout it turns the other way round. Pass null to
     * release the current indicator, which is put back upright.
     */
    public fun setIndicator(view: View?) {
        if (indicatorView === view) return
        indicatorView?.rotation = 0f
        indicatorView = view
        indicatorId = NO_ID
        syncIndicator()
    }

    /** The view set with [setIndicator] or found through the XML attribute, if any. */
    public fun getIndicator(): View? = indicatorView

    private fun glideTo(target: Float) {
        val from = fraction
        val duration = SancaiMotion.settleDuration(abs(target - from), theme.reduceMotion)
            .coerceAtMost(SancaiMotion.PAGE_MS)
        val running = ValueAnimator.ofFloat(from, target)
        running.duration = duration
        running.interpolator = SancaiMotion.glide()
        running.addUpdateListener { applyFraction(it.animatedValue as Float) }
        running.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (cancelled || animator !== animation) return
                animator = null
                applyFraction(target)
            }
        })
        animator = running
        running.start()
    }

    private fun cancelAnimator() {
        val running = animator ?: return
        animator = null
        running.cancel()
    }

    private fun applyFraction(value: Float) {
        val bounded = value.coerceIn(0f, 1f)
        val changed = bounded != fraction
        fraction = bounded
        syncBody()
        syncIndicator()
        if (changed) {
            requestLayout()
            invalidate()
            expansionProgressListener?.onExpansionProgress(this, bounded)
        }
    }

    /** Hides the body while fully collapsed and gives it back its own visibility otherwise. */
    private fun syncBody() {
        val first = getChildAt(0)
        if (first != null) restore(first)
        if (fraction > 0f) {
            if (hiddenBody.isEmpty()) return
            val entries = ArrayList(hiddenBody.entries)
            hiddenBody.clear()
            for (entry in entries) entry.key.visibility = entry.value
            return
        }
        for (index in 1 until childCount) {
            val child = getChildAt(index)
            if (hiddenBody.containsKey(child)) continue
            hiddenBody[child] = child.visibility
            child.visibility = GONE
        }
    }

    private fun restore(child: View) {
        val visibility = hiddenBody.remove(child) ?: return
        child.visibility = visibility
    }

    private fun syncIndicator() {
        if (indicatorView == null && indicatorId != NO_ID) indicatorView = findViewById(indicatorId)
        val turn = if (layoutDirection == LAYOUT_DIRECTION_RTL) -INDICATOR_TURN else INDICATOR_TURN
        indicatorView?.rotation = turn * fraction
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        if (ready) syncIndicator()
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (!ready) return
        syncBody()
        syncHeader()
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (!ready) return
        restore(child)
        if (indicatorView?.let { isInside(it, child) } == true) setIndicator(null)
        syncBody()
        syncHeader()
    }

    private fun isInside(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        syncIndicator()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var headerExtent = 0
        var body = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            val params = child.layoutParams as LayoutParams
            val extent = child.measuredHeight + params.topMargin + params.bottomMargin
            if (index == 0) headerExtent = extent else body += extent
        }
        bodyExtent = body
        if (fraction >= 1f || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) return
        val open = paddingTop + headerExtent + visibleBodyExtent() + paddingBottom
        val height = open.coerceAtLeast(suggestedMinimumHeight).coerceAtMost(measuredHeight)
        setMeasuredDimension(measuredWidthAndState, height)
    }

    private fun visibleBodyExtent(): Int = (bodyExtent * fraction).roundToInt()

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val first = getChildAt(0)
        if (child === first) {
            if (isEnabled || !toggleOnHeaderClick) return super.drawChild(canvas, child, drawingTime)
            // Disabled pales: the header is drawn at the alpha every disabled Sancai control uses.
            val save = canvas.saveLayerAlpha(
                child.left.toFloat(), child.top.toFloat(), child.right.toFloat(), child.bottom.toFloat(),
                SancaiControl.DISABLED_ALPHA,
            )
            val more = super.drawChild(canvas, child, drawingTime)
            canvas.restoreToCount(save)
            return more
        }
        if (fraction >= 1f || first == null) return super.drawChild(canvas, child, drawingTime)
        // The body keeps its place and its size; only the cover below the header moves.
        val top = first.bottom + (first.layoutParams as LayoutParams).bottomMargin
        val save = canvas.save()
        canvas.clipRect(0, top, width, top + visibleBodyExtent())
        val more = super.drawChild(canvas, child, drawingTime)
        canvas.restoreToCount(save)
        return more
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val first = getChildAt(0) ?: return
        if (!toggleOnHeaderClick || !first.isFocused || !hasWindowFocus() || isInTouchMode) return
        focusBounds.set(first.left.toFloat(), first.top.toFloat(), first.right.toFloat(), first.bottom.toFloat())
        focusBounds.offset(first.translationX, first.translationY)
        material.drawFocusRim(canvas, focusBounds, material.dp(headerFocusRadiusDp))
    }

    /** Moves the press, the state description and the actions to the current first child. */
    @SuppressLint("ClickableViewAccessibility")
    private fun syncHeader() {
        val first = getChildAt(0)
        val previous = boundHeader
        if (previous != null && previous !== first) {
            if (pressInstalled) removePress(previous)
            ViewCompat.setStateDescription(previous, null)
            ViewCompat.removeAccessibilityAction(previous, AccessibilityActionCompat.ACTION_EXPAND.id)
            ViewCompat.removeAccessibilityAction(previous, AccessibilityActionCompat.ACTION_COLLAPSE.id)
        }
        boundHeader = first
        if (first == null) {
            pressInstalled = false
            return
        }
        if (toggleOnHeaderClick && !pressInstalled) {
            val sink = resources.displayMetrics.density * SancaiMotion.PRESS_SINK_DP
            first.isClickable = true
            first.isFocusable = true
            first.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> if (view.isEnabled) SancaiPress.sink(view, sink)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> SancaiPress.release(view)
                }
                false
            }
            first.setOnClickListener { toggleByUser() }
            pressInstalled = true
        } else if (!toggleOnHeaderClick && pressInstalled) {
            removePress(first)
        }
        if (toggleOnHeaderClick) first.isEnabled = isEnabled
        syncAccessibility()
        invalidate()
    }

    private fun removePress(view: View) {
        SancaiPress.release(view)
        view.setOnTouchListener(null)
        view.setOnClickListener(null)
        view.isClickable = false
        view.isFocusable = false
        view.isEnabled = true
        pressInstalled = false
    }

    private fun syncAccessibility() {
        val first = boundHeader ?: return
        val state = if (expanded) {
            expandedStateDescription ?: resources.getString(R.string.sancai_expandable_state_expanded)
        } else {
            collapsedStateDescription ?: resources.getString(R.string.sancai_expandable_state_collapsed)
        }
        ViewCompat.setStateDescription(first, state)
        val expand = AccessibilityActionCompat.ACTION_EXPAND
        val collapse = AccessibilityActionCompat.ACTION_COLLAPSE
        val offered = if (expanded) collapse else expand
        val withdrawn = if (expanded) expand else collapse
        ViewCompat.removeAccessibilityAction(first, withdrawn.id)
        if (isEnabled) {
            ViewCompat.replaceAccessibilityAction(first, offered, null) { _, _ -> toggleByUser() }
        } else {
            ViewCompat.removeAccessibilityAction(first, offered.id)
        }
    }

    /**
     * Enter, Space and the D-pad centre toggle while the header has the focus: the header
     * sinks on the way down and the section toggles on the way up. Other keys pass.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val first = boundHeader
        if (first == null || !toggleOnHeaderClick || !isEnabled || !first.isFocused || !isToggleKey(event)) {
            return super.dispatchKeyEvent(event)
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                keyHeld = true
                SancaiPress.sink(first, resources.displayMetrics.density * SancaiMotion.PRESS_SINK_DP)
            }
            KeyEvent.ACTION_UP -> {
                SancaiPress.release(first)
                if (keyHeld && !event.isCanceled) toggleByUser()
                keyHeld = false
            }
        }
        return true
    }

    private fun isToggleKey(event: KeyEvent): Boolean = event.hasNoModifiers() && when (event.keyCode) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER -> true
        else -> false
    }

    /** The one entry for touch, keyboard and accessibility: listener first, haptic second. */
    private fun toggleByUser(): Boolean {
        if (!isEnabled) return false
        setExpanded(!expanded, true)
        expansionChangeListener?.onExpansionChanged(this, expanded)
        SancaiHaptics.tick(this)
        return true
    }

    override fun setEnabled(enabled: Boolean) {
        val changed = enabled != isEnabled
        super.setEnabled(enabled)
        if (!ready || !changed) return
        syncHeader()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(focusWatcher)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(focusWatcher)
        if (animator != null) {
            cancelAnimator()
            applyFraction(if (expanded) 1f else 0f)
        }
        boundHeader?.let { if (pressInstalled) SancaiPress.release(it) }
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        invalidate()
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = Bundle()
        state.putParcelable(STATE_SUPER, super.onSaveInstanceState())
        state.putBoolean(STATE_EXPANDED, expanded)
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        @Suppress("DEPRECATION")
        super.onRestoreInstanceState(state.getParcelable(STATE_SUPER))
        setExpanded(state.getBoolean(STATE_EXPANDED, expanded), false)
    }

    override fun getAccessibilityClassName(): CharSequence = SancaiExpandableLayout::class.java.name

    private companion object {
        const val INDICATOR_TURN = 180f
        const val STATE_SUPER = "sancai:expandable:super"
        const val STATE_EXPANDED = "sancai:expandable:expanded"
    }
}
