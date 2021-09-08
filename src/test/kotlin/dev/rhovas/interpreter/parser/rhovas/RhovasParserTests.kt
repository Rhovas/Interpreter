package dev.rhovas.interpreter.parser.rhovas

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

    private fun testExpression(input: String, expected: RhovasAst) {
        Assertions.assertEquals(expected, RhovasParser(input).parseExpression())
    }

}
