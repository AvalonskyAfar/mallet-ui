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

/**
 * The heading above a card or at the top of a page: a title in ink and, optionally, one quiet
 * line of detail below it.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiHeading
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:sancaiTitle="Protection"
 *     app:sancaiDetail="How firmly the app holds on"
 *     app:sancaiHeadingLevel="section" />
 * ```
 *
 * Unlike the rows of a card, a heading is start aligned: it labels what follows instead of
 * being operated. It is inset by 10 dp on both sides so that its text lines up with the content
 * of the card below rather than with the card's rim. The detail line disappears, together with
 * its 4 dp gap, while [detail] is empty or blank.
 *
 * The title is announced as a heading by accessibility services, so a page can be navigated
 * heading by heading. The view itself is not focusable and has no state to save.
 */
public class SancaiHeading @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** How much weight the title carries. */
    public enum class Level {
        /** Heading of a card or a group of cards: 17 sp. */
        SECTION,

        /** Title of a whole page: 20 sp. */
        PAGE,
    }

    private val titleView = SancaiText.create(context, null, SancaiTextRole.HEADING, SancaiTextTone.INK)
    private val detailView = SancaiText.create(context, null, SancaiTextRole.CAPTION, SancaiTextTone.MUTED)

    /** The heading itself; `null` or empty hides the title line. */
    public var title: CharSequence? = null
        set(value) {
            field = value
            titleView.text = value
            titleView.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

    /** Supporting line under the title; `null`, empty or blank hides the line. */
    public var detail: CharSequence? = null
        set(value) {
            field = value
            detailView.text = value
            detailView.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        }

    /** [Level.SECTION] (default) for card headings, [Level.PAGE] for the title of a page. */
    public var level: Level = Level.SECTION
        set(value) {
            field = value
            titleView.role = if (value == Level.PAGE) SancaiTextRole.TITLE else SancaiTextRole.HEADING
        }

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        val inset = (INSET_DP * density + 0.5f).toInt()
        // Only the sides belong to the heading; vertical padding from XML is kept.
        setPaddingRelative(inset, paddingTop, inset, paddingBottom)

        titleView.gravity = Gravity.START
        titleView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        ViewCompat.setAccessibilityHeading(titleView, true)
        titleView.visibility = View.GONE
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        detailView.gravity = Gravity.START
        detailView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        detailView.visibility = View.GONE
        addView(
            detailView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
                it.topMargin = (DETAIL_GAP_DP * density + 0.5f).toInt()
            },
        )

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiHeading, defStyleAttr, 0)
            try {
                title = a.getText(R.styleable.SancaiHeading_sancaiTitle)
                detail = a.getText(R.styleable.SancaiHeading_sancaiDetail)
                level = LEVELS.getOrElse(a.getInt(R.styleable.SancaiHeading_sancaiHeadingLevel, 0)) { Level.SECTION }
            } finally {
                a.recycle()
            }
        }
    }

    override fun getAccessibilityClassName(): CharSequence = SancaiHeading::class.java.name

    private companion object {
        private val LEVELS = Level.values()
        private const val INSET_DP = 10f
        private const val DETAIL_GAP_DP = 4f
    }
}
