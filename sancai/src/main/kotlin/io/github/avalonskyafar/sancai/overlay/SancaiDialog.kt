package io.github.avalonskyafar.sancai.overlay

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.interaction.SancaiPress
import io.github.avalonskyafar.sancai.internal.dp
import io.github.avalonskyafar.sancai.internal.dpInt
import io.github.avalonskyafar.sancai.material.SancaiGutters
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.material.SancaiSurfaceDrawable
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import io.github.avalonskyafar.sancai.widget.SancaiControl
import io.github.avalonskyafar.sancai.widget.SancaiText
import io.github.avalonskyafar.sancai.widget.SancaiTextField
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A modal question on a porcelain card: a centred title, a muted message, optional content
 * and one to three text actions under a rim-to-rim divider.
 *
 * ```kotlin
 * SancaiDialog.Builder(context)
 *     .title("Restore all defaults?")
 *     .message("Every setting returns to its original value.")
 *     .positive("Restore", tone = SancaiTextTone.DANGER) { dialog -> store.reset(); true }
 *     .negative("Cancel")
 *     .show()
 * ```
 *
 * Behaviour worth knowing:
 *
 * - An action callback returns `true` to close and `false` to stay open. Calling
 *   [showError] inside the positive callback also keeps the dialog open, whatever is returned.
 * - The card rises into place and sinks away again. **Once it has started to close, every
 *   further click and back press is ignored**, so an action can never be committed twice
 *   while the exit motion runs.
 * - Two actions sit side by side, the confirming one at the end. Three actions, or two whose
 *   labels do not fit on one line each, are stacked as full-width rows: positive, neutral,
 *   negative.
 * - Back closes a [Builder.cancelable] dialog; touching outside does not unless
 *   [Builder.canceledOnTouchOutside] is set.
 * - A dialog is a window, not a view, so it has no XML form and is not restored after a
 *   configuration change; show it again from your own state.
 */
public class SancaiDialog private constructor(private val context: Context, builder: Builder) {

    /** Called when the user chooses an action. */
    public fun interface OnActionListener {
        /** Returns `true` to close [dialog], `false` to keep it open. */
        public fun onAction(dialog: SancaiDialog): Boolean
    }

    /** Called with the text of an input dialog when its positive action is chosen. */
    public fun interface OnInputListener {
        /** Returns `true` to accept [text] and close, `false` to keep [dialog] open. */
        public fun onInput(dialog: SancaiDialog, text: String): Boolean
    }

    /** Called once when the dialog has left the screen. */
    public fun interface OnDismissListener {
        /**
         * @param positive `true` only when the positive action closed the dialog; `false` for
         * every other way out (another action, back, outside touch, [dismiss]). Use it to
         * roll a control back when a confirmation was not given.
         */
        public fun onDismiss(dialog: SancaiDialog, positive: Boolean)
    }

    internal class Action(
        val which: Int,
        val label: CharSequence,
        val tone: SancaiTextTone,
        val listener: OnActionListener?,
    )

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val material: SancaiMaterial = SancaiMaterial(context, theme)
    private val cancelable: Boolean = builder.cancelable
    private val dismissListener: OnDismissListener? = builder.dismissListener
    private val inputListener: OnInputListener? = builder.inputListener
    private val platformDialog: CardDialog = CardDialog(context, this)
    private val card: DialogCard = DialogCard(context)
    private val actionsLayout: ActionsLayout = ActionsLayout(context, theme)
    private val actionViews: Array<ActionView?> = arrayOfNulls(3)
    private val errorView: SancaiText = SancaiText.create(context, null, SancaiTextRole.CAPTION)
    private val actions: List<Action>
    private var textField: SancaiTextField? = null
    private var contentInstalled = false
    private var closedByPositive = false
    private var errorRaised = false

    /** `true` from the moment the dialog starts to close; actions and back are ignored then. */
    public var isClosing: Boolean = false
        private set

    /** Whether the dialog window is on screen (it still is while the exit motion runs). */
    public val isShowing: Boolean
        get() = platformDialog.isShowing

