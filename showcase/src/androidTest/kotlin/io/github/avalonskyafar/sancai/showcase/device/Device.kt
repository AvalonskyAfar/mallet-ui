package io.github.avalonskyafar.sancai.showcase.device

import android.app.Activity
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialHost
import io.github.avalonskyafar.sancai.showcase.MainActivity
import io.github.avalonskyafar.sancai.showcase.Pages
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.showcase.ShowcaseApp
import io.github.avalonskyafar.sancai.showcase.ShowcaseSettings
import io.github.avalonskyafar.sancai.widget.SancaiButton
import kotlin.math.roundToInt

/**
 * What the device tests share: starting the app on a page, waiting for it to come to rest,
 * finding views of a page, and touching the screen the way a finger does.
 *
 * Input goes through [UiDevice], so it travels the whole way a real touch does: the system's
 * input dispatcher, the window, the view tree. State is read from the views on the main thread.
 */
internal object Device {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    val ui: UiDevice get() = UiDevice.getInstance(instrumentation)

    val app: ShowcaseApp get() = instrumentation.targetContext.applicationContext as ShowcaseApp

    fun string(id: Int, vararg arguments: Any): String = instrumentation.targetContext.getString(id, *arguments)

    /** The string as the screen in front shows it: in the language the app runs in right now. */
    fun shown(id: Int, vararg arguments: Any): String = onMain { activity().getString(id, *arguments) }

