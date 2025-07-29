package dev.rhovas.interpreter.environment.type

import dev.rhovas.interpreter.environment.Variable

fun isSubtypeOf(type: Type, other: Type, bindings: Bindings): Boolean = when(type) {
    is Type.Reference -> when (other) {
        is Type.Reference -> isSubtypeOf(type, other, bindings)
        is Type.Tuple -> isSubtypeOf(type, Type.TUPLE[other], bindings)
        is Type.Struct -> isSubtypeOf(type, Type.STRUCT[other], bindings)
        is Type.Variant -> isSubtypeOf(type, other, bindings)
        is Type.Generic -> isSubtypeOf(type, other, bindings)
    }
    is Type.Tuple -> when (other) {
        is Type.Tuple -> isSubtypeOf(type, other, bindings)
        else -> isSubtypeOf(Type.TUPLE[type], other, bindings)
    }
    is Type.Struct -> when (other) {
        is Type.Struct -> isSubtypeOf(type, other, bindings)
        else -> isSubtypeOf(Type.STRUCT[type], other, bindings)
    }
    is Type.Generic -> when (other) {
        is Type.Reference -> isSubtypeOf(type, other, bindings)
        is Type.Tuple -> isSubtypeOf(type, Type.TUPLE[other], bindings)
        is Type.Struct -> isSubtypeOf(type, Type.STRUCT[other], bindings)
        is Type.Variant -> isSubtypeOf(type, other, bindings)
        is Type.Generic -> isSubtypeOf(type, other, bindings)
    }
    is Type.Variant -> when (other) {
        is Type.Reference -> isSubtypeOf(type, other, bindings)
        is Type.Tuple -> isSubtypeOf(type, Type.TUPLE[other], bindings)
        is Type.Struct -> isSubtypeOf(type, Type.STRUCT[other], bindings)
        is Type.Variant -> isSubtypeOf(type, other, bindings)
        is Type.Generic -> isSubtypeOf(type, other, bindings)
    }
}

private fun isSubtypeOf(type: Type.Reference, other: Type.Reference, bindings: Bindings): Boolean {
    return when {
        // TODO: Dynamic should ensure generics are still bound.
        type.component.name == "Dynamic" || other.component.name == "Dynamic" -> true
        type.component.name == other.component.name -> when {
            type.component.name in listOf("Tuple", "Struct") -> {
                // TODO: Fix Tuple<Dynamic>/Struct<Dynamic> behavior.
                isSubtypeOf(type.generics.values.single(), other.generics.values.single(), bindings)
            }
            else -> {
                type.generics.values.zip(other.generics.values).all { (type, other) ->
                    isInvariantSubtypeOf(type, other, bindings)
                }
            }
        }
        else -> {
            type.component.inherits.any { inherited ->
                // TODO: Review accuracy of inherited.bind(type.generics).
                // TODO: Review binding behavior for false isSubtypeOf calls.
                isSubtypeOf(inherited.bind(type.generics), other, bindings)
            }
        }
    }
}

private fun isSubtypeOf(type: Type.Reference, other: Type.Generic, bindings: Bindings): Boolean {
    if (bindings.other == null) {
        return type.component.name == "Dynamic"
    } else if (bindings.other!!.containsKey(other.name)) {
        val binding = bindings.other!![other.name]!!
        val result = isSubtypeOf(type, binding, Bindings.None)
        if (result && binding is Type.Variant) {
            bindings.other!![other.name] = Type.Variant(type, binding.upper)
        }
        return result
    } else {
        bindings.other!![other.name] = Type.Variant(type, null)
        val result = isSubtypeOf(type, other.bound, bindings)
        if (result) {
            bindings.other!![other.name] = Type.Variant(type, null)
        }
        return result
    }
}

private fun isSubtypeOf(type: Type.Reference, other: Type.Variant, bindings: Bindings): Boolean {
    return (other.lower == null || isSupertypeOf(type, other.lower, bindings))
        && (other.upper == null || isSubtypeOf(type, other.upper, bindings))
}

private fun isSubtypeOf(type: Type.Tuple, other: Type.Tuple, bindings: Bindings): Boolean {
    return type.elements.size >= other.elements.size
        && type.elements.zip(other.elements).all { (field, other) ->
            isSubtypeOf(field, other, bindings)
        }
}

