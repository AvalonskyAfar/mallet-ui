package io.github.avalonskyafar.sancai.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconPainter
import io.github.avalonskyafar.sancai.icon.SancaiIconView
import io.github.avalonskyafar.sancai.interaction.SancaiPress
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.roundToInt

/**
 * A persistent inline message at the top of a page or a card. It reports a *condition* that
 * lasts (offline, a permission is missing, a backup is overdue); a passing *event* belongs in
 * a toast.
 *
 * The body is flat porcelain (a rim, no shadow) with a thin bar in the tone colour at the
 * start, an icon in the same colour, a title over a muted detail, up to two text actions at
 * the end of the text column and an optional close icon. Text stays neutral: the tone lives in
 * the bar and the icon only.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiBanner
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiBannerTone="warning"
 *     app:sancaiTitle="Accessibility service is off"
 *     app:sancaiDetail="Nothing is being filtered until it is switched back on."
 *     app:sancaiBannerAction="Open settings"
 *     app:sancaiBannerDismissible="true" />
 * ```
 *
 * The banner appears by growing its height from zero and leaves the same way, so it pushes
 * the content below it instead of covering it. Give it `wrap_content` height. When it has
 * fully collapsed its visibility becomes `GONE`; [show] makes it `VISIBLE` again. Whether it
 * is showing survives a configuration change. The platform's `isShown()` keeps its usual
 * meaning; ask [isShowing] for the banner's own state.
 *
 * A screen reader hears the tone ("Warning") with the title, and the banner is a live region,
 * so a banner that appears is announced without stealing focus. The actions and the close icon
 * are ordinary focus stops for the keyboard.
 */
