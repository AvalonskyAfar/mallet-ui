package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Navigation. Not written yet. */
object NavPage : DemoPage("c_nav", R.string.page_c_nav_title, R.string.page_c_nav_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
