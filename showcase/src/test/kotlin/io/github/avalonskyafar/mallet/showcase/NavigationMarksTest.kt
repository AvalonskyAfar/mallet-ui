package io.github.avalonskyafar.mallet.showcase

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconView
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.widget.MalletText
import io.github.avalonskyafar.mallet.widget.MalletEntryRow
import io.github.avalonskyafar.mallet.widget.MalletNavBar
import io.github.avalonskyafar.mallet.widget.MalletPowerSwitch
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * How the showcase lets its reader move, which is the navigation of the original app: Home and
 * the pages around it carry no marks that point somewhere. Home counts its neighbours in a row
 * of entries at the bottom; a page around Home is left by dragging back; only pages deeper in
 * the map name the page they came from.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationMarksTest {

    private class Host(override val activity: Activity, override val settings: ShowcaseSettings) : ShowcaseHost {
        val opened = ArrayList<String>()
        var backs = 0

        override fun open(key: String) {
            opened += key
        }

        override fun back() {
            backs++
        }

        override fun say(message: CharSequence) = Unit

        override fun applyAppearance(source: View) = Unit
    }

    private lateinit var host: Host

    @After
    fun resetTheme() {
        MalletTheme.resolver = null
    }

    /** The page with [key], built as the app builds it and laid out on a phone screen. */
    private fun page(key: String, heightDp: Int = 914): View {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val settings = ShowcaseSettings(activity)
        MalletTheme.resolver = MalletTheme.Resolver { context -> settings.toTheme(context) }
        host = Host(activity, settings)
        val view = Pages.create(key, host)
        activity.setContentView(view)
        ShadowLooper.idleMainLooper()
        val density = activity.resources.displayMetrics.density
        view.measure(
            View.MeasureSpec.makeMeasureSpec((411 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    private fun walk(view: View, visit: (View) -> Unit) {
        visit(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), visit)
    }

    private inline fun <reified T : View> View.all(): List<T> {
        val found = ArrayList<T>()
        walk(this) { if (it is T) found += it }
        return found
    }

    /** The rows that lead back to a parent. */
    private fun waysBack(page: View): List<View> {
        val found = ArrayList<View>()
        walk(page) { if (it.id == R.id.showcase_way_back) found += it }
        return found
    }

    private fun tap(view: View, x: Float, y: Float) {
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0L, 40L, MotionEvent.ACTION_UP, x, y, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun `Home counts the pages around it in one row of entries, in the order of the map`() {
        val home = page(Pages.HOME)
        val row = home.all<MalletNavBar>().single()
        assertThat(row.mode).isEqualTo(MalletNavBar.Mode.LAUNCHER)
        // One entry for each page that the map puts beside Home, and no other.
        val around = Pages.graph.let { graph -> FIRST_LEVEL.filter { graph.parentOf(it) == Pages.HOME } }
        assertThat(around).hasSize(FIRST_LEVEL.size)
        assertThat(row.items.map { it.label.toString() })
            .containsExactlyElementsIn(FIRST_LEVEL.map { home.context.getString(Pages.titleOf(it)) })
            .inOrder()
        // Each entry carries the icon that stands for its page.
        assertThat(row.items.map { it.icon }).containsExactly(
            MalletIcon.PALETTE, MalletIcon.GRID, MalletIcon.SLIDERS, MalletIcon.HAND,
        ).inOrder()
        // The row stands at the bottom and is as wide as the page.
        assertThat(row.bottom).isEqualTo(home.height - home.paddingBottom)
        assertThat(row.width).isEqualTo(home.width - home.paddingLeft - home.paddingRight)
    }

    @Test
    fun `the composition stays clear of the row on a tall window and is cut by it on a short one`() {
        // A phone in portrait: the block stands above the row with room to spare.
        val tall = page(Pages.HOME)
        val tallRow = tall.all<MalletNavBar>().single()
        assertThat(compositionBottom(tall)).isLessThan(tallRow.top)
        // A short window, wider than it is tall: the row keeps its whole height and the
        // composition is cut at the row, not painted over it.
        val short = page(Pages.HOME, heightDp = 240)
        val shortRow = short.all<MalletNavBar>().single()
        val density = short.resources.displayMetrics.density
        assertThat(shortRow.height).isEqualTo((72 * density).toInt())
        assertThat(shortRow.bottom - short.paddingBottom).isEqualTo(short.height)
        val stage = short.findViewById<ViewGroup>(R.id.showcase_home_stage)
        assertThat(stage).isNotNull()
        // The row is the stage's second child, so it is drawn over the lower part of the
        // composition rather than under it.
        assertThat(stage.childCount).isEqualTo(2)
        assertThat(stage.getChildAt(1)).isSameInstanceAs(shortRow)
        assertThat(shortRow.top).isEqualTo(short.height - short.paddingBottom - shortRow.height)
        // The composition is pinned to the top of the room instead of being centred out of
        // the window: its top edge stays below the page's padding, and it reaches down into
        // the row's band, where the row covers it.
        assertThat(compositionTop(short)).isAtLeast(short.paddingTop)
        assertThat(compositionBottom(short)).isGreaterThan(shortRow.top)
    }

    /** The bottom edge of the composition: the wordmark, the switch and the status line. */
    private fun compositionBottom(home: View): Int {
        var bottom = 0
        walk(home) { if (it is MalletText || it is MalletPowerSwitch) bottom = maxOf(bottom, it.bottom) }
        return bottom
    }

    /** The top edge of the same composition. */
    private fun compositionTop(home: View): Int {
        var top = home.height
        walk(home) { if (it is MalletText || it is MalletPowerSwitch) top = minOf(top, it.top) }
        return top
    }

    @Test
    fun `tapping an entry travels to the page it names`() {
        val home = page(Pages.HOME)
        val row = home.all<MalletNavBar>().single()
        val density = home.resources.displayMetrics.density
        val cell = (row.width - 36f * density) / FIRST_LEVEL.size
        FIRST_LEVEL.forEachIndexed { index, _ ->
            tap(row, 18f * density + cell * (index + 0.5f), row.height * 0.4f)
        }
        assertThat(host.opened).containsExactlyElementsIn(FIRST_LEVEL).inOrder()
    }

    @Test
    @Config(qualifiers = "+ar-ldrtl")
    fun `right to left the first entry stands at the right, where its page lies`() {
        val home = page(Pages.HOME)
        val row = home.all<MalletNavBar>().single()
        val density = home.resources.displayMetrics.density
        val cell = (row.width - 36f * density) / FIRST_LEVEL.size
        tap(row, row.width - 18f * density - cell * 0.5f, row.height * 0.4f)
        assertThat(host.opened).containsExactly(FIRST_LEVEL.first())
    }

    @Test
    fun `nothing on Home points at a neighbour`() {
        val home = page(Pages.HOME)
        // No arrow and no chevron anywhere, and nothing but the row leads away.
        assertThat(home.all<MalletIconView>()).isEmpty()
        assertThat(home.all<MalletEntryRow>()).isEmpty()
        assertThat(waysBack(home)).isEmpty()
        val leaving = ArrayList<View>()
        walk(home) { if (it.isClickable && it !is MalletNavBar && it !is MalletPowerSwitch) leaving += it }
        assertThat(leaving).isEmpty()
    }

    @Test
    fun `the pages around Home carry no way back`() {
        for (key in FIRST_LEVEL) {
            assertWithMessage(key).that(waysBack(page(key))).isEmpty()
        }
    }

    @Test
    fun `a page deeper in the map names its parent once and the row leads back`() {
        val deeper = Pages.foundations + Pages.components + Pages.interactions
        assertThat(deeper).isNotEmpty()
        for (demo in deeper) {
            val view = page(demo.key)
            val rows = waysBack(view)
            assertWithMessage(demo.key).that(rows).hasSize(1)
            val parent = Pages.graph.parentOf(demo.key)!!
            assertWithMessage(demo.key).that(rows[0].contentDescription.toString())
                .isEqualTo(view.context.getString(R.string.showcase_back_to, view.context.getString(Pages.titleOf(parent))))
            assertWithMessage(demo.key).that(rows[0].performClick()).isTrue()
            ShadowLooper.idleMainLooper()
            assertWithMessage(demo.key).that(host.backs).isEqualTo(1)
        }
    }

    private companion object {
        /** The pages around Home, in the order of Home's row: start side, below, above, end side. */
        val FIRST_LEVEL = listOf("foundations", "components", "appearance", "interactions")
    }
}
