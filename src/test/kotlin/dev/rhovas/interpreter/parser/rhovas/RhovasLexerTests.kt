package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.Token
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RhovasLexerTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testWhitespace(name: String, input: String, success: Boolean) {
        test(input, listOf(), success)
    }

    fun testWhitespace(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Space", " ", true),
            Arguments.of("Tab", "\t", true),
            Arguments.of("Newline", "\n", true),
            Arguments.of("Carriage Return", "\r", true),
            Arguments.of("Mixed", " \t\n\r", true),
            Arguments.of("Non-Whitespace", "\u000B", false),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testIdentifier(name: String, input: String, success: Boolean) {
        test(input, RhovasTokenType.IDENTIFIER, success)
    }

    fun testIdentifier(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Single Letter", "c", true),
            Arguments.of("Single Digit", "1", false),
            Arguments.of("Single Underscore", "_", true),
            Arguments.of("Lowercase Letters", "abc", true),
            Arguments.of("Uppercase Letters", "ABC", true),
            Arguments.of("Digits", "abc123def", true),
            Arguments.of("Leading Digits", "123abc", false),
            Arguments.of("Trailing Digits", "abc123", true),
            Arguments.of("Underscore", "abc_def", true),
            Arguments.of("Leading Underscore", "_abc", true),
            Arguments.of("Trailing Underscore", "abc_", true),
            Arguments.of("Keyword", "class", true),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testInteger(name: String, input: String, success: Boolean) {
        test(input, RhovasTokenType.INTEGER, success)
    }

    fun testInteger(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Single Digit", "1", true),
            Arguments.of("Multiple Digits", "123", true),
            Arguments.of("Above Long Max", "1" + "0".repeat(19), true),
            Arguments.of("Signed Integer", "-123", false),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testDecimal(name: String, input: String, success: Boolean) {
        test(input, RhovasTokenType.DECIMAL, success)
    }

    fun testDecimal(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Single Digit", "1.0", true),
            Arguments.of("Multiple Digits", "123.456", true),
            Arguments.of("Above Double Max", "1" + "0".repeat(308) + ".0", true),
            Arguments.of("Leading Zeros", "000.456", true),
            Arguments.of("Trailing Zeros", "123.000", true),
            Arguments.of("Leading Decimal", ".456", false),
            Arguments.of("Trailing Decimal", "123.", false),
            Arguments.of("Signed Decimal", "-123.456", false),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testString(name: String, input: String, success: Boolean) {
        test(input, RhovasTokenType.STRING, success)
    }

    fun testString(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Empty", "\"\"", true),
            Arguments.of("Single Character", "\"c\"", true),
            Arguments.of("Multiple Characters", "\"abc\"", true),
            Arguments.of("Digits", "\"123\"", true),
            Arguments.of("Symbols", "\"!@#\"", true),
            Arguments.of("Whitespace", "\" \t\u000B\"", true),
            Arguments.of("Unicode", "\"ρ⚡♖\"", true),
            Arguments.of("Escapes", "\"\\n\\r\\t\\\"\\\$\\\\\"", true),
            Arguments.of("Invalid Escape", "\"\\e\"", false),
            Arguments.of("Unterminated", "\"string", false),
            Arguments.of("Unterminated Newline", "\"string\n\"", false),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testAtom(name: String, input: String, success: Boolean) {
        test(input, RhovasTokenType.ATOM, success)
    }

    fun testAtom(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Empty", ":", false),
            Arguments.of("Single Letter", ":c", true),
            Arguments.of("Single Digit", ":1", false),
            Arguments.of("Single Underscore", ":_", true),
            Arguments.of("Lowercase Letters", ":abc", true),
            Arguments.of("Uppercase Letters", ":ABC", true),
            Arguments.of("Digits", ":abc123def", true),
            Arguments.of("Leading Digits", ":123abc", false),
            Arguments.of("Trailing Digits", ":abc123", true),
            Arguments.of("Underscore", ":abc_def", true),
            Arguments.of("Leading Underscore", ":_abc", true),
            Arguments.of("Trailing Underscore", ":abc_", true),
            Arguments.of("Keyword", ":class", true),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testOperator(name: String, input: String, success: Boolean) {
        test(input, RhovasTokenType.OPERATOR, success)
    }

    fun testOperator(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Empty", "", false),
            Arguments.of("Single Operator", "+", true),
            Arguments.of("Multiple Operators", "<=", false),
            Arguments.of("Unicode", "ρ", true),
            Arguments.of("Period", ".", true),
            Arguments.of("Colon", ":", true),
            Arguments.of("Single Quote", "\'", true),
            Arguments.of("Whitespace", " ", false),
            Arguments.of("Non-Whitespace", "\u000B", true),
        )
    }

    private fun test(input: String, expected: RhovasTokenType, success: Boolean) {
        test(input, listOf(Token(expected, input)), success)
    }

    private fun test(input: String, expected: List<Token<RhovasTokenType>>, success: Boolean) {
        if (success) {
            Assertions.assertEquals(expected, RhovasLexer(input).lex())
        } else {
            try {
                Assertions.assertNotEquals(expected, RhovasLexer(input).lex())
            } catch (ignored: ParseException) {
            }
        }
    }

}
