package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiSegmentedTrackTest {

    private fun track(
        vararg options: String,
        theme: SancaiTheme = SancaiTheme.light(),
        selected: Int = 0,
    ): SancaiSegmentedTrack = SancaiSegmentedTrack(Snapshots.context(theme)).apply {
        id = View.generateViewId()
        setOptions(*options)
        select(selected, animate = false)
        Snapshots.layout(this, WIDTH_DP)
    }

    /** Horizontal centre of the option shown in [slot] (counted from the left). */
    private fun SancaiSegmentedTrack.slotCentre(slot: Int, count: Int): Float {
        val density = resources.displayMetrics.density
        val left = 5.5f * density
        val segment = (width - 11f * density) / count
        return left + segment * (slot + 0.5f)
    }

    private fun SancaiSegmentedTrack.bedPixel(xFraction: Float): Int {
        val density = resources.displayMetrics.density
        val centreY = ((1.5f * density) + (height - 6.5f * density)) / 2f
        return Snapshots.render(this, WIDTH_DP).getPixel((width * xFraction).toInt(), centreY.toInt())
    }

    @Test
    fun `default height is a 52dp track plus its shadow gutter`() {
        val view = track("Off", "On")
        assertThat(view.height / view.resources.displayMetrics.density).isWithin(0.5f).of(60f)
    }

    @Test
    fun `bed is painted in the exact glaze of the selected option`() {
        val view = track("Off", "On")
        assertThat(view.bedPixel(0.60f)).isEqualTo(Color.rgb(206, 131, 83))
        view.select(1, animate = false)
        assertThat(view.bedPixel(0.40f)).isEqualTo(Color.rgb(134, 172, 247))
    }

    @Test
    fun `four options run terracotta ochre cobalt celadon`() {
        val view = track("Off", "Gentle", "Standard", "Strict")
        val expected = listOf(
            Color.rgb(206, 131, 83), Color.rgb(222, 190, 119),
            Color.rgb(134, 172, 247), Color.rgb(128, 184, 151),
        )
        // Sample the gap between two labels that is farthest from the thumb.
        val probes = listOf(0.75f, 0.75f, 0.25f, 0.25f)
        for (index in 0..3) {
            view.select(index, animate = false)
            assertThat(view.bedPixel(probes[index])).isEqualTo(expected[index])
        }
    }

    @Test
    fun `dark theme keeps a restrained share of the glaze in the groove`() {
        val theme = SancaiTheme.dark()
        val view = track("Off", "On", theme = theme)
        assertThat(view.bedPixel(0.60f)).isEqualTo(theme.bedColor(theme.glazes.terracotta))
        val neutral = track("Off", "On", theme = theme.copy(nightGlaze = 0f))
        assertThat(neutral.bedPixel(0.60f)).isEqualTo(theme.groove)
    }

    @Test
    fun `tap selects the option under the finger and notifies once`() {
        val view = track("Off", "Gentle", "Standard", "Strict")
        val seen = mutableListOf<Int>()
        view.setOnSelectionChangeListener { _, index -> seen += index }
        Touch.tap(view, view.slotCentre(2, 4))
        assertThat(view.selectedIndex).isEqualTo(2)
        Touch.tap(view, view.slotCentre(2, 4))
        assertThat(seen).containsExactly(2)
    }

    @Test
    fun `selecting from code never calls the listener`() {
        val view = track("Off", "On")
        var calls = 0
        view.setOnSelectionChangeListener { _, _ -> calls++ }
        view.select(1)
        assertThat(view.selectedIndex).isEqualTo(1)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `a listener can roll the change back`() {
        val view = track("Off", "On")
        view.setOnSelectionChangeListener { t, _ -> t.select(0) }
        Touch.tap(view, view.slotCentre(1, 2))
        assertThat(view.selectedIndex).isEqualTo(0)
    }

    @Test
    fun `horizontal drag follows the finger and commits on release`() {
        val view = track("Off", "Gentle", "Standard", "Strict")
        val y = view.height / 2f
        Touch.drag(view, view.slotCentre(0, 4), y, view.slotCentre(3, 4), y, release = false)
        assertThat(view.selectedIndex).isEqualTo(0)
        // While the thumb is over the last option the bed already shows that option's glaze.
        assertThat(view.bedPixel(0.25f)).isEqualTo(Color.rgb(128, 184, 151))
        Touch.dispatch(view, android.view.MotionEvent.ACTION_UP, view.slotCentre(3, 4), y)
        assertThat(view.selectedIndex).isEqualTo(3)
    }

    @Test
    fun `a drag that turns vertical leaves the selection untouched`() {
        val view = track("Off", "Gentle", "Standard", "Strict")
        var calls = 0
        view.setOnSelectionChangeListener { _, _ -> calls++ }
        val x = view.slotCentre(3, 4)
        Touch.drag(view, x, 10f, x + 4f, view.height * 3f)
        assertThat(view.selectedIndex).isEqualTo(0)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `right to left layouts mirror the option order`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val view = SancaiSegmentedTrack(Snapshots.context()).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setOptions("Off", "Gentle", "Standard", "Strict")
            Snapshots.layout(this, WIDTH_DP)
        }
        Touch.tap(view, view.slotCentre(0, 4))
        assertThat(view.selectedIndex).isEqualTo(3)
    }

    @Test
    fun `arrow keys step through the options and stop at the ends`() {
        val view = track("Off", "Gentle", "Standard")
        fun press(code: Int) = view.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
        assertThat(press(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(press(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(view.selectedIndex).isEqualTo(2)
        // At the end the key is not consumed, so focus can move on.
        assertThat(press(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(press(KeyEvent.KEYCODE_DPAD_LEFT)).isTrue()
        assertThat(view.selectedIndex).isEqualTo(1)
    }

    @Test
    fun `disabled track ignores input`() {
        val view = track("Off", "On")
        view.isEnabled = false
        Touch.tap(view, view.slotCentre(1, 2))
        assertThat(view.selectedIndex).isEqualTo(0)
    }

    @Test
    fun `selection survives save and restore`() {
        val view = track("Off", "Gentle", "Standard", "Strict", selected = 3)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)
        val restored = SancaiSegmentedTrack(Snapshots.context()).apply {
            id = view.id
            setOptions("Off", "Gentle", "Standard", "Strict")
        }
        restored.restoreHierarchyState(saved)
        assertThat(restored.selectedIndex).isEqualTo(3)
    }

    @Test
    fun `every option is a radio button for accessibility services`() {
        val view = track("Off", "Gentle", "Standard", selected = 1)
        val provider = view.accessibilityNodeProvider
        assertThat(provider).isNotNull()
        val checked = provider.createAccessibilityNodeInfo(1)
        val unchecked = provider.createAccessibilityNodeInfo(2)
        assertThat(checked).isNotNull()
        assertThat(unchecked).isNotNull()
        assertThat(checked!!.text.toString()).isEqualTo("Gentle")
        assertThat(checked.isChecked).isTrue()
        assertThat(unchecked!!.isChecked).isFalse()
        assertThat(unchecked.className.toString()).isEqualTo("android.widget.RadioButton")
    }

    @Test
    fun `state description names the selected option from the start and after new options`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.sancaiOptions, "@android:array/emailAddressTypes")
            .addAttribute(R.attr.sancaiSelectedIndex, "2")
            .build()
        val inflated = SancaiSegmentedTrack(Snapshots.context(), attrs)
        assertThat(inflated.selectedIndex).isEqualTo(2)
        assertThat(ViewCompat.getStateDescription(inflated).toString())
            .isEqualTo(inflated.options[2].toString())

        val view = track("Off", "Gentle", "Strict", selected = 2)
        view.setOptions("Low", "High")
        assertThat(view.selectedIndex).isEqualTo(1)
        assertThat(ViewCompat.getStateDescription(view).toString()).isEqualTo("High")
    }

    @Test
    fun `disabling under the finger lets go and rests on the selected option`() {
        val view = track("Off", "Gentle", "Standard", "Strict")
        var calls = 0
        view.setOnSelectionChangeListener { _, _ -> calls++ }
        val y = view.height / 2f
        Touch.drag(view, view.slotCentre(0, 4), y, view.slotCentre(2, 4) + 9f, y, release = false)
        view.isEnabled = false
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.slotCentre(2, 4) + 9f, y)
        assertThat(view.selectedIndex).isEqualTo(0)
        assertThat(calls).isEqualTo(0)
        // Neither pressed nor between options: it looks like any disabled track on "Off".
        val rest = track("Off", "Gentle", "Standard", "Strict").apply { isEnabled = false }
        assertThat(Snapshots.render(view, WIDTH_DP).sameAs(Snapshots.render(rest, WIDTH_DP))).isTrue()
        // The rest of that gesture stays dead after enabling again.
        view.isEnabled = true
        Touch.dispatch(view, MotionEvent.ACTION_MOVE, view.slotCentre(3, 4), y)
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.slotCentre(3, 4), y)
        assertThat(view.selectedIndex).isEqualTo(0)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `replacing the options under the finger lets go`() {
        val view = track("Off", "Gentle", "Standard", "Strict")
        val y = view.height / 2f
        Touch.drag(view, view.slotCentre(0, 4), y, view.slotCentre(2, 4) + 9f, y, release = false)
        view.setOptions("Low", "High")
        val rest = track("Low", "High")
        assertThat(Snapshots.render(view, WIDTH_DP).sameAs(Snapshots.render(rest, WIDTH_DP))).isTrue()
        // The finger that is still down chooses nothing among the new options.
        var calls = 0
        view.setOnSelectionChangeListener { _, _ -> calls++ }
        Touch.dispatch(view, MotionEvent.ACTION_UP, view.slotCentre(1, 2), y)
        assertThat(view.selectedIndex).isEqualTo(0)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `new options with no finger down do not release the scrolling parent`() {
        val view = track("Off", "Gentle", "Standard")
        val asked = mutableListOf<Boolean>()
        val parent = object : android.widget.FrameLayout(view.context) {
            override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                asked += disallowIntercept
            }
        }
        parent.addView(view)
        view.setOptions("Low", "High")
        assertThat(asked).isEmpty()
    }

    @Test
    fun `a held enter steps once and a lone option leaves the key alone`() {
        val view = track("Off", "Gentle", "Standard")
        val seen = mutableListOf<Int>()
        view.setOnSelectionChangeListener { _, index -> seen += index }
        fun enter(repeat: Int) = view.onKeyDown(
            KeyEvent.KEYCODE_ENTER,
            KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, repeat),
        )
        assertThat(enter(0)).isTrue()
        assertThat(enter(1)).isTrue()
        assertThat(enter(2)).isTrue()
        assertThat(seen).containsExactly(1)

        val lone = track("Only")
        val code = KeyEvent.KEYCODE_SPACE
        assertThat(lone.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))).isFalse()
    }

    @Test
    fun `a glide that loses its window rests on the selected option`() {
        val view = track("Off", "Gentle", "Standard", "Strict")
        Snapshots.attach(view, WIDTH_DP)
        Touch.tap(view, view.slotCentre(3, 4))
        val rest = track("Off", "Gentle", "Standard", "Strict", selected = 3)
        // No frame has run yet: the thumb has not left the first option.
        assertThat(Snapshots.render(view, WIDTH_DP).sameAs(Snapshots.render(rest, WIDTH_DP))).isFalse()
        (view.parent as ViewGroup).removeView(view)
        assertThat(view.selectedIndex).isEqualTo(3)
        assertThat(Snapshots.render(view, WIDTH_DP).sameAs(Snapshots.render(rest, WIDTH_DP))).isTrue()
    }

    @Test
    fun `a second finger changes nothing once the first has lifted`() {
        val view = track("Off", "Gentle", "Standard", "Strict")
        val seen = mutableListOf<Int>()
        view.setOnSelectionChangeListener { _, index -> seen += index }
        val y = view.height / 2f
        val a = view.slotCentre(2, 4)
        Touch.drag(view, view.slotCentre(0, 4), y, a, y, release = false)
        Touch.multi(view, MotionEvent.ACTION_POINTER_DOWN, 1, Touch.Finger(0, a, y), Touch.Finger(1, view.slotCentre(3, 4), y))
        Touch.multi(view, MotionEvent.ACTION_POINTER_UP, 0, Touch.Finger(0, a, y), Touch.Finger(1, view.slotCentre(3, 4), y))
        assertThat(view.selectedIndex).isEqualTo(2)
        Touch.multi(view, MotionEvent.ACTION_MOVE, 0, Touch.Finger(1, view.slotCentre(1, 4), y))
        Touch.multi(view, MotionEvent.ACTION_MOVE, 0, Touch.Finger(1, view.slotCentre(0, 4), y))
        Touch.multi(view, MotionEvent.ACTION_UP, 0, Touch.Finger(1, view.slotCentre(0, 4), y))
        assertThat(view.selectedIndex).isEqualTo(2)
        assertThat(seen).containsExactly(2)
        val rest = track("Off", "Gentle", "Standard", "Strict", selected = 2)
        assertThat(Snapshots.render(view, WIDTH_DP).sameAs(Snapshots.render(rest, WIDTH_DP))).isTrue()
    }

    @Test
    fun `a tap with a second finger resting on the track is one tap`() {
        val view = track("Off", "Gentle", "Standard", "Strict")
        val seen = mutableListOf<Int>()
        view.setOnSelectionChangeListener { _, index -> seen += index }
        val y = view.height / 2f
        val a = view.slotCentre(1, 4)
        val b = view.slotCentre(3, 4)
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, a, y)
        Touch.multi(view, MotionEvent.ACTION_POINTER_DOWN, 1, Touch.Finger(0, a, y), Touch.Finger(1, b, y))
        Touch.multi(view, MotionEvent.ACTION_POINTER_UP, 0, Touch.Finger(0, a, y), Touch.Finger(1, b, y))
        Touch.multi(view, MotionEvent.ACTION_UP, 0, Touch.Finger(1, b, y))
        assertThat(view.selectedIndex).isEqualTo(1)
        assertThat(seen).containsExactly(1)
    }

    @Test
    fun `the track itself offers no click, its options do`() {
        val view = track("Off", "Gentle", "Standard", selected = 1)
        // In a window: a view without one fills in nothing, and the test would prove nothing.
        Snapshots.attach(view, WIDTH_DP)
        val click = AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK
        val provider = view.accessibilityNodeProvider
        val host = provider.createAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeProvider.HOST_VIEW_ID)!!
        assertThat(host.isClickable).isFalse()
        assertThat(host.actionList).doesNotContain(click)
        // Touch handling still needs the view itself to be clickable.
        assertThat(view.isClickable).isTrue()
        val option = provider.createAccessibilityNodeInfo(2)!!
        assertThat(option.actionList).contains(click)
    }

    @Test
    fun `snapshot of the scale in light and dark`() {
        for (theme in listOf("light" to SancaiTheme.light(), "dark" to SancaiTheme.dark())) {
            val context = Snapshots.context(theme.second)
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                clipChildren = false
            }
            val rows = listOf(
                listOf("Off", "On") to 0,
                listOf("Off", "On") to 1,
                listOf("Off", "Notify", "Skip") to 1,
                listOf("Off", "Gentle", "Standard", "Strict") to 2,
                listOf("Off", "Gentle", "Standard", "Strict") to 3,
            )
            for ((options, selected) in rows) {
                column.addView(
                    SancaiSegmentedTrack(context).apply {
                        this.options = options
                        select(selected, animate = false)
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
                )
            }
            val bitmap = Snapshots.capture(column, "segmented_track_${theme.first}", 360)
            assertThat(bitmap.height).isGreaterThan(0)
        }
    }

    private companion object {
        const val WIDTH_DP = 300
    }
}
