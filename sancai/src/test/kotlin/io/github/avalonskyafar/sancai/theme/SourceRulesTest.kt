package io.github.avalonskyafar.sancai.theme

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Rules of the library that are easier to keep by reading its own sources than by review.
 *
 * The tests run with the module directory as the working directory, so `src/main/kotlin` is
 * the library as it will be published.
 */
class SourceRulesTest {

    private val sources: List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun offenders(skip: Set<String> = emptySet(), rule: (String) -> Boolean): List<String> =
        sources.filter { it.name !in skip }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.trim()
                if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) return@mapIndexedNotNull null
                if (rule(code)) "${file.name}:${index + 1}: $code" else null
            }
        }

    @Test
    fun `the sources are where this test expects them`() {
        assertThat(sources.map { it.name }).containsAtLeast("SancaiType.kt", "SancaiSegmentedTrack.kt", "SancaiButton.kt")
    }

    @Test
    fun `text sizes come from the type scale`() {
        // The scale itself, and the two signature controls whose label size belongs to their
        // geometry (see DESIGN_DECISIONS D-05).
        val exempt = setOf("SancaiType.kt", "SancaiSegmentedTrack.kt", "SancaiPowerSwitch.kt")
        val constant = Regex("""const val \w*_SP\b[^=]*=\s*[\d.]+f""")
        val literalCall = Regex("""(setTextSize\(|\bsp\()[^)]*?(?<![\w.])\d+(\.\d+)?f""")
        val found = offenders(exempt) { constant.containsMatchIn(it) || literalCall.containsMatchIn(it) }
        assertWithMessage("use SancaiTextRole.X.sizeSp instead of a literal size:\n" + found.joinToString("\n"))
            .that(found).isEmpty()
    }

    @Test
    fun `listeners are set with a function, not with a property`() {
        // A property needs the interface name spelled out in Kotlin; a function takes a lambda.
        val property = Regex("""public var on[A-Z]\w*Listener\b""")
        val found = offenders { property.containsMatchIn(it) }
        assertWithMessage("declare setOnXxxListener(listener) instead:\n" + found.joinToString("\n"))
            .that(found).isEmpty()
    }

    @Test
    fun `nothing is drawn with a ripple or a platform elevation`() {
        // Pressing sinks content and depth is painted inside the view (DESIGN_SYSTEM 7 and 8).
        val forbidden = Regex("""RippleDrawable|selectableItemBackground|\.elevation\s*=|setElevation\(|StateListAnimator\(""")
        // Switching an elevation off is the rule being followed, not broken.
        val off = Regex("""elevation\s*=\s*0f|setElevation\(0f\)""")
        val found = offenders { forbidden.containsMatchIn(it) && !off.containsMatchIn(it) }
        assertWithMessage(found.joinToString("\n")).that(found).isEmpty()
    }
}
