package io.github.avalonskyafar.sancai.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconPainter
import io.github.avalonskyafar.sancai.interaction.SancaiPress
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The bar at the top of a page: a centred title, a navigation button at the start and up to
 * two action icons at the end.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiTopBar
 *     android:id="@+id/top_bar"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiTitle="Blocked numbers"
 *     app:sancaiTopBarSubtitle="12 entries"
 *     app:sancaiIcon="chevron_left" />
 * ```
 *
 * ```kotlin
 * topBar.setNavigation { finish() }
 * topBar.addAction(SancaiIcon.SEARCH, getString(R.string.search)) { openSearch() }
 * topBar.bindTo(scrollView)
 * ```
 *
 * The bar is as quiet as the page under it: it has the canvas colour and no shadow. Only when
 * it is [lifted], which [bindTo] switches on once the content has scrolled, a full-bleed
 * hairline separates it from what moves underneath. The canvas colour is only the default
 * background: a bar that stands on something else, a card for instance, keeps the background
 * you set on it (`android:background`, `setBackgroundColor`).
 *
 * The title is centred in the bar and stays there whatever buttons are present: the room it
 * may use is the same on both sides, so a lone back button never pushes it off centre. A
 * title that does not fit is shortened with an ellipsis.
 *
 * The bar is 56 dp high and grows only when large text needs more. It adds the status-bar
 * and display-cutout insets to its own height and sides ([fitStatusBar]), so it can sit at the
 * very top of an edge-to-edge window; the insets are passed on unconsumed.
 *
 * Buttons are 24 dp icons in 48 dp targets. Pressing sinks the icon, a click ticks and then
 * calls the listener. Every button needs a content description; directional icons are
 * mirrored in right-to-left layouts.
 */
public class SancaiTopBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val material: SancaiMaterial = SancaiMaterial(context, theme)
    private val density: Float = resources.displayMetrics.density
    private val linePaint: Paint = Paint()

    private val titleView: SancaiText = label(SancaiTextRole.HEADLINE, SancaiTextTone.INK)
    private val subtitleView: SancaiText = label(SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
    private var navigation: BarButton? = null
    private val actions: ArrayList<BarButton> = ArrayList(MAX_ACTIONS)

    private var insetTop = 0
    private var insetLeft = 0
    private var insetRight = 0

    private var boundView: View? = null
    private val scrollWatcher = View.OnScrollChangeListener { view, _, _, _, _ -> syncLifted(view) }
    private val layoutWatcher =
        View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> syncLifted(view) }

    /** The title, centred in the bar on one line. `null` or empty shows none. */
    public var title: CharSequence?
        get() = titleView.text.takeIf { it.isNotEmpty() }
        set(value) {
            titleView.text = value
            titleView.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
        }

    /** A quiet second line under the title. `null` or empty shows none. */
    public var subtitle: CharSequence?
        get() = subtitleView.text.takeIf { it.isNotEmpty() }
        set(value) {
            subtitleView.text = value
            subtitleView.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
        }

    /**
     * Whether content lies under the bar. A lifted bar draws a full-bleed hairline along its
     * bottom edge; nothing else changes. [bindTo] sets this for you.
     */
    public var lifted: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * Whether the bar pads itself for the status bar and display cutout (default true).
     * Switch it off inside a host that already handles those insets.
     */
    public var fitStatusBar: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /** The navigation button, or `null` when the bar has none. */
    public val navigationButton: View? get() = navigation

    /** Number of action icons at the end of the bar (0 to 2). */
    public val actionCount: Int get() = actions.size

    init {
        setWillNotDraw(false)
        // Opaque, so content that scrolls underneath does not show through.
        if (background == null) setBackgroundColor(theme.colors.canvas)
        ViewCompat.setAccessibilityHeading(titleView, true)
        titleView.visibility = GONE
        subtitleView.visibility = GONE
        addView(titleView)
        addView(subtitleView)
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiTopBar, defStyleAttr, 0)
            try {
                title = a.getText(R.styleable.SancaiTopBar_sancaiTitle)
                subtitle = a.getText(R.styleable.SancaiTopBar_sancaiTopBarSubtitle)
                lifted = a.getBoolean(R.styleable.SancaiTopBar_sancaiTopBarLifted, false)
                fitStatusBar = a.getBoolean(R.styleable.SancaiTopBar_sancaiTopBarFitStatusBar, true)
                val icon = SancaiIcon.fromAttr(a.getInt(R.styleable.SancaiTopBar_sancaiIcon, -1))
                if (icon != null) {
                    setNavigation(
                        icon,
                        a.getText(R.styleable.SancaiTopBar_sancaiTopBarNavigationDescription),
                        null,
                    )
                }
            } finally {
                a.recycle()
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            if (bars.top != insetTop || bars.left != insetLeft || bars.right != insetRight) {
                insetTop = bars.top
                insetLeft = bars.left
                insetRight = bars.right
                requestLayout()
            }
            insets
        }
    }

    /**
     * Shows the navigation button at the start of the bar, drawn in ink.
     *
     * @param icon the glyph; the default chevron points back and is mirrored in
     *   right-to-left layouts, as every directional icon is
     * @param description what a screen reader says; `null` uses the library's "Back"
     * @param onClick called after the tick when the button is operated; may be set later
     *   through [navigationButton]
     */
    @JvmOverloads
    public fun setNavigation(
        icon: SancaiIcon = SancaiIcon.CHEVRON_LEFT,
        description: CharSequence? = null,
        onClick: OnClickListener?,
    ) {
        val button = navigation ?: BarButton(context).also {
            navigation = it
            addView(it, 0)
        }
        button.bind(icon, theme.colors.ink, NAVIGATION_STROKE_DP)
        button.contentDescription = description ?: context.getString(R.string.sancai_action_back)
        button.isEnabled = isEnabled
        SancaiPress.attach(button, button, onClick)
    }

    /** Removes the navigation button. The title does not move. */
    public fun clearNavigation() {
        val button = navigation ?: return
        navigation = null
        removeView(button)
    }

    /**
     * Adds an action icon at the end of the bar, drawn muted, and returns its view (to
     * disable it, or to anchor a menu to it). Actions line up in the order they are added,
     * the first one nearest the title.
     *
     * A bar holds at most two actions; put anything further behind an overflow action.
     *
     * @param description what a screen reader says; required, an icon alone says nothing
     * @throws IllegalStateException when the bar already has two actions
     */
    public fun addAction(icon: SancaiIcon, description: CharSequence, onClick: OnClickListener?): View {
        check(actions.size < MAX_ACTIONS) { "SancaiTopBar holds at most $MAX_ACTIONS actions" }
        val button = BarButton(context)
        button.bind(icon, theme.colors.muted, ACTION_STROKE_DP)
        button.contentDescription = description
        button.isEnabled = isEnabled
        SancaiPress.attach(button, button, onClick)
        actions.add(button)
        addView(button)
        return button
    }

    /** The view of the action at [index], in the order the actions were added. */
    public fun getActionAt(index: Int): View = actions[index]

    /** Removes every action icon. */
    public fun clearActions() {
        for (button in actions) removeView(button)
        actions.clear()
    }

    /**
     * Lets the bar follow a scrolling view: [lifted] is true while [scrollView] can scroll
     * back up, that is, once content has moved under the bar. Works with anything that
     * scrolls vertically (`ScrollView`, `NestedScrollView`, `RecyclerView`, `ListView`).
     *
     * This occupies the view's single `OnScrollChangeListener` slot. If you need that
     * listener yourself, set [lifted] from it instead of binding. Pass `null` to unbind.
     * The bar lets go of the view while it is detached from its window and follows it again
     * when it comes back.
     */
    public fun bindTo(scrollView: View?) {
        boundView?.let(::unwatch)
        boundView = scrollView
        if (scrollView != null) watch(scrollView)
    }

    private fun watch(view: View) {
        view.setOnScrollChangeListener(scrollWatcher)
        view.removeOnLayoutChangeListener(layoutWatcher)
        view.addOnLayoutChangeListener(layoutWatcher)
        syncLifted(view)
    }

    private fun unwatch(view: View) {
        view.setOnScrollChangeListener(null)
        view.removeOnLayoutChangeListener(layoutWatcher)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        boundView?.let(::watch)
    }

    private fun syncLifted(view: View) {
        lifted = view.canScrollVertically(-1)
    }

    /** Disabling the bar disables and pales its buttons; the title stays as it is. */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        navigation?.isEnabled = enabled
        for (button in actions) button.isEnabled = enabled
    }

    private fun label(role: SancaiTextRole, tone: SancaiTextTone): SancaiText =
        SancaiText.create(context, null, role, tone).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textAlignment = TEXT_ALIGNMENT_CENTER
        }

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private val fitTop: Int get() = paddingTop + if (fitStatusBar) insetTop else 0
    private val fitLeft: Int get() = paddingLeft + if (fitStatusBar) insetLeft else 0
    private val fitRight: Int get() = paddingRight + if (fitStatusBar) insetRight else 0

    /** Room one side keeps free of the title: the wider of the two button groups. */
    private fun sideRoom(): Int {
        val target = px(TARGET_DP)
        val start = if (navigation != null) px(EDGE_DP) + target else px(TITLE_MARGIN_DP)
        val end = if (actions.isEmpty()) px(TITLE_MARGIN_DP) else px(EDGE_DP) + actions.size * target
        return max(start, end)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
        val buttonSpec = MeasureSpec.makeMeasureSpec(px(TARGET_DP), MeasureSpec.EXACTLY)
        navigation?.measure(buttonSpec, buttonSpec)
        for (button in actions) button.measure(buttonSpec, buttonSpec)

        val room = max(0, width - fitLeft - fitRight - 2 * sideRoom())
        val textWidth = MeasureSpec.makeMeasureSpec(room, MeasureSpec.AT_MOST)
        val textHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var text = 0
        if (titleView.visibility != GONE) {
            titleView.measure(textWidth, textHeight)
            text += titleView.measuredHeight
        }
        if (subtitleView.visibility != GONE) {
            subtitleView.measure(textWidth, textHeight)
            text += subtitleView.measuredHeight + if (text > 0) px(LINE_GAP_DP) else 0
        }
        val bar = max(px(HEIGHT_DP), text + 2 * px(TEXT_MARGIN_DP))
        setMeasuredDimension(width, resolveSize(fitTop + bar + paddingBottom, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val left = fitLeft
        val right = width - fitRight
        val centerX = (left + right) / 2
        val centerY = (fitTop + height - paddingBottom) / 2
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val target = px(TARGET_DP)
        val edge = px(EDGE_DP)
        val buttonTop = centerY - target / 2

        navigation?.let {
            val x = if (rtl) right - edge - target else left + edge
            it.layout(x, buttonTop, x + target, buttonTop + target)
        }
        val count = actions.size
        for (i in 0 until count) {
            val fromEdge = edge + (count - 1 - i) * target
            val x = if (rtl) left + fromEdge else right - fromEdge - target
            actions[i].layout(x, buttonTop, x + target, buttonTop + target)
        }

        val hasTitle = titleView.visibility != GONE
        val hasSubtitle = subtitleView.visibility != GONE
        val gap = if (hasTitle && hasSubtitle) px(LINE_GAP_DP) else 0
        val block = (if (hasTitle) titleView.measuredHeight else 0) + gap +
            (if (hasSubtitle) subtitleView.measuredHeight else 0)
        var y = centerY - block / 2
        if (hasTitle) {
            centre(titleView, centerX, y)
            y += titleView.measuredHeight + gap
        }
        if (hasSubtitle) centre(subtitleView, centerX, y)
    }

    private fun centre(view: View, centerX: Int, top: Int) {
        val x = centerX - view.measuredWidth / 2
        view.layout(x, top, x + view.measuredWidth, top + view.measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!lifted) return
        val thickness = max(1, density.roundToInt())
        linePaint.color = theme.colors.divider
        canvas.drawRect(0f, (height - thickness).toFloat(), width.toFloat(), height.toFloat(), linePaint)
    }

    override fun onDetachedFromWindow() {
        navigation?.let { SancaiPress.release(it) }
        for (button in actions) SancaiPress.release(button)
        // A bar that has left the window must not be kept alive by the list it followed.
        boundView?.let(::unwatch)
        super.onDetachedFromWindow()
    }

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        putBoolean(STATE_LIFTED, lifted)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        @Suppress("DEPRECATION")
        super.onRestoreInstanceState(state.getParcelable(STATE_SUPER))
        // A bound scroll view knows better than a saved flag.
        val bound = boundView
        if (bound != null) syncLifted(bound) else lifted = state.getBoolean(STATE_LIFTED, lifted)
    }

    /**
     * The bar is opaque to touches as it is to the eye: a tap between its buttons never
     * reaches content that has scrolled underneath.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = true

    /** One icon in a 48 dp target. */
    @SuppressLint("ViewConstructor")
    private inner class BarButton(context: Context) : View(context) {
        private var icon: SancaiIcon? = null
        private var color = 0
        private var strokeDp = SancaiIconPainter.STROKE_DP
        private val rim = RectF()

        fun bind(icon: SancaiIcon, color: Int, strokeDp: Float) {
            this.icon = icon
            this.color = color
            this.strokeDp = strokeDp
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val drawn = icon ?: return
            val size = ICON_DP * density
            val save = if (isEnabled) {
                canvas.save()
            } else {
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), SancaiControl.DISABLED_ALPHA)
            }
            SancaiIconPainter.draw(
                canvas, drawn, (width - size) / 2f, (height - size) / 2f, size, color, strokeDp * density,
                drawn.isDirectional && layoutDirection == LAYOUT_DIRECTION_RTL,
            )
            canvas.restoreToCount(save)
            if (isFocused && hasWindowFocus() && !isInTouchMode) {
                val inset = FOCUS_INSET_DP * density
                rim.set(inset, inset, width - inset, height - inset)
                material.drawFocusRim(canvas, rim, rim.height() / 2f)
            }
        }

        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            invalidate()
        }

        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previous: android.graphics.Rect?) {
            super.onFocusChanged(gainFocus, direction, previous)
            invalidate()
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            invalidate()
        }

        // A key press sinks the icon as a finger does; the platform delivers the click.
        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            if (isEnabled && isConfirm(keyCode) && event.repeatCount == 0) {
                SancaiPress.sink(this, SancaiMotion.PRESS_SINK_DP * density)
            }
            return super.onKeyDown(keyCode, event)
        }

        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
            if (isConfirm(keyCode)) SancaiPress.release(this)
            return super.onKeyUp(keyCode, event)
        }

        private fun isConfirm(keyCode: Int): Boolean =
            keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

        override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.isEnabled = isEnabled
        }
    }

    public companion object {
        /** Height of the bar without insets, in dp. */
        public const val HEIGHT_DP: Float = 56f

        /** Side of a button's touch target, in dp. */
        public const val TARGET_DP: Float = 48f

        /** Side of a button's icon, in dp. */
        public const val ICON_DP: Float = 24f

        /** Most action icons a bar holds. */
        public const val MAX_ACTIONS: Int = 2

        /** Gap between the bar's edge and the first button target: the icon sits 16 dp in. */
        private const val EDGE_DP = 4f

        /** Least distance between the title and the bar's edge when a side has no button. */
        private const val TITLE_MARGIN_DP = 16f

        private const val LINE_GAP_DP = 1f
        private const val TEXT_MARGIN_DP = 8f
        private const val FOCUS_INSET_DP = 4f
        private const val NAVIGATION_STROKE_DP = 2f
        private const val ACTION_STROKE_DP = 1.7f

        private const val STATE_SUPER = "sancai:topBar:super"
        private const val STATE_LIFTED = "sancai:topBar:lifted"
    }
}
