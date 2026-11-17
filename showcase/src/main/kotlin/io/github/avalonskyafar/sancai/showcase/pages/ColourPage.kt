package io.github.avalonskyafar.sancai.showcase.pages

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSwitch
import io.github.avalonskyafar.sancai.widget.SancaiText
import kotlin.math.roundToInt

/** Colour and glazes: the neutral and semantic roles, the five glazes and how tracks use them. */
object ColourPage : DemoPage("f_colour", R.string.page_f_colour_title, R.string.page_f_colour_summary) {
    override fun build(page: PageScope) {
        page.title()
        neutrals(page)
        semantics(page)
        glazes(page)
        scale(page)
        plain(page)
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
            val colors = SancaiTheme.of(context).colors
            view.setBackgroundColor(colors.canvas)
            label.setTextColor(colors.ink)
            hint.setTextColor(colors.muted)
        """)
    }

    private fun semantics(page: PageScope) {
        val colors = page.theme.colors
        val roles = listOf(
            Triple(SancaiTextTone.ACCENT, R.string.colour_accent_line, colors.accent),
            Triple(SancaiTextTone.DANGER, R.string.colour_danger_line, colors.danger),
            Triple(SancaiTextTone.WARNING, R.string.colour_warning_line, colors.warning),
            Triple(SancaiTextTone.SUCCESS, R.string.colour_success_line, colors.success),
        )
        page.section(R.string.colour_semantic_title, R.string.colour_semantic_detail) {
            roles.forEachIndexed { index, (tone, line, color) ->
                if (index > 0) addDivider()
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(page.dp(ROW_SIDE), page.dp(11), page.dp(ROW_SIDE), page.dp(11))
                }
                row.addView(page.text(page.str(line), SancaiTextRole.BODY, tone))
                val name = "${tone.name.lowercase()}  ${hex(color)}"
                row.addView(
                    page.text(name, SancaiTextRole.MICRO, SancaiTextTone.MUTED),
                    LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(3) },
                )
                addView(row, cardParams())
            }
        }
        page.code("""
            val error = SancaiText.create(
                context, "Could not save.",
                SancaiTextRole.BODY, SancaiTextTone.DANGER,
            )
            link.tone = SancaiTextTone.ACCENT
            val colors = SancaiTheme.of(context).colors
            badge.setTextColor(colors.success)
        """)
    }

    private fun glazes(page: PageScope) {
        val theme = page.theme
        val set = theme.glazes
        val all = listOf(
            "terracotta" to set.terracotta,
            "ochre" to set.ochre,
            "cobalt" to set.cobalt,
            "celadon" to set.celadon,
            "mist" to set.mist,
        )
        page.section(R.string.colour_glaze_title, R.string.colour_glaze_detail) {
            all.forEachIndexed { index, (name, glaze) ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPaddingRelative(page.dp(ROW_SIDE), page.dp(if (index == 0) 8 else 2), page.dp(6), page.dp(2))
                }
                row.addView(
                    page.text(name, SancaiTextRole.LABEL),
                    LinearLayout.LayoutParams(0, PageScope.WRAP, 2f),
                )
                row.addView(
                    GlazeBed(context, theme, glaze, hex(theme.bedColor(glaze))),
                    LinearLayout.LayoutParams(0, PageScope.WRAP, 3f),
                )
                addView(row, cardParams())
            }
        }
        page.code("""
            val theme = SancaiTheme.of(context)
            val glaze = theme.glazes.celadon
            val bed = theme.bedColor(glaze)
            val edge = theme.bedEdgeColor(glaze)
            val material = SancaiMaterial(context, theme)
            material.drawTrack(canvas, bounds, bed, edge)
            paint.color = theme.labelOnBed(glaze)
            canvas.drawText("Celadon", x, baseline, paint)
        """)
    }

    private fun scale(page: PageScope) {
        val status = page.text(page.str(R.string.colour_scale_idle), SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
        val sets = listOf(
            listOf(R.string.colour_opt_off, R.string.colour_opt_on),
            listOf(R.string.colour_opt_off, R.string.colour_opt_gentle, R.string.colour_opt_strict),
            listOf(R.string.colour_opt_off, R.string.colour_opt_gentle, R.string.colour_opt_standard, R.string.colour_opt_strict),
        )
        page.section(R.string.colour_scale_title, R.string.colour_scale_detail) {
            sets.forEachIndexed { index, labels ->
                val track = SancaiSegmentedTrack(context).apply {
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
            addView(status, cardParams().apply {
                marginStart = page.dp(ROW_SIDE); marginEnd = page.dp(ROW_SIDE)
                topMargin = page.dp(6); bottomMargin = page.dp(6)
            })
        }
        page.caption(page.str(R.string.colour_scale_note))
        page.code("""
            val track = SancaiSegmentedTrack(context)
            track.setOptions(
                "Off", "Gentle", "Standard", "Strict",
            )
            // no glazes set: it takes theme.glazes.scale(4)
            track.select(3, animate = false)
            track.setOnSelectionChangeListener { _, index ->
                status.text = track.options[index]
            }
        """)
    }

    private fun plain(page: PageScope) {
        val theme = page.theme
        val week = page.str(R.string.colour_opt_week)
        val status = page.text(
            page.context.getString(R.string.colour_plain_status, week), SancaiTextRole.CAPTION, SancaiTextTone.MUTED,
        )
        page.section(R.string.colour_plain_title, R.string.colour_plain_detail) {
            val track = SancaiSegmentedTrack(context).apply {
                setOptions(page.str(R.string.colour_opt_day), week, page.str(R.string.colour_opt_month))
                select(1, animate = false)
                setGlazes(theme.glazes.uniform(3, theme.glazes.mist))
                setOnSelectionChangeListener { view, chosen ->
                    status.text = page.context.getString(R.string.colour_plain_status, view.options[chosen])
                }
            }
            addView(track, cardParams())
            addView(status, cardParams().apply {
                marginStart = page.dp(ROW_SIDE); marginEnd = page.dp(ROW_SIDE)
                topMargin = page.dp(6); bottomMargin = page.dp(6)
            })
        }
        page.code("""
            val glazes = SancaiTheme.of(context).glazes
            track.setOptions("Day", "Week", "Month")
            track.setGlazes(glazes.uniform(3, glazes.mist))
            track.setGlazes(null)   // back to the scale
        """)
    }

    private fun dayAndNight(page: PageScope) {
        val day = ThemedPanel(page, SancaiTheme.light(), R.string.colour_day)
        val night = ThemedPanel(page, SancaiTheme.dark(), R.string.colour_night)
        day.mirror(night)
        night.mirror(day)
        page.section(R.string.colour_daynight_title, R.string.colour_daynight_detail) {
            val pair = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.addView(day.view, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
            pair.addView(night.view, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f).apply { marginStart = page.dp(8) })
            addView(pair, cardParams())
        }
        page.code("""
            val dark = SancaiTheme.dark()
            val night = SancaiTheme.wrap(context, dark)
            val panel = LinearLayout(night)
            panel.setBackgroundColor(dark.colors.canvas)
            panel.addView(SancaiSwitch(night))
            panel.addView(SancaiSegmentedTrack(night))
        """)
    }
}

/** One half of "Day and night": a switch and a track in an explicit theme on that theme's canvas. */
private class ThemedPanel(page: PageScope, theme: SancaiTheme, title: Int) {
    private val themed: Context = SancaiTheme.wrap(page.context, theme)
    private val toggle = SancaiSwitch(themed).apply { setChecked(true, false) }
    private val track = SancaiSegmentedTrack(themed).apply {
        setOptions(page.str(R.string.colour_opt_low), page.str(R.string.colour_opt_mid), page.str(R.string.colour_opt_high))
        select(1, animate = false)
    }

    val view: LinearLayout = LinearLayout(themed).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(theme.colors.canvas)
            cornerRadius = page.dp(SancaiMaterial.RADIUS_MEDIA_DP.roundToInt()).toFloat()
            setStroke(1, page.theme.colors.hairline)
        }
        setPadding(page.dp(8), page.dp(8), page.dp(8), page.dp(6))
        val head = LinearLayout(themed).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(page.dp(4), 0, 0, 0)
        }
        head.addView(
            SancaiText.create(themed, page.str(title), SancaiTextRole.LABEL),
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
    words.addView(page.text(name, SancaiTextRole.LABEL))
    words.addView(
        page.text(use, SancaiTextRole.MICRO, SancaiTextTone.MUTED),
        LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(2) },
    )
    row.addView(words, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f).apply {
        marginStart = page.dp(12); marginEnd = page.dp(8)
    })
    val value = page.text(hex(color), SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
    value.textDirection = View.TEXT_DIRECTION_LTR
    row.addView(value, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP))
    return row
}

/** A flat chip of one colour with a hairline rim, so that pale roles stay visible on the card. */
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

/** A short recessed bed poured with one glaze, with a label resting on it. */
private class GlazeBed(
    context: Context,
    private val theme: SancaiTheme,
    private val glaze: SancaiGlaze,
    private val label: String,
) : View(context) {
    private val material = SancaiMaterial(context, theme)
    private val bounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    init {
        SancaiTextRole.LABEL.applyTo(paint, theme, resources.displayMetrics.density * resources.configuration.fontScale)
        contentDescription = label
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = material.dp(BED_DP + 1.5f + 6.5f).roundToInt()
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val side = material.dp(1.5f)
        bounds.set(side, side, width - side, side + material.dp(BED_DP))
        material.drawTrack(canvas, bounds, theme.bedColor(glaze), theme.bedEdgeColor(glaze))
        paint.color = theme.labelOnBed(glaze)
        val metrics = paint.fontMetrics
        canvas.drawText(label, bounds.centerX(), bounds.centerY() - (metrics.ascent + metrics.descent) / 2f, paint)
    }

    private companion object {
        const val BED_DP = 38f
    }
}

private const val ROW_SIDE = 7

private fun cardParams() = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP)

/** "#RRGGBB", or "#AARRGGBB" when the colour is not opaque. */
private fun hex(@ColorInt color: Int): String =
    if (color ushr 24 == 0xFF) String.format("#%06X", color and 0xFFFFFF) else String.format("#%08X", color)
