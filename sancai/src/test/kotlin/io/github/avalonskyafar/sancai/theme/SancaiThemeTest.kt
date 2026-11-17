package io.github.avalonskyafar.sancai.theme

import android.app.Activity
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SancaiThemeTest {

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun animatorScale(activity: Activity, scale: Float) {
        Settings.Global.putFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Test
    fun `a later lookup sees the system switching animations back on`() {
        val activity = activity()
        animatorScale(activity, 0f)
        assertThat(SancaiTheme.of(activity).reduceMotion).isTrue()
        animatorScale(activity, 1f)
        assertThat(SancaiTheme.of(activity).reduceMotion).isFalse()
        animatorScale(activity, 0f)
        assertThat(SancaiTheme.of(activity).reduceMotion).isTrue()
    }

    @Test
    fun `the resolved theme is reused while nothing changes`() {
        val activity = activity()
        assertThat(SancaiTheme.of(activity)).isSameInstanceAs(SancaiTheme.of(activity))
    }

    @Test
    fun `Java can build a theme from colours alone`() {
        val colors = SancaiColors.light()
        val constructor = SancaiTheme::class.java.getConstructor(SancaiColors::class.java)
        assertThat(constructor.newInstance(colors)).isEqualTo(SancaiTheme(colors))
        // Every prefix of the defaulted parameters is there, up to the full list.
        val arities = SancaiTheme::class.java.constructors.filter { c ->
            c.parameterTypes.none { it.name.contains("DefaultConstructorMarker") }
        }.map { it.parameterTypes.size }
        assertThat(arities).containsExactly(1, 2, 3, 4, 5, 6, 7)
    }

    @Test
    fun `Java can ask for a label colour with the glaze alone`() {
        val theme = SancaiTheme.light()
        val cobalt = theme.glazes.cobalt
        val one = SancaiTheme::class.java.getMethod("labelOnBed", SancaiGlaze::class.java)
        assertThat(one.invoke(theme, cobalt)).isEqualTo(theme.labelOnBed(cobalt))
        val two = SancaiTheme::class.java.getMethod("labelOnBed", SancaiGlaze::class.java, SancaiGlaze::class.java)
        assertThat(two.invoke(theme, cobalt, theme.glazes.ochre)).isEqualTo(theme.labelOnBed(cobalt, theme.glazes.ochre))
    }
}
