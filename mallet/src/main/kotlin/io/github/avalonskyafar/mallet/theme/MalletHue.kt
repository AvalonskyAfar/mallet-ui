package io.github.avalonskyafar.mallet.theme

import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * One hue: the colour that fills a recessed track.
 *
 * In Mallet the bed of a control carries its state while the thumb stays neutral, so a hue
 * is always a triple.
 *
 * @property bed fill of the recessed track
 * @property edge one-dp rim of the track, a darker tone of the same hue
 * @property onBed label colour for options resting directly on the bed
 */
public data class MalletHue(
    @ColorInt val bed: Int,
    @ColorInt val edge: Int,
    @ColorInt val onBed: Int,
)

/**
 * The hue set of a theme.
 *
 * Four of them form an ordinal scale (off, gentle, standard, strict); [mist] is the pale
 * neutral used when an option is a plain choice rather than a level.
 */
public data class MalletHues(
    val bronze: MalletHue,
    val ochre: MalletHue,
    val cobalt: MalletHue,
    val jade: MalletHue,
    val mist: MalletHue,
) {
    /**
     * Default hue per option for an ordinal control with [count] options: two options read
     * off and on, three add the gentle step, four add the strict step. Further options stay
     * strict.
     */
    public fun scale(count: Int): List<MalletHue> = when {
        count <= 1 -> listOf(cobalt)
        count == 2 -> listOf(bronze, cobalt)
        count == 3 -> listOf(bronze, ochre, cobalt)
        else -> listOf(bronze, ochre, cobalt) + List(count - 3) { jade }
    }

    /**
     * The hue for a value of the `malletHue` XML attribute: 0 bronze, 1 ochre,
     * 2 cobalt, 3 jade, 4 mist. Anything else is cobalt.
     */
    public fun fromAttr(value: Int): MalletHue = when (value) {
        0 -> bronze
        1 -> ochre
        3 -> jade
        4 -> mist
        else -> cobalt
    }

    /** The same [hue] for every option: a plain, non-ordinal choice. */
    @JvmOverloads
    public fun uniform(count: Int, hue: MalletHue = cobalt): List<MalletHue> =
        List(count.coerceAtLeast(1)) { hue }

    public companion object {
        @JvmStatic
        public fun default(): MalletHues = MalletHues(
            bronze = MalletHue(Color.rgb(206, 131, 83), Color.rgb(190, 105, 56), Color.WHITE),
            ochre = MalletHue(Color.rgb(222, 190, 119), Color.rgb(194, 158, 82), Color.rgb(44, 33, 4)),
            cobalt = MalletHue(Color.rgb(134, 172, 247), Color.rgb(105, 144, 224), Color.WHITE),
            jade = MalletHue(Color.rgb(128, 184, 151), Color.rgb(91, 151, 116), Color.rgb(244, 255, 249)),
            mist = MalletHue(Color.rgb(226, 237, 247), Color.rgb(183, 204, 224), Color.rgb(52, 72, 92)),
        )
    }
}
