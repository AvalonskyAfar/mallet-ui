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
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.os.BundleCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconPainter
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import kotlin.math.ceil

/**
 * A small porcelain capsule for filters, suggestions and short actions.
 *
 * | Kind | Behaviour |
 * |---|---|
 * | [Kind.ACTION] | a small neutral button: a tap ticks and calls the click listener |
 * | [Kind.FILTER] | checkable: a tap flips [isChecked] and calls [onCheckedChangeListener] |
 *
 * An unchecked chip is raised white porcelain with an ink label. Checking a filter chip pours
 * a glaze into it: the body becomes a recessed glazed capsule ([glaze], cobalt by default), a
 * small check mark is drawn before the label and the chip glides to its wider size. The colour
 * is the state; nothing else about the chip changes.
 *
 * Pressing sinks the chip by 1.5 dp and tightens its shadow. Keyboard focus thickens the rim
 * into ink, Space and Enter act like a tap, and a disabled chip pales. The body is 32 dp high
 * inside a 44 dp touch target; the room around it holds the shadow.
 *
 * [onCheckedChangeListener] is called for user input only (touch, keyboard, accessibility),
 * never for [setChecked], and it is called before the haptic tick. Put several chips into a
 * [SancaiChipGroup] to let them wrap and, optionally, to allow one choice only.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiChip
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     android:text="Unread"
 *     app:sancaiChipKind="filter"
 *     app:sancaiChecked="true" />
 * ```
 */
public class SancaiChip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiControl(context, attrs, defStyleAttr) {

    /** What a chip does when it is tapped. */
    public enum class Kind { ACTION, FILTER }

    /** Called when the user checks or unchecks a filter chip. */
    public fun interface OnCheckedChangeListener {
        public fun onCheckedChanged(chip: SancaiChip, isChecked: Boolean)
    }

    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = SancaiWeight.MEDIUM.typeface()
        textSize = sp(TEXT_SP)
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val checkPath = Path()
    private val checkSegment = Path()
    private val checkMeasure = PathMeasure()
    private val body = RectF()
    private var checkedState = false
    private var down = false
    private var animator: ValueAnimator? = null

    /** 0 = porcelain, 1 = glazed and as wide as the check mark needs. */
    private var progress = 0f

    /** Listener for user-driven changes of [isChecked]. */
    public var onCheckedChangeListener: OnCheckedChangeListener? = null

    /** The label. A label wider than the chip is ellipsized. */
    public var text: CharSequence = ""
        set(value) {
            if (TextUtils.equals(field, value)) return
            field = value
            requestLayout()
            invalidate()
        }

    /** Whether the chip is pressed like a button or checked like a filter. */
    public var kind: Kind = Kind.ACTION
        set(value) {
            if (field == value) return
            field = value
            // Only a filter can hold a check.
            if (value == Kind.ACTION) setChecked(false, animate = false)
            invalidate()
        }

    /** Glaze poured into a checked filter chip. Cobalt by default. */
    public var glaze: SancaiGlaze = theme.glazes.cobalt
        set(value) {
            field = value
            invalidate()
        }

    /** Icon drawn before the label, or `null`. Directional icons mirror in right-to-left layouts. */
    public var icon: SancaiIcon? = null
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    /**
     * Whether a filter chip is checked. Setting it from code glides to the new state and does
     * not call the listener; an action chip stays unchecked.
     */
    public var isChecked: Boolean
        get() = checkedState
        set(value) = setChecked(value, animate = true)

