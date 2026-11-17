package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import org.junit.Test
import org.junit.runner.RunWith
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
