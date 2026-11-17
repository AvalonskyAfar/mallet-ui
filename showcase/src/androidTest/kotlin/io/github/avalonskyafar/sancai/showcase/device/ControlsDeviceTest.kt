package io.github.avalonskyafar.sancai.showcase.device

import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.widget.SancaiCheckbox
import io.github.avalonskyafar.sancai.widget.SancaiRadioButton
import io.github.avalonskyafar.sancai.widget.SancaiRadioGroup
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSlider
import io.github.avalonskyafar.sancai.widget.SancaiStepper
import io.github.avalonskyafar.sancai.widget.SancaiSwitch
import io.github.avalonskyafar.sancai.widget.SancaiText
import io.github.avalonskyafar.sancai.widget.SancaiTextField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The controls under a real finger: taps, drags that follow, drags that are handed on, holds, typing. */
@RunWith(AndroidJUnit4::class)
class ControlsDeviceTest : DeviceTest() {

    private fun selected(track: SancaiSegmentedTrack): Int = Device.onMain { track.selectedIndex }

    private fun textOnPage(text: String): Boolean =
        Device.all<SancaiText>().any { Device.onMain { it.text.toString() } == text }

    @Test
    fun aTrackTakesATapAndFollowsADragToItsEnd() {
        Device.launch("c_tracks")
        // The third track of the page has four options and starts on the third.
        val track = Device.all<SancaiSegmentedTrack>()[2]
        Device.bringToCentre(track)
        assertEquals(2, selected(track))

        Device.tap(track, fx = 0.125f)
        Device.waitUntil("the first option is selected") { track.selectedIndex == 0 }
        val first = Device.onMain { track.options[0].toString() }
        assertTrue(textOnPage(Device.string(R.string.tracks_status_level, first)))

        // The thumb is picked up where it rests and carried to the last option.
        Device.drag(track, fromFx = 0.125f, toFx = 0.875f, steps = 50)
        Device.waitUntil("the last option is selected") { track.selectedIndex == 3 }
        val last = Device.onMain { track.options[3].toString() }
        assertTrue(textOnPage(Device.string(R.string.tracks_status_level, last)))
    }

    @Test
    fun withAKeyboardFocusWalksIntoThePageAndATrackAnswersTheArrowKeys() {
        Device.launch("c_tracks")
        // The first track of the page has two options and starts on the second.
        val track = Device.all<SancaiSegmentedTrack>().first()
        assertEquals(1, selected(track))

        // A key leaves touch mode; further presses walk the focus down the page to the track.
        var presses = 0
        while (!Device.onMain { track.isFocused } && presses++ < 8) {
            Device.ui.pressDPadDown()
            Device.settle(150)
        }
        assertTrue("the first track took the focus", Device.onMain { track.isFocused })

        Device.ui.pressDPadLeft()
        Device.waitUntil("the left arrow chose the first option") { track.selectedIndex == 0 }
        Device.ui.pressDPadRight()
        Device.waitUntil("the right arrow chose the second option") { track.selectedIndex == 1 }
    }

    @Test
    fun aVerticalDragThatStartsOnATrackScrollsThePage() {
        Device.launch("c_tracks")
        val track = Device.all<SancaiSegmentedTrack>()[2]
        Device.bringToCentre(track)
        val scroll = Device.pageView() as ScrollView
        val before = Device.onMain { scroll.scrollY }
        val r = Device.bounds(track)

        Device.ui.swipe(r.centerX(), r.centerY(), r.centerX(), r.centerY() - r.height() * 4, 40)
        Device.settle(700)

        assertTrue("the page scrolled", Device.onMain { scroll.scrollY } > before)
        assertEquals("the track kept its value", 2, selected(track))
    }

    @Test
    fun aChangeTheListenerRefusesGlidesBack() {
        Device.launch("c_tracks")
        // The sixth track sends the thumb back when its last option is chosen.
        val track = Device.all<SancaiSegmentedTrack>()[5]
        Device.bringToCentre(track)
        val kept = selected(track)

        Device.tap(track, fx = 0.875f)
        Device.settle(900)

        assertEquals(kept, selected(track))
        val reason = Device.string(R.string.tracks_refuse_reason, Device.onMain { track.options[kept].toString() })
        val content = Device.onMain { Device.activity().findViewById<View>(android.R.id.content) }
        assertTrue("the reason is said", Device.all<TextView>(content).any { Device.onMain { it.text.toString() } == reason })
    }

