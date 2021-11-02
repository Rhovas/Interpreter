package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class DslParserTests {

    @Nested
    inner class SourceTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testInline(name: String, input: String, expected: DslAst.Source?) {
            test("source", input, expected)
        }

        fun testInline(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Empty", "{}",
                    DslAst.Source(listOf(""), listOf()),
                ),
                Arguments.of("Text", "{text}",
                    DslAst.Source(listOf("text"), listOf()),
                ),
                Arguments.of("Surrounding Whitespace", "{    text    }",
                    DslAst.Source(listOf("    text    "), listOf()),
                ),
                Arguments.of("Dollar Sign", "{first\$second}",
                    DslAst.Source(listOf("first\$second"), listOf()),
                ),
                Arguments.of("Newline", "{first\nsecond}", null),
                Arguments.of("Braces", "{text{}second}", null),
            )
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testMultiline(name: String, input: String, expected: DslAst.Source?) {
            test("source", input, expected)
        }

        fun testMultiline(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Text", """
                    {
                        text
                    }
                """.trimIndent(),
                    DslAst.Source(listOf("text"), listOf()),
                ),
                Arguments.of("Multiline Text", """
                    {
                        first
                        second
                        third
                    }
                """.trimIndent(),
                    DslAst.Source(listOf("first\nsecond\nthird"), listOf()),
                ),
                Arguments.of("Multiline Indented", """
                    {
                        first
                            second
                        third
                    }
                """.trimIndent(),
                    DslAst.Source(listOf("first\n    second\nthird"), listOf()),
                ),
                Arguments.of("Multiline Empty", """
                    {
                        first
                    
                        second
                    }
                """.trimIndent(),
                    DslAst.Source(listOf("first\n\nsecond"), listOf()),
                ),
                Arguments.of("Dollar Sign", """
                    {
                        first${"\$"}second
                    }
                """.trimIndent(),
                    DslAst.Source(listOf("first\$second"), listOf()),
                ),
                Arguments.of("Braces", """
                    {
                        {}
                    }
                """.trimIndent(),
                    DslAst.Source(listOf("{}"), listOf()),
                ),
                Arguments.of("Interpolation", """
                    {
                        ${"\$"}{value}
                    }
                """.trimIndent(),
                    DslAst.Source(listOf("", ""), listOf(
                        RhovasAst.Expression.Access(null, false, "value"),
                    ))
                ),
                Arguments.of("Empty", """
                    {
                    }
                """.trimIndent(),
                    null
                ),
                Arguments.of("Double Leading Indentation", """
                    {
                            first
                        second
                    }
                """.trimIndent(),
                    null
                ),
            )
        }

    }

    private fun test(rule: String, input: String, expected: DslAst?) {
        if (expected != null) {
            Assertions.assertEquals(expected, DslParser(input).parse(rule))
        } else {
            Assertions.assertThrows(ParseException::class.java) { DslParser(input).parse(rule) }
        }
    }

}
