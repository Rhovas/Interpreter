package dev.rhovas.interpreter.environment

sealed interface Method {

    val function: Function

    val name get() = function.name
    val generics get() = function.generics
    val receiver get() = function.parameters.first()
    val parameters get() = function.parameters.drop(1)
    val returns get() = function.returns
    val throws get() = function.throws

    data class Declaration(
        override val function: Function
    ) : Method

    data class Bound(
        override val function: Function.Definition,
        val instance: Object,
    ) : Method {

        fun invoke(arguments: List<Object>): Object {
            return function.invoke(listOf(instance) + arguments)
        }

    }

}
