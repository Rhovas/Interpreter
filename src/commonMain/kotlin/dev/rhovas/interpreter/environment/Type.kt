package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.library.Library

sealed class Type(
    open val base: Base,
) {

    companion object {
        val DYNAMIC get() = Library.type("Dynamic")
        val ANY get() = Library.type("Any")
        val TYPE get() = GenericDelegate("Type")
        val VOID get() = Library.type("Void")
        val NULL get() = Library.type("Null")
        val NULLABLE get() = GenericDelegate("Nullable")
        val BOOLEAN get() = Library.type("Boolean")
        val INTEGER get() = Library.type("Integer")
        val DECIMAL get() = Library.type("Decimal")
        val STRING get() = Library.type("String")
        val ATOM get() = Library.type("Atom")
        val LIST get() = GenericDelegate("List")
        val OBJECT get() = Library.type("Object")
        val STRUCT get() = GenericDelegate("Struct")
        val LAMBDA get() = GenericDelegate("Lambda")
        val EXCEPTION get() = Library.type("Exception")

        class GenericDelegate(val name: String) {
            val ANY get() = Library.type(name).let { Reference(it.base, it.base.generics.map { DYNAMIC }) }
            operator fun get(vararg generics: Type) = Library.type(name, *generics)
        }
    }

    val functions = FunctionsDelegate()
    val properties = PropertiesDelegate()
    val methods = MethodsDelegate()

    internal abstract fun getFunction(name: String, arity: Int): List<Function>

    internal abstract fun getFunction(name: String, arguments: List<Type>): Function?

    abstract fun bind(parameters: Map<String, Type>): Type

    abstract fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type> = mutableMapOf()): Boolean

    fun isSupertypeOf(other: Type, bindings: MutableMap<String, Type> = mutableMapOf()): Boolean {
        return other.isSubtypeOf(this, bindings)
    }

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
                listOf(Function.Declaration(name, listOf(), (1..arity).map { Variable.Declaration("val_${it}", this, false) }, this, listOf()))
            } else {
                base.scope.functions[name, arity]
            }
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            return if (base.name == "Dynamic") {
                Function.Declaration(name, listOf(), arguments.indices.map { Variable.Declaration("val_${it}", this, false) }, this, listOf())
            } else {
                base.scope.functions[name, arguments]
            }
        }

        override fun bind(parameters: Map<String, Type>): Reference {
            return Reference(base, generics.map { it.bind(parameters) })
        }

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when (other) {
                is Reference -> {
                    if (base.name == "Dynamic" || other.base.name == "Dynamic" || other.base.name == "Any") {
                        true
                    } else if (base.name == other.base.name) {
                        return generics.zip(other.generics).all { (type, other) ->
                            when (other) {
                                is Reference -> type.isSubtypeOf(other, bindings) && other.isSubtypeOf(type, bindings)
                                is Generic -> when (val binding = bindings[other.name]) {
                                    null -> type.isSubtypeOf(other, bindings).also { if (bindings[other.name] is Variant) bindings[other.name] = Variant(null, type) }
                                    is Variant -> type.isSubtypeOf(binding, bindings).takeIf { true }?.also { bindings[other.name] = Variant(type, type) } ?: false
                                    else -> type.isSubtypeOf(other, bindings) && other.isSubtypeOf(type, bindings)
                                }
                                else -> type.isSubtypeOf(other, bindings)
                            }
                        }
                    } else {
                        val parameters = base.generics.zip(generics).associate { Pair(it.first.name, it.second) }
                        base.inherits.any { it.bind(parameters).isSubtypeOf(other, bindings) }
                    }
                }
                is Struct -> this.isSubtypeOf(STRUCT[other])
                is Generic -> when {
                    base.name == "Dynamic" -> true.also { bindings[other.name] = this }
                    other.base.name == "Dynamic" -> true.also { bindings[other.name] = other }
                    bindings.containsKey(other.name) -> isSubtypeOf(bindings[other.name]!!, bindings)
                    else -> isSubtypeOf(other.bound, bindings).takeIf { it }?.also { bindings[other.name] = Variant(this, null) } ?: false
                }
                is Variant -> (other.lower?.isSubtypeOf(this) ?: true) && (other.upper?.isSupertypeOf(this) ?: true)
            }
        }

        override fun toString(): String {
            return "${base.name}${generics.takeIf { it.isNotEmpty() }?.joinToString(", ", "<", ">") ?: ""}"
        }

    }

    data class Struct(
        val fields: Map<String, Variable.Declaration>,
    ) : Type(STRUCT.ANY.base) {

        val scope = Scope.Declaration(null)

        private fun defineProperty(field: Variable.Declaration) {
            scope.functions.define(Function.Declaration(field.name, listOf(), listOf(Variable.Declaration("this", this, false)), field.type, listOf()))
            if (field.mutable) {
                scope.functions.define(Function.Declaration(field.name, listOf(), listOf(Variable.Declaration("this", this, false), Variable.Declaration("value", field.type, false)), VOID, listOf()))
            }
        }

        override fun getFunction(name: String, arity: Int): List<Function> {
            fields[name]?.takeIf { scope.functions[name, arity].isEmpty() }?.let { defineProperty(it) }
            return scope.functions[name, arity] + STRUCT[this].getFunction(name, arity)
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            fields[name]?.takeIf { scope.functions[name, arguments.size].isEmpty() }?.let { defineProperty(it) }
            return scope.functions[name, arguments] ?: STRUCT[this].getFunction(name, arguments)
        }

        override fun bind(parameters: Map<String, Type>): Type {
            return Struct(fields.mapValues { Variable.Declaration(it.key, it.value.type.bind(parameters), it.value.mutable) })
        }

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when (other) {
                is Struct -> other.fields.all {
                    val type = fields[it.key]?.type ?: return false
                    when (val other = it.value.type) {
                        is Generic -> when (val binding = bindings[other.name]) {
                            null -> type.isSubtypeOf(other, bindings).also { if (bindings[other.name] is Variant) bindings[other.name] = Variant(null, type) }
                            is Variant -> type.isSubtypeOf(binding, bindings).takeIf { true }?.also { bindings[other.name] = Variant(type, type) } ?: false
                            else -> type.isSubtypeOf(other, bindings) && other.isSubtypeOf(type, bindings)
                        }
                        is Variant -> type.isSubtypeOf(other, bindings)
                        else -> type.isSubtypeOf(other, bindings) && other.isSubtypeOf(type, bindings)
                    }
                }
                else -> STRUCT[this].isSubtypeOf(other, bindings)
            }
        }

        override fun toString(): String {
            return fields.values.joinToString(",", "{", "}") { "${it.name}: ${it.type}" }
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
            return parameters[name] ?: this
        }

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when (other) {
                is Generic -> when {
                    name != other.name -> false
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

    data class Variant(
        val lower: Type?,
        val upper: Type?,
    ) : Type((upper ?: ANY).base) {

        override fun getFunction(name: String, arity: Int): List<Function> {
            return (upper ?: ANY).getFunction(name, arity)
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            return (upper ?: ANY).getFunction(name, arguments)
        }

        override fun bind(parameters: Map<String, Type>): Type {
            return Variant(lower?.bind(parameters), upper?.bind(parameters))
        }

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when {
                lower == null && upper == null -> false
                other is Variant -> (lower?.let { other.lower?.isSubtypeOf(it) } ?: true) && (upper?.let { other.upper?.isSupertypeOf(it) } ?: true)
                else -> (upper ?: ANY).isSubtypeOf(other, bindings)
            }
        }

        override fun toString(): String {
            return when {
                lower == null && upper == null -> "*"
                else -> "${lower ?: "*"} : ${upper ?: "*"}"
            }
        }

    }

}
