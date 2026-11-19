package io.github.avalonskyafar.mallet.showcase.pages

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import io.github.avalonskyafar.mallet.interaction.MalletPager
import io.github.avalonskyafar.mallet.interaction.MalletRevealLayout
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.widget.MalletEntryRow
import io.github.avalonskyafar.mallet.widget.MalletPageIndicator
import io.github.avalonskyafar.mallet.widget.MalletSegmentedTrack
import io.github.avalonskyafar.mallet.widget.MalletSurface
import io.github.avalonskyafar.mallet.widget.MalletText
import kotlin.math.roundToInt

/**
 * Who owns the gesture: a track, a reveal row and a pager stacked in one card of a page that
 * scrolls and that itself follows the finger to its neighbour, then the rules in words.
 */
object ArbitratePage : DemoPage("i_arbitrate", R.string.page_i_arbitrate_title, R.string.page_i_arbitrate_summary) {

    private const val INSET = 8

    /** Inset of the lines of the first card: the text of an entry row starts 14 dp in. */
    private const val STACK_INSET = 14

    /** Height of the pager in dp at the normal size of text. */
    private const val PAGER_HEIGHT = 96
    private const val PAGER_PAGES = 3

    override fun build(page: PageScope) {
        page.title()
        stack(page)
        rules(page)
        owners(page)
        page.caption(page.str(R.string.arbitrate_origin), topDp = 18)
    }

    /**
     * The three controls in one card. The row that slides aside lies between two full-bleed
     * dividers, so its cell reaches from rim to rim and from divider to divider; what a drag
     * does to it stands in its own detail line. The track and the pager carry a line under
     * them, and the last line of the card names the last owner.
     */
    private fun stack(page: PageScope) {
        val status = page.text(page.str(R.string.arbitrate_status_idle), MalletTextRole.CAPTION, MalletTextTone.ACCENT)
        page.section(R.string.arbitrate_stack_title, R.string.arbitrate_stack_detail) {
            val track = MalletSegmentedTrack(context).apply {
                setOptions(
                    page.str(R.string.arbitrate_level_off),
                    page.str(R.string.arbitrate_level_gentle),
                    page.str(R.string.arbitrate_level_standard),
                    page.str(R.string.arbitrate_level_strict),
                )
                select(1, animate = false)
                setOnSelectionChangeListener { t, index ->
                    status.text = page.context.getString(R.string.arbitrate_status_track, t.options[index])
                }
            }
            addView(track, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            addView(line(page, R.string.arbitrate_track_line))
            addDivider(6f)

            addView(revealRow(page, status), LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            addDivider(6f)

            pager(page, this, status)
            addView(line(page, R.string.arbitrate_pager_line))
            addDivider(6f)
            addView(status.apply { setPadding(page.dp(STACK_INSET), page.dp(5), page.dp(STACK_INSET), page.dp(5)) })
        }
        page.code("""
            card.addView(MalletSegmentedTrack(context))
            card.addDivider(6f)
            card.addView(MalletRevealLayout(context).apply {
                addView(row)
                setAction("Delete") { close() }
            })
            card.addDivider(6f)
            card.addView(MalletPager(context).apply {
                setPages(3) { i, _ -> pageView(i) }
            })
        """)
    }

    /** A muted line inside the card, aligned with the text of the rows. */
    private fun line(page: PageScope, @StringRes text: Int): MalletText =
        page.text(page.str(text), MalletTextRole.CAPTION, MalletTextTone.MUTED).apply {
            setPadding(page.dp(STACK_INSET), page.dp(5), page.dp(STACK_INSET), page.dp(5))
        }

    private fun revealRow(page: PageScope, status: MalletText): MalletRevealLayout {
        val row = MalletEntryRow(page.context).apply {
            title = page.str(R.string.arbitrate_row_title)
            detail = page.str(R.string.arbitrate_row_detail)
            arrow = MalletEntryRow.Arrow.NONE
            alignment = MalletEntryRow.Alignment.START
        }
        return MalletRevealLayout(page.context).apply {
            addView(row)
            setAction(page.str(R.string.arbitrate_row_action)) {
                close()
                page.host.say(page.str(R.string.arbitrate_row_said))
            }
            setOnOpenChangeListener { _, open ->
                status.text = page.str(if (open) R.string.arbitrate_status_row_open else R.string.arbitrate_status_row_closed)
            }
        }
    }

    /**
     * The pager lies in the card like a row: as wide as the content, between two dividers,
     * with its dots under it. Its pages are text, so it grows with the size of the text.
     */
    private fun pager(page: PageScope, card: MalletSurface, status: MalletText) {
        val pager = MalletPager(page.context)
        pager.setPages(PAGER_PAGES) { index, _ -> pagerPage(page, index) }
        pager.addOnPageChangeListener(object : MalletPager.OnPageChangeListener {
            override fun onPageSelected(index: Int) {
                status.text = page.context.getString(R.string.arbitrate_status_pager, index + 1, PAGER_PAGES)
            }
        })
        val textScale = page.theme.textScale * page.context.resources.configuration.fontScale
        val height = (page.dp(PAGER_HEIGHT) * textScale).roundToInt()
        card.addView(pager, LinearLayout.LayoutParams(PageScope.MATCH, height))
        val dots = MalletPageIndicator(page.context).apply { attachTo(pager) }
        val dotParams = LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP)
        dotParams.gravity = Gravity.CENTER_HORIZONTAL
        card.addView(dots, dotParams)
    }

    private fun pagerPage(page: PageScope, index: Int): View {
        val hints = intArrayOf(R.string.arbitrate_page_first, R.string.arbitrate_page_middle, R.string.arbitrate_page_last)
        return LinearLayout(page.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(page.dp(INSET), 0, page.dp(INSET), 0)
            val name = page.context.getString(R.string.arbitrate_page_name, index + 1, PAGER_PAGES)
            addView(page.text(name, MalletTextRole.LABEL).apply { gravity = Gravity.CENTER })
            val hint = page.text(page.str(hints[index]), MalletTextRole.CAPTION, MalletTextTone.MUTED)
            hint.gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP)
            params.topMargin = page.dp(4)
            addView(hint, params)
        }
    }

