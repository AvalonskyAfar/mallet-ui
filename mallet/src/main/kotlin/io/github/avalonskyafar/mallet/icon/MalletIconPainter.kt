package io.github.avalonskyafar.mallet.icon

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.ColorInt

/**
 * Draws [MalletIcon]s. All geometry lives on a 24 x 24 grid and is scaled to the requested
 * size; the stroke keeps the width you ask for whatever the scale.
 *
 * Not thread-safe: use on the UI thread.
 */
public object MalletIconPainter {
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
        icon: MalletIcon,
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

    private fun glyph(canvas: Canvas, icon: MalletIcon) {
        when (icon) {
            MalletIcon.CHEVRON_LEFT -> { polyline(15f, 18f, 9f, 12f, 15f, 6f); stroke(canvas) }
            MalletIcon.CHEVRON_RIGHT -> { polyline(9f, 18f, 15f, 12f, 9f, 6f); stroke(canvas) }
            MalletIcon.CHEVRON_UP -> { polyline(6f, 15f, 12f, 9f, 18f, 15f); stroke(canvas) }
            MalletIcon.CHEVRON_DOWN -> { polyline(6f, 9f, 12f, 15f, 18f, 9f); stroke(canvas) }
            MalletIcon.ARROW_RIGHT -> {
                polyline(5f, 12f, 19f, 12f)
                polyline(14f, 7f, 19f, 12f, 14f, 17f)
                stroke(canvas)
            }
            MalletIcon.ARROW_LEFT -> {
                polyline(19f, 12f, 5f, 12f)
                polyline(10f, 7f, 5f, 12f, 10f, 17f)
                stroke(canvas)
            }
            MalletIcon.ARROW_UP -> {
                polyline(12f, 19f, 12f, 5f)
                polyline(7f, 10f, 12f, 5f, 17f, 10f)
                stroke(canvas)
            }
            MalletIcon.ARROW_DOWN -> {
                polyline(12f, 5f, 12f, 19f)
                polyline(7f, 14f, 12f, 19f, 17f, 14f)
                stroke(canvas)
            }
            MalletIcon.CHECK -> { polyline(4.5f, 12.5f, 9.5f, 17.5f, 19.5f, 6.5f); stroke(canvas) }
            MalletIcon.CLOSE -> {
                polyline(6f, 6f, 18f, 18f)
                polyline(18f, 6f, 6f, 18f)
                stroke(canvas)
            }
            MalletIcon.PLUS -> {
                polyline(12f, 5f, 12f, 19f)
                polyline(5f, 12f, 19f, 12f)
                stroke(canvas)
            }
            MalletIcon.MINUS -> { polyline(5f, 12f, 19f, 12f); stroke(canvas) }
            MalletIcon.POWER -> {
                // An open ring with an 84 degree gap at the top and a stem through the gap.
                oval.set(3.5f, 4f, 20.5f, 21f)
                path.addArc(oval, -48f, 276f)
                polyline(12f, 3f, 12f, 13.5f)
                stroke(canvas)
            }
            // The three glyphs of the original navigation row. Their geometry is the original's,
            // which was drawn 24 dp wide, at three quarters: on this grid they are as large as
            // every other icon, and a 32 dp box gives them their first size back.
            MalletIcon.BOOK -> {
                path.moveTo(12f, 6.75f)
                path.cubicTo(9f, 4.5f, 3f, 4.5f, 3f, 4.5f)
                path.lineTo(3f, 17.25f)
                path.cubicTo(7.5f, 16.5f, 10.5f, 18.75f, 12f, 19.5f)
                path.close()
                path.moveTo(12f, 6.75f)
                path.cubicTo(15f, 4.5f, 21f, 4.5f, 21f, 4.5f)
                path.lineTo(21f, 17.25f)
                path.cubicTo(16.5f, 16.5f, 13.5f, 18.75f, 12f, 19.5f)
                path.close()
                stroke(canvas)
            }
            MalletIcon.SLIDERS -> {
                polyline(3f, 6f, 21f, 6f)
                polyline(3f, 12f, 21f, 12f)
                polyline(3f, 18f, 21f, 18f)
                stroke(canvas)
                dot(canvas, 8.25f, 6f, 1.8f)
                dot(canvas, 16.5f, 12f, 1.8f)
                dot(canvas, 11.25f, 18f, 1.8f)
            }
            MalletIcon.MORE_CIRCLE -> {
                ring()
                stroke(canvas)
                dot(canvas, 8.25f, 12f, 1f)
                dot(canvas, 12f, 12f, 1f)
                dot(canvas, 15.75f, 12f, 1f)
            }
            MalletIcon.SEARCH -> {
                path.addCircle(10.5f, 10.5f, 6.5f, Path.Direction.CW)
                polyline(15.3f, 15.3f, 20f, 20f)
                stroke(canvas)
            }
            MalletIcon.MORE_HORIZONTAL -> {
                dot(canvas, 5.5f, 12f, 1.6f)
                dot(canvas, 12f, 12f, 1.6f)
                dot(canvas, 18.5f, 12f, 1.6f)
            }
            MalletIcon.MORE_VERTICAL -> {
                dot(canvas, 12f, 5.5f, 1.6f)
                dot(canvas, 12f, 12f, 1.6f)
                dot(canvas, 12f, 18.5f, 1.6f)
            }
            MalletIcon.MENU -> {
                polyline(4f, 6.5f, 20f, 6.5f)
                polyline(4f, 12f, 20f, 12f)
                polyline(4f, 17.5f, 20f, 17.5f)
                stroke(canvas)
            }
            MalletIcon.INFO -> {
                ring()
                polyline(12f, 11.5f, 12f, 16.5f)
                stroke(canvas)
                dot(canvas, 12f, 7.8f, 1.2f)
            }
            MalletIcon.WARNING -> {
                // A triangle whose three corners are rounded off with quadratic curves.
                path.moveTo(10.7f, 5.6f)
                path.quadTo(12f, 3.4f, 13.3f, 5.6f)
                path.lineTo(20.3f, 17.6f)
                path.quadTo(21.4f, 19.8f, 19f, 19.8f)
                path.lineTo(5f, 19.8f)
                path.quadTo(2.6f, 19.8f, 3.7f, 17.6f)
                path.close()
                polyline(12f, 9.5f, 12f, 13.5f)
                stroke(canvas)
                dot(canvas, 12f, 16.6f, 1.2f)
            }
            MalletIcon.ERROR -> {
                ring()
                polyline(9f, 9f, 15f, 15f)
                polyline(15f, 9f, 9f, 15f)
                stroke(canvas)
            }
            MalletIcon.SUCCESS -> {
                ring()
                polyline(8f, 12.3f, 10.8f, 15f, 16f, 9.2f)
                stroke(canvas)
            }
            MalletIcon.TRASH -> {
                polyline(4f, 6.5f, 20f, 6.5f)
                polyline(9.5f, 6.5f, 9.5f, 3.5f, 14.5f, 3.5f, 14.5f, 6.5f)
                path.moveTo(6f, 6.5f)
                path.lineTo(6.8f, 18.5f)
                path.quadTo(6.9f, 20.5f, 9f, 20.5f)
                path.lineTo(15f, 20.5f)
                path.quadTo(17.1f, 20.5f, 17.2f, 18.5f)
                path.lineTo(18f, 6.5f)
                polyline(10f, 10.5f, 10f, 16.5f)
                polyline(14f, 10.5f, 14f, 16.5f)
                stroke(canvas)
            }
            MalletIcon.EDIT -> {
                // A pencil lying on the rising diagonal, tip at the bottom start corner.
                polyline(4f, 20f, 5f, 15.5f, 15.5f, 5f, 19f, 8.5f, 8.5f, 19f)
                path.close()
                polyline(13f, 7.5f, 16.5f, 11f)
                stroke(canvas)
            }
            MalletIcon.COPY -> {
                roundRect(3.5f, 8.5f, 14.5f, 20.5f, 2.5f)
                // The sheet behind: only the part that shows past the front sheet.
                path.moveTo(9.5f, 5.5f)
                path.quadTo(9.5f, 3.5f, 11.5f, 3.5f)
                path.lineTo(18.5f, 3.5f)
                path.quadTo(20.5f, 3.5f, 20.5f, 5.5f)
                path.lineTo(20.5f, 13.5f)
                path.quadTo(20.5f, 15.5f, 18.5f, 15.5f)
                path.lineTo(17.5f, 15.5f)
                stroke(canvas)
            }
            MalletIcon.SHARE -> {
                tray(5f, 13f, 19f)
                polyline(12f, 15f, 12f, 4f)
                polyline(8f, 8f, 12f, 4f, 16f, 8f)
                stroke(canvas)
            }
            MalletIcon.DOWNLOAD -> {
                tray(4.5f, 16.5f, 19.5f)
                polyline(12f, 4f, 12f, 15f)
                polyline(8f, 11f, 12f, 15f, 16f, 11f)
                stroke(canvas)
            }
            MalletIcon.REFRESH -> {
                // Most of a turn, clockwise, ending in a square arrow head.
                oval.set(4f, 4f, 20f, 20f)
                path.arcTo(oval, 20f, 295f, true)
                path.lineTo(18.6f, 7.3f)
                polyline(18.6f, 3.3f, 18.6f, 7.3f, 14.6f, 7.3f)
                stroke(canvas)
            }
            MalletIcon.LOCK -> {
                roundRect(5f, 10.5f, 19f, 20.5f, 2.5f)
                path.moveTo(8f, 10.5f)
                path.lineTo(8f, 7.5f)
                oval.set(8f, 3.5f, 16f, 11.5f)
                path.arcTo(oval, 180f, 180f, false)
                path.lineTo(16f, 10.5f)
                stroke(canvas)
                dot(canvas, 12f, 15.5f, 1.4f)
            }
            MalletIcon.BELL -> {
                path.moveTo(4f, 17.5f)
                path.quadTo(6f, 16f, 6f, 13.5f)
                path.lineTo(6f, 11f)
                path.cubicTo(6f, 7.2f, 8.7f, 5f, 12f, 5f)
                path.cubicTo(15.3f, 5f, 18f, 7.2f, 18f, 11f)
                path.lineTo(18f, 13.5f)
                path.quadTo(18f, 16f, 20f, 17.5f)
                path.close()
                polyline(12f, 3.2f, 12f, 5f)
                oval.set(10f, 16.5f, 14f, 20.5f)
                path.addArc(oval, 0f, 180f)
                stroke(canvas)
            }
            MalletIcon.STAR -> {
                // Five points on a radius of 9 around (12, 12.8), inner corners on 4.3.
                polyline(
                    12f, 3.8f, 14.53f, 9.32f, 20.56f, 10.02f, 16.09f, 14.13f, 17.29f, 20.08f,
                    12f, 17.1f, 6.71f, 20.08f, 7.91f, 14.13f, 3.44f, 10.02f, 9.47f, 9.32f,
                )
                path.close()
                stroke(canvas)
            }
            MalletIcon.HEART -> {
                path.moveTo(12f, 20f)
                path.cubicTo(5f, 15.5f, 3f, 12f, 3f, 8.8f)
                path.cubicTo(3f, 6f, 5f, 4.2f, 7.5f, 4.2f)
                path.cubicTo(9.5f, 4.2f, 11f, 5.2f, 12f, 7f)
                path.cubicTo(13f, 5.2f, 14.5f, 4.2f, 16.5f, 4.2f)
                path.cubicTo(19f, 4.2f, 21f, 6f, 21f, 8.8f)
                path.cubicTo(21f, 12f, 19f, 15.5f, 12f, 20f)
                path.close()
                stroke(canvas)
            }
            MalletIcon.HOME -> {
                polyline(3.5f, 11.5f, 12f, 4f, 20.5f, 11.5f)
                polyline(6f, 9.4f, 6f, 20f, 18f, 20f, 18f, 9.4f)
                polyline(10f, 20f, 10f, 14.5f, 14f, 14.5f, 14f, 20f)
                stroke(canvas)
            }
            MalletIcon.USER -> {
                path.addCircle(12f, 7.5f, 4f, Path.Direction.CW)
                path.moveTo(4f, 20.5f)
                path.cubicTo(4f, 16.5f, 7.5f, 14.5f, 12f, 14.5f)
                path.cubicTo(16.5f, 14.5f, 20f, 16.5f, 20f, 20.5f)
                stroke(canvas)
            }
            MalletIcon.SUN -> {
                path.addCircle(12f, 12f, 4f, Path.Direction.CW)
                // Eight short rays, every 45 degrees, from radius 7 to 9.
                polyline(12f, 3f, 12f, 5f); polyline(12f, 19f, 12f, 21f)
                polyline(3f, 12f, 5f, 12f); polyline(19f, 12f, 21f, 12f)
                polyline(5.64f, 5.64f, 7.05f, 7.05f); polyline(16.95f, 16.95f, 18.36f, 18.36f)
                polyline(18.36f, 5.64f, 16.95f, 7.05f); polyline(7.05f, 16.95f, 5.64f, 18.36f)
                stroke(canvas)
            }
            MalletIcon.EYE -> {
                eye()
                path.addCircle(12f, 12f, 3f, Path.Direction.CW)
                stroke(canvas)
            }
            MalletIcon.EYE_OFF -> {
                eye()
                path.addCircle(12f, 12f, 3f, Path.Direction.CW)
                polyline(5f, 4f, 19f, 20f)
                stroke(canvas)
            }
            MalletIcon.MOON -> {
                // A disc of radius 8 with a disc of radius 7 bitten out of its top end corner.
                oval.set(4f, 4f, 20f, 20f)
                path.arcTo(oval, 9.9f, 250.2f, true)
                oval.set(10f, 0f, 24f, 14f)
                path.arcTo(oval, 204.3f, -138.6f, false)
                path.close()
                stroke(canvas)
            }
            MalletIcon.LAYERS -> {
                polyline(12f, 3.5f, 20.5f, 8f, 12f, 12.5f, 3.5f, 8f)
                path.close()
                polyline(3.5f, 12f, 12f, 16.5f, 20.5f, 12f)
                polyline(3.5f, 16f, 12f, 20.5f, 20.5f, 16f)
                stroke(canvas)
            }
            MalletIcon.GRID -> {
                roundRect(3.5f, 3.5f, 10f, 10f, 1.5f)
                roundRect(14f, 3.5f, 20.5f, 10f, 1.5f)
                roundRect(3.5f, 14f, 10f, 20.5f, 1.5f)
                roundRect(14f, 14f, 20.5f, 20.5f, 1.5f)
                stroke(canvas)
            }
            MalletIcon.LIST -> {
                polyline(9f, 6.5f, 20.5f, 6.5f)
                polyline(9f, 12f, 20.5f, 12f)
                polyline(9f, 17.5f, 20.5f, 17.5f)
                stroke(canvas)
                dot(canvas, 4.5f, 6.5f, 1.3f)
                dot(canvas, 4.5f, 12f, 1.3f)
                dot(canvas, 4.5f, 17.5f, 1.3f)
            }
            MalletIcon.IMAGE -> {
                roundRect(3f, 4.5f, 21f, 19.5f, 2.5f)
                polyline(3f, 17f, 9f, 13f, 13.5f, 16.5f, 16f, 14.5f, 21f, 17f)
                stroke(canvas)
                dot(canvas, 8f, 9f, 1.5f)
            }
            MalletIcon.INBOX -> {
                path.moveTo(3f, 13f)
                path.lineTo(6.5f, 5f)
                path.lineTo(17.5f, 5f)
                path.lineTo(21f, 13f)
                path.lineTo(21f, 17.5f)
                path.quadTo(21f, 19.5f, 19f, 19.5f)
                path.lineTo(5f, 19.5f)
                path.quadTo(3f, 19.5f, 3f, 17.5f)
                path.close()
                // The lip of the tray with a dip in the middle.
                path.moveTo(3f, 13f)
                path.lineTo(8.5f, 13f)
                path.cubicTo(8.5f, 17.2f, 15.5f, 17.2f, 15.5f, 13f)
                path.lineTo(21f, 13f)
                stroke(canvas)
            }
            MalletIcon.CLOUD_OFF -> {
                path.moveTo(7f, 18.5f)
                path.cubicTo(4.5f, 18.5f, 3f, 16.8f, 3f, 14.8f)
                path.cubicTo(3f, 12.8f, 4.5f, 11.2f, 6.5f, 11f)
                path.cubicTo(6.8f, 8f, 9.2f, 6f, 12f, 6f)
                path.cubicTo(14.6f, 6f, 16.7f, 7.6f, 17.4f, 10f)
                path.cubicTo(19.6f, 10.2f, 21f, 12f, 21f, 14.2f)
                path.cubicTo(21f, 16.6f, 19.3f, 18.5f, 17f, 18.5f)
                path.close()
                polyline(5f, 3.5f, 19f, 20.5f)
                stroke(canvas)
            }
            MalletIcon.CLOCK -> {
                ring()
                polyline(12f, 7f, 12f, 12f, 15.5f, 14f)
                stroke(canvas)
            }
            MalletIcon.SHIELD -> {
                path.moveTo(12f, 3f)
                path.lineTo(19.5f, 5.8f)
                path.lineTo(19.5f, 11.5f)
                path.cubicTo(19.5f, 16f, 16.5f, 19.3f, 12f, 21f)
                path.cubicTo(7.5f, 19.3f, 4.5f, 16f, 4.5f, 11.5f)
                path.lineTo(4.5f, 5.8f)
                path.close()
                stroke(canvas)
            }
            MalletIcon.HAND -> {
                // An open palm: four fingers that share their walls, a round heel, a short thumb.
                path.moveTo(5.5f, 12.5f)
                finger(5.5f, 5f)
                path.lineTo(9f, 11f)
                finger(9f, 3.5f)
                path.lineTo(12.5f, 11f)
                finger(12.5f, 4.5f)
                path.lineTo(16f, 11f)
                finger(16f, 7f)
                path.lineTo(19.5f, 14.5f)
                path.cubicTo(19.5f, 18.3f, 16.5f, 20.5f, 12.8f, 20.5f)
                path.cubicTo(9.8f, 20.5f, 7.8f, 19.3f, 6f, 16.8f)
                path.lineTo(3.5f, 13.4f)
                path.quadTo(2.6f, 11.8f, 4f, 11.4f)
                path.quadTo(5f, 11.2f, 5.5f, 12.5f)
                stroke(canvas)
            }
            MalletIcon.PALETTE -> {
                // A disc with a thumb notch scooped out of its lower end, and four wells of paint.
                oval.set(3f, 3f, 21f, 21f)
                path.arcTo(oval, 100f, 290f, true)
                path.cubicTo(18.6f, 18.4f, 15.6f, 15.6f, 14.2f, 17.2f)
                path.cubicTo(13f, 18.6f, 13.6f, 20.9f, 10.44f, 20.86f)
                stroke(canvas)
                dot(canvas, 7.8f, 11f, 1.3f)
                dot(canvas, 11.2f, 7.4f, 1.3f)
                dot(canvas, 15.8f, 8.6f, 1.3f)
                dot(canvas, 8.4f, 15.4f, 1.3f)
            }
        }
    }

