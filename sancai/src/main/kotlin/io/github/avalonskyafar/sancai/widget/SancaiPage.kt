package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.theme.SancaiSpacing
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A vertically scrolling page: the canvas every Sancai screen stands on.
 *
 * The page is a [ScrollView] that holds one vertical column, [content]. Views declared inside
 * the page in XML, or added with [addView], go into that column, so a page is used like a
 * vertical `LinearLayout` that happens to scroll:
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiPage
 *     android:id="@+id/page"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent">
 *     <!-- headings, SancaiSurface cards, buttons -->
 * </io.github.avalonskyafar.sancai.widget.SancaiPage>
 * ```
 *
 * What the page takes care of:
 *
 * - **Rhythm.** The padding is [SancaiSpacing.PAGE_SIDE], [SancaiSpacing.PAGE_TOP] and
 *   [SancaiSpacing.PAGE_BOTTOM]; [addTitle], [addSection], [addButton] and [addSpace] place
 *   the usual elements with the usual gaps. Padding given in XML replaces the default.
 * - **Edge to edge.** While [fitSystemBars] is on, the page reaches behind the system bars:
 *   the system-bar and display-cutout insets are added to its padding, and the keyboard
 *   inset to the bottom. A page asks for the insets whenever it is attached, so it is also
 *   right when it joins a screen late.
 * - **Clear bars.** Content that scrolls is not drawn under the status bar, nor under a
 *   navigation bar with buttons: text sliding beneath a clock cannot be read and makes the
 *   clock hard to read. It softens over a few dp and ends at the bar. Under the handle of
 *   gesture navigation, which covers almost nothing, content shows through.
 * - **Width.** The column grows no wider than [maxContentWidthDp] (560 dp) and stands in the
 *   middle of a wider page, so that on a tablet, or on a phone lying on its side, a title and
 *   its control do not drift to opposite ends of the screen.
 * - **Stillness.** There are no scroll bars, and the over-scroll effect only exists while the
 *   content is taller than the page: a short page does not move at all. A page opens at its
 *   top: it does not scroll to a text field, or raise the keyboard, unless the field is
 *   tapped or asked for.
 * - **State.** The scroll position is saved and restored. As for any view, this needs an id.
 *
 * The column accepts the layout attributes a page needs: width, height, margins,
 * `layout_gravity` and `layout_weight`.
 */
public open class SancaiPage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ScrollView(context, attrs, defStyleAttr) {

    /** The theme this page was created with. */
    protected val theme: SancaiTheme = SancaiTheme.of(context)

    /** The vertical column that holds the children of the page. */
    public val content: LinearLayout = LinearLayout(context)

    private val density = resources.displayMetrics.density
    private var baseStart = 0
    private var baseTop = 0
    private var baseEnd = 0
    private var baseBottom = 0
    private var lastInsets: WindowInsetsCompat? = null
    private var pendingScrollY = -1
    private var ready = false
    private var applying = false
    private var revealFocusPending = false
    private val applied = intArrayOf(-1, -1, -1, -1)

    // How much of the top and bottom edge belongs to a system bar that content must not show under.
    private var guardTop = 0
    private var guardBottom = 0
    private val guardPaint = Paint()
    private var guardColor = 0
    private var guardReady = false

    /**
     * Whether the page adds the system-bar and display-cutout insets to its padding, and the
     * keyboard inset to the bottom. On by default, which makes the page work edge to edge.
     * Switch it off for a page inside a host that already keeps clear of the bars. The insets
     * are passed on unconsumed either way.
     */
    public var fitSystemBars: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                applyPadding()
                if (value) ViewCompat.requestApplyInsets(this)
            }
        }

    /**
     * The widest the column of the page gets, in dp. On a wider page the column keeps this
     * width and is centred. Zero or less lets the column fill the page.
     */
    public var maxContentWidthDp: Float = DEFAULT_MAX_CONTENT_WIDTH_DP
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    init {
        var fit = true
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiPage, defStyleAttr, 0)
            try {
                fit = a.getBoolean(R.styleable.SancaiPage_sancaiPageFitSystemBars, true)
                if (a.hasValue(R.styleable.SancaiPage_sancaiPageMaxContentWidth)) {
                    maxContentWidthDp = a.getDimension(R.styleable.SancaiPage_sancaiPageMaxContentWidth, 0f) / density
                }
            } finally {
                a.recycle()
            }
        }
        if (paddingLeft == 0 && paddingTop == 0 && paddingRight == 0 && paddingBottom == 0) {
            baseStart = px(SancaiSpacing.PAGE_SIDE)
            baseTop = px(SancaiSpacing.PAGE_TOP)
            baseEnd = px(SancaiSpacing.PAGE_SIDE)
            baseBottom = px(SancaiSpacing.PAGE_BOTTOM)
        } else {
            baseStart = paddingStart
            baseTop = paddingTop
            baseEnd = paddingEnd
            baseBottom = paddingBottom
        }
        setBackgroundColor(theme.colors.canvas)
        isFillViewport = true
        clipToPadding = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        // Long pages keep the platform stretch; short pages have no edge effect and stay still.
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

        content.orientation = LinearLayout.VERTICAL
        super.addView(
            content,
            -1,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL,
            ),
        )

        fitSystemBars = fit
        ready = true
        applyPadding()
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            lastInsets = insets
            applyPadding()
            insets
        }
    }

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    /**
     * Replaces the padding of the page, in dp. System insets are still added on top while
     * [fitSystemBars] is on. Use this instead of `setPadding`, which the next inset pass
     * would overwrite.
     */
    public fun setPagePadding(startDp: Float, topDp: Float, endDp: Float, bottomDp: Float) {
        baseStart = px(startDp)
        baseTop = px(topDp)
        baseEnd = px(endDp)
        baseBottom = px(bottomDp)
        applyPadding()
    }

    private fun applyPadding() {
        // The base class resolves RTL properties before this class has read its padding.
        if (!ready) return
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        var left = if (rtl) baseEnd else baseStart
        var top = baseTop
        var right = if (rtl) baseStart else baseEnd
        var bottom = baseBottom
        val insets = lastInsets
        var keepTop = 0
        var keepBottom = 0
        if (fitSystemBars && insets != null) {
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            left += bars.left
            top += bars.top
            right += bars.right
            bottom += max(bars.bottom, keyboard.bottom)
            keepTop = bars.top
            // A bar with buttons is "tappable"; the handle of gesture navigation is not.
            keepBottom = min(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom)
        }
        if (keepTop != guardTop || keepBottom != guardBottom) {
            guardTop = keepTop
            guardBottom = keepBottom
            invalidate()
        }
        // Reading the padding resolves it, which reports the RTL properties, which lands here
        // again: compare against what was applied last, not against the view.
        if (applying || (applied[0] == left && applied[1] == top && applied[2] == right && applied[3] == bottom)) return
        applying = true
        // The window is not resized for the keyboard when it arrives as an inset, so the
        // platform does not bring the focused field back into view: do it after the layout.
        if (applied[3] in 0 until bottom) revealFocusPending = true
        applied[0] = left
        applied[1] = top
        applied[2] = right
        applied[3] = bottom
        setPadding(left, top, right, bottom)
        applying = false
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        applyPadding()
    }

    // ---- children go into the column ----

    override fun addView(child: View) {
        addView(child, -1)
    }

    override fun addView(child: View, index: Int) {
        addView(child, index, child.layoutParams ?: generateDefaultLayoutParams())
    }

    override fun addView(child: View, params: ViewGroup.LayoutParams) {
        addView(child, -1, params)
    }

    /**
     * Adds [child] to the column, [content], at [index] of the column. Frame-layout
     * parameters, as produced by XML inflation, are translated into column parameters.
     */
    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        content.addView(child, index, columnParams(params))
    }

    /** Removes [view] from the column. */
    override fun removeView(view: View) {
        if (view !== content) content.removeView(view)
    }

    /**
     * Removes the child at [index] of the column, mirroring [addView] with an index. The
     * column itself can not be removed from the page.
     */
    override fun removeViewAt(index: Int) {
        content.removeViewAt(index)
    }

    /** Removes [count] children of the column, starting at [start] of the column. */
    override fun removeViews(start: Int, count: Int) {
        content.removeViews(start, count)
    }

    /** Empties the column; the column itself stays. */
    override fun removeAllViews() {
        content.removeAllViews()
    }

    override fun generateLayoutParams(attrs: AttributeSet?): FrameLayout.LayoutParams =
        ColumnParams(context, attrs)

    override fun generateDefaultLayoutParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun columnParams(params: ViewGroup.LayoutParams): LinearLayout.LayoutParams = when (params) {
        is LinearLayout.LayoutParams -> params
        is FrameLayout.LayoutParams -> LinearLayout.LayoutParams(params as ViewGroup.MarginLayoutParams).also {
            // UNSPECIFIED_GRAVITY and "no gravity" are both -1, so the value carries over as is.
            it.gravity = params.gravity
            if (params is ColumnParams) it.weight = params.weight
        }
        is ViewGroup.MarginLayoutParams -> LinearLayout.LayoutParams(params)
        else -> LinearLayout.LayoutParams(params)
    }

    /** What XML inflation hands to [addView]: frame parameters that also remember the weight. */
    private class ColumnParams(context: Context, attrs: AttributeSet?) : FrameLayout.LayoutParams(context, attrs) {
        var weight = 0f

        init {
            val a = context.obtainStyledAttributes(attrs, WEIGHT_ATTR)
            try {
                weight = a.getFloat(0, 0f)
            } finally {
                a.recycle()
            }
        }
    }

    // ---- the rhythm of a page ----

    /**
     * Adds the title of the page: 20 sp, start aligned, inset like a heading and followed by
     * [SancaiSpacing.TITLE_GAP]. Accessibility services announce it as a heading.
     */
    public fun addTitle(text: CharSequence?): SancaiText {
        val title = looseText(text, SancaiTextRole.TITLE, SancaiTextTone.INK)
        ViewCompat.setAccessibilityHeading(title, true)
        content.addView(title, insetParams(SancaiSpacing.TITLE_GAP))
        return title
    }

    /**
     * Adds a section: a [SancaiHeading] ([title] over an optional muted [detail] line), then
     * [SancaiSpacing.HEADING_GAP], then [card] across the full column, then
     * [SancaiSpacing.SECTION_GAP]. A `null` or blank [title] or [detail] leaves that line out;
     * with both missing the card is added without a heading. Returns [card].
     */
    public fun <T : View> addSection(title: CharSequence?, detail: CharSequence?, card: T): T {
        val hasTitle = !title.isNullOrBlank()
        val hasDetail = !detail.isNullOrBlank()
        if (hasTitle || hasDetail) {
            // The heading insets its own text by SancaiSpacing.TEXT_INSET.
            val heading = SancaiHeading(context)
            heading.title = if (hasTitle) title else null
            heading.detail = if (hasDetail) detail else null
            content.addView(heading, matchWrap().also { it.bottomMargin = px(SancaiSpacing.HEADING_GAP) })
        }
        content.addView(card, matchWrap().also { it.bottomMargin = px(SancaiSpacing.SECTION_GAP) })
        return card
    }

    /**
     * Adds a full-width button that stands on the page rather than in a card: side margins of
     * [SancaiSpacing.BUTTON_SIDE] and [SancaiSpacing.SECTION_GAP] below. Returns [button].
     */
    public fun <T : View> addButton(button: T): T {
        val params = matchWrap()
        params.marginStart = px(SancaiSpacing.BUTTON_SIDE)
        params.marginEnd = px(SancaiSpacing.BUTTON_SIDE)
        params.bottomMargin = px(SancaiSpacing.SECTION_GAP)
        content.addView(button, params)
        return button
    }

    /** Adds [dp] of empty vertical space; accessibility services skip it. Returns the spacer. */
    public fun addSpace(dp: Float): View {
        val space = View(context)
        space.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        content.addView(space, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(max(0f, dp))))
        return space
    }

    private fun looseText(text: CharSequence?, role: SancaiTextRole, tone: SancaiTextTone): SancaiText {
        val view = SancaiText.create(context, text, role, tone)
        view.gravity = Gravity.START
        view.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        return view
    }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    /** Column parameters for headings and loose text: inset on both sides, [gapDp] below. */
    private fun insetParams(gapDp: Float): LinearLayout.LayoutParams = matchWrap().also {
        it.marginStart = px(SancaiSpacing.TEXT_INSET)
        it.marginEnd = px(SancaiSpacing.TEXT_INSET)
        it.bottomMargin = px(gapDp)
    }

    // ---- saved state ----

    override fun onSaveInstanceState(): Parcelable {
        val state = Bundle()
        state.putParcelable(STATE_SUPER, super.onSaveInstanceState())
        state.putInt(STATE_SCROLL, scrollY)
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            @Suppress("DEPRECATION")
            super.onRestoreInstanceState(state.getParcelable(STATE_SUPER))
            pendingScrollY = state.getInt(STATE_SCROLL, -1)
            // Applied after the next layout pass, when the height of the content is known.
            requestLayout()
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (guardTop == 0 && guardBottom == 0) {
            super.dispatchDraw(canvas)
            return
        }
        val left = scrollX.toFloat()
        val cutTop = scrollY.toFloat() + guardTop
        val cutBottom = scrollY.toFloat() + height - guardBottom
        val save = canvas.save()
        canvas.clipRect(left, cutTop, left + width, cutBottom)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)

        // The cut is softened in the colour of the page, when the page has one colour.
        val color = (background as? ColorDrawable)?.color ?: return
        if (color ushr 24 != 0xFF) return
        val fade = px(BAR_FADE_DP).toFloat()
        if (!guardReady || color != guardColor) {
            guardColor = color
            guardReady = true
            guardPaint.shader = LinearGradient(0f, 0f, 0f, fade, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
        if (guardTop > 0) {
            val fadeSave = canvas.save()
            canvas.translate(left, cutTop)
            canvas.drawRect(0f, 0f, width.toFloat(), fade, guardPaint)
            canvas.restoreToCount(fadeSave)
        }
        if (guardBottom > 0) {
            val fadeSave = canvas.save()
            canvas.translate(left, cutBottom)
            canvas.scale(1f, -1f)
            canvas.drawRect(0f, 0f, width.toFloat(), fade, guardPaint)
            canvas.restoreToCount(fadeSave)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Android hands the window insets down when they change, not when a view arrives. A
        // page attached later (by a page host, a pager, a fragment) has to ask for them.
        if (fitSystemBars) ViewCompat.requestApplyInsets(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // The column takes the room between the paddings, up to its limit. The width is given
        // to it as a size of its own, because a scroll view measures its child twice when it
        // stretches it to the viewport, and both times from the child's layout parameters.
        val room = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val limit = px(maxContentWidthDp)
        val capped = maxContentWidthDp > 0f && room > limit &&
            MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
        content.layoutParams.width = if (capped) limit else ViewGroup.LayoutParams.MATCH_PARENT
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (pendingScrollY >= 0) {
            val range = max(0, content.height + paddingTop + paddingBottom - height)
            scrollTo(0, pendingScrollY.coerceAtMost(range))
            pendingScrollY = -1
        }
        if (revealFocusPending) {
            revealFocusPending = false
            revealFocused()
        }
    }

    /**
     * In touch mode a request for focus that names no view is only the window looking for
     * somewhere to put the focus: Android before 8.0 does that whenever a window opens, and
     * every version does it when the focused view goes away. Handing it to a descendant would
     * open the page scrolled down to its first text field or selectable text, and raise the
     * keyboard for a field nobody touched. The page declines; a field that is asked directly
     * (`field.requestFocus()`), or tapped, takes the focus as always.
     */
    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        if (isInTouchMode) return false
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }

    // ---- keeping content clear of the bars ----

    /**
     * How far to scroll so that [rect] (in the coordinates of the page) is visible. Unlike the
     * platform version this counts the padding as covered: with the bars and the keyboard
     * added to the padding, a row underneath them is not on screen yet.
     */
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        val delta = super.computeScrollDeltaToGetChildRectOnScreen(rect)
        if (height == 0 || rect.height() > height - paddingTop - paddingBottom) return delta
        val scrolled = scrollY + delta
        val range = max(0, content.height + paddingTop + paddingBottom - height)
        val below = rect.bottom - (scrolled + height - paddingBottom)
        if (delta >= 0 && below > 0) return delta + below.coerceAtMost(max(0, range - scrolled))
        val above = (scrolled + paddingTop) - rect.top
        if (delta <= 0 && above > 0) return delta - above.coerceAtMost(max(0, scrolled))
        return delta
    }

    /** Scrolls the focused descendant out from under the keyboard after the padding grew. */
    private fun revealFocused() {
        val focused = findFocus() ?: return
        if (focused === this) return
        val rect = Rect(0, 0, focused.width, focused.height)
        offsetDescendantRectToMyCoords(focused, rect)
        val delta = computeScrollDeltaToGetChildRectOnScreen(rect)
        if (delta != 0) scrollBy(0, delta)
    }

    public companion object {
        /** The widest the column of a page gets unless [maxContentWidthDp] says otherwise, in dp. */
        public const val DEFAULT_MAX_CONTENT_WIDTH_DP: Float = 560f

        /** The distance over which scrolling content fades out before a system bar, in dp. */
        public const val BAR_FADE_DP: Float = 10f

        private const val STATE_SUPER = "sancai:page:super"
        private const val STATE_SCROLL = "sancai:page:scroll"
        private val WEIGHT_ATTR = intArrayOf(android.R.attr.layout_weight)
    }
}
