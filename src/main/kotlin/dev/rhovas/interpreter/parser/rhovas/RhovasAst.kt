package dev.rhovas.interpreter.parser.rhovas

sealed class RhovasAst {

    sealed class Expression: RhovasAst() {

        data class Literal(
            val value: Any?,
        ): Expression()

    }

    data class Atom(val name: String)

}
