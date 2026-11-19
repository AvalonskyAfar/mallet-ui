package io.github.avalonskyafar.mallet.material

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.ColorInt
import io.github.avalonskyafar.mallet.theme.MalletColors
import io.github.avalonskyafar.mallet.theme.MalletTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Which corners of a surface are rounded. [TOP] and [BOTTOM] are the two halves of a split card. */
public enum class MalletCorners { ALL, TOP, BOTTOM }

/**
 * The Mallet material engine: every shadow, rim and recess in the toolkit is painted here.
 *
 * Four materials make up the language.
 *
 * - **Raised surface** ([drawSurface]): a calm body with one hairline rim, one short soft
 *   shadow below it and a trace of inner shading. Cards, capsule buttons, dialogs and toasts.
 * - **Track** ([drawTrack]): a recessed capsule whose bed colour carries the state.
 * - **Thumb** ([drawThumb]): a neutral raised bead that rides inside a track.
 * - **Well** ([drawWell]): a recessed neutral hollow for text and pictures.
 *
 * Android clips a view's drawing to its bounds, so every outer shadow is drawn inside a small
 * gutter that the caller reserves around the material; [drawOuterShadow] measures the room it
 * really has and fades to nothing before the clip edge.
 *
 * Instances keep scratch objects and must only be used on the UI thread.
 */
