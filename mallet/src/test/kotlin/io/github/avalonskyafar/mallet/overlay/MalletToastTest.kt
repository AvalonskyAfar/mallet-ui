package io.github.avalonskyafar.mallet.overlay

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.RectF
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.testing.Touch
import io.github.avalonskyafar.mallet.theme.MalletTheme
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletToastTest {

    private val context = Snapshots.context(MalletTheme.light()).also {
        // Views only mirror when the application declares android:supportsRtl.
        it.applicationInfo.flags = it.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
    }

    private fun activity(): Activity = Snapshots.attach(View(context))

    private fun content(activity: Activity): FrameLayout = activity.findViewById(android.R.id.content)

    private fun advance(ms: Long) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)
    }

    private fun pill(
        context: Context,
        message: String,
        tone: MalletToast.Tone = MalletToast.Tone.NEUTRAL,
        action: String? = null,
    ): MalletToastView = MalletToastView(context).also {
        it.message = message
        it.tone = tone
        it.actionLabel = action
    }

    private fun wrap(pill: MalletToastView): FrameLayout = FrameLayout(pill.context).also {
        it.addView(
            pill,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL,
            ),
        )
    }

    private fun action(pill: MalletToastView): TextView = pill.getChildAt(1) as TextView

    @Test
    fun `a toast appears at the bottom centre of the content view and rises`() {
        val activity = activity()
        val toast = MalletToast.show(activity, "Saved")
        val density = activity.resources.displayMetrics.density

        assertThat(toast.isShowing).isTrue()
        assertThat(toast.view.parent).isSameInstanceAs(content(activity))
        assertThat(toast.view.message.toString()).isEqualTo("Saved")
        val params = toast.view.layoutParams as FrameLayout.LayoutParams
        assertThat(params.gravity).isEqualTo(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        assertThat(params.leftMargin).isEqualTo((18 * density).roundToInt())
        assertThat(params.rightMargin).isEqualTo((18 * density).roundToInt())
        assertThat(params.bottomMargin).isEqualTo((28 * density).roundToInt())
        assertThat(toast.view.translationY).isEqualTo(18 * density)
        assertThat(toast.view.layerType).isEqualTo(View.LAYER_TYPE_HARDWARE)

        advance(300)
        assertThat(toast.view.translationY).isEqualTo(0f)
        assertThat(toast.view.alpha).isEqualTo(1f)
    }

    @Test
    fun `a new toast replaces the one on screen at once`() {
        val activity = activity()
        var firstGone = 0
        val first = MalletToast.Builder(activity).message("One").onDismiss { _, _ -> firstGone++ }.show()
        val second = MalletToast.error(activity, "Two")

        assertThat(first.isShowing).isFalse()
        assertThat(first.view.parent).isNull()
        assertThat(firstGone).isEqualTo(1)
        assertThat(second.view.parent).isSameInstanceAs(content(activity))
        assertThat(second.view.tone).isEqualTo(MalletToast.Tone.DANGER)
        val pills = (0 until content(activity).childCount).count { content(activity).getChildAt(it) is MalletToastView }
        assertThat(pills).isEqualTo(1)

        // The timer of the replaced toast must not touch the new one.
        advance(2700)
        assertThat(firstGone).isEqualTo(1)
    }

    @Test
    fun `a toast leaves after its duration without fading`() {
        val activity = activity()
        var gone: Boolean? = null
        val toast = MalletToast.Builder(activity).message("Saved").onDismiss { _, actionTaken -> gone = actionTaken }.show()
        assertThat(toast.duration).isEqualTo(2600L)

        advance(2500)
        assertThat(toast.isShowing).isTrue()
        assertThat(toast.view.parent).isNotNull()

        // Sinking: on its way down, fully opaque, still in the content view. The test
        // choreographer may run a whole glide inside one looper turn, so the frames are
        // recorded as they are produced.
        val frames = ArrayList<Float>()
        toast.view.animate().setUpdateListener {
            frames.add(toast.view.translationY)
            assertThat(toast.view.alpha).isEqualTo(1f)
            assertThat(toast.view.parent).isNotNull()
        }
        advance(150)
        assertWithMessage("frames of the sink").that(frames).isNotEmpty()
        assertThat(frames.first()).isAtMost(frames.last())
        assertThat(frames.last()).isEqualTo(18 * activity.resources.displayMetrics.density)

        advance(400)
        assertThat(toast.isShowing).isFalse()
        assertThat(toast.view.parent).isNull()
        assertThat(toast.view.layerType).isEqualTo(View.LAYER_TYPE_NONE)
        assertThat(gone).isFalse()
    }

    @Test
    fun `duration can be changed and zero keeps the toast`() {
        val activity = activity()
        val short = MalletToast.Builder(activity).message("Short").duration(500).show()
        advance(1000)
        assertThat(short.view.parent).isNull()

        val sticky = MalletToast.Builder(activity).message("Sticky").duration(0).show()
        advance(60_000)
        assertThat(sticky.isShowing).isTrue()
        sticky.dismiss()
        sticky.dismiss()
        advance(400)
        assertThat(sticky.view.parent).isNull()
    }

    @Test
    fun `the action runs its callback once and dismisses`() {
        val activity = activity()
        var undone = 0
        var taken: Boolean? = null
        val toast = MalletToast.Builder(activity)
            .message("Entry deleted")
            .action("Undo") { undone++ }
            .onDismiss { _, actionTaken -> taken = actionTaken }
            .show()
        advance(300)
        assertThat(toast.duration).isEqualTo(5000L)
        assertThat(toast.view.hasAction).isTrue()
        assertThat(toast.view.actionLabel.toString()).isEqualTo("Undo")

        val button = action(toast.view)
        Touch.tap(toast.view, button.left + button.width / 2f, button.top + button.height / 2f)
        Snapshots.idle()
        button.performClick()
        assertThat(undone).isEqualTo(1)

        advance(400)
        assertThat(toast.view.parent).isNull()
        assertThat(taken).isTrue()
    }

    @Test
    fun `dismissing from code never runs the action`() {
        val activity = activity()
        var undone = 0
        var taken: Boolean? = null
        val toast = MalletToast.Builder(activity)
            .message("Entry deleted")
            .action("Undo") { undone++ }
            .onDismiss { _, actionTaken -> taken = actionTaken }
            .show()
        advance(300)
        toast.dismiss()
        advance(400)
        assertThat(undone).isEqualTo(0)
        assertThat(taken).isFalse()
        // Too late: the pill is gone and its action is dead.
        action(toast.view).performClick()
        assertThat(undone).isEqualTo(0)
    }

    @Test
    fun `the action answers the keyboard and a disabled pill does not`() {
        val activity = activity()
        var undone = 0
        val toast = MalletToast.Builder(activity).message("Entry deleted").action("Undo") { undone++ }.show()
        advance(300)
        val button = action(toast.view)
        assertThat(button.isFocusable).isTrue()
        assertThat(button.accessibilityClassName.toString()).isEqualTo("android.widget.Button")

        toast.view.isEnabled = false
        button.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        button.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        Snapshots.idle()
        assertThat(undone).isEqualTo(0)

        toast.view.isEnabled = true
        button.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        button.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        Snapshots.idle()
        assertThat(undone).isEqualTo(1)
    }

    /** Colour of the hue where the body gradient passes through the plain fill, clear of text. */
    private fun bodyPixel(pill: MalletToastView): Int {
        val frame = wrap(pill)
        val bitmap = Snapshots.render(frame, 300)
        val body = RectF()
        pill.bodyBounds(body)
        val density = pill.resources.displayMetrics.density
        val x = pill.left + body.left + 9 * density
        val y = pill.top + body.top + body.height() * 0.56f
        return bitmap.getPixel(x.roundToInt(), y.roundToInt())
    }

    private fun assertNear(actual: Int, expected: Int, tolerance: Int = 2) {
        assertThat(Color.alpha(actual)).isEqualTo(255)
        assertThat(Math.abs(Color.red(actual) - Color.red(expected))).isAtMost(tolerance)
        assertThat(Math.abs(Color.green(actual) - Color.green(expected))).isAtMost(tolerance)
        assertThat(Math.abs(Color.blue(actual) - Color.blue(expected))).isAtMost(tolerance)
    }

    @Test
    fun `tones hue the pill and turn the text white`() {
        for (theme in listOf(MalletTheme.light(), MalletTheme.dark())) {
            val themed = Snapshots.context(theme)
            val colors = theme.colors

            val neutral = pill(themed, "Saved")
            assertThat((neutral.getChildAt(0) as TextView).currentTextColor).isEqualTo(colors.ink)
            if (!theme.isDark) assertNear(bodyPixel(neutral), colors.surface)

            val danger = pill(themed, "Failed", MalletToast.Tone.DANGER, "Retry")
            assertThat((danger.getChildAt(0) as TextView).currentTextColor).isEqualTo(Color.WHITE)
            assertThat(action(danger).currentTextColor).isEqualTo(Color.WHITE)
            assertNear(bodyPixel(danger), colors.danger)

            val success = pill(themed, "Done", MalletToast.Tone.SUCCESS)
            assertNear(bodyPixel(success), colors.success)

            val undo = pill(themed, "Entry deleted", action = "Undo")
            assertThat(action(undo).currentTextColor).isEqualTo(colors.accent)
        }
    }

    @Test
    fun `the pill keeps its shadow gutter and fits its text`() {
        val plain = pill(context, "Saved")
        Snapshots.layout(wrap(plain), 300)
        val density = plain.resources.displayMetrics.density
        val body = RectF()
        plain.bodyBounds(body)
        assertThat(body.left).isAtLeast(1.4f * density)
        assertThat(body.top).isAtLeast(1f * density)
        assertThat(plain.height - body.bottom).isAtLeast(4.8f * density)
        val text = plain.getChildAt(0) as TextView
        assertThat(text.paddingLeft).isEqualTo((18 * density).roundToInt())
        assertThat(text.paddingTop).isEqualTo((12 * density).roundToInt())
        assertThat(text.textSize).isWithin(0.01f).of(13.5f * plain.resources.displayMetrics.scaledDensity)
        assertThat(plain.width).isLessThan((300 * density).roundToInt())

        // With an action the capsule is a 44 dp touch target.
        val undo = pill(context, "Entry deleted", action = "Undo")
        Snapshots.layout(wrap(undo), 300)
        undo.bodyBounds(body)
        assertThat(body.height()).isAtLeast(44 * density - 1f)
        assertThat(action(undo).height.toFloat()).isAtLeast(44 * density - 1f)
        assertThat(action(undo).width.toFloat()).isAtLeast(44 * density - 1f)
    }

    @Test
    fun `the action sits at the end and mirrors in RTL`() {
        val ltr = pill(context, "Entry deleted", action = "Undo")
        Snapshots.layout(wrap(ltr), 300)
        assertThat(action(ltr).left).isGreaterThan(ltr.getChildAt(0).left)

        val rtl = pill(context, "Entry deleted", action = "Undo")
        val frame = wrap(rtl)
        frame.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(frame, 300)
        assertThat(action(rtl).left).isLessThan(rtl.getChildAt(0).left)
        assertThat(action(rtl).width).isEqualTo(action(ltr).width)
    }

    @Test
    fun `a long message wraps inside the side margins and large text does not clip`() {
        val large = Snapshots.context(MalletTheme.light().copy(textScale = MalletTheme.LARGE_TEXT_SCALE))
        val long = pill(
            large,
            "The archive could not be written because the storage is full. Free some space and try again.",
            MalletToast.Tone.DANGER,
            "Retry",
        )
        val frame = wrap(long)
        Snapshots.layout(frame, 300)
        val text = long.getChildAt(0) as TextView
        assertThat(long.width).isAtMost(frame.width)
        assertThat(text.lineCount).isGreaterThan(1)
        assertThat(text.lineCount).isAtMost(3)
        assertThat(text.layout.height).isAtMost(text.height - text.paddingTop - text.paddingBottom)
        // The action keeps its full label.
        val button = action(long)
        assertThat(button.layout.getEllipsisCount(0)).isEqualTo(0)
        assertThat(button.right).isAtMost(long.width)
    }

    @Test
    fun `the message is a polite live region and a screen reader gets ten seconds`() {
        val activity = activity()
        val quick = MalletToast.show(activity, "Saved")
        assertThat(ViewCompat.getAccessibilityLiveRegion(quick.view))
            .isEqualTo(ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        assertThat(quick.view.isImportantForAccessibility).isTrue()

        val manager = activity.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(manager).setEnabled(true)
        shadowOf(manager).setTouchExplorationEnabled(true)
        val toast = MalletToast.Builder(activity).message("Entry deleted").action("Undo") { }.show()
        assertThat(toast.duration).isEqualTo(10_000L)
        advance(9_000)
        assertThat(toast.isShowing).isTrue()
        advance(1_500)
        assertThat(toast.view.parent).isNull()
    }

    @Test
    fun `cancel removes the toast and a detached pill ends quietly`() {
        val activity = activity()
        var gone = 0
        val toast = MalletToast.Builder(activity).message("Saved").onDismiss { _, _ -> gone++ }.show()
        MalletToast.cancel(activity)
        assertThat(toast.view.parent).isNull()
        assertThat(gone).isEqualTo(1)

        val other = MalletToast.Builder(activity).message("Saved").onDismiss { _, _ -> gone++ }.show()
        content(activity).removeView(other.view)
        assertThat(other.isShowing).isFalse()
        advance(3_000)
        assertThat(gone).isEqualTo(2)
    }

    @Test
    fun `a blank message shows nothing and keeps the toast on screen`() {
        val activity = activity()
        var gone = 0
        val current = MalletToast.show(activity, "Saved")
        val empty = MalletToast.Builder(activity).message("  ").onDismiss { _, _ -> gone++ }.show()

        assertThat(empty.isShowing).isFalse()
        assertThat(empty.view.parent).isNull()
        assertThat(current.isShowing).isTrue()
        empty.dismiss()
        advance(3_000)
        assertThat(gone).isEqualTo(0)

        // An action alone is still something to show.
        val bare = MalletToast.Builder(activity).action("Undo") { }.show()
        assertThat(bare.isShowing).isTrue()
        assertThat(current.isShowing).isFalse()
    }

    @Test
    fun `a toast shows once and the dismiss accessibility action sinks it`() {
        val activity = activity()
        var gone = 0
        val toast = MalletToast.Builder(activity).message("Saved").duration(0).onDismiss { _, _ -> gone++ }.build()
        assertThat(toast.isShowing).isFalse()
        toast.show()
        toast.show()
        assertThat(content(activity).indexOfChild(toast.view)).isEqualTo(content(activity).childCount - 1)
        advance(300)

        val dismiss = android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS
        assertThat(toast.view.performAccessibilityAction(dismiss, null)).isTrue()
        advance(400)
        assertThat(toast.view.parent).isNull()
        assertThat(gone).isEqualTo(1)
        toast.show()
        assertThat(toast.view.parent).isNull()
    }

    @Test
    fun `a toast shown from a dismiss listener does not leave two pills`() {
        val activity = activity()
        MalletToast.Builder(activity).message("One")
            .onDismiss { _, _ -> MalletToast.show(activity, "From the listener") }
            .show()
        val last = MalletToast.show(activity, "Two")
        val host = content(activity)
        val pills = (0 until host.childCount).map { host.getChildAt(it) }.filterIsInstance<MalletToastView>()
        assertThat(pills).containsExactly(last.view)
    }

    @Test
    fun `the pill swallows touches on its message`() {
        val plain = pill(context, "Saved")
        val frame = wrap(plain)
        var below = 0
        frame.setOnClickListener { below++ }
        Snapshots.attach(frame)
        Touch.tap(frame, plain.left + plain.width / 2f, plain.top + plain.height / 2f)
        Snapshots.idle()
        assertThat(below).isEqualTo(0)
    }

    private fun sheet(theme: MalletTheme): View {
        val themed = Snapshots.context(theme)
        val density = themed.resources.displayMetrics.density
        val column = LinearLayout(themed)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL
        val pad = (18 * density).roundToInt()
        column.setPadding(pad, pad, pad, pad)
        val pills = listOf(
            pill(themed, "Saved"),
            pill(themed, "Could not reach the server", MalletToast.Tone.DANGER),
            pill(themed, "Backup complete", MalletToast.Tone.SUCCESS),
            pill(themed, "Entry deleted", action = "Undo"),
            pill(themed, "Upload failed", MalletToast.Tone.DANGER, "Retry"),
            pill(
                themed,
                "The archive could not be written because the storage is full. Free some space and try again.",
                action = "Settings",
            ),
            pill(themed, "条目已删除", action = "撤销").also { it.layoutDirection = View.LAYOUT_DIRECTION_RTL },
            pill(themed, "Entry deleted", action = "Undo").also { it.isEnabled = false },
        )
        for (pill in pills) {
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            params.bottomMargin = (12 * density).roundToInt()
            column.addView(pill, params)
        }
        return column
    }

    @Test
    fun `action draws its own focus rim without the platform highlight`() {
        val view = action(pill(context, "Rule deleted", action = "Undo"))
        assertThat(view.isFocusable).isTrue()
        if (android.os.Build.VERSION.SDK_INT >= 26) assertThat(view.defaultFocusHighlightEnabled).isFalse()
    }

    @Test
    fun `snapshot of toasts`() {
        val light = Snapshots.capture(sheet(MalletTheme.light()), "toast_light", 360)
        val dark = Snapshots.capture(sheet(MalletTheme.dark()), "toast_dark", 360)
        assertThat(light.width).isEqualTo(dark.width)
        assertThat(light.height).isGreaterThan(0)
    }
}
