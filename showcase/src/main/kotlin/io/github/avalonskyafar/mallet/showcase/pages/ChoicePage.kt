package io.github.avalonskyafar.mallet.showcase.pages

import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import androidx.annotation.StringRes
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.widget.MalletCheckbox
import io.github.avalonskyafar.mallet.widget.MalletRadioButton
import io.github.avalonskyafar.mallet.widget.MalletRadioGroup
import io.github.avalonskyafar.mallet.widget.MalletSurface
import io.github.avalonskyafar.mallet.widget.MalletText

/** Checkboxes and radios: a "select all" box that follows its options, and two radio groups in one card. */
object ChoicePage : DemoPage("c_choice", R.string.page_c_choice_title, R.string.page_c_choice_summary) {
    override fun build(page: PageScope) {
        page.title()
        checkboxes(page)
        page.code("""
            val all = MalletCheckbox(this)
            val item = MalletCheckbox(this)
            all.text = "Select all"
            all.setOnCheckedChangeListener { _, on ->
                // set from code: no listener call
                item.isChecked = on
            }
        """)
        states(page)
        page.code("""
            val updates = MalletCheckbox(this)
            updates.hue =
                MalletTheme.of(this).hues.jade
            updates.isChecked = true
            val required = MalletCheckbox(this)
            required.setChecked(true, animate = false)
            required.isEnabled = false
        """)
        radios(page)
        page.code("""
            val group = MalletRadioGroup(this)
            val weekly = MalletRadioButton(this)
            weekly.text = "Every week"
            weekly.isChecked = true
            group.addView(weekly)
            group.setOnCheckedChangeListener { _, id ->
                Log.d("backup", "radio ${'$'}id")
            }

            val row = MalletRadioGroup(this)
            row.orientation = LinearLayout.HORIZONTAL
            val radio = MalletRadioButton(this)
            radio.hue =
                MalletTheme.of(this).hues.bronze
            radio.contentDescription = "Bronze"
            row.addView(radio)
        """)
        page.caption(page.str(R.string.choice_origin), topDp = 18)
    }
}

private const val MATCH = PageScope.MATCH
private const val WRAP = PageScope.WRAP

private fun params() = LinearLayout.LayoutParams(MATCH, WRAP)

/** Three options under a box that selects them all; the counter proves which listeners ran. */
private fun checkboxes(page: PageScope) {
    page.section(R.string.choice_boxes_title, R.string.choice_boxes_detail) {
        val context = page.context
        var calls = 0
        val all = MalletCheckbox(context).apply { text = page.str(R.string.choice_all) }
        val labels = intArrayOf(R.string.choice_messages, R.string.choice_reminders, R.string.choice_updates)
        val options = labels.map { label -> MalletCheckbox(context).apply { text = page.str(label) } }
        options[0].setChecked(true, animate = false)
        addView(all, params())
        addDivider(5f)
        options.forEach { addView(it, params()) }
        val line = status(page)

        fun report() {
            val count = options.count { it.isChecked }
            line.text = context.getString(R.string.choice_boxes_status, count, options.size, calls)
        }
        all.setOnCheckedChangeListener { _, on ->
            calls++
            // Set from code: the listeners of the three options stay silent.
            options.forEach { it.isChecked = on }
            report()
        }
        options.forEach { option ->
            option.setOnCheckedChangeListener { _, _ ->
                calls++
                all.isChecked = options.all { it.isChecked }
                report()
            }
        }
        report()
    }
}

/** Another hue, and a box that is checked and cannot be changed. */
private fun states(page: PageScope) {
    page.section(R.string.choice_states_title) {
        val context = page.context
        val tinted = MalletCheckbox(context).apply {
            text = page.str(R.string.choice_tinted)
            hue = page.theme.hues.jade
            setChecked(true, animate = false)
        }
        val unchecked = MalletCheckbox(context).apply {
            text = page.str(R.string.choice_ochre)
            hue = page.theme.hues.ochre
        }
        val locked = MalletCheckbox(context).apply {
            text = page.str(R.string.choice_locked)
            setChecked(true, animate = false)
            isEnabled = false
        }
        addView(tinted, params())
        addView(unchecked, params())
        addDivider(5f)
        addView(locked, params())
    }
}

/**
 * Two groups in one card: a question in a column, then five hues in a row. The line under the
 * question reports the answer; the head of the row names the hue that is checked.
 */
