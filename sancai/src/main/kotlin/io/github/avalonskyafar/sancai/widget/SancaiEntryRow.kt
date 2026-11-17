package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconView
import io.github.avalonskyafar.sancai.interaction.SancaiHaptics
import io.github.avalonskyafar.sancai.interaction.SancaiPress
import io.github.avalonskyafar.sancai.internal.useOwnFocusRim
import io.github.avalonskyafar.sancai.material.SancaiMaterial
import io.github.avalonskyafar.sancai.theme.SancaiMotion
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A row of a card that leads somewhere: a sub-page, a picker, a dialog.
 *
 * The title, and the optional detail line under it, are centred in the row. Everything else
 * is positioned on its own at an edge and never takes width from one side only, so the text
 * stays on the centre line of the row whatever surrounds it. (Only when the edges are so
 * crowded that a one-line text would no longer fit in less than half the row does the text
 * give up the centre line and use all the room between them.)
 *
 * - [arrow]: a chevron at the end (muted, "this opens something") or at the start (ink, "the
 *   destination lies on that side").
 * - [leadingIcon]: an icon at the start.
 * - [value]: a short muted value at the end, before the end chevron, for example the current
 *   choice of the sub-page behind the row.
 *
 * With [Alignment.START] the text is aligned to the start instead, for long, list-like
 * content.
 *
 * Pressing sinks the text, icon and chevron by 1.5 dp; the row itself, and the card behind
 * it, stay still and nothing changes colour. Keyboard focus draws an ink rim inside the row.
 * A disabled row pales. The row is 62 dp high, 76 dp with a detail line, and grows with
 * large text. In right-to-left layouts start and end swap and the chevrons turn around.
 *
 * The row is announced as one element, "title, value, detail", unless a content description
 * is set explicitly. It keeps no state of its own, so there is nothing to save or restore.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiEntryRow
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiTitle="Language"
 *     app:sancaiDetail="Used for menus and messages"
 *     app:sancaiEntryValue="English"
 *     app:sancaiEntryArrow="end" />
 * ```
 */
public class SancaiEntryRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Where the chevron of the row sits. */
    public enum class Arrow { NONE, END, START }

    /** How the title and the detail are aligned in the row. */
    public enum class Alignment { CENTER, START }

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val material = SancaiMaterial(context, theme)
    private val density = resources.displayMetrics.density
    private val focusBounds = RectF()

    private val titleView = SancaiText.create(context, null, SancaiTextRole.SUBHEAD)
    private val detailView = SancaiText.create(context, null, SancaiTextRole.MICRO, SancaiTextTone.MUTED)
    private val valueView = SancaiText.create(context, null, SancaiTextRole.BODY_COMPACT, SancaiTextTone.MUTED)
    private val iconView = SancaiIconView(context)
    private val chevronView = SancaiIconView(context)
    private val content = Content(context)

    private var userClick: OnClickListener? = null
    private var installingPress = false

    // Set by android:onClick while the super constructor runs, before any initialiser of
    // this class; a lateinit property is the only kind that keeps a value from that time.
    private lateinit var declaredClick: OnClickListener
    private var constructed = false
    private var ownDescription = true
    private var settingDescription = false

    /** Main line of the row. */
    public var title: CharSequence? = null
        set(value) {
            field = value
            titleView.text = value
            refresh()
        }

    /** Supporting line under the title; `null` or blank removes it and lowers the row. */
    public var detail: CharSequence? = null
        set(value) {
            field = value
            detailView.text = value
            detailView.visibility = if (value.isNullOrBlank()) GONE else VISIBLE
            refresh()
        }

    /**
     * Short value at the end of the row, before the end chevron; `null` or blank removes it.
     * It is kept to one line and is cut with an ellipsis rather than pushing the title.
     */
    public var value: CharSequence? = null
        set(value) {
            field = value
            valueView.text = value
            valueView.visibility = if (value.isNullOrBlank()) GONE else VISIBLE
            refresh()
        }

    /** Chevron of the row. It is directional: in right-to-left layouts it changes side and turns around. */
    public var arrow: Arrow = Arrow.NONE
        set(value) {
            field = value
            chevronView.visibility = if (value == Arrow.NONE) GONE else VISIBLE
            chevronView.icon = if (value == Arrow.START) SancaiIcon.CHEVRON_LEFT else SancaiIcon.CHEVRON_RIGHT
            chevronView.tone = if (value == Arrow.START) SancaiTextTone.INK else SancaiTextTone.MUTED
            refresh()
        }

    /** Whether the text is centred in the row (the default) or aligned to its start. */
    public var alignment: Alignment = Alignment.CENTER
        set(value) {
            field = value
            alignText(titleView, value)
            alignText(detailView, value)
            refresh()
        }

    /** Muted icon at the start of the row, or `null`. */
    public var leadingIcon: SancaiIcon? = null
        set(value) {
            field = value
            iconView.icon = value
            iconView.visibility = if (value == null) GONE else VISIBLE
            refresh()
        }

    init {
        // An explicit android:contentDescription was applied by the super constructor.
        ownDescription = contentDescription == null
        setWillNotDraw(false)
        // The row draws its own focus rim.
        useOwnFocusRim()
        valueView.maxLines = 1
        valueView.ellipsize = TextUtils.TruncateAt.END
        alignText(titleView, Alignment.CENTER)
        alignText(detailView, Alignment.CENTER)
        detailView.visibility = GONE
        valueView.visibility = GONE
        iconView.visibility = GONE
        chevronView.visibility = GONE
        content.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))

        installingPress = true
        SancaiPress.attach(this, content)
        installingPress = false
        // An action: the tick confirms the press at once, then the listener runs.
        super.setOnClickListener { view ->
            SancaiHaptics.tick(view)
            userClick?.onClick(view)
        }
        if (::declaredClick.isInitialized) userClick = declaredClick

        // The platform reads android:enabled for text views only.
        var enabledFromXml = true
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiEntryRow, defStyleAttr, 0)
            try {
                title = a.getText(R.styleable.SancaiEntryRow_sancaiTitle)
                detail = a.getText(R.styleable.SancaiEntryRow_sancaiDetail)
                value = a.getText(R.styleable.SancaiEntryRow_sancaiEntryValue)
                leadingIcon = SancaiIcon.fromAttr(a.getInt(R.styleable.SancaiEntryRow_sancaiIcon, -1))
                arrow = ARROWS.getOrElse(a.getInt(R.styleable.SancaiEntryRow_sancaiEntryArrow, 0)) { Arrow.NONE }
                alignment = ALIGNMENTS.getOrElse(a.getInt(R.styleable.SancaiEntryRow_sancaiEntryAlignment, 0)) { Alignment.CENTER }
                enabledFromXml = a.getBoolean(R.styleable.SancaiEntryRow_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        constructed = true
        if (!enabledFromXml) isEnabled = false
        refresh()
    }

    /**
     * Gravity serves apps that do not declare right-to-left support, where the platform
     * ignores text alignment; the view-relative text alignment makes Latin text in a
     * right-to-left row still begin at the start.
     */
    private fun alignText(view: SancaiText, alignment: Alignment) {
        view.gravity = if (alignment == Alignment.CENTER) Gravity.CENTER_HORIZONTAL else Gravity.START
        view.textAlignment = if (alignment == Alignment.CENTER) TEXT_ALIGNMENT_CENTER else TEXT_ALIGNMENT_VIEW_START
    }

    private fun refresh() {
        content.requestLayout()
        if (!ownDescription) return
        val separator = resources.getString(R.string.sancai_entry_row_description_separator)
        val parts = listOf(title, value, detail).filter { !it.isNullOrBlank() }
        settingDescription = true
        contentDescription = if (parts.isEmpty()) null else parts.joinToString(separator)
        settingDescription = false
    }

    /**
     * Replaces the generated "title, value, detail" description. Pass `null` to go back to
     * the generated one.
     */
    override fun setContentDescription(contentDescription: CharSequence?) {
        if (settingDescription) {
            super.setContentDescription(contentDescription)
            return
        }
        ownDescription = contentDescription == null
        if (ownDescription) refresh() else super.setContentDescription(contentDescription)
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"

    // ---------------------------------------------------------------------------------------
    // Press, focus, disabled
    // ---------------------------------------------------------------------------------------

    /**
     * Sets what a tap, Enter / Space or the accessibility click does. The content is back
     * in place and the row has ticked before [listener] runs.
     */
    override fun setOnClickListener(listener: OnClickListener?) {
        when {
            installingPress -> Unit
            constructed -> userClick = listener
            listener != null -> declaredClick = listener
        }
    }

    override fun dispatchSetPressed(pressed: Boolean) {
        super.dispatchSetPressed(pressed)
        // Touch presses sink on touch-down already; this is the keyboard press.
        if (pressed && !isInTouchMode && isEnabled) {
            SancaiPress.sink(content, SancaiMotion.PRESS_SINK_DP * density)
        } else if (!pressed) {
            SancaiPress.release(content)
        }
    }

    // Space confirms like Enter. The platform only does this by itself on newer versions.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_SPACE) return super.onKeyDown(keyCode, event)
        if (!isEnabled) return false
        if (event.repeatCount == 0) isPressed = true
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_SPACE) return super.onKeyUp(keyCode, event)
        if (!isEnabled || !isPressed) return false
        isPressed = false
        performClick()
        return true
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        content.alpha = if (enabled) 1f else SancaiControl.DISABLED_ALPHA / 255f
        if (!enabled) SancaiPress.release(content)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        SancaiPress.release(content)
        super.onDetachedFromWindow()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isFocused && hasWindowFocus() && !isInTouchMode) {
            focusBounds.set(0f, 0f, width.toFloat(), height.toFloat())
            material.drawFocusRim(canvas, focusBounds, SancaiMaterial.RADIUS_CARD_DP * density)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Content: the part that sinks. Text first, edge items positioned on their own.
    // ---------------------------------------------------------------------------------------

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private inner class Content(context: Context) : ViewGroup(context) {

        private var textStart = 0
        private var textWidth = 0

        init {
            clipChildren = false
            addView(titleView)
            addView(detailView)
            addView(valueView)
            addView(iconView)
            addView(chevronView)
        }

        private fun shown(view: View): Boolean = view.visibility != GONE

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val glyph = MeasureSpec.makeMeasureSpec(px(GLYPH_DP), MeasureSpec.EXACTLY)
            val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            iconView.measure(glyph, glyph)
            chevronView.measure(glyph, glyph)

            // Width taken at each edge by the items that sit there.
            var startReserve = 0
            if (arrow == Arrow.START) startReserve = px(EDGE_DP + GLYPH_DP)
            if (shown(iconView)) startReserve += px((if (startReserve == 0) EDGE_DP else ICON_GAP_DP) + GLYPH_DP)
            var endReserve = if (arrow == Arrow.END) px(EDGE_DP + GLYPH_DP) else 0
            if (shown(valueView)) {
                val share = if (alignment == Alignment.CENTER) VALUE_SHARE_CENTER else VALUE_SHARE_START
                valueView.measure(MeasureSpec.makeMeasureSpec((width * share).toInt(), MeasureSpec.AT_MOST), free)
                endReserve += valueView.measuredWidth + px(if (endReserve == 0) TEXT_PADDING_DP else VALUE_GAP_DP)
            }

            // The wider edge is mirrored onto the other side, so the text column stays
            // centred on the row whatever sits at the edges.
            val reserve = max(startReserve, endReserve)
            val side = if (reserve == 0) px(TEXT_PADDING_DP) else reserve + px(TEXT_GAP_DP)
            val mirrored = width - 2 * side
            if (alignment == Alignment.CENTER && (mirrored >= width / 2 || mirrored >= naturalTextWidth(free))) {
                textStart = side
                textWidth = max(0, mirrored)
            } else {
                // Start aligned, or too crowded to mirror without squeezing the text: the
                // text takes all the room between the edge items.
                textStart = if (startReserve == 0) px(TEXT_PADDING_DP) else startReserve + px(TEXT_GAP_DP)
                val end = if (endReserve == 0) px(TEXT_PADDING_DP) else endReserve + px(TEXT_GAP_DP)
                textWidth = max(0, width - textStart - end)
            }
            val textSpec = MeasureSpec.makeMeasureSpec(textWidth, MeasureSpec.EXACTLY)
            titleView.measure(textSpec, free)
            var textHeight = titleView.measuredHeight
            if (shown(detailView)) {
                detailView.measure(textSpec, free)
                textHeight += px(DETAIL_GAP_DP) + detailView.measuredHeight
            }
            val minHeight = px(if (shown(detailView)) MIN_HEIGHT_DETAIL_DP else MIN_HEIGHT_DP)
            setMeasuredDimension(width, max(minHeight, textHeight + 2 * px(VERTICAL_PADDING_DP)))
        }

        /** Width the title and the detail would like on one line each. */
        private fun naturalTextWidth(free: Int): Int {
            titleView.measure(free, free)
            if (!shown(detailView)) return titleView.measuredWidth
            detailView.measure(free, free)
            return max(titleView.measuredWidth, detailView.measuredWidth)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val height = bottom - top
            var textHeight = titleView.measuredHeight
            if (shown(detailView)) textHeight += px(DETAIL_GAP_DP) + detailView.measuredHeight
            var y = (height - textHeight) / 2
            place(titleView, textStart, y)
            if (shown(detailView)) {
                y += titleView.measuredHeight + px(DETAIL_GAP_DP)
                place(detailView, textStart, y)
            }

            var start = px(EDGE_DP)
            if (arrow == Arrow.START) {
                place(chevronView, start, (height - chevronView.measuredHeight) / 2)
                start += px(GLYPH_DP + ICON_GAP_DP)
            }
            if (shown(iconView)) place(iconView, start, (height - iconView.measuredHeight) / 2)

            var end = px(TEXT_PADDING_DP)
            if (arrow == Arrow.END) {
                val x = (right - left) - px(EDGE_DP) - chevronView.measuredWidth
                place(chevronView, x, (height - chevronView.measuredHeight) / 2)
                end = px(EDGE_DP + GLYPH_DP + VALUE_GAP_DP)
            }
            if (shown(valueView)) {
                val x = (right - left) - end - valueView.measuredWidth
                place(valueView, x, (height - valueView.measuredHeight) / 2)
            }
        }

        /** Lays [child] out [start] pixels from the start edge, mirrored in right-to-left. */
        private fun place(child: View, start: Int, top: Int) {
            val x = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - start - child.measuredWidth else start
            child.layout(x, top, x + child.measuredWidth, top + child.measuredHeight)
        }
    }

    public companion object {
        /** Height of a row without a detail line, in dp. */
        public const val MIN_HEIGHT_DP: Float = 62f

        /** Height of a row with a detail line, in dp. */
        public const val MIN_HEIGHT_DETAIL_DP: Float = 76f

        private const val DETAIL_GAP_DP = 4f
        private const val GLYPH_DP = 24f
        private const val EDGE_DP = 12f
        private const val TEXT_PADDING_DP = 14f
        private const val TEXT_GAP_DP = 8f
        private const val ICON_GAP_DP = 6f
        private const val VALUE_GAP_DP = 2f
        private const val VERTICAL_PADDING_DP = 10f
        private const val VALUE_SHARE_CENTER = 0.28f
        private const val VALUE_SHARE_START = 0.4f
        private val ARROWS = Arrow.values()
        private val ALIGNMENTS = Alignment.values()
    }
}
