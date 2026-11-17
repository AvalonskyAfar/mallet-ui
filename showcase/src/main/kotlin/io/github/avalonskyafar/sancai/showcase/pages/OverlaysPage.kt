package io.github.avalonskyafar.sancai.showcase.pages

import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.overlay.SancaiBottomSheet
import io.github.avalonskyafar.sancai.overlay.SancaiDialog
import io.github.avalonskyafar.sancai.overlay.SancaiFloatingPanel
import io.github.avalonskyafar.sancai.overlay.SancaiMenu
import io.github.avalonskyafar.sancai.overlay.SancaiToast
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiSwitch
import io.github.avalonskyafar.sancai.widget.SancaiText
import kotlin.math.roundToInt

/**
 * Dialogs, sheets, menus, toasts and the floating panel. Nothing is open when the page
 * appears: each card holds the buttons that open one kind of overlay, and one status line at
 * the top says what the user did with it.
 */
object OverlaysPage : DemoPage("c_overlays", R.string.page_c_overlays_title, R.string.page_c_overlays_summary) {
    override fun build(page: PageScope) {
        page.title()
        val status = Status(page)
        status.addTo(page)
        dialogs(page, status)
        sheets(page, status)
        menus(page, status)
        toasts(page, status)
        floatingPanel(page, status)
        page.caption(page.str(R.string.overlays_origin), topDp = 22)
    }
}

/** The shared status line. While it is scrolled out of sight, a toast carries the result. */
private class Status(private val page: PageScope) {
    private val line: SancaiText =
        page.text(page.str(R.string.overlays_status_idle), SancaiTextRole.BODY_COMPACT)

    /** Counts the results written so far; a late report uses it to see that it is out of date. */
    var stamp = 0
        private set

    fun addTo(page: PageScope) {
        val card = SancaiSurface(page.context)
        card.addView(page.text(page.str(R.string.overlays_status_label), SancaiTextRole.CAPTION, SancaiTextTone.MUTED))
        card.addView(line, below(page, 5))
        page.loose(card, topDp = 16)
    }

    /** Writes [text]; with [echo] it is also shown as a toast when the line cannot be seen. */
    fun set(text: CharSequence, echo: Boolean = true) {
        stamp++
        line.text = text
        if (echo && !line.getGlobalVisibleRect(Rect())) SancaiToast.show(page.host.activity, text)
    }

    fun set(resId: Int, echo: Boolean = true) = set(page.str(resId), echo)
}

private fun below(page: PageScope, topDp: Int): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply { topMargin = page.dp(topDp) }

private fun button(
    page: PageScope,
    label: Int,
    variant: SancaiButton.Variant = SancaiButton.Variant.NEUTRAL,
    icon: SancaiIcon? = null,
    onClick: (SancaiButton) -> Unit,
): SancaiButton = SancaiButton(page.context).apply {
    this.variant = variant
    this.icon = icon
    text = page.str(label)
    setOnClickListener { onClick(this) }
}

/** Lays [buttons] out two to a row, in equal halves, 10 dp apart. */
private fun SancaiSurface.addButtons(page: PageScope, vararg buttons: SancaiButton) {
    buttons.toList().chunked(2).forEachIndexed { rowIndex, pair ->
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        pair.forEachIndexed { index, button ->
            val params = LinearLayout.LayoutParams(0, PageScope.WRAP, 1f)
            if (index > 0) params.marginStart = page.dp(10)
            row.addView(button, params)
        }
        // A lone button keeps the width of a half, so the grid stays a grid.
        if (pair.size == 1) {
            row.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = page.dp(10) })
        }
        addView(row, below(page, if (rowIndex > 0) 10 else 0))
    }
}

