package dev.rhovas.interpreter.parser.rhovas

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
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

class RhovasLexerTests {

    @Nested
    inner class WhitespaceTests {

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

    }

    @Nested
    inner class CommentTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testComment(name: String, input: String, success: Boolean) {
            test(input, listOf(), success)
        }

        fun testComment(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Single Comment", "//comment", true),
                Arguments.of("Multiple Comments", "//first\n//second\n//third", true),
            )
        }

    }

    @Nested
    inner class IdentifierTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testIdentifier(name: String, input: String, success: Boolean) {
            test(input, listOf(
                Token(RhovasTokenType.IDENTIFIER, input, null, Input.Range(0, 1, 0, input.length))
            ), success)
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

    }

    @Nested
    inner class IntegerTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testInteger(name: String, input: String, expected: BigInteger?) {
            test(input, listOf(
                Token(RhovasTokenType.INTEGER, input, expected, Input.Range(0, 1, 0, input.length))
            ), expected != null)
        }

        fun testInteger(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Single Digit", "1", BigInteger.parseString("1")),
                Arguments.of("Multiple Digits", "123", BigInteger.parseString("123")),
                Arguments.of("Above Long Max", "1" + "0".repeat(19), BigInteger.parseString("1" + "0".repeat(19))),
                Arguments.of("Signed Integer", "-123", null),
                Arguments.of("Binary", "0b10", BigInteger.parseString("10", 2)),
                Arguments.of("Octal", "0o123", BigInteger.parseString("123", 8)),
                Arguments.of("Hexadecimal", "0x123ABC", BigInteger.parseString("123ABC", 16)),
                Arguments.of("Non-Leading Zero Base", "1b10", null),
                Arguments.of("Trailing Base", "0b", null),
                Arguments.of("Invalid Leading Digit", "0b2", null),
                Arguments.of("Invalid Inner Digit", "0b10201", null),
            )
        }

    }

    @Nested
    inner class DecimalTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testDecimal(name: String, input: String, expected: BigDecimal?) {
            test(input, listOf(
                Token(RhovasTokenType.DECIMAL, input, expected, Input.Range(0, 1, 0, input.length))
            ), expected != null)
        }

        fun testDecimal(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Single Digit", "1.0", BigDecimal.parseString("1.0")),
                Arguments.of("Multiple Digits", "123.456", BigDecimal.parseString("123.456")),
                Arguments.of("Above Double Max", "1" + "0".repeat(308) + ".0", BigDecimal.parseString("1" + "0".repeat(308) + ".0")),
                Arguments.of("Leading Zeros", "000.456", BigDecimal.parseString("000.456")),
                Arguments.of("Trailing Zeros", "123.000", BigDecimal.parseString("123.000")),
                Arguments.of("Leading Decimal", ".456", null),
                Arguments.of("Trailing Decimal", "123.", null),
                Arguments.of("Signed Decimal", "-123.456", null),
                Arguments.of("Scientific", "123.456e789", BigDecimal.parseString("123.456e789")),
                Arguments.of("Signed Exponent", "123.456e-789", BigDecimal.parseString("123.456e-789")),
                Arguments.of("Trailing Exponent", "123.456e", null),
                Arguments.of("Trailing Exponent Sign", "123.456e-", null),
            )
        }

    }

    @Nested
    inner class AtomTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testAtom(name: String, input: String, success: Boolean) {
            test(input, listOf(
                Token(RhovasTokenType.ATOM, input, RhovasAst.Atom(input.substring(1)), Input.Range(0, 1, 0, input.length))
            ), success)
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

    }

    @Nested
    inner class OperatorTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testOperator(name: String, input: String, success: Boolean) {
            test(input, listOf(
                Token(RhovasTokenType.OPERATOR, input, null, Input.Range(0, 1, 0, input.length))
            ), success)
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

    }

    @Nested
    inner class InteractionTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testInteraction(name: String, input: String, expected: List<Token<RhovasTokenType>>) {
            test(input, expected, true)
        }

        fun testInteraction(): Stream<Arguments> {
            return Stream.of(
                //whitespace
                Arguments.of("Inner Whitespace", "first \t\n\rsecond", listOf(
                    Token(RhovasTokenType.IDENTIFIER, "first", null, Input.Range(0, 1, 0, 5)),
                    Token(RhovasTokenType.IDENTIFIER, "second", null, Input.Range(9, 2, 0, 6)),
                )),
                Arguments.of("Leading Whitespace", "    token", listOf(
                    Token(RhovasTokenType.IDENTIFIER, "token", null, Input.Range(4, 1, 4, 5)),
                )),
                Arguments.of("Trailing Whitespace", "token    ", listOf(
                    Token(RhovasTokenType.IDENTIFIER, "token", null, Input.Range(0, 1, 0, 5)),
                )),
                //comment
                Arguments.of("Inner Comment", "first//comment\nsecond", listOf(
                    Token(RhovasTokenType.IDENTIFIER, "first", null, Input.Range(0, 1, 0, 5)),
                    Token(RhovasTokenType.IDENTIFIER, "second", null, Input.Range(15, 2, 0, 6)),
                )),
                Arguments.of("Leading Comment", "//comment\ntoken", listOf(
                    Token(RhovasTokenType.IDENTIFIER, "token", null, Input.Range(10, 2, 0, 5)),
                )),
                Arguments.of("Trailing Comment", "token//comment", listOf(
                    Token(RhovasTokenType.IDENTIFIER, "token", null, Input.Range(0, 1, 0, 5)),
                )),
                //identifier
                Arguments.of("Leading Digits", "123abc", listOf(
                    Token(RhovasTokenType.INTEGER, "123", BigInteger.parseString("123"), Input.Range(0, 1, 0, 3)),
                    Token(RhovasTokenType.IDENTIFIER, "abc", null, Input.Range(3, 1, 3, 3)),
                )),
                //integer
                Arguments.of("Signed Integer", "-123", listOf(
                    Token(RhovasTokenType.OPERATOR, "-", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.INTEGER, "123", BigInteger.parseString("123"), Input.Range(1, 1, 1, 3)),
                )),
                Arguments.of("Non-Leading Zero Base", "1b10", listOf(
                    Token(RhovasTokenType.INTEGER, "1", BigInteger.parseString("1"), Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.IDENTIFIER, "b10", null, Input.Range(1, 1, 1, 3)),
                )),
                Arguments.of("Trailing Base", "0b", listOf(
                    Token(RhovasTokenType.INTEGER, "0", BigInteger.parseString("0"), Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.IDENTIFIER, "b", null, Input.Range(1, 1, 1, 1)),
                )),
                Arguments.of("Invalid Leading Digit", "0b2", listOf(
                    Token(RhovasTokenType.INTEGER, "0", BigInteger.parseString("0"), Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.IDENTIFIER, "b2", null, Input.Range(1, 1, 1, 2)),
                )),
                Arguments.of("Invalid Inner Digit", "0b10201", listOf(
                    Token(RhovasTokenType.INTEGER, "0b10", BigInteger.parseString("10", 2), Input.Range(0, 1, 0, 4)),
                    Token(RhovasTokenType.INTEGER, "201", BigInteger.parseString("201"), Input.Range(4, 1, 4, 3)),
                )),
                //decimal
                Arguments.of("Leading Decimal", ".123", listOf(
                    Token(RhovasTokenType.OPERATOR, ".", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.INTEGER, "123", BigInteger.parseString("123"), Input.Range(1, 1, 1, 3)),
                )),
                Arguments.of("Trailing Decimal", "123.", listOf(
                    Token(RhovasTokenType.INTEGER, "123", BigInteger.parseString("123"), Input.Range(0, 1, 0, 3)),
                    Token(RhovasTokenType.OPERATOR, ".", null, Input.Range(3, 1, 3, 1)),
                )),
                Arguments.of("Signed Decimal", "-123.456", listOf(
                    Token(RhovasTokenType.OPERATOR, "-", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.DECIMAL, "123.456", BigDecimal.parseString("123.456"), Input.Range(1, 1, 1, 7)),
                )),
                Arguments.of("Trailing Exponent", "123.456e", listOf(
                    Token(RhovasTokenType.DECIMAL, "123.456", BigDecimal.parseString("123.456"), Input.Range(0, 1, 0, 7)),
                    Token(RhovasTokenType.IDENTIFIER, "e", null, Input.Range(7, 1, 7, 1)),
                )),
                Arguments.of("Trailing Exponent Sign", "123.456e-", listOf(
                    Token(RhovasTokenType.DECIMAL, "123.456", BigDecimal.parseString("123.456"), Input.Range(0, 1, 0, 7)),
                    Token(RhovasTokenType.IDENTIFIER, "e", null, Input.Range(7, 1, 7, 1)),
                    Token(RhovasTokenType.OPERATOR, "-", null, Input.Range(8, 1, 8, 1)),
                )),
                //string (without string mode)
                Arguments.of("String", "\"string\"", listOf(
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.IDENTIFIER, "string", null, Input.Range(1, 1, 1, 6)),
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(7, 1, 7, 1)),
                )),
                //operator
                Arguments.of("Multiple Operators", "<=", listOf(
                    Token(RhovasTokenType.OPERATOR, "<", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.OPERATOR, "=", null, Input.Range(1, 1, 1, 1)),
                )),
            )
        }

    }

    @Nested
    inner class ProgramTests {

        @Test
        fun testHelloWorld() {
            test("""
                func main() {
                    print("Hello, World!");
                }
            """.trimIndent(), listOf(
                Token(RhovasTokenType.IDENTIFIER, "func", null, Input.Range(0, 1, 0, 4)),
                Token(RhovasTokenType.IDENTIFIER, "main", null, Input.Range(5, 1, 5, 4)),
                Token(RhovasTokenType.OPERATOR, "(", null, Input.Range(9, 1, 9, 1)),
                Token(RhovasTokenType.OPERATOR, ")", null, Input.Range(10, 1, 10, 1)),
                Token(RhovasTokenType.OPERATOR, "{", null, Input.Range(12, 1, 12, 1)),

                Token(RhovasTokenType.IDENTIFIER, "print", null, Input.Range(18, 2, 4, 5)),
                Token(RhovasTokenType.OPERATOR, "(", null, Input.Range(23, 2, 9, 1)),
                Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(24, 2, 10, 1)),
                Token(RhovasTokenType.IDENTIFIER, "Hello", null, Input.Range(25, 2, 11, 5)),
                Token(RhovasTokenType.OPERATOR, ",", null, Input.Range(30, 2, 16, 1)),
                Token(RhovasTokenType.IDENTIFIER, "World!", null, Input.Range(32, 2, 18, 6)),
                Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(38, 2, 24, 1)),
                Token(RhovasTokenType.OPERATOR, ")", null, Input.Range(39, 2, 25, 1)),
                Token(RhovasTokenType.OPERATOR, ";", null, Input.Range(40, 2, 26, 1)),

                Token(RhovasTokenType.OPERATOR, "}", null, Input.Range(42, 3, 0, 1)),
            ), true)
        }

        @Test
        fun testFizzBuzz() {
            test("""
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
                Token(RhovasTokenType.IDENTIFIER, "func", null, Input.Range(0, 1, 0, 4)),
                Token(RhovasTokenType.IDENTIFIER, "fizzbuzz", null, Input.Range(5, 1, 5, 8)),
                Token(RhovasTokenType.OPERATOR, "(", null, Input.Range(13, 1, 13, 1)),
                Token(RhovasTokenType.IDENTIFIER, "num", null, Input.Range(14, 1, 14, 3)),
                Token(RhovasTokenType.OPERATOR, ":", null, Input.Range(17, 1, 17, 1)),
                Token(RhovasTokenType.IDENTIFIER, "Integer", null, Input.Range(19, 1, 19, 7)),
                Token(RhovasTokenType.OPERATOR, ")", null, Input.Range(26, 1, 26, 1)),
                Token(RhovasTokenType.OPERATOR, "{", null, Input.Range(28, 1, 28, 1)),

                Token(RhovasTokenType.IDENTIFIER, "range", null, Input.Range(34, 2, 4, 5)),
                Token(RhovasTokenType.OPERATOR, "(", null, Input.Range(39, 2, 9, 1)),
                Token(RhovasTokenType.INTEGER, "1", BigInteger.parseString("1"), Input.Range(40, 2, 10, 1)),
                Token(RhovasTokenType.OPERATOR, ",", null, Input.Range(41, 2, 11, 1)),
                Token(RhovasTokenType.IDENTIFIER, "num", null, Input.Range(43, 2, 13, 3)),
                Token(RhovasTokenType.OPERATOR, ",", null, Input.Range(46, 2, 16, 1)),
                Token(RhovasTokenType.ATOM, ":incl", RhovasAst.Atom("incl"), Input.Range(48, 2, 18, 5)),
                Token(RhovasTokenType.OPERATOR, ")", null, Input.Range(53, 2, 23, 1)),
                Token(RhovasTokenType.OPERATOR, ".", null, Input.Range(54, 2, 24, 1)),
                Token(RhovasTokenType.IDENTIFIER, "for", null, Input.Range(55, 2, 25, 3)),
                Token(RhovasTokenType.OPERATOR, "{", null, Input.Range(59, 2, 29, 1)),

                Token(RhovasTokenType.IDENTIFIER, "match", null, Input.Range(69, 3, 8, 5)),
                Token(RhovasTokenType.OPERATOR, "(", null, Input.Range(75, 3, 14, 1)),
                Token(RhovasTokenType.OPERATOR, "[", null, Input.Range(76, 3, 15, 1)),
                Token(RhovasTokenType.IDENTIFIER, "val", null, Input.Range(77, 3, 16, 3)),
                Token(RhovasTokenType.OPERATOR, ".", null, Input.Range(80, 3, 19, 1)),
                Token(RhovasTokenType.IDENTIFIER, "mod", null, Input.Range(81, 3, 20, 3)),
                Token(RhovasTokenType.OPERATOR, "(", null, Input.Range(84, 3, 23, 1)),
                Token(RhovasTokenType.INTEGER, "3", BigInteger.parseString("3"), Input.Range(85, 3, 24, 1)),
                Token(RhovasTokenType.OPERATOR, ")", null, Input.Range(86, 3, 25, 1)),
                Token(RhovasTokenType.OPERATOR, ",", null, Input.Range(87, 3, 26, 1)),
                Token(RhovasTokenType.IDENTIFIER, "val", null, Input.Range(89, 3, 28, 3)),
                Token(RhovasTokenType.OPERATOR, ".", null, Input.Range(92, 3, 31, 1)),
                Token(RhovasTokenType.IDENTIFIER, "mod", null, Input.Range(93, 3, 32, 3)),
                Token(RhovasTokenType.OPERATOR, "(", null, Input.Range(96, 3, 35, 1)),
                Token(RhovasTokenType.INTEGER, "5", BigInteger.parseString("5"), Input.Range(97, 3, 36, 1)),
                Token(RhovasTokenType.OPERATOR, ")", null, Input.Range(98, 3, 37, 1)),
                Token(RhovasTokenType.OPERATOR, "]", null, Input.Range(99, 3, 38, 1)),
                Token(RhovasTokenType.OPERATOR, ")", null, Input.Range(100, 3, 39, 1)),
                Token(RhovasTokenType.OPERATOR, "{", null, Input.Range(102, 3, 41, 1)),

                Token(RhovasTokenType.OPERATOR, "[", null, Input.Range(116, 4, 12, 1)),
                Token(RhovasTokenType.INTEGER, "0", BigInteger.parseString("0"), Input.Range(117, 4, 13, 1)),
                Token(RhovasTokenType.OPERATOR, ",", null, Input.Range(118, 4, 14, 1)),
                Token(RhovasTokenType.INTEGER, "0", BigInteger.parseString("0"), Input.Range(120, 4, 16, 1)),
                Token(RhovasTokenType.OPERATOR, "]", null, Input.Range(121, 4, 17, 1)),
                Token(RhovasTokenType.OPERATOR, ":", null, Input.Range(122, 4, 18, 1)),
                Token(RhovasTokenType.IDENTIFIER, "print", null, Input.Range(124, 4, 20, 5)),
                Token(RhovasTokenType.OPERATOR, "(", null, Input.Range(129, 4, 25, 1)),
                Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(130, 4, 26, 1)),
                Token(RhovasTokenType.IDENTIFIER, "FizzBuzz", null, Input.Range(131, 4, 27, 8)),
                Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(139, 4, 35, 1)),
                Token(RhovasTokenType.OPERATOR, ")", null, Input.Range(140, 4, 36, 1)),
                Token(RhovasTokenType.OPERATOR, ";", null, Input.Range(141, 4, 37, 1)),

                Token(RhovasTokenType.OPERATOR, "[", null, Input.Range(155, 5, 12, 1)),
                Token(RhovasTokenType.INTEGER, "0", BigInteger.parseString("0"), Input.Range(156, 5, 13, 1)),
                Token(RhovasTokenType.OPERATOR, ",", null, Input.Range(157, 5, 14, 1)),
                Token(RhovasTokenType.IDENTIFIER, "_", null, Input.Range(159, 5, 16, 1)),
                Token(RhovasTokenType.OPERATOR, "]", null, Input.Range(160, 5, 17, 1)),
                Token(RhovasTokenType.OPERATOR, ":", null, Input.Range(161, 5, 18, 1)),
                Token(RhovasTokenType.IDENTIFIER, "print", null, Input.Range(163, 5, 20, 5)),
                Token(RhovasTokenType.OPERATOR, "(", null, Input.Range(168, 5, 25, 1)),
                Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(169, 5, 26, 1)),
                Token(RhovasTokenType.IDENTIFIER, "Fizz", null, Input.Range(170, 5, 27, 4)),
                Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(174, 5, 31, 1)),
                Token(RhovasTokenType.OPERATOR, ")", null, Input.Range(175, 5, 32, 1)),
                Token(RhovasTokenType.OPERATOR, ";", null, Input.Range(176, 5, 33, 1)),

                Token(RhovasTokenType.OPERATOR, "[", null, Input.Range(190, 6, 12, 1)),
                Token(RhovasTokenType.IDENTIFIER, "_", null, Input.Range(191, 6, 13, 1)),
                Token(RhovasTokenType.OPERATOR, ",", null, Input.Range(192, 6, 14, 1)),
                Token(RhovasTokenType.INTEGER, "0", BigInteger.parseString("0"), Input.Range(194, 6, 16, 1)),
                Token(RhovasTokenType.OPERATOR, "]", null, Input.Range(195, 6, 17, 1)),
                Token(RhovasTokenType.OPERATOR, ":", null, Input.Range(196, 6, 18, 1)),
                Token(RhovasTokenType.IDENTIFIER, "print", null, Input.Range(198, 6, 20, 5)),
                Token(RhovasTokenType.OPERATOR, "(", null, Input.Range(203, 6, 25, 1)),
                Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(204, 6, 26, 1)),
                Token(RhovasTokenType.IDENTIFIER, "Buzz", null, Input.Range(205, 6, 27, 4)),
                Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(209, 6, 31, 1)),
                Token(RhovasTokenType.OPERATOR, ")", null, Input.Range(210, 6, 32, 1)),
                Token(RhovasTokenType.OPERATOR, ";", null, Input.Range(211, 6, 33, 1)),

                Token(RhovasTokenType.IDENTIFIER, "else", null, Input.Range(225, 7, 12, 4)),
                Token(RhovasTokenType.OPERATOR, ":", null, Input.Range(229, 7, 16, 1)),
                Token(RhovasTokenType.IDENTIFIER, "print", null, Input.Range(231, 7, 18, 5)),
                Token(RhovasTokenType.OPERATOR, "(", null, Input.Range(236, 7, 23, 1)),
                Token(RhovasTokenType.IDENTIFIER, "val", null, Input.Range(237, 7, 24, 3)),
                Token(RhovasTokenType.OPERATOR, ")", null, Input.Range(240, 7, 27, 1)),
                Token(RhovasTokenType.OPERATOR, ";", null, Input.Range(241, 7, 28, 1)),

                Token(RhovasTokenType.OPERATOR, "}", null, Input.Range(251, 8, 8, 1)),

                Token(RhovasTokenType.OPERATOR, "}", null, Input.Range(257, 9, 4, 1)),

                Token(RhovasTokenType.OPERATOR, "}", null, Input.Range(259, 10, 0, 1))
            ), true)
        }

    }

    private fun test(input: String, expected: List<Token<RhovasTokenType>>, success: Boolean) {
        val input = Input("Test", input)
        val lexer = RhovasLexer(input)
        try {
            val tokens = generateSequence { lexer.lexToken() }.toList()
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

    @Nested
    inner class StringModeTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testOperator(name: String, input: String) {
            test(input, listOf(
                Token(RhovasTokenType.OPERATOR, input, null, Input.Range(0, 1, 0, input.length))
            ), true)
        }

        fun testOperator(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Double Quote", "\""),
                Arguments.of("Interpolation", "\${"),
            )
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testString(name: String, input: String, expected: String?) {
            test(input, listOf(
                Token(RhovasTokenType.STRING, input, expected, Input.Range(0, 1, 0, input.length))
            ), expected != null)
        }

        fun testString(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Alphanumeric", "abc123", "abc123"),
                Arguments.of("Special", "!@#ρ⚡♖", "!@#ρ⚡♖"),
                Arguments.of("Whitespace", " \t\u000B", " \t\u000B"),
                Arguments.of("Escapes", "\\n\\r\\t\\\"\\\$\\\\", "\n\r\t\"\$\\"),
                Arguments.of("Unicode Escapes", "\\u1234\\uABCD", "\u1234\uABCD"),
                Arguments.of("Invalid Escape", "\"\\e\"", null),
                Arguments.of("Invalid Unicode Escape", "\"\\uXXXX\"", null),
            )
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testInteraction(name: String, input: String, expected: List<Token<RhovasTokenType>>) {
            test(input, expected, true)
        }

        fun testInteraction(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Empty", "\"\"", listOf(
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(1, 1, 1, 1)),
                )),
                Arguments.of("String", "\"string\"", listOf(
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.STRING, "string", "string", Input.Range(1, 1, 1, 6)),
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(7, 1, 7, 1)),
                )),
                Arguments.of("Interpolation", "\"start\${argument}end\"", listOf(
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.STRING, "start", "start", Input.Range(1, 1, 1, 5)),
                    Token(RhovasTokenType.OPERATOR, "\${", null, Input.Range(6, 1, 6, 2)),
                    Token(RhovasTokenType.STRING, "argument}end", "argument}end", Input.Range(8, 1, 8, 12)),
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(20, 1, 20, 1)),
                )),
                Arguments.of("Interpolation Multiple", "\"\${first}\${second}\"", listOf(
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.OPERATOR, "\${", null, Input.Range(1, 1, 1, 2)),
                    Token(RhovasTokenType.STRING, "first}", "first}", Input.Range(3, 1, 3, 6)),
                    Token(RhovasTokenType.OPERATOR, "\${", null, Input.Range(9, 1, 9, 2)),
                    Token(RhovasTokenType.STRING, "second}", "second}", Input.Range(11, 1, 11, 7)),
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(18, 1, 18, 1)),
                )),
                Arguments.of("Unterminated Newline", "\"unterminated\n", listOf(
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.STRING, "unterminated", "unterminated", Input.Range(1, 1, 1, 12)),
                    Token(RhovasTokenType.OPERATOR, "\n", null, Input.Range(13, 1, 13, 1)),
                )),
            )
        }

        private fun test(input: String, expected: List<Token<RhovasTokenType>>, success: Boolean) {
            val input = Input("Test", input)
            val lexer = RhovasLexer(input).also { it.mode = "string" }
            try {
                val tokens = generateSequence { lexer.lexToken() }.toList()
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

}
