package io.github.avalonskyafar.sancai.interaction

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.MainThread
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.util.WeakHashMap
import kotlin.random.Random

/**
 * Changes the theme without a flash.
 *
 * Switching the palette recreates the activity, which would cut hard from the old colours to
 * the new ones. This helper captures the old appearance just before the recreation, lays it
 * over the freshly built content and fades it away. Nothing moves, only the light changes,
 * which is why this is the one place in the toolkit where a fade is the right motion.
 *
 * ```kotlin
 * // where the user picks a theme, e.g. in a SancaiSegmentedTrack listener
 * track.setOnSelectionChangeListener { _, index ->
 *     savePalette(index)
 *     SancaiThemeTransition.recreateAfter(track)   // lets the thumb finish its glide first
 * }
 *
 * // in the activity
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     setContentView(page)
 *     SancaiThemeTransition.play(this)
 * }
 * ```
 *
 * The captured picture is kept in memory under a token that travels in the activity's
 * intent extras: it survives the recreation but not process death, after which [play] simply
 * does nothing. Pictures larger than [MAX_SNAPSHOT_BYTES] are not taken; the theme then
 * changes with the plain cut. With reduced motion nothing is captured or faded.
 *
 * All functions must be called on the main thread.
 */
public object SancaiThemeTransition {

    /** Intent extra that carries the token of the captured appearance across recreation. */
    public const val EXTRA_SNAPSHOT: String = "io.github.avalonskyafar.sancai.extra.THEME_SNAPSHOT"

    /** Largest picture, in bytes of ARGB pixels, that is captured (16 MB). */
    public const val MAX_SNAPSHOT_BYTES: Long = 16L * 1024L * 1024L

    /** Default delay of [recreateAfter]: one control glide plus two frames. */
    public const val DEFAULT_DELAY_MS: Long = SancaiMotion.CONTROL_MS + 32L

    /**
     * Pictures that may wait for [play] at the same time. A picture whose activity never
     * plays it (it was destroyed, or does not call [play]) would otherwise stay in memory;
     * the oldest ones beyond this count are dropped as stale.
     */
    internal const val MAX_WAITING: Int = 4

    /** Lift of the overlay, above any elevation an app gives its content. */
    private const val OVERLAY_Z_DP: Float = 1000f

    // Insertion-ordered, so the eldest entry is the stalest picture.
    private val snapshots = LinkedHashMap<Int, Bitmap>()
    private val pending = WeakHashMap<View, Runnable>()

    // A random start keeps a token that outlived its process from matching a newer picture.
    private var nextToken = Random.nextInt(1, 1 shl 24)

    /** Number of captured pictures waiting for [play]. */
    internal val storedCount: Int get() = snapshots.size

    /**
     * Captures the current appearance of [activity] for the next [play], without recreating.
     *
     * Use this when something else triggers the recreation (for example
     * `AppCompatDelegate`-style night mode switches); otherwise call [recreate]. A picture
     * prepared earlier and never played is dropped first. Returns `false`, leaving nothing
     * prepared, when motion is reduced, the content is not laid out yet or the picture would
     * exceed [MAX_SNAPSHOT_BYTES].
     */
    @JvmStatic
    @MainThread
    public fun prepare(activity: Activity): Boolean {
        discard(activity)
        val theme = SancaiTheme.of(activity)
        if (theme.reduceMotion) return false
        val content: View = activity.findViewById(android.R.id.content) ?: return false
        // The window background is not part of the content view; the canvas colour stands in
        // for it so that a page without a background of its own still fades from the old tone.
        val bitmap = capture(content, theme.colors.canvas) ?: return false
        val token = nextToken
        nextToken = if (nextToken == Int.MAX_VALUE) 1 else nextToken + 1
        snapshots[token] = bitmap
        while (snapshots.size > MAX_WAITING) {
            val stale = snapshots.entries.iterator()
            val picture = stale.next().value
            stale.remove()
            if (!picture.isRecycled) picture.recycle()
        }
        activity.intent.putExtra(EXTRA_SNAPSHOT, token)
        return true
    }

    /** Whether [activity] carries a captured appearance that [play] would fade out. */
    @JvmStatic
    @MainThread
    public fun isPrepared(activity: Activity): Boolean {
        val token = activity.intent?.getIntExtra(EXTRA_SNAPSHOT, 0) ?: 0
        return token != 0 && snapshots[token]?.isRecycled == false
    }

    /**
     * Forgets the appearance captured for [activity], if any, and frees its picture. Call it
     * when a prepared recreation is not going to happen after all.
     */
    @JvmStatic
    @MainThread
    public fun discard(activity: Activity) {
        take(activity)?.let { if (!it.isRecycled) it.recycle() }
    }

