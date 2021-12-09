package dev.rhovas.interpreter.environment

data class Property(
    val getter: Method,
    val setter: Method?,
) {

    fun get(receiver: Object): Object {
        return getter.invoke(receiver, listOf())
    }

    fun set(receiver: Object, value: Object) {
        //TODO: Handle immutable properties
        setter!!.invoke(receiver, listOf(value))
    }

}
