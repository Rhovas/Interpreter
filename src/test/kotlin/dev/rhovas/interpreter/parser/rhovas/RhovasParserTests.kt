package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.dsl.DslAst
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
    inner class SourceTests {

        fun testSource(name: String, input: String, expected: RhovasAst.Source?) {
            test("source", input, expected)
        }

        fun testSource(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Empty", "",
                    RhovasAst.Source(listOf()),
                ),
                Arguments.of("Single", "statement;",
                    RhovasAst.Source(listOf(statement("statement"))),
                ),
                Arguments.of("Multiple", "first; second; third;",
                    RhovasAst.Source(listOf(
                        statement("first"),
                        statement("second"),
                        statement("third"),
                    )),
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
                            RhovasAst.Expression.Invoke.Function("function", listOf()),
                        ),
                    ),
                    Arguments.of("Method", "receiver.method();",
                        RhovasAst.Statement.Expression(
                            RhovasAst.Expression.Invoke.Method(expression("receiver"), false, false, "method", listOf()),
                        ),
                    ),
                    Arguments.of("Macro", "#macro();",
                        RhovasAst.Statement.Expression(
                            RhovasAst.Expression.Macro("macro", listOf()),
                        ),
                    ),
                    Arguments.of("Other", "variable;",
                        RhovasAst.Statement.Expression(
                            RhovasAst.Expression.Access.Variable("variable"),
                        ),
                    ),
                    Arguments.of("Missing Semicolon", "expression", null),
                )
            }

        }

        @Nested
        inner class FunctionTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testFunction(name: String, input: String, expected: RhovasAst.Statement.Function?) {
                test("statement", input, expected)
            }

            fun testFunction(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Func", "func name() { body; }",
                        RhovasAst.Statement.Function("name", listOf(), listOf(), null, listOf(), RhovasAst.Statement.Block(listOf(statement("body")))),
                    ),
                    Arguments.of("Empty Body", "func name() {}",
                        RhovasAst.Statement.Function("name", listOf(), listOf(), null, listOf(), block()),
                    ),
                    Arguments.of("Single Parameter", "func name(parameter) {}",
                        RhovasAst.Statement.Function("name", listOf(), listOf("parameter" to null), null, listOf(), block()),
                    ),
                    Arguments.of("Multiple Parameters", "func name(first, second, third) {}",
                        RhovasAst.Statement.Function("name", listOf(), listOf("first" to null, "second" to null, "third" to null), null, listOf(), block()),
                    ),
                    Arguments.of("Typed Parameter", "func name(parameter: Type) {}",
                        RhovasAst.Statement.Function("name", listOf(), listOf("parameter" to RhovasAst.Type("Type", null)), null, listOf(), block()),
                    ),
                    Arguments.of("Trailing Comma", "func name(parameter,) {}",
                        RhovasAst.Statement.Function("name", listOf(), listOf("parameter" to null), null, listOf(), block()),
                    ),
                    Arguments.of("Return Type", "func name(): Type {}",
                        RhovasAst.Statement.Function("name", listOf(), listOf(), RhovasAst.Type("Type", null), listOf(), block()),
                    ),
                    Arguments.of("Single Throws", "func name() throws Type {}",
                        RhovasAst.Statement.Function("name", listOf(), listOf(), null, listOf(RhovasAst.Type("Type", null)), block()),
                    ),
                    Arguments.of("Multiple Throws", "func name() throws First, Second, Third {}",
                        RhovasAst.Statement.Function("name", listOf(), listOf(), null, listOf(RhovasAst.Type("First", null), RhovasAst.Type("Second", null), RhovasAst.Type("Third", null)), block()),
                    ),
                    Arguments.of("Missing Name", "func () {}", null),
                    Arguments.of("Missing Parenthesis", "func name {}", null),
                    Arguments.of("Missing Comma", "func name(first second) {}", null),
                    Arguments.of("Missing Closing Parenthesis", "func name(argument {}", null),
                    Arguments.of("Missing Return Type", "func name(): {}", null),
                    Arguments.of("Missing Throws Type Single", "func name() throws {}", null),
                    Arguments.of("Missing Throws Type Multiple", "func name() throws First, {}", null),
                    Arguments.of("Missing Body", "func name()", null),
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
                        RhovasAst.Statement.Declaration(false, "name", null, null),
                    ),
                    Arguments.of("Var", "var name;",
                        RhovasAst.Statement.Declaration(true, "name", null, null),
                    ),
                    Arguments.of("Type", "val name: Type;",
                        RhovasAst.Statement.Declaration(false, "name", RhovasAst.Type("Type", null), null),
                    ),
                    Arguments.of("Value", "var name = value;",
                        RhovasAst.Statement.Declaration(true, "name", null, expression("value")),
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
                    Arguments.of("Property", "receiver.property = value;",
                        RhovasAst.Statement.Assignment(
                            RhovasAst.Expression.Access.Property(expression("receiver"), false, "property"),
                            expression("value"),
                        ),
                    ),
                    Arguments.of("Index", "receiver[] = value;",
                        RhovasAst.Statement.Assignment(
                            RhovasAst.Expression.Access.Index(expression("receiver"), listOf()),
                            expression("value"),
                        ),
                    ),
                    Arguments.of("Other", "function() = value;",
                        RhovasAst.Statement.Assignment(
                            RhovasAst.Expression.Invoke.Function("function", listOf()),
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
            fun testConditional(name: String, input: String, expected: RhovasAst.Statement.Match.Conditional?) {
                test("statement", input, expected)
            }

            fun testConditional(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "match {}",
                        RhovasAst.Statement.Match.Conditional(listOf(), null),
                    ),
                    Arguments.of("Single", "match { condition: statement; }",
                        RhovasAst.Statement.Match.Conditional(
                            listOf(Pair(expression("condition"), statement("statement"))),
                            null
                        ),
                    ),
                    Arguments.of("Multiple ", "match { c1: s1; c2: s2; c3: s3; }",
                        RhovasAst.Statement.Match.Conditional(
                            listOf(
                                Pair(expression("c1"), statement("s1")),
                                Pair(expression("c2"), statement("s2")),
                                Pair(expression("c3"), statement("s3"))
                            ),
                            null
                        ),
                    ),
                    Arguments.of("Else", "match { else: statement; }",
                        RhovasAst.Statement.Match.Conditional(
                            listOf(),
                            Pair(null, statement("statement"))
                        ),
                    ),
                    Arguments.of("Else Condition", "match { else condition: statement; }",
                        RhovasAst.Statement.Match.Conditional(
                            listOf(),
                            Pair(expression("condition"), statement("statement"))
                        ),
                    ),
                    Arguments.of("Else With Cases", "match { c1: s1; c2: s2; else: s3; }",
                        RhovasAst.Statement.Match.Conditional(
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

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testStructural(name: String, input: String, expected: RhovasAst.Statement.Match.Structural?) {
                test("statement", input, expected)
            }

            fun testStructural(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "match (argument) {}",
                        RhovasAst.Statement.Match.Structural(expression("argument"), listOf(), null),
                    ),
                    Arguments.of("Single", "match (argument) { pattern: statement; }",
                        RhovasAst.Statement.Match.Structural(
                            expression("argument"),
                            listOf(Pair(RhovasAst.Pattern.Variable("pattern"), statement("statement"))),
                            null
                        ),
                    ),
                    Arguments.of("Multiple ", "match (argument) { p1: s1; p2: s2; p3: s3; }",
                        RhovasAst.Statement.Match.Structural(
                            expression("argument"),
                            listOf(
                                Pair(RhovasAst.Pattern.Variable("p1"), statement("s1")),
                                Pair(RhovasAst.Pattern.Variable("p2"), statement("s2")),
                                Pair(RhovasAst.Pattern.Variable("p3"), statement("s3"))
                            ),
                            null
                        ),
                    ),
                    Arguments.of("Else", "match (argument) { else: statement; }",
                        RhovasAst.Statement.Match.Structural(
                            expression("argument"),
                            listOf(),
                            Pair(null, statement("statement"))
                        ),
                    ),
                    Arguments.of("Else Condition", "match (argument) { else pattern: statement; }",
                        RhovasAst.Statement.Match.Structural(
                            expression("argument"),
                            listOf(),
                            Pair(RhovasAst.Pattern.Variable("pattern"), statement("statement"))
                        ),
                    ),
                    Arguments.of("Else With Cases", "match (argument) { p1: s1; p2: s2; else: s3; }",
                        RhovasAst.Statement.Match.Structural(
                            expression("argument"),
                            listOf(
                                Pair(RhovasAst.Pattern.Variable("p1"), statement("s1")),
                                Pair(RhovasAst.Pattern.Variable("p2"), statement("s2")),
                            ),
                            Pair(null, statement("s3"))
                        ),
                    ),
                    Arguments.of("Else Inner", "match (argument) { p1: s2; else: s2; p3: s3; }", null),
                    Arguments.of("Else Multiple", "match (argument) { else: s1; else: s2; }", null),
                    Arguments.of("Missing Closing Parenthesis", "match (argument {}", null),
                    Arguments.of("Missing Opening Brace", "match (argument) }", null),
                    Arguments.of("Missing Pattern", "match (argument) { : statement; }", null), //parses : statement as an Atom
                    Arguments.of("Missing Colon", "match (argument) { pattern statement; }", null),
                    Arguments.of("Missing Statement", "match (argument) { pattern }", null),
                    Arguments.of("Missing Else Colon", "match (argument) { else statement; }", null), //parses statement as the pattern
                    Arguments.of("Missing Else Statement", "match (argument) { else: }", null),
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
                    Arguments.of("Catch", "try {} catch (val name: Type) body;",
                        RhovasAst.Statement.Try(
                            block(),
                            listOf(RhovasAst.Statement.Try.Catch("name", RhovasAst.Type("Type", null), statement("body"))),
                            null,
                        ),
                    ),
                    Arguments.of("Empty Catch", "try {} catch (val name: Type) {}",
                        RhovasAst.Statement.Try(
                            block(),
                            listOf(RhovasAst.Statement.Try.Catch("name", RhovasAst.Type("Type", null), block())),
                            null,
                        ),
                    ),
                    Arguments.of("Multiple Catch", """
                        try {}
                        catch (val first: First) {}
                        catch (val second: Second) {}
                        catch (val third: Third) {}
                        """,
                        RhovasAst.Statement.Try(
                            block(),
                            listOf(
                                RhovasAst.Statement.Try.Catch("first", RhovasAst.Type("First", null), block()),
                                RhovasAst.Statement.Try.Catch("second", RhovasAst.Type("Second", null), block()),
                                RhovasAst.Statement.Try.Catch("third", RhovasAst.Type("Third", null), block()),
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
                    Arguments.of("Both Catch & Finally", "try {} catch (val name: Type) {} finally {}",
                        RhovasAst.Statement.Try(
                            block(),
                            listOf(RhovasAst.Statement.Try.Catch("name", RhovasAst.Type("Type", null), block())),
                            block(),
                        ),
                    ),
                    Arguments.of("Missing Try Statement", "try", null),
                    Arguments.of("Missing Catch Opening Parenthesis", "try {} catch val name: Type) {}", null),
                    Arguments.of("Missing Catch Val", "try {} catch (name: Type) {}", null),
                    Arguments.of("Missing Catch Name", "try {} catch (val) {}", null),
                    Arguments.of("Missing Catch Type", "try {} catch (val name) {}", null),
                    Arguments.of("Missing Catch Closing Parenthesis", "try {} catch (val name: Type {}", null),
                    Arguments.of("Missing Catch Statement", "try {} catch (val name: Type)", null),
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
                test("expression", input, RhovasAst.Expression.Literal.Scalar(expected))
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
                    Arguments.of("Atom", ":atom", RhovasAst.Atom("atom")),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testString(name: String, input: String, expected: RhovasAst.Expression.Literal.String?) {
                test("expression", input, expected)
            }

            fun testString(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "\"\"", RhovasAst.Expression.Literal.String(listOf(""), listOf())),
                    Arguments.of("String", "\"string\"", RhovasAst.Expression.Literal.String(listOf("string"), listOf())),
                    Arguments.of("Interpolation", "\"start\${argument}end\"", RhovasAst.Expression.Literal.String(
                        listOf("start", "end"),
                        listOf(RhovasAst.Expression.Access.Variable("argument")),
                    )),
                    Arguments.of("Interpolation Multiple", "\"start\${first}middle\${second}end\"", RhovasAst.Expression.Literal.String(
                        listOf("start", "middle", "end"),
                        listOf(RhovasAst.Expression.Access.Variable("first"), RhovasAst.Expression.Access.Variable("second")),
                    )),
                    Arguments.of("Interpolation Only", "\"\${argument}\"", RhovasAst.Expression.Literal.String(
                        listOf("", ""),
                        listOf(RhovasAst.Expression.Access.Variable("argument")),
                    )),
                    Arguments.of("Interpolation Only Multiple", "\"\${first}\${second}\"", RhovasAst.Expression.Literal.String(
                        listOf("", "", ""),
                        listOf(RhovasAst.Expression.Access.Variable("first"), RhovasAst.Expression.Access.Variable("second")),
                    )),
                    Arguments.of("Unterminated", "\"unterminated", null),
                    Arguments.of("Unterminated Newline", "\"unterminated\n", null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testList(name: String, input: String, expected: List<RhovasAst.Expression>?) {
                test("expression", input, expected?.let { RhovasAst.Expression.Literal.List(it) })
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
                test("expression", input, expected?.let { RhovasAst.Expression.Literal.Object(it) })
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

            @Nested
            inner class VariableTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testVariable(name: String, input: String, expected: RhovasAst.Expression.Access?) {
                    test("expression", input, expected)
                }

                fun testVariable(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Variable", "variable",
                            RhovasAst.Expression.Access.Variable("variable")
                        ),
                        Arguments.of("Underscore", "_",
                            RhovasAst.Expression.Access.Variable("_")
                        ),
                    )
                }

            }

            @Nested
            inner class PropertyTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testProperty(name: String, input: String, expected: RhovasAst.Expression.Access?) {
                    test("expression", input, expected)
                }

                fun testProperty(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Property", "receiver.property",
                            RhovasAst.Expression.Access.Property(expression("receiver"),false, "property"),
                        ),
                        Arguments.of("Multiple Properties", "receiver.first.second.third",
                            RhovasAst.Expression.Access.Property(
                                RhovasAst.Expression.Access.Property(
                                    RhovasAst.Expression.Access.Property(
                                        expression("receiver"), false, "first"
                                    ), false, "second",
                                ), false, "third",
                            ),
                        ),
                        Arguments.of("Nullable", "receiver?.property",
                            RhovasAst.Expression.Access.Property(expression("receiver"), true, "property"),
                        ),
                        Arguments.of("Coalesce", "receiver..property", null),
                        Arguments.of("Missing Identifier", "receiver.", null),
                    )
                }

            }

        }

        @Nested
        inner class IndexTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testIndex(name: String, input: String, expected: RhovasAst.Expression.Access.Index?) {
                test("expression", input, expected)
            }

            fun testIndex(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Zero Arguments", "receiver[]",
                        RhovasAst.Expression.Access.Index(expression("receiver"), listOf()),
                    ),
                    Arguments.of("Single Argument", "receiver[argument]",
                        RhovasAst.Expression.Access.Index(expression("receiver"), listOf(
                            expression("argument"),
                        )),
                    ),
                    Arguments.of("Multiple Arguments", "receiver[first, second, third]",
                        RhovasAst.Expression.Access.Index(expression("receiver"), listOf(
                            expression("first"),
                            expression("second"),
                            expression("third"),
                        )),
                    ),
                    Arguments.of("Multiple Indexes", "receiver[first][second][third]",
                        RhovasAst.Expression.Access.Index(
                            RhovasAst.Expression.Access.Index(
                                RhovasAst.Expression.Access.Index(expression("receiver"), listOf(
                                    expression("first"),
                                )),
                                listOf(expression("second")),
                            ),
                            listOf(expression("third")),
                        ),
                    ),
                    Arguments.of("Trailing Comma", "receiver[argument,]",
                        RhovasAst.Expression.Access.Index(expression("receiver"), listOf(
                            expression("argument"),
                        )),
                    ),
                    Arguments.of("Missing Comma", "receiver[first second]", null),
                    Arguments.of("Missing Closing Bracket", "receiver[argument", null),
                )
            }

        }

        @Nested
        inner class InvokeTests {

            @Nested
            inner class FunctionTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testFunction(name: String, input: String, expected: RhovasAst.Expression.Invoke.Function?) {
                    test("expression", input, expected)
                }

                fun testFunction(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Zero Arguments", "function()",
                            RhovasAst.Expression.Invoke.Function("function", listOf())
                        ),
                        Arguments.of("Single Argument", "function(argument)",
                            RhovasAst.Expression.Invoke.Function("function", listOf(
                                expression("argument"),
                            )),
                        ),
                        Arguments.of("Multiple Arguments", "function(first, second, third)",
                            RhovasAst.Expression.Invoke.Function("function", listOf(
                                expression("first"),
                                expression("second"),
                                expression("third"),
                            )),
                        ),
                        Arguments.of("Trailing Comma", "function(argument,)",
                            RhovasAst.Expression.Invoke.Function("function", listOf(
                                expression("argument"),
                            )),
                        ),
                        Arguments.of("Missing Comma", "function(first second)", null),
                        Arguments.of("Missing Closing Parenthesis", "function(argument", null),
                    )
                }

            }

            @Nested
            inner class MethodTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testMethod(name: String, input: String, expected: RhovasAst.Expression.Invoke.Method?) {
                    test("expression", input, expected)
                }

                fun testMethod(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Zero Arguments", "receiver.method()",
                            RhovasAst.Expression.Invoke.Method(expression("receiver"), false, false, "method", listOf())
                        ),
                        Arguments.of("Single Argument", "receiver.method(argument)",
                            RhovasAst.Expression.Invoke.Method(expression("receiver"), false, false, "method", listOf(
                                expression("argument"),
                            )),
                        ),
                        Arguments.of("Multiple Arguments", "receiver.method(first, second, third)",
                            RhovasAst.Expression.Invoke.Method(expression("receiver"), false, false, "method", listOf(
                                expression("first"),
                                expression("second"),
                                expression("third"),
                            )),
                        ),
                        Arguments.of("Trailing Comma", "receiver.method(argument,)",
                            RhovasAst.Expression.Invoke.Method(expression("receiver"), false, false, "method", listOf(
                                expression("argument"),
                            )),
                        ),
                        Arguments.of("Multiple Methods", "receiver.first().second().third()",
                            RhovasAst.Expression.Invoke.Method(
                                RhovasAst.Expression.Invoke.Method(
                                    RhovasAst.Expression.Invoke.Method(
                                        expression("receiver"), false, false, "first", listOf()
                                    ), false, false, "second", listOf(),
                                ), false, false, "third", listOf(),
                            ),
                        ),
                        Arguments.of("Coalesce", "receiver?.method()",
                            RhovasAst.Expression.Invoke.Method(expression("receiver"), true, false, "method", listOf()),
                        ),
                        Arguments.of("Cascade", "receiver..method()",
                            RhovasAst.Expression.Invoke.Method(expression("receiver"), false, true, "method", listOf()),
                        ),
                        Arguments.of("Coalesce & Cascade", "receiver?..method()",
                            RhovasAst.Expression.Invoke.Method(expression("receiver"), true, true, "method", listOf()),
                        ),
                        Arguments.of("Missing Comma", "receiver.method(first second)", null),
                        Arguments.of("Missing Closing Parenthesis", "receiver.method(argument", null),
                    )
                }

            }

            @Nested
            inner class PipelineTests {

                @ParameterizedTest(name = "{0}")
                @MethodSource
                fun testPipeline(name: String, input: String, expected: RhovasAst.Expression.Invoke.Pipeline?) {
                    test("expression", input, expected)
                }

                fun testPipeline(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Pipeline", "receiver.|function()",
                            RhovasAst.Expression.Invoke.Pipeline(expression("receiver"), false, false, null, "function", listOf()),
                        ),
                        Arguments.of("Qualifier", "receiver.|Qualifier.function()",
                            RhovasAst.Expression.Invoke.Pipeline(expression("receiver"), false, false, RhovasAst.Expression.Access.Variable("Qualifier"), "function", listOf()),
                        ),
                        Arguments.of("Coalesce", "receiver?.|function()",
                            RhovasAst.Expression.Invoke.Pipeline(expression("receiver"), true, false, null, "function", listOf()),
                        ),
                        Arguments.of("Cascade", "receiver..|function()",
                            RhovasAst.Expression.Invoke.Pipeline(expression("receiver"), false, true, null, "function", listOf()),
                        ),
                        Arguments.of("Coalesce & Cascade", "receiver?..|function()",
                            RhovasAst.Expression.Invoke.Pipeline(expression("receiver"), true, true, null, "function", listOf()),
                        ),
                        Arguments.of("Missing Comma", "receiver.|function(first second)", null),
                        Arguments.of("Missing Closing Parenthesis", "receiver.|function(argument", null),
                    )
                }

            }

        }

        @Nested
        inner class LambdaTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testLambda(name: String, input: String, expected: RhovasAst.Expression.Invoke?) {
                test("expression", input, expected)
            }

            fun testLambda(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Lambda", "function { body; }",
                        RhovasAst.Expression.Invoke.Function("function", listOf(
                            RhovasAst.Expression.Lambda(listOf(), RhovasAst.Statement.Block(listOf(statement("body")))),
                        )),
                    ),
                    Arguments.of("Empty Block", "function {}",
                        RhovasAst.Expression.Invoke.Function("function", listOf(
                            RhovasAst.Expression.Lambda(listOf(), block()),
                        )),
                    ),
                    Arguments.of("Argument", "function(argument) {}",
                        RhovasAst.Expression.Invoke.Function("function", listOf(
                            expression("argument"),
                            RhovasAst.Expression.Lambda(listOf(), block()),
                        )),
                    ),
                    Arguments.of("Single Parameter", "function |parameter| {}",
                        RhovasAst.Expression.Invoke.Function("function", listOf(
                            RhovasAst.Expression.Lambda(listOf("parameter" to null), block()),
                        )),
                    ),
                    Arguments.of("Multiple Parameters", "function |first, second, third| {}",
                        RhovasAst.Expression.Invoke.Function("function", listOf(
                            RhovasAst.Expression.Lambda(listOf("first" to null, "second" to null, "third" to null), block()),
                        )),
                    ),
                    Arguments.of("Typed Parameter", "function |parameter: Type| {}",
                        RhovasAst.Expression.Invoke.Function("function", listOf(
                            RhovasAst.Expression.Lambda(listOf("parameter" to RhovasAst.Type("Type", null)), block()),
                        )),
                    ),
                    Arguments.of("Trailing Comma", "function |parameter,| {}",
                        RhovasAst.Expression.Invoke.Function("function", listOf(
                            RhovasAst.Expression.Lambda(listOf("parameter" to null), block()),
                        )),
                    ),
                    Arguments.of("Argument & Parameter", "function(argument) |parameter| {}",
                        RhovasAst.Expression.Invoke.Function("function", listOf(
                            expression("argument"),
                            RhovasAst.Expression.Lambda(listOf("parameter" to null), block()),
                        )),
                    ),
                    Arguments.of("Method", "receiver.method {}",
                        RhovasAst.Expression.Invoke.Method(expression("receiver"), false, false, "method", listOf(
                            RhovasAst.Expression.Lambda(listOf(), RhovasAst.Statement.Block(listOf())),
                        )),
                    ),
                    Arguments.of("Pipeline", "receiver.|function {}",
                        RhovasAst.Expression.Invoke.Pipeline(expression("receiver"), false, false, null, "function", listOf(
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

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testDsl(name: String, input: String, expected: RhovasAst.Expression.Macro?) {
                test("expression", input, expected)
            }

            fun testDsl(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Inline", "#macro { source }",
                        RhovasAst.Expression.Macro("macro", listOf(
                            RhovasAst.Expression.Dsl("macro", DslAst.Source(listOf(" source "), listOf())),
                        )),
                    ),
                    Arguments.of("Multiline", "#macro {\n    source\n}",
                        RhovasAst.Expression.Macro("macro", listOf(
                            RhovasAst.Expression.Dsl("macro", DslAst.Source(listOf("source"), listOf())),
                        )),
                    ),
                    Arguments.of("Argument", "#macro(argument) { source }",
                        RhovasAst.Expression.Macro("macro", listOf(
                            expression("argument"),
                            RhovasAst.Expression.Dsl("macro", DslAst.Source(listOf(" source "), listOf())),
                        )),
                    ),
                    Arguments.of("Missing Closing Brace", "#macro { source", null),
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
            fun testVariable(name: String, input: String, expected: RhovasAst.Pattern.Variable?) {
                test("pattern", input, expected)
            }

            fun testVariable(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Variable", "variable", RhovasAst.Pattern.Variable("variable")),
                    Arguments.of("Underscore", "_", RhovasAst.Pattern.Variable("_")),
                )
            }

        }

        @Nested
        inner class ValueTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testValue(name: String, input: String, expected: RhovasAst.Pattern.Value?) {
                test("pattern", input, expected)
            }

            fun testValue(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Null", "null", RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(null))),
                    Arguments.of("Boolean True", "true", RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(true))),
                    Arguments.of("Boolean False", "false", RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(false))),
                    Arguments.of("Integer", "0", RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(BigInteger("0")))),
                    Arguments.of("Decimal", "0.0", RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(BigDecimal("0.0")))),
                    Arguments.of("String", "\"string\"", RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.String(listOf("string"), listOf()))),
                    Arguments.of("Atom", ":atom", RhovasAst.Pattern.Value(RhovasAst.Expression.Literal.Scalar(RhovasAst.Atom("atom")))),
                    Arguments.of("Interpolation", "\${value}", RhovasAst.Pattern.Value(expression("value"))),
                    Arguments.of("Missing Opening Brace", "\$value}", null),
                    Arguments.of("Missing Closing Brace", "\${value", null),
                )
            }

        }

        @Nested
        inner class PredicateTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testPredicate(name: String, input: String, expected: RhovasAst.Pattern.Predicate?) {
                test("pattern", input, expected)
            }

            fun testPredicate(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Variable", "pattern \${predicate}",
                        RhovasAst.Pattern.Predicate(
                            RhovasAst.Pattern.Variable("pattern"),
                            expression("predicate"),
                        ),
                    ),
                    Arguments.of("OrderedDestructure", "[ordered] \${predicate}",
                        RhovasAst.Pattern.Predicate(
                            RhovasAst.Pattern.OrderedDestructure(listOf(RhovasAst.Pattern.Variable("ordered"))),
                            expression("predicate"),
                        ),
                    ),
                    Arguments.of("VarargDestructure", "pattern* \${predicate}",
                        RhovasAst.Pattern.Predicate(
                            RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("pattern"), "*"),
                            expression("predicate")
                        ),
                    ),
                    Arguments.of("Missing Opening Brace", "pattern \$predicate}", null),
                    Arguments.of("Missing Closing Brace", "pattern \${predicate", null),
                )
            }

        }

        @Nested
        inner class OrderedDestructureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testOrderedDestructure(name: String, input: String, expected: RhovasAst.Pattern.OrderedDestructure?) {
                test("pattern", input, expected)
            }

            fun testOrderedDestructure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "[]",
                        RhovasAst.Pattern.OrderedDestructure(listOf()),
                    ),
                    Arguments.of("Single", "[pattern]",
                        RhovasAst.Pattern.OrderedDestructure(listOf(
                            RhovasAst.Pattern.Variable("pattern"),
                        )),
                    ),
                    Arguments.of("Multiple", "[first, second, third]",
                        RhovasAst.Pattern.OrderedDestructure(listOf(
                            RhovasAst.Pattern.Variable("first"),
                            RhovasAst.Pattern.Variable("second"),
                            RhovasAst.Pattern.Variable("third"),
                        )),
                    ),
                    Arguments.of("Varargs", "[first, rest*]",
                        RhovasAst.Pattern.OrderedDestructure(listOf(
                            RhovasAst.Pattern.Variable("first"),
                            RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("rest"), "*"),
                        )),
                    ),
                    Arguments.of("Missing Comma", "[first second]", null),
                    Arguments.of("Missing Closing Bracket", "[pattern", null),
                )
            }

        }

        @Nested
        inner class NamedDestructureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testNamedDestructure(name: String, input: String, expected: RhovasAst.Pattern.NamedDestructure?) {
                test("pattern", input, expected)
            }

            fun testNamedDestructure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", "{}",
                        RhovasAst.Pattern.NamedDestructure(listOf()),
                    ),
                    Arguments.of("Single", "{key: pattern}",
                        RhovasAst.Pattern.NamedDestructure(listOf(
                            Pair("key", RhovasAst.Pattern.Variable("pattern")),
                        )),
                    ),
                    Arguments.of("Multiple", "{k1: p1, k2: p2, k3: p3}",
                        RhovasAst.Pattern.NamedDestructure(listOf(
                            Pair("k1", RhovasAst.Pattern.Variable("p1")),
                            Pair("k2", RhovasAst.Pattern.Variable("p2")),
                            Pair("k3", RhovasAst.Pattern.Variable("p3")),
                        )),
                    ),
                    Arguments.of("Key Only", "{key}",
                        RhovasAst.Pattern.NamedDestructure(listOf(
                            Pair("key", null),
                        )),
                    ),
                    Arguments.of("Varargs", "{key: pattern, rest*}",
                        RhovasAst.Pattern.NamedDestructure(listOf(
                            Pair("key", RhovasAst.Pattern.Variable("pattern")),
                            Pair("", RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("rest"), "*")),
                        )),
                    ),
                    Arguments.of("Missing Colon", "{k1 p1}", null),
                    Arguments.of("Missing Comma", "{k1: p1 k2: p2}", null),
                    Arguments.of("Missing Closing Bracket", "{key: pattern", null),
                )
            }

        }

        @Nested
        inner class VarargDestructureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testVarargDestructure(name: String, input: String, expected: RhovasAst.Pattern.VarargDestructure?) {
                test("pattern", input, expected)
            }

            fun testVarargDestructure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Zero Or More", "pattern*",
                        RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("pattern"), "*"),
                    ),
                    Arguments.of("One Or More", "pattern+",
                        RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("pattern"), "+"),
                    ),
                    Arguments.of("Operator Only", "*",
                        RhovasAst.Pattern.VarargDestructure(null, "*"),
                    ),
                )
            }

        }

    }

    @Nested
    inner class TypeTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testType(name: String, input: String, expected: RhovasAst) {
            test("type", input, expected)
        }

        fun testType(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Type", "Type", RhovasAst.Type("Type", null)),
                Arguments.of("Empty Generics", "Type<>", RhovasAst.Type("Type", listOf())),
                Arguments.of("Single Generic", "Type<Generic>", RhovasAst.Type("Type", listOf(
                    RhovasAst.Type("Generic", null),
                ))),
                Arguments.of("Multiple Generics", "Type<First, Second, Third>", RhovasAst.Type("Type", listOf(
                    RhovasAst.Type("First", null),
                    RhovasAst.Type("Second", null),
                    RhovasAst.Type("Third", null),
                ))),
                Arguments.of("Trailing Comma", "Type<Generic,>", RhovasAst.Type("Type", listOf(
                    RhovasAst.Type("Generic", null),
                ))),
            )
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
                    RhovasAst.Statement.Return(RhovasAst.Expression.Literal.Scalar(RhovasAst.Atom("atom"))),
                ),
                //expression
                Arguments.of("Lambda Zero Parameters", "expression", "function || {} ",
                    RhovasAst.Expression.Binary("||",
                        expression("function"),
                        RhovasAst.Expression.Literal.Object(mapOf())
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
        return RhovasAst.Expression.Access.Variable(name)
    }

    private fun test(rule: String, input: String, expected: RhovasAst?) {
        val parser = RhovasParser(Input("Test", input))
        if (expected != null) {
            val ast = parser.parse(rule)
            Assertions.assertEquals(expected, ast)
            Assertions.assertTrue(ast.context.isNotEmpty() || input.isEmpty())
        } else {
            val exception = Assertions.assertThrows(ParseException::class.java) { parser.parse(rule) }
            Assertions.assertNotEquals("Broken lexer invariant.", exception.summary)
            Assertions.assertNotEquals("Broken parser invariant.", exception.summary)
        }
    }

}
