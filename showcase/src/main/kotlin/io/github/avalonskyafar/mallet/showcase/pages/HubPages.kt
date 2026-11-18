package io.github.avalonskyafar.mallet.showcase.pages

import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.Pages
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.widget.MalletEntryRow
import io.github.avalonskyafar.mallet.widget.MalletSurface

/*
 * The three directory pages around Home. Each is a title and one card of entry rows; the
 * chevron of a row points to the side on which its page lies.
 */

/** Lies at the start side of Home; its pages lie further towards the start. */
object FoundationsPage : DemoPage("foundations", R.string.page_foundations_title, R.string.page_foundations_summary) {
    override fun build(page: PageScope) {
        page.title()
        page.directory(Pages.foundations, MalletEntryRow.Arrow.START)
    }
}

/** Lies below Home; its pages lie at its end side. */
object ComponentsPage : DemoPage("components", R.string.page_components_title, R.string.page_components_summary) {
    override fun build(page: PageScope) {
        page.title()
        page.directory(Pages.components, MalletEntryRow.Arrow.END)
    }
}

/** Lies at the end side of Home; its pages lie further towards the end. */
object InteractionsPage : DemoPage("interactions", R.string.page_interactions_title, R.string.page_interactions_summary) {
    override fun build(page: PageScope) {
        page.title()
        page.directory(Pages.interactions, MalletEntryRow.Arrow.END)
    }
}

private fun PageScope.directory(pages: List<DemoPage>, arrow: MalletEntryRow.Arrow) {
    val card = MalletSurface(context)
    pages.forEachIndexed { index, demo ->
        if (index > 0) card.addDivider()
        val row = MalletEntryRow(context).apply {
            title = str(demo.title)
            detail = str(demo.summary)
            this.arrow = arrow
            setOnClickListener { host.open(demo.key) }
        }
        card.addView(row, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
    }
    loose(card, topDp = 16)
}
