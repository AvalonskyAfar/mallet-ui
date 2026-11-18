package io.github.avalonskyafar.mallet.internal

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.internal.HorizontalDragGate.State
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The gate follows the finger that went down first, whatever other fingers do. */
@RunWith(RobolectricTestRunner::class)
class HorizontalDragGateTest {

    private val view = View(ApplicationProvider.getApplicationContext())
    private val gate = HorizontalDragGate(slop = 10)

    /** An event with the given fingers as (id, x, y); [changed] is the index the action is about. */
    private fun event(action: Int, changed: Int, vararg fingers: Triple<Int, Float, Float>): MotionEvent {
        val properties = Array(fingers.size) { i ->
            MotionEvent.PointerProperties().apply {
                id = fingers[i].first
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(fingers.size) { i ->
            MotionEvent.PointerCoords().apply {
                x = fingers[i].second
                y = fingers[i].third
                pressure = 1f
                size = 1f
            }
        }
        val now = SystemClock.uptimeMillis()
        val masked = action or (changed shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        return MotionEvent.obtain(now, now, masked, fingers.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun finger(id: Int, x: Float, y: Float = 20f) = Triple(id, x, y)

    @Test
    fun `a sideways move claims the drag and a vertical one gives it up`() {
        gate.down(event(MotionEvent.ACTION_DOWN, 0, finger(0, 100f)))
        assertThat(gate.isActive).isTrue()
        assertThat(gate.move(view, event(MotionEvent.ACTION_MOVE, 0, finger(0, 105f)))).isEqualTo(State.UNDECIDED)
        assertThat(gate.move(view, event(MotionEvent.ACTION_MOVE, 0, finger(0, 130f)))).isEqualTo(State.DRAGGING)
        assertThat(gate.isDragging).isTrue()

        gate.down(event(MotionEvent.ACTION_DOWN, 0, finger(0, 100f)))
        assertThat(gate.move(view, event(MotionEvent.ACTION_MOVE, 0, finger(0, 104f, 60f)))).isEqualTo(State.ABORTED)
        assertThat(gate.isAborted).isTrue()
    }

    @Test
    fun `a second finger far away does not start a drag`() {
        gate.down(event(MotionEvent.ACTION_DOWN, 0, finger(0, 100f)))
        // Finger 1 lands 300 px to the side; finger 0 has not moved.
        val both = event(MotionEvent.ACTION_POINTER_DOWN, 1, finger(0, 100f), finger(1, 400f))
        assertThat(gate.isRelease(both)).isFalse()
        assertThat(gate.move(view, event(MotionEvent.ACTION_MOVE, 0, finger(0, 100f), finger(1, 420f))))
            .isEqualTo(State.UNDECIDED)
        assertThat(gate.x(both)).isEqualTo(100f)
    }

    @Test
    fun `the followed finger lifting ends the gesture although another stays down`() {
        gate.down(event(MotionEvent.ACTION_DOWN, 0, finger(0, 100f)))
        gate.move(view, event(MotionEvent.ACTION_MOVE, 0, finger(0, 140f), finger(1, 400f)))
        assertThat(gate.isDragging).isTrue()

        // The other finger lifting is not the release.
        assertThat(gate.isRelease(event(MotionEvent.ACTION_POINTER_UP, 1, finger(0, 140f), finger(1, 400f)))).isFalse()

        val lift = event(MotionEvent.ACTION_POINTER_UP, 0, finger(0, 150f), finger(1, 400f))
        assertThat(gate.isRelease(lift)).isTrue()
        assertThat(gate.x(lift)).isEqualTo(150f)
        gate.finish(view)
        assertThat(gate.isActive).isFalse()
        // What was decided stays readable after the finish.
        assertThat(gate.isDragging).isTrue()

        // The finger that is left moves and lifts: neither is part of the gesture.
        val rest = event(MotionEvent.ACTION_MOVE, 0, finger(1, 300f))
        assertThat(gate.move(view, rest)).isEqualTo(State.ABORTED)
        assertThat(gate.x(rest)).isEqualTo(150f)
        assertThat(gate.isRelease(event(MotionEvent.ACTION_UP, 0, finger(1, 300f)))).isFalse()
    }

    @Test
    fun `the last finger going up is a release`() {
        gate.down(event(MotionEvent.ACTION_DOWN, 0, finger(0, 100f)))
        val up = event(MotionEvent.ACTION_UP, 0, finger(0, 103f))
        assertThat(gate.isRelease(up)).isTrue()
        assertThat(gate.x(up)).isEqualTo(103f)
        assertThat(gate.y(up)).isEqualTo(20f)
        assertThat(gate.isRelease(event(MotionEvent.ACTION_CANCEL, 0, finger(0, 103f)))).isFalse()
    }
}
