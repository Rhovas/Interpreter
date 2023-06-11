package dev.rhovas.interpreter.parser.rhovas

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.dsl.DslAst
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class RhovasParserTests: RhovasSpec() {

    data class Test<T : RhovasAst>(val source: String, val expected: (() -> T)?)

    init {
        suite("Source", listOf(
            "Empty" to Test("""
                
            """.trimIndent()) {
                RhovasAst.Source(listOf(), listOf())
            },
            "Single Import" to Test("""
                import Type;
            """.trimIndent()) {
                RhovasAst.Source(listOf(RhovasAst.Import(listOf("Type"), null)), listOf())
            },
            "Multiple Imports" to Test("""
                import First;
                import Second;
                import Third;
            """.trimIndent()) {
                RhovasAst.Source(listOf(RhovasAst.Import(listOf("First"), null), RhovasAst.Import(listOf("Second"), null), RhovasAst.Import(listOf("Third"), null)), listOf())
            },
            "Single Statement" to Test("""
                statement;
            """.trimIndent()) {
                RhovasAst.Source(listOf(), listOf(stmt("statement")))
            },
            "Multiple Statements" to Test("""
                first; second; third;
            """.trimIndent()) {
                RhovasAst.Source(listOf(), listOf(stmt("first"), stmt("second"), stmt("third")))
            },
            "Import Before Statement" to Test("""
                import Type;
                statement;
            """.trimIndent()) {
                RhovasAst.Source(listOf(RhovasAst.Import(listOf("Type"), null)), listOf(stmt("statement")))
            },
            "Import After Statement" to Test("""
                statement;
                import Type;
            """.trimIndent(), null),
        )) { test("source", it.source, it.expected) }

        suite("Import", listOf(
            "Import" to Test("""
                import Module;
            """.trimIndent()) {
                RhovasAst.Import(listOf("Module"), null)
            },
            "Submodule" to Test("""
                import Module.Type;
            """.trimIndent()) {
                RhovasAst.Import(listOf("Module", "Type"), null)
            },
            "Alias Import" to Test("""
                import Type as Alias;
            """.trimIndent()) {
                RhovasAst.Import(listOf("Type"), "Alias")
            },
            "Missing Name" to Test("""
                import ;
            """.trimIndent(), null),
            "Missing Period/As" to Test("""
                import Module Submodule;
            """.trimIndent(), null),
            "Missing Submodule Name" to Test("""
                import Module.;
            """.trimIndent(), null),
            "Missing Alias Name" to Test("""
                import Type as ;
            """.trimIndent(), null),
            "Missing Semicolon" to Test("""
                import Type
            """.trimIndent(), null),
        )) { test("source", it.source, it.expected?.let { { RhovasAst.Source(listOf(it.invoke()), listOf()) } }) }

        suite("Component") {
            suite("Struct", listOf(
                "Empty" to Test("""
                    struct Name {}
                """.trimIndent()) {
                    RhovasAst.Component.Struct("Name", listOf())
                },
                "Members" to Test("""
                    struct Name {
                        val name: Type;
                        init() {}
                        func name() {}
                    }
                """.trimIndent()) {
                    RhovasAst.Component.Struct("Name", listOf(
                        RhovasAst.Member.Property(false, "name", type("Type"), null),
                        RhovasAst.Member.Initializer(listOf(), null, listOf(), block()),
                        RhovasAst.Member.Method(RhovasAst.Statement.Declaration.Function(null, "name", listOf(), listOf(), null, listOf(), block())),
                    ))
                },
                "Missing Name" to Test("""
                    struct {}
                """.trimIndent(), null),
                "Missing Body" to Test("""
                    struct Name;
                """.trimIndent(), null),
                "Missing Closing Brace" to Test("""
                    struct Name {
                """.trimIndent(), null),
            )) { test("component", it.source, it.expected) }

            suite("Class", listOf(
                "Empty" to Test("""
                    class Name {}
                """.trimIndent()) {
                    RhovasAst.Component.Class("Name", listOf())
                },
                "Members" to Test("""
                    class Name {
                        val name: Type;
                        init() {}
                        func name() {}
                    }
                """.trimIndent()) {
                    RhovasAst.Component.Class("Name", listOf(
                        RhovasAst.Member.Property(false, "name", type("Type"), null),
                        RhovasAst.Member.Initializer(listOf(), null, listOf(), block()),
                        RhovasAst.Member.Method(RhovasAst.Statement.Declaration.Function(null, "name", listOf(), listOf(), null, listOf(), block())),
                    ))
                },
                "Missing Name" to Test("""
                    class {}
                """.trimIndent(), null),
                "Missing Body" to Test("""
                    class Name;
                """.trimIndent(), null),
                "Missing Closing Brace" to Test("""
                    class Name {
                """.trimIndent(), null),
            )) { test("component", it.source, it.expected) }
        }

        suite("Member") {
            suite("Property", listOf(
                "Immutable" to Test("""
                    val name: Type;
                """.trimIndent()) {
                    RhovasAst.Member.Property(false, "name", type("Type"), null)
                },
                "Mutable" to Test("""
                    var name: Type;
                """.trimIndent()) {
                    RhovasAst.Member.Property(true, "name", type("Type"), null)
                },
                "Value" to Test("""
                    val name: Type = value;
                """.trimIndent()) {
                    RhovasAst.Member.Property(false, "name", type("Type"), expr("value"))
                },
                "Missing Name" to Test("""
                    val;
                """.trimIndent(), null),
                "Missing Colon" to Test("""
                    val name;
                """.trimIndent(), null),
                "Missing Type" to Test("""
                    val name: ;
                """.trimIndent(), null),
                "Missing Semicolon" to Test("""
                    val name: Type
                """.trimIndent(), null),
            )) { test("member", it.source, it.expected) }

            suite("Initializer", listOf(
                "Initializer" to Test("""
                    init() {}
                """.trimIndent()) {
                    RhovasAst.Member.Initializer(listOf(), null, listOf(), block())
                },
                "Single Parameter" to Test("""
                    init(parameter) {}
                """.trimIndent()) {
                    RhovasAst.Member.Initializer(listOf("parameter" to null), null, listOf(), block())
                },
                "Multiple Parameters" to Test("""
                    init(first, second, third) {}
                """.trimIndent()) {
                    RhovasAst.Member.Initializer(listOf("first" to null, "second" to null, "third" to null), null, listOf(), block())
                },
                "Typed Parameter" to Test("""
                    init(parameter: Type) {}
                """.trimIndent()) {
                    RhovasAst.Member.Initializer(listOf("parameter" to type("Type")), null, listOf(), block())
                },
                "Trailing Comma" to Test("""
                    init(parameter,) {}
                """.trimIndent()) {
                    RhovasAst.Member.Initializer(listOf("parameter" to null), null, listOf(), block())
                },
                "Return Type" to Test("""
                    init(): Type {}
                """.trimIndent()) {
                    RhovasAst.Member.Initializer(listOf(), type("Type"), listOf(), block())
                },
                "Single Throws" to Test("""
                    init() throws Type {}
                """.trimIndent()) {
                    RhovasAst.Member.Initializer(listOf(), null, listOf(type("Type")), block())
                },
                "Multiple Throws" to Test("""
                    init() throws First, Second, Third {}
                """.trimIndent()) {
                    RhovasAst.Member.Initializer(listOf(), null, listOf(type("First"), type("Second"), type("Third")), block())
                },
                "Unsupported Name" to Test("""
                    init name() {}
                """.trimIndent(), null),
                "Unsupported Generics" to Test("""
                    init<T>() {}
                """.trimIndent(), null),
                "Missing Parenthesis" to Test("""
                    init {}
                """.trimIndent(), null),
                "Invalid Parameter Name" to Test("""
                    init(:atom) {}
                """.trimIndent(), null),
                "Missing Parameter Comma" to Test("""
                    init(first second) {}
                """.trimIndent(), null),
                "Missing Parameter Closing Parenthesis" to Test("""
                    init(argument {}
                """.trimIndent(), null),
                "Missing Return Type" to Test("""
                    init(): {}
                """.trimIndent(), null),
                "Missing Throws Type Single" to Test("""
                    init() throws {}
                """.trimIndent(), null),
                "Missing Throws Type Multiple" to Test("""
                    init() throws First, {}
                """.trimIndent(), null),
                "Missing Body" to Test("""
                    init()
                """.trimIndent(), null),
            )) { test("member", it.source, it.expected) }

            suite("Method", listOf(
                "Function" to Test("""
                    func name() {}
                """.trimIndent()) {
                    RhovasAst.Member.Method(RhovasAst.Statement.Declaration.Function(null, "name", listOf(), listOf(), null, listOf(), block()))
                },
                "This Parameter" to Test("""
                    func name(this) {}
                """.trimIndent()) {
                    RhovasAst.Member.Method(RhovasAst.Statement.Declaration.Function(null, "name", listOf(), listOf("this" to null), null, listOf(), block()))
                },
                "Operator Overload (Addition)" to Test("""
                    func op+ add(this) {}
                """.trimIndent()) {
                    RhovasAst.Member.Method(RhovasAst.Statement.Declaration.Function("+", "add", listOf(), listOf("this" to null), null, listOf(), block()))
                },
                "Operator Overload (Index)" to Test("""
                    func op[] get(this) {}
                """.trimIndent()) {
                    RhovasAst.Member.Method(RhovasAst.Statement.Declaration.Function("[]", "get", listOf(), listOf("this" to null), null, listOf(), block()))
                },
                "Operator Overload (Index Assignment)" to Test("""
                    func op[]= set(this) {}
                """.trimIndent()) {
                    RhovasAst.Member.Method(RhovasAst.Statement.Declaration.Function("[]=", "set", listOf(), listOf("this" to null), null, listOf(), block()))
                },
                "Invalid Operator Overload" to Test("""
                    func op&& equals(this) {}
                """.trimIndent(), null),
            )) { test("member", it.source, it.expected) }

            spec("Invalid") {
                test("member", """
                    import Type;
                """.trimIndent(), null)
            }
        }

        suite("Statement") {
            suite("Block", listOf(
                "Empty" to Test("""
                    {}
                """.trimIndent()) {
                    RhovasAst.Expression.Block(listOf(), null)
                },
                "Single" to Test("""
                    { statement; }
                """.trimIndent()) {
                    RhovasAst.Expression.Block(listOf(stmt("statement")), null)
                },
                "Multiple" to Test("""
                    { first; second; third; }
                """.trimIndent()) {
                    RhovasAst.Expression.Block(listOf(stmt("first"), stmt("second"), stmt("third")), null)
                },
                "Missing Semicolon" to Test("""
                    { statement }
                """.trimIndent(), null),
                "Missing Closing Brace" to Test("""
                    { statement;
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected?.let { { RhovasAst.Statement.Expression(it.invoke()) } }) }

            suite("Component", listOf(
                "Struct" to Test("""
                    struct Name {}
                """.trimIndent()) {
                    RhovasAst.Statement.Component(RhovasAst.Component.Struct("Name", listOf()))
                },
            )) { test("statement", it.source, it.expected) }

            suite("Initializer", listOf(
                "Empty" to Test("""
                    this {};
                """.trimIndent()) {
                    RhovasAst.Statement.Initializer("this", listOf(), RhovasAst.Expression.Literal.Object(listOf()))
                },
                "Field" to Test("""
                    this { field: value };
                """.trimIndent()) {
                    RhovasAst.Statement.Initializer("this", listOf(), RhovasAst.Expression.Literal.Object(listOf("field" to expr("value"))))
                },
                "Missing Semicolon" to Test("""
                    this {}
                """.trimIndent(), null),
                "Super" to Test("""
                    super {}
                """.trimIndent(), null),
                "Parameter" to Test("""
                    this(parameter) {}
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Expression", listOf(
                "Function" to Test("""
                    function();
                """.trimIndent()) {
                    RhovasAst.Statement.Expression(RhovasAst.Expression.Invoke.Function(null, "function", listOf()))
                },
                "Method" to Test("""
                    receiver.method();
                """.trimIndent()) {
                    RhovasAst.Statement.Expression(RhovasAst.Expression.Invoke.Method(expr("receiver"), false, false, "method", listOf()))
                },
                "Macro" to Test("""
                    #macro();
                """.trimIndent()) {
                    RhovasAst.Statement.Expression(RhovasAst.Expression.Invoke.Macro("macro", listOf(), null))
                },
                "Other" to Test("""
                    variable;
                """.trimIndent()) {
                    RhovasAst.Statement.Expression(RhovasAst.Expression.Access.Variable(null, "variable"))
                },
                "Missing Semicolon" to Test("""
                    expression
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Declaration") {
                suite("Variable", listOf(
                    "Val" to Test("""
                        val name;
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Variable(false, "name", null, null)
                    },
                    "Var" to Test("""
                        var name;
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Variable(true, "name", null, null)
                    },
                    "Type" to Test("""
                        val name: Type;
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Variable(false, "name", type("Type"), null)
                    },
                    "Value" to Test("""
                        var name = value;
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Variable(true, "name", null, expr("value"))
                    },
                    "Missing Value" to Test("""
                        val name = ;
                    """.trimIndent(), null),
                    "Missing Semicolon" to Test("""
                        val name
                    """.trimIndent(), null),
                )) { test("statement", it.source, it.expected) }

                suite("Function", listOf(
                    "Function" to Test("""
                        func name() {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Function(null, "name", listOf(), listOf(), null, listOf(), block())
                    },
                    "Single Generic" to Test("""
                        func name<T>() {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Function(null, "name", listOf("T" to null), listOf(), null, listOf(), block())
                    },
                    "Multiple Generics" to Test("""
                        func name<T1, T2, T3>() {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Function(null, "name", listOf("T1" to null, "T2" to null, "T3" to null), listOf(), null, listOf(), block())
                    },
                    "Bound Generic" to Test("""
                        func name<T: Bound>() {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Function(null, "name", listOf("T" to type("Bound")), listOf(), null, listOf(), block())
                    },
                    "Single Parameter" to Test("""
                        func name(parameter) {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Function(null, "name", listOf(), listOf("parameter" to null), null, listOf(), block())
                    },
                    "Multiple Parameters" to Test("""
                        func name(first, second, third) {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Function(null, "name", listOf(), listOf("first" to null, "second" to null, "third" to null), null, listOf(), block())
                    },
                    "Typed Parameter" to Test("""
                        func name(parameter: Type) {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Function(null, "name", listOf(), listOf("parameter" to type("Type")), null, listOf(), block())
                    },
                    "Trailing Comma" to Test("""
                        func name(parameter,) {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Function(null, "name", listOf(), listOf("parameter" to null), null, listOf(), block())
                    },
                    "Return Type" to Test("""
                        func name(): Type {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Function(null, "name", listOf(), listOf(), type("Type"), listOf(), block())
                    },
                    "Single Throws" to Test("""
                        func name() throws Type {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Function(null, "name", listOf(), listOf(), null, listOf(type("Type")), block())
                    },
                    "Multiple Throws" to Test("""
                        func name() throws First, Second, Third {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Declaration.Function(null, "name", listOf(), listOf(), null, listOf(type("First"), type("Second"), type("Third")), block())
                    },
                    "Missing Name" to Test("""
                        func () {}
                    """.trimIndent(), null),
                    "Invalid Generic Name" to Test("""
                        func name<?>() {}
                    """.trimIndent(), null),
                    "Missing Generic Colon" to Test("""
                        func name<T Bound>() {}
                    """.trimIndent(), null),
                    "Missing Parenthesis" to Test("""
                        func name {}
                    """.trimIndent(), null),
                    "Invalid Parameter Name" to Test("""
                        func name(:atom) {}
                    """.trimIndent(), null),
                    "Missing Parameter Comma" to Test("""
                        func name(first second) {}
                    """.trimIndent(), null),
                    "Missing Parameter Closing Parenthesis" to Test("""
                        func name(argument {}
                    """.trimIndent(), null),
                    "Missing Return Type" to Test("""
                        func name(): {}
                    """.trimIndent(), null),
                    "Missing Throws Type Single" to Test("""
                        func name() throws {}
                    """.trimIndent(), null),
                    "Missing Throws Type Multiple" to Test("""
                        func name() throws First, {}
                    """.trimIndent(), null),
                    "Missing Body" to Test("""
                        func name()
                    """.trimIndent(), null),
                )) { test("statement", it.source, it.expected) }
            }

            suite("Assignment", listOf(
                "Variable" to Test("""
                    variable = value;
                """.trimIndent()) {
                    RhovasAst.Statement.Assignment(expr("variable"), expr("value"))
                },
                "Property" to Test("""
                    receiver.property = value;
                """.trimIndent()) {
                    RhovasAst.Statement.Assignment(RhovasAst.Expression.Access.Property(expr("receiver"), false, "property"), expr("value"))
                },
                "Index" to Test("""
                    receiver[] = value;
                """.trimIndent()) {
                    RhovasAst.Statement.Assignment(RhovasAst.Expression.Access.Index(expr("receiver"), false, listOf()), expr("value"))
                },
                "Other" to Test("""
                    function() = value;
                """.trimIndent()) {
                    RhovasAst.Statement.Assignment(RhovasAst.Expression.Invoke.Function(null, "function", listOf()), expr("value"))
                },
                "Missing Equals" to Test("""
                    variable value;
                """.trimIndent(), null),
                "Missing Value" to Test("""
                    variable = ;
                """.trimIndent(), null),
                "Missing Semicolon" to Test("""
                    variable = value
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("If", listOf(
                "Then" to Test("""
                    if (condition) { stmt; }
                """.trimIndent()) {
                    RhovasAst.Statement.If(expr("condition"), block(stmt("stmt")), null)
                },
                "Else" to Test("""
                    if (condition) {} else { stmt; }
                """.trimIndent()) {
                    RhovasAst.Statement.If(expr("condition"), block(), block(stmt("stmt")))
                },
                "Missing Opening Parenthesis" to Test("""
                    if condition) {}
                """.trimIndent(), null),
                "Missing Condition" to Test("""
                    if () {}
                """.trimIndent(), null),
                "Missing Closing Parenthesis" to Test("""
                    if (condition {}
                """.trimIndent(), null),
                "Missing Else" to Test("""
                    if (condition) {} {}
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Match") {
                suite("Conditional", listOf(
                    "Empty" to Test("""
                        match {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Match.Conditional(listOf(), null)
                    },
                    "Single" to Test("""
                        match { condition: statement; }
                    """.trimIndent()) {
                        RhovasAst.Statement.Match.Conditional(listOf(expr("condition") to stmt("statement")), null)
                    },
                    "Multiple " to Test("""
                        match { c1: s1; c2: s2; c3: s3; }
                    """.trimIndent()) {
                        RhovasAst.Statement.Match.Conditional(listOf(expr("c1") to stmt("s1"), expr("c2") to stmt("s2"), expr("c3") to stmt("s3")), null)
                    },
                    "Else" to Test("""
                        match { else: statement; }
                    """.trimIndent()) {
                        RhovasAst.Statement.Match.Conditional(listOf(), null to stmt("statement"))
                    },
                    "Else Condition" to Test("""
                        match { else condition: statement; }
                    """.trimIndent()) {
                        RhovasAst.Statement.Match.Conditional(listOf(), expr("condition") to stmt("statement"))
                    },
                    "Else With Cases" to Test("""
                        match { c1: s1; c2: s2; else: s3; }
                    """.trimIndent()) {
                        RhovasAst.Statement.Match.Conditional(listOf(expr("c1") to stmt("s1"), expr("c2") to stmt("s2")), null to stmt("s3"))
                    },
                    "Else Inner" to Test("""
                        match { c1: s2; else: s2; c3: s3; }
                    """.trimIndent(), null),
                    "Else Multiple" to Test("""
                        match { else: s1; else: s2; }
                    """.trimIndent(), null),
                    "Missing Opening Brace" to Test("""
                        match }
                    """.trimIndent(), null),
                    "Missing Condition" to Test("""
                        match { : statement; }
                    """.trimIndent(), null), //parses : statement as an Atom
                    "Missing Colon" to Test("""
                        match { condition statement; }
                    """.trimIndent(), null),
                    "Missing Statement" to Test("""
                        match { condition }
                    """.trimIndent(), null),
                    "Missing Else Colon" to Test("""
                        match { else statement; }
                    """.trimIndent(), null), //parses statement as the condition
                    "Missing Else Statement" to Test("""
                        match { else: }
                    """.trimIndent(), null),
                )) { test("statement", it.source, it.expected) }

                suite("Structural", listOf(
                    "Empty" to Test("""
                        match (argument) {}
                    """.trimIndent()) {
                        RhovasAst.Statement.Match.Structural(expr("argument"), listOf(), null)
                    },
                    "Single" to Test("""
                        match (argument) { pattern: statement; }
                    """.trimIndent()) {
                        RhovasAst.Statement.Match.Structural(expr("argument"), listOf(RhovasAst.Pattern.Variable("pattern") to stmt("statement")), null)
                    },
                    "Multiple " to Test("""
                        match (argument) { p1: s1; p2: s2; p3: s3; }
                    """.trimIndent()) {
                        RhovasAst.Statement.Match.Structural(expr("argument"), listOf(
                            RhovasAst.Pattern.Variable("p1") to stmt("s1"),
                            RhovasAst.Pattern.Variable("p2") to stmt("s2"),
                            RhovasAst.Pattern.Variable("p3") to stmt("s3"),
                        ), null)
                    },
                    "Else" to Test("""
                        match (argument) { else: statement; }
                    """.trimIndent()) {
                        RhovasAst.Statement.Match.Structural(expr("argument"), listOf(), null to stmt("statement"))
                    },
                    "Else Condition" to Test("""
                        match (argument) { else pattern: statement; }
                    """.trimIndent()) {
                        RhovasAst.Statement.Match.Structural(expr("argument"), listOf(), RhovasAst.Pattern.Variable("pattern") to stmt("statement"))
                    },
                    "Else With Cases" to Test("""
                        match (argument) { p1: s1; p2: s2; else: s3; }
                    """.trimIndent()) {
                        RhovasAst.Statement.Match.Structural(expr("argument"), listOf(
                            RhovasAst.Pattern.Variable("p1") to stmt("s1"),
                            RhovasAst.Pattern.Variable("p2") to stmt("s2"),
                        ), null to stmt("s3"))
                    },
                    "Else Inner" to Test("""
                        match (argument) { p1: s2; else: s2; p3: s3; }
                    """.trimIndent(), null),
                    "Else Multiple" to Test("""
                        match (argument) { else: s1; else: s2; }
                    """.trimIndent(), null),
                    "Missing Closing Parenthesis" to Test("""
                        match (argument {}
                    """.trimIndent(), null),
                    "Missing Opening Brace" to Test("""
                        match (argument) }
                    """.trimIndent(), null),
                    "Missing Pattern" to Test("""
                        match (argument) { : statement; }
                    """.trimIndent(), null), //parses : statement as an Atom
                    "Missing Colon" to Test("""
                        match (argument) { pattern statement; }
                    """.trimIndent(), null),
                    "Missing Statement" to Test("""
                        match (argument) { pattern }
                    """.trimIndent(), null),
                    "Missing Else Colon" to Test("""
                        match (argument) { else statement; }
                    """.trimIndent(), null), //parses statement as the pattern
                    "Missing Else Statement" to Test("""
                        match (argument) { else: }
                    """.trimIndent(), null),
                )) { test("statement", it.source, it.expected) }
            }

            suite("For", listOf(
                "For" to Test("""
                    for (val name in iterable) { stmt; }
                """.trimIndent()) {
                    RhovasAst.Statement.For("name", expr("iterable"), block(stmt("stmt")))
                },
                "Missing Opening Parenthesis" to Test("""
                    for val name in iterable) {}
                """.trimIndent(), null),
                "Missing Val" to Test("""
                    for (name in iterable) {}
                """.trimIndent(), null),
                "Missing Name" to Test("""
                    for (val in iterable) {}
                """.trimIndent(), null),
                "Invalid Name" to Test("""
                    for (val :atom in iterable) {}
                """.trimIndent(), null),
                "Missing In" to Test("""
                    for (val name iterable) {}
                """.trimIndent(), null),
                "Missing Iterable" to Test("""
                    for (val name in) {}
                """.trimIndent(), null),
                "Missing Closing Parenthesis" to Test("""
                    for (val name in iterable {}
                """.trimIndent(), null),
                "Missing Statement" to Test("""
                    for (val name in iterable)
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("While", listOf(
                "While" to Test("""
                    while (condition) { stmt; }
                """.trimIndent()) {
                    RhovasAst.Statement.While(expr("condition"), block(stmt("stmt")))
                },
                "Missing Opening Parenthesis" to Test("""
                    while condition) {}
                """.trimIndent(), null),
                "Missing Condition" to Test("""
                    while () {}
                """.trimIndent(), null),
                "Missing Closing Parenthesis" to Test("""
                    while (condition {}
                """.trimIndent(), null),
                "Missing Statement" to Test("""
                    while (condition)
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Try", listOf(
                "Try" to Test("""
                    try { stmt; }
                """.trimIndent()) {
                    RhovasAst.Statement.Try(block(stmt("stmt")), listOf(), null)
                },
                "Catch" to Test("""
                    try {} catch (val name: Type) { stmt; }
                """.trimIndent()) {
                    RhovasAst.Statement.Try(block(), listOf(RhovasAst.Statement.Try.Catch("name", type("Type"), block(stmt("stmt")))), null)
                },
                "Multiple Catch" to Test("""
                    try {}
                    catch (val first: First) {}
                    catch (val second: Second) {}
                    catch (val third: Third) {}
                """.trimIndent()) {
                    RhovasAst.Statement.Try(block(), listOf(
                        RhovasAst.Statement.Try.Catch("first", type("First"), block()),
                        RhovasAst.Statement.Try.Catch("second", type("Second"), block()),
                        RhovasAst.Statement.Try.Catch("third", type("Third"), block()),
                    ), null)
                },
                "Finally" to Test("""
                    try {} finally { stmt; }
                """.trimIndent()) {
                    RhovasAst.Statement.Try(block(), listOf(), block(stmt("stmt")))
                },
                "Both Catch & Finally" to Test("""
                    try {} catch (val name: Type) {} finally {}
                """.trimIndent()) {
                    RhovasAst.Statement.Try(block(), listOf(RhovasAst.Statement.Try.Catch("name", type("Type"), block())), block())
                },
                "Missing Try Statement" to Test("""
                    try
                """.trimIndent(), null),
                "Missing Catch Opening Parenthesis" to Test("""
                    try {} catch val name: Type) {}
                """.trimIndent(), null),
                "Missing Catch Val" to Test("""
                    try {} catch (name: Type) {}
                """.trimIndent(), null),
                "Missing Catch Name" to Test("""
                    try {} catch (val) {}
                """.trimIndent(), null),
                "Missing Catch Type" to Test("""
                    try {} catch (val name) {}
                """.trimIndent(), null),
                "Missing Catch Closing Parenthesis" to Test("""
                    try {} catch (val name: Type {}
                """.trimIndent(), null),
                "Missing Catch Statement" to Test("""
                    try {} catch (val name: Type)
                """.trimIndent(), null),
                "Missing Finally Statement" to Test("""
                    try {} finally
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("With", listOf(
                "With" to Test("""
                    with (argument) {}
                """.trimIndent()) {
                    RhovasAst.Statement.With(null, expr("argument"), block())
                },
                "Name" to Test("""
                    with (val name = argument) {}
                """.trimIndent()) {
                    RhovasAst.Statement.With("name", expr("argument"), block())
                },
                "Missing Opening Parenthesis" to Test("""
                    with argument) {}
                """.trimIndent(), null),
                "Missing Val" to Test("""
                    with (name = argument) {}
                """.trimIndent(), null),
                "Missing Name" to Test("""
                    with (val = argument) {}
                """.trimIndent(), null),
                "Missing Equals" to Test("""
                    with (val name argument) {}
                """.trimIndent(), null),
                "Missing Argument" to Test("""
                    with () {}
                """.trimIndent(), null),
                "Missing Closing Parenthesis" to Test("""
                    with (argument {}
                """.trimIndent(), null),
                "Missing Statement" to Test("""
                    with (argument)
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Label", listOf(
                "Label" to Test("""
                    label: statement;
                """.trimIndent()) {
                    RhovasAst.Statement.Label("label", stmt("statement"))
                },
                "Loop" to Test("""
                    label: while (condition) {}
                """.trimIndent()) {
                    RhovasAst.Statement.Label("label", RhovasAst.Statement.While(expr("condition"), block()))
                },
                "Missing Statement" to Test("""
                    label:
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Break", listOf(
                "Break" to Test("""
                    break;
                """.trimIndent()) {
                    RhovasAst.Statement.Break(null)
                },
                "Label" to Test("""
                    break label;
                """.trimIndent()) {
                    RhovasAst.Statement.Break("label")
                },
                "Missing Semicolon" to Test("""
                    break
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Continue", listOf(
                "Continue" to Test("""
                    continue;
                """.trimIndent()) {
                    RhovasAst.Statement.Continue(null)
                },
                "Label" to Test("""
                    continue label;
                """.trimIndent()) {
                    RhovasAst.Statement.Continue("label")
                },
                "Missing Semicolon" to Test("""
                    continue
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Return", listOf(
                "Return" to Test("""
                    return;
                """.trimIndent()) {
                    RhovasAst.Statement.Return(null)
                },
                "Return Value" to Test("""
                    return value;
                """.trimIndent()) {
                    RhovasAst.Statement.Return(expr("value"))
                },
                "Missing Semicolon" to Test("""
                    return value
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Throw", listOf(
                "Throw" to Test("""
                    throw exception;
                """.trimIndent()) {
                    RhovasAst.Statement.Throw(expr("exception"))
                },
                "Missing Exception" to Test("""
                    throw;
                """.trimIndent(), null),
                "Missing Semicolon" to Test("""
                    throw exception
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Assert", listOf(
                "Assert" to Test("""
                    assert condition;
                """.trimIndent()) {
                    RhovasAst.Statement.Assert(expr("condition"), null)
                },
                "Message" to Test("""
                    assert condition: message;
                """.trimIndent()) {
                    RhovasAst.Statement.Assert(expr("condition"), expr("message"))
                },
                "Missing Condition" to Test("""
                    assert;
                """.trimIndent(), null),
                "Missing Colon" to Test("""
                    assert condition message;
                """.trimIndent(), null),
                "Missing Message" to Test("""
                    assert condition: ;
                """.trimIndent(), null),
                "Missing Semicolon" to Test("""
                    assert condition
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Require", listOf(
                "Require" to Test("""
                    require condition;
                """.trimIndent()) {
                    RhovasAst.Statement.Require(expr("condition"), null)
                },
                "Message" to Test("""
                    require condition: message;
                """.trimIndent()) {
                    RhovasAst.Statement.Require(expr("condition"), expr("message"))
                },
                "Missing Condition" to Test("""
                    require;
                """.trimIndent(), null),
                "Missing Colon" to Test("""
                    require condition message;
                """.trimIndent(), null),
                "Missing Message" to Test("""
                    require condition: ;
                """.trimIndent(), null),
                "Missing Semicolon" to Test("""
                    require condition
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }

            suite("Ensure", listOf(
                "Ensure" to Test("""
                    ensure condition;
                """.trimIndent()) {
                    RhovasAst.Statement.Ensure(expr("condition"), null)
                },
                "Message" to Test("""
                    ensure condition: message;
                """.trimIndent()) {
                    RhovasAst.Statement.Ensure(expr("condition"), expr("message"))
                },
                "Missing Condition" to Test("""
                    ensure;
                """.trimIndent(), null),
                "Missing Colon" to Test("""
                    ensure condition message;
                """.trimIndent(), null),
                "Missing Message" to Test("""
                    ensure condition: ;
                """.trimIndent(), null),
                "Missing Semicolon" to Test("""
                    ensure condition
                """.trimIndent(), null),
            )) { test("statement", it.source, it.expected) }
        }

        suite("Expression") {
            suite("Block", listOf(
                "Empty" to Test("""
                    do {}
                """.trimIndent()) {
                    RhovasAst.Expression.Block(listOf(), null)
                },
                "Single" to Test("""
                    do { statement; }
                """.trimIndent()) {
                    RhovasAst.Expression.Block(listOf(stmt("statement")), null)
                },
                "Multiple" to Test("""
                    do { first; second; third; }
                """.trimIndent()) {
                    RhovasAst.Expression.Block(listOf(stmt("first"), stmt("second"), stmt("third")), null)
                },
                "Expression" to Test("""
                    do { expression }
                """.trimIndent()) {
                    RhovasAst.Expression.Block(listOf(), expr("expression"))
                },
                "Missing Semicolon" to Test("""
                    do { expression expression }
                """.trimIndent(), null),
                "Missing Closing Brace" to Test("""
                    do { statement;
                """.trimIndent(), null),
            )) { test("expression", it.source, it.expected) }

            suite("Literal") {
                suite("Scalar", listOf(
                    "Null" to Test("""
                        null
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Scalar(null)
                    },
                    "Boolean True" to Test("""
                        true
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Scalar(true)
                    },
                    "Boolean False" to Test("""
                        false
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Scalar(false)
                    },
                    "Integer" to Test("""
                        123
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Scalar(BigInteger.parseString("123"))
                    },
                    "Integer Above Long Max" to Test("""
                        1${"0".repeat(19)}
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Scalar(BigInteger.parseString("1" + "0".repeat(19)))
                    },
                    "Decimal" to Test("""
                        123.456
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Scalar(BigDecimal.parseString("123.456"))
                    },
                    "Decimal Above Double Max" to Test("""
                        1${"0".repeat(308)}.0
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Scalar(BigDecimal.parseString("1${"0".repeat(308)}.0"))
                    },
                    "Atom" to Test("""
                        :atom
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Scalar(RhovasAst.Atom("atom"))
                    },
                )) { test("expression", it.source, it.expected) }

                suite("String", listOf(
                    "Empty" to Test("""
                        ""
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.String(listOf(""), listOf())
                    },
                    "String" to Test("""
                        "string"
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.String(listOf("string"), listOf())
                    },
                    "Interpolation" to Test("""
                        "start${'$'}{argument}end"
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.String(listOf("start", "end"), listOf(expr("argument")))
                    },
                    "Interpolation Multiple" to Test("""
                        "start${'$'}{first}middle${'$'}{second}end"
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.String(listOf("start", "middle", "end"), listOf(expr("first"), expr("second")))
                    },
                    "Interpolation Only" to Test("""
                        "${'$'}{argument}"
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.String(listOf("", ""), listOf(expr("argument")))
                    },
                    "Interpolation Only Multiple" to Test("""
                        "${'$'}{first}${'$'}{second}"
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.String(listOf("", "", ""), listOf(expr("first"), expr("second")))
                    },
                    "Unterminated" to Test("""
                        "unterminated
                    """.trimIndent(), null),
                    "Unterminated Newline" to Test("""
                        "unterminated
                    """.trimIndent(), null),
                    "Unterminated Interpolation" to Test("""
                        "start${'$'}{argumentend"
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("List", listOf(
                    "Empty" to Test("""
                        []
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.List(listOf())
                    },
                    "Single" to Test("""
                        [element]
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.List(listOf(expr("element")))
                    },
                    "Multiple" to Test("""
                        [first, second, third]
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.List(listOf(expr("first"), expr("second"), expr("third")))
                    },
                    "Trailing Comma" to Test("""
                        [first, second,]
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.List(listOf(expr("first"), expr("second")))
                    },
                    "Missing Comma" to Test("""
                        [first second]
                    """.trimIndent(), null),
                    "Missing Closing Bracket" to Test("""
                        [element
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Object", listOf(
                    "Empty" to Test("""
                        {}
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Object(listOf())
                    },
                    "Single" to Test("""
                        {key: value}
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Object(listOf("key" to expr("value")))
                    },
                    "Multiple" to Test("""
                        {k1: v1, k2: v2, k3: v3}
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Object(listOf("k1" to expr("v1"), "k2" to expr("v2"), "k3" to expr("v3")))
                    },
                    "Trailing Comma" to Test("""
                        {k1: v1, k2: v2,}
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Object(listOf("k1" to expr("v1"), "k2" to expr("v2")))
                    },
                    "Key Only" to Test("""
                        {key}
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Object(listOf("key" to expr("key")))
                    },
                    "Invalid Key" to Test("""
                        {"key": value}
                    """.trimIndent(), null),
                    "Missing Key" to Test("""
                        {: value}
                    """.trimIndent(), null),
                    "Missing Colon" to Test("""
                        {key value}
                    """.trimIndent(), null),
                    "Missing Comma" to Test("""
                        {k1: v1 k2: v2}
                    """.trimIndent(), null),
                    "Missing Closing Bracket" to Test("""
                        {key: value
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Type", listOf(
                    "Type" to Test("""
                        Type
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Type(type("Type"))
                    },
                    "Nesting" to Test("""
                        First.Second.Third
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Type(RhovasAst.Type(listOf("First", "Second", "Third"), null))
                    },
                    "Generic" to Test("""
                        Type<Generic>
                    """.trimIndent()) {
                        RhovasAst.Expression.Literal.Type(RhovasAst.Type(listOf("Type"), listOf(type("Generic"))))
                    },
                )) { test("expression", it.source, it.expected) }
            }

            suite("Group", listOf(
                "Group" to Test("""
                    (expression)
                """.trimIndent()) {
                    RhovasAst.Expression.Group(expr("expression"))
                },
                "Nested" to Test("""
                    ((expression))
                """.trimIndent()) {
                    RhovasAst.Expression.Group(RhovasAst.Expression.Group(expr("expression")))
                },
                "Binary" to Test("""
                    (first + second)
                """.trimIndent()) {
                    RhovasAst.Expression.Group(RhovasAst.Expression.Binary("+", expr("first"), expr("second")))
                },
                "Empty" to Test("""
                    ()
                """.trimIndent(), null),
                "Tuple" to Test("""
                    (first, second)
                """.trimIndent(), null),
                "Missing Closing Parenthesis" to Test("(expression", null),
            )) { test("expression", it.source, it.expected) }

            suite("Unary", listOf(
                "Numerical Negation" to Test("""
                    -expression
                """.trimIndent()) {
                    RhovasAst.Expression.Unary("-", expr("expression"))
                },
                "Logical Negation" to Test("""
                    !expression
                """.trimIndent()) {
                    RhovasAst.Expression.Unary("!", expr("expression"))
                },
                "Multiple" to Test("""
                    -!expression
                """.trimIndent()) {
                    RhovasAst.Expression.Unary("-", RhovasAst.Expression.Unary("!", expr("expression")))
                },
                "Invalid Operator" to Test("""
                    +expression
                """.trimIndent(), null),
            )) { test("expression", it.source, it.expected) }

            suite("Binary", listOf(
                "Multiplicative" to Test("""
                    left * right
                """.trimIndent()) {
                    RhovasAst.Expression.Binary("*", expr("left"), expr("right"))
                },
                "Additive" to Test("""
                    left + right
                """.trimIndent()) {
                    RhovasAst.Expression.Binary("+", expr("left"), expr("right"))
                },
                "Comparison" to Test("""
                    left < right
                """.trimIndent()) {
                    RhovasAst.Expression.Binary("<", expr("left"), expr("right"))
                },
                "Equality" to Test("""
                    left == right
                """.trimIndent()) {
                    RhovasAst.Expression.Binary("==", expr("left"), expr("right"))
                },
                "Logical And" to Test("""
                    left && right
                """.trimIndent()) {
                    RhovasAst.Expression.Binary("&&", expr("left"), expr("right"))
                },
                "Logical Or" to Test("""
                    left || right
                """.trimIndent()) {
                    RhovasAst.Expression.Binary("||", expr("left"), expr("right"))
                },
                "Left Precedence" to Test("""
                    first * second + third < fourth == fifth && sixth || seventh
                """.trimIndent()) {
                    RhovasAst.Expression.Binary("||",
                        RhovasAst.Expression.Binary("&&",
                            RhovasAst.Expression.Binary("==",
                                RhovasAst.Expression.Binary("<",
                                    RhovasAst.Expression.Binary("+",
                                        RhovasAst.Expression.Binary("*",
                                            expr("first"),
                                            expr("second"),
                                        ),
                                        expr("third"),
                                    ),
                                    expr("fourth"),
                                ),
                                expr("fifth"),
                            ),
                            expr("sixth"),
                        ),
                        expr("seventh"),
                    )
                },
                "Right Precedence" to Test("""
                    first || second && third == fourth < fifth + sixth * seventh
                """.trimIndent()) {
                    RhovasAst.Expression.Binary("||",
                        expr("first"),
                        RhovasAst.Expression.Binary("&&",
                            expr("second"),
                            RhovasAst.Expression.Binary("==",
                                expr("third"),
                                RhovasAst.Expression.Binary("<",
                                    expr("fourth"),
                                    RhovasAst.Expression.Binary("+",
                                        expr("fifth"),
                                        RhovasAst.Expression.Binary("*",
                                            expr("sixth"),
                                            expr("seventh"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    )
                },
                "Equal Precedence" to Test("""
                    first < second <= third
                """.trimIndent()) {
                    RhovasAst.Expression.Binary("<=",
                        RhovasAst.Expression.Binary("<",
                            expr("first"),
                            expr("second"),
                        ),
                        expr("third"),
                    )
                },
                "Invalid Operator" to Test("""
                    first % second
                """.trimIndent(), null),
                "Missing Operator" to Test("""
                    first second
                """.trimIndent(), null),
                "Missing Right" to Test("""
                    first +
                """.trimIndent(), null),
            )) { test("expression", it.source, it.expected) }

            suite("Access") {
                suite("Variable", listOf(
                    "Variable" to Test("""
                        variable
                    """.trimIndent()) {
                        RhovasAst.Expression.Access.Variable(null, "variable")
                    },
                    "Underscore" to Test("""
                        _
                    """.trimIndent()) {
                        RhovasAst.Expression.Access.Variable(null, "_")
                    },
                )) { test("expression", it.source, it.expected) }

                suite("Property", listOf(
                    "Property" to Test("""
                        receiver.property
                    """.trimIndent()) {
                        RhovasAst.Expression.Access.Property(expr("receiver"),false, "property")
                    },
                    "Multiple Properties" to Test("""
                        receiver.first.second.third
                    """.trimIndent()) {
                        RhovasAst.Expression.Access.Property(
                            RhovasAst.Expression.Access.Property(
                                RhovasAst.Expression.Access.Property(
                                    expr("receiver"), false, "first"
                                ), false, "second",
                            ), false, "third",
                        )
                    },
                    "Nullable" to Test("""
                        receiver?.property
                    """.trimIndent()) {
                        RhovasAst.Expression.Access.Property(expr("receiver"), true, "property")
                    },
                    "Element" to Test("""
                        receiver.0
                    """.trimIndent()) {
                        RhovasAst.Expression.Access.Property(expr("receiver"), false, "0")
                    },
                    "Missing Identifier" to Test("""
                        receiver.
                    """.trimIndent(), null),
                    "Cascade" to Test("""
                        receiver..property
                    """.trimIndent(), null),
                    "Element Cascade" to Test("""
                        receiver..0
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Index", listOf(
                    "Zero Arguments" to Test("""
                        receiver[]
                    """.trimIndent()) {
                        RhovasAst.Expression.Access.Index(expr("receiver"), false, listOf())
                    },
                    "Single Argument" to Test("""
                        receiver[argument]
                    """.trimIndent()) {
                        RhovasAst.Expression.Access.Index(expr("receiver"), false, listOf(expr("argument")))
                    },
                    "Multiple Arguments" to Test("""
                        receiver[first, second, third]
                    """.trimIndent()) {
                        RhovasAst.Expression.Access.Index(expr("receiver"), false, listOf(expr("first"), expr("second"), expr("third")))
                    },
                    "Multiple Indexes" to Test("""
                        receiver[first][second][third]
                    """.trimIndent()) {
                        RhovasAst.Expression.Access.Index(
                            RhovasAst.Expression.Access.Index(
                                RhovasAst.Expression.Access.Index(
                                    expr("receiver"),
                                    false,
                                    listOf(expr("first")),
                                ),
                                false,
                                listOf(expr("second")),
                            ),
                            false,
                            listOf(expr("third")),
                        )
                    },
                    "Trailing Comma" to Test("""
                        receiver[argument,]
                    """.trimIndent()) {
                        RhovasAst.Expression.Access.Index(expr("receiver"), false, listOf(expr("argument")))
                    },
                    "Coalesce" to Test("""
                        receiver?[]
                    """.trimIndent()) {
                        RhovasAst.Expression.Access.Index(expr("receiver"), true, listOf())
                    },
                    "Missing Comma" to Test("""
                        receiver[first second]
                    """.trimIndent(), null),
                    "Missing Closing Bracket" to Test("""
                        receiver[argument
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }
            }

            suite("Invoke") {
                suite("Constructor", listOf(
                    "Qualifier" to Test("""
                        Qualifier.Type()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Constructor(RhovasAst.Type(listOf("Qualifier", "Type"), null), listOf())
                    },
                    "Generics" to Test("""
                        Type<Generic>()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Constructor(RhovasAst.Type(listOf("Type"), listOf(type("Generic"))), listOf())
                    },
                    "Zero Arguments" to Test("""
                        Type()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Constructor(type("Type"), listOf())
                    },
                    "Single Argument" to Test("""
                        Type(argument)
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Constructor(type("Type"), listOf(expr("argument")))
                    },
                    "Multiple Arguments" to Test("""
                        Type(first, second, third)
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Constructor(type("Type"), listOf(expr("first"), expr("second"), expr("third")))
                    },
                    "Trailing Comma" to Test("""
                        Type(argument,)
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Constructor(type("Type"), listOf(expr("argument")))
                    },
                    "Missing Comma" to Test("""
                        Type(first second)
                    """.trimIndent(), null),
                    "Missing Closing Parenthesis" to Test("""
                        Type(argument
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Function", listOf(
                    "Qualifier" to Test("""
                        Qualifier.function()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Function(type("Qualifier"), "function", listOf())
                    },
                    "Zero Arguments" to Test("""
                        function()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Function(null, "function", listOf())
                    },
                    "Single Argument" to Test("""
                        function(argument)
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Function(null, "function", listOf(expr("argument")))
                    },
                    "Multiple Arguments" to Test("""
                        function(first, second, third)
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Function(null, "function", listOf(expr("first"), expr("second"), expr("third")))
                    },
                    "Trailing Comma" to Test("""
                        function(argument,)
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Function(null, "function", listOf(expr("argument")))
                    },
                    "Missing Comma" to Test("""
                        function(first second)
                    """.trimIndent(), null),
                    "Missing Closing Parenthesis" to Test("""
                        function(argument
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Method", listOf(
                    "Zero Arguments" to Test("""
                        receiver.method()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Method(expr("receiver"), false, false, "method", listOf())
                    },
                    "Single Argument" to Test("""
                        receiver.method(argument)
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Method(expr("receiver"), false, false, "method", listOf(expr("argument")))
                    },
                    "Multiple Arguments" to Test("""
                        receiver.method(first, second, third)
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Method(expr("receiver"), false, false, "method", listOf(expr("first"), expr("second"), expr("third")))
                    },
                    "Trailing Comma" to Test("""
                        receiver.method(argument,)
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Method(expr("receiver"), false, false, "method", listOf(expr("argument")))
                    },
                    "Multiple Methods" to Test("""
                        receiver.first().second().third()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Method(
                            RhovasAst.Expression.Invoke.Method(
                                RhovasAst.Expression.Invoke.Method(
                                    expr("receiver"), false, false, "first", listOf(),
                                ), false, false, "second", listOf(),
                            ), false, false, "third", listOf(),
                        )
                    },
                    "Coalesce" to Test("""
                        receiver?.method()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Method(expr("receiver"), true, false, "method", listOf())
                    },
                    "Cascade" to Test("""
                        receiver..method()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Method(expr("receiver"), false, true, "method", listOf())
                    },
                    "Coalesce & Cascade" to Test("""
                        receiver?..method()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Method(expr("receiver"), true, true, "method", listOf())
                    },
                    "Missing Comma" to Test("""
                        receiver.method(first second)
                    """.trimIndent(), null),
                    "Missing Closing Parenthesis" to Test("""
                        receiver.method(argument
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Pipeline", listOf(
                    "Pipeline" to Test("""
                        receiver.|function()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Pipeline(expr("receiver"), false, false, null, "function", listOf())
                    },
                    "Coalesce" to Test("""
                        receiver?.|function()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Pipeline(expr("receiver"), true, false, null, "function", listOf())
                    },
                    "Cascade" to Test("""
                        receiver..|function()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Pipeline(expr("receiver"), false, true, null, "function", listOf())
                    },
                    "Coalesce & Cascade" to Test("""
                        receiver?..|function()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Pipeline(expr("receiver"), true, true, null, "function", listOf())
                    },
                    "Qualifier" to Test("""
                        receiver.|Qualifier.function()
                    """.trimIndent()) {
                        RhovasAst.Expression.Invoke.Pipeline(expr("receiver"), false, false, type("Qualifier"), "function", listOf())
                    },
                    "Missing Name" to Test("""
                        receiver.|()
                    """.trimIndent(), null),
                    "Missing Qualifier Separator" to Test("""
                        receiver.|Qualifier.Type()
                    """.trimIndent(), null),
                    "Missing Qualified Name" to Test("""
                        receiver.|Qualifier.()
                    """.trimIndent(), null),
                    "Missing Invocation" to Test("""
                        receiver.|function
                    """.trimIndent(), null),
                )) { test("expression", it.source, it.expected) }

                suite("Macro") {
                    suite("Function", listOf(
                        "Zero Arguments" to Test("""
                            #macro()
                        """.trimIndent()) {
                            RhovasAst.Expression.Invoke.Macro("macro", listOf(), null)
                        },
                        "Single Argument" to Test("""
                            #macro(argument)
                        """.trimIndent()) {
                            RhovasAst.Expression.Invoke.Macro("macro", listOf(expr("argument")), null)
                        },
                        "Multiple Arguments" to Test("""
                            #macro(first, second, third)
                        """.trimIndent()) {
                            RhovasAst.Expression.Invoke.Macro("macro", listOf(expr("first"), expr("second"), expr("third")), null)
                        },
                        "Trailing Comma" to Test("""
                            #macro(argument,)
                        """.trimIndent()) {
                            RhovasAst.Expression.Invoke.Macro("macro", listOf(expr("argument")), null)
                        },
                        "Missing Opening Parenthesis" to Test("""
                            #macro
                        """.trimIndent(), null),
                        "Missing Comma" to Test("""
                            #macro(first second)
                        """.trimIndent(), null),
                        "Missing Closing Parenthesis" to Test("""
                            #macro(argument
                        """.trimIndent(), null),
                    )) { test("expression", it.source, it.expected) }

                    suite("DSL", listOf(
                        "Inline" to Test("""
                            #macro { source }
                        """.trimIndent()) {
                            RhovasAst.Expression.Invoke.Macro("macro", listOf(), DslAst.Source(listOf(" source "), listOf()))
                        },
                        "Multiline" to Test("""
                            #macro {
                                source
                            }
                        """.trimIndent()) {
                            RhovasAst.Expression.Invoke.Macro("macro", listOf(), DslAst.Source(listOf("source"), listOf()))
                        },
                        "Argument" to Test("""
                            #macro(argument) { source }
                        """.trimIndent()) {
                            RhovasAst.Expression.Invoke.Macro("macro", listOf(expr("argument")), DslAst.Source(listOf(" source "), listOf()))
                        },
                        "Interpolation" to Test("""
                            #macro {
                                value = ${'$'}{argument}
                            }
                        """.trimIndent()) {
                            RhovasAst.Expression.Invoke.Macro("macro", listOf(), DslAst.Source(listOf("value = ", ""), listOf(expr("argument"))))
                        },
                        "Missing Closing Brace" to Test("""
                            #macro { source()
                        """.trimIndent(), null),
                        "Missing Interpolation Closing Brace" to Test("""
                            #macro {
                                value = ${'$'}{argument
                        """.trimIndent(), null),
                    )) { test("expression", it.source, it.expected) }
                }
            }

            suite("Lambda", listOf(
                "Lambda" to Test("""
                    function { stmt; }
                """.trimIndent()) {
                    RhovasAst.Expression.Invoke.Function(null, "function", listOf(
                        RhovasAst.Expression.Lambda(listOf(), block(stmt("stmt"))),
                    ))
                },
                "Expression" to Test("""
                    function { expr }
                """.trimIndent()) {
                    RhovasAst.Expression.Invoke.Function(null, "function", listOf(
                        RhovasAst.Expression.Lambda(listOf(), RhovasAst.Expression.Block(listOf(), expr("expr"))),
                    ))
                },
                "Argument" to Test("""
                    function(argument) {}
                """.trimIndent()) {
                    RhovasAst.Expression.Invoke.Function(null, "function", listOf(
                        expr("argument"),
                        RhovasAst.Expression.Lambda(listOf(), block()),
                    ))
                },
                "Single Parameter" to Test("""
                    function |parameter| {}
                """.trimIndent()) {
                    RhovasAst.Expression.Invoke.Function(null, "function", listOf(
                        RhovasAst.Expression.Lambda(listOf("parameter" to null), block()),
                    ))
                },
                "Multiple Parameters" to Test("""
                    function |first, second, third| {}
                """.trimIndent()) {
                    RhovasAst.Expression.Invoke.Function(null, "function", listOf(
                        RhovasAst.Expression.Lambda(listOf("first" to null, "second" to null, "third" to null), block()),
                    ))
                },
                "Typed Parameter" to Test("""
                    function |parameter: Type| {}
                """.trimIndent()) {
                    RhovasAst.Expression.Invoke.Function(null, "function", listOf(
                        RhovasAst.Expression.Lambda(listOf("parameter" to type("Type")), block()),
                    ))
                },
                "Trailing Comma" to Test("""
                    function |parameter,| {}
                """.trimIndent()) {
                    RhovasAst.Expression.Invoke.Function(null, "function", listOf(
                        RhovasAst.Expression.Lambda(listOf("parameter" to null), block()),
                    ))
                },
                "Argument & Parameter" to Test("""
                    function(argument) |parameter| {}
                """.trimIndent()) {
                    RhovasAst.Expression.Invoke.Function(null, "function", listOf(
                        expr("argument"),
                        RhovasAst.Expression.Lambda(listOf("parameter" to null), block()),
                    ))
                },
                "Method" to Test("""
                    receiver.method {}
                """.trimIndent()) {
                    RhovasAst.Expression.Invoke.Method(expr("receiver"), false, false, "method", listOf(
                        RhovasAst.Expression.Lambda(listOf(), block()),
                    ))
                },
                "Pipeline" to Test("""
                    receiver.|function {}
                """.trimIndent()) {
                    RhovasAst.Expression.Invoke.Pipeline(expr("receiver"), false, false, null, "function", listOf(
                        RhovasAst.Expression.Lambda(listOf(), block()),
                    ))
                },
                "Invalid Parameter Name" to Test("""
                    function |name, ?| {}
                """.trimIndent(), null),
                "Missing Comma" to Test("""
                    function |first second| {}
                """.trimIndent(), null),
                "Missing Closing Pipe" to Test("""
                    function |first, second {} 
                """.trimIndent(), null),
                "Missing Body" to Test("""
                    function |first, second|
                """.trimIndent(), null),
                "Non-Block Statement" to Test("""
                    function body;
                """.trimIndent(), null),
            )) { test("expression", it.source, it.expected) }
        }

        suite("Pattern") {
            suite("Value", listOf(
                "Null" to Test("""
                    null
                """.trimIndent()) {
                    RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(null))
                },
                "Boolean True" to Test("""
                    true
                """.trimIndent()) {
                    RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(true))
                },
                "Boolean False" to Test("""
                    false
                """.trimIndent()) {
                    RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(false))
                },
                "Integer" to Test("""
                    0
                """.trimIndent()) {
                    RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(BigInteger.parseString("0")))
                },
                "Decimal" to Test("""
                    0.0
                """.trimIndent()) {
                    RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(BigDecimal.parseString("0.0")))
                },
                "String" to Test("""
                    "string"
                """.trimIndent()) {
                    RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.String(listOf("string"), listOf()))
                },
                "Atom" to Test("""
                    :atom
                """.trimIndent()) {
                    RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(RhovasAst.Atom("atom")))
                },
                "Interpolation" to Test("""
                    ${'$'}{value}
                """.trimIndent()) {
                    RhovasAst.Pattern.Value(expr("value"))
                },
                "Missing Opening Brace" to Test("""
                    ${'$'}value}
                """.trimIndent(), null),
                "Missing Closing Brace" to Test("""
                    ${'$'}{value
                """.trimIndent(), null),
            )) { test("pattern", it.source, it.expected) }

            suite("Variable", listOf(
                "Variable" to Test("""
                    variable
                """.trimIndent()) {
                    RhovasAst.Pattern.Variable("variable")
                },
                "Underscore" to Test("""
                    _
                """.trimIndent()) {
                    RhovasAst.Pattern.Variable("_")
                },
            )) { test("pattern", it.source, it.expected) }

            suite("OrderedDestructure", listOf(
                "Empty" to Test("""
                    []
                """.trimIndent()) {
                    RhovasAst.Pattern.OrderedDestructure(listOf())
                },
                "Single" to Test("""
                    [pattern]
                """.trimIndent()) {
                    RhovasAst.Pattern.OrderedDestructure(listOf(
                        RhovasAst.Pattern.Variable("pattern"),
                    ))
                },
                "Multiple" to Test("""
                    [first, second, third]
                """.trimIndent()) {
                    RhovasAst.Pattern.OrderedDestructure(listOf(
                        RhovasAst.Pattern.Variable("first"),
                        RhovasAst.Pattern.Variable("second"),
                        RhovasAst.Pattern.Variable("third"),
                    ))
                },
                "Varargs" to Test("""
                    [first, rest*]
                """.trimIndent()) {
                    RhovasAst.Pattern.OrderedDestructure(listOf(
                        RhovasAst.Pattern.Variable("first"),
                        RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("rest"), "*"),
                    ))
                },
                "Varargs Only" to Test("""
                    [first, +]
                """.trimIndent()) {
                    RhovasAst.Pattern.OrderedDestructure(listOf(
                        RhovasAst.Pattern.Variable("first"),
                        RhovasAst.Pattern.VarargDestructure(null, "+"),
                    ))
                },
                "Missing Comma" to Test("""
                    [first second]
                """.trimIndent(), null),
                "Missing Closing Bracket" to Test("""
                    [pattern
                """.trimIndent(), null),
            )) { test("pattern", it.source, it.expected) }

            suite("NamedDestructure", listOf(
                "Empty" to Test("""
                    {}
                """.trimIndent()) {
                    RhovasAst.Pattern.NamedDestructure(listOf())
                },
                "Single" to Test("""
                    {key: pattern}
                """.trimIndent()) {
                    RhovasAst.Pattern.NamedDestructure(listOf(
                        "key" to RhovasAst.Pattern.Variable("pattern"),
                    ))
                },
                "Multiple" to Test("""
                    {k1: p1, k2: p2, k3: p3}
                """.trimIndent()) {
                    RhovasAst.Pattern.NamedDestructure(listOf(
                        "k1" to RhovasAst.Pattern.Variable("p1"),
                        "k2" to RhovasAst.Pattern.Variable("p2"),
                        "k3" to RhovasAst.Pattern.Variable("p3"),
                    ))
                },
                "Key Only" to Test("""
                    {key}
                """.trimIndent()) {
                    RhovasAst.Pattern.NamedDestructure(listOf(
                        null to RhovasAst.Pattern.Variable("key"),
                    ))
                },
                "Varargs" to Test("""
                    {key: pattern, rest*}
                """.trimIndent()) {
                    RhovasAst.Pattern.NamedDestructure(listOf(
                        "key" to RhovasAst.Pattern.Variable("pattern"),
                        null to RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("rest"), "*"),
                    ))
                },
                "Varargs Only" to Test("""
                    {key: pattern, +}
                """.trimIndent()) {
                    RhovasAst.Pattern.NamedDestructure(listOf(
                        "key" to RhovasAst.Pattern.Variable("pattern"),
                        null to RhovasAst.Pattern.VarargDestructure(null, "+"),
                    ))
                },
                "Missing Key" to Test("""
                    {:pattern}
                """.trimIndent()) {
                    RhovasAst.Pattern.NamedDestructure(listOf(
                        null to RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(RhovasAst.Atom("pattern"))),
                    ))
                },
                "Missing Colon" to Test("""
                    {key pattern}
                """.trimIndent(), null),
                "Missing Comma" to Test("""
                    {k1: p1 k2: p2}
                """.trimIndent(), null),
                "Missing Closing Bracket" to Test("""
                    {key: pattern
                """.trimIndent(), null),
            )) { test("pattern", it.source, it.expected) }

            suite("TypedDestructure", listOf(
                "Type" to Test("""
                    Type
                """.trimIndent()) {
                    RhovasAst.Pattern.TypedDestructure(type("Type"), null)
                },
                "Pattern" to Test("""
                    Type pattern
                """.trimIndent()) {
                    RhovasAst.Pattern.TypedDestructure(type("Type"), RhovasAst.Pattern.Variable("pattern"))
                },
            )) { test("pattern", it.source, it.expected) }

            suite("VarargDestructure", listOf(
                "Zero Or More" to Test("""
                    pattern*
                """.trimIndent()) {
                    RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("pattern"), "*")
                },
                "One Or More" to Test("""
                    pattern+
                """.trimIndent()) {
                    RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("pattern"), "+")
                },
                "Operator Only" to Test("""
                    *
                """.trimIndent()) {
                    RhovasAst.Pattern.VarargDestructure(null, "*")
                },
            )) { test("pattern", it.source, it.expected) }

            suite("Predicate", listOf(
                "Variable" to Test("""
                    pattern ${'$'}{predicate}
                """.trimIndent()) {
                    RhovasAst.Pattern.Predicate(RhovasAst.Pattern.Variable("pattern"), expr("predicate"))
                },
                "OrderedDestructure" to Test("""
                    [ordered] ${'$'}{predicate}
                """.trimIndent()) {
                    RhovasAst.Pattern.Predicate(RhovasAst.Pattern.OrderedDestructure(listOf(RhovasAst.Pattern.Variable("ordered"))), expr("predicate"))
                },
                "VarargDestructure" to Test("""
                    pattern* ${'$'}{predicate}
                """.trimIndent()) {
                    RhovasAst.Pattern.Predicate(RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("pattern"), "*"), expr("predicate"))
                },
                "Missing Opening Brace" to Test("""
                    pattern ${'$'}predicate}
                """.trimIndent(), null),
                "Missing Closing Brace" to Test("""
                    pattern ${'$'}{predicate
                """.trimIndent(), null),
            )) { test("pattern", it.source, it.expected) }

            suite("Error", listOf(
                "Error" to Test<RhovasAst>("""
                    #
                """.trimIndent(), null),
            )) { test("pattern", it.source, it.expected) }
        }

        suite("Type", listOf(
            "Type" to Test("""
                Type
            """.trimIndent()) {
                RhovasAst.Type(listOf("Type"), null)
            },
            "Nesting" to Test("""
                First.Second.Third
            """.trimIndent()) {
                RhovasAst.Type(listOf("First", "Second", "Third"), null)
            },
            "Empty Generics" to Test("""
                Type<>
            """.trimIndent()) {
                RhovasAst.Type(listOf("Type"), listOf())
            },
            "Single Generic" to Test("""
                Type<Generic>
            """.trimIndent()) {
                RhovasAst.Type(listOf("Type"), listOf(type("Generic")))
            },
            "Multiple Generics" to Test("""
                Type<First, Second, Third>
            """.trimIndent()) {
                RhovasAst.Type(listOf("Type"), listOf(type("First"), type("Second"), type("Third")))
            },
            "Trailing Comma" to Test("""
                Type<Generic,>
            """.trimIndent()) {
                RhovasAst.Type(listOf("Type"), listOf(type("Generic")))
            },
            "Missing Comma" to Test("""
                Type<First Second>
            """.trimIndent(), null),
        )) { test("type", it.source, it.expected) }

        suite("Interaction") {
            spec("Keyword Label Atom") {
                test("statement", """
                    return :atom;
                """.trimIndent()) {
                    RhovasAst.Statement.Return(RhovasAst.Expression.Literal.Scalar(RhovasAst.Atom("atom")))
                }
            }

            spec("Lambda Parameters Or") {
                test("expression", """
                    function || {} 
                """.trimIndent()) {
                    RhovasAst.Expression.Binary("||", expr("function"), RhovasAst.Expression.Literal.Object(listOf()))
                }
            }
        }
    }

    private fun block(vararg statements: RhovasAst.Statement): RhovasAst.Expression.Block {
        return RhovasAst.Expression.Block(statements.toList(), null)
    }

    private fun stmt(name: String): RhovasAst.Statement {
        return RhovasAst.Statement.Expression(expr(name))
    }

    private fun expr(name: String): RhovasAst.Expression {
        return RhovasAst.Expression.Access.Variable(null, name)
    }

    private fun type(name: String): RhovasAst.Type {
        return RhovasAst.Type(listOf(name), null)
    }

    private fun test(rule: String, source: String, expected: (() -> RhovasAst)?) {
        val input = Input("Test", source)
        val expected = expected?.invoke()
        try {
            val ast = RhovasParser(input).parse(rule)
            assertEquals(expected, ast)
            assertTrue(ast.context.isNotEmpty() || source.isBlank())
        } catch (e: ParseException) {
            if (expected != null || e.summary == "Broken parser invariant.") {
                fail(input.diagnostic(e.summary, e.details, e.range, e.context))
            }
        }
    }

}
