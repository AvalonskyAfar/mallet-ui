package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Lies above Home: the appearance settings of the showcase itself. Not written yet. */
object AppearancePage : DemoPage("appearance", R.string.page_appearance_title, R.string.page_appearance_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
