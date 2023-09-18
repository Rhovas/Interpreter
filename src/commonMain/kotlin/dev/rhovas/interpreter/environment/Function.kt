package dev.rhovas.interpreter.environment

sealed interface Function {

    val name: String
    val modifiers: Modifiers
    val generics: List<Type.Generic>
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

    fun bind(generics: Map<String, Type>): Function

    /**
     * Returns true if this function is disjoint with [other], aka there is no
     * overlap between function signatures.
     */
    fun isDisjointWith(other: Function): Boolean {
        val function = bind(generics.associate { Pair(it.name, it.bound) })
        val other = other.bind(other.generics.associate { Pair(it.name, it.bound) })
        return (
            function.name != other.name ||
            function.parameters.size != other.parameters.size ||
            function.parameters.zip(other.parameters).any { !it.first.type.isSubtypeOf(it.second.type) && !it.first.type.isSupertypeOf(it.second.type) }
        )
    }

    data class Declaration(
        override val name: String,
        override val modifiers: Modifiers = Modifiers(),
        override val generics: List<Type.Generic> = listOf(),
        override val parameters: List<Variable.Declaration>,
        override val returns: Type,
        override val throws: List<Type> = listOf(),
    ) : Function {

        override fun bind(generics: Map<String, Type>): Declaration {
            return Declaration(
                name,
                modifiers,
                this.generics.map { Type.Generic(it.name, it.bound.bind(generics)) },
                parameters.map { Variable.Declaration(it.name, it.type.bind(generics), it.mutable) },
                returns.bind(generics),
                throws.map { it.bind(generics) }
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
            return implementation!!.invoke(arguments)
        }

        override fun bind(generics: Map<String, Type>): Definition {
            return Definition(declaration.bind(generics)) { implementation!!.invoke(it) }
        }

    }

}
