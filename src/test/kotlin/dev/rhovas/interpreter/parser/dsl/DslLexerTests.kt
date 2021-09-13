package dev.rhovas.interpreter.parser.dsl

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
            test(input, listOf(Token(DslTokenType.INDENT, expected ?: "", null)), expected != null)
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
            test(input, listOf(Token(DslTokenType.OPERATOR, input, null)), success)
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
            test(input, listOf(Token(DslTokenType.TEXT, input, null)), success)
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
                    Token(DslTokenType.TEXT, "first", null),
                    Token(DslTokenType.INDENT, "    ", null),
                    Token(DslTokenType.TEXT, "second", null),
                )),
                Arguments.of("Leading Indent", "\n    token", listOf(
                    Token(DslTokenType.INDENT, "    ", null),
                    Token(DslTokenType.TEXT, "token", null),
                )),
                Arguments.of("Trailing Indent", "token\n    ", listOf(
                    Token(DslTokenType.TEXT, "token", null),
                    Token(DslTokenType.INDENT, "    ", null),
                )),
                Arguments.of("Empty Line", "first\n\nsecond", listOf(
                    Token(DslTokenType.TEXT, "first", null),
                    Token(DslTokenType.INDENT, "", null),
                    Token(DslTokenType.INDENT, "", null),
                    Token(DslTokenType.TEXT, "second", null),
                )),
                //operator
                Arguments.of("Interpolation", "\${value}", listOf(
                    Token(DslTokenType.OPERATOR, "\$", null),
                    Token(DslTokenType.OPERATOR, "{", null),
                    Token(DslTokenType.TEXT, "value", null),
                    Token(DslTokenType.OPERATOR, "}", null),
                )),
                Arguments.of("Inner Operator", "first\$second", listOf(
                    Token(DslTokenType.TEXT, "first", null),
                    Token(DslTokenType.OPERATOR, "\$", null),
                    Token(DslTokenType.TEXT, "second", null),
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
                Token(DslTokenType.OPERATOR, "{", null),
                Token(DslTokenType.TEXT, " /\\d+(\\.\\d+)?/ ", null),
                Token(DslTokenType.OPERATOR, "}", null),
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
                Token(DslTokenType.OPERATOR, "{", null),

                Token(DslTokenType.INDENT, "    ", null),
                Token(DslTokenType.TEXT, "SELECT * FROM users", null),

                Token(DslTokenType.INDENT, "    ", null),
                Token(DslTokenType.TEXT, "WHERE name = ", null),
                Token(DslTokenType.OPERATOR, "\$", null),
                Token(DslTokenType.OPERATOR, "{", null),
                Token(DslTokenType.TEXT, "user.name", null),
                Token(DslTokenType.OPERATOR, "}", null),

                Token(DslTokenType.INDENT, "", null),
                Token(DslTokenType.OPERATOR, "}", null),
            ), true)
        }

    }

    private fun test(input: String, expected: List<Token<DslTokenType>>, success: Boolean) {
        if (success) {
            Assertions.assertEquals(expected, DslLexer(input).lex())
        } else {
            try {
                Assertions.assertNotEquals(expected, DslLexer(input).lex())
            } catch (ignored: ParseException) {
            }
        }
    }

}
