package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Numbers and progress. Not written yet. */
object NumbersPage : DemoPage("c_numbers", R.string.page_c_numbers_title, R.string.page_c_numbers_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
