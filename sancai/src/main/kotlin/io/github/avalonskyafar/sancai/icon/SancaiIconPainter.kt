package io.github.avalonskyafar.sancai.icon

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.ColorInt

/**
 * Draws [SancaiIcon]s. All geometry lives on a 24 x 24 grid and is scaled to the requested
 * size; the stroke keeps the width you ask for whatever the scale.
 *
 * Not thread-safe: use on the UI thread.
 */
public object SancaiIconPainter {
    /** Default stroke width in dp for a 24 dp icon. */
    public const val STROKE_DP: Float = 2f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val oval = RectF()

    /**
     * Draws [icon] into the square whose top-left corner is ([left], [top]) and whose side is
     * [sizePx]. Set [mirrored] for directional icons in right-to-left layouts.
     */
    @JvmStatic
    @JvmOverloads
    public fun draw(
        canvas: Canvas,
        icon: SancaiIcon,
        left: Float,
        top: Float,
        sizePx: Float,
        @ColorInt color: Int,
        strokePx: Float,
        mirrored: Boolean = false,
    ) {
        if (sizePx <= 0f) return
        val scale = sizePx / GRID
        val save = canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        if (mirrored && icon.isDirectional) canvas.scale(-1f, 1f, GRID / 2f, GRID / 2f)
        paint.color = color
        paint.strokeWidth = strokePx / scale
        paint.style = Paint.Style.STROKE
        path.rewind()
        glyph(canvas, icon)
        canvas.restoreToCount(save)
    }

    private fun stroke(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        canvas.drawPath(path, paint)
        path.rewind()
    }

    private fun dot(canvas: Canvas, x: Float, y: Float, radius: Float) {
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, radius, paint)
        paint.style = Paint.Style.STROKE
    }

    private fun polyline(vararg points: Float) {
        path.moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) {
            path.lineTo(points[i], points[i + 1])
            i += 2
        }
    }

    private fun glyph(canvas: Canvas, icon: SancaiIcon) {
        when (icon) {
            SancaiIcon.CHEVRON_LEFT -> { polyline(15f, 18f, 9f, 12f, 15f, 6f); stroke(canvas) }
            SancaiIcon.CHEVRON_RIGHT -> { polyline(9f, 18f, 15f, 12f, 9f, 6f); stroke(canvas) }
            SancaiIcon.CHEVRON_UP -> { polyline(6f, 15f, 12f, 9f, 18f, 15f); stroke(canvas) }
            SancaiIcon.CHEVRON_DOWN -> { polyline(6f, 9f, 12f, 15f, 18f, 9f); stroke(canvas) }
            SancaiIcon.ARROW_RIGHT -> {
                polyline(5f, 12f, 19f, 12f)
                polyline(14f, 7f, 19f, 12f, 14f, 17f)
                stroke(canvas)
            }
            SancaiIcon.ARROW_LEFT -> {
                polyline(19f, 12f, 5f, 12f)
                polyline(10f, 7f, 5f, 12f, 10f, 17f)
                stroke(canvas)
            }
            SancaiIcon.ARROW_UP -> {
                polyline(12f, 19f, 12f, 5f)
                polyline(7f, 10f, 12f, 5f, 17f, 10f)
                stroke(canvas)
            }
            SancaiIcon.ARROW_DOWN -> {
                polyline(12f, 5f, 12f, 19f)
                polyline(7f, 14f, 12f, 19f, 17f, 14f)
                stroke(canvas)
            }
            SancaiIcon.CHECK -> { polyline(4.5f, 12.5f, 9.5f, 17.5f, 19.5f, 6.5f); stroke(canvas) }
            SancaiIcon.CLOSE -> {
                polyline(6f, 6f, 18f, 18f)
                polyline(18f, 6f, 6f, 18f)
                stroke(canvas)
            }
            SancaiIcon.PLUS -> {
                polyline(12f, 5f, 12f, 19f)
                polyline(5f, 12f, 19f, 12f)
                stroke(canvas)
            }
            SancaiIcon.MINUS -> { polyline(5f, 12f, 19f, 12f); stroke(canvas) }
            SancaiIcon.POWER -> {
                // An open ring with an 84 degree gap at the top and a stem through the gap.
                oval.set(3.5f, 4f, 20.5f, 21f)
                path.addArc(oval, -48f, 276f)
                polyline(12f, 3f, 12f, 13.5f)
                stroke(canvas)
            }
            SancaiIcon.BOOK -> {
                path.moveTo(12f, 6.2f)
                path.cubicTo(8.5f, 3.5f, 1.5f, 3.5f, 1.5f, 3.5f)
                path.lineTo(1.5f, 17.9f)
                path.cubicTo(6.75f, 17f, 10.25f, 19.6f, 12f, 20.5f)
                path.close()
                path.moveTo(12f, 6.2f)
                path.cubicTo(15.5f, 3.5f, 22.5f, 3.5f, 22.5f, 3.5f)
                path.lineTo(22.5f, 17.9f)
                path.cubicTo(17.25f, 17f, 13.75f, 19.6f, 12f, 20.5f)
                path.close()
                stroke(canvas)
            }
            SancaiIcon.SLIDERS -> {
                polyline(2f, 5f, 22f, 5f)
                polyline(2f, 12f, 22f, 12f)
                polyline(2f, 19f, 22f, 19f)
                stroke(canvas)
                dot(canvas, 7.6f, 5f, 2.2f)
                dot(canvas, 17.2f, 12f, 2.2f)
                dot(canvas, 11.1f, 19f, 2.2f)
            }
            SancaiIcon.MORE_CIRCLE -> {
                path.addCircle(12f, 12f, 10f, Path.Direction.CW)
                stroke(canvas)
                dot(canvas, 7.8f, 12f, 1.2f)
                dot(canvas, 12f, 12f, 1.2f)
                dot(canvas, 16.2f, 12f, 1.2f)
            }
            else -> {
                // Placeholder until the glyph is drawn: a small rounded square.
                oval.set(6f, 6f, 18f, 18f)
                path.addRoundRect(oval, 3f, 3f, Path.Direction.CW)
                stroke(canvas)
            }
        }
    }

    private const val GRID = 24f
}
