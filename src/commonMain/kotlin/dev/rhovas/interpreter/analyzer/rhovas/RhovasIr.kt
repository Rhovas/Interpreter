package dev.rhovas.interpreter.analyzer.rhovas

import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
import kotlin.js.JsName

sealed class RhovasIr {

    var context: List<Input.Range> = listOf()
        internal set

    data class Source(
        val imports: List<Import>,
        val statements: List<Statement>,
    ) : RhovasIr()

    data class Import(
        val type: dev.rhovas.interpreter.environment.Type,
    ) : RhovasIr()

    sealed class Component : RhovasIr() {

        data class Struct(
            val type: dev.rhovas.interpreter.environment.Type,
            val fields: List<Statement.Declaration.Variable>,
        ) : Component()

    }

    sealed class Statement : RhovasIr() {

        data class Component(
            val component: RhovasIr.Component,
        ) : Statement()

        data class Expression(
            val expression: RhovasIr.Expression,
        ) : Statement()

        sealed class Declaration : Statement() {

            data class Variable(
                val variable: dev.rhovas.interpreter.environment.Variable,
                val value: RhovasIr.Expression?,
            ) : Statement()

            data class Function(
                val function: dev.rhovas.interpreter.environment.Function,
                val block: RhovasIr.Expression.Block,
            ) : Statement()

        }

        sealed class Assignment : Statement() {

            data class Variable(
                val variable: dev.rhovas.interpreter.environment.Variable,
                val value: RhovasIr.Expression,
            ) : Assignment()

            data class Property(
                val receiver: RhovasIr.Expression,
                val property: dev.rhovas.interpreter.environment.Property,
                val value: RhovasIr.Expression,
            ) : Assignment()

            data class Index(
                val receiver: RhovasIr.Expression,
                val method: dev.rhovas.interpreter.environment.Method,
                val arguments: List<RhovasIr.Expression>,
                val value: RhovasIr.Expression,
            ) : Assignment()

        }

        data class If(
            val condition: RhovasIr.Expression,
            val thenBlock: RhovasIr.Expression.Block,
            val elseBlock: RhovasIr.Expression.Block?,
        ) : Statement()

        sealed class Match : Statement() {

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
            val variable: dev.rhovas.interpreter.environment.Variable.Declaration,
            val argument: RhovasIr.Expression,
            val block: RhovasIr.Expression.Block,
        ) : Statement()

        data class While(
            val condition: RhovasIr.Expression,
            val block: RhovasIr.Expression.Block,
        ) : Statement()

        data class Try(
            val tryBlock: RhovasIr.Expression.Block,
            val catchBlocks: List<Catch>,
            val finallyBlock: RhovasIr.Expression.Block?
        ) : Statement() {

            data class Catch(
                val variable: dev.rhovas.interpreter.environment.Variable.Declaration,
                val block: Expression.Block,
            ) : RhovasIr()

        }

        data class With(
            val variable: dev.rhovas.interpreter.environment.Variable.Declaration?,
            val argument: RhovasIr.Expression,
            val block: RhovasIr.Expression.Block,
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
    ) : RhovasIr() {

        data class Block(
            val statements: List<Statement>,
            val expression: Expression?,
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type)

        sealed class Literal(
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type) {

            data class Scalar(
                val value: Any?,
                override val type: dev.rhovas.interpreter.environment.Type,
            ) : Literal(type)

            data class String(
                val literals: kotlin.collections.List<kotlin.String>,
                val arguments: kotlin.collections.List<Expression>,
                override val type: dev.rhovas.interpreter.environment.Type,
            ) : Literal(type)

            data class List(
                val elements: kotlin.collections.List<Expression>,
                override val type: dev.rhovas.interpreter.environment.Type,
            ) : Literal(type)

            data class Object(
                val properties: Map<kotlin.String, Expression>,
                override val type: dev.rhovas.interpreter.environment.Type,
            ) : Literal(type)

            data class Type(
                val literal: dev.rhovas.interpreter.environment.Type,
                override val type: dev.rhovas.interpreter.environment.Type,
            ) : Literal(type)

        }

        data class Group(
            val expression: Expression,
        ) : Expression(expression.type)

        data class Unary(
            val operator: String,
            val expression: Expression,
            val method: dev.rhovas.interpreter.environment.Method,
        ) : Expression(method.returns)

        data class Binary(
            val operator: String,
            val left: Expression,
            val right: Expression,
            val method: dev.rhovas.interpreter.environment.Method?,
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type)

        sealed class Access(
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type) {

            data class Variable(
                val qualifier: Type?,
                val variable: dev.rhovas.interpreter.environment.Variable,
            ) : Access(variable.type)

            data class Property(
                val receiver: Expression,
                val property: dev.rhovas.interpreter.environment.Property,
                val coalesce: Boolean,
                override val type: dev.rhovas.interpreter.environment.Type,
            ) : Access(type)

            data class Index(
                val receiver: Expression,
                val method: dev.rhovas.interpreter.environment.Method,
                val arguments: List<Expression>,
            ) : Access(method.returns)

        }

        sealed class Invoke(
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type) {

            data class Constructor(
                val qualifier: dev.rhovas.interpreter.environment.Type.Reference,
                val function: dev.rhovas.interpreter.environment.Function,
                val arguments: List<Expression>,
            ) : Invoke(function.returns)

            data class Function(
                val qualifier: Type?,
                val function: dev.rhovas.interpreter.environment.Function,
                val arguments: List<Expression>,
            ) : Invoke(function.returns)

            data class Method(
                val receiver: Expression,
                val method: dev.rhovas.interpreter.environment.Method,
                val coalesce: Boolean,
                val cascade: Boolean,
                val arguments: List<Expression>,
                override val type: dev.rhovas.interpreter.environment.Type,
            ) : Invoke(type)

            data class Pipeline(
                val receiver: Expression,
                val qualifier: Type?,
                val function: dev.rhovas.interpreter.environment.Function,
                val coalesce: Boolean,
                val cascade: Boolean,
                val arguments: List<Expression>,
                override val type: dev.rhovas.interpreter.environment.Type,
            ) : Invoke(type)

        }

        data class Lambda(
            val parameters: List<dev.rhovas.interpreter.environment.Variable.Declaration>,
            val body: Block,
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type)

    }

