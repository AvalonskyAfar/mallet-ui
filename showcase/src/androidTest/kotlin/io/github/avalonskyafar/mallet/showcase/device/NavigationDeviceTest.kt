package io.github.avalonskyafar.mallet.showcase.device

import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.avalonskyafar.mallet.showcase.MainActivity
import io.github.avalonskyafar.mallet.showcase.NeighbourActivity
import io.github.avalonskyafar.mallet.showcase.Pages
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.widget.MalletButton
import io.github.avalonskyafar.mallet.widget.MalletEntryRow
import io.github.avalonskyafar.mallet.widget.MalletPowerSwitch
import io.github.avalonskyafar.mallet.widget.MalletText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Moving through the app with real touches: pages as places, system back, a second activity. */
@RunWith(AndroidJUnit4::class)
class NavigationDeviceTest : DeviceTest() {

    @Test
    fun theAppStartsOnHomeAndThePowerSwitchAnswersTapAndDrag() {
        Device.launch()
        val power = Device.all<MalletPowerSwitch>().single()
        assertTrue(Device.onMain { power.isChecked })

        // A tap anywhere on the switch toggles it.
        Device.tap(power, fx = 0.25f)
        Device.waitUntil("the power switch is off") { !power.isChecked }
        val status = Device.string(R.string.home_status_off)
        assertTrue(Device.all<MalletText>().any { Device.onMain { it.text.toString() } == status })

        // Dragging the thumb across turns it back on.
        Device.drag(power, fromFx = 0.3f, toFx = 0.85f, steps = 40)
        Device.waitUntil("the power switch is on again") { power.isChecked }
    }

    @Test
    fun draggingTowardsANeighbourTravelsThereAndBackReturns() {
        Device.launch()
        // The finger moves towards the start: the page at the end side follows it in.
        Device.swipeScreen(0.75f, 0.25f, 0.25f, 0.25f)
        Device.waitForPage("interactions")
        Device.ui.pressBack()
        Device.waitForPage(Pages.HOME)

        Device.swipeScreen(0.25f, 0.25f, 0.75f, 0.25f)
        Device.waitForPage("foundations")
        Device.ui.pressBack()
        Device.waitForPage(Pages.HOME)

        // Up brings in what lies below, down what lies above.
        Device.swipeScreen(0.5f, 0.8f, 0.5f, 0.35f)
        Device.waitForPage("components")
        Device.ui.pressBack()
        Device.waitForPage(Pages.HOME)

        Device.swipeScreen(0.5f, 0.2f, 0.5f, 0.65f)
        Device.waitForPage("appearance")
        Device.ui.pressBack()
        Device.waitForPage(Pages.HOME)
    }

    @Test
    fun aShortSlowDragGlidesBack() {
        Device.launch()
        // An eighth of the width in about a second: neither far enough nor a flick.
        Device.swipeScreen(0.60f, 0.25f, 0.48f, 0.25f, steps = 60)
        Device.settle(900)
        assertEquals(Pages.HOME, Device.currentPage())
        assertFalse(Device.onMain { Device.host().isMoving })
    }

    @Test
    fun aRowOpensItsPageAndBackWalksUpTheMap() {
        Device.launch("components")
        val first = Device.all<MalletEntryRow>().first()
        Device.tap(first)
        Device.waitForPage(Pages.components.first().key)
        Device.ui.pressBack()
        Device.waitForPage("components")
        Device.ui.pressBack()
        Device.waitForPage(Pages.HOME)
    }

    @Test
    fun aPageThatArrivesLaterKeepsClearOfTheSystemBars() {
        // Android hands window insets down when they change, not when a view arrives. A page
        // built and attached long after the first layout pass has to ask for them itself.
        Device.launch("components")
        Device.tap(Device.all<MalletEntryRow>().first())
        Device.waitForPage(Pages.components.first().key)

        val page = Device.pageView() as ScrollView
        @Suppress("DEPRECATION")
        val statusBar = Device.onMain { page.rootWindowInsets.systemWindowInsetTop }
        val firstTop = Device.onMain {
            val origin = IntArray(2)
            page.getChildAt(0).getLocationInWindow(origin)
            origin[1]
        }
        assertTrue("the window is laid out behind a status bar", statusBar > 0)
        assertTrue("the page starts below the status bar ($firstTop >= $statusBar)", firstTop >= statusBar)

        // The same holds for a page reached by dragging.
        Device.ui.pressBack()
        Device.waitForPage("components")
        Device.ui.pressBack()
        Device.waitForPage(Pages.HOME)
        Device.swipeScreen(0.75f, 0.25f, 0.25f, 0.25f)
        Device.waitForPage("interactions")
        val dragged = Device.pageView() as ScrollView
        val draggedTop = Device.onMain {
            val origin = IntArray(2)
            dragged.getChildAt(0).getLocationInWindow(origin)
            origin[1]
        }
        assertTrue("a page dragged in starts below the status bar ($draggedTop >= $statusBar)", draggedTop >= statusBar)
    }

    @Test
    fun aSecondActivityArrivesLikeAPageAndIsSwipedBack() {
        Device.launch("i_spatial")
        val label = Device.string(R.string.spatial_neighbour_open)
        val open = Device.all<MalletButton>().first { Device.onMain { it.text.toString() } == label }
        Device.bringToCentre(open)
        Device.tap(open)
        Device.waitUntil("the neighbour activity is in front") { Device.resumedOrNull() is NeighbourActivity }
        Device.settle(700)

        // It came in from the end side; a drag towards the end, begun inside the page, sends it back.
        Device.swipeScreen(0.30f, 0.85f, 0.88f, 0.85f)
        Device.waitUntil("the first activity is in front again") { Device.resumedOrNull() is MainActivity }
        Device.waitForPage("i_spatial")
    }

    @Test
    fun rotatingTheDeviceKeepsThePage() {
        val before = Device.launch("c_rows")
        Device.ui.setOrientationLandscape()
        Device.waitUntil("the activity was recreated for landscape") {
            val now = Device.resumedOrNull()
            now is MainActivity && now !== before
        }
        Device.waitForPage("c_rows")
        assertNotSame(before, Device.activity())
        Device.ui.setOrientationNatural()
        Device.waitForPage("c_rows")
    }
}
