package dev.rhovas.interpreter.analyzer.rhovas

import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

sealed class RhovasIr {

    var context: List<Input.Range>? = null
        internal set

    data class Source(
        val statements: List<Statement>,
    ) : RhovasIr()

    sealed class Statement: RhovasIr() {

        data class Block(
            val statements: List<Statement>,
        ) : Statement()

        data class Expression(
            val expression: RhovasIr.Expression.Invoke,
        ) : Statement()

        data class Function(
            val function: dev.rhovas.interpreter.environment.Function.Definition,
            val body: Block,
        ) : Statement()

        data class Declaration(
            val variable: dev.rhovas.interpreter.environment.Variable.Local,
            val value: RhovasIr.Expression?,
        ) : Statement()

        sealed class Assignment: Statement() {

            data class Variable(
                val variable: dev.rhovas.interpreter.environment.Variable.Local,
                val value: RhovasIr.Expression,
            ) : Assignment()

            data class Property(
                val receiver: RhovasIr.Expression,
                val property: dev.rhovas.interpreter.environment.Variable.Property,
                val value: RhovasIr.Expression,
            ) : Assignment()

            data class Index(
                val receiver: RhovasIr.Expression,
                val method: dev.rhovas.interpreter.environment.Function.Method,
                val arguments: List<RhovasIr.Expression>,
                val value: RhovasIr.Expression,
            ) : Assignment()

        }

        data class If(
            val condition: RhovasIr.Expression,
            val thenStatement: Statement,
            val elseStatement: Statement?,
        ) : Statement()

        sealed class Match: Statement() {

            data class Conditional(
                val cases: List<Pair<RhovasIr.Expression, Statement>>,
                val elseCase: Pair<RhovasIr.Expression?, Statement>?,
            ) : Match()

            data class Structural(
                val argument: RhovasIr.Expression,
                val cases: List<Pair<Pattern, Statement>>,
                val elseCase: Pair<Pattern?, Statement>?
            ) : Match()

        }

        data class For(
            val name: String,
            val iterable: RhovasIr.Expression,
            val body: Statement,
        ) : Statement()

        data class While(
            val condition: RhovasIr.Expression,
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
            ) : RhovasIr()

        }

        data class With(
            val name: String?,
            val argument: RhovasIr.Expression,
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
            val value: RhovasIr.Expression?,
        ) : Statement()

        data class Throw(
            val exception: RhovasIr.Expression,
        ) : Statement()

        data class Assert(
            val condition: RhovasIr.Expression,
            val message: RhovasIr.Expression?,
        ) : Statement()

        data class Require(
            val condition: RhovasIr.Expression,
            val message: RhovasIr.Expression?,
        ) : Statement()

