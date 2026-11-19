package io.github.avalonskyafar.mallet.showcase.pages

import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.widget.MalletControlRow
import io.github.avalonskyafar.mallet.widget.MalletEntryRow
import io.github.avalonskyafar.mallet.widget.MalletHeading
import io.github.avalonskyafar.mallet.widget.MalletKeyValueList
import io.github.avalonskyafar.mallet.widget.MalletLinkRow
import io.github.avalonskyafar.mallet.widget.MalletSlider
import io.github.avalonskyafar.mallet.widget.MalletStepper
import io.github.avalonskyafar.mallet.widget.MalletSurface
import io.github.avalonskyafar.mallet.widget.MalletSwitch
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
        val facts = MalletKeyValueList(page.context)
        controls(page, facts)
        links(page)
        page.heading(R.string.rows_facts_title, R.string.rows_facts_detail)
        page.loose(facts, topDp = PageScope.HEADING_GAP)
        page.code("""
            val facts = MalletKeyValueList(context)
            facts.addItem(
                "Sync", "Paused",
                MalletTextTone.WARNING)
            facts.addItem("Density", "Regular")
            facts.addItem(
                "Managed", "Set by policy",
                MalletTextTone.MUTED)
        """)
        page.caption(page.str(R.string.rows_note), topDp = 22)
    }

    private fun headings(page: PageScope) {
        page.section(R.string.rows_headings_title, R.string.rows_headings_detail) {
            // The card pads 9 dp, part of it room for its shadow: these margins give both
            // specimens the same air towards the rim as towards the divider between them.
            addView(MalletHeading(context).apply {
                level = MalletHeading.Level.PAGE
                title = page.str(R.string.rows_heading_page)
                detail = page.str(R.string.rows_heading_page_detail)
            }, specimen(page, topDp = 4))
            addDivider(12f)
            addView(MalletHeading(context).apply {
                title = page.str(R.string.rows_heading_section)
                detail = page.str(R.string.rows_heading_section_detail)
            }, specimen(page, bottomDp = 8))
        }
        page.code("""
            val heading = MalletHeading(context)
            heading.level = MalletHeading.Level.PAGE
            heading.title = "Display"
            heading.detail = "How the screen looks"
        """)
    }

    private fun entries(page: PageScope) {
        val languages = page.context.resources.getStringArray(R.array.rows_languages)
        var language = 0
        page.section(R.string.rows_entries_title, R.string.rows_entries_detail) {
            row(MalletEntryRow(context).apply {
                title = page.str(R.string.rows_entry_end)
                detail = page.str(R.string.rows_entry_end_detail)
                arrow = MalletEntryRow.Arrow.END
                setOnClickListener { page.host.say(page.str(R.string.rows_say_end)) }
            })
            row(MalletEntryRow(context).apply {
                title = page.str(R.string.rows_entry_start)
                detail = page.str(R.string.rows_entry_start_detail)
                arrow = MalletEntryRow.Arrow.START
                setOnClickListener { page.host.say(page.str(R.string.rows_say_start)) }
            })
            row(MalletEntryRow(context).apply {
                title = page.str(R.string.rows_entry_icon)
                leadingIcon = MalletIcon.BELL
                arrow = MalletEntryRow.Arrow.END
                setOnClickListener { page.host.say(page.str(R.string.rows_say_icon)) }
            })
            row(MalletEntryRow(context).apply {
                title = page.str(R.string.rows_entry_value)
                detail = page.str(R.string.rows_entry_value_detail)
                value = languages[language]
                arrow = MalletEntryRow.Arrow.END
                setOnClickListener {
                    language = (language + 1) % languages.size
                    value = languages[language]
                }
            })
            row(MalletEntryRow(context).apply {
                title = page.str(R.string.rows_entry_aligned)
                detail = page.str(R.string.rows_entry_aligned_detail)
                alignment = MalletEntryRow.Alignment.START
                leadingIcon = MalletIcon.SHIELD
                arrow = MalletEntryRow.Arrow.END
                setOnClickListener { page.host.say(page.str(R.string.rows_say_aligned)) }
            })
            row(MalletEntryRow(context).apply {
                title = page.str(R.string.rows_entry_disabled)
                value = page.str(R.string.rows_entry_disabled_value)
                arrow = MalletEntryRow.Arrow.END
                isEnabled = false
            })
        }
        page.code("""
            val row = MalletEntryRow(context)
            row.title = "Language"
            row.value = "English"
            row.leadingIcon = MalletIcon.BELL
            row.arrow = MalletEntryRow.Arrow.END
            row.alignment = MalletEntryRow.Alignment.START
            row.setOnClickListener { row.value = "中文" }

            val accounts = MalletEntryRow(context)
            accounts.title = "Allowed accounts"
            accounts.arrow = MalletEntryRow.Arrow.START
        """)
    }

    private fun controls(page: PageScope, facts: MalletKeyValueList) {
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
                MalletKeyValueList.Item(
                    page.str(R.string.rows_sync), sync[syncAt],
                    if (syncAt == 0) MalletTextTone.WARNING else MalletTextTone.SUCCESS,
                ),
                MalletKeyValueList.Item(page.str(R.string.rows_density), density[densityAt]),
                MalletKeyValueList.Item(
                    page.str(R.string.rows_strength), strength[strengthAt],
                    if (strengthAt == 3) MalletTextTone.DANGER else MalletTextTone.ACCENT,
                ),
                MalletKeyValueList.Item(
                    page.str(R.string.rows_haptics), if (haptics) on else off,
                    if (haptics) MalletTextTone.INK else MalletTextTone.MUTED,
                ),
                MalletKeyValueList.Item(page.str(R.string.rows_columns), columns.roundToInt().toString()),
                MalletKeyValueList.Item(
                    page.str(R.string.rows_brightness),
                    page.context.getString(R.string.rows_percent, brightness.roundToInt()),
                ),
                MalletKeyValueList.Item(
                    page.str(R.string.rows_locked), page.str(R.string.rows_locked_value),
                    MalletTextTone.MUTED,
                ),
            ))
        }

        page.section(R.string.rows_controls_title, R.string.rows_controls_detail) {
            row(MalletControlRow(context).apply {
                title = page.str(R.string.rows_sync)
                detail = page.str(R.string.rows_sync_detail)
                setSegmented(sync.toList(), syncAt) { _, index -> syncAt = index; refresh() }
            })
            row(MalletControlRow(context).apply {
                title = page.str(R.string.rows_density)
                setSegmented(density.toList(), densityAt) { _, index -> densityAt = index; refresh() }
            })
            row(MalletControlRow(context).apply {
                title = page.str(R.string.rows_strength)
                setSegmented(strength.toList(), strengthAt) { _, index -> strengthAt = index; refresh() }
            })
            row(MalletControlRow(context).apply {
                title = page.str(R.string.rows_haptics)
                detail = page.str(R.string.rows_haptics_detail)
                setControl(MalletSwitch(context).apply {
                    setChecked(haptics, false)
                    setOnCheckedChangeListener { _, checked -> haptics = checked; refresh() }
                })
            })
            row(MalletControlRow(context).apply {
                title = page.str(R.string.rows_columns)
                setControl(MalletStepper(context).apply {
                    setRange(1f, 6f)
                    stepSize = 1f
                    decimals = 0
                    value = columns
                    setOnValueChangeListener { _, value -> columns = value; refresh() }
                })
            })
            row(MalletControlRow(context).apply {
                title = page.str(R.string.rows_brightness)
                val slider = MalletSlider(context).apply {
                    valueFrom = 0f
                    valueTo = 100f
                    stepSize = 5f
                    value = brightness
                    hue = page.theme.hues.ochre
                    valueFormatter = { context.getString(R.string.rows_percent, it.roundToInt()) }
                    setOnValueChangeListener { _, value -> brightness = value; refresh() }
                }
                setControl(slider, textWeight = 1f, controlWeight = 1.6f)
            })
            row(MalletControlRow(context).apply {
                title = page.str(R.string.rows_locked)
                detail = page.str(R.string.rows_locked_detail)
                setControl(MalletSwitch(context).apply { setChecked(true, false) })
                isEnabled = false
            })
        }
        refresh()
        page.code("""
            var density = 1
            val row = MalletControlRow(context)
            row.title = "Density"
            row.setSegmented(
                listOf("Compact", "Regular", "Roomy"),
                density) { _, index -> density = index }

            val wide = MalletControlRow(context)
            wide.title = "Brightness"
            wide.setControl(
                MalletSlider(context), 1f, 1.6f)
        """)
    }

    private fun links(page: PageScope) {
        val edge = MalletLinkRow(page.context).apply {
            label = page.str(R.string.rows_link_edge)
            arrowPlacement = MalletLinkRow.ArrowPlacement.EDGE
            arrowDirection = MalletLinkRow.ArrowDirection.START
            setOnClickListener { page.host.say(page.str(R.string.rows_say_edge)) }
        }
        val adjacent = MalletLinkRow(page.context).apply {
            label = page.str(R.string.rows_link_adjacent)
            arrowPlacement = MalletLinkRow.ArrowPlacement.ADJACENT
            tone = MalletTextTone.ACCENT
            setOnClickListener { page.host.say(page.str(R.string.rows_say_adjacent)) }
        }
        page.heading(R.string.rows_links_title, R.string.rows_links_detail)
        page.loose(edge, topDp = 6)
        page.loose(adjacent)
        page.code("""
            val link = MalletLinkRow(context)
            link.label = "Earlier settings"
            link.arrowPlacement =
                MalletLinkRow.ArrowPlacement.ADJACENT
            link.arrowDirection =
                MalletLinkRow.ArrowDirection.START
            link.tone = MalletTextTone.ACCENT
            link.setOnClickListener {
                host.navigate("earlier")
            }
        """)
    }

    /** Where a heading shown as a specimen stands in its card. */
    private fun specimen(page: PageScope, topDp: Int = 0, bottomDp: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply {
            topMargin = page.dp(topDp)
            bottomMargin = page.dp(bottomDp)
        }

    private fun MalletSurface.row(view: android.view.View) {
        if (childCount > 0) addDivider()
        addView(view, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
    }
}
