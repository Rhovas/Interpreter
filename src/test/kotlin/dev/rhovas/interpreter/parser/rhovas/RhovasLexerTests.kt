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

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testMultiple(name: String, input: String, expected: List<Token<RhovasTokenType>>) {
        test(input, expected, true)
    }

    fun testMultiple(): Stream<Arguments> {
        return Stream.of(
            //whitespace
            Arguments.of("Inner Whitespace", "first \tsecond\n\rthird", listOf(
                Token(RhovasTokenType.IDENTIFIER, "first"),
                Token(RhovasTokenType.IDENTIFIER, "second"),
                Token(RhovasTokenType.IDENTIFIER, "third"),
            )),
            Arguments.of("Leading Whitespace", "    token", listOf(
                Token(RhovasTokenType.IDENTIFIER, "token"),
            )),
            Arguments.of("Trailing Whitespace", "token    ", listOf(
                Token(RhovasTokenType.IDENTIFIER, "token"),
            )),
            //identifier
            Arguments.of("Leading Digits", "123abc", listOf(
                Token(RhovasTokenType.INTEGER, "123"),
                Token(RhovasTokenType.IDENTIFIER, "abc"),
            )),
            //integer
            Arguments.of("Signed Integer", "-123", listOf(
                Token(RhovasTokenType.OPERATOR, "-"),
                Token(RhovasTokenType.INTEGER, "123"),
            )),
            //decimal
            Arguments.of("Leading Decimal", ".123", listOf(
                Token(RhovasTokenType.OPERATOR, "."),
                Token(RhovasTokenType.INTEGER, "123"),
            )),
            Arguments.of("Trailing Decimal", "123.", listOf(
                Token(RhovasTokenType.INTEGER, "123"),
                Token(RhovasTokenType.OPERATOR, "."),
            )),
            Arguments.of("Signed Decimal", "-123.456", listOf(
                Token(RhovasTokenType.OPERATOR, "-"),
                Token(RhovasTokenType.DECIMAL, "123.456"),
            )),
            //string
            Arguments.of("Triple Quotes", "\"\"\"string\"\"\"", listOf(
               Token(RhovasTokenType.STRING, "\"\""),
                Token(RhovasTokenType.STRING, "\"string\""),
                Token(RhovasTokenType.STRING, "\"\""),
            )),
            //operator
            Arguments.of("Multiple Operators", "<=", listOf(
                Token(RhovasTokenType.OPERATOR, "<"),
                Token(RhovasTokenType.OPERATOR, "="),
            )),
            //atom
            Arguments.of("Atom", ":atom", listOf(
                Token(RhovasTokenType.OPERATOR, ":"),
                Token(RhovasTokenType.IDENTIFIER, "atom"),
            )),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testProgram(name: String, input: String, expected: List<Token<RhovasTokenType>>) {
        test(input, expected, true)
    }

    fun testProgram(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Hello World", """
                func main() {
                    print("Hello, World!");
                }
            """.trimIndent(), listOf(
                Token(RhovasTokenType.IDENTIFIER, "func"),
                Token(RhovasTokenType.IDENTIFIER, "main"),
                Token(RhovasTokenType.OPERATOR, "("),
                Token(RhovasTokenType.OPERATOR, ")"),
                Token(RhovasTokenType.OPERATOR, "{"),

                Token(RhovasTokenType.IDENTIFIER, "print"),
                Token(RhovasTokenType.OPERATOR, "("),
                Token(RhovasTokenType.STRING, "\"Hello, World!\""),
                Token(RhovasTokenType.OPERATOR, ")"),
                Token(RhovasTokenType.OPERATOR, ";"),

                Token(RhovasTokenType.OPERATOR, "}"),
            )),
            Arguments.of("FizzBuzz", """
                func fizzbuzz(num: Integer) {
                    range(1, num, :incl).for {
                        match ([val.mod(3), val.mod(5)]) {
                            [0, 0]: print("FizzBuzz");
                            [0, _]: print("Fizz");
                            [_, 0]: print("Buzz");
                            else: print(val);
                        }
                    }
                }
            """.trimIndent(), listOf(
                Token(RhovasTokenType.IDENTIFIER, "func"),
                Token(RhovasTokenType.IDENTIFIER, "fizzbuzz"),
                Token(RhovasTokenType.OPERATOR, "("),
                Token(RhovasTokenType.IDENTIFIER, "num"),
                Token(RhovasTokenType.OPERATOR, ":"),
                Token(RhovasTokenType.IDENTIFIER, "Integer"),
                Token(RhovasTokenType.OPERATOR, ")"),
                Token(RhovasTokenType.OPERATOR, "{"),

                Token(RhovasTokenType.IDENTIFIER, "range"),
                Token(RhovasTokenType.OPERATOR, "("),
                Token(RhovasTokenType.INTEGER, "1"),
                Token(RhovasTokenType.OPERATOR, ","),
                Token(RhovasTokenType.IDENTIFIER, "num"),
                Token(RhovasTokenType.OPERATOR, ","),
                Token(RhovasTokenType.OPERATOR, ":"),
                Token(RhovasTokenType.IDENTIFIER, "incl"),
                Token(RhovasTokenType.OPERATOR, ")"),
                Token(RhovasTokenType.OPERATOR, "."),
                Token(RhovasTokenType.IDENTIFIER, "for"),
                Token(RhovasTokenType.OPERATOR, "{"),

                Token(RhovasTokenType.IDENTIFIER, "match"),
                Token(RhovasTokenType.OPERATOR, "("),
                Token(RhovasTokenType.OPERATOR, "["),
                Token(RhovasTokenType.IDENTIFIER, "val"),
                Token(RhovasTokenType.OPERATOR, "."),
                Token(RhovasTokenType.IDENTIFIER, "mod"),
                Token(RhovasTokenType.OPERATOR, "("),
                Token(RhovasTokenType.INTEGER, "3"),
                Token(RhovasTokenType.OPERATOR, ")"),
                Token(RhovasTokenType.OPERATOR, ","),
                Token(RhovasTokenType.IDENTIFIER, "val"),
                Token(RhovasTokenType.OPERATOR, "."),
                Token(RhovasTokenType.IDENTIFIER, "mod"),
                Token(RhovasTokenType.OPERATOR, "("),
                Token(RhovasTokenType.INTEGER, "5"),
                Token(RhovasTokenType.OPERATOR, ")"),
                Token(RhovasTokenType.OPERATOR, "]"),
                Token(RhovasTokenType.OPERATOR, ")"),
                Token(RhovasTokenType.OPERATOR, "{"),

                Token(RhovasTokenType.OPERATOR, "["),
                Token(RhovasTokenType.INTEGER, "0"),
                Token(RhovasTokenType.OPERATOR, ","),
                Token(RhovasTokenType.INTEGER, "0"),
                Token(RhovasTokenType.OPERATOR, "]"),
                Token(RhovasTokenType.OPERATOR, ":"),
                Token(RhovasTokenType.IDENTIFIER, "print"),
                Token(RhovasTokenType.OPERATOR, "("),
                Token(RhovasTokenType.STRING, "\"FizzBuzz\""),
                Token(RhovasTokenType.OPERATOR, ")"),
                Token(RhovasTokenType.OPERATOR, ";"),

                Token(RhovasTokenType.OPERATOR, "["),
                Token(RhovasTokenType.INTEGER, "0"),
                Token(RhovasTokenType.OPERATOR, ","),
                Token(RhovasTokenType.IDENTIFIER, "_"),
                Token(RhovasTokenType.OPERATOR, "]"),
                Token(RhovasTokenType.OPERATOR, ":"),
                Token(RhovasTokenType.IDENTIFIER, "print"),
                Token(RhovasTokenType.OPERATOR, "("),
                Token(RhovasTokenType.STRING, "\"Fizz\""),
                Token(RhovasTokenType.OPERATOR, ")"),
                Token(RhovasTokenType.OPERATOR, ";"),

                Token(RhovasTokenType.OPERATOR, "["),
                Token(RhovasTokenType.IDENTIFIER, "_"),
                Token(RhovasTokenType.OPERATOR, ","),
                Token(RhovasTokenType.INTEGER, "0"),
                Token(RhovasTokenType.OPERATOR, "]"),
                Token(RhovasTokenType.OPERATOR, ":"),
                Token(RhovasTokenType.IDENTIFIER, "print"),
                Token(RhovasTokenType.OPERATOR, "("),
                Token(RhovasTokenType.STRING, "\"Buzz\""),
                Token(RhovasTokenType.OPERATOR, ")"),
                Token(RhovasTokenType.OPERATOR, ";"),

                Token(RhovasTokenType.IDENTIFIER, "else"),
                Token(RhovasTokenType.OPERATOR, ":"),
                Token(RhovasTokenType.IDENTIFIER, "print"),
                Token(RhovasTokenType.OPERATOR, "("),
                Token(RhovasTokenType.IDENTIFIER, "val"),
                Token(RhovasTokenType.OPERATOR, ")"),
                Token(RhovasTokenType.OPERATOR, ";"),

                Token(RhovasTokenType.OPERATOR, "}"),

                Token(RhovasTokenType.OPERATOR, "}"),

                Token(RhovasTokenType.OPERATOR, "}"),
            )),
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
