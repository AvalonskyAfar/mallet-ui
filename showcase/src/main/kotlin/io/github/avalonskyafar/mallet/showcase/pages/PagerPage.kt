package io.github.avalonskyafar.mallet.showcase.pages

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconView
import io.github.avalonskyafar.mallet.interaction.MalletPager
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.widget.MalletButton
import io.github.avalonskyafar.mallet.widget.MalletControlRow
import io.github.avalonskyafar.mallet.widget.MalletPageIndicator
import io.github.avalonskyafar.mallet.widget.MalletSwitch
import io.github.avalonskyafar.mallet.widget.MalletText
import kotlin.math.roundToInt

/** Pager: a short flow with a linked heading and dots, moved by hand and from code. */
object PagerPage : DemoPage("i_pager", R.string.page_i_pager_title, R.string.page_i_pager_summary) {

    private const val PAGES = 4
    private const val CONTENT_DP = 220
    private const val HEADING_DP = 58

    /**
     * Where text stands inside the two cards: at the 14 dp at which a start-aligned
     * [MalletControlRow] puts its own text, so that the status line and the switch row share an edge.
     */
    private const val TEXT_EDGE_DP = 14

    /** Space above and below the divider of the second card. */
    private const val DIVIDER_GAP_DP = 3f

    /** Air the switch row keeps above and below its text when the text outgrows the row. */
    private const val ROW_AIR_DP = 8

    private val titles = intArrayOf(
        R.string.pager_page1_title, R.string.pager_page2_title,
        R.string.pager_page3_title, R.string.pager_page4_title,
    )
    private val bodies = intArrayOf(
        R.string.pager_page1_body, R.string.pager_page2_body,
        R.string.pager_page3_body, R.string.pager_page4_body,
    )
    private val icons = arrayOf(MalletIcon.HAND, MalletIcon.REFRESH, MalletIcon.LAYERS, MalletIcon.SHIELD)

    override fun build(page: PageScope) {
        val context = page.context
        page.title()

        val heading = MalletPager(context)
        val pager = MalletPager(context)
        val indicator = MalletPageIndicator(context)

        page.section(R.string.pager_flow_title, R.string.pager_flow_detail) {
            heading.setPages(PAGES) { index, _ -> headingPage(page, index) }
            pager.setPages(PAGES) { index, _ -> contentPage(page, index) }
            // Each leads the other, so a drag on the heading turns the content as well.
            pager.linkWith(heading)
            heading.linkWith(pager)
            indicator.attachTo(pager)

            val headingHeight = (page.dp(HEADING_DP) * page.theme.textScale).roundToInt()
            addView(heading, LinearLayout.LayoutParams(PageScope.MATCH, headingHeight))
            addDivider(4f)
            addView(pager, LinearLayout.LayoutParams(PageScope.MATCH, page.dp(CONTENT_DP)))
            addView(
                indicator,
                LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = page.dp(4)
                },
            )
        }
        page.code(
            """
            pager.setPages(4) { index, parent ->
                createStep(index, parent)
            }
            heading.setPages(4) { index, parent ->
                createHeading(index, parent)
            }
            pager.linkWith(heading)
            heading.linkWith(pager)
            indicator.attachTo(pager)
            """,
        )

        val status = startText(page, "", MalletTextRole.BODY_COMPACT, MalletTextTone.MUTED)
        val previous = MalletButton(context).apply {
            text = page.str(R.string.pager_previous)
            icon = MalletIcon.CHEVRON_LEFT
        }
        val next = MalletButton(context).apply {
            text = page.str(R.string.pager_next)
            variant = MalletButton.Variant.PRIMARY
        }
        val first = MalletButton(context).apply {
            text = page.str(R.string.pager_first)
            variant = MalletButton.Variant.TEXT
        }

