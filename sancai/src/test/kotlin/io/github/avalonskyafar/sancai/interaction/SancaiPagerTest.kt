package io.github.avalonskyafar.sancai.interaction

import android.content.Context
import android.graphics.Color
import android.os.Parcelable
import android.util.SparseArray
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiColors
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiPageIndicator
import io.github.avalonskyafar.sancai.widget.SancaiSurface
import io.github.avalonskyafar.sancai.widget.SancaiText
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiPagerTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val density = context.resources.displayMetrics.density
    private val widthDp = 300
    private val width = (widthDp * density).roundToInt().toFloat()
    private val midY = 60 * density

    /** A view that claims horizontal drags, like a switch inside a page. */
    private class Owner(context: Context) : View(context), SancaiHorizontalDragOwner {
        override fun onTouchEvent(event: MotionEvent): Boolean = true
    }

    private val created = ArrayList<Int>()
    private val selections = ArrayList<Int>()
    private val scrolls = ArrayList<Float>()

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private fun plainPage(context: Context, index: Int): View = View(context).apply {
        id = 100 + index
        minimumHeight = dp(120)
        setBackgroundColor(PAGE_COLORS[index % PAGE_COLORS.size])
    }

    private fun pager(
        count: Int = 4,
        context: Context = this.context,
        page: (Context, Int) -> View = ::plainPage,
    ): SancaiPager {
        val pager = SancaiPager(context)
        pager.id = 7
        pager.setPages(count) { index, _ ->
            created += index
            page(context, index)
        }
        pager.addOnPageChangeListener(object : SancaiPager.OnPageChangeListener {
            override fun onPageSelected(index: Int) {
                selections += index
            }

            override fun onPageScrolled(index: Int, offset: Float) {
                scrolls += index + offset
            }
        })
        return pager
    }

    private fun attached(count: Int = 4): SancaiPager = pager(count).also { Snapshots.attach(it, widthDp) }

    private fun supportRtl() {
        val info = context.applicationContext.applicationInfo
        info.flags = info.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
    }

    private fun finishMotion() {
        ShadowLooper.idleMainLooper(600, TimeUnit.MILLISECONDS)
    }

    /**
     * Drags by [fraction] of the width over [stepMs] per step; negative goes towards the next
     * page in LTR. The default is slow enough (well under 0.45 widths per second) that only
     * the distance decides.
     */
    private fun drag(
        pager: View,
        fraction: Float,
        release: Boolean = true,
        stepMs: Long = 150,
        y: Float = midY,
    ) {
        val from = if (fraction < 0) width * 0.9f else width * 0.1f
        val steps = 8
        val downTime = android.os.SystemClock.uptimeMillis()
        Touch.dispatch(pager, MotionEvent.ACTION_DOWN, from, y, downTime)
        for (i in 1..steps) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(stepMs))
            Touch.dispatch(pager, MotionEvent.ACTION_MOVE, from + fraction * width * i / steps, y, downTime)
        }
        if (release) Touch.dispatch(pager, MotionEvent.ACTION_UP, from + fraction * width, y, downTime)
    }

    @Test
    fun `pages are created on demand and only the current one shows`() {
        val pager = attached()
        assertThat(created).containsExactly(0)
        assertThat(pager.pageCount).isEqualTo(4)
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(pager.childCount).isEqualTo(1)

        drag(pager, -0.1f, release = false)
        assertThat(created).containsExactly(0, 1).inOrder()
        assertThat(pager.findViewById<View>(101).visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `drag moves both pages edge to edge with the finger`() {
        val pager = attached()
        drag(pager, -0.25f, release = false)
        val old = pager.findViewById<View>(100)
        val new = pager.findViewById<View>(101)
        assertThat(old.translationX).isWithin(1f).of(-0.25f * width)
        assertThat(new.translationX).isWithin(1f).of(0.75f * width)
        assertThat(new.translationX - old.translationX).isWithin(0.01f).of(pager.width.toFloat())
        assertThat(pager.isMoving).isTrue()
        assertThat(pager.scrollPosition).isWithin(0.01f).of(0.25f)
        assertThat(pager.currentPage).isEqualTo(0)
    }

    @Test
    fun `drag past 22 percent lands on the neighbour`() {
        val pager = attached()
        drag(pager, -0.24f)
        assertThat(pager.currentPage).isEqualTo(1)
        assertThat(selections).containsExactly(1)
        assertThat(pager.isMoving).isTrue()
        finishMotion()
        assertThat(pager.isMoving).isFalse()
        assertThat(pager.findViewById<View>(101).translationX).isEqualTo(0f)
        assertThat(pager.findViewById<View>(100).visibility).isEqualTo(View.INVISIBLE)
        assertThat(scrolls.last()).isEqualTo(1f)
    }

    @Test
    fun `drag short of 22 percent glides back`() {
        val pager = attached()
        drag(pager, -0.2f)
        assertThat(pager.currentPage).isEqualTo(0)
        finishMotion()
        assertThat(pager.isMoving).isFalse()
        assertThat(selections).isEmpty()
        assertThat(pager.findViewById<View>(100).translationX).isEqualTo(0f)
        assertThat(pager.findViewById<View>(101).visibility).isEqualTo(View.INVISIBLE)
        assertThat(scrolls.last()).isEqualTo(0f)
    }

    @Test
    fun `a short fast drag lands on the neighbour`() {
        val pager = attached()
        drag(pager, -0.12f, stepMs = 10) // 1.5 widths per second
        assertThat(pager.currentPage).isEqualTo(1)
        finishMotion()
        assertThat(selections).containsExactly(1)
    }

    @Test
    fun `a cancelled drag returns whatever its distance`() {
        val pager = attached()
        drag(pager, -0.6f, release = false)
        Touch.dispatch(pager, MotionEvent.ACTION_CANCEL, width * 0.3f, midY)
        finishMotion()
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(selections).isEmpty()
    }

    @Test
    fun `first and last page do not over-scroll`() {
        val pager = attached(count = 2)
        drag(pager, 0.5f, release = false)
        assertThat(pager.isMoving).isFalse()
        assertThat(pager.findViewById<View>(100).translationX).isEqualTo(0f)
        Touch.dispatch(pager, MotionEvent.ACTION_UP, width * 0.6f, midY)

        pager.currentPage = 1
        drag(pager, -0.5f)
        assertThat(pager.isMoving).isFalse()
        assertThat(pager.currentPage).isEqualTo(1)

        // Dragging back beyond the start of the gesture does not pull in the other side.
        val from = width * 0.1f
        Touch.drag(pager, from, midY, from + width * 0.3f, midY, release = false)
        Touch.dispatch(pager, MotionEvent.ACTION_MOVE, from - width * 0.05f, midY)
        assertThat(pager.scrollPosition).isEqualTo(1f)
        assertThat(pager.findViewById<View>(101).translationX).isWithin(0.01f).of(0f)
    }

    @Test
    fun `vertical movement is given back and a drag owner keeps its gesture`() {
        val pager = attached()
        Touch.drag(pager, width / 2, dp(10).toFloat(), width / 2 - dp(12), dp(110).toFloat(), release = false)
        assertThat(pager.isMoving).isFalse()
        // Once given back, a later sideways turn of the same gesture does not page.
        Touch.dispatch(pager, MotionEvent.ACTION_MOVE, width * 0.1f, dp(110).toFloat())
        assertThat(pager.isMoving).isFalse()
        Touch.dispatch(pager, MotionEvent.ACTION_UP, width * 0.1f, dp(110).toFloat())

        val owning = pager(page = { context, index ->
            FrameLayout(context).apply {
                minimumHeight = dp(120)
                addView(Owner(context), FrameLayout.LayoutParams(dp(100), dp(50)))
                tag = index
            }
        })
        Snapshots.attach(owning, widthDp)
        Touch.drag(owning, dp(90).toFloat(), dp(25).toFloat(), dp(10).toFloat(), dp(25).toFloat(), release = false)
        assertThat(owning.isMoving).isFalse()
        Touch.dispatch(owning, MotionEvent.ACTION_UP, dp(10).toFloat(), dp(25).toFloat())
        // Beside the owner the page turns as usual.
        drag(owning, -0.3f, y = dp(90).toFloat())
        assertThat(owning.currentPage).isEqualTo(1)
    }

    @Test
    fun `a drag over a clickable child is taken once it turns horizontal`() {
        var clicks = 0
        val pager = pager(page = { context, _ ->
            View(context).apply {
                minimumHeight = dp(120)
                setOnClickListener { clicks++ }
            }
        })
        Snapshots.attach(pager, widthDp)
        drag(pager, -0.3f)
        Snapshots.idle()
        assertThat(pager.currentPage).isEqualTo(1)
        assertThat(clicks).isEqualTo(0)
        finishMotion()
        Touch.tap(pager, width / 2, midY)
        Snapshots.idle()
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `programmatic move glides, calls no selection listener and ignores requests meanwhile`() {
        val pager = attached()
        pager.setCurrentPage(3, animate = true)
        assertThat(pager.currentPage).isEqualTo(3)
        assertThat(pager.isMoving).isTrue()
        // The distant page comes in directly; the pages in between are never created.
        assertThat(created).containsExactly(0, 3).inOrder()

        pager.setCurrentPage(1, animate = true)
        Touch.drag(pager, width * 0.1f, midY, width * 0.6f, midY)
        assertThat(pager.currentPage).isEqualTo(3)

        finishMotion()
        // It glided: the position passed through values between the two pages.
        assertThat(scrolls.count { it > 0f && it < 3f }).isAtLeast(3)
        assertThat(pager.isMoving).isFalse()
        assertThat(pager.scrollPosition).isEqualTo(3f)
        assertThat(selections).isEmpty()
        assertThat(scrolls).isInOrder()
        assertThat(pager.findViewById<View>(100)).isNull() // beyond offscreenPageLimit

        pager.setCurrentPage(9, animate = true)
        pager.setCurrentPage(-1, animate = false)
        assertThat(pager.currentPage).isEqualTo(3)
        pager.currentPage = 2
        assertThat(pager.currentPage).isEqualTo(2)
        assertThat(pager.isMoving).isFalse()
        assertThat(selections).isEmpty()
    }

    @Test
    fun `offscreen page limit keeps visited neighbours and recreates nothing`() {
        val pager = attached()
        pager.currentPage = 1
        pager.currentPage = 2
        assertThat(pager.childCount).isEqualTo(2)
        val first = pager.findViewById<View>(101)
        pager.offscreenPageLimit = 0
        assertThat(pager.childCount).isEqualTo(1)
        pager.currentPage = 1
        assertThat(pager.findViewById<View>(101)).isSameInstanceAs(first)
        assertThat(created).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `reduced motion still lands and without a window the move is immediate`() {
        val reduced = pager(context = Snapshots.context(SancaiTheme.light().copy(reduceMotion = true)))
        Snapshots.attach(reduced, widthDp)
        reduced.setCurrentPage(1, animate = true)
        ShadowLooper.idleMainLooper(130, TimeUnit.MILLISECONDS)
        assertThat(reduced.isMoving).isFalse()
        assertThat(reduced.currentPage).isEqualTo(1)

        val loose = pager()
        Snapshots.layout(loose, widthDp)
        loose.setCurrentPage(2, animate = true)
        assertThat(loose.isMoving).isFalse()
        assertThat(loose.currentPage).isEqualTo(2)
    }

    @Test
    fun `a linked pager stays in step`() {
        val heading = pager()
        val content = pager()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(heading, LinearLayout.LayoutParams(dp(200), dp(60)))
            addView(content, LinearLayout.LayoutParams.MATCH_PARENT, dp(120))
        }
        Snapshots.attach(column, widthDp)
        selections.clear()
        content.currentPage = 1
        content.linkWith(heading)
        assertThat(heading.currentPage).isEqualTo(1)

        // The finger moves the content; the heading covers the same share of its own width.
        Touch.drag(content, width * 0.9f, midY, width * 0.5f, midY, release = false)
        assertThat(heading.isMoving).isTrue()
        assertThat(heading.scrollPosition).isWithin(0.001f).of(content.scrollPosition)
        assertThat(heading.findViewById<View>(101).translationX).isWithin(1f).of(-0.4f * dp(200))
        assertThat(heading.findViewById<View>(102).translationX).isWithin(1f).of(0.6f * dp(200))

        Touch.dispatch(content, MotionEvent.ACTION_UP, width * 0.5f, midY)
        assertThat(heading.currentPage).isEqualTo(2)
        ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
        assertThat(heading.scrollPosition).isWithin(0.001f).of(content.scrollPosition)
        finishMotion()
        assertThat(heading.isMoving).isFalse()
        assertThat(heading.findViewById<View>(102).translationX).isEqualTo(0f)
        // One user selection: reported by the pager under the finger, not by its follower.
        assertThat(selections).containsExactly(2)

        content.setCurrentPage(0, animate = true)
        ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS)
        assertThat(heading.scrollPosition).isWithin(0.001f).of(content.scrollPosition)
        finishMotion()
        assertThat(heading.currentPage).isEqualTo(0)

        content.unlink(heading)
        content.currentPage = 3
        assertThat(heading.currentPage).isEqualTo(0)
    }

    @Test
    fun `listener reports position continuously and can be removed`() {
        val pager = SancaiPager(context)
        val seen = ArrayList<Pair<Int, Float>>()
        val listener = object : SancaiPager.OnPageChangeListener {
            override fun onPageScrolled(index: Int, offset: Float) {
                seen += index to offset
            }
        }
        pager.addOnPageChangeListener(listener)
        pager.setPages(3) { index, _ -> plainPage(context, index) }
        Snapshots.attach(pager, widthDp)
        assertThat(seen).containsExactly(0 to 0f)

        pager.currentPage = 1
        seen.clear()
        drag(pager, 0.5f, release = false) // back towards page 0
        assertThat(seen.last().first).isEqualTo(0)
        assertThat(seen.last().second).isWithin(0.01f).of(0.5f)
        assertThat(seen.map { it.first + it.second }).isInOrder(Comparator.reverseOrder<Float>())

        pager.removeOnPageChangeListener(listener)
        seen.clear()
        Touch.dispatch(pager, MotionEvent.ACTION_UP, width * 0.6f, midY)
        finishMotion()
        assertThat(seen).isEmpty()
        assertThat(pager.currentPage).isEqualTo(0)
    }

    @Test
    fun `state is saved and restored without calling the selection listener`() {
        val pager = attached()
        pager.currentPage = 2
        val state = SparseArray<Parcelable>()
        pager.saveHierarchyState(state)

        val restored = pager()
        selections.clear()
        restored.restoreHierarchyState(state)
        assertThat(restored.currentPage).isEqualTo(2)
        assertThat(selections).isEmpty()

        // Restored before the pages exist: the index waits for setPages.
        val late = SancaiPager(context).apply { id = 7 }
        late.restoreHierarchyState(state)
        late.setPages(4) { index, _ -> plainPage(context, index) }
        assertThat(late.currentPage).isEqualTo(2)
        assertThat(late.getChildAt(0).id).isEqualTo(102)
    }

    @Test
    fun `rtl reverses the direction`() {
        supportRtl()
        val pager = pager()
        pager.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.attach(pager, widthDp)
        assertThat(pager.canScrollHorizontally(-1)).isTrue()
        assertThat(pager.canScrollHorizontally(1)).isFalse()

        drag(pager, -0.5f, release = false) // towards the left: nothing before the first page
        assertThat(pager.isMoving).isFalse()
        Touch.dispatch(pager, MotionEvent.ACTION_UP, width * 0.4f, midY)

        drag(pager, 0.3f, release = false)
        assertThat(pager.findViewById<View>(100).translationX).isWithin(1f).of(0.3f * width)
        assertThat(pager.findViewById<View>(101).translationX).isWithin(1f).of(-0.7f * width)
        Touch.dispatch(pager, MotionEvent.ACTION_UP, width * 0.4f, midY)
        finishMotion()
        assertThat(pager.currentPage).isEqualTo(1)

        assertThat(pager.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))).isTrue()
        finishMotion()
        assertThat(pager.currentPage).isEqualTo(2)
    }

    @Test
    fun `can scroll horizontally while there is a page on that side`() {
        val pager = attached(count = 3)
        assertThat(pager.canScrollHorizontally(1)).isTrue()
        assertThat(pager.canScrollHorizontally(-1)).isFalse()
        pager.currentPage = 1
        assertThat(pager.canScrollHorizontally(1)).isTrue()
        assertThat(pager.canScrollHorizontally(-1)).isTrue()
        pager.currentPage = 2
        assertThat(pager.canScrollHorizontally(1)).isFalse()
        assertThat(pager.canScrollHorizontally(-1)).isTrue()
        assertThat(SancaiPager(context).canScrollHorizontally(1)).isFalse()
    }

    @Test
    fun `keys page as the user and are not consumed at the ends`() {
        val pager = attached(count = 2)
        fun key(code: Int) = pager.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        assertThat(key(KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(key(KeyEvent.KEYCODE_PAGE_UP)).isFalse()
        assertThat(key(KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        finishMotion()
        assertThat(pager.currentPage).isEqualTo(1)
        assertThat(selections).containsExactly(1)
        assertThat(key(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(key(KeyEvent.KEYCODE_PAGE_DOWN)).isFalse()
        assertThat(key(KeyEvent.KEYCODE_SPACE)).isFalse()
        assertThat(key(KeyEvent.KEYCODE_PAGE_UP)).isTrue()
        finishMotion()
        assertThat(selections).containsExactly(1, 0).inOrder()
    }

    @Test
    fun `accessibility offers scroll actions and the page text`() {
        val pager = attached(count = 3)
        assertThat(ViewCompat.getStateDescription(pager).toString()).isEqualTo("Page 1 of 3")
        var node = pager.createAccessibilityNodeInfo()
        assertThat(node).isNotNull()
        assertThat(node.isScrollable).isTrue()
        assertThat(node.actionList).contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        assertThat(node.actionList).doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)

        assertThat(pager.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null)).isFalse()
        assertThat(pager.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)).isTrue()
        finishMotion()
        assertThat(pager.currentPage).isEqualTo(1)
        assertThat(selections).containsExactly(1)
        assertThat(ViewCompat.getStateDescription(pager).toString()).isEqualTo("Page 2 of 3")
        node = pager.createAccessibilityNodeInfo()
        assertThat(node.actionList).contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    @Test
    fun `a disabled pager keeps its page`() {
        val pager = attached()
        pager.isEnabled = false
        drag(pager, -0.5f)
        assertThat(pager.isMoving).isFalse()
        assertThat(pager.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN))).isFalse()
        assertThat(pager.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)).isFalse()
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(pager.createAccessibilityNodeInfo().actionList)
            .doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        // Code may still move it.
        pager.currentPage = 1
        assertThat(pager.currentPage).isEqualTo(1)
    }

    @Test
    fun `setPages again replaces the pages and keeps a valid index`() {
        val pager = attached()
        pager.currentPage = 3
        pager.setPages(2) { index, _ -> plainPage(context, index + 10) }
        assertThat(pager.pageCount).isEqualTo(2)
        assertThat(pager.currentPage).isEqualTo(1)
        assertThat(pager.childCount).isEqualTo(1)
        assertThat(pager.getChildAt(0).id).isEqualTo(111)
        pager.setPages(0) { index, _ -> plainPage(context, index) }
        assertThat(pager.childCount).isEqualTo(0)
        drag(pager, -0.5f)
        assertThat(pager.isMoving).isFalse()
    }

    // ---- Indicator ---------------------------------------------------------------------------

    /** Centre of dot [slot] (counted from the left) of an indicator laid out [widthDp] wide. */
    private fun dotX(count: Int, slot: Int): Float = (width - count * 28 * density) / 2f + (slot + 0.5f) * 28 * density

    private fun dotPixel(indicator: SancaiPageIndicator, slot: Int): Int =
        Snapshots.render(indicator, widthDp).getPixel(dotX(indicator.count, slot).toInt(), indicator.height / 2)

    private fun withIndicator(count: Int = 4): Pair<SancaiPager, SancaiPageIndicator> {
        val pager = pager(count)
        val indicator = SancaiPageIndicator(context)
        indicator.attachTo(pager)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(pager, LinearLayout.LayoutParams.MATCH_PARENT, dp(120))
            addView(indicator, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        Snapshots.attach(column, widthDp)
        return pager to indicator
    }

    @Test
    fun `indicator measures 28 dp cells and paints the current dot in the accent`() {
        val colors = SancaiTheme.light().colors
        val indicator = SancaiPageIndicator(context)
        indicator.count = 5
        indicator.selectedIndex = 1
        indicator.measure(0, 0)
        assertThat(indicator.measuredWidth).isEqualTo((5 * 28 * density).roundToInt())
        assertThat(indicator.measuredHeight).isEqualTo((28 * density).roundToInt())

        assertThat(dotPixel(indicator, 1)).isEqualTo(colors.accent)
        assertThat(dotPixel(indicator, 0)).isEqualTo(colors.muted)
        assertThat(dotPixel(indicator, 4)).isEqualTo(colors.muted)
        // 7 dp circles: 5 dp beside the centre is canvas again.
        val bitmap = Snapshots.render(indicator, widthDp)
        val beside = (dotX(5, 1) + 5 * density).toInt()
        assertThat(bitmap.getPixel(beside, indicator.height / 2)).isEqualTo(colors.canvas)
        assertThat(bitmap.getPixel((dotX(5, 1) + 3 * density).toInt(), indicator.height / 2)).isEqualTo(colors.accent)
    }

    @Test
    fun `in the dark the resting dots recede so the accent leads`() {
        val colors = SancaiTheme.dark().colors
        val indicator = SancaiPageIndicator(Snapshots.context(SancaiTheme.dark()))
        indicator.count = 3
        assertThat(dotPixel(indicator, 0)).isEqualTo(colors.accent)
        assertThat(dotPixel(indicator, 1)).isEqualTo(SancaiColors.blend(colors.muted, colors.canvas, 0.5f))
    }

    @Test
    fun `dots cross-blend while the pager moves`() {
        val colors = SancaiTheme.light().colors
        val (pager, indicator) = withIndicator()
        assertThat(indicator.count).isEqualTo(4)
        Touch.drag(pager, width * 0.9f, midY, width * 0.9f - pager.width / 2f, midY, release = false)
        val offset = pager.scrollPosition
        assertThat(offset).isWithin(0.001f).of(0.5f)
        assertThat(dotPixel(indicator, 0)).isEqualTo(SancaiColors.blend(colors.muted, colors.accent, 1f - offset))
        assertThat(dotPixel(indicator, 1)).isEqualTo(SancaiColors.blend(colors.muted, colors.accent, offset))
        assertThat(dotPixel(indicator, 0)).isNotEqualTo(colors.muted)
        assertThat(dotPixel(indicator, 0)).isNotEqualTo(colors.accent)
        assertThat(dotPixel(indicator, 2)).isEqualTo(colors.muted)

        Touch.dispatch(pager, MotionEvent.ACTION_UP, width * 0.4f, midY)
        finishMotion()
        assertThat(indicator.selectedIndex).isEqualTo(1)
        assertThat(dotPixel(indicator, 1)).isEqualTo(colors.accent)
        assertThat(dotPixel(indicator, 0)).isEqualTo(colors.muted)
        assertThat(indicator.contentDescription.toString()).isEqualTo("Page 2 of 4")
    }

    @Test
    fun `tapping a dot moves the pager as the user`() {
        val (pager, indicator) = withIndicator()
        val chosen = ArrayList<Int>()
        indicator.setOnSelectionChangeListener { _, index -> chosen += index }

        Touch.tap(indicator, dotX(4, 2))
        assertThat(pager.currentPage).isEqualTo(2)
        assertThat(pager.isMoving).isTrue()
        assertThat(chosen).containsExactly(2)
        assertThat(selections).containsExactly(2)
        // While the move runs, further taps are ignored, as are taps beside the dots.
        Touch.tap(indicator, dotX(4, 0))
        finishMotion()
        Touch.tap(indicator, dotX(4, 0) - 30 * density)
        Touch.tap(indicator, dotX(4, 2))
        assertThat(pager.currentPage).isEqualTo(2)
        assertThat(chosen).containsExactly(2)

        // Moves from code reach the dots but not the listeners.
        pager.currentPage = 3
        indicator.selectedIndex = 0
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(indicator.selectedIndex).isEqualTo(0)
        assertThat(chosen).containsExactly(2)
        assertThat(selections).containsExactly(2)

        pager.setPages(6) { index, _ -> plainPage(context, index) }
        assertThat(indicator.count).isEqualTo(6)
    }

    @Test
    fun `standalone indicator selects by tap, keys and accessibility`() {
        val indicator = SancaiPageIndicator(context)
        val chosen = ArrayList<Int>()
        indicator.setOnSelectionChangeListener { _, index -> chosen += index }
        indicator.count = 3
        indicator.selectedIndex = 1
        indicator.selectedIndex = 7 // does not exist
        assertThat(indicator.selectedIndex).isEqualTo(1)
        assertThat(chosen).isEmpty()
        Snapshots.attach(indicator, widthDp)

        Touch.tap(indicator, dotX(3, 2))
        assertThat(indicator.selectedIndex).isEqualTo(2)
        fun key(code: Int) = indicator.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
        assertThat(key(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(key(KeyEvent.KEYCODE_DPAD_LEFT)).isTrue()
        assertThat(key(KeyEvent.KEYCODE_MOVE_HOME)).isTrue()
        assertThat(key(KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(chosen).containsExactly(2, 1, 0).inOrder()

        assertThat(indicator.contentDescription.toString()).isEqualTo("Page 1 of 3")
        val node = indicator.createAccessibilityNodeInfo()
        assertThat(node.actionList).contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        assertThat(node.actionList).doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        assertThat(node.actionList).doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        assertThat(indicator.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)).isTrue()
        assertThat(indicator.selectedIndex).isEqualTo(1)
        finishMotion()
        assertThat(dotPixel(indicator, 1)).isEqualTo(SancaiTheme.light().colors.accent)

        indicator.isEnabled = false
        Touch.tap(indicator, dotX(3, 2))
        assertThat(key(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(indicator.selectedIndex).isEqualTo(1)
    }

    @Test
    fun `indicator mirrors in rtl and restores its selection`() {
        supportRtl()
        val indicator = SancaiPageIndicator(context).apply { id = 9 }
        indicator.layoutDirection = View.LAYOUT_DIRECTION_RTL
        indicator.count = 4
        Snapshots.attach(indicator, widthDp)
        assertThat(dotPixel(indicator, 3)).isEqualTo(SancaiTheme.light().colors.accent)
        Touch.tap(indicator, dotX(4, 0)) // leftmost dot is the last page
        assertThat(indicator.selectedIndex).isEqualTo(3)
        assertThat(indicator.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
            .isTrue()
        assertThat(indicator.selectedIndex).isEqualTo(2)

        val state = SparseArray<Parcelable>()
        indicator.saveHierarchyState(state)
        val restored = SancaiPageIndicator(context).apply { id = 9 }
        restored.count = 4
        restored.restoreHierarchyState(state)
        assertThat(restored.selectedIndex).isEqualTo(2)
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "zh")
    fun `page text is translated`() {
        val pager = pager(count = 8)
        pager.currentPage = 1
        assertThat(ViewCompat.getStateDescription(pager).toString()).isEqualTo("第 2 页，共 8 页")
    }

    // ---- Review additions --------------------------------------------------------------------

    /** The first of two fingers lifts: finger 0 at [x0], finger 1 at [x1]. */
    private fun liftFirstFinger(view: View, x0: Float, x1: Float) {
        val props = Array(2) { MotionEvent.PointerProperties().apply { id = it } }
        val coords = arrayOf(x0, x1).map { MotionEvent.PointerCoords().apply { x = it; y = midY } }.toTypedArray()
        val now = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            now, now, MotionEvent.ACTION_POINTER_UP, 2, props, coords, 0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    @Test
    fun `lifting the dragging finger ends the drag instead of jumping to another finger`() {
        val pager = attached()
        drag(pager, -0.1f, release = false)
        liftFirstFinger(pager, width * 0.8f, width * 0.1f)
        // The second finger is now the only one, far to the left: it must not pull the page.
        Touch.dispatch(pager, MotionEvent.ACTION_MOVE, width * 0.1f, midY)
        Touch.dispatch(pager, MotionEvent.ACTION_UP, width * 0.1f, midY)
        finishMotion()
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(selections).isEmpty()
        assertThat(pager.findViewById<View>(100).translationX).isEqualTo(0f)
    }

    @Test
    fun `disabling in the middle of a drag glides back`() {
        val pager = attached()
        drag(pager, -0.6f, release = false)
        pager.isEnabled = false
        Touch.dispatch(pager, MotionEvent.ACTION_UP, width * 0.3f, midY)
        finishMotion()
        assertThat(pager.isMoving).isFalse()
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(selections).isEmpty()
        assertThat(pager.findViewById<View>(100).translationX).isEqualTo(0f)
    }

    @Test
    fun `a disabled pager is not moved through its indicator`() {
        val (pager, indicator) = withIndicator()
        val chosen = ArrayList<Int>()
        indicator.setOnSelectionChangeListener { _, i -> chosen += i }
        pager.isEnabled = false
        Touch.tap(indicator, dotX(4, 2), indicator.height / 2f)
        val right = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertThat(indicator.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, right)).isFalse()
        assertThat(indicator.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)).isFalse()
        finishMotion()
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(chosen).isEmpty()
        assertThat(selections).isEmpty()
        // The dots have no click: Enter is left to whoever wants it.
        val enter = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        assertThat(indicator.onKeyDown(KeyEvent.KEYCODE_ENTER, enter)).isFalse()
    }

    @Test
    fun `a pager without pages and its indicator are inert`() {
        val pager = SancaiPager(context).apply { id = 7 }
        val indicator = SancaiPageIndicator(context)
        indicator.attachTo(pager)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(pager, LinearLayout.LayoutParams.MATCH_PARENT, dp(120))
            addView(indicator, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        Snapshots.attach(column, widthDp)
        drag(pager, -0.5f)
        pager.setCurrentPage(1, animate = true)
        assertThat(pager.isMoving).isFalse()
        assertThat(pager.currentPage).isEqualTo(0)
        assertThat(pager.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN))).isFalse()
        assertThat(pager.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)).isFalse()
        assertThat(pager.createAccessibilityNodeInfo().isScrollable).isFalse()
        assertThat(ViewCompat.getStateDescription(pager)).isNull()
        val state = SparseArray<Parcelable>()
        pager.saveHierarchyState(state)
        pager.restoreHierarchyState(state)

        assertThat(indicator.count).isEqualTo(0)
        assertThat(indicator.width).isEqualTo(0)
        assertThat(indicator.height).isEqualTo(dp(28))
        assertThat(indicator.contentDescription).isNull()
        Touch.tap(indicator, 1f, indicator.height / 2f)
        assertThat(indicator.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)).isFalse()
        Snapshots.render(column, widthDp) // draws nothing, and does not fail

        // Pages arrive later: the dots follow.
        pager.setPages(3) { index, _ -> plainPage(context, index) }
        assertThat(indicator.count).isEqualTo(3)
        assertThat(indicator.contentDescription.toString()).isEqualTo("Page 1 of 3")
    }

    @Test
    fun `long text at text scale 1_13 keeps pages one width wide and the dots their size`() {
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = 1.13f))
        val words = "People whose messages always get through, however strict the keeper is tonight. ".repeat(4)
        val pager = SancaiPager(large)
        pager.setPages(3) { index, _ ->
            SancaiText.create(large, "$index $words", SancaiTextRole.BODY, SancaiTextTone.INK)
        }
        val indicator = SancaiPageIndicator(large)
        indicator.attachTo(pager)
        val column = LinearLayout(large).apply {
            orientation = LinearLayout.VERTICAL
            addView(pager, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(indicator, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        Snapshots.attach(column, widthDp)
        val first = pager.getChildAt(0) as SancaiText
        assertThat(first.lineCount).isGreaterThan(2)
        assertThat(first.width).isEqualTo(pager.width)
        assertThat(pager.height).isAtLeast(first.layout.height)
        assertThat(indicator.width).isEqualTo(dp(28 * 3))
        assertThat(indicator.height).isEqualTo(dp(28))

        drag(pager, -0.5f, y = pager.height / 2f)
        finishMotion()
        assertThat(pager.currentPage).isEqualTo(1)
        val second = pager.getChildAt(pager.childCount - 1)
        assertThat(second.width).isEqualTo(pager.width)
        assertThat(second.translationX).isEqualTo(0f)
        assertThat(second.visibility).isEqualTo(View.VISIBLE)
    }

    // ---- Snapshots ---------------------------------------------------------------------------

    private fun card(context: Context, index: Int): View {
        val card = SancaiSurface(context)
        card.gravity = Gravity.CENTER
        fun line(text: String, role: SancaiTextRole, tone: SancaiTextTone) {
            val view = SancaiText.create(context, text, role, tone)
            view.gravity = Gravity.CENTER
            card.addView(view, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        line(CARD_TITLES[index], SancaiTextRole.HEADING, SancaiTextTone.INK)
        card.addDivider(8f)
        line(CARD_DETAILS[index], SancaiTextRole.BODY_COMPACT, SancaiTextTone.MUTED)
        return FrameLayout(context).apply {
            setPadding(dp(14), dp(6), dp(14), dp(8))
            addView(card, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
    }

    private fun gallery(theme: SancaiTheme, name: String) {
        val context = Snapshots.context(theme)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(16), 0, dp(10))
        }
        fun section(startPage: Int, dragTo: Float?) {
            val pager = SancaiPager(context)
            pager.setPages(4) { index, _ -> card(context, index) }
            pager.currentPage = startPage
            val indicator = SancaiPageIndicator(context)
            indicator.attachTo(pager)
            column.addView(pager, LinearLayout.LayoutParams.MATCH_PARENT, dp(132))
            column.addView(indicator, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (dragTo != null) {
                Snapshots.layout(column, 360)
                val w = pager.width.toFloat()
                Touch.drag(pager, w * 0.9f, midY, w * (0.9f - dragTo), midY, release = false)
            }
        }
        section(startPage = 0, dragTo = null)
        section(startPage = 1, dragTo = 0.38f)
        Snapshots.capture(column, name, 360)
    }

    @Test
    fun `snapshot of pager`() {
        gallery(SancaiTheme.light(), "pager_light")
        gallery(SancaiTheme.dark(), "pager_dark")
    }

    private companion object {
        val PAGE_COLORS = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val CARD_TITLES = arrayOf("Welcome", "Choose a guard level", "Trusted accounts", "Ready")
        val CARD_DETAILS = arrayOf(
            "Three steps and the keeper stands watch.",
            "Gentle, standard or strict.",
            "People whose messages always get through.",
            "Everything is set. Swipe back to review.",
        )
    }
}
