package dev.rhovas.interpreter.environment

sealed interface Function {

    val name: String
    val modifiers: Modifiers
    val generics: LinkedHashMap<String, Type.Generic>
    val parameters: List<Variable.Declaration>
    val returns: Type
    val throws: List<Type>

    /**
     * Returns true if this function is resolved by [arguments], thus it can be
     * invoked by arguments of that type. Bounds on generic types are stored in
     * the given [generics] map for later use (such as with [bind]) and should
     * only be considered meaningful if this method returns true.
     */
    fun isResolvedBy(arguments: List<Type>, generics: MutableMap<String, Type> = mutableMapOf()): Boolean {
        val result = arguments.indices.all {
            arguments[it].isSubtypeOf(parameters[it].type, generics)
        }
        generics.mapValuesTo(generics) { (_, type) ->
            // Finalize variant type bounds between arguments after resolution is complete.
            if (type is Type.Variant) type.upper ?: type.lower ?: Type.ANY else type
        }
        return result
    }

    fun bind(bindings: Map<String, Type>): Function

    /**
     * Returns true if this function is disjoint with [other], aka there is no
     * overlap between function signatures.
     */
    fun isDisjointWith(other: Function): Boolean {
        return (
            name != other.name ||
            parameters.size != other.parameters.size ||
            parameters.zip(other.parameters).any {
                val type = it.first.type.bind(generics)
                val other = it.second.type.bind(other.generics)
                !type.isSubtypeOf(other) && !type.isSupertypeOf(other)
            }
        )
    }

    data class Declaration(
        override val name: String,
        override val modifiers: Modifiers = Modifiers(),
        override val generics: LinkedHashMap<String, Type.Generic> = linkedMapOf(),
        override val parameters: List<Variable.Declaration>,
        override val returns: Type,
        override val throws: List<Type> = listOf(),
    ) : Function {

        override fun bind(bindings: Map<String, Type>): Declaration {
            return Declaration(
                name,
                modifiers,
                generics.mapValuesTo(linkedMapOf()) { Type.Generic(it.key, it.value.bound.bind(bindings)) },
                parameters.map { Variable.Declaration(it.name, it.type.bind(bindings), it.mutable) },
                returns.bind(bindings),
                throws.map { it.bind(bindings) }
            )
        }

    }

    data class Definition(
        val declaration: Declaration,
    ) : Function by declaration {

        lateinit var implementation: (List<Object>) -> Object

        constructor(declaration: Declaration, implementation: (List<Object>) -> Object): this(declaration) {
            this.implementation = implementation
        }

        fun invoke(arguments: List<Object>): Object {
            return implementation.invoke(arguments)
        }

        override fun bind(bindings: Map<String, Type>): Definition {
            return Definition(declaration.bind(bindings)) { implementation.invoke(it) }
        }

    }

}
