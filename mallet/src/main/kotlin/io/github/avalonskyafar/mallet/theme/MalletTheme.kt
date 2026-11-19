package io.github.avalonskyafar.mallet.theme

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.accessibility.AccessibilityManager
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import io.github.avalonskyafar.mallet.R

/**
 * Everything a Mallet view needs to draw and move.
 *
 * Views resolve their theme once, when they are constructed, through [of]. The lookup order is:
 *
 * 1. a context created with [wrap];
 * 2. the process-wide [resolver], if it returns a theme;
 * 3. the `mallet*` attributes of the context's Android theme (see `Theme.Mallet`);
 * 4. the built-in light or dark palette, chosen by the current night mode.
 *
 * @property textScale multiplier applied on top of the system font scale
 * @property highContrast stronger hairlines, dividers, secondary text and on-hue labels
 * @property reduceMotion shortens every animation to a brief settle
 * @property haptics whether confirmed state changes tick
 * @property nightHue share of the hue kept in the recessed tracks of a dark theme;
 * `0` reproduces the neutral dark groove of the original app
 */
public data class MalletTheme @JvmOverloads constructor(
    val colors: MalletColors,
    val hues: MalletHues = MalletHues.default(),
    val textScale: Float = 1f,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val nightHue: Float = DEFAULT_NIGHT_HUE,
) {
    val isDark: Boolean get() = colors.isDark

    /** The plain recess of a dark theme. */
    @get:ColorInt
    val groove: Int get() = MalletColors.blend(colors.canvas, colors.surface, 0.14f)

    /**
     * Fill of a recess that carries no state: the empty part of a slider or progress groove.
     * Mist in light themes, the plain groove in dark ones.
     */
    @get:ColorInt
    val neutralBed: Int get() = if (isDark) groove else hues.mist.bed

    /** Rim of [neutralBed]. */
    @get:ColorInt
    val neutralBedEdge: Int
        get() = if (isDark) MalletColors.blend(colors.ink, colors.canvas, 0.93f) else hues.mist.edge

    /** Fill of a recessed track coloured with [hue] in this theme. */
    @ColorInt
    public fun bedColor(hue: MalletHue): Int =
        if (!isDark) hue.bed else MalletColors.blend(groove, hue.bed, nightHue)

    /** Rim of a recessed track coloured with [hue] in this theme. */
    @ColorInt
    public fun bedEdgeColor(hue: MalletHue): Int {
        if (!isDark) return hue.edge
        val neutral = MalletColors.blend(colors.ink, colors.canvas, 0.93f)
        return MalletColors.blend(neutral, hue.edge, nightHue)
    }

    /**
     * Colour of a label resting directly on a coloured bed.
     *
     * In light themes every resting label takes the tone that suits the hue that currently
     * fills the bed ([active]). In dark themes each label keeps a pale tint of its own
     * option's hue ([own]), so the scale stays readable inside the groove. High contrast
     * swaps the light tone for a deep one.
     */
    @ColorInt
    @JvmOverloads
    public fun labelOnBed(active: MalletHue, own: MalletHue = active, optionCount: Int = 2): Int {
        if (isDark) {
            val inkShare = if (highContrast) 0.86f else if (optionCount == 2) 0.62f else 0.70f
            return MalletColors.blend(own.bed, colors.ink, inkShare)
        }
        return if (highContrast) MalletColors.blend(active.edge, colors.ink, 0.82f) else active.onBed
    }

    /**
     * Colour of a label on a body coloured with [tint], such as a primary or destructive
     * capsule. It is white, as in the original design; with high contrast it becomes the
     * darkest neutral of the theme whenever that reads better on the tint.
     */
    @ColorInt
    public fun labelOnTint(@ColorInt tint: Int): Int {
        if (!highContrast) return Color.WHITE
        val deep = if (isDark) colors.canvas else colors.ink
        return if (MalletColors.contrast(Color.WHITE, tint) >= MalletColors.contrast(deep, tint)) Color.WHITE else deep
    }

    /** Supplies a theme for a context, or `null` to fall through to the Android theme. */
    public fun interface Resolver {
        public fun resolve(context: Context): MalletTheme?
    }

    public companion object {
        /** Scale used by the original "larger text" preference. */
        public const val LARGE_TEXT_SCALE: Float = 1.13f

        /** Default share of the hue kept in dark tracks. */
        public const val DEFAULT_NIGHT_HUE: Float = 0.30f

        /**
         * Optional process-wide theme source, for apps that keep appearance settings in their
         * own preferences. Set it once, before the first Mallet view is created.
         */
        @JvmStatic
        @Volatile
        public var resolver: Resolver? = null

        @JvmStatic
        @JvmOverloads
        public fun light(highContrast: Boolean = false): MalletTheme =
            MalletTheme(MalletColors.light(highContrast), highContrast = highContrast)

        @JvmStatic
        @JvmOverloads
        public fun dark(highContrast: Boolean = false): MalletTheme =
            MalletTheme(MalletColors.dark(highContrast), highContrast = highContrast)

        /** A context whose Mallet views use [theme], whatever the Android theme says. */
        @JvmStatic
        public fun wrap(base: Context, theme: MalletTheme): Context = MalletThemedContext(base, theme)

        /** The theme in effect for [context]. */
        @JvmStatic
        public fun of(context: Context): MalletTheme {
            var current: Context? = context
            while (current is ContextWrapper) {
                if (current is MalletThemedContext) return current.malletTheme
                current = current.baseContext
            }
            resolver?.resolve(context)?.let { return it }
            // Resolving three dozen attributes for every view of a page adds up, so the result
            // is remembered per context for as long as its night mode and the two system
            // settings a theme may follow (animations off, high-contrast text) stay the same.
            // Neither setting recreates an activity, so they are read again on every lookup.
            val uiMode = context.resources.configuration.uiMode
            val reduceMotion = systemReducesMotion(context)
            val highContrast = systemPrefersHighContrast(context)
            synchronized(attributeCache) {
                attributeCache[context]?.let { cached ->
                    if (cached.uiMode == uiMode && cached.reduceMotion == reduceMotion &&
                        cached.highContrast == highContrast
                    ) {
                        return cached.theme
                    }
                }
            }
            val resolved = fromAttributes(context)
            synchronized(attributeCache) {
                attributeCache[context] = CachedTheme(uiMode, reduceMotion, highContrast, resolved)
            }
            return resolved
        }

        private class CachedTheme(
            val uiMode: Int,
            val reduceMotion: Boolean,
            val highContrast: Boolean,
            val theme: MalletTheme,
        )

        private val attributeCache = java.util.WeakHashMap<Context, CachedTheme>()

        /** Whether the system has animations switched off (animator duration scale of zero). */
        @JvmStatic
        public fun systemReducesMotion(context: Context): Boolean = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        } catch (_: RuntimeException) {
            false
        }

        /**
         * Whether the system's high-contrast text setting is on. Android exposes it from
         * version 16 (API 36); on earlier versions this is always false.
         */
        @JvmStatic
        public fun systemPrefersHighContrast(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 36) return false
            return try {
                Api36.highContrastText(context)
            } catch (_: RuntimeException) {
                false
            }
        }

        /** Whether [context] is currently in night mode. */
        @JvmStatic
        public fun systemIsDark(context: Context): Boolean {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return night == Configuration.UI_MODE_NIGHT_YES
        }

        private const val REDUCE_MOTION_SYSTEM = 0
        private const val REDUCE_MOTION_ALWAYS = 1

        private fun fromAttributes(context: Context): MalletTheme {
            val a = context.obtainStyledAttributes(R.styleable.MalletTheme)
            try {
                val dark = if (a.hasValue(R.styleable.MalletTheme_malletIsDark)) {
                    a.getBoolean(R.styleable.MalletTheme_malletIsDark, false)
                } else {
                    systemIsDark(context)
                }
                val contrast = if (a.hasValue(R.styleable.MalletTheme_malletHighContrast)) {
                    a.getBoolean(R.styleable.MalletTheme_malletHighContrast, false)
                } else {
                    systemPrefersHighContrast(context)
                }
                val base = if (dark) MalletColors.dark(contrast) else MalletColors.light(contrast)
                val canvas = a.getColor(R.styleable.MalletTheme_malletColorCanvas, base.canvas)
                val ink = a.getColor(R.styleable.MalletTheme_malletColorInk, base.ink)
                val hairline = a.getColor(R.styleable.MalletTheme_malletColorHairline, base.hairline)
                val derivedDivider = if (contrast) {
                    hairline
                } else {
                    MalletColors.blend(canvas, ink, MalletColors.DIVIDER_INK_SHARE)
                }
                val colors = base.copy(
                    canvas = canvas,
                    surface = a.getColor(R.styleable.MalletTheme_malletColorSurface, base.surface),
                    raised = a.getColor(R.styleable.MalletTheme_malletColorRaised, base.raised),
                    ink = ink,
                    muted = a.getColor(R.styleable.MalletTheme_malletColorMuted, base.muted),
                    hairline = hairline,
                    divider = a.getColor(R.styleable.MalletTheme_malletColorDivider, derivedDivider),
                    accent = a.getColor(R.styleable.MalletTheme_malletColorAccent, base.accent),
                    danger = a.getColor(R.styleable.MalletTheme_malletColorDanger, base.danger),
                    warning = a.getColor(R.styleable.MalletTheme_malletColorWarning, base.warning),
                    success = a.getColor(R.styleable.MalletTheme_malletColorSuccess, base.success),
                    shadow = a.getColor(R.styleable.MalletTheme_malletColorShadow, base.shadow),
                )
                val d = MalletHues.default()
                val hues = MalletHues(
                    bronze = MalletHue(
                        a.getColor(R.styleable.MalletTheme_malletHueBronze, d.bronze.bed),
                        a.getColor(R.styleable.MalletTheme_malletHueBronzeEdge, d.bronze.edge),
                        a.getColor(R.styleable.MalletTheme_malletHueOnBronze, d.bronze.onBed),
                    ),
                    ochre = MalletHue(
                        a.getColor(R.styleable.MalletTheme_malletHueOchre, d.ochre.bed),
                        a.getColor(R.styleable.MalletTheme_malletHueOchreEdge, d.ochre.edge),
                        a.getColor(R.styleable.MalletTheme_malletHueOnOchre, d.ochre.onBed),
                    ),
                    cobalt = MalletHue(
                        a.getColor(R.styleable.MalletTheme_malletHueCobalt, d.cobalt.bed),
                        a.getColor(R.styleable.MalletTheme_malletHueCobaltEdge, d.cobalt.edge),
                        a.getColor(R.styleable.MalletTheme_malletHueOnCobalt, d.cobalt.onBed),
                    ),
                    jade = MalletHue(
                        a.getColor(R.styleable.MalletTheme_malletHueJade, d.jade.bed),
                        a.getColor(R.styleable.MalletTheme_malletHueJadeEdge, d.jade.edge),
                        a.getColor(R.styleable.MalletTheme_malletHueOnJade, d.jade.onBed),
                    ),
                    mist = MalletHue(
                        a.getColor(R.styleable.MalletTheme_malletHueMist, d.mist.bed),
                        a.getColor(R.styleable.MalletTheme_malletHueMistEdge, d.mist.edge),
                        a.getColor(R.styleable.MalletTheme_malletHueOnMist, d.mist.onBed),
                    ),
                )
                val reduce = when (a.getInt(R.styleable.MalletTheme_malletReduceMotion, REDUCE_MOTION_SYSTEM)) {
                    REDUCE_MOTION_SYSTEM -> systemReducesMotion(context)
                    REDUCE_MOTION_ALWAYS -> true
                    else -> false
                }
                return MalletTheme(
                    colors = colors,
                    hues = hues,
                    textScale = a.getFloat(R.styleable.MalletTheme_malletTextScale, 1f),
                    highContrast = contrast,
                    reduceMotion = reduce,
                    haptics = a.getBoolean(R.styleable.MalletTheme_malletHaptics, true),
                    nightHue = a.getFloat(R.styleable.MalletTheme_malletNightHue, DEFAULT_NIGHT_HUE)
                        .coerceIn(0f, 1f),
                )
            } finally {
                a.recycle()
            }
        }
    }
}

@RequiresApi(36)
private object Api36 {
    fun highContrastText(context: Context): Boolean =
        context.getSystemService(AccessibilityManager::class.java)?.isHighContrastTextEnabled == true
}

/** Carries an explicit [MalletTheme] through the context chain, including into inflaters. */
internal class MalletThemedContext(base: Context, val malletTheme: MalletTheme) : ContextWrapper(base) {
    private var inflater: LayoutInflater? = null

    override fun getSystemService(name: String): Any? {
        if (name == LAYOUT_INFLATER_SERVICE) {
            return inflater ?: LayoutInflater.from(baseContext).cloneInContext(this).also { inflater = it }
        }
        return super.getSystemService(name)
    }
}
