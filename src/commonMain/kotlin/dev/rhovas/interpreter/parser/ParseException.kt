package dev.rhovas.interpreter.parser

data class ParseException(
    val summary: String,
    val details: String,
    val range: Input.Range,
    val context: List<Input.Range>
): Exception("${range.line}:${range.column}-${range.column + range.length}: ${summary}")
