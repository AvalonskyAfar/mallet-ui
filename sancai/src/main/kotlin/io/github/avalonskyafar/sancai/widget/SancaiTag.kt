package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A status tag: a miniature recessed glazed capsule with a short bold label.
 *
 * The tag says what state something is in ("Active", "Expired", "Draft") and nothing else. It
 * is 22 dp high, has no outer shadow, cannot be pressed and takes no focus. Its colour is the
 * state, and it lives in the bed: each [Tone] maps to one glaze of the theme.
 *
 * | Tone | Glaze | Use |
 * |---|---|---|
 * | [Tone.POSITIVE] | celadon | done, healthy, allowed |
 * | [Tone.CAUTION] | ochre | pending, needs attention |
 * | [Tone.NEGATIVE] | terracotta | failed, blocked, off |
 * | [Tone.INFO] | cobalt | running, selected, new |
 * | [Tone.NEUTRAL] | mist | plain labels without a judgement |
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiTag
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     android:text="Active"
 *     app:sancaiTagTone="positive" />
 * ```
 */
public class SancaiTag @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** What the tag says about its subject; each tone is one glaze of the theme. */
    public enum class Tone { POSITIVE, CAUTION, NEGATIVE, INFO, NEUTRAL }

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val material = SancaiMaterial(context, theme)
    private val density = resources.displayMetrics.density
    private val bounds = RectF()
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = SancaiWeight.BOLD.typeface()
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, TEXT_SP, resources.displayMetrics,
        ) * theme.textScale
    }
    private var shown: CharSequence = ""

    /** The label. Keep it to a word or two; a label wider than the tag is ellipsized. */
    public var text: CharSequence = ""
        set(value) {
            if (TextUtils.equals(field, value)) return
            field = value
            requestLayout()
            invalidate()
        }

    /** What the tag says about its subject. Decides the glaze of the bed. */
    public var tone: Tone = Tone.NEUTRAL
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** The glaze [tone] maps to in the theme of this view. */
    public val glaze: SancaiGlaze
        get() = when (tone) {
            Tone.POSITIVE -> theme.glazes.celadon
            Tone.CAUTION -> theme.glazes.ochre
            Tone.NEGATIVE -> theme.glazes.terracotta
            Tone.INFO -> theme.glazes.cobalt
            Tone.NEUTRAL -> theme.glazes.mist
        }

    init {
        // A plain view without actions is skipped by screen readers unless it says otherwise.
        if (importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiTag, defStyleAttr, 0)
            try {
                text = a.getText(R.styleable.SancaiTag_android_text) ?: ""
                tone = TONES.getOrElse(a.getInt(R.styleable.SancaiTag_sancaiTagTone, Tone.NEUTRAL.ordinal)) { Tone.NEUTRAL }
            } finally {
                a.recycle()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val metrics = paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val wanted = ceil(paint.measureText(text, 0, text.length) + 2f * PADDING_DP * density).toInt() +
            paddingLeft + paddingRight
        // A capsule is never narrower than it is high, and grows with large text.
        val high = maxOf((HEIGHT_DP * density).roundToInt(), ceil(textHeight + 2f * MIN_INSET_DP * density).toInt())
        val width = maxOf(wanted, high + paddingLeft + paddingRight, suggestedMinimumWidth)
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(maxOf(high + paddingTop + paddingBottom, suggestedMinimumHeight), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val high = minOf(
            (height - paddingTop - paddingBottom).toFloat(),
            maxOf(HEIGHT_DP * density, paint.fontMetrics.let { it.descent - it.ascent } + 2f * MIN_INSET_DP * density),
        )
        val top = paddingTop + (height - paddingTop - paddingBottom - high) / 2f
        bounds.set(paddingLeft.toFloat(), top, (width - paddingRight).toFloat(), top + high)
        if (bounds.width() <= 0f || high <= 0f) return
        val glaze = glaze
        // Recessed, and flush with whatever it lies on: the bed and its rim, no outer shadow.
        material.drawTrack(canvas, bounds, theme.bedColor(glaze), theme.bedEdgeColor(glaze), lifted = false)
        val room = bounds.width() - 2f * PADDING_DP * density
        if (room <= 0f || text.isEmpty()) return
        shown = TextUtils.ellipsize(text, paint, room + 0.5f, TextUtils.TruncateAt.END)
        paint.color = theme.labelOnBed(glaze)
        val metrics = paint.fontMetrics
        val baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(shown, 0, shown.length, bounds.centerX(), baseline, paint)
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.TextView"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        // The colour is not announced: the label has to carry the meaning on its own.
        info.text = text
    }

    private companion object {
        private const val HEIGHT_DP = 22f
        private const val PADDING_DP = 9f
        private const val TEXT_SP = 11.5f

        /** Least room above and below the label when large text makes the tag grow. */
        private const val MIN_INSET_DP = 3f
        private val TONES = Tone.values()
    }
}
