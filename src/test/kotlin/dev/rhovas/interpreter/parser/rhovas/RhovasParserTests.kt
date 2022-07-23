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

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testSource(name: String, input: String, expected: RhovasAst.Source?) {
            test("source", input, expected)
        }

        fun testSource(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Empty", """
                    
                """.trimIndent(), RhovasAst.Source(
                    listOf()
                )),
                Arguments.of("Single", """
                    statement;
                """, RhovasAst.Source(
                    listOf(stmt("statement")),
                )),
                Arguments.of("Multiple", """
                    first; second; third;
                """, RhovasAst.Source(
                    listOf(stmt("first"), stmt("second"), stmt("third")),
                )),
            )
        }

    }

    @Nested
    inner class ComponentTests {

        @Nested
        inner class StructTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testComponent(name: String, input: String, expected: RhovasAst.Component.Struct?) {
                test("component", input, expected)
            }

            fun testComponent(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        struct Name {}
                    """.trimIndent(), RhovasAst.Component.Struct("Name", listOf())),
                    Arguments.of("Variable", """
                        struct Name {
                            val name: Type;
                        }
                    """.trimIndent(), RhovasAst.Component.Struct("Name", listOf(
                        RhovasAst.Statement.Declaration(false, "name", type("Type"), null),
                    ))),
                    Arguments.of("Function", """
                        struct Name {
                            func name(): Type {}
                        }
                    """.trimIndent(), null),
                    Arguments.of("Anonymous", """
                        struct {}
                    """.trimIndent(), null),
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
            fun testBlock(name: String, input: String, expected: RhovasAst.Statement.Block?) {
                test("statement", input, expected)
            }

            fun testBlock(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        {}
                    """, RhovasAst.Statement.Block(
                        listOf()
                    )),
                    Arguments.of("Single", """
                        { statement; }
                    """.trimIndent(), RhovasAst.Statement.Block(
                        listOf(stmt("statement")),
                    )),
                    Arguments.of("Multiple", """
                        { first; second; third; }
                    """.trimIndent(), RhovasAst.Statement.Block(
                        listOf(stmt("first"), stmt("second"), stmt("third")),
                    )),
                    Arguments.of("Missing Closing Brace", """
                        { statement;
                    """, null),
                )
            }

        }

        @Nested
        inner class ComponentTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testComponent(name: String, input: String, expected: RhovasAst.Statement.Component?) {
                test("statement", input, expected)
            }

            fun testComponent(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Struct", """
                        struct Name {}
                    """.trimIndent(), RhovasAst.Statement.Component(
                        RhovasAst.Component.Struct("Name", listOf())
                    )),
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
                    Arguments.of("Function", """
                        function();
                    """.trimIndent(), RhovasAst.Statement.Expression(
                        RhovasAst.Expression.Invoke.Function("function", listOf()),
                    )),
                    Arguments.of("Method", """
                        receiver.method();
                    """.trimIndent(), RhovasAst.Statement.Expression(
                        RhovasAst.Expression.Invoke.Method(expr("receiver"), false, false, "method", listOf()),
                    )),
                    Arguments.of("Macro", """
                        #macro();
                    """.trimIndent(), RhovasAst.Statement.Expression(
                        RhovasAst.Expression.Macro("macro", listOf()),
                    )),
                    Arguments.of("Other", """
                        variable;
                    """.trimIndent(), RhovasAst.Statement.Expression(
                        RhovasAst.Expression.Access.Variable("variable"),
                    )),
                    Arguments.of("Missing Semicolon", """
                        expression
                    """, null),
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
                    Arguments.of("Function", """
                        func name() {}
                    """.trimIndent(), RhovasAst.Statement.Function(
                        "name", listOf(), listOf(), null, listOf(), block(),
                    )),
                    Arguments.of("Single Generic", """
                        func name<T>() {}
                    """.trimIndent(), RhovasAst.Statement.Function(
                        "name", listOf("T" to null), listOf(), null, listOf(), block(),
                    )),
                    Arguments.of("Multiple Generics", """
                        func name<T1, T2, T3>() {}
                    """.trimIndent(), RhovasAst.Statement.Function(
                        "name", listOf("T1" to null, "T2" to null, "T3" to null), listOf(), null, listOf(), block(),
                    )),
                    Arguments.of("Bound Generic", """
                        func name<T: Bound>() {}
                    """.trimIndent(), RhovasAst.Statement.Function(
                        "name", listOf("T" to type("Bound")), listOf(), null, listOf(), block(),
                    )),
                    Arguments.of("Single Parameter", """
                        func name(parameter) {}
                    """.trimIndent(), RhovasAst.Statement.Function(
                        "name", listOf(), listOf("parameter" to null), null, listOf(), block(),
                    )),
                    Arguments.of("Multiple Parameters", """
                        func name(first, second, third) {}
                    """.trimIndent(), RhovasAst.Statement.Function(
                        "name", listOf(), listOf("first" to null, "second" to null, "third" to null), null, listOf(), block(),
                    )),
                    Arguments.of("Typed Parameter", """
                        func name(parameter: Type) {}
                    """.trimIndent(), RhovasAst.Statement.Function(
                        "name", listOf(), listOf("parameter" to type("Type")), null, listOf(), block(),
                    )),
                    Arguments.of("Trailing Comma", """
                        func name(parameter,) {}
                    """.trimIndent(), RhovasAst.Statement.Function(
                        "name", listOf(), listOf("parameter" to null), null, listOf(), block(),
                    )),
                    Arguments.of("Return Type", """
                        func name(): Type {}
                    """.trimIndent(), RhovasAst.Statement.Function(
                        "name", listOf(), listOf(), type("Type"), listOf(), block(),
                    )),
                    Arguments.of("Single Throws", """
                        func name() throws Type {}
                    """.trimIndent(), RhovasAst.Statement.Function(
                        "name", listOf(), listOf(), null, listOf(type("Type")), block(),
                    )),
                    Arguments.of("Multiple Throws", """
                        func name() throws First, Second, Third {}
                    """.trimIndent(), RhovasAst.Statement.Function(
                        "name", listOf(), listOf(), null, listOf(type("First"), type("Second"), type("Third")), block(),
                    )),
                    Arguments.of("Missing Name", """
                        func () {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Generic", """
                        func name<>() {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Generic Colon", """
                        func name<T Bound>() {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Parenthesis", """
                        func name {}
                    """.trimIndent(), null),
                    Arguments.of("Invalid Parameter Name", """
                        func name(:atom) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Parameter Comma", """
                        func name(first second) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Parameter Closing Parenthesis", """
                        func name(argument {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Return Type", """
                        func name(): {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Throws Type Single", """
                        func name() throws {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Throws Type Multiple", """
                        func name() throws First, {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Body", """
                        func name()
                    """.trimIndent(), null),
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
                    Arguments.of("Val", """
                        val name;
                    """.trimIndent(), RhovasAst.Statement.Declaration(
                        false, "name", null, null
                    )),
                    Arguments.of("Var", """
                        var name;
                    """.trimIndent(), RhovasAst.Statement.Declaration(
                        true, "name", null, null
                    )),
                    Arguments.of("Type", """
                        val name: Type;
                    """.trimIndent(), RhovasAst.Statement.Declaration(
                        false, "name", type("Type"), null
                    )),
                    Arguments.of("Value", """
                        var name = value;
                    """.trimIndent(), RhovasAst.Statement.Declaration(
                        true, "name", null, expr("value")
                    )),
                    Arguments.of("Missing Name", """
                        val;
                    """.trimIndent(), null),
                    Arguments.of("Missing Value", """
                        val name = ;
                    """.trimIndent(), null),
                    Arguments.of("Missing Semicolon", """
                        val name
                    """.trimIndent(), null),
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
                    Arguments.of("Variable", """
                        variable = value;
                    """.trimIndent(), RhovasAst.Statement.Assignment(
                        expr("variable"),
                        expr("value"),
                    )),
                    Arguments.of("Property", """
                        receiver.property = value;
                    """.trimIndent(), RhovasAst.Statement.Assignment(
                        RhovasAst.Expression.Access.Property(expr("receiver"), false, "property"),
                        expr("value"),
                    )),
                    Arguments.of("Index", """
                        receiver[] = value;
                    """.trimIndent(), RhovasAst.Statement.Assignment(
                        RhovasAst.Expression.Access.Index(expr("receiver"), listOf()),
                        expr("value"),
                    )),
                    Arguments.of("Other", """
                        function() = value;
                    """.trimIndent(), RhovasAst.Statement.Assignment(
                        RhovasAst.Expression.Invoke.Function("function", listOf()),
                        expr("value"),
                    )),
                    Arguments.of("Missing Equals", """
                        variable value;
                    """.trimIndent(), null),
                    Arguments.of("Missing Value", """
                        variable = ;
                    """.trimIndent(), null),
                    Arguments.of("Missing Semicolon", """
                        variable = value
                    """.trimIndent(), null),
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
                    Arguments.of("Then", """
                        if (condition) thenStatement;
                    """.trimIndent(), RhovasAst.Statement.If(
                        expr("condition"),
                        stmt("thenStatement"),
                        null,
                    )),
                    Arguments.of("Empty Then", """
                        if (condition) {}
                    """.trimIndent(), RhovasAst.Statement.If(
                        expr("condition"),
                        block(),
                        null
                    )),
                    Arguments.of("Else", """
                        if (condition) {} else elseStatement;
                    """.trimIndent(), RhovasAst.Statement.If(
                        expr("condition"),
                        block(),
                        stmt("elseStatement")
                    )),
                    Arguments.of("Empty Else", """
                        if (condition) {} else {}
                    """.trimIndent(), RhovasAst.Statement.If(
                        expr("condition"),
                        block(),
                        block()
                    )),
                    Arguments.of("Missing Opening Parenthesis", """
                        if condition) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Condition", """
                        if () {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Parenthesis", """
                        if (condition {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Else", """
                        if (condition) {} {}
                    """.trimIndent(), null),
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
                    Arguments.of("Empty", """
                        match {}
                    """.trimIndent(), RhovasAst.Statement.Match.Conditional(
                        listOf(),
                        null,
                    )),
                    Arguments.of("Single", """
                        match { condition: statement; }
                    """.trimIndent(), RhovasAst.Statement.Match.Conditional(
                        listOf(expr("condition") to stmt("statement")),
                        null,
                    )),
                    Arguments.of("Multiple ", """
                        match { c1: s1; c2: s2; c3: s3; }
                    """.trimIndent(), RhovasAst.Statement.Match.Conditional(
                        listOf(expr("c1") to stmt("s1"), expr("c2") to stmt("s2"), expr("c3") to stmt("s3")),
                        null,
                    )),
                    Arguments.of("Else", """
                        match { else: statement; }
                    """.trimIndent(), RhovasAst.Statement.Match.Conditional(
                        listOf(),
                        null to stmt("statement"),
                    )),
                    Arguments.of("Else Condition", """
                        match { else condition: statement; }
                    """.trimIndent(), RhovasAst.Statement.Match.Conditional(
                        listOf(),
                        expr("condition") to stmt("statement"),
                    )),
                    Arguments.of("Else With Cases", """
                        match { c1: s1; c2: s2; else: s3; }
                    """.trimIndent(), RhovasAst.Statement.Match.Conditional(
                        listOf(expr("c1") to stmt("s1"), expr("c2") to stmt("s2")),
                        null to stmt("s3"),
                    )),
                    Arguments.of("Else Inner", """
                        match { c1: s2; else: s2; c3: s3; }
                    """.trimIndent(), null),
                    Arguments.of("Else Multiple", """
                        match { else: s1; else: s2; }
                    """.trimIndent(), null),
                    Arguments.of("Missing Opening Brace", """
                        match }
                    """.trimIndent(), null),
                    Arguments.of("Missing Condition", """
                        match { : statement; }
                    """.trimIndent(), null), //parses : statement as an Atom
                    Arguments.of("Missing Colon", """
                        match { condition statement; }
                    """.trimIndent(), null),
                    Arguments.of("Missing Statement", """
                        match { condition }
                    """.trimIndent(), null),
                    Arguments.of("Missing Else Colon", """
                        match { else statement; }
                    """.trimIndent(), null), //parses statement as the condition
                    Arguments.of("Missing Else Statement", """
                        match { else: }
                    """.trimIndent(), null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testStructural(name: String, input: String, expected: RhovasAst.Statement.Match.Structural?) {
                test("statement", input, expected)
            }

            fun testStructural(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        match (argument) {}
                    """.trimIndent(), RhovasAst.Statement.Match.Structural(
                        expr("argument"),
                        listOf(),
                        null,
                    )),
                    Arguments.of("Single", """
                        match (argument) { pattern: statement; }
                    """.trimIndent(), RhovasAst.Statement.Match.Structural(
                        expr("argument"),
                        listOf(RhovasAst.Pattern.Variable("pattern") to stmt("statement")),
                        null,
                    )),
                    Arguments.of("Multiple ", """
                        match (argument) { p1: s1; p2: s2; p3: s3; }
                    """.trimIndent(), RhovasAst.Statement.Match.Structural(
                        expr("argument"),
                        listOf(
                            RhovasAst.Pattern.Variable("p1") to stmt("s1"),
                            RhovasAst.Pattern.Variable("p2") to stmt("s2"),
                            RhovasAst.Pattern.Variable("p3") to stmt("s3"),
                        ),
                        null,
                    )),
                    Arguments.of("Else", """
                        match (argument) { else: statement; }
                    """.trimIndent(), RhovasAst.Statement.Match.Structural(
                        expr("argument"),
                        listOf(),
                        null to stmt("statement"),
                    )),
                    Arguments.of("Else Condition", """
                        match (argument) { else pattern: statement; }
                    """.trimIndent(), RhovasAst.Statement.Match.Structural(
                        expr("argument"),
                        listOf(),
                        RhovasAst.Pattern.Variable("pattern") to stmt("statement")
                    )),
                    Arguments.of("Else With Cases", """
                        match (argument) { p1: s1; p2: s2; else: s3; }
                    """.trimIndent(), RhovasAst.Statement.Match.Structural(
                        expr("argument"),
                        listOf(
                            RhovasAst.Pattern.Variable("p1") to stmt("s1"),
                            RhovasAst.Pattern.Variable("p2") to stmt("s2"),
                        ),
                        null to stmt("s3"),
                    )),
                    Arguments.of("Else Inner", """
                        match (argument) { p1: s2; else: s2; p3: s3; }
                    """.trimIndent(), null),
                    Arguments.of("Else Multiple", """
                        match (argument) { else: s1; else: s2; }
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Parenthesis", """
                        match (argument {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Opening Brace", """
                        match (argument) }
                    """.trimIndent(), null),
                    Arguments.of("Missing Pattern", """
                        match (argument) { : statement; }
                    """.trimIndent(), null), //parses : statement as an Atom
                    Arguments.of("Missing Colon", """
                        match (argument) { pattern statement; }
                    """.trimIndent(), null),
                    Arguments.of("Missing Statement", """
                        match (argument) { pattern }
                    """.trimIndent(), null),
                    Arguments.of("Missing Else Colon", """
                        match (argument) { else statement; }
                    """.trimIndent(), null), //parses statement as the pattern
                    Arguments.of("Missing Else Statement", """
                        match (argument) { else: }
                    """.trimIndent(), null),
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
                    Arguments.of("For", """
                        for (val name in iterable) body;
                    """.trimIndent(), RhovasAst.Statement.For(
                        "name", expr("iterable"), stmt("body"),
                    )),
                    Arguments.of("Empty Body", """
                        for (val name in iterable) {}
                    """.trimIndent(), RhovasAst.Statement.For(
                        "name", expr("iterable"), block(),
                    )),
                    Arguments.of("Missing Opening Parenthesis", """
                        for val name in iterable) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Val", """
                        for (name in iterable) {}
                    """.trimIndent(), null),
                    //TODO: Requires special handling for `in`
                    /*Arguments.of("Missing Name", """
                        for (val in iterable) {}
                    """.trimIndent(), null),*/
                    Arguments.of("Invalid Name", """
                        for (val :atom in iterable) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing In", """
                        for (val name iterable) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Iterable", """
                        for (val name in) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Parenthesis", """
                        for (val name in iterable {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Statement", """
                        for (val name in iterable)
                    """.trimIndent(), null),
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
                    Arguments.of("While", """
                        while (condition) body;
                    """.trimIndent(), RhovasAst.Statement.While(
                        expr("condition"), stmt("body"),
                    )),
                    Arguments.of("Empty Body", """
                        while (condition) {}
                    """.trimIndent(), RhovasAst.Statement.While(
                        expr("condition"), block(),
                    )),
                    Arguments.of("Missing Opening Parenthesis", """
                        while condition) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Condition", """
                        while () {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Parenthesis", """
                        while (condition {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Statement", """
                        while (condition)
                    """.trimIndent(), null),
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
                    Arguments.of("Try", """
                        try body;
                    """.trimIndent(), RhovasAst.Statement.Try(
                        stmt("body"), listOf(), null,
                    )),
                    Arguments.of("Empty Body", """
                        try {}
                    """.trimIndent(), RhovasAst.Statement.Try(
                        block(), listOf(), null,
                    )),
                    Arguments.of("Catch", """
                        try {} catch (val name: Type) body;
                    """.trimIndent(), RhovasAst.Statement.Try(
                        block(),
                        listOf(RhovasAst.Statement.Try.Catch("name", type("Type"), stmt("body"))),
                        null,
                    )),
                    Arguments.of("Empty Catch", """
                        try {} catch (val name: Type) {}
                    """.trimIndent(), RhovasAst.Statement.Try(
                        block(),
                        listOf(RhovasAst.Statement.Try.Catch("name", type("Type"), block())),
                        null,
                    )),
                    Arguments.of("Multiple Catch", """
                        try {}
                        catch (val first: First) {}
                        catch (val second: Second) {}
                        catch (val third: Third) {}
                    """.trimIndent(), RhovasAst.Statement.Try(
                        block(),
                        listOf(
                            RhovasAst.Statement.Try.Catch("first", type("First"), block()),
                            RhovasAst.Statement.Try.Catch("second", type("Second"), block()),
                            RhovasAst.Statement.Try.Catch("third", type("Third"), block()),
                        ),
                        null,
                    )),
                    Arguments.of("Finally", """
                        try {} finally body;
                    """.trimIndent(), RhovasAst.Statement.Try(
                        block(), listOf(), stmt("body"),
                    )),
                    Arguments.of("Empty Finally", """
                        try {} finally {}
                    """.trimIndent(), RhovasAst.Statement.Try(
                        block(), listOf(), block(),
                    )),
                    Arguments.of("Both Catch & Finally", """
                        try {} catch (val name: Type) {} finally {}
                    """.trimIndent(), RhovasAst.Statement.Try(
                        block(),
                        listOf(RhovasAst.Statement.Try.Catch("name", type("Type"), block())),
                        block(),
                    )),
                    Arguments.of("Missing Try Statement", """
                        try
                    """.trimIndent(), null),
                    Arguments.of("Missing Catch Opening Parenthesis", """
                        try {} catch val name: Type) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Catch Val", """
                        try {} catch (name: Type) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Catch Name", """
                        try {} catch (val) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Catch Type", """
                        try {} catch (val name) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Catch Closing Parenthesis", """
                        try {} catch (val name: Type {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Catch Statement", """
                        try {} catch (val name: Type)
                    """.trimIndent(), null),
                    Arguments.of("Missing Finally Statement", """
                        try {} finally
                    """.trimIndent(), null),
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
                    Arguments.of("With", """
                        with (argument) {}
                    """.trimIndent(), RhovasAst.Statement.With(
                        null, expr("argument"), block(),
                    )),
                    Arguments.of("Name", """
                        with (val name = argument) {}
                    """.trimIndent(), RhovasAst.Statement.With(
                        "name", expr("argument"), block(),
                    )),
                    Arguments.of("Missing Opening Parenthesis", """
                        with argument) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Val", """
                        with (name = argument) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Name", """
                        with (val = argument) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Equals", """
                        with (val name argument) {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Argument", """
                        with () {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Parenthesis", """
                        with (argument {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Statement", """
                        with (argument)
                    """.trimIndent(), null),
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
                    Arguments.of("Label", """
                        label: statement;
                    """.trimIndent(), RhovasAst.Statement.Label(
                        "label", stmt("statement")
                    )),
                    Arguments.of("Loop", """
                        label: while (condition) {}
                    """.trimIndent(), RhovasAst.Statement.Label(
                        "label", RhovasAst.Statement.While(expr("condition"), block()),
                    )),
                    Arguments.of("Missing Statement", """
                        label:
                    """.trimIndent(), null),
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
                    Arguments.of("Break", """
                        break;
                    """.trimIndent(), RhovasAst.Statement.Break(
                        null,
                    )),
                    Arguments.of("Label", """
                        break label;
                    """.trimIndent(), RhovasAst.Statement.Break(
                        "label",
                    )),
                    Arguments.of("Missing Semicolon", """
                        break
                    """.trimIndent(), null),
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
                    Arguments.of("Continue", """
                        continue;
                    """.trimIndent(), RhovasAst.Statement.Continue(
                        null,
                    )),
                    Arguments.of("Label", """
                        continue label;
                    """.trimIndent(), RhovasAst.Statement.Continue(
                        "label",
                    )),
                    Arguments.of("Missing Semicolon", """
                        continue
                    """.trimIndent(), null),
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
                    Arguments.of("Return", """
                        return;
                    """.trimIndent(), RhovasAst.Statement.Return(
                        null,
                    )),
                    Arguments.of("Return", """
                        return value;
                    """.trimIndent(), RhovasAst.Statement.Return(
                        expr("value"),
                    )),
                    Arguments.of("Missing Semicolon", """
                        return
                    """.trimIndent(), null),
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
                    Arguments.of("Throw", """
                        throw exception;
                    """.trimIndent(), RhovasAst.Statement.Throw(
                        expr("exception"),
                    )),
                    Arguments.of("Missing Exception", """
                        throw;
                    """.trimIndent(), null),
                    Arguments.of("Missing Semicolon", """
                        throw exception
                    """.trimIndent(), null),
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
                    Arguments.of("Assert", """
                        assert condition;
                    """.trimIndent(), RhovasAst.Statement.Assert(
                        expr("condition"), null,
                    )),
                    Arguments.of("Message", """
                        assert condition: message;
                    """.trimIndent(), RhovasAst.Statement.Assert(
                        expr("condition"), expr("message"),
                    )),
                    Arguments.of("Missing Condition", """
                        assert;
                    """.trimIndent(), null),
                    Arguments.of("Missing Colon", """
                        assert condition message;
                    """.trimIndent(), null),
                    Arguments.of("Missing Message", """
                        assert condition: ;
                    """.trimIndent(), null),
                    Arguments.of("Missing Semicolon", """
                        assert condition
                    """.trimIndent(), null),
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
                    Arguments.of("Require", """
                        require condition;
                    """.trimIndent(), RhovasAst.Statement.Require(
                        expr("condition"), null,
                    )),
                    Arguments.of("Message", """
                        require condition: message;
                    """.trimIndent(), RhovasAst.Statement.Require(
                        expr("condition"), expr("message"),
                    )),
                    Arguments.of("Missing Condition", """
                        require;
                    """.trimIndent(), null),
                    Arguments.of("Missing Colon", """
                        require condition message;
                    """.trimIndent(), null),
                    Arguments.of("Missing Message", """
                        require condition: ;
                    """.trimIndent(), null),
                    Arguments.of("Missing Semicolon", """
                        require condition
                    """.trimIndent(), null),
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
                    Arguments.of("Ensure", """
                        ensure condition;
                    """.trimIndent(), RhovasAst.Statement.Ensure(
                        expr("condition"), null,
                    )),
                    Arguments.of("Message", """
                        ensure condition: message;
                    """.trimIndent(), RhovasAst.Statement.Ensure(
                        expr("condition"), expr("message"),
                    )),
                    Arguments.of("Missing Condition", """
                        ensure;
                    """.trimIndent(), null),
                    Arguments.of("Missing Colon", """
                        ensure condition message;
                    """.trimIndent(), null),
                    Arguments.of("Missing Message", """
                        ensure condition: ;
                    """.trimIndent(), null),
                    Arguments.of("Missing Semicolon", """
                        ensure condition
                    """.trimIndent(), null),
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
            fun testScalar(name: String, input: String, expected: RhovasAst.Expression.Literal.Scalar) {
                test("expression", input, expected)
            }

            fun testScalar(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Null", """
                        null
                    """.trimIndent(), RhovasAst.Expression.Literal.Scalar(
                        null,
                    )),
                    Arguments.of("Boolean True", """
                        true
                    """.trimIndent(), RhovasAst.Expression.Literal.Scalar(
                        true,
                    )),
                    Arguments.of("Boolean False", """
                        false
                    """.trimIndent(), RhovasAst.Expression.Literal.Scalar(
                        false,
                    )),
                    Arguments.of("Integer", """
                        123
                    """.trimIndent(), RhovasAst.Expression.Literal.Scalar(
                        BigInteger("123"),
                    )),
                    Arguments.of("Integer Above Long Max", """
                        1${"0".repeat(19)}
                    """.trimIndent(), RhovasAst.Expression.Literal.Scalar(
                        BigInteger("1" + "0".repeat(19)),
                    )),
                    Arguments.of("Decimal", """
                        123.456
                    """.trimIndent(), RhovasAst.Expression.Literal.Scalar(
                        BigDecimal("123.456"),
                    )),
                    Arguments.of("Decimal Above Double Max", """
                        1${"0".repeat(308)}.0
                    """.trimIndent(), RhovasAst.Expression.Literal.Scalar(
                        BigDecimal("1${"0".repeat(308)}.0"),
                    )),
                    Arguments.of("Atom", """
                        :atom
                    """.trimIndent(), RhovasAst.Expression.Literal.Scalar(
                        RhovasAst.Atom("atom"),
                    )),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testString(name: String, input: String, expected: RhovasAst.Expression.Literal.String?) {
                test("expression", input, expected)
            }

            fun testString(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        ""
                    """.trimIndent(), RhovasAst.Expression.Literal.String(
                        listOf(""),
                        listOf(),
                    )),
                    Arguments.of("String", """
                        "string"
                    """.trimIndent(), RhovasAst.Expression.Literal.String(
                        listOf("string"),
                        listOf(),
                    )),
                    Arguments.of("Interpolation", """
                        "start${'$'}{argument}end"
                    """.trimIndent(), RhovasAst.Expression.Literal.String(
                        listOf("start", "end"),
                        listOf(RhovasAst.Expression.Access.Variable("argument")),
                    )),
                    Arguments.of("Interpolation Multiple", """
                        "start${'$'}{first}middle${'$'}{second}end"
                    """.trimIndent(), RhovasAst.Expression.Literal.String(
                        listOf("start", "middle", "end"),
                        listOf(RhovasAst.Expression.Access.Variable("first"), RhovasAst.Expression.Access.Variable("second")),
                    )),
                    Arguments.of("Interpolation Only", """
                        "${'$'}{argument}"
                    """.trimIndent(), RhovasAst.Expression.Literal.String(
                        listOf("", ""),
                        listOf(RhovasAst.Expression.Access.Variable("argument")),
                    )),
                    Arguments.of("Interpolation Only Multiple", """
                        "${'$'}{first}${'$'}{second}"
                    """.trimIndent(), RhovasAst.Expression.Literal.String(
                        listOf("", "", ""),
                        listOf(RhovasAst.Expression.Access.Variable("first"), RhovasAst.Expression.Access.Variable("second")),
                    )),
                    Arguments.of("Unterminated", """
                        "unterminated
                    """.trimIndent(), null),
                    Arguments.of("Unterminated Newline", """
                        "unterminated
                    """.trimIndent(), null),
                    Arguments.of("Unterminated Interpolation", """
                        "start${'$'}{argumentend"
                    """.trimIndent(), null)
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testList(name: String, input: String, expected: RhovasAst.Expression.Literal.List?) {
                test("expression", input, expected)
            }

            fun testList(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        []
                    """.trimIndent(), RhovasAst.Expression.Literal.List(
                        listOf(),
                    )),
                    Arguments.of("Single", """
                        [element]
                    """.trimIndent(), RhovasAst.Expression.Literal.List(
                        listOf(expr("element")),
                    )),
                    Arguments.of("Multiple", """
                        [first, second, third]
                    """.trimIndent(), RhovasAst.Expression.Literal.List(
                        listOf(expr("first"), expr("second"), expr("third")),
                    )),
                    Arguments.of("Trailing Comma", """
                        [first, second,]
                    """.trimIndent(), RhovasAst.Expression.Literal.List(
                        listOf(expr("first"), expr("second")),
                    )),
                    Arguments.of("Missing Comma", """
                        [first second]
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Bracket", """
                        [element
                    """.trimIndent(), null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testObject(name: String, input: String, expected: RhovasAst.Expression.Literal.Object?) {
                test("expression", input, expected)
            }

            fun testObject(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Empty", """
                        {}
                    """.trimIndent(), RhovasAst.Expression.Literal.Object(
                        mapOf(),
                    )),
                    Arguments.of("Single", """
                        {key: value}
                    """.trimIndent(), RhovasAst.Expression.Literal.Object(
                        mapOf("key" to expr("value")),
                    )),
                    Arguments.of("Multiple", """
                        {k1: v1, k2: v2, k3: v3}
                    """.trimIndent(), RhovasAst.Expression.Literal.Object(
                        mapOf("k1" to expr("v1"), "k2" to expr("v2"), "k3" to expr("v3")),
                    )),
                    Arguments.of("Trailing Comma", """
                        {k1: v1, k2: v2,}
                    """.trimIndent(), RhovasAst.Expression.Literal.Object(
                        mapOf("k1" to expr("v1"), "k2" to expr("v2")),
                    )),
                    Arguments.of("Key Only", """
                        {key}
                    """.trimIndent(), RhovasAst.Expression.Literal.Object(
                        mapOf("key" to expr("key")),
                    )),
                    Arguments.of("Invalid Key", """
                        {"key": value}
                    """.trimIndent(), null),
                    Arguments.of("Missing Key", """
                        {: value}
                    """.trimIndent(), null),
                    Arguments.of("Missing Colon", """
                        {key value}
                    """.trimIndent(), null),
                    Arguments.of("Missing Comma", """
                        {k1: v1 k2: v2}
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Bracket", """
                        {key: value
                    """.trimIndent(), null),
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
                    Arguments.of("Group", """
                        (expression)
                    """.trimIndent(), RhovasAst.Expression.Group(
                        expr("expression"),
                    )),
                    Arguments.of("Nested", """
                        ((expression))
                    """.trimIndent(), RhovasAst.Expression.Group(
                        RhovasAst.Expression.Group(expr("expression")),
                    )),
                    Arguments.of("Binary", """
                        (first + second)
                    """.trimIndent(), RhovasAst.Expression.Group(
                        RhovasAst.Expression.Binary("+",
                            expr("first"),
                            expr("second"),
                        ),
                    )),
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
                    Arguments.of("Numerical Negation", """
                        -expression
                    """.trimIndent(), RhovasAst.Expression.Unary(
                        "-", expr("expression"),
                    )),
                    Arguments.of("Logical Negation", """
                        !expression
                    """.trimIndent(), RhovasAst.Expression.Unary(
                        "!", expr("expression"),
                    )),
                    Arguments.of("Multiple", """
                        -!expression
                    """.trimIndent(), RhovasAst.Expression.Unary("-",
                        RhovasAst.Expression.Unary("!", expr("expression")),
                    )),
                    Arguments.of("Invalid Operator", """
                        +expression
                    """.trimIndent(), null),
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
                    Arguments.of("Multiplicative", """
                        left * right
                    """.trimIndent(), RhovasAst.Expression.Binary(
                        "*",
                        expr("left"),
                        expr("right"),
                    )),
                    Arguments.of("Additive", """
                        left + right
                    """.trimIndent(), RhovasAst.Expression.Binary(
                        "+",
                        expr("left"),
                        expr("right"),
                    )),
                    Arguments.of("Comparison", """
                        left < right
                    """.trimIndent(), RhovasAst.Expression.Binary(
                        "<",
                        expr("left"),
                        expr("right"),
                    )),
                    Arguments.of("Equality", """
                        left == right
                    """.trimIndent(), RhovasAst.Expression.Binary(
                        "==",
                        expr("left"),
                        expr("right"),
                    )),
                    Arguments.of("Logical And", """
                        left && right
                    """.trimIndent(), RhovasAst.Expression.Binary(
                        "&&",
                        expr("left"),
                        expr("right"),
                    )),
                    Arguments.of("Logical Or", """
                        left || right
                    """.trimIndent(), RhovasAst.Expression.Binary(
                        "||",
                        expr("left"),
                        expr("right"),
                    )),
                    Arguments.of("Left Precedence", """
                        first * second + third < fourth == fifth && sixth || seventh
                    """.trimIndent(),
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
                        ),
                    ),
                    Arguments.of("Right Precedence", """
                        first || second && third == fourth < fifth + sixth * seventh
                    """.trimIndent(),
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
                        ),
                    ),
                    Arguments.of("Equal Precedence", """
                        first < second <= third
                    """.trimIndent(),
                        RhovasAst.Expression.Binary("<=",
                            RhovasAst.Expression.Binary("<",
                                expr("first"),
                                expr("second"),
                            ),
                            expr("third"),
                        ),
                    ),
                    Arguments.of("Invalid Operator", """
                        first % second
                    """.trimIndent(), null),
                    Arguments.of("Missing Operator", """
                        first second
                    """.trimIndent(), null),
                    Arguments.of("Missing Right", """
                        first +
                    """.trimIndent(), null),
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
                        Arguments.of("Variable", """
                            variable
                        """.trimIndent(), RhovasAst.Expression.Access.Variable(
                            "variable"
                        )),
                        Arguments.of("Underscore", """
                            _
                        """.trimIndent(), RhovasAst.Expression.Access.Variable(
                            "_"
                        )),
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
                        Arguments.of("Property", """
                            receiver.property
                        """.trimIndent(), RhovasAst.Expression.Access.Property(
                            expr("receiver"),false, "property",
                        )),
                        Arguments.of("Multiple Properties", """
                            receiver.first.second.third
                        """.trimIndent(),
                            RhovasAst.Expression.Access.Property(
                                RhovasAst.Expression.Access.Property(
                                    RhovasAst.Expression.Access.Property(
                                        expr("receiver"), false, "first"
                                    ), false, "second",
                                ), false, "third",
                            ),
                        ),
                        Arguments.of("Nullable", """
                            receiver?.property
                        """.trimIndent(), RhovasAst.Expression.Access.Property(
                            expr("receiver"), true, "property",
                        )),
                        Arguments.of("Coalesce", """
                            receiver..property
                        """.trimIndent(), null),
                        Arguments.of("Missing Identifier", """
                            receiver.
                        """.trimIndent(), null),
                    )
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
                        Arguments.of("Zero Arguments", """
                            receiver[]
                        """.trimIndent(), RhovasAst.Expression.Access.Index(
                            expr("receiver"), listOf(),
                        )),
                        Arguments.of("Single Argument", """
                            receiver[argument]
                        """.trimIndent(), RhovasAst.Expression.Access.Index(
                            expr("receiver"), listOf(expr("argument")),
                        )),
                        Arguments.of("Multiple Arguments", """
                            receiver[first, second, third]
                        """.trimIndent(), RhovasAst.Expression.Access.Index(
                            expr("receiver"), listOf(expr("first"), expr("second"), expr("third")),
                        )),
                        Arguments.of("Multiple Indexes", """
                            receiver[first][second][third]
                        """.trimIndent(),
                            RhovasAst.Expression.Access.Index(
                                RhovasAst.Expression.Access.Index(
                                    RhovasAst.Expression.Access.Index(expr("receiver"), listOf(
                                        expr("first"),
                                    )),
                                    listOf(expr("second")),
                                ),
                                listOf(expr("third")),
                            ),
                        ),
                        Arguments.of("Trailing Comma", """
                            receiver[argument,]
                        """.trimIndent(), RhovasAst.Expression.Access.Index(
                            expr("receiver"), listOf(expr("argument")),
                        )),
                        Arguments.of("Missing Comma", """
                            receiver[first second]
                        """.trimIndent(), null),
                        Arguments.of("Missing Closing Bracket", """
                            receiver[argument
                        """.trimIndent(), null),
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
                fun testFunction(name: String, input: String, expected: RhovasAst.Expression.Invoke.Function?) {
                    test("expression", input, expected)
                }

                fun testFunction(): Stream<Arguments> {
                    return Stream.of(
                        Arguments.of("Zero Arguments", """
                            function()
                        """.trimIndent(), RhovasAst.Expression.Invoke.Function(
                            "function", listOf(),
                        )),
                        Arguments.of("Single Argument", """
                            function(argument)
                        """.trimIndent(), RhovasAst.Expression.Invoke.Function(
                            "function", listOf(expr("argument")),
                        )),
                        Arguments.of("Multiple Arguments", """
                            function(first, second, third)
                        """.trimIndent(), RhovasAst.Expression.Invoke.Function(
                            "function", listOf(expr("first"), expr("second"), expr("third"))
                        )),
                        Arguments.of("Trailing Comma", """
                            function(argument,)
                        """.trimIndent(), RhovasAst.Expression.Invoke.Function(
                            "function", listOf(expr("argument"))
                        )),
                        Arguments.of("Missing Comma", """
                            function(first second)
                        """.trimIndent(), null),
                        Arguments.of("Missing Closing Parenthesis", """
                            function(argument
                        """.trimIndent(), null),
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
                        Arguments.of("Zero Arguments", """
                            receiver.method()
                        """.trimIndent(), RhovasAst.Expression.Invoke.Method(
                            expr("receiver"), false, false, "method", listOf(),
                        )),
                        Arguments.of("Single Argument", """
                            receiver.method(argument)
                        """.trimIndent(), RhovasAst.Expression.Invoke.Method(
                            expr("receiver"), false, false, "method", listOf(expr("argument"),),
                        )),
                        Arguments.of("Multiple Arguments", """
                            receiver.method(first, second, third)
                        """.trimIndent(), RhovasAst.Expression.Invoke.Method(
                            expr("receiver"), false, false, "method", listOf(
                                expr("first"),
                                expr("second"),
                                expr("third"),
                            ),
                        )),
                        Arguments.of("Trailing Comma", """
                            receiver.method(argument,)
                        """.trimIndent(), RhovasAst.Expression.Invoke.Method(
                            expr("receiver"), false, false, "method", listOf(expr("argument"),),
                        )),
                        Arguments.of("Multiple Methods", """
                            receiver.first().second().third()
                        """.trimIndent(),
                            RhovasAst.Expression.Invoke.Method(
                                RhovasAst.Expression.Invoke.Method(
                                    RhovasAst.Expression.Invoke.Method(
                                        expr("receiver"), false, false, "first", listOf()
                                    ), false, false, "second", listOf(),
                                ), false, false, "third", listOf(),
                            ),
                        ),
                        Arguments.of("Coalesce", """
                            receiver?.method()
                        """.trimIndent(), RhovasAst.Expression.Invoke.Method(
                            expr("receiver"), true, false, "method", listOf(),
                        )),
                        Arguments.of("Cascade", """
                            receiver..method()
                        """.trimIndent(), RhovasAst.Expression.Invoke.Method(
                            expr("receiver"), false, true, "method", listOf()
                        )),
                        Arguments.of("Coalesce & Cascade", """
                            receiver?..method()
                        """.trimIndent(), RhovasAst.Expression.Invoke.Method(
                            expr("receiver"), true, true, "method", listOf(),
                        )),
                        Arguments.of("Missing Comma", """
                            receiver.method(first second)
                        """.trimIndent(), null),
                        Arguments.of("Missing Closing Parenthesis", """
                            receiver.method(argument
                        """.trimIndent(), null),
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
                        Arguments.of("Pipeline", """
                            receiver.|function()
                        """.trimIndent(), RhovasAst.Expression.Invoke.Pipeline(
                            expr("receiver"), false, false, null, "function", listOf(),
                        )),
                        Arguments.of("Single Qualifier", """
                            receiver.|Qualifier.function()
                        """.trimIndent(), RhovasAst.Expression.Invoke.Pipeline(
                            expr("receiver"), false, false, RhovasAst.Expression.Access.Variable("Qualifier"), "function", listOf(),
                        )),
                        Arguments.of("Nested Qualifier", """
                            receiver.|Nested.Qualifier.function()
                        """.trimIndent(), RhovasAst.Expression.Invoke.Pipeline(
                            expr("receiver"), false, false, RhovasAst.Expression.Access.Property(expr("Nested"), false, "Qualifier"), "function", listOf(),
                        )),
                        Arguments.of("Coalesce", """
                            receiver?.|function()
                        """.trimIndent(), RhovasAst.Expression.Invoke.Pipeline(
                            expr("receiver"), true, false, null, "function", listOf(),
                        )),
                        Arguments.of("Cascade", """
                            receiver..|function()
                        """.trimIndent(), RhovasAst.Expression.Invoke.Pipeline(
                            expr("receiver"), false, true, null, "function", listOf(),
                        )),
                        Arguments.of("Coalesce & Cascade", """
                            receiver?..|function()
                        """.trimIndent(), RhovasAst.Expression.Invoke.Pipeline(
                            expr("receiver"), true, true, null, "function", listOf(),
                        )),
                        Arguments.of("Missing Name", """
                            receiver.|
                        """.trimIndent(), null),
                        Arguments.of("Missing Invocation", """
                            receiver.|function
                        """.trimIndent(), null),
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
                    Arguments.of("Lambda", """
                        function { body; }
                    """.trimIndent(), RhovasAst.Expression.Invoke.Function(
                        "function", listOf(
                            RhovasAst.Expression.Lambda(listOf(), block(stmt("body"))),
                        ),
                    )),
                    Arguments.of("Empty Block", """
                        function {}
                    """.trimIndent(), RhovasAst.Expression.Invoke.Function(
                        "function", listOf(
                            RhovasAst.Expression.Lambda(listOf(), block()),
                        ),
                    )),
                    Arguments.of("Argument", """
                        function(argument) {}
                    """.trimIndent(), RhovasAst.Expression.Invoke.Function(
                        "function", listOf(
                            expr("argument"),
                            RhovasAst.Expression.Lambda(listOf(), block()),
                        ),
                    )),
                    Arguments.of("Single Parameter", """
                        function |parameter| {}
                    """.trimIndent(), RhovasAst.Expression.Invoke.Function(
                        "function", listOf(
                            RhovasAst.Expression.Lambda(listOf("parameter" to null), block()),
                        ),
                    )),
                    Arguments.of("Multiple Parameters", """
                        function |first, second, third| {}
                    """.trimIndent(), RhovasAst.Expression.Invoke.Function(
                        "function", listOf(
                            RhovasAst.Expression.Lambda(listOf("first" to null, "second" to null, "third" to null), block()),
                        ),
                    )),
                    Arguments.of("Typed Parameter", """
                        function |parameter: Type| {}
                    """.trimIndent(), RhovasAst.Expression.Invoke.Function(
                        "function", listOf(
                            RhovasAst.Expression.Lambda(listOf("parameter" to type("Type")), block()),
                        ),
                    )),
                    Arguments.of("Trailing Comma", """
                        function |parameter,| {}
                    """.trimIndent(), RhovasAst.Expression.Invoke.Function(
                        "function", listOf(
                            RhovasAst.Expression.Lambda(listOf("parameter" to null), block()),
                        ),
                    )),
                    Arguments.of("Argument & Parameter", """
                        function(argument) |parameter| {}
                    """.trimIndent(), RhovasAst.Expression.Invoke.Function(
                        "function", listOf(
                            expr("argument"),
                            RhovasAst.Expression.Lambda(listOf("parameter" to null), block()),
                        ),
                    )),
                    Arguments.of("Method", """
                        receiver.method {}
                    """.trimIndent(), RhovasAst.Expression.Invoke.Method(
                        expr("receiver"), false, false, "method", listOf(
                            RhovasAst.Expression.Lambda(listOf(), block()),
                        ),
                    )),
                    Arguments.of("Pipeline", """
                        receiver.|function {}
                    """.trimIndent(), RhovasAst.Expression.Invoke.Pipeline(
                        expr("receiver"), false, false, null, "function", listOf(
                            RhovasAst.Expression.Lambda(listOf(), block()),
                        ),
                    )),
                    Arguments.of("Missing Comma", """
                        function |first second| {}
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Pipe", """
                        function |first, second {} 
                    """.trimIndent(), null),
                    Arguments.of("Missing Body", """
                        function |first, second|
                    """.trimIndent(), null),
                    Arguments.of("Non-Block Statement", """
                        function body;
                    """.trimIndent(), null),
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
                    Arguments.of("Zero Arguments", """
                        #macro()
                    """.trimIndent(), RhovasAst.Expression.Macro(
                        "macro", listOf(),
                    )),
                    Arguments.of("Single Argument", """
                        #macro(argument)
                    """.trimIndent(), RhovasAst.Expression.Macro(
                        "macro", listOf(expr("argument")),
                    )),
                    Arguments.of("Multiple Arguments", """
                        #macro(first, second, third)
                    """.trimIndent(), RhovasAst.Expression.Macro(
                        "macro", listOf(
                            expr("first"),
                            expr("second"),
                            expr("third"),
                        ),
                    )),
                    Arguments.of("Trailing Comma", """
                        #macro(argument,)
                    """.trimIndent(), RhovasAst.Expression.Macro(
                        "macro", listOf(expr("argument")),
                    )),
                    Arguments.of("Missing Opening Parenthesis", """
                        #macro
                    """.trimIndent(), null),
                    Arguments.of("Missing Comma", """
                        #macro(first second)
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Parenthesis", """
                        #macro(argument
                    """.trimIndent(), null),
                )
            }

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testDsl(name: String, input: String, expected: RhovasAst.Expression.Macro?) {
                test("expression", input, expected)
            }

            fun testDsl(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Inline", """
                        #macro { source }
                    """.trimIndent(), RhovasAst.Expression.Macro(
                        "macro", listOf(
                            RhovasAst.Expression.Dsl("macro", DslAst.Source(listOf(" source "), listOf())),
                        ),
                    )),
                    Arguments.of("Multiline", """
                        #macro {
                            source
                        }
                    """.trimIndent(), RhovasAst.Expression.Macro(
                        "macro", listOf(
                            RhovasAst.Expression.Dsl("macro", DslAst.Source(listOf("source"), listOf())),
                        ),
                    )),
                    Arguments.of("Argument", """
                        #macro(argument) { source }
                    """.trimIndent(), RhovasAst.Expression.Macro(
                        "macro", listOf(
                            expr("argument"),
                            RhovasAst.Expression.Dsl("macro", DslAst.Source(listOf(" source "), listOf())),
                        ),
                    )),
                    Arguments.of("Interpolation", """
                        #macro {
                            value = ${'$'}{argument}
                        }
                    """.trimIndent(), RhovasAst.Expression.Macro(
                        "macro", listOf(RhovasAst.Expression.Dsl("macro", DslAst.Source(
                            listOf("value = ", ""),
                            listOf(RhovasAst.Expression.Interpolation(RhovasAst.Expression.Access.Variable("argument"))),
                        ))),
                    )),
                    Arguments.of("Missing Closing Brace", """
                        #macro { source()
                    """.trimIndent(), null),
                    Arguments.of("Missing Interpolation Closing Brace", """
                        #macro {
                            value = ${'$'}{argument
                    """.trimIndent(), null)
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
                    Arguments.of("Variable", """
                        variable
                    """.trimIndent(), RhovasAst.Pattern.Variable(
                        "variable"
                    )),
                    Arguments.of("Underscore", """
                        _
                    """.trimIndent(), RhovasAst.Pattern.Variable(
                        "_"
                    )),
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
                    Arguments.of("Null", """
                        null
                    """.trimIndent(), RhovasAst.Pattern.Value(
                        RhovasAst.Expression.Literal.Scalar(null),
                    )),
                    Arguments.of("Boolean True", """
                        true
                    """.trimIndent(), RhovasAst.Pattern.Value(
                        RhovasAst.Expression.Literal.Scalar(true),
                    )),
                    Arguments.of("Boolean False", """
                        false
                    """.trimIndent(), RhovasAst.Pattern.Value(
                        RhovasAst.Expression.Literal.Scalar(false),
                    )),
                    Arguments.of("Integer", """
                        0
                    """.trimIndent(), RhovasAst.Pattern.Value(
                        RhovasAst.Expression.Literal.Scalar(BigInteger("0")),
                    )),
                    Arguments.of("Decimal", """
                        0.0
                    """.trimIndent(), RhovasAst.Pattern.Value(
                        RhovasAst.Expression.Literal.Scalar(BigDecimal("0.0")),
                    )),
                    Arguments.of("String", """
                        "string"
                    """.trimIndent(), RhovasAst.Pattern.Value(
                        RhovasAst.Expression.Literal.String(listOf("string"), listOf()),
                    )),
                    Arguments.of("Atom", """
                        :atom
                    """.trimIndent(), RhovasAst.Pattern.Value(
                        RhovasAst.Expression.Literal.Scalar(RhovasAst.Atom("atom")),
                    )),
                    Arguments.of("Interpolation", """
                        ${'$'}{value}
                    """.trimIndent(), RhovasAst.Pattern.Value(
                        expr("value"),
                    )),
                    Arguments.of("Missing Opening Brace", """
                        ${'$'}value}
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Brace", """
                        ${'$'}{value
                    """.trimIndent(), null),
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
                    Arguments.of("Variable", """
                        pattern ${'$'}{predicate}
                    """.trimIndent(), RhovasAst.Pattern.Predicate(
                        RhovasAst.Pattern.Variable("pattern"),
                        expr("predicate"),
                    )),
                    Arguments.of("OrderedDestructure", """
                        [ordered] ${'$'}{predicate}
                    """.trimIndent(), RhovasAst.Pattern.Predicate(
                        RhovasAst.Pattern.OrderedDestructure(listOf(RhovasAst.Pattern.Variable("ordered"))),
                        expr("predicate"),
                    )),
                    Arguments.of("VarargDestructure", """
                        pattern* ${'$'}{predicate}
                    """.trimIndent(), RhovasAst.Pattern.Predicate(
                        RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("pattern"), "*"),
                        expr("predicate")
                    )),
                    Arguments.of("Missing Opening Brace", """
                        pattern ${'$'}predicate}
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Brace", """
                        pattern ${'$'}{predicate
                    """.trimIndent(), null),
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
                    Arguments.of("Empty", """
                        []
                    """.trimIndent(), RhovasAst.Pattern.OrderedDestructure(
                        listOf(),
                    )),
                    Arguments.of("Single", """
                        [pattern]
                    """.trimIndent(), RhovasAst.Pattern.OrderedDestructure(
                        listOf(RhovasAst.Pattern.Variable("pattern")),
                    )),
                    Arguments.of("Multiple", """
                        [first, second, third]
                    """.trimIndent(), RhovasAst.Pattern.OrderedDestructure(
                        listOf(
                            RhovasAst.Pattern.Variable("first"),
                            RhovasAst.Pattern.Variable("second"),
                            RhovasAst.Pattern.Variable("third"),
                        ),
                    )),
                    Arguments.of("Varargs", """
                        [first, rest*]
                    """.trimIndent(), RhovasAst.Pattern.OrderedDestructure(
                        listOf(
                            RhovasAst.Pattern.Variable("first"),
                            RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("rest"), "*"),
                        ),
                    )),
                    Arguments.of("Varargs Only", """
                        [first, +]
                    """.trimIndent(), RhovasAst.Pattern.OrderedDestructure(
                        listOf(
                            RhovasAst.Pattern.Variable("first"),
                            RhovasAst.Pattern.VarargDestructure(null, "+"),
                        ),
                    )),
                    Arguments.of("Missing Comma", """
                        [first second]
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Bracket", """
                        [pattern
                    """.trimIndent(), null),
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
                    Arguments.of("Empty", """
                        {}
                    """.trimIndent(), RhovasAst.Pattern.NamedDestructure(
                        listOf()
                    )),
                    Arguments.of("Single", """
                        {key: pattern}
                    """.trimIndent(), RhovasAst.Pattern.NamedDestructure(
                        listOf("key" to RhovasAst.Pattern.Variable("pattern")),
                    )),
                    Arguments.of("Multiple", """
                        {k1: p1, k2: p2, k3: p3}
                    """.trimIndent(), RhovasAst.Pattern.NamedDestructure(
                        listOf(
                            "k1" to RhovasAst.Pattern.Variable("p1"),
                            "k2" to RhovasAst.Pattern.Variable("p2"),
                            "k3" to RhovasAst.Pattern.Variable("p3"),
                        ),
                    )),
                    Arguments.of("Key Only", """
                        {key}
                    """.trimIndent(), RhovasAst.Pattern.NamedDestructure(
                        listOf("key" to null),
                    )),
                    Arguments.of("Varargs", """
                        {key: pattern, rest*}
                    """.trimIndent(), RhovasAst.Pattern.NamedDestructure(
                        listOf(
                            "key" to RhovasAst.Pattern.Variable("pattern"),
                            "" to RhovasAst.Pattern.VarargDestructure(RhovasAst.Pattern.Variable("rest"), "*"),
                        ),
                    )),
                    Arguments.of("Varargs Only", """
                        {key: pattern, +}
                    """.trimIndent(), RhovasAst.Pattern.NamedDestructure(
                        listOf(
                            "key" to RhovasAst.Pattern.Variable("pattern"),
                            "" to RhovasAst.Pattern.VarargDestructure(null, "+"),
                        ),
                    )),
                    Arguments.of("Missing Key", """
                        { :pattern}
                    """.trimIndent(), null),
                    Arguments.of("Missing Colon", """
                        {key pattern}
                    """.trimIndent(), null),
                    Arguments.of("Missing Comma", """
                        {k1: p1 k2: p2}
                    """.trimIndent(), null),
                    Arguments.of("Missing Closing Bracket", """
                        {key: pattern
                    """.trimIndent(), null),
                )
            }

        }

        @Nested
        inner class TypedDestructureTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testTypedDestructure(name: String, input: String, expected: RhovasAst.Pattern.TypedDestructure?) {
                test("pattern", input, expected)
            }

            fun testTypedDestructure(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Type", """
                        Type
                    """.trimIndent(), RhovasAst.Pattern.TypedDestructure(
                        type("Type"), null,
                    )),
                    Arguments.of("Pattern", """
                        Type pattern
                    """.trimIndent(), RhovasAst.Pattern.TypedDestructure(
                        type("Type"), RhovasAst.Pattern.Variable("pattern"),
                    )),
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
                    Arguments.of("Zero Or More", """
                        pattern*
                    """.trimIndent(), RhovasAst.Pattern.VarargDestructure(
                        RhovasAst.Pattern.Variable("pattern"), "*",
                    )),
                    Arguments.of("One Or More", """
                        pattern+
                    """.trimIndent(), RhovasAst.Pattern.VarargDestructure(
                        RhovasAst.Pattern.Variable("pattern"), "+"
                    )),
                    Arguments.of("Operator Only", """
                        *
                    """.trimIndent(), RhovasAst.Pattern.VarargDestructure(
                        null, "*"
                    )),
                )
            }

        }

        @Nested
        inner class ErrorTests {

            @ParameterizedTest(name = "{0}")
            @MethodSource
            fun testError(name: String, input: String, expected: RhovasAst.Pattern.VarargDestructure?) {
                test("pattern", input, expected)
            }

            fun testError(): Stream<Arguments> {
                return Stream.of(
                    Arguments.of("Error", """
                        #
                    """.trimIndent(), null),
                )
            }

        }

    }

    @Nested
    inner class TypeTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testType(name: String, input: String, expected: RhovasAst.Type?) {
            test("type", input, expected)
        }

        fun testType(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Type", """
                    Type
                """.trimIndent(), RhovasAst.Type(
                    "Type", null,
                )),
                Arguments.of("Empty Generics", """
                    Type<>
                """.trimIndent(), RhovasAst.Type(
                    "Type", listOf(),
                )),
                Arguments.of("Single Generic", """
                    Type<Generic>
                """.trimIndent(), RhovasAst.Type(
                    "Type", listOf(type("Generic")),
                )),
                Arguments.of("Multiple Generics", """
                    Type<First, Second, Third>
                """.trimIndent(), RhovasAst.Type(
                    "Type", listOf(type("First"), type("Second"), type("Third")),
                )),
                Arguments.of("Trailing Comma", """
                    Type<Generic,>
                """.trimIndent(), RhovasAst.Type(
                    "Type", listOf(type("Generic")),
                )),
                Arguments.of("Missing Comma", """
                    Type<First Second>
                """.trimIndent(), null),
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
                Arguments.of("Keyword Label Atom", "statement", """
                    return :atom;
                """.trimIndent(),
                    RhovasAst.Statement.Return(
                        RhovasAst.Expression.Literal.Scalar(RhovasAst.Atom("atom"))
                    ),
                ),
                //expression
                Arguments.of("Lambda Zero Parameters", "expression", """
                    function || {} 
                """.trimIndent(),
                    RhovasAst.Expression.Binary("||",
                        expr("function"),
                        RhovasAst.Expression.Literal.Object(mapOf())
                    ),
                ),
            )
        }

    }

    private fun block(vararg statements: RhovasAst.Statement): RhovasAst.Statement.Block {
        return RhovasAst.Statement.Block(statements.toList())
    }

    private fun stmt(name: String): RhovasAst.Statement {
        return RhovasAst.Statement.Expression(expr(name))
    }

    private fun expr(name: String): RhovasAst.Expression {
        return RhovasAst.Expression.Access.Variable(name)
    }

    private fun type(name: String): RhovasAst.Type {
        return RhovasAst.Type(name, null)
    }

    private fun test(rule: String, input: String, expected: RhovasAst?) {
        val parser = RhovasParser(Input("Test", input))
        if (expected != null) {
            val ast = parser.parse(rule)
            Assertions.assertEquals(expected, ast)
            Assertions.assertTrue(ast.context.isNotEmpty() || input.isBlank())
        } else {
            val exception = Assertions.assertThrows(ParseException::class.java) { parser.parse(rule) }
            Assertions.assertNotEquals("Broken lexer invariant.", exception.summary)
            Assertions.assertNotEquals("Broken parser invariant.", exception.summary)
        }
    }

}
