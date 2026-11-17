package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Reveal and expand. Not written yet. */
object RevealPage : DemoPage("i_reveal", R.string.page_i_reveal_title, R.string.page_i_reveal_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
