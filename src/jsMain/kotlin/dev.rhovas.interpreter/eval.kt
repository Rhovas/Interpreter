@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package dev.rhovas.interpreter

import dev.rhovas.interpreter.parser.Input

fun eval(source: String, stdin: () -> String = ::readln, stdout: (String) -> Unit = ::println) {
    val input = Input("eval", source)
    val print = Interpreter(stdin, stdout).eval(input)
    print?.let(stdout)
}
