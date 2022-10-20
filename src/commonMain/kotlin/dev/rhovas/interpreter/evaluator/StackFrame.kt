package dev.rhovas.interpreter.evaluator

import dev.rhovas.interpreter.parser.Input

data class StackFrame(
    val source: String,
    val range: Input.Range,
)
