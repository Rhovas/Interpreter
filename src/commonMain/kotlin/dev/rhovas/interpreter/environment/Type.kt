package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.library.Library

sealed class Type(
    open val base: Base,
) {

    enum class Component { STRUCT, CLASS, INTERFACE }

    companion object {
        val ANY get() = Library.type("Any")
        val ATOM get() = Library.type("Atom")
        val BOOLEAN get() = Library.type("Boolean")
        val COMPARABLE get() = GenericDelegate("Comparable", 1)
        val DECIMAL get() = Library.type("Decimal")
        val DYNAMIC get() = Library.type("Dynamic")
        val EQUATABLE get() = GenericDelegate("Equatable", 1)
        val EXCEPTION get() = Library.type("Exception")
        val HASHABLE get() = GenericDelegate("Hashable", 1)
        val INTEGER get() = Library.type("Integer")
        val ITERABLE get() = GenericDelegate("Iterable", 1)
        val ITERATOR get() = GenericDelegate("Iterator", 1)
        val LAMBDA get() = GenericDelegate("Lambda", 3)
        val LIST get() = GenericDelegate("List", 1)
        val NULLABLE get() = GenericDelegate("Nullable", 1)
        val REGEX get() = Library.type("Regex")
        val RESULT get() = GenericDelegate("Result", 2)
        val MAP get() = GenericDelegate("Map", 2)
        val SET get() = GenericDelegate("Set", 1)
        val STRING get() = Library.type("String")
        val STRUCT get() = StructDelegate("Struct")
        val TUPLE get() = TupleDelegate("Tuple")
        val TYPE get() = GenericDelegate("Type", 1)
        val VOID get() = Library.type("Void")

        class GenericDelegate(val name: String, val generics: Int) {
            val GENERIC get() = Library.type(name)
            val DYNAMIC get() = Library.type(name, *(1..generics).map { Type.DYNAMIC }.toTypedArray())
            operator fun get(vararg generics: Type) = Library.type(name, *generics)
        }

        class TupleDelegate(val name: String) {
            val GENERIC get() = Library.type(name)
            val DYNAMIC get() = Library.type(name, Type.DYNAMIC)
            operator fun get(generic: Tuple) = Library.type(name, generic)
            operator fun get(elements: List<Type>, mutable: Boolean = false) = Library.type(name, Tuple(elements.withIndex().map {
                Variable.Declaration(it.index.toString(), it.value, mutable)
            }))
        }

        class StructDelegate(val name: String) {
            val GENERIC get() = Library.type(name)
            val DYNAMIC get() = Library.type(name, Type.DYNAMIC)
            operator fun get(generic: Struct) = Library.type(name, generic)
            operator fun get(fields: List<Pair<String, Type>>, mutable: Boolean = false) = Library.type(name, Struct(fields.associate {
                it.first to Variable.Declaration(it.first, it.second, mutable)
            }))
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

    class Base(
        val name: String,
        val component: Component,
        val modifiers: Modifiers,
        val scope: Scope.Definition,
    ) {

        val generics: MutableList<Generic> = mutableListOf()
        val inherits: MutableList<Type> = mutableListOf()
        val reference = Reference(this, generics)

        init {
            when (component) {
                Component.STRUCT -> require(modifiers.inheritance == Modifiers.Inheritance.DEFAULT)
                Component.CLASS -> {}
                Component.INTERFACE -> require(modifiers.inheritance == Modifiers.Inheritance.ABSTRACT)
            }
        }

        fun inherit(type: Reference) {
            when (component) {
                Component.STRUCT -> require(type.base == STRUCT.GENERIC.base || type.base.component == Component.INTERFACE)
                Component.CLASS -> require(type.base.modifiers.inheritance in listOf(Modifiers.Inheritance.VIRTUAL, Modifiers.Inheritance.ABSTRACT))
                Component.INTERFACE -> require(type.base == ANY.base || type.base.component == Component.INTERFACE)
            }
            inherits.add(type)
            type.base.scope.functions.collect()
                .flatMap { entry -> entry.value.map { Pair(entry.key.first, it) } }
                .filter { (_, function) -> function.parameters.firstOrNull()?.type?.isSupertypeOf(type) ?: false }
                .map { (name, function) -> Pair(name, function.bind(type.base.generics.zip(type.generics).associate { it.first.name to it.second })) }
                .filter { (name, function) -> scope.functions[name, function.parameters.size].all { it.isDisjointWith(function) } }
                .forEach { (name, function) -> scope.functions.define(function, name) }
        }

        override fun equals(other: Any?): Boolean {
            return (other is Base && name == other.name && generics == other.generics && inherits == other.inherits).also {
                if (this.toString() == other.toString()) {
                    println("${this} == ${other} = ${it}")
                }
            }
        }

        override fun toString(): String {
            return "Type.Base(name='$name', generics=$generics, inherits=$inherits, scope=$scope)"
        }

    }

    data class Reference(
        override val base: Base,
        val generics: List<Type>,
    ) : Type(base) {

        init {
            //base.generics may be empty during initialization
            require(base.generics.isEmpty() || generics.size == base.generics.size)
        }

        override fun getFunction(name: String, arity: Int): List<Function> {
            return base.scope.functions[name, arity].takeIf { it.isNotEmpty() } ?: listOfNotNull(getFunctionDynamic(name, (1..arity).map { DYNAMIC }))
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            return base.scope.functions[name, arguments] ?: getFunctionDynamic(name, arguments)
        }

        private fun getFunctionDynamic(name: String, arguments: List<Type>): Function? {
            return when (base.name) {
                "Dynamic" -> Function.Declaration(Modifiers(Modifiers.Inheritance.DEFAULT), name, listOf(), arguments.indices.map { Variable.Declaration("val_${it}", DYNAMIC, false) }, DYNAMIC, listOf())
                "Tuple" -> when (generics[0].base.name) {
                    "Dynamic" -> Tuple(listOf(Variable.Declaration(name, DYNAMIC, true))).getFunction(name, arguments)
                    else -> generics[0].getFunction(name, arguments)
                }
                "Struct" -> when (generics[0].base.name) {
                    "Dynamic" -> Struct(mapOf(name to Variable.Declaration(name, DYNAMIC, true))).getFunction(name, arguments)
                    else -> generics[0].getFunction(name, arguments)
                }
                else -> null
            }
        }

        override fun bind(parameters: Map<String, Type>): Reference {
            return Reference(base, generics.map { it.bind(parameters) })
        }

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when (other) {
                is Reference -> when {
                    base.name == "Dynamic" || other.base.name == "Dynamic" || other.base.name == "Any" -> true
                    base.name == other.base.name -> generics.zip(other.generics).all { (type, other) ->
                        when {
                            type is Reference && other is Reference -> when {
                                type.base.name == "Dynamic" || other.base.name == "Dynamic" -> true
                                else -> type.base == other.base && type.isSubtypeOf(other, bindings)
                            }
                            type is Generic -> type.isSubtypeOf(other, bindings)
                            other is Generic -> type.isSubtypeOf(other, bindings).takeIf { it }.also { bindings[other.name] = type } ?: false
                            else -> type.isSubtypeOf(other, bindings)
                        }
                    }
                    else -> {
                        val parameters = base.generics.zip(generics).associate { Pair(it.first.name, it.second) }
                        base.inherits.any { it.bind(parameters).isSubtypeOf(other, bindings) }
                    }
                }
                is Tuple -> isSubtypeOf(TUPLE[other], bindings)
                is Struct -> isSubtypeOf(STRUCT[other], bindings)
                is Generic -> when {
                    base.name == "Dynamic" || other.base.name == "Dynamic" -> true.also { bindings[other.name] = DYNAMIC }
                    bindings.containsKey(other.name) -> isSubtypeOf(bindings[other.name]!!, bindings)
                    else -> isSubtypeOf(other.bound, bindings).takeIf { it }?.also { bindings[other.name] = Variant(this, null) } ?: false
                }
                is Variant -> (other.lower?.isSubtypeOf(this, bindings) ?: true) && (other.upper?.isSupertypeOf(this, bindings) ?: true)
            }
        }

        override fun unify(other: Type, bindings: MutableMap<String, Type>): Type {
            return when (other) {
                is Reference -> when {
                    base.name == "Any" || other.base.name == "Any" -> ANY
                    base.name == "Dynamic" || other.base.name == "Dynamic" -> DYNAMIC
                    base.name == other.base.name -> Reference(base, generics.zip(other.generics).map { (type, other) -> type.unify(other, bindings) })
                    else -> {
                        var top = other
                        while (!isSubtypeOf(top)) {
                            top = top.base.inherits.first().bind(base.generics.zip(generics).associate { Pair(it.first.name, it.second) })
                        }
                        top.unify(this, bindings)
                    }
                }
                is Tuple -> unify(TUPLE[other], bindings)
                is Struct -> unify(STRUCT[other], bindings)
                is Generic -> when {
                    base.name == "Dynamic" || other.base.name == "Dynamic" -> DYNAMIC.also { bindings[other.name] = DYNAMIC }
                    bindings.containsKey(other.name) -> unify(bindings[other.name]!!, bindings)
                    else -> unify(other.bound, bindings).also { bindings[other.name] = it }
                }
                is Variant -> unify(other.upper ?: ANY)
            }
        }

        override fun equals(other: Any?): Boolean {
            return (other is Reference && this.base == other.base && this.generics == other.generics).also {
                if (this.toString() == other.toString()) {
                    println("${this} == ${other} = ${it}")
                }
            }
        }

        override fun toString(): String {
            return "${base.name}${generics.takeIf { it.isNotEmpty() }?.joinToString(", ", "<", ">") ?: ""}"
        }

    }

    data class Tuple(
        val elements: List<Variable.Declaration>,
    ) : Type(TUPLE.GENERIC.base) {

        val scope = Scope.Declaration(null)

        private fun defineProperty(field: Variable.Declaration) {
            scope.functions.define(Function.Definition(Function.Declaration(Modifiers(Modifiers.Inheritance.DEFAULT), field.name, listOf(), listOf(Variable.Declaration("this", this, false)), field.type, listOf())) { (instance) ->
                val instance = instance.value as List<Object>
                instance[field.name.toInt()]
            })
            if (field.mutable) {
                scope.functions.define(Function.Definition(Function.Declaration(Modifiers(Modifiers.Inheritance.DEFAULT), field.name, listOf(), listOf(Variable.Declaration("this", this, false), Variable.Declaration("value", field.type, false)), VOID, listOf())) { (instance, value) ->
                    val instance = instance.value as MutableList<Object>
                    instance[field.name.toInt()] = value
                    Object(VOID, Unit)
                })
            }
        }

        override fun getFunction(name: String, arity: Int): List<Function> {
            name.toIntOrNull()?.let { elements.getOrNull(it) }?.takeIf { scope.functions[name, 1].isEmpty() }?.let { defineProperty(it) }
            return scope.functions[name, arity] + TUPLE.GENERIC.base.scope.functions[name, arity]
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            name.toIntOrNull()?.let { elements.getOrNull(it) }?.takeIf { scope.functions[name, 1].isEmpty() }?.let { defineProperty(it) }
            return scope.functions[name, arguments] ?: TUPLE.GENERIC.base.scope.functions[name, arguments]
        }

        override fun bind(parameters: Map<String, Type>): Type {
            return Tuple(elements.map { Variable.Declaration(it.name, it.type.bind(parameters), it.mutable) })
        }

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when (other) {
                is Tuple -> other.elements.withIndex().all { (index, other) ->
                    elements.getOrNull(index)?.let {
                        val typeSubtype = it.type.isSubtypeOf(other.type)
                        val mutableSubtype = !other.mutable || it.mutable && it.type.isSupertypeOf(other.type)
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
    ) : Type(STRUCT.GENERIC.base) {

        val scope = Scope.Declaration(null)

        private fun defineProperty(field: Variable.Declaration) {
            scope.functions.define(Function.Definition(Function.Declaration(Modifiers(Modifiers.Inheritance.DEFAULT), field.name, listOf(), listOf(Variable.Declaration("this", this, false)), field.type, listOf())) { (instance) ->
                val instance = instance.value as Map<String, Object>
                instance[field.name]!!
            })
            if (field.mutable) {
                scope.functions.define(Function.Definition(Function.Declaration(Modifiers(Modifiers.Inheritance.DEFAULT), field.name, listOf(), listOf(Variable.Declaration("this", this, false), Variable.Declaration("value", field.type, false)), VOID, listOf())) { (instance, value) ->
                    val instance = instance.value as MutableMap<String, Object>
                    instance[field.name] = value
                    Object(VOID, Unit)
                })
            }
        }

        override fun getFunction(name: String, arity: Int): List<Function> {
            fields[name]?.takeIf { scope.functions[name, 1].isEmpty() }?.let { defineProperty(it) }
            return scope.functions[name, arity] + STRUCT.GENERIC.base.scope.functions[name, arity]
        }

        override fun getFunction(name: String, arguments: List<Type>): Function? {
            fields[name]?.takeIf { scope.functions[name, 1].isEmpty() }?.let { defineProperty(it) }
            return scope.functions[name, arguments] ?: STRUCT.GENERIC.base.scope.functions[name, arguments]
        }

        override fun bind(parameters: Map<String, Type>): Type {
            return Struct(fields.mapValues { Variable.Declaration(it.key, it.value.type.bind(parameters), it.value.mutable) })
        }

        override fun isSubtypeOf(other: Type, bindings: MutableMap<String, Type>): Boolean {
            return when (other) {
                is Struct -> other.fields.all { (key, other) ->
                    fields[key]?.let {
                        val typeSubtype = it.type.isSubtypeOf(other.type)
                        val mutableSubtype = !other.mutable || it.mutable && it.type.isSupertypeOf(other.type)
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
