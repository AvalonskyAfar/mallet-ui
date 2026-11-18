package io.github.avalonskyafar.mallet.widget

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.common.truth.Truth.assertThat
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.testing.Snapshots
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MalletMediaParagraphTest {

    private val context = Snapshots.context(MalletTheme.light())
    private val density = context.resources.displayMetrics.density
    private val joiner = MalletParagraph.WORD_JOINER

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private fun picture(color: Int, width: Int = 64, height: Int = 64): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    // ---------------------------------------------------------------- media frame

    @Test
    fun `frame insets the picture four dp and crops it to the centre`() {
        val frame = MalletMediaFrame(context)
        Snapshots.layout(frame, 300, 152)
        val image = frame.imageView
        assertThat(image.left).isEqualTo(px(4f))
        assertThat(image.top).isEqualTo(px(4f))
        assertThat(image.right).isEqualTo(frame.width - px(4f))
        assertThat(image.bottom).isEqualTo(frame.height - px(4f))
        assertThat(frame.scaleType).isEqualTo(ImageView.ScaleType.CENTER_CROP)
        assertThat(frame.hasImage).isFalse()
    }

    @Test
    fun `padding from outside does not move the picture`() {
        val frame = MalletMediaFrame(context)
        frame.setPadding(40, 40, 40, 40)
        frame.setPaddingRelative(40, 40, 40, 40)
        assertThat(frame.paddingLeft).isEqualTo(px(4f))
        assertThat(frame.paddingBottom).isEqualTo(px(4f))
    }

    @Test
    fun `picture corners are clipped and show the well`() {
        val red = Color.rgb(200, 30, 60)
        val empty = Snapshots.render(MalletMediaFrame(context), 300, 152)
        val frame = MalletMediaFrame(context)
        frame.setImageBitmap(picture(red))
        assertThat(frame.hasImage).isTrue()
        val filled = Snapshots.render(frame, 300, 152)

        val inset = px(4f)
        // Centre of the picture: the image colour, untouched.
        assertThat(filled.getPixel(filled.width / 2, filled.height / 2)).isEqualTo(red)
        // Well band between the rim and the picture: the same pixels as an empty frame.
        assertThat(filled.getPixel(filled.width / 2, inset / 2 + 1))
            .isEqualTo(empty.getPixel(filled.width / 2, inset / 2 + 1))
        // The four corner pixels of the picture rectangle lie outside the 4 dp radius.
        val right = filled.width - inset - 1
        val bottom = filled.height - inset - 1
        for ((x, y) in listOf(inset to inset, right to inset, inset to bottom, right to bottom)) {
            assertThat(filled.getPixel(x, y)).isNotEqualTo(red)
            assertThat(filled.getPixel(x, y)).isEqualTo(empty.getPixel(x, y))
        }
        // Just inside the radius along the edge the picture is back.
        assertThat(filled.getPixel(inset + px(4f) + 1, inset + 1)).isEqualTo(red)
        assertThat(filled.getPixel(inset + 1, inset + px(4f) + 1)).isEqualTo(red)
    }

    @Test
    fun `empty frame shows a muted picture icon in the centre`() {
        val theme = MalletTheme.light()
        val bitmap = Snapshots.render(MalletMediaFrame(context), 300, 152)
        val half = px(14f)
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        var inside = 0
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) != theme.colors.muted) continue
            inside++
            minX = minOf(minX, x); maxX = maxOf(maxX, x)
            minY = minOf(minY, y); maxY = maxOf(maxY, y)
        }
        assertThat(inside).isGreaterThan(40)
        // Every muted pixel belongs to the 28 dp icon box, and the glyph is centred in it.
        assertThat(minX).isAtLeast(cx - half - 1)
        assertThat(maxX).isAtMost(cx + half + 1)
        assertThat(minY).isAtLeast(cy - half - 1)
        assertThat(maxY).isAtMost(cy + half + 1)
        assertThat((minX + maxX) / 2f).isWithin(1.5f).of(cx.toFloat())

        val frame = MalletMediaFrame(context)
        frame.setImageDrawable(ColorDrawable(Color.BLUE))
        frame.setImageDrawable(null)
        assertThat(frame.hasImage).isFalse()
        assertThat(Snapshots.render(frame, 300, 152).sameAs(bitmap)).isTrue()
    }

    @Test
    fun `aspect ratio decides the height from the width`() {
        val frame = MalletMediaFrame(context)
        frame.aspectRatio = 2f
        Snapshots.layout(frame, 300)
        val pad = 2 * px(4f)
        assertThat(frame.height).isEqualTo(pad + ((frame.width - pad) / 2f).roundToInt())
        assertThat(frame.imageView.width / frame.imageView.height.toFloat()).isWithin(0.01f).of(2f)

        // The layout height loses against the ratio ...
        frame.layoutParams = ViewGroup.LayoutParams(-1, px(40f))
        Snapshots.layout(frame, 200, 40)
        assertThat(frame.imageView.width / frame.imageView.height.toFloat()).isWithin(0.01f).of(2f)

        // ... an AT_MOST limit does not, and an unknown width follows the height.
        frame.measure(
            MeasureSpec.makeMeasureSpec(px(300f), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(px(60f), MeasureSpec.AT_MOST),
        )
        assertThat(frame.measuredHeight).isEqualTo(px(60f))
        frame.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(px(108f), MeasureSpec.EXACTLY),
        )
        assertThat(frame.measuredWidth).isEqualTo(pad + (px(108f) - pad) * 2)
    }

    @Test
    fun `without a ratio the layout height is used and wrap content is 152 dp`() {
        val frame = MalletMediaFrame(context)
        frame.setImageBitmap(picture(Color.GREEN, 2000, 3000))
        Snapshots.layout(frame, 300)
        assertThat(frame.height).isEqualTo(px(152f))
        Snapshots.layout(frame, 300, 90)
        assertThat(frame.height).isEqualTo(px(90f))
        frame.aspectRatio = -3f
        assertThat(frame.aspectRatio).isEqualTo(0f)
        frame.aspectRatio = Float.NaN
        assertThat(frame.aspectRatio).isEqualTo(0f)
    }

    @Test
    fun `scale type and image setters reach the image view`() {
        val frame = MalletMediaFrame(context)
        frame.scaleType = ImageView.ScaleType.FIT_CENTER
        assertThat(frame.imageView.scaleType).isEqualTo(ImageView.ScaleType.FIT_CENTER)
        val drawable = ColorDrawable(Color.MAGENTA)
        frame.setImageDrawable(drawable)
        assertThat(frame.imageView.drawable).isSameInstanceAs(drawable)
        frame.setImageBitmap(null)
        assertThat(frame.hasImage).isFalse()
        frame.setImageResource(android.R.drawable.ic_delete)
        assertThat(frame.hasImage).isTrue()
        frame.setImageResource(0)
        assertThat(frame.hasImage).isFalse()
    }

    @Test
    fun `disabled frame pales as one body`() {
        val theme = MalletTheme.light()
        val red = Color.rgb(200, 30, 60)
        val frame = MalletMediaFrame(context)
        frame.setImageBitmap(picture(red))
        frame.isEnabled = false
        val bitmap = Snapshots.render(frame, 300, 152)
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        val share = MalletControl.DISABLED_ALPHA / 255f
        val canvas = theme.colors.canvas
        fun mix(a: Int, b: Int): Float = a * share + b * (1 - share)
        assertThat(Color.red(pixel).toFloat()).isWithin(2f).of(mix(Color.red(red), Color.red(canvas)))
        assertThat(Color.green(pixel).toFloat()).isWithin(2f).of(mix(Color.green(red), Color.green(canvas)))
        assertThat(Color.blue(pixel).toFloat()).isWithin(2f).of(mix(Color.blue(red), Color.blue(canvas)))
        // Shape and layout do not change.
        assertThat(frame.imageView.left).isEqualTo(px(4f))
        frame.isEnabled = true
        val again = Snapshots.render(frame, 300, 152)
        assertThat(again.getPixel(again.width / 2, again.height / 2)).isEqualTo(red)
    }

    @Test
    fun `frame is announced as an image only when it is described`() {
        val frame = MalletMediaFrame(context)
        assertThat(frame.importantForAccessibility)
            .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        assertThat(frame.imageView.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
        frame.contentDescription = "Step one"
        assertThat(frame.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
        assertThat(frame.accessibilityClassName.toString()).isEqualTo(ImageView::class.java.name)
        frame.contentDescription = null
        assertThat(frame.importantForAccessibility)
            .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        // Not a control: it takes neither focus nor clicks.
        assertThat(frame.isFocusable).isFalse()
        assertThat(frame.isClickable).isFalse()
    }

    @Test
    fun `frame reads its xml attributes`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(android.R.attr.src, "@android:drawable/ic_delete")
            .addAttribute(android.R.attr.scaleType, "fitCenter")
            .addAttribute(android.R.attr.contentDescription, "Step one")
            .addAttribute(R.attr.malletMediaAspectRatio, "1.5")
            .build()
        val frame = MalletMediaFrame(context, attrs)
        assertThat(frame.hasImage).isTrue()
        assertThat(frame.scaleType).isEqualTo(ImageView.ScaleType.FIT_CENTER)
        assertThat(frame.aspectRatio).isEqualTo(1.5f)
        assertThat(frame.contentDescription.toString()).isEqualTo("Step one")
        assertThat(frame.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
    }

    @Test
    fun `frame survives degenerate sizes and mirrors nothing in rtl`() {
        for (side in listOf(0, 1, px(6f), px(9f))) {
            val frame = MalletMediaFrame(context)
            frame.aspectRatio = 4f
            val spec = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY)
            frame.measure(spec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
            assertThat(frame.measuredWidth).isEqualTo(side)
            assertThat(frame.measuredHeight).isAtLeast(0)
            if (frame.width > 0 && frame.height > 0) {
                val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                frame.draw(android.graphics.Canvas(bitmap)) // empty: placeholder path
                frame.setImageBitmap(picture(Color.RED))
                frame.draw(android.graphics.Canvas(bitmap)) // filled: clip path
            }
        }
        // The inset is the same on both sides, so a right-to-left layout changes nothing.
        val rtl = MalletMediaFrame(context)
        rtl.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(rtl, 300, 152)
        assertThat(rtl.imageView.left).isEqualTo(px(4f))
        assertThat(rtl.imageView.right).isEqualTo(rtl.width - px(4f))
    }

    @Test
    fun `paragraph wraps a long unbroken word inside its width`() {
        val paragraph = MalletParagraph(context)
        paragraph.text = "Donaudampfschifffahrtsgesellschaftskapitaensmuetze".repeat(3)
        Snapshots.layout(paragraph, 120)
        val layout = paragraph.layout
        assertThat(layout.lineCount).isGreaterThan(1)
        for (line in 0 until layout.lineCount) {
            assertThat(layout.getLineWidth(line)).isAtMost(paragraph.width.toFloat())
        }
        // Nothing is hyphenated: the shown text is the given text.
        assertThat(paragraph.text.toString()).isEqualTo(paragraph.rawText.toString())
    }

    // ---------------------------------------------------------------- paragraph

    @Test
    fun `paragraph is body copy that fills its lines from the start`() {
        val paragraph = MalletParagraph(context)
        assertThat(paragraph.role).isEqualTo(MalletTextRole.BODY)
        assertThat(paragraph.tone).isEqualTo(MalletTextTone.INK)
        assertThat(paragraph.gravity and Gravity.VERTICAL_GRAVITY_MASK).isEqualTo(Gravity.TOP)
        assertThat(paragraph.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK).isEqualTo(Gravity.START)
        assertThat(paragraph.breakStrategy).isEqualTo(Layout.BREAK_STRATEGY_SIMPLE)
        assertThat(paragraph.hyphenationFrequency).isEqualTo(Layout.HYPHENATION_FREQUENCY_NONE)
        assertThat(paragraph.includeFontPadding).isFalse()
        assertThat(paragraph.protectClosingPunctuation).isTrue()
    }

    @Test
    fun `joiners go only before closing punctuation`() {
        val paragraph = MalletParagraph(context)
        paragraph.text = "你好，世界。（真的）“是”吗？"
        assertThat(paragraph.text.toString())
            .isEqualTo("你好$joiner，世界$joiner。（真的$joiner）“是$joiner”吗$joiner？")
        assertThat(paragraph.rawText.toString()).isEqualTo("你好，世界。（真的）“是”吗？")

        // One joiner per mark of the set, none anywhere else.
        for (mark in MalletParagraph.CLOSING_PUNCTUATION) {
            assertThat(MalletParagraph.protect("字$mark").toString()).isEqualTo("字$joiner$mark")
        }
        assertThat(MalletParagraph.CLOSING_PUNCTUATION).hasLength(12)
    }

    @Test
    fun `text without closing punctuation is left alone`() {
        val latin = "Hello, world. (Really?) \"Yes\"; fine: ok!"
        assertThat(MalletParagraph.protect(latin)).isSameInstanceAs(latin)
        // A mark that opens the text has nothing to cling to.
        val leading = "，好"
        assertThat(MalletParagraph.protect(leading)).isSameInstanceAs(leading)
        // Opening marks are not in the set.
        assertThat(MalletParagraph.protect("（【《“‘")).isEqualTo("（【《“‘")
        assertThat(MalletParagraph.protect(null)).isNull()
        assertThat(MalletParagraph.protect("")).isEqualTo("")
        val paragraph = MalletParagraph(context)
        paragraph.text = null
        assertThat(paragraph.text.toString()).isEmpty()
    }

    @Test
    fun `protecting twice adds nothing`() {
        val once = MalletParagraph.protect("好。好，好！")
        assertThat(MalletParagraph.protect(once)).isSameInstanceAs(once)
        val paragraph = MalletParagraph(context)
        paragraph.text = "好。好，好！"
        paragraph.text = paragraph.text
        assertThat(paragraph.text.toString()).isEqualTo("好$joiner。好$joiner，好$joiner！")
    }

    @Test
    fun `switching protection off restores the raw text`() {
        val paragraph = MalletParagraph(context)
        paragraph.rawText = "一，二。"
        assertThat(paragraph.text.toString()).isEqualTo("一$joiner，二$joiner。")
        paragraph.protectClosingPunctuation = false
        assertThat(paragraph.text.toString()).isEqualTo("一，二。")
        assertThat(paragraph.rawText.toString()).isEqualTo("一，二。")
        paragraph.protectClosingPunctuation = true
        assertThat(paragraph.text.toString()).isEqualTo("一$joiner，二$joiner。")
    }

    @Test
    fun `spans survive and move with their text`() {
        val source = SpannableString("你好，世界。")
        val bold = StyleSpan(android.graphics.Typeface.BOLD)
        source.setSpan(bold, 3, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val paragraph = MalletParagraph(context)
        paragraph.text = source
        val shown = paragraph.text as Spanned
        val start = shown.getSpanStart(bold)
        val end = shown.getSpanEnd(bold)
        assertThat(shown.subSequence(start, end).toString()).isEqualTo("世界")
        assertThat(paragraph.rawText).isSameInstanceAs(source)
    }

    @Test
    fun `no line starts with closing punctuation at any width`() {
        val copy = "守护模式开启后，应用会在后台保持运行。若系统提示耗电，请选择“允许”；" +
            "否则保护可能中断！需要帮助吗？请查看教程（第二节）、或联系我们。"
        val paragraph = MalletParagraph(context)
        paragraph.text = copy
        var wrapped = 0
        for (widthDp in 60..300 step 7) {
            Snapshots.layout(paragraph, widthDp)
            val layout = paragraph.layout
            val shown = paragraph.text
            if (layout.lineCount > 1) wrapped++
            for (line in 0 until layout.lineCount) {
                var first = layout.getLineStart(line)
                while (first < shown.length && shown[first] == joiner) first++
                if (first >= shown.length) continue
                assertThat(MalletParagraph.CLOSING_PUNCTUATION.indexOf(shown[first])).isEqualTo(-1)
            }
        }
        assertThat(wrapped).isGreaterThan(10)
    }

    @Test
    fun `paragraph reads its xml attributes`() {
        val plain = MalletParagraph(
            context,
            Robolectric.buildAttributeSet().addAttribute(android.R.attr.text, "一，二。").build(),
        )
        assertThat(plain.text.toString()).isEqualTo("一$joiner，二$joiner。")
        assertThat(plain.rawText.toString()).isEqualTo("一，二。")
        assertThat(plain.role).isEqualTo(MalletTextRole.BODY)

        val custom = MalletParagraph(
            context,
            Robolectric.buildAttributeSet()
                .addAttribute(android.R.attr.text, "一，二。")
                .addAttribute(R.attr.malletParagraphProtectClosingPunctuation, "false")
                .addAttribute(R.attr.malletTextRole, "caption")
                .addAttribute(R.attr.malletTone, "muted")
                .build(),
        )
        assertThat(custom.text.toString()).isEqualTo("一，二。")
        assertThat(custom.protectClosingPunctuation).isFalse()
        assertThat(custom.role).isEqualTo(MalletTextRole.CAPTION)
        assertThat(custom.tone).isEqualTo(MalletTextTone.MUTED)
    }

    @Test
    fun `accessibility services get the copy without joiners`() {
        val paragraph = MalletParagraph(context)
        paragraph.text = "你好，世界。"
        val info = AccessibilityNodeInfo.obtain()
        paragraph.onInitializeAccessibilityNodeInfo(info)
        assertThat(info.text?.toString()).isEqualTo("你好，世界。")
    }

    @Test
    fun `paragraph follows the layout direction and survives large text`() {
        // Without this flag the platform runs in RTL compatibility mode and ignores the direction.
        val app = context.applicationContext
        app.applicationInfo.flags = app.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        val paragraph = MalletParagraph(context)
        paragraph.text = "One line that is long enough to wrap at least once in a narrow column."
        paragraph.layoutDirection = View.LAYOUT_DIRECTION_RTL
        Snapshots.layout(paragraph, 200)
        // Latin copy in a right-to-left layout still hugs the start edge, which is the right.
        val last = paragraph.layout.lineCount - 1
        assertThat(paragraph.layout.getLineRight(last)).isWithin(1f).of(paragraph.layout.width.toFloat())
        assertThat(paragraph.layout.getLineLeft(last)).isGreaterThan(1f)
        assertThat(paragraph.textAlignment).isEqualTo(View.TEXT_ALIGNMENT_VIEW_START)
        val normalHeight = paragraph.height

        val large = MalletParagraph(Snapshots.context(MalletTheme.light().copy(textScale = MalletTheme.LARGE_TEXT_SCALE)))
        large.text = paragraph.rawText
        Snapshots.layout(large, 200)
        assertThat(large.textSize).isGreaterThan(paragraph.textSize)
        assertThat(large.height).isAtLeast(normalHeight)
        assertThat(large.layout.getLineWidth(0)).isAtMost(large.width.toFloat())
    }

    // ---------------------------------------------------------------- snapshots

    /** A small landscape, so that cropping and corner clipping are visible in the snapshot. */
    private fun landscape(dark: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, 360f,
            if (dark) Color.rgb(24, 38, 66) else Color.rgb(142, 186, 222),
            if (dark) Color.rgb(96, 84, 110) else Color.rgb(240, 226, 204),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, 640f, 360f, paint)
        paint.shader = null
        paint.color = if (dark) Color.rgb(226, 214, 180) else Color.rgb(250, 244, 226)
        canvas.drawCircle(470f, 110f, 42f, paint)
        paint.color = if (dark) Color.rgb(44, 70, 78) else Color.rgb(104, 150, 136)
        canvas.drawOval(-160f, 210f, 420f, 520f, paint)
        paint.color = if (dark) Color.rgb(30, 50, 60) else Color.rgb(70, 118, 112)
        canvas.drawOval(220f, 250f, 860f, 560f, paint)
        return bitmap
    }

    private fun card(context: android.content.Context, vararg children: View): MalletSurface {
        val surface = MalletSurface(context)
        children.forEachIndexed { index, child ->
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            // Loose text sits 10 dp inside the card content, pictures run to the content edge.
            if (child !is MalletMediaFrame) {
                params.marginStart = px(10f)
                params.marginEnd = px(10f)
            }
            params.topMargin = if (index == 0) px(if (child is MalletMediaFrame) 0f else 9f) else px(12f)
            if (index == children.lastIndex) params.bottomMargin = px(if (child is MalletMediaFrame) 0f else 10f)
            surface.addView(child, params)
        }
        return surface
    }

    private fun page(theme: MalletTheme): View {
        val themed = Snapshots.context(theme)
        val column = LinearLayout(themed)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(px(12f), px(22f), px(12f), px(34f))

        val photo = MalletMediaFrame(themed)
        photo.setImageBitmap(landscape(theme.isDark))
        photo.contentDescription = "Hills at dusk"
        val intro = MalletParagraph(themed)
        intro.text = "A picture sits in a recessed well, four dp from the rim, and the copy below it " +
            "fills every line before it wraps. Nothing is hyphenated and nothing is balanced."
        val chinese = MalletParagraph(themed)
        chinese.text = "守护模式开启后，应用会在后台保持运行。若系统提示耗电，请选择“允许”；否则保护可能中断！"

        val placeholder = MalletMediaFrame(themed)
        placeholder.aspectRatio = 16f / 9f
        val note = MalletParagraph(themed)
        note.tone = MalletTextTone.MUTED
        note.text = "Until a picture arrives the well stays empty and shows a muted icon in its centre."
        val disabled = MalletMediaFrame(themed)
        disabled.setImageBitmap(landscape(theme.isDark))
        disabled.aspectRatio = 3f
        disabled.isEnabled = false

        val gap = LinearLayout.LayoutParams(-1, -2).also { it.topMargin = px(22f) }
        column.addView(MalletText.create(themed, "Media and paragraphs", MalletTextRole.TITLE).also {
            it.setPadding(px(10f), 0, px(10f), px(16f))
        })
        column.addView(card(themed, photo, intro, chinese))
        column.addView(card(themed, placeholder, note, disabled), gap)
        return column
    }

    @Test
    fun `snapshot of media frames and paragraphs`() {
        val light = Snapshots.capture(page(MalletTheme.light()), "media_light", 360)
        val dark = Snapshots.capture(page(MalletTheme.dark()), "media_dark", 360)
        assertThat(light.getPixel(2, 2)).isEqualTo(MalletTheme.light().colors.canvas)
        assertThat(dark.getPixel(2, 2)).isEqualTo(MalletTheme.dark().colors.canvas)
    }
}
