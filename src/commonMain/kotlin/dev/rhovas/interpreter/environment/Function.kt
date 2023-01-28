package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.library.Library

sealed interface Function {

    val name: String
    val generics: List<Type.Generic>
    val parameters: List<Variable.Declaration>
    val returns: Type
    val throws: List<Type>

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
            function.parameters.zip(other.parameters).any { println(it); !it.first.type.isSubtypeOf(it.second.type) && !it.first.type.isSupertypeOf(it.second.type) }
        )
    }

    data class Declaration(
        override val name: String,
        override val generics: List<Type.Generic>,
        override val parameters: List<Variable.Declaration>,
        override val returns: Type,
        override val throws: List<Type>,
    ) : Function {

        override fun bind(generics: Map<String, Type>): Declaration {
            return Declaration(
                name,
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

        fun invoke(arguments: List<Object>): Object {
            return implementation.invoke(arguments)
        }

        override fun bind(generics: Map<String, Type>): Definition {
            return Definition(declaration.bind(generics)).also {
                it.implementation = { implementation.invoke(it) }
            }
        }

    }

}