private fun dialogs(page: PageScope, status: Status) {
    val activity = page.host.activity
    page.section(R.string.overlays_dialogs_title, R.string.overlays_dialogs_detail) {
        val confirm = button(page, R.string.overlays_dialog_confirm) {
            SancaiDialog.Builder(activity)
                .title(page.str(R.string.overlays_restore_title))
                .message(page.str(R.string.overlays_restore_message))
                .positive(page.str(R.string.overlays_restore_action))
                .negative()
                .onDismiss { _, positive ->
                    status.set(if (positive) R.string.overlays_restore_done else R.string.overlays_cancelled)
                }
                .show()
        }
        val delete = button(page, R.string.overlays_dialog_delete, SancaiButton.Variant.DANGER) {
            var deleted = false
            SancaiDialog.Builder(activity)
                .title(page.str(R.string.overlays_delete_title))
                .message(page.str(R.string.overlays_delete_message))
                .positive(page.str(R.string.overlays_delete_action), SancaiTextTone.DANGER) {
                    deleted = true
                    true
                }
                .negative()
                .onDismiss { _, _ ->
                    status.set(if (deleted) R.string.overlays_delete_done else R.string.overlays_cancelled)
                }
                .show()
        }
        val input = button(page, R.string.overlays_dialog_input) { showInput(page, status) }
        val three = button(page, R.string.overlays_dialog_three) { showThree(page, status) }
        addButtons(page, confirm, delete, input, three)
    }
    page.code("""
        SancaiDialog.confirm(this, "Delete this rule?",
            danger = true) { deleteRule() }
        SancaiDialog.Builder(this).title("Name the rule")
            .input(hint = "Rule name") { dialog, text ->
                val ok = text.length >= 2
                if (!ok) dialog.showError("Too short")
                ok
            }
            .show()
    """)
}

/** An input dialog that refuses names shorter than two characters. */
private fun showInput(page: PageScope, status: Status) {
    SancaiDialog.Builder(page.host.activity)
        .title(page.str(R.string.overlays_name_title))
        .message(page.str(R.string.overlays_name_message))
        .input(hint = page.str(R.string.overlays_name_hint)) { dialog, text ->
            val name = text.trim()
            if (name.length < 2) {
                dialog.showError(page.str(R.string.overlays_name_error))
                status.set(R.string.overlays_name_rejected, echo = false)
                false
            } else {
                status.set(page.context.getString(R.string.overlays_name_done, name))
                true
            }
        }
        .onDismiss { _, positive -> if (!positive) status.set(R.string.overlays_cancelled) }
        .show()
}

/** Save, discard or cancel: the neutral action sits between the other two. */
private fun showThree(page: PageScope, status: Status) {
    var picked = R.string.overlays_cancelled
    SancaiDialog.Builder(page.host.activity)
        .title(page.str(R.string.overlays_keep_title))
        .message(page.str(R.string.overlays_keep_message))
        .positive(page.str(R.string.overlays_keep_save)) {
            picked = R.string.overlays_keep_saved
            true
        }
        .neutral(page.str(R.string.overlays_keep_discard)) {
            picked = R.string.overlays_keep_discarded
            true
        }
        .negative()
        .onDismiss { _, _ -> status.set(picked) }
        .show()
}

