package dev.rhovas.interpreter.programs.rosettacode

import dev.rhovas.interpreter.Platform
import dev.rhovas.interpreter.programs.ProgramTests
import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.parser.Input

class RosettaCodeTests: RhovasSpec() {

    data class Test(
        val name: String,
        val stdin: String = "",
        val stdout: String = "",
    )

    init {
        listOf(
            Test("Classes"),
            Test("Factorial"),
            Test("Fibonacci_sequence"),
            Test("FizzBuzz", stdout = "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz\n"),
            Test("Hello_world/Text", stdout = "Hello world!\n"),
            Test("Palindrome_detection"),
        ).forEach { spec(it.name) {
            println("https://www.rosettacode.org/wiki/${it.name}#Rhovas")
            val input = Input(it.name, Platform.readFile("src/commonTest/resources/dev/rhovas/interpreter/programs/rosettacode/${it.name}.rho"))
            ProgramTests.test(input, it.stdin, it.stdout)
        } }
    }

}
