package io.github.avalonskyafar.sancai.showcase.pages

import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.widget.SancaiControlRow
import io.github.avalonskyafar.sancai.widget.SancaiPowerSwitch
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiSwitch
import io.github.avalonskyafar.sancai.widget.SancaiText

/** Segmented track, switch and power switch, each with the effect of its listener in view. */
object TracksPage : DemoPage("c_tracks", R.string.page_c_tracks_title, R.string.page_c_tracks_summary) {
    override fun build(page: PageScope) {
        page.title()
        segmented(page)
        plain(page)
        refused(page)
        switches(page)
        power(page)
        page.caption(page.str(R.string.tracks_origin), topDp = 22)
    }

    /** Two, three and four options; under each card a line that names the selection. */
    private fun segmented(page: PageScope) {
        val (two, twoStatus) = page.namedTrack(R.string.tracks_status_filter, 1, R.string.tracks_off, R.string.tracks_on)
        val (three, threeStatus) = page.namedTrack(
            R.string.tracks_status_range, 1, R.string.tracks_day, R.string.tracks_week, R.string.tracks_month,
        )
        val (four, fourStatus) = page.namedTrack(R.string.tracks_status_level, 2, *LEVELS)
        page.section(R.string.tracks_segmented_title, R.string.tracks_segmented_detail) { addFull(two) }
        page.status(twoStatus)
        page.loose(SancaiSurface(page.context).apply { addFull(three) }, topDp = CARD_GAP)
        page.status(threeStatus)
        page.loose(SancaiSurface(page.context).apply { addFull(four) }, topDp = CARD_GAP)
        page.status(fourStatus)
        page.code("""
            val track = SancaiSegmentedTrack(this)
            track.setOptions("Off", "Gentle", "Standard", "Strict")
            track.select(2, animate = false)
            track.setOnSelectionChangeListener { view, index ->
                status.text = "Level: " + view.options[index]
            }
        """)
    }

