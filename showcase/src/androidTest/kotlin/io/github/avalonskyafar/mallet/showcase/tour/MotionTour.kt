package io.github.avalonskyafar.mallet.showcase.tour

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.avalonskyafar.mallet.interaction.MalletExpandableLayout
import io.github.avalonskyafar.mallet.interaction.MalletPager
import io.github.avalonskyafar.mallet.interaction.MalletRevealLayout
import io.github.avalonskyafar.mallet.showcase.Pages
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.showcase.device.Device
import io.github.avalonskyafar.mallet.showcase.device.DeviceTest
import io.github.avalonskyafar.mallet.widget.MalletEntryRow
import io.github.avalonskyafar.mallet.widget.MalletPowerSwitch
import io.github.avalonskyafar.mallet.widget.MalletSegmentedTrack
import io.github.avalonskyafar.mallet.widget.MalletSlider
import io.github.avalonskyafar.mallet.widget.MalletSwitch
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the clips of the app in motion that the documentation shows. Every clip is the
 * screen of a running app, driven by real touch events; the finger speeds up and slows down
 * like a hand, and a dot marks where the app's window is touched (see [TouchMarker]).
 *
 * Run with `-e tour true`; the clips are left in `/data/local/tmp/mallet/motion`.
 */
@RunWith(AndroidJUnit4::class)
class MotionTour : DeviceTest() {

    @Before
    fun stage() {
        Tour.assumeRequested()
        Tour.shell("mkdir -p ${Tour.ROOT}/motion")
        Tour.demoMode(true)
        Tour.markTouches(true)
    }

    @After
    fun strike() {
        Tour.markTouches(false)
        Tour.demoMode(false)
    }

    private fun glideScreen(fromFx: Float, fromFy: Float, toFx: Float, toFy: Float, ms: Long = 650) =
        Device.glideScreen(fromFx, fromFy, toFx, toFy, Tour.time(ms))

    /** A clip that does not show what it is meant to show is worth nothing: fail instead. */
    private fun shows(what: String, condition: () -> Boolean) {
        if (!Device.onMain(condition)) throw AssertionError("the clip does not show that $what")
    }

    /** Pages are places: two neighbours of Home, a page opened from a row. */
    @Test
    fun places() {
        Tour.open(Pages.HOME)
        Tour.record("places") {
            Tour.pause(300)
            glideScreen(0.80f, 0.27f, 0.22f, 0.27f)
            Device.waitForPage("interactions")
            Tour.pause(350)
            glideScreen(0.22f, 0.20f, 0.80f, 0.20f)
            Device.waitForPage(Pages.HOME)
            Tour.pause(250)
            glideScreen(0.5f, 0.80f, 0.5f, 0.34f)
            Device.waitForPage("components")
            Tour.pause(350)
            Device.tap(Device.all<MalletEntryRow>().first())
            Device.waitForPage(Pages.components.first().key)
            Tour.pause(500)
        }
    }

    /** A thumb that follows the finger and takes the hue of where it is. */
    @Test
    fun tracks() {
        Tour.open("c_tracks")
        val tracks = Device.all<MalletSegmentedTrack>()
        val four = tracks[2]
        Tour.record("tracks") {
            Tour.pause(300)
            Device.glide(four, 0.625f, 0.125f, ms = Tour.time(900))
            Tour.pause(450)
            shows("the thumb was carried to the first option") { four.selectedIndex == 0 }
            Device.glide(four, 0.125f, 0.875f, ms = Tour.time(1200))
            Tour.pause(450)
            shows("the thumb was carried to the last option") { four.selectedIndex == 3 }
            Device.tap(tracks[1], fx = 0.17f)
            Tour.pause(250)
            Device.tap(tracks[0], fx = 0.25f)
            Tour.pause(400)
        }
    }

    /** Switches and the power switch: a tap, drags, and a drag that is taken back. */
    @Test
    fun switches() {
        Tour.open("c_tracks")
        val switches = Device.all<MalletSwitch>()
        val power = Device.all<MalletPowerSwitch>().first()
        Device.bringIntoView(switches[0], power)
        Tour.record("switches") {
            Tour.pause(300)
            Device.tap(switches[0])
            Tour.pause(200)
            Device.glide(switches[1], 0.25f, 0.8f, ms = Tour.time(500))
            Tour.pause(350)
            shows("the second switch was dragged on") { switches[1].isChecked }
            Device.glide(power, 0.80f, 0.22f, ms = Tour.time(900))
            Tour.pause(500)
            shows("the power switch was dragged off") { !power.isChecked }
            // Not far enough: the thumb returns to where it came from.
            Device.glide(power, 0.22f, 0.42f, ms = Tour.time(600))
            Tour.pause(500)
            shows("a short drag left the power switch off") { !power.isChecked }
            Device.glide(power, 0.22f, 0.80f, ms = Tour.time(900))
            Tour.pause(400)
            shows("the power switch was dragged on again") { power.isChecked }
        }
    }

