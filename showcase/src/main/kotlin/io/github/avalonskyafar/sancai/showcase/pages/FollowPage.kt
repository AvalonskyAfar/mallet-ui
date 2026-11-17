package io.github.avalonskyafar.sancai.showcase.pages

import android.view.Gravity
import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiPowerSwitch
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSlider
import io.github.avalonskyafar.sancai.widget.SancaiText
import kotlin.math.roundToInt

/** Follow the finger: controls that are dragged, colour that travels, and the same movement from code. */
object FollowPage : DemoPage("i_follow", R.string.page_i_follow_title, R.string.page_i_follow_summary) {
    override fun build(page: PageScope) {
        page.title()
        drag(page)
        colour(page)
        vertical(page)
        fromCode(page)
        page.caption(page.str(R.string.follow_origin), topDp = 28)
    }

    private fun drag(page: PageScope) {
        val levels = levelNames(page)
        page.section(R.string.follow_drag_title, R.string.follow_drag_detail) {
            val status = status(page)
            val track = SancaiSegmentedTrack(context).apply {
                options = levels
                select(1, animate = false)
                setOnSelectionChangeListener { _, index ->
                    status.text = page.context.getString(R.string.follow_drag_track_status, levels[index])
                }
            }
            status.text = page.context.getString(R.string.follow_drag_track_status, levels[1])
            addView(track, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            addView(status, below(page, 10))
            addDivider(14f)

            val powerStatus = status(page)
            val power = SancaiPowerSwitch(context).apply {
                setChecked(true, false)
                setOnCheckedChangeListener { _, checked ->
                    powerStatus.text = page.str(if (checked) R.string.follow_power_on else R.string.follow_power_off)
                }
            }
            powerStatus.text = page.str(R.string.follow_power_on)
            addView(power, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            addView(powerStatus, below(page, 10))
        }
        page.caption(page.str(R.string.follow_drag_note))
        page.code("""
            val track = SancaiSegmentedTrack(context)
            track.setOptions(
                "Off", "Gentle", "Standard", "Strict",
            )
            track.setOnSelectionChangeListener { _, index ->
                status.text = "Filter: ${'$'}index"
            }

            val power = SancaiPowerSwitch(context)
            power.setChecked(true, false)
            power.setOnCheckedChangeListener { _, on ->
                status.text = if (on) "On" else "Off"
            }
        """)
    }

    private fun colour(page: PageScope) {
        val levels = levelNames(page)
        page.section(R.string.follow_colour_title, R.string.follow_colour_detail) {
            val value = page.text("", SancaiTextRole.LABEL)
            fun describe(percent: Float) {
                val whole = percent.roundToInt()
                // The scale has one glaze per level; the nearest one names the value.
                val level = (percent / 100f * (levels.size - 1)).roundToInt()
                value.text = page.context.getString(R.string.follow_colour_value, whole, levels[level])
            }
            val slider = SancaiSlider(context).apply {
                valueFrom = 0f
                valueTo = 100f
                stepSize = 1f
                setGlazeScale(page.theme.glazes.scale(4))
                valueFormatter = { page.context.getString(R.string.follow_colour_percent, it.roundToInt()) }
                setValue(40f, false)
                setOnValueChangeListener { _, percent -> describe(percent) }
            }
            describe(slider.value)
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    page.text(page.str(R.string.follow_colour_label), SancaiTextRole.LABEL, SancaiTextTone.MUTED),
                    LinearLayout.LayoutParams(0, PageScope.WRAP, 1f),
                )
                addView(value, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP))
            }
            addView(header, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            addView(slider, below(page, 6))
        }
        page.code("""
            val slider = SancaiSlider(context)
            slider.valueFrom = 0f
            slider.valueTo = 100f
            val glazes = SancaiTheme.of(context).glazes
            slider.setGlazeScale(glazes.scale(4))
            slider.setOnValueChangeListener { _, value ->
                label.text = "${'$'}{value.toInt()} %"
            }
        """)
    }

    private fun vertical(page: PageScope) {
        val speeds = listOf(
            page.str(R.string.follow_speed_slow),
            page.str(R.string.follow_speed_normal),
            page.str(R.string.follow_speed_fast),
        )
        page.section(R.string.follow_vertical_title, R.string.follow_vertical_detail) {
            val status = status(page)
            val track = SancaiSegmentedTrack(context).apply {
                options = speeds
                setGlazes(page.theme.glazes.uniform(speeds.size))
                select(1, animate = false)
                setOnSelectionChangeListener { _, index ->
                    status.text = page.context.getString(R.string.follow_vertical_status, speeds[index])
                }
            }
            status.text = page.context.getString(R.string.follow_vertical_status, speeds[1])
            addView(track, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            addView(status, below(page, 10))
        }
        page.caption(page.str(R.string.follow_vertical_note))
        page.code("""
            // Nothing to set up. Inside a ScrollView a track
            // claims a gesture only once it runs sideways.
            // Up or down, the page keeps it.
            val track = SancaiSegmentedTrack(context)
            track.setOptions("Slow", "Normal", "Fast")
            column.addView(track)
        """)
    }

    private fun fromCode(page: PageScope) {
        val names = listOf(
            page.str(R.string.follow_level_off),
            page.str(R.string.follow_code_low),
            page.str(R.string.follow_code_mid),
            page.str(R.string.follow_code_high),
        )
        page.section(R.string.follow_code_title, R.string.follow_code_detail) {
            val status = status(page)
            status.text = page.str(R.string.follow_code_idle)
            val track = SancaiSegmentedTrack(context).apply {
                options = names
                setOnSelectionChangeListener { _, index ->
                    status.text = page.context.getString(R.string.follow_code_dragged, names[index])
                }
            }
            addView(track, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            addView(status, below(page, 10))
            addDivider(14f)

            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            names.forEachIndexed { index, name ->
                val button = SancaiButton(context).apply {
                    text = name
                    setOnClickListener {
                        track.select(index)
                        status.text = page.context.getString(R.string.follow_code_glided, index, name)
                    }
                }
                val params = LinearLayout.LayoutParams(0, PageScope.WRAP, 1f)
                if (index > 0) params.marginStart = page.dp(8)
                row.addView(button, params)
            }
            addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))

            val jump = SancaiButton(context).apply {
                variant = SancaiButton.Variant.TEXT
                text = page.str(R.string.follow_code_jump)
                setOnClickListener {
                    track.select(0, animate = false)
                    status.text = page.context.getString(R.string.follow_code_jumped, names[0])
                }
            }
            addView(jump, below(page, 8))
        }
        page.code("""
            // Glides to the third option.
            track.select(2)
            // Jumps: for restoring saved state.
            track.select(0, animate = false)

            // select() does not call the listener;
            // the listener reports the user's moves only.
            button.setOnClickListener { track.select(3) }
        """)
    }

    private fun levelNames(page: PageScope): List<String> = listOf(
        page.str(R.string.follow_level_off),
        page.str(R.string.follow_level_gentle),
        page.str(R.string.follow_level_standard),
        page.str(R.string.follow_level_strict),
    )

    /** The line under a control that says what it did. */
    private fun status(page: PageScope): SancaiText =
        page.text("", SancaiTextRole.CAPTION, SancaiTextTone.MUTED)

    private fun below(page: PageScope, topDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(topDp) }
}
