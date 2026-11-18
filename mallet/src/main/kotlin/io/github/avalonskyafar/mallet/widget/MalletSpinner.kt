package io.github.avalonskyafar.mallet.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AnimationUtils
import androidx.annotation.ColorInt
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * An open ring that turns while something is going on and its amount is unknown.
 *
 * The ring is the same 276 degree arc as the power icon and the loading state of
 * [MalletButton], drawn with a 2 dp round-capped stroke and turning once every 900 ms at a
 * constant speed. With reduced motion it turns at half speed rather than standing still,
 * because a spinner that does not move reads as a stalled app.
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.widget.MalletSpinner
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     app:malletTone="accent" />
 * ```
 *
 * The view is 28 dp square by default and the ring scales with it. It turns only while it is
 * attached and visible, takes neither focus nor touches, and announces itself as "Loading"
 * unless a content description is set.
 */
public class MalletSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MalletControl(context, attrs, defStyleAttr) {

    private var explicitColor: Int? = null
    private var restAngle = 0f
    private var startedAt = 0L
    private var running = false
    private var attached = false
    private val ringBounds = RectF()
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** The tone of the ring. Default muted. Setting it drops an explicit [color]. */
    public var tone: MalletTextTone = MalletTextTone.MUTED
        set(value) {
            field = value
            explicitColor = null
            invalidate()
        }

    /** The colour of the ring. Setting it overrides [tone] until [tone] is set again. */
    @get:ColorInt
    public var color: Int
        get() = explicitColor ?: tone.resolve(theme.colors)
        set(@ColorInt value) {
            explicitColor = value
            invalidate()
        }

    /** True while the ring is actually turning: attached, visible and shown. */
    public val isSpinning: Boolean get() = running

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletSpinner, defStyleAttr, 0)
            try {
                val tones = MalletTextTone.entries
                tone = tones[a.getInt(R.styleable.MalletSpinner_malletTone, tone.ordinal).coerceIn(0, tones.size - 1)]
                if (a.hasValue(R.styleable.MalletSpinner_malletProgressSpinnerColor)) {
                    color = a.getColor(R.styleable.MalletSpinner_malletProgressSpinnerColor, 0)
                }
                isEnabled = a.getBoolean(R.styleable.MalletSpinner_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        if (contentDescription == null) contentDescription = resources.getString(R.string.mallet_state_loading)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = dp(SIZE_DP).roundToInt()
        setMeasuredDimension(
            resolveSize(size + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(size + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val box = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (box <= 0f) return
        // The ring scales with the view; the stroke stays 2 dp up to the default size and
        // only then grows, so a large spinner does not look wiry.
        val scale = box / dp(SIZE_DP)
        val stroke = dp(STROKE_DP) * maxOf(1f, scale)
        val radius = min(box * RING_SHARE / 2f, (box - stroke) / 2f)
        if (radius <= 0f) return
        val centreX = (paddingLeft + width - paddingRight) / 2f
        val centreY = (paddingTop + height - paddingBottom) / 2f
        ringBounds.set(centreX - radius, centreY - radius, centreX + radius, centreY + radius)
        ringPaint.color = color
        ringPaint.strokeWidth = stroke
        canvas.drawArc(ringBounds, REST_ANGLE + currentAngle(), SWEEP, false, ringPaint)
        if (running) postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------------------------------------
    // Turning: only while attached and visible
    // ---------------------------------------------------------------------------------------

    // The ring is driven by the frame clock instead of an animator: it asks for the next
    // frame only from onDraw, so nothing runs for a view that is not being drawn and nothing
    // can outlive the view.
    private fun updateSpin() {
        val shouldRun = attached && windowVisibility == VISIBLE && isShown
        if (shouldRun == running) return
        if (shouldRun) {
            startedAt = AnimationUtils.currentAnimationTimeMillis()
        } else {
            // Keep the angle so the ring resumes where it stopped.
            restAngle = currentAngle()
        }
        running = shouldRun
        invalidate()
    }

    private fun currentAngle(): Float {
        if (!running) return restAngle
        val turn = if (theme.reduceMotion) TURN_MS * 2 else TURN_MS
        val elapsed = AnimationUtils.currentAnimationTimeMillis() - startedAt
        return (restAngle + (elapsed % turn) * 360f / turn) % 360f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        updateSpin()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        attached = false
        updateSpin()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Also reached from the View constructor, where nothing is attached yet.
        updateSpin()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateSpin()
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.ProgressBar"

    private companion object {
        const val SIZE_DP = 28f
        const val STROKE_DP = 2f

        /** Ring diameter as a share of the view: 20 dp in the default 28 dp box. */
        const val RING_SHARE = 20f / 28f
        const val SWEEP = 276f

        /** At rest the opening of the ring points straight up, as in the power icon. */
        const val REST_ANGLE = -90f + (360f - SWEEP) / 2f
        const val TURN_MS = 900L
    }
}
