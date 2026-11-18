package io.github.avalonskyafar.mallet.showcase.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.avalonskyafar.mallet.interaction.MalletExpandableLayout
import io.github.avalonskyafar.mallet.interaction.MalletPager
import io.github.avalonskyafar.mallet.interaction.MalletRevealLayout
import io.github.avalonskyafar.mallet.widget.MalletSwitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** One finger, several things that could move: rows that slide, sections that open, pagers, pages. */
@RunWith(AndroidJUnit4::class)
class GesturesDeviceTest : DeviceTest() {

    @Test
    fun aDetailPageIsDraggedBackToItsParent() {
        Device.launch("c_tracks")
        // The parent lies at the start side, so the finger moves towards the end.
        Device.swipeScreen(0.30f, 0.14f, 0.85f, 0.14f)
        Device.waitForPage("components")
    }

    @Test
    fun aRowSlidesAsideAndItsActionRemovesIt() {
        Device.launch("i_reveal")
        val rows = Device.all<MalletRevealLayout>()
        val row = rows.first()
        Device.bringToCentre(row)
        assertFalse(Device.onMain { row.isOpen })

        Device.drag(row, fromFx = 0.30f, toFx = 0.62f, steps = 40)
        Device.waitUntil("the row stays open") { row.isOpen }

        // The action stands in the strip the row has left free at its start side.
        Device.tap(row, fx = 0.09f)
        Device.waitUntil("the row is gone") { !row.isAttachedToWindow }
        assertEquals(rows.size - 1, Device.all<MalletRevealLayout>().size)
    }

    @Test
    fun aShortDragLetsTheRowGlideShut() {
        Device.launch("i_reveal")
        val row = Device.all<MalletRevealLayout>().first()
        Device.bringToCentre(row)
        // Less than two fifths of the strip.
        Device.drag(row, fromFx = 0.30f, toFx = 0.345f, steps = 40)
        Device.settle(600)
        assertFalse(Device.onMain { row.isOpen })
    }

    @Test
    fun aSwitchInsideASlidingRowKeepsItsOwnDrag() {
        Device.launch("i_reveal")
        // The third row starts with its switch off. A drag towards the end could turn the switch
        // on or slide the row aside; the switch is the deeper of the two and takes it.
        val row = Device.all<MalletRevealLayout>()[2]
        Device.bringToCentre(row)
        val switch = Device.all<MalletSwitch>(row).single()
        assertFalse(Device.onMain { switch.isChecked })

        Device.drag(switch, 0.2f, 0.8f, steps = 30)

        Device.waitUntil("the switch took the drag") { switch.isChecked }
        assertFalse("the row stayed where it was", Device.onMain { row.isOpen })
    }

    @Test
    fun aSectionOpensAndClosesUnderATap() {
        Device.launch("i_reveal")
        val section = Device.all<MalletExpandableLayout>()[1]
        Device.bringToCentre(section)
        assertFalse(Device.onMain { section.isExpanded })
        val closedHeight = Device.onMain { section.height }

        Device.tap(Device.onMain { section.header!! })
        Device.waitUntil("the section is open") { section.isExpanded && !section.isSettling }
        assertTrue(Device.onMain { section.height } > closedHeight)

        Device.tap(Device.onMain { section.header!! })
        Device.waitUntil("the section is closed again") { !section.isExpanded && !section.isSettling }
        assertEquals(closedHeight, Device.onMain { section.height })
    }

    @Test
    fun aPagerFollowsTheFingerAndItsLinkedHeadingStaysInStep() {
        Device.launch("i_pager")
        val (heading, pager) = Device.all<MalletPager>()
        Device.bringToCentre(pager)

        Device.drag(pager, fromFx = 0.8f, toFx = 0.2f, steps = 30)
        Device.waitUntil("the second page is shown") { pager.currentPage == 1 && !pager.isMoving }
        assertEquals(1, Device.onMain { heading.currentPage })

        // A short slow drag returns.
        Device.drag(pager, fromFx = 0.6f, toFx = 0.5f, steps = 60)
        Device.settle(600)
        assertEquals(1, Device.onMain { pager.currentPage })

        Device.drag(pager, fromFx = 0.2f, toFx = 0.8f, steps = 30)
        Device.waitUntil("back on the first page") { pager.currentPage == 0 && !pager.isMoving }
        assertEquals("i_pager", Device.currentPage())
    }

    @Test
    fun atItsFirstPageAPagerHandsTheDragToThePageAroundIt() {
        Device.launch("i_pager")
        val pager = Device.all<MalletPager>()[1]
        Device.bringToCentre(pager)
        assertEquals(0, Device.onMain { pager.currentPage })

        // There is no page before the first one; the drag moves the whole page to its parent.
        Device.drag(pager, fromFx = 0.2f, toFx = 0.85f, steps = 30)
        Device.waitForPage("interactions")
    }
}
