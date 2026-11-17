package io.github.avalonskyafar.sancai.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.roundToInt

/**
 * A frame that shows exactly one of four states of an area: its content, a loading
 * placeholder, an empty message or an error message.
 *
 * The first child is the content; the layout takes one child only, like a scroll view. The
 * other three views are created the first time their state is shown: loading is a centred
 * spinner over a muted caption, empty and error are [SancaiStateView] presets. Configure the
 * presets with [setEmpty] and [setError], or replace any of the three with [setLoadingView],
 * [setEmptyView] and [setErrorView]. A state view lives in the hierarchy only while it is on
 * screen; the content stays attached and is hidden, so it keeps its own saved state.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiStateLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     app:sancaiStateLayoutState="loading"
 *     app:sancaiStateLayoutEmptyTitle="No accounts yet">
 *
 *     <ScrollView android:layout_width="match_parent" android:layout_height="match_parent" />
 * </io.github.avalonskyafar.sancai.widget.SancaiStateLayout>
 * ```
 *
 * Changing [state] lets the outgoing view sink 8 dp while the incoming view rises 8 dp into
 * place; the two overlap and only the last part of the way out is faded. With reduced
 * motion, or before the layout is on screen, the views are swapped at once.
 *
 * Loading never flashes: once the loading state is on screen it stays for at least
 * [minimumLoadingMs]. A state asked for earlier is remembered ([state] returns it at once)
 * and shown when the time is up ([displayedState] follows then).
 *
 * [state] is saved and restored. Each change on screen is announced to accessibility
 * services as a pane change, and keyboard focus that was inside the outgoing view moves
 * into the incoming one. Disabling the layout disables the three state views (the message
 * pales, its action cannot be pressed); the content is left to its owner.
 */
