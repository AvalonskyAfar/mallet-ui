package io.github.avalonskyafar.sancai.showcase.tour

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.avalonskyafar.sancai.interaction.SancaiExpandableLayout
import io.github.avalonskyafar.sancai.interaction.SancaiPager
import io.github.avalonskyafar.sancai.interaction.SancaiRevealLayout
import io.github.avalonskyafar.sancai.showcase.Pages
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.showcase.device.Device
import io.github.avalonskyafar.sancai.showcase.device.DeviceTest
import io.github.avalonskyafar.sancai.widget.SancaiEntryRow
import io.github.avalonskyafar.sancai.widget.SancaiPowerSwitch
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSlider
import io.github.avalonskyafar.sancai.widget.SancaiSwitch
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the clips of the app in motion that the documentation shows. Every clip is made by
 * real touches on a running app; the finger speeds up and slows down like a hand, and the
 * system marks where it touches.
 *
 * Run with `-e tour true`; the clips are left in `/data/local/tmp/sancai/motion`.
 */
@RunWith(AndroidJUnit4::class)
class MotionTour : DeviceTest() {

    @Before
    fun stage() {
        Tour.assumeRequested()
        Tour.shell("mkdir -p ${Tour.ROOT}/motion")
        Tour.demoMode(true)
        Tour.showTouches(true)
    }

    @After
    fun strike() {
        Tour.showTouches(false)
        Tour.demoMode(false)
    }

    private fun glideScreen(fromFx: Float, fromFy: Float, toFx: Float, toFy: Float, ms: Long = 650) =
        Device.glideScreen(fromFx, fromFy, toFx, toFy, Tour.time(ms))

    /** Pages are places: the four neighbours of Home, a page opened from a row, the ways back. */
    @Test
    fun places() {
        Tour.open(Pages.HOME)
        Tour.record("places") {
            Tour.pause(500)
            glideScreen(0.80f, 0.27f, 0.22f, 0.27f)
            Device.waitForPage("interactions")
            Tour.pause(500)
            glideScreen(0.22f, 0.20f, 0.80f, 0.20f)
            Device.waitForPage(Pages.HOME)
            Tour.pause(350)
            glideScreen(0.5f, 0.80f, 0.5f, 0.34f)
            Device.waitForPage("components")
            Tour.pause(500)
            Device.tap(Device.all<SancaiEntryRow>().first())
            Device.waitForPage(Pages.components.first().key)
            Tour.pause(600)
            glideScreen(0.25f, 0.14f, 0.82f, 0.14f)
            Device.waitForPage("components")
            Tour.pause(400)
            glideScreen(0.5f, 0.22f, 0.5f, 0.66f)
            Device.waitForPage(Pages.HOME)
            Tour.pause(300)
        }
    }

    /** A thumb that follows the finger and takes the glaze of where it is. */
    @Test
    fun tracks() {
        Tour.open("c_tracks")
        val track = Device.all<SancaiSegmentedTrack>()[2]
        Device.bringToCentre(track)
        Tour.record("tracks") {
            Tour.pause(400)
            Device.glide(track, 0.625f, 0.125f, ms = Tour.time(800))
            Tour.pause(500)
            Device.glide(track, 0.125f, 0.875f, ms = Tour.time(1000))
            Tour.pause(500)
            Device.tap(track, fx = 0.375f)
            Tour.pause(500)
            Device.tap(Device.all<SancaiSegmentedTrack>()[1], fx = 0.83f)
            Tour.pause(500)
            Device.tap(Device.all<SancaiSegmentedTrack>()[0], fx = 0.25f)
            Tour.pause(400)
        }
    }

    /** Switches and the power switch: taps, drags and a drag that is taken back. */
    @Test
    fun switches() {
        Tour.open("c_tracks")
        val switches = Device.all<SancaiSwitch>()
        Device.bringToCentre(switches[1])
        Tour.record("switches") {
            Tour.pause(400)
            Device.tap(switches[0])
            Tour.pause(350)
            Device.glide(switches[1], 0.25f, 0.8f, ms = Tour.time(500))
            Tour.pause(350)
            Device.tap(switches[2])
            Tour.pause(450)
            val power = Device.all<SancaiPowerSwitch>().first()
            Device.bringToCentre(power)
            Tour.pause(500)
            Device.glide(power, 0.80f, 0.22f, ms = Tour.time(800))
            Tour.pause(600)
            // Half-way and back: the thumb returns to where it came from.
            Device.glide(power, 0.22f, 0.42f, ms = Tour.time(600))
            Tour.pause(600)
            Device.glide(power, 0.22f, 0.80f, ms = Tour.time(800))
            Tour.pause(500)
        }
    }

