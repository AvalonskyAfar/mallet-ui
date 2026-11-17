package io.github.avalonskyafar.sancai.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconPainter
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import kotlin.math.max
import kotlin.math.min

/**
 * A quiet link to another page, usually placed below the last card of a screen: a muted,
 * centred label and a chevron that says on which side the destination lies.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiLinkRow
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiTitle="More settings"
 *     app:sancaiLinkRowArrowPlacement="edge"
 *     app:sancaiLinkRowArrowDirection="end"
 *     app:sancaiTone="muted" />
 * ```
 *
 * With [ArrowPlacement.EDGE] the label is centred in the row and the chevron sits at the edge
 * on its own: it never takes width from the label or shifts it. With
 * [ArrowPlacement.ADJACENT] the chevron follows the label closely and the pair is centred as
 * a group, which reads as a compact "go deeper" link.
 *
 * The row has no body of its own. Pressing sinks the label and the chevron by 1.5 dp and
 * nothing changes colour; keyboard focus is shown as a rim inside the row. Set the action with
 * [setOnClickListener]; the click is followed by a haptic tick. Start and end follow the
 * layout direction, so in a right-to-left layout an [ArrowDirection.END] chevron sits on, and
 * points to, the left. A label that does not fit on one line is ellipsized.
 */
public class SancaiLinkRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiControl(context, attrs, defStyleAttr) {

    /** Where the chevron sits relative to the label. */
    public enum class ArrowPlacement {
        /** At the start or end edge of the row, independent of the centred label. */
        EDGE,

        /** Right next to the label; label and chevron are centred as a group. */
        ADJACENT,
    }

    /** The side the destination lies on. The chevron sits on that side and points to it. */
    public enum class ArrowDirection {
        /** The destination lies before this page: the chevron sits at, and points to, the start. */
        START,

        /** The destination lies after this page: the chevron sits at, and points to, the end. */
        END,
    }

    private val labelPaint = TextPaint(textPaint)
    private val labelBox = RectF()
    private val chevronBox = RectF()
    private val rowBox = RectF()
    private var shownLabel: CharSequence = ""
    private var sinkPx = 0f
    private var sinkAnimator: ValueAnimator? = null
    private var down = false

    /** The text of the link. It is also what accessibility services announce. */
    public var label: CharSequence? = null
        set(value) {
            field = value
            contentDescription = value
            requestLayout()
            invalidate()
        }