private fun sheets(page: PageScope, status: Status) {
    val activity = page.host.activity
    // The custom sheet edits this value; it is kept, so the sheet reopens where it was left.
    var strictness = 1
    val levels = intArrayOf(R.string.overlays_level_gentle, R.string.overlays_level_standard, R.string.overlays_level_strict)
    page.section(R.string.overlays_sheets_title, R.string.overlays_sheets_detail) {
        val actions = button(page, R.string.overlays_sheet_actions) {
            var picked = R.string.overlays_sheet_closed
            SancaiBottomSheet.Builder(activity)
                .title(page.str(R.string.overlays_share_title))
                .action(page.str(R.string.overlays_action_copy), icon = SancaiIcon.COPY) {
                    picked = R.string.overlays_picked_copy
                }
                .action(page.str(R.string.overlays_action_share), icon = SancaiIcon.SHARE) {
                    picked = R.string.overlays_picked_share
                }
                .action(page.str(R.string.overlays_action_download), icon = SancaiIcon.DOWNLOAD, enabled = false)
                .action(page.str(R.string.overlays_action_delete), SancaiTextTone.DANGER, SancaiIcon.TRASH) {
                    picked = R.string.overlays_picked_delete
                }
                .cancel()
                .onDismiss { status.set(picked) }
                .show()
        }
        val custom = button(page, R.string.overlays_sheet_custom) {
            val track = SancaiSegmentedTrack(page.context).apply {
                setOptions(*levels.map { page.str(it) }.toTypedArray())
                select(strictness, animate = false)
                setOnSelectionChangeListener { _, index -> strictness = index }
            }
            val done = SancaiButton(page.context).apply {
                variant = SancaiButton.Variant.PRIMARY
                text = page.str(R.string.overlays_sheet_done)
            }
            val content = LinearLayout(page.context).apply {
                orientation = LinearLayout.VERTICAL
                addView(track, below(page, 0))
                addView(done, below(page, 16))
            }
            val sheet = SancaiBottomSheet.Builder(activity)
                .title(page.str(R.string.overlays_strictness_title))
                .message(page.str(R.string.overlays_strictness_message))
                .view(content)
                .onDismiss {
                    status.set(page.context.getString(R.string.overlays_strictness_done, page.str(levels[strictness])))
                }
                .build()
            done.setOnClickListener { sheet.dismiss() }
            sheet.show()
        }
        addButtons(page, actions, custom)
    }
    page.code("""
        SancaiBottomSheet.Builder(this)
            .title("Share rule")
            .action("Copy") { copyRule() }
            .action("Delete", SancaiTextTone.DANGER) {
                deleteRule()
            }
            .cancel()
            .show()
        // Any view instead of actions: .view(form)
    """)
}

private fun menus(page: PageScope, status: Status) {
    // The order chosen in the second menu; it carries the check mark the next time.
    var order = 0
    val orders = intArrayOf(R.string.overlays_order_name, R.string.overlays_order_date)
    page.section(R.string.overlays_menus_title, R.string.overlays_menus_detail) {
        val more = button(page, R.string.overlays_menu_more, icon = SancaiIcon.MORE_HORIZONTAL) { anchor ->
            SancaiMenu(page.context)
                .add(page.str(R.string.overlays_action_edit), SancaiIcon.EDIT) { status.set(R.string.overlays_picked_edit) }
                .add(page.str(R.string.overlays_action_copy), SancaiIcon.COPY) { status.set(R.string.overlays_picked_copy) }
                .addDivider()
                .addDanger(page.str(R.string.overlays_action_delete), SancaiIcon.TRASH) {
                    status.set(R.string.overlays_picked_delete)
                }
                .show(anchor)
        }
        val sort = button(page, R.string.overlays_menu_sort, icon = SancaiIcon.LIST) { anchor ->
            val menu = SancaiMenu(page.context)
            orders.forEachIndexed { index, label ->
                menu.add(page.str(label)) {
                    order = index
                    status.set(page.context.getString(R.string.overlays_order_done, page.str(label)))
                }
            }
            menu.add(page.str(R.string.overlays_order_size))
            menu.setChecked(order)
            menu.setEnabled(orders.size, false)
            menu.show(anchor)
        }
        addButtons(page, more, sort)
    }
    page.code("""
        SancaiMenu(this)
            .add("Edit", SancaiIcon.EDIT) { editRule() }
            .addDivider()
            .addDanger("Delete") { deleteRule() }
            .setChecked(0)
            .show(moreButton)
    """)
}