    @Test
    fun aSwitchAnswersATapAndADrag() {
        Device.launch("c_tracks")
        val switch = Device.all<SancaiSwitch>().first { Device.onMain { it.isEnabled } }
        Device.bringToCentre(switch)
        val start = Device.onMain { switch.isChecked }

        Device.tap(switch)
        Device.waitUntil("the tap toggled the switch") { switch.isChecked != start }

        // Drag the thumb to the other side: towards the end for on, towards the start for off.
        if (start) Device.drag(switch, 0.25f, 0.8f, steps = 30) else Device.drag(switch, 0.75f, 0.2f, steps = 30)
        Device.waitUntil("the drag put the switch back") { switch.isChecked == start }

        // A disabled switch stays as it is.
        val locked = Device.all<SancaiSwitch>().first { Device.onMain { !it.isEnabled } }
        Device.bringToCentre(locked)
        val before = Device.onMain { locked.isChecked }
        Device.tap(locked)
        assertEquals(before, Device.onMain { locked.isChecked })
    }

    @Test
    fun aStepperStepsOnATapAndRepeatsWhileHeld() {
        Device.launch("c_numbers")
        val stepper = Device.all<SancaiStepper>().first()
        Device.bringToCentre(stepper)
        val start = Device.onMain { stepper.value }
        val step = Device.onMain { stepper.stepSize }

        Device.tap(stepper, fx = 0.87f)
        Device.waitUntil("plus added one step") { stepper.value == start + step }
        Device.tap(stepper, fx = 0.13f)
        Device.waitUntil("minus took it back") { stepper.value == start }

        Device.hold(stepper, fx = 0.87f, ms = 1600)
        assertTrue("holding repeated the step", Device.onMain { stepper.value } >= start + 3 * step)
    }

    @Test
    fun aSliderBeadFollowsTheFinger() {
        Device.launch("c_numbers")
        val slider = Device.all<SancaiSlider>().first()
        Device.bringToCentre(slider)
        val from = Device.onMain { slider.valueFrom }
        val to = Device.onMain { slider.valueTo }
        fun share() = Device.onMain { (slider.value - from) / (to - from) }

        Device.drag(slider, fromFx = share(), toFx = 0.8f, steps = 40)
        assertEquals(0.8f, share(), 0.08f)

        // A tap brings the bead to the finger.
        Device.tap(slider, fx = 0.2f)
        Device.waitUntil("the bead went to the tap") { kotlin.math.abs((slider.value - from) / (to - from) - 0.2f) < 0.08f }
    }

    @Test
    fun checkboxesAndRadiosFollowTheirRules() {
        Device.launch("c_choice")
        val boxes = Device.all<SancaiCheckbox>()
        val all = boxes[0]
        val options = boxes.subList(1, 4)
        Device.bringToCentre(options[1])

        Device.tap(all, fx = 0.1f)
        Device.waitUntil("\"select all\" checked the three options") { options.all { it.isChecked } && all.isChecked }
        Device.tap(options[2], fx = 0.1f)
        Device.waitUntil("unchecking one cleared \"select all\"") { !options[2].isChecked && !all.isChecked }

        val group = Device.all<SancaiRadioGroup>().first()
        val radios = Device.all<SancaiRadioButton>(group)
        val target = radios.first { Device.onMain { it.id } == R.id.choice_manual }
        Device.bringToCentre(target)
        Device.tap(target, fx = 0.1f)
        Device.waitUntil("the tapped radio is the choice") { group.checkedId == R.id.choice_manual }
        assertEquals(1, Device.onMain { radios.count { it.isChecked } })
        assertFalse(Device.onMain { radios.first { it.id == R.id.choice_daily }.isChecked })
    }

    @Test
    fun typingIntoAFieldClearsItsError() {
        Device.launch("c_fields")
        val label = Device.string(R.string.fields_code_label)
        val field = Device.all<SancaiTextField>().first { Device.onMain { it.label?.toString() } == label }
        Device.bringToCentre(field)
        val typedBefore = Device.onMain { field.editText.text.toString() }
        assertNotNull("the page opens with the error on screen", Device.onMain { field.error })

        // Focus the well, go to the end of the text and type until there are six digits.
        Device.tap(field.editText)
        Device.waitUntil("the field has focus") { field.editText.isFocused }
        Device.onMain { field.editText.setSelection(field.editText.length()) }
        val missing = "123456".take((6 - typedBefore.length).coerceAtLeast(0))
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStringSync(missing)
        Device.settle()

        assertEquals(typedBefore + missing, Device.onMain { field.editText.text.toString() })
        assertNull("six digits pass the rule", Device.onMain { field.error })
    }
}
