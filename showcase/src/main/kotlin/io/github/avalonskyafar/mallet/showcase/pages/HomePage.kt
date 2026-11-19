package io.github.avalonskyafar.mallet.showcase.pages

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.Pages
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletWindow
import io.github.avalonskyafar.mallet.widget.MalletNavBar
import io.github.avalonskyafar.mallet.widget.MalletPowerSwitch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The centre of the map: one hero control on a bare canvas and, at the bottom, one entry for
 * each page that lies around it.
 *
 * Home is built the way the main page of the original app is: nothing on it points at its
 * neighbours. There are no arrows and no names at the edges. The row at the bottom counts the
 * pages around Home, one entry each; tapping an entry travels there, and so does dragging the
 * page towards that page's side, which slides it in from there. The first and the last entry
 * are the pages at the start and at the end side; the two between them lie below and above.
 *
 * It builds its own view instead of using [PageScope]'s scrolling column, because the page is
 * a fixed composition, not a list.
 */
object HomePage : DemoPage(Pages.HOME, R.string.page_home_title, 0) {

    /** One entry of the bottom row: the page it opens and the icon that stands for it. */
    private class Entry(val page: DemoPage, val icon: MalletIcon)

    /** In the order of the row: start side, below, above, end side. */
    private val entries: List<Entry> by lazy {
        listOf(
            Entry(FoundationsPage, MalletIcon.PALETTE),
            Entry(ComponentsPage, MalletIcon.GRID),
            Entry(AppearancePage, MalletIcon.SLIDERS),
            Entry(InteractionsPage, MalletIcon.HAND),
        )
    }

    override fun build(page: PageScope) {
        // Home is a fixed composition, assembled in createView().
    }

    override fun createView(page: PageScope): View {
        val scope = page
        val context = scope.context
        val theme = scope.theme
        val root = StageColumn(context)
        root.id = R.id.showcase_home_stage
        root.setBackgroundColor(theme.colors.canvas)
        MalletWindow.padForSystemBars(root)

        val centre = CentreColumn(context, scope.dp(CENTRE_MAX_WIDTH_DP))
        val wordmark = scope.text(scope.str(R.string.home_wordmark), MalletTextRole.DISPLAY).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, WORDMARK_SP * theme.textScale)
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
        }
        val tagline = scope.text(scope.str(R.string.home_tagline), MalletTextRole.CAPTION, MalletTextTone.MUTED).apply {
            gravity = Gravity.CENTER
        }
        val status = scope.text("", MalletTextRole.CAPTION, MalletTextTone.MUTED).apply { gravity = Gravity.CENTER }
        val power = MalletPowerSwitch(context).apply {
            onLabel = scope.str(R.string.home_switch_on)
            offLabel = scope.str(R.string.home_switch_off)
            turnOnLabel = scope.str(R.string.home_switch_turn_on)
            turnOffLabel = scope.str(R.string.home_switch_turn_off)
            setChecked(true, false)
        }
        fun describe(on: Boolean) {
            status.text = scope.str(if (on) R.string.home_status_on else R.string.home_status_off)
        }
        describe(true)
        power.setOnCheckedChangeListener { _, checked -> describe(checked) }

        fun params(top: Int) = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = scope.dp(top) }
        centre.addView(wordmark, params(0))
        centre.addView(tagline, params(6))
        centre.addView(power, LinearLayout.LayoutParams(PageScope.MATCH, scope.dp(92)).apply { topMargin = scope.dp(34) })
        centre.addView(status, params(4))
        // The composition stands a little above the middle of the room the row leaves.
        val centreParams = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP)
        centreParams.marginStart = scope.dp(CENTRE_SIDE_DP)
        centreParams.marginEnd = scope.dp(CENTRE_SIDE_DP)
        root.addView(centre, centreParams)

        val row = MalletNavBar(context).apply {
            id = R.id.showcase_home_entries
            mode = MalletNavBar.Mode.LAUNCHER
            // The page is already padded for the system bars.
            fitNavigationBar = false
            setItems(entries.map { MalletNavBar.Item(it.icon, scope.str(it.page.title)) })
            setOnItemSelectedListener { _, index -> scope.host.open(entries[index].page.key) }
        }
        root.addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
        return root
    }

    private const val CENTRE_SIDE_DP = 24
    private const val CENTRE_MAX_WIDTH_DP = 420
    private const val CENTRE_LIFT_DP = 19
    private const val WORDMARK_SP = 34f

    /**
     * The page as a stage over an entry row. The stage is the composition: it stands centred
     * in the room the row leaves, lifted by [CENTRE_LIFT_DP] so it reads a little above the
     * middle, and the row keeps its whole height at the bottom. In a window too short for the
     * composition, the composition is pinned to the top and the row covers its lower part
     * rather than its middle: the wordmark stays whole, and what the row covers is reachable
     * again once the window is tall enough — the page has nothing to scroll.
     */
    private class StageColumn(context: Context) : ViewGroup(context) {

        init {
            clipChildren = false
        }

        private val centre: View get() = getChildAt(0)
        private val row: View get() = getChildAt(1)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val innerWidth = max(0, width - paddingLeft - paddingRight)
            val innerHeight = max(0, height - paddingTop - paddingBottom)
            check(childCount == 2) { "StageColumn holds the composition and the entry row, and nothing else" }
            // The row is as tall as it wants to be, but never taller than the page.
            row.measure(
                MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(innerHeight, MeasureSpec.AT_MOST),
            )
            // The composition keeps the side margins the page gives it.
            val margins = centre.layoutParams as ViewGroup.MarginLayoutParams
            val centreWidth = max(0, innerWidth - margins.leftMargin - margins.rightMargin)
            centre.measure(
                MeasureSpec.makeMeasureSpec(centreWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(innerHeight, MeasureSpec.AT_MOST),
            )
            setMeasuredDimension(
                resolveSize(innerWidth + paddingLeft + paddingRight, widthMeasureSpec),
                resolveSize(innerHeight + paddingTop + paddingBottom, heightMeasureSpec),
            )
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val roomTop = paddingTop
            val roomBottom = height - paddingBottom - row.measuredHeight
            // Centred, then lifted. In a room too short for the composition it is pinned to
            // the top of the room instead, so the wordmark is never cut off above the window.
            val topOfCentre = max(roomTop, roomTop + ((roomBottom - roomTop - centre.measuredHeight) / 2) - liftPx())
            val margins = centre.layoutParams as ViewGroup.MarginLayoutParams
            val centreWidth = centre.measuredWidth
            val centreLeft = paddingLeft + margins.leftMargin + (width - paddingLeft - paddingRight - margins.leftMargin - margins.rightMargin - centreWidth) / 2
            centre.layout(centreLeft, topOfCentre, centreLeft + centreWidth, topOfCentre + centre.measuredHeight)
            val rowWidth = row.measuredWidth
            val rowLeft = paddingLeft + (width - paddingLeft - paddingRight - rowWidth) / 2
            row.layout(rowLeft, height - paddingBottom - row.measuredHeight, rowLeft + rowWidth, height - paddingBottom)
        }

        private fun liftPx(): Int = (CENTRE_LIFT_DP * resources.displayMetrics.density).roundToInt()
    }

    /** The centre of the page; it stops growing on wide screens. */
    private class CentreColumn(context: Context, private val maxWidth: Int) : LinearLayout(context) {
        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec)
            val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) maxWidth else minOf(available, maxWidth)
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
        }
    }
}
