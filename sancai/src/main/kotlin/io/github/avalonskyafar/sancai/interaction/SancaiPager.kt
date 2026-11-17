package io.github.avalonskyafar.sancai.interaction

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.internal.HorizontalDragGate
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.abs
import kotlin.math.floor

/**
 * A horizontal pager for a handful of pages, such as the steps of a first-launch flow.
 *
 * The finger moves two pages edge to edge: the current one leaves while its neighbour follows
 * it in. Letting go after [COMMIT_FRACTION] of the width, or with a speed of
 * [COMMIT_VELOCITY_WIDTHS] widths per second towards the neighbour, lands on the neighbour;
 * anything less glides back. The first and the last page do not over-scroll.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.interaction.SancaiPager
 *     android:id="@+id/pager"
 *     android:layout_width="match_parent"
 *     android:layout_height="0dp"
 *     android:layout_weight="1"
 *     app:sancaiPagerOffscreenPageLimit="1" />
 * ```
 *
 * ```kotlin
 * pager.setPages(4) { index, parent -> createStep(index, parent) }
 * indicator.attachTo(pager)
 * ```
 *
 * Non-obvious behaviour:
 *
 * - Pages are created on demand and at most once. Only the current page and the one moving in
 *   are visible; visited neighbours inside [offscreenPageLimit] stay attached but invisible,
 *   pages further away are detached and kept for their return.
 * - While a move runs, further requests and gestures are ignored.
 * - The pager takes no keyboard focus of its own. Left / Right pressed inside a page turn the
 *   page when focus has nowhere else to go in that direction, Page Up / Page Down always do,
 *   and an attached `SancaiPageIndicator` is the focusable handle of the pager.
 * - A programmatic move to a distant page brings that page in directly, one width of travel.
 * - [OnPageChangeListener.onPageSelected] fires for user input only (drag, keyboard,
 *   accessibility, a tap on an attached indicator); [OnPageChangeListener.onPageScrolled]
 *   reports every movement.
 * - Vertical movement is left to a scrolling parent, and a gesture that starts on a
 *   [SancaiHorizontalDragOwner] (a switch or a track inside a page) is left to that view.
 * - A disabled pager keeps its page: no drag, key or accessibility action moves it. The pages
 *   themselves are not paled, since they are content rather than part of a control.
 * - In a right-to-left layout the next page comes in from the left.
 */
