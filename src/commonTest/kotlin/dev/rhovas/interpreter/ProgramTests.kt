package dev.rhovas.interpreter

import dev.rhovas.interpreter.parser.Input
import kotlin.test.assertEquals
import kotlin.test.fail

object ProgramTests {

    fun test(input: Input, stdout: String) {
        val builder = StringBuilder()
        val interpreter = Interpreter { builder.append(it).append('\n') }
        interpreter.eval(input)?.let { fail("Unexpected evaluation response: ${it}") }
        assertEquals(stdout, builder.toString())
    }

}
