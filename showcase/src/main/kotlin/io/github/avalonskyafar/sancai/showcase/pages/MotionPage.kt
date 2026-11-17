package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Motion tokens. Not written yet. */
object MotionPage : DemoPage("i_motion", R.string.page_i_motion_title, R.string.page_i_motion_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
