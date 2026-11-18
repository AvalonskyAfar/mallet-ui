package io.github.avalonskyafar.mallet.showcase

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconView
import io.github.avalonskyafar.mallet.interaction.MalletPress
import io.github.avalonskyafar.mallet.interaction.MalletSpatialGraph.Side
import io.github.avalonskyafar.mallet.material.MalletWellDrawable
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.widget.MalletPage
import io.github.avalonskyafar.mallet.widget.MalletSurface
import io.github.avalonskyafar.mallet.widget.MalletText
import kotlin.math.min
import kotlin.math.roundToInt

/** What a page may ask of the app around it. */
interface ShowcaseHost {
    val activity: Activity
    val settings: ShowcaseSettings

    /** Travels to the page with [key]. */
    fun open(key: String)

    /** Travels to the parent of the current page. */
    fun back()

    /** Shows a short message at the bottom of the screen. */
    fun say(message: CharSequence)

    /** The appearance settings changed: rebuild the screen once [source] has settled. */
    fun applyAppearance(source: View)
}

/**
 * One page of the showcase: a place in the spatial graph and the content shown there.
 *
 * A page builds its content with the helpers of [PageScope] and with the library's widgets;
 * see `TracksPage` for the pattern.
 */
abstract class DemoPage(
    val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
) {
    /** Fills [page] with this page's content. */
    abstract fun build(page: PageScope)

    /** Builds the page view. Pages that are not a scrolling column override this. */
    open fun createView(page: PageScope): View {
        build(page)
        page.finish()
        return page.view
    }
}

/** Builds the scrolling column of one page with the rhythm of the design system. */
class PageScope(val host: ShowcaseHost, private val demo: DemoPage) {
    val context: Context = host.activity
    val theme: MalletTheme = MalletTheme.of(context)
    private val density = context.resources.displayMetrics.density
    private var hasContent = false

    /**
     * The finished page: a [MalletPage], which brings the page padding, the system-bar and
     * keyboard insets, the widest a column may grow, and the saved scroll position.
     */
    val view: View = MalletPage(context).apply {
        // With an id the page keeps its scroll position when the screen is rebuilt, for
        // example after a theme change. The host stores the state of each page separately.
        id = R.id.showcase_page_scroll
    }

    private val column: LinearLayout = (view as MalletPage).content

    init {
        addWayBack(atTop = true)
    }

    fun dp(value: Int): Int = (value * density).roundToInt()

    /** Width of the content column: the window, or the widest a page is allowed to grow. */
    private fun columnWidthPx(): Int {
        val windowDp = context.resources.configuration.screenWidthDp
        val window = if (windowDp > 0) dp(windowDp) else context.resources.displayMetrics.widthPixels
        return min(window, dp(MalletPage.DEFAULT_MAX_CONTENT_WIDTH_DP.toInt()) + dp(PAGE_SIDE) * 2)
    }

    fun str(@StringRes id: Int): String = context.getString(id)

    /** The page title with an optional line under it. */
    fun title(@StringRes title: Int = demo.title, @StringRes detail: Int = demo.summary) {
        loose(label(str(title), MalletTextRole.TITLE), sideDp = TEXT_INSET)
        if (detail != 0) loose(label(str(detail), MalletTextRole.CAPTION, MalletTextTone.MUTED), topDp = 4, sideDp = TEXT_INSET)
        hasContent = true
    }

