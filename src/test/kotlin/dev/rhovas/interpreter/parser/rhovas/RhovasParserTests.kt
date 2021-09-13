package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.ParseException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.math.BigInteger
import java.util.stream.Stream

class RhovasParserTests {

    @Nested
    inner class StatementTests {

        @Nested
        inner class BlockTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testBlock(name: String, input: String, expected: RhovasAst.Statement.Block?) {
                test("statement", input, expected)
            }

            fun testBlock(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "{}",
                        RhovasAst.Statement.Block(listOf()),
                    ),
                    Arguments.of("Single", "{ statement; }",
                        RhovasAst.Statement.Block(listOf(
                            statement("statement"),
                        )),
                    ),
                    Arguments.of("Multiple", "{ first; second; third; }",
                        RhovasAst.Statement.Block(listOf(
                            statement("first"),
                            statement("second"),
                            statement("third"),
                        )),
                    ),
                    Arguments.of("Missing Closing Brace", "{ statement;", null),
                )
            }

        }

        @Nested
        inner class ExpressionTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testExpression(name: String, input: String, expected: RhovasAst.Statement.Expression?) {
                test("statement", input, expected)
            }

            fun testExpression(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Function", "function();",
                        RhovasAst.Statement.Expression(
                            RhovasAst.Expression.Function(null, "function", listOf()),
                        ),
                    ),
                    Arguments.of("Method", "receiver.method();",
                        RhovasAst.Statement.Expression(
                            RhovasAst.Expression.Function(expression("receiver"), "method", listOf()),
                        ),
                    ),
                    Arguments.of("Macro", "#macro();",
                        RhovasAst.Statement.Expression(
                            RhovasAst.Expression.Macro("macro", listOf()),
                        ),
                    ),
                    Arguments.of("Other", "variable;",
                        RhovasAst.Statement.Expression(
                            RhovasAst.Expression.Access(null, "variable"),
                        ),
                    ),
                    Arguments.of("Missing Semicolon", "expression", null),
                )
            }

        }

        @Nested
        inner class DeclarationTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testDeclaration(name: String, input: String, expected: RhovasAst.Statement.Declaration?) {
                test("statement", input, expected)
            }

            fun testDeclaration(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Val", "val name;",
                        RhovasAst.Statement.Declaration(false, "name", null),
                    ),
                    Arguments.of("Var", "var name;",
                        RhovasAst.Statement.Declaration(true, "name", null),
                    ),
                    Arguments.of("Value", "var name = value;",
                        RhovasAst.Statement.Declaration(true, "name", expression("value")),
                    ),
                    Arguments.of("Missing Name", "val;", null),
                    Arguments.of("Missing Value", "val name = ;", null),
                    Arguments.of("Missing Semicolon", "val name", null),
                )
            }

        }

        @Nested
        inner class AssignmentTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testAssignment(name: String, input: String, expected: RhovasAst.Statement.Assignment?) {
                test("statement", input, expected)
            }

            fun testAssignment(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Variable", "variable = value;",
                        RhovasAst.Statement.Assignment(
                            expression("variable"),
                            expression("value"),
                        ),
                    ),
                    Arguments.of("Field", "receiver.field = value;",
                        RhovasAst.Statement.Assignment(
                            RhovasAst.Expression.Access(expression("receiver"), "field"),
                            expression("value"),
                        ),
                    ),
                    Arguments.of("Index", "receiver[] = value;",
                        RhovasAst.Statement.Assignment(
                            RhovasAst.Expression.Index(expression("receiver"), listOf()),
                            expression("value"),
                        ),
                    ),
                    Arguments.of("Other", "function() = value;",
                        RhovasAst.Statement.Assignment(
                            RhovasAst.Expression.Function(null, "function", listOf()),
                            expression("value"),
                        ),
                    ),
                    Arguments.of("Missing Equals", "variable value;", null),
                    Arguments.of("Missing Value", "variable = ;", null),
                    Arguments.of("Missing Semicolon", "variable = value", null),
                )
            }

        }

        @Nested
        inner class IfTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIf(name: String, input: String, expected: RhovasAst.Statement.If?) {
                test("statement", input, expected)
            }

            fun testIf(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Then", "if (condition) thenStatement;",
                        RhovasAst.Statement.If(
                            expression("condition"),
                            statement("thenStatement"),
                            null,
                        ),
                    ),
                    Arguments.of("Empty Then", "if (condition) {}",
                        RhovasAst.Statement.If(expression("condition"), block(), null),
                    ),
                    Arguments.of("Else", "if (condition) {} else elseStatement;",
                        RhovasAst.Statement.If(expression("condition"), block(), statement("elseStatement")),
                    ),
                    Arguments.of("Empty Else", "if (condition) {} else {}",
                        RhovasAst.Statement.If(expression("condition"), block(), block()),
                    ),
                    Arguments.of("Missing Opening Parenthesis", "if condition) {}", null),
                    Arguments.of("Missing Condition", "if () {}", null),
                    Arguments.of("Missing Closing Parenthesis", "if (condition {}", null),
                    Arguments.of("Missing Else", "if (condition) {} {}", null),
                )
            }

        }

        @Nested
        inner class MatchTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testMatch(name: String, input: String, expected: RhovasAst.Statement.Match?) {
                test("statement", input, expected)
            }

            fun testMatch(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "match {}",
                        RhovasAst.Statement.Match(null, listOf(), null),
                    ),
                    Arguments.of("Single", "match { condition: statement; }",
                        RhovasAst.Statement.Match(
                            null,
                            listOf(Pair(expression("condition"), statement("statement"))),
                            null
                        ),
                    ),
                    Arguments.of("Multiple ", "match { c1: s1; c2: s2; c3: s3; }",
                        RhovasAst.Statement.Match(
                            null,
                            listOf(
                                Pair(expression("c1"), statement("s1")),
                                Pair(expression("c2"), statement("s2")),
                                Pair(expression("c3"), statement("s3"))
                            ),
                            null
                        ),
                    ),
                    Arguments.of("Argument", "match (argument) {}",
                        RhovasAst.Statement.Match(expression("argument"), listOf(), null),
                    ),
                    Arguments.of("Else", "match { else: statement; }",
                        RhovasAst.Statement.Match(
                            null,
                            listOf(),
                            Pair(null, statement("statement"))
                        ),
                    ),
                    Arguments.of("Else Condition", "match { else condition: statement; }",
                        RhovasAst.Statement.Match(
                            null,
                            listOf(),
                            Pair(expression("condition"), statement("statement"))
                        ),
                    ),
                    Arguments.of("Else With Cases", "match { c1: s1; c2: s2; else: s3; }",
                        RhovasAst.Statement.Match(
                            null,
                            listOf(
                                Pair(expression("c1"), statement("s1")),
                                Pair(expression("c2"), statement("s2")),
                            ),
                            Pair(null, statement("s3"))
                        ),
                    ),
                    Arguments.of("Else Inner", "match { c1: s2; else: s2; c3: s3; }", null),
                    Arguments.of("Else Multiple", "match { else: s1; else: s2; }", null),
                    Arguments.of("Missing Opening Brace", "match }", null),
                    Arguments.of("Missing Condition", "match { : statement; }", null), //parses : statement as an Atom
                    Arguments.of("Missing Colon", "match { condition statement; }", null),
                    Arguments.of("Missing Statement", "match { condition }", null),
                    Arguments.of("Missing Else Colon", "match { else statement; }", null), //parses statement as the condition
                    Arguments.of("Missing Else Statement", "match { else: }", null),
                )
            }

        }

        @Nested
        inner class ForTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testFor(name: String, input: String, expected: RhovasAst.Statement.For?) {
                test("statement", input, expected)
            }

            fun testFor(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("For", "for (val name in iterable) body;",
                        RhovasAst.Statement.For("name", expression("iterable"), statement("body")),
                    ),
                    Arguments.of("Empty Body", "for (val name in iterable) {}",
                        RhovasAst.Statement.For("name", expression("iterable"), block()),
                    ),
                    Arguments.of("Missing Opening Parenthesis", "for val name in iterable) {}", null),
                    Arguments.of("Missing Val", "for (name in iterable) {}", null),
                    Arguments.of("Missing Name", "for (val in iterable) {}", null),
                    Arguments.of("Missing In", "for (val name iterable) {}", null),
                    Arguments.of("Missing Iterable", "for (val name in) {}", null),
                    Arguments.of("Missing Closing Parenthesis", "for (val name in iterable {}", null),
                    Arguments.of("Missing Statement", "for (val name in iterable)", null),
                )
            }

        }

        @Nested
        inner class WhileTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testWhile(name: String, input: String, expected: RhovasAst.Statement.While?) {
                test("statement", input, expected)
            }

            fun testWhile(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("While", "while (condition) body;",
                        RhovasAst.Statement.While(expression("condition"), statement("body")),
                    ),
                    Arguments.of("Empty Body", "while (condition) {}",
                        RhovasAst.Statement.While(expression("condition"), block()),
                    ),
                    Arguments.of("Missing Opening Parenthesis", "while condition) {}", null),
                    Arguments.of("Missing Condition", "while () {}", null),
                    Arguments.of("Missing Closing Parenthesis", "while (condition {}", null),
                    Arguments.of("Missing Statement", "while (condition)", null),
                )
            }

        }

        @Nested
        inner class TryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testTry(name: String, input: String, expected: RhovasAst.Statement.Try?) {
                test("statement", input, expected)
            }

            fun testTry(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Try", "try body;",
                        RhovasAst.Statement.Try(statement("body"), listOf(), null),
                    ),
                    Arguments.of("Empty Body", "try {}",
                        RhovasAst.Statement.Try(block(), listOf(), null),
                    ),
                    Arguments.of("Catch", "try {} catch (val name) body;",
                        RhovasAst.Statement.Try(
                            block(),
                            listOf(RhovasAst.Statement.Try.Catch("name", statement("body"))),
                            null,
                        ),
                    ),
                    Arguments.of("Empty Catch", "try {} catch (val name) {}",
                        RhovasAst.Statement.Try(
                            block(),
                            listOf(RhovasAst.Statement.Try.Catch("name", block())),
                            null,
                        ),
                    ),
                    Arguments.of("Multiple Catch", "try {} catch (val first) {} catch (val second) {} catch (val third) {}",
                        RhovasAst.Statement.Try(
                            block(),
                            listOf(
                                RhovasAst.Statement.Try.Catch("first", block()),
                                RhovasAst.Statement.Try.Catch("second", block()),
                                RhovasAst.Statement.Try.Catch("third", block()),
                            ),
                            null,
                        ),
                    ),
                    Arguments.of("Finally", "try {} finally finallyStatement;",
                        RhovasAst.Statement.Try(block(), listOf(), statement("finallyStatement")),
                    ),
                    Arguments.of("Empty Finally", "try {} finally {}",
                        RhovasAst.Statement.Try(block(), listOf(), block()),
                    ),
                    Arguments.of("Both Catch & Finally", "try {} catch (val name) {} finally {}",
                        RhovasAst.Statement.Try(
                            block(),
                            listOf(RhovasAst.Statement.Try.Catch("name", block())),
                            block(),
                        ),
                    ),
                    Arguments.of("Missing Try Statement", "try", null),
                    Arguments.of("Missing Catch Opening Parenthesis", "try {} catch val name) {}", null),
                    Arguments.of("Missing Catch Val", "try {} catch (name) {}", null),
                    Arguments.of("Missing Catch Name", "try {} catch (val) {}", null),
                    Arguments.of("Missing Catch Closing Parenthesis", "try {} catch (val name {}", null),
                    Arguments.of("Missing Catch Statement", "try {} catch (val name)", null),
                    Arguments.of("Missing Finally Statement", "try {} finally", null),
                )
            }

        }

        @Nested
        inner class WithTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testWith(name: String, input: String, expected: RhovasAst.Statement.With?) {
                test("statement", input, expected)
            }

            fun testWith(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("With", "with (argument) {}",
                        RhovasAst.Statement.With(null, expression("argument"), block()),
                    ),
                    Arguments.of("Name", "with (val name = argument) {}",
                        RhovasAst.Statement.With("name", expression("argument"), block()),
                    ),
                    Arguments.of("Missing Opening Parenthesis", "with argument) {}", null),
                    Arguments.of("Missing Val", "with (name = argument) {}", null),
                    Arguments.of("Missing Name", "with (val = argument) {}", null),
                    Arguments.of("Missing Equals", "with (val name argument) {}", null),
                    Arguments.of("Missing Argument", "with () {}", null),
                    Arguments.of("Missing Closing Parenthesis", "with (argument {}", null),
                    Arguments.of("Missing Statement", "with (argument)", null),
                )
            }

        }

        @Nested
        inner class LabelTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLabel(name: String, input: String, expected: RhovasAst.Statement.Label?) {
                test("statement", input, expected)
            }

            fun testLabel(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Label", "label: statement;",
                        RhovasAst.Statement.Label("label", statement("statement")),
                    ),
                    Arguments.of("Loop", "label: while (condition) {}",
                        RhovasAst.Statement.Label("label",
                            RhovasAst.Statement.While(expression("condition"), block())
                        ),
                    ),
                    Arguments.of("Missing Statement", "label:", null),
                )
            }

        }

        @Nested
        inner class BreakTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testBreak(name: String, input: String, expected: RhovasAst.Statement.Break?) {
                test("statement", input, expected)
            }

            fun testBreak(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Break", "break;",
                        RhovasAst.Statement.Break(null)
                    ),
                    Arguments.of("Label", "break label;",
                        RhovasAst.Statement.Break("label")
                    ),
                    Arguments.of("Missing Semicolon", "break", null),
                )
            }

        }

        @Nested
        inner class ContinueTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testContinue(name: String, input: String, expected: RhovasAst.Statement.Continue?) {
                test("statement", input, expected)
            }

            fun testContinue(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Continue", "continue;",
                        RhovasAst.Statement.Continue(null)
                    ),
                    Arguments.of("Label", "continue label;",
                        RhovasAst.Statement.Continue("label")
                    ),
                    Arguments.of("Missing Semicolon", "continue", null),
                )
            }

        }

        @Nested
        inner class ReturnTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testReturn(name: String, input: String, expected: RhovasAst.Statement.Return?) {
                test("statement", input, expected)
            }

            fun testReturn(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Return", "return;",
                        RhovasAst.Statement.Return(null)
                    ),
                    Arguments.of("Return", "return value;",
                        RhovasAst.Statement.Return(expression("value"))
                    ),
                    Arguments.of("Missing Semicolon", "return", null),
                )
            }

        }

        @Nested
        inner class ThrowTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testThrow(name: String, input: String, expected: RhovasAst.Statement.Throw?) {
                test("statement", input, expected)
            }

            fun testThrow(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Throw", "throw exception;",
                        RhovasAst.Statement.Throw(expression("exception"))
                    ),
                    Arguments.of("Missing Exception", "throw;", null),
                    Arguments.of("Missing Semicolon", "throw exception", null),
                )
            }

        }

        @Nested
        inner class AssertTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testAssert(name: String, input: String, expected: RhovasAst.Statement.Assert?) {
                test("statement", input, expected)
            }

            fun testAssert(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Assert", "assert condition;",
                        RhovasAst.Statement.Assert(expression("condition"), null),
                    ),
                    Arguments.of("Message", "assert condition: message;",
                        RhovasAst.Statement.Assert(expression("condition"), expression("message")),
                    ),
                    Arguments.of("Missing Condition", "assert;", null),
                    Arguments.of("Missing Colon", "assert condition message;", null),
                    Arguments.of("Missing Message", "assert condition: ;", null),
                    Arguments.of("Missing Semicolon", "assert condition", null),
                )
            }

        }

        @Nested
        inner class RequireTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testRequire(name: String, input: String, expected: RhovasAst.Statement.Require?) {
                test("statement", input, expected)
            }

            fun testRequire(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Require", "require condition;",
                        RhovasAst.Statement.Require(expression("condition"), null),
                    ),
                    Arguments.of("Message", "require condition: message;",
                        RhovasAst.Statement.Require(expression("condition"), expression("message")),
                    ),
                    Arguments.of("Missing Condition", "require;", null),
                    Arguments.of("Missing Colon", "require condition message;", null),
                    Arguments.of("Missing Message", "require condition: ;", null),
                    Arguments.of("Missing Semicolon", "require condition", null),
                )
            }

        }

        @Nested
        inner class EnsureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testEnsure(name: String, input: String, expected: RhovasAst.Statement.Ensure?) {
                test("statement", input, expected)
            }

            fun testEnsure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Ensure", "ensure condition;",
                        RhovasAst.Statement.Ensure(expression("condition"), null),
                    ),
                    Arguments.of("Message", "ensure condition: message;",
                        RhovasAst.Statement.Ensure(expression("condition"), expression("message")),
                    ),
                    Arguments.of("Missing Condition", "ensure;", null),
                    Arguments.of("Missing Colon", "ensure condition message;", null),
                    Arguments.of("Missing Message", "ensure condition: ;", null),
                    Arguments.of("Missing Semicolon", "ensure condition", null),
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
            fun testScalar(name: String, input: String, expected: Any?) {
                test("expression", input, RhovasAst.Expression.Literal(expected))
            }

            fun testScalar(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Null", "null", null),
                    Arguments.of("Boolean True", "true", true),
                    Arguments.of("Boolean False", "false", false),
                    Arguments.of("Integer", "123", BigInteger("123")),
                    Arguments.of("Integer Above Long Max", "1" + "0".repeat(19), BigInteger("1" + "0".repeat(19))),
                    Arguments.of("Decimal", "123.456", BigDecimal("123.456")),
                    Arguments.of("Decimal Above Double Max", "1" + "0".repeat(308) + ".0", BigDecimal("1" + "0".repeat(308) + ".0")),
                    Arguments.of("String", "\"string\"", "string"),
                    Arguments.of("String Escapes", "\"\\n\\r\\t\\\"\\\$\\\\\"", "\n\r\t\"\$\\"),
                    Arguments.of("Atom", ":atom", RhovasAst.Atom("atom")),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testList(name: String, input: String, expected: List<RhovasAst.Expression>?) {
                test("expression", input, expected?.let { RhovasAst.Expression.Literal(it) })
            }

            fun testList(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "[]", listOf<RhovasAst.Expression>()),
                    Arguments.of("Single", "[element]", listOf(
                        expression("element"),
                    )),
                    Arguments.of("Multiple", "[first, second, third]", listOf(
                        expression("first"),
                        expression("second"),
                        expression("third"),
                    )),
                    Arguments.of("Trailing Comma", "[first, second,]", listOf(
                        expression("first"),
                        expression("second"),
                    )),
                    Arguments.of("Missing Comma", "[first second]", null),
                    Arguments.of("Missing Closing Bracket", "[element", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testObject(name: String, input: String, expected: Map<String, RhovasAst.Expression>?) {
                test("expression", input, expected?.let { RhovasAst.Expression.Literal(it) })
            }

            fun testObject(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "{}", mapOf<String, RhovasAst.Expression>()),
                    Arguments.of("Single", "{key: value}", mapOf(
                        Pair("key", expression("value")),
                    )),
                    Arguments.of("Multiple", "{k1: v1, k2: v2, k3: v3}", mapOf(
                        Pair("k1", expression("v1")),
                        Pair("k2", expression("v2")),
                        Pair("k3", expression("v3")),
                    )),
                    Arguments.of("Trailing Comma", "{k1: v1, k2: v2,}", mapOf(
                        Pair("k1", expression("v1")),
                        Pair("k2", expression("v2")),
                    )),
                    Arguments.of("Key Only", "{key}", mapOf(
                        Pair("key", expression("key"))
                    )),
                    Arguments.of("Invalid Key", "{\"key\": value}", null),
                    Arguments.of("Missing Key", "{: value}", null),
                    Arguments.of("Missing Colon", "{key value}", null),
                    Arguments.of("Missing Comma", "{k1: v1 k2: v2}", null),
                    Arguments.of("Missing Closing Bracket", "{key: value", null),
                )
            }

        }

        @Nested
        inner class GroupTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testGroup(name: String, input: String, expected: RhovasAst.Expression.Group?) {
                test("expression", input, expected)
            }

            fun testGroup(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Group", "(expression)",
                        RhovasAst.Expression.Group(expression("expression"))
                    ),
                    Arguments.of("Nested", "(((expression)))",
                        RhovasAst.Expression.Group(
                            RhovasAst.Expression.Group(
                                RhovasAst.Expression.Group(
                                    expression("expression"),
                                ),
                            ),
                        ),
                    ),
                    Arguments.of("Binary", "(first + second)",
                        RhovasAst.Expression.Group(
                            RhovasAst.Expression.Binary("+",
                                expression("first"),
                                expression("second"),
                            ),
                        ),
                    ),
                    Arguments.of("Empty", "()", null),
                    Arguments.of("Tuple", "(first, second)", null),
                    Arguments.of("Missing Closing Parenthesis", "(expression", null),
                )
            }

        }

        @Nested
        inner class UnaryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testUnary(name: String, input: String, expected: RhovasAst.Expression.Unary?) {
                test("expression", input, expected)
            }

            fun testUnary(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Numerical Negation", "-expression",
                        RhovasAst.Expression.Unary("-", expression("expression"))
                    ),
                    Arguments.of("Logical Negation", "!expression",
                        RhovasAst.Expression.Unary("!", expression("expression"))
                    ),
                    Arguments.of("Multiple", "-!expression",
                        RhovasAst.Expression.Unary("-",
                            RhovasAst.Expression.Unary("!", expression("expression"))
                        )
                    ),
                    Arguments.of("Invalid Operator", "+expression", null),
                )
            }

        }

        @Nested
        inner class BinaryTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testBinary(name: String, input: String, expected: RhovasAst.Expression.Binary?) {
                test("expression", input, expected)
            }

            fun testBinary(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Multiplicative", "left * right",
                        RhovasAst.Expression.Binary("*",
                            expression("left"),
                            expression("right"),
                        ),
                    ),
                    Arguments.of("Additive", "left + right",
                        RhovasAst.Expression.Binary("+",
                            expression("left"),
                            expression("right"),
                        ),
                    ),
                    Arguments.of("Comparison", "left < right",
                        RhovasAst.Expression.Binary("<",
                            expression("left"),
                            expression("right"),
                        ),
                    ),
                    Arguments.of("Equality", "left == right",
                        RhovasAst.Expression.Binary("==",
                            expression("left"),
                            expression("right"),
                        ),
                    ),
                    Arguments.of("Logical And", "left && right",
                        RhovasAst.Expression.Binary("&&",
                            expression("left"),
                            expression("right"),
                        ),
                    ),
                    Arguments.of("Logical Or", "left || right",
                        RhovasAst.Expression.Binary("||",
                            expression("left"),
                            expression("right"),
                        ),
                    ),
                    Arguments.of("Left Precedence", "first * second + third < fourth == fifth && sixth || seventh",
                        RhovasAst.Expression.Binary("||",
                            RhovasAst.Expression.Binary("&&",
                                RhovasAst.Expression.Binary("==",
                                    RhovasAst.Expression.Binary("<",
                                        RhovasAst.Expression.Binary("+",
                                            RhovasAst.Expression.Binary("*",
                                                expression("first"),
                                                expression("second"),
                                            ),
                                            expression("third"),
                                        ),
                                        expression("fourth"),
                                    ),
                                    expression("fifth"),
                                ),
                                expression("sixth"),
                            ),
                            expression("seventh"),
                        ),
                    ),
                    Arguments.of("Right Precedence", "first || second && third == fourth < fifth + sixth * seventh",
                        RhovasAst.Expression.Binary("||",
                            expression("first"),
                            RhovasAst.Expression.Binary("&&",
                                expression("second"),
                                RhovasAst.Expression.Binary("==",
                                    expression("third"),
                                    RhovasAst.Expression.Binary("<",
                                        expression("fourth"),
                                        RhovasAst.Expression.Binary("+",
                                            expression("fifth"),
                                            RhovasAst.Expression.Binary("*",
                                                expression("sixth"),
                                                expression("seventh"),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    Arguments.of("Equal Precedence", "first < second <= third",
                        RhovasAst.Expression.Binary("<=",
                            RhovasAst.Expression.Binary("<",
                                expression("first"),
                                expression("second"),
                            ),
                            expression("third"),
                        ),
                    ),
                    Arguments.of("Invalid Operator", "first % second", null),
                    Arguments.of("Missing Operator", "first second", null),
                    Arguments.of("Missing Right", "first +", null),
                )
            }

        }

        @Nested
        inner class AccessTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testVariable(name: String, input: String, expected: RhovasAst.Expression.Access?) {
                test("expression", input, expected)
            }

            fun testVariable(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Variable", "variable",
                        RhovasAst.Expression.Access(null, "variable")
                    ),
                    Arguments.of("Underscore", "_",
                        RhovasAst.Expression.Access(null, "_")
                    ),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testField(name: String, input: String, expected: RhovasAst.Expression.Access?) {
                test("expression", input, expected)
            }

            fun testField(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Field", "receiver.field",
                        RhovasAst.Expression.Access(expression("receiver"),"field"),
                    ),
                    Arguments.of("Multiple Fields", "receiver.first.second.third",
                        RhovasAst.Expression.Access(
                            RhovasAst.Expression.Access(
                                RhovasAst.Expression.Access(expression("receiver"), "first"),
                                "second",
                            ),
                            "third",
                        ),
                    ),
                )
            }

        }

        @Nested
        inner class IndexTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIndex(name: String, input: String, expected: RhovasAst.Expression.Index?) {
                test("expression", input, expected)
            }

            fun testIndex(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Zero Arguments", "receiver[]",
                        RhovasAst.Expression.Index(expression("receiver"), listOf()),
                    ),
                    Arguments.of("Single Argument", "receiver[argument]",
                        RhovasAst.Expression.Index(expression("receiver"), listOf(
                            expression("argument"),
                        )),
                    ),
                    Arguments.of("Multiple Arguments", "receiver[first, second, third]",
                        RhovasAst.Expression.Index(expression("receiver"), listOf(
                            expression("first"),
                            expression("second"),
                            expression("third"),
                        )),
                    ),
                    Arguments.of("Multiple Indexes", "receiver[first][second][third]",
                        RhovasAst.Expression.Index(
                            RhovasAst.Expression.Index(
                                RhovasAst.Expression.Index(expression("receiver"), listOf(
                                    expression("first"),
                                )),
                                listOf(expression("second")),
                            ),
                            listOf(expression("third")),
                        ),
                    ),
                    Arguments.of("Trailing Comma", "receiver[argument,]",
                        RhovasAst.Expression.Index(expression("receiver"), listOf(
                            expression("argument"),
                        )),
                    ),
                    Arguments.of("Missing Comma", "receiver[first second]", null),
                    Arguments.of("Missing Closing Bracket", "receiver[argument", null),
                )
            }

        }

        @Nested
        inner class FunctionTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testFunction(name: String, input: String, expected: RhovasAst.Expression.Function?) {
                test("expression", input, expected)
            }

            fun testFunction(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Zero Arguments", "function()",
                        RhovasAst.Expression.Function(null, "function", listOf())
                    ),
                    Arguments.of("Single Argument", "function(argument)",
                        RhovasAst.Expression.Function(null, "function", listOf(
                            expression("argument"),
                        )),
                    ),
                    Arguments.of("Multiple Arguments", "function(first, second, third)",
                        RhovasAst.Expression.Function(null, "function", listOf(
                            expression("first"), expression("second"), expression("third"),
                        )),
                    ),
                    Arguments.of("Trailing Comma", "function(argument,)",
                        RhovasAst.Expression.Function(null, "function", listOf(
                            expression("argument"),
                        )),
                    ),
                    Arguments.of("Missing Comma", "function(first second)", null),
                    Arguments.of("Missing Closing Parenthesis", "function(argument", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testMethod(name: String, input: String, expected: RhovasAst.Expression.Function?) {
                test("expression", input, expected)
            }

            fun testMethod(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Zero Arguments", "receiver.method()",
                        RhovasAst.Expression.Function(expression("receiver"), "method", listOf())
                    ),
                    Arguments.of("Single Argument", "receiver.method(argument)",
                        RhovasAst.Expression.Function(expression("receiver"), "method", listOf(
                            expression("argument"),
                        )),
                    ),
                    Arguments.of("Multiple Arguments", "receiver.method(first, second, third)",
                        RhovasAst.Expression.Function(expression("receiver"), "method", listOf(
                            expression("first"),
                            expression("second"),
                            expression("third"),
                        )),
                    ),
                    Arguments.of("Trailing Comma", "receiver.method(argument,)",
                        RhovasAst.Expression.Function(expression("receiver"), "method", listOf(
                            expression("argument"),
                        )),
                    ),
                    Arguments.of("Multiple Methods", "receiver.first().second().third()",
                        RhovasAst.Expression.Function(
                            RhovasAst.Expression.Function(
                                RhovasAst.Expression.Function(expression("receiver"), "first", listOf()),
                                "second",
                                listOf(),
                            ),
                            "third",
                            listOf(),
                        ),
                    ),
                    Arguments.of("Missing Comma", "receiver.method(first second)", null),
                    Arguments.of("Missing Closing Parenthesis", "receiver.method(argument", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLambda(name: String, input: String, expected: RhovasAst.Expression.Function?) {
                test("expression", input, expected)
            }

            fun testLambda(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Lambda", "function { body; }",
                        RhovasAst.Expression.Function(null, "function", listOf(
                            RhovasAst.Expression.Lambda(listOf(), RhovasAst.Statement.Block(listOf(statement("body")))),
                        )),
                    ),
                    Arguments.of("Empty Block", "function {}",
                        RhovasAst.Expression.Function(null, "function", listOf(
                            RhovasAst.Expression.Lambda(listOf(), block()),
                        )),
                    ),
                    Arguments.of("Argument", "function(argument) {}",
                        RhovasAst.Expression.Function(null, "function", listOf(
                            expression("argument"),
                            RhovasAst.Expression.Lambda(listOf(), block()),
                        )),
                    ),
                    Arguments.of("Single Parameter", "function |parameter| {}",
                        RhovasAst.Expression.Function(null, "function", listOf(
                            RhovasAst.Expression.Lambda(listOf("parameter"), block()),
                        )),
                    ),
                    Arguments.of("Multiple Parameters", "function |first, second, third| {}",
                        RhovasAst.Expression.Function(null, "function", listOf(
                            RhovasAst.Expression.Lambda(listOf("first", "second", "third"), block()),
                        )),
                    ),
                    Arguments.of("Trailing Comma", "function |parameter,| {}",
                        RhovasAst.Expression.Function(null, "function", listOf(
                            RhovasAst.Expression.Lambda(listOf("parameter"), block()),
                        )),
                    ),
                    Arguments.of("Argument & Parameter", "function(argument) |parameter| {}",
                        RhovasAst.Expression.Function(null, "function", listOf(
                            expression("argument"),
                            RhovasAst.Expression.Lambda(listOf("parameter"), block()),
                        )),
                    ),
                    Arguments.of("Method", "receiver.method {}",
                        RhovasAst.Expression.Function(expression("receiver"), "method", listOf(
                            RhovasAst.Expression.Lambda(listOf(), RhovasAst.Statement.Block(listOf())),
                        )),
                    ),
                    Arguments.of("Missing Comma", "function |first second| {} ", null),
                    Arguments.of("Missing Closing Pipe", "function |first, second {} ", null),
                    Arguments.of("Non-Block Statement", "function body;", null),
                )
            }

        }

        @Nested
        inner class MacroTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testMacro(name: String, input: String, expected: RhovasAst.Expression.Macro?) {
                test("expression", input, expected)
            }

            fun testMacro(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Zero Arguments", "#macro()",
                        RhovasAst.Expression.Macro("macro", listOf())
                    ),
                    Arguments.of("Single Argument", "#macro(argument)",
                        RhovasAst.Expression.Macro("macro", listOf(
                            expression("argument"),
                        )),
                    ),
                    Arguments.of("Multiple Arguments", "#macro(first, second, third)",
                        RhovasAst.Expression.Macro("macro", listOf(
                            expression("first"),
                            expression("second"),
                            expression("third"),
                        )),
                    ),
                    Arguments.of("Trailing Comma", "#macro(argument,)",
                        RhovasAst.Expression.Macro("macro", listOf(
                            expression("argument"),
                        )),
                    ),
                    Arguments.of("Missing Comma", "#macro(first second)", null),
                    Arguments.of("Missing Closing Parenthesis", "#macro(argument", null),
                )
            }

        }

    }

    @Nested
    inner class InteractionTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testInteraction(name: String, rule: String, input: String, expected: RhovasAst) {
            test(rule, input, expected)
        }

        fun testInteraction(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Keyword Label Atom", "statement", "return :atom;",
                    RhovasAst.Statement.Return(RhovasAst.Expression.Literal(RhovasAst.Atom("atom"))),
                ),
                //expression
                Arguments.of("Lambda Zero Parameters", "expression", "function || {} ",
                    RhovasAst.Expression.Binary("||",
                        expression("function"),
                        RhovasAst.Expression.Literal(mapOf<String, RhovasAst.Expression>())
                    ),
                ),
            )
        }

    }

    private fun block(): RhovasAst.Statement.Block {
        return RhovasAst.Statement.Block(listOf())
    }

    private fun statement(name: String): RhovasAst.Statement {
        return RhovasAst.Statement.Expression(expression(name))
    }

    private fun expression(name: String): RhovasAst.Expression {
        return RhovasAst.Expression.Access(null, name)
    }

    private fun test(rule: String, input: String, expected: RhovasAst?) {
        if (expected != null) {
            Assertions.assertEquals(expected, RhovasParser(input).parse(rule))
        } else {
            Assertions.assertThrows(ParseException::class.java) { RhovasParser(input).parse(rule) }
        }
    }

}
