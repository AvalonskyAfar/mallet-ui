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
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiRadioTest {

    private fun radio(
        label: String? = "Every day",
        checked: Boolean = false,
        theme: SancaiTheme = SancaiTheme.light(),
    ): SancaiRadioButton = SancaiRadioButton(Snapshots.context(theme)).apply {
        id = View.generateViewId()
        text = label
        isChecked = checked
        Snapshots.layout(this, WIDTH_DP)
    }

    private fun group(vararg labels: String, checked: Int = -1): SancaiRadioGroup =
        SancaiRadioGroup(Snapshots.context()).apply {
            id = GROUP_ID
            labels.forEachIndexed { index, label ->
                addView(
                    SancaiRadioButton(context).apply {
                        id = FIRST_ID + index
                        text = label
                        isChecked = index == checked
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
                )
            }
            Snapshots.layout(this, WIDTH_DP)
        }

    private fun SancaiRadioGroup.radioAt(index: Int): SancaiRadioButton = getChildAt(index) as SancaiRadioButton

    private fun settle() {
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
    }

    private val View.density: Float get() = resources.displayMetrics.density

    /** A pixel of the bed beside the bead: [side] is -1 for left of the centre, 1 for right. */
    private fun SancaiRadioButton.bedPixel(centreXDp: Float = 14.5f, side: Int = 1): Int =
        Snapshots.render(this, WIDTH_DP)
            .getPixel(((centreXDp + side * 9.5f) * density).toInt(), height / 2)

    private fun key(view: View, code: Int): Boolean = view.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))

    @Test
    fun `a radio is at least 44dp tall and a bare radio is a 44dp square`() {
        val labelled = radio()
        assertThat(labelled.height / labelled.density).isWithin(0.5f).of(44f)
        val bare = SancaiRadioButton(Snapshots.context())
        bare.measure(0, 0)
        assertThat(bare.measuredWidth / bare.density).isWithin(0.5f).of(44f)
        assertThat(bare.measuredHeight / bare.density).isWithin(0.5f).of(44f)
    }

    @Test
    fun `a long label wraps and makes the radio taller`() {
        val view = radio("Remind me every single day of the week, including weekends and public holidays, at nine")
        assertThat(view.height / view.density).isGreaterThan(60f)
        assertThat(view.width / view.density).isWithin(0.5f).of(WIDTH_DP.toFloat())
    }

    @Test
    fun `tap checks and a second tap does not uncheck`() {
        val view = radio()
        val heard = mutableListOf<Boolean>()
        view.setOnCheckedChangeListener { _, checked -> heard += checked }
        Touch.tap(view, view.width * 0.7f)
        assertThat(view.isChecked).isTrue()
        Touch.tap(view, view.width * 0.7f)
        assertThat(view.isChecked).isTrue()
        assertThat(heard).containsExactly(true)
    }

    @Test
    fun `a finger that leaves the radio does not check it`() {
        val view = radio()
        Touch.drag(view, 40f, view.height / 2f, 40f, view.height * 4f)
        assertThat(view.isChecked).isFalse()
    }

    @Test
    fun `setting the state from code does not notify`() {
        val view = radio()
        var calls = 0
        view.setOnCheckedChangeListener { _, _ -> calls++ }
        view.setChecked(true, animate = true)
        view.isChecked = false
        view.toggle()
        assertThat(view.isChecked).isTrue()
        view.toggle()
        assertThat(view.isChecked).isTrue()
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `the listener can roll the change back`() {
        val view = radio()
        var calls = 0
        view.setOnCheckedChangeListener { radio, _ ->
            calls++
            (radio as SancaiRadioButton).setChecked(false, animate = false)
        }
        Touch.tap(view, 40f)
        assertThat(view.isChecked).isFalse()
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `space and enter check, and are not consumed once checked`() {
        val view = radio()
        var calls = 0
        view.setOnCheckedChangeListener { _, _ -> calls++ }
        assertThat(key(view, KeyEvent.KEYCODE_SPACE)).isTrue()
        assertThat(view.isChecked).isTrue()
        assertThat(key(view, KeyEvent.KEYCODE_SPACE)).isFalse()
        assertThat(key(view, KeyEvent.KEYCODE_ENTER)).isFalse()
        assertThat(key(view, KeyEvent.KEYCODE_A)).isFalse()
        assertThat(calls).isEqualTo(1)
        assertThat(key(radio(), KeyEvent.KEYCODE_ENTER)).isTrue()
    }

    @Test
    fun `a disabled radio ignores touch and keys and pales`() {
        val view = radio(checked = true)
        val enabledBed = view.bedPixel()
        view.isEnabled = false
        assertThat(view.bedPixel()).isNotEqualTo(enabledBed)
        val off = radio().apply { isEnabled = false }
        Touch.tap(off, 40f)
        assertThat(key(off, KeyEvent.KEYCODE_SPACE)).isFalse()
        off.performClick()
        assertThat(off.isChecked).isFalse()
    }

    @Test
    fun `the bed is a neutral well when unchecked and the exact glaze when checked`() {
        val theme = SancaiTheme.light()
        val cobalt = theme.bedColor(theme.glazes.cobalt)
        val view = radio()
        assertThat(view.bedPixel()).isNotEqualTo(cobalt)
        view.isChecked = true
        assertThat(view.bedPixel()).isEqualTo(cobalt)
        assertThat(view.bedPixel(side = -1)).isEqualTo(cobalt)
        view.glaze = theme.glazes.celadon
        assertThat(view.bedPixel()).isEqualTo(theme.bedColor(theme.glazes.celadon))
    }

    @Test
    fun `a checked radio carries a neutral bead in its centre`() {
        val view = radio(checked = true)
        val centre = Snapshots.render(view, WIDTH_DP).getPixel((14.5f * view.density).toInt(), view.height / 2)
        val channels = listOf(Color.red(centre), Color.green(centre), Color.blue(centre))
        assertThat(centre).isNotEqualTo(view.bedPixel())
        assertThat(channels.max() - channels.min()).isLessThan(24)
        assertThat(channels.min()).isGreaterThan(200)
    }

    @Test
    fun `the glaze and the bead glide in after a tap`() {
        val view = radio()
        Snapshots.attach(view, WIDTH_DP)
        val theme = SancaiTheme.light()
        Touch.tap(view, 40f)
        assertThat(view.bedPixel()).isNotEqualTo(theme.bedColor(theme.glazes.cobalt))
        settle()
        assertThat(view.bedPixel()).isEqualTo(theme.bedColor(theme.glazes.cobalt))
    }

    @Test
    fun `right to left layouts put the bed at the right`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val theme = SancaiTheme.light()
        val view = SancaiRadioButton(Snapshots.context()).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            text = "كل يوم"
            isChecked = true
            Snapshots.layout(this, WIDTH_DP)
        }
        assertThat(view.bedPixel(centreXDp = WIDTH_DP - 14.5f, side = -1)).isEqualTo(theme.bedColor(theme.glazes.cobalt))
        assertThat(view.bedPixel()).isNotEqualTo(theme.bedColor(theme.glazes.cobalt))
    }

    @Test
    fun `checked state survives save and restore without notifying`() {
        val view = radio(checked = true)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)
        var calls = 0
        val restored = SancaiRadioButton(Snapshots.context()).apply {
            id = view.id
            setOnCheckedChangeListener { _, _ -> calls++ }
        }
        restored.restoreHierarchyState(saved)
        assertThat(restored.isChecked).isTrue()
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `accessibility sees a radio button with its label and checked state`() {
        val view = radio()
        assertThat(view.accessibilityClassName.toString()).isEqualTo("android.widget.RadioButton")
        val node = view.createAccessibilityNodeInfo()
        assertThat(node).isNotNull()
        assertThat(node!!.isCheckable).isTrue()
        assertThat(node.isChecked).isFalse()
        assertThat(node.text.toString()).isEqualTo("Every day")

        var calls = 0
        view.setOnCheckedChangeListener { _, _ -> calls++ }
        assertThat(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)).isTrue()
        assertThat(view.isChecked).isTrue()
        assertThat(calls).isEqualTo(1)
        val checked = view.createAccessibilityNodeInfo()
        assertThat(checked!!.isChecked).isTrue()
        assertThat(checked.actionList).doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
    }

    // ---------------------------------------------------------------------------------------
    // Group
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a group keeps one radio checked and reports the user's choice`() {
        val view = group("Daily", "Weekly", "Never", checked = 0)
        val heard = mutableListOf<Int>()
        val siblings = mutableListOf<Boolean>()
        view.setOnCheckedChangeListener { _, id -> heard += id }
        view.radioAt(0).setOnCheckedChangeListener { _, c -> siblings += c }
        assertThat(view.checkedId).isEqualTo(FIRST_ID)

        val second = view.radioAt(1)
        Touch.tap(second, 40f)
        assertThat(view.checkedId).isEqualTo(FIRST_ID + 1)
        assertThat((0..2).map { view.radioAt(it).isChecked }).containsExactly(false, true, false).inOrder()
        Touch.tap(second, 40f)
        assertThat(key(view.radioAt(2), KeyEvent.KEYCODE_SPACE)).isTrue()
        assertThat((0..2).map { view.radioAt(it).isChecked }).containsExactly(false, false, true).inOrder()
        assertThat(heard).containsExactly(FIRST_ID + 1, FIRST_ID + 2).inOrder()
        assertThat(siblings).containsExactly(false)
    }

    @Test
    fun `checking from code moves the choice without notifying anyone`() {
        val view = group("Daily", "Weekly", "Never", checked = 0)
        var calls = 0
        view.setOnCheckedChangeListener { _, _ -> calls++ }
        for (i in 0..2) {
            view.radioAt(i).setOnCheckedChangeListener { _, _ -> calls++ }
        }
        view.check(FIRST_ID + 2)
        assertThat(view.checkedId).isEqualTo(FIRST_ID + 2)
        assertThat((0..2).map { view.radioAt(it).isChecked }).containsExactly(false, false, true).inOrder()
        view.radioAt(1).isChecked = true
        assertThat(view.checkedId).isEqualTo(FIRST_ID + 1)
        assertThat(view.radioAt(2).isChecked).isFalse()
        view.clearCheck()
        assertThat(view.checkedId).isEqualTo(View.NO_ID)
        assertThat((0..2).none { view.radioAt(it).isChecked }).isTrue()
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `the group listener can roll the choice back`() {
        val view = group("Daily", "Weekly", checked = 0)
        var calls = 0
        view.setOnCheckedChangeListener { group, _ ->
            calls++
            group.check(FIRST_ID, animate = false)
        }
        Touch.tap(view.radioAt(1), 40f)
        assertThat(view.checkedId).isEqualTo(FIRST_ID)
        assertThat(view.radioAt(0).isChecked).isTrue()
        assertThat(view.radioAt(1).isChecked).isFalse()
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `a radio that refuses the change leaves the group as it was`() {
        val view = group("Daily", "Weekly", checked = 0)
        var groupCalls = 0
        view.setOnCheckedChangeListener { _, _ -> groupCalls++ }
        view.radioAt(1).setOnCheckedChangeListener { radio, _ ->
            (radio as SancaiRadioButton).setChecked(false, animate = false)
        }
        Touch.tap(view.radioAt(1), 40f)
        assertThat(view.checkedId).isEqualTo(FIRST_ID)
        assertThat(view.radioAt(0).isChecked).isTrue()
        assertThat(groupCalls).isEqualTo(0)
    }

    @Test
    fun `a group may start empty, adopts checked children and forgets removed ones`() {
        val view = group("Daily", "Weekly")
        assertThat(view.checkedId).isEqualTo(View.NO_ID)
        val late = SancaiRadioButton(view.context).apply {
            text = "Never"
            isChecked = true
        }
        view.radioAt(0).isChecked = true
        view.addView(late)
        assertThat(late.id).isNotEqualTo(View.NO_ID)
        assertThat(view.checkedId).isEqualTo(late.id)
        assertThat(view.radioAt(0).isChecked).isFalse()
        view.removeView(late)
        assertThat(view.checkedId).isEqualTo(View.NO_ID)
        late.isChecked = false
        late.isChecked = true
        assertThat(view.checkedId).isEqualTo(View.NO_ID)
    }

    @Test
    fun `an id checked before its radio exists is applied when the radio arrives`() {
        val view = SancaiRadioGroup(Snapshots.context())
        view.check(FIRST_ID + 1)
        view.addView(SancaiRadioButton(view.context).apply { id = FIRST_ID })
        view.addView(SancaiRadioButton(view.context).apply { id = FIRST_ID + 1 })
        assertThat(view.radioAt(0).isChecked).isFalse()
        assertThat(view.radioAt(1).isChecked).isTrue()
        assertThat(view.checkedId).isEqualTo(FIRST_ID + 1)
    }

    @Test
    fun `the choice of a group survives save and restore without notifying`() {
        val view = group("Daily", "Weekly", "Never", checked = 0)
        Touch.tap(view.radioAt(2), 40f)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)

        val restored = group("Daily", "Weekly", "Never", checked = 0)
        var calls = 0
        restored.setOnCheckedChangeListener { _, _ -> calls++ }
        restored.restoreHierarchyState(saved)
        assertThat(restored.checkedId).isEqualTo(FIRST_ID + 2)
        assertThat((0..2).map { restored.radioAt(it).isChecked }).containsExactly(false, false, true).inOrder()
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `a group is a vertical single-choice collection for accessibility`() {
        val view = group("Daily", "Weekly", "Never", checked = 1)
        assertThat(view.orientation).isEqualTo(LinearLayout.VERTICAL)
        assertThat(view.accessibilityClassName.toString()).isEqualTo("android.widget.RadioGroup")
        val node = view.createAccessibilityNodeInfo()
        assertThat(node).isNotNull()
        val collection = node!!.collectionInfo
        assertThat(collection).isNotNull()
        assertThat(collection!!.rowCount).isEqualTo(3)
        assertThat(collection.selectionMode).isEqualTo(AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE)
    }

    // ---------------------------------------------------------------------------------------
    // Review additions
    // ---------------------------------------------------------------------------------------

    @Test
    fun `an empty label behaves like no label`() {
        val view = radio(label = "")
        val bare = radio(label = null)
        assertThat(view.height).isEqualTo(bare.height)
        // Both are laid out 300 dp wide: the bed sits in the middle, not at the start.
        val cobalt = SancaiTheme.light().let { it.bedColor(it.glazes.cobalt) }
        view.isChecked = true
        assertThat(view.bedPixel(centreXDp = WIDTH_DP / 2f)).isEqualTo(cobalt)
        assertThat(view.createAccessibilityNodeInfo()!!.text).isNull()
    }

    @Test
    fun `the label grows with textScale and the radio still fits its width`() {
        val text = "Remind me every single day of the week, including weekends and public holidays"
        val plain = radio(text)
        val large = radio(text, theme = SancaiTheme.light().copy(textScale = 1.13f))
        assertThat(large.height).isGreaterThan(plain.height)
        assertThat(large.width).isEqualTo(plain.width)
        val short = radio("Never", theme = SancaiTheme.light().copy(textScale = 1.13f))
        assertThat(short.height / short.density).isWithin(0.5f).of(44f)
        short.measure(0, 0)
        val narrow = radio("Never").apply { measure(0, 0) }
        assertThat(short.measuredWidth).isGreaterThan(narrow.measuredWidth)
    }

    @Test
    fun `a wrapped right to left label stays between the bed and the far edge`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val view = SancaiRadioButton(Snapshots.context()).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            text = "ذكرني كل يوم من أيام الأسبوع بما في ذلك عطلات نهاية الأسبوع والعطلات الرسمية"
            Snapshots.layout(this, WIDTH_DP)
        }
        assertThat(view.height / view.density).isGreaterThan(50f)
        val bitmap = Snapshots.render(view, WIDTH_DP)
        val canvas = bitmap.getPixel(1, 1)
        // Ink appears left of the bed, and the 10 dp gap beside the bed stays clear.
        var ink = 0
        val gapX = ((WIDTH_DP - 1.5f - 26f - 5f) * view.density).toInt()
        for (y in 0 until bitmap.height) {
            assertThat(bitmap.getPixel(gapX, y)).isEqualTo(canvas)
            for (x in 0 until gapX step 3) if (bitmap.getPixel(x, y) != canvas) ink++
        }
        assertThat(ink).isGreaterThan(100)
    }

    @Test
    fun `reduced motion jumps straight to the glazed bed`() {
        val theme = SancaiTheme.light().copy(reduceMotion = true)
        val view = radio(theme = theme)
        Snapshots.attach(view, WIDTH_DP)
        Touch.tap(view, 40f)
        assertThat(view.bedPixel()).isEqualTo(theme.bedColor(theme.glazes.cobalt))
    }

    @Test
    fun `detaching in the middle of the glide lands on the final state`() {
        val view = radio()
        val activity = Snapshots.attach(view, WIDTH_DP)
        val theme = SancaiTheme.light()
        Touch.tap(view, 40f)
        assertThat(view.bedPixel()).isNotEqualTo(theme.bedColor(theme.glazes.cobalt))
        (view.parent as android.view.ViewGroup).removeView(view)
        assertThat(view.isChecked).isTrue()
        assertThat(view.bedPixel()).isEqualTo(theme.bedColor(theme.glazes.cobalt))
        assertThat(activity.isFinishing).isFalse()
    }

    @Test
    fun `the listener hears the change before the click listener and only once`() {
        val view = radio()
        val order = mutableListOf<String>()
        view.setOnCheckedChangeListener { v, c ->
            order += "checked=$c state=${(v as SancaiRadioButton).isChecked}"
        }
        view.setOnClickListener { order += "click" }
        Touch.tap(view, 40f)
        Touch.tap(view, 40f)
        assertThat(order).containsExactly("checked=true state=true", "click", "click").inOrder()
    }

    @Test
    fun `a key reaches the click listener after the checked listener, as a tap does`() {
        val view = radio()
        val order = mutableListOf<String>()
        view.setOnCheckedChangeListener { _, c -> order += "checked=$c" }
        view.setOnClickListener { order += "click" }
        assertThat(key(view, KeyEvent.KEYCODE_SPACE)).isTrue()
        assertThat(view.isChecked).isTrue()
        // Checked already: the key is not consumed and nothing is clicked.
        assertThat(key(view, KeyEvent.KEYCODE_ENTER)).isFalse()
        assertThat(order).containsExactly("checked=true", "click").inOrder()
    }

    @Test
    fun `styled text with the same characters replaces the label`() {
        val view = radio("Accept terms")
        val plain = Snapshots.render(view, WIDTH_DP)
        val styled = android.text.SpannableString("Accept terms").apply {
            setSpan(android.text.style.ForegroundColorSpan(Color.RED), 0, length, 0)
        }
        view.text = styled
        assertThat(view.text).isSameInstanceAs(styled)
        Snapshots.layout(view, WIDTH_DP)
        assertThat(Snapshots.render(view, WIDTH_DP).sameAs(plain)).isFalse()
        // Plain text equal to the plain label is still skipped: no new layout pass is asked for.
        val other = radio("Accept terms")
        other.text = "Accept terms"
        assertThat(other.isLayoutRequested).isFalse()
    }

    @Test
    fun `a latin label in right to left keeps its short line next to the bed`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val view = SancaiRadioButton(Snapshots.context()).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            text = "Every day of the week\nNo"
            Snapshots.layout(this, WIDTH_DP)
        }
        val bitmap = Snapshots.render(view, WIDTH_DP)
        val canvas = bitmap.getPixel(1, 1)
        // The label box ends 10 dp before the bed; the second line is the lower half of it.
        val labelEnd = ((WIDTH_DP - 1.5f - 26f - 10f) * view.density).toInt()
        fun ink(fromX: Int, toX: Int): Int {
            var count = 0
            for (y in bitmap.height / 2 + 1 until bitmap.height) {
                for (x in fromX until toX) if (bitmap.getPixel(x, y) != canvas) count++
            }
            return count
        }
        assertThat(ink(labelEnd - (40 * view.density).toInt(), labelEnd)).isGreaterThan(0)
        assertThat(ink(0, labelEnd - (60 * view.density).toInt())).isEqualTo(0)
    }

    @Test
    fun `a radio disabled under the finger does not check on release`() {
        val view = radio()
        Touch.dispatch(view, android.view.MotionEvent.ACTION_DOWN, 40f, view.height / 2f)
        view.isEnabled = false
        Touch.dispatch(view, android.view.MotionEvent.ACTION_UP, 40f, view.height / 2f)
        assertThat(view.isChecked).isFalse()
    }

    @Test
    fun `a sibling cannot hold on to the choice from inside its listener`() {
        val view = group("Daily", "Weekly", checked = 0)
        var calls = 0
        view.radioAt(0).setOnCheckedChangeListener { v, _ ->
            calls++
            (v as SancaiRadioButton).isChecked = true
        }
        Touch.tap(view.radioAt(1), 40f)
        assertThat((0..1).map { view.radioAt(it).isChecked }).containsExactly(false, true).inOrder()
        assertThat(view.checkedId).isEqualTo(FIRST_ID + 1)
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `a sibling cannot hand the choice to another radio from inside its listener`() {
        val view = group("Never", "Daily", "Weekly", checked = 1)
        val reported = mutableListOf<Int>()
        view.setOnCheckedChangeListener { _, id -> reported += id }
        view.radioAt(1).setOnCheckedChangeListener { _, checked -> if (!checked) view.check(FIRST_ID) }
        Touch.tap(view.radioAt(2), 40f)
        assertThat((0..2).map { view.radioAt(it).isChecked }).containsExactly(false, false, true).inOrder()
        assertThat(view.checkedId).isEqualTo(FIRST_ID + 2)
        assertThat(reported).containsExactly(FIRST_ID + 2)
    }

    @Test
    fun `a sibling that clears the group from inside its listener leaves no choice`() {
        val view = group("Never", "Daily", "Weekly", checked = 1)
        val reported = mutableListOf<Int>()
        view.setOnCheckedChangeListener { _, id -> reported += id }
        view.radioAt(1).setOnCheckedChangeListener { _, checked -> if (!checked) view.clearCheck() }
        Touch.tap(view.radioAt(2), 40f)
        assertThat((0..2).map { view.radioAt(it).isChecked }).containsExactly(false, false, false).inOrder()
        assertThat(view.checkedId).isEqualTo(View.NO_ID)
        assertThat(reported).isEmpty()
        // The group still works afterwards.
        Touch.tap(view.radioAt(0), 40f)
        assertThat(view.checkedId).isEqualTo(FIRST_ID)
        assertThat(reported).containsExactly(FIRST_ID)
    }

    @Test
    fun `an id no radio has is not reported as the choice`() {
        val view = group("Daily", "Weekly", checked = 0)
        view.check(FIRST_ID + 40)
        assertThat((0..1).map { view.radioAt(it).isChecked }).containsExactly(false, false).inOrder()
        assertThat(view.checkedId).isEqualTo(View.NO_ID)
        view.addView(SancaiRadioButton(view.context).apply { id = FIRST_ID + 40 })
        assertThat(view.radioAt(2).isChecked).isTrue()
        assertThat(view.checkedId).isEqualTo(FIRST_ID + 40)
    }

    @Test
    fun `restoring an id no radio has keeps the present choice`() {
        // Radios without ids get generated ones, which differ after the screen is re-created.
        val view = SancaiRadioGroup(Snapshots.context()).apply {
            id = GROUP_ID
            addView(SancaiRadioButton(context))
            addView(SancaiRadioButton(context).apply { isChecked = true })
        }
        assertThat(view.checkedId).isEqualTo(view.radioAt(1).id)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)

        val restored = group("Daily", "Weekly", checked = 0)
        restored.restoreHierarchyState(saved)
        assertThat((0..1).map { restored.radioAt(it).isChecked }).containsExactly(true, false).inOrder()
        assertThat(restored.checkedId).isEqualTo(FIRST_ID)
    }

    @Test
    fun `an empty group reports no choice and survives save and restore`() {
        val view = group()
        assertThat(view.checkedId).isEqualTo(View.NO_ID)
        view.clearCheck()
        view.check(View.NO_ID)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)
        val restored = group("Daily", "Weekly", checked = 1)
        restored.restoreHierarchyState(saved)
        assertThat(restored.checkedId).isEqualTo(View.NO_ID)
        assertThat((0..1).map { restored.radioAt(it).isChecked }).containsExactly(false, false).inOrder()
    }

    @Test
    fun `a disabled radio in a group cannot take the choice`() {
        val view = group("Daily", "Weekly", checked = 0)
        var calls = 0
        view.setOnCheckedChangeListener { _, _ -> calls++ }
        view.radioAt(1).isEnabled = false
        Touch.tap(view.radioAt(1), 40f)
        view.radioAt(1).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
        assertThat(view.checkedId).isEqualTo(FIRST_ID)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `snapshot of radios in light and dark`() {
        for (theme in listOf("light" to SancaiTheme.light(), "dark" to SancaiTheme.dark())) {
            val context = Snapshots.context(theme.second)
            val glazes = theme.second.glazes
            val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
            val match = LinearLayout.LayoutParams.MATCH_PARENT
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            val choice = SancaiRadioGroup(context)
            listOf("Every day", "Every week", "Never").forEachIndexed { index, label ->
                choice.addView(
                    SancaiRadioButton(context).apply {
                        text = label
                        isChecked = index == 1
                    },
                    LinearLayout.LayoutParams(match, wrap),
                )
            }
            column.addView(choice, LinearLayout.LayoutParams(match, wrap))
            column.addView(
                SancaiRadioButton(context).apply {
                    text = "Remind me on every single day of the week, weekends and public holidays included"
                    glaze = glazes.celadon
                    isChecked = true
                },
                LinearLayout.LayoutParams(match, wrap),
            )
            column.addView(
                SancaiRadioButton(context).apply {
                    text = "Disabled, checked"
                    isChecked = true
                    isEnabled = false
                },
                LinearLayout.LayoutParams(match, wrap),
            )
            column.addView(
                SancaiRadioButton(context).apply {
                    text = "Disabled, unchecked"
                    isEnabled = false
                },
                LinearLayout.LayoutParams(match, wrap),
            )
            val bare = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            bare.addView(SancaiRadioButton(context), LinearLayout.LayoutParams(wrap, wrap))
            for (glaze in listOf(glazes.terracotta, glazes.ochre, glazes.cobalt, glazes.celadon, glazes.mist)) {
                bare.addView(
                    SancaiRadioButton(context).apply {
                        this.glaze = glaze
                        isChecked = true
                    },
                    LinearLayout.LayoutParams(wrap, wrap),
                )
            }
            column.addView(bare, LinearLayout.LayoutParams(match, wrap))
            val bitmap = Snapshots.capture(column, "radio_${theme.first}", 360)
            assertThat(bitmap.height).isGreaterThan(0)
        }
    }

    private companion object {
        const val WIDTH_DP = 300
        const val GROUP_ID = 0x5000
        const val FIRST_ID = 0x5001
    }
}
