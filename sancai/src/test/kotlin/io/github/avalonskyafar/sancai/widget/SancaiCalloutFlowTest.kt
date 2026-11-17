package io.github.avalonskyafar.sancai.widget

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIconView
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiCalloutFlowTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val density = context.resources.displayMetrics.density
    private val colors = SancaiTheme.light().colors

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private fun callout(
        title: String? = "What stays on the device",
        body: String? = "Recognition runs locally. Nothing on the screen is uploaded.",
        tone: SancaiTextTone = SancaiTextTone.ACCENT,
    ): SancaiCallout = SancaiCallout(context).also {
        it.title = title
        it.body = body
        it.tone = tone
    }

    private fun flow(count: Int = 3): SancaiFlowRow = SancaiFlowRow(context).also { row ->
        row.steps = STEPS.take(count)
    }

    private fun SancaiFlowRow.columns(): List<View> =
        (0 until childCount).map { getChildAt(it) }.filter { it !is SancaiIconView }

    private fun SancaiFlowRow.arrows(): List<View> =
        (0 until childCount).map { getChildAt(it) }.filterIsInstance<SancaiIconView>()

    private fun enableRtl() {
        val app = context.applicationContext
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
    }

    // ---------------------------------------------------------------------------------------
    // Callout
    // ---------------------------------------------------------------------------------------

    @Test
    fun `callout bar is three dp wide in the tone colour and spans the full height`() {
        val tones = mapOf(
            SancaiTextTone.ACCENT to colors.accent,
            SancaiTextTone.DANGER to colors.danger,
            SancaiTextTone.WARNING to colors.warning,
            SancaiTextTone.SUCCESS to colors.success,
        )
        for ((tone, expected) in tones) {
            val view = callout(tone = tone)
            val bitmap = Snapshots.render(view, 300)
            val bar = px(3f)
            for (y in listOf(0, bitmap.height / 2, bitmap.height - 1)) {
                assertThat(bitmap.getPixel(0, y)).isEqualTo(expected)
                assertThat(bitmap.getPixel(bar - 1, y)).isEqualTo(expected)
                assertThat(bitmap.getPixel(bar, y)).isEqualTo(colors.canvas)
            }
            assertThat(view.barColor).isEqualTo(expected)
            assertThat(view.titleView.currentTextColor).isEqualTo(expected)
            assertThat(view.bodyView.currentTextColor).isEqualTo(colors.ink)
        }
    }

    @Test
    fun `callout defaults to the accent tone and has no background`() {
        val view = SancaiCallout(context)
        assertThat(view.tone).isEqualTo(SancaiTextTone.ACCENT)
        assertThat(view.background).isNull()
        assertThat(view.isClickable).isFalse()
        assertThat(view.isFocusable).isFalse()
    }

    @Test
    fun `callout text starts fourteen dp after the bar and the body sits seven dp below the title`() {
        val view = callout()
        Snapshots.layout(view, 300)
        val inset = px(3f) + px(14f)
        assertThat(view.titleView.left).isEqualTo(inset)
        assertThat(view.bodyView.left).isEqualTo(inset)
        assertThat(view.titleView.top).isEqualTo(0)
        assertThat(view.bodyView.top - view.titleView.bottom).isEqualTo(px(7f))
        assertThat(view.bodyView.bottom).isEqualTo(view.height)
        assertThat(view.titleView.right).isEqualTo(view.width)
    }

    @Test
    fun `callout bar spans padding too and start padding lies outside the bar`() {
        val view = callout()
        view.setPaddingRelative(px(10f), px(8f), px(6f), px(12f))
        val bitmap = Snapshots.render(view, 300)
        assertThat(view.titleView.left).isEqualTo(px(10f) + px(3f) + px(14f))
        assertThat(view.titleView.top).isEqualTo(px(8f))
        assertThat(view.titleView.right).isEqualTo(view.width - px(6f))
        assertThat(view.height).isEqualTo(view.bodyView.bottom + px(12f))
        assertThat(bitmap.getPixel(px(10f) - 1, 0)).isEqualTo(colors.canvas)
        assertThat(bitmap.getPixel(px(10f), 0)).isEqualTo(colors.accent)
        assertThat(bitmap.getPixel(px(10f) + px(3f) - 1, bitmap.height - 1)).isEqualTo(colors.accent)
        assertThat(bitmap.getPixel(px(10f) + px(3f), bitmap.height - 1)).isEqualTo(colors.canvas)
    }

    @Test
    fun `callout mirrors in rtl`() {
        enableRtl()
        val view = callout().also { it.layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val bitmap = Snapshots.render(view, 300)
        val last = bitmap.width - 1
        assertThat(bitmap.getPixel(last, 0)).isEqualTo(colors.accent)
        assertThat(bitmap.getPixel(last - px(3f) + 1, bitmap.height - 1)).isEqualTo(colors.accent)
        assertThat(bitmap.getPixel(last - px(3f), bitmap.height - 1)).isEqualTo(colors.canvas)
        assertThat(bitmap.getPixel(0, bitmap.height / 2)).isNotEqualTo(colors.accent)
        assertThat(view.titleView.left).isEqualTo(0)
        assertThat(view.titleView.right).isEqualTo(view.width - px(3f) - px(14f))
    }

    @Test
    fun `callout without a title or without a body leaves no gap`() {
        val bodyOnly = callout(title = null)
        Snapshots.layout(bodyOnly, 300)
        assertThat(bodyOnly.title).isNull()
        assertThat(bodyOnly.titleView.visibility).isEqualTo(View.GONE)
        assertThat(bodyOnly.bodyView.top).isEqualTo(0)
        assertThat(bodyOnly.height).isEqualTo(bodyOnly.bodyView.height)

        val titleOnly = callout(body = "")
        Snapshots.layout(titleOnly, 300)
        assertThat(titleOnly.bodyView.visibility).isEqualTo(View.GONE)
        assertThat(titleOnly.height).isEqualTo(titleOnly.titleView.height)

        titleOnly.body = "Back again"
        Snapshots.layout(titleOnly, 300)
        assertThat(titleOnly.bodyView.top - titleOnly.titleView.bottom).isEqualTo(px(7f))
    }

    @Test
    fun `callout reads its attributes`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.sancaiTitle, "Careful")
            .addAttribute(R.attr.sancaiDetail, "This cannot be undone.")
            .addAttribute(R.attr.sancaiTone, "danger")
            .build()
        val view = SancaiCallout(context, attrs)
        assertThat(view.title.toString()).isEqualTo("Careful")
        assertThat(view.body.toString()).isEqualTo("This cannot be undone.")
        assertThat(view.tone).isEqualTo(SancaiTextTone.DANGER)
        assertThat(view.barColor).isEqualTo(colors.danger)
        Snapshots.layout(view, 300)
        assertThat(view.titleView.left).isEqualTo(px(3f) + px(14f))
    }

    @Test
    fun `callout is one accessibility element and survives large text`() {
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = SancaiTheme.LARGE_TEXT_SCALE))
        val view = SancaiCallout(large)
        view.title = "A fairly long title that has to wrap onto a second line at this width"
        view.body = "Body"
        Snapshots.layout(view, 200)
        assertThat(ViewCompat.isScreenReaderFocusable(view)).isTrue()
        assertThat(view.accessibilityClassName).isEqualTo(SancaiCallout::class.java.name)
        assertThat(view.titleView.lineCount).isGreaterThan(1)
        assertThat(view.bodyView.bottom).isEqualTo(view.height)
        val normal = callout()
        assertThat(view.titleView.textSize).isGreaterThan(normal.titleView.textSize)
    }

    // ---------------------------------------------------------------------------------------
    // Flow row
    // ---------------------------------------------------------------------------------------

    @Test
    fun `flow row columns are equal and the arrow slots are twenty-eight dp`() {
        for (count in 2..4) {
            val row = flow(count)
            Snapshots.layout(row, 340)
            val columns = row.columns()
            val arrows = row.arrows()
            assertThat(columns).hasSize(count)
            assertThat(arrows).hasSize(count - 1)
            for (column in columns) {
                assertThat(column.width.toFloat()).isWithin(1f).of(columns[0].width.toFloat())
            }
            for (arrow in arrows) assertThat(arrow.width).isEqualTo(px(28f))
            assertThat(columns.sumOf { it.width } + arrows.sumOf { it.width }).isEqualTo(row.width)
            // Order on screen: step, arrow, step ... from the left.
            assertThat(columns.map { it.left }).isInOrder()
            assertThat(arrows[0].left).isEqualTo(columns[0].right)
        }
    }

    @Test
    fun `flow row step is a centred title over a muted detail four dp below`() {
        val row = flow(3)
        Snapshots.layout(row, 340)
        val column = row.columns()[1] as ViewGroup
        val title = column.getChildAt(0) as SancaiText
        val detail = column.getChildAt(1) as SancaiText
        assertThat(title.text.toString()).isEqualTo("Recognise")
        assertThat(title.currentTextColor).isEqualTo(colors.ink)
        assertThat(detail.currentTextColor).isEqualTo(colors.muted)
        assertThat(detail.top - title.bottom).isEqualTo(px(4f))
        assertThat(title.gravity and android.view.Gravity.HORIZONTAL_GRAVITY_MASK).isEqualTo(android.view.Gravity.CENTER_HORIZONTAL)
        assertThat(detail.gravity and android.view.Gravity.HORIZONTAL_GRAVITY_MASK).isEqualTo(android.view.Gravity.CENTER_HORIZONTAL)
        assertThat(title.width).isEqualTo(column.width)
        val scaled = context.resources.displayMetrics.scaledDensity
        assertThat(title.textSize).isWithin(0.01f).of(15.5f * scaled)
        assertThat(detail.textSize).isWithin(0.01f).of(13.5f * scaled)
    }

    @Test
    fun `flow row arrows are accent, centred on the titles row and vertically centred`() {
        val row = flow(3)
        val bitmap = Snapshots.render(row, 340)
        for (arrow in row.arrows()) {
            assertThat(arrow.height).isEqualTo(px(18f))
            assertThat((arrow.top + arrow.bottom) / 2f).isWithin(1f).of(row.height / 2f)
            val cx = (arrow.left + arrow.right) / 2
            val cy = (arrow.top + arrow.bottom) / 2
            assertThat(bitmap.getPixel(cx, cy)).isEqualTo(colors.accent)
            // The head is on the right: more ink in the right half of the slot.
            assertThat(ink(bitmap, cx, arrow.right, arrow)).isGreaterThan(ink(bitmap, arrow.left, cx, arrow))
        }
    }

    private fun ink(bitmap: Bitmap, from: Int, to: Int, box: View): Int {
        var count = 0
        for (x in from until to) for (y in box.top until box.bottom) {
            if (bitmap.getPixel(x, y) != colors.canvas) count++
        }
        return count
    }

    @Test
    fun `flow row mirrors in rtl`() {
        enableRtl()
        val row = flow(3).also { it.layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val bitmap = Snapshots.render(row, 340)
        val columns = row.columns()
        assertThat(columns[0].right).isEqualTo(row.width)
        assertThat(columns.map { it.left }).isInOrder(Comparator.reverseOrder<Int>())
        val arrow = row.arrows()[0]
        val cx = (arrow.left + arrow.right) / 2
        assertThat(ink(bitmap, arrow.left, cx, arrow)).isGreaterThan(ink(bitmap, cx, arrow.right, arrow))
    }

    @Test
    fun `flow row replaces its steps and handles one or none`() {
        val row = flow(3)
        assertThat(row.steps).containsExactlyElementsIn(STEPS.take(3)).inOrder()
        row.setSteps("Only" to null)
        Snapshots.layout(row, 340)
        assertThat(row.childCount).isEqualTo(1)
        assertThat(row.getChildAt(0).width).isEqualTo(row.width)
        assertThat((row.getChildAt(0) as ViewGroup).childCount).isEqualTo(1)
        row.steps = emptyList()
        Snapshots.layout(row, 340)
        assertThat(row.childCount).isEqualTo(0)
        assertThat(row.height).isEqualTo(0)
    }

    @Test
    fun `flow row reads its steps from string arrays`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.sancaiFlowRowTitles, "@android:array/emailAddressTypes")
            .build()
        val row = SancaiFlowRow(context, attrs)
        val expected = context.resources.getTextArray(android.R.array.emailAddressTypes)
        assertThat(row.steps.map { it.title.toString() })
            .containsExactlyElementsIn(expected.map { it.toString() }).inOrder()
        assertThat(row.steps.all { it.detail == null }).isTrue()
        assertThat(SancaiFlowRow(context, Robolectric.buildAttributeSet().build()).steps).isEmpty()
    }

    @Test
    fun `flow row exposes one accessibility element per step in order`() {
        val row = flow(4)
        Snapshots.attach(row, 340)
        assertThat(row.isFocusable).isFalse()
        assertThat(row.isClickable).isFalse()
        val columns = row.columns()
        assertThat(columns.map { it.contentDescription.toString() })
            .containsExactly("Read, this screen", "Recognise, on the device", "Remind, as you set it", "Rest")
            .inOrder()
        for (column in columns) assertThat(ViewCompat.isScreenReaderFocusable(column)).isTrue()
        for (arrow in row.arrows()) {
            assertThat(arrow.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        }
        val info = row.createAccessibilityNodeInfo()
        assertThat(info).isNotNull()
        assertThat(info!!.collectionInfo).isNotNull()
        assertThat(info.collectionInfo.columnCount).isEqualTo(4)
        val item = columns[2].createAccessibilityNodeInfo()
        assertThat(item).isNotNull()
        assertThat(item!!.collectionItemInfo).isNotNull()
        assertThat(item.collectionItemInfo.columnIndex).isEqualTo(2)
    }

    @Test
    fun `flow row wraps long text inside its column at large text`() {
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = SancaiTheme.LARGE_TEXT_SCALE))
        val row = SancaiFlowRow(large)
        row.setSteps("Necessary information only" to "the current screen", "Local" to "on device", "Go" to null)
        Snapshots.layout(row, 300)
        val columns = row.columns()
        val title = (columns[0] as ViewGroup).getChildAt(0) as SancaiText
        assertThat(title.lineCount).isGreaterThan(1)
        assertThat(columns[0].width.toFloat()).isWithin(1f).of(columns[2].width.toFloat())
        assertThat(row.height).isEqualTo(columns[0].height)
        // Shorter steps are centred on the tallest one.
        assertThat((columns[2].top + columns[2].bottom) / 2f).isWithin(1f).of(row.height / 2f)
    }

    @Test
    fun `flow row step lines are silent and an empty detail is left out`() {
        val row = SancaiFlowRow(context)
        row.setSteps("Read" to "this screen", "Decide" to "")
        val columns = row.columns().map { it as ViewGroup }
        assertThat(columns[0].importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        for (i in 0 until columns[0].childCount) {
            assertThat(columns[0].getChildAt(i).importantForAccessibility)
                .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        }
        assertThat(columns[0].contentDescription.toString())
            .isEqualTo(context.getString(R.string.sancai_flow_row_step, "Read", "this screen"))
        assertThat(columns[1].childCount).isEqualTo(1)
        assertThat(columns[1].contentDescription.toString()).isEqualTo("Decide")
    }

    @Test
    fun `flow row stays equal and inside its width in rtl at large text`() {
        enableRtl()
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = SancaiTheme.LARGE_TEXT_SCALE))
        val row = SancaiFlowRow(large)
        row.layoutDirection = View.LAYOUT_DIRECTION_RTL
        row.setSteps("Necessary information only" to "the current screen", "Local" to "on device", "Go" to null)
        Snapshots.layout(row, 260)
        val columns = row.columns()
        assertThat(columns[0].right).isEqualTo(row.width)
        assertThat(columns[2].left).isEqualTo(0)
        assertThat(columns[0].width.toFloat()).isWithin(1f).of(columns[1].width.toFloat())
        for (arrow in row.arrows()) assertThat(arrow.width).isEqualTo(px(28f))
    }

    @Test
    fun `empty callout takes no room and extra children stack beside the bar`() {
        val empty = SancaiCallout(context)
        Snapshots.layout(empty, 300)
        assertThat(empty.height).isEqualTo(0)
        assertThat(empty.title).isNull()
        assertThat(empty.body).isNull()

        val view = callout()
        val extra = View(context)
        view.addView(extra, LinearLayout.LayoutParams(-1, px(20f)))
        val bitmap = Snapshots.render(view, 300)
        assertThat(extra.left).isEqualTo(px(3f) + px(14f))
        assertThat(extra.top).isEqualTo(view.bodyView.bottom)
        assertThat(extra.bottom).isEqualTo(view.height)
        assertThat(bitmap.getPixel(1, bitmap.height - 1)).isEqualTo(colors.accent)
    }

    // ---------------------------------------------------------------------------------------
    // Snapshots
    // ---------------------------------------------------------------------------------------

    @Test
    fun `snapshot of callout and flow row`() {
        for ((name, theme) in listOf("callout_light" to SancaiTheme.light(), "callout_dark" to SancaiTheme.dark())) {
            val themed = Snapshots.context(theme)
            val page = LinearLayout(themed)
            page.orientation = LinearLayout.VERTICAL
            page.setPadding(px(22f), px(26f), px(22f), px(30f))
            val flow = SancaiFlowRow(themed)
            flow.setSteps("必要信息" to "当前屏幕", "本地识别" to "设备内完成", "提醒／划走" to "按你的设置")
            page.addView(flow)
            val samples = listOf(
                Triple(SancaiTextTone.ACCENT, "权限边界", "只读取判断所需的屏幕信息，识别在设备内完成，不上传任何内容。"),
                Triple(SancaiTextTone.WARNING, "Battery saver is on", "Reminders may arrive late while the system limits background work."),
                Triple(SancaiTextTone.DANGER, "This cannot be undone", "Deleting the rule also removes its history."),
                Triple(SancaiTextTone.SUCCESS, "All set", null),
            )
            for ((tone, title, body) in samples) {
                val callout = SancaiCallout(themed)
                callout.tone = tone
                callout.title = title
                callout.body = body
                val params = LinearLayout.LayoutParams(-1, -2)
                params.topMargin = px(26f)
                page.addView(callout, params)
            }
            val bitmap = Snapshots.capture(page, name, 360)
            assertThat(bitmap.getPixel(2, 2)).isEqualTo(theme.colors.canvas)
            val first = page.getChildAt(1)
            assertThat(bitmap.getPixel(first.left + 1, first.top + first.height / 2)).isEqualTo(theme.colors.accent)
        }
    }

    private companion object {
        val STEPS = listOf(
            SancaiFlowRow.Step("Read", "this screen"),
            SancaiFlowRow.Step("Recognise", "on the device"),
            SancaiFlowRow.Step("Remind", "as you set it"),
            SancaiFlowRow.Step("Rest", null),
        )
    }
}
