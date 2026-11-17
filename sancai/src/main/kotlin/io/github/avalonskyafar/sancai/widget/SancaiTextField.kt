package io.github.avalonskyafar.sancai.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.text.method.SingleLineTransformationMethod
import android.util.AttributeSet
import android.util.SparseArray
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.BundleCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconDrawable
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.interaction.SancaiPress
import io.github.avalonskyafar.sancai.internal.useOwnFocusRim
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.material.SancaiWellDrawable
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A text field: an optional label, a recessed well that holds the text, and an optional
 * support line with a helper or an error and a character counter.
 *
 * The well is the only material. Its rim tells the state: a soft hairline at rest, solid ink
 * while the field has focus (in touch mode too, since it shows where typing goes) and the
 * danger colour while there is an [error]. The support line grows and shrinks instead of
 * popping. Everything that is not covered by a property is reachable through [editText].
 *
 * Several fields on one screen each restore their own text, error and password visibility,
 * and the one that was being typed in takes focus again, provided each field has an id.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiTextField
 *     android:id="@+id/account"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:hint="Name shown at the top of the page"
 *     android:maxLength="64"
 *     app:sancaiFieldLabel="Account name"
 *     app:sancaiFieldCounter="64"
 *     app:sancaiFieldEndAction="clear" />
 * ```
 */
public class SancaiTextField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** What sits inside the well at its end. */
    public enum class EndAction {
        /** Nothing. */
        NONE,

        /** A cross that empties the field; shown only while there is text. */
        CLEAR,

        /** An eye that shows or hides a password. */
        PASSWORD_TOGGLE,
    }

    /** Hears about text the user typed, pasted or cleared. */
    public fun interface OnTextChangeListener {
        /** Called after [text] of [field] changed through user input. */
        public fun onTextChanged(field: SancaiTextField, text: CharSequence)
    }

    private val theme = SancaiTheme.of(context)
    private val colors = theme.colors
    private val material = SancaiMaterial(context, theme)
    private val wellDrawable = SancaiWellDrawable(material)

    private val labelView = TextView(context)
    private val well = LinearLayout(context)
    private val leadingView = ImageView(context)
    private val actionView = ImageView(context)
    private val supportRow = SupportRow(context)
    private val supportView = TextView(context)
    private val counterView = TextView(context)

    /**
     * The `EditText` inside the well, for everything this class has no property for: IME
     * options, filters, selection. Do not replace its background or padding.
     */
    public val editText: EditText = WellEditText(context)

    private var programmatic = false
    private var supportShown = false
    private var shownAction: SancaiIcon? = null
    private val touchModeListener = android.view.ViewTreeObserver.OnTouchModeChangeListener { updateRim() }
    private var supportAnimator: ValueAnimator? = null

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            updateCounter()
            updateAction()
            if (!programmatic) textChangeListener?.onTextChanged(this@SancaiTextField, editText.text)
        }
    }

    private val nodeDelegate = object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            val message = currentError
            if (message != null) {
                info.error = message
                info.isContentInvalid = true
            }
            // Every field's text has the same id; name the label of this one outright.
            if (label != null) info.setLabeledBy(labelView)
            if (label == null && editText.hint == null && helperText != null) info.hintText = helperText
        }
    }

    private var textChangeListener: OnTextChangeListener? = null

    /** Called after the user changed the text. Setting [text] from code does not call it. */
    public fun setOnTextChangeListener(listener: OnTextChangeListener?) {
        textChangeListener = listener
    }

    /** The text in the field. Setting it moves the cursor to the end and calls no listener. */
    public var text: CharSequence
        get() = editText.text
        set(value) {
            programmatic = true
            try {
                editText.setText(value)
                editText.setSelection(editText.text.length)
            } finally {
                programmatic = false
            }
        }

    /** The hint shown in the muted colour while the field is empty. */
    public var hint: CharSequence?
        get() = editText.hint
        set(value) {
            editText.hint = value
        }

    /** The label above the well, or null for none. It is announced as the name of the field. */
    public var label: CharSequence? = null
        set(value) {
            field = value?.takeIf { it.isNotEmpty() }
            labelView.text = field
            labelView.visibility = if (field == null) GONE else VISIBLE
        }

    /** Helper text below the well in the muted colour. An [error] takes its place. */
    public var helperText: CharSequence? = null
        set(value) {
            field = value?.takeIf { it.isNotEmpty() }
            updateSupport(animate = true)
        }

    /**
     * The error below the well, or null for none. It colours the rim of the well, replaces the
     * [helperText], is announced to accessibility services and gives a reject haptic.
     */
    public var error: CharSequence?
        get() = currentError
        set(value) = applyError(value, feedback = true)

    private var currentError: CharSequence? = null

    /**
     * The length the counter (`12 / 64`) counts towards; 0 hides the counter. The counter only
     * warns: it turns the danger colour beyond the maximum. To stop input at a length, use
     * `android:maxLength` or an `InputFilter.LengthFilter` on [editText].
     */
    public var counterMaxLength: Int = 0
        set(value) {
            field = max(0, value)
            updateCounter()
            updateSupport(animate = true)
        }

    /** The icon inside the well at the start, or null for none. */
    public var leadingIcon: SancaiIcon? = null
        set(value) {
            field = value
            updateLeading()
        }

    /**
     * The action inside the well at the end. [EndAction.PASSWORD_TOGGLE] hides the text and,
     * when the input type is plain text, switches it to a password type. Leaving it undoes
     * both: the plain input type comes back and the text is shown again, unless the input
     * type itself is a password type that was set through [inputType]. The selection is kept.
     */
    public var endAction: EndAction = EndAction.NONE
        set(value) {
            if (field == value) return
            val leavingToggle = field == EndAction.PASSWORD_TOGGLE
            field = value
            keepingSelection {
                if (value == EndAction.PASSWORD_TOGGLE && editText.inputType == InputType.TYPE_CLASS_TEXT) {
                    quietly {
                        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    passwordTypeFromToggle = true
                    applyTextStyle()
                } else if (leavingToggle) {
                    if (passwordTypeFromToggle) {
                        passwordTypeFromToggle = false
                        quietly { editText.inputType = InputType.TYPE_CLASS_TEXT }
                    }
                    // Setting the input type again puts back the transformation that belongs to it.
                    applyLines()
                    // A type that is no password type (a number, text with flags) was hidden
                    // by the transformation alone, which no input type takes away.
                    val plain = if (multilineNow) null else SingleLineTransformationMethod.getInstance()
                    if (!isPasswordType(editText.inputType) && editText.transformationMethod !== plain) {
                        quietly { editText.transformationMethod = plain }
                    }
                }
                applyPassword()
            }
            updateAction()
        }

    /** Whether the password input type was put there by [endAction], not by the caller. */
    private var passwordTypeFromToggle = false

    /**
     * Whether a password is shown as typed. Only has an effect with
     * [EndAction.PASSWORD_TOGGLE]; the selection is kept.
     */
    public var isPasswordVisible: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyPassword()
            updateAction()
        }

    /** Several lines instead of one: the text starts at the top and the well grows with it. */
    public var isMultiline: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyLines()
        }

    /** Lines the well is at least high for while [isMultiline]. */
    public var minLines: Int = DEFAULT_MIN_LINES
        set(value) {
            field = max(1, value)
            applyLines()
        }

    /** Lines after which the text scrolls inside the well while [isMultiline]. */
    public var maxLines: Int = DEFAULT_MAX_LINES
        set(value) {
            field = max(1, value)
            applyLines()
        }

    /** The `android.text.InputType` of the field. The multi-line flag follows [isMultiline]. */
    public var inputType: Int
        get() = editText.inputType
        set(value) {
            // A type the caller chose is never undone by leaving the password toggle.
            passwordTypeFromToggle = false
            keepingSelection {
                quietly { editText.inputType = value }
                applyLines()
            }
        }

    /**
     * Monospace type at a smaller size, for code and configuration editors. A multi-line
     * monospace field does not wrap; long lines scroll sideways.
     */
    public var isMonospace: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyTextStyle()
            applyLines()
        }

    init {
        orientation = VERTICAL
        buildLabel()
        buildWell()
        buildSupport()
        applyTextStyle()
        applyLines()
        updateLeading()
        updateAction()
        updateRim()
        if (attrs != null) readAttributes(attrs, defStyleAttr)
    }

    private fun buildLabel() {
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, SancaiTextRole.FOOTNOTE.sizeSp * theme.textScale)
        labelView.typeface = SancaiWeight.MEDIUM.typeface()
        labelView.includeFontPadding = false
        labelView.setTextColor(colors.ink)
        labelView.textAlignment = TEXT_ALIGNMENT_VIEW_START
        labelView.labelFor = R.id.sancai_text_field_edit
        labelView.visibility = GONE
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        params.bottomMargin = px(LABEL_GAP_DP)
        addView(labelView, params)
    }

    private fun buildWell() {
        well.orientation = HORIZONTAL
        well.isBaselineAligned = false
        well.background = wellDrawable
        well.minimumHeight = px(MIN_HEIGHT_DP)
        well.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        well.setOnClickListener { focusEditText() }
        // Clickable for the finger only: the keyboard stops on the text, not on the well.
        well.isFocusable = false

        leadingView.scaleType = ImageView.ScaleType.CENTER
        leadingView.visibility = GONE
        actionView.visibility = GONE
        leadingView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val iconParams = LayoutParams(px(ICON_DP), px(ICON_DP))
        iconParams.marginStart = px(PADDING_H_DP)
        well.addView(leadingView, iconParams)

        editText.id = R.id.sancai_text_field_edit
        editText.background = null
        // The well and the action draw the focus rim themselves.
        editText.useOwnFocusRim()
        actionView.useOwnFocusRim()
        editText.minimumHeight = px(MIN_HEIGHT_DP)
        editText.includeFontPadding = false
        editText.setTextColor(colors.ink)
        editText.setHintTextColor(colors.muted)
        editText.highlightColor = SancaiColors.withAlpha(colors.accent, HIGHLIGHT_ALPHA)
        editText.textAlignment = TEXT_ALIGNMENT_VIEW_START
        editText.addTextChangedListener(watcher)
        ViewCompat.setAccessibilityDelegate(editText, nodeDelegate)
        tintCursor()
        well.addView(editText, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        actionView.scaleType = ImageView.ScaleType.CENTER
        val actionParams = LayoutParams(px(ACTION_DP), px(ACTION_DP))
        well.addView(actionView, actionParams)
        SancaiPress.attach(actionView, actionView)
        // An action: tick first, then act.
        actionView.setOnClickListener {
            SancaiHaptics.tick(actionView)
            onEndActionClicked()
        }
        actionView.background = ActionFocusDrawable()
        actionView.setOnFocusChangeListener { _, _ -> actionView.invalidate() }

        addView(well, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun buildSupport() {
        supportRow.orientation = HORIZONTAL
        supportRow.setPadding(0, px(SUPPORT_GAP_DP), 0, 0)
        supportRow.visibility = GONE
        for (view in arrayOf(supportView, counterView)) {
            SancaiTextRole.CAPTION.applyTo(view, theme)
            view.setTextColor(colors.muted)
            view.textAlignment = TEXT_ALIGNMENT_VIEW_START
        }
        supportRow.addView(supportView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        counterView.visibility = GONE
        val counterParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        counterParams.marginStart = px(COUNTER_GAP_DP)
        supportRow.addView(counterView, counterParams)
        addView(supportRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun readAttributes(attrs: AttributeSet, defStyleAttr: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiTextField, defStyleAttr, 0)
        try {
            isMonospace = a.getBoolean(R.styleable.SancaiTextField_sancaiFieldMonospace, false)
            if (a.hasValue(R.styleable.SancaiTextField_android_inputType)) {
                inputType = a.getInt(R.styleable.SancaiTextField_android_inputType, InputType.TYPE_CLASS_TEXT)
            }
            minLines = a.getInt(R.styleable.SancaiTextField_android_minLines, DEFAULT_MIN_LINES)
            maxLines = a.getInt(R.styleable.SancaiTextField_android_maxLines, DEFAULT_MAX_LINES)
            isMultiline = a.getBoolean(R.styleable.SancaiTextField_sancaiFieldMultiline, false)
            val maxLength = a.getInt(R.styleable.SancaiTextField_android_maxLength, -1)
            if (maxLength >= 0) editText.filters = editText.filters + InputFilter.LengthFilter(maxLength)
            hint = a.getText(R.styleable.SancaiTextField_android_hint)
            label = a.getText(R.styleable.SancaiTextField_sancaiFieldLabel)
            leadingIcon = SancaiIcon.fromAttr(a.getInt(R.styleable.SancaiTextField_sancaiIcon, -1))
            endAction = when (a.getInt(R.styleable.SancaiTextField_sancaiFieldEndAction, 0)) {
                1 -> EndAction.CLEAR
                2 -> EndAction.PASSWORD_TOGGLE
                else -> EndAction.NONE
            }
            a.getText(R.styleable.SancaiTextField_android_text)?.let { text = it }
            counterMaxLength = a.getInt(R.styleable.SancaiTextField_sancaiFieldCounter, 0)
            helperText = a.getText(R.styleable.SancaiTextField_sancaiFieldHelper)
            applyError(a.getText(R.styleable.SancaiTextField_sancaiFieldError), feedback = false)
            if (!a.getBoolean(R.styleable.SancaiTextField_android_enabled, true)) isEnabled = false
        } finally {
            a.recycle()
        }
    }

    /**
     * Checks the text with [rule], which returns the error to show or null when the text is
     * fine. Sets or clears [error] accordingly and returns whether the text passed.
     */
    public fun validate(rule: (CharSequence) -> CharSequence?): Boolean {
        val message = rule(editText.text)?.takeIf { it.isNotEmpty() }
        applyError(message, feedback = message != null)
        return message == null
    }

    private fun applyError(value: CharSequence?, feedback: Boolean) {
        val message = value?.takeIf { it.isNotEmpty() }
        val changed = !TextUtils.equals(currentError, message)
        currentError = message
        updateRim()
        updateSupport(animate = true)
        if (message != null && feedback && (changed || isAttachedToWindow)) {
            announceForAccessibility(message)
            SancaiHaptics.reject(this)
        }
    }

    private fun updateRim() {
        val focused = editText.isFocused && isEnabled
        wellDrawable.rim = when {
            currentError != null -> colors.danger
            focused -> colors.ink
            else -> null
        }
        // Keyboard focus thickens the rim; an error keeps its colour instead.
        wellDrawable.focused = focused && currentError == null && !editText.isInTouchMode
    }

    private fun applyTextStyle() {
        val size = if (isMonospace) SancaiTextRole.FOOTNOTE.sizeSp else SancaiTextRole.BODY.sizeSp
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size * theme.textScale)
        editText.typeface = if (isMonospace) Typeface.MONOSPACE else SancaiWeight.REGULAR.typeface()
    }

    /** Whether the field really shows several lines: only text input can. */
    private val multilineNow: Boolean
        get() = isMultiline && (editText.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT

    // Setting a password input type sets the text again, which would drop the cursor to 0.
    private fun applyLines(): Unit = keepingSelection { applyLinesNow() }

    private fun applyLinesNow() {
        val flag = InputType.TYPE_TEXT_FLAG_MULTI_LINE
        val isText = (editText.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        val base = if (isText) editText.inputType and flag.inv() else editText.inputType
        val multi = isMultiline && isText
        // The input type decides single or multi line inside EditText, passwords included.
        quietly { editText.inputType = if (multi) base or flag else base }
        if (multi) {
            editText.minLines = minLines
            editText.maxLines = max(minLines, maxLines)
            editText.setHorizontallyScrolling(isMonospace)
        }
        editText.gravity = (if (multi) Gravity.TOP else Gravity.CENTER_VERTICAL) or Gravity.START
        well.gravity = if (multi) Gravity.TOP else Gravity.CENTER_VERTICAL
        // Setting an input type resets the typeface and transformation of passwords.
        applyTextStyle()
        applyPassword()
        updatePadding()
    }

    /** Whether EditText itself hides text of this input type. */
    private fun isPasswordType(type: Int): Boolean =
        when (type and (InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION)) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD -> true
            else -> false
        }

    private fun applyPassword() {
        if (endAction != EndAction.PASSWORD_TOGGLE) return
        val start = editText.selectionStart
        val end = editText.selectionEnd
        // Changing the transformation sets the text again; that is not user input.
        quietly {
            editText.transformationMethod =
                if (isPasswordVisible) null else PasswordTransformationMethod.getInstance()
        }
        if (start >= 0 && end >= 0) editText.setSelection(start, end)
    }

    private fun updateLeading() {
        val icon = leadingIcon
        leadingView.setImageDrawable(icon?.let { SancaiIconDrawable(context, it, colors.muted, ICON_DP) })
        leadingView.visibility = if (icon == null) GONE else VISIBLE
        updatePadding()
    }

    private fun updateAction() {
        val icon = when (endAction) {
            EndAction.NONE -> null
            EndAction.CLEAR -> SancaiIcon.CLOSE.takeIf { editText.length() > 0 }
            EndAction.PASSWORD_TOGGLE -> if (isPasswordVisible) SancaiIcon.EYE_OFF else SancaiIcon.EYE
        }
        if (icon != shownAction) {
            shownAction = icon
            actionView.setImageDrawable(icon?.let { SancaiIconDrawable(context, it, colors.muted, ICON_DP) })
            actionView.visibility = if (icon == null) GONE else VISIBLE
            actionView.contentDescription = when (icon) {
                SancaiIcon.CLOSE -> context.getString(R.string.sancai_text_field_clear)
                SancaiIcon.EYE -> context.getString(R.string.sancai_text_field_show_password)
                SancaiIcon.EYE_OFF -> context.getString(R.string.sancai_text_field_hide_password)
                else -> null
            }
            updatePadding()
        }
    }

    private fun updatePadding() {
        val multi = multilineNow
        val start = if (leadingView.visibility == VISIBLE) px(ICON_GAP_DP) else px(PADDING_H_DP)
        val end = if (actionView.visibility == VISIBLE) 0 else px(PADDING_H_DP)
        editText.setPaddingRelative(start, px(PADDING_V_DP), end, px(PADDING_V_DP))
        // In a multi-line field the icon and the action stay level with the first line.
        (leadingView.layoutParams as? LayoutParams)?.let {
            it.topMargin = if (multi) (px(MIN_HEIGHT_DP) - px(ICON_DP)) / 2 else 0
            leadingView.layoutParams = it
        }
        (actionView.layoutParams as? LayoutParams)?.let {
            it.topMargin = if (multi) (px(MIN_HEIGHT_DP) - px(ACTION_DP)) / 2 else 0
            actionView.layoutParams = it
        }
    }

    private fun updateCounter() {
        val limit = counterMaxLength
        counterView.visibility = if (limit > 0) VISIBLE else GONE
        if (limit <= 0) return
        val length = editText.length()
        counterView.text = context.getString(R.string.sancai_text_field_counter, length, limit)
        counterView.contentDescription =
            resources.getQuantityString(R.plurals.sancai_text_field_counter_description, limit, length, limit)
        counterView.setTextColor(if (length > limit) colors.danger else colors.muted)
    }

    private fun updateSupport(animate: Boolean) {
        val message = currentError ?: helperText
        val wanted = message != null || counterMaxLength > 0
        if (wanted) {
            // While the line shrinks away it keeps its last words.
            supportView.text = message
            supportView.setTextColor(if (currentError != null) colors.danger else colors.muted)
        }
        if (wanted == supportShown) return
        supportShown = wanted
        supportAnimator?.cancel()
        supportAnimator = null
        val target = if (wanted) 1f else 0f
        val from = if (supportRow.visibility == VISIBLE) supportRow.fraction else 0f
        if (!animate || !isAttachedToWindow || !isLaidOut || from == target) {
            finishSupport(target)
            return
        }
        supportRow.visibility = VISIBLE
        supportRow.fraction = from
        supportAnimator = ValueAnimator.ofFloat(from, target).apply {
            duration = SancaiMotion.feedbackDuration(theme.reduceMotion)
            interpolator = SancaiMotion.glide()
            addUpdateListener { supportRow.fraction = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) finishSupport(target)
                }
            })
            start()
        }
    }

    private fun finishSupport(target: Float) {
        supportAnimator = null
        supportRow.fraction = target
        supportRow.visibility = if (target > 0f) VISIBLE else GONE
    }

    private fun onEndActionClicked() {
        when (endAction) {
            EndAction.NONE -> Unit
            EndAction.CLEAR -> {
                // A user action: the listener hears about it through the watcher.
                editText.text.clear()
                focusEditText()
            }
            EndAction.PASSWORD_TOGGLE -> isPasswordVisible = !isPasswordVisible
        }
    }

    private fun focusEditText() {
        if (!isEnabled) return
        if (!editText.isFocused) editText.requestFocus()
        val keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        keyboard?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun tintCursor() {
        if (Build.VERSION.SDK_INT < 29) return
        val cursor = GradientDrawable()
        cursor.setColor(colors.accent)
        cursor.setSize(px(CURSOR_DP), 0)
        editText.textCursorDrawable = cursor
        editText.textSelectHandle?.mutate()?.let {
            it.setTint(colors.accent)
            editText.setTextSelectHandle(it)
        }
        editText.textSelectHandleLeft?.mutate()?.let {
            it.setTint(colors.accent)
            editText.setTextSelectHandleLeft(it)
        }
        editText.textSelectHandleRight?.mutate()?.let {
            it.setTint(colors.accent)
            editText.setTextSelectHandleRight(it)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        editText.isEnabled = enabled
        actionView.isEnabled = enabled
        well.isClickable = enabled
        updateRim()
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (isEnabled) {
            super.dispatchDraw(canvas)
            return
        }
        val save = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), SancaiControl.DISABLED_ALPHA)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    /** Focus goes to the text, so `requestFocus()` on the field starts typing. */
    override fun requestFocus(direction: Int, previouslyFocusedRect: android.graphics.Rect?): Boolean =
        isEnabled && editText.requestFocus(direction, previouslyFocusedRect)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnTouchModeChangeListener(touchModeListener)
        updateRim()
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnTouchModeChangeListener(touchModeListener)
        supportAnimator?.cancel()
        finishSupport(if (supportShown) 1f else 0f)
        super.onDetachedFromWindow()
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.LinearLayout"

    // The children have no stable ids of their own, so their state travels inside this view's.
    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        dispatchThawSelfOnly(container)
    }

    override fun onSaveInstanceState(): Parcelable {
        val children = SparseArray<Parcelable>()
        editText.saveHierarchyState(children)
        return Bundle().apply {
            putParcelable(STATE_SUPER, super.onSaveInstanceState())
            putSparseParcelableArray(STATE_CHILDREN, children)
            putCharSequence(STATE_ERROR, currentError)
            putBoolean(STATE_PASSWORD_VISIBLE, isPasswordVisible)
            putBoolean(STATE_FOCUSED, editText.isFocused)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        state.classLoader = javaClass.classLoader
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        programmatic = true
        try {
            BundleCompat.getSparseParcelableArray(state, STATE_CHILDREN, Parcelable::class.java)
                ?.let { editText.restoreHierarchyState(it) }
        } finally {
            programmatic = false
        }
        isPasswordVisible = state.getBoolean(STATE_PASSWORD_VISIBLE, isPasswordVisible)
        applyError(state.getCharSequence(STATE_ERROR), feedback = false)
        supportAnimator?.end()
        // Every field's text has the same id, so the window gives focus back to the first field
        // on the screen. Posted, so that this runs after the window did that.
        if (state.getBoolean(STATE_FOCUSED, false)) post { if (isEnabled) editText.requestFocus() }
    }

    /** Runs [block], which may set the text again, and puts the selection back afterwards. */
    private inline fun keepingSelection(block: () -> Unit) {
        val start = editText.selectionStart
        val end = editText.selectionEnd
        block()
        if (start < 0 || end < 0) return
        val length = editText.length()
        editText.setSelection(start.coerceAtMost(length), end.coerceAtMost(length))
    }

    /** Runs [block] without telling the text listener: the change does not come from the user. */
    private inline fun quietly(block: () -> Unit) {
        val before = programmatic
        programmatic = true
        try {
            block()
        } finally {
            programmatic = before
        }
    }

    private fun px(dp: Float): Int = (dp * resources.displayMetrics.density).roundToInt()

    /** The text itself; reports focus to the well around it. */
    private inner class WellEditText(context: Context) : EditText(context) {
        override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
            super.onFocusChanged(focused, direction, previouslyFocusedRect)
            updateRim()
        }

        // A single line never scrolls vertically. Without this, text or a selection set before
        // the first layout can leave the line scrolled out of the well.
        override fun scrollTo(x: Int, y: Int) {
            val single = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0
            super.scrollTo(x, if (single) 0 else y)
        }

        override fun getAccessibilityClassName(): CharSequence = EditText::class.java.name
    }

    /** Keyboard focus on the end action: the thick ink rim, drawn inside its bounds. */
    private inner class ActionFocusDrawable : android.graphics.drawable.Drawable() {
        private val body = android.graphics.RectF()

        override fun draw(canvas: Canvas) {
            if (!actionView.isFocused || actionView.isInTouchMode) return
            body.set(bounds)
            body.inset(px(ACTION_RIM_INSET_DP).toFloat(), px(ACTION_RIM_INSET_DP).toFloat())
            material.drawFocusRim(canvas, body, body.height() / 2f)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** The support line: its height is a share of what its content needs, so it can grow. */
    private class SupportRow(context: Context) : LinearLayout(context) {
        var fraction: Float = 1f
            set(value) {
                if (field != value) {
                    field = value
                    requestLayout()
                }
            }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            if (fraction < 1f) setMeasuredDimension(measuredWidth, (measuredHeight * fraction).roundToInt())
        }
    }

    private companion object {
        const val LABEL_GAP_DP = 7f
        const val PADDING_H_DP = 13f
        const val PADDING_V_DP = 11f
        const val MIN_HEIGHT_DP = 44f
        const val ICON_DP = 18f
        const val ICON_GAP_DP = 9f
        /** Side of the end action: a 44 dp target whose focus rim is drawn 2 dp inside it. */
        const val ACTION_DP = 44f
        const val ACTION_RIM_INSET_DP = 2f
        const val SUPPORT_GAP_DP = 6f
        const val COUNTER_GAP_DP = 12f
        const val CURSOR_DP = 2f
        const val HIGHLIGHT_ALPHA = 72
        const val DEFAULT_MIN_LINES = 3
        const val DEFAULT_MAX_LINES = 10
        const val STATE_SUPER = "sancai:super"
        const val STATE_CHILDREN = "sancai:children"
        const val STATE_ERROR = "sancai:error"
        const val STATE_PASSWORD_VISIBLE = "sancai:passwordVisible"
        const val STATE_FOCUSED = "sancai:focused"
    }
}
