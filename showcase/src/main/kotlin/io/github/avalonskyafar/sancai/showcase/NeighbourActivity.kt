package io.github.avalonskyafar.sancai.showcase

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.window.OnBackInvokedDispatcher
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialTransition
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.theme.SancaiWindow
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiText
import kotlin.math.roundToInt

/**
 * A separate activity that arrives and leaves like a neighbouring page.
 *
 * The three calls that make it so are marked below: the caller starts it with
 * [SancaiSpatialTransition.start], this activity wraps its content with
 * [SancaiSpatialTransition.wrap], and every way out goes through
 * [SancaiSpatialTransition.finish].
 */
class NeighbourActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = (application as ShowcaseApp).settings
        setTheme(if (settings.isDark(this)) R.style.Theme_Showcase_Dark else R.style.Theme_Showcase_Light)
        super.onCreate(savedInstanceState)
        SancaiWindow.apply(this)

        // (2) Wrap the page: it glides in over a picture of the screen below and can be
        // dragged back out.
        setContentView(SancaiSpatialTransition.wrap(this, buildPage()))

        // (3) System back leaves the same way.
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                SancaiSpatialTransition.finish(this)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        SancaiSpatialTransition.finish(this)
    }

    private fun buildPage(): View {
        val theme = SancaiTheme.of(this)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(PageScope.PAGE_SIDE), dp(PageScope.PAGE_TOP), dp(PageScope.PAGE_SIDE), dp(PageScope.PAGE_BOTTOM))
        }
        fun add(view: View, top: Int = 0, side: Int = 0) {
            val params = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP)
            params.topMargin = dp(top)
            params.marginStart = dp(side)
            params.marginEnd = dp(side)
            column.addView(view, params)
        }
        fun text(id: Int, role: SancaiTextRole, tone: SancaiTextTone = SancaiTextTone.INK) =
            SancaiText.create(this, getString(id), role, tone)

        add(text(R.string.neighbour_title, SancaiTextRole.TITLE), side = PageScope.TEXT_INSET)
        add(text(R.string.neighbour_summary, SancaiTextRole.CAPTION, SancaiTextTone.MUTED), top = 4, side = PageScope.TEXT_INSET)
        add(text(R.string.neighbour_body, SancaiTextRole.BODY), top = 22, side = PageScope.TEXT_INSET)

        add(text(R.string.neighbour_track_title, SancaiTextRole.HEADING), top = PageScope.SECTION_GAP, side = PageScope.TEXT_INSET)
        add(text(R.string.neighbour_track_detail, SancaiTextRole.CAPTION, SancaiTextTone.MUTED), top = 4, side = PageScope.TEXT_INSET)
        val card = SancaiSurface(this)
        card.addView(
            SancaiSegmentedTrack(this).apply {
                setOptions(
                    getString(R.string.neighbour_option_one),
                    getString(R.string.neighbour_option_two),
                    getString(R.string.neighbour_option_three),
                )
                select(1, animate = false)
            },
            LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP),
        )
        add(card, top = PageScope.HEADING_GAP)

        val leave = SancaiButton(this).apply {
            text = getString(R.string.neighbour_back)
            setOnClickListener { SancaiSpatialTransition.finish(this@NeighbourActivity) }
        }
        add(leave, top = PageScope.SECTION_GAP, side = PageScope.BUTTON_SIDE)

        return ScrollView(this).apply {
            setBackgroundColor(theme.colors.canvas)
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            addView(column, FrameLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP, Gravity.TOP))
            SancaiWindow.padForSystemBars(this)
        }
    }
}
