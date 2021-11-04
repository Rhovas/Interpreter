package dev.rhovas.interpreter.environment

data class Type(
    val name: String,
    private val scope: Scope,
) {

    //TODO: val fields by scope::variables
    val methods by scope::functions

    override fun toString(): String {
        return "Type(name='$name', scope=Scope@${scope.hashCode().toString(16)})"
    }

}
