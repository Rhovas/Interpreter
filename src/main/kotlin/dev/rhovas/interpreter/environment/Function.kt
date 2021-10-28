package dev.rhovas.interpreter.environment

data class Function(
    val name: String,
    val parameters: List<Type>,
    val returns: Type,
    private val function: (List<Object>) -> Object,
) {

    fun invoke(arguments: List<Object>): Object {
        return function(arguments)
    }

}