public class SancaiStateLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** What the area shows. */
    public enum class State { CONTENT, LOADING, EMPTY, ERROR }

    /** The text and action of a default message; kept so the view can be built lazily. */
    private class Message(
        var title: CharSequence?,
        var detail: CharSequence?,
        var actionLabel: CharSequence? = null,
        var listener: View.OnClickListener? = null,
    )

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val emptyMessage = Message(context.getText(R.string.sancai_state_layout_empty_title), null)
    private val errorMessage = Message(
        context.getText(R.string.sancai_state_layout_error_title),
        context.getText(R.string.sancai_state_layout_error_detail),
    )

    /** The three state views in use, by `State.ordinal`; `null` until first shown. */
    private val stateViews = arrayOfNulls<View>(State.entries.size)

    /** True for a slot that holds a view given by the caller rather than a default. */
    private val custom = BooleanArray(State.entries.size)
    private var addingStateView = false
    private var requested = State.CONTENT
    private var displayed = State.CONTENT
    private var loadingShownAt = 0L
    private var pendingShow: Runnable? = null
    private var transition: ValueAnimator? = null
    private var outgoing: View? = null
    private var incoming: View? = null
    private var outgoingAlpha = 1f

    /**
     * The content: the first (and only) child added by the owner, or `null` while there is
     * none. It is hidden, never removed, while another state shows.
     */
    public var contentView: View? = null
        private set

    /**
     * The state the area is asked to show. Setting it is the same as [setState] with
     * animation. Reads back at once even while [minimumLoadingMs] still holds the loading
     * state on screen; see [displayedState].
     */
    public var state: State
        get() = requested
        set(value) = setState(value, animate = true)

    /** The state that is on screen right now. Differs from [state] only while loading is held. */
    public val displayedState: State get() = displayed

    /**
     * The shortest time the loading state stays on screen once shown, in milliseconds.
     * Default 400; `0` switches the hold off. Applies to the next change away from loading.
     */
    public var minimumLoadingMs: Long = DEFAULT_MINIMUM_LOADING_MS
        set(value) {
            field = value.coerceAtLeast(0L)
        }

    /** The caption under the default spinner, also read by screen readers. Default "Loading". */
    public var loadingLabel: CharSequence = context.getText(R.string.sancai_state_loading)
        set(value) {
            field = value
            (stateViews[State.LOADING.ordinal] as? LoadingView)?.label = value
        }

    init {
        var initial = State.CONTENT
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiStateLayout, defStyleAttr, 0)
            try {
                initial = State.entries.getOrElse(a.getInt(R.styleable.SancaiStateLayout_sancaiStateLayoutState, 0)) { State.CONTENT }
                minimumLoadingMs = a.getInt(
                    R.styleable.SancaiStateLayout_sancaiStateLayoutMinimumLoadingMs,
                    DEFAULT_MINIMUM_LOADING_MS.toInt(),
                ).toLong()
                a.getText(R.styleable.SancaiStateLayout_sancaiStateLayoutLoadingLabel)?.let { loadingLabel = it }
                a.getText(R.styleable.SancaiStateLayout_sancaiStateLayoutEmptyTitle)?.let { emptyMessage.title = it }
                a.getText(R.styleable.SancaiStateLayout_sancaiStateLayoutEmptyDetail)?.let { emptyMessage.detail = it }
                a.getText(R.styleable.SancaiStateLayout_sancaiStateLayoutErrorTitle)?.let { errorMessage.title = it }
                if (a.hasValue(R.styleable.SancaiStateLayout_sancaiStateLayoutErrorDetail)) {
                    errorMessage.detail = a.getText(R.styleable.SancaiStateLayout_sancaiStateLayoutErrorDetail)
                }
                if (!a.getBoolean(R.styleable.SancaiStateLayout_android_enabled, true)) isEnabled = false
            } finally {
                a.recycle()
            }
        }
        // The content child arrives after the constructor; it is hidden as it is added.
        if (initial != State.CONTENT) {
            requested = initial
            show(initial, animate = false, announce = false)
        }
    }

    /**
     * Shows [state]. With [animate] the outgoing view sinks and the incoming view rises;
     * without it, with reduced motion, or before the layout is on screen, they swap at once.
     *
     * Leaving the loading state before [minimumLoadingMs] has passed is deferred until it
     * has; asking for another state meanwhile replaces the deferred one, and asking for
     * loading again cancels it.
     */
    public fun setState(state: State, animate: Boolean) {
        requested = state
        cancelPending()
        if (state == displayed) return
        val wait = if (displayed == State.LOADING && isAttachedToWindow) {
            minimumLoadingMs - (SystemClock.uptimeMillis() - loadingShownAt)
        } else {
            0L
        }
        if (wait <= 0L) {
            show(state, animate)
            return
        }
        val later = Runnable {
            pendingShow = null
            show(requested, animate)
        }
        pendingShow = later
        postDelayed(later, wait)
    }

    /**
     * The view used for [state]: the content, or the loading, empty or error view. `null`
     * for a state view that has not been shown yet and was not set, and for missing content.
     */
    public fun stateView(state: State): View? =
        if (state == State.CONTENT) contentView else stateViews[state.ordinal]

    /**
     * Replaces the loading view. `null` goes back to the default spinner with its caption.
     * A view without frame layout params is centred at full width. The pane announcement
     * uses the view's content description when it has one.
     */
    public fun setLoadingView(view: View?): Unit = replace(State.LOADING, view)

    /** Replaces the empty view. `null` goes back to the default message; see [setEmpty]. */
    public fun setEmptyView(view: View?): Unit = replace(State.EMPTY, view)

    /** Replaces the error view. `null` goes back to the default message; see [setError]. */
    public fun setErrorView(view: View?): Unit = replace(State.ERROR, view)

    /**
     * Configures the default empty message: an inbox icon, [title], [detail] and, when
     * [actionLabel] is given, the one action that fills the area. Parts passed as `null`
     * are left out. A custom view from [setEmptyView] is dropped.
     */
    @JvmOverloads
    public fun setEmpty(
        title: CharSequence?,
        detail: CharSequence? = null,
        actionLabel: CharSequence? = null,
        listener: OnClickListener? = null,
    ) {
        configure(State.EMPTY, emptyMessage, title, detail, actionLabel, listener)
    }

    /**
     * Configures the default error message: an error icon in the danger colour, [title],
     * [detail] and a retry action. The action shows when [retryLabel] or [listener] is
     * given; without a label it reads "Retry". A custom view from [setErrorView] is dropped.
     */
    @JvmOverloads
    public fun setError(
        title: CharSequence?,
        detail: CharSequence? = null,
        retryLabel: CharSequence? = null,
        listener: OnClickListener? = null,
    ) {
        val label = retryLabel ?: if (listener != null) context.getText(R.string.sancai_action_retry) else null
        configure(State.ERROR, errorMessage, title, detail, label, listener)
    }

    private fun configure(
        state: State,
        message: Message,
        title: CharSequence?,
        detail: CharSequence?,
        actionLabel: CharSequence?,
        listener: OnClickListener?,
    ) {
        message.title = title
        message.detail = detail
        message.actionLabel = actionLabel
        message.listener = listener
        if (custom[state.ordinal]) {
            replace(state, null)
        } else {
            (stateViews[state.ordinal] as? SancaiStateView)?.let { bind(it, message) }
        }
    }

    private fun bind(view: SancaiStateView, message: Message) {
        view.title = message.title
        view.detail = message.detail
        view.setAction(message.actionLabel, message.listener)
    }

    private fun replace(state: State, view: View?) {
        val slot = state.ordinal
        val old = stateViews[slot]
        if (view != null && old === view) return
        finishTransition()
        val hadFocus = old?.hasFocus() == true
        if (old != null && old.parent === this) removeView(old)
        stateViews[slot] = view
        custom[slot] = view != null
        if (displayed != state) return
        val shown = obtainView(state) ?: return
        reveal(shown)
        if (hadFocus) shown.requestFocus()
    }

    /** The view for [state], building the default on first use. `null` only for missing content. */
    private fun obtainView(state: State): View? {
        if (state == State.CONTENT) return contentView
        stateViews[state.ordinal]?.let { return it }
        val pad = (DEFAULT_PADDING_DP * resources.displayMetrics.density).roundToInt()
        val view: View = when (state) {
            State.LOADING -> LoadingView(context).also { it.label = loadingLabel }
            State.EMPTY -> SancaiStateView(context).also {
                it.kind = SancaiStateView.Kind.EMPTY
                bind(it, emptyMessage)
            }
            else -> SancaiStateView(context).also {
                it.kind = SancaiStateView.Kind.ERROR
                bind(it, errorMessage)
            }
        }
        view.setPadding(pad, pad, pad, pad)
        stateViews[state.ordinal] = view
        return view
    }

    private fun reveal(view: View) {
        if (view === contentView) {
            view.visibility = VISIBLE
            return
        }
        if (view.parent === this) return
        (view.parent as? ViewGroup)?.removeView(view)
        val own = view.layoutParams
        val params = if (own is LayoutParams) {
            own
        } else {
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        view.isEnabled = isEnabled
        addingStateView = true
        try {
            addView(view, params)
        } finally {
            addingStateView = false
        }
    }

    private fun conceal(view: View) {
        if (view === contentView) view.visibility = GONE else if (view.parent === this) removeView(view)
    }

    private fun show(state: State, animate: Boolean, announce: Boolean = true) {
        finishTransition()
        if (state == displayed) return
        val from = obtainView(displayed)
        val to = obtainView(state)
        val hadFocus = from?.hasFocus() == true
        displayed = state
        if (state == State.LOADING) loadingShownAt = SystemClock.uptimeMillis()
        to?.let(::reveal)
        if (hadFocus) to?.requestFocus()
        if (announce) ViewCompat.setAccessibilityPaneTitle(this, paneTitle(state))
        val moves = animate && !theme.reduceMotion && isAttachedToWindow && isLaidOut && (from != null || to != null)
        if (moves) startTransition(from, to) else from?.let(::conceal)
    }

    /** What a screen reader says when [state] comes on screen. */
    private fun paneTitle(state: State): CharSequence {
        val view = stateViews.getOrNull(state.ordinal)
        if (state != State.CONTENT && custom[state.ordinal]) {
            view?.contentDescription?.let { if (it.isNotEmpty()) return it }
        }
        return when (state) {
            State.CONTENT -> context.getText(R.string.sancai_state_layout_content_shown)
            State.LOADING -> if (custom[state.ordinal] || loadingLabel.isEmpty()) {
                context.getText(R.string.sancai_state_loading)
            } else {
                loadingLabel
            }
            State.EMPTY -> spoken(emptyMessage, custom[state.ordinal], R.string.sancai_state_layout_empty_title)
            State.ERROR -> spoken(errorMessage, custom[state.ordinal], R.string.sancai_state_layout_error_title)
        }
    }

    private fun spoken(message: Message, isCustom: Boolean, fallback: Int): CharSequence {
        val own = if (isCustom) null else message.title?.takeIf { it.isNotEmpty() } ?: message.detail
        return if (own.isNullOrEmpty()) context.getText(fallback) else own
    }

    private fun startTransition(from: View?, to: View?) {
        val shift = SHIFT_DP * resources.displayMetrics.density
        val glide = SancaiMotion.glide()
        outgoing = from
        incoming = to
        outgoingAlpha = from?.alpha ?: 1f
        to?.translationY = shift
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = SancaiMotion.feedbackDuration(false)
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            val time = it.animatedFraction
            val way = glide.getInterpolation(time)
            outgoing?.let { view ->
                view.translationY = shift * way
                // Content is never cross-faded: the view leaving pales on its last 40 % only.
                view.alpha = outgoingAlpha * (1f - ((time - FADE_START) / (1f - FADE_START)).coerceIn(0f, 1f))
            }
            incoming?.translationY = shift * (1f - way)
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (transition === animation) finishTransition()
            }
        })
        transition = animator
        animator.start()
    }

    /** Ends a running transition at its final frame: the outgoing view is gone, the incoming rests. */
    private fun finishTransition() {
        val running = transition
        transition = null
        running?.cancel()
        outgoing?.let {
            it.translationY = 0f
            it.alpha = outgoingAlpha
            conceal(it)
        }
        incoming?.translationY = 0f
        outgoing = null
        incoming = null
    }

    private fun cancelPending() {
        pendingShow?.let(::removeCallbacks)
        pendingShow = null
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        if (!addingStateView) {
            check(contentView == null) { "SancaiStateLayout can host only one content child" }
            contentView = child
            if (displayed != State.CONTENT) child.visibility = GONE
        }
        super.addView(child, index, params)
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (child === contentView) contentView = null
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        for (view in stateViews) view?.isEnabled = enabled
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Loading set up before the layout was on screen starts its minimum time now.
        if (displayed == State.LOADING) loadingShownAt = SystemClock.uptimeMillis()
    }

    override fun onDetachedFromWindow() {
        // Nothing may keep running or waiting for a view that is off screen: settle now.
        cancelPending()
        finishTransition()
        if (requested != displayed) show(requested, animate = false)
        super.onDetachedFromWindow()
    }

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        putInt(STATE_STATE, requested.ordinal)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        val restored = State.entries.getOrElse(state.getInt(STATE_STATE, requested.ordinal)) { requested }
        requested = restored
        cancelPending()
        show(restored, animate = false, announce = false)
    }

    override fun getAccessibilityClassName(): CharSequence = SancaiStateLayout::class.java.name

    /** The default loading view: a spinner over a muted caption, one element to a screen reader. */
    private class LoadingView(context: Context) : LinearLayout(context) {
        private val spinner = SancaiSpinner(context)
        private val caption = SancaiText.create(context, null, SancaiTextRole.CAPTION, SancaiTextTone.MUTED)

        var label: CharSequence? = null
            set(value) {
                field = value
                caption.text = value
                caption.visibility = if (value.isNullOrEmpty()) GONE else VISIBLE
                contentDescription = if (value.isNullOrEmpty()) spinner.contentDescription else value
            }

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            spinner.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            caption.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            caption.gravity = Gravity.CENTER_HORIZONTAL
            caption.textAlignment = View.TEXT_ALIGNMENT_CENTER
            addView(spinner, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            val captionParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            captionParams.topMargin = (CAPTION_GAP_DP * resources.displayMetrics.density).roundToInt()
            addView(caption, captionParams)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            label = null
        }

        override fun setEnabled(enabled: Boolean) {
            super.setEnabled(enabled)
            spinner.isEnabled = enabled
            caption.alpha = if (enabled) 1f else SancaiControl.DISABLED_ALPHA / 255f
        }
    }

    private companion object {
        const val DEFAULT_MINIMUM_LOADING_MS = 400L
        const val SHIFT_DP = 8f
        const val FADE_START = 0.6f
        const val DEFAULT_PADDING_DP = 16f
        const val CAPTION_GAP_DP = 12f
        const val STATE_SUPER = "sancai:stateLayout:super"
        const val STATE_STATE = "sancai:stateLayout:state"
    }
}
