package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.Token
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class DslLexerTests {

    @Nested
    inner class IndentTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testIndent(name: String, input: String, expected: String?) {
            val index = Regex("^[\n\r]*").find(input)!!.value.length
            test(input, listOf(
                Token(DslTokenType.INDENT, expected ?: "", null, Input.Range(index, if (index > 0) 2 else 1, 0, expected?.length ?: 0))
            ), expected != null)
        }

        fun testIndent(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Newline", "\n    ", "    "),
                Arguments.of("Carriage Return", "\r    ", "    "),
                Arguments.of("Newline & Carriage Return", "\n\r    ", "    "),
                Arguments.of("Tab", "\n\t", "\t"),
                Arguments.of("Spaces & Tabs", "\n    \t", "    \t"),
                Arguments.of("Newline Only", "\n", ""),
                Arguments.of("Missing Newline", "    ", null),
            )
        }

    }

    @Nested
    inner class OperatorTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testOperator(name: String, input: String, success: Boolean) {
            test(input, listOf(
                Token(DslTokenType.OPERATOR, input, null, Input.Range(0, 1, 0, input.length))
            ), success)
        }

        fun testOperator(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Dollar Sign", "\$", true),
                Arguments.of("Opening Brace", "{", true),
                Arguments.of("Closing Brace", "}", true),
                Arguments.of("Backslash", "\\", false),
                Arguments.of("Whitespace", " ", false),
            )
        }

    }

    @Nested
    inner class TextTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testText(name: String, input: String, success: Boolean) {
            test(input, listOf(
                Token(DslTokenType.TEXT, input, null, Input.Range(0, 1, 0, input.length))
            ), success)
        }

        fun testText(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Letters", "abc", true),
                Arguments.of("Numbers", "123", true),
                Arguments.of("Symbols", "!@#", true),
                Arguments.of("Unicode", "ρ⚡♖", true),
                Arguments.of("Whitespace", "    \t", true),
                Arguments.of("Leading Whitespace", "    text", true),
                Arguments.of("Trailing Whitespace", "text    ", true),
                Arguments.of("Escape", "\\\\", true),
            )
        }

    }

    @Nested
    inner class InteractionTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testInteraction(name: String, input: String, expected: List<Token<DslTokenType>>) {
            test(input, expected, true)
        }

        fun testInteraction(): Stream<Arguments> {
            return Stream.of(
                //indent
                Arguments.of("Inner Indent", "first\n    second", listOf(
                    Token(DslTokenType.TEXT, "first", null, Input.Range(0, 1, 0, 5)),
                    Token(DslTokenType.INDENT, "    ", null, Input.Range(6, 2, 0, 4)),
                    Token(DslTokenType.TEXT, "second", null, Input.Range(10, 2, 4, 6)),
                )),
                Arguments.of("Leading Indent", "\n    token", listOf(
                    Token(DslTokenType.INDENT, "    ", null, Input.Range(1, 2, 0, 4)),
                    Token(DslTokenType.TEXT, "token", null, Input.Range(5, 2, 4, 5)),
                )),
                Arguments.of("Trailing Indent", "token\n    ", listOf(
                    Token(DslTokenType.TEXT, "token", null, Input.Range(0, 1, 0, 5)),
                    Token(DslTokenType.INDENT, "    ", null, Input.Range(6, 2, 0, 4)),
                )),
                Arguments.of("Empty Line", "first\n\nsecond", listOf(
                    Token(DslTokenType.TEXT, "first", null, Input.Range(0, 1, 0, 5)),
                    Token(DslTokenType.INDENT, "", null, Input.Range(6, 2, 0, 0)),
                    Token(DslTokenType.INDENT, "", null, Input.Range(7, 3, 0, 0)),
                    Token(DslTokenType.TEXT, "second", null, Input.Range(7, 3, 0, 6)),
                )),
                //operator
                Arguments.of("Interpolation", "\${value}", listOf(
                    Token(DslTokenType.OPERATOR, "\$", null, Input.Range(0, 1, 0, 1)),
                    Token(DslTokenType.OPERATOR, "{", null, Input.Range(1, 1, 1, 1)),
                    Token(DslTokenType.TEXT, "value", null, Input.Range(2, 1, 2, 5)),
                    Token(DslTokenType.OPERATOR, "}", null, Input.Range(7, 1, 7, 1)),
                )),
                Arguments.of("Inner Operator", "first\$second", listOf(
                    Token(DslTokenType.TEXT, "first", null, Input.Range(0, 1, 0, 5)),
                    Token(DslTokenType.OPERATOR, "\$", null, Input.Range(5, 1, 5, 1)),
                    Token(DslTokenType.TEXT, "second", null, Input.Range(6, 1, 6, 6)),
                )),
            )
        }

    }

    @Nested
    inner class ProgramTests {

        @Test
        fun testRegex() {
            test("""
                { /\d+(\.\d+)?/ }
            """.trimIndent(), listOf(
                Token(DslTokenType.OPERATOR, "{", null, Input.Range(0, 1, 0, 1)),
                Token(DslTokenType.TEXT, " /\\d+(\\.\\d+)?/ ", null, Input.Range(1, 1, 1, 15)),
                Token(DslTokenType.OPERATOR, "}", null, Input.Range(16, 1, 16, 1)),
            ), true)
        }

        @Test
        fun testSql() {
            test("""
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
            ), true)
        }

    }

    private fun test(input: String, expected: List<Token<DslTokenType>>, success: Boolean) {
        val input = Input("Test", input)
        try {
            val tokens = DslLexer(input).lex()
            if (success) {
                Assertions.assertEquals(expected, tokens)
            } else {
                Assertions.assertNotEquals(expected, tokens)
            }
        } catch (e: ParseException) {
            if (success || e.summary == "Broken lexer invariant.") {
                println(input.diagnostic(e.summary, e.details, e.range, e.context))
                Assertions.fail<Unit>(e)
            }
        }
    }

}
