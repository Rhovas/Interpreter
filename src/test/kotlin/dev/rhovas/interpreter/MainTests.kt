package dev.rhovas.interpreter

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.stream.Stream

class MainTests {

    private val SYSTEM_OUT = System.out
    private lateinit var out: ByteArrayOutputStream

    @BeforeEach
    fun beforeEach() {
        out = ByteArrayOutputStream()
        System.setOut(PrintStream(out))
    }

    @AfterEach
    fun afterEach() {
        System.setOut(SYSTEM_OUT)
    }

    @Nested
    inner class ProgramTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testProgram(name: String, expected: String) {
            test("src/test/resources/dev/rhovas/interpreter/programs/${name}", expected)
        }

        fun testProgram(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("HelloWorld.rho", """
                    Hello, World!
                """.trimIndent().replace("\n", System.lineSeparator()) + System.lineSeparator()),
                //TODO: Requires type inference for lambdas & patterns
                /*Arguments.of("Factorial.rho", """
                    1
                    1
                    2
                    6
                    24
                    120
                    720
                    5040
                    40320
                    362880
                    3628800
                """.trimIndent().replace("\n", System.lineSeparator()) + System.lineSeparator()),
                Arguments.of("FizzBuzz.rho", """
                    1
                    2
                    Fizz
                    4
                    Buzz
                    Fizz
                    7
                    8
                    Fizz
                    Buzz
                    11
                    Fizz
                    13
                    14
                    FizzBuzz
                """.trimIndent().replace("\n", System.lineSeparator()) + System.lineSeparator()),
                Arguments.of("Palindrome.rho", """
                    true
                    true
                    true
                    false
                """.trimIndent().replace("\n", System.lineSeparator()) + System.lineSeparator()),*/
            )
        }

    }

    private fun test(path: String, expected: String) {
        main(arrayOf(path))
        Assertions.assertEquals(expected, out.toString())
    }

}
