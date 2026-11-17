package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A placeholder shape that stands where content will appear once it has loaded.
 *
 * The block is a quiet neutral fill, a little darker than the page in a light theme and a
 * little lighter than a card in a dark one, with 6 dp corners; [shape] turns it into a capsule
 * or a circle. While it is attached and visible a soft band of light crosses it from start to
 * end in 1400 ms at a constant speed and then rests for 300 ms.
 *
 * All skeletons in a window share one clock and one sweep: the band is 40 % of the window
 * wide and its place is derived from the frame time and from where each block sits in the
 * window, so a screen full of placeholders shows a single band passing over all of them
 * rather than one band per block. In a right-to-left layout the sweep runs the other way.
 *
 * With reduced motion there is no band; the block breathes between 70 % and 100 % opacity
 * over 1600 ms instead, again in step with every other skeleton.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiSkeleton
 *     android:layout_width="match_parent"
 *     android:layout_height="12dp"
 *     app:sancaiSkeletonShape="capsule" />
 * ```
 *
 * Without a size it is as wide as its parent allows (times [widthFraction]) and 12 dp high,
 * the height of a line of text (times the text scale of the theme); a circle is 40 dp. [text] builds a paragraph of such lines.
 * A skeleton takes no focus and no touches and is hidden from accessibility services: mark
 * the container that is loading as busy instead. A disabled skeleton pales and stands still.
 */
public class SancaiSkeleton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiControl(context, attrs, defStyleAttr) {

    /** The outline of a skeleton. */
    public enum class Shape {
        /** A rectangle with [cornerRadiusDp] corners. */
        BLOCK,

        /** A rectangle whose short sides are half circles. */
        CAPSULE,

        /** A circle as large as the smaller side allows, centred in the view. */
        CIRCLE,
    }

    private var running = false
    private var attached = false
    private val body = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bandMatrix = Matrix()
    private var bandShader: LinearGradient? = null
    private var bandShaderWidth = 0f

    /** The outline of the block. Default [Shape.BLOCK]. */
    public var shape: Shape = Shape.BLOCK
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    /** Corner radius of a [Shape.BLOCK] in dp, limited to half the smaller side. Default 6. */
    public var cornerRadiusDp: Float = RADIUS_DP
        set(value) {
            val radius = if (value.isNaN()) RADIUS_DP else value.coerceAtLeast(0f)
            if (field == radius) return
            field = radius
            invalidate()
        }

    /**
     * The share of the offered width the skeleton takes when its layout width is
     * `wrap_content`, 0.05 to 1. Default 1. This is how the last line of a paragraph is
     * made shorter without knowing the width of the column.
     */
    @get:FloatRange(from = 0.05, to = 1.0)
    public var widthFraction: Float = 1f
        set(value) {
            val fraction = if (value.isNaN()) 1f else value.coerceIn(MIN_FRACTION, 1f)
            if (field == fraction) return
            field = fraction
            requestLayout()
        }

    /**
     * Whether the block moves at all. Default true. False keeps it still at full opacity,
     * for a placeholder that stays on screen without anything being on its way.
     */
    public var isAnimated: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            updateRun()
        }

    /** True while the band travels or the block breathes: animated, enabled, attached and visible. */
    public val isRunning: Boolean get() = running

    /** The resting colour of the block in this theme. */
    @get:ColorInt
    public val fillColor: Int = if (theme.isDark) {
        SancaiColors.blend(theme.colors.surface, theme.colors.ink, FILL_INK_DARK)
    } else {
        SancaiColors.blend(theme.colors.canvas, theme.colors.ink, FILL_INK_LIGHT)
    }

    /**
     * The colour at the middle of the travelling band in this theme. The band is always
     * lighter than the block: towards the raised white in a light theme, and a touch of ink
     * in a dark one, where the raised colour is darker than the block and would dim it.
     */
    @get:ColorInt
    public val bandColor: Int = if (theme.isDark) {
        SancaiColors.blend(fillColor, theme.colors.ink, BAND_INK_DARK)
    } else {
        SancaiColors.blend(fillColor, theme.colors.raised, BAND_RAISED_SHARE)
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiSkeleton, defStyleAttr, 0)
            try {
                val shapes = Shape.entries
                shape = shapes[
                    a.getInt(R.styleable.SancaiSkeleton_sancaiSkeletonShape, shape.ordinal).coerceIn(0, shapes.size - 1),
                ]
                if (a.hasValue(R.styleable.SancaiSkeleton_sancaiCornerRadius)) {
                    cornerRadiusDp =
                        a.getDimension(R.styleable.SancaiSkeleton_sancaiCornerRadius, 0f) / resources.displayMetrics.density
                }
                widthFraction = a.getFloat(R.styleable.SancaiSkeleton_sancaiSkeletonWidthFraction, widthFraction)
                isAnimated = a.getBoolean(R.styleable.SancaiSkeleton_sancaiSkeletonAnimated, true)
                isEnabled = a.getBoolean(R.styleable.SancaiSkeleton_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val circle = shape == Shape.CIRCLE
        val horizontal = paddingLeft + paddingRight
        val vertical = paddingTop + paddingBottom
        // A line stands for a line of text, so it grows with the text scale of the theme.
        val wantedHeight = dp(if (circle) CIRCLE_DP else LINE_HEIGHT_DP * theme.textScale).roundToInt() + vertical
        val height = resolveSize(wantedHeight, heightMeasureSpec)
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> available
            // A circle is as wide as it is high; anything else takes its share of the room.
            MeasureSpec.AT_MOST ->
                if (circle) min(height - vertical + horizontal, available) else (available * widthFraction).roundToInt()
            else -> (if (circle) height - vertical else dp(UNBOUNDED_WIDTH_DP).roundToInt()) + horizontal
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        body.set(
            paddingLeft.toFloat(), paddingTop.toFloat(),
            (width - paddingRight).toFloat(), (height - paddingBottom).toFloat(),
        )
        if (body.width() <= 0f || body.height() <= 0f) return
        val half = min(body.width(), body.height()) / 2f
        val radius = when (shape) {
            Shape.BLOCK -> min(dp(cornerRadiusDp), half)
            Shape.CAPSULE -> half
            Shape.CIRCLE -> {
                body.set(body.centerX() - half, body.centerY() - half, body.centerX() + half, body.centerY() + half)
                half
            }
        }
        val now = AnimationUtils.currentAnimationTimeMillis()
        val breathing = running && theme.reduceMotion
        fillPaint.color = fillColor
        if (breathing) fillPaint.alpha = (breathAlpha(now) * 255f).roundToInt()
        canvas.drawRoundRect(body, radius, radius, fillPaint)
        if (!running) return
        if (breathing) {
            postInvalidateOnAnimation()
            return
        }
        val phase = now % CYCLE_MS
        if (phase >= TRAVEL_MS) {
            // The band has left the window: nothing changes until the next sweep starts.
            postInvalidateDelayed(CYCLE_MS - phase)
            return
        }
        val sweep = sweepWidth()
        val band = sweep * BAND_SHARE
        val travelled = -band + (sweep + band) * phase / TRAVEL_MS
        // Left edge of the band in the window; a right-to-left sweep is the mirror image.
        val bandLeft = if (isRtl) sweep - travelled - band else travelled
        val local = bandLeft - offsetInRoot()
        if (local < body.right && local + band > body.left) {
            bandMatrix.setTranslate(local, 0f)
            val shader = bandShader(band)
            shader.setLocalMatrix(bandMatrix)
            bandPaint.shader = shader
            canvas.drawRoundRect(body, radius, radius, bandPaint)
        }
        postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------------------------------------
    // One sweep per window
    // ---------------------------------------------------------------------------------------

    /** Width the band crosses: the window, or this view alone while it has no parent. */
    private fun sweepWidth(): Float {
        val root = rootView
        return (if (root != null && root.width > 0) root.width else width).toFloat()
    }

    /**
     * Left edge of this view in its root. Walks the parents instead of asking for the
     * location in the window, which is only known to an attached view.
     */
    private fun offsetInRoot(): Float {
        var x = left + translationX
        var parent = parent
        while (parent is View) {
            x += parent.left + parent.translationX - parent.scrollX
            parent = parent.parent
        }
        // The root's own position is not part of the sweep.
        val root = rootView
        return if (root != null && root !== this) x - root.left - root.translationX else 0f
    }

    private fun bandShader(band: Float): LinearGradient {
        bandShader?.let { if (bandShaderWidth == band) return it }
        // A raised cosine: no edge anywhere, full band colour only in the very middle.
        val colors = IntArray(BAND_STOPS) { index ->
            val position = index / (BAND_STOPS - 1f)
            val strength = (1f - cos(2.0 * PI * position).toFloat()) / 2f
            SancaiColors.withAlpha(bandColor, (strength * 255f).roundToInt())
        }
        return LinearGradient(0f, 0f, band, 0f, colors, null, Shader.TileMode.CLAMP).also {
            bandShader = it
            bandShaderWidth = band
        }
    }

    /** Opacity of a breathing block at [now]: 1 at the start of each period, 0.7 half-way. */
    private fun breathAlpha(now: Long): Float {
        val position = (now % BREATH_MS) / BREATH_MS.toFloat()
        val swing = (1f + cos(2.0 * PI * position).toFloat()) / 2f
        return BREATH_LOW + (1f - BREATH_LOW) * swing
    }

    // ---------------------------------------------------------------------------------------
    // Running: only while attached and visible
    // ---------------------------------------------------------------------------------------

    // There is no animator: the block asks for its next frame from onDraw alone, so nothing
    // runs for a view that is not being drawn and nothing can outlive the view.
    private fun updateRun() {
        val shouldRun = attached && isAnimated && isEnabled && windowVisibility == VISIBLE && isShown
        if (shouldRun == running) return
        running = shouldRun
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        updateRun()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        attached = false
        updateRun()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Also reached from the View constructor, where nothing is attached yet.
        updateRun()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateRun()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        updateRun()
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        invalidate()
    }

    override fun getAccessibilityClassName(): CharSequence = "android.view.View"

    public companion object {
        /**
         * Builds the placeholder of a paragraph: [lines] capsule lines, 12 dp high and 8 dp
         * apart (both times the text scale of the theme), in a vertical column. Every line fills the width of the column except the
         * last, which takes [lastLineFraction] of it and hugs the start edge, the way a
         * paragraph ends. The column is hidden from accessibility services as a whole.
         *
         * Give the returned view the width of the text it stands for (usually
         * `MATCH_PARENT`); its height follows from the number of lines.
         */
        @JvmStatic
        @JvmOverloads
        public fun text(
            context: Context,
            @IntRange(from = 1) lines: Int = 3,
            @FloatRange(from = 0.05, to = 1.0) lastLineFraction: Float = LAST_LINE_FRACTION,
        ): LinearLayout {
            val column = LinearLayout(context)
            column.orientation = LinearLayout.VERTICAL
            column.gravity = Gravity.START
            column.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            val scale = SancaiTheme.of(context).textScale
            val gap = (LINE_GAP_DP * scale * context.resources.displayMetrics.density).roundToInt()
            val count = lines.coerceAtLeast(1)
            for (index in 0 until count) {
                val line = SancaiSkeleton(context)
                line.shape = Shape.CAPSULE
                if (index == count - 1) line.widthFraction = lastLineFraction
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                if (index > 0) params.topMargin = gap
                column.addView(line, params)
            }
            return column
        }

        private const val RADIUS_DP = 6f
        private const val LINE_HEIGHT_DP = 12f
        private const val LINE_GAP_DP = 8f
        private const val LAST_LINE_FRACTION = 0.6f
        private const val CIRCLE_DP = 40f
        private const val UNBOUNDED_WIDTH_DP = 120f
        private const val MIN_FRACTION = 0.05f

        /** Share of ink in the fill: over the canvas in light, over the card in dark. */
        private const val FILL_INK_LIGHT = 0.06f
        private const val FILL_INK_DARK = 0.08f
        private const val BAND_RAISED_SHARE = 0.6f
        private const val BAND_INK_DARK = 0.07f

        /** Width of the band as a share of the width it crosses. */
        private const val BAND_SHARE = 0.4f
        private const val BAND_STOPS = 17
        private const val TRAVEL_MS = 1400L
        private const val PAUSE_MS = 300L
        private const val CYCLE_MS = TRAVEL_MS + PAUSE_MS
        private const val BREATH_MS = 1600L
        private const val BREATH_LOW = 0.7f
    }
}
