package dev.rhovas.interpreter.environment.type

fun isSubtypeOf(type: Type, other: Type, bindings: MutableMap<String, Type>): Boolean = when(type) {
    is Type.Reference -> isSubtypeOf(type, other, bindings)
    is Type.Tuple -> isSubtypeOf(type, other, bindings)
    is Type.Struct -> isSubtypeOf(type, other, bindings)
    is Type.Generic -> isSubtypeOf(type, other, bindings)
    is Type.Variant -> isSubtypeOf(type, other, bindings)
}

private fun isSubtypeOf(type: Type.Reference, other: Type, bindings: MutableMap<String, Type>): Boolean = when (other) {
    is Type.Reference -> when {
        type.component.name == "Dynamic" || other.component.name == "Dynamic" || other.component.name == "Any" -> true
        type.component.name == other.component.name -> type.generics.values.zip(other.generics.values).all { (type, other) -> when {
            type is Type.Reference && other is Type.Reference -> when {
                type.component.name == "Dynamic" || other.component.name == "Dynamic" -> true
                else -> type.component == other.component && isSubtypeOf(type, other, bindings)
            }
            type is Type.Generic -> isSubtypeOf(type, other, bindings)
            other is Type.Generic -> when {
                bindings.containsKey(other.name) -> isSubtypeOf(type, other, bindings).also { bindings[other.name] = type }
                else -> isSubtypeOf(type, other.bound, bindings.also { it[other.name] = type })
            }
            else -> isSubtypeOf(type, other, bindings)
        } }
        else -> type.component.inherits.any { isSubtypeOf(it.bind(type.generics), other, bindings) }
    }
    is Type.Tuple -> isSubtypeOf(type, Type.TUPLE[other], bindings)
    is Type.Struct -> isSubtypeOf(type, Type.STRUCT[other], bindings)
    is Type.Generic -> when {
        bindings.containsKey(other.name) -> isSubtypeOf(type, bindings[other.name]!!, bindings)
        type.component.name == "Dynamic" -> true.also { bindings[other.name] = Type.DYNAMIC }
        else -> isSubtypeOf(type, other.bound, bindings.also { it[other.name] = Type.Variant(type, null) })
    }
    is Type.Variant -> (
        type.isSupertypeOf(other.lower ?: Type.DYNAMIC, bindings) &&
        isSubtypeOf(type, other.upper ?: Type.DYNAMIC, bindings)
    )
}

private fun isSubtypeOf(type: Type.Tuple, other: Type, bindings: MutableMap<String, Type>): Boolean = when (other) {
    is Type.Tuple -> other.elements.withIndex().all { (index, other) ->
        type.elements.getOrNull(index)?.let {
            val typeSubtype = isSubtypeOf(it.type, other.type, bindings)
            val mutableSubtype = !other.mutable || it.mutable && it.type.isSupertypeOf(other.type, bindings)
            typeSubtype && mutableSubtype
        } ?: false
    }
    else -> isSubtypeOf(Type.TUPLE[type], other, bindings)
}

private fun isSubtypeOf(type: Type.Struct, other: Type, bindings: MutableMap<String, Type>): Boolean = when (other) {
    is Type.Struct -> other.fields.all { (key, other) ->
        type.fields[key]?.let {
            val typeSubtype = isSubtypeOf(it.type, other.type, bindings)
            val mutableSubtype = !other.mutable || it.mutable && it.type.isSupertypeOf(other.type, bindings)
            typeSubtype && mutableSubtype
        } ?: false
    }
    else -> isSubtypeOf(Type.STRUCT[type], other, bindings)
}

private fun isSubtypeOf(type: Type.Generic, other: Type, bindings: MutableMap<String, Type>): Boolean = when {
    type === other -> true //short-circuit for recursive generics
    bindings.containsKey(type.name) -> isSubtypeOf(bindings[type.name]!!, other, bindings)
    other is Type.Generic -> type.name == other.name
    else -> isSubtypeOf(type.bound, other, bindings.also { it[type.name] = other })
}

private fun isSubtypeOf(type: Type.Variant, other: Type, bindings: MutableMap<String, Type>): Boolean = when (other) {
    is Type.Variant -> (
        type.lower?.isSupertypeOf(other.lower ?: Type.DYNAMIC, bindings) ?: (other.lower == null) &&
        isSubtypeOf(type.upper ?: Type.ANY, other.upper ?: Type.DYNAMIC, bindings)
    )
    else -> isSubtypeOf(type.upper ?: Type.ANY, other, bindings)
}
