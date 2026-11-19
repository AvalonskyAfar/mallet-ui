package io.github.avalonskyafar.mallet.showcase.pages

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import io.github.avalonskyafar.mallet.material.MalletGutters
import io.github.avalonskyafar.mallet.material.MalletMaterial
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletHue
import io.github.avalonskyafar.mallet.theme.MalletSpacing
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.widget.MalletSegmentedTrack
import io.github.avalonskyafar.mallet.widget.MalletSwitch
import io.github.avalonskyafar.mallet.widget.MalletText
import kotlin.math.roundToInt

/** Colour and hues: the neutral and semantic roles, the five hues and how tracks use them. */
object ColourPage : DemoPage("f_colour", R.string.page_f_colour_title, R.string.page_f_colour_summary) {
    override fun build(page: PageScope) {
        page.title()
        neutrals(page)
        semantics(page)
        hues(page)
        scale(page)
        dayAndNight(page)
        page.caption(page.str(R.string.colour_origin), topDp = 22)
    }

    private fun neutrals(page: PageScope) {
        val colors = page.theme.colors
        val roles = listOf(
            Triple("canvas", R.string.colour_role_canvas, colors.canvas),
            Triple("surface", R.string.colour_role_surface, colors.surface),
            Triple("raised", R.string.colour_role_raised, colors.raised),
            Triple("ink", R.string.colour_role_ink, colors.ink),
            Triple("muted", R.string.colour_role_muted, colors.muted),
            Triple("hairline", R.string.colour_role_hairline, colors.hairline),
            Triple("divider", R.string.colour_role_divider, colors.divider),
        )
        page.section(R.string.colour_neutral_title, R.string.colour_neutral_detail) {
            roles.forEachIndexed { index, (name, use, color) ->
                if (index > 0) addDivider()
                addView(swatchRow(page, name, page.str(use), color), cardParams())
            }
        }
        page.code("""
            val colors = MalletTheme.of(context).colors
            view.setBackgroundColor(colors.canvas)
            label.setTextColor(colors.ink)
            hint.setTextColor(colors.muted)
        """)
    }

