package io.github.avalonskyafar.sancai.icon

import android.graphics.Color
import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiIconViewTest {

    private fun inkPixels(view: View, widthDp: Int): Pair<Int, Set<Int>> {
        val bitmap = Snapshots.render(view, widthDp, widthDp)
        val background = SancaiTheme.of(view.context).colors.canvas
        var count = 0
        val colours = HashSet<Int>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (pixel != background) {
                    count++
                    colours.add(pixel)
                }
            }
        }
        return count to colours
    }

    @Test
    fun `wrap content measures twenty four dp plus padding`() {
        val view = SancaiIconView(Snapshots.context()).apply { icon = SancaiIcon.CHECK }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val density = view.resources.displayMetrics.density
        assertThat(view.measuredWidth.toFloat()).isWithin(1f).of(24f * density)
        view.setPadding(10, 0, 10, 0)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        assertThat(view.measuredWidth.toFloat()).isWithin(1f).of(24f * density + 20f)
    }

    @Test
    fun `draws nothing without an icon and something with one`() {
        val view = SancaiIconView(Snapshots.context())
        assertThat(inkPixels(view, 24).first).isEqualTo(0)
        view.icon = SancaiIcon.POWER
        assertThat(inkPixels(view, 24).first).isGreaterThan(50)
    }

    @Test
    fun `tone and explicit colour choose the stroke colour`() {
        val context = Snapshots.context()
        val theme = SancaiTheme.of(context)
        val view = SancaiIconView(context).apply {
            icon = SancaiIcon.MINUS
            strokeDp = 6f
            tone = SancaiTextTone.DANGER
        }
        assertThat(inkPixels(view, 48).second).contains(theme.colors.danger)
        view.color = Color.rgb(1, 2, 3)
        val colours = inkPixels(view, 48).second
        assertThat(colours).contains(Color.rgb(1, 2, 3))
        assertThat(colours).doesNotContain(theme.colors.danger)
    }

    @Test
    fun `decorative unless described`() {
        val view = SancaiIconView(Snapshots.context()).apply { icon = SancaiIcon.INFO }
        assertThat(view.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        view.contentDescription = "Information"
        assertThat(view.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
    }
}
