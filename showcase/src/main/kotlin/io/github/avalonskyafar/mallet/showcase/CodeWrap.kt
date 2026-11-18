package io.github.avalonskyafar.mallet.showcase

/**
 * Wraps the lines of a Kotlin sample that are too long for the screen, but only at places
 * where a line break leaves the code valid: after an opening parenthesis, a comma, an
 * assignment, an opening brace, a lambda arrow or a trailing operator and, when nothing else
 * offers itself, before a member-access dot. A line that has no such place is left alone; the
 * sample then scrolls sideways.
 *
 * Samples are written once but shown on screens of many widths and text sizes, so the wrapping
 * cannot be done by hand.
 */
internal object CodeWrap {
    private const val CONTINUATION = "    "
    private const val SHORTEST_PIECE = 4
    private const val NARROWEST = 16

    /** [code] with every line made to fit [budget] characters where that can be done safely. */
    fun wrap(code: String, budget: Int): String =
        if (budget < NARROWEST) code else code.split('\n').joinToString("\n") { wrapLine(it, budget) }

    private fun wrapLine(line: String, budget: Int): String {
        if (line.length <= budget) return line
        val indent = line.takeWhile { it == ' ' }
        if (line.trimStart().startsWith("//")) return wrapComment(line.trimStart().removePrefix("//"), indent, budget)

        // A trailing comment that makes the line too long moves above the code it explains.
        val comment = commentStart(line)
        if (comment > 0) {
            val code = line.substring(0, comment).trimEnd()
            if (code.isNotBlank()) {
                return wrapComment(line.substring(comment + 2), indent, budget) + "\n" + wrapLine(code, budget)
            }
        }

        val continuation = indent + CONTINUATION
        val pieces = ArrayList<String>()
        var rest = line
        var floor = indent.length + SHORTEST_PIECE
        while (rest.length > budget) {
            var cut = breakBefore(rest, budget, floor, dots = false)
            if (cut < 0) cut = breakBefore(rest, budget, floor, dots = true)
            if (cut < 0) break
            pieces += rest.substring(0, cut).trimEnd()
            rest = continuation + rest.substring(cut).trimStart()
            floor = continuation.length + SHORTEST_PIECE
        }
        pieces += rest
        return pieces.joinToString("\n")
    }

    private fun wrapComment(text: String, indent: String, budget: Int): String {
        val prefix = "$indent// "
        val lines = ArrayList<String>()
        var current = StringBuilder(prefix)
        for (word in text.trim().split(' ')) {
            if (word.isEmpty()) continue
            if (current.length > prefix.length && current.length + 1 + word.length > budget) {
                lines += current.toString()
                current = StringBuilder(prefix)
            }
            if (current.length > prefix.length) current.append(' ')
            current.append(word)
        }
        lines += current.toString()
        return lines.joinToString("\n")
    }

    /** Index of the `//` that starts a comment outside string literals, or -1. */
    private fun commentStart(text: String): Int {
        var inString = false
        for (i in text.indices) {
            val c = text[i]
            if (!inString && c == '/' && i + 1 < text.length && text[i + 1] == '/') return i
            if (c == '"' && (i == 0 || text[i - 1] != '\\')) inString = !inString
        }
        return -1
    }

    /** The last index from [floor] to [budget] before which [text] may be broken, or -1. */
    private fun breakBefore(text: String, budget: Int, floor: Int, dots: Boolean): Int {
        var best = -1
        var inString = false
        val last = minOf(budget, text.length - 1)
        for (i in 0..last) {
            val c = text[i]
            if (!inString) {
                if (c == '/' && i + 1 < text.length && text[i + 1] == '/') break
                if (i >= floor && isBreak(text, i, dots)) best = i
            }
            if (c == '"' && (i == 0 || text[i - 1] != '\\')) inString = !inString
        }
        return best
    }

    private fun isBreak(text: String, i: Int, dots: Boolean): Boolean {
        if (i <= 0 || i >= text.length) return false
        val c = text[i]
        val before = text[i - 1]
        if (before == '(' && c != ')') return true
        if (c == '.') {
            // A dot that starts a member access, not a decimal point and not a range.
            val next = if (i + 1 < text.length) text[i + 1] else ' '
            return dots && !before.isDigit() && before != '.' && before != ' ' && next != '.'
        }
        if (c != ' ') return false
        // A space: what stands before it must be something a statement cannot end with.
        var j = i - 1
        while (j >= 0 && text[j] == ' ') j--
        if (j < 0) return false
        val p = text[j]
        if (p == ',' || p == '{' || p == '=' || p == '+') return true
        if (p == '>' && j > 0 && text[j - 1] == '-') return true
        return (p == '&' || p == '|') && j > 0 && text[j - 1] == p
    }
}
