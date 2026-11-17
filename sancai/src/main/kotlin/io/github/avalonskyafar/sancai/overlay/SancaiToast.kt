package io.github.avalonskyafar.sancai.overlay

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.interaction.SancaiPress
import io.github.avalonskyafar.sancai.material.SancaiGutters
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import io.github.avalonskyafar.sancai.widget.SancaiControl
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Passing feedback: a pill that rises at the bottom of the activity, says one thing and
 * sinks away again.
 *
 * The toast is a view inside the activity's content (`android.R.id.content`), not a system
 * toast and not a window, so it follows the Sancai theme of the activity and never outlives
 * it. A new toast removes the one on screen at once and takes its place.
 *
 * ```kotlin
 * SancaiToast.show(activity, "Saved")
 * SancaiToast.error(activity, "Could not reach the server")
 * SancaiToast.Builder(activity)
 *     .message("Entry deleted")
 *     .action("Undo") { restore() }
 *     .show()
 * ```
 *
 * A plain toast stays [DURATION_MS], one with an action [ACTION_DURATION_MS]. While a screen
 * reader explores the screen it stays at least [SCREEN_READER_DURATION_MS], is announced as
 * a polite live region and offers a "dismiss" accessibility action.
 *
 * Use it from the UI thread only.
 */
public class SancaiToast private constructor(
    private val host: FrameLayout?,
    /** The pill on screen. Exposed for tests and for hosts that want to style it further. */
    public val view: SancaiToastView,
    private val durationMs: Long,
    private val onAction: Runnable?,
    private val dismissListener: OnDismissListener?,
) {

    /** Told when a toast has left the screen. */
    public fun interface OnDismissListener {
        /**
         * [toast] has left the screen for any reason: its time ran out, [SancaiToast.dismiss],
         * a newer toast, or its action, in which case [actionTaken] is `true`.
         */
        public fun onDismiss(toast: SancaiToast, actionTaken: Boolean)
    }

    /** What the colour of the pill says. */
    public enum class Tone {
        /** Neutral porcelain with ink text: the ordinary message. */
        NEUTRAL,

        /** Glazed in the danger colour with white text: something failed. */
        DANGER,

        /** Glazed in the success colour with white text: something was completed. */
        SUCCESS,
    }

    private val theme = SancaiTheme.of(view.context)
    private val density = view.resources.displayMetrics.density
    private val risePx = RISE_DP * density
    private var state = STATE_NEW
    private var actionTaken = false

    private val leave = Runnable { dismiss() }
    private val place = Runnable { placeAboveInsets() }
    private val hostLayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> schedulePlacement() }
    private val attachState = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            schedulePlacement()
        }

        override fun onViewDetachedFromWindow(v: View) {
            // The activity went away or somebody removed the pill: nothing is left to animate.
            finish(remove = false)
        }
    }

    /** `true` from [show] until the pill has been removed; still `true` while it sinks away. */
    public val isShowing: Boolean
        get() = state == STATE_SHOWN || state == STATE_LEAVING

    /** The time this toast stays before it leaves by itself; 0 means until [dismiss]. */
    public val duration: Long
        get() {
            if (durationMs <= 0L) return 0L
            return if (screenReaderExplores(view.context)) max(durationMs, SCREEN_READER_DURATION_MS) else durationMs
        }

    /**
     * Puts the pill into the activity and lets it rise. A toast can be shown once; further
     * calls do nothing. Any toast already on screen is removed at once, without its exit
     * glide. Does nothing when the activity has no content frame, or when there is nothing
     * to say (a blank message and no action): no empty pill appears, the toast on screen
     * stays and the dismiss listener is not called.
     */
    public fun show(): SancaiToast {
        val host = host ?: return this
        if (state != STATE_NEW) return this
        if (view.message.isBlank() && !view.hasAction) return this
        removeCurrent(host)
        state = STATE_SHOWN
        view.owner = this
        view.actionClick = { takeAction() }
        ViewCompat.replaceAccessibilityAction(view, AccessibilityActionCompat.ACTION_DISMISS, null) { _, _ ->
            dismiss()
            true
        }
        // One prepared layer for the whole life: the glides move pixels and never repaint
        // the shadow.
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        )
        val side = (SIDE_MARGIN_DP * density).roundToInt()
        params.leftMargin = side
        params.rightMargin = side
        params.bottomMargin = (BOTTOM_MARGIN_DP * density).roundToInt() + bottomInset(host)
        host.addView(view, params)
        host.addOnLayoutChangeListener(hostLayout)
        view.addOnAttachStateChangeListener(attachState)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            schedulePlacement()
            insets
        }
        view.translationY = risePx
        view.animate()
            .translationY(0f)
            .setDuration(SancaiMotion.feedbackDuration(theme.reduceMotion))
            .setInterpolator(SancaiMotion.glide())
            .start()
        val stay = duration
        if (stay > 0L) view.postDelayed(leave, stay)
        return this
    }

    /**
     * Lets the pill sink away and removes it. Safe to call at any time and more than once;
     * the action callback is not called.
     */
    public fun dismiss() {
        if (state != STATE_SHOWN) return
        state = STATE_LEAVING
        view.removeCallbacks(leave)
        if (!view.isAttachedToWindow) {
            finish(remove = true)
            return
        }
        view.animate().cancel()
        view.animate()
            .translationY(risePx)
            .setDuration(SancaiMotion.feedbackDuration(theme.reduceMotion))
            .setInterpolator(SancaiMotion.glide())
            .withEndAction { finish(remove = true) }
            .start()
    }

    private fun takeAction() {
        if (state != STATE_SHOWN || actionTaken) return
        actionTaken = true
        onAction?.run()
        dismiss()
    }

    private fun finish(remove: Boolean) {
        if (state == STATE_GONE || state == STATE_NEW) return
        state = STATE_GONE
        view.animate().cancel()
        view.removeCallbacks(leave)
        view.removeCallbacks(place)
        view.removeOnAttachStateChangeListener(attachState)
        host?.removeOnLayoutChangeListener(hostLayout)
        ViewCompat.setOnApplyWindowInsetsListener(view, null)
        view.actionClick = null
        view.owner = null
        if (remove) (view.parent as? ViewGroup)?.removeView(view)
        view.translationY = 0f
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        dismissListener?.onDismiss(this, actionTaken)
    }

    private fun schedulePlacement() {
        if (state != STATE_SHOWN && state != STATE_LEAVING) return
        val host = host ?: return
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        val wanted = (BOTTOM_MARGIN_DP * density).roundToInt() + bottomInset(host)
        if (params.bottomMargin == wanted) return
        // Never change layout parameters in the middle of a layout pass.
        view.removeCallbacks(place)
        view.post(place)
    }

    private fun placeAboveInsets() {
        val host = host ?: return
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        val wanted = (BOTTOM_MARGIN_DP * density).roundToInt() + bottomInset(host)
        if (params.bottomMargin != wanted) {
            params.bottomMargin = wanted
            view.layoutParams = params
        }
    }

    /**
     * Assembles a toast with more than a message and a tone.
     *
     * ```kotlin
     * SancaiToast.Builder(activity).message("Entry deleted").action("Undo") { restore() }.show()
     * ```
     */
    public class Builder(private val activity: Activity) {
        private var message: CharSequence = ""
        private var tone: Tone = Tone.NEUTRAL
        private var actionLabel: CharSequence? = null
        private var onAction: Runnable? = null
        private var durationMs: Long = -1L
        private var dismissListener: OnDismissListener? = null

        /** The one thing the toast says. Long messages wrap to at most three lines. */
        public fun message(message: CharSequence): Builder = apply { this.message = message }

        /** The message as a string resource. */
        public fun message(@StringRes message: Int): Builder = apply { this.message = activity.getText(message) }

        /** The colour of the pill; [Tone.NEUTRAL] when not set. */
        public fun tone(tone: Tone): Builder = apply { this.tone = tone }

        /**
         * One text action at the end of the pill, for example "Undo". A tap ticks, runs
         * [onClick] once and dismisses the toast. With an action the toast stays
         * [ACTION_DURATION_MS] unless [duration] says otherwise.
         */
        public fun action(label: CharSequence, onClick: Runnable): Builder = apply {
            this.actionLabel = label
            this.onAction = onClick
        }

        /** The action label as a string resource. */
        public fun action(@StringRes label: Int, onClick: Runnable): Builder =
            action(activity.getText(label), onClick)

        /**
         * How long the toast stays, in milliseconds; 0 keeps it until [SancaiToast.dismiss].
         * A screen reader extends anything shorter to [SCREEN_READER_DURATION_MS].
         */
        public fun duration(durationMs: Long): Builder = apply { this.durationMs = max(0L, durationMs) }

        /**
         * Listener told once when the pill has left the screen, whatever made it leave. The
         * place to commit what an "Undo" action would have reverted.
         */
        public fun onDismiss(listener: OnDismissListener?): Builder = apply { dismissListener = listener }

        /** Creates the toast without showing it. */
        public fun build(): SancaiToast {
            val pill = SancaiToastView(activity)
            pill.message = message
            pill.tone = tone
            pill.actionLabel = actionLabel
            val stay = when {
                durationMs >= 0L -> durationMs
                actionLabel.isNullOrEmpty() -> DURATION_MS
                else -> ACTION_DURATION_MS
            }
            val host = activity.findViewById<View>(android.R.id.content) as? FrameLayout
            return SancaiToast(host, pill, stay, onAction, dismissListener)
        }

        /** Creates the toast and shows it. */
        public fun show(): SancaiToast = build().show()
    }

    public companion object {
        /** Time a plain toast stays, in milliseconds. */
        public const val DURATION_MS: Long = 2600L

        /** Time a toast with an action stays, in milliseconds. */
        public const val ACTION_DURATION_MS: Long = 5000L

        /** Shortest stay while a screen reader explores the screen, in milliseconds. */
        public const val SCREEN_READER_DURATION_MS: Long = 10000L

        /** Distance the pill travels when it rises and sinks, in dp. */
        public const val RISE_DP: Float = 18f

        /** Free space kept on each side of the pill, in dp. */
        public const val SIDE_MARGIN_DP: Float = 18f

        /** Distance between the pill and the bottom of the content (above bars and keyboard), in dp. */
        public const val BOTTOM_MARGIN_DP: Float = 28f

        private const val STATE_NEW = 0
        private const val STATE_SHOWN = 1
        private const val STATE_LEAVING = 2
        private const val STATE_GONE = 3
        private const val REMOVE_PASSES = 3

        /** Shows [message] in a pill of the given [tone] and returns the toast. */
        @JvmStatic
        @JvmOverloads
        public fun show(activity: Activity, message: CharSequence, tone: Tone = Tone.NEUTRAL): SancaiToast =
            Builder(activity).message(message).tone(tone).show()

        /** Shows [message] as an error: a pill glazed in the danger colour. */
        @JvmStatic
        public fun error(activity: Activity, message: CharSequence): SancaiToast =
            show(activity, message, Tone.DANGER)

        /** Removes the toast on screen in [activity], if there is one, without its exit glide. */
        @JvmStatic
        public fun cancel(activity: Activity) {
            val host = activity.findViewById<View>(android.R.id.content) as? FrameLayout ?: return
            removeCurrent(host)
        }

        private fun removeCurrent(host: FrameLayout) {
            // A dismiss listener may show a toast of its own while this one is being removed;
            // look again so that never more than one pill stays. Bounded, in case a listener
            // answers every removal with a new toast.
            for (pass in 0 until REMOVE_PASSES) {
                var removed = false
                for (index in host.childCount - 1 downTo 0) {
                    val child = host.getChildAt(index) as? SancaiToastView ?: continue
                    val owner = child.owner ?: continue
                    owner.finish(remove = true)
                    removed = true
                }
                if (!removed) return
            }
        }

        /**
         * The part of the navigation bar and the keyboard that covers the bottom of [host].
         * A content frame that the system already keeps clear of the bars gets 0.
         */
        private fun bottomInset(host: View): Int {
            val insets = ViewCompat.getRootWindowInsets(host) ?: return 0
            val covered = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
            ).bottom
            if (covered <= 0) return 0
            val location = IntArray(2)
            host.getLocationInWindow(location)
            val below = host.rootView.height - (location[1] + host.height)
            return max(0, covered - max(0, below))
        }

        private fun screenReaderExplores(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            return manager != null && manager.isEnabled && manager.isTouchExplorationEnabled
        }
    }
}

