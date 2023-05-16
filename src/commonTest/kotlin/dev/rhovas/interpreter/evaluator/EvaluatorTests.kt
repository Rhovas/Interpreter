package dev.rhovas.interpreter.evaluator

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.analyzer.AnalyzeException
import dev.rhovas.interpreter.analyzer.rhovas.RhovasAnalyzer
import dev.rhovas.interpreter.environment.Function
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

    data class Test(val source: String, val log: String? = null, val expected: ((Scope.Definition) -> Object)? = null)

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
        )) { test("source", it.source, it.log, it.expected) }

        suite("Component") {
            suite("Struct", listOf(
                "Struct" to Test("""
                    struct Name {}
                    log(Name({}));
                """.trimIndent(), "Name{}"),
                "Default Initializer" to Test("""
                    struct Name { val field: Integer = 1; }
                    log(Name({}).field);
                """.trimIndent(), "1"),
                "Default Initializer Value" to Test("""
                    struct Name { val field: Integer = 1; }
                    log(Name({field: 2}).field);
                """.trimIndent(), "2"),
                "Custom Initializer" to Test("""
                    struct Name {
                        var field: Integer;
                        init(field: Integer) {
                            this { field };
                        }
                    }
                    log(Name(1).field);
                """.trimIndent(), "1"),
                "Function" to Test("""
                    struct Name { func function(): Integer { return 1; } }
                    log(Name.function());
                """.trimIndent(), "1"),
                "Method" to Test("""
                    struct Name {
                        val field: Integer = 1;
                        func method(this): Integer { return this.field; }
                    }
                    log(Name({}).method());
                """.trimIndent(), "1"),
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
                )) { test("source", it.source, it.log, it.expected) }

                suite("Index", listOf(
                    "List" to Test("""
                        val list = ["initial"];
                        list[0] = "final";
                        log(list[0]);
                    """.trimIndent(), "final"),
                    //Disabled as Map literal type inference seem to be broken.
                    "!Object" to Test("""
                        val object = Map({ key: "initial" });
                        object[:key] = "final";
                        log(object[:key]);
                    """.trimIndent(), "final"),
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
                "Finally" to Test("""
                    try {
                        log(1);
                    } finally {
                        log(2);
                    }
                """.trimIndent(), "12"),
                "Finally Catch" to Test("""
                    try {
                        log(1);
                        throw Exception("message");
                    } catch (val e: Exception) {
                        log(2);
                    } finally {
                        log(3);
                    }
                """.trimIndent(), "123"),
            )) { test("source", it.source, it.log, it.expected) {
                it.types.define(Type.Base("SubtypeException", Scope.Definition(null)).reference.also { type ->
                    type.base.inherit(Type.EXCEPTION)
                    type.base.scope.functions.define(Function.Definition(Function.Declaration("", listOf(), listOf(Variable.Declaration("message", Type.STRING, false)), type, listOf())) { arguments ->
                        Object(type, arguments[0].value as String)
                    })
                })
            } }

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
                    "String" to Test("""
                        "string"
                    """.trimIndent()) {
                        literal("string")
                    },
                    "Atom" to Test("""
                        :atom
                    """.trimIndent()) {
                        literal(RhovasAst.Atom("atom"))
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
                        Object(Type.LIST[Type.INTEGER], mutableListOf(
                            literal(BigInteger.parseString("1")),
                        ))
                    },
                    "Multiple" to Test("""
                        [1, 2, 3]
                    """.trimIndent()) {
                        Object(Type.LIST[Type.INTEGER], mutableListOf(
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
                        Object(
                            Type.STRUCT[Type.Struct(mapOf())],
                            mapOf<String, Object>(),
                        )
                    },
                    "Single" to Test("""
                        {key: "value"}
                    """.trimIndent()) {
                        Object(
                            Type.STRUCT[Type.Struct(mapOf("key" to Variable.Declaration("key", Type.STRING, true)))],
                            mapOf("key" to literal("value"))
                        )
                    },
                    "Multiple" to Test("""
                        {k1: "v1", k2: "v2", k3: "v3"}
                    """.trimIndent()) {
                        Object(
                            Type.STRUCT[Type.Struct(mapOf("k1" to Variable.Declaration("k1", Type.STRING, true), "k2" to Variable.Declaration("k2", Type.STRING, true), "k3" to Variable.Declaration("k3", Type.STRING, true)))],
                            mapOf("k1" to literal("v1"), "k2" to literal("v2"), "k3" to literal("v3")),
                        )
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
                        Object(Type.LIST[Type.INTEGER], listOf(
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
            }

            suite("Lambda", listOf(
                "Lambda" to Test("""
                    [1, 2, 3].for { log(val); };
                """.trimIndent(), "123"),
                "Return" to Test("""
                    log([1, 2, 3].map { return val * val; });
                """.trimIndent(), "[1, 4, 9]"),
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
            )) { test("source", it.source, it.log, it.expected) }

            suite("VarargDestructure", listOf(
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
            )) { test("source", it.source, it.log, it.expected) }
        }
    }

    private fun literal(value: Any?): Object {
        return when (value) {
            null -> Object(Type.NULLABLE.ANY, null)
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

    private fun test(rule: String, source: String, log: String?, expected: ((Scope.Definition) -> Object)?, scope: (Scope.Definition) -> Unit = {}) {
        val input = Input("Test", source)
        val builder = StringBuilder()
        val scope = Scope.Definition(Library.SCOPE).also(scope)
        scope.functions.define(Function.Definition(Function.Declaration("log", listOf(), listOf(Variable.Declaration("obj", Type.ANY, false)), Type.ANY, listOf())) { arguments ->
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