    sealed class Pattern(
        open val type: dev.rhovas.interpreter.environment.Type,
    ) : RhovasIr() {

        data class Variable(
            val variable: dev.rhovas.interpreter.environment.Variable.Declaration?,
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
            val patterns: List<Pair<String?, Pattern>>,
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Pattern(type)

        data class TypedDestructure(
            override val type: dev.rhovas.interpreter.environment.Type,
            val pattern: Pattern?,
        ) : Pattern(type)

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
                is Import -> visit(ir)

                is Component.Struct -> visit(ir)

                is Statement.Component -> visit(ir)
                is Statement.Expression -> visit(ir)
                is Statement.Declaration.Variable -> visit(ir)
                is Statement.Declaration.Function -> visit(ir)
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

                is Expression.Block -> visit(ir)
                is Expression.Literal.Scalar -> visit(ir)
                is Expression.Literal.String -> visit(ir)
                is Expression.Literal.List -> visit(ir)
                is Expression.Literal.Object -> visit(ir)
                is Expression.Literal.Type -> visit(ir)
                is Expression.Group -> visit(ir)
                is Expression.Unary -> visit(ir)
                is Expression.Binary -> visit(ir)
                is Expression.Access.Variable -> visit(ir)
                is Expression.Access.Property -> visit(ir)
                is Expression.Access.Index -> visit(ir)
                is Expression.Invoke.Constructor -> visit(ir)
                is Expression.Invoke.Function -> visit(ir)
                is Expression.Invoke.Method -> visit(ir)
                is Expression.Invoke.Pipeline -> visit(ir)
                is Expression.Lambda -> visit(ir)

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
        fun visit(ir: Import): T

        fun visit(ir: Component.Struct): T

        fun visit(ir: Statement.Component): T
        fun visit(ir: Statement.Expression): T
        @JsName("visitDeclarationVariable") fun visit(ir: Statement.Declaration.Variable): T
        @JsName("visitDeclarationFunction") fun visit(ir: Statement.Declaration.Function): T
        @JsName("visitAssignmentVariable") fun visit(ir: Statement.Assignment.Variable): T
        @JsName("visitAssignmentProperty") fun visit(ir: Statement.Assignment.Property): T
        @JsName("visitAssignmentIndex") fun visit(ir: Statement.Assignment.Index): T
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

        fun visit(ir: Expression.Block): T
        fun visit(ir: Expression.Literal.Scalar): T
        fun visit(ir: Expression.Literal.String): T
        fun visit(ir: Expression.Literal.List): T
        fun visit(ir: Expression.Literal.Object): T
        @JsName("visitLiteralType") fun visit(ir: Expression.Literal.Type): T
        fun visit(ir: Expression.Group): T
        fun visit(ir: Expression.Unary): T
        fun visit(ir: Expression.Binary): T
        @JsName("visitAccessVariable") fun visit(ir: Expression.Access.Variable): T
        @JsName("visitAccessProperty") fun visit(ir: Expression.Access.Property): T
        @JsName("visitAccessIndex") fun visit(ir: Expression.Access.Index): T
        fun visit(ir: Expression.Invoke.Constructor): T
        @JsName("visitInvokeFunction") fun visit(ir: Expression.Invoke.Function): T
        fun visit(ir: Expression.Invoke.Method): T
        fun visit(ir: Expression.Invoke.Pipeline): T
        fun visit(ir: Expression.Lambda): T

        @JsName("visitPatternVariable") fun visit(ir: Pattern.Variable): T
        fun visit(ir: Pattern.Value): T
        fun visit(ir: Pattern.Predicate): T
        fun visit(ir: Pattern.OrderedDestructure): T
        fun visit(ir: Pattern.NamedDestructure): T
        fun visit(ir: Pattern.TypedDestructure): T
        fun visit(ir: Pattern.VarargDestructure): T

        @JsName("visitType") fun visit(ir: Type): T

    }

}
