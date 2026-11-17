package io.github.avalonskyafar.sancai.interaction

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import io.github.avalonskyafar.sancai.theme.SancaiMotion

/**
 * The Sancai press: what is touched sinks by 1.5 dp and springs straight back.
 *
 * Only the content that is actually operated moves. The card or page around it stays still,
 * and nothing changes colour. The sink starts on touch-down without the usual tap delay, and
 * the content is back in place before the click is delivered, so a page that starts moving in
 * response never carries a half-pressed row with it.
 */
public object SancaiPress {

    /**
     * Makes [host] pressable. Touching it sinks [content] (by default the host itself);
     * releasing inside it ticks and then calls [onClick].
     *
     * The tick comes first on purpose: an action may start something slow (a new page, a
     * dialog), and the confirmation must not wait for it.
     *
     * This installs a touch listener and a click listener on [host]. Use [attachSink] to keep
     * the click listener for yourself.
     */
    @JvmStatic
    @JvmOverloads
    public fun attach(host: View, content: View = host, onClick: View.OnClickListener? = null) {
        attachSink(host, content)
        host.setOnClickListener { view ->
            SancaiHaptics.tick(view)
            onClick?.onClick(view)
        }
    }

    /**
     * The sink alone: touching [host] sinks [content], releasing puts it back. No click
     * listener is installed and nothing ticks.
     */
    @JvmStatic
    @JvmOverloads
    @SuppressLint("ClickableViewAccessibility")
    public fun attachSink(host: View, content: View = host) {
        val sink = content.resources.displayMetrics.density * SancaiMotion.PRESS_SINK_DP
        host.isClickable = true
        host.isFocusable = true
        host.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (view.isEnabled) sink(content, sink)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release(content)
            }
            false
        }
    }

    /** Starts sinking [content] by [distancePx]. */
    @JvmStatic
    public fun sink(content: View, distancePx: Float) {
        content.animate().cancel()
        content.animate().translationY(distancePx).setDuration(SancaiMotion.PRESS_MS).start()
    }

    /** Puts [content] back at once. */
    @JvmStatic
    public fun release(content: View) {
        content.animate().cancel()
        content.translationY = 0f
    }
}
