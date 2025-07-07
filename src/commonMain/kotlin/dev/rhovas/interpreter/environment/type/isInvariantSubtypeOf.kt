package dev.rhovas.interpreter.environment.type

fun isInvariantSubtypeOf(type: Type, other: Type, bindings: MutableMap<String, Type>): Boolean = when(type) {
    is Type.Reference -> when (other) {
        is Type.Reference -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Tuple -> isInvariantSubtypeOf(type, Type.TUPLE[other], bindings)
        is Type.Struct -> isInvariantSubtypeOf(type, Type.STRUCT[other], bindings)
        is Type.Variant -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Generic -> isInvariantSubtypeOf(type, other, bindings)
    }
    is Type.Tuple -> when (other) {
        is Type.Tuple -> isInvariantSubtypeOf(type, other, bindings)
        else -> isInvariantSubtypeOf(Type.TUPLE[type], other, bindings)
    }
    is Type.Struct -> when (other) {
        is Type.Struct -> isInvariantSubtypeOf(type, other, bindings)
        else -> isInvariantSubtypeOf(Type.STRUCT[type], other, bindings)
    }
    is Type.Generic -> when (other) {
        is Type.Reference -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Tuple -> isInvariantSubtypeOf(type, Type.TUPLE[other], bindings)
        is Type.Struct -> isInvariantSubtypeOf(type, Type.STRUCT[other], bindings)
        is Type.Variant -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Generic -> isInvariantSubtypeOf(type, other, bindings)
    }
    is Type.Variant -> when (other) {
        is Type.Reference -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Tuple -> isInvariantSubtypeOf(type, Type.TUPLE[other], bindings)
        is Type.Struct -> isInvariantSubtypeOf(type, Type.STRUCT[other], bindings)
        is Type.Variant -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Generic -> isInvariantSubtypeOf(type, other, bindings)
    }
}

private fun isInvariantSubtypeOf(type: Type.Reference, other: Type.Reference, bindings: MutableMap<String, Type>): Boolean {
    return when {
        // TODO: Dynamic should ensure generics are still bound.
        type.component.name == "Dynamic" || other.component.name == "Dynamic" -> true
        type.component.name == other.component.name -> {
            type.generics.values.zip(other.generics.values).all { (type, other) ->
                isInvariantSubtypeOf(type, other, bindings)
            }
        }
        else -> false // no subtyping via inheritance for invariants
    }
}

private fun isInvariantSubtypeOf(type: Type.Reference, other: Type.Generic, bindings: MutableMap<String, Type>): Boolean {
    return when {
        bindings.containsKey(other.name) -> isInvariantSubtypeOf(type, bindings[other.name]!!, bindings).also { bindings[other.name] = type }
        type.component.name == "Dynamic" -> true.also { bindings[other.name] = Type.DYNAMIC }
        else -> isSubtypeOf(type, other.bound, bindings.also { it[other.name] = type })
    }
}

private fun isInvariantSubtypeOf(type: Type.Reference, other: Type.Variant, bindings: MutableMap<String, Type>): Boolean {
    return isSubtypeOf(type, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Tuple, other: Type.Tuple, bindings: MutableMap<String, Type>): Boolean {
    return type.elements.size == other.elements.size && type.elements.zip(other.elements).all {
        val typeInvariantSubtype = isInvariantSubtypeOf(it.first.type, it.second.type, bindings)
        val mutableInvariantSubtype = it.first.mutable == it.second.mutable
        typeInvariantSubtype && mutableInvariantSubtype
    }
}

private fun isInvariantSubtypeOf(type: Type.Struct, other: Type.Struct, bindings: MutableMap<String, Type>): Boolean {
    return type.fields.keys == other.fields.keys && type.fields.map { it.value to other.fields[it.key]!! }.all {
        val typeInvariantSubtype = isInvariantSubtypeOf(it.first.type, it.second.type, bindings)
        val mutableInvariantSubtype = it.first.mutable == it.second.mutable
        typeInvariantSubtype && mutableInvariantSubtype
    }
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type.Reference, bindings: MutableMap<String, Type>): Boolean {
    return isInvariantSubtypeOf(type.bound, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type.Generic, bindings: MutableMap<String, Type>): Boolean {
    return when {
        bindings.containsKey(other.name) -> isInvariantSubtypeOf(type, bindings[other.name]!!, bindings.also { it[other.name] = type })
        else -> type.name == other.name
    }
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type.Variant, bindings: MutableMap<String, Type>): Boolean {
    return isInvariantSubtypeOf(type.bound, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Reference, bindings: MutableMap<String, Type>): Boolean {
    return false
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Generic, bindings: MutableMap<String, Type>): Boolean {
    return false
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Variant, bindings: MutableMap<String, Type>): Boolean {
    return isSubtypeOf(type, other, bindings)
}
