package io.github.avalonskyafar.mallet.interaction

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import io.github.avalonskyafar.mallet.theme.MalletTheme

/**
 * The haptic vocabulary of Mallet. Every call is a no-op when [MalletTheme.haptics] is off.
 * Each effect falls back to the nearest one the running Android version offers; [detent] is
 * silent below Android 8.1.
 *
 * | Effect | Meaning |
 * |---|---|
 * | [tick] | a state change was committed |
 * | [detent] | the finger crossed into another option while dragging |
 * | [confirm] | a consequential action succeeded |
 * | [reject] | an action was refused or failed validation |
 * | [longPress] | a long press was recognised |
 */
public object MalletHaptics {

    @JvmStatic
    public fun tick(view: View): Unit = perform(view, HapticFeedbackConstants.CLOCK_TICK)

    @JvmStatic
    public fun detent(view: View) {
        val effect = when {
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            Build.VERSION.SDK_INT >= 27 -> HapticFeedbackConstants.TEXT_HANDLE_MOVE
            else -> return
        }
        perform(view, effect)
    }

    @JvmStatic
    public fun confirm(view: View) {
        val effect = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        perform(view, effect)
    }

    @JvmStatic
    public fun reject(view: View) {
        val effect = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        perform(view, effect)
    }

    @JvmStatic
    public fun longPress(view: View): Unit = perform(view, HapticFeedbackConstants.LONG_PRESS)

    private fun perform(view: View, effect: Int) {
        if (!MalletTheme.of(view.context).haptics) return
        view.performHapticFeedback(effect)
    }
}
