package io.github.avalonskyafar.mallet.showcase.pages

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import io.github.avalonskyafar.mallet.widget.MalletText
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** The icon set: every glyph, its size and weight, its tones and its mirroring. */
object IconsPage : DemoPage("f_icons", R.string.page_f_icons_title, R.string.page_f_icons_summary) {
    private const val COLUMNS = 5
    private const val STAGE_DP = 72
    private const val LABEL_GAP_DP = 12
    private val STROKES = floatArrayOf(1.5f, 2f, 2.5f)

    /** A zero-width space: a place where a name may break without showing a hyphen. */
    private const val BREAK = "\u200B"

    override fun build(page: PageScope) {
        page.title()
        grid(page)
        sizeAndWeight(page)
        tones(page)
        direction(page)
        page.caption(page.str(R.string.icons_origin), topDp = 22)
    }

    /**
     * All icons, five to a row, or fewer where large text needs wider cells. A cell sinks under
     * the finger and names its constant.
     */
    private fun grid(page: PageScope) {
        page.section(R.string.icons_all_title, R.string.icons_all_detail) {
            val cells = CellRows(context, COLUMNS)
            MalletIcon.values().forEach { icon -> cells.addView(page.cell(icon)) }
            addView(cells, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
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
        val label = text(name.replace("_", "_$BREAK"), MalletTextRole.MICRO, MalletTextTone.MUTED)
        label.gravity = Gravity.CENTER_HORIZONTAL
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(2), dp(10), dp(2), dp(8))
            // The room the longest word of the name needs: CellRows keeps to it.
            minimumWidth = label.longestWord() + dp(4)
            contentDescription = name.replace('_', ' ')
            addView(glyph, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(label, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = dp(6) })
            MalletPress.attach(this, this) { host.say(message) }
        }
    }

    /** Width in px of the longest stretch of this label that has to stay on one line. */
    private fun MalletText.longestWord(): Int = text.split(BREAK).maxOf { ceil(paint.measureText(it)).toInt() }

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
            // Both numbers go in as text, so the line shows the digits of the slider and the
            // track under it in every locale, not two digit systems side by side.
            val stroke = track.options[track.selectedIndex]
            status.text = context.getString(R.string.icons_size_status, side.toString(), stroke)
        }
        slider.setOnValueChangeListener { _, _ -> show() }
        track.setOnSelectionChangeListener { _, _ -> show() }
        show()
        val column = page.labelColumn(R.string.icons_size_label, R.string.icons_stroke_label)
        page.section(R.string.icons_size_title, R.string.icons_size_detail) {
            addView(stage, LinearLayout.LayoutParams(PageScope.MATCH, page.dp(STAGE_DP + 16)))
            addView(status, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            addDivider(10f)
            addView(page.labelled(R.string.icons_size_label, column, slider))
            addView(page.labelled(R.string.icons_stroke_label, column, track), spaced(page.dp(6)))
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

    /** The width the [labels] of a card share: 64 dp, or more where large text needs it. */
    private fun PageScope.labelColumn(vararg labels: Int): Int {
        val paint = text("", MalletTextRole.LABEL).paint
        val widest = labels.maxOf { paint.measureText(str(it)) }
        return max(dp(64), ceil(widest).toInt() + dp(LABEL_GAP_DP))
    }

    /** A label in a column of [column] px at the start, and [control] filling the rest of the line. */
    private fun PageScope.labelled(label: Int, column: Int, control: View): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val name = text(str(label), MalletTextRole.LABEL)
        // At the start edge of the line whatever script the label is written in.
        name.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        addView(name, LinearLayout.LayoutParams(column, PageScope.WRAP).apply { marginStart = dp(6) })
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
        // All six in one row, or three and three where large text needs wider cells.
        val row = CellRows(context, MalletTextTone.values().size)
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
                // The name on one line, with a little air between it and its neighbours.
                minimumWidth = label.longestWord() + page.dp(6)
                contentDescription = name
                addView(glyph, LinearLayout.LayoutParams(page.dp(24), page.dp(24)))
                addView(label, spaced(page.dp(6)))
            }
            MalletPress.attach(cell, cell) { show(tone) }
            row.addView(cell)
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
        // A caption stands at the start edge of the card whatever script it is written in.
        fun heading(label: Int): View = page.text(page.str(label), MalletTextRole.CAPTION, MalletTextTone.MUTED)
            .apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
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

/**
 * Equal cells in rows: [most] to a row, or fewer where the width cannot give every cell its
 * minimum width. A cell asks for the room its longest word needs, so large text gets wider
 * cells and more rows instead of a name broken inside a word. The rows are balanced: six
 * cells that do not fit one row become three and three, not five and one.
 */
@SuppressLint("ViewConstructor") // built in code only
private class CellRows(context: Context, private val most: Int) : ViewGroup(context) {
    private var perRow = most

    /** Nothing scrolls here, so a press on a cell shows at once. */
    override fun shouldDelayChildPressedState(): Boolean = false

    /**
     * Where cell [index] of a row [width] px wide begins. Pixels left over go to the last
     * cells, one each, as a row of equal weights in a `LinearLayout` shares them.
     */
    private fun edge(index: Int, width: Int): Int =
        index * (width / perRow) + max(0, index - (perRow - width % perRow))

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cells = (0 until childCount).map { getChildAt(it) }
        val need = max(1, cells.maxOfOrNull { it.minimumWidth } ?: 1)
        val unbounded = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
        val width = if (unbounded) need * most else MeasureSpec.getSize(widthMeasureSpec)
        val fit = (width / need).coerceIn(1, most)
        val rows = (cells.size + fit - 1) / fit
        perRow = if (rows == 0) most else (cells.size + rows - 1) / rows
        var height = 0
        cells.chunked(perRow).forEach { row ->
            fun measure(heightSpec: Int) = row.forEachIndexed { column, cell ->
                val wide = edge(column + 1, width) - edge(column, width)
                cell.measure(MeasureSpec.makeMeasureSpec(wide, MeasureSpec.EXACTLY), heightSpec)
            }
            measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            // Every cell is as tall as the tallest of its row: all of it sinks under the finger.
            val tallest = row.maxOf { it.measuredHeight }
            measure(MeasureSpec.makeMeasureSpec(tallest, MeasureSpec.EXACTLY))
            height += tallest
        }
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val mirrored = layoutDirection == LAYOUT_DIRECTION_RTL
        var y = 0
        for (index in 0 until childCount) {
            val cell = getChildAt(index)
            val column = index % perRow
            // Right to left the first cell of a row stands at the right edge.
            val x = if (mirrored) width - edge(column + 1, width) else edge(column, width)
            cell.layout(x, y, x + cell.measuredWidth, y + cell.measuredHeight)
            if (column == perRow - 1) y += cell.measuredHeight
        }
    }
}
