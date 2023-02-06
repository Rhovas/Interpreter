package dev.rhovas.interpreter.rosettacode

import dev.rhovas.interpreter.ProgramTests
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.readFile
import kotlin.test.Test

class RosettaCodeTests {

    @Test
    fun testFactorial() = test("Factorial.rho")

    @Test
    fun testFibonacci() = test("Fibonacci.rho")

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
    fun testPalindrome() = test("Palindrome.rho")

    private fun test(name: String, stdout: String = "") {
        ProgramTests.test(Input(name, readFile("src/commonTest/resources/dev/rhovas/interpreter/programs/rosettacode/${name}")), stdout)
    }

}
