package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A count badge: a small capsule glazed in the danger colour with a white number on it.
 *
 * A [count] of zero hides the badge, unless [showDot] is set, in which case it shrinks to an
 * 8 dp dot that says "something is new" without saying how much. Counts beyond [maxCount] are
 * written as "99+".
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiBadge
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content" />
 * ```
 *
 * Use it as a plain view, or call [attach] to overlay one on the top end corner of any view
 * without touching the layout.
 *
 * For screen readers the badge describes itself with its number and is hidden while it shows
 * no number. A content description or an `importantForAccessibility` other than `auto` that
 * you set yourself, in XML or from code, is kept: the badge then leaves that property alone.
 * Set the description back to `null`, or the importance back to `auto`, and the badge takes
 * it over again with its next change.
 */
public class SancaiBadge @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val body = RectF()
    private var host: View? = null

    /**
     * Whether the content description and the importance are the badge's to write. They are
     * until the developer sets one, in XML or from code; `null` and `auto` hand them back.
     */
    private var ownsDescription = false
    private var ownsImportance = false
    private var writing = false

    /** The number shown. Negative values count as zero. Zero hides the badge or shows the dot. */
    public var count: Int = 0
        set(value) {
            val next = max(0, value)
            if (field == next) return
            field = next
            refresh()
        }

    /** Whether a count of zero is drawn as a small dot instead of nothing. */
    public var showDot: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            refresh()
        }

    /** The largest number written out; anything above is shown as this number and a plus sign. */
    public var maxCount: Int = 99
        set(value) {
            val next = max(1, value)
            if (field == next) return
            field = next
            refresh()
        }

    /** What the badge currently says: the number, "99+" style overflow, or nothing. */
    public val text: String
        get() = when {
            count <= 0 -> ""
            count > maxCount -> "$maxCount+"
            else -> count.toString()
        }

    /** Whether anything is drawn at all: a count above zero, or the dot. */
    public val isShowing: Boolean get() = count > 0 || showDot

    init {
        fillPaint.color = theme.colors.danger
        textPaint.color = TEXT_COLOR
        textPaint.typeface = SancaiWeight.BOLD.typeface()
        textPaint.textAlign = Paint.Align.CENTER
        // The View constructor has applied the XML attributes by now: what it left is ours.
        ownsDescription = contentDescription == null
        ownsImportance = importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_AUTO
        refresh()
    }

    /** Sets the description read by screen readers. Once set, the badge no longer writes its number; `null` hands it back. */
    override fun setContentDescription(contentDescription: CharSequence?) {
        if (!writing) ownsDescription = contentDescription == null
        super.setContentDescription(contentDescription)
    }

    /** Sets the importance for accessibility. Once set, the badge no longer changes it; `auto` hands it back. */
    override fun setImportantForAccessibility(mode: Int) {
        if (!writing) ownsImportance = mode == IMPORTANT_FOR_ACCESSIBILITY_AUTO
        super.setImportantForAccessibility(mode)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun refresh() {
        // What the developer chose, in XML or from code, is theirs: the badge only replaces
        // what is unset or what it wrote itself.
        writing = true
        try {
            if (ownsDescription) contentDescription = text.ifEmpty { null }
            if (ownsImportance) {
                // Only a number can be spoken; the dot and the hidden badge are decoration.
                importantForAccessibility =
                    if (count > 0) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        } finally {
            writing = false
        }
        requestLayout()
        invalidate()
        host?.let { anchor ->
            place(anchor)
            anchor.invalidate()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Measuring and drawing
    // ---------------------------------------------------------------------------------------

    private fun configureTextPaint() {
        textPaint.textSize =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, SancaiTextRole.MICRO.sizeSp, resources.displayMetrics) * theme.textScale
    }

    /** Natural width of the capsule or dot in pixels; zero when nothing shows. */
    private fun bodyWidth(): Float {
        if (count <= 0) return if (showDot) dp(DOT_DP) else 0f
        configureTextPaint()
        return max(bodyHeight(), textPaint.measureText(text) + dp(PADDING_DP) * 2f)
    }

    private fun bodyHeight(): Float {
        if (count <= 0) return if (showDot) dp(DOT_DP) else 0f
        configureTextPaint()
        val metrics = textPaint.fontMetrics
        // Large text grows the capsule instead of touching its rim.
        return max(dp(MIN_SIZE_DP), metrics.descent - metrics.ascent + dp(2f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(bodyWidth().roundToInt() + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(bodyHeight().roundToInt() + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isShowing) return
        val w = bodyWidth()
        val h = bodyHeight()
        val cx = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
        body.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        canvas.drawRoundRect(body, h / 2f, h / 2f, fillPaint)
        if (count <= 0) return
        // Digits have no descender: centre them on their own height, not on the font box.
        val metrics = textPaint.fontMetrics
        val digitHeight = -metrics.ascent * DIGIT_HEIGHT_SHARE
        canvas.drawText(text, cx, cy + digitHeight / 2f, textPaint)
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.TextView"

    // ---------------------------------------------------------------------------------------
    // Overlaying another view
    // ---------------------------------------------------------------------------------------

    private var overlayDrawable: Drawable? = null
    private val relayout = OnLayoutChangeListener { anchor, _, _, _, _, _, _, _, _ -> place(anchor) }

    /** Measures the badge and puts it into the top end corner of [anchor], in anchor coordinates. */
    private fun place(anchor: View) {
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        measure(unspecified, unspecified)
        val rtl = anchor.layoutDirection == LAYOUT_DIRECTION_RTL
        val left = if (rtl) 0 else anchor.width - measuredWidth
        layout(left, 0, left + measuredWidth, measuredHeight)
    }

    /**
     * Removes a badge created with [attach] from its anchor. Does nothing for a badge that lives
     * in a layout.
     */
    public fun detach() {
        val anchor = host ?: return
        overlayDrawable?.let { anchor.overlay.remove(it) }
        anchor.removeOnLayoutChangeListener(relayout)
        overlayDrawable = null
        host = null
        anchor.invalidate()
    }

    /** Draws a badge that is not part of any layout into the overlay of its anchor. */
    private class OverlayDrawable(private val badge: SancaiBadge) : Drawable() {
        override fun draw(canvas: Canvas) {
            if (!badge.isShowing) return
            val save = canvas.save()
            canvas.translate(badge.left.toFloat(), badge.top.toFloat())
            badge.draw(canvas)
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int): Unit = Unit

        override fun setColorFilter(colorFilter: ColorFilter?): Unit = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    public companion object {
        private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        private const val MIN_SIZE_DP = 18f
        private const val DOT_DP = 8f
        private const val PADDING_DP = 5f
        private const val DIGIT_HEIGHT_SHARE = 0.766f

        /**
         * Overlays a new badge on the top end corner of [anchor] and returns it. The badge is
         * drawn through the anchor's overlay, inside the anchor's bounds, so no layout changes;
         * set [count] or [showDot] on the returned badge and call [detach] to remove it.
         *
         * An overlaid badge is not part of the view tree: add its count to the anchor's content
         * description yourself.
         */
        @JvmStatic
        public fun attach(anchor: View): SancaiBadge {
            val badge = SancaiBadge(anchor.context)
            val drawable = OverlayDrawable(badge)
            badge.host = anchor
            badge.overlayDrawable = drawable
            badge.place(anchor)
            anchor.overlay.add(drawable)
            anchor.addOnLayoutChangeListener(badge.relayout)
            return badge
        }
    }
}
