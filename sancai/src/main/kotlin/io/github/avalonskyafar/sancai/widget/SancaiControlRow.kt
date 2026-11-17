package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.BundleCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.roundToInt

/**
 * A settings row: a centred title (and optional detail) beside a control.
 *
 * The row shares its width between the text column and the control by weight. For the common
 * case, a [SancaiSegmentedTrack], [setSegmented] picks the proportions from the option count:
 * two options 2 : 1, three options 1 : 3, four or more 1 : 4. [Layout.UTILITY] is 1 : 1.35
 * whatever the count. Any other view (a switch, a stepper, a slider) goes in through
 * [setControl], or as the single child in XML.
 *
 * The row is 64 dp high, or 72 dp with a detail line, and grows with large text. It is not
 * focusable itself: focus and keys go to the control. The control is announced with the title
 * and detail of the row, unless it brings a content description of its own. Disabling the row
 * disables the control and pales the text.
 *
 * A track created by the row has no id; the row saves and restores its selection, provided the
 * row has an id.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiControlRow
 *     android:id="@+id/strength"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiTitle="Protection"
 *     app:sancaiDetail="How strictly apps are held"
 *     app:sancaiOptions="@array/strength_levels"
 *     app:sancaiSelectedIndex="1" />
 *
 * <io.github.avalonskyafar.sancai.widget.SancaiControlRow
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiTitle="Haptics">
 *     <io.github.avalonskyafar.sancai.widget.SancaiSwitch
 *         android:layout_width="wrap_content"
 *         android:layout_height="wrap_content" />
 * </io.github.avalonskyafar.sancai.widget.SancaiControlRow>
 * ```
 */
