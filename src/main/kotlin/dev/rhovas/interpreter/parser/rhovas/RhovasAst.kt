package dev.rhovas.interpreter.parser.rhovas

sealed class RhovasAst {

    sealed class Statement: RhovasAst() {

        data class Block(
            val statements: List<Statement>,
        ) : Statement()

        data class Expression(
            val expression: RhovasAst.Expression,
        ) : Statement()

        data class Declaration(
            val mutable: Boolean,
            val name: String,
            //TODO: val type: Type
            val value: RhovasAst.Expression?,
        ) : Statement()

        data class Assignment(
            val receiver: RhovasAst.Expression,
            val value: RhovasAst.Expression,
        ) : Statement()

        data class If(
            val condition: RhovasAst.Expression,
            val thenStatement: Statement,
            val elseStatement: Statement?,
        ) : Statement()

        data class Match(
            val argument: RhovasAst.Expression?,
            val cases: List<Pair<RhovasAst.Expression, Statement>>,
            val elseCase: Pair<RhovasAst.Expression?, Statement>?,
        ) : Statement()

        data class For(
            val name: String,
            val iterable: RhovasAst.Expression,
            val body: Statement,
        ) : Statement()

        data class While(
            val condition: RhovasAst.Expression,
            val body: Statement,
        ) : Statement()

        data class Try(
            val body: Statement,
            val catches: List<Catch>,
            val finallyStatement: Statement?
        ) : Statement() {

            data class Catch(
                val name: String,
                //TODO: val type: Type,
                val body: Statement,
            ) : RhovasAst()

        }

        data class With(
            val name: String?,
            val argument: RhovasAst.Expression,
            val body: Statement,
        ) : Statement()

        data class Label(
            val label: String,
            val statement: Statement,
        ) : Statement()

        data class Break(
            val label: String?,
        ) : Statement()

        data class Continue(
            val label: String?,
        ) : Statement()

        data class Return(
            val value: RhovasAst.Expression?,
        ) : Statement()

        data class Throw(
            val exception: RhovasAst.Expression,
        ) : Statement()

    }

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