    /** Runs up the wall at [left], over a 3.5-wide fingertip whose top is [top], to the next wall. */
    private fun finger(left: Float, top: Float) {
        path.lineTo(left, top + 1.75f)
        oval.set(left, top, left + 3.5f, top + 3.5f)
        path.arcTo(oval, 180f, 180f, false)
    }

    /** The 18-wide circle shared by the status marks and the clock. */
    private fun ring() {
        path.addCircle(12f, 12f, 9f, Path.Direction.CW)
    }

    /** An open tray: two short walls from [top] down to a rounded floor at y = 20. */
    private fun tray(left: Float, top: Float, right: Float) {
        path.moveTo(left, top)
        path.lineTo(left, 18f)
        path.quadTo(left, 20f, left + 2f, 20f)
        path.lineTo(right - 2f, 20f)
        path.quadTo(right, 20f, right, 18f)
        path.lineTo(right, top)
    }

    /** The almond outline of an eye, 18 wide and 13 tall. */
    private fun eye() {
        path.moveTo(3f, 12f)
        path.cubicTo(6f, 7f, 9f, 5.5f, 12f, 5.5f)
        path.cubicTo(15f, 5.5f, 18f, 7f, 21f, 12f)
        path.cubicTo(18f, 17f, 15f, 18.5f, 12f, 18.5f)
        path.cubicTo(9f, 18.5f, 6f, 17f, 3f, 12f)
        path.close()
    }

    private fun roundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        oval.set(left, top, right, bottom)
        path.addRoundRect(oval, radius, radius, Path.Direction.CW)
    }

    private const val GRID = 24f
}