    /** The card, for tests and for code that needs to look something up in the content. */
    public val contentView: View
        get() = card

    /** The platform dialog that hosts the card, for window flags this class does not cover. */
    public val dialog: Dialog
        get() = platformDialog

    /** The editor of an input dialog, or `null` for any other dialog. */
    public val inputField: EditText?
        get() = textField?.editText

    /** The current text of an input dialog; empty for any other dialog. */
    public val inputText: String
        get() = textField?.editText?.text?.toString().orEmpty()

    /** The message in the validation slot, or `null` while no error is shown. */
    public val error: CharSequence?
        get() = if (errorView.visibility == View.VISIBLE) errorView.text else null

    /** Whether the actions are stacked as full-width rows (valid after the card was measured). */
    public val isStacked: Boolean
        get() = actionsLayout.stacked

    init {
        val side = context.dpInt(PADDING_SIDE_DP)
        val top = context.dpInt(PADDING_TOP_DP)
        val title = builder.title?.takeIf { it.isNotEmpty() }
        if (title != null) {
            val titleView = SancaiText.create(context, title, SancaiTextRole.HEADLINE)
            titleView.gravity = Gravity.CENTER
            ViewCompat.setAccessibilityHeading(titleView, true)
            card.addView(titleView, block(side, top))
        }
        ViewCompat.setAccessibilityPaneTitle(
            card,
            title ?: context.getString(R.string.sancai_dialog_pane_title),
        )

        // Message and content share one scrolling column, so a long text never pushes the
        // title or the actions off the card.
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(side, 0, side, 0)
        val message = builder.message?.takeIf { it.isNotEmpty() }
        if (message != null) {
            val messageView = SancaiText.create(context, message, SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
            messageView.gravity = Gravity.CENTER
            column.addView(messageView, block(0, if (title != null) context.dpInt(MESSAGE_GAP_DP) else 0))
        }
        if (builder.hasInput) {
            val input = SancaiTextField(context)
            input.isMultiline = builder.inputMultiline
            if (builder.inputMultiline) {
                input.minLines = INPUT_MIN_LINES
                input.maxLines = INPUT_MAX_LINES
            } else {
                input.editText.imeOptions = EditorInfo.IME_ACTION_DONE
            }
            input.inputType = builder.inputType
            input.hint = builder.inputHint
            input.text = builder.inputInitial ?: ""
            input.editText.setSelection(input.editText.text.length)
            input.editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // An action: tick first, exactly like a tap on the positive label.
                    if (!isClosing && actionViews[ACTION_POSITIVE]?.isEnabled == true) SancaiHaptics.tick(card)
                    performAction(ACTION_POSITIVE)
                    true
                } else {
                    false
                }
            }
            textField = input
            column.addView(input, block(0, if (column.childCount > 0 || title != null) context.dpInt(CONTENT_GAP_DP) else 0))
        }
        builder.view?.let { custom ->
            (custom.parent as? ViewGroup)?.removeView(custom)
            column.addView(custom, block(0, if (column.childCount > 0 || title != null) context.dpInt(CONTENT_GAP_DP) else 0))
        }
        if (column.childCount > 0) {
            val scroll = ScrollView(context)
            scroll.overScrollMode = View.OVER_SCROLL_NEVER
            scroll.isVerticalScrollBarEnabled = false
            scroll.addView(column, ViewGroup.LayoutParams(MATCH, WRAP))
            val params = LinearLayout.LayoutParams(MATCH, WRAP, 1f)
            if (title == null) params.topMargin = top
            card.addView(scroll, params)
        }
        // Validation slot: a danger capsule as wide as the content, hidden until needed.
        errorView.typeface = SancaiWeight.MEDIUM.typeface()
        errorView.setTextColor(Color.WHITE)
        errorView.gravity = Gravity.CENTER
        errorView.background = SancaiSurfaceDrawable(material, pill = true, tint = theme.colors.danger)
        errorView.setPadding(
            context.dpInt(ERROR_PADDING_X_DP + SancaiGutters.SURFACE_SIDE_DP),
            context.dpInt(ERROR_PADDING_Y_DP + SancaiGutters.SURFACE_TOP_DP),
            context.dpInt(ERROR_PADDING_X_DP + SancaiGutters.SURFACE_SIDE_DP),
            context.dpInt(ERROR_PADDING_Y_DP + SancaiGutters.SURFACE_BOTTOM_DP),
        )
        errorView.visibility = View.GONE
        ViewCompat.setAccessibilityLiveRegion(errorView, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        // The capsule carries its own shadow gutter; pull it out so the body spans the content width.
        val errorParams = block(side - context.dpInt(SancaiGutters.SURFACE_SIDE_DP), context.dpInt(ERROR_GAP_DP))
        // The shadow may overlap the 18 dp gap below, so the gap is measured from the visible body.
        errorParams.bottomMargin = -context.dpInt(SancaiGutters.SURFACE_BOTTOM_DP)
        card.addView(errorView, errorParams)

        // Side by side the confirming action is last; a forced stack starts with it.
        val actions = builder.resolveActions(context)
        val forcedStack = actions.size >= STACK_FROM
        actionsLayout.forcedStack = forcedStack
        for (action in if (forcedStack) actions else actions.asReversed()) {
            val view = ActionView(context, material)
            view.text = action.label
            view.tone = action.tone
            SancaiPress.attach(view) { performAction(action.which) }
            actionViews[action.which] = view
            actionsLayout.addView(view)
        }
        this.actions = actions
        val actionParams = LinearLayout.LayoutParams(MATCH, WRAP)
        actionParams.topMargin = context.dpInt(DIVIDER_GAP_DP)
        card.addView(actionsLayout, actionParams)

        platformDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        platformDialog.setCancelable(cancelable)
        platformDialog.setCanceledOnTouchOutside(builder.canceledOnTouchOutside)
        platformDialog.setOnDismissListener {
            card.animate().cancel()
            card.setLayerType(View.LAYER_TYPE_NONE, null)
            dismissListener?.onDismiss(this, closedByPositive)
        }
    }

