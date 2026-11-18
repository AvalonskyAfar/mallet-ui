package io.github.avalonskyafar.mallet.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import io.github.avalonskyafar.mallet.R
import io.github.avalonskyafar.mallet.theme.MalletTextRole
import io.github.avalonskyafar.mallet.theme.MalletTextTone
import io.github.avalonskyafar.mallet.theme.MalletTheme

/**
 * A text view that speaks the Mallet type scale: pick a [role] for size, weight and leading
 * and a [tone] for colour.
 *
 * ```xml
 * <io.github.avalonskyafar.mallet.widget.MalletText
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:text="Protection strength"
 *     app:malletTextRole="heading" />
 * ```
 *
 * Font padding is switched off, so a line of text is exactly as tall as its glyphs and
 * vertical centring is optical rather than approximate.
 */
@SuppressLint("AppCompatCustomView")
public open class MalletText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : TextView(context, attrs, defStyleAttr) {

    /** The theme this view was created with. */
    protected val theme: MalletTheme = MalletTheme.of(context)

    /** Size, weight and leading. */
    public var role: MalletTextRole = MalletTextRole.BODY_COMPACT
        set(value) {
            field = value
            value.applyTo(this, theme)
        }

    /** Colour role. */
    public var tone: MalletTextTone = MalletTextTone.INK
        set(value) {
            field = value
            setTextColor(value.resolve(theme.colors))
        }

    init {
        var initialRole = MalletTextRole.BODY_COMPACT
        var initialTone = MalletTextTone.INK
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MalletText, defStyleAttr, 0)
            try {
                initialRole = ROLES.getOrElse(a.getInt(R.styleable.MalletText_malletTextRole, initialRole.ordinal)) { initialRole }
                initialTone = TONES.getOrElse(a.getInt(R.styleable.MalletText_malletTone, initialTone.ordinal)) { initialTone }
            } finally {
                a.recycle()
            }
        }
        role = initialRole
        tone = initialTone
    }

    public companion object {
        private val ROLES = MalletTextRole.values()
        private val TONES = MalletTextTone.values()

        /** Creates a text view in code. */
        @JvmStatic
        @JvmOverloads
        public fun create(
            context: Context,
            text: CharSequence?,
            role: MalletTextRole,
            tone: MalletTextTone = MalletTextTone.INK,
        ): MalletText = MalletText(context).also {
            it.role = role
            it.tone = tone
            it.text = text
        }
    }
}
