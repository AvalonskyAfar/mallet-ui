package io.github.avalonskyafar.sancai.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import io.github.avalonskyafar.sancai.R
import io.github.avalonskyafar.sancai.theme.SancaiTextRole
import io.github.avalonskyafar.sancai.theme.SancaiTextTone
import io.github.avalonskyafar.sancai.theme.SancaiTheme

/**
 * A text view that speaks the Sancai type scale: pick a [role] for size, weight and leading
 * and a [tone] for colour.
 *
 * ```xml
 * <io.github.avalonskyafar.sancai.widget.SancaiText
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     android:text="Protection strength"
 *     app:sancaiTextRole="heading" />
 * ```
 *
 * Font padding is switched off, so a line of text is exactly as tall as its glyphs and
 * vertical centring is optical rather than approximate.
 */
@SuppressLint("AppCompatCustomView")
public open class SancaiText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : TextView(context, attrs, defStyleAttr) {

    /** The theme this view was created with. */
    protected val theme: SancaiTheme = SancaiTheme.of(context)

    /** Size, weight and leading. */
    public var role: SancaiTextRole = SancaiTextRole.BODY_COMPACT
        set(value) {
            field = value
            value.applyTo(this, theme)
        }

    /** Colour role. */
    public var tone: SancaiTextTone = SancaiTextTone.INK
        set(value) {
            field = value
            setTextColor(value.resolve(theme.colors))
        }

    init {
        var initialRole = SancaiTextRole.BODY_COMPACT
        var initialTone = SancaiTextTone.INK
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.SancaiText, defStyleAttr, 0)
            try {
                initialRole = ROLES.getOrElse(a.getInt(R.styleable.SancaiText_sancaiTextRole, initialRole.ordinal)) { initialRole }
                initialTone = TONES.getOrElse(a.getInt(R.styleable.SancaiText_sancaiTone, initialTone.ordinal)) { initialTone }
            } finally {
                a.recycle()
            }
        }
        role = initialRole
        tone = initialTone
    }

    public companion object {
        private val ROLES = SancaiTextRole.values()
        private val TONES = SancaiTextTone.values()

        /** Creates a text view in code. */
        @JvmStatic
        @JvmOverloads
        public fun create(
            context: Context,
            text: CharSequence?,
            role: SancaiTextRole,
            tone: SancaiTextTone = SancaiTextTone.INK,
        ): SancaiText = SancaiText(context).also {
            it.role = role
            it.tone = tone
            it.text = text
        }
    }
}