    private fun block(sideMargin: Int, topMargin: Int): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(MATCH, WRAP)
        params.setMargins(sideMargin, topMargin, sideMargin, 0)
        return params
    }

    /**
     * Shows the dialog and plays the enter motion. An input dialog focuses its field and
     * opens the keyboard. Does nothing while the dialog is already on screen.
     */
    public fun show() {
        if (platformDialog.isShowing) return
        isClosing = false
        closedByPositive = false
        if (!contentInstalled) {
            platformDialog.setContentView(card, ViewGroup.LayoutParams(MATCH, WRAP))
            contentInstalled = true
        }
        val window = platformDialog.window
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val attributes = window.attributes
            attributes.width = windowWidth()
            attributes.height = WRAP
            attributes.dimAmount = DIM_AMOUNT
            window.attributes = attributes
            if (textField != null) {
                window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                )
            }
        }
        textField?.editText?.requestFocus()
        platformDialog.show()
        window?.setLayout(windowWidth(), WRAP)
        card.animate().cancel()
        if (theme.reduceMotion) {
            rest()
            return
        }
        card.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        card.alpha = 0f
        card.translationY = context.dp(RISE_DP)
        card.scaleX = START_SCALE
        card.scaleY = START_SCALE
        card.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(SancaiMotion.DIALOG_MS)
            .setInterpolator(SancaiMotion.glide())
            .withEndAction { card.setLayerType(View.LAYER_TYPE_NONE, null) }
            .start()
    }

    private fun rest() {
        card.alpha = 1f
        card.translationY = 0f
        card.scaleX = 1f
        card.scaleY = 1f
    }

    private fun windowWidth(): Int {
        val screen = context.resources.displayMetrics.widthPixels
        return min(context.dpInt(MAX_WIDTH_DP), screen - context.dpInt(SCREEN_MARGIN_DP))
    }

    /**
     * Closes the dialog with the exit motion. The dismiss listener reports `positive = false`.
     * Ignored when the dialog is not showing or is already closing.
     */
    public fun dismiss() {
        close(positive = false)
    }

    /** Back and outside touches arrive here through the platform dialog. */
    internal fun cancelFromWindow() {
        close(positive = false)
    }

    private fun close(positive: Boolean) {
        if (isClosing || !platformDialog.isShowing) return
        // The guard: from here on nothing the user does reaches a callback again.
        isClosing = true
        closedByPositive = positive
        card.animate().cancel()
        if (theme.reduceMotion || !card.isLaidOut) {
            platformDialog.dismiss()
            return
        }
        card.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        card.animate().alpha(0f).translationY(context.dp(RISE_DP)).scaleX(START_SCALE).scaleY(START_SCALE)
            .setDuration(SancaiMotion.DIALOG_MS)
            .setInterpolator(SancaiMotion.glide())
            .withEndAction { platformDialog.dismiss() }
            .start()
    }

    private fun performAction(which: Int) {
        if (isClosing) return
        val action = actions.firstOrNull { it.which == which } ?: return
        if (actionViews[which]?.isEnabled == false) return
        var accepted: Boolean
        if (which == ACTION_POSITIVE) {
            clearError()
            errorRaised = false
            accepted = inputListener?.onInput(this, inputText) ?: true
            if (accepted && !errorRaised) accepted = action.listener?.onAction(this) ?: true
            if (errorRaised) accepted = false
        } else {
            accepted = action.listener?.onAction(this) ?: true
        }
        if (accepted) close(positive = which == ACTION_POSITIVE)
    }

    /**
     * Fills the validation slot above the actions with a danger capsule and gives a reject
     * haptic. Called from inside a positive callback it also keeps the dialog open, whatever
     * the callback returns. The next positive attempt clears it.
     */
    public fun showError(message: CharSequence) {
        errorRaised = true
        errorView.text = message
        errorView.visibility = View.VISIBLE
        SancaiHaptics.reject(card)
    }

    /** Empties and hides the validation slot. */
    public fun clearError() {
        errorView.visibility = View.GONE
        errorView.text = null
    }

    /**
     * Enables or disables one action, for example the positive one until a form is complete.
     * A disabled action pales and cannot be pressed. Unknown actions are ignored.
     *
     * @param which [ACTION_POSITIVE], [ACTION_NEUTRAL] or [ACTION_NEGATIVE]
     */
    public fun setActionEnabled(which: Int, enabled: Boolean) {
        actionViews.getOrNull(which)?.isEnabled = enabled
    }

    /** The view of an action, or `null` when the dialog has no such action. */
    public fun actionView(which: Int): View? = actionViews.getOrNull(which)

    /** Collects the parts of a dialog. Only the context is required: the default is one OK action. */
    public class Builder(private val context: Context) {
        internal var title: CharSequence? = null
        internal var message: CharSequence? = null
        internal var view: View? = null
        internal var positive: Action? = null
        internal var neutral: Action? = null
        internal var negative: Action? = null
        internal var hasInput: Boolean = false
        internal var inputInitial: CharSequence? = null
        internal var inputHint: CharSequence? = null
        internal var inputType: Int = InputType.TYPE_CLASS_TEXT
        internal var inputMultiline: Boolean = false
        internal var inputListener: OnInputListener? = null
        internal var cancelable: Boolean = true
        internal var canceledOnTouchOutside: Boolean = false
        internal var dismissListener: OnDismissListener? = null

        /** Centred headline; it is also announced as the name of the dialog. */
        public fun title(title: CharSequence?): Builder = apply { this.title = title }

        /** Centred, muted explanation below the title. A long message scrolls inside the card. */
        public fun message(message: CharSequence?): Builder = apply { this.message = message }

        /** Custom content (a form, a list) between the message and the actions. */
        public fun view(view: View?): Builder = apply { this.view = view }

        /**
         * The confirming action, at the end of the row.
         *
         * @param label defaults to the library's "OK" ("Save" for an input dialog)
         * @param tone [SancaiTextTone.ACCENT], or [SancaiTextTone.DANGER] for a destructive confirmation
         * @param listener returns `true` to close; without one the action just closes
         */
        @JvmOverloads
        public fun positive(
            label: CharSequence? = null,
            tone: SancaiTextTone = SancaiTextTone.ACCENT,
            listener: OnActionListener? = null,
        ): Builder = apply {
            positive = Action(ACTION_POSITIVE, label ?: "", tone, listener)
        }

        /** The dismissive action in the muted tone, at the start of the row. [label] defaults to "Cancel". */
        @JvmOverloads
        public fun negative(label: CharSequence? = null, listener: OnActionListener? = null): Builder = apply {
            negative = Action(ACTION_NEGATIVE, label ?: "", SancaiTextTone.MUTED, listener)
        }

        /** A third, alternative action in the ink tone. A dialog with three actions stacks them. */
        @JvmOverloads
        public fun neutral(label: CharSequence, listener: OnActionListener? = null): Builder = apply {
            neutral = Action(ACTION_NEUTRAL, label, SancaiTextTone.INK, listener)
        }

        /**
         * Turns the dialog into an input dialog: a text field below the message. The keyboard
         * opens with the dialog and the "done" key of a single-line field triggers the
         * positive action. Without explicit actions the dialog gets "Save" and "Cancel".
         *
         * @param inputType an `android.text.InputType`
         * @param multiline a field of three to ten lines instead of one
         * @param listener receives the text when the positive action is chosen; return `false`
         * or call [SancaiDialog.showError] to reject it and keep the dialog open
         */
        @JvmOverloads
        public fun input(
            initial: CharSequence? = null,
            hint: CharSequence? = null,
            inputType: Int = InputType.TYPE_CLASS_TEXT,
            multiline: Boolean = false,
            listener: OnInputListener? = null,
        ): Builder = apply {
            hasInput = true
            inputInitial = initial
            inputHint = hint
            this.inputType = inputType
            inputMultiline = multiline
            inputListener = listener
        }

        /** Whether back closes the dialog. Default `true`. */
        public fun cancelable(cancelable: Boolean): Builder = apply { this.cancelable = cancelable }

        /** Whether a touch outside the card closes a cancelable dialog. Default `false`. */
        public fun canceledOnTouchOutside(cancel: Boolean): Builder = apply { canceledOnTouchOutside = cancel }

        /** Called once when the dialog has gone, with a flag for "the positive action closed it". */
        public fun onDismiss(listener: OnDismissListener?): Builder = apply { dismissListener = listener }

        /** Builds the dialog without showing it. */
        public fun build(): SancaiDialog = SancaiDialog(context, this)

        /** Builds the dialog and shows it. */
        public fun show(): SancaiDialog = build().also { it.show() }

        /** The actions in the order positive, neutral, negative, with default labels filled in. */
        internal fun resolveActions(context: Context): List<Action> {
            val confirm = if (hasInput) R.string.sancai_action_save else R.string.sancai_action_ok
            val result = ArrayList<Action>(3)
            val none = positive == null && neutral == null && negative == null
            val first = positive ?: if (none) Action(ACTION_POSITIVE, "", SancaiTextTone.ACCENT, null) else null
            val last = negative ?: if (none && hasInput) Action(ACTION_NEGATIVE, "", SancaiTextTone.MUTED, null) else null
            first?.let { result.add(it.labelled(context.getString(confirm))) }
            neutral?.let { result.add(it) }
            last?.let { result.add(it.labelled(context.getString(R.string.sancai_action_cancel))) }
            return result
        }

        private fun Action.labelled(fallback: CharSequence): Action =
            if (label.isNotEmpty()) this else Action(which, fallback, tone, listener)
    }

    public companion object {
        /** The confirming action. */
        public const val ACTION_POSITIVE: Int = 0

        /** The optional third action. */
        public const val ACTION_NEUTRAL: Int = 1

        /** The dismissive action. */
        public const val ACTION_NEGATIVE: Int = 2

        /**
         * Asks for a confirmation with "Cancel" and one confirming action, and shows it.
         *
         * @param positiveLabel defaults to "OK"
         * @param danger paints the confirming action in the danger colour
         * @param onConfirm runs when the user confirms; the dialog then closes
         */
        @JvmStatic
        @JvmOverloads
        public fun confirm(
            context: Context,
            title: CharSequence?,
            message: CharSequence? = null,
            positiveLabel: CharSequence? = null,
            danger: Boolean = false,
            onConfirm: Runnable? = null,
        ): SancaiDialog = Builder(context)
            .title(title)
            .message(message)
            .positive(positiveLabel, if (danger) SancaiTextTone.DANGER else SancaiTextTone.ACCENT) {
                onConfirm?.run()
                true
            }
            .negative()
            .show()

        /** Shows a notice with a single "OK" action. */
        @JvmStatic
        @JvmOverloads
        public fun message(context: Context, title: CharSequence?, message: CharSequence? = null): SancaiDialog =
            Builder(context).title(title).message(message).positive().show()

        internal const val ACTION_ROW_DP: Float = 54f
        private const val PADDING_SIDE_DP = 18f
        private const val PADDING_TOP_DP = 19f
        private const val MESSAGE_GAP_DP = 7f
        private const val CONTENT_GAP_DP = 15f
        private const val ERROR_GAP_DP = 10f
        private const val ERROR_PADDING_X_DP = 16f
        private const val ERROR_PADDING_Y_DP = 10f
        private const val DIVIDER_GAP_DP = 18f
        private const val MAX_WIDTH_DP = 352f
        private const val SCREEN_MARGIN_DP = 32f
        private const val DIM_AMOUNT = 0.27f
        private const val RISE_DP = 11.5f
        private const val START_SCALE = 0.978f
        private const val STACK_FROM = 3
        private const val INPUT_MIN_LINES = 3
        private const val INPUT_MAX_LINES = 10
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

/** The window. Back and outside touches are routed through the owner so they animate and are guarded. */
private class CardDialog(context: Context, private val owner: SancaiDialog) : Dialog(context) {
    override fun cancel() {
        owner.cancelFromWindow()
    }
}

/**
 * The porcelain card. The shadow gutter is part of the view, so the visible card is 1.4 dp
 * narrower on each side than the window. Never taller than 80 % of the screen.
 */
private class DialogCard(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        background = SancaiSurfaceDrawable(context, SancaiMaterial.RADIUS_DIALOG_DP)
        setPadding(
            context.dpInt(SancaiGutters.SURFACE_SIDE_DP),
            context.dpInt(SancaiGutters.SURFACE_TOP_DP),
            context.dpInt(SancaiGutters.SURFACE_SIDE_DP),
            context.dpInt(SancaiGutters.SURFACE_BOTTOM_DP),
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val limit = (resources.displayMetrics.heightPixels * MAX_HEIGHT_FRACTION).roundToInt()
        val given = MeasureSpec.getSize(heightMeasureSpec)
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) limit else min(limit, given)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
    }

    override fun getAccessibilityClassName(): CharSequence = Dialog::class.java.name
}

