package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiSliderTest {

    private fun slider(
        theme: SancaiTheme = SancaiTheme.light(),
        from: Float = 0f,
        to: Float = 100f,
        step: Float = 0f,
        value: Float = 0f,
        rtl: Boolean = false,
    ): SancaiSlider {
        if (rtl) {
            val app = ApplicationProvider.getApplicationContext<Context>()
            app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        }
        return SancaiSlider(Snapshots.context(theme)).apply {
            id = View.generateViewId()
            if (rtl) layoutDirection = View.LAYOUT_DIRECTION_RTL
            valueFrom = from
            valueTo = to
            stepSize = step
            this.value = value
            Snapshots.layout(this, WIDTH_DP)
        }
    }

    /** Horizontal position of the bead centre at [share] of the way from left to right. */
    private fun SancaiSlider.xAt(share: Float): Float {
        val inset = 21f * resources.displayMetrics.density
        return inset + (width - inset * 2f) * share
    }

    /** Pixel on the centre line of the track at [share] of the bead's travel. */
    private fun SancaiSlider.trackPixel(share: Float): Int =
        Snapshots.render(this, WIDTH_DP).getPixel(xAt(share).toInt(), height / 2)

    private fun assertColour(actual: Int, expected: Int, tolerance: Int = COLOUR_TOLERANCE) {
        val distance = maxOf(
            abs(Color.red(actual) - Color.red(expected)),
            abs(Color.green(actual) - Color.green(expected)),
            abs(Color.blue(actual) - Color.blue(expected)),
        )
        assertWithMessage("expected %s but was %s", Integer.toHexString(expected), Integer.toHexString(actual))
            .that(distance).isAtMost(tolerance)
    }

    private fun SancaiSlider.press(code: Int): Boolean = onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))

    @Test
    fun `default height is 44dp`() {
        val view = slider()
        assertThat(view.height / view.resources.displayMetrics.density).isWithin(0.5f).of(44f)
    }

    @Test
    fun `tap moves the bead there and reports a user change`() {
        val view = slider()
        val seen = mutableListOf<Pair<Float, Boolean>>()
        var stops = 0
        view.onValueChangeListener = SancaiSlider.OnValueChangeListener { _, value, fromUser -> seen += value to fromUser }
        view.onStopTrackingTouchListener = SancaiSlider.OnStopTrackingTouchListener { stops++ }
        Touch.tap(view, view.xAt(0.25f))
        assertThat(view.value).isWithin(0.5f).of(25f)
        assertThat(seen).hasSize(1)
        assertThat(seen[0].second).isTrue()
        assertThat(stops).isEqualTo(1)
    }

    @Test
    fun `taps beyond the ends clamp to the range`() {
        val view = slider(from = 10f, to = 20f, value = 15f)
        Touch.tap(view, 1f)
        assertThat(view.value).isEqualTo(10f)
        Touch.tap(view, view.width - 1f)
        assertThat(view.value).isEqualTo(20f)
    }

    @Test
    fun `drag follows the finger and ends tracking once`() {
        val view = slider()
        val seen = mutableListOf<Float>()
        var stops = 0
        view.onValueChangeListener = SancaiSlider.OnValueChangeListener { _, value, _ -> seen += value }
        view.onStopTrackingTouchListener = SancaiSlider.OnStopTrackingTouchListener { stops++ }
        val y = view.height / 2f
        Touch.drag(view, view.xAt(0.1f), y, view.xAt(0.8f), y, release = false)
        assertThat(view.value).isWithin(0.5f).of(80f)
        assertThat(seen.size).isGreaterThan(2)
        assertThat(seen).isInOrder()
        assertThat(stops).isEqualTo(0)
        Touch.dispatch(view, android.view.MotionEvent.ACTION_UP, view.xAt(0.8f), y)
        assertThat(view.value).isWithin(0.5f).of(80f)
        assertThat(stops).isEqualTo(1)
    }

    @Test
    fun `with a step size values snap to the grid`() {
        val view = slider(step = 25f)
        Touch.tap(view, view.xAt(0.58f))
        assertThat(view.value).isEqualTo(50f)
        val seen = mutableListOf<Float>()
        view.onValueChangeListener = SancaiSlider.OnValueChangeListener { _, value, _ -> seen += value }
        val y = view.height / 2f
        Touch.drag(view, view.xAt(0.5f), y, view.xAt(1f), y, steps = 20)
        assertThat(seen).containsExactly(75f, 100f).inOrder()
        view.value = 37f
        assertThat(view.value).isEqualTo(25f)
        view.value = 500f
        assertThat(view.value).isEqualTo(100f)
    }

    @Test
    fun `decimal steps stay on clean values`() {
        val view = slider(from = 0f, to = 1f, step = 0.1f)
        view.value = 0.31f
        assertThat(view.value).isEqualTo(0.3f)
        assertThat(androidx.core.view.ViewCompat.getStateDescription(view).toString()).isEqualTo("0.3")
    }

    @Test
    fun `a mostly vertical drag is left to the scrolling parent`() {
        val view = slider(value = 40f)
        var calls = 0
        view.onValueChangeListener = SancaiSlider.OnValueChangeListener { _, _, _ -> calls++ }
        view.onStopTrackingTouchListener = SancaiSlider.OnStopTrackingTouchListener { calls++ }
        Touch.drag(view, view.xAt(0.8f), 10f, view.xAt(0.85f), 400f)
        assertThat(view.value).isEqualTo(40f)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `right to left layouts mirror the track and the arrow keys`() {
        val view = slider(rtl = true, step = 10f)
        Touch.tap(view, view.xAt(0.2f))
        assertThat(view.value).isEqualTo(80f)
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_LEFT)).isTrue()
        assertThat(view.value).isEqualTo(90f)
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(view.value).isEqualTo(80f)
        // The glazed stretch now lies to the right of the bead.
        view.value = 50f
        assertColour(view.trackPixel(0.8f), Color.rgb(134, 172, 247))
        assertColour(view.trackPixel(0.2f), Color.rgb(226, 237, 247))
    }

    @Test
    fun `arrow keys move one step or five percent and fall through at the ends`() {
        val stepped = slider(step = 50f)
        var calls = 0
        stepped.onValueChangeListener = SancaiSlider.OnValueChangeListener { _, _, fromUser -> if (fromUser) calls++ }
        assertThat(stepped.press(KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(stepped.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(stepped.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(stepped.value).isEqualTo(100f)
        assertThat(stepped.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(stepped.press(KeyEvent.KEYCODE_TAB)).isFalse()
        assertThat(calls).isEqualTo(2)

        val continuous = slider(from = 0f, to = 200f)
        assertThat(continuous.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(continuous.value).isWithin(0.01f).of(10f)
    }

    @Test
    fun `setting the value from code does not call the listener`() {
        val view = slider()
        var calls = 0
        view.onValueChangeListener = SancaiSlider.OnValueChangeListener { _, _, _ -> calls++ }
        view.value = 30f
        view.setValue(60f, animate = true)
        view.valueTo = 50f
        assertThat(view.value).isEqualTo(50f)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `disabled slider ignores input`() {
        val view = slider(value = 20f)
        view.isEnabled = false
        Touch.tap(view, view.xAt(0.9f))
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(view.value).isEqualTo(20f)
    }

    @Test
    fun `value survives save and restore without calling the listener`() {
        val view = slider(step = 5f, value = 65f)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)
        var calls = 0
        val restored = SancaiSlider(Snapshots.context()).apply {
            id = view.id
            valueTo = 100f
            stepSize = 5f
            onValueChangeListener = SancaiSlider.OnValueChangeListener { _, _, _ -> calls++ }
        }
        restored.restoreHierarchyState(saved)
        assertThat(restored.value).isEqualTo(65f)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `stretch before the bead is glazed and the rest stays a neutral groove`() {
        val light = slider(value = 50f)
        assertColour(light.trackPixel(0.2f), Color.rgb(134, 172, 247))
        assertColour(light.trackPixel(0.8f), Color.rgb(226, 237, 247))

        val theme = SancaiTheme.dark()
        val dark = slider(theme = theme, value = 50f)
        assertColour(dark.trackPixel(0.2f), theme.bedColor(theme.glazes.cobalt))
        assertColour(dark.trackPixel(0.8f), theme.groove)
    }

    @Test
    fun `a glaze scale blends through its glazes as the value rises`() {
        val theme = SancaiTheme.light()
        val view = slider(value = 100f)
        view.setGlazeScale(listOf(theme.glazes.terracotta, theme.glazes.cobalt, theme.glazes.celadon))
        assertColour(view.trackPixel(0.3f), Color.rgb(128, 184, 151))
        view.value = 50f
        assertColour(view.trackPixel(0.3f), Color.rgb(134, 172, 247))
        view.value = 25f
        assertColour(view.trackPixel(0.1f), Color.rgb(170, 152, 165))
        // A single glaze replaces the scale again.
        view.glaze = theme.glazes.terracotta
        assertColour(view.trackPixel(0.1f), Color.rgb(206, 131, 83))
    }

    @Test
    fun `accessibility services see a seek bar with a range and step actions`() {
        val view = slider(step = 10f, value = 40f)
        assertThat(view.accessibilityClassName.toString()).isEqualTo("android.widget.SeekBar")
        val info = AccessibilityNodeInfo.obtain()
        view.onInitializeAccessibilityNodeInfo(info)
        val range = info.rangeInfo
        assertThat(range).isNotNull()
        assertThat(range!!.min).isEqualTo(0f)
        assertThat(range.max).isEqualTo(100f)
        assertThat(range.current).isEqualTo(40f)

        var calls = 0
        view.onValueChangeListener = SancaiSlider.OnValueChangeListener { _, _, fromUser -> if (fromUser) calls++ }
        assertThat(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)).isTrue()
        assertThat(view.value).isEqualTo(50f)
        assertThat(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null)).isTrue()
        assertThat(view.value).isEqualTo(40f)
        assertThat(calls).isEqualTo(2)
        view.valueFormatter = { "${it.toInt()} %" }
        assertThat(androidx.core.view.ViewCompat.getStateDescription(view).toString()).isEqualTo("40 %")
    }

    @Test
    fun `set progress action moves to a snapped value and is offered only when enabled`() {
        val view = slider(step = 10f, value = 40f)
        val seen = mutableListOf<Float>()
        view.onValueChangeListener = SancaiSlider.OnValueChangeListener { _, value, _ -> seen += value }
        val setProgress = android.R.id.accessibilityActionSetProgress
        val arguments = android.os.Bundle().apply {
            putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 73f)
        }
        assertThat(view.performAccessibilityAction(setProgress, arguments)).isTrue()
        assertThat(view.value).isEqualTo(70f)
        assertThat(seen).containsExactly(70f)
        assertThat(view.performAccessibilityAction(setProgress, null)).isFalse()

        fun actions(): List<Int> = AccessibilityNodeInfo.obtain().let { info ->
            view.onInitializeAccessibilityNodeInfo(info)
            info.actionList.map { it.id }
        }
        assertThat(actions()).containsAtLeast(
            setProgress, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
        )
        assertThat(actions()).doesNotContain(AccessibilityNodeInfo.ACTION_CLICK)
        view.value = 100f
        assertThat(actions()).doesNotContain(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        view.isEnabled = false
        assertThat(actions()).containsNoneOf(setProgress, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        assertThat(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null)).isFalse()
        assertThat(view.value).isEqualTo(100f)
    }

    @Test
    fun `an empty or reversed range and a NaN value rest at the start without crashing`() {
        val empty = slider(from = 5f, to = 5f, value = 9f)
        assertThat(empty.value).isEqualTo(5f)
        Touch.tap(empty, empty.xAt(0.9f))
        assertThat(empty.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(empty.value).isEqualTo(5f)
        // The bead rests at the start: the far end of the track is groove.
        assertColour(empty.trackPixel(0.8f), Color.rgb(226, 237, 247))

        val reversed = slider(from = 10f, to = 0f, value = 3f)
        assertThat(reversed.value).isEqualTo(10f)

        val view = slider(value = 30f)
        view.value = Float.NaN
        assertThat(view.value).isEqualTo(0f)
        view.setGlazeScale(emptyList())
        assertColour(view.trackPixel(0.8f), Color.rgb(226, 237, 247))
    }

    @Test
    fun `space and enter are not consumed because the slider has no click`() {
        val view = slider(value = 20f)
        assertThat(view.press(KeyEvent.KEYCODE_ENTER)).isFalse()
        assertThat(view.press(KeyEvent.KEYCODE_SPACE)).isFalse()
        assertThat(view.isFocusable).isTrue()
    }

    @Test
    fun `shadows have faded out before the edge of the view`() {
        for (theme in listOf(SancaiTheme.light(), SancaiTheme.dark())) {
            val view = slider(theme = theme, value = 0f)
            val bitmap = Snapshots.render(view, WIDTH_DP)
            val canvas = theme.colors.canvas
            // Under the track, under the bead, and beside the bead resting at the start. The
            // last 1 % of the bead's shadow tail may meet the bottom edge; nothing visible is cut.
            assertColour(bitmap.getPixel(view.xAt(0.7f).toInt(), view.height - 1), canvas)
            assertColour(bitmap.getPixel(view.xAt(0f).toInt(), view.height - 1), canvas, tolerance = 4)
            assertColour(bitmap.getPixel(0, view.height / 2), canvas)
            assertColour(bitmap.getPixel(view.xAt(0f).toInt(), 0), canvas)
        }
    }

    @Test
    fun `a tap glides in a window, jumps with reduced motion, and detaching ends the glide`() {
        val cobalt = Color.rgb(134, 172, 247)
        val groove = Color.rgb(226, 237, 247)
        val view = SancaiSlider(Snapshots.context()).apply { valueTo = 100f }
        Snapshots.attach(view, WIDTH_DP)
        Touch.tap(view, view.xAt(0.8f))
        // The value is final at once; the bead is still on its way.
        assertThat(view.value).isWithin(0.5f).of(80f)
        assertColour(view.trackPixel(0.5f), groove)
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        assertColour(view.trackPixel(0.5f), cobalt)

        view.setValue(0f, animate = true)
        (view.parent as android.view.ViewGroup).removeView(view)
        Snapshots.layout(view, WIDTH_DP)
        assertColour(view.trackPixel(0.5f), groove)

        val calm = SancaiSlider(Snapshots.context(SancaiTheme.light().copy(reduceMotion = true)))
        calm.valueTo = 100f
        Snapshots.attach(calm, WIDTH_DP)
        Touch.tap(calm, calm.xAt(0.8f))
        assertColour(calm.trackPixel(0.5f), cobalt)
    }

    @Test
    fun `right to left drag follows the finger mirrored`() {
        val view = slider(rtl = true)
        val y = view.height / 2f
        Touch.drag(view, view.xAt(0.9f), y, view.xAt(0.3f), y)
        assertThat(view.value).isWithin(0.5f).of(70f)
    }

    @Test
    fun `a cancelled drag keeps its value and ends tracking, disabling mid drag does too`() {
        val view = slider()
        var stops = 0
        view.onStopTrackingTouchListener = SancaiSlider.OnStopTrackingTouchListener { stops++ }
        val y = view.height / 2f
        Touch.drag(view, view.xAt(0.1f), y, view.xAt(0.6f), y, release = false)
        Touch.dispatch(view, MotionEvent.ACTION_CANCEL, view.xAt(0.6f), y)
        assertThat(view.value).isWithin(0.5f).of(60f)
        assertThat(stops).isEqualTo(1)

        Touch.drag(view, view.xAt(0.6f), y, view.xAt(0.2f), y, release = false)
        view.isEnabled = false
        assertThat(stops).isEqualTo(2)
        Touch.dispatch(view, MotionEvent.ACTION_MOVE, view.xAt(0.9f), y)
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.xAt(0.9f), y)
        assertThat(view.value).isWithin(0.5f).of(20f)
        assertThat(stops).isEqualTo(2)
    }

    @Test
    fun `large text scale changes nothing because the slider has no text`() {
        val plain = slider(value = 40f)
        val large = slider(theme = SancaiTheme.light().copy(textScale = 1.13f), value = 40f)
        assertThat(large.height).isEqualTo(plain.height)
        assertThat(Snapshots.render(large, WIDTH_DP).sameAs(Snapshots.render(plain, WIDTH_DP))).isTrue()
    }

    @Test
    fun `xml attributes set range, step, value, glaze mode and enabled`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.sancaiValueFrom, "10")
            .addAttribute(R.attr.sancaiValueTo, "50")
            .addAttribute(R.attr.sancaiStepSize, "10")
            .addAttribute(R.attr.sancaiValue, "32")
            .addAttribute(R.attr.sancaiSliderGlazeMode, "scale")
            .addAttribute(android.R.attr.enabled, "false")
            .build()
        val view = SancaiSlider(Snapshots.context(), attrs)
        assertThat(view.valueFrom).isEqualTo(10f)
        assertThat(view.valueTo).isEqualTo(50f)
        assertThat(view.stepSize).isEqualTo(10f)
        assertThat(view.value).isEqualTo(30f)
        assertThat(view.isEnabled).isFalse()
        val ochre = SancaiSlider(
            Snapshots.context(),
            Robolectric.buildAttributeSet().addAttribute(R.attr.sancaiGlaze, "ochre").build(),
        )
        assertThat(ochre.glaze).isEqualTo(SancaiTheme.light().glazes.ochre)
        assertThat(ochre.value).isEqualTo(0f)
    }

    @Test
    fun `snapshot of sliders in light and dark`() {
        for ((name, theme) in listOf("light" to SancaiTheme.light(), "dark" to SancaiTheme.dark())) {
            val context = Snapshots.context(theme)
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            val glazes = theme.glazes
            fun row(value: Float, setup: SancaiSlider.() -> Unit = {}): SancaiSlider = SancaiSlider(context).apply {
                valueTo = 100f
                this.value = value
                setup()
                column.addView(
                    this,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
                )
            }
            row(0f)
            row(40f)
            row(100f)
            row(15f) { setGlazeScale(glazes.scale(4)) }
            row(50f) { setGlazeScale(glazes.scale(4)) }
            row(85f) { setGlazeScale(glazes.scale(4)) }
            row(60f) { glaze = glazes.ochre }
            row(60f) { isEnabled = false }
            val held = row(30f) { glaze = glazes.celadon }
            // The last row is being dragged: its bead is drawn pressed.
            Snapshots.layout(column, 360)
            val y = held.height / 2f
            Touch.drag(held, held.width * 0.3f, y, held.width * 0.7f, y, release = false)
            val bitmap = Snapshots.capture(column, "slider_$name", 360)
            assertThat(bitmap.height).isGreaterThan(0)
        }
    }

    private companion object {
        const val WIDTH_DP = 300
        const val COLOUR_TOLERANCE = 2
    }
}
