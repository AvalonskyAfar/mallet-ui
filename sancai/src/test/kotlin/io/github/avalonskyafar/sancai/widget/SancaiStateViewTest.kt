package io.github.avalonskyafar.sancai.widget

import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconView
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiStateView.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiStateViewTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val density = context.resources.displayMetrics.density

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private fun stateView(
        kind: Kind,
        title: String? = "Nothing here yet",
        detail: String? = "Accounts you add will appear in this list.",
        action: String? = null,
        ctx: android.content.Context = context,
    ): SancaiStateView = SancaiStateView(ctx).also {
        it.kind = kind
        it.title = title
        it.detail = detail
        it.setAction(action)
    }

    private fun message(view: SancaiStateView): LinearLayout = view.getChildAt(0) as LinearLayout
    private fun iconOf(view: SancaiStateView): SancaiIconView = message(view).getChildAt(0) as SancaiIconView
    private fun titleOf(view: SancaiStateView): SancaiText = message(view).getChildAt(1) as SancaiText
    private fun detailOf(view: SancaiStateView): SancaiText = message(view).getChildAt(2) as SancaiText

    /** Top of [child] in the coordinates of [root]. */
    private fun topIn(root: View, child: View): Int {
        var top = 0
        var current: View = child
        while (current !== root) {
            top += current.top
            current = current.parent as View
        }
        return top
    }

    private fun leftIn(root: View, child: View): Int {
        var left = 0
        var current: View = child
        while (current !== root) {
            left += current.left
            current = current.parent as View
        }
        return left
    }

    /** Whether the icon area of [view], rendered into [bitmap], holds a pixel of exactly [color]. */
    private fun iconHas(bitmap: Bitmap, view: SancaiStateView, color: Int): Boolean {
        val icon = iconOf(view)
        val left = leftIn(view, icon)
        val top = topIn(view, icon)
        for (y in top until top + icon.height) {
            for (x in left until left + icon.width) {
                if (bitmap.getPixel(x, y) == color) return true
            }
        }
        return false
    }

    @Test
    fun `each kind presets its icon and tone`() {
        val view = SancaiStateView(context)
        assertThat(view.kind).isEqualTo(Kind.EMPTY)
        assertThat(view.icon).isEqualTo(SancaiIcon.INBOX)
        assertThat(view.iconTone).isEqualTo(SancaiTextTone.MUTED)
        val expected = mapOf(
            Kind.EMPTY to (SancaiIcon.INBOX to SancaiTextTone.MUTED),
            Kind.ERROR to (SancaiIcon.ERROR to SancaiTextTone.DANGER),
            Kind.OFFLINE to (SancaiIcon.CLOUD_OFF to SancaiTextTone.MUTED),
            Kind.SUCCESS to (SancaiIcon.SUCCESS to SancaiTextTone.SUCCESS),
            Kind.INFO to (SancaiIcon.INFO to SancaiTextTone.MUTED),
        )
        for ((kind, preset) in expected) {
            view.kind = kind
            assertThat(view.icon).isEqualTo(preset.first)
            assertThat(view.iconTone).isEqualTo(preset.second)
            assertThat(iconOf(view).icon).isEqualTo(preset.first)
            assertThat(iconOf(view).tone).isEqualTo(preset.second)
        }
    }

    @Test
    fun `overrides hold until the kind changes`() {
        val view = stateView(Kind.ERROR)
        view.icon = SancaiIcon.TRASH
        view.iconColor = 0xFF123456.toInt()
        assertThat(view.kind).isEqualTo(Kind.ERROR)
        assertThat(iconOf(view).icon).isEqualTo(SancaiIcon.TRASH)
        assertThat(iconOf(view).color).isEqualTo(0xFF123456.toInt())
        view.kind = Kind.SUCCESS
        assertThat(view.iconColor).isNull()
        assertThat(iconOf(view).color).isNull()
        assertThat(view.icon).isEqualTo(SancaiIcon.SUCCESS)
        assertThat(view.title.toString()).isEqualTo("Nothing here yet")
    }

    @Test
    fun `the icon is painted in the colour of its kind`() {
        val colors = SancaiTheme.light().colors
        val empty = stateView(Kind.EMPTY)
        val emptyPixels = Snapshots.render(empty, 320)
        assertThat(iconHas(emptyPixels, empty, colors.muted)).isTrue()
        assertThat(iconHas(emptyPixels, empty, colors.danger)).isFalse()

        val error = stateView(Kind.ERROR)
        assertThat(iconHas(Snapshots.render(error, 320), error, colors.danger)).isTrue()
        val success = stateView(Kind.SUCCESS)
        assertThat(iconHas(Snapshots.render(success, 320), success, colors.success)).isTrue()

        val dark = SancaiTheme.dark()
        val night = stateView(Kind.ERROR, ctx = Snapshots.context(dark))
        assertThat(iconHas(Snapshots.render(night, 320), night, dark.colors.danger)).isTrue()
        assertThat(titleOf(night).currentTextColor).isEqualTo(dark.colors.ink)
        assertThat(detailOf(night).currentTextColor).isEqualTo(dark.colors.muted)
    }

    @Test
    fun `parts stack centred with the specified gaps`() {
        val view = stateView(Kind.EMPTY, action = "Add account")
        Snapshots.layout(view, 360)
        val icon = iconOf(view)
        val title = titleOf(view)
        val detail = detailOf(view)
        val button = view.actionButton
        assertThat(icon.width).isEqualTo(px(44f))
        assertThat(icon.height).isEqualTo(px(44f))
        assertThat(icon.strokeDp).isEqualTo(1.6f)
        assertThat(topIn(view, title) - (topIn(view, icon) + icon.height)).isEqualTo(px(16f))
        assertThat(topIn(view, detail) - (topIn(view, title) + title.height)).isEqualTo(px(6f))
        assertThat(button.top - message(view).bottom).isEqualTo(px(20f))
        assertThat(button.width).isEqualTo(px(160f))
        assertThat(button.variant).isEqualTo(SancaiButton.Variant.NEUTRAL)
        assertThat(title.role).isEqualTo(SancaiTextRole.SUBHEAD)
        assertThat(detail.role).isEqualTo(SancaiTextRole.CAPTION)
        // Everything shares the vertical axis of the view.
        for (part in listOf(icon, title, detail, button)) {
            val centre = leftIn(view, part) + part.width / 2f
            assertThat(centre).isWithin(1f).of(view.width / 2f)
        }
        assertThat(view.height).isEqualTo(button.bottom)
        assertThat(topIn(view, icon)).isEqualTo(0)
    }

    @Test
    fun `a long detail wraps at 280 dp and stays centred`() {
        val view = stateView(
            Kind.OFFLINE,
            detail = "The list could not be refreshed because the network is unreachable. " +
                "Nothing was changed on this device and nothing was sent.",
        )
        Snapshots.layout(view, 400)
        val detail = detailOf(view)
        assertThat(detail.width).isAtMost(px(280f))
        assertThat(detail.lineCount).isGreaterThan(1)
        assertThat(detail.gravity and Gravity.HORIZONTAL_GRAVITY_MASK).isEqualTo(Gravity.CENTER_HORIZONTAL)
        assertThat(leftIn(view, detail) + detail.width / 2f).isWithin(1f).of(view.width / 2f)
    }

    @Test
    fun `absent parts collapse with their gaps`() {
        val view = stateView(Kind.EMPTY, action = "Add account")
        Snapshots.layout(view, 360)
        val full = view.height

        view.detail = null
        Snapshots.layout(view, 360)
        assertThat(detailOf(view).visibility).isEqualTo(View.GONE)
        assertThat(view.actionButton.top - (topIn(view, titleOf(view)) + titleOf(view).height)).isEqualTo(px(20f))
        assertThat(view.height).isLessThan(full)

        view.icon = null
        Snapshots.layout(view, 360)
        assertThat(iconOf(view).visibility).isEqualTo(View.GONE)
        assertThat(topIn(view, titleOf(view))).isEqualTo(0)

        view.title = ""
        Snapshots.layout(view, 360)
        assertThat(message(view).visibility).isEqualTo(View.GONE)
        assertThat(view.actionButton.top).isEqualTo(0)
        assertThat(view.height).isEqualTo(view.actionButton.height)

        view.setAction(null)
        Snapshots.layout(view, 360)
        assertThat(view.actionButton.visibility).isEqualTo(View.GONE)
        assertThat(view.actionLabel).isNull()
        assertThat(view.height).isEqualTo(0)

        // Icon and detail without a title: the detail keeps the icon gap.
        view.icon = SancaiIcon.INFO
        view.detail = "Only a note."
        Snapshots.layout(view, 360)
        assertThat(topIn(view, detailOf(view)) - iconOf(view).height).isEqualTo(px(16f))
    }

    @Test
    fun `the action calls back for a tap and a key but not for setters`() {
        var clicks = 0
        val view = stateView(Kind.ERROR)
        view.setAction("Retry") { clicks++ }
        Snapshots.attach(view, 360)
        assertThat(view.actionLabel.toString()).isEqualTo("Retry")
        assertThat(clicks).isEqualTo(0)

        val button = view.actionButton
        Touch.tap(button, button.width / 2f, button.height / 2f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(1)

        button.performClick()
        assertThat(clicks).isEqualTo(2)

        // Replacing the label keeps the view quiet and swaps the listener.
        var others = 0
        view.setAction("Try again") { others++ }
        assertThat(clicks).isEqualTo(2)
        button.performClick()
        assertThat(others).isEqualTo(1)
        assertThat(clicks).isEqualTo(2)

        // The message itself is not operable.
        assertThat(view.isClickable).isFalse()
        assertThat(message(view).isClickable).isFalse()
        assertThat(button.isFocusable).isTrue()
    }

    @Test
    fun `a disabled view pales the message and blocks the action`() {
        var clicks = 0
        val view = stateView(Kind.ERROR)
        view.setAction("Retry") { clicks++ }
        Snapshots.attach(view, 360)
        val before = view.height
        view.isEnabled = false
        Snapshots.idle()
        assertThat(message(view).alpha).isWithin(0.001f).of(107f / 255f)
        assertThat(view.actionButton.isEnabled).isFalse()
        assertThat(view.height).isEqualTo(before)
        Touch.tap(view.actionButton, 20f, 20f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(0)
        view.isEnabled = true
        assertThat(message(view).alpha).isEqualTo(1f)
        assertThat(view.actionButton.isEnabled).isTrue()
    }

    @Test
    fun `compact shrinks the icon and the gaps but not the text`() {
        val view = stateView(Kind.EMPTY, action = "Add account")
        Snapshots.layout(view, 360)
        val regular = view.height
        val titleHeight = titleOf(view).height
        view.compact = true
        Snapshots.layout(view, 360)
        val icon = iconOf(view)
        val title = titleOf(view)
        val detail = detailOf(view)
        assertThat(icon.width).isEqualTo(px(28f))
        assertThat(icon.height).isEqualTo(px(28f))
        assertThat(topIn(view, title) - icon.height).isEqualTo(px(10f))
        assertThat(topIn(view, detail) - (topIn(view, title) + title.height)).isEqualTo(px(4f))
        assertThat(view.actionButton.top - message(view).bottom).isEqualTo(px(12f))
        assertThat(title.height).isEqualTo(titleHeight)
        assertThat(regular - view.height).isEqualTo(px(44f) - px(28f) + px(16f) - px(10f) + px(6f) - px(4f) + px(20f) - px(12f))
    }

    @Test
    fun `title and detail read as one element and the action as another`() {
        val view = stateView(Kind.ERROR, title = "Could not load", detail = "Nothing was changed.", action = "Retry")
        val spoken = message(view)
        assertThat(spoken.contentDescription.toString()).isEqualTo("Could not load\nNothing was changed.")
        assertThat(spoken.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        assertThat(iconOf(view).importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        assertThat(titleOf(view).importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        assertThat(detailOf(view).importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        assertThat(view.actionButton.importantForAccessibility).isNotEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        assertThat(view.actionButton.text.toString()).isEqualTo("Retry")

        view.detail = null
        assertThat(spoken.contentDescription.toString()).isEqualTo("Could not load")
        view.title = null
        assertThat(spoken.contentDescription).isNull()
        assertThat(spoken.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
    }

    @Test
    fun `xml attributes configure every part`() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiStateViewKind, "error")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTitle, "Could not load")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiDetail, "Nothing was changed.")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiStateViewAction, "Retry")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiStateViewCompact, "true")
            .build()
        val view = SancaiStateView(context, attrs)
        assertThat(view.kind).isEqualTo(Kind.ERROR)
        assertThat(view.icon).isEqualTo(SancaiIcon.ERROR)
        assertThat(view.title.toString()).isEqualTo("Could not load")
        assertThat(view.detail.toString()).isEqualTo("Nothing was changed.")
        assertThat(view.actionLabel.toString()).isEqualTo("Retry")
        assertThat(view.compact).isTrue()

        val noIcon = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiStateViewKind, "success")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiIcon, "none")
            .build()
        val bare = SancaiStateView(context, noIcon)
        assertThat(bare.kind).isEqualTo(Kind.SUCCESS)
        assertThat(bare.icon).isNull()
        assertThat(bare.actionLabel).isNull()
    }

    @Test
    fun `large text and right-to-left keep the column centred`() {
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = SancaiTheme.LARGE_TEXT_SCALE))
        val view = stateView(Kind.OFFLINE, action = "Retry", ctx = large)
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(view, 240)
        for (part in listOf<View>(iconOf(view), titleOf(view), detailOf(view), view.actionButton)) {
            assertThat(leftIn(view, part)).isAtLeast(0)
            assertThat(leftIn(view, part) + part.width).isAtMost(view.width)
            assertThat(leftIn(view, part) + part.width / 2f).isWithin(1f).of(view.width / 2f)
        }
        // Taller than its content, the view centres the column vertically.
        Snapshots.layout(view, 240, 400)
        val above = topIn(view, iconOf(view))
        val below = view.height - view.actionButton.bottom
        assertThat((above - below).toFloat()).isWithin(1f).of(0f)
    }

    @Test
    fun `long text in a narrow view wraps inside the bounds`() {
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = SancaiTheme.LARGE_TEXT_SCALE))
        val view = stateView(
            Kind.ERROR,
            title = "The list of protected accounts could not be loaded from this device",
            detail = "Nothing was read and nothing was changed. Check the storage permission, then try again.",
            action = "Try loading the protected accounts again",
            ctx = large,
        )
        Snapshots.layout(view, 200)
        assertThat(view.width).isEqualTo(px(200f))
        assertThat(titleOf(view).lineCount).isGreaterThan(1)
        assertThat(detailOf(view).lineCount).isGreaterThan(1)
        for (part in listOf<View>(titleOf(view), detailOf(view), view.actionButton)) {
            assertThat(leftIn(view, part)).isAtLeast(0)
            assertThat(leftIn(view, part) + part.width).isAtMost(view.width)
        }
        // Nothing overlaps: the parts still follow each other from top to bottom.
        assertThat(topIn(view, detailOf(view))).isAtLeast(topIn(view, titleOf(view)) + titleOf(view).height)
        assertThat(view.actionButton.top).isAtLeast(message(view).bottom)
        assertThat(view.actionButton.bottom).isAtMost(view.height)
    }

    @Test
    fun `a view with nothing in it takes no room and fills in later`() {
        val view = SancaiStateView(context)
        view.icon = null
        Snapshots.layout(view, 360)
        assertThat(view.height).isEqualTo(0)
        assertThat(view.title).isNull()
        assertThat(view.actionLabel).isNull()
        assertThat(message(view).contentDescription).isNull()

        // A new kind brings its icon back but leaves the texts and the action alone.
        view.title = "Done"
        view.setAction("Close")
        view.kind = Kind.SUCCESS
        Snapshots.layout(view, 360)
        assertThat(view.icon).isEqualTo(SancaiIcon.SUCCESS)
        assertThat(view.title.toString()).isEqualTo("Done")
        assertThat(view.actionLabel.toString()).isEqualTo("Close")
        assertThat(view.height).isGreaterThan(view.actionButton.height + px(44f))
    }

    @Test
    fun `xml can disable the view and code can add the listener afterwards`() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiStateViewAction, "Retry")
            .addAttribute(android.R.attr.enabled, "false")
            .build()
        var clicks = 0
        val view = SancaiStateView(context, attrs)
        view.onActionClick = { clicks++ }
        Snapshots.attach(view, 360)
        assertThat(view.isEnabled).isFalse()
        assertThat(view.actionButton.isEnabled).isFalse()
        assertThat(message(view).alpha).isWithin(0.001f).of(107f / 255f)
        view.isEnabled = true
        view.actionButton.performClick()
        assertThat(clicks).isEqualTo(1)
        // A label alone keeps the listener out: setAction replaces it.
        view.setAction("Again")
        view.actionButton.performClick()
        assertThat(clicks).isEqualTo(1)
    }

    private fun gallery(theme: SancaiTheme): View {
        val ctx = Snapshots.context(theme)
        val page = LinearLayout(ctx)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(px(16f), px(28f), px(16f), px(24f))
        fun add(view: View, topDp: Float) {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            params.topMargin = px(topDp)
            page.addView(view, params)
        }
        add(stateView(Kind.EMPTY, "No accounts yet", "Accounts you add are kept out of every clean-up.", "Add account", ctx), 0f)
        add(
            stateView(
                Kind.ERROR, "Could not load the page",
                "The list was not read and nothing on this device was changed.", "Retry", ctx,
            ),
            40f,
        )
        add(stateView(Kind.OFFLINE, "No connection", "Showing nothing until the network is back.", null, ctx), 40f)
        add(stateView(Kind.SUCCESS, "All settled", null, null, ctx), 40f)

        val card = SancaiSurface(ctx)
        val compact = stateView(Kind.INFO, "No rules in this group", "Rules you add will be listed here.", "Add rule", ctx)
        compact.compact = true
        compact.setPadding(0, px(14f), 0, px(10f))
        card.addView(compact, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        add(card, 40f)

        val disabled = stateView(Kind.ERROR, "Retry is unavailable", "Disabled: the whole message pales.", "Retry", ctx)
        disabled.compact = true
        disabled.isEnabled = false
        add(disabled, 28f)
        return page
    }

    @Test
    fun `snapshot of state views`() {
        val light = Snapshots.capture(gallery(SancaiTheme.light()), "stateview_light", 360)
        val dark = Snapshots.capture(gallery(SancaiTheme.dark()), "stateview_dark", 360)
        // The message sits on the bare canvas: no card, no slot background.
        assertThat(light.getPixel(4, 4)).isEqualTo(SancaiTheme.light().colors.canvas)
        assertThat(light.getPixel(light.width / 2, px(28f) + px(44f) + px(8f))).isEqualTo(SancaiTheme.light().colors.canvas)
        assertThat(dark.getPixel(4, 4)).isEqualTo(SancaiTheme.dark().colors.canvas)
    }
}
