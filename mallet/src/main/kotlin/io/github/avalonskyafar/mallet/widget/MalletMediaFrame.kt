package io.github.avalonskyafar.mallet.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.icon.MalletIcon
import io.github.avalonskyafar.mallet.icon.MalletIconPainter
import io.github.avalonskyafar.mallet.material.MalletMaterial
import io.github.avalonskyafar.mallet.material.MalletWellDrawable
import io.github.avalonskyafar.mallet.theme.MalletTheme
import kotlin.math.roundToInt

/**
 * A picture set into a recessed well: screenshots in a tutorial, an illustration in a card.
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.widget.MalletMediaFrame
 *     android:layout_width="match_parent"
 *     android:layout_height="152dp"
 *     android:src="@drawable/tutorial_step_one"
 *     android:contentDescription="@string/tutorial_step_one_description" />
 * ```
 *
 * The well stays visible on all four sides: the picture is inset by 4 dp and its corners are
 * clipped to a 4 dp radius, inside the 7 dp radius of the well. The picture is
 * centre-cropped unless [scaleType] says otherwise.
 *
 * Height: with an [aspectRatio] the picture keeps that shape at whatever width the frame is
 * given. Without one the layout height is used, and `wrap_content` falls back to
 * [DEFAULT_HEIGHT_DP] so that an empty frame and a frame with a large bitmap are equally tall.
 *
 * While there is no image the well is empty apart from a muted picture icon in its centre,
 * which makes the frame usable as a placeholder while the image loads.
 *
 * Accessibility: the frame is one element that reports itself as an image. It is announced
 * only when it has a content description; a picture without one is treated as decoration.
 */
