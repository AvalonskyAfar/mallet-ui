package io.github.avalonskyafar.sancai.showcase.pages

import android.widget.LinearLayout
import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.Pages
import io.github.avalonskyafar.sancai.showcase.R
import io.github.avalonskyafar.sancai.widget.SancaiEntryRow
import io.github.avalonskyafar.sancai.widget.SancaiSurface

/*
 * The three directory pages around Home. Each is a title and one card of entry rows; the
 * chevron of a row points to the side on which its page lies.
 */

/** Lies at the start side of Home; its pages lie further towards the start. */
object FoundationsPage : DemoPage("foundations", R.string.page_foundations_title, R.string.page_foundations_summary) {
    override fun build(page: PageScope) {
        page.title()
        page.directory(Pages.foundations, SancaiEntryRow.Arrow.START)
    }
}

/** Lies below Home; its pages lie at its end side. */
object ComponentsPage : DemoPage("components", R.string.page_components_title, R.string.page_components_summary) {
    override fun build(page: PageScope) {
        page.title()
        page.directory(Pages.components, SancaiEntryRow.Arrow.END)
    }
}

/** Lies at the end side of Home; its pages lie further towards the end. */
object InteractionsPage : DemoPage("interactions", R.string.page_interactions_title, R.string.page_interactions_summary) {
    override fun build(page: PageScope) {
        page.title()
        page.directory(Pages.interactions, SancaiEntryRow.Arrow.END)
    }
}

private fun PageScope.directory(pages: List<DemoPage>, arrow: SancaiEntryRow.Arrow) {
    val card = SancaiSurface(context)
    pages.forEachIndexed { index, demo ->
        if (index > 0) card.addDivider()
        val row = SancaiEntryRow(context).apply {
            title = str(demo.title)
            detail = str(demo.summary)
            this.arrow = arrow
            setOnClickListener { host.open(demo.key) }
        }
        card.addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
    }
    loose(card, topDp = 16)
}
