package io.github.avalonskyafar.sancai.showcase.tour

import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import io.github.avalonskyafar.sancai.interaction.SancaiRevealLayout
import io.github.avalonskyafar.sancai.showcase.Pages
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.showcase.ShowcaseSettings
import io.github.avalonskyafar.sancai.showcase.ShowcaseSettings.Appearance
import io.github.avalonskyafar.sancai.showcase.device.Device
import io.github.avalonskyafar.sancai.showcase.device.DeviceTest
import io.github.avalonskyafar.sancai.widget.SancaiPowerSwitch
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Takes the pictures of the app that the documentation shows, on a real screen: every page in
 * both themes, the long pages screen by screen, overlays while they are open, gestures while
 * the finger is still down, the other glaze set, high contrast, large text, Chinese and a
 * right-to-left language.
 *
 * Run with `-e tour true`; the pictures are left in `/data/local/tmp/sancai/shots`.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTour : DeviceTest() {

    @Before
    fun cleanScreen() {
        Tour.assumeRequested()
        Tour.shell("mkdir -p ${Tour.ROOT}/shots")
        Tour.demoMode(true)
    }

    @After
    fun ordinaryScreen() {
        Tour.demoMode(false)
    }

    private val both = listOf(Appearance.LIGHT, Appearance.DARK)

    @Test
    fun everyPageInBothThemes() {
        for (key in Pages.graph.keys.sorted()) {
            for (appearance in both) {
                Tour.open(key, appearance)
                Tour.screenshot("page_${key}_${Tour.suffix(appearance)}")
            }
        }
    }

    /** The pages that are longer than the screen, one screen after the other, in the light theme. */
    @Test
    fun theLongPagesScreenByScreen() {
        for (key in Pages.graph.keys.sorted()) {
            Tour.open(key)
            val scroll = Device.pageView() as? ScrollView ?: continue
            var screen = 2
            while (Device.onMain { scroll.canScrollVertically(1) } && screen <= 9) {
                Device.onMain { scroll.scrollBy(0, scroll.height * 4 / 5) }
                Tour.screenshot("scroll_${key}_$screen")
                screen++
            }
        }
    }

    @Test
    fun overlaysWhileTheyAreOpen() {
        for (appearance in both) {
            val theme = Tour.suffix(appearance)
            Tour.open("c_overlays", appearance)

            Device.pressButton(R.string.overlays_dialog_confirm)
            Device.waitForText(Device.shown(R.string.overlays_restore_title))
            Tour.screenshot("overlay_dialog_$theme")
            Device.ui.pressBack()
            Device.waitTextGone(Device.shown(R.string.overlays_restore_title))

            Device.pressButton(R.string.overlays_dialog_delete)
            Device.waitForText(Device.shown(R.string.overlays_delete_title))
            Tour.screenshot("overlay_dialog_danger_$theme")
            Device.ui.pressBack()
            Device.waitTextGone(Device.shown(R.string.overlays_delete_title))

            Device.pressButton(R.string.overlays_dialog_three)
            Device.waitForText(Device.shown(R.string.overlays_keep_title))
            Tour.screenshot("overlay_dialog_three_$theme")
            Device.ui.pressBack()
            Device.waitTextGone(Device.shown(R.string.overlays_keep_title))

            Device.pressButton(R.string.overlays_sheet_actions)
            Device.waitForText(Device.shown(R.string.overlays_share_title))
            Device.settle(500)
            Tour.screenshot("overlay_sheet_$theme")
            Device.ui.pressBack()
            Device.waitTextGone(Device.shown(R.string.overlays_share_title))

            Device.pressButton(R.string.overlays_sheet_custom)
            Device.waitForText(Device.shown(R.string.overlays_strictness_title))
            Device.settle(500)
            Tour.screenshot("overlay_sheet_custom_$theme")
            Device.ui.pressBack()
            Device.waitTextGone(Device.shown(R.string.overlays_strictness_title))

            Device.pressButton(R.string.overlays_menu_more)
            Device.waitForText(Device.shown(R.string.overlays_action_edit))
            Tour.screenshot("overlay_menu_$theme")
            Device.ui.pressBack()
            Device.waitTextGone(Device.shown(R.string.overlays_action_edit))

            Device.pressButton(R.string.overlays_menu_sort)
            Device.waitForText(Device.shown(R.string.overlays_order_size))
            Tour.screenshot("overlay_menu_checked_$theme")
            Device.ui.pressBack()
            Device.waitTextGone(Device.shown(R.string.overlays_order_size))

            Device.pressButton(R.string.overlays_toast_action)
            Device.waitForText(Device.shown(R.string.overlays_toast_undo))
            Tour.screenshot("overlay_toast_$theme")
        }
    }

    @Test
    fun anInputDialogThatRefusedAName() {
        Tour.open("c_overlays")
        Device.pressButton(R.string.overlays_dialog_input)
        Device.waitForText(Device.shown(R.string.overlays_name_title))
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStringSync("a")
        Device.waitForText(Device.string(io.github.avalonskyafar.sancai.R.string.sancai_action_save)).click()
        Device.waitForText(Device.shown(R.string.overlays_name_error))
        Tour.screenshot("overlay_dialog_input_error_light")
        Device.ui.pressBack()
    }

    /** Pictures taken while the finger is still on the glass. */
    @Test
    fun gesturesHalfWay() {
        val w = Device.ui.displayWidth
        val h = Device.ui.displayHeight
        for (appearance in both) {
            val theme = Tour.suffix(appearance)

            // Between two places: Home is leaving upwards, Components arrives from below.
            Tour.open(Pages.HOME, appearance)
            Device.dragAndHold(w / 2, (h * 0.80f).toInt(), w / 2, (h * 0.42f).toInt()) {
                Tour.screenshot("gesture_between_pages_$theme")
            }
            Device.settle(900)

            // A thumb between two options: its glaze is a blend of the two.
            Tour.open("c_tracks", appearance)
            val track = Device.all<SancaiSegmentedTrack>()[2]
            Device.bringToCentre(track)
            val r = Device.bounds(track)
            Device.dragAndHold((r.left + r.width() * 0.625f).toInt(), r.centerY(), (r.left + r.width() * 0.26f).toInt(), r.centerY()) {
                Tour.screenshot("gesture_track_$theme")
            }
            Device.settle(600)

            // The power switch half-way to off.
            val power = Device.all<SancaiPowerSwitch>().first()
            Device.bringToCentre(power)
            val p = Device.bounds(power)
            Device.dragAndHold((p.left + p.width() * 0.80f).toInt(), p.centerY(), (p.left + p.width() * 0.45f).toInt(), p.centerY()) {
                Tour.screenshot("gesture_power_$theme")
            }
            Device.settle(600)

            // A row that has slid aside for its action.
            Tour.open("i_reveal", appearance)
            val row = Device.all<SancaiRevealLayout>().first()
            Device.bringToCentre(row)
            Device.drag(row, fromFx = 0.30f, toFx = 0.62f, steps = 40)
            Device.waitUntil("the row is open") { row.isOpen }
            Tour.screenshot("gesture_row_open_$theme")
        }
    }

    @Test
    fun otherAppearances() {
        for (appearance in both) {
            val theme = Tour.suffix(appearance)
            for (page in listOf("appearance", "c_tracks", Pages.HOME)) {
                Tour.open(page, appearance) { glazeSet = ShowcaseSettings.GlazeSet.PLUM }
                Tour.screenshot("look_plum_${page}_$theme")
            }
            for (page in listOf("c_tracks", "c_rows")) {
                Tour.open(page, appearance) { highContrast = true }
                Tour.screenshot("look_contrast_${page}_$theme")
            }
        }
        for (page in listOf("c_tracks", "c_rows", "c_buttons", Pages.HOME)) {
            Tour.open(page) { largeText = true }
            Tour.screenshot("look_large_$page")
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun inChinese() {
        try {
            Tour.appLanguage("zh-CN")
            for (page in listOf(Pages.HOME, "components", "c_tracks", "c_rows", "c_buttons", "f_type", "appearance")) {
                Tour.open(page)
                Tour.screenshot("zh_$page")
            }
            Tour.open("c_overlays")
            Device.pressButton(R.string.overlays_dialog_confirm)
            Device.waitForText(Device.shown(R.string.overlays_restore_title))
            Tour.screenshot("zh_overlay_dialog")
            Device.ui.pressBack()
        } finally {
            Tour.appLanguage("")
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun rightToLeft() {
        try {
            // The app has no Arabic strings; the layout direction is what this shows.
            Tour.appLanguage("ar")
            for (page in listOf(Pages.HOME, "c_tracks", "c_rows", "c_nav")) {
                Tour.open(page)
                Tour.screenshot("rtl_$page")
            }
        } finally {
            Tour.appLanguage("")
        }
    }
}