/**
 * The pill of a [SancaiToast]: a porcelain capsule with one centred message and, optionally,
 * one text action at the end behind a vertical hairline that runs from rim to rim.
 *
 * [SancaiToast] creates, shows and removes it; use the view directly only to place a pill of
 * your own, for example in a preview or a fixed status area:
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.overlay.SancaiToastView
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     app:sancaiToastMessage="@string/entry_deleted"
 *     app:sancaiToastTone="neutral"
 *     app:sancaiToastActionLabel="@string/undo" />
 * ```
 *
 * The view is a little larger than the capsule the eye sees: it keeps the shadow gutter of
 * porcelain inside its own bounds. The message wraps to at most three lines; the action never
 * wraps and never shrinks. The pill holds no user-changeable state, so it saves none.
 * Disabled, the whole pill pales and the action cannot be pressed.
 */
public class SancaiToastView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val theme = SancaiTheme.of(context)
    private val material = SancaiMaterial(context, theme)
    private val density = resources.displayMetrics.density
    private val body = RectF()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val messageView = TextView(context)
    private val actionView = ToastAction(context)

    internal var owner: SancaiToast? = null
    internal var actionClick: (() -> Unit)? = null

    /** The one thing the pill says. */
    public var message: CharSequence
        get() = messageView.text
        set(value) {
            messageView.text = value
        }

    /** The colour of the pill and of what is written on it. */
    public var tone: SancaiToast.Tone = SancaiToast.Tone.NEUTRAL
        set(value) {
            if (field != value) {
                field = value
                applyTone()
            }
        }

    /** Label of the text action at the end of the pill; `null` or empty for a pill without one. */
    public var actionLabel: CharSequence?
        get() = if (actionView.visibility == VISIBLE) actionView.text else null
        set(value) {
            actionView.text = value ?: ""
            actionView.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
            applyPadding()
            invalidate()
        }

    /** `true` when the pill carries an action. */
    public val hasAction: Boolean
        get() = actionView.visibility == VISIBLE

    /**
     * The rectangle of the visible capsule inside the view, without the shadow gutter, in
     * pixels. Valid after layout.
     */
    public fun bodyBounds(out: RectF) {
        out.set(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            (width - paddingRight).toFloat(),
            (height - paddingBottom).toFloat(),
        )
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setWillNotDraw(false)
        // The shadow lives inside the view: whole pixels, so the children sit on the body.
        super.setPadding(
            px(SancaiGutters.SURFACE_SIDE_DP, up = true),
            px(SancaiGutters.SURFACE_TOP_DP, up = true),
            px(SancaiGutters.SURFACE_SIDE_DP, up = true),
            px(SancaiGutters.SURFACE_BOTTOM_DP, up = true),
        )
        linePaint.style = Paint.Style.FILL

        for (text in arrayOf<TextView>(messageView, actionView)) {
            SancaiTextRole.FOOTNOTE.applyTo(text, theme)
            text.typeface = SancaiWeight.MEDIUM.typeface()
            text.gravity = Gravity.CENTER
        }
        messageView.maxLines = MESSAGE_MAX_LINES
        messageView.ellipsize = TextUtils.TruncateAt.END
        // Stretches only when the pill is given a fixed width; the text stays centred.
        addView(messageView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1f))

        actionView.maxLines = 1
        actionView.minimumWidth = px(MIN_TARGET_DP)
        actionView.minWidth = px(MIN_TARGET_DP)
        actionView.visibility = GONE
        SancaiPress.attach(actionView) { actionClick?.invoke() }
        actionView.setOnFocusChangeListener { _, _ -> invalidate() }
        addView(actionView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))

        ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)

        var initialTone = SancaiToast.Tone.NEUTRAL
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiToastView, defStyleAttr, 0)
            try {
                a.getText(R.styleable.SancaiToastView_sancaiToastMessage)?.let { messageView.text = it }
                val tones = SancaiToast.Tone.values()
                val index = a.getInt(R.styleable.SancaiToastView_sancaiToastTone, 0)
                initialTone = tones[index.coerceIn(0, tones.size - 1)]
                a.getText(R.styleable.SancaiToastView_sancaiToastActionLabel)?.let { actionLabel = it }
            } finally {
                a.recycle()
            }
        }
        tone = initialTone
        applyTone()
        applyPadding()
    }

    private fun px(dp: Float, up: Boolean = false): Int {
        val value = dp * density
        return if (up) kotlin.math.ceil(value).toInt() else value.roundToInt()
    }

    private fun applyTone() {
        val colors = theme.colors
        val glazed = tone != SancaiToast.Tone.NEUTRAL
        messageView.setTextColor(if (glazed) Color.WHITE else colors.ink)
        actionView.setTextColor(if (glazed) Color.WHITE else colors.accent)
        linePaint.color = if (glazed) SancaiColors.withAlpha(Color.WHITE, GLAZED_LINE_ALPHA) else colors.divider
        invalidate()
    }

    private fun applyPadding() {
        val vertical = px(PADDING_VERTICAL_DP)
        val edge = px(PADDING_HORIZONTAL_DP)
        val inner = px(ACTION_GAP_DP)
        if (hasAction) {
            messageView.setPaddingRelative(edge, vertical, inner, vertical)
            actionView.setPaddingRelative(inner, 0, edge, 0)
            // The action is a touch target: the capsule grows to 44 dp around it. The
            // message carries the height, the action fills whatever the capsule gets.
            messageView.minHeight = px(MIN_TARGET_DP, up = true)
        } else {
            messageView.setPaddingRelative(edge, vertical, edge, vertical)
            messageView.minHeight = 0
        }
    }

    private fun tint(): Int? = when (tone) {
        SancaiToast.Tone.NEUTRAL -> null
        SancaiToast.Tone.DANGER -> theme.colors.danger
        SancaiToast.Tone.SUCCESS -> theme.colors.success
    }

    override fun draw(canvas: Canvas) {
        if (isEnabled) {
            super.draw(canvas)
            return
        }
        val save = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), SancaiControl.DISABLED_ALPHA)
        super.draw(canvas)
        canvas.restoreToCount(save)
    }

    override fun onDraw(canvas: Canvas) {
        bodyBounds(body)
        val radius = body.height() / 2f
        material.drawSurface(canvas, body, radius, tint())
        if (hasAction) {
            // Rim to rim, on the edge where the action begins.
            val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
            val x = if (rtl) actionView.right.toFloat() else actionView.left.toFloat()
            val half = max(1f, (LINE_DP * density).roundToInt().toFloat()) / 2f
            canvas.drawRect(x - half, body.top, x + half, body.bottom, linePaint)
            if (actionView.isFocused && !isInTouchMode) material.drawFocusRim(canvas, body, radius)
        }
    }

    /**
     * The pill is solid: a touch on the message ends here and never reaches what lies under
     * the pill. Only the action reacts.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        return true
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        actionView.isEnabled = enabled
        invalidate()
    }

    override fun getAccessibilityClassName(): CharSequence = SancaiToastView::class.java.name

    /** The text action: a button to assistive technology, plain text to the eye. */
    private class ToastAction(context: Context) : TextView(context) {
        override fun getAccessibilityClassName(): CharSequence = Button::class.java.name
    }

    private companion object {
        const val PADDING_HORIZONTAL_DP = 18f
        const val PADDING_VERTICAL_DP = 12f
        const val ACTION_GAP_DP = 14f
        const val MIN_TARGET_DP = 44f
        const val LINE_DP = 1f
        const val MESSAGE_MAX_LINES = 3
        const val GLAZED_LINE_ALPHA = 82
    }
}
