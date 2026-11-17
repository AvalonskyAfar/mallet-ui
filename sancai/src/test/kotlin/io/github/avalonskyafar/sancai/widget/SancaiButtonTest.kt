package io.github.avalonskyafar.sancai.widget

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.View.MeasureSpec
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiButtonTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val density = context.resources.displayMetrics.density

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private fun wrapWidth(button: SancaiButton): Int {
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        button.measure(unspecified, unspecified)
        return button.measuredWidth
    }

    /** Rows (`rows = true`) or columns that hold a pixel for which [match] is true: first and last. */
    private fun extent(bitmap: Bitmap, rows: Boolean, match: (Int) -> Boolean): IntRange {
        var first = -1
        var last = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (!match(bitmap.getPixel(x, y))) continue
            val at = if (rows) y else x
            if (first == -1 || at < first) first = at
            if (at > last) last = at
        }
        check(first != -1) { "nothing matched" }
        return first..last
    }

    private fun isRed(pixel: Int): Boolean = Color.red(pixel) > 200 && Color.green(pixel) < 80 && Color.blue(pixel) < 80

    @Test
    fun `clickable and focusable by default, and the layout can say otherwise`() {
        val plain = SancaiButton(context)
        assertThat(plain.isClickable).isTrue()
        assertThat(plain.isFocusable).isTrue()
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.focusable, "false")
            .addAttribute(android.R.attr.clickable, "false")
            .build()
        val quiet = SancaiButton(context, attrs)
        assertThat(quiet.isClickable).isFalse()
        assertThat(quiet.isFocusable).isFalse()
    }

    @Test
    fun `the platform focus highlight is off, the rim is the focus mark`() {
        if (Build.VERSION.SDK_INT >= 26) {
            assertThat(SancaiButton(context).defaultFocusHighlightEnabled).isFalse()
        }
    }

    @Test
    fun `the label is centred on the capsule, not on the view`() {
        fun labelTop(variant: SancaiButton.Variant): Int {
            val button = SancaiButton(context)
            button.variant = variant
            button.text = "H"
            button.setTextColor(Color.RED)
            return extent(Snapshots.render(button, 200), rows = true, match = ::isRed).first
        }
        // A text button has no body: its label is centred on the view. The capsule's centre is
        // (4.8 - 1) / 2 dp above that.
        val lift = labelTop(SancaiButton.Variant.TEXT) - labelTop(SancaiButton.Variant.NEUTRAL)
        assertThat(lift).isAtLeast(px(1.9f) - 1)
        assertThat(lift).isAtMost(px(1.9f) + 1)
    }

    @Test
    fun `the icon takes its room out of the label's box`() {
        val button = SancaiButton(context)
        button.icon = SancaiIcon.PLUS
        button.text = "A label long enough to wrap onto a second line of the button, and a third"
        Snapshots.layout(button, 200)
        val box = button.width - button.paddingLeft - button.paddingRight
        assertThat(button.layout.width).isEqualTo(box - px(18f + 7f))
        assertThat(button.lineCount).isGreaterThan(1)
        assertThat(button.compoundPaddingLeft).isEqualTo(button.paddingLeft + px(18f + 7f))
        assertThat(button.compoundPaddingRight).isEqualTo(button.paddingRight)

        val info = context.applicationInfo
        info.flags = info.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        button.layoutParams = android.view.ViewGroup.LayoutParams(px(200f), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        button.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(button, 200)
        assertThat(button.compoundPaddingLeft).isEqualTo(button.paddingLeft)
        assertThat(button.compoundPaddingRight).isEqualTo(button.paddingRight + px(18f + 7f))
    }

    @Test
    fun `an icon widens a wrapping button by the icon and its gap`() {
        val plain = SancaiButton(context).apply { text = "Add" }
        val withIcon = SancaiButton(context).apply { text = "Add"; icon = SancaiIcon.PLUS }
        assertThat(wrapWidth(withIcon)).isEqualTo(wrapWidth(plain) + px(18f + 7f))
    }

    @Test
    fun `icon and label are centred as one group, the icon before the label`() {
        val button = SancaiButton(context)
        button.variant = SancaiButton.Variant.TEXT
        button.icon = SancaiIcon.PLUS
        button.text = "HH"
        button.setTextColor(Color.RED)
        val bitmap = Snapshots.render(button, 200)
        val canvasColor = bitmap.getPixel(0, 0)
        val group = extent(bitmap, rows = false) { it != canvasColor }
        val label = extent(bitmap, rows = false, match = ::isRed)
        assertThat((group.first + group.last + 1) / 2f).isWithin(2.5f).of(bitmap.width / 2f)
        // The icon ends before the label starts: 18 dp of icon and 7 dp of gap, less side bearings.
        assertThat(label.first - group.first).isAtLeast(px(18f + 7f) - px(3f))
        assertThat(label.first - group.first).isAtMost(px(18f + 7f) + px(3f))
        assertThat(group.last - label.last).isAtMost(2)
    }

    @Test
    fun `an icon without a label has no gap and sits in the middle`() {
        val button = SancaiButton(context)
        button.variant = SancaiButton.Variant.TEXT
        button.icon = SancaiIcon.PLUS
        assertThat(wrapWidth(button)).isEqualTo(button.paddingLeft + button.paddingRight + px(18f))

        val bitmap = Snapshots.render(button, 200)
        val canvasColor = bitmap.getPixel(0, 0)
        val columns = extent(bitmap, rows = false) { it != canvasColor }
        assertThat((columns.first + columns.last + 1) / 2f).isWithin(1.5f).of(bitmap.width / 2f)

        // The gap comes back with a label, also in a button of fixed width.
        button.layoutParams = android.view.ViewGroup.LayoutParams(px(200f), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        button.text = "Add"
        assertThat(button.isLayoutRequested).isTrue()
        Snapshots.layout(button, 200)
        assertThat(button.layout.width).isEqualTo(button.width - button.paddingLeft - button.paddingRight - px(18f + 7f))
    }
}
