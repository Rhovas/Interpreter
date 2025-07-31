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
     * Returns a map of bindings allowing this function to be resolved by
     * [arguments], if any solution exists.
     */
    fun isResolvedBy(arguments: List<Type>): Map<String, Type>? {
        require(arguments.size == parameters.size)
        val bindings = Bindings.Supertype(mutableMapOf())
        return when (arguments.zip(parameters).all { it.first.isSubtypeOf(it.second.type, bindings) }) {
            true -> bindings.refined()
            else -> null
        }
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
                !it.first.type.isSubtypeOf(it.second.type, Bindings.Supertype(mutableMapOf()))
                && !it.first.type.isSupertypeOf(it.second.type, Bindings.Supertype(mutableMapOf()))
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
            val bindings = generics + bindings //retain non-bound generics
            return Declaration(
                name,
                modifiers,
                bindings.values.filterIsInstance<Type.Generic>().associateByTo(linkedMapOf()) { it.name },
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