private fun isSubtypeOf(type: Type.Struct, other: Type.Struct, bindings: Bindings): Boolean {
    return type.fields.keys.containsAll(other.fields.keys)
        && other.fields.map { type.fields[it.key]!! to it.value }.all { (field, other) ->
            isSubtypeOf(field, other, bindings)
        }
}

private fun isSubtypeOf(type: Type.Generic, other: Type.Reference, bindings: Bindings): Boolean {
    if (bindings.type == null) {
        return isSubtypeOf(type.bound, other, bindings)
    } else if (bindings.type!!.containsKey(type.name)) {
        val binding = bindings.type!![type.name]!!
        if (binding is Type.Variant) {
            if (isSubtypeOf(binding.upper ?: Type.ANY, other, Bindings.None)) {
                return true
            } else if (!isSupertypeOf(binding.upper ?: Type.ANY, other, Bindings.None)) {
                return false
            } else if (binding.lower != null && !isSubtypeOf(binding.lower, other, Bindings.None)) {
                return false
            }
            bindings.type!![type.name] = Type.Variant(binding.lower, other)
            return true
        } else {
            return isSubtypeOf(binding, other, Bindings.None)
        }
    } else if (other.component.name == "Dynamic") {
        bindings.type!![type.name] = other
        return true
    } else {
        bindings.type!![type.name] = other
        if (isSubtypeOf(type.bound, other, bindings)) {
            bindings.type!![type.name] = Type.Variant(null, type.bound)
            return true
        } else {
            bindings.type!![type.name] = Type.Variant(null, other)
            val result = isSupertypeOf(type.bound, other, bindings)
            if (result) {
                bindings.type!![type.name] = Type.Variant(null, other)
            }
            return result
        }
    }
}

private fun isSubtypeOf(type: Type.Generic, other: Type.Generic, bindings: Bindings): Boolean {
    return when (bindings) {
        is Bindings.None -> type.name == other.name
        is Bindings.Subtype -> {
            val binding = bindings.type[type.name]
            if (binding is Type.Variant) {
                if (isSubtypeOf(binding.upper ?: Type.ANY, other, Bindings.None)) {
                    return true
                } else if (!isSupertypeOf(binding.upper ?: Type.ANY, other, Bindings.None)) {
                    return false
                } else if (binding.lower != null && !isSubtypeOf(binding.lower, other, Bindings.None)) {
                    return false
                }
                bindings.type[type.name] = Type.Variant(binding.lower, other)
                return true
            } else if (binding != null) {
                return isSubtypeOf(binding, other, Bindings.None)
            } else {
                bindings.type[type.name] = other.bound
                val result = isSupertypeOf(type.bound, other.bound, bindings)
                if (result) {
                    bindings.type[type.name] = Type.Variant(null, other)
                }
                return result
            }
        }
        is Bindings.Supertype -> {
            val binding = bindings.other[other.name]
            if (binding is Type.Variant) {
                if (binding.lower != null && isSubtypeOf(type, binding.lower, Bindings.None)) {
                    return true
                } else if (!isSubtypeOf(type, binding.upper ?: Type.ANY, Bindings.None)) {
                    return false
                }
                bindings.other[other.name] = Type.Variant(type, binding.upper)
                return true
            } else if (binding != null) {
                isSubtypeOf(type, binding, Bindings.None)
            } else {
                bindings.other[other.name] = type.bound
                val result = isSubtypeOf(type.bound, other.bound, bindings)
                if (result) {
                    bindings.other[other.name] = Type.Variant(type, null)
                }
                return result
            }
        }
        else -> {
            //TODO: Stub
            val binding = bindings.other?.get(other.name)
            return when {
                binding == null -> type.name == other.name
                else -> isSubtypeOfBinding(type, other.name, bindings)
            }
        }
    }
}

