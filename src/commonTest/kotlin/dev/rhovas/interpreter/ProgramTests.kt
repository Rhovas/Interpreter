package dev.rhovas.interpreter

import dev.rhovas.interpreter.parser.Input
import kotlin.test.assertEquals
import kotlin.test.fail

class ProgramTests {

    //Test structure retained for future tests.

    fun test(name: String, stdout: String = "") {
        test(Input(name, readFile("src/commonTest/resources/dev/rhovas/interpreter/programs/${name}")), stdout)
    }

    companion object {

        fun test(input: Input, stdout: String) {
            val builder = StringBuilder()
            INTERPRETER = Interpreter { builder.append(it).append('\n') }
            INTERPRETER.eval(input)?.let { fail("Unexpected evaluation response: ${it}") }
            assertEquals(stdout, builder.toString().trimEnd())
        }

    }

}
