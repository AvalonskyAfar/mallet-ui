package io.github.avalonskyafar.mallet.showcase.pages

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.theme.MalletWeight
import io.github.avalonskyafar.mallet.widget.MalletControlRow
import io.github.avalonskyafar.mallet.widget.MalletParagraph
import io.github.avalonskyafar.mallet.widget.MalletSurface
import io.github.avalonskyafar.mallet.widget.MalletSwitch
import io.github.avalonskyafar.mallet.widget.MalletText

/** Typography: the eleven roles, the six tones, reading copy and the larger text scale. */
object TypePage : DemoPage("f_type", R.string.page_f_type_title, R.string.page_f_type_summary) {
    override fun build(page: PageScope) {
        page.title()
        scale(page)
        tones(page)
        reading(page)
        largerText(page)
        page.caption(page.str(R.string.type_origin), topDp = 18)
    }

    /** Every role: its name in its own style, its size and weight beside it. */
    private fun scale(page: PageScope) {
        page.section(R.string.type_scale_title, R.string.type_scale_detail) {
            MalletTextRole.values().forEachIndexed { index, role ->
                if (index > 0) addDivider()
                val weight = page.str(
                    when (role.weight) {
                        MalletWeight.REGULAR -> R.string.type_weight_regular
                        MalletWeight.MEDIUM -> R.string.type_weight_medium
                        MalletWeight.BOLD -> R.string.type_weight_bold
                    },
                )
                val spec = page.context.getString(R.string.type_spec, sizeLabel(role.sizeSp), weight)
                addView(specimen(page, page.text(role.name, role), spec, minHeightDp = 44), cardParams())
            }
        }
        page.code("""
            val title = MalletText.create(context, "Typography", MalletTextRole.TITLE)
            val note = MalletText(context)
            note.role = MalletTextRole.CAPTION
            note.tone = MalletTextTone.MUTED
            val plain = TextView(context)
            MalletTextRole.BODY.applyTo(plain, MalletTheme.of(context))
        """)
    }

    /** One sentence per tone, with the name of the tone beside it. */
    private fun tones(page: PageScope) {
        page.section(R.string.type_tones_title, R.string.type_tones_detail) {
            MalletTextTone.values().forEachIndexed { index, tone ->
                if (index > 0) addDivider()
                val line = page.text(page.str(toneSample(tone)), MalletTextRole.BODY_COMPACT, tone)
                addView(specimen(page, line, tone.name, minHeightDp = 44), cardParams())
            }
        }
        page.code("""
            val warning = MalletText.create(context, "Storage is almost full",
                MalletTextRole.BODY_COMPACT, MalletTextTone.WARNING)
            warning.tone = MalletTextTone.DANGER
            val ink = MalletTextTone.INK.resolve(MalletTheme.of(context).colors)
        """)
    }

    /** The same copy in the two body roles, long enough to show the leading. */
    private fun reading(page: PageScope) {
        page.section(R.string.type_reading_title, R.string.type_reading_detail) {
            listOf(MalletTextRole.BODY, MalletTextRole.BODY_COMPACT).forEachIndexed { index, role ->
                if (index > 0) addDivider(10f)
                val label = page.text(role.name, MalletTextRole.MICRO, MalletTextTone.MUTED).atStart()
                val copy = MalletParagraph(context).apply {
                    this.role = role
                    text = page.str(R.string.type_reading_copy)
                }
                addView(label, insetParams(page, topDp = if (index == 0) 6 else 0))
                addView(copy, insetParams(page, topDp = 6, bottomDp = if (index == 0) 0 else 8))
            }
        }
        page.code("""
            val copy = MalletParagraph(context)
            copy.role = MalletTextRole.BODY_COMPACT
            copy.text = "字形重要，行距也重要。"
            copy.protectClosingPunctuation = true
        """)
    }

