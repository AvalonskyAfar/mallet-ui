package io.github.avalonskyafar.mallet.showcase.pages

import android.view.View
import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.widget.MalletButton
import io.github.avalonskyafar.mallet.widget.MalletPowerSwitch
import io.github.avalonskyafar.mallet.widget.MalletSegmentedTrack
import io.github.avalonskyafar.mallet.widget.MalletSlider
import io.github.avalonskyafar.mallet.widget.MalletText
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
            val track = MalletSegmentedTrack(context).apply {
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
            val power = MalletPowerSwitch(context).apply {
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
            val track = MalletSegmentedTrack(context)
            track.setOptions(
                "Off", "Gentle", "Standard", "Strict",
            )
            track.setOnSelectionChangeListener { _, index ->
                status.text = "Filter: ${'$'}index"
            }

            val power = MalletPowerSwitch(context)
            power.setChecked(true, false)
            power.setOnCheckedChangeListener { _, on ->
                status.text = if (on) "On" else "Off"
            }
        """)
    }

    private fun colour(page: PageScope) {
        val levels = levelNames(page)
        // The value stands on its own line under the slider: a full line of the page takes any
        // language's digits without a narrow box to cut them.
        val value = page.text("", MalletTextRole.CAPTION, MalletTextTone.MUTED).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        fun describe(percent: Float) {
            val whole = percent.roundToInt()
            // The scale has one hue per level; the nearest one names the value.
            val level = (percent / 100f * (levels.size - 1)).roundToInt()
            value.text = page.context.getString(R.string.follow_colour_value, whole, levels[level])
        }
        val slider = MalletSlider(page.context).apply {
            valueFrom = 0f
            valueTo = 100f
            stepSize = 1f
            setHueScale(page.theme.hues.scale(4))
            valueFormatter = { page.context.getString(R.string.follow_colour_percent, it.roundToInt()) }
            setValue(40f, false)
            setOnValueChangeListener { _, percent -> describe(percent) }
        }
        describe(slider.value)
        val name = page.text(page.str(R.string.follow_colour_label), MalletTextRole.LABEL, MalletTextTone.MUTED)
        name.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        // No row names this slider, so it takes the name of the line above it for a screen reader.
        slider.contentDescription = name.text
        // A lone slider gets no card: it stands on the canvas under its heading, as wide as the
        // cards. Name, slider and value in one column, at the same inset as the text around
        // them, where a finger on the bead hides none of the three.
        page.heading(R.string.follow_colour_title, R.string.follow_colour_detail)
        page.loose(name, topDp = LABEL_GAP, sideDp = PageScope.TEXT_INSET)
        page.loose(slider, sideDp = PageScope.TEXT_INSET)
        page.loose(value, topDp = LOOSE_STATUS_GAP, sideDp = PageScope.TEXT_INSET)
        page.code("""
            val slider = MalletSlider(context)
            slider.valueFrom = 0f
            slider.valueTo = 100f
            val hues = MalletTheme.of(context).hues
            slider.setHueScale(hues.scale(4))
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
        val status = status(page)
        val track = MalletSegmentedTrack(page.context).apply {
            options = speeds
            setHues(page.theme.hues.uniform(speeds.size))
            select(1, animate = false)
            setOnSelectionChangeListener { _, index ->
                status.text = page.context.getString(R.string.follow_vertical_status, speeds[index])
            }
        }
        status.text = page.context.getString(R.string.follow_vertical_status, speeds[1])
        // A lone track gets no card either. What to try is said above it, in the line under the
        // heading, so that the status line is the last thing between the track and its code.
        page.heading(R.string.follow_vertical_title, R.string.follow_vertical_detail)
        page.loose(track, topDp = PageScope.HEADING_GAP)
        page.loose(status, topDp = LOOSE_STATUS_GAP, sideDp = PageScope.TEXT_INSET)
        page.code(
            """
            // Nothing to set up. Inside a ScrollView a track
            // claims a gesture only once it runs sideways.
            // Up or down, the page keeps it.
            val track = MalletSegmentedTrack(context)
            track.setOptions("Slow", "Normal", "Fast")
            column.addView(track)
            """,
            topDp = SAMPLE_GAP,
        )
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
            val track = MalletSegmentedTrack(context).apply {
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
                val side = page.dp(ROW_BUTTON_SIDE)
                val button = MalletButton(context).apply {
                    text = name
                    setPadding(side, paddingTop, side, paddingBottom)
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

            val jump = MalletButton(context).apply {
                variant = MalletButton.Variant.TEXT
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

    /**
     * The line under a control that says what it did. It stands at the start edge whatever
     * language it is written in, as the headings do.
     */
    private fun status(page: PageScope): MalletText =
        page.text("", MalletTextRole.CAPTION, MalletTextTone.MUTED).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }

    private fun below(page: PageScope, topDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(topDp) }

    /**
     * Space above the line that names the slider, in dp. More than under a heading elsewhere, and
     * none between the line and the slider (which keeps 8 dp of its own above the bead), so that
     * the line reads as the name of the slider and not as one more line of the text above it.
     */
    private const val LABEL_GAP = 16

    /**
     * Space above the status line of a track on the canvas, in dp. No card holds the two
     * together here, so the line stands close under its track and further from the sample below.
     */
    private const val LOOSE_STATUS_GAP = 2

    /** Space above the sample that follows such a status line, in dp. */
    private const val SAMPLE_GAP = 14

    /**
     * Side padding of the four buttons that share one row, in dp: narrower than a button's own,
     * so that a label stays on one line in large text.
     */
    private const val ROW_BUTTON_SIDE = 10
}
