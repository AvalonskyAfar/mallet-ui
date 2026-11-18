package io.github.avalonskyafar.mallet.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconView
import io.github.avalonskyafar.mallet.interaction.MalletBack
import io.github.avalonskyafar.mallet.interaction.MalletExpandableLayout
import io.github.avalonskyafar.mallet.interaction.MalletHaptics
import io.github.avalonskyafar.mallet.interaction.MalletPager
import io.github.avalonskyafar.mallet.interaction.MalletRevealLayout
import io.github.avalonskyafar.mallet.interaction.MalletSpatialGraph
import io.github.avalonskyafar.mallet.interaction.MalletSpatialGraph.Side
import io.github.avalonskyafar.mallet.interaction.MalletSpatialHost
import io.github.avalonskyafar.mallet.interaction.MalletSpatialTransition
import io.github.avalonskyafar.mallet.interaction.MalletThemeTransition
import io.github.avalonskyafar.mallet.overlay.MalletBottomSheet
import io.github.avalonskyafar.mallet.overlay.MalletDialog
import io.github.avalonskyafar.mallet.overlay.MalletFloatingPanel
import io.github.avalonskyafar.mallet.overlay.MalletMenu
import io.github.avalonskyafar.mallet.overlay.MalletToast
import io.github.avalonskyafar.mallet.overlay.MalletToastView
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.testing.Touch
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.theme.MalletWindow
import io.github.avalonskyafar.mallet.widget.MalletBadge
import io.github.avalonskyafar.mallet.widget.MalletBanner
import io.github.avalonskyafar.mallet.widget.MalletButton
import io.github.avalonskyafar.mallet.widget.MalletCallout
import io.github.avalonskyafar.mallet.widget.MalletCheckbox
import io.github.avalonskyafar.mallet.widget.MalletChip
import io.github.avalonskyafar.mallet.widget.MalletChipGroup
import io.github.avalonskyafar.mallet.widget.MalletControlRow
import io.github.avalonskyafar.mallet.widget.MalletEntryRow
import io.github.avalonskyafar.mallet.widget.MalletFlowRow
import io.github.avalonskyafar.mallet.widget.MalletHeading
import io.github.avalonskyafar.mallet.widget.MalletKeyValueList
import io.github.avalonskyafar.mallet.widget.MalletLinkRow
import io.github.avalonskyafar.mallet.widget.MalletNavBar
import io.github.avalonskyafar.mallet.widget.MalletPageIndicator
import io.github.avalonskyafar.mallet.widget.MalletProgressBar
import io.github.avalonskyafar.mallet.widget.MalletRadioButton
import io.github.avalonskyafar.mallet.widget.MalletRadioGroup
import io.github.avalonskyafar.mallet.widget.MalletSegmentedTrack
import io.github.avalonskyafar.mallet.widget.MalletStateLayout
import io.github.avalonskyafar.mallet.widget.MalletStateView
import io.github.avalonskyafar.mallet.widget.MalletSurface
import io.github.avalonskyafar.mallet.widget.MalletTabs
import io.github.avalonskyafar.mallet.widget.MalletTag
import io.github.avalonskyafar.mallet.widget.MalletText
import io.github.avalonskyafar.mallet.widget.MalletTextField
import io.github.avalonskyafar.mallet.widget.MalletTopBar
import java.io.File
import java.lang.reflect.Modifier
import java.util.concurrent.TimeUnit
import kotlin.math.max
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * The whole library on older Android versions: Android 6 (API 23), the oldest it supports,
 * and two versions between that and the one the other tests run on.
 *
 * The other tests look at behaviour and at pixels on one version. Here the question is only
 * whether anything reaches for a part of the platform that an older version does not have:
 * every view is built, filled, laid out, drawn, touched, given keys, asked for its
 * accessibility node, saved, restored, disabled and focused; every overlay is opened and
 * closed; the window, back, haptics and transition helpers are called. A missing method or
 * constant ends the test with the error the device would raise.
 *
 * Lint checks the same statically (`NewApi` is an error in this module); this runs the code.
 * Drawing uses Robolectric's plain canvas here, so nothing is said about how it looks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 32])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class PlatformLevelsTest {

    @After
    fun tearDown() {
        MalletTheme.resolver = null
    }

    private fun idle(ms: Long = 700) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)
    }

    /** Every concrete public view class of the library, found in its sources. */
    private fun viewClasses(): List<Class<out View>> {
        val root = File("src/main/kotlin")
        val declared = Regex("""^public (?:open )?class (Mallet\w+)""", RegexOption.MULTILINE)
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            val pack = file.parentFile.relativeTo(root).path.replace(File.separatorChar, '.')
            declared.findAll(file.readText()).map { "$pack.${it.groupValues[1]}" }
        }.map { Class.forName(it) }
            .filter { View::class.java.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers) }
            .map { it.asSubclass(View::class.java) }
            .toList()
    }

    private fun create(type: Class<out View>, context: Context): View? =
        runCatching { type.getConstructor(Context::class.java) }.getOrNull()?.newInstance(context)

    /** Gives a view the content it needs to have something to measure, draw and operate. */
    private fun fill(view: View) {
        val context = view.context
        fun child(heightPx: Int = 120): View = View(context).apply { minimumHeight = heightPx }
        when (view) {
            is MalletSegmentedTrack -> view.setOptions("Off", "Gentle", "Standard", "Strict")
            is MalletTabs -> view.setTabs("All", "Calls", "Messages")
            is MalletNavBar -> {
                view.addItem(MalletIcon.HOME, "Home")
                view.addItem(MalletIcon.SLIDERS, "Settings")
                view.setBadge(1, 3)
            }
            is MalletPager -> view.setPages(3) { _, _ -> child() }
            is MalletPageIndicator -> view.count = 4
            is MalletKeyValueList -> {
                view.addItem("Version", "0.1.0")
                view.addItem("State", "Fine", MalletTextTone.SUCCESS)
            }
            is MalletFlowRow -> view.setSteps("One" to "first", "Two" to null)
            is MalletControlRow -> {
                view.title = "Strength"
                view.detail = "How strict the filter is"
                view.setSegmented(listOf("Off", "On"), 1, null)
            }
            is MalletEntryRow -> {
                view.title = "Accounts"
                view.detail = "Never filtered"
                view.setOnClickListener { }
            }
            is MalletLinkRow -> {
                view.label = "More"
                view.setOnClickListener { }
            }
            is MalletHeading -> {
                view.title = "Heading"
                view.detail = "Detail"
            }
            is MalletBanner -> {
                view.title = "Offline"
                view.detail = "Changes are kept on this device."
                view.setAction("Retry") { }
                view.show(animate = false)
            }
            is MalletCallout -> {
                view.title = "Note"
                view.body = "A line of explanation."
            }
            is MalletStateView -> {
                view.title = "Nothing here"
                view.detail = "Things you add appear here."
                view.setAction("Add") { }
            }
            is MalletStateLayout -> {
                view.addView(child(), FrameLayout.LayoutParams(-1, -2))
                view.setEmpty("Nothing here", "Add something", "Add") { }
                view.setState(MalletStateLayout.State.EMPTY, animate = false)
            }
            is MalletTopBar -> {
                view.title = "Title"
                view.subtitle = "Subtitle"
                view.setNavigation { }
                view.addAction(MalletIcon.SEARCH, "Search") { }
            }
            is MalletRevealLayout -> {
                view.addView(child(), FrameLayout.LayoutParams(-1, -2))
                view.setAction("Delete") { }
            }
            is MalletExpandableLayout -> {
                view.addView(child(), LinearLayout.LayoutParams(-1, -2))
                view.addView(child(), LinearLayout.LayoutParams(-1, -2))
                view.setExpanded(true, animate = false)
            }
            is MalletSpatialHost -> {
                val graph = MalletSpatialGraph.Builder("a").connect("a", Side.END, "b").build()
                view.setGraph(graph) { child(400) }
                view.show("a")
            }
            is MalletChipGroup -> for (label in listOf("One", "Two", "Three")) {
                view.addView(MalletChip(context).apply { text = label; kind = MalletChip.Kind.FILTER })
            }
            is MalletRadioGroup -> for (label in listOf("First", "Second")) {
                view.addView(MalletRadioButton(context).apply { text = label; id = View.generateViewId() })
            }
            is MalletToastView -> {
                view.message = "Saved"
                view.actionLabel = "Undo"
            }
            is MalletTextField -> {
                view.label = "Name"
                view.helperText = "As it appears in lists"
                view.text = "Home"
                view.error = "Too short"
            }
            is MalletCheckbox -> view.text = "Remember"
            is MalletRadioButton -> view.text = "Daily"
            is MalletChip -> view.text = "Chip"
            is MalletTag -> view.text = "New"
            is MalletBadge -> view.count = 12
            is MalletButton -> {
                view.text = "Save"
                view.icon = MalletIcon.CHECK
                view.setOnClickListener { }
            }
            is MalletProgressBar -> view.progress = 0.4f
            is MalletIconView -> view.icon = MalletIcon.STAR
            is MalletText -> view.text = "Text"
            is MalletSurface -> view.addView(child(), LinearLayout.LayoutParams(-1, -2))
            is MalletFloatingPanel -> view.addView(child(), FrameLayout.LayoutParams(300, 120))
        }
    }

    private fun exercise(view: View) {
        Snapshots.attach(view, 360)
        idle()
        val bitmap = Bitmap.createBitmap(max(1, view.width), max(1, view.height), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        val y = view.height / 2f
        Touch.tap(view, view.width / 2f, y)
        idle()
        Touch.drag(view, view.width * 0.2f, y, view.width * 0.8f, y)
        idle()
        Touch.drag(view, view.width * 0.8f, y, view.width * 0.2f, y, release = false)
        Touch.dispatch(view, android.view.MotionEvent.ACTION_CANCEL, view.width * 0.2f, y)
        idle()

        for (key in intArrayOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER)) {
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        }
        idle()

        view.createAccessibilityNodeInfo()
        // Views with parts of their own (the options of a track, the tabs of a row) describe them too.
        view.accessibilityNodeProvider?.let { provider ->
            val host = provider.createAccessibilityNodeInfo(View.NO_ID)
            if (host != null && host.childCount > 0) provider.createAccessibilityNodeInfo(0)
        }

        if (view.id == View.NO_ID) view.id = View.generateViewId()
        val state = SparseArray<Parcelable>()
        view.saveHierarchyState(state)
        view.restoreHierarchyState(state)

        view.isEnabled = false
        view.draw(canvas)
        view.isEnabled = true
        view.requestFocus()
        view.draw(canvas)
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        idle()
        view.draw(canvas)
        (view.parent as? ViewGroup)?.removeView(view)
        idle()
        bitmap.recycle()
    }

    @Test
    fun `every view lives through a whole life in the light and in the dark theme`() {
        // Without this flag of the application Android resolves every direction to
        // left-to-right, and the right-to-left part of the exercise would do nothing.
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val classes = viewClasses()
        assertThat(classes.map { it.simpleName }).containsAtLeast(
            "MalletSegmentedTrack", "MalletButton", "MalletTextField", "MalletSpatialHost", "MalletRevealLayout",
        )
        val failures = ArrayList<String>()
        val notBuilt = ArrayList<String>()
        for (theme in listOf(MalletTheme.light(), MalletTheme.dark())) {
            val context = Snapshots.context(theme)
            for (type in classes) {
                try {
                    val view = create(type, context)
                    if (view == null) {
                        notBuilt += type.simpleName
                        continue
                    }
                    fill(view)
                    exercise(view)
                } catch (error: Throwable) {
                    val cause = generateSequence(error) { it.cause }.last()
                    failures += "${type.simpleName} (${if (theme.isDark) "dark" else "light"}): $cause\n" +
                        cause.stackTrace.take(5).joinToString("\n") { "      at $it" }
                }
            }
        }
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
        // Every view of the library can be built from a context alone.
        assertWithMessage("views without a (Context) constructor").that(notBuilt.distinct()).isEmpty()
    }

    @Test
    fun `the steps of a flow row share its width equally without an exact width`() {
        // Android 6 measured weighted children of a wrap-content row differently; the row
        // measures twice to get the same columns everywhere.
        val row = MalletFlowRow(Snapshots.context())
        row.setSteps("Go" to null, "Review everything" to null)
        val unbounded = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        for (spec in listOf(unbounded, View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.AT_MOST))) {
            row.forceLayout()
            row.measure(spec, unbounded)
            row.layout(0, 0, row.measuredWidth, row.measuredHeight)
            val columns = (0 until row.childCount).map { row.getChildAt(it) }.filter { it !is MalletIconView }
            assertThat(columns).hasSize(2)
            assertThat(columns[0].width).isGreaterThan(0)
            assertThat(columns[1].width.toFloat()).isWithin(1f).of(columns[0].width.toFloat())
        }
    }

    @Test
    fun `overlays open and close`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val anchor = MalletButton(activity).apply { text = "More" }
        content.addView(anchor, FrameLayout.LayoutParams(-2, -2))
        idle()

        val dialog = MalletDialog.Builder(activity).title("Restore?").message("Everything returns.")
            .positive("Restore").negative().show()
        idle()
        dialog.dismiss()
        idle()

        val input = MalletDialog.Builder(activity).title("Name").input(hint = "Name") { _, _ -> true }.show()
        idle()
        input.dismiss()
        idle()

        val sheet = MalletBottomSheet.Builder(activity).title("Share")
            .action("Copy", icon = MalletIcon.COPY) { }
            .action("Delete", MalletTextTone.DANGER, MalletIcon.TRASH) { }
            .cancel().show()
        idle()
        sheet.dismiss()
        idle()

        val menu = MalletMenu(activity).add("Edit", MalletIcon.EDIT) { }.addDivider().addDanger("Delete") { }.setChecked(0)
        menu.show(anchor)
        idle()
        menu.dismiss()
        idle()

        MalletToast.show(activity, "Saved", MalletToast.Tone.SUCCESS)
        idle()
        MalletToast.Builder(activity).message("Deleted").action("Undo") { }.show()
        idle()
        MalletToast.error(activity, "Could not save")
        idle(4000)

        val panel = MalletFloatingPanel.attach(activity, View(activity).apply { minimumHeight = 80; minimumWidth = 200 })
        idle()
        panel.setPosition(1f, 0.3f)
        idle()
        panel.remove()
        idle()
    }

    @Test
    fun `window, back, haptics, themes and transitions use what the platform has`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        MalletWindow.apply(activity)
        MalletWindow.preferHighestRefreshRate(activity)
        val root = FrameLayout(activity)
        activity.setContentView(root)
        MalletWindow.padForSystemBars(root, includeIme = true)
        root.requestApplyInsets()
        idle()

        val theme = MalletTheme.of(activity)
        assertThat(theme.colors.canvas).isNotEqualTo(0)
        MalletTheme.systemIsDark(activity)
        MalletTheme.systemReducesMotion(activity)
        MalletTheme.resolver = MalletTheme.Resolver { MalletTheme.dark() }
        assertThat(MalletTheme.of(activity).isDark).isTrue()
        MalletTheme.resolver = null

        val graph = MalletSpatialGraph.Builder("a").connect("a", Side.END, "b").build()
        val host = MalletSpatialHost(activity).apply { setGraph(graph) { View(activity) } }
        root.addView(host, FrameLayout.LayoutParams(-1, -1))
        host.show("a")
        idle()
        val back = MalletBack.attach(activity, host)
        host.navigate("b")
        idle()
        assertThat(host.currentKey).isEqualTo("b")
        assertThat(back.onBackPressed()).isTrue()
        idle()
        assertThat(host.currentKey).isEqualTo("a")
        back.remove()

        MalletHaptics.tick(root)
        MalletHaptics.detent(root)
        MalletHaptics.confirm(root)
        MalletHaptics.reject(root)
        MalletHaptics.longPress(root)

        MalletThemeTransition.play(activity)
        idle()

        MalletSpatialTransition.start(activity, Intent(activity, Activity::class.java))
        idle()
        val second = Robolectric.buildActivity(Activity::class.java).setup().get()
        second.setContentView(MalletSpatialTransition.wrap(second, View(second)))
        idle()
        MalletSpatialTransition.finish(second)
        idle()
    }
}
