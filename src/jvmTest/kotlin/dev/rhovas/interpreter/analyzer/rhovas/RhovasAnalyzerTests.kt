package dev.rhovas.interpreter.analyzer.rhovas

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.analyzer.AnalyzeException
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import dev.rhovas.interpreter.parser.rhovas.RhovasParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class RhovasAnalyzerTests {

    lateinit var STMT_0: Function.Declaration
    lateinit var STMT_1: Function.Declaration

    @BeforeAll
    fun beforeAll() {
        STMT_0 = Function.Declaration("stmt", listOf(), listOf(), Type.VOID, listOf())
        STMT_1 = Function.Declaration("stmt", listOf(), listOf(Variable.Declaration("position", Type.INTEGER, false)), Type.VOID, listOf())
    }

    @Nested
    inner class SourceTests {

        val MODULE = Type.Base("Module", Scope.Definition(null)).reference.also {
            it.base.scope.types.define(Type.Base("Module.Type", Scope.Definition(null)).reference, "Type")
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testSource(name: String, input: String, expected: (() -> RhovasIr.Source?)?) {
            test("source", input, expected?.invoke()) {
                it.types.define(MODULE)
            }
        }

        fun testSource(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Empty", """
                    
                """.trimIndent(), {
                    RhovasIr.Source(listOf(), listOf())
                }),
                Arguments.of("Single Statement", """
                    stmt();
                """.trimIndent(), {
                    RhovasIr.Source(listOf(), listOf(stmt()))
                }),
                Arguments.of("Multiple Statements", """
                    stmt(1); stmt(2); stmt(3);
                """.trimIndent(), {
                    RhovasIr.Source(listOf(), listOf(stmt(1), stmt(2), stmt(3)))
                }),
            )
        }

    }

    @Nested
    inner class ImportTests {

        val MODULE = Type.Base("Module", Scope.Definition(null)).reference.also {
            it.base.scope.types.define(Type.Base("Module.Type", Scope.Definition(null)).reference, "Type")
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testImport(name: String, input: String, expected: (() -> RhovasIr.Source?)?) {
            test("source", input, expected?.invoke())
        }

        fun testImport(): Stream<Arguments> {
            Library.TYPES.define(MODULE)
            Library.TYPES.define(MODULE.base.scope.types["Type"]!!)
            return Stream.of(
                Arguments.of("Import Module", """
                    import Module;
                    val name: Module;
                """.trimIndent(), {
                    RhovasIr.Source(
                        listOf(RhovasIr.Import(Library.type("Module"))),
                        listOf(RhovasIr.Statement.Declaration.Variable(Variable.Declaration("name", Library.type("Module"), false), null))
                    )
                }),
                Arguments.of("Import Submodule", """
                    import Module.Type;
                    val name: Module.Type;
                """.trimIndent(), {
                    RhovasIr.Source(
                        listOf(RhovasIr.Import(Library.type("Module.Type"))),
                        listOf(RhovasIr.Statement.Declaration.Variable(Variable.Declaration("name", Library.type("Module.Type"), false), null))
                    )
                }),
                Arguments.of("Import Alias", """
                    import Module.Type as Alias;
                    val name: Alias;
                """.trimIndent(), {
                    RhovasIr.Source(
                        listOf(RhovasIr.Import(Library.type("Module.Type"))),
                        listOf(RhovasIr.Statement.Declaration.Variable(Variable.Declaration("name", Library.type("Module.Type"), false), null))
                    )
                }),
                Arguments.of("Undefined Import", """
                    import Undefined;
                """.trimIndent(), null),
                Arguments.of("Redefined Type", """
                    import Module;
                    import Module;
                """.trimIndent(), null),
            )
        }

    }

    @Nested
    inner class ComponentTests {

        @Nested
        inner class StructTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testStruct(name: String, input: String, expected: (() -> RhovasIr.Source?)?) {
                test("source", input, expected?.invoke())
            }

            fun testStruct(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Struct", """
                        struct Name {}
                        val instance = Name({});
                    """.trimIndent(), {
                        val type = Type.Base("Name", Scope.Definition(null)).reference
                        type.base.inherit(Type.STRUCT[Type.Struct(mapOf())])
                        RhovasIr.Source(listOf(), listOf(
                            RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf())),
                            RhovasIr.Statement.Declaration.Variable(
                                Variable.Declaration("instance", type, false),
                                RhovasIr.Expression.Invoke.Constructor(
                                    type,
                                    Function.Definition(Function.Declaration("", listOf(), listOf(Variable.Declaration("fields", type.base.inherits[0], false)), type, listOf())),
                                    listOf(RhovasIr.Expression.Literal.Object(mapOf(), type.base.inherits[0])),
                                ),
                            ),
                        ))
                    }),
                    Arguments.of("Field", """
                        struct Name { val field: Integer; }
                        val field = Name({field: 1}).field;
                    """.trimIndent(), {
                        val type = Type.Base("Name", Scope.Definition(null)).reference
                        type.base.inherit(Type.STRUCT[Type.Struct(mapOf("field" to Variable.Declaration("field", Type.INTEGER, false)))])
                        type.base.scope.functions.define(Function.Definition(Function.Declaration("field", listOf(), listOf(Variable.Declaration("this", type, false)), Type.INTEGER, listOf())))
                        RhovasIr.Source(listOf(), listOf(
                            RhovasIr.Statement.Component(RhovasIr.Component.Struct(type,
                                listOf(RhovasIr.Member.Property(type.properties["field"]!!.getter.function as Function.Definition, null, null)),
                            )),
                            RhovasIr.Statement.Declaration.Variable(
                                Variable.Declaration("field", Type.INTEGER, false),
                                RhovasIr.Expression.Access.Property(
                                    RhovasIr.Expression.Invoke.Constructor(
                                        type,
                                        Function.Definition(Function.Declaration("", listOf(), listOf(Variable.Declaration("fields", type.base.inherits[0], false)), type, listOf())),
                                        listOf(RhovasIr.Expression.Literal.Object(mapOf("field" to literal(BigInteger.parseString("1"))), type.base.inherits[0])),
                                    ),
                                    type.properties["field"]!!,
                                    false,
                                    Type.INTEGER,
                                ),
                            ),
                        ))
                    }),
                    Arguments.of("Function", """
                        struct Name { func function(): Integer { return 1; } }
                        Name.function();
                    """.trimIndent(), {
                        val type = Type.Base("Name", Scope.Definition(null)).reference
                        type.base.inherit(Type.STRUCT[Type.Struct(mapOf())])
                        type.base.scope.functions.define(Function.Definition(Function.Declaration("function", listOf(), listOf(), Type.INTEGER, listOf())))
                        RhovasIr.Source(listOf(), listOf(
                            RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                                RhovasIr.Member.Method(RhovasIr.Statement.Declaration.Function(
                                    type.functions["function", listOf()]!!,
                                    block(RhovasIr.Statement.Return(literal(BigInteger.parseString("1")))),
                                )),
                            ))),
                            RhovasIr.Statement.Expression(RhovasIr.Expression.Invoke.Function(type, type.functions["function", listOf()]!!, listOf())),
                        ))
                    }),
                    Arguments.of("Method", """
                        struct Name {
                            val field: Integer;
                            func method(this): Integer { return this.field; }
                        }
                        Name({field: 1}).method();
                    """.trimIndent(), {
                        val type = Type.Base("Name", Scope.Definition(null)).reference
                        type.base.inherit(Type.STRUCT[Type.Struct(mapOf("field" to Variable.Declaration("field", Type.INTEGER, false)))])
                        type.base.scope.functions.define(Function.Definition(Function.Declaration("field", listOf(), listOf(Variable.Declaration("this", type, false)), Type.INTEGER, listOf())))
                        type.base.scope.functions.define(Function.Definition(Function.Declaration("method", listOf(), listOf(Variable.Declaration("this", type, false)), Type.INTEGER, listOf())))
                        RhovasIr.Source(listOf(), listOf(
                            RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                                RhovasIr.Member.Property(type.properties["field"]!!.getter.function as Function.Definition, null, null),
                                RhovasIr.Member.Method(RhovasIr.Statement.Declaration.Function(
                                    type.methods["method", listOf()]!!.function,
                                    block(RhovasIr.Statement.Return(RhovasIr.Expression.Access.Property(variable("this", type), type.properties["field"]!!, false, Type.INTEGER))),
                                )),
                            ))),
                            RhovasIr.Statement.Expression(RhovasIr.Expression.Invoke.Method(
                                RhovasIr.Expression.Invoke.Constructor(
                                    type,
                                    Function.Definition(Function.Declaration("", listOf(), listOf(Variable.Declaration("fields", type.base.inherits[0], false)), type, listOf())),
                                    listOf(RhovasIr.Expression.Literal.Object(mapOf("field" to literal(BigInteger.parseString("1"))), type.base.inherits[0])),
                                ),
                                type.methods["method", listOf()]!!,
                                false, false, listOf(), Type.INTEGER)),
                        ))
                    }),
                )
            }

        }

    }

    @Nested
    inner class StatementTests {

        @Nested
        inner class BlockTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testBlock(name: String, input: String, expected: (() -> RhovasIr.Expression.Block?)?) {
                test("statement", input, expected?.invoke()?.let { RhovasIr.Statement.Expression(it) })
            }

            fun testBlock(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        {}
                    """.trimIndent(), {
                        RhovasIr.Expression.Block(listOf(), null, Type.VOID)
                    }),
                    Arguments.of("Single", """
                        { stmt(); }
                    """.trimIndent(), {
                        RhovasIr.Expression.Block(listOf(stmt()), null, Type.VOID)
                    }),
                    Arguments.of("Multiple", """
                        { stmt(1); stmt(2); stmt(3); }
                    """.trimIndent(), {
                        RhovasIr.Expression.Block(listOf(stmt(1), stmt(2), stmt(3)), null, Type.VOID)
                    }),
                    Arguments.of("Unreachable", """
                        { return; stmt(); }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class ComponentTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testComponent(name: String, input: String, expected: (() -> RhovasIr.Statement.Component?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testComponent(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Struct", """
                        struct Name {}
                    """.trimIndent(), {
                        val type = Type.Base("Name", Scope.Definition(null)).reference
                        type.base.inherit(Type.STRUCT[Type.Struct(mapOf())])
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf()))
                    }),
                )
            }

        }

        @Nested
        inner class ExpressionTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testExpression(name: String, input: String, expected: (() -> RhovasIr.Statement.Expression?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testExpression(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Function", """
                        stmt();
                    """.trimIndent(), {
                        RhovasIr.Statement.Expression(
                            RhovasIr.Expression.Invoke.Function(null, STMT_0, listOf())
                        )
                    }),
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
            fun testFunction(name: String, input: String, expected: (() -> RhovasIr.Statement.Declaration.Function?)?) {
                test("statement", input, expected?.invoke()) {
                    it.types.define(Type.Base("SubtypeException", Scope.Definition(null)).reference.also { it.base.inherit(Type.EXCEPTION) })
                    it.functions.define(Function.Declaration("fail", listOf(), listOf(Variable.Declaration("message", Type.STRING, false)), Type.VOID, listOf(Type.EXCEPTION)))
                }
            }

            fun testFunction(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Definition", """
                        func name() {
                            name();
                        }
                    """.trimIndent(), {
                        val func = Function.Declaration("name", listOf(), listOf(), Type.VOID, listOf())
                        RhovasIr.Statement.Declaration.Function(
                            func,
                            block(RhovasIr.Statement.Expression(RhovasIr.Expression.Invoke.Function(null, func, listOf()))),
                        )
                    }),
                    Arguments.of("Parameter", """
                        func name(parameter: Integer) {
                            stmt(parameter);
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("name", listOf(), listOf(Variable.Declaration("parameter", Type.INTEGER, false)), Type.VOID, listOf()),
                            block(stmt(variable("parameter", Type.INTEGER))),
                        )
                    }),
                    Arguments.of("Return Value", """
                        func name(): Integer {
                            return 1;
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("name", listOf(), listOf(), Type.INTEGER, listOf()),
                            block(RhovasIr.Statement.Return(literal(BigInteger.parseString("1")))),
                        )
                    }),
                    Arguments.of("If Return", """
                        func name(): Integer {
                            if (true) {
                                return 1;
                            } else {
                                return 2;
                            }
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("name", listOf(), listOf(), Type.INTEGER, listOf()),
                            block(RhovasIr.Statement.If(
                                literal(true),
                                block(RhovasIr.Statement.Return(literal(BigInteger.parseString("1")))),
                                block(RhovasIr.Statement.Return(literal(BigInteger.parseString("2")))),
                            )),
                        )
                    }),
                    Arguments.of("Conditional Match Return", """
                        func name(): Integer {
                            match {
                                true: return 1;
                                else: return 2;
                            }
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("name", listOf(), listOf(), Type.INTEGER, listOf()),
                            block(RhovasIr.Statement.Match.Conditional(
                                listOf(literal(true) to RhovasIr.Statement.Return(literal(BigInteger.parseString("1")))),
                                null to RhovasIr.Statement.Return(literal(BigInteger.parseString("2"))),
                            )),
                        )
                    }),
                    Arguments.of("Structural Match Return", """
                        func name(): Integer {
                            match (true) {
                                true: return 1;
                            }
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("name", listOf(), listOf(), Type.INTEGER, listOf()),
                            block(RhovasIr.Statement.Match.Structural(
                                literal(true),
                                listOf(RhovasIr.Pattern.Value(literal(true)) to RhovasIr.Statement.Return(literal(BigInteger.parseString("1")))),
                                null,
                            )),
                        )
                    }),
                    Arguments.of("Throws", """
                        func name() throws Exception {
                            throw Exception("message");
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("name", listOf(), listOf(), Type.VOID, listOf(Type.EXCEPTION)),
                            block(RhovasIr.Statement.Throw(RhovasIr.Expression.Invoke.Constructor(
                                Type.EXCEPTION.base.reference,
                                Type.EXCEPTION.functions["", listOf(Type.STRING)]!! as Function.Definition,
                                listOf(literal("message")),
                            ))),
                        )
                    }),
                    Arguments.of("Generic", """
                        func first<T>(list: List<T>): T {
                            return list[0];
                        }
                    """.trimIndent(), {
                        val listT = Type.LIST[Type.Generic("T", Type.ANY)]
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("first",
                                listOf(Type.Generic("T", Type.ANY)),
                                listOf(Variable.Declaration("list", listT, false)),
                                Type.Generic("T", Type.ANY),
                                listOf(),
                            ),
                            block(RhovasIr.Statement.Return(RhovasIr.Expression.Access.Index(
                                variable("list", listT),
                                listT.methods["[]", listOf(Type.INTEGER)]!!,
                                listOf(literal(BigInteger.parseString("0"))),
                            ))),
                        )
                    }),
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
            fun testDeclaration(name: String, input: String, expected: (() -> RhovasIr.Statement.Declaration.Variable?)?) {
                val expected = expected?.invoke()?.let {
                    RhovasIr.Source(listOf(), listOf(
                        it,
                        stmt(RhovasIr.Expression.Access.Variable(null, it.variable)),
                    ))
                }
                test("source", input, expected) {
                    it.variables.define(variable("any", Type.ANY).variable)
                }
            }

            fun testDeclaration(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Val", """
                        val name: Integer = 1;
                        stmt(name);
                    """.trimIndent(), {
                        RhovasIr.Statement.Declaration.Variable(
                            Variable.Declaration("name", Type.INTEGER, false),
                            literal(BigInteger.parseString("1")),
                        )
                    }),
                    Arguments.of("Var", """
                        var name: Integer = 1;
                        stmt(name);
                    """.trimIndent(), {
                        RhovasIr.Statement.Declaration.Variable(
                            Variable.Declaration("name", Type.INTEGER, true),
                            literal(BigInteger.parseString("1")),
                        )
                    }),
                    Arguments.of("Subtype Value", """
                        val name: Dynamic = 1;
                        stmt(name);
                    """.trimIndent(), {
                        RhovasIr.Statement.Declaration.Variable(
                            Variable.Declaration("name", Type.DYNAMIC, false),
                            literal(BigInteger.parseString("1")),
                        )
                    }),
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
            fun testVariable(name: String, input: String, expected: (() -> RhovasIr.Statement.Assignment.Variable?)?) {
                val expected = expected?.invoke()?.let {
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Declaration.Variable(it.variable as Variable.Declaration, null),
                        it,
                    ))
                }
                test("source", input, expected) {
                    it.variables.define(variable("any", Type.ANY).variable)
                }
            }

            fun testVariable(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Var Initialization", """
                        var variable: Integer;
                        variable = 1;
                    """.trimIndent(), {
                        RhovasIr.Statement.Assignment.Variable(
                            Variable.Declaration("variable", Type.INTEGER, true),
                            literal(BigInteger.parseString("1")),
                        )
                    }),
                    Arguments.of("Val Initialization", """
                        val variable: Integer;
                        variable = 1;
                    """.trimIndent(), {
                        RhovasIr.Statement.Assignment.Variable(
                            Variable.Declaration("variable", Type.INTEGER, false),
                            literal(BigInteger.parseString("1")),
                        )
                    }),
                    Arguments.of("Subtype Value", """
                        var variable: Dynamic;
                        variable = 1;
                    """.trimIndent(), {
                        RhovasIr.Statement.Assignment.Variable(
                            Variable.Declaration("variable", Type.DYNAMIC, true),
                            literal(BigInteger.parseString("1")),
                        )
                    }),
                    Arguments.of("Undefined Variable", """
                        undefined = 1;
                    """.trimIndent(), null),
                    Arguments.of("Unassignable Variable", """
                        val unassignable = 1;
                        unassignable = 1;
                    """.trimIndent(), null),
                    Arguments.of("Reinitialized Variable", """
                        val variable: Integer;
                        variable = 1;
                        variable = 2;
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

            private val ObjectType = Type.Base("ObjectType", Scope.Definition(null)).reference.also {
                it.base.scope.functions.define(Function.Definition(Function.Declaration("property", listOf(), listOf(Variable.Declaration("instance", it, false)), Type.INTEGER, listOf())))
                it.base.scope.functions.define(Function.Definition(Function.Declaration("property", listOf(), listOf(Variable.Declaration("instance", it, false), Variable.Declaration("value", Type.INTEGER, false)), Type.VOID, listOf())))
                it.base.scope.functions.define(Function.Definition(Function.Declaration("dynamic", listOf(), listOf(Variable.Declaration("instance", it, false)), Type.DYNAMIC, listOf())))
                it.base.scope.functions.define(Function.Definition(Function.Declaration("dynamic", listOf(), listOf(Variable.Declaration("instance", it, false), Variable.Declaration("value", Type.DYNAMIC, false)), Type.VOID, listOf())))
                it.base.scope.functions.define(Function.Definition(Function.Declaration("unassignable", listOf(), listOf(Variable.Declaration("instance", it, false)), Type.INTEGER, listOf())))
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testProperty(name: String, input: String, expected: (() -> RhovasIr.Statement.Assignment.Property?)?) {
                test("statement", input, expected?.invoke()) {
                    it.variables.define(variable("object", ObjectType).variable)
                }
            }

            fun testProperty(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Assignment", """
                        object.property = 1;
                    """.trimIndent(), {
                        RhovasIr.Statement.Assignment.Property(
                            variable("object", ObjectType),
                            ObjectType.properties["property"]!!,
                            literal(BigInteger.parseString("1")),
                        )
                    }),
                    Arguments.of("Subtype Value", """
                        object.dynamic = 1;
                    """.trimIndent(), {
                        RhovasIr.Statement.Assignment.Property(
                            variable("object", ObjectType),
                            ObjectType.properties["dynamic"]!!,
                            literal(BigInteger.parseString("1")),
                        )
                    }),
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

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIndex(name: String, input: String, expected: (() -> RhovasIr.Statement.Assignment.Index?)?) {
                test("statement", input, expected?.invoke()) {
                    it.variables.define(variable("list", Type.LIST[Type.INTEGER]).variable)
                    it.variables.define(variable("dynamic", Type.LIST[Type.DYNAMIC]).variable)
                }
            }

            fun testIndex(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Assignment", """
                        list[0] = 1;
                    """.trimIndent(), {
                        RhovasIr.Statement.Assignment.Index(
                            variable("list", Type.LIST[Type.INTEGER]),
                            Type.LIST[Type.INTEGER].methods["[]=", listOf(Type.INTEGER, Type.INTEGER)]!!,
                            listOf(literal(BigInteger.parseString("0"))),
                            literal(BigInteger.parseString("1")),
                        )
                    }),
                    Arguments.of("Subtype Value", """
                        dynamic[0] = 1;
                    """.trimIndent(), {
                        RhovasIr.Statement.Assignment.Index(
                            variable("dynamic", Type.LIST[Type.DYNAMIC]),
                            Type.LIST[Type.DYNAMIC].methods["[]=", listOf(Type.INTEGER, Type.DYNAMIC)]!!,
                            listOf(literal(BigInteger.parseString("0"))),
                            literal(BigInteger.parseString("1")),
                        )
                    }),
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
            fun testIf(name: String, input: String, expected: (() -> RhovasIr.Statement.If?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testIf(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("If", """
                        if (true) { stmt(); }
                    """.trimIndent(), {
                        RhovasIr.Statement.If(
                            literal(true),
                            block(stmt()),
                            null,
                        )
                    }),
                    Arguments.of("Else", """
                        if (true) { stmt(1); } else { stmt(2); }
                    """.trimIndent(), {
                        RhovasIr.Statement.If(
                            literal(true),
                            block(stmt(1)),
                            block(stmt(2)),
                        )
                    }),
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
            fun testFor(name: String, input: String, expected: (() -> RhovasIr.Statement.For?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testFor(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("For", """
                        for (val element in []) { stmt(); }
                    """.trimIndent(), {
                        RhovasIr.Statement.For(
                            Variable.Declaration("element", Type.DYNAMIC, false),
                            RhovasIr.Expression.Literal.List(listOf(),Type.LIST[Type.DYNAMIC]),
                            block(stmt()),
                        )
                    }),
                    Arguments.of("Element", """
                        for (val element in [1]) { stmt(element); }
                    """.trimIndent(), {
                        RhovasIr.Statement.For(
                            Variable.Declaration("element", Type.INTEGER, false),
                            RhovasIr.Expression.Literal.List(listOf(
                                literal(BigInteger.parseString("1")),
                            ), Type.LIST[Type.INTEGER]),
                            block(stmt(variable("element", Type.INTEGER))),
                        )
                    }),
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
            fun testWhile(name: String, input: String, expected: (() -> RhovasIr.Statement.While?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testWhile(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("While", """
                        while (true) { stmt(); }
                    """.trimIndent(), {
                        RhovasIr.Statement.While(
                            literal(true),
                            block(stmt()),
                        )
                    }),
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
            fun testTry(name: String, input: String, expected: (() -> RhovasIr.Statement.Try?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testTry(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Try", """
                        try { stmt(); }
                    """.trimIndent(), {
                        RhovasIr.Statement.Try(
                            block(stmt()),
                            listOf(),
                            null,
                        )
                    }),
                    Arguments.of("Catch", """
                        try {
                            stmt(1);
                        } catch (val e: Exception) {
                            stmt(2);
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Try(
                            block(stmt(1)),
                            listOf(RhovasIr.Statement.Try.Catch(Variable.Declaration("e", Type.EXCEPTION, false), block(stmt(2)))),
                            null,
                        )
                    }),
                    Arguments.of("Finally", """
                        try {
                            stmt(1);
                        } finally {
                            stmt(2);
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Try(
                            block(stmt(1)),
                            listOf(),
                            block(stmt(2)),
                        )
                    }),
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
            fun testWith(name: String, input: String, expected: (() -> RhovasIr.Statement.With?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testWith(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("With", """
                        with (1) { stmt(); }
                    """.trimIndent(), {
                        RhovasIr.Statement.With(null,
                            literal(BigInteger.parseString("1")),
                            block(stmt()),
                        )
                    }),
                    Arguments.of("Named Argument", """
                        with (val name = 1) { stmt(name); }
                    """.trimIndent(), {
                        RhovasIr.Statement.With(
                            Variable.Declaration("name", Type.INTEGER, false),
                            literal(BigInteger.parseString("1")),
                            block(stmt(variable("name", Type.INTEGER))),
                        )
                    }),
                )
            }

        }

        @Nested
        inner class LabelTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLabel(name: String, input: String, expected: (() -> RhovasIr.Statement.Label?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testLabel(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("For", """
                        label: for (val element in []) {
                            break label;
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Label("label",
                            RhovasIr.Statement.For(
                                Variable.Declaration("element", Type.DYNAMIC, false),
                                RhovasIr.Expression.Literal.List(listOf(), Type.LIST[Type.DYNAMIC]),
                                block(RhovasIr.Statement.Break("label")),
                            ),
                        )
                    }),
                    Arguments.of("While", """
                        label: while (true) {
                            break label;
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Label("label",
                            RhovasIr.Statement.While(
                                literal(true),
                                block(RhovasIr.Statement.Break("label")),
                            ),
                        )
                    }),
                    Arguments.of("Unused Label", """
                        label: while (true) {}
                    """.trimIndent(), {
                        RhovasIr.Statement.Label("label",
                            RhovasIr.Statement.While(
                                literal(true),
                                block(),
                            ),
                        )
                    }),
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
                )
            }

        }

        @Nested
        inner class BreakTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testBreak(name: String, input: String, expected: (() -> RhovasIr.Statement?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testBreak(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Break", """
                        while (true) {
                            break;
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.While(
                            literal(true),
                            block(RhovasIr.Statement.Break(null)),
                        )
                    }),
                    Arguments.of("Label", """
                        label: while (true) {
                            break label;
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Label(
                            "label",
                            RhovasIr.Statement.While(
                                literal(true),
                                block(RhovasIr.Statement.Break("label")),
                            ),
                        )
                    }),
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
            fun testContinue(name: String, input: String, expected: (() -> RhovasIr.Statement?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testContinue(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Continue", """
                        while (true) {
                            continue;
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.While(
                            literal(true),
                            block(RhovasIr.Statement.Continue(null)),
                        )
                    }),
                    Arguments.of("Label", """
                        label: while (true) {
                            continue label;
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Label("label",
                            RhovasIr.Statement.While(
                                literal(true),
                                block(RhovasIr.Statement.Continue("label")),
                            ),
                        )
                    }),
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
            fun testReturn(name: String, input: String, expected: (() -> RhovasIr.Statement?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testReturn(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Return Void", """
                        func test() {
                            return;
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("test", listOf(), listOf(), Type.VOID, listOf()),
                            block(RhovasIr.Statement.Return(null)),
                        )
                    }),
                    Arguments.of("Return Value", """
                        func test(): Integer {
                            return 1;
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("test", listOf(), listOf(), Type.INTEGER, listOf()),
                            block(RhovasIr.Statement.Return(literal(BigInteger.parseString("1")))),
                        )
                    }),
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
            fun testAssert(name: String, input: String, expected: (() -> RhovasIr.Statement.Assert?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testAssert(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Assert", """
                        assert true;
                    """.trimIndent(), {
                        RhovasIr.Statement.Assert(literal(true), null)
                    }),
                    Arguments.of("Message", """
                        assert true: "message";
                    """.trimIndent(), {
                        RhovasIr.Statement.Assert(literal(true), literal("message"))
                    }),
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
            fun testRequire(name: String, input: String, expected: (() -> RhovasIr.Statement.Require?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testRequire(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Require", """
                        require true;
                    """.trimIndent(), {
                        RhovasIr.Statement.Require(literal(true), null)
                    }),
                    Arguments.of("Message", """
                        require true: "message";
                    """.trimIndent(), {
                        RhovasIr.Statement.Require(literal(true), literal("message"))
                    }),
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
            fun testEnsure(name: String, input: String, expected: (() -> RhovasIr.Statement.Ensure?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testEnsure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Ensure", """
                        ensure true;
                    """.trimIndent(), {
                        RhovasIr.Statement.Ensure(literal(true), null)
                    }),
                    Arguments.of("Message", """
                        ensure true: "message";
                    """.trimIndent(), {
                        RhovasIr.Statement.Ensure(literal(true), literal("message"))
                    }),
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
            fun testScalar(name: String, input: String, expected: (() -> RhovasIr.Expression.Literal?)?) {
                test("expression", input, expected?.invoke())
            }

            fun testScalar(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Null", """
                        null
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.Scalar(null, Type.NULLABLE.ANY)
                    }),
                    Arguments.of("Boolean", """
                        true
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.Scalar(true, Type.BOOLEAN)
                    }),
                    Arguments.of("Integer", """
                        123
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.Scalar(BigInteger.parseString("123"), Type.INTEGER)
                    }),
                    Arguments.of("Decimal", """
                        123.456
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.Scalar(BigDecimal.parseString("123.456"), Type.DECIMAL)
                    }),
                    Arguments.of("Atom", """
                        :atom
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.Scalar(RhovasAst.Atom("atom"), Type.ATOM)
                    }),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testString(name: String, input: String, expected: (() -> RhovasIr.Expression.Literal.String?)?) {
                test("expression", input, expected?.invoke())
            }

            fun testString(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("String", """
                        "string"
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.String(listOf("string"), listOf(), Type.STRING)
                    }),
                    Arguments.of("Interpolation", """
                        "first${'$'}{1}second"
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.String(listOf("first", "second"), listOf(literal(BigInteger.parseString("1"))), Type.STRING)
                    }),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testList(name: String, input: String, expected: (() -> RhovasIr.Expression.Literal.List?)?) {
                test("expression", input, expected?.invoke())
            }

            fun testList(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        []
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.List(listOf(), Type.LIST[Type.DYNAMIC])
                    }),
                    Arguments.of("Single", """
                        [1]
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.List(listOf(
                            literal(BigInteger.parseString("1")),
                        ), Type.LIST[Type.INTEGER])
                    }),
                    Arguments.of("Multiple", """
                        [1, 2, 3]
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.List(listOf(
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            literal(BigInteger.parseString("3")),
                        ), Type.LIST[Type.INTEGER])
                    }),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testObject(name: String, input: String, expected: (() -> RhovasIr.Expression.Literal.Object?)?) {
                test("expression", input, expected?.invoke())
            }

            fun testObject(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        {}
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.Object(mapOf(), Type.OBJECT)
                    }),
                    Arguments.of("Single", """
                        {key: "value"}
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.Object(mapOf(
                            "key" to literal("value"),
                        ), Type.OBJECT)
                    }),
                    Arguments.of("Multiple", """
                        {k1: "v1", k2: "v2", k3: "v3"}
                    """.trimIndent(), {
                        RhovasIr.Expression.Literal.Object(mapOf(
                            "k1" to literal("v1"),
                            "k2" to literal("v2"),
                            "k3" to literal("v3"),
                        ), Type.OBJECT)
                    }),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testType(name: String, input: String, expected: RhovasIr.Expression.Literal.Type) {
                test("expression", input, expected)
            }

            fun testType(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Type", """
                        Any
                    """.trimIndent(), RhovasIr.Expression.Literal.Type(
                        Type.ANY,
                        Type.TYPE[Type.ANY],
                    )),
                )
            }

        }

        @Nested
        inner class GroupTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testGroup(name: String, input: String, expected: (() -> RhovasIr.Expression.Group?)?) {
                test("expression", input, expected?.invoke())
            }

            fun testGroup(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Literal", """
                        ("expression")
                    """.trimIndent(), {
                        RhovasIr.Expression.Group(literal("expression"))
                    }),
                    Arguments.of("Binary", """
                        ("first" + "second")
                    """.trimIndent(), {
                        RhovasIr.Expression.Group(RhovasIr.Expression.Binary("+",
                            literal("first"),
                            literal("second"),
                            Type.STRING.methods["+", listOf(Type.STRING)],
                            Type.STRING,
                        ))
                    }),
                    Arguments.of("Nested", """
                        (("expression"))
                    """.trimIndent(), {
                        RhovasIr.Expression.Group(
                            RhovasIr.Expression.Group(literal("expression")),
                        )
                    }),
                )
            }

        }

        @Nested
        inner class UnaryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testUnary(name: String, input: String, expected: (() -> RhovasIr.Expression.Unary?)?) {
                test("expression", input, expected?.invoke())
            }

            fun testUnary(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Boolean Negation", """
                        !true
                    """.trimIndent(), {
                        RhovasIr.Expression.Unary("!",
                            literal(true),
                            Type.BOOLEAN.methods["!", listOf()]!!,
                        )
                    }),
                    Arguments.of("Integer Negation", """
                        -1
                    """.trimIndent(), {
                        RhovasIr.Expression.Unary("-",
                            literal(BigInteger.parseString("1")),
                            Type.INTEGER.methods["-", listOf()]!!,
                        )
                    }),
                    Arguments.of("Invalid", "-true", null),
                )
            }

        }

        @Nested
        inner class BinaryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLogicalOr(name: String, input: String, expected: (() -> RhovasIr.Expression.Binary?)?) {
                test("expression", input, expected?.invoke())
            }

            fun testLogicalOr(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", """
                        false || true
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("||",
                            literal(false),
                            literal(true),
                            null,
                            Type.BOOLEAN,
                        )
                    }),
                    Arguments.of("False", """
                        false || false
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("||",
                            literal(false),
                            literal(false),
                            null,
                            Type.BOOLEAN,
                        )
                    }),
                    Arguments.of("Invalid Left", """
                        1 || true
                    """.trimIndent(), null),
                    Arguments.of("Invalid Right", """
                        false || 2
                    """.trimIndent(), null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLogicalAnd(name: String, input: String, expected: (() -> RhovasIr.Expression.Binary?)?) {
                test("expression", input, expected?.invoke())
            }

            fun testLogicalAnd(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", """
                        true && true
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("&&",
                            literal(true),
                            literal(true),
                            null,
                            Type.BOOLEAN,
                        )
                    }),
                    Arguments.of("False", """
                        true && false
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("&&",
                            literal(true),
                            literal(false),
                            null,
                            Type.BOOLEAN,
                        )
                    }),
                    Arguments.of("Invalid Left", """
                        1 && false
                    """.trimIndent(), null),
                    Arguments.of("Invalid Right", """
                        true && 2
                    """.trimIndent(), null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testEquality(name: String, input: String, expected: (() -> RhovasIr.Expression.Binary?)?) {
                test("expression", input, expected?.invoke()) {
                    it.variables.define(variable("any", Type.ANY).variable)
                }
            }

            fun testEquality(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Equatable", """
                        1 == 2
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("==",
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            Type.INTEGER.methods["==", listOf(Type.INTEGER)],
                            Type.BOOLEAN,
                        )
                    }),
                    Arguments.of("Maybe Equatable", """
                        1 == any
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("==",
                            literal(BigInteger.parseString("1")),
                            variable("any", Type.ANY),
                            Type.INTEGER.methods["==", listOf(Type.INTEGER)],
                            Type.BOOLEAN,
                        )
                    }),
                    Arguments.of("Not Equatable", """
                        1 != 2.0
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("!=",
                            literal(BigInteger.parseString("1")),
                            literal(BigDecimal.parseString("2.0")),
                            Type.INTEGER.methods["==", listOf(Type.INTEGER)],
                            Type.BOOLEAN,
                        )
                    }),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIdentity(name: String, input: String, expected: (() -> RhovasIr.Expression.Binary?)?) {
                test("expression", input, expected?.invoke()) {
                    it.variables.define(variable("any", Type.ANY).variable)
                }
            }

            fun testIdentity(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Equatable", """
                        1 === 2
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("===",
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            null,
                            Type.BOOLEAN,
                        )
                    }),
                    Arguments.of("Maybe Equatable", """
                        1 === any
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("===",
                            literal(BigInteger.parseString("1")),
                            variable("any", Type.ANY),
                            null,
                            Type.BOOLEAN,
                        )
                    }),
                    Arguments.of("Not Equatable", """
                        1 !== 2.0
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("!==",
                            literal(BigInteger.parseString("1")),
                            literal(BigDecimal.parseString("2.0")),
                            null,
                            Type.BOOLEAN,
                        )
                    }),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testComparison(name: String, input: String, expected: (() -> RhovasIr.Expression.Binary?)?) {
                test("expression", input, expected?.invoke())
            }

            fun testComparison(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Less Than", """
                        1 < 2
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("<",
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            Type.INTEGER.methods["<=>", listOf(Type.INTEGER)]!!,
                            Type.BOOLEAN,
                        )
                    }),
                    Arguments.of("Greater Than Or Equal", """
                        1.0 >= 2.0
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary(">=",
                            literal(BigDecimal.parseString("1.0")),
                            literal(BigDecimal.parseString("2.0")),
                            Type.DECIMAL.methods["<=>", listOf(Type.DECIMAL)],
                            Type.BOOLEAN,
                        )
                    }),
                    Arguments.of("Invalid Left", """
                        false <= 2
                    """.trimIndent(), null),
                    Arguments.of("Invalid Right", """
                        1 > true
                    """.trimIndent(), null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testArithmetic(name: String, input: String, expected: (() -> RhovasIr.Expression.Binary?)?) {
                test("expression", input, expected?.invoke())
            }

            fun testArithmetic(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Integer Add", """
                        1 + 2
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("+",
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            Type.INTEGER.methods["+", listOf(Type.INTEGER)],
                            Type.INTEGER,
                        )
                    }),
                    Arguments.of("Decimal Subtract", """
                        1.0 - 2.0
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("-",
                            literal(BigDecimal.parseString("1.0")),
                            literal(BigDecimal.parseString("2.0")),
                            Type.DECIMAL.methods["-", listOf(Type.DECIMAL)],
                            Type.DECIMAL,
                        )
                    }),
                    Arguments.of("Integer Multiply", """
                        1 * 2
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("*",
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            Type.INTEGER.methods["*", listOf(Type.INTEGER)],
                            Type.INTEGER,
                        )
                    }),
                    Arguments.of("Decimal Divide", """
                        1.0 / 2.0
                    """.trimIndent(), {
                        RhovasIr.Expression.Binary("/",
                            literal(BigDecimal.parseString("1.0")),
                            literal(BigDecimal.parseString("2.0")),
                            Type.DECIMAL.methods["/", listOf(Type.DECIMAL)],
                            Type.DECIMAL,
                        )
                    }),
                    Arguments.of("Invalid Left", """
                        false + 2
                    """.trimIndent(), null),
                    Arguments.of("Invalid Right", """
                        1 + true
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class AccessTests {

            @Nested
            inner class VariableTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testVariable(name: String, input: String, expected: (() -> RhovasIr.Expression.Access.Variable?)?) {
                    test("expression", input, expected?.invoke()) {
                        it.variables.define(variable("variable", Type.ANY).variable)
                    }
                }

                fun testVariable(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Variable", """
                            variable
                        """.trimIndent(), {
                            RhovasIr.Expression.Access.Variable(null, Variable.Declaration("variable", Type.ANY, false))
                        }),
                        Arguments.of("Undefined", """
                            undefined
                        """.trimIndent(), null),
                        Arguments.of("Uninitialized", """
                            do { val x: Integer; x }
                        """.trimIndent(), null),
                    )
                }

            }

            @Nested
            inner class PropertyTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testProperty(name: String, input: String, expected: (() -> RhovasIr.Expression.Access.Property?)?) {
                    test("expression", input, expected?.invoke())
                }

                fun testProperty(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Property", """
                            "string".size
                        """.trimIndent(), {
                            RhovasIr.Expression.Access.Property(
                                literal("string"),
                                Type.STRING.properties["size"]!!,
                                false,
                                Type.INTEGER,
                            )
                        }),
                        Arguments.of("Coalesce", """
                            Nullable("string")?.size
                        """.trimIndent(), {
                            RhovasIr.Expression.Access.Property(
                                RhovasIr.Expression.Invoke.Constructor(
                                    Type.NULLABLE.ANY.base.reference,
                                    Type.NULLABLE.ANY.functions["", listOf(Type.STRING)]!!,
                                    listOf(literal("string")),
                                ),
                                Type.STRING.properties["size"]!!,
                                true,
                                Type.NULLABLE[Type.INTEGER],
                            )
                        }),
                        Arguments.of("Undefined", """
                            string.undefined
                        """.trimIndent(), null)
                    )
                }

            }

            @Nested
            inner class IndexTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testIndex(name: String, input: String, expected: (() -> RhovasIr.Expression.Access.Index?)?) {
                    test("expression", input, expected?.invoke()) {
                        it.variables.define(variable("list", Type.LIST[Type.ANY]).variable)
                        it.variables.define(variable("any", Type.ANY).variable)
                    }
                }

                fun testIndex(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Index", """
                            list[0]
                        """.trimIndent(), {
                            RhovasIr.Expression.Access.Index(
                                variable("list", Type.LIST[Type.ANY]),
                                Type.LIST[Type.ANY].methods["[]", listOf(Type.INTEGER)]!!,
                                listOf(literal(BigInteger.parseString("0"))),
                            )
                        }),
                        Arguments.of("Invalid Arity", """
                            list[]
                        """.trimIndent(), null),
                        Arguments.of("Invalid Argument", """
                            list[:key]
                        """.trimIndent(), null),
                        Arguments.of("Undefined", """
                            any[0]
                        """.trimIndent(), null),
                    )
                }

            }

        }

        @Nested
        inner class InvokeTests {

            @Nested
            inner class ConstructorTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testConstructor(name: String, input: String, expected: (() -> RhovasIr.Expression.Invoke.Constructor?)?) {
                    test("expression", input, expected?.invoke())
                }

                fun testConstructor(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Function", """
                            Nullable("argument")
                        """.trimIndent(), {
                            RhovasIr.Expression.Invoke.Constructor(
                                Type.NULLABLE.ANY.base.reference,
                                Type.NULLABLE.ANY.functions["", listOf(Type.STRING)]!!,
                                listOf(literal("argument")),
                            )
                        }),
                        Arguments.of("Invalid Arity", """
                            Nullable()
                        """.trimIndent(), null),
                        Arguments.of("Undefined", """
                            Undefined()
                        """.trimIndent(), null),
                    )
                }

            }

            @Nested
            inner class FunctionTests {

                val FUNCTION = Function.Declaration("function", listOf(), listOf(Variable.Declaration("argument", Type.STRING, false)), Type.VOID, listOf())

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testFunction(name: String, input: String, expected: (() -> RhovasIr.Expression.Invoke.Function?)?) {
                    test("expression", input, expected?.invoke()) {
                        it.functions.define(FUNCTION)
                    }
                }

                fun testFunction(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Function", """
                            function("argument")
                        """.trimIndent(), {
                            RhovasIr.Expression.Invoke.Function(null, FUNCTION, listOf(literal("argument")))
                        }),
                        Arguments.of("Invalid Arity", """
                            function()
                        """.trimIndent(), null),
                        Arguments.of("Undefined", """
                            undefined()
                        """.trimIndent(), null),
                    )
                }

            }

            @Nested
            inner class MethodTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testMethod(name: String, input: String, expected: (() -> RhovasIr.Expression.Invoke.Method?)?) {
                    test("expression", input, expected?.invoke())
                }

                fun testMethod(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Method", """
                            "string".contains("")
                        """.trimIndent(), {
                            RhovasIr.Expression.Invoke.Method(
                                literal("string"),
                                Type.STRING.methods["contains", listOf(Type.STRING)]!!,
                                false,
                                false,
                                listOf(literal("")),
                                Type.BOOLEAN,
                            )
                        }),
                        Arguments.of("Coalesce", """
                            Nullable("string")?.contains("")
                        """.trimIndent(), {
                            RhovasIr.Expression.Invoke.Method(
                                RhovasIr.Expression.Invoke.Constructor(
                                    Type.NULLABLE.ANY.base.reference,
                                    Type.NULLABLE.ANY.functions["", listOf(Type.STRING)]!! as Function.Definition,
                                    listOf(literal("string")),
                                ),
                                Type.STRING.methods["contains", listOf(Type.STRING)]!!,
                                true,
                                false,
                                listOf(literal("")),
                                Type.NULLABLE[Type.BOOLEAN],
                            )
                        }),
                        Arguments.of("Cascade", """
                            "string"..contains("")
                        """.trimIndent(), {
                            RhovasIr.Expression.Invoke.Method(
                                literal("string"),
                                Type.STRING.methods["contains", listOf(Type.STRING)]!!,
                                false,
                                true,
                                listOf(literal("")),
                                Type.STRING,
                            )
                        }),
                        Arguments.of("Invalid Arity", """
                            "string".contains()
                        """.trimIndent(), null),
                        Arguments.of("Invalid Argument", """
                            "string".contains(0)
                        """.trimIndent(), null),
                        Arguments.of("Undefined", """
                            "string".undefined()
                        """.trimIndent(), null),
                    )
                }

            }

            @Nested
            inner class PipelineTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testPipeline(name: String, input: String, expected: (() -> RhovasIr.Expression.Invoke.Pipeline?)?) {
                    test("expression", input, expected?.invoke()) {
                        it.variables.define(variable("nullable", Type.NULLABLE[Type.INTEGER]).variable)
                    }
                }

                fun testPipeline(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Pipeline", """
                            1.|range(2, :incl)
                        """.trimIndent(), {
                            RhovasIr.Expression.Invoke.Pipeline(
                                literal(BigInteger.parseString("1")),
                                null,
                                Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                                false,
                                false,
                                listOf(literal(BigInteger.parseString("2")), literal(RhovasAst.Atom("incl"))),
                                Type.LIST[Type.INTEGER],
                            )
                        }),
                        Arguments.of("Qualified", """
                            1.|Kernel.range(2, :incl)
                        """.trimIndent(), {
                            RhovasIr.Expression.Invoke.Pipeline(
                                literal(BigInteger.parseString("1")),
                                Library.type("Kernel"),
                                Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                                false,
                                false,
                                listOf(literal(BigInteger.parseString("2")), literal(RhovasAst.Atom("incl"))),
                                Type.LIST[Type.INTEGER],
                            )
                        }),
                        Arguments.of("Coalesce", """
                            Nullable(1)?.|range(2, :incl)
                        """.trimIndent(), {
                            RhovasIr.Expression.Invoke.Pipeline(
                                RhovasIr.Expression.Invoke.Constructor(
                                    Type.NULLABLE.ANY.base.reference,
                                    Type.NULLABLE.ANY.functions["", listOf(Type.INTEGER)]!! as Function.Definition,
                                    listOf(literal(BigInteger.parseString("1"))),
                                ),
                                null,
                                Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                                true,
                                false,
                                listOf(literal(BigInteger.parseString("2")), literal(RhovasAst.Atom("incl"))),
                                Type.NULLABLE[Type.LIST[Type.INTEGER]],
                            )
                        }),
                        Arguments.of("Cascade", """
                            1..|range(2, :incl)
                        """.trimIndent(), {
                            RhovasIr.Expression.Invoke.Pipeline(
                                literal(BigInteger.parseString("1")),
                                null,
                                Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                                false,
                                true,
                                listOf(literal(BigInteger.parseString("2")), literal(RhovasAst.Atom("incl"))),
                                Type.INTEGER,
                            )
                        }),
                        Arguments.of("Invalid Arity", """
                            1.|range()
                        """.trimIndent(), null),
                        Arguments.of("Invalid Argument", """
                            1.|range(2, "incl")
                        """.trimIndent(), null),
                        Arguments.of("Undefined", """
                            1.|undefined()
                        """.trimIndent(), null),
                    )
                }

            }

        }

        @Nested
        inner class LambdaTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLambda(name: String, input: String, expected: (() -> RhovasIr.Expression.Lambda?)?) {
                val expected = expected?.invoke()?.let {
                    RhovasIr.Expression.Invoke.Function(
                        null,
                        Library.SCOPE.functions["lambda", listOf(it.type)]!!,
                        listOf(it)
                    )
                }
                test("expression", input, expected)
            }

            fun testLambda(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Lambda", """
                        lambda { stmt(); }
                    """.trimIndent(), {
                        RhovasIr.Expression.Lambda(
                            listOf(),
                            RhovasIr.Expression.Block(listOf(stmt()), null, Type.VOID),
                            Type.LAMBDA[Type.DYNAMIC, Type.DYNAMIC, Type.DYNAMIC],
                        )
                    }),
                    Arguments.of("Expression", """
                        lambda { 1 }
                    """.trimIndent(), {
                        RhovasIr.Expression.Lambda(
                            listOf(),
                            RhovasIr.Expression.Block(listOf(), literal(BigInteger.parseString("1")), Type.INTEGER),
                            Type.LAMBDA[Type.DYNAMIC, Type.DYNAMIC, Type.DYNAMIC],
                        )
                    }),
                    Arguments.of("Parameter", """
                        lambda |x| { x }
                    """.trimIndent(), {
                        RhovasIr.Expression.Lambda(
                            listOf(Variable.Declaration("x", Type.DYNAMIC, false)),
                            RhovasIr.Expression.Block(listOf(), variable("x", Type.DYNAMIC), Type.DYNAMIC),
                            Type.LAMBDA[Type.TUPLE[Type.Tuple(listOf(Variable.Declaration("x", Type.DYNAMIC, false)))], Type.DYNAMIC, Type.DYNAMIC],
                        )
                    }),
                )
            }

        }

        @Nested
        inner class DslTests {

            val DSL = Function.Definition(Function.Declaration("dsl",
                listOf(),
                listOf(
                    Variable.Declaration("literals", Type.LIST[Type.STRING], false),
                    Variable.Declaration("arguments", Type.LIST[Type.DYNAMIC], false),
                ),
                Type.DYNAMIC,
                listOf(),
            ))

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testDsl(name: String, input: String, expected: (() -> RhovasIr.Expression?)?) {
                test("expression", input, expected?.invoke()) {
                    it.functions.define(DSL)
                }
            }

            fun testDsl(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("DSL", """
                        #dsl {
                            literal
                        }
                    """.trimIndent(), {
                        RhovasIr.Expression.Invoke.Function(null, DSL, listOf(
                            RhovasIr.Expression.Literal.List(listOf(literal("literal")), Type.LIST[Type.STRING]),
                            RhovasIr.Expression.Literal.List(listOf(), Type.LIST[Type.DYNAMIC]),
                        ))
                    }),
                    Arguments.of("Argument", """
                        #dsl {
                            argument = ${'$'}{"argument"}
                        }
                    """.trimIndent(), {
                        RhovasIr.Expression.Invoke.Function(null, DSL, listOf(
                            RhovasIr.Expression.Literal.List(listOf(literal("argument = "), literal("")), Type.LIST[Type.STRING]),
                            RhovasIr.Expression.Literal.List(listOf(literal("argument")), Type.LIST[Type.DYNAMIC]),
                        ))
                    }),
                    Arguments.of("Undefined DSL", """
                        #undefined {}
                    """.trimIndent(), null)
                )
            }

        }

    }

    @Nested
    inner class PatternTests {

        @Nested
        inner class VariableTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testVariable(name: String, input: String, expected: (() -> RhovasIr.Statement.Match?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testVariable(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Variable", """
                        match (1) {
                            else name: stmt(name);
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            literal(BigInteger.parseString("1")),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.Variable(Variable.Declaration("name", Type.INTEGER, false)),
                                stmt(variable("name", Type.INTEGER))
                            )
                        )
                    }),
                    Arguments.of("Underscore", """
                        match (1) {
                            else _: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            literal(BigInteger.parseString("1")),
                            listOf(),
                            Pair(RhovasIr.Pattern.Variable(null), stmt())
                        )
                    }),
                    Arguments.of("Redefinition", """
                        match ([1, 2, 3]) {
                            [x, y, x]: stmt(x);
                            else: stmt();
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class ValueTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testValue(name: String, input: String, expected: (() -> RhovasIr.Statement.Match?)?) {
                test("statement", input, expected?.invoke()) {
                    it.variables.define(variable("any", Type.ANY).variable)
                }
            }

            fun testValue(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Boolean", """
                        match (true) {
                            else true: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            literal(true),
                            listOf(),
                            Pair(RhovasIr.Pattern.Value(literal(true)), stmt())
                        )
                    }),
                    Arguments.of("Integer", """
                        match (1) {
                            else 1: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            literal(BigInteger.parseString("1")),
                            listOf(),
                            Pair(RhovasIr.Pattern.Value(literal(BigInteger.parseString("1"))), stmt())
                        )
                    }),
                    Arguments.of("Decimal", """
                        match (1.0) {
                            else 1.0: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            literal(BigDecimal.parseString("1.0")),
                            listOf(),
                            Pair(RhovasIr.Pattern.Value(literal(BigDecimal.parseString("1.0"))), stmt())
                        )
                    }),
                    Arguments.of("String", """
                        match ("string") {
                            else "string": stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            literal("string"),
                            listOf(),
                            Pair(RhovasIr.Pattern.Value(literal("string")), stmt())
                        )
                    }),
                    Arguments.of("Atom", """
                        match (:atom) {
                            else :atom: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            literal(RhovasAst.Atom("atom")),
                            listOf(),
                            Pair(RhovasIr.Pattern.Value(literal(RhovasAst.Atom("atom"))), stmt())
                        )
                    }),
                    Arguments.of("Supertype Argument", """
                        match (any) {
                            else 1: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            variable("any", Type.ANY),
                            listOf(),
                            Pair(RhovasIr.Pattern.Value(literal(BigInteger.parseString("1"))), stmt())
                        )
                    }),
                    Arguments.of("Unmatchable Argument", """
                        match (1.0) {
                            else 1: stmt();
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class PredicateTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testPredicate(name: String, input: String, expected: (() -> RhovasIr.Statement.Match?)?) {
                test("statement", input, expected?.invoke())
            }

            fun testPredicate(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Predicate", """
                        match (1) {
                            else _ ${'$'}{val > 0}: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            literal(BigInteger.parseString("1")),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.Predicate(
                                    RhovasIr.Pattern.Variable(null),
                                    RhovasIr.Expression.Binary(">",
                                        variable("val", Type.INTEGER),
                                        literal(BigInteger.parseString("0")),
                                        Type.INTEGER.methods["<=>", listOf(Type.INTEGER)]!!,
                                        Type.BOOLEAN,
                                    ),
                                ),
                                stmt(),
                            ),
                        )
                    }),
                    Arguments.of("Variable", """
                        match (1) {
                            else name ${'$'}{name > 0}: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            literal(BigInteger.parseString("1")),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.Predicate(
                                    RhovasIr.Pattern.Variable(Variable.Declaration("name", Type.INTEGER, false)),
                                    RhovasIr.Expression.Binary(">",
                                        variable("name", Type.INTEGER),
                                        literal(BigInteger.parseString("0")),
                                        Type.INTEGER.methods["<=>", listOf(Type.INTEGER)]!!,
                                        Type.BOOLEAN,
                                    ),
                                ),
                                stmt(),
                            ),
                        )
                    }),
                    Arguments.of("Implicit Value", """
                        match (1) {
                            else _ ${'$'}{val > 0}: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            literal(BigInteger.parseString("1")),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.Predicate(
                                    RhovasIr.Pattern.Variable(null),
                                    RhovasIr.Expression.Binary(">",
                                        variable("val", Type.INTEGER),
                                        literal(BigInteger.parseString("0")),
                                        Type.INTEGER.methods["<=>", listOf(Type.INTEGER)]!!,
                                        Type.BOOLEAN,
                                    ),
                                ),
                                stmt(),
                            ),
                        )
                    }),
                    Arguments.of("Invalid Type", """
                        match (1) {
                            else _ ${'$'}{1}: stmt();
                        }
                    """.trimIndent(), null),
                    Arguments.of("Variable Scope", """
                        match (range(1, 2, :incl)) {
                            else [x, y ${'$'}{x != y}]: stmt();
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class OrderedDestructureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testOrderedDestructure(name: String, input: String, expected: (() -> RhovasIr.Statement.Match?)?) {
                test("statement", input, expected?.invoke()) {
                    it.variables.define(variable("any", Type.ANY).variable)
                }
            }

            fun testOrderedDestructure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Variable", """
                        match (range(1, 1, :incl)) {
                            else [elem]: stmt(elem);
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            RhovasIr.Expression.Invoke.Function(
                                null,
                                Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                                listOf(literal(BigInteger.parseString("1")), literal(BigInteger.parseString("1")), literal(RhovasAst.Atom("incl"))),
                            ),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.OrderedDestructure(
                                    listOf(RhovasIr.Pattern.Variable(Variable.Declaration("elem", Type.INTEGER, false))),
                                    Type.LIST[Type.INTEGER],
                                ),
                                stmt(variable("elem", Type.INTEGER)),
                            ),
                        )
                    }),
                    Arguments.of("Multiple", """
                        match (range(1, 3, :incl)) {
                            else [1, 2, 3]: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            RhovasIr.Expression.Invoke.Function(
                                null,
                                Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                                listOf(literal(BigInteger.parseString("1")), literal(BigInteger.parseString("3")), literal(RhovasAst.Atom("incl"))),
                            ),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.OrderedDestructure(
                                    listOf(
                                        RhovasIr.Pattern.Value(literal(BigInteger.parseString("1"))),
                                        RhovasIr.Pattern.Value(literal(BigInteger.parseString("2"))),
                                        RhovasIr.Pattern.Value(literal(BigInteger.parseString("3"))),
                                    ),
                                    Type.LIST[Type.INTEGER],
                                ),
                                stmt(),
                            ),
                        )
                    }),
                    Arguments.of("Varargs", """
                        match (range(1, 3, :incl)) {
                            else [*]: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            RhovasIr.Expression.Invoke.Function(
                                null,
                                Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                                listOf(literal(BigInteger.parseString("1")), literal(BigInteger.parseString("3")), literal(RhovasAst.Atom("incl"))),
                            ),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.OrderedDestructure(
                                    listOf(RhovasIr.Pattern.VarargDestructure(null, "*", Type.LIST[Type.INTEGER])),
                                    Type.LIST[Type.INTEGER],
                                ),
                                stmt(),
                            ),
                        )
                    }),
                    Arguments.of("Varargs Pattern", """
                        match (range(1, 3, :incl)) {
                            else [elements*]: stmt(elements[0]);
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            RhovasIr.Expression.Invoke.Function(
                                null,
                                Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                                listOf(literal(BigInteger.parseString("1")), literal(BigInteger.parseString("3")), literal(RhovasAst.Atom("incl"))),
                            ),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.OrderedDestructure(
                                    listOf(RhovasIr.Pattern.VarargDestructure(
                                        RhovasIr.Pattern.Variable(Variable.Declaration("elements", Type.INTEGER, false)),
                                        "*",
                                        Type.LIST[Type.INTEGER],
                                    )),
                                    Type.LIST[Type.INTEGER],
                                ),
                                stmt(RhovasIr.Expression.Access.Index(
                                    variable("elements", Type.LIST[Type.INTEGER]),
                                    Type.LIST[Type.INTEGER].methods["[]", listOf(Type.INTEGER)]!!,
                                    listOf(literal(BigInteger.parseString("0"))),
                                )),
                            ),
                        )
                    }),
                    Arguments.of("Supertype Argument", """
                        match (any) {
                            else []: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            variable("any", Type.ANY),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.OrderedDestructure(listOf(), Type.LIST[Type.DYNAMIC]),
                                stmt(),
                            ),
                        )
                    }),
                    Arguments.of("Unmatchable Type", """
                        match (1) {
                            else []: stmt();
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class NamedDestructureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testNamedDestructure(name: String, input: String, expected: (() -> RhovasIr.Statement.Match?)?) {
                test("statement", input, expected?.invoke()) {
                    it.variables.define(variable("any", Type.ANY).variable)
                }
            }

            fun testNamedDestructure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Key", """
                        match ({key: 1}) {
                            else {key}: stmt(key);
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            RhovasIr.Expression.Literal.Object(mapOf(
                                "key" to literal(BigInteger.parseString("1")),
                            ), Type.OBJECT),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.NamedDestructure(
                                    listOf("key" to RhovasIr.Pattern.Variable(Variable.Declaration("key", Type.DYNAMIC, false))),
                                    Type.OBJECT,
                                ),
                                stmt(variable("key", Type.DYNAMIC)),
                            ),
                        )
                    }),
                    Arguments.of("Value", """
                        match ({key: 1}) {
                            else {key: 1}: stmt(key);
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            RhovasIr.Expression.Literal.Object(mapOf(
                                "key" to literal(BigInteger.parseString("1")),
                            ), Type.OBJECT),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.NamedDestructure(
                                    listOf("key" to RhovasIr.Pattern.Value(literal(BigInteger.parseString("1")))),
                                    Type.OBJECT,
                                ),
                                stmt(variable("key", Type.INTEGER)),
                            ),
                        )
                    }),
                    Arguments.of("Multiple", """
                        match ({x: 1, y: 2, z: 3}) {
                            else {x, y, z}: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            RhovasIr.Expression.Literal.Object(mapOf(
                                "x" to literal(BigInteger.parseString("1")),
                                "y" to literal(BigInteger.parseString("2")),
                                "z" to literal(BigInteger.parseString("3")),
                            ), Type.OBJECT),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.NamedDestructure(listOf(
                                    "x" to RhovasIr.Pattern.Variable(Variable.Declaration("x", Type.DYNAMIC, false)),
                                    "y" to RhovasIr.Pattern.Variable(Variable.Declaration("y", Type.DYNAMIC, false)),
                                    "z" to RhovasIr.Pattern.Variable(Variable.Declaration("z", Type.DYNAMIC, false)),
                                ), Type.OBJECT),
                                stmt(),
                            ),
                        )
                    }),
                    Arguments.of("Varargs", """
                        match ({key: 1}) {
                            else {*}: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            RhovasIr.Expression.Literal.Object(mapOf(
                                "key" to literal(BigInteger.parseString("1")),
                            ), Type.OBJECT),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.NamedDestructure(
                                    listOf(null to RhovasIr.Pattern.VarargDestructure(null, "*", Type.OBJECT)),
                                    Type.OBJECT,
                                ),
                                stmt(),
                            ),
                        )
                    }),
                    Arguments.of("Varargs Pattern", """
                        match ({key: 1}) {
                            else {object*}: stmt(object[:key]);
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            RhovasIr.Expression.Literal.Object(mapOf(
                                "key" to literal(BigInteger.parseString("1")),
                            ), Type.OBJECT),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.NamedDestructure(
                                    listOf(null to RhovasIr.Pattern.VarargDestructure(
                                        RhovasIr.Pattern.Variable(Variable.Declaration("object", Type.DYNAMIC, false)),
                                        "*",
                                        Type.OBJECT,
                                    )),
                                    Type.OBJECT,
                                ),
                                stmt(RhovasIr.Expression.Access.Index(
                                    variable("object", Type.OBJECT),
                                    Type.OBJECT.methods["[]", listOf(Type.ATOM)]!!,
                                    listOf(literal(RhovasAst.Atom("key"))),
                                )),
                            ),
                        )
                    }),
                    Arguments.of("Supertype Argument", """
                        match (any) {
                            else {}: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            variable("any", Type.ANY),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.NamedDestructure(listOf(), Type.OBJECT),
                                stmt(),
                            ),
                        )
                    }),
                    Arguments.of("Missing Key", """
                        match (1) {
                            else {:pattern}: stmt();
                        }
                    """.trimIndent(), null),
                    Arguments.of("Unmatchable Type", """
                        match (1) {
                            else {}: stmt();
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class TypedDestructureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testTypedDestructure(name: String, input: String, expected: (() -> RhovasIr.Statement.Match?)?) {
                test("statement", input, expected?.invoke()) {
                    it.variables.define(variable("any", Type.ANY).variable)
                }
            }

            fun testTypedDestructure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Type", """
                        match (1) {
                            else Integer: stmt();
                        }
                    """, {
                        RhovasIr.Statement.Match.Structural(
                            literal(BigInteger.parseString("1")),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.TypedDestructure(Type.INTEGER, null),
                                stmt(),
                            ),
                        )
                    }),
                    Arguments.of("Pattern", """
                        match (1) {
                            else Integer name: stmt(name);
                        }
                    """, {
                        RhovasIr.Statement.Match.Structural(
                            literal(BigInteger.parseString("1")),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.TypedDestructure(
                                    Type.INTEGER,
                                    RhovasIr.Pattern.Variable(Variable.Declaration("name", Type.INTEGER, false)),
                                ),
                                stmt(variable("name", Type.INTEGER)),
                            ),
                        )
                    }),
                    Arguments.of("Supertype Argument", """
                        match (any) {
                            else Integer: stmt();
                        }
                    """.trimIndent(), {
                        RhovasIr.Statement.Match.Structural(
                            variable("any", Type.ANY),
                            listOf(),
                            Pair(
                                RhovasIr.Pattern.TypedDestructure(Type.INTEGER, null),
                                stmt(),
                            ),
                        )
                    }),
                    Arguments.of("Unmatchable Type", """
                        match (1.0) {
                            else Integer: stmt();
                        }
                    """.trimIndent(), null),
                )
            }

        }

    }

    @Nested
    inner class TypeTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testType(name: String, input: String, expected: (() -> RhovasIr.Type?)?) {
            test("type", input, expected?.invoke()) {
                it.types.define(Type.Generic("T", Type.ANY), "T")
            }
        }

        fun testType(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Type", """
                    Any
                """.trimIndent(), {
                    RhovasIr.Type(Type.ANY)
                }),
                Arguments.of("Generic", """
                    T
                """.trimIndent(), {
                    RhovasIr.Type(Type.Generic("T", Type.ANY))
                }),
                Arguments.of("Undefined", """
                    Undefined
                """.trimIndent(), null),
            )
        }

    }

    private fun block(vararg statements: RhovasIr.Statement): RhovasIr.Expression.Block {
        return RhovasIr.Expression.Block(statements.toList(), null, Type.VOID)
    }

    private fun stmt(position: Int): RhovasIr.Statement {
        return stmt(literal(BigInteger.fromInt(position)))
    }

    private fun stmt(argument: RhovasIr.Expression? = null): RhovasIr.Statement {
        return RhovasIr.Statement.Expression(when (argument) {
            null -> RhovasIr.Expression.Invoke.Function(null, STMT_0, listOf())
            else -> RhovasIr.Expression.Invoke.Function(null, STMT_1, listOf(argument))
        })
    }

    private fun literal(value: Any?): RhovasIr.Expression.Literal {
        return when (value) {
            null -> RhovasIr.Expression.Literal.Scalar(null, Type.NULLABLE.ANY)
            is Boolean -> RhovasIr.Expression.Literal.Scalar(value, Type.BOOLEAN)
            is BigInteger -> RhovasIr.Expression.Literal.Scalar(value, Type.INTEGER)
            is BigDecimal -> RhovasIr.Expression.Literal.Scalar(value, Type.DECIMAL)
            is RhovasAst.Atom -> RhovasIr.Expression.Literal.Scalar(value, Type.ATOM)
            is String -> RhovasIr.Expression.Literal.String(listOf(value), listOf(), Type.STRING)
            else -> throw AssertionError(value.javaClass)
        }
    }

    private fun variable(name: String, type: Type): RhovasIr.Expression.Access.Variable {
        return RhovasIr.Expression.Access.Variable(null, Variable.Declaration(name, type, false))
    }

    private fun test(rule: String, input: String, expected: RhovasIr?, scope: (Scope.Declaration) -> Unit = {}) {
        val input = Input("Test", input)
        val scope = Scope.Declaration(Library.SCOPE).also {
            scope.invoke(it)
            it.functions.define(STMT_0)
            it.functions.define(STMT_1)
        }
        try {
            val ast = RhovasParser(input).parse(rule)
            val ir = RhovasAnalyzer(scope).visit(ast)
            Assertions.assertEquals(expected, ir)
            Assertions.assertTrue(ast.context.isNotEmpty() || input.content.isBlank())
        } catch (e: ParseException) {
            println(input.diagnostic(e.summary, e.details, e.range, e.context))
            Assertions.fail(e)
        } catch (e: AnalyzeException) {
            if (expected != null || e.summary == "Broken analyzer invariant.") {
                println(input.diagnostic(e.summary, e.details, e.range, e.context))
                Assertions.fail<Unit>(e)
            }
        }
    }

}
