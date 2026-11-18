package io.github.avalonskyafar.mallet.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AnimationUtils
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.FloatRange
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.theme.MalletColors
import io.github.avalonskyafar.mallet.theme.MalletHue
import io.github.avalonskyafar.mallet.theme.MalletMotion
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A slim recessed groove that fills with hue as work completes.
 *
 * The groove is the neutral, unglazed recess; the fill is poured from the start edge (the
 * right edge in right-to-left layouts) and takes the colour of [hue]. [setProgress] glides
 * to the new amount. When the amount of work is unknown set [isIndeterminate]: a glazed
 * segment then travels through the groove and re-enters at the start. With reduced motion the
 * indeterminate bar shows a still, pale, full-width fill instead of moving.
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.widget.MalletProgressBar
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:malletHue="jade"
 *     app:malletProgressValue="0.4" />
 * ```
 *
 * The view is 22 dp tall by default: a 10 dp groove plus the gutter its shadow needs. It is
 * an indicator, so it takes neither focus nor touches. Animations run only while the view is
 * attached and visible.
 */
public class MalletProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MalletControl(context, attrs, defStyleAttr) {

    private var target = 0f
    private var shown = 0f
    private var startedAt = 0L
    private val glide = MalletMotion.glide()
    private var indeterminate = false
    private var running = false
    private var attached = false
    private var progressAnimator: ValueAnimator? = null
    private val grooveBounds = RectF()
    private val fillBounds = RectF()
    private val grooveClip = Path()

    /** The hue of the fill. Default cobalt. */
    public var hue: MalletHue = theme.hues.cobalt
        set(value) {
            field = value
            invalidate()
        }

    /**
     * The completed share of the work, from 0 to 1. Setting it jumps; use [setProgress] to
     * glide. Reading it returns the amount last set, also while the fill is still gliding.
     */
    @get:FloatRange(from = 0.0, to = 1.0)
    public var progress: Float
        get() = target
        set(value) = setProgress(value, animate = false)

    /**
     * True when the amount of work is unknown. A glazed segment 30 % of the width then
     * travels from start to end, one pass per 1400 ms. [progress] is kept and shown again
     * when this is switched off.
     */
    public var isIndeterminate: Boolean
        get() = indeterminate
        set(value) {
            if (indeterminate == value) return
            indeterminate = value
            updateTravel()
            announceState()
            invalidate()
        }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletProgressBar, defStyleAttr, 0)
            try {
                hue = theme.hues.fromAttr(a.getInt(R.styleable.MalletProgressBar_malletHue, 2))
                target = clean(a.getFloat(R.styleable.MalletProgressBar_malletProgressValue, 0f))
                shown = target
                indeterminate = a.getBoolean(R.styleable.MalletProgressBar_malletProgressIndeterminate, false)
                isEnabled = a.getBoolean(R.styleable.MalletProgressBar_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        announceState()
    }

    /**
     * Sets the completed share to [value] (clamped to 0..1). With [animate] the fill glides
     * there; without it, with reduced motion, or while the view is not attached and shown,
     * it jumps.
     * From Java the one-argument `setProgress(float)` is the [progress] setter and jumps.
     */
    public fun setProgress(@FloatRange(from = 0.0, to = 1.0) value: Float, animate: Boolean = true) {
        val next = clean(value)
        if (next == target && progressAnimator == null) return
        target = next
        progressAnimator?.cancel()
        progressAnimator = null
        if (animate && isAttachedToWindow && isShown && !indeterminate) {
            progressAnimator = settle(shown, next) { shown = it }
        } else {
            shown = next
            invalidate()
        }
        announceState()
    }

    private fun clean(value: Float): Float = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)

    // ---------------------------------------------------------------------------------------
    // Measuring and drawing
    // ---------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(DEFAULT_WIDTH_DP).roundToInt()
        val desiredHeight = dp(HEIGHT_DP).roundToInt()
        setMeasuredDimension(
            resolveSize(desiredWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(desiredHeight + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // The groove keeps its 10 dp when the view is taller and stays optically centred:
        // the shadow below it weighs more than the rim above.
        val grooveHeight = dp(GROOVE_DP)
        val spare = height - paddingTop - paddingBottom - dp(HEIGHT_DP)
        // A view squeezed below 22 dp gives up its top gutter first, never the groove.
        val top = paddingTop + max(0f, dp(GUTTER_TOP_DP) + if (spare >= 0f) spare / 2f else spare)
        grooveBounds.set(
            paddingLeft + dp(GUTTER_SIDE_DP),
            top,
            width - paddingRight - dp(GUTTER_SIDE_DP),
            top + grooveHeight,
        )
        if (grooveBounds.width() <= grooveHeight) return

        // The same unglazed recess as the slider: mist in light themes, the plain groove with
        // the neutral rim in dark ones.
        material.drawTrack(canvas, grooveBounds, theme.neutralBed, theme.neutralBedEdge)

        var bed = theme.bedColor(hue)
        var edge = theme.bedEdgeColor(hue)
        val length = grooveBounds.width()
        // Distances from the start edge of the groove.
        val from: Float
        val to: Float
        if (!indeterminate) {
            from = 0f
            to = shown * length
        } else if (theme.reduceMotion) {
            bed = MalletColors.blend(theme.neutralBed, bed, STILL_SHARE)
            edge = MalletColors.blend(theme.neutralBedEdge, edge, STILL_SHARE)
            from = 0f
            to = length
        } else {
            val segment = length * SEGMENT_SHARE
            to = travel() * (length + segment)
            from = to - segment
            if (running) postInvalidateOnAnimation()
        }
        if (to <= 0f || from >= length) return
        drawFill(canvas, from, to, bed, edge)
    }

    /** Paints the glazed stretch between [from] and [to], measured from the start edge. */
    private fun drawFill(canvas: Canvas, from: Float, to: Float, bed: Int, edge: Int) {
        // A stretch shorter than the groove is high keeps its round end and slides in from
        // behind the edge of the groove instead of being squeezed into a sliver.
        val head = to
        val tail = if (indeterminate) from else minOf(from, to - grooveBounds.height())
        if (isRtl) {
            fillBounds.set(grooveBounds.right - head, grooveBounds.top, grooveBounds.right - tail, grooveBounds.bottom)
        } else {
            fillBounds.set(grooveBounds.left + tail, grooveBounds.top, grooveBounds.left + head, grooveBounds.bottom)
        }
        val radius = grooveBounds.height() / 2f
        grooveClip.rewind()
        grooveClip.addRoundRect(grooveBounds, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(grooveClip)
        material.drawTrack(canvas, fillBounds, bed, edge, lifted = false)
        canvas.restoreToCount(save)
    }

    // ---------------------------------------------------------------------------------------
    // Animation: only while attached and visible
    // ---------------------------------------------------------------------------------------

    /** Starts or stops the travelling segment so that it runs exactly while it can be seen. */
    // The segment is driven by the frame clock instead of an endless animator: the next frame
    // is requested only from onDraw, so nothing runs for a view that is not being drawn and
    // nothing can outlive the view.
    private fun updateTravel() {
        val shouldRun = indeterminate && attached && windowVisibility == VISIBLE && isShown &&
            !theme.reduceMotion
        if (shouldRun == running) return
        running = shouldRun
        if (shouldRun) startedAt = AnimationUtils.currentAnimationTimeMillis()
        invalidate()
    }

    /** Position of the travelling segment: 0 = about to enter, 1 = just left. */
    private fun travel(): Float {
        if (!running) return REST_TRAVEL
        val elapsed = AnimationUtils.currentAnimationTimeMillis() - startedAt
        return glide.getInterpolation((elapsed % TRAVEL_MS) / TRAVEL_MS.toFloat())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        updateTravel()
    }

    override fun onDetachedFromWindow() {
        progressAnimator?.cancel()
        progressAnimator = null
        shown = target
        super.onDetachedFromWindow()
        attached = false
        updateTravel()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Also called from the View constructor, before this class is initialised; the
        // attached check inside keeps that call harmless.
        updateTravel()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateTravel()
    }

    // ---------------------------------------------------------------------------------------
    // Accessibility
    // ---------------------------------------------------------------------------------------

    override fun getAccessibilityClassName(): CharSequence = "android.widget.ProgressBar"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (!indeterminate) {
            info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 1f, target,
            )
        }
    }

    /** Spoken state: the percentage, or "Loading" while the amount is unknown. */
    private fun announceState() {
        val state: CharSequence = if (indeterminate) {
            resources.getString(R.string.mallet_state_loading)
        } else {
            NumberFormat.getPercentInstance().format(target.toDouble())
        }
        ViewCompat.setStateDescription(this, state)
    }

    private companion object {
        const val HEIGHT_DP = 22f
        const val GROOVE_DP = 10f
        const val DEFAULT_WIDTH_DP = 160f

        // Gutters of a glazed track: the groove sits high, its shadow falls below it.
        // 4 + 10 + 8 = 22 dp; the shadow needs 6.5 dp of the 8 below.
        const val GUTTER_SIDE_DP = 1.5f
        const val GUTTER_TOP_DP = 4f

        const val SEGMENT_SHARE = 0.3f
        const val TRAVEL_MS = 1400L
        const val STILL_SHARE = 0.4f


        /** Where the segment rests when nothing animates (first frame, snapshots). */
        const val REST_TRAVEL = 0.5f
    }
}
