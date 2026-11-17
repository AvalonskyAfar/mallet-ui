package io.github.avalonskyafar.sancai.widget

import android.graphics.Color
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiTopBarTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val density = context.resources.displayMetrics.density

    init {
        // Without the manifest flag a view never resolves to right-to-left.
        val info = context.applicationInfo
        info.flags = info.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
    }

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private fun bar(theme: SancaiTheme? = null): SancaiTopBar =
        SancaiTopBar(if (theme == null) context else Snapshots.context(theme)).apply { title = "Blocked numbers" }

    private fun titleOf(bar: SancaiTopBar): View =
        (0 until bar.childCount).map(bar::getChildAt).first { it is SancaiText && it.visibility == View.VISIBLE }

    private fun texts(bar: SancaiTopBar): List<SancaiText> =
        (0 until bar.childCount).map(bar::getChildAt).filterIsInstance<SancaiText>()

    private fun centreX(view: View): Float = (view.left + view.right) / 2f

    @Test
    fun `bar is 56 dp high and has the canvas colour`() {
        val bar = bar()
        val bitmap = Snapshots.render(bar, 360)
        assertThat(bar.height).isEqualTo(px(56f))
        assertThat(bitmap.getPixel(px(8f), px(8f))).isEqualTo(SancaiTheme.light().colors.canvas)
        assertThat(bitmap.getPixel(px(180f), bar.height - 1)).isEqualTo(SancaiTheme.light().colors.canvas)
    }

    @Test
    fun `title centre equals the bar centre whatever buttons are present`() {
        val setups = listOf<(SancaiTopBar) -> Unit>(
            { },
            { it.setNavigation { } },
            { it.addAction(SancaiIcon.SEARCH, "Search", null) },
            { it.setNavigation { }; it.addAction(SancaiIcon.SEARCH, "Search", null) },
            {
                it.addAction(SancaiIcon.SEARCH, "Search", null)
                it.addAction(SancaiIcon.PLUS, "Add", null)
            },
            { it.setNavigation { }; it.subtitle = "12 entries" },
        )
        for (rtl in listOf(false, true)) {
            for (setup in setups) {
                val bar = bar()
                if (rtl) bar.layoutDirection = View.LAYOUT_DIRECTION_RTL
                setup(bar)
                Snapshots.layout(bar, 360)
                for (text in texts(bar).filter { it.visibility == View.VISIBLE }) {
                    assertThat(abs(centreX(text) - bar.width / 2f)).isAtMost(0.5f)
                }
            }
        }
    }

    @Test
    fun `a long title is shortened symmetrically and never reaches the buttons`() {
        val bar = bar()
        bar.title = "A very long title that cannot possibly fit into a narrow top bar at all"
        bar.setNavigation { }
        bar.addAction(SancaiIcon.SEARCH, "Search", null)
        bar.addAction(SancaiIcon.PLUS, "Add", null)
        Snapshots.layout(bar, 320)
        val title = titleOf(bar)
        val room = px(4f) + 2 * px(48f)
        assertThat(title.left).isAtLeast(room)
        assertThat(title.right).isAtMost(bar.width - room)
        assertThat(title.right).isAtMost(bar.getActionAt(0).left)
        assertThat(abs(centreX(title) - bar.width / 2f)).isAtMost(0.5f)
    }

    @Test
    fun `title and subtitle are centred together on the bar's middle line`() {
        val bar = bar()
        bar.subtitle = "12 entries"
        Snapshots.layout(bar, 360)
        val (title, subtitle) = texts(bar)
        assertThat(bar.height).isEqualTo(px(56f))
        assertThat(subtitle.top).isEqualTo(title.bottom + px(1f))
        assertThat(abs((title.top + subtitle.bottom) / 2f - bar.height / 2f)).isAtMost(1f)
        assertThat(subtitle.currentTextColor).isEqualTo(SancaiTheme.light().colors.muted)
        assertThat(title.currentTextColor).isEqualTo(SancaiTheme.light().colors.ink)
        assertThat(bar.subtitle.toString()).isEqualTo("12 entries")
        bar.subtitle = null
        assertThat(subtitle.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `buttons are 48 dp targets at start and end and mirror in RTL`() {
        for (rtl in listOf(false, true)) {
            val bar = bar()
            if (rtl) bar.layoutDirection = View.LAYOUT_DIRECTION_RTL
            bar.setNavigation { }
            val first = bar.addAction(SancaiIcon.SEARCH, "Search", null)
            val second = bar.addAction(SancaiIcon.PLUS, "Add", null)
            Snapshots.layout(bar, 360)
            val nav = bar.navigationButton!!
            for (button in listOf(nav, first, second)) {
                assertThat(button.width).isEqualTo(px(48f))
                assertThat(button.height).isEqualTo(px(48f))
                assertThat(button.top).isEqualTo((bar.height - px(48f)) / 2)
            }
            val edge = px(4f)
            if (rtl) {
                assertThat(nav.right).isEqualTo(bar.width - edge)
                assertThat(second.left).isEqualTo(edge)
                assertThat(first.left).isEqualTo(second.right)
            } else {
                assertThat(nav.left).isEqualTo(edge)
                assertThat(second.right).isEqualTo(bar.width - edge)
                assertThat(first.right).isEqualTo(second.left)
            }
        }
    }

    /** Columns (relative to [view]) of the pixels inside [view] that have exactly [color]. */
    private fun columnsOf(bar: SancaiTopBar, view: View, color: Int): List<Int> {
        val bitmap = Snapshots.render(bar, 360)
        val found = ArrayList<Int>()
        for (y in view.top until view.bottom) {
            for (x in view.left until view.right) {
                if (bitmap.getPixel(x, y) == color) found.add(x - view.left)
            }
        }
        return found
    }

    @Test
    fun `navigation icon is ink, actions are muted, both 24 dp and centred in the target`() {
        val colors = SancaiTheme.light().colors
        val bar = bar()
        bar.setNavigation { }
        val action = bar.addAction(SancaiIcon.PLUS, "Add", null)
        Snapshots.layout(bar, 360)
        val ink = columnsOf(bar, bar.navigationButton!!, colors.ink)
        val muted = columnsOf(bar, action, colors.muted)
        assertThat(ink).isNotEmpty()
        assertThat(muted).isNotEmpty()
        assertThat(columnsOf(bar, action, colors.ink)).isEmpty()
        // The plus spans the glyph symmetrically: inside the 24 dp box, centred in 48 dp.
        assertThat(muted.min()).isAtLeast(px(12f))
        assertThat(muted.max()).isAtMost(px(36f))
        assertThat(abs((muted.min() + muted.max()) / 2f - px(24f))).isAtMost(1.5f)
    }

    @Test
    fun `back chevron is mirrored in RTL`() {
        val ink = SancaiTheme.light().colors.ink
        /** Height spanned by the ink in the three leftmost inked columns: small at a tip. */
        fun leftSpread(rtl: Boolean): Int {
            val bar = SancaiTopBar(context)
            if (rtl) bar.layoutDirection = View.LAYOUT_DIRECTION_RTL
            bar.setNavigation { }
            Snapshots.layout(bar, 360)
            val nav = bar.navigationButton!!
            val bitmap = Snapshots.render(bar, 360)
            val points = ArrayList<Pair<Int, Int>>()
            for (y in nav.top until nav.bottom) {
                for (x in nav.left until nav.right) if (bitmap.getPixel(x, y) == ink) points.add(x to y)
            }
            val left = points.minOf { it.first }
            val rows = points.filter { it.first <= left + 2 }.map { it.second }
            return rows.max() - rows.min()
        }
        // "<" has its tip on the left, ">" its two open ends.
        assertThat(leftSpread(false)).isLessThan(px(6f))
        assertThat(leftSpread(true)).isGreaterThan(px(8f))
    }

    @Test
    fun `tapping a button calls its listener and nothing else`() {
        var nav = 0
        var search = 0
        val bar = bar()
        bar.setNavigation { nav++ }
        val action = bar.addAction(SancaiIcon.SEARCH, "Search") { search++ }
        Snapshots.attach(bar)
        Touch.tap(bar, centreX(bar.navigationButton!!), bar.height / 2f)
        Snapshots.idle()
        assertThat(nav).isEqualTo(1)
        assertThat(search).isEqualTo(0)
        Touch.tap(bar, centreX(action), bar.height / 2f)
        Snapshots.idle()
        assertThat(search).isEqualTo(1)
        Touch.tap(bar, bar.width / 2f, bar.height / 2f)
        Snapshots.idle()
        assertThat(nav + search).isEqualTo(2)
    }

    @Test
    fun `pressing sinks the icon only and release puts it back`() {
        val bar = bar()
        bar.setNavigation { }
        Snapshots.attach(bar)
        val nav = bar.navigationButton!!
        Touch.dispatch(bar, android.view.MotionEvent.ACTION_DOWN, centreX(nav), bar.height / 2f)
        org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertThat(nav.translationY).isWithin(0.01f).of(1.5f * density)
        assertThat(titleOf(bar).translationY).isEqualTo(0f)
        Touch.dispatch(bar, android.view.MotionEvent.ACTION_UP, centreX(nav), bar.height / 2f)
        assertThat(nav.translationY).isEqualTo(0f)
    }

    @Test
    fun `a disabled bar pales its buttons and ignores taps`() {
        val colors = SancaiTheme.light().colors
        var clicks = 0
        val bar = bar()
        bar.setNavigation { clicks++ }
        val action = bar.addAction(SancaiIcon.SEARCH, "Search") { clicks++ }
        bar.isEnabled = false
        Snapshots.attach(bar)
        val nav = bar.navigationButton!!
        assertThat(nav.isEnabled).isFalse()
        assertThat(action.isEnabled).isFalse()
        Touch.tap(bar, centreX(nav), bar.height / 2f)
        Touch.tap(bar, centreX(action), bar.height / 2f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(0)
        assertThat(nav.translationY).isEqualTo(0f)
        assertThat(columnsOf(bar, nav, colors.ink)).isEmpty()
        assertThat(titleOf(bar).alpha).isEqualTo(1f)
        bar.isEnabled = true
        assertThat(columnsOf(bar, nav, colors.ink)).isNotEmpty()
    }

    @Test
    fun `one action can be disabled on its own`() {
        var clicks = 0
        val bar = bar()
        val first = bar.addAction(SancaiIcon.SEARCH, "Search") { clicks++ }
        val second = bar.addAction(SancaiIcon.PLUS, "Add") { clicks += 10 }
        first.isEnabled = false
        Snapshots.attach(bar)
        Touch.tap(bar, centreX(first), bar.height / 2f)
        Touch.tap(bar, centreX(second), bar.height / 2f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(10)
    }

    @Test
    fun `enabling the bar again keeps a button the app disabled on its own disabled`() {
        var clicks = 0
        val bar = bar()
        bar.setNavigation { clicks += 100 }
        val save = bar.addAction(SancaiIcon.SEARCH, "Search") { clicks++ }
        val other = bar.addAction(SancaiIcon.PLUS, "Add") { clicks += 10 }
        save.isEnabled = false
        bar.isEnabled = false
        assertThat(other.isEnabled).isFalse()
        bar.isEnabled = true
        assertThat(save.isEnabled).isFalse()
        assertThat(other.isEnabled).isTrue()
        // Also when the bar was enabled all along.
        bar.isEnabled = true
        assertThat(save.isEnabled).isFalse()
        Snapshots.attach(bar)
        Touch.tap(bar, centreX(save), bar.height / 2f)
        Touch.tap(bar, centreX(other), bar.height / 2f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(10)

        // A button enabled while the bar is disabled waits for the bar.
        bar.isEnabled = false
        save.isEnabled = true
        assertThat(save.isEnabled).isFalse()
        bar.isEnabled = true
        assertThat(save.isEnabled).isTrue()

        // Changing the navigation icon does not enable a navigation button disabled on its own.
        val nav = bar.navigationButton!!
        nav.isEnabled = false
        bar.setNavigation(SancaiIcon.CHEVRON_LEFT, null) { clicks += 100 }
        assertThat(nav.isEnabled).isFalse()
    }

    @Test
    fun `buttons draw their own focus rim without the platform highlight`() {
        val bar = bar()
        bar.setNavigation { }
        val action = bar.addAction(SancaiIcon.SEARCH, "Search") { }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            assertThat(bar.navigationButton!!.defaultFocusHighlightEnabled).isFalse()
            assertThat(action.defaultFocusHighlightEnabled).isFalse()
        }
    }

    @Test
    fun `enter and space operate a focused button, other keys are not consumed`() {
        var clicks = 0
        val bar = bar()
        bar.setNavigation { clicks++ }
        Snapshots.attach(bar)
        val nav = bar.navigationButton!!
        assertThat(nav.isFocusable).isTrue()
        for (key in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE)) {
            nav.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            nav.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            Snapshots.idle()
        }
        assertThat(clicks).isEqualTo(2)
        assertThat(nav.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))).isFalse()
        assertThat(nav.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))).isFalse()
    }

    @Test
    fun `hairline appears only when lifted and runs from edge to edge`() {
        val colors = SancaiTheme.light().colors
        val bar = bar()
        var bitmap = Snapshots.render(bar, 360)
        val y = bar.height - 1
        assertThat(bitmap.getPixel(0, y)).isEqualTo(colors.canvas)
        bar.lifted = true
        bitmap = Snapshots.render(bar, 360)
        for (x in listOf(0, bar.width / 2, bar.width - 1)) {
            assertThat(bitmap.getPixel(x, y)).isEqualTo(colors.divider)
        }
        val thickness = maxOf(1, density.roundToInt())
        assertThat(bitmap.getPixel(bar.width / 2, y - thickness)).isEqualTo(colors.canvas)
    }

    @Test
    fun `hairline follows a bound scroll view`() {
        val bar = bar()
        val scroll = ScrollView(context)
        scroll.addView(View(context).apply { minimumHeight = px(2000f) })
        Snapshots.layout(scroll, 360, 400)
        bar.bindTo(scroll)
        assertThat(bar.lifted).isFalse()
        scroll.scrollTo(0, px(30f))
        assertThat(bar.lifted).isTrue()
        scroll.scrollTo(0, 0)
        assertThat(bar.lifted).isFalse()
        scroll.scrollTo(0, px(30f))
        bar.bindTo(null)
        scroll.scrollTo(0, 0)
        assertThat(bar.lifted).isTrue()
    }

    @Test
    fun `a detached bar lets go of the bound view and follows it again when attached`() {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val bar = bar()
        val scroll = ScrollView(context)
        scroll.addView(View(context).apply { minimumHeight = px(2000f) })
        column.addView(bar)
        column.addView(scroll, LinearLayout.LayoutParams(-1, px(300f)))
        Snapshots.attach(column)
        bar.bindTo(scroll)
        scroll.scrollTo(0, px(30f))
        assertThat(bar.lifted).isTrue()
        column.removeView(bar)
        scroll.scrollTo(0, 0)
        assertThat(bar.lifted).isTrue()
        column.addView(bar, 0)
        assertThat(bar.lifted).isFalse()
        scroll.scrollTo(0, px(30f))
        assertThat(bar.lifted).isTrue()
    }

    private fun insets(top: Int, left: Int = 0, right: Int = 0): WindowInsetsCompat =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(left, top, right, 0))
            .build()

    @Test
    fun `a bar that joins a screen late asks for the window insets`() {
        var asked = 0
        val parent = object : android.widget.FrameLayout(context) {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun requestFitSystemWindows() {
                asked++
                super.requestFitSystemWindows()
            }
        }
        Snapshots.attach(parent, 360)
        asked = 0
        parent.addView(SancaiTopBar(context))
        assertThat(asked).isEqualTo(1)
    }

    @Test
    fun `status bar inset is added above the bar and passed on`() {
        val bar = bar()
        bar.setNavigation { }
        val top = px(24f)
        val result = ViewCompat.dispatchApplyWindowInsets(bar, insets(top))
        Snapshots.layout(bar, 360)
        assertThat(bar.height).isEqualTo(px(56f) + top)
        val nav = bar.navigationButton!!
        assertThat((nav.top + nav.bottom) / 2).isEqualTo(top + px(56f) / 2)
        val title = titleOf(bar)
        assertThat(abs((title.top + title.bottom) / 2f - (top + px(56f) / 2f))).isAtMost(1f)
        assertThat(result.getInsets(WindowInsetsCompat.Type.systemBars()).top).isEqualTo(top)

        bar.fitStatusBar = false
        Snapshots.layout(bar, 360)
        assertThat(bar.height).isEqualTo(px(56f))
        assertThat(nav.top).isEqualTo((px(56f) - px(48f)) / 2)
    }

    @Test
    fun `side insets move the buttons and keep the title centred between them`() {
        val bar = bar()
        bar.setNavigation { }
        val action = bar.addAction(SancaiIcon.SEARCH, "Search", null)
        val left = px(40f)
        ViewCompat.dispatchApplyWindowInsets(bar, insets(0, left = left))
        Snapshots.layout(bar, 360)
        assertThat(bar.navigationButton!!.left).isEqualTo(left + px(4f))
        assertThat(action.right).isEqualTo(bar.width - px(4f))
        assertThat(abs(centreX(titleOf(bar)) - (left + bar.width) / 2f)).isAtMost(0.5f)
    }

    @Test
    fun `lifted survives save and restore`() {
        val bar = bar().apply { id = 7; lifted = true }
        val states = SparseArray<Parcelable>()
        bar.saveHierarchyState(states)
        val restored = bar().apply { id = 7 }
        restored.restoreHierarchyState(states)
        assertThat(restored.lifted).isTrue()
    }

    @Test
    fun `buttons are described buttons and the title is a heading`() {
        val bar = bar()
        bar.setNavigation { }
        val action = bar.addAction(SancaiIcon.SEARCH, "Search", null)
        val nav = bar.navigationButton!!
        assertThat(nav.contentDescription.toString()).isEqualTo("Back")
        assertThat(action.contentDescription.toString()).isEqualTo("Search")
        assertThat(nav.accessibilityClassName.toString()).isEqualTo("android.widget.Button")
        assertThat(ViewCompat.isAccessibilityHeading(titleOf(bar))).isTrue()
        bar.setNavigation(SancaiIcon.CLOSE, "Close", null)
        assertThat(bar.navigationButton).isSameInstanceAs(nav)
        assertThat(nav.contentDescription.toString()).isEqualTo("Close")
    }

    @Test
    fun `a bar holds at most two actions and can drop them again`() {
        val bar = bar()
        bar.addAction(SancaiIcon.SEARCH, "Search", null)
        bar.addAction(SancaiIcon.PLUS, "Add", null)
        assertThrows(IllegalStateException::class.java) { bar.addAction(SancaiIcon.EDIT, "Edit", null) }
        assertThat(bar.actionCount).isEqualTo(2)
        bar.clearActions()
        bar.setNavigation { }
        bar.clearNavigation()
        assertThat(bar.actionCount).isEqualTo(0)
        assertThat(bar.navigationButton).isNull()
        assertThat(bar.childCount).isEqualTo(2)
    }

    @Test
    fun `large text keeps the title inside the bar`() {
        val large = SancaiTheme.light().copy(textScale = SancaiTheme.LARGE_TEXT_SCALE)
        val bar = bar(large)
        bar.subtitle = "12 entries"
        bar.setNavigation { }
        Snapshots.layout(bar, 320)
        val (title, subtitle) = texts(bar)
        assertThat(bar.height).isAtLeast(px(56f))
        assertThat(title.top).isAtLeast(px(7f))
        assertThat(subtitle.bottom).isAtMost(bar.height - px(7f))
    }

    @Test
    fun `an empty bar keeps its height and a bar without title centres the subtitle`() {
        val bar = SancaiTopBar(context)
        Snapshots.layout(bar, 360)
        assertThat(bar.height).isEqualTo(px(56f))
        assertThat(bar.title).isNull()
        assertThat(texts(bar).all { it.visibility == View.GONE }).isTrue()
        bar.title = ""
        bar.subtitle = "12 entries"
        Snapshots.layout(bar, 360)
        val subtitle = titleOf(bar)
        assertThat(bar.height).isEqualTo(px(56f))
        assertThat(abs((subtitle.top + subtitle.bottom) / 2f - bar.height / 2f)).isAtMost(1f)
    }

    @Test
    fun `long texts at large scale in RTL stay centred and clear of the buttons`() {
        val large = SancaiTheme.light().copy(textScale = SancaiTheme.LARGE_TEXT_SCALE)
        val bar = bar(large)
        bar.layoutDirection = View.LAYOUT_DIRECTION_RTL
        bar.title = "Protection strength and schedule for every blocked number"
        bar.subtitle = "A subtitle that is far too long to fit between the two button groups"
        bar.setNavigation { }
        val first = bar.addAction(SancaiIcon.SEARCH, "Search", null)
        bar.addAction(SancaiIcon.PLUS, "Add", null)
        Snapshots.layout(bar, 320)
        val nav = bar.navigationButton!!
        for (text in texts(bar)) {
            assertThat(abs(centreX(text) - bar.width / 2f)).isAtMost(0.5f)
            assertThat(text.left).isAtLeast(first.right)
            assertThat(text.right).isAtMost(nav.left)
            assertThat(text.right).isAtMost(bar.width - first.right)
            assertThat(text.lineCount).isEqualTo(1)
        }
    }

    @Test
    fun `a narrow bar never gives the title a negative width`() {
        val bar = bar()
        bar.setNavigation { }
        bar.addAction(SancaiIcon.SEARCH, "Search", null)
        bar.addAction(SancaiIcon.PLUS, "Add", null)
        Snapshots.layout(bar, 150)
        assertThat(titleOf(bar).width).isAtLeast(0)
        assertThat(bar.height).isEqualTo(px(56f))
    }

    @Test
    fun `touches on the bar itself do not fall through to content underneath`() {
        var below = 0
        val frame = android.widget.FrameLayout(context)
        frame.addView(View(context).apply { setOnClickListener { below++ } }, 1080, 400)
        val bar = bar()
        frame.addView(bar, android.widget.FrameLayout.LayoutParams(-1, -2))
        Snapshots.attach(frame)
        Touch.tap(frame, frame.width / 2f, bar.height / 2f)
        Snapshots.idle()
        assertThat(below).isEqualTo(0)
    }

    @Test
    fun `a key press sinks the icon and the key release puts it back`() {
        val bar = bar()
        bar.setNavigation { }
        Snapshots.attach(bar)
        val nav = bar.navigationButton!!
        nav.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertThat(nav.translationY).isWithin(0.01f).of(1.5f * density)
        nav.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        assertThat(nav.translationY).isEqualTo(0f)
    }

    @Test
    fun `xml attributes set title, subtitle, navigation and flags`() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTitle, "Rules")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTopBarSubtitle, "3 active")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiIcon, "close")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTopBarNavigationDescription, "Dismiss")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTopBarLifted, "true")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTopBarFitStatusBar, "false")
            .build()
        val bar = SancaiTopBar(context, attrs)
        assertThat(bar.title.toString()).isEqualTo("Rules")
        assertThat(bar.subtitle.toString()).isEqualTo("3 active")
        assertThat(bar.navigationButton!!.contentDescription.toString()).isEqualTo("Dismiss")
        assertThat(bar.lifted).isTrue()
        assertThat(bar.fitStatusBar).isFalse()
    }

    private fun gallery(theme: SancaiTheme): View {
        val themed = Snapshots.context(theme)
        val column = LinearLayout(themed).apply { orientation = LinearLayout.VERTICAL }
        fun add(caption: String, configure: SancaiTopBar.() -> Unit) {
            column.addView(
                SancaiText.create(themed, caption, SancaiTextRole.MICRO, SancaiTextTone.MUTED).apply {
                    setPadding(px(16f), px(14f), px(16f), px(4f))
                },
            )
            column.addView(SancaiTopBar(themed).apply(configure))
        }
        add("title only") { title = "Sancai" }
        add("navigation") {
            title = "Blocked numbers"
            setNavigation { }
        }
        add("navigation, subtitle, two actions, lifted") {
            title = "Blocked numbers"
            subtitle = "12 entries"
            setNavigation { }
            addAction(SancaiIcon.SEARCH, "Search", null)
            addAction(SancaiIcon.PLUS, "Add", null)
            lifted = true
        }
        add("close, long title, one action") {
            title = "Protection strength and schedule for weekdays"
            setNavigation(SancaiIcon.CLOSE, "Close", null)
            addAction(SancaiIcon.CHECK, "Save", null)
        }
        add("right-to-left") {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            title = "الأرقام المحظورة"
            setNavigation { }
            addAction(SancaiIcon.SEARCH, "Search", null)
        }
        add("disabled") {
            title = "Blocked numbers"
            setNavigation { }
            addAction(SancaiIcon.TRASH, "Delete", null)
            isEnabled = false
        }
        column.setPadding(0, 0, 0, px(14f))
        return column
    }

    @Test
    fun `snapshot of top bars in light and dark`() {
        Snapshots.capture(gallery(SancaiTheme.light()), "topbar_light", 360)
        val dark = Snapshots.capture(gallery(SancaiTheme.dark()), "topbar_dark", 360)
        assertThat(dark.getPixel(2, 2)).isEqualTo(SancaiTheme.dark().colors.canvas)
        assertThat(Color.alpha(dark.getPixel(2, 2))).isEqualTo(255)
    }
}
