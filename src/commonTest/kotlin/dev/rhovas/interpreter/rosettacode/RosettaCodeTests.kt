package dev.rhovas.interpreter.rosettacode

import dev.rhovas.interpreter.ProgramTests
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.readFile
import io.kotest.core.spec.style.StringSpec

class RosettaCodeTests: StringSpec() {

    init {
        test("Factorial.rho")
        test("Fibonacci.rho")
        test("FizzBuzz.rho", "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz\n")
        test("HelloWorld.rho", "Hello, World!\n")
        test("Palindrome.rho")
    }

    private fun test(name: String, stdout: String = "") = name {
        val input = Input(name, readFile("src/commonTest/resources/dev/rhovas/interpreter/programs/rosettacode/${name}"))
        ProgramTests.test(input, stdout)
    }

}
