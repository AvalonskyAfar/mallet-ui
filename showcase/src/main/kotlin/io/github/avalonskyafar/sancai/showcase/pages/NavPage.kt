package io.github.avalonskyafar.sancai.showcase.pages

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.interaction.SancaiPager
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.widget.SancaiButton
import io.github.avalonskyafar.sancai.widget.SancaiNavBar
import io.github.avalonskyafar.sancai.widget.SancaiPageIndicator
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiTabs
import io.github.avalonskyafar.sancai.widget.SancaiText
import io.github.avalonskyafar.sancai.widget.SancaiTopBar

/**
 * Navigation: the bottom row in both modes, the top bar over a scrolling area, and tabs alone
 * and coupled with a pager. The page scrolls, so every bar sits in a card instead of being
 * docked to the window, with its system-bar padding switched off.
 */
object NavPage : DemoPage("c_nav", R.string.page_c_nav_title, R.string.page_c_nav_summary) {
    override fun build(page: PageScope) {
        page.title()
        page.navRow()
        page.topBar()
        page.pagerTabs()
        page.scrollTabs()
        page.caption(page.str(R.string.nav_origin), topDp = 20)
    }
}

private fun matchWrap() = LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP)

/** The line under a bar that reports what was chosen. */
private fun PageScope.status(card: SancaiSurface, initial: Int, topDp: Int = 4): SancaiText {
    val line = text(str(initial), SancaiTextRole.FOOTNOTE, SancaiTextTone.MUTED)
    line.gravity = Gravity.CENTER_HORIZONTAL
    line.setPadding(dp(9), dp(topDp), dp(9), dp(8))
    card.addView(line, matchWrap())
    return line
}

/** A quiet label above a bar inside a card. */
private fun PageScope.label(card: SancaiSurface, res: Int) {
    val line = text(str(res), SancaiTextRole.CAPTION, SancaiTextTone.MUTED)
    line.setPadding(dp(9), dp(6), dp(9), dp(2))
    card.addView(line, matchWrap())
}

// ---- 1. Navigation row ----

private fun PageScope.navRow() {
    section(R.string.nav_row_title, R.string.nav_row_detail) {
        val card = this
        label(card, R.string.nav_row_launcher)
        val launcherNames = listOf(R.string.nav_item_rules, R.string.nav_item_history, R.string.nav_item_settings, R.string.nav_item_about)
        val launcher = SancaiNavBar(context).apply {
            mode = SancaiNavBar.Mode.LAUNCHER
            fitsNavigationBar = false
            addItem(SancaiIcon.SHIELD, str(launcherNames[0]))
            addItem(SancaiIcon.CLOCK, str(launcherNames[1]))
            addItem(SancaiIcon.SLIDERS, str(launcherNames[2]))
            addItem(SancaiIcon.INFO, str(launcherNames[3]))
        }
        addView(launcher, matchWrap())
        val launcherStatus = status(card, R.string.nav_row_launcher_idle)
        launcher.setOnItemSelectedListener { _, index ->
            launcherStatus.text = context.getString(R.string.nav_row_opened, str(launcherNames[index]))
        }

        addDivider(4f)

        label(card, R.string.nav_row_tabs)
        val tabNames = listOf(R.string.nav_item_home, R.string.nav_item_inbox, R.string.nav_item_alerts, R.string.nav_item_me)
        val tabs = SancaiNavBar(context).apply {
            mode = SancaiNavBar.Mode.TABS
            fitsNavigationBar = false
            addItem(SancaiIcon.HOME, str(tabNames[0]))
            addItem(SancaiIcon.INBOX, str(tabNames[1]))
            addItem(SancaiIcon.BELL, str(tabNames[2]))
            addItem(SancaiIcon.USER, str(tabNames[3]))
            select(0, animate = false)
            setBadge(INBOX, 3)
            setBadgeDot(ALERTS, true)
        }
        addView(tabs, matchWrap())
        val tabsStatus = status(card, R.string.nav_row_tabs_idle)
        tabs.setOnItemSelectedListener { bar, index ->
            // Opening a tab reads what it announced: the count or the dot goes away.
            if (index == INBOX) bar.setBadge(INBOX, 0)
            if (index == ALERTS) bar.setBadgeDot(ALERTS, false)
            tabsStatus.text = context.getString(R.string.nav_row_selected, str(tabNames[index]))
        }
        tabs.setOnItemReselectedListener { _, index ->
            tabsStatus.text = context.getString(R.string.nav_row_reselected, str(tabNames[index]))
        }

        val more = SancaiButton(context).apply {
            variant = SancaiButton.Variant.TEXT
            text = str(R.string.nav_row_add_badge)
            setOnClickListener {
                tabs.setBadge(INBOX, tabs.badgeCount(INBOX) + 1)
                tabsStatus.text = context.getString(R.string.nav_row_badge_now, tabs.badgeCount(INBOX))
            }
        }
        val params = LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP)
        params.gravity = Gravity.CENTER_HORIZONTAL
        addView(more, params)
    }
    code("""
        nav.mode = SancaiNavBar.Mode.TABS
        nav.fitsNavigationBar = false
        nav.addItem(SancaiIcon.HOME, "Home")
        nav.addItem(SancaiIcon.INBOX, "Inbox")
        nav.setBadge(1, 3)
        nav.setOnItemSelectedListener { bar, index -> bar.setBadge(index, 0) }
        nav.setOnItemReselectedListener { _, index -> scrollToTop(index) }
    """)
}

private const val INBOX = 1
private const val ALERTS = 2

// ---- 2. Top bar ----

