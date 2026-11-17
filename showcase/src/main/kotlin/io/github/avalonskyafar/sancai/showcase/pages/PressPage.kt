package io.github.avalonskyafar.sancai.showcase.pages

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.interaction.SancaiPress
import io.github.avalonskyafar.sancai.material.SancaiWellDrawable
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiEntryRow
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiSwitch
import io.github.avalonskyafar.sancai.widget.SancaiText

/** The press that sinks, the five haptic effects, and which of listener and tick comes first. */
object PressPage : DemoPage("i_press", R.string.page_i_press_title, R.string.page_i_press_summary) {
    override fun build(page: PageScope) {
        page.title()
        sinking(page)
        haptics(page)
        order(page)
        page.caption(page.str(R.string.press_source), topDp = 22)
    }

    /** Two entry rows, a row put together by hand, and a button: all sink the same way. */
    private fun sinking(page: PageScope) {
        val status = page.muted(page.str(R.string.press_sink_idle))
        fun pressed(name: Int) {
            status.text = page.context.getString(R.string.press_sink_last, page.str(name))
        }
        val lists = SancaiEntryRow(page.context).apply {
            title = page.str(R.string.press_sink_lists)
            detail = page.str(R.string.press_sink_lists_detail)
            alignment = SancaiEntryRow.Alignment.START
            arrow = SancaiEntryRow.Arrow.END
            setOnClickListener { pressed(R.string.press_sink_lists) }
        }
        val apps = SancaiEntryRow(page.context).apply {
            title = page.str(R.string.press_sink_apps)
            value = page.str(R.string.press_sink_apps_value)
            alignment = SancaiEntryRow.Alignment.START
            arrow = SancaiEntryRow.Arrow.END
            setOnClickListener { pressed(R.string.press_sink_apps) }
        }
        val button = SancaiButton(page.context).apply {
            text = page.str(R.string.press_sink_button)
            setOnClickListener { pressed(R.string.press_sink_button) }
        }
        page.section(R.string.press_sink_title, R.string.press_sink_detail) {
            addFull(lists)
            addDivider()
            addFull(apps)
            addDivider()
            addFull(ownRow(page) { pressed(R.string.press_sink_own) })
        }
        page.loose(status, topDp = 8, sideDp = PageScope.TEXT_INSET)
        page.loose(button, topDp = 14, sideDp = PageScope.BUTTON_SIDE)
        page.caption(page.str(R.string.press_sink_note), topDp = 12)
        page.code(SINK_CODE)
    }

