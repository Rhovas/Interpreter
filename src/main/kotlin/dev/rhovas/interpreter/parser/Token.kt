package dev.rhovas.interpreter.parser

data class Token<T : Token.Type>(
    val type: T,
    val literal: String,
    val value: Any?,
) {

    interface Type

}
