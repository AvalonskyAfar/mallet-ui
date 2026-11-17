package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Empty, loading, error. Not written yet. */
object StatesPage : DemoPage("c_states", R.string.page_c_states_title, R.string.page_c_states_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
