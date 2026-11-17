package io.github.avalonskyafar.sancai.widget

import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiStepperTest {

    private val changes = mutableListOf<Float>()
    private var edits = 0

    private fun stepper(
        value: Float = 50f,
        from: Float = 0f,
        to: Float = 100f,
        step: Float = 10f,
        theme: SancaiTheme = SancaiTheme.light(),
        layout: Boolean = true,
    ): SancaiStepper = SancaiStepper(Snapshots.context(theme)).apply {
        id = View.generateViewId()
        setRange(from, to)
        stepSize = step
        this.value = value
        setOnValueChangeListener { _, v -> changes += v }
        setOnEditRequestListener { edits++ }
        if (layout) Snapshots.layout(this, WIDTH_DP)
    }

    private val SancaiStepper.minusX: Float get() = width * 0.15f
    private val SancaiStepper.valueX: Float get() = width * 0.50f
    private val SancaiStepper.plusX: Float get() = width * 0.85f

    /** Vertical centre of the track, which sits above a 6.5 dp shadow gutter. */
    private val SancaiStepper.trackCentreY: Float
        get() = (1.5f * resources.displayMetrics.density + (height - 6.5f * resources.displayMetrics.density)) / 2f

    private fun key(view: View, code: Int): Boolean = view.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))

    private fun advance(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    @Test
    fun `default height is a 53dp track plus its shadow gutter`() {
        val view = stepper()
        assertThat(view.height / view.resources.displayMetrics.density).isWithin(0.5f).of(61f)
    }

    @Test
    fun `tapping plus and minus moves one step and tells the listener`() {
        val view = stepper()
        Touch.tap(view, view.plusX)
        assertThat(view.value).isEqualTo(60f)
        Touch.tap(view, view.minusX)
        Touch.tap(view, view.minusX)
        assertThat(view.value).isEqualTo(40f)
        assertThat(changes).containsExactly(60f, 50f, 40f).inOrder()
        assertThat(edits).isEqualTo(0)
    }

    @Test
    fun `setting the value from code snaps, clamps and stays silent`() {
        val view = stepper()
        view.value = 34f
        assertThat(view.value).isEqualTo(30f)
        view.value = 36f
        assertThat(view.value).isEqualTo(40f)
        view.value = 250f
        assertThat(view.value).isEqualTo(100f)
        view.value = -3f
        assertThat(view.value).isEqualTo(0f)
        assertThat(changes).isEmpty()
    }

    @Test
    fun `the grid starts at valueFrom and never passes valueTo`() {
        val view = stepper(value = 5f, from = 5f, to = 30f, step = 10f)
        view.value = 30f
        assertThat(view.value).isEqualTo(25f)
        Touch.tap(view, view.plusX)
        assertThat(view.value).isEqualTo(25f)
        assertThat(changes).isEmpty()
        view.setRange(5f, 12f)
        assertThat(view.value).isEqualTo(5f)
        view.stepSize = 0.1f
        view.value = 5.34f
        assertThat(view.value).isWithin(1e-4f).of(5.3f)
    }

    @Test
    fun `value is formatted with decimals, unit or a custom formatter`() {
        val view = stepper(value = 120f, to = 500f)
        assertThat(view.formattedValue.toString()).isEqualTo("120")
        view.unit = "ms"
        assertThat(view.formattedValue.toString()).isEqualTo("120ms")
        view.decimals = 2
        view.unit = null
        assertThat(view.formattedValue.toString()).isEqualTo("120.00")
        view.valueFormatter = { "x${it.toInt() / 10}" }
        assertThat(view.formattedValue.toString()).isEqualTo("x12")
        assertThat(ViewCompat.getStateDescription(view).toString()).isEqualTo("x12")
    }

    @Test
    fun `at a bound the zone does nothing and its mark pales`() {
        val view = stepper(value = 0f)
        Touch.tap(view, view.minusX)
        assertThat(view.value).isEqualTo(0f)
        assertThat(changes).isEmpty()

        val theme = SancaiTheme.light()
        val bed = theme.bedColor(theme.glazes.cobalt)
        val mark = theme.labelOnBed(theme.glazes.cobalt)
        val bitmap = Snapshots.render(view, WIDTH_DP)
        val y = view.trackCentreY.toInt()
        // Both marks have a horizontal bar through the centre of their zone.
        val trackWidth = view.width - 3f * view.resources.displayMetrics.density
        val sideCentre = trackWidth * 0.5f / 3.35f
        val left = 1.5f * view.resources.displayMetrics.density
        assertThat(bitmap.getPixel((left + trackWidth - sideCentre).toInt(), y)).isEqualTo(mark)
        val pale = bitmap.getPixel((left + sideCentre).toInt(), y)
        assertThat(pale).isNotEqualTo(mark)
        assertThat(pale).isNotEqualTo(bed)
        assertThat(colourDistance(pale, SancaiColors.blend(bed, mark, 0.4f))).isAtMost(6)
        // Between a mark and the thumb the bed shows its exact glaze.
        assertThat(bitmap.getPixel((view.width * 0.24f).toInt(), y)).isEqualTo(bed)
    }

    @Test
    fun `tapping the value requests editing and leaves the value alone`() {
        val view = stepper()
        Touch.tap(view, view.valueX)
        assertThat(edits).isEqualTo(1)
        assertThat(view.value).isEqualTo(50f)
        assertThat(changes).isEmpty()
    }

    @Test
    fun `the press follows the finger to another zone before release`() {
        val view = stepper()
        val y = view.height / 2f
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.minusX, y)
        Touch.dispatch(view, MotionEvent.ACTION_MOVE, view.valueX, y)
        Touch.dispatch(view, MotionEvent.ACTION_MOVE, view.plusX, y)
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.plusX, y)
        assertThat(view.value).isEqualTo(60f)
        assertThat(edits).isEqualTo(0)

        // Leaving the control altogether withdraws the press.
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.plusX, y)
        Touch.dispatch(view, MotionEvent.ACTION_MOVE, view.plusX, view.height * 4f)
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.plusX, view.height * 4f)
        assertThat(view.value).isEqualTo(60f)
        assertThat(changes).containsExactly(60f)
    }

    @Test
    fun `holding plus repeats, speeds up after ten steps and release adds nothing`() {
        val view = stepper(value = 0f, to = 1000f, step = 1f, layout = false)
        Snapshots.attach(view, WIDTH_DP)
        val y = view.height / 2f
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.plusX, y)
        advance(399)
        assertThat(view.value).isEqualTo(0f)
        advance(1)
        assertThat(view.value).isEqualTo(1f)
        advance(79)
        assertThat(view.value).isEqualTo(1f)
        advance(1)
        assertThat(view.value).isEqualTo(2f)
        advance(80 * 8)
        assertThat(view.value).isEqualTo(10f)
        // Ten repeats are done: from here on a step every 40 ms.
        advance(40)
        assertThat(view.value).isEqualTo(11f)
        advance(40 * 5)
        assertThat(view.value).isEqualTo(16f)
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.plusX, y)
        advance(1000)
        assertThat(view.value).isEqualTo(16f)
        assertThat(changes).hasSize(16)
    }

    @Test
    fun `a hold stops at the bound and a short press still counts as one tap`() {
        val view = stepper(value = 20f, layout = false)
        Snapshots.attach(view, WIDTH_DP)
        val y = view.height / 2f
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.minusX, y)
        advance(5000)
        assertThat(view.value).isEqualTo(0f)
        assertThat(changes).containsExactly(10f, 0f).inOrder()
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.minusX, y)

        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.plusX, y)
        advance(200)
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.plusX, y)
        advance(1000)
        assertThat(view.value).isEqualTo(10f)
    }

    @Test
    fun `arrow and sign keys step, Enter requests editing, a key at a bound is not consumed`() {
        val view = stepper(value = 90f)
        assertThat(key(view, KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(view.value).isEqualTo(100f)
        assertThat(key(view, KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(key(view, KeyEvent.KEYCODE_PLUS)).isFalse()
        assertThat(key(view, KeyEvent.KEYCODE_DPAD_LEFT)).isTrue()
        assertThat(key(view, KeyEvent.KEYCODE_MINUS)).isTrue()
        assertThat(view.value).isEqualTo(80f)
        assertThat(key(view, KeyEvent.KEYCODE_NUMPAD_ADD)).isTrue()
        assertThat(changes).containsExactly(100f, 90f, 80f, 90f).inOrder()
        assertThat(key(view, KeyEvent.KEYCODE_ENTER)).isTrue()
        assertThat(edits).isEqualTo(1)
    }

    @Test
    fun `minus stays on the left in right-to-left layouts`() {
        val view = stepper(layout = false)
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(view, WIDTH_DP)
        Touch.tap(view, view.minusX)
        assertThat(view.value).isEqualTo(40f)
        assertThat(key(view, KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(view.value).isEqualTo(50f)
    }

    @Test
    fun `saved state restores the value without calling the listener`() {
        val view = stepper(value = 70f)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)
        val restored = stepper(value = 10f)
        restored.id = view.id
        restored.restoreHierarchyState(saved)
        assertThat(restored.value).isEqualTo(70f)
        assertThat(changes).isEmpty()
    }

    @Test
    fun `a disabled stepper ignores touch and keys`() {
        val view = stepper()
        view.isEnabled = false
        assertThat(Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.plusX, view.height / 2f)).isFalse()
        assertThat(key(view, KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)).isFalse()
        assertThat(view.value).isEqualTo(50f)
    }

    @Test
    fun `xml attributes configure range, step, value and unit`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.sancaiValueFrom, "100")
            .addAttribute(R.attr.sancaiValueTo, "500")
            .addAttribute(R.attr.sancaiStepSize, "50")
            .addAttribute(R.attr.sancaiValue, "260")
            .addAttribute(R.attr.sancaiStepperUnit, "ms")
            .build()
        val view = SancaiStepper(Snapshots.context(), attrs)
        assertThat(view.valueFrom).isEqualTo(100f)
        assertThat(view.valueTo).isEqualTo(500f)
        assertThat(view.stepSize).isEqualTo(50f)
        assertThat(view.value).isEqualTo(250f)
        assertThat(view.formattedValue.toString()).isEqualTo("250ms")
    }

    @Test
    fun `accessibility exposes a seek bar with range, scroll actions and an edit action`() {
        val view = stepper(value = 90f)
        view.unit = "%"
        assertThat(view.accessibilityClassName.toString()).isEqualTo("android.widget.SeekBar")
        assertThat(ViewCompat.getStateDescription(view).toString()).isEqualTo("90%")

        val info = AccessibilityNodeInfo.obtain()
        view.onInitializeAccessibilityNodeInfo(info)
        val range = info.rangeInfo
        assertThat(range).isNotNull()
        assertThat(range!!.min).isEqualTo(0f)
        assertThat(range.max).isEqualTo(100f)
        assertThat(range.current).isEqualTo(90f)
        val edit = info.actionList.firstOrNull { it.id == R.id.sancai_stepper_action_edit }
        assertThat(edit).isNotNull()
        assertThat(edit!!.label.toString()).isEqualTo("Edit")

        assertThat(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)).isTrue()
        assertThat(view.value).isEqualTo(100f)
        assertThat(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)).isFalse()
        assertThat(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null)).isTrue()
        assertThat(changes).containsExactly(100f, 90f).inOrder()

        val atTop = AccessibilityNodeInfo.obtain()
        view.value = 100f
        view.onInitializeAccessibilityNodeInfo(atTop)
        val ids = atTop.actionList.map { it.id }
        assertThat(ids).contains(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        assertThat(ids).doesNotContain(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

        val arguments = Bundle().apply {
            putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 33f)
        }
        assertThat(view.performAccessibilityAction(android.R.id.accessibilityActionSetProgress, arguments)).isTrue()
        assertThat(view.value).isEqualTo(30f)
        assertThat(view.performAccessibilityAction(R.id.sancai_stepper_action_edit, null)).isTrue()
        assertThat(edits).isEqualTo(1)
    }

    @Test
    fun `an empty range pales both marks and nothing steps`() {
        val view = stepper(value = 5f, from = 5f, to = 5f, step = 1f)
        Touch.tap(view, view.plusX)
        Touch.tap(view, view.minusX)
        assertThat(key(view, KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(view.value).isEqualTo(5f)
        assertThat(changes).isEmpty()
        val info = AccessibilityNodeInfo.obtain()
        view.onInitializeAccessibilityNodeInfo(info)
        val ids = info.actionList.map { it.id }
        assertThat(ids).doesNotContain(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        assertThat(ids).doesNotContain(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    @Test
    fun `a bad step or range is refused from code`() {
        val view = stepper()
        assertThat(runCatching { view.stepSize = 0f }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { view.setRange(10f, 5f) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        view.value = Float.NaN
        assertThat(view.value).isEqualTo(50f)
        assertThat(view.stepSize).isEqualTo(10f)
    }

    @Test
    fun `the listener may roll the value back and may disable the control during a hold`() {
        val view = stepper(value = 0f, to = 1000f, step = 1f, layout = false)
        view.setOnValueChangeListener { stepper, v ->
            changes += v
            if (v == 1f && changes.size == 1) stepper.value = 0f
            if (v == 3f) stepper.isEnabled = false
        }
        Snapshots.attach(view, WIDTH_DP)
        Touch.tap(view, view.plusX)
        assertThat(view.value).isEqualTo(0f)
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.plusX, view.height / 2f)
        advance(5000)
        assertThat(view.value).isEqualTo(3f)
        assertThat(changes).containsExactly(1f, 1f, 2f, 3f).inOrder()
    }

    @Test
    fun `detaching ends a hold`() {
        val view = stepper(value = 0f, to = 1000f, step = 1f, layout = false)
        Snapshots.attach(view, WIDTH_DP)
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.plusX, view.height / 2f)
        advance(480)
        assertThat(view.value).isEqualTo(2f)
        (view.parent as android.view.ViewGroup).removeView(view)
        advance(2000)
        assertThat(view.value).isEqualTo(2f)
    }

    @Test
    fun `a cancelled touch does not step or edit`() {
        val view = stepper()
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.plusX, view.height / 2f)
        Touch.dispatch(view, MotionEvent.ACTION_CANCEL, view.plusX, view.height / 2f)
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.valueX, view.height / 2f)
        Touch.dispatch(view, MotionEvent.ACTION_CANCEL, view.valueX, view.height / 2f)
        assertThat(changes).isEmpty()
        assertThat(edits).isEqualTo(0)
    }

    @Test
    fun `a long value shrinks to stay on the thumb, also with large text`() {
        val theme = SancaiTheme.light().copy(textScale = 1.13f)
        val bed = theme.bedColor(theme.glazes.cobalt)
        val view = stepper(theme = theme)
        view.valueFormatter = { "1 234 567.89 milliseconds per frame" }
        assertThat(view.height / view.resources.displayMetrics.density).isWithin(0.5f).of(61f)
        val bitmap = Snapshots.render(view, WIDTH_DP)
        val y = view.trackCentreY.toInt()
        // The bed between each mark and the thumb stays clean: the text did not spill.
        assertThat(bitmap.getPixel((view.width * 0.24f).toInt(), y)).isEqualTo(bed)
        assertThat(bitmap.getPixel((view.width * 0.76f).toInt(), y)).isEqualTo(bed)
        Touch.tap(view, view.plusX)
        assertThat(view.value).isEqualTo(60f)
    }

    @Test
    fun `a pressed zone sinks the marks by one dp`() {
        val theme = SancaiTheme.light()
        val mark = theme.labelOnBed(theme.glazes.cobalt)
        val view = stepper()
        val density = view.resources.displayMetrics.density
        val x = (1.5f * density + (view.width - 3f * density) * 0.5f / 3.35f).toInt()
        val y = view.trackCentreY.toInt()
        val resting = Snapshots.render(view, WIDTH_DP)
        assertThat(resting.getPixel(x, y - 2)).isEqualTo(mark)
        assertThat(resting.getPixel(x, y + 4)).isNotEqualTo(mark)
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.minusX, view.height / 2f)
        val pressed = Snapshots.render(view, WIDTH_DP)
        assertThat(pressed.getPixel(x, y + 4)).isEqualTo(mark)
        assertThat(pressed.getPixel(x, y - 2)).isNotEqualTo(mark)
        Touch.dispatch(view, MotionEvent.ACTION_CANCEL, view.minusX, view.height / 2f)
        assertThat(Snapshots.render(view, WIDTH_DP).getPixel(x, y - 2)).isEqualTo(mark)
    }

    @Test
    fun `activating through accessibility requests editing only when someone listens`() {
        val view = stepper()
        assertThat(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)).isTrue()
        assertThat(edits).isEqualTo(1)
        view.setOnEditRequestListener(null)
        assertThat(view.performAccessibilityAction(R.id.sancai_stepper_action_edit, null)).isFalse()
        Touch.tap(view, view.valueX)
        assertThat(edits).isEqualTo(1)
        val info = AccessibilityNodeInfo.obtain()
        view.onInitializeAccessibilityNodeInfo(info)
        assertThat(info.actionList.map { it.id }).doesNotContain(R.id.sancai_stepper_action_edit)
    }

    @Test
    fun `a value that rounds to zero is written without a minus sign`() {
        val view = stepper(value = -0.04f, from = -1f, to = 1f, step = 0.04f)
        view.decimals = 1
        assertThat(view.formattedValue.toString()).isEqualTo("0.0")
        view.value = -0.48f
        assertThat(view.formattedValue.toString()).isEqualTo("-0.5")
    }

    @Test
    fun `a negative value keeps its sign in a locale with its own digits`() {
        val before = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("fa-IR"))
        try {
            val view = stepper(value = -5f, from = -50f, to = 50f, step = 1f)
            assertThat(view.formattedValue.toString()).startsWith("-")
            view.stepSize = 0.25f
            view.value = -0.25f
            assertThat(view.formattedValue.toString()).doesNotContain("-")
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    @Test
    fun `a hold withdrawn by a lost window focus or by disabling stays over for the rest of the gesture`() {
        val view = stepper(value = 0f, to = 1000f, step = 1f, layout = false)
        Snapshots.attach(view, WIDTH_DP)
        val y = view.height / 2f
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.plusX, y)
        advance(480)
        assertThat(view.value).isEqualTo(2f)
        view.onWindowFocusChanged(false)
        Touch.dispatch(view, MotionEvent.ACTION_MOVE, view.plusX + 1f, y)
        advance(2000)
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.plusX + 1f, y)
        assertThat(view.value).isEqualTo(2f)

        // A plain press, disabled and enabled again under the finger: the release is not a tap.
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.plusX, y)
        view.isEnabled = false
        view.isEnabled = true
        Touch.dispatch(view, MotionEvent.ACTION_MOVE, view.plusX + 1f, y)
        advance(2000)
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.plusX + 1f, y)
        assertThat(view.value).isEqualTo(2f)

        // The next gesture works again.
        Touch.tap(view, view.plusX)
        assertThat(view.value).isEqualTo(3f)
    }

    @Test
    fun `after a hold, lifting over another zone neither edits nor steps back`() {
        val view = stepper(value = 0f, to = 1000f, step = 1f, layout = false)
        Snapshots.attach(view, WIDTH_DP)
        val y = view.height / 2f
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.plusX, y)
        advance(480)
        Touch.dispatch(view, MotionEvent.ACTION_MOVE, view.valueX, y)
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.valueX, y)
        assertThat(edits).isEqualTo(0)
        assertThat(view.value).isEqualTo(2f)

        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.plusX, y)
        advance(400)
        Touch.dispatch(view, MotionEvent.ACTION_MOVE, view.minusX, y)
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.minusX, y)
        assertThat(view.value).isEqualTo(3f)
    }

    @Test
    fun `a second finger changes nothing and the first finger's lift ends the gesture`() {
        val view = stepper(value = 0f, to = 1000f, step = 1f, layout = false)
        Snapshots.attach(view, WIDTH_DP)
        val y = view.height / 2f
        val a = Touch.Finger(0, view.plusX, y)
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.plusX, y)
        Touch.multi(view, MotionEvent.ACTION_POINTER_DOWN, 1, a, Touch.Finger(1, view.minusX, y))
        Touch.multi(view, MotionEvent.ACTION_MOVE, 1, a, Touch.Finger(1, view.minusX + 2f, y))
        Touch.multi(view, MotionEvent.ACTION_POINTER_UP, 0, a, Touch.Finger(1, view.minusX + 2f, y))
        assertThat(view.value).isEqualTo(1f)
        Touch.multi(view, MotionEvent.ACTION_MOVE, 0, Touch.Finger(1, view.minusX, y))
        advance(2000)
        Touch.multi(view, MotionEvent.ACTION_UP, 0, Touch.Finger(1, view.valueX, y))
        assertThat(view.value).isEqualTo(1f)
        assertThat(changes).containsExactly(1f)
        assertThat(edits).isEqualTo(0)
    }

    @Test
    fun `the accessibility node offers a click only while someone listens for it`() {
        // The platform fills in the click only for a view that is in a window.
        val view = stepper(layout = false)
        Snapshots.attach(view, WIDTH_DP)
        fun node(): AccessibilityNodeInfo = AccessibilityNodeInfo.obtain().also { view.onInitializeAccessibilityNodeInfo(it) }
        assertThat(node().actionList.map { it.id }).contains(AccessibilityNodeInfo.ACTION_CLICK)
        assertThat(node().isClickable).isTrue()
        view.setOnEditRequestListener(null)
        assertThat(node().isClickable).isFalse()
        assertThat(node().actionList.map { it.id }).doesNotContain(AccessibilityNodeInfo.ACTION_CLICK)
        view.setOnClickListener { }
        assertThat(node().isClickable).isTrue()
    }

    private fun gallery(theme: SancaiTheme): View {
        val context = Snapshots.context(theme)
        val density = context.resources.displayMetrics.density
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        fun add(configure: SancaiStepper.() -> Unit): SancaiStepper {
            val view = SancaiStepper(context).apply(configure)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = (10 * density).toInt()
            column.addView(view, params)
            return view
        }
        add { setRange(0f, 500f); stepSize = 10f; value = 120f; unit = "ms" }
        add { value = 0f }
        add { value = 100f; unit = "%" }
        add { glaze = theme.glazes.celadon; setRange(0f, 2f); stepSize = 0.05f; decimals = 2; value = 1.15f }
        val pressedPlus = add { glaze = theme.glazes.ochre; value = 7f }
        val pressedValue = add { glaze = theme.glazes.terracotta; setRange(-50f, 50f); value = -12f }
        add { value = 64f; isEnabled = false }
        Snapshots.layout(column, 360)
        Touch.dispatch(pressedPlus, MotionEvent.ACTION_DOWN, pressedPlus.width * 0.85f, pressedPlus.height / 2f)
        Touch.dispatch(pressedValue, MotionEvent.ACTION_DOWN, pressedValue.width * 0.5f, pressedValue.height / 2f)
        return column
    }

    @Test
    fun `snapshot of steppers in light and dark`() {
        Snapshots.capture(gallery(SancaiTheme.light()), "stepper_light", 360)
        Snapshots.capture(gallery(SancaiTheme.dark()), "stepper_dark", 360)
    }

    private fun colourDistance(a: Int, b: Int): Int {
        var worst = 0
        for (shift in intArrayOf(0, 8, 16)) {
            worst = maxOf(worst, kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)))
        }
        return worst
    }

    private companion object {
        const val WIDTH_DP = 300
    }
}
