package dev.rhovas.interpreter.analyzer.rhovas

sealed class RhovasIr {

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
            val function: dev.rhovas.interpreter.environment.Function,
            val body: Block,
        ) : Statement()

        data class Declaration(
            val variable: dev.rhovas.interpreter.environment.Variable,
            val value: RhovasIr.Expression?,
        ) : Statement()

        sealed class Assignment: Statement() {

            data class Variable(
                val variable: dev.rhovas.interpreter.environment.Variable,
                val value: RhovasIr.Expression,
            ) : Assignment()

            data class Property(
                val receiver: RhovasIr.Expression,
                val setter: dev.rhovas.interpreter.environment.Method,
                val value: RhovasIr.Expression,
            ) : Assignment()

            data class Index(
                val method: dev.rhovas.interpreter.environment.Method,
                val receiver: RhovasIr.Expression,
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

        data class Literal(
            val value: Any?,
            override val type: dev.rhovas.interpreter.environment.Type
        ): Expression(type)

        data class Group(
            val expression: Expression,
        ): Expression(expression.type)

        data class Unary(
            val operator: String,
            val expression: Expression,
            val method: dev.rhovas.interpreter.environment.Method,
        ): Expression(method.returns)

        data class Binary(
            val operator: String,
            val left: Expression,
            val right: Expression,
            val method: dev.rhovas.interpreter.environment.Method,
        ): Expression(method.returns)

        sealed class Access(
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type) {

            data class Variable(
                val variable: dev.rhovas.interpreter.environment.Variable,
            ) : Access(variable.type)

            data class Property(
                val receiver: Expression,
                val method: dev.rhovas.interpreter.environment.Method,
                val coalesce: Boolean,
            ) : Access(method.returns)

            data class Index(
                val receiver: Expression,
                val method: dev.rhovas.interpreter.environment.Method,
                val arguments: List<Expression>,
            ) : Access(method.returns)

        }

        sealed class Invoke(
            override val type: dev.rhovas.interpreter.environment.Type,
        ) : Expression(type) {

            data class Function(
                val function: dev.rhovas.interpreter.environment.Function,
                val arguments: List<Expression>,
            ) : Invoke(function.returns)

            data class Method(
                val receiver: Expression,
                val method: dev.rhovas.interpreter.environment.Method,
                val coalesce: Boolean,
                val cascade: Boolean,
                val arguments: List<Expression>,
            ) : Invoke(if (cascade) receiver.type else method.returns)

            data class Pipeline(
                val receiver: Expression,
                val qualifier: Access?,
                val function: dev.rhovas.interpreter.environment.Function,
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
            val variable: dev.rhovas.interpreter.environment.Variable,
        ) : Pattern(variable.type)

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

}
