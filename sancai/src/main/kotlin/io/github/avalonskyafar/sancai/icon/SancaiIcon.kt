package io.github.avalonskyafar.sancai.icon

/**
 * The built-in Sancai icon set.
 *
 * Every icon is a line drawing on a 24 x 24 grid: round caps, round joins, one stroke weight,
 * no fills except the occasional dot. They are drawn in code by [SancaiIconPainter], so they
 * take any colour and any size without a raster or vector asset.
 *
 * The order of the constants is part of the XML contract: the `sancaiIcon` attribute stores
 * the ordinal. Add new icons at the end.
 */
public enum class SancaiIcon {
    CHEVRON_LEFT,
    CHEVRON_RIGHT,
    CHEVRON_UP,
    CHEVRON_DOWN,
    ARROW_LEFT,
    ARROW_RIGHT,
    ARROW_UP,
    ARROW_DOWN,
    CHECK,
    CLOSE,
    PLUS,
    MINUS,
    SEARCH,
    MORE_HORIZONTAL,
    MORE_VERTICAL,
    MENU,
    POWER,
    BOOK,
    SLIDERS,
    MORE_CIRCLE,
    INFO,
    WARNING,
    ERROR,
    SUCCESS,
    TRASH,
    EDIT,
    COPY,
    SHARE,
    DOWNLOAD,
    REFRESH,
    EYE,
    EYE_OFF,
    LOCK,
    BELL,
    STAR,
    HEART,
    HOME,
    USER,
    SUN,
    MOON,
    LAYERS,
    GRID,
    LIST,
    IMAGE,
    INBOX,
    CLOUD_OFF,
    CLOCK,
    SHIELD,
    HAND,
    PALETTE;

    /**
     * Whether the icon points along the reading direction and must be mirrored in
     * right-to-left layouts.
     */
    public val isDirectional: Boolean
        get() = this == CHEVRON_LEFT || this == CHEVRON_RIGHT || this == ARROW_LEFT || this == ARROW_RIGHT

    public companion object {
        private val all = values()

        /** The icon for a value of the `sancaiIcon` XML attribute, or `null` for "none". */
        @JvmStatic
        public fun fromAttr(value: Int): SancaiIcon? = all.getOrNull(value)
    }
}
