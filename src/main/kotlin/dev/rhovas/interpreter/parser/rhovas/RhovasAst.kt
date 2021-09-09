package dev.rhovas.interpreter.parser.rhovas

sealed class RhovasAst {

    sealed class Expression: RhovasAst() {

        data class Literal(
            val value: Any?,
        ): Expression()

        data class Access(
            //TODO: val receiver: Expression?
            val name: String,
        ) : Expression()

        data class Function(
            //TODO: val receiver: Expression?
            val name: String,
            val arguments: List<Expression>,
        ) : Expression()

    }

    data class Atom(val name: String)

}