private const val MAX_HEIGHT_FRACTION = 0.8f

/** One text action: a centred label that may wrap to two lines, with a focus rim and a paled disabled state. */
@SuppressLint("AppCompatCustomView", "ViewConstructor")
private class ActionView(context: Context, private val material: SancaiMaterial) : SancaiText(context) {
    private val rim = RectF()

    init {
        role = SancaiTextRole.LABEL
        gravity = Gravity.CENTER
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        val side = context.dpInt(ACTION_PADDING_X_DP)
        val vertical = context.dpInt(ACTION_PADDING_Y_DP)
        setPadding(side, vertical, side, vertical)
        minimumHeight = context.dpInt(SancaiDialog.ACTION_ROW_DP)
    }

    /** Width this label needs to stay on one line. */
    fun singleLineWidth(): Int = paint.measureText(text.toString()).let { kotlin.math.ceil(it).toInt() } +
        paddingLeft + paddingRight

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else SancaiControl.DISABLED_ALPHA / 255f
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isFocused && !isInTouchMode) {
            // Concentric with the 16 dp corner of the card, so the rim never crosses it.
            val inset = context.dp(FOCUS_INSET_DP)
            rim.set(inset, inset, width - inset, height - inset)
            material.drawFocusRim(canvas, rim, context.dp(SancaiMaterial.RADIUS_DIALOG_DP - FOCUS_INSET_DP))
        }
    }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name
}

