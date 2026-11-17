package io.github.avalonskyafar.sancai.widget

import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.SparseArray
import android.os.Parcelable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiTextFieldTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val colors = SancaiTheme.light().colors

    private fun field(block: SancaiTextField.() -> Unit = {}): SancaiTextField =
        SancaiTextField(context).apply(block)

    // Children in order: label, well (icon, edit, action), support row (message, counter).
    private fun SancaiTextField.labelView() = getChildAt(0) as TextView
    private fun SancaiTextField.well() = getChildAt(1) as LinearLayout
    private fun SancaiTextField.leading() = well().getChildAt(0) as ImageView
    private fun SancaiTextField.action() = well().getChildAt(2) as ImageView
    private fun SancaiTextField.supportRow() = getChildAt(2) as LinearLayout
    private fun SancaiTextField.supportView() = supportRow().getChildAt(0) as TextView
    private fun SancaiTextField.counter() = supportRow().getChildAt(1) as TextView

    /** Counts dark pixels where the text of [field] is drawn. */
    private fun inkPixels(field: SancaiTextField): Int {
        val bitmap = Snapshots.render(field, 300)
        val edit = field.editText
        var count = 0
        for (x in edit.left + edit.paddingLeft until edit.right step 2) {
            for (y in field.well().top + edit.paddingTop until field.well().bottom - edit.paddingBottom step 2) {
                if (android.graphics.Color.red(bitmap.getPixel(x, y)) < 90) count++
            }
        }
        return count
    }

    @Test
    fun `text round trips and code does not call the listener`() {
        val heard = mutableListOf<String>()
        val field = field { setOnTextChangeListener { _, t -> heard += t.toString() } }
        field.text = "hello"
        assertThat(field.text.toString()).isEqualTo("hello")
        assertThat(field.editText.selectionStart).isEqualTo(5)
        assertThat(heard).isEmpty()

        field.editText.append("!")
        assertThat(heard).containsExactly("hello!")
    }

    @Test
    fun `label helper and error show only when set`() {
        val field = field()
        assertThat(field.labelView().visibility).isEqualTo(View.GONE)
        assertThat(field.supportRow().visibility).isEqualTo(View.GONE)

        field.label = "Account"
        field.helperText = "As shown on the page"
        assertThat(field.labelView().visibility).isEqualTo(View.VISIBLE)
        assertThat(field.labelView().text.toString()).isEqualTo("Account")
        assertThat(field.labelView().labelFor).isEqualTo(field.editText.id)
        assertThat(field.supportRow().visibility).isEqualTo(View.VISIBLE)
        assertThat(field.supportView().currentTextColor).isEqualTo(colors.muted)

        field.label = null
        field.helperText = null
        assertThat(field.labelView().visibility).isEqualTo(View.GONE)
        assertThat(field.supportRow().visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `error wins over helper and is exposed on the node`() {
        val field = field { helperText = "Helper" }
        field.error = "Too short"
        assertThat(field.error.toString()).isEqualTo("Too short")
        assertThat(field.supportView().text.toString()).isEqualTo("Too short")
        assertThat(field.supportView().currentTextColor).isEqualTo(colors.danger)

        val node = AccessibilityNodeInfoCompat.obtain()
        androidx.core.view.ViewCompat.onInitializeAccessibilityNodeInfo(field.editText, node)
        assertThat(node.error.toString()).isEqualTo("Too short")
        assertThat(node.isContentInvalid).isTrue()

        field.error = null
        assertThat(field.error).isNull()
        assertThat(field.supportView().text.toString()).isEqualTo("Helper")
        assertThat(field.supportView().currentTextColor).isEqualTo(colors.muted)
        assertThat(field.supportRow().visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `validate sets and clears the error`() {
        val field = field { text = "ab" }
        val rule = { text: CharSequence -> if (text.length < 3) "At least three" else null }
        assertThat(field.validate(rule)).isFalse()
        assertThat(field.error.toString()).isEqualTo("At least three")
        field.text = "abc"
        assertThat(field.validate(rule)).isTrue()
        assertThat(field.error).isNull()
        assertThat(field.supportRow().visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `support line grows over the feedback duration when attached`() {
        val field = field()
        Snapshots.attach(field)
        val before = field.height
        val heights = mutableListOf<Int>()
        // One animation frame per 16 ms instead of the whole animation in one go.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        try {
            field.error = "Wrong"
            repeat(30) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                heights += field.supportRow().height
            }
        } finally {
            ShadowChoreographer.setPaused(false)
        }
        Snapshots.idle()
        val full = heights.last()
        assertThat(full).isAtLeast(field.supportView().height)
        assertThat(field.height).isEqualTo(before + full)
        // It grows: some frames are in between, and no frame is lower than the one before.
        assertThat(heights.filter { it in 1 until full }).isNotEmpty()
        assertThat(heights).isInOrder()

        field.error = null
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        assertThat(field.supportRow().visibility).isEqualTo(View.GONE)
        assertThat(field.height).isEqualTo(before)
    }

    @Test
    fun `counter counts and turns danger beyond the maximum`() {
        val field = field { counterMaxLength = 5 }
        assertThat(field.supportRow().visibility).isEqualTo(View.VISIBLE)
        assertThat(field.counter().text.toString()).isEqualTo("0 / 5")
        field.text = "abcde"
        assertThat(field.counter().text.toString()).isEqualTo("5 / 5")
        assertThat(field.counter().currentTextColor).isEqualTo(colors.muted)
        field.editText.append("f")
        assertThat(field.counter().text.toString()).isEqualTo("6 / 5")
        assertThat(field.counter().currentTextColor).isEqualTo(colors.danger)
        field.counterMaxLength = 0
        assertThat(field.counter().visibility).isEqualTo(View.GONE)
        assertThat(field.supportRow().visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `clear action appears with text and clears it as user input`() {
        val heard = mutableListOf<String>()
        val field = field { endAction = SancaiTextField.EndAction.CLEAR }
        field.setOnTextChangeListener { _, t -> heard += t.toString() }
        Snapshots.attach(field)
        assertThat(field.action().visibility).isEqualTo(View.GONE)
        field.text = "abc"
        assertThat(field.action().visibility).isEqualTo(View.VISIBLE)
        assertThat(field.action().contentDescription.toString())
            .isEqualTo(context.getString(R.string.sancai_text_field_clear))
        Snapshots.idle()
        assertThat(field.action().width).isEqualTo(Math.round(44 * field.resources.displayMetrics.density))

        field.action().performClick()
        Snapshots.idle()
        assertThat(field.text.toString()).isEmpty()
        assertThat(heard).containsExactly("")
        assertThat(field.action().visibility).isEqualTo(View.GONE)
        assertThat(field.editText.isFocused).isTrue()
    }

    @Test
    fun `password toggle changes the transformation and keeps the selection`() {
        val field = field { endAction = SancaiTextField.EndAction.PASSWORD_TOGGLE }
        field.text = "secret"
        Snapshots.attach(field)
        field.editText.setSelection(2, 4)
        assertThat(field.editText.transformationMethod).isInstanceOf(PasswordTransformationMethod::class.java)
        assertThat(field.action().contentDescription.toString())
            .isEqualTo(context.getString(R.string.sancai_text_field_show_password))

        val hidden = field.editText.layout
        com.google.common.truth.Truth.assertWithMessage(
            "hidden text '${hidden.text}' width ${hidden.getLineWidth(0)} scrollX ${field.editText.scrollX} " +
                "edit ${field.editText.width}x${field.editText.height} lines ${hidden.lineCount}",
        ).that(inkPixels(field)).isGreaterThan(20)

        field.action().performClick()
        Snapshots.idle()
        assertThat(field.isPasswordVisible).isTrue()
        assertThat(field.editText.transformationMethod).isNull()
        assertThat(field.editText.selectionStart).isEqualTo(2)
        assertThat(field.editText.selectionEnd).isEqualTo(4)
        assertThat(field.action().contentDescription.toString())
            .isEqualTo(context.getString(R.string.sancai_text_field_hide_password))

        field.isPasswordVisible = false
        assertThat(field.editText.transformationMethod).isInstanceOf(PasswordTransformationMethod::class.java)
        assertThat(field.editText.selectionStart).isEqualTo(2)
        assertThat(field.editText.selectionEnd).isEqualTo(4)
    }

    @Test
    fun `multiline follows the input type and grows the well`() {
        val single = field { text = "one" }
        val multi = field { isMultiline = true; text = "one" }
        Snapshots.layout(single, 300)
        Snapshots.layout(multi, 300)
        assertThat(single.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE).isEqualTo(0)
        assertThat(multi.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE).isNotEqualTo(0)
        assertThat(multi.editText.minLines).isEqualTo(3)
        assertThat(multi.height).isGreaterThan(single.height)
        // 44 dp touch target at least.
        assertThat(single.height).isAtLeast((44 * single.resources.displayMetrics.density).toInt())

        val number = field { isMultiline = true; inputType = InputType.TYPE_CLASS_NUMBER }
        Snapshots.layout(number, 300)
        assertThat(number.height).isEqualTo(single.height)
    }

    @Test
    fun `large text does not clip`() {
        val large = SancaiTheme.light().copy(textScale = SancaiTheme.LARGE_TEXT_SCALE)
        val normal = field { text = "Text" }
        val scaled = SancaiTextField(Snapshots.context(large)).apply { text = "Text" }
        Snapshots.layout(normal, 300)
        Snapshots.layout(scaled, 300)
        val edit = scaled.editText
        assertThat(edit.layout.height).isAtMost(edit.height - edit.paddingTop - edit.paddingBottom)
        assertThat(edit.textSize).isGreaterThan(normal.editText.textSize)
    }

    @Test
    fun `disabled field disables the text and its action`() {
        val field = field { endAction = SancaiTextField.EndAction.CLEAR; text = "abc" }
        field.isEnabled = false
        assertThat(field.editText.isEnabled).isFalse()
        assertThat(field.action().isEnabled).isFalse()
        assertThat(field.requestFocus()).isFalse()

        // The whole field pales: the ink of the well's content is no longer solid.
        val on = Snapshots.render(field { text = "abc"; error = "Bad" }, 300)
        val off = Snapshots.render(field { text = "abc"; error = "Bad"; isEnabled = false }, 300)
        val y = on.height / 4
        assertThat(on.getPixel(2, y)).isEqualTo(colors.danger)
        assertThat(off.getPixel(2, y)).isNotEqualTo(colors.danger)
    }

    @Test
    fun `two fields restore independently without calling listeners`() {
        fun form(): Triple<LinearLayout, SancaiTextField, SancaiTextField> {
            val first = field { id = 101; endAction = SancaiTextField.EndAction.PASSWORD_TOGGLE }
            val second = field { id = 102 }
            val parent = LinearLayout(context).apply { addView(first); addView(second) }
            return Triple(parent, first, second)
        }
        val (parent, first, second) = form()
        first.text = "alpha"
        first.isPasswordVisible = true
        second.text = "beta"
        second.error = "Taken"
        val state = SparseArray<Parcelable>()
        parent.saveHierarchyState(state)

        val (newParent, newFirst, newSecond) = form()
        var calls = 0
        newFirst.setOnTextChangeListener { _, _ -> calls++ }
        newSecond.setOnTextChangeListener { _, _ -> calls++ }
        newParent.restoreHierarchyState(state)

        assertThat(newFirst.text.toString()).isEqualTo("alpha")
        assertThat(newFirst.isPasswordVisible).isTrue()
        assertThat(newFirst.editText.transformationMethod).isNull()
        assertThat(newFirst.error).isNull()
        assertThat(newSecond.text.toString()).isEqualTo("beta")
        assertThat(newSecond.error.toString()).isEqualTo("Taken")
        assertThat(newSecond.supportRow().visibility).isEqualTo(View.VISIBLE)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `the field that was typed in takes focus again after a restore`() {
        fun form(): Triple<LinearLayout, SancaiTextField, SancaiTextField> {
            val first = field { id = 101 }
            val second = field { id = 102 }
            val parent = LinearLayout(context).apply { addView(first); addView(second) }
            return Triple(parent, first, second)
        }
        val (parent, _, second) = form()
        Snapshots.attach(parent)
        assertThat(second.requestFocus()).isTrue()
        val state = SparseArray<Parcelable>()
        parent.saveHierarchyState(state)

        val (newParent, newFirst, newSecond) = form()
        Snapshots.attach(newParent)
        newParent.restoreHierarchyState(state)
        // What the window does after the hierarchy: focus the first view with the saved id.
        newParent.findViewById<View>(R.id.sancai_text_field_edit).requestFocus()
        assertThat(newFirst.editText.isFocused).isTrue()
        Snapshots.idle()
        assertThat(newSecond.editText.isFocused).isTrue()
        assertThat(newFirst.editText.isFocused).isFalse()
    }

    @Test
    fun `visible password keeps the cursor when lines or input type are set`() {
        val field = field { endAction = SancaiTextField.EndAction.PASSWORD_TOGGLE }
        field.text = "secret"
        field.isPasswordVisible = true
        field.editText.setSelection(6)
        field.minLines = 2
        assertThat(field.editText.selectionStart).isEqualTo(6)
        field.maxLines = 4
        assertThat(field.editText.selectionStart).isEqualTo(6)
        field.editText.setSelection(2, 4)
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertThat(field.editText.selectionStart).isEqualTo(2)
        assertThat(field.editText.selectionEnd).isEqualTo(4)
        assertThat(field.editText.transformationMethod).isNull()

        val plain = field { text = "secret" }
        plain.editText.setSelection(6)
        plain.endAction = SancaiTextField.EndAction.PASSWORD_TOGGLE
        assertThat(plain.editText.selectionStart).isEqualTo(6)
    }

    @Test
    fun `leaving the password toggle shows the text again and keeps a chosen password type`() {
        val field = field { text = "secret" }
        var calls = 0
        field.setOnTextChangeListener { _, _ -> calls++ }
        field.endAction = SancaiTextField.EndAction.PASSWORD_TOGGLE
        field.editText.setSelection(3)
        field.endAction = SancaiTextField.EndAction.CLEAR
        assertThat(field.inputType).isEqualTo(InputType.TYPE_CLASS_TEXT)
        assertThat(field.editText.transformationMethod).isNotInstanceOf(PasswordTransformationMethod::class.java)
        assertThat(field.editText.selectionStart).isEqualTo(3)
        assertThat(calls).isEqualTo(0)

        val password = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val chosen = field { inputType = password; text = "secret" }
        chosen.endAction = SancaiTextField.EndAction.PASSWORD_TOGGLE
        chosen.isPasswordVisible = true
        chosen.endAction = SancaiTextField.EndAction.NONE
        assertThat(chosen.inputType).isEqualTo(password)
        assertThat(chosen.editText.transformationMethod).isInstanceOf(PasswordTransformationMethod::class.java)

        // A type that is no password type was hidden by the transformation alone.
        for (type in listOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)) {
            val other = field { inputType = type; text = "1234" }
            other.endAction = SancaiTextField.EndAction.PASSWORD_TOGGLE
            assertThat(other.editText.transformationMethod).isInstanceOf(PasswordTransformationMethod::class.java)
            other.endAction = SancaiTextField.EndAction.NONE
            assertThat(other.inputType).isEqualTo(type)
            assertThat(other.editText.transformationMethod).isNotInstanceOf(PasswordTransformationMethod::class.java)
        }
        val lines = field { isMultiline = true; text = "a\nb" }
        lines.endAction = SancaiTextField.EndAction.PASSWORD_TOGGLE
        lines.endAction = SancaiTextField.EndAction.NONE
        assertThat(lines.editText.transformationMethod).isNull()
    }

    @Test
    fun `the text and the end action leave the focus highlight to the rim`() {
        val field = field { endAction = SancaiTextField.EndAction.CLEAR }
        assertThat(field.editText.defaultFocusHighlightEnabled).isFalse()
        assertThat(field.action().defaultFocusHighlightEnabled).isFalse()
    }

    @Test
    fun `rim is hairline at rest ink with focus and danger with an error`() {
        val field = field { text = "abc" }
        Snapshots.attach(field, 300)
        val y = { field.height / 2 }
        val rest = Snapshots.render(field, 300).getPixel(2, y())
        assertThat(rest).isNotEqualTo(colors.ink)
        assertThat(rest).isNotEqualTo(colors.danger)

        // Tapping anywhere on the well gives the text the focus.
        field.well().performClick()
        Snapshots.idle()
        assertThat(field.editText.isFocused).isTrue()
        assertThat(Snapshots.render(field, 300).getPixel(2, y())).isEqualTo(colors.ink)

        field.error = "Bad"
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        val bitmap = Snapshots.render(field, 300)
        assertThat(bitmap.getPixel(2, field.well().height / 2)).isEqualTo(colors.danger)
        assertThat(bitmap.getPixel(bitmap.width - 3, field.well().height / 2)).isEqualTo(colors.danger)
    }

    @Test
    fun `icon action and counter swap sides in a right to left layout`() {
        val app = context.applicationContext
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        fun build(direction: Int) = field {
            layoutDirection = direction
            leadingIcon = SancaiIcon.SEARCH
            endAction = SancaiTextField.EndAction.CLEAR
            counterMaxLength = 9
            helperText = "Helper"
            text = "abc"
        }.also { Snapshots.layout(it, 300) }

        val ltr = build(View.LAYOUT_DIRECTION_LTR)
        assertThat(ltr.leading().left).isLessThan(ltr.editText.left)
        assertThat(ltr.action().left).isAtLeast(ltr.editText.right)
        assertThat(ltr.counter().left).isAtLeast(ltr.supportView().right)

        val rtl = build(View.LAYOUT_DIRECTION_RTL)
        assertThat(rtl.leading().left).isAtLeast(rtl.editText.right)
        assertThat(rtl.action().right).isAtMost(rtl.editText.left)
        assertThat(rtl.counter().right).isAtMost(rtl.supportView().left)
        assertThat(rtl.width - rtl.leading().right).isEqualTo(ltr.leading().left)
    }

    @Test
    fun `xml attributes configure the field without feedback`() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.text, "abcdefgh")
            .addAttribute(android.R.attr.hint, "Hint")
            .addAttribute(android.R.attr.maxLength, "5")
            .addAttribute(android.R.attr.enabled, "false")
            .addAttribute(R.attr.sancaiFieldLabel, "Label")
            .addAttribute(R.attr.sancaiFieldHelper, "Helper")
            .addAttribute(R.attr.sancaiFieldError, "Error")
            .addAttribute(R.attr.sancaiFieldCounter, "5")
            .addAttribute(R.attr.sancaiFieldEndAction, "clear")
            .addAttribute(R.attr.sancaiFieldMonospace, "true")
            .build()
        val field = SancaiTextField(context, attrs)
        assertThat(field.text.toString()).isEqualTo("abcde")
        assertThat(field.hint.toString()).isEqualTo("Hint")
        assertThat(field.label.toString()).isEqualTo("Label")
        assertThat(field.helperText.toString()).isEqualTo("Helper")
        assertThat(field.error.toString()).isEqualTo("Error")
        assertThat(field.counter().text.toString()).isEqualTo("5 / 5")
        assertThat(field.endAction).isEqualTo(SancaiTextField.EndAction.CLEAR)
        assertThat(field.action().visibility).isEqualTo(View.VISIBLE)
        assertThat(field.isMonospace).isTrue()
        assertThat(field.isEnabled).isFalse()
        assertThat(field.editText.isEnabled).isFalse()

        val multi = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(R.attr.sancaiFieldMultiline, "true")
            .addAttribute(android.R.attr.minLines, "2")
            .addAttribute(R.attr.sancaiFieldEndAction, "passwordToggle")
            .build()
        val second = SancaiTextField(context, multi)
        assertThat(second.isMultiline).isTrue()
        assertThat(second.editText.minLines).isEqualTo(2)
        assertThat(second.endAction).isEqualTo(SancaiTextField.EndAction.PASSWORD_TOGGLE)
    }

    @Test
    fun `empty field and long text keep the layout inside the width`() {
        val empty = field()
        Snapshots.layout(empty, 300)
        assertThat(empty.height).isEqualTo(empty.well().height)
        assertThat(empty.text.toString()).isEmpty()

        val long = "A very long sentence that cannot possibly fit on a single line of a narrow field. "
        val plain = field { text = "x" }.also { Snapshots.layout(it, 200) }
        val field = field {
            label = long; helperText = long; text = long.repeat(4); counterMaxLength = 20
            endAction = SancaiTextField.EndAction.CLEAR
        }
        Snapshots.layout(field, 200)
        // A single line stays one line high; label and helper wrap instead of clipping.
        assertThat(field.well().height).isEqualTo(plain.well().height)
        assertThat(field.labelView().lineCount).isGreaterThan(1)
        assertThat(field.supportView().lineCount).isGreaterThan(1)
        assertThat(field.counter().right).isAtMost(field.width)
        assertThat(field.counter().layout.getEllipsisCount(0)).isEqualTo(0)
        assertThat(field.action().right).isAtMost(field.well().width)
        assertThat(field.counter().currentTextColor).isEqualTo(colors.danger)

        val multi = field { isMultiline = true; maxLines = 4; text = long.repeat(20) }
        Snapshots.layout(multi, 200)
        assertThat(multi.editText.lineCount).isGreaterThan(4)
        assertThat(multi.editText.height).isLessThan(multi.editText.layout.height)
    }

    @Test
    fun `large text in a right to left field keeps every part visible`() {
        val app = context.applicationContext
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val large = SancaiTheme.light().copy(textScale = SancaiTheme.LARGE_TEXT_SCALE)
        val field = SancaiTextField(Snapshots.context(large)).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            label = "Label"; text = "Text"; error = "Wrong"; counterMaxLength = 8
            isMultiline = true
        }
        Snapshots.layout(field, 300)
        for (view in listOf(field.labelView(), field.supportView(), field.counter(), field.editText)) {
            val needed = view.layout.height + view.paddingTop + view.paddingBottom
            assertThat(view.height).isAtLeast(needed)
        }
        assertThat(field.counter().left).isEqualTo(0)
        assertThat(field.supportRow().bottom).isEqualTo(field.height)
    }

    @Test
    fun `keyboard stops on the text and the action but not on the well`() {
        val heard = mutableListOf<String>()
        val field = field { endAction = SancaiTextField.EndAction.CLEAR; text = "abc" }
        field.setOnTextChangeListener { _, t -> heard += t.toString() }
        Snapshots.attach(field)
        assertThat(field.well().isFocusable).isFalse()
        assertThat(field.editText.isFocusable).isTrue()
        assertThat(field.action().isFocusable).isTrue()
        assertThat(field.requestFocus()).isTrue()
        assertThat(field.editText.isFocused).isTrue()

        // The accessibility click on the action is user input too.
        field.action().performAccessibilityAction(
            android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null,
        )
        Snapshots.idle()
        assertThat(heard).containsExactly("")

        val node = AccessibilityNodeInfoCompat.obtain()
        androidx.core.view.ViewCompat.onInitializeAccessibilityNodeInfo(field.editText, node)
        assertThat(node.className.toString()).isEqualTo("android.widget.EditText")
        assertThat(node.error).isNull()
    }

    private fun gallery(theme: SancaiTheme, name: String) {
        val themed = Snapshots.context(theme)
        val density = themed.resources.displayMetrics.density
        val column = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(theme.colors.canvas)
        }
        fun add(block: SancaiTextField.() -> Unit): SancaiTextField {
            val field = SancaiTextField(themed).apply(block)
            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = (14 * density).toInt()
            column.addView(field, params)
            return field
        }
        add { label = "Empty"; hint = "Name shown at the top of the page" }
        add { label = "Filled"; text = "Porcelain" }
        val focused = add { label = "Focused"; text = "Typing goes here" }
        add { label = "Error"; text = "ab"; helperText = "Helper"; error = "Use at least three characters" }
        add { label = "Helper with counter"; text = "Twelve chars"; helperText = "Shown on your profile"; counterMaxLength = 64 }
        add { label = "Multi-line"; isMultiline = true; text = "com.example.one\ncom.example.two" }
        add {
            label = "Monospace"; isMultiline = true; isMonospace = true; minLines = 2
            text = "{\n  \"enabled\": true\n}"
        }
        add { label = "Disabled"; text = "Cannot be edited"; helperText = "Locked by policy"; isEnabled = false }
        add { label = "Icon and clear"; leadingIcon = SancaiIcon.SEARCH; endAction = SancaiTextField.EndAction.CLEAR; text = "glaze" }
        add { label = "Password"; endAction = SancaiTextField.EndAction.PASSWORD_TOGGLE; text = "secret" }
        Snapshots.attach(column, 360)
        focused.requestFocus()
        Snapshots.idle()
        assertThat(focused.editText.isFocused).isTrue()
        // Text set before the first layout must not leave a single line scrolled out of its well.
        for (i in 0 until column.childCount) {
            val field = column.getChildAt(i) as SancaiTextField
            if (!field.isMultiline) assertThat(field.editText.scrollY).isEqualTo(0)
        }
        Snapshots.capture(column, name, 360)
    }

    @Test
    fun `snapshot of text fields in light and dark`() {
        gallery(SancaiTheme.light(), "field_light")
        gallery(SancaiTheme.dark(), "field_dark")
    }
}