public class SancaiControlRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** How the width is shared between the text and a segmented track. */
    public enum class Layout {
        /** By option count: two options 2 : 1, three 1 : 3, four or more 1 : 4. */
        AUTO,

        /** Always 1 : 1.35, for utility settings whose columns should line up. */
        UTILITY,
    }

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val copy = LinearLayout(context)
    private val titleView = SancaiText.create(context, null, SancaiTextRole.SUBHEAD, SancaiTextTone.INK)
    private val detailView = SancaiText.create(context, null, SancaiTextRole.MICRO, SancaiTextTone.MUTED)
    private var segmented = false
    private var appliedDescription: CharSequence? = null
    private var xmlTextWeight = 1f
    private var xmlControlWeight = 0f
    private var ready = false

    /** The main line. Centred in the text column. */
    public var title: CharSequence?
        get() = titleView.text
        set(value) {
            titleView.text = value
            describe()
        }

    /** The supporting line under the title. `null` or blank removes it and lowers the row to 64 dp. */
    public var detail: CharSequence?
        get() = detailView.text.takeIf { it.isNotBlank() }
        set(value) {
            detailView.text = value
            detailView.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
            minimumHeight = dp(if (value.isNullOrBlank()) MIN_HEIGHT_DP else MIN_HEIGHT_DETAIL_DP)
            describe()
        }

    /** The proportions used by [setSegmented]. Changing it re-shares the width of an existing track. */
    public var layout: Layout = Layout.AUTO
        set(value) {
            if (field == value) return
            field = value
            val track = track
            if (segmented && track != null) {
                val (text, control) = weightsFor(track.options.size)
                applyWeights(text, control)
            }
        }

    /** The control of the row, or `null` while there is none. */
    public var control: View? = null
        private set

    /** The control as a segmented track: the one made by [setSegmented], or one that was put in. */
    public val track: SancaiSegmentedTrack? get() = control as? SancaiSegmentedTrack

    /** Weight of the text column. */
    public var textWeight: Float = 1f
        private set

    /** Weight of the control; 0 when the control keeps its own width. */
    public var controlWeight: Float = 0f
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // The control paints its shadow inside its own bounds; this only keeps a sinking
        // or sliding part of it from being cut at the row edge.
        clipChildren = false
        clipToPadding = false

        copy.orientation = VERTICAL
        copy.gravity = Gravity.CENTER
        titleView.gravity = Gravity.CENTER
        detailView.gravity = Gravity.CENTER
        detailView.visibility = View.GONE
        copy.addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        copy.addView(
            detailView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(DETAIL_GAP_DP) },
        )
        super.addView(copy, -1, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        minimumHeight = dp(MIN_HEIGHT_DP)

        var enabledFromXml = true
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiControlRow, defStyleAttr, 0)
            try {
                title = a.getText(R.styleable.SancaiControlRow_sancaiTitle)
                detail = a.getText(R.styleable.SancaiControlRow_sancaiDetail)
                layout = LAYOUTS.getOrElse(a.getInt(R.styleable.SancaiControlRow_sancaiControlRowLayout, 0)) { Layout.AUTO }
                xmlTextWeight = a.getFloat(R.styleable.SancaiControlRow_sancaiControlRowTextWeight, 1f)
                xmlControlWeight = a.getFloat(R.styleable.SancaiControlRow_sancaiControlRowControlWeight, 0f)
                val options = a.getTextArray(R.styleable.SancaiControlRow_sancaiOptions)
                if (options != null) {
                    setSegmented(options.toList(), a.getInt(R.styleable.SancaiControlRow_sancaiSelectedIndex, 0), null)
                }
                enabledFromXml = a.getBoolean(R.styleable.SancaiControlRow_android_enabled, true)
            } finally {
                a.recycle()
            }
        }
        ready = true
        if (!enabledFromXml) isEnabled = false
        describe()
    }

    /**
     * Puts a segmented track into the row and returns it. The width is shared according to
     * [layout] and the number of [options]. A track from an earlier call is reused, so its
     * selection glides to [selected] only when asked to through [SancaiSegmentedTrack.select].
     *
     * [listener] follows the rule of the track: it is called for user input only.
     */
    public fun setSegmented(
        options: List<CharSequence>,
        selected: Int,
        listener: SancaiSegmentedTrack.OnSelectionChangeListener?,
    ): SancaiSegmentedTrack {
        val existing = if (segmented) track else null
        val track = existing ?: SancaiSegmentedTrack(context)
        track.options = options
        track.select(selected, animate = false)
        track.setOnSelectionChangeListener(listener)
        val (text, control) = weightsFor(options.size)
        if (existing == null) {
            install(track, text, control, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            segmented = true
        } else {
            applyWeights(text, control)
        }
        return track
    }

    /**
     * Puts any view into the row as its control, replacing the previous one; `null` leaves the
     * text alone. The width is shared [textWeight] : [controlWeight]. A [controlWeight] of 0
     * (the default) keeps the control at its own width and gives the rest to the text, which
     * suits switches and steppers; a slider wants a weight.
     */
    @JvmOverloads
    public fun setControl(view: View?, textWeight: Float = 1f, controlWeight: Float = 0f) {
        require(textWeight > 0f) { "textWeight must be positive" }
        require(controlWeight >= 0f) { "controlWeight must not be negative" }
        if (view == null) {
            removeControl()
            return
        }
        install(view, textWeight, controlWeight, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    /**
     * A child added from XML or with `addView` becomes the control. Its `layout_weight`, when
     * set, is the control weight. The row holds a single control.
     */
    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
        check(control == null) { "SancaiControlRow holds a single control; use setControl to replace it" }
        val given = params as? LayoutParams
        val weight = if (given != null && given.weight > 0f) given.weight else xmlControlWeight
        install(
            child,
            xmlTextWeight,
            weight,
            params?.width ?: LayoutParams.WRAP_CONTENT,
            params?.height ?: LayoutParams.WRAP_CONTENT,
        )
    }

    private fun install(view: View, textWeight: Float, controlWeight: Float, width: Int, height: Int) {
        removeControl()
        control = view
        super.addView(view, -1, LayoutParams(width, height))
        applyWeights(textWeight, controlWeight)
        if (!isEnabled) view.isEnabled = false
        describe()
    }

    private fun removeControl() {
        val old = control ?: return
        super.removeView(old)
    }

    /** Forgets the control however it left the row, `removeView` and `removeAllViews` included. */
    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (child !== control) return
        if (appliedDescription != null && child.contentDescription == appliedDescription) {
            child.contentDescription = null
        }
        appliedDescription = null
        control = null
        segmented = false
        if (copy.parent === this) applyWeights(1f, 0f)
        describe()
    }

    private fun applyWeights(text: Float, control: Float) {
        textWeight = text
        controlWeight = control
        val view = this.control
        (copy.layoutParams as LayoutParams).apply {
            weight = text
            marginEnd = if (view == null) 0 else dp(TEXT_GAP_DP)
        }
        // A segmented track keeps a deeper shadow gutter below its capsule than above it, so
        // the capsule sits above the middle of the track's view. The text follows the capsule.
        val lift = SancaiSegmentedTrack.GUTTER_BOTTOM_DP - SancaiSegmentedTrack.GUTTER_TOP_DP
        copy.setPadding(0, 0, 0, if (view is SancaiSegmentedTrack) dp(lift) else 0)
        if (view != null) {
            (view.layoutParams as LayoutParams).apply {
                weight = control
                if (control > 0f) width = 0 else if (width == 0) width = LayoutParams.WRAP_CONTENT
            }
        }
        requestLayout()
    }

    private fun weightsFor(optionCount: Int): Pair<Float, Float> = when {
        layout == Layout.UTILITY -> 1f to UTILITY_CONTROL_WEIGHT
        optionCount == 2 -> 2f to 1f
        optionCount == 3 -> 1f to 3f
        optionCount >= 4 -> 1f to 4f
        else -> 1f to 1f
    }

    /**
     * Hands the text of the row to the control, so that a screen reader meets one element
     * ("title, detail", then the state of the control) instead of a label and an unnamed
     * control. A description the control brought itself is left alone.
     */
    private fun describe() {
        if (!ready) return
        val title = title?.takeIf { it.isNotBlank() }
        val detail = detail
        val spoken: CharSequence? = when {
            title != null && detail != null ->
                context.getString(R.string.sancai_control_row_description, title, detail)
            else -> title ?: detail
        }
        val view = control
        if (view == null) {
            copy.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            copy.contentDescription = spoken
            return
        }
        val current = view.contentDescription
        if (current == null || current == appliedDescription) {
            view.contentDescription = spoken
            appliedDescription = spoken
            copy.contentDescription = null
            copy.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            // The control names itself; the text of the row stays readable as its own element.
            copy.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            copy.contentDescription = spoken
        }
    }

    /** Disables or enables the control with the row; the text pales as every disabled control does. */
    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!ready) return
        control?.isEnabled = enabled
        copy.alpha = if (enabled) 1f else SancaiControl.DISABLED_ALPHA / 255f
    }

    override fun getAccessibilityClassName(): CharSequence = SancaiControlRow::class.java.name

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        putInt(STATE_SELECTED, if (segmented) track?.selectedIndex ?: -1 else -1)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        val selected = state.getInt(STATE_SELECTED, -1)
        val track = track
        // Only the row's own track: a control that was put in looks after its own state.
        if (segmented && track != null && selected >= 0) track.select(selected, animate = false)
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    public companion object {
        /** Height of a row without a detail line, in dp. */
        public const val MIN_HEIGHT_DP: Float = 64f

        /** Height of a row with a detail line, in dp. */
        public const val MIN_HEIGHT_DETAIL_DP: Float = 72f

        /** Control weight of [Layout.UTILITY], against a text weight of 1. */
        public const val UTILITY_CONTROL_WEIGHT: Float = 1.35f

        private const val TEXT_GAP_DP = 10f
        private const val DETAIL_GAP_DP = 3f

        private const val STATE_SUPER = "sancai:super"
        private const val STATE_SELECTED = "sancai:selected"
        private val LAYOUTS = Layout.values()
    }
}
