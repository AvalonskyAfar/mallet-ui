package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiGlaze
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import org.robolectric.shadows.ShadowLooper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiChipTest {

    private val light = SancaiTheme.light()
    private val dark = SancaiTheme.dark()

    private val View.density: Float get() = resources.displayMetrics.density
    private fun View.px(dp: Float): Int = (dp * density).roundToInt()

    /** Measures [view] at its natural size and lays it out. */
    private fun <T : View> wrap(view: T): T {
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        view.measure(free, free)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    private fun draw(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(SancaiTheme.of(view.context).colors.canvas)
        view.draw(canvas)
        return bitmap
    }

    private fun chip(
        text: String = "Unread",
        kind: SancaiChip.Kind = SancaiChip.Kind.FILTER,
        theme: SancaiTheme = light,
        checked: Boolean = false,
    ): SancaiChip = SancaiChip(Snapshots.context(theme)).apply {
        id = View.generateViewId()
        this.text = text
        this.kind = kind
        setChecked(checked, animate = false)
        wrap(this)
    }

    private fun SancaiChip.tapCentre() = Touch.tap(this, width / 2f, height / 2f)

    private fun SancaiChip.bodyBounds(): RectF = RectF().also { getBodyBounds(it) }

    /** A pixel of the body beside the content, on the middle line where no depth is layered. */
    private fun SancaiChip.bodyPixel(): Int = draw(this).getPixel(px(2f + 7f), height / 2)

    private fun assertNear(actual: Int, expected: Int, tolerance: Int = 3) {
        assertThat(abs(Color.red(actual) - Color.red(expected))).isAtMost(tolerance)
        assertThat(abs(Color.green(actual) - Color.green(expected))).isAtMost(tolerance)
        assertThat(abs(Color.blue(actual) - Color.blue(expected))).isAtMost(tolerance)
    }

    // ---------------------------------------------------------------------------------------
    // Chip
    // ---------------------------------------------------------------------------------------

    @Test
    fun `chip is a 32 dp capsule inside a 44 dp target`() {
        val view = chip()
        assertThat(view.height).isEqualTo(view.px(44f))
        val body = view.bodyBounds()
        assertThat(body.height()).isWithin(0.01f).of(32f * view.density)
        assertThat(body.centerY()).isWithin(0.01f).of(view.height / 2f)
        assertThat(body.left).isWithin(0.01f).of(2f * view.density)
        assertThat(body.right).isWithin(0.01f).of(view.width - 2f * view.density)
    }

    @Test
    fun `action chip clicks and never checks`() {
        val view = chip(kind = SancaiChip.Kind.ACTION)
        var clicks = 0
        val checks = mutableListOf<Boolean>()
        view.setOnClickListener { clicks++ }
        view.setOnCheckedChangeListener { _, checked -> checks += checked }
        view.tapCentre()
        assertThat(clicks).isEqualTo(1)
        assertThat(view.isChecked).isFalse()
        view.isChecked = true
        assertThat(view.isChecked).isFalse()
        assertThat(checks).isEmpty()
    }

    @Test
    fun `filter chip flips on tap and tells the listener for user input only`() {
        val view = chip()
        val checks = mutableListOf<Boolean>()
        view.setOnCheckedChangeListener { _, checked -> checks += checked }
        view.tapCentre()
        assertThat(view.isChecked).isTrue()
        view.tapCentre()
        assertThat(view.isChecked).isFalse()
        assertThat(checks).containsExactly(true, false).inOrder()
        view.isChecked = true
        view.setChecked(false, animate = false)
        assertThat(checks).hasSize(2)
    }

    @Test
    fun `listener may roll the change back`() {
        val view = chip()
        view.setOnCheckedChangeListener { chip, _ ->
            chip.setChecked(false, animate = false)
        }
        view.tapCentre()
        assertThat(view.isChecked).isFalse()
    }

    @Test
    fun `finger sliding off the chip cancels the press`() {
        val view = chip()
        Touch.drag(view, view.width / 2f, view.height / 2f, view.width / 2f, view.height * 6f)
        assertThat(view.isChecked).isFalse()
    }

    @Test
    fun `checked chip is glazed in the bed colour of its glaze`() {
        for (theme in listOf(light, dark)) {
            val view = chip(theme = theme, checked = true)
            assertThat(view.bodyPixel()).isEqualTo(theme.bedColor(theme.glazes.cobalt))
            view.glaze = theme.glazes.celadon
            assertThat(view.bodyPixel()).isEqualTo(theme.bedColor(theme.glazes.celadon))
            view.setChecked(false, animate = false)
            assertThat(view.bodyPixel()).isNotEqualTo(theme.bedColor(theme.glazes.celadon))
        }
    }

    @Test
    fun `unchecked light chip is white porcelain`() {
        assertNear(chip().bodyPixel(), light.colors.surface, tolerance = 4)
    }

    @Test
    fun `checking makes room for the check mark`() {
        val view = chip()
        val plain = view.width
        view.setChecked(true, animate = false)
        wrap(view)
        assertThat(view.width - plain).isIn(view.px(19f) - 1..view.px(19f) + 1)
        // The mark is drawn in the label colour, before the label.
        val bitmap = draw(view)
        val label = light.labelOnBed(light.glazes.cobalt)
        var marked = 0
        for (x in view.px(16f) until view.px(30f)) {
            for (y in view.height / 2 - view.px(7f) until view.height / 2 + view.px(7f)) {
                if (bitmap.getPixel(x, y) == label) marked++
            }
        }
        assertThat(marked).isGreaterThan(8)
    }

    @Test
    fun `space and enter act, other keys pass through`() {
        val view = chip()
        fun down(code: Int) = view.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
        fun up(code: Int) = view.onKeyUp(code, KeyEvent(KeyEvent.ACTION_UP, code))
        assertThat(down(KeyEvent.KEYCODE_SPACE)).isTrue()
        assertThat(view.isChecked).isFalse()
        assertThat(up(KeyEvent.KEYCODE_SPACE)).isTrue()
        assertThat(view.isChecked).isTrue()
        down(KeyEvent.KEYCODE_ENTER)
        up(KeyEvent.KEYCODE_ENTER)
        assertThat(view.isChecked).isFalse()
        assertThat(down(KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(down(KeyEvent.KEYCODE_A)).isFalse()
    }

    @Test
    fun `disabled chip ignores input and pales`() {
        val view = chip(checked = true)
        val enabled = view.bodyPixel()
        view.isEnabled = false
        view.tapCentre()
        assertThat(view.onKeyDown(KeyEvent.KEYCODE_SPACE, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))).isFalse()
        assertThat(view.performClick()).isFalse()
        assertThat(view.isChecked).isTrue()
        assertThat(view.bodyPixel()).isNotEqualTo(enabled)
    }

    @Test
    fun `checked state survives save and restore without calling back`() {
        val view = chip(checked = true)
        val state = SparseArray<Parcelable>()
        view.saveHierarchyState(state)
        val copy = chip()
        copy.id = view.id
        var calls = 0
        copy.setOnCheckedChangeListener { _, _ -> calls++ }
        copy.restoreHierarchyState(state)
        assertThat(copy.isChecked).isTrue()
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `chip mirrors in right-to-left layouts`() {
        supportRtl()
        fun inkStart(rtl: Boolean): Float {
            val view = chip(text = "Starred")
            view.icon = SancaiIcon.STAR
            view.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            wrap(view)
            // The icon is drawn with a thin stroke, the label is denser: weigh the ink per half.
            val bitmap = draw(view)
            val body = bitmap.getPixel(view.px(9f), view.height / 2)
            var left = 0
            var right = 0
            for (y in view.height / 2 - view.px(8f) until view.height / 2 + view.px(8f)) {
                for (x in view.px(14f) until view.width - view.px(14f)) {
                    if (bitmap.getPixel(x, y) != body) if (x < view.width / 2) left++ else right++
                }
            }
            return left.toFloat() / (left + right)
        }
        // Left to right the sparse icon leads; right to left it trails.
        assertThat(inkStart(rtl = false)).isLessThan(0.5f)
        assertThat(inkStart(rtl = true)).isGreaterThan(0.5f)
    }

    @Test
    fun `accessibility describes the kind and the state`() {
        val filter = chip(checked = true)
        val info = AccessibilityNodeInfo.obtain()
        filter.onInitializeAccessibilityNodeInfo(info)
        assertThat(info.isCheckable).isTrue()
        assertThat(info.isChecked).isTrue()
        assertThat(info.text.toString()).isEqualTo("Unread")
        assertThat(filter.accessibilityClassName.toString()).isEqualTo("android.widget.CheckBox")
        val action = chip(kind = SancaiChip.Kind.ACTION)
        val actionInfo = AccessibilityNodeInfo.obtain()
        action.onInitializeAccessibilityNodeInfo(actionInfo)
        assertThat(actionInfo.isCheckable).isFalse()
        assertThat(action.accessibilityClassName.toString()).isEqualTo("android.widget.Button")
    }

    @Test
    fun `chip without a label or with an over-long one keeps its shape`() {
        val bare = chip(text = "")
        assertThat(bare.bodyBounds().width()).isWithin(1f).of(32f * bare.density)
        draw(bare)
        val iconOnly = chip(text = "").apply {
            icon = SancaiIcon.PLUS
            wrap(this)
        }
        // 14 dp of padding on each side of the 16 dp icon, and no gap for a missing label.
        assertThat(iconOnly.bodyBounds().width()).isAtLeast(44f * iconOnly.density)
        draw(iconOnly)

        val long = SancaiChip(Snapshots.context(light)).apply {
            text = "Everything that arrived while you were away last week"
            kind = SancaiChip.Kind.FILTER
            setChecked(true, animate = false)
        }
        val limit = long.px(140f)
        long.measure(
            MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        long.layout(0, 0, long.measuredWidth, long.measuredHeight)
        assertThat(long.width).isEqualTo(limit)
        // The label is ellipsized inside the padding: the glaze shows at the far end.
        val bitmap = draw(long)
        assertNear(bitmap.getPixel(limit - long.px(2f + 7f), long.height / 2), light.bedColor(light.glazes.cobalt))
    }

    @Test
    fun `checking glides the width, jumps with reduced motion and settles on detach`() {
        val view = SancaiChip(Snapshots.context(light)).apply {
            text = "Unread"
            kind = SancaiChip.Kind.FILTER
        }
        val frame = LinearLayout(view.context)
        frame.addView(view, LinearLayout.LayoutParams(-2, -2))
        Snapshots.attach(frame)
        val plain = view.width
        val grown = plain + view.px(19f)
        view.setChecked(true, animate = true)
        // The glide starts from the old width; it has not jumped. (Any idling of the test
        // looper runs the whole animation, so the values in between cannot be sampled.)
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        view.measure(free, free)
        assertThat(view.measuredWidth).isEqualTo(plain)
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
        assertThat(view.width).isIn(grown - 1..grown + 1)

        // Detached while the glide back is still pending: the chip comes to rest at once.
        view.setChecked(false, animate = true)
        view.measure(free, free)
        assertThat(view.measuredWidth).isIn(grown - 1..grown + 1)
        frame.removeView(view)
        assertThat(wrap(view).width).isEqualTo(plain)

        val still = chip(theme = light.copy(reduceMotion = true))
        val frame2 = LinearLayout(still.context)
        frame2.addView(still, LinearLayout.LayoutParams(-2, -2))
        Snapshots.attach(frame2)
        val before = still.width
        still.tapCentre()
        Snapshots.idle()
        assertThat(still.isChecked).isTrue()
        assertThat(still.width).isIn(before + still.px(19f) - 1..before + still.px(19f) + 1)
    }

    // ---------------------------------------------------------------------------------------
    // Group
    // ---------------------------------------------------------------------------------------

    @Test
    fun `chip disabled or left by the window under a press comes back up`() {
        val view = chip()
        val rest = draw(view)
        Touch.dispatch(view, android.view.MotionEvent.ACTION_DOWN, view.width / 2f, view.height / 2f)
        assertThat(draw(view).sameAs(rest)).isFalse()
        view.isEnabled = false
        view.isEnabled = true
        assertThat(draw(view).sameAs(rest)).isTrue()
        assertThat(view.isChecked).isFalse()

        val code = KeyEvent.KEYCODE_SPACE
        view.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
        assertThat(draw(view).sameAs(rest)).isFalse()
        view.onWindowFocusChanged(false)
        assertThat(draw(view).sameAs(rest)).isTrue()
        // The key-up that still arrives finds no press to finish.
        assertThat(view.onKeyUp(code, KeyEvent(KeyEvent.ACTION_UP, code))).isFalse()
        assertThat(view.isChecked).isFalse()
    }

    @Test
    fun `chip detached in mid-glide is measured again when it comes back`() {
        val view = SancaiChip(Snapshots.context(light)).apply {
            text = "Unread"
            kind = SancaiChip.Kind.FILTER
        }
        val inner = LinearLayout(view.context)
        inner.addView(view, LinearLayout.LayoutParams(-2, -2))
        val outer = LinearLayout(view.context)
        outer.addView(inner, LinearLayout.LayoutParams(-2, -2))
        Snapshots.attach(outer)
        val plain = view.width
        view.setChecked(true, animate = false)
        Snapshots.idle()
        val grown = view.width
        assertThat(grown).isGreaterThan(plain)

        // A layout pass while the glide back is under way: nothing is pending after it.
        view.setChecked(false, animate = true)
        outer.measure(
            MeasureSpec.makeMeasureSpec(outer.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(outer.height, MeasureSpec.EXACTLY),
        )
        outer.layout(outer.left, outer.top, outer.right, outer.bottom)
        assertThat(view.isLayoutRequested).isFalse()
        assertThat(view.width).isEqualTo(grown)

        // The page is taken away and put back with the same room.
        outer.removeView(inner)
        assertThat(view.isLayoutRequested).isTrue()
        outer.addView(inner, LinearLayout.LayoutParams(-2, -2))
        Snapshots.idle()
        assertThat(view.width).isEqualTo(plain)
    }

    @Test
    fun `empty group takes no room and a narrow group clamps its chips`() {
        val empty = group(labels = emptyList())
        assertThat(empty.height).isEqualTo(0)
        assertThat(empty.checkedChipId).isEqualTo(View.NO_ID)
        assertThat(empty.checkedChipIds).isEmpty()

        val narrow = group(labels = listOf("A filter with a very long name indeed", "B"), widthDp = 120)
        assertThat(narrow.chipAt(0).width).isEqualTo(narrow.width)
        assertThat(narrow.chipAt(1).top).isGreaterThan(narrow.chipAt(0).top)
        draw(narrow)
    }

    private fun supportRtl() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
    }

    private fun group(
        labels: List<String> = listOf("All", "Unread", "Starred", "Archived", "With files", "Mine"),
        theme: SancaiTheme = light,
        widthDp: Int = 220,
        rtl: Boolean = false,
    ): SancaiChipGroup = SancaiChipGroup(Snapshots.context(theme)).apply {
        id = View.generateViewId()
        if (rtl) layoutDirection = View.LAYOUT_DIRECTION_RTL
        for (label in labels) {
            addView(SancaiChip(context).apply {
                text = label
                kind = SancaiChip.Kind.FILTER
            })
        }
        Snapshots.layout(this, widthDp)
    }

    private fun SancaiChipGroup.chipAt(index: Int): SancaiChip = getChildAt(index) as SancaiChip

    /** The capsule of the chip at [index], in the coordinates of the group. */
    private fun SancaiChipGroup.bodyOf(index: Int): RectF {
        val chip = chipAt(index)
        return chip.bodyBounds().apply { offset(chip.left.toFloat(), chip.top.toFloat()) }
    }

    @Test
    fun `group wraps and keeps 8 dp between capsules`() {
        val view = group()
        val gap = 8f * view.density
        var rows = 1
        for (i in 0 until view.childCount) {
            val chip = view.chipAt(i)
            assertThat(chip.left).isAtLeast(0)
            assertThat(chip.right).isAtMost(view.width)
            assertThat(chip.bottom).isAtMost(view.height)
            if (i == 0) continue
            val body = view.bodyOf(i)
            val previous = view.bodyOf(i - 1)
            if (chip.top == view.chipAt(i - 1).top) {
                assertThat(body.left - previous.right).isWithin(1.01f).of(gap)
            } else {
                rows++
                assertThat(body.top - previous.bottom).isWithin(1.01f).of(gap)
                assertThat(chip.left).isEqualTo(0)
            }
        }
        assertThat(rows).isGreaterThan(1)
        // Rows are 40 dp apart: 32 dp of capsule and the gap.
        assertThat(view.height).isIn(view.px(44f + 40f * (rows - 1)) - 2..view.px(44f + 40f * (rows - 1)) + 2)
    }

    @Test
    fun `group flows from the right in right-to-left layouts`() {
        supportRtl()
        val view = group(rtl = true)
        assertThat(view.chipAt(0).right).isEqualTo(view.width)
        assertThat(view.chipAt(1).right).isLessThan(view.chipAt(0).left + view.px(5f))
    }

    @Test
    fun `single selection keeps one chip checked`() {
        val view = group()
        view.isSingleSelection = true
        val heard = mutableListOf<List<Int>>()
        val unchecked = mutableListOf<Boolean>()
        view.setOnCheckedStateChangeListener { _, ids -> heard += ids }
        view.chipAt(0).setOnCheckedChangeListener { _, checked -> unchecked += checked }
        view.chipAt(0).tapCentre()
        view.chipAt(2).tapCentre()
        assertThat(view.chipAt(0).isChecked).isFalse()
        assertThat(view.checkedChipId).isEqualTo(view.chipAt(2).id)
        assertThat(heard).containsExactly(listOf(view.chipAt(0).id), listOf(view.chipAt(2).id)).inOrder()
        assertThat(unchecked).containsExactly(true, false).inOrder()

        // From code: moves the choice, tells nobody.
        view.check(view.chipAt(1).id, animate = false)
        assertThat(view.checkedChipIds).containsExactly(view.chipAt(1).id)
        view.clearCheck(animate = false)
        assertThat(view.checkedChipId).isEqualTo(View.NO_ID)
        assertThat(heard).hasSize(2)
        assertThat(view.chipAt(1).accessibilityClassName.toString()).isEqualTo("android.widget.RadioButton")
    }

    @Test
    fun `multiple selection is the default and a required selection cannot be emptied`() {
        val view = group()
        view.chipAt(0).tapCentre()
        view.chipAt(1).tapCentre()
        assertThat(view.checkedChipIds).containsExactly(view.chipAt(0).id, view.chipAt(1).id).inOrder()

        view.isSelectionRequired = true
        view.chipAt(0).tapCentre()
        assertThat(view.checkedChipIds).containsExactly(view.chipAt(1).id)
        view.chipAt(1).tapCentre()
        assertThat(view.chipAt(1).isChecked).isTrue()

        // Switching to single selection keeps the first checked chip.
        view.chipAt(3).setChecked(true, animate = false)
        view.isSingleSelection = true
        assertThat(view.checkedChipIds).containsExactly(view.chipAt(1).id)
    }

    @Test
    fun `change rolled back by the chip listener leaves the group as it was`() {
        val view = group()
        view.isSingleSelection = true
        view.isSelectionRequired = true
        view.chipAt(0).setChecked(true, animate = false)
        val heard = mutableListOf<List<Int>>()
        val first = mutableListOf<Boolean>()
        view.setOnCheckedStateChangeListener { _, ids -> heard += ids }
        view.chipAt(0).setOnCheckedChangeListener { _, checked -> first += checked }
        view.chipAt(1).setOnCheckedChangeListener { chip, _ -> chip.setChecked(false, animate = false) }
        view.chipAt(1).tapCentre()
        assertThat(view.checkedChipIds).containsExactly(view.chipAt(0).id)
        assertThat(heard).isEmpty()
        assertThat(first).isEmpty()

        // Several may be checked: the refused one is not reported either.
        val many = group()
        val told = mutableListOf<List<Int>>()
        many.setOnCheckedStateChangeListener { _, ids -> told += ids }
        many.chipAt(1).setOnCheckedChangeListener { chip, _ -> chip.setChecked(false, animate = false) }
        many.chipAt(1).tapCentre()
        many.chipAt(2).tapCentre()
        assertThat(told).containsExactly(listOf(many.chipAt(2).id))
    }

    @Test
    fun `key is not consumed by the one checked chip of a required selection`() {
        val view = group()
        view.isSelectionRequired = true
        val only = view.chipAt(0)
        only.setChecked(true, animate = false)
        val rest = draw(only)
        val code = KeyEvent.KEYCODE_ENTER
        assertThat(only.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))).isFalse()
        // It does not sink either.
        assertThat(draw(only).sameAs(rest)).isTrue()
        assertThat(only.onKeyUp(code, KeyEvent(KeyEvent.ACTION_UP, code))).isFalse()
        assertThat(only.isChecked).isTrue()

        // With a second chip checked the key can act again.
        view.chipAt(1).setChecked(true, animate = false)
        assertThat(only.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))).isTrue()
        assertThat(only.onKeyUp(code, KeyEvent(KeyEvent.ACTION_UP, code))).isTrue()
        assertThat(only.isChecked).isFalse()
    }

    @Test
    fun `group restores its chips by position`() {
        val view = group()
        view.chipAt(1).setChecked(true, animate = false)
        view.chipAt(4).setChecked(true, animate = false)
        val state = SparseArray<Parcelable>()
        view.saveHierarchyState(state)
        // Chips made in code get fresh ids after a recreation.
        val copy = group()
        copy.id = view.id
        var calls = 0
        copy.setOnCheckedStateChangeListener { _, _ -> calls++ }
        copy.restoreHierarchyState(state)
        assertThat(copy.checkedChipIds).containsExactly(copy.chipAt(1).id, copy.chipAt(4).id).inOrder()
        assertThat(calls).isEqualTo(0)
    }

    // ---------------------------------------------------------------------------------------
    // Tag
    // ---------------------------------------------------------------------------------------

    private fun tag(text: String, tone: SancaiTag.Tone, theme: SancaiTheme = light): SancaiTag =
        SancaiTag(Snapshots.context(theme)).apply {
            this.text = text
            this.tone = tone
            wrap(this)
        }

    private fun toneGlazes(theme: SancaiTheme): Map<SancaiTag.Tone, SancaiGlaze> = mapOf(
        SancaiTag.Tone.POSITIVE to theme.glazes.celadon,
        SancaiTag.Tone.CAUTION to theme.glazes.ochre,
        SancaiTag.Tone.NEGATIVE to theme.glazes.terracotta,
        SancaiTag.Tone.INFO to theme.glazes.cobalt,
        SancaiTag.Tone.NEUTRAL to theme.glazes.mist,
    )

    @Test
    fun `tag is a 22 dp capsule that cannot be pressed`() {
        val view = tag("Active", SancaiTag.Tone.POSITIVE)
        assertThat(view.height).isEqualTo(view.px(22f))
        assertThat(view.isClickable).isFalse()
        assertThat(view.isFocusable).isFalse()
        val bare = tag("", SancaiTag.Tone.NEUTRAL)
        assertThat(view.width - bare.width).isGreaterThan(0)
        // 9 dp of padding on each side of the label; an empty tag is a 22 dp circle.
        assertThat(bare.width).isEqualTo(view.px(22f))
        val info = AccessibilityNodeInfo.obtain()
        view.onInitializeAccessibilityNodeInfo(info)
        assertThat(info.text.toString()).isEqualTo("Active")
    }

    @Test
    fun `tag takes bed and edge from the glaze of its tone`() {
        for (theme in listOf(light, dark)) {
            for ((tone, glaze) in toneGlazes(theme)) {
                val view = tag("Queued for review", tone, theme)
                assertThat(view.glaze).isEqualTo(glaze)
                val bitmap = draw(view)
                // Beside the label, on the middle line; the bed's depth shading just ends there.
                assertNear(bitmap.getPixel(view.px(5f), view.height / 2), theme.bedColor(glaze), tolerance = 6)
                // No shadow: the corner outside the capsule is untouched canvas.
                assertThat(bitmap.getPixel(0, 0)).isEqualTo(theme.colors.canvas)
                assertThat(bitmap.getPixel(0, view.height - 1)).isEqualTo(theme.colors.canvas)
            }
        }
    }

    @Test
    fun `tag is read by screen readers and survives empty and over-long labels`() {
        val view = tag("Active", SancaiTag.Tone.POSITIVE)
        assertThat(view.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        assertThat(view.accessibilityClassName.toString()).isEqualTo("android.widget.TextView")
        draw(tag("", SancaiTag.Tone.NEUTRAL))

        val long = SancaiTag(Snapshots.context(light)).apply { text = "Waiting for the second reviewer to sign off" }
        val limit = long.px(90f)
        long.measure(
            MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        long.layout(0, 0, long.measuredWidth, long.measuredHeight)
        assertThat(long.width).isEqualTo(limit)
        assertThat(long.height).isEqualTo(long.px(22f))
        // The ellipsized label stays inside the padding: the bed shows at both ends.
        val bitmap = draw(long)
        assertNear(bitmap.getPixel(long.px(5f), long.height / 2), light.bedColor(light.glazes.mist), tolerance = 6)
        assertNear(bitmap.getPixel(limit - long.px(5f), long.height / 2), light.bedColor(light.glazes.mist), tolerance = 6)
    }

    @Test
    fun `large text grows chip and tag without clipping the label`() {
        // textScale 1.13 on its own still fits the nominal heights.
        val scaled = light.copy(textScale = 1.13f)
        val plain = chip()
        val bigger = chip(theme = scaled)
        assertThat(bigger.width).isGreaterThan(plain.width)
        assertThat(bigger.bodyBounds().height()).isWithin(0.5f).of(32f * bigger.density)
        assertThat(tag("Active", SancaiTag.Tone.POSITIVE, scaled).height).isEqualTo(plain.px(22f))

        // Far larger text: the capsules grow and stay inside their views.
        val huge = light.copy(textScale = 2.2f)
        val chip = chip(theme = huge, checked = true)
        val body = chip.bodyBounds()
        assertThat(body.height()).isGreaterThan(32f * chip.density)
        assertThat(body.top).isAtLeast(chip.px(6f) - 1f)
        assertThat(chip.height - body.bottom).isAtLeast(chip.px(6f) - 1f)
        draw(chip)
        val tag = tag("Active", SancaiTag.Tone.POSITIVE, huge)
        assertThat(tag.height).isGreaterThan(tag.px(22f))
        draw(tag)
    }

    // ---------------------------------------------------------------------------------------
    // Snapshot
    // ---------------------------------------------------------------------------------------

    private fun showcase(theme: SancaiTheme): View {
        val context = Snapshots.context(theme)
        val density = context.resources.displayMetrics.density
        fun dp(value: Float) = (value * density).roundToInt()
        fun chip(
            label: String,
            kind: SancaiChip.Kind = SancaiChip.Kind.FILTER,
            checked: Boolean = false,
            icon: SancaiIcon? = null,
            glaze: SancaiGlaze? = null,
            enabled: Boolean = true,
        ) = SancaiChip(context).apply {
            text = label
            this.kind = kind
            this.icon = icon
            if (glaze != null) this.glaze = glaze
            setChecked(checked, animate = false)
            isEnabled = enabled
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(10f), dp(14f), dp(14f))
        }
        val glazes = theme.glazes
        column.addView(SancaiChipGroup(context).apply {
            addView(chip("All", checked = true))
            addView(chip("Unread"))
            addView(chip("Starred", icon = SancaiIcon.STAR))
            addView(chip("Archived", checked = true, glaze = glazes.celadon))
            addView(chip("Muted", checked = true, glaze = glazes.terracotta))
            addView(chip("Snoozed", checked = true, glaze = glazes.ochre, icon = SancaiIcon.CLOCK))
            addView(chip("Plain", checked = true, glaze = glazes.mist))
            addView(chip("Drafts", enabled = false))
            addView(chip("Mine", checked = true, enabled = false))
        })
        column.addView(SancaiChipGroup(context).apply {
            addView(chip("Add filter", SancaiChip.Kind.ACTION, icon = SancaiIcon.PLUS))
            addView(chip("Sort", SancaiChip.Kind.ACTION))
            addView(chip("Reset", SancaiChip.Kind.ACTION, enabled = false))
        })
        column.addView(SancaiChipGroup(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(chip("Next", SancaiChip.Kind.ACTION, icon = SancaiIcon.ARROW_RIGHT))
            addView(chip("Starred", checked = true, icon = SancaiIcon.STAR))
            addView(chip("Unread"))
        })
        val tags = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2f), dp(8f), 0, 0)
        }
        val labels = listOf("Active", "Pending", "Failed", "Syncing", "Draft")
        for ((index, tone) in SancaiTag.Tone.values().withIndex()) {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            if (index > 0) params.marginStart = dp(8f)
            tags.addView(SancaiTag(context).apply {
                text = labels[index]
                this.tone = tone
            }, params)
        }
        column.addView(tags)
        return column
    }

    @Test
    fun `snapshot of chips and tags in light and dark`() {
        supportRtl()
        for ((name, theme) in listOf("light" to light, "dark" to dark)) {
            val bitmap = Snapshots.capture(showcase(theme), "chip_$name", 360)
            assertThat(bitmap.width).isEqualTo((360 * 2.625f).roundToInt())
        }
    }
}
