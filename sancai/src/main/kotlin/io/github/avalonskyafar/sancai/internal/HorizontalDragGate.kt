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
 *
 * The gate follows **one finger**, the one that went down first, by its pointer id. A second
 * finger on the same control changes nothing, and the gesture ends when the followed finger
 * lifts, whether or not another one is still down. A control therefore reads the position
 * with [x] and [y] (never `event.x`), treats [isRelease] as the release, and calls [finish]
 * once it has read what it needs:
 *
 * ```kotlin
 * when {
 *     action == MotionEvent.ACTION_DOWN -> gate.down(event)
 *     action == MotionEvent.ACTION_MOVE -> when (gate.move(this, event)) {
 *         State.DRAGGING -> follow(gate.x(event))
 *         State.ABORTED -> letGo()
 *         State.UNDECIDED -> Unit
 *     }
 *     gate.isRelease(event) -> { val x = gate.x(event); gate.finish(this); commit(x) }
 *     action == MotionEvent.ACTION_CANCEL -> { gate.finish(this); restore() }
 * }
 * ```
 *
 * After [finish], events of fingers that are still down are answered with
 * [State.ABORTED] by [move] and with `false` by [isRelease], so they do nothing; a control
 * that only looks at [isAborted] when the last finger lifts is safe as well.
 */
internal class HorizontalDragGate(private val slop: Int) {

    enum class State { UNDECIDED, DRAGGING, ABORTED }

    var downX = 0f
        private set
    var downY = 0f
        private set
    var state = State.UNDECIDED
        private set
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var lastX = 0f
    private var lastY = 0f

    val isDragging: Boolean get() = state == State.DRAGGING
    val isAborted: Boolean get() = state == State.ABORTED

    /** Whether a finger is being followed: from [down] until [finish]. */
    val isActive: Boolean get() = pointerId != MotionEvent.INVALID_POINTER_ID

    /** Starts a gesture with the finger that went down first. */
    fun down(event: MotionEvent) {
        pointerId = event.getPointerId(0)
        downX = event.x
        downY = event.y
        lastX = downX
        lastY = downY
        state = State.UNDECIDED
    }

    /** x of the followed finger; where it was last seen when it is not part of [event]. */
    fun x(event: MotionEvent): Float {
        val index = event.findPointerIndex(pointerId)
        if (index >= 0) lastX = event.getX(index)
        return lastX
    }

    /** y of the followed finger; where it was last seen when it is not part of [event]. */
    fun y(event: MotionEvent): Float {
        val index = event.findPointerIndex(pointerId)
        if (index >= 0) lastY = event.getY(index)
        return lastY
    }

    /**
     * Whether [event] lifts the followed finger: the last finger going up, or the followed one
     * going up while another stays down. Both end the gesture.
     */
    fun isRelease(event: MotionEvent): Boolean {
        if (!isActive) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_UP -> true
            MotionEvent.ACTION_POINTER_UP -> event.getPointerId(event.actionIndex) == pointerId
            else -> false
        }
    }

    /** Feeds a move event; returns the state after it. */
    fun move(view: View, event: MotionEvent): State {
        val index = event.findPointerIndex(pointerId)
        // The followed finger is gone: whatever still moves, or lifts later, is not this gesture.
        if (index < 0) {
            state = State.ABORTED
            return state
        }
        lastX = event.getX(index)
        lastY = event.getY(index)
        if (state != State.UNDECIDED) return state
        val dx = lastX - downX
        val dy = lastY - downY
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

    /**
     * Ends the gesture: call on release and on cancel, after reading [x], [isDragging] and
     * [isAborted] (which keep their values).
     */
    fun finish(view: View) {
        pointerId = MotionEvent.INVALID_POINTER_ID
        view.parent?.requestDisallowInterceptTouchEvent(false)
    }
}
