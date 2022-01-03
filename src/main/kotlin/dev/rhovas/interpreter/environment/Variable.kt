package dev.rhovas.interpreter.environment

sealed class Variable(
    open val name: String,
    open val type: Type,
) {

    data class Local(
        override val name: String,
        override val type: Type,
    ) : Variable(name, type) {

        data class Runtime(
            val variable: Local,
            var value: Object,
        ) : Variable(variable.name, variable.type)

    }

    data class Property(
        val getter: Function.Method,
        val setter: Function.Method?,
    ) : Variable(getter.name, getter.returns) {

        data class Bound(
            val getter: Function.Method.Bound,
            val setter: Function.Method.Bound?,
        ) : Variable(getter.name, getter.returns)

    }

}
