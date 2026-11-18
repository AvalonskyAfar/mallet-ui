package io.github.avalonskyafar.mallet.interaction

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import io.github.avalonskyafar.mallet.theme.MalletMotion

/**
 * The Mallet press: what is touched sinks by 1.5 dp and springs straight back.
 *
 * Only the content that is actually operated moves. The card or page around it stays still,
 * and nothing changes colour. The sink starts on touch-down without the usual tap delay, and
 * the content is back in place before the click is delivered, so a page that starts moving in
 * response never carries a half-pressed row with it.
 */
public object MalletPress {

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
            MalletHaptics.tick(view)
            onClick?.onClick(view)
        }
    }

    /**
     * The sink alone: touching [host] sinks [content], releasing puts it back. No click
     * listener is installed and nothing ticks. The content also comes back when the host is
     * disabled or leaves its window while the finger is still down.
     */
    @JvmStatic
    @JvmOverloads
    @SuppressLint("ClickableViewAccessibility")
    public fun attachSink(host: View, content: View = host) {
        val distance = content.resources.displayMetrics.density * MalletMotion.PRESS_SINK_DP
        host.isClickable = true
        host.isFocusable = true
        host.setOnTouchListener(Sink(host, content, distance))
    }

    /** Starts sinking [content] by [distancePx]. */
    @JvmStatic
    public fun sink(content: View, distancePx: Float) {
        content.animate().cancel()
        content.animate().translationY(distancePx).setDuration(MalletMotion.PRESS_MS).start()
    }

    /** Puts [content] back at once. */
    @JvmStatic
    public fun release(content: View) {
        content.animate().cancel()
        content.translationY = 0f
    }

    private class Sink(
        private val host: View,
        private val content: View,
        private val distance: Float,
    ) : View.OnTouchListener, ViewTreeObserver.OnPreDrawListener {

        /** The observer this is registered with while a finger is down. */
        private var watched: ViewTreeObserver? = null

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (view.isEnabled) {
                    sink(content, distance)
                    watch()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    release(content)
                    unwatch()
                }
            }
            return false
        }

        // A disabled view is no longer told that the finger lifted, and a detached one is
        // told nothing at all: look before each frame while the content is down.
        override fun onPreDraw(): Boolean {
            if (!host.isEnabled || !host.isAttachedToWindow) {
                release(content)
                unwatch()
            }
            return true
        }

        private fun watch() {
            unwatch()
            val observer = host.viewTreeObserver
            if (!observer.isAlive) return
            observer.addOnPreDrawListener(this)
            watched = observer
        }

        private fun unwatch() {
            val observer = watched ?: return
            watched = null
            if (observer.isAlive) observer.removeOnPreDrawListener(this)
        }
    }
}
