package io.github.avalonskyafar.sancai.interaction

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialGraph.Side
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Makes a second activity behave like a neighbouring page.
 *
 * The new activity slides in from one side while the activity below slides out the other way,
 * edge to edge and at the same speed; nothing fades or scales. Afterwards the user can drag the
 * page back out by swiping anywhere inside it, towards the side it came from. Letting go past
 * 28 % of the way, or with a flick, finishes the activity; anything less glides back.
 *
 * ```kotlin
 * // in the launching activity
 * SancaiSpatialTransition.start(this, Intent(this, DetailActivity::class.java), Side.END)
 *
 * // in the new activity
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     setContentView(SancaiSpatialTransition.wrap(this, pageView))
 * }
 *
 * override fun onBackPressed() {
 *     SancaiSpatialTransition.finish(this)
 * }
 * ```
 *
 * How it works: [start] draws the launching activity into a bitmap, keeps it in memory under a
 * token and sends the token along in the intent. [wrap] puts that picture behind the real page
 * and moves both itself, so the window animation is switched off. When there is no picture
 * (the activity was launched some other way, the process was restarted, or the picture would
 * exceed [MAX_SNAPSHOT_BYTES]) the page is shown at once and the plain window slide animations
 * `R.anim.sancai_slide_*` are used instead; the swipe back still works.
 *
 * The swipe leaves alone what needs horizontal drags itself: anything that is a
 * [SancaiHorizontalDragOwner], every `EditText`, and the 24 dp zones at both screen edges,
 * which belong to the system. A page that is in the middle of something can refuse the swipe
 * with a [SwipeGate].
 *
 * Only [Side.START] and [Side.END] are supported; they follow the layout direction.
 */
public object SancaiSpatialTransition {

    /**
     * Lets a page refuse the swipe back while it is in the middle of something, for example
     * while it shows a detail pane of its own or while one of its controls is being dragged.
     *
     * Implement it on the activity, or hand one to [setSwipeGate]. It is asked once, when a
     * finger goes down. [finish] is not affected by it.
     */
    public fun interface SwipeGate {
        /** Returns false to keep the page where it is for the gesture that is starting. */
        public fun canSwipeBack(): Boolean
    }

    /** A picture of the launching activity larger than this (16 MB of pixels) is not taken. */
    public const val MAX_SNAPSHOT_BYTES: Long = 16L * 1024L * 1024L

    /** Intent extra (int): token of the picture of the launching activity. */
    internal const val EXTRA_SNAPSHOT: String = "io.github.avalonskyafar.sancai.extra.SPATIAL_SNAPSHOT"

    /** Intent extra (string): name of the [Side] the page enters from. */
    internal const val EXTRA_ENTER_FROM: String = "io.github.avalonskyafar.sancai.extra.SPATIAL_ENTER_FROM"

    /**
     * Starts [intent] from [activity] as a neighbouring page that comes in from [enterFrom].
     *
     * Two extras are added to [intent]; the started activity must build its content with
     * [wrap], otherwise it simply appears without movement when a picture was taken.
     *
     * @throws IllegalArgumentException for [Side.ABOVE] and [Side.BELOW]
     */
    @JvmStatic
    @JvmOverloads
    public fun start(activity: Activity, intent: Intent, enterFrom: Side = Side.END) {
        requireHorizontal(enterFrom)
        val content: View? = activity.findViewById(android.R.id.content)
        val token = SancaiSpatialSnapshots.capture(content)
        if (token != 0) intent.putExtra(EXTRA_SNAPSHOT, token) else intent.removeExtra(EXTRA_SNAPSHOT)
        intent.putExtra(EXTRA_ENTER_FROM, enterFrom.name)
        activity.startActivity(intent)
        if (token != 0) {
            // The host in the new activity owns the whole movement. Running the window
            // animation as well would compose the movement twice.
            overrideTransition(activity, 0, 0)
            return
        }
        val fromLeft = direction(activity, enterFrom) < 0
        val reduced = SancaiTheme.of(activity).reduceMotion
        overrideTransition(activity, slideIn(fromLeft, reduced), slideOut(!fromLeft, reduced))
    }

