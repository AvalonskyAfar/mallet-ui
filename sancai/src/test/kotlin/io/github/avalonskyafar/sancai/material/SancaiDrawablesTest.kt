package io.github.avalonskyafar.sancai.material

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.DrawableCompat
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SancaiDrawablesTest {

    private fun surface() = SancaiSurfaceDrawable(Snapshots.context(SancaiTheme.light()))

    private class Invalidations : Drawable.Callback {
        var count = 0
        override fun invalidateDrawable(who: Drawable) {
            count++
        }
        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = Unit
        override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
    }

    @Test
    fun `the platform setTint glazes the body and invalidates`() {
        val d = surface()
        val seen = Invalidations()
        d.callback = seen
        // What Java's d.setTint(int) and Kotlin's d.setTint(color) resolve to.
        (d as Drawable).setTint(Color.RED)
        assertThat(d.tint).isEqualTo(Color.RED)
        assertThat(seen.count).isEqualTo(1)
    }

    @Test
    fun `a tint list glazes with its default colour and null clears it`() {
        val d = surface()
        d.setTintList(ColorStateList.valueOf(Color.BLUE))
        assertThat(d.tint).isEqualTo(Color.BLUE)
        DrawableCompat.setTint(d, Color.GREEN)
        assertThat(d.tint).isEqualTo(Color.GREEN)
        d.setTintList(null)
        assertThat(d.tint).isNull()
    }

    @Test
    fun `the property still takes a colour or null`() {
        val d = surface()
        d.tint = Color.RED
        assertThat(d.tint).isEqualTo(Color.RED)
        d.tint = null
        assertThat(d.tint).isNull()
    }
}
