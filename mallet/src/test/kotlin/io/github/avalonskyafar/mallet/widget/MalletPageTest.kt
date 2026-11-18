package io.github.avalonskyafar.mallet.widget

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Parcelable
import android.util.SparseArray
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.theme.MalletSpacing
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import kotlin.math.roundToInt
import io.github.avalonskyafar.mallet.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletPageTest {

    private val context = Snapshots.context(MalletTheme.light())
    private val density = context.resources.displayMetrics.density

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private fun block(heightDp: Int, color: Int = Color.TRANSPARENT): View = View(context).also {
        it.setBackgroundColor(color)
        it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(heightDp.toFloat()))
    }

    private fun insets(left: Int, top: Int, right: Int, bottom: Int, keyboard: Int = 0): WindowInsetsCompat =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(left, top, right, bottom))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
            .build()

    // ---- MalletSpacing ----

    @Test
    fun `spacing constants carry the rhythm of the original pages`() {
        assertThat(MalletSpacing.PAGE_SIDE).isEqualTo(12f)
        assertThat(MalletSpacing.PAGE_TOP).isEqualTo(22f)
        assertThat(MalletSpacing.PAGE_BOTTOM).isEqualTo(34f)
        assertThat(MalletSpacing.TEXT_INSET).isEqualTo(10f)
        assertThat(MalletSpacing.TITLE_GAP).isEqualTo(16f)
        assertThat(MalletSpacing.HEADING_GAP).isEqualTo(10f)
        assertThat(MalletSpacing.SECTION_GAP).isEqualTo(22f)
        assertThat(MalletSpacing.CARD_PADDING).isEqualTo(9f)
        assertThat(MalletSpacing.DIVIDER_GAP).isEqualTo(5f)
        assertThat(MalletSpacing.BUTTON_SIDE).isEqualTo(30f)
        assertThat(MalletSpacing.CARD_PADDING).isEqualTo(MalletSurface.CONTENT_PADDING_DP)
    }

    // ---- scaffold ----

    @Test
    fun `page is a still scroll view on the canvas colour`() {
        val page = MalletPage(context)
        assertThat(page.isFillViewport).isTrue()
        assertThat(page.clipToPadding).isFalse()
        assertThat(page.isVerticalScrollBarEnabled).isFalse()
        assertThat(page.isHorizontalScrollBarEnabled).isFalse()
        assertThat(page.overScrollMode).isEqualTo(View.OVER_SCROLL_IF_CONTENT_SCROLLS)
        assertThat(page.childCount).isEqualTo(1)
        assertThat(page.getChildAt(0)).isSameInstanceAs(page.content)
        assertThat(page.content.orientation).isEqualTo(LinearLayout.VERTICAL)
        assertThat(page.accessibilityClassName.toString()).isEqualTo(ScrollView::class.java.name)

        for (theme in listOf(MalletTheme.light(), MalletTheme.dark())) {
            val themed = MalletPage(Snapshots.context(theme))
            themed.addView(block(40))
            val bitmap = Snapshots.render(themed, 200)
            assertThat(bitmap.getPixel(3, 3)).isEqualTo(theme.colors.canvas)
            assertThat(bitmap.getPixel(bitmap.width - 3, bitmap.height - 3)).isEqualTo(theme.colors.canvas)
        }
    }

    @Test
    fun `padding follows the spacing scale without insets`() {
        val page = MalletPage(context)
        assertThat(page.paddingLeft).isEqualTo(px(12f))
        assertThat(page.paddingTop).isEqualTo(px(22f))
        assertThat(page.paddingRight).isEqualTo(px(12f))
        assertThat(page.paddingBottom).isEqualTo(px(34f))

        val child = block(40)
        page.addView(child)
        Snapshots.layout(page, 300)
        assertThat(page.content.left).isEqualTo(px(12f))
        assertThat(page.content.top).isEqualTo(px(22f))
        assertThat(page.content.width).isEqualTo(px(300f) - 2 * px(12f))
        assertThat(child.width).isEqualTo(page.content.width)
        assertThat(page.height).isEqualTo(px(22f) + px(40f) + px(34f))
    }

    @Test
    fun `system bars cutout and keyboard are added to the padding`() {
        val page = MalletPage(context)
        val result = ViewCompat.dispatchApplyWindowInsets(page, insets(7, 63, 11, 48))
        assertThat(page.paddingLeft).isEqualTo(px(12f) + 7)
        assertThat(page.paddingTop).isEqualTo(px(22f) + 63)
        assertThat(page.paddingRight).isEqualTo(px(12f) + 11)
        assertThat(page.paddingBottom).isEqualTo(px(34f) + 48)
        // Passed on unconsumed, so siblings and children still see the bars.
        assertThat(result.getInsets(WindowInsetsCompat.Type.systemBars()).top).isEqualTo(63)

        // The keyboard replaces the navigation bar at the bottom, it is not added to it.
        ViewCompat.dispatchApplyWindowInsets(page, insets(0, 63, 0, 48, keyboard = 700))
        assertThat(page.paddingLeft).isEqualTo(px(12f))
        assertThat(page.paddingBottom).isEqualTo(px(34f) + 700)

        // A second pass with the same insets does not accumulate.
        ViewCompat.dispatchApplyWindowInsets(page, insets(0, 63, 0, 48))
        ViewCompat.dispatchApplyWindowInsets(page, insets(0, 63, 0, 48))
        assertThat(page.paddingTop).isEqualTo(px(22f) + 63)
        assertThat(page.paddingBottom).isEqualTo(px(34f) + 48)
    }

    @Test
    fun `fitSystemBars off ignores insets and on applies the last ones again`() {
        val page = MalletPage(context)
        page.fitSystemBars = false
        ViewCompat.dispatchApplyWindowInsets(page, insets(7, 63, 11, 48))
        assertThat(page.paddingLeft).isEqualTo(px(12f))
        assertThat(page.paddingTop).isEqualTo(px(22f))
        assertThat(page.paddingRight).isEqualTo(px(12f))
        assertThat(page.paddingBottom).isEqualTo(px(34f))

        page.fitSystemBars = true
        assertThat(page.paddingTop).isEqualTo(px(22f) + 63)
        assertThat(page.paddingBottom).isEqualTo(px(34f) + 48)

        page.fitSystemBars = false
        assertThat(page.paddingTop).isEqualTo(px(22f))
    }

    @Test
    fun `xml attributes set the inset switch and replace the padding`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.malletPageFitSystemBars, "false")
            .addAttribute(android.R.attr.padding, "4dp")
            .build()
        val page = MalletPage(context, attrs)
        assertThat(page.fitSystemBars).isFalse()
        assertThat(page.paddingLeft).isEqualTo(px(4f))
        assertThat(page.paddingBottom).isEqualTo(px(4f))

        page.fitSystemBars = true
        ViewCompat.dispatchApplyWindowInsets(page, insets(0, 63, 0, 48))
        assertThat(page.paddingTop).isEqualTo(px(4f) + 63)

        page.setPagePadding(20f, 0f, 8f, 10f)
        assertThat(page.paddingLeft).isEqualTo(px(20f))
        assertThat(page.paddingTop).isEqualTo(63)
        assertThat(page.paddingRight).isEqualTo(px(8f))
        assertThat(page.paddingBottom).isEqualTo(px(10f) + 48)
    }

    @Test
    fun `start and end padding mirror in rtl`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val page = MalletPage(context)
        page.setPagePadding(20f, 0f, 8f, 0f)
        page.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.attach(page)
        assertThat(page.paddingLeft).isEqualTo(px(8f))
        assertThat(page.paddingRight).isEqualTo(px(20f))
    }

    // ---- children ----

    @Test
    fun `every addView overload lands in the column`() {
        val page = MalletPage(context)
        val a = View(context)
        val b = View(context)
        val c = View(context)
        val d = View(context)
        val e = View(context)
        page.addView(a)
        page.addView(b, 0)
        page.addView(c, ViewGroup.LayoutParams(50, 60))
        page.addView(d, 1, ViewGroup.MarginLayoutParams(50, 60).also { it.topMargin = 9 })
        page.addView(e, 70, 80)

        assertThat(page.childCount).isEqualTo(1)
        assertThat((0 until page.content.childCount).map { page.content.getChildAt(it) })
            .containsExactly(b, d, a, c, e).inOrder()
        for (child in listOf(a, b, c, d, e)) {
            assertThat(child.layoutParams).isInstanceOf(LinearLayout.LayoutParams::class.java)
        }
        // A child without parameters spans the column and wraps its height.
        assertThat(a.layoutParams.width).isEqualTo(ViewGroup.LayoutParams.MATCH_PARENT)
        assertThat(a.layoutParams.height).isEqualTo(ViewGroup.LayoutParams.WRAP_CONTENT)
        assertThat(c.layoutParams.width).isEqualTo(50)
        assertThat((d.layoutParams as LinearLayout.LayoutParams).topMargin).isEqualTo(9)
        assertThat(e.layoutParams.height).isEqualTo(80)

        page.removeView(a)
        assertThat(page.content.childCount).isEqualTo(4)
        page.removeView(page.content)
        assertThat(page.childCount).isEqualTo(1)
        page.removeAllViews()
        assertThat(page.content.childCount).isEqualTo(0)
        assertThat(page.getChildAt(0)).isSameInstanceAs(page.content)
    }

    @Test
    fun `xml children keep margins gravity and weight`() {
        val page = MalletPage(context)
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.layout_width, "120dp")
            .addAttribute(android.R.attr.layout_height, "40dp")
            .addAttribute(android.R.attr.layout_marginTop, "8dp")
            .addAttribute(android.R.attr.layout_marginStart, "6dp")
            .addAttribute(android.R.attr.layout_gravity, "center_horizontal")
            .addAttribute(android.R.attr.layout_weight, "1")
            .build()
        // What LayoutInflater does for a child tag.
        val child = View(context)
        page.addView(child, page.generateLayoutParams(attrs))

        val params = child.layoutParams as LinearLayout.LayoutParams
        assertThat(child.parent).isSameInstanceAs(page.content)
        assertThat(params.width).isEqualTo(px(120f))
        assertThat(params.height).isEqualTo(px(40f))
        assertThat(params.topMargin).isEqualTo(px(8f))
        assertThat(params.marginStart).isEqualTo(px(6f))
        assertThat(params.gravity).isEqualTo(Gravity.CENTER_HORIZONTAL)
        assertThat(params.weight).isEqualTo(1f)

        Snapshots.layout(page, 300)
        val column = page.content.width
        assertThat(child.left).isEqualTo((column - px(120f)) / 2 + px(6f))
    }

    // ---- scrolling and state ----

    private fun tallPage(id: Int): MalletPage = MalletPage(context).also {
        it.id = id
        repeat(10) { _ -> it.addView(block(200)) }
        Snapshots.layout(it, 300, 400)
    }

    @Test
    fun `short page fills the viewport and long page scrolls past its padding`() {
        val short = MalletPage(context)
        short.addView(block(40))
        Snapshots.layout(short, 300, 400)
        assertThat(short.content.height).isEqualTo(px(400f) - px(22f) - px(34f))
        assertThat(short.canScrollVertically(1)).isFalse()

        val long = tallPage(101)
        assertThat(long.canScrollVertically(1)).isTrue()
        long.scrollTo(0, 100_000)
        assertThat(long.scrollY).isEqualTo(px(22f) + 10 * px(200f) + px(34f) - px(400f))
    }

    @Test
    fun `on a wide page the column keeps its limit and stands in the middle`() {
        val page = MalletPage(context)
        page.addView(block(40))
        // Narrow: the column fills the room between the paddings.
        Snapshots.layout(page, 360, 600)
        assertThat(page.content.width).isEqualTo(px(360f) - 2 * px(MalletSpacing.PAGE_SIDE))

        // Wide (a tablet, a phone on its side): 560 dp, centred, also when the page is short
        // and the scroll view stretches the column to the viewport.
        Snapshots.layout(page, 900, 600)
        assertThat(page.content.width).isEqualTo(px(560f))
        assertThat(page.content.left).isEqualTo((px(900f) - px(560f)) / 2)
        assertThat(page.content.height).isEqualTo(px(600f) - px(22f) - px(34f))

        page.maxContentWidthDp = 700f
        Snapshots.layout(page, 900, 600)
        assertThat(page.content.width).isEqualTo(px(700f))

        page.maxContentWidthDp = 0f
        Snapshots.layout(page, 900, 600)
        assertThat(page.content.width).isEqualTo(px(900f) - 2 * px(MalletSpacing.PAGE_SIDE))
    }

    @Test
    fun `a page that joins a screen late asks for the window insets`() {
        var asked = 0
        val parent = object : android.widget.FrameLayout(context) {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun requestFitSystemWindows() {
                asked++
                super.requestFitSystemWindows()
            }
        }
        Snapshots.attach(parent, 300)
        asked = 0

        parent.addView(MalletPage(context))
        assertThat(asked).isEqualTo(1)

        // A page that keeps clear of the bars by other means does not ask.
        parent.addView(MalletPage(context).apply { fitSystemBars = false })
        assertThat(asked).isEqualTo(1)
    }

    /** What the page shows on screen: the scrolled content, as its parent would draw it. */
    private fun onScreen(page: MalletPage): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.translate(0f, -page.scrollY.toFloat())
        page.draw(canvas)
        return bitmap
    }

    @Test
    fun `scrolling content ends at the status bar instead of sliding under the clock`() {
        val page = MalletPage(context)
        page.addView(block(3000, Color.RED))
        ViewCompat.dispatchApplyWindowInsets(page, insets(0, 63, 0, 0))
        Snapshots.layout(page, 300, 400)
        page.scrollTo(0, px(600f))
        val picture = onScreen(page)
        val x = picture.width / 2
        val canvasColour = MalletTheme.light().colors.canvas

        // Behind the status bar: the page's own colour, although content lies there.
        assertThat(picture.getPixel(x, 10)).isEqualTo(canvasColour)
        assertThat(picture.getPixel(x, 62)).isEqualTo(canvasColour)
        // Just below the bar the content is still faint, a little further down it is itself.
        val faint = picture.getPixel(x, 63 + px(2f))
        assertThat(Color.green(faint)).isGreaterThan(120)
        assertThat(picture.getPixel(x, 63 + px(MalletPage.BAR_FADE_DP) + 2)).isEqualTo(Color.RED)
        // The lower edge has no bar here: content runs to the edge.
        assertThat(picture.getPixel(x, picture.height - 2)).isEqualTo(Color.RED)
    }

    @Test
    fun `content shows under a gesture handle but not under a bar with buttons`() {
        fun bottomInsets(tappable: Int): WindowInsetsCompat = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 0, 0, 126))
            .setInsets(WindowInsetsCompat.Type.tappableElement(), Insets.of(0, 0, 0, tappable))
            .build()

        val page = MalletPage(context)
        page.addView(block(3000, Color.RED))
        // Gesture navigation: an inset, but nothing to tap in it.
        ViewCompat.dispatchApplyWindowInsets(page, bottomInsets(tappable = 0))
        Snapshots.layout(page, 300, 400)
        page.scrollTo(0, px(600f))
        var picture = onScreen(page)
        assertThat(picture.getPixel(picture.width / 2, picture.height - 10)).isEqualTo(Color.RED)

        // Three buttons: the bar is kept clear.
        ViewCompat.dispatchApplyWindowInsets(page, bottomInsets(tappable = 126))
        Snapshots.layout(page, 300, 400)
        page.scrollTo(0, px(600f))
        picture = onScreen(page)
        val canvasColour = MalletTheme.light().colors.canvas
        assertThat(picture.getPixel(picture.width / 2, picture.height - 10)).isEqualTo(canvasColour)
        assertThat(picture.getPixel(picture.width / 2, picture.height - 126 - px(MalletPage.BAR_FADE_DP) - 2)).isEqualTo(Color.RED)

        // A page that is told not to fit the bars draws everywhere.
        page.fitSystemBars = false
        Snapshots.layout(page, 300, 400)
        page.scrollTo(0, px(600f))
        picture = onScreen(page)
        assertThat(picture.getPixel(picture.width / 2, picture.height - 10)).isEqualTo(Color.RED)
    }

    @Test
    fun `a page opens at its top, whatever in it could take the focus`() {
        val page = MalletPage(context)
        page.addView(block(500))
        // Half on screen, and focusable by touch: a text field, or text that can be selected.
        val field = android.widget.EditText(context)
        page.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(300f)))
        page.addView(block(900))
        Snapshots.attach(page, 300)
        Snapshots.layout(page, 300, 600)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()

        // What a window does when it opens (before Android 8.0) or loses its focused view.
        instrumentation.setInTouchMode(true)
        assertThat(page.requestFocus(View.FOCUS_FORWARD)).isFalse()
        Snapshots.idle()
        assertThat(field.isFocused).isFalse()
        assertThat(page.scrollY).isEqualTo(0)

        // Asked for by name, the field takes the focus and is brought on screen.
        assertThat(field.requestFocus()).isTrue()
        Snapshots.idle()
        assertThat(field.isFocused).isTrue()
        assertThat(page.scrollY).isGreaterThan(0)
        // (With a keyboard, focus travels into the page as on any screen; the device tests
        // walk it there with the arrow keys.)
    }

    @Test
    fun `scroll position is saved and restored`() {
        val first = tallPage(101)
        first.scrollTo(0, 777)
        assertThat(first.scrollY).isEqualTo(777)
        val saved = SparseArray<Parcelable>()
        first.saveHierarchyState(saved)

        val second = tallPage(101)
        assertThat(second.scrollY).isEqualTo(0)
        second.restoreHierarchyState(saved)
        Snapshots.layout(second, 300, 400)
        assertThat(second.scrollY).isEqualTo(777)

        // Content that became shorter: the position is clamped, never past the end.
        val shorter = MalletPage(context).also {
            it.id = 101
            repeat(3) { _ -> it.addView(block(200)) }
        }
        shorter.restoreHierarchyState(saved)
        Snapshots.layout(shorter, 300, 400)
        assertThat(shorter.scrollY).isEqualTo(px(22f) + 3 * px(200f) + px(34f) - px(400f))

        // A page without an id has nothing to restore from.
        val anonymous = MalletPage(context)
        repeat(10) { anonymous.addView(block(200)) }
        anonymous.restoreHierarchyState(saved)
        Snapshots.layout(anonymous, 300, 400)
        assertThat(anonymous.scrollY).isEqualTo(0)
    }

    @Test
    fun `empty page is plain canvas and does not scroll`() {
        val page = MalletPage(context)
        Snapshots.layout(page, 300)
        assertThat(page.height).isEqualTo(px(22f) + px(34f))
        Snapshots.layout(page, 300, 400)
        assertThat(page.content.childCount).isEqualTo(0)
        assertThat(page.canScrollVertically(1)).isFalse()
        assertThat(page.canScrollVertically(-1)).isFalse()
        val bitmap = Snapshots.render(page, 300, 400)
        assertThat(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)).isEqualTo(MalletTheme.light().colors.canvas)
        page.removeAllViews()
        assertThat(page.childCount).isEqualTo(1)
    }

    @Test
    fun `indexed removal works on the column and never removes it`() {
        val page = MalletPage(context)
        val views = List(4) { View(context) }
        views.forEach { page.addView(it) }
        page.removeViewAt(0)
        assertThat(page.getChildAt(0)).isSameInstanceAs(page.content)
        assertThat(page.content.getChildAt(0)).isSameInstanceAs(views[1])
        page.removeViews(1, 2)
        assertThat(page.content.childCount).isEqualTo(1)
        assertThat(page.content.getChildAt(0)).isSameInstanceAs(views[1])
        assertThat(page.childCount).isEqualTo(1)
    }

    @Test
    fun `accessibility scroll actions move a long page only`() {
        val long = tallPage(101)
        assertThat(long.performAccessibilityAction(4096 /* ACTION_SCROLL_FORWARD */, null)).isTrue()
        assertThat(long.scrollY).isGreaterThan(0)
        val short = MalletPage(context)
        short.addView(block(40))
        Snapshots.layout(short, 300, 400)
        assertThat(short.performAccessibilityAction(4096, null)).isFalse()
        assertThat(short.scrollY).isEqualTo(0)
    }

    @Test
    fun `a row asked on screen clears the bars and the keyboard arriving reveals the focused field`() {
        val page = MalletPage(context)
        val rows = List(10) { block(100) }
        rows.forEach { page.addView(it) }
        ViewCompat.dispatchApplyWindowInsets(page, insets(0, 60, 0, 120))
        Snapshots.layout(page, 300, 400)
        val h = page.height

        // Row 2 ends above the fold of the view but underneath the bottom bar.
        val row = rows[2]
        assertThat(page.paddingTop + row.bottom).isLessThan(h)
        assertThat(page.paddingTop + row.bottom).isGreaterThan(h - page.paddingBottom)
        row.requestRectangleOnScreen(android.graphics.Rect(0, 0, row.width, row.height), true)
        assertThat(page.paddingTop + row.bottom - page.scrollY).isEqualTo(h - page.paddingBottom)
        // ... and back up: the first row comes out from under the top bar, not just into the view.
        rows[0].requestRectangleOnScreen(android.graphics.Rect(0, 0, row.width, row.height), true)
        assertThat(page.scrollY).isEqualTo(0)

        // The keyboard arrives as an inset: the focused row is scrolled clear of it.
        val field = rows[1]
        field.isFocusable = true
        field.isFocusableInTouchMode = true
        assertThat(field.requestFocus()).isTrue()
        page.scrollTo(0, 0)
        ViewCompat.dispatchApplyWindowInsets(page, insets(0, 60, 0, 120, keyboard = 500))
        Snapshots.layout(page, 300, 400)
        assertThat(page.paddingBottom).isEqualTo(px(34f) + 500)
        assertThat(page.paddingTop + field.bottom - page.scrollY).isAtMost(h - page.paddingBottom)
        assertThat(page.paddingTop + field.top - page.scrollY).isAtLeast(page.paddingTop)
    }

    // ---- rhythm helpers ----

    @Test
    fun `helpers place title section button and space with the page rhythm`() {
        val page = MalletPage(context)
        val title = page.addTitle("Settings")
        val card = page.addSection("Protection", "How firmly the app holds on", MalletSurface(context))
        card.addView(block(50))
        val space = page.addSpace(13f)
        val button = page.addButton(MalletButton(context).also { it.text = "Restore" })
        Snapshots.layout(page, 360)

        val column = page.content
        assertThat(column.childCount).isEqualTo(5)
        val heading = column.getChildAt(1) as LinearLayout
        val headingTitle = heading.getChildAt(0) as MalletText
        val headingDetail = heading.getChildAt(1) as MalletText

        assertThat(title.role).isEqualTo(MalletTextRole.TITLE)
        assertThat(headingTitle.role).isEqualTo(MalletTextRole.HEADING)
        assertThat(headingTitle.tone).isEqualTo(MalletTextTone.INK)
        assertThat(headingDetail.role).isEqualTo(MalletTextRole.CAPTION)
        assertThat(headingDetail.tone).isEqualTo(MalletTextTone.MUTED)
        assertThat(headingDetail.text.toString()).isEqualTo("How firmly the app holds on")
        assertThat(ViewCompat.isAccessibilityHeading(title)).isTrue()
        assertThat(ViewCompat.isAccessibilityHeading(headingTitle)).isTrue()
        assertThat(ViewCompat.isAccessibilityHeading(headingDetail)).isFalse()

        // Horizontal: text is inset 10 dp, the card spans the column, the button stands 30 dp in.
        assertThat(title.left).isEqualTo(px(10f))
        assertThat(title.right).isEqualTo(column.width - px(10f))
        assertThat(heading.left).isEqualTo(0)
        assertThat(headingTitle.left).isEqualTo(px(10f))
        assertThat(headingTitle.right).isEqualTo(column.width - px(10f))
        assertThat(card.left).isEqualTo(0)
        assertThat(card.width).isEqualTo(column.width)
        assertThat(button.left).isEqualTo(px(30f))
        assertThat(button.right).isEqualTo(column.width - px(30f))

        // Vertical: 16 under the title, 4 inside the heading, 10 above the card, 22 below it.
        assertThat(title.top).isEqualTo(0)
        assertThat(heading.top - title.bottom).isEqualTo(px(16f))
        assertThat(headingDetail.top - headingTitle.bottom).isEqualTo(px(4f))
        assertThat(card.top - heading.bottom).isEqualTo(px(10f))
        assertThat(space.top - card.bottom).isEqualTo(px(22f))
        assertThat(space.height).isEqualTo(px(13f))
        assertThat(space.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        assertThat(button.top).isEqualTo(space.bottom)
        assertThat(column.height - button.bottom).isEqualTo(px(22f))
    }

    @Test
    fun `section leaves out the lines it has no text for`() {
        val page = MalletPage(context)
        page.addSection("Only a title", null, block(20))
        page.addSection(null, "Only a detail", block(20))
        page.addSection(null, "  ", block(20))
        Snapshots.layout(page, 360)

        val column = page.content
        assertThat(column.childCount).isEqualTo(5)
        val first = column.getChildAt(0) as MalletHeading
        assertThat(first.getChildAt(0).visibility).isEqualTo(View.VISIBLE)
        assertThat(first.getChildAt(1).visibility).isEqualTo(View.GONE)
        assertThat(first.height).isEqualTo(first.getChildAt(0).height)
        val second = column.getChildAt(2) as MalletHeading
        assertThat(second.getChildAt(0).visibility).isEqualTo(View.GONE)
        // A lone detail line has no gap above it.
        assertThat(second.getChildAt(1).top).isEqualTo(0)
        assertThat(second.height).isEqualTo(second.getChildAt(1).height)
        // No heading at all: the card follows the previous section directly.
        assertThat(column.getChildAt(4).top - column.getChildAt(3).bottom).isEqualTo(px(22f))
    }

    @Test
    fun `headings are start aligned and mirror in rtl`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val page = MalletPage(context)
        page.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val title = page.addTitle("Settings")
        val button = page.addButton(block(40))
        Snapshots.attach(page)
        assertThat(title.textAlignment).isEqualTo(View.TEXT_ALIGNMENT_VIEW_START)
        assertThat(title.layout.getLineRight(0)).isWithin(1f).of(title.width.toFloat())
        assertThat(title.layout.getLineLeft(0)).isGreaterThan(0f)
        assertThat(button.left).isEqualTo(px(30f))
        assertThat(button.right).isEqualTo(page.content.width - px(30f))
    }

    @Test
    fun `layout survives large text`() {
        val large = Snapshots.context(MalletTheme.light().copy(textScale = 1.13f))
        val page = MalletPage(large)
        val title = page.addTitle("A page title that is long enough to wrap onto a second line at this width")
        val card = page.addSection("Heading", "Detail", View(large))
        Snapshots.layout(page, 240)
        assertThat(title.lineCount).isGreaterThan(1)
        assertThat(title.layout.getLineWidth(0)).isAtMost(title.width.toFloat())
        assertThat(card.top).isGreaterThan(title.bottom + px(16f))
    }

    // ---- snapshots ----

    private fun row(themed: android.content.Context, text: String, tone: MalletTextTone = MalletTextTone.INK): MalletText =
        MalletText.create(themed, text, MalletTextRole.BODY, tone).also {
            it.gravity = Gravity.CENTER
            it.minHeight = px(46f)
        }

    private fun samplePage(theme: MalletTheme): MalletPage {
        val themed = Snapshots.context(theme)
        val page = MalletPage(themed)
        page.addTitle("Settings")

        val strength = page.addSection(
            "Protection strength",
            "The higher the level, the more readily suspicious content is held back",
            MalletSurface(themed),
        )
        strength.addView(row(themed, "Short video"))
        strength.addView(row(themed, "Live"))
        strength.addDivider(MalletSpacing.DIVIDER_GAP)
        strength.addView(row(themed, "Allowed accounts", MalletTextTone.MUTED))

        val status = page.addSection(
            "Status notification",
            "Shows in the notification shade whether protection is at work",
            MalletSurface(themed),
        )
        status.addView(row(themed, "Keep a status notification"))

        page.addButton(MalletButton(themed).also { it.text = "Restore all defaults" })
        return page
    }

    @Test
    fun `snapshot of page in light and dark`() {
        for ((name, theme) in listOf("page_light" to MalletTheme.light(), "page_dark" to MalletTheme.dark())) {
            val page = samplePage(theme)
            ViewCompat.dispatchApplyWindowInsets(page, insets(0, px(24f), 0, px(16f)))
            val bitmap = Snapshots.capture(page, name, 360)
            assertThat(bitmap.getPixel(4, 4)).isEqualTo(theme.colors.canvas)
            // The gap between the two sections is plain canvas from edge to edge.
            val firstCard = page.content.getChildAt(2)
            val gapY = page.paddingTop + firstCard.bottom + px(11f)
            assertThat(bitmap.getPixel(bitmap.width / 2, gapY)).isEqualTo(theme.colors.canvas)
        }
    }
}