    /** A slider coloured along a scale of hues, one that moves in steps and one over a plain share. */
    @Test
    fun sliders() {
        Tour.open("c_numbers")
        val sliders = Device.all<MalletSlider>()
        Device.bringIntoView(sliders[0], sliders[2])
        Tour.record("sliders") {
            Tour.pause(300)
            Device.glide(sliders[0], 0.40f, 0.86f, ms = Tour.time(900))
            Tour.pause(300)
            Device.glide(sliders[1], 0.40f, 0.80f, ms = Tour.time(900))
            Tour.pause(300)
            Device.glide(sliders[2], 0.36f, 0.95f, ms = Tour.time(1100))
            Tour.pause(400)
            shows("the bead was carried to the end of the scale") { sliders[2].value == sliders[2].valueTo }
            Device.glide(sliders[2], 0.95f, 0.06f, ms = Tour.time(1300))
            Tour.pause(400)
            shows("the bead was carried back to the start") { sliders[2].value == sliders[2].valueFrom }
        }
    }

    /** The appearance changes while the app runs: the old look fades over the new one. */
    @Test
    fun appearance() {
        Tour.open("appearance")
        Tour.record("appearance") {
            Tour.pause(400)
            choose(which = 0, index = 2, options = 3)
            Tour.pause(700)
            choose(which = 1, index = 1, options = 2)
            Tour.pause(700)
            choose(which = 0, index = 1, options = 3)
            Tour.pause(600)
        }
    }

    /** Taps an option of a track of the Appearance page and waits for the rebuilt screen. */
    private fun choose(which: Int, index: Int, options: Int) {
        val before = Device.activity()
        val track = Device.all<MalletSegmentedTrack>()[which]
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
        val rows = Device.all<MalletRevealLayout>()
        Tour.record("reveal") {
            Tour.pause(300)
            Device.glide(rows[0], 0.30f, 0.64f, ms = Tour.time(700))
            Tour.pause(500)
            shows("the first row is open") { rows[0].isOpen }
            // Opening another row closes the first.
            Device.glide(rows[2], 0.30f, 0.64f, ms = Tour.time(700))
            Tour.pause(500)
            shows("the third row is open and the first one shut") { rows[2].isOpen && !rows[0].isOpen }
            Device.tap(rows[2], fx = 0.09f)
            Tour.pause(500)
            shows("the action removed its row") { !rows[2].isAttachedToWindow }
            val sections = Device.all<MalletExpandableLayout>()
            Device.bringIntoView(sections.first(), sections.last(), smooth = true)
            Device.tap(Device.onMain { sections[1].header!! })
            Tour.pause(450)
            Device.tap(Device.onMain { sections[0].header!! })
            Tour.pause(500)
            shows("the second section opened and the first one closed") { sections[1].isExpanded && !sections[0].isExpanded }
        }
    }

    /** A dialog, a sheet that follows the finger down, a menu and a toast. */
    @Test
    fun overlays() {
        Tour.open("c_overlays")
        Tour.record("overlays") {
            Tour.pause(300)
            Device.pressButton(R.string.overlays_dialog_confirm, smooth = true)
            Device.waitForText(Device.shown(R.string.overlays_restore_title))
            Tour.pause(700)
            Device.waitForText(Device.shown(R.string.overlays_restore_action)).click()
            Device.waitTextGone(Device.shown(R.string.overlays_restore_title))
            Tour.pause(400)

            Device.pressButton(R.string.overlays_sheet_actions, smooth = true)
            val title = Device.waitForText(Device.shown(R.string.overlays_share_title))
            Tour.pause(800)
            val from = title.visibleCenter
            Device.glide(from.x, from.y, from.x, from.y + Device.ui.displayHeight / 2, Tour.time(700))
            Device.waitTextGone(Device.shown(R.string.overlays_action_copy))
            Tour.pause(400)

            Device.pressButton(R.string.overlays_menu_more, smooth = true)
            Device.waitForText(Device.shown(R.string.overlays_action_edit))
            Tour.pause(700)
            Device.waitForText(Device.shown(R.string.overlays_action_edit)).click()
            Device.waitTextGone(Device.shown(R.string.overlays_action_edit))
            Tour.pause(400)

            Device.pressButton(R.string.overlays_toast_action, smooth = true)
            Device.waitForText(Device.shown(R.string.overlays_toast_undo))
            Tour.pause(1200)
        }
    }

    /** Two pagers that turn together. */
    @Test
    fun pager() {
        Tour.open("i_pager")
        val (heading, pager) = Device.all<MalletPager>()
        Device.bringToCentre(pager)
        Tour.record("pager") {
            Tour.pause(300)
            Device.glide(pager, 0.80f, 0.20f, ms = Tour.time(700))
            Tour.pause(400)
            Device.glide(pager, 0.80f, 0.20f, ms = Tour.time(700))
            Tour.pause(400)
            Device.glide(heading, 0.25f, 0.80f, ms = Tour.time(700))
            Tour.pause(500)
            shows("two pages forward and one back, both pagers together") { pager.currentPage == 1 && heading.currentPage == 1 }
        }
    }
}
