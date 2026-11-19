package io.github.avalonskyafar.mallet.showcase.pages

import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconView
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.widget.MalletBadge
import io.github.avalonskyafar.mallet.widget.MalletButton
import io.github.avalonskyafar.mallet.widget.MalletChip
import io.github.avalonskyafar.mallet.widget.MalletChipGroup
import io.github.avalonskyafar.mallet.widget.MalletControlRow
import io.github.avalonskyafar.mallet.widget.MalletSwitch
import io.github.avalonskyafar.mallet.widget.MalletTag
import io.github.avalonskyafar.mallet.widget.MalletText

private const val BUTTON_GAP = 10
private const val ROW_GAP = 10
private const val BLOCK_GAP = 12
private const val DIVIDER_GAP = 14f
private const val ROW_TOP_GAP = 5
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

/**
 * Muted text inside a card. It keeps to the start edge whatever language it is written in,
 * so that it lines up with the chips and buttons it belongs to right to left too.
 */
private fun line(page: PageScope, value: String, role: MalletTextRole): MalletText =
    page.text(value, role, MalletTextTone.MUTED).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START }

/** The label above a block of a card. */
private fun label(page: PageScope, res: Int): MalletText = line(page, page.str(res), MalletTextRole.CAPTION)

/** The line of a block that says what its listener heard. */
private fun statusLine(page: PageScope, value: String = ""): MalletText = line(page, value, MalletTextRole.FOOTNOTE)

private fun buttons(page: PageScope) {
    val context = page.context
    val status = statusLine(page, page.str(R.string.buttons_status_idle))
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

    fun button(labelRes: Int, look: MalletButton.Variant, glyph: MalletIcon? = null) =
        MalletButton(context).apply {
            text = page.str(labelRes)
            variant = look
            icon = glyph
            setOnClickListener {
                lastTapped = page.str(labelRes)
                refresh()
            }
        }

    val neutral = button(R.string.buttons_neutral, MalletButton.Variant.NEUTRAL)
    val primary = button(R.string.buttons_primary, MalletButton.Variant.PRIMARY)
    val danger = button(R.string.buttons_danger, MalletButton.Variant.DANGER, MalletIcon.TRASH)
    val plain = button(R.string.buttons_text, MalletButton.Variant.TEXT)
    val share = button(R.string.buttons_icon, MalletButton.Variant.NEUTRAL, MalletIcon.SHARE)
    val disabled = button(R.string.buttons_disabled, MalletButton.Variant.NEUTRAL).apply { isEnabled = false }
    val live = listOf(neutral, primary, danger, plain, share)

    val toggle = MalletSwitch(context)
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
        // The row brings its own air. With less under the divider than above it, the switch
        // stands half way between the divider and the rim of the card.
        val rule = addDivider(DIVIDER_GAP)
        (rule.layoutParams as LinearLayout.LayoutParams).bottomMargin = page.dp(ROW_TOP_GAP)
        val row = MalletControlRow(context).apply {
            title = page.str(R.string.buttons_loading_title)
            detail = page.str(R.string.buttons_loading_detail)
            alignment = MalletControlRow.Alignment.START
            setControl(toggle)
        }
        addView(row, match())
    }
    page.code("""
        val share = MalletButton(this).apply {
            text = "Share"
            variant = MalletButton.Variant.PRIMARY
            icon = MalletIcon.SHARE
        }
        val busy = MalletSwitch(this)
        busy.setOnCheckedChangeListener { _, isChecked ->
            share.isLoading = isChecked
        }
    """)
}

