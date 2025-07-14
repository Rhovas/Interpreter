package dev.rhovas.interpreter.environment.type

sealed class Bindings {

    open val type: MutableMap<String, Type>? = null
    open val other: MutableMap<String, Type>? = null

    object None : Bindings()

    data class Subtype(override val type: MutableMap<String, Type>) : Bindings()

    data class Supertype(override val other: MutableMap<String, Type>) : Bindings()

    //TODO: Stub to match existing behavior (invalid).
    data class Both(override val type: MutableMap<String, Type>, override val other: MutableMap<String, Type>) : Bindings()

}
