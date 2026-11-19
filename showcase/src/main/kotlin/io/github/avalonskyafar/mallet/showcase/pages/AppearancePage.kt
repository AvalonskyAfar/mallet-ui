package io.github.avalonskyafar.mallet.showcase.pages

import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.showcase.ShowcaseSettings
import io.github.avalonskyafar.mallet.theme.MalletHue
import io.github.avalonskyafar.mallet.theme.MalletHues
import io.github.avalonskyafar.mallet.widget.MalletControlRow
import io.github.avalonskyafar.mallet.widget.MalletSegmentedTrack
import io.github.avalonskyafar.mallet.widget.MalletSurface

/**
 * A first-level page, above Home: the appearance settings of the showcase itself.
 *
 * Every row writes one value to [ShowcaseSettings]; all but haptics then ask the host to
 * rebuild the screen. The library reads the settings through `MalletTheme.resolver` (see
 * `ShowcaseApp`), and `MalletThemeTransition` fades the old look over the new one.
 *
 * The settings are grouped in cards, two rows each. The preview is no setting and has no
 * relatives: its track stands on the canvas, between the groups.
 *
 * Device tests and the motion tour find the tracks by position: appearance, hues, the
 * preview, larger text, high contrast, reduce motion, haptics. Keep that order.
 */
object AppearancePage : DemoPage("appearance", R.string.page_appearance_title, R.string.page_appearance_summary) {

    override fun build(page: PageScope) {
        val settings = page.host.settings
        val hues = page.theme.hues
        val offOn = listOf(page.str(R.string.appearance_off), page.str(R.string.appearance_on))
        page.title()

        page.section(R.string.appearance_theme_title, R.string.appearance_theme_detail) {
            // Three choices that are not levels: each gets a hue of its own, as in the original.
            choice(
                page, R.string.appearance_mode, R.string.appearance_mode_detail,
                listOf(
                    page.str(R.string.appearance_mode_system),
                    page.str(R.string.appearance_mode_light),
                    page.str(R.string.appearance_mode_dark),
                ),
                settings.appearance.ordinal,
                listOf(hues.bronze, hues.mist, hues.cobalt),
            ) { track, index ->
                settings.appearance = ShowcaseSettings.Appearance.values()[index]
                page.host.applyAppearance(track)
            }
            // Each option has the "standard" hue (cobalt) of the set it names.
            choice(
                page, R.string.appearance_hues, R.string.appearance_hues_detail,
                listOf(page.str(R.string.appearance_hues_classic), page.str(R.string.appearance_hues_plum)),
                settings.hueSet.ordinal,
                listOf(MalletHues.default().cobalt, ShowcaseSettings.plumHues().cobalt),
            ) { track, index ->
                settings.hueSet = ShowcaseSettings.HueSet.values()[index]
                page.host.applyAppearance(track)
            }
        }

        // A lone track gets no card: it stands on the canvas under its heading, as wide as the
        // cards. Nothing listens to it; it is there to be tried with the settings around it.
        page.heading(R.string.appearance_preview_title, R.string.appearance_preview_detail)
        val scale = MalletSegmentedTrack(page.context).apply {
            setOptions(
                page.str(R.string.appearance_level_off),
                page.str(R.string.appearance_level_gentle),
                page.str(R.string.appearance_level_standard),
                page.str(R.string.appearance_level_strict),
            )
            select(2, animate = false)
            // No row names this track, so it names itself for a screen reader.
            contentDescription = page.str(R.string.appearance_preview_title)
        }
        page.loose(scale, topDp = PageScope.HEADING_GAP)

        page.section(R.string.appearance_reading_title, R.string.appearance_reading_detail) {
            choice(page, R.string.appearance_large_text, R.string.appearance_large_text_detail, offOn, settings.largeText.index()) { track, index ->
                settings.largeText = index == 1
                page.host.applyAppearance(track)
            }
            choice(page, R.string.appearance_contrast, R.string.appearance_contrast_detail, offOn, settings.highContrast.index()) { track, index ->
                settings.highContrast = index == 1
                page.host.applyAppearance(track)
            }
        }

        page.section(R.string.appearance_feel_title, R.string.appearance_feel_detail) {
            choice(page, R.string.appearance_reduce_motion, R.string.appearance_reduce_motion_detail, offOn, settings.reduceMotion.index()) { track, index ->
                settings.reduceMotion = index == 1
                page.host.applyAppearance(track)
            }
            // Haptics are read each time something ticks, so nothing has to be rebuilt. The track
            // calls this listener before it ticks: switching haptics off is silent, on confirms.
            choice(page, R.string.appearance_haptics, R.string.appearance_haptics_detail, offOn, settings.haptics.index()) { _, index ->
                settings.haptics = index == 1
            }
        }

        page.code(
            """
            // Application: your settings are the theme
            MalletTheme.resolver =
                MalletTheme.Resolver { context ->
                    settings.toTheme(context)
                }

            // A setting changed: let the thumb land,
            // then rebuild the screen
            MalletThemeTransition.recreateAfter(track)

            // Activity.onCreate, after setContentView:
            // fade the old look over the new one
            MalletThemeTransition.play(this)
            """,
            topDp = PageScope.SECTION_GAP,
        )
        page.caption(page.str(R.string.appearance_provenance), topDp = 14)
    }

    private fun Boolean.index(): Int = if (this) 1 else 0

    /** A utility row (text 1 : control 1.35) with a segmented track. */
    private fun MalletSurface.choice(
        page: PageScope,
        title: Int,
        detail: Int,
        options: List<String>,
        selected: Int,
        hues: List<MalletHue>? = null,
        onChange: (MalletSegmentedTrack, Int) -> Unit,
    ) {
        val row = MalletControlRow(context).apply {
            this.title = page.str(title)
            this.detail = page.str(detail)
            layout = MalletControlRow.Layout.UTILITY
        }
        val track = row.setSegmented(options, selected) { changed, index -> onChange(changed, index) }
        if (hues != null) track.setHues(hues)
        addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
    }
}
