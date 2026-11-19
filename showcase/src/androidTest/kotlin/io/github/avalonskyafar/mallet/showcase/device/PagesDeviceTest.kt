package io.github.avalonskyafar.mallet.showcase.device

import android.graphics.Bitmap
import android.view.PixelCopy
import android.view.View
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import io.github.avalonskyafar.mallet.showcase.Pages
import io.github.avalonskyafar.mallet.showcase.ShowcaseSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens every page of the app on the device, in the light and in the dark theme, and scrolls
 * it from top to bottom, so that each view is laid out and drawn by the real renderer once.
 *
 * The JVM tests draw the pages in software; this is the hardware-accelerated counterpart.
 * A page that throws while it is built, laid out or drawn ends the test with that exception.
 */
@RunWith(AndroidJUnit4::class)
class PagesDeviceTest : DeviceTest() {

    private fun visit(appearance: ShowcaseSettings.Appearance) {
        Device.resetSettings(appearance)
        for (key in Pages.graph.keys.sorted()) {
            Device.finishAll()
            Device.launch(key)
            val page = Device.pageView()
            assertTrue("$key has a size", Device.onMain { page.width > 0 && page.height > 0 })
            val scroll = page as? ScrollView ?: continue
            // Android before 8.0 hands the focus to the first text field or selectable text
            // of a window that opens, and a scroll view scrolls to it. A page opens at its top.
            assertEquals("$key opens at its top", 0, Device.onMain { scroll.scrollY })
            // Down in steps of a screen, so every part of the page is drawn, and back up.
            var guard = 0
            while (Device.onMain { scroll.canScrollVertically(1) } && guard++ < 40) {
                Device.onMain { scroll.scrollBy(0, scroll.height * 3 / 4) }
                Device.settle(60)
            }
            Device.onMain { scroll.scrollTo(0, 0) }
            Device.settle(60)
            assertTrue("$key is still attached", Device.onMain { page.isAttachedToWindow })
        }
    }

    @Test
    fun everyPageOpensAndDrawsInTheLightTheme() = visit(ShowcaseSettings.Appearance.LIGHT)

    @Test
    fun everyPageOpensAndDrawsInTheDarkTheme() = visit(ShowcaseSettings.Appearance.DARK)

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun theWindowIsNotBlank() {
        Device.launch(Pages.HOME)
        val activity = Device.activity()
        val root: View = Device.onMain { activity.window.decorView }
        val bitmap = Bitmap.createBitmap(Device.onMain { root.width }, Device.onMain { root.height }, Bitmap.Config.ARGB_8888)
        val done = java.util.concurrent.CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        val thread = android.os.HandlerThread("pixel-copy").apply { start() }
        PixelCopy.request(activity.window, bitmap, { code -> result = code; done.countDown() }, android.os.Handler(thread.looper))
        assertTrue("the window was copied in time", done.await(5, java.util.concurrent.TimeUnit.SECONDS))
        thread.quitSafely()
        assertTrue("PixelCopy succeeded", result == PixelCopy.SUCCESS)

        // The home page has a white canvas with ink, a coloured power switch and muted labels on it.
        val seen = HashSet<Int>()
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                seen.add(bitmap.getPixel(x, y))
                x += 6
            }
            y += 6
        }
        assertTrue("the window shows more than a flat colour (${seen.size} colours)", seen.size > 40)
    }
}
