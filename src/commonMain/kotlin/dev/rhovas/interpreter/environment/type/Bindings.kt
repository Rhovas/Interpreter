package dev.rhovas.interpreter.environment.type

sealed class Bindings {

    open val type: MutableMap<String, Type>? = null
    open val other: MutableMap<String, Type>? = null

    object None : Bindings()

    data class Subtype(override val type: MutableMap<String, Type>) : Bindings()

    data class Supertype(override val other: MutableMap<String, Type>) : Bindings()

    /**
     * Returns a map of refined bindings, which transforms unrefined variant
     * bindings into the most-specific concrete type that is compatible, namely:
     *  - * -> Any
     *  - * : Number -> Number
     *  - Number : * -> Number
     *  - Number : Any -> Number
     * The original bindings are left unchanged for future subtype checks.
     */
    fun refined(): Map<String, Type> {
        val bindings = type ?: other ?: mapOf()
        return bindings.mapValues { when (val type = it.value) {
            is Type.Variant -> type.lower ?: type.upper ?: Type.ANY
            else -> type
        } }
    }

}
