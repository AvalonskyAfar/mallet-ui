package io.github.avalonskyafar.sancai.theme

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SancaiWindowTest {

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `light theme gets a canvas window and dark bar icons`() {
        val activity = activity()
        SancaiWindow.apply(activity, SancaiTheme.light())
        val window = activity.window
        assertThat((window.decorView.background as ColorDrawable).color).isEqualTo(Color.WHITE)
        @Suppress("DEPRECATION")
        assertThat(window.statusBarColor).isEqualTo(Color.TRANSPARENT)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        assertThat(controller.isAppearanceLightStatusBars).isTrue()
        assertThat(controller.isAppearanceLightNavigationBars).isTrue()
    }

    @Test
    fun `dark theme gets light bar icons`() {
        val activity = activity()
        val theme = SancaiTheme.dark()
        SancaiWindow.apply(activity, theme)
        val window = activity.window
        assertThat((window.decorView.background as ColorDrawable).color).isEqualTo(theme.colors.canvas)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        assertThat(controller.isAppearanceLightStatusBars).isFalse()
        assertThat(controller.isAppearanceLightNavigationBars).isFalse()
    }

    @Test
    fun `without edge to edge the bars take the canvas colour`() {
        val activity = activity()
        val theme = SancaiTheme.dark()
        SancaiWindow.apply(activity, theme, edgeToEdge = false)
        @Suppress("DEPRECATION")
        assertThat(activity.window.statusBarColor).isEqualTo(theme.colors.canvas)
        @Suppress("DEPRECATION")
        assertThat(activity.window.navigationBarColor).isEqualTo(theme.colors.canvas)
    }

    @Test
    fun `system bar insets are added to the existing padding`() {
        val view = View(activity())
        view.setPadding(10, 20, 30, 40)
        SancaiWindow.padForSystemBars(view)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 63, 0, 48))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 700))
            .build()
        ViewCompat.dispatchApplyWindowInsets(view, insets)
        assertThat(view.paddingTop).isEqualTo(20 + 63)
        assertThat(view.paddingBottom).isEqualTo(40 + 48)
        assertThat(view.paddingLeft).isEqualTo(10)

        // Applying new insets replaces the old ones instead of piling up.
        val changed = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 10, 0, 0))
            .build()
        ViewCompat.dispatchApplyWindowInsets(view, changed)
        assertThat(view.paddingTop).isEqualTo(20 + 10)
        assertThat(view.paddingBottom).isEqualTo(40)
    }

    @Test
    fun `the keyboard is included on request`() {
        val view = View(activity())
        SancaiWindow.padForSystemBars(view, includeIme = true)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 0, 0, 48))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 700))
            .build()
        ViewCompat.dispatchApplyWindowInsets(view, insets)
        assertThat(view.paddingBottom).isEqualTo(700)
    }

    @Test
    fun `a padded view asks for the insets each time it is attached`() {
        var asked = 0
        val activity = activity()
        val parent = object : android.widget.FrameLayout(activity) {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun requestFitSystemWindows() {
                asked++
                super.requestFitSystemWindows()
            }
        }
        activity.setContentView(parent)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val view = View(activity)
        SancaiWindow.padForSystemBars(view)
        asked = 0

        // Attached long after the window was first laid out, taken away, and brought back.
        parent.addView(view)
        assertThat(asked).isEqualTo(1)
        parent.removeView(view)
        parent.addView(view)
        assertThat(asked).isEqualTo(2)
    }

    @Test
    fun `asking for a high refresh rate never fails`() {
        SancaiWindow.preferHighestRefreshRate(activity())
    }
}
