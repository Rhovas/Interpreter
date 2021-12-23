package dev.rhovas.interpreter.environment

data class Function(
    val name: String,
    val parameters: List<Pair<String, Type>>,
    val returns: Type,
) {

    lateinit var implementation: (List<Object>) -> Object

    fun invoke(arguments: List<Object>): Object {
        return implementation(arguments)
    }

}
