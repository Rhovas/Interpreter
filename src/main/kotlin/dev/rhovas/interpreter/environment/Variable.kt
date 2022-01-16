package dev.rhovas.interpreter.environment

sealed class Variable(
    open val name: String,
    open val type: Type,
    open val mutable: Boolean,
) {

    data class Local(
        override val name: String,
        override val type: Type,
        override val mutable: Boolean,
    ) : Variable(name, type, mutable) {

        data class Runtime(
            val variable: Local,
            var value: Object,
        ) : Variable(variable.name, variable.type, variable.mutable)

    }

    data class Property(
        val getter: Function.Method,
        val setter: Function.Method?,
    ) : Variable(getter.name, getter.returns, setter != null) {

        data class Bound(
            val getter: Function.Method.Bound,
            val setter: Function.Method.Bound?,
        ) : Variable(getter.name, getter.returns, setter != null)

    }

}
