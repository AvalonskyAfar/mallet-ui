package io.github.avalonskyafar.sancai.showcase.pages

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.material.SancaiCorners
import io.github.avalonskyafar.sancai.material.SancaiGutters
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.material.SancaiSurfaceDrawable
import io.github.avalonskyafar.sancai.material.SancaiWellDrawable
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiDivider
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiText
import kotlin.math.roundToInt

/** Materials and depth: porcelain, the glazed track and its thumb, wells, and where the shadow lives. */
object MaterialPage : DemoPage("f_material", R.string.page_f_material_title, R.string.page_f_material_summary) {
    override fun build(page: PageScope) {
        page.title()
        porcelain(page)
        trackAndThumb(page)
        wells(page)
        gutter(page)
        splitCard(page)
        page.caption(page.str(R.string.material_origin), topDp = 18)
    }

    private fun porcelain(page: PageScope) {
        page.section(R.string.material_porcelain_title, R.string.material_porcelain_detail) {
            addView(page.line(R.string.material_porcelain_card), cardParams())
        }
        val flatLabel = page.line(R.string.material_porcelain_flat)
        val flat = SancaiSurface(page.context).apply {
            elevated = false
            addView(flatLabel, cardParams())
        }
        page.loose(flat, topDp = 8)

        val lift = SancaiButton(page.context).apply {
            text = page.str(R.string.material_porcelain_lift)
            setOnClickListener {
                flat.elevated = !flat.elevated
                text = page.str(if (flat.elevated) R.string.material_porcelain_flatten else R.string.material_porcelain_lift)
                flatLabel.text = page.str(
                    if (flat.elevated) R.string.material_porcelain_lifted else R.string.material_porcelain_flat,
                )
            }
        }
        val glazed = SancaiButton(page.context).apply {
            variant = SancaiButton.Variant.PRIMARY
            text = page.str(R.string.material_porcelain_glazed)
            setOnClickListener { page.host.say(page.str(R.string.material_porcelain_said)) }
        }
        page.loose(page.pair(lift, glazed), topDp = 12)
        page.code(PORCELAIN_CODE)
    }

