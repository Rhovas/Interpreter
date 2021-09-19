package dev.rhovas.interpreter.environment

class Type(
    private val scope: Scope,
) {

    companion object {

        val VOID = Type(Scope(null)) //TODO: Void methods

    }

    //TODO: val fields by scope::variables
    val methods by scope::functions

}