    /**
     * Text that labels what follows it: aligned to the start edge of the page whatever
     * language it is written in, so that headings line up with their cards right to left too.
     */
    private fun label(value: CharSequence, role: MalletTextRole, tone: MalletTextTone = MalletTextTone.INK): MalletText =
        text(value, role, tone).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START }

    /** A heading with a card under it; [content] fills the card. */
    fun section(@StringRes title: Int, @StringRes detail: Int = 0, content: MalletSurface.() -> Unit): MalletSurface {
        heading(title, detail)
        val card = MalletSurface(context)
        card.content()
        loose(card, topDp = HEADING_GAP)
        return card
    }

    /** A heading on the canvas with nothing under it yet; what follows is added by the page. */
    fun heading(@StringRes title: Int, @StringRes detail: Int = 0) {
        loose(label(str(title), MalletTextRole.HEADING), topDp = if (hasContent) SECTION_GAP else 0, sideDp = TEXT_INSET)
        if (detail != 0) loose(label(str(detail), MalletTextRole.CAPTION, MalletTextTone.MUTED), topDp = 4, sideDp = TEXT_INSET)
    }

    /** A view placed directly on the canvas. */
    fun loose(view: View, topDp: Int = 0, sideDp: Int = 0, width: Int = MATCH, gravity: Int = Gravity.NO_GRAVITY) {
        val params = LinearLayout.LayoutParams(width, WRAP)
        params.topMargin = dp(topDp)
        params.marginStart = dp(sideDp)
        params.marginEnd = dp(sideDp)
        params.gravity = gravity
        column.addView(view, params)
        hasContent = true
    }

    /** Inflates [layout] into the column of the page and returns its root view. */
    fun inflate(@LayoutRes layout: Int, topDp: Int = 0): View {
        val view = LayoutInflater.from(context).inflate(layout, column, false)
        loose(view, topDp = topDp)
        return view
    }

    /** A muted remark on the canvas. */
    fun caption(text: CharSequence, topDp: Int = 8) {
        loose(label(text, MalletTextRole.CAPTION, MalletTextTone.MUTED), topDp = topDp, sideDp = TEXT_INSET)
    }

    /**
     * A code sample in a recessed well. The text can be selected and scrolls sideways.
     *
     * Kotlin is wrapped to the width of the well. Pass [wrap] = false for text that must stay
     * as it was written, such as a layout file. A line that is still too long fades out at
     * the rim of the well and is read by dragging the sample sideways.
     */
    fun code(snippet: String, topDp: Int = 10, wrap: Boolean = true) {
        val label = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * theme.textScale)
            setTextColor(theme.colors.ink)
            setTextIsSelectable(true)
            includeFontPadding = false
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(CODE_PADDING - CODE_RIM), dp(11), dp(CODE_PADDING - CODE_RIM), dp(11))
            textDirection = View.TEXT_DIRECTION_LTR
        }
        // Lines too long for the well are wrapped where the code allows it; see CodeWrap.
        val room = columnWidthPx() - dp(PAGE_SIDE) * 2 - dp(CODE_PADDING) * 2
        val code = snippet.trimIndent()
        label.text = if (wrap) CodeWrap.wrap(code, (room / label.paint.measureText("0")).toInt()) else code
        val scroller = HorizontalScrollView(context).apply {
            background = MalletWellDrawable(context)
            // Code reads left to right in every language, so the sample starts at its left end.
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            // The padding keeps scrolled text inside the rim of the well instead of over it.
            setPadding(dp(CODE_RIM), 0, dp(CODE_RIM), 0)
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(24))
            addView(label)
        }
        loose(scroller, topDp = topDp)
    }

    /** Text in a role of the type scale. */
    fun text(value: CharSequence, role: MalletTextRole, tone: MalletTextTone = MalletTextTone.INK): MalletText =
        MalletText.create(context, value, role, tone)

    /** Used by pages that are not written yet. */
    fun placeholder() {
        title()
        caption(str(R.string.showcase_placeholder), topDp = 24)
    }

    /** Adds what follows the content of every page. */
    internal fun finish() {
        addWayBack(atTop = false)
    }

    /**
     * Every page names the page it came from, at the edge facing it, with a chevron pointing
     * that way: at the top for a parent at the start, at the end or above, at the bottom for a
     * parent below.
     */
    private fun addWayBack(atTop: Boolean) {
        val parentKey = Pages.graph.parentOf(demo.key) ?: return
        val side = Pages.graph.sideOf(demo.key, parentKey) ?: return
        if ((side == Side.BELOW) == atTop) return
        val icon = when (side) {
            Side.START -> MalletIcon.CHEVRON_LEFT
            Side.END -> MalletIcon.CHEVRON_RIGHT
            Side.ABOVE -> MalletIcon.CHEVRON_UP
            Side.BELOW -> MalletIcon.CHEVRON_DOWN
        }
        val name = text(str(Pages.titleOf(parentKey)), MalletTextRole.LABEL, MalletTextTone.MUTED)
        val chevron = MalletIconView(context).apply { this.icon = icon }
        val row = LinearLayout(context).apply {
            orientation = if (side.isHorizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = dp(44)
            minimumWidth = dp(44)
            contentDescription = context.getString(R.string.showcase_back_to, name.text)
        }
        val iconSize = LinearLayout.LayoutParams(dp(24), dp(24))
        val label = LinearLayout.LayoutParams(WRAP, WRAP)
        if (side == Side.END || side == Side.BELOW) {
            row.addView(name, label)
            row.addView(chevron, iconSize)
        } else {
            row.addView(chevron, iconSize)
            row.addView(name, label)
        }
        MalletPress.attach(row, row) { host.back() }
        val gravity = when (side) {
            Side.START -> Gravity.START
            Side.END -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }
        val params = LinearLayout.LayoutParams(WRAP, WRAP)
        params.gravity = gravity
        if (atTop) params.bottomMargin = dp(10) else params.topMargin = dp(SECTION_GAP)
        column.addView(row, params)
    }

    companion object {
        const val MATCH: Int = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP: Int = ViewGroup.LayoutParams.WRAP_CONTENT

        // The page rhythm of the design system, in dp.
        const val PAGE_SIDE = 12
        const val PAGE_TOP = 22
        const val PAGE_BOTTOM = 34
        const val TEXT_INSET = 10
        const val HEADING_GAP = 10
        const val SECTION_GAP = 22
        const val BUTTON_SIDE = 30
        private const val CODE_PADDING = 13

        /** Room the gutter and the rim of a well take at each side, in dp. */
        private const val CODE_RIM = 3
    }
}
