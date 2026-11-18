package io.github.avalonskyafar.mallet.showcase.pages

import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletHue
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.widget.MalletControlRow
import io.github.avalonskyafar.mallet.widget.MalletPowerSwitch
import io.github.avalonskyafar.mallet.widget.MalletSegmentedTrack
import io.github.avalonskyafar.mallet.widget.MalletSurface
import io.github.avalonskyafar.mallet.widget.MalletSwitch
import io.github.avalonskyafar.mallet.widget.MalletText

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
        page.loose(MalletSurface(page.context).apply { addFull(three) }, topDp = CARD_GAP)
        page.status(threeStatus)
        page.loose(MalletSurface(page.context).apply { addFull(four) }, topDp = CARD_GAP)
        page.status(fourStatus)
        page.code("""
            val track = MalletSegmentedTrack(this)
            track.setOptions("Off", "Gentle", "Standard", "Strict")
            track.select(2, animate = false)
            track.setOnSelectionChangeListener { view, index ->
                status.text = "Level: " + view.options[index]
            }
        """)
    }

    /** Options that are equals: one hue under the whole track. */
    private fun plain(page: PageScope) {
        val hues = page.theme.hues
        val status = page.text("", MalletTextRole.CAPTION, MalletTextTone.MUTED)
        val view = page.track(0, hues.uniform(2, hues.mist), R.string.tracks_list, R.string.tracks_grid)
        val order = page.track(
            0, hues.uniform(3, hues.jade), R.string.tracks_newest, R.string.tracks_oldest, R.string.tracks_by_name,
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
            val hues = MalletTheme.of(this).hues
            view.setOptions("List", "Grid")
            view.setHues(hues.uniform(2, hues.mist))
            order.setOptions("Newest", "Oldest", "By name")
            order.setHues(hues.uniform(3, hues.jade))
        """)
    }

    /** The last option is refused: the listener rolls the selection back and says why. */
    private fun refused(page: PageScope) {
        val track = page.track(1, null, *LEVELS)
        val status = page.text("", MalletTextRole.CAPTION, MalletTextTone.MUTED)
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

    /** Label at the start, switch at the end; default hues, other hues, disabled. */
    private fun switches(page: PageScope) {
        val hues = page.theme.hues
        val status = page.text("", MalletTextRole.CAPTION, MalletTextTone.MUTED)
        fun show(label: Int, checked: Boolean) {
            val format = if (checked) R.string.tracks_switch_status_on else R.string.tracks_switch_status_off
            status.text = page.context.getString(format, page.str(label))
        }
        fun row(label: Int, checked: Boolean, enabled: Boolean = true, setUp: MalletSwitch.() -> Unit = {}): MalletControlRow {
            val switch = MalletSwitch(page.context).apply {
                setUp()
                setChecked(checked, false)
                setOnCheckedChangeListener { _, isOn -> show(label, isOn) }
            }
            // The row names the switch for screen readers and pales with it when disabled.
            return MalletControlRow(page.context).apply {
                title = page.str(label)
                alignment = MalletControlRow.Alignment.START
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
                offHue = hues.mist
                onHue = hues.jade
            })
            addDivider()
            addFull(row(R.string.tracks_switch_managed, true, enabled = false))
        }
        page.status(status)
        page.code("""
            val hues = MalletTheme.of(this).hues
            val quiet = MalletSwitch(this)
            quiet.offHue = hues.mist
            quiet.onHue = hues.jade
            quiet.setChecked(true, false)   // jumps, no callback
            quiet.setOnCheckedChangeListener { _, checked ->
                status.text = if (checked) "On" else "Off"
            }
            val row = MalletControlRow(this)
            row.title = "Quiet hours"
            row.alignment = MalletControlRow.Alignment.START
            row.setControl(quiet)
        """)
    }

    /** The hero switch with its own words, and a disabled one with the default words. */
    private fun power(page: PageScope) {
        val status = page.text(page.str(R.string.tracks_power_status_on), MalletTextRole.CAPTION, MalletTextTone.MUTED)
        val shield = MalletPowerSwitch(page.context).apply {
            onLabel = page.str(R.string.tracks_power_on)
            offLabel = page.str(R.string.tracks_power_off)
            turnOnLabel = page.str(R.string.tracks_power_turn_on)
            turnOffLabel = page.str(R.string.tracks_power_turn_off)
            setChecked(true, false)
            setOnCheckedChangeListener { _, checked ->
                status.text = page.str(if (checked) R.string.tracks_power_status_on else R.string.tracks_power_status_off)
            }
        }
        val disabled = MalletPowerSwitch(page.context).apply { isEnabled = false }
        page.section(R.string.tracks_power_title, R.string.tracks_power_detail) { addFull(shield) }
        page.status(status)
        page.loose(MalletSurface(page.context).apply { addFull(disabled) }, topDp = CARD_GAP)
        page.caption(page.str(R.string.tracks_power_disabled))
        page.code("""
            val shield = MalletPowerSwitch(this)
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
private fun MalletSurface.addFull(view: android.view.View) {
    addView(view, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
}

/** Puts a status line under the card above it, aligned with the headings. */
private fun PageScope.status(line: MalletText) {
    loose(line, topDp = 8, sideDp = PageScope.TEXT_INSET)
}

/** A track with [labels], [start] selected, and [hues] under its options (`null`: the ordinal scale). */
private fun PageScope.track(start: Int, hues: List<MalletHue>?, vararg labels: Int): MalletSegmentedTrack =
    MalletSegmentedTrack(context).apply {
        options = labels.map { str(it) }
        setHues(hues)
        select(start, animate = false)
    }

/** A track and the line that names its selection through [format]; the listener keeps the line current. */
private fun PageScope.namedTrack(format: Int, start: Int, vararg labels: Int): Pair<MalletSegmentedTrack, MalletText> {
    val track = track(start, null, *labels)
    val line = text(context.getString(format, track.options[start]), MalletTextRole.CAPTION, MalletTextTone.MUTED)
    track.setOnSelectionChangeListener { view, index ->
        line.text = context.getString(format, view.options[index])
    }
    return track to line
}
