package io.github.avalonskyafar.mallet.interaction

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Parcelable
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.interaction.MalletSpatialGraph.Side
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.testing.Touch
import io.github.avalonskyafar.mallet.widget.MalletSegmentedTrack
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletSpatialHostTest {

    private val context: Context = Snapshots.context()
    private val created = HashMap<String, Int>()
    private val settled = ArrayList<String>()
    private val colours = mapOf(
        "main" to Color.WHITE, "settings" to Color.rgb(134, 172, 247), "guide" to Color.rgb(222, 190, 119),
        "more" to Color.rgb(128, 184, 151), "advanced" to Color.rgb(206, 131, 83), "about" to Color.GRAY,
    )
    private var track: MalletSegmentedTrack? = null
    private var scroller: ScrollView? = null

    private fun graph(): MalletSpatialGraph = MalletSpatialGraph.Builder("main")
        .connect("main", Side.BELOW, "settings")
        .connect("main", Side.START, "guide")
        .connect("main", Side.END, "more")
        .connect("settings", Side.END, "advanced")
        .child("more", Side.END, "about")
        .build()

    private fun page(key: String): View {
        created[key] = (created[key] ?: 0) + 1
        val colour = colours.getValue(key)
        return when (key) {
            // A page with a horizontal control in its middle.
            "more" -> FrameLayout(context).apply {
                setBackgroundColor(colour)
                val control = MalletSegmentedTrack(context).apply { setOptions("A", "B", "C") }
                track = control
                addView(control, FrameLayout.LayoutParams(-1, -2).apply { topMargin = 300 })
            }
            // A page whose content scrolls.
            "settings" -> ScrollView(context).apply {
                id = 42
                setBackgroundColor(colour)
                addView(View(context).apply { minimumHeight = 6000 })
                scroller = this
            }
            else -> View(context).apply { setBackgroundColor(colour) }
        }
    }

    private fun host(start: String = "main"): MalletSpatialHost = MalletSpatialHost(context).apply {
        id = 7
        setGraph(graph()) { page(it) }
        addListener(object : MalletSpatialHost.Listener {
            override fun onPageSettled(key: String) {
                settled += key
            }
        })
        Snapshots.layout(this, 360, 640)
        show(start)
        Snapshots.layout(this, 360, 640)
    }

    private fun land() {
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS)
    }

    private fun View.midX() = width / 2f

    @Test
    fun `show attaches exactly one page`() {
        val host = host()
        assertThat(host.childCount).isEqualTo(1)
        assertThat(host.currentKey).isEqualTo("main")
        assertThat(settled).containsExactly("main")
    }

    @Test
    fun `navigate glides to the neighbour and leaves one page attached`() {
        val host = host()
        host.navigate("settings")
        assertThat(host.isMoving).isTrue()
        assertThat(host.childCount).isEqualTo(2)
        land()
        assertThat(host.isMoving).isFalse()
        assertThat(host.childCount).isEqualTo(1)
        assertThat(host.currentKey).isEqualTo("settings")
        assertThat(host.getChildAt(0).translationY).isEqualTo(0f)
    }

    @Test
    fun `both pages travel together under the finger`() {
        val host = host()
        // Finger moves up: the page below comes into view.
        Touch.drag(host, host.midX(), 500f, host.midX(), 300f, release = false)
        assertThat(host.isMoving).isTrue()
        val incoming = host.getChildAt(0)
        val outgoing = host.getChildAt(1)
        assertThat(outgoing.translationY).isEqualTo(-200f)
        assertThat(incoming.translationY - outgoing.translationY).isEqualTo(host.height.toFloat())
    }

    @Test
    fun `releasing past the threshold lands on the new page`() {
        val host = host()
        val far = host.height * 0.4f
        Touch.drag(host, host.midX(), 600f, host.midX(), 600f - far)
        land()
        assertThat(host.currentKey).isEqualTo("settings")
    }

    @Test
    fun `releasing short of the threshold glides back`() {
        val host = host()
        val near = host.height * 0.15f
        Touch.drag(host, host.midX(), 600f, host.midX(), 600f - near)
        land()
        assertThat(host.currentKey).isEqualTo("main")
        assertThat(host.childCount).isEqualTo(1)
        assertThat(host.getChildAt(0).translationY).isEqualTo(0f)
    }

    @Test
    fun `a quick flick lands even when it is short`() {
        val host = host()
        val start = android.os.SystemClock.uptimeMillis()
        Touch.dispatch(host, MotionEvent.ACTION_DOWN, host.midX(), 600f, start)
        for (step in 1..6) {
            android.os.SystemClock.sleep(8)
            Touch.dispatch(host, MotionEvent.ACTION_MOVE, host.midX(), 600f - step * 25f, start)
        }
        android.os.SystemClock.sleep(8)
        Touch.dispatch(host, MotionEvent.ACTION_UP, host.midX(), 450f, start)
        land()
        assertThat(host.currentKey).isEqualTo("settings")
    }

    @Test
    fun `a drag towards an empty side does nothing`() {
        val host = host()
        // Finger moves down: nothing lies above the main page.
        Touch.drag(host, host.midX(), 200f, host.midX(), 500f, release = false)
        assertThat(host.isMoving).isFalse()
        assertThat(host.childCount).isEqualTo(1)
    }

    @Test
    fun `horizontal drags from the screen edge are left to the system`() {
        val host = host()
        Touch.drag(host, 6f, 300f, 400f, 300f, release = false)
        assertThat(host.isMoving).isFalse()
        Touch.dispatch(host, MotionEvent.ACTION_UP, 400f, 300f)
        // The same drag from further in moves the page.
        Touch.drag(host, 200f, 300f, 600f, 300f, release = false)
        assertThat(host.isMoving).isTrue()
    }

    @Test
    fun `a drag that starts on a control belongs to the control`() {
        val host = host("more")
        val control = track!!
        val y = control.top + control.height / 2f
        Touch.drag(host, host.width * 0.25f, y, host.width * 0.8f, y)
        assertThat(control.selectedIndex).isEqualTo(2)
        assertThat(host.isMoving).isFalse()
        assertThat(host.currentKey).isEqualTo("more")
        // Away from the control the same drag moves the page (main lies at the start side).
        Touch.drag(host, host.width * 0.25f, 100f, host.width * 0.8f, 100f, release = false)
        assertThat(host.isMoving).isTrue()
    }

    @Test
    fun `scrolling content goes first and hands over at its limit`() {
        val host = host("settings")
        val list = scroller!!
        list.scrollTo(0, 150)
        val x = host.midX()
        val start = android.os.SystemClock.uptimeMillis()
        Touch.dispatch(host, MotionEvent.ACTION_DOWN, x, 100f, start)
        // Finger moves down: the list scrolls back towards its top, the page stays.
        for (step in 1..4) Touch.dispatch(host, MotionEvent.ACTION_MOVE, x, 100f + step * 25f, start)
        assertThat(list.scrollY).isLessThan(150)
        assertThat(list.scrollY).isGreaterThan(0)
        assertThat(host.isMoving).isFalse()
        // Keep going: the list reaches its top, then the page follows the finger.
        for (step in 5..40) Touch.dispatch(host, MotionEvent.ACTION_MOVE, x, 100f + step * 25f, start)
        assertThat(list.scrollY).isEqualTo(0)
        assertThat(host.isMoving).isTrue()
        Touch.dispatch(host, MotionEvent.ACTION_UP, x, 1100f, start)
        land()
        assertThat(host.currentKey).isEqualTo("main")
    }

    /** A host on a page that has a neighbour at its start side and a row that slides aside. */
    private fun hostWithRevealRow(): Pair<MalletSpatialHost, MalletRevealLayout> {
        lateinit var row: MalletRevealLayout
        val graph = MalletSpatialGraph.Builder("main").connect("main", Side.END, "list").build()
        val host = MalletSpatialHost(context).apply {
            setGraph(graph) { key ->
                if (key != "list") {
                    View(context)
                } else {
                    FrameLayout(context).apply {
                        row = MalletRevealLayout(context).apply {
                            addView(View(context), FrameLayout.LayoutParams(-1, 160))
                            setAction("Delete") {}
                        }
                        addView(row, FrameLayout.LayoutParams(-1, -2).apply { topMargin = 200 })
                    }
                }
            }
            Snapshots.layout(this, 360, 640)
            show("list")
            Snapshots.layout(this, 360, 640)
        }
        return host to row
    }

    @Test
    fun `sideways content keeps the gesture it began, also beyond its limit`() {
        val (host, row) = hostWithRevealRow()
        val y = 280f
        // The finger travels much further than the strip is wide. The row goes as far as it
        // can and stays there; the page, whose neighbour lies the same way, does not move.
        Touch.drag(host, host.width * 0.2f, y, host.width * 0.9f, y, steps = 24, release = false)
        assertThat(host.isMoving).isFalse()
        assertThat(row.row!!.translationX).isGreaterThan(0f)
        Touch.dispatch(host, MotionEvent.ACTION_UP, host.width * 0.9f, y)
        land()
        assertThat(row.isOpen).isTrue()
        assertThat(host.currentKey).isEqualTo("list")
    }

    @Test
    fun `sideways content that has nowhere left to go leaves the drag to the page`() {
        val (host, row) = hostWithRevealRow()
        row.open(animate = false)
        val y = 280f
        // The open row cannot move further this way: the same drag now moves the page.
        Touch.drag(host, host.width * 0.3f, y, host.width * 0.8f, y, steps = 12, release = false)
        assertThat(host.isMoving).isTrue()
    }

    @Test
    fun `a page that arrives later is given the window insets`() {
        var asked = 0
        val outer = object : FrameLayout(context) {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun requestFitSystemWindows() {
                asked++
                super.requestFitSystemWindows()
            }
        }
        val host = host()
        outer.addView(host)
        asked = 0
        // A page shown without a glide, and one dragged in: both are attached late.
        host.navigate("settings", animate = false)
        assertThat(asked).isEqualTo(1)
        host.navigate("main", animate = false)
        asked = 0
        Touch.drag(host, host.width * 0.25f, 100f, host.width * 0.8f, 100f, release = false)
        assertThat(host.isMoving).isTrue()
        assertThat(asked).isEqualTo(1)
    }

    @Test
    fun `a navigation request during a drag adopts the nearer page first`() {
        val host = host()
        // 70 % of the way to the settings page, finger still down.
        Touch.drag(host, host.midX(), 1500f, host.midX(), 1500f - host.height * 0.7f, release = false)
        settled.clear()
        host.show("advanced")
        assertThat(settled).containsExactly("settings", "advanced").inOrder()

        val other = host()
        Touch.drag(other, other.midX(), 1500f, other.midX(), 1500f - other.height * 0.3f, release = false)
        settled.clear()
        other.show("more")
        assertThat(settled).containsExactly("main", "more").inOrder()
    }

    @Test
    fun `visited pages are kept unless keep alive says otherwise`() {
        val host = host()
        host.navigate("guide", animate = false)
        host.navigate("main", animate = false)
        host.navigate("guide", animate = false)
        assertThat(created["guide"]).isEqualTo(1)
        host.invalidatePage("main")
        host.navigate("main", animate = false)
        assertThat(created["main"]).isEqualTo(2)

        created.clear()
        val strict = host()
        strict.setKeepAlive(setOf("main"))
        strict.navigate("guide", animate = false)
        strict.navigate("main", animate = false)
        strict.navigate("guide", animate = false)
        assertThat(created["guide"]).isEqualTo(2)
        assertThat(created["main"]).isEqualTo(1)
    }

    @Test
    fun `back walks towards the root`() {
        val host = host("about")
        assertThat(host.canGoBack()).isTrue()
        assertThat(host.back()).isTrue()
        land()
        assertThat(host.currentKey).isEqualTo("more")
        assertThat(host.back()).isTrue()
        land()
        assertThat(host.currentKey).isEqualTo("main")
        assertThat(host.canGoBack()).isFalse()
        assertThat(host.back()).isFalse()
    }

    @Test
    fun `a system back preview follows progress then lands or returns`() {
        val host = host("guide")
        assertThat(host.beginBackPreview()).isTrue()
        host.updateBackPreview(1f)
        val outgoing = host.getChildAt(1)
        // The parent lies at the end side, so the page moves towards the start by 40 %.
        assertThat(outgoing.translationX).isWithin(1f).of(-host.width * MalletSpatialHost.BACK_PREVIEW_FRACTION)
        host.endBackPreview(commit = false)
        land()
        assertThat(host.currentKey).isEqualTo("guide")
        assertThat(host.beginBackPreview()).isTrue()
        host.endBackPreview(commit = true)
        land()
        assertThat(host.currentKey).isEqualTo("main")
        assertThat(host.beginBackPreview()).isFalse()
    }

    @Test
    fun `the current page and its scroll position survive save and restore`() {
        val host = host()
        host.navigate("settings", animate = false)
        Snapshots.layout(host, 360, 640)
        scroller!!.scrollTo(0, 420)
        val state = SparseArray<Parcelable>()
        host.saveHierarchyState(state)

        val restored = host()
        restored.restoreHierarchyState(state)
        Snapshots.layout(restored, 360, 640)
        assertThat(restored.currentKey).isEqualTo("settings")
        assertThat(scroller!!.scrollY).isEqualTo(420)
    }

    @Test
    fun `refreshing the current page keeps what it had saved`() {
        val host = host()
        host.navigate("settings", animate = false)
        Snapshots.layout(host, 360, 640)
        val before = scroller!!
        before.scrollTo(0, 300)
        host.refreshCurrent()
        Snapshots.layout(host, 360, 640)
        assertThat(scroller).isNotSameInstanceAs(before)
        assertThat(scroller!!.scrollY).isEqualTo(300)
        assertThat(host.currentKey).isEqualTo("settings")
        assertThat(host.childCount).isEqualTo(1)
    }

    @Test
    fun `right to left layouts swap the start and end sides`() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val host = host()
        host.layoutDirection = View.LAYOUT_DIRECTION_RTL
        // In RTL the start side is on the right, so a finger moving left reveals it.
        Touch.drag(host, 700f, 300f, 700f - host.width * 0.5f, 300f)
        land()
        assertThat(host.currentKey).isEqualTo("guide")
    }

    @Test
    fun `listeners hear how far the pages have moved`() {
        val host = host()
        val fractions = ArrayList<Float>()
        host.addListener(object : MalletSpatialHost.Listener {
            override fun onPageScrolled(from: String, to: String, fraction: Float) {
                assertThat(from).isEqualTo("main")
                assertThat(to).isEqualTo("settings")
                fractions += fraction
            }
        })
        Touch.drag(host, host.midX(), 1500f, host.midX(), 1500f - host.height * 0.5f, release = false)
        assertThat(fractions.last()).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `a new graph removes the page on screen so that show builds it again`() {
        val host = host()
        host.setGraph(graph()) { page(it) }
        assertThat(host.currentKey).isNull()
        assertThat(host.childCount).isEqualTo(0)
        host.show("main")
        assertThat(created["main"]).isEqualTo(2)
        assertThat(host.childCount).isEqualTo(1)
        assertThat(host.currentKey).isEqualTo("main")
    }

    @Test
    fun `the drag goes on with the second finger when the first one lifts`() {
        val host = host()
        val start = host.width * 0.6f
        val pulled = host.width * 0.1f
        val other = host.width * 0.3f
        Touch.dispatch(host, MotionEvent.ACTION_DOWN, start, 300f)
        Touch.multi(host, MotionEvent.ACTION_MOVE, 0, Touch.Finger(0, start - pulled, 300f))
        val outgoing = host.getChildAt(1)
        assertThat(outgoing.translationX).isWithin(0.01f).of(-pulled)
        val both = arrayOf(Touch.Finger(0, start - pulled, 300f), Touch.Finger(1, other, 500f))
        Touch.multi(host, MotionEvent.ACTION_POINTER_DOWN, 1, *both)
        Touch.multi(host, MotionEvent.ACTION_POINTER_UP, 0, *both)
        Touch.multi(host, MotionEvent.ACTION_MOVE, 0, Touch.Finger(1, other, 500f))
        assertThat(outgoing.translationX).isWithin(0.01f).of(-pulled)
        Touch.multi(host, MotionEvent.ACTION_MOVE, 0, Touch.Finger(1, other - 10f, 500f))
        assertThat(outgoing.translationX).isWithin(0.01f).of(-pulled - 10f)
        Touch.multi(host, MotionEvent.ACTION_UP, 0, Touch.Finger(1, other - 10f, 500f))
        land()
        assertThat(host.currentKey).isEqualTo("main")
    }

    @Test
    fun `a flick with the finger that took over lands`() {
        val host = host()
        val both = arrayOf(Touch.Finger(0, host.midX(), 600f), Touch.Finger(1, 100f, 600f))
        Touch.dispatch(host, MotionEvent.ACTION_DOWN, host.midX(), 600f)
        Touch.multi(host, MotionEvent.ACTION_POINTER_DOWN, 1, *both)
        Touch.multi(host, MotionEvent.ACTION_POINTER_UP, 0, *both)
        for (step in 1..6) {
            android.os.SystemClock.sleep(8)
            Touch.multi(host, MotionEvent.ACTION_MOVE, 0, Touch.Finger(1, 100f, 600f - step * 25f))
        }
        android.os.SystemClock.sleep(8)
        Touch.multi(host, MotionEvent.ACTION_UP, 0, Touch.Finger(1, 100f, 450f))
        land()
        assertThat(host.currentKey).isEqualTo("settings")
    }

    @Test
    fun `scrolling content can keep the ancestors of the host out of its gesture`() {
        val asked = ArrayList<Boolean>()
        val outer = object : FrameLayout(context) {
            override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                asked += disallowIntercept
                super.requestDisallowInterceptTouchEvent(disallowIntercept)
            }
        }
        val host = host("settings")
        outer.addView(host)
        Touch.dispatch(host, MotionEvent.ACTION_DOWN, host.midX(), 300f)
        // What a list inside the page does once it starts to scroll.
        host.requestDisallowInterceptTouchEvent(true)
        assertThat(asked).containsExactly(true)
        // The host itself still watches, to take over when the content reaches its limit.
        Touch.drag(host, host.midX(), 300f, host.midX(), 900f, release = false)
        assertThat(host.isMoving).isTrue()
    }

    /** Scrolls "settings", returns to "main" and saves the host. */
    private fun savedWithSettingsScrolled(): SparseArray<Parcelable> {
        val host = host()
        host.navigate("settings", animate = false)
        Snapshots.layout(host, 360, 640)
        scroller!!.scrollTo(0, 420)
        host.navigate("main", animate = false)
        return SparseArray<Parcelable>().also { host.saveHierarchyState(it) }
    }

    @Test
    fun `a page warmed up after a restore still gets its saved state`() {
        val state = savedWithSettingsScrolled()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val restored = MalletSpatialHost(context).apply {
            id = 7
            prewarmNeighbours = true
            setGraph(graph()) { page(it) }
        }
        activity.setContentView(restored)
        restored.restoreHierarchyState(state)
        Snapshots.idle()
        // The warm-up has built the neighbour: the visit below takes that view.
        assertThat(created["settings"]).isEqualTo(2)
        restored.navigate("settings", animate = false)
        Snapshots.idle()
        assertThat(created["settings"]).isEqualTo(2)
        assertThat(scroller!!.parent).isSameInstanceAs(restored)
        assertThat(scroller!!.scrollY).isEqualTo(420)
    }

    @Test
    fun `state of a page that is not visited between two restores is kept`() {
        val first = savedWithSettingsScrolled()
        val between = host()
        between.restoreHierarchyState(first)
        val second = SparseArray<Parcelable>()
        between.saveHierarchyState(second)

        val restored = host()
        restored.restoreHierarchyState(second)
        restored.navigate("settings", animate = false)
        Snapshots.layout(restored, 360, 640)
        assertThat(scroller!!.parent).isSameInstanceAs(restored)
        assertThat(scroller!!.scrollY).isEqualTo(420)
    }

    @Test
    fun `a page built before the restore gets its saved state too`() {
        val host = host()
        host.navigate("settings", animate = false)
        Snapshots.layout(host, 360, 640)
        scroller!!.scrollTo(0, 420)
        host.navigate("advanced", animate = false)
        val state = SparseArray<Parcelable>()
        host.saveHierarchyState(state)

        // The usual onCreate: the start page is shown before the saved state arrives, and
        // nothing has been laid out yet (a ScrollView takes its position on its first layout).
        val restored = MalletSpatialHost(context).apply {
            id = 7
            setGraph(graph()) { page(it) }
            show("settings")
        }
        val early = scroller!!
        restored.restoreHierarchyState(state)
        assertThat(restored.currentKey).isEqualTo("advanced")
        restored.navigate("settings", animate = false)
        Snapshots.layout(restored, 360, 640)
        assertThat(scroller).isSameInstanceAs(early)
        assertThat(early.parent).isSameInstanceAs(restored)
        assertThat(early.scrollY).isEqualTo(420)
    }

    @Test
    fun `snapshot of two pages edge to edge mid drag`() {
        val host = host()
        Touch.drag(host, 800f, 300f, 800f - host.width * 0.4f, 300f, release = false)
        Snapshots.layout(host, 360, 640)
        val bitmap = android.graphics.Bitmap.createBitmap(host.width, host.height, android.graphics.Bitmap.Config.ARGB_8888)
        host.draw(android.graphics.Canvas(bitmap))
        Snapshots.save(bitmap, "spatial_drag_light")
        // Left: the main page on its way out. Right: the page from the end side coming in.
        assertThat(bitmap.getPixel(40, 40)).isEqualTo(colours.getValue("main"))
        assertThat(bitmap.getPixel(host.width - 40, 40)).isEqualTo(colours.getValue("more"))
    }
}