    /** Options that are equals: one glaze under the whole track. */
    private fun plain(page: PageScope) {
        val glazes = page.theme.glazes
        val status = page.text("", SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
        val view = page.track(0, glazes.uniform(2, glazes.mist), R.string.tracks_list, R.string.tracks_grid)
        val order = page.track(
            0, glazes.uniform(3, glazes.celadon), R.string.tracks_newest, R.string.tracks_oldest, R.string.tracks_by_name,
        )
        fun show() {
            status.text = page.context.getString(
                R.string.tracks_status_plain, view.options[view.selectedIndex], order.options[order.selectedIndex],
            )
        }
        view.setOnSelectionChangeListener { _, _ -> show() }
        order.setOnSelectionChangeListener { _, _ -> show() }
        show()
        page.section(R.string.tracks_plain_title, R.string.tracks_plain_detail) {
            addFull(view)
            addDivider(5f)
            addFull(order)
        }
        page.status(status)
        page.code("""
            val glazes = SancaiTheme.of(this).glazes
            view.setOptions("List", "Grid")
            view.setGlazes(glazes.uniform(2, glazes.mist))
            order.setOptions("Newest", "Oldest", "By name")
            order.setGlazes(glazes.uniform(3, glazes.celadon))
        """)
    }

    /** The last option is refused: the listener rolls the selection back and says why. */
    private fun refused(page: PageScope) {
        val track = page.track(1, null, *LEVELS)
        val status = page.text("", SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
        var previous = track.selectedIndex
        fun show() {
            status.text = page.context.getString(R.string.tracks_status_level, track.options[previous])
        }
        track.setOnSelectionChangeListener { view, index ->
            if (index == view.options.lastIndex) {
                view.select(previous)
                page.host.say(page.context.getString(R.string.tracks_refuse_reason, view.options[previous]))
            } else {
                previous = index
                show()
            }
        }
        show()
        page.section(R.string.tracks_refuse_title, R.string.tracks_refuse_detail) { addFull(track) }
        page.status(status)
        page.code("""
            var previous = track.selectedIndex
            track.setOnSelectionChangeListener { view, index ->
                if (index == view.options.lastIndex) {
                    view.select(previous)   // glides back
                    status.text = "Strict needs a passcode."
                } else previous = index
            }
        """)
    }

    /** Label at the start, switch at the end; default glazes, other glazes, disabled. */
    private fun switches(page: PageScope) {
        val glazes = page.theme.glazes
        val status = page.text("", SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
        fun show(label: Int, checked: Boolean) {
            val format = if (checked) R.string.tracks_switch_status_on else R.string.tracks_switch_status_off
            status.text = page.context.getString(format, page.str(label))
        }
        fun row(label: Int, checked: Boolean, enabled: Boolean = true, setUp: SancaiSwitch.() -> Unit = {}): SancaiControlRow {
            val switch = SancaiSwitch(page.context).apply {
                setUp()
                setChecked(checked, false)
                setOnCheckedChangeListener { _, isOn -> show(label, isOn) }
            }
            // The row names the switch for screen readers and pales with it when disabled.
            return SancaiControlRow(page.context).apply {
                title = page.str(label)
                alignment = SancaiControlRow.Alignment.START
                setControl(switch)
                isEnabled = enabled
            }
        }
        show(R.string.tracks_switch_blocking, true)
        page.section(R.string.tracks_switch_title, R.string.tracks_switch_detail) {
            addFull(row(R.string.tracks_switch_blocking, true))
            addDivider()
            addFull(row(R.string.tracks_switch_images, false))
            addDivider()
            addFull(row(R.string.tracks_switch_quiet, true) {
                offGlaze = glazes.mist
                onGlaze = glazes.celadon
            })
            addDivider()
            addFull(row(R.string.tracks_switch_managed, true, enabled = false))
        }
        page.status(status)
        page.code("""
            val glazes = SancaiTheme.of(this).glazes
            val quiet = SancaiSwitch(this)
            quiet.offGlaze = glazes.mist
            quiet.onGlaze = glazes.celadon
            quiet.setChecked(true, false)   // jumps, no callback
            quiet.setOnCheckedChangeListener { _, checked ->
                status.text = if (checked) "On" else "Off"
            }
            val row = SancaiControlRow(this)
            row.title = "Quiet hours"
            row.alignment = SancaiControlRow.Alignment.START
            row.setControl(quiet)
        """)
    }

    /** The hero switch with its own words, and a disabled one with the default words. */
    private fun power(page: PageScope) {
        val status = page.text(page.str(R.string.tracks_power_status_on), SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
        val shield = SancaiPowerSwitch(page.context).apply {
            onLabel = page.str(R.string.tracks_power_on)
            offLabel = page.str(R.string.tracks_power_off)
            turnOnLabel = page.str(R.string.tracks_power_turn_on)
            turnOffLabel = page.str(R.string.tracks_power_turn_off)
            setChecked(true, false)
            setOnCheckedChangeListener { _, checked ->
                status.text = page.str(if (checked) R.string.tracks_power_status_on else R.string.tracks_power_status_off)
            }
        }
        val disabled = SancaiPowerSwitch(page.context).apply { isEnabled = false }
        page.section(R.string.tracks_power_title, R.string.tracks_power_detail) { addFull(shield) }
        page.status(status)
        page.loose(SancaiSurface(page.context).apply { addFull(disabled) }, topDp = CARD_GAP)
        page.caption(page.str(R.string.tracks_power_disabled))
        page.code("""
            val shield = SancaiPowerSwitch(this)
            shield.onLabel = "Protected"
            shield.offLabel = "Paused"
            shield.turnOffLabel = "Pause"
            shield.turnOnLabel = "Resume"
            shield.setOnCheckedChangeListener { _, on ->
                status.text = if (on) "Running" else "Paused"
            }
        """)
    }
}

private const val CARD_GAP = 14
private val LEVELS = intArrayOf(R.string.tracks_off, R.string.tracks_gentle, R.string.tracks_standard, R.string.tracks_strict)

/** Adds [view] across the whole width of the card. */
private fun SancaiSurface.addFull(view: android.view.View) {
    addView(view, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
}

/** Puts a status line under the card above it, aligned with the headings. */
private fun PageScope.status(line: SancaiText) {
    loose(line, topDp = 8, sideDp = PageScope.TEXT_INSET)
}

/** A track with [labels], [start] selected, and [glazes] under its options (`null`: the ordinal scale). */
private fun PageScope.track(start: Int, glazes: List<SancaiGlaze>?, vararg labels: Int): SancaiSegmentedTrack =
    SancaiSegmentedTrack(context).apply {
        options = labels.map { str(it) }
        setGlazes(glazes)
        select(start, animate = false)
    }

/** A track and the line that names its selection through [format]; the listener keeps the line current. */
private fun PageScope.namedTrack(format: Int, start: Int, vararg labels: Int): Pair<SancaiSegmentedTrack, SancaiText> {
    val track = track(start, null, *labels)
    val line = text(context.getString(format, track.options[start]), SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
    track.setOnSelectionChangeListener { view, index ->
        line.text = context.getString(format, view.options[index])
    }
    return track to line
}
