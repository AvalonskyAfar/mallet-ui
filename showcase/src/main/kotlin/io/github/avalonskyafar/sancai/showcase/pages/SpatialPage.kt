package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Pages as places. Not written yet. */
object SpatialPage : DemoPage("i_spatial", R.string.page_i_spatial_title, R.string.page_i_spatial_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