    /**
     * Captures the current appearance of [activity] and recreates it. The new instance shows
     * the cross-fade when it calls [play] in `onCreate`. Does nothing for an activity that is
     * finishing, destroyed or already being recreated.
     */
    @JvmStatic
    @MainThread
    public fun recreate(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || activity.isChangingConfigurations) return
        prepare(activity)
        activity.recreate()
    }

    /**
     * Recreates the activity that hosts [view] after [delayMs], so that the control which
     * triggered the change finishes settling in the old theme first.
     *
     * Repeated calls for the same activity coalesce: the earlier request is dropped and the
     * delay starts again, so a quick run of changes recreates once. Returns `false` when
     * [view] does not live in an activity.
     */
    @JvmStatic
    @JvmOverloads
    @MainThread
    public fun recreateAfter(view: View, delayMs: Long = DEFAULT_DELAY_MS): Boolean {
        val activity = activityOf(view.context) ?: return false
        val host = activity.window.decorView
        pending.remove(host)?.let(host::removeCallbacks)
        val request = object : Runnable {
            override fun run() {
                if (pending[host] === this) pending.remove(host)
                recreate(activity)
            }
        }
        pending[host] = request
        host.postDelayed(request, delayMs.coerceAtLeast(0L))
        return true
    }

    /**
     * Withdraws a recreation requested with [recreateAfter] for the activity that hosts
     * [view]. Returns whether one was waiting.
     */
    @JvmStatic
    @MainThread
    public fun cancelRecreate(view: View): Boolean {
        val host = activityOf(view.context)?.window?.decorView ?: return false
        val request = pending.remove(host) ?: return false
        host.removeCallbacks(request)
        return true
    }

    /**
     * Lays the appearance captured before the recreation over the content of [activity] and
     * fades it out over [SancaiMotion.THEME_FADE_MS]. Call it in `onCreate` after
     * `setContentView`.
     *
     * The overlay is a plain picture: it takes no touches, is hidden from accessibility
     * services, and is removed (and its bitmap recycled) when the fade ends or the activity
     * goes away. Returns `false` and shows nothing when no appearance was prepared, the
     * process was restarted in between, or motion is reduced.
     */
    @JvmStatic
    @MainThread
    public fun play(activity: Activity): Boolean {
        val bitmap = take(activity) ?: return false
        val container = activity.findViewById<View>(android.R.id.content) as? ViewGroup
        if (bitmap.isRecycled || container == null || SancaiTheme.of(activity).reduceMotion) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return false
        }
        val overlay = ImageView(activity)
        overlay.scaleType = ImageView.ScaleType.FIT_XY
        overlay.setImageBitmap(bitmap)
        overlay.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        // Children with elevation are drawn after flat ones whatever their index; the old
        // picture has to cover them too. Without an outline it casts no shadow of its own.
        overlay.outlineProvider = null
        overlay.translationZ = OVERLAY_Z_DP * activity.resources.displayMetrics.density
        var released = false
        val release = Runnable {
            if (!released) {
                released = true
                // Posted: a view must not be removed while its parent dispatches the detach.
                container.post { container.removeView(overlay) }
                overlay.setImageDrawable(null)
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        container.addView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        overlay.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                overlay.animate().cancel()
                release.run()
            }
        })
        overlay.animate()
            .alpha(0f)
            .setDuration(SancaiMotion.THEME_FADE_MS)
            .setInterpolator(SancaiMotion.fade())
            .withEndAction(release)
            .start()
        return true
    }

    /** Removes and returns the picture [activity] carries, clearing the intent extra. */
    private fun take(activity: Activity): Bitmap? {
        val intent = activity.intent ?: return null
        val token = intent.getIntExtra(EXTRA_SNAPSHOT, 0)
        if (token == 0) return null
        intent.removeExtra(EXTRA_SNAPSHOT)
        return snapshots.remove(token)
    }

    /** Draws [content] on [backdrop]; `null` when it has no size or is over the limit. */
    internal fun capture(content: View, backdrop: Int): Bitmap? {
        val width = content.width
        val height = content.height
        if (width <= 0 || height <= 0) return null
        if (width.toLong() * height.toLong() * 4L > MAX_SNAPSHOT_BYTES) return null
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (tooLarge: OutOfMemoryError) {
            return null
        }
        val canvas = Canvas(bitmap)
        canvas.drawColor(backdrop)
        try {
            content.draw(canvas)
        } catch (undrawable: RuntimeException) {
            // Some content cannot be drawn in software (hardware bitmaps, for one). The
            // theme then changes with the plain cut instead of crashing.
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    private fun activityOf(context: Context?): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
