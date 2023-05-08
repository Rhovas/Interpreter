package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import kotlin.test.assertEquals
import kotlin.test.fail

class DslParserTests: RhovasSpec() {

    data class Test(val source: String, val expected: DslAst.Source?)

    init {
        suite("Inline") { listOf(
            "Empty" to Test("{}", DslAst.Source(listOf(""), listOf())),
            "Text" to Test("{text}", DslAst.Source(listOf("text"), listOf())),
            "Surrounding Whitespace" to Test("{    text    }", DslAst.Source(listOf("    text    "), listOf())),
            "Dollar Sign" to Test("{first\$second}", DslAst.Source(listOf("first\$second"), listOf())),
            "Newline" to Test("{first\nsecond}", null),
            "Braces" to Test("{text{}second}", null),
        ).forEach { (name, test) -> spec(name) {
            test("source", test.source, test.expected)
        } } }

        suite("Multiline") { listOf(
            "Text" to Test("""
                {
                    text
                }
            """.trimIndent(), DslAst.Source(
                listOf("text"), listOf()
            )),
            "Multiline Text" to Test("""
                {
                    first
                    second
                    third
                }
            """.trimIndent(), DslAst.Source(
                listOf("first\nsecond\nthird"),
                listOf(),
            )),
            "Multiline Indented" to Test("""
                {
                    first
                        second
                    third
                }
            """.trimIndent(), DslAst.Source(
                listOf("first\n    second\nthird"),
                listOf(),
            )),
            "Multiline Empty" to Test("""
                {
                    first
                
                    second
                }
            """.trimIndent(), DslAst.Source(
                listOf("first\n\nsecond"),
                listOf(),
            )),
            "Dollar Sign" to Test("""
                {
                    first${"\$"}second
                }
            """.trimIndent(), DslAst.Source(
                listOf("first\$second"),
                listOf(),
            )),
            "Braces" to Test("""
                {
                    {}
                }
            """.trimIndent(), DslAst.Source(
                listOf("{}"),
                listOf(),
            )),
            "Interpolation" to Test("""
                {
                    ${"\$"}{value}
                }
            """.trimIndent(), DslAst.Source(
                listOf("", ""),
                listOf(RhovasAst.Expression.Access.Variable(null, "value")),
            )),
            "Empty" to Test("""
                {
                }
            """.trimIndent(), null),
            "Double Leading Indentation" to Test("""
                {
                        first
                    second
                }
            """.trimIndent(), null),
        ).forEach { (name, test) -> spec(name) {
            test("source", test.source, test.expected)
        } } }
    }

    private fun test(rule: String, source: String, expected: DslAst?) {
        val input = Input("Test", source)
        try {
            val ast = DslParser(input).parse(rule)
            assertEquals(expected, ast)
        } catch (e: ParseException) {
            if (expected != null || e.summary == "Broken parser invariant.") {
                fail(input.diagnostic(e.summary, e.details, e.range, e.context))
            }
        }
    }

}