    /** The rules in words: long enough that the page scrolls under the card above. */
    private fun rules(page: PageScope) {
        val paragraphs = intArrayOf(
            R.string.arbitrate_rule_wait,
            R.string.arbitrate_rule_inner,
            R.string.arbitrate_rule_scroll,
            R.string.arbitrate_rule_own,
        )
        page.section(R.string.arbitrate_rules_title) {
            paragraphs.forEachIndexed { index, res ->
                if (index > 0) addDivider(4f)
                val body = page.text(page.str(res), MalletTextRole.BODY_COMPACT)
                body.setPadding(page.dp(INSET), page.dp(7), page.dp(INSET), page.dp(7))
                addView(body, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            }
        }
        page.code("""
            class Dial(context: Context) : View(context),
                MalletHorizontalDragOwner
        """)
    }

    /** Two columns of text: where the finger lands, and who owns the drag. */
    private fun owners(page: PageScope) {
        val rows = arrayOf(
            R.string.arbitrate_land_control to R.string.arbitrate_owner_control,
            R.string.arbitrate_land_row to R.string.arbitrate_owner_row,
            R.string.arbitrate_land_pager to R.string.arbitrate_owner_pager,
            R.string.arbitrate_land_pager_end to R.string.arbitrate_owner_pager_end,
            R.string.arbitrate_land_content to R.string.arbitrate_owner_content,
            R.string.arbitrate_land_edge to R.string.arbitrate_owner_edge,
        )
        page.section(R.string.arbitrate_table_title) {
            addView(tableRow(page, R.string.arbitrate_table_lands, R.string.arbitrate_table_owner, header = true))
            rows.forEach { (lands, owner) ->
                addDivider()
                addView(tableRow(page, lands, owner, header = false))
            }
        }
        page.caption(page.str(R.string.arbitrate_edge_note))
        page.code("""
            val edgeDp = MalletSpatialHost.EDGE_DP
        """)
    }

    private fun tableRow(page: PageScope, @StringRes lands: Int, @StringRes owner: Int, header: Boolean): View =
        LinearLayout(page.context).apply {
            orientation = LinearLayout.HORIZONTAL
            val vertical = page.dp(if (header) 6 else 9)
            setPadding(page.dp(INSET), vertical, page.dp(INSET), vertical)
            val role = if (header) MalletTextRole.CAPTION else MalletTextRole.BODY_COMPACT
            val first = page.text(page.str(lands), role, if (header) MalletTextTone.MUTED else MalletTextTone.INK)
            val second = page.text(page.str(owner), role, MalletTextTone.MUTED)
            addView(first, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
            val params = LinearLayout.LayoutParams(0, PageScope.WRAP, 1f)
            params.marginStart = page.dp(12)
            addView(second, params)
        }
}
