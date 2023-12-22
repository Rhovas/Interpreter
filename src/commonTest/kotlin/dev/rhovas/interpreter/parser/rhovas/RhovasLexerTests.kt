package dev.rhovas.interpreter.parser.rhovas

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.Token
import dev.rhovas.interpreter.parser.dsl.DslTokenType
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail

class RhovasLexerTests: RhovasSpec() {

    data class Test<T>(val source: String, val argument: T)

    init {
        suite("Whitespace", listOf(
            "Space" to Test(" ", true),
            "Tab" to Test("\t", true),
            "Newline" to Test("\n", true),
            "Carriage Return" to Test("\r", true),
            "Mixed" to Test(" \t\n\r", true),
            "Non-Whitespace" to Test("\u000B", false),
        )) { test(it.source, listOf(), it.argument) }

        suite("Comment", listOf(
            "Single Comment" to Test("//comment", true),
            "Multiple Comments" to Test("//first\n//second\n//third", true),
        )) { test(it.source, listOf(), it.argument) }

        suite("Identifier", listOf(
            "Single Letter" to Test("c", true),
            "Single Digit" to Test("1", false),
            "Single Underscore" to Test("_", true),
            "Lowercase Letters" to Test("abc", true),
            "Uppercase Letters" to Test("ABC", true),
            "Digits" to Test("abc123def", true),
            "Leading Digits" to Test("123abc", false),
            "Trailing Digits" to Test("abc123", true),
            "Underscore" to Test("abc_def", true),
            "Leading Underscore" to Test("_abc", true),
            "Trailing Underscore" to Test("abc_", true),
            "Keyword" to Test("class", true),
        )) { test(it.source, listOf(Token(RhovasTokenType.IDENTIFIER, it.source, null, Input.Range(0, 1, 0, it.source.length))), it.argument) }

        suite("Integer", listOf(
            "Single Digit" to Test("1", BigInteger.parseString("1")),
            "Multiple Digits" to Test("123", BigInteger.parseString("123")),
            "Above Long Max" to Test("1" + "0".repeat(19), BigInteger.parseString("1" + "0".repeat(19))),
            "Signed Integer" to Test("-123", null),
            "Binary" to Test("0b10", BigInteger.parseString("10", 2)),
            "Octal" to Test("0o123", BigInteger.parseString("123", 8)),
            "Hexadecimal" to Test("0x123ABC", BigInteger.parseString("123ABC", 16)),
            "Non-Leading Zero Base" to Test("1b10", null),
            "Trailing Base" to Test("0b", null),
            "Invalid Leading Digit" to Test("0b2", null),
            "Invalid Inner Digit" to Test("0b10201", null),
        )) { test(it.source, listOf(Token(RhovasTokenType.INTEGER, it.source, it.argument, Input.Range(0, 1, 0, it.source.length))), it.argument != null) }

        suite("Decimal", listOf(
            "Single Digit" to Test("1.0", BigDecimal.parseString("1.0")),
            "Multiple Digits" to Test("123.456", BigDecimal.parseString("123.456")),
            "Above Double Max" to Test("1" + "0".repeat(308) + ".0", BigDecimal.parseString("1" + "0".repeat(308) + ".0")),
            "Leading Zeros" to Test("000.456", BigDecimal.parseString("000.456")),
            "Trailing Zeros" to Test("123.000", BigDecimal.parseString("123.000")),
            "Leading Decimal" to Test(".456", null),
            "Trailing Decimal" to Test("123.", null),
            "Signed Decimal" to Test("-123.456", null),
            "Scientific" to Test("123.456e789", BigDecimal.parseString("123.456e789")),
            "Signed Exponent" to Test("123.456e-789", BigDecimal.parseString("123.456e-789")),
            "Trailing Exponent" to Test("123.456e", null),
            "Trailing Exponent Sign" to Test("123.456e-", null),
        )) { test(it.source, listOf(Token(RhovasTokenType.DECIMAL, it.source, it.argument, Input.Range(0, 1, 0, it.source.length))), it.argument != null) }

        suite("Atom", listOf(
            "Empty" to Test(":", false),
            "Single Letter" to Test(":c", true),
            "Single Digit" to Test(":1", false),
            "Single Underscore" to Test(":_", true),
            "Lowercase Letters" to Test(":abc", true),
            "Uppercase Letters" to Test(":ABC", true),
            "Digits" to Test(":abc123def", true),
            "Leading Digits" to Test(":123abc", false),
            "Trailing Digits" to Test(":abc123", true),
            "Underscore" to Test(":abc_def", true),
            "Leading Underscore" to Test(":_abc", true),
            "Trailing Underscore" to Test(":abc_", true),
            "Keyword" to Test(":class", true),
        )) { test(it.source, listOf(Token(RhovasTokenType.ATOM, it.source, RhovasAst.Atom(it.source.trimStart(':')), Input.Range(0, 1, 0, it.source.length))), it.argument) }

        suite("Operator", listOf(
            "Empty" to Test("", false),
            "Single Operator" to Test("+", true),
            "Multiple Operators" to Test("<=", false),
            "Unicode" to Test("ρ", true),
            "Period" to Test(".", true),
            "Colon" to Test(":", true),
            "Single Quote" to Test("\'", true),
            "Whitespace" to Test(" ", false),
            "Non-Whitespace" to Test("\u000B", true),
        )) { test(it.source, listOf(Token(RhovasTokenType.OPERATOR, it.source, null, Input.Range(0, 1, 0, it.source.length))), it.argument) }

        suite("Interaction", listOf(
            //whitespace
            "Inner Whitespace" to Test("first \t\n\rsecond", listOf(
                Token(RhovasTokenType.IDENTIFIER, "first", null, Input.Range(0, 1, 0, 5)),
                Token(RhovasTokenType.IDENTIFIER, "second", null, Input.Range(9, 2, 0, 6)),
            )),
            "Leading Whitespace" to Test("    token", listOf(
                Token(RhovasTokenType.IDENTIFIER, "token", null, Input.Range(4, 1, 4, 5)),
            )),
            "Trailing Whitespace" to Test("token    ", listOf(
                Token(RhovasTokenType.IDENTIFIER, "token", null, Input.Range(0, 1, 0, 5)),
            )),
            //comment
            "Inner Comment" to Test("first//comment\nsecond", listOf(
                Token(RhovasTokenType.IDENTIFIER, "first", null, Input.Range(0, 1, 0, 5)),
                Token(RhovasTokenType.IDENTIFIER, "second", null, Input.Range(15, 2, 0, 6)),
            )),
            "Leading Comment" to Test("//comment\ntoken", listOf(
                Token(RhovasTokenType.IDENTIFIER, "token", null, Input.Range(10, 2, 0, 5)),
            )),
            "Trailing Comment" to Test("token//comment", listOf(
                Token(RhovasTokenType.IDENTIFIER, "token", null, Input.Range(0, 1, 0, 5)),
            )),
            //identifier
            "Leading Digits" to Test("123abc", listOf(
                Token(RhovasTokenType.INTEGER, "123", BigInteger.parseString("123"), Input.Range(0, 1, 0, 3)),
                Token(RhovasTokenType.IDENTIFIER, "abc", null, Input.Range(3, 1, 3, 3)),
            )),
            //integer
            "Signed Integer" to Test("-123", listOf(
                Token(RhovasTokenType.OPERATOR, "-", null, Input.Range(0, 1, 0, 1)),
                Token(RhovasTokenType.INTEGER, "123", BigInteger.parseString("123"), Input.Range(1, 1, 1, 3)),
            )),
            "Non-Leading Zero Base" to Test("1b10", listOf(
                Token(RhovasTokenType.INTEGER, "1", BigInteger.parseString("1"), Input.Range(0, 1, 0, 1)),
                Token(RhovasTokenType.IDENTIFIER, "b10", null, Input.Range(1, 1, 1, 3)),
            )),
            "Trailing Base" to Test("0b", listOf(
                Token(RhovasTokenType.INTEGER, "0", BigInteger.parseString("0"), Input.Range(0, 1, 0, 1)),
                Token(RhovasTokenType.IDENTIFIER, "b", null, Input.Range(1, 1, 1, 1)),
            )),
            "Invalid Leading Digit" to Test("0b2", listOf(
                Token(RhovasTokenType.INTEGER, "0", BigInteger.parseString("0"), Input.Range(0, 1, 0, 1)),
                Token(RhovasTokenType.IDENTIFIER, "b2", null, Input.Range(1, 1, 1, 2)),
            )),
            "Invalid Inner Digit" to Test("0b10201", listOf(
                Token(RhovasTokenType.INTEGER, "0b10", BigInteger.parseString("10", 2), Input.Range(0, 1, 0, 4)),
                Token(RhovasTokenType.INTEGER, "201", BigInteger.parseString("201"), Input.Range(4, 1, 4, 3)),
            )),
            //decimal
            "Leading Decimal" to Test(".123", listOf(
                Token(RhovasTokenType.OPERATOR, ".", null, Input.Range(0, 1, 0, 1)),
                Token(RhovasTokenType.INTEGER, "123", BigInteger.parseString("123"), Input.Range(1, 1, 1, 3)),
            )),
            "Trailing Decimal" to Test("123.", listOf(
                Token(RhovasTokenType.INTEGER, "123", BigInteger.parseString("123"), Input.Range(0, 1, 0, 3)),
                Token(RhovasTokenType.OPERATOR, ".", null, Input.Range(3, 1, 3, 1)),
            )),
            "Signed Decimal" to Test("-123.456", listOf(
                Token(RhovasTokenType.OPERATOR, "-", null, Input.Range(0, 1, 0, 1)),
                Token(RhovasTokenType.DECIMAL, "123.456", BigDecimal.parseString("123.456"), Input.Range(1, 1, 1, 7)),
            )),
            "Trailing Exponent" to Test("123.456e", listOf(
                Token(RhovasTokenType.DECIMAL, "123.456", BigDecimal.parseString("123.456"), Input.Range(0, 1, 0, 7)),
                Token(RhovasTokenType.IDENTIFIER, "e", null, Input.Range(7, 1, 7, 1)),
            )),
            "Trailing Exponent Sign" to Test("123.456e-", listOf(
                Token(RhovasTokenType.DECIMAL, "123.456", BigDecimal.parseString("123.456"), Input.Range(0, 1, 0, 7)),
                Token(RhovasTokenType.IDENTIFIER, "e", null, Input.Range(7, 1, 7, 1)),
                Token(RhovasTokenType.OPERATOR, "-", null, Input.Range(8, 1, 8, 1)),
            )),
            //string (without string mode)
            "String" to Test("\"string\"", listOf(
                Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(0, 1, 0, 1)),
                Token(RhovasTokenType.IDENTIFIER, "string", null, Input.Range(1, 1, 1, 6)),
                Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(7, 1, 7, 1)),
            )),
            //operator
            "Multiple Operators" to Test("<=", listOf(
                Token(RhovasTokenType.OPERATOR, "<", null, Input.Range(0, 1, 0, 1)),
                Token(RhovasTokenType.OPERATOR, "=", null, Input.Range(1, 1, 1, 1)),
            )),
        )) { test(it.source, it.argument, true) }

        suite("Program", listOf(
            "Hello World" to Test("""
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
            )),
            "FizzBuzz" to Test("""
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
            )),
        )) { test(it.source, it.argument, true) }

        suite("StringMode") {
            suite("Operator", listOf(
                "Double Quote" to Test("\"", true),
                "Interpolation" to Test("\${", true),
            )) { test(it.source, listOf(Token(RhovasTokenType.OPERATOR, it.source, null, Input.Range(0, 1, 0, it.source.length))), it.argument, "string") }

            suite("String", listOf(
                "Alphanumeric" to Test("abc123", "abc123"),
                "Special" to Test("!@#ρ⚡♖", "!@#ρ⚡♖"),
                "Whitespace" to Test(" \t\u000B", " \t\u000B"),
                "Escapes" to Test("\\n\\r\\t\\\"\\\$\\\\", "\n\r\t\"\$\\"),
                "Unicode Escapes" to Test("\\u1234\\uABCD", "\u1234\uABCD"),
                "Invalid Escape" to Test("\"\\e\"", null),
                "Invalid Unicode Escape" to Test("\"\\uXXXX\"", null),
            )) { test(it.source, listOf(Token(RhovasTokenType.STRING, it.source, it.argument, Input.Range(0, 1, 0, it.source.length))), it.argument != null, "string") }

            suite("Interaction", listOf(
                "Empty" to Test("\"\"", listOf(
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(1, 1, 1, 1)),
                )),
                "String" to Test("\"string\"", listOf(
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.STRING, "string", "string", Input.Range(1, 1, 1, 6)),
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(7, 1, 7, 1)),
                )),
                "Interpolation" to Test("\"start\${argument}end\"", listOf(
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.STRING, "start", "start", Input.Range(1, 1, 1, 5)),
                    Token(RhovasTokenType.OPERATOR, "\${", null, Input.Range(6, 1, 6, 2)),
                    Token(RhovasTokenType.STRING, "argument}end", "argument}end", Input.Range(8, 1, 8, 12)),
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(20, 1, 20, 1)),
                )),
                "Interpolation Multiple" to Test("\"\${first}\${second}\"", listOf(
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.OPERATOR, "\${", null, Input.Range(1, 1, 1, 2)),
                    Token(RhovasTokenType.STRING, "first}", "first}", Input.Range(3, 1, 3, 6)),
                    Token(RhovasTokenType.OPERATOR, "\${", null, Input.Range(9, 1, 9, 2)),
                    Token(RhovasTokenType.STRING, "second}", "second}", Input.Range(11, 1, 11, 7)),
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(18, 1, 18, 1)),
                )),
                "Unterminated Newline" to Test("\"unterminated\n", listOf(
                    Token(RhovasTokenType.OPERATOR, "\"", null, Input.Range(0, 1, 0, 1)),
                    Token(RhovasTokenType.STRING, "unterminated", "unterminated", Input.Range(1, 1, 1, 12)),
                    Token(RhovasTokenType.OPERATOR, "\n", null, Input.Range(13, 1, 13, 1)),
                )),
            )) { test(it.source, it.argument, true, "string") }
        }
    }

    private fun test(input: String, expected: List<Token<RhovasTokenType>>, success: Boolean, mode: String = "") {
        val input = Input("Test", input)
        val lexer = RhovasLexer(input).also { it.mode = mode }
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
