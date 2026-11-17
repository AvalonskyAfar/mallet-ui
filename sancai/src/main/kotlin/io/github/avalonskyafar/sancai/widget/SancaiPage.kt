package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.graphics.Rect
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
 * - **Edge to edge.** While [fitSystemBars] is on, the system-bar and display-cutout insets
 *   are added to the padding, and the keyboard inset to the bottom. Padding is not clipped,
 *   so content scrolls underneath the bars instead of being cut off above them.
 * - **Stillness.** There are no scroll bars, and the over-scroll effect only exists while the
 *   content is taller than the page: a short page does not move at all.
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

    init {
        var fit = true
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiPage, defStyleAttr, 0)
            try {
                fit = a.getBoolean(R.styleable.SancaiPage_sancaiPageFitSystemBars, true)
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
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
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
        if (fitSystemBars && insets != null) {
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            left += bars.left
            top += bars.top
            right += bars.right
            bottom += max(bars.bottom, keyboard.bottom)
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

    private companion object {
        private const val STATE_SUPER = "sancai:page:super"
        private const val STATE_SCROLL = "sancai:page:scroll"
        private val WEIGHT_ATTR = intArrayOf(android.R.attr.layout_weight)
    }
}
