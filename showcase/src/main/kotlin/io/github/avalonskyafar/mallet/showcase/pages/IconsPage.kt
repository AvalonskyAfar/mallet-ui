package io.github.avalonskyafar.mallet.showcase.pages

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconView
import io.github.avalonskyafar.mallet.interaction.MalletPress
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.widget.MalletSegmentedTrack
import io.github.avalonskyafar.mallet.widget.MalletSlider
import java.util.Locale
import kotlin.math.roundToInt

/** The icon set: every glyph, its size and weight, its tones and its mirroring. */
object IconsPage : DemoPage("f_icons", R.string.page_f_icons_title, R.string.page_f_icons_summary) {
    private const val COLUMNS = 5
    private const val STAGE_DP = 72
    private val STROKES = floatArrayOf(1.5f, 2f, 2.5f)

    override fun build(page: PageScope) {
        page.title()
        grid(page)
        sizeAndWeight(page)
        tones(page)
        direction(page)
        page.caption(page.str(R.string.icons_origin), topDp = 22)
    }

    /** All icons, five to a row. A cell sinks under the finger and names its constant. */
    private fun grid(page: PageScope) {
        page.section(R.string.icons_all_title, R.string.icons_all_detail) {
            MalletIcon.values().toList().chunked(COLUMNS).forEach { icons ->
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                icons.forEach { icon ->
                    row.addView(page.cell(icon), LinearLayout.LayoutParams(0, PageScope.MATCH, 1f))
                }
                addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            }
        }
        page.code("""
            val search = MalletIconView(this)
            search.icon = MalletIcon.SEARCH
            search.contentDescription = "Search"
            MalletPress.attach(search) {
                title = MalletIcon.SEARCH.name
            }
        """)
    }

