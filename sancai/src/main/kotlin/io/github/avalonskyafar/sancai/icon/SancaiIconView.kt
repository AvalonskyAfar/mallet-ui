package io.github.avalonskyafar.sancai.icon

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shows one [SancaiIcon].
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.icon.SancaiIconView
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     app:sancaiIcon="shield"
 *     app:sancaiTone="accent" />
 * ```
 *
 * The icon fills the smaller side of the content box; `wrap_content` measures 24 dp plus
 * padding. Directional icons mirror in right-to-left layouts. The view is decorative unless
 * you give it a content description.
 */
public class SancaiIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val density = resources.displayMetrics.density

    /** The icon to draw, or `null` for nothing. */
    public var icon: SancaiIcon? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Colour role of the stroke, used when [color] is `null`. */
    public var tone: SancaiTextTone = SancaiTextTone.MUTED
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Explicit stroke colour; overrides [tone] when set. */
    @get:ColorInt
    @setparam:ColorInt
    public var color: Int? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Stroke width in dp at the drawn size. */
    public var strokeDp: Float = SancaiIconPainter.STROKE_DP
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiIconView, defStyleAttr, 0)
            try {
                icon = SancaiIcon.fromAttr(a.getInt(R.styleable.SancaiIconView_sancaiIcon, -1))
                tone = TONES.getOrElse(a.getInt(R.styleable.SancaiIconView_sancaiTone, SancaiTextTone.MUTED.ordinal)) {
                    SancaiTextTone.MUTED
                }
                if (a.hasValue(R.styleable.SancaiIconView_sancaiIconStroke)) {
                    strokeDp = a.getDimension(R.styleable.SancaiIconView_sancaiIconStroke, 0f) / density
                }
            } finally {
                a.recycle()
            }
        }
        if (contentDescription == null) importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun setContentDescription(contentDescription: CharSequence?) {
        super.setContentDescription(contentDescription)
        importantForAccessibility = if (contentDescription == null) {
            IMPORTANT_FOR_ACCESSIBILITY_NO
        } else {
            IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = (DEFAULT_SIZE_DP * density).roundToInt()
        setMeasuredDimension(
            resolveSize(side + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(side + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val drawn = icon ?: return
        val boxWidth = (width - paddingLeft - paddingRight).toFloat()
        val boxHeight = (height - paddingTop - paddingBottom).toFloat()
        val side = min(boxWidth, boxHeight)
        if (side <= 0f) return
        SancaiIconPainter.draw(
            canvas,
            drawn,
            paddingLeft + (boxWidth - side) / 2f,
            paddingTop + (boxHeight - side) / 2f,
            side,
            color ?: tone.resolve(theme.colors),
            strokeDp * density,
            layoutDirection == LAYOUT_DIRECTION_RTL,
        )
    }

    public companion object {
        /** Size, in dp, that `wrap_content` resolves to. */
        public const val DEFAULT_SIZE_DP: Float = 24f

        private val TONES = SancaiTextTone.values()
    }
}
