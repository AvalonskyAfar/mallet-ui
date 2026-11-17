package io.github.avalonskyafar.sancai.overlay

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.interaction.SancaiHorizontalDragOwner
import io.github.avalonskyafar.sancai.material.SancaiGutters
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.material.SancaiSurfaceDrawable
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A small porcelain panel that floats above a screen and is moved with the finger: a status
 * read-out, a mini player, a debug badge.
 *
 * It is a [FrameLayout] that holds any content, 12 dp from the sides of the porcelain body and
 * 8 dp from its top and bottom. The view is slightly larger than the body the eye sees, because
 * the shadow is drawn inside the view (1.4 dp on each side, 1 dp above, 4.8 dp below).
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.overlay.SancaiFloatingPanel
 *     android:id="@+id/status"
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     app:sancaiFloatingPanelPositionX="1"
 *     app:sancaiFloatingPanelPositionY="0.2"
 *     app:sancaiFloatingPanelSnapToEdge="true">
 *     <io.github.avalonskyafar.sancai.widget.SancaiText ... />
 * </io.github.avalonskyafar.sancai.overlay.SancaiFloatingPanel>
 * ```
 *
 * From code, [attach] adds one to an activity and [remove] takes it away again.
 *
 * **Moving.** A drag that starts anywhere on the panel, including on its content, moves it. The
 * panel cannot leave the padded bounds of its parent. On release it stays where it is, or glides
 * to the nearer side when [snapToEdge] is set. While it is dragged it draws the tightened shadow
 * of a pressed body. A touch that stays inside the touch slop is a click.
 *
 * **Position.** [positionX] and [positionY] are fractions of the free travel: 0 is the start
 * (or top) edge of the parent, 1 the end (or bottom) edge, whatever the sizes of panel and
 * parent. The position therefore survives rotation and resizing, mirrors in right-to-left
 * layouts, and is saved with the view state (the view needs an id; [attach] gives it one). The
 * panel is placed with translation, so give it `wrap_content` layout params without gravity
 * inside a `FrameLayout`-like parent.
 *
 * **Keyboard and accessibility.** With keyboard focus the arrow keys move the panel in steps of
 * 16 dp (an arrow that cannot move it further is not consumed, so focus travels on) and Space or
 * Enter click it. Accessibility services get the actions "move to the start / end / top / bottom
 * edge". [setOnPositionChangeListener] fires for all of these and for drags, never for setters.
 *
 * **As a system overlay.** The same view can float over other apps in a `WindowManager` window
 * of type `TYPE_ACCESSIBILITY_OVERLAY` (from an accessibility service) or
 * `TYPE_APPLICATION_OVERLAY` (with the "draw over other apps" permission). [overlayLayoutParams]
 * returns non-focusable, wrap-content params and [bindToWindow] makes dragging move the window
 * instead of translating the view; the fractions then refer to the screen:
 *
 * ```kotlin
 * val panel = SancaiFloatingPanel(service)          // a context with a Sancai theme
 * panel.addView(SancaiText.create(service, "Capturing", SancaiTextRole.CAPTION))
 * val params = SancaiFloatingPanel.overlayLayoutParams(
 *     WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
 * val windowManager = service.getSystemService(WindowManager::class.java)
 * panel.bindToWindow(windowManager, params)         // before addView
 * windowManager.addView(panel, params)
 * // later
 * panel.remove()
 * ```
 */
public open class SancaiFloatingPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), SancaiHorizontalDragOwner {

    /** The theme this panel was created with. */
    protected val theme: SancaiTheme = SancaiTheme.of(context)
    private val material = SancaiMaterial(context, theme)
    private val surface = SancaiSurfaceDrawable(material, SancaiMaterial.RADIUS_PANEL_DP)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val body = RectF()
    private val viewBounds = Rect()

    private var fractionX = 0f
    private var fractionY = 0f

    // Gesture state. Offsets are pixels from the start / top limit of the travel.
    private var downRawX = 0f
    private var downRawY = 0f
    private var downOffsetX = 0f
    private var downOffsetY = 0f
    private var tracking = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var snapAnimator: ValueAnimator? = null

    private var windowManager: WindowManager? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var hostParent: View? = null
    private val parentLayoutListener =
        OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyPosition() }

    /** True while the finger is moving the panel. */
    public var isDragging: Boolean = false
        private set

    /**
     * Whether the panel glides to the nearer side of its parent when a drag ends. The vertical
     * position is kept. Off by default: the panel stays where it is released.
     */
    public var snapToEdge: Boolean = false

    /** Called when the user has moved the panel. */
    public fun interface OnPositionChangeListener {
        /** The user moved [panel]; [x] and [y] are its new [positionX] and [positionY]. */
        public fun onPositionChanged(panel: SancaiFloatingPanel, x: Float, y: Float)
    }

    private var positionChangeListener: OnPositionChangeListener? = null

    /**
     * Listener for moves made by the user: the end of a drag, an arrow key, an accessibility
     * action. Not called by [setPosition] or on restore.
     */
    public fun setOnPositionChangeListener(listener: OnPositionChangeListener?) {
        positionChangeListener = listener
    }

    /**
     * Horizontal position as a fraction of the free travel: 0 rests on the start edge of the
     * parent's padded bounds, 1 on the end edge. Setting it moves the panel at once.
     */
    public var positionX: Float
        get() = fractionX
        set(value) = setPosition(value, fractionY)

    /** Vertical position as a fraction of the free travel: 0 is the top, 1 the bottom. */
    public var positionY: Float
        get() = fractionY
        set(value) = setPosition(fractionX, value)

    /** True once [bindToWindow] has made this panel a window of its own. */
    public val isBoundToWindow: Boolean get() = windowManager != null

    init {
        val side = material.dp(CONTENT_PADDING_HORIZONTAL_DP + SancaiGutters.SURFACE_SIDE_DP).roundToInt()
        val top = material.dp(CONTENT_PADDING_VERTICAL_DP + SancaiGutters.SURFACE_TOP_DP).roundToInt()
        val bottom = material.dp(CONTENT_PADDING_VERTICAL_DP + SancaiGutters.SURFACE_BOTTOM_DP).roundToInt()
        if (paddingLeft == 0 && paddingTop == 0 && paddingRight == 0 && paddingBottom == 0) {
            setPadding(side, top, side, bottom)
        }
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiFloatingPanel, defStyleAttr, 0)
            try {
                snapToEdge = a.getBoolean(R.styleable.SancaiFloatingPanel_sancaiFloatingPanelSnapToEdge, false)
                fractionX = a.getFloat(R.styleable.SancaiFloatingPanel_sancaiFloatingPanelPositionX, 0f)
                    .coerceIn(0f, 1f)
                fractionY = a.getFloat(R.styleable.SancaiFloatingPanel_sancaiFloatingPanelPositionY, 0f)
                    .coerceIn(0f, 1f)
            } finally {
                a.recycle()
            }
        }
        background = surface
        isFocusable = true
        registerMoveActions()
    }

    /**
     * Moves the panel to the given fractions (clamped to 0..1). With [animate] it glides there;
     * it jumps when motion is reduced or the panel is not laid out yet. Never calls the
     * listener.
     */
    @JvmOverloads
    public fun setPosition(x: Float, y: Float, animate: Boolean = false) {
        val toX = if (x.isNaN()) 0f else x.coerceIn(0f, 1f)
        val toY = if (y.isNaN()) 0f else y.coerceIn(0f, 1f)
        snapAnimator?.cancel()
        if (!animate || theme.reduceMotion || !isLaidOut || !isAttachedToWindow) {
            fractionX = toX
            fractionY = toY
            applyPosition()
            return
        }
        val fromX = fractionX
        val fromY = fractionY
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SancaiMotion.controlDuration(theme.reduceMotion)
            interpolator = SancaiMotion.glide()
            addUpdateListener {
                val t = it.animatedValue as Float
                fractionX = fromX + (toX - fromX) * t
                fractionY = fromY + (toY - fromY) * t
                applyPosition()
            }
            start()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Geometry
    // ---------------------------------------------------------------------------------------

    private val isRtl: Boolean get() = layoutDirection == LAYOUT_DIRECTION_RTL

    /** Free horizontal travel in pixels: the room the panel has between its two limits. */
    private fun travelX(): Float {
        val room = if (isBoundToWindow) {
            resources.displayMetrics.widthPixels
        } else {
            val parent = parent as? View ?: return 0f
            parent.width - parent.paddingLeft - parent.paddingRight
        }
        return (room - width).coerceAtLeast(0).toFloat()
    }

    private fun travelY(): Float {
        val room = if (isBoundToWindow) {
            resources.displayMetrics.heightPixels
        } else {
            val parent = parent as? View ?: return 0f
            parent.height - parent.paddingTop - parent.paddingBottom
        }
        return (room - height).coerceAtLeast(0).toFloat()
    }

    /** Puts the view (or its window) where the fractions say. */
    private fun applyPosition() {
        val offsetX = (fractionX * travelX()).roundToInt()
        val offsetY = (fractionY * travelY()).roundToInt()
        val params = windowParams
        val manager = windowManager
        if (params != null && manager != null) {
            if (params.x == offsetX && params.y == offsetY) return
            params.x = offsetX
            params.y = offsetY
            if (isAttachedToWindow) {
                try {
                    manager.updateViewLayout(this, params)
                } catch (ignored: RuntimeException) {
                    // The window went away under us; remove() cleans up.
                }
            }
            return
        }
        val parent = parent as? View ?: return
        val targetLeft = if (isRtl) {
            parent.width - parent.paddingRight - width - offsetX
        } else {
            parent.paddingLeft + offsetX
        }
        translationX = (targetLeft - left).toFloat()
        translationY = (parent.paddingTop + offsetY - top).toFloat()
    }

    /** Follows the finger: offsets are pixels from the start / top limit, clamped to the travel. */
    private fun moveToOffsets(offsetX: Float, offsetY: Float) {
        val travelX = travelX()
        val travelY = travelY()
        if (travelX > 0f) fractionX = offsetX.coerceIn(0f, travelX) / travelX
        if (travelY > 0f) fractionY = offsetY.coerceIn(0f, travelY) / travelY
        applyPosition()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        viewBounds.set(0, 0, width, height)
        surface.bodyBounds(viewBounds, body)
        applyPosition()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The parent can change size (rotation, keyboard) without re-laying this view out.
        hostParent = (parent as? View)?.also { it.addOnLayoutChangeListener(parentLayoutListener) }
    }

    override fun onDetachedFromWindow() {
        endDrag(cancelled = true)
        snapAnimator?.end()
        snapAnimator = null
        hostParent?.removeOnLayoutChangeListener(parentLayoutListener)
        hostParent = null
        super.onDetachedFromWindow()
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        if (isLaidOut) applyPosition()
    }

    // ---------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------

    override fun draw(canvas: Canvas) {
        // Disabled pales as a whole; the view alpha stays free for the host (a translucent overlay).
        if (isEnabled) {
            super.draw(canvas)
        } else {
            val save = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), DISABLED_ALPHA)
            super.draw(canvas)
            canvas.restoreToCount(save)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isFocused && hasWindowFocus() && !isInTouchMode) {
            material.drawFocusRim(canvas, body, material.dp(SancaiMaterial.RADIUS_PANEL_DP))
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) endDrag(cancelled = true)
        super.setEnabled(enabled)
        invalidate()
    }

    // ---------------------------------------------------------------------------------------
    // Touch
    // ---------------------------------------------------------------------------------------

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginTracking(event)
            MotionEvent.ACTION_MOVE -> if (tracking && passedSlop(event)) {
                // A drag that starts on a child (a button inside the panel) moves the panel.
                startDrag()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> handOver(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return false
    }

    @Suppress("ClickableViewAccessibility") // performClick() is called for a touch inside the slop.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginTracking(event)
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return true
                if (!isDragging && passedSlop(event)) startDrag()
                if (isDragging) {
                    val dx = rawX(event, index) - downRawX
                    val dy = rawY(event, index) - downRawY
                    moveToOffsets(downOffsetX + if (isRtl) -dx else dx, downOffsetY + dy)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> handOver(event)
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    endDrag(cancelled = false)
                } else if (tracking) {
                    tracking = false
                    click()
                }
            }
            MotionEvent.ACTION_CANCEL -> endDrag(cancelled = true)
        }
        return true
    }

    private fun beginTracking(event: MotionEvent) {
        snapAnimator?.cancel()
        activePointerId = event.getPointerId(0)
        rebase(event, 0)
        tracking = true
    }

    /** Measures the gesture from where the pointer at [index] and the panel are now. */
    private fun rebase(event: MotionEvent, index: Int) {
        downRawX = rawX(event, index)
        downRawY = rawY(event, index)
        downOffsetX = fractionX * travelX()
        downOffsetY = fractionY * travelY()
    }

    /** When the finger that moves the panel lifts while another is down, that one takes over. */
    private fun handOver(event: MotionEvent) {
        if (!tracking || event.getPointerId(event.actionIndex) != activePointerId) return
        val next = if (event.actionIndex == 0) 1 else 0
        if (next >= event.pointerCount) return
        activePointerId = event.getPointerId(next)
        rebase(event, next)
    }

    // Screen coordinates of any pointer (the platform only offers them for all pointers from
    // API 29): the panel moves under the finger, so view coordinates would feed back.
    private fun rawX(event: MotionEvent, index: Int): Float = event.getX(index) + event.rawX - event.x

    private fun rawY(event: MotionEvent, index: Int): Float = event.getY(index) + event.rawY - event.y

    private fun passedSlop(event: MotionEvent): Boolean {
        val index = event.findPointerIndex(activePointerId)
        if (index < 0) return false
        return hypot(rawX(event, index) - downRawX, rawY(event, index) - downRawY) > touchSlop
    }

    private fun startDrag() {
        isDragging = true
        surface.pressed = true
        invalidate()
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    /** Ends a drag. The panel stays put or snaps; the listener hears about it unless [cancelled]. */
    private fun endDrag(cancelled: Boolean) {
        tracking = false
        if (!isDragging) return
        isDragging = false
        surface.pressed = false
        invalidate()
        if (snapToEdge) {
            val side = if (fractionX < 0.5f) 0f else 1f
            setPosition(side, fractionY, animate = !cancelled)
            // Report where the panel comes to rest, not where the glide starts.
            if (!cancelled) positionChangeListener?.onPositionChanged(this, side, fractionY)
        } else if (!cancelled) {
            positionChangeListener?.onPositionChanged(this, fractionX, fractionY)
        }
    }

    private fun click() {
        val heard = hasOnClickListeners()
        performClick()
        if (heard) SancaiHaptics.tick(this)
    }

    /** A move asked for by key or accessibility action: moves, then tells the listener. */
    private fun userMoveTo(x: Float, y: Float, animate: Boolean): Boolean {
        val toX = x.coerceIn(0f, 1f)
        val toY = y.coerceIn(0f, 1f)
        if (abs(toX - fractionX) < EPSILON && abs(toY - fractionY) < EPSILON) return false
        setPosition(toX, toY, animate)
        positionChangeListener?.onPositionChanged(this, toX, toY)
        return true
    }

    // ---------------------------------------------------------------------------------------
    // Keyboard and accessibility
    // ---------------------------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isEnabled) {
            val step = material.dp(KEY_STEP_DP)
            val startward = if (isRtl) step else -step
            val handled = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> nudge(startward, 0f)
                KeyEvent.KEYCODE_DPAD_RIGHT -> nudge(-startward, 0f)
                KeyEvent.KEYCODE_DPAD_UP -> nudge(0f, -step)
                KeyEvent.KEYCODE_DPAD_DOWN -> nudge(0f, step)
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                    (event.repeatCount == 0 && hasOnClickListeners()).also { if (it) click() }
                else -> false
            }
            if (handled) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Moves by pixels along the start-to-end and top-to-bottom axes; false at the limit. */
    private fun nudge(dx: Float, dy: Float): Boolean {
        val travelX = travelX()
        val travelY = travelY()
        val toX = if (travelX > 0f && dx != 0f) fractionX + dx / travelX else fractionX
        val toY = if (travelY > 0f && dy != 0f) fractionY + dy / travelY else fractionY
        return userMoveTo(toX, toY, animate = false)
    }

    private fun registerMoveActions() {
        val moves = listOf<Pair<Int, () -> Boolean>>(
            R.string.sancai_floating_panel_move_start to { userMoveTo(0f, fractionY, animate = true) },
            R.string.sancai_floating_panel_move_end to { userMoveTo(1f, fractionY, animate = true) },
            R.string.sancai_floating_panel_move_top to { userMoveTo(fractionX, 0f, animate = true) },
            R.string.sancai_floating_panel_move_bottom to { userMoveTo(fractionX, 1f, animate = true) },
        )
        for ((label, move) in moves) {
            ViewCompat.addAccessibilityAction(this, context.getString(label)) { _, _ -> isEnabled && move() }
        }
    }

    override fun getAccessibilityClassName(): CharSequence = SancaiFloatingPanel::class.java.name

    // ---------------------------------------------------------------------------------------
    // Saved state
    // ---------------------------------------------------------------------------------------

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        putFloat(STATE_X, fractionX)
        putFloat(STATE_Y, fractionY)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        @Suppress("DEPRECATION")
        super.onRestoreInstanceState(state.getParcelable(STATE_SUPER))
        setPosition(state.getFloat(STATE_X, fractionX), state.getFloat(STATE_Y, fractionY))
    }

    // ---------------------------------------------------------------------------------------
    // Hosting
    // ---------------------------------------------------------------------------------------

    /**
     * Makes the panel a window of its own: from now on a drag updates the position of [params]
     * through [windowManager] instead of translating the view, and the fractions refer to the
     * screen. Call it before `windowManager.addView(panel, params)`, with params whose gravity
     * is top-start (see [overlayLayoutParams]). The x and y of [params] are overwritten from
     * [positionX] and [positionY].
     */
    public fun bindToWindow(windowManager: WindowManager, params: WindowManager.LayoutParams) {
        this.windowManager = windowManager
        windowParams = params
        translationX = 0f
        translationY = 0f
        applyPosition()
    }

    /**
     * Takes the panel off the screen: out of its parent, or out of the window manager when it
     * was bound with [bindToWindow]. The position is kept, so the panel reappears where it was
     * when it is added again.
     */
    public fun remove() {
        endDrag(cancelled = true)
        val manager = windowManager
        if (manager != null) {
            try {
                manager.removeView(this)
            } catch (ignored: RuntimeException) {
                // Not added, or the window is already gone.
            }
        } else {
            (parent as? ViewGroup)?.removeView(this)
        }
    }

    public companion object {
        /** Space between the sides of the porcelain body and the content, in dp. */
        public const val CONTENT_PADDING_HORIZONTAL_DP: Float = 12f

        /** Space between the top and bottom of the porcelain body and the content, in dp. */
        public const val CONTENT_PADDING_VERTICAL_DP: Float = 8f

        private const val KEY_STEP_DP = 16f
        private const val DISABLED_ALPHA = 107
        private const val EPSILON = 0.0005f
        private const val STATE_SUPER = "sancai:floatingPanel:super"
        private const val STATE_X = "sancai:floatingPanel:x"
        private const val STATE_Y = "sancai:floatingPanel:y"

        /**
         * Adds a floating panel holding [content] to the content view of [activity] and returns
         * it. The panel is created with the context of [content], so it wears the same Sancai
         * theme, starts in the top-start corner and carries the id `sancai_floating_panel`, which
         * lets the activity save its position. Take it away with [remove].
         */
        @JvmStatic
        public fun attach(activity: Activity, content: View): SancaiFloatingPanel {
            val panel = SancaiFloatingPanel(content.context)
            panel.id = R.id.sancai_floating_panel
            panel.addView(content)
            activity.findViewById<ViewGroup>(android.R.id.content).addView(
                panel,
                LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            return panel
        }

        /**
         * Layout params for hosting a panel in a `WindowManager` overlay: wrap-content, anchored
         * top-start, translucent, not focusable (the app below keeps the keyboard) but touchable
         * so the panel can be dragged. [type] is
         * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] (the default, for accessibility
         * services) or [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] (API 26, needs the
         * overlay permission). Pass the result to [bindToWindow] and to `addView`.
         */
        @JvmStatic
        @JvmOverloads
        public fun overlayLayoutParams(
            type: Int = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        ): WindowManager.LayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }
}