    /**
     * Wraps [page] for an activity started with [start] and returns the view to pass to
     * `setContentView`.
     *
     * With a picture of the launching activity the page glides in over
     * [SancaiMotion.PAGE_MS]; without one it is simply there. Either way the returned view
     * follows horizontal swipes towards the entry side and finishes [activity] when one is
     * completed. An activity that implements [SwipeGate] is asked before each swipe.
     */
    @JvmStatic
    public fun wrap(activity: Activity, page: View): View {
        val intent = activity.intent
        val token = intent?.getIntExtra(EXTRA_SNAPSHOT, 0) ?: 0
        val snapshot = SancaiSpatialSnapshots.take(token)
        val host = SancaiSpatialSwipeHost(activity, page, snapshot, enterSide(intent))
        host.onFinish = Runnable {
            activity.finish()
            overrideTransition(activity, 0, 0)
        }
        return host
    }

    /**
     * Sends the page of [activity] back out the way it came in and finishes the activity when
     * it has left the screen. Call it for the back key and for a back button in the page.
     *
     * When the content was not built with [wrap], or there is no picture of the activity
     * below, the activity is finished at once with the window slide animations.
     */
    @JvmStatic
    public fun finish(activity: Activity) {
        val host = findHost(activity)
        if (host != null && host.finishProgrammatically()) return
        val towardsRight = direction(activity, enterSide(activity.intent)) > 0
        val reduced = SancaiTheme.of(activity).reduceMotion
        activity.finish()
        overrideTransition(activity, slideIn(towardsRight, reduced), slideOut(!towardsRight, reduced))
    }

    /**
     * Sets the [gate] asked before each swipe on [wrapped], the view returned by [wrap]. It
     * takes precedence over a gate implemented by the activity; null restores that default.
     *
     * @throws IllegalArgumentException when [wrapped] did not come from [wrap]
     */
    @JvmStatic
    public fun setSwipeGate(wrapped: View, gate: SwipeGate?) {
        require(wrapped is SancaiSpatialSwipeHost) { "Not a view returned by SancaiSpatialTransition.wrap()" }
        wrapped.swipeGate = gate
    }

    /** +1 when [side] is the right edge in the layout direction of [context], otherwise -1. */
    internal fun direction(context: Context, side: Side): Int {
        requireHorizontal(side)
        // Views only mirror when the application declares android:supportsRtl.
        val mirrors = context.applicationInfo.flags and ApplicationInfo.FLAG_SUPPORTS_RTL != 0
        val rtl = mirrors && context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        return if ((side == Side.END) != rtl) 1 else -1
    }

    /** Window animation for a page that comes in from the left or from the right edge. */
    internal fun slideIn(fromLeft: Boolean, reduced: Boolean): Int = when {
        fromLeft -> if (reduced) R.anim.sancai_slide_in_start_reduced else R.anim.sancai_slide_in_start
        else -> if (reduced) R.anim.sancai_slide_in_end_reduced else R.anim.sancai_slide_in_end
    }

    /** Window animation for a page that leaves through the left or through the right edge. */
    internal fun slideOut(toLeft: Boolean, reduced: Boolean): Int = when {
        toLeft -> if (reduced) R.anim.sancai_slide_out_start_reduced else R.anim.sancai_slide_out_start
        else -> if (reduced) R.anim.sancai_slide_out_end_reduced else R.anim.sancai_slide_out_end
    }

    private fun requireHorizontal(side: Side) {
        require(side == Side.START || side == Side.END) {
            "SancaiSpatialTransition moves pages sideways only: use Side.START or Side.END, not $side"
        }
    }

    /** The side recorded by [start]; a page launched some other way counts as coming from the end. */
    private fun enterSide(intent: Intent?): Side {
        val name = intent?.getStringExtra(EXTRA_ENTER_FROM)
        return if (name == Side.START.name) Side.START else Side.END
    }

