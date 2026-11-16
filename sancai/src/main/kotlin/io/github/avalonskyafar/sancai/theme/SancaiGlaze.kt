package io.github.avalonskyafar.sancai.theme

import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * One glaze: the colour poured into a recessed track.
 *
 * In Sancai the bed of a control carries its state while the thumb stays neutral, so a glaze
 * is always a triple.
 *
 * @property bed fill of the recessed track
 * @property edge one-dp rim of the track, a darker tone of the same hue
 * @property onBed label colour for options resting directly on the bed
 */
public data class SancaiGlaze(
    @ColorInt val bed: Int,
    @ColorInt val edge: Int,
    @ColorInt val onBed: Int,
)

/**
 * The glaze set of a theme.
 *
 * Four of them form an ordinal scale (off, gentle, standard, strict); [mist] is the pale
 * neutral used when an option is a plain choice rather than a level.
 */
public data class SancaiGlazes(
    val terracotta: SancaiGlaze,
    val ochre: SancaiGlaze,
    val cobalt: SancaiGlaze,
    val celadon: SancaiGlaze,
    val mist: SancaiGlaze,
) {
    /**
     * Default glaze per option for an ordinal control with [count] options: two options read
     * off and on, three add the gentle step, four add the strict step. Further options stay
     * strict.
     */
    public fun scale(count: Int): List<SancaiGlaze> = when {
        count <= 1 -> listOf(cobalt)
        count == 2 -> listOf(terracotta, cobalt)
        count == 3 -> listOf(terracotta, ochre, cobalt)
        else -> listOf(terracotta, ochre, cobalt) + List(count - 3) { celadon }
    }

    /**
     * The glaze for a value of the `sancaiGlaze` XML attribute: 0 terracotta, 1 ochre,
     * 2 cobalt, 3 celadon, 4 mist. Anything else is cobalt.
     */
    public fun fromAttr(value: Int): SancaiGlaze = when (value) {
        0 -> terracotta
        1 -> ochre
        3 -> celadon
        4 -> mist
        else -> cobalt
    }

    /** The same [glaze] for every option: a plain, non-ordinal choice. */
    @JvmOverloads
    public fun uniform(count: Int, glaze: SancaiGlaze = cobalt): List<SancaiGlaze> =
        List(count.coerceAtLeast(1)) { glaze }

    public companion object {
        @JvmStatic
        public fun default(): SancaiGlazes = SancaiGlazes(
            terracotta = SancaiGlaze(Color.rgb(206, 131, 83), Color.rgb(190, 105, 56), Color.WHITE),
            ochre = SancaiGlaze(Color.rgb(222, 190, 119), Color.rgb(194, 158, 82), Color.rgb(44, 33, 4)),
            cobalt = SancaiGlaze(Color.rgb(134, 172, 247), Color.rgb(105, 144, 224), Color.WHITE),
            celadon = SancaiGlaze(Color.rgb(128, 184, 151), Color.rgb(91, 151, 116), Color.rgb(244, 255, 249)),
            mist = SancaiGlaze(Color.rgb(226, 237, 247), Color.rgb(183, 204, 224), Color.rgb(52, 72, 92)),
        )
    }
}
