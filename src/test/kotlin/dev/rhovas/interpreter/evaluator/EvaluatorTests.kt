package dev.rhovas.interpreter.evaluator

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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.math.BigInteger
import java.util.stream.Stream

class EvaluatorTests {

    @Nested
    inner class SourceTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testSource(name: String, input: String, expected: String?) {
            test("source", input, expected)
        }

        fun testSource(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Empty", """
                    
                """.trimIndent(), ""),
                Arguments.of("Single", """
                    log(1);
                """.trimIndent(), "1"),
                Arguments.of("Multiple", """
                    log(1);
                    log(2);
                    log(3);
                """.trimIndent(), "123"),
            )
        }

    }

    @Nested
    inner class ComponentTests {

        @Nested
        inner class StructTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testStruct(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testStruct(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Struct", """
                        struct Name {}
                        log(Name({}));
                    """.trimIndent(), "Name {}"),
                    Arguments.of("Default Field", """
                        struct Name { val field: Integer = 1; }
                        log(Name({}).field);
                    """.trimIndent(), "1"),
                    Arguments.of("Initialized Field", """
                        struct Name { val field: Integer; }
                        log(Name({field: 1}).field);
                    """.trimIndent(), "1"),
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
            fun testBlock(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testBlock(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        {}
                    """.trimIndent(), ""),
                    Arguments.of("Single", """
                        { log(1); }
                    """.trimIndent(), "1"),
                    Arguments.of("Multiple", """
                        { log(1); log(2); log(3); }
                    """.trimIndent(), "123"),
                )
            }

        }

        @Nested
        inner class ComponentTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testComponent(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testComponent(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Struct", """
                        struct Name {}
                        log(Name({}));
                    """.trimIndent(), "Name {}"),
                )
            }

        }

        @Nested
        inner class ExpressionTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testExpression(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testExpression(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Function", """
                        log(1);
                    """.trimIndent(), "1"),
                )
            }

        }

        @Nested
        inner class FunctionTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testFunction(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testFunction(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Declaration", """
                        func name() {
                            log(1);
                        }
                        name();
                    """.trimIndent(), "1"),
                    Arguments.of("Single Parameter", """
                        func name(x) {
                            log(x);
                        }
                        name(1);
                    """.trimIndent(), "1"),
                    Arguments.of("Multiple Parameters", """
                        func name(x, y, z) {
                            log(x);
                            log(y);
                            log(z);
                        }
                        name(1, 2, 3);
                    """.trimIndent(), "123"),
                    Arguments.of("Return Value", """
                        func name(): Integer {
                            return 1;
                        }
                        log(name());
                    """.trimIndent(), "1"),
                )
            }

        }

        @Nested
        inner class DeclarationTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testDeclaration(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testDeclaration(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Val Initialization", """
                        val name = 1;
                        log(name);
                    """.trimIndent(), "1"),
                    Arguments.of("Var Initialization", """
                        var name = 1;
                        log(name);
                    """.trimIndent(), "1"),
                )
            }

        }

        @Nested
        inner class AssignmentTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testVariable(name: String, input: String, expected: String?) {
                test("source", input, expected, Scope(Library.SCOPE).also {
                    it.variables.define(Variable.Local.Runtime(
                        Variable.Local("variable", type("String"), true),
                        Object(type("String"), "initial")
                    ))
                })
            }

            fun testVariable(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Variable", """
                        variable = "final";
                        log(variable);
                    """.trimIndent(), "final"),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testProperty(name: String, input: String, expected: String?) {
                test("source", input, expected, Scope(Library.SCOPE).also {
                    val type = Type.Base("TestObject", listOf(), listOf(), Scope(null).also {
                        it.functions.define(Function.Definition("property", listOf(), listOf("this" to type("Any")), type("Any"), listOf()).also {
                            it.implementation = { arguments ->
                                (arguments[0].value as MutableMap<String, Object>)["property"]!!
                            }
                        })
                        it.functions.define(Function.Definition("property", listOf(), listOf("this" to type("Any"), "value" to type("Any")), type("Any"), listOf()).also {
                            it.implementation = { arguments ->
                                (arguments[0].value as MutableMap<String, Object>)["property"] = arguments[1]
                                Object(type("Void"), Unit)
                            }
                        })
                    }).reference
                    it.variables.define(variable("object", type, mutableMapOf(
                        "property" to literal("initial"),
                    )))
                })
            }

            fun testProperty(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Property", """
                        object.property = "final";
                        log(object.property);
                    """.trimIndent(), "final"),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIndex(name: String, input: String, expected: String?) {
                test("source", input, expected, Scope(Library.SCOPE).also {
                    it.variables.define(variable("variable", type("String"), "initial"))
                    it.variables.define(variable("list", type("List", "String"), mutableListOf(
                        literal("initial"),
                    )))
                    it.variables.define(variable("object", type("Object"), mutableMapOf(
                        "key" to literal("initial"),
                    )))
                })
            }

            fun testIndex(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("List", """
                        list[0] = "final";
                        log(list[0]);
                    """.trimIndent(), "final"),
                    Arguments.of("Object", """
                        object[:key] = "final";
                        log(object[:key]);
                    """.trimIndent(), "final"),
                )
            }

        }

        @Nested
        inner class IfTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIf(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testIf(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", """
                        if (true) {
                            log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("False", """
                        if (false) {
                            log(1);
                        }
                    """.trimIndent(), ""),
                    Arguments.of("Else", """
                        if (false) {
                            log(1);
                        } else {
                            log(2);
                        }
                    """.trimIndent(), "2"),
                )
            }

        }

        @Nested
        inner class MatchTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testConditional(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testConditional(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("First", """
                        match {
                            true: log(1);
                            false: log(2);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Last", """
                        match {
                            false: log(1);
                            true: log(2);
                        }
                    """.trimIndent(), "2"),
                    Arguments.of("None", """
                        match {
                            false: log(1);
                            false: log(2);
                        }
                    """.trimIndent(), ""),
                    Arguments.of("Else", """
                        match {
                            else: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Else Condition True", """
                        match {
                            else true: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Else Condition False", """
                        match {
                            else false: log(1);
                        }
                    """.trimIndent(), null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testStructural(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testStructural(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("First", """
                        match (true) {
                            true: log(1);
                            false: log(2);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Last", """
                        match (false) {
                            true: log(1);
                            false: log(2);
                        }
                    """.trimIndent(), "2"),
                    Arguments.of("None", """
                        match (true) {
                            false: log(1);
                            false: log(2);
                        }
                    """.trimIndent(), null),
                    Arguments.of("Else", """
                        match (true) {
                            else: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Else Condition True", """
                        match (true) {
                            else true: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Else Condition False", """
                        match (true) {
                            else false: log(1);
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class ForTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testFor(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testFor(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        for (val element in []) {
                            log(1);
                        }
                    """.trimIndent(), ""),
                    Arguments.of("Single", """
                        for (val element in [1]) {
                            log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Multiple", """
                        for (val element in [1, 2, 3]) {
                            log(1);
                        }
                    """.trimIndent(), "111"),
                    Arguments.of("Element", """
                        for (val element in [1, 2, 3]) {
                            log(element);
                        }
                    """.trimIndent(), "123"),
                    Arguments.of("Break", """
                        for (val element in [1, 2, 3]) {
                            log(1);
                            break;
                            log(2);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Break Label", """
                        outer: for (val first in [1, 2, 3]) {
                            log(1);
                            for (val second in [4, 5, 6]) {
                                log(2);
                                break outer;
                                log(3);
                            }
                            log(4);
                        }
                    """.trimIndent(), "12"),
                    Arguments.of("Continue", """
                        for (val element in [1, 2, 3]) {
                            log(1);
                            continue;
                            log(2);
                        }
                    """.trimIndent(), "111"),
                    Arguments.of("Continue Label", """
                        outer: for (val first in [1, 2, 3]) {
                            log(1);
                            for (val second in [4, 5, 6]) {
                                log(2);
                                continue outer;
                                log(3);
                            }
                            log(4);
                        }
                    """.trimIndent(), "121212"),
                )
            }

        }

        @Nested
        inner class WhileTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testWhile(name: String, input: String, expected: String?) {
                test("source", input, expected, Scope(Library.SCOPE).also {
                    it.variables.define(Variable.Local.Runtime(
                        Variable.Local("number", type("Integer"), true),
                        literal(BigInteger.ZERO)
                    ))
                })
            }

            fun testWhile(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Zero Iterations", """
                        while (number < 0) {
                            log(1);
                        }
                    """.trimIndent(), ""),
                    Arguments.of("Single Iteration", """
                        while (number < 1) {
                            log(1);
                            number = number + 1;
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Multiple Iterations", """
                        while (number < 5) {
                            log(1);
                            number = number + 1;
                        }
                    """.trimIndent(), "11111"),
                    Arguments.of("Break", """
                        while (number < 5) {
                            log(1);
                            number = number + 1;
                            break;
                            log(2);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Break Label", """
                        outer: while (number < 5) {
                            log(1);
                            number = number + 1;
                            while (number < 5) {
                                log(2);
                                break outer;
                                log(3);
                            }
                            log(4);
                        }
                    """.trimIndent(), "12"),
                    Arguments.of("Continue", """
                        while (number < 5) {
                            log(1);
                            number = number + 1;
                            continue;
                            log(2);
                        }
                    """.trimIndent(), "11111"),
                    Arguments.of("Continue Label", """
                        outer: while (number < 5) {
                            log(1);
                            number = number + 1;
                            while (number < 5) {
                                log(2);
                                continue outer;
                                log(3);
                            }
                            log(4);
                        }
                    """.trimIndent(), "1212121214"),
                )
            }

        }

        @Nested
        inner class TryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testTry(name: String, input: String, expected: String?) {
                test("source", input, expected, Scope(Library.SCOPE).also { scope ->
                    scope.types.define(Type.Base("SubtypeException", listOf(), listOf(type("Exception")), Scope(null)).reference)
                    scope.functions.define(Function.Definition("Exception", listOf(), listOf("message" to type("String")), type("Exception"), listOf()).also {
                        it.implementation = { arguments -> Object(type("Exception"), arguments[0].value as String) }
                    })
                    scope.functions.define(Function.Definition("SubtypeException", listOf(), listOf("message" to type("String")), scope.types["SubtypeException"]!!, listOf()).also {
                        it.implementation = { arguments -> Object(scope.types["SubtypeException"]!!, arguments[0].value as String) }
                    })
                })
            }

            fun testTry(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Try", """
                        try {
                            log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Catch Thrown", """
                        try {
                            log(1);
                            throw Exception("message");
                        } catch (val e: Exception) {
                            log(2);
                        }
                    """.trimIndent(), "12"),
                    Arguments.of("Catch Unthrown", """
                        try {
                            log(1);
                        } catch (val e: Exception) {
                            log(2);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Catch Subtype", """
                        try {
                            log(1);
                            throw SubtypeException("message");
                        } catch (val e: Exception) {
                            log(2);
                        }
                    """.trimIndent(), "12"),
                    Arguments.of("Catch Supertype", """
                        try {
                            log(1);
                            throw Exception("message");
                        } catch (val e: SubtypeException) {
                            log(2);
                        } catch (val e: Exception) {
                            log(3);
                        }
                    """.trimIndent(), "13"),
                    Arguments.of("Finally", """
                        try {
                            log(1);
                        } finally {
                            log(2);
                        }
                    """.trimIndent(), "12"),
                    Arguments.of("Finally Catch", """
                        try {
                            log(1);
                            throw Exception("message");
                        } catch (val e: Exception) {
                            log(2);
                        } finally {
                            log(3);
                        }
                    """.trimIndent(), "123")
                )
            }

        }

        @Nested
        inner class AssertTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testAssert(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testAssert(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", """
                        assert true;
                    """.trimIndent(), ""),
                    Arguments.of("False", """
                        assert false;
                    """.trimIndent(), null),
                    //TODO: Test invalid condition
                    //TODO: Test message
                )
            }

        }

        @Nested
        inner class RequireTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testRequire(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testRequire(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", """
                        require true;
                    """.trimIndent(), ""),
                    Arguments.of("False", """
                        require false;
                    """.trimIndent(), null),
                    //TODO: Test invalid condition
                    //TODO: Test message
                )
            }

        }

        @Nested
        inner class EnsureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testEnsure(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testEnsure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", """
                        ensure true;
                    """.trimIndent(), ""),
                    Arguments.of("False", """
                        ensure false;
                    """.trimIndent(), null),
                    //TODO: Test invalid condition
                    //TODO: Test message
                )
            }

        }

        //TODO: Scope tests

    }

    @Nested
    inner class ExpressionTests {

        @Nested
        inner class LiteralTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testScalar(name: String, input: String, expected: Object?) {
                test("expression", input, expected)
            }

            fun testScalar(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Null", """
                        null
                    """.trimIndent(),
                        literal(null),
                    ),
                    Arguments.of("Boolean", """
                        true
                    """.trimIndent(),
                        literal(true),
                    ),
                    Arguments.of("Integer", """
                        123
                    """.trimIndent(),
                        literal(BigInteger("123")),
                    ),
                    Arguments.of("Decimal", """
                        123.456
                    """.trimIndent(),
                        literal(BigDecimal("123.456")),
                    ),
                    Arguments.of("String", """
                        "string"
                    """.trimIndent(),
                        literal("string"),
                    ),
                    Arguments.of("Atom", """
                        :atom
                    """.trimIndent(),
                        literal(RhovasAst.Atom("atom")),
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testList(name: String, input: String, expected: Object?) {
                test("expression", input, expected)
            }

            fun testList(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        []
                    """.trimIndent(),
                        Object(type("List", "Dynamic"), mutableListOf<Object>()),
                    ),
                    Arguments.of("Single", """
                        [1]
                    """.trimIndent(),
                        Object(type("List", "Dynamic"), mutableListOf(
                            literal(BigInteger("1")),
                        )),
                    ),
                    Arguments.of("Multiple", """
                        [1, 2, 3]
                    """.trimIndent(),
                        Object(type("List", "Dynamic"), mutableListOf(
                            literal(BigInteger("1")),
                            literal(BigInteger("2")),
                            literal(BigInteger("3")),
                        )),
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testObject(name: String, input: String, expected: Object?) {
                test("expression", input, expected)
            }

            fun testObject(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        {}
                    """.trimIndent(),
                        Object(type("Object"), mapOf<String, Object>()),
                    ),
                    Arguments.of("Single", """
                        {key: "value"}
                    """.trimIndent(),
                        Object(type("Object"), mapOf(
                            "key" to literal("value"),
                        )),
                    ),
                    Arguments.of("Multiple", """
                        {k1: "v1", k2: "v2", k3: "v3"}
                    """.trimIndent(),
                        Object(type("Object"), mapOf(
                            "k1" to literal("v1"),
                            "k2" to literal("v2"),
                            "k3" to literal("v3"),
                        )),
                    ),
                    //TODO: Arguments.of("Key Only", "{key}", null),
                )
            }

        }

        @Nested
        inner class GroupTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testGroup(name: String, input: String, expected: Object?) {
                test("expression", input, expected)
            }

            fun testGroup(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Group", """
                        ("expression")
                    """.trimIndent(),
                        literal("expression"),
                    ),
                    Arguments.of("Nested", """
                        ((("expression")))
                    """.trimIndent(),
                        literal("expression"),
                    ),
                    Arguments.of("Binary", """
                        ("first" + "second")
                    """.trimIndent(),
                        literal("firstsecond"),
                    ),
                )
            }

        }

        @Nested
        inner class UnaryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testUnary(name: String, input: String, expected: Object?) {
                test("expression", input, expected)
            }

            fun testUnary(): Stream<Arguments> {
                return Stream.of(
                    //TODO: Depends on unsigned number literals
                    Arguments.of("Numerical Negation", """
                        -1
                    """.trimIndent(),
                        literal(BigInteger("-1")),
                    ),
                    Arguments.of("Logical Negation", """
                        !true
                    """.trimIndent(),
                        literal(false),
                    ),
                )
            }

        }

        @Nested
        inner class BinaryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLogicalOr(name: String, input: String, expected: Object?) {
                test("expression", input, expected)
            }

            fun testLogicalOr(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", """
                        false || true
                    """.trimIndent(),
                        literal(true),
                    ),
                    Arguments.of("False", """
                        false || false
                    """.trimIndent(),
                        literal(false),
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLogicalAnd(name: String, input: String, expected: Object?) {
                test("expression", input, expected)
            }

            fun testLogicalAnd(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", """
                        true && true
                    """.trimIndent(),
                        literal(true),
                    ),
                    Arguments.of("False", """
                        true && false
                    """.trimIndent(),
                        literal(false),
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testEquality(name: String, input: String, expected: Object?) {
                test("expression", input, expected)
            }

            fun testEquality(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", """
                        1 == 1
                    """.trimIndent(),
                        literal(true),
                    ),
                    Arguments.of("False", """
                        1 != 1
                    """.trimIndent(),
                        literal(false),
                    ),
                    Arguments.of("Different Types", """
                        1 == 1.0
                    """.trimIndent(),
                        literal(false),
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIdentity(name: String, input: String, expected: Object?) {
                test("expression", input, expected)
            }

            fun testIdentity(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", """
                        true === true
                    """.trimIndent(),
                        literal(true),
                    ),
                    Arguments.of("False", """
                        [] !== []
                    """.trimIndent(),
                        literal(true),
                    ),
                    //Arguments.of("Different Types") TODO: Requires types with identical values (void?)
                    //TODO: Identity equality for implementation non-primitives (Integer/Decimal/String/Atom)
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testComparison(name: String, input: String, expected: Object?) {
                test("expression", input, expected)
            }

            fun testComparison(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Less Than", """
                        0 < 1
                    """.trimIndent(),
                        literal(true),
                    ),
                    Arguments.of("Greater Than", """
                        0 > 1
                    """.trimIndent(),
                        literal(false),
                    ),
                    Arguments.of("Less Than Or Equal", """
                        0 <= 1
                    """.trimIndent(),
                        literal(true),
                    ),
                    Arguments.of("Greater Than Or Equal", """
                        0 >= 1
                    """.trimIndent(),
                        literal(false),
                    ),
                    Arguments.of("Less Than Equal", """
                        0 < 0
                    """.trimIndent(),
                        literal(false),
                    ),
                    Arguments.of("Less Than Or Equal Equal", """
                        0 <= 0
                    """.trimIndent(),
                        literal(true),
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testArithmetic(name: String, input: String, expected: Object?) {
                test("expression", input, expected)
            }

            fun testArithmetic(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Integer Add", """
                        1 + 2
                    """.trimIndent(),
                        literal(BigInteger("3")),
                    ),
                    Arguments.of("Integer Subtract", """
                        1 - 2
                    """.trimIndent(),
                        literal(BigInteger("-1")),
                    ),
                    Arguments.of("Decimal Multiply", """
                        1.2 * 2.3
                    """.trimIndent(),
                        literal(BigDecimal("2.76")),
                    ),
                    Arguments.of("Decimal Divide", """
                        1.2 / 2.3
                    """.trimIndent(),
                        literal(BigDecimal("0.5")),
                    ),
                    Arguments.of("String Concat", """
                        "first" + "second"
                    """.trimIndent(),
                        literal("firstsecond"),
                    ),
                    Arguments.of("List Concat", """
                        [1] + [2]
                    """.trimIndent(),
                        Object(type("List", "Dynamic"), listOf(
                            literal(BigInteger("1")),
                            literal(BigInteger("2")),
                        )),
                    ),
                )
            }

        }

        @Nested
        inner class AccessTests {

            @Nested
            inner class VariableTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testVariable(name: String, input: String, expected: Object?) {
                    test("expression", input, expected, Scope(Library.SCOPE).also {
                        it.variables.define(variable("variable", type("String"), "variable"))
                    })
                }

                fun testVariable(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Variable", """
                            variable
                        """.trimIndent(),
                            literal("variable")
                        ),
                    )
                }

            }

            @Nested
            inner class PropertyTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testProperty(name: String, input: String, expected: Object?) {
                    test("expression", input, expected, Scope(Library.SCOPE).also {
                        val type = Type.Base("TestObject", listOf(), listOf(), Scope(null).also {
                            it.functions.define(Function.Definition("property", listOf(), listOf("this" to type("Any")), type("Any"), listOf()).also {
                                it.implementation = { arguments ->
                                    (arguments[0].value as Map<String, Object>)["property"]!!
                                }
                            })
                        }).reference
                        it.variables.define(variable("object", type, mapOf(
                            "property" to literal("property"),
                        )))
                        it.variables.define(Variable.Local.Runtime(
                            Variable.Local("nullObject", Type.Reference(type("Nullable").base, listOf(type)), false),
                            Object(type("Null"), null)
                        ))
                    })
                }

                fun testProperty(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Property", """
                            object.property
                        """.trimIndent(),
                            literal("property")
                        ),
                        Arguments.of("Coalesce", """
                            nullObject?.property
                        """.trimIndent(),
                            literal(null),
                        ),
                    )
                }

            }

            @Nested
            inner class IndexTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testIndex(name: String, input: String, expected: Object?) {
                    test("expression", input, expected, Scope(Library.SCOPE).also {
                        it.variables.define(variable("variable", type("String"), "variable"))
                        it.variables.define(variable("list", type("List", "String"), mutableListOf(
                            literal("element"),
                        )))
                        it.variables.define(variable("object", type("Object"), mutableMapOf(
                            "key" to literal("value"),
                        )))
                    })
                }

                fun testIndex(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("List", """
                            list[0]
                        """.trimIndent(),
                            literal("element"),
                        ),
                        Arguments.of("Object", """
                            object[:key]
                        """.trimIndent(),
                            literal("value"),
                        ),
                    )
                }

            }

        }

        @Nested
        inner class InvokeTests {

            @Nested
            inner class FunctionTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testFunction(name: String, input: String, expected: Object?) {
                    test("expression", input, expected, Scope(Library.SCOPE).also {
                        it.functions.define(Function.Definition("function", listOf(), listOf("obj" to type("Any")), type("Any"), listOf()).also {
                            it.implementation = { arguments ->
                                arguments[0]
                            }
                        })
                    })
                }

                fun testFunction(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Function", """
                            function("argument")
                        """.trimIndent(),
                            literal("argument")
                        ),
                    )
                }

            }

            @Nested
            inner class MethodTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testMethod(name: String, input: String, expected: Object?) {
                    test("expression", input, expected, Scope(Library.SCOPE).also {
                        val type = Type.Base("TestObject", listOf(), listOf(), Scope(null).also {
                            it.functions.define(Function.Definition("method", listOf(), listOf("this" to type("Any"), "obj" to type("Any")), type("Any"), listOf()).also {
                                it.implementation = { arguments ->
                                    arguments[1]
                                }
                            })
                        }).reference
                        it.variables.define(variable("object", type, mapOf(
                            "property" to literal("property"),
                        )))
                        it.variables.define(Variable.Local.Runtime(
                            Variable.Local("nullObject", Type.Reference(type("Nullable").base, listOf(type)), false),
                            Object(type("Null"), null)
                        ))
                    })
                }

                fun testMethod(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Method", """
                            object.method("argument")
                        """.trimIndent(),
                            literal("argument")
                        ),
                        Arguments.of("Coalesce", """
                            nullObject?.method("argument")
                        """.trimIndent(),
                            literal(null),
                        ),
                        Arguments.of("Cascade", """
                            1..add(2)
                        """.trimIndent(),
                            literal(BigInteger("1")),
                        ),
                    )
                }

            }

            @Nested
            inner class PipelineTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testPipeline(name: String, input: String, expected: Object?) {
                    test("expression", input, expected, Scope(Library.SCOPE).also {
                        val qualified = Type.Base("Qualified", listOf(), listOf(), Scope(null).also {
                            it.functions.define(Function.Definition("function", listOf(), listOf("obj" to type("Any")), type("Any"), listOf()).also {
                                it.implementation = { arguments ->
                                    arguments[0]
                                }
                            })
                        }).reference
                        it.variables.define(variable("Qualified", qualified, Unit))
                        it.variables.define(Variable.Local.Runtime(
                            Variable.Local("nullInteger", type("Nullable", "Integer"), false),
                            Object(type("Null"), null)
                        ))
                    })
                }

                fun testPipeline(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Pipeline", """
                            1.|range(2, :incl)
                        """.trimIndent(),
                            Object(type("List", "Integer"), mutableListOf(
                                literal(BigInteger("1")),
                                literal(BigInteger("2")),
                            )),
                        ),
                        Arguments.of("Qualified", """
                            1.|Qualified.function()
                        """.trimIndent(),
                            literal(BigInteger("1")),
                        ),
                        Arguments.of("Coalesce", """
                            nullInteger?.|range(2, :incl)
                        """.trimIndent(),
                            literal(null),
                        ),
                        Arguments.of("Cascade", """
                            1..|range(2, :incl)
                        """.trimIndent(),
                            literal(BigInteger("1")),
                        ),
                    )
                }

            }

        }

        @Nested
        inner class LambdaTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLambda(name: String, input: String, expected: String?) {
                test("expression", input, expected)
            }

            fun testLambda(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Lambda", """
                        [1, 2, 3].for { log(val); }
                    """.trimIndent(), "123"),
                    Arguments.of("Return", """
                        log([1, 2, 3].map { return val * val; })
                    """.trimIndent(), "[1, 4, 9]")
                    //TODO: Lambda scoping
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
            fun testVariable(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testVariable(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Variable", """
                        match (1) {
                            variable: log(variable);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Underscore", """
                        match (1) {
                            _: log("1");
                        }
                    """.trimIndent(), "1"),
                )
            }

        }

        @Nested
        inner class ValueTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testValue(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testValue(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Null", """
                        match (null) {
                            null: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Boolean True", """
                        match (true) {
                            true: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Boolean False", """
                        match (false) {
                            false: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Integer", """
                        match (0) {
                            0: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Decimal", """
                        match (0.0) {
                            0.0: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("String", """
                        match ("string") {
                            "string": log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Atom", """
                        match (:atom) {
                            :atom: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Interpolation", """
                        match (1 + 2) {
                            ${'$'}{1 + 2}: log(1);
                        }
                    """.trimIndent(), "1"),
                )
            }

        }

        @Nested
        inner class PredicateTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testPredicate(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testPredicate(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", """
                        match (0) {
                            num ${'$'}{true}: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("False", """
                        match (0) {
                            num ${'$'}{false}: log(1);
                            else: log(2);
                        }
                    """.trimIndent(), "2"),
                    Arguments.of("Variable True", """
                        match (0) {
                            num ${'$'}{num == 0}: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Variable False", """
                        match (1) {
                            num ${'$'}{num == 0}: log(1);
                            else: log(2);
                        }
                    """.trimIndent(), "2"),
                    Arguments.of("Vararg True", """
                        match ([]) {
                            [list*] ${'$'}{list == []}: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Vararg False", """
                        match ([1]) {
                            [list*] ${'$'}{list == []}: log(1);
                            else: log(2);
                        }
                    """.trimIndent(), "2"),
                )
            }

        }

        @Nested
        inner class OrderedDestructureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testOrderedDestructure(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testOrderedDestructure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        match ([]) {
                            []: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Single", """
                        match ([1]) {
                            [element]: log(element);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Multiple", """
                        match ([1, 2, 3]) {
                            [first, second, third]: { log(first); log(second); log(third); }
                        }
                    """.trimIndent(), "123"),
                    Arguments.of("Leading Varargs", """
                        match ([1, 2, 3]) {
                            [rest*, last]: { log(rest); log(last); }
                        }
                    """.trimIndent(), "[1, 2]3"),
                    Arguments.of("Middle Varargs", """
                        match ([1, 2, 3]) {
                            [first, rest*, last]: { log(first); log(rest); log(last); }
                        }
                    """.trimIndent(), "1[2]3"),
                    Arguments.of("Trailing Varargs", """
                        match ([1, 2, 3]) {
                            [first, rest*]: { log(first); log(rest); }
                        }
                    """.trimIndent(), "1[2, 3]"),
                )
            }

        }

        @Nested
        inner class NamedDestructureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testNamedDestructure(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testNamedDestructure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        match ({}) {
                            {}: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Single", """
                        match ({key: 1}) {
                            {key: value}: log(value);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Multiple", """
                        match ({k1: 1, k2: 2, k3: 3}) {
                            {k1: v1, k2: v2, k3: v3}: { log(v1); log(v2); log(v3); }
                        }
                    """.trimIndent(), "123"),
                    Arguments.of("Key Only", """
                        match ({key: 1}) {
                            {key}: log(key);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Varargs", """
                        match ({k1: 1, k2: 2, k3: 3}) {
                            {k1: v1, rest*}: { log(v1); log(rest); }
                        }
                    """.trimIndent(), "1{k2=2, k3=3}"),
                )
            }

        }

        @Nested
        inner class VarargDestructureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testVarargDestructure(name: String, input: String, expected: String?) {
                test("source", input, expected)
            }

            fun testVarargDestructure(): Stream<Arguments> {
                return Stream.of(
                    //TODO: Move into sequence/named destructuring
                    Arguments.of("Zero Or More", """
                        match ([]) {
                            [list*]: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("One Or More True", """
                        match ([1]) {
                            [list+]: log(1);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("One Or More False", """
                        match ([]) {
                            [list+]: log(1);
                            else: log(2);
                        }
                    """.trimIndent(), "2"),
                    Arguments.of("Operator Only", """
                        match ([]) {
                            [*]: log(1);
                        }
                    """.trimIndent(), "1"),
                )
            }

        }

    }

    private fun literal(value: Any?): Object {
        return when (value) {
            null -> Object(type("Null"), null)
            is Boolean -> Object(type("Boolean"), value)
            is BigInteger -> Object(type("Integer"), value)
            is BigDecimal -> Object(type("Decimal"), value)
            is RhovasAst.Atom -> Object(type("Atom"), value)
            is String -> Object(type("String"), value)
            else -> throw AssertionError()
        }
    }

    private fun variable(name: String, type: Type, value: Any?): Variable {
        return Variable.Local.Runtime(
            Variable.Local(name, type, false),
            Object(type, value)
        )
    }

    private fun type(name: String, vararg generics: String): Type {
        return Type.Reference(Library.TYPES[name]!!.base, generics.map { Library.TYPES[it]!! })
    }

    private fun test(rule: String, input: String, expected: String?, scope: Scope = Scope(Library.SCOPE)) {
        val log = StringBuilder()
        scope.functions.define(Function.Definition("log", listOf(), listOf("obj" to type("Any")), type("Any"), listOf()).also {
            it.implementation = { arguments ->
                log.append(arguments[0].methods["toString", listOf()]!!.invoke(listOf()).value as String)
                arguments[0]
            }
        })
        val input = Input("Test", input)
        try {
            val ast = RhovasParser(input).parse(rule)
            val ir = RhovasAnalyzer(scope).visit(ast)
            Evaluator(scope).visit(ir)
            Assertions.assertEquals(expected, log.toString())
        } catch (e: ParseException) {
            println(input.diagnostic(e.summary, e.details, e.range, e.context))
            Assertions.fail(e)
        } catch (e: AnalyzeException) {
            println(input.diagnostic(e.summary, e.details, e.range, e.context))
            Assertions.fail(e)
        } catch (e: EvaluateException) {
            if (expected != null || e.summary == "Broken evaluator invariant.") {
                println(input.diagnostic(e.summary, e.details, e.range, e.context))
                Assertions.fail<Unit>(e)
            }
        }
    }

    private fun test(rule: String, input: String, expected: Object?, scope: Scope = Scope(Library.SCOPE)) {
        val input = Input("Test", input)
        try {
            val ast = RhovasParser(input).parse(rule)
            val ir = RhovasAnalyzer(scope).visit(ast)
            val obj = Evaluator(scope).visit(ir)
            Assertions.assertEquals(expected, obj)
        } catch (e: ParseException) {
            println(input.diagnostic(e.summary, e.details, e.range, e.context))
            Assertions.fail(e)
        } catch (e: AnalyzeException) {
            println(input.diagnostic(e.summary, e.details, e.range, e.context))
            Assertions.fail(e)
        } catch (e: EvaluateException) {
            if (expected != null || e.summary == "Broken evaluator invariant.") {
                println(input.diagnostic(e.summary, e.details, e.range, e.context))
                Assertions.fail<Unit>(e)
            }
        }
    }

}
