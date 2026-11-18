package io.github.avalonskyafar.mallet.showcase.device

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiObject2
import io.github.avalonskyafar.mallet.overlay.MalletToastView
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.widget.MalletText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dialogs, sheets, menus and toasts on a device. They live in windows of their own, so they
 * are found the way an accessibility service finds them: by their label on the screen.
 */
@RunWith(AndroidJUnit4::class)
class OverlaysDeviceTest : DeviceTest() {

    private fun waitFor(label: String): UiObject2 = Device.waitForText(label)

    private fun waitGone(label: String) = Device.waitTextGone(label)

    private fun onScreen(label: String): UiObject2? = Device.onScreen(label)

    /** Presses the button of the page that carries [label]. */
    private fun press(label: Int) = Device.pressButton(label)

    private fun waitForStatus(expected: String) {
        Device.waitUntil("the page reports \"$expected\"") {
            Device.all<MalletText>().any { it.text.toString() == expected }
        }
    }

    @Test
    fun aDialogRisesAndItsConfirmingActionCloses() {
        Device.launch("c_overlays")
        press(R.string.overlays_dialog_confirm)
        waitFor(Device.string(R.string.overlays_restore_title))
        waitFor(Device.string(R.string.overlays_restore_action)).click()
        waitGone(Device.string(R.string.overlays_restore_title))
        waitForStatus(Device.string(R.string.overlays_restore_done))
    }

    @Test
    fun backClosesADialogWithoutConfirmingAndStaysOnThePage() {
        Device.launch("c_overlays")
        press(R.string.overlays_dialog_delete)
        waitFor(Device.string(R.string.overlays_delete_title))
        Device.ui.pressBack()
        waitGone(Device.string(R.string.overlays_delete_title))
        waitForStatus(Device.string(R.string.overlays_cancelled))
        assertEquals("c_overlays", Device.currentPage())
    }

    @Test
    fun aDialogWithThreeActionsReportsTheOneThatWasPicked() {
        Device.launch("c_overlays")
        press(R.string.overlays_dialog_three)
        waitFor(Device.string(R.string.overlays_keep_title))
        waitFor(Device.string(R.string.overlays_keep_discard)).click()
        waitGone(Device.string(R.string.overlays_keep_title))
        waitForStatus(Device.string(R.string.overlays_keep_discarded))
    }

    @Test
    fun anInputDialogKeepsARejectedNameAndClosesOnAGoodOne() {
        Device.launch("c_overlays")
        press(R.string.overlays_dialog_input)
        waitFor(Device.string(R.string.overlays_name_title))
        val save = Device.string(io.github.avalonskyafar.mallet.R.string.mallet_action_save)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.sendStringSync("a")
        waitFor(save).click()
        // One letter is refused: the error shows inside the dialog, which stays open.
        waitFor(Device.string(R.string.overlays_name_error))
        waitFor(Device.string(R.string.overlays_name_title))

        instrumentation.sendStringSync("bc")
        waitFor(save).click()
        waitGone(Device.string(R.string.overlays_name_title))
        waitForStatus(Device.string(R.string.overlays_name_done, "abc"))
    }

    @Test
    fun aSheetRisesAndFollowsTheFingerBackDown() {
        Device.launch("c_overlays")
        press(R.string.overlays_sheet_actions)
        val title = waitFor(Device.string(R.string.overlays_share_title))
        waitFor(Device.string(R.string.overlays_action_copy))
        Device.settle(500)

        val from = title.visibleCenter
        Device.ui.swipe(from.x, from.y, from.x, from.y + Device.ui.displayHeight / 2, 25)
        waitGone(Device.string(R.string.overlays_action_copy))
        waitForStatus(Device.string(R.string.overlays_sheet_closed))
    }

    @Test
    fun anActionOfASheetIsPicked() {
        Device.launch("c_overlays")
        press(R.string.overlays_sheet_actions)
        waitFor(Device.string(R.string.overlays_share_title))
        Device.settle(500)
        waitFor(Device.string(R.string.overlays_action_share)).click()
        waitGone(Device.string(R.string.overlays_share_title))
        waitForStatus(Device.string(R.string.overlays_picked_share))
    }

    @Test
    fun aMenuGrowsFromItsButtonAndAnItemIsPicked() {
        Device.launch("c_overlays")
        press(R.string.overlays_menu_more)
        waitFor(Device.string(R.string.overlays_action_edit)).click()
        waitGone(Device.string(R.string.overlays_action_edit))
        waitForStatus(Device.string(R.string.overlays_picked_edit))
    }

    @Test
    fun aToastRisesAndLeavesByItself() {
        Device.launch("c_overlays")
        val content = Device.onMain { Device.activity().findViewById<View>(android.R.id.content) }
        press(R.string.overlays_toast_neutral)
        Device.waitUntil("the toast is on screen") { Device.all<MalletToastView>(content).isNotEmpty() }
        assertTrue(onScreen(Device.string(R.string.overlays_toast_neutral_text)) != null)
        Device.waitUntil("the toast has left", timeoutMs = 9_000) { Device.all<MalletToastView>(content).isEmpty() }
    }
}
