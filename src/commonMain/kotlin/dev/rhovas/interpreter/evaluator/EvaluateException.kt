package dev.rhovas.interpreter.evaluator

import dev.rhovas.interpreter.parser.Input

data class EvaluateException(
    val summary: String,
    val details: String,
    val range: Input.Range,
    val context: List<Input.Range>,
): Exception("${range.line}:${range.column}-${range.column + range.length}: ${summary}")
