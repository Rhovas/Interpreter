package dev.rhovas.interpreter.environment

sealed class Function(
    open val name: String,
    open val parameters: List<Pair<String, Type>>,
    open val returns: Type,
) {

    data class Definition(
        override val name: String,
        override val parameters: List<Pair<String, Type>>,
        override val returns: Type,
    ) : Function(name, parameters, returns) {

        lateinit var implementation: (List<Object>) -> Object

        fun invoke(arguments: List<Object>): Object {
            return implementation.invoke(arguments)
        }

    }

    data class Method(
        override val name: String,
        override val parameters: List<Pair<String, Type>>,
        override val returns: Type,
    ) : Function(name, parameters, returns) {

        data class Bound(
            val receiver: Object,
            val function: Definition,
        ) : Function(function.name, function.parameters.subList(1, function.parameters.size), function.returns) {

            fun invoke(arguments: List<Object>): Object {
                return function.invoke(listOf(receiver) + arguments)
            }

        }

    }

}