private fun radios(page: PageScope) {
    val backupIds = intArrayOf(R.id.choice_daily, R.id.choice_weekly, R.id.choice_manual, R.id.choice_hourly)
    val answers = intArrayOf(
        R.string.choice_daily_status, R.string.choice_weekly_status, R.string.choice_manual_status,
    )
    val hueIds = intArrayOf(
        R.id.choice_hue_0, R.id.choice_hue_1, R.id.choice_hue_2, R.id.choice_hue_3, R.id.choice_hue_4,
    )
    val names = intArrayOf(
        R.string.choice_bronze, R.string.choice_ochre_name, R.string.choice_cobalt,
        R.string.choice_jade, R.string.choice_mist,
    )
    page.section(R.string.choice_radios_title, R.string.choice_radios_detail) {
        val backup = backupColumn(page, backupIds)
        addView(backup, params())
        val line = status(page)
        addDivider(5f)
        val checked = head(page, R.string.choice_hues_title)
        val row = hueRow(page, hueIds, names)
        addView(row, params())

        fun reportBackup(checkedId: Int) {
            val index = backupIds.indexOf(checkedId)
            if (index in answers.indices) line.text = page.str(answers[index])
        }
        fun reportHue(checkedId: Int) {
            val index = hueIds.indexOf(checkedId)
            if (index >= 0) checked.text = page.str(names[index])
        }
        backup.setOnCheckedChangeListener { _, checkedId ->
            reportBackup(checkedId)
        }
        row.setOnCheckedChangeListener { _, checkedId ->
            reportHue(checkedId)
        }
        reportBackup(backup.checkedId)
        reportHue(row.checkedId)
    }
}

/** One question, three answers and one that is not on offer. */
private fun backupColumn(page: PageScope, ids: IntArray): MalletRadioGroup {
    val labels = intArrayOf(
        R.string.choice_daily, R.string.choice_weekly, R.string.choice_manual, R.string.choice_hourly,
    )
    val group = MalletRadioGroup(page.context).apply { id = R.id.choice_backup_group }
    ids.forEachIndexed { index, radioId ->
        val radio = MalletRadioButton(page.context).apply {
            id = radioId
            text = page.str(labels[index])
            if (index == 1) setChecked(true, animate = false)
            if (index == 3) isEnabled = false
        }
        group.addView(radio, params())
    }
    return group
}

/** Five radios without text in a row. Each has a hue of its own and is named after it for a screen reader. */
private fun hueRow(page: PageScope, ids: IntArray, names: IntArray): MalletRadioGroup {
    val context = page.context
    val hues = page.theme.hues
    val fiveHues = listOf(hues.bronze, hues.ochre, hues.cobalt, hues.jade, hues.mist)
    val row = MalletRadioGroup(context).apply {
        id = R.id.choice_hue_group
        orientation = LinearLayout.HORIZONTAL
    }
    // Equal spaces before, between and after the radios, whatever the width of the card.
    row.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
    fiveHues.forEachIndexed { index, bedHue ->
        val radio = MalletRadioButton(context).apply {
            id = ids[index]
            hue = bedHue
            contentDescription = page.str(names[index])
            if (index == 0) setChecked(true, animate = false)
        }
        row.addView(radio, LinearLayout.LayoutParams(WRAP, WRAP))
        row.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
    }
    return row
}

/** The head of a block inside a card: its label at the start and a value at the end, which is returned. */
private fun MalletSurface.head(page: PageScope, @StringRes label: Int): MalletText {
    val name = page.text(page.str(label), MalletTextRole.LABEL)
    name.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    val value = page.text("", MalletTextRole.BODY_COMPACT, MalletTextTone.MUTED)
    val line = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(page.dp(8), page.dp(5), page.dp(8), 0)
        addView(name, LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(value, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = page.dp(12) })
    }
    addView(line, params())
    return value
}

/** A muted line inside a card that reports what the controls above it did. */
private fun MalletSurface.status(page: PageScope): MalletText {
    addDivider(5f)
    val line = page.text("", MalletTextRole.CAPTION, MalletTextTone.MUTED)
    // Starts at the start edge of the card in every language, as the labels of the controls do.
    line.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    line.setPadding(page.dp(8), page.dp(7), page.dp(8), page.dp(6))
    addView(line, LinearLayout.LayoutParams(MATCH, WRAP))
    return line
}
