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
