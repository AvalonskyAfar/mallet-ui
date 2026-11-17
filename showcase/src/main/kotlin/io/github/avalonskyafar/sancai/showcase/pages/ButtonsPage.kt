package io.github.avalonskyafar.sancai.showcase.pages

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconView
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.widget.SancaiBadge
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiChip
import io.github.avalonskyafar.sancai.widget.SancaiChipGroup
import io.github.avalonskyafar.sancai.widget.SancaiControlRow
import io.github.avalonskyafar.sancai.widget.SancaiSwitch
import io.github.avalonskyafar.sancai.widget.SancaiTag
import io.github.avalonskyafar.sancai.widget.SancaiText

private const val BUTTON_GAP = 10
private const val ROW_GAP = 10
private const val BLOCK_GAP = 12
private const val DIVIDER_GAP = 14f
private const val BADGE_STEP = 5
private const val BELL_BOX = 44

/** Buttons, chips, tags and badges: the small capsules of the system. */
object ButtonsPage : DemoPage("c_buttons", R.string.page_c_buttons_title, R.string.page_c_buttons_summary) {
    override fun build(page: PageScope) {
        page.title()
        buttons(page)
        chips(page)
        tagsAndBadges(page)
        page.caption(page.str(R.string.buttons_note), topDp = 22)
    }
}

private fun match() = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP)

private fun below(page: PageScope, topDp: Int) = match().apply { topMargin = page.dp(topDp) }

/** A row of views that share the width equally. */
private fun pair(page: PageScope, vararg views: View): LinearLayout =
    LinearLayout(page.context).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { index, view ->
            val params = LinearLayout.LayoutParams(0, PageScope.WRAP, 1f)
            if (index > 0) params.marginStart = page.dp(BUTTON_GAP)
            addView(view, params)
        }
    }

private fun label(page: PageScope, res: Int): SancaiText =
    page.text(page.str(res), SancaiTextRole.CAPTION, SancaiTextTone.MUTED)

private fun buttons(page: PageScope) {
    val context = page.context
    val status = page.text(page.str(R.string.buttons_status_idle), SancaiTextRole.FOOTNOTE, SancaiTextTone.MUTED)
    var lastTapped: String? = null
    var loading = false

    fun refresh() {
        val tapped = lastTapped
        status.text = when {
            loading -> page.str(R.string.buttons_status_loading)
            tapped != null -> context.getString(R.string.buttons_status_tapped, tapped)
            else -> page.str(R.string.buttons_status_idle)
        }
    }

    fun button(labelRes: Int, look: SancaiButton.Variant, glyph: SancaiIcon? = null) =
        SancaiButton(context).apply {
            text = page.str(labelRes)
            variant = look
            icon = glyph
            setOnClickListener {
                lastTapped = page.str(labelRes)
                refresh()
            }
        }

    val neutral = button(R.string.buttons_neutral, SancaiButton.Variant.NEUTRAL)
    val primary = button(R.string.buttons_primary, SancaiButton.Variant.PRIMARY)
    val danger = button(R.string.buttons_danger, SancaiButton.Variant.DANGER, SancaiIcon.TRASH)
    val plain = button(R.string.buttons_text, SancaiButton.Variant.TEXT)
    val share = button(R.string.buttons_icon, SancaiButton.Variant.NEUTRAL, SancaiIcon.SHARE)
    val disabled = button(R.string.buttons_disabled, SancaiButton.Variant.NEUTRAL).apply { isEnabled = false }
    val live = listOf(neutral, primary, danger, plain, share)

    val toggle = SancaiSwitch(context)
    toggle.setOnCheckedChangeListener { _, isChecked ->
        loading = isChecked
        live.forEach { it.isLoading = isChecked }
        refresh()
    }

    page.section(R.string.buttons_capsule_title, R.string.buttons_capsule_detail) {
        addView(pair(page, neutral, primary), match())
        addView(pair(page, danger, plain), below(page, ROW_GAP))
        addView(pair(page, share, disabled), below(page, ROW_GAP))
        addView(status, below(page, BLOCK_GAP))
        addDivider(DIVIDER_GAP)
        val row = SancaiControlRow(context).apply {
            title = page.str(R.string.buttons_loading_title)
            detail = page.str(R.string.buttons_loading_detail)
            alignment = SancaiControlRow.Alignment.START
            setControl(toggle)
        }
        addView(row, match())
    }
    page.code("""
        val share = SancaiButton(this).apply {
            text = "Share"
            variant = SancaiButton.Variant.PRIMARY
            icon = SancaiIcon.SHARE
        }
        val busy = SancaiSwitch(this)
        busy.setOnCheckedChangeListener { _, isChecked ->
            share.isLoading = isChecked
        }
    """)
}