    /** A row that is not a library widget: the host takes the touch, only its content sinks. */
    private fun ownRow(page: PageScope, onPress: () -> Unit): View {
        var presses = 0
        fun counted() = page.context.resources.getQuantityString(R.plurals.press_sink_own_count, presses, presses)
        val count = page.muted(counted())
        val content = LinearLayout(page.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(page.dp(OWN_ROW_START), page.dp(10), page.dp(OWN_ROW_END), page.dp(10))
            addView(page.text(page.str(R.string.press_sink_own), SancaiTextRole.BODY), LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
            addView(count, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP).apply { marginStart = page.dp(12) })
        }
        val row = FrameLayout(page.context).apply {
            minimumHeight = page.dp(SancaiEntryRow.MIN_HEIGHT_DP.toInt())
            addView(content, FrameLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP, Gravity.CENTER_VERTICAL))
        }
        SancaiPress.attach(row, content) {
            presses++
            count.text = counted()
            onPress()
        }
        return row
    }

    /** One button per effect with its meaning beside it. */
    private fun haptics(page: PageScope) {
        val status = page.muted(page.str(R.string.press_haptics_idle))
        fun effect(name: String, meaning: Int, play: (View) -> Unit): View {
            val button = SancaiButton(page.context).apply {
                text = name
                setOnClickListener { view ->
                    play(view)
                    val line = if (page.theme.haptics) R.string.press_haptics_played else R.string.press_haptics_muted
                    status.text = page.context.getString(line, name)
                }
            }
            return LinearLayout(page.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPaddingRelative(page.dp(3), page.dp(4), page.dp(7), page.dp(4))
                addView(button, LinearLayout.LayoutParams(page.dp(EFFECT_BUTTON_WIDTH), PageScope.WRAP))
                addView(
                    page.muted(page.str(meaning)),
                    LinearLayout.LayoutParams(0, PageScope.WRAP, 1f).apply { marginStart = page.dp(12) },
                )
            }
        }
        page.section(R.string.press_haptics_title, R.string.press_haptics_detail) {
            addFull(effect("tick", R.string.press_haptics_tick) { SancaiHaptics.tick(it) })
            addFull(effect("detent", R.string.press_haptics_detent) { SancaiHaptics.detent(it) })
            addFull(effect("confirm", R.string.press_haptics_confirm) { SancaiHaptics.confirm(it) })
            addFull(effect("reject", R.string.press_haptics_reject) { SancaiHaptics.reject(it) })
            addFull(effect("longPress", R.string.press_haptics_long_press) { SancaiHaptics.longPress(it) })
        }
        page.loose(status, topDp = 8, sideDp = PageScope.TEXT_INSET)
        page.caption(page.str(R.string.press_haptics_note), topDp = 6)
        page.code(HAPTICS_CODE)
    }

    /** A switch and a button write to one log; each line says which came first. */
    private fun order(page: PageScope) {
        val lines = ArrayDeque<String>()
        var count = 0
        val log = page.text(page.str(R.string.press_order_empty), SancaiTextRole.CAPTION, SancaiTextTone.MUTED).apply {
            background = SancaiWellDrawable(page.context)
            minLines = LOG_LINES
            setPadding(page.dp(13), page.dp(11), page.dp(13), page.dp(11))
        }
        fun append(line: Int) {
            count++
            lines.addLast(page.context.getString(R.string.press_order_line, count, page.str(line)))
            if (lines.size > LOG_LINES) lines.removeFirst()
            log.tone = SancaiTextTone.INK
            log.text = lines.joinToString("\n")
        }
        val switch = SancaiSwitch(page.context).apply {
            setOnCheckedChangeListener { _, checked ->
                append(if (checked) R.string.press_order_switch_on else R.string.press_order_switch_off)
            }
        }
        val button = SancaiButton(page.context).apply {
            text = page.str(R.string.press_order_button)
            setOnClickListener { append(R.string.press_order_button_line) }
        }
        val switchRow = LinearLayout(page.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = page.dp(56)
            setPaddingRelative(page.dp(7), page.dp(6), page.dp(3), page.dp(6))
            addView(page.text(page.str(R.string.press_order_switch), SancaiTextRole.BODY), LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
            addView(switch, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP).apply { marginStart = page.dp(12) })
        }
        page.section(R.string.press_order_title, R.string.press_order_detail) {
            addFull(switchRow)
            addDivider()
            addView(button, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply {
                setMargins(page.dp(21), page.dp(10), page.dp(21), page.dp(6))
            })
        }
        page.loose(log, topDp = 10)
        page.caption(page.str(R.string.press_order_note))
        page.code(ORDER_CODE)
    }
}

private const val OWN_ROW_START = 14
private const val OWN_ROW_END = 16
private const val EFFECT_BUTTON_WIDTH = 124
private const val LOG_LINES = 4

private const val SINK_CODE = """
    val row = SancaiEntryRow(this)
    row.title = "Filter lists"
    row.setOnClickListener { row.value = "Pressed" }

    val content = TextView(this)
    val own = FrameLayout(this)
    own.addView(content)
    SancaiPress.attach(own, content) {
        content.text = "Pressed"
    }
"""

private const val HAPTICS_CODE = """
    val save = SancaiButton(this)
    save.setOnClickListener { view ->
        val valid = title.isNotEmpty()
        if (valid) SancaiHaptics.confirm(view)
        else SancaiHaptics.reject(view)
    }
    SancaiHaptics.tick(save)
    SancaiHaptics.detent(save)
    SancaiHaptics.longPress(save)
"""

private const val ORDER_CODE = """
    val log = SancaiText(this)
    val switch = SancaiSwitch(this)
    switch.setOnCheckedChangeListener { _, _ ->
        log.append("listener, then tick\n")
    }
    val button = SancaiButton(this)
    button.setOnClickListener {
        log.append("tick, then listener\n")
    }
"""

private fun PageScope.muted(value: CharSequence): SancaiText = text(value, SancaiTextRole.CAPTION, SancaiTextTone.MUTED)

private fun SancaiSurface.addFull(view: View) {
    addView(view, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
}
