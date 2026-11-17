package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Press and haptics. Not written yet. */
object PressPage : DemoPage("i_press", R.string.page_i_press_title, R.string.page_i_press_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
