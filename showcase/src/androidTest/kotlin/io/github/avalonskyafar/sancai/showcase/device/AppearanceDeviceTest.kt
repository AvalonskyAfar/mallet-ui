package io.github.avalonskyafar.sancai.showcase.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.avalonskyafar.sancai.showcase.MainActivity
import io.github.avalonskyafar.sancai.showcase.ShowcaseSettings
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Changing the appearance while the app runs: the screen is rebuilt in the new theme, in place. */
@RunWith(AndroidJUnit4::class)
class AppearanceDeviceTest : DeviceTest() {

    /** Taps option [index] of the [which]-th track of the Appearance page and waits for the rebuilt screen. */
    private fun choose(which: Int, index: Int, options: Int): MainActivity {
        val before = Device.activity()
        val track = Device.all<SancaiSegmentedTrack>()[which]
        Device.bringToCentre(track)
        Device.tap(track, fx = (index + 0.5f) / options)
        Device.waitUntil("the screen was rebuilt") {
            val now = Device.resumedOrNull()
            now is MainActivity && now !== before
        }
        Device.waitForPage("appearance")
        return Device.activity() as MainActivity
    }

    @Test
    fun choosingDarkRebuildsTheScreenInTheDarkPaletteAndLightBringsItBack() {
        Device.launch("appearance")
        assertFalse(Device.onMain { SancaiTheme.of(Device.activity()).isDark })

        // The first track of the page: System, Light, Dark.
        val dark = choose(which = 0, index = 2, options = 3)
        assertTrue(Device.onMain { SancaiTheme.of(dark).isDark })
        assertEquals(SancaiColors.dark().canvas, Device.onMain { SancaiTheme.of(dark).colors.canvas })
        assertEquals(ShowcaseSettings.Appearance.DARK, Device.onMain { Device.app.settings.appearance })
        // The control that caused the change shows the new choice on the rebuilt page.
        assertEquals(2, Device.onMain { Device.all<SancaiSegmentedTrack>()[0].selectedIndex })

        val light = choose(which = 0, index = 1, options = 3)
        assertFalse(Device.onMain { SancaiTheme.of(light).isDark })
    }

    @Test
    fun largerTextScalesTheTypeOfTheRebuiltPage() {
        Device.launch("appearance")
        val before = Device.onMain { Device.all<SancaiText>().first().textSize }

        // The fourth track of the page is "Larger text": Off, On.
        choose(which = 3, index = 1, options = 2)

        assertTrue(Device.onMain { Device.app.settings.largeText })
        val after = Device.onMain { Device.all<SancaiText>().first().textSize }
        assertEquals(SancaiTheme.LARGE_TEXT_SCALE, after / before, 0.02f)
    }
}
