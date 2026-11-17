package io.github.avalonskyafar.sancai.showcase

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.View.MeasureSpec
import android.widget.ScrollView
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Builds every page of the showcase, in light and in dark, the way the app does, and writes a
 * picture of each to `showcase/build/snapshots`.
 *
 * A page that throws while it is built, or that comes out empty, fails the test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShowcasePagesTest {

    private class TestHost(override val activity: Activity, override val settings: ShowcaseSettings) : ShowcaseHost {
        val opened = ArrayList<String>()
        val messages = ArrayList<CharSequence>()

        override fun open(key: String) {
            opened += key
        }

        override fun back() = Unit

        override fun say(message: CharSequence) {
            messages += message
        }

        override fun applyAppearance(source: View) = Unit
    }

    @After
    fun resetTheme() {
        SancaiTheme.resolver = null
    }

    private fun render(key: String, appearance: ShowcaseSettings.Appearance): Bitmap {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val settings = ShowcaseSettings(activity)
        settings.appearance = appearance
        SancaiTheme.resolver = SancaiTheme.Resolver { context -> settings.toTheme(context) }
        val view = Pages.create(key, TestHost(activity, settings))
        activity.setContentView(view)
        ShadowLooper.idleMainLooper()

        val density = activity.resources.displayMetrics.density
        val width = (WIDTH_DP * density).roundToInt()
        val screen = (SCREEN_HEIGHT_DP * density).roundToInt()
        // A scrolling page is drawn at its full height, capped so the picture stays readable.
        val content = (view as? ScrollView)?.getChildAt(0)
        var height = screen
        if (content != null) {
            content.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            height = min((MAX_HEIGHT_DP * density).roundToInt(), maxOf(screen, content.measuredHeight))
        }
        view.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        val dir = File(System.getProperty("sancai.snapshot.dir") ?: "build/snapshots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Share of pixels that differ from the top-left pixel: zero means a blank page. */
    private fun inkShare(bitmap: Bitmap): Float {
        val background = bitmap.getPixel(0, 0)
        var different = 0
        var total = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (bitmap.getPixel(x, y) != background) different++
                total++
                x += 4
            }
            y += 4
        }
        return different.toFloat() / total
    }

    @Test
    fun `every page builds and draws in light and dark`() {
        val failures = ArrayList<String>()
        for (key in Pages.graph.keys.sorted()) {
            for (appearance in listOf(ShowcaseSettings.Appearance.LIGHT, ShowcaseSettings.Appearance.DARK)) {
                val name = "page_${key}_${appearance.name.lowercase()}"
                try {
                    val bitmap = render(key, appearance)
                    save(bitmap, name)
                    if (inkShare(bitmap) < 0.002f) failures += "$name is blank"
                } catch (error: Throwable) {
                    failures += "$name threw ${error::class.java.simpleName}: ${error.message}\n" +
                        error.stackTrace.take(6).joinToString("\n") { "      at $it" }
                }
            }
        }
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
    }

    @Test
    fun `every page of the registry has a place in the graph`() {
        val listed = (Pages.foundations + Pages.components + Pages.interactions).map { it.key }
        assertThat(Pages.graph.keys).containsAtLeastElementsIn(listed)
        assertThat(listed).containsNoDuplicates()
        for (key in Pages.graph.keys) {
            if (key != Pages.HOME) assertThat(Pages.graph.parentOf(key)).isNotNull()
        }
    }

    @Test
    fun `settings turn into a theme`() {
        val context: Context = Robolectric.buildActivity(Activity::class.java).setup().get()
        val settings = ShowcaseSettings(context)
        settings.appearance = ShowcaseSettings.Appearance.DARK
        settings.largeText = true
        settings.haptics = false
        settings.glazeSet = ShowcaseSettings.GlazeSet.PLUM
        val theme = settings.toTheme(context)
        assertThat(theme.isDark).isTrue()
        assertThat(theme.textScale).isEqualTo(SancaiTheme.LARGE_TEXT_SCALE)
        assertThat(theme.haptics).isFalse()
        assertThat(theme.glazes).isEqualTo(ShowcaseSettings.plumGlazes())
    }

    private companion object {
        const val WIDTH_DP = 411
        const val SCREEN_HEIGHT_DP = 860
        const val MAX_HEIGHT_DP = 3200
    }
}
