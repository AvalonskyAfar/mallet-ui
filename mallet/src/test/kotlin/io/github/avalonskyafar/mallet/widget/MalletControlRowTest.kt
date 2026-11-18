package io.github.avalonskyafar.mallet.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.testing.Touch
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.theme.MalletWeight
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletControlRowTest {

    private val context = Snapshots.context(MalletTheme.light())
    private val density = context.resources.displayMetrics.density

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private fun row(
        options: Int,
        detail: String? = null,
        layout: MalletControlRow.Layout = MalletControlRow.Layout.AUTO,
        context: Context = this.context,
    ): MalletControlRow = MalletControlRow(context).also {
        it.title = "Protection"
        it.detail = detail
        it.layout = layout
        it.setSegmented(LABELS.take(options), 0, null)
    }

    private fun copyOf(row: MalletControlRow): View = row.getChildAt(0)

    /** Width of the control divided by the width of the text column. */
    private fun measuredRatio(row: MalletControlRow): Float {
        Snapshots.layout(row, 340)
        return row.control!!.width.toFloat() / copyOf(row).width
    }

    // ---------------------------------------------------------------------------------------
    // Control row
    // ---------------------------------------------------------------------------------------

    @Test
    fun `two options share the width two to one`() {
        val row = row(2)
        assertThat(row.textWeight).isEqualTo(2f)
        assertThat(row.controlWeight).isEqualTo(1f)
        assertThat(measuredRatio(row)).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `three options share the width one to three and four one to four`() {
        assertThat(measuredRatio(row(3))).isWithin(0.03f).of(3f)
        assertThat(measuredRatio(row(4))).isWithin(0.04f).of(4f)
        assertThat(measuredRatio(row(5))).isWithin(0.04f).of(4f)
    }

    @Test
    fun `utility layout is one to one point three five whatever the count`() {
        for (count in 2..4) {
            val row = row(count, layout = MalletControlRow.Layout.UTILITY)
            assertThat(row.textWeight).isEqualTo(1f)
            assertThat(row.controlWeight).isEqualTo(1.35f)
            assertThat(measuredRatio(row)).isWithin(0.02f).of(1.35f)
        }
        // Switching the layout re-shares the width of the track that is already there.
        val row = row(3)
        row.layout = MalletControlRow.Layout.UTILITY
        assertThat(measuredRatio(row)).isWithin(0.02f).of(1.35f)
        row.layout = MalletControlRow.Layout.AUTO
        assertThat(measuredRatio(row)).isWithin(0.03f).of(3f)
    }

    @Test
    fun `row is 64 dp high and 72 dp with a detail and centres the control`() {
        val plain = row(3)
        Snapshots.layout(plain, 340)
        assertThat(plain.height).isEqualTo(px(64f))
        val track = plain.track!!
        assertThat(track.height).isEqualTo(px(MalletSegmentedTrack.HEIGHT_DP))
        assertThat(abs((track.top + track.bottom) - plain.height)).isAtMost(1)

        val detailed = row(3, detail = "How strictly apps are held")
        Snapshots.layout(detailed, 340)
        assertThat(detailed.height).isEqualTo(px(72f))

        detailed.detail = " "
        Snapshots.layout(detailed, 340)
        assertThat(detailed.height).isEqualTo(px(64f))
        assertThat(detailed.detail).isNull()
    }

    @Test
    fun `text is centred in its column with a ten dp gap to the control`() {
        val row = row(2, detail = "Detail")
        Snapshots.layout(row, 340)
        val copy = copyOf(row) as LinearLayout
        val title = copy.getChildAt(0) as TextView
        val detail = copy.getChildAt(1) as TextView
        assertThat(title.left).isEqualTo(0)
        assertThat(title.width).isEqualTo(copy.width)
        // The line of text itself sits in the middle of the column.
        val line = title.layout
        val centre = (line.getLineLeft(0) + line.getLineRight(0)) / 2f
        assertThat(centre).isWithin(1f).of(copy.width / 2f)
        assertThat(detail.top - title.bottom).isEqualTo(px(3f))
        assertThat(row.control!!.left - copy.right).isEqualTo(px(10f))
        assertThat(abs((copy.top + copy.bottom) - row.height)).isAtMost(1)
    }

    @Test
    fun `any view can be the control and keeps its own width by default`() {
        val row = MalletControlRow(context)
        row.title = "Haptics"
        val switch = MalletSwitch(context)
        row.setControl(switch)
        Snapshots.layout(row, 340)
        assertThat(row.control).isSameInstanceAs(switch)
        assertThat(row.track).isNull()
        assertThat(switch.width).isEqualTo(px(MalletSwitch.WIDTH_DP))
        assertThat(copyOf(row).width).isEqualTo(row.width - switch.width - px(10f))
        assertThat(abs((switch.top + switch.bottom) - row.height)).isAtMost(1)

        row.setControl(switch, 1f, 1f)
        Snapshots.layout(row, 340)
        assertThat(abs(switch.width - copyOf(row).width)).isAtMost(1)
    }

    @Test
    fun `a new control replaces the old one and a second child is refused`() {
        val row = row(3)
        val first = row.track
        val slider = MalletSlider(context)
        row.setControl(slider, 1f, 2f)
        assertThat(row.childCount).isEqualTo(2)
        assertThat(row.control).isSameInstanceAs(slider)
        assertThat(first!!.parent).isNull()
        assertThrows(IllegalStateException::class.java) { row.addView(View(context)) }

        row.setControl(null)
        assertThat(row.childCount).isEqualTo(1)
        Snapshots.layout(row, 340)
        assertThat(copyOf(row).width).isEqualTo(row.width)
    }

    @Test
    fun `listener fires for a tap on the track and not for calls from code`() {
        val heard = ArrayList<Int>()
        val row = MalletControlRow(context)
        row.title = "Protection"
        val track = row.setSegmented(LABELS.take(3), 1) { _, index -> heard.add(index) }
        Snapshots.attach(row, 340)
        assertThat(row.track).isSameInstanceAs(track)
        assertThat(track.selectedIndex).isEqualTo(1)
        assertThat(heard).isEmpty()

        Touch.tap(row, track.left + track.width * 0.85f, row.height / 2f)
        Snapshots.idle()
        assertThat(track.selectedIndex).isEqualTo(2)
        assertThat(heard).containsExactly(2)

        // A tap on the text does nothing: the row itself is not a control.
        Touch.tap(row, copyOf(row).width / 2f, row.height / 2f)
        Snapshots.idle()
        assertThat(heard).containsExactly(2)

        // Calling again reuses the track and stays silent.
        val again = row.setSegmented(LABELS.take(4), 3) { _, index -> heard.add(index) }
        assertThat(again).isSameInstanceAs(track)
        assertThat(track.selectedIndex).isEqualTo(3)
        assertThat(row.controlWeight).isEqualTo(4f)
        assertThat(heard).containsExactly(2)
    }

    @Test
    fun `selection of the row's own track survives save and restore`() {
        val heard = ArrayList<Int>()
        val saved = row(4)
        saved.id = View.generateViewId()
        saved.track!!.select(2, animate = false)
        val state = SparseArray<Parcelable>()
        saved.saveHierarchyState(state)

        val restored = row(4)
        restored.id = saved.id
        restored.track!!.setOnSelectionChangeListener { _, index -> heard.add(index) }
        restored.restoreHierarchyState(state)
        assertThat(restored.track!!.selectedIndex).isEqualTo(2)
        assertThat(heard).isEmpty()
    }

    @Test
    fun `right to left puts the control at the left`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val row = row(2)
        Snapshots.layout(row, 340)
        assertThat(row.control!!.left).isGreaterThan(copyOf(row).right)

        row.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(row, 340)
        val copy = copyOf(row)
        assertThat(row.control!!.left).isEqualTo(0)
        assertThat(copy.right).isEqualTo(row.width)
        assertThat(copy.left - row.control!!.right).isEqualTo(px(10f))
        assertThat(measuredRatio(row)).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `disabling the row disables the control and pales the text`() {
        val row = row(3)
        row.isEnabled = false
        assertThat(row.control!!.isEnabled).isFalse()
        assertThat(copyOf(row).alpha).isWithin(0.001f).of(107 / 255f)

        // A control that arrives later is disabled as well, and a tap changes nothing.
        val switch = MalletSwitch(context)
        row.setControl(switch)
        assertThat(switch.isEnabled).isFalse()
        Snapshots.attach(row, 340)
        Touch.tap(row, switch.left + switch.width / 2f, row.height / 2f)
        Snapshots.idle()
        assertThat(switch.isChecked).isFalse()

        row.isEnabled = true
        assertThat(switch.isEnabled).isTrue()
        assertThat(copyOf(row).alpha).isEqualTo(1f)
    }

    @Test
    fun `the control speaks the text of the row unless it has a description of its own`() {
        val row = row(3, detail = "How strictly apps are held")
        assertThat(row.isFocusable).isFalse()
        assertThat(row.track!!.isFocusable).isTrue()
        assertThat(row.track!!.contentDescription.toString())
            .isEqualTo("Protection, How strictly apps are held")
        assertThat(copyOf(row).importantForAccessibility)
            .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)

        row.detail = null
        row.title = "Strength"
        assertThat(row.track!!.contentDescription.toString()).isEqualTo("Strength")

        val named = MalletSwitch(context)
        named.contentDescription = "Vibrate on touch"
        row.setControl(named)
        row.title = "Haptics"
        assertThat(named.contentDescription.toString()).isEqualTo("Vibrate on touch")

        // Without a control the text is read on its own.
        row.setControl(null)
        assertThat(copyOf(row).importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        assertThat(copyOf(row).contentDescription.toString()).isEqualTo("Haptics")
    }

    @Test
    fun `start alignment puts the text at the start edge with the inset of an entry row`() {
        val row = MalletControlRow(context)
        row.title = "Quiet hours"
        row.detail = "From ten to seven"
        row.setControl(MalletSwitch(context))
        Snapshots.layout(row, 340)
        val title = (copyOf(row) as LinearLayout).getChildAt(0) as TextView
        // Centred by default: the line starts well inside its column.
        assertThat(title.layout.getLineLeft(0)).isGreaterThan(px(30f).toFloat())

        row.alignment = MalletControlRow.Alignment.START
        Snapshots.layout(row, 340)
        assertThat(copyOf(row).paddingStart).isEqualTo(px(14f))
        assertThat(title.layout.getLineLeft(0)).isWithin(1f).of(0f)
        assertThat(title.left).isEqualTo(px(14f))
        // The control stays at the end edge and the row keeps its height.
        assertThat(row.control!!.right).isEqualTo(row.width)
        assertThat(row.height).isEqualTo(px(72f))

        row.alignment = MalletControlRow.Alignment.CENTER
        Snapshots.layout(row, 340)
        assertThat(copyOf(row).paddingStart).isEqualTo(0)
        assertThat(title.layout.getLineLeft(0)).isGreaterThan(px(30f).toFloat())
    }

    @Test
    fun `start alignment keeps the lift beside a track and comes from xml`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.malletTitle, "Level")
            .addAttribute(R.attr.malletControlRowAlignment, "start")
            .build()
        val row = MalletControlRow(context, attrs)
        assertThat(row.alignment).isEqualTo(MalletControlRow.Alignment.START)
        row.setSegmented(LABELS.take(3), 0, null)
        Snapshots.layout(row, 340)
        assertThat(copyOf(row).paddingStart).isEqualTo(px(14f))
        assertThat(copyOf(row).paddingBottom).isEqualTo(px(5f))
    }

    @Test
    fun `xml attributes set the text the layout and the enabled state`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.malletTitle, "Cleanup")
            .addAttribute(R.attr.malletDetail, "Runs at night")
            .addAttribute(R.attr.malletControlRowLayout, "utility")
            .addAttribute(R.attr.malletControlRowTextWeight, "2")
            .addAttribute(R.attr.malletControlRowControlWeight, "3")
            .addAttribute(android.R.attr.enabled, "false")
            .build()
        val row = MalletControlRow(context, attrs)
        assertThat(row.title.toString()).isEqualTo("Cleanup")
        assertThat(row.detail.toString()).isEqualTo("Runs at night")
        assertThat(row.layout).isEqualTo(MalletControlRow.Layout.UTILITY)
        assertThat(row.isEnabled).isFalse()

        // The single child becomes the control, as the inflater would add it.
        val slider = MalletSlider(context)
        row.addView(slider)
        assertThat(row.control).isSameInstanceAs(slider)
        assertThat(slider.isEnabled).isFalse()
        assertThat(row.textWeight).isEqualTo(2f)
        assertThat(row.controlWeight).isEqualTo(3f)
        assertThat(measuredRatio(row)).isWithin(0.02f).of(1.5f)

        row.setSegmented(LABELS.take(2), 0, null)
        assertThat(row.controlWeight).isEqualTo(1.35f)
    }

    @Test
    fun `large text makes the row taller instead of clipping it`() {
        val large = Snapshots.context(MalletTheme.light().copy(textScale = MalletTheme.LARGE_TEXT_SCALE))
        val row = row(4, detail = "A detail long enough to wrap in a narrow column", context = large)
        Snapshots.layout(row, 300)
        val copy = copyOf(row)
        assertThat(row.height).isGreaterThan(px(72f))
        assertThat(copy.top).isAtLeast(0)
        assertThat(copy.bottom).isAtMost(row.height)
    }

    @Test
    fun `beside a track the text is centred on the capsule and not on the track's view`() {
        for (detail in listOf(null, "Detail")) {
            val row = row(3, detail = detail)
            Snapshots.layout(row, 340)
            val copy = copyOf(row) as LinearLayout
            val track = row.track!!
            // The capsule lies between a 1.5 dp gutter above and a 6.5 dp gutter below.
            val capsuleCentre = track.top + (px(1.5f) + track.height - px(6.5f)) / 2f
            val last = copy.getChildAt(if (detail == null) 0 else 1)
            val textCentre = copy.top + (copy.getChildAt(0).top + last.bottom) / 2f
            assertThat(textCentre).isWithin(1.5f).of(capsuleCentre)
        }
        // Any other control is centred as a view, and so is the text.
        val row = row(3)
        row.setControl(MalletSwitch(context))
        Snapshots.layout(row, 340)
        val copy = copyOf(row) as LinearLayout
        val title = copy.getChildAt(0)
        assertThat(copy.top + (title.top + title.bottom) / 2f).isWithin(1f).of(row.height / 2f)
    }

    @Test
    fun `a control taken out with removeView or removeAllViews is forgotten`() {
        val row = row(3)
        val track = row.track!!
        row.removeView(track)
        assertThat(row.control).isNull()
        assertThat(row.track).isNull()
        assertThat(track.contentDescription).isNull()
        assertThat(copyOf(row).importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        Snapshots.layout(row, 340)
        assertThat(copyOf(row).width).isEqualTo(row.width)

        // The row takes a new control afterwards, and a new track is a fresh one.
        val next = row.setSegmented(LABELS.take(2), 1, null)
        assertThat(next).isNotSameInstanceAs(track)
        assertThat(row.control).isSameInstanceAs(next)
        assertThat(row.textWeight).isEqualTo(2f)

        row.removeAllViews()
        assertThat(row.control).isNull()
        // The text column is part of the row: it stays, and a new control stands next to it.
        assertThat(copyOf(row).parent).isSameInstanceAs(row)
        assertThat(row.childCount).isEqualTo(1)
        val switch = MalletSwitch(context)
        row.setControl(switch)
        Snapshots.layout(row, 340)
        assertThat(row.childCount).isEqualTo(2)
        assertThat(copyOf(row).width).isGreaterThan(0)
        assertThat(switch.left).isAtLeast(copyOf(row).right)
    }

    @Test
    fun `a control with text of its own keeps it and the row text stays readable`() {
        val row = MalletControlRow(context)
        row.title = "Backup"
        val button = MalletButton(context)
        button.text = "Run now"
        row.setControl(button)
        // A content description would replace "Run now" for a screen reader.
        assertThat(button.contentDescription).isNull()
        assertThat(copyOf(row).importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        assertThat(copyOf(row).contentDescription.toString()).isEqualTo("Backup")

        // A text view that gets its text after the row described it gives the description back.
        val late = TextView(context)
        row.setControl(late)
        assertThat(late.contentDescription.toString()).isEqualTo("Backup")
        late.text = "On"
        row.title = "Sync"
        assertThat(late.contentDescription).isNull()
        assertThat(copyOf(row).contentDescription.toString()).isEqualTo("Sync")
        assertThat(copyOf(row).importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)

        // The labelled controls of the library that are not text views name themselves too.
        val box = MalletCheckbox(context)
        box.text = "Daily"
        row.setControl(box)
        assertThat(box.contentDescription).isNull()
        assertThat(copyOf(row).importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        // A bare box has no name, so it takes the text of the row.
        val bare = MalletCheckbox(context)
        row.setControl(bare)
        assertThat(bare.contentDescription.toString()).isEqualTo("Sync")
    }

    @Test
    fun `margins and gravity of an added control are kept`() {
        val row = MalletControlRow(context)
        row.title = "Haptics"
        val switch = MalletSwitch(context)
        val given = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        given.marginEnd = px(8f)
        given.gravity = android.view.Gravity.BOTTOM
        row.addView(switch, given)
        val applied = switch.layoutParams as LinearLayout.LayoutParams
        assertThat(applied.marginEnd).isEqualTo(px(8f))
        assertThat(applied.gravity).isEqualTo(android.view.Gravity.BOTTOM)
        Snapshots.layout(row, 340)
        assertThat(switch.right).isEqualTo(row.width - row.paddingRight - px(8f))
        assertThat(switch.bottom).isEqualTo(row.height - row.paddingBottom)
    }

    @Test
    fun `a control with its own description leaves the text of the row readable`() {
        val row = MalletControlRow(context)
        row.title = "Haptics"
        val named = MalletSwitch(context)
        named.contentDescription = "Vibrate on touch"
        row.setControl(named)
        assertThat(named.contentDescription.toString()).isEqualTo("Vibrate on touch")
        assertThat(copyOf(row).importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        assertThat(copyOf(row).contentDescription.toString()).isEqualTo("Haptics")
        // Taking it out again does not strip the description it came with.
        row.setControl(null)
        assertThat(named.contentDescription.toString()).isEqualTo("Vibrate on touch")
    }

    @Test
    fun `empty and extreme content does not break the row`() {
        val bare = MalletControlRow(context)
        Snapshots.layout(bare, 340)
        assertThat(bare.height).isEqualTo(px(64f))
        assertThat(bare.title.toString()).isEmpty()
        assertThat(bare.detail).isNull()
        assertThat(copyOf(bare).contentDescription).isNull()

        val none = MalletControlRow(context)
        none.title = "Nothing to choose"
        val track = none.setSegmented(emptyList(), 3, null)
        Snapshots.render(none, 340)
        assertThat(track.options).isEmpty()
        // An index out of range lands on the nearest option.
        assertThat(none.setSegmented(LABELS.take(3), 9, null).selectedIndex).isEqualTo(2)

        val long = row(4, detail = "A detail that is far too long for the narrow column beside four options")
        long.title = "An unreasonably long title for a settings row"
        Snapshots.layout(long, 280)
        val copy = copyOf(long) as LinearLayout
        assertThat(copy.left).isEqualTo(0)
        assertThat(long.control!!.right).isEqualTo(long.width)
        assertThat(copy.top).isAtLeast(0)
        assertThat(copy.bottom).isAtMost(long.height)
        assertThat((copy.getChildAt(0) as TextView).lineCount).isGreaterThan(1)

        assertThrows(IllegalArgumentException::class.java) { long.setControl(View(context), 0f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { long.setControl(View(context), 1f, -1f) }
    }

    @Test
    fun `an empty list is an empty card and setItems replaces the rows`() {
        val list = MalletKeyValueList(context)
        Snapshots.render(list, 340)
        assertThat(list.items).isEmpty()
        assertThat(list.childCount).isEqualTo(0)

        list.setItems(listOf(MalletKeyValueList.Item("A", "1"), MalletKeyValueList.Item("B", null)))
        assertThat(list.items).hasSize(2)
        assertThat(list.childCount).isEqualTo(3)
        list.setItems(listOf(MalletKeyValueList.Item("C", "", MalletTextTone.DANGER)))
        assertThat(list.childCount).isEqualTo(1)
        assertThat(list.items.single().tone).isEqualTo(MalletTextTone.DANGER)
        list.clearItems()
        assertThat(list.childCount).isEqualTo(0)
        assertThat(list.items).isEmpty()
    }

    @Test
    fun `large text scales the key-value text and keeps the columns`() {
        val large = Snapshots.context(MalletTheme.light().copy(textScale = MalletTheme.LARGE_TEXT_SCALE))
        val plain = rowsOf(facts().also { Snapshots.layout(it, 340) })
        val scaled = rowsOf(facts(large).also { Snapshots.layout(it, 340) })
        val small = plain[0].getChildAt(0) as TextView
        val big = scaled[0].getChildAt(0) as TextView
        assertThat(big.textSize / small.textSize).isWithin(0.01f).of(MalletTheme.LARGE_TEXT_SCALE)
        for (row in scaled) {
            val key = row.getChildAt(0)
            val value = row.getChildAt(1)
            assertThat(key.width / value.width.toFloat()).isWithin(0.02f).of(1.22f)
            assertThat(key.top).isAtLeast(px(8f))
            assertThat(key.bottom).isAtMost(row.height - px(8f))
            assertThat(value.bottom).isAtMost(row.height - px(8f))
        }
    }

    // ---------------------------------------------------------------------------------------
    // Key-value list
    // ---------------------------------------------------------------------------------------

    private fun facts(context: Context = this.context): MalletKeyValueList = MalletKeyValueList(context).also {
        it.addItem("Service", "Running", MalletTextTone.SUCCESS)
        it.addItem("Accessibility permission, needed to see the foreground app", "Off", MalletTextTone.DANGER)
        it.addItem("Version", "2.4.1 (build 318), released to the beta channel last week")
        it.addItem("Last check", null)
    }

    private fun rowsOf(list: MalletKeyValueList): List<LinearLayout> =
        (0 until list.childCount).map { list.getChildAt(it) }.filterIsInstance<LinearLayout>()

    @Test
    fun `columns line up across rows with long and short text`() {
        val list = facts()
        Snapshots.layout(list, 340)
        val rows = rowsOf(list)
        assertThat(rows).hasSize(4)
        val first = rows[0]
        for (row in rows) {
            val key = row.getChildAt(0)
            val value = row.getChildAt(1)
            assertThat(key.left).isEqualTo(px(12f))
            assertThat(key.width).isEqualTo(first.getChildAt(0).width)
            assertThat(value.left).isEqualTo(first.getChildAt(1).left)
            assertThat(value.right).isEqualTo(row.width - px(12f))
            assertThat(value.left - key.right).isEqualTo(px(10f))
            assertThat(key.width / value.width.toFloat()).isWithin(0.02f).of(1.22f)
            assertThat(row.height).isAtLeast(px(52f))
            assertThat(abs((key.top + key.bottom) - row.height)).isAtMost(1)
        }
        assertThat(rows[0].height).isEqualTo(px(52f))
        // Long text wraps inside its column and makes only its own row taller.
        assertThat((rows[1].getChildAt(0) as TextView).lineCount).isGreaterThan(1)
        assertThat(rows[1].height).isGreaterThan(px(52f))
        // Values hug the end edge, keys the start edge.
        val value = (rows[0].getChildAt(1) as TextView)
        assertThat(value.layout.getLineRight(0)).isWithin(1f).of(value.width.toFloat())
        val key = (rows[0].getChildAt(0) as TextView)
        assertThat(key.layout.getLineLeft(0)).isWithin(1f).of(0f)
    }

    @Test
    fun `values take their tone, keys stay ink and a missing value is a dash`() {
        val colors = MalletTheme.light().colors
        val list = facts()
        val rows = rowsOf(list)
        fun key(i: Int) = rows[i].getChildAt(0) as TextView
        fun value(i: Int) = rows[i].getChildAt(1) as TextView
        assertThat(value(0).currentTextColor).isEqualTo(colors.success)
        assertThat(value(1).currentTextColor).isEqualTo(colors.danger)
        assertThat(value(2).currentTextColor).isEqualTo(colors.ink)
        for (i in 0..3) assertThat(key(i).currentTextColor).isEqualTo(colors.ink)
        assertThat(value(3).text.toString()).isEqualTo("—")
        assertThat(key(0).textSize).isWithin(0.01f).of(13.5f * context.resources.displayMetrics.scaledDensity)
        assertThat(value(0).typeface).isEqualTo(MalletWeight.MEDIUM.typeface())
        assertThat(key(0).typeface).isEqualTo(MalletWeight.REGULAR.typeface())
        assertThat(list.items.map { it.value }).containsExactly("Running", "Off", list.items[2].value, null).inOrder()

        list.setItems(listOf(MalletKeyValueList.Item("Only", "one")))
        assertThat(list.childCount).isEqualTo(1)
        list.clearItems()
        assertThat(list.childCount).isEqualTo(0)
        assertThat(list.items).isEmpty()
    }

    @Test
    fun `dividers run from rim to rim in the divider colour`() {
        val list = facts()
        val bitmap = Snapshots.render(list, 340)
        val dividers = (0 until list.childCount).map { list.getChildAt(it) }.filterIsInstance<MalletDivider>()
        assertThat(dividers).hasSize(3)
        val colour = MalletTheme.light().colors.divider
        for (divider in dividers) {
            // The body of the card starts 1.4 dp inside the view; the divider ignores the padding.
            assertThat(divider.left).isEqualTo(px(1.4f))
            assertThat(divider.right).isEqualTo(list.width - px(1.4f))
            val y = divider.top + divider.height / 2
            assertThat(bitmap.getPixel(divider.left + px(3f), y)).isEqualTo(colour)
            assertThat(bitmap.getPixel(list.width / 2, y)).isEqualTo(colour)
            assertThat(bitmap.getPixel(divider.right - px(3f), y)).isEqualTo(colour)
        }
        // The rows themselves paint nothing: above the first divider is plain porcelain.
        val above = bitmap.getPixel(list.width / 2, dividers[0].top - px(2f))
        assertThat(above).isNotEqualTo(colour)
    }

    @Test
    fun `rows are read as one element and cannot be operated`() {
        val list = facts()
        Snapshots.attach(list, 340)
        val rows = rowsOf(list)
        assertThat(rows[0].contentDescription.toString()).isEqualTo("Service, Running")
        assertThat(rows[3].contentDescription.toString()).isEqualTo("Last check, No value")
        for (row in rows) {
            assertThat(row.isClickable).isFalse()
            assertThat(row.isFocusable).isFalse()
            assertThat(row.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
            assertThat(row.getChildAt(0).importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
            assertThat(row.getChildAt(1).importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        }
        // No press feedback: nothing moves under a finger.
        val row = rows[0]
        val top = row.getChildAt(0).top
        Touch.dispatch(list, android.view.MotionEvent.ACTION_DOWN, list.width / 2f, row.top + row.height / 2f)
        assertThat(row.isPressed).isFalse()
        assertThat(row.getChildAt(0).top).isEqualTo(top)
        assertThat(row.getChildAt(0).translationY).isEqualTo(0f)
    }

    @Test
    fun `right to left mirrors the key and value columns`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val list = facts()
        list.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(list, 340)
        val row = rowsOf(list)[0]
        val key = row.getChildAt(0) as TextView
        val value = row.getChildAt(1) as TextView
        assertThat(key.right).isEqualTo(row.width - px(12f))
        assertThat(value.left).isEqualTo(px(12f))
        assertThat(key.left - value.right).isEqualTo(px(10f))
        assertThat(key.layout.getLineRight(0)).isWithin(1f).of(key.width.toFloat())
        assertThat(value.layout.getLineLeft(0)).isWithin(1f).of(0f)
    }

    // ---------------------------------------------------------------------------------------
    // Snapshots
    // ---------------------------------------------------------------------------------------

    private fun page(theme: MalletTheme): View {
        val context = Snapshots.context(theme)
        val page = LinearLayout(context)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(px(14f), px(14f), px(14f), px(14f))
        page.setBackgroundColor(theme.colors.canvas)

        val card = MalletSurface(context)
        fun add(row: MalletControlRow) {
            if (card.childCount > 0) card.addDivider()
            card.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        add(row(2, context = context).also { it.title = "Haptics"; it.track!!.select(1, false) })
        add(row(3, detail = "How strictly apps are held", context = context).also { it.track!!.select(1, false) })
        add(row(4, context = context).also { it.title = "Level"; it.track!!.select(3, false) })
        add(row(3, layout = MalletControlRow.Layout.UTILITY, context = context).also {
            it.title = "Text size"
            it.detail = "Utility layout"
            it.track!!.setHues(listOf(theme.hues.mist))
            it.track!!.select(2, false)
        })
        add(MalletControlRow(context).also {
            it.title = "Start on boot"
            it.detail = "Any view can be the control"
            it.setControl(MalletSwitch(context).also { switch -> switch.setChecked(true, false) })
        })
        add(MalletControlRow(context).also {
            it.title = "Volume"
            it.setControl(MalletSlider(context), 1f, 2f)
        })
        add(row(3, detail = "Disabled", context = context).also { it.isEnabled = false })
        page.addView(card, LinearLayout.LayoutParams(-1, -2))

        val facts = facts(context)
        page.addView(facts, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12f) })
        return page
    }

    @Test
    fun `snapshot of control rows and a key-value list`() {
        val light = Snapshots.capture(page(MalletTheme.light()), "controlrow_light", 380)
        val dark = Snapshots.capture(page(MalletTheme.dark()), "controlrow_dark", 380)
        assertThat(light.width).isEqualTo(dark.width)
        assertThat(light.height).isEqualTo(dark.height)
        assertThat(light.getPixel(4, 4)).isEqualTo(MalletTheme.light().colors.canvas)
        assertThat(dark.getPixel(4, 4)).isEqualTo(MalletTheme.dark().colors.canvas)
    }

    private companion object {
        val LABELS = listOf("Off", "Gentle", "Standard", "Strict", "Max")
    }
}
