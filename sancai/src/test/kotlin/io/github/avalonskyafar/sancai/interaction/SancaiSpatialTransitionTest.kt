package io.github.avalonskyafar.sancai.interaction

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialGraph.Side
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiText
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiSpatialTransitionTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private var finished = 0

    @After
    fun tearDown() {
        SancaiSpatialSnapshots.clear()
    }

    private fun advance(ms: Long = 600) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)
    }

    private fun solid(color: Int = BACKDROP, width: Int = 40, height: Int = 40): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    @Test
    fun `snapshot store hands a picture out once`() {
        val bitmap = solid()
        val token = SancaiSpatialSnapshots.put(bitmap)
        assertThat(token).isNotEqualTo(0)
        assertThat(SancaiSpatialSnapshots.size).isEqualTo(1)
        assertThat(SancaiSpatialSnapshots.take(token)).isSameInstanceAs(bitmap)
        assertThat(SancaiSpatialSnapshots.take(token)).isNull()
        assertThat(SancaiSpatialSnapshots.take(0)).isNull()
        assertThat(SancaiSpatialSnapshots.take(token + 100)).isNull()
    }

    @Test
    fun `snapshot store refuses a picture above sixteen megabytes`() {
        // Counted as 32-bit pixels whatever the config: 2100 x 2000 x 4 = 16.8 MB.
        val large = Bitmap.createBitmap(2100, 2000, Bitmap.Config.ALPHA_8)
        assertThat(SancaiSpatialSnapshots.put(large)).isEqualTo(0)
        assertThat(large.isRecycled).isFalse()
        assertThat(SancaiSpatialSnapshots.size).isEqualTo(0)
        // 2048 x 2048 x 4 is exactly the limit and still accepted.
        val limit = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ALPHA_8)
        assertThat(SancaiSpatialSnapshots.put(limit)).isNotEqualTo(0)

        val recycled = solid().also { it.recycle() }
        assertThat(SancaiSpatialSnapshots.put(recycled)).isEqualTo(0)
    }

    @Test
    fun `snapshot store drops stale pictures`() {
        val first = solid()
        val firstToken = SancaiSpatialSnapshots.put(first)
        val second = solid()
        val secondToken = SancaiSpatialSnapshots.put(second)
        val thirdToken = SancaiSpatialSnapshots.put(solid())
        assertThat(SancaiSpatialSnapshots.size).isEqualTo(SancaiSpatialSnapshots.MAX_PENDING)
        assertThat(first.isRecycled).isTrue()
        assertThat(SancaiSpatialSnapshots.take(firstToken)).isNull()
        assertThat(SancaiSpatialSnapshots.take(secondToken)).isSameInstanceAs(second)
        assertThat(SancaiSpatialSnapshots.take(thirdToken)).isNotNull()
    }

    @Test
    fun `page glides in from the end and the picture leaves the other way`() {
        val page = Page(context, "Developer options")
        val host = host(page = page)
        assertThat(page.visibility).isEqualTo(View.INVISIBLE)
        // First frame (laid out, the glide not yet started): the page is one width away and
        // the picture fills the host.
        Snapshots.layout(host, 360, PAGE_HEIGHT_DP)
        val width = host.width.toFloat()
        assertThat(width).isGreaterThan(0f)
        assertThat(host.isMoving).isTrue()
        assertThat(page.visibility).isEqualTo(View.VISIBLE)
        assertThat(host.pageOffset).isEqualTo(width)
        assertThat(page.translationX).isEqualTo(width)
        assertThat(host.getChildAt(0).translationX).isEqualTo(0f)

        Snapshots.attach(host, 360)
        advance()
        assertThat(host.isMoving).isFalse()
        assertThat(page.translationX).isEqualTo(0f)
        assertThat(host.getChildAt(0).translationX).isEqualTo(-width)
        assertThat(finished).isEqualTo(0)
    }

    @Test
    fun `a slow swipe past the threshold finishes, with reduced motion too`() {
        // Frame times of the JVM test clock make animators jump, so only the outcome of a
        // settle is asserted, not its duration.
        val normal = settledHost()
        val width = normal.width.toFloat()
        timedDrag(normal, width * 0.3f, width * 0.7f, totalMs = 2000)
        assertThat(normal.isMoving).isTrue()
        assertThat(finished).isEqualTo(0)
        advance()
        assertThat(finished).isEqualTo(1)

        val reduced = settledHost(themed = Snapshots.context(SancaiTheme.light().copy(reduceMotion = true)))
        timedDrag(reduced, width * 0.3f, width * 0.7f, totalMs = 2000)
        advance()
        assertThat(finished).isEqualTo(2)
        assertThat(reduced.pageOffset).isEqualTo(width)
    }

    @Test
    fun `touches are swallowed while the page comes in`() {
        val host = host()
        Snapshots.layout(host, 360, PAGE_HEIGHT_DP)
        val width = host.width.toFloat()
        Touch.drag(host, width * 0.3f, titleY(host), width * 0.9f, titleY(host))
        assertThat(host.pageOffset).isEqualTo(width)
        Snapshots.attach(host, 360)
        advance()
        assertThat(finished).isEqualTo(0)
        assertThat(host.pageOffset).isEqualTo(0f)
    }

    @Test
    fun `swipe past the threshold finishes when the settle ends`() {
        val host = settledHost()
        val width = host.width.toFloat()
        val y = titleY(host)
        Touch.drag(host, width * 0.3f, y, width * 0.65f, y, release = false)
        // Follows the finger exactly: no slop offset, no lag.
        assertThat(host.pageOffset).isWithin(0.5f).of(width * 0.35f)
        assertThat(host.getChildAt(0).translationX).isWithin(0.5f).of(width * 0.35f - width)
        Touch.dispatch(host, MotionEvent.ACTION_UP, width * 0.65f, y)
        assertThat(host.isMoving).isTrue()
        assertThat(finished).isEqualTo(0)
        advance()
        assertThat(finished).isEqualTo(1)
        assertThat(host.pageOffset).isEqualTo(width)
        assertThat(host.getChildAt(0).translationX).isEqualTo(0f)
    }

    @Test
    fun `swipe short of the threshold returns`() {
        val host = settledHost()
        val width = host.width.toFloat()
        val y = titleY(host)
        // Slowly, so that only the distance counts: 20 % is short of the 28 % threshold.
        timedDrag(host, width * 0.3f, width * 0.5f, totalMs = 2000, release = false)
        assertThat(host.pageOffset).isWithin(0.5f).of(width * 0.2f)
        sendAt(host, MotionEvent.ACTION_UP, width * 0.5f, DOWN_TIME + 2000)
        assertThat(host.isMoving).isTrue()
        advance()
        assertThat(finished).isEqualTo(0)
        assertThat(host.pageOffset).isEqualTo(0f)
        assertThat(host.isMoving).isFalse()
        // And the page is ready for the next gesture.
        Touch.drag(host, width * 0.3f, y, width * 0.7f, y)
        advance()
        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun `cancelled gesture returns even past the threshold`() {
        val host = settledHost()
        val width = host.width.toFloat()
        val y = titleY(host)
        Touch.drag(host, width * 0.3f, y, width * 0.8f, y, release = false)
        Touch.dispatch(host, MotionEvent.ACTION_CANCEL, width * 0.8f, y)
        advance()
        assertThat(finished).isEqualTo(0)
        assertThat(host.pageOffset).isEqualTo(0f)
    }

    @Test
    fun `swipe away from the entry side or vertically does nothing`() {
        val host = settledHost()
        val width = host.width.toFloat()
        val y = titleY(host)
        Touch.drag(host, width * 0.7f, y, width * 0.2f, y, release = false)
        assertThat(host.pageOffset).isEqualTo(0f)
        Touch.dispatch(host, MotionEvent.ACTION_UP, width * 0.2f, y)
        Touch.drag(host, width * 0.5f, y, width * 0.6f, y + width * 0.5f, release = false)
        assertThat(host.pageOffset).isEqualTo(0f)
        Touch.dispatch(host, MotionEvent.ACTION_UP, width * 0.6f, y + width * 0.5f)
        advance()
        assertThat(finished).isEqualTo(0)
    }

    @Test
    fun `swipe starting on a track, a text field or in the edge zone does nothing`() {
        val page = Page(context, "Developer options")
        val host = host(page = page)
        Snapshots.attach(host, 360)
        advance()
        val width = host.width.toFloat()
        val card = page.track.parent as View
        val trackY = card.top + page.track.top + page.track.height / 2f
        Touch.drag(host, width * 0.3f, trackY, width * 0.8f, trackY, release = false)
        assertThat(host.pageOffset).isEqualTo(0f)
        Touch.dispatch(host, MotionEvent.ACTION_UP, width * 0.8f, trackY)

        val fieldY = page.field.top + page.field.height / 2f
        Touch.drag(host, width * 0.3f, fieldY, width * 0.8f, fieldY, release = false)
        assertThat(host.pageOffset).isEqualTo(0f)
        Touch.dispatch(host, MotionEvent.ACTION_UP, width * 0.8f, fieldY)

        // 24 dp at 2.625 is 63 px: 40 px is inside the zone, the far edge mirrors it.
        val y = titleY(host)
        Touch.drag(host, 40f, y, width * 0.8f, y, release = false)
        assertThat(host.pageOffset).isEqualTo(0f)
        Touch.dispatch(host, MotionEvent.ACTION_UP, width * 0.8f, y)
        advance()
        assertThat(finished).isEqualTo(0)

        // Just inside the zone boundary the swipe is taken.
        Touch.drag(host, 70f, y, width * 0.8f, y)
        advance()
        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun `gate refuses the swipe and is asked once per gesture`() {
        val host = settledHost()
        val width = host.width.toFloat()
        val y = titleY(host)
        var allowed = false
        var asked = 0
        SancaiSpatialTransition.setSwipeGate(host) {
            asked++
            allowed
        }
        Touch.drag(host, width * 0.3f, y, width * 0.8f, y, release = false)
        assertThat(host.pageOffset).isEqualTo(0f)
        Touch.dispatch(host, MotionEvent.ACTION_UP, width * 0.8f, y)
        advance()
        assertThat(finished).isEqualTo(0)
        assertThat(asked).isEqualTo(1)

        allowed = true
        Touch.drag(host, width * 0.3f, y, width * 0.8f, y)
        advance()
        assertThat(finished).isEqualTo(1)
        assertThat(asked).isEqualTo(2)

        assertThrows(IllegalArgumentException::class.java) {
            SancaiSpatialTransition.setSwipeGate(View(context), null)
        }
    }

    @Test
    fun `without a picture the page is there at once and still swipes back`() {
        val page = Page(context, "Developer options")
        val host = host(snapshot = null, page = page)
        Snapshots.attach(host, 360)
        assertThat(host.hasSnapshot).isFalse()
        assertThat(host.isMoving).isFalse()
        assertThat(page.visibility).isEqualTo(View.VISIBLE)
        assertThat(page.translationX).isEqualTo(0f)
        assertThat(host.getChildAt(0).visibility).isEqualTo(View.GONE)
        // Nothing to reveal: finishing is left to the window animation.
        assertThat(host.finishProgrammatically()).isFalse()

        val width = host.width.toFloat()
        Touch.drag(host, width * 0.3f, titleY(host), width * 0.8f, titleY(host))
        advance()
        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun `a recycled picture counts as none`() {
        val host = host(snapshot = solid().also { it.recycle() })
        Snapshots.attach(host, 360)
        assertThat(host.hasSnapshot).isFalse()
        assertThat(host.isMoving).isFalse()
    }

    @Test
    fun `page from the start side comes from the left and leaves to the left`() {
        val host = host(side = Side.START)
        Snapshots.layout(host, 360, PAGE_HEIGHT_DP)
        val width = host.width.toFloat()
        assertThat(host.pageOffset).isEqualTo(-width)
        Snapshots.attach(host, 360)
        advance()
        assertThat(host.pageOffset).isEqualTo(0f)
        assertThat(host.getChildAt(0).translationX).isEqualTo(width)
        val y = titleY(host)
        Touch.drag(host, width * 0.3f, y, width * 0.8f, y)
        advance()
        assertThat(finished).isEqualTo(0)
        Touch.drag(host, width * 0.8f, y, width * 0.3f, y)
        advance()
        assertThat(finished).isEqualTo(1)
        assertThat(host.pageOffset).isEqualTo(-width)
    }

    @Test
    fun `end is the left edge in right-to-left layouts`() {
        val rtl = rtlContext()
        assertThat(SancaiSpatialTransition.direction(context, Side.END)).isEqualTo(1)
        assertThat(SancaiSpatialTransition.direction(context, Side.START)).isEqualTo(-1)
        assertThat(SancaiSpatialTransition.direction(rtl, Side.END)).isEqualTo(-1)
        assertThat(SancaiSpatialTransition.direction(rtl, Side.START)).isEqualTo(1)

        val host = host(themed = rtl)
        Snapshots.layout(host, 360, PAGE_HEIGHT_DP)
        val width = host.width.toFloat()
        assertThat(host.pageOffset).isEqualTo(-width)
        Snapshots.attach(host, 360)
        advance()
        Touch.drag(host, width * 0.8f, titleY(host), width * 0.3f, titleY(host))
        advance()
        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun `vertical sides are rejected`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertThrows(IllegalArgumentException::class.java) {
            SancaiSpatialTransition.start(activity, Intent(activity, Activity::class.java), Side.ABOVE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SancaiSpatialSwipeHost(context, View(context), null, Side.BELOW)
        }
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    @Test
    fun `start takes a picture, passes its token and switches the window animation off`() {
        val activity = Snapshots.attach(Page(context, "Overview"), 360)
        val intent = Intent(activity, Activity::class.java)
        SancaiSpatialTransition.start(activity, intent, Side.START)
        val started = shadowOf(activity).nextStartedActivity
        assertThat(started).isNotNull()
        val token = started.getIntExtra(SancaiSpatialTransition.EXTRA_SNAPSHOT, 0)
        assertThat(token).isNotEqualTo(0)
        assertThat(started.getStringExtra(SancaiSpatialTransition.EXTRA_ENTER_FROM)).isEqualTo("START")
        assertThat(shadowOf(activity).pendingTransitionEnterAnimationResourceId).isEqualTo(0)
        assertThat(shadowOf(activity).pendingTransitionExitAnimationResourceId).isEqualTo(0)
        val content = activity.findViewById<View>(android.R.id.content)
        val picture = SancaiSpatialSnapshots.take(token)
        assertThat(picture).isNotNull()
        assertThat(picture!!.width).isEqualTo(content.width)
        assertThat(picture.height).isEqualTo(content.height)
    }

    @Test
    fun `start without a picture uses the slide animations`() {
        // Created but never shown: the content has no size, so there is nothing to draw.
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        SancaiSpatialTransition.start(activity, Intent(activity, Activity::class.java))
        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.hasExtra(SancaiSpatialTransition.EXTRA_SNAPSHOT)).isFalse()
        assertThat(started.getStringExtra(SancaiSpatialTransition.EXTRA_ENTER_FROM)).isEqualTo("END")
        assertThat(shadowOf(activity).pendingTransitionEnterAnimationResourceId)
            .isEqualTo(R.anim.sancai_slide_in_end)
        assertThat(shadowOf(activity).pendingTransitionExitAnimationResourceId)
            .isEqualTo(R.anim.sancai_slide_out_start)
    }

    @Test
    fun `wrap claims the picture and finish glides the page out first`() {
        val picture = solid()
        val intent = Intent()
            .putExtra(SancaiSpatialTransition.EXTRA_SNAPSHOT, SancaiSpatialSnapshots.put(picture))
            .putExtra(SancaiSpatialTransition.EXTRA_ENTER_FROM, "END")
        val activity = Robolectric.buildActivity(Activity::class.java, intent).setup().get()
        val wrapped = SancaiSpatialTransition.wrap(activity, Page(context, "Developer options"))
        activity.setContentView(wrapped)
        Snapshots.idle()
        assertThat(SancaiSpatialSnapshots.size).isEqualTo(0)
        val host = wrapped as SancaiSpatialSwipeHost
        assertThat(host.hasSnapshot).isTrue()
        advance()
        assertThat(host.pageOffset).isEqualTo(0f)

        SancaiSpatialTransition.finish(activity)
        assertThat(activity.isFinishing).isFalse()
        SancaiSpatialTransition.finish(activity)
        advance()
        assertThat(activity.isFinishing).isTrue()
        assertThat(host.pageOffset).isEqualTo(host.width.toFloat())
        assertThat(shadowOf(activity).pendingTransitionEnterAnimationResourceId).isEqualTo(0)

        (host.parent as ViewGroup).removeView(host)
        assertThat(picture.isRecycled).isTrue()
    }

    @Test
    fun `finish interrupts the entry`() {
        val host = host()
        Snapshots.layout(host, 360, PAGE_HEIGHT_DP)
        assertThat(host.pageOffset).isEqualTo(host.width.toFloat())
        assertThat(host.finishProgrammatically()).isTrue()
        Snapshots.attach(host, 360)
        advance()
        assertThat(finished).isEqualTo(1)
        assertThat(host.pageOffset).isEqualTo(host.width.toFloat())
    }

    @Test
    fun `finish without a picture falls back to the slide animations`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(SancaiSpatialTransition.wrap(activity, Page(context, "Direct")))
        Snapshots.idle()
        SancaiSpatialTransition.finish(activity)
        assertThat(activity.isFinishing).isTrue()
        // The page came from the end, so it leaves to the right and the parent follows from the left.
        assertThat(shadowOf(activity).pendingTransitionEnterAnimationResourceId)
            .isEqualTo(R.anim.sancai_slide_in_start)
        assertThat(shadowOf(activity).pendingTransitionExitAnimationResourceId)
            .isEqualTo(R.anim.sancai_slide_out_end)

        val plain = Robolectric.buildActivity(Activity::class.java).setup().get()
        plain.setContentView(View(plain))
        SancaiSpatialTransition.finish(plain)
        assertThat(plain.isFinishing).isTrue()
    }

    @Test
    fun `an activity that implements the gate is asked`() {
        val activity = Robolectric.buildActivity(GatedActivity::class.java).setup().get()
        val host = SancaiSpatialTransition.wrap(activity, Page(context, "Busy"))
        activity.setContentView(host)
        Snapshots.idle()
        val width = host.width.toFloat()
        Touch.drag(host, width * 0.3f, titleY(host), width * 0.8f, titleY(host))
        advance()
        assertThat(activity.isFinishing).isFalse()
        activity.busy = false
        Touch.drag(host, width * 0.3f, titleY(host), width * 0.8f, titleY(host))
        advance()
        assertThat(activity.isFinishing).isTrue()
    }

    private fun sendAt(host: View, action: Int, x: Float, time: Long) {
        val event = MotionEvent.obtain(DOWN_TIME, time, action, x, titleY(host), 0)
        host.dispatchTouchEvent(event)
        event.recycle()
    }

    /**
     * A drag along the title with real timestamps, so that its speed is known: `Touch.drag`
     * sends every event at the same instant, which a velocity tracker reads as a flick.
     */
    private fun timedDrag(host: View, fromX: Float, toX: Float, totalMs: Long, release: Boolean = true) {
        val steps = 10
        sendAt(host, MotionEvent.ACTION_DOWN, fromX, DOWN_TIME)
        for (step in 1..steps) {
            sendAt(host, MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * step / steps, DOWN_TIME + totalMs * step / steps)
        }
        if (release) sendAt(host, MotionEvent.ACTION_UP, toX, DOWN_TIME + totalMs)
    }

    @Test
    fun `a flick finishes although it is short, a slow drag of the same length returns`() {
        // 150 px is 16 % of the width, short of the threshold; 320 dp/s is 840 px/s here.
        val slow = settledHost()
        timedDrag(slow, 300f, 450f, totalMs = 1500) // 100 px/s
        advance()
        assertThat(finished).isEqualTo(0)
        assertThat(slow.pageOffset).isEqualTo(0f)

        val fast = settledHost()
        timedDrag(fast, 300f, 450f, totalMs = 60) // 2500 px/s
        advance()
        assertThat(finished).isEqualTo(1)
    }

    /** Sends an event with two fingers down: ids 0 and 1, in that order. */
    private fun sendTwo(host: View, action: Int, firstX: Float, secondX: Float, time: Long) {
        val y = titleY(host)
        val properties = Array(2) { MotionEvent.PointerProperties().apply { id = it } }
        val coords = Array(2) { MotionEvent.PointerCoords() }
        coords[0].x = firstX
        coords[0].y = y
        coords[1].x = secondX
        coords[1].y = y
        val event = MotionEvent.obtain(DOWN_TIME, time, action, 2, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        host.dispatchTouchEvent(event)
        event.recycle()
    }

    @Test
    fun `a second finger does not move the page and the first one lifting ends the swipe`() {
        val host = settledHost()
        val width = host.width.toFloat()
        val pointerDown = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        timedDrag(host, width * 0.3f, width * 0.5f, totalMs = 2000, release = false)
        sendTwo(host, pointerDown, width * 0.5f, width * 0.9f, DOWN_TIME + 2100)
        sendTwo(host, MotionEvent.ACTION_MOVE, width * 0.5f, width * 0.95f, DOWN_TIME + 2200)
        assertThat(host.pageOffset).isWithin(0.5f).of(width * 0.2f)
        // The first finger lifts (pointer index 0): the page must not jump to the second one.
        sendTwo(host, MotionEvent.ACTION_POINTER_UP, width * 0.5f, width * 0.95f, DOWN_TIME + 4000)
        assertThat(host.pageOffset).isWithin(0.5f).of(width * 0.2f)
        assertThat(host.isMoving).isTrue()
        advance()
        assertThat(finished).isEqualTo(0)
        assertThat(host.pageOffset).isEqualTo(0f)
    }

    @Test
    fun `an empty page and a page with large long text swipe back the same way`() {
        val large = SancaiTheme.wrap(context, SancaiTheme.light().copy(textScale = 1.13f))
        val pages = listOf(
            FrameLayout(context),
            Page(large, "Developer options with a very long title that cannot fit on one line at all"),
        )
        for ((index, page) in pages.withIndex()) {
            val host = host(page = page)
            Snapshots.attach(host, 360)
            advance()
            assertThat(host.pageOffset).isEqualTo(0f)
            assertThat(page.width).isEqualTo(host.width)
            val y = titleY(host)
            Touch.drag(host, host.width * 0.3f, y, host.width * 0.7f, y)
            advance()
            assertThat(finished).isEqualTo(index + 1)
        }
    }

    @Test
    fun `detaching recycles the picture and leaves the page at rest`() {
        val picture = solid()
        val page = Page(context, "Developer options")
        val host = host(snapshot = picture, page = page)
        Snapshots.attach(host, 360)
        advance(SancaiMotion.PAGE_MS / 2)
        (host.parent as ViewGroup).removeView(host)
        assertThat(picture.isRecycled).isTrue()
        assertThat(host.hasSnapshot).isFalse()
        assertThat(host.isMoving).isFalse()
        assertThat(page.translationX).isEqualTo(0f)
        advance()
        assertThat(finished).isEqualTo(0)
    }

    @Test
    fun `the picture is hidden from accessibility and the page is not`() {
        val page = Page(context, "Developer options")
        val host = host(page = page)
        assertThat(host.getChildAt(0).importantForAccessibility)
            .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        assertThat(host.getChildAt(1)).isSameInstanceAs(page)
        assertThat(host.isFocusable).isFalse()
    }

    @Test
    fun `xml animations inflate with the glide curve and the page durations`() {
        val full = listOf(
            R.anim.sancai_slide_in_start, R.anim.sancai_slide_in_end,
            R.anim.sancai_slide_out_start, R.anim.sancai_slide_out_end,
        )
        val reduced = listOf(
            R.anim.sancai_slide_in_start_reduced, R.anim.sancai_slide_in_end_reduced,
            R.anim.sancai_slide_out_start_reduced, R.anim.sancai_slide_out_end_reduced,
        )
        val glide = SancaiMotion.glide()
        for (id in full + reduced) {
            val animation = AnimationUtils.loadAnimation(context, id)
            val expected = if (id in full) SancaiMotion.PAGE_MS else SancaiMotion.REDUCED_MS
            assertThat(animation.duration).isEqualTo(expected)
            for (t in listOf(0.1f, 0.5f, 0.9f)) {
                assertThat(animation.interpolator.getInterpolation(t))
                    .isWithin(0.002f).of(glide.getInterpolation(t))
            }
        }
        assertThat(SancaiSpatialTransition.slideIn(fromLeft = true, reduced = false))
            .isEqualTo(R.anim.sancai_slide_in_start)
        assertThat(SancaiSpatialTransition.slideIn(fromLeft = false, reduced = true))
            .isEqualTo(R.anim.sancai_slide_in_end_reduced)
        assertThat(SancaiSpatialTransition.slideOut(toLeft = true, reduced = true))
            .isEqualTo(R.anim.sancai_slide_out_start_reduced)
        assertThat(SancaiSpatialTransition.slideOut(toLeft = false, reduced = false))
            .isEqualTo(R.anim.sancai_slide_out_end)
    }

    private fun draw(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    @Test
    fun `page and picture meet edge to edge with nothing in between`() {
        val host = settledHost()
        val canvasColor = SancaiTheme.of(context).colors.canvas
        val width = host.width
        // At rest the picture is entirely off screen.
        val rest = draw(host)
        assertThat(rest.getPixel(4, 4)).isEqualTo(canvasColor)
        assertThat(rest.getPixel(width - 4, host.height - 4)).isEqualTo(canvasColor)

        val y = titleY(host)
        Touch.drag(host, 300f, y, 600f, y, release = false)
        assertThat(host.pageOffset).isEqualTo(300f)
        val dragged = draw(host)
        // Left of the seam: the picture, unscaled and undimmed. Right of it: the page.
        assertThat(dragged.getPixel(4, 4)).isEqualTo(BACKDROP)
        assertThat(dragged.getPixel(299, host.height / 2)).isEqualTo(BACKDROP)
        assertThat(dragged.getPixel(300, 4)).isEqualTo(canvasColor)
        assertThat(dragged.getPixel(300, host.height - 4)).isEqualTo(canvasColor)
        assertThat(dragged.getPixel(width - 4, 4)).isEqualTo(canvasColor)
    }

    private fun snapshotScene(theme: SancaiTheme, name: String) {
        val themed = Snapshots.context(theme)
        val below = Snapshots.render(Page(themed, "Overview"), 360, PAGE_HEIGHT_DP)
        val host = settledHost(snapshot = below, themed = themed)
        assertThat(host.height).isEqualTo(below.height)
        val y = titleY(host)
        // Caught mid-swipe, 36 % of the way back to the page below.
        Touch.drag(host, 200f, y, 200f + host.width * 0.36f, y, release = false)
        val bitmap = draw(host)
        Snapshots.save(bitmap, name)
        assertThat(bitmap.getPixel(host.width - 4, 4)).isEqualTo(theme.colors.canvas)
        assertThat(bitmap.getPixel(4, 4)).isEqualTo(theme.colors.canvas)
    }

    @Test
    fun `snapshot of spatial transition`() {
        snapshotScene(SancaiTheme.light(), "transition_light")
        snapshotScene(SancaiTheme.dark(), "transition_dark")
    }

    /** Refuses the swipe while [busy]. */
    class GatedActivity : Activity(), SancaiSpatialTransition.SwipeGate {
        var busy = true

        override fun canSwipeBack(): Boolean = !busy
    }

    /** A small settings-like page: centred title, a card with a track, a text field. */
    private class Page(context: Context, title: String) : LinearLayout(context) {
        val track = SancaiSegmentedTrack(context)
        val field = EditText(context)

        init {
            val theme = SancaiTheme.of(context)
            val density = resources.displayMetrics.density
            val pad = (16 * density).toInt()
            orientation = VERTICAL
            setBackgroundColor(theme.colors.canvas)
            setPadding(pad, pad, pad, pad)
            minimumHeight = (PAGE_HEIGHT_DP * density).toInt()
            val heading = SancaiText.create(context, title, SancaiTextRole.TITLE, SancaiTextTone.INK)
            heading.gravity = Gravity.CENTER
            addView(heading, LayoutParams(LayoutParams.MATCH_PARENT, (56 * density).toInt()))
            val card = SancaiSurface(context)
            val label = SancaiText.create(context, "Protection", SancaiTextRole.SUBHEAD, SancaiTextTone.INK)
            label.gravity = Gravity.CENTER
            card.addView(label, LayoutParams(LayoutParams.MATCH_PARENT, (40 * density).toInt()))
            track.options = listOf("Off", "Gentle", "Standard")
            track.select(2, animate = false)
            card.addView(track, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            val note = SancaiText.create(
                context, "Swipe anywhere to go back", SancaiTextRole.CAPTION, SancaiTextTone.MUTED,
            )
            note.gravity = Gravity.CENTER
            addView(note, LayoutParams(LayoutParams.MATCH_PARENT, (44 * density).toInt()))
            field.setText("editable")
            field.setTextColor(theme.colors.ink)
            addView(field, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun host(
        snapshot: Bitmap? = solid(),
        side: Side = Side.END,
        themed: Context = context,
        page: View = Page(themed, "Developer options"),
    ): SancaiSpatialSwipeHost {
        val host = SancaiSpatialSwipeHost(themed, page, snapshot, side)
        host.onFinish = Runnable { finished++ }
        return host
    }

    /** A host in a window whose page has finished coming in. */
    private fun settledHost(
        snapshot: Bitmap? = solid(),
        side: Side = Side.END,
        themed: Context = context,
    ): SancaiSpatialSwipeHost {
        val host = host(snapshot, side, themed)
        Snapshots.attach(host, 360)
        advance()
        assertThat(host.isMoving).isFalse()
        assertThat(host.pageOffset).isEqualTo(0f)
        return host
    }

    /** Y of the page title: plain, not clickable, nothing that drags. */
    private fun titleY(host: View): Float = 40 * host.resources.displayMetrics.density

    private fun rtlContext(): Context {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val config = Configuration(app.resources.configuration)
        config.setLayoutDirection(Locale("ar"))
        return SancaiTheme.wrap(app.createConfigurationContext(config), SancaiTheme.light())
    }

    private companion object {
        /** Colour of the stand-in picture of the activity below. */
        val BACKDROP: Int = Color.rgb(200, 60, 40)
        const val PAGE_HEIGHT_DP = 320
        const val DOWN_TIME = 10_000L
    }
}
