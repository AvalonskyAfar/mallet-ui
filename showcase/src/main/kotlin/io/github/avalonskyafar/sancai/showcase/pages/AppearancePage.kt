package io.github.avalonskyafar.sancai.showcase.pages

import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.showcase.ShowcaseSettings
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import io.github.avalonskyafar.sancai.theme.SancaiGlazes
import io.github.avalonskyafar.sancai.widget.SancaiControlRow
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSurface

/**
 * Lies above Home: the appearance settings of the showcase itself.
 *
 * Every row writes one value to [ShowcaseSettings] and asks the host to rebuild the screen.
 * The library reads the settings through `SancaiTheme.resolver` (see `ShowcaseApp`), and
 * `SancaiThemeTransition` fades the old look over the new one.
 */
object AppearancePage : DemoPage("appearance", R.string.page_appearance_title, R.string.page_appearance_summary) {

    override fun build(page: PageScope) {
        val settings = page.host.settings
        val glazes = page.theme.glazes
        val offOn = listOf(page.str(R.string.appearance_off), page.str(R.string.appearance_on))
        page.title()

        page.section(R.string.appearance_theme_title, R.string.appearance_theme_detail) {
            // Three choices that are not levels: each gets a glaze of its own, as in the original.
            choice(
                page, R.string.appearance_mode, R.string.appearance_mode_detail,
                listOf(
                    page.str(R.string.appearance_mode_system),
                    page.str(R.string.appearance_mode_light),
                    page.str(R.string.appearance_mode_dark),
                ),
                settings.appearance.ordinal,
                listOf(glazes.terracotta, glazes.mist, glazes.cobalt),
            ) { track, index ->
                settings.appearance = ShowcaseSettings.Appearance.values()[index]
                page.host.applyAppearance(track)
            }
            // Each option is poured in the "standard" glaze of the set it names.
            choice(
                page, R.string.appearance_glazes, R.string.appearance_glazes_detail,
                listOf(page.str(R.string.appearance_glazes_classic), page.str(R.string.appearance_glazes_plum)),
                settings.glazeSet.ordinal,
                listOf(SancaiGlazes.default().cobalt, ShowcaseSettings.plumGlazes().cobalt),
            ) { track, index ->
                settings.glazeSet = ShowcaseSettings.GlazeSet.values()[index]
                page.host.applyAppearance(track)
            }
        }

        page.section(R.string.appearance_preview_title, R.string.appearance_preview_detail) {
            val scale = SancaiSegmentedTrack(context).apply {
                setOptions(
                    page.str(R.string.appearance_level_off),
                    page.str(R.string.appearance_level_gentle),
                    page.str(R.string.appearance_level_standard),
                    page.str(R.string.appearance_level_strict),
                )
                select(2, animate = false)
            }
            addView(scale, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
        }

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
            // Application.onCreate: the library asks your settings for its theme
            SancaiTheme.resolver = SancaiTheme.Resolver { context -> settings.toTheme(context) }

            // A setting changed: let the thumb finish its glide, then rebuild the screen
            track.onSelectionChangedListener =
                SancaiSegmentedTrack.OnSelectionChangedListener { track, index ->
                    settings.appearance = Appearance.values()[index]
                    SancaiThemeTransition.recreateAfter(track)
                }

            // Activity.onCreate, after setContentView: fade the old look over the new one
            SancaiThemeTransition.play(this)
            """,
            topDp = PageScope.SECTION_GAP,
        )
        page.caption(page.str(R.string.appearance_provenance), topDp = 14)
    }

    private fun Boolean.index(): Int = if (this) 1 else 0

    /** A utility row (text 1 : control 1.35) with a segmented track. */
    private fun SancaiSurface.choice(
        page: PageScope,
        title: Int,
        detail: Int,
        options: List<String>,
        selected: Int,
        glazes: List<SancaiGlaze>? = null,
        onChange: (SancaiSegmentedTrack, Int) -> Unit,
    ) {
        val row = SancaiControlRow(context).apply {
            this.title = page.str(title)
            this.detail = page.str(detail)
            layout = SancaiControlRow.Layout.UTILITY
        }
        val track = row.setSegmented(options, selected) { changed, index -> onChange(changed, index) }
        if (glazes != null) track.setGlazes(glazes)
        addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
    }
}
