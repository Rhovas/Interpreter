package dev.rhovas.interpreter.evaluator

import dev.rhovas.interpreter.analyzer.rhovas.RhovasIr
import dev.rhovas.interpreter.parser.Input

data class StackFrame(
    val ir: RhovasIr?,
    val source: String,
    val range: Input.Range,
)
