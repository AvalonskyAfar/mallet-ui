package io.github.avalonskyafar.mallet.showcase.pages

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.widget.MalletButton
import io.github.avalonskyafar.mallet.widget.MalletControlRow
import io.github.avalonskyafar.mallet.widget.MalletProgressBar
import io.github.avalonskyafar.mallet.widget.MalletSlider
import io.github.avalonskyafar.mallet.widget.MalletSpinner
import io.github.avalonskyafar.mallet.widget.MalletStepper
import io.github.avalonskyafar.mallet.widget.MalletSurface
import io.github.avalonskyafar.mallet.widget.MalletSwitch
import io.github.avalonskyafar.mallet.widget.MalletText
import java.text.NumberFormat
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
private fun MalletSurface.block(
    page: PageScope,
    @StringRes label: Int,
    control: View,
    first: Boolean = false,
    gapDp: Int = 6,
): MalletText {
    val value = page.text("", MalletTextRole.BODY_COMPACT, MalletTextTone.MUTED)
    val head = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(page.dp(INSET), 0, page.dp(INSET), 0)
        addView(page.text(page.str(label), MalletTextRole.LABEL), LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
        addView(value, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP).apply { marginStart = page.dp(12) })
    }
    addView(head, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(if (first) 6 else 14) })
    addView(control, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(gapDp) })
    return value
}

