package io.github.avalonskyafar.mallet.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Parcelable
import android.util.SparseArray
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.testing.Touch
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletSwitchTest {

    private fun switch(
        checked: Boolean = false,
        theme: MalletTheme = MalletTheme.light(),
    ): MalletSwitch = MalletSwitch(Snapshots.context(theme)).apply {
        id = View.generateViewId()
        setChecked(checked, animate = false)
        Snapshots.layout(this, WIDTH_DP)
    }

    /** Bed colour on the vertical centre of the track, at [xFraction] of the view width. */
    private fun MalletSwitch.bedPixel(xFraction: Float): Int {
        val density = resources.displayMetrics.density
        // The track is 1 dp lower at the top than at the bottom of its box, which is centred.
        val centreY = height / 2f - 1f * density
        return Snapshots.render(this, WIDTH_DP).getPixel((width * xFraction).toInt(), centreY.toInt())
    }

    private fun MalletSwitch.press(code: Int): Boolean = onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))

    private fun MalletSwitch.count(): IntArray {
        val calls = IntArray(1)
        setOnCheckedChangeListener { _, _ -> calls[0]++ }
        return calls
    }

    @Test
    fun `default size is a 61 by 34 dp track in a 44 dp touch target`() {
        val view = MalletSwitch(Snapshots.context())
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val density = view.resources.displayMetrics.density
        assertThat(view.measuredWidth / density).isWithin(0.5f).of(61f)
        assertThat(view.measuredHeight / density).isWithin(0.5f).of(44f)

        // The track itself is still 34 dp high (30 dp of bed between its gutters), centred.
        val canvas = MalletTheme.light().colors.canvas
        val bitmap = Snapshots.render(view, 61)
        val x = (view.width * 0.7f).toInt()
        assertThat(bitmap.getPixel(x, (2f * density).toInt())).isEqualTo(canvas)
        assertThat(bitmap.getPixel(x, (9f * density).toInt())).isNotEqualTo(canvas)
        assertThat(bitmap.getPixel(x, (33f * density).toInt())).isNotEqualTo(canvas)

        // A view forced to 34 dp is all track, as in the original.
        val tight = Snapshots.render(MalletSwitch(Snapshots.context()), 61, 34)
        assertThat(tight.getPixel(x, (4f * density).toInt())).isNotEqualTo(canvas)
    }

    @Test
    fun `bed is bronze when off and cobalt when on`() {
        val view = switch()
        assertThat(view.bedPixel(OFF_PROBE)).isEqualTo(Color.rgb(206, 131, 83))
        view.setChecked(true, animate = false)
        assertThat(view.bedPixel(ON_PROBE)).isEqualTo(Color.rgb(134, 172, 247))
    }

    @Test
    fun `hues are properties and dark theme paints them through the theme`() {
        val theme = MalletTheme.dark()
        val view = switch(theme = theme)
        assertThat(view.bedPixel(OFF_PROBE)).isEqualTo(theme.bedColor(theme.hues.bronze))
        view.onHue = theme.hues.jade
        view.setChecked(true, animate = false)
        assertThat(view.bedPixel(ON_PROBE)).isEqualTo(theme.bedColor(theme.hues.jade))
    }

    @Test
    fun `thumb is a neutral bead on the side of the state`() {
        val view = switch()
        val light = MalletTheme.light()
        // Under the thumb the bed is covered: no bronze in the middle of the off place.
        val y = view.height * 0.45f
        val off = Snapshots.render(view, WIDTH_DP).getPixel((view.width * ON_PROBE).toInt(), y.toInt())
        assertThat(off).isNotEqualTo(light.bedColor(light.hues.bronze))
        assertThat(Color.red(off) - Color.blue(off)).isLessThan(24)
    }

    @Test
    fun `tap toggles and notifies once per change`() {
        val view = switch()
        val seen = mutableListOf<Boolean>()
        view.setOnCheckedChangeListener { _, checked -> seen += checked }
        Touch.tap(view, view.width * 0.2f)
        assertThat(view.isChecked).isTrue()
        Touch.tap(view, view.width * 0.2f)
        assertThat(view.isChecked).isFalse()
        assertThat(seen).containsExactly(true, false).inOrder()
    }

    @Test
    fun `setting from code never calls the listener`() {
        val view = switch()
        val calls = view.count()
        view.isChecked = true
        view.toggle()
        view.setChecked(true, animate = false)
        assertThat(view.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(0)
    }

    @Test
    fun `a listener can roll the change back`() {
        val view = switch()
        view.setOnCheckedChangeListener { v, _ ->
            (v as MalletSwitch).setChecked(false, animate = false)
        }
        Touch.tap(view, view.width / 2f)
        assertThat(view.isChecked).isFalse()
        assertThat(view.bedPixel(OFF_PROBE)).isEqualTo(Color.rgb(206, 131, 83))
    }

    @Test
    fun `horizontal drag follows the finger and commits to the nearer side`() {
        val view = switch()
        val calls = view.count()
        val y = view.height / 2f
        Touch.drag(view, view.width * 0.25f, y, view.width * 0.95f, y, release = false)
        // Not committed yet, but the bed already shows where the thumb is.
        assertThat(view.isChecked).isFalse()
        assertThat(view.bedPixel(ON_PROBE)).isEqualTo(Color.rgb(134, 172, 247))
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.width * 0.95f, y)
        assertThat(view.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(1)
    }

    @Test
    fun `a second finger changes nothing once the first has lifted`() {
        val view = switch()
        val calls = view.count()
        val y = view.height / 2f
        val a = view.width * 0.95f
        val b = view.width * 0.3f
        Touch.drag(view, view.width * 0.25f, y, a, y, release = false)
        Touch.multi(view, MotionEvent.ACTION_POINTER_DOWN, 1, Touch.Finger(0, a, y), Touch.Finger(1, b, y))
        Touch.multi(view, MotionEvent.ACTION_POINTER_UP, 0, Touch.Finger(0, a, y), Touch.Finger(1, b, y))
        assertThat(view.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(1)
        Touch.multi(view, MotionEvent.ACTION_MOVE, 0, Touch.Finger(1, view.width * 0.15f, y))
        Touch.multi(view, MotionEvent.ACTION_MOVE, 0, Touch.Finger(1, 0f, y))
        Touch.multi(view, MotionEvent.ACTION_UP, 0, Touch.Finger(1, 0f, y))
        assertThat(view.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(1)
        val rest = switch(checked = true)
        assertThat(Snapshots.render(view, WIDTH_DP).sameAs(Snapshots.render(rest, WIDTH_DP))).isTrue()
    }

    @Test
    fun `a tap with a second finger resting on the switch is one tap`() {
        val view = switch()
        val calls = view.count()
        val y = view.height / 2f
        val a = view.width * 0.5f
        val b = view.width * 0.8f
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, a, y)
        Touch.multi(view, MotionEvent.ACTION_POINTER_DOWN, 1, Touch.Finger(0, a, y), Touch.Finger(1, b, y))
        Touch.multi(view, MotionEvent.ACTION_POINTER_UP, 0, Touch.Finger(0, a, y), Touch.Finger(1, b, y))
        Touch.multi(view, MotionEvent.ACTION_UP, 0, Touch.Finger(1, b, y))
        assertThat(view.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(1)
    }

    @Test
    fun `a drag that stops short of the middle returns without a callback`() {
        val view = switch()
        val calls = view.count()
        val y = view.height / 2f
        // Out to the far side, then back again before letting go.
        Touch.drag(view, view.width * 0.25f, y, view.width * 0.95f, y, release = false)
        Touch.dispatch(view, MotionEvent.ACTION_MOVE, view.width * 0.38f, y)
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.width * 0.38f, y)
        assertThat(view.isChecked).isFalse()
        assertThat(calls[0]).isEqualTo(0)
        // The thumb glides home and the bed is plain bronze again.
        org.robolectric.shadows.ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertThat(view.bedPixel(OFF_PROBE)).isEqualTo(Color.rgb(206, 131, 83))
    }

    @Test
    fun `a drag that turns vertical leaves the state untouched`() {
        val view = switch()
        val calls = view.count()
        val x = view.width * 0.3f
        Touch.drag(view, x, 10f, x + 4f, view.height * 3f)
        assertThat(view.isChecked).isFalse()
        assertThat(calls[0]).isEqualTo(0)
    }

    @Test
    fun `right to left layouts mirror the thumb and the drag direction`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val view = MalletSwitch(Snapshots.context()).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            Snapshots.layout(this, WIDTH_DP)
        }
        // Off rests at the right, so the free bed is at the left.
        assertThat(view.bedPixel(ON_PROBE)).isEqualTo(Color.rgb(206, 131, 83))
        val y = view.height / 2f
        Touch.drag(view, view.width * 0.75f, y, view.width * 0.05f, y)
        assertThat(view.isChecked).isTrue()
        assertThat(view.bedPixel(OFF_PROBE)).isEqualTo(Color.rgb(134, 172, 247))
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(view.isChecked).isFalse()
    }

    @Test
    fun `space and enter toggle, arrows move towards a side and fall through at it`() {
        val view = switch()
        val calls = view.count()
        assertThat(view.press(KeyEvent.KEYCODE_SPACE)).isTrue()
        assertThat(view.isChecked).isTrue()
        assertThat(view.press(KeyEvent.KEYCODE_ENTER)).isTrue()
        assertThat(view.isChecked).isFalse()
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(view.isChecked).isTrue()
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(view.press(KeyEvent.KEYCODE_TAB)).isFalse()
        assertThat(calls[0]).isEqualTo(3)
    }

    @Test
    fun `disabled switch ignores input and pales`() {
        val view = switch()
        val calls = view.count()
        view.isEnabled = false
        Touch.tap(view, view.width / 2f)
        view.press(KeyEvent.KEYCODE_SPACE)
        view.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        view.performClick()
        assertThat(view.isChecked).isFalse()
        assertThat(calls[0]).isEqualTo(0)
        val canvas = MalletTheme.light().colors.canvas
        val pale = view.bedPixel(OFF_PROBE)
        assertThat(pale).isNotEqualTo(Color.rgb(206, 131, 83))
        assertThat(Color.red(pale)).isWithin(2).of((206 * 107 + Color.red(canvas) * 148) / 255)
    }

    @Test
    fun `state survives save and restore without a callback`() {
        val view = switch(checked = true)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)
        val restored = MalletSwitch(Snapshots.context()).apply { id = view.id }
        val calls = restored.count()
        restored.restoreHierarchyState(saved)
        assertThat(restored.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(0)
        Snapshots.layout(restored, WIDTH_DP)
        assertThat(restored.bedPixel(ON_PROBE)).isEqualTo(Color.rgb(134, 172, 247))
    }

    @Test
    fun `accessibility sees a switch with a checked state and an on off description`() {
        val view = switch()
        assertThat(view.accessibilityClassName.toString()).isEqualTo("android.widget.Switch")
        assertThat(ViewCompat.getStateDescription(view).toString()).isEqualTo("Off")
        val calls = view.count()
        // The click action of a screen reader toggles as a user would.
        assertThat(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)).isTrue()
        assertThat(view.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(1)
        assertThat(ViewCompat.getStateDescription(view).toString()).isEqualTo("On")
        val node = view.createAccessibilityNodeInfo()
        assertThat(node).isNotNull()
        assertThat(node!!.isCheckable).isTrue()
        assertThat(node.isChecked).isTrue()
        view.onStateDescription = "Allowed"
        assertThat(ViewCompat.getStateDescription(view).toString()).isEqualTo("Allowed")
    }

    @Test
    fun `an attached switch glides and ends on the committed side`() {
        val view = MalletSwitch(Snapshots.context())
        Snapshots.attach(view, WIDTH_DP)
        var clicks = 0
        view.setOnClickListener { clicks++ }
        Touch.tap(view, view.width / 2f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper(400, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertThat(view.isChecked).isTrue()
        assertThat(clicks).isEqualTo(1)
        assertThat(view.bedPixel(ON_PROBE)).isEqualTo(Color.rgb(134, 172, 247))
    }

    @Test
    fun `reduced motion jumps to the committed side`() {
        val view = MalletSwitch(Snapshots.context(MalletTheme.light().copy(reduceMotion = true)))
        Snapshots.attach(view, WIDTH_DP)
        Touch.tap(view, view.width / 2f)
        // No time passes: the bed is already fully cobalt.
        assertThat(view.bedPixel(ON_PROBE)).isEqualTo(Color.rgb(134, 172, 247))
    }

    @Test
    fun `a glide that loses its window ends on the committed side`() {
        val view = MalletSwitch(Snapshots.context())
        Snapshots.attach(view, WIDTH_DP)
        Touch.tap(view, view.width / 2f)
        (view.parent as android.view.ViewGroup).removeView(view)
        assertThat(view.isChecked).isTrue()
        assertThat(view.bedPixel(ON_PROBE)).isEqualTo(Color.rgb(134, 172, 247))
    }

    @Test
    fun `performClick and the accessibility click act as user input`() {
        val view = switch()
        val seen = mutableListOf<Boolean>()
        view.setOnCheckedChangeListener { _, checked -> seen += checked }
        var clicks = 0
        view.setOnClickListener { clicks++ }
        view.performClick()
        assertThat(view.isChecked).isTrue()
        view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
        Snapshots.idle()
        assertThat(view.isChecked).isFalse()
        assertThat(seen).containsExactly(true, false).inOrder()
        assertThat(clicks).isEqualTo(2)
    }

    @Test
    fun `a gesture that began while disabled changes nothing`() {
        val view = switch()
        val calls = view.count()
        view.isEnabled = false
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.width * 0.2f, view.height / 2f)
        view.isEnabled = true
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.width * 0.2f, view.height / 2f)
        assertThat(view.isChecked).isFalse()
        assertThat(calls[0]).isEqualTo(0)
    }

    @Test
    fun `text scale does not change the size and a larger frame keeps the thumb inside the track`() {
        val scaled = switch(theme = MalletTheme.light().copy(textScale = 1.13f))
        val plain = switch()
        assertThat(scaled.width).isEqualTo(plain.width)
        assertThat(scaled.height).isEqualTo(plain.height)

        val light = MalletTheme.light()
        val big = MalletSwitch(Snapshots.context()).apply { setChecked(true, animate = false) }
        val bitmap = Snapshots.render(big, 88, 44)
        val density = big.resources.displayMetrics.density
        val centreY = (big.height / 2f - 1f * density).toInt()
        // Free cobalt bed on the left, neutral thumb at the right end, vertically centred.
        assertThat(bitmap.getPixel((big.width * 0.3f).toInt(), centreY)).isEqualTo(light.bedColor(light.hues.cobalt))
        val thumb = bitmap.getPixel((big.width - 18f * density).toInt(), centreY)
        assertThat(Color.blue(thumb) - Color.red(thumb)).isLessThan(24)
        Touch.tap(big, big.width * 0.5f, big.height / 2f)
        assertThat(big.isChecked).isFalse()
    }

    @Test
    fun `snapshot of the switch in light and dark`() {
        for ((name, theme) in listOf("light" to MalletTheme.light(), "dark" to MalletTheme.dark())) {
            val context = Snapshots.context(theme)
            val density = context.resources.displayMetrics.density
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * density).toInt()
                setPadding(pad, pad / 2, pad, pad / 2)
            }
            fun row(label: String, configure: MalletSwitch.() -> Unit): MalletSwitch {
                val view = MalletSwitch(context).apply(configure)
                val line = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = (48 * density).toInt()
                }
                line.addView(
                    MalletText.create(context, label, MalletTextRole.BODY),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                line.addView(view)
                column.addView(line)
                return view
            }
            row("Off") {}
            row("On") { setChecked(true, animate = false) }
            val held = row("Held mid-way") {}
            row("Off, disabled") { isEnabled = false }
            row("On, disabled") {
                setChecked(true, animate = false)
                isEnabled = false
            }
            row("Mist to jade") {
                offHue = theme.hues.mist
                onHue = theme.hues.jade
                setChecked(true, animate = false)
            }
            row("Mist, off") { offHue = theme.hues.mist }
            Snapshots.layout(column, SNAPSHOT_WIDTH_DP)
            Touch.drag(held, held.width * 0.25f, held.height / 2f, held.width * 0.62f, held.height / 2f, release = false)
            val bitmap = Snapshots.capture(column, "switch_$name", SNAPSHOT_WIDTH_DP)
            assertThat(bitmap.height).isGreaterThan(0)
        }
    }

    private companion object {
        const val WIDTH_DP = 61
        const val SNAPSHOT_WIDTH_DP = 220

        /** Free bed while the thumb rests at the left: right of the middle. */
        const val OFF_PROBE = 0.72f

        /** Free bed while the thumb rests at the right: left of the middle. */
        const val ON_PROBE = 0.28f
    }
}