    private fun semantics(page: PageScope) {
        val colors = page.theme.colors
        val roles = listOf(
            Triple(MalletTextTone.ACCENT, R.string.colour_accent_line, colors.accent),
            Triple(MalletTextTone.DANGER, R.string.colour_danger_line, colors.danger),
            Triple(MalletTextTone.WARNING, R.string.colour_warning_line, colors.warning),
            Triple(MalletTextTone.SUCCESS, R.string.colour_success_line, colors.success),
        )
        page.section(R.string.colour_semantic_title, R.string.colour_semantic_detail) {
            roles.forEachIndexed { index, (tone, line, color) ->
                if (index > 0) addDivider()
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(page.dp(ROW_SIDE), page.dp(11), page.dp(ROW_SIDE), page.dp(11))
                }
                row.addView(page.line(page.str(line), MalletTextRole.BODY, tone))
                val name = "${tone.name.lowercase()}  ${hex(color)}"
                row.addView(
                    page.line(name, MalletTextRole.MICRO, MalletTextTone.MUTED),
                    LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(3) },
                )
                addView(row, cardParams())
            }
        }
        page.code("""
            val error = MalletText.create(
                context, "Could not save.",
                MalletTextRole.BODY, MalletTextTone.DANGER,
            )
            link.tone = MalletTextTone.ACCENT
            val colors = MalletTheme.of(context).colors
            badge.setTextColor(colors.success)
        """)
    }

    private fun hues(page: PageScope) {
        val theme = page.theme
        val set = theme.hues
        val all = listOf(
            "bronze" to set.bronze,
            "ochre" to set.ochre,
            "cobalt" to set.cobalt,
            "jade" to set.jade,
            "mist" to set.mist,
        )
        page.section(R.string.colour_hue_title, R.string.colour_hue_detail) {
            all.forEachIndexed { index, (name, hue) ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPaddingRelative(page.dp(ROW_SIDE), page.dp(if (index == 0) 8 else 2), page.dp(6), page.dp(2))
                }
                row.addView(
                    page.line(name, MalletTextRole.LABEL),
                    LinearLayout.LayoutParams(0, PageScope.WRAP, 2f),
                )
                row.addView(
                    HueBed(context, theme, hue, hex(theme.bedColor(hue))),
                    LinearLayout.LayoutParams(0, PageScope.WRAP, 3f),
                )
                addView(row, cardParams())
            }
        }
        page.code("""
            val theme = MalletTheme.of(context)
            val hue = theme.hues.jade
            val bed = theme.bedColor(hue)
            val edge = theme.bedEdgeColor(hue)
            val material = MalletMaterial(context, theme)
            material.drawTrack(canvas, bounds, bed, edge)
            paint.color = theme.labelOnBed(hue)
            canvas.drawText("Jade", x, baseline, paint)
        """)
    }

    /**
     * One card with two groups: three tracks that take the scale and, under a divider, one track
     * whose options have no order. Each group ends with the line its listener writes.
     */
    private fun scale(page: PageScope) {
        val theme = page.theme
        val week = page.str(R.string.colour_opt_week)
        val status = page.line(page.str(R.string.colour_scale_idle), MalletTextRole.CAPTION, MalletTextTone.MUTED)
        val grouped = page.line(
            page.context.getString(R.string.colour_plain_status, week), MalletTextRole.CAPTION, MalletTextTone.MUTED,
        )
        val sets = listOf(
            listOf(R.string.colour_opt_off, R.string.colour_opt_on),
            listOf(R.string.colour_opt_off, R.string.colour_opt_gentle, R.string.colour_opt_strict),
            listOf(R.string.colour_opt_off, R.string.colour_opt_gentle, R.string.colour_opt_standard, R.string.colour_opt_strict),
        )
        page.section(R.string.colour_scale_title, R.string.colour_scale_detail) {
            addView(page.line(page.str(R.string.colour_scale_ordered), MalletTextRole.LABEL), lineParams(page))
            sets.forEachIndexed { index, labels ->
                val track = MalletSegmentedTrack(context).apply {
                    options = labels.map { page.str(it) }
                    select(if (index == 2) 3 else 1, animate = false)
                    setOnSelectionChangeListener { view, chosen ->
                        status.text = page.context.getString(
                            R.string.colour_scale_status, view.options[chosen], chosen + 1, view.options.size,
                        )
                    }
                }
                addView(track, cardParams().apply { topMargin = page.dp(if (index == 0) 0 else 4) })
            }
            addView(status, lineParams(page))
            addDivider(MalletSpacing.DIVIDER_GAP)
            addView(page.line(page.str(R.string.colour_plain_title), MalletTextRole.LABEL), lineParams(page))
            val plain = MalletSegmentedTrack(context).apply {
                setOptions(page.str(R.string.colour_opt_day), week, page.str(R.string.colour_opt_month))
                select(1, animate = false)
                setHues(theme.hues.uniform(3, theme.hues.mist))
                setOnSelectionChangeListener { view, chosen ->
                    grouped.text = page.context.getString(R.string.colour_plain_status, view.options[chosen])
                }
            }
            addView(plain, cardParams())
            addView(grouped, lineParams(page))
        }
        page.code("""
            val track = MalletSegmentedTrack(context)
            track.setOptions(
                "Off", "Gentle", "Standard", "Strict",
            )
            // no hues set: it takes theme.hues.scale(4)
            track.select(3, animate = false)
            track.setOnSelectionChangeListener { _, index ->
                status.text = track.options[index]
            }

            val hues = MalletTheme.of(context).hues
            val plain = MalletSegmentedTrack(context)
            plain.setOptions("Day", "Week", "Month")
            plain.setHues(hues.uniform(3, hues.mist))
            plain.setHues(null)   // back to the scale
        """)
    }

    /**
     * The twins stand on the canvas, side by side and without a card around them: each panel is a
     * piece of the canvas of its own theme.
     */
    private fun dayAndNight(page: PageScope) {
        val day = ThemedPanel(page, MalletTheme.light(), R.string.colour_day)
        val night = ThemedPanel(page, MalletTheme.dark(), R.string.colour_night)
        day.mirror(night)
        night.mirror(day)
        val pair = LinearLayout(page.context).apply { orientation = LinearLayout.HORIZONTAL }
        pair.addView(day.view, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
        pair.addView(night.view, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f).apply { marginStart = page.dp(8) })
        // The pair stands where the body of a card would: inside the gutter a card keeps for its shadow.
        val density = page.context.resources.displayMetrics.density
        val side = (MalletGutters.SURFACE_SIDE_DP * density).roundToInt()
        pair.setPadding(
            side, (MalletGutters.SURFACE_TOP_DP * density).roundToInt(),
            side, (MalletGutters.SURFACE_BOTTOM_DP * density).roundToInt(),
        )
        page.heading(R.string.colour_daynight_title, R.string.colour_daynight_detail)
        page.loose(pair, topDp = PageScope.HEADING_GAP)
        page.code("""
            val dark = MalletTheme.dark()
            val night = MalletTheme.wrap(context, dark)
            val panel = LinearLayout(night)
            panel.setBackgroundColor(dark.colors.canvas)
            panel.addView(MalletSwitch(night))
            panel.addView(MalletSegmentedTrack(night))
        """)
    }
}

/**
 * One half of "Day and night": a switch and a track in an explicit theme on that theme's canvas,
 * with the rim and the corners of a card, since it stands on the page where a card would.
 */
private class ThemedPanel(page: PageScope, theme: MalletTheme, title: Int) {
    private val themed: Context = MalletTheme.wrap(page.context, theme)
    private val toggle = MalletSwitch(themed).apply { setChecked(true, false) }
    private val track = MalletSegmentedTrack(themed).apply {
        setOptions(page.str(R.string.colour_opt_low), page.str(R.string.colour_opt_mid), page.str(R.string.colour_opt_high))
        select(1, animate = false)
    }

