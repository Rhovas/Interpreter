package dev.rhovas.interpreter.environment

sealed class Type(
    open val base: Base,
) {

    val functions = FunctionsDelegate()
    val properties = PropertiesDelegate()
    val methods = MethodsDelegate()

    internal abstract fun getFunction(name: String, arguments: List<Type>): Function.Definition?

    abstract fun bind(type: Type): Map<String, Type>

    abstract fun bind(parameters: Map<String, Type>): Type

    abstract fun isSubtypeOf(other: Type): Boolean

    inner class FunctionsDelegate {

        operator fun get(name: String, arguments: List<Type>): Function.Definition? {
            return getFunction(name, arguments)
        }

    }

    inner class PropertiesDelegate {

        operator fun get(name: String): Variable.Property? {
            return methods[name, listOf()]?.let { getter ->
                Variable.Property(getter, methods[name, listOf(getter.returns)])
            }
        }

    }

    inner class MethodsDelegate {

        operator fun get(name: String, arguments: List<Type>): Function.Method? {
            return functions[name, listOf(this@Type) + arguments]?.let {
                Function.Method(it.name, it.generics, it.parameters.drop(1), it.returns, it.throws)
            }
        }

    }

    data class Base(
        val name: String,
        val generics: List<Generic>,
        val inherits: List<Type>,
        val scope: Scope,
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


    }

    data class Reference(
        override val base: Base,
        val generics: List<Type>,
    ) : Type(base) {

        override fun getFunction(name: String, arguments: List<Type>): Function.Definition? {
            return if (base.name == "Dynamic") {
                Function.Definition(name, listOf(), arguments.indices.map { Pair("val_${it}", this) }, this, listOf())
            } else {
                base.scope.functions[name, arguments]?.let { it as Function.Definition }
            }
        }

        override fun bind(type: Type): Map<String, Type> {
            return when (type) {
                is Reference -> {
                    if (base.name == "Dynamic" || type.base.name == "Dynamic" || type.base.name == "Any") {
                        mapOf()
                    } else if (base.name == type.base.name) {
                        mapOf(*generics.zip(type.generics)
                            .flatMap { it.first.bind(it.second).entries.map { it.key to it.value } }
                            .toTypedArray())
                    } else {
                        val parameters = base.generics.zip(generics).associate { Pair(it.first.name, it.second) }
                        base.inherits.firstNotNullOfOrNull {
                            val parent = it.bind(parameters)
                            if (parent.isSubtypeOf(type)) parent.bind(type) else null
                        } ?: mapOf()
                    }
                }
                is Generic -> bind(type.bound)
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
                        generics.zip(other.generics).all { it.first.isSubtypeOf(it.second) }
                    } else {
                        val parameters = base.generics.zip(generics).associate { Pair(it.first.name, it.second) }
                        base.inherits.any { it.bind(parameters).isSubtypeOf(other) }
                    }
                }
                is Generic -> base.name == "Dynamic" || this.isSubtypeOf(other.bound)
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

        override fun getFunction(name: String, arguments: List<Type>): Function.Definition? {
            return bound.getFunction(name, arguments)
        }

        override fun bind(type: Type): Map<String, Type> {
            return mapOf(Pair(name, type))
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

        override fun toString(): String {
            return "${name}: ${bound}"
        }
    }

}
