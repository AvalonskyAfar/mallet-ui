package io.github.avalonskyafar.sancai.widget

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Parcelable
import android.util.SparseArray
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.theme.SancaiSpacing
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.roundToInt
import io.github.avalonskyafar.sancai.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiPageTest {

    private val context = Snapshots.context(SancaiTheme.light())
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

    // ---- SancaiSpacing ----

    @Test
    fun `spacing constants carry the rhythm of the original pages`() {
        assertThat(SancaiSpacing.PAGE_SIDE).isEqualTo(12f)
        assertThat(SancaiSpacing.PAGE_TOP).isEqualTo(22f)
        assertThat(SancaiSpacing.PAGE_BOTTOM).isEqualTo(34f)
        assertThat(SancaiSpacing.TEXT_INSET).isEqualTo(10f)
        assertThat(SancaiSpacing.TITLE_GAP).isEqualTo(16f)
        assertThat(SancaiSpacing.HEADING_GAP).isEqualTo(10f)
        assertThat(SancaiSpacing.SECTION_GAP).isEqualTo(22f)
        assertThat(SancaiSpacing.CARD_PADDING).isEqualTo(9f)
        assertThat(SancaiSpacing.DIVIDER_GAP).isEqualTo(5f)
        assertThat(SancaiSpacing.BUTTON_SIDE).isEqualTo(30f)
        assertThat(SancaiSpacing.CARD_PADDING).isEqualTo(SancaiSurface.CONTENT_PADDING_DP)
    }

    // ---- scaffold ----

    @Test
    fun `page is a still scroll view on the canvas colour`() {
        val page = SancaiPage(context)
        assertThat(page.isFillViewport).isTrue()
        assertThat(page.clipToPadding).isFalse()
        assertThat(page.isVerticalScrollBarEnabled).isFalse()
        assertThat(page.isHorizontalScrollBarEnabled).isFalse()
        assertThat(page.overScrollMode).isEqualTo(View.OVER_SCROLL_IF_CONTENT_SCROLLS)
        assertThat(page.childCount).isEqualTo(1)
        assertThat(page.getChildAt(0)).isSameInstanceAs(page.content)
        assertThat(page.content.orientation).isEqualTo(LinearLayout.VERTICAL)
        assertThat(page.accessibilityClassName.toString()).isEqualTo(ScrollView::class.java.name)

        for (theme in listOf(SancaiTheme.light(), SancaiTheme.dark())) {
            val themed = SancaiPage(Snapshots.context(theme))
            themed.addView(block(40))
            val bitmap = Snapshots.render(themed, 200)
            assertThat(bitmap.getPixel(3, 3)).isEqualTo(theme.colors.canvas)
            assertThat(bitmap.getPixel(bitmap.width - 3, bitmap.height - 3)).isEqualTo(theme.colors.canvas)
        }
    }

    @Test
    fun `padding follows the spacing scale without insets`() {
        val page = SancaiPage(context)
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
        val page = SancaiPage(context)
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
        val page = SancaiPage(context)
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
            .addAttribute(R.attr.sancaiPageFitSystemBars, "false")
            .addAttribute(android.R.attr.padding, "4dp")
            .build()
        val page = SancaiPage(context, attrs)
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
        val page = SancaiPage(context)
        page.setPagePadding(20f, 0f, 8f, 0f)
        page.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.attach(page)
        assertThat(page.paddingLeft).isEqualTo(px(8f))
        assertThat(page.paddingRight).isEqualTo(px(20f))
    }

    // ---- children ----

    @Test
    fun `every addView overload lands in the column`() {
        val page = SancaiPage(context)
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
        val page = SancaiPage(context)
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

    private fun tallPage(id: Int): SancaiPage = SancaiPage(context).also {
        it.id = id
        repeat(10) { _ -> it.addView(block(200)) }
        Snapshots.layout(it, 300, 400)
    }

    @Test
    fun `short page fills the viewport and long page scrolls past its padding`() {
        val short = SancaiPage(context)
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
        val shorter = SancaiPage(context).also {
            it.id = 101
            repeat(3) { _ -> it.addView(block(200)) }
        }
        shorter.restoreHierarchyState(saved)
        Snapshots.layout(shorter, 300, 400)
        assertThat(shorter.scrollY).isEqualTo(px(22f) + 3 * px(200f) + px(34f) - px(400f))

        // A page without an id has nothing to restore from.
        val anonymous = SancaiPage(context)
        repeat(10) { anonymous.addView(block(200)) }
        anonymous.restoreHierarchyState(saved)
        Snapshots.layout(anonymous, 300, 400)
        assertThat(anonymous.scrollY).isEqualTo(0)
    }

    @Test
    fun `empty page is plain canvas and does not scroll`() {
        val page = SancaiPage(context)
        Snapshots.layout(page, 300)
        assertThat(page.height).isEqualTo(px(22f) + px(34f))
        Snapshots.layout(page, 300, 400)
        assertThat(page.content.childCount).isEqualTo(0)
        assertThat(page.canScrollVertically(1)).isFalse()
        assertThat(page.canScrollVertically(-1)).isFalse()
        val bitmap = Snapshots.render(page, 300, 400)
        assertThat(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)).isEqualTo(SancaiTheme.light().colors.canvas)
        page.removeAllViews()
        assertThat(page.childCount).isEqualTo(1)
    }

    @Test
    fun `indexed removal works on the column and never removes it`() {
        val page = SancaiPage(context)
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
        val short = SancaiPage(context)
        short.addView(block(40))
        Snapshots.layout(short, 300, 400)
        assertThat(short.performAccessibilityAction(4096, null)).isFalse()
        assertThat(short.scrollY).isEqualTo(0)
    }

    @Test
    fun `a row asked on screen clears the bars and the keyboard arriving reveals the focused field`() {
        val page = SancaiPage(context)
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
        val page = SancaiPage(context)
        val title = page.addTitle("Settings")
        val card = page.addSection("Protection", "How firmly the app holds on", SancaiSurface(context))
        card.addView(block(50))
        val space = page.addSpace(13f)
        val button = page.addButton(SancaiButton(context).also { it.text = "Restore" })
        Snapshots.layout(page, 360)

        val column = page.content
        assertThat(column.childCount).isEqualTo(5)
        val heading = column.getChildAt(1) as LinearLayout
        val headingTitle = heading.getChildAt(0) as SancaiText
        val headingDetail = heading.getChildAt(1) as SancaiText

        assertThat(title.role).isEqualTo(SancaiTextRole.TITLE)
        assertThat(headingTitle.role).isEqualTo(SancaiTextRole.HEADING)
        assertThat(headingTitle.tone).isEqualTo(SancaiTextTone.INK)
        assertThat(headingDetail.role).isEqualTo(SancaiTextRole.CAPTION)
        assertThat(headingDetail.tone).isEqualTo(SancaiTextTone.MUTED)
        assertThat(headingDetail.text.toString()).isEqualTo("How firmly the app holds on")
        assertThat(ViewCompat.isAccessibilityHeading(title)).isTrue()
        assertThat(ViewCompat.isAccessibilityHeading(headingTitle)).isTrue()
        assertThat(ViewCompat.isAccessibilityHeading(headingDetail)).isFalse()

        // Horizontal: text is inset 10 dp, the card spans the column, the button stands 30 dp in.
        assertThat(title.left).isEqualTo(px(10f))
        assertThat(title.right).isEqualTo(column.width - px(10f))
        assertThat(heading.left).isEqualTo(px(10f))
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
        val page = SancaiPage(context)
        page.addSection("Only a title", null, block(20))
        page.addSection(null, "Only a detail", block(20))
        page.addSection(null, "  ", block(20))
        Snapshots.layout(page, 360)

        val column = page.content
        assertThat(column.childCount).isEqualTo(5)
        val first = column.getChildAt(0) as LinearLayout
        assertThat(first.childCount).isEqualTo(1)
        val second = column.getChildAt(2) as LinearLayout
        assertThat(second.childCount).isEqualTo(1)
        // A lone detail line has no gap above it.
        assertThat(second.getChildAt(0).top).isEqualTo(0)
        // No heading at all: the card follows the previous section directly.
        assertThat(column.getChildAt(4).top - column.getChildAt(3).bottom).isEqualTo(px(22f))
    }

    @Test
    fun `headings are start aligned and mirror in rtl`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val page = SancaiPage(context)
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
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = 1.13f))
        val page = SancaiPage(large)
        val title = page.addTitle("A page title that is long enough to wrap onto a second line at this width")
        val card = page.addSection("Heading", "Detail", View(large))
        Snapshots.layout(page, 240)
        assertThat(title.lineCount).isGreaterThan(1)
        assertThat(title.layout.getLineWidth(0)).isAtMost(title.width.toFloat())
        assertThat(card.top).isGreaterThan(title.bottom + px(16f))
    }

    // ---- snapshots ----

    private fun row(themed: android.content.Context, text: String, tone: SancaiTextTone = SancaiTextTone.INK): SancaiText =
        SancaiText.create(themed, text, SancaiTextRole.BODY, tone).also {
            it.gravity = Gravity.CENTER
            it.minHeight = px(46f)
        }

    private fun samplePage(theme: SancaiTheme): SancaiPage {
        val themed = Snapshots.context(theme)
        val page = SancaiPage(themed)
        page.addTitle("Settings")

        val strength = page.addSection(
            "Protection strength",
            "The higher the level, the more readily suspicious content is held back",
            SancaiSurface(themed),
        )
        strength.addView(row(themed, "Short video"))
        strength.addView(row(themed, "Live"))
        strength.addDivider(SancaiSpacing.DIVIDER_GAP)
        strength.addView(row(themed, "Allowed accounts", SancaiTextTone.MUTED))

        val status = page.addSection(
            "Status notification",
            "Shows in the notification shade whether protection is at work",
            SancaiSurface(themed),
        )
        status.addView(row(themed, "Keep a status notification"))

        page.addButton(SancaiButton(themed).also { it.text = "Restore all defaults" })
        return page
    }

    @Test
    fun `snapshot of page in light and dark`() {
        for ((name, theme) in listOf("page_light" to SancaiTheme.light(), "page_dark" to SancaiTheme.dark())) {
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