public open class MalletMediaFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val theme: MalletTheme = MalletTheme.of(context)
    private val material = MalletMaterial(context, theme)
    private val density = resources.displayMetrics.density

    // False while the super constructor runs, which already calls setContentDescription.
    private var ready = false

    /**
     * The view that shows the picture, for image loaders and anything this class does not
     * forward. It is created by the frame and must not be removed from it.
     */
    public val imageView: ImageView = ClippedImageView(context, theme, IMAGE_RADIUS_DP * density)

    private val well = MalletWellDrawable(material, MalletMaterial.RADIUS_MEDIA_DP)

    /** How the picture is fitted into the frame. Defaults to `CENTER_CROP`. */
    public var scaleType: ImageView.ScaleType
        get() = imageView.scaleType
        set(value) {
            imageView.scaleType = value
        }

    /**
     * Width divided by height of the picture inside the well, for example `16f / 9f`. With a
     * ratio the frame derives its height from its width (or its width from its height when
     * only the height is known) and ignores the layout height, except that it never grows
     * past an `AT_MOST` limit. `0` (the default) uses the layout height.
     */
    public var aspectRatio: Float = 0f
        set(value) {
            val bounded = if (value.isNaN() || value.isInfinite() || value < 0f) 0f else value
            if (field != bounded) {
                field = bounded
                requestLayout()
            }
        }

    /** Whether a picture is set. Without one the frame shows the empty well and its icon. */
    public val hasImage: Boolean get() = imageView.drawable != null

    init {
        background = well
        val inset = (IMAGE_INSET_DP * density).roundToInt()
        super.setPadding(inset, inset, inset, inset)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletMediaFrame, defStyleAttr, 0)
            try {
                aspectRatio = a.getFloat(R.styleable.MalletMediaFrame_malletMediaAspectRatio, 0f)
                val type = a.getInt(R.styleable.MalletMediaFrame_android_scaleType, -1)
                if (type in SCALE_TYPES.indices) imageView.scaleType = SCALE_TYPES[type]
                a.getDrawable(R.styleable.MalletMediaFrame_android_src)?.let { imageView.setImageDrawable(it) }
            } finally {
                a.recycle()
            }
        }
        ready = true
        syncAccessibilityImportance()
    }

    /** Shows [drawable], or the empty well when it is `null`. */
    public fun setImageDrawable(drawable: Drawable?) {
        imageView.setImageDrawable(drawable)
    }

    /** Shows [bitmap], or the empty well when it is `null`. */
    public fun setImageBitmap(bitmap: Bitmap?) {
        if (bitmap == null) imageView.setImageDrawable(null) else imageView.setImageBitmap(bitmap)
    }

    /** Shows the drawable resource [resId]; `0` clears the picture. */
    public fun setImageResource(@DrawableRes resId: Int) {
        imageView.setImageResource(resId)
    }

    /**
     * The distance between the well and the picture is part of the material and stays at
     * 4 dp; padding set from outside is ignored.
     */
    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        // Deliberately empty, see above.
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        // Deliberately empty, see setPadding.
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val padH = paddingLeft + paddingRight
        val padV = paddingTop + paddingBottom
        val fallback = (DEFAULT_HEIGHT_DP * density).roundToInt()
        val ratio = aspectRatio

        var width: Int
        var height: Int
        if (ratio > 0f) {
            if (widthMode != MeasureSpec.UNSPECIFIED) {
                width = widthSize
                height = padV + ((width - padH).coerceAtLeast(0) / ratio).roundToInt()
                if (heightMode == MeasureSpec.AT_MOST) height = height.coerceAtMost(heightSize)
            } else {
                height = if (heightMode == MeasureSpec.UNSPECIFIED) fallback else heightSize
                width = padH + ((height - padV).coerceAtLeast(0) * ratio).roundToInt()
            }
        } else {
            height = when (heightMode) {
                MeasureSpec.EXACTLY -> heightSize
                MeasureSpec.AT_MOST -> fallback.coerceAtMost(heightSize)
                else -> fallback
            }
            width = if (widthMode == MeasureSpec.UNSPECIFIED) fallback else widthSize
        }
        width = width.coerceAtLeast(suggestedMinimumWidth)
        height = height.coerceAtLeast(suggestedMinimumHeight)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    override fun draw(canvas: Canvas) {
        if (isEnabled) {
            super.draw(canvas)
            return
        }
        // Disabled pales: the well and the picture fade together, as one body.
        val save = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), MalletControl.DISABLED_ALPHA)
        super.draw(canvas)
        canvas.restoreToCount(save)
    }

    override fun setEnabled(enabled: Boolean) {
        val changed = enabled != isEnabled
        super.setEnabled(enabled)
        if (changed) invalidate()
    }

    override fun setContentDescription(contentDescription: CharSequence?) {
        super.setContentDescription(contentDescription)
        if (ready) syncAccessibilityImportance()
    }

    private fun syncAccessibilityImportance() {
        importantForAccessibility = if (contentDescription.isNullOrEmpty()) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

    override fun getAccessibilityClassName(): CharSequence = ImageView::class.java.name

    public companion object {
        /** Distance between the edge of the well and the picture, in dp. */
        public const val IMAGE_INSET_DP: Float = 4f

        /** Corner radius of the picture, in dp. */
        public const val IMAGE_RADIUS_DP: Float = 4f

        /** Height of a frame that has neither a layout height nor an aspect ratio, in dp. */
        public const val DEFAULT_HEIGHT_DP: Float = 152f

        // Same order as the android:scaleType attribute.
        private val SCALE_TYPES = arrayOf(
            ImageView.ScaleType.MATRIX,
            ImageView.ScaleType.FIT_XY,
            ImageView.ScaleType.FIT_START,
            ImageView.ScaleType.FIT_CENTER,
            ImageView.ScaleType.FIT_END,
            ImageView.ScaleType.CENTER,
            ImageView.ScaleType.CENTER_CROP,
            ImageView.ScaleType.CENTER_INSIDE,
        )
    }
}

private const val EMPTY_ICON_DP = 28f
private const val EMPTY_ICON_STROKE_DP = 1.75f

/**
 * Clips the picture with a path rather than an outline, so the result is the same on every
 * API level and in software rendering, and paints the placeholder icon while it is empty.
 */
@SuppressLint("AppCompatCustomView", "ViewConstructor")
private class ClippedImageView(
    context: Context,
    private val theme: MalletTheme,
    private val radiusPx: Float,
) : ImageView(context) {

    private val clipPath = Path()
    private val clipBounds = RectF()

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        clipPath.reset()
        clipBounds.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.addRoundRect(clipBounds, radiusPx, radiusPx, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        if (drawable == null) {
            drawPlaceholder(canvas)
            return
        }
        val checkpoint = canvas.save()
        canvas.clipPath(clipPath)
        super.onDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    private fun drawPlaceholder(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val size = minOf(EMPTY_ICON_DP * density, minOf(width, height) * 0.6f)
        if (size <= 0f) return
        MalletIconPainter.draw(
            canvas, MalletIcon.IMAGE,
            (width - size) / 2f, (height - size) / 2f, size,
            theme.colors.muted, EMPTY_ICON_STROKE_DP * density * size / (EMPTY_ICON_DP * density),
        )
    }
}
