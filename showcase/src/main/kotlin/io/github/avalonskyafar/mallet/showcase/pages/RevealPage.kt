package io.github.avalonskyafar.mallet.showcase.pages

import android.view.Gravity
import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconView
import io.github.avalonskyafar.mallet.interaction.MalletExpandableLayout
import io.github.avalonskyafar.mallet.interaction.MalletRevealLayout
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.widget.MalletButton
import io.github.avalonskyafar.mallet.widget.MalletControlRow
import io.github.avalonskyafar.mallet.widget.MalletSurface
import io.github.avalonskyafar.mallet.widget.MalletSwitch

/** Reveal and expand: rows that slide aside for one action, sections that open in place. */
object RevealPage : DemoPage("i_reveal", R.string.page_i_reveal_title, R.string.page_i_reveal_summary) {
    override fun build(page: PageScope) {
        page.title()
        page.swipeSection()
        page.endEdgeSection()
        page.expandSection()
        page.caption(page.str(R.string.reveal_note), topDp = 22)
    }
}

/** One rule of the list: its name and whether its switch starts on. */
private class Rule(val name: Int, val on: Boolean)

private val RULES = listOf(
    Rule(R.string.reveal_rule_ads, true),
    Rule(R.string.reveal_rule_trackers, true),
    Rule(R.string.reveal_rule_popups, false),
    Rule(R.string.reveal_rule_autoplay, true),
)

/** A control row with a switch, wrapped so that it slides aside. */
private fun PageScope.switchRow(title: String, on: Boolean, onChange: () -> Unit = {}): MalletRevealLayout {
    val toggle = MalletSwitch(context).apply {
        isChecked = on
        setOnCheckedChangeListener { _, _ -> onChange() }
    }
    val row = MalletControlRow(context).apply {
        this.title = title
        setControl(toggle)
    }
    return MalletRevealLayout(context).apply {
        addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
    }
}

private val MalletRevealLayout.isSwitchedOn: Boolean
    get() = ((row as? MalletControlRow)?.control as? MalletSwitch)?.isChecked == true

/** Four rows that slide towards the end side; Delete removes a row, the button brings all back. */
private fun PageScope.swipeSection() {
    val rows = ArrayList<MalletRevealLayout>()
    val status = text("", MalletTextRole.CAPTION, MalletTextTone.MUTED)
    val empty = text(str(R.string.reveal_empty), MalletTextRole.BODY, MalletTextTone.MUTED).apply {
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(18), dp(8), dp(18))
    }
    lateinit var card: MalletSurface

    fun refresh() {
        status.text = context.getString(R.string.reveal_status, rows.size, rows.count { it.isSwitchedOn })
    }

    // Lays the remaining rows into the card again, with a divider between two rows.
    fun relay() {
        card.removeAllViews()
        rows.forEachIndexed { index, row ->
            if (index > 0) card.addDivider()
            card.addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
        }
        if (rows.isEmpty()) card.addView(empty, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
        refresh()
    }

    fun fill() {
        rows.clear()
        for (rule in RULES) {
            val name = str(rule.name)
            val row = switchRow(name, rule.on) { refresh() }
            row.setAction(str(R.string.reveal_delete), MalletTextTone.DANGER, MalletIcon.TRASH) {
                rows.remove(row)
                relay()
                host.say(context.getString(R.string.reveal_removed, name))
            }
            rows.add(row)
        }
        relay()
    }

    card = section(R.string.reveal_swipe_title, R.string.reveal_swipe_detail) {}
    fill()

    val restore = MalletButton(context).apply {
        variant = MalletButton.Variant.TEXT
        text = str(R.string.reveal_restore)
        setOnClickListener {
            fill()
            host.say(str(R.string.reveal_restored))
        }
    }
    val foot = LinearLayout(context).apply {
        setGravity(Gravity.CENTER_VERTICAL)
        addView(status, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
        addView(restore, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP))
    }
    loose(foot, topDp = 6, sideDp = PageScope.TEXT_INSET)
    code("""
        val row = MalletRevealLayout(context)
        row.addView(controlRow)
        row.setAction("Delete", MalletTextTone.DANGER, MalletIcon.TRASH) {
            card.removeView(row)
        }
        card.addView(row)
    """)
}

/** A short card whose action waits at the end side; the second row starts open. */
private fun PageScope.endEdgeSection() {
    val names = listOf(R.string.reveal_site_news, R.string.reveal_site_video)
    section(R.string.reveal_end_title, R.string.reveal_end_detail) {
        names.forEachIndexed { index, res ->
            val name = str(res)
            val row = switchRow(name, index == 0)
            row.revealEdge = MalletRevealLayout.Edge.END
            row.setAction(str(R.string.reveal_pin), MalletTextTone.ACCENT, MalletIcon.STAR) {
                row.close()
                host.say(context.getString(R.string.reveal_pinned, name))
            }
            row.setOnOpenChangeListener { _, open ->
                if (open) host.say(context.getString(R.string.reveal_opened, name))
            }
            if (index > 0) addDivider()
            addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            if (index == 1) row.open(animate = false)
        }
    }
    code("""
        row.revealEdge = MalletRevealLayout.Edge.END
        row.setAction("Pin", MalletTextTone.ACCENT, MalletIcon.STAR) {
            row.close()
        }
        row.open(animate = false)
        val open = row.isOpen
    """)
}

/** Three sections in one card; the first starts expanded. */
private fun PageScope.expandSection() {
    val topics = listOf(
        R.string.reveal_topic_what to R.string.reveal_topic_what_body,
        R.string.reveal_topic_motion to R.string.reveal_topic_motion_body,
        R.string.reveal_topic_keys to R.string.reveal_topic_keys_body,
    )
    val sections = ArrayList<MalletExpandableLayout>()
    val status = text("", MalletTextRole.CAPTION, MalletTextTone.MUTED)
    fun refresh() {
        status.text = context.getString(R.string.reveal_expand_status, sections.count { it.isExpanded }, sections.size)
    }
    section(R.string.reveal_expand_title, R.string.reveal_expand_detail) {
        topics.forEachIndexed { index, (title, body) ->
            val chevron = MalletIconView(context).apply {
                icon = MalletIcon.CHEVRON_DOWN
                tone = MalletTextTone.MUTED
            }
            val header = LinearLayout(context).apply {
                setGravity(Gravity.CENTER_VERTICAL)
                minimumHeight = dp(52)
                setPaddingRelative(dp(10), dp(6), dp(8), dp(6))
                addView(text(str(title), MalletTextRole.LABEL), LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
                addView(chevron, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP))
            }
            val paragraph = text(str(body), MalletTextRole.BODY, MalletTextTone.MUTED).apply {
                setPaddingRelative(dp(10), 0, dp(10), dp(14))
            }
            val expandable = MalletExpandableLayout(context).apply {
                addView(header, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
                addView(paragraph, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
                setIndicator(chevron)
                setExpanded(index == 0, animate = false)
                setOnExpansionChangeListener { _, _ -> refresh() }
            }
            sections.add(expandable)
            if (index > 0) addDivider()
            addView(expandable, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
        }
    }
    refresh()
    loose(status, topDp = 8, sideDp = PageScope.TEXT_INSET)
    code("""
        val section = MalletExpandableLayout(context)
        section.addView(header)
        section.addView(paragraph)
        section.setIndicator(chevron)
        section.setExpanded(true, animate = false)
        section.toggle()
        val open = section.isExpanded
    """)
}
