package dev.rhovas.interpreter.programs

import dev.rhovas.interpreter.Interpreter
import dev.rhovas.interpreter.parser.Input
import kotlin.test.assertEquals
import kotlin.test.fail

object ProgramTests {

    fun test(input: Input, stdin: String, stdout: String) {
        val lines = stdin.lineSequence().iterator()
        val builder = StringBuilder()
        val interpreter = Interpreter(
            stdin = { lines.next() },
            stdout = { builder.append(it).append('\n') },
        )
        interpreter.eval(input)?.let { fail("Unexpected evaluation response: ${it}") }
        assertEquals(stdout, builder.toString())
    }

}