private fun chips(page: PageScope) {
    val context = page.context

    fun chip(labelRes: Int, role: MalletChip.Kind, glyph: MalletIcon? = null) = MalletChip(context).apply {
        text = page.str(labelRes)
        kind = role
        icon = glyph
    }

    fun names(group: MalletChipGroup, ids: List<Int>): String =
        ids.mapNotNull { id -> group.findViewById<MalletChip>(id)?.text }
            .joinToString(page.str(R.string.buttons_list_separator))

    // Filter chips: any number can be checked.
    val filterStatus = statusLine(page)
    val filters = MalletChipGroup(context)
    val filterLabels = listOf(
        R.string.buttons_filter_bowls, R.string.buttons_filter_vases, R.string.buttons_filter_figures,
        R.string.buttons_filter_tiles, R.string.buttons_filter_jars,
    )
    filterLabels.forEachIndexed { index, res ->
        filters.addView(chip(res, MalletChip.Kind.FILTER).apply { isChecked = index == 0 || index == 2 })
    }
    fun showFilters(ids: List<Int>) {
        filterStatus.text =
            if (ids.isEmpty()) page.str(R.string.buttons_filter_none)
            else context.getString(R.string.buttons_filter_status, names(filters, ids))
    }
    filters.setOnCheckedStateChangeListener { _, ids -> showFilters(ids) }
    showFilters(filters.checkedChipIds)

    // Single selection in another hue; the last checked chip cannot be unchecked.
    val rangeStatus = statusLine(page)
    val ranges = MalletChipGroup(context).apply {
        isSingleSelection = true
        isSelectionRequired = true
    }
    val rangeLabels = listOf(
        R.string.buttons_single_day, R.string.buttons_single_week,
        R.string.buttons_single_month, R.string.buttons_single_year,
    )
    rangeLabels.forEachIndexed { index, res ->
        ranges.addView(chip(res, MalletChip.Kind.FILTER).apply {
            hue = page.theme.hues.jade
            isChecked = index == 1
        })
    }
    fun showRange(ids: List<Int>) {
        rangeStatus.text = context.getString(R.string.buttons_single_status, names(ranges, ids))
    }
    ranges.setOnCheckedStateChangeListener { _, ids -> showRange(ids) }
    showRange(ranges.checkedChipIds)

    // Action chips: pressed like small buttons.
    val actions = MalletChipGroup(context)
    listOf(
        R.string.buttons_action_copy to MalletIcon.COPY,
        R.string.buttons_action_share to MalletIcon.SHARE,
        R.string.buttons_action_refresh to MalletIcon.REFRESH,
    ).forEach { (res, glyph) ->
        actions.addView(chip(res, MalletChip.Kind.ACTION, glyph).apply {
            setOnClickListener { page.host.say(context.getString(R.string.buttons_action_said, page.str(res))) }
        })
    }
    actions.addView(chip(R.string.buttons_disabled, MalletChip.Kind.ACTION).apply { isEnabled = false })

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
        val group = MalletChipGroup(this)
        group.isSingleSelection = true
        group.addView(MalletChip(this).apply {
            text = "Week"
            kind = MalletChip.Kind.FILTER
        })
        group.setOnCheckedStateChangeListener { _, ids ->
            title = "${'$'}{ids.size} checked"
        }
    """)
}

private fun tagsAndBadges(page: PageScope) {
    val context = page.context

    // A chip group wraps whatever it holds, so the tags move to a second line when they must.
    val tags = MalletChipGroup(context).apply {
        listOf(
            R.string.buttons_tag_positive to MalletTag.Tone.POSITIVE,
            R.string.buttons_tag_caution to MalletTag.Tone.CAUTION,
            R.string.buttons_tag_negative to MalletTag.Tone.NEGATIVE,
            R.string.buttons_tag_info to MalletTag.Tone.INFO,
            R.string.buttons_tag_neutral to MalletTag.Tone.NEUTRAL,
        ).forEach { (res, look) ->
            addView(MalletTag(context).apply {
                text = page.str(res)
                tone = look
            })
        }
    }

    /** One standalone badge above the remark that says what it was given. */
    fun sample(count: Int, dot: Boolean, remark: Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val badge = MalletBadge(context).apply {
            showDot = dot
            this.count = count
        }
        val slot = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            minimumHeight = page.dp(24)
            addView(badge, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP))
        }
        addView(slot, match())
        val note = label(page, remark).apply {
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            // The remarks stand side by side in columns of their own. With the taller line of a
            // fallback font, one that holds digits only would stand higher than its neighbours.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isFallbackLineSpacing = false
        }
        addView(note, below(page, 4))
    }
    val samples = pair(
        page,
        sample(0, true, R.string.buttons_badge_zero),
        sample(5, false, R.string.buttons_badge_five),
        sample(120, false, R.string.buttons_badge_many),
    )

    // A badge attached to an icon, driven by the two buttons under it.
    val bell = MalletIconView(context).apply { icon = MalletIcon.BELL }
    val attached = MalletBadge.attach(bell)
    val unreadStatus = statusLine(page)
    var unread = BADGE_STEP
    fun showUnread() {
        attached.count = unread
        bell.contentDescription = context.resources.getQuantityString(R.plurals.buttons_badge_bell, unread, unread)
        unreadStatus.text =
            if (attached.isShowing) context.getString(R.string.buttons_badge_status, unread, attached.text)
            else page.str(R.string.buttons_badge_status_none)
    }
    showUnread()
    val add = MalletButton(context).apply {
        text = page.str(R.string.buttons_badge_add)
        icon = MalletIcon.PLUS
        setOnClickListener {
            unread += BADGE_STEP
            showUnread()
        }
    }
    val clear = MalletButton(context).apply {
        text = page.str(R.string.buttons_badge_clear)
        variant = MalletButton.Variant.TEXT
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
        val tag = MalletTag(this)
        tag.text = "Passed"
        tag.tone = MalletTag.Tone.POSITIVE
        val bell = MalletIconView(this)
        bell.icon = MalletIcon.BELL
        val badge = MalletBadge.attach(bell)
        badge.count = 120
    """)
}
