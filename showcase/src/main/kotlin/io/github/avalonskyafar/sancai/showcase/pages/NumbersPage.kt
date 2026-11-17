package io.github.avalonskyafar.sancai.showcase.pages

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiControlRow
import io.github.avalonskyafar.sancai.widget.SancaiProgressBar
import io.github.avalonskyafar.sancai.widget.SancaiSlider
import io.github.avalonskyafar.sancai.widget.SancaiSpinner
import io.github.avalonskyafar.sancai.widget.SancaiStepper
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiSwitch
import io.github.avalonskyafar.sancai.widget.SancaiText
import kotlin.math.roundToInt

/** Numbers and progress: stepper, slider, progress bar and spinner. */
object NumbersPage : DemoPage("c_numbers", R.string.page_c_numbers_title, R.string.page_c_numbers_summary) {
    override fun build(page: PageScope) {
        page.title()
        steppers(page)
        val followers = ArrayList<(Float) -> Unit>()
        val level = sliders(page, followers)
        progress(page, level.value / 100f, followers)
        spinners(page)
        page.caption(page.str(R.string.numbers_origin), topDp = 22)
    }
}

private const val INSET = 7

/** A labelled block inside a card: the label, a live value at its end, the control below. */
private fun SancaiSurface.block(
    page: PageScope,
    @StringRes label: Int,
    control: View,
    first: Boolean = false,
    gapDp: Int = 6,
): SancaiText {
    val value = page.text("", SancaiTextRole.BODY_COMPACT, SancaiTextTone.MUTED)
    val head = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(page.dp(INSET), 0, page.dp(INSET), 0)
        addView(page.text(page.str(label), SancaiTextRole.LABEL), LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
        addView(value, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP).apply { marginStart = page.dp(12) })
    }
    addView(head, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(if (first) 6 else 14) })
    addView(control, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(gapDp) })
    return value
}

private fun steppers(page: PageScope) {
    val context = page.context
    page.section(R.string.numbers_stepper_title, R.string.numbers_stepper_detail) {
        val delay = SancaiStepper(context).apply {
            setRange(0f, 2000f)
            stepSize = 50f
            unit = " ms"
            value = 250f
        }
        val ratio = SancaiStepper(context).apply {
            setRange(0f, 1f)
            stepSize = 0.01f
            decimals = 2
            value = 0.35f
            glaze = page.theme.glazes.celadon
        }
        val copies = SancaiStepper(context).apply {
            setRange(1f, 10f)
            stepSize = 1f
            value = 10f
            glaze = page.theme.glazes.ochre
        }
        block(page, R.string.numbers_stepper_delay, delay, first = true)
        block(page, R.string.numbers_stepper_ratio, ratio)
        block(page, R.string.numbers_stepper_copies, copies)
        val status = status(page, page.str(R.string.numbers_stepper_idle))
        val labels = mapOf(
            delay to R.string.numbers_stepper_delay,
            ratio to R.string.numbers_stepper_ratio,
            copies to R.string.numbers_stepper_copies,
        )
        for ((stepper, label) in labels) {
            stepper.setOnValueChangeListener { view, _ ->
                status.text = context.getString(R.string.numbers_stepper_changed, page.str(label), view.formattedValue)
            }
            stepper.setOnEditRequestListener { view ->
                page.host.say(context.getString(R.string.numbers_stepper_edit, page.str(label), view.formattedValue))
            }
        }
    }
    page.code(
        """
        stepper.setRange(0f, 1f)
        stepper.stepSize = 0.01f
        stepper.decimals = 2
        stepper.setOnValueChangeListener { view, _ ->
            title = view.formattedValue
        }
        """,
    )
    page.caption(page.str(R.string.numbers_stepper_note))
}

/**
 * Builds the slider section. Returns the first slider; every function in [followers] is
 * called with its share (0..1) when the user moves it.
 */
private fun sliders(page: PageScope, followers: List<(Float) -> Unit>): SancaiSlider {
    val context = page.context
    val level = SancaiSlider(context).apply {
        valueFrom = 0f
        valueTo = 100f
        value = 40f
        valueFormatter = { percent(it / 100f) }
    }
    val volume = SancaiSlider(context).apply {
        valueFrom = 0f
        valueTo = 10f
        stepSize = 1f
        value = 4f
        glaze = page.theme.glazes.celadon
    }
    val names = context.resources.getStringArray(R.array.numbers_slider_strictness_names)
    val strictness = SancaiSlider(context).apply {
        valueFrom = 0f
        valueTo = 3f
        stepSize = 1f
        value = 1f
        setGlazeScale(page.theme.glazes.scale(4))
        valueFormatter = { names[it.roundToInt()] }
    }
    page.section(R.string.numbers_slider_title, R.string.numbers_slider_detail) {
        val levelValue = block(page, R.string.numbers_slider_level, level, first = true, gapDp = 0)
        val volumeValue = block(page, R.string.numbers_slider_volume, volume, gapDp = 0)
        val strictnessValue = block(page, R.string.numbers_slider_strictness, strictness, gapDp = 0)
        levelValue.text = percent(level.value / 100f)
        volumeValue.text = volume.value.roundToInt().toString()
        strictnessValue.text = names[strictness.value.roundToInt()]
        level.setOnValueChangeListener { _, value ->
            levelValue.text = percent(value / 100f)
            followers.forEach { it(value / 100f) }
        }
        volume.setOnValueChangeListener { _, value ->
            volumeValue.text = value.roundToInt().toString()
        }
        strictness.setOnValueChangeListener { _, value ->
            strictnessValue.text = names[value.roundToInt()]
        }
    }
    page.code(
        """
        slider.valueTo = 3f
        slider.stepSize = 1f
        slider.setGlazeScale(
            SancaiTheme.of(this).glazes.scale(4)
        )
        slider.setOnValueChangeListener { _, v ->
            title = "Level " + v
        }
        """,
    )
    return level
}

