package dev.rhovas.interpreter.environment

data class Variable(
    val name: String,
    val type: Type,
    private var value: Object,
) {

    fun get(): Object {
        return value
    }

    fun set(value: Object) {
        this.value = value
    }

}