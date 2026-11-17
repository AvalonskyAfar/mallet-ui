package io.github.avalonskyafar.sancai.widget

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
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.material.SancaiCorners
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.material.SancaiSurfaceDrawable
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A hairline divider: 14 % of the ink colour laid over the canvas, one dp thick.
 *
 * Inside a [SancaiSurface] a divider is full-bleed by default: the surface stretches it from
 * rim to rim regardless of the surface padding, so rows on either side read as sections of one
 * card rather than as boxes inside a box.
 */
public class SancaiDivider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Whether a parent [SancaiSurface] stretches this divider to its rims. */
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
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiDivider, defStyleAttr, 0)
            try {
                fullBleed = a.getBoolean(R.styleable.SancaiDivider_sancaiFullBleed, true)
                orientation = a.getInt(R.styleable.SancaiDivider_android_orientation, LinearLayout.HORIZONTAL)
            } finally {
                a.recycle()
            }
        }
        setBackgroundColor(SancaiTheme.of(context).colors.divider)
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
 * The Sancai card: a porcelain body that groups rows and controls.
 *
 * It is a [LinearLayout] (vertical unless told otherwise) with the porcelain material as its
 * background. Three things differ from a plain layout:
 *
 * - The view is slightly larger than the card the eye sees, because the shadow is drawn inside
 *   the view: 1.4 dp on each side, 1 dp above and 4.8 dp below. Content padding (9 dp by
 *   default) is measured from the view bounds.
 * - [SancaiDivider] children marked full-bleed are stretched from rim to rim.
 * - Children are not clipped to their bounds, so that the tracks and capsules inside a card
 *   can let their shadows fade out into the room around them. A child that scrolls (a
 *   `ScrollView`, a list, a `RecyclerView`, anything marked as a scroll container) is the
 *   exception: its content ends at its own edge.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiSurface
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content">
 *     <!-- rows, a SancaiDivider, more rows -->
 * </io.github.avalonskyafar.sancai.widget.SancaiSurface>
 * ```
 */
public open class SancaiSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** The theme this surface was created with. */
    protected val theme: SancaiTheme = SancaiTheme.of(context)
    private val material = SancaiMaterial(context, theme)
    private val body = RectF()
    private val viewBounds = android.graphics.Rect()
    private var surface: SancaiSurfaceDrawable

    /** Corner radius in dp. */
    public var cornerRadiusDp: Float = SancaiMaterial.RADIUS_CARD_DP
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
    public var corners: SancaiCorners = SancaiCorners.ALL
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
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiSurface, defStyleAttr, 0)
            try {
                val density = resources.displayMetrics.density
                if (a.hasValue(R.styleable.SancaiSurface_sancaiCornerRadius)) {
                    cornerRadiusDp = a.getDimension(R.styleable.SancaiSurface_sancaiCornerRadius, 0f) / density
                }
                elevated = a.getBoolean(R.styleable.SancaiSurface_sancaiElevated, true)
                corners = CORNERS.getOrElse(a.getInt(R.styleable.SancaiSurface_sancaiSurfaceCorners, 0)) { SancaiCorners.ALL }
                contentPadding = a.getDimensionPixelSize(R.styleable.SancaiSurface_sancaiContentPadding, contentPadding)
                orientationFromXml = a.getInt(R.styleable.SancaiSurface_android_orientation, VERTICAL)
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
        surface = SancaiSurfaceDrawable(material, cornerRadiusDp, false, null, elevated, corners)
        background = surface
    }

    private fun rebuild() {
        surface = SancaiSurfaceDrawable(material, cornerRadiusDp, false, null, elevated, corners)
        background = surface
        requestLayout()
    }

    /**
     * Adds a full-bleed divider. In a vertical surface it gets [verticalMarginDp] of space
     * above and below; a horizontal surface gets a vertical line without margins.
     */
    @JvmOverloads
    public fun addDivider(verticalMarginDp: Float = 0f): SancaiDivider {
        val divider = SancaiDivider(context)
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
            if (child !is SancaiDivider || !child.fullBleed || child.visibility == GONE) continue
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

        private val CORNERS = SancaiCorners.values()
    }
}
