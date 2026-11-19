package io.github.avalonskyafar.mallet.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ScrollingView
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.interaction.MalletRevealLayout
import io.github.avalonskyafar.mallet.material.MalletCorners
import io.github.avalonskyafar.mallet.material.MalletMaterial
import io.github.avalonskyafar.mallet.material.MalletSurfaceDrawable
import io.github.avalonskyafar.mallet.theme.MalletTheme
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A hairline divider: 14 % of the ink colour laid over the canvas, one dp thick.
 *
 * Inside a [MalletSurface] a divider is full-bleed by default: the surface stretches it from
 * rim to rim regardless of the surface padding, so rows on either side read as sections of one
 * card rather than as boxes inside a box.
 */
public class MalletDivider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Whether a parent [MalletSurface] stretches this divider to its rims. */
    public var fullBleed: Boolean = true
        set(value) {
            field = value
            requestLayout()
        }

    /** [LinearLayout.HORIZONTAL] for a horizontal line (the default) or [LinearLayout.VERTICAL]. */
    public var orientation: Int = LinearLayout.HORIZONTAL
        set(value) {
            field = value
            requestLayout()
        }

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletDivider, defStyleAttr, 0)
            try {
                fullBleed = a.getBoolean(R.styleable.MalletDivider_malletFullBleed, true)
                orientation = a.getInt(R.styleable.MalletDivider_android_orientation, LinearLayout.HORIZONTAL)
            } finally {
                a.recycle()
            }
        }
        setBackgroundColor(MalletTheme.of(context).colors.divider)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val thickness = max(1, resources.displayMetrics.density.roundToInt())
        if (orientation == LinearLayout.VERTICAL) {
            setMeasuredDimension(thickness, getDefaultSize(suggestedMinimumHeight, heightMeasureSpec))
        } else {
            setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), thickness)
        }
    }
}

/**
 * The Mallet card: a raised surface that groups rows and controls.
 *
 * A card is a group: it holds at least two rows or blocks that belong together. A lone control
 * never gets a card of its own: it joins the card of its relatives, or it stands on the canvas
 * under its heading, without a card. One composite block, such as a list, a state view, a
 * paragraph or a picture, may have a card to itself.
 *
 * It is a [LinearLayout] (vertical unless told otherwise) with the surface material as its
 * background. Four things differ from a plain layout:
 *
 * - The view is slightly larger than the card the eye sees, because the shadow is drawn inside
 *   the view: 1.4 dp on each side, 1 dp above and 4.8 dp below. Content padding (9 dp by
 *   default) is measured from the view bounds.
 * - [MalletDivider] children marked full-bleed are stretched from rim to rim.
 * - A [MalletRevealLayout] child of a vertical surface is stretched over the cell of its row,
 *   from rim to rim and from divider to divider, so that the action it uncovers comes out from
 *   the edge of the card. The row inside it keeps its place.
 * - Children are not clipped to their bounds, so that the tracks and capsules inside a card
 *   can let their shadows fade out into the room around them. A child that scrolls (a
 *   `ScrollView`, a list, a `RecyclerView`, anything marked as a scroll container) is the
 *   exception: its content ends at its own edge.
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.widget.MalletSurface
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content">
 *     <!-- rows, a MalletDivider, more rows -->
 * </io.github.avalonskyafar.mallet.widget.MalletSurface>
 * ```
 */
