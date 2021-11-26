package dev.rhovas.interpreter.environment

data class Method(
    val receiver: Object,
    val function: Function,
) {

    fun invoke(arguments: List<Object>): Object {
        return function.invoke(listOf(receiver) + arguments)
    }

}
