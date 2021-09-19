package dev.rhovas.interpreter.environment

data class Method(
    val name: String,
    val arity: Int,
    private val receiver: Object,
    private val function: Function,
) {

    fun invoke(arguments: List<Object>): Object {
        return function.invoke(listOf(receiver) + arguments)
    }

}
