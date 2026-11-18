package io.github.avalonskyafar.mallet.showcase.pages

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import io.github.avalonskyafar.mallet.interaction.MalletHaptics
import io.github.avalonskyafar.mallet.interaction.MalletSpatialHost
import io.github.avalonskyafar.mallet.interaction.MalletSpatialTransition
import io.github.avalonskyafar.mallet.showcase.DemoPage
import io.github.avalonskyafar.mallet.showcase.NeighbourActivity
import io.github.avalonskyafar.mallet.showcase.PageScope
import io.github.avalonskyafar.mallet.showcase.Pages
import io.github.avalonskyafar.mallet.showcase.R
import io.github.avalonskyafar.mallet.theme.MalletColors
import io.github.avalonskyafar.mallet.theme.MalletMotion
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.widget.MalletButton
import kotlin.math.roundToInt

/** Pages as places: the map of this app, how to move through it, and an activity as a neighbour. */
object SpatialPage : DemoPage("i_spatial", R.string.page_i_spatial_title, R.string.page_i_spatial_summary) {
    override fun build(page: PageScope) {
        page.title()
        buildMap(page)
        buildTryIt(page)
        buildNeighbour(page)
        buildSystemBack(page)
        page.caption(page.str(R.string.spatial_origin), topDp = 22)
    }

