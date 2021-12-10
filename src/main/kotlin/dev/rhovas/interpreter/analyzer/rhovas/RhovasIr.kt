package dev.rhovas.interpreter.analyzer.rhovas

sealed class RhovasIr {

    sealed class Statement: RhovasIr() //TODO

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

    data class Type(
        val type: dev.rhovas.interpreter.environment.Type
    ) : RhovasIr()

}