    /** One cell of the grid: the icon in ink over its lower-case name. */
    private fun PageScope.cell(icon: MalletIcon, message: String = "MalletIcon." + icon.name): View {
        val name = icon.name.lowercase(Locale.ROOT)
        val glyph = MalletIconView(context).apply {
            this.icon = icon
            tone = MalletTextTone.INK
        }
        // A zero-width space after each underscore lets a long name break there.
        val label = text(name.replace("_", "_​"), MalletTextRole.MICRO, MalletTextTone.MUTED)
        label.gravity = Gravity.CENTER_HORIZONTAL
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(2), dp(10), dp(2), dp(8))
            contentDescription = name.replace('_', ' ')
            addView(glyph, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(label, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = dp(6) })
            MalletPress.attach(this, this) { host.say(message) }
        }
    }

    /** One icon on a stage; the slider sets the side of its box, the track its stroke. */
    private fun sizeAndWeight(page: PageScope) {
        val context = page.context
        val glyph = MalletIconView(context).apply {
            icon = MalletIcon.BELL
            tone = MalletTextTone.INK
        }
        val stage = FrameLayout(context)
        stage.addView(glyph, FrameLayout.LayoutParams(page.dp(40), page.dp(40), Gravity.CENTER))
        val status = page.text("", MalletTextRole.CAPTION, MalletTextTone.MUTED)
        status.gravity = Gravity.CENTER_HORIZONTAL
        val slider = MalletSlider(context).apply {
            valueFrom = 16f
            valueTo = 64f
            stepSize = 1f
            value = 40f
            valueFormatter = { "${it.roundToInt()} dp" }
            contentDescription = page.str(R.string.icons_size_label)
        }
        val track = MalletSegmentedTrack(context).apply {
            setOptions("1.5", "2", "2.5")
            select(1, animate = false)
            contentDescription = page.str(R.string.icons_stroke_label)
        }
        fun show() {
            val side = slider.value.roundToInt()
            glyph.layoutParams = FrameLayout.LayoutParams(page.dp(side), page.dp(side), Gravity.CENTER)
            glyph.strokeDp = STROKES[track.selectedIndex]
            status.text = context.getString(R.string.icons_size_status, side, track.options[track.selectedIndex])
        }
        slider.setOnValueChangeListener { _, _ -> show() }
        track.setOnSelectionChangeListener { _, _ -> show() }
        show()
        page.section(R.string.icons_size_title, R.string.icons_size_detail) {
            addView(stage, LinearLayout.LayoutParams(PageScope.MATCH, page.dp(STAGE_DP + 16)))
            addView(status, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            addDivider(10f)
            addView(page.labelled(R.string.icons_size_label, slider))
            addView(page.labelled(R.string.icons_stroke_label, track), spaced(page.dp(6)))
        }
        page.code("""
            val bell = MalletIconView(this)
            bell.icon = MalletIcon.BELL
            bell.tone = MalletTextTone.INK
            bell.strokeDp = 1.5f
            val density = resources.displayMetrics.density
            val side = (48 * density).toInt()
            val box = ViewGroup.LayoutParams(side, side)
            addContentView(bell, box)
        """)
    }

    /** A label at the start and [control] filling the rest of the line. */
    private fun PageScope.labelled(label: Int, control: View): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val name = text(str(label), MalletTextRole.LABEL)
        addView(name, LinearLayout.LayoutParams(dp(64), PageScope.WRAP).apply { marginStart = dp(6) })
        addView(control, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
    }

    /** The same icon in every tone; tapping a tone sets the large icon in it. */
    private fun tones(page: PageScope) {
        val context = page.context
        val large = MalletIconView(context).apply { icon = MalletIcon.SHIELD }
        val status = page.text("", MalletTextRole.CAPTION, MalletTextTone.MUTED)
        status.gravity = Gravity.CENTER_HORIZONTAL
        fun show(tone: MalletTextTone) {
            large.tone = tone
            status.text = context.getString(R.string.icons_tone_status, tone.name)
        }
        show(MalletTextTone.MUTED)
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        MalletTextTone.values().forEach { tone ->
            val name = tone.name.lowercase(Locale.ROOT)
            val glyph = MalletIconView(context).apply {
                icon = MalletIcon.SHIELD
                this.tone = tone
            }
            val label = page.text(name, MalletTextRole.MICRO, MalletTextTone.MUTED)
            label.gravity = Gravity.CENTER_HORIZONTAL
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, page.dp(10), 0, page.dp(8))
                contentDescription = name
                addView(glyph, LinearLayout.LayoutParams(page.dp(24), page.dp(24)))
                addView(label, spaced(page.dp(6)))
            }
            MalletPress.attach(cell, cell) { show(tone) }
            row.addView(cell, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
        }
        page.section(R.string.icons_tones_title, R.string.icons_tones_detail) {
            val box = LinearLayout.LayoutParams(page.dp(40), page.dp(40))
            box.gravity = Gravity.CENTER_HORIZONTAL
            box.topMargin = page.dp(12)
            addView(large, box)
            addView(status, spaced(page.dp(8)))
            addDivider(10f)
            addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
        }
        page.code("""
            val shield = MalletIconView(this)
            shield.icon = MalletIcon.SHIELD
            shield.tone = MalletTextTone.DANGER
            // A colour overrides the tone; null restores it.
            shield.color = MalletTheme.of(this).colors.ink
        """)
    }

    /** Directional icons around one that is not, laid out in both reading directions. */
    private fun direction(page: PageScope) {
        val context = page.context
        fun row(direction: Int): View = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = direction
            DIRECTION_ICONS.forEach { icon ->
                val message = "MalletIcon.${icon.name}.isDirectional = ${icon.isDirectional}"
                addView(page.cell(icon, message), LinearLayout.LayoutParams(0, PageScope.MATCH, 1f))
            }
        }
        fun heading(label: Int): View = page.text(page.str(label), MalletTextRole.CAPTION, MalletTextTone.MUTED)
        val inset = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply {
            topMargin = page.dp(6)
            marginStart = page.dp(6)
            marginEnd = page.dp(6)
        }
        page.section(R.string.icons_direction_title, R.string.icons_direction_detail) {
            addView(heading(R.string.icons_ltr), inset)
            addView(row(View.LAYOUT_DIRECTION_LTR), LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            addDivider(4f)
            addView(heading(R.string.icons_rtl), LinearLayout.LayoutParams(inset))
            addView(row(View.LAYOUT_DIRECTION_RTL), LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
        }
        page.code("""
            val row = LinearLayout(this)
            row.layoutDirection = View.LAYOUT_DIRECTION_RTL
            val back = MalletIconView(this)
            back.icon = MalletIcon.CHEVRON_LEFT
            row.addView(back)
            val mirrored = back.icon?.isDirectional
        """)
    }

    private val DIRECTION_ICONS = listOf(
        MalletIcon.CHEVRON_LEFT, MalletIcon.CHEVRON_RIGHT, MalletIcon.CHECK,
        MalletIcon.ARROW_LEFT, MalletIcon.ARROW_RIGHT,
    )

    private fun spaced(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = top }
}