    private fun trackAndThumb(page: PageScope) {
        val specimen = TrackSpecimen(page.context)
        val status = page.text(page.str(R.string.material_track_rest_note), SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
        page.section(R.string.material_track_title, R.string.material_track_detail) {
            val state = SancaiSegmentedTrack(context).apply {
                setOptions(page.str(R.string.material_track_rest), page.str(R.string.material_track_pressed))
                setOnSelectionChangeListener { _, index ->
                    specimen.down = index == 1
                    status.text = page.str(
                        if (index == 1) R.string.material_track_pressed_note else R.string.material_track_rest_note,
                    )
                }
            }
            addView(state, cardParams())
            addDivider(5f)
            addView(specimen, cardParams(topDp = 6, sideDp = 4))
            addView(status, cardParams(topDp = 4, sideDp = 6, bottomDp = 4))
        }
        page.code(TRACK_CODE)
    }

    private fun wells(page: PageScope) {
        val colors = page.theme.colors
        page.section(R.string.material_well_title, R.string.material_well_detail) {
            val rims = listOf(
                R.string.material_well_default to null,
                R.string.material_well_focus to colors.ink,
                R.string.material_well_error to colors.danger,
            )
            rims.forEachIndexed { index, (label, rim) ->
                val well = page.text(page.str(label), SancaiTextRole.BODY_COMPACT).apply {
                    background = SancaiWellDrawable(context).apply { this.rim = rim }
                    setPadding(page.dp(13), page.dp(13), page.dp(13), page.dp(13))
                }
                addView(well, cardParams(topDp = if (index == 0) 0 else 8))
            }
        }
        page.code(WELL_CODE)
    }

    private fun gutter(page: PageScope) {
        val specimen = GutterSpecimen(page.context)
        val status = page.text(page.str(R.string.material_gutter_actual_note), SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
        page.section(R.string.material_gutter_title, R.string.material_gutter_detail) {
            val zoom = SancaiSegmentedTrack(context).apply {
                setOptions(page.str(R.string.material_gutter_actual), page.str(R.string.material_gutter_magnified))
                // The options are equals, not levels: one glaze under all of them.
                setGlazes(page.theme.glazes.uniform(2, page.theme.glazes.mist))
                setOnSelectionChangeListener { _, index ->
                    specimen.zoom = if (index == 1) GutterSpecimen.MAGNIFIED else 1f
                    status.text = page.str(
                        if (index == 1) R.string.material_gutter_magnified_note else R.string.material_gutter_actual_note,
                    )
                }
            }
            addView(zoom, cardParams())
            addView(status, cardParams(topDp = 2, sideDp = 6, bottomDp = 4))
        }
        page.loose(specimen, topDp = 10)
        page.code(GUTTER_CODE)
    }

    private fun splitCard(page: PageScope) {
        val top = page.section(R.string.material_split_title, R.string.material_split_detail) {
            corners = SancaiCorners.TOP
            addView(page.line(R.string.material_split_top), cardParams())
        }
        val divider = SancaiDivider(page.context)
        page.loose(divider)
        // The divider spans the body of the halves, not their shadow gutter.
        val side = (SancaiGutters.SURFACE_SIDE_DP * page.context.resources.displayMetrics.density).roundToInt()
        (divider.layoutParams as LinearLayout.LayoutParams).apply {
            marginStart = side
            marginEnd = side
        }
        val bottom = SancaiSurface(page.context).apply {
            corners = SancaiCorners.BOTTOM
            addView(page.line(R.string.material_split_bottom), cardParams())
        }
        page.loose(bottom)

        val fold = SancaiButton(page.context).apply {
            text = page.str(R.string.material_split_fold)
            setOnClickListener {
                val folded = bottom.visibility == View.VISIBLE
                bottom.visibility = if (folded) View.GONE else View.VISIBLE
                divider.visibility = bottom.visibility
                top.corners = if (folded) SancaiCorners.ALL else SancaiCorners.TOP
                text = page.str(if (folded) R.string.material_split_unfold else R.string.material_split_fold)
            }
        }
        page.loose(fold, topDp = 12, sideDp = PageScope.BUTTON_SIDE)
        page.code(SPLIT_CODE)
    }
}

/** A line of body text with the inner margin of a card row. */
private fun PageScope.line(id: Int): SancaiText = text(str(id), SancaiTextRole.BODY).apply {
    setPadding(dp(6), dp(7), dp(6), dp(7))
}

/** Two buttons side by side, sharing the width. */
private fun PageScope.pair(first: View, second: View): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    addView(first, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f).apply { marginEnd = dp(5) })
    addView(second, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f).apply { marginStart = dp(5) })
}

private fun View.cardParams(topDp: Int = 0, sideDp: Int = 0, bottomDp: Int = 0): LinearLayout.LayoutParams {
    val density = resources.displayMetrics.density
    return LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply {
        topMargin = (topDp * density).roundToInt()
        bottomMargin = (bottomDp * density).roundToInt()
        marginStart = (sideDp * density).roundToInt()
        marginEnd = marginStart
    }
}

/** A large glazed track with its thumb, painted straight from the material engine. */
private class TrackSpecimen(context: Context) : View(context) {
    private val theme = SancaiTheme.of(context)
    private val material = SancaiMaterial(context, theme)
    private val track = RectF()
    private val thumb = RectF()

    var down: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), material.dp(HEIGHT_DP).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        val glaze = theme.glazes.cobalt
        // The gutter of a track: 1.5 dp at the sides and above, 6.5 dp below for the shadow.
        track.set(material.dp(1.5f), material.dp(1.5f), width - material.dp(1.5f), height - material.dp(6.5f))
        material.drawTrack(canvas, track, theme.bedColor(glaze), theme.bedEdgeColor(glaze), down)

        val inset = material.dp(5f)
        val half = track.width() / 2f
        val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) track.left + half else track.left + inset
        thumb.set(left, track.top + inset, left + half - inset, track.bottom - inset)
        material.drawThumb(canvas, thumb, down)
    }

    private companion object {
        const val HEIGHT_DP = 80f
    }
}

