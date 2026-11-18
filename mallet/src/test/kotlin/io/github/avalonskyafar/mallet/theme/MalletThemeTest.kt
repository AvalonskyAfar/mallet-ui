package io.github.avalonskyafar.mallet.theme

import android.app.Activity
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MalletThemeTest {

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun animatorScale(activity: Activity, scale: Float) {
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Test
    fun `a later lookup sees the system switching animations back on`() {
        val activity = activity()
        animatorScale(activity, 0f)
        assertThat(MalletTheme.of(activity).reduceMotion).isTrue()
        animatorScale(activity, 1f)
        assertThat(MalletTheme.of(activity).reduceMotion).isFalse()
        animatorScale(activity, 0f)
        assertThat(MalletTheme.of(activity).reduceMotion).isTrue()
    }

    @Test
    fun `the resolved theme is reused while nothing changes`() {
        val activity = activity()
        assertThat(MalletTheme.of(activity)).isSameInstanceAs(MalletTheme.of(activity))
    }

    @Test
    fun `Java can build a theme from colours alone`() {
        val colors = MalletColors.light()
        val constructor = MalletTheme::class.java.getConstructor(MalletColors::class.java)
        assertThat(constructor.newInstance(colors)).isEqualTo(MalletTheme(colors))
        // Every prefix of the defaulted parameters is there, up to the full list.
        val arities = MalletTheme::class.java.constructors.filter { c ->
            c.parameterTypes.none { it.name.contains("DefaultConstructorMarker") }
        }.map { it.parameterTypes.size }
        assertThat(arities).containsExactly(1, 2, 3, 4, 5, 6, 7)
    }

    @Test
    fun `Java can ask for a label colour with the hue alone`() {
        val theme = MalletTheme.light()
        val cobalt = theme.hues.cobalt
        val one = MalletTheme::class.java.getMethod("labelOnBed", MalletHue::class.java)
        assertThat(one.invoke(theme, cobalt)).isEqualTo(theme.labelOnBed(cobalt))
        val two = MalletTheme::class.java.getMethod("labelOnBed", MalletHue::class.java, MalletHue::class.java)
        assertThat(two.invoke(theme, cobalt, theme.hues.ochre)).isEqualTo(theme.labelOnBed(cobalt, theme.hues.ochre))
    }
}
