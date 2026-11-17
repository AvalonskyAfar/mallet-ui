package io.github.avalonskyafar.sancai.showcase.pages

import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.widget.SancaiControlRow
import io.github.avalonskyafar.sancai.widget.SancaiEntryRow
import io.github.avalonskyafar.sancai.widget.SancaiHeading
import io.github.avalonskyafar.sancai.widget.SancaiKeyValueList
import io.github.avalonskyafar.sancai.widget.SancaiLinkRow
import io.github.avalonskyafar.sancai.widget.SancaiSlider
import io.github.avalonskyafar.sancai.widget.SancaiStepper
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiSwitch
import kotlin.math.roundToInt

/**
 * Rows and lists: the two heading levels, entry rows, control rows, link rows and a list of
 * facts. The facts at the end of the page follow the control rows above them.
 */
object RowsPage : DemoPage("c_rows", R.string.page_c_rows_title, R.string.page_c_rows_summary) {
    override fun build(page: PageScope) {
        page.title()
        headings(page)
        entries(page)
        val facts = SancaiKeyValueList(page.context)
        controls(page, facts)
        links(page)
        page.loose(
            page.text(page.str(R.string.rows_facts_title), SancaiTextRole.HEADING),
            topDp = PageScope.SECTION_GAP, sideDp = PageScope.TEXT_INSET,
        )
        page.caption(page.str(R.string.rows_facts_detail), topDp = 4)
        page.loose(facts, topDp = PageScope.HEADING_GAP)
        page.code("""
            val facts = SancaiKeyValueList(context)
            facts.addItem("Density", "Regular")
            facts.addItem(
                "Sync", "Paused",
                SancaiTextTone.WARNING)
            facts.addItem(
                "Brightness", "60%",
                SancaiTextTone.MUTED)
        """)
        page.caption(page.str(R.string.rows_note), topDp = 22)
    }

