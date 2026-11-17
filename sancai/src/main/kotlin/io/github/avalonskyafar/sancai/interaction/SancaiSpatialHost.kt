package io.github.avalonskyafar.sancai.interaction

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.BundleCompat
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialGraph.Side
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * Shows one page of a [SancaiSpatialGraph] and moves between pages with the finger.
 *
 * The page under the finger and the page it uncovers travel together, edge to edge, at finger
 * speed. Nothing fades, scales or lags behind. Letting go past 28 % of the way, or with a
 * flick, lands on the new page; anything less glides back. A touch during the landing takes
 * over at once.
 *
 * ```kotlin
 * host.setGraph(graph) { key -> createPageView(key) }
 * host.show("main")
 * row.setOnClickListener { host.navigate("settings") }
 * ```
 *
 * How the host shares the screen with what is inside the pages:
 *
 * - A drag that starts on a [SancaiHorizontalDragOwner] (a track, a switch, a slider) belongs
 *   to that control, unless it turns out to be vertical.
 * - Content that can still scroll up or down scrolls first; when it reaches its limit within
 *   the same gesture, the page starts to follow.
 * - Sideways, a gesture has one owner from start to finish. Content that can move in the
 *   direction of the drag when the drag begins (a pager with a page left that way, a row that
 *   slides aside, a horizontal list) keeps the whole gesture, also beyond the point where it
 *   stops; the page only follows a sideways drag that nothing under the finger can use.
 * - Horizontal drags that begin within 24 dp of a screen edge are left to the system.
 *
 * A page is given the window insets (system bars, keyboard) when it is attached, however late
 * that is, so a page that pads itself for the bars is right from its first frame.
 *
 * Page views come from the [PageFactory] and are kept while detached, so returning to a page
 * restores it exactly; see [setKeepAlive] and [invalidatePage].
 */
