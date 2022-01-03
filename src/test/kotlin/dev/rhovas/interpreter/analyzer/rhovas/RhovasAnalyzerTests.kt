package dev.rhovas.interpreter.analyzer.rhovas

import dev.rhovas.interpreter.analyzer.AnalyzeException
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
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

class RhovasAnalyzerTests {

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
            fun testScalar(name: String, input: String, expected: RhovasIr.Expression.Literal?) {
                test("expression", input, expected)
            }

            fun testScalar(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Null", "null",
                        RhovasIr.Expression.Literal(null, Library.TYPES["Null"]!!),
                    ),
                    Arguments.of("Boolean", "true",
                        RhovasIr.Expression.Literal(true, Library.TYPES["Boolean"]!!),
                    ),
                    Arguments.of("Integer", "123",
                        RhovasIr.Expression.Literal(BigInteger("123"), Library.TYPES["Integer"]!!),
                    ),
                    Arguments.of("Decimal", "123.456",
                        RhovasIr.Expression.Literal(BigDecimal("123.456"), Library.TYPES["Decimal"]!!),
                    ),
                    Arguments.of("String", "\"string\"",
                        RhovasIr.Expression.Literal("string", Library.TYPES["String"]!!),
                    ),
                    Arguments.of("Atom", ":atom",
                        RhovasIr.Expression.Literal(RhovasAst.Atom("atom"), Library.TYPES["Atom"]!!),
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testList(name: String, input: String, expected: RhovasIr.Expression.Literal?) {
                test("expression", input, expected)
            }

            fun testList(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "[]",
                        RhovasIr.Expression.Literal(
                            listOf<RhovasIr.Expression>(),
                            Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)),
                        ),
                    ),
                    Arguments.of("Single", "[1]",
                        RhovasIr.Expression.Literal(
                            listOf(RhovasIr.Expression.Literal(BigInteger("1"), Library.TYPES["Integer"]!!)),
                            Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)),
                        ),
                    ),
                    Arguments.of("Multiple", "[1, 2, 3]",
                        RhovasIr.Expression.Literal(
                            listOf(
                                RhovasIr.Expression.Literal(BigInteger("1"), Library.TYPES["Integer"]!!),
                                RhovasIr.Expression.Literal(BigInteger("2"), Library.TYPES["Integer"]!!),
                                RhovasIr.Expression.Literal(BigInteger("3"), Library.TYPES["Integer"]!!),
                            ),
                            Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)),
                        ),
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testObject(name: String, input: String, expected: RhovasIr.Expression.Literal?) {
                test("expression", input, expected)
            }

            fun testObject(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "{}",
                        RhovasIr.Expression.Literal(
                            mapOf<String, RhovasIr.Expression>(),
                            Library.TYPES["Object"]!!,
                        ),
                    ),
                    Arguments.of("Single", "{key: \"value\"}",
                        RhovasIr.Expression.Literal(
                            mapOf(Pair("key", RhovasIr.Expression.Literal("value", Library.TYPES["String"]!!))),
                            Library.TYPES["Object"]!!,
                        ),
                    ),
                    Arguments.of("Multiple", "{k1: \"v1\", k2: \"v2\", k3: \"v3\"}",
                        RhovasIr.Expression.Literal(
                            mapOf(
                                Pair("k1", RhovasIr.Expression.Literal("v1", Library.TYPES["String"]!!)),
                                Pair("k2", RhovasIr.Expression.Literal("v2", Library.TYPES["String"]!!)),
                                Pair("k3", RhovasIr.Expression.Literal("v3", Library.TYPES["String"]!!)),
                            ),
                            Library.TYPES["Object"]!!,
                        ),
                    ),
                )
            }

        }

        @Nested
        inner class GroupTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testGroup(name: String, input: String, expected: RhovasIr.Expression.Group?) {
                test("expression", input, expected)
            }

            fun testGroup(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Literal", "(\"expression\")",
                        RhovasIr.Expression.Group(RhovasIr.Expression.Literal("expression", Library.TYPES["String"]!!)),
                    ),
                    Arguments.of("Binary", "(\"first\" + \"second\")",
                        RhovasIr.Expression.Group(RhovasIr.Expression.Binary("+",
                            RhovasIr.Expression.Literal("first", Library.TYPES["String"]!!),
                            RhovasIr.Expression.Literal("second", Library.TYPES["String"]!!),
                            Library.TYPES["String"]!!,
                        ),
                    ),
                    Arguments.of("Nested", "((\"expression\"))",
                        RhovasIr.Expression.Group(RhovasIr.Expression.Group(
                            RhovasIr.Expression.Literal("expression", Library.TYPES["String"]!!)),
                        )),
                    ),
                )
            }

        }

        @Nested
        inner class UnaryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testUnary(name: String, input: String, expected: RhovasIr.Expression.Unary?) {
                test("expression", input, expected)
            }

            fun testUnary(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Boolean Negation", "!true",
                        RhovasIr.Expression.Unary("!",
                            RhovasIr.Expression.Literal(true, Library.TYPES["Boolean"]!!),
                            Library.TYPES["Boolean"]!!.methods["!", 0]!!,
                        ),
                    ),
                    Arguments.of("Integer Negation", "-1", //TODO: Depends on unsigned number literals
                        RhovasIr.Expression.Unary("-",
                            RhovasIr.Expression.Literal(BigInteger("1"), Library.TYPES["Integer"]!!),
                            Library.TYPES["Integer"]!!.methods["-", 0]!!,
                        ),
                    ),
                    Arguments.of("Invalid", "-true", null),
                )
            }

        }

        @Nested
        inner class BinaryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLogicalOr(name: String, input: String, expected: RhovasIr.Expression.Binary?) {
                test("expression", input, expected)
            }

            fun testLogicalOr(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", "false || true",
                        RhovasIr.Expression.Binary("||",
                            RhovasIr.Expression.Literal(false, Library.TYPES["Boolean"]!!),
                            RhovasIr.Expression.Literal(true, Library.TYPES["Boolean"]!!),
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    Arguments.of("False", "false || false",
                        RhovasIr.Expression.Binary("||",
                            RhovasIr.Expression.Literal(false, Library.TYPES["Boolean"]!!),
                            RhovasIr.Expression.Literal(false, Library.TYPES["Boolean"]!!),
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    Arguments.of("Invalid Left", "1 || true", null),
                    Arguments.of("Invalid Right", "false || 2", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLogicalAnd(name: String, input: String, expected: RhovasIr.Expression.Binary?) {
                test("expression", input, expected)
            }

            fun testLogicalAnd(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", "true && true",
                        RhovasIr.Expression.Binary("&&",
                            RhovasIr.Expression.Literal(true, Library.TYPES["Boolean"]!!),
                            RhovasIr.Expression.Literal(true, Library.TYPES["Boolean"]!!),
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    Arguments.of("False", "true && false",
                        RhovasIr.Expression.Binary("&&",
                            RhovasIr.Expression.Literal(true, Library.TYPES["Boolean"]!!),
                            RhovasIr.Expression.Literal(false, Library.TYPES["Boolean"]!!),
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    Arguments.of("Invalid Left", "1 && false", null),
                    Arguments.of("Invalid Right", "true && 2", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testEquality(name: String, input: String, expected: RhovasIr.Expression.Binary?) {
                test("expression", input, expected)
            }

            fun testEquality(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Equatable", "1 == 2",
                        RhovasIr.Expression.Binary("==",
                            RhovasIr.Expression.Literal(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal(BigInteger("2"), Library.TYPES["Integer"]!!),
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    //TODO: Maybe Equatable
                    Arguments.of("Not Equatable", "1 != 2.0",
                        RhovasIr.Expression.Binary("!=",
                            RhovasIr.Expression.Literal(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal(BigDecimal("2.0"), Library.TYPES["Decimal"]!!),
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIdentity(name: String, input: String, expected: RhovasIr.Expression.Binary?) {
                test("expression", input, expected)
            }

            fun testIdentity(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Equatable", "1 == 2",
                        RhovasIr.Expression.Binary("==",
                            RhovasIr.Expression.Literal(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal(BigInteger("2"), Library.TYPES["Integer"]!!),
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    //TODO: Maybe Equatable
                    Arguments.of("Not Equatable", "1 != 2.0",
                        RhovasIr.Expression.Binary("!=",
                            RhovasIr.Expression.Literal(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal(BigDecimal("2.0"), Library.TYPES["Decimal"]!!),
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testComparison(name: String, input: String, expected: RhovasIr.Expression.Binary?) {
                test("expression", input, expected)
            }

            fun testComparison(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Less Than", "1 < 2",
                        RhovasIr.Expression.Binary("<",
                            RhovasIr.Expression.Literal(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal(BigInteger("2"), Library.TYPES["Integer"]!!),
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    Arguments.of("Greater Than Or Equal", "1.0 >= 2.0",
                        RhovasIr.Expression.Binary(">=",
                            RhovasIr.Expression.Literal(BigDecimal("1.0"), Library.TYPES["Decimal"]!!),
                            RhovasIr.Expression.Literal(BigDecimal("2.0"), Library.TYPES["Decimal"]!!),
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    Arguments.of("Invalid Left", "false <= 2", null),
                    Arguments.of("Invalid Right", "1 > true", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testArithmetic(name: String, input: String, expected: RhovasIr.Expression.Binary?) {
                test("expression", input, expected)
            }

            fun testArithmetic(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Integer Add", "1 + 2",
                        RhovasIr.Expression.Binary("+",
                            RhovasIr.Expression.Literal(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal(BigInteger("2"), Library.TYPES["Integer"]!!),
                            Library.TYPES["Integer"]!!,
                        ),
                    ),
                    Arguments.of("Decimal Subtract", "1.0 - 2.0",
                        RhovasIr.Expression.Binary("-",
                            RhovasIr.Expression.Literal(BigDecimal("1.0"), Library.TYPES["Decimal"]!!),
                            RhovasIr.Expression.Literal(BigDecimal("2.0"), Library.TYPES["Decimal"]!!),
                            Library.TYPES["Decimal"]!!,
                        ),
                    ),
                    Arguments.of("Integer Multiply", "1 * 2",
                        RhovasIr.Expression.Binary("*",
                            RhovasIr.Expression.Literal(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal(BigInteger("2"), Library.TYPES["Integer"]!!),
                            Library.TYPES["Integer"]!!,
                        ),
                    ),
                    Arguments.of("Decimal Divide", "1.0 / 2.0",
                        RhovasIr.Expression.Binary("/",
                            RhovasIr.Expression.Literal(BigDecimal("1.0"), Library.TYPES["Decimal"]!!),
                            RhovasIr.Expression.Literal(BigDecimal("2.0"), Library.TYPES["Decimal"]!!),
                            Library.TYPES["Decimal"]!!,
                        ),
                    ),
                    Arguments.of("Invalid Left", "false + 2", null),
                    Arguments.of("Invalid Right", "1 + true", null),
                )
            }

        }

        @Nested
        inner class AccessTests {

            @Nested
            inner class VariableTests {

                val VARIABLE = Variable.Local("variable", Library.TYPES["Any"]!!)

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testVariable(name: String, input: String, expected: RhovasIr.Expression.Access.Variable?) {
                    test("expression", input, expected, Scope(null).also {
                        it.variables.define(VARIABLE)
                    })
                }

                fun testVariable(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Variable", "variable",
                            RhovasIr.Expression.Access.Variable(VARIABLE),
                        ),
                        Arguments.of("Undefined", "undefined", null),
                    )
                }

            }

            @Nested
            inner class PropertyTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testProperty(name: String, input: String, expected: RhovasIr.Expression.Access.Property?) {
                    test("expression", input, expected)
                }

                fun testProperty(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Property", "\"string\".size",
                            RhovasIr.Expression.Access.Property(
                                RhovasIr.Expression.Literal("string", Library.TYPES["String"]!!),
                                Library.TYPES["String"]!!.properties["size"]!!,
                                false,
                            ),
                        ),
                        //TODO: Coalesce (requires nullable type)
                        Arguments.of("Undefined", "string.undefined", null)
                    )
                }

            }

            @Nested
            inner class IndexTests {

                val LIST = Variable.Local("list", Library.TYPES["List"]!!)
                val ANY = Variable.Local("any", Library.TYPES["Any"]!!)

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testIndex(name: String, input: String, expected: RhovasIr.Expression.Access.Index?) {
                    test("expression", input, expected, Scope(null).also {
                        it.variables.define(LIST)
                        it.variables.define(ANY)
                    })
                }

                fun testIndex(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Index", "list[0]",
                            RhovasIr.Expression.Access.Index(
                                RhovasIr.Expression.Access.Variable(LIST),
                                LIST.type.methods["[]", 1]!!,
                                listOf(RhovasIr.Expression.Literal(BigInteger("0"), Library.TYPES["Integer"]!!)),
                            ),
                        ),
                        Arguments.of("Invalid Arity", "list[]", null),
                        Arguments.of("Invalid Argument", "list[:key]", null),
                        Arguments.of("Undefined", "any[0]", null),
                    )
                }

            }

        }

        @Nested
        inner class InvokeTests {

            @Nested
            inner class FunctionTests {

                val FUNCTION = Function.Definition("function", listOf(Pair("argument", Library.TYPES["String"]!!)), Library.TYPES["Void"]!!)

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testFunction(name: String, input: String, expected: RhovasIr.Expression.Invoke.Function?) {
                    test("expression", input, expected, Scope(null).also {
                        it.functions.define(FUNCTION)
                    })
                }

                fun testFunction(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Function", "function(\"argument\")",
                            RhovasIr.Expression.Invoke.Function(FUNCTION, listOf(
                                RhovasIr.Expression.Literal("argument", Library.TYPES["String"]!!)
                            )),
                        ),
                        Arguments.of("Invalid Arity", "function()", null),
                        Arguments.of("Undefined", "undefined", null),
                    )
                }

            }

            @Nested
            inner class MethodTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testMethod(name: String, input: String, expected: RhovasIr.Expression.Invoke.Method?) {
                    test("expression", input, expected)
                }

                fun testMethod(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Method", "\"string\".contains(\"\")",
                            RhovasIr.Expression.Invoke.Method(
                                RhovasIr.Expression.Literal("string", Library.TYPES["String"]!!),
                                Library.TYPES["String"]!!.methods["contains", 1]!!,
                                false,
                                false,
                                listOf(RhovasIr.Expression.Literal("", Library.TYPES["String"]!!))
                            ),
                        ),
                        //TODO: Coalesce (requires nullable type)
                        //TODO: Test cascade? (logic built into constructor)
                        Arguments.of("Invalid Arity", "\"string\".contains()", null),
                        Arguments.of("Invalid Argument", "\"string\".contains(0)", null),
                        Arguments.of("Undefined", "\"string\".undefined()", null),
                    )
                }

            }

            @Nested
            inner class PipelineTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testPipeline(name: String, input: String, expected: RhovasIr.Expression.Invoke.Pipeline?) {
                    test("expression", input, expected, Scope(Library.SCOPE).also {
                        it.variables.define(Variable.Local("Kernel", Library.TYPES["Kernel"]!!))
                    })
                }

                fun testPipeline(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Pipeline", "1.|range(2, :incl)",
                            RhovasIr.Expression.Invoke.Pipeline(
                                RhovasIr.Expression.Literal(BigInteger("1"), Library.TYPES["Integer"]!!),
                                Library.SCOPE.functions["range", 3]!! as Function.Definition,
                                false,
                                false,
                                listOf(
                                    RhovasIr.Expression.Literal(BigInteger("2"), Library.TYPES["Integer"]!!),
                                    RhovasIr.Expression.Literal(RhovasAst.Atom("incl"), Library.TYPES["Atom"]!!)
                                )
                            ),
                        ),
                        Arguments.of("Pipeline", "1.|Kernel.range(2, :incl)",
                            RhovasIr.Expression.Invoke.Pipeline(
                                RhovasIr.Expression.Literal(BigInteger("1"), Library.TYPES["Integer"]!!),
                                Library.SCOPE.functions["range", 3]!! as Function.Definition,
                                false,
                                false,
                                listOf(
                                    RhovasIr.Expression.Literal(BigInteger("2"), Library.TYPES["Integer"]!!),
                                    RhovasIr.Expression.Literal(RhovasAst.Atom("incl"), Library.TYPES["Atom"]!!)
                                )
                            ),
                        ),
                        //TODO: Coalesce (requires nullable type)
                        //TODO: Test cascade? (logic built into constructor)
                        Arguments.of("Invalid Arity", "1.|range()", null),
                        Arguments.of("Invalid Argument", "1.|range(2, \"incl\")", null),
                        Arguments.of("Undefined", "1.|undefined()", null),
                    )
                }

            }

        }

        @Nested
        inner class LambdaTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLambda(name: String, input: String, expected: RhovasIr.Expression.Lambda?) {
                val expected = expected?.let {
                    RhovasIr.Expression.Invoke.Function(
                        Library.SCOPE.functions["lambda", 1]!! as Function.Definition,
                        listOf(it)
                    )
                }
                test("expression", input, expected, Library.SCOPE)
            }

            fun testLambda(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Lambda", """
                        lambda {}
                    """.trimIndent(),
                        RhovasIr.Expression.Lambda(
                            listOf(),
                            RhovasIr.Statement.Block(listOf()),
                            Type.Reference(Library.TYPES["Lambda"]!!.base, listOf(Library.TYPES["Dynamic"]!!, Library.TYPES["Dynamic"]!!)),
                        ),
                    ),
                    //TODO: Validation
                )
            }

        }

    }

    private fun test(rule: String, input: String, expected: RhovasIr?, scope: Scope = Scope(null)) {
        val ast = RhovasParser(Input("AnalyzerTests.test", input)).parse(rule)
        if (expected != null) {
            val ir = RhovasAnalyzer(scope).visit(ast)
            Assertions.assertEquals(expected, ir)
        } else {
            Assertions.assertThrows(AnalyzeException::class.java) { RhovasAnalyzer(scope).visit(ast) }
        }
    }

}
