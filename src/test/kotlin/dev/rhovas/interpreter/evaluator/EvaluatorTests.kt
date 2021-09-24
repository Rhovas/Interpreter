package dev.rhovas.interpreter.evaluator

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
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
    inner class StatementTests {

        @Nested
        inner class BlockTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testBlock(name: String, input: String, expected: String?) {
                test(input, expected)
            }

            fun testBlock(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "{}", ""),
                    Arguments.of("Single", "{ log(1); }", "1"),
                    Arguments.of("Multiple", "{ log(1); log(2); log(3); }", "123"),
                )
            }

        }

        @Nested
        inner class ExpressionTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testExpression(name: String, input: String, expected: String?) {
                test(input, expected)
            }

            fun testExpression(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Function", "log(1);", "1"),
                    Arguments.of("Invalid", "1;", null),
                )
            }

        }

        @Nested
        inner class DeclarationTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testDeclaration(name: String, input: String, expected: String?) {
                test(input, expected)
            }

            fun testDeclaration(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Val Declaration", "{ val name; }", null),
                    Arguments.of("Var Declaration", "{ var name; log(name); }", "null"),
                    Arguments.of("Val Initialization", "{ val name = 1; log(name); }", "1"),
                    Arguments.of("Var Initialization", "{ var name = 1; log(name); }", "1"),
                    Arguments.of("Redeclaration", "{ val name = 1; val name = 2; log(name); }", "2"),
                )
            }

        }

        @Nested
        inner class AssignmentTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testVariable(name: String, input: String, expected: String?) {
                test(input, expected, Scope(null).also {
                    it.variables.define(Variable("variable", Object(Library.TYPES["String"]!!, "initial")))
                })
            }

            fun testVariable(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Variable", "{ variable = \"final\"; log(variable); }", "final"),
                    Arguments.of("Undefined", "{ undefined = \"final\"; }", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testProperty(name: String, input: String, expected: String?) {
                test(input, expected, Scope(null).also {
                    val type = Type("TestObject", Scope(null).also {
                        it.functions.define(Function("property", 1) { arguments ->
                            (arguments[0].value as MutableMap<String, Object>)["property"]!!
                        })
                        it.functions.define(Function("property", 2) { arguments ->
                            (arguments[0].value as MutableMap<String, Object>)["property"] = arguments[1]
                            Object(Library.TYPES["Void"]!!, Unit)
                        })
                    })
                    it.variables.define(Variable("object", Object(type, mutableMapOf(
                        Pair("property",  Object(Library.TYPES["String"]!!, "initial")),
                    ))))
                })
            }

            fun testProperty(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Property", "{ object.property = \"final\"; log(object.property); }", "final"),
                    Arguments.of("Undefined", "{ object.undefined = \"final\"; }", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIndex(name: String, input: String, expected: String?) {
                test(input, expected, Scope(null).also {
                    it.variables.define(Variable("variable", Object(Library.TYPES["String"]!!, "initial")))
                    it.variables.define(Variable("list", Object(Library.TYPES["List"]!!, mutableListOf(
                        Object(Library.TYPES["String"]!!, "initial"),
                    ))))
                    it.variables.define(Variable("object", Object(Library.TYPES["Object"]!!, mutableMapOf(
                        Pair("key", Object(Library.TYPES["String"]!!, "initial")),
                    ))))
                })
            }

            fun testIndex(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("List", "{ list[0] = \"final\"; log(list[0]); }", "final"),
                    Arguments.of("Object", "{ object[:key] = \"final\"; log(object[:key]); }", "final"),
                    Arguments.of("Invalid Arity", "{ object[] = \"final\"; }", null),
                    Arguments.of("Undefined", "{ variable[:key] = \"final\"; }", null), //TODO: Depends on Strings not supporting indexing
                )
            }

        }

        @Nested
        inner class IfTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIf(name: String, input: String, expected: String?) {
                test(input, expected)
            }

            fun testIf(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", "if (true) { log(1); }", "1"),
                    Arguments.of("False", "if (false) { log(1); }", ""),
                    Arguments.of("Else", "if (false) { log(1); } else { log(2); }", "2"),
                    Arguments.of("Invalid Condition", "if (1) { log(1); }", null),
                )
            }

        }

        @Nested
        inner class MatchTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testConditional(name: String, input: String, expected: String?) {
                test(input, expected)
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
                test(input, expected)
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
                test(input, expected)
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
                    Arguments.of("Invalid Iterable", """
                        for (val element in 1) {}
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class WhileTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testWhile(name: String, input: String, expected: String?) {
                test(input, expected, Scope(null).also {
                    it.variables.define(Variable("number", Object(Library.TYPES["Integer"]!!, BigInteger.ZERO)))
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
                    Arguments.of("Invalid Condition", """
                        while (1) {}
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class TryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testTry(name: String, input: String, expected: String?) {
                test(input, expected, Scope(null).also {
                    it.functions.define(Function("Exception", 1) { arguments ->
                        Object(Library.TYPES["Exception"]!!, arguments[0].value as String)
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
                            log(2);
                        } catch (val e) {
                            log(3);
                        }
                    """.trimIndent(), "13"),
                    Arguments.of("Catch Unthrown", """
                        try {
                            log(1);
                        } catch (val e) {
                            log(2);
                        }
                    """.trimIndent(), "1"),
                    Arguments.of("Finally", """
                        try {
                            log(1);
                        } finally {
                            log(2);
                        }
                    """.trimIndent(), "12"),
                    //TODO: Tests with other control flow modifications
                )
            }

        }

        //TODO: Scope tests

        private fun test(input: String, expected: String?, scope: Scope = Scope(null)) {
            val log = StringBuilder()
            scope.functions.define(Function("log", 1) { arguments ->
                log.append(arguments[0].methods["toString", 0]!!.invoke(listOf()).value as String)
                arguments[0]
            })
            val ast = RhovasParser(input).parse("statement")
            if (expected != null) {
                Evaluator(scope).visit(ast)
                Assertions.assertEquals(expected, log.toString())
            } else {
                Assertions.assertThrows(EvaluateException::class.java) { Evaluator(scope).visit(ast) }
            }
        }

    }

    @Nested
    inner class ExpressionTests {

        @Nested
        inner class LiteralTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testScalar(name: String, input: String, expected: Object?) {
                test(input, expected)
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
            fun testList(name: String, input: String, expected: Object?) {
                test(input, expected)
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
            fun testObject(name: String, input: String, expected: Object?) {
                test(input, expected)
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

        @Nested
        inner class GroupTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testGroup(name: String, input: String, expected: Object?) {
                test(input, expected)
            }

            fun testGroup(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Group", "(\"expression\")",
                        Object(Library.TYPES["String"]!!, "expression"),
                    ),
                    Arguments.of("Nested", "(((\"expression\")))",
                        Object(Library.TYPES["String"]!!, "expression"),
                    ),
                    Arguments.of("Binary", "(\"first\" + \"second\")",
                        Object(Library.TYPES["String"]!!, "firstsecond"),
                    ),
                )
            }

        }

        @Nested
        inner class UnaryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testUnary(name: String, input: String, expected: Object?) {
                test(input, expected)
            }

            fun testUnary(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Numerical Negation", "-1", //TODO: Depends on unsigned number literals
                        Object(Library.TYPES["Integer"]!!, BigInteger("-1")),
                    ),
                    Arguments.of("Logical Negation", "!true",
                        Object(Library.TYPES["Boolean"]!!, false),
                    ),
                    Arguments.of("Invalid", "-true", null),
                )
            }

        }

        @Nested
        inner class BinaryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLogicalOr(name: String, input: String, expected: Object?) {
                test(input, expected)
            }

            fun testLogicalOr(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", "false || true",
                        Object(Library.TYPES["Boolean"]!!, true),
                    ),
                    Arguments.of("False", "false || false",
                        Object(Library.TYPES["Boolean"]!!, false),
                    ),
                    Arguments.of("Short Circuit", "true || invalid",
                        Object(Library.TYPES["Boolean"]!!, true),
                    ),
                    Arguments.of("Invalid Left", "0 || true", null),
                    Arguments.of("Invalid Right", "false || 1", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLogicalAnd(name: String, input: String, expected: Object?) {
                test(input, expected)
            }

            fun testLogicalAnd(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", "true && true",
                        Object(Library.TYPES["Boolean"]!!, true),
                    ),
                    Arguments.of("False", "true && false",
                        Object(Library.TYPES["Boolean"]!!, false),
                    ),
                    Arguments.of("Short Circuit", "false && invalid",
                        Object(Library.TYPES["Boolean"]!!, false),
                    ),
                    Arguments.of("Invalid Left", "1 && false", null),
                    Arguments.of("Invalid Right", "true && 0", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testEquality(name: String, input: String, expected: Object?) {
                test(input, expected)
            }

            fun testEquality(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", "1 == 1",
                        Object(Library.TYPES["Boolean"]!!, true),
                    ),
                    Arguments.of("False", "1 != 1",
                        Object(Library.TYPES["Boolean"]!!, false),
                    ),
                    Arguments.of("Different Types", "1 == 1.0",
                        Object(Library.TYPES["Boolean"]!!, false),
                    ),
                    //Arguments.of("Invalid Left") TODO: Requires non-equatable types
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIdentity(name: String, input: String, expected: Object?) {
                test(input, expected)
            }

            fun testIdentity(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("True", "true === true",
                        Object(Library.TYPES["Boolean"]!!, true),
                    ),
                    Arguments.of("False", "[] !== []",
                        Object(Library.TYPES["Boolean"]!!, true),
                    ),
                    //Arguments.of("Different Types") TODO: Requires types with identical values (void?)
                    //TODO: Identity equality for implementation non-primitives (Integer/Decimal/String/Atom)
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testComparison(name: String, input: String, expected: Object?) {
                test(input, expected)
            }

            fun testComparison(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Less Than", "0 < 1",
                        Object(Library.TYPES["Boolean"]!!, true),
                    ),
                    Arguments.of("Greater Than", "0 > 1",
                        Object(Library.TYPES["Boolean"]!!, false),
                    ),
                    Arguments.of("Less Than Or Equal", "0 <= 1",
                        Object(Library.TYPES["Boolean"]!!, true),
                    ),
                    Arguments.of("Greater Than Or Equal", "0 >= 1",
                        Object(Library.TYPES["Boolean"]!!, false),
                    ),
                    Arguments.of("Less Than Equal", "0 < 0",
                        Object(Library.TYPES["Boolean"]!!, false),
                    ),
                    Arguments.of("Less Than Or Equal Equal", "0 <= 0",
                        Object(Library.TYPES["Boolean"]!!, true),
                    ),
                    Arguments.of("Invalid Left", "false < 1", null),
                    Arguments.of("Invalid Right", "0 < true", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testArithmetic(name: String, input: String, expected: Object?) {
                test(input, expected)
            }

            fun testArithmetic(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Integer Add", "1 + 2",
                        Object(Library.TYPES["Integer"]!!, BigInteger("3")),
                    ),
                    Arguments.of("Integer Subtract", "1 - 2",
                        Object(Library.TYPES["Integer"]!!, BigInteger("-1")),
                    ),
                    Arguments.of("Decimal Multiply", "1.2 * 2.3",
                        Object(Library.TYPES["Decimal"]!!, BigDecimal("2.76")),
                    ),
                    Arguments.of("Decimal Divide", "1.2 / 2.3",
                        Object(Library.TYPES["Decimal"]!!, BigDecimal("0.5")),
                    ),
                    Arguments.of("String Concat", "\"first\" + \"second\"",
                        Object(Library.TYPES["String"]!!, "firstsecond"),
                    ),
                    Arguments.of("List Concat", "[1] + [2]",
                        Object(Library.TYPES["List"]!!, listOf(
                            Object(Library.TYPES["Integer"]!!, BigInteger("1")),
                            Object(Library.TYPES["Integer"]!!, BigInteger("2")),
                        )),
                    ),
                    Arguments.of("Invalid Left", "true + false", null),
                )
            }

        }

        @Nested
        inner class AccessTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testVariable(name: String, input: String, expected: Object?) {
                test(input, expected, Scope(null).also {
                    it.variables.define(Variable("variable", Object(Library.TYPES["String"]!!, "variable")))
                })
            }

            fun testVariable(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Variable", "variable",
                        Object(Library.TYPES["String"]!!, "variable")
                    ),
                    Arguments.of("Undefined", "undefined", null)
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testProperty(name: String, input: String, expected: Object?) {
                test(input, expected, Scope(null).also {
                    val type = Type("TestObject", Scope(null).also {
                        it.functions.define(Function("property", 1) { arguments ->
                            (arguments[0].value as Map<String, Object>)["property"]!!
                        })
                    })
                    it.variables.define(Variable("object", Object(type, mapOf(
                        Pair("property",  Object(Library.TYPES["String"]!!, "property")),
                    ))))
                })
            }

            fun testProperty(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Property", "object.property",
                        Object(Library.TYPES["String"]!!, "property")
                    ),
                    Arguments.of("Undefined", "object.undefined", null)
                )
            }

        }

        @Nested
        inner class IndexTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIndex(name: String, input: String, expected: Object?) {
                test(input, expected, Scope(null).also {
                    it.variables.define(Variable("variable", Object(Library.TYPES["String"]!!, "variable")))
                    it.variables.define(Variable("list", Object(Library.TYPES["List"]!!, mutableListOf(
                        Object(Library.TYPES["String"]!!, "element"),
                    ))))
                    it.variables.define(Variable("object", Object(Library.TYPES["Object"]!!, mutableMapOf(
                        Pair("key", Object(Library.TYPES["String"]!!, "value")),
                    ))))
                })
            }

            fun testIndex(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("List", "list[0]",
                        Object(Library.TYPES["String"]!!, "element"),
                    ),
                    Arguments.of("Object", "object[:key]",
                        Object(Library.TYPES["String"]!!, "value"),
                    ),
                    Arguments.of("Invalid Arity", "list[]", null),
                    Arguments.of("Undefined", "variable[:key]", null), //TODO: Depends on Strings not supporting indexing
                )
            }

        }

        @Nested
        inner class FunctionTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testFunction(name: String, input: String, expected: Object?) {
                test(input, expected, Scope(null).also {
                    it.functions.define(Function("function", 1) { arguments ->
                        arguments[0]
                    })
                })
            }

            fun testFunction(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Function", "function(\"argument\")",
                        Object(Library.TYPES["String"]!!, "argument")
                    ),
                    Arguments.of("Invalid Arity", "function()", null),
                    Arguments.of("Undefined", "undefined", null)
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testMethod(name: String, input: String, expected: Object?) {
                test(input, expected, Scope(null).also {
                    val type = Type("TestObject", Scope(null).also {
                        it.functions.define(Function("method", 2) { arguments ->
                            arguments[1]
                        })
                    })
                    it.variables.define(Variable("object", Object(type, mapOf(
                        Pair("property",  Object(Library.TYPES["String"]!!, "property")),
                    ))))
                })
            }

            fun testMethod(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Method", "object.method(\"argument\")",
                        Object(Library.TYPES["String"]!!, "argument")
                    ),
                    Arguments.of("Invalid Arity", "object.method()", null),
                    Arguments.of("Undefined", "object.undefined()", null)
                )
            }

        }

        @Nested
        inner class LambdaTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLambda(name: String, input: String, expected: String?) {
                val log = StringBuilder()
                val scope = Scope(null)
                scope.functions.define(Function("log", 1) { arguments ->
                    log.append(arguments[0].methods["toString", 0]!!.invoke(listOf()).value as String)
                    arguments[0]
                })
                val ast = RhovasParser(input).parse("expression")
                if (expected != null) {
                    Evaluator(scope).visit(ast)
                    Assertions.assertEquals(expected, log.toString())
                } else {
                    Assertions.assertThrows(EvaluateException::class.java) { Evaluator(scope).visit(ast) }
                }
            }

            fun testLambda(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Lambda", """
                        [1, 2, 3].for { log(val); }
                    """.trimIndent(), "123"),
                    //TODO: Lambda scoping
                )
            }

        }

        private fun test(input: String, expected: Object?, scope: Scope = Scope(null)) {
            val ast = RhovasParser(input).parse("expression")
            if (expected != null) {
                Assertions.assertEquals(expected, Evaluator(scope).visit(ast))
            } else {
                Assertions.assertThrows(EvaluateException::class.java) { Evaluator(scope).visit(ast) }
            }
        }

    }

}
