package io.github.avalonskyafar.mallet.interaction

import android.content.Context
import android.os.Parcelable
import android.util.SparseArray
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconView
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.testing.Touch
import io.github.avalonskyafar.mallet.theme.MalletMotion
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.widget.MalletSurface
import io.github.avalonskyafar.mallet.widget.MalletText
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletExpandableLayoutTest {

    private val context = Snapshots.context(MalletTheme.light())
    private val density = context.resources.displayMetrics.density

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    /** A section with a 56 dp header (centred title, chevron at the end) and a 120 dp body. */
    private class Section(context: Context, title: String, text: String?, bodyDp: Int?) {
        private val density = context.resources.displayMetrics.density
        val layout = MalletExpandableLayout(context)
        val header = FrameLayout(context)
        val chevron = MalletIconView(context)
        val body: View

        init {
            val label = MalletText.create(context, title, MalletTextRole.HEADING)
            header.addView(
                label,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
                ),
            )
            chevron.icon = MalletIcon.CHEVRON_DOWN
            val side = (24 * density).roundToInt()
            val edge = FrameLayout.LayoutParams(side, side, Gravity.END or Gravity.CENTER_VERTICAL)
            edge.marginEnd = (10 * density).roundToInt()
            header.addView(chevron, edge)
            layout.addView(header, ViewGroup.LayoutParams.MATCH_PARENT, (56 * density).roundToInt())
            body = if (text != null) {
                MalletText.create(context, text, MalletTextRole.BODY_COMPACT, MalletTextTone.MUTED).also {
                    val side = (12 * density).roundToInt()
                    it.gravity = Gravity.CENTER
                    it.setPadding(side, (2 * density).roundToInt(), side, (14 * density).roundToInt())
                }
            } else {
                View(context)
            }
            val height = if (bodyDp == null) ViewGroup.LayoutParams.WRAP_CONTENT else (bodyDp * density).roundToInt()
            layout.addView(body, ViewGroup.LayoutParams.MATCH_PARENT, height)
            layout.setIndicator(chevron)
        }
    }

    private fun section(context: Context = this.context): Section = Section(context, "Details", null, 120)

    /** Moves the clock frame by frame, the way a display would. */
    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, 16L)
            ShadowLooper.idleMainLooper(step, TimeUnit.MILLISECONDS)
            left -= step
        }
    }

    /** Frames only run when the clock moves, so that the middle of an animation can be seen. */
    private fun attachPaused(view: View) {
        Snapshots.attach(view, 300)
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun tapHeader(section: Section) {
        Touch.tap(section.layout, section.layout.width / 2f, section.header.height / 2f)
        Snapshots.idle()
    }

    @Test
    fun `starts expanded with header and body`() {
        val s = section()
        Snapshots.layout(s.layout, 300)
        assertThat(s.layout.isExpanded).isTrue()
        assertThat(s.layout.expansionFraction).isEqualTo(1f)
        assertThat(s.layout.height).isEqualTo(px(56f) + px(120f))
        assertThat(s.layout.header).isSameInstanceAs(s.header)
        assertThat(s.layout.orientation).isEqualTo(android.widget.LinearLayout.VERTICAL)
    }

    @Test
    fun `collapsed body is gone and comes back with its own visibility`() {
        val s = section()
        val hidden = View(context)
        hidden.visibility = View.INVISIBLE
        s.layout.addView(hidden, ViewGroup.LayoutParams.MATCH_PARENT, px(10f))
        s.layout.setExpanded(false, animate = false)
        Snapshots.layout(s.layout, 300)
        assertThat(s.layout.height).isEqualTo(px(56f))
        assertThat(s.body.visibility).isEqualTo(View.GONE)
        assertThat(hidden.visibility).isEqualTo(View.GONE)
        assertThat(s.header.visibility).isEqualTo(View.VISIBLE)

        s.layout.isExpanded = true
        Snapshots.layout(s.layout, 300)
        assertThat(s.body.visibility).isEqualTo(View.VISIBLE)
        assertThat(hidden.visibility).isEqualTo(View.INVISIBLE)
        assertThat(s.layout.height).isEqualTo(px(56f) + px(120f) + px(10f))
    }

    @Test
    fun `a child added while collapsed joins the hidden body`() {
        val s = section()
        s.layout.setExpanded(false, animate = false)
        val late = View(context)
        s.layout.addView(late, ViewGroup.LayoutParams.MATCH_PARENT, px(20f))
        assertThat(late.visibility).isEqualTo(View.GONE)
        s.layout.removeView(late)
        assertThat(late.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `heights at start, middle and end of the animation`() {
        val s = section()
        attachPaused(s.layout)
        val full = px(56f) + px(120f)
        assertThat(s.layout.height).isEqualTo(full)

        s.layout.setExpanded(false, animate = true)
        assertThat(s.layout.isExpanded).isFalse()
        assertThat(s.layout.isSettling).isTrue()
        advance(100)
        val middle = s.layout.height
        assertThat(middle).isGreaterThan(px(56f))
        assertThat(middle).isLessThan(full)
        // The body keeps its size and its place under the header: it is clipped, not squeezed.
        assertThat(s.body.height).isEqualTo(px(120f))
        assertThat(s.body.top).isEqualTo(px(56f))
        assertThat(s.body.visibility).isEqualTo(View.VISIBLE)

        advance(MalletMotion.PAGE_MS)
        assertThat(s.layout.isSettling).isFalse()
        assertThat(s.layout.height).isEqualTo(px(56f))
        assertThat(s.body.visibility).isEqualTo(View.GONE)

        s.layout.toggle()
        advance(MalletMotion.PAGE_MS + 50)
        assertThat(s.layout.height).isEqualTo(full)
        assertThat(s.layout.expansionFraction).isEqualTo(1f)
    }

    @Test
    fun `interruption reverses from the current height`() {
        val s = section()
        attachPaused(s.layout)
        s.layout.setExpanded(false, animate = true)
        advance(100)
        val before = s.layout.expansionFraction
        assertThat(before).isGreaterThan(0f)
        assertThat(before).isLessThan(1f)

        s.layout.setExpanded(true, animate = true)
        assertThat(s.layout.expansionFraction).isEqualTo(before)
        advance(32)
        val after = s.layout.expansionFraction
        assertThat(after).isAtLeast(before)
        assertThat(after).isLessThan(1f)
        advance(MalletMotion.PAGE_MS)
        assertThat(s.layout.expansionFraction).isEqualTo(1f)
        assertThat(s.layout.height).isEqualTo(px(56f) + px(120f))
        assertThat(s.body.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `progress listener reports every step and ends exactly`() {
        val s = section()
        attachPaused(s.layout)
        val seen = ArrayList<Float>()
        s.layout.setOnExpansionProgressListener { _, fraction -> seen += fraction }
        s.layout.setExpanded(false, animate = true)
        advance(MalletMotion.PAGE_MS + 50)
        assertThat(seen.size).isGreaterThan(2)
        assertThat(seen.last()).isEqualTo(0f)
        assertThat(seen).isInOrder(Comparator.reverseOrder<Float>())
    }

    @Test
    fun `reduced motion uses the reduced duration`() {
        val reduced = Snapshots.context(MalletTheme.light().copy(reduceMotion = true))
        val s = section(reduced)
        Snapshots.attach(s.layout, 300)
        s.layout.setExpanded(false, animate = true)
        advance(MalletMotion.REDUCED_MS + 20)
        assertThat(s.layout.expansionFraction).isEqualTo(0f)
        assertThat(s.layout.isSettling).isFalse()
    }

    @Test
    fun `header tap toggles and tells the change listener, setters do not`() {
        val s = section()
        Snapshots.attach(s.layout, 300)
        val changes = ArrayList<Boolean>()
        s.layout.setOnExpansionChangeListener { _, expanded -> changes += expanded }

        s.layout.setExpanded(false, animate = false)
        s.layout.toggle()
        s.layout.isExpanded = true
        assertThat(changes).isEmpty()

        tapHeader(s)
        assertThat(s.layout.isExpanded).isFalse()
        assertThat(changes).containsExactly(false)
        advance(MalletMotion.PAGE_MS + 50)
        tapHeader(s)
        assertThat(s.layout.isExpanded).isTrue()
        assertThat(changes).containsExactly(false, true).inOrder()
    }

    @Test
    fun `header sinks under the finger and returns on release`() {
        val s = section()
        Snapshots.attach(s.layout, 300)
        val x = s.layout.width / 2f
        Touch.dispatch(s.layout, MotionEvent.ACTION_DOWN, x, 20f)
        advance(MalletMotion.PRESS_MS + 30)
        assertThat(s.header.translationY).isWithin(0.01f).of(MalletMotion.PRESS_SINK_DP * density)
        Touch.dispatch(s.layout, MotionEvent.ACTION_UP, x, 20f)
        assertThat(s.header.translationY).isEqualTo(0f)
    }

    @Test
    fun `a tap on the body does not toggle`() {
        val s = section()
        Snapshots.attach(s.layout, 300)
        Touch.tap(s.layout, s.layout.width / 2f, px(56f) + px(60f).toFloat())
        Snapshots.idle()
        assertThat(s.layout.isExpanded).isTrue()
    }

    @Test
    fun `indicator turns with the progress and mirrors in RTL`() {
        val s = section()
        attachPaused(s.layout)
        assertThat(s.chevron.rotation).isEqualTo(180f)
        s.layout.setExpanded(false, animate = true)
        advance(100)
        assertThat(s.chevron.rotation).isWithin(0.01f).of(180f * s.layout.expansionFraction)
        assertThat(s.chevron.rotation).isLessThan(180f)
        advance(MalletMotion.PAGE_MS)
        assertThat(s.chevron.rotation).isEqualTo(0f)

        s.layout.setIndicator(null)
        s.layout.isExpanded = true
        assertThat(s.chevron.rotation).isEqualTo(0f)
        assertThat(s.layout.getIndicator()).isNull()

        val app = context.applicationContext
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val rtl = section()
        rtl.layout.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(rtl.layout, 300)
        assertThat(rtl.chevron.rotation).isEqualTo(-180f)
        // start / end follow the direction: the chevron sits at the left edge.
        assertThat(rtl.chevron.right).isLessThan(rtl.layout.width / 2)
    }

    @Test
    fun `enter and space on the focused header toggle`() {
        val s = section()
        Snapshots.attach(s.layout, 300)
        val changes = ArrayList<Boolean>()
        s.layout.setOnExpansionChangeListener { _, expanded -> changes += expanded }
        assertThat(s.header.isFocusable).isTrue()
        s.header.isFocusableInTouchMode = true
        assertThat(s.header.requestFocus()).isTrue()
        for (code in intArrayOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE)) {
            assertThat(s.layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))).isTrue()
            assertThat(s.layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))).isTrue()
            Snapshots.idle()
        }
        assertThat(changes).containsExactly(false, true).inOrder()
        // A key without a meaning here is not consumed.
        assertThat(s.layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))).isFalse()
        s.layout.isEnabled = false
        assertThat(s.layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE))).isFalse()
    }

    @Test
    fun `accessibility state and actions live on the header`() {
        val s = section()
        Snapshots.attach(s.layout, 300)
        val changes = ArrayList<Boolean>()
        s.layout.setOnExpansionChangeListener { _, expanded -> changes += expanded }
        assertThat(ViewCompat.getStateDescription(s.header).toString()).isEqualTo("Expanded")

        assertThat(s.header.performAccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND, null)).isFalse()
        assertThat(s.header.performAccessibilityAction(AccessibilityNodeInfo.ACTION_COLLAPSE, null)).isTrue()
        assertThat(s.layout.isExpanded).isFalse()
        assertThat(ViewCompat.getStateDescription(s.header).toString()).isEqualTo("Collapsed")
        assertThat(s.header.performAccessibilityAction(AccessibilityNodeInfo.ACTION_COLLAPSE, null)).isFalse()
        assertThat(s.header.performAccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND, null)).isTrue()
        assertThat(changes).containsExactly(false, true).inOrder()

        s.layout.expandedStateDescription = "Open"
        assertThat(ViewCompat.getStateDescription(s.header).toString()).isEqualTo("Open")
        assertThat(s.layout.accessibilityClassName.toString()).isEqualTo(MalletExpandableLayout::class.java.name)
    }

    @Test
    fun `save and restore keep the state without calling listeners`() {
        val s = section()
        s.layout.id = 7
        s.layout.setExpanded(false, animate = false)
        val saved = SparseArray<Parcelable>()
        s.layout.saveHierarchyState(saved)

        val other = section()
        other.layout.id = 7
        var calls = 0
        other.layout.setOnExpansionChangeListener { _, _ -> calls++ }
        other.layout.restoreHierarchyState(saved)
        Snapshots.layout(other.layout, 300)
        assertThat(other.layout.isExpanded).isFalse()
        assertThat(other.layout.height).isEqualTo(px(56f))
        assertThat(other.body.visibility).isEqualTo(View.GONE)
        assertThat(other.chevron.rotation).isEqualTo(0f)
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun `disabled section keeps its state and does not toggle`() {
        val s = section()
        Snapshots.attach(s.layout, 300)
        s.layout.isEnabled = false
        assertThat(s.header.isEnabled).isFalse()
        tapHeader(s)
        assertThat(s.layout.isExpanded).isTrue()
        assertThat(s.header.translationY).isEqualTo(0f)
        assertThat(s.header.performAccessibilityAction(AccessibilityNodeInfo.ACTION_COLLAPSE, null)).isFalse()
        // Code may still move it.
        s.layout.setExpanded(false, animate = false)
        assertThat(s.layout.isExpanded).isFalse()
        s.layout.isEnabled = true
        tapHeader(s)
        assertThat(s.layout.isExpanded).isTrue()
    }

    @Test
    fun `without toggleOnHeaderClick the header is left alone`() {
        val s = section()
        Snapshots.attach(s.layout, 300)
        s.layout.toggleOnHeaderClick = false
        assertThat(s.header.isClickable).isFalse()
        tapHeader(s)
        assertThat(s.layout.isExpanded).isTrue()
        // The state is still announced and the actions still work.
        assertThat(ViewCompat.getStateDescription(s.header).toString()).isEqualTo("Expanded")
        assertThat(s.header.performAccessibilityAction(AccessibilityNodeInfo.ACTION_COLLAPSE, null)).isTrue()
        s.layout.toggleOnHeaderClick = true
        advance(MalletMotion.PAGE_MS + 50)
        tapHeader(s)
        assertThat(s.layout.isExpanded).isTrue()
    }

    @Test
    fun `the body is clipped from below and stays anchored at its top`() {
        val theme = MalletTheme.of(context)
        val s = section()
        val group = s.body
        group.setBackgroundColor(theme.colors.accent)
        val cap = View(context)
        cap.setBackgroundColor(theme.colors.danger)
        s.layout.addView(cap, 1, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(16f)))
        attachPaused(s.layout)
        s.layout.setExpanded(false, animate = true)
        advance(60)
        val open = s.layout.height - px(56f)
        assertThat(open).isGreaterThan(px(24f))
        assertThat(open).isLessThan(px(136f))

        val bitmap = Snapshots.render(s.layout, 300)
        assertThat(bitmap.height).isEqualTo(s.layout.height)
        val x = bitmap.width / 4
        // The first body child is still right under the header, the rest is cut off below.
        assertThat(bitmap.getPixel(x, px(56f) + px(8f))).isEqualTo(theme.colors.danger)
        assertThat(bitmap.getPixel(x, bitmap.height - 2)).isEqualTo(theme.colors.accent)
        assertThat(bitmap.getPixel(x, px(28f))).isEqualTo(theme.colors.canvas)
    }

    @Test
    fun `a disabled header pales`() {
        val theme = MalletTheme.of(context)
        val s = section()
        s.header.setBackgroundColor(theme.colors.ink)
        val enabled = Snapshots.render(s.layout, 300).getPixel(20, px(28f))
        assertThat(enabled).isEqualTo(theme.colors.ink)
        s.layout.isEnabled = false
        val pale = Snapshots.render(s.layout, 300)
        val pixel = pale.getPixel(20, px(28f))
        assertThat(pixel).isNotEqualTo(theme.colors.ink)
        assertThat(pixel).isNotEqualTo(theme.colors.canvas)
    }

    @Test
    fun `an empty layout and a header alone do not break`() {
        val empty = MalletExpandableLayout(context)
        assertThat(empty.header).isNull()
        empty.toggle()
        empty.setExpanded(true, animate = true)
        Snapshots.layout(empty, 300)
        assertThat(empty.height).isEqualTo(0)
        assertThat(empty.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))).isFalse()

        val only = MalletExpandableLayout(context)
        val head = View(context)
        only.addView(head, ViewGroup.LayoutParams.MATCH_PARENT, px(56f))
        Snapshots.attach(only, 300)
        assertThat(only.height).isEqualTo(px(56f))
        Touch.tap(only, only.width / 2f, px(28f).toFloat())
        Snapshots.idle()
        assertThat(only.isExpanded).isFalse()
        advance(MalletMotion.PAGE_MS + 50)
        assertThat(only.height).isEqualTo(px(56f))
        assertThat(head.visibility).isEqualTo(View.VISIBLE)
        // Removing the header promotes nothing and leaves a usable layout.
        only.removeAllViews()
        assertThat(only.header).isNull()
        assertThat(head.isClickable).isFalse()
    }

    @Test
    fun `the listener may roll back and hears the change before the haptic`() {
        val s = section()
        Snapshots.attach(s.layout, 300)
        val shadow = org.robolectric.Shadows.shadowOf(s.layout)
        var hapticSeenInside = 0
        var calls = 0
        s.layout.setOnExpansionChangeListener { layout, expanded ->
            calls++
            hapticSeenInside = shadow.lastHapticFeedbackPerformed()
            layout.setExpanded(!expanded, animate = false)
        }
        tapHeader(s)
        assertThat(calls).isEqualTo(1)
        assertThat(hapticSeenInside).isEqualTo(-1)
        assertThat(s.layout.isExpanded).isTrue()
        assertThat(s.layout.isSettling).isFalse()
        assertThat(s.layout.expansionFraction).isEqualTo(1f)
        if (MalletTheme.of(context).haptics) {
            assertThat(shadow.lastHapticFeedbackPerformed())
                .isEqualTo(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    @Test
    fun `a cancelled touch releases the header without toggling`() {
        val s = section()
        Snapshots.attach(s.layout, 300)
        val x = s.layout.width / 2f
        Touch.dispatch(s.layout, MotionEvent.ACTION_DOWN, x, 20f)
        Touch.dispatch(s.layout, MotionEvent.ACTION_CANCEL, x, 60f)
        Snapshots.idle()
        assertThat(s.header.translationY).isEqualTo(0f)
        assertThat(s.layout.isExpanded).isTrue()
    }

    @Test
    fun `detaching ends the animation at its target`() {
        val s = section()
        attachPaused(s.layout)
        s.layout.setExpanded(false, animate = true)
        advance(60)
        assertThat(s.layout.isSettling).isTrue()
        (s.layout.parent as ViewGroup).removeView(s.layout)
        assertThat(s.layout.isSettling).isFalse()
        assertThat(s.layout.expansionFraction).isEqualTo(0f)
        assertThat(s.body.visibility).isEqualTo(View.GONE)
        assertThat(s.chevron.rotation).isEqualTo(0f)
    }

    @Test
    fun `long text at text scale 1_13 keeps its measure through a round trip`() {
        val large = Snapshots.context(MalletTheme.light().copy(textScale = 1.13f))
        val text = "A long explanation that has to wrap over several lines in a narrow column. ".repeat(4)
        val s = Section(large, "A fairly long section title", text, null)
        attachPaused(s.layout)
        val bodyHeight = s.body.height
        val full = s.layout.height
        assertThat((s.body as android.widget.TextView).lineCount).isGreaterThan(3)
        assertThat(full).isEqualTo(px(56f) + bodyHeight)

        s.layout.setExpanded(false, animate = true)
        advance(100)
        assertThat(s.body.height).isEqualTo(bodyHeight)
        assertThat(s.layout.height).isLessThan(full)
        advance(MalletMotion.PAGE_MS)
        assertThat(s.layout.height).isEqualTo(px(56f))
        s.layout.toggle()
        advance(MalletMotion.PAGE_MS + 50)
        assertThat(s.layout.height).isEqualTo(full)
        assertThat(s.body.height).isEqualTo(bodyHeight)
    }

    @Test
    fun `xml attributes set the start state and the layout stays top aligned`() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(io.github.avalonskyafar.mallet.R.attr.malletExpandableExpanded, "false")
            .addAttribute(io.github.avalonskyafar.mallet.R.attr.malletExpandableToggleOnHeaderClick, "false")
            .addAttribute(io.github.avalonskyafar.mallet.R.attr.malletExpandableIndicator, "@android:id/icon")
            .build()
        val layout = MalletExpandableLayout(context, attrs)
        val head = FrameLayout(context)
        val mark = View(context)
        mark.id = android.R.id.icon
        head.addView(mark, px(24f), px(24f))
        val body = View(context)
        layout.addView(head, ViewGroup.LayoutParams.MATCH_PARENT, px(56f))
        layout.addView(body, ViewGroup.LayoutParams.MATCH_PARENT, px(40f))
        assertThat(layout.isExpanded).isFalse()
        assertThat(layout.toggleOnHeaderClick).isFalse()
        assertThat(head.isClickable).isFalse()
        assertThat(body.visibility).isEqualTo(View.GONE)

        layout.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        layout.setVerticalGravity(Gravity.BOTTOM)
        layout.orientation = android.widget.LinearLayout.HORIZONTAL
        layout.isExpanded = true
        Snapshots.layout(layout, 300, 200)
        assertThat(layout.getIndicator()).isSameInstanceAs(mark)
        assertThat(mark.rotation).isEqualTo(180f)
        assertThat(head.top).isEqualTo(0)
        assertThat(body.top).isEqualTo(px(56f))
    }

    @Test
    fun `a header disabled under the finger comes back up`() {
        val s = section()
        Snapshots.attach(s.layout, 300)
        Touch.dispatch(s.layout, MotionEvent.ACTION_DOWN, s.layout.width / 2f, 20f)
        advance(MalletMotion.PRESS_MS + 30)
        assertThat(s.header.translationY).isGreaterThan(0f)
        s.layout.isEnabled = false
        assertThat(s.header.translationY).isEqualTo(0f)
        Touch.dispatch(s.layout, MotionEvent.ACTION_UP, s.layout.width / 2f, 20f)
        s.layout.isEnabled = true
        advance(MalletMotion.PRESS_MS + 30)
        assertThat(s.header.translationY).isEqualTo(0f)
        assertThat(s.layout.isExpanded).isTrue()
    }

    @Test
    fun `a header disabled under a held key comes back up and the key does not toggle`() {
        val s = section()
        Snapshots.attach(s.layout, 300)
        val changes = ArrayList<Boolean>()
        s.layout.setOnExpansionChangeListener { _, expanded -> changes += expanded }
        s.header.isFocusableInTouchMode = true
        assertThat(s.header.requestFocus()).isTrue()
        s.layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        advance(MalletMotion.PRESS_MS + 30)
        assertThat(s.header.translationY).isGreaterThan(0f)
        s.layout.isEnabled = false
        assertThat(s.header.translationY).isEqualTo(0f)
        s.layout.isEnabled = true
        s.header.requestFocus()
        s.layout.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        Snapshots.idle()
        assertThat(changes).isEmpty()
        assertThat(s.header.translationY).isEqualTo(0f)
    }

    @Test
    fun `the platform focus highlight is off under the header's own rim`() {
        assertThat(section().header.defaultFocusHighlightEnabled).isFalse()
    }

    private fun gallery(context: Context): View {
        val pad = (16 * context.resources.displayMetrics.density).roundToInt()
        val frame = FrameLayout(context)
        frame.setPadding(pad, pad, pad, pad)
        val card = MalletSurface(context)
        val open = Section(
            context, "Sync details",
            "Last synced a few minutes ago.\nThree devices share this vault.", null,
        )
        val closed = Section(context, "Advanced", "Hidden while collapsed.", null)
        closed.layout.setExpanded(false, animate = false)
        val off = Section(context, "Diagnostics", "Hidden while collapsed.", null)
        off.layout.setExpanded(false, animate = false)
        off.layout.isEnabled = false
        card.addView(open.layout)
        card.addDivider()
        card.addView(closed.layout)
        card.addDivider()
        card.addView(off.layout)
        frame.addView(card)
        return frame
    }

    @Test
    fun `snapshot of expandable`() {
        Snapshots.capture(gallery(Snapshots.context(MalletTheme.light())), "expandable_light", 360)
        Snapshots.capture(gallery(Snapshots.context(MalletTheme.dark())), "expandable_dark", 360)
    }
}
