package io.github.avalonskyafar.mallet.showcase.pages

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.interaction.MalletHaptics
import io.github.avalonskyafar.mallet.interaction.MalletPress
import io.github.avalonskyafar.mallet.material.MalletWellDrawable
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.widget.MalletButton
import io.github.avalonskyafar.mallet.widget.MalletEntryRow
import io.github.avalonskyafar.mallet.widget.MalletSurface
import io.github.avalonskyafar.mallet.widget.MalletSwitch
import io.github.avalonskyafar.mallet.widget.MalletText

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
        val lists = MalletEntryRow(page.context).apply {
            title = page.str(R.string.press_sink_lists)
            detail = page.str(R.string.press_sink_lists_detail)
            alignment = MalletEntryRow.Alignment.START
            arrow = MalletEntryRow.Arrow.END
            setOnClickListener { pressed(R.string.press_sink_lists) }
        }
        val apps = MalletEntryRow(page.context).apply {
            title = page.str(R.string.press_sink_apps)
            value = page.str(R.string.press_sink_apps_value)
            alignment = MalletEntryRow.Alignment.START
            arrow = MalletEntryRow.Arrow.END
            setOnClickListener { pressed(R.string.press_sink_apps) }
        }
        val button = MalletButton(page.context).apply {
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
            addView(page.text(page.str(R.string.press_sink_own), MalletTextRole.BODY), LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
            addView(count, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP).apply { marginStart = page.dp(12) })
        }
        val row = FrameLayout(page.context).apply {
            minimumHeight = page.dp(MalletEntryRow.MIN_HEIGHT_DP.toInt())
            addView(content, FrameLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP, Gravity.CENTER_VERTICAL))
        }
        MalletPress.attach(row, content) {
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
            val button = MalletButton(page.context).apply {
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
            addFull(effect("tick", R.string.press_haptics_tick) { MalletHaptics.tick(it) })
            addFull(effect("detent", R.string.press_haptics_detent) { MalletHaptics.detent(it) })
            addFull(effect("confirm", R.string.press_haptics_confirm) { MalletHaptics.confirm(it) })
            addFull(effect("reject", R.string.press_haptics_reject) { MalletHaptics.reject(it) })
            addFull(effect("longPress", R.string.press_haptics_long_press) { MalletHaptics.longPress(it) })
        }
        page.loose(status, topDp = 8, sideDp = PageScope.TEXT_INSET)
        page.caption(page.str(R.string.press_haptics_note), topDp = 6)
        page.code(HAPTICS_CODE)
    }

    /** A switch and a button write to one log; each line says which came first. */
    private fun order(page: PageScope) {
        val lines = ArrayDeque<String>()
        var count = 0
        val log = page.text(page.str(R.string.press_order_empty), MalletTextRole.CAPTION, MalletTextTone.MUTED).apply {
            background = MalletWellDrawable(page.context)
            minLines = LOG_LINES
            setPadding(page.dp(13), page.dp(11), page.dp(13), page.dp(11))
        }
        fun append(line: Int) {
            count++
            lines.addLast(page.context.getString(R.string.press_order_line, count, page.str(line)))
            if (lines.size > LOG_LINES) lines.removeFirst()
            log.tone = MalletTextTone.INK
            log.text = lines.joinToString("\n")
        }
        val switch = MalletSwitch(page.context).apply {
            setOnCheckedChangeListener { _, checked ->
                append(if (checked) R.string.press_order_switch_on else R.string.press_order_switch_off)
            }
        }
        val button = MalletButton(page.context).apply {
            text = page.str(R.string.press_order_button)
            setOnClickListener { append(R.string.press_order_button_line) }
        }
        val switchRow = LinearLayout(page.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = page.dp(56)
            setPaddingRelative(page.dp(7), page.dp(6), page.dp(3), page.dp(6))
            addView(page.text(page.str(R.string.press_order_switch), MalletTextRole.BODY), LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
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
    val row = MalletEntryRow(this)
    row.title = "Filter lists"
    row.setOnClickListener { row.value = "Pressed" }

    val content = TextView(this)
    val own = FrameLayout(this)
    own.addView(content)
    MalletPress.attach(own, content) {
        content.text = "Pressed"
    }
"""

private const val HAPTICS_CODE = """
    val save = MalletButton(this)
    save.setOnClickListener { view ->
        val valid = title.isNotEmpty()
        if (valid) MalletHaptics.confirm(view)
        else MalletHaptics.reject(view)
    }
    MalletHaptics.tick(save)
    MalletHaptics.detent(save)
    MalletHaptics.longPress(save)
"""

private const val ORDER_CODE = """
    val log = MalletText(this)
    val switch = MalletSwitch(this)
    switch.setOnCheckedChangeListener { _, _ ->
        log.append("listener, then tick\n")
    }
    val button = MalletButton(this)
    button.setOnClickListener {
        log.append("tick, then listener\n")
    }
"""

private fun PageScope.muted(value: CharSequence): MalletText = text(value, MalletTextRole.CAPTION, MalletTextTone.MUTED)

private fun MalletSurface.addFull(view: View) {
    addView(view, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
}
