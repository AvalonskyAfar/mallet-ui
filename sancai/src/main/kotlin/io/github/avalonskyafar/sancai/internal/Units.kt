package io.github.avalonskyafar.sancai.internal

import android.content.Context
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
