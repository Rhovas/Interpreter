package dev.rhovas.interpreter.parser.rhovas

sealed class RhovasAst {

    sealed class Expression: RhovasAst() {

        data class Literal(
            val value: Any?,
        ): Expression()

        data class Group(
            val expression: Expression,
        ) : Expression()

        data class Unary(
            val operator: String,
            val expression: Expression,
        ) : Expression()

        data class Binary(
            val operator: String,
            val left: Expression,
            val right: Expression,
        ) : Expression()

        data class Access(
            val receiver: Expression?,
            val name: String,
        ) : Expression()

        data class Index(
            val receiver: Expression,
            val arguments: List<Expression>,
        ) : Expression()

        data class Function(
            val receiver: Expression?,
            val name: String,
            val arguments: List<Expression>,
        ) : Expression()

        data class Macro(
            val name: String,
            val arguments: List<Expression>,
            //TODO: Syntax macros
        ) : Expression()

    }

    data class Atom(val name: String)

}