@SuppressLint("ClickableViewAccessibility")
private fun PageScope.topBar() {
    section(R.string.nav_top_title, R.string.nav_top_detail) {
        val card = this
        elevated = false
        val bar = SancaiTopBar(context).apply {
            fitStatusBar = false
            // The bar takes the colour of what it lies on: here a card, not the page.
            setBackgroundColor(Color.TRANSPARENT)
            title = str(R.string.nav_top_bar_title)
            subtitle = str(R.string.nav_top_bar_subtitle)
        }
        addView(bar, matchWrap())

        val paragraphs = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), dp(12), dp(9), dp(12))
        }
        listOf(R.string.nav_top_p1, R.string.nav_top_p2, R.string.nav_top_p3, R.string.nav_top_p4).forEachIndexed { index, res ->
            val paragraph = text(str(res), SancaiTextRole.BODY_COMPACT, SancaiTextTone.MUTED)
            val params = matchWrap()
            if (index > 0) params.topMargin = dp(14)
            paragraphs.addView(paragraph, params)
        }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(paragraphs, FrameLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            // The page scrolls too: while this area can still move, it keeps the gesture.
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                    val canMove = view.canScrollVertically(1) || view.canScrollVertically(-1)
                    view.parent?.requestDisallowInterceptTouchEvent(canMove)
                }
                false
            }
        }
        // A card does not clip its children, so the scrolling area gets a window of its own.
        val window = FrameLayout(context).apply {
            addView(scroll, FrameLayout.LayoutParams(PageScope.MATCH, PageScope.MATCH))
            addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
                view.clipBounds = Rect(0, 0, right - left, bottom - top)
            }
        }
        addView(window, LinearLayout.LayoutParams(PageScope.MATCH, dp(TOP_AREA_DP)))
        bar.bindTo(scroll)

        addDivider()
        val line = status(card, R.string.nav_top_idle, topDp = 9)
        bar.setNavigation(SancaiIcon.CHEVRON_LEFT, str(R.string.nav_top_back)) {
            scroll.smoothScrollTo(0, 0)
            line.text = str(R.string.nav_top_did_back)
        }
        bar.addAction(SancaiIcon.SEARCH, str(R.string.nav_top_search)) {
            line.text = str(R.string.nav_top_did_search)
        }
        bar.addAction(SancaiIcon.MORE_VERTICAL, str(R.string.nav_top_more)) {
            line.text = str(R.string.nav_top_did_more)
        }
    }
    code("""
        bar.title = "Blocked numbers"
        bar.subtitle = "12 entries"
        bar.setNavigation { finish() }
        bar.addAction(SancaiIcon.SEARCH, "Search") { openSearch() }
        bar.addAction(SancaiIcon.MORE_VERTICAL, "More") { openMenu() }
        bar.bindTo(scrollView)
    """)
}

private const val TOP_AREA_DP = 200

// ---- 3. Tabs ----

private fun PageScope.pagerTabs() {
    section(R.string.nav_tabs_title, R.string.nav_tabs_detail) {
        val card = this
        val names = listOf(R.string.nav_tab_all, R.string.nav_tab_calls, R.string.nav_tab_messages)
        val bodies = listOf(R.string.nav_tab_all_body, R.string.nav_tab_calls_body, R.string.nav_tab_messages_body)
        val pager = SancaiPager(context)
        pager.setPages(names.size) { index, _ ->
            text(str(bodies[index]), SancaiTextRole.BODY_COMPACT, SancaiTextTone.MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
            }
        }
        val tabs = SancaiTabs(context).apply {
            layout = SancaiTabs.Layout.FILL
            setTabs(names.map { str(it) })
            attachTo(pager)
        }
        val dots = SancaiPageIndicator(context).apply { attachTo(pager) }

        addView(tabs, matchWrap())
        addView(pager, LinearLayout.LayoutParams(PageScope.MATCH, dp(PAGER_DP)))
        val dotsParams = LinearLayout.LayoutParams(PageScope.WRAP, PageScope.WRAP)
        dotsParams.gravity = Gravity.CENTER_HORIZONTAL
        addView(dots, dotsParams)

        val line = status(card, R.string.nav_tabs_idle)
        fun report(index: Int) {
            line.text = context.getString(R.string.nav_tabs_showing, str(names[index]))
        }
        tabs.setOnTabSelectedListener { _, index -> report(index) }
        dots.setOnSelectionChangeListener { _, index -> report(index) }
    }
    code("""
        pager.setPages(3) { index, parent -> createPage(index, parent) }
        tabs.layout = SancaiTabs.Layout.FILL
        tabs.setTabs("All", "Calls", "Messages")
        tabs.attachTo(pager)
        dots.attachTo(pager)
        tabs.setOnTabSelectedListener { _, index -> load(index) }
    """)
}

private fun PageScope.scrollTabs() {
    section(R.string.nav_scroll_title, R.string.nav_scroll_detail) {
        val card = this
        val names = context.resources.getStringArray(R.array.nav_scroll_tabs)
        val tabs = SancaiTabs(context).apply {
            layout = SancaiTabs.Layout.SCROLL
            setTabs(names.toList())
        }
        addView(tabs, matchWrap())
        val line = status(card, R.string.nav_scroll_idle, topDp = 10)
        tabs.setOnTabSelectedListener { _, index ->
            line.text = context.getString(R.string.nav_scroll_chosen, names[index], index + 1, names.size)
        }
    }
    code("""
        tabs.layout = SancaiTabs.Layout.SCROLL
        tabs.setTabs("Today", "This week", "This month", "This year")
        tabs.setOnTabSelectedListener { _, index -> show(index) }
    """)
}

private const val PAGER_DP = 120
