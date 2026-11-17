package io.github.avalonskyafar.sancai.overlay

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.overlay.SancaiDialog.Companion.ACTION_NEGATIVE
import io.github.avalonskyafar.sancai.overlay.SancaiDialog.Companion.ACTION_NEUTRAL
import io.github.avalonskyafar.sancai.overlay.SancaiDialog.Companion.ACTION_POSITIVE
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiControl
import io.github.avalonskyafar.sancai.widget.SancaiText
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiDialogTest {

    private val theme = SancaiTheme.light()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val context: Context = SancaiTheme.wrap(activity, theme)
    private val density = context.resources.displayMetrics.density

    private fun dp(value: Float): Int = (value * density).roundToInt()

    private fun builder(): SancaiDialog.Builder = SancaiDialog.Builder(context)
        .title("Restore all defaults?")
        .message("Every setting returns to its original value.")

    /** Runs the enter or exit motion to its end. */
    private fun settle() {
        ShadowLooper.idleMainLooper(SancaiMotion.DIALOG_MS + 80, TimeUnit.MILLISECONDS)
        Snapshots.idle()
    }

    private fun shown(builder: SancaiDialog.Builder): SancaiDialog {
        val dialog = builder.show()
        settle()
        return dialog
    }

    private fun click(dialog: SancaiDialog, which: Int) {
        checkNotNull(dialog.actionView(which)).performClick()
    }

    private fun texts(view: View, out: MutableList<TextView> = ArrayList()): List<TextView> {
        if (view is TextView) out.add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) texts(view.getChildAt(i), out)
        return out
    }

    private fun label(dialog: SancaiDialog, which: Int): TextView = dialog.actionView(which) as TextView

    @Test
    fun `one action is the default and fills the row`() {
        val dialog = builder().create()
        Snapshots.layout(dialog.contentView, 352)
        val ok = label(dialog, ACTION_POSITIVE)
        assertThat(ok.text.toString()).isEqualTo("OK")
        assertThat(dialog.actionView(ACTION_NEGATIVE)).isNull()
        assertThat(dialog.actionView(ACTION_NEUTRAL)).isNull()
        assertThat(ok.width).isEqualTo((ok.parent as View).width)
        assertThat(ok.height).isEqualTo(dp(54f))
        assertThat(dialog.isStacked).isFalse()
    }

    @Test
    fun `two actions share the row equally with the confirming one at the end`() {
        val dialog = builder().positive("Restore", SancaiTextTone.DANGER).negative().create()
        Snapshots.layout(dialog.contentView, 352)
        val cancel = label(dialog, ACTION_NEGATIVE)
        val restore = label(dialog, ACTION_POSITIVE)
        assertThat(cancel.text.toString()).isEqualTo("Cancel")
        assertThat(dialog.isStacked).isFalse()
        assertThat(cancel.left).isEqualTo(0)
        assertThat(restore.left).isGreaterThan(cancel.right)
        assertThat(restore.right).isEqualTo((restore.parent as View).width)
        assertThat(Math.abs(cancel.width - restore.width)).isAtMost(1)
        assertThat(cancel.height).isEqualTo(dp(54f))
        assertThat(cancel.currentTextColor).isEqualTo(theme.colors.muted)
        assertThat(restore.currentTextColor).isEqualTo(theme.colors.danger)
        assertThat(label(builder().positive().negative().create(), ACTION_POSITIVE).currentTextColor)
            .isEqualTo(theme.colors.accent)
    }

    @Test
    fun `three actions stack as full-width rows positive, neutral, negative`() {
        val dialog = builder().positive("Save").neutral("Discard").negative().create()
        Snapshots.layout(dialog.contentView, 352)
        val rows = listOf(ACTION_POSITIVE, ACTION_NEUTRAL, ACTION_NEGATIVE).map { label(dialog, it) }
        assertThat(dialog.isStacked).isTrue()
        assertThat(rows.map { it.text.toString() }).containsExactly("Save", "Discard", "Cancel").inOrder()
        assertThat(rows[0].top).isLessThan(rows[1].top)
        assertThat(rows[1].top).isLessThan(rows[2].top)
        for (row in rows) {
            assertThat(row.width).isEqualTo((row.parent as View).width)
            assertThat(row.height).isEqualTo(dp(54f))
        }
        // One divider between two rows.
        assertThat(rows[1].top - rows[0].bottom).isEqualTo(density.roundToInt())
    }

    @Test
    fun `two labels that do not fit on one line each stack with the positive first`() {
        val dialog = builder()
            .positive("Restore every single setting now")
            .negative("Keep my current settings")
            .create()
        Snapshots.layout(dialog.contentView, 352)
        val positive = label(dialog, ACTION_POSITIVE)
        val negative = label(dialog, ACTION_NEGATIVE)
        assertThat(dialog.isStacked).isTrue()
        assertThat(positive.top).isLessThan(negative.top)
        assertThat(positive.width).isEqualTo((positive.parent as View).width)
        assertThat(positive.lineCount).isEqualTo(1)
        assertThat(negative.lineCount).isEqualTo(1)
    }

    @Test
    fun `card follows the original layout numbers`() {
        val dialog = builder().positive().negative().create()
        val card = dialog.contentView as ViewGroup
        Snapshots.layout(card, 352)
        val all = texts(card)
        val title = all.first { it.text.toString() == "Restore all defaults?" }
        val message = all.first { it.text.toString().startsWith("Every setting") }
        val scroll = message.parent.parent as ScrollView
        val actions = (dialog.actionView(ACTION_POSITIVE) as View).parent as View
        assertThat(title.top).isEqualTo(card.paddingTop + dp(19f))
        assertThat(title.left).isEqualTo(card.paddingLeft + dp(18f))
        assertThat(scroll.top + message.top - title.bottom).isEqualTo(dp(7f))
        assertThat(actions.top - scroll.bottom).isEqualTo(dp(18f))
        // Full-bleed: the action block spans the whole body, and nothing lies below it.
        assertThat(actions.left).isEqualTo(card.paddingLeft)
        assertThat(actions.right).isEqualTo(card.width - card.paddingRight)
        assertThat(actions.bottom).isEqualTo(card.height - card.paddingBottom)
        assertThat(title.gravity and android.view.Gravity.HORIZONTAL_GRAVITY_MASK)
            .isEqualTo(android.view.Gravity.CENTER_HORIZONTAL)
        assertThat(ViewCompat.getAccessibilityPaneTitle(card).toString()).isEqualTo("Restore all defaults?")
    }

    @Test
    fun `callback returning true closes and false keeps the dialog open`() {
        var allow = false
        var calls = 0
        val dialog = shown(builder().positive("Go") { calls++; allow }.negative())
        assertThat(dialog.isShowing).isTrue()
        click(dialog, ACTION_POSITIVE)
        settle()
        assertThat(calls).isEqualTo(1)
        assertThat(dialog.isShowing).isTrue()
        assertThat(dialog.isClosing).isFalse()
        allow = true
        click(dialog, ACTION_POSITIVE)
        assertThat(dialog.isClosing).isTrue()
        settle()
        assertThat(calls).isEqualTo(2)
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun `action without a callback just closes`() {
        val dialog = shown(builder().positive().negative())
        click(dialog, ACTION_NEGATIVE)
        settle()
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun `showError keeps the dialog open and shows the capsule`() {
        val dialog = shown(builder().positive("Save") { it.showError("Name is taken"); true }.negative())
        assertThat(dialog.error).isNull()
        click(dialog, ACTION_POSITIVE)
        settle()
        assertThat(dialog.isShowing).isTrue()
        assertThat(dialog.isClosing).isFalse()
        assertThat(dialog.error.toString()).isEqualTo("Name is taken")
        val capsule = texts(dialog.contentView).first { it.text.toString() == "Name is taken" }
        assertThat(capsule.visibility).isEqualTo(View.VISIBLE)
        assertThat(capsule.currentTextColor).isEqualTo(Color.WHITE)
        dialog.clearError()
        assertThat(dialog.error).isNull()
        assertThat(capsule.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `error capsule is glazed in the danger colour and as wide as the content`() {
        val dialog = builder().positive().negative().create()
        dialog.showError("Name is taken")
        val card = dialog.contentView
        val bitmap = Snapshots.render(card, 352)
        val capsule = texts(card).first { it.text.toString() == "Name is taken" }
        val actions = (dialog.actionView(ACTION_POSITIVE) as View).parent as View
        assertThat(actions.top - capsule.bottom).isAtMost(dp(18f))
        assertThat(capsule.left + capsule.paddingLeft - dp(16f)).isWithin(1).of(card.paddingLeft + dp(18f))
        // Well inside the capsule, left of the centred text.
        val pixel = bitmap.getPixel(capsule.left + dp(14f), capsule.top + capsule.height / 2 - dp(1.5f))
        val danger = theme.colors.danger
        assertThat(Math.abs(Color.red(pixel) - Color.red(danger))).isAtMost(14)
        assertThat(Math.abs(Color.green(pixel) - Color.green(danger))).isAtMost(14)
        assertThat(Math.abs(Color.blue(pixel) - Color.blue(danger))).isAtMost(14)
    }

    @Test
    fun `closing guard commits a rapid double click once`() {
        var commits = 0
        var cancels = 0
        val dialog = shown(builder().positive("Delete", SancaiTextTone.DANGER) { commits++; true }.negative { cancels++; true })
        click(dialog, ACTION_POSITIVE)
        click(dialog, ACTION_POSITIVE)
        click(dialog, ACTION_NEGATIVE)
        dialog.dialog.onBackPressed()
        assertThat(commits).isEqualTo(1)
        assertThat(cancels).isEqualTo(0)
        settle()
        click(dialog, ACTION_POSITIVE)
        assertThat(commits).isEqualTo(1)
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun `back key closes a cancelable dialog and is ignored by a locked one`() {
        val dialog = shown(builder().positive().negative())
        val now = android.os.SystemClock.uptimeMillis()
        dialog.dialog.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
        dialog.dialog.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0))
        assertThat(dialog.isClosing).isTrue()
        settle()
        assertThat(dialog.isShowing).isFalse()

        val locked = shown(builder().positive().cancelable(false))
        locked.dialog.onBackPressed()
        settle()
        assertThat(locked.isShowing).isTrue()
        locked.dismiss()
        settle()
        assertThat(locked.isShowing).isFalse()
    }

    @Test
    fun `dismiss listener tells whether the positive action closed the dialog`() {
        val flags = ArrayList<Boolean>()
        fun open(): SancaiDialog = shown(builder().positive().negative().onDismiss { _, positive -> flags.add(positive) })
        click(open(), ACTION_POSITIVE)
        settle()
        click(open(), ACTION_NEGATIVE)
        settle()
        open().dialog.onBackPressed()
        settle()
        open().dismiss()
        settle()
        assertThat(flags).containsExactly(true, false, false, false).inOrder()
    }

    @Test
    fun `input dialog returns the text, validates and reacts to the done key`() {
        val accepted = ArrayList<String>()
        val dialog = shown(
            SancaiDialog.Builder(context).title("Rename").input("draft", "Name") { d, text ->
                if (text.isBlank()) d.showError("Enter a name") else accepted.add(text)
                true
            },
        )
        val editor = checkNotNull(dialog.inputField)
        assertThat(dialog.inputText).isEqualTo("draft")
        assertThat(editor.hint.toString()).isEqualTo("Name")
        assertThat(editor.selectionStart).isEqualTo(5)
        assertThat(label(dialog, ACTION_POSITIVE).text.toString()).isEqualTo("Save")
        assertThat(label(dialog, ACTION_NEGATIVE).text.toString()).isEqualTo("Cancel")

        editor.setText("  ")
        click(dialog, ACTION_POSITIVE)
        settle()
        assertThat(dialog.isShowing).isTrue()
        assertThat(dialog.error.toString()).isEqualTo("Enter a name")
        assertThat(accepted).isEmpty()

        editor.setText("Kiln")
        editor.onEditorAction(EditorInfo.IME_ACTION_DONE)
        assertThat(dialog.error).isNull()
        settle()
        assertThat(accepted).containsExactly("Kiln")
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun `input variants set the input type and the line count`() {
        val number = SancaiDialog.Builder(context).input(inputType = InputType.TYPE_CLASS_NUMBER).create()
        assertThat(checkNotNull(number.inputField).inputType and InputType.TYPE_MASK_CLASS)
            .isEqualTo(InputType.TYPE_CLASS_NUMBER)
        val notes = SancaiDialog.Builder(context).input(multiline = true).create()
        val editor = checkNotNull(notes.inputField)
        assertThat(editor.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE).isNotEqualTo(0)
        assertThat(editor.minLines).isEqualTo(3)
        assertThat(SancaiDialog.Builder(context).create().inputField).isNull()
    }

    @Test
    fun `window is 352 dp wide at most, dims by 0_27 and ignores outside touches`() {
        val dialog = shown(builder().positive())
        val window = checkNotNull(dialog.dialog.window)
        val screen = context.resources.displayMetrics.widthPixels
        assertThat(window.attributes.width).isEqualTo(minOf(dp(352f), screen - dp(32f)))
        assertThat(window.attributes.dimAmount).isEqualTo(0.27f)
        assertThat(window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND).isNotEqualTo(0)
        val shadow = org.robolectric.Shadows.shadowOf(dialog.dialog)
        assertThat(shadow.isCancelableOnTouchOutside).isFalse()
        assertThat(shadow.isCancelable).isTrue()
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w320dp-h568dp")
    fun `narrow screen leaves 16 dp on each side`() {
        val dialog = shown(builder().positive())
        val screen = context.resources.displayMetrics.widthPixels
        assertThat(screen).isLessThan(dp(384f))
        assertThat(checkNotNull(dialog.dialog.window).attributes.width).isEqualTo(screen - dp(32f))
    }

    @Test
    fun `right-to-left layout swaps the action order`() {
        context.applicationInfo.flags = context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        val dialog = builder().positive().negative().create()
        val root = FrameLayout(context)
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.addView(dialog.contentView)
        Snapshots.layout(root, 352)
        val cancel = label(dialog, ACTION_NEGATIVE)
        val ok = label(dialog, ACTION_POSITIVE)
        assertThat(ok.left).isEqualTo(0)
        assertThat(cancel.left).isGreaterThan(ok.right)
        assertThat(cancel.right).isEqualTo((cancel.parent as View).width)
    }

    @Test
    fun `card rises in and reduced motion skips the animation`() {
        val dialog = builder().positive().show()
        val card = dialog.contentView
        assertThat(card.alpha).isEqualTo(0f)
        assertThat(card.translationY).isWithin(0.01f).of(11.5f * density)
        assertThat(card.scaleX).isEqualTo(0.978f)
        settle()
        assertThat(card.alpha).isEqualTo(1f)
        assertThat(card.translationY).isEqualTo(0f)
        assertThat(card.scaleX).isEqualTo(1f)

        val calm = SancaiDialog.Builder(SancaiTheme.wrap(activity, theme.copy(reduceMotion = true)))
            .title("Calm").positive().show()
        assertThat(calm.contentView.alpha).isEqualTo(1f)
        assertThat(calm.contentView.translationY).isEqualTo(0f)
        checkNotNull(calm.actionView(ACTION_POSITIVE)).performClick()
        assertThat(calm.isShowing).isFalse()
    }

    @Test
    fun `disabled action pales and does nothing`() {
        var calls = 0
        val dialog = shown(builder().positive("Add") { calls++; true }.negative())
        dialog.setActionEnabled(ACTION_POSITIVE, false)
        val add = checkNotNull(dialog.actionView(ACTION_POSITIVE))
        assertThat(add.isEnabled).isFalse()
        assertThat(add.alpha).isWithin(0.001f).of(SancaiControl.DISABLED_ALPHA / 255f)
        click(dialog, ACTION_POSITIVE)
        assertThat(calls).isEqualTo(0)
        assertThat(dialog.isShowing).isTrue()
        dialog.setActionEnabled(ACTION_POSITIVE, true)
        assertThat(add.alpha).isEqualTo(1f)
        click(dialog, ACTION_POSITIVE)
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `long message scrolls inside a card of at most 80 percent of the screen`() {
        val dialog = builder().message("A long explanation. ".repeat(400)).positive().negative().create()
        val card = dialog.contentView as ViewGroup
        Snapshots.layout(card, 352)
        val limit = (context.resources.displayMetrics.heightPixels * 0.8f).roundToInt()
        assertThat(card.height).isEqualTo(limit)
        val actions = (dialog.actionView(ACTION_POSITIVE) as View).parent as View
        assertThat(actions.bottom).isEqualTo(card.height - card.paddingBottom)
        val scroll = (0 until card.childCount).map { card.getChildAt(it) }.first { it is ScrollView } as ScrollView
        assertThat(scroll.getChildAt(0).height).isGreaterThan(scroll.height)
        assertThat(texts(card).first().top).isEqualTo(card.paddingTop + dp(19f))
    }

    @Test
    fun `large text and long labels wrap instead of clipping`() {
        val large = SancaiTheme.wrap(activity, theme.copy(textScale = 1.13f))
        val dialog = SancaiDialog.Builder(large).title("Leave without saving your changes to this page?")
            .positive("A very long confirming label that cannot possibly fit on a single line of the card")
            .create()
        Snapshots.layout(dialog.contentView, 300)
        val action = label(dialog, ACTION_POSITIVE)
        assertThat(action.lineCount).isEqualTo(2)
        assertThat(action.height).isGreaterThan(dp(54f) - 1)
        assertThat(action.layout.height).isAtMost(action.height - action.paddingTop - action.paddingBottom)
        assertThat(action.textSize).isWithin(0.5f).of(14f * 1.13f * context.resources.displayMetrics.scaledDensity)
    }

    @Test
    fun `accessibility names the pane and exposes actions as buttons`() {
        val dialog = builder().positive().negative().create()
        val untitled = SancaiDialog.Builder(context).message("Saved.").create()
        assertThat(ViewCompat.getAccessibilityPaneTitle(untitled.contentView).toString()).isEqualTo("Dialog")
        val ok = checkNotNull(dialog.actionView(ACTION_POSITIVE))
        assertThat(ok.accessibilityClassName.toString()).isEqualTo("android.widget.Button")
        assertThat(ok.isFocusable).isTrue()
        assertThat(ok.isClickable).isTrue()
        assertThat(ok.minimumHeight).isAtLeast(dp(44f))
    }

    @Test
    fun `convenience helpers show a confirmation and a notice`() {
        var confirmed = 0
        val confirm = SancaiDialog.confirm(context, "Delete rule?", "This cannot be undone.", "Delete", true) { confirmed++ }
        settle()
        assertThat(label(confirm, ACTION_POSITIVE).currentTextColor).isEqualTo(theme.colors.danger)
        click(confirm, ACTION_POSITIVE)
        settle()
        assertThat(confirmed).isEqualTo(1)
        assertThat(confirm.isShowing).isFalse()
        val notice = SancaiDialog.message(context, "Saved", "Your rules are active.")
        settle()
        assertThat(notice.actionView(ACTION_NEGATIVE)).isNull()
        assertThat(notice.isShowing).isTrue()
    }

    /** The four reference dialogs, rendered as cards on the canvas colour. */
    @Test
    fun `dialog with nothing but the default action still lays out`() {
        val dialog = shown(SancaiDialog.Builder(context).title("").message(null))
        val card = dialog.contentView
        assertThat(texts(card).filter { it.visibility == View.VISIBLE }.map { it.text.toString() }).containsExactly("OK")
        val ok = label(dialog, ACTION_POSITIVE)
        assertThat(ok.height).isEqualTo(dp(54f))
        assertThat(card.height).isGreaterThan(ok.height)
        assertThat(dialog.error).isNull()
        assertThat(dialog.inputField).isNull()
        assertThat(dialog.inputText).isEmpty()
        assertThat(dialog.actionView(ACTION_NEGATIVE)).isNull()
        dialog.setActionEnabled(ACTION_NEGATIVE, false)
        click(dialog, ACTION_POSITIVE)
        settle()
        assertThat(dialog.isShowing).isFalse()
    }

    @Test
    fun `enter key on a focused action commits it once`() {
        var commits = 0
        val dialog = shown(builder().positive("Apply") { commits++; true }.negative())
        val apply = checkNotNull(dialog.actionView(ACTION_POSITIVE))
        val now = android.os.SystemClock.uptimeMillis()
        repeat(2) {
            apply.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
            apply.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
            Snapshots.idle()
        }
        assertThat(commits).isEqualTo(1)
        assertThat(dialog.isClosing).isTrue()
        // A key that does nothing is not consumed.
        assertThat(apply.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0))).isFalse()
    }

    @Test
    fun `stack of two is read in the drawn order and ignores the layout direction`() {
        val dialog = builder()
            .positive("Turn on protection for every profile now")
            .negative("Keep everything exactly as it is today")
            .create()
        dialog.contentView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(dialog.contentView, 352)
        assertThat(dialog.isStacked).isTrue()
        val positive = label(dialog, ACTION_POSITIVE)
        val negative = label(dialog, ACTION_NEGATIVE)
        assertThat(positive.bottom).isLessThan(negative.top + 1)
        assertThat(positive.left).isEqualTo(0)
        assertThat(positive.width).isEqualTo(negative.width)
        val order = ArrayList<View>()
        (positive.parent as ViewGroup).addChildrenForAccessibility(order)
        assertThat(order).containsExactly(positive, negative).inOrder()
    }

    @Test
    fun `dialog can be shown again after it closed and the next attempt clears the error`() {
        var dismissals = 0
        var reject = true
        val dialog = shown(builder().positive("Save") { d -> if (reject) d.showError("No") ; true }
            .onDismiss { _, _ -> dismissals++ })
        click(dialog, ACTION_POSITIVE)
        assertThat(dialog.error.toString()).isEqualTo("No")
        assertThat(dialog.isClosing).isFalse()
        reject = false
        click(dialog, ACTION_POSITIVE)
        assertThat(dialog.error).isNull()
        settle()
        assertThat(dialog.isShowing).isFalse()
        assertThat(dismissals).isEqualTo(1)
        dialog.show()
        settle()
        assertThat(dialog.isShowing).isTrue()
        assertThat(dialog.isClosing).isFalse()
        assertThat(dialog.contentView.alpha).isEqualTo(1f)
        dialog.dismiss()
        settle()
        assertThat(dismissals).isEqualTo(2)
    }

    private fun gallery(theme: SancaiTheme): View {
        val themed = SancaiTheme.wrap(activity, theme)
        val column = LinearLayout(themed)
        column.orientation = LinearLayout.VERTICAL
        column.setBackgroundColor(theme.colors.canvas)
        column.setPadding(dp(16f), dp(18f), dp(16f), dp(14f))
        val input = SancaiDialog.Builder(themed).title("Rename profile")
            .message("Shown in the list and in notifications.")
            .input("Night", "Profile name").create()
        input.showError("A profile with this name already exists")
        val cards = listOf(
            SancaiDialog.Builder(themed).title("Apply the new rules?")
                .message("They take effect the next time the screen is locked.")
                .positive("Apply").negative().create(),
            SancaiDialog.Builder(themed).title("Restore all defaults?")
                .message("Every setting returns to its original value.")
                .positive("Restore", SancaiTextTone.DANGER).negative().create(),
            input,
            SancaiDialog.Builder(themed).title("Save changes?")
                .message("You have edited this profile.")
                .positive("Save").neutral("Discard").negative().create(),
        )
        for (card in cards) {
            val params = LinearLayout.LayoutParams(dp(352f), ViewGroup.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = dp(14f)
            column.addView(card.contentView, params)
        }
        return column
    }

    @Test
    fun `snapshot of dialogs in light and dark`() {
        val light = Snapshots.capture(gallery(SancaiTheme.light()), "dialog_light", 384)
        val dark = Snapshots.capture(gallery(SancaiTheme.dark()), "dialog_dark", 384)
        assertThat(light.width).isEqualTo(dp(384f))
        assertThat(dark.getPixel(4, 4)).isNotEqualTo(light.getPixel(4, 4))
    }
}
