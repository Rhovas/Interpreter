package dev.rhovas.interpreter.environment.type

sealed class Bindings {

    open val type: MutableMap<String, Type>? = null
    open val other: MutableMap<String, Type>? = null

    object None : Bindings()

    data class Subtype(override val type: MutableMap<String, Type>) : Bindings()

    data class Supertype(override val other: MutableMap<String, Type>) : Bindings()

    //TODO: Stub to match existing behavior (invalid).
    data class Both(override val type: MutableMap<String, Type>, override val other: MutableMap<String, Type>) : Bindings()

    fun finalize(): MutableMap<String, Type> {
        val bindings = type ?: other ?: mutableMapOf()
        bindings.mapValuesTo(bindings) { (_, type) -> when (type) {
            is Type.Variant -> type.lower ?: type.upper ?: Type.ANY
            else -> type
        } }
        return bindings
    }

}
