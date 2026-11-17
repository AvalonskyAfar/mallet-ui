package io.github.avalonskyafar.sancai.showcase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CodeWrapTest {

    private fun wrap(code: String, budget: Int = 46): List<String> = CodeWrap.wrap(code, budget).split('\n')

    @Test
    fun `short lines are left alone`() {
        val code = "val track = SancaiSegmentedTrack(this)\ntrack.select(2)"
        assertThat(CodeWrap.wrap(code, 46)).isEqualTo(code)
    }

    @Test
    fun `every piece fits and the code is unchanged apart from line breaks`() {
        val line = "track.setOptions(\"Off\", \"Gentle\", \"Standard\", \"Strict\")"
        val pieces = wrap(line)
        assertThat(pieces.size).isGreaterThan(1)
        for (piece in pieces) assertThat(piece.length).isAtMost(46)
        assertThat(pieces.joinToString(" ") { it.trim() }.replace("( ", "(")).isEqualTo(line)
    }

    @Test
    fun `breaks come after a comma an assignment a brace or an arrow`() {
        val pieces = wrap("    SancaiSwitch.OnCheckedChangeListener { _, checked ->", 46)
        assertThat(pieces.size).isEqualTo(2)
        assertThat(pieces[0].trimEnd().last()).isAnyOf(',', '{', '=', '>')
        // The continuation is indented one step further than the line it continues.
        assertThat(pieces[1]).startsWith("        ")
    }

    @Test
    fun `a string literal is never split`() {
        val line = "status.text = \"Strict needs a passcode, and a second factor, today.\""
        val pieces = wrap(line, 40)
        // The only safe place is after the assignment; the long literal then stays whole.
        assertThat(pieces).containsExactly(
            "status.text =",
            "    \"Strict needs a passcode, and a second factor, today.\"",
        ).inOrder()
    }

    @Test
    fun `a trailing comment moves above its line`() {
        val pieces = wrap("quiet.setChecked(true, false)   // jumps, no callback", 46)
        assertThat(pieces).containsExactly("// jumps, no callback", "quiet.setChecked(true, false)").inOrder()
    }

    @Test
    fun `a comment line wraps as a comment`() {
        val pieces = wrap("    // Application.onCreate: the library asks your settings for its theme", 40)
        assertThat(pieces.size).isGreaterThan(1)
        for (piece in pieces) {
            assertThat(piece).startsWith("    // ")
            assertThat(piece.length).isAtMost(40)
        }
    }

    @Test
    fun `a member access dot is the last resort and decimals stay whole`() {
        val pieces = wrap("SancaiSegmentedTrack.OnSelectionChangedListener.somethingElse", 50)
        assertThat(pieces.size).isEqualTo(2)
        assertThat(pieces[1].trimStart()).startsWith(".")
        val number = "1.3" + "5".repeat(36) + "f"
        assertThat(wrap("val ratio = $number", 30)).containsExactly("val ratio =", "    $number").inOrder()
    }

    @Test
    fun `a line with no safe place is left as it is`() {
        val line = "aVeryLongIdentifierThatSimplyCannotBeBrokenAnywhereAtAllReally"
        assertThat(wrap(line, 30)).containsExactly(line)
    }
}
