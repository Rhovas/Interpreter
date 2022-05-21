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

    @Nested
    inner class SourceTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testSource(name: String, input: String, expected: String?) {
            test(input, expected)
        }

        fun testSource(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Empty", "{}", ""),
                Arguments.of("Single", "{ log(1); }", "1"),
                Arguments.of("Multiple", "{ log(1); log(2); log(3); }", "123"),
            )
        }

        private fun test(input: String, expected: String?, scope: Scope = Scope(null)) {
            val log = StringBuilder()
            val function = Function.Definition("log", listOf(Pair("obj", Library.TYPES["Any"]!!)), Library.TYPES["Any"]!!)
            function.implementation = { arguments ->
                log.append(arguments[0].methods["toString", listOf()]!!.invoke(listOf()).value as String)
                arguments[0]
            }
            scope.functions.define(function)
            val ast = RhovasParser(Input("Test", input)).parse("source")
            try {
                Evaluator(scope).visit(RhovasAnalyzer(scope).visit(ast))
                Assertions.assertEquals(expected, log.toString())
            } catch (e: AnalyzeException) {
                if (expected != null) Assertions.fail<Unit>(e)
            } catch (e: EvaluateException) {
                if (expected != null) Assertions.fail<Unit>(e)
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
        inner class FunctionTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testFunction(name: String, input: String, expected: String?) {
                test(input, expected)
            }

            fun testFunction(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Declaration", "{ func name() { log(1); } name(); }", "1"),
                    Arguments.of("Single Parameter", "{ func name(x) { log(x); } name(1); }", "1"),
                    Arguments.of("Multiple Parameters", "{ func name(x, y, z) { log(x); log(y); log(z); } name(1, 2, 3); }", "123"),
                    Arguments.of("Return Value", "{ func name(): Integer { return 1; } log(name()); }", "1"),
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
                    Arguments.of("Var Declaration", "{ var name; log(name); }", null),
                    Arguments.of("Val Initialization", "{ val name = 1; log(name); }", "1"),
                    Arguments.of("Var Initialization", "{ var name = 1; log(name); }", "1"),
                    Arguments.of("Redeclaration", "{ val name = 1; val name = 2; log(name); }", null),
                )
            }

        }

        @Nested
        inner class AssignmentTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testVariable(name: String, input: String, expected: String?) {
                test(input, expected, Scope(null).also {
                    it.variables.define(Variable.Local.Runtime(
                        Variable.Local("variable", Library.TYPES["String"]!!, true),
                        Object(Library.TYPES["String"]!!, "initial"),
                    ))
                    it.variables.define(Variable.Local.Runtime(
                        Variable.Local("unassignable", Library.TYPES["String"]!!, false),
                        Object(Library.TYPES["String"]!!, "initial"),
                    ))
                })
            }

            fun testVariable(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Variable", "{ variable = \"final\"; log(variable); }", "final"),
                    Arguments.of("Undefined", "{ undefined = \"final\"; }", null),
                    Arguments.of("Unassignable", "{ unassignable = \"final\"; }", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testProperty(name: String, input: String, expected: String?) {
                test(input, expected, Scope(null).also {
                    val type = Type.Base("TestObject", listOf(), listOf(), Scope(null).also {
                        val getter = Function.Definition("property", listOf(Pair("this", Library.TYPES["Any"]!!)), Library.TYPES["Any"]!!)
                        getter.implementation = { arguments ->
                            (arguments[0].value as MutableMap<String, Object>)["property"]!!
                        }
                        it.functions.define(getter)
                        val setter = Function.Definition("property", listOf(Pair("this", Library.TYPES["Any"]!!), Pair("value", Library.TYPES["Any"]!!)), Library.TYPES["Any"]!!)
                        setter.implementation = { arguments ->
                            (arguments[0].value as MutableMap<String, Object>)["property"] = arguments[1]
                            Object(Library.TYPES["Void"]!!, Unit)
                        }
                        it.functions.define(setter)
                    }).reference
                    it.variables.define(Variable.Local.Runtime(
                        Variable.Local("object", type, false),
                        Object(type, mutableMapOf(
                            Pair("property", Object(Library.TYPES["String"]!!, "initial")),
                        )),
                    ))
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
                    it.variables.define(Variable.Local.Runtime(
                        Variable.Local("variable", Library.TYPES["String"]!!, false),
                        Object(Library.TYPES["String"]!!, "initial"),
                    ))
                    it.variables.define(Variable.Local.Runtime(
                        Variable.Local("list", Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!)), false),
                        Object(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!)), mutableListOf(
                            Object(Library.TYPES["String"]!!, "initial"),
                        )),
                    ))
                    it.variables.define(Variable.Local.Runtime(
                        Variable.Local("object", Library.TYPES["Object"]!!, false),
                        Object(Library.TYPES["Object"]!!, mutableMapOf(
                            Pair("key", Object(Library.TYPES["String"]!!, "initial")),
                        )),
                    ))
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
                    it.variables.define(Variable.Local.Runtime(
                        Variable.Local("number", Library.TYPES["Integer"]!!, true),
                        Object(Library.TYPES["Integer"]!!, BigInteger.ZERO),
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
                    it.functions.define(Function.Definition("Exception", listOf(Pair("message", Library.TYPES["String"]!!)), Library.TYPES["Exception"]!!).also {
                        it.implementation = { arguments -> Object(Library.TYPES["Exception"]!!, arguments[0].value as String) }
                    })
                    it.functions.define(Function.Definition("SubtypeException", listOf(Pair("message", Library.TYPES["String"]!!)), Library.TYPES["SubtypeException"]!!).also {
                        it.implementation = { arguments -> Object(Library.TYPES["SubtypeException"]!!, arguments[0].value as String) }
                    })
                })
            }

            fun testTry(): Stream<Arguments> {
                Library.TYPES["SubtypeException"] = Type.Reference(Type.Base(
                    "SubtypeException",
                    listOf(),
                    listOf(Library.TYPES["Exception"]!!),
                    Scope(null)
                ), listOf())
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
                        } catch (val e: SubtypeException) {
                            log(2);
                        } catch (val e: Exception) {
                            log(3);
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
                test(input, expected)
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
                test(input, expected)
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
                test(input, expected)
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

        private fun test(input: String, expected: String?, scope: Scope = Scope(null)) {
            val log = StringBuilder()
            val function = Function.Definition("log", listOf(Pair("obj", Library.TYPES["Any"]!!)), Library.TYPES["Any"]!!)
            function.implementation = { arguments ->
                log.append(arguments[0].methods["toString", listOf()]!!.invoke(listOf()).value as String)
                arguments[0]
            }
            scope.functions.define(function)
            val ast = RhovasParser(Input("Test", input)).parse("statement")
            try {
                Evaluator(scope).visit(RhovasAnalyzer(scope).visit(ast))
                Assertions.assertEquals(expected, log.toString())
            } catch (e: AnalyzeException) {
                if (expected != null) Assertions.fail<Unit>(e)
            } catch (e: EvaluateException) {
                if (expected != null) Assertions.fail<Unit>(e)
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
                        Object(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)), listOf<Object>()),
                    ),
                    Arguments.of("Single", "[1]",
                        Object(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)), listOf(
                            Object(Library.TYPES["Integer"]!!, BigInteger("1")),
                        )),
                    ),
                    Arguments.of("Multiple", "[1, 2, 3]",
                        Object(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)), listOf(
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
                    //TODO: Type checking
                    /*Arguments.of("Short Circuit", "true || invalid",
                        Object(Library.TYPES["Boolean"]!!, true),
                    ),*/
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
                    //TODO: Type checking
                    /*Arguments.of("Short Circuit", "false && invalid",
                        Object(Library.TYPES["Boolean"]!!, false),
                    ),*/
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
                        Object(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)), listOf(
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

            @Nested
            inner class VariableTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testVariable(name: String, input: String, expected: Object?) {
                    test(input, expected, Scope(null).also {
                        it.variables.define(Variable.Local.Runtime(
                            Variable.Local("variable", Library.TYPES["String"]!!, false),
                            Object(Library.TYPES["String"]!!, "variable"),
                        ))
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

            }

            @Nested
            inner class PropertyTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testProperty(name: String, input: String, expected: Object?) {
                    test(input, expected, Scope(null).also {
                        val type = Type.Base("TestObject", listOf(), listOf(), Scope(null).also {
                            val property = Function.Definition("property", listOf(Pair("this", Library.TYPES["Any"]!!)), Library.TYPES["Any"]!!)
                            property.implementation = { arguments ->
                                (arguments[0].value as Map<String, Object>)["property"]!!
                            }
                            it.functions.define(property)
                        }).reference
                        it.variables.define(Variable.Local.Runtime(
                            Variable.Local("object", type, false),
                            Object(type, mapOf(
                                Pair("property",  Object(Library.TYPES["String"]!!, "property")),
                            )),
                        ))
                    })
                }

                fun testProperty(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Property", "object.property",
                            Object(Library.TYPES["String"]!!, "property")
                        ),
                        //TODO: Type checking
                        /*Arguments.of("Nullable", "null?.property",
                            Object(Library.TYPES["Null"]!!, null),
                        ),*/
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
                        it.variables.define(Variable.Local.Runtime(
                            Variable.Local("variable", Library.TYPES["String"]!!, false),
                            Object(Library.TYPES["String"]!!, "variable"),
                        ))
                        it.variables.define(Variable.Local.Runtime(
                            Variable.Local("list", Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!)), false),
                            Object(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!)), mutableListOf(
                                Object(Library.TYPES["String"]!!, "element"),
                            )),
                        ))
                        it.variables.define(Variable.Local.Runtime(
                            Variable.Local("object", Library.TYPES["Object"]!!, false),
                            Object(Library.TYPES["Object"]!!, mutableMapOf(
                                Pair("key", Object(Library.TYPES["String"]!!, "value")),
                            )),
                        ))
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

        }

        @Nested
        inner class InvokeTests {

            @Nested
            inner class FunctionTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testFunction(name: String, input: String, expected: Object?) {
                    test(input, expected, Scope(null).also {
                        val function = Function.Definition("function", listOf(Pair("obj", Library.TYPES["Any"]!!)), Library.TYPES["Any"]!!)
                        function.implementation = { arguments ->
                            arguments[0]
                        }
                        it.functions.define(function)
                    })
                }

                fun testFunction(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Function", "function(\"argument\")",
                            Object(Library.TYPES["String"]!!, "argument")
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
                fun testMethod(name: String, input: String, expected: Object?) {
                    test(input, expected, Scope(null).also {
                        val type = Type.Base("TestObject", listOf(), listOf(), Scope(null).also {
                            val function = Function.Definition("method", listOf(Pair("this", Library.TYPES["Any"]!!), Pair("obj", Library.TYPES["Any"]!!)), Library.TYPES["Any"]!!)
                            function.implementation = { arguments ->
                                arguments[1]
                            }
                            it.functions.define(function)
                        }).reference
                        it.variables.define(Variable.Local.Runtime(
                            Variable.Local("object", type, false),
                            Object(type, mapOf(
                                Pair("property",  Object(Library.TYPES["String"]!!, "property")),
                            )),
                        ))
                        it.functions.define(Library.SCOPE.functions["range", listOf(Library.TYPES["Integer"]!!, Library.TYPES["Integer"]!!, Library.TYPES["Atom"]!!)]!!)
                    })
                }

                fun testMethod(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Method", "object.method(\"argument\")",
                            Object(Library.TYPES["String"]!!, "argument")
                        ),
                        //TODO: Type checking
                        /*Arguments.of("Nullable", "null?.method(\"argument\")",
                            Object(Library.TYPES["Null"]!!, null),
                        ),*/
                        Arguments.of("Coalesce", "1..add(2)",
                            Object(Library.TYPES["Integer"]!!, BigInteger("1")),
                        ),
                        Arguments.of("Invalid Arity", "object.method()", null),
                        Arguments.of("Undefined", "object.undefined()", null),
                    )
                }

            }

            @Nested
            inner class PipelineTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testPipeline(name: String, input: String, expected: Object?) {
                    test(input, expected, Scope(null).also {
                        val type = Type.Base("TestObject", listOf(), listOf(), Scope(null).also {
                            val function = Function.Definition("method", listOf(Pair("this", Library.TYPES["Any"]!!), Pair("obj", Library.TYPES["Any"]!!)), Library.TYPES["Any"]!!)
                            function.implementation = { arguments ->
                                arguments[1]
                            }
                            it.functions.define(function)
                        }).reference
                        it.variables.define(Variable.Local.Runtime(
                            Variable.Local("object", type, false),
                            Object(type, mapOf(
                                Pair("property",  Object(Library.TYPES["String"]!!, "property")),
                            )),
                        ))
                        it.functions.define(Library.SCOPE.functions["range", listOf(Library.TYPES["Integer"]!!, Library.TYPES["Integer"]!!, Library.TYPES["Atom"]!!)]!!)
                        val qualified = Type.Base("Qualified", listOf(), listOf(), Scope(null).also {
                            val function = Function.Definition("function", listOf(Pair("obj", Library.TYPES["Any"]!!)), Library.TYPES["Any"]!!)
                            function.implementation = { arguments ->
                                arguments[0]
                            }
                            it.functions.define(function)
                        }).reference
                        it.variables.define(Variable.Local.Runtime(
                            Variable.Local("Qualified", qualified, false),
                            Object(qualified, Unit),
                        ))
                    })
                }

                fun testPipeline(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Pipeline", "1.|range(2, :incl)",
                            Object(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Integer"]!!)), listOf(
                                Object(Library.TYPES["Integer"]!!, BigInteger("1")),
                                Object(Library.TYPES["Integer"]!!, BigInteger("2")),
                            )),
                        ),
                        Arguments.of("Qualified", "1.|Qualified.function()",
                            Object(Library.TYPES["Integer"]!!, BigInteger("1")),
                        ),
                        Arguments.of("Invalid Arity", "1.|range()", null),
                        Arguments.of("Undefined", "1.|undefined()", null),
                    )
                }

            }

        }

        @Nested
        inner class LambdaTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLambda(name: String, input: String, expected: String?) {
                val log = StringBuilder()
                val scope = Scope(null)
                val function = Function.Definition("log", listOf(Pair("obj", Library.TYPES["Any"]!!)), Library.TYPES["Any"]!!)
                function.implementation = { arguments ->
                    log.append(arguments[0].methods["toString", listOf()]!!.invoke(listOf()).value as String)
                    arguments[0]
                }
                scope.functions.define(function)
                val ast = RhovasParser(Input("Test", input)).parse("expression")
                try {
                    Evaluator(scope).visit(RhovasAnalyzer(scope).visit(ast))
                    Assertions.assertEquals(expected, log.toString())
                } catch (e: AnalyzeException) {
                    if (expected != null) Assertions.fail<Unit>(e)
                } catch (e: EvaluateException) {
                    if (expected != null) Assertions.fail<Unit>(e)
                }
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

        private fun test(input: String, expected: Object?, scope: Scope = Scope(null)) {
            val ast = RhovasParser(Input("Test", input)).parse("expression")
            try {
                val result = Evaluator(scope).visit(RhovasAnalyzer(scope).visit(ast))
                Assertions.assertEquals(expected, result)
            } catch (e: AnalyzeException) {
                if (expected != null) Assertions.fail<Unit>(e)
            } catch (e: EvaluateException) {
                if (expected != null) Assertions.fail<Unit>(e)
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
                test(input, expected)
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
                            _: log(_);
                        }
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class ValueTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testValue(name: String, input: String, expected: String?) {
                test(input, expected)
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
                test(input, expected)
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
                test(input, expected)
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
                test(input, expected)
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
                test(input, expected)
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

        private fun test(input: String, expected: String?, scope: Scope = Scope(null)) {
            val log = StringBuilder()
            val function = Function.Definition("log", listOf(Pair("obj", Library.TYPES["Any"]!!)), Library.TYPES["Any"]!!)
            function.implementation = { arguments ->
                log.append(arguments[0].methods["toString", listOf()]!!.invoke(listOf()).value as String)
                arguments[0]
            }
            scope.functions.define(function)
            val ast = RhovasParser(Input("Test", input)).parse("statement")
            try {
                Evaluator(scope).visit(RhovasAnalyzer(scope).visit(ast))
                Assertions.assertEquals(expected, log.toString())
            } catch (e: AnalyzeException) {
                if (expected != null) Assertions.fail<Unit>(e)
            } catch (e: EvaluateException) {
                if (expected != null) Assertions.fail<Unit>(e)
            }
        }

    }

}
