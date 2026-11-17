package io.github.avalonskyafar.sancai.widget

import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiProgressTest {

    private fun bar(
        progress: Float = 0f,
        theme: SancaiTheme = SancaiTheme.light(),
    ): SancaiProgressBar = SancaiProgressBar(Snapshots.context(theme)).apply {
        this.progress = progress
        Snapshots.layout(this, WIDTH_DP)
    }

    /** The pixel on the centre line of the groove at [fraction] of the width from the left. */
    private fun Bitmap.onGroove(view: View, fraction: Float): Int {
        val density = view.resources.displayMetrics.density
        return getPixel((width * fraction).toInt(), (9f * density).toInt())
    }

    /** True when the two colours differ by at most [tolerance] per channel. */
    private fun close(first: Int, second: Int, tolerance: Int = 14): Boolean =
        abs(Color.red(first) - Color.red(second)) <= tolerance &&
            abs(Color.green(first) - Color.green(second)) <= tolerance &&
            abs(Color.blue(first) - Color.blue(second)) <= tolerance

    private fun fillOf(view: SancaiProgressBar, theme: SancaiTheme): Int = theme.bedColor(view.glaze)

    private fun grooveOf(theme: SancaiTheme): Int =
        if (theme.isDark) theme.groove else theme.bedColor(theme.glazes.mist)

    // ---------------------------------------------------------------------------------------
    // Progress bar
    // ---------------------------------------------------------------------------------------

    @Test
    fun `bar is 22 dp tall and takes neither focus nor clicks`() {
        val view = bar()
        val density = view.resources.displayMetrics.density
        assertThat(view.height).isEqualTo(Math.round(22 * density))
        assertThat(view.isFocusable).isFalse()
        assertThat(view.isClickable).isFalse()
        assertThat(Touch.dispatch(view, android.view.MotionEvent.ACTION_DOWN, 20f, 20f)).isFalse()
    }

    @Test
    fun `progress is clamped and survives nonsense`() {
        val view = bar()
        view.progress = 1.7f
        assertThat(view.progress).isEqualTo(1f)
        view.progress = -3f
        assertThat(view.progress).isEqualTo(0f)
        view.setProgress(Float.NaN, animate = false)
        assertThat(view.progress).isEqualTo(0f)
    }

    @Test
    fun `fill width follows progress`() {
        val theme = SancaiTheme.light()
        val view = bar(0.5f, theme)
        val fill = fillOf(view, theme)
        val groove = grooveOf(theme)
        var bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(close(bitmap.onGroove(view, 0.25f), fill)).isTrue()
        assertThat(close(bitmap.onGroove(view, 0.45f), fill)).isTrue()
        assertThat(close(bitmap.onGroove(view, 0.55f), groove)).isTrue()
        assertThat(close(bitmap.onGroove(view, 0.9f), groove)).isTrue()

        view.progress = 0.8f
        bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(close(bitmap.onGroove(view, 0.75f), fill)).isTrue()
        assertThat(close(bitmap.onGroove(view, 0.85f), groove)).isTrue()

        view.progress = 0f
        bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(close(bitmap.onGroove(view, 0.03f), groove)).isTrue()

        view.progress = 1f
        bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(close(bitmap.onGroove(view, 0.03f), fill)).isTrue()
        assertThat(close(bitmap.onGroove(view, 0.97f), fill)).isTrue()
    }

    @Test
    fun `fill takes the glaze in light and dark`() {
        for (theme in listOf(SancaiTheme.light(), SancaiTheme.dark())) {
            val view = bar(0.6f, theme)
            view.glaze = theme.glazes.celadon
            val bitmap = Snapshots.render(view, WIDTH_DP)
            assertThat(close(bitmap.onGroove(view, 0.3f), theme.bedColor(theme.glazes.celadon))).isTrue()
            assertThat(close(bitmap.onGroove(view, 0.3f), theme.bedColor(theme.glazes.cobalt), 4)).isFalse()
            assertThat(close(bitmap.onGroove(view, 0.8f), grooveOf(theme))).isTrue()
        }
    }

    @Test
    fun `fill grows from the right in right-to-left layouts`() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val theme = SancaiTheme.light()
        val view = SancaiProgressBar(Snapshots.context(theme)).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            progress = 0.3f
        }
        val bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(close(bitmap.onGroove(view, 0.85f), fillOf(view, theme))).isTrue()
        assertThat(close(bitmap.onGroove(view, 0.15f), grooveOf(theme))).isTrue()
    }

    @Test
    fun `setProgress glides when attached and jumps otherwise`() {
        val theme = SancaiTheme.light()
        val loose = bar(0f, theme)
        loose.setProgress(0.8f)
        assertThat(close(Snapshots.render(loose, WIDTH_DP).onGroove(loose, 0.7f), fillOf(loose, theme))).isTrue()

        val view = SancaiProgressBar(Snapshots.context(theme))
        Snapshots.attach(view, WIDTH_DP)
        view.setProgress(0.8f)
        assertThat(view.progress).isEqualTo(0.8f)
        // Just started: the fill has not reached 70 % yet.
        assertThat(close(draw(view).onGroove(view, 0.7f), grooveOf(theme))).isTrue()
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        assertThat(close(draw(view).onGroove(view, 0.7f), fillOf(view, theme))).isTrue()
        assertThat(close(draw(view).onGroove(view, 0.9f), grooveOf(theme))).isTrue()
    }

    @Test
    fun `indeterminate segment covers 30 percent and travels while attached`() {
        val theme = SancaiTheme.light()
        val view = SancaiProgressBar(Snapshots.context(theme)).apply { isIndeterminate = true }
        // At rest (not attached) the segment sits in the middle of the groove.
        var bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(close(bitmap.onGroove(view, 0.5f), fillOf(view, theme))).isTrue()
        assertThat(close(bitmap.onGroove(view, 0.38f), fillOf(view, theme))).isTrue()
        assertThat(close(bitmap.onGroove(view, 0.62f), fillOf(view, theme))).isTrue()
        assertThat(close(bitmap.onGroove(view, 0.3f), grooveOf(theme))).isTrue()
        assertThat(close(bitmap.onGroove(view, 0.7f), grooveOf(theme))).isTrue()

        Snapshots.attach(view, WIDTH_DP)
        val seen = HashSet<Int>()
        repeat(14) {
            ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
            bitmap = draw(view)
            seen += (1..19).firstOrNull { close(bitmap.onGroove(view, it / 20f), fillOf(view, theme)) } ?: -1
        }
        // The segment was seen at several different places during one pass.
        assertThat(seen.size).isAtLeast(4)

        // Hidden or detached: nothing keeps invalidating the view.
        view.visibility = View.GONE
        ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
        view.visibility = View.VISIBLE
        (view.parent as android.view.ViewGroup).removeView(view)
        bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(close(bitmap.onGroove(view, 0.5f), fillOf(view, theme))).isTrue()
    }

    @Test
    fun `reduced motion shows a still pale full-width fill`() {
        val theme = SancaiTheme.light().copy(reduceMotion = true)
        val view = SancaiProgressBar(Snapshots.context(theme)).apply { isIndeterminate = true }
        Snapshots.attach(view, WIDTH_DP)
        val pale = SancaiColors.blend(grooveOf(theme), fillOf(view, theme), 0.4f)
        val first = draw(view)
        ShadowLooper.idleMainLooper(700, TimeUnit.MILLISECONDS)
        val second = draw(view)
        for (fraction in listOf(0.05f, 0.5f, 0.95f)) {
            assertThat(close(first.onGroove(view, fraction), pale)).isTrue()
            assertThat(second.onGroove(view, fraction)).isEqualTo(first.onGroove(view, fraction))
        }
    }

    private fun draw(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(SancaiTheme.of(view.context).colors.canvas)
        view.draw(canvas)
        return bitmap
    }

    @Test
    fun `bar reports itself as a progress bar with a range`() {
        val view = bar(0.25f)
        assertThat(view.accessibilityClassName.toString()).isEqualTo("android.widget.ProgressBar")
        val info = view.createAccessibilityNodeInfo()
        assertThat(info).isNotNull()
        val range = info!!.rangeInfo
        assertThat(range).isNotNull()
        assertThat(range!!.min).isEqualTo(0f)
        assertThat(range.max).isEqualTo(1f)
        assertThat(range.current).isEqualTo(0.25f)
        assertThat(androidx.core.view.ViewCompat.getStateDescription(view).toString()).contains("25")

        view.isIndeterminate = true
        assertThat(view.createAccessibilityNodeInfo()!!.rangeInfo).isNull()
        assertThat(view.progress).isEqualTo(0.25f)
    }

    @Test
    fun `disabled bar pales but keeps its shape`() {
        val theme = SancaiTheme.light()
        val view = bar(0.5f, theme)
        view.isEnabled = false
        val bitmap = Snapshots.render(view, WIDTH_DP)
        val expected = SancaiColors.blend(theme.colors.canvas, fillOf(view, theme), 107f / 255f)
        assertThat(close(bitmap.onGroove(view, 0.25f), expected)).isTrue()
    }

    @Test
    fun `xml attributes set glaze, progress, indeterminate and enabled`() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiGlaze, "celadon")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiProgressValue, "0.4")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiProgressIndeterminate, "true")
            .addAttribute(android.R.attr.enabled, "false")
            .build()
        val view = SancaiProgressBar(Snapshots.context(), attrs)
        assertThat(view.glaze).isEqualTo(SancaiTheme.light().glazes.celadon)
        assertThat(view.progress).isEqualTo(0.4f)
        assertThat(view.isIndeterminate).isTrue()
        assertThat(view.isEnabled).isFalse()
        val plain = SancaiProgressBar(Snapshots.context(), org.robolectric.Robolectric.buildAttributeSet().build())
        assertThat(plain.glaze).isEqualTo(SancaiTheme.light().glazes.cobalt)
        assertThat(plain.progress).isEqualTo(0f)
        assertThat(plain.isIndeterminate).isFalse()
    }

    @Test
    fun `groove respects padding, stays 10 dp in a taller view and survives a squeeze`() {
        val theme = SancaiTheme.light()
        val view = SancaiProgressBar(Snapshots.context(theme)).apply { progress = 1f }
        val density = view.resources.displayMetrics.density
        val pad = (20 * density).toInt()
        view.setPadding(pad, 0, pad, 0)
        var bitmap = Snapshots.render(view, WIDTH_DP)
        val fill = fillOf(view, theme)
        val y = (9 * density).toInt()
        assertThat(bitmap.getPixel(pad / 2, y)).isEqualTo(theme.colors.canvas)
        assertThat(bitmap.getPixel(bitmap.width - pad / 2, y)).isEqualTo(theme.colors.canvas)
        assertThat(close(bitmap.getPixel(pad + (8 * density).toInt(), y), fill)).isTrue()

        // 62 dp tall: the 22 dp band is centred, so the groove runs from 24 to 34 dp.
        view.setPadding(0, 0, 0, 0)
        bitmap = Snapshots.render(view, WIDTH_DP, 62)
        val x = bitmap.width / 2
        assertThat(close(bitmap.getPixel(x, (29 * density).toInt()), fill)).isTrue()
        assertThat(close(bitmap.getPixel(x, (20 * density).toInt()), fill, 30)).isFalse()
        assertThat(close(bitmap.getPixel(x, (38 * density).toInt()), fill, 30)).isFalse()

        // 12 dp tall: the groove gives up its top gutter and still fits.
        bitmap = Snapshots.render(view, WIDTH_DP, 12)
        assertThat(close(bitmap.getPixel(x, (5 * density).toInt()), fill)).isTrue()

        // No room at all: nothing is drawn and nothing crashes.
        bitmap = Snapshots.render(view, 8)
        assertThat(bitmap.getPixel(bitmap.width / 2, y)).isEqualTo(theme.colors.canvas)
    }

    @Test
    fun `text scale does not change the bar or the spinner`() {
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = 1.13f))
        val bar = SancaiProgressBar(large).apply { progress = 0.5f }
        assertThat(Snapshots.render(bar, WIDTH_DP).sameAs(Snapshots.render(bar(0.5f), WIDTH_DP))).isTrue()
        val spinner = SancaiSpinner(large)
        Snapshots.layout(spinner, 100)
        assertThat(spinner.height).isEqualTo(Math.round(28 * spinner.resources.displayMetrics.density))
    }

    @Test
    fun `reduced motion and hidden bars jump, and a detached bar finishes its glide`() {
        val theme = SancaiTheme.light()
        val reduced = SancaiProgressBar(Snapshots.context(theme.copy(reduceMotion = true)))
        Snapshots.attach(reduced, WIDTH_DP)
        reduced.setProgress(0.8f)
        assertThat(close(draw(reduced).onGroove(reduced, 0.7f), fillOf(reduced, theme))).isTrue()

        val hidden = SancaiProgressBar(Snapshots.context(theme))
        Snapshots.attach(hidden, WIDTH_DP)
        hidden.visibility = View.INVISIBLE
        hidden.setProgress(0.8f)
        hidden.visibility = View.VISIBLE
        assertThat(close(draw(hidden).onGroove(hidden, 0.7f), fillOf(hidden, theme))).isTrue()

        val view = SancaiProgressBar(Snapshots.context(theme))
        Snapshots.attach(view, WIDTH_DP)
        view.setProgress(0.8f)
        ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
        (view.parent as android.view.ViewGroup).removeView(view)
        val bitmap = Snapshots.render(view, WIDTH_DP)
        assertThat(close(bitmap.onGroove(view, 0.7f), fillOf(view, theme))).isTrue()
        // The cancelled animator does not come back to life.
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        assertThat(view.progress).isEqualTo(0.8f)
    }

    @Test
    fun `indeterminate segment enters at the start edge in both directions`() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val theme = SancaiTheme.light()
        for (rtl in listOf(false, true)) {
            val view = SancaiProgressBar(Snapshots.context(theme)).apply {
                layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                isIndeterminate = true
            }
            Snapshots.attach(view, WIDTH_DP)
            var first: Bitmap? = null
            for (step in 0 until 28) {
                val frame = draw(view)
                if ((1..19).any { close(frame.onGroove(view, it / 20f), fillOf(view, theme)) }) {
                    first = frame
                    break
                }
                ShadowLooper.idleMainLooper(50, TimeUnit.MILLISECONDS)
            }
            assertThat(first).isNotNull()
            val startProbe = if (rtl) 0.95f else 0.05f
            val endProbe = if (rtl) 0.05f else 0.95f
            assertThat(close(first!!.onGroove(view, startProbe), fillOf(view, theme))).isTrue()
            assertThat(close(first.onGroove(view, endProbe), grooveOf(theme))).isTrue()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Spinner
    // ---------------------------------------------------------------------------------------

    @Test
    fun `spinner reads xml attributes and keeps an xml description`() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiTone, "accent")
            .addAttribute(android.R.attr.contentDescription, "Syncing")
            .build()
        val view = SancaiSpinner(Snapshots.context(), attrs)
        assertThat(view.tone).isEqualTo(SancaiTextTone.ACCENT)
        assertThat(view.contentDescription.toString()).isEqualTo("Syncing")
        val red = SancaiSpinner(
            Snapshots.context(),
            org.robolectric.Robolectric.buildAttributeSet()
                .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiProgressSpinnerColor, "#ff0000")
                .addAttribute(android.R.attr.enabled, "false")
                .build(),
        )
        assertThat(red.color).isEqualTo(Color.RED)
        assertThat(red.isEnabled).isFalse()
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "zh")
    fun `loading description is translated`() {
        val spinner = SancaiSpinner(Snapshots.context())
        assertThat(spinner.contentDescription.toString()).isEqualTo("正在加载")
        val bar = SancaiProgressBar(Snapshots.context()).apply { isIndeterminate = true }
        assertThat(androidx.core.view.ViewCompat.getStateDescription(bar).toString()).isEqualTo("正在加载")
    }

    /** Counts the pixels of [bitmap] that are closer to [ink] than to the canvas. */
    private fun inked(bitmap: Bitmap, ink: Int, left: Int, top: Int, right: Int, bottom: Int): Int {
        var count = 0
        for (y in top until bottom) for (x in left until right) {
            if (close(bitmap.getPixel(x, y), ink, 40)) count++
        }
        return count
    }

    @Test
    fun `spinner draws an open ring in its tone`() {
        val theme = SancaiTheme.light()
        val view = SancaiSpinner(Snapshots.context(theme))
        val bitmap = Snapshots.render(view, 28, 28)
        val size = bitmap.width
        val density = view.resources.displayMetrics.density
        assertThat(size).isEqualTo(Math.round(28 * density))
        val ink = theme.colors.muted
        // Ring radius 10 dp around the centre: left, right and bottom are inked ...
        val centre = size / 2
        val reach = (10 * density).toInt()
        assertThat(close(bitmap.getPixel(centre - reach, centre), ink, 40)).isTrue()
        assertThat(close(bitmap.getPixel(centre + reach, centre), ink, 40)).isTrue()
        assertThat(close(bitmap.getPixel(centre, centre + reach), ink, 40)).isTrue()
        // ... the opening at the top and the middle are not.
        assertThat(bitmap.getPixel(centre, centre - reach)).isEqualTo(theme.colors.canvas)
        assertThat(bitmap.getPixel(centre, centre)).isEqualTo(theme.colors.canvas)
        // 276 of 360 degrees of a 2 dp stroke on a 20 dp ring.
        val expected = Math.PI * 20 * density * 2 * density * 276 / 360
        val painted = inked(bitmap, ink, 0, 0, size, size)
        assertThat(painted.toDouble()).isWithin(expected * 0.2).of(expected)
        // Nothing touches the edge of the view.
        assertThat(inked(bitmap, ink, 0, 0, size, 2) + inked(bitmap, ink, 0, 0, 2, size)).isEqualTo(0)
    }

    @Test
    fun `spinner turns only while attached and visible`() {
        val view = SancaiSpinner(Snapshots.context())
        assertThat(view.isSpinning).isFalse()
        Snapshots.attach(view, 28)
        assertThat(view.isSpinning).isTrue()
        val before = draw(view)
        ShadowLooper.idleMainLooper(225, TimeUnit.MILLISECONDS)
        assertThat(draw(view).sameAs(before)).isFalse()

        view.visibility = View.INVISIBLE
        assertThat(view.isSpinning).isFalse()
        view.visibility = View.VISIBLE
        assertThat(view.isSpinning).isTrue()
        (view.parent as android.view.ViewGroup).removeView(view)
        assertThat(view.isSpinning).isFalse()
    }

    @Test
    fun `spinner keeps turning at half speed with reduced motion`() {
        val centre = { v: View -> v.width / 2 }
        val reach = { v: View -> (10 * v.resources.displayMetrics.density).toInt() }
        // After a quarter of the normal turn time the opening has moved from the top to the
        // right at full speed, and only half as far with reduced motion.
        val full = SancaiSpinner(Snapshots.context())
        Snapshots.attach(full, 28)
        ShadowLooper.idleMainLooper(225, TimeUnit.MILLISECONDS)
        val canvas = SancaiTheme.light().colors.canvas
        assertThat(draw(full).getPixel(centre(full) + reach(full), centre(full))).isEqualTo(canvas)

        val reduced = SancaiSpinner(Snapshots.context(SancaiTheme.light().copy(reduceMotion = true)))
        Snapshots.attach(reduced, 28)
        assertThat(reduced.isSpinning).isTrue()
        ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS)
        assertThat(draw(reduced).getPixel(centre(reduced) + reach(reduced), centre(reduced))).isNotEqualTo(canvas)
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        assertThat(draw(reduced).getPixel(centre(reduced) + reach(reduced), centre(reduced))).isEqualTo(canvas)
    }

    @Test
    fun `spinner colour, tone and description`() {
        val theme = SancaiTheme.dark()
        val view = SancaiSpinner(Snapshots.context(theme))
        assertThat(view.tone).isEqualTo(SancaiTextTone.MUTED)
        assertThat(view.color).isEqualTo(theme.colors.muted)
        view.color = Color.RED
        assertThat(view.color).isEqualTo(Color.RED)
        val bitmap = Snapshots.render(view, 28, 28)
        assertThat(inked(bitmap, Color.RED, 0, 0, bitmap.width, bitmap.height)).isGreaterThan(50)
        view.tone = SancaiTextTone.ACCENT
        assertThat(view.color).isEqualTo(theme.colors.accent)

        assertThat(view.accessibilityClassName.toString()).isEqualTo("android.widget.ProgressBar")
        assertThat(view.contentDescription.toString()).isNotEmpty()
        assertThat(view.isFocusable).isFalse()
        view.contentDescription = "Syncing"
        assertThat(view.contentDescription.toString()).isEqualTo("Syncing")
    }

    @Test
    fun `spinner ring scales with the view`() {
        val view = SancaiSpinner(Snapshots.context())
        val bitmap = Snapshots.render(view, 56, 56)
        val centre = bitmap.width / 2
        val reach = (20 * view.resources.displayMetrics.density).toInt()
        assertThat(close(bitmap.getPixel(centre - reach, centre), SancaiTheme.light().colors.muted, 40)).isTrue()
    }

    // ---------------------------------------------------------------------------------------
    // Snapshot
    // ---------------------------------------------------------------------------------------

    @Test
    fun `snapshot of progress and spinners in light and dark`() {
        for ((name, theme) in listOf("light" to SancaiTheme.light(), "dark" to SancaiTheme.dark())) {
            val context = Snapshots.context(theme)
            val density = context.resources.displayMetrics.density
            val match = LinearLayout.LayoutParams.MATCH_PARENT
            val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val pad = (16 * density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            fun row(view: View) = column.addView(
                view,
                LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = (10 * density).toInt() },
            )
            val glazes = theme.glazes
            row(SancaiProgressBar(context))
            row(SancaiProgressBar(context).apply { progress = 0.02f })
            row(SancaiProgressBar(context).apply { progress = 0.35f; glaze = glazes.terracotta })
            row(SancaiProgressBar(context).apply { progress = 0.5f; glaze = glazes.ochre })
            row(SancaiProgressBar(context).apply { progress = 0.72f })
            row(SancaiProgressBar(context).apply { progress = 1f; glaze = glazes.celadon })
            row(SancaiProgressBar(context).apply { isIndeterminate = true })
            row(SancaiProgressBar(context).apply { progress = 0.6f; isEnabled = false })
            row(
                SancaiProgressBar(SancaiTheme.wrap(context, theme.copy(reduceMotion = true))).apply {
                    isIndeterminate = true
                },
            )

            val spinners = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            fun spinner(sizeDp: Int, configure: SancaiSpinner.() -> Unit = {}) = spinners.addView(
                SancaiSpinner(context).apply(configure),
                LinearLayout.LayoutParams((sizeDp * density).toInt(), (sizeDp * density).toInt()).apply {
                    marginStart = (10 * density).toInt()
                    marginEnd = (10 * density).toInt()
                },
            )
            spinner(20)
            spinner(28)
            spinner(28) { tone = SancaiTextTone.INK }
            spinner(28) { tone = SancaiTextTone.ACCENT }
            spinner(28) { isEnabled = false }
            spinner(44) { tone = SancaiTextTone.ACCENT }
            row(spinners)

            val bitmap = Snapshots.capture(column, "progress_$name", 360)
            assertThat(bitmap.height).isGreaterThan(0)
        }
    }

    companion object {
        const val WIDTH_DP = 300
    }
}
