package io.github.avalonskyafar.mallet.showcase.pages

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconPainter
import io.github.avalonskyafar.mallet.interaction.MalletPress
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.Pages
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.theme.MalletWeight
import io.github.avalonskyafar.mallet.theme.MalletWindow
import io.github.avalonskyafar.mallet.widget.MalletPowerSwitch

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
        MalletWindow.padForSystemBars(root)

        val centre = CentreColumn(context, scope.dp(CENTRE_MAX_WIDTH_DP))
        val wordmark = scope.text(scope.str(R.string.home_wordmark), MalletTextRole.DISPLAY).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 34f * theme.textScale)
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }
        val name = scope.text(scope.str(R.string.home_name), MalletTextRole.LABEL, MalletTextTone.MUTED).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }
        val tagline = scope.text(scope.str(R.string.home_tagline), MalletTextRole.CAPTION, MalletTextTone.MUTED).apply {
            gravity = Gravity.CENTER
        }
        val status = scope.text("", MalletTextRole.CAPTION, MalletTextTone.MUTED).apply { gravity = Gravity.CENTER }
        val power = MalletPowerSwitch(context).apply {
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
        power.setOnCheckedChangeListener { _, checked -> describe(checked) }

        fun params(top: Int) = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = scope.dp(top) }
        centre.addView(wordmark, params(0))
        centre.addView(name, params(6))
        centre.addView(tagline, params(6))
        centre.addView(power, LinearLayout.LayoutParams(PageScope.MATCH, scope.dp(92)).apply { topMargin = scope.dp(34) })
        centre.addView(status, params(4))
        // The side margins leave the edges to the names of the pages that lie there.
        val centreParams = FrameLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP, Gravity.CENTER)
        centreParams.marginStart = scope.dp(CENTRE_SIDE_DP)
        centreParams.marginEnd = scope.dp(CENTRE_SIDE_DP)
        root.addView(centre, centreParams)

        fun place(key: String, icon: MalletIcon, gravity: Int, vertical: Boolean) {
            val marker = PlaceMarker(context, theme, scope.str(Pages.titleOf(key)), icon, vertical)
            MalletPress.attach(marker, marker) { scope.host.open(key) }
            val size = if (vertical) FrameLayout.LayoutParams(scope.dp(140), scope.dp(64), gravity)
            else FrameLayout.LayoutParams(scope.dp(64), scope.dp(140), gravity)
            root.addView(marker, size)
        }
        place(AppearancePage.key, MalletIcon.CHEVRON_UP, Gravity.TOP or Gravity.CENTER_HORIZONTAL, vertical = true)
        place(ComponentsPage.key, MalletIcon.CHEVRON_DOWN, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, vertical = true)
        place(FoundationsPage.key, MalletIcon.CHEVRON_LEFT, Gravity.START or Gravity.CENTER_VERTICAL, vertical = false)
        place(InteractionsPage.key, MalletIcon.CHEVRON_RIGHT, Gravity.END or Gravity.CENTER_VERTICAL, vertical = false)
        return root
    }

    private const val CENTRE_SIDE_DP = 56
    private const val CENTRE_MAX_WIDTH_DP = 420

    /** The centre of the page; it stops growing on wide screens. */
    private class CentreColumn(context: Context, private val maxWidth: Int) : LinearLayout(context) {
        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec)
            val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) maxWidth else minOf(available, maxWidth)
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
        }
    }

    /**
     * The name of a neighbouring page with a chevron pointing at it. Side markers write the
     * name along the edge, so it takes no width from the centre of the page.
     */
    private class PlaceMarker(
        context: Context,
        private val theme: MalletTheme,
        private val label: String,
        private val icon: MalletIcon,
        private val vertical: Boolean,
    ) : View(context) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = MalletWeight.BOLD.typeface()
            textAlign = Paint.Align.CENTER
            color = theme.colors.muted
            textSize = MalletTextRole.CAPTION.sizeSp * theme.textScale * resources.displayMetrics.scaledDensity
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
                val up = icon == MalletIcon.CHEVRON_UP
                val iconTop = if (up) 6f * density else height - 6f * density - iconSize
                MalletIconPainter.draw(canvas, icon, cx - iconSize / 2f, iconTop, iconSize, theme.colors.muted, 1.7f * density)
                val baseline = if (up) iconTop + iconSize + 16f * density else iconTop - 8f * density
                canvas.drawText(label, cx, baseline, paint)
                return
            }
            val atLeft = (icon == MalletIcon.CHEVRON_LEFT) != rtl
            val iconLeft = if (atLeft) 4f * density else width - 4f * density - iconSize
            MalletIconPainter.draw(canvas, icon, iconLeft, cy - iconSize / 2f, iconSize, theme.colors.muted, 1.7f * density, rtl)
            // The name runs along the edge, reading towards the top on the left and the bottom on the right.
            val textX = if (atLeft) iconLeft + iconSize + 14f * density else iconLeft - 6f * density
            if (label.all { isIdeograph(it) }) {
                // Chinese is written down the edge with every character upright.
                val line = paint.textSize * 1.3f
                val x = if (atLeft) iconLeft + iconSize + 8f * density else iconLeft - 8f * density
                var baseline = cy - line * (label.length - 1) / 2f - (paint.ascent() + paint.descent()) / 2f
                for (character in label) {
                    canvas.drawText(character.toString(), x, baseline, paint)
                    baseline += line
                }
                return
            }
            val save = canvas.save()
            canvas.rotate(if (atLeft) -90f else 90f, textX, cy)
            canvas.drawText(label, textX, cy, paint)
            canvas.restoreToCount(save)
        }

        /** Han, kana and hangul: scripts that are written upright in a vertical line. */
        private fun isIdeograph(character: Char): Boolean =
            character in '\u3040'..'\u30FF' || character in '\u3400'..'\u9FFF' || character in '\uAC00'..'\uD7AF'
    }
}