private fun chips(page: PageScope) {
    val context = page.context

    fun chip(labelRes: Int, role: SancaiChip.Kind, glyph: SancaiIcon? = null) = SancaiChip(context).apply {
        text = page.str(labelRes)
        kind = role
        icon = glyph
    }

    fun names(group: SancaiChipGroup, ids: List<Int>): String =
        ids.mapNotNull { id -> group.findViewById<SancaiChip>(id)?.text }
            .joinToString(page.str(R.string.buttons_list_separator))

    // Filter chips: any number can be checked.
    val filterStatus = page.text("", SancaiTextRole.FOOTNOTE, SancaiTextTone.MUTED)
    val filters = SancaiChipGroup(context)
    val filterLabels = listOf(
        R.string.buttons_filter_bowls, R.string.buttons_filter_vases, R.string.buttons_filter_figures,
        R.string.buttons_filter_tiles, R.string.buttons_filter_jars,
    )
    filterLabels.forEachIndexed { index, res ->
        filters.addView(chip(res, SancaiChip.Kind.FILTER).apply { isChecked = index == 0 || index == 2 })
    }
    fun showFilters(ids: List<Int>) {
        filterStatus.text =
            if (ids.isEmpty()) page.str(R.string.buttons_filter_none)
            else context.getString(R.string.buttons_filter_status, names(filters, ids))
    }
    filters.setOnCheckedStateChangeListener { _, ids -> showFilters(ids) }
    showFilters(filters.checkedChipIds)

    // Single selection in another glaze; the last checked chip cannot be unchecked.
    val rangeStatus = page.text("", SancaiTextRole.FOOTNOTE, SancaiTextTone.MUTED)
    val ranges = SancaiChipGroup(context).apply {
        isSingleSelection = true
        isSelectionRequired = true
    }
    val rangeLabels = listOf(
        R.string.buttons_single_day, R.string.buttons_single_week,
        R.string.buttons_single_month, R.string.buttons_single_year,
    )
    rangeLabels.forEachIndexed { index, res ->
        ranges.addView(chip(res, SancaiChip.Kind.FILTER).apply {
            glaze = page.theme.glazes.celadon
            isChecked = index == 1
        })
    }
    fun showRange(ids: List<Int>) {
        rangeStatus.text = context.getString(R.string.buttons_single_status, names(ranges, ids))
    }
    ranges.setOnCheckedStateChangeListener { _, ids -> showRange(ids) }
    showRange(ranges.checkedChipIds)

    // Action chips: pressed like small buttons.
    val actions = SancaiChipGroup(context)
    listOf(
        R.string.buttons_action_copy to SancaiIcon.COPY,
        R.string.buttons_action_share to SancaiIcon.SHARE,
        R.string.buttons_action_refresh to SancaiIcon.REFRESH,
    ).forEach { (res, glyph) ->
        actions.addView(chip(res, SancaiChip.Kind.ACTION, glyph).apply {
            setOnClickListener { page.host.say(context.getString(R.string.buttons_action_said, page.str(res))) }
        })
    }
    actions.addView(chip(R.string.buttons_disabled, SancaiChip.Kind.ACTION).apply { isEnabled = false })

    page.section(R.string.buttons_chips_title, R.string.buttons_chips_detail) {
        addView(label(page, R.string.buttons_filter_label), match())
        addView(filters, below(page, 4))
        addView(filterStatus, below(page, 4))
        addDivider(DIVIDER_GAP)
        addView(label(page, R.string.buttons_single_label), match())
        addView(ranges, below(page, 4))
        addView(rangeStatus, below(page, 4))
        addDivider(DIVIDER_GAP)
        addView(label(page, R.string.buttons_action_label), match())
        addView(actions, below(page, 4))
    }
    page.code("""
        val group = SancaiChipGroup(this)
        group.isSingleSelection = true
        group.addView(SancaiChip(this).apply {
            text = "Week"
            kind = SancaiChip.Kind.FILTER
        })
        group.setOnCheckedStateChangeListener { _, ids ->
            title = "${'$'}{ids.size} checked"
        }
    """)
}