    val view: LinearLayout = LinearLayout(themed).apply {
        orientation = LinearLayout.VERTICAL
        val density = resources.displayMetrics.density
        background = GradientDrawable().apply {
            setColor(theme.colors.canvas)
            cornerRadius = MalletMaterial.RADIUS_CARD_DP * density
            setStroke((MalletMaterial.RIM_DP * density).roundToInt().coerceAtLeast(1), theme.colors.hairline)
        }
        setPadding(page.dp(8), page.dp(8), page.dp(8), page.dp(6))
        val head = LinearLayout(themed).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(page.dp(4), 0, 0, 0)
        }
        head.addView(
            MalletText.create(themed, page.str(title), MalletTextRole.LABEL).apply {
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            },
            LinearLayout.LayoutParams(0, PageScope.WRAP, 1f),
        )
        head.addView(toggle, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP))
        addView(head, cardParams())
        addView(track, cardParams().apply { topMargin = page.dp(4) })
    }

    /** What the user does here also happens on [other], so the two themes can be compared state by state. */
    fun mirror(other: ThemedPanel) {
        toggle.setOnCheckedChangeListener { _, checked ->
            other.toggle.setChecked(checked, true)
        }
        track.setOnSelectionChangeListener { _, index ->
            other.track.select(index)
        }
    }
}

/** Swatch, role name with its use under it, and the hex value at the end. */
private fun swatchRow(page: PageScope, name: String, use: String, @ColorInt color: Int): View {
    val row = LinearLayout(page.context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(page.dp(ROW_SIDE), page.dp(9), page.dp(ROW_SIDE), page.dp(9))
    }
    row.addView(Swatch(page.context, color, page.theme.colors.hairline), LinearLayout.LayoutParams(page.dp(40), page.dp(28)))
    val words = LinearLayout(page.context).apply { orientation = LinearLayout.VERTICAL }
    words.addView(page.line(name, MalletTextRole.LABEL))
    words.addView(
        page.line(use, MalletTextRole.MICRO, MalletTextTone.MUTED),
        LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(2) },
    )
    row.addView(words, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f).apply {
        marginStart = page.dp(12); marginEnd = page.dp(8)
    })
    val value = page.text(hex(color), MalletTextRole.CAPTION, MalletTextTone.MUTED)
    value.textDirection = View.TEXT_DIRECTION_LTR
    row.addView(value, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP))
    return row
}

/** A flat chip of one colour with a hairline rim, so that pale roles stay visible on the card. */
@SuppressLint("ViewConstructor") // built in code only
private class Swatch(context: Context, @ColorInt private val color: Int, @ColorInt private val rim: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val radius = 6f * density
        box.set(0.5f, 0.5f, width - 0.5f, height - 0.5f)
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(box, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = rim
        canvas.drawRoundRect(box, radius, radius, paint)
    }
}

/** A short recessed bed filled with one hue, with a label resting on it. */
@SuppressLint("ViewConstructor") // built in code only
private class HueBed(
    context: Context,
    private val theme: MalletTheme,
    private val hue: MalletHue,
    private val label: String,
) : View(context) {
    private val material = MalletMaterial(context, theme)
    private val bounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    init {
        MalletTextRole.LABEL.applyTo(paint, theme, resources.displayMetrics.density * resources.configuration.fontScale)
        contentDescription = label
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = material.dp(BED_DP + 1.5f + 6.5f).roundToInt()
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val side = material.dp(1.5f)
        bounds.set(side, side, width - side, side + material.dp(BED_DP))
        material.drawTrack(canvas, bounds, theme.bedColor(hue), theme.bedEdgeColor(hue))
        paint.color = theme.labelOnBed(hue)
        val metrics = paint.fontMetrics
        canvas.drawText(label, bounds.centerX(), bounds.centerY() - (metrics.ascent + metrics.descent) / 2f, paint)
    }

    private companion object {
        const val BED_DP = 38f
    }
}

private const val ROW_SIDE = 7

/**
 * Text inside a card or a panel. Like the headings of the page it stands at the start edge
 * whatever language it is written in, so an English line mirrors with the page right to left.
 */
private fun PageScope.line(value: CharSequence, role: MalletTextRole, tone: MalletTextTone = MalletTextTone.INK): MalletText =
    text(value, role, tone).apply { textAlignment = View.TEXT_ALIGNMENT_VIEW_START }

/** Where a label or a status line stands in a card of tracks: as far in as the text of a row. */
private fun lineParams(page: PageScope) = cardParams().apply {
    marginStart = page.dp(ROW_SIDE); marginEnd = page.dp(ROW_SIDE)
    topMargin = page.dp(6); bottomMargin = page.dp(6)
}

private fun cardParams() = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP)

/** "#RRGGBB", or "#AARRGGBB" when the colour is not opaque. */
private fun hex(@ColorInt color: Int): String =
    if (color ushr 24 == 0xFF) String.format("#%06X", color and 0xFFFFFF) else String.format("#%08X", color)
