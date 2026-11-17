package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Pager. Not written yet. */
object PagerPage : DemoPage("i_pager", R.string.page_i_pager_title, R.string.page_i_pager_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