        data class Ensure(
            val condition: RhovasIr.Expression,
            val message: RhovasIr.Expression?,
        ) : Statement()

    }

    sealed class Expression(
        open val type: dev.rhovas.interpreter.environment.Type,
    ): RhovasIr() {

        sealed class Literal(
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type) {

            data class Scalar(
                val value: Any?,
                override val type: dev.rhovas.interpreter.environment.Type,
            ) : Literal(type)

            data class List(
                val elements: kotlin.collections.List<Expression>,
                override val type: dev.rhovas.interpreter.environment.Type,
            ) : Literal(type)

            data class Object(
                val properties: Map<String, Expression>,
                override val type: dev.rhovas.interpreter.environment.Type,
            ) : Literal(type)

        }

        data class Group(
            val expression: Expression,
        ): Expression(expression.type)

        data class Unary(
            val operator: String,
            val expression: Expression,
            val method: dev.rhovas.interpreter.environment.Function.Method,
        ): Expression(method.returns)

        data class Binary(
            val operator: String,
            val left: Expression,
            val right: Expression,
            override val type: dev.rhovas.interpreter.environment.Type,
        ): Expression(type)

        sealed class Access(
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type) {

            data class Variable(
                val variable: dev.rhovas.interpreter.environment.Variable.Local,
            ) : Access(variable.type)

            data class Property(
                val receiver: Expression,
                val property: dev.rhovas.interpreter.environment.Variable.Property,
                val coalesce: Boolean,
            ) : Access(property.type)

            data class Index(
                val receiver: Expression,
                val method: dev.rhovas.interpreter.environment.Function.Method,
                val arguments: List<Expression>,
            ) : Access(method.returns)

        }

        sealed class Invoke(
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type) {

            data class Function(
                val function: dev.rhovas.interpreter.environment.Function.Definition,
                val arguments: List<Expression>,
            ) : Invoke(function.returns)

            data class Method(
                val receiver: Expression,
                val method: dev.rhovas.interpreter.environment.Function.Method,
                val coalesce: Boolean,
                val cascade: Boolean,
                val arguments: List<Expression>,
            ) : Invoke(if (cascade) receiver.type else method.returns)

            data class Pipeline(
                val receiver: Expression,
                //TODO: Track function namespace (access) for generator
                val function: dev.rhovas.interpreter.environment.Function.Definition,
                val coalesce: Boolean,
                val cascade: Boolean,
                val arguments: List<Expression>,
            ) : Invoke(if (cascade) receiver.type else function.returns)

        }

        data class Lambda(
            val parameters: List<Pair<String, Type?>>,
            val body: Statement,
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type)

        data class Macro(
            val name: String,
            val arguments: List<Expression>,
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type)

        data class Dsl(
            val name: String,
            val ast: Any,
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type)

        data class Interpolation(
            val expression: Expression,
        ) : Expression(expression.type)

    }

    sealed class Pattern(
        open val type: dev.rhovas.interpreter.environment.Type,
    ) : RhovasIr() {

        data class Variable(
            val variable: dev.rhovas.interpreter.environment.Variable.Local?,
        ) : Pattern(variable?.type ?: Library.TYPES["Void"]!!)

        data class Value(
            val value: Expression,
        ) : Pattern(value.type)

        data class Predicate(
            val pattern: Pattern,
            val predicate: Expression,
        ) : Pattern(pattern.type)

        data class OrderedDestructure(
            val patterns: List<Pattern>,
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Pattern(type)

        data class NamedDestructure(
            val patterns: List<Pair<String, Pattern?>>,
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Pattern(type)

        data class TypedDestructure(
            override val type: dev.rhovas.interpreter.environment.Type,
            val pattern: Pattern?,
        ): Pattern(type)

        data class VarargDestructure(
            val pattern: Pattern?,
            val operator: String,
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Pattern(type)

    }

    data class Type(
        val type: dev.rhovas.interpreter.environment.Type
    ) : RhovasIr()

    interface Visitor<T> {

        fun visit(ir: RhovasIr): T {
            return when (ir) {
                is Source -> visit(ir)

                is Statement.Block -> visit(ir)
                is Statement.Expression -> visit(ir)
                is Statement.Function -> visit(ir)
                is Statement.Declaration -> visit(ir)
                is Statement.Assignment.Variable -> visit(ir)
                is Statement.Assignment.Property -> visit(ir)
                is Statement.Assignment.Index -> visit(ir)
                is Statement.If -> visit(ir)
                is Statement.Match.Conditional -> visit(ir)
                is Statement.Match.Structural -> visit(ir)
                is Statement.For -> visit(ir)
                is Statement.While -> visit(ir)
                is Statement.Try -> visit(ir)
                is Statement.Try.Catch -> visit(ir)
                is Statement.With -> visit(ir)
                is Statement.Label -> visit(ir)
                is Statement.Break -> visit(ir)
                is Statement.Continue -> visit(ir)
                is Statement.Return -> visit(ir)
                is Statement.Throw -> visit(ir)
                is Statement.Assert -> visit(ir)
                is Statement.Ensure -> visit(ir)
                is Statement.Require -> visit(ir)

                is Expression.Literal.Scalar -> visit(ir)
                is Expression.Literal.List -> visit(ir)
                is Expression.Literal.Object -> visit(ir)
                is Expression.Group -> visit(ir)
                is Expression.Unary -> visit(ir)
                is Expression.Binary -> visit(ir)
                is Expression.Access.Variable -> visit(ir)
                is Expression.Access.Property -> visit(ir)
                is Expression.Access.Index -> visit(ir)
                is Expression.Invoke.Function -> visit(ir)
                is Expression.Invoke.Method -> visit(ir)
                is Expression.Invoke.Pipeline -> visit(ir)
                is Expression.Lambda -> visit(ir)
                is Expression.Macro -> visit(ir)
                is Expression.Dsl -> visit(ir)
                is Expression.Interpolation -> visit(ir)

                is Pattern.Variable -> visit(ir)
                is Pattern.Value -> visit(ir)
                is Pattern.Predicate -> visit(ir)
                is Pattern.OrderedDestructure -> visit(ir)
                is Pattern.NamedDestructure -> visit(ir)
                is Pattern.TypedDestructure -> visit(ir)
                is Pattern.VarargDestructure -> visit(ir)

                is Type -> visit(ir)
            }
        }

        fun visit(ir: Source): T

        fun visit(ir: Statement.Block): T
        fun visit(ir: Statement.Expression): T
        fun visit(ir: Statement.Function): T
        fun visit(ir: Statement.Declaration): T
        fun visit(ir: Statement.Assignment.Variable): T
        fun visit(ir: Statement.Assignment.Property): T
        fun visit(ir: Statement.Assignment.Index): T
        fun visit(ir: Statement.If): T
        fun visit(ir: Statement.Match.Conditional): T
        fun visit(ir: Statement.Match.Structural): T
        fun visit(ir: Statement.For): T
        fun visit(ir: Statement.While): T
        fun visit(ir: Statement.Try): T
        fun visit(ir: Statement.Try.Catch): T
        fun visit(ir: Statement.With): T
        fun visit(ir: Statement.Label): T
        fun visit(ir: Statement.Break): T
        fun visit(ir: Statement.Continue): T
        fun visit(ir: Statement.Return): T
        fun visit(ir: Statement.Throw): T
        fun visit(ir: Statement.Assert): T
        fun visit(ir: Statement.Require): T
        fun visit(ir: Statement.Ensure): T

        fun visit(ir: Expression.Literal.Scalar): T
        fun visit(ir: Expression.Literal.List): T
        fun visit(ir: Expression.Literal.Object): T
        fun visit(ir: Expression.Group): T
        fun visit(ir: Expression.Unary): T
        fun visit(ir: Expression.Binary): T
        fun visit(ir: Expression.Access.Variable): T
        fun visit(ir: Expression.Access.Property): T
        fun visit(ir: Expression.Access.Index): T
        fun visit(ir: Expression.Invoke.Function): T
        fun visit(ir: Expression.Invoke.Method): T
        fun visit(ir: Expression.Invoke.Pipeline): T
        fun visit(ir: Expression.Lambda): T
        fun visit(ir: Expression.Macro): T
        fun visit(ir: Expression.Dsl): T
        fun visit(ir: Expression.Interpolation): T

        fun visit(ir: Pattern.Variable): T
        fun visit(ir: Pattern.Value): T
        fun visit(ir: Pattern.Predicate): T
        fun visit(ir: Pattern.OrderedDestructure): T
        fun visit(ir: Pattern.NamedDestructure): T
        fun visit(ir: Pattern.TypedDestructure): T
        fun visit(ir: Pattern.VarargDestructure): T

        fun visit(ir: Type): T

    }

}
