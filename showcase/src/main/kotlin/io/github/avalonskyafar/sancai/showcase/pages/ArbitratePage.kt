package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Who owns the gesture. Not written yet. */
object ArbitratePage : DemoPage("i_arbitrate", R.string.page_i_arbitrate_title, R.string.page_i_arbitrate_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
