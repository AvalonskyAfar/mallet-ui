package io.github.avalonskyafar.sancai.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Checkable
import androidx.core.os.BundleCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A small recessed bed that fills with glaze when it is checked.
 *
 * Unchecked, the box is a neutral well. Checked, it becomes a glazed bed (cobalt unless
 * [glaze] says otherwise) carrying a check mark. Between the two the glaze fades in and the
 * check is drawn along its path; with reduced motion the box jumps.
 *
 * An optional label ([text]) sits at the end of the box and wraps when it has to; the box
 * stays level with the first line. The whole view is the touch target, at least 44 dp tall.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiCheckbox
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     android:text="@string/remember_me"
 *     app:sancaiChecked="true"
 *     app:sancaiGlaze="celadon" />
 * ```
 *
 * [onCheckedChangeListener] is called for user input only (touch, keyboard, accessibility),
 * never for [setChecked] or [toggle], so a listener may roll the box back safely.
 */
public class SancaiCheckbox @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiControl(context, attrs, defStyleAttr), Checkable {

    /** Called when the user checks or unchecks the box. */
    public fun interface OnCheckedChangeListener {
        public fun onCheckedChanged(view: View, checked: Boolean)
    }

    private var checkedState = false
    private var progress = 0f
    private var down = false
    private var animator: ValueAnimator? = null
    private var label: CharSequence = ""
    private var labelLayout: StaticLayout? = null
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val boxBounds = RectF()
    private val checkPath = Path()
    private val checkSegment = Path()
    private val checkMeasure = PathMeasure()
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Listener for user-driven changes. Not called for [setChecked] or [toggle]. */
    public var onCheckedChangeListener: OnCheckedChangeListener? = null

    /** The glaze poured into the box when it is checked. Cobalt by default. */
    public var glaze: SancaiGlaze = theme.glazes.cobalt
        set(value) {
            field = value
            invalidate()
        }

    /** The label at the end of the box, or empty for a bare box. */
    public var text: CharSequence
        get() = label
        set(value) {
            if (label.toString() == value.toString()) return
            label = value
            labelLayout = null
            requestLayout()
            invalidate()
        }

    init {
        labelPaint.typeface = SancaiWeight.REGULAR.typeface()
        checkPaint.style = Paint.Style.STROKE
        checkPaint.strokeCap = Paint.Cap.ROUND
        checkPaint.strokeJoin = Paint.Join.ROUND
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiCheckbox, defStyleAttr, 0)
            try {
                a.getText(R.styleable.SancaiCheckbox_android_text)?.let { label = it }
                checkedState = a.getBoolean(R.styleable.SancaiCheckbox_sancaiChecked, false)
                if (a.hasValue(R.styleable.SancaiCheckbox_sancaiGlaze)) {
                    glaze = theme.glazes.fromAttr(a.getInt(R.styleable.SancaiCheckbox_sancaiGlaze, 2))
                }
                isEnabled = a.getBoolean(R.styleable.SancaiCheckbox_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        progress = if (checkedState) 1f else 0f
    }

    // ---------------------------------------------------------------------------------------
    // Checkable
    // ---------------------------------------------------------------------------------------

    override fun isChecked(): Boolean = checkedState

    /** Checks or unchecks the box from code, gliding when it is on screen. No callback. */
    override fun setChecked(checked: Boolean) {
        setChecked(checked, animate = true)
    }

    /**
     * Checks or unchecks the box from code. The listener is not called, which makes this safe
     * to use for rolling back a change the listener refused. With [animate] false the box jumps.
     */
    public fun setChecked(checked: Boolean, animate: Boolean) {
        val changed = checked != checkedState
        checkedState = checked
        moveTo(if (checked) 1f else 0f, animate)
        if (changed) announceState()
    }

    /** Flips the box from code. The listener is not called. */
    override fun toggle() {
        setChecked(!checkedState, animate = true)
    }

    // ---------------------------------------------------------------------------------------
    // Measuring and drawing
    // ---------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        labelPaint.textSize = sp(LABEL_SP)
        val horizontalPadding = paddingLeft + paddingRight
        val desiredWidth: Float
        var labelHeight = 0
        if (label.isEmpty()) {
            labelLayout = null
            desiredWidth = dp(MIN_TARGET_DP)
        } else {
            val lead = dp(GUTTER_SIDE_DP + BOX_DP + GAP_DP)
            val wanted = ceil(Layout.getDesiredWidth(label, labelPaint)).toInt()
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            val room = max(1, MeasureSpec.getSize(widthMeasureSpec) - horizontalPadding - ceil(lead).toInt())
            val labelWidth = when (mode) {
                MeasureSpec.UNSPECIFIED -> max(1, wanted)
                MeasureSpec.EXACTLY -> room
                else -> wanted.coerceIn(1, room)
            }
            val layout = buildLabel(labelWidth)
            labelLayout = layout
            labelHeight = layout.height
            desiredWidth = ceil(lead) + labelWidth
        }
        val desiredHeight = max(dp(MIN_TARGET_DP), labelHeight + dp(LABEL_MARGIN_DP * 2))
        setMeasuredDimension(
            resolveSize(desiredWidth.roundToInt() + horizontalPadding, widthMeasureSpec),
            resolveSize(desiredHeight.roundToInt() + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    /**
     * Lays the label out so that it starts next to the box: a label written against the
     * direction of the view (Latin text in a right-to-left layout) is aligned to its far side.
     */
    private fun buildLabel(width: Int): StaticLayout {
        val natural = buildLabel(width, Layout.Alignment.ALIGN_NORMAL)
        val runsRtl = natural.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT
        return if (runsRtl == isRtl) natural else buildLabel(width, Layout.Alignment.ALIGN_OPPOSITE)
    }

    private fun buildLabel(width: Int, alignment: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(label, 0, label.length, labelPaint, width)
            .setAlignment(alignment)
            .setTextDirection(
                if (isRtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR,
            )
            .setIncludePad(false)
            .build()

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        labelLayout = null
        requestLayout()
    }

    /** Places the box: level with the first label line, or in the middle of a bare view. */
    private fun placeBox(layout: StaticLayout?, labelTop: Float) {
        val box = dp(BOX_DP)
        val left: Float
        val centerY: Float
        if (layout == null) {
            left = paddingLeft + (width - paddingLeft - paddingRight - box) / 2f
            centerY = paddingTop + (height - paddingTop - paddingBottom) / 2f
        } else {
            val metrics = labelPaint.fontMetrics
            left = if (isRtl) width - paddingRight - dp(GUTTER_SIDE_DP) - box else paddingLeft + dp(GUTTER_SIDE_DP)
            centerY = labelTop + layout.getLineBaseline(0) + (metrics.ascent + metrics.descent) / 2f
        }
        // Whole pixels keep the hairline rim crisp.
        val x = left.roundToInt().toFloat()
        val y = (centerY - box / 2f).roundToInt().toFloat()
        boxBounds.set(x, y, x + box.roundToInt(), y + box.roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = labelLayout
        val labelTop = if (layout == null) 0f else {
            paddingTop + (height - paddingTop - paddingBottom - layout.height) / 2f
        }
        placeBox(layout, labelTop)
        val radius = dp(RADIUS_DP)

        val fade = (progress / GLAZE_SHARE).coerceIn(0f, 1f)
        if (fade < 1f) {
            // A full rim: the well's own lower edge is a highlight that vanishes on pale ground,
            // and an empty box still has to read as a box.
            val rim = SancaiColors.blend(theme.colors.hairline, theme.colors.ink, WELL_RIM_INK)
            material.drawWell(canvas, boxBounds, radius, rim)
        }
        if (fade > 0f) {
            // The glaze fades in as one body, shadow and rim included.
            val save = if (fade < 1f) {
                canvas.saveLayerAlpha(
                    boxBounds.left - dp(GUTTER_SIDE_DP), boxBounds.top - dp(GUTTER_TOP_DP),
                    boxBounds.right + dp(GUTTER_SIDE_DP), boxBounds.bottom + dp(SHADOW_REACH_DP),
                    (fade * 255f).roundToInt(),
                )
            } else {
                canvas.save()
            }
            material.drawTrack(
                canvas, boxBounds, theme.bedColor(glaze), theme.bedEdgeColor(glaze),
                pressed = down, radius = radius,
            )
            canvas.restoreToCount(save)
        }
        drawCheck(canvas, ((progress - CHECK_DELAY) / (1f - CHECK_DELAY)).coerceIn(0f, 1f))
        if (showsFocus) material.drawFocusRim(canvas, boxBounds, radius)

        if (layout != null) {
            labelPaint.color = theme.colors.ink
            val lead = ceil(dp(GUTTER_SIDE_DP + BOX_DP + GAP_DP))
            val labelLeft = if (isRtl) width - paddingRight - lead - layout.width else paddingLeft + lead
            val save = canvas.save()
            canvas.translate(labelLeft, labelTop)
            layout.draw(canvas)
            canvas.restoreToCount(save)
        }
    }

    /** Draws the first [share] of the check mark, from its short stroke to the tip of the long one. */
    private fun drawCheck(canvas: Canvas, share: Float) {
        if (share <= 0f) return
        val unit = dp(CHECK_DP) / CHECK_GRID
        val left = boxBounds.centerX() - dp(CHECK_DP) / 2f
        val top = boxBounds.centerY() - dp(CHECK_DP) / 2f + if (down) dp(PRESS_SINK_DP) else 0f
        checkPath.rewind()
        checkPath.moveTo(left + 4.5f * unit, top + 12.5f * unit)
        checkPath.lineTo(left + 9.5f * unit, top + 17.5f * unit)
        checkPath.lineTo(left + 19.5f * unit, top + 6.5f * unit)
        checkPaint.color = theme.labelOnBed(glaze)
        checkPaint.strokeWidth = dp(CHECK_STROKE_DP)
        if (share >= 1f) {
            canvas.drawPath(checkPath, checkPaint)
            return
        }
        checkMeasure.setPath(checkPath, false)
        checkSegment.rewind()
        checkMeasure.getSegment(0f, checkMeasure.length * share, checkSegment, true)
        canvas.drawPath(checkSegment, checkPaint)
    }

    // ---------------------------------------------------------------------------------------
    // Touch and keys
    // ---------------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> setDown(true)
            // A finger that slides off the view takes the press with it.
            MotionEvent.ACTION_MOVE -> if (down && !contains(event.x, event.y)) setDown(false)
            MotionEvent.ACTION_UP -> {
                val wasDown = down
                setDown(false)
                if (wasDown) performClick()
            }
            MotionEvent.ACTION_CANCEL -> setDown(false)
        }
        return true
    }

    private fun setDown(value: Boolean) {
        if (down == value) return
        down = value
        invalidate()
    }

    private fun contains(x: Float, y: Float): Boolean =
        x >= -touchSlop && y >= -touchSlop && x < width + touchSlop && y < height + touchSlop

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // A disabled box cannot act on the key, so it does not swallow it either.
                if (!isEnabled) return false
                if (event.repeatCount == 0) performClick()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * A user-driven flip: taps, keys and the accessibility click action all arrive here. The
     * box glides, then the listener hears about it, then the haptic ticks.
     */
    override fun performClick(): Boolean {
        if (!isEnabled) return false
        checkedState = !checkedState
        moveTo(if (checkedState) 1f else 0f, animate = true)
        announceState()
        onCheckedChangeListener?.onCheckedChanged(this, checkedState)
        SancaiHaptics.tick(this)
        super.performClick()
        return true
    }

    private fun moveTo(target: Float, animate: Boolean) {
        animator?.cancel()
        animator = null
        if (!animate || progress == target) {
            progress = target
            invalidate()
            return
        }
        animator = settle(progress, target) { progress = it }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        progress = if (checkedState) 1f else 0f
        down = false
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------------------------------
    // State and accessibility
    // ---------------------------------------------------------------------------------------

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        putBoolean(STATE_CHECKED, checkedState)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        setChecked(state.getBoolean(STATE_CHECKED, checkedState), animate = false)
    }

    private fun announceState() {
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.CheckBox"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isCheckable = true
        info.isChecked = checkedState
        if (label.isNotEmpty()) info.text = label
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        event.isChecked = checkedState
    }

    override fun onPopulateAccessibilityEvent(event: AccessibilityEvent) {
        super.onPopulateAccessibilityEvent(event)
        if (label.isNotEmpty()) event.text.add(label)
    }

    private companion object {
        const val BOX_DP = 26f
        const val RADIUS_DP = 7f
        // The gutters of the compact switch: room for the bed's shadow inside the view.
        const val GUTTER_SIDE_DP = 1.5f
        const val GUTTER_TOP_DP = 1f
        /** How far the soft tail of the bed's shadow may fall; the layer of the fade keeps it. */
        const val SHADOW_REACH_DP = 9f
        const val WELL_RIM_INK = 0.14f
        const val GAP_DP = 10f
        const val MIN_TARGET_DP = 44f
        const val LABEL_MARGIN_DP = 10f
        const val LABEL_SP = 15.5f
        const val CHECK_DP = 18f
        const val CHECK_GRID = 24f
        const val CHECK_STROKE_DP = 2.2f
        const val PRESS_SINK_DP = 1f
        /** Share of the transition in which the glaze reaches full strength. */
        const val GLAZE_SHARE = 0.6f
        /** Share of the transition that passes before the check starts. */
        const val CHECK_DELAY = 0.2f
        const val STATE_SUPER = "sancai:super"
        const val STATE_CHECKED = "sancai:checked"
    }
}
