package dev.rhovas.interpreter.environment.type

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Method
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Property
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.library.Library

sealed class Type {

    companion object {

        val ANY get() = Library.type("Any")
        val ATOM get() = Library.type("Atom")
        val BOOLEAN get() = Library.type("Boolean")
        val COMPARABLE get() = GenericDelegate("Comparable", listOf("T"))
        val DECIMAL get() = Library.type("Decimal")
        val DYNAMIC get() = Library.type("Dynamic")
        val EQUATABLE get() = GenericDelegate("Equatable", listOf("T"))
        val EXCEPTION get() = Library.type("Exception")
        val HASHABLE get() = GenericDelegate("Hashable", listOf("T"))
        val INTEGER get() = Library.type("Integer")
        val ITERABLE get() = GenericDelegate("Iterable", listOf("T"))
        val ITERATOR get() = GenericDelegate("Iterator", listOf("T"))
        val LAMBDA get() = GenericDelegate("Lambda", listOf("T", "R", "E"))
        val LIST get() = GenericDelegate("List", listOf("T"))
        val NULLABLE get() = GenericDelegate("Nullable", listOf("T"))
        val REGEX get() = Library.type("Regex")
        val RESULT get() = GenericDelegate("Result", listOf("T", "E"))
        val MAP get() = GenericDelegate("Map", listOf("K", "V"))
        val SET get() = GenericDelegate("Set", listOf("T"))
        val STRING get() = Library.type("String")
        val STRUCT get() = StructDelegate("Struct")
        val TUPLE get() = TupleDelegate("Tuple")
        val TYPE get() = GenericDelegate("Type", listOf("T"))
        val VOID get() = Library.type("Void")

        class GenericDelegate(name: String, private val generics: List<String>) {
            val component = Library.type(name).component
            val DYNAMIC = Reference(component, generics.associateWith { Type.DYNAMIC })
            operator fun get(vararg generics: Type) = Reference(component, this.generics.zip(generics).associate { it.first to it.second })
            fun bindings(type: Type) = bindings(type, component)
        }

        class TupleDelegate(name: String) {
            val component = Library.type(name).component
            val DYNAMIC = Reference(component, mapOf("T" to Type.DYNAMIC))
            operator fun get(generic: Tuple) = Reference(component, mapOf("T" to generic))
            operator fun get(elements: List<Type>, mutable: Boolean = false) = Reference(component, mapOf("T" to Tuple(elements.withIndex().map {
                Variable.Declaration(it.index.toString(), it.value, mutable)
            })))
            fun bindings(type: Type) = bindings(type, component)?.mapValues {
                (it.value as Reference).takeIf { it.component.name == "Tuple" }?.generics["T"] as Tuple? ?: it.value
            }
        }

        class StructDelegate(name: String) {
            val component = Library.type(name).component
            val DYNAMIC = Reference(component, mapOf("T" to Type.DYNAMIC))
            operator fun get(generic: Struct) = Reference(component, mapOf("T" to generic))
            operator fun get(fields: List<Pair<String, Type>>, mutable: Boolean = false) = Reference(component, mapOf("T" to Struct(fields.associate {
                it.first to Variable.Declaration(it.first, it.second, mutable)
            })))
            fun bindings(type: Type) = bindings(type, component)?.mapValues {
                (it.value as Reference).takeIf { it.component.name == "Struct" }?.generics["T"] as Struct? ?: it.value
            }
        }

        private fun bindings(type: Type, component: Component<*>): Map<String, Type>? {
            val bindings = Bindings.Supertype(mutableMapOf())
            return when (isSubtypeOf(type, component.type, bindings)) {
                true -> bindings.refined()
                false -> null
            }
        }

    }

    val functions = FunctionsDelegate()
    val properties = PropertiesDelegate()
    val methods = MethodsDelegate()

    internal abstract fun getFunction(name: String, arity: Int): List<Function>

    internal abstract fun getFunction(name: String, arguments: List<Type>): Function?

