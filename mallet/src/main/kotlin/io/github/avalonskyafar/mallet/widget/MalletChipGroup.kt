package io.github.avalonskyafar.mallet.widget

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.os.BundleCompat
import io.github.avalonskyafar.mallet.R
import kotlin.math.roundToInt

/**
 * A wrapping row of [MalletChip]s: chips flow from the start edge and continue on the next
 * line when the width runs out. Right-to-left layouts flow from the right.
 *
 * Gaps are measured between the visible capsules, 8 dp by default in both directions. A chip
 * view is larger than its capsule (touch target and room for the shadow), so neighbouring chip
 * views sit closer than the gap and rows overlap slightly; the capsules never do. The first
 * capsule of a row is inset from the group's edge by the chip's 2 dp shadow gutter.
 *
 * With [isSingleSelection] at most one filter chip is checked at a time: checking one unchecks
 * the others. [isSelectionRequired] also keeps the user from unchecking the last checked chip.
 * [setOnCheckedStateChangeListener] is called for user input only, never for [check],
 * [clearCheck] or [MalletChip.setChecked].
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.widget.MalletChipGroup
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:malletChipGroupSingleSelection="true"
 *     app:malletChipGroupCheckedChip="@id/chip_all">
 *
 *     <io.github.avalonskyafar.mallet.widget.MalletChip
 *         android:id="@+id/chip_all"
 *         android:layout_width="wrap_content"
 *         android:layout_height="wrap_content"
 *         android:text="All"
 *         app:malletChipKind="filter" />
 * </io.github.avalonskyafar.mallet.widget.MalletChipGroup>
 * ```
 */
