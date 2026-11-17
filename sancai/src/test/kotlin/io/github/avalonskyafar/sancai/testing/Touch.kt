package io.github.avalonskyafar.sancai.testing

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/** Synthetic touch input for view tests. Coordinates are in the view's own pixels. */
object Touch {

    fun dispatch(view: View, action: Int, x: Float, y: Float, downTime: Long = SystemClock.uptimeMillis()): Boolean {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            return view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** One finger of a multi-touch event: its pointer id and where it is. */
    class Finger(val id: Int, val x: Float, val y: Float)

    /**
     * Dispatches an event that carries several fingers. [action] is the plain action
     * (`ACTION_POINTER_DOWN`, `ACTION_POINTER_UP`, `ACTION_MOVE`, ...) and [changed] the index,
     * in [fingers], of the finger the action is about.
     *
     * A second finger landing, the first one lifting and the second one moving on is:
     * ```
     * Touch.dispatch(view, ACTION_DOWN, 10f, 20f)
     * Touch.multi(view, ACTION_POINTER_DOWN, 1, Finger(0, 10f, 20f), Finger(1, 200f, 20f))
     * Touch.multi(view, ACTION_POINTER_UP, 0, Finger(0, 10f, 20f), Finger(1, 200f, 20f))
     * Touch.multi(view, ACTION_MOVE, 0, Finger(1, 220f, 20f))
     * Touch.multi(view, ACTION_UP, 0, Finger(1, 220f, 20f))
     * ```
     */
    fun multi(view: View, action: Int, changed: Int, vararg fingers: Finger): Boolean {
        val properties = Array(fingers.size) { i ->
            MotionEvent.PointerProperties().apply {
                id = fingers[i].id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(fingers.size) { i ->
            MotionEvent.PointerCoords().apply {
                x = fingers[i].x
                y = fingers[i].y
                pressure = 1f
                size = 1f
            }
        }
        val now = SystemClock.uptimeMillis()
        val masked = action or (changed shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val event = MotionEvent.obtain(now, now, masked, fingers.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0)
        try {
            return view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** Down and up at the same point. */
    fun tap(view: View, x: Float, y: Float = view.height / 2f) {
        val downTime = SystemClock.uptimeMillis()
        dispatch(view, MotionEvent.ACTION_DOWN, x, y, downTime)
        dispatch(view, MotionEvent.ACTION_UP, x, y, downTime)
    }

    /**
     * Down at the first point, [steps] evenly spaced moves, and up at the last point unless
     * [release] is false.
     */
    fun drag(
        view: View,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        steps: Int = 8,
        release: Boolean = true,
    ) {
        val downTime = SystemClock.uptimeMillis()
        dispatch(view, MotionEvent.ACTION_DOWN, fromX, fromY, downTime)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            dispatch(view, MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * t, fromY + (toY - fromY) * t, downTime)
        }
        if (release) dispatch(view, MotionEvent.ACTION_UP, toX, toY, downTime)
    }
}