public class SancaiSpatialHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Creates the view of a page. Called again after [invalidatePage]. */
    public fun interface PageFactory {
        public fun createPage(key: String): View
    }

    /** Observes page changes. */
    public interface Listener {
        /** The host has come to rest on [key]. */
        public fun onPageSettled(key: String) {}

        /** Pages are moving: [fraction] of the way from [from] to [to] (0..1). */
        public fun onPageScrolled(from: String, to: String, fraction: Float) {}
    }

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val edgePx = EDGE_DP * resources.displayMetrics.density
    private val commitVelocityPx = SancaiMotion.COMMIT_VELOCITY_DP * resources.displayMetrics.density

    private var graph: SancaiSpatialGraph? = null
    private var factory: PageFactory? = null
    private val cache = HashMap<String, View>()
    private val savedPages = HashMap<String, SparseArray<Parcelable>>()
    private var keepAlive: Set<String>? = null
    private val listeners = ArrayList<Listener>()

    private var current: String? = null
    private var currentView: View? = null

    // One move at a time: a drag, a settle, or nothing.
    private var dragging = false
    private var settling = false
    private var horizontal = false
    private var direction = 1
    private var sourceKey: String? = null
    private var targetKey: String? = null
    private var outgoing: View? = null
    private var incoming: View? = null
    private var settleAnimator: ValueAnimator? = null
    private var settleCompletes = false

    // Per-gesture bookkeeping.
    private var downX = 0f
    private var downY = 0f
    private var activePointerId = -1
    private var handoverX = 0f
    private var handoverY = 0f
    private var axisLocked = false
    private var rejected = false
    private var ownerGesture = false
    private var waitingForContent = false
    private var velocity: VelocityTracker? = null
    private val hitRect = Rect()

    private val warmups = ArrayList<Runnable>()
    private var warmupGeneration = 0

    /** Builds neighbouring pages ahead of time, while the host is idle. Off by default. */
    public var prewarmNeighbours: Boolean = false

    /** The page the host rests on, or is leaving. */
    public val currentKey: String? get() = current

    /** Whether pages are being dragged or are landing. */
    public val isMoving: Boolean get() = dragging || settling

    init {
        clipChildren = true
        setBackgroundColor(theme.colors.canvas)
        isSaveEnabled = true
    }

    /**
     * Sets the places and how to build them. Call [show] afterwards: the page on screen and
     * every kept view came from the previous factory and are removed, so the host is empty
     * ([currentKey] is `null`) until then. State restored for pages that have not been built
     * yet is kept and handed to the views of the new factory.
     */
    public fun setGraph(graph: SancaiSpatialGraph, factory: PageFactory) {
        stabilise()
        cancelWarmups()
        currentView?.let { removeView(it) }
        currentView = null
        current = null
        this.graph = graph
        this.factory = factory
        cache.clear()
    }

    public fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    public fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * Limits which pages stay alive while detached; `null` (the default) keeps every visited
     * page. Pages outside the set are rebuilt on each visit.
     */
    public fun setKeepAlive(keys: Set<String>?) {
        keepAlive = keys?.toSet()
        if (keys != null) cache.keys.retainAll(keys)
    }

    /** Discards the kept view of [key], so the next visit builds a fresh one. */
    public fun invalidatePage(key: String) {
        cache.remove(key)
        savedPages.remove(key)
    }

    /** Discards every kept view. The page on screen stays as it is. */
    public fun invalidatePages() {
        cache.clear()
        savedPages.clear()
    }

    /** Shows [key] at once, without movement. */
    public fun show(key: String) {
        requireGraph(key)
        stabilise()
        if (current == key && currentView != null) return
        replaceWith(key, obtainPage(key))
    }

    /**
     * Rebuilds the page on screen in place, for example after its data changed. Whatever the
     * page had saved (scroll position, typed text) is handed to the new view.
     */
    public fun refreshCurrent() {
        val key = current ?: return
        stabilise()
        currentView?.let { view ->
            val state = SparseArray<Parcelable>()
            view.saveHierarchyState(state)
            savedPages[key] = state
        }
        cache.remove(key)
        replaceWith(key, obtainPage(key))
    }

    /**
     * Moves to [key], which slides in from the side where it lies. For a page that is not
     * adjacent to the current one, it slides in from the end side.
     */
    @JvmOverloads
    public fun navigate(key: String, animate: Boolean = true) {
        val from = current
        val side = if (from == null) null else requireGraph(key).sideOf(from, key)
        navigate(key, side ?: Side.END, animate)
    }

    /** Moves to [key], sliding it in from [from]. */
    public fun navigate(key: String, from: Side, animate: Boolean) {
        requireGraph(key)
        stabilise()
        if (current == key && currentView != null) return
        val old = currentView
        if (!animate || old == null || width == 0 || height == 0) {
            replaceWith(key, obtainPage(key))
            return
        }
        val page = obtainPage(key)
        horizontal = from.isHorizontal
        direction = contentDirection(from)
        sourceKey = current
        targetKey = key
        outgoing = old
        incoming = page
        if (horizontal) page.translationX = -direction * travel() else page.translationY = -direction * travel()
        addView(page, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        startSettle(complete = true)
    }

    /** Moves to the parent of the current page. Returns false on the root. */
    public fun back(): Boolean {
        stabilise()
        val from = current ?: return false
        val parent = graph?.parentOf(from) ?: return false
        navigate(parent, graph?.sideOf(from, parent) ?: Side.START, animate = true)
        return true
    }

    /** Whether [back] would move. */
    public fun canGoBack(): Boolean {
        val from = current ?: return false
        return graph?.parentOf(from) != null
    }

    private fun requireGraph(key: String): SancaiSpatialGraph {
        val g = checkNotNull(graph) { "Call setGraph() before showing pages" }
        require(g.contains(key)) { "\"$key\" is not a page of the graph" }
        return g
    }

    /** +1 when the content moves right or down to reveal a page on [side], -1 otherwise. */
    private fun contentDirection(side: Side): Int = when (side) {
        Side.START -> if (isRtl()) -1 else 1
        Side.END -> if (isRtl()) 1 else -1
        Side.ABOVE -> 1
        Side.BELOW -> -1
    }

    private fun isRtl(): Boolean = layoutDirection == LAYOUT_DIRECTION_RTL

    private fun travel(): Float = max(1, if (horizontal) width else height).toFloat()

    private fun offsetOf(view: View): Float = if (horizontal) view.translationX else view.translationY

    private fun setOffset(view: View?, value: Float) {
        if (view == null) return
        if (horizontal) view.translationX = value else view.translationY = value
    }

    // ---------------------------------------------------------------------------------------
    // Pages
    // ---------------------------------------------------------------------------------------

    private fun obtainPage(key: String): View {
        val kept = cache.remove(key)
        if (kept != null && kept.parent == null) {
            kept.translationX = 0f
            kept.translationY = 0f
            return kept
        }
        return buildPage(key)
    }

    /** A new view for [key], free of any parent and carrying the state saved for the page. */
    private fun buildPage(key: String): View {
        val created = checkNotNull(factory) { "Call setGraph() before showing pages" }.createPage(key)
        (created.parent as? ViewGroup)?.removeView(created)
        savedPages.remove(key)?.let { created.restoreHierarchyState(it) }
        return created
    }

    /** Takes a page off screen and keeps it for its next visit when it may be kept. */
    private fun park(key: String?, view: View?) {
        if (view == null) return
        if (view.parent === this) removeView(view)
        view.translationX = 0f
        view.translationY = 0f
        val keep = keepAlive
        if (key != null && (keep == null || key in keep)) cache[key] = view
    }

    private fun replaceWith(key: String, view: View) {
        cancelSettleAnimator()
        val oldKey = current
        val oldView = currentView
        for (i in childCount - 1 downTo 0) {
            if (getChildAt(i) !== view) removeViewAt(i)
        }
        if (oldView != null && oldView !== view && oldKey != key) park(oldKey, oldView)
        view.translationX = 0f
        view.translationY = 0f
        if (view.parent !== this) addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        current = key
        currentView = view
        clearMove()
        notifySettled()
        scheduleWarmups(immediate = true)
    }

    // ---------------------------------------------------------------------------------------
    // Landing
    // ---------------------------------------------------------------------------------------

    private fun startSettle(complete: Boolean) {
        cancelSettleAnimator()
        val out = outgoing
        val inc = incoming
        if (out == null || inc == null) {
            clearMove()
            return
        }
        dragging = false
        settling = true
        settleCompletes = complete
        val distance = travel()
        val outStart = offsetOf(out)
        val inStart = offsetOf(inc)
        val outTarget = if (complete) direction * distance else 0f
        val inTarget = if (complete) 0f else -direction * distance
        val remaining = max(abs(outTarget - outStart), abs(inTarget - inStart)) / distance
        val animator = ValueAnimator.ofFloat(0f, 1f)
        settleAnimator = animator
        animator.duration = SancaiMotion.settleDuration(remaining, theme.reduceMotion)
        animator.interpolator = SancaiMotion.glide()
        animator.addUpdateListener {
            val progress = it.animatedValue as Float
            setOffset(out, outStart + (outTarget - outStart) * progress)
            setOffset(inc, inStart + (inTarget - inStart) * progress)
            notifyScrolled()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled && settleAnimator === animation) finishMove(complete)
            }
        })
        animator.start()
    }

    private fun cancelSettleAnimator() {
        val active = settleAnimator
        settleAnimator = null
        active?.cancel()
    }

    /** Ends the move: [completed] lands on the incoming page, otherwise back on the old one. */
    private fun finishMove(completed: Boolean) {
        val keep = if (completed) incoming else outgoing
        val drop = if (completed) outgoing else incoming
        val dropKey = if (completed) sourceKey else targetKey
        if (completed && incoming != null) {
            current = targetKey
            currentView = incoming
        }
        keep?.translationX = 0f
        keep?.translationY = 0f
        park(dropKey, drop)
        clearMove()
        notifySettled()
        scheduleWarmups(immediate = false)
    }

    /**
     * A touch or a navigation request arrived mid-move: whichever page is at least half way in
     * becomes the page on screen, at once.
     */
    private fun adoptNearest() {
        if (!dragging && !settling) return
        cancelSettleAnimator()
        val inc = incoming
        finishMove(inc != null && abs(offsetOf(inc)) <= travel() * 0.5f)
    }

    /** Brings the host to rest before something else happens to it. */
    private fun stabilise() {
        if (settling) {
            val complete = settleCompletes
            cancelSettleAnimator()
            finishMove(complete)
        } else if (dragging) {
            adoptNearest()
        }
    }

    private fun clearMove() {
        dragging = false
        settling = false
        settleCompletes = false
        outgoing = null
        incoming = null
        sourceKey = null
        targetKey = null
    }

    private fun notifySettled() {
        val key = current ?: return
        for (listener in listeners.toTypedArray()) listener.onPageSettled(key)
    }

    private fun notifyScrolled() {
        val from = sourceKey ?: return
        val to = targetKey ?: return
        val out = outgoing ?: return
        val fraction = (abs(offsetOf(out)) / travel()).coerceIn(0f, 1f)
        for (listener in listeners.toTypedArray()) listener.onPageScrolled(from, to, fraction)
    }

    // ---------------------------------------------------------------------------------------
    // Gestures
    // ---------------------------------------------------------------------------------------

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        // Android hands the window insets down when they change, not when a view arrives. A
        // page attached after the first layout pass would never hear of the system bars.
        child.requestApplyInsets()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // A touch is a stronger signal than a warm-up timer: page building waits.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) cancelWarmups()
        return super.dispatchTouchEvent(ev)
    }

    private fun beginGesture(event: MotionEvent) {
        if (dragging || settling) adoptNearest()
        downX = event.x
        downY = event.y
        activePointerId = event.getPointerId(0)
        handoverX = 0f
        handoverY = 0f
        axisLocked = false
        rejected = false
        waitingForContent = false
        resetVelocity(event)
    }

    // The gesture follows one finger. Its position is reported less the distance to the fingers
    // it took over from, so a hand-over never moves the pages.
    private fun fingerIndex(event: MotionEvent): Int = max(0, event.findPointerIndex(activePointerId))

    private fun fingerX(event: MotionEvent): Float = event.getX(fingerIndex(event)) - handoverX

    private fun fingerY(event: MotionEvent): Float = event.getY(fingerIndex(event)) - handoverY

    /** When the followed finger lifts while another is down, the gesture goes on with that one. */
    private fun handOver(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_POINTER_UP) return
        val lifted = event.actionIndex
        if (event.getPointerId(lifted) != activePointerId) return
        val next = if (lifted == 0) 1 else 0
        handoverX += event.getX(next) - event.getX(lifted)
        handoverY += event.getY(next) - event.getY(lifted)
        activePointerId = event.getPointerId(next)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            beginGesture(event)
            val page = currentView
            ownerGesture = page != null && ownerUnder(page, downX - page.left, downY - page.top)
            return false
        }
        handOver(event)
        if (ownerGesture) {
            // The control keeps a horizontal gesture. Once the finger has clearly gone
            // vertical the control has let go, and normal arbitration resumes.
            if (action != MotionEvent.ACTION_MOVE) return false
            val dx = fingerX(event) - downX
            val dy = fingerY(event) - downY
            if (max(abs(dx), abs(dy)) < slop || abs(dx) > abs(dy)) return false
            ownerGesture = false
        }
        velocity?.addMovement(event)
        if (action != MotionEvent.ACTION_MOVE) return false
        if (waitingForContent) {
            if (contentCanScroll(false, -waitSign)) return false
            // The content has reached its limit: the page takes over from here.
            waitingForContent = false
            axisLocked = false
            // A fresh start where the followed finger really is: the point is hit tested.
            handoverX = 0f
            handoverY = 0f
            downX = fingerX(event)
            downY = fingerY(event)
            resetVelocity(event)
            return false
        }
        return tryBeginDrag(fingerX(event) - downX, fingerY(event) - downY)
    }

    // Lint: the host only drags; it has no click of its own.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            beginGesture(event)
            ownerGesture = false
            return true
        }
        handOver(event)
        velocity?.addMovement(event)
        val dx = fingerX(event) - downX
        val dy = fingerY(event) - downY
        if (!dragging && action == MotionEvent.ACTION_MOVE) {
            waitingForContent = false
            tryBeginDrag(dx, dy)
        }
        if (!dragging) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) recycleVelocity()
            return true
        }
        val delta = if (horizontal) dx else dy
        if (action == MotionEvent.ACTION_MOVE) {
            updateDrag(delta)
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            var speed = 0f
            velocity?.let {
                it.computeCurrentVelocity(1000)
                // Of the finger the gesture follows, which is not always the first one.
                val id = event.getPointerId(fingerIndex(event))
                speed = if (horizontal) it.getXVelocity(id) else it.getYVelocity(id)
            }
            val far = abs(delta) > travel() * SancaiMotion.COMMIT_FRACTION && sign(delta) == direction.toFloat()
            val fast = abs(speed) > commitVelocityPx && sign(speed) == direction.toFloat()
            parent?.requestDisallowInterceptTouchEvent(false)
            recycleVelocity()
            startSettle(complete = action == MotionEvent.ACTION_UP && (far || fast))
        }
        return true
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Only a drag owner may keep the host out of a gesture. Scrolling content may not,
        // or the host could never take over when that content reaches its limit. Its request
        // still goes up, so that the ancestors of the host stay out of the content's gesture.
        if (ownerGesture) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        } else {
            parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    private var waitSign = 1

    private fun tryBeginDrag(dx: Float, dy: Float): Boolean {
        if (rejected) return false
        if (!axisLocked) {
            if (max(abs(dx), abs(dy)) < slop) return false
            horizontal = abs(dx) > abs(dy)
            axisLocked = true
            val delta = if (horizontal) dx else dy
            val fingerSign = if (delta >= 0f) 1 else -1
            val from = current
            val target = if (from == null) null else graph?.neighbour(from, sideRevealedBy(horizontal, fingerSign))
            if (target == null || (horizontal && (downX < edgePx || downX > width - edgePx))) {
                rejected = true
                return false
            }
            if (contentCanScroll(horizontal, -fingerSign)) {
                if (horizontal) {
                    // Sideways there is no taking over: a row that has slid as far as it goes
                    // would otherwise be cancelled, and shut, by the very drag that opened it.
                    rejected = true
                    return false
                }
                waitingForContent = true
                waitSign = fingerSign
                return false
            }
            beginDrag(target, delta)
        }
        return dragging
    }

    /** The side whose page comes into view when the finger moves in [fingerSign] direction. */
    private fun sideRevealedBy(horizontalMove: Boolean, fingerSign: Int): Side = when {
        !horizontalMove -> if (fingerSign > 0) Side.ABOVE else Side.BELOW
        (fingerSign > 0) != isRtl() -> Side.START
        else -> Side.END
    }

    private fun beginDrag(target: String, delta: Float) {
        val old = currentView ?: return
        val page = obtainPage(target)
        dragging = true
        sourceKey = current
        targetKey = target
        outgoing = old
        incoming = page
        direction = if (delta >= 0f) 1 else -1
        setOffset(page, -direction * travel())
        addView(page, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        parent?.requestDisallowInterceptTouchEvent(true)
        updateDrag(delta)
    }

    private fun updateDrag(delta: Float) {
        val distance = travel()
        var bounded = delta.coerceIn(-distance, distance)
        if (sign(bounded) != direction.toFloat()) bounded = 0f
        setOffset(outgoing, bounded)
        setOffset(incoming, bounded - direction * distance)
        notifyScrolled()
    }

    private fun resetVelocity(event: MotionEvent) {
        recycleVelocity()
        velocity = VelocityTracker.obtain().also { it.addMovement(event) }
    }

    private fun recycleVelocity() {
        velocity?.recycle()
        velocity = null
    }

    /** Whether anything under the touch-down point can still scroll in [scrollDirection]. */
    private fun contentCanScroll(horizontalAxis: Boolean, scrollDirection: Int): Boolean {
        val page = currentView ?: return false
        return canScrollUnder(page, downX - page.left, downY - page.top, horizontalAxis, scrollDirection)
    }

    private fun canScrollUnder(view: View, x: Float, y: Float, horizontalAxis: Boolean, dir: Int): Boolean {
        if (view is ViewGroup) {
            val px = x + view.scrollX
            val py = y + view.scrollY
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                if (child.visibility != VISIBLE) continue
                if (px >= child.left && px < child.right && py >= child.top && py < child.bottom &&
                    canScrollUnder(child, px - child.left, py - child.top, horizontalAxis, dir)
                ) {
                    return true
                }
            }
        }
        return if (horizontalAxis) view.canScrollHorizontally(dir) else view.canScrollVertically(dir)
    }

    private fun ownerUnder(view: View, x: Float, y: Float): Boolean {
        if (view is SancaiHorizontalDragOwner) return view.isEnabled
        if (view !is ViewGroup) return false
        val px = x + view.scrollX
        val py = y + view.scrollY
        for (i in view.childCount - 1 downTo 0) {
            val child = view.getChildAt(i)
            if (child.visibility != VISIBLE) continue
            val cx = px - child.left - child.translationX
            val cy = py - child.top - child.translationY
            if (cx >= 0f && cx < child.width && cy >= 0f && cy < child.height && ownerUnder(child, cx, cy)) return true
        }
        return false
    }

    // ---------------------------------------------------------------------------------------
    // Warm-up of neighbouring pages
    // ---------------------------------------------------------------------------------------

    private fun scheduleWarmups(immediate: Boolean) {
        cancelWarmups()
        if (!prewarmNeighbours) return
        val g = graph ?: return
        val key = current ?: return
        val generation = warmupGeneration
        var index = 0
        for (side in Side.values()) {
            val target = g.neighbour(key, side) ?: continue
            if (cache.containsKey(target) || keepAlive?.contains(target) == false) continue
            val task = object : Runnable {
                override fun run() {
                    warmups.remove(this)
                    if (generation != warmupGeneration || current != key || isMoving) return
                    if (cache.containsKey(target)) return
                    if (factory == null) return
                    cache[target] = buildPage(target)
                }
            }
            warmups.add(task)
            // After a plain swap the host is idle, so build on the next loop. After a landing,
            // leave the first frames and the first touch of the new page alone.
            if (immediate) post(task) else postDelayed(task, WARMUP_SETTLE_DELAY_MS + index * WARMUP_STEP_MS)
            index++
        }
    }

    private fun cancelWarmups() {
        warmupGeneration++
        for (task in warmups) removeCallbacks(task)
        warmups.clear()
    }

    // ---------------------------------------------------------------------------------------
    // Back preview (driven by SancaiBack)
    // ---------------------------------------------------------------------------------------

    /** Starts a move towards the parent page that [updateBackPreview] then drives. */
    internal fun beginBackPreview(): Boolean {
        stabilise()
        val from = current ?: return false
        val g = graph ?: return false
        val parentKey = g.parentOf(from) ?: return false
        if (width == 0 || height == 0 || currentView == null) return false
        val side = g.sideOf(from, parentKey) ?: Side.START
        horizontal = side.isHorizontal
        beginDrag(parentKey, contentDirection(side).toFloat())
        updateBackPreview(0f)
        return dragging
    }

    /** Moves the previewed pages to [progress] (0..1) of the preview distance. */
    internal fun updateBackPreview(progress: Float) {
        if (!dragging) return
        updateDrag(direction * travel() * BACK_PREVIEW_FRACTION * progress.coerceIn(0f, 1f))
    }

    /** Ends the preview: [commit] lands on the parent, otherwise the page glides back. */
    internal fun endBackPreview(commit: Boolean) {
        if (dragging) startSettle(commit)
    }

    // ---------------------------------------------------------------------------------------
    // State and lifecycle
    // ---------------------------------------------------------------------------------------

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        // Pages are saved per key in onSaveInstanceState; ids may repeat across pages.
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        dispatchThawSelfOnly(container)
    }

    override fun onSaveInstanceState(): Parcelable {
        stabilise()
        val pages = Bundle()
        // State still waiting for a page that has not been built since the last restore.
        for ((key, pending) in savedPages) pages.putSparseParcelableArray(key, pending)
        for ((key, view) in cache) savePage(pages, key, view)
        savePage(pages, current, currentView)
        return Bundle().apply {
            putParcelable(STATE_SUPER, super.onSaveInstanceState())
            putString(STATE_CURRENT, current)
            putBundle(STATE_PAGES, pages)
        }
    }

    private fun savePage(into: Bundle, key: String?, view: View?) {
        if (key == null || view == null) return
        val state = SparseArray<Parcelable>()
        view.saveHierarchyState(state)
        into.putSparseParcelableArray(key, state)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        savedPages.clear()
        state.getBundle(STATE_PAGES)?.let { pages ->
            for (key in pages.keySet()) {
                BundleCompat.getSparseParcelableArray(pages, key, Parcelable::class.java)?.let { savedPages[key] = it }
            }
        }
        // Pages built before the restore (the usual setGraph + show in onCreate) take theirs now.
        val shownKey = current
        val shownView = currentView
        if (shownKey != null && shownView != null) {
            savedPages.remove(shownKey)?.let { shownView.restoreHierarchyState(it) }
        }
        for ((kept, view) in cache) savedPages.remove(kept)?.let { view.restoreHierarchyState(it) }
        val key = state.getString(STATE_CURRENT) ?: return
        val g = graph ?: return
        if (!g.contains(key)) return
        stabilise()
        val shown = currentView
        if (current == key && shown != null) {
            savedPages.remove(key)?.let { shown.restoreHierarchyState(it) }
        } else {
            replaceWith(key, obtainPage(key))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Offsets are measured in the old size; come to rest rather than guess.
        if (oldw != 0 && oldh != 0) stabilise()
    }

    override fun onDetachedFromWindow() {
        stabilise()
        cancelWarmups()
        recycleVelocity()
        super.onDetachedFromWindow()
    }

    public companion object {
        /** Width, in dp, of the screen-edge zones left to the system back gesture. */
        public const val EDGE_DP: Float = 24f

        /** Share of the travel that a full system back swipe previews before it commits. */
        public const val BACK_PREVIEW_FRACTION: Float = 0.4f

        private const val WARMUP_SETTLE_DELAY_MS = 250L
        private const val WARMUP_STEP_MS = 120L
        private const val STATE_SUPER = "sancai:super"
        private const val STATE_CURRENT = "sancai:current"
        private const val STATE_PAGES = "sancai:pages"
    }
}
