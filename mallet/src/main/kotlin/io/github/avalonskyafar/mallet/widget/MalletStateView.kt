package io.github.avalonskyafar.mallet.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconView
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import kotlin.math.roundToInt

/**
 * A centred message for an area that is empty, has failed, is offline or has finished.
 *
 * From top to bottom: an outline icon, a title, a supporting detail and one optional action
 * as a neutral capsule button. Every part is optional; a part that is absent takes its gap
 * with it. There is no card around the message: it sits on the canvas, or inside whatever
 * card contains it. [compact] shrinks the icon and the gaps for use inside a card row.
 *
 * [kind] chooses the icon and its colour; [icon], [iconTone] and [iconColor] override the
 * preset afterwards. The view holds no state the user can change, so it saves none.
 *
 * To a screen reader the icon, title and detail are one element; the action is a second one.
 * Disabling the view pales the message and disables the action.
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.widget.MalletStateView
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:malletStateViewKind="offline"
 *     app:malletTitle="No connection"
 *     app:malletDetail="Nothing was changed. Check the network and try again."
 *     app:malletStateViewAction="Retry" />
 * ```
 */
public class MalletStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** What the area is telling the user. Each kind is a preset of icon and icon colour. */
    public enum class Kind(internal val icon: MalletIcon, internal val tone: MalletTextTone) {
        /** Nothing to show yet: an inbox in the muted colour. */
        EMPTY(MalletIcon.INBOX, MalletTextTone.MUTED),

        /** Something failed: the error mark in the danger colour. */
        ERROR(MalletIcon.ERROR, MalletTextTone.DANGER),

        /** The network is unreachable: a struck-through cloud in the muted colour. */
        OFFLINE(MalletIcon.CLOUD_OFF, MalletTextTone.MUTED),

        /** The work is finished: the success mark in the success colour. */
        SUCCESS(MalletIcon.SUCCESS, MalletTextTone.SUCCESS),

        /** A neutral note: the info mark in the muted colour. */
        INFO(MalletIcon.INFO, MalletTextTone.MUTED),
    }

    private val density = resources.displayMetrics.density
    private val message = LinearLayout(context)
    private val iconView = MalletIconView(context)
    private val titleView = MalletText.create(context, "", MalletTextRole.SUBHEAD, MalletTextTone.INK)
    private val detailView = MalletText.create(context, "", MalletTextRole.CAPTION, MalletTextTone.MUTED)
    private var ready = false

    /**
     * The action button. It is hidden until [setAction] gives it a label; use it to change
     * the variant or to show `isLoading` while a retry runs.
     */
    public val actionButton: MalletButton = MalletButton(context)

    /**
     * The preset. Setting it replaces [icon] and [iconTone] with the preset's and clears
     * [iconColor]; title, detail and action are left alone.
     */
    public var kind: Kind = Kind.EMPTY
        set(value) {
            field = value
            iconColor = null
            iconTone = value.tone
            icon = value.icon
        }

    /** The icon above the title, or `null` for none. */
    public var icon: MalletIcon? = Kind.EMPTY.icon
        set(value) {
            field = value
            iconView.icon = value
            refresh()
        }

    /** Colour role of the icon, used while [iconColor] is `null`. */
    public var iconTone: MalletTextTone = Kind.EMPTY.tone
        set(value) {
            field = value
            iconView.tone = value
        }

    /** An explicit icon colour; wins over [iconTone] until set back to `null`. */
    @get:ColorInt
    @setparam:ColorInt
    public var iconColor: Int? = null
        set(value) {
            field = value
            iconView.color = value
        }

    /** The title, one short phrase in ink; `null` or empty hides it. */
    public var title: CharSequence? = null
        set(value) {
            field = value
            titleView.text = value
            refresh()
        }

    /** The supporting sentence in the muted colour; `null` or empty hides it. */
    public var detail: CharSequence? = null
        set(value) {
            field = value
            detailView.text = value
            refresh()
        }

    /**
     * The label of the action, or `null` while there is no action. Setting it shows the action
     * as a neutral capsule button under the message, or removes it for `null` or an empty
     * label; the listener set with [setOnActionClickListener] stays.
     */
    public var actionLabel: CharSequence?
        get() = if (actionButton.visibility == VISIBLE) actionButton.text else null
        set(value) {
            val shown = !value.isNullOrEmpty()
            actionButton.text = if (shown) value else null
            actionButton.visibility = if (shown) VISIBLE else GONE
            refresh()
        }

    private var actionClickListener: OnClickListener? = null

    /**
     * Called when the user presses the action (touch, keyboard or accessibility click), with
     * [actionButton] as the view. The button has already given its haptic tick.
     */
    public fun setOnActionClickListener(listener: OnClickListener?) {
        actionClickListener = listener
    }

    /**
     * The small form for a card row: a 28 dp icon and tighter gaps. Text sizes stay, so the
     * message remains readable.
     */
    public var compact: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            refresh()
        }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        message.orientation = VERTICAL
        message.gravity = Gravity.CENTER_HORIZONTAL
        // The icon and the two lines are one element to a screen reader: the container
        // carries the text and the parts stay out of the accessibility tree.
        iconView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        titleView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        detailView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        for (line in arrayOf(titleView, detailView)) {
            line.gravity = Gravity.CENTER_HORIZONTAL
            line.textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        detailView.maxWidth = px(DETAIL_MAX_WIDTH_DP)
        message.addView(iconView, LayoutParams(0, 0))
        message.addView(titleView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        message.addView(detailView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(message, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        actionButton.minWidth = px(ACTION_MIN_WIDTH_DP)
        actionButton.visibility = GONE
        actionButton.setOnClickListener { actionClickListener?.onClick(it) }
        addView(actionButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        var initialKind = Kind.EMPTY
        var initialAction: CharSequence? = null
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletStateView, defStyleAttr, 0)
            try {
                initialKind = Kind.entries.getOrElse(a.getInt(R.styleable.MalletStateView_malletStateViewKind, 0)) { Kind.EMPTY }
                kind = initialKind
                if (a.hasValue(R.styleable.MalletStateView_malletIcon)) {
                    icon = MalletIcon.fromAttr(a.getInt(R.styleable.MalletStateView_malletIcon, -1))
                }
                title = a.getText(R.styleable.MalletStateView_malletTitle)
                detail = a.getText(R.styleable.MalletStateView_malletDetail)
                initialAction = a.getText(R.styleable.MalletStateView_malletStateViewAction)
                compact = a.getBoolean(R.styleable.MalletStateView_malletStateViewCompact, false)
                if (!a.getBoolean(R.styleable.MalletStateView_android_enabled, true)) isEnabled = false
            } finally {
                a.recycle()
            }
        } else {
            kind = initialKind
        }
        ready = true
        if (!initialAction.isNullOrEmpty()) actionLabel = initialAction else refresh()
    }

    /**
     * Sets the action in one call: [label] as [actionLabel] and [listener] as the click
     * listener, replacing the previous one.
     *
     * ```kotlin
     * empty.setAction("Add account") { openEditor() }
     * ```
     */
    public fun setAction(label: CharSequence?, listener: OnClickListener?) {
        actionClickListener = listener
        actionLabel = label
    }

    /** Lays the parts out again: which are present, their sizes and the gaps between them. */
    private fun refresh() {
        if (!ready) return
        val hasIcon = icon != null
        val hasTitle = !title.isNullOrEmpty()
        val hasDetail = !detail.isNullOrEmpty()
        val hasMessage = hasIcon || hasTitle || hasDetail
        val side = px(if (compact) ICON_COMPACT_DP else ICON_DP)
        iconView.strokeDp = if (compact) ICON_COMPACT_STROKE_DP else ICON_STROKE_DP
        iconView.visibility = if (hasIcon) VISIBLE else GONE
        titleView.visibility = if (hasTitle) VISIBLE else GONE
        detailView.visibility = if (hasDetail) VISIBLE else GONE
        message.visibility = if (hasMessage) VISIBLE else GONE

        (iconView.layoutParams as LayoutParams).let {
            it.width = side
            it.height = side
        }
        val iconGap = px(if (compact) ICON_GAP_COMPACT_DP else ICON_GAP_DP)
        val detailGap = px(if (compact) DETAIL_GAP_COMPACT_DP else DETAIL_GAP_DP)
        (titleView.layoutParams as LayoutParams).topMargin = if (hasIcon) iconGap else 0
        (detailView.layoutParams as LayoutParams).topMargin = when {
            hasTitle -> detailGap
            hasIcon -> iconGap
            else -> 0
        }
        (actionButton.layoutParams as LayoutParams).topMargin =
            if (hasMessage) px(if (compact) ACTION_GAP_COMPACT_DP else ACTION_GAP_DP) else 0

        // One sentence for the screen reader; a lone icon says nothing and is skipped.
        val spoken = listOfNotNull(title?.takeIf { hasTitle }, detail?.takeIf { hasDetail })
        message.contentDescription = if (spoken.isEmpty()) null else spoken.joinToString("\n")
        message.importantForAccessibility = if (spoken.isEmpty()) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        message.requestLayout()
        requestLayout()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        // Disabled pales: the message fades as one, the button pales itself.
        message.alpha = if (enabled) 1f else MalletControl.DISABLED_ALPHA / 255f
        actionButton.isEnabled = enabled
    }

    override fun getAccessibilityClassName(): CharSequence = "android.view.ViewGroup"

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private companion object {
        const val ICON_DP = 44f
        const val ICON_STROKE_DP = 1.6f
        const val ICON_GAP_DP = 16f
        const val DETAIL_GAP_DP = 6f
        const val ACTION_GAP_DP = 20f
        const val DETAIL_MAX_WIDTH_DP = 280f
        const val ACTION_MIN_WIDTH_DP = 160f

        // Compact: the icon drops to 28 dp and every gap to roughly five eighths.
        const val ICON_COMPACT_DP = 28f
        const val ICON_COMPACT_STROKE_DP = 1.5f
        const val ICON_GAP_COMPACT_DP = 10f
        const val DETAIL_GAP_COMPACT_DP = 4f
        const val ACTION_GAP_COMPACT_DP = 12f
    }
}