    /** [ArrowPlacement.EDGE] (default) or [ArrowPlacement.ADJACENT]. */
    public var arrowPlacement: ArrowPlacement = ArrowPlacement.EDGE
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    /** The side of the destination; [ArrowDirection.END] by default. */
    public var arrowDirection: ArrowDirection = ArrowDirection.END
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Colour role of the label and the chevron; muted by default, because the link is quiet. */
    public var tone: SancaiTextTone = SancaiTextTone.MUTED
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        labelPaint.typeface = SancaiWeight.MEDIUM.typeface()
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiLinkRow, defStyleAttr, 0)
            try {
                label = a.getText(R.styleable.SancaiLinkRow_sancaiTitle)
                arrowPlacement = PLACEMENTS.getOrElse(
                    a.getInt(R.styleable.SancaiLinkRow_sancaiLinkRowArrowPlacement, 0),
                ) { ArrowPlacement.EDGE }
                arrowDirection = DIRECTIONS.getOrElse(
                    a.getInt(R.styleable.SancaiLinkRow_sancaiLinkRowArrowDirection, 1),
                ) { ArrowDirection.END }
                tone = TONES.getOrElse(
                    a.getInt(R.styleable.SancaiLinkRow_sancaiTone, SancaiTextTone.MUTED.ordinal),
                ) { SancaiTextTone.MUTED }
            } finally {
                a.recycle()
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Measure and arrange
    // ---------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        labelPaint.textSize = sp(SancaiTextRole.LABEL.sizeSp)
        val text = label ?: ""
        val labelWidth = labelPaint.measureText(text, 0, text.length)
        val reserved = if (arrowPlacement == ArrowPlacement.EDGE) {
            2f * dp(EDGE_INSET_DP + CHEVRON_DP + EDGE_GAP_DP)
        } else {
            dp(2f * EDGE_INSET_DP + ADJACENT_GAP_DP + CHEVRON_DP)
        }
        val wantedWidth = paddingLeft + paddingRight + labelWidth + reserved
        val metrics = labelPaint.fontMetrics
        val wantedHeight = max(
            dp(HEIGHT_DP),
            paddingTop + paddingBottom + (metrics.descent - metrics.ascent) + dp(2f * MIN_VERTICAL_SPACE_DP),
        )
        setMeasuredDimension(
            resolveSize(max(suggestedMinimumWidth, (wantedWidth + 0.5f).toInt()), widthMeasureSpec),
            resolveSize(max(suggestedMinimumHeight, (wantedHeight + 0.5f).toInt()), heightMeasureSpec),
        )
    }

    /** Places the label and the chevron for the current size; fills [labelBox] and [chevronBox]. */
    private fun arrange() {
        labelPaint.textSize = sp(SancaiTextRole.LABEL.sizeSp)
        val left = paddingLeft.toFloat()
        val right = (width - paddingRight).toFloat()
        val centerX = (left + right) / 2f
        val centerY = (paddingTop + height - paddingBottom) / 2f
        val chevron = dp(CHEVRON_DP)
        val edge = arrowPlacement == ArrowPlacement.EDGE
        val room = if (edge) {
            right - left - 2f * dp(EDGE_INSET_DP + CHEVRON_DP + EDGE_GAP_DP)
        } else {
            right - left - dp(2f * EDGE_INSET_DP + ADJACENT_GAP_DP + CHEVRON_DP)
        }
        val text = label ?: ""
        shownLabel = TextUtils.ellipsize(text, labelPaint, max(0f, room), TextUtils.TruncateAt.END)
        val labelWidth = min(max(0f, room), labelPaint.measureText(shownLabel, 0, shownLabel.length))
        val metrics = labelPaint.fontMetrics
        val labelTop = centerY - (metrics.descent - metrics.ascent) / 2f
        val labelBottom = labelTop + metrics.descent - metrics.ascent

        val labelLeft: Float
        val chevronLeft: Float
        if (edge) {
            // The label is centred in the row; the chevron is placed without looking at it.
            labelLeft = centerX - labelWidth / 2f
            chevronLeft = if (chevronOnLeft) left + dp(EDGE_INSET_DP) else right - dp(EDGE_INSET_DP) - chevron
        } else {
            val gap = dp(ADJACENT_GAP_DP)
            val groupLeft = centerX - (labelWidth + gap + chevron) / 2f
            if (chevronOnLeft) {
                chevronLeft = groupLeft
                labelLeft = groupLeft + chevron + gap
            } else {
                labelLeft = groupLeft
                chevronLeft = groupLeft + labelWidth + gap
            }
        }
        labelBox.set(labelLeft, labelTop, labelLeft + labelWidth, labelBottom)
        chevronBox.set(chevronLeft, centerY - chevron / 2f, chevronLeft + chevron, centerY + chevron / 2f)
    }

    /** Whether the chevron sits on, and points to, the left: the start side mirrored for RTL. */
    private val chevronOnLeft: Boolean get() = (arrowDirection == ArrowDirection.START) != isRtl

    /** Position of the label as it is drawn at rest, in view coordinates. */
    internal fun labelBounds(out: RectF) {
        arrange()
        out.set(labelBox)
    }

    /** Position of the 24 dp chevron square at rest, in view coordinates. */
    internal fun chevronBounds(out: RectF) {
        arrange()
        out.set(chevronBox)
    }

    /** How far the content is currently sunk, in pixels. */
    internal val contentSinkPx: Float get() = sinkPx

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        invalidate()
    }

    // ---------------------------------------------------------------------------------------
    // Press
    // ---------------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isEnabled) {
            when (event.actionMasked) {
                // The sink starts on touch-down, without the tap delay of scrolling parents.
                MotionEvent.ACTION_DOWN -> setDown(true)
                // A finger that leaves the row gives the press up; the content comes back at once.
                MotionEvent.ACTION_MOVE -> if (!contains(event.x, event.y)) setDown(false)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setDown(false)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun contains(x: Float, y: Float): Boolean =
        x >= -touchSlop && y >= -touchSlop && x < width + touchSlop && y < height + touchSlop

    override fun dispatchSetPressed(pressed: Boolean) {
        super.dispatchSetPressed(pressed)
        // Keyboard and accessibility presses arrive here rather than through touch events.
        if (!pressed) setDown(false) else if (!isInTouchMode) setDown(true)
    }

    private fun setDown(value: Boolean) {
        if (down == value) return
        down = value
        sinkAnimator?.cancel()
        sinkAnimator = null
        if (value) {
            sinkAnimator = settle(sinkPx, dp(SancaiMotion.PRESS_SINK_DP), SancaiMotion.PRESS_MS) { sinkPx = it }
        } else {
            // Back in place at once, before the click is delivered.
            sinkPx = 0f
            invalidate()
        }
    }

    override fun performClick(): Boolean {
        // An action: tick first, then the listener.
        SancaiHaptics.tick(this)
        return super.performClick()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) setDown(false)
    }

    override fun onDetachedFromWindow() {
        sinkAnimator?.cancel()
        sinkAnimator = null
        down = false
        sinkPx = 0f
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        arrange()
        if (showsFocus) {
            // The rim belongs to the row and stays where it is while the content sinks.
            rowBox.set(0f, 0f, width.toFloat(), height.toFloat())
            material.drawFocusRim(canvas, rowBox, dp(SancaiMaterial.RADIUS_CARD_DP))
        }
        val color = tone.resolve(theme.colors)
        val save = canvas.save()
        canvas.translate(0f, sinkPx)
        labelPaint.color = color
        val baseline = labelBox.top - labelPaint.fontMetrics.ascent
        canvas.drawText(shownLabel, 0, shownLabel.length, labelBox.left, baseline, labelPaint)
        SancaiIconPainter.draw(
            canvas,
            if (chevronOnLeft) SancaiIcon.CHEVRON_LEFT else SancaiIcon.CHEVRON_RIGHT,
            chevronBox.left,
            chevronBox.top,
            chevronBox.width(),
            color,
            dp(CHEVRON_STROKE_DP),
        )
        canvas.restoreToCount(save)
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"

    private companion object {
        private val PLACEMENTS = ArrowPlacement.values()
        private val DIRECTIONS = ArrowDirection.values()
        private val TONES = SancaiTextTone.values()
        private const val HEIGHT_DP = 64f
        private const val CHEVRON_DP = 24f
        private const val CHEVRON_STROKE_DP = 2f
        private const val EDGE_INSET_DP = 12f
        private const val ADJACENT_GAP_DP = 5f

        /** Smallest distance kept between the label and an edge chevron when the label is long. */
        private const val EDGE_GAP_DP = 6f
        private const val MIN_VERTICAL_SPACE_DP = 10f
    }
}