    /** Runs [block] on the main thread and returns its result. May be called from the main thread itself. */
    fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    /** Waits until [condition], asked on the main thread, holds; fails with [what] after [timeoutMs]. */
    fun waitUntil(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            if (onMain(condition)) return
            if (SystemClock.uptimeMillis() > end) throw AssertionError("timed out after $timeoutMs ms waiting until $what")
            SystemClock.sleep(40)
        }
    }

    /** Lets animations that are under way finish and the main thread go idle. */
    fun settle(ms: Long = 450) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(ms)
        instrumentation.waitForIdleSync()
    }

    /** Puts the settings of the app back to what a fresh install has, with a fixed appearance. */
    fun resetSettings(appearance: ShowcaseSettings.Appearance = ShowcaseSettings.Appearance.LIGHT) = onMain {
        val settings = app.settings
        settings.appearance = appearance
        settings.glazeSet = ShowcaseSettings.GlazeSet.CLASSIC
        settings.largeText = false
        settings.highContrast = false
        settings.reduceMotion = false
        settings.haptics = true
    }

    /** Starts the app on [page] in a task of its own and waits until the page rests. */
    fun launch(page: String = Pages.HOME): MainActivity {
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(MainActivity.EXTRA_PAGE, page)
        val activity = instrumentation.startActivitySync(intent) as MainActivity
        waitForPage(page)
        return activity
    }

    /** The activity the user is looking at. */
    fun activity(): Activity = onMain {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
            ?: throw AssertionError("no activity is resumed")
    }

    fun resumedOrNull(): Activity? = onMain {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
    }

    /** Finishes every activity of the app, so the next test starts from nothing. */
    fun finishAll() {
        onMain {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            for (stage in Stage.values()) {
                if (stage == Stage.DESTROYED) continue
                registry.getActivitiesInStage(stage).forEach { it.finish() }
            }
        }
        settle(200)
    }

    fun host(): SancaiSpatialHost = onMain { activity().findViewById(R.id.showcase_host) }

    fun currentPage(): String? = onMain { (activity() as? MainActivity)?.findViewById<SancaiSpatialHost>(R.id.showcase_host)?.currentKey }

    /** Waits until the host rests on [page]. */
    fun waitForPage(page: String) {
        waitUntil("the app rests on the page \"$page\"") {
            val host = (ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull() as? MainActivity)
                ?.findViewById<SancaiSpatialHost>(R.id.showcase_host)
            host != null && host.currentKey == page && !host.isMoving && host.width > 0
        }
        settle(250)
    }

    /** The view of the page the host rests on. */
    fun pageView(): View = onMain {
        val host = activity().findViewById<SancaiSpatialHost>(R.id.showcase_host)
        (0 until host.childCount).map { host.getChildAt(it) }
            .last { it.visibility == View.VISIBLE && it.translationX == 0f && it.translationY == 0f }
    }

    /** Every view of type [T] under [root], in drawing order. */
    inline fun <reified T : View> all(root: View = pageView()): List<T> = all(root, T::class.java)

    /** Every visible view of the class [type] under [root], in drawing order. */
    fun <T : View> all(root: View, type: Class<T>): List<T> = onMain {
        val found = ArrayList<T>()
        fun walk(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (type.isInstance(view)) found.add(type.cast(view)!!)
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        found
    }

    /** Where [view] is on the screen. */
    fun bounds(view: View): Rect = onMain {
        val origin = IntArray(2)
        view.getLocationOnScreen(origin)
        Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
    }

    /**
     * Scrolls the page so that [view] stands in the middle of the screen, clear of the system
     * bars. This is the test arranging the screen, not a gesture under test.
     */
    fun bringToCentre(view: View) {
        onMain {
            var parent = view.parent
            while (parent != null && parent !is ScrollView) parent = parent.parent
            val scroll = parent as? ScrollView ?: return@onMain
            val rect = Rect(0, 0, view.width, view.height)
            scroll.offsetDescendantRectToMyCoords(view, rect)
            val range = (scroll.getChildAt(0).height - (scroll.height - scroll.paddingTop - scroll.paddingBottom)).coerceAtLeast(0)
            scroll.scrollTo(0, (rect.centerY() - scroll.height / 2).coerceIn(0, range))
        }
        settle(200)
    }

    /** A tap at the given share of the width and height of [view]. */
    fun tap(view: View, fx: Float = 0.5f, fy: Float = 0.5f) {
        val r = bounds(view)
        ui.click((r.left + r.width() * fx).roundToInt(), (r.top + r.height() * fy).roundToInt())
        settle()
    }

    /** A finger that goes down on [view] at [fromFx], travels to [toFx] and lifts. A step lasts 5 ms. */
    fun drag(view: View, fromFx: Float, toFx: Float, fy: Float = 0.5f, steps: Int = 30) {
        val r = bounds(view)
        val y = (r.top + r.height() * fy).roundToInt()
        ui.swipe((r.left + r.width() * fromFx).roundToInt(), y, (r.left + r.width() * toFx).roundToInt(), y, steps)
        settle()
    }

    /** A finger that rests on one point of [view] for [ms]. */
    fun hold(view: View, fx: Float, ms: Long, fy: Float = 0.5f) {
        val r = bounds(view)
        val x = (r.left + r.width() * fx).roundToInt()
        val y = (r.top + r.height() * fy).roundToInt()
        ui.swipe(x, y, x, y, (ms / 5).toInt())
        settle()
    }

    /** A swipe across the screen, in shares of its width and height. */
    fun swipeScreen(fromFx: Float, fromFy: Float, toFx: Float, toFy: Float, steps: Int = 30) {
        val w = ui.displayWidth
        val h = ui.displayHeight
        ui.swipe((w * fromFx).roundToInt(), (h * fromFy).roundToInt(), (w * toFx).roundToInt(), (h * toFy).roundToInt(), steps)
    }

    /** Wakes the device and makes sure nothing covers the app. */
    fun prepareScreen() {
        ui.wakeUp()
        ui.executeShellCommand("wm dismiss-keyguard")
        // With a hardware keyboard attached (every emulator has one) the soft keyboard would
        // only cover the lower half of the screen; text is typed as key events anyway.
        ui.executeShellCommand("settings put secure show_ime_with_hard_keyboard 0")
        ui.setOrientationNatural()
    }

    // ---- Things in other windows: dialogs, sheets, menus ----

    /** Something on the screen, in any window, that shows or is described by [label]. */
    fun onScreen(label: String): UiObject2? = ui.findObject(By.text(label)) ?: ui.findObject(By.desc(label))

    /** Waits until [label] is on the screen and returns what carries it. */
    fun waitForText(label: String, timeoutMs: Long = 8_000): UiObject2 {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            onScreen(label)?.let { return it }
            SystemClock.sleep(60)
        }
        throw AssertionError("\"$label\" did not appear on the screen")
    }

    /** Waits until nothing on the screen carries [label] any more. */
    fun waitTextGone(label: String, timeoutMs: Long = 8_000) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (onScreen(label) == null) return
            SystemClock.sleep(60)
        }
        throw AssertionError("\"$label\" stayed on the screen")
    }

    /** Brings the button of the page that carries the text [label] to the middle and taps it. */
    fun pressButton(label: Int) {
        val text = shown(label)
        val button = all<SancaiButton>().firstOrNull { onMain { it.text.toString() } == text }
            ?: throw AssertionError("the page has no button \"$text\"")
        bringToCentre(button)
        tap(button)
    }

    /**
     * Scrolls the page so that the top of [view] stands [marginDp] below the top of the
     * scrolling area. Like [bringToCentre], this arranges the screen; it is not a gesture.
     */
    fun bringToTop(view: View, marginDp: Int = 16) {
        onMain {
            var parent = view.parent
            while (parent != null && parent !is ScrollView) parent = parent.parent
            val scroll = parent as? ScrollView ?: return@onMain
            val rect = Rect(0, 0, view.width, view.height)
            scroll.offsetDescendantRectToMyCoords(view, rect)
            val range = (scroll.getChildAt(0).height - (scroll.height - scroll.paddingTop - scroll.paddingBottom)).coerceAtLeast(0)
            val margin = (marginDp * view.resources.displayMetrics.density).roundToInt()
            scroll.scrollTo(0, (rect.top - scroll.paddingTop - margin).coerceIn(0, range))
        }
        settle(200)
    }

    /**
     * A finger that speeds up, travels and slows down again, the way a hand moves: it goes
     * down at the first point, takes [ms] to reach the second and lifts there at rest.
     * Positions are in pixels of the screen.
     */
    fun glide(fromX: Int, fromY: Int, toX: Int, toY: Int, ms: Long = 600) {
        val count = (ms / 10).toInt().coerceAtLeast(4)
        val points = Array(count + 1) { index ->
            val t = index / count.toFloat()
            val eased = t * t * (3f - 2f * t)
            Point((fromX + (toX - fromX) * eased).roundToInt(), (fromY + (toY - fromY) * eased).roundToInt())
        }
        // Two steps of 5 ms between neighbouring points.
        ui.swipe(points, 2)
    }

    /**
     * A finger that goes down, glides to the second point and stays there while [held] runs;
     * then it lifts. For looking at something half-way through a gesture. Only for windows of
     * this app, and not to be called on the main thread.
     */
    fun dragAndHold(fromX: Int, fromY: Int, toX: Int, toY: Int, ms: Long = 450, held: () -> Unit) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, fromX.toFloat(), fromY.toFloat())
        val count = (ms / 10).toInt().coerceAtLeast(4)
        for (index in 1..count) {
            val t = index / count.toFloat()
            val eased = t * t * (3f - 2f * t)
            send(MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * eased, fromY + (toY - fromY) * eased)
            SystemClock.sleep(10)
        }
        try {
            held()
        } finally {
            send(MotionEvent.ACTION_UP, toX.toFloat(), toY.toFloat())
        }
    }

    /** [glide] across [view], in shares of its width and height. */
    fun glide(view: View, fromFx: Float, toFx: Float, fy: Float = 0.5f, ms: Long = 600) {
        val r = bounds(view)
        val y = (r.top + r.height() * fy).roundToInt()
        glide((r.left + r.width() * fromFx).roundToInt(), y, (r.left + r.width() * toFx).roundToInt(), y, ms)
    }

    /** [glide] across the screen, in shares of its width and height. */
    fun glideScreen(fromFx: Float, fromFy: Float, toFx: Float, toFy: Float, ms: Long = 600) {
        val w = ui.displayWidth
        val h = ui.displayHeight
        glide((w * fromFx).roundToInt(), (h * fromFy).roundToInt(), (w * toFx).roundToInt(), (h * toFy).roundToInt(), ms)
    }
}
