package dev.rhovas.interpreter.analyzer

import dev.rhovas.interpreter.parser.Input

class AnalyzeException(
    val summary: String,
    val details: String,
    val range: Input.Range,
    val context: List<Input.Range>,
): Exception("${range.line}:${range.column}-${range.column + range.length}: ${summary}")
