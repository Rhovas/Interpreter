package dev.rhovas.interpreter.environment

data class Method(
    val function: Function,
) {

    val name get() = function.name
    val parameters get() = function.parameters.subList(1, function.parameters.size)
    val returns get() = function.returns

    fun invoke(receiver: Object, arguments: List<Object>): Object {
        return function.invoke(listOf(receiver) + arguments)
    }

}