/** A porcelain body with the bounds of its own view drawn as a dashed line around it. */
private class GutterSpecimen(context: Context) : View(context) {
    private val theme = SancaiTheme.of(context)
    private val density = resources.displayMetrics.density
    private val porcelain = SancaiSurfaceDrawable(context)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = theme.colors.accent
    }

    /** 1 for the real size; above 1 the bottom start corner is magnified. */
    var zoom: Float = 1f
        set(value) {
            if (field != value) {
                field = value
                dash = null
                invalidate()
            }
        }

    /** The dashes of the outline at the current zoom; made when first drawn. */
    private var dash: DashPathEffect? = null

    private fun newDash(stroke: Float) = DashPathEffect(floatArrayOf(4f * stroke, 3f * stroke), 0f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), (HEIGHT_DP * density).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        val pivotX = if (layoutDirection == LAYOUT_DIRECTION_RTL) width.toFloat() else 0f
        canvas.save()
        // The magnified drawing is larger than the view; keep it inside.
        canvas.clipRect(0, 0, width, height)
        canvas.scale(zoom, zoom, pivotX, height.toFloat())
        porcelain.setBounds(0, 0, width, height)
        porcelain.draw(canvas)

        val stroke = density / zoom
        outline.strokeWidth = stroke
        if (dash == null) dash = newDash(stroke)
        outline.pathEffect = dash
        canvas.drawRect(stroke / 2f, stroke / 2f, width - stroke / 2f, height - stroke / 2f, outline)
        canvas.restore()
    }

    companion object {
        const val MAGNIFIED = 4f
        private const val HEIGHT_DP = 88f
    }
}

private const val PORCELAIN_CODE = """
    val card = SancaiSurface(this)
    val flat = SancaiSurface(this).apply { elevated = false }
    val neutral = SancaiButton(this)
    val glazed = SancaiButton(this).apply { variant = SancaiButton.Variant.PRIMARY }
    neutral.setOnClickListener { flat.elevated = !flat.elevated }
"""

private const val TRACK_CODE = """
    // In onDraw of a custom view. `track` and `thumb` are RectF.
    // The track keeps 1.5 dp free at the sides and above, 6.5 dp below.
    val theme = SancaiTheme.of(context)
    val material = SancaiMaterial(context, theme)
    val bed = theme.bedColor(theme.glazes.cobalt)
    val edge = theme.bedEdgeColor(theme.glazes.cobalt)
    material.drawTrack(canvas, track, bed, edge, isPressed)
    material.drawThumb(canvas, thumb, isPressed)
"""

private const val WELL_CODE = """
    val colors = SancaiTheme.of(this).colors
    field.background = SancaiWellDrawable(this)
    focused.background = SancaiWellDrawable(this).apply { rim = colors.ink }
    invalid.background = SancaiWellDrawable(this).apply { rim = colors.danger }
"""

private const val GUTTER_CODE = """
    val porcelain = SancaiSurfaceDrawable(context)
    val body = RectF()
    porcelain.setBounds(0, 0, width, height)        // the view
    porcelain.bodyBounds(porcelain.bounds, body)    // the card the eye sees
    // SancaiGutters.SURFACE_SIDE_DP 1.4, SURFACE_TOP_DP 1, SURFACE_BOTTOM_DP 4.8
"""

private const val SPLIT_CODE = """
    val top = SancaiSurface(this).apply { corners = SancaiCorners.TOP }
    val bottom = SancaiSurface(this).apply { corners = SancaiCorners.BOTTOM }
    column.addView(top)
    column.addView(SancaiDivider(this))
    column.addView(bottom)
    top.corners = SancaiCorners.ALL    // folded: one whole card again
"""
