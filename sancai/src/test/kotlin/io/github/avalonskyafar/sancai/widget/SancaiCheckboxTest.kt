package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiCheckboxTest {

    private val light = SancaiTheme.light()

    private fun checkbox(
        text: String = "",
        theme: SancaiTheme = light,
        checked: Boolean = false,
        widthDp: Int = if (text.isEmpty()) BARE_DP else WIDTH_DP,
    ): SancaiCheckbox = SancaiCheckbox(Snapshots.context(theme)).apply {
        id = View.generateViewId()
        this.text = text
        setChecked(checked, animate = false)
        Snapshots.layout(this, widthDp)
    }

    /** Records every callback of a checkbox. */
    private class Recorder : SancaiCheckbox.OnCheckedChangeListener {
        val calls = mutableListOf<Boolean>()
        override fun onCheckedChanged(view: View, checked: Boolean) {
            calls += checked
        }
    }

    private val View.density: Float get() = resources.displayMetrics.density

    /**
     * Pixel at ([dxDp], [dyDp]) from the top-left corner of the box. [boxLeftDp] is where the
     * box starts; by default it is centred, as in a bare checkbox.
     */
    private fun SancaiCheckbox.boxPixel(dxDp: Float, dyDp: Float, widthDp: Int, boxLeftDp: Float? = null): Int {
        val bitmap = Snapshots.render(this, widthDp)
        val left = if (boxLeftDp == null) ((width - BOX_DP * density) / 2f).roundToInt() else (boxLeftDp * density).roundToInt()
        val top = ((height - BOX_DP * density) / 2f).roundToInt()
        return bitmap.getPixel(left + (dxDp * density).roundToInt(), top + (dyDp * density).roundToInt())
    }

    /** A spot of the bed that the check mark never crosses. */
    private fun SancaiCheckbox.bedPixel(widthDp: Int = BARE_DP, boxLeftDp: Float? = null): Int =
        boxPixel(4f, 13f, widthDp, boxLeftDp)

    /** The elbow of the check mark. */
    private fun SancaiCheckbox.checkPixel(widthDp: Int = BARE_DP): Int = boxPixel(11.1f, 17.1f, widthDp)

    private fun SancaiCheckbox.tapCentre() = Touch.tap(this, width / 2f, height / 2f)

    @Test
    fun `a bare box is a 44dp touch target and a label keeps that height`() {
        val bare = SancaiCheckbox(Snapshots.context())
        bare.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        assertThat(bare.measuredWidth / bare.density).isWithin(0.5f).of(44f)
        assertThat(bare.measuredHeight / bare.density).isWithin(0.5f).of(44f)

        val labelled = checkbox("Remember me")
        assertThat(labelled.height / labelled.density).isWithin(0.5f).of(44f)
    }

    @Test
    fun `wrap content hugs the label and a long label wraps and grows the view`() {
        val short = SancaiCheckbox(Snapshots.context()).apply { text = "Mute" }
        short.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        assertThat(short.measuredWidth / short.density).isGreaterThan(37.5f + 20f)
        assertThat(short.measuredWidth / short.density).isLessThan(120f)

        val long = checkbox("Keep the lantern lit until every visitor has left the grounds for the night", widthDp = 180)
        assertThat(long.height / long.density).isGreaterThan(60f)
    }

    @Test
    fun `tap checks then unchecks and tells the listener each time`() {
        val view = checkbox("Remember me")
        val recorder = Recorder()
        view.onCheckedChangeListener = recorder
        // The label is part of the target, not only the box.
        Touch.tap(view, view.width * 0.8f, view.height / 2f)
        assertThat(view.isChecked).isTrue()
        view.tapCentre()
        assertThat(view.isChecked).isFalse()
        assertThat(recorder.calls).containsExactly(true, false).inOrder()
    }

    @Test
    fun `setting from code never notifies`() {
        val view = checkbox()
        val recorder = Recorder()
        view.onCheckedChangeListener = recorder
        view.isChecked = true
        view.setChecked(false, animate = false)
        view.toggle()
        assertThat(view.isChecked).isTrue()
        assertThat(recorder.calls).isEmpty()
    }

    @Test
    fun `listener can roll the change back`() {
        val view = checkbox()
        var calls = 0
        view.onCheckedChangeListener = SancaiCheckbox.OnCheckedChangeListener { box, _ ->
            calls++
            (box as SancaiCheckbox).setChecked(false, animate = false)
        }
        view.tapCentre()
        assertThat(view.isChecked).isFalse()
        assertThat(calls).isEqualTo(1)
        assertThat(view.bedPixel()).isNotEqualTo(light.bedColor(light.glazes.cobalt))
    }

    @Test
    fun `a finger that slides off or a cancelled gesture changes nothing`() {
        val view = checkbox()
        val recorder = Recorder()
        view.onCheckedChangeListener = recorder
        Touch.drag(view, view.width / 2f, view.height / 2f, view.width / 2f, view.height * 4f)
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, 10f, 10f)
        Touch.dispatch(view, MotionEvent.ACTION_CANCEL, 10f, 10f)
        assertThat(view.isChecked).isFalse()
        assertThat(recorder.calls).isEmpty()
    }

    @Test
    fun `space and enter flip the box and other keys pass through`() {
        val view = checkbox()
        val recorder = Recorder()
        view.onCheckedChangeListener = recorder
        fun press(code: Int) = view.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
        assertThat(press(KeyEvent.KEYCODE_SPACE)).isTrue()
        assertThat(view.isChecked).isTrue()
        assertThat(press(KeyEvent.KEYCODE_ENTER)).isTrue()
        assertThat(view.isChecked).isFalse()
        assertThat(press(KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(press(KeyEvent.KEYCODE_A)).isFalse()
        assertThat(recorder.calls).containsExactly(true, false).inOrder()
    }

    @Test
    fun `disabled box ignores input and pales`() {
        val view = checkbox(checked = true)
        val enabledBed = view.bedPixel()
        val recorder = Recorder()
        view.onCheckedChangeListener = recorder
        view.isEnabled = false
        view.tapCentre()
        view.onKeyDown(KeyEvent.KEYCODE_SPACE, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
        assertThat(view.performClick()).isFalse()
        assertThat(view.isChecked).isTrue()
        assertThat(recorder.calls).isEmpty()
        assertThat(view.bedPixel()).isNotEqualTo(enabledBed)
    }

    @Test
    fun `checked box is the exact glaze with the check in the on-bed colour`() {
        val view = checkbox(checked = true)
        assertThat(view.bedPixel()).isEqualTo(light.bedColor(light.glazes.cobalt))
        assertThat(view.checkPixel()).isEqualTo(light.labelOnBed(light.glazes.cobalt))

        view.glaze = light.glazes.celadon
        assertThat(view.bedPixel()).isEqualTo(light.bedColor(light.glazes.celadon))

        view.setChecked(false, animate = false)
        assertThat(view.bedPixel()).isNotEqualTo(light.bedColor(light.glazes.celadon))
        assertThat(view.checkPixel()).isNotEqualTo(light.labelOnBed(light.glazes.celadon))
    }

    @Test
    fun `dark theme pours the night glaze`() {
        val dark = SancaiTheme.dark()
        val view = checkbox(theme = dark, checked = true)
        assertThat(view.bedPixel()).isEqualTo(dark.bedColor(dark.glazes.cobalt))
        assertThat(view.checkPixel()).isEqualTo(dark.labelOnBed(dark.glazes.cobalt))
    }

    @Test
    fun `pressing sinks the check by one dp`() {
        val view = checkbox(checked = true)
        val onBed = light.labelOnBed(light.glazes.cobalt)
        // Just below the elbow: bed at rest, check while the finger is down.
        assertThat(view.boxPixel(11.1f, 18.5f, BARE_DP)).isNotEqualTo(onBed)
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.width / 2f, view.height / 2f)
        assertThat(view.boxPixel(11.1f, 18.5f, BARE_DP)).isEqualTo(onBed)
        Touch.dispatch(view, MotionEvent.ACTION_CANCEL, view.width / 2f, view.height / 2f)
        assertThat(view.boxPixel(11.1f, 18.5f, BARE_DP)).isNotEqualTo(onBed)
        assertThat(view.isChecked).isTrue()
    }

    @Test
    fun `box sits at the start and mirrors in right-to-left`() {
        val bed = light.bedColor(light.glazes.cobalt)
        val ltr = checkbox("Remember me", checked = true)
        assertThat(ltr.bedPixel(WIDTH_DP, boxLeftDp = 1.5f)).isEqualTo(bed)

        val app = ApplicationProvider.getApplicationContext<Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val rtl = SancaiCheckbox(Snapshots.context()).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            text = "Remember me"
            setChecked(true, animate = false)
        }
        assertThat(rtl.bedPixel(WIDTH_DP, boxLeftDp = WIDTH_DP - 27.5f)).isEqualTo(bed)
        assertThat(rtl.bedPixel(WIDTH_DP, boxLeftDp = 1.5f)).isNotEqualTo(bed)
    }

    @Test
    fun `state survives save and restore without notifying`() {
        val view = checkbox(checked = true)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)

        val restored = checkbox().apply { id = view.id }
        val recorder = Recorder()
        restored.onCheckedChangeListener = recorder
        restored.restoreHierarchyState(saved)
        assertThat(restored.isChecked).isTrue()
        assertThat(recorder.calls).isEmpty()
        assertThat(restored.bedPixel()).isEqualTo(light.bedColor(light.glazes.cobalt))
    }

    @Test
    fun `accessibility sees a checkbox and its click action flips it`() {
        val view = checkbox("Remember me", checked = true)
        assertThat(view.accessibilityClassName.toString()).isEqualTo("android.widget.CheckBox")
        val node = view.createAccessibilityNodeInfo()
        assertThat(node).isNotNull()
        assertThat(node.isCheckable).isTrue()
        assertThat(node.isChecked).isTrue()
        assertThat(node.text.toString()).isEqualTo("Remember me")

        val recorder = Recorder()
        view.onCheckedChangeListener = recorder
        assertThat(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)).isTrue()
        assertThat(view.isChecked).isFalse()
        assertThat(view.createAccessibilityNodeInfo().isChecked).isFalse()
        assertThat(recorder.calls).containsExactly(false)
    }

    @Test
    fun `xml attributes set label, state and glaze`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.text, "Remember me")
            .addAttribute(R.attr.sancaiChecked, "true")
            .addAttribute(R.attr.sancaiGlaze, "celadon")
            .build()
        val view = SancaiCheckbox(Snapshots.context(), attrs)
        assertThat(view.text.toString()).isEqualTo("Remember me")
        assertThat(view.isChecked).isTrue()
        assertThat(view.glaze).isEqualTo(light.glazes.celadon)
        assertThat(view.bedPixel(WIDTH_DP, boxLeftDp = 1.5f)).isEqualTo(light.bedColor(light.glazes.celadon))
    }

    @Test
    fun `the glaze glides in on screen and jumps with reduced motion`() {
        val bed = light.bedColor(light.glazes.cobalt)
        val view = SancaiCheckbox(Snapshots.context())
        Snapshots.attach(view, ATTACHED_DP)
        view.tapCentre()
        assertThat(view.isChecked).isTrue()
        assertThat(view.bedPixel(ATTACHED_DP)).isNotEqualTo(bed)
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        assertThat(view.bedPixel(ATTACHED_DP)).isEqualTo(bed)
        assertThat(view.checkPixel(ATTACHED_DP)).isEqualTo(light.labelOnBed(light.glazes.cobalt))

        val calm = SancaiCheckbox(Snapshots.context(light.copy(reduceMotion = true)))
        Snapshots.attach(calm, ATTACHED_DP)
        calm.tapCentre()
        assertThat(calm.bedPixel(ATTACHED_DP)).isEqualTo(bed)
    }

    /** Painted pixels (anything but the empty corner) in the columns from [fromDp] to [toDp]. */
    private fun SancaiCheckbox.paintedBetween(fromDp: Float, toDp: Float, widthDp: Int = WIDTH_DP): Int {
        val bitmap = Snapshots.render(this, widthDp)
        val empty = bitmap.getPixel(0, 0)
        var count = 0
        for (x in (fromDp * density).roundToInt() until (toDp * density).roundToInt()) {
            for (y in 0 until bitmap.height) if (bitmap.getPixel(x, y) != empty) count++
        }
        return count
    }

    @Test
    fun `a disabled box does not swallow the keys it cannot act on`() {
        val view = checkbox(checked = true)
        view.isEnabled = false
        for (code in listOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER)) {
            assertThat(view.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))).isFalse()
        }
        assertThat(view.isChecked).isTrue()
    }

    @Test
    fun `a latin label in right-to-left starts next to the box`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val rtl = SancaiCheckbox(Snapshots.context()).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            text = "Mute"
        }
        // The box occupies the last 27.5 dp; the label ends 10 dp before it.
        assertThat(rtl.paintedBetween(210f, 262f)).isGreaterThan(0)
        assertThat(rtl.paintedBetween(0f, 150f)).isEqualTo(0)

        val ltr = checkbox("Mute")
        assertThat(ltr.paintedBetween(38f, 90f)).isGreaterThan(0)
        assertThat(ltr.paintedBetween(150f, 300f)).isEqualTo(0)
    }

    @Test
    fun `empty text is a bare box and clearing a label shrinks the view`() {
        val view = SancaiCheckbox(Snapshots.context()).apply { text = "Remember me" }
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        assertThat(view.measuredWidth / view.density).isGreaterThan(80f)
        view.text = ""
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        assertThat(view.measuredWidth / view.density).isWithin(0.5f).of(44f)
        assertThat(view.createAccessibilityNodeInfo().text).isNull()
        Snapshots.layout(view, BARE_DP)
        view.tapCentre()
        assertThat(view.isChecked).isTrue()
    }

    @Test
    fun `large text widens the label, keeps the box and still wraps inside the view`() {
        fun measured(theme: SancaiTheme): SancaiCheckbox = SancaiCheckbox(Snapshots.context(theme)).apply {
            text = "Remember this device"
            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        }
        val large = light.copy(textScale = 1.13f)
        val plain = measured(light)
        val scaled = measured(large)
        val lead = 37.5f * plain.density
        assertThat((scaled.measuredWidth - lead) / (plain.measuredWidth - lead)).isWithin(0.04f).of(1.13f)

        val long = "Keep the lantern lit until every visitor has left the grounds for the night"
        val wrapped = checkbox(long, theme = large, checked = true, widthDp = 160)
        assertThat(wrapped.height).isGreaterThan(checkbox(long, checked = true, widthDp = 160).height)
        // The box keeps its size and place; nothing is painted past the end of the view.
        val bitmap = Snapshots.render(wrapped, 160)
        assertThat(bitmap.width).isEqualTo((160 * wrapped.density).roundToInt())
        val boxTop = (0 until bitmap.height).first { bitmap.getPixel((14.5f * wrapped.density).roundToInt(), it) == large.bedColor(large.glazes.cobalt) }
        assertThat(boxTop / wrapped.density).isGreaterThan(1f)
        assertThat(wrapped.paintedBetween(159f, 160f, widthDp = 160)).isEqualTo(0)
    }

    @Test
    fun `leaving the window ends the glide at its target`() {
        val view = SancaiCheckbox(Snapshots.context())
        val activity = Snapshots.attach(view, ATTACHED_DP)
        view.tapCentre()
        assertThat(view.bedPixel(ATTACHED_DP)).isNotEqualTo(light.bedColor(light.glazes.cobalt))
        (view.parent as android.view.ViewGroup).removeView(view)
        assertThat(view.isChecked).isTrue()
        assertThat(view.bedPixel(ATTACHED_DP)).isEqualTo(light.bedColor(light.glazes.cobalt))
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        assertThat(view.bedPixel(ATTACHED_DP)).isEqualTo(light.bedColor(light.glazes.cobalt))
        assertThat(activity).isNotNull()
    }

    @Test
    fun `snapshot of the checkbox in light and dark`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        for ((name, theme) in listOf("light" to SancaiTheme.light(), "dark" to SancaiTheme.dark())) {
            val context = Snapshots.context(theme)
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            fun row(text: String, checked: Boolean, enabled: Boolean = true, edit: SancaiCheckbox.() -> Unit = {}) {
                column.addView(
                    SancaiCheckbox(context).apply {
                        this.text = text
                        setChecked(checked, animate = false)
                        isEnabled = enabled
                        edit()
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
                )
            }
            row("Remember this device", checked = false)
            row("Remember this device", checked = true)
            row("严格模式 Strict mode", checked = true) { glaze = theme.glazes.celadon }
            row("Warn before deleting", checked = true) { glaze = theme.glazes.terracotta }
            row("Keep the lantern lit until every visitor has left the grounds for the night", checked = true) {
                glaze = theme.glazes.ochre
            }
            row("Unavailable", checked = false, enabled = false)
            row("Unavailable", checked = true, enabled = false)
            row("", checked = true)
            row("Right to left", checked = true) { layoutDirection = View.LAYOUT_DIRECTION_RTL }
            row("تذكر هذا الجهاز", checked = false) { layoutDirection = View.LAYOUT_DIRECTION_RTL }
            val bitmap = Snapshots.capture(column, "checkbox_$name", 300)
            assertThat(bitmap.height).isGreaterThan(0)
        }
    }

    private companion object {
        const val BOX_DP = 26f
        const val BARE_DP = 44
        const val WIDTH_DP = 300
        const val ATTACHED_DP = 200
    }
}
