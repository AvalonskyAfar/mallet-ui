package io.github.avalonskyafar.sancai.showcase.pages

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.widget.SancaiBanner
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiSegmentedTrack
import io.github.avalonskyafar.sancai.widget.SancaiSkeleton
import io.github.avalonskyafar.sancai.widget.SancaiStateLayout
import io.github.avalonskyafar.sancai.widget.SancaiStateLayout.State
import io.github.avalonskyafar.sancai.widget.SancaiStateView
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiEntryRow

/** Empty, loading, error: state views, the state layout, skeletons and banners. */
object StatesPage : DemoPage("c_states", R.string.page_c_states_title, R.string.page_c_states_summary) {
    override fun build(page: PageScope) {
        page.title()
        stateViews(page)
        stateLayout(page)
        skeletons(page)
        banners(page)
        page.caption(page.str(R.string.states_origin), topDp = 22)
    }

    private fun match() = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP)

    /** One state view whose preset follows a track, and the compact form in a card. */
    private fun stateViews(page: PageScope) {
        val kinds = SancaiStateView.Kind.values()
        val titles = intArrayOf(
            R.string.states_kind_empty_title, R.string.states_kind_error_title, R.string.states_kind_offline_title,
            R.string.states_kind_success_title, R.string.states_kind_info_title,
        )
        val details = intArrayOf(
            R.string.states_kind_empty_detail, R.string.states_kind_error_detail, R.string.states_kind_offline_detail,
            R.string.states_kind_success_detail, R.string.states_kind_info_detail,
        )
        val actions = intArrayOf(
            R.string.states_kind_empty_action, R.string.states_kind_error_action, R.string.states_kind_offline_action,
            0, 0,
        )
        page.section(R.string.states_views_title, R.string.states_views_detail) {
            val state = SancaiStateView(context)
            fun show(index: Int) {
                state.kind = kinds[index]
                state.title = page.str(titles[index])
                state.detail = page.str(details[index])
                state.actionLabel = if (actions[index] != 0) page.str(actions[index]) else null
            }
            state.setOnActionClickListener {
                page.host.say(context.getString(R.string.states_said_action, state.actionLabel))
            }
            show(0)
            val track = SancaiSegmentedTrack(context)
            track.setOptions(
                page.str(R.string.states_kind_empty), page.str(R.string.states_kind_error),
                page.str(R.string.states_kind_offline), page.str(R.string.states_kind_success),
                page.str(R.string.states_kind_info),
            )
            // The options are equals, not levels: one glaze under all of them.
            track.setGlazes(page.theme.glazes.uniform(5, page.theme.glazes.mist))
            track.setOnSelectionChangeListener { _, index -> show(index) }
            addView(track, match())
            // A fixed height keeps the card still while the message changes length.
            val frame = FrameLayout(context)
            frame.addView(state, FrameLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP, Gravity.CENTER))
            addView(frame, LinearLayout.LayoutParams(PageScope.MATCH, page.dp(220)).apply { topMargin = page.dp(6) })
        }
        page.code(
            """
            val state = SancaiStateView(context)
            state.kind = SancaiStateView.Kind.EMPTY
            state.title = "No notes yet"
            state.detail = "Notes you write appear here."
            state.setAction("New note") { openEditor() }
            """,
        )

        // The compact form: a smaller icon and tighter gaps, for an area inside a card.
        val card = SancaiSurface(page.context)
        card.addView(
            page.text(page.str(R.string.states_compact_heading), SancaiTextRole.SUBHEAD),
            match().apply { marginStart = page.dp(INSET); marginEnd = page.dp(INSET); topMargin = page.dp(10) },
        )
        card.addDivider(10f)
        val compact = SancaiStateView(page.context).apply {
            compact = true
        }
        // The action pretends to work: the message turns into a success, and back again.
        fun offline(isOffline: Boolean) {
            compact.kind = if (isOffline) SancaiStateView.Kind.OFFLINE else SancaiStateView.Kind.SUCCESS
            compact.title = page.str(if (isOffline) R.string.states_compact_title else R.string.states_compact_done_title)
            compact.detail = page.str(if (isOffline) R.string.states_compact_detail else R.string.states_compact_done_detail)
            compact.setAction(page.str(if (isOffline) R.string.states_compact_action else R.string.states_compact_reset)) {
                offline(!isOffline)
            }
        }
        offline(true)
        card.addView(compact, match())
        page.loose(card, topDp = 14)
        page.code(
            """
            state.compact = true
            state.kind = SancaiStateView.Kind.OFFLINE
            state.setAction("Try again") { sync() }
            """,
        )
    }

    /** One layout, four states; the track above it and the actions inside it both drive it. */
    private fun stateLayout(page: PageScope) {
        val states = arrayOf(State.CONTENT, State.LOADING, State.EMPTY, State.ERROR)
        page.section(R.string.states_layout_title, R.string.states_layout_detail) {
            val track = SancaiSegmentedTrack(context)
            track.setOptions(
                page.str(R.string.states_layout_content), page.str(R.string.states_layout_loading),
                page.str(R.string.states_layout_empty), page.str(R.string.states_layout_error),
            )
            track.setGlazes(page.theme.glazes.uniform(4, page.theme.glazes.mist))
            val layout = SancaiStateLayout(context)
            layout.minimumLoadingMs = 400L
            layout.addView(backupList(page))

            // Loads again: loading now, content a moment later. Moving the track meanwhile, or
            // leaving the page, cancels the second step.
            var reloading = false
            val finish = Runnable {
                reloading = false
                layout.state = State.CONTENT
                track.select(0)
            }
            fun reload() {
                layout.removeCallbacks(finish)
                reloading = true
                layout.state = State.LOADING
                track.select(1)
                layout.postDelayed(finish, RELOAD_MS)
            }
            layout.setEmpty(
                page.str(R.string.states_layout_empty_title), page.str(R.string.states_layout_empty_detail),
                page.str(R.string.states_layout_empty_action),
            ) { reload() }
            layout.setError(
                page.str(R.string.states_layout_error_title), page.str(R.string.states_layout_error_detail),
                page.str(R.string.states_layout_error_action),
            ) { reload() }
            track.setOnSelectionChangeListener { _, index ->
                layout.removeCallbacks(finish)
                reloading = false
                layout.state = states[index]
            }
            layout.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    // A reload that was under way is settled at once instead of being lost.
                    if (reloading) {
                        reloading = false
                        layout.removeCallbacks(finish)
                        layout.setState(State.CONTENT, false)
                        track.select(0, animate = false)
                    }
                }
            })
            addView(track, match())
            // A fixed height: the card does not jump when the state changes.
            addView(layout, LinearLayout.LayoutParams(PageScope.MATCH, page.dp(LAYOUT_HEIGHT)).apply { topMargin = page.dp(10) })
        }
        page.code(
            """
            val layout = SancaiStateLayout(context)
            layout.addView(list)
            layout.setEmpty("No backups", "Nothing has been saved yet.")
            layout.setError("Could not load", "Check the connection.") { load() }
            layout.state = SancaiStateLayout.State.LOADING
            """,
        )
    }

    /** The content of the state layout: three plain rows of a list. */
    private fun backupList(page: PageScope): View {
        val list = LinearLayout(page.context)
        list.orientation = LinearLayout.VERTICAL
        val titles = intArrayOf(R.string.states_row_1_title, R.string.states_row_2_title, R.string.states_row_3_title)
        val values = intArrayOf(R.string.states_row_1_value, R.string.states_row_2_value, R.string.states_row_3_value)
        for (i in titles.indices) {
            val row = SancaiEntryRow(page.context).apply {
                title = page.str(titles[i])
                detail = page.str(R.string.states_row_detail)
                value = page.str(values[i])
                arrow = SancaiEntryRow.Arrow.NONE
                alignment = SancaiEntryRow.Alignment.START
                val name = title
                setOnClickListener { page.host.say(context.getString(R.string.states_said_row, name)) }
            }
            list.addView(row, match())
        }
        return list
    }

    /** A card of skeleton rows, and a track that stops and starts their motion. */
    private fun skeletons(page: PageScope) {
        val blocks = ArrayList<SancaiSkeleton>()
        page.section(R.string.states_skeleton_title, R.string.states_skeleton_detail) {
            val track = SancaiSegmentedTrack(context)
            track.setOptions(page.str(R.string.states_skeleton_still), page.str(R.string.states_skeleton_moving))
            track.select(1, animate = false)
            track.setOnSelectionChangeListener { _, index -> blocks.forEach { it.isAnimated = index == 1 } }
            addView(track, match())
            for (i in 0 until 3) {
                if (i == 0) addDivider(12f) else addDivider(14f)
                val row = LinearLayout(context)
                row.gravity = Gravity.CENTER_VERTICAL
                val avatar = SancaiSkeleton(context)
                avatar.shape = SancaiSkeleton.Shape.CIRCLE
                row.addView(avatar, LinearLayout.LayoutParams(page.dp(40), page.dp(40)))
                val lines = SancaiSkeleton.text(context, lines = 2, lastLineFraction = 0.45f + 0.1f * i)
                row.addView(lines, LinearLayout.LayoutParams(0, PageScope.WRAP, 1f).apply { marginStart = page.dp(14) })
                blocks += avatar
                for (j in 0 until lines.childCount) (lines.getChildAt(j) as? SancaiSkeleton)?.let { blocks += it }
                addView(row, match().apply { marginStart = page.dp(INSET); marginEnd = page.dp(INSET) })
            }
            // A picture and its caption, as blocks of their own.
            addDivider(14f)
            val picture = SancaiSkeleton(context)
            picture.cornerRadiusDp = 12f
            addView(picture, LinearLayout.LayoutParams(PageScope.MATCH, page.dp(96)).apply { marginStart = page.dp(INSET); marginEnd = page.dp(INSET) })
            val label = SancaiSkeleton(context)
            label.shape = SancaiSkeleton.Shape.CAPSULE
            label.widthFraction = 0.4f
            addView(
                label,
                LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP).apply {
                    topMargin = page.dp(12); bottomMargin = page.dp(14); marginStart = page.dp(INSET)
                },
            )
            blocks += picture
            blocks += label
        }
        page.code(
            """
            val avatar = SancaiSkeleton(context)
            avatar.shape = SancaiSkeleton.Shape.CIRCLE
            row.addView(avatar, LinearLayout.LayoutParams(size, size))
            row.addView(SancaiSkeleton.text(context, 2))
            avatar.isAnimated = false
            """,
        )
    }

    /** The four tones in a column on the canvas, each with a close icon, and a way back. */
    private fun banners(page: PageScope) {
        page.heading(R.string.states_banner_title, R.string.states_banner_detail)
        val tones = arrayOf(
            SancaiBanner.Tone.INFO, SancaiBanner.Tone.WARNING, SancaiBanner.Tone.DANGER, SancaiBanner.Tone.SUCCESS,
        )
        val titles = intArrayOf(
            R.string.states_banner_info_title, R.string.states_banner_warning_title,
            R.string.states_banner_danger_title, R.string.states_banner_success_title,
        )
        val details = intArrayOf(
            R.string.states_banner_info_detail, R.string.states_banner_warning_detail,
            R.string.states_banner_danger_detail, R.string.states_banner_success_detail,
        )
        val status = page.text("", SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
        status.gravity = Gravity.CENTER
        val all = ArrayList<SancaiBanner>()
        val again = SancaiButton(page.context)
        fun refresh() {
            val showing = all.count { it.isShowing }
            status.text = page.context.getString(R.string.states_banner_status, showing, all.size)
            again.isEnabled = showing < all.size
        }
        for (i in tones.indices) {
            val banner = SancaiBanner(page.context).apply {
                tone = tones[i]
                title = page.str(titles[i])
                detail = page.str(details[i])
                isDismissible = true
                setOnDismissListener { refresh() }
            }
            if (tones[i] == SancaiBanner.Tone.DANGER) {
                banner.setAction(page.str(R.string.states_banner_danger_action)) {
                    page.host.say(page.str(R.string.states_banner_danger_said))
                }
            }
            all += banner
            // The gap above a banner is its own margin: it closes and opens with the banner.
            page.loose(banner, topDp = if (i == 0) 14 else 10)
        }
        again.text = page.str(R.string.states_banner_again)
        again.setOnClickListener {
            all.forEach { it.show() }
            refresh()
        }
        refresh()
        page.loose(status, topDp = 14, sideDp = 10)
        page.loose(again, topDp = 12, sideDp = PageScope.BUTTON_SIDE)
        page.code(
            """
            val banner = SancaiBanner(context)
            banner.tone = SancaiBanner.Tone.DANGER
            banner.title = "Backup failed"
            banner.detail = "The disk is full."
            banner.setAction("Free space") { openStorage() }
            banner.isDismissible = true
            banner.setOnDismissListener { remember(it) }
            banner.show()
            """,
        )
    }

    private const val LAYOUT_HEIGHT = 220
    private const val INSET = 10
    private const val RELOAD_MS = 1400L
}