private const val RUN_STEPS = 8
private const val RUN_STEP_MS = 340L

private fun progress(page: PageScope, share: Float, followers: MutableList<(Float) -> Unit>) {
    val context = page.context
    val glazes = page.theme.glazes
    page.section(R.string.numbers_progress_title, R.string.numbers_progress_detail) {
        val follow = SancaiProgressBar(context).apply { progress = share }
        val followValue = block(page, R.string.numbers_progress_follow, follow, first = true)
        followValue.text = percent(share)

        val task = SancaiProgressBar(context).apply { glaze = glazes.celadon }
        val taskValue = block(page, R.string.numbers_progress_task, task)
        taskValue.text = percent(0f)
        val run = SancaiButton(context).apply { text = page.str(R.string.numbers_progress_run) }
        addView(
            run,
            LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP).apply {
                topMargin = page.dp(8)
                marginStart = page.dp(INSET - 2)
            },
        )
        run.setOnClickListener {
            run.isEnabled = false
            task.setProgress(0f, animate = false)
            taskValue.text = percent(0f)
            for (step in 1..RUN_STEPS) {
                task.postDelayed({
                    val done = step.toFloat() / RUN_STEPS
                    task.setProgress(done, animate = true)
                    taskValue.text = if (step == RUN_STEPS) page.str(R.string.numbers_progress_done) else percent(done)
                    if (step == RUN_STEPS) {
                        run.isEnabled = true
                        run.text = page.str(R.string.numbers_progress_again)
                    }
                }, step * RUN_STEP_MS)
            }
        }

        val unknown = SancaiProgressBar(context).apply { isIndeterminate = true }
        block(page, R.string.numbers_progress_unknown, unknown)

        // Mist is left out: on a light surface its fill is too pale to read as progress.
        val others = listOf(glazes.terracotta, glazes.ochre).map { other ->
            SancaiProgressBar(context).apply {
                glaze = other
                progress = share
            }
        }
        block(page, R.string.numbers_progress_glazes, others[0])
        addView(others[1], LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
        followers += { value ->
            follow.setProgress(value, animate = false)
            followValue.text = percent(value)
            others.forEach { it.setProgress(value, animate = false) }
        }
    }
    page.code(
        """
        bar.glaze =
            SancaiTheme.of(this).glazes.celadon
        bar.setProgress(0.6f, animate = true)

        waiting.isIndeterminate = true
        """,
    )
}

private fun spinnerRow(page: PageScope, spinners: List<Pair<SancaiSpinner, Int>>): LinearLayout =
    LinearLayout(page.context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(page.dp(INSET), page.dp(4), page.dp(INSET), page.dp(2))
        for ((spinner, sizeDp) in spinners) {
            addView(
                spinner,
                LinearLayout.LayoutParams(page.dp(sizeDp), page.dp(sizeDp)).apply { marginEnd = page.dp(22) },
            )
        }
    }

private fun spinners(page: PageScope) {
    val context = page.context
    val save = SancaiButton(context).apply {
        variant = SancaiButton.Variant.PRIMARY
        text = page.str(R.string.numbers_spinner_save)
        isLoading = true
        setOnClickListener { page.host.say(page.str(R.string.numbers_spinner_saved)) }
    }
    page.section(R.string.numbers_spinner_title, R.string.numbers_spinner_detail) {
        val sizes = listOf(18, 28, 44).map { SancaiSpinner(context) to it }
        val tones = listOf(SancaiTextTone.INK, SancaiTextTone.ACCENT, SancaiTextTone.SUCCESS, SancaiTextTone.DANGER)
            .map { SancaiSpinner(context).apply { tone = it } to 28 }
        block(page, R.string.numbers_spinner_sizes, spinnerRow(page, sizes), first = true)
        block(page, R.string.numbers_spinner_tones, spinnerRow(page, tones))
        addDivider(10f)
        val busy = SancaiSwitch(context).apply { setChecked(true, animate = false) }
        busy.setOnCheckedChangeListener { _, checked ->
            save.isLoading = checked
        }
        val row = SancaiControlRow(context).apply {
            title = page.str(R.string.numbers_spinner_busy)
            detail = page.str(R.string.numbers_spinner_busy_detail)
            alignment = SancaiControlRow.Alignment.START
            setControl(busy)
        }
        addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
    }
    page.loose(save, topDp = 16, sideDp = PageScope.BUTTON_SIDE)
    page.code(
        """
        spinner.tone = SancaiTextTone.ACCENT

        save.variant = SancaiButton.Variant.PRIMARY
        save.isLoading = true   // ignores clicks
        """,
    )
}

private fun percent(share: Float): String = "${(share * 100f).roundToInt()} %"

/** A muted status line under a divider at the foot of a card. */
private fun SancaiSurface.status(page: PageScope, text: CharSequence): SancaiText {
    val line = page.text(text, SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
    line.setPadding(page.dp(INSET), 0, page.dp(INSET), page.dp(4))
    addDivider(10f)
    addView(line, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
    return line
}
