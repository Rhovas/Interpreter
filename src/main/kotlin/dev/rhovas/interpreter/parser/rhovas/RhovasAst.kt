package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.Input

sealed class RhovasAst {

    var context: List<Input.Range> = listOf()
        internal set

    data class Source(
        val statements: List<Statement>,
    ) : RhovasAst()

    sealed class Component : RhovasAst() {

        data class Struct(
            val name: String,
            val fields: List<Statement.Declaration>,
        ) : Component()

    }

    sealed class Statement : RhovasAst() {

        data class Block(
            val statements: List<Statement>,
        ) : Statement()

        data class Component(
            val component: RhovasAst.Component,
        ) : Statement()

        data class Expression(
            val expression: RhovasAst.Expression,
        ) : Statement()

        data class Function(
            val name: String,
            val generics: List<Pair<String, Type?>>,
            val parameters: List<Pair<String, Type?>>,
            val returns: Type?,
            val throws: List<Type>,
            val body: Statement,
        ) : Statement()

        data class Declaration(
            val mutable: Boolean,
            val name: String,
            val type: Type?,
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

        sealed class Match : Statement() {

            data class Conditional(
                val cases: List<Pair<RhovasAst.Expression, Statement>>,
                val elseCase: Pair<RhovasAst.Expression?, Statement>?,
            ) : Match()

            data class Structural(
                val argument: RhovasAst.Expression,
                val cases: List<Pair<Pattern, Statement>>,
                val elseCase: Pair<Pattern?, Statement>?
            ) : Match()

        }

        data class For(
            val name: String,
            val argument: RhovasAst.Expression,
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
                val type: Type,
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

        data class Assert(
            val condition: RhovasAst.Expression,
            val message: RhovasAst.Expression?,
        ) : Statement()

        data class Require(
            val condition: RhovasAst.Expression,
            val message: RhovasAst.Expression?,
        ) : Statement()

        data class Ensure(
            val condition: RhovasAst.Expression,
            val message: RhovasAst.Expression?,
        ) : Statement()

    }

    sealed class Expression : RhovasAst() {

        sealed class Literal : Expression() {

            data class Scalar(
                val value: Any?,
            ) : Literal()

            data class String(
                val literals: kotlin.collections.List<kotlin.String>,
                val arguments: kotlin.collections.List<Expression>,
            ): Literal()

            data class List(
                val elements: kotlin.collections.List<Expression>,
            ) : Literal()

            data class Object(
                val properties: Map<kotlin.String, Expression>,
            ) : Literal()

        }

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

        sealed class Access : Expression() {

            data class Variable(
                val name: String,
            ) : Access()

            data class Property(
                val receiver: Expression,
                val coalesce: Boolean,
                val name: String,
            ) : Access()

            data class Index(
                val receiver: Expression,
                val arguments: List<Expression>,
            ) : Access()

        }

        sealed class Invoke : Expression() {

            data class Function(
                val name: String,
                val arguments: List<Expression>,
            ) : Invoke()

            data class Method(
                val receiver: Expression,
                val coalesce: Boolean,
                val cascade: Boolean,
                val name: String,
                val arguments: List<Expression>,
            ) : Invoke()

            data class Pipeline(
                val receiver: Expression,
                val coalesce: Boolean,
                val cascade: Boolean,
                val qualifier: Access?,
                val name: String,
                val arguments: List<Expression>,
            ) : Invoke()

        }

        data class Lambda(
            val parameters: List<Pair<String, Type?>>,
            val body: Statement,
        ) : Expression()

        data class Macro(
            val name: String,
            val arguments: List<Expression>,
        ) : Expression()

        data class Dsl(
            val name: String,
            val ast: Any,
        ) : Expression()

        data class Interpolation(
            val expression: Expression,
        ) : Expression()

    }

    sealed class Pattern : RhovasAst() {

        data class Variable(
            val name: String,
        ) : Pattern()

        data class Value(
            val value: Expression,
        ) : Pattern()

        data class Predicate(
            val pattern: Pattern,
            val predicate: Expression,
        ) : Pattern()

        data class OrderedDestructure(
            val patterns: List<Pattern>
        ) : Pattern()

        data class NamedDestructure(
            val patterns: List<Pair<String?, Pattern>>
        ) : Pattern()

        data class TypedDestructure(
            val type: Type,
            val pattern: Pattern?,
        ) : Pattern()

        data class VarargDestructure(
            val pattern: Pattern?,
            val operator: String,
        ) : Pattern()

    }

    data class Type(
        val name: String,
        val generics: List<Type>?,
    ) : RhovasAst()

    data class Atom(val name: String)

    interface Visitor<T> {

        fun visit(ast: RhovasAst): T {
            return when (ast) {
                is Source -> visit(ast)

                is Component.Struct -> visit(ast)

                is Statement.Block -> visit(ast)
                is Statement.Component -> visit(ast)
                is Statement.Expression -> visit(ast)
                is Statement.Function -> visit(ast)
                is Statement.Declaration -> visit(ast)
                is Statement.Assignment -> visit(ast)
                is Statement.If -> visit(ast)
                is Statement.Match.Conditional -> visit(ast)
                is Statement.Match.Structural -> visit(ast)
                is Statement.For -> visit(ast)
                is Statement.While -> visit(ast)
                is Statement.Try -> visit(ast)
                is Statement.Try.Catch -> visit(ast)
                is Statement.With -> visit(ast)
                is Statement.Label -> visit(ast)
                is Statement.Break -> visit(ast)
                is Statement.Continue -> visit(ast)
                is Statement.Return -> visit(ast)
                is Statement.Throw -> visit(ast)
                is Statement.Assert -> visit(ast)
                is Statement.Ensure -> visit(ast)
                is Statement.Require -> visit(ast)

                is Expression.Literal.Scalar -> visit(ast)
                is Expression.Literal.String -> visit(ast)
                is Expression.Literal.List -> visit(ast)
                is Expression.Literal.Object -> visit(ast)
                is Expression.Group -> visit(ast)
                is Expression.Unary -> visit(ast)
                is Expression.Binary -> visit(ast)
                is Expression.Access.Variable -> visit(ast)
                is Expression.Access.Property -> visit(ast)
                is Expression.Access.Index -> visit(ast)
                is Expression.Invoke.Function -> visit(ast)
                is Expression.Invoke.Method -> visit(ast)
                is Expression.Invoke.Pipeline -> visit(ast)
                is Expression.Lambda -> visit(ast)
                is Expression.Macro -> visit(ast)
                is Expression.Dsl -> visit(ast)
                is Expression.Interpolation -> visit(ast)

                is Pattern.Variable -> visit(ast)
                is Pattern.Value -> visit(ast)
                is Pattern.Predicate -> visit(ast)
                is Pattern.OrderedDestructure -> visit(ast)
                is Pattern.NamedDestructure -> visit(ast)
                is Pattern.TypedDestructure -> visit(ast)
                is Pattern.VarargDestructure -> visit(ast)

                is Type -> visit(ast)
            }
        }

        fun visit(ast: Source): T

        fun visit(ast: Component.Struct): T

        fun visit(ast: Statement.Block): T
        fun visit(ast: Statement.Component): T
        fun visit(ast: Statement.Expression): T
        fun visit(ast: Statement.Function): T
        fun visit(ast: Statement.Declaration): T
        fun visit(ast: Statement.Assignment): T
        fun visit(ast: Statement.If): T
        fun visit(ast: Statement.Match.Conditional): T
        fun visit(ast: Statement.Match.Structural): T
        fun visit(ast: Statement.For): T
        fun visit(ast: Statement.While): T
        fun visit(ast: Statement.Try): T
        fun visit(ast: Statement.Try.Catch): T
        fun visit(ast: Statement.With): T
        fun visit(ast: Statement.Label): T
        fun visit(ast: Statement.Break): T
        fun visit(ast: Statement.Continue): T
        fun visit(ast: Statement.Return): T
        fun visit(ast: Statement.Throw): T
        fun visit(ast: Statement.Assert): T
        fun visit(ast: Statement.Require): T
        fun visit(ast: Statement.Ensure): T

        fun visit(ast: Expression.Literal.Scalar): T
        fun visit(ast: Expression.Literal.String): T
        fun visit(ast: Expression.Literal.List): T
        fun visit(ast: Expression.Literal.Object): T
        fun visit(ast: Expression.Group): T
        fun visit(ast: Expression.Unary): T
        fun visit(ast: Expression.Binary): T
        fun visit(ast: Expression.Access.Variable): T
        fun visit(ast: Expression.Access.Property): T
        fun visit(ast: Expression.Access.Index): T
        fun visit(ast: Expression.Invoke.Function): T
        fun visit(ast: Expression.Invoke.Method): T
        fun visit(ast: Expression.Invoke.Pipeline): T
        fun visit(ast: Expression.Lambda): T
        fun visit(ast: Expression.Macro): T
        fun visit(ast: Expression.Dsl): T
        fun visit(ast: Expression.Interpolation): T

        fun visit(ast: Pattern.Variable): T
        fun visit(ast: Pattern.Value): T
        fun visit(ast: Pattern.Predicate): T
        fun visit(ast: Pattern.OrderedDestructure): T
        fun visit(ast: Pattern.NamedDestructure): T
        fun visit(ast: Pattern.TypedDestructure): T
        fun visit(ast: Pattern.VarargDestructure): T

        fun visit(ast: Type): T

    }

}
