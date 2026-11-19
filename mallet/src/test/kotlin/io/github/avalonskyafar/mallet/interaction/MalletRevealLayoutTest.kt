package io.github.avalonskyafar.mallet.interaction

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
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.material.MalletCorners
import io.github.avalonskyafar.mallet.material.MalletGutters
import io.github.avalonskyafar.mallet.material.MalletMaterial
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.testing.Touch
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.widget.MalletDivider
import io.github.avalonskyafar.mallet.widget.MalletSurface
import io.github.avalonskyafar.mallet.widget.MalletText
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletRevealLayoutTest {

    private val context = Snapshots.context(MalletTheme.light())
    private val density = context.resources.displayMetrics.density
    private val strip = MalletRevealLayout.STRIP_WIDTH_DP * density

    /** A view that claims horizontal drags, like a switch in the row. */
    private class Owner(context: Context) : View(context), MalletHorizontalDragOwner {
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
    private fun row(ctx: Context = context, title: String = "Account", owner: View? = null): MalletRevealLayout {
        val content = LinearLayout(ctx)
        content.gravity = Gravity.CENTER_VERTICAL
        content.setPaddingRelative(dp(14), dp(8), dp(7), dp(8))
        val text = MalletText.create(ctx, title, MalletTextRole.LABEL, MalletTextTone.INK)
        content.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (owner != null) content.addView(owner, LinearLayout.LayoutParams(dp(61), dp(34)))
        val layout = MalletRevealLayout(ctx)
        layout.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(50)))
        layout.setAction("Delete") { clicks++ }
        layout.setOnOpenChangeListener { _, open -> changes += open }
        return layout
    }

    private fun laidOut(layout: MalletRevealLayout = row()): MalletRevealLayout {
        Snapshots.layout(layout, 300)
        return layout
    }

    private val MalletRevealLayout.shift: Float get() = row!!.translationX

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
        assertThat(layout.accessibilityClassName).isEqualTo(MalletRevealLayout::class.java.name)
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
        layout.revealEdge = MalletRevealLayout.Edge.END
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
    fun `the row keeps the background it brings, gets none, and a second child is refused`() {
        val layout = laidOut()
        assertThat(layout.row!!.background).isNull()
        val own = android.graphics.drawable.ColorDrawable(Color.GREEN)
        val content = View(context)
        content.background = own
        val other = MalletRevealLayout(context)
        other.addView(content)
        assertThat(content.background).isSameInstanceAs(own)
        assertThrows(IllegalStateException::class.java) { other.addView(View(context)) }
    }

    private fun near(actual: Int, expected: Int, tolerance: Int = 10): Boolean =
        abs(Color.red(actual) - Color.red(expected)) <= tolerance &&
            abs(Color.green(actual) - Color.green(expected)) <= tolerance &&
            abs(Color.blue(actual) - Color.blue(expected)) <= tolerance

    @Test
    fun `the bed shows the tone as far as the row has moved and is hidden while closed`() {
        val colors = MalletTheme.light().colors
        val layout = laidOut()
        val y = (layout.height * 0.54f).toInt()
        val closed = Snapshots.render(layout, 300)
        assertThat(closed.getPixel(8, y)).isEqualTo(colors.surface)
        layout.open()
        val open = Snapshots.render(layout, 300)
        assertThat(near(open.getPixel(8, y), colors.danger)).isTrue()
        assertThat(open.getPixel(strip.toInt() - 4, y)).isNotEqualTo(colors.surface)
        assertThat(open.getPixel(strip.toInt() + 4, y)).isEqualTo(colors.surface)
        layout.setAction("Archive", MalletTextTone.ACCENT, MalletIcon.INBOX) { }
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
    fun `a clickable row is still dragged and its click is not triggered`() {
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
        first.setOnOpenChangeListener { _, open -> firstChanges += open }
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
        val layout = MalletRevealLayout(context)
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
        val theme = MalletTheme.light().copy(textScale = 1.13f)
        val layout = row(Snapshots.context(theme), "A very long account name that does not fit the row at all")
        layout.setAction("Remove from the allow list forever", MalletTextTone.DANGER, MalletIcon.CLOSE) { clicks++ }
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
        layout.revealEdge = MalletRevealLayout.Edge.END
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

    private fun gallery(theme: MalletTheme, name: String) {
        val ctx = Snapshots.context(theme)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(theme.colors.canvas)
        root.setPadding(dp(16), dp(16), dp(16), dp(16))
        val card = MalletSurface(ctx)
        val open = row(ctx, "Open, with an icon")
        open.setAction("Delete", MalletTextTone.DANGER, MalletIcon.TRASH) { }
        val half = row(ctx, "Half open")
        val closed = row(ctx, "Closed row")
        val disabled = row(ctx, "Disabled")
        disabled.isEnabled = false
        val end = row(ctx, "End edge, accent tone")
        end.revealEdge = MalletRevealLayout.Edge.END
        end.setAction("Archive", MalletTextTone.ACCENT, MalletIcon.INBOX) { }
        val rows = listOf(open, half, closed, disabled, end)
        rows.forEach { it.closeSiblings = false }
        rows.forEachIndexed { index, item ->
            if (index > 0) card.addDivider()
            card.addView(item)
        }
        root.addView(card)
        // A card of one row: its bed follows both corners of the card.
        val single = MalletSurface(ctx)
        val only = row(ctx, "The only row of its card")
        single.addView(only)
        root.addView(single, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        Snapshots.layout(root, 360)
        Touch.drag(half, 100f, 60f, 100f + strip * 0.7f, 60f, release = false)
        open.open()
        end.open()
        only.open()
        Snapshots.capture(root, name, 360)
    }

    @Test
    fun `a held enter runs the action once and swallows the repeats`() {
        val layout = laidOut()
        layout.open(animate = false)
        assertThat(key(layout, KeyEvent.KEYCODE_ENTER)).isTrue()
        for (repeat in 1..5) {
            val held = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, repeat)
            assertThat(layout.onKeyDown(KeyEvent.KEYCODE_ENTER, held)).isTrue()
        }
        assertThat(clicks).isEqualTo(1)
        assertThat(layout.isOpen).isTrue()
    }

    @Test
    fun `padding insets the row and never the bed`() {
        val layout = row()
        layout.setBackgroundColor(Color.GREEN)
        layout.setPadding(dp(16), dp(6), 0, dp(6))
        Snapshots.layout(layout, 300)
        assertThat(layout.row!!.left).isEqualTo(dp(16))
        assertThat(layout.row!!.top).isEqualTo(dp(6))
        layout.open(animate = false)
        val bitmap = Snapshots.render(layout, 300)
        val danger = MalletTheme.light().colors.danger
        val middle = layout.height / 2
        // The bed starts at the edge of the layout: beside the row and above it.
        assertThat(near(bitmap.getPixel(2, middle), danger)).isTrue()
        assertThat(near(bitmap.getPixel(dp(8), dp(3)), danger, 40)).isTrue()
        // It is as wide as the row has moved, wherever the padding put the row.
        assertThat(layout.shift).isEqualTo(strip)
        assertThat(near(bitmap.getPixel(strip.toInt() - dp(8), middle), danger)).isTrue()
        assertThat(bitmap.getPixel(strip.toInt() + 4, middle)).isEqualTo(Color.GREEN)
        // All of the uncovered bed is the action, the part beside the padding too.
        Touch.tap(layout, 3f, 3f)
        assertThat(clicks).isEqualTo(1)
        assertThat(layout.isOpen).isTrue()
        // Beside the bed a tap closes the row.
        Touch.tap(layout, strip + 8f)
        assertThat(clicks).isEqualTo(1)
        assertThat(layout.isOpen).isFalse()
    }

    @Test
    fun `a second finger does not move the row once the first has lifted`() {
        val layout = laidOut()
        val far = 100f + strip * 0.35f
        Touch.drag(layout, 100f, 60f, far, 60f, release = false)
        assertThat(layout.shift).isGreaterThan(0f)
        val a = Touch.Finger(0, far, 60f)
        Touch.multi(layout, MotionEvent.ACTION_POINTER_DOWN, 1, a, Touch.Finger(1, 250f, 60f))
        Touch.multi(layout, MotionEvent.ACTION_MOVE, 0, a, Touch.Finger(1, 260f, 60f))
        assertThat(layout.shift).isLessThan(strip * 0.35f + 1f)
        // The first finger lifts: the drag ends where it left the row, short of opening.
        Touch.multi(layout, MotionEvent.ACTION_POINTER_UP, 0, a, Touch.Finger(1, 260f, 60f))
        assertThat(layout.isOpen).isFalse()
        assertThat(layout.shift).isEqualTo(0f)
        Touch.multi(layout, MotionEvent.ACTION_MOVE, 0, Touch.Finger(1, 260f + strip, 60f))
        assertThat(layout.shift).isEqualTo(0f)
        Touch.multi(layout, MotionEvent.ACTION_UP, 0, Touch.Finger(1, 260f + strip, 60f))
        assertThat(layout.isOpen).isFalse()
        assertThat(layout.shift).isEqualTo(0f)
        assertThat(changes).isEmpty()
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `a row opened by the first finger stays open whatever the second does`() {
        val layout = laidOut()
        val far = 100f + strip * 0.8f
        Touch.drag(layout, 100f, 60f, far, 60f, release = false)
        val a = Touch.Finger(0, far, 60f)
        Touch.multi(layout, MotionEvent.ACTION_POINTER_DOWN, 1, a, Touch.Finger(1, 280f, 60f))
        Touch.multi(layout, MotionEvent.ACTION_POINTER_UP, 0, a, Touch.Finger(1, 280f, 60f))
        assertThat(layout.isOpen).isTrue()
        Touch.multi(layout, MotionEvent.ACTION_MOVE, 0, Touch.Finger(1, 280f - strip, 60f))
        Touch.multi(layout, MotionEvent.ACTION_UP, 0, Touch.Finger(1, 280f - strip, 60f))
        assertThat(layout.isOpen).isTrue()
        assertThat(layout.shift).isEqualTo(strip)
        assertThat(changes).containsExactly(true)
        assertThat(clicks).isEqualTo(0)
    }

    // ---- inside a card: the bed starts at the rim ----

    /** A card of [count] rows with a divider between two rows, laid out 320 dp wide. */
    private fun card(count: Int, dividerMarginDp: Float = 0f, ctx: Context = context): Pair<MalletSurface, List<MalletRevealLayout>> {
        val card = MalletSurface(ctx)
        val rows = List(count) { row(ctx, "Row $it") }
        rows.forEachIndexed { index, item ->
            if (index > 0) card.addDivider(dividerMarginDp)
            card.addView(item)
        }
        Snapshots.layout(card, 320)
        return card to rows
    }

    private fun dividersOf(card: MalletSurface): List<MalletDivider> =
        (0 until card.childCount).map { card.getChildAt(it) }.filterIsInstance<MalletDivider>()

    private val rim = MalletMaterial.RIM_DP * density

    /** A pixel of a danger-coloured bed, whatever shade or light lies on it. */
    private fun reddish(color: Int): Boolean =
        Color.red(color) - Color.green(color) > 50 && Color.red(color) - Color.blue(color) > 50

    @Test
    fun `in a card the layout reaches from rim to rim and the row keeps its place`() {
        val plain = MalletSurface(context)
        val reference = View(context)
        plain.addView(reference, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)))
        Snapshots.layout(plain, 320)
        val (card, rows) = card(1)
        val only = rows[0]
        assertThat(only.left).isEqualTo((MalletGutters.SURFACE_SIDE_DP * density + rim).roundToInt())
        assertThat(only.right).isEqualTo((card.width - MalletGutters.SURFACE_SIDE_DP * density - rim).roundToInt())
        assertThat(only.top).isEqualTo((MalletGutters.SURFACE_TOP_DP * density + rim).roundToInt())
        assertThat(only.bottom).isEqualTo((card.height - MalletGutters.SURFACE_BOTTOM_DP * density - rim).roundToInt())
        // The row is exactly where a plain child of the card is, and the card is no taller.
        val inner = only.row!!
        assertThat(only.left + inner.left).isEqualTo(reference.left)
        assertThat(only.top + inner.top).isEqualTo(reference.top)
        assertThat(inner.width).isEqualTo(reference.width)
        assertThat(inner.height).isEqualTo(reference.height)
        assertThat(card.height).isEqualTo(plain.height)
    }

    @Test
    fun `between rows the layout ends at the dividers`() {
        val (card, rows) = card(3)
        val dividers = dividersOf(card)
        assertThat(rows[0].bottom).isEqualTo(dividers[0].top)
        assertThat(rows[1].top).isEqualTo(dividers[0].bottom)
        assertThat(rows[1].bottom).isEqualTo(dividers[1].top)
        assertThat(rows[2].top).isEqualTo(dividers[1].bottom)
        // A second pass changes nothing.
        val before = rows.map { listOf(it.left, it.top, it.right, it.bottom, it.row!!.left, it.row!!.top) }
        Snapshots.layout(card, 320)
        assertThat(rows.map { listOf(it.left, it.top, it.right, it.bottom, it.row!!.left, it.row!!.top) }).isEqualTo(before)
    }

    @Test
    fun `a divider with margins still bounds the cell and the row stays put`() {
        val (plainCard, plainRows) = card(2)
        val (card, rows) = card(2, dividerMarginDp = 5f)
        val divider = dividersOf(card)[0]
        assertThat(rows[0].bottom).isEqualTo(divider.top)
        assertThat(rows[1].top).isEqualTo(divider.bottom)
        // The second row sits one margin below the divider, as it did before it was stretched.
        assertThat(rows[1].row!!.top).isEqualTo((5f * density).roundToInt())
        assertThat(card.height - plainCard.height).isEqualTo(2 * (5f * density).roundToInt())
        assertThat(plainRows[1].row!!.top).isEqualTo(0)
    }

    @Test
    fun `beside a neighbour that is not a divider the layout keeps its own edge`() {
        val card = MalletSurface(context)
        val above = View(context)
        val below = View(context)
        val layout = row()
        card.addView(above, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(20)))
        card.addView(layout)
        card.addView(below, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(20)))
        Snapshots.layout(card, 320)
        assertThat(layout.top).isEqualTo(above.bottom)
        assertThat(layout.bottom).isEqualTo(below.top)
        // Sideways it still runs from rim to rim.
        assertThat(layout.left).isLessThan(above.left)
        assertThat(layout.right).isGreaterThan(above.right)
        assertThat(layout.left + layout.row!!.left).isEqualTo(above.left)
    }

    @Test
    fun `a gone neighbour does not count`() {
        val card = MalletSurface(context)
        val hidden = View(context)
        hidden.visibility = View.GONE
        val layout = row()
        card.addView(hidden, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(20)))
        card.addView(layout)
        Snapshots.layout(card, 320)
        assertThat(layout.top).isEqualTo((MalletGutters.SURFACE_TOP_DP * density + rim).roundToInt())
    }

    @Test
    fun `the open first row is coloured from the rim on and follows the corner of the card`() {
        val (card, rows) = card(2)
        rows[0].open(animate = false)
        val bitmap = Snapshots.render(card, 320)
        val danger = MalletTheme.light().colors.danger
        val first = rows[0]
        val middle = first.top + first.height / 2
        assertThat(near(bitmap.getPixel(first.left + 1, middle), danger)).isTrue()
        // Under the upper rim, clear of the corner: the shaded top of the bed.
        assertThat(reddish(bitmap.getPixel(first.left + dp(12), first.top + 4))).isTrue()
        // The corner of the cell lies outside the rounded corner of the card: no colour there.
        assertThat(reddish(bitmap.getPixel(first.left, first.top))).isFalse()
        assertThat(reddish(bitmap.getPixel(first.left + 1, first.top + 1))).isFalse()
        // Down to the divider, and not a pixel into the second row.
        assertThat(reddish(bitmap.getPixel(first.left + 1, first.bottom - 3))).isTrue()
        val second = rows[1]
        assertThat(reddish(bitmap.getPixel(second.left + 1, second.top + 3))).isFalse()
        // As wide as the row has moved.
        assertThat(near(bitmap.getPixel(first.left + strip.toInt() - dp(8), middle), danger)).isTrue()
        assertThat(reddish(bitmap.getPixel(first.left + strip.toInt() + 3, middle))).isFalse()
    }

    @Test
    fun `the last row follows the lower corner and a single row both`() {
        val (card, rows) = card(2)
        rows[1].open(animate = false)
        val danger = MalletTheme.light().colors.danger
        var bitmap = Snapshots.render(card, 320)
        val last = rows[1]
        assertThat(near(bitmap.getPixel(last.left + 1, last.top + last.height / 2), danger)).isTrue()
        assertThat(reddish(bitmap.getPixel(last.left, last.bottom - 1))).isFalse()
        assertThat(reddish(bitmap.getPixel(last.left + 1, last.top + 3))).isTrue()

        val (single, only) = card(1)
        only[0].open(animate = false)
        bitmap = Snapshots.render(single, 320)
        val row = only[0]
        assertThat(reddish(bitmap.getPixel(row.left, row.top))).isFalse()
        assertThat(reddish(bitmap.getPixel(row.left, row.bottom - 1))).isFalse()
        assertThat(near(bitmap.getPixel(row.left + 1, row.top + row.height / 2), danger)).isTrue()
    }

    @Test
    fun `closed rows in a card show no colour at all`() {
        val (card, _) = card(3)
        val bitmap = Snapshots.render(card, 320)
        val danger = MalletTheme.light().colors.danger
        var coloured = 0
        for (x in 0 until bitmap.width step 2) for (y in 0 until bitmap.height step 2) {
            if (reddish(bitmap.getPixel(x, y))) coloured++
        }
        assertThat(coloured).isEqualTo(0)
    }

    /** Pixels of the white label inside the uncovered bed of [layout]: their count, lowest and highest x. */
    private fun labelPixels(card: MalletSurface, layout: MalletRevealLayout): Triple<Int, Int, Int> {
        val bitmap = Snapshots.render(card, 320)
        var count = 0
        var low = Int.MAX_VALUE
        var high = -1
        val until = layout.left + abs(layout.shift).toInt()
        // The middle band only: beyond a rounded corner the card itself is white.
        for (x in layout.left until until) for (y in layout.top + dp(12) until layout.bottom - dp(12)) {
            if (near(bitmap.getPixel(x, y), Color.WHITE, 6)) {
                count++
                if (x < low) low = x
                if (x > high) high = x
            }
        }
        return Triple(count, low - layout.left, high - layout.left)
    }

    @Test
    fun `the action comes out from the rim with the row`() {
        val (card, rows) = card(1)
        val layout = rows[0]
        layout.open(animate = false)
        val (openCount, openLow, openHigh) = labelPixels(card, layout)
        // Open: the label stands in the middle of the strip.
        assertThat(openCount).isGreaterThan(20)
        assertThat(abs((openLow + openHigh) / 2f - strip / 2f)).isLessThan(4f)
        layout.close(animate = false)
        // Held part of the way: the label has not arrived yet. What shows of it is cut by the
        // rim and lies as far behind its place as the row is short of being open.
        Touch.drag(layout, 100f, 30f, 100f + strip * 0.75f, 30f, release = false)
        val short = strip - layout.shift
        assertThat(short).isGreaterThan(strip * 0.2f)
        val (count, low, high) = labelPixels(card, layout)
        assertThat(count).isGreaterThan(0)
        assertThat(count).isLessThan(openCount)
        assertThat(low).isLessThan(3)
        assertThat(abs(high - (openHigh - short))).isLessThan(3f)
        Touch.dispatch(layout, MotionEvent.ACTION_CANCEL, 0f, 0f)
    }

    @Test
    fun `the whole uncovered bed is the action, from the rim on`() {
        val (_, rows) = card(1)
        val layout = rows[0]
        layout.open(animate = false)
        Touch.tap(layout, 1f)
        assertThat(clicks).isEqualTo(1)
        // The part of the bed that lies in the padding of the card, above the row.
        Touch.tap(layout, strip - 3f, 2f)
        assertThat(clicks).isEqualTo(2)
        assertThat(layout.isOpen).isTrue()
        Touch.tap(layout, strip + 6f)
        assertThat(clicks).isEqualTo(2)
        assertThat(layout.isOpen).isFalse()
    }

    @Test
    fun `the end edge uses the far rim and right to left mirrors both`() {
        val (card, rows) = card(1)
        val layout = rows[0]
        layout.revealEdge = MalletRevealLayout.Edge.END
        layout.open(animate = false)
        val danger = MalletTheme.light().colors.danger
        var bitmap = Snapshots.render(card, 320)
        val middle = layout.top + layout.height / 2
        assertThat(near(bitmap.getPixel(layout.right - 2, middle), danger)).isTrue()
        assertThat(reddish(bitmap.getPixel(layout.left + 1, middle))).isFalse()
        assertThat(reddish(bitmap.getPixel(layout.right - 1, layout.top))).isFalse()

        val app = context.applicationContext
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val mirrored = MalletSurface(context)
        mirrored.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val start = row()
        mirrored.addView(start)
        Snapshots.layout(mirrored, 320)
        start.open(animate = false)
        bitmap = Snapshots.render(mirrored, 320)
        assertThat(start.shift).isEqualTo(-strip)
        assertThat(near(bitmap.getPixel(start.right - 2, start.top + start.height / 2), danger)).isTrue()
        assertThat(reddish(bitmap.getPixel(start.left + 1, start.top + start.height / 2))).isFalse()
        Touch.tap(start, start.width - 2f)
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `a split card rounds only the corners it has`() {
        val top = MalletSurface(context)
        top.corners = MalletCorners.TOP
        val layout = row()
        top.addView(layout)
        Snapshots.layout(top, 320)
        layout.open(animate = false)
        val bitmap = Snapshots.render(top, 320)
        val danger = MalletTheme.light().colors.danger
        assertThat(reddish(bitmap.getPixel(layout.left, layout.top))).isFalse()
        // The lower corners of the upper half are square: the bed fills them.
        assertThat(reddish(bitmap.getPixel(layout.left + 1, layout.bottom - 3))).isTrue()
    }

    @Test
    fun `a horizontal card and a plain parent leave the layout where it is`() {
        val card = MalletSurface(context)
        card.orientation = LinearLayout.HORIZONTAL
        val inCard = row()
        card.addView(inCard, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        Snapshots.layout(card, 320)
        assertThat(inCard.left).isEqualTo(card.paddingLeft)
        assertThat(inCard.top).isEqualTo(card.paddingTop)
        assertThat(inCard.row!!.left).isEqualTo(0)

        val frame = FrameLayout(context)
        frame.setPadding(dp(9), dp(9), dp(9), dp(9))
        val inFrame = row()
        frame.addView(inFrame)
        Snapshots.layout(frame, 320)
        assertThat(inFrame.left).isEqualTo(dp(9))
        assertThat(inFrame.top).isEqualTo(dp(9))
        assertThat(inFrame.height).isEqualTo(dp(50))
    }

    @Test
    fun `a row taken out of its card and used alone is its own cell again`() {
        val (card, rows) = card(2)
        val layout = rows[0]
        card.removeView(layout)
        val frame = FrameLayout(context)
        frame.addView(layout)
        Snapshots.layout(frame, 300)
        assertThat(layout.row!!.left).isEqualTo(0)
        assertThat(layout.row!!.top).isEqualTo(0)
        assertThat(layout.height).isEqualTo(dp(50))
    }

    @Test
    fun `the platform focus highlight is off under the row's own rim`() {
        assertThat(row().defaultFocusHighlightEnabled).isFalse()
    }

    @Test
    fun `snapshot of reveal rows`() {
        gallery(MalletTheme.light(), "reveal_light")
        gallery(MalletTheme.dark(), "reveal_dark")
    }
}
