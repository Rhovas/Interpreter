package dev.rhovas.interpreter.environment

data class Property(
    val receiver: Object,
    val getter: Function,
    val setter: Function?,
) {

    fun get(): Object {
        return getter.invoke(listOf(receiver))
    }

    fun set(value: Object) {
        //TODO: Handle immutable properties
        setter?.invoke(listOf(receiver, value))
    }

}
