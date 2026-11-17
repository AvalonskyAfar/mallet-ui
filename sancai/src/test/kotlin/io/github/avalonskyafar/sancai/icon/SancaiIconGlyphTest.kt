package io.github.avalonskyafar.sancai.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiIconGlyphTest {

    /** Renders [icon] at 10 px per grid unit with a 2-unit stroke on a transparent bitmap. */
    private fun render(icon: SancaiIcon, mirrored: Boolean = false, color: Int = Color.BLACK): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        SancaiIconPainter.draw(Canvas(bitmap), icon, 0f, 0f, SIZE.toFloat(), color, 2f * UNIT, mirrored)
        return bitmap
    }

    private fun pixels(bitmap: Bitmap): IntArray {
        val out = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(out, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return out
    }

    /** Ink bounds in grid units: left, top, right, bottom; null when nothing was drawn. */
    private fun inkBounds(bitmap: Bitmap): FloatArray? {
        var l = SIZE
        var t = SIZE
        var r = -1
        var b = -1
        val all = pixels(bitmap)
        for (i in all.indices) {
            if (Color.alpha(all[i]) < 128) continue
            val x = i % SIZE
            val y = i / SIZE
            if (x < l) l = x
            if (x > r) r = x
            if (y < t) t = y
            if (y > b) b = y
        }
        if (r < 0) return null
        return floatArrayOf(l / UNIT, t / UNIT, (r + 1) / UNIT, (b + 1) / UNIT)
    }

    private fun flipped(bitmap: Bitmap): IntArray {
        val all = pixels(bitmap)
        val out = IntArray(all.size)
        for (i in all.indices) out[i - i % SIZE + (SIZE - 1 - i % SIZE)] = all[i]
        return out
    }

    @Test
    fun `every icon draws ink that is not clipped by the grid`() {
        for (icon in SancaiIcon.values()) {
            val bounds = inkBounds(render(icon))
            assertWithMessage("$icon draws something").that(bounds).isNotNull()
            assertWithMessage("$icon left").that(bounds!![0]).isAtLeast(0.4f)
            assertWithMessage("$icon top").that(bounds[1]).isAtLeast(0.4f)
            assertWithMessage("$icon right").that(bounds[2]).isAtMost(23.6f)
            assertWithMessage("$icon bottom").that(bounds[3]).isAtMost(23.6f)
        }
    }

    @Test
    fun `the later glyphs keep their ink inside two to twenty two`() {
        for (icon in LATER) {
            val bounds = inkBounds(render(icon))!!
            assertWithMessage("$icon left").that(bounds[0]).isAtLeast(1.9f)
            assertWithMessage("$icon top").that(bounds[1]).isAtLeast(1.9f)
            assertWithMessage("$icon right").that(bounds[2]).isAtMost(22.1f)
            assertWithMessage("$icon bottom").that(bounds[3]).isAtMost(22.1f)
        }
    }

    @Test
    fun `the later glyphs are optically centred and of comparable size`() {
        for (icon in LATER) {
            val bounds = inkBounds(render(icon))!!
            assertWithMessage("$icon centre x").that((bounds[0] + bounds[2]) / 2f).isWithin(1f).of(12f)
            assertWithMessage("$icon centre y").that((bounds[1] + bounds[3]) / 2f).isWithin(1f).of(12f)
            val larger = maxOf(bounds[2] - bounds[0], bounds[3] - bounds[1])
            assertWithMessage("$icon size").that(larger).isAtLeast(16f)
        }
    }

    @Test
    fun `no two icons render identically`() {
        val icons = SancaiIcon.values()
        val images = icons.map { pixels(render(it)) }
        for (i in icons.indices) {
            for (j in 0 until i) {
                // At 10 px per grid unit a real difference is hundreds of pixels.
                assertWithMessage("${icons[i]} looks like ${icons[j]}")
                    .that(inkDifference(images[i], images[j])).isGreaterThan(300)
            }
        }
    }

    @Test
    fun `no placeholder square is left`() {
        // The old fallback was a 12 x 12 rounded square outline around the centre.
        val placeholder = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * UNIT
            color = Color.BLACK
        }
        Canvas(placeholder).drawRoundRect(60f, 60f, 180f, 180f, 30f, 30f, paint)
        val expected = pixels(placeholder)
        for (icon in SancaiIcon.values()) {
            val actual = pixels(render(icon))
            var differing = 0
            for (i in actual.indices) {
                if ((Color.alpha(actual[i]) >= 128) != (Color.alpha(expected[i]) >= 128)) differing++
            }
            assertWithMessage("$icon is not the placeholder").that(differing).isGreaterThan(400)
        }
    }

    @Test
    fun `directional icons mirror and the others do not`() {
        for (icon in SancaiIcon.values()) {
            val plain = render(icon)
            val mirrored = pixels(render(icon, mirrored = true))
            if (icon.isDirectional) {
                // Anti-aliasing may differ by a pixel along the edges of a mirrored stroke.
                assertWithMessage("$icon mirrors").that(inkDifference(mirrored, flipped(plain))).isLessThan(120)
                assertWithMessage("$icon changes").that(inkDifference(mirrored, pixels(plain))).isGreaterThan(400)
            } else {
                assertWithMessage("$icon stays").that(mirrored.contentEquals(pixels(plain))).isTrue()
            }
        }
    }

    /** Number of pixels that are ink in one image and not in the other. */
    private fun inkDifference(a: IntArray, b: IntArray): Int {
        var differing = 0
        for (i in a.indices) if ((Color.alpha(a[i]) >= 128) != (Color.alpha(b[i]) >= 128)) differing++
        return differing
    }

    @Test
    fun `ink has exactly the requested colour, dots included`() {
        val cobalt = Color.rgb(40, 90, 200)
        // Centre of a stroke, then centre of a dot.
        assertThat(render(SancaiIcon.CLOCK, color = cobalt).getPixel(120, 30)).isEqualTo(cobalt)
        assertThat(render(SancaiIcon.MENU, color = cobalt).getPixel(120, 120)).isEqualTo(cobalt)
        assertThat(render(SancaiIcon.MORE_VERTICAL, color = cobalt).getPixel(120, 120)).isEqualTo(cobalt)
        assertThat(render(SancaiIcon.INFO, color = cobalt).getPixel(120, 78)).isEqualTo(cobalt)
        // Nothing but the ring and the mark: the inside of a status mark stays empty.
        assertThat(render(SancaiIcon.ERROR, color = cobalt).getPixel(120, 70)).isEqualTo(Color.TRANSPARENT)
    }

    @Test
    fun `stroke keeps the requested width at any size`() {
        // MENU's middle bar is horizontal: count the ink rows through its centre column.
        for (size in intArrayOf(48, 96, 240)) {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            SancaiIconPainter.draw(Canvas(bitmap), SancaiIcon.MENU, 0f, 0f, size.toFloat(), Color.BLACK, 4f)
            var rows = 0
            val from = size * 5 / 12
            val to = size * 7 / 12
            for (y in from until to) if (Color.alpha(bitmap.getPixel(size / 2, y)) >= 128) rows++
            assertWithMessage("bar thickness at $size px").that(rows).isEqualTo(4)
        }
    }

    @Test
    fun `a filled dot is left in stroke mode for the next icon`() {
        // PALETTE ends with dots; CLOCK drawn afterwards must still be an outline.
        render(SancaiIcon.PALETTE)
        assertThat(render(SancaiIcon.CLOCK).getPixel(170, 80)).isEqualTo(Color.TRANSPARENT)
    }

    @Test
    fun `a zero or negative size draws nothing`() {
        for (size in floatArrayOf(0f, -24f)) {
            val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            SancaiIconPainter.draw(Canvas(bitmap), SancaiIcon.STAR, 0f, 0f, size, Color.BLACK, 2f * UNIT)
            assertWithMessage("size $size").that(inkBounds(bitmap)).isNull()
        }
    }

    @Test
    fun `every icon stays inside the square it is given and leaves the canvas as it found it`() {
        // A 96 px icon at (72, 48) on a 240 px bitmap, drawn on an already transformed canvas.
        for (icon in SancaiIcon.values()) {
            val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(12f, 8f)
            val saves = canvas.saveCount
            SancaiIconPainter.draw(canvas, icon, 60f, 40f, 96f, Color.BLACK, 8f, mirrored = true)
            assertWithMessage("$icon save count").that(canvas.saveCount).isEqualTo(saves)
            val bounds = inkBounds(bitmap)!!
            assertWithMessage("$icon left").that(bounds[0] * UNIT).isAtLeast(72f)
            assertWithMessage("$icon top").that(bounds[1] * UNIT).isAtLeast(48f)
            assertWithMessage("$icon right").that(bounds[2] * UNIT).isAtMost(168f)
            assertWithMessage("$icon bottom").that(bounds[3] * UNIT).isAtMost(144f)
            // The caller's own transform is still in force afterwards.
            canvas.drawRect(0f, 0f, 2f, 2f, Paint().apply { color = Color.RED })
            assertWithMessage("$icon matrix").that(bitmap.getPixel(12, 8)).isEqualTo(Color.RED)
        }
    }

    @Test
    fun `a translucent colour is not made opaque, in strokes or in dots`() {
        val veil = Color.argb(128, 0, 0, 0)
        assertThat(Color.alpha(render(SancaiIcon.MENU, color = veil).getPixel(120, 120))).isEqualTo(128)
        assertThat(Color.alpha(render(SancaiIcon.MORE_VERTICAL, color = veil).getPixel(120, 120))).isEqualTo(128)
        // The next opaque icon is opaque again.
        assertThat(render(SancaiIcon.MENU).getPixel(120, 120)).isEqualTo(Color.BLACK)
    }

    @Test
    fun `every icon still draws at a 12 px size`() {
        for (icon in SancaiIcon.values()) {
            val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
            SancaiIconPainter.draw(Canvas(bitmap), icon, 0f, 0f, 12f, Color.BLACK, 1f)
            val ink = pixels(bitmap).count { Color.alpha(it) >= 128 }
            assertWithMessage("$icon ink at 12 px").that(ink).isAtLeast(3)
        }
    }

    /** A labelled contact sheet: every icon at 24 dp in five columns, then a row at 48 dp. */
    private class Sheet(context: android.content.Context) : View(context) {
        private val theme = SancaiTheme.of(context)
        private val density = resources.displayMetrics.density
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.colors.muted
            textSize = 8f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        private val large = listOf(
            SancaiIcon.SEARCH, SancaiIcon.WARNING, SancaiIcon.TRASH, SancaiIcon.REFRESH,
            SancaiIcon.EYE_OFF, SancaiIcon.BELL, SancaiIcon.HAND, SancaiIcon.PALETTE,
        )

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val rows = (SancaiIcon.values().size + COLUMNS - 1) / COLUMNS
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                ((rows * CELL_DP + 72f + 16f) * density).toInt(),
            )
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(theme.colors.canvas)
            val cellW = width.toFloat() / COLUMNS
            val cellH = CELL_DP * density
            val size = 24f * density
            val strokePx = SancaiIconPainter.STROKE_DP * density
            SancaiIcon.values().forEachIndexed { index, icon ->
                val cx = (index % COLUMNS + 0.5f) * cellW
                val top = (index / COLUMNS) * cellH + 10f * density
                SancaiIconPainter.draw(canvas, icon, cx - size / 2f, top, size, theme.colors.ink, strokePx)
                canvas.drawText(icon.name.lowercase(), cx, top + size + 12f * density, label)
            }
            val rowTop = height - 64f * density
            val step = width.toFloat() / large.size
            large.forEachIndexed { index, icon ->
                val left = (index + 0.5f) * step - size
                SancaiIconPainter.draw(canvas, icon, left, rowTop, size * 2f, theme.colors.ink, strokePx * 2f)
            }
        }

        private companion object {
            const val COLUMNS = 5
            const val CELL_DP = 54f
        }
    }

    @Test
    fun `snapshot of the glyph sheet in light and dark`() {
        val light = Snapshots.capture(Sheet(Snapshots.context(SancaiTheme.light())), "glyphs_light", 400)
        val dark = Snapshots.capture(Sheet(Snapshots.context(SancaiTheme.dark())), "glyphs_dark", 400)
        assertThat(light.getPixel(1, 1)).isEqualTo(SancaiTheme.light().colors.canvas)
        assertThat(dark.getPixel(1, 1)).isEqualTo(SancaiTheme.dark().colors.canvas)
    }

    private companion object {
        const val SIZE = 240
        const val UNIT = 10f

        /** The icons that were already drawn before the set was completed. */
        val EARLY: Set<SancaiIcon> = setOf(
            SancaiIcon.CHEVRON_LEFT, SancaiIcon.CHEVRON_RIGHT, SancaiIcon.CHEVRON_UP, SancaiIcon.CHEVRON_DOWN,
            SancaiIcon.ARROW_LEFT, SancaiIcon.ARROW_RIGHT, SancaiIcon.ARROW_UP, SancaiIcon.ARROW_DOWN,
            SancaiIcon.CHECK, SancaiIcon.CLOSE, SancaiIcon.PLUS, SancaiIcon.MINUS,
            SancaiIcon.POWER, SancaiIcon.BOOK, SancaiIcon.SLIDERS, SancaiIcon.MORE_CIRCLE,
        )

        /** The 34 glyphs drawn to the "ink inside 2..22" rule. */
        val LATER: List<SancaiIcon> = SancaiIcon.values().filter { it !in EARLY }
    }
}
