package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.parser.Input

sealed class DslAst {

    lateinit var context: List<Input.Range> internal set

    /**
     * DSLs are represented using a combination of string literals and
     * interpolated arguments. There should always be one more literal than
     * arguments since each argument is surrounded by potentially-empty strings.
     */
    data class Source(
        val literals: List<String>,
        val arguments: List<Any>,
    ) : DslAst()

}
