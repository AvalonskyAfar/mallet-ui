package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.interaction.SancaiPager
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * A card leaves its children room to draw past their bounds, because that is where the
 * shadows of tracks and capsules fade out. Content that scrolls or slides must not use it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiClippingTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val density = context.resources.displayMetrics.density

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    /** Paints red from its top edge to 40 dp below its own bottom edge. */
    private class Spilling(context: Context) : View(context) {
        private val paint = Paint().apply { color = Color.RED }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height + 40f * resources.displayMetrics.density, paint)
        }
    }

    private fun cardWith(child: View, heightDp: Float): SancaiSurface {
        val card = SancaiSurface(context)
        card.addView(child, LinearLayout.LayoutParams(-1, px(heightDp)))
        card.addView(View(context), LinearLayout.LayoutParams(-1, px(80f)))
        return card
    }

    @Test
    fun `a child of a card may draw past its bounds`() {
        val child = Spilling(context)
        val bitmap = Snapshots.render(cardWith(child, 20f), 200)
        assertThat(bitmap.getPixel(bitmap.width / 2, child.bottom + px(10f))).isEqualTo(Color.RED)
    }

    @Test
    fun `a scroll view in a card ends at its own edge`() {
        val scroll = ScrollView(context)
        val tall = View(context)
        tall.setBackgroundColor(Color.RED)
        // A scroll view measures its content without a height limit; this is what makes it tall.
        tall.minimumHeight = px(400f)
        scroll.addView(tall, android.widget.FrameLayout.LayoutParams(-1, -2))
        val bitmap = Snapshots.render(cardWith(scroll, 40f), 200)
        val x = bitmap.width / 2
        assertThat(bitmap.getPixel(x, scroll.top + px(20f))).isEqualTo(Color.RED)
        assertThat(bitmap.getPixel(x, scroll.bottom + px(10f))).isNotEqualTo(Color.RED)
        assertThat(bitmap.getPixel(x, scroll.bottom + px(60f))).isNotEqualTo(Color.RED)
    }

    @Test
    fun `any child marked as a scroll container is clipped too`() {
        val child = Spilling(context)
        child.isScrollContainer = true
        val card = cardWith(child, 20f)
        // The platform only reports a scroll container once it is in a window.
        Snapshots.attach(card, 200)
        val bitmap = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
        card.draw(Canvas(bitmap))
        assertThat(bitmap.getPixel(bitmap.width / 2, child.top + px(10f))).isEqualTo(Color.RED)
        assertThat(bitmap.getPixel(bitmap.width / 2, child.bottom + px(10f))).isNotEqualTo(Color.RED)
    }

    @Test
    fun `pages of a pager never draw outside it`() {
        val pager = SancaiPager(context)
        pager.setPages(2) { _, _ -> View(context).also { it.setBackgroundColor(Color.RED) } }
        // A row that does not clip, with room beside the pager.
        val row = LinearLayout(context)
        row.clipChildren = false
        row.addView(pager, LinearLayout.LayoutParams(px(100f), px(40f)))
        Snapshots.layout(row, 200)
        // Half-way through a move the page reaches across the pager's edge.
        val page = pager.getChildAt(0)
        page.translationX = pager.width / 2f
        val bitmap = Snapshots.render(row, 200)
        val y = pager.height / 2
        assertThat(bitmap.getPixel(pager.right - px(10f), y)).isEqualTo(Color.RED)
        assertThat(bitmap.getPixel(pager.right + px(10f), y)).isNotEqualTo(Color.RED)
    }
}
