package io.github.avalonskyafar.mallet.icon

import android.graphics.Color
import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletIconViewTest {

    private fun inkPixels(view: View, widthDp: Int): Pair<Int, Set<Int>> {
        val bitmap = Snapshots.render(view, widthDp, widthDp)
        val background = MalletTheme.of(view.context).colors.canvas
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
        val view = MalletIconView(Snapshots.context()).apply { icon = MalletIcon.CHECK }
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
        val view = MalletIconView(Snapshots.context())
        assertThat(inkPixels(view, 24).first).isEqualTo(0)
        view.icon = MalletIcon.POWER
        assertThat(inkPixels(view, 24).first).isGreaterThan(50)
    }

    @Test
    fun `tone and explicit colour choose the stroke colour`() {
        val context = Snapshots.context()
        val theme = MalletTheme.of(context)
        val view = MalletIconView(context).apply {
            icon = MalletIcon.MINUS
            strokeDp = 6f
            tone = MalletTextTone.DANGER
        }
        assertThat(inkPixels(view, 48).second).contains(theme.colors.danger)
        view.color = Color.rgb(1, 2, 3)
        val colours = inkPixels(view, 48).second
        assertThat(colours).contains(Color.rgb(1, 2, 3))
        assertThat(colours).doesNotContain(theme.colors.danger)
    }

    @Test
    fun `decorative unless described`() {
        val view = MalletIconView(Snapshots.context()).apply { icon = MalletIcon.INFO }
        assertThat(view.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        view.contentDescription = "Information"
        assertThat(view.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
    }
}
