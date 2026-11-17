package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiWeight
import kotlin.math.roundToInt

/**
 * A read-only card of facts: keys down the start side, their values down the end side, a
 * divider from rim to rim between the rows.
 *
 * The key column and the value column keep the same widths in every row (1.22 : 1), so values
 * line up however long the texts are; long texts wrap inside their column. A value can carry a
 * tone: ink by default, [SancaiTextTone.SUCCESS] and [SancaiTextTone.DANGER] for good and bad
 * states. A `null` value is shown as an em dash.
 *
 * Rows cannot be clicked or focused with the keyboard and show no press feedback. For a
 * screen reader each row is one element, "key, value".
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiKeyValueList
 *     android:id="@+id/diagnostics"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 * ```
 * ```kotlin
 * diagnostics.addItem("Service", "Running", SancaiTextTone.SUCCESS)
 * diagnostics.addItem("Last check", null)
 * ```
 */
public class SancaiKeyValueList @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : SancaiSurface(context, attrs, defStyleAttr) {

    /** One fact: a [key], its [value] (`null` shows an em dash) and the [tone] of the value. */
    public data class Item @JvmOverloads constructor(
        val key: CharSequence,
        val value: CharSequence?,
        val tone: SancaiTextTone = SancaiTextTone.INK,
    )

    private val entries = ArrayList<Item>()

    /** The facts shown, in order. */
    public val items: List<Item> get() = entries.toList()

    init {
        orientation = VERTICAL
    }

    /** Replaces all rows. */
    public fun setItems(items: List<Item>) {
        removeAllViews()
        entries.clear()
        items.forEach { append(it) }
    }

    /** Appends one row, with a divider above it when it is not the first. */
    @JvmOverloads
    public fun addItem(key: CharSequence, value: CharSequence?, tone: SancaiTextTone = SancaiTextTone.INK) {
        append(Item(key, value, tone))
    }

    /** Removes all rows. */
    public fun clearItems() {
        removeAllViews()
        entries.clear()
    }

    private fun append(item: Item) {
        if (entries.isNotEmpty()) addDivider()
        entries.add(item)

        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = dp(ROW_MIN_HEIGHT_DP)
        row.setPaddingRelative(dp(ROW_PADDING_H_DP), dp(ROW_PADDING_V_DP), dp(ROW_PADDING_H_DP), dp(ROW_PADDING_V_DP))

        val key = cell(item.key, SancaiWeight.REGULAR, SancaiTextTone.INK)
        key.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        key.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        row.addView(
            key,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, KEY_WEIGHT).apply { marginEnd = dp(COLUMN_GAP_DP) },
        )

        val shown = item.value ?: context.getString(R.string.sancai_control_row_no_value)
        val value = cell(shown, SancaiWeight.MEDIUM, item.tone)
        value.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        value.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        row.addView(value, LayoutParams(0, LayoutParams.WRAP_CONTENT, VALUE_WEIGHT))

        // One element per row; the two texts are not visited separately.
        key.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        value.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        row.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        row.contentDescription = context.getString(
            R.string.sancai_control_row_description,
            item.key,
            item.value ?: context.getString(R.string.sancai_control_row_no_value_spoken),
        )
        ViewCompat.setScreenReaderFocusable(row, true)
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun cell(text: CharSequence, weight: SancaiWeight, tone: SancaiTextTone): SancaiText {
        val view = SancaiText.create(context, text, SancaiTextRole.FOOTNOTE, tone)
        view.typeface = weight.typeface()
        return view
    }

    override fun getAccessibilityClassName(): CharSequence = SancaiKeyValueList::class.java.name

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    public companion object {
        /** Weight of the key column. */
        public const val KEY_WEIGHT: Float = 1.22f

        /** Weight of the value column. */
        public const val VALUE_WEIGHT: Float = 1f

        private const val ROW_MIN_HEIGHT_DP = 52f
        private const val ROW_PADDING_H_DP = 12f
        private const val ROW_PADDING_V_DP = 8f
        private const val COLUMN_GAP_DP = 10f
    }
}
