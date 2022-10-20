@file:OptIn(ExperimentalJsExport::class)
@file:JsExport

package dev.rhovas.interpreter

import dev.rhovas.interpreter.parser.Input

fun eval(source: String, stdout: (String) -> Unit = ::println) {
    val input = Input("eval", source)
    val print = Interpreter(stdout).eval(input)
    print?.let(stdout)
}
