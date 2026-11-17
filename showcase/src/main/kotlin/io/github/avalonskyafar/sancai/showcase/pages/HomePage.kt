package io.github.avalonskyafar.sancai.showcase.pages

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconPainter
import io.github.avalonskyafar.sancai.interaction.SancaiPress
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.Pages
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import io.github.avalonskyafar.sancai.theme.SancaiWindow
import io.github.avalonskyafar.sancai.widget.SancaiPowerSwitch

/**
 * The centre of the map: one hero control on a bare canvas, with the four neighbouring pages
 * named at the edges they lie behind. Dragging towards a name, or tapping it, travels there.
 *
 * It builds its own view instead of using [PageScope]'s scrolling column, because the page is
 * a fixed composition, not a list.
 */
object HomePage : DemoPage(Pages.HOME, R.string.page_home_title, 0) {

    override fun build(page: PageScope) {
        // Home is a fixed composition, assembled in createView().
    }

    override fun createView(page: PageScope): View {
        val scope = page
        val context = scope.context
        val theme = scope.theme
        val root = FrameLayout(context)
        root.setBackgroundColor(theme.colors.canvas)
        SancaiWindow.padForSystemBars(root)

        val centre = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(scope.dp(24), 0, scope.dp(24), 0)
        }
        val wordmark = scope.text(scope.str(R.string.home_wordmark), SancaiTextRole.DISPLAY).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 34f * theme.textScale)
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }
        val name = scope.text(scope.str(R.string.home_name), SancaiTextRole.LABEL, SancaiTextTone.MUTED).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }
        val tagline = scope.text(scope.str(R.string.home_tagline), SancaiTextRole.CAPTION, SancaiTextTone.MUTED).apply {
            gravity = Gravity.CENTER
        }
        val status = scope.text("", SancaiTextRole.CAPTION, SancaiTextTone.MUTED).apply { gravity = Gravity.CENTER }
        val power = SancaiPowerSwitch(context).apply {
            onLabel = scope.str(R.string.home_switch_on)
            offLabel = scope.str(R.string.home_switch_off)
            turnOnLabel = scope.str(R.string.home_switch_turn_on)
            turnOffLabel = scope.str(R.string.home_switch_turn_off)
            setChecked(true, false)
        }
        fun describe(on: Boolean) {
            status.text = scope.str(if (on) R.string.home_status_on else R.string.home_status_off)
        }
        describe(true)
        power.onCheckedChangeListener = SancaiPowerSwitch.OnCheckedChangeListener { _, checked -> describe(checked) }

        fun params(top: Int) = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = scope.dp(top) }
        centre.addView(wordmark, params(0))
        centre.addView(name, params(6))
        centre.addView(tagline, params(6))
        centre.addView(power, LinearLayout.LayoutParams(PageScope.MATCH, scope.dp(92)).apply { topMargin = scope.dp(34) })
        centre.addView(status, params(4))
        root.addView(
            centre,
            FrameLayout.LayoutParams(scope.dp(380).coerceAtMost(context.resources.displayMetrics.widthPixels), PageScope.WRAP, Gravity.CENTER),
        )

        fun place(key: String, icon: SancaiIcon, gravity: Int, vertical: Boolean) {
            val marker = PlaceMarker(context, theme, scope.str(Pages.titleOf(key)), icon, vertical)
            SancaiPress.attach(marker, marker) { scope.host.open(key) }
            val size = if (vertical) FrameLayout.LayoutParams(scope.dp(140), scope.dp(64), gravity)
            else FrameLayout.LayoutParams(scope.dp(64), scope.dp(140), gravity)
            root.addView(marker, size)
        }
        place(AppearancePage.key, SancaiIcon.CHEVRON_UP, Gravity.TOP or Gravity.CENTER_HORIZONTAL, vertical = true)
        place(ComponentsPage.key, SancaiIcon.CHEVRON_DOWN, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, vertical = true)
        place(FoundationsPage.key, SancaiIcon.CHEVRON_LEFT, Gravity.START or Gravity.CENTER_VERTICAL, vertical = false)
        place(InteractionsPage.key, SancaiIcon.CHEVRON_RIGHT, Gravity.END or Gravity.CENTER_VERTICAL, vertical = false)
        return root
    }

    /**
     * The name of a neighbouring page with a chevron pointing at it. Side markers write the
     * name along the edge, so it takes no width from the centre of the page.
     */
    private class PlaceMarker(
        context: Context,
        private val theme: SancaiTheme,
        private val label: String,
        private val icon: SancaiIcon,
        private val vertical: Boolean,
    ) : View(context) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = SancaiWeight.BOLD.typeface()
            textAlign = Paint.Align.CENTER
            color = theme.colors.muted
            textSize = 12.5f * theme.textScale * resources.displayMetrics.scaledDensity
        }

        init {
            contentDescription = label
        }

        override fun onDraw(canvas: Canvas) {
            val iconSize = 24f * density
            val cx = width / 2f
            val cy = height / 2f
            val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
            if (vertical) {
                // Chevron towards the edge, name towards the centre of the page.
                val up = icon == SancaiIcon.CHEVRON_UP
                val iconTop = if (up) 6f * density else height - 6f * density - iconSize
                SancaiIconPainter.draw(canvas, icon, cx - iconSize / 2f, iconTop, iconSize, theme.colors.muted, 1.7f * density)
                val baseline = if (up) iconTop + iconSize + 16f * density else iconTop - 8f * density
                canvas.drawText(label, cx, baseline, paint)
                return
            }
            val atLeft = (icon == SancaiIcon.CHEVRON_LEFT) != rtl
            val iconLeft = if (atLeft) 4f * density else width - 4f * density - iconSize
            SancaiIconPainter.draw(canvas, icon, iconLeft, cy - iconSize / 2f, iconSize, theme.colors.muted, 1.7f * density, rtl)
            // The name runs along the edge, reading towards the top on the left and the bottom on the right.
            val textX = if (atLeft) iconLeft + iconSize + 14f * density else iconLeft - 6f * density
            val save = canvas.save()
            canvas.rotate(if (atLeft) -90f else 90f, textX, cy)
            canvas.drawText(label, textX, cy, paint)
            canvas.restoreToCount(save)
        }
    }
}
