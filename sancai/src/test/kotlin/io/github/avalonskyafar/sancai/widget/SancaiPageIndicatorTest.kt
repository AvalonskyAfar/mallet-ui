package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.interaction.SancaiPager
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** The dots on their own and let go by a pager; coupled behaviour is tested with the pager. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiPageIndicatorTest {

    private val context: Context = Snapshots.context(SancaiTheme.light())
    private val colors = SancaiTheme.light().colors
    private val density = context.resources.displayMetrics.density
    private val widthDp = 240
    private val width = widthDp * density

    /** Centre of dot [slot], counted from the left, of [count] dots. */
    private fun dotX(count: Int, slot: Int): Float = (width - count * 28 * density) / 2f + (slot + 0.5f) * 28 * density

    private fun dotPixel(indicator: SancaiPageIndicator, slot: Int): Int =
        Snapshots.render(indicator, widthDp).getPixel(dotX(indicator.count, slot).toInt(), indicator.height / 2)

    /** A standalone indicator in a window, where a chosen dot glides. */
    private fun standalone(count: Int): SancaiPageIndicator = SancaiPageIndicator(context).also {
        it.count = count
        Snapshots.attach(it, widthDp)
    }

    private fun finishMotion() {
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `changing the count during a glide ends the glide on the kept selection`() {
        val indicator = standalone(5)
        Touch.tap(indicator, dotX(5, 3))
        assertThat(indicator.selectedIndex).isEqualTo(3)
        indicator.count = 2
        finishMotion()
        assertThat(indicator.selectedIndex).isEqualTo(1)
        assertThat(dotPixel(indicator, 1)).isEqualTo(colors.accent)
        assertThat(dotPixel(indicator, 0)).isEqualTo(colors.muted)
    }

    @Test
    fun `an indicator detached during its glide rests on the chosen dot`() {
        val indicator = standalone(5)
        Touch.tap(indicator, dotX(5, 3))
        (indicator.parent as ViewGroup).removeView(indicator)
        finishMotion()
        assertThat(indicator.selectedIndex).isEqualTo(3)
        assertThat(dotPixel(indicator, 3)).isEqualTo(colors.accent)
        assertThat(dotPixel(indicator, 0)).isEqualTo(colors.muted)
    }

    @Test
    fun `letting a moving pager go leaves the dots on its page`() {
        val pager = SancaiPager(context)
        pager.setPages(3) { _, _ -> View(context) }
        val indicator = SancaiPageIndicator(context)
        indicator.attachTo(pager)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(pager, LinearLayout.LayoutParams.MATCH_PARENT, (120 * density).toInt())
            addView(indicator, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        Snapshots.attach(column, widthDp)
        pager.setCurrentPage(2, animate = true)
        assertThat(pager.isMoving).isTrue()
        indicator.attachTo(null)
        finishMotion()
        assertThat(indicator.count).isEqualTo(3)
        assertThat(indicator.selectedIndex).isEqualTo(2)
        assertThat(dotPixel(indicator, 2)).isEqualTo(colors.accent)
        assertThat(dotPixel(indicator, 0)).isEqualTo(colors.muted)
    }

    @Test
    fun `disabling under the finger lets the pressed dot come back up`() {
        val held = SancaiPageIndicator(context).apply { count = 4 }
        Snapshots.layout(held, widthDp)
        Touch.dispatch(held, MotionEvent.ACTION_DOWN, dotX(4, 2), held.height / 2f)
        held.isEnabled = false
        val never = SancaiPageIndicator(context).apply {
            count = 4
            isEnabled = false
        }
        assertThat(Snapshots.render(held, widthDp).sameAs(Snapshots.render(never, widthDp))).isTrue()
    }
}
