package io.github.avalonskyafar.sancai.internal

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import kotlin.math.roundToInt

internal fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

internal fun Context.dpInt(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun Context.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

internal fun View.dp(value: Float): Float = context.dp(value)

internal fun View.dpInt(value: Float): Int = context.dpInt(value)

internal fun View.sp(value: Float): Float = context.sp(value)

/**
 * For a view that draws its own focus rim: switches off the highlight the platform lays over
 * a focused view from Android 8 on. Call it once, when the view is created.
 */
internal fun View.useOwnFocusRim() {
    if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
}