public open class MalletSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** The theme this surface was created with. */
    protected val theme: MalletTheme = MalletTheme.of(context)
    private val material = MalletMaterial(context, theme)
    private val body = RectF()
    private val viewBounds = android.graphics.Rect()
    private var surface: MalletSurfaceDrawable

    /** Corner radius in dp. */
    public var cornerRadiusDp: Float = MalletMaterial.RADIUS_CARD_DP
        set(value) {
            if (field != value) {
                field = value
                rebuild()
            }
        }

    /** Lifted (rim and shadow) or flat (rim only). */
    public var elevated: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                rebuild()
            }
        }

    /** Which corners are rounded. Pair TOP and BOTTOM to build a split card. */
    public var corners: MalletCorners = MalletCorners.ALL
        set(value) {
            if (field != value) {
                field = value
                rebuild()
            }
        }

    init {
        var contentPadding = (CONTENT_PADDING_DP * resources.displayMetrics.density).roundToInt()
        var orientationFromXml = VERTICAL
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletSurface, defStyleAttr, 0)
            try {
                val density = resources.displayMetrics.density
                if (a.hasValue(R.styleable.MalletSurface_malletCornerRadius)) {
                    cornerRadiusDp = a.getDimension(R.styleable.MalletSurface_malletCornerRadius, 0f) / density
                }
                elevated = a.getBoolean(R.styleable.MalletSurface_malletElevated, true)
                corners = CORNERS.getOrElse(a.getInt(R.styleable.MalletSurface_malletSurfaceCorners, 0)) { MalletCorners.ALL }
                contentPadding = a.getDimensionPixelSize(R.styleable.MalletSurface_malletContentPadding, contentPadding)
                orientationFromXml = a.getInt(R.styleable.MalletSurface_android_orientation, VERTICAL)
            } finally {
                a.recycle()
            }
        }
        orientation = orientationFromXml
        if (paddingLeft == 0 && paddingTop == 0 && paddingRight == 0 && paddingBottom == 0) {
            setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
        }
        clipChildren = false
        clipToPadding = false
        surface = MalletSurfaceDrawable(material, cornerRadiusDp, false, null, elevated, corners)
        background = surface
    }

    private fun rebuild() {
        surface = MalletSurfaceDrawable(material, cornerRadiusDp, false, null, elevated, corners)
        background = surface
        requestLayout()
    }

    /**
     * Adds a full-bleed divider. In a vertical surface it gets [verticalMarginDp] of space
     * above and below; a horizontal surface gets a vertical line without margins.
     */
    @JvmOverloads
    public fun addDivider(verticalMarginDp: Float = 0f): MalletDivider {
        val divider = MalletDivider(context)
        val margin = (verticalMarginDp * resources.displayMetrics.density).roundToInt()
        val params = if (orientation == VERTICAL) {
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = margin
                bottomMargin = margin
            }
        } else {
            divider.orientation = VERTICAL
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        addView(divider, params)
        return divider
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // Full-bleed dividers ignore the content padding and meet the rim of the body.
        body.set(0f, 0f, 0f, 0f)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child !is MalletDivider || !child.fullBleed || child.visibility == GONE) continue
            if (body.isEmpty) {
                viewBounds.set(0, 0, width, height)
                surface.bodyBounds(viewBounds, body)
            }
            if (child.orientation == VERTICAL) {
                child.layout(child.left, body.top.roundToInt(), child.right, body.bottom.roundToInt())
            } else {
                child.layout(body.left.roundToInt(), child.top, body.right.roundToInt(), child.bottom)
            }
        }
        if (stretchesRows) layoutRevealCells()
    }

    /** Whether rows that slide aside are stretched over their cells: in a vertical surface. */
    internal val stretchesRows: Boolean get() = orientation == VERTICAL

    /**
     * A row that slides aside uncovers its bed from the rim of the card, not from the edge of
     * the content. Each [MalletRevealLayout] is therefore stretched over the cell of its row:
     * from rim to rim, up to the divider above it and down to the divider below it, or to the
     * rim of the body where it is the first or the last child. The row inside keeps the place
     * that the padding of the card gave it.
     */
    private fun layoutRevealCells() {
        var previous: View? = null
        var measured = false
        var index = 0
        while (index < childCount) {
            val child = getChildAt(index)
            index++
            if (child.visibility == GONE) continue
            if (child is MalletRevealLayout) {
                if (!measured) {
                    viewBounds.set(0, 0, width, height)
                    surface.bodyBounds(viewBounds, body)
                    measured = true
                }
                var next: View? = null
                var ahead = index
                while (ahead < childCount && next == null) {
                    val candidate = getChildAt(ahead)
                    if (candidate.visibility != GONE) next = candidate
                    ahead++
                }
                stretchOverCell(child, previous, next)
            }
            previous = child
        }
    }

    private fun stretchOverCell(child: MalletRevealLayout, previous: View?, next: View?) {
        val rim = material.dp(MalletMaterial.RIM_DP)
        val left = (body.left + rim).roundToInt()
        val right = (body.right - rim).roundToInt()
        val top = when {
            previous == null -> (body.top + rim).roundToInt()
            previous.isRimToRimLine -> previous.bottom
            else -> child.top
        }
        val bottom = when {
            next == null -> (body.bottom - rim).roundToInt()
            next.isRimToRimLine -> next.top
            else -> child.bottom
        }
        // A child that already reaches past the cell (a negative margin) is left as it is.
        if (child.left < left || child.right > right || child.top < top || child.bottom > bottom) {
            child.setCell(0, 0, 0, 0, 0f, 0f)
            return
        }
        val radius = (material.dp(cornerRadiusDp) - rim).coerceAtLeast(0f)
        child.setCell(
            child.left - left, child.top - top, right - child.right, bottom - child.bottom,
            if (previous == null && corners != MalletCorners.BOTTOM) radius else 0f,
            if (next == null && corners != MalletCorners.TOP) radius else 0f,
        )
        child.layout(left, top, right, bottom)
    }

    private val View.isRimToRimLine: Boolean
        get() {
            val divider = this as? MalletDivider ?: return false
            return divider.fullBleed && divider.orientation == HORIZONTAL
        }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (!scrolls(child)) return super.drawChild(canvas, child, drawingTime)
        val save = canvas.save()
        canvas.clipRect(
            child.left + child.translationX, child.top + child.translationY,
            child.right + child.translationX, child.bottom + child.translationY,
        )
        val more = super.drawChild(canvas, child, drawingTime)
        canvas.restoreToCount(save)
        return more
    }

    /** Decided by what the child is, not by how far it can scroll right now: it must not change between frames. */
    private fun scrolls(child: View): Boolean =
        child is ScrollView || child is HorizontalScrollView || child is AbsListView ||
            child is ScrollingView || child.isScrollContainer

    public companion object {
        /** Default padding between the view bounds and the content, in dp. */
        public const val CONTENT_PADDING_DP: Float = 9f

        private val CORNERS = MalletCorners.values()
    }
}
