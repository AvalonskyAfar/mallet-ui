package io.github.avalonskyafar.sancai.internal

import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/**
 * Decides, for one gesture on a horizontal control, whether the finger is dragging the control
 * or scrolling the page.
 *
 * Until the finger leaves the touch slop nothing is decided. If it leaves mostly sideways the
 * control claims the gesture and tells its parents not to intercept; if it leaves mostly
 * up or down the control gives the gesture up for good.
 */
internal class HorizontalDragGate(private val slop: Int) {

    enum class State { UNDECIDED, DRAGGING, ABORTED }

    var downX = 0f
        private set
    var downY = 0f
        private set
    var state = State.UNDECIDED
        private set

    val isDragging: Boolean get() = state == State.DRAGGING
    val isAborted: Boolean get() = state == State.ABORTED

    fun down(event: MotionEvent) {
        downX = event.x
        downY = event.y
        state = State.UNDECIDED
    }

    /** Feeds a move event; returns the state after it. */
    fun move(view: View, event: MotionEvent): State {
        if (state != State.UNDECIDED) return state
        val dx = event.x - downX
        val dy = event.y - downY
        if (max(abs(dx), abs(dy)) < slop) return state
        if (abs(dy) >= abs(dx)) {
            state = State.ABORTED
            view.parent?.requestDisallowInterceptTouchEvent(false)
        } else {
            state = State.DRAGGING
            view.parent?.requestDisallowInterceptTouchEvent(true)
        }
        return state
    }

    /** Call on up and cancel. */
    fun finish(view: View) {
        view.parent?.requestDisallowInterceptTouchEvent(false)
    }
}
