package io.github.avalonskyafar.mallet.theme

/**
 * The vertical rhythm of a Mallet page, in dp.
 *
 * A page is one column: an optional title, then sections. A section is a heading, a card and
 * the gap to the next heading. Cards span the column; headings and loose text are inset a
 * little further so that their first letter stands above the content of the card, not above
 * its rim. `MalletPage` applies these numbers for you; use them directly when you build a
 * column by hand, so that hand-made screens keep the same beat.
 *
 * All values are density-independent pixels. Multiply by the display density (or use
 * `MalletMaterial.dp`) before handing them to a view.
 */
public object MalletSpacing {

    /** Page padding on the left and on the right of the column. */
    public const val PAGE_SIDE: Float = 12f

    /** Page padding above the first element. */
    public const val PAGE_TOP: Float = 22f

    /** Page padding below the last element; deeper than the top so the page ends calmly. */
    public const val PAGE_BOTTOM: Float = 34f

    /**
     * Extra inset of headings and loose text on both sides, on top of [PAGE_SIDE], so that
     * they line up with the content of a card rather than with its rim.
     */
    public const val TEXT_INSET: Float = 10f

    /** From the page title down to the first heading. */
    public const val TITLE_GAP: Float = 16f

    /** From a heading down to its card. */
    public const val HEADING_GAP: Float = 10f

    /** From a card down to the next heading. */
    public const val SECTION_GAP: Float = 22f

    /** Content padding of a card, measured from the bounds of the card view. */
    public const val CARD_PADDING: Float = 9f

    /** Space above and below a divider that separates two groups of rows inside a card. */
    public const val DIVIDER_GAP: Float = 5f

    /** Side margin of a full-width capsule button that stands on the page, outside a card. */
    public const val BUTTON_SIDE: Float = 30f
}
