package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconView
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiBannerTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val density = context.resources.displayMetrics.density

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private fun banner(
        tone: SancaiBanner.Tone = SancaiBanner.Tone.INFO,
        title: String? = "Offline",
        detail: String? = "Changes are kept on this device.",
        ctx: Context = context,
    ): SancaiBanner = SancaiBanner(ctx).also {
        it.tone = tone
        it.title = title
        it.detail = detail
    }

    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, 16L)
            ShadowLooper.idleMainLooper(step, TimeUnit.MILLISECONDS)
            left -= step
        }
    }

    /** Frames only run when the test advances the clock, so a glide can be seen half-way. */
    private fun attachPaused(view: View) {
        Snapshots.attach(view, 320)
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16))
    }

    private fun enableRtl() {
        val app = context.applicationContext
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
    }

    private fun iconOf(banner: SancaiBanner): SancaiIconView {
        val row = banner.getChildAt(0) as ViewGroup
        return row.getChildAt(0) as SancaiIconView
    }

    private fun closeOf(banner: SancaiBanner): View {
        val row = banner.getChildAt(0) as ViewGroup
        return row.getChildAt(row.childCount - 1)
    }

    @Test
    fun `each tone paints the bar and the icon in its colour`() {
        val colors = SancaiTheme.of(context).colors
        val expected = mapOf(
            SancaiBanner.Tone.INFO to (colors.accent to SancaiIcon.INFO),
            SancaiBanner.Tone.WARNING to (colors.warning to SancaiIcon.WARNING),
            SancaiBanner.Tone.DANGER to (colors.danger to SancaiIcon.ERROR),
            SancaiBanner.Tone.SUCCESS to (colors.success to SancaiIcon.SUCCESS),
        )
        for ((tone, pair) in expected) {
            val view = banner(tone)
            val bitmap = Snapshots.render(view, 320)
            assertThat(view.toneColor).isEqualTo(pair.first)
            assertThat(bitmap.getPixel(px(1.5f), view.height / 2)).isEqualTo(pair.first)
            assertThat(iconOf(view).icon).isEqualTo(pair.second)
            assertThat(iconOf(view).color).isEqualTo(pair.first)
        }
    }
    @Test
    fun `the bar moves to the right edge in right-to-left layouts`() {
        enableRtl()
        val view = banner(SancaiBanner.Tone.DANGER)
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val bitmap = Snapshots.render(view, 320)
        val y = view.height / 2
        assertThat(bitmap.getPixel(view.width - px(1.5f), y)).isEqualTo(view.toneColor)
        assertThat(bitmap.getPixel(px(1.5f), y)).isNotEqualTo(view.toneColor)
        assertThat(iconOf(view).left).isGreaterThan(view.width / 2)
    }

    @Test
    fun `the body is flat porcelain that fills the view`() {
        val view = banner()
        val bitmap = Snapshots.render(view, 320)
        val surface = SancaiTheme.of(context).colors.surface
        // No shadow gutter: the body colour reaches to just inside the bottom rim.
        val inside = bitmap.getPixel(view.width / 2, view.height - px(3f))
        assertThat(android.graphics.Color.alpha(inside)).isEqualTo(255)
        assertThat(colorDistance(inside, surface)).isLessThan(24)
        // The rounded corner leaves the very corner pixel to the page behind.
        assertThat(bitmap.getPixel(view.width - 1, 0)).isEqualTo(SancaiTheme.of(context).colors.canvas)
    }

    private fun colorDistance(a: Int, b: Int): Int =
        kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) +
            kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) +
            kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b))

    @Test
    fun `empty parts take no room`() {
        val full = banner()
        Snapshots.layout(full, 320)
        val titleOnly = banner(detail = null)
        Snapshots.layout(titleOnly, 320)
        assertThat(titleOnly.detailView.visibility).isEqualTo(View.GONE)
        assertThat(titleOnly.height).isLessThan(full.height)
        assertThat(titleOnly.actionButton.visibility).isEqualTo(View.GONE)
        assertThat(closeOf(titleOnly).visibility).isEqualTo(View.GONE)

        val noIcon = banner()
        noIcon.showIcon = false
        Snapshots.layout(noIcon, 320)
        assertThat(iconOf(noIcon).visibility).isEqualTo(View.GONE)
        assertThat(noIcon.titleView.width).isGreaterThan(full.titleView.width)
    }

    @Test
    fun `the icon is centred on the first line of the title`() {
        val view = banner()
        Snapshots.layout(view, 320)
        val icon = iconOf(view)
        val column = view.titleView.parent as View
        val line = view.titleView.layout
        val middle = column.top + view.titleView.top + (line.getLineTop(0) + line.getLineBottom(0)) / 2
        assertThat((icon.top + icon.bottom) / 2).isWithin(1).of(middle)
        assertThat(icon.width).isEqualTo(px(20f))
    }

    @Test
    fun `actions sit at the end and tick then call back`() {
        val view = banner(SancaiBanner.Tone.WARNING)
        var primary = 0
        var secondary = 0
        view.setAction("Retry") { primary++ }
        view.setSecondaryAction("Details") { secondary++ }
        Snapshots.attach(view, 320)
        val first = view.secondaryActionButton
        val last = view.actionButton
        assertThat(first.visibility).isEqualTo(View.VISIBLE)
        assertThat(first.right).isAtMost(last.left)
        assertThat(last.height).isAtLeast(px(44f))
        // The label of the last action ends on the text column's end edge.
        val column = view.titleView.parent as View
        val actions = last.parent as View
        assertThat(actions.left + last.right - last.paddingRight).isEqualTo(column.width)

        last.performClick()
        first.performClick()
        Snapshots.idle()
        assertThat(primary).isEqualTo(1)
        assertThat(secondary).isEqualTo(1)

        view.setAction(null, null)
        view.setSecondaryAction(null, null)
        Snapshots.idle()
        assertThat(actions.visibility).isEqualTo(View.GONE)
    }
    @Test
    fun `dismiss shrinks the height to zero and the view becomes gone`() {
        val view = banner()
        attachPaused(view)
        val full = view.height
        assertThat(full).isGreaterThan(0)
        assertThat(view.isShowing).isTrue()

        view.dismiss()
        assertThat(view.isShowing).isFalse()
        advance(60)
        assertThat(view.visibility).isEqualTo(View.VISIBLE)
        assertThat(view.height).isGreaterThan(0)
        assertThat(view.height).isLessThan(full)
        advance(400)
        assertThat(view.height).isEqualTo(0)
        assertThat(view.visibility).isEqualTo(View.GONE)

        view.show()
        assertThat(view.visibility).isEqualTo(View.VISIBLE)
        assertThat(view.isShowing).isTrue()
        advance(60)
        assertThat(view.height).isLessThan(full)
        advance(400)
        assertThat(view.height).isEqualTo(full)
    }

    @Test
    fun `a banner pushes the content below it`() {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        val view = banner()
        val below = View(context)
        column.addView(view, LinearLayout.LayoutParams(-1, -2))
        column.addView(below, LinearLayout.LayoutParams(-1, px(40f)))
        Snapshots.attach(column, 320)
        val pushed = below.top
        assertThat(pushed).isEqualTo(view.height)
        view.dismiss()
        advance(500)
        assertThat(below.top).isEqualTo(0)
    }

    @Test
    fun `the gap around a banner closes and opens with it`() {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        val view = banner()
        val below = View(context)
        val params = LinearLayout.LayoutParams(-1, -2)
        params.topMargin = px(10f)
        params.bottomMargin = px(12f)
        column.addView(view, params)
        column.addView(below, LinearLayout.LayoutParams(-1, px(40f)))
        attachPaused(column)
        val full = view.height
        assertThat(below.top).isEqualTo(px(10f) + full + px(12f))

        view.dismiss()
        advance(60)
        // Half-way: the margins have shrunk with the height, nothing has jumped.
        assertThat(view.height).isLessThan(full)
        assertThat(params.topMargin).isLessThan(px(10f))
        assertThat(params.topMargin).isGreaterThan(0)
        assertThat(below.top).isEqualTo(params.topMargin + view.height + params.bottomMargin)
        // The glide lands softly: the last frame before the banner is gone leaves no gap to
        // close in one step. Without the margins that step would be 22 dp.
        var last = below.top
        var frames = 0
        while (view.visibility != View.GONE && frames < 60) {
            last = below.top
            advance(16)
            frames++
        }
        assertThat(view.visibility).isEqualTo(View.GONE)
        assertThat(last).isLessThan(px(6f))
        assertThat(below.top).isEqualTo(0)
        // At rest the margins are the ones the banner was given.
        assertThat(params.topMargin).isEqualTo(px(10f))
        assertThat(params.bottomMargin).isEqualTo(px(12f))

        view.show()
        advance(16)
        // The first frames of the way back carry no full gap either.
        assertThat(below.top).isLessThan(px(10f) + px(12f))
        advance(400)
        assertThat(view.height).isEqualTo(full)
        assertThat(params.topMargin).isEqualTo(px(10f))
        assertThat(below.top).isEqualTo(px(10f) + full + px(12f))
    }

    @Test
    fun `without animation and when not attached the change is immediate`() {
        val attached = banner()
        Snapshots.attach(attached, 320)
        attached.dismiss(animate = false)
        assertThat(attached.visibility).isEqualTo(View.GONE)
        attached.show(animate = false)
        Snapshots.idle()
        assertThat(attached.height).isGreaterThan(0)

        val loose = banner()
        Snapshots.layout(loose, 320)
        loose.dismiss()
        assertThat(loose.visibility).isEqualTo(View.GONE)
        assertThat(loose.isShowing).isFalse()
    }

    @Test
    fun `the close icon dismisses and tells the listener but dismiss from code does not`() {
        val view = banner()
        view.isDismissible = true
        var closed = 0
        view.setOnDismissListener { closed++ }
        Snapshots.attach(view, 320)
        val close = closeOf(view)
        assertThat(close.visibility).isEqualTo(View.VISIBLE)
        assertThat(close.width).isAtLeast(px(44f))
        assertThat(close.height).isAtLeast(px(44f))
        assertThat(close.right).isEqualTo(view.width)
        assertThat(close.contentDescription.toString()).isEqualTo(context.getString(R.string.sancai_action_close))

        close.performClick()
        Snapshots.idle()
        assertThat(closed).isEqualTo(1)
        assertThat(view.isShowing).isFalse()
        advance(500)
        assertThat(view.visibility).isEqualTo(View.GONE)

        view.show(animate = false)
        view.dismiss()
        advance(500)
        assertThat(closed).isEqualTo(1)
    }

    @Test
    fun `a tap on the close icon closes the banner`() {
        val view = banner()
        view.isDismissible = true
        Snapshots.attach(view, 320)
        Touch.tap(view, view.width - px(22f).toFloat(), px(22f).toFloat())
        Snapshots.idle()
        assertThat(view.isShowing).isFalse()
    }
    @Test
    fun `a disabled banner pales and stops responding`() {
        val view = banner()
        view.isDismissible = true
        var clicks = 0
        view.setAction("Retry") { clicks++ }
        view.isEnabled = false
        Snapshots.attach(view, 320)
        assertThat(view.actionButton.isEnabled).isFalse()
        assertThat(closeOf(view).isEnabled).isFalse()
        assertThat(view.titleView.alpha).isWithin(0.01f).of(107 / 255f)
        Touch.tap(view, view.width - px(22f).toFloat(), px(22f).toFloat())
        Snapshots.idle()
        assertThat(view.isShowing).isTrue()
        // The bar is drawn through the same fade as the body.
        val bitmap = Snapshots.render(view, 320)
        assertThat(bitmap.getPixel(px(1.5f), view.height / 2)).isNotEqualTo(view.toneColor)
        view.isEnabled = true
        assertThat(view.actionButton.isEnabled).isTrue()
        assertThat(view.titleView.alpha).isEqualTo(1f)
    }

    @Test
    fun `whether it is showing survives save and restore without calling back`() {
        val id = View.generateViewId()
        val view = banner()
        view.id = id
        Snapshots.layout(view, 320)
        view.dismiss(animate = false)
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)

        val restored = banner()
        restored.id = id
        var closed = 0
        restored.setOnDismissListener { closed++ }
        restored.restoreHierarchyState(saved)
        assertThat(restored.isShowing).isFalse()
        assertThat(restored.visibility).isEqualTo(View.GONE)
        assertThat(closed).isEqualTo(0)

        view.show(animate = false)
        view.saveHierarchyState(saved)
        restored.restoreHierarchyState(saved)
        assertThat(restored.isShowing).isTrue()
        assertThat(restored.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `a screen reader hears the tone with the title`() {
        val view = banner(SancaiBanner.Tone.WARNING, title = "Service is off")
        val tone = context.getString(R.string.sancai_banner_tone_warning)
        assertThat(view.titleView.contentDescription.toString()).isEqualTo("$tone, Service is off")
        assertThat(view.accessibilityClassName.toString()).isEqualTo(SancaiBanner::class.java.name)
        assertThat(view.accessibilityLiveRegion).isEqualTo(View.ACCESSIBILITY_LIVE_REGION_POLITE)
        view.toneDescription = ""
        assertThat(view.titleView.contentDescription).isNull()
        view.toneDescription = "Heads up"
        view.title = null
        assertThat(view.detailView.contentDescription.toString()).startsWith("Heads up, ")
        view.closeDescription = "Hide"
        assertThat(closeOf(view).contentDescription.toString()).isEqualTo("Hide")
        assertThat(closeOf(view).isFocusable).isTrue()
    }

    @Test
    fun `attributes from a layout are applied`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.sancaiTitle, "Backup overdue")
            .addAttribute(R.attr.sancaiDetail, "Last backup was nine days ago.")
            .addAttribute(R.attr.sancaiBannerTone, "danger")
            .addAttribute(R.attr.sancaiBannerAction, "Back up")
            .addAttribute(R.attr.sancaiBannerSecondaryAction, "Later")
            .addAttribute(R.attr.sancaiBannerDismissible, "true")
            .addAttribute(R.attr.sancaiBannerShowIcon, "false")
            .addAttribute(R.attr.sancaiBannerShown, "false")
            .build()
        val view = SancaiBanner(context, attrs)
        assertThat(view.title.toString()).isEqualTo("Backup overdue")
        assertThat(view.detail.toString()).isEqualTo("Last backup was nine days ago.")
        assertThat(view.tone).isEqualTo(SancaiBanner.Tone.DANGER)
        assertThat(view.actionButton.text.toString()).isEqualTo("Back up")
        assertThat(view.secondaryActionButton.text.toString()).isEqualTo("Later")
        assertThat(view.isDismissible).isTrue()
        assertThat(view.showIcon).isFalse()
        assertThat(view.isShowing).isFalse()
        assertThat(view.visibility).isEqualTo(View.GONE)
    }
    @Test
    fun `large text keeps every part inside the body`() {
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = 1.13f))
        val view = banner(
            detail = "A long explanation that has to wrap onto a second line at this width.",
            ctx = large,
        )
        view.isDismissible = true
        view.setAction("Open settings", null)
        view.setSecondaryAction("Not now", null)
        Snapshots.layout(view, 280)
        val actions = view.actionButton.parent as View
        val column = actions.parent as View
        assertThat(column.top + actions.bottom).isAtMost(view.height)
        assertThat(view.detailView.lineCount).isGreaterThan(1)
    }

    @Test
    fun `actions that do not fit side by side stack inside the body`() {
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = 1.13f))
        val view = banner(ctx = large)
        view.isDismissible = true
        view.setAction("Open accessibility settings", null)
        view.setSecondaryAction("Remind me tomorrow", null)
        Snapshots.layout(view, 260)
        val first = view.secondaryActionButton
        val last = view.actionButton
        val actions = last.parent as View
        assertThat(first.bottom).isAtMost(last.top)
        assertThat(first.left).isAtLeast(0)
        assertThat(last.left).isAtLeast(0)
        assertThat(last.right).isAtMost(actions.width)
        assertThat((actions.parent as View).top + actions.bottom).isAtMost(view.height)

        // With room they go back side by side.
        Snapshots.layout(view, 640)
        assertThat(first.right).isAtMost(last.left)
        assertThat(first.top).isEqualTo(last.top)
    }

    @Test
    fun `a banner with no text at all still lays out and a long title wraps`() {
        val empty = banner(title = null, detail = null)
        empty.isDismissible = true
        Snapshots.layout(empty, 320)
        assertThat(empty.title).isNull()
        assertThat(empty.height).isGreaterThan(0)
        Snapshots.render(empty, 320)

        val long = banner(title = "A very long title that cannot possibly fit on one single line of this banner", detail = null)
        long.isDismissible = true
        Snapshots.layout(long, 240)
        assertThat(long.titleView.lineCount).isGreaterThan(1)
        val column = long.titleView.parent as View
        assertThat(column.right).isAtMost(closeOf(long).left)
        assertThat(column.bottom).isAtMost(long.height)
    }

    @Test
    fun `in right-to-left the icon leads from the right and the close icon sits on the left`() {
        enableRtl()
        val view = banner()
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        view.isDismissible = true
        view.setAction("Retry", null)
        Snapshots.layout(view, 320)
        val column = view.titleView.parent as View
        assertThat(closeOf(view).left).isEqualTo(0)
        assertThat(iconOf(view).left).isAtLeast(column.right)
        assertThat(iconOf(view).right).isAtMost(view.width - px(3f))
    }

    @Test
    fun `a short action keeps a 44 dp target and a collapsing banner drops keyboard focus`() {
        val view = banner()
        view.setAction("OK", null)
        Snapshots.attach(view, 320)
        assertThat(view.actionButton.width).isAtLeast(px(44f))
        assertThat(view.actionButton.isFocusable).isTrue()
        view.dismiss()
        assertThat(view.descendantFocusability).isEqualTo(ViewGroup.FOCUS_BLOCK_DESCENDANTS)
        view.show(animate = false)
        assertThat(view.descendantFocusability).isEqualTo(ViewGroup.FOCUS_AFTER_DESCENDANTS)
    }

    @Test
    fun `reduced motion still ends gone and detaching mid-glide settles`() {
        val calm = Snapshots.context(SancaiTheme.light().copy(reduceMotion = true))
        val view = banner(ctx = calm)
        attachPaused(view)
        view.dismiss()
        advance(120)
        assertThat(view.visibility).isEqualTo(View.GONE)

        val other = banner()
        attachPaused(other)
        other.dismiss()
        advance(32)
        (other.parent as ViewGroup).removeView(other)
        assertThat(other.visibility).isEqualTo(View.GONE)
        assertThat(other.isShowing).isFalse()
    }

    @Test
    fun `a banner that starts hidden grows from zero on its first show`() {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        val view = banner()
        view.dismiss(animate = false)
        val below = View(context)
        column.addView(view, LinearLayout.LayoutParams(-1, -2))
        column.addView(below, LinearLayout.LayoutParams(-1, px(40f)))
        attachPaused(column)
        assertThat(view.visibility).isEqualTo(View.GONE)
        assertThat(below.top).isEqualTo(0)

        view.show()
        assertThat(view.isShowing).isTrue()
        advance(60)
        val partly = view.height
        assertThat(partly).isGreaterThan(0)
        assertThat(below.top).isEqualTo(partly)
        advance(400)
        assertThat(view.height).isGreaterThan(partly)
        assertThat(below.top).isEqualTo(view.height)
    }

    @Test
    fun `a banner hidden through its visibility stays hidden after a restore`() {
        val id = View.generateViewId()
        val view = banner()
        view.id = id
        view.visibility = View.GONE
        val saved = SparseArray<Parcelable>()
        view.saveHierarchyState(saved)

        val restored = banner()
        restored.id = id
        restored.visibility = View.GONE
        restored.restoreHierarchyState(saved)
        assertThat(restored.isShowing).isTrue()
        assertThat(restored.visibility).isEqualTo(View.GONE)

        // The banner's own collapse is still undone when the saved banner was on screen.
        view.visibility = View.VISIBLE
        view.saveHierarchyState(saved)
        val collapsed = banner()
        collapsed.id = id
        collapsed.dismiss(animate = false)
        collapsed.restoreHierarchyState(saved)
        assertThat(collapsed.isShowing).isTrue()
        assertThat(collapsed.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `the close icon draws its own focus rim only`() {
        val close = closeOf(banner().also { it.isDismissible = true })
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            assertThat(close.defaultFocusHighlightEnabled).isFalse()
        }
    }

    private fun sheet(ctx: Context): View {
        val theme = SancaiTheme.of(ctx)
        val page = LinearLayout(ctx)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(theme.colors.canvas)
        val pad = px(16f)
        page.setPadding(pad, pad, pad, pad)
        fun add(view: View) {
            val params = LinearLayout.LayoutParams(-1, -2)
            if (page.childCount > 0) params.topMargin = px(12f)
            page.addView(view, params)
        }
        add(banner(SancaiBanner.Tone.INFO, "Sync is paused", "Changes are kept on this device until you are back online.", ctx))
        add(
            banner(SancaiBanner.Tone.WARNING, "Accessibility service is off", "Nothing is filtered until it is switched back on.", ctx).also {
                it.setAction("Open settings", null)
                it.setSecondaryAction("Not now", null)
                it.isDismissible = true
            },
        )
        add(
            banner(SancaiBanner.Tone.DANGER, "Backup failed", null, ctx).also {
                it.setAction("Retry", null)
            },
        )
        add(banner(SancaiBanner.Tone.SUCCESS, "Everything is up to date", null, ctx).also { it.isDismissible = true })
        add(banner(SancaiBanner.Tone.SUCCESS, "Restored 214 entries", null, ctx))
        add(
            banner(SancaiBanner.Tone.INFO, "في وضع عدم الاتصال", "تُحفظ التغييرات على هذا الجهاز.", ctx).also {
                it.layoutDirection = View.LAYOUT_DIRECTION_RTL
                it.setAction("إعادة المحاولة", null)
                it.isDismissible = true
            },
        )
        add(
            banner(SancaiBanner.Tone.WARNING, "Disabled banner", "Pales as a whole.", ctx).also {
                it.setAction("Action", null)
                it.isDismissible = true
                it.isEnabled = false
            },
        )
        add(
            banner(SancaiBanner.Tone.DANGER, "A long title that wraps onto a second line of the banner", "Long action labels stack.", ctx).also {
                it.setAction("Open accessibility settings", null)
                it.setSecondaryAction("Remind me again tomorrow", null)
                it.isDismissible = true
            },
        )
        return page
    }

    @Test
    fun `snapshot of banners in light and dark`() {
        enableRtl()
        Snapshots.capture(sheet(context), "banner_light", 360)
        Snapshots.capture(sheet(Snapshots.context(SancaiTheme.dark())), "banner_dark", 360)
    }
}
