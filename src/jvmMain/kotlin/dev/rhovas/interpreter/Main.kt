package dev.rhovas.interpreter

import dev.rhovas.interpreter.parser.Input
import java.io.File
import kotlin.system.exitProcess

/**
 * Rhovas JVM main. If given an argument, the argument is the path of a file to
 * be evaluated. Without arguments, enters a REPL environment.
 */
fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        val input = Input(args[0], File(args[0]).readText())
        val print = INTERPRETER.eval(input)
        print?.let(INTERPRETER.stdout)
    } else {
        while (true) {
            val input = read()
            val print = INTERPRETER.eval(input)
            print?.let(INTERPRETER.stdout)
        }
    }
}

/**
 * Reads input from stdin. This hackily supports multiline input by tracking
 * indentation based on leading/trailing parenthesis/braces/brackets.
 */
fun read(): Input {
    val builder = StringBuilder()
    var indent = 0
    do {
        val line = readLine() ?: exitProcess(0)
        if (line.firstOrNull { it != ' ' } in listOf(')', '}', ']')) {
            indent--
        }
        if (line.lastOrNull { it != ' ' } in listOf('(', '{', '[')) {
            indent++
        }
        builder.append(line).append('\n')
    } while (indent != 0)
    return Input("REPL", builder.toString())
}
