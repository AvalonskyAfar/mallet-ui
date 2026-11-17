package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Follow the finger. Not written yet. */
object FollowPage : DemoPage("i_follow", R.string.page_i_follow_title, R.string.page_i_follow_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
