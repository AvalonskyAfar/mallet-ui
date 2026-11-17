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
    fun `a control draws its own focus rim and switches the platform highlight off`() {
        assertThat(Build.VERSION.SDK_INT).isAtLeast(26)
        assertThat(SancaiSwitch(context).defaultFocusHighlightEnabled).isFalse()
        assertThat(SancaiSegmentedTrack(context).defaultFocusHighlightEnabled).isFalse()
        assertThat(SancaiCheckbox(context).defaultFocusHighlightEnabled).isFalse()
    }
}