    private fun findHost(activity: Activity): SancaiSpatialSwipeHost? {
        val content = activity.findViewById<View>(android.R.id.content) as? ViewGroup ?: return null
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child is SancaiSpatialSwipeHost) return child
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun overrideTransition(activity: Activity, enter: Int, exit: Int) {
        activity.overridePendingTransition(enter, exit)
    }
}

/**
 * Process-wide store of the pictures that travel from [SancaiSpatialTransition.start] to
 * [SancaiSpatialTransition.wrap]. Only the token crosses the intent; the pixels stay here.
 */
internal object SancaiSpatialSnapshots {
    /** Pictures nobody has claimed are dropped once more than this many are waiting. */
    internal const val MAX_PENDING: Int = 2

    private var nextToken = 1
    private val pending = LinkedHashMap<Int, Bitmap>()

    /** Number of pictures waiting to be claimed. */
    val size: Int
        @Synchronized get() = pending.size

    /** Draws [content] into a new bitmap and stores it. Returns its token, or 0 without one. */
    fun capture(content: View?): Int {
        if (content == null || content.width <= 0 || content.height <= 0) return 0
        if (content.width.toLong() * content.height * 4L > SancaiSpatialTransition.MAX_SNAPSHOT_BYTES) return 0
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        content.draw(Canvas(bitmap))
        return put(bitmap)
    }

    /**
     * Stores [bitmap] and returns its token (never 0). A recycled bitmap or one above
     * [SancaiSpatialTransition.MAX_SNAPSHOT_BYTES] is refused with 0 and left untouched.
     * The oldest unclaimed pictures beyond [MAX_PENDING] are recycled: their activity never
     * came to collect them.
     */
    @Synchronized
    fun put(bitmap: Bitmap): Int {
        if (bitmap.isRecycled) return 0
        if (bitmap.width.toLong() * bitmap.height * 4L > SancaiSpatialTransition.MAX_SNAPSHOT_BYTES) return 0
        val token = nextToken
        nextToken = if (nextToken == Int.MAX_VALUE) 1 else nextToken + 1
        pending[token] = bitmap
        val stale = pending.entries.iterator()
        while (pending.size > MAX_PENDING && stale.hasNext()) {
            val eldest = stale.next().value
            stale.remove()
            if (!eldest.isRecycled) eldest.recycle()
        }
        return token
    }

    /** Hands out the picture stored under [token], once. Null when there is none (any more). */
    @Synchronized
    fun take(token: Int): Bitmap? {
        if (token == 0) return null
        val bitmap = pending.remove(token) ?: return null
        return if (bitmap.isRecycled) null else bitmap
    }

    /** Recycles everything that is still waiting. */
    @Synchronized
    fun clear() {
        pending.values.forEach { if (!it.isRecycled) it.recycle() }
        pending.clear()
    }
}

/**
 * The view behind [SancaiSpatialTransition.wrap]: the real [page] on top of a picture of the
 * activity below. Both are moved by one offset, so they always meet edge to edge.
 *
 * It keeps no state of its own to save: where the page rests is always zero. The back key is
 * the activity's business ([SancaiSpatialTransition.finish]); the picture is hidden from
 * accessibility services, which only ever see the page.
 */
