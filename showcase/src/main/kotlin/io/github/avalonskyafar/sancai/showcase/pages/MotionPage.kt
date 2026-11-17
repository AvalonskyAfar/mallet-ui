package io.github.avalonskyafar.sancai.showcase.pages

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiEntryRow
import io.github.avalonskyafar.sancai.widget.SancaiSlider
import kotlin.math.roundToInt

/** Motion tokens: the one curve, the durations, and the settle after a drag. */
object MotionPage : DemoPage("i_motion", R.string.page_i_motion_title, R.string.page_i_motion_summary) {
    override fun build(page: PageScope) {
        page.title()
        page.curveSection()
        page.durationSection()
        page.settleSection()
        page.caption(page.str(R.string.motion_origin), topDp = 18)
    }
}

/** The plot of the curve, and two beads that cross in the same time: one glides, one does not. */
private fun PageScope.curveSection() {
    val plot = CurveView(context, str(R.string.motion_axis_time), str(R.string.motion_axis_progress))
    val glazed = theme.glazes.celadon
    val glide = GrooveView(context, theme.bedColor(glazed), theme.bedEdgeColor(glazed))
    val linear = GrooveView(context, theme.neutralBed, theme.neutralBedEdge)
    var atEnd = false
    val play = SancaiButton(context).apply {
        text = str(R.string.motion_play)
        variant = SancaiButton.Variant.PRIMARY
        setOnClickListener {
            atEnd = !atEnd
            val target = if (atEnd) 1f else 0f
            glide.play(target, SancaiMotion.PAGE_MS, SancaiMotion.glide())
            linear.play(target, SancaiMotion.PAGE_MS, LinearInterpolator())
            plot.play(SancaiMotion.PAGE_MS)
        }
    }
    section(R.string.motion_curve_title, R.string.motion_curve_detail) {
        addView(plot, inCard(topDp = 5))
        addView(labelled(str(R.string.motion_curve_glide), glide), inCard(topDp = 14))
        addView(labelled(str(R.string.motion_curve_linear), linear), inCard(topDp = 8))
        addView(play, inCard(topDp = 14, bottomDp = 7, sideDp = 22))
    }
    code("""
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SancaiMotion.PAGE_MS
            interpolator = SancaiMotion.glide()
            addUpdateListener {
                bead.translationX = travel * it.animatedFraction
            }
            start()
        }
    """)
}

