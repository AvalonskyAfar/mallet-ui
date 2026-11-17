package io.github.avalonskyafar.sancai.showcase.pages

import android.widget.LinearLayout
import android.widget.Space
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.widget.SancaiCheckbox
import io.github.avalonskyafar.sancai.widget.SancaiRadioButton
import io.github.avalonskyafar.sancai.widget.SancaiRadioGroup
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiText

/** Checkboxes and radios: a "select all" box that follows its options, and two radio groups. */
object ChoicePage : DemoPage("c_choice", R.string.page_c_choice_title, R.string.page_c_choice_summary) {
    override fun build(page: PageScope) {
        page.title()
        checkboxes(page)
        page.code("""
            val all = SancaiCheckbox(this)
            val item = SancaiCheckbox(this)
            all.text = "Select all"
            all.onCheckedChangeListener =
                SancaiCheckbox.OnCheckedChangeListener {
                    _, on ->
                    // set from code: no listener call
                    item.isChecked = on
                }
        """)
        states(page)
        page.code("""
            val updates = SancaiCheckbox(this)
            updates.glaze =
                SancaiTheme.of(this).glazes.celadon
            updates.isChecked = true
            val required = SancaiCheckbox(this)
            required.setChecked(true, animate = false)
            required.isEnabled = false
        """)
        radios(page)
        page.code("""
            val group = SancaiRadioGroup(this)
            val weekly = SancaiRadioButton(this)
            weekly.text = "Every week"
            weekly.isChecked = true
            group.addView(weekly)
            group.onCheckedChangeListener =
                SancaiRadioGroup.OnCheckedChangeListener {
                    _, id -> Log.d("backup", "radio ${'$'}id")
                }
        """)
        glazes(page)
        page.code("""
            val row = SancaiRadioGroup(this)
            row.orientation = LinearLayout.HORIZONTAL
            val radio = SancaiRadioButton(this)
            radio.glaze =
                SancaiTheme.of(this).glazes.terracotta
            radio.contentDescription = "Terracotta"
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
        val all = SancaiCheckbox(context).apply { text = page.str(R.string.choice_all) }
        val labels = intArrayOf(R.string.choice_messages, R.string.choice_reminders, R.string.choice_updates)
        val options = labels.map { label -> SancaiCheckbox(context).apply { text = page.str(label) } }
        options[0].setChecked(true, animate = false)
        addView(all, params())
        addDivider(5f)
        options.forEach { addView(it, params()) }
        val line = status(page)

        fun report() {
            val count = options.count { it.isChecked }
            line.text = context.getString(R.string.choice_boxes_status, count, options.size, calls)
        }
        all.onCheckedChangeListener = SancaiCheckbox.OnCheckedChangeListener { _, on ->
            calls++
            // Set from code: the listeners of the three options stay silent.
            options.forEach { it.isChecked = on }
            report()
        }
        options.forEach { option ->
            option.onCheckedChangeListener = SancaiCheckbox.OnCheckedChangeListener { _, _ ->
                calls++
                all.isChecked = options.all { it.isChecked }
                report()
            }
        }
        report()
    }
}

/** Another glaze, and a box that is checked and cannot be changed. */
private fun states(page: PageScope) {
    page.section(R.string.choice_states_title) {
        val context = page.context
        val glazed = SancaiCheckbox(context).apply {
            text = page.str(R.string.choice_glazed)
            glaze = page.theme.glazes.celadon
            setChecked(true, animate = false)
        }
        val bare = SancaiCheckbox(context).apply {
            text = page.str(R.string.choice_ochre)
            glaze = page.theme.glazes.ochre
        }
        val locked = SancaiCheckbox(context).apply {
            text = page.str(R.string.choice_locked)
            setChecked(true, animate = false)
            isEnabled = false
        }
        addView(glazed, params())
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
        val group = SancaiRadioGroup(context).apply { id = R.id.choice_backup_group }
        ids.forEachIndexed { index, radioId ->
            val radio = SancaiRadioButton(context).apply {
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
        group.onCheckedChangeListener = SancaiRadioGroup.OnCheckedChangeListener { _, checkedId ->
            report(checkedId)
        }
        report(group.checkedId)
    }
}

/** Five bare radios in a row, one per glaze. */
private fun glazes(page: PageScope) {
    page.section(R.string.choice_glazes_title, R.string.choice_glazes_detail) {
        val context = page.context
        val all = page.theme.glazes
        val pours = listOf(all.terracotta, all.ochre, all.cobalt, all.celadon, all.mist)
        val ids = intArrayOf(
            R.id.choice_glaze_0, R.id.choice_glaze_1, R.id.choice_glaze_2, R.id.choice_glaze_3, R.id.choice_glaze_4,
        )
        val names = intArrayOf(
            R.string.choice_terracotta, R.string.choice_ochre_name, R.string.choice_cobalt,
            R.string.choice_celadon, R.string.choice_mist,
        )
        val row = SancaiRadioGroup(context).apply {
            id = R.id.choice_glaze_group
            orientation = LinearLayout.HORIZONTAL
        }
        row.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
        pours.forEachIndexed { index, pour ->
            val radio = SancaiRadioButton(context).apply {
                id = ids[index]
                glaze = pour
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
            if (index >= 0) line.text = context.getString(R.string.choice_glazes_status, page.str(names[index]))
        }
        row.onCheckedChangeListener = SancaiRadioGroup.OnCheckedChangeListener { _, checkedId ->
            report(checkedId)
        }
        report(row.checkedId)
    }
}

/** A muted line inside a card that reports what the controls above it did. */
private fun SancaiSurface.status(page: PageScope): SancaiText {
    addDivider(5f)
    val line = page.text("", SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
    line.setPadding(page.dp(8), page.dp(7), page.dp(8), page.dp(6))
    addView(line, LinearLayout.LayoutParams(MATCH, WRAP))
    return line
}
