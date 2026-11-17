package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiNavBarTest {

    private fun bar(
        mode: SancaiNavBar.Mode = SancaiNavBar.Mode.LAUNCHER,
        theme: SancaiTheme = SancaiTheme.light(),
        rtl: Boolean = false,
        labels: List<String> = listOf("HHH", "HHH", "HHH"),
    ): SancaiNavBar {
        if (rtl) {
            val app = ApplicationProvider.getApplicationContext<Context>()
            app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        }
        return SancaiNavBar(Snapshots.context(theme)).apply {
            id = View.generateViewId()
            if (rtl) layoutDirection = View.LAYOUT_DIRECTION_RTL
            this.mode = mode
            val icons = listOf(SancaiIcon.BOOK, SancaiIcon.SLIDERS, SancaiIcon.MORE_CIRCLE)
            labels.forEachIndexed { i, label -> addItem(icons[i % icons.size], label) }
            Snapshots.layout(this, WIDTH_DP)
        }
    }

    private val View.density: Float get() = resources.displayMetrics.density

    /** Horizontal centre of the item drawn in [slot], counted from the left. */
    private fun SancaiNavBar.slotCentre(slot: Int, count: Int = 3): Float {
        val left = 18f * density
        return left + (width - 36f * density) / count * (slot + 0.5f)
    }

    /** Pixels of the item drawn in [slot] between two heights given in dp. */
    private fun SancaiNavBar.pixels(bitmap: Bitmap, slot: Int, fromDp: Float, toDp: Float, count: Int = 3): List<Int> {
        val cell = (width - 36f * density) / count
        val x0 = (slotCentre(slot, count) - cell / 2f).toInt() + 1
        val x1 = (slotCentre(slot, count) + cell / 2f).toInt() - 1
        val out = ArrayList<Int>()
        for (y in (fromDp * density).toInt() until (toDp * density).toInt().coerceAtMost(bitmap.height)) {
            for (x in x0 until x1) out.add(bitmap.getPixel(x, y))
        }
        return out
    }

    /** First and last row (in px) of the item in [slot] that is not plain canvas, within a dp band. */
    private fun SancaiNavBar.inkRows(bitmap: Bitmap, slot: Int, fromDp: Float, toDp: Float): Pair<Int, Int> {
        val canvas = SancaiTheme.of(context).colors.canvas
        val half = 16f * density
        var first = -1
        var last = -1
        for (y in (fromDp * density).toInt() until (toDp * density).toInt()) {
            for (x in (slotCentre(slot) - half).toInt()..(slotCentre(slot) + half).toInt()) {
                if (bitmap.getPixel(x, y) != canvas) {
                    if (first < 0) first = y
                    last = y
                    break
                }
            }
        }
        return first to last
    }

    private fun SancaiNavBar.press(code: Int): Boolean = onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))

    // --- layout -----------------------------------------------------------------------------

    @Test
    fun `row is 72dp high and items share the width equally inside 18dp margins`() {
        val view = bar()
        assertThat(view.height / view.density).isWithin(0.5f).of(72f)
        val provider = view.accessibilityNodeProvider
        assertThat(provider).isNotNull()
        val rects = (0..2).map { index ->
            val node = provider.createAccessibilityNodeInfo(index)
            assertThat(node).isNotNull()
            android.graphics.Rect().also { @Suppress("DEPRECATION") node!!.getBoundsInParent(it) }
        }
        assertThat(rects[0].left.toFloat()).isWithin(1f).of(18f * view.density)
        assertThat(rects[2].right.toFloat()).isWithin(1f).of(view.width - 18f * view.density)
        assertThat(rects[1].width().toFloat()).isWithin(1.5f).of(rects[0].width().toFloat())
        assertThat(rects[2].width().toFloat()).isWithin(1.5f).of(rects[0].width().toFloat())
        assertThat(rects[1].left).isEqualTo(rects[0].right)
    }

    @Test
    fun `icon centre lies 21dp and label baseline 57dp below the top`() {
        val view = bar()
        val bitmap = Snapshots.render(view, WIDTH_DP)
        // Slot 2 is the circle icon: its outline is symmetric around its centre.
        val (iconTop, iconBottom) = view.inkRows(bitmap, 2, 0f, 40f)
        assertThat((iconTop + iconBottom + 1) / 2f).isWithin(1.5f).of(21f * view.density)
        // As in the original app the circle is 24 dp across, plus its 1.7 dp stroke.
        assertThat((iconBottom + 1 - iconTop) / view.density).isWithin(0.8f).of(25.7f)
        // "HHH" has no descender: its lowest row sits on the baseline.
        val (_, labelBottom) = view.inkRows(bitmap, 2, 40f, 72f)
        assertThat((labelBottom + 1).toFloat()).isWithin(1.5f).of(57f * view.density)
    }

    @Test
    fun `launcher mode draws every item muted and selects nothing`() {
        val view = bar()
        val colors = SancaiTheme.light().colors
        val bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(view.selectedIndex).isEqualTo(-1)
        for (slot in 0..2) {
            val label = view.pixels(bitmap, slot, 44f, 60f)
            assertThat(label).contains(colors.muted)
            assertThat(label).doesNotContain(colors.ink)
        }
        // Nothing but canvas between the items and around them: no bar, no pill, no line.
        assertThat(bitmap.getPixel(0, 0)).isEqualTo(colors.canvas)
        assertThat(bitmap.getPixel(view.width / 3, (66f * view.density).toInt())).isEqualTo(colors.canvas)
    }
    // --- behaviour --------------------------------------------------------------------------

    @Test
    fun `launcher mode calls back on every tap, also twice on the same item`() {
        val view = bar()
        val taps = ArrayList<Int>()
        view.setOnItemSelectedListener { _, index -> taps.add(index) }
        Touch.tap(view, view.slotCentre(1))
        Touch.tap(view, view.slotCentre(1))
        Touch.tap(view, view.slotCentre(2))
        assertThat(taps).containsExactly(1, 1, 2).inOrder()
        assertThat(view.selectedIndex).isEqualTo(-1)
    }

    @Test
    fun `tabs mode selects on tap, reports a reselect separately and paints the selection in ink`() {
        val view = bar(SancaiNavBar.Mode.TABS)
        val colors = SancaiTheme.light().colors
        val selections = ArrayList<Int>()
        val reselections = ArrayList<Int>()
        view.setOnItemSelectedListener { _, index -> selections.add(index) }
        view.setOnItemReselectedListener { _, index -> reselections.add(index) }
        assertThat(view.selectedIndex).isEqualTo(0)

        Touch.tap(view, view.slotCentre(2))
        assertThat(view.selectedIndex).isEqualTo(2)
        Touch.tap(view, view.slotCentre(2))
        assertThat(selections).containsExactly(2)
        assertThat(reselections).containsExactly(2)

        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS)
        val bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(view.pixels(bitmap, 2, 44f, 60f)).contains(colors.ink)
        assertThat(view.pixels(bitmap, 2, 44f, 60f)).doesNotContain(colors.muted)
        assertThat(view.pixels(bitmap, 2, 8f, 34f)).contains(colors.ink)
        assertThat(view.pixels(bitmap, 0, 44f, 60f)).contains(colors.muted)
        assertThat(view.pixels(bitmap, 0, 0f, 72f)).doesNotContain(colors.ink)
    }

    @Test
    fun `setting the selection from code never calls the listeners`() {
        val view = bar(SancaiNavBar.Mode.TABS)
        var calls = 0
        view.setOnItemSelectedListener { _, _ -> calls++ }
        view.setOnItemReselectedListener { _, _ -> calls++ }
        view.select(1)
        view.selectedIndex = 2
        view.select(99, animate = false)
        assertThat(view.selectedIndex).isEqualTo(2)
        assertThat(calls).isEqualTo(0)
        // Rolling back inside the listener is safe.
        view.setOnItemSelectedListener { nav, _ ->
            calls++
            nav.select(2, animate = false)
        }
        Touch.tap(view, view.slotCentre(0))
        assertThat(calls).isEqualTo(1)
        assertThat(view.selectedIndex).isEqualTo(2)
    }

    @Test
    fun `a finger that leaves the item or a disabled row does nothing`() {
        val view = bar(SancaiNavBar.Mode.TABS)
        var calls = 0
        view.setOnItemSelectedListener { _, _ -> calls++ }
        Touch.drag(view, view.slotCentre(1), view.height / 2f, view.slotCentre(2), view.height / 2f)
        assertThat(calls).isEqualTo(0)
        assertThat(view.selectedIndex).isEqualTo(0)
        view.isEnabled = false
        Touch.tap(view, view.slotCentre(1))
        view.press(KeyEvent.KEYCODE_ENTER)
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(calls).isEqualTo(0)
        assertThat(view.selectedIndex).isEqualTo(0)
    }
    @Test
    fun `arrow keys walk the items and enter activates the one under the cursor`() {
        val view = bar(SancaiNavBar.Mode.TABS)
        val selections = ArrayList<Int>()
        view.setOnItemSelectedListener { _, index -> selections.add(index) }
        // The cursor starts on the first item; moving it does not select yet.
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(view.selectedIndex).isEqualTo(0)
        assertThat(view.press(KeyEvent.KEYCODE_ENTER)).isTrue()
        assertThat(view.selectedIndex).isEqualTo(1)
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(view.press(KeyEvent.KEYCODE_SPACE)).isTrue()
        // At the end the key is not consumed, so focus can move on.
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(view.press(KeyEvent.KEYCODE_A)).isFalse()
        assertThat(selections).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `right to left reverses the order for touch and keys`() {
        val view = bar(SancaiNavBar.Mode.TABS, rtl = true)
        val colors = SancaiTheme.light().colors
        // The first item is drawn in the rightmost slot.
        val bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(view.pixels(bitmap, 2, 44f, 60f)).contains(colors.ink)
        assertThat(view.pixels(bitmap, 0, 44f, 60f)).doesNotContain(colors.ink)
        Touch.tap(view, view.slotCentre(0))
        assertThat(view.selectedIndex).isEqualTo(2)
        val keys = bar(SancaiNavBar.Mode.TABS, rtl = true)
        assertThat(keys.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(keys.press(KeyEvent.KEYCODE_DPAD_LEFT)).isTrue()
        assertThat(keys.press(KeyEvent.KEYCODE_ENTER)).isTrue()
        assertThat(keys.selectedIndex).isEqualTo(1)
    }

    @Test
    fun `selection survives save and restore without calling the listener`() {
        val view = bar(SancaiNavBar.Mode.TABS)
        view.select(2, animate = false)
        val container = SparseArray<Parcelable>()
        view.saveHierarchyState(container)

        val restored = bar(SancaiNavBar.Mode.TABS)
        restored.id = view.id
        var calls = 0
        restored.setOnItemSelectedListener { _, _ -> calls++ }
        restored.restoreHierarchyState(container)
        assertThat(restored.selectedIndex).isEqualTo(2)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `items can be replaced and the selection stays valid`() {
        val view = bar(SancaiNavBar.Mode.TABS)
        view.select(2, animate = false)
        view.setBadge(2, 4)
        view.setItems(listOf(SancaiNavBar.Item(SancaiIcon.BOOK, "A"), SancaiNavBar.Item(SancaiIcon.SLIDERS, "B")))
        assertThat(view.items).hasSize(2)
        assertThat(view.selectedIndex).isEqualTo(1)
        assertThat(view.badgeCount(1)).isEqualTo(0)
        assertThat(view.addItem(SancaiIcon.MORE_CIRCLE, "C")).isEqualTo(2)
        view.mode = SancaiNavBar.Mode.LAUNCHER
        assertThat(view.selectedIndex).isEqualTo(-1)
    }
    // --- badge ------------------------------------------------------------------------------

    @Test
    fun `badge writes the count, caps it at 99 and shrinks to a dot for zero`() {
        val badge = SancaiBadge(Snapshots.context())
        val density = badge.resources.displayMetrics.density
        assertThat(badge.text).isEmpty()
        assertThat(badge.isShowing).isFalse()
        Snapshots.layout(badge, 100)
        assertThat(badge.measuredHeight).isEqualTo(0)

        badge.count = 5
        assertThat(badge.text).isEqualTo("5")
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        badge.measure(unspecified, unspecified)
        assertThat(badge.measuredHeight / density).isWithin(0.5f).of(18f)
        assertThat(badge.measuredWidth).isEqualTo(badge.measuredHeight)

        badge.count = 120
        assertThat(badge.text).isEqualTo("99+")
        assertThat(badge.contentDescription.toString()).isEqualTo("99+")
        badge.measure(unspecified, unspecified)
        assertThat(badge.measuredWidth).isGreaterThan(badge.measuredHeight)

        badge.count = 0
        badge.showDot = true
        assertThat(badge.text).isEmpty()
        assertThat(badge.isShowing).isTrue()
        badge.measure(unspecified, unspecified)
        assertThat(badge.measuredWidth / density).isWithin(0.5f).of(8f)
        assertThat(badge.measuredHeight / density).isWithin(0.5f).of(8f)
    }

    @Test
    fun `badge is glazed in the exact danger colour with white digits`() {
        val badge = SancaiBadge(Snapshots.context()).apply { count = 120 }
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        badge.measure(unspecified, unspecified)
        badge.layout(0, 0, badge.measuredWidth, badge.measuredHeight)
        val bitmap = Bitmap.createBitmap(badge.width, badge.height, Bitmap.Config.ARGB_8888)
        badge.draw(android.graphics.Canvas(bitmap))
        val danger = SancaiTheme.light().colors.danger
        // Just inside the top of the capsule there is glaze and no text.
        assertThat(bitmap.getPixel(badge.width / 2, 2)).isEqualTo(danger)
        val row = (0 until badge.width).map { bitmap.getPixel(it, badge.height / 2) }
        assertThat(row).contains(0xFFFFFFFF.toInt())
    }

    @Test
    fun `attached badge hangs on the top end corner of its anchor`() {
        val anchor = FrameLayout(Snapshots.context())
        Snapshots.layout(anchor, 60, 60)
        val canvas = SancaiTheme.light().colors.canvas
        val danger = SancaiTheme.light().colors.danger
        val badge = SancaiBadge.attach(anchor)
        badge.showDot = true
        var bitmap = Snapshots.render(anchor, 60, 60)
        assertThat(bitmap.getPixel(anchor.width - 10, 10)).isEqualTo(danger)
        assertThat(bitmap.getPixel(10, 10)).isEqualTo(canvas)
        badge.detach()
        bitmap = Snapshots.render(anchor, 60, 60)
        assertThat(bitmap.getPixel(anchor.width - 10, 10)).isEqualTo(canvas)
    }

    @Test
    fun `nav bar badge sits at the top end of the icon and is spoken with the label`() {
        val view = bar()
        val danger = SancaiTheme.light().colors.danger
        view.setBadge(1, 5)
        view.setBadgeDot(2, true)
        assertThat(view.badgeCount(1)).isEqualTo(5)
        assertThat(view.hasBadgeDot(2)).isTrue()
        val bitmap = Snapshots.render(view, WIDTH_DP)
        fun dangerColumns(slot: Int): List<Int> = (0 until view.width).filter { x ->
            x > view.slotCentre(slot) - 30f * view.density && x < view.slotCentre(slot) + 30f * view.density &&
                (0 until (30f * view.density).toInt()).any { y -> bitmap.getPixel(x, y) == danger }
        }
        for (slot in 1..2) {
            val columns = dangerColumns(slot)
            assertThat(columns).isNotEmpty()
            // To the right of the icon centre, and hanging on the icon rather than floating away.
            assertThat(columns.first().toFloat()).isGreaterThan(view.slotCentre(slot))
            assertThat(columns.first().toFloat()).isLessThan(view.slotCentre(slot) + 12f * view.density)
        }
        assertThat(dangerColumns(0)).isEmpty()

        val provider = view.accessibilityNodeProvider
        assertThat(provider).isNotNull()
        assertThat(provider.createAccessibilityNodeInfo(1)?.contentDescription.toString()).isEqualTo("HHH, 5 new")
        assertThat(provider.createAccessibilityNodeInfo(2)?.contentDescription.toString()).isEqualTo("HHH, new")
        view.setBadge(1, 0)
        view.setBadgeDot(2, false)
        assertThat(view.pixels(Snapshots.render(view, WIDTH_DP), 1, 0f, 30f)).doesNotContain(danger)
        assertThat(provider.createAccessibilityNodeInfo(1)?.contentDescription.toString()).isEqualTo("HHH")
    }
    // --- accessibility, chrome --------------------------------------------------------------

    @Test
    fun `items are tabs with selected state in tabs mode and buttons in launcher mode`() {
        val tabs = bar(SancaiNavBar.Mode.TABS)
        tabs.select(1, animate = false)
        val provider = tabs.accessibilityNodeProvider
        assertThat(provider).isNotNull()
        val selected = provider.createAccessibilityNodeInfo(1)
        val other = provider.createAccessibilityNodeInfo(0)
        assertThat(selected).isNotNull()
        assertThat(other).isNotNull()
        assertThat(selected!!.isSelected).isTrue()
        assertThat(other!!.isSelected).isFalse()
        assertThat(selected.className.toString()).contains("Tab")
        var picked = -1
        tabs.setOnItemSelectedListener { _, index -> picked = index }
        assertThat(provider.performAction(2, android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null)).isTrue()
        assertThat(picked).isEqualTo(2)
        assertThat(tabs.selectedIndex).isEqualTo(2)

        val launcher = bar()
        val button = launcher.accessibilityNodeProvider.createAccessibilityNodeInfo(0)
        assertThat(button).isNotNull()
        assertThat(button!!.className.toString()).isEqualTo("android.widget.Button")
        assertThat(button.isSelected).isFalse()
    }

    @Test
    fun `elevated draws a full-bleed hairline along the top edge only`() {
        val view = bar()
        val colors = SancaiTheme.light().colors
        assertThat(Snapshots.render(view, WIDTH_DP).getPixel(0, 0)).isEqualTo(colors.canvas)
        view.elevated = true
        val bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(bitmap.getPixel(0, 0)).isEqualTo(colors.divider)
        assertThat(bitmap.getPixel(view.width - 1, 0)).isEqualTo(colors.divider)
        assertThat(bitmap.getPixel(view.width / 2, (4f * view.density).toInt())).isEqualTo(colors.canvas)
    }

    @Test
    fun `disabled pales the row without moving anything`() {
        val view = bar(SancaiNavBar.Mode.TABS)
        val colors = SancaiTheme.light().colors
        val enabled = Snapshots.render(view, WIDTH_DP)
        view.isEnabled = false
        val disabled = Snapshots.render(view, WIDTH_DP)
        assertThat(view.pixels(disabled, 0, 0f, 72f)).doesNotContain(colors.ink)
        assertThat(view.inkRows(disabled, 2, 0f, 40f)).isEqualTo(view.inkRows(enabled, 2, 0f, 40f))
    }
    @Test
    fun `pressing sinks the item content by one and a half dp`() {
        val view = bar(theme = SancaiTheme.light().copy(reduceMotion = true))
        val rest = view.inkRows(Snapshots.render(view, WIDTH_DP), 1, 40f, 72f)
        Touch.dispatch(view, android.view.MotionEvent.ACTION_DOWN, view.slotCentre(1), view.height / 2f)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(SancaiTheme.light().colors.canvas)
        view.draw(android.graphics.Canvas(bitmap))
        val sunk = view.inkRows(bitmap, 1, 40f, 72f)
        assertThat((sunk.second - rest.second).toFloat()).isWithin(1.1f).of(1.5f * view.density)
        // The neighbours stay where they are.
        assertThat(view.inkRows(bitmap, 0, 40f, 72f)).isEqualTo(rest)
    }

    @Test
    fun `a row disabled under the finger lets the item rise at once`() {
        val view = bar(theme = SancaiTheme.light().copy(reduceMotion = true))
        val rest = view.inkRows(Snapshots.render(view, WIDTH_DP), 1, 40f, 72f)
        var picked = -1
        view.setOnItemSelectedListener { _, index -> picked = index }
        Touch.dispatch(view, android.view.MotionEvent.ACTION_DOWN, view.slotCentre(1), view.height / 2f)
        view.isEnabled = false
        Touch.dispatch(view, android.view.MotionEvent.ACTION_UP, view.slotCentre(1), view.height / 2f)
        assertThat(view.inkRows(Snapshots.render(view, WIDTH_DP), 1, 40f, 72f)).isEqualTo(rest)
        view.isEnabled = true
        assertThat(view.inkRows(Snapshots.render(view, WIDTH_DP), 1, 40f, 72f)).isEqualTo(rest)
        assertThat(picked).isEqualTo(-1)
    }

    @Test
    fun `a held confirm key activates once and its repeats are swallowed`() {
        val view = bar()
        var calls = 0
        view.setOnItemSelectedListener { _, _ -> calls++ }
        fun down(repeat: Int): Boolean =
            view.onKeyDown(KeyEvent.KEYCODE_ENTER, KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, repeat))
        assertThat(down(0)).isTrue()
        assertThat(down(1)).isTrue()
        assertThat(down(2)).isTrue()
        assertThat(calls).isEqualTo(1)

        val tabs = bar(SancaiNavBar.Mode.TABS)
        var reselected = 0
        tabs.setOnItemReselectedListener { _, _ -> reselected++ }
        val space = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 3)
        assertThat(tabs.onKeyDown(KeyEvent.KEYCODE_SPACE, space)).isTrue()
        assertThat(reselected).isEqualTo(0)
    }

    @Test
    fun `the row itself offers no click to accessibility, its items do`() {
        for (mode in SancaiNavBar.Mode.values()) {
            val view = bar(mode)
            // Without a window the platform fills in nothing, and the test would pass on its own.
            Snapshots.attach(view)
            val host = android.view.accessibility.AccessibilityNodeInfo.obtain()
            view.onInitializeAccessibilityNodeInfo(host)
            assertThat(host.isFocusable).isTrue()
            assertThat(host.isClickable).isFalse()
            assertThat(host.actionList)
                .doesNotContain(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            val item = view.accessibilityNodeProvider.createAccessibilityNodeInfo(0)!!
            assertThat(item.isClickable).isTrue()
            assertThat(item.actionList)
                .contains(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            // Touch still needs the view to be clickable.
            assertThat(view.isClickable).isTrue()
        }
    }

    @Test
    fun `a bar that joins a screen late asks for the window insets`() {
        var asked = 0
        val parent = object : android.widget.FrameLayout(Snapshots.context()) {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun requestFitSystemWindows() {
                asked++
                super.requestFitSystemWindows()
            }
        }
        Snapshots.attach(parent, 360)
        asked = 0
        parent.addView(SancaiNavBar(Snapshots.context()))
        assertThat(asked).isEqualTo(1)
    }

    @Test
    fun `row grows by the navigation bar inset`() {
        val view = bar()
        val bare = view.height
        val insets = androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), androidx.core.graphics.Insets.of(0, 0, 0, 40))
            .build()
        view.onApplyWindowInsets(insets.toWindowInsets()!!)
        Snapshots.layout(view, WIDTH_DP)
        assertThat(view.height).isEqualTo(bare + 40)
        view.fitNavigationBar = false
        Snapshots.layout(view, WIDTH_DP)
        assertThat(view.height).isEqualTo(bare)
    }

    @Test
    fun `long labels and large text stay inside their item`() {
        val theme = SancaiTheme.light().copy(textScale = 1.13f)
        val view = bar(theme = theme, labels = listOf("Notifications", "Preferences", "Everything else", "Help", "More"))
        val colors = theme.colors
        val bitmap = Snapshots.capture(view, "navbar_long_labels", WIDTH_DP)
        // The column between two items stays free of text.
        val cell = (view.width - 36f * view.density) / 5
        for (boundary in 1..4) {
            val x = (18f * view.density + cell * boundary).toInt()
            for (y in (44f * view.density).toInt() until (62f * view.density).toInt()) {
                assertThat(bitmap.getPixel(x, y)).isEqualTo(colors.canvas)
            }
        }
    }

    // --- review additions -------------------------------------------------------------------

    @Test
    fun `a row without items draws nothing and ignores touch, keys and stray indices`() {
        val view = bar(SancaiNavBar.Mode.TABS, labels = emptyList())
        val colors = SancaiTheme.light().colors
        var calls = 0
        view.setOnItemSelectedListener { _, _ -> calls++ }
        assertThat(view.height / view.density).isWithin(0.5f).of(72f)
        assertThat(view.selectedIndex).isEqualTo(-1)
        Touch.tap(view, view.width / 2f)
        assertThat(view.press(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        view.select(1)
        view.setBadge(0, 3)
        view.setBadgeDot(4, true)
        assertThat(view.badgeCount(0)).isEqualTo(0)
        assertThat(calls).isEqualTo(0)
        val bitmap = Snapshots.render(view, WIDTH_DP)
        for (x in 0 until view.width step 7) {
            for (y in 0 until view.height step 5) assertThat(bitmap.getPixel(x, y)).isEqualTo(colors.canvas)
        }
        // Items that arrive later work at once.
        view.addItem(SancaiIcon.BOOK, "HHH")
        Snapshots.layout(view, WIDTH_DP)
        assertThat(view.selectedIndex).isEqualTo(0)
    }

    @Test
    fun `a vertical drag takes the press back and selects nothing`() {
        val view = bar(SancaiNavBar.Mode.TABS)
        var calls = 0
        view.setOnItemSelectedListener { _, _ -> calls++ }
        val x = view.slotCentre(1)
        Touch.drag(view, x, view.height * 0.7f, x, view.height * 0.1f)
        assertThat(calls).isEqualTo(0)
        assertThat(view.selectedIndex).isEqualTo(0)
        // The next plain tap still works.
        Touch.tap(view, x)
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `large text keeps the icon and the baseline where they are`() {
        val view = bar(theme = SancaiTheme.light().copy(textScale = 1.13f))
        val plain = bar()
        assertThat(view.height).isEqualTo(plain.height)
        val bitmap = Snapshots.render(view, WIDTH_DP)
        val (_, labelBottom) = view.inkRows(bitmap, 1, 40f, 72f)
        assertThat((labelBottom + 1).toFloat()).isWithin(1.5f).of(57f * view.density)
        val (labelTop, _) = view.inkRows(bitmap, 1, 36f, 72f)
        val (plainTop, _) = plain.inkRows(Snapshots.render(plain, WIDTH_DP), 1, 36f, 72f)
        // Taller letters, and still clear of the icon, which ends 33 dp below the top.
        assertThat(labelTop).isLessThan(plainTop)
        assertThat(labelTop.toFloat()).isGreaterThan(35f * view.density)
    }

    @Test
    fun `right to left hangs the badge on the left of the icon and keeps item bounds mirrored`() {
        val view = bar(rtl = true)
        val danger = SancaiTheme.light().colors.danger
        view.setBadge(0, 5)
        val bitmap = Snapshots.render(view, WIDTH_DP)
        // Item 0 is drawn in the rightmost slot; its badge hangs towards the left, the end side.
        val centre = view.slotCentre(2)
        val columns = (0 until view.width).filter { x ->
            (0 until (30f * view.density).toInt()).any { y -> bitmap.getPixel(x, y) == danger }
        }
        assertThat(columns).isNotEmpty()
        assertThat(columns.last().toFloat()).isLessThan(centre)
        assertThat(columns.first().toFloat()).isGreaterThan(centre - 30f * view.density)
        val node = view.accessibilityNodeProvider.createAccessibilityNodeInfo(0)
        assertThat(node).isNotNull()
        val rect = android.graphics.Rect().also { @Suppress("DEPRECATION") node!!.getBoundsInParent(it) }
        assertThat(rect.right.toFloat()).isWithin(1f).of(view.width - 18f * view.density)
    }

    @Test
    fun `the ink glides to the new tab, and detaching half way leaves the final state`() {
        val view = bar(SancaiNavBar.Mode.TABS)
        val colors = SancaiTheme.light().colors
        Snapshots.attach(view, WIDTH_DP)
        Touch.tap(view, view.slotCentre(2))
        ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
        (view.parent as android.view.ViewGroup).removeView(view)
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS)
        val bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(view.pixels(bitmap, 2, 44f, 60f)).contains(colors.ink)
        assertThat(view.pixels(bitmap, 2, 44f, 60f)).doesNotContain(colors.muted)
        assertThat(view.pixels(bitmap, 0, 0f, 72f)).doesNotContain(colors.ink)
    }

    @Test
    fun `reduced motion jumps to the new selection`() {
        val view = bar(SancaiNavBar.Mode.TABS, theme = SancaiTheme.light().copy(reduceMotion = true))
        val colors = SancaiTheme.light().colors
        Snapshots.attach(view, WIDTH_DP)
        Touch.tap(view, view.slotCentre(1))
        val bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(view.pixels(bitmap, 1, 44f, 60f)).contains(colors.ink)
        assertThat(view.pixels(bitmap, 0, 0f, 72f)).doesNotContain(colors.ink)
    }

    @Test
    fun `XML attributes configure the row, and the item list cannot be changed from outside`() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(R.attr.sancaiNavBarMode, "tabs")
            .addAttribute(R.attr.sancaiOptions, "@android:array/emailAddressTypes")
            .addAttribute(R.attr.sancaiSelectedIndex, "1")
            .addAttribute(R.attr.sancaiElevated, "true")
            .addAttribute(R.attr.sancaiNavBarFitNavigationBar, "false")
            .build()
        val view = SancaiNavBar(Snapshots.context(), attrs)
        assertThat(view.fitNavigationBar).isFalse()
        assertThat(view.mode).isEqualTo(SancaiNavBar.Mode.TABS)
        assertThat(view.items.size).isAtLeast(2)
        assertThat(view.items[0].icon).isEqualTo(SancaiIcon.MORE_CIRCLE)
        assertThat(view.selectedIndex).isEqualTo(1)
        assertThat(view.elevated).isTrue()
        assertThat(view.items).isNotInstanceOf(java.util.ArrayList::class.java)
    }

    @Test
    fun `only a badge with a number is offered to accessibility`() {
        val badge = SancaiBadge(Snapshots.context())
        assertThat(badge.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        badge.count = 3
        assertThat(badge.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        badge.count = 0
        badge.showDot = true
        assertThat(badge.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        assertThat(badge.contentDescription).isNull()
    }

    // --- snapshots --------------------------------------------------------------------------

    private fun gallery(theme: SancaiTheme): View {
        val context = Snapshots.context(theme)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.colors.canvas)
        }
        fun row(mode: SancaiNavBar.Mode, configure: SancaiNavBar.() -> Unit) {
            val view = SancaiNavBar(context).apply {
                this.mode = mode
                addItem(SancaiIcon.BOOK, "Guide")
                addItem(SancaiIcon.SLIDERS, "Settings")
                addItem(SancaiIcon.MORE_CIRCLE, "More")
                configure()
            }
            column.addView(view, LinearLayout.LayoutParams(-1, -2))
        }
        row(SancaiNavBar.Mode.LAUNCHER) {}
        row(SancaiNavBar.Mode.TABS) {
            elevated = true
            select(1, animate = false)
            setBadge(0, 5)
            setBadgeDot(2, true)
        }
        row(SancaiNavBar.Mode.TABS) {
            elevated = true
            setBadge(1, 120)
            setBadge(2, 18)
        }
        row(SancaiNavBar.Mode.TABS) {
            elevated = true
            select(2, animate = false)
            isEnabled = false
        }
        return column
    }

    @Test
    fun `snapshot of nav bar in light and dark`() {
        Snapshots.capture(gallery(SancaiTheme.light()), "navbar_light", WIDTH_DP)
        Snapshots.capture(gallery(SancaiTheme.dark()), "navbar_dark", WIDTH_DP)
    }

    private companion object {
        const val WIDTH_DP = 360
    }
}
