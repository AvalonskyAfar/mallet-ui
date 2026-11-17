package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.icon.SancaiIcon
import io.github.avalonskyafar.sancai.icon.SancaiIconView
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme
import kotlin.math.roundToInt

/**
 * A flat sequence of steps: equal-width columns, each a centred title over a centred, muted
 * detail, with an accent arrow between neighbours. It shows an order without turning the
 * steps into cards or controls.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiFlowRow
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiFlowRowTitles="@array/flow_titles"
 *     app:sancaiFlowRowDetails="@array/flow_details" />
 * ```
 *
 * The columns share the width that the 28 dp arrow slots leave over, so every step is exactly
 * as wide as the others whatever its text; long text wraps inside its column. In right-to-left
 * layouts the first step is on the right and the arrows point left. A screen reader meets one
 * element per step, in order, and is told the position of each ("2 of 3"); the arrows are
 * decoration. The row is for reading: it takes no clicks and no keyboard focus.
 */
public class SancaiFlowRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** One step of a [SancaiFlowRow]: a short [title] and an optional [detail] below it. */
    public data class Step @JvmOverloads constructor(
        /** The name of the step, one or two words. */
        val title: CharSequence,
        /** A supporting line under the title; `null` or empty leaves it out. */
        val detail: CharSequence? = null,
    )

    private val theme: SancaiTheme = SancaiTheme.of(context)
    private val density: Float = resources.displayMetrics.density

    /**
     * The steps shown, in reading order. Each becomes one column; an arrow is placed between
     * neighbours, so a single step shows no arrow and an empty list shows nothing.
     */
    public var steps: List<Step> = emptyList()
        set(value) {
            field = value.toList()
            rebuild()
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isBaselineAligned = false
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiFlowRow, defStyleAttr, 0)
            try {
                val titles = a.getTextArray(R.styleable.SancaiFlowRow_sancaiFlowRowTitles)
                val details = a.getTextArray(R.styleable.SancaiFlowRow_sancaiFlowRowDetails)
                if (titles != null) {
                    steps = titles.mapIndexed { index, title -> Step(title, details?.getOrNull(index)) }
                }
            } finally {
                a.recycle()
            }
        }
    }

    private fun rebuild() {
        removeAllViews()
        steps.forEachIndexed { index, step ->
            if (index > 0) addView(createArrow(), LayoutParams(dpInt(ARROW_SLOT_DP), dpInt(ARROW_SIZE_DP)))
            addView(createStep(step, index), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Convenience for code: `setSteps("Read" to "this screen", "Decide" to "on the device")`. */
    public fun setSteps(vararg steps: Pair<CharSequence, CharSequence?>) {
        this.steps = steps.map { Step(it.first, it.second) }
    }

    override fun getAccessibilityClassName(): CharSequence = SancaiFlowRow::class.java.name

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(1, steps.size, false)
    }

    private fun createArrow(): View = SancaiIconView(context).apply {
        icon = SancaiIcon.ARROW_RIGHT
        tone = SancaiTextTone.ACCENT
        val side = (dpInt(ARROW_SLOT_DP) - dpInt(ARROW_SIZE_DP)) / 2
        setPadding(side, 0, side, 0)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun createStep(step: Step, index: Int): View {
        val column = LinearLayout(context)
        column.orientation = VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL
        val title = SancaiText.create(context, step.title, SancaiTextRole.SUBHEAD, SancaiTextTone.INK)
        title.gravity = Gravity.CENTER
        title.textAlignment = View.TEXT_ALIGNMENT_CENTER
        column.addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (!step.detail.isNullOrEmpty()) {
            val detail = SancaiText.create(context, step.detail, SancaiTextRole.FOOTNOTE, SancaiTextTone.MUTED)
            detail.gravity = Gravity.CENTER
            detail.textAlignment = View.TEXT_ALIGNMENT_CENTER
            val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            params.topMargin = dpInt(DETAIL_GAP_DP)
            detail.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            column.addView(detail, params)
        }
        // The column speaks for its two lines, so they are not announced a second time.
        title.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        column.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        ViewCompat.setScreenReaderFocusable(column, true)
        column.contentDescription = if (step.detail.isNullOrEmpty()) {
            step.title
        } else {
            context.getString(R.string.sancai_flow_row_step, step.title, step.detail)
        }
        ViewCompat.setAccessibilityDelegate(column, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.setCollectionItemInfo(
                    AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(0, 1, index, 1, false),
                )
            }
        })
        return column
    }

    private fun dpInt(value: Float): Int = (value * density).roundToInt()
}

private const val ARROW_SLOT_DP = 28f
private const val ARROW_SIZE_DP = 18f
private const val DETAIL_GAP_DP = 4f