private const val ACTION_PADDING_X_DP = 12f
private const val ACTION_PADDING_Y_DP = 8f
private const val FOCUS_INSET_DP = 5f

/**
 * The actions under a full-bleed divider. Side by side: equal cells split by vertical
 * dividers, first child at the start. Stacked: full-width rows split by horizontal dividers.
 * It stacks when told to, or when two labels would not fit on one line each.
 */
@SuppressLint("ViewConstructor")
private class ActionsLayout(context: Context, theme: SancaiTheme) : ViewGroup(context) {
    private val line = Paint().apply { color = theme.colors.divider }
    private val thickness = max(1, resources.displayMetrics.density.roundToInt())

    /** Stack whatever the labels need (three actions). Children are then already in top-down order. */
    var forcedStack = false

    var stacked = false
        private set

    init {
        setWillNotDraw(false)
    }

    /** Child for visual slot [slot]. A stack that was not forced shows the last child (positive) first. */
    private fun slotChild(slot: Int): View =
        getChildAt(if (stacked && !forcedStack) childCount - 1 - slot else slot)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val count = childCount
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val cell = if (count == 0) width else (width - thickness * (count - 1)) / count
        stacked = forcedStack || (count > 1 && (0 until count).any {
            (getChildAt(it) as? ActionView)?.let { action -> action.singleLineWidth() > cell } ?: false
        })
        var height = thickness
        if (stacked) {
            val spec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
            for (i in 0 until count) {
                getChildAt(i).measure(spec, unspecified)
                height += getChildAt(i).measuredHeight + if (i > 0) thickness else 0
            }
        } else {
            val spec = MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY)
            var row = 0
            for (i in 0 until count) {
                getChildAt(i).measure(spec, unspecified)
                row = max(row, getChildAt(i).measuredHeight)
            }
            // Every cell is as tall as the tallest label, so all of them centre on one line.
            val exact = MeasureSpec.makeMeasureSpec(row, MeasureSpec.EXACTLY)
            for (i in 0 until count) getChildAt(i).measure(spec, exact)
            height += row
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        var offset = 0
        for (slot in 0 until childCount) {
            val child = slotChild(slot)
            if (stacked) {
                val top = thickness + offset
                child.layout(0, top, width, top + child.measuredHeight)
                offset += child.measuredHeight + thickness
            } else {
                // The last cell takes the rounding remainder so the row always ends at the rim.
                val end = if (slot == childCount - 1) width else offset + child.measuredWidth
                if (rtl) child.layout(width - end, thickness, width - offset, height)
                else child.layout(offset, thickness, end, height)
                offset = end + thickness
            }
        }
    }

    /** Screen readers meet the actions in the order they are drawn, also in a stack that was not forced. */
    override fun addChildrenForAccessibility(outChildren: ArrayList<View>) {
        for (slot in 0 until childCount) {
            val child = slotChild(slot)
            if (child.visibility == VISIBLE) outChildren.add(child)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), thickness.toFloat(), line)
        for (slot in 1 until childCount) {
            val child = slotChild(slot)
            if (stacked) {
                canvas.drawRect(0f, (child.top - thickness).toFloat(), width.toFloat(), child.top.toFloat(), line)
            } else {
                val x = if (layoutDirection == LAYOUT_DIRECTION_RTL) child.right else child.left - thickness
                canvas.drawRect(x.toFloat(), thickness.toFloat(), (x + thickness).toFloat(), height.toFloat(), line)
            }
        }
    }
}

