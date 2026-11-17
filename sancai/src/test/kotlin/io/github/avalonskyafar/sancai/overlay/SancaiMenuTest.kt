package io.github.avalonskyafar.sancai.overlay

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiDivider
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiMenuTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val density = context.resources.displayMetrics.density

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private fun sample(theme: SancaiTheme = SancaiTheme.light()): SancaiMenu =
        SancaiMenu(Snapshots.context(theme))
            .add("Sort by name", SancaiIcon.EDIT)
            .add("Sort by date", SancaiIcon.SEARCH)
            .add("Show hidden", SancaiIcon.EYE)
            .addDivider()
            .addDanger("Delete all", SancaiIcon.TRASH)
            .setChecked(1)

    /** Lays the panel out the way the popup does: as wide as it wants to be. */
    private fun laidOut(menu: SancaiMenu, rtl: Boolean = false): MenuPanel {
        val panel = menu.buildPanel(if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        return panel
    }

    private fun render(panel: MenuPanel): Bitmap = Snapshots.render(panel, (panel.width / density).roundToInt())

    private fun count(bitmap: Bitmap, area: Rect, color: Int): Int {
        var hits = 0
        for (y in area.top until area.bottom) for (x in area.left until area.right) {
            if (bitmap.getPixel(x, y) == color) hits++
        }
        return hits
    }

    /** Pixels dark enough to be part of an ink stroke on the light porcelain. */
    private fun dark(bitmap: Bitmap, area: Rect): Int {
        var hits = 0
        for (y in area.top until area.bottom) for (x in area.left until area.right) {
            if (android.graphics.Color.red(bitmap.getPixel(x, y)) < 128) hits++
        }
        return hits
    }

    private fun bounds(view: View): Rect = Rect(view.left, view.top, view.right, view.bottom)

    /** An attached anchor button near the top of the window, or near its bottom when [low]. */
    private fun anchor(low: Boolean = false): SancaiButton {
        val button = SancaiButton(context)
        button.text = "More"
        val frame = FrameLayout(context)
        val params = FrameLayout.LayoutParams(px(96f), FrameLayout.LayoutParams.WRAP_CONTENT)
        params.topMargin = px(40f)
        params.leftMargin = px(200f)
        frame.addView(button, params)
        Snapshots.attach(frame, 360)
        if (low) {
            val root = button.rootView.height
            (frame.layoutParams as FrameLayout.LayoutParams).height = root
            params.topMargin = root - px(120f)
            frame.requestLayout()
            Snapshots.idle()
        }
        return button
    }

    private fun settle() {
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        Snapshots.idle()
    }

    @Test
    fun `items and groups are built in order`() {
        val menu = sample()
        assertThat(menu.itemCount).isEqualTo(4)
        val panel = laidOut(menu)
        assertThat(panel.items.map { it.entry.label.toString() })
            .containsExactly("Sort by name", "Sort by date", "Show hidden", "Delete all").inOrder()
        assertThat(panel.childCount).isEqualTo(5)
        assertThat(panel.getChildAt(3)).isInstanceOf(SancaiDivider::class.java)
        // The divider runs from rim to rim of the body.
        assertThat(panel.getChildAt(3).left).isEqualTo(panel.gutterSide)
        assertThat(panel.getChildAt(3).right).isEqualTo(panel.width - panel.gutterSide)
    }

    @Test
    fun `an item can be added with nothing but a label and what it does`() {
        // The two-argument forms exist for Java, where optional parameters cannot be skipped.
        val run = Runnable { }
        val menu = SancaiMenu(context).add("Edit", run).addDanger("Delete", run)
        assertThat(menu.itemCount).isEqualTo(2)
        assertThat(menu.isEnabled(0)).isTrue()
    }

    @Test
    fun `leading and doubled dividers are ignored`() {
        val menu = SancaiMenu(context).addDivider().add("One").addDivider().addDivider().add("Two")
        val panel = laidOut(menu)
        assertThat(panel.childCount).isEqualTo(3)
        assertThat(menu.itemCount).isEqualTo(2)
    }

    @Test
    fun `panel keeps its metrics`() {
        val short = laidOut(SancaiMenu(context).add("A").add("B"))
        assertThat(short.width - 2 * short.gutterSide).isEqualTo(px(SancaiMenu.MIN_WIDTH_DP))
        assertThat(short.items[0].height).isEqualTo(px(SancaiMenu.ITEM_HEIGHT_DP))
        assertThat(short.items[0].top).isEqualTo(short.gutterTop + px(4f))
        assertThat(short.height).isEqualTo(short.gutterTop + short.gutterBottom + 2 * px(4f) + 2 * px(48f))

        val long = laidOut(SancaiMenu(context).add("A very long label that cannot possibly fit into the widest menu"))
        assertThat(long.width - 2 * long.gutterSide).isEqualTo(px(SancaiMenu.MAX_WIDTH_DP))
    }

    @Test
    fun `checked state can be set and cleared`() {
        val menu = sample()
        assertThat(menu.isChecked(1)).isTrue()
        assertThat(menu.isChecked(0)).isFalse()
        menu.setChecked(1, false).setChecked(2).setChecked(99)
        assertThat(menu.isChecked(1)).isFalse()
        assertThat(menu.isChecked(2)).isTrue()
    }

    @Test
    fun `destructive item is drawn in the danger colour and nothing else is`() {
        val theme = SancaiTheme.light()
        val panel = laidOut(sample())
        val bitmap = render(panel)
        assertThat(count(bitmap, bounds(panel.items[3]), theme.colors.danger)).isGreaterThan(20)
        assertThat(count(bitmap, bounds(panel.items[0]), theme.colors.danger)).isEqualTo(0)
        assertThat(count(bitmap, bounds(panel.items[0]), theme.colors.ink)).isGreaterThan(20)
        assertThat(count(bitmap, bounds(panel.items[3]), theme.colors.ink)).isEqualTo(0)
    }

    @Test
    fun `only the checked item carries a check mark at the end edge`() {
        val panel = laidOut(sample())
        val bitmap = render(panel)
        fun endColumn(index: Int): Rect {
            val item = panel.items[index]
            return Rect(item.right - px(36f), item.top, item.right - px(16f), item.bottom)
        }
        assertThat(dark(bitmap, endColumn(1))).isGreaterThan(20)
        assertThat(dark(bitmap, endColumn(0))).isEqualTo(0)

        // Mirrored: the check moves to the left edge, the label starts on the right.
        val app = context.applicationContext
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val mirrored = laidOut(sample(), rtl = true)
        val flipped = render(mirrored)
        val item = mirrored.items[1]
        assertThat(dark(flipped, Rect(item.left + px(16f), item.top, item.left + px(36f), item.bottom))).isGreaterThan(20)
        assertThat(dark(flipped, Rect(item.right - px(36f), item.top, item.right - px(16f), item.bottom))).isEqualTo(0)
    }

    @Test
    fun `disabled item pales and cannot be picked`() {
        var runs = 0
        val menu = SancaiMenu(context).add("Copy") { runs++ }.add("Paste") { runs++ }.setEnabled(1, false)
        assertThat(menu.isEnabled(1)).isFalse()
        val ink = SancaiTheme.light().colors.ink
        val bitmap = render(laidOut(menu))
        val still = laidOut(menu)
        assertThat(count(bitmap, bounds(still.items[0]), ink)).isGreaterThan(20)
        assertThat(count(bitmap, bounds(still.items[1]), ink)).isEqualTo(0)

        menu.show(anchor())
        val item = menu.showingPanel!!.items[1]
        assertThat(item.isEnabled).isFalse()
        assertThat(item.isFocusable).isFalse()
        Touch.tap(item, item.width / 2f, item.height / 2f)
        settle()
        assertThat(runs).isEqualTo(0)
        assertThat(menu.isShowing).isTrue()
    }

    @Test
    fun `click runs the callback then dismisses`() {
        val order = ArrayList<String>()
        val menu = SancaiMenu(context)
            .add("Copy") { order.add("copy") }
            .add("Paste") { order.add("paste") }
            .setOnDismissListener { order.add("dismissed") }
        val opener = anchor()
        menu.show(opener)
        assertThat(menu.isShowing).isTrue()
        val item = menu.showingPanel!!.items[1]
        Touch.tap(item, item.width / 2f, item.height / 2f)
        settle()
        assertThat(order).containsExactly("paste", "dismissed").inOrder()
        assertThat(menu.isShowing).isFalse()
        assertThat(menu.showingPanel).isNull()

        // It can be shown again.
        menu.show(opener)
        assertThat(menu.isShowing).isTrue()
        menu.dismiss()
        settle()
        assertThat(menu.isShowing).isFalse()
    }

    @Test
    fun `click ticks before it runs the callback`() {
        var hapticSeenInside = 0
        var item: View? = null
        val menu = SancaiMenu(context).add("Copy") {
            hapticSeenInside = org.robolectric.Shadows.shadowOf(item).lastHapticFeedbackPerformed()
        }
        menu.show(anchor())
        item = menu.showingPanel!!.items[0]
        Touch.tap(item, item.width / 2f, item.height / 2f)
        settle()
        assertThat(SancaiTheme.of(context).haptics).isTrue()
        assertThat(hapticSeenInside).isEqualTo(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }

    @Test
    fun `pressing sinks the content and release restores it`() {
        val menu = SancaiMenu(context).add("Copy").add("Paste")
        menu.show(anchor())
        settle()
        val item = menu.showingPanel!!.items[0]
        Touch.dispatch(item, android.view.MotionEvent.ACTION_DOWN, 20f, 20f)
        ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
        assertThat(item.sinkPx).isWithin(0.01f).of(1.5f * density)
        Touch.dispatch(item, android.view.MotionEvent.ACTION_CANCEL, 20f, 20f)
        assertThat(item.sinkPx).isEqualTo(0f)
    }

    @Test
    fun `placement hangs below the end edge, flips when low and stays inside the window`() {
        val frame = Rect(0, 0, 1000, 2000)
        val gutters = Rect(4, 3, 4, 13)
        // Body is 400 by 300.
        val below = SancaiMenu.place(Rect(700, 100, 900, 200), frame, 408, 316, gutters, false, 20, 10)
        assertThat(below.above).isFalse()
        assertThat(below.x + 4 + 400).isEqualTo(900)
        assertThat(below.y + 3).isEqualTo(210)

        val rtl = SancaiMenu.place(Rect(100, 100, 300, 200), frame, 408, 316, gutters, true, 20, 10)
        assertThat(rtl.x + 4).isEqualTo(100)

        val flipped = SancaiMenu.place(Rect(700, 1800, 900, 1900), frame, 408, 316, gutters, false, 20, 10)
        assertThat(flipped.above).isTrue()
        assertThat(flipped.y + 3 + 300).isEqualTo(1790)

        // An anchor at the start edge: the body is pushed back to the margin.
        val clamped = SancaiMenu.place(Rect(0, 100, 100, 200), frame, 408, 316, gutters, false, 20, 10)
        assertThat(clamped.x + 4).isEqualTo(20)
    }

    @Test
    fun `menu flips above an anchor that sits low in the window`() {
        val high = sample()
        val top = anchor()
        high.show(top)
        val origin = IntArray(2)
        top.getLocationInWindow(origin)
        assertThat(high.placement!!.above).isFalse()
        assertThat(high.placement!!.y).isAtLeast(origin[1] + top.height)

        val low = sample()
        val bottom = anchor(low = true)
        low.show(bottom)
        bottom.getLocationInWindow(origin)
        val panel = low.showingPanel!!
        assertThat(low.placement!!.above).isTrue()
        assertThat(low.placement!!.y + panel.measuredHeight - panel.gutterBottom).isAtMost(origin[1])
        // Body end edge on the anchor's end edge.
        assertThat(low.placement!!.x + panel.measuredWidth - panel.gutterSide).isEqualTo(origin[0] + bottom.width)
    }

    @Test
    fun `keyboard walks enabled items and escape closes`() {
        val menu = SancaiMenu(context).add("One").add("Two").add("Three").setEnabled(1, false)
        menu.show(anchor())
        settle()
        val panel = menu.showingPanel!!
        val items = panel.items
        // Focus skips the disabled row; at the ends the key is not consumed.
        items.forEach { it.isFocusableInTouchMode = it.isEnabled }
        assertThat(panel.moveFocus(items[0], 1)).isTrue()
        assertThat(items[2].isFocused).isTrue()
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
        assertThat(items[2].onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, down)).isFalse()
        val up = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
        assertThat(items[2].onKeyDown(KeyEvent.KEYCODE_DPAD_UP, up)).isTrue()
        assertThat(items[0].isFocused).isTrue()

        val escape = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)
        assertThat(items[0].onKeyDown(KeyEvent.KEYCODE_ESCAPE, escape)).isTrue()
        settle()
        assertThat(menu.isShowing).isFalse()
    }

    @Test
    fun `enter activates the focused item`() {
        var runs = 0
        val menu = SancaiMenu(context).add("One") { runs++ }
        menu.show(anchor())
        settle()
        val item = menu.showingPanel!!.items[0]
        item.onKeyDown(KeyEvent.KEYCODE_ENTER, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        item.onKeyUp(KeyEvent.KEYCODE_ENTER, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        settle()
        assertThat(runs).isEqualTo(1)
        assertThat(menu.isShowing).isFalse()
    }

    @Test
    fun `items are menu items with their state`() {
        val panel = laidOut(sample().setEnabled(2, false))
        assertThat(panel.accessibilityClassName.toString()).isEqualTo("android.view.Menu")
        val checked = AccessibilityNodeInfo.obtain()
        panel.items[1].onInitializeAccessibilityNodeInfo(checked)
        assertThat(checked.className.toString()).isEqualTo("android.view.MenuItem")
        assertThat(checked.text.toString()).isEqualTo("Sort by date")
        assertThat(checked.isCheckable).isTrue()
        assertThat(checked.isChecked).isTrue()
        val disabled = AccessibilityNodeInfo.obtain()
        panel.items[2].onInitializeAccessibilityNodeInfo(disabled)
        assertThat(disabled.isEnabled).isFalse()
        assertThat(disabled.isChecked).isFalse()
    }

    @Test
    fun `an empty menu or a detached anchor does not show`() {
        SancaiMenu(context).also { it.show(anchor()) }.let { assertThat(it.isShowing).isFalse() }
        val menu = sample()
        menu.show(SancaiButton(context))
        assertThat(menu.isShowing).isFalse()
    }

    @Test
    fun `a long label stops at the widest panel and is ellipsised inside it`() {
        val panel = laidOut(SancaiMenu(context).add("Move every selected record to the archive of last year", SancaiIcon.INBOX).add("Ok"))
        assertThat(panel.width - 2 * panel.gutterSide).isEqualTo(px(SancaiMenu.MAX_WIDTH_DP))
        // Nothing is drawn in the end padding of the long row.
        val row = bounds(panel.items[0])
        val endPadding = Rect(row.right - px(14f), row.top, row.right, row.bottom)
        assertThat(dark(render(panel), endPadding)).isEqualTo(0)
    }

    @Test
    fun `large text keeps the rows tall enough and the panel within its widths`() {
        val large = SancaiTheme.light().copy(textScale = 1.13f)
        val menu = SancaiMenu(Snapshots.context(large)).add("Sort by name", SancaiIcon.EDIT).add("Sort by date").setChecked(1)
        val panel = laidOut(menu)
        val plain = laidOut(SancaiMenu(context).add("Sort by name", SancaiIcon.EDIT).add("Sort by date").setChecked(1))
        assertThat(panel.items[0].height).isAtLeast(px(SancaiMenu.ITEM_HEIGHT_DP))
        assertThat(panel.width).isAtLeast(plain.width)
        assertThat(panel.width - 2 * panel.gutterSide).isAtMost(px(SancaiMenu.MAX_WIDTH_DP))
        // The label still has ink and stays clear of the row edges.
        val row = bounds(panel.items[0])
        val bitmap = render(panel)
        assertThat(dark(bitmap, row)).isGreaterThan(20)
        assertThat(dark(bitmap, Rect(row.left, row.top, row.right, row.top + px(8f)))).isEqualTo(0)
        assertThat(dark(bitmap, Rect(row.left, row.bottom - px(8f), row.right, row.bottom))).isEqualTo(0)
    }

    @Test
    fun `menu grows from 0_92 and reduced motion does not scale`() {
        val menu = SancaiMenu(context).add("One")
        menu.show(anchor())
        val host = menu.showingPanel!!
        assertThat(host.scaleX).isWithin(0.001f).of(0.92f)
        settle()
        assertThat(host.scaleX).isEqualTo(1f)
        assertThat(host.alpha).isEqualTo(1f)

        val calm = SancaiMenu(Snapshots.context(SancaiTheme.light().copy(reduceMotion = true))).add("One")
        calm.show(anchor())
        assertThat(calm.showingPanel!!.scaleX).isEqualTo(1f)
        calm.dismiss()
        assertThat(calm.showingPanel!!.scaleX).isEqualTo(1f)
        settle()
        assertThat(calm.isShowing).isFalse()
    }

    @Test
    fun `platform dismissal and a detaching anchor close the menu without running actions`() {
        var runs = 0
        var dismissed = 0
        val menu = SancaiMenu(context).add("One") { runs++ }.setOnDismissListener { dismissed++ }
        val opener = anchor()
        menu.show(opener)
        settle()
        // What outside touch and Back do.
        org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication()).latestPopupWindow.dismiss()
        assertThat(menu.isShowing).isTrue()
        settle()
        assertThat(menu.isShowing).isFalse()
        assertThat(dismissed).isEqualTo(1)

        menu.show(opener)
        (opener.parent as android.view.ViewGroup).removeView(opener)
        assertThat(menu.isShowing).isFalse()
        assertThat(dismissed).isEqualTo(2)
        assertThat(runs).isEqualTo(0)
    }

    private fun board(theme: SancaiTheme): View {
        val themed = Snapshots.context(theme)
        val row = android.widget.LinearLayout(themed)
        row.setBackgroundColor(theme.colors.canvas)
        row.setPadding(px(16f), px(16f), px(16f), px(16f))
        val icons = sample(theme).setEnabled(2, false).buildPanel()
        val plain = SancaiMenu(themed)
            .add("Light").add("Dark").add("Follow system")
            .addDivider()
            .addDanger("Reset")
            .setChecked(2)
            .buildPanel()
        val gap = android.widget.LinearLayout.LayoutParams(-2, -2)
        gap.marginEnd = px(16f)
        row.addView(icons, gap)
        row.addView(plain, android.widget.LinearLayout.LayoutParams(-2, -2))
        return row
    }

    @Test
    fun `a menu taller than the window stays inside it and scrolls`() {
        val menu = SancaiMenu(context)
        repeat(40) { menu.add("Item $it") }
        val opener = anchor()
        menu.show(opener)
        settle()
        val panel = menu.showingPanel!!
        val host = panel.parent as android.widget.ScrollView
        val window = opener.rootView.height
        assertThat(host.height).isAtMost(window)
        assertThat(host.height).isLessThan(panel.height)
        assertThat(menu.placement!!.y).isAtLeast(0)
        assertThat(menu.placement!!.y + host.height).isAtMost(window)
        menu.dismiss()
        settle()
        assertThat(menu.isShowing).isFalse()
    }

    @Test
    fun `snapshot of menu`() {
        Snapshots.capture(board(SancaiTheme.light()), "menu_light", 411)
        Snapshots.capture(board(SancaiTheme.dark()), "menu_dark", 411)
    }
}
