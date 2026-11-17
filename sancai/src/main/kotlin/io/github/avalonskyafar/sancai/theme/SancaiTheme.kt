package io.github.avalonskyafar.sancai.theme

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
import io.github.avalonskyafar.sancai.R

/**
 * Everything a Sancai view needs to draw and move.
 *
 * Views resolve their theme once, when they are constructed, through [of]. The lookup order is:
 *
 * 1. a context created with [wrap];
 * 2. the process-wide [resolver], if it returns a theme;
 * 3. the `sancai*` attributes of the context's Android theme (see `Theme.Sancai`);
 * 4. the built-in light or dark palette, chosen by the current night mode.
 *
 * @property textScale multiplier applied on top of the system font scale
 * @property highContrast stronger hairlines, dividers, secondary text and on-glaze labels
 * @property reduceMotion shortens every animation to a brief settle
 * @property haptics whether confirmed state changes tick
 * @property nightGlaze share of glaze colour kept in the recessed tracks of a dark theme;
 * `0` reproduces the neutral dark groove of the original app
 */
public data class SancaiTheme(
    val colors: SancaiColors,
    val glazes: SancaiGlazes = SancaiGlazes.default(),
    val textScale: Float = 1f,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val nightGlaze: Float = DEFAULT_NIGHT_GLAZE,
) {
    val isDark: Boolean get() = colors.isDark

    /** The unglazed recess of a dark theme. */
    @get:ColorInt
    val groove: Int get() = SancaiColors.blend(colors.canvas, colors.surface, 0.14f)

    /** Fill of a recessed track glazed with [glaze] in this theme. */
    @ColorInt
    public fun bedColor(glaze: SancaiGlaze): Int =
        if (!isDark) glaze.bed else SancaiColors.blend(groove, glaze.bed, nightGlaze)

    /** Rim of a recessed track glazed with [glaze] in this theme. */
    @ColorInt
    public fun bedEdgeColor(glaze: SancaiGlaze): Int {
        if (!isDark) return glaze.edge
        val neutral = SancaiColors.blend(colors.ink, colors.canvas, 0.93f)
        return SancaiColors.blend(neutral, glaze.edge, nightGlaze)
    }

    /**
     * Colour of a label resting directly on a glazed bed.
     *
     * In light themes every resting label takes the tone that suits the glaze currently poured
     * ([active]). In dark themes each label keeps a pale tint of its own option's glaze
     * ([own]), so the scale stays readable inside the groove. High contrast swaps the light
     * tone for a deep one.
     */
    @ColorInt
    public fun labelOnBed(active: SancaiGlaze, own: SancaiGlaze = active, optionCount: Int = 2): Int {
        if (isDark) {
            val inkShare = if (highContrast) 0.86f else if (optionCount == 2) 0.62f else 0.70f
            return SancaiColors.blend(own.bed, colors.ink, inkShare)
        }
        return if (highContrast) SancaiColors.blend(active.edge, colors.ink, 0.82f) else active.onBed
    }

    /**
     * Colour of a label on a body glazed with [tint], such as a primary or destructive
     * capsule. It is white, as in the original design; with high contrast it becomes the
     * darkest neutral of the theme whenever that reads better on the tint.
     */
    @ColorInt
    public fun labelOnTint(@ColorInt tint: Int): Int {
        if (!highContrast) return Color.WHITE
        val deep = if (isDark) colors.canvas else colors.ink
        return if (SancaiColors.contrast(Color.WHITE, tint) >= SancaiColors.contrast(deep, tint)) Color.WHITE else deep
    }

    /** Supplies a theme for a context, or `null` to fall through to the Android theme. */
    public fun interface Resolver {
        public fun resolve(context: Context): SancaiTheme?
    }

    public companion object {
        /** Scale used by the original "larger text" preference. */
        public const val LARGE_TEXT_SCALE: Float = 1.13f

        /** Default share of glaze colour kept in dark tracks. */
        public const val DEFAULT_NIGHT_GLAZE: Float = 0.30f

        /**
         * Optional process-wide theme source, for apps that keep appearance settings in their
         * own preferences. Set it once, before the first Sancai view is created.
         */
        @JvmStatic
        @Volatile
        public var resolver: Resolver? = null

        @JvmStatic
        @JvmOverloads
        public fun light(highContrast: Boolean = false): SancaiTheme =
            SancaiTheme(SancaiColors.light(highContrast), highContrast = highContrast)

        @JvmStatic
        @JvmOverloads
        public fun dark(highContrast: Boolean = false): SancaiTheme =
            SancaiTheme(SancaiColors.dark(highContrast), highContrast = highContrast)

        /** A context whose Sancai views use [theme], whatever the Android theme says. */
        @JvmStatic
        public fun wrap(base: Context, theme: SancaiTheme): Context = SancaiThemedContext(base, theme)

        /** The theme in effect for [context]. */
        @JvmStatic
        public fun of(context: Context): SancaiTheme {
            var current: Context? = context
            while (current is ContextWrapper) {
                if (current is SancaiThemedContext) return current.sancaiTheme
                current = current.baseContext
            }
            resolver?.resolve(context)?.let { return it }
            // Resolving three dozen attributes for every view of a page adds up, so the result
            // is remembered per context for as long as its night mode stays the same.
            val uiMode = context.resources.configuration.uiMode
            synchronized(attributeCache) {
                attributeCache[context]?.let { cached -> if (cached.uiMode == uiMode) return cached.theme }
            }
            val resolved = fromAttributes(context)
            synchronized(attributeCache) { attributeCache[context] = CachedTheme(uiMode, resolved) }
            return resolved
        }

        private class CachedTheme(val uiMode: Int, val theme: SancaiTheme)

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

        private fun fromAttributes(context: Context): SancaiTheme {
            val a = context.obtainStyledAttributes(R.styleable.SancaiTheme)
            try {
                val dark = if (a.hasValue(R.styleable.SancaiTheme_sancaiIsDark)) {
                    a.getBoolean(R.styleable.SancaiTheme_sancaiIsDark, false)
                } else {
                    systemIsDark(context)
                }
                val contrast = if (a.hasValue(R.styleable.SancaiTheme_sancaiHighContrast)) {
                    a.getBoolean(R.styleable.SancaiTheme_sancaiHighContrast, false)
                } else {
                    systemPrefersHighContrast(context)
                }
                val base = if (dark) SancaiColors.dark(contrast) else SancaiColors.light(contrast)
                val canvas = a.getColor(R.styleable.SancaiTheme_sancaiColorCanvas, base.canvas)
                val ink = a.getColor(R.styleable.SancaiTheme_sancaiColorInk, base.ink)
                val hairline = a.getColor(R.styleable.SancaiTheme_sancaiColorHairline, base.hairline)
                val derivedDivider = if (contrast) {
                    hairline
                } else {
                    SancaiColors.blend(canvas, ink, SancaiColors.DIVIDER_INK_SHARE)
                }
                val colors = base.copy(
                    canvas = canvas,
                    surface = a.getColor(R.styleable.SancaiTheme_sancaiColorSurface, base.surface),
                    raised = a.getColor(R.styleable.SancaiTheme_sancaiColorRaised, base.raised),
                    ink = ink,
                    muted = a.getColor(R.styleable.SancaiTheme_sancaiColorMuted, base.muted),
                    hairline = hairline,
                    divider = a.getColor(R.styleable.SancaiTheme_sancaiColorDivider, derivedDivider),
                    accent = a.getColor(R.styleable.SancaiTheme_sancaiColorAccent, base.accent),
                    danger = a.getColor(R.styleable.SancaiTheme_sancaiColorDanger, base.danger),
                    warning = a.getColor(R.styleable.SancaiTheme_sancaiColorWarning, base.warning),
                    success = a.getColor(R.styleable.SancaiTheme_sancaiColorSuccess, base.success),
                    shadow = a.getColor(R.styleable.SancaiTheme_sancaiColorShadow, base.shadow),
                )
                val d = SancaiGlazes.default()
                val glazes = SancaiGlazes(
                    terracotta = SancaiGlaze(
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeTerracotta, d.terracotta.bed),
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeTerracottaEdge, d.terracotta.edge),
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeOnTerracotta, d.terracotta.onBed),
                    ),
                    ochre = SancaiGlaze(
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeOchre, d.ochre.bed),
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeOchreEdge, d.ochre.edge),
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeOnOchre, d.ochre.onBed),
                    ),
                    cobalt = SancaiGlaze(
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeCobalt, d.cobalt.bed),
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeCobaltEdge, d.cobalt.edge),
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeOnCobalt, d.cobalt.onBed),
                    ),
                    celadon = SancaiGlaze(
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeCeladon, d.celadon.bed),
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeCeladonEdge, d.celadon.edge),
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeOnCeladon, d.celadon.onBed),
                    ),
                    mist = SancaiGlaze(
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeMist, d.mist.bed),
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeMistEdge, d.mist.edge),
                        a.getColor(R.styleable.SancaiTheme_sancaiGlazeOnMist, d.mist.onBed),
                    ),
                )
                val reduce = when (a.getInt(R.styleable.SancaiTheme_sancaiReduceMotion, REDUCE_MOTION_SYSTEM)) {
                    REDUCE_MOTION_SYSTEM -> systemReducesMotion(context)
                    REDUCE_MOTION_ALWAYS -> true
                    else -> false
                }
                return SancaiTheme(
                    colors = colors,
                    glazes = glazes,
                    textScale = a.getFloat(R.styleable.SancaiTheme_sancaiTextScale, 1f),
                    highContrast = contrast,
                    reduceMotion = reduce,
                    haptics = a.getBoolean(R.styleable.SancaiTheme_sancaiHaptics, true),
                    nightGlaze = a.getFloat(R.styleable.SancaiTheme_sancaiNightGlaze, DEFAULT_NIGHT_GLAZE)
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

/** Carries an explicit [SancaiTheme] through the context chain, including into inflaters. */
internal class SancaiThemedContext(base: Context, val sancaiTheme: SancaiTheme) : ContextWrapper(base) {
    private var inflater: LayoutInflater? = null

    override fun getSystemService(name: String): Any? {
        if (name == LAYOUT_INFLATER_SERVICE) {
            return inflater ?: LayoutInflater.from(baseContext).cloneInContext(this).also { inflater = it }
        }
        return super.getSystemService(name)
    }
}
