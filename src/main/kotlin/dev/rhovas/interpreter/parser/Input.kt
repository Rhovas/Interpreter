package dev.rhovas.interpreter.parser

data class Input(
    val source: String,
    val content: String,
) {

    data class Range(
        val index: Int,
        val line: Int,
        val column: Int,
        val length: Int,
    )

}
