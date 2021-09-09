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
    fun testAccess(name: String, input: String, expected: RhovasAst.Expression.Access?) {
        testExpression(input, expected)
    }

    fun testAccess(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Variable", "variable", RhovasAst.Expression.Access("variable")),
            //TODO: Fields
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
                RhovasAst.Expression.Function("function", listOf())
            ),
            Arguments.of("Single Argument", "function(argument)",
                RhovasAst.Expression.Function("function", listOf(
                    RhovasAst.Expression.Access("argument"),
                ))
            ),
            Arguments.of("Multiple Arguments", "function(first, second, third)",
                RhovasAst.Expression.Function("function", listOf(
                    RhovasAst.Expression.Access("first"),
                    RhovasAst.Expression.Access("second"),
                    RhovasAst.Expression.Access("third"),
                ))
            ),
            Arguments.of("Trailing Comma", "function(argument,)",
                RhovasAst.Expression.Function("function", listOf(
                    RhovasAst.Expression.Access("argument"),
                ))
            ),
            Arguments.of("Missing Closing Parenthesis", "function(argument", null),
            Arguments.of("Missing Comma", "function(first second)", null),
            //TODO: Methods
        )
    }

    private fun testExpression(input: String, expected: RhovasAst?) {
        if (expected != null) {
            Assertions.assertEquals(expected, RhovasParser(input).parseExpression())
        } else {
            Assertions.assertThrows(ParseException::class.java) { RhovasParser(input).parseExpression() }
        }
    }

}
