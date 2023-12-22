package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.Token
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail

class DslLexerTests: RhovasSpec() {

    data class Test<T>(val source: String, val argument: T)

    init {
        suite("Indent", listOf(
            "Newline" to Test("\n    ", "    "),
            "Carriage Return" to Test("\r    ", "    "),
            "Newline & Carriage Return" to Test("\n\r    ", "    "),
            "Tab" to Test("\n\t", "\t"),
            "Spaces & Tabs" to Test("\n    \t", "    \t"),
            "Newline Only" to Test("\n", ""),
            "Missing Newline" to Test("    ", null),
        )) {
            val index = Regex("^[\n\r]*").find(it.source)!!.value.length
            val token = Token(DslTokenType.INDENT, it.argument ?: "", null, Input.Range(index, if (index > 0) 2 else 1, 0, it.argument?.length ?: 0))
            test(it.source, listOf(token), it.argument != null)
        }

        suite("Operator", listOf(
            "Dollar Sign" to Test("\$", true),
            "Opening Brace" to Test("{", true),
            "Closing Brace" to Test("}", true),
            "Backslash" to Test("\\", false),
            "Whitespace" to Test(" ", false),
        )) { test(it.source, listOf(Token(DslTokenType.OPERATOR, it.source, null, Input.Range(0, 1, 0, it.source.length))), it.argument) }

        suite("Text", listOf(
            "Letters" to Test("abc", true),
            "Numbers" to Test("123", true),
            "Symbols" to Test("!@#", true),
            "Unicode" to Test("ρ⚡♖", true),
            "Whitespace" to Test("    \t", true),
            "Leading Whitespace" to Test("    text", true),
            "Trailing Whitespace" to Test("text    ", true),
            "Escape" to Test("\\\\", true),
        )) { test(it.source, listOf(Token(DslTokenType.TEXT, it.source, null, Input.Range(0, 1, 0, it.source.length))), it.argument) }

        suite("Interaction", listOf(
            //indent
            "Inner Indent" to Test("first\n    second", listOf(
                Token(DslTokenType.TEXT, "first", null, Input.Range(0, 1, 0, 5)),
                Token(DslTokenType.INDENT, "    ", null, Input.Range(6, 2, 0, 4)),
                Token(DslTokenType.TEXT, "second", null, Input.Range(10, 2, 4, 6)),
            )),
            "Leading Indent" to Test("\n    token", listOf(
                Token(DslTokenType.INDENT, "    ", null, Input.Range(1, 2, 0, 4)),
                Token(DslTokenType.TEXT, "token", null, Input.Range(5, 2, 4, 5)),
            )),
            "Trailing Indent" to Test("token\n    ", listOf(
                Token(DslTokenType.TEXT, "token", null, Input.Range(0, 1, 0, 5)),
                Token(DslTokenType.INDENT, "    ", null, Input.Range(6, 2, 0, 4)),
            )),
            "Empty Line" to Test("first\n\nsecond", listOf(
                Token(DslTokenType.TEXT, "first", null, Input.Range(0, 1, 0, 5)),
                Token(DslTokenType.INDENT, "", null, Input.Range(6, 2, 0, 0)),
                Token(DslTokenType.INDENT, "", null, Input.Range(7, 3, 0, 0)),
                Token(DslTokenType.TEXT, "second", null, Input.Range(7, 3, 0, 6)),
            )),
            //operator
            "Interpolation" to Test("\${value}", listOf(
                Token(DslTokenType.OPERATOR, "\$", null, Input.Range(0, 1, 0, 1)),
                Token(DslTokenType.OPERATOR, "{", null, Input.Range(1, 1, 1, 1)),
                Token(DslTokenType.TEXT, "value", null, Input.Range(2, 1, 2, 5)),
                Token(DslTokenType.OPERATOR, "}", null, Input.Range(7, 1, 7, 1)),
            )),
            "Inner Operator" to Test("first\$second", listOf(
                Token(DslTokenType.TEXT, "first", null, Input.Range(0, 1, 0, 5)),
                Token(DslTokenType.OPERATOR, "\$", null, Input.Range(5, 1, 5, 1)),
                Token(DslTokenType.TEXT, "second", null, Input.Range(6, 1, 6, 6)),
            )),
        )) { test(it.source, it.argument, true) }

        suite("Program", listOf(
            "Regex" to Test("""
                { /\d+(\.\d+)?/ }
            """.trimIndent(), listOf(
                Token(DslTokenType.OPERATOR, "{", null, Input.Range(0, 1, 0, 1)),
                Token(DslTokenType.TEXT, " /\\d+(\\.\\d+)?/ ", null, Input.Range(1, 1, 1, 15)),
                Token(DslTokenType.OPERATOR, "}", null, Input.Range(16, 1, 16, 1)),
            )),
            "SQL" to Test("""
                {
                    SELECT * FROM users
                    WHERE name = ${"\$"}{user.name}
                }
            """.trimIndent(), listOf(
                Token(DslTokenType.OPERATOR, "{", null, Input.Range(0, 1, 0, 1)),
                Token(DslTokenType.INDENT, "    ", null, Input.Range(2, 2, 0, 4)),
                Token(DslTokenType.TEXT, "SELECT * FROM users", null, Input.Range(6, 2, 4, 19)),
                Token(DslTokenType.INDENT, "    ", null, Input.Range(26, 3, 0, 4)),
                Token(DslTokenType.TEXT, "WHERE name = ", null, Input.Range(30, 3, 4, 13)),
                Token(DslTokenType.OPERATOR, "\$", null, Input.Range(43, 3, 17, 1)),
                Token(DslTokenType.OPERATOR, "{", null, Input.Range(44, 3, 18, 1)),
                Token(DslTokenType.TEXT, "user.name", null, Input.Range(45, 3, 19, 9)),
                Token(DslTokenType.OPERATOR, "}", null, Input.Range(54, 3, 28, 1)),
                Token(DslTokenType.INDENT, "", null, Input.Range(56, 4, 0, 0)),
                Token(DslTokenType.OPERATOR, "}", null, Input.Range(56, 4, 0, 1)),
            )),
        )) { test(it.source, it.argument, true) }
    }

    private fun test(source: String, expected: List<Token<DslTokenType>>, success: Boolean) {
        val input = Input("Test", source)
        val lexer = DslLexer(input)
        try {
            val tokens = generateSequence { lexer.lexToken() }.toList()
            if (success) {
                assertEquals(expected, tokens)
            } else {
                assertNotEquals(expected, tokens)
            }
        } catch (e: ParseException) {
            if (success || e.summary == "Broken lexer invariant.") {
                fail(input.diagnostic(e.summary, e.details, e.range, e.context), e)
            }
        }
    }

}
