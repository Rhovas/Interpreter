package dev.rhovas.interpreter.environment

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

        class GenericDelegate(val name: String, val generics: List<String>) {
            val GENERIC get() = Library.type(name)
            val DYNAMIC get() = Library.type(name, generics.associateWith { Type.DYNAMIC })
            operator fun get(vararg generics: Type) = Library.type(name, this.generics.zip(generics).associate { it.first to it.second })
        }

        class TupleDelegate(val name: String) {
            val GENERIC get() = Library.type(name)
            val DYNAMIC get() = Library.type(name, mapOf("T" to Type.DYNAMIC))
            operator fun get(generic: Tuple) = Library.type(name, mapOf("T" to generic))
            operator fun get(elements: List<Type>, mutable: Boolean = false) = Library.type(name, mapOf("T" to Tuple(elements.withIndex().map {
                Variable.Declaration(it.index.toString(), it.value, mutable)
            })))
        }

        class StructDelegate(val name: String) {
            val GENERIC get() = Library.type(name)
            val DYNAMIC get() = Library.type(name, mapOf("T" to Type.DYNAMIC))
            operator fun get(generic: Struct) = Library.type(name, mapOf("T" to generic))
            operator fun get(fields: List<Pair<String, Type>>, mutable: Boolean = false) = Library.type(name, mapOf("T" to Struct(fields.associate {
                it.first to Variable.Declaration(it.first, it.second, mutable)
            })))
        }
    }

    val functions = FunctionsDelegate()
    val properties = PropertiesDelegate()
    val methods = MethodsDelegate()

    internal abstract fun getFunction(name: String, arity: Int): List<Function>

    internal abstract fun getFunction(name: String, arguments: List<Type>): Function?

    abstract fun bind(bindings: Map<String, Type>): Type

    abstract fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type> = mutableMapOf()): Boolean

    fun isSupertypeOf(other: Type, bindings: MutableMap<String, Type> = mutableMapOf()): Boolean {
        return other.isSubtypeOf(this, bindings)
    }

    fun generic(name: String, projection: Type): Type? {
        return when (this) {
            DYNAMIC -> DYNAMIC
            else -> mutableMapOf<String, Type>().also { isSubtypeOf(projection, it) }[name]
        }
    }

    abstract fun unify(other: Type, bindings: MutableMap<String, Type> = mutableMapOf()): Type

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

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when (other) {
                is Reference -> when {
                    component.name == "Dynamic" || other.component.name == "Dynamic" || other.component.name == "Any" -> true
                    component.name == other.component.name -> generics.values.zip(other.generics.values).all { (type, other) ->
                        when {
                            type is Reference && other is Reference -> when {
                                type.component.name == "Dynamic" || other.component.name == "Dynamic" -> true
                                else -> type.component == other.component && type.isSubtypeOf(other, bindings)
                            }
                            type is Generic -> type.isSubtypeOf(other, bindings)
                            other is Generic -> type.isSubtypeOf(other, bindings).takeIf { it }.also { bindings[other.name] = type } ?: false
                            else -> type.isSubtypeOf(other, bindings)
                        }
                    }
                    else -> component.inherits.any { it.bind(generics).isSubtypeOf(other, bindings) }
                }
                is Tuple -> isSubtypeOf(TUPLE[other], bindings)
                is Struct -> isSubtypeOf(STRUCT[other], bindings)
                is Generic -> when {
                    bindings.containsKey(other.name) -> isSubtypeOf(bindings[other.name]!!, bindings)
                    component.name == "Dynamic" || (other as? Reference)?.component?.name == "Dynamic" -> true.also { bindings[other.name] = DYNAMIC }
                    else -> other.bound.isSupertypeOf(this, bindings).takeIf { it }?.also { bindings[other.name] = Variant(this, null) } ?: false
                }
                is Variant -> (other.lower?.isSubtypeOf(this, bindings) ?: true) && (other.upper?.isSupertypeOf(this, bindings) ?: true)
            }
        }

        override fun unify(other: Type, bindings: MutableMap<String, Type>): Type {
            return when (other) {
                is Reference -> when {
                    component.name == "Dynamic" || other.component.name == "Dynamic" -> DYNAMIC
                    component.name == "Any" || other.component.name == "Any" -> ANY
                    component.name == other.component.name -> Reference(component, generics.entries.zip(other.generics.values).associate { (entry, other) -> entry.key to entry.value.unify(other, bindings) })
                    else -> {
                        var top: Reference = other
                        while (!isSubtypeOf(top, bindings)) {
                            top = top.component.inherits.first().bind(generics)
                        }
                        top.unify(this, bindings)
                    }
                }
                is Tuple -> unify(TUPLE[other], bindings)
                is Struct -> unify(STRUCT[other], bindings)
                is Generic -> when {
                    bindings.containsKey(other.name) -> unify(bindings[other.name]!!, bindings)
                    component.name == "Dynamic" || (other as? Reference)?.component?.name == "Dynamic" -> DYNAMIC.also { bindings[other.name] = DYNAMIC }
                    else -> unify(other.bound, bindings).also { bindings[other.name] = it }
                }
                is Variant -> unify(other.upper ?: ANY)
            }
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
            return scope.functions[name, arity] + TUPLE.GENERIC.component.scope.functions[name, arity]
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            name.toIntOrNull()?.let { elements.getOrNull(it) }?.takeIf { scope.functions[name, 1].isEmpty() }?.let { defineProperty(it) }
            return scope.functions[name, arguments] ?: TUPLE.GENERIC.component.scope.functions[name, arguments]
        }

        override fun bind(bindings: Map<String, Type>): Type {
            return Tuple(elements.map { Variable.Declaration(it.name, it.type.bind(bindings), it.mutable) })
        }

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when (other) {
                is Tuple -> other.elements.withIndex().all { (index, other) ->
                    elements.getOrNull(index)?.let {
                        val typeSubtype = it.type.isSubtypeOf(other.type, bindings)
                        val mutableSubtype = !other.mutable || it.mutable && it.type.isSupertypeOf(other.type, bindings)
                        typeSubtype && mutableSubtype
                    } ?: false
                }
                else -> TUPLE[this].isSubtypeOf(other, bindings)
            }
        }

        override fun unify(other: Type, bindings: MutableMap<String, Type>): Type {
            return when (other) {
                is Tuple -> Tuple(elements.zip(other.elements).mapIndexed { index, pair ->
                    Variable.Declaration(index.toString(), pair.first.type.unify(pair.second.type, bindings), pair.first.mutable && pair.second.mutable)
                })
                else -> TUPLE[this].unify(other, bindings)
            }
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
            return scope.functions[name, arity] + STRUCT.GENERIC.component.scope.functions[name, arity]
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            fields[name]?.takeIf { scope.functions[name, 1].isEmpty() }?.let { defineProperty(it) }
            return scope.functions[name, arguments] ?: STRUCT.GENERIC.component.scope.functions[name, arguments]
        }

        override fun bind(bindings: Map<String, Type>): Type {
            return Struct(fields.mapValues { Variable.Declaration(it.key, it.value.type.bind(bindings), it.value.mutable) })
        }

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when (other) {
                is Struct -> other.fields.all { (key, other) ->
                    fields[key]?.let {
                        val typeSubtype = it.type.isSubtypeOf(other.type, bindings)
                        val mutableSubtype = !other.mutable || it.mutable && it.type.isSupertypeOf(other.type, bindings)
                        typeSubtype && mutableSubtype
                    } ?: false
                }
                else -> STRUCT[this].isSubtypeOf(other, bindings)
            }
        }

        override fun unify(other: Type, bindings: MutableMap<String, Type>): Type {
            return when (other) {
                is Struct -> Struct(fields.keys.intersect(other.fields.keys).map { Pair(fields[it]!!, other.fields[it]!!) }.associate { pair ->
                    pair.first.name to Variable.Declaration(pair.first.name, pair.first.type.unify(pair.second.type, bindings), pair.first.mutable && pair.second.mutable)
                })
                else -> STRUCT[this].unify(other, bindings)
            }
        }

        override fun toString(): String {
            return fields.values.joinToString(", ", "{", "}") { "${if (it.mutable) "var" else "val"} ${it.name}: ${it.type}" }
        }

    }

    data class Generic(
        val name: String,
        val bound: Type,
    ) : Type() {

        override fun getFunction(name: String, arity: Int): List<Function> {
            return bound.getFunction(name, arity)
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            return bound.getFunction(name, arguments)
        }

        override fun bind(bindings: Map<String, Type>): Type {
            return bindings[name] ?: this
        }

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when {
                bindings.containsKey(name) -> bindings[name]!!.isSubtypeOf(other, bindings)
                other is Generic -> name == other.name
                else -> bound.isSubtypeOf(other, bindings).takeIf { it }?.also { bindings[name] = other } ?: false
            }
        }

        override fun unify(other: Type, bindings: MutableMap<String, Type>): Type {
            return when {
                bindings.containsKey(name) -> bindings[name]!!.unify(other, bindings)
                other is Generic -> if (name == other.name) this else bound.unify(other.bound)
                else -> bound.unify(other, bindings).also { bindings[name] = other }
            }
        }

        override fun toString(): String {
            return "${name}: ${bound}"
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

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when (other) {
                is Variant -> {
                    val lowerSubtype = lower?.isSupertypeOf(other.lower ?: DYNAMIC, bindings) ?: (other.lower == null)
                    val upperSubtype = (upper ?: ANY).isSubtypeOf(other.upper ?: DYNAMIC, bindings)
                    lowerSubtype && upperSubtype
                }
                else -> (upper ?: ANY).isSubtypeOf(other, bindings)
            }
        }

        override fun unify(other: Type, bindings: MutableMap<String, Type>): Type {
            return when (other) {
                is Variant -> Variant(other.lower?.let { lower?.unify(it, bindings) } ?: lower, (upper ?: ANY).unify(other.upper ?: ANY, bindings))
                else -> (upper ?: ANY).unify(other, bindings)
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
