package io.github.avalonskyafar.sancai.showcase.pages

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconView
import io.github.avalonskyafar.sancai.interaction.SancaiPager
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiControlRow
import io.github.avalonskyafar.sancai.widget.SancaiPageIndicator
import io.github.avalonskyafar.sancai.widget.SancaiSwitch
import kotlin.math.roundToInt

/** Pager: a short flow with a linked heading and dots, moved by hand and from code. */
object PagerPage : DemoPage("i_pager", R.string.page_i_pager_title, R.string.page_i_pager_summary) {

    private const val PAGES = 4
    private const val CONTENT_DP = 220
    private const val HEADING_DP = 58

    private val titles = intArrayOf(
        R.string.pager_page1_title, R.string.pager_page2_title,
        R.string.pager_page3_title, R.string.pager_page4_title,
    )
    private val bodies = intArrayOf(
        R.string.pager_page1_body, R.string.pager_page2_body,
        R.string.pager_page3_body, R.string.pager_page4_body,
    )
    private val icons = arrayOf(SancaiIcon.HAND, SancaiIcon.REFRESH, SancaiIcon.LAYERS, SancaiIcon.SHIELD)

    override fun build(page: PageScope) {
        val context = page.context
        page.title()

        val heading = SancaiPager(context)
        val pager = SancaiPager(context)
        val indicator = SancaiPageIndicator(context)

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

        val status = page.text("", SancaiTextRole.BODY_COMPACT, SancaiTextTone.MUTED)
        val previous = SancaiButton(context).apply {
            text = page.str(R.string.pager_previous)
            icon = SancaiIcon.CHEVRON_LEFT
        }
        val next = SancaiButton(context).apply {
            text = page.str(R.string.pager_next)
            variant = SancaiButton.Variant.PRIMARY
        }
        val first = SancaiButton(context).apply {
            text = page.str(R.string.pager_first)
            variant = SancaiButton.Variant.TEXT
        }

        var reason = R.string.pager_status_rest
        fun refresh() {
            val current = pager.currentPage
            status.text = context.getString(reason, current + 1, PAGES)
            previous.isEnabled = current > 0
            next.isEnabled = current < PAGES - 1
            first.isEnabled = current > 0
        }
        val listener = object : SancaiPager.OnPageChangeListener {
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
            addView(status, below(page, 6).apply { marginStart = page.dp(8); marginEnd = page.dp(8) })
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(previous, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
            row.addView(next, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f).apply { marginStart = page.dp(8) })
            addView(row, below(page, 12))
            addView(first, below(page, 6))
            addDivider(6f)
            val swipe = SancaiSwitch(context).apply {
                setChecked(true, animate = false)
                onCheckedChangeListener = SancaiSwitch.OnCheckedChangeListener { _, checked ->
                    pager.isEnabled = checked
                    heading.isEnabled = checked
                    indicator.isEnabled = checked
                }
            }
            val swipeRow = SancaiControlRow(context).apply {
                title = page.str(R.string.pager_swipe_title)
                detail = page.str(R.string.pager_swipe_detail)
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

    private fun headingPage(page: PageScope, index: Int): View =
        LinearLayout(page.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(page.dp(8), 0, page.dp(8), 0)
            val step = page.context.getString(R.string.pager_step, index + 1, PAGES)
            addView(page.text(step, SancaiTextRole.CAPTION, SancaiTextTone.MUTED))
            addView(page.text(page.str(titles[index]), SancaiTextRole.HEADLINE), below(page, 3))
        }

    private fun contentPage(page: PageScope, index: Int): View =
        LinearLayout(page.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(page.dp(24), 0, page.dp(24), 0)
            val icon = SancaiIconView(page.context).apply {
                icon = icons[index]
                tone = SancaiTextTone.ACCENT
            }
            addView(icon, LinearLayout.LayoutParams(page.dp(40), page.dp(40)))
            val percent = (SancaiPager.COMMIT_FRACTION * 100).roundToInt()
            val body = page.context.getString(bodies[index], percent)
            val label = page.text(body, SancaiTextRole.BODY, SancaiTextTone.MUTED).apply {
                gravity = Gravity.CENTER
            }
            addView(label, below(page, 14))
        }
}
