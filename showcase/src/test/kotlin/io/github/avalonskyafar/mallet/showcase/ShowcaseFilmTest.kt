package io.github.avalonskyafar.mallet.showcase

import android.content.Intent
import android.view.View
import android.widget.ScrollView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.interaction.MalletExpandableLayout
import io.github.avalonskyafar.mallet.interaction.MalletPager
import io.github.avalonskyafar.mallet.interaction.MalletRevealLayout
import io.github.avalonskyafar.mallet.interaction.MalletSpatialHost
import io.github.avalonskyafar.mallet.widget.MalletEntryRow
import io.github.avalonskyafar.mallet.widget.MalletPowerSwitch
import io.github.avalonskyafar.mallet.widget.MalletSegmentedTrack
import io.github.avalonskyafar.mallet.widget.MalletSlider
import io.github.avalonskyafar.mallet.widget.MalletStepper
import io.github.avalonskyafar.mallet.widget.MalletSwitch
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Films of the real app, made on the JVM: the activity of the showcase is started, touched
 * and drawn frame by frame (see [Film]). Each test leaves one film in `build/films`.
 *
 * These are not screen recordings. They show what the code of the app and of the library
 * draws for a given sequence of touches, with an exact clock, which is why they are smooth;
 * they do not show a device, its status bar or its fonts.
 *
 * The films are only made when asked for: `./gradlew :showcase:testDebugUnitTest -PmalletFilms`.
 * Every film also asserts where the app ended up, so a gesture that stops working fails here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShowcaseFilmTest {

    @Before
    fun onlyWhenAskedFor() {
        Assume.assumeTrue("pass -PmalletFilms to make the films", System.getProperty("mallet.films") != null)
    }

    private val app: ShowcaseApp get() = ApplicationProvider.getApplicationContext()

    private fun start(page: String, dark: Boolean = false): MainActivity {
        app.settings.appearance = if (dark) ShowcaseSettings.Appearance.DARK else ShowcaseSettings.Appearance.LIGHT
        val intent = Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAGE, page)
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup().get()
        ShadowLooper.idleMainLooper()
        return activity
    }

    private fun host(activity: MainActivity): MalletSpatialHost = activity.findViewById(R.id.showcase_host)

    /** The view of the page the host rests on. */
    private fun page(activity: MainActivity): View {
        val host = host(activity)
        return (0 until host.childCount).map { host.getChildAt(it) }
            .last { it.visibility == View.VISIBLE && it.translationX == 0f && it.translationY == 0f }
    }

    /** Scrolls the page so that [view] stands in the middle of the window; no film is taken. */
    private fun centre(activity: MainActivity, view: View) {
        val scroll = page(activity) as ScrollView
        val rect = android.graphics.Rect(0, 0, view.width, view.height)
        scroll.offsetDescendantRectToMyCoords(view, rect)
        val range = (scroll.getChildAt(0).height - scroll.height + scroll.paddingTop + scroll.paddingBottom).coerceAtLeast(0)
        scroll.scrollTo(0, (rect.centerY() - scroll.height / 2).coerceIn(0, range))
        ShadowLooper.idleMainLooper()
    }

    /** Scrolls the page so that [first] and [last] both stand in the window, as near its middle as they fit. */
    private fun frame(activity: MainActivity, first: View, last: View) {
        val scroll = page(activity) as ScrollView
        val top = android.graphics.Rect(0, 0, first.width, first.height)
        val bottom = android.graphics.Rect(0, 0, last.width, last.height)
        scroll.offsetDescendantRectToMyCoords(first, top)
        scroll.offsetDescendantRectToMyCoords(last, bottom)
        val range = (scroll.getChildAt(0).height - scroll.height + scroll.paddingTop + scroll.paddingBottom).coerceAtLeast(0)
        scroll.scrollTo(0, ((top.top + bottom.bottom) / 2 - scroll.height / 2).coerceIn(0, range))
        ShadowLooper.idleMainLooper()
        val window = IntArray(2)
        first.getLocationInWindow(window)
        check(window[1] >= 0) { "the first view does not fit into the window together with the last" }
    }

    /** Like [frame], but the page glides there while the film runs. */
    private fun glideTo(activity: MainActivity, film: Film, first: View, last: View) {
        val scroll = page(activity) as ScrollView
        val top = android.graphics.Rect(0, 0, first.width, first.height)
        val bottom = android.graphics.Rect(0, 0, last.width, last.height)
        scroll.offsetDescendantRectToMyCoords(first, top)
        scroll.offsetDescendantRectToMyCoords(last, bottom)
        val range = (scroll.getChildAt(0).height - scroll.height + scroll.paddingTop + scroll.paddingBottom).coerceAtLeast(0)
        scroll.smoothScrollTo(0, ((top.top + bottom.bottom) / 2 - scroll.height / 2).coerceIn(0, range))
        film.hold(900)
    }

    private fun places(name: String, dark: Boolean) {
        val activity = start(Pages.HOME, dark)
        val film = Film(activity, name)
        val w = film.width.toFloat()
        val h = film.height.toFloat()
        val host = host(activity)
        film.hold(500)
        film.drag(w * 0.80f, h * 0.27f, w * 0.22f, h * 0.27f)
        film.hold(900)
        assertThat(host.currentKey).isEqualTo("interactions")
        film.drag(w * 0.22f, h * 0.20f, w * 0.80f, h * 0.20f)
        film.hold(800)
        assertThat(host.currentKey).isEqualTo(Pages.HOME)
        film.drag(w * 0.5f, h * 0.80f, w * 0.5f, h * 0.34f)
        film.hold(900)
        assertThat(host.currentKey).isEqualTo("components")
        film.tap(Film.all<MalletEntryRow>(page(activity)).first())
        film.hold(1100)
        assertThat(host.currentKey).isEqualTo(Pages.components.first().key)
        film.drag(w * 0.25f, h * 0.14f, w * 0.82f, h * 0.14f)
        film.hold(800)
        assertThat(host.currentKey).isEqualTo("components")
        film.drag(w * 0.5f, h * 0.22f, w * 0.5f, h * 0.66f)
        film.hold(800)
        assertThat(host.currentKey).isEqualTo(Pages.HOME)
    }

    @Test
    fun `pages are places`() = places("places", dark = false)

    @Test
    fun `pages are places, at night`() = places("places_dark", dark = true)

    private fun tracks(name: String, dark: Boolean) {
        val activity = start("c_tracks", dark)
        val tracks = Film.all<MalletSegmentedTrack>(page(activity))
        val four = tracks[2]
        val film = Film(activity, name)
        film.hold(400)
        film.drag(four, 0.625f, 0.125f, ms = 800)
        film.hold(600)
        assertThat(four.selectedIndex).isEqualTo(0)
        film.drag(four, 0.125f, 0.875f, ms = 1100)
        film.hold(600)
        assertThat(four.selectedIndex).isEqualTo(3)
        film.tap(four, fx = 0.375f)
        film.hold(600)
        assertThat(four.selectedIndex).isEqualTo(1)
        film.tap(tracks[1], fx = 0.83f)
        film.hold(500)
        film.tap(tracks[0], fx = 0.25f)
        film.hold(600)
        assertThat(tracks[1].selectedIndex).isEqualTo(2)
        assertThat(tracks[0].selectedIndex).isEqualTo(0)
    }

    @Test
    fun `a thumb takes the hue of where it is`() = tracks("tracks", dark = false)

    @Test
    fun `a thumb takes the hue of where it is, at night`() = tracks("tracks_dark", dark = true)

    @Test
    fun `switches follow the finger`() {
        val activity = start("c_tracks")
        val switches = Film.all<MalletSwitch>(page(activity))
        val power = Film.all<MalletPowerSwitch>(page(activity)).first()
        frame(activity, switches[0], power)
        val film = Film(activity, "switches")
        film.hold(400)
        film.tap(switches[0])
        film.hold(450)
        assertThat(switches[0].isChecked).isFalse()
        film.drag(switches[1], 0.25f, 0.80f, ms = 500)
        film.hold(450)
        assertThat(switches[1].isChecked).isTrue()
        film.tap(switches[2])
        film.hold(500)
        film.drag(power, 0.80f, 0.22f, ms = 800)
        film.hold(700)
        assertThat(power.isChecked).isFalse()
        // Not far enough: the thumb glides back to where it came from.
        film.drag(power, 0.22f, 0.40f, ms = 600)
        film.hold(700)
        assertThat(power.isChecked).isFalse()
        film.drag(power, 0.22f, 0.80f, ms = 800)
        film.hold(700)
        assertThat(power.isChecked).isTrue()
    }

    @Test
    fun `sliders are coloured along a scale and steppers step`() {
        val activity = start("c_numbers")
        val sliders = Film.all<MalletSlider>(page(activity))
        val steppers = Film.all<MalletStepper>(page(activity))
        val stepper = steppers.first()
        frame(activity, steppers.first(), steppers.last())
        val film = Film(activity, "sliders")
        film.hold(400)
        val before = stepper.value
        film.tap(stepper, fx = 0.87f)
        film.hold(300)
        film.tap(stepper, fx = 0.87f)
        film.hold(300)
        // Held: the step repeats.
        film.tap(stepper, fx = 0.87f, ms = 1300)
        film.hold(500)
        assertThat(stepper.value).isGreaterThan(before + 2 * stepper.stepSize)
        glideTo(activity, film, sliders[0], sliders[2])
        film.drag(sliders[0], 0.40f, 0.86f, ms = 900)
        film.hold(400)
        film.drag(sliders[1], 0.40f, 0.80f, ms = 900)
        film.hold(400)
        film.drag(sliders[2], 0.36f, 0.95f, ms = 1100)
        film.hold(500)
        assertThat(sliders[2].value).isEqualTo(sliders[2].valueTo)
        film.drag(sliders[2], 0.95f, 0.05f, ms = 1300)
        film.hold(700)
        assertThat(sliders[2].value).isEqualTo(sliders[2].valueFrom)
    }

    @Test
    fun `rows slide aside and sections open`() {
        val activity = start("i_reveal")
        val rows = Film.all<MalletRevealLayout>(page(activity))
        val sections = Film.all<MalletExpandableLayout>(page(activity))
        val film = Film(activity, "reveal")
        film.hold(400)
        film.drag(rows[0], 0.30f, 0.64f, ms = 700)
        film.hold(700)
        assertThat(rows[0].isOpen).isTrue()
        // Opening another row closes the first.
        film.drag(rows[2], 0.30f, 0.64f, ms = 700)
        film.hold(700)
        assertThat(rows[0].isOpen).isFalse()
        assertThat(rows[2].isOpen).isTrue()
        film.tap(rows[2], fx = 0.09f)
        film.hold(900)
        assertThat(Film.all<MalletRevealLayout>(page(activity))).hasSize(rows.size - 1)
        glideTo(activity, film, sections.first(), sections.last())
        film.tap(sections[1].header!!)
        film.hold(800)
        assertThat(sections[1].isExpanded).isTrue()
        film.tap(sections[2].header!!)
        film.hold(800)
        film.tap(sections[0].header!!)
        film.hold(800)
        assertThat(sections[0].isExpanded).isFalse()
    }

    @Test
    fun `two pagers turn together`() {
        val activity = start("i_pager")
        val (heading, pager) = Film.all<MalletPager>(page(activity))
        val film = Film(activity, "pager")
        film.hold(400)
        film.drag(pager, 0.80f, 0.20f, ms = 700)
        film.hold(600)
        assertThat(pager.currentPage).isEqualTo(1)
        assertThat(heading.currentPage).isEqualTo(1)
        film.drag(pager, 0.80f, 0.20f, ms = 700)
        film.hold(600)
        film.drag(heading, 0.25f, 0.80f, ms = 700)
        film.hold(600)
        assertThat(pager.currentPage).isEqualTo(1)
        // Not far enough: the page glides back.
        film.drag(pager, 0.70f, 0.56f, ms = 600)
        film.hold(600)
        assertThat(pager.currentPage).isEqualTo(1)
        film.drag(pager, 0.25f, 0.80f, ms = 700)
        film.hold(700)
        assertThat(pager.currentPage).isEqualTo(0)
    }
}
