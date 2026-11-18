package io.github.avalonskyafar.mallet.showcase

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.avalonskyafar.mallet.theme.MalletTheme
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt
import org.robolectric.shadows.ShadowLooper

/**
 * Films the running app frame by frame on the JVM.
 *
 * Time does not pass by itself in a Robolectric test: [frame] moves the clock on by
 * [FRAME_MS], lets the app run whatever is due (animations, posted work, layout), draws the
 * whole window into a bitmap and writes it as the next picture of the film. Touches are real
 * `MotionEvent`s given to the activity, one per frame, so a thumb follows the finger exactly
 * as it does on a device; only the clock is artificial. A dot marks where the finger is.
 *
 * The result is a sequence of pictures, `build/films/<name>/0001.png` and so on, at half the
 * resolution of the window. `tools/films.py` turns each sequence into a GIF.
 */
internal class Film(private val activity: Activity, name: String) {

    private val decor: View = activity.window.decorView
    private val directory = File(System.getProperty("mallet.film.dir") ?: "build/films", name)
    private val bitmap: Bitmap
    private val canvas: Canvas
    private val density = activity.resources.displayMetrics.density
    private val background = MalletTheme.of(activity).colors.canvas
    private val fingerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fingerRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var count = 0
    private var fingerX = 0f
    private var fingerY = 0f
    private var fingerAlpha = 0f
    private var fingerDown = false
    private var downTime = 0L

    /** Width and height of the window in pixels. */
    val width: Int get() = decor.width
    val height: Int get() = decor.height

    init {
        ShadowLooper.idleMainLooper()
        check(decor.width > 0 && decor.height > 0) { "the window has no size" }
        directory.deleteRecursively()
        directory.mkdirs()
        bitmap = Bitmap.createBitmap(
            (decor.width * SCALE).roundToInt(), (decor.height * SCALE).roundToInt(), Bitmap.Config.ARGB_8888,
        )
        canvas = Canvas(bitmap)
    }

    /** Moves the clock on by one frame, lets the app run, and takes a picture. */
    fun frame() {
        ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
        val save = canvas.save()
        canvas.scale(SCALE, SCALE)
        canvas.drawColor(background)
        decor.draw(canvas)
        if (fingerAlpha > 0f) drawFinger()
        canvas.restoreToCount(save)
        count++
        File(directory, "%04d.png".format(count)).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        if (!fingerDown && fingerAlpha > 0f) fingerAlpha = max(0f, fingerAlpha - 0.25f)
    }

    /** Lets [ms] pass, a frame at a time. */
    fun hold(ms: Long) {
        repeat((ms / FRAME_MS).toInt().coerceAtLeast(1)) { frame() }
    }

    private fun drawFinger() {
        val radius = 17f * density
        fingerFill.color = (((0.30f * fingerAlpha) * 255).roundToInt() shl 24) or 0x808890
        fingerRim.color = (((0.55f * fingerAlpha) * 255).roundToInt() shl 24) or 0xFFFFFF
        fingerRim.strokeWidth = 1.5f * density
        canvas.drawCircle(fingerX, fingerY, radius, fingerFill)
        canvas.drawCircle(fingerX, fingerY, radius, fingerRim)
    }

    private fun send(action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            activity.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    /**
     * A finger that goes down at the first point, speeds up, travels and slows down to rest at
     * the second after [ms], waits there for [restMs] and lifts. Positions are in pixels of the
     * window.
     */
    fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float, ms: Long = 650, restMs: Long = 0) {
        downTime = SystemClock.uptimeMillis()
        fingerDown = true
        fingerAlpha = 1f
        fingerX = fromX
        fingerY = fromY
        send(MotionEvent.ACTION_DOWN, fromX, fromY)
        frame()
        val steps = (ms / FRAME_MS).toInt().coerceAtLeast(2)
        for (step in 1..steps) {
            val t = step / steps.toFloat()
            val eased = t * t * (3f - 2f * t)
            fingerX = fromX + (toX - fromX) * eased
            fingerY = fromY + (toY - fromY) * eased
            send(MotionEvent.ACTION_MOVE, fingerX, fingerY)
            frame()
        }
        if (restMs > 0) hold(restMs)
        send(MotionEvent.ACTION_UP, toX, toY)
        fingerDown = false
    }

    /** [drag] across [view], in shares of its width, at the share [fy] of its height. */
    fun drag(view: View, fromFx: Float, toFx: Float, fy: Float = 0.5f, ms: Long = 650, restMs: Long = 0) {
        val origin = origin(view)
        val y = origin[1] + view.height * fy
        drag(origin[0] + view.width * fromFx, y, origin[0] + view.width * toFx, y, ms, restMs)
    }

    /** A tap: the finger rests for [ms] and lifts. */
    fun tap(x: Float, y: Float, ms: Long = 80) {
        downTime = SystemClock.uptimeMillis()
        fingerDown = true
        fingerAlpha = 1f
        fingerX = x
        fingerY = y
        send(MotionEvent.ACTION_DOWN, x, y)
        hold(ms)
        send(MotionEvent.ACTION_UP, x, y)
        fingerDown = false
    }

    /** A tap on [view] at the given shares of its width and height. */
    fun tap(view: View, fx: Float = 0.5f, fy: Float = 0.5f, ms: Long = 80) {
        val origin = origin(view)
        tap(origin[0] + view.width * fx, origin[1] + view.height * fy, ms)
    }

    private fun origin(view: View): IntArray = IntArray(2).also { view.getLocationInWindow(it) }

    /** How many pictures the film has so far. */
    val frames: Int get() = count

    companion object {
        /** One frame of a film: 50 pictures a second. */
        const val FRAME_MS: Long = 20
        private const val SCALE = 0.5f

        /** Every visible view of type [T] under [root], in drawing order. */
        inline fun <reified T : View> all(root: View): List<T> {
            val found = ArrayList<T>()
            collect(root, T::class.java, found)
            return found
        }

        fun <T : View> collect(view: View, type: Class<T>, into: MutableList<T>) {
            if (view.visibility != View.VISIBLE) return
            if (type.isInstance(view)) into.add(type.cast(view)!!)
            if (view is ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index), type, into)
        }
    }
}
