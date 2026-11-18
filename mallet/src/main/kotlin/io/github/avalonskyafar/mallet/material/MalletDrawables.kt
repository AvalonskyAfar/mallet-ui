package io.github.avalonskyafar.mallet.material

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import kotlin.math.roundToInt

/**
 * Room, in dp, that a raised porcelain body leaves around itself for its shadow.
 *
 * A view that uses [MalletSurfaceDrawable] as its background is therefore a little larger than
 * the card the eye sees: 1.4 dp on each side, 1 dp above and 4.8 dp below.
 */
public object MalletGutters {
    public const val SURFACE_SIDE_DP: Float = 1.4f
    public const val SURFACE_TOP_DP: Float = 1f
    public const val SURFACE_BOTTOM_DP: Float = 4.8f

    /** Gutter of a flat body that carries no shadow. */
    public const val FLAT_DP: Float = 0.5f
}

/**
 * Background drawable for porcelain: cards, capsule buttons, dialogs, toasts and panels.
 *
 * @param radiusDp corner radius; ignored when [pill] is set
 * @param pill fully rounded ends, whatever the height
 * @param tint `null` for neutral porcelain, or the colour of a glazed body
 * @param shadow whether the body is lifted; a flat body only keeps its rim
 */
public class MalletSurfaceDrawable @JvmOverloads constructor(
    private val material: MalletMaterial,
    private val radiusDp: Float = MalletMaterial.RADIUS_CARD_DP,
    private val pill: Boolean = false,
    @ColorInt tint: Int? = null,
    private val shadow: Boolean = true,
    private val corners: MalletCorners = MalletCorners.ALL,
) : Drawable() {

    @JvmOverloads
    public constructor(
        context: Context,
        radiusDp: Float = MalletMaterial.RADIUS_CARD_DP,
        pill: Boolean = false,
        @ColorInt tint: Int? = null,
        shadow: Boolean = true,
        corners: MalletCorners = MalletCorners.ALL,
    ) : this(MalletMaterial(context), radiusDp, pill, tint, shadow, corners)

    private val body = RectF()
    private var layerAlpha = 255

    /**
     * Colour of a glazed body, or `null` for neutral porcelain.
     *
     * The platform's tint calls end here too: `Drawable.setTint(int)`, [setTintList],
     * `DrawableCompat.setTint` and `android:backgroundTint` of the view that carries this
     * background all hue the body.
     */
    @get:ColorInt
    @setparam:ColorInt
    public var tint: Int? = tint
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    /**
     * Hues the body with the default colour of [tint]; `null` returns it to neutral
     * porcelain. The body has one colour, so the states of the list are not followed.
     */
    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint?.defaultColor
    }

    /** Draws the tighter shadow of a pressed body and lets it sink by [sinkPx]. */
    public var pressed: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    /** Distance the body sinks while [pressed], in pixels. */
    public var sinkPx: Float = 0f

    /** The rectangle of the visible body inside [bounds], without the shadow gutter. */
    public fun bodyBounds(bounds: Rect, out: RectF) {
        val m = material
        if (shadow) {
            val top = if (corners == MalletCorners.BOTTOM) 0f else m.dp(MalletGutters.SURFACE_TOP_DP)
            val bottom = if (corners == MalletCorners.TOP) 0f else m.dp(MalletGutters.SURFACE_BOTTOM_DP)
            out.set(
                bounds.left + m.dp(MalletGutters.SURFACE_SIDE_DP),
                bounds.top + top,
                bounds.right - m.dp(MalletGutters.SURFACE_SIDE_DP),
                bounds.bottom - bottom,
            )
        } else {
            val flat = m.dp(MalletGutters.FLAT_DP)
            out.set(bounds.left + flat, bounds.top + flat, bounds.right - flat, bounds.bottom - flat)
        }
    }

    private fun radius(): Float = if (pill) body.height() / 2f else material.dp(radiusDp)

    override fun draw(canvas: Canvas) {
        bodyBounds(bounds, body)
        if (body.width() <= 0f || body.height() <= 0f) return
        if (pressed && sinkPx != 0f) body.offset(0f, sinkPx)
        val save = if (layerAlpha < 255) {
            canvas.saveLayerAlpha(
                bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(), layerAlpha,
            )
        } else {
            -1
        }
        material.drawSurface(canvas, body, radius(), tint, shadow, pressed, corners)
        if (save >= 0) canvas.restoreToCount(save)
    }

    override fun getOutline(outline: Outline) {
        bodyBounds(bounds, body)
        outline.setRoundRect(
            body.left.roundToInt(), body.top.roundToInt(),
            body.right.roundToInt(), body.bottom.roundToInt(), radius(),
        )
    }

    override fun setAlpha(alpha: Int) {
        val bounded = alpha.coerceIn(0, 255)
        if (layerAlpha != bounded) {
            layerAlpha = bounded
            invalidateSelf()
        }
    }

    override fun getAlpha(): Int = layerAlpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Porcelain is drawn from theme colours; a colour filter has no defined meaning here.
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Background drawable for a recessed well: text fields, bulk editors and media frames.
 *
 * The rim normally shades from dark at the top to light at the bottom. Set [rim] to replace it
 * with a solid colour, which is how a field shows focus or an error.
 */
public class MalletWellDrawable @JvmOverloads constructor(
    private val material: MalletMaterial,
    private val radiusDp: Float = MalletMaterial.RADIUS_WELL_DP,
) : Drawable() {

    @JvmOverloads
    public constructor(context: Context, radiusDp: Float = MalletMaterial.RADIUS_WELL_DP) :
        this(MalletMaterial(context), radiusDp)

    private val body = RectF()
    private var layerAlpha = 255

    @get:ColorInt
    @setparam:ColorInt
    public var rim: Int? = null
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    /** Draws the two-dp ink rim of keyboard focus on top of the well. */
    public var focused: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    override fun draw(canvas: Canvas) {
        val flat = material.dp(MalletGutters.FLAT_DP)
        body.set(bounds.left + flat, bounds.top + flat, bounds.right - flat, bounds.bottom - flat)
        if (body.width() <= 0f || body.height() <= 0f) return
        val save = if (layerAlpha < 255) {
            canvas.saveLayerAlpha(
                bounds.left.toFloat(), bounds.top.toFloat(),
                bounds.right.toFloat(), bounds.bottom.toFloat(), layerAlpha,
            )
        } else {
            -1
        }
        val radius = material.dp(radiusDp)
        material.drawWell(canvas, body, radius, rim)
        if (focused) material.drawFocusRim(canvas, body, radius)
        if (save >= 0) canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        val bounded = alpha.coerceIn(0, 255)
        if (layerAlpha != bounded) {
            layerAlpha = bounded
            invalidateSelf()
        }
    }

    override fun getAlpha(): Int = layerAlpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Wells are drawn from theme colours; a colour filter has no defined meaning here.
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
