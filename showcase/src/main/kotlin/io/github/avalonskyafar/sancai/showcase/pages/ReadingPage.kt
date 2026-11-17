package io.github.avalonskyafar.sancai.showcase.pages

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.widget.SancaiCallout
import io.github.avalonskyafar.sancai.widget.SancaiFlowRow
import io.github.avalonskyafar.sancai.widget.SancaiMediaFrame
import io.github.avalonskyafar.sancai.widget.SancaiParagraph
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack

/** Reading: paragraphs, callouts, a flow row and media frames. */
object ReadingPage : DemoPage("c_reading", R.string.page_c_reading_title, R.string.page_c_reading_summary) {
    override fun build(page: PageScope) {
        page.title()
        paragraphs(page)
        callouts(page)
        flow(page)
        media(page)
        page.caption(page.str(R.string.reading_origin), topDp = 22)
    }

    private fun paragraphs(page: PageScope) {
        val context = page.context
        val chinese = SancaiParagraph(context).apply { text = page.str(R.string.reading_para_cjk) }
        val status = page.text(page.str(R.string.reading_para_status_on), SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
        val track = SancaiSegmentedTrack(context).apply {
            setOptions(page.str(R.string.reading_para_option_on), page.str(R.string.reading_para_option_off))
            // The options are equals, not levels: one glaze under all of them.
            setGlazes(page.theme.glazes.uniform(2, page.theme.glazes.mist))
            setOnSelectionChangeListener { _, index ->
                chinese.protectClosingPunctuation = index == 0
                status.text = page.str(
                    if (index == 0) R.string.reading_para_status_on else R.string.reading_para_status_off,
                )
            }
        }
        page.section(R.string.reading_para_title, R.string.reading_para_detail) {
            addView(SancaiParagraph(context).apply { text = page.str(R.string.reading_para_intro) }, fill())
            addView(chinese, fill(page.dp(12)))
            addDivider(14f)
            addView(track, fill())
            addView(status, fill(page.dp(10)))
        }
        page.code(
            """
            val p = SancaiParagraph(context)
            p.text = "识别在本机完成，用完即弃。"
            // On by default: 。，、 never start a line
            p.protectClosingPunctuation = false
            // The text as set, without word joiners
            val plain = p.rawText
            """,
        )
    }

    private fun callouts(page: PageScope) {
        val context = page.context
        val notes = listOf(
            Triple(SancaiTextTone.ACCENT, R.string.reading_note_accent_title, R.string.reading_note_accent_body),
            Triple(SancaiTextTone.SUCCESS, R.string.reading_note_success_title, R.string.reading_note_success_body),
            Triple(SancaiTextTone.WARNING, R.string.reading_note_warning_title, R.string.reading_note_warning_body),
            Triple(SancaiTextTone.DANGER, R.string.reading_note_danger_title, R.string.reading_note_danger_body),
        )
        val views = notes.map { (tone, title, body) ->
            SancaiCallout(context).apply {
                this.title = page.str(title)
                this.body = page.str(body)
                this.tone = tone
            }
        }
        val track = SancaiSegmentedTrack(context).apply {
            setOptions(page.str(R.string.reading_note_option_tones), page.str(R.string.reading_note_option_quiet))
            setGlazes(page.theme.glazes.uniform(2, page.theme.glazes.mist))
            setOnSelectionChangeListener { _, index ->
                views.forEachIndexed { i, view ->
                    view.tone = if (index == 0) notes[i].first else SancaiTextTone.INK
                }
                page.host.say(
                    page.str(if (index == 0) R.string.reading_note_said_tones else R.string.reading_note_said_quiet),
                )
            }
        }
        page.section(R.string.reading_note_title, R.string.reading_note_detail) {
            views.forEachIndexed { i, view -> addView(view, fill(if (i == 0) 0 else page.dp(16))) }
            addDivider(14f)
            addView(track, fill())
        }
        page.code(
            """
            val note = SancaiCallout(context)
            note.title = "Check the unit"
            note.body = "Speeds are in km/h."
            note.tone = SancaiTextTone.WARNING
            // INK or MUTED: a quiet aside
            note.tone = SancaiTextTone.INK
            """,
        )
    }

    private fun flow(page: PageScope) {
        page.section(R.string.reading_flow_title, R.string.reading_flow_detail) {
            val row = SancaiFlowRow(context)
            row.setSteps(
                page.str(R.string.reading_flow_one) to page.str(R.string.reading_flow_one_detail),
                page.str(R.string.reading_flow_two) to page.str(R.string.reading_flow_two_detail),
                page.str(R.string.reading_flow_three) to page.str(R.string.reading_flow_three_detail),
            )
            addView(row, fill())
        }
        page.code(
            """
            val row = SancaiFlowRow(context)
            row.setSteps(
                "Capture" to "A still frame",
                "Read" to "On the device",
                "Answer" to "Shown in place",
            )
            """,
        )
    }

    private fun media(page: PageScope) {
        val context = page.context
        val ratios = floatArrayOf(16f / 9f, 2f, 3f)
        val picture = SancaiMediaFrame(context).apply {
            aspectRatio = ratios[0]
            setImageDrawable(HillsDrawable(page.theme.colors))
            contentDescription = page.str(R.string.reading_media_description)
        }
        val track = SancaiSegmentedTrack(context).apply {
            setOptions("16:9", "2:1", "3:1")
            setGlazes(page.theme.glazes.uniform(3, page.theme.glazes.mist))
            setOnSelectionChangeListener { _, index ->
                picture.aspectRatio = ratios[index]
            }
        }
        page.section(R.string.reading_media_title, R.string.reading_media_detail) {
            addView(picture, fill())
            addView(mutedLine(page, R.string.reading_media_picture_note), fill(page.dp(8)))
            addView(track, fill(page.dp(12)))
            addDivider(14f)
            addView(SancaiMediaFrame(context), fill())
            addView(mutedLine(page, R.string.reading_media_empty_note), fill(page.dp(8)))
        }
        page.code(
            """
            val frame = SancaiMediaFrame(context)
            frame.aspectRatio = 16f / 9f
            // Any Drawable; here a flat colour
            frame.setImageDrawable(ColorDrawable(Color.GRAY))
            frame.contentDescription = "Three hills"

            // No picture: the well and its icon
            frame.setImageDrawable(null)
            val empty = !frame.hasImage
            """,
        )
    }

    private fun mutedLine(page: PageScope, text: Int) =
        page.text(page.str(text), SancaiTextRole.CAPTION, SancaiTextTone.MUTED)

    private fun fill(top: Int = 0) = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply {
        topMargin = top
    }
}

/** Three glazed hills under a low sun, drawn from the theme colours at any size. */
private class HillsDrawable(private val colors: SancaiColors) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        paint.color = mix(colors.surface, colors.accent, 0.10f)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.color = mix(colors.surface, colors.warning, 0.70f)
        canvas.drawCircle(w * 0.72f, h * 0.36f, h * 0.13f, paint)
        hill(canvas, w, h, -0.10f, 0.62f, 0.50f, mix(colors.surface, colors.accent, 0.30f))
        hill(canvas, w, h, 0.38f, 1.12f, 0.58f, mix(colors.surface, colors.success, 0.45f))
        hill(canvas, w, h, 0.12f, 0.80f, 0.74f, mix(colors.surface, colors.accent, 0.62f))
        canvas.restore()
    }

    /** A rounded hill from [from] to [to] (fractions of the width) with its top at [top]. */
    private fun hill(canvas: Canvas, w: Float, h: Float, from: Float, to: Float, top: Float, color: Int) {
        val mid = (from + to) / 2f
        path.rewind()
        path.moveTo(w * from, h)
        path.cubicTo(w * (from + mid) / 2f, h, w * (from + mid) / 2f, h * top, w * mid, h * top)
        path.cubicTo(w * (mid + to) / 2f, h * top, w * (mid + to) / 2f, h, w * to, h)
        path.close()
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun mix(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).toInt() shl shift
        }
        return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