public class MalletMaterial @JvmOverloads constructor(
    context: Context,
    public val theme: MalletTheme = MalletTheme.of(context),
) {
    private val density: Float = context.resources.displayMetrics.density
    private val colors: MalletColors = theme.colors
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scratch = RectF()
    private val path = Path()
    private val clip = Rect()
    private val radii = FloatArray(8)

    /** Converts density-independent pixels to pixels. */
    public fun dp(value: Float): Float = value * density

    // ---------------------------------------------------------------------------------------
    // Primitives
    // ---------------------------------------------------------------------------------------

    /**
     * A soft shadow below [bounds], built from low-alpha shells so that it reaches zero inside
     * whatever gutter the current clip leaves. The numbers mirror a CSS
     * `box-shadow: 0 offset blur rgba(ink, opacity/255)`.
     */
    public fun drawOuterShadow(
        canvas: Canvas,
        bounds: RectF,
        radius: Float,
        blurDp: Float,
        offsetDp: Float,
        opacity: Int,
    ) {
        if (!canvas.getClipBounds(clip)) return
        val sideRoom = min(max(0f, bounds.left - clip.left), max(0f, clip.right - bounds.right))
        val sideReach = min(sideRoom, dp(blurDp * 0.48f))
        val topReach = min(max(0f, bounds.top - clip.top), dp(max(0f, blurDp - offsetDp) * 0.22f))
        val bottomReach = min(max(0f, clip.bottom - bounds.bottom), dp(offsetDp + blurDp * 0.72f))
        if (sideReach <= 0.1f && topReach <= 0.1f && bottomReach <= 0.1f) return

        resetPaint()
        // Slightly below the CSS alpha: the native gutter is narrower than a free browser blur.
        val layerAlpha = max(1, (opacity * 0.76f / SHADOW_LAYERS).roundToInt())
        paint.color = MalletColors.withAlpha(colors.ink, layerAlpha)
        for (i in 0 until SHADOW_LAYERS) {
            val factor = 1f - i.toFloat() / SHADOW_LAYERS
            scratch.set(
                bounds.left - sideReach * factor,
                bounds.top - topReach * factor,
                bounds.right + sideReach * factor,
                bounds.bottom + bottomReach * factor,
            )
            val r = radius + sideReach * factor * 0.38f
            canvas.drawRoundRect(scratch, r, r, paint)
        }
    }

    /**
     * The standard lift of a raised or recessed element. [compact] selects the darker shadow
     * used by tracks (deeper in light themes, tighter in dark ones); otherwise the fainter
     * shadow of cards.
     */
    public fun drawLift(canvas: Canvas, bounds: RectF, radius: Float, pressed: Boolean, compact: Boolean) {
        if (!theme.isDark) {
            if (pressed) {
                drawOuterShadow(
                    canvas, bounds, radius,
                    blurDp = if (compact) 5.3f else 4.6f,
                    offsetDp = if (compact) 2.5f else 1.8f,
                    opacity = if (compact) 28 else 20,
                )
            } else {
                drawOuterShadow(
                    canvas, bounds, radius,
                    blurDp = if (compact) 12.8f else 8f,
                    offsetDp = if (compact) 6.08f else 3.52f,
                    opacity = if (compact) 31 else 23,
                )
            }
            return
        }
        val scale = if (pressed) 0.52f else 1f
        val spread = if (compact) DARK_SPREAD_COMPACT else DARK_SPREAD_WIDE
        val opacity = if (compact) DARK_OPACITY_COMPACT else DARK_OPACITY_WIDE
        resetPaint()
        for (i in spread.indices.reversed()) {
            val amount = dp(spread[i]) * scale
            scratch.set(bounds)
            scratch.inset(-amount * 0.42f, -amount * 0.18f)
            scratch.offset(0f, amount * 0.56f)
            paint.color = Color.argb((opacity[i] * scale).roundToInt(), 0, 0, 0)
            val r = radius + amount * 0.32f
            canvas.drawRoundRect(scratch, r, r, paint)
        }
    }

    /**
     * Two short inset shades, one falling from the top edge and one rising from the bottom
     * edge, clipped to [clipTo]. Depths are absolute (offset + two blur radii), never a share
     * of the element height.
     */
    private fun drawInnerDepth(
        canvas: Canvas,
        bounds: RectF,
        clipTo: Path,
        topOffsetDp: Float,
        topBlurDp: Float,
        topAlpha: Int,
        bottomOffsetDp: Float,
        bottomBlurDp: Float,
        bottomAlpha: Int,
        top: Boolean = true,
        bottom: Boolean = true,
    ) {
        val save = canvas.save()
        canvas.clipPath(clipTo)
        resetPaint()
        val ink = colors.ink
        val topDepth = min(bounds.height() / 2f, dp(topOffsetDp + topBlurDp * 2f))
        if (top && topAlpha > 0 && topDepth > 0f) {
            paint.shader = LinearGradient(
                0f, bounds.top, 0f, bounds.top + topDepth,
                intArrayOf(
                    MalletColors.withAlpha(ink, topAlpha),
                    MalletColors.withAlpha(ink, (topAlpha * 0.36f).roundToInt()),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, min(0.72f, dp(topOffsetDp) / topDepth), 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.top + topDepth, paint)
        }
        val bottomDepth = min(bounds.height() / 2f, dp(bottomOffsetDp + bottomBlurDp * 2f))
        if (bottom && bottomAlpha > 0 && bottomDepth > 0f) {
            paint.shader = LinearGradient(
                0f, bounds.bottom - bottomDepth, 0f, bounds.bottom,
                intArrayOf(
                    Color.TRANSPARENT,
                    MalletColors.withAlpha(ink, (bottomAlpha * 0.36f).roundToInt()),
                    MalletColors.withAlpha(ink, bottomAlpha),
                ),
                floatArrayOf(0f, max(0.28f, 1f - dp(bottomOffsetDp) / bottomDepth), 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(bounds.left, bounds.bottom - bottomDepth, bounds.right, bounds.bottom, paint)
        }
        paint.shader = null
        canvas.restoreToCount(save)
    }

    // ---------------------------------------------------------------------------------------
    // Track and thumb
    // ---------------------------------------------------------------------------------------

    /**
     * A recessed capsule filled with [bed] and rimmed with [edge]. The bed colour is painted
     * as given; depth is layered on top of it, never mixed into it. [hero] gives the deeper
     * shadow of the large power switch. Pass `lifted = false` to repaint part of a track that
     * already has its shadow, for example the coloured stretch of a slider.
     */
    @JvmOverloads
    public fun drawTrack(
        canvas: Canvas,
        bounds: RectF,
        @ColorInt bed: Int,
        @ColorInt edge: Int,
        pressed: Boolean = false,
        hero: Boolean = false,
        radius: Float = bounds.height() / 2f,
        lifted: Boolean = true,
    ) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        if (!lifted) {
            // The shadow is already there.
        } else if (!theme.isDark && hero) {
            drawOuterShadow(
                canvas, bounds, radius,
                blurDp = if (pressed) 9.92f else 17.6f,
                offsetDp = if (pressed) 4.48f else 8.8f,
                opacity = if (pressed) 33 else 41,
            )
        } else {
            drawLift(canvas, bounds, radius, pressed, compact = true)
        }
        resetPaint()
        paint.color = bed
        canvas.drawRoundRect(bounds, radius, radius, paint)

        path.rewind()
        path.addRoundRect(bounds, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(path)
        val dark = theme.isDark
        val topDepth = min(bounds.height() / 2f, dp(3.2f + 5.12f * 2f))
        paint.shader = LinearGradient(
            0f, bounds.top, 0f, bounds.top + topDepth,
            intArrayOf(
                Color.argb(if (dark) 70 else 41, 0, 0, 0),
                Color.argb(if (dark) 30 else 15, 0, 0, 0),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, min(0.72f, dp(3.2f) / topDepth), 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.top + topDepth, paint)
        val bottomDepth = min(bounds.height() / 2f, dp(2.4f + 4f * 2f))
        paint.shader = LinearGradient(
            0f, bounds.bottom - bottomDepth, 0f, bounds.bottom,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(if (dark) 18 else 14, 255, 255, 255),
                Color.argb(if (dark) 38 else 87, 255, 255, 255),
            ),
            floatArrayOf(0f, max(0.28f, 1f - dp(2.4f) / bottomDepth), 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(bounds.left, bounds.bottom - bottomDepth, bounds.right, bounds.bottom, paint)
        paint.shader = null
        canvas.restoreToCount(save)

        scratch.set(bounds)
        scratch.inset(dp(0.5f), dp(0.5f))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = edge
        val rim = max(0f, radius - dp(0.5f))
        canvas.drawRoundRect(scratch, rim, rim, paint)
        paint.style = Paint.Style.FILL
    }

    /** The neutral raised bead that rides inside a track. Its rim is tonal, never a hard line. */
    @JvmOverloads
    public fun drawThumb(
        canvas: Canvas,
        bounds: RectF,
        pressed: Boolean = false,
        radius: Float = bounds.height() / 2f,
        lifted: Boolean = true,
    ) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        val dark = theme.isDark
        if (lifted) {
            if (dark) {
                drawLift(canvas, bounds, radius, pressed, compact = true)
            } else {
                drawOuterShadow(
                    canvas, bounds, radius,
                    blurDp = if (pressed) 2.72f else 5.12f,
                    offsetDp = if (pressed) 1.92f else 4.48f,
                    opacity = if (pressed) 48 else 59,
                )
            }
        }
        resetPaint()
        if (dark) {
            paint.shader = LinearGradient(
                0f, bounds.top, 0f, bounds.bottom,
                intArrayOf(
                    MalletColors.blend(colors.raised, colors.ink, 0.055f),
                    colors.raised,
                    MalletColors.blend(colors.raised, colors.canvas, 0.19f),
                ),
                floatArrayOf(0f, 0.56f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            paint.color = colors.raised
        }
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = null
        if (!dark) {
            path.rewind()
            path.addRoundRect(bounds, radius, radius, Path.Direction.CW)
            drawInnerDepth(canvas, bounds, path, 2.56f, 1.92f, 20, 1.92f, 2.88f, 26)
        }

        scratch.set(bounds)
        scratch.inset(dp(0.45f), dp(0.45f))
        resetPaint()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.9f)
        if (dark) {
            paint.shader = LinearGradient(
                0f, scratch.top, 0f, scratch.bottom,
                intArrayOf(
                    Color.argb(105, 255, 255, 255),
                    MalletColors.withAlpha(colors.hairline, 142),
                    Color.argb(88, 30, 39, 50),
                ),
                floatArrayOf(0f, 0.60f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            paint.color = MalletColors.blend(colors.canvas, colors.ink, 0.24f)
        }
        val rim = max(0f, radius - dp(0.45f))
        canvas.drawRoundRect(scratch, rim, rim, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
    }

    // ---------------------------------------------------------------------------------------
    // Surface
    // ---------------------------------------------------------------------------------------

    /**
     * A raised surface.
     *
     * @param bounds the material itself; leave a gutter around it for the shadow
     * @param radius corner radius in pixels
     * @param tint `null` for a neutral surface, or a colour for a coloured body such as a
     * destructive or primary capsule
     * @param shadow whether to draw the outer lift
     * @param pressed tighter shadow of a pressed body
     * @param corners rounded corners; a split card pairs [MalletCorners.TOP] with
     * [MalletCorners.BOTTOM], and only the bottom half carries the shadow
     */
    @JvmOverloads
    public fun drawSurface(
        canvas: Canvas,
        bounds: RectF,
        radius: Float,
        @ColorInt tint: Int? = null,
        shadow: Boolean = true,
        pressed: Boolean = false,
        corners: MalletCorners = MalletCorners.ALL,
    ) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        val dark = theme.isDark
        val tinted = tint != null
        val fill = tint ?: colors.surface
        if (shadow && corners != MalletCorners.TOP) drawLift(canvas, bounds, radius, pressed, compact = false)

        shape(path, bounds, radius, corners)
        resetPaint()
        if (dark || tinted) {
            val top = if (tinted) {
                MalletColors.blend(fill, Color.WHITE, if (dark) 0.055f else 0.10f)
            } else {
                MalletColors.blend(fill, colors.ink, 0.028f)
            }
            val bottom = if (tinted) {
                MalletColors.blend(fill, Color.BLACK, if (dark) 0.10f else 0.055f)
            } else {
                MalletColors.blend(fill, colors.canvas, 0.15f)
            }
            // The two halves of a split card must meet in the same tone.
            paint.shader = LinearGradient(
                0f, bounds.top, 0f, bounds.bottom,
                intArrayOf(
                    if (corners == MalletCorners.BOTTOM) fill else top,
                    fill,
                    if (corners == MalletCorners.TOP) fill else bottom,
                ),
                floatArrayOf(0f, 0.56f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            paint.color = fill
        }
        canvas.drawPath(path, paint)
        paint.shader = null
        if (!dark && !tinted) {
            drawInnerDepth(
                canvas, bounds, path, 1.6f, 1.6f, 10, 1.28f, 2.24f, 13,
                top = corners != MalletCorners.BOTTOM,
                bottom = corners != MalletCorners.TOP,
            )
        }

        scratch.set(bounds)
        scratch.inset(dp(0.45f), dp(0.45f))
        shape(path, scratch, max(0f, radius - dp(0.45f)), corners)
        resetPaint()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.85f)
        if (dark || tinted) {
            val edgeBase = if (tinted) {
                MalletColors.blend(fill, colors.ink, 0.20f)
            } else {
                MalletColors.blend(colors.canvas, colors.ink, 0.082f)
            }
            paint.shader = LinearGradient(
                0f, scratch.top, 0f, scratch.bottom,
                intArrayOf(
                    Color.argb(if (dark) 66 else 172, 255, 255, 255),
                    MalletColors.withAlpha(edgeBase, if (dark) 142 else 126),
                    Color.argb(if (dark) 78 else 48, 30, 39, 50),
                ),
                floatArrayOf(0f, 0.60f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            paint.color = colors.hairline
        }
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL

        // The divider between the two halves of a split card owns the joining edge.
        if (corners != MalletCorners.ALL) {
            paint.color = fill
            if (corners == MalletCorners.TOP) {
                canvas.drawRect(
                    bounds.left + dp(0.9f), bounds.bottom - dp(1.5f),
                    bounds.right - dp(0.9f), bounds.bottom, paint,
                )
            } else {
                canvas.drawRect(
                    bounds.left + dp(0.9f), bounds.top,
                    bounds.right - dp(0.9f), bounds.top + dp(1.5f), paint,
                )
            }
        }
    }

    /**
     * A recessed well: text fields, bulk editors and media frames. [pressed] deepens the shade
     * at the top, for small wells that are operated, such as an empty checkbox.
     */
    @JvmOverloads
    public fun drawWell(
        canvas: Canvas,
        bounds: RectF,
        radius: Float,
        @ColorInt rim: Int? = null,
        pressed: Boolean = false,
    ) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        val dark = theme.isDark
        val fill = MalletColors.blend(colors.canvas, colors.surface, if (dark) 0.22f else 0.35f)
        val topShade = (if (dark) 0.07f else 0.045f) * (if (pressed) 2.6f else 1f)
        resetPaint()
        paint.shader = LinearGradient(
            0f, bounds.top, 0f, bounds.bottom,
            intArrayOf(
                MalletColors.blend(fill, colors.ink, topShade),
                fill,
                MalletColors.blend(fill, colors.raised, if (dark) 0.025f else 0.16f),
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = null

        scratch.set(bounds)
        scratch.inset(dp(0.45f), dp(0.45f))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.9f)
        if (rim != null) {
            paint.color = rim
        } else if (dark) {
            paint.shader = LinearGradient(
                0f, scratch.top, 0f, scratch.bottom,
                intArrayOf(
                    Color.argb(104, 0, 0, 0),
                    MalletColors.withAlpha(colors.hairline, 132),
                    Color.argb(30, 255, 255, 255),
                ),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            // On a white canvas a light lower rim cannot be seen and the well would look open
            // at the bottom. The rim keeps its shaded top edge and closes in a hairline tone.
            paint.shader = LinearGradient(
                0f, scratch.top, 0f, scratch.bottom,
                intArrayOf(
                    MalletColors.blend(colors.canvas, Color.BLACK, 74f / 255f),
                    MalletColors.blend(colors.hairline, colors.ink, 0.14f),
                    MalletColors.blend(colors.hairline, colors.ink, 0.06f),
                ),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        val r = max(0f, radius - dp(0.45f))
        canvas.drawRoundRect(scratch, r, r, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
    }

    /** The flat fill of a row that lives inside a card and must not add its own rim or shadow. */
    public fun drawRowFill(canvas: Canvas, bounds: RectF) {
        resetPaint()
        if (theme.isDark) {
            paint.shader = LinearGradient(
                0f, bounds.top, 0f, bounds.bottom,
                intArrayOf(
                    MalletColors.blend(colors.surface, colors.ink, 0.028f),
                    colors.surface,
                    MalletColors.blend(colors.surface, colors.canvas, 0.15f),
                ),
                floatArrayOf(0f, 0.56f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            paint.color = colors.surface
        }
        canvas.drawRect(bounds, paint)
        paint.shader = null
    }

    /**
     * The recessed coloured bed uncovered behind a row that is slid aside.
     *
     * [bounds] is the whole cell of the row, not only the part that shows: the caller clips to
     * what is uncovered. In a card the cell runs from rim to rim, and [topRadius] and
     * [bottomRadius] (pixels) round the corners it shares with the card.
     */
    @JvmOverloads
    public fun drawRevealBed(
        canvas: Canvas,
        bounds: RectF,
        @ColorInt tint: Int = colors.danger,
        topRadius: Float = 0f,
        bottomRadius: Float = 0f,
    ) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        val dark = theme.isDark
        val base = if (dark) MalletColors.blend(tint, colors.canvas, 0.26f) else tint
        cell(path, bounds, topRadius, bottomRadius)
        resetPaint()
        paint.shader = LinearGradient(
            0f, bounds.top, 0f, bounds.bottom,
            intArrayOf(
                MalletColors.blend(base, colors.ink, if (dark) 0.13f else 0.09f),
                base,
                MalletColors.blend(base, Color.WHITE, if (dark) 0.035f else 0.12f),
            ),
            floatArrayOf(0f, 0.54f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, paint)
        paint.shader = null
        // The shade under the upper edge and the light on the lower one keep to the corners.
        var save = canvas.save()
        canvas.clipRect(bounds.left, bounds.top, bounds.right, bounds.top + dp(1.2f))
        paint.color = Color.argb(if (dark) 52 else 42, 0, 0, 0)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
        save = canvas.save()
        canvas.clipRect(bounds.left, bounds.bottom - dp(1.1f), bounds.right, bounds.bottom)
        paint.color = Color.argb(if (dark) 26 else 52, 255, 255, 255)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }

    /**
     * The short shade that the raised edge of a row casts on the bed it uncovers.
     *
     * [bounds], [topRadius] and [bottomRadius] describe the cell as for [drawRevealBed]. The
     * edge of the row stands at [edgeX]; the shade falls on the bed, which lies to the left of
     * the edge when [bedOnLeft] is true and to the right of it otherwise.
     */
    @JvmOverloads
    public fun drawRevealShade(
        canvas: Canvas,
        bounds: RectF,
        edgeX: Float,
        bedOnLeft: Boolean,
        topRadius: Float = 0f,
        bottomRadius: Float = 0f,
    ) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        // A narrow strip is all shade: the fade never reaches past the uncovered bed.
        val reach = min(
            dp(REVEAL_SHADE_DP),
            if (bedOnLeft) edgeX - bounds.left else bounds.right - edgeX,
        ).coerceAtLeast(0f)
        if (reach <= 0.1f) return
        val far = if (bedOnLeft) max(bounds.left, edgeX - reach) else min(bounds.right, edgeX + reach)
        if (far == edgeX) return
        cell(path, bounds, topRadius, bottomRadius)
        resetPaint()
        paint.shader = LinearGradient(
            edgeX, 0f, if (bedOnLeft) edgeX - reach else edgeX + reach, 0f,
            intArrayOf(
                Color.argb(if (theme.isDark) 62 else 38, 0, 0, 0),
                Color.argb(if (theme.isDark) 20 else 12, 0, 0, 0),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        val save = canvas.save()
        canvas.clipRect(min(far, edgeX), bounds.top, max(far, edgeX), bounds.bottom)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
        paint.shader = null
    }

    /**
     * Keyboard focus: the hairline rim of the focused material thickens into a two-dp ink rim.
     * Drawn inside [bounds], so it never needs room outside the material.
     */
    public fun drawFocusRim(canvas: Canvas, bounds: RectF, radius: Float) {
        drawFocusRim(canvas, bounds, radius, radius)
    }

    /** The focus rim of a body whose upper and lower corners are rounded differently. */
    public fun drawFocusRim(canvas: Canvas, bounds: RectF, topRadius: Float, bottomRadius: Float) {
        scratch.set(bounds)
        scratch.inset(dp(1f), dp(1f))
        resetPaint()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = colors.ink
        cell(path, scratch, max(0f, topRadius - dp(1f)), max(0f, bottomRadius - dp(1f)))
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    /** A rectangle whose two upper and two lower corners are rounded by one radius each. */
    private fun cell(out: Path, bounds: RectF, topRadius: Float, bottomRadius: Float) {
        out.rewind()
        if (topRadius <= 0f && bottomRadius <= 0f) {
            out.addRect(bounds, Path.Direction.CW)
            return
        }
        val limit = min(bounds.width(), bounds.height()) / 2f
        val top = topRadius.coerceIn(0f, limit)
        val bottom = bottomRadius.coerceIn(0f, limit)
        radii[0] = top; radii[1] = top; radii[2] = top; radii[3] = top
        radii[4] = bottom; radii[5] = bottom; radii[6] = bottom; radii[7] = bottom
        out.addRoundRect(bounds, radii, Path.Direction.CW)
    }

    private fun shape(out: Path, bounds: RectF, radius: Float, corners: MalletCorners) {
        out.rewind()
        if (corners == MalletCorners.ALL) {
            out.addRoundRect(bounds, radius, radius, Path.Direction.CW)
            return
        }
        val top = if (corners == MalletCorners.TOP) radius else 0f
        val bottom = if (corners == MalletCorners.BOTTOM) radius else 0f
        radii[0] = top; radii[1] = top; radii[2] = top; radii[3] = top
        radii[4] = bottom; radii[5] = bottom; radii[6] = bottom; radii[7] = bottom
        out.addRoundRect(bounds, radii, Path.Direction.CW)
    }

    private fun resetPaint() {
        paint.shader = null
        paint.colorFilter = null
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    public companion object {
        /** Corner radius of cards, in dp. */
        public const val RADIUS_CARD_DP: Float = 8f

        /** Corner radius of dialogs and sheets, in dp. */
        public const val RADIUS_DIALOG_DP: Float = 16f

        /** Corner radius of floating panels and menus, in dp. */
        public const val RADIUS_PANEL_DP: Float = 10f

        /** Corner radius of text wells, in dp. */
        public const val RADIUS_WELL_DP: Float = 9f

        /** Corner radius of media wells, in dp. */
        public const val RADIUS_MEDIA_DP: Float = 7f

        /**
         * How far the hairline rim of a raised surface reaches into its body, in dp. What is
         * drawn inside a body from rim to rim starts this far from its edge.
         */
        public const val RIM_DP: Float = 0.9f

        private const val REVEAL_SHADE_DP = 5f

        private const val SHADOW_LAYERS = 18
        private val DARK_SPREAD_COMPACT = floatArrayOf(2.8f, 2.05f, 1.35f, 0.72f)
        private val DARK_SPREAD_WIDE = floatArrayOf(4.8f, 3.55f, 2.3f, 1.15f)
        private val DARK_OPACITY_COMPACT = intArrayOf(16, 20, 25, 30)
        private val DARK_OPACITY_WIDE = intArrayOf(10, 14, 19, 25)
    }
}
