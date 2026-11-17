package io.github.avalonskyafar.sancai.widget

import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconView
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiEntryRowTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val density = context.resources.displayMetrics.density

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private fun row(
        title: String = "Language",
        configure: SancaiEntryRow.() -> Unit = {},
    ): SancaiEntryRow = SancaiEntryRow(context).apply {
        this.title = title
        configure()
    }

    // Children of the sinking content, in the order the row adds them.
    private fun content(row: SancaiEntryRow): ViewGroup = row.getChildAt(0) as ViewGroup
    private fun titleOf(row: SancaiEntryRow): TextView = content(row).getChildAt(0) as TextView
    private fun detailOf(row: SancaiEntryRow): TextView = content(row).getChildAt(1) as TextView
    private fun valueOf(row: SancaiEntryRow): TextView = content(row).getChildAt(2) as TextView
    private fun iconOf(row: SancaiEntryRow): SancaiIconView = content(row).getChildAt(3) as SancaiIconView
    private fun chevronOf(row: SancaiEntryRow): SancaiIconView = content(row).getChildAt(4) as SancaiIconView

    private fun centreX(view: View): Float = (view.left + view.right) / 2f

    @Test
    fun `title stays on the centre line whatever sits at the edges`() {
        val variants: List<SancaiEntryRow.() -> Unit> = listOf(
            {},
            { arrow = SancaiEntryRow.Arrow.END },
            { arrow = SancaiEntryRow.Arrow.START },
            { leadingIcon = SancaiIcon.INFO },
            { value = "English" },
            { arrow = SancaiEntryRow.Arrow.END; value = "English"; leadingIcon = SancaiIcon.INFO },
            { arrow = SancaiEntryRow.Arrow.START; leadingIcon = SancaiIcon.INFO; detail = "Used for menus" },
            { arrow = SancaiEntryRow.Arrow.END; title = "A title that is long enough to wrap onto a second line of the row" },
        )
        for (variant in variants) {
            val row = row(configure = variant)
            Snapshots.layout(row, 300)
            assertThat(centreX(titleOf(row))).isWithin(0.5f).of(row.width / 2f)
            if (!row.detail.isNullOrBlank()) assertThat(centreX(detailOf(row))).isWithin(0.5f).of(row.width / 2f)
            // The glyphs of a centred single line are centred in the view as well.
            val layout = titleOf(row).layout
            if (layout.lineCount == 1) {
                val ink = (layout.getLineLeft(0) + layout.getLineRight(0)) / 2f
                assertThat(titleOf(row).left + ink).isWithin(1f).of(row.width / 2f)
            }
        }
    }

    @Test
    fun `row is 62 dp high and 76 dp with a detail line`() {
        val plain = row()
        Snapshots.layout(plain, 300)
        assertThat(plain.height).isEqualTo(px(62f))

        val detailed = row { detail = "Used for menus and messages" }
        Snapshots.layout(detailed, 300)
        assertThat(detailed.height).isEqualTo(px(76f))
        assertThat(detailOf(detailed).top - titleOf(detailed).bottom).isEqualTo(px(4f))
        // The pair is centred as a group.
        val above = titleOf(detailed).top
        val below = detailed.height - detailOf(detailed).bottom
        assertThat(above - below).isAtMost(1)
        assertThat(below - above).isAtMost(1)

        detailed.detail = "  "
        Snapshots.layout(detailed, 300)
        assertThat(detailed.height).isEqualTo(px(62f))
    }

    @Test
    fun `row grows with large text instead of clipping it`() {
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = SancaiTheme.LARGE_TEXT_SCALE))
        val row = SancaiEntryRow(large).apply {
            title = "A title that is long enough to wrap onto a second and a third line of the row"
            detail = "And a detail that is long enough to wrap onto a second line as well, easily"
            arrow = SancaiEntryRow.Arrow.END
            value = "Value"
        }
        Snapshots.layout(row, 240)
        assertThat(row.height).isGreaterThan(px(76f))
        assertThat(titleOf(row).top).isAtLeast(px(10f))
        assertThat(detailOf(row).bottom).isAtMost(row.height - px(10f))
        assertThat(titleOf(row).right).isAtMost(valueOf(row).left)
    }

    @Test
    fun `click fires once and ticks`() {
        val row = row { arrow = SancaiEntryRow.Arrow.END }
        var clicks = 0
        var clicked: View? = null
        row.setOnClickListener { clicks++; clicked = it }
        Snapshots.attach(row, 300)
        Touch.tap(row, row.width / 2f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(1)
        assertThat(clicked).isSameInstanceAs(row)
        assertThat(Shadows.shadowOf(row).lastHapticFeedbackPerformed()).isEqualTo(HapticFeedbackConstants.CLOCK_TICK)
        assertThat(content(row).translationY).isEqualTo(0f)

        row.setOnClickListener(null)
        Touch.tap(row, row.width / 2f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `content sinks on press while the row stays still`() {
        val row = row { arrow = SancaiEntryRow.Arrow.END; detail = "Detail" }
        Snapshots.attach(row, 300)
        val top = row.top
        Touch.dispatch(row, MotionEvent.ACTION_DOWN, row.width / 2f, row.height / 2f)
        ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS)
        assertThat(content(row).translationY).isWithin(0.01f).of(1.5f * density)
        assertThat(row.translationY).isEqualTo(0f)
        assertThat(row.top).isEqualTo(top)
        // Text and chevron live in the content, so they sink together.
        assertThat(titleOf(row).parent).isSameInstanceAs(content(row))
        assertThat(chevronOf(row).parent).isSameInstanceAs(content(row))
        Touch.dispatch(row, MotionEvent.ACTION_CANCEL, row.width / 2f, row.height / 2f)
        assertThat(content(row).translationY).isEqualTo(0f)
    }

    @Test
    fun `chevron is 24 dp, 12 dp from its edge and vertically centred`() {
        val end = row { arrow = SancaiEntryRow.Arrow.END; detail = "Detail" }
        Snapshots.layout(end, 300)
        val chevron = chevronOf(end)
        assertThat(chevron.width).isEqualTo(px(24f))
        assertThat(chevron.height).isEqualTo(px(24f))
        assertThat(end.width - chevron.right).isEqualTo(px(12f))
        assertThat((chevron.top + chevron.bottom) / 2f).isWithin(0.5f).of(end.height / 2f)
        assertThat(chevron.icon).isEqualTo(SancaiIcon.CHEVRON_RIGHT)
        assertThat(chevron.tone).isEqualTo(SancaiTextTone.MUTED)

        val start = row { arrow = SancaiEntryRow.Arrow.START }
        Snapshots.layout(start, 300)
        assertThat(chevronOf(start).left).isEqualTo(px(12f))
        assertThat(chevronOf(start).icon).isEqualTo(SancaiIcon.CHEVRON_LEFT)
        assertThat(chevronOf(start).tone).isEqualTo(SancaiTextTone.INK)

        val none = row()
        Snapshots.layout(none, 300)
        assertThat(chevronOf(none).visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `RTL swaps the chevron side and keeps the title centred`() {
        val app = context.applicationContext
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val row = row { arrow = SancaiEntryRow.Arrow.END; leadingIcon = SancaiIcon.INFO; value = "English" }
        row.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(row, 300)
        assertThat(chevronOf(row).left).isEqualTo(px(12f))
        assertThat(row.width - iconOf(row).right).isEqualTo(px(12f))
        assertThat(valueOf(row).left).isEqualTo(chevronOf(row).right + px(2f))
        assertThat(centreX(titleOf(row))).isWithin(0.5f).of(row.width / 2f)
        // The chevron glyph turns around with the layout.
        assertThat(chevronOf(row).layoutDirection).isEqualTo(View.LAYOUT_DIRECTION_RTL)
        assertThat(SancaiIcon.CHEVRON_RIGHT.isDirectional).isTrue()

        val start = row { arrow = SancaiEntryRow.Arrow.START; alignment = SancaiEntryRow.Alignment.START }
        start.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(start, 300)
        assertThat(start.width - chevronOf(start).right).isEqualTo(px(12f))
        assertThat(titleOf(start).right).isEqualTo(chevronOf(start).left - px(8f))
        val layout = titleOf(start).layout
        assertThat(layout.getLineRight(0)).isWithin(1f).of(titleOf(start).width.toFloat())
    }

    @Test
    fun `leading icon and value sit at the edges on their own`() {
        val row = row { leadingIcon = SancaiIcon.INFO; value = "English"; arrow = SancaiEntryRow.Arrow.END }
        Snapshots.layout(row, 300)
        assertThat(iconOf(row).left).isEqualTo(px(12f))
        assertThat(iconOf(row).width).isEqualTo(px(24f))
        assertThat(iconOf(row).tone).isEqualTo(SancaiTextTone.MUTED)
        assertThat(valueOf(row).right).isEqualTo(chevronOf(row).left - px(2f))
        assertThat((valueOf(row).top + valueOf(row).bottom) / 2f).isWithin(0.5f).of(row.height / 2f)
        assertThat(valueOf(row).currentTextColor).isEqualTo(SancaiTheme.light().colors.muted)

        // Without a chevron the value keeps the text padding from the edge.
        val bare = row { value = "English" }
        Snapshots.layout(bare, 300)
        assertThat(bare.width - valueOf(bare).right).isEqualTo(px(14f))

        // Icon after a start chevron.
        val both = row { leadingIcon = SancaiIcon.INFO; arrow = SancaiEntryRow.Arrow.START }
        Snapshots.layout(both, 300)
        assertThat(iconOf(both).left).isEqualTo(chevronOf(both).right + px(6f))

        // A long value is cut; it never reaches the title.
        val long = row { value = "A value that is far too long for the end of a row"; arrow = SancaiEntryRow.Arrow.END }
        Snapshots.layout(long, 300)
        assertThat(valueOf(long).width).isAtMost((long.width * 0.28f).toInt())
        assertThat(titleOf(long).right).isAtMost(valueOf(long).left)
    }

    @Test
    fun `start alignment puts the text 14 dp from the start`() {
        val row = row { alignment = SancaiEntryRow.Alignment.START; detail = "Detail"; arrow = SancaiEntryRow.Arrow.END }
        Snapshots.layout(row, 300)
        assertThat(titleOf(row).left).isEqualTo(px(14f))
        assertThat(detailOf(row).left).isEqualTo(px(14f))
        assertThat(titleOf(row).layout.getLineLeft(0)).isEqualTo(0f)
        assertThat(titleOf(row).right).isEqualTo(chevronOf(row).left - px(8f))

        // Icon and value take their width from the text here; nothing overlaps.
        val full = row {
            alignment = SancaiEntryRow.Alignment.START
            leadingIcon = SancaiIcon.INFO
            value = "English"
            arrow = SancaiEntryRow.Arrow.END
        }
        Snapshots.layout(full, 300)
        assertThat(titleOf(full).left).isEqualTo(iconOf(full).right + px(8f))
        assertThat(titleOf(full).right).isEqualTo(valueOf(full).left - px(8f))
    }

    @Test
    fun `keyboard confirms and a disabled row pales and ignores input`() {
        val row = row { arrow = SancaiEntryRow.Arrow.END }
        var clicks = 0
        row.setOnClickListener { clicks++ }
        Snapshots.attach(row, 300)
        assertThat(row.isFocusable).isTrue()
        for (code in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER)) {
            // The row has no focus in a touch-mode test window, so the keys are handed over directly.
            assertThat(row.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))).isTrue()
            row.onKeyUp(code, KeyEvent(KeyEvent.ACTION_UP, code))
            Snapshots.idle()
        }
        assertThat(clicks).isEqualTo(3)
        assertThat(content(row).translationY).isEqualTo(0f)
        // A key that cannot do anything is not consumed.
        assertThat(row.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))).isFalse()

        row.isEnabled = false
        val height = row.height
        assertThat(content(row).alpha).isWithin(0.001f).of(107 / 255f)
        Touch.dispatch(row, MotionEvent.ACTION_DOWN, row.width / 2f, row.height / 2f)
        ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS)
        assertThat(content(row).translationY).isEqualTo(0f)
        Touch.dispatch(row, MotionEvent.ACTION_UP, row.width / 2f, row.height / 2f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(3)
        assertThat(row.height).isEqualTo(height)
        row.isEnabled = true
        assertThat(content(row).alpha).isEqualTo(1f)
    }

    @Test
    fun `row is announced as one button with title value and detail`() {
        val row = row { detail = "Used for menus"; value = "English" }
        assertThat(row.contentDescription.toString()).isEqualTo("Language, English, Used for menus")
        assertThat(row.accessibilityClassName.toString()).isEqualTo("android.widget.Button")
        assertThat(content(row).importantForAccessibility)
            .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        row.detail = null
        row.value = null
        assertThat(row.contentDescription.toString()).isEqualTo("Language")

        // An explicit description wins until it is cleared again.
        row.contentDescription = "Change the language"
        row.detail = "Other"
        assertThat(row.contentDescription.toString()).isEqualTo("Change the language")
        row.contentDescription = null
        assertThat(row.contentDescription.toString()).isEqualTo("Language, Other")

        val separator = context.getString(R.string.sancai_entry_row_description_separator)
        assertThat(separator).isEqualTo(", ")
    }

    @Test
    fun `XML attributes configure the row`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.sancaiTitle, "Language")
            .addAttribute(R.attr.sancaiDetail, "Used for menus")
            .addAttribute(R.attr.sancaiEntryValue, "English")
            .addAttribute(R.attr.sancaiIcon, "info")
            .addAttribute(R.attr.sancaiEntryArrow, "start")
            .addAttribute(R.attr.sancaiEntryAlignment, "start")
            .build()
        val row = SancaiEntryRow(context, attrs)
        assertThat(row.title.toString()).isEqualTo("Language")
        assertThat(row.detail.toString()).isEqualTo("Used for menus")
        assertThat(row.value.toString()).isEqualTo("English")
        assertThat(row.leadingIcon).isEqualTo(SancaiIcon.INFO)
        assertThat(row.arrow).isEqualTo(SancaiEntryRow.Arrow.START)
        assertThat(row.alignment).isEqualTo(SancaiEntryRow.Alignment.START)

        val plain = SancaiEntryRow(context, Robolectric.buildAttributeSet().build())
        assertThat(plain.arrow).isEqualTo(SancaiEntryRow.Arrow.NONE)
        assertThat(plain.alignment).isEqualTo(SancaiEntryRow.Alignment.CENTER)
        assertThat(plain.leadingIcon).isNull()
        assertThat(plain.contentDescription).isNull()
    }

    @Test
    fun `XML onClick set by the platform constructor is honoured`() {
        val declared = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.onClick, "openLanguage")
            .build()
        val row = SancaiEntryRow(context, declared)
        // The declared handler survives construction: the click reaches it, and it fails
        // only because this test context has no such method.
        val failure = runCatching { row.performClick() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
        assertThat(failure!!.message).contains("openLanguage")
    }

    @Test
    fun `listener runs before the tick and setters never call it`() {
        val row = row()
        var hapticSeenByListener: Int? = null
        var clicks = 0
        row.setOnClickListener {
            clicks++
            hapticSeenByListener = Shadows.shadowOf(row).lastHapticFeedbackPerformed()
        }
        Snapshots.attach(row, 300)
        row.title = "Other"
        row.detail = "Detail"
        row.value = "Value"
        row.arrow = SancaiEntryRow.Arrow.END
        row.alignment = SancaiEntryRow.Alignment.START
        row.leadingIcon = SancaiIcon.INFO
        Snapshots.idle()
        assertThat(clicks).isEqualTo(0)

        row.performClick()
        assertThat(clicks).isEqualTo(1)
        assertThat(hapticSeenByListener).isNotEqualTo(HapticFeedbackConstants.CLOCK_TICK)
        assertThat(Shadows.shadowOf(row).lastHapticFeedbackPerformed()).isEqualTo(HapticFeedbackConstants.CLOCK_TICK)
    }

    @Test
    fun `empty and extreme data keep the row whole`() {
        val empty = SancaiEntryRow(context)
        Snapshots.layout(empty, 300)
        assertThat(empty.height).isEqualTo(px(62f))
        assertThat(empty.title).isNull()
        assertThat(empty.contentDescription).isNull()
        empty.title = ""
        empty.detail = ""
        empty.value = ""
        Snapshots.layout(empty, 300)
        assertThat(empty.height).isEqualTo(px(62f))
        assertThat(detailOf(empty).visibility).isEqualTo(View.GONE)
        assertThat(valueOf(empty).visibility).isEqualTo(View.GONE)
        assertThat(empty.contentDescription).isNull()

        // Everything at once in a row far too narrow: nothing overlaps, nothing has negative size.
        val narrow = row("A long title that cannot possibly fit here") {
            detail = "A long detail that cannot fit either"
            value = "A long value"
            leadingIcon = SancaiIcon.INFO
            arrow = SancaiEntryRow.Arrow.END
        }
        Snapshots.layout(narrow, 200)
        // Too crowded to mirror the edges: the text uses the room between them instead of vanishing.
        assertThat(titleOf(narrow).width).isAtLeast(px(40f))
        assertThat(titleOf(narrow).left).isEqualTo(iconOf(narrow).right + px(8f))
        assertThat(titleOf(narrow).left).isAtLeast(iconOf(narrow).right)
        assertThat(titleOf(narrow).right).isAtMost(valueOf(narrow).left)
        assertThat(valueOf(narrow).right).isAtMost(chevronOf(narrow).left)
        assertThat(titleOf(narrow).top).isAtLeast(0)
        assertThat(detailOf(narrow).bottom).isAtMost(narrow.height)
        Snapshots.render(narrow, 200)
        // Narrower than the edge items themselves: still lays out and draws.
        Snapshots.render(narrow, 90)
    }

    @Test
    fun `type and colours follow the theme and the row paints no background`() {
        for (theme in listOf(SancaiTheme.light(), SancaiTheme.dark())) {
            val themed = Snapshots.context(theme)
            val row = SancaiEntryRow(themed).apply {
                title = "Language"
                detail = "Detail"
                arrow = SancaiEntryRow.Arrow.END
            }
            val colors = theme.colors
            assertThat(titleOf(row).currentTextColor).isEqualTo(colors.ink)
            assertThat(detailOf(row).currentTextColor).isEqualTo(colors.muted)
            val scaled = themed.resources.displayMetrics.scaledDensity
            assertThat(titleOf(row).textSize).isWithin(0.01f).of(15.5f * scaled)
            assertThat(detailOf(row).textSize).isWithin(0.01f).of(11.8f * scaled)
            assertThat(titleOf(row).typeface).isEqualTo(SancaiTextRole.SUBHEAD.weight.typeface())

            val bitmap = Snapshots.render(row, 300)
            // Nothing but the page shows through next to the text: no fill, no tint.
            assertThat(bitmap.getPixel(px(40f), px(8f))).isEqualTo(colors.canvas)
            assertThat(bitmap.getPixel(bitmap.width - 2, bitmap.height - 2)).isEqualTo(colors.canvas)
            // The chevron is stroked in the muted colour.
            val chevron = chevronOf(row)
            val y = (chevron.top + chevron.bottom) / 2
            val stroke = (chevron.left until chevron.right).map { bitmap.getPixel(it, y) }
            assertThat(stroke).contains(colors.muted)
            assertThat(stroke).doesNotContain(colors.ink)

            row.arrow = SancaiEntryRow.Arrow.START
            val start = Snapshots.render(row, 300)
            val inked = (chevronOf(row).left until chevronOf(row).right).map { start.getPixel(it, y) }
            assertThat(inked).contains(colors.ink)
        }
    }

    @Test
    fun `snapshot of entry rows`() {
        for ((name, theme) in listOf("light" to SancaiTheme.light(), "dark" to SancaiTheme.dark())) {
            val themed = Snapshots.context(theme)
            fun dp(value: Int) = (value * themed.resources.displayMetrics.density).toInt()
            val page = LinearLayout(themed).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(22), dp(12), dp(30))
                clipChildren = false
            }
            fun params(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
            fun heading(text: String, top: Int) = page.addView(
                SancaiText.create(themed, text, SancaiTextRole.HEADING).apply { setPadding(dp(10), 0, dp(10), 0) },
                params(top),
            )
            fun entry(title: String, configure: SancaiEntryRow.() -> Unit = {}) =
                SancaiEntryRow(themed).apply { this.title = title; configure() }
            fun card(vararg rows: SancaiEntryRow) {
                val card = SancaiSurface(themed)
                rows.forEachIndexed { index, row ->
                    if (index > 0) card.addDivider()
                    card.addView(row, params())
                }
                page.addView(card, params(10))
            }

            heading("Centred", 0)
            card(
                entry("Rules") { detail = "Words and pages that are held back"; arrow = SancaiEntryRow.Arrow.END },
                entry("Language") { value = "English"; arrow = SancaiEntryRow.Arrow.END },
                entry("Backup") {
                    leadingIcon = SancaiIcon.INBOX
                    detail = "Last saved yesterday"
                    arrow = SancaiEntryRow.Arrow.END
                },
                entry("Back to overview") { arrow = SancaiEntryRow.Arrow.START },
                entry("About"),
                entry("Unavailable") { detail = "Sign in to use this"; arrow = SancaiEntryRow.Arrow.END; isEnabled = false },
            )
            heading("Start aligned", 20)
            card(
                entry("Notification sound") {
                    alignment = SancaiEntryRow.Alignment.START
                    value = "Porcelain"
                    arrow = SancaiEntryRow.Arrow.END
                },
                entry("Storage") {
                    alignment = SancaiEntryRow.Alignment.START
                    leadingIcon = SancaiIcon.INFO
                    detail = "A longer explanation that runs on and wraps onto a second line of the row"
                    arrow = SancaiEntryRow.Arrow.END
                },
                entry("Plain item") { alignment = SancaiEntryRow.Alignment.START },
            )
            val bitmap = Snapshots.capture(page, "entry_$name", 360)
            assertThat(bitmap.width).isGreaterThan(0)
        }
    }
}
