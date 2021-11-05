package dev.rhovas.interpreter.parser

data class ParseException(
    override val message: String,
    val range: Input.Range,
): Exception("${range.line}.${range.column}-${range.column + range.length}: ${message}")
