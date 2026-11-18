package io.github.avalonskyafar.mallet.showcase.pages

import android.content.Context
import android.content.Intent
import android.view.View
import io.github.avalonskyafar.mallet.interaction.MalletSpatialTransition
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.NeighbourActivity
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.widget.MalletButton
import io.github.avalonskyafar.mallet.widget.MalletCheckbox
import io.github.avalonskyafar.mallet.widget.MalletControlRow
import io.github.avalonskyafar.mallet.widget.MalletEntryRow
import io.github.avalonskyafar.mallet.widget.MalletSwitch
import io.github.avalonskyafar.mallet.widget.MalletText
import io.github.avalonskyafar.mallet.widget.MalletTextField
import java.io.IOException

/**
 * The same widgets, declared in a layout file.
 *
 * The cards of this page come from `res/layout/page_xml.xml`. What is left for Kotlin is to
 * find views by id and to add listeners; [wire] does nothing else. The page then shows the
 * layout file itself: the build copies it into the assets (see `showcase/build.gradle.kts`),
 * so the text on screen is the text that was inflated.
 */
object XmlPage : DemoPage("c_xml", R.string.page_c_xml_title, R.string.page_c_xml_summary) {

    /** The copy of `res/layout/page_xml.xml` in the assets. */
    internal const val SOURCE_ASSET: String = "page_xml.xml"
    internal const val SAMPLE_BEGIN: String = "<!-- sample:begin -->"
    internal const val SAMPLE_END: String = "<!-- sample:end -->"

    override fun build(page: PageScope) {
        page.title()
        page.caption(page.str(R.string.xml_intro), topDp = 14)
        val cards = page.inflate(R.layout.page_xml, topDp = PageScope.SECTION_GAP)
        wire(page, cards)
        source(page)
        kotlin(page)
        theme(page)
        screen(page)
        page.caption(page.str(R.string.xml_origin), topDp = 22)
    }

    /** Everything the inflated cards need from code. */
    private fun wire(page: PageScope, cards: View) {
        val context = page.context
        val status = cards.findViewById<MalletText>(R.id.xml_status)
        val strength = cards.findViewById<MalletControlRow>(R.id.xml_strength)
        val name = cards.findViewById<MalletTextField>(R.id.xml_name)

        strength.track?.setOnSelectionChangeListener { view, index ->
            status.text = context.getString(R.string.xml_status_strength, view.options[index])
        }
        cards.findViewById<MalletSwitch>(R.id.xml_wifi).setOnCheckedChangeListener { _, checked ->
            status.setText(if (checked) R.string.xml_status_wifi_on else R.string.xml_status_wifi_off)
        }
        cards.findViewById<MalletEntryRow>(R.id.xml_exceptions).setOnClickListener {
            status.setText(R.string.xml_status_exceptions)
        }
        cards.findViewById<MalletCheckbox>(R.id.xml_remember).setOnCheckedChangeListener { _, checked ->
            status.setText(if (checked) R.string.xml_status_remember_on else R.string.xml_status_remember_off)
        }
        // An error stays until the text it complained about changes.
        name.setOnTextChangeListener { field, _ -> field.error = null }
        cards.findViewById<MalletButton>(R.id.xml_save).setOnClickListener {
            val named = name.validate { text ->
                if (text.isBlank()) context.getString(R.string.xml_error_name) else null
            }
            if (named) {
                val level = strength.track?.let { it.options[it.selectedIndex] } ?: ""
                status.text = context.getString(R.string.xml_status_saved, name.text.trim(), level)
            }
        }
    }

    /** The first card as it is written in the layout file. */
    private fun source(page: PageScope) {
        page.heading(R.string.xml_source_title, R.string.xml_source_detail)
        val sample = layoutSample(page.context)
        if (sample == null) {
            page.caption(page.str(R.string.xml_source_missing))
        } else {
            page.code(sample, wrap = false)
        }
    }

    private fun kotlin(page: PageScope) {
        page.heading(R.string.xml_kotlin_title, R.string.xml_kotlin_detail)
        page.code("""
            val cards = layoutInflater.inflate(
                R.layout.page_xml, parent, false)
            val status: MalletText =
                cards.findViewById(R.id.xml_status)
            val row: MalletControlRow =
                cards.findViewById(R.id.xml_strength)
            val wifi: MalletSwitch =
                cards.findViewById(R.id.xml_wifi)

            // The row made its track from malletOptions.
            row.track?.setOnSelectionChangeListener { v, i ->
                status.text = v.options[i]
            }
            wifi.setOnCheckedChangeListener { _, on ->
                status.text = if (on) "On" else "Off"
            }
        """)
    }

    private fun theme(page: PageScope) {
        page.heading(R.string.xml_theme_title, R.string.xml_theme_detail)
        page.code(
            """
            <!-- res/values/themes.xml -->
            <style name="Theme.App" parent="Theme.Mallet">
                <item name="malletColorAccent">#4C5FC4</item>
                <item name="malletHueCobalt">#8C9BEB</item>
                <item name="malletHueCobaltEdge">#6C7DD6</item>
                <item name="malletHighContrast">true</item>
                <item name="malletTextScale">1.13</item>
            </style>
            """,
            wrap = false,
        )
        page.caption(page.str(R.string.xml_theme_note))
    }

    /** A whole screen from one layout file: the neighbour activity. */
    private fun screen(page: PageScope) {
        page.heading(R.string.xml_screen_title, R.string.xml_screen_detail)
        val open = MalletButton(page.context).apply {
            text = page.str(R.string.xml_screen_open)
            setOnClickListener {
                MalletSpatialTransition.start(page.host.activity, Intent(page.context, NeighbourActivity::class.java))
            }
        }
        page.loose(open, topDp = 14, sideDp = PageScope.BUTTON_SIDE)
        page.code("""
            // NeighbourActivity.onCreate
            val root: ViewGroup =
                findViewById(android.R.id.content)
            val screen = layoutInflater.inflate(
                R.layout.activity_neighbour, root, false)
            setContentView(
                MalletSpatialTransition.wrap(this, screen))
            val back: MalletButton =
                findViewById(R.id.neighbour_back)
            back.setOnClickListener {
                MalletSpatialTransition.finish(this)
            }
        """)
    }

    /**
     * The part of the packaged layout file between the two sample markers, or null when the
     * file is not in the assets or carries no markers.
     */
    internal fun layoutSample(context: Context): String? {
        val file = try {
            context.assets.open(SOURCE_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: IOException) {
            return null
        }
        return sampleOf(file)
    }

    /** The text between the sample markers of [file], without the indentation they share. */
    internal fun sampleOf(file: String): String? {
        val begin = file.indexOf(SAMPLE_BEGIN)
        val end = file.indexOf(SAMPLE_END)
        if (begin < 0 || end < begin) return null
        return file.substring(begin + SAMPLE_BEGIN.length, end).replace("\r\n", "\n").trimIndent().trim()
    }
}
