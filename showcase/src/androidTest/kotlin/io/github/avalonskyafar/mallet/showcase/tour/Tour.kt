package io.github.avalonskyafar.mallet.showcase.tour

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.avalonskyafar.mallet.showcase.ShowcaseSettings
import io.github.avalonskyafar.mallet.showcase.device.Device
import org.junit.Assume

/**
 * What the two tours share. A tour is not a test: it walks through the app on a device and
 * leaves pictures or clips behind, in [ROOT] on the device, for `tools/device.py` to collect.
 *
 * Tours run only when asked for (`-e tour true`), so that a plain `connectedAndroidTest`
 * stays a test run.
 */
internal object Tour {
    const val ROOT: String = "/data/local/tmp/mallet"

    /** How long a recording runs before its scene begins; `tools/device.py` cuts most of it off again. */
    const val RECORD_LEAD_MS: Long = 1800
    const val PACKAGE: String = "io.github.avalonskyafar.mallet.showcase"

    private val arguments get() = InstrumentationRegistry.getArguments()

    /**
     * How much slower than life the clips are made (`-e slow 3`). On an emulator that draws in
     * software the recorder gets few frames per second; gestures and pauses are stretched by
     * this factor, the system's animator scale is set to the same value by the script that
     * runs the tour, and the finished clip is played back faster by it again.
     */
    val slow: Float get() = arguments.getString("slow")?.toFloatOrNull()?.coerceIn(1f, 10f) ?: 1f

    private val recordSize: String get() = arguments.getString("size") ?: "720x1600"

    private val touchMarker = ActivityLifecycleCallback { activity, stage ->
        if (stage == Stage.RESUMED) TouchMarker.install(activity)
    }

    /** From now on every activity of the app shows where it is touched; see [TouchMarker]. */
    fun markTouches(on: Boolean) {
        val registry = ActivityLifecycleMonitorRegistry.getInstance()
        registry.removeLifecycleCallback(touchMarker)
        if (on) registry.addLifecycleCallback(touchMarker)
    }

    fun assumeRequested() {
        Assume.assumeTrue("pass -e tour true to run the tours", arguments.getString("tour") == "true")
    }

    fun shell(command: String): String = Device.ui.executeShellCommand(command)

    /** Opens [page] in a fresh app with the given appearance. */
    fun open(
        page: String,
        appearance: ShowcaseSettings.Appearance = ShowcaseSettings.Appearance.LIGHT,
        configure: ShowcaseSettings.() -> Unit = {},
    ) {
        Device.finishAll()
        Device.resetSettings(appearance)
        Device.onMain { Device.app.settings.configure() }
        Device.launch(page)
    }

    fun suffix(appearance: ShowcaseSettings.Appearance): String =
        if (appearance == ShowcaseSettings.Appearance.DARK) "dark" else "light"

    // ---- Pictures ----

    fun screenshot(name: String) {
        Device.settle(350)
        shell("screencap -p $ROOT/shots/$name.png")
    }

    /** A status bar without today's clock, battery and notifications. */
    fun demoMode(on: Boolean) {
        val broadcast = "am broadcast -a com.android.systemui.demo -e command"
        if (!on) {
            shell("$broadcast exit")
            return
        }
        shell("settings put global sysui_demo_allowed 1")
        shell("$broadcast enter")
        shell("$broadcast clock -e hhmm 0930")
        shell("$broadcast battery -e level 100 -e plugged false")
        shell("$broadcast network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4")
        shell("$broadcast notifications -e visible false")
        SystemClock.sleep(400)
    }

    /** The language the app itself runs in; an empty tag gives it back to the system. Android 13 and later. */
    fun appLanguage(tag: String) {
        Device.finishAll()
        if (tag.isEmpty()) shell("cmd locale set-app-locales $PACKAGE --user 0")
        else shell("cmd locale set-app-locales $PACKAGE --user 0 --locales $tag")
        Device.settle(900)
    }

    // ---- Clips ----

    private var recorder: Thread? = null

    /** A pause the viewer of a clip needs to see what happened. */
    fun pause(ms: Long) {
        SystemClock.sleep((ms * slow).toLong())
    }

    /** The duration of a gesture in a clip. */
    fun time(ms: Long): Long = (ms * slow).toLong()

    /** Records the screen into `motion/<name>.mp4` while [block] runs. */
    fun record(name: String, block: () -> Unit) {
        val file = "$ROOT/motion/$name.mp4"
        shell("rm -f $file")
        // The recorder runs until it is interrupted; its shell call blocks a thread of its own.
        val thread = Thread {
            runCatching { shell("screenrecord --size $recordSize --bit-rate 8000000 --time-limit 170 $file") }
        }
        recorder = thread
        thread.start()
        // The recorder needs a moment before it takes its first frame.
        SystemClock.sleep(RECORD_LEAD_MS)
        try {
            block()
            pause(400)
        } finally {
            shell("pkill -2 screenrecord")
            thread.join(15_000)
            recorder = null
            SystemClock.sleep(400)
        }
    }
}
