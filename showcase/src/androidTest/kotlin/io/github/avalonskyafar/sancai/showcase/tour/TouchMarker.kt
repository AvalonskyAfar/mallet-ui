package io.github.avalonskyafar.sancai.showcase.tour

import android.app.Activity
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.Window

/**
 * Draws a dot where a window is touched, so that a clip shows the finger.
 *
 * Android's own "show taps" setting only shows touches that come from the touch screen. The
 * touches of a tour are injected and stay invisible. This marker belongs to the tour, not to
 * the app or the library: it wraps the callback of the window, sees every touch event the
 * window is given, and draws a dot at that point in the overlay of the window. It does not
 * change the event or how it is handled.
 *
 * Dialogs, sheets and menus live in windows of their own and are not marked.
 */
internal class TouchMarker private constructor(private val decor: View) : Drawable() {

    private val density = decor.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x52808890 }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xB8FFFFFF.toInt()
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.75f * density
        color = 0x30000000
    }
    private var x = 0f
    private var y = 0f
    private var shown = false
    private val hide = Runnable {
        shown = false
        decor.invalidate()
    }

    fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                decor.removeCallbacks(hide)
                x = event.x
                y = event.y
                shown = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // The dot lingers a moment, or a tap could not be seen at all.
                decor.removeCallbacks(hide)
                decor.postDelayed(hide, LINGER_MS)
            }
        }
        decor.invalidate()
    }

    override fun draw(canvas: Canvas) {
        if (!shown) return
        val radius = RADIUS_DP * density
        canvas.drawCircle(x, y, radius, fill)
        canvas.drawCircle(x, y, radius, ring)
        canvas.drawCircle(x, y, radius + ring.strokeWidth / 2f, edge)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    /** Hands every touch event on, unchanged, after showing the marker where it is. */
    private class Marking(private val base: Window.Callback, private val marker: TouchMarker) : Window.Callback by base {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            marker.onTouch(event)
            return base.dispatchTouchEvent(event)
        }
    }

    companion object {
        private const val RADIUS_DP = 15f
        private const val LINGER_MS = 170L

        /** Marks the touches of the window of [activity]. Does nothing the second time. Main thread only. */
        fun install(activity: Activity) {
            val window = activity.window
            if (window.callback is Marking) return
            val decor = window.decorView
            val marker = TouchMarker(decor)
            marker.setBounds(0, 0, 1 shl 16, 1 shl 16)
            decor.overlay.add(marker)
            window.callback = Marking(window.callback, marker)
        }
    }
}