        var reason = R.string.pager_status_rest
        fun refresh() {
            val current = pager.currentPage
            status.text = context.getString(reason, current + 1, PAGES)
            previous.isEnabled = current > 0
            next.isEnabled = current < PAGES - 1
            first.isEnabled = current > 0
        }
        val listener = object : MalletPager.OnPageChangeListener {
            override fun onPageSelected(index: Int) {
                reason = R.string.pager_status_hand
                refresh()
            }

            override fun onPageScrolled(index: Int, offset: Float) = refresh()
        }
        pager.addOnPageChangeListener(listener)
        heading.addOnPageChangeListener(listener)

        fun move(index: Int, animate: Boolean) {
            if (pager.isMoving || heading.isMoving) return
            reason = if (animate) R.string.pager_status_code else R.string.pager_status_jump
            pager.setCurrentPage(index, animate)
            refresh()
        }
        previous.setOnClickListener { move(pager.currentPage - 1, animate = true) }
        next.setOnClickListener { move(pager.currentPage + 1, animate = true) }
        first.setOnClickListener { move(0, animate = false) }

        page.section(R.string.pager_code_title, R.string.pager_code_detail) {
            val edge = page.dp(TEXT_EDGE_DP)
            addView(status, below(page, 6).apply { marginStart = edge; marginEnd = edge })
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(previous, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
            row.addView(next, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f).apply { marginStart = page.dp(8) })
            addView(row, below(page, 12))
            // The text button and the row keep air around their text themselves, so the divider
            // needs little of its own to stand evenly between the two.
            addView(first, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            addDivider(DIVIDER_GAP_DP)
            val swipe = MalletSwitch(context).apply {
                setChecked(true, animate = false)
                setOnCheckedChangeListener { _, checked ->
                    pager.isEnabled = checked
                    heading.isEnabled = checked
                    indicator.isEnabled = checked
                }
            }
            val swipeRow = MalletControlRow(context).apply {
                title = page.str(R.string.pager_swipe_title)
                detail = page.str(R.string.pager_swipe_detail)
                alignment = MalletControlRow.Alignment.START
                // Enlarged text makes the row taller than its minimum height, and then it has
                // no air of its own above and below its text.
                setPadding(0, page.dp(ROW_AIR_DP), 0, page.dp(ROW_AIR_DP))
                setControl(swipe)
            }
            addView(swipeRow, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
        }
        page.code(
            """
            next.setOnClickListener {
                val step = pager.currentPage + 1
                pager.setCurrentPage(step, animate = true)
            }
            first.setOnClickListener {
                pager.setCurrentPage(0, animate = false)
            }
            pager.isEnabled = false
            """,
        )
        page.caption(page.str(R.string.pager_note), topDp = 18)
        refresh()
    }

    private fun below(page: PageScope, topDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(topDp) }

    /**
     * Text of a card that keeps to the start edge whatever language it is written in, so that
     * it mirrors with the rest of the page in a right-to-left layout.
     */
    private fun startText(
        page: PageScope,
        value: CharSequence,
        role: MalletTextRole,
        tone: MalletTextTone = MalletTextTone.INK,
    ): MalletText = page.text(value, role, tone).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START }

    private fun headingPage(page: PageScope, index: Int): View =
        LinearLayout(page.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(page.dp(TEXT_EDGE_DP), 0, page.dp(TEXT_EDGE_DP), 0)
            val step = page.context.getString(R.string.pager_step, index + 1, PAGES)
            addView(startText(page, step, MalletTextRole.CAPTION, MalletTextTone.MUTED))
            addView(startText(page, page.str(titles[index]), MalletTextRole.HEADLINE), below(page, 3))
        }

    private fun contentPage(page: PageScope, index: Int): View =
        LinearLayout(page.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(page.dp(24), 0, page.dp(24), 0)
            val icon = MalletIconView(page.context).apply {
                icon = icons[index]
                tone = MalletTextTone.ACCENT
            }
            addView(icon, LinearLayout.LayoutParams(page.dp(40), page.dp(40)))
            val percent = (MalletPager.COMMIT_FRACTION * 100).roundToInt()
            val body = page.context.getString(bodies[index], percent)
            val label = page.text(body, MalletTextRole.BODY, MalletTextTone.MUTED).apply {
                gravity = Gravity.CENTER
            }
            addView(label, below(page, 14))
        }
}