private fun isSubtypeOf(type: Type.Generic, other: Type.Variant, bindings: Bindings): Boolean {
    if (bindings.type == null) {
        return (other.lower == null || isSupertypeOf(type, other.lower, bindings))
            && isSubtypeOf(type, other.upper ?: Type.ANY, bindings)
    } else if (bindings.type!!.containsKey(type.name)) {
        val binding = bindings.type!![type.name]!!
        if (binding is Type.Variant) {
            val upper = when {
                isSubtypeOf(binding.upper ?: Type.ANY, other.upper ?: Type.ANY, Bindings.None) -> binding.upper
                isSupertypeOf(binding.upper ?: Type.ANY, other.upper ?: Type.ANY, Bindings.None) -> other.upper
                else -> return false
            }
            val lower = when {
                binding.lower == null -> other.lower
                other.lower == null || isSupertypeOf(binding.lower, other.lower, Bindings.None) -> binding.lower
                isSubtypeOf(binding.lower, other.lower, Bindings.None) -> other.lower
                else -> return false
            }
            if (lower != null && !isSubtypeOf(lower, upper ?: Type.ANY, Bindings.None)) {
                return false
            }
            bindings.type!![type.name] = Type.Variant(lower, upper)
            return true
        } else {
            return isSubtypeOf(binding, other, Bindings.None)
        }
    } else {
        val upper = when {
            isSupertypeOf(type.bound, other.upper ?: Type.ANY, bindings) -> other.upper
            isSubtypeOf(type.bound, other.upper ?: Type.ANY, bindings) -> type.bound
            else -> return false
        }
        val lower = when {
            other.lower == null || isSupertypeOf(type.bound, other.lower, bindings) -> other.lower
            else -> return false
        }
        bindings.type!![type.name] = Type.Variant(lower, upper)
        return true
    }
}

private fun isSubtypeOf(type: Type.Variant, other: Type.Reference, bindings: Bindings): Boolean {
    return isSubtypeOf(type.upper ?: Type.ANY, other, bindings)
}

private fun isSubtypeOf(type: Type.Variant, other: Type.Generic, bindings: Bindings): Boolean {
    if (bindings.other == null) {
        return isSubtypeOf(type.upper ?: Type.ANY, other, bindings)
    } else if (bindings.other!!.containsKey(other.name)) {
        val binding = bindings.other!![other.name]!!
        if (binding is Type.Variant) {
            val lower = when {
                binding.lower == null && isSubtypeOf(type.upper ?: Type.ANY, binding.upper ?: Type.ANY, Bindings.None) -> type.upper ?: Type.ANY
                binding.lower != null && isSubtypeOf(type.upper ?: Type.ANY, binding.lower, Bindings.None) -> binding.lower
                binding.lower != null && isSupertypeOf(type.upper ?: Type.ANY, binding.lower, Bindings.None) -> type.upper ?: Type.ANY
                else -> return false
            }
            bindings.other!![other.name] = Type.Variant(lower, binding.upper)
            return true
        } else {
            return isSubtypeOf(type, binding, Bindings.None)
        }
    } else {
        //TODO: Audit contextualized use cases
        bindings.other!![other.name] = type
        return isSubtypeOf(type.upper ?: Type.ANY, other.bound, bindings)
    }
}

private fun isSubtypeOf(type: Type.Variant, other: Type.Variant, bindings: Bindings): Boolean {
    return (other.lower == null || type.lower != null && isSupertypeOf(type.lower, other.lower, bindings))
        && (other.upper == null || isSubtypeOf(type, other.upper, bindings))
}

private fun isSubtypeOf(field: Variable.Declaration, other: Variable.Declaration, bindings: Bindings): Boolean {
    return when {
        // When other is immutable (read-only), field can be any subtype.
        !other.mutable -> isSubtypeOf(field.type, other.type, bindings)
        // Otherwise, field must be mutable and invariant to support writes.
        field.mutable -> isInvariantSubtypeOf(field.type, other.type, bindings)
        else -> false
    }
}

private fun isSubtypeOfBinding(type: Type, name: String, bindings: Bindings): Boolean {
    val subtype = isSubtypeOf(type, bindings.other?.get(name)!!, bindings)
    if (subtype && bindings.other?.get(name) is Type.Variant) {
        bindings.other?.set(name, Type.Variant(type, (bindings.other?.get(name)!! as Type.Variant).upper))
    }
    return subtype
}

fun isSupertypeOf(type: Type, other: Type, bindings: Bindings): Boolean {
    return isSubtypeOf(other, type, when (bindings) {
        is Bindings.None -> bindings
        is Bindings.Subtype -> Bindings.Supertype(bindings.type)
        is Bindings.Supertype -> Bindings.Subtype(bindings.other)
    })
}