@SuppressLint("ViewConstructor")
internal class SancaiSpatialSwipeHost(
    context: Context,
    private val page: View,
    snapshot: Bitmap?,
    enterFrom: Side,
) : FrameLayout(context) {

    /** Runs when the page has left the screen after a swipe or a programmatic finish. */
    var onFinish: Runnable? = null

    /** Overrides the gate implemented by the activity. */
    var swipeGate: SancaiSpatialTransition.SwipeGate? = null

    /** Where the page is: 0 at rest, one width towards the entry side when it is gone. */
    val pageOffset: Float
        get() = offset

    /** True while the picture of the activity below is behind the page. */
    val hasSnapshot: Boolean
        get() = snapshot != null

    /** True while the page moves on its own: coming in, going out or returning. */
    val isMoving: Boolean
        get() = entering || finishing || animator != null

    private val theme = SancaiTheme.of(context)
    private val direction = SancaiSpatialTransition.direction(context, enterFrom)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val edgeZone = (EDGE_ZONE_DP * resources.displayMetrics.density).roundToInt()
    private val commitVelocity = SancaiMotion.COMMIT_VELOCITY_DP * resources.displayMetrics.density
    private val backdrop = ImageView(context)
    private var snapshot: Bitmap? = snapshot?.takeUnless { it.isRecycled }
    private val hitBounds = Rect()

    private var offset = 0f
    private var downX = 0f
    private var downY = 0f
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var eligible = false
    private var dragging = false
    private var entering = true
    private var enterStarted = false
    private var finishing = false
    private var velocity: VelocityTracker? = null
    private var animator: ValueAnimator? = null

    init {
        setBackgroundColor(theme.colors.canvas)
        clipChildren = true
        backdrop.scaleType = ImageView.ScaleType.FIT_XY
        backdrop.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(backdrop, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        (page.parent as? ViewGroup)?.removeView(page)
        addView(page, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (this.snapshot != null) {
            backdrop.setImageBitmap(this.snapshot)
            // The page stays out of the first frame until its starting position, one full
            // width away, is known. The picture is visible meanwhile, so nothing fades.
            page.visibility = INVISIBLE
        } else {
            // Launched without a picture: the page is usable at once and keeps the swipe.
            backdrop.visibility = GONE
            entering = false
            enterStarted = true
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (!enterStarted && width > 0) {
            enterStarted = true
            applyOffset(direction * width.toFloat())
            page.visibility = VISIBLE
            post {
                if (finishing || !entering) return@post
                val duration = if (theme.reduceMotion) SancaiMotion.REDUCED_MS else SancaiMotion.PAGE_MS
                glideTo(0f, duration) { entering = false }
            }
        } else if (!entering && !finishing && !dragging && animator == null) {
            // Keeps the picture waiting just off screen when the width changes.
            applyOffset(0f)
        }
    }

    /**
     * Glides the page out and reports through [onFinish]. Returns false when this host cannot
     * do it (not laid out, or nothing to show behind the page), so the caller falls back to
     * the window animation.
     */
    fun finishProgrammatically(): Boolean {
        if (finishing) return true
        if (width <= 0 || snapshot == null) return false
        entering = false
        dragging = false
        eligible = false
        settle(complete = true)
        return true
    }

    private fun applyOffset(value: Float) {
        val width = max(1, width).toFloat()
        offset = if (direction > 0) value.coerceIn(0f, width) else value.coerceIn(-width, 0f)
        page.translationX = offset
        backdrop.translationX = offset - direction * width
    }

    private fun settle(complete: Boolean) {
        finishing = complete
        val width = max(1, width).toFloat()
        val target = if (complete) direction * width else 0f
        val remaining = abs(target - offset) / width
        glideTo(target, SancaiMotion.settleDuration(remaining, theme.reduceMotion)) {
            dragging = false
            eligible = false
            if (complete) onFinish?.run()
        }
    }

    /** Moves the page to [target] with the one easing; [end] does not run when cancelled. */
    private fun glideTo(target: Float, duration: Long, end: () -> Unit) {
        cancelAnimator()
        val running = ValueAnimator.ofFloat(offset, target)
        running.duration = duration
        running.interpolator = SancaiMotion.glide()
        running.addUpdateListener { applyOffset(it.animatedValue as Float) }
        running.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (animator === running) animator = null
                if (cancelled) return
                applyOffset(target)
                end()
            }
        })
        animator = running
        running.start()
    }

    private fun cancelAnimator() {
        val running = animator ?: return
        animator = null
        running.cancel()
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isMoving) {
            // The page is on its way; a touch neither stops it nor reaches the content.
            eligible = false
            return true
        }
        return track(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isMoving) {
            eligible = false
            return true
        }
        // Reached when nothing in the page wanted the touch, or once the drag is ours. The
        // down event has already been through onInterceptTouchEvent.
        if (event.actionMasked != MotionEvent.ACTION_DOWN) track(event)
        return true
    }

    /** Follows one gesture. Returns true from the moment the page is being dragged. */
    private fun track(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                pointerId = event.getPointerId(0)
                dragging = false
                eligible = downX >= edgeZone && downX <= width - edgeZone &&
                    gateAllows() && !startsOnDragOwner(downX, downY)
                recycleVelocity()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                // Only the finger that went down first moves the page; a second one is ignored.
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return dragging
                val dx = event.getX(index) - downX
                val dy = event.getY(index) - downY
                if (dragging) {
                    applyOffset(dx)
                } else if (eligible && max(abs(dx), abs(dy)) >= touchSlop) {
                    if (abs(dy) >= abs(dx) || sign(dx).toInt() != direction) {
                        // Vertical, or away from where the page came from: not ours.
                        eligible = false
                    } else {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        applyOffset(dx)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.addMovement(event)
                // Another finger lifting changes nothing; ours lifting ends the gesture even
                // while others stay down.
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP &&
                    event.getPointerId(event.actionIndex) != pointerId
                ) {
                    return dragging
                }
                if (dragging) {
                    var speed = 0f
                    velocity?.let {
                        it.computeCurrentVelocity(1000)
                        speed = it.getXVelocity(pointerId)
                    }
                    val complete = event.actionMasked != MotionEvent.ACTION_CANCEL &&
                        (abs(offset) >= width * SancaiMotion.COMMIT_FRACTION ||
                            (abs(speed) >= commitVelocity && sign(speed).toInt() == direction))
                    settle(complete)
                }
                eligible = false
                pointerId = MotionEvent.INVALID_POINTER_ID
                recycleVelocity()
            }
        }
        return dragging
    }

    private fun gateAllows(): Boolean {
        val gate = swipeGate ?: activityGate()
        return gate == null || gate.canSwipeBack()
    }

    private fun activityGate(): SancaiSpatialTransition.SwipeGate? {
        var current: Context? = context
        while (current != null) {
            if (current is SancaiSpatialTransition.SwipeGate) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }

    /** True when the point, in this view's coordinates, lies on a view that drags sideways itself. */
    private fun startsOnDragOwner(x: Float, y: Float): Boolean {
        // Visible rectangles are in the coordinates of the window's root view, not of the
        // screen, so the point is moved there rather than taken from the raw position.
        if (!getGlobalVisibleRect(hitBounds)) return false
        return excluded(this, hitBounds.left + x.roundToInt(), hitBounds.top + y.roundToInt())
    }

    private fun excluded(candidate: View, rawX: Int, rawY: Int): Boolean {
        if (!candidate.isShown) return false
        if (!candidate.getGlobalVisibleRect(hitBounds) || !hitBounds.contains(rawX, rawY)) return false
        if (candidate is EditText || candidate is SancaiHorizontalDragOwner) return true
        if (candidate !is ViewGroup) return false
        for (index in candidate.childCount - 1 downTo 0) {
            if (excluded(candidate.getChildAt(index), rawX, rawY)) return true
        }
        return false
    }

    private fun recycleVelocity() {
        velocity?.recycle()
        velocity = null
    }

    override fun onDetachedFromWindow() {
        cancelAnimator()
        recycleVelocity()
        dragging = false
        eligible = false
        if (!finishing) {
            // Whatever was under way, the page is found at rest if the view comes back.
            entering = false
            enterStarted = true
            page.visibility = VISIBLE
            page.translationX = 0f
            offset = 0f
        }
        backdrop.setImageDrawable(null)
        backdrop.visibility = GONE
        snapshot?.let { if (!it.isRecycled) it.recycle() }
        snapshot = null
        super.onDetachedFromWindow()
    }

    private companion object {
        /** Width of the zones at both edges that are left to the system gestures. */
        const val EDGE_ZONE_DP = 24f
    }
}
