package io.github.avalonskyafar.sancai.testing

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.io.File
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper
import kotlin.math.roundToInt

/**
 * Renders views to real bitmaps on the JVM (Robolectric native graphics) so that tests can
 * assert on pixels and leave a PNG behind for a human to look at.
 *
 * PNGs are written to `sancai/build/snapshots`.
 */
object Snapshots {

    /** A context whose Sancai views use [theme]. */
    fun context(theme: SancaiTheme = SancaiTheme.light()): Context =
        SancaiTheme.wrap(ApplicationProvider.getApplicationContext(), theme)

    /** Measures and lays out [view] at [widthDp] and, when given, [heightDp]. */
    fun layout(view: View, widthDp: Int, heightDp: Int? = null) {
        val density = view.resources.displayMetrics.density
        val widthSpec = MeasureSpec.makeMeasureSpec((widthDp * density).roundToInt(), MeasureSpec.EXACTLY)
        val heightSpec = if (heightDp == null) {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        } else {
            MeasureSpec.makeMeasureSpec((heightDp * density).roundToInt(), MeasureSpec.EXACTLY)
        }
        view.measure(widthSpec, heightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    /**
     * Puts [view] into a resumed activity, [widthDp] wide, and runs the pending layout. Use
     * this when the test needs a window: clicks, focus, animations and dialogs.
     */
    fun attach(view: View, widthDp: Int = 360): Activity {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val density = activity.resources.displayMetrics.density
        val frame = FrameLayout(activity)
        frame.addView(
            view,
            FrameLayout.LayoutParams((widthDp * density).roundToInt(), FrameLayout.LayoutParams.WRAP_CONTENT),
        )
        activity.setContentView(frame)
        idle()
        return activity
    }

    /** Runs everything queued on the main looper, including posted clicks and layout. */
    fun idle() {
        ShadowLooper.idleMainLooper()
    }

    /** Lays [view] out and draws it on the canvas colour of its theme. */
    fun render(view: View, widthDp: Int, heightDp: Int? = null): Bitmap {
        layout(view, widthDp, heightDp)
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(SancaiTheme.of(view.context).colors.canvas)
        view.draw(canvas)
        return bitmap
    }

    /** Writes [bitmap] as `<name>.png` into the snapshot directory and returns the file. */
    fun save(bitmap: Bitmap, name: String): File {
        val dir = File(System.getProperty("sancai.snapshot.dir") ?: "build/snapshots")
        dir.mkdirs()
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    /** [render] followed by [save]. */
    fun capture(view: View, name: String, widthDp: Int, heightDp: Int? = null): Bitmap =
        render(view, widthDp, heightDp).also { save(it, name) }
}
