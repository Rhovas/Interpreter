package dev.rhovas.interpreter.programs.rosettacode

import dev.rhovas.interpreter.Platform
import dev.rhovas.interpreter.programs.ProgramTests
import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.parser.Input

class RosettaCodeTests: RhovasSpec() {

    init {
        test("Factorial.rho")
        test("Fibonacci.rho")
        test("FizzBuzz.rho", "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz\n")
        test("HelloWorld.rho", "Hello, World!\n")
        test("Palindrome.rho")
    }

    private fun test(name: String, stdout: String = "") = spec(name) {
        val input = Input(name, Platform.readFile("src/commonTest/resources/dev/rhovas/interpreter/programs/rosettacode/${name}"))
        ProgramTests.test(input, stdout)
    }

}
