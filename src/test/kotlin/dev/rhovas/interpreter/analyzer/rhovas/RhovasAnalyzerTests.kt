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

    lateinit var STMT_0: Function.Definition
    lateinit var STMT_1: Function.Definition

    @BeforeAll
    fun beforeAll() {
        STMT_0 = Function.Definition("stmt", listOf(), listOf(), Library.TYPES["Void"]!!, listOf())
        STMT_1 = Function.Definition("stmt", listOf(), listOf(Pair("position", Library.TYPES["Integer"]!!)), Library.TYPES["Void"]!!, listOf())
    }

    @Nested
    inner class SourceTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testSource(name: String, input: String, expected: RhovasIr.Source?) {
            test("source", input, expected)
        }

        fun testSource(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Empty", "",
                    RhovasIr.Source(listOf()),
                ),
                Arguments.of("Single", """
                    stmt();
                """.trimIndent(),
                    RhovasIr.Source(listOf(stmt())),
                ),
                Arguments.of("Multiple", """
                    stmt(1); stmt(2); stmt(3);
                """.trimIndent(),
                    RhovasIr.Source(listOf(stmt(1), stmt(2), stmt(3))),
                ),
            )
        }

    }

    @Nested
    inner class StatementTests {

        @Nested
        inner class BlockTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testBlock(name: String, input: String, expected: RhovasIr.Statement.Block?) {
                test("statement", input, expected)
            }

            fun testBlock(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        {}
                    """.trimIndent(),
                        RhovasIr.Statement.Block(listOf()),
                    ),
                    Arguments.of("Single", """
                        { stmt(); }
                    """.trimIndent(),
                        RhovasIr.Statement.Block(listOf(stmt())),
                    ),
                    Arguments.of("Multiple", """
                        { stmt(1); stmt(2); stmt(3); }
                    """.trimIndent(),
                        RhovasIr.Statement.Block(listOf(stmt(1), stmt(2), stmt(3))),
                    ),
                    Arguments.of("Unreachable", """
                        { return; stmt(); }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class ExpressionTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testExpression(name: String, input: String, expected: RhovasIr.Statement.Expression?) {
                test("statement", input, expected)
            }

            fun testExpression(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Function", """
                        stmt();
                    """.trimIndent(), stmt()),
                    Arguments.of("Invalid", """
                        1;
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class FunctionTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testFunction(name: String, input: String, expected: RhovasIr.Statement.Function?) {
                test("statement", input, expected, Scope(null).also {
                    it.functions.define(Function.Definition("Exception", listOf(), listOf(Pair("message", Library.TYPES["String"]!!)), Library.TYPES["Exception"]!!, listOf()))
                    it.functions.define(Function.Definition("fail", listOf(), listOf(Pair("message", Library.TYPES["String"]!!)), Library.TYPES["Void"]!!, listOf(Library.TYPES["Exception"]!!)))
                })
            }

            fun testFunction(): Stream<Arguments> {
                Library.TYPES["SubtypeException"] = Type.Reference(Type.Base(
                    "SubtypeException",
                    listOf(),
                    listOf(Library.TYPES["Exception"]!!),
                    Scope(null)
                ), listOf())
                return Stream.of(
                    Arguments.of("Definition", """
                        func name() {
                            name();
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Function(
                            Function.Definition("name", listOf(), listOf(), Library.TYPES["Void"]!!, listOf()),
                            RhovasIr.Statement.Block(listOf(
                                RhovasIr.Statement.Expression(RhovasIr.Expression.Invoke.Function(
                                    Function.Definition("name", listOf(), listOf(), Library.TYPES["Void"]!!, listOf()),
                                    listOf(),
                                )),
                            )),
                        )
                    ),
                    Arguments.of("Parameter", """
                        func name(parameter: Integer) {
                            stmt(parameter);
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Function(
                            Function.Definition("name", listOf(), listOf(Pair("parameter", Library.TYPES["Integer"]!!)), Library.TYPES["Void"]!!, listOf()),
                            RhovasIr.Statement.Block(listOf(
                                stmt(RhovasIr.Expression.Access.Variable(Variable.Local("parameter", Library.TYPES["Integer"]!!, false))),
                            )),
                        ),
                    ),
                    Arguments.of("Return Value", """
                        func name(): Integer {
                            return 1;
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Function(
                            Function.Definition("name", listOf(), listOf(), Library.TYPES["Integer"]!!, listOf()),
                            RhovasIr.Statement.Block(listOf(
                                RhovasIr.Statement.Return(RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!)),
                            )),
                        ),
                    ),
                    Arguments.of("If Return", """
                        func test(): Integer {
                            if (true) {
                                return 1;
                            } else {
                                return 2;
                            }
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Function(
                            Function.Definition("test", listOf(), listOf(), Library.TYPES["Integer"]!!, listOf()),
                            RhovasIr.Statement.Block(listOf(
                                RhovasIr.Statement.If(
                                    RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                                    RhovasIr.Statement.Block(listOf(
                                        RhovasIr.Statement.Return(RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!)),
                                    )),
                                    RhovasIr.Statement.Block(listOf(
                                        RhovasIr.Statement.Return(RhovasIr.Expression.Literal.Scalar(BigInteger("2"), Library.TYPES["Integer"]!!)),
                                    )),
                                ),
                            ))
                        ),
                    ),
                    Arguments.of("Conditional Match Return", """
                        func test(): Integer {
                            match {
                                true: return 1;
                                else: return 2;
                            }
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Function(
                            Function.Definition("test", listOf(), listOf(), Library.TYPES["Integer"]!!, listOf()),
                            RhovasIr.Statement.Block(listOf(
                                RhovasIr.Statement.Match.Conditional(
                                    listOf(Pair(
                                        RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                                        RhovasIr.Statement.Return(RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!)),
                                    )),
                                    Pair(null, RhovasIr.Statement.Return(RhovasIr.Expression.Literal.Scalar(BigInteger("2"), Library.TYPES["Integer"]!!))),
                                ),
                            ))
                        ),
                    ),
                    Arguments.of("Structural Match Return", """
                        func test(): Integer {
                            match (true) {
                                true: return 1;
                            }
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Function(
                            Function.Definition("test", listOf(), listOf(), Library.TYPES["Integer"]!!, listOf()),
                            RhovasIr.Statement.Block(listOf(
                                RhovasIr.Statement.Match.Structural(
                                    RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                                    listOf(Pair(
                                        RhovasIr.Pattern.Value(RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!)),
                                        RhovasIr.Statement.Return(RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!)),
                                    )),
                                    null,
                                ),
                            ))
                        ),
                    ),
                    Arguments.of("Throws", """
                        func name() throws Exception {
                            throw Exception("message");
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Function(
                            Function.Definition("name", listOf(), listOf(), Library.TYPES["Void"]!!, listOf(Library.TYPES["Exception"]!!)),
                            RhovasIr.Statement.Block(listOf(RhovasIr.Statement.Throw(RhovasIr.Expression.Invoke.Function(
                                Function.Definition("Exception", listOf(), listOf(Pair("message", Library.TYPES["String"]!!)), Library.TYPES["Exception"]!!, listOf()),
                                listOf(RhovasIr.Expression.Literal.String(listOf("message"), listOf(), Library.TYPES["String"]!!)),
                            )))),
                        ),
                    ),
                    Arguments.of("Missing Return Value", """
                        func name(): Integer {
                            stmt();
                        }
                    """.trimIndent(), null),
                    Arguments.of("Incomplete If Return", """
                        func name(): Integer {
                            if (true) {
                                return 1;
                            }
                        }
                    """.trimIndent(), null),
                    Arguments.of("Incomplete Conditional Match Return", """
                        func name(): Integer {
                            match {
                                true: return 1;
                            }
                        }
                    """.trimIndent(), null),
                    Arguments.of("Uncaught Exception", """
                        func name() {
                            throw Exception("message");
                        }
                    """.trimIndent(), null),
                    Arguments.of("Uncaught Function Exception", """
                        func name() {
                            fail("message");
                        }
                    """.trimIndent(), null),
                    Arguments.of("Uncaught Supertype Exception", """
                        func name() {
                            try {
                                throw Exception("message");
                            } catch (val e: SubtypeException) {
                                stmt();
                            }
                        }
                    """.trimIndent(), null),
                    Arguments.of("Rethrown Exception", """
                        func name() {
                            try {
                                throw Exception("message");
                            } catch (val e: Exception) {
                                throw e;
                            }
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class DeclarationTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testDeclaration(name: String, input: String, expected: RhovasIr.Statement.Declaration?) {
                val expected = expected?.let {
                    RhovasIr.Source(listOf(
                        it,
                        stmt(RhovasIr.Expression.Access.Variable(it.variable)),
                    ))
                }
                test("source", input, expected, Scope(null).also {
                    it.variables.define(Variable.Local("any", Library.TYPES["Any"]!!, false))
                })
            }

            fun testDeclaration(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Val Declaration", """
                        val name: Integer;
                        stmt(name);
                    """.trimIndent(),
                        RhovasIr.Statement.Declaration(
                            Variable.Local("name", Library.TYPES["Integer"]!!, false),
                            null,
                        )
                    ),
                    Arguments.of("Var Declaration", """
                        var name: Integer;
                        stmt(name);
                    """.trimIndent(),
                        RhovasIr.Statement.Declaration(
                            Variable.Local("name", Library.TYPES["Integer"]!!, true),
                            null,
                        ),
                    ),
                    Arguments.of("Initialization", """
                        val name = 1;
                        stmt(name);
                    """.trimIndent(),
                        RhovasIr.Statement.Declaration(
                            Variable.Local("name", Library.TYPES["Integer"]!!, false),
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                        ),
                    ),
                    Arguments.of("Subtype Value", """
                        val name: Dynamic = 1;
                        stmt(name);
                    """.trimIndent(),
                        RhovasIr.Statement.Declaration(
                            Variable.Local("name", Library.TYPES["Dynamic"]!!, false),
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                        ),
                    ),
                    Arguments.of("Undefined Type", """
                        val name;
                    """.trimIndent(), null),
                    Arguments.of("Invalid Value", """
                        val name: Integer = 1.0;
                    """.trimIndent(), null),
                    Arguments.of("Supertype Value", """
                        val name: Integer = any;
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class AssignmentTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testVariable(name: String, input: String, expected: RhovasIr.Statement.Assignment.Variable?) {
                val expected = expected?.let {
                    RhovasIr.Source(listOf(
                        RhovasIr.Statement.Declaration(it.variable, null),
                        it,
                    ))
                }
                test("source", input, expected, Scope(null).also {
                    it.variables.define(Variable.Local("any", Library.TYPES["Any"]!!, false))
                })
            }

            fun testVariable(): Stream<Arguments> {
                return Stream.of(
                    //TODO: Variable mutability
                    Arguments.of("Assignment", """
                        var variable: Integer;
                        variable = 1;
                    """.trimIndent(),
                        RhovasIr.Statement.Assignment.Variable(
                            Variable.Local("variable", Library.TYPES["Integer"]!!, true),
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                        ),
                    ),
                    Arguments.of("Subtype Value", """
                        var variable: Dynamic;
                        variable = 1;
                    """.trimIndent(),
                        RhovasIr.Statement.Assignment.Variable(
                            Variable.Local("variable", Library.TYPES["Dynamic"]!!, true),
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                        ),
                    ),
                    Arguments.of("Undefined Variable", """
                        undefined = 1;
                    """.trimIndent(), null),
                    Arguments.of("Unassignable Variable", """
                        val unassignable = 1;
                        unassignable = 1;
                    """.trimIndent(), null),
                    Arguments.of("Invalid Value", """
                        var variable: Integer;
                        variable = 1.0;
                    """.trimIndent(), null),
                    Arguments.of("Supertype Value", """
                        var variable: Integer;
                        variable = any;
                    """.trimIndent(), null),
                )
            }

            private val ObjectType = Type.Base("ObjectType", listOf(), listOf(Library.TYPES["Any"]!!), Scope(null).also {
                it.functions.define(Function.Definition("property", listOf(), listOf(Pair("instance", Library.TYPES["Dynamic"]!!)), Library.TYPES["Integer"]!!, listOf()))
                it.functions.define(Function.Definition("property", listOf(), listOf(Pair("instance", Library.TYPES["Dynamic"]!!), Pair("value", Library.TYPES["Integer"]!!)), Library.TYPES["Void"]!!, listOf()))
                it.functions.define(Function.Definition("dynamic", listOf(), listOf(Pair("instance", Library.TYPES["Dynamic"]!!)), Library.TYPES["Dynamic"]!!, listOf()))
                it.functions.define(Function.Definition("dynamic", listOf(), listOf(Pair("instance", Library.TYPES["Dynamic"]!!), Pair("value", Library.TYPES["Dynamic"]!!)), Library.TYPES["Void"]!!, listOf()))
                it.functions.define(Function.Definition("unassignable", listOf(), listOf(Pair("unassignable", Library.TYPES["Dynamic"]!!)), Library.TYPES["Integer"]!!, listOf()))
            }).reference

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testProperty(name: String, input: String, expected: RhovasIr.Statement.Assignment.Property?) {
                test("statement", input, expected, Scope(null).also {
                    it.variables.define(Variable.Local("object", ObjectType, false))
                })
            }

            fun testProperty(): Stream<Arguments> {
                return Stream.of(
                    //TODO: Property mutability
                    Arguments.of("Assignment", """
                        object.property = 1;
                    """.trimIndent(),
                        RhovasIr.Statement.Assignment.Property(
                            RhovasIr.Expression.Access.Variable(Variable.Local("object", ObjectType, false)),
                            ObjectType.properties["property"]!!,
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                        ),
                    ),
                    Arguments.of("Subtype Value", """
                        object.dynamic = 1;
                    """.trimIndent(),
                        RhovasIr.Statement.Assignment.Property(
                            RhovasIr.Expression.Access.Variable(Variable.Local("object", ObjectType, false)),
                            ObjectType.properties["dynamic"]!!,
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                        ),
                    ),
                    Arguments.of("Undefined Property", """
                        object.undefined = 1;
                    """.trimIndent(), null),
                    Arguments.of("Unassignable Property", """
                        object.unassignable = 1;
                    """.trimIndent(), null),
                    Arguments.of("Invalid Value", """
                        object.property = 1.0;
                    """.trimIndent(), null),
                    Arguments.of("Supertype Value", """
                        object.property = any;
                    """.trimIndent(), null),
                )
            }

            val IntegerList = Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Integer"]!!))
            val DynamicList = Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!))

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIndex(name: String, input: String, expected: RhovasIr.Statement.Assignment.Index?) {
                test("statement", input, expected, Scope(null).also { scope ->
                    scope.variables.define(Variable.Local("list", IntegerList, false))
                    scope.variables.define(Variable.Local("dynamic", DynamicList, false))
                })
            }

            fun testIndex(): Stream<Arguments> {
                return Stream.of(
                    //TODO: Property mutability
                    Arguments.of("Assignment", """
                        list[0] = 1;
                    """.trimIndent(),
                        RhovasIr.Statement.Assignment.Index(
                            RhovasIr.Expression.Access.Variable(Variable.Local("list", IntegerList, false)),
                            IntegerList.methods["[]=", listOf(Library.TYPES["Integer"]!!, Library.TYPES["Integer"]!!)]!!,
                            listOf(RhovasIr.Expression.Literal.Scalar(BigInteger("0"), Library.TYPES["Integer"]!!)),
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                        ),
                    ),
                    Arguments.of("Subtype Value", """
                        dynamic[0] = 1;
                    """.trimIndent(),
                        RhovasIr.Statement.Assignment.Index(
                            RhovasIr.Expression.Access.Variable(Variable.Local("dynamic", DynamicList, false)),
                            DynamicList.methods["[]=", listOf(Library.TYPES["Integer"]!!, Library.TYPES["Dynamic"]!!)]!!,
                            listOf(RhovasIr.Expression.Literal.Scalar(BigInteger("0"), Library.TYPES["Integer"]!!)),
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                        ),
                    ),
                    Arguments.of("Undefined Method", """
                        any[0] = 1;
                    """.trimIndent(), null),
                    Arguments.of("Invalid Arity", """
                        list[0, 1, 2] = 1;
                    """.trimIndent(), null),
                    Arguments.of("Invalid Argument", """
                        list[0.0] = 1;
                    """.trimIndent(), null),
                    Arguments.of("Invalid Value", """
                        list[0] = 1.0;
                    """.trimIndent(), null),
                    Arguments.of("Supertype Value", """
                        list[0] = any;
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class IfTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIf(name: String, input: String, expected: RhovasIr.Statement.If?) {
                test("statement", input, expected)
            }

            fun testIf(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("If", """
                        if (true) { stmt(); }
                    """.trimIndent(),
                        RhovasIr.Statement.If(
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            RhovasIr.Statement.Block(listOf(stmt())),
                            null,
                        ),
                    ),
                    Arguments.of("Else", """
                        if (true) { stmt(1); } else { stmt(2); }
                    """.trimIndent(),
                        RhovasIr.Statement.If(
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            RhovasIr.Statement.Block(listOf(stmt(1))),
                            RhovasIr.Statement.Block(listOf(stmt(2))),
                        ),
                    ),
                    Arguments.of("Invalid Condition", """
                        if (1) { stmt(); }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class MatchTests {}

        @Nested
        inner class ForTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testFor(name: String, input: String, expected: RhovasIr.Statement.For?) {
                test("statement", input, expected)
            }

            fun testFor(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("For", """
                        for (val element in []) { stmt(); }
                    """.trimIndent(),
                        RhovasIr.Statement.For("element",
                            RhovasIr.Expression.Literal.List(
                                listOf<RhovasIr.Expression>(),
                                Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)),
                            ),
                            RhovasIr.Statement.Block(listOf(stmt())),
                        ),
                    ),
                    Arguments.of("Element", """
                        for (val element in [1]) { stmt(element); }
                    """.trimIndent(),
                        RhovasIr.Statement.For("element",
                            RhovasIr.Expression.Literal.List(
                                listOf(RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!)),
                                Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)),
                            ),
                            RhovasIr.Statement.Block(listOf(
                                stmt(RhovasIr.Expression.Access.Variable(Variable.Local("element", Library.TYPES["Dynamic"]!!, false))),
                            )),
                        ),
                    ),
                    Arguments.of("Invalid Iterable", """
                        for (val element in {}) { stmt(); }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class WhileTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testWhile(name: String, input: String, expected: RhovasIr.Statement.While?) {
                test("statement", input, expected)
            }

            fun testWhile(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("While", """
                        while (true) { stmt(); }
                    """.trimIndent(),
                        RhovasIr.Statement.While(
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            RhovasIr.Statement.Block(listOf(stmt())),
                        )
                    ),
                    Arguments.of("Invalid Condition", """
                        while (1) { stmt(); }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class TryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testTry(name: String, input: String, expected: RhovasIr.Statement.Try?) {
                test("statement", input, expected, Scope(null).also {
                    it.functions.define(Function.Definition("Exception", listOf(), listOf(Pair("message", Library.TYPES["String"]!!)), Library.TYPES["Exception"]!!, listOf()))
                })
            }

            fun testTry(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Try", """
                        try { stmt(); }
                    """.trimIndent(),
                        RhovasIr.Statement.Try(
                            RhovasIr.Statement.Block(listOf(stmt())),
                            listOf(),
                            null,
                        ),
                    ),
                    Arguments.of("Catch", """
                        try {
                            stmt(1);
                        } catch (val e: Exception) {
                            stmt(2);
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Try(
                            RhovasIr.Statement.Block(listOf(stmt(1))),
                            listOf(RhovasIr.Statement.Try.Catch("e", Library.TYPES["Exception"]!!, RhovasIr.Statement.Block(listOf(stmt(2))))),
                            null,
                        ),
                    ),
                    Arguments.of("Finally", """
                        try {
                            stmt(1);
                        } finally {
                            stmt(2);
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Try(
                            RhovasIr.Statement.Block(listOf(stmt(1))),
                            listOf(),
                            RhovasIr.Statement.Block(listOf(stmt(2))),
                        ),
                    ),
                    Arguments.of("Invalid Catch Type", """
                        try {
                            stmt(1);
                        } catch (val e: Any) {
                            stmt(2);
                        }
                    """.trimIndent(), null),
                    Arguments.of("Finally Exception", """
                        try {
                            stmt();
                        } finally {
                            throw Exception("message");
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class WithTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testWith(name: String, input: String, expected: RhovasIr.Statement.With?) {
                test("statement", input, expected)
            }

            fun testWith(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("With", """
                        with (1) { stmt(); }
                    """.trimIndent(),
                        RhovasIr.Statement.With(null,
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Statement.Block(listOf(stmt())),
                        ),
                    ),
                    Arguments.of("Named Argument", """
                        with (val name = 1) { stmt(name); }
                    """.trimIndent(),
                        RhovasIr.Statement.With("name",
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Statement.Block(listOf(
                                stmt(RhovasIr.Expression.Access.Variable(Variable.Local("name", Library.TYPES["Integer"]!!, false))),
                            )),
                        ),
                    ),
                )
            }

        }

        @Nested
        inner class LabelTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLabel(name: String, input: String, expected: RhovasIr.Statement.Label?) {
                test("statement", input, expected)
            }

            fun testLabel(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("For", """
                        label: for (val element in []) {
                            break label;
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Label("label",
                            RhovasIr.Statement.For("element",
                                RhovasIr.Expression.Literal.List(listOf<RhovasIr.Expression>(), Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!))),
                                RhovasIr.Statement.Block(listOf(
                                    RhovasIr.Statement.Break("label"),
                                )),
                            ),
                        ),
                    ),
                    Arguments.of("While", """
                        label: while (true) {
                            break label;
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Label("label",
                            RhovasIr.Statement.While(
                                RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                                RhovasIr.Statement.Block(listOf(
                                    RhovasIr.Statement.Break("label"),
                                )),
                            ),
                        ),
                    ),
                    Arguments.of("Invalid Statement", """
                        label: stmt(0);
                    """.trimIndent(), null),
                    Arguments.of("Redefined Label", """
                        label: while (true) {
                            label: while (true) {
                                break label;
                            }
                        }
                    """.trimIndent(), null),
                    Arguments.of("Unused Label", """
                        label: while (true) {
                            break;
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class BreakTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testBreak(name: String, input: String, expected: RhovasIr.Statement?) {
                test("statement", input, expected)
            }

            fun testBreak(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Break", """
                        while (true) {
                            break;
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.While(
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            RhovasIr.Statement.Block(listOf(
                                RhovasIr.Statement.Break(null),
                            )),
                        ),
                    ),
                    Arguments.of("Label", """
                        label: while (true) {
                            break label;
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Label("label",
                            RhovasIr.Statement.While(
                                RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                                RhovasIr.Statement.Block(listOf(
                                    RhovasIr.Statement.Break("label"),
                                )),
                            ),
                        ),
                    ),
                    Arguments.of("Invalid Statement", """
                        break;
                    """.trimIndent(), null),
                    Arguments.of("Undefined Label", """
                        while (true) {
                            break label;
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class ContinueTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testContinue(name: String, input: String, expected: RhovasIr.Statement?) {
                test("statement", input, expected)
            }

            fun testContinue(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Continue", """
                        while (true) {
                            continue;
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.While(
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            RhovasIr.Statement.Block(listOf(
                                RhovasIr.Statement.Continue(null),
                            )),
                        ),
                    ),
                    Arguments.of("Label", """
                        label: while (true) {
                            continue label;
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Label("label",
                            RhovasIr.Statement.While(
                                RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                                RhovasIr.Statement.Block(listOf(
                                    RhovasIr.Statement.Continue("label"),
                                )),
                            ),
                        ),
                    ),
                    Arguments.of("Invalid Statement", """
                        continue;
                    """.trimIndent(), null),
                    Arguments.of("Undefined Label", """
                        while (true) {
                            continue label;
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class ReturnTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testReturn(name: String, input: String, expected: RhovasIr.Statement?) {
                test("statement", input, expected)
            }

            fun testReturn(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Return Void", """
                        func test() {
                            return;
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Function(
                            Function.Definition("test", listOf(), listOf(), Library.TYPES["Void"]!!, listOf()),
                            RhovasIr.Statement.Block(listOf(
                                RhovasIr.Statement.Return(null),
                            )),
                        ),
                    ),
                    Arguments.of("Return Value", """
                        func test(): Integer {
                            return 1;
                        }
                    """.trimIndent(),
                        RhovasIr.Statement.Function(
                            Function.Definition("test", listOf(), listOf(), Library.TYPES["Integer"]!!, listOf()),
                            RhovasIr.Statement.Block(listOf(
                                RhovasIr.Statement.Return(RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!)),
                            )),
                        ),
                    ),
                    Arguments.of("Invalid Return", """
                        return;
                    """.trimIndent(), null),
                    Arguments.of("Invalid Return Type", """
                        func test(): Integer {
                            return 1.0;
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class ThrowTests {}

        @Nested
        inner class AssertTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testAssert(name: String, input: String, expected: RhovasIr.Statement.Assert?) {
                test("statement", input, expected)
            }

            fun testAssert(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Assert", """
                        assert true;
                    """.trimIndent(),
                        RhovasIr.Statement.Assert(RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!), null),
                    ),
                    Arguments.of("Message", """
                        assert true: "message";
                    """.trimIndent(),
                        RhovasIr.Statement.Assert(
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            RhovasIr.Expression.Literal.String(listOf("message"), listOf(), Library.TYPES["String"]!!),
                        ),
                    ),
                    Arguments.of("Invalid Condition", """
                        assert 1;
                    """.trimIndent(), null),
                    Arguments.of("Invalid Message", """
                        assert true: 1;
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class RequireTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testRequire(name: String, input: String, expected: RhovasIr.Statement.Require?) {
                test("statement", input, expected)
            }

            fun testRequire(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Require", """
                        require true;
                    """.trimIndent(),
                        RhovasIr.Statement.Require(RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!), null),
                    ),
                    Arguments.of("Message", """
                        require true: "message";
                    """.trimIndent(),
                        RhovasIr.Statement.Require(
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            RhovasIr.Expression.Literal.String(listOf("message"), listOf(), Library.TYPES["String"]!!),
                        ),
                    ),
                    Arguments.of("Invalid Condition", """
                        require 1;
                    """.trimIndent(), null),
                    Arguments.of("Invalid Message", """
                        require true: 1;
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class EnsureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testEnsure(name: String, input: String, expected: RhovasIr.Statement.Ensure?) {
                test("statement", input, expected)
            }

            fun testEnsure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Ensure", """
                        ensure true;
                    """.trimIndent(),
                        RhovasIr.Statement.Ensure(RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!), null),
                    ),
                    Arguments.of("Message", """
                        ensure true: "message";
                    """.trimIndent(),
                        RhovasIr.Statement.Ensure(
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            RhovasIr.Expression.Literal.String(listOf("message"), listOf(), Library.TYPES["String"]!!),
                        ),
                    ),
                    Arguments.of("Invalid Condition", """
                        ensure 1;
                    """.trimIndent(), null),
                    Arguments.of("Invalid Message", """
                        ensure true: 1;
                    """.trimIndent(), null),
                )
            }

        }

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
                        RhovasIr.Expression.Literal.Scalar(null, Library.TYPES["Null"]!!),
                    ),
                    Arguments.of("Boolean", "true",
                        RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                    ),
                    Arguments.of("Integer", "123",
                        RhovasIr.Expression.Literal.Scalar(BigInteger("123"), Library.TYPES["Integer"]!!),
                    ),
                    Arguments.of("Decimal", "123.456",
                        RhovasIr.Expression.Literal.Scalar(BigDecimal("123.456"), Library.TYPES["Decimal"]!!),
                    ),
                    Arguments.of("String", "\"string\"",
                        RhovasIr.Expression.Literal.String(listOf("string"), listOf(), Library.TYPES["String"]!!),
                    ),
                    Arguments.of("Atom", ":atom",
                        RhovasIr.Expression.Literal.Scalar(RhovasAst.Atom("atom"), Library.TYPES["Atom"]!!),
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
                        RhovasIr.Expression.Literal.List(
                            listOf<RhovasIr.Expression>(),
                            Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)),
                        ),
                    ),
                    Arguments.of("Single", "[1]",
                        RhovasIr.Expression.Literal.List(
                            listOf(RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!)),
                            Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)),
                        ),
                    ),
                    Arguments.of("Multiple", "[1, 2, 3]",
                        RhovasIr.Expression.Literal.List(
                            listOf(
                                RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                                RhovasIr.Expression.Literal.Scalar(BigInteger("2"), Library.TYPES["Integer"]!!),
                                RhovasIr.Expression.Literal.Scalar(BigInteger("3"), Library.TYPES["Integer"]!!),
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
                        RhovasIr.Expression.Literal.Object(
                            mapOf<String, RhovasIr.Expression>(),
                            Library.TYPES["Object"]!!,
                        ),
                    ),
                    Arguments.of("Single", "{key: \"value\"}",
                        RhovasIr.Expression.Literal.Object(
                            mapOf(Pair("key", RhovasIr.Expression.Literal.String(listOf("value"), listOf(), Library.TYPES["String"]!!))),
                            Library.TYPES["Object"]!!,
                        ),
                    ),
                    Arguments.of("Multiple", "{k1: \"v1\", k2: \"v2\", k3: \"v3\"}",
                        RhovasIr.Expression.Literal.Object(
                            mapOf(
                                Pair("k1", RhovasIr.Expression.Literal.String(listOf("v1"), listOf(), Library.TYPES["String"]!!)),
                                Pair("k2", RhovasIr.Expression.Literal.String(listOf("v2"), listOf(), Library.TYPES["String"]!!)),
                                Pair("k3", RhovasIr.Expression.Literal.String(listOf("v3"), listOf(), Library.TYPES["String"]!!)),
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
                        RhovasIr.Expression.Group(RhovasIr.Expression.Literal.String(listOf("expression"), listOf(), Library.TYPES["String"]!!)),
                    ),
                    Arguments.of("Binary", "(\"first\" + \"second\")",
                        RhovasIr.Expression.Group(RhovasIr.Expression.Binary("+",
                            RhovasIr.Expression.Literal.String(listOf("first"), listOf(), Library.TYPES["String"]!!),
                            RhovasIr.Expression.Literal.String(listOf("second"), listOf(), Library.TYPES["String"]!!),
                            Library.TYPES["String"]!!.methods["+", listOf(Library.TYPES["String"]!!)],
                            Library.TYPES["String"]!!,
                        ),
                    ),
                    Arguments.of("Nested", "((\"expression\"))",
                        RhovasIr.Expression.Group(RhovasIr.Expression.Group(
                            RhovasIr.Expression.Literal.String(listOf("expression"), listOf(), Library.TYPES["String"]!!)),
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
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            Library.TYPES["Boolean"]!!.methods["!", listOf()]!!,
                        ),
                    ),
                    Arguments.of("Integer Negation", "-1", //TODO: Depends on unsigned number literals
                        RhovasIr.Expression.Unary("-",
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                            Library.TYPES["Integer"]!!.methods["-", listOf()]!!,
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
                            RhovasIr.Expression.Literal.Scalar(false, Library.TYPES["Boolean"]!!),
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            null,
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    Arguments.of("False", "false || false",
                        RhovasIr.Expression.Binary("||",
                            RhovasIr.Expression.Literal.Scalar(false, Library.TYPES["Boolean"]!!),
                            RhovasIr.Expression.Literal.Scalar(false, Library.TYPES["Boolean"]!!),
                            null,
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
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            null,
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    Arguments.of("False", "true && false",
                        RhovasIr.Expression.Binary("&&",
                            RhovasIr.Expression.Literal.Scalar(true, Library.TYPES["Boolean"]!!),
                            RhovasIr.Expression.Literal.Scalar(false, Library.TYPES["Boolean"]!!),
                            null,
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
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal.Scalar(BigInteger("2"), Library.TYPES["Integer"]!!),
                            Library.TYPES["Integer"]!!.methods["==", listOf(Library.TYPES["Integer"]!!)],
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    //TODO: Maybe Equatable
                    Arguments.of("Not Equatable", "1 != 2.0",
                        RhovasIr.Expression.Binary("!=",
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal.Scalar(BigDecimal("2.0"), Library.TYPES["Decimal"]!!),
                            Library.TYPES["Integer"]!!.methods["==", listOf(Library.TYPES["Integer"]!!)],
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
                    Arguments.of("Equatable", "1 === 2",
                        RhovasIr.Expression.Binary("===",
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal.Scalar(BigInteger("2"), Library.TYPES["Integer"]!!),
                            null,
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    //TODO: Maybe Equatable
                    Arguments.of("Not Equatable", "1 !== 2.0",
                        RhovasIr.Expression.Binary("!==",
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal.Scalar(BigDecimal("2.0"), Library.TYPES["Decimal"]!!),
                            null,
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
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal.Scalar(BigInteger("2"), Library.TYPES["Integer"]!!),
                            Library.TYPES["Integer"]!!.methods["<=>", listOf(Library.TYPES["Integer"]!!)],
                            Library.TYPES["Boolean"]!!,
                        ),
                    ),
                    Arguments.of("Greater Than Or Equal", "1.0 >= 2.0",
                        RhovasIr.Expression.Binary(">=",
                            RhovasIr.Expression.Literal.Scalar(BigDecimal("1.0"), Library.TYPES["Decimal"]!!),
                            RhovasIr.Expression.Literal.Scalar(BigDecimal("2.0"), Library.TYPES["Decimal"]!!),
                            Library.TYPES["Decimal"]!!.methods["<=>", listOf(Library.TYPES["Decimal"]!!)],
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
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal.Scalar(BigInteger("2"), Library.TYPES["Integer"]!!),
                            Library.TYPES["Integer"]!!.methods["+", listOf(Library.TYPES["Integer"]!!)],
                            Library.TYPES["Integer"]!!,
                        ),
                    ),
                    Arguments.of("Decimal Subtract", "1.0 - 2.0",
                        RhovasIr.Expression.Binary("-",
                            RhovasIr.Expression.Literal.Scalar(BigDecimal("1.0"), Library.TYPES["Decimal"]!!),
                            RhovasIr.Expression.Literal.Scalar(BigDecimal("2.0"), Library.TYPES["Decimal"]!!),
                            Library.TYPES["Decimal"]!!.methods["-", listOf(Library.TYPES["Decimal"]!!)],
                            Library.TYPES["Decimal"]!!,
                        ),
                    ),
                    Arguments.of("Integer Multiply", "1 * 2",
                        RhovasIr.Expression.Binary("*",
                            RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                            RhovasIr.Expression.Literal.Scalar(BigInteger("2"), Library.TYPES["Integer"]!!),
                            Library.TYPES["Integer"]!!.methods["*", listOf(Library.TYPES["Integer"]!!)],
                            Library.TYPES["Integer"]!!,
                        ),
                    ),
                    Arguments.of("Decimal Divide", "1.0 / 2.0",
                        RhovasIr.Expression.Binary("/",
                            RhovasIr.Expression.Literal.Scalar(BigDecimal("1.0"), Library.TYPES["Decimal"]!!),
                            RhovasIr.Expression.Literal.Scalar(BigDecimal("2.0"), Library.TYPES["Decimal"]!!),
                            Library.TYPES["Decimal"]!!.methods["/", listOf(Library.TYPES["Decimal"]!!)],
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

                val VARIABLE = Variable.Local("variable", Library.TYPES["Any"]!!, false)

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
                                RhovasIr.Expression.Literal.String(listOf("string"), listOf(), Library.TYPES["String"]!!),
                                Library.TYPES["String"]!!.properties["size"]!!,
                                false,
                                Library.TYPES["Integer"]!!,
                            ),
                        ),
                        //TODO: Coalesce (requires nullable type)
                        Arguments.of("Undefined", "string.undefined", null)
                    )
                }

            }

            @Nested
            inner class IndexTests {

                val LIST = Variable.Local("list", Library.TYPES["List"]!!, false)
                val ANY = Variable.Local("any", Library.TYPES["Any"]!!, false)

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
                                LIST.type.methods["[]", listOf(Library.TYPES["Integer"]!!)]!!,
                                listOf(RhovasIr.Expression.Literal.Scalar(BigInteger("0"), Library.TYPES["Integer"]!!)),
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

                val FUNCTION = Function.Definition("function", listOf(), listOf(Pair("argument", Library.TYPES["String"]!!)), Library.TYPES["Void"]!!, listOf())

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
                                RhovasIr.Expression.Literal.String(listOf("argument"), listOf(), Library.TYPES["String"]!!)
                            )),
                        ),
                        Arguments.of("Invalid Arity", "function()", null),
                        Arguments.of("Undefined", "undefined", null),
                    )
                }

            }

            @Nested
            inner class MethodTests {

                val NULLABLE = Function.Definition(
                    "Nullable",
                    listOf(),
                    listOf(Pair("value", Library.TYPES["String"]!!)),
                    Type.Reference(Library.TYPES["Nullable"]!!.base, listOf(Library.TYPES["String"]!!)),
                    listOf()
                )

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testMethod(name: String, input: String, expected: RhovasIr.Expression.Invoke.Method?) {
                    test("expression", input, expected, Scope(null).also {
                        it.functions.define(NULLABLE)
                    })
                }

                fun testMethod(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Method", "\"string\".contains(\"\")",
                            RhovasIr.Expression.Invoke.Method(
                                RhovasIr.Expression.Literal.String(listOf("string"), listOf(), Library.TYPES["String"]!!),
                                Library.TYPES["String"]!!.methods["contains", listOf(Library.TYPES["String"]!!)]!!,
                                false,
                                false,
                                listOf(RhovasIr.Expression.Literal.String(listOf(""), listOf(), Library.TYPES["String"]!!)),
                                Library.TYPES["Boolean"]!!,
                            ),
                        ),
                        Arguments.of("Coalesce", "Nullable(\"string\")?.contains(\"\")",
                            RhovasIr.Expression.Invoke.Method(
                                RhovasIr.Expression.Invoke.Function(
                                    NULLABLE,
                                    listOf(RhovasIr.Expression.Literal.String(listOf("string"), listOf(), Library.TYPES["String"]!!)),
                                ),
                                Library.TYPES["String"]!!.methods["contains", listOf(Library.TYPES["String"]!!)]!!,
                                true,
                                false,
                                listOf(RhovasIr.Expression.Literal.String(listOf(""), listOf(), Library.TYPES["String"]!!)),
                                Type.Reference(Library.TYPES["Nullable"]!!.base, listOf(Library.TYPES["Boolean"]!!)),
                            )
                        ),
                        Arguments.of("Cascade", "\"string\"..contains(\"\")",
                            RhovasIr.Expression.Invoke.Method(
                                RhovasIr.Expression.Literal.String(listOf("string"), listOf(), Library.TYPES["String"]!!),
                                Library.TYPES["String"]!!.methods["contains", listOf(Library.TYPES["String"]!!)]!!,
                                false,
                                true,
                                listOf(RhovasIr.Expression.Literal.String(listOf(""), listOf(), Library.TYPES["String"]!!)),
                                Library.TYPES["String"]!!,
                            ),
                        ),
                        Arguments.of("Invalid Arity", "\"string\".contains()", null),
                        Arguments.of("Invalid Argument", "\"string\".contains(0)", null),
                        Arguments.of("Undefined", "\"string\".undefined()", null),
                    )
                }

            }

            @Nested
            inner class PipelineTests {

                val NULLABLE = Function.Definition(
                    "Nullable",
                    listOf(),
                    listOf(Pair("value", Library.TYPES["Integer"]!!)),
                    Type.Reference(Library.TYPES["Nullable"]!!.base, listOf(Library.TYPES["Integer"]!!)),
                    listOf()
                )

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testPipeline(name: String, input: String, expected: RhovasIr.Expression.Invoke.Pipeline?) {
                    test("expression", input, expected, Scope(Library.SCOPE).also {
                        it.functions.define(NULLABLE)
                        it.variables.define(Variable.Local("Kernel", Library.TYPES["Kernel"]!!, false))
                    })
                }

                fun testPipeline(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Pipeline", "1.|range(2, :incl)",
                            RhovasIr.Expression.Invoke.Pipeline(
                                RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                                Library.SCOPE.functions["range", listOf(Library.TYPES["Integer"]!!, Library.TYPES["Integer"]!!, Library.TYPES["Atom"]!!)]!! as Function.Definition,
                                false,
                                false,
                                listOf(
                                    RhovasIr.Expression.Literal.Scalar(BigInteger("2"), Library.TYPES["Integer"]!!),
                                    RhovasIr.Expression.Literal.Scalar(RhovasAst.Atom("incl"), Library.TYPES["Atom"]!!)
                                ),
                                Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Integer"]!!)),
                            ),
                        ),
                        Arguments.of("Qualified", "1.|Kernel.range(2, :incl)",
                            RhovasIr.Expression.Invoke.Pipeline(
                                RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                                Library.SCOPE.functions["range", listOf(Library.TYPES["Integer"]!!, Library.TYPES["Integer"]!!, Library.TYPES["Atom"]!!)]!! as Function.Definition,
                                false,
                                false,
                                listOf(
                                    RhovasIr.Expression.Literal.Scalar(BigInteger("2"), Library.TYPES["Integer"]!!),
                                    RhovasIr.Expression.Literal.Scalar(RhovasAst.Atom("incl"), Library.TYPES["Atom"]!!)
                                ),
                                Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Integer"]!!)),
                            ),
                        ),
                        Arguments.of("Coalesce", "Nullable(1)?.|range(2, :incl)",
                            RhovasIr.Expression.Invoke.Pipeline(
                                RhovasIr.Expression.Invoke.Function(
                                    NULLABLE,
                                    listOf(RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!)),
                                ),
                                Library.SCOPE.functions["range", listOf(Library.TYPES["Integer"]!!, Library.TYPES["Integer"]!!, Library.TYPES["Atom"]!!)]!! as Function.Definition,
                                true,
                                false,
                                listOf(
                                    RhovasIr.Expression.Literal.Scalar(BigInteger("2"), Library.TYPES["Integer"]!!),
                                    RhovasIr.Expression.Literal.Scalar(RhovasAst.Atom("incl"), Library.TYPES["Atom"]!!)
                                ),
                                Type.Reference(Library.TYPES["Nullable"]!!.base, listOf(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Integer"]!!)))),
                            ),
                        ),
                        Arguments.of("Cascade", "1..|range(2, :incl)",
                            RhovasIr.Expression.Invoke.Pipeline(
                                RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!),
                                Library.SCOPE.functions["range", listOf(Library.TYPES["Integer"]!!, Library.TYPES["Integer"]!!, Library.TYPES["Atom"]!!)]!! as Function.Definition,
                                false,
                                true,
                                listOf(
                                    RhovasIr.Expression.Literal.Scalar(BigInteger("2"), Library.TYPES["Integer"]!!),
                                    RhovasIr.Expression.Literal.Scalar(RhovasAst.Atom("incl"), Library.TYPES["Atom"]!!)
                                ),
                                Library.TYPES["Integer"]!!,
                            ),
                        ),
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
                        Library.SCOPE.functions["lambda", listOf(Library.TYPES["Lambda"]!!)]!! as Function.Definition,
                        listOf(it)
                    )
                }
                test("expression", input, expected, Library.SCOPE)
            }

            fun testLambda(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Lambda", """
                        lambda { stmt(); }
                    """.trimIndent(),
                        RhovasIr.Expression.Lambda(
                            listOf(),
                            RhovasIr.Statement.Block(listOf(stmt())),
                            Type.Reference(Library.TYPES["Lambda"]!!.base, listOf(Library.TYPES["Dynamic"]!!, Library.TYPES["Dynamic"]!!)),
                        ),
                    ),
                    Arguments.of("Return Value", """
                        lambda { return 1; }
                    """.trimIndent(),
                        RhovasIr.Expression.Lambda(
                            listOf(),
                            RhovasIr.Statement.Block(listOf(
                                RhovasIr.Statement.Return(RhovasIr.Expression.Literal.Scalar(BigInteger("1"), Library.TYPES["Integer"]!!)),
                            )),
                            Type.Reference(Library.TYPES["Lambda"]!!.base, listOf(Library.TYPES["Dynamic"]!!, Library.TYPES["Dynamic"]!!)),
                        ),
                    ),
                    //TODO: Validation
                )
            }

        }

        @Nested
        inner class DslTests {

            val DSL = Function.Definition("dsl",
                listOf(),
                listOf(
                    Pair("literals", Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!))),
                    Pair("arguments", Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!))),
                ),
                Library.TYPES["Dynamic"]!!,
                listOf(),
            )

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testDsl(name: String, input: String, expected: RhovasIr.Expression?) {
                test("expression", input, expected, Scope(Library.SCOPE).also {
                    it.functions.define(DSL)
                })
            }

            fun testDsl(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("DSL", """
                        #dsl {
                            literal
                        }
                    """.trimIndent(),
                        RhovasIr.Expression.Invoke.Function(DSL, listOf(
                            RhovasIr.Expression.Literal.List(listOf(
                                RhovasIr.Expression.Literal.String(listOf("literal"), listOf(), Library.TYPES["String"]!!),
                            ), Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!))),
                            RhovasIr.Expression.Literal.List(
                                listOf(),
                                Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!))
                            ),
                        ))
                    ),
                    Arguments.of("Argument", """
                        #dsl {
                            argument = ${'$'}{"argument"}
                        }
                    """.trimIndent(),
                        RhovasIr.Expression.Invoke.Function(DSL, listOf(
                            RhovasIr.Expression.Literal.List(listOf(
                                RhovasIr.Expression.Literal.String(listOf("argument = "), listOf(), Library.TYPES["String"]!!),
                                RhovasIr.Expression.Literal.String(listOf(""), listOf(), Library.TYPES["String"]!!),
                            ), Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!))),
                            RhovasIr.Expression.Literal.List(listOf(
                                RhovasIr.Expression.Interpolation(RhovasIr.Expression.Literal.String(listOf("argument"), listOf(), Library.TYPES["String"]!!)),
                            ), Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!))),
                        ))
                    ),
                    Arguments.of("Undefined DSL", """
                        #undefined {}
                    """.trimIndent(), null)
                )
            }

        }

    }

    private fun stmt(position: Int): RhovasIr.Statement {
        return stmt(RhovasIr.Expression.Literal.Scalar(position.toBigInteger(), Library.TYPES["Integer"]!!))
    }

    private fun stmt(argument: RhovasIr.Expression? = null): RhovasIr.Statement {
        return RhovasIr.Statement.Expression(when (argument) {
            null -> RhovasIr.Expression.Invoke.Function(STMT_0, listOf())
            else -> RhovasIr.Expression.Invoke.Function(STMT_1, listOf(argument))
        })
    }

    private fun test(rule: String, input: String, expected: RhovasIr?, scope: Scope = Scope(null)) {
        val ast = RhovasParser(Input("AnalyzerTests.test", input)).parse(rule)
        val analyzer = RhovasAnalyzer(scope.also {
            it.functions.define(STMT_0)
            it.functions.define(STMT_1)
        })
        if (expected != null) {
            val ir = analyzer.visit(ast)
            Assertions.assertEquals(expected, ir)
            Assertions.assertTrue(ast.context.isNotEmpty() || input.isEmpty())
        } else {
            Assertions.assertThrows(AnalyzeException::class.java) { RhovasAnalyzer(scope).visit(ast) }
        }
    }

}
