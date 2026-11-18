package io.github.avalonskyafar.mallet.showcase

import android.content.Context
import android.content.SharedPreferences
import io.github.avalonskyafar.mallet.theme.MalletColors
import io.github.avalonskyafar.mallet.theme.MalletHue
import io.github.avalonskyafar.mallet.theme.MalletHues
import io.github.avalonskyafar.mallet.theme.MalletTheme

/**
 * The appearance settings of the Showcase app, and their translation into a [MalletTheme].
 *
 * This is the pattern an app with its own settings screen would use: keep the choices in
 * preferences, build a theme from them, and hand it to the library through
 * [MalletTheme.resolver].
 */
class ShowcaseSettings(context: Context) {

    enum class Appearance { SYSTEM, LIGHT, DARK }

    enum class HueSet { CLASSIC, PLUM }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("mallet_showcase", Context.MODE_PRIVATE)

    // Every view asks for the theme when it is built, so the theme is made once per change.
    private var version = 0
    private var cachedKey = -1
    private var cached: MalletTheme? = null

    private fun put(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
        version++
    }

    private fun put(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        version++
    }

    var appearance: Appearance
        get() = Appearance.values().getOrElse(prefs.getInt(KEY_APPEARANCE, 0)) { Appearance.SYSTEM }
        set(value) = put(KEY_APPEARANCE, value.ordinal)

    var largeText: Boolean
        get() = prefs.getBoolean(KEY_LARGE_TEXT, false)
        set(value) = put(KEY_LARGE_TEXT, value)

    var highContrast: Boolean
        get() = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        set(value) = put(KEY_HIGH_CONTRAST, value)

    var reduceMotion: Boolean
        get() = prefs.getBoolean(KEY_REDUCE_MOTION, false)
        set(value) = put(KEY_REDUCE_MOTION, value)

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = put(KEY_HAPTICS, value)

    var hueSet: HueSet
        get() = HueSet.values().getOrElse(prefs.getInt(KEY_HUES, 0)) { HueSet.CLASSIC }
        set(value) = put(KEY_HUES, value.ordinal)

    /** Whether the dark palette is in effect for [context]. */
    fun isDark(context: Context): Boolean = when (appearance) {
        Appearance.LIGHT -> false
        Appearance.DARK -> true
        Appearance.SYSTEM -> MalletTheme.systemIsDark(context)
    }

    /** The theme these settings describe. */
    fun toTheme(context: Context): MalletTheme {
        val dark = isDark(context)
        val contrast = highContrast || MalletTheme.systemPrefersHighContrast(context)
        val reduce = reduceMotion || MalletTheme.systemReducesMotion(context)
        // The system side can change behind our back (night mode, animation scale), so it is
        // part of the key together with the count of our own changes.
        val key = (version shl 3) or (if (dark) 4 else 0) or (if (contrast) 2 else 0) or (if (reduce) 1 else 0)
        cached?.let { if (cachedKey == key) return it }
        val theme = MalletTheme(
            colors = if (dark) MalletColors.dark(contrast) else MalletColors.light(contrast),
            hues = if (hueSet == HueSet.PLUM) plumHues() else MalletHues.default(),
            textScale = if (largeText) MalletTheme.LARGE_TEXT_SCALE else 1f,
            highContrast = contrast,
            reduceMotion = reduce,
            haptics = haptics,
        )
        cached = theme
        cachedKey = key
        return theme
    }

    companion object {
        private const val KEY_APPEARANCE = "appearance"
        private const val KEY_LARGE_TEXT = "large_text"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_HUES = "hues"

        /**
         * A second hue set, to show that the scale is a token and not a fixed palette: the
         * same four steps from "least" to "most", poured in rose, sand, plum and teal.
         */
        fun plumHues(): MalletHues = MalletHues(
            bronze = MalletHue(rgb(0xD9, 0x8E, 0x9A), rgb(0xC2, 0x70, 0x7E), rgb(0xFF, 0xFF, 0xFF)),
            ochre = MalletHue(rgb(0xE3, 0xCF, 0xA4), rgb(0xC6, 0xAE, 0x7C), rgb(0x3A, 0x2C, 0x10)),
            cobalt = MalletHue(rgb(0xA9, 0x97, 0xE6), rgb(0x88, 0x74, 0xD0), rgb(0xFF, 0xFF, 0xFF)),
            jade = MalletHue(rgb(0x7F, 0xBF, 0xBE), rgb(0x57, 0x9E, 0x9D), rgb(0xF2, 0xFF, 0xFE)),
            mist = MalletHue(rgb(0xEA, 0xE6, 0xF4), rgb(0xC7, 0xBF, 0xDD), rgb(0x45, 0x3F, 0x5C)),
        )

        private fun rgb(r: Int, g: Int, b: Int): Int = android.graphics.Color.rgb(r, g, b)
    }
}