public class SancaiPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Creates the pages of a [SancaiPager]. */
    public fun interface PageProvider {
        /**
         * Returns the view of page [index]. Called once per page, the first time it is needed.
         * Do not add the view to [parent]; the pager does that.
         */
        public fun createPage(index: Int, parent: ViewGroup): View
    }

    /** Receives the movement of a [SancaiPager]. */
    public interface OnPageChangeListener {
        /** The user moved to page [index]. Not called for [setCurrentPage]. */
        public fun onPageSelected(index: Int) {}

        /**
         * The pager stands [offset] (0 until 1) of the way from page [index] to the page after
         * it. Called for every movement, whoever caused it. During a programmatic move to a
         * distant page the values run through the pages in between.
         */
        public fun onPageScrolled(index: Int, offset: Float) {}
    }

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val gate = HorizontalDragGate(android.view.ViewConfiguration.get(context).scaledTouchSlop)

    private var provider: PageProvider? = null
    private val pages = SparseArray<View>()
    private val listeners = ArrayList<OnPageChangeListener>()
    private val observers = ArrayList<() -> Unit>()
    private val followers = ArrayList<SancaiPager>()
    private var pendingPage = NO_PAGE

    private var page = 0
    private var savedPages: Bundle? = null

    // The move in progress: from page moveFrom to page moveTo, moveFraction of the way.
    private var moveFraction = 0f
    private var moveFrom = 0
    private var moveTo = NO_PAGE
    private var settling = false
    private var led = false
    private var animator: ValueAnimator? = null

    // The gesture in progress.
    private var tracking = false
    private var dragging = false
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var velocity: VelocityTracker? = null

    /** Number of pages, as given to [setPages]. */
    public var pageCount: Int = 0
        private set

    /**
     * The page the pager shows or is landing on. Setting it jumps without animation; see
     * [setCurrentPage].
     */
    public var currentPage: Int
        get() = page
        set(value) {
            setCurrentPage(value, animate = false)
        }

    /**
     * How many visited pages on each side of the current one stay attached (invisible) so that
     * they need no layout pass when they move in. Default 1; 0 keeps only the current page.
     */
    public var offscreenPageLimit: Int = 1
        set(value) {
            field = value.coerceAtLeast(0)
            if (!isMoving) trimPages()
        }

    /** Whether a drag, a settle or an animated [setCurrentPage] is in progress. */
    public val isMoving: Boolean get() = moveTo != NO_PAGE

    /**
     * The continuous position: `2.25` is a quarter of the way from page 2 to page 3. Equal to
     * [currentPage] while the pager rests.
     */
    public val scrollPosition: Float
        get() = if (isMoving) moveFrom + (moveTo - moveFrom) * moveFraction else page.toFloat()

    private val isRtl: Boolean get() = layoutDirection == LAYOUT_DIRECTION_RTL

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiPager, defStyleAttr, 0)
        offscreenPageLimit = a.getInt(R.styleable.SancaiPager_sancaiPagerOffscreenPageLimit, 1)
        a.recycle()
        clipChildren = true
        if (importantForAccessibility == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    /** Pages slide inside the pager and never over its neighbours, whatever the parent clips. */
    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipRect(0, 0, width, height)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    /**
     * Sets the pages: [count] of them, created by [provider] when they are first needed.
     * Replaces earlier pages, keeps the current index if it still exists (or takes the one
     * restored from saved state) and calls no listener except
     * [OnPageChangeListener.onPageScrolled]. Saved view state waiting for pages that were not
     * rebuilt yet is kept only by the first call with pages after a restore that found none;
     * replacing pages discards it, so new pages never receive the state of the ones they replace.
     */
    public fun setPages(count: Int, provider: PageProvider) {
        finishMoveNow()
        for (i in 0 until pages.size()) removeView(pages.valueAt(i))
        pages.clear()
        // pendingPage is set only by a restore that found no pages to give the state to.
        if (pendingPage == NO_PAGE) savedPages = null
        this.provider = provider
        pageCount = count.coerceAtLeast(0)
        val wanted = if (pendingPage != NO_PAGE) pendingPage else page
        // No pages yet: what the restore brought goes on waiting for the first real ones.
        if (pageCount > 0) pendingPage = NO_PAGE
        page = wanted.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (pageCount > 0) showOnly(page)
        updateStateDescription()
        dispatchScrolled()
        for (i in observers.indices) observers[i]()
    }

    /**
     * Moves to page [index], gliding when [animate] is true and the pager is laid out, else at
     * once. Ignored while a move runs and for an index that does not exist.
     * [OnPageChangeListener.onPageSelected] is not called.
     */
    public fun setCurrentPage(index: Int, animate: Boolean) {
        moveToPage(index, animate, fromUser = false)
    }

    /** Adds a listener for page changes and movement. */
    public fun addOnPageChangeListener(listener: OnPageChangeListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    /** Removes a listener added with [addOnPageChangeListener]. */
    public fun removeOnPageChangeListener(listener: OnPageChangeListener) {
        listeners.remove(listener)
    }

    /**
     * Makes [other] follow this pager continuously: every drag, settle and programmatic move
     * of this pager moves [other] by the same share of its own width, as a heading area that
     * moves in step with a content area. [other] jumps to this pager's page at once. Linking
     * both ways is allowed. [other] calls [OnPageChangeListener.onPageScrolled] but not
     * `onPageSelected`.
     */
    public fun linkWith(other: SancaiPager) {
        if (other === this || followers.contains(other)) return
        followers.add(other)
        if (!isMoving && !other.isMoving) other.moveToPage(page, animate = false, fromUser = false)
    }

    /**
     * Ends the link made with [linkWith]. A move this pager is leading [other] through ends for
     * [other] at once: it lands on the page the move is committed to, else back where it began.
     */
    public fun unlink(other: SancaiPager) {
        if (followers.remove(other) && isMoving && !led && other.led) other.finishMoveNow()
    }

    // ---- Moves -------------------------------------------------------------------------------

    /** The one entry for page changes; `internal` so that the indicator can act for the user. */
    internal fun moveToPage(index: Int, animate: Boolean, fromUser: Boolean): Boolean {
        if (isMoving || index == page || index < 0 || index >= pageCount) return false
        beginMove(page, index)
        commitMove(fromUser)
        if (animate && canGlide()) settleTo(1f) else finishMoveNow()
        return true
    }

    internal fun addPagesObserver(observer: () -> Unit) {
        observers.add(observer)
    }

    internal fun removePagesObserver(observer: () -> Unit) {
        observers.remove(observer)
    }

    private fun canGlide(): Boolean = isLaidOut && isAttachedToWindow && width > 0

    private fun beginMove(from: Int, to: Int) {
        moveFrom = from
        moveTo = to
        moveFraction = 0f
        pageView(to).visibility = VISIBLE
        placePages()
        eachFollower { it.followBegin(from, to) }
    }

    private fun setMoveFraction(fraction: Float) {
        moveFraction = fraction.coerceIn(0f, 1f)
        placePages()
        dispatchScrolled()
        eachFollower { it.followFraction(moveFraction) }
    }

    /** The move will land on its target: the target becomes the current page. */
    private fun commitMove(fromUser: Boolean) {
        page = moveTo
        updateStateDescription()
        eachFollower { it.followCommit() }
        if (fromUser) {
            for (i in listeners.indices.reversed()) listeners[i].onPageSelected(page)
        }
        announceForAccessibility(pageDescription())
    }

    private fun endMove() {
        if (!isMoving) return
        moveTo = NO_PAGE
        moveFraction = 0f
        settling = false
        showOnly(page)
        eachFollower { it.followEnd() }
        led = false
    }

    private fun settleTo(target: Float) {
        settling = true
        val remaining = abs(target - moveFraction)
        if (remaining == 0f || !canGlide()) {
            setMoveFraction(target)
            endMove()
            return
        }
        animator = ValueAnimator.ofFloat(moveFraction, target).apply {
            duration = SancaiMotion.settleDuration(remaining, theme.reduceMotion)
            interpolator = SancaiMotion.glide()
            addUpdateListener { setMoveFraction(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animator = null
                    setMoveFraction(target)
                    endMove()
                }
            })
            start()
        }
    }

    /** Lands a running move at once: on its target if committed, else back where it began. */
    private fun finishMoveNow() {
        if (!isMoving) return
        val running = animator
        if (running != null) {
            running.cancel() // ends the move through onAnimationEnd
            return
        }
        releaseGesture()
        setMoveFraction(if (page == moveTo) 1f else 0f)
        endMove()
    }

    private inline fun eachFollower(action: (SancaiPager) -> Unit) {
        if (led) return
        // A follower's listener may unlink followers while they are being told.
        for (i in followers.indices.reversed()) followers.getOrNull(i)?.let(action)
    }

    private fun followBegin(from: Int, to: Int) {
        if (pageCount == 0) return
        finishMoveNow()
        val first = from.coerceIn(0, pageCount - 1)
        val second = to.coerceIn(0, pageCount - 1)
        if (first == second) return
        if (page != first) {
            page = first
            showOnly(page)
        }
        led = true
        beginMove(first, second)
    }

    private fun followFraction(fraction: Float) {
        if (led) setMoveFraction(fraction)
    }

    private fun followCommit() {
        if (!led) return
        page = moveTo
        updateStateDescription()
    }

    private fun followEnd() {
        if (led) endMove()
    }

    // ---- Pages -------------------------------------------------------------------------------

    private fun pageView(index: Int): View {
        var view = pages.get(index)
        if (view == null) {
            val source = checkNotNull(provider) { "setPages() has not been called" }
            view = source.createPage(index, this)
            pages.put(index, view)
            savedPages?.let { saved ->
                BundleCompat.getSparseParcelableArray(saved, pageKey(index), Parcelable::class.java)
                    ?.let { view.restoreHierarchyState(it) }
                saved.remove(pageKey(index))
            }
        }
        if (view.parent == null) {
            view.visibility = INVISIBLE
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        return view
    }

    /** Rests on page [index]: it alone is visible, visited neighbours wait, the rest leave. */
    private fun showOnly(index: Int) {
        val current = pageView(index)
        // Keyboard focus travels with the page instead of falling out of the pager.
        if (hasFocus() && !current.hasFocus()) current.requestFocus()
        for (i in pages.size() - 1 downTo 0) {
            val view = pages.valueAt(i)
            view.translationX = 0f
            if (view === current) continue
            if (abs(pages.keyAt(i) - index) <= offscreenPageLimit) {
                view.visibility = INVISIBLE
            } else if (view.parent === this) {
                removeView(view)
            }
        }
        current.visibility = VISIBLE
    }

    private fun trimPages() {
        if (pageCount > 0 && provider != null) showOnly(page)
    }

    private fun placePages() {
        if (!isMoving) return
        val travel = width.toFloat()
        val side = if ((moveTo > moveFrom) != isRtl) 1f else -1f
        // Adding zero turns a negative zero into a plain one.
        pages.get(moveFrom)?.translationX = -side * moveFraction * travel + 0f
        pages.get(moveTo)?.translationX = side * (1f - moveFraction) * travel + 0f
    }

    private fun dispatchScrolled() {
        if (pageCount == 0) return
        val position = scrollPosition
        val index = floor(position).toInt().coerceIn(0, pageCount - 1)
        val offset = (position - index).coerceIn(0f, 1f)
        for (i in listeners.indices.reversed()) listeners[i].onPageScrolled(index, offset)
    }

    private fun pageDescription(): String =
        resources.getString(R.string.sancai_pager_page_of, page + 1, pageCount)

    private fun updateStateDescription() {
        ViewCompat.setStateDescription(this, if (pageCount > 0) pageDescription() else null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        placePages()
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        placePages()
    }

    // ---- Touch -------------------------------------------------------------------------------

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when {
            action == MotionEvent.ACTION_DOWN -> startTracking(event)
            action == MotionEvent.ACTION_MOVE -> return tracking && trackMove(event)
            gate.isRelease(event) || action == MotionEvent.ACTION_CANCEL ->
                if (!dragging) releaseGesture()
        }
        return false
    }

    @Suppress("ClickableViewAccessibility") // paging is offered as accessibility scroll actions
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when {
            action == MotionEvent.ACTION_DOWN -> return tracking
            action == MotionEvent.ACTION_MOVE -> {
                if (tracking) trackMove(event)
                return tracking
            }
            // The finger the pages followed lifts, alone or with another one still down: the
            // gesture ends here instead of jumping to another finger.
            gate.isRelease(event) || action == MotionEvent.ACTION_CANCEL -> {
                if (dragging) finishDrag(event) else releaseGesture()
                return true
            }
        }
        return tracking
    }

    private fun startTracking(event: MotionEvent) {
        releaseGesture()
        tracking = isEnabled && pageCount > 1 && !isMoving && !hitsDragOwner(this, event.x, event.y)
        if (!tracking) return
        gate.down(event)
        pointerId = event.getPointerId(0)
        velocity = VelocityTracker.obtain().also { it.addMovement(event) }
    }

    /** Feeds a move; returns true while the pager owns the gesture. */
    private fun trackMove(event: MotionEvent): Boolean {
        velocity?.addMovement(event)
        val dx = gate.x(event) - gate.downX
        if (!dragging) {
            when (gate.move(this, event)) {
                HorizontalDragGate.State.UNDECIDED -> return false
                HorizontalDragGate.State.ABORTED -> {
                    releaseGesture()
                    return false
                }
                HorizontalDragGate.State.DRAGGING -> {
                    val target = if (dx * forwardSign > 0f) page + 1 else page - 1
                    if (isMoving || target < 0 || target >= pageCount) {
                        // Nothing on that side: the gesture belongs to whoever hosts the pager.
                        gate.finish(this)
                        releaseGesture()
                        return false
                    }
                    dragging = true
                    beginMove(page, target)
                }
            }
        }
        val towardsTarget = if (moveTo > moveFrom) forwardSign else -forwardSign
        setMoveFraction(dx * towardsTarget / width.coerceAtLeast(1))
        return true
    }

    private fun finishDrag(event: MotionEvent) {
        val tracker = velocity
        var speed = 0f
        if (tracker != null) {
            // A finger lifting beside another one says nothing new about where it was.
            if (event.actionMasked != MotionEvent.ACTION_POINTER_UP) tracker.addMovement(event)
            tracker.computeCurrentVelocity(1000)
            val towardsTarget = if (moveTo > moveFrom) forwardSign else -forwardSign
            // The speed of the followed finger, not of whichever finger is first in the event.
            speed = tracker.getXVelocity(pointerId) * towardsTarget / width.coerceAtLeast(1)
        }
        val complete = event.actionMasked != MotionEvent.ACTION_CANCEL &&
            (moveFraction >= COMMIT_FRACTION || speed >= COMMIT_VELOCITY_WIDTHS)
        gate.finish(this)
        releaseGesture()
        if (complete) commitMove(fromUser = true)
        settleTo(if (complete) 1f else 0f)
    }

    /**
     * Disabling the pager in the middle of a drag lets the page glide back; disabling it under
     * a finger that has not started to drag yet gives that gesture up.
     */
    override fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            if (dragging) {
                releaseGesture()
                settleTo(0f)
            } else if (tracking) {
                releaseGesture()
            }
        }
        super.setEnabled(enabled)
    }

    private fun releaseGesture() {
        // Fingers that stay down after this are answered with "not this gesture" by the gate.
        if (gate.isActive) gate.finish(this)
        tracking = false
        dragging = false
        velocity?.recycle()
        velocity = null
    }

    /** Sign of a horizontal finger movement that leads to the next page. */
    private val forwardSign: Float get() = if (isRtl) 1f else -1f

    private fun hitsDragOwner(group: ViewGroup, x: Float, y: Float): Boolean {
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child.visibility != VISIBLE) continue
            val childX = x + group.scrollX - child.left - child.translationX
            val childY = y + group.scrollY - child.top - child.translationY
            if (childX < 0f || childY < 0f || childX >= child.width || childY >= child.height) continue
            if (child is SancaiHorizontalDragOwner) return true
            if (child is ViewGroup && hitsDragOwner(child, childX, childY)) return true
        }
        return false
    }

    /**
     * Tells gesture hosts around the pager whether it still has a page to go to: `true` for a
     * positive [direction] while there is a page towards the right, for a negative one while
     * there is a page towards the left (the next page is on the right, in RTL on the left).
     */
    override fun canScrollHorizontally(direction: Int): Boolean {
        if (direction == 0 || pageCount == 0) return false
        val towardsNext = (direction > 0) != isRtl
        return if (towardsNext) page < pageCount - 1 else page > 0
    }

    // ---- Keyboard ----------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (super.dispatchKeyEvent(event)) return true
        if (event.action != KeyEvent.ACTION_DOWN || !isEnabled) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_PAGE_DOWN -> step(1)
            KeyEvent.KEYCODE_PAGE_UP -> step(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> !focusStaysInside(FOCUS_RIGHT) && step(if (isRtl) -1 else 1)
            KeyEvent.KEYCODE_DPAD_LEFT -> !focusStaysInside(FOCUS_LEFT) && step(if (isRtl) 1 else -1)
            else -> false
        }
    }

    private fun focusStaysInside(direction: Int): Boolean {
        var next: View? = findFocus()?.focusSearch(direction) ?: return false
        while (next != null) {
            if (next === this) return true
            next = next.parent as? View
        }
        return false
    }

    /** A user step by [delta] pages; a key is consumed when there is a page to go to. */
    private fun step(delta: Int): Boolean {
        val target = page + delta
        if (target < 0 || target >= pageCount) return false
        moveToPage(target, animate = true, fromUser = true)
        return true
    }

    // ---- Accessibility -----------------------------------------------------------------------

    override fun getAccessibilityClassName(): CharSequence = "androidx.viewpager.widget.ViewPager"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isScrollable = pageCount > 1
        if (!isEnabled) return
        if (page < pageCount - 1) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        if (page > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (isEnabled) {
            when (action) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> return step(1)
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> return step(-1)
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    // ---- State -------------------------------------------------------------------------------

    override fun onSaveInstanceState(): Parcelable {
        // Each page keeps its own container, so pages inflated from one layout do not clash.
        val saved = Bundle()
        savedPages?.let { saved.putAll(it) }
        for (i in 0 until pages.size()) {
            val container = SparseArray<Parcelable>()
            pages.valueAt(i).saveHierarchyState(container)
            saved.putSparseParcelableArray(pageKey(pages.keyAt(i)), container)
        }
        return Bundle().apply {
            putParcelable(STATE_SUPER, super.onSaveInstanceState())
            putInt(STATE_PAGE, page)
            putBundle(STATE_PAGES, saved)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        val saved = state.getBundle(STATE_PAGES)
        savedPages = saved
        val restored = state.getInt(STATE_PAGE, 0)
        if (pageCount == 0) {
            pendingPage = restored
            return
        }
        finishMoveNow()
        if (saved != null) {
            for (i in 0 until pages.size()) {
                val key = pageKey(pages.keyAt(i))
                BundleCompat.getSparseParcelableArray(saved, key, Parcelable::class.java)
                    ?.let { pages.valueAt(i).restoreHierarchyState(it) }
                saved.remove(key)
            }
        }
        if (restored in 0 until pageCount && restored != page) {
            page = restored
            showOnly(page)
            updateStateDescription()
            dispatchScrolled()
        }
    }

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        dispatchThawSelfOnly(container)
    }

    override fun onDetachedFromWindow() {
        finishMoveNow()
        releaseGesture()
        super.onDetachedFromWindow()
    }

    private fun pageKey(index: Int): String = "page_$index"

    public companion object {
        /**
         * Share of the width a drag must cover to land on the neighbour. Lower than
         * [SancaiMotion.COMMIT_FRACTION] because paging is the main gesture of such a screen.
         */
        public const val COMMIT_FRACTION: Float = 0.22f

        /** Speed towards the neighbour, in pager widths per second, that lands on it at once. */
        public const val COMMIT_VELOCITY_WIDTHS: Float = 0.45f

        private const val NO_PAGE = -1
        private const val STATE_SUPER = "sancai:super"
        private const val STATE_PAGE = "sancai:page"
        private const val STATE_PAGES = "sancai:pages"
    }
}
