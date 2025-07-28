package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.environment.type.Bindings
import dev.rhovas.interpreter.environment.type.Type

sealed interface Function {

    val declaration: Declaration

    val name get() = declaration.name
    val modifiers get() = declaration.modifiers
    val generics get() = declaration.generics
    val parameters get() = declaration.parameters
    val returns get() = declaration.returns
    val throws get() = declaration.throws

    /**
     * Returns true if this function is resolved by [arguments], thus it can be
     * invoked by arguments of that type. Bounds on generic types are stored in
     * the given [generics] map for later use (such as with [bind]) and should
     * only be considered meaningful if this method returns true.
     */
    fun isResolvedBy(arguments: List<Type>, generics: MutableMap<String, Type> = mutableMapOf()): Boolean {
        val bindings = Bindings.Supertype(generics)
        val result = arguments.indices.all {
            arguments[it].isSubtypeOf(parameters[it].type, bindings)
        }
        bindings.finalize()
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
                !type.isSubtypeOf(other, false) && !type.isSupertypeOf(other, false)
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

        override val declaration = this

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
        override val declaration: Declaration,
    ) : Function {

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