    /** The group this chip lives in; it keeps a single selection and hears about changes. */
    internal var group: SancaiChipGroup? = null

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiChip, defStyleAttr, 0)
            try {
                text = a.getText(R.styleable.SancaiChip_android_text) ?: ""
                kind = KINDS.getOrElse(a.getInt(R.styleable.SancaiChip_sancaiChipKind, 0)) { Kind.ACTION }
                if (a.hasValue(R.styleable.SancaiChip_sancaiGlaze)) {
                    glaze = theme.glazes.fromAttr(a.getInt(R.styleable.SancaiChip_sancaiGlaze, 2))
                }
                icon = SancaiIcon.fromAttr(a.getInt(R.styleable.SancaiChip_sancaiIcon, -1))
                setChecked(a.getBoolean(R.styleable.SancaiChip_sancaiChecked, false), animate = false)
                if (a.hasValue(R.styleable.SancaiChip_android_enabled)) {
                    isEnabled = a.getBoolean(R.styleable.SancaiChip_android_enabled, true)
                }
            } finally {
                a.recycle()
            }
        }
    }

    /**
     * Checks or unchecks a filter chip from code, gliding when [animate] is set. Listeners are
     * not called, so this is safe for rolling back a change a listener refused. Inside a
     * single-selection [SancaiChipGroup] checking one chip unchecks the others.
     */
    public fun setChecked(checked: Boolean, animate: Boolean) {
        applyChecked(checked, animate, fromUser = false)
    }

    internal fun applyChecked(checked: Boolean, animate: Boolean, fromUser: Boolean) {
        val value = checked && kind == Kind.FILTER
        if (checkedState == value) return
        checkedState = value
        moveTo(if (value) 1f else 0f, animate)
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        group?.onChipChecked(this, value, animate, fromUser)
        if (fromUser) onCheckedChangeListener?.onCheckedChanged(this, value)
    }

    private fun moveTo(target: Float, animate: Boolean) {
        animator?.cancel()
        animator = null
        if (!animate || progress == target) {
            setProgress(target)
            return
        }
        // The chip grows by the check mark, so every step is also a layout step.
        animator = settle(progress, target) { setProgress(it) }
    }

    private fun setProgress(value: Float) {
        progress = value
        requestLayout()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        progress = if (checkedState) 1f else 0f
        down = false
        super.onDetachedFromWindow()
    }

    // ---------------------------------------------------------------------------------------
    // Measuring and drawing
    // ---------------------------------------------------------------------------------------

    /** Height of the capsule: 32 dp, or more when large text needs the room. */
    private val bodyHeight: Float
        get() {
            val metrics = labelPaint.fontMetrics
            return maxOf(dp(BODY_DP), metrics.descent - metrics.ascent + 2f * dp(MIN_TEXT_INSET_DP))
        }

    /** Width of what stands before the label at the current [progress]. */
    private val leadWidth: Float
        get() {
            val iconPart = if (icon == null) 0f else dp(ICON_DP + ICON_GAP_DP)
            return iconPart + dp(CHECK_DP + CHECK_GAP_DP) * progress
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val content = leadWidth + labelPaint.measureText(text, 0, text.length) + 2f * dp(PADDING_DP)
        val bodyWidth = maxOf(content, bodyHeight)
        val width = ceil(bodyWidth + 2f * dp(GUTTER_SIDE_DP)).toInt() + paddingLeft + paddingRight
        val height = ceil(maxOf(dp(MIN_TARGET_DP), bodyHeight + 2f * dp(GUTTER_VERTICAL_DP))).toInt() +
            paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(maxOf(width, suggestedMinimumWidth), widthMeasureSpec),
            resolveSize(maxOf(height, suggestedMinimumHeight), heightMeasureSpec),
        )
    }

    /** Places the capsule: full width between the side gutters, centred vertically. */
    private fun placeBody() {
        val high = minOf(bodyHeight, (height - paddingTop - paddingBottom).toFloat())
        val top = paddingTop + (height - paddingTop - paddingBottom - high) / 2f
        body.set(
            paddingLeft + dp(GUTTER_SIDE_DP), top,
            width - paddingRight - dp(GUTTER_SIDE_DP), top + high,
        )
    }

    /**
     * The visible capsule in view coordinates, written to [out]. The rest of the view is the
     * touch target and the room for the shadow; [SancaiChipGroup] measures its gaps from here.
     */
    public fun getBodyBounds(out: RectF) {
        placeBody()
        out.set(body)
    }

    /** Measured distance from the side of the view to the side of the capsule. */
    internal fun bodyInsetX(): Int = (paddingLeft + paddingRight) / 2 + dp(GUTTER_SIDE_DP).toInt()

    /** Measured distance from the top (and bottom) of the view to the capsule. */
    internal fun bodyInsetY(): Int = maxOf(0f, (measuredHeight - bodyHeight) / 2f).toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        placeBody()
        if (body.width() <= 0f || body.height() <= 0f) return
        if (down) body.offset(0f, dp(SancaiMotion.PRESS_SINK_DP))
        val radius = body.height() / 2f
        val fade = (progress / GLAZE_SHARE).coerceIn(0f, 1f)
        if (fade < 1f) material.drawSurface(canvas, body, radius, pressed = down)
        if (fade > 0f) {
            // The glaze is poured in as one body, shadow and rim included.
            val save = if (fade < 1f) {
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (fade * 255f + 0.5f).toInt())
            } else {
                canvas.save()
            }
            material.drawTrack(canvas, body, theme.bedColor(glaze), theme.bedEdgeColor(glaze), pressed = down)
            canvas.restoreToCount(save)
        }
        if (showsFocus) material.drawFocusRim(canvas, body, radius)
        drawContent(canvas, SancaiColors.blend(theme.colors.ink, theme.labelOnBed(glaze), fade))
    }

    /** Check mark, icon and label, centred in the capsule as one group and mirrored in RTL. */
    private fun drawContent(canvas: Canvas, color: Int) {
        val lead = leadWidth
        val room = body.width() - 2f * dp(PADDING_DP) - lead
        val shown = if (room <= 0f || text.isEmpty()) "" else {
            TextUtils.ellipsize(text, labelPaint, room + 0.5f, TextUtils.TruncateAt.END)
        }
        val textWidth = labelPaint.measureText(shown, 0, shown.length)
        // A chip without a label has no trailing gap to balance.
        val group = if (shown.isEmpty() && lead > 0f) lead - trailingGap() else lead + textWidth
        val rtl = isRtl
        val direction = if (rtl) -1f else 1f
        var cursor = body.centerX() - direction * group / 2f
        val centreY = body.centerY()

        if (progress > 0f) {
            val size = dp(CHECK_DP)
            val left = if (rtl) cursor - size else cursor
            drawCheck(canvas, left, centreY - size / 2f, color)
            cursor += direction * dp(CHECK_DP + CHECK_GAP_DP) * progress
        }
        val glyph = icon
        if (glyph != null) {
            val size = dp(ICON_DP)
            val left = if (rtl) cursor - size else cursor
            SancaiIconPainter.draw(canvas, glyph, left, centreY - size / 2f, size, color, dp(ICON_STROKE_DP), rtl)
            cursor += direction * dp(ICON_DP + ICON_GAP_DP)
        }
        if (shown.isEmpty()) return
        labelPaint.color = color
        val metrics = labelPaint.fontMetrics
        val baseline = centreY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(shown, 0, shown.length, if (rtl) cursor - textWidth else cursor, baseline, labelPaint)
    }

    private fun trailingGap(): Float = if (icon != null) dp(ICON_GAP_DP) else dp(CHECK_GAP_DP) * progress

    /** Draws the check mark stroke by stroke as the glaze settles. It is not mirrored. */
    private fun drawCheck(canvas: Canvas, left: Float, top: Float, color: Int) {
        val share = ((progress - CHECK_DELAY) / (1f - CHECK_DELAY)).coerceIn(0f, 1f)
        if (share <= 0f) return
        val unit = dp(CHECK_DP) / CHECK_GRID
        checkPath.rewind()
        checkPath.moveTo(left + 4.5f * unit, top + 12.5f * unit)
        checkPath.lineTo(left + 9.5f * unit, top + 17.5f * unit)
        checkPath.lineTo(left + 19.5f * unit, top + 6.5f * unit)
        checkPaint.color = color
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
            // The sink starts on touch-down, without the tap delay of scrolling parents.
            MotionEvent.ACTION_DOWN -> setDown(true)
            // A finger that slides off the chip takes the press with it.
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
                // A chip that cannot act on the key does not swallow it either.
                if (!isEnabled) return false
                if (event.repeatCount == 0) setDown(true)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!isEnabled || !down) return false
                setDown(false)
                performClick()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Taps, keys and the accessibility click action all arrive here. An action chip ticks and
     * then calls its click listener. A filter chip flips, tells its listeners and ticks last,
     * so a filter that switches haptics off stays silent. The one checked chip of a group that
     * requires a selection cannot be unchecked and ignores the click.
     */
    override fun performClick(): Boolean {
        if (!isEnabled) return false
        if (kind == Kind.ACTION) {
            SancaiHaptics.tick(this)
            super.performClick()
            return true
        }
        if (checkedState && group?.mayUncheck(this) == false) return false
        applyChecked(!checkedState, animate = true, fromUser = true)
        SancaiHaptics.tick(this)
        super.performClick()
        return true
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        if (!gainFocus) down = false
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
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

    override fun getAccessibilityClassName(): CharSequence = when {
        kind == Kind.ACTION -> "android.widget.Button"
        group?.isSingleSelection == true -> "android.widget.RadioButton"
        else -> "android.widget.CheckBox"
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (kind == Kind.FILTER) {
            info.isCheckable = true
            info.isChecked = checkedState
        }
        if (text.isNotEmpty()) info.text = text
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        if (kind == Kind.FILTER) event.isChecked = checkedState
    }

    override fun onPopulateAccessibilityEvent(event: AccessibilityEvent) {
        super.onPopulateAccessibilityEvent(event)
        if (text.isNotEmpty()) event.text.add(text)
    }

    internal companion object {
        private const val BODY_DP = 32f
        private const val PADDING_DP = 14f
        private const val TEXT_SP = 13f
        private const val ICON_DP = 16f
        private const val ICON_GAP_DP = 6f
        private const val ICON_STROKE_DP = 1.7f
        private const val CHECK_DP = 14f
        private const val CHECK_GAP_DP = 5f
        private const val CHECK_GRID = 24f
        private const val CHECK_STROKE_DP = 1.9f
        private const val MIN_TARGET_DP = 44f
        private const val MIN_TEXT_INSET_DP = 6f

        /** Room for the shadow beside the capsule, inside the view. */
        internal const val GUTTER_SIDE_DP = 2f

        /** Room above and below the capsule; the lower one holds the shadow. */
        internal const val GUTTER_VERTICAL_DP = 6f

        /** Share of the transition in which the glaze reaches full strength. */
        private const val GLAZE_SHARE = 0.6f

        /** Share of the transition that passes before the check starts. */
        private const val CHECK_DELAY = 0.2f
        private const val STATE_SUPER = "sancai:super"
        private const val STATE_CHECKED = "sancai:checked"
        private val KINDS = Kind.values()
    }
}