    private fun buildMap(page: PageScope) {
        val places = listOf(
            MapPlace(Pages.HOME, 1, 1, R.string.spatial_route_home),
            MapPlace(AppearancePage.key, 1, 0, R.string.spatial_route_appearance),
            MapPlace(FoundationsPage.key, 0, 1, R.string.spatial_route_foundations),
            MapPlace(ComponentsPage.key, 1, 2, R.string.spatial_route_components),
            MapPlace(InteractionsPage.key, 2, 1, R.string.spatial_route_interactions),
            MapPlace(key, 3, 1, R.string.spatial_route_here, parent = 4, opened = true),
        )
        val route = page.text("", MalletTextRole.BODY_COMPACT, MalletTextTone.MUTED)
        val names = places.map { page.str(Pages.titleOf(it.key)) }
        val map = SpatialMapView(page.context, places, names, here = places.lastIndex)
        fun describe(index: Int) {
            val text = page.str(places[index].route)
            route.text = text
            map.contentDescription = page.context.getString(R.string.spatial_map_place, names[index], text)
        }
        map.onSelected = ::describe
        describe(map.selected)
        page.section(R.string.spatial_map_title, R.string.spatial_map_detail) {
            addView(map, LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP))
            addDivider(8f)
            addView(route, inset(page, bottomDp = 5))
        }
        page.caption(page.str(R.string.spatial_map_legend))
        page.code("""
            val graph = MalletSpatialGraph.Builder("home")
                .connect("home", Side.START, "foundations")
                .connect("home", Side.END, "interactions")
                .connect("home", Side.BELOW, "components")
                .connect("home", Side.ABOVE, "appearance")
                .child("interactions", Side.END, "i_spatial")
                .build()
            host.setGraph(graph) { key -> createPageView(key) }
        """)
    }

    private fun buildTryIt(page: PageScope) {
        val steps = intArrayOf(
            R.string.spatial_try_directions, R.string.spatial_try_reverse,
            R.string.spatial_try_threshold, R.string.spatial_try_flick,
        )
        val live = page.text(page.str(R.string.spatial_live_idle), MalletTextRole.BODY_COMPACT, MalletTextTone.MUTED)
        val parentName = page.str(Pages.titleOf(InteractionsPage.key))
        val listener = object : MalletSpatialHost.Listener {
            override fun onPageScrolled(from: String, to: String, fraction: Float) {
                if (from != key) return
                val lands = fraction >= MalletMotion.COMMIT_FRACTION
                val percent = (fraction * 100).roundToInt()
                live.tone = if (lands) MalletTextTone.ACCENT else MalletTextTone.INK
                live.text = page.str(if (lands) R.string.spatial_live_lands else R.string.spatial_live_returns)
                    .format(percent, page.str(Pages.titleOf(to)))
            }

            override fun onPageSettled(key: String) {
                live.tone = MalletTextTone.MUTED
                live.text = page.str(R.string.spatial_live_idle)
            }
        }
        // The page listens to the host it is shown in, for as long as it is shown.
        live.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            private var spatialHost: MalletSpatialHost? = null

            override fun onViewAttachedToWindow(view: View) {
                var parent = view.parent
                while (parent != null && parent !is MalletSpatialHost) parent = parent.parent
                spatialHost = (parent as? MalletSpatialHost)?.also { it.addListener(listener) }
            }

            override fun onViewDetachedFromWindow(view: View) {
                spatialHost?.removeListener(listener)
                spatialHost = null
            }
        })
        page.section(R.string.spatial_try_title, R.string.spatial_try_detail) {
            steps.forEachIndexed { index, step ->
                if (index > 0) addDivider(7f)
                addView(stepRow(page, index + 1, page.str(step)), inset(page, topDp = if (index == 0) 5 else 0))
            }
            addDivider(7f)
            addView(page.text(page.context.getString(R.string.spatial_live_title, parentName), MalletTextRole.LABEL), inset(page))
            addView(live, inset(page, topDp = 3, bottomDp = 5))
        }
        page.code("""
            val watch = object : MalletSpatialHost.Listener {
                override fun onPageScrolled(
                    from: String, to: String, fraction: Float,
                ) {
                    // 0.28 of the way
                    val commit = MalletMotion.COMMIT_FRACTION
                    val lands = fraction >= commit
                    status.text =
                        if (lands) "lands" else "glides back"
                }
            }
            host.addListener(watch)
        """)
    }

    private fun buildNeighbour(page: PageScope) {
        page.section(R.string.spatial_neighbour_title, R.string.spatial_neighbour_detail) {
            addView(
                page.text(page.str(R.string.spatial_neighbour_body), MalletTextRole.BODY_COMPACT),
                inset(page, topDp = 5, bottomDp = 5),
            )
        }
        val open = MalletButton(page.context).apply {
            variant = MalletButton.Variant.PRIMARY
            text = page.str(R.string.spatial_neighbour_open)
            setOnClickListener {
                MalletSpatialTransition.start(page.host.activity, Intent(page.context, NeighbourActivity::class.java))
            }
        }
        page.loose(open, topDp = 14, sideDp = PageScope.BUTTON_SIDE)
        page.code("""
            // In the caller
            val intent =
                Intent(this, NeighbourActivity::class.java)
            MalletSpatialTransition.start(this, intent)

            // In NeighbourActivity.onCreate
            setContentView(
                MalletSpatialTransition.wrap(this, page),
            )
            // In NeighbourActivity, on every way out
            MalletSpatialTransition.finish(this)
        """)
    }

    private fun buildSystemBack(page: PageScope) {
        page.section(R.string.spatial_back_title) {
            addView(
                page.text(page.str(R.string.spatial_back_body), MalletTextRole.BODY_COMPACT),
                inset(page, topDp = 5, bottomDp = 5),
            )
        }
        val back = MalletButton(page.context).apply {
            text = page.context.getString(R.string.spatial_back_go, page.str(Pages.titleOf(InteractionsPage.key)))
            setOnClickListener { page.host.back() }
        }
        page.loose(back, topDp = 14, sideDp = PageScope.BUTTON_SIDE)
        page.code("""
            // In onCreate, after host.setGraph(...)
            back = MalletBack.attach(this, host)

            // Below Android 13, or without enableOnBackInvokedCallback
            override fun onBackPressed() {
                if (back.onBackPressed()) return
                super.onBackPressed()
            }
        """)
    }

    private fun inset(page: PageScope, topDp: Int = 0, bottomDp: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(PageScope.MATCH, PageScope.WRAP).apply {
            marginStart = page.dp(5)
            marginEnd = page.dp(5)
            topMargin = page.dp(topDp)
            bottomMargin = page.dp(bottomDp)
        }

    private fun stepRow(page: PageScope, number: Int, step: String): View =
        LinearLayout(page.context).apply {
            orientation = LinearLayout.HORIZONTAL
            val index = page.text(number.toString(), MalletTextRole.LABEL, MalletTextTone.MUTED)
            addView(index, LinearLayout.LayoutParams(page.dp(22), PageScope.WRAP))
            addView(page.text(step, MalletTextRole.BODY_COMPACT), LinearLayout.LayoutParams(0, PageScope.WRAP, 1f))
        }
}

/**
 * One place on the map: its page, its cell, and how to get there from this page. [parent] is
 * the index of the place it hangs from; [opened] marks a page that is opened from a row.
 */
private class MapPlace(
    val key: String,
    val column: Int,
    val row: Int,
    val route: Int,
    val parent: Int = 0,
    val opened: Boolean = false,
)

