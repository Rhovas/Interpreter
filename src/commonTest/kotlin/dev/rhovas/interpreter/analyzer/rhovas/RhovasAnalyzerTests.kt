package dev.rhovas.interpreter.analyzer.rhovas

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.RhovasSpec
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class RhovasAnalyzerTests: RhovasSpec() {

    data class Test<T : RhovasIr>(val source: String, val expected: ((Scope.Declaration) -> T?)?)

    private val MODULE = Type.Base("Module", Scope.Definition(null)).reference.also(Library.SCOPE.types::define)
    private val SUBMODULE = Type.Base("Module.Type", Scope.Definition(null)).reference.also(Library.SCOPE.types::define).also { MODULE.base.scope.types.define(it, "Type") }
    private val STMT_0 = Function.Definition(Function.Declaration("stmt", listOf(), listOf(), Type.VOID, listOf())).also(Library.SCOPE.functions::define)
    private val STMT_1 = Function.Definition(Function.Declaration("stmt", listOf(), listOf(Variable.Declaration("argument", Type.ANY, false)), Type.VOID, listOf())).also(Library.SCOPE.functions::define)

    init {
        suite("Source", listOf(
            "Empty" to Test("""
                
            """.trimIndent()) {
                RhovasIr.Source(listOf(), listOf())
            },
            "Single Statement" to Test("""
                stmt();
            """.trimIndent()) {
                RhovasIr.Source(listOf(), listOf(stmt()))
            },
            "Multiple Statements" to Test("""
                stmt(1); stmt(2); stmt(3);
            """.trimIndent()) {
                RhovasIr.Source(listOf(), listOf(stmt(1), stmt(2), stmt(3)))
            },
        )) { test("source", it.source, it.expected) }

        suite("Import", listOf(
            "Import Module" to Test("""
                import Module;
                val name: Module;
            """.trimIndent()) {
                RhovasIr.Source(
                    listOf(RhovasIr.Import(MODULE)),
                    listOf(RhovasIr.Statement.Declaration.Variable(Variable.Declaration("name", MODULE, false), null))
                )
            },
            "Import Submodule" to Test("""
                import Module.Type;
                val name: Module.Type;
            """.trimIndent()) {
                RhovasIr.Source(
                    listOf(RhovasIr.Import(SUBMODULE)),
                    listOf(RhovasIr.Statement.Declaration.Variable(Variable.Declaration("name", SUBMODULE, false), null))
                )
            },
            "Import Alias" to Test("""
                import Module.Type as Alias;
                val name: Alias;
            """.trimIndent()) {
                RhovasIr.Source(
                    listOf(RhovasIr.Import(SUBMODULE)),
                    listOf(RhovasIr.Statement.Declaration.Variable(Variable.Declaration("name", SUBMODULE, false), null))
                )
            },
            "Undefined Import" to Test("""
                import Undefined;
            """.trimIndent(), null),
            "Redefined Type" to Test("""
                import Module;
                import Module;
            """.trimIndent(), null),
        )) { test("source", it.source, it.expected) }

        suite("Component") {
            suite("Struct", listOf(
                "Struct" to Test("""
                    struct Name {}
                    stmt(Name({}));
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf())])
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf())),
                        stmt(RhovasIr.Expression.Invoke.Constructor(
                            type,
                            Function.Definition(Function.Declaration("", listOf(), listOf(Variable.Declaration("fields", type.base.inherits[0], false)), type, listOf())),
                            listOf(RhovasIr.Expression.Literal.Object(mapOf(), Type.STRUCT[Type.Struct(mapOf())])),
                            type,
                        )),
                    ))
                },
                "Field" to Test("""
                    struct Name { val field: Integer; }
                    stmt(Name({field: 1}).field);
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf("field" to Variable.Declaration("field", Type.INTEGER, false)))])
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("field", listOf(), listOf(Variable.Declaration("this", type, false)), Type.INTEGER, listOf())))
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type,
                            listOf(RhovasIr.Member.Property(type.properties["field"]!!.getter.function as Function.Definition, null, null)),
                        )),
                        stmt(RhovasIr.Expression.Access.Property(
                            RhovasIr.Expression.Invoke.Constructor(
                                type,
                                Function.Definition(Function.Declaration("", listOf(), listOf(Variable.Declaration("fields", type.base.inherits[0], false)), type, listOf())),
                                listOf(RhovasIr.Expression.Literal.Object(mapOf("field" to literal(BigInteger.parseString("1"))), Type.STRUCT[Type.Struct(mapOf("field" to Variable.Declaration("field", Type.INTEGER, true)))])),
                                type,
                            ),
                            type.properties["field"]!!, false, false, Type.INTEGER,
                        )),
                    ))
                },
                "Function" to Test("""
                    struct Name { func function(): Integer { return 1; } }
                    Name.function();
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf())])
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("function", listOf(), listOf(), Type.INTEGER, listOf())))
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                            RhovasIr.Member.Method(RhovasIr.Statement.Declaration.Function(
                                type.functions["function", listOf()]!!,
                                block(RhovasIr.Statement.Return(literal(BigInteger.parseString("1")), listOf())),
                            )),
                        ))),
                        RhovasIr.Statement.Expression(RhovasIr.Expression.Invoke.Function(type, type.functions["function", listOf()]!!, false, listOf(), Type.INTEGER)),
                    ))
                },
                "Method" to Test("""
                    struct Name {
                        val field: Integer;
                        func method(this): Integer { return this.field; }
                    }
                    Name({field: 1}).method();
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf("field" to Variable.Declaration("field", Type.INTEGER, false)))])
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("field", listOf(), listOf(Variable.Declaration("this", type, false)), Type.INTEGER, listOf())))
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("method", listOf(), listOf(Variable.Declaration("this", type, false)), Type.INTEGER, listOf())))
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                            RhovasIr.Member.Property(type.properties["field"]!!.getter.function as Function.Definition, null, null),
                            RhovasIr.Member.Method(RhovasIr.Statement.Declaration.Function(
                                type.methods["method", listOf()]!!.function,
                                block(RhovasIr.Statement.Return(RhovasIr.Expression.Access.Property(variable("this", type), type.properties["field"]!!, false, false, Type.INTEGER), listOf())),
                            )),
                        ))),
                        RhovasIr.Statement.Expression(RhovasIr.Expression.Invoke.Method(
                            RhovasIr.Expression.Invoke.Constructor(
                                type,
                                Function.Definition(Function.Declaration("", listOf(), listOf(Variable.Declaration("fields", type.base.inherits[0], false)), type, listOf())),
                                listOf(RhovasIr.Expression.Literal.Object(mapOf("field" to literal(BigInteger.parseString("1"))), Type.STRUCT[Type.Struct(mapOf("field" to Variable.Declaration("field", Type.INTEGER, true)))])),
                                type,
                            ),
                            type.methods["method", listOf()]!!,
                            false, false, false, listOf(), Type.INTEGER,
                        )),
                    ))
                },
                "Redefined" to Test("""
                    struct Name {}
                    struct Name {}
                """.trimIndent(), null),
            )) { test("source", it.source, it.expected) }

            suite("Class", listOf(
                "Class" to Test("""
                    class Name {}
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.ANY)
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Class(type, listOf())),
                    ))
                },
                "Members" to Test("""
                    class Name {
                        val field: Integer;
                        init() {}
                        func function() {}
                    }
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.ANY)
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("", listOf(), listOf(), type, listOf())))
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("field", listOf(), listOf(Variable.Declaration("this", type, false)), Type.INTEGER, listOf())))
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("function", listOf(), listOf(), Type.VOID, listOf())))
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Class(type, listOf(
                            RhovasIr.Member.Property(type.properties["field"]!!.getter.function as Function.Definition, null, null),
                            RhovasIr.Member.Initializer(type.functions["", listOf()]!! as Function.Definition, block()),
                            RhovasIr.Member.Method(RhovasIr.Statement.Declaration.Function(type.functions["function", listOf()]!!, block())),
                        ))),
                    ))
                },
                "Redefined" to Test("""
                    class Name {}
                    class Name {}
                """.trimIndent(), null),
            )) { test("source", it.source, it.expected) }
        }

        suite("Member") {
            suite("Property", listOf(
                "Immutable" to Test("""
                    struct Name {
                        val field: Integer;
                    }
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf("field" to Variable.Declaration("field", Type.INTEGER, false)))])
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("field", listOf(), listOf(Variable.Declaration("this", type, false)), Type.INTEGER, listOf())))
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                            RhovasIr.Member.Property(type.properties["field"]!!.getter.function as Function.Definition, null, null),
                        ))),
                    ))
                },
                "Mutable" to Test("""
                    struct Name {
                        var field: Integer;
                    }
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf("field" to Variable.Declaration("field", Type.INTEGER, true)))])
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("field", listOf(), listOf(Variable.Declaration("this", type, false)), Type.INTEGER, listOf())))
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("field", listOf(), listOf(Variable.Declaration("this", type, false), Variable.Declaration("value", Type.INTEGER, false)), Type.VOID, listOf())))
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                            RhovasIr.Member.Property(type.properties["field"]!!.getter.function as Function.Definition, type.properties["field"]!!.setter!!.function as Function.Definition, null),
                        ))),
                    ))
                },
                "Value" to Test("""
                    struct Name {
                        val field: Integer = 1;
                    }
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf("field" to Variable.Declaration("field", Type.INTEGER, false)))])
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("field", listOf(), listOf(Variable.Declaration("this", type, false)), Type.INTEGER, listOf())))
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                            RhovasIr.Member.Property(type.properties["field"]!!.getter.function as Function.Definition, null, literal(BigInteger.parseString("1"))),
                        ))),
                    ))
                },
                "Redefined" to Test("""
                    struct Name {
                        val field: Integer;
                        val field: Integer;
                    }
                """.trimIndent(), null),
                "Invalid Value" to Test("""
                    struct Name {
                        val field: Integer = 1.0;
                    }
                """.trimIndent(), null),
            )) { test("source", it.source, it.expected) }

            suite("Initializer", listOf(
                /*
                Overlaps with default constructors.
                "Empty" to Test("""
                    struct Name {
                        init() {}
                    }
                """.trimIndent(), null),
                 */
                "Parameters" to Test("""
                    struct Name {
                        init(argument: Integer) {}
                    }
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf())])
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("", listOf(), listOf(Variable.Declaration("argument", Type.INTEGER, false)), type, listOf())))
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                            RhovasIr.Member.Initializer(type.functions["", listOf(Type.INTEGER)]!! as Function.Definition, block()),
                        ))),
                    ))
                },
                "Return Type" to Test("""
                    struct Name {
                        init(argument: Integer): Integer {
                            return argument;
                        }
                    }
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf())])
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("", listOf(), listOf(Variable.Declaration("argument", Type.INTEGER, false)), Type.INTEGER, listOf())))
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                            RhovasIr.Member.Initializer(type.functions["", listOf(Type.INTEGER)]!! as Function.Definition, block(
                                RhovasIr.Statement.Return(variable("argument", Type.INTEGER), listOf()),
                            )),
                        ))),
                    ))
                },
                "Missing Parameter Type" to Test("""
                    struct Name {
                        init(parameter) {}
                    }
                """.trimIndent(), null),
                "Redefined" to Test("""
                    struct Name {
                        init(argument: Integer) {}
                        init(argument: Integer) {}
                    }
                """.trimIndent(), null),
            )) { test("source", it.source, it.expected) }

            suite("Method", listOf(
                "Function" to Test("""
                    struct Name {
                        func function() {}
                    }
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf())])
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("function", listOf(), listOf(), Type.VOID, listOf())))
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                            RhovasIr.Member.Method(RhovasIr.Statement.Declaration.Function(type.functions["function", listOf()]!! as Function.Definition, block())),
                        ))),
                    ))
                },
                "Method" to Test("""
                    struct Name {
                        func method(this) {}
                    }
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf())])
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("method", listOf(), listOf(Variable.Declaration("this", type, false)), Type.VOID, listOf())))
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                            RhovasIr.Member.Method(RhovasIr.Statement.Declaration.Function(type.functions["method", listOf(type)]!! as Function.Definition, block())),
                        ))),
                    ))
                },
                "Operator Overload" to Test("""
                    struct Name {
                        func op+ add(this, other: Name) {
                            print(this + other);
                        }
                    }
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf())])
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("add", listOf(), listOf(Variable.Declaration("this", type, false), Variable.Declaration("other", type, false)), Type.VOID, listOf())))
                    RhovasIr.Source(listOf(), listOf(
                        RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                            RhovasIr.Member.Method(RhovasIr.Statement.Declaration.Function(type.functions["add", listOf(type, type)]!! as Function.Definition, block(
                                RhovasIr.Statement.Expression(RhovasIr.Expression.Invoke.Function(null, Library.SCOPE.functions["print", listOf(type)]!!, false, listOf(
                                    RhovasIr.Expression.Binary("+", variable("this", type), variable("other", type), type.methods["add", listOf(type)]!!, Type.VOID),
                                ), Type.VOID)),
                            ))),
                        ))),
                    ))
                },
                "Redefined" to Test("""
                    struct Name {
                        func function() {}
                        func function() {}
                    }
                """.trimIndent(), null),
            )) { test("source", it.source, it.expected) }
        }

        suite("Statement") {
            suite("Block", listOf(
                "Block" to Test("""
                    { stmt(); }
                """.trimIndent()) {
                    RhovasIr.Expression.Block(listOf(stmt()), null, Type.VOID)
                },
            )) { test("statement", it.source, it.expected?.let { e -> { e.invoke(it)?.let { RhovasIr.Statement.Expression(it) } } }) }

            suite("Component", listOf(
                "Struct" to Test("""
                    struct Name {}
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf())])
                    RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf()))
                },
            )) { test("statement", it.source, it.expected) }

            suite("Initializer", listOf(
                "Initializer" to Test("""
                    struct Name {
                        init(field: Integer) {
                            this { field };
                        }
                    }
                """.trimIndent()) {
                    val type = Type.Base("Name", Scope.Definition(null)).reference
                    type.base.inherit(Type.STRUCT[Type.Struct(mapOf())])
                    RhovasIr.Statement.Component(RhovasIr.Component.Struct(type, listOf(
                        RhovasIr.Member.Initializer(Function.Definition(Function.Declaration("", listOf(), listOf(Variable.Declaration("field", Type.INTEGER, false)), type, listOf())), block(
                            RhovasIr.Statement.Initializer(RhovasIr.Expression.Literal.Object(mapOf("field" to variable("field", Type.INTEGER)), Type.STRUCT[Type.Struct(mapOf("field" to Variable.Declaration("field", Type.INTEGER, true)))]))
                        )),
                    )))
                },
                "Invalid Initializer" to Test("""
                    func function() {
                        this { field };
                    }
                """.trimIndent(), null),
                "Reinitialization" to Test("""
                    struct Name {
                        init(field: Integer) {
                            this { field };
                            this { field };
                        }
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Expression", listOf(
                "Function" to Test("""
                    stmt();
                """.trimIndent()) {
                    RhovasIr.Statement.Expression(
                        RhovasIr.Expression.Invoke.Function(null, STMT_0, false, listOf(), Type.VOID)
                    )
                },
                "Invalid" to Test("""
                    1;
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Declaration") {
                suite("Variable", listOf(
                    "Val" to Test("""
                        val name: Integer = 1;
                        stmt(name);
                    """.trimIndent()) {
                        RhovasIr.Statement.Declaration.Variable(
                            Variable.Declaration("name", Type.INTEGER, false),
                            literal(BigInteger.parseString("1")),
                        )
                    },
                    "Var" to Test("""
                        var name: Integer = 1;
                        stmt(name);
                    """.trimIndent()) {
                        RhovasIr.Statement.Declaration.Variable(
                            Variable.Declaration("name", Type.INTEGER, true),
                            literal(BigInteger.parseString("1")),
                        )
                    },
                    "Subtype Value" to Test("""
                        val name: Dynamic = 1;
                        stmt(name);
                    """.trimIndent()) {
                        RhovasIr.Statement.Declaration.Variable(
                            Variable.Declaration("name", Type.DYNAMIC, false),
                            literal(BigInteger.parseString("1")),
                        )
                    },
                    "Redefined" to Test("""
                        val name = 1;
                        val name = 2;
                    """.trimIndent(), null),
                    "Undefined Type" to Test("""
                        val name;
                    """.trimIndent(), null),
                    "Invalid Value" to Test("""
                        val name: Integer = 1.0;
                    """.trimIndent(), null),
                    "Supertype Value" to Test("""
                        val name: Integer = any;
                    """.trimIndent(), null),
                )) { test("source", it.source, it.expected?.let { e -> { e.invoke(it)?.let { RhovasIr.Source(listOf(), listOf(it, stmt(RhovasIr.Expression.Access.Variable(null, it.variable)))) } } }) }

                suite("Function", listOf(
                    "Definition" to Test("""
                        func name() {
                            name();
                        }
                    """.trimIndent()) {
                        val func = Function.Declaration("name", listOf(), listOf(), Type.VOID, listOf())
                        RhovasIr.Statement.Declaration.Function(
                            func,
                            block(RhovasIr.Statement.Expression(RhovasIr.Expression.Invoke.Function(null, func, false, listOf(), Type.VOID))),
                        )
                    },
                    "Parameter" to Test("""
                        func name(parameter: Integer) {
                            stmt(parameter);
                        }
                    """.trimIndent()) {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("name", listOf(), listOf(Variable.Declaration("parameter", Type.INTEGER, false)), Type.VOID, listOf()),
                            block(stmt(variable("parameter", Type.INTEGER))),
                        )
                    },
                    "Return Value" to Test("""
                        func name(): Integer {
                            return 1;
                        }
                    """.trimIndent()) {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("name", listOf(), listOf(), Type.INTEGER, listOf()),
                            block(RhovasIr.Statement.Return(literal(BigInteger.parseString("1")), listOf())),
                        )
                    },
                    "If Return" to Test("""
                        func name(): Integer {
                            if (true) {
                                return 1;
                            } else {
                                return 2;
                            }
                        }
                    """.trimIndent()) {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("name", listOf(), listOf(), Type.INTEGER, listOf()),
                            block(RhovasIr.Statement.If(
                                literal(true),
                                block(RhovasIr.Statement.Return(literal(BigInteger.parseString("1")), listOf())),
                                block(RhovasIr.Statement.Return(literal(BigInteger.parseString("2")), listOf())),
                            )),
                        )
                    },
                    "Conditional Match Return" to Test("""
                        func name(): Integer {
                            match {
                                true: return 1;
                                else: return 2;
                            }
                        }
                    """.trimIndent()) {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("name", listOf(), listOf(), Type.INTEGER, listOf()),
                            block(RhovasIr.Statement.Match.Conditional(
                                listOf(literal(true) to RhovasIr.Statement.Return(literal(BigInteger.parseString("1")), listOf())),
                                null to RhovasIr.Statement.Return(literal(BigInteger.parseString("2")), listOf()),
                            )),
                        )
                    },
                    "Structural Match Return" to Test("""
                        func name(): Integer {
                            match (true) {
                                true: return 1;
                            }
                        }
                    """.trimIndent()) {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("name", listOf(), listOf(), Type.INTEGER, listOf()),
                            block(RhovasIr.Statement.Match.Structural(
                                literal(true),
                                listOf(RhovasIr.Pattern.Value(literal(true)) to RhovasIr.Statement.Return(literal(BigInteger.parseString("1")), listOf())),
                                null,
                            )),
                        )
                    },
                    "Throws" to Test("""
                        func name() throws Exception {
                            throw Exception("message");
                        }
                    """.trimIndent()) {
                        RhovasIr.Statement.Declaration.Function(
                            Function.Declaration("name", listOf(), listOf(), Type.VOID, listOf(Type.EXCEPTION)),
                            block(RhovasIr.Statement.Throw(RhovasIr.Expression.Invoke.Constructor(
                                Type.EXCEPTION,
                                Type.EXCEPTION.functions["", listOf(Type.STRING)]!! as Function.Definition,
                                listOf(literal("message")),
                                Type.EXCEPTION,
                            ))),
                        )
                    },
                    "Generic" to Test("""
                        func first<T>(list: List<T>): T {
                            return list[0];
                        }
                    """.trimIndent()) {
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
                                false,
                                listOf(literal(BigInteger.parseString("0"))),
                                Type.Generic("T", Type.ANY),
                            ), listOf())),
                        )
                    },
                    "Invalid Operator Overload" to Test("""
                        func op+ add() {}
                    """.trimIndent(), null),
                    "Missing Parameter Type" to Test("""
                        func name(parameter) {}
                    """.trimIndent(), null),
                    "Missing Return Value" to Test("""
                        func name(): Integer {
                            stmt();
                        }
                    """.trimIndent(), null),
                    "Incomplete If Return" to Test("""
                        func name(): Integer {
                            if (true) {
                                return 1;
                            }
                        }
                    """.trimIndent(), null),
                    "Incomplete Conditional Match Return" to Test("""
                        func name(): Integer {
                            match {
                                true: return 1;
                            }
                        }
                    """.trimIndent(), null),
                    "Uncaught Exception" to Test("""
                        func name() {
                            throw Exception("message");
                        }
                    """.trimIndent(), null),
                    "Uncaught Function Exception" to Test("""
                        func name() {
                            fail("message");
                        }
                    """.trimIndent(), null),
                    "Uncaught Supertype Exception" to Test("""
                        func name() {
                            try {
                                throw Exception("message");
                            } catch (val e: SubtypeException) {
                                stmt();
                            }
                        }
                    """.trimIndent(), null),
                    "Rethrown Exception" to Test("""
                        func name() {
                            try {
                                throw Exception("message");
                            } catch (val e: Exception) {
                                throw e;
                            }
                        }
                    """.trimIndent(), null),
                )) { test("statement", it.source, it.expected) }
            }

            suite("Assignment") {
                suite("Variable", listOf(
                    "Val Initialization" to Test("""
                        val variable: Integer;
                        variable = 1;
                    """.trimIndent()) {
                        RhovasIr.Source(listOf(), listOf(
                            RhovasIr.Statement.Declaration.Variable(Variable.Declaration("variable", Type.INTEGER, false), null),
                            RhovasIr.Statement.Assignment.Variable(Variable.Declaration("variable", Type.INTEGER, false), literal(BigInteger.parseString("1"))),
                        ))
                    },
                    "Var Initialization" to Test("""
                        var variable: Integer;
                        variable = 1;
                    """.trimIndent()) {
                        RhovasIr.Source(listOf(), listOf(
                            RhovasIr.Statement.Declaration.Variable(Variable.Declaration("variable", Type.INTEGER, true), null),
                            RhovasIr.Statement.Assignment.Variable(Variable.Declaration("variable", Type.INTEGER, true), literal(BigInteger.parseString("1"))),
                        ))
                    },
                    "Var Reassignment" to Test("""
                        var variable = 1;
                        variable = 2;
                    """.trimIndent()) {
                        RhovasIr.Source(listOf(), listOf(
                            RhovasIr.Statement.Declaration.Variable(Variable.Declaration("variable", Type.INTEGER, true), literal(BigInteger.parseString("1"))),
                            RhovasIr.Statement.Assignment.Variable(Variable.Declaration("variable", Type.INTEGER, true), literal(BigInteger.parseString("2"))),
                        ))
                    },
                    "Invalid Receiver" to Test("""
                        0 = 1;
                    """.trimIndent(), null),
                    "Undefined Variable" to Test("""
                        undefined = 1;
                    """.trimIndent(), null),
                    "Unassignable Variable" to Test("""
                        val unassignable = 1;
                        unassignable = 1;
                    """.trimIndent(), null),
                    "Reinitialized Variable" to Test("""
                        val variable: Integer;
                        variable = 1;
                        variable = 2;
                    """.trimIndent(), null),
                    "Partial Initialization" to Test("""
                        val name: Integer;
                        if (true) {
                            name = 1;
                        }
                    """.trimIndent(), null),
                    "Invalid Value" to Test("""
                        var variable: Integer;
                        variable = 1.0;
                    """.trimIndent(), null),
                    "Supertype Value" to Test("""
                        var variable: Integer;
                        variable = any;
                    """.trimIndent(), null),
                )) { test("source", it.source, it.expected) }

                suite("Property", listOf(
                    "Assignment" to Test("""
                        object.property = 1;
                    """.trimIndent()) {
                        RhovasIr.Statement.Assignment.Property(
                            variable("object", it.variables["object"]!!.type),
                            it.variables["object"]!!.type.properties["property"]!!,
                            literal(BigInteger.parseString("1")),
                        )
                    },
                    "Subtype Value" to Test("""
                        object.dynamic = 1;
                    """.trimIndent()) {
                        RhovasIr.Statement.Assignment.Property(
                            variable("object", it.variables["object"]!!.type),
                            it.variables["object"]!!.type.properties["dynamic"]!!,
                            literal(BigInteger.parseString("1")),
                        )
                    },
                    "Element" to Test("""
                        tuple.0 = 1;
                    """.trimIndent()) {
                        RhovasIr.Statement.Assignment.Property(
                            variable("tuple", it.variables["tuple"]!!.type),
                            it.variables["tuple"]!!.type.properties["0"]!!,
                            literal(BigInteger.parseString("1")),
                        )
                    },
                    "Invalid Receiver" to Test("""
                        object?.property = 1;
                    """.trimIndent(), null),
                    "Undefined Property" to Test("""
                        object.undefined = 1;
                    """.trimIndent(), null),
                    "Unassignable Property" to Test("""
                        object.unassignable = 1;
                    """.trimIndent(), null),
                    "Invalid Value" to Test("""
                        object.property = 1.0;
                    """.trimIndent(), null),
                    "Supertype Value" to Test("""
                        object.property = any;
                    """.trimIndent(), null),
                )) { test("statement", it.source, it.expected) {
                    it.variables.define(variable("object", Type.STRUCT[Type.Struct(mapOf(
                        "property" to Variable.Declaration("property", Type.INTEGER, true),
                        "dynamic" to Variable.Declaration("dynamic", Type.DYNAMIC, true),
                        "unassignable" to Variable.Declaration("unassignable", Type.INTEGER, false),
                    ))]).variable)
                    it.variables.define(variable("tuple", Type.TUPLE[listOf(Type.INTEGER), true]).variable)
                } }

                suite("Index", listOf(
                    "Assignment" to Test("""
                        list[0] = 1;
                    """.trimIndent()) {
                        RhovasIr.Statement.Assignment.Index(
                            variable("list", Type.LIST[Type.INTEGER]),
                            Type.LIST[Type.INTEGER].methods["[]=", listOf(Type.INTEGER, Type.INTEGER)]!!,
                            listOf(literal(BigInteger.parseString("0"))),
                            literal(BigInteger.parseString("1")),
                        )
                    },
                    "Subtype Value" to Test("""
                        dynamic[0] = 1;
                    """.trimIndent()) {
                        RhovasIr.Statement.Assignment.Index(
                            variable("dynamic", Type.LIST[Type.DYNAMIC]),
                            Type.LIST[Type.DYNAMIC].methods["[]=", listOf(Type.INTEGER, Type.DYNAMIC)]!!,
                            listOf(literal(BigInteger.parseString("0"))),
                            literal(BigInteger.parseString("1")),
                        )
                    },
                    "Undefined Method" to Test("""
                        any[0] = 1;
                    """.trimIndent(), null),
                    "Coalesce" to Test("""
                        Nullable(list)?[0] = 1;
                    """.trimIndent(), null),
                    "Invalid Arity" to Test("""
                        list[0, 1, 2] = 1;
                    """.trimIndent(), null),
                    "Invalid Argument" to Test("""
                        list[0.0] = 1;
                    """.trimIndent(), null),
                    "Invalid Value" to Test("""
                        list[0] = 1.0;
                    """.trimIndent(), null),
                    "Supertype Value" to Test("""
                        list[0] = any;
                    """.trimIndent(), null),
                )) { test("statement", it.source, it.expected) {
                    it.variables.define(variable("any", Type.ANY).variable)
                    it.variables.define(variable("list", Type.LIST[Type.INTEGER]).variable)
                    it.variables.define(variable("dynamic", Type.LIST[Type.DYNAMIC]).variable)
                } }
            }

            suite("If", listOf(
                "If" to Test("""
                    if (true) { stmt(); }
                """.trimIndent()) {
                    RhovasIr.Statement.If(
                        literal(true),
                        block(stmt()),
                        null,
                    )
                },
                "Else" to Test("""
                    if (true) { stmt(1); } else { stmt(2); }
                """.trimIndent()) {
                    RhovasIr.Statement.If(
                        literal(true),
                        block(stmt(1)),
                        block(stmt(2)),
                    )
                },
                "Invalid Condition" to Test("""
                    if (1) { stmt(); }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Match") {
                suite("Conditional", listOf(
                    "Match" to Test("""
                        match {
                            true: stmt();
                        }
                    """.trimIndent()) {
                        RhovasIr.Statement.Match.Conditional(listOf(literal(true) to stmt()), null)
                    },
                    "Else" to Test("""
                        match {
                            else: stmt();
                        }
                    """.trimIndent()) {
                        RhovasIr.Statement.Match.Conditional(listOf(), null to stmt())
                    },
                    "Else Condition" to Test("""
                        match {
                            else true: stmt();
                        }
                    """.trimIndent()) {
                        RhovasIr.Statement.Match.Conditional(listOf(), literal(true) to stmt())
                    },
                    "Invalid Condition" to Test("""
                        match {
                            1: stmt();
                        }
                    """.trimIndent(), null),
                    "Invalid Else Condition" to Test("""
                        match {
                            else 1: stmt();
                        }
                    """.trimIndent(), null),
                )) { test("statement", it.source, it.expected) }

                suite("Structural", listOf(
                    "Match" to Test("""
                        match (true) {
                            true: stmt();
                        }
                    """.trimIndent()) {
                        RhovasIr.Statement.Match.Structural(literal(true), listOf(RhovasIr.Pattern.Value(literal(true)) to stmt()), null)
                    },
                    "Else" to Test("""
                        match (true) {
                            else: stmt();
                        }
                    """.trimIndent()) {
                        RhovasIr.Statement.Match.Structural(literal(true), listOf(), null to stmt())
                    },
                    "Else Pattern" to Test("""
                        match (true) {
                            else true: stmt();
                        }
                    """.trimIndent()) {
                        RhovasIr.Statement.Match.Structural(literal(true), listOf(), RhovasIr.Pattern.Value(literal(true)) to stmt())
                    },
                )) { test("statement", it.source, it.expected) }
            }

            suite("For", listOf(
                "For" to Test("""
                    for (val element in []) { stmt(); }
                """.trimIndent()) {
                    RhovasIr.Statement.For(
                        Variable.Declaration("element", Type.DYNAMIC, false),
                        RhovasIr.Expression.Literal.List(listOf(),Type.LIST[Type.DYNAMIC]),
                        block(stmt()),
                    )
                },
                "Element" to Test("""
                    for (val element in [1]) { stmt(element); }
                """.trimIndent()) {
                    RhovasIr.Statement.For(
                        Variable.Declaration("element", Type.INTEGER, false),
                        RhovasIr.Expression.Literal.List(listOf(
                            literal(BigInteger.parseString("1")),
                        ), Type.LIST[Type.INTEGER]),
                        block(stmt(variable("element", Type.INTEGER))),
                    )
                },
                "Invalid Iterable" to Test("""
                    for (val element in {}) { stmt(); }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("While", listOf(
                "While" to Test("""
                    while (true) { stmt(); }
                """.trimIndent()) {
                    RhovasIr.Statement.While(
                        literal(true),
                        block(stmt()),
                    )
                },
                "Invalid Condition" to Test("""
                    while (1) { stmt(); }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Try", listOf(
                "Try" to Test("""
                    try { stmt(); }
                """.trimIndent()) {
                    RhovasIr.Statement.Try(
                        block(stmt()),
                        listOf(),
                        null,
                    )
                },
                "Catch" to Test("""
                    try {
                        stmt(1);
                    } catch (val e: Exception) {
                        stmt(2);
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Try(
                        block(stmt(1)),
                        listOf(RhovasIr.Statement.Try.Catch(Variable.Declaration("e", Type.EXCEPTION, false), block(stmt(2)))),
                        null,
                    )
                },
                "Finally" to Test("""
                    try {
                        stmt(1);
                    } finally {
                        stmt(2);
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Try(
                        block(stmt(1)),
                        listOf(),
                        block(stmt(2)),
                    )
                },
                "Invalid Catch Type" to Test("""
                    try {
                        stmt(1);
                    } catch (val e: Any) {
                        stmt(2);
                    }
                """.trimIndent(), null),
                "Finally Exception" to Test("""
                    try {
                        stmt();
                    } finally {
                        throw Exception("message");
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("With", listOf(
                "With" to Test("""
                    with (1) { stmt(); }
                """.trimIndent()) {
                    RhovasIr.Statement.With(null,
                        literal(BigInteger.parseString("1")),
                        block(stmt()),
                    )
                },
                "Named Argument" to Test("""
                    with (val name = 1) { stmt(name); }
                """.trimIndent()) {
                    RhovasIr.Statement.With(
                        Variable.Declaration("name", Type.INTEGER, false),
                        literal(BigInteger.parseString("1")),
                        block(stmt(variable("name", Type.INTEGER))),
                    )
                },
            )) { test("statement", it.source, it.expected) }

            suite("Label", listOf(
                "For" to Test("""
                    label: for (val element in []) {
                        break label;
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Label("label",
                        RhovasIr.Statement.For(
                            Variable.Declaration("element", Type.DYNAMIC, false),
                            RhovasIr.Expression.Literal.List(listOf(), Type.LIST[Type.DYNAMIC]),
                            block(RhovasIr.Statement.Break("label")),
                        ),
                    )
                },
                "While" to Test("""
                    label: while (true) {
                        break label;
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Label("label",
                        RhovasIr.Statement.While(
                            literal(true),
                            block(RhovasIr.Statement.Break("label")),
                        ),
                    )
                },
                "Unused Label" to Test("""
                    label: while (true) {}
                """.trimIndent()) {
                    RhovasIr.Statement.Label("label",
                        RhovasIr.Statement.While(
                            literal(true),
                            block(),
                        ),
                    )
                },
                "Invalid Statement" to Test("""
                    label: stmt(0);
                """.trimIndent(), null),
                "Redefined Label" to Test("""
                    label: while (true) {
                        label: while (true) {
                            break label;
                        }
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Break", listOf(
                "Break" to Test("""
                    while (true) {
                        break;
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.While(
                        literal(true),
                        block(RhovasIr.Statement.Break(null)),
                    )
                },
                "Label" to Test("""
                    label: while (true) {
                        break label;
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Label(
                        "label",
                        RhovasIr.Statement.While(
                            literal(true),
                            block(RhovasIr.Statement.Break("label")),
                        ),
                    )
                },
                "Invalid Statement" to Test("""
                    break;
                """.trimIndent(), null),
                "Undefined Label" to Test("""
                    while (true) {
                        break label;
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Continue", listOf(
                "Continue" to Test("""
                    while (true) {
                        continue;
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.While(
                        literal(true),
                        block(RhovasIr.Statement.Continue(null)),
                    )
                },
                "Label" to Test("""
                    label: while (true) {
                        continue label;
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Label("label",
                        RhovasIr.Statement.While(
                            literal(true),
                            block(RhovasIr.Statement.Continue("label")),
                        ),
                    )
                },
                "Invalid Statement" to Test("""
                    continue;
                """.trimIndent(), null),
                "Undefined Label" to Test("""
                    while (true) {
                        continue label;
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Return", listOf(
                "Return Void" to Test("""
                    func test() {
                        return;
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Declaration.Function(
                        Function.Declaration("test", listOf(), listOf(), Type.VOID, listOf()),
                        block(RhovasIr.Statement.Return(null, listOf())),
                    )
                },
                "Return Value" to Test("""
                    func test(): Integer {
                        return 1;
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Declaration.Function(
                        Function.Declaration("test", listOf(), listOf(), Type.INTEGER, listOf()),
                        block(RhovasIr.Statement.Return(literal(BigInteger.parseString("1")), listOf())),
                    )
                },
                "Invalid Return" to Test("""
                    return;
                """.trimIndent(), null),
                "Invalid Return Type" to Test("""
                    func test(): Integer {
                        return 1.0;
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Throw", listOf(
                "Throw" to Test("""
                    func function() throws Exception {
                        throw Exception("message");
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Declaration.Function(
                        Function.Declaration("function", listOf(), listOf(), Type.VOID, listOf(Type.EXCEPTION)),
                        block(RhovasIr.Statement.Throw(RhovasIr.Expression.Invoke.Constructor(
                            Type.EXCEPTION,
                            Type.EXCEPTION.functions["", listOf(Type.STRING)]!! as Function.Definition,
                            listOf(literal("message")),
                            Type.EXCEPTION,
                        ))),
                    )
                },
                "Invalid" to Test("""
                    throw 1;
                """.trimIndent(), null),
                "Uncaught" to Test("""
                    func function() {
                        throw Exception("message");
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Assert", listOf(
                "Assert" to Test("""
                    assert true;
                """.trimIndent()) {
                    RhovasIr.Statement.Assert(literal(true), null)
                },
                "Message" to Test("""
                    assert true: "message";
                """.trimIndent()) {
                    RhovasIr.Statement.Assert(literal(true), literal("message"))
                },
                "Invalid Condition" to Test("""
                    assert 1;
                """.trimIndent(), null),
                "Invalid Message" to Test("""
                    assert true: 1;
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Require", listOf(
                "Require" to Test("""
                    require true;
                """.trimIndent()) {
                    RhovasIr.Statement.Require(literal(true), null)
                },
                "Message" to Test("""
                    require true: "message";
                """.trimIndent()) {
                    RhovasIr.Statement.Require(literal(true), literal("message"))
                },
                "Invalid Condition" to Test("""
                    require 1;
                """.trimIndent(), null),
                "Invalid Message" to Test("""
                    require true: 1;
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Ensure", listOf(
                "Ensure" to Test("""
                    ensure true;
                """.trimIndent()) {
                    RhovasIr.Statement.Ensure(literal(true), null)
                },
                "Message" to Test("""
                    ensure true: "message";
                """.trimIndent()) {
                    RhovasIr.Statement.Ensure(literal(true), literal("message"))
                },
                "Post-Return" to Test("""
                    func test(): Boolean {
                        return true;
                        ensure val;
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Ensure(literal(true), literal("message"))
                    RhovasIr.Statement.Declaration.Function(
                        Function.Declaration("test", listOf(), listOf(), Type.BOOLEAN, listOf()),
                        block(RhovasIr.Statement.Return(literal(true), listOf(
                            RhovasIr.Statement.Ensure(variable("val", Type.BOOLEAN), null),
                        ))),
                    )
                },
                "Invalid Condition" to Test("""
                    ensure 1;
                """.trimIndent(), null),
                "Invalid Message" to Test("""
                    ensure true: 1;
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }
        }

        suite("Expression") {
            suite("Block", listOf(
                "Empty" to Test("""
                    do {}
                """.trimIndent()) {
                    RhovasIr.Expression.Block(listOf(), null, Type.VOID)
                },
                "Statement" to Test("""
                    do { stmt(); }
                """.trimIndent()) {
                    RhovasIr.Expression.Block(listOf(stmt()), null, Type.VOID)
                },
                "Expression" to Test("""
                    do { 1 }
                """.trimIndent()) {
                    RhovasIr.Expression.Block(listOf(), literal(BigInteger.parseString("1")), Type.INTEGER)
                },
                "Unreachable Statement" to Test("""
                    do {
                        throw Exception("message");
                        stmt();
                    }
                """.trimIndent(), null),
                "Unreachable Statement (Expression)" to Test("""
                    do {
                        throw Exception("message");
                        1
                    }
                """.trimIndent(), null),
            )) { test("expression", it.source, it.expected) }

            suite("Literal") {
                suite("Scalar", listOf(
                    "Null" to Test("""
                        null
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.Scalar(null, Type.NULLABLE[Type.DYNAMIC])
                    },
                    "Boolean" to Test("""
                        true
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.Scalar(true, Type.BOOLEAN)
                    },
                    "Integer" to Test("""
                        123
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.Scalar(BigInteger.parseString("123"), Type.INTEGER)
                    },
                    "Decimal" to Test("""
                        123.456
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.Scalar(BigDecimal.parseString("123.456"), Type.DECIMAL)
                    },
                    "Atom" to Test("""
                        :atom
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.Scalar(RhovasAst.Atom("atom"), Type.ATOM)
                    },
                )) { test("expression", it.source, it.expected) }

                suite("String", listOf(
                    "String" to Test("""
                        "string"
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.String(listOf("string"), listOf(), Type.STRING)
                    },
                    "Interpolation" to Test("""
                        "first${'$'}{1}second"
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.String(listOf("first", "second"), listOf(literal(BigInteger.parseString("1"))), Type.STRING)
                    },
                )) { test("expression", it.source, it.expected) }

                suite("List", listOf(
                    "Empty" to Test("""
                        []
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.List(listOf(), Type.LIST[Type.DYNAMIC])
                    },
                    "Single" to Test("""
                        [1]
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.List(listOf(
                            literal(BigInteger.parseString("1")),
                        ), Type.LIST[Type.INTEGER])
                    },
                    "Multiple" to Test("""
                        [1, 2, 3]
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.List(listOf(
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            literal(BigInteger.parseString("3")),
                        ), Type.LIST[Type.INTEGER])
                    },
                    "Tuple" to Test("""
                        Tuple([1, "string"])
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Constructor(
                            Type.TUPLE.GENERIC,
                            Type.TUPLE.GENERIC.functions["", listOf(Type.TUPLE[listOf(Type.INTEGER, Type.STRING), true])]!!,
                            listOf(RhovasIr.Expression.Literal.List(
                                listOf(literal(BigInteger.parseString("1")), literal("string")),
                                Type.TUPLE[listOf(Type.INTEGER, Type.STRING), true],
                            )),
                            Type.TUPLE[listOf(Type.INTEGER, Type.STRING), true],
                        )
                    },
                )) { test("expression", it.source, it.expected) }

                suite("Object", listOf(
                    "Empty" to Test("""
                        {}
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.Object(
                            mapOf(),
                            Type.STRUCT[Type.Struct(mapOf())],
                        )
                    },
                    "Single" to Test("""
                        {key: "value"}
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.Object(
                            mapOf("key" to literal("value")),
                            Type.STRUCT[Type.Struct(mapOf("key" to Variable.Declaration("key", Type.STRING, true)))],
                        )
                    },
                    "Multiple" to Test("""
                        {k1: "v1", k2: "v2", k3: "v3"}
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.Object(
                            mapOf("k1" to literal("v1"), "k2" to literal("v2"), "k3" to literal("v3")),
                            Type.STRUCT[Type.Struct(mapOf("k1" to Variable.Declaration("k1", Type.STRING, true), "k2" to Variable.Declaration("k2", Type.STRING, true), "k3" to Variable.Declaration("k3", Type.STRING, true)))],
                        )
                    },
                    "Map" to Test("""
                        Map({key: "value"})
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Constructor(
                            Type.MAP.GENERIC,
                            Type.MAP.GENERIC.functions["", listOf(Type.MAP[Type.ATOM, Type.STRING])]!!,
                            listOf(RhovasIr.Expression.Literal.Object(
                                mapOf("key" to literal("value")),
                                Type.MAP[Type.ATOM, Type.STRING],
                            )),
                            Type.MAP[Type.ATOM, Type.STRING],
                        )
                    },
                    "Redefined" to Test("""
                        {key: "v1", key: "v2"}
                    """.trimIndent(), null),
                    "Map Redefined" to Test("""
                        Map({key: "v1", key: "v2"})
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Type", listOf(
                    "Type" to Test("""
                        Any
                    """.trimIndent()) {
                        RhovasIr.Expression.Literal.Type(Type.ANY, Type.TYPE[Type.ANY])
                    },
                )) { test("expression", it.source, it.expected) }
            }

            suite("Group", listOf(
                "Literal" to Test("""
                    ("expression")
                """.trimIndent()) {
                    RhovasIr.Expression.Group(literal("expression"))
                },
                "Binary" to Test("""
                    ("first" + "second")
                """.trimIndent()) {
                    RhovasIr.Expression.Group(RhovasIr.Expression.Binary("+",
                        literal("first"),
                        literal("second"),
                        Type.STRING.methods["+", listOf(Type.STRING)],
                        Type.STRING,
                    ))
                },
                "Nested" to Test("""
                    (("expression"))
                """.trimIndent()) {
                    RhovasIr.Expression.Group(
                        RhovasIr.Expression.Group(literal("expression")),
                    )
                },
            )) { test("expression", it.source, it.expected) }

            suite("Unary", listOf(
                "Boolean Negation" to Test("""
                    !true
                """.trimIndent()) {
                    RhovasIr.Expression.Unary("!",
                        literal(true),
                        Type.BOOLEAN.methods["!", listOf()]!!,
                    )
                },
                "Integer Negation" to Test("""
                    -1
                """.trimIndent()) {
                    RhovasIr.Expression.Unary("-",
                        literal(BigInteger.parseString("1")),
                        Type.INTEGER.methods["-", listOf()]!!,
                    )
                },
                "Invalid" to Test("""
                    -true
                """.trimIndent(), null),
            )) { test("expression", it.source, it.expected) }

            suite("Binary") {
                suite("Logical And", listOf(
                    "True" to Test("""
                        true && true
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("&&",
                            literal(true),
                            literal(true),
                            null,
                            Type.BOOLEAN,
                        )
                    },
                    "False" to Test("""
                        true && false
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("&&",
                            literal(true),
                            literal(false),
                            null,
                            Type.BOOLEAN,
                        )
                    },
                    "Invalid Left" to Test("""
                        1 && false
                    """.trimIndent(), null),
                    "Invalid Right" to Test("""
                        true && 2
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Logical Or", listOf(
                    "True" to Test("""
                        false || true
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("||",
                            literal(false),
                            literal(true),
                            null,
                            Type.BOOLEAN,
                        )
                    },
                    "False" to Test("""
                        false || false
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("||",
                            literal(false),
                            literal(false),
                            null,
                            Type.BOOLEAN,
                        )
                    },
                    "Invalid Left" to Test("""
                        1 || true
                    """.trimIndent(), null),
                    "Invalid Right" to Test("""
                        false || 2
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Equality", listOf(
                    "Equatable" to Test("""
                        1 == 2
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("==",
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            null,
                            Type.BOOLEAN,
                        )
                    },
                    "Unequatable Left" to Test("""
                        lambda {} != 2
                    """.trimIndent(), null),
                    "Unequatable Right" to Test("""
                        1 != lambda {}
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Identity", listOf(
                    "Equatable" to Test("""
                        1 === 2
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("===",
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            null,
                            Type.BOOLEAN,
                        )
                    },
                    "Maybe Equatable" to Test("""
                        1 === any
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("===",
                            literal(BigInteger.parseString("1")),
                            variable("any", Type.ANY),
                            null,
                            Type.BOOLEAN,
                        )
                    },
                    "Not Equatable" to Test("""
                        1 !== 2.0
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("!==",
                            literal(BigInteger.parseString("1")),
                            literal(BigDecimal.parseString("2.0")),
                            null,
                            Type.BOOLEAN,
                        )
                    },
                )) { test("expression", it.source, it.expected) {
                    it.variables.define(variable("any", Type.ANY).variable)
                } }

                suite("Comparison", listOf(
                    "Less Than" to Test("""
                        1 < 2
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("<",
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            Type.INTEGER.methods["<=>", listOf(Type.INTEGER)]!!,
                            Type.BOOLEAN,
                        )
                    },
                    "Greater Than Or Equal" to Test("""
                        1.0 >= 2.0
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary(">=",
                            literal(BigDecimal.parseString("1.0")),
                            literal(BigDecimal.parseString("2.0")),
                            Type.DECIMAL.methods["<=>", listOf(Type.DECIMAL)],
                            Type.BOOLEAN,
                        )
                    },
                    "Invalid Left" to Test("""
                        false <= 2
                    """.trimIndent(), null),
                    "Invalid Right" to Test("""
                        1 > true
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Arithmetic", listOf(
                    "Integer Add" to Test("""
                        1 + 2
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("+",
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            Type.INTEGER.methods["+", listOf(Type.INTEGER)],
                            Type.INTEGER,
                        )
                    },
                    "Decimal Subtract" to Test("""
                        1.0 - 2.0
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("-",
                            literal(BigDecimal.parseString("1.0")),
                            literal(BigDecimal.parseString("2.0")),
                            Type.DECIMAL.methods["-", listOf(Type.DECIMAL)],
                            Type.DECIMAL,
                        )
                    },
                    "Integer Multiply" to Test("""
                        1 * 2
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("*",
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            Type.INTEGER.methods["*", listOf(Type.INTEGER)],
                            Type.INTEGER,
                        )
                    },
                    "Decimal Divide" to Test("""
                        1.0 / 2.0
                    """.trimIndent()) {
                        RhovasIr.Expression.Binary("/",
                            literal(BigDecimal.parseString("1.0")),
                            literal(BigDecimal.parseString("2.0")),
                            Type.DECIMAL.methods["/", listOf(Type.DECIMAL)],
                            Type.DECIMAL,
                        )
                    },
                    "Invalid Left" to Test("""
                        false + 2
                    """.trimIndent(), null),
                    "Invalid Right" to Test("""
                        1 + true
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }
            }

            suite("Access") {
                suite("Variable", listOf(
                    "Variable" to Test("""
                        variable
                    """.trimIndent()) {
                        RhovasIr.Expression.Access.Variable(null, Variable.Declaration("variable", Type.ANY, false))
                    },
                    "Undefined" to Test("""
                        undefined
                    """.trimIndent(), null),
                    "Uninitialized" to Test("""
                        do { val x: Integer; x }
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) {
                    it.variables.define(variable("variable", Type.ANY).variable)
                } }

                suite("Property", listOf(
                    "Property" to Test("""
                        "string".size
                    """.trimIndent()) {
                        RhovasIr.Expression.Access.Property(
                            literal("string"),
                            Type.STRING.properties["size"]!!,
                            false,
                            false,
                            Type.INTEGER,
                        )
                    },
                    "Bang" to Test("""
                        [""].first!
                    """.trimIndent()) {
                        RhovasIr.Expression.Access.Property(
                            RhovasIr.Expression.Literal.List(listOf(literal("")), Type.LIST[Type.STRING]),
                            Type.LIST[Type.STRING].properties["first"]!!,
                            true,
                            false,
                            Type.STRING,
                        )
                    },
                    "Coalesce" to Test("""
                        Nullable("string")?.size
                    """.trimIndent()) {
                        RhovasIr.Expression.Access.Property(
                            RhovasIr.Expression.Invoke.Constructor(
                                Type.NULLABLE.GENERIC,
                                Type.NULLABLE.GENERIC.functions["", listOf(Type.STRING)]!!,
                                listOf(literal("string")),
                                Type.NULLABLE[Type.STRING]
                            ),
                            Type.STRING.properties["size"]!!,
                            false,
                            true,
                            Type.NULLABLE[Type.INTEGER],
                        )
                    },
                    "Element" to Test("""
                        Tuple([1]).0
                    """.trimIndent()) {
                        RhovasIr.Expression.Access.Property(
                            RhovasIr.Expression.Invoke.Constructor(
                                Type.TUPLE.GENERIC,
                                Type.TUPLE.GENERIC.functions["", listOf(Type.TUPLE[listOf(Type.INTEGER), true])]!!,
                                listOf(RhovasIr.Expression.Literal.List(listOf(literal(BigInteger.parseString("1"))), Type.TUPLE[listOf(Type.INTEGER), true])),
                                Type.TUPLE[listOf(Type.INTEGER), true],
                            ),
                            Type.TUPLE[listOf(Type.INTEGER), true].properties["0"]!!,
                            false,
                            false,
                            Type.INTEGER,
                        )
                    },
                    "Undefined" to Test("""
                        "string".undefined
                    """.trimIndent(), null),
                    "Invalid Bang" to Test("""
                        "string".size!
                    """.trimIndent(), null),
                    "Invalid Coalesce" to Test("""
                        "string"?.size
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Index", listOf(
                    "Index" to Test("""
                        list[0]
                    """.trimIndent()) {
                        RhovasIr.Expression.Access.Index(
                            variable("list", Type.LIST[Type.ANY]),
                            Type.LIST[Type.ANY].methods["[]", listOf(Type.INTEGER)]!!,
                            false,
                            listOf(literal(BigInteger.parseString("0"))),
                            Type.ANY,
                        )
                    },
                    "Coalesce" to Test("""
                        Nullable(list)?[0]
                    """.trimIndent()) {
                        RhovasIr.Expression.Access.Index(
                            RhovasIr.Expression.Invoke.Constructor(
                                Type.NULLABLE.GENERIC,
                                Type.NULLABLE.GENERIC.functions["", listOf(Type.LIST[Type.ANY])]!!,
                                listOf(variable("list", Type.LIST[Type.ANY])),
                                Type.NULLABLE[Type.LIST[Type.ANY]]
                            ),
                            Type.LIST[Type.ANY].methods["[]", listOf(Type.INTEGER)]!!,
                            true,
                            listOf(literal(BigInteger.parseString("0"))),
                            Type.NULLABLE[Type.ANY],
                        )
                    },
                    "Invalid Arity" to Test("""
                        list[]
                    """.trimIndent(), null),
                    "Invalid Argument" to Test("""
                        list[:key]
                    """.trimIndent(), null),
                    "Undefined" to Test("""
                        any[0]
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) {
                    it.variables.define(variable("list", Type.LIST[Type.ANY]).variable)
                    it.variables.define(variable("any", Type.ANY).variable)
                } }
            }

            suite("Invoke") {
                suite("Constructor", listOf(
                    "Function" to Test("""
                        Nullable("argument")
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Constructor(
                            Type.NULLABLE.GENERIC,
                            Type.NULLABLE.GENERIC.functions["", listOf(Type.STRING)]!!,
                            listOf(literal("argument")),
                            Type.NULLABLE[Type.STRING],
                        )
                    },
                    "Unconstructable" to Test("""
                        Unconstructable()
                    """.trimIndent()) {
                        it.types.define(Type.Generic("T", Type.ANY), "Unconstructable")
                        null
                    },
                    "Undefined" to Test("""
                        Undefined()
                    """.trimIndent(), null),
                    "Invalid Arity" to Test("""
                        Nullable()
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Function", listOf(
                    "Function" to Test("""
                        stmt("argument")
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Function(null, STMT_1, false, listOf(literal("argument")), Type.VOID)
                    },
                    "Undefined" to Test("""
                        undefined()
                    """.trimIndent(), null),
                    "Invalid Arity" to Test("""
                        stmt(1, 2)
                    """.trimIndent(), null),
                    "Invalid Argument" to Test("""
                        range(1, 2, "incl")
                    """.trimIndent(), null),
                    "Invalid Bang" to Test("""
                        stmt!("argument")
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Method", listOf(
                    "Method" to Test("""
                        "string".contains("")
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Method(
                            literal("string"),
                            Type.STRING.methods["contains", listOf(Type.STRING)]!!,
                            false,
                            false,
                            false,
                            listOf(literal("")),
                            Type.BOOLEAN,
                        )
                    },
                    "Coalesce" to Test("""
                        Nullable("string")?.contains("")
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Method(
                            RhovasIr.Expression.Invoke.Constructor(
                                Type.NULLABLE.GENERIC,
                                Type.NULLABLE.GENERIC.functions["", listOf(Type.STRING)]!! as Function.Definition,
                                listOf(literal("string")),
                                Type.NULLABLE[Type.STRING],
                            ),
                            Type.STRING.methods["contains", listOf(Type.STRING)]!!,
                            false,
                            true,
                            false,
                            listOf(literal("")),
                            Type.NULLABLE[Type.BOOLEAN],
                        )
                    },
                    "Cascade" to Test("""
                        "string"..contains("")
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Method(
                            literal("string"),
                            Type.STRING.methods["contains", listOf(Type.STRING)]!!,
                            false,
                            false,
                            true,
                            listOf(literal("")),
                            Type.STRING,
                        )
                    },
                    "Undefined" to Test("""
                        "string".undefined()
                    """.trimIndent(), null),
                    "Invalid Arity" to Test("""
                        "string".contains()
                    """.trimIndent(), null),
                    "Invalid Argument" to Test("""
                        "string".contains(0)
                    """.trimIndent(), null),
                    "Unresolved Overload" to Test("""
                        "string".to(Boolean)
                    """.trimIndent(), null),
                    "Invalid Bang" to Test("""
                        "string".contains!("")
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Pipeline", listOf(
                    "Pipeline" to Test("""
                        1.|range(2, :incl)
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Pipeline(
                            literal(BigInteger.parseString("1")),
                            null,
                            Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                            false,
                            false,
                            false,
                            listOf(literal(BigInteger.parseString("2")), literal(RhovasAst.Atom("incl"))),
                            Type.LIST[Type.INTEGER],
                        )
                    },
                    "Qualified" to Test("""
                        1.|Kernel.range(2, :incl)
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Pipeline(
                            literal(BigInteger.parseString("1")),
                            Library.type("Kernel"),
                            Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                            false,
                            false,
                            false,
                            listOf(literal(BigInteger.parseString("2")), literal(RhovasAst.Atom("incl"))),
                            Type.LIST[Type.INTEGER],
                        )
                    },
                    "Coalesce" to Test("""
                        Nullable(1)?.|range(2, :incl)
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Pipeline(
                            RhovasIr.Expression.Invoke.Constructor(
                                Type.NULLABLE.GENERIC,
                                Type.NULLABLE.GENERIC.functions["", listOf(Type.INTEGER)]!! as Function.Definition,
                                listOf(literal(BigInteger.parseString("1"))),
                                Type.NULLABLE[Type.INTEGER],
                            ),
                            null,
                            Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                            false,
                            true,
                            false,
                            listOf(literal(BigInteger.parseString("2")), literal(RhovasAst.Atom("incl"))),
                            Type.NULLABLE[Type.LIST[Type.INTEGER]],
                        )
                    },
                    "Cascade" to Test("""
                        1..|range(2, :incl)
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Pipeline(
                            literal(BigInteger.parseString("1")),
                            null,
                            Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                            false,
                            false,
                            true,
                            listOf(literal(BigInteger.parseString("2")), literal(RhovasAst.Atom("incl"))),
                            Type.INTEGER,
                        )
                    },
                    "Undefined" to Test("""
                        1.|undefined()
                    """.trimIndent(), null),
                    "Invalid Arity" to Test("""
                        1.|range()
                    """.trimIndent(), null),
                    "Invalid Argument" to Test("""
                        1.|range(2, "incl")
                    """.trimIndent(), null),
                    "Invalid Bang" to Test("""
                        1.|range!(2, :incl)
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Macro", listOf(
                    "Macro (Unsupported)" to Test("""
                        #macro(1)
                    """.trimIndent(), null),
                    "DSL" to Test("""
                        #regex {
                            literal
                        }
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Function(null, Library.SCOPE.functions["regex", 2].single(), false, listOf(
                            RhovasIr.Expression.Literal.List(listOf(literal("literal")), Type.LIST[Type.STRING]),
                            RhovasIr.Expression.Literal.List(listOf(), Type.LIST[Type.DYNAMIC]),
                        ), Type.REGEX)
                    },
                    "Argument" to Test("""
                        #regex {
                            argument = ${'$'}{"argument"}
                        }
                    """.trimIndent()) {
                        RhovasIr.Expression.Invoke.Function(null, Library.SCOPE.functions["regex", 2].single(), false, listOf(
                            RhovasIr.Expression.Literal.List(listOf(literal("argument = "), literal("")), Type.LIST[Type.STRING]),
                            RhovasIr.Expression.Literal.List(listOf(literal("argument")), Type.LIST[Type.DYNAMIC]),
                        ), Type.REGEX)
                    },
                    "Undefined" to Test("""
                        #undefined {}
                    """.trimIndent(), null),
                    "Arguments (Unsupported)" to Test("""
                        #regex(1) {}
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }
            }

            suite("Lambda", listOf(
                "Empty" to Test("""
                    lambda {}
                """.trimIndent()) {
                    RhovasIr.Expression.Invoke.Function(null, Library.SCOPE.functions["lambda", listOf(Type.LAMBDA[Type.TUPLE.DYNAMIC, Type.DYNAMIC, Type.DYNAMIC])]!!, false, listOf(
                        RhovasIr.Expression.Lambda(listOf(), block(), Type.LAMBDA[Type.TUPLE.DYNAMIC, Type.DYNAMIC, Type.DYNAMIC]),
                    ), Type.LAMBDA[Type.TUPLE.DYNAMIC, Type.DYNAMIC, Type.DYNAMIC])
                },
                "Body" to Test("""
                    lambda { stmt(); }
                """.trimIndent()) {
                    RhovasIr.Expression.Invoke.Function(null, Library.SCOPE.functions["lambda", listOf(Type.LAMBDA[Type.TUPLE.DYNAMIC, Type.DYNAMIC, Type.DYNAMIC])]!!, false, listOf(
                        RhovasIr.Expression.Lambda(listOf(), block(stmt()), Type.LAMBDA[Type.TUPLE.DYNAMIC, Type.DYNAMIC, Type.DYNAMIC]),
                    ), Type.LAMBDA[Type.TUPLE.DYNAMIC, Type.DYNAMIC, Type.DYNAMIC])
                },
                "Parameter" to Test("""
                    lambda |x| {}
                """.trimIndent()) {
                    RhovasIr.Expression.Invoke.Function(null, Library.SCOPE.functions["lambda", listOf(Type.LAMBDA[Type.TUPLE[listOf(Type.DYNAMIC)], Type.DYNAMIC, Type.DYNAMIC])]!!, false, listOf(
                        RhovasIr.Expression.Lambda(
                            listOf(Variable.Declaration("x", Type.DYNAMIC, false)),
                            block(),
                            Type.LAMBDA[Type.TUPLE[listOf(Type.DYNAMIC)], Type.DYNAMIC, Type.DYNAMIC],
                        ),
                    ), Type.LAMBDA[Type.TUPLE[listOf(Type.DYNAMIC)], Type.DYNAMIC, Type.DYNAMIC])
                },
                "Parameter Type" to Test("""
                    lambda |x: Integer| {}
                """.trimIndent()) {
                    RhovasIr.Expression.Invoke.Function(null, Library.SCOPE.functions["lambda", listOf(Type.LAMBDA[Type.TUPLE[listOf(Type.INTEGER)], Type.DYNAMIC, Type.DYNAMIC])]!!, false, listOf(
                        RhovasIr.Expression.Lambda(
                            listOf(Variable.Declaration("x", Type.INTEGER, false)),
                            block(),
                            Type.LAMBDA[Type.TUPLE[listOf(Type.INTEGER)], Type.DYNAMIC, Type.DYNAMIC],
                        ),
                    ), Type.LAMBDA[Type.TUPLE[listOf(Type.INTEGER)], Type.DYNAMIC, Type.DYNAMIC])
                },
                "Expression" to Test("""
                    lambda { 1 }
                """.trimIndent()) {
                    RhovasIr.Expression.Invoke.Function(null, Library.SCOPE.functions["lambda", listOf(Type.LAMBDA[Type.TUPLE.DYNAMIC, Type.DYNAMIC, Type.DYNAMIC])]!!, false, listOf(
                        RhovasIr.Expression.Lambda(
                            listOf(),
                            RhovasIr.Expression.Block(listOf(), literal(BigInteger.parseString("1")), Type.INTEGER),
                            Type.LAMBDA[Type.TUPLE.DYNAMIC, Type.DYNAMIC, Type.DYNAMIC],
                        ),
                    ), Type.LAMBDA[Type.TUPLE.DYNAMIC, Type.DYNAMIC, Type.DYNAMIC])
                },
                "Without Inference" to Test("""
                    print {}
                """.trimIndent()) {
                    RhovasIr.Expression.Invoke.Function(null, Library.SCOPE.functions["print", listOf(Type.ANY)]!!, false, listOf(
                        RhovasIr.Expression.Lambda(listOf(), block(), Type.LAMBDA[Type.TUPLE.DYNAMIC, Type.DYNAMIC, Type.DYNAMIC]),
                    ), Type.VOID)
                },
                "Invalid Return Type" to Test("""
                    [].find { 1 }
                """.trimIndent(), null),
            )) { test("expression", it.source, it.expected) }
        }

        suite("Pattern") {
            suite("Value", listOf(
                "Boolean" to Test("""
                    match (true) {
                        else true: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        literal(true),
                        listOf(),
                        Pair(RhovasIr.Pattern.Value(literal(true)), stmt())
                    )
                },
                "Integer" to Test("""
                    match (1) {
                        else 1: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        literal(BigInteger.parseString("1")),
                        listOf(),
                        Pair(RhovasIr.Pattern.Value(literal(BigInteger.parseString("1"))), stmt())
                    )
                },
                "Decimal" to Test("""
                    match (1.0) {
                        else 1.0: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        literal(BigDecimal.parseString("1.0")),
                        listOf(),
                        Pair(RhovasIr.Pattern.Value(literal(BigDecimal.parseString("1.0"))), stmt())
                    )
                },
                "String" to Test("""
                    match ("string") {
                        else "string": stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        literal("string"),
                        listOf(),
                        Pair(RhovasIr.Pattern.Value(literal("string")), stmt())
                    )
                },
                "Atom" to Test("""
                    match (:atom) {
                        else :atom: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        literal(RhovasAst.Atom("atom")),
                        listOf(),
                        Pair(RhovasIr.Pattern.Value(literal(RhovasAst.Atom("atom"))), stmt())
                    )
                },
                "Supertype Argument" to Test("""
                    match (any) {
                        else 1: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        variable("any", Type.ANY),
                        listOf(),
                        Pair(RhovasIr.Pattern.Value(literal(BigInteger.parseString("1"))), stmt())
                    )
                },
                "Unmatchable Argument" to Test("""
                    match (1.0) {
                        else 1: stmt();
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) {
                it.variables.define(variable("any", Type.ANY).variable)
            } }

            suite("Variable", listOf(
                "Variable" to Test("""
                    match (1) {
                        else name: stmt(name);
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        literal(BigInteger.parseString("1")),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.Variable(Variable.Declaration("name", Type.INTEGER, false)),
                            stmt(variable("name", Type.INTEGER))
                        )
                    )
                },
                "Underscore" to Test("""
                    match (1) {
                        else _: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        literal(BigInteger.parseString("1")),
                        listOf(),
                        Pair(RhovasIr.Pattern.Variable(null), stmt())
                    )
                },
                "Redefinition" to Test("""
                    match ([1, 2, 3]) {
                        [x, y, x]: stmt(x);
                        else: stmt();
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Predicate", listOf(
                "Predicate" to Test("""
                    match (1) {
                        else _ ${'$'}{val > 0}: stmt();
                    }
                """.trimIndent()) {
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
                },
                "Variable" to Test("""
                    match (1) {
                        else name ${'$'}{name > 0}: stmt();
                    }
                """.trimIndent()) {
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
                },
                "Implicit Value" to Test("""
                    match (1) {
                        else _ ${'$'}{val > 0}: stmt();
                    }
                """.trimIndent()) {
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
                },
                "Invalid Type" to Test("""
                    match (1) {
                        else _ ${'$'}{1}: stmt();
                    }
                """.trimIndent(), null),
                "Variable Scope" to Test("""
                    match (range(1, 2, :incl)) {
                        else [x, y ${'$'}{x != y}]: stmt();
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("OrderedDestructure", listOf(
                "Variable" to Test("""
                    match (range(1, 1, :incl)) {
                        else [elem]: stmt(elem);
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        RhovasIr.Expression.Invoke.Function(
                            null,
                            Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                            false,
                            listOf(literal(BigInteger.parseString("1")), literal(BigInteger.parseString("1")), literal(RhovasAst.Atom("incl"))),
                            Type.LIST[Type.INTEGER],
                        ),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.OrderedDestructure(
                                listOf(RhovasIr.Pattern.Variable(Variable.Declaration("elem", Type.INTEGER, false))),
                            ),
                            stmt(variable("elem", Type.INTEGER)),
                        ),
                    )
                },
                "Multiple" to Test("""
                    match (range(1, 3, :incl)) {
                        else [1, 2, 3]: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        RhovasIr.Expression.Invoke.Function(
                            null,
                            Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                            false,
                            listOf(literal(BigInteger.parseString("1")), literal(BigInteger.parseString("3")), literal(RhovasAst.Atom("incl"))),
                            Type.LIST[Type.INTEGER],
                        ),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.OrderedDestructure(
                                listOf(
                                    RhovasIr.Pattern.Value(literal(BigInteger.parseString("1"))),
                                    RhovasIr.Pattern.Value(literal(BigInteger.parseString("2"))),
                                    RhovasIr.Pattern.Value(literal(BigInteger.parseString("3"))),
                                ),
                            ),
                            stmt(),
                        ),
                    )
                },
                "Varargs" to Test("""
                    match (range(1, 3, :incl)) {
                        else [*]: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        RhovasIr.Expression.Invoke.Function(
                            null,
                            Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                            false,
                            listOf(literal(BigInteger.parseString("1")), literal(BigInteger.parseString("3")), literal(RhovasAst.Atom("incl"))),
                            Type.LIST[Type.INTEGER],
                        ),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.OrderedDestructure(
                                listOf(RhovasIr.Pattern.VarargDestructure(null, "*", mapOf())),
                            ),
                            stmt(),
                        ),
                    )
                },
                "Varargs Pattern" to Test("""
                    match (range(1, 3, :incl)) {
                        else [elements*]: stmt(elements[0]);
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        RhovasIr.Expression.Invoke.Function(
                            null,
                            Library.SCOPE.functions["range", listOf(Type.INTEGER, Type.INTEGER, Type.ATOM)]!!,
                            false,
                            listOf(literal(BigInteger.parseString("1")), literal(BigInteger.parseString("3")), literal(RhovasAst.Atom("incl"))),
                            Type.LIST[Type.INTEGER],
                        ),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.OrderedDestructure(
                                listOf(RhovasIr.Pattern.VarargDestructure(
                                    RhovasIr.Pattern.Variable(Variable.Declaration("elements", Type.INTEGER, false)),
                                    "*",
                                    mapOf("elements" to Variable.Declaration("elements", Type.LIST[Type.INTEGER], false)),
                                )),
                            ),
                            stmt(RhovasIr.Expression.Access.Index(
                                variable("elements", Type.LIST[Type.INTEGER]),
                                Type.LIST[Type.INTEGER].methods["[]", listOf(Type.INTEGER)]!!,
                                false,
                                listOf(literal(BigInteger.parseString("0"))),
                                Type.INTEGER,
                            )),
                        ),
                    )
                },
                "Supertype Argument" to Test("""
                    match (any) {
                        else []: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        variable("any", Type.ANY),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.OrderedDestructure(listOf()),
                            stmt(),
                        ),
                    )
                },
                "Unmatchable Type" to Test("""
                    match (1) {
                        else []: stmt();
                    }
                """.trimIndent(), null),
                "Multiple Varargs" to Test("""
                    match (any) {
                        else [x*, y+]: stmt();
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) {
                it.variables.define(variable("any", Type.ANY).variable)
            } }

            suite("NamedDestructure", listOf(
                "Key" to Test("""
                    match ({key: 1}) {
                        else {key}: stmt(key);
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        RhovasIr.Expression.Literal.Object(
                            mapOf("key" to literal(BigInteger.parseString("1"))),
                            Type.STRUCT[Type.Struct(mapOf("key" to Variable.Declaration("key", Type.INTEGER, true)))],
                        ),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.NamedDestructure(
                                listOf("key" to RhovasIr.Pattern.Variable(Variable.Declaration("key", Type.INTEGER, false))),
                            ),
                            stmt(variable("key", Type.INTEGER)),
                        ),
                    )
                },
                "Value" to Test("""
                    match ({key: 1}) {
                        else {key: 1}: stmt(key);
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        RhovasIr.Expression.Literal.Object(
                            mapOf("key" to literal(BigInteger.parseString("1"))),
                            Type.STRUCT[Type.Struct(mapOf("key" to Variable.Declaration("key", Type.INTEGER, true)))],
                        ),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.NamedDestructure(
                                listOf("key" to RhovasIr.Pattern.Value(literal(BigInteger.parseString("1")))),
                            ),
                            stmt(variable("key", Type.INTEGER)),
                        ),
                    )
                },
                "Multiple" to Test("""
                    match ({x: 1, y: 2, z: 3}) {
                        else {x, y, z}: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        RhovasIr.Expression.Literal.Object(
                            mapOf("x" to literal(BigInteger.parseString("1")), "y" to literal(BigInteger.parseString("2")), "z" to literal(BigInteger.parseString("3"))),
                            Type.STRUCT[Type.Struct(mapOf("x" to Variable.Declaration("x", Type.INTEGER, true), "y" to Variable.Declaration("y", Type.INTEGER, true), "z" to Variable.Declaration("z", Type.INTEGER, true)))],
                        ),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.NamedDestructure(listOf(
                                "x" to RhovasIr.Pattern.Variable(Variable.Declaration("x", Type.INTEGER, false)),
                                "y" to RhovasIr.Pattern.Variable(Variable.Declaration("y", Type.INTEGER, false)),
                                "z" to RhovasIr.Pattern.Variable(Variable.Declaration("z", Type.INTEGER, false)),
                            )),
                            stmt(),
                        ),
                    )
                },
                "Varargs" to Test("""
                    match ({key: 1}) {
                        else {*}: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        RhovasIr.Expression.Literal.Object(
                            mapOf("key" to literal(BigInteger.parseString("1"))),
                            Type.STRUCT[Type.Struct(mapOf("key" to Variable.Declaration("key", Type.INTEGER, true)))],
                        ),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.NamedDestructure(
                                listOf(null to RhovasIr.Pattern.VarargDestructure(null, "*", mapOf())),
                            ),
                            stmt(),
                        ),
                    )
                },
                "Varargs Pattern" to Test("""
                    match ({key: 1}) {
                        else {object*}: stmt(object.key);
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        RhovasIr.Expression.Literal.Object(
                            mapOf("key" to literal(BigInteger.parseString("1"))),
                            Type.STRUCT[Type.Struct(mapOf("key" to Variable.Declaration("key", Type.INTEGER, true)))],
                        ),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.NamedDestructure(
                                listOf(null to RhovasIr.Pattern.VarargDestructure(
                                    RhovasIr.Pattern.Variable(Variable.Declaration("object", Type.INTEGER, false)),
                                    "*",
                                    mapOf("object" to Variable.Declaration("object", Type.STRUCT[Type.Struct(mapOf("key" to Variable.Declaration("key", Type.INTEGER, false)))], false)),
                                )),
                            ),
                            stmt(RhovasIr.Expression.Access.Property(
                                variable("object", Type.STRUCT[Type.Struct(mapOf("key" to Variable.Declaration("key", Type.INTEGER, false)))]),
                                Type.STRUCT[Type.Struct(mapOf("key" to Variable.Declaration("key", Type.INTEGER, false)))].properties["key"]!!,
                                false,
                                false,
                                Type.INTEGER,
                            )),
                        ),
                    )
                },
                "Supertype Argument" to Test("""
                    match (any) {
                        else {}: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        variable("any", Type.ANY),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.NamedDestructure(listOf()),
                            stmt(),
                        ),
                    )
                },
                "Missing Key" to Test("""
                    match (any) {
                        else {:pattern}: stmt();
                    }
                """.trimIndent(), null),
                "Unmatchable Type" to Test("""
                    match (1) {
                        else {}: stmt();
                    }
                """.trimIndent(), null),
                "Multiple Varargs" to Test("""
                    match (any) {
                        else {x*, y+}: stmt();
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) {
                it.variables.define(variable("any", Type.ANY).variable)
            } }

            suite("TypedDestructure", listOf(
                "Type" to Test("""
                    match (1) {
                        else Integer: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        literal(BigInteger.parseString("1")),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.TypedDestructure(Type.INTEGER, null),
                            stmt(),
                        ),
                    )
                },
                "Pattern" to Test("""
                    match (1) {
                        else Integer name: stmt(name);
                    }
                """.trimIndent()) {
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
                },
                "Supertype Argument" to Test("""
                    match (any) {
                        else Integer: stmt();
                    }
                """.trimIndent()) {
                    RhovasIr.Statement.Match.Structural(
                        variable("any", Type.ANY),
                        listOf(),
                        Pair(
                            RhovasIr.Pattern.TypedDestructure(Type.INTEGER, null),
                            stmt(),
                        ),
                    )
                },
                "Unmatchable Type" to Test("""
                    match (1.0) {
                        else Integer: stmt();
                    }
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) {
                it.variables.define(variable("any", Type.ANY).variable)
            } }
        }

        suite("Type", listOf(
            "Type" to Test("""
                Any
            """.trimIndent()) {
                RhovasIr.Type(Type.ANY)
            },
            "Generic" to Test("""
                T
            """.trimIndent()) {
                it.types.define(Type.Generic("T", Type.ANY), "T")
                RhovasIr.Type(Type.Generic("T", Type.ANY))
            },
            "Undefined" to Test("""
                Undefined
            """.trimIndent(), null),
            "Undefined Submodule" to Test("""
                Type.Undefined
            """.trimIndent(), null),
            "Invalid Generic Receiver" to Test("""
                Invalid<String>
            """.trimIndent()) {
                it.types.define(Type.Generic("T", Type.ANY), "Invalid")
                null
            },
            "Invalid Generic Arity" to Test("""
                List<>
            """.trimIndent(), null),
            "Invalid Generic Type" to Test("""
                Equatable<Any>
            """.trimIndent(), null),
        )) { test("type", it.source, it.expected) }
    }

    private fun block(vararg statements: RhovasIr.Statement): RhovasIr.Expression.Block {
        return RhovasIr.Expression.Block(statements.toList(), null, Type.VOID)
    }

    private fun stmt(position: Int): RhovasIr.Statement {
        return stmt(literal(BigInteger.fromInt(position)))
    }

    private fun stmt(argument: RhovasIr.Expression? = null): RhovasIr.Statement {
        return RhovasIr.Statement.Expression(when (argument) {
            null -> RhovasIr.Expression.Invoke.Function(null, STMT_0, false, listOf(), Type.VOID)
            else -> RhovasIr.Expression.Invoke.Function(null, STMT_1, false, listOf(argument), Type.VOID)
        })
    }

    private fun literal(value: Any?): RhovasIr.Expression.Literal {
        return when (value) {
            null -> RhovasIr.Expression.Literal.Scalar(null, Type.NULLABLE[Type.DYNAMIC])
            is Boolean -> RhovasIr.Expression.Literal.Scalar(value, Type.BOOLEAN)
            is BigInteger -> RhovasIr.Expression.Literal.Scalar(value, Type.INTEGER)
            is BigDecimal -> RhovasIr.Expression.Literal.Scalar(value, Type.DECIMAL)
            is RhovasAst.Atom -> RhovasIr.Expression.Literal.Scalar(value, Type.ATOM)
            is String -> RhovasIr.Expression.Literal.String(listOf(value), listOf(), Type.STRING)
            else -> throw AssertionError()
        }
    }

    private fun variable(name: String, type: Type): RhovasIr.Expression.Access.Variable {
        return RhovasIr.Expression.Access.Variable(null, Variable.Declaration(name, type, false))
    }

    private fun test(rule: String, source: String, expected: ((Scope.Declaration) -> RhovasIr?)?, scope: (Scope.Declaration) -> Unit = {}) {
        val input = Input("Test", source)
        val scope = Scope.Declaration(Library.SCOPE).also(scope)
        val expected = expected?.invoke(scope)
        try {
            val ast = RhovasParser(input).parse(rule)
            val ir = RhovasAnalyzer(scope).visit(ast)
            assertEquals(expected, ir)
            assertTrue(ast.context.isNotEmpty() || input.content.isBlank())
        } catch (e: ParseException) {
            fail(input.diagnostic(e.summary, e.details, e.range, e.context))
        } catch (e: AnalyzeException) {
            if (expected != null || e.summary == "Broken analyzer invariant.") {
                fail(input.diagnostic(e.summary, e.details, e.range, e.context))
            }
        }
    }

}
