package dev.rhovas.interpreter.evaluator

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.analyzer.AnalyzeException
import dev.rhovas.interpreter.analyzer.rhovas.RhovasAnalyzer
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import dev.rhovas.interpreter.parser.rhovas.RhovasParser
import kotlin.test.assertEquals
import kotlin.test.fail

class EvaluatorTests: RhovasSpec() {

    data class Test(val source: String, val log: String? = null, val expected: ((Scope.Definition) -> Object?)? = null)

    init {
        suite("Source", listOf(
            "Empty" to Test("""
                    
            """.trimIndent(), ""),
            "Single" to Test("""
                log(1);
            """.trimIndent(), "1"),
            "Multiple" to Test("""
                log(1);
                log(2);
                log(3);
            """.trimIndent(), "123"),
            "Exception" to Test("""
                throw Exception("message");
            """.trimIndent(), null),
        )) { test("source", it.source, it.log, it.expected) }

        suite("Component") {
            suite("Struct", listOf(
                "Struct" to Test("""
                    struct Name {}
                    log(Name({}));
                """.trimIndent(), "Name{}"),
                "Default Initializer" to Test("""
                    struct Name {
                        val field: Integer = 1;
                    }
                    log(Name({}).field);
                """.trimIndent(), "1"),
                "Default Initializer Value" to Test("""
                    struct Name {
                        val field: Integer = 1;
                    }
                    log(Name({field: 2}).field);
                """.trimIndent(), "2"),
                "Default Positional Initializer" to Test("""
                    struct Name {
                        val field: Integer = 1;
                    }
                    log(Name(1).field);
                """.trimIndent(), "1"),
                "Custom Initializer" to Test("""
                    struct Name {
                        var field: Integer;
                        init(field: Decimal) {
                            this { field: field.to(Integer) };
                        }
                    }
                    log(Name(1.0).field);
                """.trimIndent(), "1"),
                "Function" to Test("""
                    struct Name {
                        func function(): Integer {
                            return 1;
                        }
                    }
                    log(Name.function());
                """.trimIndent(), "1"),
                "Method" to Test("""
                    struct Name {
                        val field: Integer = 1;
                        func method(this): Integer {
                            return this.field;
                        }
                    }
                    log(Name({}).method());
                """.trimIndent(), "1"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("Class", listOf(
                "Class" to Test("""
                    class Name {
                        init() {}
                    }
                    log(Name());
                """.trimIndent(), "Name{}"),
                "Extends Field" to Test("""
                    virtual class Parent {
                        val field: Integer;
                        init() {
                            this { field: 1 };
                        }
                    }
                    class Child: Parent {
                        init() {
                            super();
                        }
                    }
                    log(Child().field);
                """.trimIndent(), "1"),
                "Extends Method" to Test("""
                    virtual class Parent {
                        init() {}
                        func method(this) {
                            log(1);
                        }
                    }
                    class Child: Parent {
                        init() {
                            super();
                        }
                    }
                    Child().method();
                """.trimIndent(), "1"),
                "Custom Initializer" to Test("""
                    class Name {
                        var field: Integer;
                        init(field: Decimal) {
                            this { field: field.to(Integer) };
                        }
                    }
                    log(Name(1.0).field);
                """.trimIndent(), "1"),
                "Function" to Test("""
                    class Name {
                        func function(): Integer {
                            return 1;
                        }
                    }
                    log(Name.function());
                """.trimIndent(), "1"),
                "Method" to Test("""
                    class Name {
                        val field: Integer;
                        init() {
                            this { field: 1 };
                        }
                        func method(this): Integer {
                            return this.field;
                        }
                    }
                    log(Name().method());
                """.trimIndent(), "1"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("Interface", listOf(
                "Interface" to Test("""
                    interface Parent {
                        func method(this): Integer {
                            return 1;
                        }
                    }
                    class Name: Parent {
                        init() {}
                    }
                    log(Name().method());
                """.trimIndent(), "1"),
            )) { test("source", it.source, it.log, it.expected) }
        }

        suite("Member") {
            suite("Property", listOf(
                "Getter" to Test("""
                    struct Name {
                        val field: Integer = 1;
                    }
                    val instance = Name({});
                    log(instance.field);
                """.trimIndent(), "1"),
                "Setter" to Test("""
                    struct Name {
                        var field: Integer = 1;
                    }
                    val instance = Name({});
                    instance.field = 2;
                    log(instance.field);
                """.trimIndent(), "2"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("Initializer", listOf(
                "Empty" to Test("""
                    class Name {
                        init() {
                            log(1);
                        }
                    }
                    Name();
                """.trimIndent(), "1"),
                "Argument" to Test("""
                    class Name {
                        init(argument: Integer) {
                            log(argument);
                        }
                    }
                    Name(1);
                """.trimIndent(), "1"),
                "Return" to Test("""
                    class Name {
                        init() {
                            this {};
                            return this;
                        }
                    }
                    Name();
                """.trimIndent(), ""),
                "Declared Exception" to Test("""
                    class Name {
                        init() throws Exception {
                            throw Exception("message");
                        }
                    }
                    Name();
                """.trimIndent(), null),
                "Undeclared Exception" to Test("""
                    class Name {
                        init() {
                            lambda { throw Exception("message"); }.invoke!([]);
                        }
                    }
                    Name();
                """.trimIndent(), null),
            )) { test("source", it.source, it.log, it.expected) }
        }

        suite("Statement") {
            suite("Block", listOf(
                "Empty" to Test("""
                    {}
                """.trimIndent(), ""),
                "Single" to Test("""
                    { log(1); }
                """.trimIndent(), "1"),
                "Multiple" to Test("""
                    { log(1); log(2); log(3); }
                """.trimIndent(), "123"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("Component", listOf(
                "Struct" to Test("""
                    struct Name {}
                    log(Name({}));
                """.trimIndent(), "Name{}"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("Expression", listOf(
                "Function" to Test("""
                    log(1);
                """.trimIndent(), "1"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("Declaration") {
                suite("Variable", listOf(
                    "Val Initialization" to Test("""
                        val name = 1;
                        log(name);
                    """.trimIndent(), "1"),
                    "Var Initialization" to Test("""
                        var name = 1;
                        log(name);
                    """.trimIndent(), "1"),
                )) { test("source", it.source, it.log, it.expected) }

                suite("Function", listOf(
                    "Declaration" to Test("""
                        func name() {
                            log(1);
                        }
                        name();
                    """.trimIndent(), "1"),
                    "Single Parameter" to Test("""
                        func name(x: Integer) {
                            log(x);
                        }
                        name(1);
                    """.trimIndent(), "1"),
                    "Multiple Parameters" to Test("""
                        func name(x: Integer, y: Integer, z: Integer) {
                            log(x);
                            log(y);
                            log(z);
                        }
                        name(1, 2, 3);
                    """.trimIndent(), "123"),
                    "Return Value" to Test("""
                        func name(): Integer {
                            return 1;
                        }
                        log(name());
                    """.trimIndent(), "1"),
                    "Declared Exception" to Test("""
                        func name() throws Exception {
                            throw Exception("message");
                        }
                        name!();
                    """.trimIndent(), null),
                    "Undeclared Exception" to Test("""
                        func name() {
                            lambda { throw Exception("message"); }.invoke!([]);
                        }
                        name();
                    """.trimIndent(), null),
                )) { test("source", it.source, it.log, it.expected) }
            }

            suite("Assignment") {
                suite("Variable", listOf(
                    "Variable" to Test("""
                        var variable = "initial";
                        variable = "final";
                        log(variable);
                    """.trimIndent(), "final"),
                )) { test("source", it.source, it.log, it.expected) }

                suite("Property", listOf(
                    "Property" to Test("""
                        val object = { property: "initial" };
                        object.property = "final";
                        log(object.property);
                    """.trimIndent(), "final"),
                    "Element" to Test("""
                        val tuple = Tuple(["initial"]);
                        tuple.0 = "final";
                        log(tuple.0);
                    """.trimIndent(), "final"),
                    //"Undefined" to Test("""
                    //    val dynamic: Dynamic = {};
                    //    dynamic.property = "value";
                    //""".trimIndent(), null),
                    "Unassignable" to Test("""
                        val dynamic: Dynamic = [];
                        dynamic.size = "value";
                    """.trimIndent(), null),
                    //"Invalid Value" to Test("""
                    //    val dynamic: Dynamic = { property: "initial" };
                    //    dynamic.property = 1;
                    //""".trimIndent(), null),
                )) { test("source", it.source, it.log, it.expected) }

                suite("Index", listOf(
                    "List" to Test("""
                        val list = ["initial"];
                        list[0] = "final";
                        log(list[0]);
                    """.trimIndent(), "final"),
                    "Map" to Test("""
                        val map = Map({ key: "initial" });
                        map[:key] = "final";
                        log(map[:key]);
                    """.trimIndent(), "final"),
                    "Undefined" to Test("""
                        val dynamic: Dynamic = 1;
                        dynamic[:key] = "value";
                    """.trimIndent(), null),
                    "Invalid Value" to Test("""
                        val dynamic: Dynamic = [];
                        dynamic[:key] = "value";
                    """.trimIndent(), null),
                )) { test("source", it.source, it.log, it.expected) }
            }

            suite("If", listOf(
                "True" to Test("""
                    if (true) {
                        log(1);
                    }
                """.trimIndent(), "1"),
                "False" to Test("""
                    if (false) {
                        log(1);
                    }
                """.trimIndent(), ""),
                "Else" to Test("""
                    if (false) {
                        log(1);
                    } else {
                        log(2);
                    }
                """.trimIndent(), "2"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("Match") {
                suite("Conditional", listOf(
                    "First" to Test("""
                        match {
                            true: log(1);
                            false: log(2);
                        }
                    """.trimIndent(), "1"),
                    "Last" to Test("""
                        match {
                            false: log(1);
                            true: log(2);
                        }
                    """.trimIndent(), "2"),
                    "None" to Test("""
                        match {
                            false: log(1);
                            false: log(2);
                        }
                    """.trimIndent(), ""),
                    "Else" to Test("""
                        match {
                            else: log(1);
                        }
                    """.trimIndent(), "1"),
                    "Else Condition True" to Test("""
                        match {
                            else true: log(1);
                        }
                    """.trimIndent(), "1"),
                    "Else Condition False" to Test("""
                        match {
                            else false: log(1);
                        }
                    """.trimIndent(), null),
                )) { test("source", it.source, it.log, it.expected) }

                suite("Structural", listOf(
                    "First" to Test("""
                        match (true) {
                            true: log(1);
                            false: log(2);
                        }
                    """.trimIndent(), "1"),
                    "Last" to Test("""
                        match (false) {
                            true: log(1);
                            false: log(2);
                        }
                    """.trimIndent(), "2"),
                    "None" to Test("""
                        match (true) {
                            false: log(1);
                            false: log(2);
                        }
                    """.trimIndent(), null),
                    "Else" to Test("""
                        match (true) {
                            else: log(1);
                        }
                    """.trimIndent(), "1"),
                    "Else Condition True" to Test("""
                        match (true) {
                            else true: log(1);
                        }
                    """.trimIndent(), "1"),
                    "Else Condition False" to Test("""
                        match (true) {
                            else false: log(1);
                        }
                    """.trimIndent(), null),
                )) { test("source", it.source, it.log, it.expected) }
            }

            suite("For", listOf(
                "Empty" to Test("""
                    for (val element in []) {
                        log(1);
                    }
                """.trimIndent(), ""),
                "Single" to Test("""
                    for (val element in [1]) {
                        log(1);
                    }
                """.trimIndent(), "1"),
                "Multiple" to Test("""
                    for (val element in [1, 2, 3]) {
                        log(1);
                    }
                """.trimIndent(), "111"),
                "Element" to Test("""
                    for (val element in [1, 2, 3]) {
                        log(element);
                    }
                """.trimIndent(), "123"),
                "Break" to Test("""
                    for (val element in [1, 2, 3]) {
                        log(1);
                        if (true) { break; }
                        log(2);
                    }
                """.trimIndent(), "1"),
                "Break Label" to Test("""
                    outer: for (val first in [1, 2, 3]) {
                        log(1);
                        for (val second in [4, 5, 6]) {
                            log(2);
                            if (true) { break outer; }
                            log(3);
                        }
                        log(4);
                    }
                """.trimIndent(), "12"),
                "Continue" to Test("""
                    for (val element in [1, 2, 3]) {
                        log(1);
                        if (true) { continue; }
                        log(2);
                    }
                """.trimIndent(), "111"),
                "Continue Label" to Test("""
                    outer: for (val first in [1, 2, 3]) {
                        log(1);
                        for (val second in [4, 5, 6]) {
                            log(2);
                            if (true) { continue outer; }
                            log(3);
                        }
                        log(4);
                    }
                """.trimIndent(), "121212"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("While", listOf(
                "Zero Iterations" to Test("""
                    while (number < 0) {
                        log(1);
                    }
                """.trimIndent(), ""),
                "Single Iteration" to Test("""
                    while (number < 1) {
                        log(1);
                        number = number + 1;
                    }
                """.trimIndent(), "1"),
                "Multiple Iterations" to Test("""
                    while (number < 5) {
                        log(1);
                        number = number + 1;
                    }
                """.trimIndent(), "11111"),
                "Break" to Test("""
                    while (number < 5) {
                        log(1);
                        number = number + 1;
                        if (true) { break; }
                        log(2);
                    }
                """.trimIndent(), "1"),
                "Break Label" to Test("""
                    outer: while (number < 5) {
                        log(1);
                        number = number + 1;
                        while (number < 5) {
                            log(2);
                            if (true) { break outer; }
                            log(3);
                        }
                        log(4);
                    }
                """.trimIndent(), "12"),
                "Continue" to Test("""
                    while (number < 5) {
                        log(1);
                        number = number + 1;
                        if (true) { continue; }
                        log(2);
                    }
                """.trimIndent(), "11111"),
                "Continue Label" to Test("""
                    outer: while (number < 5) {
                        log(1);
                        number = number + 1;
                        while (number < 5) {
                            log(2);
                            if (true) { continue outer; }
                            log(3);
                        }
                        log(4);
                    }
                """.trimIndent(), "1212121214"),
            )) { test("source", it.source, it.log, it.expected) {
                it.variables.define(Variable.Definition(Variable.Declaration("number", Type.INTEGER, true), Object(Type.INTEGER, BigInteger.ZERO)))
            } }

            suite("Try", listOf(
                "Try" to Test("""
                    try {
                        log(1);
                    }
                """.trimIndent(), "1"),
                "Catch Thrown" to Test("""
                    try {
                        log(1);
                        throw Exception("message");
                    } catch (val e: Exception) {
                        log(2);
                    }
                """.trimIndent(), "12"),
                "Catch Unthrown" to Test("""
                    try {
                        log(1);
                    } catch (val e: Exception) {
                        log(2);
                    }
                """.trimIndent(), "1"),
                "Catch Subtype" to Test("""
                    try {
                        log(1);
                        throw SubtypeException("message");
                    } catch (val e: Exception) {
                        log(2);
                    }
                """.trimIndent(), "12"),
                "Catch Supertype" to Test("""
                    try {
                        log(1);
                        throw Exception("message");
                    } catch (val e: SubtypeException) {
                        log(2);
                    } catch (val e: Exception) {
                        log(3);
                    }
                """.trimIndent(), "13"),
                "Catch Exception" to Test("""
                    try {
                        try {
                            throw Exception("try");
                        } catch (val e: Exception) {
                            throw Exception("catch");
                        }
                    } catch (val e: Exception) {
                        log(e.message);
                    }
                """.trimIndent(), "catch"),
                "Finally" to Test("""
                    try {
                        log(1);
                    } finally {
                        log(2);
                    }
                """.trimIndent(), "12"),
                "Finally With Catch" to Test("""
                    try {
                        log(1);
                        throw Exception("message");
                    } catch (val e: Exception) {
                        log(2);
                    } finally {
                        log(3);
                    }
                """.trimIndent(), "123"),
                "Finally Without Catch" to Test("""
                    try {
                        try {
                            throw Exception("message");
                        } finally {
                            log(1);
                        }
                    } catch (val e: Exception) {
                        log(2);
                    }
                """.trimIndent(), "12"),
                "Finally Exception" to Test("""
                    try {
                        try {
                            throw Exception("try");
                        } finally {
                            lambda { throw Exception("finally"); }.invoke!([]);
                        }
                    } catch (val e: Exception) {
                        log(e.message);
                    }
                """.trimIndent(), "finally"),
            )) { test("source", it.source, it.log, it.expected) {
                val component = Component.Class("SubtypeException", Modifiers(Modifiers.Inheritance.DEFAULT), Scope.Definition(null))
                component.inherit(Type.EXCEPTION)
                (component.scope as Scope<*, in Function.Definition>).functions.define(Function.Definition(Function.Declaration(Modifiers(Modifiers.Inheritance.DEFAULT), "", listOf(), listOf(Variable.Declaration("message", Type.STRING, false)), component.type, listOf())) { arguments ->
                    Object(component.type, arguments[0].value as String)
                })
                it.types.define(component.type)
            } }

            suite("With", listOf(
                "With" to Test("""
                    with (1) {
                        log(1);
                    }
                """.trimIndent(), "1"),
                "Argument" to Test("""
                    with (val argument = 1) {
                        log(argument);
                    }
                """.trimIndent(), "1")
            )) { test("source", it.source, it.log, it.expected) }

            suite("Assert", listOf(
                "True" to Test("""
                    assert true;
                """.trimIndent(), ""),
                "False" to Test("""
                    assert false;
                """.trimIndent(), null),
            )) { test("source", it.source, it.log, it.expected) }

            suite("Require", listOf(
                "True" to Test("""
                    require true;
                """.trimIndent(), ""),
                "False" to Test("""
                    require false;
                """.trimIndent(), null),
            )) { test("source", it.source, it.log, it.expected) }

            suite("Ensure", listOf(
                "True" to Test("""
                    ensure true;
                """.trimIndent(), ""),
                "False" to Test("""
                    ensure false;
                """.trimIndent(), null),
                "Post-Return True" to Test("""
                    func test(): Boolean {
                        return true;
                        ensure val;
                    }
                    log(test());
                """.trimIndent(), "true"),
                "Post-Return False" to Test("""
                    func test(): Boolean {
                        return false;
                        ensure val;
                    }
                    log(test());
                """.trimIndent(), null),
            )) { test("source", it.source, it.log, it.expected) }
        }

        suite("Expression") {
            suite("Literal") {
                suite("Scalar", listOf(
                    "Null" to Test("""
                        null
                    """.trimIndent()) {
                        literal(null)
                    },
                    "Boolean" to Test("""
                        true
                    """.trimIndent()) {
                        literal(true)
                    },
                    "Integer" to Test("""
                        123
                    """.trimIndent()) {
                        literal(BigInteger.parseString("123"))
                    },
                    "Decimal" to Test("""
                        123.456
                    """.trimIndent()) {
                        literal(BigDecimal.parseString("123.456"))
                    },
                    "Atom" to Test("""
                        :atom
                    """.trimIndent()) {
                        literal(RhovasAst.Atom("atom"))
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("String", listOf(
                    "String" to Test("""
                        "string"
                    """.trimIndent()) {
                        literal("string")
                    },
                    "Interpolation" to Test("""
                        "first${'$'}{1}second"
                    """.trimIndent()) {
                        literal("first1second")
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("List", listOf(
                    "Empty" to Test("""
                        []
                    """.trimIndent()) {
                        Object(Type.LIST[Type.DYNAMIC], mutableListOf<Object>())
                    },
                    "Single" to Test("""
                        [1]
                    """.trimIndent()) {
                        Object(Type.LIST[Type.DYNAMIC], mutableListOf(
                            literal(BigInteger.parseString("1")),
                        ))
                    },
                    "Multiple" to Test("""
                        [1, 2, 3]
                    """.trimIndent()) {
                        Object(Type.LIST[Type.DYNAMIC], mutableListOf(
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                            literal(BigInteger.parseString("3")),
                        ))
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Object", listOf(
                    "Empty" to Test("""
                        {}
                    """.trimIndent()) {
                        Object(Type.STRUCT.DYNAMIC, mapOf<String, Object>())
                    },
                    "Single" to Test("""
                        {key: "value"}
                    """.trimIndent()) {
                        Object(Type.STRUCT.DYNAMIC, mapOf("key" to literal("value")))
                    },
                    "Multiple" to Test("""
                        {k1: "v1", k2: "v2", k3: "v3"}
                    """.trimIndent()) {
                        Object(Type.STRUCT.DYNAMIC, mapOf("k1" to literal("v1"), "k2" to literal("v2"), "k3" to literal("v3")))
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Type", listOf(
                    "Type" to Test("""
                        Any
                    """.trimIndent()) {
                        Object(Type.TYPE[Type.ANY], Type.ANY)
                    },
                )) { test("expression", it.source, it.log, it.expected) }
            }

            suite("Group", listOf(
                "Group" to Test("""
                    ("expression")
                """.trimIndent()) {
                    literal("expression")
                },
                "Nested" to Test("""
                    ((("expression")))
                """.trimIndent()) {
                    literal("expression")
                },
                "Binary" to Test("""
                    ("first" + "second")
                """.trimIndent()) {
                    literal("firstsecond")
                },
            )) { test("expression", it.source, it.log, it.expected) }

            suite("Unary", listOf(
                "Numerical Negation" to Test("""
                    -1
                """.trimIndent()) {
                    literal(BigInteger.parseString("-1"))
                },
                "Logical Negation" to Test("""
                    !true
                """.trimIndent()) {
                    literal(false)
                },
            )) { test("expression", it.source, it.log, it.expected) }

            suite("Binary") {
                suite("Logical Or", listOf(
                    "True" to Test("""
                        false || true
                    """.trimIndent()) {
                        literal(true)
                    },
                    "False" to Test("""
                        false || false
                    """.trimIndent()) {
                        literal(false)
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Logical And", listOf(
                    "True" to Test("""
                        true && true
                    """.trimIndent()) {
                        literal(true)
                    },
                    "False" to Test("""
                        true && false
                    """.trimIndent()) {
                        literal(false)
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Equality", listOf(
                    "True" to Test("""
                        1 == 1
                    """.trimIndent()) {
                        literal(true)
                    },
                    "False" to Test("""
                        1 != 1
                    """.trimIndent()) {
                        literal(false)
                    },
                    "Different Types" to Test("""
                        1 == 1.0
                    """.trimIndent()) {
                        literal(false)
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Identity", listOf(
                    "True" to Test("""
                        true === true
                    """.trimIndent()) {
                        literal(true)
                    },
                    "False" to Test("""
                        [] !== []
                    """.trimIndent()) {
                        literal(true)
                    },
                    "Implementation Non-Primitive" to Test("""
                        1 === 1
                    """.trimIndent()) {
                        literal(true)
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Comparison", listOf(
                    "Less Than" to Test("""
                        0 < 1
                    """.trimIndent()) {
                        literal(true)
                    },
                    "Greater Than" to Test("""
                        0 > 1
                    """.trimIndent()) {
                        literal(false)
                    },
                    "Less Than Or Equal" to Test("""
                        0 <= 1
                    """.trimIndent()) {
                        literal(true)
                    },
                    "Greater Than Or Equal" to Test("""
                        0 >= 1
                    """.trimIndent()) {
                        literal(false)
                    },
                    "Less Than Equal" to Test("""
                        0 < 0
                    """.trimIndent()) {
                        literal(false)
                    },
                    "Less Than Or Equal Equal" to Test("""
                        0 <= 0
                    """.trimIndent()) {
                        literal(true)
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Arithmetic", listOf(
                    "Integer Add" to Test("""
                        1 + 2
                    """.trimIndent()) {
                        literal(BigInteger.parseString("3"))
                    },
                    "Integer Subtract" to Test("""
                        1 - 2
                    """.trimIndent()) {
                        literal(BigInteger.parseString("-1"))
                    },
                    "Decimal Multiply" to Test("""
                        1.2 * 2.3
                    """.trimIndent()) {
                        literal(BigDecimal.parseString("2.76"))
                    },
                    "Decimal Divide" to Test("""
                        1.2 / 2.3
                    """.trimIndent()) {
                        literal(BigDecimal.parseString("0.52"))
                    },
                    "String Concat" to Test("""
                        "first" + "second"
                    """.trimIndent()) {
                        literal("firstsecond")
                    },
                    "List Concat" to Test("""
                        [1] + [2]
                    """.trimIndent()) {
                        Object(Type.LIST[Type.DYNAMIC], listOf(
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                        ))
                    },
                )) { test("expression", it.source, it.log, it.expected) }
            }

            suite("Access") {
                suite("Variable", listOf(
                    "Variable" to Test("""
                        variable
                    """.trimIndent()) {
                        it.variables.define(variable("variable", Type.STRING,"variable"))
                        literal("variable")
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Property", listOf(
                    "Property" to Test("""
                        object.property
                    """.trimIndent()) {
                        it.variables.define(variable("object", Type.STRUCT[Type.Struct(mapOf("property" to Variable.Declaration("property", Type.STRING, false)))], mapOf("property" to literal("property"))))
                        literal("property")
                    },
                    "Coalesce" to Test("""
                        nullObject?.property
                    """.trimIndent()) {
                        it.variables.define(variable("nullObject", Type.NULLABLE[Type.DYNAMIC], null))
                        literal(null)
                    },
                    "Element" to Test("""
                        tuple.0
                    """.trimIndent()) {
                        it.variables.define(variable("tuple", Type.TUPLE[listOf(Type.INTEGER)], listOf(literal(BigInteger.parseString("1")))))
                        literal(BigInteger.parseString("1"))
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Index", listOf(
                    "List" to Test("""
                        list[0]
                    """.trimIndent()) {
                        it.variables.define(variable("list", Type.LIST[Type.STRING], mutableListOf(literal("element"))))
                        literal("element")
                    },
                    "Object" to Test("""
                        object[:key]
                    """.trimIndent()) {
                        it.variables.define(variable("object", Type.MAP[Type.ATOM, Type.STRING], mutableMapOf(Object.Hashable(literal(RhovasAst.Atom("key"))) to literal("value"))))
                        Object(Type.NULLABLE[Type.STRING], Pair(literal("value"), null))
                    },
                    "Coalesce" to Test("""
                        null?[0]
                    """.trimIndent()) {
                       literal(null)
                    },
                )) { test("expression", it.source, it.log, it.expected) }
            }

            suite("Invoke") {
                suite("Constructor", listOf(
                    "Constructor" to Test("""
                        Nullable("argument")
                    """.trimIndent()) {
                        Object(Type.NULLABLE[Type.STRING], Pair(literal("argument"), null))
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Function", listOf(
                    "Function" to Test("""
                        range(1, 2, :incl)
                    """.trimIndent()) {
                        Object(Type.LIST[Type.INTEGER], mutableListOf(
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                        ))
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Method", listOf(
                    "Method" to Test("""
                        1.add(2)
                    """.trimIndent()) {
                        literal(BigInteger.parseString("3"))
                    },
                    "Coalesce" to Test("""
                        null?.add(2)
                    """.trimIndent()) {
                        literal(null)
                    },
                    "Cascade" to Test("""
                        1..add(2)
                    """.trimIndent()) {
                        literal(BigInteger.parseString("1"))
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Pipeline", listOf(
                    "Pipeline" to Test("""
                        1.|range(2, :incl)
                    """.trimIndent()) {
                        Object(Type.LIST[Type.INTEGER], mutableListOf(
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                        ))
                    },
                    "Qualified" to Test("""
                        1.|Kernel.range(2, :incl)
                    """.trimIndent()) {
                        Object(Type.LIST[Type.INTEGER], mutableListOf(
                            literal(BigInteger.parseString("1")),
                            literal(BigInteger.parseString("2")),
                        ))
                    },
                    "Coalesce" to Test("""
                        null?.|range(2, :incl)
                    """.trimIndent()) {
                        literal(null)
                    },
                    "Cascade" to Test("""
                        1..|range(2, :incl)
                    """.trimIndent()) {
                        literal(BigInteger.parseString("1"))
                    },
                )) { test("expression", it.source, it.log, it.expected) }

                suite("Bang", listOf(
                    "Exception to Result" to Test("""
                        func throws!(): Integer throws Exception {
                            return 1;
                        }
                        log(throws());
                    """.trimIndent(), "1"),
                    "Exception to Result Throws" to Test("""
                        func throws!() throws Exception {
                            throw Exception("message");
                        }
                        log(throws());
                    """.trimIndent(), "message"),
                    "Exception to Result Throws Undeclared" to Test("""
                        func throws!() throws SubtypeException {
                            lambda { throw Exception("message"); }.invoke!([]);
                        }
                        try {
                            log(throws());
                        } catch (val e: Exception) {
                            log(e.message);
                        }
                    """.trimIndent(), "message") {
                        val component = Component.Class("SubtypeException", Modifiers(Modifiers.Inheritance.DEFAULT), Scope.Definition(null))
                        component.inherit(Type.EXCEPTION)
                        (component.scope as Scope<*, in Function.Definition>).functions.define(Function.Definition(Function.Declaration(Modifiers(Modifiers.Inheritance.DEFAULT), "", listOf(), listOf(Variable.Declaration("message", Type.STRING, false)), component.type, listOf())) { arguments ->
                            Object(component.type, arguments[0].value as String)
                        })
                        it.types.define(component.type)
                        null
                    },
                    "Result to Exception" to Test("""
                        func result(): Nullable<Integer> {
                            return Nullable(1);
                        }
                        log(result!());
                    """.trimIndent(), "1"),
                    "Result to Exception Throws" to Test("""
                        func result(): Nullable<Integer> {
                            return null;
                        }
                        try {
                            log(result!());
                        } catch (val e: Exception) {
                            log("exception");
                        }
                    """.trimIndent(), "exception"),
                )) { test("source", it.source, it.log, it.expected) }
            }

            suite("Lambda", listOf(
                //"Zero Arguments" to Test("""
                //    null.else { log(val) };
                //""".trimIndent(), "void"),
                "Single Argument" to Test("""
                    [1, 2, 3].for { log(val); };
                """.trimIndent(), "123"),
                //"Multiple Arguments" to Test("""
                //    [1, 2, 3].reduce { log(val.0) + log(val.1) };
                //""".trimIndent(), "1233"),
                "Parameter" to Test("""
                    [1, 2, 3].for |x| { log(x); };
                """.trimIndent(), "123"),
                "Return" to Test("""
                    log([1, 2, 3].map { return val * val; });
                """.trimIndent(), "[1, 4, 9]"),
                "Invalid Return" to Test("""
                    [1, 2, 3].filter { lambda { 1 }.invoke!([]) };
                """.trimIndent(), null),
            )) { test("source", it.source, it.log, it.expected) }
        }

        suite("Pattern") {
            suite("Value", listOf(
                "Null" to Test("""
                    match (null) {
                        null: log(1);
                    }
                """.trimIndent(), "1"),
                "Boolean True" to Test("""
                    match (true) {
                        true: log(1);
                    }
                """.trimIndent(), "1"),
                "Boolean False" to Test("""
                    match (false) {
                        false: log(1);
                    }
                """.trimIndent(), "1"),
                "Integer" to Test("""
                    match (0) {
                        0: log(1);
                    }
                """.trimIndent(), "1"),
                "Decimal" to Test("""
                    match (0.0) {
                        0.0: log(1);
                    }
                """.trimIndent(), "1"),
                "String" to Test("""
                    match ("string") {
                        "string": log(1);
                    }
                """.trimIndent(), "1"),
                "Atom" to Test("""
                    match (:atom) {
                        :atom: log(1);
                    }
                """.trimIndent(), "1"),
                "Interpolation" to Test("""
                    match (1 + 2) {
                        ${'$'}{1 + 2}: log(1);
                    }
                """.trimIndent(), "1"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("Variable", listOf(
                "Variable" to Test("""
                    match (1) {
                        variable: log(variable);
                    }
                """.trimIndent(), "1"),
                "Underscore" to Test("""
                    match (1) {
                        _: log("1");
                    }
                """.trimIndent(), "1"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("Predicate", listOf(
                "True" to Test("""
                    match (0) {
                        num ${'$'}{true}: log(1);
                    }
                """.trimIndent(), "1"),
                "False" to Test("""
                    match (0) {
                        num ${'$'}{false}: log(1);
                        else: log(2);
                    }
                """.trimIndent(), "2"),
                "Variable True" to Test("""
                    match (0) {
                        num ${'$'}{num == 0}: log(1);
                    }
                """.trimIndent(), "1"),
                "Variable False" to Test("""
                    match (1) {
                        num ${'$'}{num == 0}: log(1);
                        else: log(2);
                    }
                """.trimIndent(), "2"),
                "Vararg True" to Test("""
                    match ([]) {
                        [list*] ${'$'}{list == []}: log(1);
                    }
                """.trimIndent(), "1"),
                "Vararg False" to Test("""
                    match ([1]) {
                        [list*] ${'$'}{list == []}: log(1);
                        else: log(2);
                    }
                """.trimIndent(), "2"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("OrderedDestructure", listOf(
                "Empty" to Test("""
                    match ([]) {
                        []: log(1);
                    }
                """.trimIndent(), "1"),
                "Single" to Test("""
                    match ([1]) {
                        [element]: log(element);
                    }
                """.trimIndent(), "1"),
                "Multiple" to Test("""
                    match ([1, 2, 3]) {
                        [first, second, third]: { log(first); log(second); log(third); }
                    }
                """.trimIndent(), "123"),
                "Leading Varargs" to Test("""
                    match ([1, 2, 3]) {
                        [rest*, last]: { log(rest); log(last); }
                    }
                """.trimIndent(), "[1, 2]3"),
                "Middle Varargs" to Test("""
                    match ([1, 2, 3]) {
                        [first, rest*, last]: { log(first); log(rest); log(last); }
                    }
                """.trimIndent(), "1[2]3"),
                "Trailing Varargs" to Test("""
                    match ([1, 2, 3]) {
                        [first, rest*]: { log(first); log(rest); }
                    }
                """.trimIndent(), "1[2, 3]"),
                "Unmatched Type" to Test("""
                    val any: Any = null;
                    match (any) {
                        []: log(1);
                        else: log(2);
                    }
                """.trimIndent(), "2"),
                "Unmatched Arity" to Test("""
                    match ([1, 2, 3]) {
                        [x, y]: log(1);
                        else: log(2);
                    }
                """.trimIndent(), "2"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("NamedDestructure", listOf(
                "Empty" to Test("""
                    match ({}) {
                        {}: log(1);
                    }
                """.trimIndent(), "1"),
                "Single" to Test("""
                    match ({key: 1}) {
                        {key: value}: log(value);
                    }
                """.trimIndent(), "1"),
                "Multiple" to Test("""
                    match ({k1: 1, k2: 2, k3: 3}) {
                        {k1: v1, k2: v2, k3: v3}: { log(v1); log(v2); log(v3); }
                    }
                """.trimIndent(), "123"),
                "Key Only" to Test("""
                    match ({key: 1}) {
                        {key}: log(key);
                    }
                """.trimIndent(), "1"),
                "Varargs" to Test("""
                    match ({k1: 1, k2: 2, k3: 3}) {
                        {k1: v1, rest*}: { log(v1); log(rest); }
                    }
                """.trimIndent(), "1{k2=2, k3=3}"),
                "Unmatched Type" to Test("""
                    val any: Any = null;
                    match (any) {
                        {}: log(1);
                        else: log(2);
                    }
                """.trimIndent(), "2"),
                "Unmatched Keys" to Test("""
                    match ({x: 1, y: 2, z: 3}) {
                        {x, y}: log(1);
                        else: log(2);
                    }
                """.trimIndent(), "2"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("TypedDestructure", listOf(
                "Type" to Test("""
                    match (1) {
                        Integer: log(1);
                    }
                """.trimIndent(), "1"),
                "Pattern" to Test("""
                    match (1) {
                        Integer x: log(x);
                    }
                """.trimIndent(), "1"),
                "Unmatched Type" to Test("""
                    val any: Any = null;
                    match (any) {
                        Integer: log(1);
                        else: log(2);
                    }
                """.trimIndent(), "2"),
            )) { test("source", it.source, it.log, it.expected) }

            suite("VarargDestructure") {
                suite("List", listOf(
                    "Zero Or More" to Test("""
                        match ([]) {
                            [list*]: log(1);
                        }
                    """.trimIndent(), "1"),
                    "One Or More True" to Test("""
                        match ([1]) {
                            [list+]: log(1);
                        }
                    """.trimIndent(), "1"),
                    "One Or More False" to Test("""
                        match ([]) {
                            [list+]: log(1);
                            else: log(2);
                        }
                    """.trimIndent(), "2"),
                    "Operator Only" to Test("""
                        match ([]) {
                            [*]: log(1);
                        }
                    """.trimIndent(), "1"),
                    "Pattern True" to Test("""
                        match ([[1], [2], [3]]) {
                            [[x]*]: log(x);
                        }
                    """.trimIndent(), "[1, 2, 3]"),
                    "Pattern False" to Test("""
                        match ([[1], [], [3]]) {
                            [[x]*]: log(x);
                            else: log(2);
                        }
                    """.trimIndent(), "2"),
                )) { test("source", it.source, it.log, it.expected) }

                suite("Struct", listOf(
                    "Zero Or More" to Test("""
                        match ({}) {
                            {struct*}: log(1);
                        }
                    """.trimIndent(), "1"),
                    "One Or More True" to Test("""
                        match ({x: 1}) {
                            {struct+}: log(1);
                        }
                    """.trimIndent(), "1"),
                    "One Or More False" to Test("""
                        match ({}) {
                            {struct+}: log(1);
                            else: log(2);
                        }
                    """.trimIndent(), "2"),
                    "Operator Only" to Test("""
                        match ({}) {
                            {*}: log(1);
                        }
                    """.trimIndent(), "1"),
                    "Pattern True" to Test("""
                        match ({x: [1], y: [2], z: [3]}) {
                            {[x]*}: log(x);
                        }
                    """.trimIndent(), "{x=1, y=2, z=3}"),
                    "Pattern False" to Test("""
                        match ({x: [1], y: [], z: [3]}) {
                            {[x]*}: log(x);
                            else: log(2);
                        }
                    """.trimIndent(), "2"),
                )) { test("source", it.source, it.log, it.expected) }
            }
        }
    }

    private fun literal(value: Any?): Object {
        return when (value) {
            null -> Object(Type.NULLABLE[Type.DYNAMIC], null)
            is Boolean -> Object(Type.BOOLEAN, value)
            is BigInteger -> Object(Type.INTEGER, value)
            is BigDecimal -> Object(Type.DECIMAL, value)
            is RhovasAst.Atom -> Object(Type.ATOM, value)
            is String -> Object(Type.STRING, value)
            else -> throw AssertionError()
        }
    }

    private fun variable(name: String, type: Type, value: Any?): Variable.Definition {
        return Variable.Definition(Variable.Declaration(name, type, false), Object(type, value))
    }

    private fun test(rule: String, source: String, log: String?, expected: ((Scope.Definition) -> Object?)?, scope: (Scope.Definition) -> Unit = {}) {
        val input = Input("Test", source)
        val builder = StringBuilder()
        val scope = Scope.Definition(Library.SCOPE).also(scope)
        scope.functions.define(Function.Definition(Function.Declaration(Modifiers(Modifiers.Inheritance.DEFAULT), "log", listOf(), listOf(Variable.Declaration("obj", Type.ANY, false)), Type.ANY, listOf())) { arguments ->
            arguments[0].also { builder.append(it.methods.toString()) }
        })
        val expected = expected?.invoke(scope)
        try {
            val ast = RhovasParser(input).parse(rule)
            val ir = RhovasAnalyzer(scope).visit(ast)
            val obj = Evaluator(scope).visit(ir)
            assertEquals(expected ?: log?.let { Object(Type.VOID, Unit) }, obj)
            assertEquals(log ?: expected?.let { "" }, builder.toString())
        } catch (e: ParseException) {
            fail(input.diagnostic(e.summary, e.details, e.range, e.context))
        } catch (e: AnalyzeException) {
            fail(input.diagnostic(e.summary, e.details, e.range, e.context))
        } catch (e: EvaluateException) {
            if (expected != null || e.summary == "Broken evaluator invariant.") {
                fail(input.diagnostic(e.summary, e.details, e.range, e.context))
            }
        }
    }

}
