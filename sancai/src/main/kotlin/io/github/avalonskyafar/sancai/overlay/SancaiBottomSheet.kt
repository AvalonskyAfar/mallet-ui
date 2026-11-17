package io.github.avalonskyafar.sancai.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconView
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.interaction.SancaiPress
import io.github.avalonskyafar.sancai.internal.useOwnFocusRim
import io.github.avalonskyafar.sancai.material.SancaiCorners
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiControl
import io.github.avalonskyafar.sancai.widget.SancaiDivider
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_WIDTH_DP = 560f
private const val GRABBER_WIDTH_DP = 36f
private const val GRABBER_HEIGHT_DP = 4f
private const val GRABBER_TOP_DP = 8f
private const val ACTION_HEIGHT_DP = 56f
private const val ACTION_ICON_DP = 20f
private const val ACTION_PADDING_DP = 16f
private const val HEADER_SIDE_DP = 18f
private const val TOP_GAP_DP = 24f

/** Air between the grabber and the first line of the header. */
private const val HEADER_TITLE_GAP_DP = 14f

/** The dialog dim of the design language: black at 27 %. */
private const val SCRIM_ALPHA = 69

/**
 * A porcelain sheet that rises from the bottom edge of the window and follows the finger
 * back down.
 *
 * The sheet lives in a full-window dialog of its own, so it traps focus while it is open. It
 * shows a grabber, an optional centred title and message, and then either any view or a list
 * of centred actions with an optional cancel action below a divider.
 *
 * ```kotlin
 * SancaiBottomSheet.Builder(activity)
 *     .title("Photo")
 *     .action("Share", icon = SancaiIcon.ARROW_UP) { share() }
 *     .action("Delete", SancaiTextTone.DANGER) { delete() }
 *     .cancel()
 *     .show()
 * ```
 *
 * Behaviour worth knowing:
 *
 * - It glides up over [SancaiMotion.DIALOG_MS] and glides down again; only the scrim fades,
 *   in proportion to the position of the sheet.
 * - Dragging down on the grabber, the header, or on content that is scrolled to its top
 *   moves the sheet. Letting go past [SancaiMotion.COMMIT_FRACTION] of its height, or faster
 *   than [SancaiMotion.COMMIT_VELOCITY_DP], dismisses it; otherwise it returns.
 * - A tap on the scrim, the back key and the accessibility "dismiss" action dismiss it
 *   unless it was built with `cancelable(false)`.
 * - An action ticks, runs its callback, and then dismisses the sheet.
 * - The sheet pads itself for the navigation bar and rises above the keyboard.
 * - It is a transient surface: it is not restored after the activity is recreated. Dismiss
 *   it in `onDestroy` if it can outlive a configuration change.
 *
 * The context must be an activity (or a themed wrapper around one), as for any dialog.
 */
