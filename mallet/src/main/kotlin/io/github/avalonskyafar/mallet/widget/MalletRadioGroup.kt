package io.github.avalonskyafar.mallet.widget

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import androidx.core.os.BundleCompat
import io.github.avalonskyafar.mallet.R

/**
 * A vertical column of [MalletRadioButton]s that answers one question: checking one radio
 * unchecks the one that was checked before.
 *
 * The group starts with whatever its children say (the last checked child wins) or with
 * `app:malletRadioCheckedButton`. It may start empty; once a radio is checked the user can only
 * move the choice, never remove it. [clearCheck] removes it from code.
 *
 * Only direct children take part. Give every radio an `android:id`: [checkedId] reports it and
 * the saved state is keyed by it. A radio without an id receives a generated one, which does
 * not survive re-creation of the screen: the group then keeps the choice it was built with.
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.widget.MalletRadioGroup
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:malletRadioCheckedButton="@+id/weekly">
 *
 *     <io.github.avalonskyafar.mallet.widget.MalletRadioButton
 *         android:id="@+id/daily"
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         android:text="@string/every_day" />
 *
 *     <io.github.avalonskyafar.mallet.widget.MalletRadioButton
 *         android:id="@+id/weekly"
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         android:text="@string/every_week" />
 * </io.github.avalonskyafar.mallet.widget.MalletRadioGroup>
 * ```
 */
public class MalletRadioGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Called when the user moves the choice to another radio of the group. */
    public fun interface OnCheckedChangeListener {
        public fun onCheckedChanged(group: MalletRadioGroup, checkedId: Int)
    }

    private var current = View.NO_ID

    /** An id asked for before a child had it; applied when that child is added. */
    private var pendingId = View.NO_ID

    /** Depth of the group's own unchecking: nested calls from listeners must not end it early. */
    private var adjusting = 0

    private var checkedChangeListener: OnCheckedChangeListener? = null

    /** Listener for user-driven changes of the choice. Not called for [check] or [clearCheck]. */
    public fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        checkedChangeListener = listener
    }

    /** Id of the checked radio, or [View.NO_ID] while nothing is chosen. */
    public val checkedId: Int get() = current

    init {
        orientation = VERTICAL
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletRadioGroup, defStyleAttr, 0)
            try {
                // Applied as soon as the child with this id is added.
                pendingId = a.getResourceId(R.styleable.MalletRadioGroup_malletRadioCheckedButton, View.NO_ID)
            } finally {
                a.recycle()
            }
        }
    }

    /**
     * Moves the choice to the radio with [id] from code; [View.NO_ID] clears it. The listeners
     * of the group and of the radios are not called, which makes this safe for rolling back a
     * change a listener refused. An id that no child has yet is remembered and applied when
     * that child is added; until then nothing is checked and [checkedId] is [View.NO_ID].
     */
    @JvmOverloads
    public fun check(id: Int, animate: Boolean = true) {
        if (id == View.NO_ID) {
            clearCheck(animate)
            return
        }
        val target = radio(id)
        if (target != null) {
            pendingId = View.NO_ID
            // The hook moves `current` and unchecks the previous radio.
            if (target.isChecked) current = id else target.setChecked(true, animate)
            return
        }
        uncheckCurrent(animate, fromUser = false)
        current = View.NO_ID
        pendingId = id
    }

    /** Leaves no radio checked. Listeners are not called. */
    @JvmOverloads
    public fun clearCheck(animate: Boolean = true) {
        uncheckCurrent(animate, fromUser = false)
        current = View.NO_ID
        pendingId = View.NO_ID
    }

    private fun radio(id: Int): MalletRadioButton? {
        if (id == View.NO_ID) return null
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is MalletRadioButton && child.id == id) return child
        }
        return null
    }

    private fun uncheckCurrent(animate: Boolean, fromUser: Boolean, except: MalletRadioButton? = null) {
        adjusting++
        try {
            // Every other checked radio, not just `current`: ids may repeat or change.
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child is MalletRadioButton && child !== except && child.isChecked) {
                    child.setCheckedFromGroup(false, animate, fromUser)
                    // A sibling cannot refuse to give the choice up: its listener was told,
                    // and a re-check from inside it is undone without a second call.
                    if (child.isChecked) child.setCheckedFromGroup(false, animate, fromUser = false)
                }
            }
            // Nor can its listener hand the choice to another sibling: whatever it checked
            // meanwhile is unchecked again, silently.
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child is MalletRadioButton && child !== except && child.isChecked) {
                    child.setCheckedFromGroup(false, animate, fromUser = false)
                }
            }
        } finally {
            adjusting--
        }
    }

    private val hook = { button: MalletRadioButton, checked: Boolean, animate: Boolean, fromUser: Boolean ->
        if (adjusting == 0) {
            if (checked) {
                uncheckCurrent(animate, fromUser, except = button)
                // A sibling's listener may have cleared the group meanwhile: then nothing is
                // chosen and there is no change to report.
                if (button.isChecked) {
                    current = button.id
                    pendingId = View.NO_ID
                    if (fromUser) checkedChangeListener?.onCheckedChanged(this, current)
                } else if (current == button.id) {
                    current = View.NO_ID
                }
            } else if (button.id == current) {
                current = View.NO_ID
            }
        }
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (child !is MalletRadioButton) return
        if (child.id == View.NO_ID) child.id = View.generateViewId()
        child.groupHook = hook
        if (child.isChecked) {
            uncheckCurrent(animate = false, fromUser = false, except = child)
            current = child.id
            pendingId = View.NO_ID
        } else if (child.id == pendingId) {
            pendingId = View.NO_ID
            child.setChecked(true, animate = false)
        }
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (child !is MalletRadioButton) return
        child.groupHook = null
        if (child.id == current) current = View.NO_ID
    }

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable(STATE_SUPER, super.onSaveInstanceState())
        // A choice still waiting for its child is kept as well.
        putInt(STATE_CHECKED_ID, if (current != View.NO_ID) current else pendingId)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java))
        if (!state.containsKey(STATE_CHECKED_ID)) return
        val id = state.getInt(STATE_CHECKED_ID)
        when {
            id == View.NO_ID || radio(id) != null -> check(id, animate = false)
            // An id no child has (a generated one from before the screen was re-created) must
            // not wipe the present choice; with nothing chosen it waits for its child.
            radio(current) == null -> pendingId = id
        }
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.RadioGroup"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        var radios = 0
        for (i in 0 until childCount) if (getChildAt(i) is MalletRadioButton) radios++
        info.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
            radios,
            1,
            false,
            AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE,
        )
    }

    private companion object {
        private const val STATE_SUPER = "mallet:super"
        private const val STATE_CHECKED_ID = "mallet:checkedId"
    }
}