/** Draws the places as tiles on a 4 x 3 grid with their connections; a tap selects a place. */
@SuppressLint("ViewConstructor") // built in code only
private class SpatialMapView(
    context: Context,
    private val places: List<MapPlace>,
    val names: List<String>,
    private val here: Int,
) : View(context) {
    private val theme = MalletTheme.of(context)
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val tiles = List(places.size) { RectF() }
    private var labels: List<StaticLayout> = emptyList()
    private val dash = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)

    var onSelected: ((Int) -> Unit)? = null
    var selected: Int = here
        private set

    init {
        isClickable = true
        isFocusable = true
        MalletTextRole.MICRO.applyTo(label, theme, density * resources.configuration.fontScale)
    }

    private fun dp(value: Float): Float = value * density

    private fun select(index: Int) {
        if (index == selected) return
        selected = index
        MalletHaptics.tick(this)
        invalidate()
        onSelected?.invoke(index)
    }

    /** A click without a position (keyboard, accessibility) moves on to the next place. */
    override fun performClick(): Boolean {
        super.performClick()
        select((selected + 1) % places.size)
        return true
    }

    @SuppressLint("ClickableViewAccessibility") // a tap needs its position; performClick is the path without one
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val reach = dp(6f)
                val hit = tiles.indexOfFirst {
                    event.x >= it.left - reach && event.x <= it.right + reach &&
                        event.y >= it.top - reach && event.y <= it.bottom + reach
                }
                if (hit >= 0) select(hit)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val step = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> 1
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> -1
            else -> return super.onKeyUp(keyCode, event)
        }
        select((selected + step + places.size) % places.size)
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = getDefaultSize(dp(320f).roundToInt(), widthMeasureSpec)
        val tileWidth = (width - 2 * dp(SIDE_DP) - (COLUMNS - 1) * dp(GAP_DP)) / COLUMNS
        val textWidth = (tileWidth - 2 * dp(TILE_PAD_DP)).roundToInt().coerceAtLeast(1)
        labels = names.map { name ->
            StaticLayout.Builder.obtain(name, 0, name.length, label, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
        }
        val tileHeight = maxOf(dp(44f), (labels.maxOfOrNull { it.height } ?: 0) + dp(14f))
        val top = dp(TOP_DP)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        places.forEachIndexed { index, place ->
            val column = if (rtl) COLUMNS - 1 - place.column else place.column
            val left = dp(SIDE_DP) + column * (tileWidth + dp(GAP_DP))
            val y = top + place.row * (tileHeight + dp(ROW_GAP_DP))
            tiles[index].set(left, y, left + tileWidth, y + tileHeight)
        }
        val height = 2 * top + ROWS * tileHeight + (ROWS - 1) * dp(ROW_GAP_DP)
        setMeasuredDimension(width, height.roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        val colors = theme.colors
        // Connections first, between the facing edges of two tiles.
        line.strokeWidth = dp(1.5f)
        line.color = MalletColors.blend(colors.surface, colors.muted, 0.7f)
        places.forEachIndexed { index, place ->
            if (index == 0) return@forEachIndexed
            val a = tiles[place.parent]
            val b = tiles[index]
            line.pathEffect = if (place.opened) dash else null
            if (places[place.parent].row == place.row) {
                val y = a.centerY()
                canvas.drawLine(if (a.left < b.left) a.right else a.left, y, if (a.left < b.left) b.left else b.right, y, line)
            } else {
                val x = a.centerX()
                canvas.drawLine(x, if (a.top < b.top) a.bottom else a.top, x, if (a.top < b.top) b.top else b.bottom, line)
            }
        }
        line.pathEffect = null
        val radius = dp(8f)
        tiles.forEachIndexed { index, tile ->
            val isHere = index == here
            fill.color = MalletColors.blend(colors.surface, if (isHere) colors.accent else colors.ink, if (isHere) 0.14f else 0.05f)
            canvas.drawRoundRect(tile, radius, radius, fill)
            val isSelected = index == selected
            line.strokeWidth = dp(if (isSelected) 2f else 1f)
            line.color = when {
                isSelected -> colors.accent
                isHere -> MalletColors.blend(colors.surface, colors.accent, 0.5f)
                else -> colors.hairline
            }
            val inset = line.strokeWidth / 2
            canvas.drawRoundRect(
                tile.left + inset, tile.top + inset, tile.right - inset, tile.bottom - inset, radius - inset, radius - inset, line,
            )
            val text = labels.getOrNull(index) ?: return@forEachIndexed
            label.color = if (isHere) colors.accent else colors.ink
            canvas.save()
            canvas.translate(tile.left + dp(TILE_PAD_DP), tile.centerY() - text.height / 2f)
            text.draw(canvas)
            canvas.restore()
        }
    }

    private companion object {
        const val COLUMNS = 4
        const val ROWS = 3
        const val SIDE_DP = 2f
        const val TOP_DP = 8f
        const val GAP_DP = 12f
        const val ROW_GAP_DP = 14f
        const val TILE_PAD_DP = 4f
    }
}
