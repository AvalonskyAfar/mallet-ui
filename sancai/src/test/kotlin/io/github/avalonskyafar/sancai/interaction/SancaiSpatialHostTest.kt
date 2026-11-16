package io.github.avalonskyafar.sancai.interaction

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
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialGraph.Side
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiSpatialHostTest {

    private val context: Context = Snapshots.context()
    private val created = HashMap<String, Int>()
    private val settled = ArrayList<String>()
    private val colours = mapOf(
        "main" to Color.WHITE, "settings" to Color.rgb(134, 172, 247), "guide" to Color.rgb(222, 190, 119),
        "more" to Color.rgb(128, 184, 151), "advanced" to Color.rgb(206, 131, 83), "about" to Color.GRAY,
    )
    private var track: SancaiSegmentedTrack? = null
    private var scroller: ScrollView? = null

    private fun graph(): SancaiSpatialGraph = SancaiSpatialGraph.Builder("main")
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
                val control = SancaiSegmentedTrack(context).apply { setOptions("A", "B", "C") }
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

    private fun host(start: String = "main"): SancaiSpatialHost = SancaiSpatialHost(context).apply {
        id = 7
        setGraph(graph()) { page(it) }
        addListener(object : SancaiSpatialHost.Listener {
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
        assertThat(outgoing.translationX).isWithin(1f).of(-host.width * SancaiSpatialHost.BACK_PREVIEW_FRACTION)
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
        host.addListener(object : SancaiSpatialHost.Listener {
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
