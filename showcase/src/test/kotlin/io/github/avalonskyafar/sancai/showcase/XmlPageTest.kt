package io.github.avalonskyafar.sancai.showcase

import android.app.Activity
import android.view.View
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.showcase.pages.XmlPage
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiControlRow
import io.github.avalonskyafar.sancai.widget.SancaiPage
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSwitch
import io.github.avalonskyafar.sancai.widget.SancaiText
import io.github.avalonskyafar.sancai.widget.SancaiTextField
import java.io.File
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * The XML demonstration: the page inflated from `res/layout/page_xml.xml`, the copy of that
 * file the build puts into the assets, and the activity whose whole screen is a layout file.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class XmlPageTest {

    private class Host(override val activity: Activity, override val settings: ShowcaseSettings) : ShowcaseHost {
        override fun open(key: String) = Unit
        override fun back() = Unit
        override fun say(message: CharSequence) = Unit
        override fun applyAppearance(source: View) = Unit
    }

    @After
    fun resetTheme() {
        SancaiTheme.resolver = null
    }

    private fun page(): Pair<Activity, View> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = Pages.create(XmlPage.key, Host(activity, ShowcaseSettings(activity)))
        activity.setContentView(view)
        ShadowLooper.idleMainLooper()
        return activity to view
    }

    @Test
    fun `the source on screen is the layout file that was inflated`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val packaged = XmlPage.layoutSample(activity)
        val written = XmlPage.sampleOf(File("src/main/res/layout/page_xml.xml").readText())

        assertThat(packaged).isNotNull()
        assertThat(packaged).isEqualTo(written)
        assertThat(packaged).contains("SancaiControlRow")
        assertThat(packaged).contains("app:sancaiOptions=\"@array/xml_levels\"")
        assertThat(packaged).doesNotContain("sample:")
        // The sample starts at the left edge and ends without a blank line.
        assertThat(packaged!!.first()).isEqualTo('<')
        assertThat(packaged.last()).isEqualTo('>')
    }

    @Test
    fun `a file without markers has no sample`() {
        assertThat(XmlPage.sampleOf("<LinearLayout />")).isNull()
        assertThat(XmlPage.sampleOf("${XmlPage.SAMPLE_END} x ${XmlPage.SAMPLE_BEGIN}")).isNull()
    }

    @Test
    fun `attributes of the layout reach the widgets`() {
        val (activity, view) = page()
        val strength = view.findViewById<SancaiControlRow>(R.id.xml_strength)
        val track = strength.track!!

        assertThat(strength.title.toString()).isEqualTo(activity.getString(R.string.xml_strength))
        assertThat(track.options.map { it.toString() })
            .containsExactlyElementsIn(activity.resources.getStringArray(R.array.xml_levels)).inOrder()
        assertThat(track.selectedIndex).isEqualTo(2)
        assertThat(view.findViewById<SancaiSwitch>(R.id.xml_wifi).isChecked).isTrue()
        val name = view.findViewById<SancaiTextField>(R.id.xml_name)
        assertThat(name.label.toString()).isEqualTo(activity.getString(R.string.xml_name))
        assertThat(name.counterMaxLength).isEqualTo(24)
        assertThat(name.endAction).isEqualTo(SancaiTextField.EndAction.CLEAR)
        assertThat(view.findViewById<SancaiButton>(R.id.xml_save).variant).isEqualTo(SancaiButton.Variant.PRIMARY)
    }

    @Test
    fun `saving needs a name and then reports what was declared`() {
        val (activity, view) = page()
        val status = view.findViewById<SancaiText>(R.id.xml_status)
        val name = view.findViewById<SancaiTextField>(R.id.xml_name)
        val save = view.findViewById<SancaiButton>(R.id.xml_save)

        save.performClick()
        ShadowLooper.idleMainLooper()
        assertThat(name.error.toString()).isEqualTo(activity.getString(R.string.xml_error_name))
        assertThat(status.text.toString()).isEqualTo(activity.getString(R.string.xml_status_idle))

        name.text = "  Weekdays "
        save.performClick()
        ShadowLooper.idleMainLooper()
        assertThat(name.error).isNull()
        val level = activity.resources.getStringArray(R.array.xml_levels)[2]
        assertThat(status.text.toString())
            .isEqualTo(activity.getString(R.string.xml_status_saved, "Weekdays", level))
    }

    @Test
    fun `the switch declared inside a row reports to the page`() {
        val (activity, view) = page()
        val status = view.findViewById<SancaiText>(R.id.xml_status)
        val wifi = view.findViewById<SancaiSwitch>(R.id.xml_wifi)

        wifi.performClick()
        ShadowLooper.idleMainLooper()

        assertThat(wifi.isChecked).isFalse()
        assertThat(status.text.toString()).isEqualTo(activity.getString(R.string.xml_status_wifi_off))
    }

    @Test
    fun `the neighbour screen is a layout file`() {
        val activity = Robolectric.buildActivity(NeighbourActivity::class.java).setup().get()
        ShadowLooper.idleMainLooper()

        val page = activity.findViewById<SancaiPage>(R.id.neighbour_page)
        assertThat(page).isNotNull()
        assertThat(page.content.childCount).isEqualTo(5)
        val track = activity.findViewById<SancaiSegmentedTrack>(R.id.neighbour_track)
        assertThat(track.options).hasSize(3)
        assertThat(track.selectedIndex).isEqualTo(1)
        assertThat(activity.findViewById<SancaiButton>(R.id.neighbour_back).hasOnClickListeners()).isTrue()
    }
}
