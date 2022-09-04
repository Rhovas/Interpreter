package dev.rhovas.interpreter.environment

sealed class Type(
    open val base: Base,
) {

    val functions = FunctionsDelegate()
    val properties = PropertiesDelegate()
    val methods = MethodsDelegate()

    internal abstract fun getFunction(name: String, arity: Int): List<Function>

    internal abstract fun getFunction(name: String, arguments: List<Type>): Function?

    abstract fun bind(parameters: Map<String, Type>): Type

    abstract fun isSubtypeOf(other: Type): Boolean

    abstract fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean

    inner class FunctionsDelegate {

        operator fun get(name: String, arity: Int): List<Function> {
            return getFunction(name, arity)
        }

        operator fun get(name: String, arguments: List<Type>): Function? {
            return getFunction(name, arguments)
        }

    }

    inner class PropertiesDelegate {

        operator fun get(name: String): Property? {
            return methods[name, listOf()]?.let { getter ->
                Property.Declaration(getter, methods[name, listOf(getter.returns)])
            }
        }

    }

    inner class MethodsDelegate {

        operator fun get(name: String, arguments: List<Type>): Method? {
            return functions[name, listOf(this@Type) + arguments]?.let {
                Method.Declaration(it)
            }
        }

    }

    data class Base(
        val name: String,
        val generics: List<Generic>,
        val inherits: List<Type>,
        val scope: Scope.Definition,
    ) {

        val reference = Reference(this, generics)

        override fun toString(): String {
            //TODO: Descriptor printing
            return "Base(" +
                    "name='$name', " +
                    "generics=$generics, " +
                    "inherits=$inherits, " +
                    "scope=$scope, " +
                    ")"
        }

        override fun equals(other: Any?): Boolean {
            return other is Base && name == other.name && generics == other.generics && inherits == other.inherits
        }

    }

    data class Reference(
        override val base: Base,
        val generics: List<Type>,
    ) : Type(base) {

        override fun getFunction(name: String, arity: Int): List<Function> {
            return if (base.name == "Dynamic") {
                listOf(Function.Declaration(name, listOf(), (1..arity).map { Pair("val_${it}", this) }, this, listOf()))
            } else {
                base.scope.functions[name, arity]
            }
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            return if (base.name == "Dynamic") {
                Function.Declaration(name, listOf(), arguments.indices.map { Pair("val_${it}", this) }, this, listOf())
            } else {
                base.scope.functions[name, arguments]
            }
        }

        override fun bind(parameters: Map<String, Type>): Reference {
            return Reference(base, generics.map { it.bind(parameters) })
        }

        override fun isSubtypeOf(other: Type): Boolean {
            return when (other) {
                is Reference -> {
                    if (base.name == "Dynamic" || other.base.name == "Dynamic" || other.base.name == "Any") {
                        true
                    } else if (base.name == other.base.name) {
                        generics.zip(other.generics).all {
                            it.first.isSubtypeOf(it.second) && it.second.isSubtypeOf(it.first) //TODO: Variance
                        }
                    } else {
                        val parameters = base.generics.zip(generics).associate { Pair(it.first.name, it.second) }
                        base.inherits.any { it.bind(parameters).isSubtypeOf(other) }
                    }
                }
                is Generic -> base.name == "Dynamic"
            }
        }

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when (other) {
                is Reference -> {
                    if (base.name == "Dynamic" || other.base.name == "Dynamic" || other.base.name == "Any") {
                        true
                    } else if (base.name == other.base.name) {
                        generics.zip(other.generics).all { (type, other) ->
                            //TODO: Variance
                            return when {
                                other is Generic && !bindings.containsKey(other.name) -> type.isSubtypeOf(other, bindings)
                                else -> type.isSubtypeOf(other, bindings) && other.isSubtypeOf(type, bindings) //TODO: ???
                            }
                        }
                    } else {
                        val parameters = base.generics.zip(generics).associate { Pair(it.first.name, it.second) }
                        base.inherits.any { it.bind(parameters).isSubtypeOf(other, bindings) }
                    }
                }
                is Generic -> when {
                    base.name == "Dynamic" -> true.also { bindings[other.name] = this }
                    other.base.name == "Dynamic" -> true.also { bindings[other.name] = other }
                    bindings.containsKey(other.name) -> isSubtypeOf(bindings[other.name]!!, bindings)
                    else -> isSubtypeOf(other.bound, bindings).takeIf { it }?.also { bindings[other.name] = this } ?: false
                }
            }
        }

        override fun toString(): String {
            return "${base.name}${generics.takeIf { it.isNotEmpty() }?.joinToString(", ", "<", ">") ?: ""}"
        }

    }

    data class Generic(
        val name: String,
        val bound: Type,
    ) : Type(bound.base) {

        override fun getFunction(name: String, arity: Int): List<Function> {
            return bound.getFunction(name, arity)
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            return bound.getFunction(name, arguments)
        }

        override fun bind(parameters: Map<String, Type>): Type {
            return parameters[name] ?: bound //TODO ???
        }

        override fun isSubtypeOf(other: Type): Boolean {
            return when(other) {
                is Generic -> name == other.name //TODO: ???
                else -> bound.isSubtypeOf(other)
            }
        }

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when (other) {
                is Generic -> when {
                    bindings.containsKey(name) -> bindings[name]!!.isSubtypeOf(other)
                    else -> bound.isSubtypeOf(other.bound).takeIf { it }?.also { bindings[name] = other } ?: false
                }
                else -> bound.isSubtypeOf(other).takeIf { it }?.also { bindings[name] = other } ?: false
            }
        }

        override fun toString(): String {
            return "${name}: ${bound}"
        }
    }

}
