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
                    Arguments.of("Empty", "{}", RhovasAst.Statement.Block(listOf())),
                    Arguments.of("Single", "{ statement; }", RhovasAst.Statement.Block(listOf(
                        RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "statement")),
                    ))),
                    Arguments.of("Multiple", "{ first; second; third; }", RhovasAst.Statement.Block(listOf(
                        RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "first")),
                        RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "second")),
                        RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "third")),
                    ))),
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
                val receiver = RhovasAst.Expression.Access(null, "object")
                return Stream.of(
                    Arguments.of("Function", "function();", RhovasAst.Statement.Expression(
                        RhovasAst.Expression.Function(null, "function", listOf()),
                    )),
                    Arguments.of("Method", "object.method();", RhovasAst.Statement.Expression(
                        RhovasAst.Expression.Function(receiver, "method", listOf()),
                    )),
                    Arguments.of("Macro", "#macro();", RhovasAst.Statement.Expression(
                        RhovasAst.Expression.Macro("macro", listOf()),
                    )),
                    Arguments.of("Other", "variable;", RhovasAst.Statement.Expression(
                        RhovasAst.Expression.Access(null, "variable"),
                    )),
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
                        RhovasAst.Statement.Declaration(true, "name",
                            RhovasAst.Expression.Access(null, "value"),
                        ),
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
                    Arguments.of("Variable", "variable = value;", RhovasAst.Statement.Assignment(
                        RhovasAst.Expression.Access(null, "variable"),
                        RhovasAst.Expression.Access(null, "value"),
                    )),
                    Arguments.of("Field", "object.field = value;", RhovasAst.Statement.Assignment(
                        RhovasAst.Expression.Access(RhovasAst.Expression.Access(null, "object"), "field"),
                        RhovasAst.Expression.Access(null, "value"),
                    )),
                    Arguments.of("Index", "object[] = value;", RhovasAst.Statement.Assignment(
                        RhovasAst.Expression.Index(RhovasAst.Expression.Access(null, "object"), listOf()),
                        RhovasAst.Expression.Access(null, "value"),
                    )),
                    Arguments.of("Other", "function() = value;", RhovasAst.Statement.Assignment(
                        RhovasAst.Expression.Function(null, "function", listOf()),
                        RhovasAst.Expression.Access(null, "value"),
                    )),
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
                    Arguments.of("Then Empty", "if (condition) {}", RhovasAst.Statement.If(
                        RhovasAst.Expression.Access(null, "condition"),
                        RhovasAst.Statement.Block(listOf()),
                        null,
                    )),
                    Arguments.of("Then Single", "if (condition) { statement; }", RhovasAst.Statement.If(
                        RhovasAst.Expression.Access(null, "condition"),
                        RhovasAst.Statement.Block(listOf(
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "statement")),
                        )),
                        null,
                    )),
                    Arguments.of("Then Multiple", "if (condition) { first; second; third; }", RhovasAst.Statement.If(
                        RhovasAst.Expression.Access(null, "condition"),
                        RhovasAst.Statement.Block(listOf(
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "first")),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "second")),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "third")),
                        )),
                        null,
                    )),
                    Arguments.of("Else Empty", "if (condition) {} else {}", RhovasAst.Statement.If(
                        RhovasAst.Expression.Access(null, "condition"),
                        RhovasAst.Statement.Block(listOf()),
                        RhovasAst.Statement.Block(listOf()),
                    )),
                    Arguments.of("Else Single", "if (condition) {} else { statement; }", RhovasAst.Statement.If(
                        RhovasAst.Expression.Access(null, "condition"),
                        RhovasAst.Statement.Block(listOf()),
                        RhovasAst.Statement.Block(listOf(
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "statement")),
                        )),
                    )),
                    Arguments.of("Else Multiple", "if (condition) {} else { first; second; third; }", RhovasAst.Statement.If(
                        RhovasAst.Expression.Access(null, "condition"),
                        RhovasAst.Statement.Block(listOf()),
                        RhovasAst.Statement.Block(listOf(
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "first")),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "second")),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "third")),
                        )),
                    )),
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
                    Arguments.of("Empty", "match {}", RhovasAst.Statement.Match(null, listOf(), null)),
                    Arguments.of("Single", "match { condition: statement; }", RhovasAst.Statement.Match(
                        null,
                        listOf(Pair(
                            RhovasAst.Expression.Access(null, "condition"),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "statement")),
                        )),
                        null
                    )),
                    Arguments.of("Multiple ", "match { c1: s1; c2: s2; c3: s3; }", RhovasAst.Statement.Match(
                        null,
                        listOf(Pair(
                            RhovasAst.Expression.Access(null, "c1"),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "s1")),
                        ), Pair(
                            RhovasAst.Expression.Access(null, "c2"),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "s2")),
                        ), Pair(
                            RhovasAst.Expression.Access(null, "c3"),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "s3")),
                        )),
                        null
                    )),
                    Arguments.of("Argument", "match (argument) {}", RhovasAst.Statement.Match(
                        RhovasAst.Expression.Access(null, "argument"),
                        listOf(),
                        null,
                    )),
                    Arguments.of("Else", "match { else: statement; }", RhovasAst.Statement.Match(
                        null,
                        listOf(),
                        Pair(null, RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "statement")))
                    )),
                    Arguments.of("Else Condition", "match { else condition: statement; }", RhovasAst.Statement.Match(
                        null,
                        listOf(),
                        Pair(
                            RhovasAst.Expression.Access(null, "condition"),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "statement")),
                        )
                    )),
                    Arguments.of("Else With Cases", "match { c1: s1; c2: s2; else: s3; }", RhovasAst.Statement.Match(
                        null,
                        listOf(Pair(
                            RhovasAst.Expression.Access(null, "c1"),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "s1")),
                        ), Pair(
                            RhovasAst.Expression.Access(null, "c2"),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "s2")),
                        )),
                        Pair(null, RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "s3")))
                    )),
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
                    Arguments.of("Empty", "for (val name in iterable) {}", RhovasAst.Statement.For(
                        "name",
                        RhovasAst.Expression.Access(null, "iterable"),
                        RhovasAst.Statement.Block(listOf()),
                    )),
                    Arguments.of("Single", "for (val name in iterable) { statement; }", RhovasAst.Statement.For(
                        "name",
                        RhovasAst.Expression.Access(null, "iterable"),
                        RhovasAst.Statement.Block(listOf(
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "statement")),
                        )),
                    )),
                    Arguments.of("Multiple", "for (val name in iterable) { first; second; third; }", RhovasAst.Statement.For(
                        "name",
                        RhovasAst.Expression.Access(null, "iterable"),
                        RhovasAst.Statement.Block(listOf(
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "first")),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "second")),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "third")),
                        )),
                    )),
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
                    Arguments.of("Empty", "while (condition) {}", RhovasAst.Statement.While(
                        RhovasAst.Expression.Access(null, "condition"),
                        RhovasAst.Statement.Block(listOf()),
                    )),
                    Arguments.of("Single", "while (condition) { statement; }", RhovasAst.Statement.While(
                        RhovasAst.Expression.Access(null, "condition"),
                        RhovasAst.Statement.Block(listOf(
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "statement")),
                        )),
                    )),
                    Arguments.of("Multiple", "while (condition) { first; second; third; }", RhovasAst.Statement.While(
                        RhovasAst.Expression.Access(null, "condition"),
                        RhovasAst.Statement.Block(listOf(
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "first")),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "second")),
                            RhovasAst.Statement.Expression(RhovasAst.Expression.Access(null, "third")),
                        )),
                    )),
                    Arguments.of("Missing Opening Parenthesis", "while condition) {}", null),
                    Arguments.of("Missing Condition", "while () {}", null),
                    Arguments.of("Missing Closing Parenthesis", "while (condition {}", null),
                    Arguments.of("Missing Statement", "while (condition)", null),
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
                        RhovasAst.Expression.Access(null, "element"),
                    )),
                    Arguments.of("Multiple", "[first, second, third]", listOf(
                        RhovasAst.Expression.Access(null, "first"),
                        RhovasAst.Expression.Access(null, "second"),
                        RhovasAst.Expression.Access(null, "third"),
                    )),
                    Arguments.of("Trailing Comma", "[first, second,]", listOf(
                        RhovasAst.Expression.Access(null, "first"),
                        RhovasAst.Expression.Access(null, "second"),
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
                        "key" to RhovasAst.Expression.Access(null, "value"),
                    )),
                    Arguments.of("Multiple", "{k1: v1, k2: v2, k3: v3}", mapOf(
                        "k1" to RhovasAst.Expression.Access(null, "v1"),
                        "k2" to RhovasAst.Expression.Access(null, "v2"),
                        "k3" to RhovasAst.Expression.Access(null, "v3"),
                    )),
                    Arguments.of("Trailing Comma", "{k1: v1, k2: v2,}", mapOf(
                        "k1" to RhovasAst.Expression.Access(null, "v1"),
                        "k2" to RhovasAst.Expression.Access(null, "v2"),
                    )),
                    Arguments.of("Key Only", "{key}", mapOf(
                        "key" to RhovasAst.Expression.Access(null, "key")
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
                        RhovasAst.Expression.Group(RhovasAst.Expression.Access(null, "expression"))
                    ),
                    Arguments.of("Nested", "(((expression)))",
                        RhovasAst.Expression.Group(
                            RhovasAst.Expression.Group(
                                RhovasAst.Expression.Group(
                                    RhovasAst.Expression.Access(null, "expression"),
                                ),
                            ),
                        ),
                    ),
                    Arguments.of("Binary", "(first + second)",
                        RhovasAst.Expression.Group(
                            RhovasAst.Expression.Binary("+",
                                RhovasAst.Expression.Access(null, "first"),
                                RhovasAst.Expression.Access(null, "second"),
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
                        RhovasAst.Expression.Unary("-", RhovasAst.Expression.Access(null, "expression"))
                    ),
                    Arguments.of("Logical Negation", "!expression",
                        RhovasAst.Expression.Unary("!", RhovasAst.Expression.Access(null, "expression"))
                    ),
                    Arguments.of("Multiple", "-!expression",
                        RhovasAst.Expression.Unary("-",
                            RhovasAst.Expression.Unary("!", RhovasAst.Expression.Access(null, "expression"))
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
                            RhovasAst.Expression.Access(null, "left"),
                            RhovasAst.Expression.Access(null, "right"),
                        ),
                    ),
                    Arguments.of("Additive", "left + right",
                        RhovasAst.Expression.Binary("+",
                            RhovasAst.Expression.Access(null, "left"),
                            RhovasAst.Expression.Access(null, "right"),
                        ),
                    ),
                    Arguments.of("Comparison", "left < right",
                        RhovasAst.Expression.Binary("<",
                            RhovasAst.Expression.Access(null, "left"),
                            RhovasAst.Expression.Access(null, "right"),
                        ),
                    ),
                    Arguments.of("Equality", "left == right",
                        RhovasAst.Expression.Binary("==",
                            RhovasAst.Expression.Access(null, "left"),
                            RhovasAst.Expression.Access(null, "right"),
                        ),
                    ),
                    Arguments.of("Logical And", "left && right",
                        RhovasAst.Expression.Binary("&&",
                            RhovasAst.Expression.Access(null, "left"),
                            RhovasAst.Expression.Access(null, "right"),
                        ),
                    ),
                    Arguments.of("Logical Or", "left || right",
                        RhovasAst.Expression.Binary("||",
                            RhovasAst.Expression.Access(null, "left"),
                            RhovasAst.Expression.Access(null, "right"),
                        ),
                    ),
                    Arguments.of("Left Precedence", "first * second + third < fourth == fifth && sixth || seventh",
                        RhovasAst.Expression.Binary("||",
                            RhovasAst.Expression.Binary("&&",
                                RhovasAst.Expression.Binary("==",
                                    RhovasAst.Expression.Binary("<",
                                        RhovasAst.Expression.Binary("+",
                                            RhovasAst.Expression.Binary("*",
                                                RhovasAst.Expression.Access(null, "first"),
                                                RhovasAst.Expression.Access(null, "second"),
                                            ),
                                            RhovasAst.Expression.Access(null, "third"),
                                        ),
                                        RhovasAst.Expression.Access(null, "fourth"),
                                    ),
                                    RhovasAst.Expression.Access(null, "fifth"),
                                ),
                                RhovasAst.Expression.Access(null, "sixth"),
                            ),
                            RhovasAst.Expression.Access(null, "seventh"),
                        ),
                    ),
                    Arguments.of("Right Precedence", "first || second && third == fourth < fifth + sixth * seventh",
                        RhovasAst.Expression.Binary("||",
                            RhovasAst.Expression.Access(null, "first"),
                            RhovasAst.Expression.Binary("&&",
                                RhovasAst.Expression.Access(null, "second"),
                                RhovasAst.Expression.Binary("==",
                                    RhovasAst.Expression.Access(null, "third"),
                                    RhovasAst.Expression.Binary("<",
                                        RhovasAst.Expression.Access(null, "fourth"),
                                        RhovasAst.Expression.Binary("+",
                                            RhovasAst.Expression.Access(null, "fifth"),
                                            RhovasAst.Expression.Binary("*",
                                                RhovasAst.Expression.Access(null, "sixth"),
                                                RhovasAst.Expression.Access(null, "seventh"),
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
                                RhovasAst.Expression.Access(null, "first"),
                                RhovasAst.Expression.Access(null, "second"),
                            ),
                            RhovasAst.Expression.Access(null, "third"),
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
                    Arguments.of("Variable", "variable", RhovasAst.Expression.Access(null, "variable")),
                    Arguments.of("Underscore", "_", RhovasAst.Expression.Access(null, "_")),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testField(name: String, input: String, expected: RhovasAst.Expression.Access?) {
                test("expression", input, expected)
            }

            fun testField(): Stream<Arguments> {
                val receiver = RhovasAst.Expression.Access(null, "object")
                return Stream.of(
                    Arguments.of("Field", "object.field",
                        RhovasAst.Expression.Access(receiver,"field"),
                    ),
                    Arguments.of("Multiple Fields", "object.first.second.third",
                        RhovasAst.Expression.Access(
                            RhovasAst.Expression.Access(
                                RhovasAst.Expression.Access(receiver, "first"),
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
                val receiver = RhovasAst.Expression.Access(null, "object")
                return Stream.of(
                    Arguments.of("Zero Arguments", "object[]",
                        RhovasAst.Expression.Index(receiver, listOf()),
                    ),
                    Arguments.of("Single Argument", "object[argument]",
                        RhovasAst.Expression.Index(receiver, listOf(
                            RhovasAst.Expression.Access(null, "argument"),
                        )),
                    ),
                    Arguments.of("Multiple Arguments", "object[first, second, third]",
                        RhovasAst.Expression.Index(receiver, listOf(
                            RhovasAst.Expression.Access(null, "first"),
                            RhovasAst.Expression.Access(null, "second"),
                            RhovasAst.Expression.Access(null, "third"),
                        )),
                    ),
                    Arguments.of("Multiple Indexes", "object[first][second][third]",
                        RhovasAst.Expression.Index(
                            RhovasAst.Expression.Index(
                                RhovasAst.Expression.Index(receiver, listOf(
                                    RhovasAst.Expression.Access(null, "first"),
                                )),
                                listOf(RhovasAst.Expression.Access(null, "second")),
                            ),
                            listOf(RhovasAst.Expression.Access(null, "third")),
                        ),
                    ),
                    Arguments.of("Trailing Comma", "object[argument,]",
                        RhovasAst.Expression.Index(receiver, listOf(
                            RhovasAst.Expression.Access(null, "argument"),
                        )),
                    ),
                    Arguments.of("Missing Comma", "object[first second]", null),
                    Arguments.of("Missing Closing Bracket", "object[argument", null),
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
                            RhovasAst.Expression.Access(null, "argument"),
                        )),
                    ),
                    Arguments.of("Multiple Arguments", "function(first, second, third)",
                        RhovasAst.Expression.Function(null, "function", listOf(
                            RhovasAst.Expression.Access(null, "first"),
                            RhovasAst.Expression.Access(null, "second"),
                            RhovasAst.Expression.Access(null, "third"),
                        )),
                    ),
                    Arguments.of("Trailing Comma", "function(argument,)",
                        RhovasAst.Expression.Function(null, "function", listOf(
                            RhovasAst.Expression.Access(null, "argument"),
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
                val receiver = RhovasAst.Expression.Access(null, "object")
                return Stream.of(
                    Arguments.of("Zero Arguments", "object.method()",
                        RhovasAst.Expression.Function(receiver, "method", listOf())
                    ),
                    Arguments.of("Single Argument", "object.method(argument)",
                        RhovasAst.Expression.Function(receiver, "method", listOf(
                            RhovasAst.Expression.Access(null, "argument"),
                        )),
                    ),
                    Arguments.of("Multiple Arguments", "object.method(first, second, third)",
                        RhovasAst.Expression.Function(receiver, "method", listOf(
                            RhovasAst.Expression.Access(null, "first"),
                            RhovasAst.Expression.Access(null, "second"),
                            RhovasAst.Expression.Access(null, "third"),
                        )),
                    ),
                    Arguments.of("Trailing Comma", "object.method(argument,)",
                        RhovasAst.Expression.Function(receiver, "method", listOf(
                            RhovasAst.Expression.Access(null, "argument"),
                        )),
                    ),
                    Arguments.of("Multiple Methods", "object.first().second().third()",
                        RhovasAst.Expression.Function(
                            RhovasAst.Expression.Function(
                                RhovasAst.Expression.Function(receiver, "first", listOf()),
                                "second",
                                listOf(),
                            ),
                            "third",
                            listOf(),
                        ),
                    ),
                    Arguments.of("Missing Comma", "object.method(first second)", null),
                    Arguments.of("Missing Closing Parenthesis", "object.method(argument", null),
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
                            RhovasAst.Expression.Access(null, "argument"),
                        )),
                    ),
                    Arguments.of("Multiple Arguments", "#macro(first, second, third)",
                        RhovasAst.Expression.Macro("macro", listOf(
                            RhovasAst.Expression.Access(null, "first"),
                            RhovasAst.Expression.Access(null, "second"),
                            RhovasAst.Expression.Access(null, "third"),
                        )),
                    ),
                    Arguments.of("Trailing Comma", "#macro(argument,)",
                        RhovasAst.Expression.Macro("macro", listOf(
                            RhovasAst.Expression.Access(null, "argument"),
                        )),
                    ),
                    Arguments.of("Missing Comma", "#macro(first second)", null),
                    Arguments.of("Missing Closing Parenthesis", "#macro(argument", null),
                )
            }

        }

    }

    private fun test(rule: String, input: String, expected: RhovasAst?) {
        if (expected != null) {
            Assertions.assertEquals(expected, RhovasParser(input).parse(rule))
        } else {
            Assertions.assertThrows(ParseException::class.java) { RhovasParser(input).parse(rule) }
        }
    }

}
