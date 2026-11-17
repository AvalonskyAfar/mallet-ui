package io.github.avalonskyafar.sancai.widget

import android.content.Context
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole

/**
 * Long-form copy: explanations, tutorials, anything that is read rather than scanned.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiParagraph
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:text="@string/tutorial_intro" />
 * ```
 *
 * A paragraph is a [SancaiText] in the body role that is aligned to the start and the top
 * and fills every line before it wraps: the simple break strategy and no hyphenation, so
 * earlier lines are never shortened to balance the last one.
 *
 * With [protectClosingPunctuation] a word joiner (U+2060) is put in front of every CJK
 * closing punctuation mark when the text is set, which keeps the mark on the line of the
 * character it closes. [getText] therefore returns the processed text; the text as it was
 * given stays available as [rawText].
 */
public open class SancaiParagraph @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : SancaiText(context, attrs, defStyleAttr) {

    // False while the super constructor runs: it may set the text before our fields exist.
    private var ready = false

    private var original: CharSequence? = null

    /**
     * The text as it was handed to [setText], without the inserted word joiners. Setting it
     * is the same as calling [setText].
     */
    public var rawText: CharSequence?
        get() = original
        set(value) {
            text = value
        }

    /**
     * Whether CJK closing punctuation (`，。！？；：、）】》”’`) is tied to the character before
     * it so that it never starts a line. Changing it re-applies [rawText].
     */
    public var protectClosingPunctuation: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                if (ready) text = original
            }
        }

    init {
        var roleFromXml = false
        var protect = true
        if (attrs != null) {
            val shared = context.obtainStyledAttributes(attrs, R.styleable.SancaiText, defStyleAttr, 0)
            try {
                roleFromXml = shared.hasValue(R.styleable.SancaiText_sancaiTextRole)
            } finally {
                shared.recycle()
            }
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiParagraph, defStyleAttr, 0)
            try {
                protect = a.getBoolean(R.styleable.SancaiParagraph_sancaiParagraphProtectClosingPunctuation, true)
            } finally {
                a.recycle()
            }
        }
        if (!roleFromXml) role = SancaiTextRole.BODY
        gravity = Gravity.START or Gravity.TOP
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE

        protectClosingPunctuation = protect
        ready = true
        // android:text arrived while the super constructor ran, before it could be processed.
        val inflated = super.getText()
        if (!inflated.isNullOrEmpty()) text = inflated
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        if (!ready) {
            super.setText(text, type)
            return
        }
        original = text
        super.setText(if (protectClosingPunctuation) protect(text) else text, type)
    }

    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        // Screen readers and braille displays get the copy without the invisible joiners.
        original?.let { info.text = it }
    }

    override fun getAccessibilityClassName(): CharSequence = android.widget.TextView::class.java.name

    public companion object {
        /** The CJK closing punctuation marks that must not start a line. */
        public const val CLOSING_PUNCTUATION: String = "，。！？；：、）】》”’"

        /** The invisible character that forbids a line break on both of its sides. */
        public const val WORD_JOINER: Char = '\u2060'

        /**
         * Returns [text] with a [WORD_JOINER] in front of every mark of [CLOSING_PUNCTUATION]
         * that is not the first character and is not already protected. Spans are kept. The
         * same instance is returned when there is nothing to protect.
         */
        @JvmStatic
        public fun protect(text: CharSequence?): CharSequence? {
            if (text.isNullOrEmpty()) return text
            var builder: SpannableStringBuilder? = null
            var shift = 0
            for (index in 1 until text.length) {
                if (CLOSING_PUNCTUATION.indexOf(text[index]) < 0 || text[index - 1] == WORD_JOINER) continue
                val target = builder ?: SpannableStringBuilder(text).also { builder = it }
                target.insert(index + shift, WORD_JOINER.toString())
                shift++
            }
            val result = builder ?: return text
            return if (text is Spanned) result else result.toString()
        }
    }
}