private fun steppers(page: PageScope) {
    val context = page.context
    page.section(R.string.numbers_stepper_title, R.string.numbers_stepper_detail) {
        val delay = MalletStepper(context).apply {
            setRange(0f, 2000f)
            stepSize = 50f
            unit = " ms"
            value = 250f
        }
        val ratio = MalletStepper(context).apply {
            setRange(0f, 1f)
            stepSize = 0.01f
            decimals = 2
            value = 0.35f
            hue = page.theme.hues.jade
        }
        val copies = MalletStepper(context).apply {
            setRange(1f, 10f)
            stepSize = 1f
            value = 10f
            hue = page.theme.hues.ochre
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
private fun sliders(page: PageScope, followers: List<(Float) -> Unit>): MalletSlider {
    val context = page.context
    val level = MalletSlider(context).apply {
        valueFrom = 0f
        valueTo = 100f
        value = 40f
        valueFormatter = { percent(it / 100f) }
    }
    val volume = MalletSlider(context).apply {
        valueFrom = 0f
        valueTo = 10f
        stepSize = 1f
        value = 4f
        hue = page.theme.hues.jade
    }
    val names = context.resources.getStringArray(R.array.numbers_slider_strictness_names)
    val strictness = MalletSlider(context).apply {
        valueFrom = 0f
        valueTo = 3f
        stepSize = 1f
        value = 1f
        setHueScale(page.theme.hues.scale(4))
        valueFormatter = { names[it.roundToInt()] }
    }
    page.section(R.string.numbers_slider_title, R.string.numbers_slider_detail) {
        val levelValue = block(page, R.string.numbers_slider_level, level, first = true, gapDp = 0)
        val volumeValue = block(page, R.string.numbers_slider_volume, volume, gapDp = 0)
        val strictnessValue = block(page, R.string.numbers_slider_strictness, strictness, gapDp = 0)
        levelValue.text = percent(level.value / 100f)
        volumeValue.text = whole(volume.value)
        strictnessValue.text = names[strictness.value.roundToInt()]
        level.setOnValueChangeListener { _, value ->
            levelValue.text = percent(value / 100f)
            followers.forEach { it(value / 100f) }
        }
        volume.setOnValueChangeListener { _, value ->
            volumeValue.text = whole(value)
        }
        strictness.setOnValueChangeListener { _, value ->
            strictnessValue.text = names[value.roundToInt()]
        }
    }
    page.code(
        """
        slider.valueTo = 3f
        slider.stepSize = 1f
        slider.setHueScale(
            MalletTheme.of(this).hues.scale(4)
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
    val hues = page.theme.hues
    page.section(R.string.numbers_progress_title, R.string.numbers_progress_detail) {
        val follow = MalletProgressBar(context).apply { progress = share }
        val followValue = block(page, R.string.numbers_progress_follow, follow, first = true)
        followValue.text = percent(share)

        val task = MalletProgressBar(context).apply { hue = hues.jade }
        val taskValue = block(page, R.string.numbers_progress_task, task)
        taskValue.text = percent(0f)
        val run = MalletButton(context).apply { text = page.str(R.string.numbers_progress_run) }
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

        val unknown = MalletProgressBar(context).apply { isIndeterminate = true }
        block(page, R.string.numbers_progress_unknown, unknown)

        // Mist is left out: on a light surface its fill is too pale to read as progress.
        val others = listOf(hues.bronze, hues.ochre).map { other ->
            MalletProgressBar(context).apply {
                hue = other
                progress = share
            }
        }
        block(page, R.string.numbers_progress_hues, others[0])
        addView(others[1], LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
        followers += { value ->
            follow.setProgress(value, animate = false)
            followValue.text = percent(value)
            others.forEach { it.setProgress(value, animate = false) }
        }
    }
    page.code(
        """
        bar.hue =
            MalletTheme.of(this).hues.jade
        bar.setProgress(0.6f, animate = true)

        waiting.isIndeterminate = true
        """,
    )
}

private fun spinnerRow(page: PageScope, spinners: List<Pair<MalletSpinner, Int>>): LinearLayout =
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
    val save = MalletButton(context).apply {
        variant = MalletButton.Variant.PRIMARY
        text = page.str(R.string.numbers_spinner_save)
        isLoading = true
        setOnClickListener { page.host.say(page.str(R.string.numbers_spinner_saved)) }
    }
    page.section(R.string.numbers_spinner_title, R.string.numbers_spinner_detail) {
        val sizes = listOf(18, 28, 44).map { MalletSpinner(context) to it }
        val tones = listOf(MalletTextTone.INK, MalletTextTone.ACCENT, MalletTextTone.SUCCESS, MalletTextTone.DANGER)
            .map { MalletSpinner(context).apply { tone = it } to 28 }
        block(page, R.string.numbers_spinner_sizes, spinnerRow(page, sizes), first = true)
        block(page, R.string.numbers_spinner_tones, spinnerRow(page, tones))
        addDivider(10f)
        val busy = MalletSwitch(context).apply { setChecked(true, animate = false) }
        busy.setOnCheckedChangeListener { _, checked ->
            save.isLoading = checked
        }
        val row = MalletControlRow(context).apply {
            title = page.str(R.string.numbers_spinner_busy)
            detail = page.str(R.string.numbers_spinner_busy_detail)
            alignment = MalletControlRow.Alignment.START
            setControl(busy)
        }
        addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
    }
    page.loose(save, topDp = 16, sideDp = PageScope.BUTTON_SIDE)
    page.code(
        """
        spinner.tone = MalletTextTone.ACCENT

        save.variant = MalletButton.Variant.PRIMARY
        save.isLoading = true   // ignores clicks
        """,
    )
}

/** A share as the language of the device writes a percentage: "40%" in English, "40 %" in German. */
private fun percent(share: Float): String = NumberFormat.getPercentInstance().format(share.toDouble())

private fun whole(value: Float): String = NumberFormat.getIntegerInstance().format(value.roundToInt().toLong())

/** A muted status line under a divider at the foot of a card. */
private fun MalletSurface.status(page: PageScope, text: CharSequence): MalletText {
    val line = page.text(text, MalletTextRole.CAPTION, MalletTextTone.MUTED)
    line.setPadding(page.dp(INSET), 0, page.dp(INSET), page.dp(4))
    addDivider(10f)
    addView(line, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
    return line
}
