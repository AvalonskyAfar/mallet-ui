package io.github.avalonskyafar.sancai.widget

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiFoundationTest {

    @Test
    fun `glaze scale follows the option count`() {
        val g = SancaiTheme.light().glazes
        assertThat(g.scale(2)).containsExactly(g.terracotta, g.cobalt).inOrder()
        assertThat(g.scale(3)).containsExactly(g.terracotta, g.ochre, g.cobalt).inOrder()
        assertThat(g.scale(4)).containsExactly(g.terracotta, g.ochre, g.cobalt, g.celadon).inOrder()
        assertThat(g.uniform(3, g.mist)).containsExactly(g.mist, g.mist, g.mist)
    }

    @Test
    fun `divider is fourteen percent ink on canvas`() {
        // blend(#FFFFFF, #1A1C1F, 0.14) = #DFDFE0
        assertThat(SancaiTheme.light().colors.divider).isEqualTo(Color.rgb(223, 223, 224))
        val contrast = SancaiTheme.light(highContrast = true).colors
        assertThat(contrast.divider).isEqualTo(contrast.hairline)
    }

    @Test
    fun `settle duration is bounded and shortens with reduced motion`() {
        assertThat(SancaiMotion.settleDuration(0f, false)).isEqualTo(220L)
        assertThat(SancaiMotion.settleDuration(0.5f, false)).isEqualTo(315L)
        assertThat(SancaiMotion.settleDuration(1f, false)).isEqualTo(420L)
        assertThat(SancaiMotion.settleDuration(1f, true)).isEqualTo(120L)
    }

    @Test
    fun `an explicit theme wins over the context`() {
        val dark = SancaiTheme.dark().copy(textScale = SancaiTheme.LARGE_TEXT_SCALE)
        assertThat(SancaiTheme.of(Snapshots.context(dark))).isEqualTo(dark)
    }

    @Test
    fun `high contrast swaps pale on-glaze labels for deep ones`() {
        val normal = SancaiTheme.light()
        val strong = SancaiTheme.light(highContrast = true)
        assertThat(normal.labelOnBed(normal.glazes.cobalt)).isEqualTo(Color.WHITE)
        val deep = strong.labelOnBed(strong.glazes.cobalt)
        assertThat(Color.red(deep) + Color.green(deep) + Color.blue(deep)).isLessThan(3 * 80)
    }

    @Test
    fun `surface stretches full bleed dividers from rim to rim`() {
        val context = Snapshots.context()
        val surface = SancaiSurface(context)
        surface.addView(SancaiText.create(context, "Row", SancaiTextRole.SUBHEAD))
        val divider = surface.addDivider()
        surface.addView(SancaiText.create(context, "Row", SancaiTextRole.SUBHEAD))
        Snapshots.layout(surface, 300)
        val density = context.resources.displayMetrics.density
        assertThat(divider.left.toFloat()).isWithin(1f).of(1.4f * density)
        assertThat(divider.right.toFloat()).isWithin(1f).of(surface.width - 1.4f * density)
        // Ordinary children keep the 9 dp content padding.
        assertThat(surface.getChildAt(0).left.toFloat()).isWithin(1f).of(9f * density)
    }

    @Test
    fun `button clicks tick once and loading swallows them`() {
        val button = SancaiButton(Snapshots.context()).apply { text = "Go" }
        var clicks = 0
        button.setOnClickListener { clicks++ }
        Snapshots.attach(button, 200)
        assertThat(button.width).isGreaterThan(0)
        Touch.tap(button, button.width / 2f)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(1)
        button.isLoading = true
        assertThat(button.performClick()).isFalse()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `snapshot of surfaces buttons and type`() {
        for ((name, theme) in listOf("light" to SancaiTheme.light(), "dark" to SancaiTheme.dark())) {
            val context = Snapshots.context(theme)
            val density = context.resources.displayMetrics.density
            fun px(dp: Int) = (dp * density).toInt()
            val page = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(12), px(22), px(12), px(34))
                clipChildren = false
            }
            fun params(top: Int = 0, side: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = px(top)
                leftMargin = px(side)
                rightMargin = px(side)
            }
            page.addView(SancaiText.create(context, "Sancai", SancaiTextRole.TITLE).apply { setPadding(px(10), 0, px(10), 0) }, params())
            page.addView(SancaiText.create(context, "Protection strength", SancaiTextRole.HEADING).apply { setPadding(px(10), 0, px(10), 0) }, params(16))
            page.addView(
                SancaiText.create(context, "A higher level reacts to more content", SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
                    .apply { setPadding(px(10), 0, px(10), 0) },
                params(4),
            )
            val card = SancaiSurface(context)
            card.addView(SancaiSegmentedTrack(context).apply { setOptions("Off", "Gentle", "Standard", "Strict"); select(2, false) }, params())
            card.addDivider(5f)
            card.addView(
                SancaiText.create(context, "A row of plain text inside the card", SancaiTextRole.SUBHEAD).apply {
                    gravity = Gravity.CENTER
                    minimumHeight = px(62)
                },
                params(),
            )
            page.addView(card, params(10))
            page.addView(SancaiButton(context).apply { text = "Restore defaults" }, params(22, 30))
            page.addView(SancaiButton(context).apply { text = "Continue"; variant = SancaiButton.Variant.PRIMARY; icon = SancaiIcon.ARROW_RIGHT }, params(10, 30))
            page.addView(SancaiButton(context).apply { text = "Delete account"; variant = SancaiButton.Variant.DANGER }, params(10, 30))
            page.addView(SancaiButton(context).apply { text = "Disabled"; isEnabled = false }, params(10, 30))
            page.addView(SancaiButton(context).apply { text = "Loading"; isLoading = true }, params(10, 30))
            page.addView(SancaiButton(context).apply { text = "Learn more"; variant = SancaiButton.Variant.TEXT }, params(4, 30))
            val bitmap = Snapshots.capture(page, "foundation_$name", 360)
            assertThat(bitmap.width).isGreaterThan(0)
        }
    }
}
