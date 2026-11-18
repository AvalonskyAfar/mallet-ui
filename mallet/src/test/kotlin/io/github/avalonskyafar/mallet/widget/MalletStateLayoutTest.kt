package io.github.avalonskyafar.mallet.widget

import android.content.Context
import android.graphics.Color
import android.os.Parcelable
import android.util.SparseArray
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.testing.Touch
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import io.github.avalonskyafar.mallet.widget.MalletStateLayout.State
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletStateLayoutTest {

    private val context = Snapshots.context(MalletTheme.light())
    private val density = context.resources.displayMetrics.density

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private fun advance(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    /** A state layout around a 120 dp tall content block. */
    private fun stateLayout(ctx: Context = context): MalletStateLayout = MalletStateLayout(ctx).also {
        it.id = 7
        val content = View(ctx)
        content.isFocusable = true
        it.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, px(120f)))
    }

    /** The children that are in the hierarchy and not hidden. */
    private fun shown(layout: MalletStateLayout): List<View> =
        (0 until layout.childCount).map(layout::getChildAt).filter { it.visibility == View.VISIBLE }

    @Test
    fun `the first child is the content and shows by default`() {
        val layout = stateLayout()
        assertThat(layout.state).isEqualTo(State.CONTENT)
        assertThat(layout.displayedState).isEqualTo(State.CONTENT)
        assertThat(layout.contentView).isSameInstanceAs(layout.getChildAt(0))
        assertThat(layout.childCount).isEqualTo(1)
        assertThat(shown(layout)).containsExactly(layout.contentView)
    }

    @Test
    fun `a second content child is refused`() {
        val layout = stateLayout()
        assertThrows(IllegalStateException::class.java) { layout.addView(View(context)) }
    }

    @Test
    fun `exactly one child is visible in each state`() {
        val layout = stateLayout()
        for (state in listOf(State.LOADING, State.EMPTY, State.ERROR, State.CONTENT, State.EMPTY)) {
            layout.setState(state, animate = false)
            assertThat(layout.displayedState).isEqualTo(state)
            assertThat(shown(layout)).containsExactly(layout.stateView(state))
            // The content stays in the hierarchy; a state view leaves it when it is not shown.
            assertThat(layout.contentView?.parent).isSameInstanceAs(layout)
            assertThat(layout.childCount).isEqualTo(if (state == State.CONTENT) 1 else 2)
        }
    }

    @Test
    fun `default views are created on first use only`() {
        val layout = stateLayout()
        for (state in listOf(State.LOADING, State.EMPTY, State.ERROR)) assertThat(layout.stateView(state)).isNull()

        layout.setState(State.EMPTY, animate = false)
        val empty = layout.stateView(State.EMPTY) as MalletStateView
        assertThat(empty.kind).isEqualTo(MalletStateView.Kind.EMPTY)
        assertThat(empty.title.toString()).isEqualTo(context.getString(R.string.mallet_state_layout_empty_title))
        assertThat(empty.actionButton.visibility).isEqualTo(View.GONE)
        assertThat(layout.stateView(State.LOADING)).isNull()
        assertThat(layout.stateView(State.ERROR)).isNull()

        layout.setState(State.ERROR, animate = false)
        val error = layout.stateView(State.ERROR) as MalletStateView
        assertThat(error.kind).isEqualTo(MalletStateView.Kind.ERROR)
        assertThat(error.detail.toString()).isEqualTo(context.getString(R.string.mallet_state_layout_error_detail))

        // Coming back reuses the same view.
        layout.setState(State.EMPTY, animate = false)
        assertThat(layout.stateView(State.EMPTY)).isSameInstanceAs(empty)
    }

    @Test
    fun `the default loading view is a spinner over a muted caption`() {
        val layout = stateLayout()
        layout.setState(State.LOADING, animate = false)
        val loading = layout.stateView(State.LOADING) as LinearLayout
        val spinner = loading.getChildAt(0)
        val caption = loading.getChildAt(1) as MalletText
        assertThat(spinner).isInstanceOf(MalletSpinner::class.java)
        assertThat(caption.text.toString()).isEqualTo(context.getString(R.string.mallet_state_loading))
        assertThat(caption.role).isEqualTo(MalletTextRole.CAPTION)
        assertThat(caption.tone).isEqualTo(MalletTextTone.MUTED)
        assertThat(loading.contentDescription.toString()).isEqualTo("Loading")

        layout.loadingLabel = "Reading accounts"
        assertThat(caption.text.toString()).isEqualTo("Reading accounts")
        assertThat(loading.contentDescription.toString()).isEqualTo("Reading accounts")

        // Centred in the frame, the spinner centred over the caption.
        Snapshots.layout(layout, 300, 200)
        assertThat(loading.left + loading.width / 2).isEqualTo(layout.width / 2)
        assertThat((loading.top + loading.bottom) / 2).isWithin(1).of(layout.height / 2)
        assertThat(spinner.left + spinner.width / 2).isWithin(1).of(loading.width / 2)
        assertThat(caption.top - spinner.bottom).isEqualTo(px(12f))
    }

    @Test
    fun `setEmpty and setError configure the default messages before and after first use`() {
        val layout = stateLayout()
        var added = 0
        var retried = 0
        layout.setEmpty("No accounts yet", "Accounts you add appear here.", "Add account") { added++ }
        layout.setState(State.EMPTY, animate = false)
        val empty = layout.stateView(State.EMPTY) as MalletStateView
        assertThat(empty.title.toString()).isEqualTo("No accounts yet")
        assertThat(empty.detail.toString()).isEqualTo("Accounts you add appear here.")
        assertThat(empty.actionLabel.toString()).isEqualTo("Add account")
        empty.actionButton.performClick()
        assertThat(added).isEqualTo(1)

        layout.setEmpty("Still nothing")
        assertThat(layout.stateView(State.EMPTY)).isSameInstanceAs(empty)
        assertThat(empty.title.toString()).isEqualTo("Still nothing")
        assertThat(empty.detail).isNull()
        assertThat(empty.actionButton.visibility).isEqualTo(View.GONE)

        layout.setState(State.ERROR, animate = false)
        val error = layout.stateView(State.ERROR) as MalletStateView
        assertThat(error.actionButton.visibility).isEqualTo(View.GONE)
        // A retry callback without a label gets the shared "Retry".
        layout.setError("Could not load", "Nothing was changed.") { retried++ }
        assertThat(error.actionLabel.toString()).isEqualTo(context.getString(R.string.mallet_action_retry))
        error.actionButton.performClick()
        assertThat(retried).isEqualTo(1)
        layout.setError("Could not load", null, "Try again") { retried++ }
        assertThat(error.actionLabel.toString()).isEqualTo("Try again")
    }

    @Test
    fun `custom views replace the defaults and null brings them back`() {
        val layout = stateLayout()
        val customLoading = View(context)
        val customEmpty = View(context)
        val customError = View(context)
        layout.setLoadingView(customLoading)
        layout.setEmptyView(customEmpty)
        layout.setErrorView(customError)
        assertThat(layout.childCount).isEqualTo(1)

        layout.setState(State.LOADING, animate = false)
        assertThat(shown(layout)).containsExactly(customLoading)
        // Without frame params of its own a state view is centred at full width.
        val params = customLoading.layoutParams as FrameLayout.LayoutParams
        assertThat(params.gravity).isEqualTo(Gravity.CENTER)
        assertThat(params.width).isEqualTo(FrameLayout.LayoutParams.MATCH_PARENT)
        layout.setState(State.ERROR, animate = false)
        assertThat(shown(layout)).containsExactly(customError)

        // Replacing the view of the state on screen swaps it in place.
        val other = View(context)
        layout.setErrorView(other)
        assertThat(shown(layout)).containsExactly(other)
        assertThat(customError.parent).isNull()
        layout.setErrorView(null)
        assertThat(shown(layout)).containsExactly(layout.stateView(State.ERROR))
        assertThat(layout.stateView(State.ERROR)).isInstanceOf(MalletStateView::class.java)

        // Configuring a default message drops the custom view of that state.
        layout.setEmpty("No accounts yet")
        layout.setState(State.EMPTY, animate = false)
        assertThat(layout.stateView(State.EMPTY)).isInstanceOf(MalletStateView::class.java)
        assertThat(customEmpty.parent).isNull()
    }

    @Test
    fun `loading stays at least the minimum time`() {
        val layout = stateLayout()
        Snapshots.attach(layout)
        assertThat(layout.minimumLoadingMs).isEqualTo(400L)
        layout.setState(State.LOADING, animate = false)
        advance(100)

        layout.setState(State.CONTENT, animate = false)
        // The request reads back at once; the screen still shows loading.
        assertThat(layout.state).isEqualTo(State.CONTENT)
        assertThat(layout.displayedState).isEqualTo(State.LOADING)
        assertThat(shown(layout)).containsExactly(layout.stateView(State.LOADING))
        advance(250)
        assertThat(layout.displayedState).isEqualTo(State.LOADING)
        advance(100)
        assertThat(layout.displayedState).isEqualTo(State.CONTENT)
        assertThat(shown(layout)).containsExactly(layout.contentView)
    }

    @Test
    fun `a later request replaces the held one and loading again cancels it`() {
        val layout = stateLayout()
        Snapshots.attach(layout)
        layout.setState(State.LOADING, animate = false)
        layout.setState(State.CONTENT, animate = false)
        layout.setState(State.ERROR, animate = false)
        advance(450)
        assertThat(layout.displayedState).isEqualTo(State.ERROR)

        layout.setState(State.LOADING, animate = false)
        layout.setState(State.EMPTY, animate = false)
        layout.setState(State.LOADING, animate = false)
        advance(1000)
        assertThat(layout.state).isEqualTo(State.LOADING)
        assertThat(layout.displayedState).isEqualTo(State.LOADING)
    }

    @Test
    fun `loading that has been up long enough leaves at once, and zero switches the hold off`() {
        val layout = stateLayout()
        Snapshots.attach(layout)
        layout.setState(State.LOADING, animate = false)
        advance(500)
        layout.setState(State.EMPTY, animate = false)
        assertThat(layout.displayedState).isEqualTo(State.EMPTY)

        layout.minimumLoadingMs = 0
        layout.setState(State.LOADING, animate = false)
        layout.setState(State.CONTENT, animate = false)
        assertThat(layout.displayedState).isEqualTo(State.CONTENT)
        layout.minimumLoadingMs = -5
        assertThat(layout.minimumLoadingMs).isEqualTo(0L)
    }

    @Test
    fun `the outgoing view sinks while the incoming view rises, then only one remains`() {
        val layout = stateLayout()
        Snapshots.attach(layout)
        // Frames only run when the clock moves, so that the middle of the transition can be seen.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val content = layout.contentView!!
        layout.state = State.EMPTY
        val empty = layout.stateView(State.EMPTY)!!
        // Both overlap; the incoming view starts 8 dp low.
        assertThat(shown(layout)).containsExactly(content, empty)
        assertThat(empty.translationY).isWithin(0.01f).of(8f * density)
        assertThat(empty.alpha).isEqualTo(1f)

        // Step frame by frame and record (sink of the outgoing, its alpha, rise left of the incoming).
        val shift = 8f * density
        val frames = ArrayList<Triple<Float, Float, Float>>()
        var steps = 0
        while (content.visibility == View.VISIBLE && steps++ < 60) {
            advance(16)
            if (content.visibility == View.VISIBLE) {
                frames += Triple(content.translationY, content.alpha, empty.translationY)
            }
            // No cross-fade: the incoming view is opaque throughout.
            assertThat(empty.alpha).isEqualTo(1f)
        }
        val moving = frames.filter { it.first > 0f && it.first < shift }
        assertWithMessage("frames $frames").that(moving.size).isAtLeast(3)
        // Both move together: what the outgoing view has sunk, the incoming one has risen.
        for (frame in moving) assertThat(frame.first + frame.third).isWithin(0.01f).of(shift)
        // The outgoing view keeps its full alpha at first and pales only at the end of the way.
        assertWithMessage("frames $frames").that(moving.first().second).isEqualTo(1f)
        assertWithMessage("frames $frames").that(frames.any { it.second < 1f }).isTrue()
        for (frame in frames) if (frame.second < 1f) assertThat(frame.first).isGreaterThan(shift * 0.8f)

        advance(50)
        assertThat(shown(layout)).containsExactly(empty)
        assertThat(empty.translationY).isEqualTo(0f)
        assertThat(content.visibility).isEqualTo(View.GONE)
        assertThat(content.translationY).isEqualTo(0f)
        assertThat(content.alpha).isEqualTo(1f)
    }

    @Test
    fun `a change during a transition finishes the first one`() {
        val layout = stateLayout()
        Snapshots.attach(layout)
        layout.state = State.EMPTY
        advance(60)
        layout.state = State.ERROR
        val empty = layout.stateView(State.EMPTY)!!
        val error = layout.stateView(State.ERROR)!!
        assertThat(layout.contentView?.visibility).isEqualTo(View.GONE)
        assertThat(shown(layout)).containsExactly(empty, error)
        advance(400)
        assertThat(shown(layout)).containsExactly(error)
        assertThat(empty.parent).isNull()
        assertThat(empty.alpha).isEqualTo(1f)
    }

    @Test
    fun `reduced motion and an unattached layout swap at once`() {
        val calm = stateLayout(Snapshots.context(MalletTheme.light().copy(reduceMotion = true)))
        Snapshots.attach(calm)
        calm.state = State.ERROR
        assertThat(shown(calm)).containsExactly(calm.stateView(State.ERROR))
        assertThat(calm.stateView(State.ERROR)?.translationY).isEqualTo(0f)

        val loose = stateLayout()
        loose.state = State.EMPTY
        assertThat(shown(loose)).containsExactly(loose.stateView(State.EMPTY))
    }

    @Test
    fun `detaching settles a held state and a running transition`() {
        val layout = stateLayout()
        val activity = Snapshots.attach(layout)
        layout.setState(State.LOADING, animate = false)
        layout.state = State.ERROR
        assertThat(layout.displayedState).isEqualTo(State.LOADING)
        (layout.parent as FrameLayout).removeView(layout)
        // The held state is shown as soon as the detach is over, not during it.
        Snapshots.idle()
        assertThat(layout.displayedState).isEqualTo(State.ERROR)
        assertThat(shown(layout)).containsExactly(layout.stateView(State.ERROR))
        advance(1000)
        assertThat(layout.displayedState).isEqualTo(State.ERROR)
        assertThat(activity.isFinishing).isFalse()
    }

    @Test
    fun `state is saved and restored without animation or hold`() {
        val layout = stateLayout()
        layout.setState(State.ERROR, animate = false)
        val saved = SparseArray<Parcelable>()
        layout.saveHierarchyState(saved)

        val copy = stateLayout()
        Snapshots.attach(copy)
        copy.setState(State.LOADING, animate = false)
        copy.restoreHierarchyState(saved)
        assertThat(copy.state).isEqualTo(State.ERROR)
        assertThat(copy.displayedState).isEqualTo(State.ERROR)
        assertThat(shown(copy)).containsExactly(copy.stateView(State.ERROR))
    }

    @Test
    fun `the requested state is the one saved while loading is held`() {
        val layout = stateLayout()
        Snapshots.attach(layout)
        layout.setState(State.LOADING, animate = false)
        layout.setState(State.EMPTY, animate = false)
        val saved = SparseArray<Parcelable>()
        layout.saveHierarchyState(saved)
        val copy = stateLayout()
        copy.restoreHierarchyState(saved)
        assertThat(copy.displayedState).isEqualTo(State.EMPTY)
    }

    @Test
    fun `xml attributes set the first state and the texts`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.malletStateLayoutState, "empty")
            .addAttribute(R.attr.malletStateLayoutMinimumLoadingMs, "250")
            .addAttribute(R.attr.malletStateLayoutLoadingLabel, "Reading")
            .addAttribute(R.attr.malletStateLayoutEmptyTitle, "No accounts yet")
            .addAttribute(R.attr.malletStateLayoutEmptyDetail, "Add one to begin.")
            .addAttribute(R.attr.malletStateLayoutErrorTitle, "Could not load")
            .addAttribute(R.attr.malletStateLayoutErrorDetail, "The list is unchanged.")
            .build()
        val layout = MalletStateLayout(context, attrs)
        val content = View(context)
        layout.addView(content)
        // The content arrives after the constructor and is hidden as it is added.
        assertThat(layout.state).isEqualTo(State.EMPTY)
        assertThat(content.visibility).isEqualTo(View.GONE)
        assertThat(shown(layout)).containsExactly(layout.stateView(State.EMPTY))
        assertThat(layout.minimumLoadingMs).isEqualTo(250L)
        assertThat(layout.loadingLabel.toString()).isEqualTo("Reading")
        val empty = layout.stateView(State.EMPTY) as MalletStateView
        assertThat(empty.title.toString()).isEqualTo("No accounts yet")
        assertThat(empty.detail.toString()).isEqualTo("Add one to begin.")
        layout.setState(State.ERROR, animate = false)
        val error = layout.stateView(State.ERROR) as MalletStateView
        assertThat(error.title.toString()).isEqualTo("Could not load")
        assertThat(error.detail.toString()).isEqualTo("The list is unchanged.")
    }

    @Test
    fun `a state change is announced as a pane title`() {
        val layout = stateLayout()
        assertThat(ViewCompat.getAccessibilityPaneTitle(layout)).isNull()
        layout.setState(State.LOADING, animate = false)
        assertThat(ViewCompat.getAccessibilityPaneTitle(layout).toString()).isEqualTo("Loading")
        layout.setError("Could not load", "Nothing was changed.")
        layout.setState(State.ERROR, animate = false)
        assertThat(ViewCompat.getAccessibilityPaneTitle(layout).toString()).isEqualTo("Could not load")
        layout.setState(State.CONTENT, animate = false)
        assertThat(ViewCompat.getAccessibilityPaneTitle(layout).toString())
            .isEqualTo(context.getString(R.string.mallet_state_layout_content_shown))
        // A custom view speaks through its content description.
        layout.setEmptyView(View(context).also { it.contentDescription = "No results for this filter" })
        layout.setState(State.EMPTY, animate = false)
        assertThat(ViewCompat.getAccessibilityPaneTitle(layout).toString()).isEqualTo("No results for this filter")
        assertThat(layout.accessibilityClassName.toString()).isEqualTo(MalletStateLayout::class.java.name)
    }

    @Test
    fun `disabling reaches the state views, shown or created later, and leaves the content alone`() {
        val layout = stateLayout()
        var retried = 0
        layout.setError("Could not load", null, "Retry") { retried++ }
        layout.setState(State.ERROR, animate = false)
        val error = layout.stateView(State.ERROR) as MalletStateView
        layout.isEnabled = false
        assertThat(error.isEnabled).isFalse()
        assertThat(error.actionButton.isEnabled).isFalse()
        assertThat(layout.contentView?.isEnabled).isTrue()

        layout.setState(State.LOADING, animate = false)
        assertThat(layout.stateView(State.LOADING)?.isEnabled).isFalse()
        layout.isEnabled = true
        assertThat(layout.stateView(State.LOADING)?.isEnabled).isTrue()
        layout.setState(State.ERROR, animate = false)
        assertThat(error.actionButton.isEnabled).isTrue()
    }

    @Test
    fun `focus inside the outgoing view moves into the incoming one`() {
        val layout = stateLayout()
        Snapshots.attach(layout)
        val content = layout.contentView!!
        content.isFocusableInTouchMode = true
        val error = View(context).also {
            it.isFocusable = true
            it.isFocusableInTouchMode = true
        }
        layout.setErrorView(error)
        assertThat(content.requestFocus()).isTrue()
        layout.setState(State.ERROR, animate = false)
        assertThat(error.isFocused).isTrue()
    }

    @Test
    fun `the message stays centred in a right-to-left layout`() {
        val layout = stateLayout()
        layout.layoutDirection = View.LAYOUT_DIRECTION_RTL
        layout.setState(State.EMPTY, animate = false)
        Snapshots.layout(layout, 320, 300)
        val empty = layout.stateView(State.EMPTY)!!
        assertThat(empty.left + empty.width / 2).isEqualTo(layout.width / 2)
        assertThat((empty.top + empty.bottom) / 2).isWithin(1).of(layout.height / 2)
    }

    @Test
    fun `loading shown before the layout is on screen is held from the moment it attaches`() {
        val layout = stateLayout()
        layout.setState(State.LOADING, animate = false)
        advance(1000)
        Snapshots.attach(layout)
        layout.setState(State.CONTENT, animate = false)
        assertThat(layout.displayedState).isEqualTo(State.LOADING)
        advance(450)
        assertThat(layout.displayedState).isEqualTo(State.CONTENT)
    }

    @Test
    fun `messages without any text still lay out and are announced by their fallback`() {
        val layout = stateLayout()
        layout.setEmpty(null)
        layout.setError(null, null)
        layout.loadingLabel = ""
        for (state in listOf(State.EMPTY, State.ERROR, State.LOADING)) {
            layout.setState(state, animate = false)
            Snapshots.layout(layout, 320)
            val view = layout.stateView(state)!!
            assertWithMessage("$state").that(shown(layout)).containsExactly(view)
            assertWithMessage("$state").that(view.height).isGreaterThan(0)
            assertWithMessage("$state").that(ViewCompat.getAccessibilityPaneTitle(layout).isNullOrEmpty()).isFalse()
        }
        assertThat(ViewCompat.getAccessibilityPaneTitle(layout).toString())
            .isEqualTo(context.getString(R.string.mallet_state_loading))
    }

    /** Every descendant of [root] lies inside [root]'s width. */
    private fun assertInsideWidth(root: View, view: View = root, offset: Int = 0) {
        if (view !== root && view.visibility == View.VISIBLE) {
            assertWithMessage(view.javaClass.simpleName).that(offset + view.left).isAtLeast(0)
            assertWithMessage(view.javaClass.simpleName).that(offset + view.right).isAtMost(root.width)
        }
        if (view is android.view.ViewGroup) {
            val inner = if (view === root) 0 else offset + view.left
            for (i in 0 until view.childCount) assertInsideWidth(root, view.getChildAt(i), inner)
        }
    }

    @Test
    fun `long text at large text scale wraps inside a narrow layout, also right-to-left`() {
        val big = Snapshots.context(MalletTheme.light().copy(textScale = 1.13f))
        val words = "The list of whitelisted accounts could not be read from the device storage "
        for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            val short = stateLayout(big)
            short.setError("Failed", "Try again.", "Retry") {}
            short.setState(State.ERROR, animate = false)
            Snapshots.layout(short, 240)

            val layout = stateLayout(big)
            layout.layoutDirection = direction
            layout.setError(words.repeat(2), words.repeat(4), words) {}
            layout.loadingLabel = words.repeat(3)
            layout.setState(State.ERROR, animate = false)
            Snapshots.layout(layout, 240)
            val error = layout.stateView(State.ERROR)!!
            assertThat(error.width).isEqualTo(layout.width)
            assertThat(layout.height).isGreaterThan(short.height)
            assertThat(error.bottom).isAtMost(layout.height)
            assertInsideWidth(layout)

            layout.setState(State.LOADING, animate = false)
            Snapshots.layout(layout, 240)
            assertThat(layout.stateView(State.LOADING)!!.bottom).isAtMost(layout.height)
            assertInsideWidth(layout)
        }
    }

    @Test
    fun `only the shown state is painted`() {
        val layout = stateLayout()
        layout.contentView?.setBackgroundColor(Color.MAGENTA)
        val canvas = MalletTheme.light().colors.canvas
        val asContent = Snapshots.render(layout, 300)
        assertThat(asContent.getPixel(4, 4)).isEqualTo(Color.MAGENTA)

        layout.setState(State.EMPTY, animate = false)
        val asEmpty = Snapshots.render(layout, 300)
        // No card and no backdrop: the message sits on the bare canvas.
        assertThat(asEmpty.getPixel(4, 4)).isEqualTo(canvas)
        assertThat(asEmpty.getPixel(asEmpty.width - 5, asEmpty.height - 5)).isEqualTo(canvas)

        layout.setState(State.LOADING, animate = false)
        val asLoading = Snapshots.render(layout, 300)
        assertThat(asLoading.getPixel(4, 4)).isEqualTo(canvas)
        // The ring is drawn in the muted colour somewhere on the spinner's middle row.
        val loading = layout.stateView(State.LOADING) as LinearLayout
        val row = loading.top + loading.paddingTop + loading.getChildAt(0).height / 2
        val muted = MalletTheme.light().colors.muted
        assertThat((0 until asLoading.width).any { asLoading.getPixel(it, row) == muted }).isTrue()
    }

    @Test
    fun `a second tap on an action that is leaving does not reach it`() {
        val layout = stateLayout()
        var retries = 0
        layout.setError("Could not load", null, "Retry") {
            retries++
            layout.state = State.LOADING
        }
        layout.setState(State.ERROR, animate = false)
        Snapshots.attach(layout)
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        // With frames paused a posted click waits for the next frame: each tap is followed by one.
        val error = layout.stateView(State.ERROR) as MalletStateView
        val button = error.actionButton
        val spot = android.graphics.Rect(0, 0, button.width, button.height)
        layout.offsetDescendantRectToMyCoords(button, spot)
        val x = spot.exactCenterX()
        val y = spot.exactCenterY()

        Touch.tap(layout, x, y)
        advance(32)
        assertThat(retries).isEqualTo(1)
        // The error view is still there, sinking: the same spot is tapped again.
        assertThat(error.parent).isSameInstanceAs(layout)
        Touch.tap(layout, x, y)
        advance(32)
        assertThat(retries).isEqualTo(1)

        // Once the swap is over the layout passes touches on again.
        advance(400)
        layout.setState(State.ERROR, animate = false)
        advance(2000)
        assertThat(layout.displayedState).isEqualTo(State.ERROR)
        Touch.tap(layout, x, y)
        advance(32)
        assertThat(retries).isEqualTo(2)
    }

    @Test
    fun `removeAllViews swaps the content and keeps the state on screen`() {
        val layout = stateLayout()
        layout.setState(State.ERROR, animate = false)
        Snapshots.attach(layout)
        val error = layout.stateView(State.ERROR)!!
        layout.removeAllViews()
        assertThat(layout.contentView).isNull()
        val fresh = View(context)
        layout.addView(fresh, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, px(120f)))
        Snapshots.idle()
        assertThat(layout.contentView).isSameInstanceAs(fresh)
        assertThat(shown(layout)).containsExactly(error)
        layout.setState(State.CONTENT, animate = false)
        assertThat(shown(layout)).containsExactly(fresh)
    }

    @Test
    fun `a held state shown on detach is not attached to the window being left`() {
        var attached = 0
        var detached = 0
        val custom = object : View(context) {
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                attached++
            }

            override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                detached++
            }
        }
        val layout = stateLayout()
        layout.setErrorView(custom)
        Snapshots.attach(layout)
        layout.setState(State.LOADING, animate = false)
        layout.state = State.ERROR
        val frame = layout.parent as FrameLayout
        frame.removeView(layout)
        Snapshots.idle()
        assertThat(layout.displayedState).isEqualTo(State.ERROR)
        assertThat(custom.parent).isSameInstanceAs(layout)
        assertThat(attached).isEqualTo(0)
        assertThat(custom.isAttachedToWindow).isFalse()

        frame.addView(layout)
        assertThat(attached).isEqualTo(1)
        assertThat(detached).isEqualTo(0)
    }

    /** One page: the four states on the canvas, then loading inside the card that will hold the result. */
    private fun gallery(theme: MalletTheme): View {
        val ctx = Snapshots.context(theme)
        val page = LinearLayout(ctx)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(px(16f), px(12f), px(16f), px(16f))

        fun section(name: String, body: View) {
            val label = MalletText.create(ctx, name, MalletTextRole.MICRO, MalletTextTone.MUTED)
            val labelParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            labelParams.topMargin = px(8f)
            labelParams.bottomMargin = px(6f)
            page.addView(label, labelParams)
            page.addView(body, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        fun card(): MalletSurface = MalletSurface(ctx).also {
            it.addView(MalletText.create(ctx, "Whitelisted accounts", MalletTextRole.SUBHEAD))
            it.addView(MalletText.create(ctx, "Three accounts are exempt from the rules.", MalletTextRole.CAPTION, MalletTextTone.MUTED))
        }

        fun layout(state: State, content: View = card()): MalletStateLayout = MalletStateLayout(ctx).also {
            it.addView(content)
            it.setEmpty("No accounts yet", "Accounts you add are exempt from the rules.", "Add account") {}
            it.setError("Could not read the list", "Nothing was changed. Check the connection and try again.") {}
            it.setState(state, animate = false)
        }

        section("CONTENT", layout(State.CONTENT))
        section("LOADING", layout(State.LOADING))
        section("EMPTY", layout(State.EMPTY))
        section("ERROR", layout(State.ERROR))

        // The habit this layout comes from: the wait is shown inside the card that will hold the result.
        val host = MalletSurface(ctx)
        host.addView(MalletText.create(ctx, "Performance", MalletTextRole.SUBHEAD))
        val inner = MalletStateLayout(ctx)
        inner.addView(MalletText.create(ctx, "Result", MalletTextRole.BODY))
        inner.loadingLabel = "Measuring"
        inner.setState(State.LOADING, animate = false)
        host.addView(inner)
        section("LOADING IN A CARD", host)
        return page
    }

    @Test
    fun `snapshot of state layouts`() {
        val light = Snapshots.capture(gallery(MalletTheme.light()), "statelayout_light", 360)
        val dark = Snapshots.capture(gallery(MalletTheme.dark()), "statelayout_dark", 360)
        assertThat(light.getPixel(4, 4)).isEqualTo(MalletTheme.light().colors.canvas)
        assertThat(dark.getPixel(4, 4)).isEqualTo(MalletTheme.dark().colors.canvas)
    }
}
