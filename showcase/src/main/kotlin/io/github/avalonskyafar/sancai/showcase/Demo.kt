package io.github.avalonskyafar.sancai.showcase

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconView
import io.github.avalonskyafar.sancai.interaction.SancaiPress
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialGraph.Side
import io.github.avalonskyafar.sancai.material.SancaiWellDrawable
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.theme.SancaiWindow
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiText
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
    val theme: SancaiTheme = SancaiTheme.of(context)
    private val density = context.resources.displayMetrics.density
    private val column = ContentColumn(context)
    private var hasContent = false

    /** The finished page. */
    val view: View = ScrollView(context).apply {
        setBackgroundColor(theme.colors.canvas)
        isFillViewport = true
        clipToPadding = false
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        addView(column, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_HORIZONTAL))
        SancaiWindow.padForSystemBars(this, includeIme = true)
    }

    init {
        column.setPadding(dp(PAGE_SIDE), dp(PAGE_TOP), dp(PAGE_SIDE), dp(PAGE_BOTTOM))
        addWayBack(atTop = true)
    }

    fun dp(value: Int): Int = (value * density).roundToInt()

    fun str(@StringRes id: Int): String = context.getString(id)

    /** The page title with an optional line under it. */
    fun title(@StringRes title: Int = demo.title, @StringRes detail: Int = demo.summary) {
        loose(text(str(title), SancaiTextRole.TITLE), sideDp = TEXT_INSET)
        if (detail != 0) loose(text(str(detail), SancaiTextRole.CAPTION, SancaiTextTone.MUTED), topDp = 4, sideDp = TEXT_INSET)
        hasContent = true
    }

    /** A heading with a card under it; [content] fills the card. */
    fun section(@StringRes title: Int, @StringRes detail: Int = 0, content: SancaiSurface.() -> Unit): SancaiSurface {
        loose(text(str(title), SancaiTextRole.HEADING), topDp = if (hasContent) SECTION_GAP else 0, sideDp = TEXT_INSET)
        if (detail != 0) loose(text(str(detail), SancaiTextRole.CAPTION, SancaiTextTone.MUTED), topDp = 4, sideDp = TEXT_INSET)
        val card = SancaiSurface(context)
        card.content()
        loose(card, topDp = HEADING_GAP)
        return card
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

    /** A muted remark on the canvas. */
    fun caption(text: CharSequence, topDp: Int = 8) {
        loose(text(text, SancaiTextRole.CAPTION, SancaiTextTone.MUTED), topDp = topDp, sideDp = TEXT_INSET)
    }

    /** A code sample in a recessed well. The text can be selected and scrolls sideways. */
    fun code(snippet: String, topDp: Int = 10) {
        val label = TextView(context).apply {
            text = snippet.trimIndent()
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * theme.textScale)
            setTextColor(theme.colors.ink)
            setTextIsSelectable(true)
            includeFontPadding = false
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(13), dp(11), dp(13), dp(11))
            textDirection = View.TEXT_DIRECTION_LTR
        }
        val scroller = HorizontalScrollView(context).apply {
            background = SancaiWellDrawable(context)
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(label)
        }
        loose(scroller, topDp = topDp)
    }

    /** Text in a role of the type scale. */
    fun text(value: CharSequence, role: SancaiTextRole, tone: SancaiTextTone = SancaiTextTone.INK): SancaiText =
        SancaiText.create(context, value, role, tone)

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
            Side.START -> SancaiIcon.CHEVRON_LEFT
            Side.END -> SancaiIcon.CHEVRON_RIGHT
            Side.ABOVE -> SancaiIcon.CHEVRON_UP
            Side.BELOW -> SancaiIcon.CHEVRON_DOWN
        }
        val name = text(str(Pages.titleOf(parentKey)), SancaiTextRole.LABEL, SancaiTextTone.MUTED)
        val chevron = SancaiIconView(context).apply { this.icon = icon }
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
        SancaiPress.attach(row, row) { host.back() }
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

    /** A vertical column that stays readable on wide screens. */
    private class ContentColumn(context: Context) : LinearLayout(context) {
        private val maxWidth = (MAX_WIDTH_DP * resources.displayMetrics.density).roundToInt()

        init {
            orientation = VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec)
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            val width = if (mode == MeasureSpec.UNSPECIFIED) maxWidth else min(available, maxWidth)
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
        }
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
        private const val MAX_WIDTH_DP = 560
    }
}
