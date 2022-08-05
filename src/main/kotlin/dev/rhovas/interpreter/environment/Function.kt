package dev.rhovas.interpreter.environment

sealed interface Function {

    val name: String
    val generics: List<Type.Generic>
    val parameters: List<Pair<String, Type>>
    val returns: Type
    val throws: List<Type>

    fun bind(generics: Map<String, Type>): Function

    data class Declaration(
        override val name: String,
        override val generics: List<Type.Generic>,
        override val parameters: List<Pair<String, Type>>,
        override val returns: Type,
        override val throws: List<Type>,
    ) : Function {

        override fun bind(generics: Map<String, Type>): Declaration {
            return Declaration(
                name,
                this.generics.map { Type.Generic(it.name, it.bound.bind(generics)) },
                parameters.map { Pair(it.first, it.second.bind(generics)) },
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
