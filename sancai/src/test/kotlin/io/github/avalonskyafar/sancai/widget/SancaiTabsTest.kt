package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.graphics.RectF
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.interaction.SancaiPager
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiTabsTest {

    private val context: Context = Snapshots.context(SancaiTheme.light())

    private fun tabs(
        vararg labels: String,
        context: Context = this.context,
        layout: SancaiTabs.Layout = SancaiTabs.Layout.FILL,
        widthDp: Int = 360,
    ): SancaiTabs = SancaiTabs(context).apply {
        this.layout = layout
        setTabs(*labels)
        Snapshots.layout(this, widthDp)
    }

    private fun indicator(view: SancaiTabs): RectF = RectF().also { view.indicatorBounds(it) }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    /** Layout direction is only honoured for applications that declare RTL support. */
    private fun supportRtl() {
        val info = context.applicationContext.applicationInfo
        info.flags = info.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
    }

    @Test
    fun `is 44 dp high and gives equal widths in fill layout`() {
        val view = tabs("Recent", "All", "Starred")
        assertThat(view.height).isEqualTo(Math.round(dp(44f)))
        val provider = checkNotNull(view.accessibilityNodeProvider)
        val widths = (0..2).map { index ->
            val bounds = android.graphics.Rect()
            @Suppress("DEPRECATION")
            checkNotNull(provider.createAccessibilityNodeInfo(index)).getBoundsInParent(bounds)
            bounds.width()
        }
        assertThat(widths.max() - widths.min()).isAtMost(1)
        assertThat(widths.sum()).isWithin(2).of(view.width)
    }

    @Test
    fun `a tap selects and tells the listener but select does not`() {
        val view = tabs("Recent", "All", "Starred")
        val heard = mutableListOf<Int>()
        view.setOnTabSelectedListener { _, index -> heard += index }
        Touch.tap(view, view.width * 5f / 6f)
        assertThat(view.selectedIndex).isEqualTo(2)
        Touch.tap(view, view.width * 5f / 6f)
        assertThat(heard).containsExactly(2)
        view.select(0, animate = false)
        assertThat(view.selectedIndex).isEqualTo(0)
        assertThat(heard).containsExactly(2)
    }

    private fun labelWidth(label: String): Float {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG)
        paint.typeface = io.github.avalonskyafar.sancai.theme.SancaiWeight.MEDIUM.typeface()
        paint.textSize = 14.5f * context.resources.displayMetrics.scaledDensity * SancaiTheme.of(context).textScale
        return paint.measureText(label)
    }

    @Test
    fun `indicator is a 3 dp capsule as wide as the label and centred under it`() {
        val view = tabs("Recent", "All", "Starred")
        val cell = view.width / 3f
        val rest = indicator(view)
        assertThat(rest.centerX()).isWithin(0.6f).of(cell / 2f)
        assertThat(rest.width()).isWithin(0.6f).of(labelWidth("Recent"))
        assertThat(rest.height()).isWithin(0.01f).of(dp(3f))
        // The capsule rests on the 1 dp hairline.
        assertThat(rest.bottom).isWithin(0.01f).of(view.height - dp(1f))

        view.select(1, animate = false)
        val second = indicator(view)
        assertThat(second.centerX()).isWithin(0.6f).of(cell * 1.5f)
        assertThat(second.width()).isWithin(0.6f).of(labelWidth("All"))
    }

    @Test
    fun `indicator stands half way between two tabs for a scroll position`() {
        val view = tabs("Recent", "All", "Starred")
        val cell = view.width / 3f
        view.setScrollPosition(1, 0.5f)
        val half = indicator(view)
        assertThat(half.centerX()).isWithin(0.6f).of(cell * 2f)
        assertThat(half.width()).isWithin(0.6f).of((labelWidth("All") + labelWidth("Starred")) / 2f)
        // Following a position is not a selection.
        assertThat(view.selectedIndex).isEqualTo(0)
        view.setScrollPosition(2, 0.7f)
        assertThat(indicator(view).centerX()).isWithin(0.6f).of(cell * 2.5f)
    }

    @Test
    fun `indicator is accent and the hairline runs from edge to edge`() {
        for (theme in listOf(SancaiTheme.light(), SancaiTheme.dark())) {
            val themed = Snapshots.context(theme)
            val view = tabs("Recent", "All", "Starred", context = themed)
            view.setPadding(40, 0, 40, 0)
            val bitmap = Snapshots.render(view, 360)
            val bar = indicator(view)
            assertThat(bitmap.getPixel(bar.centerX().toInt(), view.height - 4)).isEqualTo(theme.colors.accent)
            // Full-bleed: the padding does not shorten the line.
            assertThat(bitmap.getPixel(1, view.height - 1)).isEqualTo(theme.colors.divider)
            assertThat(bitmap.getPixel(view.width - 2, view.height - 1)).isEqualTo(theme.colors.divider)
            assertThat(bitmap.getPixel(view.width / 2, view.height - 1)).isEqualTo(theme.colors.divider)
        }
    }

    @Test
    fun `selected label is ink and the others muted`() {
        val view = tabs("Recent", "All", "Starred")
        val bitmap = Snapshots.render(view, 360)
        val colors = SancaiTheme.light().colors
        fun darkest(tab: Int): Int {
            val cell = view.width / 3
            var best = 255 * 3
            for (x in cell * tab until cell * (tab + 1)) for (y in 0 until view.height - 12) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) < 255) continue
                best = minOf(best, android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel))
            }
            return best
        }
        fun sum(color: Int) = android.graphics.Color.red(color) + android.graphics.Color.green(color) + android.graphics.Color.blue(color)
        assertThat(darkest(0)).isWithin(24).of(sum(colors.ink))
        assertThat(darkest(1)).isWithin(24).of(sum(colors.muted))
    }

    private val many = Array(10) { "Section ${it + 1}" }

    @Test
    fun `scroll layout uses natural widths and keeps the selection visible`() {
        val view = tabs(*many, layout = SancaiTabs.Layout.SCROLL, widthDp = 300)
        assertThat(view.scrollOffset).isEqualTo(0f)
        assertThat(indicator(view).width()).isWithin(0.6f).of(labelWidth("Section 1"))

        view.select(8, animate = false)
        val far = indicator(view)
        assertThat(view.scrollOffset).isGreaterThan(0f)
        assertThat(far.left).isAtLeast(0f)
        assertThat(far.right).isAtMost(view.width.toFloat())
        // Natural width: the label plus 16 dp on either side.
        val provider = checkNotNull(view.accessibilityNodeProvider)
        val bounds = android.graphics.Rect()
        @Suppress("DEPRECATION")
        checkNotNull(provider.createAccessibilityNodeInfo(8)).getBoundsInParent(bounds)
        assertThat(bounds.width().toFloat()).isWithin(1.5f).of(labelWidth("Section 9") + dp(32f))
        assertThat(bounds.left).isAtLeast(0)
        assertThat(bounds.right).isAtMost(view.width)

        // A tap lands on the tab that is under the finger after scrolling.
        Touch.tap(view, far.centerX())
        assertThat(view.selectedIndex).isEqualTo(8)
        view.select(9, animate = false)
        assertThat(indicator(view).right).isAtMost(view.width.toFloat())
        view.select(0, animate = false)
        assertThat(view.scrollOffset).isEqualTo(0f)
    }

    @Test
    fun `a scrolling row follows the finger without changing the selection`() {
        val view = tabs(*many, layout = SancaiTabs.Layout.SCROLL, widthDp = 300)
        val heard = mutableListOf<Int>()
        view.setOnTabSelectedListener { _, index -> heard += index }
        val y = view.height / 2f
        Touch.drag(view, view.width * 0.8f, y, view.width * 0.2f, y)
        assertThat(view.scrollOffset).isGreaterThan(view.width * 0.4f)
        assertThat(view.selectedIndex).isEqualTo(0)
        assertThat(heard).isEmpty()
        // Tabs that fit do not scroll at all.
        val few = tabs("One", "Two", layout = SancaiTabs.Layout.SCROLL)
        Touch.drag(few, few.width * 0.8f, y, few.width * 0.2f, y)
        assertThat(few.scrollOffset).isEqualTo(0f)
        assertThat(few.selectedIndex).isEqualTo(0)
    }

    @Test
    fun `arrow keys move the selection and mirror in rtl`() {
        val view = tabs("Recent", "All", "Starred")
        val heard = mutableListOf<Int>()
        view.setOnTabSelectedListener { _, index -> heard += index }
        fun press(target: SancaiTabs, code: Int) = target.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
        assertThat(press(view, KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(press(view, KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(press(view, KeyEvent.KEYCODE_MOVE_END)).isTrue()
        assertThat(press(view, KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(heard).containsExactly(1, 2).inOrder()

        supportRtl()
        val rtl = SancaiTabs(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setTabs("Recent", "All", "Starred")
            Snapshots.layout(this, 360)
        }
        // The first tab stands at the right.
        assertThat(indicator(rtl).centerX()).isWithin(0.6f).of(rtl.width * 5f / 6f)
        assertThat(press(rtl, KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(press(rtl, KeyEvent.KEYCODE_DPAD_LEFT)).isTrue()
        assertThat(rtl.selectedIndex).isEqualTo(1)
        Touch.tap(rtl, rtl.width / 6f)
        assertThat(rtl.selectedIndex).isEqualTo(2)
    }

    @Test
    fun `selection survives save and restore without calling the listener`() {
        val view = tabs("Recent", "All", "Starred").apply { id = 7 }
        view.select(2, animate = false)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)

        val restored = tabs("Recent", "All", "Starred").apply { id = 7 }
        val heard = mutableListOf<Int>()
        restored.setOnTabSelectedListener { _, index -> heard += index }
        restored.restoreHierarchyState(saved)
        assertThat(restored.selectedIndex).isEqualTo(2)
        assertThat(indicator(restored).centerX()).isWithin(0.6f).of(restored.width * 5f / 6f)
        assertThat(heard).isEmpty()
    }

    @Test
    fun `is a tab list for accessibility`() {
        val view = tabs("Recent", "All", "Starred")
        view.select(1, animate = false)
        assertThat(view.accessibilityClassName.toString()).isEqualTo("android.widget.TabWidget")
        assertThat(androidx.core.view.ViewCompat.getStateDescription(view).toString()).isEqualTo("All, tab 2 of 3")
        val provider = checkNotNull(view.accessibilityNodeProvider)
        val selected = checkNotNull(provider.createAccessibilityNodeInfo(1))
        val other = checkNotNull(provider.createAccessibilityNodeInfo(2))
        assertThat(selected.isSelected).isTrue()
        assertThat(selected.text.toString()).isEqualTo("All")
        assertThat(other.isSelected).isFalse()
        assertThat(other.className.toString()).isEqualTo("android.app.ActionBar\$Tab")

        val heard = mutableListOf<Int>()
        view.setOnTabSelectedListener { _, index -> heard += index }
        assertThat(provider.performAction(2, android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null)).isTrue()
        assertThat(view.selectedIndex).isEqualTo(2)
        assertThat(heard).containsExactly(2)
    }

    @Test
    fun `disabled tabs ignore touch and keys`() {
        val view = tabs("Recent", "All", "Starred")
        view.isEnabled = false
        Touch.tap(view, view.width * 5f / 6f)
        assertThat(view.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))).isFalse()
        assertThat(view.selectedIndex).isEqualTo(0)
    }

    @Test
    fun `replacing the tabs keeps a valid selection and clamps an invalid one`() {
        val view = tabs("Recent", "All", "Starred")
        view.select(2, animate = false)
        view.setTabs(listOf("One", "Two", "Three", "Four"))
        assertThat(view.selectedIndex).isEqualTo(2)
        view.setTabs("One", "Two")
        assertThat(view.selectedIndex).isEqualTo(1)
        view.setTabs()
        assertThat(view.selectedIndex).isEqualTo(0)
        Snapshots.render(view, 360) // an empty row still draws its hairline
    }

    @Test
    fun `indicator glides to the new tab`() {
        val view = SancaiTabs(context).apply { setTabs("Recent", "All", "Starred") }
        Snapshots.attach(view, 360)
        Snapshots.idle()
        val cell = view.width / 3f
        view.select(2)
        // The test looper does not stop a glide half way reliably, so only its landing is
        // asserted; the half-way geometry is covered through setScrollPosition.
        assertThat(view.selectedIndex).isEqualTo(2)
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        assertThat(indicator(view).centerX()).isWithin(0.6f).of(cell * 2.5f)
    }

    private fun withPager(heard: MutableList<Int>): Pair<SancaiTabs, SancaiPager> {
        val view = SancaiTabs(context).apply { setTabs("Recent", "All", "Starred") }
        view.setOnTabSelectedListener { _, index -> heard += index }
        val pager = SancaiPager(context)
        pager.setPages(3) { _, _ -> View(context).apply { minimumHeight = dp(120f).toInt() } }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(pager, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(120f).toInt()))
        }
        Snapshots.attach(column, 360)
        Snapshots.idle()
        view.attachTo(pager)
        return view to pager
    }

    @Test
    fun `choosing a tab glides the attached pager and the indicator follows it`() {
        val heard = mutableListOf<Int>()
        val (view, pager) = withPager(heard)
        val cell = view.width / 3f
        Touch.tap(view, cell * 2.5f)
        assertThat(view.selectedIndex).isEqualTo(2)
        assertThat(pager.currentPage).isEqualTo(2)
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS)
        assertThat(pager.isMoving).isFalse()
        assertThat(indicator(view).centerX()).isWithin(0.6f).of(cell * 2.5f)
        assertThat(heard).containsExactly(2)
    }

    @Test
    fun `tabs follow a swipe and a programmatic move of the attached pager`() {
        val heard = mutableListOf<Int>()
        val (view, pager) = withPager(heard)
        val cell = view.width / 3f
        val y = pager.height / 2f
        Touch.drag(pager, pager.width * 0.9f, y, pager.width * 0.3f, y, release = false)
        val dragged = indicator(view).centerX()
        assertThat(dragged).isGreaterThan(cell * 0.5f + 1f)
        assertThat(dragged).isLessThan(cell * 1.5f - 1f)
        Touch.dispatch(pager, android.view.MotionEvent.ACTION_UP, pager.width * 0.3f, y)
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS)
        assertThat(pager.currentPage).isEqualTo(1)
        assertThat(view.selectedIndex).isEqualTo(1)
        assertThat(indicator(view).centerX()).isWithin(0.6f).of(cell * 1.5f)
        assertThat(heard).containsExactly(1)

        pager.setCurrentPage(2, false)
        assertThat(view.selectedIndex).isEqualTo(2)
        assertThat(indicator(view).centerX()).isWithin(0.6f).of(cell * 2.5f)
        assertThat(heard).containsExactly(1)

        view.attachTo(null)
        pager.setCurrentPage(0, false)
        assertThat(view.selectedIndex).isEqualTo(2)
    }

    @Test
    fun `empty tabs measure, draw and ignore input`() {
        val view = tabs()
        assertThat(view.height).isEqualTo(Math.round(dp(44f)))
        assertThat(view.tabs).isEmpty()
        assertThat(view.selectedIndex).isEqualTo(0)
        Touch.tap(view, view.width / 2f)
        view.select(3)
        view.setScrollPosition(1, 0.5f)
        assertThat(view.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))).isFalse()
        assertThat(view.selectedIndex).isEqualTo(0)
        assertThat(indicator(view).isEmpty).isTrue()
        // Only the hairline is drawn, from edge to edge.
        val bitmap = Snapshots.render(view, 360)
        val divider = SancaiTheme.light().colors.divider
        assertThat(bitmap.getPixel(0, bitmap.height - 1)).isEqualTo(divider)
        assertThat(bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)).isEqualTo(divider)
    }

    @Test
    fun `a label too long for its share is shortened and stays inside its tab`() {
        val long = "An extraordinarily long section name"
        val view = tabs("Recent", long, "All", "Starred", widthDp = 300)
        val cell = view.width / 4f
        view.select(1, animate = false)
        val bounds = indicator(view)
        assertThat(bounds.left).isAtLeast(cell)
        assertThat(bounds.right).isAtMost(cell * 2f)
        assertThat(bounds.centerX()).isWithin(0.6f).of(cell * 1.5f)
        // Accessibility still reads the whole label.
        val provider = checkNotNull(view.accessibilityNodeProvider)
        assertThat(checkNotNull(provider.createAccessibilityNodeInfo(1)).text.toString()).isEqualTo(long)
        // No ink of the long label reaches the edges of its own tab.
        val bitmap = Snapshots.render(view, 300)
        val bare = bitmap.getPixel(2, 2)
        for (y in 0 until bitmap.height - Math.round(dp(5f))) {
            assertThat(bitmap.getPixel((cell + 2f).toInt(), y)).isEqualTo(bare)
            assertThat(bitmap.getPixel((cell * 2f - 2f).toInt(), y)).isEqualTo(bare)
        }
        // In a scrolling row the same label keeps its natural width.
        val scrolling = tabs("Recent", long, "All", layout = SancaiTabs.Layout.SCROLL, widthDp = 300)
        scrolling.select(1, animate = false)
        assertThat(indicator(scrolling).width()).isWithin(0.6f).of(labelWidth(long))
    }

    @Test
    fun `large text keeps the height and widens the indicator`() {
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = 1.13f))
        val normal = tabs("Recent", "All", "Starred")
        val view = tabs("Recent", "All", "Starred", context = large)
        assertThat(view.height).isEqualTo(Math.round(dp(44f)))
        assertThat(indicator(view).width()).isWithin(2f).of(indicator(normal).width() * 1.13f)
        assertThat(indicator(view).centerX()).isWithin(0.6f).of(view.width / 6f)
        assertThat(indicator(view).height()).isWithin(0.01f).of(dp(3f))
        // Four long labels at the large size still fit their shares.
        val tight = tabs("Overview", "Statistics", "Blocklist", "Settings", context = large, widthDp = 300)
        tight.select(1, animate = false)
        assertThat(indicator(tight).width()).isAtMost(tight.width / 4f)
    }

    @Test
    fun `a vertical movement is not a tap`() {
        val view = tabs("Recent", "All", "Starred")
        val heard = mutableListOf<Int>()
        view.setOnTabSelectedListener { _, index -> heard += index }
        val x = view.width * 5f / 6f
        Touch.drag(view, x, view.height * 0.2f, x, view.height * 0.2f + dp(60f))
        assertThat(view.selectedIndex).isEqualTo(0)
        // Nor is a finger that slides to another tab.
        Touch.drag(view, x, view.height / 2f, view.width / 2f, view.height / 2f)
        assertThat(view.selectedIndex).isEqualTo(0)
        assertThat(heard).isEmpty()
    }

    @Test
    fun `labels that arrive late find the restored tab and the pager's page`() {
        val view = tabs("Recent", "All", "Starred").apply { id = 7 }
        view.select(2, animate = false)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)
        val late = tabs().apply { id = 7 }
        late.restoreHierarchyState(saved)
        late.setTabs("Recent", "All", "Starred")
        assertThat(late.selectedIndex).isEqualTo(2)

        val pager = SancaiPager(context)
        pager.setPages(3) { _, _ -> View(context) }
        pager.setCurrentPage(1, false)
        val coupled = tabs()
        coupled.attachTo(pager)
        coupled.setTabs("Recent", "All", "Starred")
        assertThat(coupled.selectedIndex).isEqualTo(1)
    }

    @Test
    fun `reduced motion jumps and rtl mirrors a scrolling row`() {
        val calm = Snapshots.context(SancaiTheme.light().copy(reduceMotion = true))
        val view = SancaiTabs(calm).apply { setTabs("Recent", "All", "Starred") }
        Snapshots.attach(view, 360)
        Snapshots.idle()
        view.select(2)
        assertThat(indicator(view).centerX()).isWithin(0.6f).of(view.width * 5f / 6f)

        supportRtl()
        val rtl = SancaiTabs(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            layout = SancaiTabs.Layout.SCROLL
            setTabs(*many)
            Snapshots.layout(this, 300)
        }
        // The first tab stands at the right end, the row is scrolled all the way there.
        assertThat(indicator(rtl).right).isGreaterThan(rtl.width * 0.7f)
        assertThat(rtl.scrollOffset).isGreaterThan(0f)
        rtl.select(9, animate = false)
        assertThat(rtl.scrollOffset).isEqualTo(0f)
        assertThat(indicator(rtl).left).isAtLeast(0f)
        assertThat(indicator(rtl).right).isLessThan(rtl.width * 0.5f)
    }

    @Test
    fun `a disabled pager is not moved through its tabs`() {
        val heard = mutableListOf<Int>()
        val (view, pager) = withPager(heard)
        pager.isEnabled = false
        Touch.tap(view, view.width * 5f / 6f)
        assertThat(view.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
            .isFalse()
        val provider = checkNotNull(view.accessibilityNodeProvider)
        assertThat(provider.performAction(1, AccessibilityNodeInfo.ACTION_CLICK, null)).isFalse()
        assertThat(view.selectedIndex).isEqualTo(0)
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(pager.isMoving).isFalse()
        assertThat(heard).isEmpty()
    }

    @Test
    fun `a key or click the busy pager refuses is not consumed`() {
        val heard = mutableListOf<Int>()
        val (view, pager) = withPager(heard)
        Touch.tap(view, view.width * 5f / 6f)
        assertThat(pager.isMoving).isTrue()
        assertThat(view.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)))
            .isFalse()
        assertThat(view.onKeyDown(KeyEvent.KEYCODE_MOVE_HOME, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME)))
            .isFalse()
        val provider = checkNotNull(view.accessibilityNodeProvider)
        assertThat(provider.performAction(0, AccessibilityNodeInfo.ACTION_CLICK, null)).isFalse()
        assertThat(view.selectedIndex).isEqualTo(2)
        assertThat(heard).containsExactly(2)
    }

    @Test
    fun `a tab that takes accessibility focus is scrolled into view`() {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(manager).setEnabled(true)
        shadowOf(manager).setTouchExplorationEnabled(true)
        val view = SancaiTabs(context).apply {
            layout = SancaiTabs.Layout.SCROLL
            setTabs(*many)
        }
        Snapshots.attach(view, 300)
        val provider = checkNotNull(view.accessibilityNodeProvider)
        assertThat(view.scrollOffset).isEqualTo(0f)
        assertThat(provider.performAction(8, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)).isTrue()
        assertThat(view.scrollOffset).isGreaterThan(0f)
        val bounds = android.graphics.Rect()
        @Suppress("DEPRECATION")
        checkNotNull(provider.createAccessibilityNodeInfo(8)).getBoundsInParent(bounds)
        assertThat(bounds.width().toFloat()).isWithin(1.5f).of(labelWidth("Section 9") + dp(32f))
        assertThat(bounds.left).isAtLeast(0)
        assertThat(bounds.right).isAtMost(view.width)
        // The selection stays where it was.
        assertThat(view.selectedIndex).isEqualTo(0)
    }

    @Test
    fun `a tab explored by touch at the edge does not pull the row along`() {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(manager).setEnabled(true)
        shadowOf(manager).setTouchExplorationEnabled(true)
        val view = SancaiTabs(context).apply {
            layout = SancaiTabs.Layout.SCROLL
            setTabs(*many)
        }
        Snapshots.attach(view, 300)
        val provider = checkNotNull(view.accessibilityNodeProvider)
        val x = view.width - 2
        val y = view.height / 2
        val bounds = android.graphics.Rect()
        val atEdge = many.indices.first {
            @Suppress("DEPRECATION")
            checkNotNull(provider.createAccessibilityNodeInfo(it)).getBoundsInParent(bounds)
            bounds.contains(x, y)
        }
        fun hover(action: Int) {
            val event = MotionEvent.obtain(0L, 0L, action, x.toFloat(), y.toFloat(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            view.dispatchGenericMotionEvent(event)
            event.recycle()
        }
        // A screen reader focuses what the finger touches: the row stays under the finger.
        hover(MotionEvent.ACTION_HOVER_ENTER)
        assertThat(provider.performAction(atEdge, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)).isTrue()
        assertThat(view.scrollOffset).isEqualTo(0f)
        // The same tab reached by a swipe, with no finger on it, is shown whole.
        hover(MotionEvent.ACTION_HOVER_EXIT)
        provider.performAction(atEdge, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null)
        assertThat(provider.performAction(atEdge, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)).isTrue()
        assertThat(view.scrollOffset).isGreaterThan(0f)
    }

    @Test
    fun `the tab list itself offers no click but its tabs do`() {
        val view = tabs("Recent", "All", "Starred")
        val provider = checkNotNull(view.accessibilityNodeProvider)
        val host = checkNotNull(provider.createAccessibilityNodeInfo(AccessibilityNodeProvider.HOST_VIEW_ID))
        assertThat(host.isClickable).isFalse()
        assertThat(host.actionList).doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        val child = checkNotNull(provider.createAccessibilityNodeInfo(1))
        assertThat(child.isClickable).isTrue()
        assertThat(child.actionList).contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
    }

    @Test
    fun `disabling under the finger lets the pressed label come back up`() {
        val held = tabs("Recent", "All", "Starred")
        Touch.dispatch(held, MotionEvent.ACTION_DOWN, held.width / 2f, held.height / 2f)
        held.isEnabled = false
        val never = tabs("Recent", "All", "Starred").apply { isEnabled = false }
        assertThat(Snapshots.render(held, 360).sameAs(Snapshots.render(never, 360))).isTrue()
        // The rest of that gesture does nothing, also when the tabs are enabled again.
        held.isEnabled = true
        Touch.dispatch(held, MotionEvent.ACTION_UP, held.width / 2f, held.height / 2f)
        assertThat(held.selectedIndex).isEqualTo(0)
    }

    @Test
    fun `tabs are placed anew when an ancestor changes the layout direction`() {
        supportRtl()
        val view = SancaiTabs(context).apply { setTabs("Recent", "All", "Starred") }
        val frame = FrameLayout(context)
        frame.addView(view, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        Snapshots.layout(frame, 360)
        assertThat(indicator(view).centerX()).isWithin(0.6f).of(view.width / 6f)
        frame.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(frame, 360)
        // The first tab now stands at the right, and the arrows agree with what is drawn.
        assertThat(indicator(view).centerX()).isWithin(0.6f).of(view.width * 5f / 6f)
        Touch.tap(view, view.width / 6f)
        assertThat(view.selectedIndex).isEqualTo(2)
    }

    @Test
    fun `only the first finger counts and a second one does nothing`() {
        val view = tabs(*many, layout = SancaiTabs.Layout.SCROLL, widthDp = 300)
        val heard = mutableListOf<Int>()
        view.setOnTabSelectedListener { _, index -> heard += index }
        val y = view.height / 2f
        val w = view.width.toFloat()
        Touch.drag(view, w * 0.8f, y, w * 0.5f, y, release = false)
        val left = view.scrollOffset
        assertThat(left).isGreaterThan(w * 0.2f)
        Touch.multi(view, MotionEvent.ACTION_POINTER_DOWN, 1, Touch.Finger(0, w * 0.5f, y), Touch.Finger(1, w * 0.1f, y))
        Touch.multi(view, MotionEvent.ACTION_POINTER_UP, 0, Touch.Finger(0, w * 0.5f, y), Touch.Finger(1, w * 0.1f, y))
        Touch.multi(view, MotionEvent.ACTION_MOVE, 0, Touch.Finger(1, w * 0.9f, y))
        Touch.multi(view, MotionEvent.ACTION_UP, 0, Touch.Finger(1, w * 0.9f, y))
        assertThat(view.scrollOffset).isEqualTo(left)
        assertThat(view.selectedIndex).isEqualTo(0)
        assertThat(heard).isEmpty()

        // A tap of the first finger is a tap where it lifted; the second finger chooses nothing.
        val few = tabs("Recent", "All", "Starred")
        few.setOnTabSelectedListener { _, index -> heard += index }
        val a = Touch.Finger(0, few.width * 5f / 6f, y)
        Touch.dispatch(few, MotionEvent.ACTION_DOWN, a.x, y)
        Touch.multi(few, MotionEvent.ACTION_POINTER_DOWN, 1, a, Touch.Finger(1, few.width / 2f, y))
        Touch.multi(few, MotionEvent.ACTION_POINTER_UP, 0, a, Touch.Finger(1, few.width / 2f, y))
        assertThat(few.selectedIndex).isEqualTo(2)
        Touch.multi(few, MotionEvent.ACTION_MOVE, 0, Touch.Finger(1, few.width / 6f, y))
        Touch.multi(few, MotionEvent.ACTION_UP, 0, Touch.Finger(1, few.width / 6f, y))
        assertThat(few.selectedIndex).isEqualTo(2)
        assertThat(heard).containsExactly(2)
    }

    @Test
    fun `snapshot of tabs`() {
        supportRtl()
        for ((name, theme) in listOf("tabs_light" to SancaiTheme.light(), "tabs_dark" to SancaiTheme.dark())) {
            val themed = Snapshots.context(theme)
            val column = LinearLayout(themed).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(theme.colors.canvas)
                setPadding(0, dp(12f).toInt(), 0, dp(20f).toInt())
            }
            fun add(build: SancaiTabs.() -> Unit) {
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                params.bottomMargin = dp(18f).toInt()
                column.addView(SancaiTabs(themed).apply(build), params)
            }
            add { setTabs("Recent", "All", "Starred") }
            add {
                setTabs("Overview", "Rules", "Log", "About")
                select(1, animate = false)
            }
            add {
                // Half way between two tabs, as while a pager is dragged.
                setTabs("Recent", "All", "Starred")
                setScrollPosition(0, 0.5f)
            }
            add {
                layout = SancaiTabs.Layout.SCROLL
                setTabs(*many)
                select(3, animate = false)
            }
            add {
                layout = SancaiTabs.Layout.SCROLL
                setTabs("最近", "全部", "已收藏")
                select(1, animate = false)
            }
            add {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setTabs("First", "Second", "Third")
            }
            add {
                setTabs("Recent", "All", "Starred")
                select(2, animate = false)
                isEnabled = false
            }
            add {
                // Long labels in equal shares: condensed first, then shortened.
                setTabs("Overview", "Notifications", "An extraordinarily long name", "Log")
                select(2, animate = false)
            }
            val large = Snapshots.context(theme.copy(textScale = 1.13f))
            column.addView(
                SancaiTabs(large).apply {
                    setTabs("Overview", "Statistics", "Blocklist", "Settings")
                    select(1, animate = false)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            Snapshots.capture(column, name, 360)
        }
    }
}
