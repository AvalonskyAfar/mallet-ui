package io.github.avalonskyafar.mallet.showcase.device

import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.avalonskyafar.mallet.showcase.Pages
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.widget.MalletPowerSwitch
import io.github.avalonskyafar.mallet.widget.MalletSwitch
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * Controls glide every time they are operated, not only on the first tap.
 *
 * A glide is a run of frames; a jump is one. The test counts the frames the page draws while a
 * control changes state, so it tells the two apart without looking at pixels.
 */
@RunWith(AndroidJUnit4::class)
class AnimationDeviceTest : DeviceTest() {

    @Test
    fun thePowerSwitchGlidesOnEveryTap() {
        Device.launch()
        repeat(5) { round ->
            val switch = Device.all<MalletPowerSwitch>().single()
            assertGlides(switch, "tap ${round + 1} on Home")
        }
    }

    @Test
    fun thePowerSwitchStillGlidesAfterHomeComesBack() {
        Device.launch()
        repeat(4) { round ->
            // Leave Home for the page below it and come back: it is a kept page, re-attached.
            Device.swipeScreen(0.5f, 0.8f, 0.5f, 0.35f)
            Device.waitForPage("components")
            Device.ui.pressBack()
            Device.waitForPage(Pages.HOME)
            val switch = Device.all<MalletPowerSwitch>().single()
            assertGlides(switch, "tap ${round + 1} after Home came back")
        }
    }

    @Test
    fun aKeptPageComesBackLaidOut() {
        // A page that is taken off screen and put back at the size it left is not measured
        // again, and Android lays nothing out inside it unless the page is asked; a control
        // that then believed "not laid out" would silently stop gliding.
        Device.launch()
        Device.swipeScreen(0.5f, 0.8f, 0.5f, 0.35f)
        Device.waitForPage("components")
        Device.ui.pressBack()
        Device.waitForPage(Pages.HOME)
        val page = Device.pageView()
        val switch = Device.all<MalletPowerSwitch>().single()
        val chain = chainOf(switch)
        assertTrue("the page that came back is laid out$chain", Device.onMain { page.isLaidOut })
        assertTrue("the control in it is laid out$chain", Device.onMain { switch.isLaidOut })
    }

    /** For a failure: what each view on the way up from [view] says about itself. */
    private fun chainOf(view: View): String = Device.onMain {
        val parts = ArrayList<String>()
        var v: View? = view
        while (v != null) {
            parts += "${v.javaClass.simpleName} laidOut=${v.isLaidOut} requested=${v.isLayoutRequested} " +
                "${v.width}x${v.height} shown=${v.visibility == View.VISIBLE}"
            v = v.parent as? View
        }
        "\n  " + parts.joinToString("\n  ")
    }

    @Test
    fun aSwitchInACardGlidesOnEveryTap() {
        Device.launch("c_buttons")
        val switch = Device.all<MalletSwitch>().first { Device.onMain { it.isEnabled } }
        Device.bringToCentre(switch)
        repeat(5) { round ->
            assertGlides(switch, "tap ${round + 1} on c_buttons")
        }
    }

    /** Taps [view] and fails unless the page drew a run of frames, that is, a glide. */
    private fun assertGlides(view: View, what: String) {
        val frames = AtomicInteger()
        val page = Device.pageView()
        val listener = ViewTreeObserver.OnDrawListener { frames.incrementAndGet() }
        Device.onMain { page.viewTreeObserver.addOnDrawListener(listener) }
        Device.tap(view)
        Device.settle(400)
        Device.onMain { page.viewTreeObserver.removeOnDrawListener(listener) }
        val drawn = frames.get()
        val laidOut = Device.onMain { view.isLaidOut }
        val reduce = Device.onMain { MalletTheme.of(view.context).reduceMotion }
        val enabled =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ValueAnimator.areAnimatorsEnabled().toString()
            else "not readable before Android 8.0"
        assertTrue(
            "$what drew $drawn frames; a glide needs a run (laidOut=$laidOut, " +
                "reduceMotion=$reduce, animatorsEnabled=$enabled)",
            drawn >= 6,
        )
    }
}
