package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import androidx.core.os.BundleCompat
import io.github.avalonskyafar.sancai.R

/**
 * A vertical column of [SancaiRadioButton]s that answers one question: checking one radio
 * unchecks the one that was checked before.
 *
 * The group starts with whatever its children say (the last checked child wins) or with
 * `app:sancaiRadioCheckedButton`. It may start empty; once a radio is checked the user can only
 * move the choice, never remove it. [clearCheck] removes it from code.
 *
 * Only direct children take part. Give every radio an `android:id`: [checkedId] reports it and
 * the saved state is keyed by it. A radio without an id receives a generated one, which does
 * not survive re-creation of the screen.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiRadioGroup
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiRadioCheckedButton="@+id/weekly">
 *
 *     <io.github.avalonskyafar.sancai.widget.SancaiRadioButton
 *         android:id="@+id/daily"
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         android:text="@string/every_day" />
 *
 *     <io.github.avalonskyafar.sancai.widget.SancaiRadioButton
 *         android:id="@+id/weekly"
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         android:text="@string/every_week" />
 * </io.github.avalonskyafar.sancai.widget.SancaiRadioGroup>
 * ```
 */
public class SancaiRadioGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Called when the user moves the choice to another radio of the group. */
    public fun interface OnCheckedChangeListener {
        public fun onCheckedChanged(group: SancaiRadioGroup, checkedId: Int)
    }

    private var current = View.NO_ID
    private var adjusting = false

    /** Listener for user-driven changes of the choice. Not called for [check] or [clearCheck]. */
    public var onCheckedChangeListener: OnCheckedChangeListener? = null

    /** Id of the checked radio, or [View.NO_ID] while nothing is chosen. */
    public val checkedId: Int get() = current

    init {
        orientation = VERTICAL
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiRadioGroup, defStyleAttr, 0)
            try {
                // Applied as soon as the child with this id is added.
                current = a.getResourceId(R.styleable.SancaiRadioGroup_sancaiRadioCheckedButton, View.NO_ID)
            } finally {
                a.recycle()
            }
        }
    }

    /**
     * Moves the choice to the radio with [id] from code; [View.NO_ID] clears it. The listeners
     * of the group and of the radios are not called, which makes this safe for rolling back a
     * change a listener refused. An id that no child has yet is remembered and applied when
     * that child is added.
     */
    @JvmOverloads
    public fun check(id: Int, animate: Boolean = true) {
        if (id == View.NO_ID) {
            clearCheck(animate)
            return
        }
        val target = radio(id)
        if (target != null) {
            // The hook moves `current` and unchecks the previous radio.
            if (target.isChecked) current = id else target.setChecked(true, animate)
            return
        }
        uncheckCurrent(animate, fromUser = false)
        current = id
    }

    /** Leaves no radio checked. Listeners are not called. */
    @JvmOverloads
    public fun clearCheck(animate: Boolean = true) {
        uncheckCurrent(animate, fromUser = false)
        current = View.NO_ID
    }

    private fun radio(id: Int): SancaiRadioButton? {
        if (id == View.NO_ID) return null
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is SancaiRadioButton && child.id == id) return child
        }
        return null
    }

    private fun uncheckCurrent(animate: Boolean, fromUser: Boolean, except: SancaiRadioButton? = null) {
        adjusting = true
        try {
            // Every other checked radio, not just `current`: ids may repeat or change.
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child is SancaiRadioButton && child !== except && child.isChecked) {
                    child.setCheckedFromGroup(false, animate, fromUser)
                    // A sibling cannot refuse to give the choice up: its listener was told,
                    // and a re-check from inside it is undone without a second call.
                    if (child.isChecked) child.setCheckedFromGroup(false, animate, fromUser = false)
                }
            }
        } finally {
            adjusting = false
        }
    }

    private val hook = { button: SancaiRadioButton, checked: Boolean, animate: Boolean, fromUser: Boolean ->
        if (!adjusting) {
            if (checked) {
                uncheckCurrent(animate, fromUser, except = button)
                current = button.id
                if (fromUser) onCheckedChangeListener?.onCheckedChanged(this, current)
            } else if (button.id == current) {
                current = View.NO_ID
            }
        }
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (child !is SancaiRadioButton) return
        if (child.id == View.NO_ID) child.id = View.generateViewId()
        child.groupHook = hook
        if (child.isChecked) {
            uncheckCurrent(animate = false, fromUser = false, except = child)
            current = child.id
        } else if (child.id == current) {
            child.setChecked(true, animate = false)
        }
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (child !is SancaiRadioButton) return
        child.groupHook = null
        if (child.id == current) current = View.NO_ID
    }

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        putInt(STATE_CHECKED_ID, current)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        check(state.getInt(STATE_CHECKED_ID, current), animate = false)
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.RadioGroup"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        var radios = 0
        for (i in 0 until childCount) if (getChildAt(i) is SancaiRadioButton) radios++
        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
            radios,
            1,
            false,
            AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE,
        )
    }

    private companion object {
        private const val STATE_SUPER = "sancai:super"
        private const val STATE_CHECKED_ID = "sancai:checkedId"
    }
}
