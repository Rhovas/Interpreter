package dev.rhovas.interpreter.environment.type

import dev.rhovas.interpreter.environment.Variable

fun isInvariantSubtypeOf(type: Type, other: Type, bindings: Bindings): Boolean = when(type) {
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

private fun isInvariantSubtypeOf(type: Type.Reference, other: Type.Reference, bindings: Bindings): Boolean {
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

private fun isInvariantSubtypeOf(type: Type.Reference, other: Type.Generic, bindings: Bindings): Boolean {
    if (bindings.other == null) {
        return type.component.name == "Dynamic"
    } else if (bindings.other!!.containsKey(other.name)) {
        val binding = bindings.other!![other.name]!!
        val result = isInvariantSubtypeOf(type, binding, bindings)
        if (result) {
            bindings.other!![other.name] = type
        }
        return result
    } else {
        bindings.other!![other.name] = type
        return isSubtypeOf(type, other.bound, bindings)
    }
}

private fun isInvariantSubtypeOf(type: Type.Reference, other: Type.Variant, bindings: Bindings): Boolean {
    return isSubtypeOf(type, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Tuple, other: Type.Tuple, bindings: Bindings): Boolean {
    return type.elements.size == other.elements.size
        && type.elements.zip(other.elements).all { (field, other) ->
            isInvariantSubtypeOf(field, other, bindings)
        }
}

private fun isInvariantSubtypeOf(type: Type.Struct, other: Type.Struct, bindings: Bindings): Boolean {
    return type.fields.keys == other.fields.keys
        && type.fields.map { it.value to other.fields[it.key]!! }.all { (field, other) ->
            isInvariantSubtypeOf(field, other, bindings)
        }
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type.Reference, bindings: Bindings): Boolean {
    return isInvariantSubtypeOf(type.bound, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type.Generic, bindings: Bindings): Boolean {
    return when {
        bindings.other?.containsKey(other.name) == true -> isInvariantSubtypeOf(type, bindings.other?.get(other.name)!!, bindings.also { it.other?.set(other.name, type) })
        else -> type.name == other.name
    }
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type.Variant, bindings: Bindings): Boolean {
    return isInvariantSubtypeOf(type.bound, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Reference, bindings: Bindings): Boolean {
    return false
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Generic, bindings: Bindings): Boolean {
    return false
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Variant, bindings: Bindings): Boolean {
    return isSubtypeOf(type, other, bindings)
}

private fun isInvariantSubtypeOf(field: Variable.Declaration, other: Variable.Declaration, bindings: Bindings): Boolean {
    return field.mutable == other.mutable && isInvariantSubtypeOf(field.type, other.type, bindings)
}