    /** A slider whose glaze is poured along a scale, and one that moves in steps. */
    @Test
    fun sliders() {
        Tour.open("c_numbers")
        val sliders = Device.all<SancaiSlider>()
        Device.bringToCentre(sliders[1])
        Tour.record("sliders") {
            Tour.pause(400)
            Device.glide(sliders[0], 0.40f, 0.86f, ms = Tour.time(900))
            Tour.pause(350)
            Device.glide(sliders[1], 0.40f, 0.80f, ms = Tour.time(900))
            Tour.pause(350)
            Device.glide(sliders[2], 0.36f, 0.95f, ms = Tour.time(1100))
            Tour.pause(450)
            Device.glide(sliders[2], 0.95f, 0.06f, ms = Tour.time(1300))
            Tour.pause(500)
        }
    }

    /** The appearance changes while the app runs: the old look fades over the new one. */
    @Test
    fun appearance() {
        Tour.open("appearance")
        Tour.record("appearance") {
            Tour.pause(500)
            choose(which = 0, index = 2, options = 3)
            Tour.pause(900)
            choose(which = 1, index = 1, options = 2)
            Tour.pause(900)
            choose(which = 0, index = 1, options = 3)
            Tour.pause(900)
            choose(which = 1, index = 0, options = 2)
            Tour.pause(700)
        }
    }

    /** Taps an option of a track of the Appearance page and waits for the rebuilt screen. */
    private fun choose(which: Int, index: Int, options: Int) {
        val before = Device.activity()
        val track = Device.all<SancaiSegmentedTrack>()[which]
        Device.tap(track, fx = (index + 0.5f) / options)
        Device.waitUntil("the screen was rebuilt") {
            val now = Device.resumedOrNull()
            now != null && now !== before
        }
        Device.waitForPage("appearance")
    }

    /** Rows that slide aside for one action; sections that open in place. */
    @Test
    fun reveal() {
        Tour.open("i_reveal")
        val rows = Device.all<SancaiRevealLayout>()
        Device.bringToCentre(rows[1])
        Tour.record("reveal") {
            Tour.pause(400)
            Device.glide(rows[0], 0.30f, 0.64f, ms = Tour.time(700))
            Tour.pause(600)
            // Opening another row closes the first.
            Device.glide(rows[2], 0.30f, 0.64f, ms = Tour.time(700))
            Tour.pause(600)
            Device.tap(rows[2], fx = 0.09f)
            Tour.pause(900)
            val sections = Device.all<SancaiExpandableLayout>()
            Device.bringToCentre(sections[1])
            Tour.pause(500)
            Device.tap(Device.onMain { sections[1].header!! })
            Tour.pause(700)
            Device.tap(Device.onMain { sections[2].header!! })
            Tour.pause(700)
            Device.tap(Device.onMain { sections[0].header!! })
            Tour.pause(600)
        }
    }

    /** A dialog, a sheet that follows the finger down, a menu and a toast. */
    @Test
    fun overlays() {
        Tour.open("c_overlays")
        Tour.record("overlays") {
            Tour.pause(300)
            Device.pressButton(R.string.overlays_dialog_confirm)
            Device.waitForText(Device.shown(R.string.overlays_restore_title))
            Tour.pause(900)
            Device.waitForText(Device.shown(R.string.overlays_restore_action)).click()
            Device.waitTextGone(Device.shown(R.string.overlays_restore_title))
            Tour.pause(500)

            Device.pressButton(R.string.overlays_sheet_actions)
            val title = Device.waitForText(Device.shown(R.string.overlays_share_title))
            Tour.pause(900)
            val from = title.visibleCenter
            Device.glide(from.x, from.y, from.x, from.y + Device.ui.displayHeight / 2, Tour.time(800))
            Device.waitTextGone(Device.shown(R.string.overlays_action_copy))
            Tour.pause(500)

            Device.pressButton(R.string.overlays_menu_more)
            Device.waitForText(Device.shown(R.string.overlays_action_edit))
            Tour.pause(800)
            Device.waitForText(Device.shown(R.string.overlays_action_edit)).click()
            Device.waitTextGone(Device.shown(R.string.overlays_action_edit))
            Tour.pause(500)

            Device.pressButton(R.string.overlays_toast_action)
            Device.waitForText(Device.shown(R.string.overlays_toast_undo))
            Tour.pause(1400)
        }
    }

    /** Two pagers that turn together, and the page around them taking over at the first page. */
    @Test
    fun pager() {
        Tour.open("i_pager")
        val (heading, pager) = Device.all<SancaiPager>()
        Device.bringToCentre(pager)
        Tour.record("pager") {
            Tour.pause(400)
            Device.glide(pager, 0.80f, 0.20f, ms = Tour.time(700))
            Tour.pause(450)
            Device.glide(pager, 0.80f, 0.20f, ms = Tour.time(700))
            Tour.pause(450)
            Device.glide(heading, 0.25f, 0.80f, ms = Tour.time(700))
            Tour.pause(450)
            Device.glide(pager, 0.25f, 0.80f, ms = Tour.time(700))
            Tour.pause(600)
        }
    }
}
