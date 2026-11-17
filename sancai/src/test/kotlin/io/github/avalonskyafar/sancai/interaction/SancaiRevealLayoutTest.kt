package io.github.avalonskyafar.sancai.interaction

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiText
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiRevealLayoutTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val density = context.resources.displayMetrics.density
    private val strip = SancaiRevealLayout.STRIP_WIDTH_DP * density

    /** A view that claims horizontal drags, like a switch in the row. */
    private class Owner(context: Context) : View(context), SancaiHorizontalDragOwner {
        var moves = 0
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_MOVE) moves++
            return true
        }
    }

    private var clicks = 0
    private val changes = ArrayList<Boolean>()

    private fun dp(value: Int): Int = (value * density).toInt()

    /** A 50 dp row: a title and, when asked, a drag owner at the end. */
    private fun row(ctx: Context = context, title: String = "Account", owner: View? = null): SancaiRevealLayout {
        val content = LinearLayout(ctx)
        content.gravity = Gravity.CENTER_VERTICAL
        content.setPaddingRelative(dp(14), dp(8), dp(7), dp(8))
        val text = SancaiText.create(ctx, title, SancaiTextRole.LABEL, SancaiTextTone.INK)
        content.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (owner != null) content.addView(owner, LinearLayout.LayoutParams(dp(61), dp(34)))
        val layout = SancaiRevealLayout(ctx)
        layout.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(50)))
        layout.setAction("Delete") { clicks++ }
        layout.onOpenChangeListener = SancaiRevealLayout.OnOpenChangeListener { _, open -> changes += open }
        return layout
    }

    private fun laidOut(layout: SancaiRevealLayout = row()): SancaiRevealLayout {
        Snapshots.layout(layout, 300)
        return layout
    }

    private val SancaiRevealLayout.shift: Float get() = row!!.translationX

    @Test
    fun `a drag past 42 percent opens and tells the listener once`() {
        val layout = laidOut()
        Touch.drag(layout, 300f, 60f, 300f + strip * 0.75f, 62f)
        assertThat(layout.isOpen).isTrue()
        assertThat(layout.shift).isEqualTo(strip)
        assertThat(changes).containsExactly(true)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `a drag short of 42 percent closes again`() {
        val layout = laidOut()
        Touch.drag(layout, 300f, 60f, 300f + strip * 0.3f, 60f)
        assertThat(layout.isOpen).isFalse()
        assertThat(layout.shift).isEqualTo(0f)
        assertThat(changes).isEmpty()
    }

    @Test
    fun `the row follows the finger and stops at the strip width`() {
        val layout = laidOut()
        Touch.drag(layout, 100f, 60f, 100f + strip * 0.6f, 60f, release = false)
        assertThat(layout.shift).isGreaterThan(strip * 0.3f)
        assertThat(layout.shift).isLessThan(strip * 0.6f + 1f)
        Touch.dispatch(layout, MotionEvent.ACTION_MOVE, 700f, 60f)
        assertThat(layout.shift).isEqualTo(strip)
        Touch.dispatch(layout, MotionEvent.ACTION_MOVE, 20f, 60f)
        assertThat(layout.shift).isEqualTo(0f)
        Touch.dispatch(layout, MotionEvent.ACTION_CANCEL, 20f, 60f)
        assertThat(layout.isOpen).isFalse()
    }

    @Test
    fun `cancel closes a row that was dragged open`() {
        val layout = laidOut()
        Touch.drag(layout, 100f, 60f, 100f + strip, 60f, release = false)
        Touch.dispatch(layout, MotionEvent.ACTION_CANCEL, 100f + strip, 60f)
        assertThat(layout.isOpen).isFalse()
        assertThat(layout.shift).isEqualTo(0f)
    }

    @Test
    fun `vertical movement gives the gesture back`() {
        val layout = laidOut()
        Touch.drag(layout, 300f, 20f, 330f, 120f, release = false)
        Touch.dispatch(layout, MotionEvent.ACTION_MOVE, 600f, 120f)
        assertThat(layout.shift).isEqualTo(0f)
        Touch.dispatch(layout, MotionEvent.ACTION_UP, 600f, 120f)
        assertThat(layout.isOpen).isFalse()
        assertThat(changes).isEmpty()
    }

    @Test
    fun `a drag towards the closed side of a closed row is not taken`() {
        val layout = laidOut()
        Touch.drag(layout, 400f, 60f, 150f, 60f)
        assertThat(layout.shift).isEqualTo(0f)
        assertThat(layout.isOpen).isFalse()
    }

    @Test
    fun `a gesture that starts on a drag owner stays with the owner`() {
        val owner = Owner(context)
        val layout = laidOut(row(owner = owner))
        val x = layout.width - dp(7) - dp(30).toFloat()
        Touch.drag(layout, x, 60f, x - 20f, 60f, release = false)
        Touch.drag(layout, x, 60f, x + strip, 60f)
        assertThat(owner.moves).isGreaterThan(8)
        assertThat(layout.shift).isEqualTo(0f)
        assertThat(layout.isOpen).isFalse()
    }

    @Test
    fun `a tap on the uncovered strip runs the action and leaves the row open`() {
        val layout = laidOut()
        layout.open()
        assertThat(layout.shift).isEqualTo(strip)
        Touch.tap(layout, strip / 2f)
        assertThat(clicks).isEqualTo(1)
        assertThat(layout.isOpen).isTrue()
        assertThat(changes).isEmpty()
    }

    @Test
    fun `a tap on the open row closes it without running the action`() {
        val layout = laidOut()
        layout.open()
        Touch.tap(layout, strip + 100f)
        assertThat(layout.isOpen).isFalse()
        assertThat(layout.shift).isEqualTo(0f)
        assertThat(clicks).isEqualTo(0)
        assertThat(changes).containsExactly(false)
    }

    @Test
    fun `an open row can be dragged shut`() {
        val layout = laidOut()
        layout.open()
        Touch.drag(layout, 500f, 60f, 500f - strip, 60f)
        assertThat(layout.isOpen).isFalse()
        assertThat(changes).containsExactly(false)
    }

    @Test
    fun `open and close from code do not call the listener`() {
        val layout = laidOut()
        layout.open()
        layout.close()
        assertThat(changes).isEmpty()
        assertThat(layout.isOpen).isFalse()
    }

    @Test
    fun `opening a row closes its open siblings`() {
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        val first = row()
        val second = row()
        val third = row()
        third.closeSiblings = false
        listOf(first, second, third).forEach { list.addView(it) }
        Snapshots.layout(list, 300)
        first.open()
        Touch.drag(list, 300f, second.top + 60f, 300f + strip, second.top + 60f)
        assertThat(second.isOpen).isTrue()
        assertThat(first.isOpen).isFalse()
        assertThat(first.shift).isEqualTo(0f)
        third.open()
        assertThat(second.isOpen).isTrue()
    }

    @Test
    fun `the action is offered as an accessibility action`() {
        val layout = laidOut()
        val info = AccessibilityNodeInfo.obtain()
        layout.onInitializeAccessibilityNodeInfo(info)
        val action = info.actionList.firstOrNull { it.label?.toString() == "Delete" }
        assertThat(action).isNotNull()
        assertThat(layout.performAccessibilityAction(action!!.id, null)).isTrue()
        assertThat(clicks).isEqualTo(1)
        val rowInfo = AccessibilityNodeInfo.obtain()
        layout.row!!.onInitializeAccessibilityNodeInfo(rowInfo)
        assertThat(rowInfo.actionList.map { it.label?.toString() }).contains("Delete")
        assertThat(layout.accessibilityClassName).isEqualTo(SancaiRevealLayout::class.java.name)
    }

    @Test
    fun `right to left mirrors the edge`() {
        val app = context.applicationContext
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val layout = row()
        layout.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(layout, 300)
        // The start edge is on the right: the row slides to the left.
        Touch.drag(layout, 300f, 60f, 300f + strip, 60f)
        assertThat(layout.isOpen).isFalse()
        Touch.drag(layout, 500f, 60f, 500f - strip, 60f)
        assertThat(layout.isOpen).isTrue()
        assertThat(layout.shift).isEqualTo(-strip)
        assertThat(layout.canScrollHorizontally(-1)).isTrue()
        assertThat(layout.canScrollHorizontally(1)).isFalse()
        Touch.tap(layout, layout.width - strip / 2f)
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `the end edge uncovers the action on the other side`() {
        val layout = row()
        layout.revealEdge = SancaiRevealLayout.Edge.END
        Snapshots.layout(layout, 300)
        Touch.drag(layout, 500f, 60f, 500f - strip, 60f)
        assertThat(layout.isOpen).isTrue()
        assertThat(layout.shift).isEqualTo(-strip)
    }

    @Test
    fun `page hosts are told in which direction the row can still move`() {
        val layout = laidOut()
        // Closed, strip on the left: only a finger moving right (scroll direction -1) moves it.
        assertThat(layout.canScrollHorizontally(-1)).isTrue()
        assertThat(layout.canScrollHorizontally(1)).isFalse()
        layout.open()
        assertThat(layout.canScrollHorizontally(-1)).isFalse()
        assertThat(layout.canScrollHorizontally(1)).isTrue()
        layout.clearAction()
        assertThat(layout.isOpen).isFalse()
        assertThat(layout.canScrollHorizontally(-1)).isFalse()
        assertThat(layout.canScrollHorizontally(1)).isFalse()
    }

    private fun key(layout: View, code: Int): Boolean = layout.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))

    @Test
    fun `arrows open and close and enter runs the uncovered action`() {
        val layout = laidOut()
        assertThat(layout.isFocusable).isTrue()
        assertThat(key(layout, KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(key(layout, KeyEvent.KEYCODE_ENTER)).isFalse()
        assertThat(clicks).isEqualTo(0)
        assertThat(key(layout, KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(layout.isOpen).isTrue()
        assertThat(key(layout, KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(key(layout, KeyEvent.KEYCODE_ENTER)).isTrue()
        assertThat(clicks).isEqualTo(1)
        assertThat(key(layout, KeyEvent.KEYCODE_DPAD_LEFT)).isTrue()
        assertThat(layout.isOpen).isFalse()
        assertThat(changes).containsExactly(true, false).inOrder()
    }

    @Test
    fun `a disabled row closes and does not move`() {
        val layout = laidOut()
        layout.open()
        layout.isEnabled = false
        assertThat(layout.isOpen).isFalse()
        Touch.drag(layout, 300f, 60f, 300f + strip, 60f)
        assertThat(layout.shift).isEqualTo(0f)
        layout.open()
        assertThat(layout.isOpen).isFalse()
    }

    @Test
    fun `the row gets a fill only when it brings no background and a second child is refused`() {
        val layout = laidOut()
        assertThat(layout.row!!.background).isNotNull()
        val own = android.graphics.drawable.ColorDrawable(Color.GREEN)
        val content = View(context)
        content.background = own
        val other = SancaiRevealLayout(context)
        other.addView(content)
        assertThat(content.background).isSameInstanceAs(own)
        assertThrows(IllegalStateException::class.java) { other.addView(View(context)) }
    }

    private fun near(actual: Int, expected: Int, tolerance: Int = 10): Boolean =
        abs(Color.red(actual) - Color.red(expected)) <= tolerance &&
            abs(Color.green(actual) - Color.green(expected)) <= tolerance &&
            abs(Color.blue(actual) - Color.blue(expected)) <= tolerance

    @Test
    fun `the bed shows the tone beside a flat surface fill and is hidden while closed`() {
        val colors = SancaiTheme.light().colors
        val layout = laidOut()
        val y = (layout.height * 0.54f).toInt()
        val closed = Snapshots.render(layout, 300)
        assertThat(closed.getPixel(8, y)).isEqualTo(colors.surface)
        layout.open()
        val open = Snapshots.render(layout, 300)
        assertThat(near(open.getPixel(8, y), colors.danger)).isTrue()
        assertThat(open.getPixel(strip.toInt() - 4, y)).isNotEqualTo(colors.surface)
        assertThat(open.getPixel(strip.toInt() + 4, y)).isEqualTo(colors.surface)
        layout.setAction("Archive", SancaiTextTone.ACCENT, SancaiIcon.INBOX) { }
        assertThat(near(Snapshots.render(layout, 300).getPixel(8, y), colors.accent)).isTrue()
    }

    @Test
    fun `an attached row glides open and announces its state`() {
        val layout = row()
        Snapshots.attach(layout, 300)
        assertThat(androidx.core.view.ViewCompat.getStateDescription(layout)).isNull()
        layout.open()
        assertThat(layout.isOpen).isTrue()
        assertThat(layout.shift).isLessThan(strip)
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        assertThat(layout.shift).isEqualTo(strip)
        assertThat(androidx.core.view.ViewCompat.getStateDescription(layout).toString()).isEqualTo("Delete shown")
        layout.close(animate = false)
        assertThat(layout.shift).isEqualTo(0f)
        assertThat(androidx.core.view.ViewCompat.getStateDescription(layout)).isNull()
    }

    @Test
    fun `rows come back closed after save and restore`() {
        val layout = laidOut()
        layout.id = 7
        layout.open()
        val state = android.util.SparseArray<android.os.Parcelable>()
        layout.saveHierarchyState(state)
        val fresh = laidOut()
        fresh.id = 7
        fresh.restoreHierarchyState(state)
        assertThat(fresh.isOpen).isFalse()
        assertThat(fresh.shift).isEqualTo(0f)
    }

    @Test
    fun `a clickable row is still dragged and its click is not fired`() {
        val layout = row()
        var rowClicks = 0
        layout.row!!.setOnClickListener { rowClicks++ }
        Snapshots.attach(layout, 300)
        Touch.drag(layout, 300f, 60f, 300f + strip, 60f)
        Snapshots.idle()
        assertThat(layout.isOpen).isTrue()
        assertThat(rowClicks).isEqualTo(0)
        // Open: the tap closes the row instead of clicking it.
        Touch.tap(layout, strip + 100f)
        Snapshots.idle()
        assertThat(layout.isOpen).isFalse()
        assertThat(rowClicks).isEqualTo(0)
        Touch.tap(layout, 300f)
        Snapshots.idle()
        assertThat(rowClicks).isEqualTo(1)
    }

    @Test
    fun `the sliding row is clipped to the layout`() {
        val root = FrameLayout(context)
        root.setBackgroundColor(Color.GREEN)
        root.setPadding(0, 0, dp(40), 0)
        val layout = row()
        root.addView(layout)
        Snapshots.layout(root, 300)
        layout.open()
        val bitmap = Snapshots.render(root, 300)
        assertThat(bitmap.getPixel(layout.width + dp(10), layout.height / 2)).isEqualTo(Color.GREEN)
    }

    @Test
    fun `a sibling closed by the user's gesture tells its listener, one closed from code does not`() {
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        val first = row()
        val second = row()
        listOf(first, second).forEach { list.addView(it) }
        val firstChanges = ArrayList<Boolean>()
        first.onOpenChangeListener = SancaiRevealLayout.OnOpenChangeListener { _, open -> firstChanges += open }
        Snapshots.layout(list, 300)
        first.open()
        second.open()
        assertThat(first.isOpen).isFalse()
        assertThat(firstChanges).isEmpty()
        second.close()
        first.open()
        Touch.drag(list, 300f, second.top + 60f, 300f + strip, second.top + 60f)
        assertThat(first.isOpen).isFalse()
        assertThat(firstChanges).containsExactly(false)
    }

    @Test
    fun `a row without an action does not move and leaves its child alone`() {
        val content = View(context)
        var rowClicks = 0
        content.setOnClickListener { rowClicks++ }
        val layout = SancaiRevealLayout(context)
        layout.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(50)))
        Snapshots.attach(layout, 300)
        assertThat(layout.actionLabel).isNull()
        assertThat(layout.isFocusable).isFalse()
        layout.open()
        assertThat(layout.isOpen).isFalse()
        Touch.drag(layout, 300f, 60f, 300f + strip, 60f)
        Snapshots.idle()
        assertThat(content.translationX).isEqualTo(0f)
        assertThat(key(layout, KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        val info = AccessibilityNodeInfo.obtain()
        layout.onInitializeAccessibilityNodeInfo(info)
        assertThat(info.actionList.filter { it.label != null }).isEmpty()
        val before = rowClicks
        Touch.tap(layout, 300f)
        Snapshots.idle()
        assertThat(rowClicks).isEqualTo(before + 1)
    }

    @Test
    fun `a long label at text scale 1_13 stays inside the strip`() {
        val theme = SancaiTheme.light().copy(textScale = 1.13f)
        val layout = row(Snapshots.context(theme), "A very long account name that does not fit the row at all")
        layout.setAction("Remove from the allow list forever", SancaiTextTone.DANGER, SancaiIcon.CLOSE) { clicks++ }
        Snapshots.layout(layout, 300)
        layout.open()
        val bitmap = Snapshots.render(layout, 300)
        assertThat(layout.height).isEqualTo(dp(50))
        // Nothing of the label leaks past the strip onto the row, at any height.
        for (y in 2 until layout.height - 2 step 4) {
            assertThat(near(bitmap.getPixel(strip.toInt() + 3, y), theme.colors.surface, 8)).isTrue()
        }
        // The label is there: white pixels inside the strip, clear of its outer edge.
        var white = 0
        for (x in dp(6) until strip.toInt() - dp(6)) for (y in 0 until layout.height) {
            if (near(bitmap.getPixel(x, y), Color.WHITE, 6)) white++
        }
        assertThat(white).isGreaterThan(20)
        for (y in 0 until layout.height) assertThat(near(bitmap.getPixel(2, y), Color.WHITE, 6)).isFalse()
    }

    @Test
    fun `keys reach the row while a view inside it has the focus`() {
        val layout = row()
        val content = layout.row!!
        content.isFocusable = true
        content.isFocusableInTouchMode = true
        Snapshots.attach(layout, 300)
        assertThat(content.requestFocus()).isTrue()
        val right = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertThat(layout.dispatchKeyEvent(right)).isTrue()
        assertThat(layout.isOpen).isTrue()
        assertThat(layout.dispatchKeyEvent(right)).isFalse()
        assertThat(layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))).isTrue()
        assertThat(layout.isOpen).isFalse()
        assertThat(changes).containsExactly(true, false).inOrder()
    }

    @Test
    fun `right to left with the end edge slides to the right and a removed row leaves clean`() {
        val app = context.applicationContext
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val layout = row()
        layout.revealEdge = SancaiRevealLayout.Edge.END
        layout.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(layout, 300)
        Touch.drag(layout, 300f, 60f, 300f + strip, 60f)
        assertThat(layout.isOpen).isTrue()
        assertThat(layout.shift).isEqualTo(strip)
        val content = layout.row!!
        layout.removeView(content)
        assertThat(content.translationX).isEqualTo(0f)
        assertThat(content.background).isNull()
        val info = AccessibilityNodeInfo.obtain()
        content.onInitializeAccessibilityNodeInfo(info)
        assertThat(info.actionList.map { it.label?.toString() }).doesNotContain("Delete")
    }

    private fun gallery(theme: SancaiTheme, name: String) {
        val ctx = Snapshots.context(theme)
        val root = FrameLayout(ctx)
        root.setBackgroundColor(theme.colors.canvas)
        root.setPadding(dp(16), dp(16), dp(16), dp(16))
        val card = SancaiSurface(ctx)
        val closed = row(ctx, "Closed row")
        val half = row(ctx, "Half open")
        val open = row(ctx, "Open, with an icon")
        open.setAction("Delete", SancaiTextTone.DANGER, SancaiIcon.CLOSE) { }
        val end = row(ctx, "End edge, accent tone")
        end.revealEdge = SancaiRevealLayout.Edge.END
        end.setAction("Archive", SancaiTextTone.ACCENT) { }
        val disabled = row(ctx, "Disabled")
        disabled.isEnabled = false
        val rows = listOf(closed, half, open, end, disabled)
        rows.forEach { it.closeSiblings = false }
        rows.forEachIndexed { index, item ->
            if (index > 0) card.addDivider()
            card.addView(item)
        }
        root.addView(card)
        Snapshots.layout(root, 360)
        Touch.drag(half, 100f, 60f, 100f + strip * 0.7f, 60f, release = false)
        open.open()
        end.open()
        Snapshots.capture(root, name, 360)
    }

    @Test
    fun `snapshot of reveal rows`() {
        gallery(SancaiTheme.light(), "reveal_light")
        gallery(SancaiTheme.dark(), "reveal_dark")
    }
}
