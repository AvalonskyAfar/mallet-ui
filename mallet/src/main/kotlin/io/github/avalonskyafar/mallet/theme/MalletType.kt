package io.github.avalonskyafar.mallet.theme

import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.ColorInt

/** The three weights of the Mallet type scale. All of them are system fonts. */
public enum class MalletWeight {
    REGULAR,
    MEDIUM,
    BOLD;

    /** The platform typeface for this weight. */
    public fun typeface(): Typeface = when (this) {
        REGULAR -> Typeface.DEFAULT
        MEDIUM -> MEDIUM_TYPEFACE
        BOLD -> Typeface.DEFAULT_BOLD
    }

    private companion object {
        val MEDIUM_TYPEFACE: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
}

/**
 * The Mallet type scale.
 *
 * Sizes are in sp and are multiplied by [MalletTheme.textScale]. Titles and labels are medium
 * weight with tight leading; only the two body roles open the leading up for reading.
 *
 * | Role | sp | Weight | Typical use |
 * |---|---|---|---|
 * | [DISPLAY] | 20.5 | medium | onboarding and hero titles |
 * | [TITLE] | 20 | medium | page title |
 * | [HEADLINE] | 18 | medium | dialog title, article group title |
 * | [HEADING] | 17 | medium | section heading above a card |
 * | [SUBHEAD] | 15.5 | medium | row title, article sub-heading |
 * | [BODY] | 15.5 | regular | long-form copy |
 * | [BODY_COMPACT] | 14.5 | regular | dense article copy |
 * | [LABEL] | 14.5 | medium | buttons and actions |
 * | [FOOTNOTE] | 13.5 | regular | facts, step details and other dense secondary text |
 * | [CAPTION] | 12.5 | regular | supporting line under a title |
 * | [MICRO] | 11.5 | regular | detail line inside a row |
 */
public enum class MalletTextRole(
    public val sizeSp: Float,
    public val weight: MalletWeight,
    public val lineExtraDp: Float = 0f,
    public val lineMultiplier: Float = 1.16f,
    public val letterSpacing: Float = 0f,
) {
    DISPLAY(20.5f, MalletWeight.MEDIUM),
    TITLE(20f, MalletWeight.MEDIUM),
    HEADLINE(18f, MalletWeight.MEDIUM),
    HEADING(17f, MalletWeight.MEDIUM),
    SUBHEAD(15.5f, MalletWeight.MEDIUM),
    BODY(15.5f, MalletWeight.REGULAR, lineExtraDp = 3f, lineMultiplier = 1.24f, letterSpacing = 0.01f),
    BODY_COMPACT(14.5f, MalletWeight.REGULAR, lineExtraDp = 3f, lineMultiplier = 1.28f, letterSpacing = 0.01f),
    LABEL(14.5f, MalletWeight.MEDIUM),
    FOOTNOTE(13.5f, MalletWeight.REGULAR),
    CAPTION(12.5f, MalletWeight.REGULAR),
    MICRO(11.5f, MalletWeight.REGULAR);

    /** Applies size, weight and leading to [view]. Colour is left to the caller. */
    public fun applyTo(view: TextView, theme: MalletTheme) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp * theme.textScale)
        view.typeface = weight.typeface()
        view.includeFontPadding = false
        view.setLineSpacing(lineExtraDp * view.resources.displayMetrics.density, lineMultiplier)
        view.letterSpacing = letterSpacing
    }

    /** Configures [paint] for drawing this role directly on a canvas. */
    public fun applyTo(paint: Paint, theme: MalletTheme, scaledDensity: Float) {
        paint.textSize = sizeSp * theme.textScale * scaledDensity
        paint.typeface = weight.typeface()
        paint.letterSpacing = letterSpacing
    }
}

/** The colour roles text can take. */
public enum class MalletTextTone {
    INK,
    MUTED,
    ACCENT,
    DANGER,
    WARNING,
    SUCCESS;

    @ColorInt
    public fun resolve(colors: MalletColors): Int = when (this) {
        INK -> colors.ink
        MUTED -> colors.muted
        ACCENT -> colors.accent
        DANGER -> colors.danger
        WARNING -> colors.warning
        SUCCESS -> colors.success
    }
}
