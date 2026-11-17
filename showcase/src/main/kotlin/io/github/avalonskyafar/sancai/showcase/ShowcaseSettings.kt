package io.github.avalonskyafar.sancai.showcase

import android.content.Context
import android.content.SharedPreferences
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import io.github.avalonskyafar.sancai.theme.SancaiGlazes
import io.github.avalonskyafar.sancai.theme.SancaiTheme

/**
 * The appearance settings of the Showcase app, and their translation into a [SancaiTheme].
 *
 * This is the pattern an app with its own settings screen would use: keep the choices in
 * preferences, build a theme from them, and hand it to the library through
 * [SancaiTheme.resolver].
 */
class ShowcaseSettings(context: Context) {

    enum class Appearance { SYSTEM, LIGHT, DARK }

    enum class GlazeSet { CLASSIC, PLUM }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("sancai_showcase", Context.MODE_PRIVATE)

    var appearance: Appearance
        get() = Appearance.values().getOrElse(prefs.getInt(KEY_APPEARANCE, 0)) { Appearance.SYSTEM }
        set(value) = prefs.edit().putInt(KEY_APPEARANCE, value.ordinal).apply()

    var largeText: Boolean
        get() = prefs.getBoolean(KEY_LARGE_TEXT, false)
        set(value) = prefs.edit().putBoolean(KEY_LARGE_TEXT, value).apply()

    var highContrast: Boolean
        get() = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()

    var reduceMotion: Boolean
        get() = prefs.getBoolean(KEY_REDUCE_MOTION, false)
        set(value) = prefs.edit().putBoolean(KEY_REDUCE_MOTION, value).apply()

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    var glazeSet: GlazeSet
        get() = GlazeSet.values().getOrElse(prefs.getInt(KEY_GLAZES, 0)) { GlazeSet.CLASSIC }
        set(value) = prefs.edit().putInt(KEY_GLAZES, value.ordinal).apply()

    /** Whether the dark palette is in effect for [context]. */
    fun isDark(context: Context): Boolean = when (appearance) {
        Appearance.LIGHT -> false
        Appearance.DARK -> true
        Appearance.SYSTEM -> SancaiTheme.systemIsDark(context)
    }

    /** The theme these settings describe. */
    fun toTheme(context: Context): SancaiTheme {
        val contrast = highContrast || SancaiTheme.systemPrefersHighContrast(context)
        val colors = if (isDark(context)) SancaiColors.dark(contrast) else SancaiColors.light(contrast)
        return SancaiTheme(
            colors = colors,
            glazes = if (glazeSet == GlazeSet.PLUM) plumGlazes() else SancaiGlazes.default(),
            textScale = if (largeText) SancaiTheme.LARGE_TEXT_SCALE else 1f,
            highContrast = contrast,
            reduceMotion = reduceMotion || SancaiTheme.systemReducesMotion(context),
            haptics = haptics,
        )
    }

    companion object {
        private const val KEY_APPEARANCE = "appearance"
        private const val KEY_LARGE_TEXT = "large_text"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_GLAZES = "glazes"

        /**
         * A second glaze set, to show that the scale is a token and not a fixed palette: the
         * same four steps from "least" to "most", poured in rose, sand, plum and teal.
         */
        fun plumGlazes(): SancaiGlazes = SancaiGlazes(
            terracotta = SancaiGlaze(rgb(0xD9, 0x8E, 0x9A), rgb(0xC2, 0x70, 0x7E), rgb(0xFF, 0xFF, 0xFF)),
            ochre = SancaiGlaze(rgb(0xE3, 0xCF, 0xA4), rgb(0xC6, 0xAE, 0x7C), rgb(0x3A, 0x2C, 0x10)),
            cobalt = SancaiGlaze(rgb(0xA9, 0x97, 0xE6), rgb(0x88, 0x74, 0xD0), rgb(0xFF, 0xFF, 0xFF)),
            celadon = SancaiGlaze(rgb(0x7F, 0xBF, 0xBE), rgb(0x57, 0x9E, 0x9D), rgb(0xF2, 0xFF, 0xFE)),
            mist = SancaiGlaze(rgb(0xEA, 0xE6, 0xF4), rgb(0xC7, 0xBF, 0xDD), rgb(0x45, 0x3F, 0x5C)),
        )

        private fun rgb(r: Int, g: Int, b: Int): Int = android.graphics.Color.rgb(r, g, b)
    }
}
