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
        val result = isInvariantSubtypeOf(type, binding, Bindings.None)
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
    if (bindings.type == null) {
        return other.component.name == "Dynamic"
    } else if (bindings.type!!.containsKey(type.name)) {
        val binding = bindings.type!![type.name]!!
        if (binding is Type.Variant) {
            if (!isSupertypeOf(binding.upper ?: Type.ANY, other, Bindings.None)) {
                return false
            } else if (binding.lower != null && !isSubtypeOf(binding.lower, other, Bindings.None)) {
                return false
            }
            bindings.type!![type.name] = other
            return true
        } else {
            return isInvariantSubtypeOf(binding, other, Bindings.None)
        }
    } else {
        bindings.type!![type.name] = other
        return isSupertypeOf(type.bound, other, bindings)
    }
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type.Generic, bindings: Bindings): Boolean {
    return when (bindings) {
        is Bindings.None -> type.name == other.name
        is Bindings.Subtype -> {
            val binding = bindings.type[type.name]
            if (binding is Type.Variant) {
                if (!isSupertypeOf(binding.upper ?: Type.ANY, other, Bindings.None)) {
                    return false
                } else if (binding.lower != null && !isSubtypeOf(binding.lower, other, Bindings.None)) {
                    return false
                }
                bindings.type[type.name] = other
                return true
            } else if (binding != null) {
                isInvariantSubtypeOf(binding, other, Bindings.None)
            } else {
                val result = isSupertypeOf(type.bound, other.bound, bindings)
                if (result) {
                    bindings.type[type.name] = other
                }
                return result
            }
        }
        is Bindings.Supertype -> {
            val binding = bindings.other[other.name]
            if (binding is Type.Variant) {
                if (binding.lower != null && isSubtypeOf(type, binding.lower, Bindings.None)) {
                    bindings.other[other.name] = type
                    return true
                } else if (!isSubtypeOf(type, binding.upper ?: Type.ANY, Bindings.None)) {
                    return false
                }
                bindings.other[other.name] = type
                return true
            } else if (binding != null) {
                isInvariantSubtypeOf(type, binding, Bindings.None)
            } else {
                val result = isSubtypeOf(type.bound, other.bound, bindings)
                if (result) {
                    bindings.other[other.name] = type
                }
                return result
            }
        }
        else -> {
            //TODO: Stub
            val binding = bindings.other?.get(other.name)
            return when {
                binding == null -> type.name == other.name
                else -> isInvariantSubtypeOf(type, binding, bindings.also { it.other?.set(other.name, type) })
            }
        }
    }
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type.Variant, bindings: Bindings): Boolean {
    return isSubtypeOf(type, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Reference, bindings: Bindings): Boolean {
    return false
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Generic, bindings: Bindings): Boolean {
    if (bindings.other == null) {
        return false
    } else if (bindings.other!!.containsKey(other.name)) {
        val binding = bindings.other!![other.name]!!
        return isInvariantSubtypeOf(type, binding, Bindings.None)
    } else {
        //TODO: Audit contextualized use cases
        bindings.other!![other.name] = type
        return isSubtypeOf(type.upper ?: Type.ANY, other.bound, bindings)
    }
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Variant, bindings: Bindings): Boolean {
    return isSubtypeOf(type, other, bindings)
}

private fun isInvariantSubtypeOf(field: Variable.Declaration, other: Variable.Declaration, bindings: Bindings): Boolean {
    return field.mutable == other.mutable && isInvariantSubtypeOf(field.type, other.type, bindings)
}
