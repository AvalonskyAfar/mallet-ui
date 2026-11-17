package io.github.avalonskyafar.sancai.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiSkeletonTest {

    private val light = SancaiTheme.light()
    private val context = Snapshots.context(light)

    private fun px(dp: Float): Int = (dp * context.resources.displayMetrics.density).roundToInt()

    /** Draws an already laid out view as it is, on the canvas colour of [theme]. */
    private fun draw(view: View, theme: SancaiTheme = light): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(theme.colors.canvas)
        view.draw(canvas)
        return bitmap
    }

    /** Moves the shared clock to [phaseMs] inside a period of [periodMs]. */
    private fun moveClockTo(phaseMs: Long, periodMs: Long = CYCLE_MS) {
        val now = AnimationUtils.currentAnimationTimeMillis()
        val delta = ((phaseMs - now % periodMs) + periodMs) % periodMs
        if (delta > 0) ShadowLooper.idleMainLooper(delta, TimeUnit.MILLISECONDS)
        assertThat(AnimationUtils.currentAnimationTimeMillis() % periodMs).isEqualTo(phaseMs)
    }

    private fun close(first: Int, second: Int, tolerance: Int = 3): Boolean =
        abs(Color.red(first) - Color.red(second)) <= tolerance &&
            abs(Color.green(first) - Color.green(second)) <= tolerance &&
            abs(Color.blue(first) - Color.blue(second)) <= tolerance

    private fun xInRoot(view: View): Int {
        var x = view.left
        var parent = view.parent
        while (parent is View && parent !== view.rootView) {
            x += parent.left - parent.scrollX
            parent = parent.parent
        }
        return x
    }

    /** Clock phase at which the middle of the band is at [x] in the root of [view]. */
    private fun phaseForBandCentre(view: View, x: Float, rtl: Boolean = false): Long {
        val sweep = view.rootView.width.toFloat()
        val band = sweep * 0.4f
        val centre = if (rtl) sweep - x else x
        return ((centre + band / 2f) * TRAVEL_MS / (sweep + band)).roundToInt().toLong()
    }

    @Test
    fun `fill is six percent ink on the canvas in light and eight percent on the card in dark`() {
        val view = SancaiSkeleton(context)
        assertThat(view.fillColor).isEqualTo(SancaiColors.blend(light.colors.canvas, light.colors.ink, 0.06f))
        assertThat(close(view.fillColor, Color.rgb(241, 241, 242), 1)).isTrue()
        assertThat(view.bandColor).isEqualTo(SancaiColors.blend(view.fillColor, light.colors.raised, 0.6f))

        val dark = SancaiTheme.dark()
        val night = SancaiSkeleton(Snapshots.context(dark))
        assertThat(night.fillColor).isEqualTo(SancaiColors.blend(dark.colors.surface, dark.colors.ink, 0.08f))
        // Raised is darker than the block in a dark theme, so the band takes a touch of ink instead.
        assertThat(night.bandColor).isEqualTo(SancaiColors.blend(night.fillColor, dark.colors.ink, 0.07f))
        assertThat(Color.green(night.bandColor)).isGreaterThan(Color.green(night.fillColor))
    }

    /** Views only mirror when the application declares android:supportsRtl. */
    private fun enableRtl() {
        val info = context.applicationContext.applicationInfo
        info.flags = info.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
    }

    @Test
    fun `draws a still block with six dp corners while it is not attached`() {
        val view = SancaiSkeleton(context)
        val bitmap = Snapshots.render(view, 200, 40)
        assertThat(view.isRunning).isFalse()
        assertThat(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)).isEqualTo(view.fillColor)
        assertThat(bitmap.getPixel(px(20f), px(3f))).isEqualTo(view.fillColor)
        // The very corner is cut away; 6 dp along the edge the body has begun.
        assertThat(bitmap.getPixel(0, 0)).isEqualTo(light.colors.canvas)
        assertThat(bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)).isEqualTo(light.colors.canvas)
        assertThat(bitmap.getPixel(px(3f), px(3f))).isEqualTo(view.fillColor)
        assertThat(bitmap.getPixel(px(6f), 1)).isEqualTo(view.fillColor)
    }

    @Test
    fun `two skeletons side by side show one continuous band`() {
        val row = LinearLayout(context)
        val first = SancaiSkeleton(context)
        val second = SancaiSkeleton(context)
        row.addView(first, LinearLayout.LayoutParams(0, px(40f), 1f))
        row.addView(second, LinearLayout.LayoutParams(0, px(40f), 1f).apply { marginStart = px(8f) })
        Snapshots.attach(row, 360)
        assertThat(first.isRunning).isTrue()
        assertThat(second.isRunning).isTrue()

        // Put the middle of the band into the gap between the two blocks.
        val seam = xInRoot(second) - px(4f)
        moveClockTo(phaseForBandCentre(first, seam.toFloat()))
        val bitmap = draw(row)
        val y = row.height / 2
        val nearFirst = bitmap.getPixel(first.right - 2, y)
        val nearSecond = bitmap.getPixel(second.left + 1, y)
        // Both blocks are lit almost fully on the sides that face each other ...
        assertThat(close(nearFirst, first.bandColor, 2)).isTrue()
        assertThat(close(nearSecond, first.bandColor, 2)).isTrue()
        // ... and fall off symmetrically away from the seam: one band, not two.
        val band = first.rootView.width * 0.4f
        val quarter = (band / 4f).roundToInt()
        val leftQuarter = bitmap.getPixel(seam - xInRoot(row) - quarter, y)
        val rightQuarter = bitmap.getPixel(seam - xInRoot(row) + quarter, y)
        val half = SancaiColors.blend(first.fillColor, first.bandColor, 0.5f)
        assertThat(close(leftQuarter, half)).isTrue()
        assertThat(close(rightQuarter, half)).isTrue()
        // Outside the band the blocks rest.
        assertThat(bitmap.getPixel(px(8f), y)).isEqualTo(first.fillColor)
        assertThat(bitmap.getPixel(row.width - px(8f), y)).isEqualTo(first.fillColor)
    }

    @Test
    fun `band travels from start to end and then pauses`() {
        val view = SancaiSkeleton(context)
        Snapshots.attach(view, 360)
        val y = view.height / 2
        val startX = px(60f)
        val endX = px(300f)
        moveClockTo(phaseForBandCentre(view, startX.toFloat()))
        var bitmap = draw(view)
        assertThat(close(bitmap.getPixel(startX, y), view.bandColor, 2)).isTrue()
        assertThat(bitmap.getPixel(endX, y)).isEqualTo(view.fillColor)

        moveClockTo(phaseForBandCentre(view, endX.toFloat()))
        bitmap = draw(view)
        assertThat(close(bitmap.getPixel(endX, y), view.bandColor, 2)).isTrue()
        assertThat(bitmap.getPixel(startX, y)).isEqualTo(view.fillColor)

        // 1400 ms of travel, then 300 ms in which the whole block rests.
        moveClockTo(1500)
        bitmap = draw(view)
        for (x in listOf(2 * px(6f), startX, view.width / 2, endX, view.width - 2 * px(6f))) {
            assertThat(bitmap.getPixel(x, y)).isEqualTo(view.fillColor)
        }
    }

    @Test
    fun `right to left layouts sweep the other way`() {
        enableRtl()
        val view = SancaiSkeleton(context)
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.attach(view, 360)
        val y = view.height / 2
        val early = phaseForBandCentre(view, px(300f).toFloat(), rtl = true)
        val late = phaseForBandCentre(view, px(60f).toFloat(), rtl = true)
        assertThat(early).isLessThan(late)
        moveClockTo(early)
        var bitmap = draw(view)
        assertThat(close(bitmap.getPixel(px(300f), y), view.bandColor, 2)).isTrue()
        assertThat(bitmap.getPixel(px(60f), y)).isEqualTo(view.fillColor)
        moveClockTo(late)
        bitmap = draw(view)
        assertThat(close(bitmap.getPixel(px(60f), y), view.bandColor, 2)).isTrue()
        assertThat(bitmap.getPixel(px(300f), y)).isEqualTo(view.fillColor)
    }

    @Test
    fun `reduced motion draws no band and breathes instead`() {
        val calm = light.copy(reduceMotion = true)
        val view = SancaiSkeleton(Snapshots.context(calm))
        Snapshots.attach(view, 360)
        assertThat(view.isRunning).isTrue()
        val y = view.height / 2
        val probes = listOf(px(20f), px(100f), px(180f), px(260f), px(340f))

        // Full opacity at the start of a breath, at any place the band would have been.
        moveClockTo(0, BREATH_MS)
        var bitmap = draw(view, calm)
        for (x in probes) assertThat(bitmap.getPixel(x, y)).isEqualTo(view.fillColor)

        // Half-way the block is at 70 %, evenly: no band anywhere.
        moveClockTo(800, BREATH_MS)
        bitmap = draw(view, calm)
        val pale = SancaiColors.blend(calm.colors.canvas, view.fillColor, 0.7f)
        for (x in probes) assertThat(close(bitmap.getPixel(x, y), pale, 1)).isTrue()
        assertThat(probes.map { bitmap.getPixel(it, y) }.toSet()).hasSize(1)

        moveClockTo(400, BREATH_MS)
        bitmap = draw(view, calm)
        val between = SancaiColors.blend(calm.colors.canvas, view.fillColor, 0.85f)
        assertThat(close(bitmap.getPixel(probes[2], y), between, 1)).isTrue()
    }

    @Test
    fun `runs only while attached visible enabled and animated`() {
        val view = SancaiSkeleton(context)
        assertThat(view.isRunning).isFalse()
        Snapshots.attach(view, 360)
        assertThat(view.isRunning).isTrue()

        view.visibility = View.INVISIBLE
        assertThat(view.isRunning).isFalse()
        view.visibility = View.VISIBLE
        assertThat(view.isRunning).isTrue()

        (view.parent as View).visibility = View.GONE
        assertThat(view.isRunning).isFalse()
        (view.parent as View).visibility = View.VISIBLE
        assertThat(view.isRunning).isTrue()

        view.isAnimated = false
        assertThat(view.isRunning).isFalse()
        view.isAnimated = true
        view.isEnabled = false
        assertThat(view.isRunning).isFalse()
        view.isEnabled = true
        assertThat(view.isRunning).isTrue()

        (view.parent as ViewGroup).removeView(view)
        assertThat(view.isRunning).isFalse()
    }

    @Test
    fun `stops drawing the band when detached and pales when disabled`() {
        val view = SancaiSkeleton(context)
        Snapshots.attach(view, 360)
        val x = px(180f)
        val y = view.height / 2
        moveClockTo(phaseForBandCentre(view, x.toFloat()))
        assertThat(close(draw(view).getPixel(x, y), view.bandColor, 2)).isTrue()

        // Disabled: still, and at the alpha every disabled control has.
        view.isEnabled = false
        val pale = SancaiColors.blend(light.colors.canvas, view.fillColor, SancaiControl.DISABLED_ALPHA / 255f)
        assertThat(close(draw(view).getPixel(x, y), pale, 1)).isTrue()
        view.isEnabled = true

        (view.parent as ViewGroup).removeView(view)
        assertThat(draw(view).getPixel(x, y)).isEqualTo(view.fillColor)
        // Nothing is left behind that would bring it back to life.
        ShadowLooper.idleMainLooper(3400, TimeUnit.MILLISECONDS)
        assertThat(view.isRunning).isFalse()
        assertThat(draw(view).getPixel(x, y)).isEqualTo(view.fillColor)
    }

    @Test
    fun `is passive and hidden from accessibility`() {
        val view = SancaiSkeleton(context)
        assertThat(view.isClickable).isFalse()
        assertThat(view.isFocusable).isFalse()
        assertThat(view.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        val column = SancaiSkeleton.text(context, 2)
        assertThat(column.importantForAccessibility)
            .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
    }

    @Test
    fun `text builds lines twelve dp high eight apart with a shorter last line`() {
        val column = SancaiSkeleton.text(context, lines = 3, lastLineFraction = 0.5f)
        Snapshots.layout(column, 200)
        assertThat(column.childCount).isEqualTo(3)
        val lines = (0 until 3).map { column.getChildAt(it) as SancaiSkeleton }
        for (line in lines) {
            assertThat(line.height).isEqualTo(px(12f))
            assertThat(line.shape).isEqualTo(SancaiSkeleton.Shape.CAPSULE)
        }
        assertThat(lines[0].width).isEqualTo(px(200f))
        assertThat(lines[1].width).isEqualTo(px(200f))
        assertThat(lines[2].width).isEqualTo(px(100f))
        assertThat(lines[1].top - lines[0].bottom).isEqualTo(px(8f))
        assertThat(lines[2].top - lines[1].bottom).isEqualTo(px(8f))
        assertThat(column.height).isEqualTo(3 * px(12f) + 2 * px(8f))
        assertThat(lines[2].left).isEqualTo(0)

        // The short line hugs the start edge, which is the right one in a right-to-left layout.
        enableRtl()
        val mirrored = SancaiSkeleton.text(context, 2, 0.5f)
        mirrored.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(mirrored, 200)
        assertThat(mirrored.getChildAt(1).right).isEqualTo(px(200f))
        assertThat(mirrored.getChildAt(1).left).isEqualTo(px(200f) - px(100f))

        assertThat(SancaiSkeleton.text(context, 0).childCount).isEqualTo(1)
        assertThat(SancaiSkeleton.text(context).childCount).isEqualTo(3)
    }

    @Test
    fun `reads its XML attributes`() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiSkeletonShape, "circle")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiSkeletonAnimated, "false")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiSkeletonWidthFraction, "0.4")
            .addAttribute(io.github.avalonskyafar.sancai.R.attr.sancaiCornerRadius, "10dp")
            .addAttribute(android.R.attr.enabled, "false")
            .build()
        val view = SancaiSkeleton(context, attrs)
        assertThat(view.shape).isEqualTo(SancaiSkeleton.Shape.CIRCLE)
        assertThat(view.isAnimated).isFalse()
        assertThat(view.widthFraction).isEqualTo(0.4f)
        assertThat(view.cornerRadiusDp).isWithin(0.01f).of(10f)
        assertThat(view.isEnabled).isFalse()

        val plain = SancaiSkeleton(context, org.robolectric.Robolectric.buildAttributeSet().build())
        assertThat(plain.shape).isEqualTo(SancaiSkeleton.Shape.BLOCK)
        assertThat(plain.isAnimated).isTrue()
        assertThat(plain.widthFraction).isEqualTo(1f)
        assertThat(plain.cornerRadiusDp).isEqualTo(6f)
        assertThat(plain.isEnabled).isTrue()
    }

    @Test
    fun `a still skeleton stays at rest while attached`() {
        val view = SancaiSkeleton(context).apply { isAnimated = false }
        Snapshots.attach(view, 360)
        val x = px(180f)
        moveClockTo(phaseForBandCentre(view, x.toFloat()))
        assertThat(draw(view).getPixel(x, view.height / 2)).isEqualTo(view.fillColor)
    }

    // ---------------------------------------------------------------------------------------
    // Snapshots
    // ---------------------------------------------------------------------------------------

    private fun wrap() = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun match() = ViewGroup.LayoutParams.MATCH_PARENT

    /** A list row that is loading: an avatar, a title line and a shorter detail line. */
    private fun personRow(themed: android.content.Context, titleShare: Float, detailShare: Float): View {
        val row = LinearLayout(themed)
        row.gravity = Gravity.CENTER_VERTICAL
        val avatar = SancaiSkeleton(themed).apply { shape = SancaiSkeleton.Shape.CIRCLE }
        row.addView(avatar, LinearLayout.LayoutParams(wrap(), wrap()))
        val column = LinearLayout(themed)
        column.orientation = LinearLayout.VERTICAL
        val title = SancaiSkeleton(themed).apply {
            shape = SancaiSkeleton.Shape.CAPSULE
            widthFraction = titleShare
        }
        val detail = SancaiSkeleton(themed).apply {
            shape = SancaiSkeleton.Shape.CAPSULE
            widthFraction = detailShare
        }
        column.addView(title, LinearLayout.LayoutParams(wrap(), px(14f)))
        column.addView(detail, LinearLayout.LayoutParams(wrap(), px(10f)).apply { topMargin = px(8f) })
        row.addView(column, LinearLayout.LayoutParams(0, wrap(), 1f).apply { marginStart = px(12f) })
        return row
    }

    private fun gallery(theme: SancaiTheme): LinearLayout {
        val themed = Snapshots.context(theme)
        val page = LinearLayout(themed)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(theme.colors.canvas)
        page.setPadding(px(16f), px(16f), px(16f), px(16f))

        // On the page itself: list rows that are loading.
        page.addView(personRow(themed, 0.55f, 0.8f), LinearLayout.LayoutParams(match(), wrap()))
        page.addView(
            personRow(themed, 0.4f, 0.65f),
            LinearLayout.LayoutParams(match(), wrap()).apply { topMargin = px(16f) },
        )

        // Inside a card: a picture, a paragraph and the place of a capsule button.
        val card = SancaiSurface(themed)
        val inner = LinearLayout(themed)
        inner.orientation = LinearLayout.VERTICAL
        inner.setPadding(px(7f), px(7f), px(7f), px(7f))
        inner.addView(SancaiSkeleton(themed), LinearLayout.LayoutParams(match(), px(96f)))
        inner.addView(
            SancaiSkeleton.text(themed, 3, 0.6f),
            LinearLayout.LayoutParams(match(), wrap()).apply { topMargin = px(14f) },
        )
        val button = SancaiSkeleton(themed).apply { shape = SancaiSkeleton.Shape.CAPSULE }
        inner.addView(
            button,
            LinearLayout.LayoutParams(px(132f), px(36f)).apply {
                topMargin = px(16f)
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        card.addView(inner, LinearLayout.LayoutParams(match(), wrap()))
        page.addView(card, LinearLayout.LayoutParams(match(), wrap()).apply { topMargin = px(20f) })

        // Reduced-motion look is the same block without a band; a still one stands for it.
        val still = SancaiSkeleton.text(themed, 2, 0.45f)
        for (index in 0 until still.childCount) (still.getChildAt(index) as SancaiSkeleton).isAnimated = false
        page.addView(still, LinearLayout.LayoutParams(match(), wrap()).apply { topMargin = px(16f) })
        return page
    }

    @Test
    fun `snapshot of skeletons in light and dark`() {
        for ((name, theme) in listOf("skeleton_light" to SancaiTheme.light(), "skeleton_dark" to SancaiTheme.dark())) {
            val page = gallery(theme)
            Snapshots.attach(page, 360)
            // A fixed moment: the band is a little left of the middle of the window.
            moveClockTo(phaseForBandCentre(page, page.rootView.width * 0.42f))
            val bitmap = draw(page, theme)
            Snapshots.save(bitmap, name)
            assertThat(bitmap.width).isEqualTo(px(360f))
            // Page colour in the margin, resting fill at the far end of the still line.
            assertThat(bitmap.getPixel(px(4f), px(4f))).isEqualTo(theme.colors.canvas)
            val probe = SancaiSkeleton(Snapshots.context(theme))
            assertThat(bitmap.getPixel(px(40f), bitmap.height - px(42f))).isEqualTo(probe.fillColor)
        }
    }

    private companion object {
        const val TRAVEL_MS = 1400L
        const val CYCLE_MS = 1700L
        const val BREATH_MS = 1600L
    }

    @Test
    fun `capsule and circle shapes`() {
        val capsule = SancaiSkeleton(context).apply { shape = SancaiSkeleton.Shape.CAPSULE }
        var bitmap = Snapshots.render(capsule, 200, 40)
        // A 6 dp corner would be filled here; a 20 dp half circle is not.
        assertThat(bitmap.getPixel(px(3f), px(3f))).isEqualTo(light.colors.canvas)
        assertThat(bitmap.getPixel(px(3f), bitmap.height / 2)).isEqualTo(capsule.fillColor)

        val circle = SancaiSkeleton(context).apply { shape = SancaiSkeleton.Shape.CIRCLE }
        bitmap = Snapshots.render(circle, 200, 40)
        // Centred in a wider view: the sides stay empty.
        assertThat(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)).isEqualTo(circle.fillColor)
        assertThat(bitmap.getPixel(px(40f), bitmap.height / 2)).isEqualTo(light.colors.canvas)
        assertThat(bitmap.getPixel(bitmap.width / 2 - px(18f), bitmap.height / 2)).isEqualTo(circle.fillColor)
        assertThat(bitmap.getPixel(bitmap.width / 2 - px(18f), px(2f))).isEqualTo(light.colors.canvas)
    }

    @Test
    fun `default size is a text line and a circle is forty dp`() {
        val frame = android.widget.FrameLayout(context)
        val line = SancaiSkeleton(context)
        val short = SancaiSkeleton(context).apply { widthFraction = 0.5f }
        val circle = SancaiSkeleton(context).apply { shape = SancaiSkeleton.Shape.CIRCLE }
        for (child in listOf(line, short, circle)) {
            frame.addView(child, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        Snapshots.layout(frame, 200)
        assertThat(line.width).isEqualTo(px(200f))
        assertThat(line.height).isEqualTo(px(12f))
        assertThat(short.width).isEqualTo(px(100f))
        assertThat(circle.width).isEqualTo(px(40f))
        assertThat(circle.height).isEqualTo(px(40f))
        short.widthFraction = 7f
        assertThat(short.widthFraction).isEqualTo(1f)
    }

    @Test
    fun `lines and gaps follow the text scale of the theme`() {
        val large = Snapshots.context(light.copy(textScale = 1.13f))
        val column = SancaiSkeleton.text(large, 2)
        Snapshots.layout(column, 200)
        val first = column.getChildAt(0)
        val second = column.getChildAt(1)
        assertThat(first.height).isEqualTo(px(12f * 1.13f))
        assertThat(second.top - first.bottom).isEqualTo(px(8f * 1.13f))
        assertThat(second.width).isEqualTo((px(200f) * 0.6f).roundToInt())
        // A circle is an avatar, not text: it keeps its size.
        val circle = SancaiSkeleton(large).apply { shape = SancaiSkeleton.Shape.CIRCLE }
        circle.measure(0, 0)
        assertThat(circle.measuredWidth).isEqualTo(px(40f))
        assertThat(circle.measuredHeight).isEqualTo(px(40f))
    }

    @Test
    fun `survives no room, no bounds and values that are not numbers`() {
        val view = SancaiSkeleton(context)
        view.measure(0, 0)
        assertThat(view.measuredWidth).isEqualTo(px(120f))
        assertThat(view.measuredHeight).isEqualTo(px(12f))

        view.widthFraction = Float.NaN
        assertThat(view.widthFraction).isEqualTo(1f)
        view.cornerRadiusDp = Float.NaN
        assertThat(view.cornerRadiusDp).isEqualTo(6f)
        view.cornerRadiusDp = -4f
        assertThat(view.cornerRadiusDp).isEqualTo(0f)
        assertThat(SancaiSkeleton.text(context, 1, Float.NaN).childCount).isEqualTo(1)

        // Padding larger than the view leaves nothing to draw, and nothing to crash on.
        val frame = android.widget.FrameLayout(context)
        val squeezed = SancaiSkeleton(context)
        squeezed.setPadding(px(30f), px(30f), px(30f), px(30f))
        frame.addView(squeezed, ViewGroup.LayoutParams(px(40f), px(40f)))
        val empty = SancaiSkeleton(context)
        frame.addView(empty, ViewGroup.LayoutParams(0, 0))
        Snapshots.attach(frame, 360)
        assertThat(squeezed.isRunning).isTrue()
        moveClockTo(300)
        val bitmap = draw(frame)
        assertThat(bitmap.getPixel(px(20f), px(20f))).isEqualTo(light.colors.canvas)
    }

    @Test
    fun `band keeps its place in the window when a parent is scrolled`() {
        val scroller = android.widget.HorizontalScrollView(context)
        val strip = LinearLayout(context)
        val view = SancaiSkeleton(context)
        strip.addView(view, LinearLayout.LayoutParams(px(900f), px(40f)))
        scroller.addView(strip, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(40f)))
        Snapshots.attach(scroller, 360)
        scroller.scrollTo(px(200f), 0)
        assertThat(scroller.scrollX).isEqualTo(px(200f))
        // The band is in the middle of the window, which is 200 dp further along the block.
        val windowX = xInRoot(scroller) + px(180f)
        moveClockTo(phaseForBandCentre(view, windowX.toFloat()))
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val y = view.height / 2
        assertThat(close(bitmap.getPixel(px(380f), y), view.bandColor, 2)).isTrue()
        assertThat(bitmap.getPixel(px(180f), y)).isEqualTo(view.fillColor)
    }
}
