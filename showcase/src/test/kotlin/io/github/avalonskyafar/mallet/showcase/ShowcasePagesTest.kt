package io.github.avalonskyafar.mallet.showcase

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.View.MeasureSpec
import android.widget.ScrollView
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.avalonskyafar.mallet.theme.MalletTheme
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Builds every page of the showcase the way the app does and writes a picture of each to
 * `showcase/build/snapshots`: in light and in dark, in Chinese, right to left, and with large
 * text (system font scale 1.3 together with the app's larger-text setting).
 *
 * A page that throws while it is built, or that comes out empty, fails the test. Whether a
 * page looks right is judged by a person who opens the pictures.
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
        MalletTheme.resolver = null
    }

    /** One way of looking at a page; [suffix] ends the name of its picture. */
    private class Variant(
        val suffix: String,
        val appearance: ShowcaseSettings.Appearance,
        val largeText: Boolean = false,
    )

    private fun render(key: String, variant: Variant): Bitmap {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val settings = ShowcaseSettings(activity)
        settings.appearance = variant.appearance
        settings.largeText = variant.largeText
        MalletTheme.resolver = MalletTheme.Resolver { context -> settings.toTheme(context) }
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
            // The page padding lies on the scroll view, around the column.
            content.measure(
                MeasureSpec.makeMeasureSpec(width - view.paddingLeft - view.paddingRight, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            val whole = content.measuredHeight + view.paddingTop + view.paddingBottom
            height = min((MAX_HEIGHT_DP * density).roundToInt(), maxOf(screen, whole))
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
        val dir = File(System.getProperty("mallet.snapshot.dir") ?: "build/snapshots")
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
    fun `every page builds and draws in light and dark`() = renderAll(
        Variant("light", ShowcaseSettings.Appearance.LIGHT),
        Variant("dark", ShowcaseSettings.Appearance.DARK),
    )

    @Test
    @Config(qualifiers = "+zh")
    fun `every page builds and draws in Chinese`() = renderAll(Variant("zh", ShowcaseSettings.Appearance.LIGHT))

    @Test
    @Config(qualifiers = "+ar-ldrtl")
    fun `every page builds and draws right to left`() = renderAll(Variant("rtl", ShowcaseSettings.Appearance.DARK))

    @Test
    @Config(fontScale = 1.3f)
    fun `every page builds and draws with large text`() =
        renderAll(Variant("large", ShowcaseSettings.Appearance.LIGHT, largeText = true))

    private fun renderAll(vararg variants: Variant) {
        val failures = ArrayList<String>()
        // -PmalletPages=<regex> renders only the pages whose snapshot name matches.
        val only = System.getProperty("mallet.pages")?.takeIf { it.isNotBlank() }?.toRegex()
        for (key in Pages.graph.keys.sorted()) {
            for (variant in variants) {
                val name = "page_${key}_${variant.suffix}"
                if (only != null && !only.containsMatchIn(name)) continue
                try {
                    val bitmap = render(key, variant)
                    save(bitmap, name)
                    val blank = inkShare(bitmap) < 0.002f
                    bitmap.recycle()
                    if (blank) failures += "$name is blank"
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
        settings.hueSet = ShowcaseSettings.HueSet.PLUM
        val theme = settings.toTheme(context)
        assertThat(theme.isDark).isTrue()
        assertThat(theme.textScale).isEqualTo(MalletTheme.LARGE_TEXT_SCALE)
        assertThat(theme.haptics).isFalse()
        assertThat(theme.hues).isEqualTo(ShowcaseSettings.plumHues())
    }

    private companion object {
        const val WIDTH_DP = 411
        const val SCREEN_HEIGHT_DP = 860
        const val MAX_HEIGHT_DP = 3200
    }
}
