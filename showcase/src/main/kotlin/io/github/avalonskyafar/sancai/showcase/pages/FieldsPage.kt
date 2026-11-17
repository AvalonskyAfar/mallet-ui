package io.github.avalonskyafar.sancai.showcase.pages

import io.github.avalonskyafar.sancai.showcase.DemoPage
import io.github.avalonskyafar.sancai.showcase.PageScope
import io.github.avalonskyafar.sancai.showcase.R

/** Text fields. Not written yet. */
object FieldsPage : DemoPage("c_fields", R.string.page_c_fields_title, R.string.page_c_fields_summary) {
    override fun build(page: PageScope) {
        page.placeholder()
    }
}
