package io.github.avalonskyafar.mallet.interaction

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.testing.Touch
import io.github.avalonskyafar.mallet.theme.MalletMotion
import io.github.avalonskyafar.mallet.theme.MalletTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletPressTest {

    private val context = Snapshots.context(MalletTheme.light().copy(haptics = false))
    private val sink = MalletMotion.PRESS_SINK_DP * context.resources.displayMetrics.density

    private class Scene(val host: FrameLayout, val content: View)

    private fun scene(): Scene {
        val host = FrameLayout(context)
        val content = View(context)
        host.addView(content, FrameLayout.LayoutParams(-1, 120))
        Snapshots.attach(host, 200)
        return Scene(host, content)
    }

    @Test
    fun `touching sinks the content and releasing puts it back before the click`() {
        val scene = scene()
        var atClick = -1f
        MalletPress.attach(scene.host, scene.content) { atClick = scene.content.translationY }
        Touch.dispatch(scene.host, MotionEvent.ACTION_DOWN, 20f, 20f)
        Snapshots.idle()
        assertThat(scene.content.translationY).isWithin(0.01f).of(sink)
        // The host itself does not move.
        assertThat(scene.host.translationY).isEqualTo(0f)
        Touch.dispatch(scene.host, MotionEvent.ACTION_UP, 20f, 20f)
        Snapshots.idle()
        assertThat(atClick).isEqualTo(0f)
        assertThat(scene.content.translationY).isEqualTo(0f)
    }

    @Test
    fun `a cancelled touch puts the content back without a click`() {
        val scene = scene()
        var clicks = 0
        MalletPress.attach(scene.host, scene.content) { clicks++ }
        Touch.dispatch(scene.host, MotionEvent.ACTION_DOWN, 20f, 20f)
        Snapshots.idle()
        Touch.dispatch(scene.host, MotionEvent.ACTION_CANCEL, 20f, 20f)
        Snapshots.idle()
        assertThat(scene.content.translationY).isEqualTo(0f)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `a host disabled under the finger lets the content come back`() {
        val scene = scene()
        MalletPress.attachSink(scene.host, scene.content)
        Touch.dispatch(scene.host, MotionEvent.ACTION_DOWN, 20f, 20f)
        Snapshots.idle()
        assertThat(scene.content.translationY).isWithin(0.01f).of(sink)

        // A disabled view is not told that the finger lifted.
        scene.host.isEnabled = false
        scene.host.viewTreeObserver.dispatchOnPreDraw()
        assertThat(scene.content.translationY).isEqualTo(0f)
        Touch.dispatch(scene.host, MotionEvent.ACTION_UP, 20f, 20f)
        assertThat(scene.content.translationY).isEqualTo(0f)

        // Enabled again, it presses as before.
        scene.host.isEnabled = true
        Touch.dispatch(scene.host, MotionEvent.ACTION_DOWN, 20f, 20f)
        Snapshots.idle()
        assertThat(scene.content.translationY).isWithin(0.01f).of(sink)
        Touch.dispatch(scene.host, MotionEvent.ACTION_UP, 20f, 20f)
    }

    @Test
    fun `a host taken out of its window lets the content come back`() {
        val scene = scene()
        MalletPress.attachSink(scene.host, scene.content)
        Touch.dispatch(scene.host, MotionEvent.ACTION_DOWN, 20f, 20f)
        Snapshots.idle()
        val observer = scene.host.viewTreeObserver
        (scene.host.parent as ViewGroup).removeView(scene.host)
        observer.dispatchOnPreDraw()
        assertThat(scene.content.translationY).isEqualTo(0f)
    }

    @Test
    fun `a disabled host does not sink at all`() {
        val scene = scene()
        MalletPress.attachSink(scene.host, scene.content)
        scene.host.isEnabled = false
        Touch.dispatch(scene.host, MotionEvent.ACTION_DOWN, 20f, 20f)
        Snapshots.idle()
        assertThat(scene.content.translationY).isEqualTo(0f)
    }
}
