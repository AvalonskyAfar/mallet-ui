package io.github.avalonskyafar.mallet.showcase.pages

import android.widget.LinearLayout
import android.widget.Space
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

/** Checkboxes and radios: a "select all" box that follows its options, and two radio groups. */
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
        """)
        hues(page)
        page.code("""
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
        val bare = MalletCheckbox(context).apply {
            text = page.str(R.string.choice_ochre)
            hue = page.theme.hues.ochre
        }
        val locked = MalletCheckbox(context).apply {
            text = page.str(R.string.choice_locked)
            setChecked(true, animate = false)
            isEnabled = false
        }
        addView(tinted, params())
        addView(bare, params())
        addDivider(5f)
        addView(locked, params())
    }
}

/** One question, three answers and one that is not on offer. */
private fun radios(page: PageScope) {
    page.section(R.string.choice_radios_title, R.string.choice_radios_detail) {
        val context = page.context
        val ids = intArrayOf(R.id.choice_daily, R.id.choice_weekly, R.id.choice_manual, R.id.choice_hourly)
        val labels = intArrayOf(
            R.string.choice_daily, R.string.choice_weekly, R.string.choice_manual, R.string.choice_hourly,
        )
        val answers = intArrayOf(
            R.string.choice_daily_status, R.string.choice_weekly_status, R.string.choice_manual_status,
        )
        val group = MalletRadioGroup(context).apply { id = R.id.choice_backup_group }
        ids.forEachIndexed { index, radioId ->
            val radio = MalletRadioButton(context).apply {
                id = radioId
                text = page.str(labels[index])
                if (index == 1) setChecked(true, animate = false)
                if (index == 3) isEnabled = false
            }
            group.addView(radio, params())
        }
        addView(group, params())
        val line = status(page)

        fun report(checkedId: Int) {
            val index = ids.indexOf(checkedId)
            if (index in answers.indices) line.text = page.str(answers[index])
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            report(checkedId)
        }
        report(group.checkedId)
    }
}

/** Five bare radios in a row, one per hue. */
private fun hues(page: PageScope) {
    page.section(R.string.choice_hues_title, R.string.choice_hues_detail) {
        val context = page.context
        val all = page.theme.hues
        val pours = listOf(all.bronze, all.ochre, all.cobalt, all.jade, all.mist)
        val ids = intArrayOf(
            R.id.choice_hue_0, R.id.choice_hue_1, R.id.choice_hue_2, R.id.choice_hue_3, R.id.choice_hue_4,
        )
        val names = intArrayOf(
            R.string.choice_bronze, R.string.choice_ochre_name, R.string.choice_cobalt,
            R.string.choice_jade, R.string.choice_mist,
        )
        val row = MalletRadioGroup(context).apply {
            id = R.id.choice_hue_group
            orientation = LinearLayout.HORIZONTAL
        }
        row.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
        pours.forEachIndexed { index, pour ->
            val radio = MalletRadioButton(context).apply {
                id = ids[index]
                hue = pour
                contentDescription = page.str(names[index])
                if (index == 0) setChecked(true, animate = false)
            }
            row.addView(radio, LinearLayout.LayoutParams(WRAP, WRAP))
            row.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
        }
        addView(row, params())
        val line = status(page)

        fun report(checkedId: Int) {
            val index = ids.indexOf(checkedId)
            if (index >= 0) line.text = context.getString(R.string.choice_hues_status, page.str(names[index]))
        }
        row.setOnCheckedChangeListener { _, checkedId ->
            report(checkedId)
        }
        report(row.checkedId)
    }
}

/** A muted line inside a card that reports what the controls above it did. */
private fun MalletSurface.status(page: PageScope): MalletText {
    addDivider(5f)
    val line = page.text("", MalletTextRole.CAPTION, MalletTextTone.MUTED)
    line.setPadding(page.dp(8), page.dp(7), page.dp(8), page.dp(6))
    addView(line, LinearLayout.LayoutParams(MATCH, WRAP))
    return line
}