    /** One heading and one row, built twice: at text scale 1.0 and at the larger scale. */
    private fun largerText(page: PageScope) {
        val large = page.theme.copy(textScale = MalletTheme.LARGE_TEXT_SCALE)
        val standard = ScaledSample(page, MalletTheme.wrap(page.context, page.theme.copy(textScale = 1f)), 1f)
        val larger = ScaledSample(page, MalletTheme.wrap(page.context, large), large.textScale)
        standard.mirror(larger)
        larger.mirror(standard)
        page.section(R.string.type_large_title, R.string.type_large_detail) {
            standard.addTo(this, page, first = true)
            addDivider(6f)
            larger.addTo(this, page, first = false)
        }
        page.code("""
            val theme = MalletTheme.of(context)
            val large = theme.copy(textScale = MalletTheme.LARGE_TEXT_SCALE)
            val scaled = MalletTheme.wrap(context, large)
            val heading = MalletText.create(scaled, "Display", MalletTextRole.HEADING)
            val row = MalletControlRow(scaled)
            row.title = "Larger text"
            row.setControl(MalletSwitch(scaled))
        """)
    }

    /** A line of a card: [sample] at the start, [note] in MICRO muted at the end, on one baseline. */
    private fun specimen(page: PageScope, sample: MalletText, note: String, minHeightDp: Int): LinearLayout {
        val row = LinearLayout(page.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = true
            minimumHeight = page.dp(minHeightDp)
            setPaddingRelative(page.dp(INSET), page.dp(6), page.dp(INSET), page.dp(6))
        }
        val mark = page.text(note, MalletTextRole.MICRO, MalletTextTone.MUTED).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        }
        row.addView(sample.atStart(), LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
        row.addView(mark, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP).apply { marginStart = page.dp(12) })
        return row
    }

    private fun toneSample(tone: MalletTextTone): Int = when (tone) {
        MalletTextTone.INK -> R.string.type_tone_ink
        MalletTextTone.MUTED -> R.string.type_tone_muted
        MalletTextTone.ACCENT -> R.string.type_tone_accent
        MalletTextTone.DANGER -> R.string.type_tone_danger
        MalletTextTone.WARNING -> R.string.type_tone_warning
        MalletTextTone.SUCCESS -> R.string.type_tone_success
    }

    /** 20.0 reads "20", 15.5 stays "15.5". */
    private fun sizeLabel(sizeSp: Float): String =
        if (sizeSp == sizeSp.toInt().toFloat()) sizeSp.toInt().toString() else sizeSp.toString()

    private fun cardParams() = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP)

    private fun insetParams(page: PageScope, topDp: Int = 0, bottomDp: Int = 0) = cardParams().apply {
        marginStart = page.dp(INSET)
        marginEnd = page.dp(INSET)
        topMargin = page.dp(topDp)
        bottomMargin = page.dp(bottomDp)
    }

    /** Text inset inside a card, on top of the 9 dp content padding of the card. */
    internal const val INSET = 7
}

/**
 * Text inside a card stands at the start edge whatever language it is written in, as the
 * headings on the canvas do; without it a Latin line keeps to the left in a right-to-left layout.
 */
private fun MalletText.atStart(): MalletText = apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START }

/** One half of "Larger text": a heading and a switch row in a theme with the given text scale. */
private class ScaledSample(page: PageScope, private val themed: Context, scale: Float) {
    private val toggle = MalletSwitch(themed).apply { setChecked(true, false) }
    private val label = MalletText.create(
        themed,
        page.context.getString(R.string.type_large_scale, if (scale == 1f) "1.0" else scale.toString()),
        MalletTextRole.MICRO,
        MalletTextTone.MUTED,
    ).atStart()
    private val heading =
        MalletText.create(themed, page.str(R.string.type_large_heading), MalletTextRole.HEADING).atStart()
    private val row = MalletControlRow(themed).apply {
        title = page.str(R.string.type_large_row_title)
        detail = page.str(R.string.type_large_row_on)
        setControl(toggle)
    }

    /** The switch here also moves the one of [other], so the two scales stay comparable. */
    fun mirror(other: ScaledSample) {
        toggle.setOnCheckedChangeListener { _, checked ->
            show(checked)
            other.toggle.setChecked(checked, true)
            other.show(checked)
        }
    }

    private fun show(checked: Boolean) {
        row.detail = themed.getString(if (checked) R.string.type_large_row_on else R.string.type_large_row_off)
    }

    fun addTo(card: MalletSurface, page: PageScope, first: Boolean) {
        val side = page.dp(TypePage.INSET)
        fun params(topDp: Int) = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply {
            marginStart = side
            marginEnd = side
            topMargin = page.dp(topDp)
        }
        card.addView(label, params(if (first) 6 else 0))
        card.addView(heading, params(8))
        card.addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(2) })
    }
}
