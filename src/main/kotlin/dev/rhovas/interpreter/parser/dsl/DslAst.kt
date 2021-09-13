package dev.rhovas.interpreter.parser.dsl

sealed class DslAst {

    data class Source(
        val literals: List<String>,
        val arguments: List<Any>,
    ) : DslAst()

}
