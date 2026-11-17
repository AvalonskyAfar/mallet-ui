package io.github.avalonskyafar.sancai.overlay

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.testing.Snapshots
import io.github.avalonskyafar.sancai.testing.Touch
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import io.github.avalonskyafar.sancai.widget.SancaiText
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SancaiFloatingPanelTest {

    private val context = Snapshots.context(SancaiTheme.light())
    private val density = context.resources.displayMetrics.density

    private fun dp(value: Float): Int = (value * density).roundToInt()

    /** A 300 by 400 dp parent with 10 dp padding, in a window, holding one panel with a caption. */
    private class Scene(val host: FrameLayout, val panel: SancaiFloatingPanel) {
        val moves = ArrayList<Pair<Float, Float>>()
        var clicks = 0
    }

    private fun scene(
        context: Context = this.context,
        rtl: Boolean = false,
        attach: Boolean = true,
    ): Scene {
        val host = FrameLayout(context)
        host.minimumHeight = dp(400f)
        host.setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
        if (rtl) {
            // Layout direction only resolves to RTL for an application that declares support.
            val info = context.applicationContext.applicationInfo
            info.flags = info.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
            host.layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val panel = SancaiFloatingPanel(context)
        panel.id = R.id.sancai_floating_panel
        panel.addView(SancaiText.create(context, "Capturing", SancaiTextRole.CAPTION, SancaiTextTone.INK))
        host.addView(panel, FrameLayout.LayoutParams(-2, -2))
        val scene = Scene(host, panel)
        panel.setOnPositionChangeListener { _, x, y -> scene.moves += x to y }
        if (attach) Snapshots.attach(host, 300) else Snapshots.layout(host, 300, 400)
        return scene
    }

    private fun glide() {
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `padding is twelve by eight around the content plus the shadow gutter`() {
        val s = scene()
        val text = s.panel.getChildAt(0)
        assertThat(s.panel.paddingLeft).isEqualTo((13.4f * density).roundToInt())
        assertThat(s.panel.paddingRight).isEqualTo((13.4f * density).roundToInt())
        assertThat(s.panel.paddingTop).isEqualTo((9f * density).roundToInt())
        assertThat(s.panel.paddingBottom).isEqualTo((12.8f * density).roundToInt())
        assertThat(s.panel.width).isEqualTo(text.width + s.panel.paddingLeft + s.panel.paddingRight)
        // It starts in the top-start corner of the padded parent.
        assertThat(s.panel.x).isEqualTo(dp(10f).toFloat())
        assertThat(s.panel.y).isEqualTo(dp(10f).toFloat())
    }

    @Test
    fun `a drag moves the panel with the finger and reports once on release`() {
        val s = scene()
        Touch.drag(s.panel, 20f, 20f, 20f + dp(100f), 20f + dp(50f), release = false)
        assertThat(s.panel.isDragging).isTrue()
        assertThat(s.panel.translationX).isEqualTo(dp(100f).toFloat())
        assertThat(s.panel.translationY).isEqualTo(dp(50f).toFloat())
        assertThat(s.moves).isEmpty()

        Touch.dispatch(s.panel, MotionEvent.ACTION_UP, 20f + dp(100f), 20f + dp(50f))
        assertThat(s.panel.isDragging).isFalse()
        // It stays where it was released.
        assertThat(s.panel.translationX).isEqualTo(dp(100f).toFloat())
        assertThat(s.moves).hasSize(1)
        val travelX = (s.host.width - 2 * dp(10f) - s.panel.width).toFloat()
        assertThat(s.panel.positionX).isWithin(0.001f).of(dp(100f) / travelX)
        assertThat(s.moves[0].first).isEqualTo(s.panel.positionX)
    }

    @Test
    fun `the panel cannot leave the padded bounds of its parent`() {
        val s = scene()
        Touch.drag(s.panel, 20f, 20f, 5000f, 5000f)
        assertThat(s.panel.positionX).isEqualTo(1f)
        assertThat(s.panel.positionY).isEqualTo(1f)
        assertThat(s.panel.x + s.panel.width).isEqualTo((s.host.width - dp(10f)).toFloat())
        assertThat(s.panel.y + s.panel.height).isEqualTo((s.host.height - dp(10f)).toFloat())

        Touch.drag(s.panel, 20f, 20f, -5000f, -5000f)
        assertThat(s.panel.positionX).isEqualTo(0f)
        assertThat(s.panel.x).isEqualTo(dp(10f).toFloat())
        assertThat(s.panel.y).isEqualTo(dp(10f).toFloat())
    }

    @Test
    fun `a touch inside the slop is a click and not a move`() {
        val s = scene()
        s.panel.setOnClickListener { s.clicks++ }
        Touch.drag(s.panel, 20f, 20f, 23f, 22f, steps = 2)
        assertThat(s.clicks).isEqualTo(1)
        assertThat(s.moves).isEmpty()
        assertThat(s.panel.translationX).isEqualTo(0f)

        Touch.drag(s.panel, 20f, 20f, 20f + dp(60f), 20f)
        assertThat(s.clicks).isEqualTo(1)
        assertThat(s.moves).hasSize(1)
    }

    @Test
    fun `a drag that starts on a child is taken over by the panel`() {
        val s = scene()
        var childClicks = 0
        val child = s.panel.getChildAt(0)
        child.setOnClickListener { childClicks++ }
        val x = child.left + 10f
        val y = child.top + 10f
        Touch.drag(s.panel, x, y, x + dp(80f), y)
        Snapshots.idle()
        assertThat(childClicks).isEqualTo(0)
        assertThat(s.panel.translationX).isEqualTo(dp(80f).toFloat())

        // Touch coordinates are local to the panel, so the child is still under the same point.
        Touch.tap(s.panel, x, y)
        Snapshots.idle()
        assertThat(childClicks).isEqualTo(1)
    }

    @Test
    fun `snap to edge glides to the nearer side and reports the resting place`() {
        val s = scene()
        s.panel.snapToEdge = true
        Touch.drag(s.panel, 20f, 20f, 20f + dp(60f), 20f + dp(100f))
        assertThat(s.moves).hasSize(1)
        assertThat(s.moves[0].first).isEqualTo(0f)
        glide()
        assertThat(s.panel.positionX).isEqualTo(0f)
        assertThat(s.panel.x).isEqualTo(dp(10f).toFloat())
        val y = s.panel.positionY
        assertThat(y).isGreaterThan(0.2f)

        Touch.drag(s.panel, 20f, 20f, 20f + dp(200f), 20f)
        assertThat(s.moves.last()).isEqualTo(1f to y)
        // Mid-glide the panel is on its way; afterwards it rests on the end edge.
        glide()
        assertThat(s.panel.positionX).isEqualTo(1f)
        assertThat(s.panel.positionY).isEqualTo(y)
        assertThat(s.panel.x + s.panel.width).isEqualTo((s.host.width - dp(10f)).toFloat())
    }

    @Test
    fun `reduced motion snaps without a glide`() {
        val s = scene(Snapshots.context(SancaiTheme.light().copy(reduceMotion = true)))
        s.panel.snapToEdge = true
        Touch.drag(s.panel, 20f, 20f, 20f + dp(200f), 20f)
        assertThat(s.panel.positionX).isEqualTo(1f)
    }

    @Test
    fun `setters move the panel without calling the listener`() {
        val s = scene()
        s.panel.setPosition(0.5f, 0.25f)
        assertThat(s.panel.positionX).isEqualTo(0.5f)
        val travelX = s.host.width - 2 * dp(10f) - s.panel.width
        assertThat(s.panel.translationX).isWithin(0.5f).of(travelX * 0.5f)
        s.panel.positionY = 7f
        assertThat(s.panel.positionY).isEqualTo(1f)
        s.panel.setPosition(0f, 0f, animate = true)
        glide()
        assertThat(s.panel.positionX).isEqualTo(0f)
        assertThat(s.moves).isEmpty()
    }

    @Test
    fun `position is a fraction and survives a new parent size and save and restore`() {
        val s = scene()
        s.panel.setPosition(1f, 0.5f)
        val saved = SparseArray<Parcelable>()
        s.host.saveHierarchyState(saved)

        // Same fractions in a wider, shorter parent: still on the end edge, still half way down.
        Snapshots.layout(s.host, 500, 200)
        assertThat(s.panel.x + s.panel.width).isEqualTo((s.host.width - dp(10f)).toFloat())
        assertThat(s.panel.y).isWithin(0.5f).of(dp(10f) + (s.host.height - 2 * dp(10f) - s.panel.height) * 0.5f)

        val fresh = scene()
        fresh.host.restoreHierarchyState(saved)
        assertThat(fresh.panel.positionX).isEqualTo(1f)
        assertThat(fresh.panel.positionY).isEqualTo(0.5f)
        assertThat(fresh.panel.x + fresh.panel.width).isEqualTo((fresh.host.width - dp(10f)).toFloat())
        assertThat(fresh.moves).isEmpty()
    }

    @Test
    fun `right to left mirrors the horizontal axis`() {
        val s = scene(rtl = true)
        // Position 0 is the start edge, which is on the right.
        assertThat(s.panel.x + s.panel.width).isEqualTo((s.host.width - dp(10f)).toFloat())
        val startX = s.panel.x
        Touch.drag(s.panel, 20f, 20f, 20f - dp(50f), 20f)
        assertThat(s.panel.x).isEqualTo(startX - dp(50f))
        assertThat(s.panel.positionX).isGreaterThan(0f)
        s.panel.positionX = 1f
        assertThat(s.panel.x).isEqualTo(dp(10f).toFloat())
    }

    private fun key(panel: SancaiFloatingPanel, code: Int): Boolean =
        panel.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))

    @Test
    fun `arrow keys move in steps and are not consumed at the limit`() {
        val s = scene()
        assertThat(key(s.panel, KeyEvent.KEYCODE_DPAD_LEFT)).isFalse()
        assertThat(key(s.panel, KeyEvent.KEYCODE_DPAD_UP)).isFalse()
        assertThat(s.moves).isEmpty()

        assertThat(key(s.panel, KeyEvent.KEYCODE_DPAD_RIGHT)).isTrue()
        assertThat(key(s.panel, KeyEvent.KEYCODE_DPAD_DOWN)).isTrue()
        assertThat(s.panel.translationX).isEqualTo(dp(16f).toFloat())
        assertThat(s.panel.translationY).isEqualTo(dp(16f).toFloat())
        assertThat(s.moves).hasSize(2)

        // Space and Enter click only when there is something to click.
        assertThat(key(s.panel, KeyEvent.KEYCODE_ENTER)).isFalse()
        s.panel.setOnClickListener { s.clicks++ }
        assertThat(key(s.panel, KeyEvent.KEYCODE_ENTER)).isTrue()
        assertThat(key(s.panel, KeyEvent.KEYCODE_SPACE)).isTrue()
        assertThat(s.clicks).isEqualTo(2)
    }

    @Test
    fun `arrow keys follow the layout direction`() {
        val s = scene(rtl = true)
        assertThat(key(s.panel, KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        val startX = s.panel.x
        assertThat(key(s.panel, KeyEvent.KEYCODE_DPAD_LEFT)).isTrue()
        assertThat(s.panel.x).isEqualTo(startX - dp(16f))
    }

    @Test
    fun `accessibility offers a move to each edge`() {
        val s = scene()
        val node = s.panel.createAccessibilityNodeInfo()
        assertThat(node).isNotNull()
        assertThat(node.className.toString()).isEqualTo(SancaiFloatingPanel::class.java.name)
        val labels = node.actionList.mapNotNull { it.label?.toString() }
        assertThat(labels).containsAtLeast(
            "Move to the start edge", "Move to the end edge", "Move to the top", "Move to the bottom",
        )
        val toEnd = node.actionList.first { it.label?.toString() == "Move to the end edge" }
        assertThat(s.panel.performAccessibilityAction(toEnd.id, null)).isTrue()
        glide()
        assertThat(s.panel.positionX).isEqualTo(1f)
        assertThat(s.moves).containsExactly(1f to 0f)
        // Already there: nothing to do.
        assertThat(s.panel.performAccessibilityAction(toEnd.id, null)).isFalse()
    }

    @Test
    fun `a disabled panel pales and does not move`() {
        val s = scene()
        val enabled = draw(s.panel)
        s.panel.isEnabled = false
        Touch.drag(s.panel, 20f, 20f, 20f + dp(100f), 20f)
        assertThat(key(s.panel, KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
        assertThat(s.panel.translationX).isEqualTo(0f)
        assertThat(s.moves).isEmpty()
        assertThat(draw(s.panel).sameAs(enabled)).isFalse()
        assertThat(s.panel.alpha).isEqualTo(1f)
    }

    private fun draw(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(SancaiTheme.of(view.context).colors.canvas)
        view.draw(canvas)
        return bitmap
    }

    @Test
    fun `the body is porcelain and the shadow tightens while dragged`() {
        val dark = SancaiTheme.dark()
        val s = scene(Snapshots.context(dark))
        val rest = draw(s.panel)
        // Left of the text, half way down: the porcelain body. The corner is outside the rounding.
        assertNear(rest.getPixel(dp(6f), s.panel.height / 2), dark.colors.surface)
        assertThat(rest.getPixel(0, 0)).isEqualTo(dark.colors.canvas)

        Touch.drag(s.panel, 20f, 20f, 20f + dp(40f), 20f, release = false)
        val dragged = draw(s.panel)
        assertThat(dragged.sameAs(rest)).isFalse()
        assertNear(dragged.getPixel(dp(6f), s.panel.height / 2), dark.colors.surface)

        Touch.dispatch(s.panel, MotionEvent.ACTION_CANCEL, 20f, 20f)
        assertThat(s.panel.isDragging).isFalse()
        assertThat(draw(s.panel).sameAs(rest)).isTrue()
        assertThat(s.moves).isEmpty()
    }

    /** The porcelain body carries a faint inner shading: within two steps per channel. */
    private fun assertNear(actual: Int, expected: Int) {
        for (shift in intArrayOf(24, 16, 8, 0)) {
            assertThat((actual ushr shift) and 0xFF).isIn(
                com.google.common.collect.Range.closed(((expected ushr shift) and 0xFF) - 2, ((expected ushr shift) and 0xFF) + 2),
            )
        }
    }

    @Test
    fun `attach adds a panel to the activity and remove takes it away`() {
        val activity = Snapshots.attach(View(context))
        val content = SancaiText.create(context, "Capturing", SancaiTextRole.CAPTION, SancaiTextTone.INK)
        val panel = SancaiFloatingPanel.attach(activity as Activity, content)
        Snapshots.idle()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        assertThat(panel.parent).isSameInstanceAs(root)
        assertThat(panel.getChildAt(0)).isSameInstanceAs(content)
        assertThat(panel.id).isEqualTo(R.id.sancai_floating_panel)
        assertThat(panel.width).isLessThan(root.width)
        assertThat(panel.isBoundToWindow).isFalse()

        panel.remove()
        assertThat(panel.parent).isNull()
    }

    @Test
    fun `bound to a window a drag moves the window and not the view`() {
        val params = SancaiFloatingPanel.overlayLayoutParams()
        assertThat(params.type).isEqualTo(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        assertThat(params.width).isEqualTo(WindowManager.LayoutParams.WRAP_CONTENT)
        assertThat(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).isNotEqualTo(0)
        assertThat(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE).isEqualTo(0)
        assertThat(SancaiFloatingPanel.overlayLayoutParams(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY).type)
            .isEqualTo(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)

        val panel = SancaiFloatingPanel(context)
        panel.addView(SancaiText.create(context, "Capturing", SancaiTextRole.CAPTION, SancaiTextTone.INK))
        val moves = ArrayList<Pair<Float, Float>>()
        panel.setOnPositionChangeListener { _, x, y -> moves += x to y }
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        panel.bindToWindow(windowManager, params)
        Snapshots.layout(panel, 120)
        assertThat(panel.isBoundToWindow).isTrue()

        Touch.drag(panel, 20f, 20f, 70f, 60f)
        assertThat(params.x).isEqualTo(50)
        assertThat(params.y).isEqualTo(40)
        assertThat(panel.translationX).isEqualTo(0f)
        assertThat(moves).hasSize(1)

        // The window stays on the screen.
        val metrics = context.resources.displayMetrics
        Touch.drag(panel, 20f, 20f, 9000f, 9000f)
        assertThat(params.x).isEqualTo(metrics.widthPixels - panel.width)
        assertThat(params.y).isEqualTo(metrics.heightPixels - panel.height)
        panel.remove() // never added: must not throw
    }

    @Test
    fun `xml attributes set the start position and snapping`() {
        val attrs = org.robolectric.Robolectric.buildAttributeSet()
            .addAttribute(R.attr.sancaiFloatingPanelPositionX, "1")
            .addAttribute(R.attr.sancaiFloatingPanelPositionY, "0.25")
            .addAttribute(R.attr.sancaiFloatingPanelSnapToEdge, "true")
            .build()
        val panel = SancaiFloatingPanel(context, attrs)
        assertThat(panel.positionX).isEqualTo(1f)
        assertThat(panel.positionY).isEqualTo(0.25f)
        assertThat(panel.snapToEdge).isTrue()
    }

    @Test
    fun `an empty panel and a long text at large type stay inside the parent`() {
        val large = Snapshots.context(SancaiTheme.light().copy(textScale = 1.13f))
        val host = FrameLayout(large)
        host.setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
        val empty = SancaiFloatingPanel(large)
        val long = SancaiFloatingPanel(large)
        val text = label(large, "Capturing notifications from forty-two applications and counting, strict mode")
        long.addView(text)
        long.setPosition(1f, 1f)
        host.addView(empty, FrameLayout.LayoutParams(-2, -2))
        host.addView(long, FrameLayout.LayoutParams(-2, -2))
        Snapshots.layout(host, 200, 300)

        // Empty: only the padding, and it still drags without dividing by zero.
        assertThat(empty.width).isEqualTo(empty.paddingLeft + empty.paddingRight)
        Touch.drag(empty, 5f, 5f, 5f + dp(30f), 5f + dp(30f))
        assertThat(empty.translationX).isEqualTo(dp(30f).toFloat())

        // Long: wraps instead of leaving the padded bounds, and rests in the bottom-end corner.
        assertThat(long.width).isAtMost(host.width - 2 * dp(10f))
        assertThat(text.lineCount).isGreaterThan(1)
        assertThat(long.x).isAtLeast(dp(10f).toFloat())
        assertThat(long.x + long.width).isEqualTo((host.width - dp(10f)).toFloat())
        assertThat(long.y + long.height).isEqualTo((host.height - dp(10f)).toFloat())
        Touch.drag(long, 20f, 20f, 20f + dp(60f), 20f - dp(40f))
        assertThat(long.x + long.width).isEqualTo((host.width - dp(10f)).toFloat())
        assertThat(long.positionY).isLessThan(1f)
        // Large type makes the caption taller than the default one.
        val plain = scene()
        assertThat(text.textSize).isGreaterThan((plain.panel.getChildAt(0) as SancaiText).textSize)
    }

    private fun twoFingers(action: Int, x0: Float, y0: Float, x1: Float, y1: Float): MotionEvent {
        val props = Array(2) { MotionEvent.PointerProperties().apply { id = it } }
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x = x0; y = y0 },
            MotionEvent.PointerCoords().apply { x = x1; y = y1 },
        )
        return MotionEvent.obtain(0L, 0L, action, 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    @Test
    fun `a second finger takes over without a jump when the first lifts`() {
        val s = scene()
        Touch.drag(s.panel, 20f, 20f, 20f + dp(40f), 20f, release = false)
        assertThat(s.panel.translationX).isEqualTo(dp(40f).toFloat())
        val x0 = 20f + dp(40f)
        s.panel.dispatchTouchEvent(
            twoFingers(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), x0, 20f, 300f, 60f),
        )
        s.panel.dispatchTouchEvent(twoFingers(MotionEvent.ACTION_POINTER_UP, x0, 20f, 300f, 60f))
        assertThat(s.panel.translationX).isEqualTo(dp(40f).toFloat())
        assertThat(s.panel.isDragging).isTrue()

        // The remaining finger (pointer id 1, now alone) moves the panel from where it is.
        s.panel.dispatchTouchEvent(secondFinger(MotionEvent.ACTION_MOVE, 300f + dp(10f), 60f + dp(20f)))
        assertThat(s.panel.translationX).isEqualTo(dp(50f).toFloat())
        assertThat(s.panel.translationY).isEqualTo(dp(20f).toFloat())
        s.panel.dispatchTouchEvent(secondFinger(MotionEvent.ACTION_UP, 300f + dp(10f), 60f + dp(20f)))
        assertThat(s.panel.isDragging).isFalse()
        assertThat(s.moves).hasSize(1)
    }

    @Test
    fun `bound to a window right to left keeps the start edge on the right and follows the finger`() {
        val info = context.applicationContext.applicationInfo
        info.flags = info.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val params = SancaiFloatingPanel.overlayLayoutParams()
        // x is measured from the left whatever the layout direction.
        assertThat(params.gravity).isEqualTo(android.view.Gravity.TOP or android.view.Gravity.LEFT)
        val panel = SancaiFloatingPanel(context)
        panel.layoutDirection = View.LAYOUT_DIRECTION_RTL
        panel.addView(SancaiText.create(context, "Capturing", SancaiTextRole.CAPTION, SancaiTextTone.INK))
        panel.bindToWindow(context.getSystemService(Context.WINDOW_SERVICE) as WindowManager, params)
        Snapshots.layout(panel, 120)
        val travel = context.resources.displayMetrics.widthPixels - panel.width

        assertThat(params.x).isEqualTo(travel)
        Touch.drag(panel, 200f, 20f, 50f, 20f)
        assertThat(params.x).isEqualTo(travel - 150)
        assertThat(panel.positionX).isWithin(0.001f).of(150f / travel)
        Touch.drag(panel, 20f, 20f, 80f, 20f)
        assertThat(params.x).isEqualTo(travel - 90)
        panel.positionX = 1f
        assertThat(params.x).isEqualTo(0)
    }

    @Test
    fun `a touch during the snap glide does not strand the panel`() {
        val s = scene()
        s.panel.snapToEdge = true
        Touch.drag(s.panel, 20f, 20f, 20f + dp(60f), 20f)
        assertThat(s.moves).containsExactly(0f to s.panel.positionY)

        // A tap stops the glide under the finger; it goes on when the finger lifts.
        Touch.dispatch(s.panel, MotionEvent.ACTION_DOWN, 20f, 20f)
        val held = s.panel.positionX
        assertThat(held).isGreaterThan(0f)
        ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
        assertThat(s.panel.positionX).isEqualTo(held)
        Touch.dispatch(s.panel, MotionEvent.ACTION_UP, 20f, 20f)
        glide()
        assertThat(s.panel.positionX).isEqualTo(0f)
        assertThat(s.panel.x).isEqualTo(dp(10f).toFloat())
        assertThat(s.moves).hasSize(1)
    }

    @Test
    fun `a cancelled drag is undone and tells nobody`() {
        val s = scene()
        s.panel.setPosition(0.25f, 0.5f)
        Touch.drag(s.panel, 20f, 20f, 20f + dp(100f), 20f + dp(50f), release = false)
        assertThat(s.panel.positionX).isGreaterThan(0.25f)
        Touch.dispatch(s.panel, MotionEvent.ACTION_CANCEL, 20f, 20f)
        assertThat(s.panel.isDragging).isFalse()
        assertThat(s.panel.positionX).isEqualTo(0.25f)
        assertThat(s.panel.positionY).isEqualTo(0.5f)

        // Disabled under the finger, with snapping on: back as well, not to an edge.
        s.panel.snapToEdge = true
        Touch.drag(s.panel, 20f, 20f, 20f + dp(150f), 20f - dp(50f), release = false)
        assertThat(s.panel.positionX).isGreaterThan(0.5f)
        s.panel.isEnabled = false
        glide()
        assertThat(s.panel.positionX).isEqualTo(0.25f)
        assertThat(s.panel.positionY).isEqualTo(0.5f)
        assertThat(s.moves).isEmpty()
    }

    @Test
    fun `state saved under the finger is where the panel will rest`() {
        val s = scene()
        s.panel.setPosition(0.25f, 0.5f)
        Touch.drag(s.panel, 20f, 20f, 20f + dp(100f), 20f + dp(50f), release = false)
        val dragged = SparseArray<Parcelable>().also { s.host.saveHierarchyState(it) }
        val fresh = scene()
        fresh.host.restoreHierarchyState(dragged)
        assertThat(fresh.panel.positionX).isEqualTo(0.25f)
        assertThat(fresh.panel.positionY).isEqualTo(0.5f)

        // Released with snapping on: the edge the listener was told, not a point of the glide.
        s.panel.snapToEdge = true
        Touch.dispatch(s.panel, MotionEvent.ACTION_UP, 20f + dp(100f), 20f + dp(50f))
        val side = s.moves.single().first
        assertThat(s.panel.positionX).isNotEqualTo(side)
        val gliding = SparseArray<Parcelable>().also { s.host.saveHierarchyState(it) }
        val other = scene()
        other.host.restoreHierarchyState(gliding)
        assertThat(other.panel.positionX).isEqualTo(side)
        glide()
        assertThat(s.panel.positionX).isEqualTo(side)
    }

    @Test
    fun `the focus rim is the only focus highlight`() {
        val panel = SancaiFloatingPanel(context)
        assertThat(panel.isFocusable).isTrue()
        if (android.os.Build.VERSION.SDK_INT >= 26) assertThat(panel.defaultFocusHighlightEnabled).isFalse()
    }

    private fun secondFinger(action: Int, x: Float, y: Float): MotionEvent {
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 1 })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y })
        return MotionEvent.obtain(0L, 0L, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun label(context: Context, text: String, tone: SancaiTextTone = SancaiTextTone.INK): SancaiText =
        SancaiText.create(context, text, SancaiTextRole.CAPTION, tone)

    private fun gallery(theme: SancaiTheme): View {
        val themed = Snapshots.context(theme)
        val host = FrameLayout(themed)
        host.setBackgroundColor(theme.colors.canvas)
        host.setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
        fun panel(text: String, x: Float, y: Float, tone: SancaiTextTone = SancaiTextTone.INK): SancaiFloatingPanel {
            val panel = SancaiFloatingPanel(themed)
            panel.addView(label(themed, text, tone))
            panel.setPosition(x, y)
            host.addView(panel, FrameLayout.LayoutParams(-2, -2))
            return panel
        }
        panel("Capturing · 3 rules", 0f, 0f)
        panel("Paused", 1f, 0f, SancaiTextTone.MUTED)
        val dragged = panel("Dragging", 0.5f, 0.5f)
        panel("Disabled", 0f, 1f).isEnabled = false
        panel("Strict · 12 blocked", 1f, 1f, SancaiTextTone.ACCENT)
        Snapshots.layout(host, 320, 190)
        Touch.drag(dragged, 20f, 20f, 20f + dp(14f), 20f + dp(6f), release = false)
        return host
    }

    @Test
    fun `snapshot of floating panels`() {
        val light = Snapshots.capture(gallery(SancaiTheme.light()), "panel_light", 320, 190)
        val dark = Snapshots.capture(gallery(SancaiTheme.dark()), "panel_dark", 320, 190)
        assertThat(light.width).isEqualTo(dp(320f))
        assertThat(dark.sameAs(light)).isFalse()
    }
}