    abstract fun bind(bindings: Map<String, Type>): Type

    fun isSubtypeOf(other: Type, bindings: Bindings = Bindings.None): Boolean {
        return isSubtypeOf(this, other, bindings)
    }

    fun isSupertypeOf(other: Type, bindings: Bindings = Bindings.None): Boolean {
        return isSupertypeOf(this, other, bindings)
    }

    fun unify(other: Type): Type {
        return unify(this, other)
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

    data class Reference(
        val component: Component<*>,
        val generics: Map<String, Type>,
    ) : Type() {

        init {
            //component.generics may be empty during initialization
            require(component.generics.isEmpty() || generics.size == component.generics.size)
        }

        override fun getFunction(name: String, arity: Int): List<Function> {
            return component.scope.functions[name, arity].takeIf { it.isNotEmpty() } ?: listOfNotNull(getFunctionDynamic(name, (1..arity).map { DYNAMIC }))
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            return component.scope.functions[name, arguments] ?: getFunctionDynamic(name, arguments)
        }

        private fun getFunctionDynamic(name: String, arguments: List<Type>): Function? {
            return when (component.name) {
                "Dynamic" -> Function.Declaration(name,
                    parameters = arguments.indices.map { Variable.Declaration("val_${it}", DYNAMIC) },
                    returns = DYNAMIC,
                )
                "Tuple" -> when ((generics["T"]!! as? Reference)?.component?.name) {
                    "Dynamic" -> Tuple(listOf(Variable.Declaration(name, DYNAMIC, true))).getFunction(name, arguments)
                    else -> generics["T"]!!.getFunction(name, arguments)
                }
                "Struct" -> when ((generics["T"]!! as? Reference)?.component?.name) {
                    "Dynamic" -> Struct(mapOf(name to Variable.Declaration(name, DYNAMIC, true))).getFunction(name, arguments)
                    else -> generics["T"]!!.getFunction(name, arguments)
                }
                else -> null
            }
        }

        override fun bind(bindings: Map<String, Type>): Reference {
            return Reference(component, generics.mapValues { it.value.bind(bindings) })
        }

        override fun equals(other: Any?): Boolean {
            return (other is Reference && this.component == other.component && this.generics == other.generics).also {
                require(it || toString() != other.toString())
            }
        }

        override fun toString(): String {
            return "${component.name}${generics.takeIf { it.isNotEmpty() }?.values?.joinToString(", ", "<", ">") ?: ""}"
        }

    }

    data class Tuple(
        val elements: List<Variable.Declaration>,
    ) : Type() {

        val scope = Scope.Declaration(null)

        private fun defineProperty(field: Variable.Declaration) {
            scope.functions.define(Function.Definition(Function.Declaration(field.name,
                parameters = listOf(Variable.Declaration("this", this)),
                returns = field.type,
            )) { (instance) ->
                val instance = instance.value as List<Object>
                instance[field.name.toInt()]
            })
            if (field.mutable) {
                scope.functions.define(Function.Definition(Function.Declaration(field.name,
                    parameters = listOf(Variable.Declaration("this", this), Variable.Declaration("value", field.type)),
                    returns = VOID,
                )) { (instance, value) ->
                    val instance = instance.value as MutableList<Object>
                    instance[field.name.toInt()] = value
                    Object(VOID, Unit)
                })
            }
        }

        override fun getFunction(name: String, arity: Int): List<Function> {
            name.toIntOrNull()?.let { elements.getOrNull(it) }?.takeIf { scope.functions[name, 1].isEmpty() }?.let { defineProperty(it) }
            return scope.functions[name, arity] + TUPLE.component.scope.functions[name, arity]
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            name.toIntOrNull()?.let { elements.getOrNull(it) }?.takeIf { scope.functions[name, 1].isEmpty() }?.let { defineProperty(it) }
            return scope.functions[name, arguments] ?: TUPLE.component.scope.functions[name, arguments]
        }

        override fun bind(bindings: Map<String, Type>): Type {
            return Tuple(elements.map { Variable.Declaration(it.name, it.type.bind(bindings), it.mutable) })
        }

        override fun toString(): String {
            return elements.joinToString(", ", "[", "]") { "${if (it.mutable) "var" else "val"} ${it.name}: ${it.type}" }
        }

    }

    data class Struct(
        val fields: Map<String, Variable.Declaration>,
    ) : Type() {

        val scope = Scope.Declaration(null)

        private fun defineProperty(field: Variable.Declaration) {
            scope.functions.define(Function.Definition(Function.Declaration(field.name,
                parameters = listOf(Variable.Declaration("this", this)),
                returns = field.type,
            )) { (instance) ->
                val instance = instance.value as Map<String, Object>
                instance[field.name]!!
            })
            if (field.mutable) {
                scope.functions.define(Function.Definition(Function.Declaration(field.name,
                    parameters = listOf(Variable.Declaration("this", this), Variable.Declaration("value", field.type)),
                    returns = VOID,
                )) { (instance, value) ->
                    val instance = instance.value as MutableMap<String, Object>
                    instance[field.name] = value
                    Object(VOID, Unit)
                })
            }
        }

        override fun getFunction(name: String, arity: Int): List<Function> {
            fields[name]?.takeIf { scope.functions[name, 1].isEmpty() }?.let { defineProperty(it) }
            return scope.functions[name, arity] + STRUCT.component.scope.functions[name, arity]
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            fields[name]?.takeIf { scope.functions[name, 1].isEmpty() }?.let { defineProperty(it) }
            return scope.functions[name, arguments] ?: STRUCT.component.scope.functions[name, arguments]
        }

        override fun bind(bindings: Map<String, Type>): Type {
            return Struct(fields.mapValues { Variable.Declaration(it.key, it.value.type.bind(bindings), it.value.mutable) })
        }

        override fun toString(): String {
            return fields.values.joinToString(", ", "{", "}") { "${if (it.mutable) "var" else "val"} ${it.name}: ${it.type}" }
        }

    }

    class Generic private constructor(
        val name: String,
    ) : Type() {

        lateinit var bound: Type

        constructor(name: String, bound: Type) : this(name) { this.bound = bound }
        constructor(name: String, bound: (Type) -> Type) : this(name) { this.bound = bound(this) }

        override fun getFunction(name: String, arity: Int): List<Function> {
            return bound.getFunction(name, arity)
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            return bound.getFunction(name, arguments)
        }

        override fun bind(bindings: Map<String, Type>): Type {
            //TODO: Review uses of fallback bindings; this is sketchy!
            return bindings[name] ?: bound.bind(bindings + mapOf(name to DYNAMIC))
        }

        override fun equals(other: Any?): Boolean {
            return other is Generic && name == other.name && bound == other.bound
        }

        override fun toString(): String {
            fun format(bound: Type?) = when (bound) {
                is Generic, is Variant -> "(${bound})"
                else -> bound.toString()
            }
            return when {
                ::bound.isInitialized -> "${name}: ${format(bound.bind(mapOf(name to Generic(name))))}"
                else -> name //special case for recursive generics within toString
            }
        }
    }

    data class Variant(
        val lower: Type?,
        val upper: Type?,
    ) : Type() {

        override fun getFunction(name: String, arity: Int): List<Function> {
            return (upper ?: ANY).getFunction(name, arity)
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            return (upper ?: ANY).getFunction(name, arguments)
        }

        override fun bind(bindings: Map<String, Type>): Type {
            return Variant(lower?.bind(bindings), upper?.bind(bindings))
        }

        override fun toString(): String {
            fun format(bound: Type?) = when (bound) {
                null -> "*"
                is Generic, is Variant -> "(${bound})"
                else -> bound.toString()
            }
            return when {
                lower == null && upper == null -> "*"
                else -> "${format(lower)} : ${format(upper)}"
            }
        }

    }

}
