package io.github.avalonskyafar.mallet.material

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.DrawableCompat
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.theme.MalletTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MalletDrawablesTest {

    private fun surface() = MalletSurfaceDrawable(Snapshots.context(MalletTheme.light()))

    private class Invalidations : Drawable.Callback {
        var count = 0
        override fun invalidateDrawable(who: Drawable) {
            count++
        }
        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = Unit
        override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
    }

    @Test
    fun `the platform setTint colours the body and invalidates`() {
        val d = surface()
        val seen = Invalidations()
        d.callback = seen
        // What Java's d.setTint(int) and Kotlin's d.setTint(color) resolve to.
        (d as Drawable).setTint(Color.RED)
        assertThat(d.tint).isEqualTo(Color.RED)
        assertThat(seen.count).isEqualTo(1)
    }

    @Test
    fun `a tint list colours with its default colour and null clears it`() {
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
