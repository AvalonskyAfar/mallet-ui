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
     * releasing inside it ticks and calls [onClick].
     *
     * This installs a touch listener and a click listener on [host].
     */
    @JvmStatic
    @JvmOverloads
    @SuppressLint("ClickableViewAccessibility")
    public fun attach(host: View, content: View = host, onClick: View.OnClickListener? = null) {
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
        host.setOnClickListener { view ->
            SancaiHaptics.tick(view)
            onClick?.onClick(view)
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