private fun toasts(page: PageScope, status: Status) {
    val activity = page.host.activity
    page.section(R.string.overlays_toasts_title, R.string.overlays_toasts_detail) {
        val neutral = button(page, R.string.overlays_toast_neutral) {
            SancaiToast.show(activity, page.str(R.string.overlays_toast_neutral_text))
            status.set(R.string.overlays_toast_neutral_shown, echo = false)
        }
        val success = button(page, R.string.overlays_toast_success) {
            SancaiToast.show(activity, page.str(R.string.overlays_toast_success_text), SancaiToast.Tone.SUCCESS)
            status.set(R.string.overlays_toast_success_shown, echo = false)
        }
        val danger = button(page, R.string.overlays_toast_danger) {
            SancaiToast.error(activity, page.str(R.string.overlays_toast_danger_text))
            status.set(R.string.overlays_toast_danger_shown, echo = false)
        }
        val action = button(page, R.string.overlays_toast_action) {
            var shownAt = -1
            SancaiToast.Builder(activity)
                .message(page.str(R.string.overlays_toast_action_text))
                .action(page.str(R.string.overlays_toast_undo)) {}
                .onDismiss { _, actionTaken ->
                    // The toast may leave long after something else was reported: only Undo,
                    // which the user chose, may then replace the newer result.
                    if (actionTaken) {
                        status.set(R.string.overlays_toast_undone, echo = false)
                    } else if (status.stamp == shownAt) {
                        status.set(R.string.overlays_toast_left, echo = false)
                    }
                }
                .show()
            // After show(): a toast it replaced reports its own leaving first.
            status.set(R.string.overlays_toast_action_shown, echo = false)
            shownAt = status.stamp
        }
        addButtons(page, neutral, success, danger, action)
    }
    page.code("""
        SancaiToast.show(this, "Copied to the clipboard")
        SancaiToast.error(this, "Could not save")
        SancaiToast.Builder(this)
            .message("Rule deleted")
            .action("Undo") { restoreRule() }
            .onDismiss { _, undone -> log(undone) }
            .show()
    """)
}

private fun floatingPanel(page: PageScope, status: Status) {
    val activity = page.host.activity
    var panel: SancaiFloatingPanel? = null
    var snap = true
    lateinit var toggle: SancaiButton

    fun percent(value: Float) = (value * 100).roundToInt()

    fun detach() {
        panel?.remove()
        panel = null
        toggle.text = page.str(R.string.overlays_panel_attach)
        toggle.variant = SancaiButton.Variant.PRIMARY
    }

    fun attach() {
        val readout = page.text("", SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
        fun show(x: Float, y: Float) {
            readout.text = page.context.getString(R.string.overlays_panel_position, percent(x), percent(y))
        }
        val content = LinearLayout(page.context).apply {
            orientation = LinearLayout.VERTICAL
            addView(page.text(page.str(R.string.overlays_panel_name), SancaiTextRole.LABEL))
            addView(readout, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP).apply { topMargin = page.dp(2) })
        }
        panel = SancaiFloatingPanel.attach(activity, content).apply {
            snapToEdge = snap
            setPosition(1f, 0.3f)
            setOnPositionChangeListener { _, x, y -> show(x, y) }
        }
        show(1f, 0.3f)
        toggle.text = page.str(R.string.overlays_panel_remove)
        toggle.variant = SancaiButton.Variant.NEUTRAL
    }

    page.section(R.string.overlays_panel_title, R.string.overlays_panel_detail) {
        toggle = button(page, R.string.overlays_panel_attach, SancaiButton.Variant.PRIMARY) {
            if (panel == null) {
                attach()
                status.set(R.string.overlays_panel_attached)
            } else {
                detach()
                status.set(R.string.overlays_panel_removed)
            }
        }
        val snapSwitch = SancaiSwitch(context).apply {
            isChecked = snap
            contentDescription = page.str(R.string.overlays_panel_snap)
            setOnCheckedChangeListener { _, isChecked ->
                snap = isChecked
                panel?.snapToEdge = isChecked
            }
        }
        val snapRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                page.text(page.str(R.string.overlays_panel_snap), SancaiTextRole.BODY_COMPACT),
                LinearLayout.LayoutParams(0, PageScope.WRAP, 1f).apply { marginEnd = page.dp(12) },
            )
            addView(snapSwitch, LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP))
        }
        addView(toggle, below(page, 0))
        addDivider(14f)
        addView(snapRow, below(page, 0))
    }
    // The panel belongs to the activity, not to this page: take it along when the page leaves.
    page.view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit

        override fun onViewDetachedFromWindow(view: View) {
            if (panel != null) detach()
        }
    })
    page.code("""
        val panel =
            SancaiFloatingPanel.attach(this, content)
        panel.snapToEdge = true
        panel.setPosition(1f, 0.3f)
        panel.setOnPositionChangeListener { _, x, y ->
            save(x, y)
        }
        panel.remove()
    """)
}
