package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.Token
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.math.BigInteger
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
        test(input, listOf(Token(RhovasTokenType.IDENTIFIER, input, null)), success)
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
        val value = if (success) BigInteger(input) else null
        test(input, listOf(Token(RhovasTokenType.INTEGER, input, value)), success)
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
        val value = if (success) BigDecimal(input) else null
        test(input, listOf(Token(RhovasTokenType.DECIMAL, input, value)), success)
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
    fun testString(name: String, input: String, expected: String?) {
        test(input, listOf(Token(RhovasTokenType.STRING, input, expected)), expected != null)
    }

    fun testString(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Empty", "\"\"", ""),
            Arguments.of("Single Character", "\"c\"", "c"),
            Arguments.of("Multiple Characters", "\"abc\"", "abc"),
            Arguments.of("Digits", "\"123\"", "123"),
            Arguments.of("Symbols", "\"!@#\"", "!@#"),
            Arguments.of("Whitespace", "\" \t\u000B\"", " \t\u000B"),
            Arguments.of("Unicode", "\"ρ⚡♖\"", "ρ⚡♖"),
            Arguments.of("Escapes", "\"\\n\\r\\t\\\"\\\$\\\\\"", "\n\r\t\"\$\\"),
            Arguments.of("Invalid Escape", "\"\\e\"", null),
            Arguments.of("Unterminated", "\"string", null),
            Arguments.of("Unterminated Newline", "\"string\n\"", null),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testOperator(name: String, input: String, success: Boolean) {
        test(input, listOf(Token(RhovasTokenType.OPERATOR, input, null)), success)
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
                Token(RhovasTokenType.IDENTIFIER, "first", null),
                Token(RhovasTokenType.IDENTIFIER, "second", null),
                Token(RhovasTokenType.IDENTIFIER, "third", null),
            )),
            Arguments.of("Leading Whitespace", "    token", listOf(
                Token(RhovasTokenType.IDENTIFIER, "token", null),
            )),
            Arguments.of("Trailing Whitespace", "token    ", listOf(
                Token(RhovasTokenType.IDENTIFIER, "token", null),
            )),
            //identifier
            Arguments.of("Leading Digits", "123abc", listOf(
                Token(RhovasTokenType.INTEGER, "123", BigInteger("123")),
                Token(RhovasTokenType.IDENTIFIER, "abc", null),
            )),
            //integer
            Arguments.of("Signed Integer", "-123", listOf(
                Token(RhovasTokenType.OPERATOR, "-", null),
                Token(RhovasTokenType.INTEGER, "123", BigInteger("123")),
            )),
            //decimal
            Arguments.of("Leading Decimal", ".123", listOf(
                Token(RhovasTokenType.OPERATOR, ".", null),
                Token(RhovasTokenType.INTEGER, "123", BigInteger("123")),
            )),
            Arguments.of("Trailing Decimal", "123.", listOf(
                Token(RhovasTokenType.INTEGER, "123", BigInteger("123")),
                Token(RhovasTokenType.OPERATOR, ".", null),
            )),
            Arguments.of("Signed Decimal", "-123.456", listOf(
                Token(RhovasTokenType.OPERATOR, "-", null),
                Token(RhovasTokenType.DECIMAL, "123.456", BigDecimal("123.456")),
            )),
            //string
            Arguments.of("Triple Quotes", "\"\"\"string\"\"\"", listOf(
               Token(RhovasTokenType.STRING, "\"\"", ""),
                Token(RhovasTokenType.STRING, "\"string\"", "string"),
                Token(RhovasTokenType.STRING, "\"\"", ""),
            )),
            //operator
            Arguments.of("Multiple Operators", "<=", listOf(
                Token(RhovasTokenType.OPERATOR, "<", null),
                Token(RhovasTokenType.OPERATOR, "=", null),
            )),
            //atom
            Arguments.of("Atom", ":atom", listOf(
                Token(RhovasTokenType.OPERATOR, ":", null),
                Token(RhovasTokenType.IDENTIFIER, "atom", null),
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
                Token(RhovasTokenType.IDENTIFIER, "func", null),
                Token(RhovasTokenType.IDENTIFIER, "main", null),
                Token(RhovasTokenType.OPERATOR, "(", null),
                Token(RhovasTokenType.OPERATOR, ")", null),
                Token(RhovasTokenType.OPERATOR, "{", null),

                Token(RhovasTokenType.IDENTIFIER, "print", null),
                Token(RhovasTokenType.OPERATOR, "(", null),
                Token(RhovasTokenType.STRING, "\"Hello, World!\"", "Hello, World!"),
                Token(RhovasTokenType.OPERATOR, ")", null),
                Token(RhovasTokenType.OPERATOR, ";", null),

                Token(RhovasTokenType.OPERATOR, "}", null),
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
                Token(RhovasTokenType.IDENTIFIER, "func", null),
                Token(RhovasTokenType.IDENTIFIER, "fizzbuzz", null),
                Token(RhovasTokenType.OPERATOR, "(", null),
                Token(RhovasTokenType.IDENTIFIER, "num", null),
                Token(RhovasTokenType.OPERATOR, ":", null),
                Token(RhovasTokenType.IDENTIFIER, "Integer", null),
                Token(RhovasTokenType.OPERATOR, ")", null),
                Token(RhovasTokenType.OPERATOR, "{", null),

                Token(RhovasTokenType.IDENTIFIER, "range", null),
                Token(RhovasTokenType.OPERATOR, "(", null),
                Token(RhovasTokenType.INTEGER, "1", BigInteger("1")),
                Token(RhovasTokenType.OPERATOR, ",", null),
                Token(RhovasTokenType.IDENTIFIER, "num", null),
                Token(RhovasTokenType.OPERATOR, ",", null),
                Token(RhovasTokenType.OPERATOR, ":", null),
                Token(RhovasTokenType.IDENTIFIER, "incl", null),
                Token(RhovasTokenType.OPERATOR, ")", null),
                Token(RhovasTokenType.OPERATOR, ".", null),
                Token(RhovasTokenType.IDENTIFIER, "for", null),
                Token(RhovasTokenType.OPERATOR, "{", null),

                Token(RhovasTokenType.IDENTIFIER, "match", null),
                Token(RhovasTokenType.OPERATOR, "(", null),
                Token(RhovasTokenType.OPERATOR, "[", null),
                Token(RhovasTokenType.IDENTIFIER, "val", null),
                Token(RhovasTokenType.OPERATOR, ".", null),
                Token(RhovasTokenType.IDENTIFIER, "mod", null),
                Token(RhovasTokenType.OPERATOR, "(", null),
                Token(RhovasTokenType.INTEGER, "3", BigInteger("3")),
                Token(RhovasTokenType.OPERATOR, ")", null),
                Token(RhovasTokenType.OPERATOR, ",", null),
                Token(RhovasTokenType.IDENTIFIER, "val", null),
                Token(RhovasTokenType.OPERATOR, ".", null),
                Token(RhovasTokenType.IDENTIFIER, "mod", null),
                Token(RhovasTokenType.OPERATOR, "(", null),
                Token(RhovasTokenType.INTEGER, "5", BigInteger("5")),
                Token(RhovasTokenType.OPERATOR, ")", null),
                Token(RhovasTokenType.OPERATOR, "]", null),
                Token(RhovasTokenType.OPERATOR, ")", null),
                Token(RhovasTokenType.OPERATOR, "{", null),

                Token(RhovasTokenType.OPERATOR, "[", null),
                Token(RhovasTokenType.INTEGER, "0", BigInteger("0")),
                Token(RhovasTokenType.OPERATOR, ",", null),
                Token(RhovasTokenType.INTEGER, "0", BigInteger("0")),
                Token(RhovasTokenType.OPERATOR, "]", null),
                Token(RhovasTokenType.OPERATOR, ":", null),
                Token(RhovasTokenType.IDENTIFIER, "print", null),
                Token(RhovasTokenType.OPERATOR, "(", null),
                Token(RhovasTokenType.STRING, "\"FizzBuzz\"", "FizzBuzz"),
                Token(RhovasTokenType.OPERATOR, ")", null),
                Token(RhovasTokenType.OPERATOR, ";", null),

                Token(RhovasTokenType.OPERATOR, "[", null),
                Token(RhovasTokenType.INTEGER, "0", BigInteger("0")),
                Token(RhovasTokenType.OPERATOR, ",", null),
                Token(RhovasTokenType.IDENTIFIER, "_", null),
                Token(RhovasTokenType.OPERATOR, "]", null),
                Token(RhovasTokenType.OPERATOR, ":", null),
                Token(RhovasTokenType.IDENTIFIER, "print", null),
                Token(RhovasTokenType.OPERATOR, "(", null),
                Token(RhovasTokenType.STRING, "\"Fizz\"", "Fizz"),
                Token(RhovasTokenType.OPERATOR, ")", null),
                Token(RhovasTokenType.OPERATOR, ";", null),

                Token(RhovasTokenType.OPERATOR, "[", null),
                Token(RhovasTokenType.IDENTIFIER, "_", null),
                Token(RhovasTokenType.OPERATOR, ",", null),
                Token(RhovasTokenType.INTEGER, "0", BigInteger("0")),
                Token(RhovasTokenType.OPERATOR, "]", null),
                Token(RhovasTokenType.OPERATOR, ":", null),
                Token(RhovasTokenType.IDENTIFIER, "print", null),
                Token(RhovasTokenType.OPERATOR, "(", null),
                Token(RhovasTokenType.STRING, "\"Buzz\"", "Buzz"),
                Token(RhovasTokenType.OPERATOR, ")", null),
                Token(RhovasTokenType.OPERATOR, ";", null),

                Token(RhovasTokenType.IDENTIFIER, "else", null),
                Token(RhovasTokenType.OPERATOR, ":", null),
                Token(RhovasTokenType.IDENTIFIER, "print", null),
                Token(RhovasTokenType.OPERATOR, "(", null),
                Token(RhovasTokenType.IDENTIFIER, "val", null),
                Token(RhovasTokenType.OPERATOR, ")", null),
                Token(RhovasTokenType.OPERATOR, ";", null),

                Token(RhovasTokenType.OPERATOR, "}", null),

                Token(RhovasTokenType.OPERATOR, "}", null),

                Token(RhovasTokenType.OPERATOR, "}", null),
            )),
        )
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