/** One row per duration token; a tap sends the bead at the top of the card across for that long. */
private fun PageScope.durationSection() {
    val tokens = listOf(
        Triple("CONTROL_MS", SancaiMotion.CONTROL_MS, R.string.motion_use_control),
        Triple("PAGE_MS", SancaiMotion.PAGE_MS, R.string.motion_use_page),
        Triple("DIALOG_MS", SancaiMotion.DIALOG_MS, R.string.motion_use_dialog),
        Triple("FEEDBACK_MS", SancaiMotion.FEEDBACK_MS, R.string.motion_use_feedback),
        Triple("PRESS_MS", SancaiMotion.PRESS_MS, R.string.motion_use_press),
        Triple("REDUCED_MS", SancaiMotion.REDUCED_MS, R.string.motion_use_reduced),
    )
    val glazed = theme.glazes.ochre
    val groove = GrooveView(context, theme.bedColor(glazed), theme.bedEdgeColor(glazed))
    val status = text(str(R.string.motion_durations_hint), SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
    var atEnd = false
    section(R.string.motion_durations_title, R.string.motion_durations_detail) {
        addView(groove, inCard(topDp = 7, sideDp = 6))
        addView(status, inCard(topDp = 6, bottomDp = 9, sideDp = 8))
        for ((name, millis, use) in tokens) {
            addDivider()
            val row = SancaiEntryRow(context).apply {
                title = name
                detail = str(use)
                value = context.getString(R.string.motion_ms, millis.toInt())
                arrow = SancaiEntryRow.Arrow.NONE
                alignment = SancaiEntryRow.Alignment.START
                setOnClickListener {
                    atEnd = !atEnd
                    groove.play(if (atEnd) 1f else 0f, millis, SancaiMotion.glide())
                    status.text = context.getString(R.string.motion_durations_playing, name, millis.toInt())
                }
            }
            addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
        }
    }
    code("""
        // 260 ms, or 120 ms with reduced motion
        val reduce = SancaiTheme.of(this).reduceMotion
        val ms = SancaiMotion.controlDuration(reduce)
        bead.animate()
            .translationX(travel)
            .setDuration(ms)
            .setInterpolator(SancaiMotion.glide())
    """)
}

/** The slider is the finger: it leaves a share of the way open; Play lets go. */
private fun PageScope.settleSection() {
    val glazed = theme.glazes.cobalt
    val groove = GrooveView(context, theme.bedColor(glazed), theme.bedEdgeColor(glazed))
    val status = text("", SancaiTextRole.BODY_COMPACT)
    val slider = SancaiSlider(context).apply {
        valueFrom = 0f
        valueTo = 1f
        stepSize = 0.05f
        value = 0.5f
        this.glaze = glazed
        valueFormatter = { "${(it * 100).roundToInt()}%" }
    }
    fun show() {
        val ms = SancaiMotion.settleDuration(slider.value, theme.reduceMotion)
        status.text = context.getString(R.string.motion_settle_status, (slider.value * 100).roundToInt(), ms.toInt())
        groove.jump(1f - slider.value)
    }
    slider.onValueChangeListener = SancaiSlider.OnValueChangeListener { _, _, _ -> show() }
    val play = SancaiButton(context).apply {
        text = str(R.string.motion_play)
        variant = SancaiButton.Variant.PRIMARY
        setOnClickListener {
            groove.jump(1f - slider.value)
            groove.play(1f, SancaiMotion.settleDuration(slider.value, theme.reduceMotion), SancaiMotion.glide())
        }
    }
    section(R.string.motion_settle_title, R.string.motion_settle_detail) {
        addView(text(str(R.string.motion_settle_left), SancaiTextRole.LABEL), inCard(topDp = 6, sideDp = 8))
        addView(slider, inCard(topDp = 4, sideDp = 2))
        addView(status, inCard(topDp = 2, sideDp = 8))
        addView(groove, inCard(topDp = 12, sideDp = 6))
        addView(play, inCard(topDp = 14, bottomDp = 7, sideDp = 22))
    }
    show()
    code("""
        // 210 + 210 x remaining, from 220 to 420 ms
        val reduce = SancaiTheme.of(this).reduceMotion
        val ms =
            SancaiMotion.settleDuration(0.5f, reduce)
        bead.animate()
            .translationX(travel)
            .setDuration(ms)
            .setInterpolator(SancaiMotion.glide())
    """)
}

private fun PageScope.inCard(topDp: Int = 0, bottomDp: Int = 0, sideDp: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply {
        topMargin = dp(topDp)
        bottomMargin = dp(bottomDp)
        marginStart = dp(sideDp)
        marginEnd = dp(sideDp)
    }

/** A groove with its name before it; the names share one width so the grooves line up. */
private fun PageScope.labelled(label: String, groove: View): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative(dp(8), 0, dp(6), 0)
        val name = text(label, SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
        addView(name, LinearLayout.LayoutParams((dp(LABEL_WIDTH_DP) * theme.textScale).roundToInt(), PageScope.WRAP))
        addView(groove, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
    }

/**
 * A recessed groove with one bead. [position] runs from 0 at the start to 1 at the end and is
 * mirrored in right-to-left layouts.
 */
private class GrooveView(context: Context, private val bed: Int, private val edge: Int) : View(context) {
    private val material = SancaiMaterial(context, SancaiTheme.of(context))
    private val track = RectF()
    private val bead = RectF()
    private var animator: ValueAnimator? = null
    private var position = 0f

    /** Moves the bead to [target] in [durationMs] along [curve]. */
    fun play(target: Float, durationMs: Long, curve: TimeInterpolator) {
        animator?.cancel()
        if (!isLaidOut) return jump(target)
        animator = ValueAnimator.ofFloat(position, target).apply {
            duration = durationMs
            interpolator = curve
            addUpdateListener {
                position = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Places the bead at [target] at once. */
    fun jump(target: Float) {
        animator?.cancel()
        position = target.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = material.dp(TRACK_DP + GUTTER_TOP_DP + GUTTER_BOTTOM_DP).roundToInt()
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        val side = material.dp(GUTTER_SIDE_DP)
        val top = material.dp(GUTTER_TOP_DP)
        track.set(side, top, width - side, top + material.dp(TRACK_DP))
        material.drawTrack(canvas, track, bed, edge)
        val inset = material.dp(BEAD_INSET_DP)
        val size = track.height() - inset * 2
        val travel = track.width() - inset * 2 - size
        val shown = if (layoutDirection == LAYOUT_DIRECTION_RTL) 1f - position else position
        val left = track.left + inset + travel * shown
        bead.set(left, track.top + inset, left + size, track.top + inset + size)
        material.drawThumb(canvas, bead)
    }

    override fun onDetachedFromWindow() {
        animator?.end()
        super.onDetachedFromWindow()
    }
}

private const val LABEL_WIDTH_DP = 52
private const val TRACK_DP = 24f
private const val BEAD_INSET_DP = 3f
private const val GUTTER_SIDE_DP = 1.5f
private const val GUTTER_TOP_DP = 1.5f
private const val GUTTER_BOTTOM_DP = 6.5f
private const val PLOT_HEIGHT_DP = 168f
private const val PLOT_PAD_DP = 14f
private const val PLOT_STEPS = 48

/**
 * Plots [SancaiMotion.glide] in a well: time runs to the right, progress runs up. The dashed
 * diagonal is linear motion. While a bead plays, a dot rides the curve with it.
 */
private class CurveView(context: Context, private val timeLabel: String, private val progressLabel: String) : View(context) {
    private val theme = SancaiTheme.of(context)
    private val material = SancaiMaterial(context, theme)
    private val curve = SancaiMotion.glide()
    private val well = RectF()
    private val path = Path()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val dashes = DashPathEffect(floatArrayOf(material.dp(4f), material.dp(4f)), 0f)
    private var animator: ValueAnimator? = null
    private var time = 1f

    init {
        SancaiTextRole.MICRO.applyTo(label, theme, resources.displayMetrics.scaledDensity)
        label.color = theme.colors.muted
        contentDescription = "$progressLabel / $timeLabel"
    }

    /** Runs the dot along the curve in [durationMs], at constant speed in time. */
    fun play(durationMs: Long) {
        animator?.cancel()
        if (!isLaidOut) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = null
            addUpdateListener {
                time = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (material.dp(PLOT_HEIGHT_DP) + label.textSize * 2).roundToInt()
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        val colors = theme.colors
        well.set(material.dp(1f), material.dp(1f), width - material.dp(1f), height - material.dp(1f))
        material.drawWell(canvas, well, material.dp(SancaiMaterial.RADIUS_WELL_DP))
        val pad = material.dp(PLOT_PAD_DP)
        val left = well.left + pad
        val right = well.right - pad
        val top = well.top + pad + label.textSize
        val bottom = well.bottom - pad - label.textSize
        val w = right - left
        val h = bottom - top

        line.pathEffect = null
        line.strokeWidth = material.dp(1f)
        line.color = colors.hairline
        for (i in 0..4) {
            val x = left + w * i / 4f
            val y = top + h * i / 4f
            canvas.drawLine(x, top, x, bottom, line)
            canvas.drawLine(left, y, right, y, line)
        }
        line.color = colors.muted
        line.pathEffect = dashes
        canvas.drawLine(left, bottom, right, top, line)

        path.rewind()
        path.moveTo(left, bottom)
        for (i in 1..PLOT_STEPS) {
            val t = i / PLOT_STEPS.toFloat()
            path.lineTo(left + w * t, bottom - h * curve.getInterpolation(t))
        }
        line.pathEffect = null
        line.strokeWidth = material.dp(2.5f)
        line.strokeCap = Paint.Cap.ROUND
        line.strokeJoin = Paint.Join.ROUND
        line.color = colors.accent
        canvas.drawPath(path, line)

        val x = left + w * time
        val y = bottom - h * curve.getInterpolation(time)
        fill.color = colors.surface
        canvas.drawCircle(x, y, material.dp(5.5f), fill)
        fill.color = colors.accent
        canvas.drawCircle(x, y, material.dp(3.5f), fill)

        val ascent = -label.ascent()
        label.textAlign = Paint.Align.LEFT
        canvas.drawText(progressLabel, left, well.top + pad * 0.5f + ascent, label)
        label.textAlign = Paint.Align.RIGHT
        canvas.drawText(timeLabel, right, bottom + pad * 0.4f + ascent, label)
    }

    override fun onDetachedFromWindow() {
        animator?.end()
        super.onDetachedFromWindow()
    }
}
