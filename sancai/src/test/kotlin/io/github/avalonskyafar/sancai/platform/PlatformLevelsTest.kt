package io.github.avalonskyafar.sancai.platform

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
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconView
import io.github.avalonskyafar.sancai.interaction.SancaiBack
import io.github.avalonskyafar.sancai.interaction.SancaiExpandableLayout
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.interaction.SancaiPager
import io.github.avalonskyafar.sancai.interaction.SancaiRevealLayout
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialGraph
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialGraph.Side
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialHost
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialTransition
import io.github.avalonskyafar.sancai.interaction.SancaiThemeTransition
import io.github.avalonskyafar.sancai.overlay.SancaiBottomSheet
import io.github.avalonskyafar.sancai.overlay.SancaiDialog
import io.github.avalonskyafar.sancai.overlay.SancaiFloatingPanel
import io.github.avalonskyafar.sancai.overlay.SancaiMenu
import io.github.avalonskyafar.sancai.overlay.SancaiToast
import io.github.avalonskyafar.sancai.overlay.SancaiToastView
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.theme.SancaiWindow
import io.github.avalonskyafar.sancai.widget.SancaiBadge
import io.github.avalonskyafar.sancai.widget.SancaiBanner
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiCallout
import io.github.avalonskyafar.sancai.widget.SancaiCheckbox
import io.github.avalonskyafar.sancai.widget.SancaiChip
import io.github.avalonskyafar.sancai.widget.SancaiChipGroup
import io.github.avalonskyafar.sancai.widget.SancaiControlRow
import io.github.avalonskyafar.sancai.widget.SancaiEntryRow
import io.github.avalonskyafar.sancai.widget.SancaiFlowRow
import io.github.avalonskyafar.sancai.widget.SancaiHeading
import io.github.avalonskyafar.sancai.widget.SancaiKeyValueList
import io.github.avalonskyafar.sancai.widget.SancaiLinkRow
import io.github.avalonskyafar.sancai.widget.SancaiNavBar
import io.github.avalonskyafar.sancai.widget.SancaiPageIndicator
import io.github.avalonskyafar.sancai.widget.SancaiProgressBar
import io.github.avalonskyafar.sancai.widget.SancaiRadioButton
import io.github.avalonskyafar.sancai.widget.SancaiRadioGroup
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiStateLayout
import io.github.avalonskyafar.sancai.widget.SancaiStateView
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiTabs
import io.github.avalonskyafar.sancai.widget.SancaiTag
import io.github.avalonskyafar.sancai.widget.SancaiText
import io.github.avalonskyafar.sancai.widget.SancaiTextField
import io.github.avalonskyafar.sancai.widget.SancaiTopBar
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
        SancaiTheme.resolver = null
    }

    private fun idle(ms: Long = 700) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)
    }

    /** Every concrete public view class of the library, found in its sources. */
    private fun viewClasses(): List<Class<out View>> {
        val root = File("src/main/kotlin")
        val declared = Regex("""^public (?:open )?class (Sancai\w+)""", RegexOption.MULTILINE)
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
            is SancaiSegmentedTrack -> view.setOptions("Off", "Gentle", "Standard", "Strict")
            is SancaiTabs -> view.setTabs("All", "Calls", "Messages")
            is SancaiNavBar -> {
                view.addItem(SancaiIcon.HOME, "Home")
                view.addItem(SancaiIcon.SLIDERS, "Settings")
                view.setBadge(1, 3)
            }
            is SancaiPager -> view.setPages(3) { _, _ -> child() }
            is SancaiPageIndicator -> view.count = 4
            is SancaiKeyValueList -> {
                view.addItem("Version", "0.1.0")
                view.addItem("State", "Fine", SancaiTextTone.SUCCESS)
            }
            is SancaiFlowRow -> view.setSteps("One" to "first", "Two" to null)
            is SancaiControlRow -> {
                view.title = "Strength"
                view.detail = "How strict the filter is"
                view.setSegmented(listOf("Off", "On"), 1, null)
            }
            is SancaiEntryRow -> {
                view.title = "Accounts"
                view.detail = "Never filtered"
                view.setOnClickListener { }
            }
            is SancaiLinkRow -> {
                view.label = "More"
                view.setOnClickListener { }
            }
            is SancaiHeading -> {
                view.title = "Heading"
                view.detail = "Detail"
            }
            is SancaiBanner -> {
                view.title = "Offline"
                view.detail = "Changes are kept on this device."
                view.setAction("Retry") { }
                view.show(animate = false)
            }
            is SancaiCallout -> {
                view.title = "Note"
                view.body = "A line of explanation."
            }
            is SancaiStateView -> {
                view.title = "Nothing here"
                view.detail = "Things you add appear here."
                view.setAction("Add") { }
            }
            is SancaiStateLayout -> {
                view.addView(child(), FrameLayout.LayoutParams(-1, -2))
                view.setEmpty("Nothing here", "Add something", "Add") { }
                view.setState(SancaiStateLayout.State.EMPTY, animate = false)
            }
            is SancaiTopBar -> {
                view.title = "Title"
                view.subtitle = "Subtitle"
                view.setNavigation { }
                view.addAction(SancaiIcon.SEARCH, "Search") { }
            }
            is SancaiRevealLayout -> {
                view.addView(child(), FrameLayout.LayoutParams(-1, -2))
                view.setAction("Delete") { }
            }
            is SancaiExpandableLayout -> {
                view.addView(child(), LinearLayout.LayoutParams(-1, -2))
                view.addView(child(), LinearLayout.LayoutParams(-1, -2))
                view.setExpanded(true, animate = false)
            }
            is SancaiSpatialHost -> {
                val graph = SancaiSpatialGraph.Builder("a").connect("a", Side.END, "b").build()
                view.setGraph(graph) { child(400) }
                view.show("a")
            }
            is SancaiChipGroup -> for (label in listOf("One", "Two", "Three")) {
                view.addView(SancaiChip(context).apply { text = label; kind = SancaiChip.Kind.FILTER })
            }
            is SancaiRadioGroup -> for (label in listOf("First", "Second")) {
                view.addView(SancaiRadioButton(context).apply { text = label; id = View.generateViewId() })
            }
            is SancaiToastView -> {
                view.message = "Saved"
                view.actionLabel = "Undo"
            }
            is SancaiTextField -> {
                view.label = "Name"
                view.helperText = "As it appears in lists"
                view.text = "Home"
                view.error = "Too short"
            }
            is SancaiCheckbox -> view.text = "Remember"
            is SancaiRadioButton -> view.text = "Daily"
            is SancaiChip -> view.text = "Chip"
            is SancaiTag -> view.text = "New"
            is SancaiBadge -> view.count = 12
            is SancaiButton -> {
                view.text = "Save"
                view.icon = SancaiIcon.CHECK
                view.setOnClickListener { }
            }
            is SancaiProgressBar -> view.progress = 0.4f
            is SancaiIconView -> view.icon = SancaiIcon.STAR
            is SancaiText -> view.text = "Text"
            is SancaiSurface -> view.addView(child(), LinearLayout.LayoutParams(-1, -2))
            is SancaiFloatingPanel -> view.addView(child(), FrameLayout.LayoutParams(300, 120))
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
        val classes = viewClasses()
        assertThat(classes.map { it.simpleName }).containsAtLeast(
            "SancaiSegmentedTrack", "SancaiButton", "SancaiTextField", "SancaiSpatialHost", "SancaiRevealLayout",
        )
        val failures = ArrayList<String>()
        val notBuilt = ArrayList<String>()
        for (theme in listOf(SancaiTheme.light(), SancaiTheme.dark())) {
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
    fun `overlays open and close`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val anchor = SancaiButton(activity).apply { text = "More" }
        content.addView(anchor, FrameLayout.LayoutParams(-2, -2))
        idle()

        val dialog = SancaiDialog.Builder(activity).title("Restore?").message("Everything returns.")
            .positive("Restore").negative().show()
        idle()
        dialog.dismiss()
        idle()

        val input = SancaiDialog.Builder(activity).title("Name").input(hint = "Name") { _, _ -> true }.show()
        idle()
        input.dismiss()
        idle()

        val sheet = SancaiBottomSheet.Builder(activity).title("Share")
            .action("Copy", icon = SancaiIcon.COPY) { }
            .action("Delete", SancaiTextTone.DANGER, SancaiIcon.TRASH) { }
            .cancel().show()
        idle()
        sheet.dismiss()
        idle()

        val menu = SancaiMenu(activity).add("Edit", SancaiIcon.EDIT) { }.addDivider().addDanger("Delete") { }.setChecked(0)
        menu.show(anchor)
        idle()
        menu.dismiss()
        idle()

        SancaiToast.show(activity, "Saved", SancaiToast.Tone.SUCCESS)
        idle()
        SancaiToast.Builder(activity).message("Deleted").action("Undo") { }.show()
        idle()
        SancaiToast.error(activity, "Could not save")
        idle(4000)

        val panel = SancaiFloatingPanel.attach(activity, View(activity).apply { minimumHeight = 80; minimumWidth = 200 })
        idle()
        panel.setPosition(1f, 0.3f)
        idle()
        panel.remove()
        idle()
    }

    @Test
    fun `window, back, haptics, themes and transitions use what the platform has`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        SancaiWindow.apply(activity)
        SancaiWindow.preferHighestRefreshRate(activity)
        val root = FrameLayout(activity)
        activity.setContentView(root)
        SancaiWindow.padForSystemBars(root, includeIme = true)
        root.requestApplyInsets()
        idle()

        val theme = SancaiTheme.of(activity)
        assertThat(theme.colors.canvas).isNotEqualTo(0)
        SancaiTheme.systemIsDark(activity)
        SancaiTheme.systemReducesMotion(activity)
        SancaiTheme.resolver = SancaiTheme.Resolver { SancaiTheme.dark() }
        assertThat(SancaiTheme.of(activity).isDark).isTrue()
        SancaiTheme.resolver = null

        val graph = SancaiSpatialGraph.Builder("a").connect("a", Side.END, "b").build()
        val host = SancaiSpatialHost(activity).apply { setGraph(graph) { View(activity) } }
        root.addView(host, FrameLayout.LayoutParams(-1, -1))
        host.show("a")
        idle()
        val back = SancaiBack.attach(activity, host)
        host.navigate("b")
        idle()
        assertThat(host.currentKey).isEqualTo("b")
        assertThat(back.onBackPressed()).isTrue()
        idle()
        assertThat(host.currentKey).isEqualTo("a")
        back.remove()

        SancaiHaptics.tick(root)
        SancaiHaptics.detent(root)
        SancaiHaptics.confirm(root)
        SancaiHaptics.reject(root)
        SancaiHaptics.longPress(root)

        SancaiThemeTransition.play(activity)
        idle()

        SancaiSpatialTransition.start(activity, Intent(activity, Activity::class.java))
        idle()
        val second = Robolectric.buildActivity(Activity::class.java).setup().get()
        second.setContentView(SancaiSpatialTransition.wrap(second, View(second)))
        idle()
        SancaiSpatialTransition.finish(second)
        idle()
    }
}
