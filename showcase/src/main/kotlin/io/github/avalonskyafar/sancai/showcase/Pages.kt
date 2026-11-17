package io.github.avalonskyafar.sancai.showcase

import android.view.View
import androidx.annotation.StringRes
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialGraph
import io.github.avalonskyafar.sancai.interaction.SancaiSpatialGraph.Side
import io.github.avalonskyafar.sancai.showcase.pages.AppearancePage
import io.github.avalonskyafar.sancai.showcase.pages.ArbitratePage
import io.github.avalonskyafar.sancai.showcase.pages.ButtonsPage
import io.github.avalonskyafar.sancai.showcase.pages.ChoicePage
import io.github.avalonskyafar.sancai.showcase.pages.ColourPage
import io.github.avalonskyafar.sancai.showcase.pages.ComponentsPage
import io.github.avalonskyafar.sancai.showcase.pages.FieldsPage
import io.github.avalonskyafar.sancai.showcase.pages.FollowPage
import io.github.avalonskyafar.sancai.showcase.pages.FoundationsPage
import io.github.avalonskyafar.sancai.showcase.pages.HomePage
import io.github.avalonskyafar.sancai.showcase.pages.IconsPage
import io.github.avalonskyafar.sancai.showcase.pages.InteractionsPage
import io.github.avalonskyafar.sancai.showcase.pages.MaterialPage
import io.github.avalonskyafar.sancai.showcase.pages.MotionPage
import io.github.avalonskyafar.sancai.showcase.pages.NavPage
import io.github.avalonskyafar.sancai.showcase.pages.NumbersPage
import io.github.avalonskyafar.sancai.showcase.pages.OverlaysPage
import io.github.avalonskyafar.sancai.showcase.pages.PagerPage
import io.github.avalonskyafar.sancai.showcase.pages.PressPage
import io.github.avalonskyafar.sancai.showcase.pages.ReadingPage
import io.github.avalonskyafar.sancai.showcase.pages.RevealPage
import io.github.avalonskyafar.sancai.showcase.pages.RowsPage
import io.github.avalonskyafar.sancai.showcase.pages.SpatialPage
import io.github.avalonskyafar.sancai.showcase.pages.StatesPage
import io.github.avalonskyafar.sancai.showcase.pages.TracksPage
import io.github.avalonskyafar.sancai.showcase.pages.TypePage
import io.github.avalonskyafar.sancai.showcase.pages.XmlPage

/**
 * Every page of the showcase and where it lies.
 *
 * ```
 *                     Appearance
 *                         |
 *   Foundations  ——  Home  ——  Interactions
 *                         |
 *                    Components
 * ```
 *
 * The four pages around Home are reached by dragging towards them. Their own pages are
 * opened from rows and lie one step further out, on the side the row's chevron points to.
 */
object Pages {
    const val HOME: String = "home"

    /** Pages opened from the Foundations page; they lie further towards the start. */
    val foundations: List<DemoPage> = listOf(ColourPage, TypePage, MaterialPage, IconsPage)

    /** Pages opened from the Components page; they lie at its end side. */
    val components: List<DemoPage> = listOf(
        TracksPage, ChoicePage, NumbersPage, ButtonsPage, FieldsPage, RowsPage,
        NavPage, OverlaysPage, StatesPage, ReadingPage, XmlPage,
    )

    /** Pages opened from the Interactions page; they lie further towards the end. */
    val interactions: List<DemoPage> = listOf(
        PressPage, FollowPage, SpatialPage, RevealPage, PagerPage, ArbitratePage, MotionPage,
    )

    private val all: Map<String, DemoPage> =
        (listOf<DemoPage>(HomePage, FoundationsPage, ComponentsPage, InteractionsPage, AppearancePage) +
            foundations + components + interactions).associateBy { it.key }

    /** The map of the app. */
    val graph: SancaiSpatialGraph = SancaiSpatialGraph.Builder(HOME)
        .connect(HOME, Side.START, FoundationsPage.key)
        .connect(HOME, Side.END, InteractionsPage.key)
        .connect(HOME, Side.BELOW, ComponentsPage.key)
        .connect(HOME, Side.ABOVE, AppearancePage.key)
        .apply {
            for (page in foundations) child(FoundationsPage.key, Side.START, page.key)
            for (page in components) child(ComponentsPage.key, Side.END, page.key)
            for (page in interactions) child(InteractionsPage.key, Side.END, page.key)
        }
        .build()

    fun page(key: String): DemoPage = all.getValue(key)

    @StringRes
    fun titleOf(key: String): Int = page(key).title

    /** Builds the view of the page with [key]. */
    fun create(key: String, host: ShowcaseHost): View {
        val demo = page(key)
        return demo.createView(PageScope(host, demo))
    }
}
