package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Checkboxes and radios. Not written yet. */
object ChoicePage : DemoPage("c_choice", R.string.page_c_choice_title, R.string.page_c_choice_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
