package io.github.avalonskyafar.mallet.showcase.pages

import android.text.InputType
import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.widget.MalletButton
import io.github.avalonskyafar.mallet.widget.MalletTextField

private const val FIELD_GAP = 16
private const val MOTTO_LENGTH = 24
private const val CODE_LENGTH = 6

private const val CONFIG_TEXT = "[kiln]\npeak = 1300\nhold_minutes = 20"

private const val PARTS_CODE = """
    val name = MalletTextField(this)
    name.label = "Display name"
    name.helperText = "Shown at the top of your page."
    motto.counterMaxLength = 24
    password.endAction =
        MalletTextField.EndAction.PASSWORD_TOGGLE
    search.leadingIcon = MalletIcon.SEARCH
    search.endAction = MalletTextField.EndAction.CLEAR
"""

private const val VALIDATION_CODE = """
    val rule = { text: CharSequence ->
        if (text.length == 6) null
        else "Enter all six digits."
    }
    check.setOnClickListener { code.validate(rule) }
    code.setOnTextChangeListener { f, text ->
        if (rule(text) == null) f.error = null
    }
"""

private const val FORMS_CODE = """
    note.isMultiline = true
    note.minLines = 3
    note.maxLines = 6
    config.isMonospace = true
    config.isMultiline = true
    locked.isEnabled = false
"""

private fun below(page: PageScope, topDp: Int): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(topDp) }

/** Text fields: the parts of a field, validation, and the multi-line, monospace and disabled forms. */
object FieldsPage : DemoPage("c_fields", R.string.page_c_fields_title, R.string.page_c_fields_summary) {
    override fun build(page: PageScope) {
        page.title()
        parts(page)
        validation(page)
        forms(page)
        page.caption(page.str(R.string.fields_origin), topDp = 22)
    }

    /** A label and helper, a counter, a password toggle, a leading icon with the clear action. */
    private fun parts(page: PageScope) {
        val status = page.text(page.str(R.string.fields_search_idle), MalletTextRole.CAPTION, MalletTextTone.MUTED)
        page.section(R.string.fields_parts_title, R.string.fields_parts_detail) {
            val name = MalletTextField(context).apply {
                label = page.str(R.string.fields_name_label)
                hint = page.str(R.string.fields_name_hint)
                helperText = page.str(R.string.fields_name_helper)
            }
            val motto = MalletTextField(context).apply {
                label = page.str(R.string.fields_motto_label)
                text = page.str(R.string.fields_motto_text)
                counterMaxLength = MOTTO_LENGTH
            }
            val password = MalletTextField(context).apply {
                label = page.str(R.string.fields_password_label)
                endAction = MalletTextField.EndAction.PASSWORD_TOGGLE
                text = "kiln-1300"
            }
            val search = MalletTextField(context).apply {
                hint = page.str(R.string.fields_search_hint)
                leadingIcon = MalletIcon.SEARCH
                endAction = MalletTextField.EndAction.CLEAR
                text = page.str(R.string.fields_search_text)
                setOnTextChangeListener { _, text ->
                    status.text = searchStatus(page, text)
                }
            }
            status.text = searchStatus(page, search.text)
            addView(name, below(page, 0))
            addView(motto, below(page, FIELD_GAP))
            addView(password, below(page, FIELD_GAP))
            addView(search, below(page, FIELD_GAP))
            addView(status, below(page, 8))
        }
        page.code(PARTS_CODE)
    }

    private fun searchStatus(page: PageScope, text: CharSequence): String =
        if (text.isBlank()) page.str(R.string.fields_search_idle)
        else page.context.getString(R.string.fields_search_status, text.trim().toString())

    /** A field, a rule and a button. The error leaves as soon as the text passes the rule. */
    private fun validation(page: PageScope) {
        val rule: (CharSequence) -> CharSequence? = { text ->
            if (text.length == CODE_LENGTH) null else page.str(R.string.fields_code_error)
        }
        val result = page.text(page.str(R.string.fields_code_waiting), MalletTextRole.CAPTION, MalletTextTone.MUTED)
        page.section(R.string.fields_validation_title, R.string.fields_validation_detail) {
            val code = MalletTextField(context).apply {
                label = page.str(R.string.fields_code_label)
                hint = page.str(R.string.fields_code_hint)
                helperText = page.str(R.string.fields_code_helper)
                inputType = InputType.TYPE_CLASS_NUMBER
                text = "3015"
                setOnTextChangeListener { field, text ->
                    // Typing never raises the error; it only takes it away once the text is fine.
                    if (field.error != null && rule(text) == null) field.error = null
                    result.text = page.str(R.string.fields_code_waiting)
                }
            }
            val check = MalletButton(context).apply {
                variant = MalletButton.Variant.PRIMARY
                text = page.str(R.string.fields_code_check)
                setOnClickListener {
                    val passed = code.validate(rule)
                    result.text =
                        if (passed) page.context.getString(R.string.fields_code_accepted, code.text.toString())
                        else page.str(R.string.fields_code_rejected)
                    if (passed) page.host.say(page.str(R.string.fields_code_said))
                }
            }
            // The page opens with the error on screen, so that the state can be seen at once.
            code.validate(rule)
            result.text = page.str(R.string.fields_code_rejected)
            addView(code, below(page, 0))
            addView(check, below(page, 14))
            addView(result, below(page, 10))
        }
        page.code(VALIDATION_CODE)
    }

    /** Several lines, monospace, and a field that is switched off. */
    private fun forms(page: PageScope) {
        page.section(R.string.fields_forms_title, R.string.fields_forms_detail) {
            val note = MalletTextField(context).apply {
                label = page.str(R.string.fields_note_label)
                isMultiline = true
                minLines = 3
                maxLines = 6
                text = page.str(R.string.fields_note_text)
            }
            val config = MalletTextField(context).apply {
                label = page.str(R.string.fields_config_label)
                isMonospace = true
                isMultiline = true
                minLines = 3
                text = CONFIG_TEXT
            }
            val locked = MalletTextField(context).apply {
                label = page.str(R.string.fields_locked_label)
                text = page.str(R.string.fields_locked_text)
                helperText = page.str(R.string.fields_locked_helper)
                isEnabled = false
            }
            val unlock = MalletButton(context).apply {
                text = page.str(R.string.fields_locked_enable)
                setOnClickListener {
                    locked.isEnabled = !locked.isEnabled
                    text = page.str(if (locked.isEnabled) R.string.fields_locked_disable else R.string.fields_locked_enable)
                }
            }
            addView(note, below(page, 0))
            addView(config, below(page, FIELD_GAP))
            addView(locked, below(page, FIELD_GAP))
            addView(unlock, below(page, 14))
        }
        page.code(FORMS_CODE)
    }
}
