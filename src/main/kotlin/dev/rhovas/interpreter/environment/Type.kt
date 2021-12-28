package dev.rhovas.interpreter.environment

sealed class Type(
    open val name: String,
) {

    val functions = FunctionsDelegate()
    val properties = PropertiesDelegate()
    val methods = MethodsDelegate()

    internal abstract fun getFunction(name: String, arity: Int): Function.Definition?

    abstract fun bind(parameters: Map<String, Type>): Type

    abstract fun isSubtypeOf(other: Type): Boolean

    inner class FunctionsDelegate {

        operator fun get(name: String, arity: Int): Function.Definition? {
            return getFunction(name, arity)
        }

    }

    inner class PropertiesDelegate {

        operator fun get(name: String): Variable.Property? {
            return methods[name, 0]?.let { getter ->
                Variable.Property(getter, methods[name, 1])
            }
        }

    }

    inner class MethodsDelegate {

        operator fun get(name: String, arity: Int): Function.Method? {
            return functions[name, 1 + arity]?.let {
                Function.Method(it.name, it.parameters.drop(1), it.returns)
            }
        }

    }

    data class Base(
        override val name: String,
        val generics: List<Generic>,
        val inherits: List<Type>,
        val scope: Scope,
    ) : Type(name) {

        override fun getFunction(name: String, arity: Int): Function.Definition? {
            return Reference(this, generics).getFunction(name, arity)
        }

        override fun bind(parameters: Map<String, Type>): Reference {
            return Reference(this, generics).bind(parameters)
        }

        override fun isSubtypeOf(other: Type): Boolean {
            return Reference(this, generics).isSubtypeOf(other)
        }

    }

    data class Reference(
        val base: Base,
        val generics: List<Type>,
    ) : Type(base.name) {

        override fun getFunction(name: String, arity: Int): Function.Definition? {
            return if (base.name == "Dynamic") {
                Function.Definition(name, 0.until(arity).map { Pair("val_${it}", base) }, base)
            } else {
                base.scope.functions[name, arity]?.let { function ->
                    val parameters = base.generics.zip(generics).associate { Pair(it.first.name, it.second) }
                    Function.Definition(
                        function.name,
                        function.parameters.map { Pair(it.first, it.second.bind(parameters)) },
                        function.returns.bind(parameters)
                    ).also {
                        it.implementation = (function as Function.Definition).implementation
                    }
                }
            }
        }

        override fun bind(parameters: Map<String, Type>): Reference {
            return Reference(base, generics.map { it.bind(parameters) })
        }

        override fun isSubtypeOf(other: Type): Boolean {
            return when (other) {
                is Base -> isSubtypeOf(Reference(other, other.generics))
                is Reference -> {
                    if (base.name == "Dynamic" || other.name == "Dynamic" || other.name == "Any") {
                        return true
                    } else if (base.name == other.base.name) {
                        generics.zip(other.generics).all { it.first.isSubtypeOf(it.second) }
                    } else {
                        val parameters = base.generics.zip(generics).associate { Pair(it.first.name, it.second) }
                        base.inherits.any { it.bind(parameters).isSubtypeOf(other) }
                    }
                }
                is Generic -> false
            }
        }

    }

    data class Generic(
        override val name: String,
        val bound: Type,
    ) : Type(name) {

        override fun getFunction(name: String, arity: Int): Function.Definition? {
            return bound.getFunction(name, arity)
        }

        override fun bind(parameters: Map<String, Type>): Type {
            return parameters[name] ?: this //TODO ???
        }

        override fun isSubtypeOf(other: Type): Boolean {
            return when(other) {
                is Generic -> name == other.name
                else -> bound.isSubtypeOf(other)
            }
        }

    }

}
