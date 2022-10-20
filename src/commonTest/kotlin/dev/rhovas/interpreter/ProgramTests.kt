package dev.rhovas.interpreter

import dev.rhovas.interpreter.parser.Input
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgramTests {

    @Test
    fun testFactorial() = test("Factorial.rho", """
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
    """.trimIndent())

    @Test
    fun testFizzBuzz() = test("FizzBuzz.rho", """
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
    """.trimIndent())

    @Test
    fun testHelloWorld() = test("HelloWorld.rho", """
        Hello, World!
    """.trimIndent())

    @Test
    fun testPalindrome() = test("Palindrome.rho", """
        true
        true
        true
        false
    """.trimIndent())

    fun test(name: String, expected: String) {
        val builder = StringBuilder()
        INTERPRETER = Interpreter { builder.append(it).append('\n') }
        val input = Input(name, readFile("src/commonTest/resources/dev/rhovas/interpreter/programs/${name}"))
        INTERPRETER.eval(input)
        assertEquals(expected + '\n', builder.toString())
    }

}
