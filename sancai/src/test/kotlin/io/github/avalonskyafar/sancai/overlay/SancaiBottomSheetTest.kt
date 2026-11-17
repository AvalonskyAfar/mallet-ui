package io.github.avalonskyafar.sancai.overlay

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import android.window.OnBackInvokedCallback
import kotlin.math.roundToInt
import org.robolectric.util.ReflectionHelpers
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiDivider
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiBottomSheetTest {

    private fun activityContext(theme: SancaiTheme = SancaiTheme.light()): Context {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return SancaiTheme.wrap(activity, theme)
    }

    /** Runs posted work and lets every glide finish. */
    private fun settle() {
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS)
        ShadowLooper.idleMainLooper()
    }

    private fun actionSheet(context: Context = activityContext()): SancaiBottomSheet.Builder =
        SancaiBottomSheet.Builder(context)
            .title("Photo")
            .message("Taken yesterday at the kiln")
            .action("Share", icon = SancaiIcon.ARROW_UP)
            .action("Rename", icon = SancaiIcon.EDIT)
            .action("Delete", SancaiTextTone.DANGER, SancaiIcon.TRASH)
            .cancel()

    private fun shown(builder: SancaiBottomSheet.Builder = actionSheet()): SancaiBottomSheet {
        val sheet = builder.show()
        settle()
        return sheet
    }

    /**
     * Drags on the header, [fraction] of the sheet height downwards. The clock moves between
     * the events, so the release is slow and only the distance decides.
     */
    private fun dragHeader(sheet: SancaiBottomSheet, fraction: Float, release: Boolean = true) {
        val root = sheet.root
        val x = root.width / 2f
        val y = root.sheet.top + 12f
        val travel = root.sheet.height * fraction
        val downTime = SystemClock.uptimeMillis()
        Touch.dispatch(root, MotionEvent.ACTION_DOWN, x, y, downTime)
        for (i in 1..8) {
            ShadowSystemClock.advanceBy(150, TimeUnit.MILLISECONDS)
            Touch.dispatch(root, MotionEvent.ACTION_MOVE, x, y + travel * i / 8f, downTime)
        }
        ShadowSystemClock.advanceBy(150, TimeUnit.MILLISECONDS)
        if (release) Touch.dispatch(root, MotionEvent.ACTION_UP, x, y + travel, downTime)
    }

    @Test
    fun `structure has header, actions with dividers and a cancel row`() {
        val sheet = shown()
        val body = sheet.root.sheet
        assertThat(sheet.isShowing).isTrue()
        assertThat(body.titleView?.text.toString()).isEqualTo("Photo")
        assertThat(body.messageView?.text.toString()).isEqualTo("Taken yesterday at the kiln")
        assertThat(body.actionRows.map { it.label.text.toString() })
            .containsExactly("Share", "Rename", "Delete").inOrder()
        assertThat(body.cancelRow?.label?.text.toString()).isEqualTo("Cancel")
        val density = body.resources.displayMetrics.density
        for (row in body.actionRows) assertThat(row.height).isEqualTo((56 * density).toInt())
        // Dividers reach from rim to rim.
        val list = body.actionRows[0].parent as android.view.ViewGroup
        val divider = list.getChildAt(1)
        assertThat(divider).isInstanceOf(SancaiDivider::class.java)
        assertThat(divider.width).isEqualTo(body.width)
    }

    @Test
    fun `sheet rests on the bottom edge, centred and at most 560 dp wide`() {
        val sheet = shown()
        val root = sheet.root
        val density = root.resources.displayMetrics.density
        assertThat(root.sheet.bottom).isEqualTo(root.height)
        assertThat(root.sheet.width).isAtMost((560 * density).toInt())
        assertThat(root.sheet.left).isEqualTo(root.width - root.sheet.right)
        assertThat(root.offset).isEqualTo(0f)
        assertThat(root.shownFraction()).isEqualTo(1f)

        val wide = actionSheet().build().root
        Snapshots.layout(wide, 800, 600)
        assertThat(wide.sheet.width).isEqualTo((560 * density).toInt())
        assertThat(wide.sheet.left).isEqualTo(wide.width - wide.sheet.right)
    }

    @Test
    fun `colours follow the tone and the icon never shifts the label`() {
        val sheet = shown()
        val colors = SancaiTheme.light().colors
        val rows = sheet.root.sheet.actionRows
        assertThat(rows[0].label.currentTextColor).isEqualTo(colors.ink)
        assertThat(rows[2].label.currentTextColor).isEqualTo(colors.danger)
        assertThat(sheet.root.sheet.cancelRow?.label?.currentTextColor).isEqualTo(colors.muted)
        val row = rows[0]
        val icon = checkNotNull(row.iconView)
        assertThat(row.label.left + row.label.width / 2).isEqualTo(row.width / 2)
        assertThat(icon.right).isLessThan(row.width / 2)
        assertThat(row.label.paddingLeft).isEqualTo(row.label.paddingRight)
    }

    @Test
    fun `icon moves to the other side in a right-to-left layout`() {
        val context = activityContext()
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val root = actionSheet(context).build().root
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(root, 360, 500)
        val row = root.sheet.actionRows[0]
        val icon = checkNotNull(row.iconView)
        assertThat(icon.left).isGreaterThan(row.width / 2)
        assertThat(row.label.left + row.label.width / 2).isEqualTo(row.width / 2)
    }

    @Test
    fun `action runs its callback once and then dismisses`() {
        var shared = 0
        var dismissed = 0
        val sheet = shown(
            SancaiBottomSheet.Builder(activityContext())
                .action("Share") { shared++ }
                .action("Delete", SancaiTextTone.DANGER)
                .cancel()
                .onDismiss { dismissed++ },
        )
        val row = sheet.root.sheet.actionRows[0]
        Touch.tap(row, row.width / 2f, row.height / 2f)
        Snapshots.idle()
        assertThat(shared).isEqualTo(1)
        // A second tap while the sheet glides away is ignored.
        row.performClick()
        settle()
        assertThat(shared).isEqualTo(1)
        assertThat(sheet.isShowing).isFalse()
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `disabled action pales and does nothing`() {
        var runs = 0
        val sheet = shown(
            SancaiBottomSheet.Builder(activityContext()).action("Archive", enabled = false) { runs++ },
        )
        val row = sheet.root.sheet.actionRows[0]
        assertThat(row.alpha).isWithin(0.001f).of(107 / 255f)
        assertThat(row.isFocusable).isFalse()
        Touch.tap(row, row.width / 2f, row.height / 2f)
        settle()
        assertThat(runs).isEqualTo(0)
        assertThat(sheet.isShowing).isTrue()
    }

    @Test
    fun `cancel row, scrim tap and back dismiss`() {
        val byCancel = shown()
        checkNotNull(byCancel.root.sheet.cancelRow).performClick()
        settle()
        assertThat(byCancel.isShowing).isFalse()

        val byScrim = shown()
        Touch.tap(byScrim.root, byScrim.root.width / 2f, 20f)
        settle()
        assertThat(byScrim.isShowing).isFalse()

        val byBack = shown()
        @Suppress("DEPRECATION")
        ShadowDialog.getLatestDialog().onBackPressed()
        settle()
        assertThat(byBack.isShowing).isFalse()
    }

    @Test
    fun `back key reaches the sheet through the dialog's own back handling`() {
        // The platform routes both the key and its predictive-back default callback to
        // Dialog.onBackPressed(), which the sheet overrides; no second callback is registered.
        val sheet = shown()
        val dialog = ShadowDialog.getLatestDialog()
        val now = SystemClock.uptimeMillis()
        dialog.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
        dialog.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0))
        // It glides out first instead of vanishing.
        assertThat(sheet.isShowing).isTrue()
        settle()
        assertThat(sheet.isShowing).isFalse()
    }

    @Test
    fun `predictive back reaches the sheet through the dialog's default callback`() {
        // What android:enableOnBackInvokedCallback="true" sets on the application.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val info = activity.applicationInfo
        val flags = ApplicationInfo::class.java.getDeclaredField("privateFlagsExt")
        flags.isAccessible = true
        flags.setInt(info, flags.getInt(info) or (1 shl 3))
        val enabled = Class.forName("android.window.WindowOnBackInvokedDispatcher")
            .getMethod("isOnBackInvokedCallbackEnabled", Context::class.java)
            .invoke(null, activity)
        assertThat(enabled).isEqualTo(true)

        val sheet = shown(actionSheet(SancaiTheme.wrap(activity, SancaiTheme.light())))
        val dialog = ShadowDialog.getLatestDialog()
        // The platform registered its own callback for this dialog; the sheet adds none.
        val callback = ReflectionHelpers.getField<OnBackInvokedCallback?>(dialog, "mDefaultBackCallback")
        checkNotNull(callback).onBackInvoked()
        assertThat(sheet.isShowing).isTrue()
        settle()
        assertThat(sheet.isShowing).isFalse()

        val locked = shown(actionSheet(SancaiTheme.wrap(activity, SancaiTheme.light())).cancelable(false))
        val lockedDialog = ShadowDialog.getLatestDialog()
        checkNotNull(ReflectionHelpers.getField<OnBackInvokedCallback?>(lockedDialog, "mDefaultBackCallback"))
            .onBackInvoked()
        settle()
        assertThat(locked.isShowing).isTrue()
    }

    @Test
    fun `window lets the scrim run behind the system bars`() {
        val sheet = shown(actionSheet(activityContext(SancaiTheme.dark())))
        val window = checkNotNull(ShadowDialog.getLatestDialog().window)
        @Suppress("DEPRECATION")
        assertThat(window.navigationBarColor).isEqualTo(Color.TRANSPARENT)
        @Suppress("DEPRECATION")
        assertThat(window.statusBarColor).isEqualTo(Color.TRANSPARENT)
        assertThat(window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND).isEqualTo(0)
        assertThat(sheet.isShowing).isTrue()
    }

    @Test
    fun `tap on the sheet itself does not dismiss`() {
        val sheet = shown()
        val root = sheet.root
        Touch.tap(root, root.width / 2f, root.sheet.top + 12f)
        settle()
        assertThat(sheet.isShowing).isTrue()
    }

    @Test
    fun `non-cancelable sheet only leaves through dismiss or its cancel row`() {
        val sheet = shown(actionSheet().cancelable(false))
        val root = sheet.root
        Touch.tap(root, root.width / 2f, 20f)
        @Suppress("DEPRECATION")
        ShadowDialog.getLatestDialog().onBackPressed()
        dragHeader(sheet, 0.8f)
        settle()
        assertThat(sheet.isShowing).isTrue()
        assertThat(root.offset).isEqualTo(0f)
        sheet.dismiss()
        settle()
        assertThat(sheet.isShowing).isFalse()

        val second = shown(actionSheet().cancelable(false))
        checkNotNull(second.root.sheet.cancelRow).performClick()
        settle()
        assertThat(second.isShowing).isFalse()
    }

    @Test
    fun `sheet follows the finger down and the scrim fades with it`() {
        val sheet = shown()
        val root = sheet.root
        dragHeader(sheet, 0.5f, release = false)
        assertThat(root.isDragging).isTrue()
        assertThat(root.offset).isGreaterThan(root.sheet.height * 0.3f)
        assertThat(root.sheet.translationY).isEqualTo(root.offset)
        assertThat(root.shownFraction()).isLessThan(0.7f)
        assertThat(root.shownFraction()).isGreaterThan(0.4f)
    }

    @Test
    fun `drag past the commit fraction dismisses, a shorter one returns`() {
        val short = shown()
        dragHeader(short, SancaiMotion.COMMIT_FRACTION - 0.08f)
        settle()
        assertThat(short.isShowing).isTrue()
        assertThat(short.root.offset).isEqualTo(0f)

        dragHeader(short, SancaiMotion.COMMIT_FRACTION + 0.2f)
        settle()
        assertThat(short.isShowing).isFalse()
    }

    @Test
    fun `sheet cannot be dragged above its rest position`() {
        val sheet = shown()
        val root = sheet.root
        val x = root.width / 2f
        val y = root.sheet.top + 12f
        Touch.drag(root, x, y, x, y - 200f, release = false)
        assertThat(root.offset).isEqualTo(0f)
        // Down and back up past the start: it stops at rest.
        Touch.drag(root, x, y, x, y + 300f, release = false)
        Touch.dispatch(root, MotionEvent.ACTION_MOVE, x, y - 300f)
        assertThat(root.offset).isEqualTo(0f)
    }

    private fun tallContent(context: Context): ScrollView {
        val scroller = ScrollView(context)
        val tall = View(context)
        tall.minimumHeight = 4000
        scroller.addView(tall, android.view.ViewGroup.LayoutParams(-1, 4000))
        return scroller
    }

    @Test
    fun `drag does not start while the content is scrolled`() {
        val context = activityContext()
        val scroller = tallContent(context)
        val sheet = shown(SancaiBottomSheet.Builder(context).title("Notes").view(scroller))
        val root = sheet.root
        assertThat(scroller.height).isGreaterThan(0)
        assertThat(root.sheet.top).isGreaterThan(0)
        scroller.scrollTo(0, 300)
        val x = root.width / 2f
        val y = root.sheet.bottom - 200f
        Touch.drag(root, x, y - 150f, x, y, release = false)
        assertThat(root.isDragging).isFalse()
        assertThat(root.offset).isEqualTo(0f)
        Touch.dispatch(root, MotionEvent.ACTION_UP, x, y)

        // The header still moves the sheet, and so does the content once it is at its top.
        dragHeader(sheet, 0.1f, release = false)
        assertThat(root.isDragging).isTrue()
        Touch.dispatch(root, MotionEvent.ACTION_CANCEL, x, y)
        settle()
        scroller.scrollTo(0, 0)
        Touch.drag(root, x, y - 150f, x, y, release = false)
        assertThat(root.isDragging).isTrue()
        assertThat(root.offset).isGreaterThan(0f)
    }

    @Test
    fun `accessibility has a pane title, a dismiss action and button rows`() {
        val sheet = shown()
        val body = sheet.root.sheet
        assertThat(ViewCompat.getAccessibilityPaneTitle(body).toString()).isEqualTo("Photo")
        val row = body.actionRows[2]
        assertThat(row.accessibilityClassName.toString()).isEqualTo("android.widget.Button")
        assertThat(row.contentDescription.toString()).isEqualTo("Delete")
        val dismiss = AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_DISMISS.id
        assertThat(ViewCompat.performAccessibilityAction(body, dismiss, null)).isTrue()
        settle()
        assertThat(sheet.isShowing).isFalse()

        val untitled = SancaiBottomSheet.Builder(activityContext()).action("One").build()
        assertThat(ViewCompat.getAccessibilityPaneTitle(untitled.root.sheet).toString()).isEqualTo("Bottom sheet")
    }

    @Test
    fun `sheet can be shown again after it was dismissed`() {
        var dismissed = 0
        val sheet = shown(actionSheet().onDismiss { dismissed++ })
        sheet.dismiss()
        settle()
        sheet.show()
        settle()
        assertThat(sheet.isShowing).isTrue()
        assertThat(sheet.root.offset).isEqualTo(0f)
        sheet.dismiss()
        settle()
        assertThat(dismissed).isEqualTo(2)
    }

    @Test
    fun `sheet keeps the bottom inset free`() {
        val root = actionSheet().build().root
        Snapshots.layout(root, 360, 600)
        val before = root.sheet.height
        root.sheet.bottomInset = 90
        Snapshots.layout(root, 360, 600)
        assertThat(root.sheet.height).isEqualTo(before + 90)
        assertThat(root.sheet.paddingBottom).isEqualTo(90)
    }

    @Test
    fun `empty sheet still shows, names itself and dismisses`() {
        val sheet = shown(SancaiBottomSheet.Builder(activityContext()).title("").message(null))
        val body = sheet.root.sheet
        assertThat(body.titleView).isNull()
        assertThat(body.messageView).isNull()
        assertThat(body.actionRows).isEmpty()
        assertThat(body.cancelRow).isNull()
        assertThat(body.height).isGreaterThan(0)
        assertThat(ViewCompat.getAccessibilityPaneTitle(body).toString()).isEqualTo("Bottom sheet")
        sheet.dismiss()
        settle()
        assertThat(sheet.isShowing).isFalse()
    }

    @Test
    fun `long text wraps at large text scale and the label stays centred`() {
        val theme = SancaiTheme.light().copy(textScale = SancaiTheme.LARGE_TEXT_SCALE)
        val long = "Move this photograph and all of its notes to the archive of the old kiln"
        val root = SancaiBottomSheet.Builder(activityContext(theme))
            .title("$long $long")
            .message(long)
            .action(long, icon = SancaiIcon.TRASH)
            .action("Share")
            .cancel()
            .build().root
        Snapshots.capture(root, "sheet_light_long", 320, 640)
        val body = root.sheet
        val density = root.resources.displayMetrics.density
        val row = body.actionRows[0]
        assertThat(checkNotNull(body.titleView).lineCount).isGreaterThan(1)
        assertThat(row.label.lineCount).isGreaterThan(1)
        assertThat(row.height).isGreaterThan((56 * density).toInt())
        assertThat(body.actionRows[1].height).isAtLeast((56 * density).toInt())
        assertThat(row.label.left + row.label.width / 2).isEqualTo(row.width / 2)
        // The text keeps clear of the icon.
        assertThat(row.label.paddingLeft).isGreaterThan(checkNotNull(row.iconView).right)
        assertThat(row.label.textSize).isWithin(0.6f).of(15.5f * 1.13f * density)
        assertThat(body.top).isAtLeast((24 * density).toInt())
        assertThat(body.bottom).isEqualTo(root.height)
    }

    @Test
    fun `too many actions keep the sheet below the top gap and scroll`() {
        val builder = SancaiBottomSheet.Builder(activityContext()).title("Pick one").cancel()
        for (i in 1..30) builder.action("Action $i")
        val root = builder.build().root
        Snapshots.layout(root, 360, 500)
        val body = root.sheet
        val density = root.resources.displayMetrics.density
        assertThat(body.top).isEqualTo((24 * density).roundToInt())
        val scroller = body.actionRows[0].parent.parent as ScrollView
        assertThat(scroller.canScrollVertically(1)).isTrue()
        // Header and cancel row stay on screen around the scrolling list.
        val cancel = checkNotNull(body.cancelRow)
        assertThat(cancel.bottom).isEqualTo(body.height)
        assertThat(cancel.height).isEqualTo((56 * density).toInt())
    }

    private fun assertNear(actual: Int, expected: Int) {
        val message = "#%08X vs #%08X".format(actual, expected)
        assertThat(abs(Color.red(actual) - Color.red(expected)) <= 1).isTrue()
        assertThat(abs(Color.green(actual) - Color.green(expected)) <= 1).isTrue()
        com.google.common.truth.Truth.assertWithMessage(message)
            .that(abs(Color.blue(actual) - Color.blue(expected)) <= 1).isTrue()
    }

    private fun scrimOver(color: Int): Int {
        val keep = 1f - 69 / 255f
        return Color.rgb(
            Math.round(Color.red(color) * keep),
            Math.round(Color.green(color) * keep),
            Math.round(Color.blue(color) * keep),
        )
    }

    @Test
    fun `scrim is black at 27 percent, the grabber is hairline and the top corners are round`() {
        for (theme in listOf(SancaiTheme.light(), SancaiTheme.dark())) {
            val root = actionSheet(activityContext(theme)).build().root
            val bitmap = Snapshots.render(root, 360, 520)
            val density = root.resources.displayMetrics.density
            val body = root.sheet
            val scrim = scrimOver(theme.colors.canvas)
            assertNear(bitmap.getPixel(20, 20), scrim)
            // Outside the rounded corner the scrim shows through.
            assertNear(bitmap.getPixel(body.left + 2, body.top + 2), scrim)
            assertNear(bitmap.getPixel(body.right - 3, body.top + 2), scrim)
            val grabberY = body.top + (10 * density).toInt()
            assertThat(bitmap.getPixel(root.width / 2, grabberY)).isEqualTo(theme.colors.hairline)
            // 36 dp wide: 17 dp from the centre is pill, 20 dp is porcelain.
            assertThat(bitmap.getPixel(root.width / 2 + (17 * density).toInt(), grabberY))
                .isEqualTo(theme.colors.hairline)
            assertThat(bitmap.getPixel(root.width / 2 + (20 * density).toInt(), grabberY))
                .isNotEqualTo(theme.colors.hairline)
        }
        val light = actionSheet().build().root
        val bitmap = Snapshots.render(light, 360, 520)
        val y = light.sheet.top + (10 * light.resources.displayMetrics.density).toInt()
        assertThat(bitmap.getPixel(light.width / 4, y)).isEqualTo(SancaiTheme.light().colors.surface)
    }

    @Test
    fun `snapshot of sheet`() {
        for ((name, theme) in listOf("sheet_light" to SancaiTheme.light(), "sheet_dark" to SancaiTheme.dark())) {
            val root = actionSheet(activityContext(theme)).build().root
            Snapshots.capture(root, name, 360, 520)
            assertThat(root.sheet.height).isGreaterThan(0)
            val plain = SancaiBottomSheet.Builder(activityContext(theme))
                .action("Take photo")
                .action("Choose from library")
                .action("Remove photo", SancaiTextTone.DANGER, enabled = false)
                .build().root
            Snapshots.capture(plain, name + "_plain", 360, 300)
        }
    }
}
