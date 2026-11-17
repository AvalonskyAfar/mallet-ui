package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiPowerSwitchTest {

    private val light = SancaiTheme.light()

    private fun power(
        checked: Boolean = false,
        theme: SancaiTheme = light,
        rtl: Boolean = false,
    ): SancaiPowerSwitch {
        if (rtl) {
            val app = ApplicationProvider.getApplicationContext<Context>()
            app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        }
        return SancaiPowerSwitch(Snapshots.context(theme)).apply {
            id = View.generateViewId()
            if (rtl) layoutDirection = View.LAYOUT_DIRECTION_RTL
            setChecked(checked, animate = false)
            Snapshots.layout(this, WIDTH_DP)
        }
    }

    /** Pixel on the vertical centre line of the capsule, clear of its top and bottom shading. */
    private fun SancaiPowerSwitch.pixel(xFraction: Float): Int {
        val density = resources.displayMetrics.density
        val centreY = (2f * density + (height - 10f * density)) / 2f
        return Snapshots.render(this, WIDTH_DP).getPixel((width * xFraction).toInt(), centreY.toInt())
    }

    private fun SancaiPowerSwitch.count(): IntArray {
        val calls = IntArray(1)
        onCheckedChangeListener = SancaiPowerSwitch.OnCheckedChangeListener { _, _ -> calls[0]++ }
        return calls
    }

    private val SancaiPowerSwitch.midY: Float get() = height * 0.45f

    @Test
    fun `default height is an 80dp capsule plus its shadow gutter`() {
        val view = power()
        assertThat(view.height / view.resources.displayMetrics.density).isWithin(0.5f).of(92f)
    }

    @Test
    fun `bed is terracotta when off and cobalt when on`() {
        val view = power()
        // Off: the thumb rests at the start, the free half is at the end.
        assertThat(view.pixel(FREE_END)).isEqualTo(Color.rgb(206, 131, 83))
        assertThat(view.pixel(FREE_END)).isEqualTo(light.glazes.terracotta.bed)
        view.setChecked(true, animate = false)
        assertThat(view.pixel(FREE_START)).isEqualTo(light.glazes.cobalt.bed)
        // The thumb is neutral: where it rests, the bed colour does not show.
        assertThat(view.pixel(FREE_END)).isNotEqualTo(light.glazes.cobalt.bed)
    }

    @Test
    fun `custom glazes replace the default pair`() {
        val view = power(checked = true)
        view.onGlaze = light.glazes.celadon
        assertThat(view.pixel(FREE_START)).isEqualTo(light.glazes.celadon.bed)
    }

    @Test
    fun `tap toggles and notifies once`() {
        val view = power()
        val calls = view.count()
        Touch.tap(view, view.width * 0.8f, view.midY)
        assertThat(view.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(1)
        Touch.tap(view, view.width * 0.8f, view.midY)
        assertThat(view.isChecked).isFalse()
        assertThat(calls[0]).isEqualTo(2)
    }

    @Test
    fun `drag past the middle turns on and short of it springs back`() {
        val view = power()
        val calls = view.count()
        val travel = view.width * 0.42f
        Touch.drag(view, view.width * 0.2f, view.midY, view.width * 0.2f + travel * 0.3f, view.midY)
        assertThat(view.isChecked).isFalse()
        assertThat(calls[0]).isEqualTo(0)
        Touch.drag(view, view.width * 0.2f, view.midY, view.width * 0.2f + travel * 0.8f, view.midY)
        assertThat(view.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(1)
        // Dragging further towards the on side changes nothing.
        Touch.drag(view, view.width * 0.5f, view.midY, view.width * 0.9f, view.midY)
        assertThat(view.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(1)
    }

    @Test
    fun `bed follows the finger and blends the two glazes mid drag`() {
        val view = power()
        // Travel is 44 % of the track minus the inset; half of it puts the thumb mid-way.
        val density = view.resources.displayMetrics.density
        val travel = (WIDTH_DP - 5f) * density * 0.44f - 5.6f * density
        Touch.drag(view, view.width * 0.2f, view.midY, view.width * 0.2f + travel * 0.5f, view.midY, release = false)
        val expected = SancaiColors.blend(light.glazes.terracotta.bed, light.glazes.cobalt.bed, 0.5f)
        val actual = view.pixel(0.97f)
        assertThat(Color.red(actual).toDouble()).isWithin(3.0).of(Color.red(expected).toDouble())
        assertThat(Color.blue(actual).toDouble()).isWithin(3.0).of(Color.blue(expected).toDouble())
        assertThat(view.isChecked).isFalse()
    }

    /** Whether dark label pixels sit on the bed between two fractions of the width. */
    private fun SancaiPowerSwitch.hasLabelBetween(from: Float, to: Float): Boolean {
        val density = resources.displayMetrics.density
        val bitmap = Snapshots.render(this, WIDTH_DP)
        val centreY = ((2f * density + (height - 10f * density)) / 2f).toInt()
        val bed = bitmap.getPixel((width * 0.975f).toInt(), centreY)
        val reach = (5 * density).toInt()
        for (y in centreY - reach..centreY + reach) {
            for (x in (width * from).toInt()..(width * to).toInt()) {
                if (bitmap.getPixel(x, y).brightness() < bed.brightness() - 150) return true
            }
        }
        return false
    }

    private fun Int.brightness(): Int = Color.red(this) + Color.green(this) + Color.blue(this)

    @Test
    fun `action label changes sides when the thumb crosses the middle, before release`() {
        val view = power()
        assertThat(view.hasLabelBetween(0.70f, 0.86f)).isTrue()
        val density = view.resources.displayMetrics.density
        val travel = (WIDTH_DP - 5f) * density * 0.44f - 5.6f * density
        Touch.drag(view, view.width * 0.2f, view.midY, view.width * 0.2f + travel * 0.8f, view.midY, release = false)
        assertThat(view.isChecked).isFalse()
        assertThat(view.hasLabelBetween(0.14f, 0.26f)).isTrue()
    }

    @Test
    fun `vertical drag aborts and leaves the state alone`() {
        val view = power()
        val calls = view.count()
        Touch.drag(view, view.width * 0.8f, 10f, view.width * 0.8f + 4f, view.height * 3f)
        assertThat(view.isChecked).isFalse()
        assertThat(calls[0]).isEqualTo(0)
    }

    @Test
    fun `setting from code does not notify`() {
        val view = power()
        val calls = view.count()
        view.setChecked(true, animate = false)
        view.setChecked(false)
        view.toggle()
        assertThat(view.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(0)
    }

    @Test
    fun `listener can roll the change back`() {
        val view = power()
        var calls = 0
        view.onCheckedChangeListener = SancaiPowerSwitch.OnCheckedChangeListener { v, checked ->
            calls++
            (v as SancaiPowerSwitch).setChecked(!checked, animate = false)
        }
        Touch.tap(view, view.width * 0.8f, view.midY)
        assertThat(view.isChecked).isFalse()
        assertThat(calls).isEqualTo(1)
        assertThat(view.pixel(FREE_END)).isEqualTo(light.glazes.terracotta.bed)
    }

    @Test
    fun `keyboard toggles with space and enter and moves with arrows`() {
        val view = power()
        val calls = view.count()
        fun press(code: Int) = view.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
        assertThat(press(KeyEvent.KEYCODE_SPACE)).isTrue()
        assertThat(view.isChecked).isTrue()
        assertThat(press(KeyEvent.KEYCODE_ENTER)).isTrue()
        assertThat(view.isChecked).isFalse()
        assertThat(press(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(view.isChecked).isTrue()
        // Already on: the key is not consumed, so focus can move on.
        assertThat(press(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(press(KeyEvent.KEYCODE_DPAD_LEFT)).isTrue()
        assertThat(view.isChecked).isFalse()
        assertThat(press(KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(calls[0]).isEqualTo(4)
    }

    @Test
    fun `disabled switch ignores touch and keys and pales`() {
        val view = power()
        val calls = view.count()
        val vivid = view.pixel(FREE_END)
        view.isEnabled = false
        Touch.tap(view, view.width * 0.8f, view.midY)
        Touch.drag(view, view.width * 0.2f, view.midY, view.width * 0.9f, view.midY)
        view.onKeyDown(KeyEvent.KEYCODE_SPACE, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
        assertThat(view.isChecked).isFalse()
        assertThat(calls[0]).isEqualTo(0)
        assertThat(view.pixel(FREE_END)).isNotEqualTo(vivid)
    }

    @Test
    fun `right to left layouts mirror the thumb, the drag and the arrows`() {
        val view = power(rtl = true)
        val calls = view.count()
        // Off in RTL: the thumb rests on the right, the free half is on the left.
        assertThat(view.pixel(FREE_START)).isEqualTo(light.glazes.terracotta.bed)
        assertThat(view.pixel(FREE_END)).isNotEqualTo(light.glazes.terracotta.bed)
        Touch.drag(view, view.width * 0.8f, view.midY, view.width * 0.3f, view.midY)
        assertThat(view.isChecked).isTrue()
        view.setChecked(true, animate = false)
        assertThat(view.pixel(FREE_END)).isEqualTo(light.glazes.cobalt.bed)
        fun press(code: Int) = view.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
        assertThat(press(KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(press(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(view.isChecked).isFalse()
        assertThat(calls[0]).isEqualTo(2)
    }

    @Test
    fun `state survives save and restore without notifying`() {
        val view = power(checked = true)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)
        val restored = SancaiPowerSwitch(Snapshots.context()).apply { id = view.id }
        val calls = restored.count()
        restored.restoreHierarchyState(saved)
        assertThat(restored.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(0)
    }

    @Test
    fun `accessibility sees a switch with a checked state and can click it`() {
        val view = power()
        val calls = view.count()
        assertThat(view.accessibilityClassName.toString()).isEqualTo("android.widget.Switch")
        assertThat(ViewCompat.getStateDescription(view).toString()).isEqualTo("Off")
        val node = AccessibilityNodeInfo.obtain()
        view.onInitializeAccessibilityNodeInfo(node)
        assertThat(node.isCheckable).isTrue()
        assertThat(node.isChecked).isFalse()
        assertThat(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)).isTrue()
        assertThat(view.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(1)
        assertThat(ViewCompat.getStateDescription(view).toString()).isEqualTo("On")
        view.onLabel = "Protected"
        assertThat(ViewCompat.getStateDescription(view).toString()).isEqualTo("Protected")
    }

    @Test
    fun `tap glides and the thumb arrives`() {
        val view = power()
        Snapshots.attach(view, WIDTH_DP)
        Touch.tap(view, view.width * 0.8f, view.midY)
        assertThat(view.isChecked).isTrue()
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        assertThat(view.pixel(FREE_START)).isEqualTo(light.glazes.cobalt.bed)
    }

    @Test
    fun `reduced motion jumps instead of gliding`() {
        val view = power(theme = light.copy(reduceMotion = true))
        Snapshots.attach(view, WIDTH_DP)
        Touch.tap(view, view.width * 0.8f, view.midY)
        assertThat(view.pixel(FREE_START)).isEqualTo(light.glazes.cobalt.bed)
    }

    @Test
    fun `detaching mid glide rests on the committed side`() {
        val view = power()
        val activity = Snapshots.attach(view, WIDTH_DP)
        Touch.tap(view, view.width * 0.8f, view.midY)
        ShadowLooper.idleMainLooper(60, TimeUnit.MILLISECONDS)
        (view.parent as android.view.ViewGroup).removeView(view)
        assertThat(activity).isNotNull()
        Snapshots.layout(view, WIDTH_DP)
        assertThat(view.isChecked).isTrue()
        assertThat(view.pixel(FREE_START)).isEqualTo(light.glazes.cobalt.bed)
        assertThat(view.pixel(FREE_END)).isNotEqualTo(light.glazes.cobalt.bed)
    }

    @Test
    fun `disabling under the finger lets go and keeps the state`() {
        val view = power()
        val calls = view.count()
        Touch.drag(view, view.width * 0.2f, view.midY, view.width * 0.9f, view.midY, release = false)
        view.isEnabled = false
        Touch.dispatch(view, android.view.MotionEvent.ACTION_UP, view.width * 0.9f, view.midY)
        assertThat(view.isChecked).isFalse()
        assertThat(calls[0]).isEqualTo(0)
        view.isEnabled = true
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        assertThat(view.pixel(FREE_END)).isEqualTo(light.glazes.terracotta.bed)
    }

    @Test
    fun `wrap content is wide enough for the thumb and the action label`() {
        val view = SancaiPowerSwitch(Snapshots.context())
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val density = view.resources.displayMetrics.density
        assertThat(view.measuredWidth / density).isAtLeast(214f)
        assertThat(view.measuredHeight / density).isWithin(0.5f).of(92f)
    }

    @Test
    fun `empty labels leave a bare bed and a lone glyph`() {
        val view = power()
        view.offLabel = ""
        view.turnOnLabel = ""
        Snapshots.layout(view, WIDTH_DP)
        assertThat(view.hasLabelBetween(0.62f, 0.94f)).isFalse()
        assertThat(view.pixel(0.78f)).isEqualTo(light.glazes.terracotta.bed)
        Touch.tap(view, view.width * 0.8f, view.midY)
        assertThat(view.isChecked).isTrue()
    }

    @Test
    fun `long labels at large text stay inside their half`() {
        val view = power(checked = true, theme = light.copy(textScale = 1.13f))
        view.onLabel = "Protection is active on this device"
        view.turnOffLabel = "Stop protecting this device right now"
        Snapshots.layout(view, WIDTH_DP)
        // The action label keeps clear of the rim and of the thumb's edge.
        assertThat(view.pixel(FREE_START)).isEqualTo(light.glazes.cobalt.bed)
        assertThat(view.hasLabelBetween(0.025f, 0.044f)).isFalse()
        assertThat(view.hasLabelBetween(0.10f, 0.40f)).isTrue()
        assertThat(view.measuredHeight / view.resources.displayMetrics.density).isWithin(0.5f).of(92f)
    }

    @Test
    fun `large text asks for a wider switch`() {
        fun wrapWidth(theme: SancaiTheme): Int {
            val view = SancaiPowerSwitch(Snapshots.context(theme))
            val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.turnOnLabel = "Turn the protection on"
            view.measure(spec, spec)
            return view.measuredWidth
        }
        assertThat(wrapWidth(light.copy(textScale = 1.13f))).isGreaterThan(wrapWidth(light))
    }

    @Test
    fun `right to left puts the action label on the left while off`() {
        val view = power(rtl = true)
        assertThat(view.hasLabelBetween(0.14f, 0.30f)).isTrue()
        assertThat(view.hasLabelBetween(0.02f, 0.06f)).isFalse()
        // A tap toggles wherever it lands, as in left-to-right.
        Touch.tap(view, view.width * 0.2f, view.midY)
        assertThat(view.isChecked).isTrue()
    }

    @Test
    fun `a held key toggles once`() {
        val view = power()
        val calls = view.count()
        val code = KeyEvent.KEYCODE_SPACE
        assertThat(view.onKeyDown(code, KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, code, 0))).isTrue()
        assertThat(view.onKeyDown(code, KeyEvent(0L, 50L, KeyEvent.ACTION_DOWN, code, 1))).isTrue()
        assertThat(view.onKeyDown(code, KeyEvent(0L, 100L, KeyEvent.ACTION_DOWN, code, 2))).isTrue()
        assertThat(view.isChecked).isTrue()
        assertThat(calls[0]).isEqualTo(1)
    }

    @Test
    fun `snapshot of the power switch in light and dark`() {
        for (theme in listOf("light" to SancaiTheme.light(), "dark" to SancaiTheme.dark())) {
            val context = Snapshots.context(theme.second)
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                setBackgroundColor(theme.second.colors.canvas)
            }
            val gap = (10 * context.resources.displayMetrics.density).toInt()
            fun row(configure: SancaiPowerSwitch.() -> Unit) {
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                params.bottomMargin = gap
                column.addView(SancaiPowerSwitch(context).apply(configure), params)
            }
            row { setChecked(false, animate = false) }
            row { setChecked(true, animate = false) }
            row {
                setChecked(true, animate = false)
                onLabel = "已开启"
                turnOffLabel = "关闭保护"
            }
            row {
                onLabel = "Protection is active"
                turnOffLabel = "Stop protecting this device"
                onGlaze = theme.second.glazes.celadon
                setChecked(true, animate = false)
            }
            row {
                setChecked(true, animate = false)
                isEnabled = false
            }
            val bitmap = Snapshots.capture(column, "power_${theme.first}", 360)
            assertThat(bitmap.height).isGreaterThan(0)
        }
    }

    private companion object {
        const val WIDTH_DP = 300

        /** Fractions of the width that lie on the bed at either end, clear of labels. */
        const val FREE_START = 0.035f
        const val FREE_END = 0.965f
    }
}