    private fun headings(page: PageScope) {
        page.section(R.string.rows_headings_title, R.string.rows_headings_detail) {
            row(SancaiHeading(context).apply {
                level = SancaiHeading.Level.PAGE
                title = page.str(R.string.rows_heading_page)
                detail = page.str(R.string.rows_heading_page_detail)
            })
            addDivider(12f)
            addView(SancaiHeading(context).apply {
                title = page.str(R.string.rows_heading_section)
                detail = page.str(R.string.rows_heading_section_detail)
            }, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
        }
        page.code("""
            val heading = SancaiHeading(context)
            heading.level = SancaiHeading.Level.PAGE
            heading.title = "Display"
            heading.detail = "How the screen looks"
        """)
    }

    private fun entries(page: PageScope) {
        val languages = page.context.resources.getStringArray(R.array.rows_languages)
        var language = 0
        page.section(R.string.rows_entries_title, R.string.rows_entries_detail) {
            row(SancaiEntryRow(context).apply {
                title = page.str(R.string.rows_entry_end)
                detail = page.str(R.string.rows_entry_end_detail)
                arrow = SancaiEntryRow.Arrow.END
                setOnClickListener { page.host.say(page.str(R.string.rows_say_end)) }
            })
            row(SancaiEntryRow(context).apply {
                title = page.str(R.string.rows_entry_start)
                arrow = SancaiEntryRow.Arrow.START
                setOnClickListener { page.host.say(page.str(R.string.rows_say_start)) }
            })
            row(SancaiEntryRow(context).apply {
                title = page.str(R.string.rows_entry_icon)
                leadingIcon = SancaiIcon.BELL
                arrow = SancaiEntryRow.Arrow.END
                setOnClickListener { page.host.say(page.str(R.string.rows_say_icon)) }
            })
            row(SancaiEntryRow(context).apply {
                title = page.str(R.string.rows_entry_value)
                detail = page.str(R.string.rows_entry_value_detail)
                value = languages[language]
                arrow = SancaiEntryRow.Arrow.END
                setOnClickListener {
                    language = (language + 1) % languages.size
                    value = languages[language]
                }
            })
            row(SancaiEntryRow(context).apply {
                title = page.str(R.string.rows_entry_aligned)
                detail = page.str(R.string.rows_entry_aligned_detail)
                alignment = SancaiEntryRow.Alignment.START
                leadingIcon = SancaiIcon.SHIELD
                arrow = SancaiEntryRow.Arrow.END
                setOnClickListener { page.host.say(page.str(R.string.rows_say_aligned)) }
            })
            row(SancaiEntryRow(context).apply {
                title = page.str(R.string.rows_entry_disabled)
                value = page.str(R.string.rows_entry_disabled_value)
                arrow = SancaiEntryRow.Arrow.END
                isEnabled = false
            })
        }
        page.code("""
            val row = SancaiEntryRow(context)
            row.title = "Language"
            row.value = "English"
            row.leadingIcon = SancaiIcon.BELL
            row.arrow = SancaiEntryRow.Arrow.END
            row.alignment =
                SancaiEntryRow.Alignment.START
            row.setOnClickListener { row.value = "中文" }
        """)
    }

    private fun controls(page: PageScope, facts: SancaiKeyValueList) {
        val sync = page.context.resources.getStringArray(R.array.rows_sync_options)
        val density = page.context.resources.getStringArray(R.array.rows_density_options)
        val strength = page.context.resources.getStringArray(R.array.rows_strength_options)
        var syncAt = 0
        var densityAt = 1
        var strengthAt = 2
        var haptics = true
        var columns = 3f
        var brightness = 60f

        fun refresh() {
            val on = page.str(R.string.rows_on)
            val off = page.str(R.string.rows_off)
            facts.setItems(listOf(
                SancaiKeyValueList.Item(
                    page.str(R.string.rows_sync), sync[syncAt],
                    if (syncAt == 0) SancaiTextTone.WARNING else SancaiTextTone.SUCCESS,
                ),
                SancaiKeyValueList.Item(page.str(R.string.rows_density), density[densityAt]),
                SancaiKeyValueList.Item(
                    page.str(R.string.rows_strength), strength[strengthAt],
                    if (strengthAt == 3) SancaiTextTone.DANGER else SancaiTextTone.ACCENT,
                ),
                SancaiKeyValueList.Item(
                    page.str(R.string.rows_haptics), if (haptics) on else off,
                    if (haptics) SancaiTextTone.INK else SancaiTextTone.MUTED,
                ),
                SancaiKeyValueList.Item(page.str(R.string.rows_columns), columns.roundToInt().toString()),
                SancaiKeyValueList.Item(
                    page.str(R.string.rows_brightness),
                    page.context.getString(R.string.rows_percent, brightness.roundToInt()),
                ),
                SancaiKeyValueList.Item(
                    page.str(R.string.rows_locked), page.str(R.string.rows_locked_value),
                    SancaiTextTone.MUTED,
                ),
            ))
        }

        page.section(R.string.rows_controls_title, R.string.rows_controls_detail) {
            row(SancaiControlRow(context).apply {
                title = page.str(R.string.rows_sync)
                detail = page.str(R.string.rows_sync_detail)
                setSegmented(sync.toList(), syncAt) { _, index -> syncAt = index; refresh() }
            })
            row(SancaiControlRow(context).apply {
                title = page.str(R.string.rows_density)
                setSegmented(density.toList(), densityAt) { _, index -> densityAt = index; refresh() }
            })
            row(SancaiControlRow(context).apply {
                title = page.str(R.string.rows_strength)
                setSegmented(strength.toList(), strengthAt) { _, index -> strengthAt = index; refresh() }
            })
            row(SancaiControlRow(context).apply {
                title = page.str(R.string.rows_haptics)
                detail = page.str(R.string.rows_haptics_detail)
                setControl(SancaiSwitch(context).apply {
                    setChecked(haptics, false)
                    setOnCheckedChangeListener { _, checked -> haptics = checked; refresh() }
                })
            })
            row(SancaiControlRow(context).apply {
                title = page.str(R.string.rows_columns)
                setControl(SancaiStepper(context).apply {
                    setRange(1f, 6f)
                    stepSize = 1f
                    decimals = 0
                    value = columns
                    setOnValueChangeListener { _, value -> columns = value; refresh() }
                })
            })
            row(SancaiControlRow(context).apply {
                title = page.str(R.string.rows_brightness)
                val slider = SancaiSlider(context).apply {
                    valueFrom = 0f
                    valueTo = 100f
                    stepSize = 5f
                    value = brightness
                    glaze = page.theme.glazes.ochre
                    valueFormatter = { context.getString(R.string.rows_percent, it.roundToInt()) }
                    setOnValueChangeListener { _, value -> brightness = value; refresh() }
                }
                setControl(slider, textWeight = 1f, controlWeight = 1.6f)
            })
            row(SancaiControlRow(context).apply {
                title = page.str(R.string.rows_locked)
                detail = page.str(R.string.rows_locked_detail)
                setControl(SancaiSwitch(context).apply { setChecked(true, false) })
                isEnabled = false
            })
        }
        refresh()
        page.code("""
            var density = 1
            val row = SancaiControlRow(context)
            row.title = "Density"
            row.setSegmented(
                listOf("Compact", "Regular", "Roomy"),
                density) { _, index -> density = index }

            val wide = SancaiControlRow(context)
            wide.title = "Brightness"
            wide.setControl(
                SancaiSlider(context), 1f, 1.6f)
        """)
    }

    private fun links(page: PageScope) {
        val edge = SancaiLinkRow(page.context).apply {
            label = page.str(R.string.rows_link_edge)
            arrowPlacement = SancaiLinkRow.ArrowPlacement.EDGE
            arrowDirection = SancaiLinkRow.ArrowDirection.START
            setOnClickListener { page.host.say(page.str(R.string.rows_say_edge)) }
        }
        val adjacent = SancaiLinkRow(page.context).apply {
            label = page.str(R.string.rows_link_adjacent)
            arrowPlacement = SancaiLinkRow.ArrowPlacement.ADJACENT
            tone = SancaiTextTone.ACCENT
            setOnClickListener { page.host.say(page.str(R.string.rows_say_adjacent)) }
        }
        page.loose(
            page.text(page.str(R.string.rows_links_title), SancaiTextRole.HEADING),
            topDp = PageScope.SECTION_GAP, sideDp = PageScope.TEXT_INSET,
        )
        page.caption(page.str(R.string.rows_links_detail), topDp = 4)
        page.loose(edge, topDp = 6)
        page.loose(adjacent)
        page.code("""
            val link = SancaiLinkRow(context)
            link.label = "Earlier settings"
            link.arrowPlacement =
                SancaiLinkRow.ArrowPlacement.ADJACENT
            link.arrowDirection =
                SancaiLinkRow.ArrowDirection.START
            link.tone = SancaiTextTone.ACCENT
            link.setOnClickListener { finish() }
        """)
    }

    private fun SancaiSurface.row(view: android.view.View) {
        if (childCount > 0) addDivider()
        addView(view, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
    }
}
