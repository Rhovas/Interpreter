package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.ParseException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.math.BigInteger
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RhovasParserTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testLiteral(name: String, input: String, expected: Any?) {
        testExpression(input, RhovasAst.Expression.Literal(expected))
    }

    fun testLiteral(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Null", "null", null),
            Arguments.of("Boolean True", "true", true),
            Arguments.of("Boolean False", "false", false),
            Arguments.of("Integer", "123", BigInteger("123")),
            Arguments.of("Integer Above Long Max", "1" + "0".repeat(19), BigInteger("1" + "0".repeat(19))),
            Arguments.of("Decimal", "123.456", BigDecimal("123.456")),
            Arguments.of("Decimal Above Double Max", "1" + "0".repeat(308) + ".0", BigDecimal("1" + "0".repeat(308) + ".0")),
            Arguments.of("String", "\"string\"", "string"),
            //TODO: Arguments.of("String Escapes", "\"\\n\\r\\t\\\"\\\$\\\\\"", "\n\r\t\"\$\\"),
            Arguments.of("Atom", ":atom", RhovasAst.Atom("atom")),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testGroup(name: String, input: String, expected: RhovasAst.Expression.Group?) {
        testExpression(input, expected)
    }

    fun testGroup(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Group", "(expr)",
                RhovasAst.Expression.Group(RhovasAst.Expression.Access(null, "expr"))
            ),
            Arguments.of("Nested", "(((expr)))",
                RhovasAst.Expression.Group(
                    RhovasAst.Expression.Group(
                        RhovasAst.Expression.Group(
                            RhovasAst.Expression.Access(null, "expr"),
                        ),
                    ),
                ),
            ),
            Arguments.of("Binary", "(first + second)",
                RhovasAst.Expression.Group(
                    RhovasAst.Expression.Binary("+",
                        RhovasAst.Expression.Access(null, "first"),
                        RhovasAst.Expression.Access(null, "second"),
                    ),
                ),
            ),
            Arguments.of("Empty", "()", null),
            Arguments.of("Tuple", "(first, second)", null),
            Arguments.of("Missing Closing Parenthesis", "(expr", null),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testUnary(name: String, input: String, expected: RhovasAst.Expression.Unary?) {
        testExpression(input, expected)
    }

    fun testUnary(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Numerical Negation", "-expr",
                RhovasAst.Expression.Unary("-", RhovasAst.Expression.Access(null, "expr"))
            ),
            Arguments.of("Logical Negation", "!expr",
                RhovasAst.Expression.Unary("!", RhovasAst.Expression.Access(null, "expr"))
            ),
            Arguments.of("Multiple", "-!expr",
                RhovasAst.Expression.Unary("-", RhovasAst.Expression.Unary("!", RhovasAst.Expression.Access(null, "expr")))
            ),
            Arguments.of("Unknown Operator", "+expr", null),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testBinary(name: String, input: String, expected: RhovasAst.Expression.Binary?) {
        testExpression(input, expected)
    }

    fun testBinary(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Multiplicative", "left * right",
                RhovasAst.Expression.Binary("*",
                    RhovasAst.Expression.Access(null, "left"),
                    RhovasAst.Expression.Access(null, "right"),
                ),
            ),
            Arguments.of("Additive", "left + right",
                RhovasAst.Expression.Binary("+",
                    RhovasAst.Expression.Access(null, "left"),
                    RhovasAst.Expression.Access(null, "right"),
                ),
            ),
            Arguments.of("Comparison", "left < right",
                RhovasAst.Expression.Binary("<",
                    RhovasAst.Expression.Access(null, "left"),
                    RhovasAst.Expression.Access(null, "right"),
                ),
            ),
            Arguments.of("Equality", "left == right",
                RhovasAst.Expression.Binary("==",
                    RhovasAst.Expression.Access(null, "left"),
                    RhovasAst.Expression.Access(null, "right"),
                ),
            ),
            Arguments.of("Logical And", "left && right",
                RhovasAst.Expression.Binary("&&",
                    RhovasAst.Expression.Access(null, "left"),
                    RhovasAst.Expression.Access(null, "right"),
                ),
            ),
            Arguments.of("Logical Or", "left || right",
                RhovasAst.Expression.Binary("||",
                    RhovasAst.Expression.Access(null, "left"),
                    RhovasAst.Expression.Access(null, "right"),
                ),
            ),
            Arguments.of("Left Precedence", "first * second + third < fourth == fifth && sixth || seventh",
                RhovasAst.Expression.Binary("||",
                    RhovasAst.Expression.Binary("&&",
                        RhovasAst.Expression.Binary("==",
                            RhovasAst.Expression.Binary("<",
                                RhovasAst.Expression.Binary("+",
                                    RhovasAst.Expression.Binary("*",
                                        RhovasAst.Expression.Access(null, "first"),
                                        RhovasAst.Expression.Access(null, "second"),
                                    ),
                                    RhovasAst.Expression.Access(null, "third"),
                                ),
                                RhovasAst.Expression.Access(null, "fourth"),
                            ),
                            RhovasAst.Expression.Access(null, "fifth"),
                        ),
                        RhovasAst.Expression.Access(null, "sixth"),
                    ),
                    RhovasAst.Expression.Access(null, "seventh"),
                ),
            ),
            Arguments.of("Right Precedence", "first || second && third == fourth < fifth + sixth * seventh",
                RhovasAst.Expression.Binary("||",
                    RhovasAst.Expression.Access(null, "first"),
                    RhovasAst.Expression.Binary("&&",
                        RhovasAst.Expression.Access(null, "second"),
                        RhovasAst.Expression.Binary("==",
                            RhovasAst.Expression.Access(null, "third"),
                            RhovasAst.Expression.Binary("<",
                                RhovasAst.Expression.Access(null, "fourth"),
                                RhovasAst.Expression.Binary("+",
                                    RhovasAst.Expression.Access(null, "fifth"),
                                    RhovasAst.Expression.Binary("*",
                                        RhovasAst.Expression.Access(null, "sixth"),
                                        RhovasAst.Expression.Access(null, "seventh"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            Arguments.of("Equal Precedence", "first < second <= third",
                RhovasAst.Expression.Binary("<=",
                    RhovasAst.Expression.Binary("<",
                        RhovasAst.Expression.Access(null, "first"),
                        RhovasAst.Expression.Access(null, "second"),
                    ),
                    RhovasAst.Expression.Access(null, "third"),
                ),
            ),
            Arguments.of("Missing Right", "first +", null),
            Arguments.of("Unknown Operator", "first % second", null),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testAccess(name: String, input: String, expected: RhovasAst.Expression.Access?) {
        testExpression(input, expected)
    }

    fun testAccess(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Variable", "variable",
                RhovasAst.Expression.Access(null, "variable")
            ),
            Arguments.of("Field", "object.field",
                RhovasAst.Expression.Access(
                    RhovasAst.Expression.Access(null, "object"),
                    "field",
                ),
            ),
            Arguments.of("Multiple Fields", "first.second.third",
                RhovasAst.Expression.Access(
                    RhovasAst.Expression.Access(
                        RhovasAst.Expression.Access(null, "first"),
                        "second",
                    ),
                    "third",
                ),
            )
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testIndex(name: String, input: String, expected: RhovasAst.Expression.Index?) {
        testExpression(input, expected)
    }

    fun testIndex(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Zero Arguments", "object[]",
                RhovasAst.Expression.Index(
                    RhovasAst.Expression.Access(null, "object"),
                    listOf(),
                ),
            ),
            Arguments.of("Single Argument", "object[argument]",
                RhovasAst.Expression.Index(
                    RhovasAst.Expression.Access(null, "object"),
                    listOf(
                        RhovasAst.Expression.Access(null, "argument"),
                    ),
                ),
            ),
            Arguments.of("Multiple Arguments", "object[first, second, third]",
                RhovasAst.Expression.Index(
                    RhovasAst.Expression.Access(null, "object"),
                    listOf(
                        RhovasAst.Expression.Access(null, "first"),
                        RhovasAst.Expression.Access(null, "second"),
                        RhovasAst.Expression.Access(null, "third"),
                    ),
                ),
            ),
            Arguments.of("Trailing Comma", "object[argument,]",
                RhovasAst.Expression.Index(
                    RhovasAst.Expression.Access(null, "object"),
                    listOf(
                        RhovasAst.Expression.Access(null, "argument"),
                    ),
                ),
            ),
            Arguments.of("Missing Closing Bracket", "object[argument", null),
            Arguments.of("Missing Comma", "object[first second]", null),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testFunction(name: String, input: String, expected: RhovasAst.Expression.Function?) {
        testExpression(input, expected)
    }

    fun testFunction(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Zero Arguments", "function()",
                RhovasAst.Expression.Function(null, "function", listOf())
            ),
            Arguments.of("Single Argument", "function(argument)",
                RhovasAst.Expression.Function(null, "function", listOf(
                    RhovasAst.Expression.Access(null, "argument"),
                )),
            ),
            Arguments.of("Multiple Arguments", "function(first, second, third)",
                RhovasAst.Expression.Function(null, "function", listOf(
                    RhovasAst.Expression.Access(null, "first"),
                    RhovasAst.Expression.Access(null, "second"),
                    RhovasAst.Expression.Access(null, "third"),
                )),
            ),
            Arguments.of("Trailing Comma", "function(argument,)",
                RhovasAst.Expression.Function(null, "function", listOf(
                    RhovasAst.Expression.Access(null, "argument"),
                )),
            ),
            Arguments.of("Missing Closing Parenthesis", "function(argument", null),
            Arguments.of("Missing Comma", "function(first second)", null),
            Arguments.of("Method", "object.method(argument)",
                RhovasAst.Expression.Function(
                    RhovasAst.Expression.Access(null, "object"),
                    "method",
                    listOf(
                        RhovasAst.Expression.Access(null, "argument"),
                    ),
                ),
            ),
            Arguments.of("Multiple Methods", "first().second().third()",
                RhovasAst.Expression.Function(
                    RhovasAst.Expression.Function(
                        RhovasAst.Expression.Function(null, "first", listOf()),
                        "second",
                        listOf(),
                    ),
                    "third",
                    listOf(),
                ),
            ),
        )
    }

    private fun testExpression(input: String, expected: RhovasAst?) {
        if (expected != null) {
            Assertions.assertEquals(expected, RhovasParser(input).parse("expression"))
        } else {
            Assertions.assertThrows(ParseException::class.java) { RhovasParser(input).parse("expression") }
        }
    }

}
