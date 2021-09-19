package dev.rhovas.interpreter.evaluator

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import dev.rhovas.interpreter.parser.rhovas.RhovasParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.math.BigInteger
import java.util.stream.Stream

class EvaluatorTests {

    @BeforeAll
    fun beforeAll() {
        Library.initialize()
    }

    @Nested
    inner class ExpressionTests {

        @Nested
        inner class LiteralTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testScalar(name: String, input: String, expected: Object) {
                test("expression", input, expected)
            }

            fun testScalar(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Null", "null",
                        Object(Library.TYPES["Null"]!!, null),
                    ),
                    Arguments.of("Boolean", "true",
                        Object(Library.TYPES["Boolean"]!!, true),
                    ),
                    Arguments.of("Integer", "123",
                        Object(Library.TYPES["Integer"]!!, BigInteger("123")),
                    ),
                    Arguments.of("Decimal", "123.456",
                        Object(Library.TYPES["Decimal"]!!, BigDecimal("123.456")),
                    ),
                    Arguments.of("String", "\"string\"",
                        Object(Library.TYPES["String"]!!, "string"),
                    ),
                    Arguments.of("Atom", ":atom",
                        Object(Library.TYPES["Atom"]!!, RhovasAst.Atom("atom")),
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testList(name: String, input: String, expected: Object) {
                test("expression", input, expected)
            }

            fun testList(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "[]",
                        Object(Library.TYPES["List"]!!, listOf<Object>()),
                    ),
                    Arguments.of("Single", "[1]",
                        Object(Library.TYPES["List"]!!, listOf(
                            Object(Library.TYPES["Integer"]!!, BigInteger("1")),
                        )),
                    ),
                    Arguments.of("Multiple", "[1, 2, 3]",
                        Object(Library.TYPES["List"]!!, listOf(
                            Object(Library.TYPES["Integer"]!!, BigInteger("1")),
                            Object(Library.TYPES["Integer"]!!, BigInteger("2")),
                            Object(Library.TYPES["Integer"]!!, BigInteger("3")),
                        )),
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testObject(name: String, input: String, expected: Object) {
                test("expression", input, expected)
            }

            fun testObject(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "{}",
                        Object(Library.TYPES["Object"]!!, mapOf<String, Object>()),
                    ),
                    Arguments.of("Single", "{key: \"value\"}",
                        Object(Library.TYPES["Object"]!!, mapOf(
                            Pair("key", Object(Library.TYPES["String"]!!, "value")),
                        )),
                    ),
                    Arguments.of("Multiple", "{k1: \"v1\", k2: \"v2\", k3: \"v3\"}",
                        Object(Library.TYPES["Object"]!!, mapOf(
                            Pair("k1", Object(Library.TYPES["String"]!!, "v1")),
                            Pair("k2", Object(Library.TYPES["String"]!!, "v2")),
                            Pair("k3", Object(Library.TYPES["String"]!!, "v3")),
                        )),
                    ),
                    //TODO: Arguments.of("Key Only", "{key}", null),
                )
            }

        }

    }

    fun test(rule: String, input: String, expected: Object) {
        val ast = RhovasParser(input).parse(rule)
        Assertions.assertEquals(expected, Evaluator().visit(ast))
    }

}