public class SancaiBottomSheet private constructor(
    private val context: Context,
    private val builder: Builder,
) {

    /** Told when a sheet has left the screen, whatever made it leave. */
    public fun interface OnDismissListener {
        public fun onDismiss(sheet: SancaiBottomSheet)
    }

    private val theme = SancaiTheme.of(context)
    private var dialog: Dialog? = null
    private var dismissListener: OnDismissListener? = builder.dismissListener
    private var leaving = false

    /** Whether the sheet is on screen, including while it glides in or out. */
    public val isShowing: Boolean
        get() = dialog?.isShowing == true

    /** Whether scrim taps, the back key and drags may dismiss the sheet. */
    public var isCancelable: Boolean = builder.cancelable
        set(value) {
            field = value
            root.syncDismissAction()
        }

    /** The scrim and the sheet; the content view of the dialog. */
    internal val root: Root = Root(context, this)

    /** Replaces the listener that is told when the sheet has left the screen. */
    public fun setOnDismissListener(listener: OnDismissListener?) {
        dismissListener = listener
    }

    /**
     * Shows the sheet and lets it glide up. Does nothing when it is already showing.
     *
     * @return this sheet
     */
    public fun show(): SancaiBottomSheet {
        if (isShowing) return this
        leaving = false
        val sheetDialog = SheetDialog(context, this)
        dialog = sheetDialog
        // The dialog before this one lets go of the root only in its posted dismiss callback.
        (root.parent as? ViewGroup)?.removeView(root)
        sheetDialog.setContentView(
            root,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        sheetDialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setWindowAnimations(0)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window.setTitle(root.paneTitle())
            WindowCompat.setDecorFitsSystemWindows(window, false)
            styleSystemBars(window)
        }
        sheetDialog.setOnDismissListener {
            // Shown again in the meantime: the root belongs to the new dialog by now.
            if (dialog === sheetDialog) {
                root.cancelMotion()
                (root.parent as? ViewGroup)?.removeView(root)
                dialog = null
            }
            dismissListener?.onDismiss(this)
        }
        root.prepareEnter()
        sheetDialog.show()
        return this
    }

    /** Lets the sheet glide down and then removes it. Works whether or not it is cancelable. */
    public fun dismiss() {
        if (!isShowing || leaving) return
        leaving = true
        root.leave { dismissNow() }
    }

    /** Dismisses like the user would: ignored unless the sheet [isCancelable]. */
    internal fun cancel(): Boolean {
        if (!isCancelable) return false
        dismiss()
        return true
    }

    /**
     * The scrim and the sheet run behind both system bars, so the window must not paint a
     * strip of its own there. The status bar keeps the icons of the activity below; the
     * navigation bar takes the icons that read on the sheet.
     */
    @Suppress("DEPRECATION")
    private fun styleSystemBars(window: Window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        // Dark navigation icons need API 26; before that a veil keeps the light ones readable.
        val clear = theme.isDark || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        window.navigationBarColor = if (clear) Color.TRANSPARENT else Color.argb(SCRIM_ALPHA, 0, 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.isAppearanceLightNavigationBars = !theme.isDark
        hostWindow()?.let { host ->
            bars.isAppearanceLightStatusBars =
                WindowCompat.getInsetsController(host, host.decorView).isAppearanceLightStatusBars
        }
    }

    /** The window of the activity the sheet was built for, when there is one. */
    private fun hostWindow(): Window? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current.window
            current = current.baseContext
        }
        return null
    }

    private fun dismissNow() {
        val current = dialog ?: return
        // The window may already be gone when the activity was destroyed underneath the sheet.
        try {
            current.dismiss()
        } catch (ignored: IllegalArgumentException) {
            dialog = null
        }
    }

    private class SheetDialog(context: Context, private val owner: SancaiBottomSheet) :
        Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {

        @Deprecated("Deprecated in Java")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onBackPressed() {
            owner.cancel()
        }
    }

    internal class Action(
        val label: CharSequence,
        val tone: SancaiTextTone,
        val icon: SancaiIcon?,
        val enabled: Boolean,
        val onClick: Runnable?,
    )

    /**
     * Collects the parts of a sheet. Use either [view] or [action]; when both are given the
     * view comes first and the actions follow it.
     */
    public class Builder(private val context: Context) {
        internal var title: CharSequence? = null
        internal var message: CharSequence? = null
        internal var view: View? = null
        internal val actions: MutableList<Action> = ArrayList()
        internal var cancelLabel: CharSequence? = null
        internal var cancelable: Boolean = true
        internal var dismissListener: OnDismissListener? = null

        /** Centred title above the content; it is also announced as the name of the sheet. */
        public fun title(title: CharSequence?): Builder = apply { this.title = title }

        /** Centred, muted explanation below the title. */
        public fun message(message: CharSequence?): Builder = apply { this.message = message }

        /**
         * Any content. A scrolling view keeps its own gesture: the sheet only follows a
         * downward drag while that view is scrolled to its top.
         */
        public fun view(view: View?): Builder = apply { this.view = view }

        /**
         * Adds an action row. Its label is centred; the [icon] sits at the start edge on its
         * own and never shifts the label. Use [SancaiTextTone.DANGER] for a destructive
         * action. A row that is not [enabled] pales and cannot be operated.
         *
         * A tap ticks, runs [onClick], and then the sheet dismisses itself.
         */
        @JvmOverloads
        public fun action(
            label: CharSequence,
            tone: SancaiTextTone = SancaiTextTone.INK,
            icon: SancaiIcon? = null,
            enabled: Boolean = true,
            onClick: Runnable? = null,
        ): Builder = apply { actions.add(Action(label, tone, icon, enabled, onClick)) }

        /**
         * Adds the cancel action below a full-bleed divider. Without a [label] it reads
         * "Cancel" in the language of the device.
         */
        @JvmOverloads
        public fun cancel(label: CharSequence? = null): Builder = apply {
            cancelLabel = label ?: context.getString(R.string.sancai_action_cancel)
        }

        /** Whether scrim taps, the back key and drags may dismiss the sheet (default true). */
        public fun cancelable(cancelable: Boolean): Builder = apply { this.cancelable = cancelable }

        /** Listener told when the sheet has left the screen. */
        public fun onDismiss(listener: OnDismissListener?): Builder = apply { dismissListener = listener }

        /** Builds the sheet without showing it. */
        public fun build(): SancaiBottomSheet = SancaiBottomSheet(context, this)

        /** Builds the sheet and shows it. */
        public fun show(): SancaiBottomSheet = build().show()
    }

    /** Full-window host: paints the scrim, places the sheet and owns the drag. */
    @SuppressLint("ViewConstructor")
    internal class Root(context: Context, private val owner: SancaiBottomSheet) : FrameLayout(context) {
        private val theme = owner.theme
        private val density = resources.displayMetrics.density
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var animator: ValueAnimator? = null
        private var velocity: VelocityTracker? = null
        private var enterPending = false
        private var insetLeft = 0
        private var insetTop = 0
        private var insetRight = 0
        private var downX = 0f
        private var downY = 0f
        private var dragStartY = 0f
        private var dragStartOffset = 0f
        private var downOnSheet = false

        /** The finger the gesture follows: the one that went down first. */
        private var pointerId = MotionEvent.INVALID_POINTER_ID
        private var lastX = 0f
        private var lastY = 0f

        /** The porcelain body. */
        val sheet: SheetView = SheetView(context, owner)

        /** Whether the finger currently moves the sheet. */
        var isDragging: Boolean = false
            private set

        /** Distance of the sheet below its rest position, in pixels. */
        var offset: Float = 0f
            private set(value) {
                field = value
                sheet.translationY = value
                invalidate()
            }

        init {
            setWillNotDraw(false)
            clipChildren = true
            isMotionEventSplittingEnabled = false
            addView(sheet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                insetLeft = bars.left
                insetTop = bars.top
                insetRight = bars.right
                sheet.bottomInset = max(bars.bottom, ime.bottom)
                requestLayout()
                WindowInsetsCompat.CONSUMED
            }
            syncDismissAction()
        }

        fun paneTitle(): CharSequence =
            owner.builder.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.sancai_bottom_sheet_pane)

        /** Offers or withdraws the accessibility "dismiss" action with [isCancelable]. */
        fun syncDismissAction() {
            ViewCompat.setAccessibilityPaneTitle(sheet, paneTitle())
            if (owner.isCancelable) {
                ViewCompat.replaceAccessibilityAction(
                    sheet,
                    AccessibilityActionCompat.ACTION_DISMISS,
                    context.getString(R.string.sancai_bottom_sheet_dismiss),
                ) { _, _ -> owner.cancel() }
            } else {
                ViewCompat.removeAccessibilityAction(sheet, AccessibilityActionCompat.ACTION_DISMISS.id)
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val available = max(0, width - insetLeft - insetRight)
            val sheetWidth = min(available, (MAX_WIDTH_DP * density).roundToInt())
            val widthSpec = MeasureSpec.makeMeasureSpec(sheetWidth, MeasureSpec.EXACTLY)
            val gap = insetTop + (TOP_GAP_DP * density).roundToInt()
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                sheet.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                setMeasuredDimension(width, sheet.measuredHeight + gap)
            } else {
                val height = MeasureSpec.getSize(heightMeasureSpec)
                sheet.measure(widthSpec, MeasureSpec.makeMeasureSpec(max(0, height - gap), MeasureSpec.AT_MOST))
                setMeasuredDimension(width, height)
            }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val available = width - insetLeft - insetRight
            val sheetLeft = insetLeft + (available - sheet.measuredWidth) / 2
            sheet.layout(sheetLeft, height - sheet.measuredHeight, sheetLeft + sheet.measuredWidth, height)
            if (enterPending && sheet.height > 0) {
                enterPending = false
                offset = sheet.height.toFloat()
                // Start after this pass, so the first frame already shows the sheet below the edge.
                post { if (!owner.leaving) animateTo(0f, slideDuration(), null) }
            }
        }

        override fun onDraw(canvas: Canvas) {
            val alpha = (SCRIM_ALPHA * shownFraction()).roundToInt()
            if (alpha > 0) canvas.drawColor(Color.argb(alpha, 0, 0, 0))
        }

        /** 1 at rest, 0 when the sheet is completely below the edge. */
        fun shownFraction(): Float {
            val height = sheet.height
            if (height <= 0) return 1f
            return (1f - offset / height).coerceIn(0f, 1f)
        }

        fun prepareEnter() {
            cancelMotion()
            isDragging = false
            enterPending = true
            syncDismissAction()
            requestLayout()
        }

        /** Glides the sheet below the edge, then calls [end]. */
        fun leave(end: () -> Unit) {
            enterPending = false
            // A finger that is still down lets go of the sheet: its release must not stop the glide.
            isDragging = false
            val duration = if (offset > 0f) {
                SancaiMotion.settleDuration(shownFraction(), theme.reduceMotion)
            } else {
                slideDuration()
            }
            animateTo(sheet.height.toFloat(), duration, end)
        }

        fun cancelMotion() {
            animator?.cancel()
            animator = null
        }

        private fun slideDuration(): Long =
            if (theme.reduceMotion) SancaiMotion.REDUCED_MS else SancaiMotion.DIALOG_MS

        private fun animateTo(target: Float, durationMs: Long, end: (() -> Unit)?) {
            cancelMotion()
            if (!isLaidOut || durationMs <= 0L || offset == target) {
                offset = target
                end?.invoke()
                return
            }
            val motion = ValueAnimator.ofFloat(offset, target)
            motion.duration = durationMs
            motion.interpolator = SancaiMotion.glide()
            motion.addUpdateListener { offset = it.animatedValue as Float }
            motion.addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (animator === motion) animator = null
                    if (!cancelled) end?.invoke()
                }
            })
            animator = motion
            motion.start()
        }

        override fun onDetachedFromWindow() {
            cancelMotion()
            velocity?.recycle()
            velocity = null
            isDragging = false
            super.onDetachedFromWindow()
        }

        /** x of the followed finger; where it was last seen when it is not part of [event]. */
        private fun fingerX(event: MotionEvent): Float {
            val index = event.findPointerIndex(pointerId)
            if (index >= 0) lastX = event.getX(index)
            return lastX
        }

        /** y of the followed finger; where it was last seen when it is not part of [event]. */
        private fun fingerY(event: MotionEvent): Float {
            val index = event.findPointerIndex(pointerId)
            if (index >= 0) lastY = event.getY(index)
            return lastY
        }

        /** Whether [event] lifts the followed finger while another one stays down. */
        private fun liftsFollowed(event: MotionEvent): Boolean =
            event.actionMasked == MotionEvent.ACTION_POINTER_UP &&
                pointerId != MotionEvent.INVALID_POINTER_ID &&
                event.getPointerId(event.actionIndex) == pointerId

        private fun begin(event: MotionEvent) {
            pointerId = event.getPointerId(0)
            downX = event.x
            downY = event.y
            lastX = downX
            lastY = downY
            downOnSheet = event.x >= sheet.left && event.x < sheet.right && event.y >= sheet.top + offset
            velocity?.recycle()
            velocity = VelocityTracker.obtain().also { it.addMovement(event) }
        }

        private fun wantsDrag(event: MotionEvent): Boolean {
            if (isDragging || !downOnSheet || !owner.isCancelable || owner.leaving) return false
            // The followed finger is gone: what another finger does is not this gesture.
            if (event.findPointerIndex(pointerId) < 0) return false
            val dx = fingerX(event) - downX
            val dy = fingerY(event) - downY
            if (dy <= touchSlop || dy <= abs(dx)) return false
            return !sheetCanScrollUp(sheet, downX - sheet.left, downY - sheet.top - offset)
        }

        private fun startDrag(event: MotionEvent) {
            cancelMotion()
            enterPending = false
            isDragging = true
            dragStartY = fingerY(event)
            dragStartOffset = offset
            parent?.requestDisallowInterceptTouchEvent(true)
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> begin(event)
                MotionEvent.ACTION_MOVE -> {
                    velocity?.addMovement(event)
                    if (wantsDrag(event)) {
                        startDrag(event)
                        return true
                    }
                }
                // The other finger must not start a drag of its own later in this gesture.
                MotionEvent.ACTION_POINTER_UP -> if (liftsFollowed(event) && !isDragging) endGesture()
            }
            return isDragging
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> begin(event)
                MotionEvent.ACTION_MOVE -> {
                    velocity?.addMovement(event)
                    if (wantsDrag(event)) startDrag(event)
                    // Never above the rest position: the sheet only follows the finger down.
                    if (isDragging) offset = max(0f, dragStartOffset + fingerY(event) - dragStartY)
                }
                // The followed finger lifting ends the gesture, whether or not another is down.
                MotionEvent.ACTION_POINTER_UP -> if (liftsFollowed(event)) {
                    velocity?.addMovement(event)
                    if (isDragging) finishDrag(cancelled = false)
                    endGesture()
                }
                MotionEvent.ACTION_UP -> {
                    velocity?.addMovement(event)
                    if (isDragging) {
                        finishDrag(cancelled = false)
                    } else if (pointerId != MotionEvent.INVALID_POINTER_ID && !downOnSheet && isTap(event)) {
                        owner.cancel()
                    }
                    endGesture()
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) finishDrag(cancelled = true)
                    endGesture()
                }
            }
            return true
        }

        private fun isTap(event: MotionEvent): Boolean =
            abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop

        private fun finishDrag(cancelled: Boolean) {
            isDragging = false
            val tracker = velocity
            tracker?.computeCurrentVelocity(1000)
            val speed = (tracker?.getYVelocity(pointerId) ?: 0f).let { if (it.isNaN()) 0f else it }
            val fling = SancaiMotion.COMMIT_VELOCITY_DP * density
            val far = offset > sheet.height * SancaiMotion.COMMIT_FRACTION
            // A quick flick back up keeps the sheet, however far it was pulled.
            val commit = !cancelled && speed > -fling && (far || speed > fling)
            if (commit && owner.cancel()) return
            animateTo(0f, SancaiMotion.settleDuration(1f - shownFraction(), theme.reduceMotion), null)
        }

        private fun endGesture() {
            pointerId = MotionEvent.INVALID_POINTER_ID
            velocity?.recycle()
            velocity = null
        }
    }

    /** The sheet itself: porcelain with rounded top corners, a grabber, header and content. */
    @SuppressLint("ViewConstructor")
    internal class SheetView(context: Context, private val owner: SancaiBottomSheet) : LinearLayout(context) {
        private val theme = owner.theme
        private val material = SancaiMaterial(context, theme)
        private val body = RectF()
        private val pill = RectF()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        /** Action rows in order; the cancel row, when there is one, is not among them. */
        val actionRows: MutableList<ActionRow> = ArrayList()

        var titleView: TextView? = null
            private set
        var messageView: TextView? = null
            private set
        var cancelRow: ActionRow? = null
            private set

        /** Height of the navigation bar or the keyboard, kept free below the content. */
        var bottomInset: Int = 0
            set(value) {
                if (field != value) {
                    field = value
                    syncPadding()
                }
            }

        init {
            orientation = VERTICAL
            setWillNotDraw(false)
            syncPadding()
            populate()
        }

        private fun px(dp: Float): Int = (dp * resources.displayMetrics.density).roundToInt()

        private fun syncPadding() {
            setPadding(0, px(GRABBER_TOP_DP + GRABBER_HEIGHT_DP), 0, bottomInset)
        }

        override fun onDraw(canvas: Canvas) {
            // The body runs past the bottom edge, so it has no rim and no shadow gutter there.
            body.set(0f, 0f, width.toFloat(), height + material.dp(SancaiMaterial.RADIUS_DIALOG_DP))
            material.drawSurface(
                canvas, body, material.dp(SancaiMaterial.RADIUS_DIALOG_DP),
                shadow = false, corners = SancaiCorners.TOP,
            )
            val half = material.dp(GRABBER_WIDTH_DP) / 2f
            val top = material.dp(GRABBER_TOP_DP)
            val thickness = material.dp(GRABBER_HEIGHT_DP)
            pill.set(width / 2f - half, top, width / 2f + half, top + thickness)
            paint.color = theme.colors.hairline
            canvas.drawRoundRect(pill, thickness / 2f, thickness / 2f, paint)
        }

        private fun matchWrap(): LayoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        private fun populate() {
            val config = owner.builder
            val side = px(HEADER_SIDE_DP)
            val hasTitle = !TextUtils.isEmpty(config.title)
            val hasMessage = !TextUtils.isEmpty(config.message)
            val hasHeader = hasTitle || hasMessage
            val hasActions = config.actions.isNotEmpty()
            if (hasTitle) {
                val title = TextView(context)
                SancaiTextRole.HEADLINE.applyTo(title, theme)
                title.setTextColor(theme.colors.ink)
                title.gravity = Gravity.CENTER
                title.text = config.title
                title.setPadding(side, 0, side, 0)
                ViewCompat.setAccessibilityHeading(title, true)
                addView(title, matchWrap().apply { topMargin = px(HEADER_TITLE_GAP_DP) })
                titleView = title
            }
            if (hasMessage) {
                val message = TextView(context)
                SancaiTextRole.CAPTION.applyTo(message, theme)
                message.setTextColor(theme.colors.muted)
                message.gravity = Gravity.CENTER
                message.text = config.message
                message.setPadding(side, 0, side, 0)
                addView(message, matchWrap().apply { topMargin = px(if (hasTitle) 7f else HEADER_TITLE_GAP_DP) })
                messageView = message
            }
            config.view?.let { custom ->
                (custom.parent as? ViewGroup)?.removeView(custom)
                val frame = FrameLayout(context)
                frame.setPadding(side, px(if (hasHeader) 15f else 8f), side, px(HEADER_SIDE_DP))
                frame.addView(custom, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                addView(frame, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f))
            }
            val needsRule = hasActions && (hasHeader || config.view != null)
            if (needsRule) {
                // Same rhythm as a dialog: 18 dp of air, then a rule from rim to rim.
                addView(SancaiDivider(context), matchWrap().apply { topMargin = if (config.view != null) 0 else side })
            } else if (config.view == null) {
                addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, px(if (hasHeader) HEADER_SIDE_DP else 8f)))
            }
            if (hasActions) addActions(config.actions)
            config.cancelLabel?.let { label ->
                addView(SancaiDivider(context), matchWrap())
                val row = ActionRow(context, owner, Action(label, SancaiTextTone.MUTED, null, true, null), true)
                addView(row, matchWrap())
                cancelRow = row
            }
        }

        private fun addActions(actions: List<Action>) {
            val list = LinearLayout(context)
            list.orientation = VERTICAL
            actions.forEachIndexed { index, action ->
                if (index > 0) list.addView(SancaiDivider(context), matchWrap())
                val row = ActionRow(context, owner, action, false)
                list.addView(row, matchWrap())
                actionRows.add(row)
            }
            val scroller = ScrollView(context)
            scroller.overScrollMode = OVER_SCROLL_NEVER
            scroller.isVerticalScrollBarEnabled = false
            scroller.isFillViewport = true
            scroller.addView(list, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** One action: a centred label, an optional icon at the start edge, 56 dp high. */
    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    internal class ActionRow(
        context: Context,
        private val owner: SancaiBottomSheet,
        val action: Action,
        private val isCancel: Boolean,
    ) : FrameLayout(context) {
        private val theme = owner.theme
        private val material = SancaiMaterial(context, theme)
        private val rim = RectF()
        private val content = FrameLayout(context)

        val label: TextView = TextView(context)
        var iconView: SancaiIconView? = null
            private set

        init {
            val density = resources.displayMetrics.density
            val edge = (ACTION_PADDING_DP * density).roundToInt()
            val iconSize = (ACTION_ICON_DP * density).roundToInt()
            setWillNotDraw(false)
            minimumHeight = (ACTION_HEIGHT_DP * density).roundToInt()
            (if (isCancel) SancaiTextRole.LABEL else SancaiTextRole.BODY).applyTo(label, theme)
            label.setTextColor(action.tone.resolve(theme.colors))
            label.gravity = Gravity.CENTER
            label.text = action.label
            // Equal room on both sides keeps the label centred whether or not there is an icon.
            val reserve = edge + iconSize + (12f * density).roundToInt()
            label.setPadding(reserve, (8f * density).roundToInt(), reserve, (8f * density).roundToInt())
            label.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            content.addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            action.icon?.let { icon ->
                val view = SancaiIconView(context)
                view.icon = icon
                view.tone = if (action.tone == SancaiTextTone.INK) SancaiTextTone.MUTED else action.tone
                view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                val params = LayoutParams(iconSize, iconSize, Gravity.START or Gravity.CENTER_VERTICAL)
                params.marginStart = edge
                content.addView(view, params)
                iconView = view
            }
            content.minimumHeight = minimumHeight
            addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            contentDescription = action.label
            isEnabled = action.enabled
            isClickable = true
            isFocusable = action.enabled
            useOwnFocusRim()
            if (!action.enabled) alpha = SancaiControl.DISABLED_ALPHA / 255f
            val sink = SancaiMotion.PRESS_SINK_DP * density
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> if (view.isEnabled) SancaiPress.sink(content, sink)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> SancaiPress.release(content)
                }
                false
            }
            setOnClickListener { view ->
                if (owner.leaving) return@setOnClickListener
                // An action: tick first, then run it, and only then does the sheet leave.
                SancaiHaptics.tick(view)
                action.onClick?.run()
                // The cancel row is an explicit choice, so it works on a non-cancelable sheet too.
                owner.dismiss()
            }
        }

        override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"

        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previous: android.graphics.Rect?) {
            super.onFocusChanged(gainFocus, direction, previous)
            invalidate()
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            // Keyboard focus thickens a rim inside the row; touch never shows it.
            if (isFocused && !isInTouchMode) {
                rim.set(0f, 0f, width.toFloat(), height.toFloat())
                material.drawFocusRim(canvas, rim, material.dp(SancaiMaterial.RADIUS_CARD_DP))
            }
        }
    }

    private companion object {
        /**
         * Whether a scrolling view under the point ([x], [y]) of [view] can still scroll
         * towards its top, in which case a downward drag belongs to it and not to the sheet.
         */
        fun sheetCanScrollUp(view: View, x: Float, y: Float): Boolean {
            if (view.canScrollVertically(-1)) return true
            if (view !is ViewGroup) return false
            for (index in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(index)
                if (child.visibility != View.VISIBLE) continue
                val childX = x + view.scrollX - child.left
                val childY = y + view.scrollY - child.top
                if (childX < 0f || childY < 0f || childX >= child.width || childY >= child.height) continue
                if (sheetCanScrollUp(child, childX, childY)) return true
            }
            return false
        }
    }
}