public class MalletChipGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    /** Called when the user changes which chips of the group are checked. */
    public fun interface OnCheckedStateChangeListener {
        public fun onCheckedStateChanged(group: MalletChipGroup, checkedIds: List<Int>)
    }

    private val density = resources.displayMetrics.density
    private val visible = ArrayList<View>()
    private var adjusting = false
    private var pendingId = View.NO_ID
    private var widest = 0

    private var checkedStateChangeListener: OnCheckedStateChangeListener? = null

    /** Listener for user-driven changes of the checked chips. */
    public fun setOnCheckedStateChangeListener(listener: OnCheckedStateChangeListener?) {
        checkedStateChangeListener = listener
    }

    /**
     * Whether at most one chip can be checked. Switching it on keeps the first checked chip
     * and unchecks the rest, without calling listeners.
     */
    public var isSingleSelection: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) keepOnly(firstChecked(), animate = false, fromUser = false)
        }

    /** Whether the user is kept from unchecking the last checked chip. */
    public var isSelectionRequired: Boolean = false

    /** Gap between the capsules of neighbouring chips in a row, in pixels. */
    public var horizontalGap: Int = (GAP_DP * density).roundToInt()
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /** Gap between the capsules of neighbouring rows, in pixels. */
    public var verticalGap: Int = (GAP_DP * density).roundToInt()
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /** Ids of the checked chips, in child order. */
    public val checkedChipIds: List<Int>
        get() {
            val ids = ArrayList<Int>()
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child is MalletChip && child.isChecked) ids.add(child.id)
            }
            return ids
        }

    /** Id of the first checked chip, or [View.NO_ID] while none is checked. */
    public val checkedChipId: Int get() = firstChecked()?.id ?: View.NO_ID

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletChipGroup, defStyleAttr, 0)
            try {
                isSingleSelection = a.getBoolean(R.styleable.MalletChipGroup_malletChipGroupSingleSelection, false)
                isSelectionRequired = a.getBoolean(R.styleable.MalletChipGroup_malletChipGroupSelectionRequired, false)
                horizontalGap = a.getDimensionPixelSize(R.styleable.MalletChipGroup_malletChipGroupHorizontalGap, horizontalGap)
                verticalGap = a.getDimensionPixelSize(R.styleable.MalletChipGroup_malletChipGroupVerticalGap, verticalGap)
                // Applied as soon as the child with this id is added.
                pendingId = a.getResourceId(R.styleable.MalletChipGroup_malletChipGroupCheckedChip, View.NO_ID)
            } finally {
                a.recycle()
            }
        }
    }

    /**
     * Checks the chip with [id] from code; listeners are not called. An id that no child has
     * yet is remembered and applied when that child is added.
     */
    @JvmOverloads
    public fun check(id: Int, animate: Boolean = true) {
        if (id == View.NO_ID) return
        val target = chip(id)
        if (target == null) pendingId = id else target.setChecked(true, animate)
    }

    /** Leaves no chip checked. Listeners are not called. */
    @JvmOverloads
    public fun clearCheck(animate: Boolean = true) {
        pendingId = View.NO_ID
        keepOnly(null, animate, fromUser = false)
    }

    private fun chip(id: Int): MalletChip? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is MalletChip && child.id == id) return child
        }
        return null
    }

    private fun firstChecked(): MalletChip? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is MalletChip && child.isChecked) return child
        }
        return null
    }

    /** Unchecks every chip but [keep]. */
    private fun keepOnly(keep: MalletChip?, animate: Boolean, fromUser: Boolean) {
        adjusting = true
        try {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child is MalletChip && child !== keep && child.isChecked) {
                    child.applyChecked(false, animate, fromUser)
                    // A sibling cannot refuse to give the choice up: its listener was told,
                    // and a re-check from inside it is undone without a second call.
                    if (child.isChecked) child.applyChecked(false, animate, fromUser = false)
                }
            }
        } finally {
            adjusting = false
        }
    }

    /** Called by a chip of this group after its checked state changed. */
    internal fun onChipChecked(chip: MalletChip, checked: Boolean, animate: Boolean, fromUser: Boolean) {
        if (adjusting) return
        if (checked && isSingleSelection) keepOnly(chip, animate, fromUser)
        if (fromUser) checkedStateChangeListener?.onCheckedStateChanged(this, checkedChipIds)
    }

    /** Whether the user may uncheck [chip] right now. */
    internal fun mayUncheck(chip: MalletChip): Boolean {
        if (!isSelectionRequired) return true
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is MalletChip && child !== chip && child.isChecked) return true
        }
        return false
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (child !is MalletChip) return
        if (child.id == View.NO_ID) child.id = View.generateViewId()
        child.group = this
        if (child.id == pendingId) {
            pendingId = View.NO_ID
            child.setChecked(true, animate = false)
        } else if (child.isChecked && isSingleSelection) {
            // The chip added last wins, as with radios.
            keepOnly(child, animate = false, fromUser = false)
        }
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (child is MalletChip) child.group = null
    }

    // ---------------------------------------------------------------------------------------
    // Flow layout
    // ---------------------------------------------------------------------------------------

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = LayoutParams(context, attrs)

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = LayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p != null

    override fun shouldDelayChildPressedState(): Boolean = false

    private fun insetX(child: View): Int = if (child is MalletChip) child.bodyInsetX() else 0

    private fun insetY(child: View): Int = if (child is MalletChip) child.bodyInsetY() else 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val limit = if (widthMode == MeasureSpec.UNSPECIFIED) {
            Int.MAX_VALUE
        } else {
            maxOf(0, MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
        }
        val childWidth = if (widthMode == MeasureSpec.UNSPECIFIED) {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        } else {
            MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST)
        }
        val childHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        visible.clear()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val params = child.layoutParams
            child.measure(
                getChildMeasureSpec(childWidth, 0, params.width),
                getChildMeasureSpec(childHeight, 0, params.height),
            )
            visible.add(child)
        }
        val height = arrange(limit, place = false, fullWidth = 0)
        setMeasuredDimension(
            resolveSize(maxOf(widest + paddingLeft + paddingRight, suggestedMinimumWidth), widthMeasureSpec),
            resolveSize(maxOf(height, suggestedMinimumHeight), heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        arrange(maxOf(0, r - l - paddingLeft - paddingRight), place = true, fullWidth = r - l)
    }

    /**
     * Breaks the measured children of [visible] into rows no wider than [limit] and, with
     * [place], lays them out. Returns the height the group needs and leaves the width of the
     * widest row in [widest].
     */
    private fun arrange(limit: Int, place: Boolean, fullWidth: Int): Int {
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val count = visible.size
        widest = 0
        var bottom = paddingTop
        var previousInset = 0
        var start = 0
        while (start < count) {
            // Gather one row.
            var end = start
            var rowWidth = 0
            var rowHeight = 0
            while (end < count) {
                val child = visible[end]
                val advance = if (end == start) 0 else horizontalGap - insetX(visible[end - 1]) - insetX(child)
                if (end > start && rowWidth + advance + child.measuredWidth > limit) break
                rowWidth += advance + child.measuredWidth
                rowHeight = maxOf(rowHeight, child.measuredHeight)
                end++
            }
            // How far the capsules of this row lie inside the row's own top and bottom.
            var rowInset = Int.MAX_VALUE
            for (i in start until end) {
                val child = visible[i]
                rowInset = minOf(rowInset, (rowHeight - child.measuredHeight) / 2 + insetY(child))
            }
            val top = if (start == 0) bottom else bottom + verticalGap - previousInset - rowInset
            if (place) {
                var cursor = 0
                for (i in start until end) {
                    val child = visible[i]
                    if (i > start) cursor += horizontalGap - insetX(visible[i - 1]) - insetX(child)
                    val width = child.measuredWidth
                    val left = if (rtl) fullWidth - paddingRight - cursor - width else paddingLeft + cursor
                    val childTop = top + (rowHeight - child.measuredHeight) / 2
                    child.layout(left, childTop, left + width, childTop + child.measuredHeight)
                    cursor += width
                }
            }
            widest = maxOf(widest, rowWidth)
            bottom = top + rowHeight
            previousInset = rowInset
            start = end
        }
        return bottom + paddingBottom
    }

    // ---------------------------------------------------------------------------------------
    // State and accessibility
    // ---------------------------------------------------------------------------------------

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        // By position, so that chips created in code without stable ids are restored too.
        putBooleanArray(STATE_CHECKED, BooleanArray(childCount) { (getChildAt(it) as? MalletChip)?.isChecked == true })
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        val checked = state.getBooleanArray(STATE_CHECKED) ?: return
        if (checked.size != childCount) return
        pendingId = View.NO_ID
        for (i in checked.indices) (getChildAt(i) as? MalletChip)?.setChecked(checked[i], animate = false)
    }

    override fun getAccessibilityClassName(): CharSequence = MalletChipGroup::class.java.name

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        var chips = 0
        for (i in 0 until childCount) if (getChildAt(i) is MalletChip) chips++
        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
            1,
            chips,
            false,
            if (isSingleSelection) {
                AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE
            } else {
                AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_MULTIPLE
            },
        )
    }

    private companion object {
        private const val GAP_DP = 8f
        private const val STATE_SUPER = "mallet:super"
        private const val STATE_CHECKED = "mallet:chipsChecked"
    }
}
