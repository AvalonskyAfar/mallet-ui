package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.roundToInt

/**
 * A note beside running text: a thin bar in the tone colour at the start, then a title in the
 * same colour over a body in ink. It has no card and no background, so it reads as part of the
 * page rather than as a control.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiCallout
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiTitle="What stays on the device"
 *     app:sancaiDetail="Recognition runs locally. Nothing on the screen is uploaded."
 *     app:sancaiTone="accent" />
 * ```
 *
 * The bar always spans the full height of the view, padding included, and sits at the start
 * edge (the right edge in right-to-left layouts). A missing title or body takes no room.
 * Extra views added in XML or with `addView` stack below the body, beside the bar.
 * A screen reader reads the callout as one element.
 */
public class SancaiCallout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val density: Float = resources.displayMetrics.density
    private val barPaint = Paint()
    private val barWidth: Int = (BAR_WIDTH_DP * density).roundToInt()

    /** The view that shows [title]; exposed for fine tuning (max lines, text selection). */
    public val titleView: SancaiText =
        SancaiText.create(context, null, SancaiTextRole.SUBHEAD, SancaiTextTone.ACCENT)

    /** The view that shows [body]. */
    public val bodyView: SancaiText =
        SancaiText.create(context, null, SancaiTextRole.BODY_COMPACT, SancaiTextTone.INK)

    /** Start padding asked for by the user; the bar sits after it and the content after the bar. */
    private var userPaddingStart = 0
    private var ready = false

    /** The title, in the tone colour. `null` or empty removes the line. */
    public var title: CharSequence?
        get() = titleView.text.takeIf { it.isNotEmpty() }
        set(value) {
            titleView.text = value
            syncVisibility()
        }

    /** The body, in ink, 7 dp below the title. `null` or empty removes it. */
    public var body: CharSequence?
        get() = bodyView.text.takeIf { it.isNotEmpty() }
        set(value) {
            bodyView.text = value
            syncVisibility()
        }

    /**
     * Colour of the bar and of the title: [SancaiTextTone.ACCENT] (default) for a plain note,
     * `DANGER`, `WARNING` or `SUCCESS` for a note with a verdict. `INK` and `MUTED` are accepted
     * and give a quiet, colourless aside. The body always stays in ink.
     */
    public var tone: SancaiTextTone = SancaiTextTone.ACCENT
        set(value) {
            field = value
            titleView.tone = value
            barPaint.color = value.resolve(theme.colors)
            invalidate()
        }

    /** The colour the bar is painted with right now. */
    public val barColor: Int
        get() = barPaint.color

    init {
        orientation = VERTICAL
        gravity = Gravity.START or Gravity.TOP
        setWillNotDraw(false)
        titleView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        bodyView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(bodyView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        var initialTone = SancaiTextTone.ACCENT
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiCallout, defStyleAttr, 0)
            try {
                titleView.text = a.getText(R.styleable.SancaiCallout_sancaiTitle)
                bodyView.text = a.getText(R.styleable.SancaiCallout_sancaiDetail)
                initialTone = TONES.getOrElse(
                    a.getInt(R.styleable.SancaiCallout_sancaiTone, initialTone.ordinal),
                ) { initialTone }
            } finally {
                a.recycle()
            }
        }
        tone = initialTone
        syncVisibility()
        val start = paddingStart
        val end = paddingEnd
        ready = true
        setPaddingRelative(start, paddingTop, end, paddingBottom)
        ViewCompat.setScreenReaderFocusable(this, true)
    }

    /**
     * Sets the padding around the note. The start padding lies outside the bar; the bar and
     * its 14 dp gap are added to it, so [getPaddingStart] reports 17 dp more than was set.
     */
    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        if (!ready) {
            super.setPaddingRelative(start, top, end, bottom)
            return
        }
        userPaddingStart = start
        super.setPaddingRelative(start + contentInset(), top, end, bottom)
    }

    /** Like [setPaddingRelative], with `left` and `right` read in the current layout direction. */
    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        if (!ready) {
            super.setPadding(left, top, right, bottom)
            return
        }
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        setPaddingRelative(if (rtl) right else left, top, if (rtl) left else right, bottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            width - userPaddingStart - barWidth
        } else {
            userPaddingStart
        }
        canvas.drawRect(left.toFloat(), 0f, (left + barWidth).toFloat(), height.toFloat(), barPaint)
    }

    override fun getAccessibilityClassName(): CharSequence = SancaiCallout::class.java.name

    private fun contentInset(): Int = barWidth + (BAR_GAP_DP * density).roundToInt()

    private fun syncVisibility() {
        val hasTitle = titleView.text.isNotEmpty()
        val hasBody = bodyView.text.isNotEmpty()
        titleView.visibility = if (hasTitle) VISIBLE else GONE
        bodyView.visibility = if (hasBody) VISIBLE else GONE
        val params = bodyView.layoutParams as LayoutParams
        val gap = if (hasTitle) (BODY_GAP_DP * density).roundToInt() else 0
        if (params.topMargin != gap) {
            params.topMargin = gap
            bodyView.layoutParams = params
        }
    }
}

private val TONES = SancaiTextTone.values()

private const val BAR_WIDTH_DP = 3f
private const val BAR_GAP_DP = 14f
private const val BODY_GAP_DP = 7f
