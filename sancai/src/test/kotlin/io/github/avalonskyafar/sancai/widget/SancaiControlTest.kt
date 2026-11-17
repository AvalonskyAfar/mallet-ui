package io.github.avalonskyafar.sancai.widget

import android.os.Build
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** What every custom-drawn control inherits from [SancaiControl]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiControlTest {

    private val context = Snapshots.context(SancaiTheme.light())

    @Test
    fun `a control is clickable and focusable unless the layout says otherwise`() {
        val plain = SancaiSwitch(context)
        assertThat(plain.isClickable).isTrue()
        assertThat(plain.isFocusable).isTrue()

        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.focusable, "false")
            .addAttribute(android.R.attr.clickable, "false")
            .build()
        val quiet = SancaiSwitch(context, attrs)
        assertThat(quiet.isFocusable).isFalse()
        assertThat(quiet.isClickable).isFalse()

        val half = SancaiSwitch(
            context, Robolectric.buildAttributeSet().addAttribute(android.R.attr.focusable, "false").build(),
        )
        assertThat(half.isFocusable).isFalse()
        assertThat(half.isClickable).isTrue()
    }

    @Test
    fun `a control is laid out and drawn again when the direction around it changes`() {
        // Without this flag of the application Android resolves every direction to left-to-right.
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        var layouts = 0
        var redraws = 0
        val control = object : SancaiControl(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 100)

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                layouts++
            }

            override fun invalidate() {
                redraws++
                super.invalidate()
            }
        }
        // The control fills its parent, so a new direction moves and resizes nothing.
        val parent = android.widget.FrameLayout(context)
        parent.addView(control, android.widget.FrameLayout.LayoutParams(-1, 100))
        Snapshots.attach(parent, 300)
        Snapshots.layout(parent, 300)
        layouts = 0
        redraws = 0

        parent.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(parent, 300)
        assertThat(control.layoutDirection).isEqualTo(android.view.View.LAYOUT_DIRECTION_RTL)
        assertThat(layouts).isEqualTo(1)
        assertThat(redraws).isAtLeast(1)

        // Laid out again in the same direction, the control stays as it is.
        layouts = 0
        redraws = 0
        parent.requestLayout()
        Snapshots.layout(parent, 300)
        assertThat(layouts).isEqualTo(0)
        assertThat(redraws).isEqualTo(0)
    }

    @Test
    fun `a control draws its own focus rim and switches the platform highlight off`() {
        assertThat(Build.VERSION.SDK_INT).isAtLeast(26)
        assertThat(SancaiSwitch(context).defaultFocusHighlightEnabled).isFalse()
        assertThat(SancaiSegmentedTrack(context).defaultFocusHighlightEnabled).isFalse()
        assertThat(SancaiCheckbox(context).defaultFocusHighlightEnabled).isFalse()
    }
}
