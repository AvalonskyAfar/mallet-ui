package io.github.avalonskyafar.sancai.widget

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiLinkRow.ArrowDirection
import io.github.avalonskyafar.sancai.widget.SancaiLinkRow.ArrowPlacement
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiHeadingLinkTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val density = context.resources.displayMetrics.density

    private fun dp(value: Float): Float = value * density

    private fun heading(title: String, detail: String? = null): SancaiHeading =
        SancaiHeading(context).also {
            it.title = title
            it.detail = detail
        }

    private fun link(
        label: String = "More settings",
        placement: ArrowPlacement = ArrowPlacement.EDGE,
        direction: ArrowDirection = ArrowDirection.END,
    ): SancaiLinkRow = SancaiLinkRow(context).also {
        it.label = label
        it.arrowPlacement = placement
        it.arrowDirection = direction
    }

    private fun SancaiLinkRow.label(): RectF = RectF().also { labelBounds(it) }

    private fun SancaiLinkRow.chevron(): RectF = RectF().also { chevronBounds(it) }

    /** Bounding box of every pixel that differs from the top-left pixel, or null. */
    private fun inkBox(bitmap: Bitmap, left: Int = 0, right: Int = bitmap.width): RectF? {
        val background = bitmap.getPixel(0, 0)
        var box: RectF? = null
        for (y in 0 until bitmap.height) for (x in left until right) {
            if (bitmap.getPixel(x, y) == background) continue
            val b = box ?: RectF(x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat()).also { box = it }
            b.union(x.toFloat(), y.toFloat())
        }
        return box
    }

    // ---------------------------------------------------------------------------------------
    // Heading
    // ---------------------------------------------------------------------------------------

    @Test
    fun `heading shows an ink title and hides a missing detail`() {
        val view = heading("Protection")
        Snapshots.layout(view, 300)
        val title = view.getChildAt(0) as TextView
        val detail = view.getChildAt(1) as TextView
        val colors = SancaiTheme.light().colors

        assertThat(view.title.toString()).isEqualTo("Protection")
        assertThat(title.currentTextColor).isEqualTo(colors.ink)
        assertThat(title.textSize).isWithin(0.01f).of(17f * context.resources.displayMetrics.scaledDensity)
        assertThat(detail.visibility).isEqualTo(View.GONE)
        // 10 dp at the sides, nothing above or below, and no gap for the hidden line.
        assertThat(view.paddingStart).isEqualTo(26)
        assertThat(view.paddingEnd).isEqualTo(26)
        assertThat(view.height).isEqualTo(title.height)
        assertThat(title.left).isEqualTo(26)
        assertThat(title.width).isEqualTo(view.width - 52)
    }

    @Test
    fun `heading detail sits four dp below the title in muted caption`() {
        val view = heading("Protection", "How firmly the app holds on")
        Snapshots.layout(view, 300)
        val title = view.getChildAt(0) as TextView
        val detail = view.getChildAt(1) as TextView

        assertThat(detail.visibility).isEqualTo(View.VISIBLE)
        assertThat(detail.currentTextColor).isEqualTo(SancaiTheme.light().colors.muted)
        assertThat(detail.textSize).isWithin(0.01f).of(12.5f * context.resources.displayMetrics.scaledDensity)
        assertThat(detail.top - title.bottom).isEqualTo(11) // 4 dp = 10.5 px
        assertThat(view.height).isEqualTo(detail.bottom)

        view.detail = "   "
        assertThat(detail.visibility).isEqualTo(View.GONE)
        view.detail = null
        assertThat(view.detail).isNull()
        assertThat(detail.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `heading level page uses the title size`() {
        val view = heading("Settings")
        val title = view.getChildAt(0) as TextView
        val section = title.textSize
        view.level = SancaiHeading.Level.PAGE
        assertThat(title.textSize / section).isWithin(0.001f).of(20f / 17f)
        view.level = SancaiHeading.Level.SECTION
        assertThat(title.textSize).isEqualTo(section)
    }

    @Test
    fun `heading text is start aligned and mirrors in rtl`() {
        val ltr = Snapshots.render(heading("Protection"), 300)
        val ltrBox = inkBox(ltr)!!
        // Ink starts at the 10 dp inset (plus the side bearing of the glyph) and ends well before the middle.
        assertThat(ltrBox.left).isWithin(6f).of(dp(10f))
        assertThat(ltrBox.right).isLessThan(ltr.width / 2f)

        enableRtl()
        val view = heading("Protection").also { it.layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val rtl = Snapshots.render(view, 300)
        val rtlBox = inkBox(rtl)!!
        assertThat(rtlBox.right).isWithin(6f).of(rtl.width - dp(10f))
        assertThat(rtlBox.left).isGreaterThan(rtl.width / 2f)
    }

    @Test
    fun `heading title is announced as a heading and the view takes no focus`() {
        val view = heading("Protection", "Detail")
        assertThat(view.isFocusable).isFalse()
        assertThat(view.isClickable).isFalse()
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            assertThat(view.getChildAt(0).isAccessibilityHeading).isTrue()
            assertThat(view.getChildAt(1).isAccessibilityHeading).isFalse()
        }
    }

    private fun enableRtl() {
        val app = context.applicationContext
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
    }

    // ---------------------------------------------------------------------------------------
    // Link row: geometry
    // ---------------------------------------------------------------------------------------

    @Test
    fun `link row is sixty-four dp high`() {
        val view = link()
        Snapshots.layout(view, 360)
        assertThat(view.height).isEqualTo(168)
    }

    @Test
    fun `edge chevron never shifts the centred label`() {
        for (direction in ArrowDirection.values()) {
            val view = link(direction = direction)
            Snapshots.layout(view, 360)
            val label = view.label()
            val chevron = view.chevron()

            assertThat(label.centerX()).isWithin(0.01f).of(view.width / 2f)
            assertThat(label.centerY()).isWithin(0.01f).of(view.height / 2f)
            assertThat(chevron.width()).isWithin(0.01f).of(dp(24f))
            assertThat(chevron.height()).isWithin(0.01f).of(dp(24f))
            assertThat(chevron.centerY()).isWithin(0.01f).of(view.height / 2f)
            if (direction == ArrowDirection.END) {
                assertThat(view.width - chevron.right).isWithin(0.01f).of(dp(12f))
            } else {
                assertThat(chevron.left).isWithin(0.01f).of(dp(12f))
            }
        }
    }

    @Test
    fun `drawn edge label is centred in the row`() {
        val view = link(label = "HHHH")
        val bitmap = Snapshots.render(view, 360)
        // Look at the middle only, so the chevron at the edge is not part of the box.
        val ink = inkBox(bitmap, bitmap.width / 4, bitmap.width * 3 / 4)!!
        assertThat(ink.centerX()).isWithin(1.5f).of((bitmap.width - 1) / 2f)
        // Capitals sit on the baseline, so their centre is a little above the line box centre.
        assertThat(ink.centerY()).isWithin(dp(2.5f)).of((bitmap.height - 1) / 2f)
    }

    @Test
    fun `adjacent chevron follows the label and the pair is centred`() {
        val end = link(placement = ArrowPlacement.ADJACENT)
        Snapshots.layout(end, 360)
        assertThat(end.chevron().left - end.label().right).isWithin(0.01f).of(dp(5f))
        assertThat((end.label().left + end.chevron().right) / 2f).isWithin(0.01f).of(end.width / 2f)

        val start = link(placement = ArrowPlacement.ADJACENT, direction = ArrowDirection.START)
        Snapshots.layout(start, 360)
        assertThat(start.label().left - start.chevron().right).isWithin(0.01f).of(dp(5f))
        assertThat((start.chevron().left + start.label().right) / 2f).isWithin(0.01f).of(start.width / 2f)
    }

    @Test
    fun `rtl swaps the side of the chevron`() {
        enableRtl()
        val edge = link().also { it.layoutDirection = View.LAYOUT_DIRECTION_RTL }
        Snapshots.layout(edge, 360)
        assertThat(edge.chevron().left).isWithin(0.01f).of(dp(12f))
        assertThat(edge.label().centerX()).isWithin(0.01f).of(edge.width / 2f)

        val adjacent = link(placement = ArrowPlacement.ADJACENT).also { it.layoutDirection = View.LAYOUT_DIRECTION_RTL }
        Snapshots.layout(adjacent, 360)
        assertThat(adjacent.label().left - adjacent.chevron().right).isWithin(0.01f).of(dp(5f))

        // The chevron also points the other way: its tip is in the left half of its square.
        val bitmap = Snapshots.render(edge, 360)
        val box = edge.chevron()
        val ink = inkBox(bitmap, box.left.toInt(), box.right.toInt())!!
        val tipColumn = (ink.left.toInt()..ink.right.toInt()).first { x ->
            bitmap.getPixel(x, bitmap.height / 2) != bitmap.getPixel(0, 0)
        }
        assertThat(tipColumn.toFloat()).isLessThan(box.centerX())
    }

    @Test
    fun `long label is ellipsized between the chevron slots`() {
        val view = link(label = "A very long label that cannot possibly fit into a narrow row of text")
        Snapshots.layout(view, 240)
        val label = view.label()
        assertThat(label.centerX()).isWithin(0.01f).of(view.width / 2f)
        assertThat(label.right).isAtMost(view.chevron().left)
        assertThat(label.left).isAtLeast(dp(36f))

        val adjacent = link(label = view.label.toString(), placement = ArrowPlacement.ADJACENT)
        Snapshots.layout(adjacent, 240)
        assertThat(adjacent.label().left).isAtLeast(dp(12f) - 0.01f)
        assertThat(adjacent.chevron().right).isAtMost(adjacent.width - dp(12f) + 0.01f)
    }

    // ---------------------------------------------------------------------------------------
    // Link row: behaviour
    // ---------------------------------------------------------------------------------------

    @Test
    fun `tap clicks once and code never calls back`() {
        val view = link()
        var clicks = 0
        view.setOnClickListener { clicks++ }
        Snapshots.attach(view)

        view.label = "Other"
        view.arrowPlacement = ArrowPlacement.ADJACENT
        view.arrowDirection = ArrowDirection.START
        assertThat(clicks).isEqualTo(0)

        Touch.tap(view, view.width / 2f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `press sinks the content by one and a half dp and release restores it at once`() {
        val view = link()
        Snapshots.attach(view)
        val top = view.top
        val x = view.width / 2f
        val y = view.height / 2f

        Touch.dispatch(view, MotionEvent.ACTION_DOWN, x, y)
        ShadowLooper.idleMainLooper(SancaiMotion.PRESS_MS + 50, TimeUnit.MILLISECONDS)
        assertThat(view.contentSinkPx).isWithin(0.01f).of(dp(SancaiMotion.PRESS_SINK_DP))
        // The row itself stays where it is; only what is drawn inside moves.
        assertThat(view.top).isEqualTo(top)
        assertThat(view.translationY).isEqualTo(0f)

        Touch.dispatch(view, MotionEvent.ACTION_CANCEL, x, y)
        assertThat(view.contentSinkPx).isEqualTo(0f)
    }

    @Test
    fun `sunk content is drawn lower`() {
        val view = link(label = "HHHH")
        Snapshots.attach(view)
        val rest = inkBox(draw(view))!!
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.width / 2f, view.height / 2f)
        ShadowLooper.idleMainLooper(SancaiMotion.PRESS_MS + 50, TimeUnit.MILLISECONDS)
        val sunk = inkBox(draw(view))!!
        assertThat(sunk.top - rest.top).isWithin(1.01f).of(dp(SancaiMotion.PRESS_SINK_DP))
        assertThat(sunk.left).isEqualTo(rest.left)
        Touch.dispatch(view, MotionEvent.ACTION_CANCEL, 0f, 0f)
    }

    private fun draw(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(SancaiTheme.light().colors.canvas)
        view.draw(canvas)
        return bitmap
    }

    @Test
    fun `enter and space click and other keys are not consumed`() {
        val view = link()
        var clicks = 0
        view.setOnClickListener { clicks++ }
        Snapshots.attach(view)
        assertThat(view.isFocusable).isTrue()

        for (code in intArrayOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE)) {
            assertThat(view.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))).isTrue()
            assertThat(view.onKeyUp(code, KeyEvent(KeyEvent.ACTION_UP, code))).isTrue()
            Snapshots.idle()
        }
        assertThat(clicks).isEqualTo(2)
        assertThat(view.contentSinkPx).isEqualTo(0f)

        val right = KeyEvent.KEYCODE_DPAD_RIGHT
        assertThat(view.onKeyDown(right, KeyEvent(KeyEvent.ACTION_DOWN, right))).isFalse()
        assertThat(clicks).isEqualTo(2)
    }

    @Test
    fun `disabled row does not click or sink and pales`() {
        val view = link(label = "HHHH")
        var clicks = 0
        view.setOnClickListener { clicks++ }
        Snapshots.attach(view)
        val enabled = darkest(draw(view))
        view.isEnabled = false

        Touch.tap(view, view.width / 2f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(0)
        assertThat(view.contentSinkPx).isEqualTo(0f)

        val colors = SancaiTheme.light().colors
        assertThat(enabled).isEqualTo(colors.muted)
        // 107 / 255 of muted over the canvas.
        val expected = SancaiColors.blend(colors.canvas, colors.muted, SancaiControl.DISABLED_ALPHA / 255f)
        val pale = darkest(draw(view))
        assertThat(abs(Color.red(pale) - Color.red(expected))).isAtMost(2)
        assertThat(abs(Color.blue(pale) - Color.blue(expected))).isAtMost(2)
    }

    /** The darkest pixel: the core of a stroke, free of antialiasing. */
    private fun darkest(bitmap: Bitmap): Int {
        var best = Color.WHITE
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val p = bitmap.getPixel(x, y)
            if (Color.red(p) + Color.green(p) + Color.blue(p) < Color.red(best) + Color.green(best) + Color.blue(best)) best = p
        }
        return best
    }

    @Test
    fun `link row is announced as a button with its label`() {
        val view = link(label = "Advanced")
        assertThat(view.contentDescription.toString()).isEqualTo("Advanced")
        assertThat(view.accessibilityClassName.toString()).isEqualTo("android.widget.Button")
        view.label = "Developer"
        assertThat(view.contentDescription.toString()).isEqualTo("Developer")

        // An explicit description wins until it is cleared again.
        view.contentDescription = "Developer options"
        view.label = "More"
        assertThat(view.contentDescription.toString()).isEqualTo("Developer options")
        view.contentDescription = null
        assertThat(view.contentDescription.toString()).isEqualTo("More")
    }

    @Test
    fun `a content description from XML is kept and android enabled is read`() {
        val described = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTitle, "More")
            .addAttribute(android.R.attr.contentDescription, "More privacy settings")
            .build()
        val row = SancaiLinkRow(context, described)
        assertThat(row.label.toString()).isEqualTo("More")
        assertThat(row.contentDescription.toString()).isEqualTo("More privacy settings")
        assertThat(row.isEnabled).isTrue()

        val untitled = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.contentDescription, "More privacy settings")
            .build()
        assertThat(SancaiLinkRow(context, untitled).contentDescription.toString())
            .isEqualTo("More privacy settings")

        val disabled = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTitle, "More")
            .addAttribute(android.R.attr.enabled, "false")
            .build()
        val off = SancaiLinkRow(context, disabled)
        assertThat(off.isEnabled).isFalse()
        var clicks = 0
        off.setOnClickListener { clicks++ }
        Snapshots.attach(off)
        Touch.tap(off, off.width / 2f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `space is handled by the row itself and is not consumed when it cannot click`() {
        // The platform confirms with Space only on newer versions, where it also swallows the
        // key of a disabled view. The row's own handling shows in the second half.
        val view = link()
        var clicks = 0
        view.setOnClickListener { clicks++ }
        Snapshots.attach(view)
        val space = KeyEvent.KEYCODE_SPACE
        assertThat(view.onKeyUp(space, KeyEvent(KeyEvent.ACTION_UP, space))).isFalse()
        assertThat(view.onKeyDown(space, KeyEvent(KeyEvent.ACTION_DOWN, space))).isTrue()
        assertThat(view.isPressed).isTrue()
        assertThat(view.onKeyUp(space, KeyEvent(KeyEvent.ACTION_UP, space))).isTrue()
        assertThat(clicks).isEqualTo(1)
        assertThat(view.isPressed).isFalse()

        view.isEnabled = false
        assertThat(view.onKeyDown(space, KeyEvent(KeyEvent.ACTION_DOWN, space))).isFalse()
        assertThat(view.onKeyUp(space, KeyEvent(KeyEvent.ACTION_UP, space))).isFalse()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `label and chevron share the tone colour`() {
        val view = link(label = "HHHH")
        assertThat(darkest(Snapshots.render(view, 360))).isEqualTo(SancaiTheme.light().colors.muted)
        view.tone = SancaiTextTone.INK
        assertThat(darkest(Snapshots.render(view, 360))).isEqualTo(SancaiTheme.light().colors.ink)
    }

    @Test
    fun `attributes configure both views`() {
        val headingAttrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTitle, "Settings")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiDetail, "Everything in one place")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiHeadingLevel, "page")
            .build()
        val heading = SancaiHeading(context, headingAttrs)
        assertThat(heading.title.toString()).isEqualTo("Settings")
        assertThat(heading.detail.toString()).isEqualTo("Everything in one place")
        assertThat(heading.level).isEqualTo(SancaiHeading.Level.PAGE)

        val linkAttrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTitle, "Back")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiLinkRowArrowPlacement, "adjacent")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiLinkRowArrowDirection, "start")
            .build()
        val row = SancaiLinkRow(context, linkAttrs)
        assertThat(row.label.toString()).isEqualTo("Back")
        assertThat(row.arrowPlacement).isEqualTo(ArrowPlacement.ADJACENT)
        assertThat(row.arrowDirection).isEqualTo(ArrowDirection.START)

        val plain = SancaiLinkRow(context)
        assertThat(plain.arrowPlacement).isEqualTo(ArrowPlacement.EDGE)
        assertThat(plain.arrowDirection).isEqualTo(ArrowDirection.END)
    }

    @Test
    fun `layout survives large text`() {
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = 1.13f))
        val row = SancaiLinkRow(large).also { it.label = "More settings" }
        Snapshots.layout(row, 360)
        assertThat(row.height).isAtLeast(168)
        assertThat(row.label().centerX()).isWithin(0.01f).of(row.width / 2f)
        assertThat(row.label().right).isAtMost(row.chevron().left)
    }

    // ---------------------------------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------------------------------

    @Test
    fun `empty heading takes no room and a lone detail still shows`() {
        val view = SancaiHeading(context)
        Snapshots.layout(view, 300)
        assertThat(view.title).isNull()
        assertThat(view.height).isEqualTo(0)

        view.title = ""
        view.detail = "Only a detail"
        Snapshots.layout(view, 300)
        assertThat(view.getChildAt(0).visibility).isEqualTo(View.GONE)
        assertThat(view.getChildAt(1).visibility).isEqualTo(View.VISIBLE)
        assertThat(view.height).isGreaterThan(0)

        view.title = "Back again"
        assertThat(view.getChildAt(0).visibility).isEqualTo(View.VISIBLE)
        assertThat(androidx.core.view.ViewCompat.isAccessibilityHeading(view.getChildAt(0))).isTrue()
    }

    @Test
    fun `long heading wraps inside the inset and grows with text scale`() {
        val long = "A heading that is far too long to fit on a single line of a narrow page"
        val short = heading("Short", "Detail")
        val view = heading(long, "$long, and its detail is no shorter than the title above it")
        Snapshots.layout(short, 200)
        Snapshots.layout(view, 200)
        val title = view.getChildAt(0) as TextView
        val detail = view.getChildAt(1) as TextView
        assertThat(title.lineCount).isGreaterThan(1)
        assertThat(detail.lineCount).isGreaterThan(1)
        assertThat(view.height).isGreaterThan(short.height)
        assertThat(title.right).isAtMost(view.width - 26)
        assertThat(detail.top - title.bottom).isEqualTo(11)

        val large = SancaiHeading(Snapshots.context(SancaiTheme.light().copy(textScale = 1.13f)))
        large.title = long
        large.detail = "Detail"
        Snapshots.layout(large, 200)
        val largeTitle = large.getChildAt(0) as TextView
        assertThat(largeTitle.textSize / title.textSize).isWithin(0.001f).of(1.13f)
        assertThat(large.height).isEqualTo(large.getChildAt(1).bottom)
        assertThat(largeTitle.lineCount).isAtLeast(title.lineCount)
    }

    @Test
    fun `link row without a label keeps its height and its chevron`() {
        val view = SancaiLinkRow(context)
        val bitmap = Snapshots.render(view, 360)
        assertThat(view.height).isEqualTo(168)
        assertThat(view.contentDescription).isNull()
        assertThat(view.label().width()).isEqualTo(0f)
        assertThat(view.width - view.chevron().right).isWithin(0.01f).of(dp(12f))
        // Only the chevron is drawn.
        assertThat(inkBox(bitmap)!!.left).isAtLeast(view.chevron().left - 1f)

        view.arrowPlacement = ArrowPlacement.ADJACENT
        Snapshots.layout(view, 360)
        assertThat(view.chevron().left).isGreaterThan(view.width / 2f - dp(24f))
        assertThat(view.chevron().right).isLessThan(view.width / 2f + dp(24f))
    }

    @Test
    fun `long label in rtl with large text stays between the chevron slots`() {
        enableRtl()
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = 1.13f))
        val text = "A very long label that cannot possibly fit into a narrow row of text"
        for (placement in ArrowPlacement.values()) {
            val row = SancaiLinkRow(large).also {
                it.label = text
                it.arrowPlacement = placement
                it.layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            Snapshots.layout(row, 240)
            val label = row.label()
            val chevron = row.chevron()
            // END in a right-to-left layout is the left side.
            if (placement == ArrowPlacement.EDGE) {
                assertThat(chevron.left).isWithin(0.01f).of(dp(12f))
            } else {
                // The ellipsized pair is still centred, so it may stop short of the inset.
                assertThat(chevron.left).isAtLeast(dp(12f) - 0.01f)
                assertThat(label.left - chevron.right).isWithin(0.01f).of(dp(5f))
            }
            assertThat(label.left).isAtLeast(chevron.right)
            assertThat(label.right).isAtMost(row.width - dp(12f) + 0.01f)
            assertThat(label.height()).isLessThan(row.height.toFloat())
            if (placement == ArrowPlacement.EDGE) {
                assertThat(label.centerX()).isWithin(0.01f).of(row.width / 2f)
            }
        }
    }

    @Test
    fun `a finger that leaves the row releases the content and does not click`() {
        val view = link()
        var clicks = 0
        view.setOnClickListener { clicks++ }
        Snapshots.attach(view)
        val x = view.width / 2f
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, x, view.height / 2f)
        ShadowLooper.idleMainLooper(SancaiMotion.PRESS_MS + 50, TimeUnit.MILLISECONDS)
        assertThat(view.contentSinkPx).isGreaterThan(0f)

        Touch.dispatch(view, MotionEvent.ACTION_MOVE, x, view.height + 300f)
        assertThat(view.contentSinkPx).isEqualTo(0f)
        Touch.dispatch(view, MotionEvent.ACTION_UP, x, view.height + 300f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `reduced motion sinks at once`() {
        val view = SancaiLinkRow(Snapshots.context(SancaiTheme.light().copy(reduceMotion = true)))
        view.label = "More settings"
        Snapshots.attach(view)
        Touch.dispatch(view, MotionEvent.ACTION_DOWN, view.width / 2f, view.height / 2f)
        assertThat(view.contentSinkPx).isWithin(0.01f).of(dp(SancaiMotion.PRESS_SINK_DP))
        Touch.dispatch(view, MotionEvent.ACTION_CANCEL, 0f, 0f)
        assertThat(view.contentSinkPx).isEqualTo(0f)
    }

    @Test
    fun `tone attribute colours the link`() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTitle, "Delete everything")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTone, "danger")
            .build()
        val row = SancaiLinkRow(context, attrs)
        assertThat(row.tone).isEqualTo(SancaiTextTone.DANGER)
        assertThat(SancaiLinkRow(context).tone).isEqualTo(SancaiTextTone.MUTED)
    }

    // ---------------------------------------------------------------------------------------
    // Snapshots
    // ---------------------------------------------------------------------------------------

    private fun page(theme: SancaiTheme): View {
        val themed = Snapshots.context(theme)
        val d = themed.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val page = LinearLayout(themed)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(theme.colors.canvas)
        page.setPadding(px(14), px(18), px(14), px(8))
        fun add(view: View, top: Int = 0) = page.addView(
            view,
            LinearLayout.LayoutParams(-1, -2).also { it.topMargin = px(top) },
        )
        add(SancaiHeading(themed).also { it.title = "Settings"; it.level = SancaiHeading.Level.PAGE })
        add(SancaiHeading(themed).also { it.title = "Protection"; it.detail = "How firmly the app holds on" }, 22)
        val card = SancaiSurface(themed)
        card.addView(SancaiText.create(themed, "Card content", io.github.avalonskyafar.sancai.theme.SancaiTextRole.SUBHEAD).also {
            it.gravity = android.view.Gravity.CENTER
            it.minHeight = px(62)
        })
        add(card, 10)
        add(SancaiHeading(themed).also { it.title = "About" }, 18)
        add(SancaiLinkRow(themed).also { it.label = "More settings" }, 4)
        add(SancaiLinkRow(themed).also { it.label = "Back to overview"; it.arrowDirection = ArrowDirection.START })
        add(SancaiLinkRow(themed).also { it.label = "Advanced"; it.arrowPlacement = ArrowPlacement.ADJACENT })
        add(SancaiLinkRow(themed).also {
            it.label = "Developer"; it.arrowPlacement = ArrowPlacement.ADJACENT; it.arrowDirection = ArrowDirection.START
        })
        add(SancaiLinkRow(themed).also { it.label = "Unavailable"; it.isEnabled = false })
        return page
    }

    @Test
    fun `snapshot of headings and link rows`() {
        val light = Snapshots.capture(page(SancaiTheme.light()), "headinglink_light", 360)
        val dark = Snapshots.capture(page(SancaiTheme.dark()), "headinglink_dark", 360)
        assertThat(light.getPixel(2, 2)).isEqualTo(SancaiTheme.light().colors.canvas)
        assertThat(dark.getPixel(2, 2)).isEqualTo(SancaiTheme.dark().colors.canvas)
    }
}
