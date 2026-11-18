package io.github.avalonskyafar.mallet.widget

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.testing.Touch
import io.github.avalonskyafar.mallet.theme.MalletColors
import io.github.avalonskyafar.mallet.theme.MalletMotion
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletFoundationTest {

    @Test
    fun `hue scale follows the option count`() {
        val g = MalletTheme.light().hues
        assertThat(g.scale(2)).containsExactly(g.bronze, g.cobalt).inOrder()
        assertThat(g.scale(3)).containsExactly(g.bronze, g.ochre, g.cobalt).inOrder()
        assertThat(g.scale(4)).containsExactly(g.bronze, g.ochre, g.cobalt, g.jade).inOrder()
        assertThat(g.uniform(3, g.mist)).containsExactly(g.mist, g.mist, g.mist)
    }

    @Test
    fun `divider is fourteen percent ink on canvas`() {
        // blend(#FFFFFF, #1A1C1F, 0.14) = #DFDFE0
        assertThat(MalletTheme.light().colors.divider).isEqualTo(Color.rgb(223, 223, 224))
        val contrast = MalletTheme.light(highContrast = true).colors
        assertThat(contrast.divider).isEqualTo(contrast.hairline)
    }

    @Test
    fun `settle duration is bounded and shortens with reduced motion`() {
        assertThat(MalletMotion.settleDuration(0f, false)).isEqualTo(220L)
        assertThat(MalletMotion.settleDuration(0.5f, false)).isEqualTo(315L)
        assertThat(MalletMotion.settleDuration(1f, false)).isEqualTo(420L)
        assertThat(MalletMotion.settleDuration(1f, true)).isEqualTo(120L)
    }

    @Test
    fun `an explicit theme wins over the context`() {
        val dark = MalletTheme.dark().copy(textScale = MalletTheme.LARGE_TEXT_SCALE)
        assertThat(MalletTheme.of(Snapshots.context(dark))).isEqualTo(dark)
    }

    @Test
    fun `high contrast swaps pale on-hue labels for deep ones`() {
        val normal = MalletTheme.light()
        val strong = MalletTheme.light(highContrast = true)
        assertThat(normal.labelOnBed(normal.hues.cobalt)).isEqualTo(Color.WHITE)
        val deep = strong.labelOnBed(strong.hues.cobalt)
        assertThat(Color.red(deep) + Color.green(deep) + Color.blue(deep)).isLessThan(3 * 80)
    }

    @Test
    fun `contrast follows the WCAG formula`() {
        assertThat(MalletColors.contrast(Color.BLACK, Color.WHITE)).isWithin(0.01f).of(21f)
        assertThat(MalletColors.contrast(Color.WHITE, Color.WHITE)).isWithin(0.001f).of(1f)
        val light = MalletTheme.light().colors
        assertThat(MalletColors.contrast(light.ink, light.canvas)).isWithin(0.1f).of(17.1f)
    }

    @Test
    fun `warning reads as text on the light canvas`() {
        val light = MalletTheme.light().colors
        assertThat(MalletColors.contrast(light.warning, light.canvas)).isAtLeast(4.5f)
    }

    @Test
    fun `high contrast makes every text role pass AA on canvas and on a card`() {
        for (colors in listOf(MalletTheme.light(true).colors, MalletTheme.dark(true).colors)) {
            for (role in listOf(colors.ink, colors.muted, colors.accent, colors.danger, colors.warning, colors.success)) {
                assertThat(MalletColors.contrast(role, colors.canvas)).isAtLeast(4.5f)
                assertThat(MalletColors.contrast(role, colors.surface)).isAtLeast(4.5f)
            }
        }
    }

    @Test
    fun `high contrast keeps labels legible on hue and on tinted capsules`() {
        for (theme in listOf(MalletTheme.light(true), MalletTheme.dark(true))) {
            val g = theme.hues
            for (hue in listOf(g.bronze, g.ochre, g.cobalt, g.jade, g.mist)) {
                assertThat(MalletColors.contrast(theme.labelOnBed(hue), theme.bedColor(hue))).isAtLeast(4.5f)
            }
            for (tint in listOf(theme.colors.accent, theme.colors.danger, theme.colors.success)) {
                assertThat(MalletColors.contrast(theme.labelOnTint(tint), tint)).isAtLeast(4.5f)
            }
        }
        // Without high contrast the capsule label stays white, as designed.
        assertThat(MalletTheme.light().labelOnTint(MalletTheme.light().colors.danger)).isEqualTo(Color.WHITE)
    }

    @Test
    fun `surface stretches full bleed dividers from rim to rim`() {
        val context = Snapshots.context()
        val surface = MalletSurface(context)
        surface.addView(MalletText.create(context, "Row", MalletTextRole.SUBHEAD))
        val divider = surface.addDivider()
        surface.addView(MalletText.create(context, "Row", MalletTextRole.SUBHEAD))
        Snapshots.layout(surface, 300)
        val density = context.resources.displayMetrics.density
        assertThat(divider.left.toFloat()).isWithin(1f).of(1.4f * density)
        assertThat(divider.right.toFloat()).isWithin(1f).of(surface.width - 1.4f * density)
        // Ordinary children keep the 9 dp content padding.
        assertThat(surface.getChildAt(0).left.toFloat()).isWithin(1f).of(9f * density)
    }

    @Test
    fun `button clicks tick once and loading swallows them`() {
        val button = MalletButton(Snapshots.context()).apply { text = "Go" }
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
        for ((name, theme) in listOf("light" to MalletTheme.light(), "dark" to MalletTheme.dark())) {
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
            page.addView(MalletText.create(context, "Mallet", MalletTextRole.TITLE).apply { setPadding(px(10), 0, px(10), 0) }, params())
            page.addView(MalletText.create(context, "Protection strength", MalletTextRole.HEADING).apply { setPadding(px(10), 0, px(10), 0) }, params(16))
            page.addView(
                MalletText.create(context, "A higher level reacts to more content", MalletTextRole.CAPTION, MalletTextTone.MUTED)
                    .apply { setPadding(px(10), 0, px(10), 0) },
                params(4),
            )
            val card = MalletSurface(context)
            card.addView(MalletSegmentedTrack(context).apply { setOptions("Off", "Gentle", "Standard", "Strict"); select(2, false) }, params())
            card.addDivider(5f)
            card.addView(
                MalletText.create(context, "A row of plain text inside the card", MalletTextRole.SUBHEAD).apply {
                    gravity = Gravity.CENTER
                    minimumHeight = px(62)
                },
                params(),
            )
            page.addView(card, params(10))
            page.addView(MalletButton(context).apply { text = "Restore defaults" }, params(22, 30))
            page.addView(MalletButton(context).apply { text = "Continue"; variant = MalletButton.Variant.PRIMARY; icon = MalletIcon.ARROW_RIGHT }, params(10, 30))
            page.addView(MalletButton(context).apply { text = "Delete account"; variant = MalletButton.Variant.DANGER }, params(10, 30))
            page.addView(MalletButton(context).apply { text = "Disabled"; isEnabled = false }, params(10, 30))
            page.addView(MalletButton(context).apply { text = "Loading"; isLoading = true }, params(10, 30))
            page.addView(MalletButton(context).apply { text = "Learn more"; variant = MalletButton.Variant.TEXT }, params(4, 30))
            val bitmap = Snapshots.capture(page, "foundation_$name", 360)
            assertThat(bitmap.width).isGreaterThan(0)
        }
    }
}