private fun tagsAndBadges(page: PageScope) {
    val context = page.context

    // A chip group wraps whatever it holds, so the tags move to a second line when they must.
    val tags = SancaiChipGroup(context).apply {
        listOf(
            R.string.buttons_tag_positive to SancaiTag.Tone.POSITIVE,
            R.string.buttons_tag_caution to SancaiTag.Tone.CAUTION,
            R.string.buttons_tag_negative to SancaiTag.Tone.NEGATIVE,
            R.string.buttons_tag_info to SancaiTag.Tone.INFO,
            R.string.buttons_tag_neutral to SancaiTag.Tone.NEUTRAL,
        ).forEach { (res, look) ->
            addView(SancaiTag(context).apply {
                text = page.str(res)
                tone = look
            })
        }
    }

    /** One standalone badge above the remark that says what it was given. */
    fun sample(count: Int, dot: Boolean, remark: Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val badge = SancaiBadge(context).apply {
            showDot = dot
            this.count = count
        }
        val slot = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            minimumHeight = page.dp(24)
            addView(badge, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP))
        }
        addView(slot, match())
        val note = label(page, remark).apply { gravity = Gravity.CENTER_HORIZONTAL }
        addView(note, below(page, 4))
    }
    val samples = pair(
        page,
        sample(0, true, R.string.buttons_badge_zero),
        sample(5, false, R.string.buttons_badge_five),
        sample(120, false, R.string.buttons_badge_many),
    )

    // A badge attached to an icon, driven by the two buttons under it.
    val bell = SancaiIconView(context).apply { icon = SancaiIcon.BELL }
    val attached = SancaiBadge.attach(bell)
    val unreadStatus = page.text("", SancaiTextRole.FOOTNOTE, SancaiTextTone.MUTED)
    var unread = BADGE_STEP
    fun showUnread() {
        attached.count = unread
        bell.contentDescription = context.resources.getQuantityString(R.plurals.buttons_badge_bell, unread, unread)
        unreadStatus.text =
            if (attached.isShowing) context.getString(R.string.buttons_badge_status, unread, attached.text)
            else page.str(R.string.buttons_badge_status_none)
    }
    showUnread()
    val add = SancaiButton(context).apply {
        text = page.str(R.string.buttons_badge_add)
        icon = SancaiIcon.PLUS
        setOnClickListener {
            unread += BADGE_STEP
            showUnread()
        }
    }
    val clear = SancaiButton(context).apply {
        text = page.str(R.string.buttons_badge_clear)
        variant = SancaiButton.Variant.TEXT
        setOnClickListener {
            unread = 0
            showUnread()
        }
    }
    val bellRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(bell, LinearLayout.LayoutParams(page.dp(BELL_BOX), page.dp(BELL_BOX)))
        val params = LinearLayout.LayoutParams(0, PageScope.WRAP, 1f)
        params.marginStart = page.dp(BLOCK_GAP)
        addView(unreadStatus, params)
    }

    page.section(R.string.buttons_tags_title, R.string.buttons_tags_detail) {
        addView(tags, match())
        addDivider(DIVIDER_GAP)
        addView(samples, match())
        addDivider(DIVIDER_GAP)
        addView(bellRow, match())
        addView(pair(page, add, clear), below(page, ROW_GAP))
    }
    page.code("""
        val tag = SancaiTag(this)
        tag.text = "Passed"
        tag.tone = SancaiTag.Tone.POSITIVE
        val bell = SancaiIconView(this)
        bell.icon = SancaiIcon.BELL
        val badge = SancaiBadge.attach(bell)
        badge.count = 120
    """)
}
