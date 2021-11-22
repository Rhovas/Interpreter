package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.parser.Input

sealed class DslAst {

    lateinit var context: List<Input.Range> internal set

    data class Source(
        val literals: List<String>,
        val arguments: List<Any>,
    ) : DslAst()

}
