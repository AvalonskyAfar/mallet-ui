package io.github.avalonskyafar.sancai.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Checkable
import androidx.core.os.BundleCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One choice out of several: a small round recessed bed that fills with glaze and grows a
 * neutral bead in its centre when it is checked.
 *
 * Unchecked the bed is a plain well; checked it is a glazed track ([glaze], cobalt by default)
 * and the bead scales up from nothing while the glaze fades in. An optional label ([text]) sits
 * to the end of the bed, and the whole view is the touch target.
 *
 * A checked radio cannot be unchecked by the user; only choosing a sibling does that. Put the
 * radios of one question into a [SancaiRadioGroup], which keeps a single one checked.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiRadioButton
 *     android:id="@+id/daily"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:text="@string/every_day"
 *     app:sancaiChecked="true" />
 * ```
 *
 * The view is at least 44 dp tall; the bed itself is 26 dp across.
 */
public class SancaiRadioButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiControl(context, attrs, defStyleAttr), Checkable {

    /** Called when the user changes the checked state of a radio. */
    public fun interface OnCheckedChangeListener {
        /** The user changed [radio]; [isChecked] is its new state. */
        public fun onCheckedChanged(radio: SancaiRadioButton, isChecked: Boolean)
    }

    private var checkedState = false
    private var progress = 0f
    private var down = false
    private var animator: ValueAnimator? = null
    private var label: CharSequence? = null
    private var labelLayout: StaticLayout? = null
    private val labelPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG)
    private val bedBounds = RectF()
    private val beadBounds = RectF()
    private val bedClip = Path()

    /**
     * Hook of the owning [SancaiRadioGroup]: told about every change of the checked state,
     * with `animate` as it was asked for and `fromUser` saying whether listeners may be called.
     */
    internal var groupHook: ((button: SancaiRadioButton, checked: Boolean, animate: Boolean, fromUser: Boolean) -> Unit)? =
        null

    private var checkedChangeListener: OnCheckedChangeListener? = null

    /**
     * Listener for user-driven changes: a tap, a key or an accessibility action on this radio,
     * or the user choosing a sibling in the same [SancaiRadioGroup] (which reports `false`
     * here). Not called for [setChecked] or [SancaiRadioGroup.check].
     */
    public fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        checkedChangeListener = listener
    }

    /** Glaze poured into the bed while the radio is checked. */
    public var glaze: SancaiGlaze = theme.glazes.cobalt
        set(value) {
            field = value
            invalidate()
        }

    /** Label drawn to the end of the bed, or `null` for a bare radio. It wraps when it must. */
    public var text: CharSequence?
        get() = label
        set(value) {
            if (label?.toString() == value?.toString()) return
            label = value
            labelLayout = null
            requestLayout()
            invalidate()
        }

    init {
        labelPaint.typeface = SancaiWeight.REGULAR.typeface()
        labelPaint.color = theme.colors.ink
        var initial = false
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiRadioButton, defStyleAttr, 0)
            try {
                initial = a.getBoolean(R.styleable.SancaiRadioButton_sancaiChecked, false)
                if (a.hasValue(R.styleable.SancaiRadioButton_sancaiGlaze)) {
                    glaze = theme.glazes.fromAttr(a.getInt(R.styleable.SancaiRadioButton_sancaiGlaze, 2))
                }
                label = a.getText(R.styleable.SancaiRadioButton_android_text)
                isEnabled = a.getBoolean(R.styleable.SancaiRadioButton_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        checkedState = initial
        progress = if (initial) 1f else 0f
    }

    // ---------------------------------------------------------------------------------------
    // Checked state
    // ---------------------------------------------------------------------------------------

    override fun isChecked(): Boolean = checkedState

    /** Sets the state from code without animation. The listener is not called. */
    override fun setChecked(checked: Boolean) {
        setChecked(checked, animate = false)
    }

    /**
     * Sets the state from code; with [animate] the glaze and the bead glide in or out. The
     * listener is never called, so this is safe for rolling back a change it refused.
     */
    public fun setChecked(checked: Boolean, animate: Boolean) {
        change(checked, animate, fromUser = false)
    }

    /** Checks the radio from code. A radio never toggles back to unchecked. */
    override fun toggle() {
        if (!checkedState) setChecked(true, animate = true)
    }

    /** Lets the owning group move the check mark; `fromUser` passes the listener rule on. */
    internal fun setCheckedFromGroup(checked: Boolean, animate: Boolean, fromUser: Boolean) {
        change(checked, animate, fromUser)
    }

    /**
     * The one place the state changes. For user input the own listener hears it first; if it
     * rolled the change back, the group never learns of it and keeps its previous choice.
     */
    private fun change(checked: Boolean, animate: Boolean, fromUser: Boolean) {
        if (checkedState == checked) return
        checkedState = checked
        val target = if (checked) 1f else 0f
        animator?.cancel()
        if (animate) {
            animator = settle(progress, target) { progress = it }
        } else {
            progress = target
            invalidate()
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        if (fromUser) checkedChangeListener?.onCheckedChanged(this, checked)
        if (checkedState == checked) groupHook?.invoke(this, checked, animate, fromUser)
    }

    /** A user-driven check: animate, tell the listeners, then tick. */
    private fun checkByUser() {
        if (checkedState) return
        change(checked = true, animate = true, fromUser = true)
        SancaiHaptics.tick(this)
    }

    // ---------------------------------------------------------------------------------------
    // Measuring and drawing
    // ---------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val text = label
        val horizontalPadding = paddingLeft + paddingRight
        if (text.isNullOrEmpty()) {
            labelLayout = null
            setMeasuredDimension(
                resolveSize(dp(MIN_TARGET_DP).roundToInt() + horizontalPadding, widthMeasureSpec),
                resolveSize(dp(MIN_TARGET_DP).roundToInt() + paddingTop + paddingBottom, heightMeasureSpec),
            )
            return
        }
        applyLabelType()
        val fixed = dp(GUTTER_SIDE_DP + BED_DP + GAP_DP) + horizontalPadding
        val desired = ceil(Layout.getDesiredWidth(text, labelPaint)).toInt()
        val room = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            desired
        } else {
            max(0, (MeasureSpec.getSize(widthMeasureSpec) - fixed).toInt())
        }
        val layout = buildLabel(text, min(desired, room))
        labelLayout = layout
        val contentHeight = max(dp(MIN_TARGET_DP), layout.height + dp(LABEL_MARGIN_DP) * 2f)
        setMeasuredDimension(
            resolveSize(ceil(fixed + layout.width).toInt(), widthMeasureSpec),
            resolveSize(ceil(contentHeight).toInt() + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    /** SUBHEAD size and spacing in the regular weight; follows `theme.textScale`. */
    private fun applyLabelType() {
        SancaiTextRole.SUBHEAD.applyTo(labelPaint, theme, resources.displayMetrics.scaledDensity)
        labelPaint.typeface = SancaiWeight.REGULAR.typeface()
    }

    private fun buildLabel(text: CharSequence, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, labelPaint, max(0, width))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setTextDirection(
                if (isRtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR,
            )
            .build()

    /** Places the 26 dp bed: at the start beside a label, in the middle of a bare radio. */
    private fun placeBed() {
        val layout = labelLayout
        val size = dp(BED_DP)
        val middle = (paddingTop + height - paddingBottom) / 2f
        val centreX: Float
        var centreY = middle
        if (layout == null) {
            centreX = (paddingLeft + width - paddingRight) / 2f
        } else {
            val reach = dp(GUTTER_SIDE_DP) + size / 2f
            centreX = if (isRtl) width - paddingRight - reach else paddingLeft + reach
            // A wrapped label keeps the bed beside its first line.
            centreY = middle - layout.height / 2f + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f
        }
        bedBounds.set(centreX - size / 2f, centreY - size / 2f, centreX + size / 2f, centreY + size / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        placeBed()
        val radius = bedBounds.height() / 2f

        // The gutters of the compact switch, so a radio and a switch cast the same shadow.
        val body = canvas.save()
        canvas.clipRect(
            bedBounds.left - dp(GUTTER_SIDE_DP),
            bedBounds.top - dp(GUTTER_TOP_DP),
            bedBounds.right + dp(GUTTER_SIDE_DP),
            bedBounds.bottom + dp(GUTTER_BOTTOM_DP),
        )
        if (progress < 1f) {
            // An even rim: the default well rim fades out at the bottom, which is right for a
            // wide field but leaves so small a circle looking open.
            val rim = SancaiColors.blend(theme.colors.hairline, theme.colors.ink, WELL_RIM_INK)
            material.drawWell(canvas, bedBounds, radius, rim, pressed = down)
        }
        if (progress > 0f) {
            val fade = if (progress < 1f) {
                canvas.saveLayerAlpha(
                    bedBounds.left - dp(GUTTER_SIDE_DP),
                    bedBounds.top - dp(GUTTER_TOP_DP),
                    bedBounds.right + dp(GUTTER_SIDE_DP),
                    bedBounds.bottom + dp(GUTTER_BOTTOM_DP),
                    (progress * 255f).roundToInt(),
                )
            } else {
                -1
            }
            material.drawTrack(canvas, bedBounds, theme.bedColor(glaze), theme.bedEdgeColor(glaze), down, radius = radius)
            if (fade >= 0) canvas.restoreToCount(fade)

            val bead = dp(BEAD_DP) * progress
            if (bead >= 1f) {
                val sink = if (down) dp(PRESS_SINK_DP) else 0f
                beadBounds.set(
                    bedBounds.centerX() - bead / 2f,
                    bedBounds.centerY() - bead / 2f + sink,
                    bedBounds.centerX() + bead / 2f,
                    bedBounds.centerY() + bead / 2f + sink,
                )
                bedClip.rewind()
                bedClip.addCircle(bedBounds.centerX(), bedBounds.centerY(), radius, Path.Direction.CW)
                canvas.clipPath(bedClip)
                if (theme.isDark) {
                    // A dark thumb would read as a hole in the glaze: the bead takes the pale
                    // tone that labels have on this bed.
                    textPaint.style = Paint.Style.FILL
                    textPaint.color = theme.labelOnBed(glaze)
                    canvas.drawCircle(beadBounds.centerX(), beadBounds.centerY(), bead / 2f, textPaint)
                } else {
                    material.drawThumb(canvas, beadBounds, down)
                }
            }
        }
        canvas.restoreToCount(body)
        if (showsFocus) material.drawFocusRim(canvas, bedBounds, radius)

        val layout = labelLayout ?: return
        val left = if (isRtl) bedBounds.left - dp(GAP_DP) - layout.width else bedBounds.right + dp(GAP_DP)
        val text = canvas.save()
        canvas.translate(left, (paddingTop + height - paddingBottom) / 2f - layout.height / 2f)
        layout.draw(canvas)
        canvas.restoreToCount(text)
    }

    // ---------------------------------------------------------------------------------------
    // Touch and keys
    // ---------------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> press(true)
            MotionEvent.ACTION_MOVE -> {
                val slop = touchSlop.toFloat()
                val inside = event.x >= -slop && event.x <= width + slop && event.y >= -slop && event.y <= height + slop
                if (!inside) press(false)
            }
            MotionEvent.ACTION_UP -> if (down) {
                press(false)
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> press(false)
        }
        return true
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        // A radio disabled under the finger must not stay sunk.
        if (!enabled) press(false)
    }

    private fun press(pressed: Boolean) {
        if (down == pressed) return
        down = pressed
        invalidate()
    }

    /** Checks the radio as the user would: listeners are called, then the click listener. */
    override fun performClick(): Boolean {
        if (isEnabled) checkByUser()
        return super.performClick()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                // A checked radio has nothing left to do, so the key is not consumed.
                if (!isEnabled || checkedState) return false
                checkByUser()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        // Handled on key-down; the platform's click-on-release must not run a second time.
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> false
        else -> super.onKeyUp(keyCode, event)
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

    override fun getAccessibilityClassName(): CharSequence = "android.widget.RadioButton"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isCheckable = true
        info.isChecked = checkedState
        if (!label.isNullOrEmpty()) info.text = label
        // Choosing it again would change nothing, so a checked radio offers no click.
        if (checkedState) info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        event.isChecked = checkedState
    }

    override fun onPopulateAccessibilityEvent(event: AccessibilityEvent) {
        super.onPopulateAccessibilityEvent(event)
        label?.takeIf { it.isNotEmpty() }?.let { event.text.add(it) }
    }

    public companion object {
        /** Diameter of the round bed in dp. */
        public const val BED_DP: Float = 26f

        /** Diameter of the neutral bead of a checked radio in dp. */
        public const val BEAD_DP: Float = 12f

        /** Smallest height of the view, and width of a radio without a label, in dp. */
        public const val MIN_TARGET_DP: Float = 44f

        private const val GAP_DP = 10f
        private const val LABEL_MARGIN_DP = 10f
        private const val PRESS_SINK_DP = 1f
        private const val WELL_RIM_INK = 0.14f

        // Room the shadow of the bed needs: the gutters of the compact switch.
        private const val GUTTER_SIDE_DP = 1.5f
        private const val GUTTER_TOP_DP = 1f
        private const val GUTTER_BOTTOM_DP = 3f

        private const val STATE_SUPER = "sancai:super"
        private const val STATE_CHECKED = "sancai:checked"
    }
}
