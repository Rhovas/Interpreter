package dev.rhovas.interpreter.environment.type

fun isInvariantSubtypeOf(type: Type, other: Type, bindings: MutableMap<String, Type>): Boolean = when(type) {
    is Type.Reference -> isInvariantSubtypeOf(type, other, bindings)
    is Type.Tuple -> isInvariantSubtypeOf(type, other, bindings)
    is Type.Struct -> isInvariantSubtypeOf(type, other, bindings)
    is Type.Generic -> isInvariantSubtypeOf(type, other, bindings)
    is Type.Variant -> isInvariantSubtypeOf(type, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Reference, other: Type, bindings: MutableMap<String, Type>): Boolean = when (other) {
    is Type.Reference -> when {
        type.component.name == "Dynamic" || other.component.name == "Dynamic" -> true
        type.component.name == other.component.name -> type.generics.values.zip(other.generics.values).all { isInvariantSubtypeOf(it.first, it.second, bindings) }
        else -> false
    }
    is Type.Tuple -> isInvariantSubtypeOf(type, Type.TUPLE[other], bindings)
    is Type.Struct -> isInvariantSubtypeOf(type, Type.STRUCT[other], bindings)
    is Type.Generic -> when {
        bindings.containsKey(other.name) -> isInvariantSubtypeOf(type, bindings[other.name]!!, bindings).also { bindings[other.name] = type }
        type.component.name == "Dynamic" -> true.also { bindings[other.name] = Type.DYNAMIC }
        else -> isSubtypeOf(type, other.bound, bindings.also { it[other.name] = type })
    }
    is Type.Variant -> isSubtypeOf(type, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Tuple, other: Type, bindings: MutableMap<String, Type>): Boolean = when (other) {
    is Type.Tuple -> type.elements.size == other.elements.size && type.elements.zip(other.elements).all {
        val typeInvariantSubtype = isInvariantSubtypeOf(it.first.type, it.second.type, bindings)
        val mutableInvariantSubtype = it.first.mutable == it.second.mutable
        typeInvariantSubtype && mutableInvariantSubtype
    }
    else -> isInvariantSubtypeOf(Type.TUPLE[type], other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Struct, other: Type, bindings: MutableMap<String, Type>): Boolean = when (other) {
    is Type.Struct -> type.fields.keys == other.fields.keys && type.fields.map { it.value to other.fields[it.key]!! }.all {
        val typeInvariantSubtype = isInvariantSubtypeOf(it.first.type, it.second.type, bindings)
        val mutableInvariantSubtype = it.first.mutable == it.second.mutable
        typeInvariantSubtype && mutableInvariantSubtype
    }
    else -> isInvariantSubtypeOf(Type.STRUCT[type], other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type, bindings: MutableMap<String, Type>): Boolean = when {
    type === other -> true //short-circuit for recursive generics
    bindings.containsKey(type.name) -> isInvariantSubtypeOf(bindings[type.name]!!, other, bindings)
    other is Type.Generic -> type.name == other.name
    else -> isInvariantSubtypeOf(type.bound, other, bindings.also { it[type.name] = other })
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type, bindings: MutableMap<String, Type>): Boolean = when (other) {
    is Type.Variant -> isSubtypeOf(type, other, bindings)
    else -> false
}