public class SancaiBanner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** What kind of condition a banner reports; picks the colour of the bar and the icon. */
    public enum class Tone {
        /** Neutral information, in the accent colour. */
        INFO,

        /** Something needs attention soon. */
        WARNING,

        /** Something is broken or blocked. */
        DANGER,

        /** A condition that is fine and worth saying so. */
        SUCCESS,
    }

    /** Called when the user closes the banner with its close icon. */
    public fun interface OnDismissListener {
        /** [banner] has started to collapse because the user closed it. */
        public fun onDismiss(banner: SancaiBanner)
    }

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val material = SancaiMaterial(context, theme)
    private val density: Float = resources.displayMetrics.density
    private val barPaint = Paint()
    private val body = RectF()
    private val clip = Path()
    private val radius: Float = SancaiMaterial.RADIUS_CARD_DP * density

    private val row = LinearLayout(context)
    private val column = LinearLayout(context)
    private val actions = ActionsRow(context)
    private val iconView = SancaiIconView(context)
    private val closeView = CloseView(context)

    /** The view that shows [title]; exposed for fine tuning (max lines, ellipsis). */
    public val titleView: SancaiText =
        SancaiText.create(context, null, SancaiTextRole.SUBHEAD, SancaiTextTone.INK)

    /** The view that shows [detail]. */
    public val detailView: SancaiText =
        SancaiText.create(context, null, SancaiTextRole.CAPTION, SancaiTextTone.MUTED)

    /** The main text action, at the very end of the text column. Hidden until [setAction]. */
    public val actionButton: SancaiButton = textButton()

    /** The second text action, before the main one. Hidden until [setSecondaryAction]. */
    public val secondaryActionButton: SancaiButton = textButton()

    /** 0 = collapsed, 1 = fully grown; the measured height is the content height times this. */
    private var reveal = 1f
    private var showing = true
    private var animator: ValueAnimator? = null
    private var customIcon: SancaiIcon? = null

    /** Called when the user closes the banner. Not called by [dismiss]. */
    public var onDismissListener: OnDismissListener? = null

    /** The title, in ink. `null` or empty removes the line. */
    public var title: CharSequence?
        get() = titleView.text.takeIf { it.isNotEmpty() }
        set(value) {
            titleView.text = value
            syncContent()
        }

    /** The muted line under the title. `null` or empty removes it. */
    public var detail: CharSequence?
        get() = detailView.text.takeIf { it.isNotEmpty() }
        set(value) {
            detailView.text = value
            syncContent()
        }

    /** Colour of the bar and the icon, and the icon itself unless [icon] replaces it. */
    public var tone: Tone = Tone.INFO
        set(value) {
            field = value
            applyTone()
        }

    /** Replaces the icon the [tone] brings; `null` (default) goes back to the tone's own. */
    public var icon: SancaiIcon?
        get() = customIcon
        set(value) {
            customIcon = value
            applyTone()
        }
    /** Whether the tone icon is drawn. Without it the text starts right after the bar. */
    public var showIcon: Boolean = true
        set(value) {
            field = value
            syncContent()
        }

    /** Whether the close icon is offered. Closing collapses the banner and tells [onDismissListener]. */
    public var isDismissible: Boolean = false
        set(value) {
            field = value
            syncContent()
        }

    /**
     * What a screen reader says before the title: by default the name of the [tone]
     * ("Warning"). Set an empty string to have the title read alone.
     */
    public var toneDescription: CharSequence? = null
        set(value) {
            field = value
            syncDescription()
        }

    /** Description of the close icon for screen readers; "Close" by default. */
    public var closeDescription: CharSequence
        get() = closeView.contentDescription
        set(value) {
            closeView.contentDescription = value
        }

    /** The colour the bar and the icon are painted with right now. */
    @get:ColorInt
    public val toneColor: Int
        get() = barPaint.color

    /**
     * Whether the banner is showing or on its way in. `false` from the moment [dismiss] is
     * called, while it is still collapsing.
     */
    public val isShowing: Boolean
        get() = showing

    init {
        setWillNotDraw(false)
        clipChildren = true
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.TOP
        row.clipChildren = false
        column.orientation = LinearLayout.VERTICAL
        column.clipChildren = false
        actions.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        titleView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        detailView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        column.addView(titleView, LinearLayout.LayoutParams(match, wrap))
        column.addView(detailView, LinearLayout.LayoutParams(match, wrap))
        actions.addView(secondaryActionButton, LinearLayout.LayoutParams(wrap, wrap))
        actions.addView(actionButton, LinearLayout.LayoutParams(wrap, wrap))
        column.addView(actions, LinearLayout.LayoutParams(match, wrap))

        val iconSide = px(ICON_DP)
        row.addView(iconView, LinearLayout.LayoutParams(iconSide, iconSide))
        row.addView(column, LinearLayout.LayoutParams(0, wrap, 1f))
        val target = px(CLOSE_TARGET_DP)
        row.addView(closeView, LinearLayout.LayoutParams(target, target))
        addView(row, LayoutParams(match, wrap))

        closeView.contentDescription = context.getString(R.string.sancai_action_close)
        SancaiPress.attach(closeView) { closeByUser() }
        var initialTone = Tone.INFO
        var shownAtStart = true
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiBanner, defStyleAttr, 0)
            try {
                titleView.text = a.getText(R.styleable.SancaiBanner_sancaiTitle)
                detailView.text = a.getText(R.styleable.SancaiBanner_sancaiDetail)
                customIcon = SancaiIcon.fromAttr(a.getInt(R.styleable.SancaiBanner_sancaiIcon, -1))
                initialTone = TONES.getOrElse(a.getInt(R.styleable.SancaiBanner_sancaiBannerTone, 0)) { Tone.INFO }
                setAction(a.getText(R.styleable.SancaiBanner_sancaiBannerAction), null)
                setSecondaryAction(a.getText(R.styleable.SancaiBanner_sancaiBannerSecondaryAction), null)
                isDismissible = a.getBoolean(R.styleable.SancaiBanner_sancaiBannerDismissible, false)
                showIcon = a.getBoolean(R.styleable.SancaiBanner_sancaiBannerShowIcon, true)
                shownAtStart = a.getBoolean(R.styleable.SancaiBanner_sancaiBannerShown, true)
            } finally {
                a.recycle()
            }
        }
        tone = initialTone
        syncContent()
        ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        if (!shownAtStart) jumpTo(false)
    }

    // ---------------------------------------------------------------------------------------
    // Content
    // ---------------------------------------------------------------------------------------

    /**
     * Sets the main text action. A `null` or empty [text] removes it. The click ticks first
     * and then calls [listener], like every Sancai action.
     */
    public fun setAction(text: CharSequence?, listener: OnClickListener?) {
        bind(actionButton, text, listener)
    }

    /** Sets the second text action, placed before the main one. `null` or empty removes it. */
    public fun setSecondaryAction(text: CharSequence?, listener: OnClickListener?) {
        bind(secondaryActionButton, text, listener)
    }

    private fun bind(button: SancaiButton, text: CharSequence?, listener: OnClickListener?) {
        button.text = text
        button.setOnClickListener(listener)
        button.visibility = if (text.isNullOrEmpty()) GONE else VISIBLE
        syncContent()
    }

    private fun textButton(): SancaiButton = SancaiButton(context).also {
        it.variant = SancaiButton.Variant.TEXT
        it.tone = SancaiTextTone.ACCENT
        it.visibility = GONE
        val pad = px(ACTION_PADDING_DP)
        it.setPadding(pad, pad, pad, pad)
        it.minimumHeight = px(CLOSE_TARGET_DP)
        it.minimumWidth = px(CLOSE_TARGET_DP)
    }

    private fun applyTone() {
        val colors = theme.colors
        val color = when (tone) {
            Tone.INFO -> colors.accent
            Tone.WARNING -> colors.warning
            Tone.DANGER -> colors.danger
            Tone.SUCCESS -> colors.success
        }
        barPaint.color = color
        iconView.color = color
        iconView.icon = customIcon ?: when (tone) {
            Tone.INFO -> SancaiIcon.INFO
            Tone.WARNING -> SancaiIcon.WARNING
            Tone.DANGER -> SancaiIcon.ERROR
            Tone.SUCCESS -> SancaiIcon.SUCCESS
        }
        syncDescription()
        invalidate()
    }
    /** Collapses the parts that are empty and sets the spacing between those that remain. */
    private fun syncContent() {
        val hasTitle = titleView.text.isNotEmpty()
        val hasDetail = detailView.text.isNotEmpty()
        val hasActions = actionButton.visibility == VISIBLE || secondaryActionButton.visibility == VISIBLE
        titleView.visibility = if (hasTitle) VISIBLE else GONE
        detailView.visibility = if (hasDetail) VISIBLE else GONE
        actions.visibility = if (hasActions) VISIBLE else GONE
        iconView.visibility = if (showIcon) VISIBLE else GONE
        closeView.visibility = if (isDismissible) VISIBLE else GONE

        (detailView.layoutParams as LinearLayout.LayoutParams).topMargin =
            if (hasTitle) px(DETAIL_GAP_DP) else 0
        (column.layoutParams as LinearLayout.LayoutParams).marginStart =
            if (showIcon) px(ICON_GAP_DP) else 0
        // The buttons carry their own 12 dp of padding: pull the row so that the label of the
        // last action ends where the text column ends, and so that the padding is not added
        // to the gap above and to the padding below.
        (actions.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = if (hasTitle || hasDetail) -px(ACTION_PADDING_DP - ACTION_GAP_DP) else -px(ACTION_PADDING_DP)
            marginEnd = -px(ACTION_PADDING_DP)
        }
        // The close target is a 44 dp square whose glyph sits where the padding would put it.
        (closeView.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = -px(PADDING_VERTICAL_DP)
            marginEnd = -px(PADDING_END_DP)
            bottomMargin = -px(PADDING_VERTICAL_DP)
        }
        row.setPaddingRelative(
            px(BAR_WIDTH_DP + PADDING_START_DP),
            px(PADDING_VERTICAL_DP),
            px(PADDING_END_DP),
            if (hasActions) px(PADDING_VERTICAL_DP - ACTION_PADDING_DP + ACTION_BOTTOM_DP) else px(PADDING_VERTICAL_DP),
        )
        syncDescription()
        row.requestLayout()
    }

    private fun syncDescription() {
        val prefix = toneDescription ?: context.getString(
            when (tone) {
                Tone.INFO -> R.string.sancai_banner_tone_info
                Tone.WARNING -> R.string.sancai_banner_tone_warning
                Tone.DANGER -> R.string.sancai_banner_tone_danger
                Tone.SUCCESS -> R.string.sancai_banner_tone_success
            },
        )
        val first = if (titleView.text.isNotEmpty()) titleView else detailView
        val other = if (first === titleView) detailView else titleView
        other.contentDescription = null
        first.contentDescription =
            if (prefix.isEmpty() || first.text.isEmpty()) null else "$prefix, ${first.text}"
    }

    private fun px(dp: Float): Int = (dp * density).roundToInt()
    // ---------------------------------------------------------------------------------------
    // Showing and dismissing
    // ---------------------------------------------------------------------------------------

    /**
     * Shows the banner by growing its height from where it is to its full height, pushing the
     * content below it. With [animate] off, with reduced motion handled by the shorter
     * duration, or before the first layout, it simply appears.
     */
    @JvmOverloads
    public fun show(animate: Boolean = true) {
        if (showing && animator == null && reveal == 1f) {
            if (visibility != VISIBLE) visibility = VISIBLE
            return
        }
        showing = true
        visibility = VISIBLE
        glideTo(1f, animate)
    }

    /**
     * Collapses the banner to zero height and then sets its visibility to `GONE`. Does not
     * call [onDismissListener]: that is for the user's own close.
     */
    @JvmOverloads
    public fun dismiss(animate: Boolean = true) {
        if (!showing) return
        showing = false
        glideTo(0f, animate)
    }

    private fun closeByUser() {
        if (!showing || !isEnabled) return
        dismiss()
        onDismissListener?.onDismiss(this)
    }

    /** A banner on its way out is no longer a stop for the keyboard. */
    private fun syncFocusability() {
        descendantFocusability = if (showing) FOCUS_AFTER_DESCENDANTS else FOCUS_BLOCK_DESCENDANTS
        if (!showing) focusedChild?.clearFocus()
    }

    private fun glideTo(target: Float, animate: Boolean) {
        animator?.cancel()
        animator = null
        syncFocusability()
        if (!animate || !isLaidOut || !isAttachedToWindow || reveal == target) {
            jumpTo(target == 1f)
            return
        }
        val distance = kotlin.math.abs(target - reveal)
        val run = ValueAnimator.ofFloat(reveal, target)
        run.duration = (SancaiMotion.feedbackDuration(theme.reduceMotion) * distance).toLong().coerceAtLeast(1L)
        run.interpolator = SancaiMotion.glide()
        run.addUpdateListener {
            reveal = it.animatedValue as Float
            requestLayout()
        }
        run.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (cancelled) return
                animator = null
                jumpTo(target == 1f)
            }
        })
        animator = run
        run.start()
    }

    /** Settles at fully shown or fully collapsed without motion. */
    private fun jumpTo(shown: Boolean) {
        showing = shown
        reveal = if (shown) 1f else 0f
        syncFocusability()
        if (!shown) visibility = GONE
        requestLayout()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        // An animator must not outlive the window: finish where it was heading.
        if (animator != null) {
            animator?.cancel()
            animator = null
            jumpTo(showing)
        }
        super.onDetachedFromWindow()
    }
    // ---------------------------------------------------------------------------------------
    // Layout and drawing
    // ---------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (reveal < 1f) setMeasuredDimension(measuredWidth, (measuredHeight * reveal).roundToInt())
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Optical centring: the icon sits on the middle of the first line of text, whatever
        // the text size, instead of on the top edge of the column.
        val first = when {
            titleView.visibility == VISIBLE -> titleView
            detailView.visibility == VISIBLE -> detailView
            else -> null
        }
        val lines = first?.layout
        if (iconView.visibility == VISIBLE && first != null && lines != null && lines.lineCount > 0) {
            val middle = column.top + first.top + first.paddingTop +
                (lines.getLineTop(0) + lines.getLineBottom(0)) / 2
            iconView.offsetTopAndBottom(middle - iconView.height / 2 - iconView.top)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        body.set(0f, 0f, width.toFloat(), height.toFloat())
        val fade = if (isEnabled) -1 else canvas.saveLayerAlpha(body, SancaiControl.DISABLED_ALPHA)
        // While the banner grows or collapses the corners never exceed half its height.
        val radius = minOf(radius, body.height() / 2f)
        material.drawSurface(canvas, body, radius, null, false)
        // The bar follows the rounded corners of the body at the start edge.
        clip.rewind()
        clip.addRoundRect(body, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clip)
        val bar = BAR_WIDTH_DP * density
        if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            canvas.drawRect(body.right - bar, body.top, body.right, body.bottom, barPaint)
        } else {
            canvas.drawRect(body.left, body.top, body.left + bar, body.bottom, barPaint)
        }
        canvas.restoreToCount(save)
        if (fade >= 0) canvas.restoreToCount(fade)
    }

    override fun dispatchDraw(canvas: Canvas) {
        // The content keeps its full height while the banner grows; cut it at the banner's own
        // edge even inside a parent that does not clip its children.
        if (reveal >= 1f) {
            super.dispatchDraw(canvas)
            return
        }
        val save = canvas.save()
        canvas.clipRect(0, 0, width, height)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    /** A disabled banner pales as a whole and its actions and close icon stop responding. */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        val alpha = if (enabled) 1f else SancaiControl.DISABLED_ALPHA / 255f
        iconView.alpha = alpha
        titleView.alpha = alpha
        detailView.alpha = alpha
        actionButton.isEnabled = enabled
        secondaryActionButton.isEnabled = enabled
        closeView.isEnabled = enabled
        invalidate()
    }

    /** While the banner is collapsing its actions no longer take touches. */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = !showing || !isEnabled

    override fun getAccessibilityClassName(): CharSequence = SancaiBanner::class.java.name
    // ---------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        putBoolean(STATE_SHOWING, showing)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        @Suppress("DEPRECATION")
        super.onRestoreInstanceState(state.getParcelable(STATE_SUPER))
        animator?.cancel()
        animator = null
        val shown = state.getBoolean(STATE_SHOWING, true)
        if (shown) visibility = VISIBLE
        jumpTo(shown)
    }

    /**
     * The text actions side by side; when their labels do not fit the text column (long
     * translations, large text) they stack, each on its own line at the end edge.
     */
    private class ActionsRow(context: Context) : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            var stacked = false
            if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                var wanted = paddingLeft + paddingRight
                for (index in 0 until childCount) {
                    val child = getChildAt(index)
                    if (child.visibility == GONE) continue
                    child.measure(free, free)
                    wanted += child.measuredWidth
                }
                stacked = wanted > MeasureSpec.getSize(widthMeasureSpec)
            }
            val wantedOrientation = if (stacked) VERTICAL else HORIZONTAL
            if (orientation != wantedOrientation) orientation = wantedOrientation
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /** The close icon: a 16 dp cross in the muted colour inside a 44 dp touch target. */
    private inner class CloseView(context: Context) : View(context) {
        private val bounds = RectF()

        override fun onDraw(canvas: Canvas) {
            val side = CLOSE_ICON_DP * density
            val left = (width - side) / 2f
            val top = (height - side) / 2f
            val muted = theme.colors.muted
            val color = if (isEnabled) muted else SancaiColors.withAlpha(muted, SancaiControl.DISABLED_ALPHA)
            SancaiIconPainter.draw(canvas, SancaiIcon.CLOSE, left, top, side, color, CLOSE_STROKE_DP * density)
            if (isFocused && hasWindowFocus() && !isInTouchMode) {
                val inset = CLOSE_FOCUS_INSET_DP * density
                bounds.set(inset, inset, width - inset, height - inset)
                material.drawFocusRim(canvas, bounds, bounds.height() / 2f)
            }
        }

        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            invalidate()
        }

        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previous: android.graphics.Rect?) {
            super.onFocusChanged(gainFocus, direction, previous)
            invalidate()
        }

        override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name
    }
}

private val TONES = SancaiBanner.Tone.values()

private const val STATE_SUPER = "sancai:banner:super"
private const val STATE_SHOWING = "sancai:banner:showing"

private const val BAR_WIDTH_DP = 3f
private const val PADDING_START_DP = 12f
private const val PADDING_END_DP = 14f
private const val PADDING_VERTICAL_DP = 12f
private const val ICON_DP = 20f
private const val ICON_GAP_DP = 10f
private const val DETAIL_GAP_DP = 3f
private const val ACTION_PADDING_DP = 12f
private const val ACTION_GAP_DP = 8f
private const val ACTION_BOTTOM_DP = 0f
private const val CLOSE_TARGET_DP = 44f
private const val CLOSE_ICON_DP = 16f
private const val CLOSE_STROKE_DP = 1.75f
private const val CLOSE_FOCUS_INSET_DP = 6f
